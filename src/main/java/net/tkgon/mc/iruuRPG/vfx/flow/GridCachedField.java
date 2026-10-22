package net.tkgon.mc.iruuRPG.vfx.flow;

/**
 * A {@link VectorField} worked out once on a grid (a cube of {@code cells} cells a side, {@code half} blocks from the centre each way) and read
 * by lookup, so a sample costs three array reads whatever the field is. It is the time-frozen slice of {@code source} at the time it was
 * made; outside the cube the velocity is zero. Meant for the day a flow is used in a combo: make the grid once, share it between flows.
 * (Cells are read without blending; a later version can blend between neighbours, or between two grids in time.)
 */
public final class GridCachedField implements VectorField {

    private final float[] data;
    private final int cells;
    private final double half;
    private final double scale;

    public GridCachedField(VectorField source, double half, int cells, double time) {
        this.cells = Math.max(2, Math.min(64, cells));
        this.half = half;
        this.scale = this.cells / (2.0 * half);
        this.data = new float[this.cells * this.cells * this.cells * 3];
        double[] out = new double[3];
        for (int ix = 0; ix < this.cells; ix++) {
            for (int iy = 0; iy < this.cells; iy++) {
                for (int iz = 0; iz < this.cells; iz++) {
                    source.sample((ix + 0.5) / scale - half, (iy + 0.5) / scale - half, (iz + 0.5) / scale - half, time, out);
                    int at = ((ix * this.cells + iy) * this.cells + iz) * 3;
                    data[at] = (float) out[0];
                    data[at + 1] = (float) out[1];
                    data[at + 2] = (float) out[2];
                }
            }
        }
    }

    @Override
    public void sample(double x, double y, double z, double t, double[] out) {
        int ix = (int) Math.floor((x + half) * scale);
        int iy = (int) Math.floor((y + half) * scale);
        int iz = (int) Math.floor((z + half) * scale);
        if (ix < 0 || iy < 0 || iz < 0 || ix >= cells || iy >= cells || iz >= cells) {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            return;
        }
        int at = ((ix * cells + iy) * cells + iz) * 3;
        out[0] = data[at];
        out[1] = data[at + 1];
        out[2] = data[at + 2];
    }
}
