package net.tkgon.mc.iruuRPG.vfx.flow;

import net.tkgon.mc.iruuRPG.vfx.Trig;

import java.util.Random;

/**
 * A smooth random-looking number in [-1, 1] at every point of space. The two kinds here are far lighter than curl noise: {@link #sine}
 * adds a few waves (two trig lookups an octave), {@link #value} blends hashed lattice values (eight hashes an octave). Both take an
 * octave count, so the cost is chosen by the caller; to try another kind of noise, implement this interface and add it to {@link #create}.
 */
public interface NoiseSource {

    double value(double x, double y, double z);

    /** The kinds {@link #create} knows. */
    java.util.List<String> TYPES = java.util.List.of("sine", "value");

    static NoiseSource create(String type, int octaves, long seed) {
        int count = Math.max(1, Math.min(4, octaves));
        return "value".equalsIgnoreCase(type) ? value(count, seed) : sine(count, seed);
    }

    /** Waves of growing frequency and shrinking height, each a product of a sine and a cosine along two fixed directions. */
    static NoiseSource sine(int octaves, long seed) {
        Random random = new Random(seed);
        double[][] directions = new double[octaves * 2][3];
        double[] phases = new double[octaves * 2];
        for (int index = 0; index < directions.length; index++) {
            double x = random.nextGaussian();
            double y = random.nextGaussian();
            double z = random.nextGaussian();
            double length = Math.max(1.0E-6, Math.sqrt(x * x + y * y + z * z));
            directions[index][0] = x / length;
            directions[index][1] = y / length;
            directions[index][2] = z / length;
            phases[index] = random.nextDouble() * Math.PI * 2.0;
        }
        return (x, y, z) -> {
            double sum = 0.0;
            double total = 0.0;
            double amplitude = 1.0;
            double frequency = 1.0;
            for (int octave = 0; octave < octaves; octave++) {
                double[] a = directions[octave * 2];
                double[] b = directions[octave * 2 + 1];
                double s = Trig.sin(frequency * (a[0] * x + a[1] * y + a[2] * z) + phases[octave * 2]);
                double c = Trig.cos(frequency * 1.37 * (b[0] * x + b[1] * y + b[2] * z) + phases[octave * 2 + 1]);
                sum += amplitude * (0.6 * s + 0.4 * c);
                total += amplitude;
                amplitude *= 0.5;
                frequency *= 2.03;
            }
            return sum / total;
        };
    }

    /** Value noise: random values at the lattice points, blended smoothly between them, summed over the octaves. */
    static NoiseSource value(int octaves, long seed) {
        return (x, y, z) -> {
            double sum = 0.0;
            double total = 0.0;
            double amplitude = 1.0;
            double frequency = 1.0;
            for (int octave = 0; octave < octaves; octave++) {
                sum += amplitude * lattice(x * frequency, y * frequency, z * frequency, seed + octave * 7919L);
                total += amplitude;
                amplitude *= 0.5;
                frequency *= 2.0;
            }
            return sum / total;
        };
    }

    private static double lattice(double x, double y, double z, long seed) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = smooth(x - ix);
        double fy = smooth(y - iy);
        double fz = smooth(z - iz);
        double c000 = hash(ix, iy, iz, seed);
        double c100 = hash(ix + 1, iy, iz, seed);
        double c010 = hash(ix, iy + 1, iz, seed);
        double c110 = hash(ix + 1, iy + 1, iz, seed);
        double c001 = hash(ix, iy, iz + 1, seed);
        double c101 = hash(ix + 1, iy, iz + 1, seed);
        double c011 = hash(ix, iy + 1, iz + 1, seed);
        double c111 = hash(ix + 1, iy + 1, iz + 1, seed);
        double x00 = c000 + (c100 - c000) * fx;
        double x10 = c010 + (c110 - c010) * fx;
        double x01 = c001 + (c101 - c001) * fx;
        double x11 = c011 + (c111 - c011) * fx;
        double y0 = x00 + (x10 - x00) * fy;
        double y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
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
