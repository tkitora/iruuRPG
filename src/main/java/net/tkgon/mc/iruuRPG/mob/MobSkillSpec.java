package net.tkgon.mc.iruuRPG.mob;

import java.util.Locale;

/**
 * One skill of a mob (an entry of the "skills:" list in yml). Types:
 * <ul>
 *   <li>{@code strike}: a ground slam; a circle of {@code radius} around the mob is marked, then hit after the windup</li>
 *   <li>{@code sweep}: a wide cut in front of the mob ({@code range} blocks, 130 degrees), marked, then hit</li>
 *   <li>{@code barrage}: a melee mob that stops and fires {@code count} bolts one after the other, {@code intervalTicks}
 *       apart, at the player (the bolts fly at {@code speed} blocks per second and can be dodged)</li>
 *   <li>{@code dash}: marks a line toward the player, then lunges along it and hits what it reaches</li>
 *   <li>{@code wave}: marks a line, then sends a slash flying toward the player ({@code range} blocks, {@code speed}
 *       blocks per second, {@code radius} wide); it passes through, hits each player it crosses once, and stops at a wall</li>
 * </ul>
 * The damage of every hit is the mob's normal damage times {@code damageRate}.
 */
public record MobSkillSpec(
        Type type,
        int cooldownTicks,
        double range,
        double radius,
        int windupTicks,
        double damageRate,
        int count,
        int intervalTicks,
        double speed
) {

    public enum Type {
        STRIKE, SWEEP, BARRAGE, DASH, WAVE;

        public static Type parse(String raw) {
            if (raw == null) return null;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public MobSkillSpec {
        cooldownTicks = Math.max(20, cooldownTicks);
        range = Math.max(1.0, range);
        radius = Math.max(1.0, radius);
        windupTicks = Math.max(4, windupTicks);
        damageRate = Math.max(0.0, damageRate);
        count = Math.max(1, Math.min(20, count));
        intervalTicks = Math.max(2, intervalTicks);
        speed = Math.max(2.0, Math.min(60.0, speed));
    }

    /** The defaults of a type, used for every value the yml leaves out. */
    public static MobSkillSpec defaults(Type type) {
        return switch (type) {
            case STRIKE -> new MobSkillSpec(type, 160, 4.5, 3.5, 24, 1.8, 1, 4, 12.0);
            case SWEEP -> new MobSkillSpec(type, 120, 4.5, 4.5, 14, 1.3, 1, 4, 12.0);
            case BARRAGE -> new MobSkillSpec(type, 200, 12.0, 1.0, 12, 0.5, 5, 5, 14.0);
            case DASH -> new MobSkillSpec(type, 180, 10.0, 1.0, 16, 1.5, 1, 4, 12.0);
            case WAVE -> new MobSkillSpec(type, 140, 16.0, 1.3, 16, 1.2, 1, 4, 14.0);
        };
    }
}
