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
 * <p>Normal: 1 two bolts from the circle at the nearest enemies within {@code circle-reach} (9) of it (40% x2), 2 an eruption under the nearest
 * (80% round it), 3 three orbs that circle the furnace for 1.5 s (25% to an enemy each touches, at most three times), 4 lightning from the furnace
 * jumping between three enemies (35% x3), 5 the critical burst over the whole circle (90%; at full criticality {@code full-burst} 50% more and a
 * second wave).
 *
 * <p>The heavy attacks below are what they were before t168 (a deployed weapon has no skills).
 */
public final class MageDeployCombo extends BaseCombo {

    static final String PLAN = "mage_deploy";

    public MageDeployCombo() {
        super("mage", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.4, 0.8, 0.25, 0.35, 0.9}, new int[]{2, 1, 3, 3, 1},
                new double[]{0.25, 0.4, 1.3, 0.35, 0.12, 0.15}, new int[]{140, 0, 0, 0, 0, 0}, 40));
    }

    /** An arcane spire standing at {@code at}: it hangs a ball of light over a crystal and shoots the nearest enemy within 10 blocks every 8 ticks, as long as the placement lasts. */
    private void spire(MoveKit kit, String key, Location at, double rate) {
        kit.sound(at, Sfx.MAGIC, 0.8f, 1.6f);
        kit.zone(key, at, kit.linger(50), 2, zone -> {
            double bob = Math.sin(zone.age() * 0.25) * 0.2;
            Location orb = at.clone().add(0.0, 1.9 + bob, 0.0);
            for (double y = 0.0; y <= 1.5; y += 0.3) kit.fx(at).dust(at.getX(), at.getY() + y, at.getZ(), kit.palette(), 0.3 + 0.5 * y / 1.5, 1.1);
            kit.fx(orb).sphereShell(orb, 0.3, zone.age() * 0.4, 14, kit.palette(), 1.1);
            if (zone.age() % 8 != 4) return;
            LivingEntity best = null;
            double distance = 100.0;
            for (LivingEntity candidate : kit.around(at, 10.0)) {
                double d = candidate.getLocation().distanceSquared(at);
                if (d < distance) {
                    distance = d;
                    best = candidate;
                }
            }
            if (best == null) return;
            Location to = best.getLocation().add(0.0, best.getHeight() * 0.5, 0.0);
            kit.line(orb, to, 0.2, 1.0, 1.1);
            kit.hit(best, rate);
            kit.sound(orb, Sfx.MAGIC, 0.4f, 1.8f);
        }, zone -> kit.shockwave(at, 0.5, 1.6, 2, 0.8));
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(MageArcana.ARCANE);
        Location center = kit.ground(kit.origin());
        double radius = kit.number(PLAN, "circle-radius", 6.0);
        double reach = kit.number(PLAN, "circle-reach", 9.0);
        int max = (int) Math.round(kit.number(PLAN, "critical-max", 8.0));
        java.util.UUID id = kit.player.getUniqueId();
        boolean inside = kit.player.getWorld() == center.getWorld() && kit.player.getLocation().distanceSquared(center) <= radius * radius;
        int stacks = MageArcana.attackAt(id, inside, max, System.currentTimeMillis(), MageArcana.holdMillis());
        double rate = step.damageMultiplier() * (1.0 + kit.number(PLAN, "critical-damage", 0.08) * stacks);
        int hits = step.extraHits() + 1;
        furnace(kit, center, radius, max);
        if (stacks >= max) kit.palette(MageArcana.TORRENT);
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
                kit.sound(under, Sfx.MAGE_IMPACT, 0.9f, 1.0f);
                kit.shockwave(under, 0.4, 2.2, 4, 1.1);
                for (double y = 0.0; y <= 3.0; y += 0.5) kit.fx(under).flatRing(under.clone().add(0.0, y, 0.0), 1.0 - 0.2 * y / 3.0, 16, kit.palette(), 0.4 + 0.2 * y, 0.2, 1.0);
                for (LivingEntity victim : kit.around(under, 2.2)) kit.hit(victim, rate);
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
                }
            }
        }
    }

    /** The ley furnace round the placement (one; the attacks keep it): its circle burns brighter with the criticality; the mage out of it loses it. */
    private static void furnace(MoveKit kit, Location center, double radius, int max) {
        java.util.UUID id = kit.player.getUniqueId();
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
            if (age % 4 == 0) {
                net.tkgon.mc.iruuRPG.vfx.Fx fx = kit.fx(center);
                fx.flatRing(center.clone().add(0.0, 0.12, 0.0), radius, 56, stacks >= max ? MageArcana.TORRENT : MageArcana.ARCANE, 0.3 + 0.6 * heat, 0.2, 0.9 + 0.3 * heat);
                fx.flatRing(center.clone().add(0.0, 0.12, 0.0), radius * 0.4, 24, MageArcana.ARCANE, 0.5 + 0.4 * heat, 0.3, 0.9);
                double bob = Math.sin(age * 0.2) * 0.15;
                Location core = center.clone().add(0.0, 1.6 + bob, 0.0);
                fx.sphereShell(core, 0.3 + 0.25 * heat, age * 0.3, 12, MageArcana.ARCANE, 1.0);
                for (double y = 0.0; y <= 1.3; y += 0.35) fx.dust(center.getX(), center.getY() + y, center.getZ(), MageArcana.ARCANE, 0.3 + 0.5 * y / 1.3, 1.0);
            }
            // the mage burning with it: sparks rising round them, the more the stronger
            if (stacks > 0 && age % 6 == 0) {
                Location feet = kit.player.getLocation();
                kit.fx(feet).flatRing(feet.clone().add(0.0, 0.1, 0.0), 0.6 + 0.05 * stacks, 14, MageArcana.TORRENT, 0.4 + 0.6 * heat, 0.3, 0.9);
                if (stacks >= max) kit.particle(org.bukkit.Particle.ELECTRIC_SPARK, feet.clone().add(0.0, 1.0, 0.0), 3, 0.4, 0.05);
            }
        }, null);
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.mixed(kit.weapon.element(), 0.7));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.6f);
                ComboParts.sigil(kit, "mage_summon", at, 3.5, rate, 50, 10, null);
            }
            case 1 -> {
                // three circles, one after another, toward the aim
                Vector toward = target.toVector().subtract(feet.toVector()).setY(0.0);
                Vector facing = toward.lengthSquared() < 1.0 ? kit.facing() : toward.normalize();
                for (int index = 0; index < 3; index++) {
                    Location at = kit.ground(feet.clone().add(facing.clone().multiply(3.0 + 2.6 * index)));
                    kit.later(index * 8L, () -> {
                        for (int tick = 0; tick < 5; tick++) {
                            double r = 2.0 - 1.0 * tick / 4.0;
                            kit.later(tick, () -> kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), r, 36, kit.palette(), 0.4, 0.3, 1.0));
                        }
                        kit.later(5, () -> {
                            kit.burst(at.clone().add(0.0, 0.3, 0.0), 2.2);
                            kit.sound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.0f);
                            kit.hitAll(kit.around(at, 2.2), rate);
                        });
                    });
                }
            }
            case 2 -> ComboParts.pullBurst(kit, target.clone().add(0.0, 1.5, 0.0), 4.5, rate, 40.0);
            case 3 -> ComboParts.homingVolley(kit, 4, rate, 14.0, end -> kit.burst(end, 1.2));
            case 4 -> {
                // a black hole: it pulls for a second and hurts, and bursts
                Location at = target.clone().add(0.0, 1.5, 0.0);
                kit.sound(at, Sfx.MAGIC_DARK, 1.0f, 0.6f);
                kit.every(21, age -> {
                    kit.fx(at).sphereShell(at, 0.7 + 0.2 * Math.sin(age * 0.6), age * 0.5, 32, kit.palette(), 1.2);
                    for (LivingEntity victim : kit.around(at, 6.0)) kit.pull(victim, at, 0.2);
                    if (age % 4 == 0 && age >= 4 && age < 20) kit.hitAll(kit.around(at, 3.0), rate);
                    if (age == 20) {
                        kit.burst(at, 4.5);
                        kit.shockwave(target, 1.0, 4.5, 5, 1.2);
                        kit.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                        kit.hitAll(kit.around(at, 4.5), 0.9);
                    }
                    return false;
                });
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a gate of stars opens over the aim and twelve stars fall round it (3 blocks) one after the other. */
    private void sixth(MoveKit kit, double rate) {
        Location target = ComboParts.target(kit);
        Location gate = target.clone().add(0.0, 5.0, 0.0);
        kit.fx(gate).flatRing(gate, 2.0, 30, kit.palette(), 0.9, 0.1, 1.2);
        kit.sound(target, Sfx.MAGIC, 0.9f, 1.2f);
        ComboParts.rain(kit, target, 3.0, 12, rate, 1.4, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST);
    }
}
