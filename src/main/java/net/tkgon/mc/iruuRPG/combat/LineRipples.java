package net.tkgon.mc.iruuRPG.combat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.tkgon.mc.iruuRPG.markpack.MarkGlyphs;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A ripple drawn as a thin line of texture instead of particles (t257): one text display showing the rim of a disc of the font
 * {@code iruurpg:marks} ({@link MarkGlyphs}), stood up across {@code heading} (so the ring faces the way the arrow goes), that opens a little and fades
 * for a few ticks and goes. Particles of a ring always crowd the view; a line a few hundredths of a block wide does not.
 * <p>
 * Config {@code combat.effects.ripple-lines}: {@code enabled} (false, the default since the ranger's lines were put back to particles, = the old particle rings), {@code line-width} (blocks, nearest of the font's rim
 * widths is used), {@code open} (how much larger the ring is at the end, 1.25), {@code ticks} (how long it lives, 5).
 */
public final class LineRipples {

    private static final Key FONT = Key.key(MarkGlyphs.FONT);
    private static final String TAG = "iruurpg_line_ripple";

    private LineRipples() {
    }

    private static ConfigurationSection config(JavaPlugin plugin) {
        return plugin.getConfig().getConfigurationSection("combat.effects.ripple-lines");
    }

    /** Whether ripples are drawn as lines (else the callers draw the old particle rings). */
    public static boolean enabled(JavaPlugin plugin) {
        ConfigurationSection section = config(plugin);
        return section != null && section.getBoolean("enabled", false);
    }

    /**
     * One ring of {@code radius} blocks round {@code centre}, facing {@code heading}, in {@code color} at {@code opacity} (4..255); it opens and fades
     * for the configured ticks. {@code hideFrom} (may be null) does not see it. {@code bothSides}: a second display faces the other way, so that the ring is
     * drawn when seen from behind too (the shooter looks at the ring from behind: first person).
     */
    public static void ring(JavaPlugin plugin, Location centre, Vector heading, double radius, Color color, int opacity, Player hideFrom, boolean bothSides) {
        ConfigurationSection section = config(plugin);
        double open = section == null ? 1.25 : section.getDouble("open", 1.25);
        int ticks = Math.max(2, section == null ? 5 : section.getInt("ticks", 5));
        play(plugin, centre, heading, radius, radius * open, color, opacity, 0.0, ticks, hideFrom, bothSides);
    }

    private static void play(JavaPlugin plugin, Location centre, Vector heading, double radius, double endRadius, Color color, int opacity, double fadeTo, int ticks,
                             Player hideFrom, boolean bothSides) {
        if (centre.getWorld() == null || radius <= 0.0) return;
        // the line is as wide as the rim of the glyph for the larger size (so that it does not thicken much while it opens)
        char glyph = glyph(plugin, Math.max(radius, endRadius));
        Quaternionf turn = turn(heading);
        boolean opening = Math.abs(endRadius - radius) > 1.0E-6;
        List<TextDisplay> displays = new ArrayList<>(2);
        displays.add(spawn(plugin, centre, glyph, turn, radius, color, opacity, hideFrom, opening ? ticks : 0));
        if (bothSides) displays.add(spawn(plugin, centre, glyph, new Quaternionf(turn).rotateY((float) Math.PI), radius, color, opacity, hideFrom, opening ? ticks : 0));
        int every = ticks > 6 ? 2 : 1;
        int[] age = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, (BukkitTask task) -> {
            age[0]++;
            if (age[0] > ticks || displays.stream().noneMatch(TextDisplay::isValid)) {
                displays.forEach(display -> {
                    if (display.isValid()) display.remove();
                });
                task.cancel();
                return;
            }
            // opens from the next tick on (the first transformation has to reach the players before the change is smoothed)
            if (age[0] == 1 && opening) {
                for (int index = 0; index < displays.size(); index++) {
                    TextDisplay display = displays.get(index);
                    if (!display.isValid()) continue;
                    display.setInterpolationDelay(0);
                    display.setTransformation(transformation(endRadius, index == 0 ? turn : new Quaternionf(turn).rotateY((float) Math.PI)));
                }
            }
            if (age[0] % every == 0 || age[0] == 1) {
                double share = 1.0 - (1.0 - fadeTo) * (age[0] - 1.0) / ticks;
                byte faded = (byte) Math.max(4, Math.min(255, Math.round(opacity * share)));
                displays.forEach(display -> {
                    if (display.isValid()) display.setTextOpacity(faded);
                });
            }
        }, 1L, 1L);
    }

    private static TextDisplay spawn(JavaPlugin plugin, Location centre, char glyph, Quaternionf turn, double radius, Color color, int opacity, Player hideFrom,
                                     int smooth) {
        TextDisplay display = centre.getWorld().spawn(centre, TextDisplay.class, spawned -> {
            spawned.text(Component.text(String.valueOf(glyph)).font(FONT).color(TextColor.color(color.asRGB())));
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setShadowed(false);
            spawned.setSeeThrough(false);
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setTextOpacity((byte) Math.max(4, Math.min(255, opacity)));
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setLineWidth(1000);
            spawned.setRotation(0.0f, 0.0f);
            spawned.setTransformation(transformation(radius, turn));
            if (smooth > 0) spawned.setInterpolationDuration(smooth);
            spawned.setViewRange(1.0f);
            spawned.setPersistent(false);
            spawned.addScoreboardTag(TAG);
        });
        if (hideFrom != null) hideFrom.hideEntity(plugin, display);
        return display;
    }

    private static Quaternionf turn(Vector heading) {
        Vector way = heading.lengthSquared() < 1.0E-6 ? new Vector(0.0, 0.0, 1.0) : heading.clone().normalize();
        return new Quaternionf().rotateTo(new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f((float) way.getX(), (float) way.getY(), (float) way.getZ()));
    }

    private static char glyph(JavaPlugin plugin, double radius) {
        ConfigurationSection section = config(plugin);
        double lineWidth = section == null ? 0.025 : section.getDouble("line-width", 0.025);
        return MarkGlyphs.disc(MarkGlyphs.Part.RIM, MarkGlyphs.nearestRim(lineWidth / radius));
    }

    /** The glyph (0.2 blocks across at scale 1) stretched to a ring of {@code radius}, its centre on the display's place. */
    private static Transformation transformation(double radius, Quaternionf turn) {
        float scale = (float) (radius * 2.0 / MarkGlyphs.GLYPH);
        Vector3f centre = new Vector3f((float) MarkGlyphs.CENTER_X * scale, 0.0f, 0.0f);
        new Quaternionf(turn).transform(centre);
        return new Transformation(centre.negate(), new Quaternionf(turn), new Vector3f(scale, scale, 1.0f), new Quaternionf());
    }
}
