package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The warrior's combo with a ranged weapon: the great bow (t198, {@link WarriorSkills}; the floating greatswords of t168 went to the assassin's
 * knives). Not the ranger's many arrows: few heavy ones, the first blow from afar and the time of the draw. Every arrow has the bow's bonus
 * ({@link WarriorSkills#arrowHit}: an unhurt enemy and a far one are hit harder); the attacks are farther apart than the other combos'
 * ({@code normal-gap-ticks} 14). Drawn in the weapon's colors.
 *
 * <p>Normal: 1 a heavy arrow (90%), 2 two arrows one after the other (55% x2), 3 an arrow through everything in the way (100%), 4 three arrows
 * fanned out (40% x3), 5 a draw of {@code finisher-draw-ticks} (15; slowed and guarded) and a mighty shot through everything in the way (200%)
 * that bursts where it stops ({@code finisher-burst-share} 25% of it round it).
 *
 * <p>The skills are the warrior's own ({@link WarriorSkills}); the combo has no heavy attacks since t168.
 */
public final class WarriorRangeCombo extends BaseCombo {

    static final String PLAN = "warrior_range";

    public WarriorRangeCombo() {
        super("warrior", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.9, 0.55, 1.0, 0.4, 2.0}, new int[]{1, 2, 1, 3, 1},
                new double[]{0.25, 1.3, 0.15, 1.2, 1.5, 0.7}, new int[]{150, 0, 0, 0, 0, 0}, 44));
    }

    @Override
    public int normalGapTicks() {
        return 14;
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.rippleLines(true);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        double reach = Math.max(8.0, kit.number(PLAN, "aim-range", 24.0));
        switch (step.stage()) {
            case 1 -> shot(kit, reach, rate, 0.0, false, 1.2);
            case 2 -> {
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 4L, () -> shot(kit, reach, rate, 0.0, false, 1.0 + 0.1 * which));
                }
            }
            case 3 -> shot(kit, reach, rate, 0.0, true, 1.5);
            case 4 -> {
                double spread = kit.number(PLAN, "fan-degrees", 9.0);
                for (int index = 0; index < hits; index++) {
                    double turn = (index - (hits - 1) / 2.0) * spread;
                    kit.later(index, () -> shot(kit, reach, rate, turn, false, 1.0));
                }
            }
            default -> finisher(kit, reach, rate);
        }
    }

    /** One heavy arrow at the aim (turned {@code turn} degrees aside), into the first enemy or through all ({@code pierce}); {@code weight} its look. */
    private static void shot(MoveKit kit, double reach, double rate, double turn, boolean pierce, double weight) {
        Location from = WarriorSkills.bow(kit);
        Vector way = WarriorSkills.toAim(kit, from, reach);
        if (turn != 0.0) way.rotateAroundY(Math.toRadians(turn));
        kit.sound(from, Sfx.GREATBOW_LOOSE, 0.6f, 1.0f + 0.1f * (float) Math.random());
        WarriorSkills.heavyArrow(kit, from, way, 1.8, reach, weight, pierce, victim -> {
            if (WarriorSkills.arrowHit(kit, victim, rate)) kit.push(victim, kit.player.getLocation().toVector(), 0.15 * weight, 0.05);
        }, null);
    }

    /** The finisher: a short draw, then a mighty arrow through everything in the way that bursts where it stops. */
    private static void finisher(MoveKit kit, double reach, double rate) {
        int ticks = Math.max(2, (int) Math.round(kit.number(PLAN, "finisher-draw-ticks", 15.0)));
        double share = kit.number(PLAN, "finisher-burst-share", 0.25);
        double radius = kit.number(PLAN, "finisher-burst-radius", 2.5);
        kit.player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 1, false, false, false));
        kit.guard(kit.number(PLAN, "draw-guard", 0.25), ticks);
        kit.sound(kit.player.getLocation(), Sfx.GREATBOW_DRAW, 0.9f, 0.8f);
        kit.every(ticks + 1, age -> {
            WarriorSkills.drawLook(kit, Math.min(1.0, (double) age / ticks));
            if (age < ticks) return false;
            Location from = WarriorSkills.bow(kit);
            Vector way = WarriorSkills.toAim(kit, from, reach * 1.3);
            kit.sound(from, Sfx.GREATBOW_LOOSE, 1.1f, 0.75f);
            WarriorSkills.heavyArrow(kit, from, way, 2.2, reach * 1.3, 2.0, true, victim -> {
                if (WarriorSkills.arrowHit(kit, victim, rate)) kit.push(victim, kit.player.getLocation().toVector(), 0.4, 0.1);
            }, end -> {
                if (kit.rippleLines()) kit.lineShockwave(end, 0.5, radius, 4);
                else kit.shockwave(end, 0.5, radius, 4, 1.2);
                kit.particle(Particle.EXPLOSION_NORMAL, end, 8, 0.4, 0.05);
                for (LivingEntity victim : kit.around(end, radius)) WarriorSkills.arrowHit(kit, victim, rate * share);
            });
            return true;
        });
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: the class's skills are its own (WarriorSkills).
    }
}
