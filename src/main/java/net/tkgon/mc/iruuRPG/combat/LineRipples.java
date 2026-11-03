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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A ripple drawn as a thin line of texture instead of particles (t257): one text display showing the rim of a disc of the font
 * {@code iruurpg:marks} ({@link MarkGlyphs}), stood up across {@code heading} (so the ring faces the way the arrow goes), that opens a little and fades
 * for a few ticks and goes. Particles of a ring always crowd the view; a line a few hundredths of a block wide does not.
 * <p>
 * Config {@code combat.effects.ripple-lines}: {@code enabled} (false = the old particle rings), {@code line-width} (blocks, nearest of the font's rim
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
        return section == null || section.getBoolean("enabled", true);
    }

    /**
     * One ring of {@code radius} blocks round {@code centre}, facing {@code heading}, in {@code color} at {@code opacity} (4..255); it opens and fades
     * for the configured ticks. {@code hideFrom} (may be null) does not see it. {@code bothSides}: a second display faces the other way, so that the ring is
     * drawn when seen from behind too (the shooter looks at the ring from behind: first person).
     */
    public static void ring(JavaPlugin plugin, Location centre, Vector heading, double radius, Color color, int opacity, Player hideFrom, boolean bothSides) {
        if (centre.getWorld() == null || radius <= 0.0) return;
        ConfigurationSection section = config(plugin);
        double lineWidth = section == null ? 0.025 : section.getDouble("line-width", 0.025);
        double open = section == null ? 1.25 : section.getDouble("open", 1.25);
        int ticks = Math.max(2, section == null ? 5 : section.getInt("ticks", 5));
        char glyph = MarkGlyphs.disc(MarkGlyphs.Part.RIM, MarkGlyphs.nearestRim(lineWidth / radius));
        Vector way = heading.lengthSquared() < 1.0E-6 ? new Vector(0.0, 0.0, 1.0) : heading.clone().normalize();
        Quaternionf turn = new Quaternionf().rotateTo(new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f((float) way.getX(), (float) way.getY(), (float) way.getZ()));
        show(plugin, centre, glyph, turn, radius, color, opacity, hideFrom, open, ticks);
        if (bothSides) show(plugin, centre, glyph, new Quaternionf(turn).rotateY((float) Math.PI), radius, color, opacity, hideFrom, open, ticks);
    }

    private static void show(JavaPlugin plugin, Location centre, char glyph, Quaternionf turn, double radius, Color color, int opacity, Player hideFrom,
                             double open, int ticks) {
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
            spawned.setInterpolationDuration(ticks);
            spawned.setViewRange(1.0f);
            spawned.setPersistent(false);
            spawned.addScoreboardTag(TAG);
        });
        if (hideFrom != null) hideFrom.hideEntity(plugin, display);
        // opens from the next tick on (the first transformation has to reach the players before the change is smoothed), fading a step a tick
        for (int age = 1; age <= ticks; age++) {
            int step = age;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!display.isValid()) return;
                if (step == 1) {
                    display.setInterpolationDelay(0);
                    display.setTransformation(transformation(radius * open, turn));
                }
                int faded = (int) Math.round(opacity * (1.0 - (step - 1.0) / ticks));
                display.setTextOpacity((byte) Math.max(4, Math.min(255, faded)));
            }, step);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) display.remove();
        }, ticks + 1L);
    }

    /** The glyph (0.2 blocks across at scale 1) stretched to a ring of {@code radius}, its centre on the display's place. */
    private static Transformation transformation(double radius, Quaternionf turn) {
        float scale = (float) (radius * 2.0 / MarkGlyphs.GLYPH);
        Vector3f centre = new Vector3f((float) MarkGlyphs.CENTER_X * scale, 0.0f, 0.0f);
        new Quaternionf(turn).transform(centre);
        return new Transformation(centre.negate(), new Quaternionf(turn), new Vector3f(scale, scale, 1.0f), new Quaternionf());
    }
}
