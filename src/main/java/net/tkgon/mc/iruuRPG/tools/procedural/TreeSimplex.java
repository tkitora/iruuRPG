package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.Random;

/**
 * 3D simplex noise (Perlin's simplex grid as written out by Stefan Gustavson, "Simplex noise demystified", 2005) in about [-1, 1], with the permutation shuffled by a
 * seed, so the same seed gives the same field. Smooth and without the grid look of value noise: the branches of the fractal tree read it step by step for their sway.
 */
final class TreeSimplex {

    private static final int[][] GRAD = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    private final int[] perm = new int[512];

    TreeSimplex(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = p[i];
            p[i] = p[j];
            p[j] = swap;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    double noise(double x, double y, double z) {
        double s = (x + y + z) * F3;
        int i = (int) Math.floor(x + s);
        int j = (int) Math.floor(y + s);
        int k = (int) Math.floor(z + s);
        double t = (i + j + k) * G3;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        double z0 = z - (k - t);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
            else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
            else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
        } else {
            if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
            else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
            else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        }
        double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3, y2 = y0 - j2 + 2.0 * G3, z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3, y3 = y0 - 1.0 + 3.0 * G3, z3 = z0 - 1.0 + 3.0 * G3;
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        double n = corner(perm[ii + perm[jj + perm[kk]]], x0, y0, z0)
                + corner(perm[ii + i1 + perm[jj + j1 + perm[kk + k1]]], x1, y1, z1)
                + corner(perm[ii + i2 + perm[jj + j2 + perm[kk + k2]]], x2, y2, z2)
                + corner(perm[ii + 1 + perm[jj + 1 + perm[kk + 1]]], x3, y3, z3);
        return 32.0 * n;
    }

    private static double corner(int hash, double x, double y, double z) {
        double t = 0.6 - x * x - y * y - z * z;
        if (t < 0) return 0.0;
        int[] g = GRAD[hash % 12];
        t *= t;
        return t * t * (g[0] * x + g[1] * y + g[2] * z);
    }
}
