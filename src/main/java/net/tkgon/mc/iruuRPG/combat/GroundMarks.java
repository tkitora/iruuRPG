package net.tkgon.mc.iruuRPG.combat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.tkgon.mc.iruuRPG.markpack.MarkGlyphs;
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
 * The ground mark of an attack's reach (where a hit will land): a circle, a fan or a box, its inside and its rim each with their own color and
 * opacity, drawn with text displays laid flat on the ground (facing up, like the top of a block). Each display shows one glyph of the font
 * {@code iruurpg:marks} ({@link MarkGlyphs}: a disc, a wedge or a square, with or without a rim) stretched to its piece, so a circle is 1 or 2
 * displays, a box 1 or 5, a fan 1 or 2 per wedge (90° = one wedge). The pieces never overlap: a see-through mark has the same strength
 * everywhere. The font comes with the server's pack {@code iruumark.zip}.
 * <p>
 * The shapes are worked out in the mark's own frame ({@code forward} / {@code right} blocks from its origin), so a mark that follows its caster is
 * only moved and turned ({@link Mark#place}). A mark lives until {@link Mark#remove}, or, when it is kept alive by calls ({@link Mark#keepAlive}),
 * until it has not been kept for a while. The displays are not saved with the world, and {@link #removeAll()} (on disable) takes away what is left.
 * <p>
 * Config {@code combat.effects.ground-mark}: {@code enabled} (false = the old particle outlines), {@code outline} (draw both, to compare), and the
 * default look of the attacks' marks ({@link #style(Color)}).
 */
public final class GroundMarks {

    /** How a mark looks: the inside and the rim (color, opacity 0..255), and the rim's width in blocks (0 = no rim). */
    public record Style(Color fill, int fillOpacity, Color rim, int rimOpacity, double rimWidth) {
        boolean hasRim() {
            return rimWidth > 0.0 && rimOpacity > 0;
        }
    }

    /** A piece in the mark's frame: its centre ({@code forward}, {@code right}), its size across and along, and its turn from forward (radians, towards right). */
    public record Cell(double forward, double right, double across, double along, double turn) {
    }

    /** One display of a mark: the glyph, where and how big it is, and its color. */
    public record Piece(char glyph, Cell cell, Color color, int opacity) {
    }

    private static final Key FONT = Key.key(MarkGlyphs.FONT);
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

    /** Whether the old particle outline is drawn as well (to check that a mark lines up with the real reach). */
    public boolean outlineToo() {
        ConfigurationSection section = config();
        return section != null && section.getBoolean("outline", false);
    }

    /**
     * The look of an attack's mark in {@code color} (config: {@code fill-opacity}, {@code rim-opacity}, {@code rim-width} in blocks, and
     * {@code rim-whiten}, how much lighter the rim is).
     */
    public Style style(Color color) {
        ConfigurationSection section = config();
        int fillOpacity = section == null ? 90 : section.getInt("fill-opacity", 90);
        int rimOpacity = section == null ? 210 : section.getInt("rim-opacity", 210);
        double rimWidth = section == null ? 0.15 : section.getDouble("rim-width", 0.15);
        double whiten = clamp(section == null ? 0.35 : section.getDouble("rim-whiten", 0.35), 0.0, 1.0);
        Color rim = Color.fromRGB((int) Math.round(color.getRed() + (255 - color.getRed()) * whiten),
                (int) Math.round(color.getGreen() + (255 - color.getGreen()) * whiten), (int) Math.round(color.getBlue() + (255 - color.getBlue()) * whiten));
        return new Style(color, fillOpacity, rim, rimOpacity, rimWidth);
    }

    // ---- shapes (in the mark's frame) ---------------------------------------------------------------------------------

    /** A disc of {@code radius} whose centre is {@code forward} blocks ahead. */
    public static List<Piece> circle(double forward, double radius, Style style) {
        List<Piece> pieces = new ArrayList<>();
        if (radius <= 0.0) return pieces;
        Cell cell = new Cell(forward, 0.0, radius * 2.0, radius * 2.0, 0.0);
        if (!style.hasRim()) {
            addFill(pieces, MarkGlyphs.disc(MarkGlyphs.Part.WHOLE, -1), cell, style);
            return pieces;
        }
        int rim = MarkGlyphs.nearestRim(style.rimWidth() / radius);
        addFill(pieces, MarkGlyphs.disc(MarkGlyphs.Part.FILL, rim), cell, style);
        pieces.add(new Piece(MarkGlyphs.disc(MarkGlyphs.Part.RIM, rim), cell, style.rim(), opacity(style.rimOpacity())));
        return pieces;
    }

    /** A fan of {@code radius} and {@code angleDegrees} (the whole opening, rounded to whole degrees) from the origin, opening forward. */
    public static List<Piece> fan(double radius, double angleDegrees, Style style) {
        int degrees = (int) Math.round(clamp(angleDegrees, 1.0, 360.0));
        if (degrees >= 360) return circle(0.0, radius, style);
        List<Piece> pieces = new ArrayList<>();
        if (radius <= 0.0) return pieces;
        List<Integer> parts = MarkGlyphs.split(degrees);
        int rim = style.hasRim() ? MarkGlyphs.nearestRim(style.rimWidth() / radius) : -1;
        double turn = -Math.toRadians(degrees) / 2.0;
        for (int index = 0; index < parts.size(); index++) {
            int angle = parts.get(index);
            Cell cell = new Cell(0.0, 0.0, radius * 2.0, radius * 2.0, turn);
            if (rim < 0) {
                addFill(pieces, MarkGlyphs.wedge(angle, MarkGlyphs.Part.WHOLE, -1, MarkGlyphs.Sides.NONE), cell, style);
            } else {
                // the straight rims only on the fan's own two sides (the first wedge's start, the last wedge's end)
                MarkGlyphs.Sides sides = MarkGlyphs.Sides.of(index == 0, index == parts.size() - 1);
                addFill(pieces, MarkGlyphs.wedge(angle, MarkGlyphs.Part.FILL, rim, sides), cell, style);
                pieces.add(new Piece(MarkGlyphs.wedge(angle, MarkGlyphs.Part.RIM, rim, sides), cell, style.rim(), opacity(style.rimOpacity())));
            }
            turn += Math.toRadians(angle);
        }
        return pieces;
    }

    /** A box {@code length} long (forward) and {@code width} wide, starting {@code forward} blocks ahead: the inside, and the rim as 4 strips round it. */
    public static List<Piece> box(double forward, double length, double width, Style style) {
        List<Piece> pieces = new ArrayList<>();
        if (length <= 0.0 || width <= 0.0) return pieces;
        char square = MarkGlyphs.square();
        double t = style.hasRim() ? Math.min(style.rimWidth(), Math.min(length, width) / 2.0) : 0.0;
        if (t <= 0.0) {
            addFill(pieces, square, new Cell(forward + length / 2.0, 0.0, width, length, 0.0), style);
            return pieces;
        }
        if (length > 2 * t && width > 2 * t) addFill(pieces, square, new Cell(forward + length / 2.0, 0.0, width - 2 * t, length - 2 * t, 0.0), style);
        Color rim = style.rim();
        int rimOpacity = opacity(style.rimOpacity());
        pieces.add(new Piece(square, new Cell(forward + t / 2.0, 0.0, width, t, 0.0), rim, rimOpacity));                  // back
        pieces.add(new Piece(square, new Cell(forward + length - t / 2.0, 0.0, width, t, 0.0), rim, rimOpacity));         // front
        if (length > 2 * t) {
            pieces.add(new Piece(square, new Cell(forward + length / 2.0, -width / 2.0 + t / 2.0, t, length - 2 * t, 0.0), rim, rimOpacity));
            pieces.add(new Piece(square, new Cell(forward + length / 2.0, width / 2.0 - t / 2.0, t, length - 2 * t, 0.0), rim, rimOpacity));
        }
        return pieces;
    }

    private static void addFill(List<Piece> pieces, char glyph, Cell cell, Style style) {
        if (style.fillOpacity() > 0) pieces.add(new Piece(glyph, cell, style.fill(), opacity(style.fillOpacity())));
    }

    /** The game shows text of opacity 0..3 as fully opaque; anything that low is raised a little. */
    private static int opacity(int value) {
        return (int) clamp(value, 4, 255);
    }

    // ---- turning a piece ----------------------------------------------------------------------------------------------

    /**
     * The turn of a flat piece: the glyph's face points up, its up (local y) along {@code way} and its right (local x) across it, to the right.
     * (A text display is drawn in its x / y plane, facing +z.)
     */
    static Quaternionf flatAlong(double wayX, double wayZ) {
        double yaw = Math.atan2(-wayX, -wayZ);
        return new Quaternionf().rotateY((float) yaw).rotateX((float) (-Math.PI / 2.0));
    }

    /** Stretches the glyph to {@code across} x {@code along}, lays it flat along the way and puts its centre on the display's place. */
    static Transformation transformation(double across, double along, double wayX, double wayZ) {
        Quaternionf turn = flatAlong(wayX, wayZ);
        float scaleX = (float) (across / MarkGlyphs.GLYPH);
        float scaleY = (float) (along / MarkGlyphs.GLYPH);
        Vector3f center = new Vector3f((float) MarkGlyphs.CENTER_X * scaleX, 0.0f, 0.0f);
        turn.transform(center);
        return new Transformation(center.negate(), turn, new Vector3f(scaleX, scaleY, 1.0f), new Quaternionf());
    }

    // ---- marks --------------------------------------------------------------------------------------------------------

    /**
     * Shows a mark at {@code origin}, its frame turned to {@code facing} (flat). {@code ticks} > 0: it goes by itself then; otherwise it stays until
     * removed or until it is not kept alive.
     */
    public Mark show(Location origin, Vector facing, List<Piece> pieces, int ticks) {
        Mark mark = new Mark(pieces);
        mark.place(origin, facing);
        mark.expiresAt = ticks > 0 ? now() + ticks : now() + 10;
        LIVE.add(mark);
        startTicker();
        return mark;
    }

    /**
     * A mark that follows repeated calls with the same {@code key} (a skill's task calls this every few ticks during its wind-up): made the first
     * time, then moved; it goes {@code keepTicks} after the last call.
     */
    public Mark follow(Object key, Location origin, Vector facing, java.util.function.Supplier<List<Piece>> pieces, int keepTicks) {
        Mark mark = keyed.get(key);
        if (mark == null || mark.removed) {
            mark = show(origin, facing, pieces.get(), 0);
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
        private final List<Piece> pieces;
        private final List<TextDisplay> displays = new ArrayList<>();
        private Location lastOrigin;
        private Vector lastFacing;
        private long expiresAt;
        private boolean removed;

        private Mark(List<Piece> pieces) {
            this.pieces = pieces;
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
            // one height for the whole mark (the ground under its origin): the pieces are large, and they must not step apart
            double y = groundY(world, origin.getX(), origin.getY(), origin.getZ()) + 0.02;
            for (int index = 0; index < pieces.size(); index++) {
                Piece piece = pieces.get(index);
                Cell cell = piece.cell();
                double x = origin.getX() + forward.getX() * cell.forward() + right.getX() * cell.right();
                double z = origin.getZ() + forward.getZ() * cell.forward() + right.getZ() * cell.right();
                Location at = new Location(world, x, y, z);
                // the piece's own way: forward turned by its angle towards right
                double wayX = forward.getX() * Math.cos(cell.turn()) + right.getX() * Math.sin(cell.turn());
                double wayZ = forward.getZ() * Math.cos(cell.turn()) + right.getZ() * Math.sin(cell.turn());
                if (index < displays.size()) {
                    TextDisplay display = displays.get(index);
                    if (!display.isValid()) continue;
                    display.teleport(at);
                    if (turned) display.setTransformation(transformation(cell.across(), cell.along(), wayX, wayZ));
                } else {
                    Transformation shape = transformation(cell.across(), cell.along(), wayX, wayZ);
                    displays.add(world.spawn(at, TextDisplay.class, spawned -> {
                        spawned.text(Component.text(String.valueOf(piece.glyph())).font(FONT).color(TextColor.color(piece.color().asRGB())));
                        spawned.setBillboard(Display.Billboard.FIXED);
                        spawned.setDefaultBackground(false);
                        spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        spawned.setShadowed(false);
                        spawned.setSeeThrough(false);
                        spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                        spawned.setTextOpacity((byte) piece.opacity());
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

    /** The top of the ground under ({@code x}, {@code z}) near {@code y} (a block up to a few down), else {@code y}. */
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
