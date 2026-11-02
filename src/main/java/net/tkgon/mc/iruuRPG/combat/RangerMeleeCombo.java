package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The ranger's combo with a melee weapon (a hunting knife): quick, mobile, setting up the next shot. A melee hit powers up the ranger's next
 * ranged attack (the loop). Normal: 1 two quick stabs (55% x2), 2 three cuts (40% x3), 3 a lunge (120%), 4 a flurry (28% x5), 5 a leap onto
 * the target (150%). (t168) 3 is a wire to the enemy ahead and a zip in to it, 5 a spinning kick that sends the enemies to the ranger's spirit
 * bow when it stands near ({@link RangerSkills}: they are tied there), else away. Nothing can hurt the ranger while 3 and 5 go.
 * The combo has no heavy attacks since t168 (the knife's skills are its own, {@link RangerSkills}).
 */
public final class RangerMeleeCombo extends BaseCombo {

    static final String PLAN = "ranger_melee";

    public RangerMeleeCombo() {
        super("gifted", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.32, 0.29, 1.09, 0.26, 1.52}, new int[]{2, 3, 1, 5, 1},
                new double[]{0.21, 1.02, 0.34, 1.1, 1.28, 1.3}, new int[]{100, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                for (int index = 0; index < hits; index++) {
                    kit.later(index * 2L, () -> {
                        Location from = kit.eye().add(facing.clone().multiply(0.8)).subtract(0.0, 0.4, 0.0);
                        kit.line(from, from.clone().add(facing.clone().multiply(3.4)), 0.2, 1.0, 1.2);
                        kit.sound(feet, Sfx.WIND, 0.6f, 1.7f);
                        kit.hitAll(kit.cone(3.4, 25.0), rate);
                    });
                }
            }
            case 2 -> {
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 2L, () -> {
                        kit.slash(which == 1 ? "line" : "arc", 2.3, 120.0, 35.0 + which * 20.0, which % 2 == 0 ? 0.0 : 180.0, 2.0, 0.3, 0.0, 1.0, 0.1, 0.0);
                        kit.sound(feet, Sfx.WIND, 0.6f, 1.5f);
                        kit.hitAll(kit.cone(3.2, 65.0), rate);
                    });
                }
            }
            case 3 -> {
                // a wire to the enemy ahead and a zip in to it (nothing can hurt the ranger on the way), a cut, and a step back
                LivingEntity ahead = kit.nearest(kit.number(PLAN, "lunge-range", 8.0), 40.0);
                Vector heading = ahead == null ? facing : ahead.getLocation().toVector().subtract(feet.toVector());
                if (ahead != null) RangerSkills.wire(kit, ComboParts.hand(kit), ahead.getLocation().add(0.0, ahead.getHeight() * 0.55, 0.0));
                kit.sound(feet, ahead != null ? Sfx.WIRE_ZIP : Sfx.WIND, 0.8f, 1.3f);
                int ticks = ahead == null ? 3 : Math.max(2, Math.min(6, (int) Math.ceil(heading.clone().setY(0.0).length() / 1.1)));
                kit.invulnerable(ticks + 2);
                kit.dashToContact(heading, ahead == null ? 0.45 : 1.1, ticks, 1.6, (end, touched) -> {
                    kit.slash("drop", 2.6, 130.0, 25.0, 0.0, 1.8, 0.3, 0.0, 1.2, 0.14, 0.0);
                    kit.hitAll(kit.cone(3.2, 60.0), rate);
                    if (touched != null) kit.later(2L, () -> kit.hopBack(0.8, 0.25));
                });
            }
            case 4 -> {
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index, () -> {
                        kit.slash("line", 2.0, 110.0, which * 36.0 - 70.0, which % 2 == 0 ? 0.0 : 180.0, 1.9, 0.3, 0.0, 0.85, 0.08, 0.0);
                        kit.hitAll(kit.cone(3.0, 65.0), rate);
                    });
                }
                kit.sound(feet, Sfx.WIND, 0.9f, 1.8f);
            }
            default -> {
                // the finisher: a spinning kick that sends the enemies to the ranger's spirit bow (when it stands near; else away), tied there
                Location turret = RangerDeployCombo.turretNear(kit.player, kit.number(RangerSkills.MELEE, "turret-reach", 20.0));
                kit.invulnerable(6);
                kit.slash("arc", 2.8, 300.0, 75.0, 0.0, 0.6, 0.9, 0.0, 1.3, 0.12, 0.0);
                kit.sound(feet, Sfx.WIND_BURST, 0.9f, 1.3f);
                kit.shockwave(feet, 0.6, 3.0, 3, 1.0);
                for (LivingEntity target : kit.cone(3.6, 70.0)) {
                    if (!kit.hit(target, rate)) continue;
                    if (turret != null) {
                        RangerSkills.tie(target, kit.number(RangerSkills.MELEE, "tie-seconds", 3.0));
                        RangerSkills.drag(kit, target, turret.clone().add(0.0, 0.5, 0.0));
                    } else {
                        kit.push(target, feet.toVector(), 0.7, 0.3);
                    }
                }
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: the class's skills are its own (RangerSkills).
    }

}
