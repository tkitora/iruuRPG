package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The assassin's combo with a ranged weapon: floating blades (the combo that was the mage's melee one) that fly, turn round the assassin and lunge at the enemies close by
 * and fall from above; they are drawn as blades of gradient dust (see {@link Blades}) and the sounds are the wind's. Normal: 1 two swords sweep across the front one after the
 * other (60% x2), 2 two swords cross at the aim (50% each), 3 three swords seek the nearest enemies (40% each), 4 four swords turn round the assassin for 2 s and lunge at the
 * enemies close by (7% a lunge, every 8 ticks), 5 five swords gather above the aimed enemy and fall on it (30% each). Heavy: 0 four swords turn round the assassin for 2 s in a
 * wider ring (6% a lunge every 10 ticks; its own cooldown of 6 s), 1 a fan of five swords (24% each), 2 a great sword charges for 0.4 s and pierces the line ahead (130% on
 * all in it), 3 four seeking swords (35% each), 4 a storm of six swords turning round the aim for 1.2 s (20% on all round every 4 ticks, draws them in).
 */
public final class AssassinRangeCombo extends BaseCombo {

    static final String PLAN = "assassin_range";

    public AssassinRangeCombo() {
        super("assassin", AttackType.RANGE, new ComboSpec(PLAN,
                new double[]{0.37, 0.5, 0.42, 0.07, 0.35}, new int[]{2, 2, 3, 1, 5},
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
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.35));
        double rate = step.damageMultiplier();
        int count = step.extraHits() + 1;
        Location feet = kit.player.getLocation();
        Vector facing = kit.facing();
        Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
        switch (step.stage()) {
            case 1 -> {
                sweep(kit, true, rate);
                kit.later(3L, () -> sweep(kit, false, rate));
            }
            case 2 -> {
                // two swords from the upper sides cross at the aim
                Location aim = kit.aim();
                Location mid = kit.eye().add(kit.eye().getDirection().multiply(Math.min(5.0, kit.eye().distance(aim))));
                for (int side = -1; side <= 1; side += 2) {
                    Location start = kit.player.getLocation().add(0.0, 1.9, 0.0).add(right.clone().multiply(side * 1.1)).add(facing.clone().multiply(0.6));
                    Vector toward = mid.toVector().subtract(start.toVector());
                    Blades.fly(kit, start, toward, 1.5, 7.0, rate, null);
                }
            }
            case 3 -> {
                // three swords seek the nearest enemies in front (or fly on straight when there are none)
                List<LivingEntity> targets = new ArrayList<>(kit.cone(9.0, 70.0));
                targets.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(feet), b.getLocation().distanceSquared(feet)));
                for (int index = 0; index < count; index++) {
                    double side = (index - (count - 1) / 2.0) * 0.8;
                    Location start = kit.player.getLocation().add(0.0, 1.7, 0.0).add(right.clone().multiply(side)).add(facing.clone().multiply(0.5));
                    Vector heading = targets.isEmpty() ? facing.clone().add(right.clone().multiply(side * 0.2))
                            : targets.get(index % targets.size()).getLocation().add(0.0, 1.0, 0.0).toVector().subtract(start.toVector());
                    kit.later(index, () -> Blades.seek(kit, start, heading.clone().add(new Vector(0.0, 0.25, 0.0)), 1.3, 30, 10.0, rate, null));
                }
            }
            case 4 -> Blades.orbit(kit, "assassin_blades", 4, 40, 1.5, 3.4, 8, rate);
            default -> {
                // five swords gather above the aimed enemy, then fall on it
                LivingEntity target = kit.nearest(10.0, 70.0);
                Location above = target == null ? ComboParts.target(kit).add(0.0, 1.0, 0.0) : target.getLocation();
                kit.sound(above, Sfx.MAGIC, 0.9f, 1.2f);
                for (int index = 0; index < count; index++) {
                    double angle = Math.PI * 2.0 * index / count;
                    Location top = above.clone().add(Trig.cos(angle) * 1.2, 6.0, Trig.sin(angle) * 1.2);
                    for (int hover = 0; hover < 5; hover++) kit.later(hover, () -> Blades.draw(kit, top, new Vector(0.0, -1.0, 0.0), 1.2));
                    kit.later(6L + index, () -> {
                        Location hit = target != null && target.isValid() && !target.isDead() ? target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0) : above.clone().add(0.0, 0.5, 0.0);
                        for (int fall = 0; fall < 3; fall++) {
                            double t = (fall + 1) / 3.0;
                            Location at = top.clone().add(hit.toVector().subtract(top.toVector()).multiply(t - 0.34));
                            kit.later(fall, () -> Blades.draw(kit, at, new Vector(0.0, -1.0, 0.0), 1.2));
                        }
                        kit.later(2L, () -> {
                            kit.burst(hit, 1.1);
                            kit.sound(hit, Sfx.CRACK, 0.6f, 1.5f);
                            if (target != null) kit.hit(target, rate);
                            else kit.hitAll(kit.around(hit, 1.6), rate);
                        });
                    });
                }
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
