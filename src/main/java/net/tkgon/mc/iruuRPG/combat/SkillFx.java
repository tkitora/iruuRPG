package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Curves;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import net.tkgon.mc.iruuRPG.vfx.Shapes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The look of the class skills, drawn with gradients (see the {@code vfx} package): each skill has its gradient, from a deep shade
 * through its color to a bright core, and the shapes it is drawn with: a magic circle, a bolt of lightning, a dome, a column, a trail, a ring.
 */
final class SkillFx {

    static final Gradient METEOR = Gradient.of(Color.fromRGB(35, 70, 200), Color.fromRGB(110, 170, 255), Color.fromRGB(235, 245, 255));
    static final Gradient LIGHTNING = Gradient.of(Color.fromRGB(190, 120, 15), Color.fromRGB(255, 225, 70), Color.fromRGB(255, 255, 235));
    static final Gradient ICE = Gradient.of(Color.fromRGB(35, 100, 225), Color.fromRGB(130, 200, 255), Color.fromRGB(240, 252, 255));
    static final Gradient HOLY = Gradient.of(Color.fromRGB(35, 160, 85), Color.fromRGB(120, 240, 150), Color.fromRGB(255, 246, 205));
    static final Gradient SHADOW = Gradient.of(Color.fromRGB(70, 28, 18), Color.fromRGB(255, 130, 30), Color.fromRGB(255, 225, 160));
    static final Gradient INSTINCT = Gradient.of(Color.fromRGB(255, 185, 55), Color.fromRGB(255, 250, 232), Color.fromRGB(185, 232, 255));

    private SkillFx() {
    }

    /** A magic circle in the plane of {@code right} and {@code up}: a ring, a five-pointed star turning the other way, and ticks round the edge. */
    static void circle(Player owner, Location center, Vector right, Vector up, double radius, int age, Gradient gradient) {
        Fx fx = Fx.of(owner, center.getWorld(), center);
        fx.ring(center, right, up, radius, 30, gradient, 0.45, 0.5, 1.0);
        // ticks at the edge: short spokes, turning slowly
        for (int tick = 0; tick < 8; tick++) {
            double angle = Math.PI * 2.0 * tick / 8.0 + age * 0.06;
            double c = Trig.cos(angle);
            double s = Trig.sin(angle);
            for (double r = radius * 0.86; r <= radius * 1.1; r += radius * 0.08) {
                fx.dust(center.getX() + (right.getX() * c + up.getX() * s) * r, center.getY() + (right.getY() * c + up.getY() * s) * r,
                        center.getZ() + (right.getZ() * c + up.getZ() * s) * r, gradient, 0.9, 0.9);
            }
        }
        for (int ray = 0; ray < 5; ray++) {
            double a0 = Math.PI * 2.0 * ray / 5.0 - age * 0.09;
            double a1 = Math.PI * 2.0 * (ray + 2) / 5.0 - age * 0.09;
            Location from = center.clone().add(right.clone().multiply(Trig.cos(a0) * radius * 0.78)).add(up.clone().multiply(Trig.sin(a0) * radius * 0.78));
            Location to = center.clone().add(right.clone().multiply(Trig.cos(a1) * radius * 0.78)).add(up.clone().multiply(Trig.sin(a1) * radius * 0.78));
            fx.line(from, to, gradient, 0.3, 0.9, 0.0, 0.8, 0.22, null, 1);
        }
    }

    /** A streak with a deep tail and a bright head (one tick of a flying thing). */
    static void trail(Player owner, Location from, Location to, Gradient gradient, double size) {
        Fx.of(owner, from.getWorld(), from).line(from, to, gradient, 0.15, 1.0, 2.5, size, 0.25, null, 1);
    }

    /** A bolt from {@code top} down to {@code head}: a zigzag whose jitter shrinks toward the head, with a hot core and a wider glow. */
    static void bolt(Player owner, Location top, Location head, int segments, double spread, Gradient gradient, boolean big) {
        World world = head.getWorld();
        Fx fx = Fx.of(owner, world, head);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location previous = top;
        for (int index = 1; index <= segments; index++) {
            double progress = index / (double) segments;
            double jitter = (1.0 - progress) * spread;
            Location point = index == segments ? head.clone() : new Location(world,
                    Curves.lerp(top.getX(), head.getX(), progress) + (jitter < 1.0E-9 ? 0.0 : random.nextDouble(-jitter, jitter)),
                    Curves.lerp(top.getY(), head.getY(), progress),
                    Curves.lerp(top.getZ(), head.getZ(), progress) + (jitter < 1.0E-9 ? 0.0 : random.nextDouble(-jitter, jitter)));
            // the glow is a second, dimmer line beside the core
            fx.line(previous, point, gradient, 0.35, 1.0, 1.0, big ? 1.5 : 1.1, 0.2, null, 1);
            fx.line(previous.clone().add(0.12, 0.0, 0.0), point.clone().add(-0.12, 0.0, 0.1), gradient, 0.1, 0.45, 0.0, 0.8, 0.3, null, 1);
            previous = point;
        }
    }

