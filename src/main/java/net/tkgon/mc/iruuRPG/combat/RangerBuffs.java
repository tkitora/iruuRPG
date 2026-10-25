package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the ranger's heavy attack 0 (the backstep) gives to the arrows after it: one of four effects, drawn from a shuffle bag (all four come up, in a random order, before
 * the bag is filled again). Ricochet and poison are for the next 5 arrows of any attack; the small explosion and the orange damage are for the next 3 normal attacks (every
 * arrow of them). The arrow shows it with the colour of its trail. Also the ring of heavy 2 that makes the arrows that pass through it faster and stronger.
 */
final class RangerBuffs {

    enum Type {
        /** The arrow bounces off a block, or goes on to another enemy after one. */
        RICOCHET("跳弾", 5, false, Gradient.of(Color.fromRGB(20, 120, 200), Color.fromRGB(90, 210, 255), Color.fromRGB(235, 252, 255))),
        /** The enemy it hits is poisoned: it takes slip damage for 4 s. */
        POISON("毒", 5, false, Gradient.of(Color.fromRGB(30, 110, 30), Color.fromRGB(110, 225, 70), Color.fromRGB(225, 255, 190))),
        /** It bursts where it hits (a block or an enemy): a small explosion, 10% more damage round it and a stack of explosion on each. */
        BLAST("爆破", 3, true, Gradient.of(Color.fromRGB(150, 40, 10), Color.fromRGB(255, 120, 35), Color.fromRGB(255, 232, 160))),
        /** It deals 10% of its damage (before the target's defence) again as orange damage. */
        ORANGE("橙", 3, true, Gradient.of(Color.fromRGB(170, 90, 0), Color.fromRGB(255, 170, 30), Color.fromRGB(255, 245, 200)));

        final String label;
        final int count;
        /** True: counted in normal attacks (every arrow of them); false: counted in arrows. */
        final boolean perAttack;
        final Gradient trail;

        Type(String label, int count, boolean perAttack, Gradient trail) {
            this.label = label;
            this.count = count;
            this.perAttack = perAttack;
            this.trail = trail;
        }
    }

    private static final class State {
        private final Type type;
        private int left;

        private State(Type type) {
            this.type = type;
            this.left = type.count;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();
    private static final Map<UUID, List<Type>> BAG = new HashMap<>();

    private RangerBuffs() {
    }

    /** Draws the next effect from the player's bag (filling it again, shuffled, when it is empty), makes it the one that is on and says so. */
    static Type roll(Player player) {
        List<Type> bag = BAG.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        if (bag.isEmpty()) {
            bag.addAll(List.of(Type.values()));
            Collections.shuffle(bag);
        }
        Type type = bag.remove(bag.size() - 1);
        ACTIVE.put(player.getUniqueId(), new State(type));
        String unit = type.perAttack ? "回の通常攻撃" : "発";
        player.sendTitle("", "§e" + type.label + " §7次の" + type.count + unit, 0, 25, 10);
        return type;
    }

    /** The effect for one arrow of any attack (ricochet, poison), or null; it counts the arrow. */
    static Type takeArrow(UUID id) {
        State state = ACTIVE.get(id);
        if (state == null || state.type.perAttack) return null;
        if (--state.left <= 0) ACTIVE.remove(id);
        return state.type;
    }

    /** The effect for the arrows of a normal attack (the small explosion, orange damage), or null; it counts the attack. */
    static Type takeAttack(UUID id) {
        State state = ACTIVE.get(id);
        if (state == null || !state.type.perAttack) return null;
        if (--state.left <= 0) ACTIVE.remove(id);
        return state.type;
    }

    static void forget(UUID id) {
        ACTIVE.remove(id);
        BAG.remove(id);
        CIRCLES.remove(id);
    }

    // ---- the ring of heavy 2 ----------------------------------------------------------------------------------------------------

    /** A ring standing in the air: an arrow that goes through it is faster and stronger. */
    record Circle(Location center, Vector normal, double radius, int untilTick) {
    }

    private static final Map<UUID, Circle> CIRCLES = new HashMap<>();

    static void setCircle(UUID id, Circle circle) {
        CIRCLES.put(id, circle);
    }

    static Circle circle(UUID id) {
        Circle circle = CIRCLES.get(id);
        if (circle == null) return null;
        if (org.bukkit.Bukkit.getCurrentTick() > circle.untilTick()) {
            CIRCLES.remove(id);
            return null;
        }
        return circle;
    }

    /** Whether the segment from {@code from} to {@code to} goes through the ring. */
    static boolean passes(Circle circle, Location from, Location to) {
        if (circle.center().getWorld() != from.getWorld()) return false;
        double d0 = from.toVector().subtract(circle.center().toVector()).dot(circle.normal());
        double d1 = to.toVector().subtract(circle.center().toVector()).dot(circle.normal());
        if (d0 * d1 > 0.0 || Math.abs(d0 - d1) < 1.0E-9) return false;
        double t = d0 / (d0 - d1);
        Vector at = from.toVector().add(to.toVector().subtract(from.toVector()).multiply(t));
        return at.distance(circle.center().toVector()) <= circle.radius() + 0.35;
    }
}
