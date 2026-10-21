package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The mage's combo with a deployed weapon (a lantern of runes): conjured circles and satellites. Normal: 1 an orb that circles the mage for 2.5 s and
 * shoots the nearest enemy (20% a shot), 2 two orbs that do the same (10% a shot each), 3 a magic circle at the aim that erupts after
 * a moment (130%, slows), 4 three arcane lanterns in a row (40% x3), 5 a falling star (150%). Heavy: 0 a summoning circle under the feet for
 * 2.5 s (25% a pulse), 1 three circles one after the other (40% each), 2 a lantern that pulls in and bursts (130%, slows), 3 four seeking orbs
 * (35% x4), 4 a black hole for 1 s that pulls and hurts (12% x5) and bursts (90%).
 */
public final class MageDeployCombo extends BaseCombo {

    static final String PLAN = "mage_deploy";

    public MageDeployCombo() {
        super("mage", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.2, 0.1, 1.3, 0.4, 1.5}, new int[]{6, 6, 1, 3, 1},
                new double[]{0.25, 0.4, 1.3, 0.35, 0.12}, new int[]{140, 0, 0, 0, 0}, 40));
    }

    /** An orb that circles the player for 2.5 s and shoots the nearest enemy within 10 blocks every 8 ticks. */
    private void satellite(MoveKit kit, String key, double phase, double rate) {
        Location start = kit.player.getLocation();
        kit.sound(start, Sfx.MAGIC, 0.8f, 1.6f);
        kit.zone(key, start, 50, 1, zone -> {
            double angle = phase + zone.age() * 0.28;
            Location at = kit.player.getLocation().add(Trig.cos(angle) * 1.8, 1.4 + Math.sin(zone.age() * 0.2) * 0.2, Trig.sin(angle) * 1.8);
            kit.fx(at).sphereShell(at, 0.28, zone.age() * 0.4, 14, kit.palette(), 1.0);
            if (zone.age() % 8 == 4) {
                LivingEntity best = null;
                double distance = 100.0;
                for (LivingEntity candidate : kit.around(kit.player.getLocation(), 10.0)) {
                    double d = candidate.getLocation().distanceSquared(at);
                    if (d < distance) {
                        distance = d;
                        best = candidate;
                    }
                }
                if (best != null) {
                    Location to = best.getLocation().add(0.0, best.getHeight() * 0.5, 0.0);
                    kit.line(at, to, 0.2, 1.0, 1.1);
                    kit.hit(best, rate);
                    kit.sound(at, Sfx.MAGIC, 0.4f, 1.8f);
                }
            }
        }, null);
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.6));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location target = ComboParts.target(kit);
        switch (step.stage()) {
            case 1 -> satellite(kit, "mage_orb_1", 0.0, rate);
            case 2 -> {
                satellite(kit, "mage_orb_1", 0.0, rate);
                satellite(kit, "mage_orb_2", Math.PI, rate);
            }
            case 3 -> {
                // a circle on the ground at the aim: it shows for a moment and then erupts
                kit.sound(target, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.8f);
                for (int tick = 0; tick < 10; tick++) {
                    double r = 3.0 - 1.2 * tick / 9.0;
                    kit.later(tick, () -> {
                        kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), r, 48, kit.palette(), 0.4, 0.4, 1.0);
                        kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), r * 0.55, 28, kit.palette(), 0.8, 0.3, 1.0);
                    });
                }
                kit.later(10, () -> {
                    kit.pillar(target, 1.6, 5.0);
                    kit.burst(target.clone().add(0.0, 0.4, 0.0), 3.2);
                    kit.sound(target, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.8f);
                    for (LivingEntity victim : kit.around(target, 3.2)) {
                        if (kit.hit(victim, rate)) kit.slow(victim, 40.0, 2.0);
                    }
                });
            }
            case 4 -> {
                Vector line = target.toVector().subtract(kit.player.getLocation().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.facing().multiply(6.0);
                for (int index = 0; index < hits; index++) {
                    Location ground = kit.ground(kit.player.getLocation().add(line.clone().multiply(0.3 + 0.35 * index)));
                    kit.later(index * 2L, () -> {
                        ComboParts.orb(kit, ground.clone().add(0.0, 0.5, 0.0), 0.4);
                        kit.burst(ground.clone().add(0.0, 0.3, 0.0), 1.8);
                        kit.sound(ground, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.7f);
                        kit.hitAll(kit.around(ground, 1.9), rate);
                    });
                }
            }
            default -> {
                // a star falls on the aim
                Location from = target.clone().add(4.0, 16.0, -3.0);
                kit.sound(target, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 0.8f);
                kit.lob(from, target, 9, 0.0, land -> {
                    kit.burst(land.clone().add(0.0, 0.3, 0.0), 4.0);
                    kit.shockwave(land, 1.0, 4.2, 5, 1.2);
                    kit.sound(land, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                    kit.sound(land, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.7f);
                    for (LivingEntity victim : kit.around(land, 4.2)) {
                        if (kit.hit(victim, rate)) kit.push(victim, land.toVector(), 0.6, 0.35);
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
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.6f);
                ComboParts.sigil(kit, "mage_summon", at, 3.5, rate, 50, 10, null);
            }
            case 1 -> {
                // three circles, one after another, toward the aim
                Vector toward = target.toVector().subtract(feet.toVector()).setY(0.0);
                Vector facing = toward.lengthSquared() < 1.0 ? kit.facing() : toward.normalize();
                for (int index = 0; index < 3; index++) {
                    Location at = kit.ground(feet.clone().add(facing.clone().multiply(3.0 + 2.6 * index)));
                    kit.later(index * 8L, () -> {
                        for (int tick = 0; tick < 5; tick++) {
                            double r = 2.0 - 1.0 * tick / 4.0;
                            kit.later(tick, () -> kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), r, 36, kit.palette(), 0.4, 0.3, 1.0));
                        }
                        kit.later(5, () -> {
                            kit.burst(at.clone().add(0.0, 0.3, 0.0), 2.2);
                            kit.sound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.0f);
                            kit.hitAll(kit.around(at, 2.2), rate);
                        });
                    });
                }
            }
            case 2 -> ComboParts.pullBurst(kit, target.clone().add(0.0, 1.5, 0.0), 4.5, rate, 40.0);
            case 3 -> ComboParts.homingVolley(kit, 4, rate, 14.0, end -> kit.burst(end, 1.2));
            default -> {
                // a black hole: it pulls for a second and hurts, and bursts
                Location at = target.clone().add(0.0, 1.5, 0.0);
                kit.sound(at, Sfx.MAGIC_DARK, 1.0f, 0.6f);
                kit.every(21, age -> {
                    kit.fx(at).sphereShell(at, 0.7 + 0.2 * Math.sin(age * 0.6), age * 0.5, 32, kit.palette(), 1.2);
                    for (LivingEntity victim : kit.around(at, 6.0)) kit.pull(victim, at, 0.2);
                    if (age % 4 == 0 && age >= 4 && age < 20) kit.hitAll(kit.around(at, 3.0), rate);
                    if (age == 20) {
                        kit.burst(at, 4.5);
                        kit.shockwave(target, 1.0, 4.5, 5, 1.2);
                        kit.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                        kit.hitAll(kit.around(at, 4.5), 0.9);
                    }
                    return false;
                });
            }
        }
    }
}
