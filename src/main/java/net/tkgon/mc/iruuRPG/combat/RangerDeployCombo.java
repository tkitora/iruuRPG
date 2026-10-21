package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The ranger's combo with a deployed weapon (the lantern): the ranger fights with many hands and with things it places. The
 * lantern is thrown and bursts. Normal: 1 a lobbed lantern (100%), 2 two lanterns (60% x2), 3 a mine that waits for 2.7 s and goes off when
 * an enemy comes close (130%, slows), 4 three lanterns in a row to the aim (40% x3), 5 a rain of five lanterns (30% x5). Heavy: 0 a sigil
 * on the ground for 2.5 s (25% a pulse), 1 a lantern that bounces three times (40% x3), 2 a wall of five lanterns for 2.5 s
 * (25% a pulse), 3 four homing lanterns (35% x4), 4 a great lantern that pulls enemies in for a second and bursts (150%).
 * A deployed weapon's damage powers up the ranger's melee attacks (the loop).
 */
public final class RangerDeployCombo implements ClassCombo {

    static final String PLAN = "ranger_deploy";
    private static final double REACH = 11.0;

    private static final ComboSpec SPEC = new ComboSpec(PLAN,
            new double[]{1.0, 0.6, 1.3, 0.4, 0.3}, new int[]{1, 2, 1, 3, 5},
            new double[]{0.25, 0.4, 0.25, 0.35, 1.5}, new int[]{120, 0, 0, 0, 0}, 40);

    @Override
    public String classId() {
        return "gifted";
    }

    @Override
    public AttackType type() {
        return AttackType.DEPLOY;
    }

    @Override
    public ComboSpec spec() {
        return SPEC;
    }

    /** Where the player aims, no further than {@code REACH} blocks away, on the ground. */
    private Location target(MoveKit kit) {
        Location aim = kit.aim();
        Location from = kit.player.getLocation();
        Vector away = aim.toVector().subtract(from.toVector());
        if (away.length() > REACH) aim = from.clone().add(away.normalize().multiply(REACH));
        return kit.ground(aim);
    }

    private Location hand(MoveKit kit) {
        return kit.eye().add(kit.axes()[0].clone().multiply(0.6)).subtract(0.0, 0.3, 0.0);
    }

