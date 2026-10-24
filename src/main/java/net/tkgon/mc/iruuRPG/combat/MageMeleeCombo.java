package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The mage's combo with a melee weapon, a scythe: wide cuts that reap, and what they reap is let go as souls. The reach is between the assassin's and the warrior's
 * (3.3 to 3.8 blocks, wide arcs), the sounds are the sweep, the phantom and the skeleton ({@link Sfx#REAP}).
 * Normal: 1 two sweeps, out and back (29% each), 2 a reaping cut that lifts (79%), 3 a whirl, three spins round (32% each), 4 a soul wave, a wide crescent that flies
 * 11 blocks through everything (118%), 5 the harvest (a pull in for 0.3 s, then a full circle of 3.8 blocks: 138%, slows). Heavy: 0 a reaper's veil (50% less damage taken
 * for 2 s, souls turn round the mage and pulse every 0.4 s: 10%; its own cooldown of 6 s), 1 a fan of five crescents (20% each), 2 three soul pillars along the aim
 * (36% each), 3 a cleaving line (a charge of 0.4 s, then 110% on all in a line of 10 blocks), 4 a falling scythe at the aim (125% round where it lands).
 */
public final class MageMeleeCombo extends BaseCombo {

    static final String PLAN = "mage_melee";

    public MageMeleeCombo() {
        super("mage", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.29, 0.79, 0.32, 1.18, 1.38}, new int[]{2, 1, 3, 1, 1},
                new double[]{0.1, 0.2, 0.36, 1.1, 1.25}, new int[]{120, 0, 0, 0, 0}, 40));
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

    /** A rune struck in the plane of {@code right} and {@code up} round {@code at}: it shows, closing in, for {@code ticks} ticks, then {@code after} runs. */
    private static void rune(MoveKit kit, Location at, Vector right, Vector up, double radius, int ticks, Runnable after) {
        kit.sound(at, Sfx.MAGIC, 0.6f, 1.4f);
        for (int tick = 0; tick < ticks; tick++) {
            int which = tick;
            kit.later(tick, () -> SkillFx.circle(kit.player, at, right, up, radius * (1.0 - 0.25 * which / ticks), which * 3, kit.palette()));
        }
        kit.later(ticks, after);
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.8));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector[] axes = kit.axes();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                // two sweeps, out and back: a wide arc each way
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 4L, () -> {
                        kit.slash("arc", 3.4, 160.0, 15.0, which % 2 == 0 ? 0.0 : 180.0, 1.7, 0.5, 0.0, 1.2, 0.1, 0.0);
                        kit.sound(kit.player.getLocation(), Sfx.REAP, 0.8f, which % 2 == 0 ? 1.0f : 1.2f);
                        kit.particle(org.bukkit.Particle.SOUL, kit.player.getLocation().add(kit.facing().multiply(2.2)).add(0.0, 1.0, 0.0), 4, 0.8, 0.02);
                        kit.hitAll(kit.cone(3.4, 75.0), rate);
                    });
                }
            }
            case 2 -> {
                // a reaping cut from high to low: the hook of the blade lifts what it catches
                kit.slash("drop", 3.6, 140.0, 10.0, 0.0, 1.8, 0.1, 0.0, 1.3, 0.12, 0.0);
                kit.sound(feet, Sfx.REAP_HEAVY, 0.7f, 1.1f);
                for (LivingEntity victim : kit.cone(3.6, 60.0)) {
                    if (kit.hit(victim, rate)) kit.lift(victim, 0.3);
                }
            }
            case 3 -> {
                // a whirl: three spins round the mage, a flat ring of cut each time
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 3L, () -> {
                        Location at = kit.player.getLocation();
                        kit.slash("arc", 3.3, 320.0, 70.0, which % 2 == 0 ? 0.0 : 180.0, 0.4, 0.6, 0.0, 1.2, 0.08, 0.0);
                        kit.fx(at).flatRing(at.clone().add(0.0, 0.3 + 0.35 * which, 0.0), 3.2, 36, kit.palette(), 0.5, 0.2, 1.0);
                        kit.sound(at, Sfx.REAP, 0.7f, 0.9f + 0.15f * which);
                        kit.hitAll(kit.around(at, 3.3), rate);
                    });
                }
            }
            case 4 -> {
                // a soul wave: the cut leaves the blade as a wide crescent that goes on through everything for 11 blocks
                kit.slash("arc", 3.6, 150.0, 30.0, 0.0, 1.8, 0.4, 0.0, 1.4, 0.12, 0.0);
                kit.sound(feet, Sfx.SOUL, 0.8f, 1.0f);
                Location start = kit.eye().add(axes[0].clone().multiply(1.4)).subtract(0.0, 0.5, 0.0);
                crescent(kit, start, axes[0], 1.2, 11.0, 0.2, rate, 2.3);
            }
            default -> {
                // the harvest: the enemies are drawn in for 0.3 s, then the whole circle is cut
                kit.sound(feet, Sfx.SOUL, 0.9f, 0.8f);
                for (int tick = 0; tick < 6; tick++) {
                    double r = 4.8 - 2.6 * tick / 5.0;
                    kit.later(tick, () -> {
                        Location at = kit.player.getLocation();
                        kit.fx(at).flatRing(at.clone().add(0.0, 0.2, 0.0), r, 40, kit.palette(), 0.4, 0.2, 1.0);
                        for (LivingEntity victim : kit.around(at, 4.8)) kit.pull(victim, at, 0.08);
                    });
                }
                kit.later(6L, () -> {
                    Location at = kit.player.getLocation();
                    kit.slash("arc", 3.8, 340.0, 70.0, 0.0, 0.4, 0.5, 0.0, 1.6, 0.16, 0.0);
                    kit.shockwave(at, 1.0, 4.2, 5, 1.2);
                    kit.fx(at).sphereShell(at.clone().add(0.0, 1.0, 0.0), 1.8, 0.0, 48, kit.palette(), 1.0);
                    kit.particle(org.bukkit.Particle.SOUL_FIRE_FLAME, at.clone().add(0.0, 1.0, 0.0), 30, 1.8, 0.08);
                    kit.sound(at, Sfx.REAP_HEAVY, 1.0f, 0.8f);
                    for (LivingEntity victim : kit.around(at, 3.8)) {
                        if (kit.hit(victim, rate)) kit.slow(victim, 40.0, 1.5);
                    }
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.85));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Vector[] axes = kit.axes();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a reaper's veil: souls turning round the mage for 2 s; damage is halved, and every 0.4 s they pulse and push the enemies off
                kit.guard(0.5, 40);
                kit.sound(feet, Sfx.SOUL, 1.0f, 1.0f);
                kit.zone("mage_shield", feet, 40, 2, zone -> {
                    Location centre = kit.player.getLocation().add(0.0, 1.0, 0.0);
                    kit.fx(centre).sphereShell(centre, 1.7, zone.age() * 0.15, 40, kit.palette(), 1.0);
                    for (int soul = 0; soul < 3; soul++) {
                        double angle = zone.age() * 0.35 + soul * Math.PI * 2.0 / 3.0;
                        kit.particle(org.bukkit.Particle.SOUL_FIRE_FLAME, centre.clone().add(Math.cos(angle) * 1.7, 0.3 * Math.sin(angle * 2.0), Math.sin(angle) * 1.7), 2, 0.05, 0.01);
                    }
                    if (zone.age() % 8 == 0) {
                        kit.shockwave(kit.player.getLocation(), 0.8, 2.8, 2, 0.9);
                        for (LivingEntity victim : kit.around(kit.player.getLocation(), 2.8)) {
                            if (kit.hit(victim, rate)) kit.push(victim, kit.player.getLocation().toVector(), 0.3, 0.05);
                        }
                    }
                }, null);
            }
            case 1 -> {
                // five crescents in a fan
                for (int index = 0; index < 5; index++) {
                    double turn = Math.toRadians((index - 2) * 14.0);
                    Vector direction = axes[0].clone().multiply(Math.cos(turn)).add(axes[1].clone().multiply(Math.sin(turn))).normalize();
                    Location start = kit.eye().add(direction.clone().multiply(1.0)).subtract(0.0, 0.5, 0.0);
                    double tilt = (index - 2) * 0.35;
                    kit.later(index / 2, () -> crescent(kit, start, direction, 1.1, 8.0, tilt, rate));
                }
            }
            case 2 -> {
                // three soul pillars along the aim, each risen a little after the one before
                for (int index = 1; index <= 3; index++) {
                    int which = index;
                    Location at = kit.ground(feet.clone().add(kit.facing().multiply(3.0 * index))).add(0.0, 0.2, 0.0);
                    kit.later((index - 1) * 4L, () -> rune(kit, at, new Vector(1, 0, 0), new Vector(0, 0, 1), 1.8, 6, () -> {
                        kit.burst(at.clone().add(0.0, 0.3, 0.0), 2.2);
                        kit.shockwave(at, 0.8, 2.4, 3, 1.0);
                        kit.sound(at, Sfx.SOUL, 0.8f, 0.9f + 0.15f * which);
                        kit.particle(org.bukkit.Particle.SOUL_FIRE_FLAME, at.clone().add(0.0, 1.0, 0.0), 24, 0.5, 0.1);
                        kit.hitAll(kit.around(at, 2.4), rate);
                    }));
                }
            }
            case 3 -> {
                // a cleaving line: a charge of 0.4 s in front of the mage, then a cut through the line ahead
                Location base = kit.eye().add(axes[0].clone().multiply(1.2)).subtract(0.0, 0.3, 0.0);
                kit.sound(feet, Sfx.MAGIC, 1.0f, 0.7f);
                for (int tick = 0; tick < 8; tick++) {
                    int which = tick;
                    kit.later(tick, () -> kit.fx(base).ring(base.clone().add(axes[0].clone().multiply(0.8)), axes[1], axes[2], 1.3 - 0.1 * which, 20, kit.palette(), 0.5, 0.2, 1.0));
                }
                kit.later(8L, () -> {
                    Location end = base.clone().add(axes[0].clone().multiply(10.0));
                    kit.line(base, end, 0.2, 1.0, 1.6);
                    kit.line(base.clone().add(0.0, 0.08, 0.0), end.clone().add(0.0, 0.08, 0.0), 0.7, 1.0, 1.1);
                    for (int ring = 1; ring <= 5; ring++) {
                        Location at = base.clone().add(axes[0].clone().multiply(ring * 1.8));
                        kit.fx(at).ring(at, axes[1], axes[2], 0.6, 16, kit.palette(), 0.6, 0.3, 1.0);
                    }
                    kit.sound(base, Sfx.REAP_HEAVY, 1.0f, 1.1f);
                    kit.hitAll(kit.corridor(base, axes[0], 10.0, 1.1), rate);
                });
            }
            default -> {
                // a falling scythe at the aim: a ring marks the spot, the blade comes down in six ticks
                Location target = ComboParts.target(kit);
                Location from = target.clone().add(0.0, 14.0, 0.0);
                kit.sound(target, Sfx.SOUL, 1.0f, 0.8f);
                for (int tick = 0; tick < 6; tick++) {
                    double r = 3.6 - 2.0 * tick / 5.0;
                    kit.later(tick, () -> kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), r, 40, kit.palette(), 0.5, 0.2, 1.0));
                }
                kit.later(6L, () -> {
                    for (int tick = 0; tick < 5; tick++) {
                        double t0 = tick / 5.0;
                        double t1 = (tick + 1) / 5.0;
                        Location a = from.clone().add(target.clone().subtract(from).toVector().multiply(t0));
                        Location b = from.clone().add(target.clone().subtract(from).toVector().multiply(t1));
                        kit.later(tick, () -> kit.line(a, b, 0.2, 1.0, 1.6));
                    }
                    kit.later(5L, () -> {
                        kit.burst(target.clone().add(0.0, 0.4, 0.0), 3.4);
                        kit.shockwave(target, 1.0, 3.8, 5, 1.2);
                        kit.sound(target, Sfx.REAP_HEAVY, 1.0f, 0.7f);
                        for (LivingEntity victim : kit.around(target, 3.6)) {
                            if (kit.hit(victim, rate)) kit.push(victim, target.toVector(), 0.4, 0.15);
                        }
                    });
                });
            }
        }
    }
}
