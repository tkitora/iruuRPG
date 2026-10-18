package net.tkgon.mc.iruuRPG.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What each player has dealt: the damage per second of the last few seconds, and the total dealt while the class
 * skill is up (and a moment after). Fed from the damage path of normal attacks, skills and class skills.
 */
public final class DamageMeter {

    /** The window the damage per second is taken over. */
    private static final long WINDOW_MILLIS = 5000L;
    /** The total of a class skill stays on the bar this long after it ends. */
    private static final long LINGER_MILLIS = 3000L;

    private record Hit(long at, double amount) {
    }

    private static final class Session {
        private long activeUntil;
        private long shownUntil;
        private double total;
    }

    private final Map<UUID, Deque<Hit>> hits = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    /** {@code amount} of damage dealt by {@code player} just now. */
    public void record(UUID player, double amount) {
        if (amount <= 0.0) return;
        long now = System.currentTimeMillis();
        Deque<Hit> list = hits.computeIfAbsent(player, ignored -> new ArrayDeque<>());
        list.addLast(new Hit(now, amount));
        trim(list, now);
        Session session = sessions.get(player);
        if (session != null && now <= session.activeUntil) session.total += amount;
    }

    private void trim(Deque<Hit> list, long now) {
        while (!list.isEmpty() && now - list.peekFirst().at() > WINDOW_MILLIS) list.pollFirst();
    }

    /** Damage per second over the last few seconds (the time since the first hit if that is shorter). */
    public double dps(UUID player) {
        Deque<Hit> list = hits.get(player);
        if (list == null) return 0.0;
        long now = System.currentTimeMillis();
        trim(list, now);
        if (list.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Hit hit : list) sum += hit.amount();
        double seconds = Math.max(1.0, Math.min(WINDOW_MILLIS, now - list.peekFirst().at()) / 1000.0);
        return sum / seconds;
    }

    /** The class skill was cast: count the damage dealt for the next {@code activeMillis}. */
    public void startSkill(UUID player, long activeMillis) {
        long now = System.currentTimeMillis();
        Session session = new Session();
        session.activeUntil = now + Math.max(0L, activeMillis);
        session.shownUntil = session.activeUntil + LINGER_MILLIS;
        sessions.put(player, session);
    }

    /** True while the class skill is up (its damage is being counted). */
    public boolean skillActive(UUID player) {
        Session session = sessions.get(player);
        return session != null && System.currentTimeMillis() <= session.activeUntil;
    }

    /** True while the total is on the bar: the skill is up, or ended a moment ago. */
    public boolean skillTotalShown(UUID player) {
        Session session = sessions.get(player);
        return session != null && System.currentTimeMillis() <= session.shownUntil;
    }

    public double skillTotal(UUID player) {
        Session session = sessions.get(player);
        return session == null ? 0.0 : session.total;
    }

    public void forget(UUID player) {
        hits.remove(player);
        sessions.remove(player);
    }
}
