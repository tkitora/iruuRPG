package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The mage's combo with a deployed weapon (a lantern of runes): the <b>ley furnace</b> (t168: the most damage for being in its reach and attacking
 * all the while, paid for in staying power). The attacks are made from a circle round the placement ({@code circle-radius} 6). Each attack made
 * while the mage stands in the circle adds a stack of <b>criticality</b> ({@code critical-max} 8); the stacks are all lost when the mage steps out
 * or does not attack for {@code critical-hold-seconds} (2). Each stack makes the furnace's attacks {@code critical-damage} (8%) stronger and the
 * damage the mage takes {@code critical-vulnerability} (4%) greater ({@link MageArcana#vulnerability}).
 *
 * <p>t198: the criticality also changes what the attacks are and how they look, by its <b>stage</b> ({@link #stage}: 0 below 3 stacks, 1 from 3,
 * 2 from 6, 3 at the full 8): the furnace burns from the weapon's colors to white heat, a rune turns round the mage for each stack, and at the
 * full stage a column of light stands over the furnace.
 *
 * <p>Normal: 1 bolts from the circle at the nearest enemies within {@code circle-reach} (9) of it (40% each; 2, 3 from stage 1, 4 from stage 2),
 * 2 an eruption under the nearest (80% round it; wider by stage, and from stage 2 the ground burns for 2 s: {@code burn-rate} 20% every half
 * second), 3 orbs that circle the furnace for 1.5 s (25% to an enemy each touches; 3, 4 from stage 1, 5 from stage 2), 4 lightning from the
 * furnace jumping between enemies (35% each; 3, 4 from stage 1, 6 from stage 2), 5 the critical burst over the whole circle (90%; at full
 * criticality {@code full-burst} 50% more, a second wave and a pillar of fire from the furnace on every enemy in it, {@code pillar-rate} 40%).
 *
 * <p>The combo has no heavy attacks since t168 (a deployed weapon has no skills).
 */
public final class MageDeployCombo extends BaseCombo {

    static final String PLAN = "mage_deploy";

    public MageDeployCombo() {
        super("mage", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.4, 0.8, 0.25, 0.35, 0.9}, new int[]{2, 1, 3, 3, 1},
                new double[]{0.25, 0.4, 1.3, 0.35, 0.12, 0.15}, new int[]{140, 0, 0, 0, 0, 0}, 40));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        Location center = kit.ground(kit.origin());
        double radius = kit.number(PLAN, "circle-radius", 6.0);
        double reach = kit.number(PLAN, "circle-reach", 9.0);
        int max = (int) Math.round(kit.number(PLAN, "critical-max", 8.0));
        java.util.UUID id = kit.player.getUniqueId();
        boolean inside = kit.player.getWorld() == center.getWorld() && kit.player.getLocation().distanceSquared(center) <= radius * radius;
        int stacks = MageArcana.attackAt(id, inside, max, System.currentTimeMillis(), MageArcana.holdMillis());
        double rate = step.damageMultiplier() * (1.0 + kit.number(PLAN, "critical-damage", 0.08) * stacks);
        int stage = stage(stacks, max);
        int hits = step.extraHits() + 1 + more(step.stage(), stage);
        furnace(kit, center, radius, max);
        kit.palette(heated(kit.palette(), stacks, max));
        java.util.List<LivingEntity> enemies = kit.around(center, reach);
        enemies.sort(java.util.Comparator.comparingDouble(victim -> victim.getLocation().distanceSquared(center)));
        Location core = center.clone().add(0.0, 1.6, 0.0);
        switch (step.stage()) {
            case 1 -> {
                kit.sound(core, Sfx.MAGE_CAST, 0.6f, 1.5f);
                for (int index = 0; index < Math.min(hits, enemies.size()); index++) {
                    LivingEntity victim = enemies.get(index);
                    kit.later(index * 2L, () -> {
                        if (!victim.isValid() || victim.isDead()) return;
                        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                        kit.line(core, chest, 0.2, 1.0, 1.2);
                        kit.burst(chest, 0.6);
                        kit.hit(victim, rate);
                    });
                }
            }
            case 2 -> {
                if (enemies.isEmpty()) return;
                Location under = kit.ground(enemies.get(0).getLocation());
                double wide = 2.2 + 0.5 * stage;
                kit.sound(under, Sfx.MAGE_IMPACT, 0.9f, 1.0f - 0.1f * stage);
                kit.shockwave(under, 0.4, wide, 4, 1.1 + 0.1 * stage);
                double tall = 3.0 + stage;
                for (double y = 0.0; y <= tall; y += 0.5) kit.fx(under).flatRing(under.clone().add(0.0, y, 0.0), (1.0 + 0.3 * stage) * (1.0 - 0.2 * y / tall), 16 + 4 * stage, kit.palette(), 0.4 + 0.6 * y / tall, 0.2, 1.0);
                for (LivingEntity victim : kit.around(under, wide)) kit.hit(victim, rate);
                if (stage >= 2) burn(kit, under, wide, rate * kit.number(PLAN, "burn-rate", 0.2) / Math.max(0.01, step.damageMultiplier()));
            }
            case 3 -> {
                // three orbs that circle the furnace; each strikes an enemy it touches, at most three times in all
                kit.sound(core, Sfx.MAGIC, 0.8f, 1.2f);
                int[] struck = {0};
                java.util.Map<java.util.UUID, Integer> last = new java.util.HashMap<>();
                kit.every(31, age -> {
                    for (int orb = 0; orb < hits; orb++) {
                        double angle = age * 0.21 + orb * Math.PI * 2.0 / hits;
                        Location at = center.clone().add(Math.cos(angle) * radius * 0.55, 1.1, Math.sin(angle) * radius * 0.55);
                        kit.fx(at).sphereShell(at, 0.3, age * 0.4, 10, kit.palette(), 1.0);
                        if (struck[0] >= hits) continue;
                        for (LivingEntity victim : kit.around(at, 1.4)) {
                            Integer before = last.get(victim.getUniqueId());
                            if (before != null && age - before < 10) continue;
                            last.put(victim.getUniqueId(), age);
                            if (kit.hit(victim, rate)) struck[0]++;
                            if (struck[0] >= hits) break;
                        }
                    }
                    return false;
                });
            }
            case 4 -> {
                if (enemies.isEmpty()) return;
                kit.sound(core, Sfx.MAGE_SURGE, 0.6f, 1.6f);
                Location from = core;
                java.util.Set<java.util.UUID> struck = new java.util.HashSet<>();
                LivingEntity current = enemies.get(0);
                for (int jump = 0; jump < hits && current != null; jump++) {
                    struck.add(current.getUniqueId());
                    Location to = current.getLocation().add(0.0, current.getHeight() * 0.6, 0.0);
                    SkillFx.trail(kit.player, from, to, kit.palette(), 1.0);
                    kit.particle(org.bukkit.Particle.ELECTRIC_SPARK, to, 4, 0.2, 0.1);
                    kit.hit(current, rate);
                    from = to;
                    LivingEntity next = null;
                    double best = 36.0;
                    for (LivingEntity candidate : kit.around(current.getLocation(), 6.0)) {
                        if (struck.contains(candidate.getUniqueId())) continue;
                        double distance = candidate.getLocation().distanceSquared(current.getLocation());
                        if (distance < best) {
                            best = distance;
                            next = candidate;
                        }
                    }
                    current = next;
                }
            }
            default -> {
                // the critical burst over the whole circle; at full criticality stronger, and a second wave
                boolean full = stacks >= max;
                double burst = rate * (full ? 1.0 + kit.number(PLAN, "full-burst", 0.5) : 1.0);
                kit.sound(center, Sfx.MAGE_IMPACT, 1.0f, full ? 0.7f : 1.0f);
                kit.shockwave(center, 0.6, radius, 6, full ? 1.5 : 1.2);
                kit.fx(core).sphereShell(core, 1.0, 0.0, 30, kit.palette(), 1.2);
                for (LivingEntity victim : kit.around(center, radius)) kit.hit(victim, burst);
                if (full) {
                    kit.later(10L, () -> {
                        kit.sound(center, Sfx.MAGE_SURGE, 0.9f, 1.4f);
                        kit.shockwave(center, radius * 0.4, radius, 5, 1.3);
                        for (LivingEntity victim : kit.around(center, radius)) kit.hit(victim, burst * 0.5);
                    });
                    // a pillar of fire from the furnace on every enemy in it
                    double pillar = rate * kit.number(PLAN, "pillar-rate", 0.4) / Math.max(0.01, step.damageMultiplier());
                    kit.later(14L, () -> {
                        kit.sound(core, Sfx.MAGE_BLAST, 1.0f, 0.9f);
                        for (LivingEntity victim : kit.around(center, radius)) {
                            Location foot = kit.ground(victim.getLocation());
                            kit.fx(core).line(core, victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0), kit.palette(), 0.3, 1.0, 1.0, 1.0, 0.25, null, 1);
                            kit.pillar(foot, 0.9, 4.0);
                            kit.particle(org.bukkit.Particle.FLAME, foot.clone().add(0.0, 0.5, 0.0), 6, 0.3, 0.04);
                            kit.hit(victim, pillar);
                        }
                    });
                }
            }
        }
    }

    /** The ley furnace round the placement (one; the attacks keep it): its circle burns brighter with the criticality; the mage out of it loses it. */
    private static void furnace(MoveKit kit, Location center, double radius, int max) {
        java.util.UUID id = kit.player.getUniqueId();
        net.tkgon.mc.iruuRPG.vfx.Gradient own = Palettes.of(kit.weapon.element());
        kit.zone("mage_furnace", center, kit.linger(60), 2, zone -> {
            int age = zone.age();
            long now = System.currentTimeMillis();
            int stacks = MageArcana.criticality(id, now, MageArcana.holdMillis());
            boolean inside = kit.player.getWorld() == center.getWorld() && kit.player.getLocation().distanceSquared(center) <= radius * radius;
            if (!inside && stacks > 0) {
                MageArcana.leave(id);
                stacks = 0;
                kit.sound(kit.player.getLocation(), Sfx.MAGIC_DARK, 0.4f, 0.7f);
            }
            double heat = stacks / (double) Math.max(1, max);
            net.tkgon.mc.iruuRPG.vfx.Gradient burning = heated(own, stacks, max);
            if (age % 4 == 0) {
                net.tkgon.mc.iruuRPG.vfx.Fx fx = kit.fx(center);
                fx.flatRing(center.clone().add(0.0, 0.12, 0.0), radius, 56, burning, 0.3 + 0.6 * heat, 0.2, 0.9 + 0.3 * heat);
                fx.flatRing(center.clone().add(0.0, 0.12, 0.0), radius * 0.4, 24, burning, 0.5 + 0.4 * heat, 0.3, 0.9);
                double bob = Math.sin(age * 0.2) * 0.15;
                Location core = center.clone().add(0.0, 1.6 + bob, 0.0);
                fx.sphereShell(core, 0.3 + 0.25 * heat, age * 0.3, 12 + 2 * stacks, burning, 1.0 + 0.3 * heat);
                for (double y = 0.0; y <= 1.3; y += 0.35) fx.dust(center.getX(), center.getY() + y, center.getZ(), burning, 0.3 + 0.5 * y / 1.3, 1.0);
                // at the full stage, a column of white light over the furnace
                if (stacks >= max) fx.line(core, core.clone().add(0.0, 5.0, 0.0), MageArcana.WHITE_HOT, 0.3, 1.0, 0.0, 1.3, 0.3, org.bukkit.Particle.END_ROD, 2);
            }
            // the mage burning with it: a rune turning round them for each stack
            if (stacks > 0 && age % 2 == 0) {
                Location feet = kit.player.getLocation();
                net.tkgon.mc.iruuRPG.vfx.Fx fx = kit.fx(feet);
                for (int rune = 0; rune < stacks; rune++) {
                    double angle = age * 0.16 + rune * Math.PI * 2.0 / stacks;
                    fx.dust(feet.getX() + Math.cos(angle) * 0.9, feet.getY() + 0.3 + 0.12 * (rune % 2), feet.getZ() + Math.sin(angle) * 0.9, burning, 0.6 + 0.4 * heat, 1.0);
                }
                if (age % 6 == 0) fx.flatRing(feet.clone().add(0.0, 0.1, 0.0), 0.6 + 0.05 * stacks, 14, burning, 0.4 + 0.6 * heat, 0.3, 0.9);
                if (stacks >= max) kit.particle(org.bukkit.Particle.ELECTRIC_SPARK, feet.clone().add(0.0, 1.0, 0.0), 3, 0.4, 0.05);
            }
        }, null);
    }

    /** The stage of {@code stacks} of criticality out of {@code max}: 0 below 3/8 of it, 1 from 3/8, 2 from 6/8, 3 at the full. Pure, for the tests. */
    static int stage(int stacks, int max) {
        if (max <= 0 || stacks <= 0) return 0;
        if (stacks >= max) return 3;
        double share = stacks / (double) max;
        return share >= 0.75 ? 2 : share >= 0.375 ? 1 : 0;
    }

    /** How many more hits normal attack {@code attack} (1 - 5) has at {@code stage}: the bolts, the orbs and the jumps of the lightning. */
    static int more(int attack, int stage) {
        return switch (attack) {
            case 1, 3 -> Math.min(stage, 2);
            case 4 -> stage <= 0 ? 0 : stage == 1 ? 1 : 3;
            default -> 0;
        };
    }

    /** The weapon's colors burning to white heat with the criticality. */
    static net.tkgon.mc.iruuRPG.vfx.Gradient heated(net.tkgon.mc.iruuRPG.vfx.Gradient own, int stacks, int max) {
        double heat = max <= 0 ? 0.0 : Math.min(1.0, stacks / (double) max);
        return heat <= 0.0 ? own : own.mix(MageArcana.WHITE_HOT, Math.round(heat * 0.7 * 10.0) / 10.0);
    }

    /** The ground under the eruption burns for 2 s: {@code rate} on the enemies on it every half second. */
    private static void burn(MoveKit kit, Location at, double radius, double rate) {
        kit.every(41, age -> {
            if (age % 4 == 0) {
                kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), radius * (0.6 + 0.1 * Math.sin(age * 0.5)), 20, kit.palette(), 0.7, 0.4, 1.0);
                kit.particle(org.bukkit.Particle.FLAME, at.clone().add(0.0, 0.3, 0.0), 4, radius * 0.4, 0.02);
            }
            if (age > 0 && age % 10 == 0) for (LivingEntity victim : kit.around(at, radius)) kit.hit(victim, rate);
            return false;
        });
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        // (t168) no heavy attack of this combo is thrown any more: a deployed weapon has no skills.
    }

}
