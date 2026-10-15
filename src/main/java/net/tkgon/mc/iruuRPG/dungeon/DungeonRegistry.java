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
        ConfigurationSection exitSection = yaml.getConfigurationSection("exit");
        DungeonDefinition.Point exit = exitSection == null ? null
                : new DungeonDefinition.Point(exitSection.getDouble("x"), exitSection.getDouble("y"), exitSection.getDouble("z"), 0.0f, 0.0f);
        DungeonDefinition.DeathMode deathMode = "lives".equalsIgnoreCase(yaml.getString("death", "revive"))
                ? DungeonDefinition.DeathMode.LIVES : DungeonDefinition.DeathMode.REVIVE;
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
                spawns,
                yaml.getString("boss"),
                exit,
                yaml.getInt("close-delay-seconds", 300),
                deathMode,
                yaml.getInt("max-players", 4),
                point(yaml.getConfigurationSection("reward-room")),
                point(yaml.getConfigurationSection("chest")),
                point(yaml.getConfigurationSection("boss-trigger")),
                point(yaml.getConfigurationSection("boss-room")),
                roadChests(yaml, id)
        );
    }

    private static DungeonDefinition.Point point(ConfigurationSection section) {
        if (section == null) return null;
        return new DungeonDefinition.Point(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0), (float) section.getDouble("pitch", 0.0));
    }

    private static final java.util.regex.Pattern TABLE = java.util.regex.Pattern.compile("[a-z0-9_]{1,40}");

    /** A loot table name: a-z, 0-9, _ (it is a file name under loot/). */
    public static boolean validTable(String table) {
        return table != null && TABLE.matcher(table).matches();
    }

    /**
     * "chests:" entries (x, y, z, yaw, pitch, table). The older "road-chests:" (no table) is read too, with the
     * dungeon's default table &lt;id&gt;_road.
     */
    private List<DungeonDefinition.RoadChest> roadChests(YamlConfiguration yaml, String id) {
        List<DungeonDefinition.RoadChest> chests = new ArrayList<>();
        for (Map<?, ?> entry : yaml.getMapList("chests")) {
            String table = entry.get("table") == null ? id + "_road" : String.valueOf(entry.get("table")).trim().toLowerCase(java.util.Locale.ROOT);
            if (!validTable(table)) {
                plugin.getLogger().warning("dungeon " + id + ": invalid chest table '" + table + "' (use a-z, 0-9, _); using " + id + "_road");
                table = id + "_road";
            }
            chests.add(new DungeonDefinition.RoadChest(pointOf(entry), table));
        }
        for (Map<?, ?> entry : yaml.getMapList("road-chests")) {
            chests.add(new DungeonDefinition.RoadChest(pointOf(entry), id + "_road"));
        }
        return chests;
    }

    private static DungeonDefinition.Point pointOf(Map<?, ?> entry) {
        return new DungeonDefinition.Point(number(entry.get("x")), number(entry.get("y")), number(entry.get("z")),
                (float) number(entry.get("yaw")), (float) number(entry.get("pitch")));
    }

    private static Map<String, Object> pointMap(DungeonDefinition.Point point) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", point.x());
        map.put("y", point.y());
        map.put("z", point.z());
        map.put("yaw", (double) point.yaw());
        map.put("pitch", (double) point.pitch());
        return map;
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
                new DungeonDefinition.Point(0.5, 65.0, 0.5, 0.0f, 0.0f), List.of(), null, null, 300,
                DungeonDefinition.DeathMode.REVIVE, 4, null, null, null, null, List.of());
        put(definition);
        return definition;
    }

    public void setEntry(DungeonDefinition definition, Location location) {
        put(definition.withEntry(DungeonDefinition.Point.of(location)));
    }

    public void addSpawn(DungeonDefinition definition, String mobId, Location location) {
        List<DungeonDefinition.MobSpawn> spawns = new ArrayList<>(definition.spawns());
        spawns.add(new DungeonDefinition.MobSpawn(mobId, location.getX(), location.getY(), location.getZ()));
        put(definition.withSpawns(spawns));
    }

    /** Removes the spawn at {@code index} (0-based); false if there is none. */
    public boolean removeSpawn(DungeonDefinition definition, int index) {
        if (index < 0 || index >= definition.spawns().size()) return false;
        List<DungeonDefinition.MobSpawn> spawns = new ArrayList<>(definition.spawns());
        spawns.remove(index);
        put(definition.withSpawns(spawns));
        return true;
    }

        public void setRewardRoom(DungeonDefinition definition, Location location) {
        put(definition.withRewardRoom(DungeonDefinition.Point.of(location)));
    }

    /** The chest is a block: its position is stored as the block corner. */
    public void setChest(DungeonDefinition definition, Location block) {
        put(definition.withChest(new DungeonDefinition.Point(block.getBlockX(), block.getBlockY(), block.getBlockZ(), 0.0f, 0.0f)));
    }

    public void setBossTrigger(DungeonDefinition definition, Location location) {
        put(definition.withBossTrigger(DungeonDefinition.Point.of(location)));
    }

    public void setBossRoom(DungeonDefinition definition, Location location) {
        put(definition.withBossRoom(DungeonDefinition.Point.of(location)));
    }

    /** Adds a road chest at this block, or changes the table of the one already there. */
    public void setRoadChest(DungeonDefinition definition, Location block, String table) {
        List<DungeonDefinition.RoadChest> chests = new ArrayList<>(definition.roadChests());
        DungeonDefinition.RoadChest chest = new DungeonDefinition.RoadChest(
                new DungeonDefinition.Point(block.getBlockX(), block.getBlockY(), block.getBlockZ(), 0.0f, 0.0f), table);
        int index = definition.roadChestAt(block.getBlockX(), block.getBlockY(), block.getBlockZ());
        if (index >= 0) {
            chests.set(index, chest);
        } else {
            chests.add(chest);
        }
        put(definition.withRoadChests(chests));
    }

    public boolean removeRoadChest(DungeonDefinition definition, int index) {
        if (index < 0 || index >= definition.roadChests().size()) return false;
        List<DungeonDefinition.RoadChest> chests = new ArrayList<>(definition.roadChests());
        chests.remove(index);
        put(definition.withRoadChests(chests));
        return true;
    }

    public void clearSpawns(DungeonDefinition definition) {
        put(definition.withSpawns(List.of()));
    }

    public void setBoss(DungeonDefinition definition, String mobId) {
        put(definition.withBoss(mobId));
    }

    public void setExit(DungeonDefinition definition, Location location) {
        put(definition.withExit(DungeonDefinition.Point.of(location)));
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
        yaml.set("boss", d.boss());
        if (d.exit() != null) {
            Map<String, Object> exit = new LinkedHashMap<>();
            exit.put("x", d.exit().x());
            exit.put("y", d.exit().y());
            exit.put("z", d.exit().z());
            yaml.set("exit", exit);
        }
        yaml.set("close-delay-seconds", d.closeDelaySeconds());
        yaml.set("death", d.deathMode() == DungeonDefinition.DeathMode.LIVES ? "lives" : "revive");
        yaml.set("max-players", d.maxPlayers());
        if (d.rewardRoom() != null) yaml.set("reward-room", pointMap(d.rewardRoom()));
        if (d.chest() != null) yaml.set("chest", pointMap(d.chest()));
        if (d.bossTrigger() != null) yaml.set("boss-trigger", pointMap(d.bossTrigger()));
        if (d.bossRoom() != null) yaml.set("boss-room", pointMap(d.bossRoom()));
        List<Map<String, Object>> chests = new ArrayList<>();
        for (DungeonDefinition.RoadChest chest : d.roadChests()) {
            Map<String, Object> map = pointMap(chest.at());
            map.put("table", chest.table());
            chests.add(map);
        }
        if (!chests.isEmpty()) yaml.set("chests", chests);
        File file = new File(folder(), d.id() + ".yml");
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("dungeon: could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
