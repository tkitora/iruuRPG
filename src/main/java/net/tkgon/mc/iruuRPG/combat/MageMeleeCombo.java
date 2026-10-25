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
 * The mage's combo with a melee weapon, a scythe, all of it in reach of the blade: wide cuts of about 250 degrees that reap, slow and heavy (the sounds are lower and longer
 * than a sword's: a sweep, a phantom's wing, a skeleton's rattle), and ash blown off an enemy it cuts, in the direction the blade came from. The reach is between the assassin's
 * and the warrior's (3.4 to 3.9 blocks); the pace is slower than the other melee combos' ({@link #normalGapTicks()}).
 * Normal: 1 two sweeps, out and back (29% each), 2 a reaping cut that lifts (79%), 3 a whirl, three spins round (32% each), 4 a step in and a cut (118%), 5 the harvest (a pull
 * in for 0.3 s, then a full circle of 3.8 blocks: 138%, slows). Heavy (each can be pressed again within a moment to go on with the next part of it): 0 a leap: the mage jumps,
 * the enemies near are thrown up (10%), and the second press is the plunge: the mage comes down on the spot with a cut downward (90%); its own cooldown of 6 s. 1 three sweeping
 * cuts forward (20% each; again: a crossing cut, 40%), 2 a long line slash straight ahead (a thin line shows for 0.3 s, then the blade goes down 6 blocks of it, 36%, slows; again: two more slanted 28 degrees each way, 36% each), 3 a short dash forward and
 * a sweep (110%; again: a dash and a sweep back, 66%), 4 three short dashes with a cut each and a last cut (125% in all; again: an overhead cut, 62%).
 */
public final class MageMeleeCombo extends BaseCombo {

    static final String PLAN = "mage_melee";

    public MageMeleeCombo() {
        super("mage", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.29, 0.79, 0.32, 1.18, 1.38}, new int[]{2, 1, 3, 1, 1},
                new double[]{0.1, 0.2, 0.36, 1.1, 1.25}, new int[]{120, 0, 0, 0, 0}, 40));
    }

    /** The scythe is slower than the other melee weapons: at least this many ticks between two normal attacks. */
    @Override
    public int normalGapTicks() {
        return 18;
    }

    // ---- what comes after a heavy attack (pressed again) ----------------------------------------------------------------------------

    /** A heavy attack that can be gone on with: which one, from when until when (game ticks) and the rate of the next part. */
    private record Follow(int index, int readyTick, int untilTick, double rate) {
    }

    private static final Map<UUID, Follow> FOLLOW = new HashMap<>();

    private static void arm(MoveKit kit, int index, int delayTicks, int windowTicks, double rate) {
        int now = Bukkit.getCurrentTick();
        FOLLOW.put(kit.player.getUniqueId(), new Follow(index, now + delayTicks, now + windowTicks, rate));
    }

    /** The mage left, or the class changed. */
    public static void forget(UUID id) {
        FOLLOW.remove(id);
    }

    @Override
    public boolean followUp(MoveKit kit) {
        Follow follow = FOLLOW.get(kit.player.getUniqueId());
        if (follow == null) return false;
        int now = Bukkit.getCurrentTick();
        if (now > follow.untilTick()) {
            FOLLOW.remove(kit.player.getUniqueId());
            return false;
        }
        // (a click that comes too early is used up, so that a held button does not throw another heavy attack in the middle of this one)
        if (now < follow.readyTick()) return true;
        FOLLOW.remove(kit.player.getUniqueId());
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.85));
        follow(kit, follow);
        return true;
    }

    // ---- ash and cuts --------------------------------------------------------------------------------------------------------------

    /** Ash blown off {@code victim} along {@code direction} (the way the blade went through it), in two puffs. */
    private static void ash(MoveKit kit, LivingEntity victim, Vector direction) {
        Location from = victim.getLocation().add(0.0, victim.getHeight() * 0.6, 0.0);
        Vector way = direction.lengthSquared() < 1.0E-6 ? new Vector(0.0, 0.0, 1.0) : direction.clone().normalize();
        for (int index = 0; index < 8; index++) {
            kit.particle(Particle.ASH, from.clone().add(way.clone().multiply(0.35 * index)), 3, 0.12 + 0.03 * index, 0.0);
        }
        kit.particle(Particle.SMOKE_NORMAL, from, 4, 0.2, 0.02);
        kit.later(3L, () -> {
            for (int index = 0; index < 6; index++) {
                kit.particle(Particle.ASH, from.clone().add(way.clone().multiply(2.8 + 0.4 * index)), 3, 0.2, 0.0);
            }
        });
    }

    /** {@code rate} on each of {@code targets}; the ones hit blow ash along {@code direction}. */
    private static void reap(MoveKit kit, java.util.List<LivingEntity> targets, double rate, Vector direction) {
        for (LivingEntity victim : targets) {
            if (kit.hit(victim, rate)) ash(kit, victim, direction);
        }
    }

    /** The way a sweep that begins on the right ({@code roll} 0) or on the left (180) goes through what is in front: across, and a little forward. */
    private static Vector sweepWay(MoveKit kit, double roll) {
        Vector[] axes = kit.axes();
        return axes[1].clone().multiply(roll == 0.0 ? -0.9 : 0.9).add(axes[0].clone().multiply(0.4));
    }

    /** A sweep of the scythe: an arc of 250 degrees ahead, and everything in the cone cut for {@code rate}. */
    private static void sweep(MoveKit kit, double radius, double tilt, double roll, double rate) {
        kit.slash("arc", radius, 250.0, tilt, roll, radius * 0.9, 0.5, 0.0, 1.3, 0.14, 0.0);
        kit.sound(kit.player.getLocation(), Sfx.REAP, 0.8f, 0.85f);
        reap(kit, kit.cone(radius, 115.0), rate, sweepWay(kit, roll));
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
                if (hit.add(victim.getUniqueId())) kit.hit(victim, rate);
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
        FOLLOW.remove(kit.player.getUniqueId());
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.8));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                // two sweeps, out and back
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 6L, () -> sweep(kit, 3.4, 78.0, which % 2 == 0 ? 0.0 : 180.0, rate));
                }
            }
            case 2 -> {
                // a reaping cut from high to low: the hook of the blade lifts what it catches
                kit.slash("arc", 3.6, 250.0, 25.0, 0.0, 3.2, 0.1, 0.0, 1.4, 0.16, 0.0);
                kit.sound(feet, Sfx.REAP_HEAVY, 0.8f, 0.9f);
                Vector way = facing.clone().multiply(0.5).add(new Vector(0.0, -0.5, 0.0));
                for (LivingEntity victim : kit.cone(3.6, 100.0)) {
                    if (kit.hit(victim, rate)) {
                        kit.lift(victim, 0.3);
                        ash(kit, victim, way);
                    }
                }
            }
            case 3 -> {
                // a whirl: three spins round the mage, a flat ring of cut each time
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 4L, () -> {
                        Location at = kit.player.getLocation();
                        kit.slash("arc", 3.4, 330.0, 85.0, which % 2 == 0 ? 0.0 : 180.0, 3.2, 0.6, 0.0, 1.2, 0.1, 0.0);
                        kit.fx(at).flatRing(at.clone().add(0.0, 0.3 + 0.35 * which, 0.0), 3.3, 36, kit.palette(), 0.5, 0.2, 1.0);
                        kit.sound(at, Sfx.REAP, 0.8f, 0.75f + 0.1f * which);
                        for (LivingEntity victim : kit.around(at, 3.4)) {
                            if (!kit.hit(victim, rate)) continue;
                            // the ash goes round with the blade
                            Vector off = victim.getLocation().toVector().subtract(at.toVector()).setY(0.0);
                            ash(kit, victim, new Vector(-off.getZ(), 0.0, off.getX()));
                        }
                    });
                }
            }
            case 4 -> {
                // a step in and a cut: the mage slides forward a little under the blade
                kit.sound(feet, Sfx.REAP_HEAVY, 0.9f, 0.8f);
                kit.dash(facing, 0.5, 3, end -> {
                    kit.slash("arc", 3.9, 250.0, 30.0, 0.0, 3.5, 0.4, 0.0, 1.5, 0.16, 0.0);
                    kit.sound(end, Sfx.REAP, 1.0f, 0.7f);
                    kit.shockwave(end, 0.8, 3.0, 3, 1.0);
                    reap(kit, kit.cone(3.9, 115.0), rate, sweepWay(kit, 0.0));
                });
            }
            default -> {
                // the harvest: the enemies are drawn in for 0.3 s, then the whole circle is cut
                kit.sound(feet, Sfx.SOUL, 0.9f, 0.7f);
                for (int tick = 0; tick < 6; tick++) {
                    double r = 4.8 - 2.6 * tick / 5.0;
                    kit.later(tick, () -> {
                        Location at = kit.player.getLocation();
                        kit.fx(at).flatRing(at.clone().add(0.0, 0.2, 0.0), r, 40, kit.palette(), 0.4, 0.2, 1.0);
                        for (LivingEntity victim : kit.around(at, 4.8)) kit.pull(victim, at, 0.08);
                    });
                }
                kit.later(7L, () -> {
                    Location at = kit.player.getLocation();
                    kit.slash("arc", 3.8, 340.0, 85.0, 0.0, 3.6, 0.5, 0.0, 1.6, 0.2, 0.0);
                    kit.shockwave(at, 1.0, 4.2, 5, 1.2);
                    kit.fx(at).sphereShell(at.clone().add(0.0, 1.0, 0.0), 1.8, 0.0, 48, kit.palette(), 1.0);
                    kit.particle(Particle.SOUL_FIRE_FLAME, at.clone().add(0.0, 1.0, 0.0), 30, 1.8, 0.08);
                    kit.sound(at, Sfx.REAP_HEAVY, 1.0f, 0.65f);
                    for (LivingEntity victim : kit.around(at, 3.8)) {
                        if (!kit.hit(victim, rate)) continue;
                        kit.slow(victim, 40.0, 1.5);
                        ash(kit, victim, victim.getLocation().toVector().subtract(at.toVector()).setY(0.2));
                    }
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        FOLLOW.remove(kit.player.getUniqueId());
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.85));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a leap: the mage jumps with a cut upward, and what is near is thrown up; the second press is the plunge
                kit.guard(0.5, 30);
                kit.sound(feet, Sfx.REAP_HEAVY, 1.0f, 0.9f);
                kit.slash("arc", 3.6, 250.0, 20.0, 180.0, 3.2, 0.9, 0.0, 1.4, 0.16, 0.0);
                for (LivingEntity victim : kit.around(feet, 3.6)) {
                    if (kit.hit(victim, rate)) {
                        kit.lift(victim, 0.95);
                        ash(kit, victim, new Vector(0.0, 1.0, 0.0));
                    }
                }
                kit.player.setFallDistance(0.0f);
                kit.player.setVelocity(new Vector(0.0, 0.95, 0.0));
                kit.every(12, age -> {
                    Location at = kit.player.getLocation();
                    kit.particle(Particle.SOUL_FIRE_FLAME, at.clone().add(0.0, 0.3, 0.0), 5, 0.4, 0.02);
                    kit.fx(at).flatRing(at.clone().add(0.0, 0.2, 0.0), 1.2 + 0.1 * age, 24, kit.palette(), 0.5, 0.2, 1.0);
                    return false;
                });
                arm(kit, 0, 8, 40, kit.number(PLAN, "plunge-rate", 0.9));
            }
            case 1 -> {
                // three sweeping cuts forward
                for (int index = 0; index < 3; index++) {
                    int which = index;
                    kit.later(index * 5L, () -> sweep(kit, 3.5, 75.0 - 15.0 * which, which % 2 == 0 ? 0.0 : 180.0, rate));
                }
                arm(kit, 1, 10, 36, rate * 2.0);
            }
            case 2 -> {
                // a long line slash straight ahead: a thin line shows along the way for 0.3 s, then the blade goes down it (6 blocks, slows)
                lineSlash(kit, facing, rate, true);
                arm(kit, 2, 12, 36, rate);
            }
            case 3 -> {
                // a short dash forward and a sweep
                kit.sound(feet, Sfx.REAP_HEAVY, 1.0f, 0.8f);
                kit.dash(facing, 1.0, 4, end -> {
                    kit.slash("arc", 3.9, 250.0, 75.0, 0.0, 3.5, 0.4, 0.0, 1.5, 0.16, 0.0);
                    kit.sound(end, Sfx.REAP, 1.0f, 0.7f);
                    kit.shockwave(end, 0.8, 3.2, 3, 1.0);
                    reap(kit, kit.cone(3.9, 120.0), rate, sweepWay(kit, 0.0));
                });
                arm(kit, 3, 10, 36, rate * 0.6);
            }
            default -> {
                // three short dashes, a cut at the end of each, and a last cut
                for (int index = 0; index < 3; index++) {
                    int which = index;
                    kit.later(index * 5L, () -> {
                        kit.sound(kit.player.getLocation(), Sfx.REAP_HEAVY, 0.8f, 0.8f + 0.1f * which);
                        kit.dash(kit.facing(), 0.9, 3, end -> {
                            kit.slash("arc", 3.5, 250.0, 75.0 - 15.0 * which, which % 2 == 0 ? 0.0 : 180.0, 3.1, 0.5, 0.0, 1.3, 0.12, 0.0);
                            reap(kit, kit.cone(3.5, 115.0), rate * 0.22, sweepWay(kit, which % 2 == 0 ? 0.0 : 180.0));
                        });
                    });
                }
                kit.later(16L, () -> {
                    Location at = kit.player.getLocation();
                    kit.slash("arc", 3.9, 250.0, 40.0, 0.0, 3.5, 0.3, 0.0, 1.6, 0.18, 0.0);
                    kit.shockwave(at, 1.0, 3.8, 4, 1.1);
                    kit.sound(at, Sfx.REAP_HEAVY, 1.0f, 0.65f);
                    reap(kit, kit.cone(3.9, 120.0), rate * 0.44, sweepWay(kit, 0.0));
                });
                arm(kit, 4, 22, 50, rate * 0.5);
            }
        }
    }

    /** A line slash along {@code way} (6 blocks): with {@code telegraph}, a thin line shows for 0.3 s first; then the blade goes down it and cuts all within 0.9 blocks of it. */
    private static void lineSlash(MoveKit kit, Vector way, double rate, boolean telegraph) {
        Location from = kit.player.getLocation().add(0.0, 1.0, 0.0);
        Location to = from.clone().add(way.clone().multiply(6.2));
        Vector side = new Vector(-way.getZ(), 0.0, way.getX());
        int wait = telegraph ? 6 : 1;
        if (telegraph) {
            kit.sound(from, Sfx.REAP, 0.7f, 0.7f);
            for (int tick = 0; tick < wait; tick++) kit.later(tick, () -> kit.line(from, to, 0.1, 0.6, 0.8));
        }
        kit.later(wait, () -> {
            kit.slash("arc", 3.8, 160.0, 85.0, 0.0, 3.4, 0.5, 0.0, 1.5, 0.12, 0.0);
            kit.sound(from, Sfx.REAP_HEAVY, 1.0f, 0.7f);
            for (int offset = -1; offset <= 1; offset++) {
                Vector shift = side.clone().multiply(0.7 * offset);
                kit.line(from.clone().add(shift), to.clone().add(shift), 0.1, 1.0, 1.5);
            }
            kit.burst(to, 1.6);
            for (LivingEntity victim : kit.corridor(from, way, 6.2, 1.8)) {
                if (!kit.hit(victim, rate)) continue;
                kit.slow(victim, 40.0, 1.2);
                ash(kit, victim, way);
            }
        });
    }

    /** The next part of the heavy attack {@code follow.index()}. */
    private void follow(MoveKit kit, Follow follow) {
        double rate = follow.rate();
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (follow.index()) {
            case 0 -> {
                // the plunge: down on the spot, and the cut comes down with the mage
                kit.guard(0.5, 20);
                kit.sound(feet, Sfx.REAP_HEAVY, 1.0f, 0.7f);
                Vector drift = kit.player.getVelocity().setY(0.0).multiply(0.3);
                kit.every(16, age -> {
                    kit.player.setFallDistance(0.0f);
                    if (age < 5) kit.player.setVelocity(drift.clone().setY(-1.3));
                    Location at = kit.player.getLocation();
                    kit.particle(Particle.SOUL_FIRE_FLAME, at.clone().add(0.0, 1.0, 0.0), 4, 0.3, 0.02);
                    if ((age >= 2 && kit.player.isOnGround()) || age == 15) {
                        kit.slash("arc", 3.9, 250.0, 5.0, 0.0, 3.2, 0.2, 0.0, 1.7, 0.2, 0.0);
                        kit.shockwave(at, 1.0, 4.4, 5, 1.2);
                        kit.burst(at.clone().add(0.0, 0.4, 0.0), 3.0);
                        kit.sound(at, Sfx.REAP_HEAVY, 1.0f, 0.6f);
                        for (LivingEntity victim : kit.around(at, 4.0)) {
                            if (!kit.hit(victim, rate)) continue;
                            kit.push(victim, at.toVector(), 0.4, 0.1);
                            ash(kit, victim, victim.getLocation().toVector().subtract(at.toVector()).setY(-0.2));
                        }
                        return true;
                    }
                    return false;
                });
            }
            case 1 -> {
                // a crossing cut: two arcs, slanted each way, at once
                kit.slash("arc", 3.8, 250.0, 50.0, 0.0, 3.4, 0.5, 0.0, 1.4, 0.14, 0.0);
                kit.slash("arc", 3.8, 250.0, 130.0, 180.0, 3.4, 0.5, 0.0, 1.4, 0.14, 0.04);
                kit.sound(feet, Sfx.REAP_HEAVY, 1.0f, 0.75f);
                reap(kit, kit.cone(3.8, 125.0), rate, facing);
            }
            case 2 -> {
                // two more line slashes, slanted 28 degrees each way
                for (int side = -1; side <= 1; side += 2) {
                    double turn = Math.toRadians(28.0 * side);
                    Vector way = new Vector(facing.getX() * Math.cos(turn) - facing.getZ() * Math.sin(turn), 0.0, facing.getX() * Math.sin(turn) + facing.getZ() * Math.cos(turn));
                    lineSlash(kit, way, rate, false);
                }
            }
            case 3 -> {
                // a dash on and a sweep back the other way
                kit.sound(feet, Sfx.REAP_HEAVY, 1.0f, 0.8f);
                kit.dash(facing, 0.8, 3, end -> {
                    kit.slash("arc", 3.9, 250.0, 75.0, 180.0, 3.5, 0.4, 0.0, 1.5, 0.16, 0.0);
                    kit.sound(end, Sfx.REAP, 1.0f, 0.7f);
                    reap(kit, kit.cone(3.9, 120.0), rate, sweepWay(kit, 180.0));
                });
            }
            default -> {
                // an overhead cut
                kit.slash("arc", 3.9, 250.0, 5.0, 0.0, 3.5, 0.1, 0.0, 1.7, 0.2, 0.0);
                kit.shockwave(feet, 0.8, 4.0, 4, 1.1);
                kit.sound(feet, Sfx.REAP_HEAVY, 1.0f, 0.6f);
                Vector way = facing.clone().multiply(0.4).add(new Vector(0.0, -0.6, 0.0));
                for (LivingEntity victim : kit.cone(3.9, 110.0)) {
                    if (kit.hit(victim, rate)) {
                        kit.lift(victim, 0.25);
                        ash(kit, victim, way);
                    }
                }
            }
        }
    }
}
