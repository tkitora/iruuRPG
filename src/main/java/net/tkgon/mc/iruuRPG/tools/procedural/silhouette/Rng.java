package net.tkgon.mc.iruuRPG.tools.procedural.silhouette;

import java.util.SplittableRandom;

/** 乱数・座標ハッシュ・値ノイズ。座標ハッシュは位置だけで決まるので、生成順に依存しない。 */
public final class Rng {
    private final SplittableRandom r;
    public final long seed;

    public Rng(long seed) { this.seed = seed; this.r = new SplittableRandom(seed); }

    public double next() { return r.nextDouble(); }
    public double range(double a, double b) { return a + (b - a) * r.nextDouble(); }
    public int irange(int a, int bInclusive) { return a + r.nextInt(bInclusive - a + 1); }
    public boolean chance(double p) { return r.nextDouble() < p; }
    public double signed() { return r.nextDouble() * 2 - 1; }
    public double sign() { return r.nextBoolean() ? 1 : -1; }

    /** 位置ハッシュ [0,1) */
    public static double hash(long seed, int x, int y, int z) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= y * 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** 3D値ノイズ [0,1) 、smoothstep 補間 */
    public static double noise(long seed, double x, double y, double z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        double v = 0;
        for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) for (int dz = 0; dz < 2; dz++) {
            double w = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);
            v += w * hash(seed, ix + dx, iy + dy, iz + dz);
        }
        return v;
    }

    private static final int[][] BAYER4 = {
            {0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    /** 3Dに展開した順序ディザ閾値 [0,1) */
    public static double bayer(int x, int y, int z) {
        return (BAYER4[(x + 2 * y) & 3][(z + y) & 3] + 0.5) / 16.0;
    }
}
