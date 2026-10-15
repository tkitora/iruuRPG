package net.tkgon.mc.iruuRPG.dungeon;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * One dungeon: a template world that is copied for every run (the instance), plus where the player enters,
 * which mobs are placed and what clearing it gives. Coordinates are the same in the template and in every copy.
 *
 * @param boss              mob id whose death clears the dungeon (null: every placed mob must die)
 * @param exit              the way out; it can be seen and touched from the start but only works after the clear
 * @param closeDelaySeconds after the clear, everyone still inside is sent home after this many seconds (default 5 min)
 * @param deathMode         REVIVE: a fallen player waits as a spectator until a teammate stands by the body for
 *                          5 seconds (nobody left standing = failed); LIVES: come back at the entry while lives last
 * @param maxPlayers        how many players one run can hold (the first one invites the others)
 * @param rewardRoom        where everyone is taken a few seconds after the clear (null: they stay where they are)
 * @param chest             the reward chest; each player opens it once for gold, XP and an item (loot/&lt;id&gt;.yml)
 * @param bossTrigger       stand here with enough of the party (party size - 1, at least 1) and everyone is taken to
 *                          the boss room
 * @param bossRoom          where the party is taken by the boss trigger
 * @param roadChests        chests along the way ("chests:" in the file), each with its own table loot/&lt;table&gt;.yml;
 *                          each player opens each once per run, also before the boss
 */
public record DungeonDefinition(
        String id,
        String name,
        List<String> description,
        int minLevel,
        int timeLimitSeconds,
        int lives,
        long rewardGold,
        long rewardXp,
        Point entry,
        List<MobSpawn> spawns,
        String boss,
        Point exit,
        int closeDelaySeconds,
        DeathMode deathMode,
        int maxPlayers,
        Point rewardRoom,
        Point chest,
        Point bossTrigger,
        Point bossRoom,
        List<RoadChest> roadChests
) {

    public enum DeathMode { REVIVE, LIVES }

    /** A chest along the way and the name of its loot table (loot/&lt;table&gt;.yml). */
    public record RoadChest(Point at, String table) {
    }

    public record Point(double x, double y, double z, float yaw, float pitch) {

        public Location in(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }

        public static Point of(Location location) {
            return new Point(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }
    }

    public record MobSpawn(String mobId, double x, double y, double z) {

        public Location in(World world) {
            return new Location(world, x, y, z);
        }
    }

    public DungeonDefinition {
        description = description == null ? List.of() : List.copyOf(description);
        spawns = spawns == null ? List.of() : List.copyOf(spawns);
        minLevel = Math.max(1, minLevel);
        timeLimitSeconds = Math.max(30, timeLimitSeconds);
        lives = Math.max(0, lives);
        rewardGold = Math.max(0L, rewardGold);
        rewardXp = Math.max(0L, rewardXp);
        boss = boss == null || boss.isBlank() ? null : boss;
        closeDelaySeconds = Math.max(1, closeDelaySeconds);
        deathMode = deathMode == null ? DeathMode.REVIVE : deathMode;
        maxPlayers = Math.max(1, maxPlayers);
        roadChests = roadChests == null ? List.of() : List.copyOf(roadChests);
    }

    /** The template world that is copied for each run. */
    public String templateWorldName() {
        return "dungeon_template_" + id;
    }

    /** Ready to run: at least one mob is placed, and the boss (if there is one) is among them. */
    public boolean ready() {
        if (spawns.isEmpty()) return false;
        if (boss == null) return true;
        for (MobSpawn spawn : spawns) {
            if (spawn.mobId().equals(boss)) return true;
        }
        return false;
    }

    // ---- copies with one thing changed (admin editing) -------------------------------------------------------------

    public DungeonDefinition withEntry(Point point) {
        return new DungeonDefinition(id, name, description, minLevel, timeLimitSeconds, lives, rewardGold, rewardXp,
                point, spawns, boss, exit, closeDelaySeconds, deathMode, maxPlayers, rewardRoom, chest, bossTrigger, bossRoom, roadChests);
    }

    public DungeonDefinition withSpawns(List<MobSpawn> newSpawns) {
        return new DungeonDefinition(id, name, description, minLevel, timeLimitSeconds, lives, rewardGold, rewardXp,
                entry, newSpawns, boss, exit, closeDelaySeconds, deathMode, maxPlayers, rewardRoom, chest, bossTrigger, bossRoom, roadChests);
    }

    public DungeonDefinition withBoss(String newBoss) {
        return new DungeonDefinition(id, name, description, minLevel, timeLimitSeconds, lives, rewardGold, rewardXp,
                entry, spawns, newBoss, exit, closeDelaySeconds, deathMode, maxPlayers, rewardRoom, chest, bossTrigger, bossRoom, roadChests);
    }

    public DungeonDefinition withExit(Point newExit) {
        return new DungeonDefinition(id, name, description, minLevel, timeLimitSeconds, lives, rewardGold, rewardXp,
                entry, spawns, boss, newExit, closeDelaySeconds, deathMode, maxPlayers, rewardRoom, chest, bossTrigger, bossRoom, roadChests);
    }

    public DungeonDefinition withRewardRoom(Point newRoom) {
        return new DungeonDefinition(id, name, description, minLevel, timeLimitSeconds, lives, rewardGold, rewardXp,
                entry, spawns, boss, exit, closeDelaySeconds, deathMode, maxPlayers, newRoom, chest, bossTrigger, bossRoom, roadChests);
    }

    public DungeonDefinition withChest(Point newChest) {
        return new DungeonDefinition(id, name, description, minLevel, timeLimitSeconds, lives, rewardGold, rewardXp,
                entry, spawns, boss, exit, closeDelaySeconds, deathMode, maxPlayers, rewardRoom, newChest, bossTrigger, bossRoom, roadChests);
    }

    public DungeonDefinition withBossTrigger(Point newTrigger) {
        return new DungeonDefinition(id, name, description, minLevel, timeLimitSeconds, lives, rewardGold, rewardXp,
                entry, spawns, boss, exit, closeDelaySeconds, deathMode, maxPlayers, rewardRoom, chest, newTrigger, bossRoom, roadChests);
    }

    public DungeonDefinition withBossRoom(Point newRoom) {
        return new DungeonDefinition(id, name, description, minLevel, timeLimitSeconds, lives, rewardGold, rewardXp,
                entry, spawns, boss, exit, closeDelaySeconds, deathMode, maxPlayers, rewardRoom, chest, bossTrigger, newRoom, roadChests);
    }

    public DungeonDefinition withRoadChests(List<RoadChest> newChests) {
        return new DungeonDefinition(id, name, description, minLevel, timeLimitSeconds, lives, rewardGold, rewardXp,
                entry, spawns, boss, exit, closeDelaySeconds, deathMode, maxPlayers, rewardRoom, chest, bossTrigger, bossRoom, newChests);
    }

    /** The default table name for a new road chest of this dungeon. */
    public String defaultChestTable() {
        return id + "_road";
    }

    /** The road chest at this block, or -1. */
    public int roadChestAt(int x, int y, int z) {
        for (int index = 0; index < roadChests.size(); index++) {
            Point point = roadChests.get(index).at();
            if ((int) Math.floor(point.x()) == x && (int) Math.floor(point.y()) == y && (int) Math.floor(point.z()) == z) return index;
        }
        return -1;
    }
}
