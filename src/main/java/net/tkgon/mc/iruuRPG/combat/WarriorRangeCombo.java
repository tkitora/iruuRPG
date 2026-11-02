package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The warrior's combo with a ranged weapon: floating greatswords and chains (t168, {@link WarriorSkills}, {@link WarriorSwords}). Two greatswords
 * hover behind the warrior's shoulders while they fight, and every attack sends swords at the aim (where the warrior looks: the first enemy or
 * block, at most the weapon's range).
 *
 * <p>Normal: 1 a sword flies from behind the shoulder through everything in the way (75%), 2 two swords from both shoulders cross at the aim
 * (50% x2), 3 a sword turns flat at the aim, cutting round it (25% x4), 4 three swords fall round the aim (45% x3), 5 a great sword falls on the
 * aim (160% round it) and stays planted for 3 s, its chains binding the enemies near it to it ({@code finisher-bind-seconds} 3: they can move
 * only {@code finisher-bind-radius} 2 blocks from it). A bound enemy takes {@code bound-bonus} (30%) more from the swords.
 *
 * <p>The skills are the warrior's own ({@link WarriorSkills}); the combo has no heavy attacks since t168.
 */
public final class WarriorRangeCombo extends BaseCombo {

    static final String PLAN = "warrior_range";

    public WarriorRangeCombo() {
        super("warrior", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.75, 0.5, 0.25, 0.45, 1.6}, new int[]{1, 2, 4, 3, 1},
                new double[]{0.25, 1.3, 0.15, 1.2, 1.5, 0.7}, new int[]{150, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        WarriorSkills.lookSteel(kit);
        WarriorSkills.hover(kit);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        double reach = Math.max(6.0, kit.number(PLAN, "aim-range", 18.0));
        Location aim = WarriorSkills.aimWithin(kit, reach);
        switch (step.stage()) {
            case 1 -> {
                // a sword from behind the shoulder, straight through everything in the way
                Location from = WarriorSwords.launch(kit.player, 1);
                Vector way = aim.toVector().subtract(from.toVector());
                if (way.lengthSquared() < 1.0E-6) way = kit.axes()[0];
                kit.sound(from, Sfx.SWORD_FLY, 0.8f, 1.1f);
                WarriorSwords.fly(kit, from, from.clone().add(way.normalize().multiply(reach)), 1.8, 1.6, 1.0, victim -> WarriorSkills.swordHit(kit, victim, rate),
                        end -> kit.burst(end, 0.7));
            }
            case 2 -> {
                // two swords, one from each shoulder, cross at the aim
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 2L, () -> {
                        Location from = WarriorSwords.launch(kit.player, which & 1);
                        Vector way = aim.toVector().subtract(from.toVector());
                        if (way.lengthSquared() < 1.0E-6) return;
                        Location past = aim.clone().add(way.normalize().multiply(1.5));
                        kit.sound(from, Sfx.SWORD_FLY, 0.7f, 1.2f + 0.1f * which);
                        WarriorSwords.fly(kit, from, past, 1.7, 1.5, 1.0, victim -> WarriorSkills.swordHit(kit, victim, rate), null);
                    });
                }
                kit.later(hits * 2L + 6L, () -> kit.burst(aim, 1.2));
            }
            case 3 -> spinner(kit, aim, rate, hits);
            case 4 -> {
                // three swords fall round the aim
                Location ground = kit.ground(aim);
                for (int index = 0; index < hits; index++) {
                    double angle = Math.PI * 2.0 * index / hits + ThreadLocalRandom.current().nextDouble(0.6);
                    double spread = index == 0 ? 0.0 : 1.6;
                    Location spot = kit.ground(ground.clone().add(Math.cos(angle) * spread, 0.0, Math.sin(angle) * spread));
                    kit.later(index * 3L, () -> WarriorSwords.fall(kit, spot, 1.6, 6.0, 4, 8, land -> {
                        kit.sound(land, Sfx.SWORD_IMPACT, 0.6f, 1.1f);
                        kit.particle(org.bukkit.Particle.CRIT, land.clone().add(0.0, 0.3, 0.0), 5, 0.3, 0.1);
                        for (LivingEntity victim : kit.around(land, 1.5)) WarriorSkills.swordHit(kit, victim, rate);
                    }));
                }
            }
            default -> {
                // the finisher: a great sword falls on the aim and stays; its chains bind the enemies near it to it
                Location ground = kit.ground(aim);
                double radius = kit.number(PLAN, "finisher-radius", 2.6);
                int ticks = (int) Math.round(kit.number(PLAN, "finisher-bind-seconds", 3.0) * 20.0);
                double hold = kit.number(PLAN, "finisher-bind-radius", 2.0);
                kit.sound(ground, Sfx.SWORD_SUMMON, 0.8f, 0.8f);
                WarriorSwords.fall(kit, ground, 3.2, 9.0, 5, ticks, land -> {
                    kit.sound(land, Sfx.SWORD_GRAND, 1.0f, 1.0f);
                    kit.shockwave(land, 0.8, radius + 0.6, 5, 1.3);
                    kit.burst(land.clone().add(0.0, 0.5, 0.0), radius);
                    Location anchor = land.clone().add(0.0, 0.8, 0.0);
                    for (LivingEntity victim : kit.around(land, radius)) WarriorSkills.swordHit(kit, victim, rate);
                    for (LivingEntity victim : kit.around(land, radius + 1.5)) WarriorSwords.bind(kit, victim, anchor, hold, ticks, null);
                    kit.sound(land, Sfx.CHAIN_BIND, 0.8f, 1.0f);
                });
            }
        }
    }

    /** A sword turning flat at {@code aim}: everyone round it is cut {@code hits} times, 4 ticks apart. */
    private static void spinner(MoveKit kit, Location aim, double rate, int hits) {
        Location at = aim.clone().add(0.0, 0.2, 0.0);
        Location from = WarriorSwords.launch(kit.player, 0);
        kit.sound(from, Sfx.SWORD_FLY, 0.7f, 0.9f);
        org.bukkit.entity.ItemDisplay sword = WarriorSwords.spawn(from, at.toVector().subtract(from.toVector()), 1.7);
        kit.later(1L, () -> WarriorSwords.move(sword, at, at.toVector().subtract(from.toVector()), 1.7, 4));
        int life = 6 + hits * 4;
        kit.every(life + 1, age -> {
            if (age < 5) return false;
            double angle = age * 0.9;
            Vector blade = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
            WarriorSwords.move(sword, at, blade, 1.7, 1);
            kit.fx(at).flatRing(at, 1.7, 22, WarriorSwords.STEEL, 0.5, 0.4, 1.0);
            if ((age - 5) % 4 == 0 && (age - 5) / 4 < hits) {
                kit.sound(at, Sfx.SWORD_FLY, 0.4f, 1.5f);
                for (LivingEntity victim : kit.around(at, 1.9)) WarriorSkills.swordHit(kit, victim, rate);
            }
            if (age >= life) {
                WarriorSwords.vanish(kit, sword, blade, 4);
                return true;
            }
            return false;
        });
        org.bukkit.Bukkit.getScheduler().runTaskLater(kit.plugin(), () -> WarriorSwords.remove(sword), life + 20L);
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: the class's skills are its own (WarriorSkills).
    }

}
