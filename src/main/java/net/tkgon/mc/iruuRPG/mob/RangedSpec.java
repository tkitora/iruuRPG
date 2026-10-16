package net.tkgon.mc.iruuRPG.mob;

/**
 * How a ranged mob behaves (the "ranged:" section of a mob in yml). Explore mode: it walks like any mob and closes in
 * on its target. Attack mode: once its target is within {@code range} blocks with a clear line, it stands still and
 * fires a bolt every {@code intervalTicks}; the bolt flies at {@code speed} blocks per second and hits whoever it
 * reaches (a player who keeps moving can dodge it). The bolt's colors and its damage come from the mob's own stats
 * and weapon.
 *
 * @param range         attack range (blocks)
 * @param speed         bolt speed (blocks per second)
 * @param intervalTicks time between shots (20 ticks = 1 s)
 */
public record RangedSpec(double range, double speed, int intervalTicks) {

    public static final double DEFAULT_RANGE = 14.0;
    public static final double DEFAULT_SPEED = 12.0;
    public static final int DEFAULT_INTERVAL = 40;

    public RangedSpec {
        range = Math.max(2.0, range);
        speed = Math.max(2.0, Math.min(60.0, speed));
        intervalTicks = Math.max(5, intervalTicks);
    }

    public static RangedSpec defaults() {
        return new RangedSpec(DEFAULT_RANGE, DEFAULT_SPEED, DEFAULT_INTERVAL);
    }
}
