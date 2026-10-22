package net.tkgon.mc.iruuRPG.vfx.flow;

/**
 * A velocity at every point: what moves the particles of a flow. Coordinates are the flow's own frame (blocks from the centre of the
 * effect), {@code t} is the time in ticks. Implementations must be cheap (a handful of trig calls or lookups): a flow asks for
 * every particle every tick. To make a field cheaper later, wrap it in a {@link GridCachedField}.
 */
public interface VectorField {

    /** Writes the velocity (blocks per tick) at ({@code x}, {@code y}, {@code z}) at time {@code t} into {@code out[0..2]}. */
    void sample(double x, double y, double z, double t, double[] out);
}
