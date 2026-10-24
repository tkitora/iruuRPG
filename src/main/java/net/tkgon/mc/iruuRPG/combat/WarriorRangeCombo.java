package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The warrior's combo with a ranged weapon (thrown axes and spears): heavy missiles. Normal: 1 a thrown axe (100%), 2 two javelins (60% x2),
 * 3 a boomerang axe that hits going out and coming back (60% x2), 4 three shock waves along the ground (40% x3), 5 a spear thrown from a leap
 * (150%). Heavy: 0 a war cry for 2.5 s (pushes and hurts what is near, 25% a pulse), 1 a great axe that pierces (130%), 2 a rain of spears
 * (15% x8), 3 a chain that pulls the target in and cuts (120%), 4 a siege spear that bursts (150%).
 */
public final class WarriorRangeCombo extends BaseCombo {

    static final String PLAN = "warrior_range";

    public WarriorRangeCombo() {
        super("warrior", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.73, 0.48, 0.61, 0.48, 1.7}, new int[]{1, 2, 2, 3, 1},
                new double[]{0.25, 1.3, 0.15, 1.2, 1.5}, new int[]{150, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        Vector[] axes = kit.axes();
        switch (step.stage()) {
            case 1 -> {
                Location start = ComboParts.hand(kit);
                kit.sound(start, Sfx.WIND, 1.0f, 0.8f);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                kit.bolt(start, direction, 1.7, 14.0, rate, end -> {
                    kit.burst(end, 1.6);
                    kit.sound(end, Sfx.METAL_HEAVY, 0.8f, 0.8f);
                });
            }
            case 2 -> ComboParts.quickBolts(kit, hits, rate, 1.9, 14.0, 0.0);
            case 3 -> {
                // a boomerang axe: it flies out, hits, and comes back, hitting again
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                kit.sound(start, Sfx.WIND, 1.0f, 1.2f);
                kit.bolt(start, direction, 1.5, 9.0, rate, out -> {
                    kit.burst(out, 1.2);
                    kit.later(2, () -> {
                        Vector back = kit.player.getEyeLocation().subtract(0.0, 0.4, 0.0).toVector().subtract(out.toVector());
                        if (back.lengthSquared() < 1.0E-6) return;
                        kit.sound(out, Sound.ITEM_TRIDENT_RETURN, 0.9f, 1.0f);
                        kit.bolt(out.clone(), back.normalize(), 1.5, back.length(), rate, here -> kit.burst(here, 1.0));
                    });
                });
            }
            case 4 -> {
                // shock waves run along the ground one after the other
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 3L, () -> {
                        kit.sound(feet, Sfx.METAL_HEAVY, 0.8f, 0.7f + which * 0.15f);
                        for (int along = 1; along <= 6; along++) {
                            double d = along;
                            kit.later(along / 2, () -> {
                                Location at = feet.clone().add(facing.clone().multiply(d * 1.4)).add(0.0, 0.3, 0.0);
                                kit.fx(at).flatRing(at, 0.8 + d * 0.1, 20, kit.palette(), 0.4 + 0.08 * d, 0.3, 1.0);
                            });
                        }
                        for (LivingEntity target : kit.corridor(feet.clone().add(0.0, 0.5, 0.0), facing, 8.4, 1.4)) {
                            if (kit.hit(target, rate)) kit.push(target, feet.toVector(), 0.4, 0.2);
                        }
                    });
                }
            }
            default -> {
                // the finisher: a leap, and the spear comes down on the aim
                kit.leap(facing, 0.3, 0.7, land -> {
                    Location from = ComboParts.hand(kit);
                    Location aim = kit.aim();
                    Vector direction = aim.toVector().subtract(from.toVector());
                    direction = direction.lengthSquared() < 1.0E-6 ? axes[0].clone() : direction.normalize();
                    kit.sound(land, Sfx.WIND, 1.0f, 0.7f);
                    kit.bolt(from, direction, 1.9, 14.0, rate, end -> {
                        kit.burst(end, 3.0);
                        kit.shockwave(end, 0.8, 3.0, 4, 1.1);
                        kit.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
                        kit.hitAll(kit.around(end, 2.8), rate * 0.3);
                    });
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                // a war cry: what is near is pushed away and hurt for 2.5 seconds
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.ENTITY_RAVAGER_ROAR, 0.9f, 1.2f);
                ComboParts.sigil(kit, "warrior_cry", at, 4.0, rate, 50, 10, here -> {
                    for (LivingEntity victim : kit.around(here, 4.0)) kit.push(victim, here.toVector(), 0.35, 0.1);
                });
            }
            case 1 -> {
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                kit.sound(start, Sfx.WIND, 1.0f, 0.6f);
                kit.slash("drop", 3.0, 120.0, 20.0, 0.0, 1.8, 0.3, 0.0, 1.5, 0.16, 0.0);
                ComboParts.pierce(kit, start, direction, 12.0, 1.2, rate);
            }
            case 2 -> ComboParts.rain(kit, target, 3.4, 8, rate, 1.6, Sound.ITEM_TRIDENT_HIT);
            case 3 -> {
                // a chain: the target is hooked and dragged to the warrior, then cut
                LivingEntity hooked = kit.aimed();
                if (hooked == null) {
                    kit.sound(feet, Sound.BLOCK_DISPENSER_FAIL, 0.5f, 1.4f);
                    return;
                }
                Location from = ComboParts.hand(kit);
                Location to = hooked.getLocation().add(0.0, hooked.getHeight() * 0.5, 0.0);
                kit.sound(from, Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.0f);
                kit.line(from, to, 0.5, 1.0, 1.3);
                kit.pull(hooked, feet.clone().add(kit.facing().multiply(1.5)), 1.1);
                kit.later(4, () -> {
                    kit.slash("arc", 2.4, 140.0, 40.0, 0.0, 1.8, 0.3, 0.0, 1.2, 0.12, 0.0);
                    kit.hit(hooked, rate);
                });
            }
            default -> {
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                kit.sound(start, Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 0.5f);
                kit.bolt(start, direction, 1.6, 16.0, 0.0, end -> {
                    kit.burst(end, 4.0);
                    kit.shockwave(end, 1.0, 4.2, 5, 1.2);
                    kit.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                    for (LivingEntity victim : kit.around(end, 4.2)) {
                        if (kit.hit(victim, rate)) kit.push(victim, end.toVector(), 0.7, 0.3);
                    }
                });
            }
        }
    }
}
