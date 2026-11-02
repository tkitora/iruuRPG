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
 * <p>The skills are the warrior's own ({@link WarriorSkills}); the heavy attacks below are what they were before t168.
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
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.3));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                // a war cry: what is near is pushed away and hurt for 2.5 seconds
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.ENTITY_RAVAGER_ROAR, 0.9f, 1.2f);
                ComboParts.sigil(kit, "warrior_cry", at, 4.0, rate, 50, 10, here -> {
                    for (LivingEntity victim : kit.around(here, 4.0)) kit.push(victim, here.toVector(), 0.35, 0.1);
                });
            }
            case 1 -> {
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                kit.sound(start, Sfx.WIND, 1.0f, 0.6f);
                kit.slash("drop", 3.0, 120.0, 20.0, 0.0, 1.8, 0.3, 0.0, 1.5, 0.16, 0.0);
                ComboParts.pierce(kit, start, direction, 12.0, 1.2, rate);
            }
            case 2 -> ComboParts.rain(kit, target, 3.4, 8, rate, 1.6, Sound.ITEM_TRIDENT_HIT);
            case 3 -> {
                // a chain: the target is hooked and dragged to the warrior, then cut
                LivingEntity hooked = kit.aimed();
                if (hooked == null) {
                    kit.sound(feet, Sound.BLOCK_DISPENSER_FAIL, 0.5f, 1.4f);
                    return;
                }
                Location from = ComboParts.hand(kit);
                Location to = hooked.getLocation().add(0.0, hooked.getHeight() * 0.5, 0.0);
                kit.sound(from, Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.0f);
                kit.line(from, to, 0.5, 1.0, 1.3);
                kit.pull(hooked, feet.clone().add(kit.facing().multiply(1.5)), 1.1);
                kit.later(4, () -> {
                    kit.slash("arc", 2.4, 140.0, 40.0, 0.0, 1.8, 0.3, 0.0, 1.2, 0.12, 0.0);
                    kit.hit(hooked, rate);
                });
            }
            case 4 -> {
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                kit.sound(start, Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 0.5f);
                kit.bolt(start, direction, 1.6, 16.0, 0.0, end -> {
                    kit.burst(end, 4.0);
                    kit.shockwave(end, 1.0, 4.2, 5, 1.2);
                    kit.sound(end, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                    for (LivingEntity victim : kit.around(end, 4.2)) {
                        if (kit.hit(victim, rate)) kit.push(victim, end.toVector(), 0.7, 0.3);
                    }
                });
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a great axe thrown spinning to the aim (12 blocks at most) and back to the hand, cutting what it passes, both ways. */
    private void sixth(MoveKit kit, double rate) {
        Location start = ComboParts.hand(kit);
        Vector direction = kit.aim().toVector().subtract(start.toVector());
        double length = Math.min(12.0, Math.max(4.0, direction.length()));
        if (direction.lengthSquared() < 1.0E-6) return;
        direction.normalize();
        Location far = start.clone().add(direction.clone().multiply(length));
        kit.sound(start, Sfx.WIND_BURST, 1.0f, 0.6f);
        kit.slash("arc", 2.6, 160.0, 80.0, 0.0, 1.6, 0.3, 0.0, 1.3, 0.14, 0.0);
        kit.line(start, far, 0.2, 1.0, 1.6);
        kit.hitAll(kit.corridor(start, direction, length, 1.3), rate);
        kit.later(8L, () -> {
            Location home = ComboParts.hand(kit);
            Vector back = home.toVector().subtract(far.toVector());
            double distance = back.length();
            if (distance < 0.5) return;
            back.normalize();
            kit.sound(far, Sfx.WIND, 0.9f, 0.8f);
            kit.line(far, home, 0.2, 1.0, 1.6);
            kit.hitAll(kit.corridor(far, back, distance, 1.3), rate);
        });
    }
}
