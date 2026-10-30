package net.tkgon.mc.iruuRPG.bgm;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The songs and the volumes. Songs come from two folders: {@code plugins/iruuRPG/bgm-bundled/} (the songs inside the jar, written at every start) and
 * {@code plugins/iruuRPG/bgm/} (every {@code <id>.nbs}; the folder of the yml repository and of the admins). The id of a song is its file name, and a song of {@code bgm/} replaces
 * the bundled one of the same id. The volumes are in {@code bgm/volumes.yml}. {@link #refreshIfChanged()} sees a file that came, changed or went (a pull of the yml repository, a
 * file put there by hand) and reads again, so songs are updated without a restart. Nothing here throws: a song that cannot be read is left out and written to {@link #problems()}.
 */
public final class BgmRegistry {

    /** The songs inside the jar ({@code bgm/<id>.nbs}). */
    private static final List<String> BUNDLED = List.of("plains_adventure", "atlas_cave", "atlas_vindicator", "atlas_dungeon", "atlas_dungeon_2", "atlas_vindicator_2", "cave_depths", "colossus_phase1", "colossus_phase2", "necromancer_phase1", "necromancer_phase2");

    /** A song read from a file; kept while the file is unchanged, so a reload does not make a playing song start over. */
    private record Read(long length, long modified, NbsSong song) {
    }

    private final JavaPlugin plugin;
    private volatile Map<String, NbsSong> songs = Map.of();
    private volatile Map<String, String> origins = Map.of();
    private volatile Map<String, BgmVolume> volumes = Map.of();
    private final List<String> problems = new ArrayList<>();
    private final Map<String, Read> read = new HashMap<>();
    private String songStamp = "";
    private String volumeStamp = "";

    public BgmRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** The folder of the yml repository and of the admins. */
    public File folder() {
        return new File(plugin.getDataFolder(), "bgm");
    }

    /** The songs of the jar. */
    public File bundledFolder() {
        return new File(plugin.getDataFolder(), "bgm-bundled");
    }

    private File volumeFile() {
        return new File(folder(), "volumes.yml");
    }

    public Map<String, NbsSong> songs() {
        return songs;
    }

    public NbsSong song(String id) {
        return id == null ? null : songs.get(id.toLowerCase(Locale.ROOT));
    }

    /** Where a song comes from: {@code "bgm"} (the folder) or {@code "bundled"} (the jar). */
    public String origin(String id) {
        return origins.getOrDefault(id.toLowerCase(Locale.ROOT), "?");
    }

    public Map<String, BgmVolume> volumes() {
        return volumes;
    }

    public BgmVolume volume(String id) {
        return id == null ? null : volumes.get(id.toLowerCase(Locale.ROOT));
    }

    public synchronized List<String> problems() {
        return new ArrayList<>(problems);
    }

    // ---- reading ---------------------------------------------------------------------------------------------------------------------

    /** Reads the songs and the volumes again (writes the bundled songs first). Returns what changed in the songs, in one line ("" when nothing). */
    public synchronized String reload() {
        problems.clear();
        File folder = folder();
        if (!folder.exists() && !folder.mkdirs()) problems.add("cannot make the folder " + folder);
        Map<String, byte[]> bundled = bundledBytes();
        migrateOldCopies(bundled);
        writeBundled(bundled);
        readVolumes();
        String changes = readSongs();
        logProblems();
        return changes;
    }

    /**
     * Reads again what changed on disk since the last read: the songs when a {@code .nbs} came, changed or went (in either folder), the volumes when {@code volumes.yml} changed.
     * Returns a line saying what changed, or null when nothing did.
     */
    public synchronized String refreshIfChanged() {
        String songsNow = songStamp();
        String volumesNow = volumeStamp();
        boolean songsChanged = !songsNow.equals(songStamp);
        boolean volumesChanged = !volumesNow.equals(volumeStamp);
        if (!songsChanged && !volumesChanged) return null;
        problems.clear();
        if (volumesChanged) readVolumes();
        String changes = songsChanged ? readSongs() : "";
        logProblems();
        StringBuilder text = new StringBuilder();
        if (songsChanged) text.append("songs ").append(changes.isEmpty() ? "(the list is the same)" : changes);
        if (volumesChanged) text.append(text.length() > 0 ? "; " : "").append("volumes.yml: ").append(volumes.size()).append(" volume(s)");
        return text.toString();
    }

    private void logProblems() {
        for (String problem : problems) plugin.getLogger().warning("bgm: " + problem);
    }

    private String readSongs() {
        Map<String, NbsSong> loaded = new TreeMap<>();
        Map<String, String> from = new HashMap<>();
        Map<String, Read> kept = new HashMap<>();
        readFolder(bundledFolder(), "bundled", loaded, from, kept);
        readFolder(folder(), "bgm", loaded, from, kept); // (replaces a bundled song of the same id)
        read.clear();
        read.putAll(kept);
        Map<String, NbsSong> before = this.songs;
        this.songs = loaded;
        this.origins = from;
        this.songStamp = songStamp();
        for (BgmVolume volume : volumes.values()) {
            if (song(volume.song()) == null) problems.add("volume " + volume.id() + ": there is no song '" + volume.song() + "'");
        }
        return describe(before, loaded);
    }

    private void readFolder(File folder, String origin, Map<String, NbsSong> into, Map<String, String> from, Map<String, Read> kept) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".nbs"));
        if (files == null) return;
        Arrays.sort(files);
        for (File file : files) {
            try {
                Read earlier = read.get(file.getAbsolutePath());
                Read now = earlier != null && earlier.length() == file.length() && earlier.modified() == file.lastModified()
                        ? earlier : new Read(file.length(), file.lastModified(), NbsSong.read(file.toPath()));
                kept.put(file.getAbsolutePath(), now);
                String id = now.song().id().toLowerCase(Locale.ROOT);
                into.put(id, now.song());
                from.put(id, origin);
            } catch (IOException | RuntimeException exception) {
                problems.add("song " + file.getName() + ": " + exception.getMessage());
            }
        }
    }

    /** "+new ~changed -gone", for the songs whose list or file changed. */
    private static String describe(Map<String, NbsSong> before, Map<String, NbsSong> after) {
        StringBuilder text = new StringBuilder();
        for (String id : new TreeSet<>(after.keySet())) {
            if (!before.containsKey(id)) text.append(text.length() > 0 ? " " : "").append('+').append(id);
            else if (before.get(id) != after.get(id)) text.append(text.length() > 0 ? " " : "").append('~').append(id);
        }
        for (String id : new TreeSet<>(before.keySet())) {
            if (!after.containsKey(id)) text.append(text.length() > 0 ? " " : "").append('-').append(id);
        }
        return text.toString();
    }

    /** Names, sizes and times of the song files of both folders: when it changes, a song came, changed or went. */
    private String songStamp() {
        StringBuilder text = new StringBuilder();
        for (File folder : new File[]{bundledFolder(), folder()}) {
            File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".nbs"));
            if (files == null) continue;
            Arrays.sort(files);
            for (File file : files) {
                text.append(file.getParentFile().getName()).append('/').append(file.getName()).append(':').append(file.length()).append(':').append(file.lastModified()).append(';');
            }
        }
        return text.toString();
    }

    private String volumeStamp() {
        File file = volumeFile();
        return file.isFile() ? file.length() + ":" + file.lastModified() : "none";
    }

    // ---- the songs of the jar ----------------------------------------------------------------------------------------------------------

    private Map<String, byte[]> bundledBytes() {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String id : BUNDLED) {
            try (InputStream in = plugin.getResource("bgm/" + id + ".nbs")) {
                if (in != null) result.put(id, in.readAllBytes());
            } catch (IOException exception) {
                problems.add("cannot read the bundled song " + id + ": " + exception.getMessage());
            }
        }
        return result;
    }

    /** Writes the songs of the jar to {@code bgm-bundled/} (only those that differ). */
    private void writeBundled(Map<String, byte[]> bundled) {
        File folder = bundledFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            problems.add("cannot make the folder " + folder);
            return;
        }
        for (Map.Entry<String, byte[]> entry : bundled.entrySet()) {
            File target = new File(folder, entry.getKey() + ".nbs");
            try {
                if (target.isFile() && Arrays.equals(Files.readAllBytes(target.toPath()), entry.getValue())) continue;
                Files.write(target.toPath(), entry.getValue());
            } catch (IOException exception) {
                problems.add("cannot put the bundled song " + entry.getKey() + " in the folder: " + exception.getMessage());
            }
        }
    }

    /**
     * Before the songs of the jar had a folder of their own they were written to {@code bgm/}, where a song of the yml repository with the same name would have stopped its
     * {@code git pull}. A copy there that still is the bundled song (or the one recorded in {@code bgm/.bundled.yml}) is taken away; an edited one stays and replaces the bundled song.
     */
    private void migrateOldCopies(Map<String, byte[]> bundled) {
        File record = new File(folder(), ".bundled.yml");
        YamlConfiguration recorded = record.isFile() ? YamlConfiguration.loadConfiguration(record) : new YamlConfiguration();
        for (Map.Entry<String, byte[]> entry : bundled.entrySet()) {
            File old = new File(folder(), entry.getKey() + ".nbs");
            if (!old.isFile()) continue;
            try {
                String hash = sha1(Files.readAllBytes(old.toPath()));
                boolean untouched = hash.equals(sha1(entry.getValue())) || hash.equals(recorded.getString(entry.getKey()));
                if (untouched && !old.delete()) problems.add("cannot take away the old copy " + old.getName());
            } catch (IOException exception) {
                problems.add("cannot check the old copy " + old.getName() + ": " + exception.getMessage());
            }
        }
        if (record.isFile() && !record.delete()) problems.add("cannot take away bgm/.bundled.yml");
    }

    private static String sha1(byte[] data) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-1").digest(data);
            StringBuilder text = new StringBuilder();
            for (byte value : digest) text.append(String.format("%02x", value));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ---- the volumes -------------------------------------------------------------------------------------------------------------------

    private void readVolumes() {
        Map<String, BgmVolume> loaded = new LinkedHashMap<>();
        File file = volumeFile();
        if (file.isFile()) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection section = yaml.getConfigurationSection("volumes");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        ConfigurationSection entry = section.getConfigurationSection(key);
                        if (entry == null) continue;
                        List<Integer> min = entry.getIntegerList("min");
                        List<Integer> max = entry.getIntegerList("max");
                        String world = entry.getString("world", "");
                        if (min.size() != 3 || max.size() != 3 || world.isBlank()) {
                            problems.add("volume " + key + ": needs world, min and max (three numbers each)");
                            continue;
                        }
                        BgmVolume volume = new BgmVolume(key.toLowerCase(Locale.ROOT), world, new int[]{min.get(0), min.get(1), min.get(2)},
                                new int[]{max.get(0), max.get(1), max.get(2)}, entry.getString("song", "").toLowerCase(Locale.ROOT));
                        volume.setPriority(entry.getInt("priority", 0));
                        volume.setVolume(entry.getInt("volume", 100));
                        volume.setFade(entry.getDouble("fade", 3.0));
                        volume.setLoop(entry.getBoolean("loop", true));
                        loaded.put(volume.id(), volume);
                    }
                }
            } catch (RuntimeException exception) {
                problems.add("volumes.yml: " + exception.getMessage());
            }
        }
        this.volumes = loaded;
        this.volumeStamp = volumeStamp();
    }

    /** Adds or replaces a volume and saves the file. */
    public synchronized void put(BgmVolume volume) {
        Map<String, BgmVolume> copy = new LinkedHashMap<>(volumes);
        copy.put(volume.id(), volume);
        this.volumes = copy;
        save();
    }

    public synchronized boolean remove(String id) {
        Map<String, BgmVolume> copy = new LinkedHashMap<>(volumes);
        if (copy.remove(id.toLowerCase(Locale.ROOT)) == null) return false;
        this.volumes = copy;
        save();
        return true;
    }

    /** Writes volumes.yml (call after changing a volume that is already in the registry). */
    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (BgmVolume volume : volumes.values()) {
            String path = "volumes." + volume.id();
            yaml.set(path + ".world", volume.world());
            yaml.set(path + ".min", toList(volume.min()));
            yaml.set(path + ".max", toList(volume.max()));
            yaml.set(path + ".song", volume.song());
            yaml.set(path + ".priority", volume.priority());
            yaml.set(path + ".volume", volume.volume());
            yaml.set(path + ".fade", volume.fade());
            yaml.set(path + ".loop", volume.loop());
        }
        try {
            File folder = folder();
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("cannot make " + folder);
            yaml.save(volumeFile());
            this.volumeStamp = volumeStamp(); // (our own write is not a change from outside)
        } catch (IOException exception) {
            plugin.getLogger().warning("bgm: cannot save volumes.yml: " + exception.getMessage());
        }
    }

    private static List<Integer> toList(int[] values) {
        return List.of(values[0], values[1], values[2]);
    }

    /**
     * The volume whose song a player at {@code at} hears. A volume that is already playing is kept a little way outside its edge (a player on the border does not make
     * the song start over and over), unless another volume that wins has been entered.
     */
    public BgmVolume at(Location at, BgmVolume current, double margin) {
        BgmVolume best = null;
        for (BgmVolume volume : volumes.values()) {
            if (!volume.contains(at)) continue;
            if (best == null || best.beatenBy(volume)) best = volume;
        }
        if (best == null && current != null && volumes.get(current.id()) == current && current.contains(at, margin)) return current;
        if (best != null && current != null && best != current && volumes.get(current.id()) == current && current.contains(at, margin) && !current.beatenBy(best)) return current;
        return best;
    }

    /** The volumes sorted for a list: by world, then by id. */
    public List<BgmVolume> sorted() {
        List<BgmVolume> list = new ArrayList<>(volumes.values());
        list.sort(Comparator.comparing(BgmVolume::world).thenComparing(BgmVolume::id));
        return list;
    }
}
