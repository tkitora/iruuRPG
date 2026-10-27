package net.tkgon.mc.iruuRPG.mob;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The poise (体幹) of the mobs whose model has a {@code hurt} animation: not every hit plays it. Each hit fills the gauge (a part for the hit itself, and a part for the
 * share of the mob's life it took, so a big mob is not much slower to fill); when it is full the mob flinches: it plays {@code hurt} and stands still for a moment, and the
 * gauge starts again. The gauge drains when the mob is not hit for a while. Not a stun: a short flinch, every few hits.
 */
final class Poise {

    /** The numbers of one mob (config {@code poise:}, overridden by the mob's {@code model: poise:}). */
    record Settings(double max, double perHit, double damageScale, int drainDelayTicks, double drainPerTick, int flinchTicks, int minIntervalTicks) {

        static Settings of(ConfigurationSection defaults, Object override) {
            Map<?, ?> mob = override instanceof Map<?, ?> map ? map : Map.of();
            return new Settings(
                    Math.max(1.0, number(mob, defaults, "max", 100.0)),
                    Math.max(0.0, number(mob, defaults, "per-hit", 30.0)),
                    Math.max(0.0, number(mob, defaults, "damage-scale", 100.0)),
                    (int) Math.round(Math.max(0.0, number(mob, defaults, "drain-delay", 2.5)) * 20.0),
                    Math.max(0.0, number(mob, defaults, "drain-per-second", 30.0)) / 20.0,
                    (int) Math.round(Math.max(0.05, number(mob, defaults, "flinch-seconds", 0.35)) * 20.0),
                    (int) Math.round(Math.max(0.0, number(mob, defaults, "min-interval", 1.5)) * 20.0));
        }

        private static double number(Map<?, ?> mob, ConfigurationSection defaults, String key, double fallback) {
            if (mob.get(key) instanceof Number value) return value.doubleValue();
            return defaults == null ? fallback : defaults.getDouble(key, fallback);
        }
    }

    private static final class Gauge {
        double value;
        int lastHit;
        int lastFlinch = Integer.MIN_VALUE / 2;
    }

    private final Map<UUID, Gauge> gauges = new HashMap<>();

    /**
     * A hit of {@code damage} on a mob of {@code maxHp} life at tick {@code now}: true when the gauge is full and the last flinch was long enough ago (call
     * {@link #flinched} if the mob then flinches). A full gauge that cannot flinch yet stays full for the next hit.
     */
    boolean hit(UUID id, Settings settings, double damage, double maxHp, int now) {
        Gauge gauge = gauges.computeIfAbsent(id, key -> new Gauge());
        int quiet = now - gauge.lastHit - settings.drainDelayTicks();
        if (quiet > 0) gauge.value = Math.max(0.0, gauge.value - quiet * settings.drainPerTick());
        gauge.lastHit = now;
        double share = maxHp > 0.0 ? Math.max(0.0, damage) / maxHp : 0.0;
        gauge.value = Math.min(settings.max(), gauge.value + settings.perHit() + share * settings.damageScale());
        return gauge.value >= settings.max() && now - gauge.lastFlinch >= settings.minIntervalTicks();
    }

    void flinched(UUID id, int now) {
        Gauge gauge = gauges.get(id);
        if (gauge == null) return;
        gauge.value = 0.0;
        gauge.lastFlinch = now;
    }

    /** The gauge now (0..max), for the debug view; 0 when the mob was never hit. */
    double value(UUID id) {
        Gauge gauge = gauges.get(id);
        return gauge == null ? 0.0 : gauge.value;
    }

    void forget(UUID id) {
        gauges.remove(id);
    }

    void clear() {
        gauges.clear();
    }
}
