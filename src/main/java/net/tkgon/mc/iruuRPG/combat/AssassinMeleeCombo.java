package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * The assassin's combo with melee weapons: mobility joined to skill. Every cut is struck with the hand whose turn it is (the two
 * weapons of 固有0 take turns). The reach is short (under 3 blocks), the hits few; what the assassin has is the way to the enemy and
 * the way out: a step that closes the gap, a blink behind the target, and then one enemy takes the whole of it. For a moment the other
 * way: the whirling blades hit everyone round the assassin. Always available with a favorite weapon (no node needed).
 *
 * <p>Normal: 1 a fang cross (two cuts, 50% x2), 2 a step in (a dash that stops at the first enemy it touches, then two cuts, 60% x2), 3 a shadow step
 * (a blink behind the nearest enemy and a backstab, 130%, confuses a little), 4 whirling blades (a spin of 4 cuts, 25% each on everyone round, 12 ticks),
 * 5 an execution (a blink behind the aim, 150% on it and 40% on the others near). Heavy: 0 a shadow dash (6 blocks, nothing can hurt the assassin
 * for 8 ticks, 100% on all in the way), 1 a chain blink (blinks behind the nearest enemy, three cuts of 40%), 2 a blade storm (a spin for 1.2 s, 16% every
 * 3 ticks on everyone round, takes 40% less damage meanwhile), 3 a crossing step (through the enemy ahead and back across, 60% on all in the way each time),
 * 4 a phantom execution (afterimages, then a blink behind the nearest enemy: 150% and confusion).
 *
 * <p>The class skill (SATUJIN, F) puts the assassin in a frenzy for some seconds: the blows are 20% stronger (35% when upgraded), the normal attacks come faster,
 * an enemy out of reach is blinked to before a normal attack, every normal attack is followed by the cut of a shadow round the assassin (40%), heavy 0 has no cooldown,
 * and the status effects (confusion) land with every hit.
 */
public final class AssassinMeleeCombo extends BaseCombo {

    static final String PLAN = "assassin_melee";

