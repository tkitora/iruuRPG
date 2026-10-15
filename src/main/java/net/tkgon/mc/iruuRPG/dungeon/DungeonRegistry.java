package net.tkgon.mc.iruuRPG.dungeon;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Loads and saves dungeons/*.yml. */
public final class DungeonRegistry {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,24}");

    private final JavaPlugin plugin;
    private final Map<String, DungeonDefinition> definitions = new LinkedHashMap<>();

    public DungeonRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    private File folder() {
        return new File(plugin.getDataFolder(), "dungeons");
    }

    public void reload() {
        definitions.clear();
        File folder = folder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create dungeons folder: " + folder.getAbsolutePath());
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            try {
                plugin.saveResource("dungeons/cave.yml", false);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Missing bundled dungeon resource: dungeons/cave.yml");
            }
            files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        }
        if (files == null) return;

        for (File file : files) {
            try {
                DungeonDefinition definition = read(YamlConfiguration.loadConfiguration(file), file.getName());
                if (definition != null) definitions.put(definition.id(), definition);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("dungeon: could not read " + file.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + definitions.size() + " dungeon definitions.");
    }

    private DungeonDefinition read(YamlConfiguration yaml, String fileName) {
        String id = yaml.getString("id", fileName.replace(".yml", "")).toLowerCase();
        if (!validId(id)) {
            plugin.getLogger().warning("dungeon: invalid id '" + id + "' in " + fileName + " (use a-z, 0-9, _)");
            return null;
        }
        ConfigurationSection entry = yaml.getConfigurationSection("entry");
        DungeonDefinition.Point point = entry == null
                ? new DungeonDefinition.Point(0.5, 65.0, 0.5, 0.0f, 0.0f)
                : new DungeonDefinition.Point(entry.getDouble("x", 0.5), entry.getDouble("y", 65.0), entry.getDouble("z", 0.5),
                (float) entry.getDouble("yaw", 0.0), (float) entry.getDouble("pitch", 0.0));
        List<DungeonDefinition.MobSpawn> spawns = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList("spawns")) {
            Object mob = raw.get("mob");
            if (mob == null) continue;
            spawns.add(new DungeonDefinition.MobSpawn(String.valueOf(mob), number(raw.get("x")), number(raw.get("y")), number(raw.get("z"))));
        }
        return new DungeonDefinition(
                id,
                yaml.getString("name", id),
                yaml.getStringList("description"),
                yaml.getInt("min-level", 1),
                yaml.getInt("time-limit-seconds", 900),
                yaml.getInt("lives", 3),
                yaml.getLong("reward.gold", 0L),
                yaml.getLong("reward.xp", 0L),
                point,
                spawns
        );
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    public Optional<DungeonDefinition> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(definitions.get(id.toLowerCase()));
    }

    public List<DungeonDefinition> all() {
        return List.copyOf(definitions.values());
    }

    // ---- editing (admin commands) ----------------------------------------------------------------------------------

    public DungeonDefinition create(String id, String name) {
        DungeonDefinition definition = new DungeonDefinition(id, name, List.of(), 1, 900, 3, 0L, 0L,
                new DungeonDefinition.Point(0.5, 65.0, 0.5, 0.0f, 0.0f), List.of());
        put(definition);
        return definition;
    }

    public void setEntry(DungeonDefinition definition, Location location) {
        put(new DungeonDefinition(definition.id(), definition.name(), definition.description(), definition.minLevel(),
                definition.timeLimitSeconds(), definition.lives(), definition.rewardGold(), definition.rewardXp(),
                new DungeonDefinition.Point(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()),
                definition.spawns()));
    }

    public void addSpawn(DungeonDefinition definition, String mobId, Location location) {
        List<DungeonDefinition.MobSpawn> spawns = new ArrayList<>(definition.spawns());
        spawns.add(new DungeonDefinition.MobSpawn(mobId, location.getX(), location.getY(), location.getZ()));
        put(withSpawns(definition, spawns));
    }

    public void clearSpawns(DungeonDefinition definition) {
        put(withSpawns(definition, List.of()));
    }

    private DungeonDefinition withSpawns(DungeonDefinition d, List<DungeonDefinition.MobSpawn> spawns) {
        return new DungeonDefinition(d.id(), d.name(), d.description(), d.minLevel(), d.timeLimitSeconds(), d.lives(),
                d.rewardGold(), d.rewardXp(), d.entry(), spawns);
    }

    private void put(DungeonDefinition definition) {
        definitions.put(definition.id(), definition);
        save(definition);
    }

    private void save(DungeonDefinition d) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", d.id());
        yaml.set("name", d.name());
        yaml.set("description", d.description());
        yaml.set("min-level", d.minLevel());
        yaml.set("time-limit-seconds", d.timeLimitSeconds());
        yaml.set("lives", d.lives());
        yaml.set("reward.gold", d.rewardGold());
        yaml.set("reward.xp", d.rewardXp());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("x", d.entry().x());
        entry.put("y", d.entry().y());
        entry.put("z", d.entry().z());
        entry.put("yaw", (double) d.entry().yaw());
        entry.put("pitch", (double) d.entry().pitch());
        yaml.set("entry", entry);
        List<Map<String, Object>> spawns = new ArrayList<>();
        for (DungeonDefinition.MobSpawn spawn : d.spawns()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("mob", spawn.mobId());
            map.put("x", spawn.x());
            map.put("y", spawn.y());
            map.put("z", spawn.z());
            spawns.add(map);
        }
        yaml.set("spawns", spawns);
        File file = new File(folder(), d.id() + ".yml");
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("dungeon: could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
