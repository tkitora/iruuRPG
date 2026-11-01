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
 * strike the aimed enemy from both sides, 75% each, and the assassin cuts the others near for 40%). Heavy: 0 a grey clone, a decoy (it is flung the way the assassin faces and stands there for 10 s, striking the nearest enemy near it every half a second for 20%;
 * cooldown 8 s; during the item swap skill it is a flat dash to the front instead (the clones of the skill follow it, once in half a second), 3% to everyone along the way, cooldown 2 ticks; 強1 again, up to twice, made one more clone and then sent the clones charging forward, 15% each, by the old keys (not as 中 of the redesign); every heavy attack
 * while one stands, other than heavy 0 and 1, is made by the clones too, for 30% of its damage), 1 a shuriken (a small bright head with a thin streak, fast, 12 blocks in all; each clone of the item swap skill throws one too) that bounces up to three times, half the damage lost at each bounce
 * (three of them in the item swap skill), 2 a blade storm (a spin for 1.2 s, 16% every 3 ticks on
 * everyone round, takes 40% less damage meanwhile), 3 crossing clones (two clones run through the line ahead from both ends, 60% on all in the way each), 4 a phantom execution
 * (afterimages, then three clones close on the nearest enemy: 50% each and confusion).
 *
 * <p>The skills of the redesign of 2026-11-02 ({@link SlotMoves}): 小 the crossing clones (heavy 3), 中 the decoy (0), 大 the blade storm (2), util the
 * shadow run that leaves a decoy where it started (5).
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

    /**
     * Heavy 0 during the item swap skill: a dash to the front, flat (it never lifts the assassin), that cuts everyone along the way it went for {@code swap-dash-rate} (3%) of the
     * weapon's damage where it ends. Its cooldown is 2 ticks (kept in {@link AttackService#heavyAttack}); nothing makes a clone.
     */
    private void swapDash(MoveKit kit) {
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        Location from = feet.clone().add(0.0, 1.0, 0.0);
        double power = kit.number(PLAN, "swap-dash-power", 1.2);
        int ticks = (int) Math.round(kit.number(PLAN, "swap-dash-ticks", 5.0));
        double rate = kit.number(PLAN, "swap-dash-rate", 0.03);
        kit.sound(feet, Sfx.WIND_BURST, 0.7f, 1.4f);
        kit.line(from, from.clone().add(facing.clone().multiply(power * ticks)), 0.2, 1.0, 1.4);
        // the clones of the skill go the same way (once in half a second)
        AssassinClones.followDash(kit, facing.clone().multiply(power * ticks));
        kit.dash(facing, power, ticks, end -> {
            double length = Math.hypot(end.getX() - feet.getX(), end.getZ() - feet.getZ()) + 0.5;
            kit.shockwave(end, 0.8, 2.0, 3, 1.0);
            cut(kit, kit.corridor(from, facing, length, 1.3), rate);
        });
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        look(kit);
        int frenzy = kit.frenzy();
        double rate = step.damageMultiplier() * boost(frenzy);
        // during the item swap skill heavy 0 is a dash, not a clone (and is no attack for the clones of the skill to copy)
        if (step.heavyIndex() == 0 && frenzy > 0) {
            swapDash(kit);
            return;
        }
        if (frenzy > 0) AssassinClones.copyAttack(kit, step.heavyIndex());
        // every heavy attack (other than heavy 0 and 1) while a clone stands is made by the clones too, for 30% of its damage (no limit: the redesign of 2026-11-02)
        if (step.heavyIndex() >= 2 && AssassinClones.takeMimic(kit.player)) AssassinClones.mimic(kit, step.heavyIndex(), rate);
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a grey clone, a decoy, is flung the way the assassin faces and stands there (10 s), striking the nearest enemy near it every half a second
                // (decoy-hit-rate, 20%); by the old keys 強1 again made one more, and again sent them charging
                Location at = kit.ground(feet.clone().add(facing.clone().multiply(kit.number(PLAN, "stand-clone-distance", 5.0))));
                double yaw = Math.toDegrees(Math.atan2(-facing.getX(), facing.getZ()));
                int life = (int) Math.round(kit.number(PLAN, "stand-clone-seconds", 10.0) * 20.0);
                AssassinClones.standDecoy(kit, at, yaw, life, rate * kit.number(PLAN, "decoy-hit-rate", 0.2),
                        (int) Math.round(kit.number(PLAN, "decoy-hit-ticks", 10.0)), kit.number(PLAN, "decoy-reach", 3.5));
                AssassinClones.beginCast(kit);
                kit.sound(at, Sfx.WIND, 0.6f, 1.6f);
            }
            case 1 -> {
                // a shuriken that bounces up to three times (off blocks, and from enemy to enemy), losing half of its damage at each bounce; in a frenzy three fly, to the front and a little to each side
                kit.sound(feet, Sfx.WIND, 0.8f, 1.5f);
                int bounces = (int) Math.round(kit.number(PLAN, "shuriken-bounces", 3.0));
                double decay = Math.max(0.0, Math.min(0.95, kit.number(PLAN, "shuriken-decay", 0.5)));
                Vector[] axes = kit.axes();
                int count = frenzy > 0 ? 3 : 1;
                for (int index = 0; index < count; index++) {
                    double turn = Math.toRadians(count == 1 ? 0.0 : 22.0 * (index - 1));
                    Vector direction = axes[0].clone().multiply(Math.cos(turn)).add(axes[1].clone().multiply(Math.sin(turn))).normalize();
                    Location start = kit.eye().add(direction.clone().multiply(0.9)).subtract(0.0, 0.4, 0.0);
                    Blades.bounce(kit, start, direction, kit.number(PLAN, "shuriken-speed", 2.0), bounces, decay, rate, kit.number(PLAN, "shuriken-range", 12.0));
                }
            }
            case 2 -> {
                // a blade storm: a spin for 0.85 s (0.7 of the earlier time) that hits everyone round every 3 ticks, and takes 40% off what the assassin suffers
                kit.guard(0.4, 19);
                kit.sound(feet, Sfx.WIND_BURST, 0.9f, 0.9f);
                kit.every(18, age -> {
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
                // crossing clones: two of them run through the line ahead from both ends, cutting everything on the way
                LivingEntity target = kit.nearest(8.0, 70.0);
                Vector heading = target == null ? facing : target.getLocation().toVector().subtract(feet.toVector()).setY(0.0).normalize();
                double length = target == null ? 5.0 : Math.min(8.0, target.getLocation().distance(feet) + 2.5);
                Location from = feet.clone().add(0.0, 1.0, 0.0);
                Location near = feet.clone();
                Location far = feet.clone().add(heading.clone().multiply(length));
                kit.sound(feet, Sfx.WIND_BURST, 0.9f, 1.3f);
                kit.line(from, from.clone().add(heading.clone().multiply(length)), 0.2, 1.0, 1.4);
                kit.cloneRun(near, far, 5, tick -> {
                    if (tick == 2) cut(kit, kit.corridor(from, heading, length, 1.4), rate);
                });
                kit.later(4L, () -> {
                    kit.cloneRun(far, near, 5, tick -> {
                        if (tick == 2) {
                            kit.sound(far, Sfx.WIND, 0.6f, 1.4f);
                            cut(kit, kit.corridor(from.clone().add(heading.clone().multiply(length)), heading.clone().multiply(-1.0), length, 1.4), rate);
                        }
                    });
                });
            }
            case 4 -> {
                // a phantom execution: afterimages gather, then three clones close on the nearest enemy
                LivingEntity target = kit.nearest(12.0, 90.0);
                kit.invulnerable(10);
                kit.sound(feet, Sfx.WIND_BURST, 0.8f, 0.9f);
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
                    double behind = kit.behindAngle(target);
                    boolean[] confused = {false};
                    for (int index = 0; index < 2; index++) {
                        int which = index;
                        kit.later(index * 2L, () -> kit.clone(target, behind + (which == 0 ? -1 : 1) * Math.toRadians(80.0), 1, 1, false, blow -> {
                            Location at = target.getLocation();
                            kit.slash("drop", 3.0, 170.0, 10.0, 0.0, 1.8, 0.4, 0.0, 1.5, 0.18, 0.0);
                            kit.shockwave(at, 0.8, 3.2, 4, 1.1);
                            kit.sound(at, Sfx.WIND_BURST, 0.9f, 1.1f);
                            if (kit.hitHand(kit.nextHand(), target, rate / 2.0) && !confused[0]) {
                                confused[0] = true;
                                kit.confuse(target, 100.0);
                            }
                        }));
                    }
                });
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /**
     * Skill 6 (t138): the assassin runs 7 blocks ahead as a shadow (nothing can hurt it on the way), cutting every enemy it passes once; they are
     * confused. A grey clone, a decoy, stays where it started (util-decoy-seconds, 10 s: util of the redesign of 2026-11-02).
     */
    private void sixth(MoveKit kit, double rate) {
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing().clone().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) return;
        facing.normalize();
        AssassinClones.standOne(kit, kit.ground(feet), Math.toDegrees(Math.atan2(-facing.getX(), facing.getZ())),
                (int) Math.round(kit.number(PLAN, "util-decoy-seconds", 10.0) * 20.0));
        Location from = feet.clone().add(0.0, 1.0, 0.0);
        java.util.List<org.bukkit.entity.LivingEntity> passed = kit.corridor(from, facing, 7.0, 1.5);
        kit.invulnerable(10);
        kit.sound(feet, Sfx.WIND_BURST, 1.0f, 1.4f);
        kit.line(from, from.clone().add(facing.clone().multiply(7.0)), 0.1, 0.9, 1.0);
        kit.dash(facing, 1.4, 5, end -> {
            kit.sound(end, Sfx.METAL, 0.8f, 1.5f);
            kit.slash("arc", 2.4, 200.0, 20.0, 180.0, 0.4, 0.3, 0.0, 1.1, 0.12, 0.0);
            for (org.bukkit.entity.LivingEntity victim : passed) {
                if (kit.hit(victim, rate)) kit.confuse(victim, 60.0);
            }
        });
    }
}
