package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Moves that several combos are made of, with the numbers that tell them apart (the look comes from the kit's palette, the
 * sounds are given). Each class puts them together with its own other moves.
 */
final class ComboParts {

    static final double REACH = 11.0;

    private ComboParts() {
    }

    /** Where the player aims, no further than {@link #REACH} blocks, on the ground. */
    static Location target(MoveKit kit) {
        Location aim = kit.aim();
        Location from = kit.player.getLocation();
        Vector away = aim.toVector().subtract(from.toVector());
        if (away.length() > REACH) aim = from.clone().add(away.normalize().multiply(REACH));
        return kit.ground(aim);
    }

    /** Where a thrown or conjured thing leaves the player's hand. */
    static Location hand(MoveKit kit) {
        return kit.eye().add(kit.axes()[0].clone().multiply(0.6)).subtract(0.0, 0.3, 0.0);
    }

    /** A small glowing ball of the palette (a lantern, an orb). */
    static void orb(MoveKit kit, Location at, double size) {
        kit.fx(at).sphereShell(at, size, 0.0, 22, kit.palette(), 1.0);
    }

    /** A lob from the hand to {@code to} that bursts for {@code rate} within {@code radius}; {@code extra} runs at the landing. */
    static void lobBurst(MoveKit kit, Location to, double radius, double rate, Sound sound, Consumer<Location> extra) {
        kit.lob(hand(kit), to, 8, 2.0, land -> {
            kit.burst(land.clone().add(0.0, 0.3, 0.0), radius);
            kit.sound(land, sound, 0.8f, 1.3f);
            kit.sound(land, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.7f);
            kit.hitAll(kit.around(land, radius), rate);
            if (extra != null) extra.accept(land);
        });
    }

