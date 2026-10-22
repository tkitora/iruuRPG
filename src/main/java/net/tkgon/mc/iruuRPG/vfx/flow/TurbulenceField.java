package net.tkgon.mc.iruuRPG.vfx.flow;

/**
 * Turbulence: each component of the velocity is its own noise, read at a different offset of the same space. It is not divergence free like
 * curl noise (particles bunch and spread a little) but costs three noise reads a sample instead of the six or more of a curl. The pattern
 * drifts along {@code drift} (blocks per tick of the noise space) so it flows over time, and {@code rise} is a steady push up.
 */
public final class TurbulenceField implements VectorField {

    private final NoiseSource noise;
    private final double frequency;
    private final double strength;
    private final double driftX;
    private final double driftY;
    private final double driftZ;
    private final double rise;

    public TurbulenceField(NoiseSource noise, double frequency, double strength, double drift, double rise) {
        this.noise = noise;
        this.frequency = frequency;
        this.strength = strength;
        this.driftX = drift * 0.8;
        this.driftY = drift * 0.3;
        this.driftZ = drift * 0.5;
        this.rise = rise;
    }

    @Override
    public void sample(double x, double y, double z, double t, double[] out) {
        double px = x * frequency + driftX * t;
        double py = y * frequency + driftY * t;
        double pz = z * frequency + driftZ * t;
        out[0] = noise.value(px, py, pz) * strength;
        out[1] = noise.value(px + 31.4, py + 17.7, pz + 5.1) * strength + rise;
        out[2] = noise.value(px + 11.9, py + 43.3, pz + 23.7) * strength;
    }
}
