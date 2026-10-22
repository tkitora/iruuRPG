package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Floating swords, for the mage's melee combo: each is drawn as a blade of gradient dust (a long tapering line, a guard and a glint at the tip)
 * wherever it is, so there is no entity to keep or lose. They fly (a straight blade, a seeking one), turn round the player and lunge at enemies, and
 * fall from above; everything here ends within 3 seconds.
 */
final class Blades {

    private static final Vector Y = new Vector(0.0, 1.0, 0.0);

    private Blades() {
    }

    /** One blade: its base at {@code at}, the point toward {@code dir}, {@code length} blocks long. */
    static void draw(MoveKit kit, Location at, Vector dir, double length) {
        Fx fx = kit.fx(at);
        if (!fx.active()) return;
        Vector d = dir.clone();
        if (d.lengthSquared() < 1.0E-6) d = new Vector(0.0, 1.0, 0.0);
        d.normalize();
        Vector side = d.clone().crossProduct(Y);
        if (side.lengthSquared() < 1.0E-6) side = new Vector(1.0, 0.0, 0.0);
        side.normalize();
        Location tip = at.clone().add(d.clone().multiply(length));
        fx.line(at, tip, kit.palette(), 0.25, 1.0, 1.5, 1.0, 0.12, null, 1);
        Vector off = side.clone().multiply(0.06);
        fx.line(at.clone().add(off), tip, kit.palette(), 0.5, 1.0, 1.5, 0.8, 0.16, null, 1);
        Location guard = at.clone().add(d.clone().multiply(0.12));
        fx.line(guard.clone().subtract(side.clone().multiply(0.2)), guard.clone().add(side.clone().multiply(0.2)), kit.palette(), 0.6, 0.6, 0.0, 0.9, 0.1, null, 1);
        fx.dust(tip.getX(), tip.getY(), tip.getZ(), kit.palette(), 1.0, 1.4);
    }

    /** A blade that flies from {@code start} along {@code dir}, {@code speed} blocks a tick for up to {@code range} blocks, and ends at the first enemy it touches (one hit of {@code rate}) or block. */
    static void fly(MoveKit kit, Location start, Vector dir, double speed, double range, double rate, Consumer<Location> onEnd) {
        Vector heading = dir.clone().normalize();
        Location[] position = {start.clone()};
        int ticks = (int) Math.ceil(range / speed);
        kit.sound(start, Sfx.WIND, 0.4f, 1.6f);
        kit.every(ticks + 1, age -> {
            Location next = position[0].clone().add(heading.clone().multiply(speed));
            draw(kit, position[0], heading, 1.1);
            boolean blocked = !next.getBlock().isPassable();
            LivingEntity touched = null;
            for (LivingEntity victim : kit.around(next, 1.1)) {
                touched = victim;
                break;
            }
            if (touched != null || blocked || age >= ticks) {
                if (touched != null) kit.hit(touched, rate);
                kit.burst(next, 0.7);
                if (onEnd != null) onEnd.accept(next);
                return true;
            }
            position[0] = next;
            return false;
        });
    }

