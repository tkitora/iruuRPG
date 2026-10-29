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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The songs of the folder {@code plugins/iruuRPG/bgm/} (every {@code <id>.nbs}; the id of a song is its file name) and the volumes of {@code bgm/volumes.yml}. The songs that
 * come with the plugin are put in the folder when they are not there yet (delete one to get the bundled one back). Nothing here throws: a song that cannot be read is
 * left out and written to {@link #problems()}.
 */
public final class BgmRegistry {

    /** The songs inside the jar ({@code bgm/<id>.nbs}). */
    private static final List<String> BUNDLED = List.of("plains_adventure", "atlas_cave", "atlas_vindicator");
    /** The SHA-1 of the versions of a bundled song that went out earlier and were never recorded (a file that still is one of them was not edited, so it is replaced). */
    private static final Map<String, List<String>> PREVIOUS = Map.of("plains_adventure", List.of("4258ea7855cdb44c8f2f7ee28d2a77d3a567075d"));

    private final JavaPlugin plugin;
    private volatile Map<String, NbsSong> songs = Map.of();
    private volatile Map<String, BgmVolume> volumes = Map.of();
    private final List<String> problems = new ArrayList<>();

    public BgmRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public File folder() {
        return new File(plugin.getDataFolder(), "bgm");
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

    public Map<String, BgmVolume> volumes() {
        return volumes;
    }

    public BgmVolume volume(String id) {
        return id == null ? null : volumes.get(id.toLowerCase(Locale.ROOT));
    }

    public synchronized List<String> problems() {
        return new ArrayList<>(problems);
    }

    /** Reads the songs and the volumes again. */
    public synchronized void reload() {
        problems.clear();
        File folder = folder();
        if (!folder.exists() && !folder.mkdirs()) problems.add("cannot make the folder " + folder);
        extractBundled();

        Map<String, NbsSong> loaded = new TreeMap<>();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".nbs"));
        if (files != null) {
            for (File file : files) {
                try {
                    NbsSong song = NbsSong.read(file.toPath());
                    loaded.put(song.id().toLowerCase(Locale.ROOT), song);
                } catch (IOException | RuntimeException exception) {
                    problems.add("song " + file.getName() + ": " + exception.getMessage());
                }
            }
        }
        this.songs = loaded;
        this.volumes = loadVolumes();
        for (BgmVolume volume : volumes.values()) {
            if (song(volume.song()) == null) problems.add("volume " + volume.id() + ": there is no song '" + volume.song() + "'");
        }
        for (String problem : problems) plugin.getLogger().warning("bgm: " + problem);
    }

    /**
     * Puts the bundled songs in the folder. A song that is not there is written; one that is there is replaced by a newer bundled version only when nobody has edited it
     * (its SHA-1 is the one recorded when it was written, in {@code bgm/.bundled.yml}, or one of {@link #PREVIOUS}); an edited song is left alone.
     */
    private void extractBundled() {
        File recordFile = new File(folder(), ".bundled.yml");
        YamlConfiguration record = recordFile.isFile() ? YamlConfiguration.loadConfiguration(recordFile) : new YamlConfiguration();
        boolean changed = false;
        for (String id : BUNDLED) {
            File target = new File(folder(), id + ".nbs");
            try (InputStream in = plugin.getResource("bgm/" + id + ".nbs")) {
                if (in == null) continue;
                byte[] bundled = in.readAllBytes();
                String bundledHash = sha1(bundled);
                boolean write = !target.exists();
                if (!write) {
                    String current = sha1(Files.readAllBytes(target.toPath()));
                    if (current.equals(bundledHash)) {
                        if (!bundledHash.equals(record.getString(id))) {
                            record.set(id, bundledHash);
                            changed = true;
                        }
                        continue;
                    }
                    write = current.equals(record.getString(id)) || PREVIOUS.getOrDefault(id, List.of()).contains(current);
                }
                if (write) {
                    Files.write(target.toPath(), bundled);
                    record.set(id, bundledHash);
                    changed = true;
                }
            } catch (IOException | RuntimeException exception) {
                problems.add("cannot put the bundled song " + id + " in the folder: " + exception.getMessage());
            }
        }
        if (changed) {
            try {
                record.save(recordFile);
            } catch (IOException exception) {
                problems.add("cannot write bgm/.bundled.yml: " + exception.getMessage());
            }
        }
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

    private Map<String, BgmVolume> loadVolumes() {
        Map<String, BgmVolume> loaded = new LinkedHashMap<>();
        File file = volumeFile();
        if (!file.isFile()) return loaded;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("volumes");
            if (section == null) return loaded;
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
        } catch (RuntimeException exception) {
            problems.add("volumes.yml: " + exception.getMessage());
        }
        return loaded;
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
