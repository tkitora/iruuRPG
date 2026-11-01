package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The warrior's combo with melee weapons: staying power and the weight of one blow. The blows are few and slow, each one a whole stroke on one
 * enemy or a short sweep; between them the warrior is guarded (takes less damage for a few ticks), gets shields from heavy 0 only (three turning round, each
 * cuts a damage by 40% and breaks) and heals a little with the heavy ones, so the fight can go on.
 *
 * <p>Normal: 1 a cleave (100%, 25% less damage taken for 0.6 s), 2 a shield bash (70%, slows), 3 a wide sweep (110%, 25% less damage taken for
 * 0.6 s), 4 a hammer drop on one enemy (130%, slows hard), 5 an earthbreaker (150% on all round, heals 3%, 40% less damage taken for
 * 0.75 s; no leap). Heavy: 0 a bulwark (three shields for 10 s, 40% less damage taken for 2 s; its own cooldown of 18 s; it is the only thing that gives shields), 1 a stomp (60% round, slows),
 * 2 a whirlwind (a spin for 1 s, 24% every 4 ticks round, 30% less damage taken, heals 1%), 3 a colossus strike (a moment of winding up with 60% less damage
 * taken, then 150% on the enemy ahead and a throw up), 4 a shield rush (a charge of 6 blocks under a 50% guard, 80% on all in the way and a push, 40% more
 * with a shield up).
 */
public final class WarriorMeleeCombo extends BaseCombo {

    static final String PLAN = "warrior_melee";

