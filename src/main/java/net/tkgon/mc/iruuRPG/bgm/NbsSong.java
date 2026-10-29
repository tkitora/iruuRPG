package net.tkgon.mc.iruuRPG.bgm;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A song of Note Block Studio (.nbs, every version from the classic one to version 5), read into the notes that the player needs: when, which instrument, which key, how
 * loud and how far left or right. Custom instruments are skipped (the sounds of a resource pack are not played yet), and so is everything the plugin has no use for (the
 * lock of a layer, the fine pitch of a note, the clicks counted by the editor).
 */
public final class NbsSong {

    /** One note: {@code tick} is when it sounds, {@code volume} is 0 to 1 (layer volume times velocity), {@code pan} is -1 (left) to 1 (right). */
    public record Note(int tick, int instrument, int key, float volume, float pan) {
    }

    private static final int VANILLA_INSTRUMENTS = 16;
    private static final int MAX_STRING = 1 << 20;

    private final String id;
    private final String title;
    private final double ticksPerSecond;
    private final int length;
    private final boolean loop;
    private final int loopStart;
    private final Note[] notes;
    /** {@code firstNote[t]} is the index of the first note at tick {@code t} or later; one more entry at the end. */
    private final int[] firstNote;

    private NbsSong(String id, String title, double ticksPerSecond, int length, boolean loop, int loopStart, List<Note> list) {
        this.id = id;
        this.title = title;
        this.ticksPerSecond = ticksPerSecond;
        this.length = length;
        this.loop = loop;
        this.loopStart = Math.max(0, Math.min(loopStart, length - 1));
        List<Note> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingInt(Note::tick));
        this.notes = sorted.toArray(new Note[0]);
        this.firstNote = new int[length + 1];
        int index = 0;
        for (int tick = 0; tick <= length; tick++) {
            while (index < notes.length && notes[index].tick() < tick) index++;
            firstNote[tick] = index;
        }
    }

    public String id() {
        return id;
    }

    /** The name written in the file, or the id when the file has none. */
    public String title() {
        return title;
    }

    public double ticksPerSecond() {
        return ticksPerSecond;
    }

    /** The number of ticks of the song: the ticks 0 to length - 1 are played, then it ends (or loops). */
    public int length() {
        return length;
    }

    public double seconds() {
        return length / ticksPerSecond;
    }

    /** Whether the file asks for a loop; the player of a volume can also loop a song that does not. */
    public boolean loops() {
        return loop;
    }

    /** The tick a loop goes back to (0 when the file has none). */
    public int loopStart() {
        return loopStart;
    }

    public int noteCount() {
        return notes.length;
    }

    /** The index of the first note at {@code tick} or later (for {@link #note(int)}); {@code noteCount()} when there is none. */
    public int firstNoteAt(int tick) {
        if (tick <= 0) return 0;
        if (tick >= length) return notes.length;
        return firstNote[tick];
    }

    public Note note(int index) {
        return notes[index];
    }

    // ---- reading ---------------------------------------------------------------------------------------------------------------------

    public static NbsSong read(Path file) throws IOException {
        String name = file.getFileName().toString();
        String id = name.toLowerCase(java.util.Locale.ROOT).endsWith(".nbs") ? name.substring(0, name.length() - 4) : name;
        return read(id, Files.readAllBytes(file));
    }

    public static NbsSong read(String id, byte[] data) throws IOException {
        try {
            return parse(id, new DataInputStream(new ByteArrayInputStream(data)));
        } catch (EOFException exception) {
            throw new IOException("the file ends too early", exception);
        } catch (RuntimeException exception) {
            throw new IOException("not a usable .nbs file: " + exception.getMessage(), exception);
        }
    }

    private static NbsSong parse(String id, DataInputStream in) throws IOException {
        int first = readShort(in);
        int version = 0;
        int vanilla = VANILLA_INSTRUMENTS;
        int length;
        if (first == 0) { // the newer format starts with a zero where the old one has the length
            version = in.readUnsignedByte();
            vanilla = in.readUnsignedByte();
            length = version >= 3 ? readShort(in) : 0;
        } else {
            length = first;
        }
        int layerCount = readShort(in);
        String title = readString(in);
        readString(in); // author
        readString(in); // original author
        readString(in); // description
        int tempo = readShort(in);
        in.readNBytes(3); // auto-save, its period, time signature
        in.readNBytes(20); // minutes spent, left clicks, right clicks, blocks added, blocks removed
        readString(in); // the midi / schematic file name
        boolean loop = false;
        int loopStart = 0;
        if (version >= 4) {
            loop = in.readUnsignedByte() != 0;
            in.readUnsignedByte(); // how many times
            loopStart = readShort(in);
        }
        double tps = tempo > 0 ? tempo / 100.0 : 10.0;
        // (the file keeps two decimals: 6.67 is meant as 20 / 3, a song tick of exactly 3 server ticks; this keeps the notes from drifting off the server ticks)
        double serverTicks = 20.0 / tps;
        if (Math.abs(serverTicks - Math.rint(serverTicks)) < 0.01 && Math.rint(serverTicks) >= 1.0) tps = 20.0 / Math.rint(serverTicks);

        List<int[]> raw = new ArrayList<>(); // tick, layer, instrument, key, velocity, pan
        int tick = -1;
        int lastTick = 0;
        while (true) {
            int jump = readShort(in);
            if (jump == 0) break;
            tick += jump;
            int layer = -1;
            while (true) {
                int layerJump = readShort(in);
                if (layerJump == 0) break;
                layer += layerJump;
                int instrument = in.readUnsignedByte();
                int key = in.readUnsignedByte();
                int velocity = 100;
                int pan = 100;
                if (version >= 4) {
                    velocity = in.readUnsignedByte();
                    pan = in.readUnsignedByte();
                    in.readShort(); // fine pitch
                }
                raw.add(new int[]{tick, layer, instrument, key, velocity, pan});
                lastTick = Math.max(lastTick, tick);
            }
        }

        int[] layerVolume = new int[Math.max(layerCount, maxLayer(raw) + 1)];
        int[] layerPan = new int[layerVolume.length];
        java.util.Arrays.fill(layerVolume, 100);
        java.util.Arrays.fill(layerPan, 100);
        for (int layer = 0; layer < layerCount; layer++) {
            if (in.available() <= 0) break; // some files stop after the notes
            readString(in);
            if (version >= 4) in.readUnsignedByte(); // locked
            layerVolume[layer] = in.readUnsignedByte();
            if (version >= 2) layerPan[layer] = in.readUnsignedByte();
        }

        List<Note> notes = new ArrayList<>(raw.size());
        for (int[] entry : raw) {
            int instrument = entry[2];
            if (instrument >= vanilla || instrument >= VANILLA_INSTRUMENTS) continue; // a custom instrument
            int layer = entry[1];
            float volume = layerVolume[layer] / 100.0f * entry[4] / 100.0f;
            int pan = entry[5] == 100 ? layerPan[layer] : entry[5];
            notes.add(new Note(entry[0], instrument, entry[3], Math.max(0.0f, Math.min(1.0f, volume)), Math.max(-1.0f, Math.min(1.0f, (pan - 100) / 100.0f))));
        }
        // (the length of the header counts the last tick that holds a note, or, for a song that ends in silence, more; the notes decide when it is shorter)
        int songLength = Math.max(Math.max(length, lastTick) + 1, 1);
        return new NbsSong(id, title.isBlank() ? id : title, tps, songLength, loop, loopStart, notes);
    }

    private static int maxLayer(List<int[]> raw) {
        int max = 0;
        for (int[] entry : raw) max = Math.max(max, entry[1]);
        return max;
    }

    private static int readShort(DataInputStream in) throws IOException {
        int low = in.readUnsignedByte();
        int high = in.readUnsignedByte();
        return low | (high << 8);
    }

    private static int readInt(DataInputStream in) throws IOException {
        int a = in.readUnsignedByte();
        int b = in.readUnsignedByte();
        int c = in.readUnsignedByte();
        int d = in.readUnsignedByte();
        return a | (b << 8) | (c << 16) | (d << 24);
    }

    private static String readString(DataInputStream in) throws IOException {
        int size = readInt(in);
        if (size < 0 || size > MAX_STRING) throw new IOException("a text of " + size + " bytes");
        byte[] bytes = new byte[size];
        in.readFully(bytes);
        return new String(bytes, Charset.forName("windows-1252"));
    }
}
