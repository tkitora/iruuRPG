package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The cleric's combo with a ranged weapon (the staff): a healer's staff that can hurt. The lights hurt the enemies and mend the allies near them. Normal: 1 two bolts of
 * light (55% x2, mends 0.4% round the cleric), 2 three bolts (40% x3, mends 0.4%), 3 a beam (120% on all in the line, heals 2%; an ally the beam passes through is healed 3%),
 * 4 four seeking lights (30% x4, mends 1% round the cleric), 5 a spear of light (100%) that bursts (50%, heals 4% the allies at the burst and 1.5% the cleric).
 * Heavy: 0 a blessed ground at the aim for 2.5 s (20% a pulse, heals 1% a pulse the allies on it), 1 light falls on the aim (15% x8), 2 a chain of light
 * between three enemies (40% x3), 3 a pillar of light (140%), 4 a beam from the sky for 1 s (30% x5, heals 5% the allies in it). The heals of a staff are capped
 * ({@code combat.combo.ally-heal-cap}, 4% a second for one ally).
 */
public final class ClericRangeCombo extends BaseCombo {

    static final String PLAN = "cleric_range";

    public ClericRangeCombo() {
        super("healer", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.34, 0.3, 1.14, 0.34, 1.59}, new int[]{2, 3, 1, 4, 1},
                new double[]{0.2, 0.15, 0.4, 1.4, 0.3}, new int[]{160, 0, 0, 0, 0}, 40));
    }

    /** A mending light round the cleric: the allies within 8 blocks (the cleric too) are healed a little and a ring of light spreads. */
    private static void mend(MoveKit kit, double percent) {
        Location at = kit.player.getLocation();
        kit.healAllies(at, 8.0, percent);
        kit.fx(at).flatRing(at.clone().add(0.0, 0.2, 0.0), 1.2, 24, kit.palette(), 0.5, 0.2, 1.0);
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.35));
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Vector[] axes = kit.axes();
        switch (step.stage()) {
            case 1 -> {
                ComboParts.quickBolts(kit, hits, rate, 1.6, 14.0, 0.0);
                mend(kit, 0.4);
            }
            case 2 -> {
                ComboParts.quickBolts(kit, hits, rate, 1.7, 14.0, 0.06);
                mend(kit, 0.4);
            }
            case 3 -> {
                Location start = ComboParts.hand(kit);
                kit.sound(start, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
                kit.sound(start, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                int before = kit.corridor(start, direction, 13.0, 0.9).size();
                ComboParts.pierce(kit, start, direction, 13.0, 0.9, rate);
                if (before > 0) kit.heal(2.0);
                // an ally in the line is healed by the beam too
                for (org.bukkit.entity.Player ally : kit.world().getPlayers()) {
                    if (ally.equals(kit.player) || ally.isDead()) continue;
                    Vector toAlly = ally.getLocation().add(0.0, 1.0, 0.0).toVector().subtract(start.toVector());
                    double along = toAlly.dot(direction);
                    if (along < 0.0 || along > 13.0 || toAlly.subtract(direction.clone().multiply(along)).length() > 1.1) continue;
                    kit.healAllies(ally.getLocation(), 0.5, 3.0);
                    kit.burst(ally.getLocation().add(0.0, 1.0, 0.0), 1.0);
                }
            }
            case 4 -> {
                ComboParts.homingVolley(kit, hits, rate, 14.0, end -> kit.burst(end, 1.0));
                mend(kit, 1.0);
            }
            default -> {
                Location start = ComboParts.hand(kit);
                kit.sound(start, Sfx.MAGIC, 1.0f, 1.4f);
                Vector direction = kit.aim().toVector().subtract(start.toVector()).normalize();
                kit.bolt(start, direction, 1.8, 16.0, rate, end -> {
                    kit.burst(end, 2.6);
                    kit.shockwave(end, 0.8, 2.6, 3, 1.0);
                    kit.sound(end, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.0f);
                    kit.hitAll(kit.around(end, 2.6), rate * 0.5);
                    kit.healAllies(end, 3.0, 4.0);
                    kit.heal(1.5);
                });
            }
        }
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
            default -> {
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
        }
    }
}
