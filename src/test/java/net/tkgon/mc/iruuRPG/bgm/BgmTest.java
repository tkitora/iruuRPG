package net.tkgon.mc.iruuRPG.bgm;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BgmTest {

    // ---- a small .nbs file made here: version 5, one layer, one note per tick at 20 ticks a second (one song tick for every server tick) -------------------

    private static void le(ByteArrayOutputStream out, int value, int bytes) {
        for (int i = 0; i < bytes; i++) out.write((value >> (8 * i)) & 0xff);
    }

    private static void text(ByteArrayOutputStream out, String value) {
        byte[] data = value.getBytes(StandardCharsets.ISO_8859_1);
        le(out, data.length, 4);
        out.writeBytes(data);
    }

    /** Notes at ticks 0..length-1, the key of the note at tick t is 33 + t. */
    private static NbsSong song(int length, boolean loop, int loopStart) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        le(out, 0, 2);
        out.write(5);
        out.write(16);
        le(out, length - 1, 2);
        le(out, 1, 2);
        text(out, "Test");
        text(out, "");
        text(out, "");
        text(out, "");
        le(out, 2000, 2);
        out.write(0);
        out.write(10);
        out.write(4);
        for (int i = 0; i < 5; i++) le(out, 0, 4);
        text(out, "");
        out.write(loop ? 1 : 0);
        out.write(0);
        le(out, loopStart, 2);
        for (int tick = 0; tick < length; tick++) {
            le(out, 1, 2);                 // one tick on from the last
            le(out, 1, 2);                 // layer 0
            out.write(0);                  // harp
            out.write(33 + tick);
            out.write(100);
            out.write(100);
            le(out, 0, 2);
            le(out, 0, 2);                 // no more layers on this tick
        }
        le(out, 0, 2);                     // no more ticks
        text(out, "Layer");
        out.write(0);
        out.write(100);
        out.write(100);
        out.write(0);
        return NbsSong.read("test", out.toByteArray());
    }

    private static List<Integer> play(BgmVoice voice, int serverTicks) {
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < serverTicks; i++) voice.tick((instrument, key, volume, pan) -> keys.add(key), 1.0f);
        return keys;
    }

    @Test
    void readsTheSongThatComesWithThePlugin() throws IOException {
        byte[] data;
        try (InputStream in = BgmTest.class.getResourceAsStream("/bgm/plains_adventure.nbs")) {
            assertNotNull(in, "the bundled song is in the jar");
            data = in.readAllBytes();
        }
        NbsSong song = NbsSong.read("plains_adventure", data);
        assertEquals("Under an Endless Sky (Field001)", song.title());
        assertEquals(20.0 / 3.0, song.ticksPerSecond(), 1.0E-9);
        assertEquals(1024, song.length());
        assertEquals(64 * 16, song.length(), "64 bars");
        assertTrue(song.seconds() > 150.0, "a field song is 2:30 or longer");
        assertTrue(song.loops());
        assertEquals(64, song.loopStart());
        assertTrue(song.noteCount() > 1000);
        for (int i = 0; i < song.noteCount(); i++) {
            NbsSong.Note note = song.note(i);
            assertTrue(note.key() >= 33 && note.key() <= 57, "key " + note.key() + " is outside a note block");
            assertTrue(note.instrument() >= 0 && note.instrument() < 16);
            assertTrue(note.volume() > 0.05f && note.volume() <= 1.0f, "volume " + note.volume());
            assertTrue(note.tick() >= 0 && note.tick() < song.length());
            if (i > 0) assertTrue(song.note(i - 1).tick() <= note.tick(), "notes are in order");
        }
        assertEquals(0, song.firstNoteAt(0));
        assertEquals(song.noteCount(), song.firstNoteAt(song.length()));
    }

    @Test
    void theTempoOfTheBundledSongIsADivisorOfTwenty() throws IOException {
        NbsSong song = bundled();
        double serverTicksPerSongTick = 20.0 / song.ticksPerSecond();
        assertEquals(Math.rint(serverTicksPerSongTick), serverTicksPerSongTick, 1.0E-9, "a song tick is a whole number of server ticks");
    }

    @Test
    void theBundledSongKeepsTheNotesTogetherLow() throws IOException {
        NbsSong song = bundled();
        int[] perTick = new int[song.length()];
        for (int i = 0; i < song.noteCount(); i++) perTick[song.note(i).tick()]++;
        int peak = 0;
        for (int count : perTick) peak = Math.max(peak, count);
        assertTrue(peak <= 8, "at most 8 notes at the same time (" + peak + ")");
    }

    @Test
    void theBundledSongLoopsOnAQuietEnd() throws IOException {
        // the last two bars are only the guitar, the harp and the chime: no flute, no bass, no drum (the jump back to the loop point is smooth)
        NbsSong song = bundled();
        int from = song.length() - 32;
        java.util.Set<Integer> instruments = new java.util.TreeSet<>();
        for (int i = song.firstNoteAt(from); i < song.noteCount(); i++) instruments.add(song.note(i).instrument());
        assertTrue(java.util.Set.of(0, 5, 8).containsAll(instruments), "only guitar, harp and chime at the end: " + instruments);
        assertTrue(instruments.contains(5) && instruments.contains(0));
    }

    private static NbsSong bundled() throws IOException {
        return bundled("plains_adventure");
    }

    private static NbsSong bundled(String id) throws IOException {
        try (InputStream in = BgmTest.class.getResourceAsStream("/bgm/" + id + ".nbs")) {
            assertNotNull(in, id);
            return NbsSong.read(id, in.readAllBytes());
        }
    }

    private static final List<String> BUNDLED = List.of("plains_adventure", "atlas_cave", "atlas_vindicator");

    @Test
    void everyBundledSongKeepsTheRules() throws IOException {
        for (String id : BUNDLED) {
            NbsSong song = bundled(id);
            double serverTicks = 20.0 / song.ticksPerSecond();
            assertEquals(Math.rint(serverTicks), serverTicks, 1.0E-9, id + ": the tempo divides 20");
            assertTrue(song.seconds() >= 120.0, id + ": at least two minutes (" + song.seconds() + ")");
            assertTrue(song.loops() && song.loopStart() == 64, id + ": loops from bar 5");
            assertTrue(song.title().matches(".+ \\((Field|Boss)\\d{3}\\)"), id + ": named like 'Title (Field001)': " + song.title());
            int[] perTick = new int[song.length()];
            for (int i = 0; i < song.noteCount(); i++) {
                NbsSong.Note note = song.note(i);
                assertTrue(note.key() >= 33 && note.key() <= 57, id + ": key " + note.key());
                perTick[note.tick()]++;
            }
            int peak = 0;
            for (int count : perTick) peak = Math.max(peak, count);
            assertTrue(peak <= 8, id + ": at most 8 notes at once (" + peak + ")");
        }
    }

    @Test
    void theAtlasSongsAreTheTwoLengthsWeMade() throws IOException {
        assertEquals(768, bundled("atlas_cave").length());
        assertEquals(1280, bundled("atlas_vindicator").length());
        assertEquals("Where the Titan Sleeps (Field002)", bundled("atlas_cave").title());
        assertEquals("The Axe at the Gate (Boss001)", bundled("atlas_vindicator").title());
    }

    @Test
    void bundledSongsAreWrittenAndOnlyUneditedOnesAreReplaced(@TempDir File folder) throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        when(plugin.getResource(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> BgmTest.class.getResourceAsStream("/" + call.getArgument(0, String.class)));
        BgmRegistry registry = new BgmRegistry(plugin);
        registry.reload();
        assertEquals(3, registry.songs().size());
        File cave = new File(new File(folder, "bgm"), "atlas_cave.nbs");
        assertTrue(cave.isFile());
        byte[] bundled = java.nio.file.Files.readAllBytes(cave.toPath());

        // edited by someone (not the recorded version): left alone
        byte[] edited = java.util.Arrays.copyOf(bundled, bundled.length - 1);
        java.nio.file.Files.write(cave.toPath(), edited);
        new BgmRegistry(plugin).reload();
        assertEquals(edited.length, cave.length(), "an edited song stays");

        // an older bundled version that nobody edited (it is the recorded one): replaced by the jar's
        org.bukkit.configuration.file.YamlConfiguration record = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-1").digest(edited);
            StringBuilder hash = new StringBuilder();
            for (byte value : digest) hash.append(String.format("%02x", value));
            record.set("atlas_cave", hash.toString());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        record.save(new File(new File(folder, "bgm"), ".bundled.yml"));
        new BgmRegistry(plugin).reload();
        assertEquals(bundled.length, cave.length(), "an unedited older version is replaced");
    }

    @Test
    void refusesAFileThatIsNotASong() {
        assertThrows(IOException.class, () -> NbsSong.read("x", new byte[]{1, 2, 3}));
        assertThrows(IOException.class, () -> NbsSong.read("x", new byte[0]));
    }

    @Test
    void aSongPlaysOnceAndEnds() throws IOException {
        BgmVoice voice = new BgmVoice(song(4, false, 0), "t", false, 1.0f, 0.0);
        assertEquals(List.of(33, 34, 35, 36), play(voice, 10));
        assertTrue(voice.done());
    }

    @Test
    void aLoopGoesBackToTheLoopPoint() throws IOException {
        BgmVoice voice = new BgmVoice(song(4, true, 2), "t", true, 1.0f, 0.0);
        assertEquals(List.of(33, 34, 35, 36, 35, 36, 35, 36, 35, 36), play(voice, 10));
        assertFalse(voice.done());
    }

    @Test
    void theNotesAreAsLoudAsTheGainsMultiplied() throws IOException {
        BgmVoice voice = new BgmVoice(song(2, false, 0), "t", false, 0.5f, 0.0);
        List<Float> volumes = new ArrayList<>();
        for (int i = 0; i < 4; i++) voice.tick((instrument, key, volume, pan) -> volumes.add(volume), 0.8f);
        assertEquals(2, volumes.size());
        assertEquals(0.4f, volumes.get(0), 1.0E-6f);
    }

    @Test
    void aSongAtZeroLoudnessIsSilentButKeepsItsPlace() throws IOException {
        BgmVoice voice = new BgmVoice(song(4, false, 0), "t", false, 1.0f, 0.0);
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 2; i++) voice.tick((instrument, key, volume, pan) -> keys.add(key), 0.0f);
        for (int i = 0; i < 6; i++) voice.tick((instrument, key, volume, pan) -> keys.add(key), 1.0f);
        assertEquals(List.of(35, 36), keys);
    }

    @Test
    void fadesInAndOut() throws IOException {
        BgmVoice voice = new BgmVoice(song(40, true, 0), "t", true, 1.0f, 1.0);
        assertEquals(0.0f, voice.gain(), 1.0E-6f);
        play(voice, 10);
        assertEquals(0.5f, voice.gain(), 0.01f);
        play(voice, 10);
        assertEquals(1.0f, voice.gain(), 1.0E-6f);
        voice.fadeOut(0.5);
        assertTrue(voice.fadingOut());
        play(voice, 5);
        assertEquals(0.5f, voice.gain(), 0.01f);
        assertFalse(voice.done());
        play(voice, 5);
        assertTrue(voice.done());
        // coming back before it is gone
        BgmVoice back = new BgmVoice(song(40, true, 0), "t", true, 1.0f, 0.0);
        back.fadeOut(1.0);
        play(back, 10);
        back.fadeIn(1.0);
        assertFalse(back.fadingOut());
        play(back, 20);
        assertEquals(1.0f, back.gain(), 1.0E-6f);
    }

    // ---- the boxes ---------------------------------------------------------------------------------------------------------------------

    @Test
    void theBoxThatWinsIsTheOneWithTheHigherPriorityThenTheSmaller(@TempDir File folder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        BgmRegistry registry = new BgmRegistry(plugin);
        BgmVolume big = new BgmVolume("big", "world", new int[]{0, 0, 0}, new int[]{9, 9, 9}, "a");
        BgmVolume small = new BgmVolume("small", "world", new int[]{2, 2, 2}, new int[]{4, 4, 4}, "b");
        registry.put(big);
        registry.put(small);

        Location both = new Location(world, 3.5, 3.5, 3.5);
        Location onlyBig = new Location(world, 8.5, 3.5, 3.5);
        Location outside = new Location(world, 30.5, 3.5, 3.5);
        Location justOutside = new Location(world, 10.5, 3.5, 3.5);

        assertSame(small, registry.at(both, null, 1.5), "equal priority: the smaller box");
        big.setPriority(2);
        assertSame(big, registry.at(both, null, 1.5), "the higher priority");
        assertSame(big, registry.at(onlyBig, null, 1.5));
        assertNull(registry.at(outside, null, 1.5));
        assertNull(registry.at(justOutside, null, 1.5), "not in a box when none was playing");
        assertSame(big, registry.at(justOutside, big, 1.5), "a box that plays is kept a little outside its edge");
        assertNull(registry.at(outside, big, 1.5), "but not far outside");
        assertSame(big, registry.at(both, small, 1.5), "a box that wins takes over");

        World other = mock(World.class);
        when(other.getName()).thenReturn("nether");
        assertNull(registry.at(new Location(other, 3.5, 3.5, 3.5), null, 1.5), "another world");
    }

    @Test
    void theBoxesAreKeptInAFile(@TempDir File folder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        BgmRegistry registry = new BgmRegistry(plugin);
        BgmVolume volume = new BgmVolume("plains", "world", new int[]{10, 60, -5}, new int[]{-10, 90, 5}, "plains_adventure");
        volume.setPriority(3);
        volume.setVolume(70);
        volume.setFade(2.5);
        volume.setLoop(false);
        registry.put(volume);

        BgmRegistry again = new BgmRegistry(plugin);
        again.reload();
        BgmVolume read = again.volume("plains");
        assertNotNull(read);
        assertEquals("world", read.world());
        assertEquals(-10, read.min()[0]);
        assertEquals(10, read.max()[0]);
        assertEquals(3, read.priority());
        assertEquals(70, read.volume());
        assertEquals(2.5, read.fade(), 1.0E-9);
        assertFalse(read.loop());
        assertEquals("plains_adventure", read.song());
    }
}
