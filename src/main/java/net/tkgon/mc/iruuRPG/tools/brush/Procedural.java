package net.tkgon.mc.iruuRPG.tools.brush;

/** The patterns the shade, the pattern and the gradient can be drawn from besides the light: several kinds of noise, and cells (Voronoi). Pure arithmetic, the same answer for the same place. */
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

    /** The kinds of noise: what they look like, from soft clouds to sharp cracks. */
    enum NoiseType {
        VALUE("バリュー", "滑らかな雲のような斑 (基本)"),
        PERLIN("パーリン", "自然な起伏の滑らかなノイズ。バリューより縞が出にくい"),
        RIDGED("リッジ", "稜線のような尖った筋 (割れ目・山の尾根)"),
        BILLOW("ビロー", "丸く盛り上がった雲 (もこもこした斑)"),
        VORONOI("ボロノイ", "細胞の中心からの距離 (丸い粒・斑点)"),
        CELLULAR("セルラー", "細胞の境目 (ひび割れ・石畳の目地)"),
        SHARD("シャード", "角ばった破片 (四角い欠片の模様)");

        final String label;
        final String description;

        NoiseType(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    private static final double[][] GRADIENTS = {{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}};

    private static double gradient(int ix, int iy, int iz, int salt, double dx, double dy, double dz) {
        int index = (int) (hash(ix, iy, iz, salt) * 11.999);
        double[] g = GRADIENTS[index];
        return g[0] * dx + g[1] * dy + g[2] * dz;
    }

    /** Gradient (Perlin) noise, 0 - 1. */
    static double perlin(double x, double y, double z, int salt) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fy = y - iy;
        double fz = z - iz;
        double u = fade(fx);
        double v = fade(fy);
        double w = fade(fz);
        double n000 = gradient(ix, iy, iz, salt, fx, fy, fz);
        double n100 = gradient(ix + 1, iy, iz, salt, fx - 1, fy, fz);
        double n010 = gradient(ix, iy + 1, iz, salt, fx, fy - 1, fz);
        double n110 = gradient(ix + 1, iy + 1, iz, salt, fx - 1, fy - 1, fz);
        double n001 = gradient(ix, iy, iz + 1, salt, fx, fy, fz - 1);
        double n101 = gradient(ix + 1, iy, iz + 1, salt, fx - 1, fy, fz - 1);
        double n011 = gradient(ix, iy + 1, iz + 1, salt, fx, fy - 1, fz - 1);
        double n111 = gradient(ix + 1, iy + 1, iz + 1, salt, fx - 1, fy - 1, fz - 1);
        double value = lerp(lerp(lerp(n000, n100, u), lerp(n010, n110, u), v), lerp(lerp(n001, n101, u), lerp(n011, n111, u), v), w);
        return Math.max(0.0, Math.min(1.0, 0.5 + value * 0.9));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** One layer of the noise of {@code type} at the point, 0 - 1. */
    private static double layer(NoiseType type, double x, double y, double z, int salt) {
        return switch (type) {
            case VALUE -> noise(x + salt * 17.0, y, z);
            case PERLIN -> perlin(x, y, z, salt);
            case RIDGED -> 1.0 - Math.abs(2.0 * perlin(x, y, z, salt) - 1.0);
            case BILLOW -> Math.abs(2.0 * perlin(x, y, z, salt) - 1.0);
            case VORONOI -> {
                double[] c = cell(x + salt * 13.0, y, z, 1.0);
                yield Math.max(0.0, 1.0 - Math.min(1.0, c[0] * 1.15));
            }
            case CELLULAR -> {
                double[] c = cell(x + salt * 13.0, y, z, 1.0);
                yield Math.min(1.0, (c[1] - c[0]) * 1.6);
            }
            case SHARD -> {
                double[] c = cellBox(x + salt * 13.0, y, z);
                yield c[2] * 0.7 + Math.min(1.0, (c[1] - c[0]) * 1.2) * 0.3;
            }
        };
    }

    /**
     * The noise of the settings (type, octaves, persistence, contrast, seed) at ({@code x}, {@code y}, {@code z}) taken {@code scale} blocks to a feature: 0 - 1. More octaves
     * add finer detail on top, each {@code persistence} as strong as the one before.
     */
    static double sample(BrushSettings s, double x, double y, double z, double scale) {
        double sc = Math.max(0.25, scale);
        double sum = 0.0;
        double amplitude = 1.0;
        double total = 0.0;
        double frequency = 1.0 / sc;
        for (int octave = 0; octave < Math.max(1, s.noiseOctaves); octave++) {
            sum += amplitude * layer(s.noiseType, x * frequency + s.noiseSeed * 7.31, y * frequency + s.noiseSeed * 3.17, z * frequency + s.noiseSeed * 5.53, octave + s.noiseSeed);
            total += amplitude;
            amplitude *= s.noisePersistence;
            frequency *= 2.0;
        }
        double value = total <= 0.0 ? 0.0 : sum / total;
        return Math.max(0.0, Math.min(1.0, (value - 0.5) * s.noiseContrast + 0.5));
    }

    /** Like {@link #cell} but the distance is the largest of the three (a cube's), so the cells have flat faces: shards. [0] nearest, [1] next nearest, [2] 0 - 1 of the nearest. */
    static double[] cellBox(double x, double y, double z) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        int cz = (int) Math.floor(z);
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
                    double d = Math.max(Math.abs(x - fx), Math.max(Math.abs(y - fy), Math.abs(z - fz)));
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
