package net.tkgon.mc.iruuRPG.spawner;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One place in the open field where a mob appears when a player comes close (see {@link FieldSpawnerService}). */
public final class FieldSpawner {

    private final String id;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private String mobId;
    /** How many appear at once: a random number from {@code min} to {@code max}. */
    private int min;
    private int max;
    /** How many of its mobs can be alive at the same time. */
    private int maxAlive;
    /** A player this close (blocks) makes it spawn. */
    private int radius;

    /** The mobs alive now, with the time (millis) each appeared. Not saved. */
    final Map<UUID, Long> alive = new HashMap<>();
    long nextSpawnAt;

    public FieldSpawner(String id, String worldName, double x, double y, double z, String mobId, int min, int max, int maxAlive, int radius) {
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.mobId = mobId;
        this.min = min;
        this.max = max;
        this.maxAlive = maxAlive;
        this.radius = radius;
    }

    public String id() {
        return id;
    }

    public String worldName() {
        return worldName;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public String mobId() {
        return mobId;
    }

    public void mobId(String mobId) {
        this.mobId = mobId;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int maxAlive() {
        return maxAlive;
    }

    public int radius() {
        return radius;
    }

    public void min(int value) {
        min = Math.max(1, Math.min(10, value));
        if (max < min) max = min;
    }

    public void max(int value) {
        max = Math.max(1, Math.min(10, value));
        if (min > max) min = max;
    }

    public void maxAlive(int value) {
        maxAlive = Math.max(1, Math.min(20, value));
    }

    public void radius(int value) {
        radius = Math.max(8, Math.min(64, value));
    }

    public int aliveCount() {
        return alive.size();
    }

    public Location in(World world) {
        return new Location(world, x, y, z);
    }
}
