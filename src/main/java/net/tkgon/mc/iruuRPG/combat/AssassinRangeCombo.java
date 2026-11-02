package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The assassin's combo with a ranged weapon: the shadow bow (t168, {@link AssassinShade}). Arrows of shadow that fly through blocks, seek the
 * enemy nearest the line of sight (behind a wall too) and leave afflictions for the assassination of 大. Normal: 1 a shadow arrow (70%, confusion
 * {@code n1-confusion} 10), 2 two arrows in a narrow V (45% x2, a bleed of {@code n2-bleed} 20% of each hit), 3 three quick arrows (35% x3, slowed
 * 20% for 1 s), 4 four arrows falling on the aim from above, through any roof (25% x4, confusion 5 each), 5 a great shadow arrow through everything
 * in the way (150%: confusion 15, bleed 20% and slowed 30%).
 *
 * <p>The skills are the shadow bow's own ({@link AssassinShade}); the combo has no heavy attacks since t168 (the floating blades are gone).
 */
public final class AssassinRangeCombo extends BaseCombo {

    static final String PLAN = "assassin_range";

    public AssassinRangeCombo() {
        super("assassin", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.7, 0.45, 0.35, 0.25, 1.5}, new int[]{1, 2, 3, 4, 1},
                new double[]{0.06, 0.24, 1.3, 0.35, 0.2, 0.2}, new int[]{120, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(AssassinShade.SHADE);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        java.util.UUID id = kit.player.getUniqueId();
        switch (step.stage()) {
            case 1 -> {
                Location start = ComboParts.hand(kit);
                kit.sound(start, Sfx.SHADE_LOOSE, 0.8f, 1.2f);
                AssassinShade.arrow(kit, start, AssassinShade.seek(kit, start, 28.0), 2.0, 28.0, 1.0, false, victim -> {
                    if (!kit.hit(victim, rate)) return;
                    AssassinShade.struck(kit, victim);
                    kit.confuse(victim, kit.number(PLAN, "n1-confusion", 10.0));
                });
            }
            case 2 -> {
                for (int index = 0; index < hits; index++) {
                    double turn = Math.toRadians(index % 2 == 0 ? -5.0 : 5.0);
                    kit.later(index, () -> {
                        Location start = ComboParts.hand(kit);
                        kit.sound(start, Sfx.SHADE_LOOSE, 0.6f, 1.4f);
                        AssassinShade.arrow(kit, start, AssassinShade.seek(kit, start, 28.0).rotateAroundY(turn), 2.0, 28.0, 0.9, false, victim -> {
                            double before = victim.getHealth();
                            if (!kit.hit(victim, rate)) return;
                            AssassinShade.struck(kit, victim);
                            double dealt = Math.max(0.0, before - victim.getHealth());
                            if (dealt > 0.0 && victim.isValid() && !victim.isDead()) kit.bleed(victim, dealt * kit.number(PLAN, "n2-bleed", 0.2));
                        });
                    });
                }
            }
            case 3 -> {
                for (int index = 0; index < hits; index++) {
                    kit.later(index * 3L, () -> {
                        Location start = ComboParts.hand(kit);
                        kit.sound(start, Sfx.SHADE_LOOSE, 0.5f, 1.6f);
                        AssassinShade.arrow(kit, start, AssassinShade.seek(kit, start, 26.0), 2.3, 26.0, 0.8, false, victim -> {
                            if (!kit.hit(victim, rate)) return;
                            AssassinShade.struck(kit, victim);
                            kit.slow(victim, 20.0, 1.0);
                        });
                    });
                }
            }
            case 4 -> {
                // four arrows fall on the aim from high above, through any roof
                LivingEntity aimed = kit.nearest(26.0, 25.0);
                Location aim = aimed != null ? aimed.getLocation() : kit.ground(kit.aim());
                kit.sound(kit.player.getLocation(), Sfx.SHADE_LOOSE, 0.8f, 0.9f);
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                for (int index = 0; index < hits; index++) {
                    double angle = random.nextDouble(Math.PI * 2.0);
                    double r = index == 0 ? 0.0 : 1.4 * Math.sqrt(random.nextDouble());
                    Location spot = aim.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
                    kit.later(4L + index * 2L, () -> AssassinShade.arrow(kit, spot.clone().add(0.3, 9.0, 0.2), new Vector(-0.03, -1.0, -0.02), 2.5, 10.0, 0.9, false, victim -> {
                        if (!kit.hit(victim, rate)) return;
                        AssassinShade.struck(kit, victim);
                        kit.confuse(victim, kit.number(PLAN, "n4-confusion", 5.0));
                    }));
                }
            }
            default -> {
                // a great shadow arrow through everything in the way, every affliction at once
                Location start = ComboParts.hand(kit);
                kit.sound(start, Sfx.SHADE_DRAW, 0.6f, 1.4f);
                kit.later(4L, () -> {
                    Location from = ComboParts.hand(kit);
                    kit.sound(from, Sfx.SHADE_LOOSE, 1.0f, 0.8f);
                    AssassinShade.arrow(kit, from, AssassinShade.seek(kit, from, 32.0), 2.6, 32.0, 1.5, true, victim -> {
                        double before = victim.getHealth();
                        if (!kit.hit(victim, rate)) return;
                        AssassinShade.struck(kit, victim);
                        kit.confuse(victim, kit.number(PLAN, "n5-confusion", 15.0));
                        kit.slow(victim, 30.0, 1.5);
                        double dealt = Math.max(0.0, before - victim.getHealth());
                        if (dealt > 0.0 && victim.isValid() && !victim.isDead()) kit.bleed(victim, dealt * kit.number(PLAN, "n5-bleed", 0.2));
                    });
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: the class's skills are its own (AssassinShade).
    }

}
