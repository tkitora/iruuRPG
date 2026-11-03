package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The warrior's combo with melee weapons: the shield and the blood (t168, retuned by t198, {@link WarriorSkills}). Heavy blows on the front line;
 * during the ULT every hit drinks back the wound the warrior took ({@link WarriorBlood}). The class's innate 薙ぎ払い ({@code melee_area}) makes
 * every hit of it strike the other enemies round the one hit too (t198, {@link WarriorSkills#cutAll}). Drawn in the weapon's colors.
 *
 * <p>Normal: 1 a cut down from the shoulder (60%), 2 the rising return cut (70%, pushes a little), 3 a step in and two quick cuts (45% x2), 4 two
 * wide sweeps round the warrior (55% x2), 5 a great cut brought down on a strip ahead (130%; it drinks the wound twice as fast) and a wave that
 * runs on along the ground ({@code wave-share} 50% of it, {@code wave-range} 8 blocks). (t198: weaker by about a fifth, and no more guard on the
 * blows: the warrior's staying power is the shield of 大.)
 *
 * <p>The skills are the warrior's own ({@link WarriorSkills}); the combo has no heavy attacks since t168.
 */
public final class WarriorMeleeCombo extends BaseCombo {

    static final String PLAN = "warrior_melee";

    public WarriorMeleeCombo() {
        super("warrior", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.6, 0.7, 0.45, 0.55, 1.3}, new int[]{1, 1, 2, 2, 1},
                new double[]{1.0, 0.6, 0.24, 1.5, 0.8, 1.2}, new int[]{360, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                // a cut down from the right shoulder
                kit.slash("drop", 3.2, 130.0, 30.0, 0.0, 1.9, 0.3, 0.0, 1.35, 0.13, 0.0);
                kit.sound(feet, Sfx.BLOOD_SWING, 0.8f, 1.0f);
                if (WarriorSkills.cutAll(kit, kit.cone(3.6, 60.0), facing, rate, null, true) > 0) kit.sound(feet, Sfx.BLOOD_HIT, 0.5f, 1.1f);
            }
            case 2 -> {
                // the return: a rising cut from the left that pushes a little
                kit.slash("arc", 3.2, 150.0, -35.0, 180.0, 1.8, 0.4, 0.0, 1.35, 0.13, 0.0);
                kit.sound(feet, Sfx.BLOOD_SWING, 0.8f, 1.15f);
                java.util.List<LivingEntity> targets = kit.cone(3.6, 70.0);
                WarriorSkills.cutAll(kit, targets, new Vector(0.0, 1.0, 0.0), rate, null, true);
                for (LivingEntity target : targets) kit.push(target, feet.toVector(), 0.2, 0.12);
            }
            case 3 -> {
                // a step in, and two quick cuts one from each side
                kit.dash(facing, 0.55, 3, end -> {
                    for (int index = 0; index < hits; index++) {
                        int which = index;
                        kit.later(index * 3L, () -> {
                            kit.slash("arc", 2.9, 120.0, which % 2 == 0 ? 20.0 : -20.0, which % 2 == 0 ? 0.0 : 180.0, 1.6, 0.35, 0.0, 1.2, 0.09, 0.0);
                            kit.sound(kit.player.getLocation(), Sfx.BLOOD_SWING, 0.6f, 1.2f + 0.1f * which);
                            WarriorSkills.cutAll(kit, kit.cone(3.4, 60.0), kit.facing(), rate, null, true);
                        });
                    }
                });
            }
            case 4 -> {
                // two wide sweeps, turning: everyone round
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 5L, () -> {
                        Location at = kit.player.getLocation();
                        kit.slash("arc", 3.4, 300.0, 70.0, which % 2 == 0 ? 0.0 : 180.0, 0.5, 0.5, 0.0, 1.35, 0.12, 0.0);
                        kit.sound(at, Sfx.BLOOD_SWING, 0.7f, 0.9f);
                        WarriorSkills.cutAll(kit, kit.around(at, 3.8), kit.facing().rotateAroundY(Math.PI / 2.0), rate, null, true);
                    });
                }
            }
            default -> {
                // the finisher: a great cut brought down on a strip ahead (it drinks the wound twice as fast in the ULT), and a wave runs on
                WarriorSkills.Blood blood = WarriorSkills.blood(kit.number(PLAN, "finisher-rally", 2.0), 0.0);
                kit.slash("drop", 3.6, 150.0, 5.0, 0.0, 2.0, 0.3, 0.0, 1.7, 0.18, 0.0);
                kit.sound(feet, Sfx.BLOOD_SLAM, 1.0f, 1.0f);
                Location from = feet.clone().add(0.0, 0.5, 0.0);
                kit.shockwave(feet.clone().add(facing.clone().multiply(2.2)), 0.6, 2.4, 4, 1.2);
                WarriorSkills.cutAll(kit, kit.corridor(from, facing, 4.5, 1.6), new Vector(0.0, 1.0, 0.0), rate, blood, true);
                Location start = feet.clone().add(0.0, 0.6, 0.0).add(facing.clone().multiply(1.2));
                MageMeleeCombo.crescent(kit, start, facing, 1.3, kit.number(PLAN, "wave-range", 8.0), Math.PI / 2.0,
                        rate * kit.number(PLAN, "wave-share", 0.5), 1.4, org.bukkit.Particle.DAMAGE_INDICATOR, Sfx.BLOOD_SWING,
                        target -> WarriorSkills.bled(kit, target, facing));
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: the class's skills are its own (WarriorSkills).
    }

}
