package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The basic combo of a sword, for every class that may use a melee weapon without it being a favorite of the class: five blows and five heavy attacks that need no class
 * skill. Normal: 1 a slash (40%), 2 a back slash (50%), 3 a thrust (60%), 4 a spin (35% x2 on everyone round), 5 an overhead cleave with a shockwave (120%). Heavy: 0 a lunging
 * slash (50%, cooldown 3 s: it moves the player a little, so it is not for pressing again and again), 1 a rising slash (60%), 2 a whirl (70% on everyone round), 3 a leaping
 * smash (100%), 4 a wave of the blade ahead (140%). Cutting the whole chain does the damage of the plain blows of the same time (rates add up to 340% over five blows); a
 * longer chain is stronger per second than a short one.
 */
public final class GenericMeleeCombo extends BaseCombo {

    static final String PLAN = "generic_melee";

    public GenericMeleeCombo() {
        super("*", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.4, 0.54, 0.68, 0.81, 0.95}, new int[]{1, 1, 1, 2, 1},
                new double[]{0.5, 0.6, 0.7, 1.0, 1.4}, new int[]{60, 0, 0, 0, 0}, 36));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        switch (step.stage()) {
            case 1 -> {
                kit.slash("arc", 2.2, 120.0, 40.0, 0.0, 1.9, 0.4, 0.0, 1.0, 0.08, 0.0);
                kit.sound(feet, Sfx.WIND, 0.5f, 1.6f);
                kit.hitAll(kit.cone(2.8, 60.0), rate);
            }
            case 2 -> {
                kit.slash("arc", 2.2, 120.0, 40.0, 180.0, 1.9, 0.4, 0.0, 1.0, 0.08, 0.0);
                kit.sound(feet, Sfx.WIND, 0.5f, 1.8f);
                kit.hitAll(kit.cone(2.8, 60.0), rate);
            }
            case 3 -> {
                Location from = kit.eye().subtract(0.0, 0.3, 0.0);
                Vector facing = kit.axes()[0];
                kit.line(from, from.clone().add(facing.clone().multiply(3.4)), 0.2, 1.0, 1.2);
                kit.sound(feet, Sfx.WIND_BURST, 0.6f, 1.5f);
                kit.hitAll(kit.corridor(from, facing, 3.4, 0.9), rate);
            }
            case 4 -> {
                int hits = step.extraHits() + 1;
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 3L, () -> {
                        kit.slash("arc", 2.6, 300.0, 70.0, which % 2 == 0 ? 0.0 : 180.0, 0.4, 0.5, 0.0, 1.0, 0.07, 0.0);
                        kit.sound(kit.player.getLocation(), Sfx.WIND, 0.4f, 1.3f + which * 0.15f);
                        kit.hitAll(kit.around(kit.player.getLocation(), 3.0), rate / hits);
                    });
                }
            }
            default -> {
                kit.slash("drop", 2.8, 150.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.3, 0.14, 0.0);
                Location land = kit.ground(feet.clone().add(kit.facing().multiply(2.2)));
                kit.later(3L, () -> {
                    kit.shockwave(land, 0.8, 3.2, 3, 1.0);
                    kit.sound(land, Sfx.METAL_HEAVY, 0.7f, 0.9f);
                    kit.hitAll(kit.around(land, 3.2), rate);
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a lunge: a short dash that ends in a slash (a cooldown of 3 s)
                kit.sound(feet, Sfx.WIND_BURST, 0.8f, 1.3f);
                kit.dash(facing, 0.8, 4, end -> {
                    kit.slash("drop", 2.4, 120.0, 25.0, 0.0, 1.9, 0.4, 0.0, 1.1, 0.1, 0.0);
                    kit.hitAll(kit.cone(3.0, 60.0), rate);
                });
            }
            case 1 -> {
                kit.slash("arc", 2.6, 130.0, -50.0, 0.0, 1.9, 0.9, 0.0, 1.1, 0.1, 0.0);
                kit.sound(feet, Sfx.WIND, 0.6f, 1.2f);
                kit.hitAll(kit.cone(3.0, 65.0), rate);
            }
            case 2 -> {
                kit.sound(feet, Sfx.WIND_BURST, 0.8f, 1.0f);
                for (int index = 0; index < 2; index++) {
                    int which = index;
                    kit.later(index * 4L, () -> {
                        kit.slash("arc", 2.8, 320.0, 70.0, which == 0 ? 0.0 : 180.0, 0.4, 0.5, 0.0, 1.1, 0.08, 0.0);
                        kit.hitAll(kit.around(kit.player.getLocation(), 3.4), rate / 2.0);
                    });
                }
            }
            case 3 -> {
                kit.leap(facing, 0.6, 0.45, land -> {
                    kit.shockwave(land, 0.8, 3.6, 4, 1.1);
                    kit.slash("drop", 2.8, 150.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.3, 0.14, 0.0);
                    kit.sound(land, Sfx.METAL_HEAVY, 0.8f, 0.8f);
                    kit.hitAll(kit.around(land, 3.4), rate);
                });
            }
            default -> {
                Location from = kit.eye().subtract(0.0, 0.4, 0.0);
                kit.slash("drop", 3.0, 160.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.4, 0.16, 0.0);
                kit.sound(feet, Sfx.WIND_BURST, 1.0f, 0.9f);
                CombatLine.wave(kit, from, kit.axes()[0], 7.0, 1.6, rate);
            }
        }
    }
}
