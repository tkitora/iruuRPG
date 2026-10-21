package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The ranger's combo with a ranged weapon (the reworked one): many hands, and things that are planted. Arrows are fast. A
 * melee hit of the ranger makes the next arrow stronger and faster (the loop). Normal: 1 two arrows (55% x2), 2 three arrows (40% x3),
 * 3 a piercing arrow (120% on all in the line), 4 a fan of five arrows (28% x5), 5 a great arrow that bursts (100% and 50%). Heavy: 0 an arrow
 * that plants a trap for 2.7 s (130%, slows), 1 an arrow that bounces twice (40% x3), 2 three spike traps planted round the aim (40% each, 2.7 s),
 * 3 four homing arrows (35% x4), 4 a rain of arrows on the aim (12% x12).
 */
public final class RangerRangeCombo implements ClassCombo {

    static final String PLAN = "ranger_range";

    private static final ComboSpec SPEC = new ComboSpec(PLAN,
            new double[]{0.55, 0.4, 1.2, 0.28, 1.0}, new int[]{2, 3, 1, 5, 1},
            new double[]{1.3, 0.4, 0.4, 0.35, 0.12}, new int[]{100, 0, 0, 0, 0}, 40);

    @Override
    public String classId() {
        return "gifted";
    }

    @Override
    public AttackType type() {
        return AttackType.RANGE;
    }

    @Override
    public ComboSpec spec() {
        return SPEC;
    }

    private Location muzzle(MoveKit kit, double side, double up) {
        Vector[] axes = kit.axes();
        return kit.eye().add(axes[0].clone().multiply(0.6)).add(axes[1].clone().multiply(side)).add(axes[2].clone().multiply(up));
    }

