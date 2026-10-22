package net.tkgon.mc.iruuRPG.vfx.flow;

import org.bukkit.Location;

/** Where a flow's centre is right now: fixed, or moving (the test motion, later an entity). */
public interface FlowOrigin {

    Location current(int tick);

    static FlowOrigin fixed(Location at) {
        return tick -> at.clone();
    }

    /** Goes round a circle of {@code radius} blocks in the horizontal plane at {@code speed} blocks a second, the circle touching {@code start}. */
    static FlowOrigin circling(Location start, double radius, double speed) {
        return tick -> {
            double angle = speed / 20.0 / radius * tick;
            return start.clone().add(Math.sin(angle) * radius, 0.0, (1.0 - Math.cos(angle)) * radius);
        };
    }
}
