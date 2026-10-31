package net.tkgon.mc.iruuRPG.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * One NPC of npcs.yml (written by /npc edit, not by the yml repository): where it stands, its name over its head, what it
 * looks like, and what it says when no quest has anything for it.
 */
public final class Npc {

    /**
     * What an NPC looks like: a villager (profession, biome type), any living entity type, or a model of the model
     * feature (on a hidden villager).
     */
    public record Look(String type, String profession, String villagerType, String entity, boolean baby, String model, double scale) {

        public static Look villager(String profession, String villagerType) {
            return new Look("villager", profession, villagerType, null, false, null, 1.0);
        }

        public static Look entity(String entity) {
            return new Look("entity", null, null, entity, false, null, 1.0);
        }

        public static Look model(String model, double scale) {
            return new Look("model", null, null, null, false, model, scale);
        }

        public String describe() {
            return switch (type) {
                case "entity" -> entity + (baby ? " (子ども)" : "");
                case "model" -> "モデル " + model + (scale != 1.0 ? " ×" + scale : "");
                default -> "村人 " + profession + " / " + villagerType;
            };
        }
    }

    private final String id;
    private String name;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private Look look;
    private final List<String> idle = new ArrayList<>();
    /** The quest lines this NPC offers besides those whose start.npc names it (chosen in /npc edit). */
    private final List<String> quests = new ArrayList<>();

    public Npc(String id, String name, Location at, Look look) {
        this.id = id;
        this.name = name;
        this.look = look;
        moveTo(at);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String worldName() {
        return world;
    }

    public Location location() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z, yaw, 0f);
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

    public float yaw() {
        return yaw;
    }

    public void yaw(float yaw) {
        this.yaw = ((yaw % 360f) + 360f) % 360f;
    }

    public void moveTo(Location at) {
        this.world = at.getWorld() == null ? "world" : at.getWorld().getName();
        this.x = at.getX();
        this.y = at.getY();
        this.z = at.getZ();
        yaw(at.getYaw());
    }

    void set(String world, double x, double y, double z, float yaw) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        yaw(yaw);
    }

    public Look look() {
        return look;
    }

    public void look(Look look) {
        this.look = look;
    }

    public List<String> idle() {
        return idle;
    }

    public List<String> quests() {
        return quests;
    }

    /** The chunk it stands in (chunk x, chunk z). */
    public int chunkX() {
        return (int) Math.floor(x) >> 4;
    }

    public int chunkZ() {
        return (int) Math.floor(z) >> 4;
    }
}