    public WarriorMeleeCombo() {
        super("warrior", AttackType.MELEE, new ComboSpec(PLAN,
                new double[]{0.67, 0.89, 1.12, 1.34, 1.56}, new int[]{1, 1, 1, 1, 1},
                new double[]{0.0, 0.6, 0.24, 1.5, 0.8, 1.2}, new int[]{360, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.stage()) {
            case 1 -> {
                kit.guard(0.25, 12);
                kit.slash("drop", 3.0, 125.0, 12.0, 0.0, 1.9, 0.3, 0.0, 1.3, 0.14, 0.0);
                kit.sound(feet, Sfx.METAL, 0.8f, 0.9f);
                for (LivingEntity target : kit.cone(3.4, 55.0)) {
                    if (kit.hit(target, rate)) kit.push(target, feet.toVector(), 0.2, 0.05);
                }
            }
            case 2 -> {
                // a shield bash: short and square, the target is stunned for a moment
                kit.slash("line", 2.4, 90.0, 0.0, 0.0, 1.7, 0.2, 0.0, 1.1, 0.1, 0.0);
                kit.sound(feet, Sfx.METAL_HEAVY, 0.7f, 1.1f);
                for (LivingEntity target : kit.cone(3.0, 50.0)) {
                    if (kit.hit(target, rate)) {
                        kit.slow(target, 60.0, 0.8);
                        kit.push(target, feet.toVector(), 0.25, 0.05);
                    }
                }
            }
            case 3 -> {
                kit.guard(0.25, 12);
                kit.slash("arc", 3.4, 170.0, 60.0, 0.0, 1.5, 0.4, 0.0, 1.35, 0.15, 0.0);
                kit.sound(feet, Sfx.WIND, 0.8f, 0.7f);
                kit.hitAll(kit.cone(4.0, 80.0), rate);
            }
            case 4 -> {
                // a hammer drop: the whole of it on one enemy
                LivingEntity target = kit.nearest(3.8, 45.0);
                kit.slash("drop", 3.0, 130.0, 8.0, 0.0, 1.9, 0.3, 0.0, 1.45, 0.17, 0.0);
                kit.sound(feet, Sfx.METAL_HEAVY, 0.9f, 0.8f);
                if (target == null) return;
                Location at = target.getLocation();
                kit.shockwave(at, 0.6, 2.4, 3, 1.1);
                if (kit.hit(target, rate)) kit.slow(target, 70.0, 1.0);
            }
            default -> {
                // the finisher: the earthbreaker, brought down on the spot (no leap)
                kit.guard(0.4, 15);
                kit.slash("drop", 3.2, 150.0, 5.0, 0.0, 1.8, 0.4, 0.0, 1.5, 0.18, 0.0);
                kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.7f);
                Location land = kit.player.getLocation();
                kit.shockwave(land, 1.0, 3.8, 5, 1.2);
                for (LivingEntity target : kit.around(land, 3.6)) {
                    if (kit.hit(target, rate)) kit.push(target, land.toVector(), 0.4, 0.2);
                }
                kit.heal(3.0);
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a bulwark: three shields round the warrior for 10 s, and less damage for a moment
                kit.bulwark();
                kit.guard(0.4, 40);
                kit.shockwave(feet, 0.8, 3.0, 4, 1.0);
                kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.9f);
            }
            case 1 -> {
                // a stomp: the ground round shakes, the enemies are slowed, a shield is picked up
                kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.6f);
                kit.shockwave(feet, 0.8, 4.2, 5, 1.2);
                for (LivingEntity target : kit.around(feet, 4.0)) {
                    if (kit.hit(target, rate)) kit.slow(target, 40.0, 2.0);
                }
            }
            case 2 -> {
                // a whirlwind: a spin for a second; everyone round is cut every 4 ticks
                kit.guard(0.3, 22);
                kit.sound(feet, Sfx.WIND_BURST, 0.9f, 0.8f);
                kit.every(21, age -> {
                    Location at = kit.player.getLocation();
                    if (age % 4 == 0) {
                        kit.slash("arc", 3.0, 320.0, 70.0, age % 8 == 0 ? 0.0 : 180.0, 0.4, 0.5, 0.0, 1.2, 0.08, 0.0);
                        kit.sound(at, Sfx.METAL, 0.4f, 0.9f);
                        kit.hitAll(kit.around(at, 3.5), rate);
                    }
                    kit.fx(at).flatRing(at.clone().add(0.0, 0.2 + (age % 6) * 0.25, 0.0), 3.0, 40, kit.palette(), 0.4 + 0.02 * age, 0.2, 1.0);
                    if (age == 20) kit.heal(1.0);
                    return false;
                });
            }
            case 3 -> {
                // a colossus strike: a moment of winding up behind the guard, then the whole of it on the enemy ahead
                kit.guard(0.6, 12);
                kit.sound(feet, Sfx.METAL, 0.8f, 0.6f);
                for (int tick = 0; tick < 8; tick++) {
                    double r = 2.4 - 1.6 * tick / 7.0;
                    kit.later(tick, () -> kit.fx(kit.player.getLocation()).flatRing(kit.player.getLocation().add(0.0, 0.2, 0.0), r, 28, kit.palette(), 0.4 + 0.07 * (2.4 - r), 0.2, 1.0));
                }
                kit.later(8L, () -> {
                    Location from = kit.player.getLocation();
                    LivingEntity target = kit.nearest(4.2, 45.0);
                    kit.slash("drop", 3.4, 140.0, 5.0, 0.0, 1.9, 0.3, 0.0, 1.6, 0.2, 0.0);
                    kit.sound(from, Sfx.METAL_HEAVY, 1.0f, 0.7f);
                    if (target == null) return;
                    Location at = target.getLocation();
                    kit.shockwave(at, 0.6, 3.0, 4, 1.2);
                    if (kit.hit(target, rate)) kit.lift(target, 0.45);
                });
            }
            case 4 -> {
                // a shield rush: under a guard, six blocks, everything in the way is struck and pushed aside; more with a shield up
                double boost = kit.shields() > 0 ? 1.4 : 1.0;
                Location from = feet.clone().add(0.0, 1.0, 0.0);
                kit.guard(0.5, 12);
                kit.sound(feet, Sfx.WIND_BURST, 1.0f, 0.8f);
                kit.line(from, from.clone().add(facing.clone().multiply(6.0)), 0.2, 1.0, 1.5);
                for (LivingEntity target : kit.corridor(from, facing, 6.5, 1.6)) {
                    if (kit.hit(target, rate * boost)) kit.push(target, feet.toVector(), 0.8, 0.2);
                }
                kit.dash(facing, 1.2, 5, end -> {
                    kit.shockwave(end, 0.8, 3.0, 4, 1.1);
                    kit.sound(end, Sfx.METAL_HEAVY, 0.9f, 0.9f);
                });
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a slam that splits the ground in three fissures fanned out ahead (-25, 0, 25 degrees, 7 blocks); each enemy on them is struck once and thrown up a little. */
    private void sixth(MoveKit kit, double rate) {
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing().clone().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) return;
        facing.normalize();
        kit.guard(0.3, 10);
        kit.slash("drop", 3.0, 120.0, 5.0, 0.0, 1.6, 0.4, 0.0, 1.4, 0.16, 0.0);
        kit.sound(feet, Sfx.METAL_HEAVY, 1.0f, 0.55f);
        java.util.Set<org.bukkit.entity.LivingEntity> struck = new java.util.HashSet<>();
        Location from = feet.clone().add(0.0, 0.5, 0.0);
        for (int side = -1; side <= 1; side++) {
            Vector way = facing.clone().rotateAroundY(Math.toRadians(25.0 * side));
            for (int along = 1; along <= 6; along++) {
                Location at = feet.clone().add(way.clone().multiply(along * 1.15)).add(0.0, 0.2, 0.0);
                kit.later(along, () -> kit.fx(at).flatRing(at, 0.6, 12, kit.palette(), 0.5, 0.2, 1.0));
            }
            for (org.bukkit.entity.LivingEntity target : kit.corridor(from, way, 7.0, 1.2)) {
                if (struck.add(target) && kit.hit(target, rate)) kit.lift(target, 0.35);
            }
        }
    }
}
