package net.tkgon.mc.iruuRPG.mob;

import java.util.Locale;

/**
 * How a ranged mob behaves (the "ranged:" section of a mob in yml). Explore mode: it walks like any mob and closes in
 * on its target. Attack mode: once its target is within {@code range} blocks with a clear line, it stands still and
 * shoots every {@code intervalTicks}; each shot hits with {@code accuracy} percent and otherwise visibly misses.
 *
 * @param range         attack range (blocks)
 * @param accuracy      hit chance of a shot, 0-100
 * @param intervalTicks time between shots (20 ticks = 1 s)
 * @param projectile    look and sound of the shot: ARROW, FIRE or MAGIC
 */
public record RangedSpec(double range, double accuracy, int intervalTicks, String projectile) {

    public static final double DEFAULT_RANGE = 14.0;
    public static final double DEFAULT_ACCURACY = 75.0;
    public static final int DEFAULT_INTERVAL = 40;

    public RangedSpec {
        range = Math.max(2.0, range);
        accuracy = Math.max(0.0, Math.min(100.0, accuracy));
        intervalTicks = Math.max(5, intervalTicks);
        projectile = projectile == null || projectile.isBlank() ? "ARROW" : projectile.trim().toUpperCase(Locale.ROOT);
    }

    public static RangedSpec defaults() {
        return new RangedSpec(DEFAULT_RANGE, DEFAULT_ACCURACY, DEFAULT_INTERVAL, "ARROW");
    }

    public static boolean knownProjectile(String name) {
        return name != null && switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "ARROW", "FIRE", "MAGIC" -> true;
            default -> false;
        };
    }
}
