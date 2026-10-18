package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The light / heavy chain of melee attacks. A left click is a light attack: {@code light-count} lights in a row (each
 * within {@code window-ticks} of the one before) end with the finisher, the last light, which hits harder. A shift +
 * left click is a heavy attack, thrown after the lights done so far; it ends the chain.
 *
 * <p>Two plans: the usual one (any class) has one kind of heavy attack that hits harder after more lights and takes
 * longer to recover from. The assassin plan ({@link #ASSASSIN}) has five lights (the fifth is the finisher) and five
 * heavy attacks, 0 - 4, picked by the number of lights thrown before it (heavy 0 is the right click; a shift + click
 * before any light is an ordinary light attack), each with a cooldown of its own.
 */
public final class MeleeCombo {

    public static final String USUAL = "";
    public static final String ASSASSIN = "assassin";

    public enum Kind { LIGHT, HEAVY, FINISHER }

    /**
     * One attack of the chain. {@code stage} is the number of this light (1-based; for a heavy, the number of lights
     * thrown before it). {@code extraHits}: further hits of the same strength a few ticks after the first (the
     * assassin's third light is three hits). {@code areaScale}: how much wider the splash around the hit is.
     * {@code heavyIndex}: which heavy attack of the plan (0 - 3), for a heavy.
     */
    public record Step(Kind kind, int stage, double damageMultiplier, int recoveryTicks, String plan, int heavyIndex,
                       int extraHits, double areaScale) {

        /** An attack outside the chain (the chain is switched off, or a skill swings the weapon). */
        public static final Step PLAIN = new Step(Kind.LIGHT, 1, 1.0, 0, USUAL, 0, 0, 1.0);

        public boolean heavy() {
            return kind == Kind.HEAVY;
        }

        public boolean finisher() {
            return kind == Kind.FINISHER;
        }

        public boolean assassin() {
            return ASSASSIN.equals(plan);
        }

        public int visual() {
            return switch (kind) {
                case FINISHER, HEAVY -> 3;
                case LIGHT -> stage % 2 == 1 ? 1 : 2;
            };
        }
    }

    private static final int ASSASSIN_LIGHTS = 5;
    private static final int ASSASSIN_HEAVIES = 5;

    private final JavaPlugin plugin;
    private final Map<UUID, long[]> chains = new HashMap<>();
    private final Map<UUID, long[]> heavyReadyAt = new HashMap<>();

    public MeleeCombo(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private double number(String key, double fallback, double min, double max) {
        return Math.max(min, Math.min(max, plugin.getConfig().getDouble("combat.combo." + key, fallback)));
    }

    /** The i-th number of a list setting (the last one is used for the ones missing), or {@code fallback}. */
    private double listed(String key, int index, double fallback, double min, double max) {
        java.util.List<?> list = plugin.getConfig().getList("combat.combo." + key);
        if (list == null || list.isEmpty()) return fallback;
        Object item = list.get(Math.min(index, list.size() - 1));
        double value = item instanceof Number number ? number.doubleValue() : fallback;
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The next attack of {@code player}'s chain; {@code heavy} for the shift + left click. Null when it is a heavy
     * attack that is still cooling down (nothing is thrown, and the chain stays as it is).
     */
    public Step next(Player player, boolean heavy, String plan) {
        if (!plugin.getConfig().getBoolean("combat.combo.enabled", true)) return Step.PLAIN;
        return ASSASSIN.equals(plan) ? nextAssassin(player, heavy) : nextUsual(player, heavy);
    }

    /** Heavy attack 0 (the right click of the assassin plan); null while it cools down. */
    public Step heavyZero(Player player) {
        if (!plugin.getConfig().getBoolean("combat.combo.enabled", true)) return null;
        long[] state = chains.computeIfAbsent(player.getUniqueId(), ignored -> new long[]{0L, 0L});
        Step step = heavy(player, ASSASSIN, 0);
        if (step != null) {
            state[0] = 0L;
            state[1] = System.currentTimeMillis();
        }
        return step;
    }

    private long window() {
        return Math.round(number("window-ticks", 32, 6, 200)) * 50L;
    }

    private Step nextUsual(Player player, boolean heavy) {
        int lights = (int) number("light-count", 5, 2, 9);
        long now = System.currentTimeMillis();
        long[] state = chains.computeIfAbsent(player.getUniqueId(), ignored -> new long[]{0L, 0L});
        int done = now - state[1] > window() ? 0 : (int) state[0];
        state[1] = now;

        if (heavy) {
            state[0] = 0L;
            double multiplier = number("heavy-damage-multiplier", 2.0, 1.0, 10.0) + number("heavy-stage-bonus", 0.15, 0.0, 2.0) * done;
            return new Step(Kind.HEAVY, done, multiplier, (int) number("heavy-recovery-ticks", 10, 0, 100), USUAL, 0, 0, 1.0);
        }
        int stage = done + 1;
        if (stage >= lights) {
            state[0] = 0L;
            return new Step(Kind.FINISHER, lights, number("finisher-damage-multiplier", 2.0, 1.0, 10.0),
                    (int) number("finisher-recovery-ticks", 8, 0, 100), USUAL, 0, 0, 1.0);
        }
        state[0] = stage;
        return new Step(Kind.LIGHT, stage, 1.0, 0, USUAL, 0, 0, 1.0);
    }

    private Step nextAssassin(Player player, boolean heavy) {
        long now = System.currentTimeMillis();
        long[] state = chains.computeIfAbsent(player.getUniqueId(), ignored -> new long[]{0L, 0L});
        int done = now - state[1] > window() ? 0 : (int) state[0];

        // shift + click with no light thrown yet is an ordinary light attack (heavy 0 is the right click only)
        if (heavy && done > 0) {
            Step step = heavy(player, ASSASSIN, Math.min(done, ASSASSIN_HEAVIES - 1));
            if (step != null) {
                state[0] = 0L;
                state[1] = now;
            }
            return step;
        }
        state[1] = now;
        int stage = done + 1;
        if (stage >= ASSASSIN_LIGHTS) {
            state[0] = 0L;
            return new Step(Kind.FINISHER, ASSASSIN_LIGHTS, number("assassin.finisher-damage-multiplier", 1.5, 0.5, 10.0), 0,
                    ASSASSIN, 0, 0, number("assassin.finisher-area-scale", 1.5, 1.0, 3.0));
        }
        state[0] = stage;
        if (stage == 3 || stage == 4) {
            // several hits in front: the third light is 3 hits of 40%, the fourth 5 hits of 25%
            int which = stage - 3;
            double rate = listed("assassin.multi-hit-rate", which, new double[]{0.4, 0.25}[which], 0.05, 2.0);
            int hits = (int) listed("assassin.multi-hit-counts", which, new double[]{3, 5}[which], 2, 10);
            return new Step(Kind.LIGHT, stage, rate, 0, ASSASSIN, 0, hits - 1, 1.0);
        }
        return new Step(Kind.LIGHT, stage, 1.0, 0, ASSASSIN, 0, 0, 1.0);
    }

    /** Heavy attack {@code index} of the assassin plan, or null while its own cooldown runs. */
    private Step heavy(Player player, String plan, int index) {
        long now = System.currentTimeMillis();
        long[] ready = heavyReadyAt.computeIfAbsent(player.getUniqueId(), ignored -> new long[ASSASSIN_HEAVIES]);
        if (ready[index] > now) return null;
        long cooldownTicks = Math.round(listed("assassin.heavy-cooldown-ticks", index, new double[]{40, 60, 80, 100, 120}[index], 0, 1200));
        ready[index] = now + cooldownTicks * 50L;
        double multiplier = listed("assassin.heavy-damage-multiplier", index, new double[]{0.0, 2.2, 0.4, 0.7, 0.1}[index], 0, 20);
        return new Step(Kind.HEAVY, index, multiplier, 0, plan, index, 0, 1.0);
    }

    /** Seconds until heavy attack {@code index} can be thrown again (0 when ready). */
    public double heavyRemainingSeconds(Player player, int index) {
        long[] ready = heavyReadyAt.get(player.getUniqueId());
        if (ready == null || index < 0 || index >= ready.length) return 0.0;
        return Math.max(0.0, (ready[index] - System.currentTimeMillis()) / 1000.0);
    }

    public void forget(UUID player) {
        chains.remove(player);
        heavyReadyAt.remove(player);
    }
}
