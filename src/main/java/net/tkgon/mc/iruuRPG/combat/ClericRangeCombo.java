package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * The cleric's combo with a ranged weapon (the staff): light that hurts the enemies a little and mends the allies (t168, {@link ClericSkills};
 * t198: bells, feathers and halos instead of the lightning and beams that looked like the mage's). Every attack that hits adds a verse (the bell
 * of 大 spends them). Drawn in the weapon's colors, the gold of holy light as a touch.
 *
 * <p>Normal: 1 a drop of light lobbed onto the aim (60% round where it falls; the allies near it are healed {@code shot-heal} 0.5%), 2 a bell
 * rung: a wave of light ahead (40% to all in it), 3 three feathers of light that seek the enemies (35% x3), 4 a halo widening round the cleric
 * (45%; the allies in it are healed {@code ring-heal} 1%), 5 a bell of light falls on the aim and rings (130% round it; the allies near it are
 * healed {@code strike-heal} 2%, the cleric {@code strike-self-heal} 1%).
 *
 * <p>The skills are the cleric's own ({@link ClericSkills}); the combo has no heavy attacks since t168.
 */
public final class ClericRangeCombo extends BaseCombo {

    static final String PLAN = "cleric_range";

    public ClericRangeCombo() {
        super("healer", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.6, 0.4, 0.35, 0.45, 1.3}, new int[]{1, 1, 3, 1, 1},
                new double[]{0.2, 0.15, 0.4, 1.4, 0.3, 0.7}, new int[]{160, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        boolean[] versed = {false};
        java.util.function.Consumer<LivingEntity> landed = victim -> {
            if (versed[0]) return;
            versed[0] = true;
            ClericSkills.verse(kit.player);
        };
        switch (step.stage()) {
            case 1 -> {
                // a drop of light lobbed onto the aim; the allies near where it falls are healed a little
                Location hand = ComboParts.hand(kit);
                Location aim = kit.ground(within(kit, 18.0)).add(0.0, 0.2, 0.0);
                kit.sound(hand, Sfx.HOLY_FEATHER, 0.6f, 1.3f);
                kit.lob(hand, aim, 8, 2.2, end -> {
                    kit.sound(end, Sfx.HOLY_BELL, 0.35f, 1.8f);
                    kit.fx(end).sphereShell(end.clone().add(0.0, 0.3, 0.0), 0.6, 0.0, 12, kit.palette(), 1.0);
                    kit.fx(end).flatRing(end, 1.6, 18, ClericSkills.HOLY, 0.8, 0.1, 0.9);
                    for (LivingEntity victim : kit.around(end, 1.8)) {
                        if (kit.hit(victim, rate)) landed.accept(victim);
                    }
                    kit.healAllies(end, 3.0, kit.number(PLAN, "shot-heal", 0.5));
                });
            }
            case 2 -> {
                // a bell rung: a wave of light ahead
                Location feet = kit.player.getLocation();
                Vector facing = kit.facing();
                double reach = kit.number(PLAN, "wave-reach", 6.0);
                kit.sound(feet, Sfx.HOLY_BELL, 0.7f, 1.3f);
                ClericSkills.drawBell(kit, ComboParts.hand(kit).add(0.0, 0.6, 0.0), 0.35, 0.2);
                for (int step2 = 0; step2 < 5; step2++) {
                    int which = step2;
                    kit.later(step2, () -> arc(kit, feet, facing, reach * (which + 1) / 5.0, 50.0));
                }
                for (LivingEntity victim : kit.cone(reach, 50.0)) {
                    if (kit.hit(victim, rate)) landed.accept(victim);
                }
            }
            case 3 -> {
                // three feathers of light from over the shoulders, seeking the enemies
                Location aim = within(kit, 18.0);
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 3L, () -> {
                        Vector[] axes = kit.axes();
                        double side = (which - (hits - 1) / 2.0) * 0.7;
                        Location start = kit.eye().add(axes[1].clone().multiply(side)).add(0.0, 0.5, 0.0).subtract(axes[0].clone().multiply(0.3));
                        Vector heading = aim.toVector().subtract(start.toVector()).normalize().add(new Vector(0.0, 0.3, 0.0)).normalize();
                        kit.sound(start, Sfx.HOLY_FEATHER, 0.5f, 1.4f + 0.1f * which);
                        kit.homing(start, heading, 1.0, 40, rate, 12.0, end -> {
                            kit.particle(org.bukkit.Particle.END_ROD, end, 4, 0.2, 0.02);
                            kit.fx(end).sphereShell(end, 0.35, 0.0, 8, ClericSkills.HOLY, 0.9);
                            // (the feather struck an enemy when one is where it ended)
                            List<LivingEntity> near = kit.around(end, 1.2);
                            if (!near.isEmpty()) landed.accept(near.get(0));
                        });
                    });
                }
            }
            case 4 -> {
                // a halo widening round the cleric: the enemies in it are struck, the allies healed
                Location feet = kit.player.getLocation();
                double radius = kit.number(PLAN, "ring-radius", 4.0);
                kit.sound(feet, Sfx.HOLY_BLESS, 0.7f, 1.5f);
                kit.shockwave(feet, 0.6, radius, 5, 1.1);
                kit.later(2L, () -> kit.fx(feet).flatRing(feet.clone().add(0.0, 0.3, 0.0), radius * 0.8, 30, ClericSkills.HOLY, 0.9, 0.1, 1.0));
                for (LivingEntity victim : kit.around(feet, radius)) {
                    if (kit.hit(victim, rate)) landed.accept(victim);
                }
                kit.healAllies(feet, radius, kit.number(PLAN, "ring-heal", 1.0));
            }
            default -> {
                // a bell of light falls on the aim and rings; the allies near it are healed
                Location aim = kit.ground(within(kit, 18.0));
                kit.sound(ComboParts.hand(kit), Sfx.HOLY_CHANT, 0.6f, 1.4f);
                kit.every(7, age -> {
                    double drop = 1.0 - age / 6.0;
                    if (age < 6) {
                        ClericSkills.drawBell(kit, aim.clone().add(0.0, 1.6 + 5.0 * drop, 0.0), 0.8, 0.0);
                        return false;
                    }
                    kit.sound(aim, Sfx.HOLY_BELL, 1.0f, 0.9f);
                    kit.shockwave(aim, 0.6, 3.0, 5, 1.3);
                    ClericSkills.drawBell(kit, aim.clone().add(0.0, 1.6, 0.0), 0.9, 0.3);
                    for (LivingEntity victim : kit.around(aim, 3.0)) {
                        if (kit.hit(victim, rate)) landed.accept(victim);
                    }
                    kit.healAllies(aim, 4.0, kit.number(PLAN, "strike-heal", 2.0));
                    kit.heal(kit.number(PLAN, "strike-self-heal", 1.0));
                    return true;
                });
            }
        }
    }

    /** Where the cleric aims, at most {@code reach} blocks away. */
    private static Location within(MoveKit kit, double reach) {
        Location eye = kit.eye();
        Location aim = kit.aim();
        Vector way = aim.toVector().subtract(eye.toVector());
        return way.length() > reach ? eye.clone().add(way.normalize().multiply(reach)) : aim;
    }

    /** An arc of light of {@code radius} ahead of {@code feet}, {@code halfAngle} degrees each side (one step of the bell's wave). */
    private static void arc(MoveKit kit, Location feet, Vector facing, double radius, double halfAngle) {
        net.tkgon.mc.iruuRPG.vfx.Fx fx = kit.fx(feet);
        double base = Math.atan2(facing.getZ(), facing.getX());
        int points = (int) Math.max(6, radius * 5);
        for (int index = 0; index <= points; index++) {
            double angle = base + Math.toRadians(-halfAngle + 2.0 * halfAngle * index / points);
            fx.dust(feet.getX() + Math.cos(angle) * radius, feet.getY() + 0.9, feet.getZ() + Math.sin(angle) * radius, kit.palette(), 0.5 + 0.5 * index / points, 1.0);
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: the class's skills are its own (ClericSkills).
    }

}
