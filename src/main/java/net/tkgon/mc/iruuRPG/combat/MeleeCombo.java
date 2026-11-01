package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The light / heavy chain of melee attacks. A left click is a light attack: {@code light-count} lights in a row (each
 * within {@code window-ticks} of the one before) end with the finisher, the last light, which hits harder. A heavy
 * input ({@link HeavyInput}) is a heavy attack, thrown after the lights done so far; it ends the chain.
 *
 * <p>Two kinds of plan: the usual one (a weapon that is not a class's favorite) has one kind of heavy attack that hits
 * harder after more lights and takes longer to recover from. The plans of the combos ({@link ComboSpec}, registered with
 * {@link #register}) have five normal and five heavy attacks: heavy 0 - 4 (強1 - 強5) is chosen by the buttons ({@link HeavyInput}), each has a
 * cooldown of its own, and the chain of normal attacks goes on after it. The mage's ranged plan ({@link #MAGE}) has a spec of its own too
 * ({@link MageMoves#SPEC}).
 */
public final class MeleeCombo {

    public static final String USUAL = "";
    /** The mage's plan: the same chain on the ranged weapon (a right click is a normal attack, a left click a heavy one). */
    public static final String MAGE = "mage";

    public enum Kind { LIGHT, HEAVY, FINISHER }

    /**
     * One attack of the chain. {@code stage} is the number of this light (1-based; for a heavy, the number of lights
     * thrown before it). {@code extraHits}: further hits of the same strength a few ticks after the first (the
     * assassin's third light is three hits). {@code areaScale}: how much wider the splash around the hit is.
     * {@code areaRadius}: when above 0, the splash around the hit has exactly this radius (instead of the usual one
     * times {@code areaScale}). {@code heavyIndex}: which heavy attack of the plan (0 - 4), for a heavy.
     */
    public record Step(Kind kind, int stage, double damageMultiplier, int recoveryTicks, String plan, int heavyIndex,
                       int extraHits, double areaScale, double areaRadius) {

        public Step(Kind kind, int stage, double damageMultiplier, int recoveryTicks, String plan, int heavyIndex,
                    int extraHits, double areaScale) {
            this(kind, stage, damageMultiplier, recoveryTicks, plan, heavyIndex, extraHits, areaScale, 0.0);
        }

        /** An attack outside the chain (the chain is switched off, or a skill swings the weapon). */
        public static final Step PLAIN = new Step(Kind.LIGHT, 1, 1.0, 0, USUAL, 0, 0, 1.0);

        public boolean heavy() {
            return kind == Kind.HEAVY;
        }

        public boolean finisher() {
            return kind == Kind.FINISHER;
        }

        public boolean mage() {
            return MAGE.equals(plan);
        }

        public int visual() {
            return switch (kind) {
                case FINISHER, HEAVY -> 3;
                case LIGHT -> stage % 2 == 1 ? 1 : 2;
            };
        }
    }

    private static final int HEAVIES = 5;

    private final JavaPlugin plugin;
    /** The combos described by a {@link ComboSpec} (the newer ones), by plan. */
    private final Map<String, ComboSpec> specs = new HashMap<>();
    /** The running chain of each player and plan (the mage's ranged chain and their melee chain are separate). */
    private final Map<String, long[]> chains = new HashMap<>();

    private long[] chain(Player player, String plan) {
        return chains.computeIfAbsent(player.getUniqueId() + "|" + plan, ignored -> new long[]{0L, 0L});
    }
    /** When each heavy attack of each player's plan can be thrown again (a player can have several plans: one per kind of weapon). */
    private final Map<String, long[]> heavyReadyAt = new HashMap<>();

    public MeleeCombo(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Makes the plan of {@code spec} a combo of the generic kind (five normal and five heavy attacks). */
    public void register(ComboSpec spec) {
        specs.put(spec.plan(), spec);
    }

    private Step nextSpec(Player player, ComboSpec spec) {
        String plan = spec.plan();
        long now = System.currentTimeMillis();
        long[] state = chain(player, plan);
        int done = now - state[1] > window(plan) ? 0 : (int) state[0];
        state[1] = now;
        int stage = done + 1;
        shown(player, plan, Math.min(stage, 5));
        int at = Math.min(stage, 5) - 1;
        double rate = listed(plan + ".hit-rate", at, spec.rates()[at], 0.02, 5.0);
        int extra = (int) Math.max(0, listed(plan + ".hit-counts", at, spec.hits()[at], 1, 20) - 1);
        if (stage >= 5) {
            state[0] = 0L;
            return new Step(Kind.FINISHER, 5, rate, 0, plan, 0, extra, 1.0);
        }
        state[0] = stage;
        return new Step(Kind.LIGHT, stage, rate, 0, plan, 0, extra, 1.0);
    }

    private double number(String key, double fallback, double min, double max) {
        return Math.max(min, Math.min(max, plugin.getConfig().getDouble("combat.combo." + key, fallback)));
    }

    /** The i-th number of a list setting (the last one is used for the ones missing), or {@code fallback}. */
    /** Attack {@code stage} (1 - 5) of {@code plan} as a step, without touching the chain: a deployed weapon's placement makes its attacks in an order of its own. */
    public Step stageStep(String plan, int stage) {
        ComboSpec spec = specs.get(plan);
        if (spec == null) return Step.PLAIN;
        int at = Math.max(1, Math.min(5, stage)) - 1;
        double rate = listed(plan + ".hit-rate", at, spec.rates()[at], 0.02, 5.0);
        int extra = (int) Math.max(0, listed(plan + ".hit-counts", at, spec.hits()[at], 1, 20) - 1);
        return new Step(at == 4 ? Kind.FINISHER : Kind.LIGHT, at + 1, rate, 0, plan, 0, extra, 1.0);
    }

    /** Heavy attack {@code index} (0 - 4) of {@code plan} as a step, with no cooldown of its own (the placement allows it once). */
    public Step heavyStep(String plan, int index) {
        int at = Math.max(0, Math.min(HEAVIES - 1, index));
        return new Step(Kind.HEAVY, at, heavyMultiplier(plan, at), 0, plan, at, 0, 1.0);
    }

    private double listed(String key, int index, double fallback, double min, double max) {
        java.util.List<?> list = plugin.getConfig().getList("combat.combo." + key);
        if (list == null || list.isEmpty()) return fallback;
        Object item = list.get(Math.min(index, list.size() - 1));
        double value = item instanceof Number number ? number.doubleValue() : fallback;
        return Math.max(min, Math.min(max, value));
    }

    /** The next normal attack of {@code player}'s chain. */
    public Step next(Player player, String plan) {
        if (!plugin.getConfig().getBoolean("combat.combo.enabled", true)) return Step.PLAIN;
        ComboSpec spec = specs.get(plan);
        if (spec != null) return nextSpec(player, spec);
        return nextUsual(player, false);
    }

    /**
     * Heavy attack {@code index} (0 - 4: 強1 - 強5) of {@code plan}, chosen by the buttons. Null while it cools down. The chain of normal attacks
     * goes on after it (its time to the next normal attack starts again).
     */
    public Step heavyByInput(Player player, String plan, int index, boolean skipCooldown) {
        if (!plugin.getConfig().getBoolean("combat.combo.enabled", true)) return null;
        int at = Math.max(0, Math.min(HEAVIES - 1, index));
        Step step = heavy(player, plan, at, skipCooldown);
        if (step != null) {
            long[] state = chain(player, plan);
            long now = System.currentTimeMillis();
            if (now - state[1] <= window(plan)) state[1] = now;
        }
        return step;
    }

    /** The heavy attack of the usual plan (thrown after the lights done so far, which it ends); it has no cooldown, only its recovery. */
    public Step usualHeavy(Player player) {
        if (!plugin.getConfig().getBoolean("combat.combo.enabled", true)) return Step.PLAIN;
        return nextUsual(player, true);
    }

    public double heavyMultiplier(String plan, int index) {
        ComboSpec spec = specs.get(plan);
        if (spec != null) return listed(plan + ".heavy-damage-multiplier", index, spec.heavyRates()[Math.max(0, Math.min(4, index))], 0, 20);
        double[] defaults = new double[]{0.0, 0.6, 0.15, 0.6, 0.1};
        return listed(plan + ".heavy-damage-multiplier", index, defaults[Math.max(0, Math.min(4, index))], 0, 20);
    }

    private long window() {
        return Math.round(number("window-ticks", 32, 6, 200)) * 50L;
    }

    /** The ranger takes a moment to change weapons between the normal attacks, so its chain waits longer. */
    private long window(String plan) {
        ComboSpec spec = specs.get(plan);
        if (spec != null) return Math.round(number(plan + ".window-ticks", spec.windowTicks(), 6, 200)) * 50L;
        return window();
    }

    /** How many normal attacks of {@code plan}'s running chain were thrown (0 when it is not running). */
    public int done(Player player, String plan) {
        long[] state = chain(player, plan);
        return System.currentTimeMillis() - state[1] > window(plan) ? 0 : (int) state[0];
    }

    private Step nextUsual(Player player, boolean heavy) {
        int lights = (int) number("light-count", 5, 2, 9);
        long now = System.currentTimeMillis();
        long[] state = chain(player, USUAL);
        int done = now - state[1] > window() ? 0 : (int) state[0];
        state[1] = now;

        if (heavy) {
            state[0] = 0L;
            shown(player, USUAL, 0);
            double multiplier = number("heavy-damage-multiplier", 2.0, 1.0, 10.0) + number("heavy-stage-bonus", 0.15, 0.0, 2.0) * done;
            return new Step(Kind.HEAVY, done, multiplier, (int) number("heavy-recovery-ticks", 10, 0, 100), USUAL, 0, 0, 1.0);
        }
        int stage = done + 1;
        shown(player, USUAL, Math.min(stage, lights));
        if (stage >= lights) {
            state[0] = 0L;
            return new Step(Kind.FINISHER, lights, number("finisher-damage-multiplier", 2.0, 1.0, 10.0),
                    (int) number("finisher-recovery-ticks", 8, 0, 100), USUAL, 0, 0, 1.0);
        }
        state[0] = stage;
        return new Step(Kind.LIGHT, stage, 1.0, 0, USUAL, 0, 0, 1.0);
    }

    /**
     * The cooldowns the heavy attacks 強2 - 強5 have when the plan's own is 0 (they used to need the normal attacks before them, and had none):
     * 3, 4, 5 and 6 s.
     */
    private static final int[] LATER_COOLDOWN_TICKS = {0, 60, 80, 100, 120};

    /** The cooldown of heavy attack {@code index} of {@code plan} in ticks (the config's, else the plan's own). */
    public int heavyCooldownTicks(String plan, int index) {
        ComboSpec spec = specs.get(plan);
        int at = Math.max(0, Math.min(HEAVIES - 1, index));
        int own = spec == null ? (at == 0 ? 60 : 0) : spec.heavyCooldownTicks()[at];
        if (own == 0 && at > 0) own = LATER_COOLDOWN_TICKS[at];
        return (int) Math.round(listed(plan + ".heavy-cooldown-ticks", at, own, 0, 1200));
    }

    /** Heavy attack {@code index} of {@code plan}, or null while its own cooldown runs. */
    private Step heavy(Player player, String plan, int index, boolean skipCooldown) {
        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + "|" + plan;
        long[] ready = heavyReadyAt.computeIfAbsent(key, ignored -> new long[HEAVIES]);
        if (!skipCooldown) {
            if (ready[index] > now) return null;
            long cooldown = heavyCooldownTicks(plan, index) * 50L;
            ready[index] = now + cooldown;
            heavyTotal.computeIfAbsent(key, ignored -> new long[HEAVIES])[index] = cooldown;
        }
        double multiplier = heavyMultiplier(plan, index);
        return new Step(Kind.HEAVY, index, multiplier, 0, plan, index, 0, 1.0);
    }

    /** The whole cooldown last started for each heavy attack of each player's plan (milliseconds). */
    private final Map<String, long[]> heavyTotal = new HashMap<>();

    /** {milliseconds left, the whole cooldown} of heavy attack {@code index} of {@code plan} ({0, 0} when it is ready). */
    public long[] heavyCooldown(Player player, String plan, int index) {
        String key = player.getUniqueId() + "|" + plan;
        long[] ready = heavyReadyAt.get(key);
        int at = Math.max(0, Math.min(HEAVIES - 1, index));
        if (ready == null) return new long[]{0L, 0L};
        long left = ready[at] - System.currentTimeMillis();
        if (left <= 0L) return new long[]{0L, 0L};
        long[] total = heavyTotal.get(key);
        return new long[]{left, Math.max(left, total == null ? left : total[at])};
    }

    /** The number of the last light attack of the running chain (the finisher included); 0 when the chain is not running. */
    public int currentStage(Player player, String plan) {
        long[] last = lastShown.get(player.getUniqueId() + "|" + plan);
        if (last == null || System.currentTimeMillis() - last[1] > window(plan)) return 0;
        return (int) last[0];
    }

    /** {number of the last light, when} per player (kept after a finisher, which resets the chain, so the bar can show it). */
    private final Map<String, long[]> lastShown = new HashMap<>();

    private void shown(Player player, String plan, int stage) {
        lastShown.put(player.getUniqueId() + "|" + plan, new long[]{stage, System.currentTimeMillis()});
    }

    public void forget(UUID player) {
        chains.keySet().removeIf(key -> key.startsWith(player + "|"));
        heavyReadyAt.keySet().removeIf(key -> key.startsWith(player + "|"));
        heavyTotal.keySet().removeIf(key -> key.startsWith(player + "|"));
        lastShown.keySet().removeIf(key -> key.startsWith(player + "|"));
    }
}
