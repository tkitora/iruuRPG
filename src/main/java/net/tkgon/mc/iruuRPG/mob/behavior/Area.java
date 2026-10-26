package net.tkgon.mc.iruuRPG.mob.behavior;

/**
 * The reach of an area attack, laid out from the mob along the way it faces: a {@code circle} of {@code radius} round a point {@code forward} blocks ahead, a {@code cone} of
 * {@code radius} and {@code angle} degrees from the mob, or a {@code line} (a box) {@code radius} blocks long and {@code width} wide ahead of it. A player more than
 * {@code height} blocks above or below the mob is not reached.
 */
public record Area(String shape, double forward, double radius, double angle, double width, double height) {
}
