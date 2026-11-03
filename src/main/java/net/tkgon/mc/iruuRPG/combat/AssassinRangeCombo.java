package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The assassin's combo with a ranged weapon: the shadow knives (t198, {@link AssassinKnives}; the shadow bow of t168 is gone). Six knives hover in
 * a fan behind the assassin and every attack sends some at the enemy aimed at (the one nearest the line of sight): many quick hits, sharp sounds.
 * Normal: 1 two knives (35% x2), 2 three knives crossing at the aim (30% x3, through all in their way), 3 a knife circling the enemy aimed at,
 * cutting round it six times (15% x6), 4 five knives fanned out (25% x5), 5 all six knives into the enemy aimed at, one after another (30% x6,
 * confused {@code n5-confusion} and bleeding {@code n5-bleed} of the damage). Drawn in the weapon's colors.
 *
 * <p>The skills are the knives' own ({@link AssassinKnives}); the combo has no heavy attacks since t168.
 */
public final class AssassinRangeCombo extends BaseCombo {

    static final String PLAN = "assassin_range";

    public AssassinRangeCombo() {
        super("assassin", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.35, 0.3, 0.15, 0.25, 0.3}, new int[]{2, 3, 6, 5, 6},
                new double[]{0.25, 0.2, 0.15, 0.2, 1.5, 0.6}, new int[]{140, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public int normalGapTicks() {
        return 8;
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        AssassinKnives.hover(kit);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        double reach = kit.number(PLAN, "aim-range", 28.0);
        LivingEntity target = AssassinKnives.aimed(kit, reach);
        switch (step.stage()) {
            case 1 -> {
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 2L, () -> AssassinKnives.send(kit, 2 + which, AssassinKnives.goal(kit, target, reach), 2.0, false,
                            victim -> AssassinKnives.cut(kit, victim, rate)));
                }
            }
            case 2 -> {
                // three knives from the ends and the middle of the fan, crossing at the aim
                Location goal = AssassinKnives.goal(kit, target, reach);
                int[] from = {0, AssassinKnives.FAN - 1, 2};
                for (int index = 0; index < Math.min(hits, from.length); index++) {
                    int which = from[index];
                    kit.later(index, () -> AssassinKnives.send(kit, which, goal, 2.5, true, victim -> AssassinKnives.cut(kit, victim, rate)));
                }
            }
            case 3 -> circle(kit, target, reach, rate, hits);
            case 4 -> {
                Location goal = AssassinKnives.goal(kit, target, reach);
                Location eye = kit.eye();
                Vector way = goal.toVector().subtract(eye.toVector());
                double length = Math.max(4.0, way.length());
                Vector straight = way.lengthSquared() < 1.0E-6 ? kit.axes()[0] : way.normalize();
                double spread = kit.number(PLAN, "fan-degrees", 24.0);
                for (int index = 0; index < hits; index++) {
                    double turn = hits == 1 ? 0.0 : Math.toRadians(-spread + 2.0 * spread * index / (hits - 1));
                    Location aim = eye.clone().add(straight.clone().rotateAroundY(turn).multiply(length));
                    int which = index;
                    kit.later(index / 2, () -> AssassinKnives.send(kit, which, aim, 2.0, false, victim -> AssassinKnives.cut(kit, victim, rate)));
                }
            }
            default -> {
                // all six knives into the enemy aimed at, one after another: confused and bleeding
                double confusion = kit.number(PLAN, "n5-confusion", 20.0) / Math.max(1, hits);
                double bleed = kit.number(PLAN, "n5-bleed", 0.2);
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 2L, () -> AssassinKnives.send(kit, which, AssassinKnives.goal(kit, target, reach), 1.5, false, victim -> {
                        double before = victim.getHealth();
                        if (!AssassinKnives.cut(kit, victim, rate)) return;
                        kit.confuse(victim, confusion);
                        AssassinKnives.bleedOf(kit, victim, before, bleed);
                    }));
                }
            }
        }
    }

    /** A knife sent to circle {@code target} (or the aim): it cuts the enemies round it {@code hits} times, 3 ticks apart, then fades. */
    private static void circle(MoveKit kit, LivingEntity target, double reach, double rate, int hits) {
        Location first = AssassinKnives.goal(kit, target, reach);
        Location from = AssassinKnives.launch(kit.player, 3);
        Vector way = first.toVector().subtract(from.toVector());
        ItemDisplay knife = WarriorSwords.spawn(from, way, AssassinKnives.SIZE);
        kit.sound(from, Sfx.KNIFE_THROW, 0.7f, 1.2f);
        kit.later(1L, () -> WarriorSwords.move(knife, first, way, AssassinKnives.SIZE, 3));
        int life = 4 + hits * 3;
        org.bukkit.Bukkit.getScheduler().runTaskLater(kit.plugin(), () -> WarriorSwords.remove(knife), life + 20L);
        kit.every(life + 1, age -> {
            if (age < 4) return false;
            Location center = target != null && target.isValid() && !target.isDead() ? target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0) : first;
            double angle = age * 0.7;
            Vector round = new Vector(Math.cos(angle), 0.15 * Math.sin(age * 0.5), Math.sin(angle));
            Location at = center.clone().add(round.clone().multiply(1.3));
            WarriorSwords.move(knife, at, new Vector(-round.getZ(), 0.0, round.getX()), AssassinKnives.SIZE, 1);
            kit.fx(at).line(center.clone().add(new Vector(Math.cos(angle - 0.7), 0.0, Math.sin(angle - 0.7)).multiply(1.3)), at, kit.palette(), 0.3, 1.0, 0.0, 0.8, 0.15, null, 1);
            if ((age - 4) % 3 == 0 && (age - 4) / 3 < hits) {
                kit.sound(at, Sfx.KNIFE_HIT, 0.4f, 1.5f);
                for (LivingEntity victim : kit.around(center, 1.6)) AssassinKnives.cut(kit, victim, rate);
            }
            if (age >= life) {
                WarriorSwords.vanish(kit, knife, round, 3);
                return true;
            }
            return false;
        });
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: the class's skills are its own (AssassinKnives).
    }
}
