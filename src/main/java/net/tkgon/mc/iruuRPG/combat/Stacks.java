package net.tkgon.mc.iruuRPG.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stacks a player builds up and spends (t168: the cleric's radiance with a mace, its verses with a staff): a count up to a cap, all of them lost
 * when none was added for some time. Only the counting is here (times in milliseconds), so that it can be tested.
 */
final class Stacks {

    /** {count, until (ms)} of each player. */
    private final Map<UUID, long[]> counts = new ConcurrentHashMap<>();

    /** {@code amount} more for {@code player} (at most {@code max}), all of them lasting until {@code until}; the count now. */
    int add(UUID player, int amount, int max, long until, long now) {
        long[] old = counts.get(player);
        long before = old == null || old[1] < now ? 0L : old[0];
        int count = (int) Math.max(0L, Math.min(Math.max(1, max), before + amount));
        if (count == 0) {
            counts.remove(player);
            return 0;
        }
        counts.put(player, new long[]{count, until});
        return count;
    }

    /** The stacks of {@code player} at {@code now} (0 when none or gone). */
    int count(UUID player, long now) {
        long[] state = counts.get(player);
        if (state == null) return 0;
        if (state[1] < now) {
            counts.remove(player, state);
            return 0;
        }
        return (int) state[0];
    }

    /** Spends all the stacks of {@code player}: how many there were. */
    int take(UUID player, long now) {
        long[] state = counts.remove(player);
        return state == null || state[1] < now ? 0 : (int) state[0];
    }

    void forget(UUID player) {
        counts.remove(player);
    }

    void clear() {
        counts.clear();
    }
}
