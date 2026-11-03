package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The mage's combo with a melee weapon, a scythe: the <b>reaper</b> (t167, {@link MageReaper}; its skills are the reaper's own). Wide, low cuts
 * of the night and the blue-green of souls, slower than the other melee combos ({@link #normalGapTicks()}); every enemy cut gets a mark of death
 * (the reaper's skills reap them). Normal: 1 a wide sweep from the right with the ghost of a second blade behind it (62%), 2 the blade back from
 * the left, rising, hooking the enemies up (72%), 3 a step in and two short hooking reaps that draw them in (42% each), 4 two spins in a ring of
 * souls (48% each), 5 the verdict: the scythe comes down overhead (120%) and a crescent of souls flies on 9 blocks (half of it, marking too).
 * The combo has no heavy attacks (they went in t168; the reaper's skills are its own, {@link MageReaper}).
 */
public final class MageMeleeCombo extends BaseCombo {

    static final String PLAN = "mage_melee";

    public MageMeleeCombo() {
        super("mage", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.62, 0.72, 0.42, 0.48, 1.2}, new int[]{1, 1, 2, 2, 1},
                new double[]{0.1, 0.2, 0.36, 1.1, 1.25, 1.35}, new int[]{120, 0, 0, 0, 0, 0}, 40));
    }

    /** The scythe is slower than the other melee weapons: at least this many ticks between two normal attacks. */
    @Override
    public int normalGapTicks() {
        return 16;
    }

    /** The way a sweep that begins on the right ({@code roll} 0) or on the left (180) goes through what is in front: across, and a little forward. */
    private static Vector sweepWay(MoveKit kit, double roll) {
        Vector[] axes = kit.axes();
        return axes[1].clone().multiply(roll == 0.0 ? -0.9 : 0.9).add(axes[0].clone().multiply(0.4));
    }

    /**
     * A crescent of light that flies from {@code start} along {@code direction} ({@code speed} blocks a tick for up to {@code range} blocks), tilted {@code tilt} radians
     * from upright. It cuts everything it passes through once for {@code rate} and ends at a block.
     */
    static void crescent(MoveKit kit, Location start, Vector direction, double speed, double range, double tilt, double rate) {
        crescent(kit, start, direction, speed, range, tilt, rate, 1.5);
    }

    /** The same crescent with the radius of its arc {@code size}, with a trail of souls. */
    static void crescent(MoveKit kit, Location start, Vector direction, double speed, double range, double tilt, double rate, double size) {
        crescent(kit, start, direction, speed, range, tilt, rate, size, org.bukkit.Particle.SOUL_FIRE_FLAME, Sfx.REAP);
    }

    /** The same crescent with the {@code trail} particle and the {@code sound} it is sent with. */
    static void crescent(MoveKit kit, Location start, Vector direction, double speed, double range, double tilt, double rate, double size, org.bukkit.Particle trail, Sfx sound) {
        crescent(kit, start, direction, speed, range, tilt, rate, size, trail, sound, null);
    }

    /** The same crescent, {@code onHit} told of each enemy it cuts (null: nothing). */
    static void crescent(MoveKit kit, Location start, Vector direction, double speed, double range, double tilt, double rate, double size, org.bukkit.Particle trail, Sfx sound,
                         java.util.function.Consumer<LivingEntity> onHit) {
        Vector heading = direction.clone().normalize();
        Vector side = heading.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (side.lengthSquared() < 1.0E-6) side = new Vector(1.0, 0.0, 0.0);
        side.normalize();
        Vector up = side.clone().crossProduct(heading).normalize();
        // the arc is drawn in the plane of side and up, turned by the tilt
        Vector u = side.clone().multiply(Math.cos(tilt)).add(up.clone().multiply(Math.sin(tilt)));
        Vector v = up.clone().multiply(Math.cos(tilt)).subtract(side.clone().multiply(Math.sin(tilt)));
        Set<UUID> hit = new HashSet<>();
        Location[] position = {start.clone()};
        int ticks = (int) Math.ceil(range / speed);
        kit.sound(start, sound, 0.6f, 1.2f);
        kit.every(ticks + 1, age -> {
            Fx fx = kit.fx(position[0]);
            if (fx.active()) {
                int points = 16;
                for (int index = 0; index < points; index++) {
                    double t = index / (double) (points - 1);
                    double angle = Math.toRadians(-55.0 + 110.0 * t);
                    double radius = size;
                    // the middle of the arc is ahead and bright, the ends trail behind
                    double lag = (1.0 - Math.cos(angle)) * 0.9;
                    fx.dust(position[0].getX() + (u.getX() * Trig.sin(angle) + v.getX() * 0.0) * radius - heading.getX() * lag,
                            position[0].getY() + (u.getY() * Trig.sin(angle)) * radius - heading.getY() * lag,
                            position[0].getZ() + (u.getZ() * Trig.sin(angle)) * radius - heading.getZ() * lag,
                            kit.palette(), 1.0 - Math.abs(t - 0.5) * 1.2, 1.1);
                }
                fx.line(position[0].clone().subtract(heading.clone().multiply(1.6)), position[0], kit.palette(), 0.2, 0.9, 1.5, 1.0, 0.2, null, 1);
            }
            Location next = position[0].clone().add(heading.clone().multiply(speed));
            for (LivingEntity victim : kit.around(next, 1.7)) {
                if (hit.add(victim.getUniqueId()) && kit.hit(victim, rate) && onHit != null) onHit.accept(victim);
            }
            if (age % 2 == 0) if (trail != null) kit.particle(trail, position[0], 2, size * 0.4, 0.01);
            if (!next.getBlock().isPassable()) {
                kit.burst(next, 0.9);
                return true;
            }
            position[0] = next;
            return false;
        });
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                // a wide sweep from the right, low and flat, and the ghost of a second blade behind it
                kit.slash("arc", 3.6, 250.0, 80.0, 0.0, 3.0, 0.5, 0.0, 1.35, 0.14, 0.0);
                kit.slash("arc", 3.2, 230.0, 74.0, 0.0, 2.8, 0.4, 0.0, 1.15, 0.12, 0.05);
                kit.sound(feet, Sfx.REAPER_SWING, 0.8f, 1.0f);
                MageReaper.cut(kit, kit.cone(3.6, 115.0), rate, sweepWay(kit, 0.0));
            }
            case 2 -> {
                // the blade comes back from the left, rising: it hooks what it catches up a little
                kit.slash("arc", 3.7, 250.0, 35.0, 180.0, 3.0, 0.9, 0.0, 1.4, 0.15, 0.0);
                kit.sound(feet, Sfx.REAPER_SWING, 0.85f, 0.9f);
                Vector way = sweepWay(kit, 180.0).add(new Vector(0.0, 0.6, 0.0));
                for (LivingEntity victim : kit.cone(3.7, 110.0)) {
                    if (!kit.hit(victim, rate)) continue;
                    kit.lift(victim, 0.25);
                    MageReaper.marked(kit, victim, way);
                }
            }
            case 3 -> {
                // a step in, then short hooking reaps that draw the enemies to the reaper
                kit.dash(facing, 0.45, 3, end -> {
                    for (int index = 0; index < hits; index++) {
                        int which = index;
                        kit.later(which * 4L, () -> {
                            double roll = which % 2 == 0 ? 0.0 : 180.0;
                            kit.slash("arc", 3.3, 200.0, which % 2 == 0 ? 65.0 : 115.0, roll, 2.6, 0.5, 0.0, 1.25, 0.11, 0.0);
                            kit.sound(kit.player.getLocation(), Sfx.REAPER_SWING, 0.75f, 1.1f + 0.1f * which);
                            for (LivingEntity victim : kit.cone(3.3, 100.0)) {
                                if (!kit.hit(victim, rate)) continue;
                                kit.pull(victim, kit.player.getLocation(), 0.25);
                                MageReaper.marked(kit, victim, sweepWay(kit, roll));
                            }
                        });
                    }
                });
            }
            case 4 -> {
                // spins round the reaper in a ring of souls
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(which * 5L, () -> {
                        Location at = kit.player.getLocation();
                        kit.slash("arc", 3.5, 340.0, 85.0, which % 2 == 0 ? 0.0 : 180.0, 3.2, 0.6, 0.0, 1.3, 0.12, 0.0);
                        kit.fx(at).flatRing(at.clone().add(0.0, 0.3 + 0.4 * which, 0.0), 3.3, 36, kit.palette(), 0.5, 0.25, 1.0);
                        kit.particle(Particle.SOUL, at.clone().add(0.0, 1.0, 0.0), 4, 1.6, 0.01);
                        kit.sound(at, Sfx.REAPER_SWING, 0.8f, 0.8f + 0.12f * which);
                        for (LivingEntity victim : kit.around(at, 3.5)) {
                            if (!kit.hit(victim, rate)) continue;
                            // (the souls go round with the blade)
                            Vector off = victim.getLocation().toVector().subtract(at.toVector()).setY(0.0);
                            MageReaper.marked(kit, victim, new Vector(-off.getZ(), 0.0, off.getX()));
                        }
                    });
                }
            }
            default -> {
                // the verdict: the scythe comes down overhead, and a crescent of souls flies on from where it struck
                kit.slash("arc", 3.9, 250.0, 5.0, 0.0, 3.4, 0.1, 0.0, 1.7, 0.2, 0.0);
                kit.sound(feet, Sfx.REAPER_HEAVY, 1.0f, 0.8f);
                kit.shockwave(feet.clone().add(facing.clone().multiply(2.2)), 0.6, 2.8, 4, 1.1);
                Vector down = facing.clone().multiply(0.4).add(new Vector(0.0, -0.6, 0.0));
                for (LivingEntity victim : kit.cone(3.9, 100.0)) {
                    if (kit.hit(victim, rate)) MageReaper.marked(kit, victim, down);
                }
                double wave = rate * kit.number(PLAN, "wave-share", 0.5);
                double range = kit.number(PLAN, "wave-range", 9.0);
                kit.later(4L, () -> {
                    Vector ahead = kit.facing();
                    Location start = kit.player.getLocation().add(0.0, 1.0, 0.0).add(ahead.clone().multiply(1.2));
                    crescent(kit, start, ahead, 1.1, range, 0.0, wave, 1.6, Particle.SOUL, Sfx.REAPER_THROW, victim -> MageReaper.mark(kit, victim, 1));
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: the reaper's skills are its own (MageReaper).
    }

}
