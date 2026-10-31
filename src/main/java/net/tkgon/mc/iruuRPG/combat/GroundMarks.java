package net.tkgon.mc.iruuRPG.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ground mark of an attack's reach (where a hit will land), drawn with text displays of "■" laid flat on the ground (facing up,
 * like the top of a block) and turned and stretched so that they tile the area: a circle and a fan are tiled ring by ring (each piece
 * turned along its radius), a box (a line attack) row by row. The pieces overlap a little so no gap shows.
 * <p>
 * The shapes are worked out in the mark's own frame ({@code forward} / {@code right} blocks from its origin), so a mark that follows its
 * caster is only moved and turned ({@link Mark#place}). A mark lives until {@link Mark#remove}, or, when it is kept alive by calls
 * ({@link Mark#keepAlive}), until it has not been kept for a while. The displays are not saved with the world, and
 * {@link #removeAll()} (on disable) takes away what is left.
 * <p>
 * Config {@code combat.effects.ground-mark}: {@code enabled} (false = the old particle outlines), the glyph's size and centre at scale 1
 * (to line the pieces up; check them in game with {@code outline: true}, which draws the old outline too), the piece size, the most pieces
 * of one mark and the opacity.
 */
public final class GroundMarks {

    /** A piece in the mark's frame: its centre ({@code forward}, {@code right}), its size across and along, and its turn from forward (radians, towards right). */
    public record Cell(double forward, double right, double across, double along, double turn) {
    }

    /** The glyph's numbers (blocks at scale 1). */
    public record Glyph(String text, double size, double centerX, double centerY, double overlap, int opacity) {
        static Glyph of(ConfigurationSection section) {
            if (section == null) return new Glyph("■", 0.15, -0.0125, 0.1375, 1.12, 150);
            return new Glyph(section.getString("text", "■"), clamp(section.getDouble("glyph-size", 0.15), 0.01, 2.0),
                    section.getDouble("glyph-center-x", -0.0125), section.getDouble("glyph-center-y", 0.1375),
                    clamp(section.getDouble("overlap", 1.12), 1.0, 2.0), (int) clamp(section.getInt("opacity", 150), 26, 255));
        }
    }

    private static final String TAG = "iruurpg_ground_mark";
    /** Every mark still on the ground (to take them away on disable and when they run out). */
    private static final Set<Mark> LIVE = Collections.synchronizedSet(new LinkedHashSet<>());
    private static BukkitTask ticker;

    private final JavaPlugin plugin;
    /** Marks kept alive by repeated calls, by the caller's key (a skill's task). */
    private final Map<Object, Mark> keyed = Collections.synchronizedMap(new java.util.HashMap<>());

    public GroundMarks(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection config() {
        return plugin.getConfig().getConfigurationSection("combat.effects.ground-mark");
    }

    /** Whether marks are drawn with displays (else the callers draw the old particle outlines). */
    public boolean enabled() {
        ConfigurationSection section = config();
        return section == null || section.getBoolean("enabled", true);
    }

    /** Whether the old particle outline is drawn as well (to check that the pieces line up with the real reach). */
    public boolean outlineToo() {
        ConfigurationSection section = config();
        return section != null && section.getBoolean("outline", false);
    }

    private double cellSize() {
        ConfigurationSection section = config();
        return clamp(section == null ? 0.75 : section.getDouble("cell", 0.75), 0.2, 4.0);
    }

    private int maxCells() {
        ConfigurationSection section = config();
        return (int) clamp(section == null ? 160 : section.getInt("max-cells", 160), 8, 600);
    }

    // ---- shapes (in the mark's frame) ---------------------------------------------------------------------------------

    /** A disc of {@code radius} whose centre is {@code forward} blocks ahead. */
    public List<Cell> circle(double forward, double radius) {
        return circle(forward, radius, cellSize(), maxCells());
    }

    /** A fan of {@code radius} and {@code angleDegrees} (the whole opening) from the origin, opening forward. */
    public List<Cell> fan(double radius, double angleDegrees) {
        return fan(radius, angleDegrees, cellSize(), maxCells());
    }

    /** A box {@code length} long (forward) and {@code width} wide, starting {@code forward} blocks ahead. */
    public List<Cell> box(double forward, double length, double width) {
        return box(forward, length, width, cellSize(), maxCells());
    }

    static List<Cell> circle(double forward, double radius, double cell, int max) {
        List<Cell> cells = sector(radius, Math.PI * 2.0, cell, max);
        if (forward == 0.0) return cells;
        List<Cell> moved = new ArrayList<>(cells.size());
        for (Cell c : cells) moved.add(new Cell(c.forward() + forward, c.right(), c.across(), c.along(), c.turn()));
        return moved;
    }

    static List<Cell> fan(double radius, double angleDegrees, double cell, int max) {
        return sector(radius, Math.toRadians(clamp(angleDegrees, 1.0, 360.0)), cell, max);
    }

    /** Rings from the centre out; each ring cut into pieces about {@code cell} long round it, each turned along its radius. */
    private static List<Cell> sector(double radius, double opening, double cell, int max) {
        if (radius <= 0.0) return List.of();
        double size = cell;
        for (int guard = 0; guard < 40; guard++) {
            List<Cell> cells = new ArrayList<>();
            int rings = Math.max(1, (int) Math.ceil(radius / size));
            double depth = radius / rings;
            for (int ring = 0; ring < rings; ring++) {
                double middle = (ring + 0.5) * depth;
                int pieces = Math.max(ring == 0 && opening >= Math.PI * 2.0 - 1.0E-6 ? 3 : 1, (int) Math.ceil(middle * opening / size));
                double step = opening / pieces;
                for (int piece = 0; piece < pieces; piece++) {
                    double angle = -opening / 2.0 + (piece + 0.5) * step;
                    // across = the arc of the ring's outer edge (so the outer pieces meet), along = the ring's depth
                    double across = Math.max(0.05, (ring + 1) * depth * step);
                    cells.add(new Cell(Math.cos(angle) * middle, Math.sin(angle) * middle, Math.min(across, 2.0 * (ring + 1) * depth), depth, angle));
                }
            }
            if (cells.size() <= max) return cells;
            size *= 1.25;
        }
        return List.of();
    }

    static List<Cell> box(double forward, double length, double width, double cell, int max) {
        if (length <= 0.0 || width <= 0.0) return List.of();
        double size = cell;
        for (int guard = 0; guard < 40; guard++) {
            int rows = Math.max(1, (int) Math.ceil(length / size));
            int columns = Math.max(1, (int) Math.ceil(width / size));
            if (rows * columns <= max || guard == 39) {
                List<Cell> cells = new ArrayList<>(rows * columns);
                double along = length / rows, across = width / columns;
                for (int row = 0; row < rows; row++) {
                    for (int column = 0; column < columns; column++) {
                        cells.add(new Cell(forward + (row + 0.5) * along, -width / 2.0 + (column + 0.5) * across, across, along, 0.0));
                    }
                }
                return cells;
            }
            size *= 1.25;
        }
        return List.of();
    }

    // ---- turning a piece ----------------------------------------------------------------------------------------------

    /**
     * The turn of a flat piece: the glyph's face points up, its up (local y) along {@code way} and its right (local x) across it.
     * (A text display is drawn in its x / y plane, facing +z.)
     */
    static Quaternionf flatAlong(double wayX, double wayZ) {
        double yaw = Math.atan2(-wayX, -wayZ);
        return new Quaternionf().rotateY((float) yaw).rotateX((float) (-Math.PI / 2.0));
    }

    static Transformation transformation(Glyph glyph, double across, double along, double wayX, double wayZ) {
        Quaternionf turn = flatAlong(wayX, wayZ);
        float scaleX = (float) (across / glyph.size() * glyph.overlap());
        float scaleY = (float) (along / glyph.size() * glyph.overlap());
        // move the glyph's centre (after scaling and turning) onto the entity's place
        Vector3f center = new Vector3f((float) glyph.centerX() * scaleX, (float) glyph.centerY() * scaleY, 0.0f);
        turn.transform(center);
        return new Transformation(center.negate(), turn, new Vector3f(scaleX, scaleY, 1.0f), new Quaternionf());
    }

    // ---- marks --------------------------------------------------------------------------------------------------------

    /**
     * Shows a mark at {@code origin}, its frame turned to {@code facing} (flat), in {@code color}. {@code ticks} > 0: it goes by itself then;
     * otherwise it stays until removed or until it is not kept alive.
     */
    public Mark show(Location origin, Vector facing, List<Cell> cells, Color color, int ticks) {
        Mark mark = new Mark(cells, Glyph.of(config()), color);
        mark.place(origin, facing);
        mark.expiresAt = ticks > 0 ? now() + ticks : now() + 10;
        LIVE.add(mark);
        startTicker();
        return mark;
    }

    /**
     * A mark that follows repeated calls with the same {@code key} (a skill's task calls this every few ticks during its wind-up): made
     * the first time, then moved; it goes {@code keepTicks} after the last call.
     */
    public Mark follow(Object key, Location origin, Vector facing, java.util.function.Supplier<List<Cell>> cells, Color color, int keepTicks) {
        Mark mark = keyed.get(key);
        if (mark == null || mark.removed) {
            mark = show(origin, facing, cells.get(), color, 0);
            keyed.put(key, mark);
        } else {
            mark.place(origin, facing);
        }
        mark.keepAlive(keepTicks);
        return mark;
    }

    private void startTicker() {
        if (ticker != null && !ticker.isCancelled()) return;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = now();
            List<Mark> done = new ArrayList<>();
            synchronized (LIVE) {
                for (Mark mark : LIVE) if (mark.removed || mark.expiresAt <= now) done.add(mark);
            }
            done.forEach(Mark::remove);
            synchronized (keyed) {
                keyed.values().removeIf(mark -> mark.removed);
            }
            if (LIVE.isEmpty() && ticker != null) {
                ticker.cancel();
                ticker = null;
            }
        }, 1L, 1L);
    }

    /** Takes every mark away (on disable). */
    public static void removeAll() {
        List<Mark> all;
        synchronized (LIVE) {
            all = new ArrayList<>(LIVE);
        }
        all.forEach(Mark::remove);
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private static long now() {
        return Bukkit.getCurrentTick();
    }

    /** One mark: its pieces (one text display each). */
    public static final class Mark {
        private final List<Cell> cells;
        private final Glyph glyph;
        private final Color color;
        private final List<TextDisplay> displays = new ArrayList<>();
        private Location lastOrigin;
        private Vector lastFacing;
        private long expiresAt;
        private boolean removed;

        private Mark(List<Cell> cells, Glyph glyph, Color color) {
            this.cells = cells;
            this.glyph = glyph;
            this.color = color;
        }

        /** Keeps the mark for {@code ticks} more. */
        public void keepAlive(int ticks) {
            expiresAt = Math.max(expiresAt, now() + Math.max(1, ticks));
        }

        /** Lays the mark (again) at {@code origin}, turned to {@code facing}. Nothing is redone when it has not moved. */
        public void place(Location origin, Vector facing) {
            if (removed || origin == null || origin.getWorld() == null) return;
            Vector forward = facing == null ? new Vector(0.0, 0.0, 1.0) : facing.clone().setY(0.0);
            if (forward.lengthSquared() < 1.0E-6) forward = new Vector(0.0, 0.0, 1.0);
            forward.normalize();
            if (lastOrigin != null && lastOrigin.getWorld() == origin.getWorld() && lastOrigin.distanceSquared(origin) < 0.0025
                    && lastFacing.dot(forward) > 0.9998) return;
            boolean turned = lastFacing == null || lastFacing.dot(forward) <= 0.9998;
            Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
            World world = origin.getWorld();
            for (int index = 0; index < cells.size(); index++) {
                Cell cell = cells.get(index);
                double x = origin.getX() + forward.getX() * cell.forward() + right.getX() * cell.right();
                double z = origin.getZ() + forward.getZ() * cell.forward() + right.getZ() * cell.right();
                Location at = new Location(world, x, groundY(world, x, origin.getY(), z) + 0.02, z);
                // the piece's own way: forward turned by its angle towards right
                double wayX = forward.getX() * Math.cos(cell.turn()) + right.getX() * Math.sin(cell.turn());
                double wayZ = forward.getZ() * Math.cos(cell.turn()) + right.getZ() * Math.sin(cell.turn());
                if (index < displays.size()) {
                    TextDisplay display = displays.get(index);
                    if (!display.isValid()) continue;
                    display.teleport(at);
                    if (turned) display.setTransformation(transformation(glyph, cell.across(), cell.along(), wayX, wayZ));
                } else {
                    Transformation shape = transformation(glyph, cell.across(), cell.along(), wayX, wayZ);
                    displays.add(world.spawn(at, TextDisplay.class, spawned -> {
                        spawned.text(Component.text(glyph.text(), TextColor.color(color.asRGB())));
                        spawned.setBillboard(Display.Billboard.FIXED);
                        spawned.setDefaultBackground(false);
                        spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        spawned.setShadowed(false);
                        spawned.setSeeThrough(false);
                        spawned.setTextOpacity((byte) glyph.opacity());
                        spawned.setBrightness(new Display.Brightness(15, 15));
                        spawned.setLineWidth(1000);
                        spawned.setRotation(0.0f, 0.0f);
                        spawned.setTransformation(shape);
                        spawned.setTeleportDuration(3);
                        spawned.setViewRange(1.5f);
                        spawned.setPersistent(false);
                        spawned.addScoreboardTag(TAG);
                    }));
                }
            }
            lastOrigin = origin.clone();
            lastFacing = forward;
        }

        public void remove() {
            if (removed) return;
            removed = true;
            for (TextDisplay display : displays) if (display.isValid()) display.remove();
            displays.clear();
            LIVE.remove(this);
        }

        public boolean removed() {
            return removed;
        }
    }

    /** The top of the ground under ({@code x}, {@code z}) near {@code y} (a block or so up or down), else {@code y}. */
    private static double groundY(World world, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), top = (int) Math.floor(y + 1.0);
        for (int by = top; by >= top - 4; by--) {
            Block block = world.getBlockAt(bx, by, bz);
            if (block.isPassable()) continue;
            double max = block.getBoundingBox().getMaxY();
            return max > by ? max : by + 1.0;
        }
        return y;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
