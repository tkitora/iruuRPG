package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The light / heavy chain of melee attacks. A left click is a light attack: {@code light-count} lights in a row (each
 * within {@code window-ticks} of the one before) end with the finisher, the last light, which hits harder. A shift +
 * left click is a heavy attack: it can be thrown after any light, hits harder than the finisher, takes longer to
 * recover from, and ends the chain.
 */
public final class MeleeCombo {

    public enum Kind { LIGHT, HEAVY, FINISHER }

    /**
     * One attack of the chain. {@code stage} is the number of this light (1-based; for a heavy, the number of lights
     * thrown before it), {@code visual} 1 / 2 / 3 picks the slash that is drawn (3 = the big one).
     */
    public record Step(Kind kind, int stage, double damageMultiplier, int recoveryTicks) {

        /** An attack outside the chain (the chain is switched off, or a skill swings the weapon). */
        public static final Step PLAIN = new Step(Kind.LIGHT, 1, 1.0, 0);

        public boolean heavy() {
            return kind == Kind.HEAVY;
        }

        public boolean finisher() {
            return kind == Kind.FINISHER;
        }

        public int visual() {
            return switch (kind) {
                case FINISHER, HEAVY -> 3;
                case LIGHT -> stage % 2 == 1 ? 1 : 2;
            };
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, long[]> chains = new HashMap<>();

    public MeleeCombo(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private double number(String key, double fallback, double min, double max) {
        return Math.max(min, Math.min(max, plugin.getConfig().getDouble("combat.combo." + key, fallback)));
    }

    /** The next attack of {@code player}'s chain; {@code heavy} for the shift + left click. */
    public Step next(Player player, boolean heavy) {
        if (!plugin.getConfig().getBoolean("combat.combo.enabled", true)) return Step.PLAIN;

        int lights = (int) number("light-count", 5, 2, 9);
        long window = Math.round(number("window-ticks", 32, 6, 200)) * 50L;
        long now = System.currentTimeMillis();
        long[] state = chains.computeIfAbsent(player.getUniqueId(), ignored -> new long[]{0L, 0L});
        int done = now - state[1] > window ? 0 : (int) state[0];
        state[1] = now;

        if (heavy) {
            state[0] = 0L;
            double multiplier = number("heavy-damage-multiplier", 2.0, 1.0, 10.0) + number("heavy-stage-bonus", 0.15, 0.0, 2.0) * done;
            return new Step(Kind.HEAVY, done, multiplier, (int) number("heavy-recovery-ticks", 10, 0, 100));
        }
        int stage = done + 1;
        if (stage >= lights) {
            state[0] = 0L;
            return new Step(Kind.FINISHER, lights, number("finisher-damage-multiplier", 2.0, 1.0, 10.0),
                    (int) number("finisher-recovery-ticks", 8, 0, 100));
        }
        state[0] = stage;
        return new Step(Kind.LIGHT, stage, 1.0, 0);
    }

    public void forget(UUID player) {
        chains.remove(player);
    }
}
