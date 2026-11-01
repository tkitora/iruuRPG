package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The mage's combo with a deployed weapon (a lantern of runes): conjured constructs that are set down at the placement of the weapon (see {@link DeployHold}) and stay as long
 * as it does, each in its own way. Normal: 1 an arcane spire that shoots the nearest enemy (20% a shot), 2 two small spires (10% a shot each), 3 a magic circle that
 * erupts again and again (60% an eruption, every second), 4 three arcane lanterns in a row that pulse (18% a pulse), 5 a star beacon that calls stars down round it
 * (30% a star). Heavy: 0 a summoning circle under the feet (25% a pulse), 1 three circles one after the other (40% each), 2 a lantern that pulls in and bursts (130%, slows),
 * 3 four seeking orbs (35% x4), 4 a black hole for 1 s that pulls and hurts (12% x5) and bursts (90%).
 */
public final class MageDeployCombo extends BaseCombo {

    static final String PLAN = "mage_deploy";

    public MageDeployCombo() {
        super("mage", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.2, 0.1, 0.6, 0.18, 0.3}, new int[]{1, 1, 1, 1, 1},
                new double[]{0.25, 0.4, 1.3, 0.35, 0.12, 0.15}, new int[]{140, 0, 0, 0, 0, 0}, 40));
    }

    /** An arcane spire standing at {@code at}: it hangs a ball of light over a crystal and shoots the nearest enemy within 10 blocks every 8 ticks, as long as the placement lasts. */
    private void spire(MoveKit kit, String key, Location at, double rate) {
        kit.sound(at, Sfx.MAGIC, 0.8f, 1.6f);
        kit.zone(key, at, kit.linger(50), 2, zone -> {
            double bob = Math.sin(zone.age() * 0.25) * 0.2;
            Location orb = at.clone().add(0.0, 1.9 + bob, 0.0);
            for (double y = 0.0; y <= 1.5; y += 0.3) kit.fx(at).dust(at.getX(), at.getY() + y, at.getZ(), kit.palette(), 0.3 + 0.5 * y / 1.5, 1.1);
            kit.fx(orb).sphereShell(orb, 0.3, zone.age() * 0.4, 14, kit.palette(), 1.1);
            if (zone.age() % 8 != 4) return;
            LivingEntity best = null;
            double distance = 100.0;
            for (LivingEntity candidate : kit.around(at, 10.0)) {
                double d = candidate.getLocation().distanceSquared(at);
                if (d < distance) {
                    distance = d;
                    best = candidate;
                }
            }
            if (best == null) return;
            Location to = best.getLocation().add(0.0, best.getHeight() * 0.5, 0.0);
            kit.line(orb, to, 0.2, 1.0, 1.1);
            kit.hit(best, rate);
            kit.sound(orb, Sfx.MAGIC, 0.4f, 1.8f);
        }, zone -> kit.shockwave(at, 0.5, 1.6, 2, 0.8));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.6));
        double rate = step.damageMultiplier();
        Location target = ComboParts.target(kit);
        Vector right = kit.originRight();
        switch (step.stage()) {
            case 1 -> {
                ComboParts.appear(kit, target);
                kit.later(2L, () -> spire(kit, "mage_spire_1", target, rate));
            }
            case 2 -> {
                for (int index = 0; index < 2; index++) {
                    Location spot = kit.ground(target.clone().add(right.clone().multiply(index == 0 ? -2.0 : 2.0)));
                    String key = "mage_spire_2_" + index;
                    kit.later(index, () -> spire(kit, key, spot, rate));
                }
            }
            case 3 -> {
                // a magic circle on the ground at the aim: it draws itself in, and erupts again every second
                kit.sound(target, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.8f);
                kit.zone("mage_circle", target, kit.linger(50), 2, zone -> {
                    int cycle = zone.age() % 20;
                    double r = 3.0 - 1.6 * cycle / 19.0;
                    kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), r, 48, kit.palette(), 0.4, 0.4, 1.0);
                    kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), r * 0.55, 28, kit.palette(), 0.8, 0.3, 1.0);
                    if (cycle != 18) return;
                    kit.pillar(target, 1.6, 5.0);
                    kit.burst(target.clone().add(0.0, 0.4, 0.0), 3.2);
                    kit.sound(target, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.8f);
                    for (LivingEntity victim : kit.around(target, 3.2)) {
                        if (kit.hit(victim, rate)) kit.slow(victim, 40.0, 2.0);
                    }
                }, zone -> kit.shockwave(target, 1.0, 3.2, 3, 1.0));
            }
            case 4 -> {
                Vector line = target.toVector().subtract(kit.origin().toVector()).setY(0.0);
                if (line.lengthSquared() < 1.0) line = kit.originFacing().multiply(6.0);
                for (int index = 0; index < 3; index++) {
                    Location ground = kit.ground(kit.origin().add(line.clone().multiply(0.3 + 0.35 * index)));
                    String key = "mage_lantern_" + index;
                    kit.later(index * 2L, () -> ComboParts.placeAt(kit, key, ground, 44, 14, 2.0, rate, 0.0, 0.0,
                            here -> kit.fx(here).sphereShell(here.clone().add(0.0, 1.3, 0.0), 0.3, 0.0, 12, kit.palette(), 1.0)));
                }
            }
            default -> {
                // a star beacon: stars are called down round it
                kit.sound(target, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 0.8f);
                ComboParts.rainBeacon(kit, "mage_stars", target, 50, 8, 4.0, rate, Sfx.MAGIC);
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.7));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
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
            case 4 -> {
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
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a gate of stars opens over the aim and twelve stars fall round it (3 blocks) one after the other. */
    private void sixth(MoveKit kit, double rate) {
        Location target = ComboParts.target(kit);
        Location gate = target.clone().add(0.0, 5.0, 0.0);
        kit.fx(gate).flatRing(gate, 2.0, 30, kit.palette(), 0.9, 0.1, 1.2);
        kit.sound(target, Sfx.MAGIC, 0.9f, 1.2f);
        ComboParts.rain(kit, target, 3.0, 12, rate, 1.4, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST);
    }
}
