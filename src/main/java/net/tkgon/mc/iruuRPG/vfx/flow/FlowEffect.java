package net.tkgon.mc.iruuRPG.vfx.flow;

import java.util.List;
import java.util.Random;

/**
 * A light fluid-like effect: particles born near a centre and carried by a {@link VectorField}, drawn as dust along an RGB gradient (see
 * {@link FlowRunner}). Every flow can be run in two frames: {@code absolute} (a particle stays where the world left it when the centre moves, so a
 * moving centre leaves a trail) or {@code relative} (the particles move with the centre). Add an effect by implementing this and listing it in
 * {@link FlowEffects}; {@code /vfx list}, {@code /vfx help <id>} and {@code /vfx test <id> ...} then work for it with no more code.
 */
public interface FlowEffect {

    /** The name used in the commands (lower case). */
    String id();

    /** One line for {@code /vfx list}. */
    String summary();

    /** The arguments after the duration in {@code /vfx test}, in order (the common ones of {@link BaseFlow} come last). */
    List<Param> params();

    /** The field of one flow. {@code seed} is fresh each time: use it for any random choice so two flows differ. */
    VectorField field(FlowArgs args, long seed);

    /** Where a new particle starts (relative to the centre) and its first velocity, written into the arrays. */
    void spawn(Random random, FlowArgs args, double[] position, double[] velocity);

    /** How much of the field's velocity a particle takes each tick (0 - 1): small is lazy and smooth, 1 follows the field exactly. Ignored when {@link #accelerates}. */
    default double inertia() {
        return 0.3;
    }

    /** True when the field gives an acceleration (added to the velocity every tick, with a little drag) rather than the velocity itself. */
    default boolean accelerates() {
        return false;
    }
}