    /**
     * A mine: thrown to {@code to}, it waits up to 2.7 s and goes off when an enemy comes within {@code trigger} blocks:
     * {@code rate} within {@code radius}, and the enemies slowed by {@code slowPercent} (0 = no slow) for 2 s.
     */
    static void mine(MoveKit kit, String key, Location to, double trigger, double radius, double rate, double slowPercent) {
        boolean[] set = {false};
        kit.lob(hand(kit), to, 8, 1.5, land -> {
            Location mine = land.clone();
            kit.sound(mine, Sound.BLOCK_TRIPWIRE_ATTACH, 0.9f, 0.8f);
            kit.zone(key, mine, 55, 3, zone -> {
                orb(kit, mine.clone().add(0.0, 0.4, 0.0), 0.3);
                kit.fx(mine).flatRing(mine.clone().add(0.0, 0.1, 0.0), trigger - 0.1, 28, kit.palette(), 0.3, 0.3, 0.9);
                if (!kit.around(mine, trigger).isEmpty()) {
                    set[0] = true;
                    zone.stop();
                }
            }, zone -> {
                if (!set[0]) {
                    kit.fx(mine).particle(Particle.SMOKE_NORMAL, mine.getX(), mine.getY() + 0.3, mine.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
                    return;
                }
                kit.burst(mine.clone().add(0.0, 0.3, 0.0), radius);
                kit.shockwave(mine, 1.0, radius, 4, 1.0);
                kit.sound(mine, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                for (LivingEntity victim : kit.around(mine, radius)) {
                    if (kit.hit(victim, rate) && slowPercent > 0.0) kit.slow(victim, slowPercent, 2.0);
                }
            });
        });
    }

    /** A sigil on the ground at {@code at} for {@code life} ticks (at most 60): every {@code period} ticks it hits what stands in {@code radius}. {@code each} runs each pulse. */
    static void sigil(MoveKit kit, String key, Location at, double radius, double rate, int life, int period, Consumer<Location> each) {
        kit.zone(key, at, life, period, zone -> {
            kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), radius, 56, kit.palette(), 0.5 + 0.4 * (1.0 - zone.progress()), 0.4, 1.0);
            kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), radius * 0.62, 36, kit.palette(), 0.8, 0.4, 1.0);
            kit.hitAll(kit.around(at, radius), rate);
            if (each != null) each.accept(at);
        }, zone -> kit.shockwave(at, radius * 0.85, radius * 1.05, 3, 0.9));
    }

    /** {@code count} bolts that seek the enemies, from the hand's sides. */
    static void homingVolley(MoveKit kit, int count, double rate, double seek, Consumer<Location> end) {
        Vector[] axes = kit.axes();
        for (int index = 0; index < count; index++) {
            double side = (index - (count - 1) / 2.0) * 0.8;
            Location start = hand(kit).add(axes[1].clone().multiply(side)).add(0.0, 0.1, 0.0);
            Vector heading = axes[0].clone().add(axes[1].clone().multiply(side * 0.45)).add(new Vector(0.0, 0.3, 0.0));
            kit.later(index, () -> kit.homing(start, heading, 1.25, 42, rate, seek, end));
        }
    }

    /** {@code count} things fall from the sky round {@code aim} within {@code radius}, each hitting {@code hitRadius} for {@code rate}. */
    static void rain(MoveKit kit, Location aim, double radius, int count, double rate, double hitRadius, Sound sound) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < count; index++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = Math.sqrt(random.nextDouble()) * radius;
            Location spot = kit.ground(aim.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance));
            Location from = spot.clone().add(random.nextDouble(-1.5, 1.5), 9.0, random.nextDouble(-1.5, 1.5));
            kit.later(index * Math.max(1, 12 / Math.max(1, count)) , () -> {
                kit.line(from, spot, 0.2, 1.0, 1.1);
                kit.burst(spot.clone().add(0.0, 0.2, 0.0), hitRadius * 0.6);
                kit.sound(spot, sound, 0.4f, 1.3f);
                kit.hitAll(kit.around(spot, hitRadius), rate);
            });
        }
    }

    /** Something that swells for a second at {@code at}, pulling the enemies in, and bursts for {@code rate} within {@code radius} (slowing them by {@code slowPercent}). */
    static void pullBurst(MoveKit kit, Location at, double radius, double rate, double slowPercent) {
        kit.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
        kit.every(21, age -> {
            kit.fx(at).sphereShell(at, 0.5 + 1.5 * age / 20.0, age * 0.3, 32, kit.palette(), 1.1);
            if (age % 3 == 0) {
                for (LivingEntity victim : kit.around(at, radius + 1.0)) kit.pull(victim, at, 0.18);
            }
            if (age == 20) {
                kit.burst(at, radius);
                kit.shockwave(at.clone().add(0.0, -1.2, 0.0), 1.0, radius, 5, 1.2);
                kit.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
                for (LivingEntity victim : kit.around(at, radius)) {
                    if (kit.hit(victim, rate) && slowPercent > 0.0) kit.slow(victim, slowPercent, 2.5);
                }
            }
            return false;
        });
    }

    /** A line of light that hits everything within {@code width} of it for {@code length} blocks once. */
    static void pierce(MoveKit kit, Location start, Vector direction, double length, double width, double rate) {
        Location end = start.clone().add(direction.clone().multiply(length));
        kit.line(start, end, 0.3, 1.0, 1.2);
        kit.line(start.clone().add(0.0, 0.05, 0.0), end.clone().add(0.0, 0.05, 0.0), 0.7, 1.0, 0.9);
        Vector[] axes = kit.axes();
        for (int ring = 0; ring < 5; ring++) {
            Location at = start.clone().add(direction.clone().multiply(length * (0.15 + 0.2 * ring)));
            kit.fx(at).ring(at, axes[1], axes[2], 0.5 + 0.05 * ring, 14, kit.palette(), 0.5, 0.4, 1.0);
        }
        kit.hitAll(kit.corridor(start, direction, length, width), rate);
    }

    /** A fan of {@code count} bolts across {@code spreadDegrees}, from the hand. */
    static void fanBolts(MoveKit kit, int count, double spreadDegrees, double rate, double speed, double range) {
        Vector[] axes = kit.axes();
        Location start = hand(kit);
        for (int index = 0; index < count; index++) {
            double turn = Math.toRadians((index - (count - 1) / 2.0) * spreadDegrees / Math.max(1, count - 1));
            Vector direction = axes[0].clone().multiply(Math.cos(turn)).add(axes[1].clone().multiply(Math.sin(turn))).normalize();
            kit.later(index / 3, () -> kit.bolt(start.clone(), direction, speed, range, rate, end -> kit.burst(end, 0.8)));
        }
    }

    /** {@code count} bolts one after the other (a tick apart) straight at the aim, each a little off the way. */
    static void quickBolts(MoveKit kit, int count, double rate, double speed, double range, double waver) {
        Vector[] axes = kit.axes();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < count; index++) {
            int which = index;
            kit.later(index, () -> {
                Location start = hand(kit).add(axes[1].clone().multiply(which % 2 == 0 ? 0.3 : -0.3));
                Vector direction = kit.aim().toVector().subtract(start.toVector());
                direction = direction.lengthSquared() < 1.0E-6 ? axes[0].clone() : direction.normalize();
                if (which > 0 && waver > 0.0) {
                    direction.add(axes[1].clone().multiply(random.nextDouble(-waver, waver))).add(axes[2].clone().multiply(random.nextDouble(-waver, waver))).normalize();
                }
                kit.bolt(start, direction, speed, range, rate, end -> kit.burst(end, 0.7));
            });
        }
    }
}
