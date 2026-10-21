package net.tkgon.mc.iruuRPG.vfx;

/**
 * Sine and cosine from a table made once (at class load): for the many effects whose points are worked out from angles
 * every tick, a lookup (with a linear step between two entries) is cheaper than {@code Math.sin} and the table is small
 * (2 x 4096 floats). The angle is in radians; any value is accepted.
 */
public final class Trig {

    private static final int SIZE = 4096;
    private static final int MASK = SIZE - 1;
    private static final double TURN = Math.PI * 2.0;
    private static final double SCALE = SIZE / TURN;
    private static final float[] SIN = new float[SIZE + 1];

    static {
        for (int index = 0; index <= SIZE; index++) SIN[index] = (float) Math.sin(TURN * index / SIZE);
    }

    private Trig() {
    }

    public static double sin(double radians) {
        double position = radians * SCALE;
        double floor = Math.floor(position);
        int index = ((int) floor) & MASK;
        double fraction = position - floor;
        return SIN[index] + (SIN[index + 1] - SIN[index]) * fraction;
    }

    public static double cos(double radians) {
        return sin(radians + Math.PI / 2.0);
    }
}
