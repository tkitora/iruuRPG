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
 * The mage's combo with a melee weapon (a spell blade), made again: the blade cuts and what it cuts leaves magic behind. Crescents of light fly off the edge, runes are
 * struck into the air and into the ground and go off a moment later, orbs peel off the blade and seek, and a rune on the ground pulls the enemies together before it bursts.
 * The sounds are the allay's. Normal: 1 two crescent waves one after the other (45% each, they pierce), 2 a rune thrust (30% for the thrust, a rune before the blade bursts
 * after 0.3 s for 70%), 3 an orb volley (three seeking orbs, 33% each), 4 a gravity rune on the ground at the nearest enemy for 1.2 s that pulls the enemies in and bursts
 * (110%, slows), 5 a nova cut (a charge of 0.3 s, then a ring of 4 blocks: 125%). Heavy: 0 a spell shield (50% less damage taken for 2 s, a pulse every 0.4 s that pushes
 * the enemies off a little: 10%; its own cooldown of 6 s), 1 a storm of five crescents in a fan (20% each), 2 a chain of three runes along the aim (36% each), 3 an arcane lance (a
 * charge of 0.4 s, then 110% on all in a line of 10 blocks), 4 a falling star at the aim (125% round where it lands).
 * The numbers are 15% under the older version of this combo (the mage's melee damage was turned down).
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
    private static void crescent(MoveKit kit, Location start, Vector direction, double speed, double range, double tilt, double rate) {
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
        kit.sound(start, Sfx.MAGIC, 0.5f, 1.6f);
        kit.every(ticks + 1, age -> {
            Fx fx = kit.fx(position[0]);
            if (fx.active()) {
                int points = 16;
                for (int index = 0; index < points; index++) {
                    double t = index / (double) (points - 1);
                    double angle = Math.toRadians(-55.0 + 110.0 * t);
                    double radius = 1.5;
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
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.6));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector[] axes = kit.axes();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                // two crescents, the first upright, the second slanted the other way
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 3L, () -> {
                        kit.slash("arc", 2.3, 130.0, 40.0, which % 2 == 0 ? 0.0 : 180.0, 1.9, 0.4, 0.0, 1.0, 0.09, 0.0);
                        Location start = kit.eye().add(axes[0].clone().multiply(1.2)).subtract(0.0, 0.5, 0.0);
                        crescent(kit, start, axes[0], 1.15, 9.0, which % 2 == 0 ? 0.35 : -0.35, rate);
                    });
                }
            }
            case 2 -> {
                // a thrust, and a rune struck in the air ahead that goes off a moment later
                kit.slash("line", 2.6, 100.0, 0.0, 0.0, 2.0, 0.2, 0.0, 1.1, 0.08, 0.0);
                kit.sound(feet, Sfx.MAGIC, 0.7f, 1.2f);
                kit.hitAll(kit.cone(3.2, 40.0), 0.3);
                Location at = kit.eye().add(axes[0].clone().multiply(2.6));
                rune(kit, at, axes[1], axes[2], 1.3, 6, () -> {
                    kit.burst(at, 2.0);
                    kit.sound(at, Sfx.MAGIC, 0.9f, 0.9f);
                    kit.hitAll(kit.around(at, 2.4), rate);
                });
            }
            case 3 -> {
                // three orbs peel off the blade and seek
                kit.sound(feet, Sfx.MAGIC, 0.7f, 1.7f);
                for (int index = 0; index < hits; index++) {
                    double side = (index - (hits - 1) / 2.0) * 0.8;
                    Location start = kit.eye().add(axes[0].clone().multiply(0.8)).add(axes[1].clone().multiply(side)).subtract(0.0, 0.3, 0.0);
                    Vector heading = axes[0].clone().add(axes[1].clone().multiply(side * 0.5)).add(new Vector(0.0, 0.15, 0.0));
                    kit.later(index, () -> kit.homing(start, heading, 1.25, 36, rate, 10.0, end -> kit.burst(end, 1.0)));
                }
            }
            case 4 -> {
                // a rune on the ground at the nearest enemy (else ahead): for 1.2 s it draws the enemies in, then bursts
                LivingEntity near = kit.nearest(9.0, 80.0);
                Location centre = kit.ground(near != null ? near.getLocation() : feet.clone().add(facing.clone().multiply(4.0)));
                kit.sound(centre, Sfx.MAGIC_DARK, 0.8f, 0.9f);
                kit.zone("mage_gravity", centre, 24, 2, zone -> {
                    SkillFx.circle(kit.player, centre.clone().add(0.0, 0.15, 0.0), new Vector(1, 0, 0), new Vector(0, 0, 1), 3.6 - 1.4 * zone.progress(), zone.age(), kit.palette());
                    if (zone.age() % 4 == 0) {
                        for (LivingEntity victim : kit.around(centre, 4.4)) kit.pull(victim, centre, 0.16);
                    }
                }, zone -> {
                    kit.burst(centre.clone().add(0.0, 0.4, 0.0), 3.2);
                    kit.shockwave(centre, 1.0, 3.4, 4, 1.1);
                    kit.sound(centre, Sfx.MAGIC, 1.0f, 0.7f);
                    for (LivingEntity victim : kit.around(centre, 3.4)) {
                        if (kit.hit(victim, rate)) kit.slow(victim, 40.0, 1.5);
                    }
                });
            }
            default -> {
                // a nova cut: rings draw in for 0.3 s, then a ring of four blocks goes off round the mage
                kit.sound(feet, Sfx.MAGIC, 0.9f, 0.8f);
                for (int tick = 0; tick < 6; tick++) {
                    double r = 4.2 - 2.6 * tick / 5.0;
                    kit.later(tick, () -> kit.fx(kit.player.getLocation()).flatRing(kit.player.getLocation().add(0.0, 0.2, 0.0), r, 40, kit.palette(), 0.4, 0.2, 1.0));
                }
                kit.later(6L, () -> {
                    Location at = kit.player.getLocation();
                    kit.slash("arc", 3.2, 330.0, 70.0, 0.0, 0.4, 0.6, 0.0, 1.5, 0.16, 0.0);
                    kit.shockwave(at, 1.0, 4.2, 5, 1.2);
                    kit.fx(at).sphereShell(at.clone().add(0.0, 1.0, 0.0), 1.8, 0.0, 48, kit.palette(), 1.0);
                    kit.sound(at, Sfx.MAGIC, 1.0f, 0.7f);
                    for (LivingEntity victim : kit.around(at, 4.0)) {
                        if (kit.hit(victim, rate)) kit.push(victim, at.toVector(), 0.4, 0.1);
                    }
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.7));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Vector[] axes = kit.axes();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a spell shield: a sphere of light turning round the mage for 2 s; damage is halved, and every 0.4 s it pulses and pushes the enemies off
                kit.guard(0.5, 40);
                kit.sound(feet, Sfx.MAGIC, 1.0f, 1.0f);
                kit.zone("mage_shield", feet, 40, 2, zone -> {
                    Location centre = kit.player.getLocation().add(0.0, 1.0, 0.0);
                    kit.fx(centre).sphereShell(centre, 1.7, zone.age() * 0.15, 40, kit.palette(), 1.0);
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
                // a chain of three runes along the aim, each struck a little after the one before
                for (int index = 1; index <= 3; index++) {
                    int which = index;
                    Location at = kit.ground(feet.clone().add(kit.facing().multiply(3.0 * index))).add(0.0, 0.2, 0.0);
                    kit.later((index - 1) * 4L, () -> rune(kit, at, new Vector(1, 0, 0), new Vector(0, 0, 1), 1.8, 6, () -> {
                        kit.burst(at.clone().add(0.0, 0.3, 0.0), 2.2);
                        kit.shockwave(at, 0.8, 2.4, 3, 1.0);
                        kit.sound(at, Sfx.MAGIC, 0.8f, 0.9f + 0.15f * which);
                        kit.hitAll(kit.around(at, 2.4), rate);
                    }));
                }
            }
            case 3 -> {
                // an arcane lance: a charge of 0.4 s in front of the mage, then a beam through the line ahead
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
                    kit.sound(base, Sfx.MAGIC, 1.0f, 1.2f);
                    kit.hitAll(kit.corridor(base, axes[0], 10.0, 1.1), rate);
                });
            }
            default -> {
                // a falling star at the aim: a ring marks the spot, the star comes down in six ticks
                Location target = ComboParts.target(kit);
                Location from = target.clone().add(0.0, 14.0, 0.0);
                kit.sound(target, Sfx.MAGIC_DARK, 1.0f, 0.8f);
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
                        kit.sound(target, Sfx.MAGIC, 1.0f, 0.6f);
                        for (LivingEntity victim : kit.around(target, 3.6)) {
                            if (kit.hit(victim, rate)) kit.push(victim, target.toVector(), 0.4, 0.15);
                        }
                    });
                });
            }
        }
    }
}
