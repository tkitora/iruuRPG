package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The warrior's combo with melee weapons: the blood knight (t168, {@link WarriorSkills}). Heavy blows on the front line; every hit drinks back the
 * wound the warrior took ({@link WarriorBlood}), so staying in the fight is how the warrior lasts.
 *
 * <p>Normal: 1 a cut down from the shoulder (70%, braced: 20% less damage taken for 0.5 s), 2 the
 * rising return cut (85%, pushes a little), 3 a step in and two quick cuts (55% x2), 4 two wide sweeps round the warrior (70% x2, 25% less damage
 * taken for 0.7 s), 5 a great cut brought down on a strip ahead (160%; it drinks the wound twice as fast) and a wave of blood that runs on along
 * the ground ({@code wave-share} 50% of it, {@code wave-range} 8 blocks).
 *
 * <p>The skills are the blood knight's own ({@link WarriorSkills}); the heavy attacks below are what they were before t168 (they are no longer
 * thrown by the warrior's skills).
 */
public final class WarriorMeleeCombo extends BaseCombo {

    static final String PLAN = "warrior_melee";

    public WarriorMeleeCombo() {
        super("warrior", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.7, 0.85, 0.55, 0.7, 1.6}, new int[]{1, 1, 2, 2, 1},
                new double[]{1.0, 0.6, 0.24, 1.5, 0.8, 1.2}, new int[]{360, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        WarriorSkills.lookBlood(kit);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                // a cut down from the right shoulder, the warrior braced behind it
                kit.guard(0.2, 10);
                kit.slash("drop", 3.2, 130.0, 30.0, 0.0, 1.9, 0.3, 0.0, 1.35, 0.13, 0.0);
                kit.sound(feet, Sfx.BLOOD_SWING, 0.8f, 1.0f);
                if (WarriorSkills.cutAhead(kit, 3.6, 60.0, rate, null) > 0) kit.sound(feet, Sfx.BLOOD_HIT, 0.5f, 1.1f);
            }
            case 2 -> {
                // the return: a rising cut from the left that pushes a little
                kit.slash("arc", 3.2, 150.0, -35.0, 180.0, 1.8, 0.4, 0.0, 1.35, 0.13, 0.0);
                kit.sound(feet, Sfx.BLOOD_SWING, 0.8f, 1.15f);
                for (LivingEntity target : kit.cone(3.6, 70.0)) {
                    if (!WarriorSkills.strike(kit, target, rate, null)) continue;
                    WarriorSkills.bled(kit, target, new Vector(0.0, 1.0, 0.0));
                    kit.push(target, feet.toVector(), 0.2, 0.12);
                }
            }
            case 3 -> {
                // a step in, and two quick cuts one from each side
                kit.dash(facing, 0.55, 3, end -> {
                    for (int index = 0; index < hits; index++) {
                        int which = index;
                        kit.later(index * 3L, () -> {
                            kit.slash("arc", 2.9, 120.0, which % 2 == 0 ? 20.0 : -20.0, which % 2 == 0 ? 0.0 : 180.0, 1.6, 0.35, 0.0, 1.2, 0.09, 0.0);
                            kit.sound(kit.player.getLocation(), Sfx.BLOOD_SWING, 0.6f, 1.2f + 0.1f * which);
                            WarriorSkills.cutAhead(kit, 3.4, 60.0, rate, null);
                        });
                    }
                });
            }
            case 4 -> {
                // two wide sweeps, turning: everyone round
                kit.guard(0.25, 14);
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 5L, () -> {
                        Location at = kit.player.getLocation();
                        kit.slash("arc", 3.4, 300.0, 70.0, which % 2 == 0 ? 0.0 : 180.0, 0.5, 0.5, 0.0, 1.35, 0.12, 0.0);
                        kit.sound(at, Sfx.BLOOD_SWING, 0.7f, 0.9f);
                        for (LivingEntity target : kit.around(at, 3.8)) {
                            Vector off = target.getLocation().toVector().subtract(at.toVector()).setY(0.0);
                            if (WarriorSkills.strike(kit, target, rate, null)) WarriorSkills.bled(kit, target, new Vector(-off.getZ(), 0.0, off.getX()));
                        }
                    });
                }
            }
            default -> {
                // the finisher: a great cut brought down on a strip ahead (it drinks the wound twice as fast), and a wave of blood runs on
                WarriorSkills.Blood blood = WarriorSkills.blood(kit, kit.number(PLAN, "finisher-rally", 2.0), 0.0, 0.0, 0.0);
                kit.guard(0.35, 14);
                kit.slash("drop", 3.6, 150.0, 5.0, 0.0, 2.0, 0.3, 0.0, 1.7, 0.18, 0.0);
                kit.sound(feet, Sfx.BLOOD_SLAM, 1.0f, 1.0f);
                Location from = feet.clone().add(0.0, 0.5, 0.0);
                kit.shockwave(feet.clone().add(facing.clone().multiply(2.2)), 0.6, 2.4, 4, 1.2);
                for (LivingEntity target : kit.corridor(from, facing, 4.5, 1.6)) {
                    if (WarriorSkills.strike(kit, target, rate, blood)) WarriorSkills.bled(kit, target, new Vector(0.0, 1.0, 0.0));
                }
                Location start = feet.clone().add(0.0, 0.6, 0.0).add(facing.clone().multiply(1.2));
                MageMeleeCombo.crescent(kit, start, facing, 1.3, kit.number(PLAN, "wave-range", 8.0), Math.PI / 2.0,
                        rate * kit.number(PLAN, "wave-share", 0.5), 1.4, org.bukkit.Particle.DAMAGE_INDICATOR, Sfx.BLOOD_SWING,
                        target -> WarriorSkills.bled(kit, target, facing));
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a shield bash on the enemies just ahead, and a bulwark: three shields round the warrior for 10 s, and less damage for a moment
                kit.slash("line", 2.6, 100.0, 0.0, 0.0, 1.7, 0.2, 0.0, 1.2, 0.12, 0.0);
                for (LivingEntity target : kit.cone(3.2, 60.0)) {
                    if (kit.hit(target, rate)) {
                        kit.slow(target, 60.0, 1.0);
                        kit.push(target, feet.toVector(), 0.3, 0.05);
                    }
                }
                kit.bulwark();
                kit.guard(0.4, 40);
                kit.shockwave(feet, 0.8, 3.0, 4, 1.0);
                kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.9f);
            }
            case 1 -> {
                // a stomp: the ground round shakes, the enemies are slowed, a shield is picked up
                kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.6f);
                kit.shockwave(feet, 0.8, 4.2, 5, 1.2);
                for (LivingEntity target : kit.around(feet, 4.0)) {
                    if (kit.hit(target, rate)) kit.slow(target, 40.0, 2.0);
                }
            }
            case 2 -> {
                // a whirlwind: a spin for a second; everyone round is cut every 4 ticks
                kit.guard(0.3, 22);
                kit.sound(feet, Sfx.WIND_BURST, 0.9f, 0.8f);
                kit.every(21, age -> {
                    Location at = kit.player.getLocation();
                    if (age % 4 == 0) {
                        kit.slash("arc", 3.0, 320.0, 70.0, age % 8 == 0 ? 0.0 : 180.0, 0.4, 0.5, 0.0, 1.2, 0.08, 0.0);
                        kit.sound(at, Sfx.METAL, 0.4f, 0.9f);
                        kit.hitAll(kit.around(at, 3.5), rate);
                    }
                    kit.fx(at).flatRing(at.clone().add(0.0, 0.2 + (age % 6) * 0.25, 0.0), 3.0, 40, kit.palette(), 0.4 + 0.02 * age, 0.2, 1.0);
                    if (age == 20) kit.heal(1.0);
                    return false;
                });
            }
            case 3 -> {
                // a colossus strike: a moment of winding up behind the guard, then the whole of it on the enemy ahead
                kit.guard(0.6, 12);
                kit.sound(feet, Sfx.METAL, 0.8f, 0.6f);
                for (int tick = 0; tick < 8; tick++) {
                    double r = 2.4 - 1.6 * tick / 7.0;
                    kit.later(tick, () -> kit.fx(kit.player.getLocation()).flatRing(kit.player.getLocation().add(0.0, 0.2, 0.0), r, 28, kit.palette(), 0.4 + 0.07 * (2.4 - r), 0.2, 1.0));
                }
                kit.later(8L, () -> {
                    // one great downward cut: the ground splits along a strip ahead, and every enemy on it is struck and thrown up
                    Location from = kit.player.getLocation();
                    Vector way = kit.facing().clone().setY(0.0);
                    if (way.lengthSquared() < 1.0E-6) return;
                    way.normalize();
                    kit.slash("drop", 3.8, 150.0, 5.0, 0.0, 2.1, 0.3, 0.0, 1.8, 0.22, 0.0);
                    kit.sound(from, Sfx.METAL_HEAVY, 1.0f, 0.7f);
                    for (int along = 1; along <= 4; along++) {
                        Location at = from.clone().add(way.clone().multiply(along * 1.2)).add(0.0, 0.2, 0.0);
                        kit.later(along, () -> kit.fx(at).flatRing(at, 0.8, 14, kit.palette(), 0.5, 0.2, 1.0));
                    }
                    kit.shockwave(from.clone().add(way.clone().multiply(3.0)), 0.6, 2.4, 4, 1.2);
                    for (LivingEntity target : kit.corridor(from.clone().add(0.0, 0.5, 0.0), way, 5.0, 1.6)) {
                        if (kit.hit(target, rate)) kit.lift(target, 0.45);
                    }
                });
            }
            case 4 -> {
                // a shield rush: under a guard, six blocks, everything in the way is struck (not pushed: util of the redesign has no crowd control); more with a shield up
                double boost = kit.shields() > 0 ? 1.4 : 1.0;
                Location from = feet.clone().add(0.0, 1.0, 0.0);
                kit.guard(0.5, 12);
                kit.sound(feet, Sfx.WIND_BURST, 1.0f, 0.8f);
                kit.line(from, from.clone().add(facing.clone().multiply(6.0)), 0.2, 1.0, 1.5);
                kit.hitAll(kit.corridor(from, facing, 6.5, 1.6), rate * boost);
                kit.dash(facing, 1.2, 5, end -> {
                    kit.shockwave(end, 0.8, 3.0, 4, 1.1);
                    kit.sound(end, Sfx.METAL_HEAVY, 0.9f, 0.9f);
                });
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a slam that splits the ground in three fissures fanned out ahead (-25, 0, 25 degrees, 7 blocks); each enemy on them is struck once and thrown up a little. */
    private void sixth(MoveKit kit, double rate) {
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing().clone().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) return;
        facing.normalize();
        kit.guard(0.3, 10);
        kit.slash("drop", 3.0, 120.0, 5.0, 0.0, 1.6, 0.4, 0.0, 1.4, 0.16, 0.0);
        kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.55f);
        java.util.Set<org.bukkit.entity.LivingEntity> struck = new java.util.HashSet<>();
        Location from = feet.clone().add(0.0, 0.5, 0.0);
        for (int side = -1; side <= 1; side++) {
            Vector way = facing.clone().rotateAroundY(Math.toRadians(25.0 * side));
            for (int along = 1; along <= 6; along++) {
                Location at = feet.clone().add(way.clone().multiply(along * 1.15)).add(0.0, 0.2, 0.0);
                kit.later(along, () -> kit.fx(at).flatRing(at, 0.6, 12, kit.palette(), 0.5, 0.2, 1.0));
            }
            for (org.bukkit.entity.LivingEntity target : kit.corridor(from, way, 7.0, 1.2)) {
                if (struck.add(target) && kit.hit(target, rate)) kit.lift(target, 0.35);
            }
        }
    }
}
