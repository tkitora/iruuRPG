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
 * The cleric's combo with a melee weapon (a mace and a shield): the second tank (t168, {@link ClericSkills}). Blunt blows and the shield; the
 * finisher spends the radiance the cleric built up by taking blows: it stuns and heals more for each stack. Normal: 1 an overhead smash (55%), 2 a
 * punch of the shield that throws back (45%), 3 two swings from the side (40% x2, 0.5% life back each), 4 two rising blows (35% x2, the second
 * lifts), 5 the blow of judgment round the spot ahead (110%, {@code finisher-stack-rate} 15% more and a stun of {@code finisher-stun} 0.4 s +
 * {@code finisher-stack-stun} 0.15 s for each stack of radiance, which it spends; heals 2% + 1% for each).
 *
 * <p>The skills are the cleric's own ({@link ClericSkills}); the heavy attacks below are what they were before t168.
 */
public final class ClericMeleeCombo implements ClassCombo {

    static final String PLAN = "cleric_melee";

    private static final ComboSpec SPEC = new ComboSpec(PLAN,
            new double[]{0.55, 0.45, 0.4, 0.35, 1.1}, new int[]{1, 1, 2, 2, 1},
            new double[]{0.13, 0.77, 0.12, 0.85, 0.68, 1.2}, new int[]{160, 0, 0, 0, 0, 0}, 40);

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
        ClericSkills.lookHoly(kit);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                // the mace comes down ahead, and a ring of light spreads from where it lands
                Location hit = kit.ground(feet.clone().add(facing.clone().multiply(2.2)));
                kit.slash("drop", 2.4, 100.0, 5.0, 0.0, 1.9, 0.3, 0.0, 1.15, 0.12, 0.0);
                kit.sound(feet, Sfx.HOLY_SWING, 0.8f, 1.0f);
                kit.later(2L, () -> {
                    kit.shockwave(hit, 0.5, 2.4, 3, 1.0);
                    kit.sound(hit, Sfx.HOLY_BASH, 0.6f, 1.3f);
                    for (LivingEntity target : kit.around(hit, 2.4)) {
                        if (kit.hit(target, rate)) kit.push(target, feet.toVector(), 0.15, 0.0);
                    }
                });
            }
            case 2 -> {
                // a punch of the shield: square, and it throws back
                Location front = feet.clone().add(0.0, 1.0, 0.0).add(facing.clone().multiply(1.3));
                kit.fx(front).disc(front, new Vector(-facing.getZ(), 0.0, facing.getX()), new Vector(0.0, 1.0, 0.0), 0.8, 30, ClericSkills.HOLY, 1.0, 1.0);
                kit.slash("line", 2.2, 80.0, 0.0, 0.0, 1.5, 0.2, 0.0, 1.1, 0.1, 0.0);
                kit.sound(feet, Sfx.HOLY_BASH, 0.9f, 1.0f);
                for (LivingEntity target : kit.cone(3.0, 45.0)) {
                    if (kit.hit(target, rate)) kit.push(target, feet.toVector(), 0.5, 0.12);
                }
            }
            case 3 -> {
                // two swings from the side; each that lands gives a little life back
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 4L, () -> {
                        kit.slash("arc", 3.0, 150.0, which % 2 == 0 ? 15.0 : -15.0, which % 2 == 0 ? 0.0 : 180.0, 1.4, 0.4, 0.0, 1.2, 0.11, 0.0);
                        kit.sound(kit.player.getLocation(), Sfx.HOLY_SWING, 0.7f, 1.1f + 0.1f * which);
                        if (kit.hitAll(kit.cone(3.6, 80.0), rate) > 0) kit.heal(kit.number(PLAN, "swing-heal", 0.5));
                    });
                }
            }
            case 4 -> {
                // two rising blows; the second lifts
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 4L, () -> {
                        kit.slash("arc", 2.6, 110.0, -60.0, which % 2 == 0 ? 0.0 : 180.0, 1.5, 0.6, 0.0, 1.15, 0.1, 0.0);
                        kit.sound(kit.player.getLocation(), Sfx.HOLY_SWING, 0.7f, 1.3f);
                        for (LivingEntity target : kit.cone(3.0, 50.0)) {
                            if (kit.hit(target, rate) && which == hits - 1) kit.lift(target, 0.3);
                        }
                    });
                }
            }
            default -> {
                // the blow of judgment: the radiance gathered is spent on it (more damage, a stun, a heal)
                int stacks = ClericSkills.spendRadiance(kit.player);
                double boosted = rate * (1.0 + kit.number(PLAN, "finisher-stack-rate", 0.15) * stacks);
                double stunned = stacks > 0 ? kit.number(PLAN, "finisher-stun", 0.4) + kit.number(PLAN, "finisher-stack-stun", 0.15) * stacks : 0.0;
                Location hit = kit.ground(feet.clone().add(facing.clone().multiply(1.8)));
                kit.slash("drop", 3.0, 140.0, 5.0, 0.0, 1.8, 0.4, 0.0, 1.5, 0.16, 0.0);
                kit.sound(feet, Sfx.HOLY_HAMMER, 0.9f, 1.1f - 0.03f * stacks);
                kit.shockwave(hit, 0.6, 3.2, 5, 1.2);
                ClericSkills.lightPillar(kit, hit, 0.9, 2.0 + 0.5 * stacks);
                for (LivingEntity target : kit.around(hit, 3.0)) {
                    if (!kit.hit(target, boosted)) continue;
                    kit.push(target, hit.toVector(), 0.3, 0.15);
                    ClericSkills.stun(kit, target, stunned);
                }
                kit.heal(kit.number(PLAN, "finisher-heal", 2.0) + kit.number(PLAN, "finisher-stack-heal", 1.0) * stacks);
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
                // a shield bash: a shield of light strikes the enemies just ahead, and slows them for a moment
                Vector facing = kit.facing().clone().setY(0.0);
                if (facing.lengthSquared() < 1.0E-6) return;
                facing.normalize();
                kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.9f);
                kit.slash("line", 2.6, 100.0, 0.0, 0.0, 1.7, 0.2, 0.0, 1.2, 0.12, 0.0);
                Location shield = feet.clone().add(facing.clone().multiply(1.4)).add(0.0, 1.1, 0.0);
                kit.fx(shield).disc(shield, new Vector(-facing.getZ(), 0.0, facing.getX()), new Vector(0, 1, 0), 0.9, 30, kit.palette(), 1.4, 0.9);
                for (LivingEntity target : kit.cone(3.2, 60.0)) {
                    if (kit.hit(target, rate)) kit.slow(target, 50.0, 0.8);
                }
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
            case 4 -> {
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
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a hammer of light falls on the enemy ahead (2.5 blocks round it are struck), and the allies near the cleric are healed 3%. */
    private void sixth(MoveKit kit, double rate) {
        org.bukkit.entity.LivingEntity target = kit.nearest(5.0, 50.0);
        Location at = target != null ? target.getLocation() : kit.ground(kit.player.getLocation().add(kit.facing().clone().setY(0.0).multiply(2.5)));
        kit.sound(at, Sfx.MAGIC, 1.0f, 0.7f);
        kit.pillar(at, 0.9, 5.0);
        kit.later(4L, () -> {
            kit.shockwave(at, 0.5, 2.5, 4, 1.1);
            kit.sound(at, Sfx.METAL_HEAVY, 0.9f, 1.2f);
            for (org.bukkit.entity.LivingEntity victim : kit.around(at, 2.5)) kit.hit(victim, rate);
            kit.healAllies(kit.player.getLocation(), 6.0, 3.0);
        });
    }
}
