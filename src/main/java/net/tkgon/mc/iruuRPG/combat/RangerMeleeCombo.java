package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The ranger's combo with a melee weapon (a hunting knife): quick, mobile, setting up the next shot. A melee hit powers up the ranger's next
 * ranged attack (the loop). Normal: 1 two quick stabs (55% x2), 2 three cuts (40% x3), 3 a lunge (120%), 4 a flurry (28% x5), 5 a leap onto
 * the target (150%). The lunge (3) and the leap (5) leave the ranger unhurtable while they go; after the blow the ranger steps back. Heavy: 0 a backstep that scatters caltrops for 2.5 s (25% a pulse, slows), 1 a low sweep all round (120%), 2 three
 * thrown knives (40% x3), 3 a cut that pins (130%, slows 60%), 4 a run through the line (150% on all in the way).
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
                // a short step in (the big moves are at the end of the chain or heavy 0, so none of them can be pressed again and again): nothing can hurt the ranger for 3 ticks of it, which ends at the first enemy it touches; after the cut, a backstep
                LivingEntity ahead = kit.nearest(6.0, 45.0);
                Vector heading = ahead == null ? facing : ahead.getLocation().toVector().subtract(feet.toVector());
                kit.sound(feet, Sfx.METAL, 0.9f, 1.2f);
                kit.invulnerable(3);
                kit.dashToContact(heading, 0.45, 3, 1.6, (end, touched) -> {
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
                // the finisher: a leap onto the target (nothing can hurt the ranger in the air); after the blow, a backstep
                kit.invulnerable(16);
                kit.leap(facing, 0.8, 0.55, land -> {
                    kit.shockwave(land, 1.0, 3.4, 4, 1.1);
                    kit.slash("drop", 2.8, 150.0, 8.0, 0.0, 1.8, 0.4, 0.0, 1.3, 0.16, 0.0);
                    kit.sound(land, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
                    boolean struck = false;
                    for (LivingEntity target : kit.around(land, 3.4)) {
                        if (kit.hit(target, rate)) {
                            kit.push(target, land.toVector(), 0.5, 0.3);
                            struck = true;
                        }
                    }
                    if (struck) kit.later(2L, () -> kit.hopBack(0.7, 0.3));
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
                // a backstep that scatters caltrops where the ranger stood
                Location at = kit.ground(feet);
                kit.hopBack(0.9, 0.3);
                kit.sound(feet, Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.4f);
                ComboParts.sigil(kit, "ranger_caltrops", at, 3.0, rate, 50, 10, here -> {
                    for (LivingEntity victim : kit.around(here, 3.0)) kit.slow(victim, 45.0, 1.0);
                });
            }
            case 1 -> {
                kit.sound(feet, Sfx.WIND, 1.0f, 0.8f);
                kit.slash("arc", 3.0, 340.0, 85.0, 0.0, 0.8, 0.9, 0.0, 1.3, 0.16, 0.0);
                kit.shockwave(feet, 1.0, 3.2, 3, 1.0);
                for (LivingEntity target : kit.around(feet, 3.2)) {
                    if (kit.hit(target, rate)) kit.push(target, feet.toVector(), 0.4, 0.2);
                }
            }
            case 2 -> {
                Vector[] axes = kit.axes();
                kit.sound(feet, Sfx.WIND, 0.9f, 1.4f);
                for (int index = 0; index < 3; index++) {
                    double turn = Math.toRadians((index - 1) * 12.0);
                    Vector direction = axes[0].clone().multiply(Math.cos(turn)).add(axes[1].clone().multiply(Math.sin(turn))).normalize();
                    Location start = ComboParts.hand(kit);
                    kit.later(index, () -> kit.bolt(start.clone(), direction, 1.8, 10.0, rate, end -> kit.burst(end, 0.8)));
                }
            }
            case 3 -> {
                kit.slash("drop", 2.8, 120.0, 20.0, 0.0, 2.0, 0.3, 0.0, 1.3, 0.14, 0.0);
                kit.sound(feet, Sfx.METAL, 1.0f, 0.9f);
                for (LivingEntity target : kit.cone(3.6, 50.0)) {
                    if (kit.hit(target, rate)) kit.slow(target, 60.0, 2.0);
                }
            }
            case 4 -> {
                // a run through the line: everything in the way is cut once, the ranger comes out the other side
                kit.sound(feet, Sfx.WIND, 1.0f, 0.6f);
                Location from = feet.clone().add(0.0, 1.0, 0.0);
                kit.line(from, from.clone().add(facing.clone().multiply(7.0)), 0.2, 1.0, 1.5);
                kit.hitAll(kit.corridor(from, facing, 7.0, 1.6), rate);
                kit.invulnerable(8);
                kit.dash(facing, 1.4, 5, end -> kit.shockwave(end, 0.8, 2.6, 3, 1.0));
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a leap ahead (nothing can hurt the ranger in the air) and a plunge: everything within 2.8 blocks of the landing is struck and pushed out. */
    private void sixth(MoveKit kit, double rate) {
        Vector facing = kit.facing().clone().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
        facing.normalize();
        kit.invulnerable(14);
        kit.sound(kit.player.getLocation(), Sfx.WIND_BURST, 1.0f, 1.2f);
        kit.leap(facing, 0.9, 0.55, land -> {
            kit.slash("drop", 2.8, 140.0, 5.0, 0.0, 0.8, 0.4, 0.0, 1.3, 0.14, 0.0);
            kit.shockwave(land, 0.6, 2.8, 4, 1.1);
            kit.sound(land, Sfx.METAL_HEAVY, 0.9f, 1.1f);
            for (org.bukkit.entity.LivingEntity victim : kit.around(land, 2.8)) {
                if (kit.hit(victim, rate)) kit.push(victim, land.toVector(), 0.5, 0.2);
            }
        });
    }
}