    /** A ring on the ground growing quickly and slowing (an exponential curve) over {@code ticks} ticks, drawn by the caller's scheduler: one frame. */
    static void groundRing(Player owner, Location center, double radius, Gradient gradient, double t, double size) {
        Fx.of(owner, center.getWorld(), center).flatRing(center.clone().add(0.0, 0.12, 0.0), radius, (int) Math.max(24, radius * 9), gradient, t, 0.4, size);
    }

    /** The upper half of a sphere of {@code radius} round {@code center}, turning, with a ring at the foot and motes rising inside. */
    static void dome(Player owner, Location center, double radius, int age, Gradient gradient) {
        Fx fx = Fx.of(owner, center.getWorld(), center);
        if (!fx.active()) return;
        float[] sphere = Shapes.sphere(120);
        double turn = age * 0.03;
        double c = Trig.cos(turn);
        double s = Trig.sin(turn);
        for (int index = 0; index < 120; index += fx.step()) {
            double y = sphere[index * 3 + 1];
            if (y < 0.0) continue;
            double x = sphere[index * 3];
            double z = sphere[index * 3 + 2];
            fx.dust(center.getX() + (x * c - z * s) * radius, center.getY() + y * radius, center.getZ() + (x * s + z * c) * radius, gradient, 0.25 + 0.6 * y, 0.85);
        }
        fx.flatRing(center.clone().add(0.0, 0.12, 0.0), radius, (int) Math.max(40, radius * 8), gradient, 0.5, 0.5, 1.0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int mote = 0; mote < 6; mote++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = Math.sqrt(random.nextDouble()) * radius * 0.9;
            double rise = ((age + mote * 7) % 40) / 40.0 * radius * 0.7;
            fx.dust(center.getX() + Math.cos(angle) * distance, center.getY() + 0.2 + rise, center.getZ() + Math.sin(angle) * distance, gradient, 0.6 + 0.4 * (rise / (radius * 0.7)), 0.8);
        }
    }

    /** A column of light: rings along it, wide at the foot, the color bright toward the top. */
    static void column(Player owner, Location base, double height, double radius, Gradient gradient) {
        Fx fx = Fx.of(owner, base.getWorld(), base);
        for (double y = 0.0; y <= height; y += 0.5) {
            fx.flatRing(base.clone().add(0.0, y, 0.0), radius * (1.0 - 0.55 * y / height), 14, gradient, 0.3 + 0.7 * y / height, 0.3, 1.0);
        }
    }

    /**
     * Two strands turning round the line from {@code start} along {@code direction}, between {@code from} and {@code to} blocks: the
     * radius is {@code radius}, the strands are a half turn apart, the color goes from the deep shade at the start to the bright core
     * at the end of the line ({@code length}); with a thin bright core on the axis.
     */
    static void helix(Player owner, Location start, Vector direction, double from, double to, double length, double radius, Gradient gradient) {
        helix(owner, start, direction, from, to, length, radius, gradient, 0.0);
    }

    /** The same turned by {@code phase} radians (to make it turn round over time). */
    static void helix(Player owner, Location start, Vector direction, double from, double to, double length, double radius, Gradient gradient, double phase) {
        Fx fx = Fx.of(owner, start.getWorld(), start.clone().add(direction.clone().multiply((from + to) / 2.0)));
        if (!fx.active()) return;
        Vector side = direction.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (side.lengthSquared() < 1.0E-6) side = new Vector(1.0, 0.0, 0.0);
        side.normalize();
        Vector up = side.clone().crossProduct(direction).normalize();
        for (double distance = from; distance < to; distance += 0.18) {
            double t = Curves.expOut(distance / length, 1.5);
            double angle = distance * 2.6 + phase;
            for (int strand = 0; strand < 2; strand++) {
                double a = angle + strand * Math.PI;
                double c = Trig.cos(a) * radius;
                double s = Trig.sin(a) * radius;
                fx.dust(start.getX() + direction.getX() * distance + side.getX() * c + up.getX() * s,
                        start.getY() + direction.getY() * distance + side.getY() * c + up.getY() * s,
                        start.getZ() + direction.getZ() * distance + side.getZ() * c + up.getZ() * s, gradient, 0.3 + 0.7 * t, 1.0);
            }
            fx.dust(start.getX() + direction.getX() * distance, start.getY() + direction.getY() * distance, start.getZ() + direction.getZ() * distance, gradient, 0.85 + 0.15 * t, 1.3);
        }
    }

    /** A burst of soft light where something struck: a small sphere shell and a ring (no end-rod sparks). */
    static void burst(Player owner, Location at, double radius, Gradient gradient) {
        Fx fx = Fx.of(owner, at.getWorld(), at);
        fx.sphereShell(at, radius * 0.6, 0.0, 22, gradient, 1.0);
        fx.flatRing(at.clone().add(0.0, 0.1, 0.0), radius, 28, gradient, 0.7, 0.3, 1.0);
    }
}
