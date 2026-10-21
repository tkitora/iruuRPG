package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The assassin's combo with a ranged weapon (throwing knives): fast, from the shadows, ending behind the target. Normal: 1 two knives (55% x2),
 * 2 three knives (40% x3), 3 a knife that bounces off two walls (40% x3), 4 knives fall on the aim (28% x5), 5 a blink behind the target and a cut
 * (150%). Heavy: 0 a smoke bomb at the feet for 2.5 s (slows 40%, 15% a pulse), 1 a clone throws four knives (30% x4), 2 three knives that pin
 * (40% each, slows 70%), 3 a poisoned knife (130%, slows), 4 a thousand knives (12% x12).
 */
public final class AssassinRangeCombo extends BaseCombo {

    static final String PLAN = "assassin_range";

    public AssassinRangeCombo() {
        super("assassin", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.55, 0.4, 0.4, 0.28, 1.5}, new int[]{2, 3, 3, 5, 1},
                new double[]{0.15, 0.3, 0.4, 1.3, 0.12}, new int[]{120, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        switch (step.stage()) {
            case 1 -> ComboParts.quickBolts(kit, hits, rate, 2.2, 14.0, 0.0);
            case 2 -> ComboParts.quickBolts(kit, hits, rate, 2.3, 14.0, 0.07);
            case 3 -> {
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                kit.sound(start, Sound.ITEM_TRIDENT_THROW, 0.9f, 1.8f);
                kit.ricochet(start, direction, 2, 2.2, 40, rate, end -> kit.burst(end, 1.0));
            }
            case 4 -> {
                Location aim = ComboParts.target(kit);
                ComboParts.rain(kit, aim, 3.0, hits, rate, 1.4, Sound.ENTITY_ARROW_HIT);
            }
            default -> {
                // the finisher: into the shadow behind the target and a cut
                LivingEntity target = kit.aimed();
                Vector facing = kit.facing();
                Location from = feet.clone();
                Location to;
                if (target != null) {
                    Vector away = target.getLocation().getDirection().setY(0.0);
                    if (away.lengthSquared() < 1.0E-6) away = facing.clone();
                    to = target.getLocation().subtract(away.normalize().multiply(1.3));
                } else {
                    to = from.clone().add(facing.clone().multiply(5.0));
                }
                Location safe = kit.ground(to);
                kit.sound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.7f);
                kit.fx(from).sphereShell(from.clone().add(0.0, 1.0, 0.0), 0.7, 0.0, 32, kit.palette(), 1.0);
                if (safe.getBlock().isPassable() && safe.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
                    Vector look = (target != null ? target.getLocation() : to.clone().add(facing)).toVector().subtract(safe.toVector());
                    safe.setDirection(look);
                    kit.player.teleport(safe);
                    kit.player.setFallDistance(0.0f);
                }
                kit.slash("drop", 2.8, 150.0, 20.0, 0.0, 1.8, 0.3, 0.0, 1.3, 0.14, 0.0);
                kit.sound(kit.player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.3f);
                kit.later(1, () -> {
                    for (LivingEntity victim : kit.cone(3.4, 65.0)) kit.hit(victim, rate);
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Location target = ComboParts.target(kit);
        Vector[] axes = kit.axes();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a smoke bomb: the enemies in the smoke are slowed
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.7f);
                ComboParts.sigil(kit, "assassin_smoke", at, 3.4, rate, 50, 10, here -> {
                    for (LivingEntity victim : kit.around(here, 3.4)) kit.slow(victim, 40.0, 1.0);
                    kit.fx(here).particle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, here.getX(), here.getY() + 0.6, here.getZ(), 6, 1.2, 0.3, 1.2, 0.01);
                });
            }
            case 1 -> {
                // an afterimage steps aside and throws four knives
                Location clone = feet.clone().add(axes[1].clone().multiply(1.6));
                kit.fx(clone).sphereShell(clone.clone().add(0.0, 1.0, 0.0), 0.6, 0.0, 28, kit.palette(), 1.0);
                kit.sound(clone, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.9f);
                for (int index = 0; index < 4; index++) {
                    kit.later(index * 2L, () -> {
                        Location start = clone.clone().add(0.0, 1.4, 0.0);
                        Vector direction = kit.aim().toVector().subtract(start.toVector());
                        direction = direction.lengthSquared() < 1.0E-6 ? axes[0].clone() : direction.normalize();
                        kit.bolt(start, direction, 2.2, 14.0, rate, end -> kit.burst(end, 0.8));
                    });
                }
            }
            case 2 -> {
                // three knives that pin
                Location start = ComboParts.hand(kit);
                kit.sound(start, Sound.ITEM_TRIDENT_THROW, 0.9f, 1.5f);
                for (int index = 0; index < 3; index++) {
                    double turn = Math.toRadians((index - 1) * 8.0);
                    Vector direction = axes[0].clone().multiply(Math.cos(turn)).add(axes[1].clone().multiply(Math.sin(turn))).normalize();
                    kit.later(index, () -> {
                        kit.bolt(start.clone(), direction, 2.2, 14.0, rate, end -> {
                            kit.burst(end, 1.0);
                            for (LivingEntity victim : kit.around(end, 1.2)) kit.slow(victim, 70.0, 1.5);
                        });
                    });
                }
            }
            case 3 -> {
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                kit.sound(start, Sound.ENTITY_SPIDER_AMBIENT, 0.6f, 1.6f);
                kit.arrow(start, direction, rate, 1.3, end -> {
                    kit.burst(end, 1.6);
                    for (LivingEntity victim : kit.around(end, 1.4)) kit.slow(victim, 50.0, 2.5);
                });
            }
            default -> {
                // a thousand knives (twelve of them): a fan that fills the air
                Location start = ComboParts.hand(kit);
                kit.sound(feet, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.9f);
                for (int index = 0; index < 12; index++) {
                    double turn = Math.toRadians((index - 5.5) * 5.0);
                    double lift = Math.toRadians(((index % 3) - 1) * 4.0);
                    Vector direction = axes[0].clone().multiply(Math.cos(turn)).add(axes[1].clone().multiply(Math.sin(turn))).add(axes[2].clone().multiply(Math.sin(lift))).normalize();
                    kit.later(index / 3, () -> kit.bolt(start.clone(), direction, 2.3, 12.0, rate, end -> kit.burst(end, 0.6)));
                }
            }
        }
    }
}