    /** From the muzzle to the aim: the direction of an arrow. */
    private Vector toward(MoveKit kit, Location start) {
        Vector direction = kit.aim().toVector().subtract(start.toVector());
        return direction.lengthSquared() < 1.0E-6 ? kit.axes()[0] : direction.normalize();
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.25));
        double[] boost = kit.rangeBoost();
        double rate = step.damageMultiplier() * boost[0];
        double speed = 2.0 * boost[1];
        int hits = step.extraHits() + 1;
        Vector[] axes = kit.axes();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (step.stage()) {
            case 1 -> {
                // two quick arrows, a tick apart, from either side of the bow
                for (int index = 0; index < hits; index++) {
                    double side = index == 0 ? 0.3 : -0.3;
                    kit.later(index, () -> {
                        Location start = muzzle(kit, side, -0.1);
                        kit.arrow(start, toward(kit, start), rate, speed, null);
                    });
                }
            }
            case 2 -> {
                // three arrows that waver a little
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index, () -> {
                        Location start = muzzle(kit, which % 2 == 0 ? 0.3 : -0.3, -0.1);
                        Vector direction = toward(kit, start);
                        if (which > 0) {
                            direction.add(axes[1].clone().multiply(random.nextDouble(-0.07, 0.07))).add(axes[2].clone().multiply(random.nextDouble(-0.05, 0.05))).normalize();
                        }
                        kit.arrow(start, direction, rate, speed * 1.1, null);
                    });
                }
            }
            case 3 -> {
                // a piercing arrow: everything along the line takes the hit
                Location start = muzzle(kit, 0.0, -0.1);
                Vector direction = toward(kit, start);
                double range = 14.0;
                Location end = start.clone().add(direction.clone().multiply(range));
                kit.sound(start, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.8f);
                kit.sound(start, Sound.ITEM_CROSSBOW_SHOOT, 0.6f, 0.7f);
                kit.line(start, end, 0.3, 1.0, 1.2);
                kit.line(start.clone().add(0.0, 0.05, 0.0), end.clone().add(0.0, 0.05, 0.0), 0.7, 1.0, 0.9);
                for (int ring = 0; ring < 5; ring++) {
                    Location at = start.clone().add(direction.clone().multiply(2.0 + ring * 2.6));
                    kit.fx(at).ring(at, axes[1], axes[2], 0.5 + 0.05 * ring, 14, kit.palette(), 0.5, 0.4, 1.0);
                }
                for (LivingEntity target : kit.corridor(start, direction, range, 0.9)) kit.hit(target, rate);
            }
            case 4 -> {
                // a fan of five arrows
                Location start = muzzle(kit, 0.0, -0.1);
                for (int index = 0; index < hits; index++) {
                    double turn = Math.toRadians((index - 2) * 10.0);
                    Vector direction = axes[0].clone().multiply(Math.cos(turn)).add(axes[1].clone().multiply(Math.sin(turn))).normalize();
                    kit.later(index / 3, () -> kit.arrow(start.clone(), direction, rate, speed, null));
                }
            }
            default -> {
                // the finisher: a great arrow that bursts where it ends
                Location start = muzzle(kit, 0.0, -0.1);
                kit.sound(start, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.7f);
                kit.arrow(start, toward(kit, start), rate, speed * 0.9, end -> {
                    kit.burst(end, 2.6);
                    kit.shockwave(end, 0.8, 2.6, 3, 1.0);
                    kit.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
                    kit.hitAll(kit.around(end, 2.4), rate * 0.5);
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.25));
        double[] boost = kit.rangeBoost();
        double rate = step.damageMultiplier() * (step.heavyIndex() == 0 ? 1.0 : boost[0]);
        Location feet = kit.player.getLocation();
        Vector[] axes = kit.axes();
        switch (step.heavyIndex()) {
            case 0 -> {
                // an arrow into the ground at the aim: a trap that waits for 2.7 s and goes off when an enemy comes within 1.8 blocks
                Location start = muzzle(kit, 0.0, -0.1);
                Location aim = kit.ground(kit.aim());
                boolean[] set = {false};
                kit.arrow(start, aim.toVector().add(new Vector(0.0, 0.3, 0.0)).subtract(start.toVector()).normalize(), 0.0, 2.0, end -> {
                    Location trap = kit.ground(end);
                    kit.sound(trap, Sound.BLOCK_TRIPWIRE_ATTACH, 0.9f, 0.7f);
                    kit.zone("ranger_trap", trap, 55, 3, zone -> {
                        kit.fx(trap).flatRing(trap.clone().add(0.0, 0.1, 0.0), 1.7, 28, kit.palette(), 0.4, 0.4, 0.9);
                        kit.fx(trap).sphereShell(trap.clone().add(0.0, 0.35, 0.0), 0.3, zone.age() * 0.2, 14, kit.palette(), 0.9);
                        if (!kit.around(trap, 1.8).isEmpty()) {
                            set[0] = true;
                            zone.stop();
                        }
                    }, zone -> {
                        if (!set[0]) return;
                        kit.burst(trap.clone().add(0.0, 0.3, 0.0), 3.0);
                        kit.shockwave(trap, 1.0, 3.0, 4, 1.0);
                        kit.sound(trap, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
                        for (LivingEntity victim : kit.around(trap, 3.0)) {
                            if (kit.hit(victim, rate)) kit.slow(victim, 50.0, 2.0);
                        }
                    });
                });
                kit.hopBack(0.6, 0.25);
            }
            case 1 -> {
                Location start = muzzle(kit, 0.0, -0.1);
                kit.sound(start, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.1f);
                kit.ricochet(start, toward(kit, start), 2, 2.0, 40, rate, end -> kit.burst(end, 1.2));
            }
            case 2 -> {
                // three spike traps round the aim
                Location aim = kit.ground(kit.aim());
                boolean[] used = new boolean[3];
                kit.sound(feet, Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.2f);
                Location[] spots = new Location[3];
                for (int index = 0; index < 3; index++) {
                    double angle = Math.PI * 2.0 * index / 3.0 + 0.5;
                    spots[index] = kit.ground(aim.clone().add(Math.cos(angle) * 2.0, 0.0, Math.sin(angle) * 2.0));
                }
                kit.zone("ranger_spikes", aim, 55, 3, zone -> {
                    for (int index = 0; index < 3; index++) {
                        if (used[index]) continue;
                        Location spot = spots[index];
                        kit.fx(spot).flatRing(spot.clone().add(0.0, 0.1, 0.0), 1.1, 20, kit.palette(), 0.4, 0.4, 0.9);
                        if (kit.around(spot, 1.3).isEmpty()) continue;
                        used[index] = true;
                        kit.burst(spot.clone().add(0.0, 0.3, 0.0), 1.6);
                        kit.sound(spot, Sound.ENTITY_ARROW_HIT, 0.9f, 0.8f);
                        for (LivingEntity victim : kit.around(spot, 1.5)) {
                            if (kit.hit(victim, rate)) kit.lift(victim, 0.45);
                        }
                    }
                }, null);
            }
            case 3 -> {
                kit.sound(feet, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.3f);
                for (int index = 0; index < 4; index++) {
                    double side = (index - 1.5) * 0.7;
                    Location start = muzzle(kit, side, 0.1);
                    Vector heading = axes[0].clone().add(axes[1].clone().multiply(side * 0.5)).add(new Vector(0.0, 0.25, 0.0));
                    kit.later(index, () -> kit.homing(start, heading, 1.3, 45, rate, 16.0, end -> kit.burst(end, 1.0)));
                }
            }
            default -> {
                // arrows fall on the aim from the sky
                Location aim = kit.ground(kit.aim());
                ThreadLocalRandom random = ThreadLocalRandom.current();
                kit.sound(feet, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.7f);
                kit.sound(aim, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.3f);
                for (int index = 0; index < 12; index++) {
                    double angle = random.nextDouble(Math.PI * 2.0);
                    double distance = Math.sqrt(random.nextDouble()) * 4.0;
                    Location spot = kit.ground(aim.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance));
                    Location from = spot.clone().add(random.nextDouble(-1.5, 1.5), 9.0, random.nextDouble(-1.5, 1.5));
                    kit.later(index, () -> {
                        kit.line(from, spot, 0.2, 1.0, 1.1);
                        kit.burst(spot.clone().add(0.0, 0.2, 0.0), 0.9);
                        kit.sound(spot, Sound.ENTITY_ARROW_HIT, 0.4f, 1.3f);
                        kit.hitAll(kit.around(spot, 1.6), rate);
                    });
                }
            }
        }
    }
}
