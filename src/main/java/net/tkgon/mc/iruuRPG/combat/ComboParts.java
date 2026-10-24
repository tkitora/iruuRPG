package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
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

    /**
     * The middle of what a placed-thing combo puts down: a few blocks ahead of where the combo began (not of where the player stands now, nor where they aim),
     * leaned toward the aim by up to 3 blocks so the player can still choose the side, on the ground.
     */
    static Location target(MoveKit kit) {
        Location origin = kit.origin();
        Vector facing = kit.originFacing();
        Location centre = origin.clone().add(facing.multiply(3.0));
        Location aim = kit.aim();
        Vector lean = aim.toVector().subtract(centre.toVector()).setY(0.0);
        if (lean.length() > 3.0) lean = lean.normalize().multiply(3.0);
        return kit.ground(centre.add(lean));
    }

    /** A light that comes down on the spot where something is set (nothing is thrown from the player's hand). */
    static void appear(MoveKit kit, Location at) {
        kit.line(at.clone().add(0.0, 5.0, 0.0), at.clone().add(0.0, 0.3, 0.0), 0.2, 1.0, 1.2);
        kit.burst(at.clone().add(0.0, 0.3, 0.0), 0.9);
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
        appear(kit, to);
        kit.later(2L, () -> {
            Location mine = to.clone();
            kit.sound(mine, Sound.BLOCK_TRIPWIRE_ATTACH, 0.9f, 0.8f);
            kit.zone(key, mine, kit.linger(55), 3, zone -> {
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
        kit.zone(key, at, kit.linger(life), period, zone -> {
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
        pullBurst(kit, at, radius, rate, slowPercent, 20);
    }

    /** The same, swelling for {@code ticks} ticks (at most 50: it stands in the world that long). */
    /**
     * A ball of light that swells for {@code ticks} ticks and bursts: nothing is pulled or held. Every few ticks, while the player stands within the radius, they are
     * healed {@code healPercent}; the burst hurts everyone in the radius for {@code rate}.
     */
    static void swellBurst(MoveKit kit, Location at, double radius, double rate, double healPercent, int ticks) {
        int life = Math.max(10, Math.min(50, ticks));
        kit.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.4f);
        kit.every(life + 1, age -> {
            kit.fx(at).sphereShell(at, 0.5 + 1.5 * age / (double) life, age * 0.3, 32, kit.palette(), 1.1);
            if (age % 8 == 4 && healPercent > 0.0 && kit.player.getLocation().distanceSquared(at) <= (radius + 1.5) * (radius + 1.5)) kit.healCapped(healPercent);
            if (age == life) {
                kit.burst(at, radius);
                kit.shockwave(at.clone().add(0.0, -1.2, 0.0), 1.0, radius, 5, 1.2);
                kit.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.1f);
                for (LivingEntity victim : kit.around(at, radius)) kit.hit(victim, rate);
            }
            return false;
        });
    }

    static void pullBurst(MoveKit kit, Location at, double radius, double rate, double slowPercent, int ticks) {
        int life = Math.max(10, Math.min(50, ticks));
        kit.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
        kit.every(life + 1, age -> {
            kit.fx(at).sphereShell(at, 0.5 + 1.5 * age / (double) life, age * 0.3, 32, kit.palette(), 1.1);
            if (age % 3 == 0) {
                for (LivingEntity victim : kit.around(at, radius + 1.0)) kit.pull(victim, at, 0.18);
            }
            if (age == life) {
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

    // ---- placed things: a deployed weapon sets things in the world and they stay for a few seconds ------------------------------

    /** A placed lantern drawn at {@code at} (on the ground): a pole of light, an orb on top that swells with {@code pulse} (0 - 1) and a small ring at its foot. */
    static void standLantern(MoveKit kit, Location at, double height, double pulse) {
        Fx fx = kit.fx(at);
        if (!fx.active()) return;
        // the ranger's placed things are traps
        if ("gifted".equals(kit.classId())) {
            standTrap(kit, fx, at, height, pulse);
            return;
        }
        for (double y = 0.1; y < height; y += 0.3) fx.dust(at.getX(), at.getY() + y, at.getZ(), kit.palette(), 0.2 + 0.4 * y / height, 0.9);
        fx.sphereShell(at.clone().add(0.0, height + 0.15, 0.0), 0.25 + 0.12 * pulse, 0.0, 14, kit.palette(), 1.0);
        fx.flatRing(at.clone().add(0.0, 0.12, 0.0), 0.55, 14, kit.palette(), 0.5, 0.1, 0.9);
    }

    /**
     * A trap drawn at {@code at} (on the ground): a ring of eight teeth that stand open and, as {@code pulse} (0 - 1) rises, snap shut toward the middle, a stake with a chain
     * to the ring and a little dust at the middle where the trigger is.
     */
    private static void standTrap(MoveKit kit, Fx fx, Location at, double height, double pulse) {
        double reach = 0.7 - 0.35 * pulse;
        double tooth = 0.4 * Math.min(1.3, height) * (1.0 - 0.5 * pulse);
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI * 2.0 * index / 8.0;
            Location foot = at.clone().add(Math.cos(angle) * 0.7, 0.1, Math.sin(angle) * 0.7);
            Location tip = at.clone().add(Math.cos(angle) * reach, 0.1 + tooth, Math.sin(angle) * reach);
            fx.line(foot, tip, kit.palette(), 0.4, 1.0, 0.0, 0.9, 0.3, null, 1);
        }
        fx.flatRing(at.clone().add(0.0, 0.12, 0.0), 0.7, 18, kit.palette(), 0.5, 0.1, 0.9);
        Location stake = at.clone().add(0.9, 0.0, 0.0);
        fx.line(stake, stake.clone().add(0.0, 0.5, 0.0), kit.palette(), 0.2, 0.6, 0.0, 0.9, 0.3, null, 1);
        fx.line(stake.clone().add(0.0, 0.3, 0.0), at.clone().add(0.7, 0.15, 0.0), kit.palette(), 0.3, 0.5, 0.0, 0.8, 0.3, null, 1);
        fx.dust(at.getX(), at.getY() + 0.15, at.getZ(), kit.palette(), 0.4 + 0.5 * pulse, 0.9);
    }

    /** A lantern lobbed to {@code to}: see {@link #placeAt}. */
    static void place(MoveKit kit, String key, Location to, int life, int every, double radius, double rate, double slowPercent, double healPercent,
                      Consumer<Location> each) {
        appear(kit, to);
        kit.later(2L, () -> placeAt(kit, key, to, life, every, radius, rate, slowPercent, healPercent, each));
    }

    /**
     * A lantern standing at {@code at} for {@code life} ticks (at most 60): every {@code every} ticks (a multiple of 2) it pulses: a wave
     * runs out to {@code radius}, every enemy in it takes a hit of {@code rate} (and is slowed by {@code slowPercent} when above 0), the
     * player is healed {@code healPercent} when within reach, and {@code each} runs. One of the same {@code key} is replaced.
     */
    static void placeAt(MoveKit kit, String key, Location at, int life, int every, double radius, double rate, double slowPercent, double healPercent,
                        Consumer<Location> each) {
        kit.sound(at, Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.3f);
        kit.zone(key, at, kit.linger(life), 2, zone -> {
            boolean pulse = zone.age() % every == 0;
            standLantern(kit, at, 1.0, pulse ? 1.0 : 0.0);
            if (zone.age() % 6 == 0) kit.fx(at).flatRing(at.clone().add(0.0, 0.12, 0.0), radius, (int) Math.max(18, radius * 8), kit.palette(), 0.35, 0.15, 0.8);
            if (!pulse) return;
            kit.sound(at, Sfx.MAGIC, 0.3f, 1.5f);
            kit.shockwave(at, radius * 0.3, radius, 3, 0.9);
            for (LivingEntity victim : kit.around(at, radius)) {
                boolean hit = rate <= 0.0 || kit.hit(victim, rate);
                if (hit && slowPercent > 0.0) kit.slow(victim, slowPercent, 1.5);
            }
            if (healPercent > 0.0 && kit.player.getLocation().distanceSquared(at) <= (radius + 1.5) * (radius + 1.5)) kit.healCapped(healPercent);
            if (each != null) each.accept(at);
        }, zone -> kit.shockwave(at, radius * 0.8, radius, 2, 0.8));
    }

    /** A turret lobbed to {@code to}: see {@link #turretAt}. */
    static void turret(MoveKit kit, String key, Location to, int life, int every, double range, double rate, Consumer<LivingEntity> onShot) {
        appear(kit, to);
        kit.later(2L, () -> turretAt(kit, key, to, life, every, range, rate, onShot));
    }

    /** A lantern standing at {@code at} for {@code life} ticks that, every {@code every} ticks (a multiple of 2), shoots the nearest enemy within {@code range} for {@code rate}. */
    static void turretAt(MoveKit kit, String key, Location at, int life, int every, double range, double rate, Consumer<LivingEntity> onShot) {
        kit.sound(at, Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.3f);
        kit.zone(key, at, kit.linger(life), 2, zone -> {
            boolean fire = zone.age() % every == 0;
            standLantern(kit, at, 1.2, fire ? 1.0 : 0.0);
            if (!fire) return;
            LivingEntity nearest = null;
            double best = range * range;
            for (LivingEntity victim : kit.around(at, range)) {
                double distance = victim.getLocation().distanceSquared(at);
                if (distance < best) {
                    best = distance;
                    nearest = victim;
                }
            }
            if (nearest == null) return;
            Location top = at.clone().add(0.0, 1.35, 0.0);
            Location chest = nearest.getLocation().add(0.0, nearest.getHeight() * 0.5, 0.0);
            kit.line(top, chest, 0.2, 1.0, 1.1);
            kit.burst(chest, 0.7);
            kit.sound(top, Sfx.MAGIC, 0.35f, 1.9f);
            if (kit.hit(nearest, rate) && onShot != null) onShot.accept(nearest);
        }, zone -> kit.shockwave(at, 0.6, 1.6, 2, 0.8));
    }

    /** A bomb lobbed to {@code to}: see {@link #bombAt}. */
    static void bomb(MoveKit kit, String key, Location to, int fuse, double radius, double rate, double knock) {
        appear(kit, to);
        kit.later(2L, () -> bombAt(kit, key, to, fuse, radius, rate, knock));
    }

    /** A bomb that sits at {@code at} for {@code fuse} ticks (at most 60), its orb swelling and its reach shown, and then goes off: {@code rate} within {@code radius}, a push of {@code knock} (0 = none). */
    static void bombAt(MoveKit kit, String key, Location at, int fuse, double radius, double rate, double knock) {
        kit.sound(at, Sound.ENTITY_TNT_PRIMED, 0.7f, 1.2f);
        kit.zone(key, at, fuse, 2, zone -> {
            standLantern(kit, at, 0.5, zone.progress());
            if (zone.age() % 4 == 0) kit.fx(at).flatRing(at.clone().add(0.0, 0.12, 0.0), radius * (0.6 + 0.4 * zone.progress()), (int) Math.max(18, radius * 8), kit.palette(), 0.3 + 0.6 * zone.progress(), 0.15, 0.9);
        }, zone -> {
            kit.burst(at.clone().add(0.0, 0.4, 0.0), radius);
            kit.shockwave(at, 1.0, radius, 4, 1.1);
            kit.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.2f);
            for (LivingEntity victim : kit.around(at, radius)) {
                if (kit.hit(victim, rate) && knock > 0.0) kit.push(victim, at.toVector(), knock, knock * 0.5);
            }
        });
    }

    /**
     * A beacon standing at {@code at} for {@code life} ticks (at most 60) that makes things fall onto the ground within {@code radius}
     * round it, one every {@code every} ticks (a multiple of 2), each hitting for {@code rate} within 1.4 blocks.
     */
    static void rainBeacon(MoveKit kit, String key, Location at, int life, int every, double radius, double rate, Sfx sound) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        kit.sound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.7f);
        kit.zone(key, at, kit.linger(life), 2, zone -> {
            for (double y = 0.0; y < 3.0; y += 0.4) kit.fx(at).dust(at.getX(), at.getY() + y, at.getZ(), kit.palette(), 0.3 + 0.5 * y / 3.0, 1.0);
            if (zone.age() % 6 == 0) kit.fx(at).flatRing(at.clone().add(0.0, 0.12, 0.0), radius, (int) Math.max(24, radius * 9), kit.palette(), 0.4, 0.15, 0.8);
            if (zone.age() % every != 0) return;
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = Math.sqrt(random.nextDouble()) * radius;
            Location spot = kit.ground(at.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance));
            Location from = spot.clone().add(random.nextDouble(-1.0, 1.0), 8.0, random.nextDouble(-1.0, 1.0));
            kit.line(from, spot, 0.2, 1.0, 1.1);
            kit.burst(spot.clone().add(0.0, 0.2, 0.0), 0.8);
            kit.sound(spot, sound, 0.4f, 1.3f);
            kit.hitAll(kit.around(spot, 1.4), rate);
        }, null);
    }
}
