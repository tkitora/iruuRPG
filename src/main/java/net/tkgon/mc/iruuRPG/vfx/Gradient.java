package net.tkgon.mc.iruuRPG.vfx;

import org.bukkit.Color;
import org.bukkit.Particle;

/**
 * A color that changes along 0 - 1, worked out in RGB between stops and baked into a table (256 entries), so asking for
 * a color is a lookup. Boundaries: the value is clamped into [0, 1], the first and the last entry are exactly the first and
 * the last stop, and the stops are evenly spaced unless given, so there is never a gap or a jump in the middle.
 *
 * <p>{@link #dust(double, double)} gives a (shared) {@code DustOptions} of the color at a position and a size; they are made
 * once and reused (the size is rounded to 0.1), so drawing does not create an object per particle.
 */
public final class Gradient {

    private static final int STEPS = 256;
    private static final int SIZES = 41; // sizes 0.0 - 4.0 in steps of 0.1

    private final int[] rgb = new int[STEPS];
    private final Particle.DustOptions[][] cache = new Particle.DustOptions[STEPS][SIZES];

    private Gradient(int[] colors, double[] positions) {
        int count = colors.length;
        for (int step = 0; step < STEPS; step++) {
            double t = step / (double) (STEPS - 1);
            int upper = 1;
            while (upper < count - 1 && positions[upper] < t) upper++;
            int lower = upper - 1;
            double span = positions[upper] - positions[lower];
            double local = span <= 1.0E-9 ? 1.0 : Curves.clamp01((t - positions[lower]) / span);
            rgb[step] = mixRgb(colors[lower], colors[upper], local);
        }
        rgb[0] = colors[0];
        rgb[STEPS - 1] = colors[count - 1];
    }

    /** A gradient through {@code colors}, the stops evenly spaced. One color is a plain color. */
    public static Gradient of(Color... colors) {
        if (colors.length == 0) throw new IllegalArgumentException("a gradient needs a color");
        int[] values = new int[Math.max(2, colors.length)];
        double[] positions = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            values[index] = colors[Math.min(index, colors.length - 1)].asRGB();
            positions[index] = values.length == 1 ? 0.0 : index / (double) (values.length - 1);
        }
        return new Gradient(values, positions);
    }

    /** A gradient through {@code colors} with the stops at {@code positions} (rising, first 0, last 1). */
    public static Gradient of(double[] positions, Color... colors) {
        if (colors.length < 2 || positions.length != colors.length) throw new IllegalArgumentException("two stops at least, one position each");
        int[] values = new int[colors.length];
        double[] fixed = positions.clone();
        fixed[0] = 0.0;
        fixed[fixed.length - 1] = 1.0;
        for (int index = 0; index < colors.length; index++) {
            values[index] = colors[index].asRGB();
            if (index > 0 && fixed[index] < fixed[index - 1]) fixed[index] = fixed[index - 1];
        }
        return new Gradient(values, fixed);
    }

    private static int mixRgb(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) * (1.0 - t) + ((to >> 16) & 0xFF) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) * (1.0 - t) + ((to >> 8) & 0xFF) * t);
        int b = (int) Math.round((from & 0xFF) * (1.0 - t) + (to & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    private int index(double t) {
        return (int) Math.round(Curves.clamp01(t) * (STEPS - 1));
    }

    public Color at(double t) {
        return Color.fromRGB(rgb[index(t)]);
    }

    /** The color at {@code t}, going 0 - 1 - 0 as {@code t} goes 0 - 1 (around a ring: no seam where it closes). */
    public Color atWrap(double t) {
        return at(Curves.triangle(t));
    }

    /** The color at {@code t} of {@code curve}d position: {@code expOut(t, k)} and so on are given by the caller. */
    public Particle.DustOptions dust(double t, double size) {
        int step = index(t);
        int sizeIndex = (int) Math.max(1, Math.min(SIZES - 1, Math.round(size * 10.0)));
        Particle.DustOptions options = cache[step][sizeIndex];
        if (options == null) {
            options = new Particle.DustOptions(Color.fromRGB(rgb[step]), sizeIndex / 10.0f);
            cache[step][sizeIndex] = options;
        }
        return options;
    }

    /** {@link #dust} around a ring: going 0 - 1 - 0 with {@code t}. */
    public Particle.DustOptions dustWrap(double t, double size) {
        return dust(Curves.triangle(t), size);
    }

    /** This gradient and {@code other} mixed in RGB, {@code share} of the other (0 - 1). */
    public Gradient mix(Gradient other, double share) {
        int[] colors = new int[STEPS];
        double[] positions = new double[STEPS];
        for (int step = 0; step < STEPS; step++) {
            colors[step] = mixRgb(rgb[step], other.rgb[step], Curves.clamp01(share));
            positions[step] = step / (double) (STEPS - 1);
        }
        return new Gradient(colors, positions);
    }
}