    public AssassinMeleeCombo() {
        super("assassin", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.5, 0.6, 1.3, 0.25, 1.5}, new int[]{2, 2, 1, 4, 1},
                new double[]{1.0, 0.4, 0.16, 0.6, 1.5}, new int[]{100, 0, 0, 0, 0}, 36));
    }

    /** One cut at {@code rate} on everyone in {@code targets}, struck with the hand whose turn it is. */
    private int cut(MoveKit kit, List<LivingEntity> targets, double rate) {
        AttackService.MeleeHand hand = kit.nextHand();
        int hit = 0;
        for (LivingEntity target : targets) if (kit.hitHand(hand, target, rate)) hit++;
        return hit;
    }

    /** How much stronger the blows are in a frenzy (SATUJIN): 1 when there is none. */
    private static double boost(int frenzy) {
        return frenzy >= 2 ? 1.35 : frenzy == 1 ? 1.2 : 1.0;
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        int frenzy = kit.frenzy();
        if (frenzy == 0) {
            normalBody(kit, step, 1.0);
            return;
        }
        // in a frenzy every blow is stronger, the assassin first blinks to an enemy that is out of reach (the blinking moves do it anyway),
        // and a shadow of the assassin cuts again a moment later round the place it stands
        double boost = boost(frenzy);
        int stage = step.stage();
        LivingEntity far = stage == 3 || stage == 5 ? null : kit.nearest(9.0, 90.0);
        if (far != null && far.getLocation().distanceSquared(kit.player.getLocation()) > 6.25) {
            kit.blink(kit.behind(far, 1.6), far);
            kit.later(1L, () -> normalBody(kit, step, boost));
        } else {
            normalBody(kit, step, boost);
        }
        double afterimage = step.damageMultiplier() * 0.4 * boost;
        kit.later(5L, () -> {
            Location at = kit.player.getLocation();
            kit.fx(at).sphereShell(at.clone().add(0.0, 1.0, 0.0), 0.6, 0.0, 22, kit.palette(), 1.0);
            kit.slash("arc", 2.6, 300.0, 70.0, 0.0, 0.4, 0.5, 0.0, 1.0, 0.07, 0.0);
            kit.sound(at, Sfx.WIND, 0.35f, 1.7f);
            cut(kit, kit.around(at, 3.2), afterimage);
        });
    }

    private void normalBody(MoveKit kit, MeleeCombo.Step step, double boost) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier() * boost;
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 2L, () -> {
                        kit.slash("arc", 2.2, 120.0, 40.0, which % 2 == 0 ? 180.0 : 0.0, 1.9, 0.4, 0.0, 1.0, 0.08, 0.0);
                        kit.sound(feet, Sfx.WIND, 0.5f, 1.6f + which * 0.15f);
                        cut(kit, kit.cone(2.8, 60.0), rate);
                    });
                }
            }
            case 2 -> {
                // a step in: the dash ends at the first enemy it touches (a moment nothing can hurt the assassin), then two cuts
                LivingEntity ahead = kit.nearest(7.0, 45.0);
                Vector heading = ahead == null ? facing : ahead.getLocation().toVector().subtract(feet.toVector());
                kit.invulnerable(5);
                kit.sound(feet, Sfx.WIND, 0.7f, 1.3f);
                kit.dashToContact(heading, 1.0, 5, 1.7, (end, touched) -> {
                    for (int index = 0; index < hits; index++) {
                        int which = index;
                        kit.later(index * 2L, () -> {
                            kit.slash(which == 0 ? "drop" : "line", 2.3, 120.0, 30.0 + which * 25.0, which == 0 ? 0.0 : 180.0, 1.9, 0.4, 0.0, 1.05, 0.09, 0.0);
                            kit.sound(end, Sfx.CRACK, 0.5f, 1.5f);
                            cut(kit, kit.cone(2.9, 65.0), rate);
                        });
                    }
                });
            }
            case 3 -> {
                // a shadow step: behind the nearest enemy, and a backstab
                LivingEntity target = kit.nearest(8.0, 60.0);
                if (target == null) {
                    kit.invulnerable(3);
                    kit.dash(facing, 0.9, 3, end -> {
                        kit.slash("drop", 2.4, 120.0, 25.0, 0.0, 1.9, 0.4, 0.0, 1.1, 0.1, 0.0);
                        cut(kit, kit.cone(2.8, 60.0), rate);
                    });
                    return;
                }
                kit.blink(kit.behind(target, 1.3), target);
                kit.later(2L, () -> {
                    kit.slash("drop", 2.4, 130.0, 20.0, 0.0, 1.8, 0.4, 0.0, 1.2, 0.1, 0.0);
                    kit.sound(target.getLocation(), Sfx.CRACK, 0.8f, 1.2f);
                    AttackService.MeleeHand hand = kit.nextHand();
                    if (kit.hitHand(hand, target, rate)) kit.confuse(target, 30.0);
                });
            }
            case 4 -> {
                // whirling blades: four cuts in a spin round the assassin, everyone close takes them
                kit.sound(feet, Sfx.WIND, 0.8f, 1.1f);
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 3L, () -> {
                        kit.slash("arc", 2.6, 300.0, 70.0, which % 2 == 0 ? 0.0 : 180.0, 0.4, 0.5, 0.0, 1.0, 0.07, 0.0);
                        kit.sound(kit.player.getLocation(), Sfx.WIND, 0.4f, 1.4f + which * 0.12f);
                        cut(kit, kit.around(kit.player.getLocation(), 3.0), rate);
                    });
                }
            }
            default -> {
                // an execution: behind the aimed enemy (or the nearest), 150% on it and a part of that on the others near
                LivingEntity target = kit.nearest(9.0, 70.0);
                if (target == null) {
                    kit.leap(facing, 0.6, 0.4, land -> {
                        kit.shockwave(land, 0.8, 3.0, 3, 1.0);
                        kit.slash("drop", 2.8, 150.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.3, 0.14, 0.0);
                        cut(kit, kit.around(land, 3.0), rate * 0.5);
                    });
                    return;
                }
                kit.blink(kit.behind(target, 1.2), target);
                kit.later(2L, () -> {
                    Location at = target.getLocation();
                    kit.slash("drop", 2.8, 160.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.35, 0.16, 0.0);
                    kit.shockwave(at, 0.8, 2.8, 3, 1.0);
                    kit.sound(at, Sfx.METAL_HEAVY, 0.7f, 1.3f);
                    AttackService.MeleeHand hand = kit.nextHand();
                    kit.hitHand(hand, target, rate);
                    for (LivingEntity other : kit.around(at, 2.8)) {
                        if (!other.equals(target)) kit.hitHand(hand, other, rate * 0.4);
                    }
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier() * boost(kit.frenzy());
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a shadow dash: six blocks through the enemies; nothing can hurt the assassin for 8 ticks
                Location from = feet.clone().add(0.0, 1.0, 0.0);
                List<LivingEntity> inWay = kit.corridor(from, facing, 6.5, 1.3);
                kit.invulnerable(8);
                kit.sound(feet, Sfx.WIND_BURST, 0.9f, 1.2f);
                kit.line(from, from.clone().add(facing.clone().multiply(6.0)), 0.2, 1.0, 1.4);
                kit.dash(facing, 1.2, 5, end -> {
                    kit.shockwave(end, 0.8, 2.4, 3, 1.0);
                    cut(kit, inWay, rate);
                });
            }
            case 1 -> {
                // a chain blink: behind the nearest enemy, three quick cuts
                LivingEntity target = kit.nearest(9.0, 80.0);
                if (target == null) {
                    kit.sound(feet, Sfx.WIND, 0.8f, 1.0f);
                    kit.slash("arc", 2.6, 150.0, 40.0, 0.0, 1.9, 0.4, 0.0, 1.1, 0.1, 0.0);
                    cut(kit, kit.cone(3.0, 70.0), rate);
                    return;
                }
                kit.blink(kit.behind(target, 1.3), target);
                for (int index = 0; index < 3; index++) {
                    int which = index;
                    kit.later(2L + index * 2L, () -> {
                        kit.slash(which == 1 ? "line" : "arc", 2.2, 120.0, 25.0 + which * 20.0, which % 2 == 0 ? 0.0 : 180.0, 1.7, 0.4, 0.0, 1.0, 0.07, 0.0);
                        kit.sound(target.getLocation(), Sfx.CRACK, 0.5f, 1.5f + which * 0.15f);
                        AttackService.MeleeHand hand = kit.nextHand();
                        kit.hitHand(hand, target, rate);
                    });
                }
            }
            case 2 -> {
                // a blade storm: a spin for 1.2 s that hits everyone round every 3 ticks, and takes 40% off what the assassin suffers
                kit.guard(0.4, 26);
                kit.sound(feet, Sfx.WIND_BURST, 0.9f, 0.9f);
                kit.every(25, age -> {
                    Location at = kit.player.getLocation();
                    if (age % 3 == 0) {
                        kit.slash("arc", 2.8, 320.0, 70.0, age % 2 == 0 ? 0.0 : 180.0, 0.4, 0.5, 0.0, 1.1, 0.07, 0.0);
                        kit.sound(at, Sfx.WIND, 0.35f, 1.3f);
                        cut(kit, kit.around(at, 3.4), rate);
                    }
                    kit.fx(at).flatRing(at.clone().add(0.0, 0.2 + (age % 6) * 0.25, 0.0), 2.6, 36, kit.palette(), 0.4 + 0.02 * age, 0.2, 0.9);
                    return false;
                });
            }
            case 3 -> {
                // a crossing step: through the enemy ahead to its far side, cutting all in the way, then back across
                LivingEntity target = kit.nearest(8.0, 70.0);
                Vector heading = target == null ? facing : target.getLocation().toVector().subtract(feet.toVector()).setY(0.0).normalize();
                double length = target == null ? 5.0 : Math.min(8.0, target.getLocation().distance(feet) + 2.5);
                Location from = feet.clone().add(0.0, 1.0, 0.0);
                kit.invulnerable(10);
                kit.sound(feet, Sfx.WIND_BURST, 0.9f, 1.3f);
                kit.line(from, from.clone().add(heading.clone().multiply(length)), 0.2, 1.0, 1.4);
                cut(kit, kit.corridor(from, heading, length, 1.4), rate);
                Location far = feet.clone().add(heading.clone().multiply(length));
                kit.blink(standable(kit, far), null);
                kit.later(5L, () -> {
                    Location here = kit.player.getLocation().add(0.0, 1.0, 0.0);
                    Vector back = heading.clone().multiply(-1.0);
                    kit.line(here, here.clone().add(back.clone().multiply(length)), 0.2, 1.0, 1.4);
                    kit.sound(here, Sfx.CRACK, 0.6f, 1.4f);
                    cut(kit, kit.corridor(here, back, length, 1.4), rate);
                    kit.blink(standable(kit, feet), null);
                });
            }
            default -> {
                // a phantom execution: afterimages gather, then a blink behind the nearest enemy and one great cut
                LivingEntity target = kit.nearest(12.0, 90.0);
                kit.invulnerable(10);
                kit.sound(feet, Sfx.MAGIC_DARK, 0.8f, 0.9f);
                for (int index = 0; index < 3; index++) {
                    int which = index;
                    kit.later(index * 2L, () -> kit.fx(kit.player.getLocation()).sphereShell(kit.player.getLocation().add(0.0, 1.0, 0.0), 0.5 + 0.25 * which, which * 0.8, 20, kit.palette(), 1.0));
                }
                kit.later(6L, () -> {
                    if (target == null || !target.isValid() || target.isDead()) {
                        kit.slash("drop", 3.0, 160.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.4, 0.16, 0.0);
                        cut(kit, kit.cone(3.2, 70.0), rate * 0.6);
                        return;
                    }
                    kit.blink(kit.behind(target, 1.2), target);
                    kit.later(2L, () -> {
                        Location at = target.getLocation();
                        kit.slash("drop", 3.0, 170.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.5, 0.18, 0.0);
                        kit.shockwave(at, 0.8, 3.2, 4, 1.1);
                        kit.sound(at, Sfx.METAL_HEAVY, 0.9f, 1.1f);
                        AttackService.MeleeHand hand = kit.nextHand();
                        if (kit.hitHand(hand, target, rate)) kit.confuse(target, 100.0);
                    });
                });
            }
        }
    }

    /** A spot the assassin can stand on at or near {@code at}: the spot itself when its two blocks are free, else one block up. */
    private Location standable(MoveKit kit, Location at) {
        Location spot = at.clone();
        if (!spot.getBlock().isPassable() || !spot.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) spot.add(0.0, 1.0, 0.0);
        return spot;
    }
}
