package net.tkgon.mc.iruuRPG.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The marks of death the reaper (the mage with a scythe, t167) cuts into the enemies: each reaper's own, up to a number on one enemy, for some
 * seconds after the last one (a new one makes them all last again). The reaper's skills reap them: they are taken off, and each one adds to the
 * blow and gives back a soul ({@link MageReaper}). Only the counting is here (times in milliseconds), so that it can be tested.
 */
final class ReaperMarks {

    /** {@code count} marks on an enemy until {@code until} (milliseconds). */
    record Mark(int count, long until) {
    }

    /** The marks of each reaper, by the enemy. */
    private static final Map<UUID, Map<UUID, Mark>> MARKS = new ConcurrentHashMap<>();

    private ReaperMarks() {
    }

    /** {@code amount} more marks of {@code owner} on {@code target} (at most {@code max}), all of them lasting until {@code until}; the count now. */
    static int add(UUID owner, UUID target, int amount, int max, long until, long now) {
        Map<UUID, Mark> marks = MARKS.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>());
        Mark old = marks.get(target);
        int before = old == null || old.until() < now ? 0 : old.count();
        int count = Math.max(0, Math.min(Math.max(1, max), before + amount));
        if (count == 0) return 0;
        marks.put(target, new Mark(count, until));
        return count;
    }

    /** The marks of {@code owner} on {@code target} at {@code now} (0 when none or gone). */
    static int count(UUID owner, UUID target, long now) {
        Map<UUID, Mark> marks = MARKS.get(owner);
        Mark mark = marks == null ? null : marks.get(target);
        return mark == null || mark.until() < now ? 0 : mark.count();
    }

    /** Reaps the marks of {@code owner} on {@code target}: they are taken off; how many there were. */
    static int take(UUID owner, UUID target, long now) {
        Map<UUID, Mark> marks = MARKS.get(owner);
        Mark mark = marks == null ? null : marks.remove(target);
        return mark == null || mark.until() < now ? 0 : mark.count();
    }

    /** The enemies {@code owner} has marks on at {@code now} and how many; the ones gone are dropped. */
    static Map<UUID, Integer> all(UUID owner, long now) {
        Map<UUID, Mark> marks = MARKS.get(owner);
        Map<UUID, Integer> out = new HashMap<>();
        if (marks == null) return out;
        marks.entrySet().removeIf(entry -> entry.getValue().until() < now);
        for (Map.Entry<UUID, Mark> entry : marks.entrySet()) out.put(entry.getKey(), entry.getValue().count());
        if (marks.isEmpty()) MARKS.remove(owner, marks);
        return out;
    }

    /** The reaper left: their marks go. */
    static void forget(UUID owner) {
        MARKS.remove(owner);
    }
}
