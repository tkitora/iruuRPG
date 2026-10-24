package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * The cleric's combo with a melee weapon (a mace): prayer and judgment. Blows that also heal a little (a heal makes the
 * cleric's pulse hit the enemies around). Normal: 1 an overhead smash (68%), 2 two quick cuts (38% x2), 3 a holy ring around
 * (72%, heals 2%), 4 a thrust of light (38% x2), 5 a leaping slam (94%, heals 5%). Heavy: 0 a sanctuary under the feet for 2.5 s (13%
 * a pulse, heals 1% a pulse), 1 a hammer wave ahead (77%, it does not push), 2 a spinning prayer (about 85%, heals 3%), 3 a pillar of light at the aim (85%),
 * 4 a wide sanctuary ring (68%, slows 40%, heals 6%). The blows push little (the cleric's damage is not meant to come from knocking enemies about). All numbers are 15% under
 * the earlier ones.
 */
public final class ClericMeleeCombo implements ClassCombo {

    static final String PLAN = "cleric_melee";

    private static final ComboSpec SPEC = new ComboSpec(PLAN,
            new double[]{0.46, 0.3, 0.77, 0.46, 1.08}, new int[]{1, 2, 1, 2, 1},
            new double[]{0.13, 0.77, 0.12, 0.85, 0.68}, new int[]{160, 0, 0, 0, 0}, 40);

    @Override
    public String classId() {
        return "healer";
    }

    @Override
    public AttackType type() {
        return AttackType.MELEE;
    }

    @Override
    public ComboSpec spec() {
        return SPEC;
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.35));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        switch (step.stage()) {
            case 1 -> {
                // a mace smash: the head comes down in front and a ring of light spreads from where it lands (a blunt blow on the ground, not a sweep through the air)
                Location hit = kit.ground(feet.clone().add(kit.facing().multiply(2.3)));
                kit.slash("drop", 2.4, 100.0, 5.0, 0.0, 1.9, 0.3, 0.0, 1.1, 0.12, 0.0);
                kit.sound(feet, Sfx.METAL, 1.0f, 0.8f);
                kit.later(2L, () -> {
                    kit.shockwave(hit, 0.5, 2.4, 3, 1.0);
                    kit.sound(hit, Sound.BLOCK_BELL_USE, 0.7f, 1.3f);
                    for (LivingEntity target : kit.around(hit, 2.4)) {
                        if (kit.hit(target, rate)) kit.push(target, feet.toVector(), 0.15, 0.0);
                    }
                });
            }
            case 2 -> {
                // two tolls: a pulse of light runs out ahead each time, hitting everything on its way
                Vector facing = kit.facing();
                Location from = kit.ground(feet).add(0.0, 0.4, 0.0);
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 4L, () -> {
                        kit.sound(feet, Sound.BLOCK_BELL_RESONATE, 0.7f, 1.4f + which * 0.2f);
                        for (int ring = 0; ring < 4; ring++) {
                            Location at = from.clone().add(facing.clone().multiply(1.2 + ring * 1.1));
                            kit.later(ring, () -> kit.shockwave(at, 0.3, 1.3, 2, 0.9));
                        }
                        kit.hitAll(kit.corridor(from, facing, 5.0, 1.3), rate);
                    });
                }
            }
            case 3 -> {
                kit.shockwave(feet, 1.0, 3.6, 4, 1.0);
                kit.sound(feet, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
                kit.sound(feet, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                kit.hitAll(kit.around(feet, 3.6), rate);
                kit.heal(2.0);
            }
            case 4 -> {
                Vector facing = kit.facing();
                Location from = kit.eye().add(facing.clone().multiply(0.8)).subtract(0.0, 0.4, 0.0);
                for (int index = 0; index < hits; index++) {
                    kit.later(index * 2L, () -> {
                        kit.line(from, from.clone().add(facing.clone().multiply(5.2)), 0.2, 1.0, 1.4);
                        kit.fx(from.clone().add(facing.clone().multiply(5.2))).particle(Particle.CRIT_MAGIC, from.getX() + facing.getX() * 5.2, from.getY(), from.getZ() + facing.getZ() * 5.2, 6, 0.3, 0.3, 0.3, 0.2);
                        kit.sound(feet, Sfx.WIND_BURST, 0.7f, 1.5f);
                        for (LivingEntity target : kit.corridor(from, facing, 5.2, 1.1)) {
                            kit.hit(target, rate);
                        }
                    });
                }
            }
            default -> {
                // the finisher: up and down on the spot, the blow lands with a ring of light
                kit.leap(kit.facing(), 0.1, 0.75, land -> {
                    kit.shockwave(land, 1.0, 4.2, 5, 1.2);
                    kit.slash("drop", 3.0, 150.0, 5.0, 0.0, 1.8, 0.4, 0.0, 1.4, 0.16, 0.0);
                    kit.sound(land, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
                    kit.sound(land, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.9f);
                    for (LivingEntity target : kit.around(land, 4.2)) {
                        if (kit.hit(target, rate)) kit.push(target, land.toVector(), 0.4, 0.15);
                    }
                    kit.heal(5.0);
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.45));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a sanctuary under the feet: it heals a little and hurts what stands in it, for 2.5 seconds
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
                kit.zone("cleric_sanctuary", at, 50, 10, zone -> {
                    kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), 3.5, 56, kit.palette(), 0.5 + 0.4 * (1.0 - zone.progress()), 0.4, 1.0);
                    kit.fx(at).disc(at.clone().add(0.0, 0.1, 0.0), new Vector(1, 0, 0), new Vector(0, 0, 1), 3.4, 40, kit.palette(), 1.5, 0.9);
                    kit.hitAll(kit.around(at, 3.5), rate);
                    kit.heal(1.0);
                }, zone -> kit.shockwave(at, 3.0, 3.8, 3, 0.9));
            }
            case 1 -> {
                // a hammer wave: rings of light run ahead one after the other
                Vector facing = kit.facing();
                kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.7f);
                kit.slash("line", 3.0, 100.0, 0.0, 0.0, 1.8, 0.2, 0.0, 1.3, 0.14, 0.0);
                for (int index = 1; index <= 6; index++) {
                    double along = index;
                    kit.later(index - 1, () -> {
                        Location at = feet.clone().add(facing.clone().multiply(along)).add(0.0, 0.3, 0.0);
                        kit.fx(at).flatRing(at, 0.9 + along * 0.12, 20, kit.palette(), 0.4 + 0.1 * along, 0.3, 1.1);
                    });
                }
                // (the hammer wave no longer throws what it hits)
                kit.hitAll(kit.corridor(feet.clone().add(0.0, 0.5, 0.0), facing, 6.0, 1.5), rate);
            }
            case 2 -> {
                // a spinning prayer: arcs of light turn round the cleric, a hit every 3 ticks
                kit.sound(feet, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
                kit.every(21, age -> {
                    Location center = kit.player.getLocation().add(0.0, 1.0, 0.0);
                    for (int arm = 0; arm < 3; arm++) {
                        double base = age * 0.7 + arm * Math.PI * 2.0 / 3.0;
                        kit.fx(center).spiral(center, new Vector(1, 0, 0), new Vector(0, 0, 1), new Vector(0, 1, 0), 2.4, 3.4, -0.18, base - 0.9, 0.0, 9, kit.palette(), 1.2);
                    }
                    if (age % 3 == 0) {
                        kit.hitAll(kit.around(kit.player.getLocation(), 3.5), rate);
                        kit.sound(center, Sfx.WIND, 0.5f, 1.3f);
                    }
                    if (age == 20) kit.heal(3.0);
                    return false;
                });
            }
            case 3 -> {
                // a pillar of light where the cleric aims: it is shown first (a ring that closes), then it falls
                Location at = kit.ground(kit.aim());
                kit.sound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
                for (int tick = 0; tick < 10; tick++) {
                    double r = 2.6 - 1.6 * tick / 9.0;
                    kit.later(tick, () -> kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), r, 40, kit.palette(), 0.4, 0.3, 1.0));
                }
                kit.later(10, () -> {
                    kit.pillar(at, 2.0, 7.0);
                    kit.shockwave(at, 1.0, 3.0, 4, 1.1);
                    kit.sound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
                    kit.sound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.7f);
                    for (LivingEntity target : kit.around(at, 2.4)) {
                        kit.hit(target, rate);
                    }
                });
            }
            default -> {
                // a wide sanctuary ring: the enemies in it are hurt, the cleric is healed (nothing holds the enemies: the cleric's combo has no restraint)
                kit.shockwave(feet, 1.5, 5.2, 6, 1.2);
                kit.sound(feet, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
                kit.sound(feet, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.9f);
                List<LivingEntity> inside = kit.around(feet, 5.2);
                for (LivingEntity target : inside) {
                    kit.hit(target, rate);
                }
                kit.heal(6.0);
                double spin = Trig.sin(0.0);
                kit.fx(feet).sphereShell(feet.clone().add(0.0, 1.0, 0.0), 1.4, spin, 32, kit.palette(), 1.0);
            }
        }
    }
}
