package net.tkgon.mc.iruuRPG.vfx;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Unit shapes made once and reused ("baked"): the points of a circle and of a sphere (evenly spread, by the golden angle)
 * for a number of points, as flat arrays. An effect scales and turns them to where it is, which costs a multiply-add per
 * point and no trigonometry. The arrays must not be changed by the caller.
 */
public final class Shapes {

    private static final ConcurrentHashMap<Integer, float[]> CIRCLES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, float[]> SPHERES = new ConcurrentHashMap<>();
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private Shapes() {
    }

    /** {@code count} points on the unit circle, as (cos, sin) pairs: {@code [2 i]} and {@code [2 i + 1]}. */
    public static float[] circle(int count) {
        return CIRCLES.computeIfAbsent(count, n -> {
            float[] points = new float[n * 2];
            for (int index = 0; index < n; index++) {
                double angle = Math.PI * 2.0 * index / n;
                points[index * 2] = (float) Math.cos(angle);
                points[index * 2 + 1] = (float) Math.sin(angle);
            }
            return points;
        });
    }

    /** {@code count} points evenly spread over the unit sphere, as (x, y, z) triples (y is up, from the top down). */
    public static float[] sphere(int count) {
        return SPHERES.computeIfAbsent(count, n -> {
            float[] points = new float[n * 3];
            for (int index = 0; index < n; index++) {
                double y = 1.0 - 2.0 * (index + 0.5) / n;
                double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
                double angle = GOLDEN_ANGLE * index;
                points[index * 3] = (float) (Math.cos(angle) * ring);
                points[index * 3 + 1] = (float) y;
                points[index * 3 + 2] = (float) (Math.sin(angle) * ring);
            }
            return points;
        });
    }

    /** Makes the shapes that effects use at once, when the plugin starts (so none is made in the middle of a fight). */
    public static void warmUp() {
        for (int count : new int[]{8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 48}) circle(count);
        for (int count : new int[]{16, 22, 32, 48}) sphere(count);
    }
}
