package net.tkgon.mc.iruuRPG.dungeon;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The reward chest of a dungeon (loot/&lt;id&gt;.yml): a random amount of gold, a random amount of XP, and one item picked
 * by weight from a table (rare entries can be announced to the whole server).
 */
public record DungeonLoot(long goldMin, long goldMax, long xpMin, long xpMax, List<Entry> items) {

    public record Entry(String itemId, int weight, int minAmount, int maxAmount, boolean announce) {
    }

    /** One roll of the chest. {@code item} is null when the table is empty. */
    public record Roll(long gold, long xp, Entry item, int amount) {
    }

    public Roll roll() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long gold = goldMax <= goldMin ? goldMin : random.nextLong(goldMin, goldMax + 1);
        long xp = xpMax <= xpMin ? xpMin : random.nextLong(xpMin, xpMax + 1);
        int total = 0;
        for (Entry entry : items) total += Math.max(0, entry.weight());
        if (total <= 0) return new Roll(gold, xp, null, 0);
        int pick = random.nextInt(total);
        for (Entry entry : items) {
            pick -= Math.max(0, entry.weight());
            if (pick < 0) {
                int amount = entry.maxAmount() <= entry.minAmount() ? entry.minAmount() : random.nextInt(entry.minAmount(), entry.maxAmount() + 1);
                return new Roll(gold, xp, entry, amount);
            }
        }
        return new Roll(gold, xp, null, 0);
    }

    /** loot/&lt;id&gt;.yml, or a table built from the dungeon's own gold/XP (no item) when there is no file. */
    public static DungeonLoot load(JavaPlugin plugin, DungeonDefinition definition) {
        File file = new File(plugin.getDataFolder(), "loot/" + definition.id() + ".yml");
        long gold = definition.rewardGold();
        long xp = definition.rewardXp();
        if (!file.exists()) return new DungeonLoot(gold, gold + gold / 2, xp, xp + xp / 2, List.of());
        return fromSection(YamlConfiguration.loadConfiguration(file), gold, xp);
    }

    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** No gold, no XP, no item: what a chest gives when its table is missing. */
    public boolean empty() {
        return goldMax <= 0 && xpMax <= 0 && items.isEmpty();
    }

    /**
     * A road chest's table, loot/&lt;table&gt;.yml (gold / xp default to 0). For the default table &lt;id&gt;_road, an older
     * "road:" section of loot/&lt;id&gt;.yml is used when the file is missing. A missing table is warned about once and
     * gives nothing (the chest shows as empty).
     */
    public static DungeonLoot loadTable(JavaPlugin plugin, String table, DungeonDefinition definition) {
        File file = new File(plugin.getDataFolder(), "loot/" + table + ".yml");
        if (DungeonRegistry.validTable(table) && file.exists()) {
            return fromSection(YamlConfiguration.loadConfiguration(file), 0, 0);
        }
        if (table.equals(definition.defaultChestTable())) {
            File legacy = new File(plugin.getDataFolder(), "loot/" + definition.id() + ".yml");
            ConfigurationSection road = legacy.exists() ? YamlConfiguration.loadConfiguration(legacy).getConfigurationSection("road") : null;
            if (road != null) return fromSection(road, 0, 0);
        }
        if (WARNED.add(table)) {
            plugin.getLogger().warning("dungeon loot: table '" + table + "' (loot/" + table + ".yml) is missing; that chest gives nothing");
        }
        return new DungeonLoot(0, 0, 0, 0, List.of());
    }

    /** The names of the loot tables in loot/ (file names without .yml), sorted. */
    public static List<String> tableNames(JavaPlugin plugin) {
        File[] files = new File(plugin.getDataFolder(), "loot").listFiles((dir, name) -> name.endsWith(".yml"));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 4));
        }
        names.sort(String::compareTo);
        return names;
    }

    private static DungeonLoot fromSection(ConfigurationSection section, long fallbackGold, long fallbackXp) {
        long[] gold = range(section.get("gold"), fallbackGold);
        long[] xp = range(section.get("xp"), fallbackXp);
        List<Entry> entries = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("items")) {
            Object item = raw.get("item");
            if (item == null) continue;
            long[] amount = range(raw.get("amount"), 1);
            int weight = raw.get("weight") instanceof Number number ? number.intValue() : 1;
            boolean announce = Boolean.TRUE.equals(raw.get("announce"));
            entries.add(new Entry(String.valueOf(item), weight, (int) Math.max(1, amount[0]), (int) Math.max(1, amount[1]), announce));
        }
        return new DungeonLoot(gold[0], gold[1], xp[0], xp[1], entries);
    }

    /** [min, max] from "[a, b]" or a single number; the fallback when missing. */
    private static long[] range(Object raw, long fallback) {
        if (raw instanceof Number number) return new long[]{number.longValue(), number.longValue()};
        if (raw instanceof List<?> list && list.size() >= 2 && list.get(0) instanceof Number a && list.get(1) instanceof Number b) {
            return new long[]{Math.min(a.longValue(), b.longValue()), Math.max(a.longValue(), b.longValue())};
        }
        return new long[]{fallback, fallback};
    }
}
