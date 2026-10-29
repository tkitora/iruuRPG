package net.tkgon.mc.iruuRPG.bgm;

import org.bukkit.Location;

/**
 * A box in a world (or in every world whose name fits a pattern with {@code *}, e.g. {@code dungeon_*_cave*}: the template and every copy of an instance dungeon) that has a song: whoever stands in it hears the song. When boxes overlap the one with the higher {@code priority} wins, and of equal ones the smaller.
 * {@code min} and {@code max} are block coordinates, both inside the box. {@code fade} is the seconds of the fade when the song starts or ends because of this box.
 */
public final class BgmVolume {

    private final String id;
    private String world;
    /** Set when {@code world} has a {@code *}: the worlds whose name it fits. */
    private java.util.regex.Pattern worldPattern;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private String song;
    private int priority;
    private int volume = 100;
    private double fade = 3.0;
    private boolean loop = true;

    public BgmVolume(String id, String world, int[] a, int[] b, String song) {
        this.id = id;
        this.song = song;
        setBox(world, a, b);
    }

    public String id() {
        return id;
    }

    public String world() {
        return world;
    }

    /** Whether a world of this name is one of the volume's worlds. */
    public boolean inWorld(String name) {
        return worldPattern != null ? worldPattern.matcher(name).matches() : world.equals(name);
    }

    /** True when the world is a pattern (with {@code *}) and not the name of one world. */
    public boolean worldIsPattern() {
        return worldPattern != null;
    }

    /** Sets the world (a name, or a pattern with {@code *}). */
    public void setWorld(String world) {
        this.world = world;
        this.worldPattern = null;
        if (world.indexOf('*') >= 0) {
            StringBuilder regex = new StringBuilder();
            for (String part : world.split("\\*", -1)) {
                if (regex.length() > 0 || world.startsWith("*")) regex.append(".*");
                regex.append(java.util.regex.Pattern.quote(part));
            }
            this.worldPattern = java.util.regex.Pattern.compile(regex.toString());
        }
    }

    public String song() {
        return song;
    }

    public void setSong(String song) {
        this.song = song;
    }

    public int priority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /** The loudness of this box, in percent (0 to 100). */
    public int volume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
    }

    public double fade() {
        return fade;
    }

    public void setFade(double fade) {
        this.fade = Math.max(0.0, Math.min(60.0, fade));
    }

    public boolean loop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public int[] min() {
        return new int[]{minX, minY, minZ};
    }

    public int[] max() {
        return new int[]{maxX, maxY, maxZ};
    }

    /** Puts the box between two corners (any two opposite ones). */
    public void setBox(String world, int[] a, int[] b) {
        setWorld(world);
        this.minX = Math.min(a[0], b[0]);
        this.minY = Math.min(a[1], b[1]);
        this.minZ = Math.min(a[2], b[2]);
        this.maxX = Math.max(a[0], b[0]);
        this.maxY = Math.max(a[1], b[1]);
        this.maxZ = Math.max(a[2], b[2]);
    }

    public long blocks() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean contains(Location at) {
        return contains(at, 0.0);
    }

    /** Whether the place is in the box grown by {@code margin} blocks on every side (the part just outside, where a player who is leaving does not flicker in and out). */
    public boolean contains(Location at, double margin) {
        if (at.getWorld() == null || !inWorld(at.getWorld().getName())) return false;
        double x = at.getX();
        double y = at.getY();
        double z = at.getZ();
        return x >= minX - margin && x < maxX + 1 + margin
                && y >= minY - margin && y < maxY + 1 + margin
                && z >= minZ - margin && z < maxZ + 1 + margin;
    }

    /** True when {@code other} is the one that wins where both contain the place. */
    boolean beatenBy(BgmVolume other) {
        if (other.priority != priority) return other.priority > priority;
        return other.blocks() < blocks();
    }
}
