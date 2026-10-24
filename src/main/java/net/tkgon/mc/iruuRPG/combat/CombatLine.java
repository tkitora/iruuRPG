package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/** A few shapes the generic combos share. */
final class CombatLine {

    private CombatLine() {
    }

    /** A wave that runs out from {@code from} along {@code direction} for {@code length} blocks and hurts everything within {@code width} of its way once, for {@code rate}. */
    static void wave(MoveKit kit, Location from, Vector direction, double length, double width, double rate) {
        Location end = from.clone().add(direction.clone().multiply(length));
        kit.line(from, end, 0.2, 1.0, 1.5);
        kit.line(from.clone().add(0.0, 0.3, 0.0), end.clone().add(0.0, 0.3, 0.0), 0.5, 1.0, 1.1);
        kit.burst(end, 1.0);
        kit.hitAll(kit.corridor(from, direction, length, width), rate);
    }
}
