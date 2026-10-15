package net.tkgon.mc.iruuRPG.dungeon;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * One dungeon: a template world that is copied for every run (the instance), plus where the player enters,
 * which mobs are placed and what clearing it gives. Coordinates are the same in the template and in every copy.
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
        List<MobSpawn> spawns
) {

    public record Point(double x, double y, double z, float yaw, float pitch) {

        public Location in(World world) {
            return new Location(world, x, y, z, yaw, pitch);
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
    }

    /** The template world that is copied for each run. */
    public String templateWorldName() {
        return "dungeon_template_" + id;
    }

    /** Ready to run: at least one mob is placed. */
    public boolean ready() {
        return !spawns.isEmpty();
    }
}