    /**
     * A shuriken (a spinning cross of four blades) that flies from {@code start} along {@code dir}, {@code speed} blocks a tick, and bounces up to {@code bounces} times: off a
     * block (it turns as light does) and off an enemy it hits (it turns to the nearest other enemy within 9 blocks, else it flies back, a little aside). It hits an enemy it touches for
     * {@code rate}; after each bounce the damage is {@code decay} (0.5) less than before. It ends at the contact after the last bounce, or after 55 ticks.
     */
    static void bounce(MoveKit kit, Location start, Vector dir, double speed, int bounces, double decay, double rate) {
        World world = start.getWorld();
        Location[] position = {start.clone()};
        Vector[] heading = {dir.clone().normalize()};
        int[] done = {0};
        double[] power = {rate};
        UUID[] last = {null};
        kit.sound(start, Sfx.WIND, 0.5f, 1.8f);
        kit.every(55, age -> {
            Vector flight = heading[0].clone().multiply(speed);
            RayTraceResult wall = world.rayTraceBlocks(position[0], heading[0], speed, FluidCollisionMode.NEVER, true);
            Location next = wall != null && wall.getHitPosition() != null ? wall.getHitPosition().toLocation(world) : position[0].clone().add(flight);
            star(kit, position[0], heading[0], age);
            LivingEntity touched = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity victim : kit.around(next, 1.1)) {
                if (victim.getUniqueId().equals(last[0])) continue;
                double distance = victim.getLocation().distanceSquared(next);
                if (distance < best) {
                    best = distance;
                    touched = victim;
                }
            }
            if (touched != null) {
                kit.hit(touched, power[0]);
                kit.burst(next, 0.7);
                kit.sound(next, Sfx.CRACK, 0.4f, 1.7f);
                if (done[0] >= bounces) return true;
                done[0]++;
                power[0] *= 1.0 - decay;
                last[0] = touched.getUniqueId();
                LivingEntity other = null;
                double nearest = Double.MAX_VALUE;
                for (LivingEntity candidate : kit.around(next, 9.0)) {
                    if (candidate.equals(touched)) continue;
                    double distance = candidate.getLocation().distanceSquared(next);
                    if (distance < nearest) {
                        nearest = distance;
                        other = candidate;
                    }
                }
                Vector toward;
                if (other != null) {
                    toward = other.getLocation().add(0.0, other.getHeight() * 0.5, 0.0).toVector().subtract(next.toVector());
                } else {
                    double turn = Math.toRadians(age % 2 == 0 ? 35.0 : -35.0);
                    Vector back = heading[0].clone().multiply(-1.0);
                    toward = new Vector(back.getX() * Math.cos(turn) - back.getZ() * Math.sin(turn), back.getY(), back.getX() * Math.sin(turn) + back.getZ() * Math.cos(turn));
                }
                heading[0] = toward.lengthSquared() < 1.0E-6 ? heading[0].clone().multiply(-1.0) : toward.normalize();
                position[0] = next.clone();
                return false;
            }
            if (wall != null && wall.getHitBlockFace() != null) {
                kit.burst(next, 0.5);
                kit.sound(next, Sfx.CRACK, 0.3f, 1.9f);
                if (done[0] >= bounces) return true;
                done[0]++;
                power[0] *= 1.0 - decay;
                last[0] = null;
                Vector normal = wall.getHitBlockFace().getDirection();
                heading[0] = heading[0].clone().subtract(normal.clone().multiply(2.0 * heading[0].dot(normal))).normalize();
                position[0] = next.clone().add(normal.multiply(0.15));
                return false;
            }
            position[0] = next;
            return false;
        });
    }

    /** One frame of a shuriken at {@code at}: two crossed blades, turning with {@code age}, facing the way it flies. */
    private static void star(MoveKit kit, Location at, Vector heading, int age) {
        Fx fx = kit.fx(at);
        if (!fx.active()) return;
        Vector u = heading.clone().crossProduct(Y);
        if (u.lengthSquared() < 1.0E-6) u = new Vector(1.0, 0.0, 0.0);
        u.normalize();
        Vector v = heading.clone().crossProduct(u).normalize();
        for (int arm = 0; arm < 2; arm++) {
            double angle = age * 0.9 + arm * Math.PI / 2.0;
            Vector out = u.clone().multiply(Math.cos(angle)).add(v.clone().multiply(Math.sin(angle))).multiply(0.45);
            fx.line(at.clone().subtract(out), at.clone().add(out), kit.palette(), 0.4, 1.0, 0.8, 1.1, 0.1, null, 1);
        }
        fx.dust(at.getX(), at.getY(), at.getZ(), kit.palette(), 1.0, 1.3);
    }

    /** A blade that bends toward the nearest enemy within {@code seek} blocks after 3 ticks: one hit of {@code rate} on the first it touches. */
    static void seek(MoveKit kit, Location start, Vector heading, double speed, int life, double seek, double rate, Consumer<Location> onEnd) {
        Location[] position = {start.clone()};
        Vector velocity = heading.clone().normalize().multiply(speed * 0.7);
        kit.sound(start, Sfx.MAGIC, 0.4f, 1.8f);
        kit.every(life, age -> {
            if (age >= 3) {
                LivingEntity target = null;
                double best = seek * seek;
                for (LivingEntity candidate : kit.around(position[0], seek)) {
                    double distance = candidate.getLocation().add(0.0, candidate.getHeight() * 0.5, 0.0).distanceSquared(position[0]);
                    if (distance < best) {
                        best = distance;
                        target = candidate;
                    }
                }
                if (target != null) {
                    Vector toward = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0).toVector().subtract(position[0].toVector());
                    if (toward.lengthSquared() > 1.0E-6) velocity.add(toward.normalize().multiply(speed).subtract(velocity).multiply(0.45));
                }
            }
            double length = velocity.length();
            if (length > speed) velocity.multiply(speed / length);
            if (length < 1.0E-6) return false;
            Location next = position[0].clone().add(velocity);
            draw(kit, position[0], velocity, 1.1);
            LivingEntity touched = null;
            for (LivingEntity victim : kit.around(next, 1.0)) {
                touched = victim;
                break;
            }
            if (touched != null || !next.getBlock().isPassable()) {
                if (touched != null) kit.hit(touched, rate);
                kit.burst(next, 0.7);
                if (onEnd != null) onEnd.accept(next);
                return true;
            }
            position[0] = next;
            return false;
        });
    }

    /**
     * {@code count} blades turning round the player for {@code life} ticks (at most 60) at {@code radius}; every {@code every} ticks (a multiple of 2) each
     * lunges at an enemy within {@code reach} of the player (a different one each when there are enough) for one hit of {@code rate}. A new one with the same
     * {@code key} replaces the old.
     */
    static void orbit(MoveKit kit, String key, int count, int life, double radius, double reach, int every, double rate) {
        kit.sound(kit.player.getLocation(), Sfx.MAGIC, 0.7f, 1.3f);
        kit.zone(key, kit.player.getLocation(), life, 2, zone -> {
            Location center = kit.player.getLocation().add(0.0, 1.1, 0.0);
            List<LivingEntity> enemies = zone.age() % every == 0 ? kit.around(kit.player.getLocation(), reach) : new ArrayList<>();
            for (int index = 0; index < count; index++) {
                double angle = zone.age() * 0.3 + Math.PI * 2.0 * index / count;
                double c = Trig.cos(angle);
                double s = Trig.sin(angle);
                Location at = center.clone().add(c * radius, index % 2 == 0 ? 0.25 : -0.2, s * radius);
                draw(kit, at, new Vector(-s, 0.0, c), 0.9);
                if (enemies.isEmpty()) continue;
                LivingEntity victim = enemies.get(index % enemies.size());
                Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                kit.line(at, chest, 0.2, 1.0, 1.1);
                draw(kit, chest.clone().subtract(chest.toVector().subtract(at.toVector()).normalize().multiply(0.9)), chest.toVector().subtract(at.toVector()), 0.9);
                kit.hit(victim, rate);
                kit.sound(chest, Sfx.CRACK, 0.3f, 1.8f);
            }
        }, null);
    }
}
