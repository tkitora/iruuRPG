package net.tkgon.mc.iruuRPG.dungeon;

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
        if (!file.exists()) {
            long gold = definition.rewardGold();
            long xp = definition.rewardXp();
            return new DungeonLoot(gold, gold + gold / 2, xp, xp + xp / 2, List.of());
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long[] gold = range(yaml.get("gold"), definition.rewardGold());
        long[] xp = range(yaml.get("xp"), definition.rewardXp());
        List<Entry> entries = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList("items")) {
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
