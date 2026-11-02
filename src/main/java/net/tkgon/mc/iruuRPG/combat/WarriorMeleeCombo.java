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
 * <p>The skills are the blood knight's own ({@link WarriorSkills}); the combo has no heavy attacks since t168.
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
        // (t168) no heavy attack of this combo is thrown any more: the class's skills are its own (WarriorSkills).
    }

}