    private void lantern(MoveKit kit, Location at, double size) {
        kit.fx(at).sphereShell(at, size, 0.0, 22, kit.palette(), 1.0);
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.3));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location target = target(kit);
        Vector right = kit.axes()[1];
        switch (step.stage()) {
            case 1 -> kit.lob(hand(kit), target, 8, 2.0, land -> {
                kit.burst(land.clone().add(0.0, 0.3, 0.0), 3.0);
                kit.sound(land, Sfx.CRACK, 0.8f, 1.3f);
                kit.sound(land, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.6f);
                kit.hitAll(kit.around(land, 3.0), rate);
            });
            case 2 -> {
                for (int index = 0; index < hits; index++) {
                    Location spot = target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0));
                    kit.later(index, () -> kit.lob(hand(kit), spot, 8, 1.8, land -> {
                        kit.burst(land.clone().add(0.0, 0.3, 0.0), 2.5);
                        kit.sound(land, Sfx.CRACK, 0.7f, 1.5f);
                        kit.hitAll(kit.around(land, 2.5), rate);
                    }));
                }
            }
            case 3 -> {
                // a mine: it waits, and goes off when an enemy comes within 2 blocks (or fizzles)
                boolean[] set = {false};
                kit.lob(hand(kit), target, 8, 1.5, land -> {
                    Location mine = land.clone();
                    kit.sound(mine, Sound.BLOCK_TRIPWIRE_ATTACH, 0.9f, 0.8f);
                    kit.zone("ranger_mine", mine, 55, 3, zone -> {
                        lantern(kit, mine.clone().add(0.0, 0.4, 0.0), 0.3);
                        kit.fx(mine).flatRing(mine.clone().add(0.0, 0.1, 0.0), 1.9, 28, kit.palette(), 0.3, 0.3, 0.9);
                        if (!kit.around(mine, 2.0).isEmpty()) {
                            set[0] = true;
                            zone.stop();
                        }
                    }, zone -> {
                        if (!set[0]) {
                            kit.fx(mine).particle(org.bukkit.Particle.SMOKE_NORMAL, mine.getX(), mine.getY() + 0.3, mine.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
                            return;
                        }
                        kit.burst(mine.clone().add(0.0, 0.3, 0.0), 3.5);
                        kit.shockwave(mine, 1.0, 3.5, 4, 1.0);
                        kit.sound(mine, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                        for (LivingEntity victim : kit.around(mine, 3.5)) {
                            if (kit.hit(victim, rate)) kit.slow(victim, 40.0, 2.0);
                        }
                    });
                });
            }
            case 4 -> {
                // three lanterns in a row from the player to the aim, one after the other
                Vector line = target.toVector().subtract(kit.player.getLocation().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.facing().multiply(6.0);
                for (int index = 0; index < hits; index++) {
                    Location spot = kit.player.getLocation().add(line.clone().multiply(0.3 + 0.35 * index));
                    Location ground = kit.ground(spot);
                    kit.later(index * 2L, () -> {
                        lantern(kit, ground.clone().add(0.0, 0.5, 0.0), 0.4);
                        kit.burst(ground.clone().add(0.0, 0.3, 0.0), 1.8);
                        kit.sound(ground, Sfx.CRACK, 0.6f, 1.7f);
                        kit.hitAll(kit.around(ground, 1.9), rate);
                    });
                }
            }
            default -> {
                // the finisher: lanterns rain on the aim
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int index = 0; index < hits; index++) {
                    double angle = random.nextDouble(Math.PI * 2.0);
                    double distance = Math.sqrt(random.nextDouble()) * 3.6;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance));
                    Location from = spot.clone().add(0.0, 8.0, 0.0);
                    kit.later(index * 2L, () -> kit.lob(from, spot, 5, 0.0, land -> {
                        kit.burst(land.clone().add(0.0, 0.3, 0.0), 2.0);
                        kit.sound(land, Sfx.CRACK, 0.6f, 1.2f);
                        kit.hitAll(kit.around(land, 2.0), rate);
                    }));
                }
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Location target = target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                // a sigil on the ground under the feet for 2.5 seconds
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
                kit.zone("ranger_sigil", at, 50, 10, zone -> {
                    kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), 3.5, 56, kit.palette(), 0.5, 0.5, 1.0);
                    kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), 2.2, 36, kit.palette(), 0.8, 0.4, 1.0);
                    kit.hitAll(kit.around(at, 3.5), rate);
                }, zone -> kit.shockwave(at, 3.0, 3.8, 3, 0.9));
            }
            case 1 -> {
                // a lantern that bounces three times along the ground
                Vector toward = target.toVector().subtract(feet.toVector()).setY(0.0);
                Vector facing = toward.lengthSquared() < 1.0 ? kit.facing() : toward.normalize();
                Location from = hand(kit);
                for (int bounce = 0; bounce < 3; bounce++) {
                    Location landing = kit.ground(feet.clone().add(facing.clone().multiply(3.0 + 3.0 * bounce)));
                    Location start = bounce == 0 ? from : kit.ground(feet.clone().add(facing.clone().multiply(3.0 * bounce)));
                    kit.later(bounce * 7L, () -> kit.lob(start, landing, 6, 1.5, land -> {
                        kit.burst(land.clone().add(0.0, 0.3, 0.0), 2.0);
                        kit.sound(land, Sfx.CRACK, 0.7f, 1.0f + 0.2f * (float) (land.distance(feet) / 6.0));
                        kit.hitAll(kit.around(land, 2.2), rate);
                    }));
                }
            }
            case 2 -> {
                // a wall of five lanterns across the aim for 2.5 seconds
                Vector forward = kit.facing();
                Vector side = new Vector(-forward.getZ(), 0.0, forward.getX());
                Location middle = kit.ground(feet.clone().add(forward.clone().multiply(4.5)));
                kit.sound(feet, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);
                kit.zone("ranger_wall", middle, 50, 8, zone -> {
                    for (int index = -2; index <= 2; index++) {
                        Location at = middle.clone().add(side.clone().multiply(index * 1.6)).add(0.0, 0.6, 0.0);
                        lantern(kit, at, 0.4);
                        kit.fx(at).dust(at.getX(), at.getY() + 0.6, at.getZ(), kit.palette(), 0.9, 1.0);
                        kit.hitAll(kit.around(at, 1.5), rate);
                    }
                }, zone -> kit.shockwave(middle, 1.0, 2.5, 3, 0.8));
            }
            case 3 -> {
                // four lanterns that seek the enemies
                Vector[] axes = kit.axes();
                for (int index = 0; index < 4; index++) {
                    double side = (index - 1.5) * 0.9;
                    Location start = hand(kit).add(axes[1].clone().multiply(side));
                    Vector heading = axes[0].clone().add(axes[1].clone().multiply(side * 0.4)).add(new Vector(0.0, 0.3, 0.0));
                    kit.later(index, () -> kit.homing(start, heading, 1.2, 40, rate, 14.0, end -> kit.burst(end, 1.4)));
                }
                kit.sound(feet, Sfx.MAGIC_DARK, 0.8f, 1.6f);
            }
            default -> {
                // a great lantern: it swells for a second and pulls the enemies in, then bursts
                Location at = target.clone().add(0.0, 1.5, 0.0);
                kit.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
                kit.every(21, age -> {
                    double radius = 0.5 + 1.5 * age / 20.0;
                    kit.fx(at).sphereShell(at, radius, age * 0.3, 32, kit.palette(), 1.1);
                    if (age % 3 == 0) {
                        for (LivingEntity victim : kit.around(at, 6.0)) kit.pull(victim, at, 0.18);
                    }
                    if (age == 20) {
                        kit.burst(at, 5.0);
                        kit.shockwave(target, 1.0, 5.0, 5, 1.2);
                        kit.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
                        kit.sound(at, Sfx.CRACK, 1.0f, 0.6f);
                        for (LivingEntity victim : kit.around(at, 5.0)) {
                            if (kit.hit(victim, rate)) kit.lift(victim, 0.5);
                        }
                    }
                    return false;
                });
            }
        }
    }
}
