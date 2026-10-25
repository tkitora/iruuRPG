package net.tkgon.mc.iruuRPG.mob.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the mobs run by behavior trees share, so that they can act as a group: the attack slots (tokens) that limit how many of them do a thing at once against one target, and the
 * cooldowns that several of them have in common. One per server; the entries run out by themselves ({@code until}), so a mob that dies holding a slot does not keep it.
 */
public final class Coordinator {

    private record Holder(UUID mob, int until) {
    }

    private final Map<String, List<Holder>> slots = new HashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private int sweptAt;

    private List<Holder> live(String key, int now) {
        List<Holder> list = slots.computeIfAbsent(key, ignored -> new ArrayList<>());
        for (Iterator<Holder> iterator = list.iterator(); iterator.hasNext(); ) if (iterator.next().until() <= now) iterator.remove();
        return list;
    }

    /** Whether {@code mob} holds the slot or one is free ({@code max} at most hold it at once). */
    boolean available(String key, UUID mob, int max, int now) {
        List<Holder> list = live(key, now);
        for (Holder holder : list) if (holder.mob().equals(mob)) return true;
        return list.size() < max;
    }

    /** Takes the slot for {@code ticks}; false when it is full. */
    boolean acquire(String key, UUID mob, int max, int ticks, int now) {
        List<Holder> list = live(key, now);
        list.removeIf(holder -> holder.mob().equals(mob));
        if (list.size() >= max) return false;
        list.add(new Holder(mob, now + ticks));
        sweep(now);
        return true;
    }

    void release(String key, UUID mob) {
        List<Holder> list = slots.get(key);
        if (list != null) list.removeIf(holder -> holder.mob().equals(mob));
    }

    /** The number of mobs that hold a slot against {@code target} (any name). */
    public int attackers(String target, int now) {
        int count = 0;
        for (Map.Entry<String, List<Holder>> entry : slots.entrySet()) {
            if (!entry.getKey().endsWith("@" + target)) continue;
            count += live(entry.getKey(), now).size();
        }
        return count;
    }

    boolean ready(String key, int now) {
        return now >= cooldowns.getOrDefault(key, 0);
    }

    void start(String key, int now, int ticks) {
        cooldowns.put(key, now + ticks);
        sweep(now);
    }

    /** Now and then, forgets what ran out. */
    private void sweep(int now) {
        if (now - sweptAt < 600) return;
        sweptAt = now;
        cooldowns.values().removeIf(readyAt -> readyAt <= now);
        slots.values().removeIf(list -> {
            list.removeIf(holder -> holder.until() <= now);
            return list.isEmpty();
        });
    }
}
