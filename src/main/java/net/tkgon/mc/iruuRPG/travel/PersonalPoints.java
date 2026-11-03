package net.tkgon.mc.iruuRPG.travel;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * (t254) The teleport points an admin keeps for themselves in the dictionary menu ({@code /iruurpg dic}): a name and a place each, up to {@link #MAX} a
 * player, in {@code <dataFolder>/travel/personal-points.yml} ({@code players.<uuid>: [{name, world, x, y, z, yaw, pitch}]}). Written whole on every change
 * (through a temporary file); a file that cannot be read is left alone and the points start empty, with a warning. Never throws.
 */
public final class PersonalPoints {

    /** As many as one page of the menu shows. */
    public static final int MAX = 45;

    /** One point: its name and where it is. */
    public record Point(String name, String world, double x, double y, double z, float yaw, float pitch) {
    }

    private final File file;
    private final Consumer<String> warn;
    private final Map<UUID, List<Point>> points = new HashMap<>();
    /** The file could not be read: it is not written over, so nothing in it is lost. */
    private boolean broken;

    public PersonalPoints(File dataFolder, Consumer<String> warn) {
        this.file = new File(new File(dataFolder, "travel"), "personal-points.yml");
        this.warn = warn;
        load();
    }

    private void load() {
        points.clear();
        broken = false;
        if (!file.isFile()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException exception) {
            broken = true;
            warn.accept("travel/personal-points.yml could not be read (" + exception.getMessage() + "); the admins' own points start empty and the file is kept");
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            List<Point> list = new ArrayList<>();
            for (Map<?, ?> map : players.getMapList(key)) {
                Point point = point(map);
                if (point != null && list.size() < MAX) list.add(point);
            }
            if (!list.isEmpty()) points.put(id, list);
        }
    }

    private static Point point(Map<?, ?> map) {
        Object name = map.get("name");
        Object world = map.get("world");
        if (name == null || world == null) return null;
        return new Point(String.valueOf(name), String.valueOf(world), number(map.get("x")), number(map.get("y")), number(map.get("z")),
                (float) number(map.get("yaw")), (float) number(map.get("pitch")));
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    /** The points of a player, oldest first (a copy). */
    public List<Point> of(UUID player) {
        return List.copyOf(points.getOrDefault(player, List.of()));
    }

    /** Adds a point; false when the player has {@link #MAX} already. */
    public boolean add(UUID player, Point point) {
        List<Point> list = points.computeIfAbsent(player, ignored -> new ArrayList<>());
        if (list.size() >= MAX) return false;
        list.add(point);
        save();
        return true;
    }

    /** Takes away the point at {@code index}; false when there is none. */
    public boolean remove(UUID player, int index) {
        List<Point> list = points.get(player);
        if (list == null || index < 0 || index >= list.size()) return false;
        list.remove(index);
        if (list.isEmpty()) points.remove(player);
        save();
        return true;
    }

    private void save() {
        if (broken) {
            warn.accept("travel/personal-points.yml was not readable at start: the change is kept until the plugin stops, the file is not written over");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, List<Point>> entry : points.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Point point : entry.getValue()) {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("name", point.name());
                map.put("world", point.world());
                map.put("x", point.x());
                map.put("y", point.y());
                map.put("z", point.z());
                map.put("yaw", (double) point.yaw());
                map.put("pitch", (double) point.pitch());
                list.add(map);
            }
            yaml.set("players." + entry.getKey(), list);
        }
        try {
            File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("cannot make " + folder);
            File temporary = new File(folder, file.getName() + ".tmp");
            yaml.save(temporary);
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException exception) {
            warn.accept("travel/personal-points.yml not saved: " + exception.getMessage());
        }
    }
}
