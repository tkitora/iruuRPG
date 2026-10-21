package net.tkgon.mc.iruuRPG.vfx;

/**
 * The curves effects are shaped with (all take and give 0 - 1 unless said): exponential and logarithmic ones for the
 * natural look of a burst that slows down or a fade that speeds up, and smoothstep for a soft edge.
 */
public final class Curves {

    private Curves() {
    }

    public static double clamp01(double value) {
        return value < 0.0 ? 0.0 : value > 1.0 ? 1.0 : value;
    }

    public static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /** Fast at first, then slower (1 - e^(-k t), scaled to end at exactly 1). k about 2 - 6. */
    public static double expOut(double t, double k) {
        t = clamp01(t);
        if (k < 1.0E-6) return t;
        return (1.0 - Math.exp(-k * t)) / (1.0 - Math.exp(-k));
    }

    /** Slow at first, then faster (the mirror of {@link #expOut}). */
    public static double expIn(double t, double k) {
        return 1.0 - expOut(1.0 - clamp01(t), k);
    }

    /** log curve: rises quickly and flattens (log(1 + k t) / log(1 + k)). */
    public static double logOut(double t, double k) {
        t = clamp01(t);
        if (k < 1.0E-6) return t;
        return Math.log(1.0 + k * t) / Math.log(1.0 + k);
    }

    public static double smoothstep(double t) {
        t = clamp01(t);
        return t * t * (3.0 - 2.0 * t);
    }

    /** 0 at both ends, 1 in the middle, smooth. */
    public static double bump(double t) {
        t = clamp01(t);
        double s = Math.sin(Math.PI * t);
        return s * s;
    }

    /** 0 - 1 - 0 triangle wave of {@code t} (period 1): for a gradient around a ring, so it has no seam where it closes. */
    public static double triangle(double t) {
        double f = t - Math.floor(t);
        return f < 0.5 ? f * 2.0 : 2.0 - f * 2.0;
    }
}
