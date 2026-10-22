package net.tkgon.mc.iruuRPG.tool.tree;

/** A small smooth 3D value noise in [-1, 1] (a hashed lattice blended between points), for the rough edges of leaf clouds. */
final class TreeNoise {

    private TreeNoise() {
    }

    static double value(double x, double y, double z, long seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = smooth(x - ix);
        double fy = smooth(y - iy);
        double fz = smooth(z - iz);
        double x00 = lerp(hash(ix, iy, iz, seed), hash(ix + 1, iy, iz, seed), fx);
        double x10 = lerp(hash(ix, iy + 1, iz, seed), hash(ix + 1, iy + 1, iz, seed), fx);
        double x01 = lerp(hash(ix, iy, iz + 1, seed), hash(ix + 1, iy, iz + 1, seed), fx);
        double x11 = lerp(hash(ix, iy + 1, iz + 1, seed), hash(ix + 1, iy + 1, iz + 1, seed), fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double hash(int x, int y, int z, long seed) {
        long h = seed + x * 374761393L + y * 668265263L + z * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return ((h & 0xFFFFL) / 32767.5) - 1.0;
    }
}
