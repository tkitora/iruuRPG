package net.tkgon.mc.iruuRPG.dungeon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A group of blocks in a dungeon that all vanish together when its condition is met during a run: a door to a side
 * path or to the next part of the dungeon. A dungeon can have several groups, each with its own condition; the blocks
 * of one group can be in more than one place.
 *
 * @param when   what opens it
 * @param count  KILLS: how many mobs (not the boss) must be dead; GATHER: how many players must stand by it (never more
 *               than the players who can stand)
 * @param target TARGET: the placed mob (0-based spawn index) that must be killed; -1 = not chosen. If that mob does not
 *               appear in a run (a mob with a chance), the wall stays closed in that run.
 * @param blocks the blocks that vanish
 */
public record DungeonWall(Condition when, int count, int target, List<Pos> blocks) {

    /** Players standing within this many blocks of any of the wall's blocks count as gathered. */
    public static final double GATHER_RANGE = 3.0;

    public enum Condition {
        KILLS("敵を倒す"),
        GATHER("人が集まる"),
        TARGET("指定の1体を倒す");

        private final String label;

        Condition(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** The name in the file. */
        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Condition of(String key) {
            if (key != null) {
                for (Condition condition : values()) {
                    if (condition.key().equalsIgnoreCase(key.trim())) return condition;
                }
            }
            return null;
        }
    }

    public record Pos(int x, int y, int z) {
    }

    public DungeonWall {
        when = when == null ? Condition.KILLS : when;
        count = Math.max(1, Math.min(999, count));
        target = Math.max(-1, target);
        blocks = blocks == null ? List.of() : List.copyOf(new LinkedHashSet<>(blocks));
    }

    public DungeonWall withWhen(Condition newWhen) {
        return new DungeonWall(newWhen, count, target, blocks);
    }

    public DungeonWall withCount(int newCount) {
        return new DungeonWall(when, newCount, target, blocks);
    }

    public DungeonWall withTarget(int newTarget) {
        return new DungeonWall(when, count, newTarget, blocks);
    }

    public DungeonWall withBlocks(List<Pos> newBlocks) {
        return new DungeonWall(when, count, target, newBlocks);
    }

    public boolean has(Pos pos) {
        return blocks.contains(pos);
    }

    /** The same wall with this block added, or taken out if it was there. */
    public DungeonWall toggle(Pos pos) {
        List<Pos> list = new ArrayList<>(blocks);
        if (!list.remove(pos)) list.add(pos);
        return withBlocks(list);
    }

    /** The wall after the placed mob {@code removed} (0-based) was deleted: later mobs move up by one. */
    public DungeonWall afterSpawnRemoved(int removed) {
        if (target < 0 || target < removed) return this;
        return withTarget(target == removed ? -1 : target - 1);
    }

    /**
     * Whether the wall opens now.
     *
     * @param killed     mobs (not the boss) killed in this run so far
     * @param gathered   players standing by the wall
     * @param standing   players in the run who could stand by it (not fallen)
     * @param targetDead the target mob appeared in this run and is dead
     */
    public boolean met(int killed, int gathered, int standing, boolean targetDead) {
        return switch (when) {
            case KILLS -> killed >= count;
            case GATHER -> gathered >= needed(standing);
            case TARGET -> targetDead;
        };
    }

    /** GATHER: how many must stand by it when this many can (at least 1). */
    public int needed(int standing) {
        return Math.max(1, Math.min(count, standing));
    }

    /** The condition in a few words, for labels and menus. */
    public String describe(String targetName) {
        return switch (when) {
            case KILLS -> "敵を " + count + " 体倒す";
            case GATHER -> count + " 人で集まる";
            case TARGET -> target < 0 ? "倒す1体が未設定" : "#" + (target + 1) + " " + targetName + " を倒す";
        };
    }

    /**
     * Kills needed before the boss room opens: the share of the mobs that always appear (rounded up), plus one for
     * each mob that appeared by chance in this run.
     */
    public static int requiredKills(int trashTotal, int byChance, double share) {
        int always = Math.max(0, trashTotal - byChance);
        return (int) Math.ceil(always * share - 1.0E-9) + Math.max(0, byChance);
    }

    /** Rolls a placed mob's chance (0-99 from the random source). */
    public static boolean appears(int chance, int roll) {
        return chance >= 100 || roll < chance;
    }
}
