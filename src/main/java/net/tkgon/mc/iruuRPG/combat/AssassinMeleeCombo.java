package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * The assassin's combo with melee weapons: mobility joined to skill. Every cut is struck with the hand whose turn it is (the two weapons of 固有0 take turns). The reach
 * is short (under 3 blocks), the hits few; what the assassin has is the way to the enemy and the way out: a step that closes the gap, and clones that step in to an enemy from
 * behind and strike for the assassin, so one enemy takes the whole of it. For a moment the other way: the whirling blades hit everyone round the assassin. The assassin never
 * teleports in the combo (a teleport throws the camera): the clones do the reaching. Always available with a favorite weapon (no node needed).
 *
 * <p>Normal: 1 a fang cross (two cuts, 50% x2), 2 a step in (a dash that stops at the first enemy it touches, then two cuts, 60% x2), 3 a shadow clone (a clone steps in from
 * behind the nearest enemy and strikes, 130%, confuses a little), 4 whirling blades (a spin of 4 cuts, 25% each on everyone round, 12 ticks), 5 an execution (two clones
 * strike the aimed enemy from both sides, 75% each, and the assassin cuts the others near for 40%).
 *
 * <p>The combo has no heavy attacks since t168 (the assassin's melee skills are its own since t161, {@link AssassinSkills}).
 *
 * <p>The class skill (SATUJIN, F) puts the assassin in a frenzy for some seconds: the blows are 20% stronger (35% when upgraded), the normal attacks come faster, a clone
 * strikes an enemy that is out of reach before a normal attack, every normal attack is followed by the cut of a shadow round the assassin (40%), and the status effects
 * (confusion) land with every hit.
 */
public final class AssassinMeleeCombo extends BaseCombo {

    static final String PLAN = "assassin_melee";

    public AssassinMeleeCombo() {
        super("assassin", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.36, 0.48, 1.2, 0.36, 1.68}, new int[]{2, 2, 1, 4, 1},
                new double[]{1.0, 1.0, 0.16, 0.6, 1.5, 1.3}, new int[]{160, 0, 0, 0, 0, 0}, 36));
    }

    /** One cut at {@code rate} on everyone in {@code targets}, struck with the hand whose turn it is. */
    private int cut(MoveKit kit, List<LivingEntity> targets, double rate) {
        AttackService.MeleeHand hand = kit.nextHand();
        int hit = 0;
        for (LivingEntity target : targets) if (kit.hitHand(hand, target, rate)) hit++;
        return hit;
    }

    /** The colours of the slashes while the item swap skill lasts: dark red, bright red, a hot core. */
    private static final java.util.List<Color> RED_BLACK_SLASH = java.util.List.of(Color.fromRGB(90, 0, 8), Color.fromRGB(230, 25, 30), Color.fromRGB(255, 120, 100));

    /** The look of the combo: steel and the weapon's element, or (during the item swap skill) red and black, in the dust and in the slashes. */
    private static void look(MoveKit kit) {
        if (kit.frenzy() > 0) {
            kit.palette(Clones.RED_BLACK);
            kit.slashColors(RED_BLACK_SLASH);
        } else {
            kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
            kit.slashColors(null);
        }
    }

    /** How much stronger the blows are in a frenzy (SATUJIN): 1 when there is none. */
    private static double boost(int frenzy) {
        return frenzy >= 2 ? 1.35 : frenzy == 1 ? 1.2 : 1.0;
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        // (only the normal attacks show the two crossing cuts of a blow with the off hand; the heavy attacks do not)
        kit.offHandVisuals(true);
        int frenzy = kit.frenzy();
        // the clones of the item swap skill copy this attack
        if (frenzy > 0) AssassinClones.copyAttack(kit, -1);
        if (frenzy == 0) {
            normalBody(kit, step, 1.0);
            return;
        }
        // in a frenzy every blow is stronger; a clone strikes an enemy that is out of reach first; and a shadow of the assassin cuts again a moment later round the place it stands
        double boost = boost(frenzy);
        int stage = step.stage();
        LivingEntity far = stage == 3 || stage == 5 ? null : kit.nearest(9.0, 90.0);
        if (far != null && far.getLocation().distanceSquared(kit.player.getLocation()) > 6.25) {
            double reach = step.damageMultiplier() * 0.35 * boost;
            kit.clone(far, kit.behindAngle(far), 1, 1, false, index -> {
                if (far.isValid() && !far.isDead()) kit.hitHand(kit.nextHand(), far, reach);
            });
        }
        normalBody(kit, step, boost);
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
        look(kit);
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
                // a step in: a short step (not a dash) that ends at the first enemy it touches (a moment nothing can hurt the assassin), then two cuts
                LivingEntity ahead = kit.nearest(7.0, 45.0);
                Vector heading = ahead == null ? facing : ahead.getLocation().toVector().subtract(feet.toVector());
                kit.invulnerable(3);
                kit.sound(feet, Sfx.WIND, 0.7f, 1.3f);
                kit.dashToContact(heading, kit.number(PLAN, "step-dash-power", 0.5), 3, 1.7, (end, touched) -> {
                    for (int index = 0; index < hits; index++) {
                        int which = index;
                        kit.later(index * 2L, () -> {
                            kit.slash(which == 0 ? "drop" : "line", 2.3, 120.0, 30.0 + which * 25.0, which == 0 ? 0.0 : 180.0, 1.9, 0.4, 0.0, 1.05, 0.09, 0.0);
                            kit.sound(end, Sfx.WIND_BURST, 0.5f, 1.5f);
                            cut(kit, kit.cone(2.9, 65.0), rate);
                        });
                    }
                });
            }
            case 3 -> {
                // a shadow clone: it steps in from behind the nearest enemy and strikes; the assassin stays where they are
                LivingEntity target = kit.nearest(8.0, 60.0);
                if (target == null) {
                    kit.invulnerable(3);
                    kit.dash(facing, 0.9, 3, end -> {
                        kit.slash("drop", 2.4, 120.0, 25.0, 0.0, 1.9, 0.4, 0.0, 1.1, 0.1, 0.0);
                        cut(kit, kit.cone(2.8, 60.0), rate);
                    });
                    return;
                }
                kit.sound(feet, Sfx.WIND, 0.6f, 1.2f);
                kit.clone(target, kit.behindAngle(target), 1, 1, false, index -> {
                    kit.slash("drop", 2.4, 130.0, 20.0, 0.0, 1.8, 0.4, 0.0, 1.2, 0.1, 0.0);
                    kit.sound(target.getLocation(), Sfx.WIND_BURST, 0.8f, 1.2f);
                    if (kit.hitHand(kit.nextHand(), target, rate)) kit.confuse(target, 30.0);
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
                // an execution: two clones strike the aimed enemy from both sides, the assassin cuts the others near it
                LivingEntity target = kit.nearest(9.0, 70.0);
                if (target == null) {
                    kit.leap(facing, 0.6, 0.4, land -> {
                        kit.shockwave(land, 0.8, 3.0, 3, 1.0);
                        kit.slash("drop", 2.8, 150.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.3, 0.14, 0.0);
                        cut(kit, kit.around(land, 3.0), rate * 0.5);
                    });
                    return;
                }
                double behind = kit.behindAngle(target);
                for (int side = -1; side <= 1; side += 2) {
                    kit.clone(target, behind + side * Math.toRadians(95.0), 1, 1, false, index -> {
                        Location at = target.getLocation();
                        kit.slash("drop", 2.8, 160.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.35, 0.16, 0.0);
                        kit.sound(at, Sfx.WIND_BURST, 0.7f, 1.3f);
                        kit.hitHand(kit.nextHand(), target, rate * 0.5);
                    });
                }
                kit.later(7L, () -> {
                    Location at = target.getLocation();
                    kit.shockwave(at, 0.8, 2.8, 3, 1.0);
                    AttackService.MeleeHand hand = kit.nextHand();
                    for (LivingEntity other : kit.around(at, 2.8)) {
                        if (!other.equals(target)) kit.hitHand(hand, other, rate * 0.4);
                    }
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: the class's skills are its own (AssassinSkills).
    }

}
