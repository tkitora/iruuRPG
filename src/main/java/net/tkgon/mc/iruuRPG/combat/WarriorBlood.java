package net.tkgon.mc.iruuRPG.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The warrior's wounds (t168, the blood knight of {@link WarriorSkills}): a share of the damage a warrior takes stays as a <b>wound</b> that can be
 * drunk back by hitting enemies with a melee weapon. A wound fades to nothing over some seconds after the last damage (a new one adds to what is left
 * and starts the fading again); while it is held ({@link #hold}) it does not fade. Only the counting is here (times in milliseconds), so that it can
 * be tested.
 */
final class WarriorBlood {

    /** A wound of {@code amount} that starts fading at {@code since} and is held until {@code heldUntil}. */
    static final class Wound {
        double amount;
        long since;
        long heldUntil;
    }

    private static final Map<UUID, Wound> WOUNDS = new ConcurrentHashMap<>();

    private WarriorBlood() {
    }

    /** What is left of {@code wound} at {@code now}: all of it before it starts fading, then less and less until {@code fadeMillis} later. */
    static double left(Wound wound, long fadeMillis, long now) {
        if (wound == null || wound.amount <= 0.0) return 0.0;
        if (now <= wound.since) return wound.amount;
        if (fadeMillis <= 0L) return 0.0;
        return wound.amount * Math.max(0.0, 1.0 - (now - wound.since) / (double) fadeMillis);
    }

    /** What is left of the wound of {@code player} at {@code now}. */
    static double left(UUID player, long fadeMillis, long now) {
        return left(WOUNDS.get(player), fadeMillis, now);
    }

    /**
     * {@code player} took {@code damage}: {@code share} of it is added to what is left of the wound (never more than {@code cap} in all), and its
     * fading starts again (after the hold, while one lasts). The wound now.
     */
    static double wound(UUID player, double damage, double share, double cap, long fadeMillis, long now) {
        Wound wound = WOUNDS.computeIfAbsent(player, ignored -> new Wound());
        double left = left(wound, fadeMillis, now);
        wound.amount = Math.max(0.0, Math.min(Math.max(0.0, cap), left + Math.max(0.0, damage) * Math.max(0.0, share)));
        wound.since = Math.max(now, wound.heldUntil);
        return wound.amount;
    }

    /** Drinks back up to {@code wanted} of the wound of {@code player}: what was drunk (taken off the wound). */
    static double drink(UUID player, double wanted, long fadeMillis, long now) {
        Wound wound = WOUNDS.get(player);
        if (wound == null || wanted <= 0.0) return 0.0;
        double left = left(wound, fadeMillis, now);
        double drunk = Math.min(left, wanted);
        // (what is left keeps fading as it did: it is rescaled to the same point of its fading)
        double rest = left - drunk;
        if (rest <= 1.0E-9) {
            WOUNDS.remove(player, wound);
            return drunk;
        }
        double fraction = left <= 0.0 ? 0.0 : rest / left;
        wound.amount *= fraction;
        return drunk;
    }

    /** The wound of {@code player} does not fade until {@code until} (the oath of 大); after that it fades as usual. */
    static void hold(UUID player, long until, long fadeMillis, long now) {
        Wound wound = WOUNDS.computeIfAbsent(player, ignored -> new Wound());
        wound.amount = left(wound, fadeMillis, now);
        wound.heldUntil = Math.max(wound.heldUntil, until);
        wound.since = wound.heldUntil;
    }

    static void forget(UUID player) {
        WOUNDS.remove(player);
    }
}
