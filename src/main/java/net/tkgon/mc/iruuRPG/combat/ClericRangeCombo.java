package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The cleric's combo with a ranged weapon (the staff): lightning and light that hurt the enemies and mend the allies (t168, {@link ClericSkills}).
 * Every attack that hits adds a verse (the chant of 大 spends them). Normal: 1 two shots of light (45% x2; the allies near where each lands are
 * healed a little), 2 lightning on the enemy aimed at, jumping on to two more (35% x3), 3 a beam through everything in the line (100%; an ally in
 * it is healed 2%), 4 three shots of light fanned out (30% x3), 5 a bolt of lightning on the aim (140% round it; the allies near it are healed 2%,
 * the cleric 1%).
 *
 * <p>The skills are the cleric's own ({@link ClericSkills}); the heavy attacks below are what they were before t168.
 */
public final class ClericRangeCombo extends BaseCombo {

    static final String PLAN = "cleric_range";

    public ClericRangeCombo() {
        super("healer", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.45, 0.35, 1.0, 0.3, 1.4}, new int[]{2, 3, 1, 3, 1},
                new double[]{0.2, 0.15, 0.4, 1.4, 0.3, 0.7}, new int[]{160, 0, 0, 0, 0, 0}, 40));
    }

    /** A mending light round the cleric: the allies within 8 blocks (the cleric too) are healed a little and a ring of light spreads. */
    private static void mend(MoveKit kit, double percent) {
        Location at = kit.player.getLocation();
        kit.healAllies(at, 8.0, percent);
        kit.fx(at).flatRing(at.clone().add(0.0, 0.2, 0.0), 1.2, 24, kit.palette(), 0.5, 0.2, 1.0);
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        ClericSkills.lookStorm(kit);
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
                // two shots of light; the allies near where each lands are healed a little
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 3L, () -> {
                        Location start = ComboParts.hand(kit);
                        Vector direction = kit.aim().toVector().subtract(start.toVector());
                        if (direction.lengthSquared() < 1.0E-6) return;
                        kit.sound(start, Sfx.STORM_CAST, 0.6f, 1.3f + 0.1f * which);
                        shot(kit, start, direction.normalize(), 1.7, 16.0, victim -> {
                            if (kit.hit(victim, rate)) landed.accept(victim);
                        }, end -> kit.healAllies(end, 3.0, kit.number(PLAN, "shot-heal", 0.5)));
                    });
                }
            }
            case 2 -> {
                // lightning on the enemy aimed at, jumping on to the nearest ones
                LivingEntity first = kit.nearest(16.0, 30.0);
                Location hand = ComboParts.hand(kit);
                kit.sound(hand, Sfx.STORM_CAST, 0.8f, 1.1f);
                if (first == null) {
                    Location aim = kit.ground(kit.aim());
                    ClericSkills.lightning(kit, hand, aim, false);
                    return;
                }
                java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();
                arc(kit, hand, first, struck, hits - 1, rate, landed);
            }
            case 3 -> {
                // a beam through everything in the line; an ally in it is healed too
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector());
                if (direction.lengthSquared() < 1.0E-6) direction = kit.axes()[0];
                direction.normalize();
                kit.sound(start, Sfx.HOLY_BLESS, 0.7f, 1.6f);
                kit.palette(ClericSkills.HOLY);
                kit.line(start, start.clone().add(direction.clone().multiply(14.0)), 0.3, 1.0, 1.6);
                for (LivingEntity victim : kit.corridor(start, direction, 14.0, 0.9)) {
                    if (kit.hit(victim, rate)) landed.accept(victim);
                }
                for (org.bukkit.entity.Player ally : kit.world().getPlayers()) {
                    if (ally.equals(kit.player) || ally.isDead()) continue;
                    Vector toAlly = ally.getLocation().add(0.0, 1.0, 0.0).toVector().subtract(start.toVector());
                    double along = toAlly.dot(direction);
                    if (along < 0.0 || along > 14.0 || toAlly.subtract(direction.clone().multiply(along)).length() > 1.1) continue;
                    kit.healAllies(ally.getLocation(), 0.5, kit.number(PLAN, "beam-heal", 2.0));
                    kit.burst(ally.getLocation().add(0.0, 1.0, 0.0), 1.0);
                }
            }
            case 4 -> {
                // three shots of light, fanned out
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector());
                if (direction.lengthSquared() < 1.0E-6) direction = kit.axes()[0];
                direction.normalize();
                kit.sound(start, Sfx.STORM_CAST, 0.8f, 1.4f);
                for (int index = 0; index < hits; index++) {
                    double turn = hits == 1 ? 0.0 : Math.toRadians(-9.0 + 18.0 * index / (hits - 1));
                    Vector way = direction.clone().rotateAroundY(turn);
                    shot(kit, start, way, 1.6, 15.0, victim -> {
                        if (kit.hit(victim, rate)) landed.accept(victim);
                    }, null);
                }
            }
            default -> {
                // a bolt of lightning on the aim; the allies near it are healed
                Location aim = kit.ground(kit.aim());
                Location hand = ComboParts.hand(kit);
                kit.sound(hand, Sfx.STORM_CAST, 1.0f, 0.9f);
                kit.later(3L, () -> {
                    ClericSkills.lightning(kit, aim.clone().add(0.0, 12.0, 0.0), aim, true);
                    kit.sound(aim, Sfx.STORM_STRIKE, 1.0f, 0.9f);
                    kit.shockwave(aim, 0.6, 3.0, 4, 1.2);
                    for (LivingEntity victim : kit.around(aim, 3.0)) {
                        if (kit.hit(victim, rate)) landed.accept(victim);
                    }
                    kit.healAllies(aim, 4.0, kit.number(PLAN, "strike-heal", 2.0));
                    kit.heal(kit.number(PLAN, "strike-self-heal", 1.0));
                });
            }
        }
    }

    /**
     * A shot of light from {@code start} along {@code direction} ({@code speed} blocks a tick, at most {@code range}): the first enemy it touches is
     * given to {@code onHit} (the shot ends there); it ends at a block too. {@code onEnd} where it ended.
     */
    static void shot(MoveKit kit, Location start, Vector direction, double speed, double range, java.util.function.Consumer<LivingEntity> onHit,
                     java.util.function.Consumer<Location> onEnd) {
        Vector heading = direction.clone().normalize();
        Location[] position = {start.clone()};
        double[] travelled = {0.0};
        kit.every((int) Math.ceil(range / speed) + 2, age -> {
            double step = Math.min(speed, range - travelled[0]);
            if (step <= 1.0E-6) {
                if (onEnd != null) onEnd.accept(position[0]);
                return true;
            }
            Location from = position[0];
            org.bukkit.util.RayTraceResult wall = from.getWorld().rayTraceBlocks(from, heading, step, org.bukkit.FluidCollisionMode.NEVER, true);
            double length = wall == null || wall.getHitPosition() == null ? step : wall.getHitPosition().distance(from.toVector());
            LivingEntity touched = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity victim : kit.corridor(from, heading, length, 0.7)) {
                double along = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0).toVector().subtract(from.toVector()).dot(heading);
                if (along < best) {
                    best = along;
                    touched = victim;
                }
            }
            Location next = from.clone().add(heading.clone().multiply(touched != null ? Math.max(0.0, Math.min(length, best)) : length));
            kit.line(from, next, 0.3, 1.0, 1.1);
            if (touched != null || wall != null) {
                kit.burst(next, 0.7);
                if (touched != null && onHit != null) onHit.accept(touched);
                if (onEnd != null) onEnd.accept(next);
                return true;
            }
            position[0] = next;
            travelled[0] += step;
            return false;
        });
    }

    /** Lightning from {@code from} to {@code victim}, then on to the nearest enemy not struck yet, {@code left} more times. */
    private static void arc(MoveKit kit, Location from, LivingEntity victim, java.util.Set<java.util.UUID> struck, int left, double rate,
                            java.util.function.Consumer<LivingEntity> landed) {
        if (!victim.isValid() || victim.isDead()) return;
        struck.add(victim.getUniqueId());
        Location to = victim.getLocation().add(0.0, victim.getHeight() * 0.6, 0.0);
        SkillFx.trail(kit.player, from, to, ClericSkills.STORM, 1.0);
        kit.particle(org.bukkit.Particle.ELECTRIC_SPARK, to, 5, 0.3, 0.15);
        kit.sound(to, Sfx.STORM_STRIKE, 0.5f, 1.4f);
        if (kit.hit(victim, rate)) landed.accept(victim);
        if (left <= 0) return;
        kit.later(2L, () -> {
            LivingEntity next = null;
            double best = 36.0;
            for (LivingEntity candidate : kit.around(victim.getLocation(), 6.0)) {
                if (struck.contains(candidate.getUniqueId())) continue;
                double distance = candidate.getLocation().distanceSquared(victim.getLocation());
                if (distance < best) {
                    best = distance;
                    next = candidate;
                }
            }
            if (next != null) arc(kit, to, next, struck, left - 1, rate, landed);
        });
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.45));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                kit.sound(target, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
                ComboParts.sigil(kit, "cleric_blessing", target, 3.5, rate, 50, 10, at -> {
                    kit.healAllies(at, 3.5, 1.0);
                });
            }
            case 1 -> ComboParts.rain(kit, target, 3.2, 8, rate, 1.5, Sound.BLOCK_AMETHYST_BLOCK_CHIME);
            case 2 -> {
                // a chain of light from enemy to enemy
                LivingEntity first = kit.aimed();
                if (first == null) {
                    List<LivingEntity> near = kit.around(target, 6.0);
                    if (!near.isEmpty()) first = near.get(0);
                }
                if (first == null) {
                    kit.sound(feet, Sound.BLOCK_DISPENSER_FAIL, 0.5f, 1.4f);
                    return;
                }
                List<LivingEntity> chain = new ArrayList<>();
                chain.add(first);
                while (chain.size() < 3) {
                    LivingEntity last = chain.get(chain.size() - 1);
                    LivingEntity next = null;
                    double best = Double.MAX_VALUE;
                    for (LivingEntity candidate : kit.around(last.getLocation(), 6.0)) {
                        if (chain.contains(candidate)) continue;
                        double distance = candidate.getLocation().distanceSquared(last.getLocation());
                        if (distance < best) {
                            best = distance;
                            next = candidate;
                        }
                    }
                    if (next == null) break;
                    chain.add(next);
                }
                Location from = ComboParts.hand(kit);
                kit.sound(from, Sound.ITEM_TRIDENT_THUNDER, 0.7f, 1.6f);
                for (int index = 0; index < chain.size(); index++) {
                    LivingEntity victim = chain.get(index);
                    Location start = from.clone();
                    Location to = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                    kit.later(index * 2L, () -> {
                        kit.line(start, to, 0.2, 1.0, 1.3);
                        kit.burst(to, 1.0);
                        kit.hit(victim, rate);
                    });
                    from = to;
                }
            }
            case 3 -> {
                kit.sound(target, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
                for (int tick = 0; tick < 10; tick++) {
                    double r = 2.6 - 1.6 * tick / 9.0;
                    kit.later(tick, () -> kit.fx(target).flatRing(target.clone().add(0.0, 0.15, 0.0), r, 40, kit.palette(), 0.4, 0.3, 1.0));
                }
                kit.later(10, () -> {
                    kit.pillar(target, 2.0, 7.0);
                    kit.shockwave(target, 1.0, 3.0, 4, 1.1);
                    kit.sound(target, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
                    for (LivingEntity victim : kit.around(target, 2.4)) {
                        kit.hit(victim, rate);
                    }
                });
            }
            case 4 -> {
                // a beam from the sky on the aim for a second
                kit.sound(target, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
                kit.every(21, age -> {
                    kit.pillar(target, 1.6, 8.0);
                    if (age % 4 == 0 && age >= 4) {
                        kit.shockwave(target, 0.8, 2.6, 2, 1.0);
                        kit.hitAll(kit.around(target, 2.6), rate);
                    }
                    if (age == 20) {
                        kit.healAllies(target, 2.6, 5.0);
                        kit.heal(2.0);
                    }
                    return false;
                });
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a halo of light spreads from the cleric to 6 blocks: it strikes the enemies it passes and heals the allies within it 4%. */
    private void sixth(MoveKit kit, double rate) {
        Location feet = kit.player.getLocation();
        kit.sound(feet, Sfx.MAGIC, 1.0f, 1.3f);
        kit.shockwave(feet, 0.8, 6.0, 8, 1.2);
        kit.healAllies(feet, 6.0, 4.0);
        kit.later(3L, () -> {
            for (org.bukkit.entity.LivingEntity victim : kit.around(feet, 6.0)) {
                if (kit.hit(victim, rate)) kit.push(victim, feet.toVector(), 0.3, 0.1);
            }
        });
    }
}
