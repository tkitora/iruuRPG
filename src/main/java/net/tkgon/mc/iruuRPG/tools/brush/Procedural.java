package net.tkgon.mc.iruuRPG.tools.brush;

/** The patterns the shade can be drawn from besides the light: smooth noise and cells (Voronoi). Pure arithmetic, the same answer for the same place. */
final class Procedural {

    private Procedural() {
    }

    static double smooth(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return c * c * (3.0 - 2.0 * c);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double hash(int x, int y, int z, int salt) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L + salt * 1103515245L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFL) / 65535.0;
    }

    /** Smooth value noise, 0 - 1. */
    static double noise(double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = smooth(x - ix);
        double fy = smooth(y - iy);
        double fz = smooth(z - iz);
        double a = lerp(lerp(hash(ix, iy, iz, 0), hash(ix + 1, iy, iz, 0), fx), lerp(hash(ix, iy + 1, iz, 0), hash(ix + 1, iy + 1, iz, 0), fx), fy);
        double b = lerp(lerp(hash(ix, iy, iz + 1, 0), hash(ix + 1, iy, iz + 1, 0), fx), lerp(hash(ix, iy + 1, iz + 1, 0), hash(ix + 1, iy + 1, iz + 1, 0), fx), fy);
        return lerp(a, b, fz);
    }

    /**
     * Cells of about {@code size} blocks: one feature point in each cube of the grid, and the distance from the place to the nearest ({@code [0]}) and the next nearest
     * ({@code [1]}) in grid units, and a number 0 - 1 for the nearest cell ({@code [2]}).
     */
    static double[] cell(double x, double y, double z, double size) {
        double px = x / size;
        double py = y / size;
        double pz = z / size;
        int cx = (int) Math.floor(px);
        int cy = (int) Math.floor(py);
        int cz = (int) Math.floor(pz);
        double nearest = 9.0;
        double second = 9.0;
        double id = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int gx = cx + dx;
                    int gy = cy + dy;
                    int gz = cz + dz;
                    double fx = gx + hash(gx, gy, gz, 1);
                    double fy = gy + hash(gx, gy, gz, 2);
                    double fz = gz + hash(gx, gy, gz, 3);
                    double d = Math.sqrt((px - fx) * (px - fx) + (py - fy) * (py - fy) + (pz - fz) * (pz - fz));
                    if (d < nearest) {
                        second = nearest;
                        nearest = d;
                        id = hash(gx, gy, gz, 4);
                    } else if (d < second) {
                        second = d;
                    }
                }
            }
        }
        return new double[]{nearest, second, id};
    }
}
