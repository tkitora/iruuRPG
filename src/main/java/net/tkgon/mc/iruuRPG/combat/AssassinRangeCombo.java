package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The assassin's combo with a ranged weapon: the shadow bow (t168, {@link AssassinShade}). Arrows of shadow that fly through blocks, seek the
 * enemy nearest the line of sight (behind a wall too) and leave afflictions for the assassination of 大. Normal: 1 a shadow arrow (70%, confusion
 * {@code n1-confusion} 10), 2 two arrows in a narrow V (45% x2, a bleed of {@code n2-bleed} 20% of each hit), 3 three quick arrows (35% x3, slowed
 * 20% for 1 s), 4 four arrows falling on the aim from above, through any roof (25% x4, confusion 5 each), 5 a great shadow arrow through everything
 * in the way (150%: confusion 15, bleed 20% and slowed 30%).
 *
 * <p>The skills are the shadow bow's own ({@link AssassinShade}); the heavy attacks below (the floating blades) are what they were before t168.
 */
public final class AssassinRangeCombo extends BaseCombo {

    static final String PLAN = "assassin_range";

    public AssassinRangeCombo() {
        super("assassin", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.7, 0.45, 0.35, 0.25, 1.5}, new int[]{1, 2, 3, 4, 1},
                new double[]{0.06, 0.24, 1.3, 0.35, 0.2, 0.2}, new int[]{120, 0, 0, 0, 0, 0}, 40));
    }

    /** A sword that sweeps across the front of the mage, from one side to the other (3 ticks), cutting what is in the cone at the middle. */
    private void sweep(MoveKit kit, boolean leftToRight, double rate) {
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
        kit.sound(feet, Sfx.WIND, 0.6f, leftToRight ? 1.5f : 1.8f);
        kit.slash("arc", 2.4, 130.0, 35.0, leftToRight ? 180.0 : 0.0, 1.9, 0.4, 0.0, 1.0, 0.09, 0.0);
        for (int step = 0; step < 4; step++) {
            double angle = Math.toRadians((leftToRight ? -1.0 : 1.0) * (60.0 - 40.0 * step));
            int which = step;
            kit.later(step, () -> {
                Location at = kit.player.getLocation().add(0.0, 1.2, 0.0)
                        .add(facing.clone().multiply(Math.cos(angle) * 1.9)).add(right.clone().multiply(Math.sin(angle) * 1.9));
                Vector point = facing.clone().multiply(Math.cos(angle)).add(right.clone().multiply(Math.sin(angle)));
                Blades.draw(kit, at, point, 1.1);
                if (which == 2) kit.hitAll(kit.cone(3.4, 65.0), rate);
            });
        }
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(AssassinShade.SHADE);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        java.util.UUID id = kit.player.getUniqueId();
        switch (step.stage()) {
            case 1 -> {
                Location start = ComboParts.hand(kit);
                kit.sound(start, Sfx.SHADE_LOOSE, 0.8f, 1.2f);
                AssassinShade.arrow(kit, start, AssassinShade.seek(kit, start, 28.0), 2.0, 28.0, 1.0, false, victim -> {
                    if (!kit.hit(victim, rate)) return;
                    AssassinShade.struck(kit, victim);
                    kit.confuse(victim, kit.number(PLAN, "n1-confusion", 10.0));
                });
            }
            case 2 -> {
                for (int index = 0; index < hits; index++) {
                    double turn = Math.toRadians(index % 2 == 0 ? -5.0 : 5.0);
                    kit.later(index, () -> {
                        Location start = ComboParts.hand(kit);
                        kit.sound(start, Sfx.SHADE_LOOSE, 0.6f, 1.4f);
                        AssassinShade.arrow(kit, start, AssassinShade.seek(kit, start, 28.0).rotateAroundY(turn), 2.0, 28.0, 0.9, false, victim -> {
                            double before = victim.getHealth();
                            if (!kit.hit(victim, rate)) return;
                            AssassinShade.struck(kit, victim);
                            double dealt = Math.max(0.0, before - victim.getHealth());
                            if (dealt > 0.0 && victim.isValid() && !victim.isDead()) kit.bleed(victim, dealt * kit.number(PLAN, "n2-bleed", 0.2));
                        });
                    });
                }
            }
            case 3 -> {
                for (int index = 0; index < hits; index++) {
                    kit.later(index * 3L, () -> {
                        Location start = ComboParts.hand(kit);
                        kit.sound(start, Sfx.SHADE_LOOSE, 0.5f, 1.6f);
                        AssassinShade.arrow(kit, start, AssassinShade.seek(kit, start, 26.0), 2.3, 26.0, 0.8, false, victim -> {
                            if (!kit.hit(victim, rate)) return;
                            AssassinShade.struck(kit, victim);
                            kit.slow(victim, 20.0, 1.0);
                        });
                    });
                }
            }
            case 4 -> {
                // four arrows fall on the aim from high above, through any roof
                LivingEntity aimed = kit.nearest(26.0, 25.0);
                Location aim = aimed != null ? aimed.getLocation() : kit.ground(kit.aim());
                kit.sound(kit.player.getLocation(), Sfx.SHADE_LOOSE, 0.8f, 0.9f);
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                for (int index = 0; index < hits; index++) {
                    double angle = random.nextDouble(Math.PI * 2.0);
                    double r = index == 0 ? 0.0 : 1.4 * Math.sqrt(random.nextDouble());
                    Location spot = aim.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
                    kit.later(4L + index * 2L, () -> AssassinShade.arrow(kit, spot.clone().add(0.3, 9.0, 0.2), new Vector(-0.03, -1.0, -0.02), 2.5, 10.0, 0.9, false, victim -> {
                        if (!kit.hit(victim, rate)) return;
                        AssassinShade.struck(kit, victim);
                        kit.confuse(victim, kit.number(PLAN, "n4-confusion", 5.0));
                    }));
                }
            }
            default -> {
                // a great shadow arrow through everything in the way, every affliction at once
                Location start = ComboParts.hand(kit);
                kit.sound(start, Sfx.SHADE_DRAW, 0.6f, 1.4f);
                kit.later(4L, () -> {
                    Location from = ComboParts.hand(kit);
                    kit.sound(from, Sfx.SHADE_LOOSE, 1.0f, 0.8f);
                    AssassinShade.arrow(kit, from, AssassinShade.seek(kit, from, 32.0), 2.6, 32.0, 1.5, true, victim -> {
                        double before = victim.getHealth();
                        if (!kit.hit(victim, rate)) return;
                        AssassinShade.struck(kit, victim);
                        kit.confuse(victim, kit.number(PLAN, "n5-confusion", 15.0));
                        kit.slow(victim, 30.0, 1.5);
                        double dealt = Math.max(0.0, before - victim.getHealth());
                        if (dealt > 0.0 && victim.isValid() && !victim.isDead()) kit.bleed(victim, dealt * kit.number(PLAN, "n5-bleed", 0.2));
                    });
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.35));
        double rate = step.damageMultiplier();
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
        switch (step.heavyIndex()) {
            case 0 -> Blades.orbit(kit, "assassin_blade_ring", 4, 50, 2.6, 3.8, 10, rate);
            case 1 -> {
                // a fan of five swords
                Vector aimed = kit.axes()[0];
                for (int index = 0; index < 5; index++) {
                    double turn = Math.toRadians((index - 2) * 14.0);
                    Vector heading = aimed.clone().multiply(Math.cos(turn)).add(kit.axes()[1].clone().multiply(Math.sin(turn))).normalize();
                    Location start = kit.player.getLocation().add(0.0, 1.5, 0.0).add(heading.clone().multiply(0.8));
                    kit.later(index / 2, () -> Blades.fly(kit, start, heading, 1.4, 8.0, rate, null));
                }
            }
            case 2 -> {
                // a great sword: it hangs before the mage for 0.4 s, growing bright, then runs through the line ahead
                Vector aimed = kit.axes()[0];
                Location base = kit.eye().add(aimed.clone().multiply(1.2)).subtract(0.0, 0.3, 0.0);
                kit.sound(feet, Sfx.MAGIC, 1.0f, 0.8f);
                for (int tick = 0; tick < 8; tick++) {
                    int which = tick;
                    kit.later(tick, () -> {
                        Blades.draw(kit, base, aimed, 1.6 + 0.1 * which);
                        kit.fx(base).ring(base.clone().add(aimed.clone().multiply(0.8)), kit.axes()[1], kit.axes()[2], 1.2 - 0.1 * which, 18, kit.palette(), 0.5, 0.2, 1.0);
                    });
                }
                kit.later(8L, () -> {
                    Location end = base.clone().add(aimed.clone().multiply(9.0));
                    kit.line(base, end, 0.2, 1.0, 1.6);
                    kit.line(base.clone().add(0.0, 0.08, 0.0), end.clone().add(0.0, 0.08, 0.0), 0.7, 1.0, 1.1);
                    Blades.draw(kit, end.clone().subtract(aimed.clone().multiply(2.0)), aimed, 2.0);
                    kit.sound(base, Sfx.WIND_BURST, 1.0f, 1.0f);
                    kit.hitAll(kit.corridor(base, aimed, 9.0, 1.2), rate);
                });
            }
            case 3 -> {
                // four seeking swords
                List<LivingEntity> targets = new ArrayList<>(kit.cone(10.0, 90.0));
                targets.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(feet), b.getLocation().distanceSquared(feet)));
                for (int index = 0; index < 4; index++) {
                    double side = (index - 1.5) * 0.7;
                    Location start = kit.player.getLocation().add(0.0, 1.8, 0.0).add(right.clone().multiply(side));
                    Vector heading = targets.isEmpty() ? facing.clone().add(right.clone().multiply(side * 0.3))
                            : targets.get(index % targets.size()).getLocation().add(0.0, 1.0, 0.0).toVector().subtract(start.toVector());
                    kit.later(index, () -> Blades.seek(kit, start, heading.clone().add(new Vector(0.0, 0.3, 0.0)), 1.3, 36, 12.0, rate, null));
                }
            }
            case 4 -> {
                // a storm of six swords round the aim for 1.2 s: everyone in it is cut every 4 ticks and drawn in
                Location center = ComboParts.target(kit);
                kit.sound(center, Sfx.MAGIC, 1.0f, 0.9f);
                kit.zone("assassin_blade_storm", center, 24, 2, zone -> {
                    Location mid = center.clone().add(0.0, 1.2, 0.0);
                    for (int index = 0; index < 6; index++) {
                        double angle = zone.age() * 0.5 + Math.PI * 2.0 * index / 6.0;
                        double c = Trig.cos(angle);
                        double s = Trig.sin(angle);
                        Blades.draw(kit, mid.clone().add(c * 2.4, (index % 3) * 0.3 - 0.3, s * 2.4), new Vector(-s, 0.0, c), 1.0);
                    }
                    if (zone.age() % 4 == 0) {
                        kit.sound(center, Sfx.WIND, 0.4f, 1.5f);
                        for (LivingEntity victim : kit.around(center, 3.2)) {
                            if (kit.hit(victim, rate)) kit.pull(victim, center, 0.15);
                        }
                    }
                }, zone -> kit.shockwave(center, 1.0, 3.2, 3, 1.0));
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a thousand needles: seven fast needles fanned out over 60 degrees at once. */
    private void sixth(MoveKit kit, double rate) {
        kit.sound(kit.eye(), Sfx.WIND, 0.9f, 1.6f);
        ComboParts.fanBolts(kit, 7, 60.0, rate, 1.8, 14.0);
    }
}
