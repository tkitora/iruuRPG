package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.skill.SkillRegistry;
import net.tkgon.mc.iruuRPG.skill.SkillRunner;
import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The mage's combo (see {@link MeleeCombo}, plan {@code mage}) on the ranged weapon. The colors are the weapon's element
 * and purple: the more powerful the attack, the more purple.
 *
 * <p>Normal attacks (right click): 1 and 2 a magic bolt from the upper left (50%) / upper right (80%), 3 a fast scatter
 * of bolts (3 hits of 30%) and 4 a dense one (4 hits of 30%), both coming from the sides and diagonals so they cross,
 * 5 a burst of magic (3 hits of 60%) with two vortexes of light turning opposite ways. Runs of more than one effect are
 * a tick apart and each a little turned from the one before. Between two normal attacks there is a least delay.
 *
 * <p>Heavy attacks (left click, the number of normal attacks so far): 1 a ball of magic flying ahead that bursts for 60%,
 * 2 three homing bolts conjured behind the player (15% each), 3 three fan-shaped areas, one beyond the other, that erupt
 * in spikes after 8 ticks (60%), 4 a sphere ahead that pulls enemies in and hurts them every 2 ticks (10%).
 * Heavy 0 does nothing of its own: while a heavy attack can be reactivated it triggers that (1 bursts the ball at once,
 * 2 conjures five smaller homing bolts (5%), 3 stabs the enemies the spikes hit again from the air (20%), 4 sends out a
 * ring that pulls in again (5%), up to three times). Without a reactivation to trigger, heavy 0 cools down for 3 seconds.
 */
public final class MageMoves {

    private static final Color PURPLE = Color.fromRGB(176, 96, 255);
    private static final Color DEEP_PURPLE = Color.fromRGB(120, 50, 210);

    /** A heavy attack that can be triggered again by heavy 0, until {@code until}, {@code uses} more times. */
    private static final class Pending {
        private final long until;
        private int uses;
        private final Runnable action;

        private Pending(long until, int uses, Runnable action) {
            this.until = until;
            this.uses = uses;
            this.action = action;
        }
    }

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final AttackEffects effects;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public MageMoves(JavaPlugin plugin, AttackService attackService, AttackEffects effects, SkillRegistry skills, SkillRunner runner) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.effects = effects;
    }

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo.mage." + key, fallback);
    }

    /** True while a heavy attack of the player can be triggered again: a heavy 0 then does that (and has no cooldown). */
    public boolean chainActive(Player player) {
        Pending current = pending.get(player.getUniqueId());
        if (current == null) return false;
        if (System.currentTimeMillis() > current.until) {
            pending.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void offer(Player player, long millis, int uses, Runnable action) {
        pending.put(player.getUniqueId(), new Pending(System.currentTimeMillis() + millis, uses, action));
    }

    // ---- colors ----------------------------------------------------------------------------------------------------------

    private static Color elementColor(Element element) {
        return switch (element) {
            case RED -> Color.fromRGB(255, 90, 80);
            case BLUE -> Color.fromRGB(100, 160, 255);
            case GREEN -> Color.fromRGB(120, 235, 140);
            case ORANGE -> Color.fromRGB(255, 165, 60);
            default -> Color.fromRGB(240, 240, 255);
        };
    }

    /** A color of {@code element} and purple: {@code purple} is the share of purple (0 - 1). */
    private static Color pick(Element element, double purple) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < purple) return random.nextBoolean() ? PURPLE : DEEP_PURPLE;
        return elementColor(element);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------------

    private Vector[] basis(Player player) {
        Vector forward = player.getEyeLocation().getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1.0, 0.0, 0.0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        return new Vector[]{forward, right, up};
    }

    private Vector facing(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0.0);
        if (direction.lengthSquared() < 1.0E-6) direction = new Vector(0.0, 0.0, 1.0);
        return direction.normalize();
    }

    /** Where the player is aiming: the first enemy or block in the way, or a point ahead. */
    private Location aimPoint(Player player) {
        return attackService.traceRange(player).hitLocation();
    }

    private List<LivingEntity> around(Player player, Location center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, Math.max(2.0, radius * 0.6), radius)) {
            if (!attackService.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            Location at = living.getLocation().add(0.0, living.getHeight() * 0.5, 0.0);
            if (at.distanceSquared(center) > radius * radius * 1.5) continue;
            result.add(living);
        }
        return result;
    }

    private boolean hit(Player player, RpgItemDefinition weapon, LivingEntity target, double rate) {
        return attackService.strikeRanged(player, target, weapon, rate);
    }

    private void later(long ticks, Runnable action) {
        if (ticks <= 0L) action.run();
        else plugin.getServer().getScheduler().runTaskLater(plugin, action, ticks);
    }

    private void dust(Player player, World world, Location at, Element element, double purple, float size) {
        ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, at, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(pick(element, purple), size));
    }

    /** A line of light between two points in the weapon's element and purple. */
    private void line(Player player, World world, Location from, Location to, Element element, double purple, float size) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 1.0E-6) return;
        Vector direction = path.normalize();
        int points = Math.max(1, (int) Math.ceil(length / 0.3));
        for (int index = 0; index <= points; index++) {
            Location point = from.clone().add(direction.clone().multiply(length * index / points));
            dust(player, world, point, element, purple, size);
            if (index % 3 == 0) ParticleFx.to(player, world).spawnParticle(Particle.END_ROD, point, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    // ---- normal attacks ----------------------------------------------------------------------------------------------------

    /** The normal attack {@code step.stage()} (1 - 5) of the combo. */
    public void normal(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        switch (step.stage()) {
            case 1 -> bolt(player, weapon, -1.2, 0.8, step.damageMultiplier(), 1.0, 0.0, 0.3);
            case 2 -> bolt(player, weapon, 1.2, 0.8, step.damageMultiplier(), 1.0, 0.0, 0.3);
            case 3 -> scatter(player, weapon, step, false);
            case 4 -> scatter(player, weapon, step, true);
            default -> burstSeries(player, weapon, step);
        }
    }

    /** One bolt from {@code side} (right, negative = left) and {@code up} blocks off the eyes, flying to where the player aims. */
    private void bolt(Player player, RpgItemDefinition weapon, double side, double up, double rate, double speedScale, double spread, double purple) {
        Vector[] axes = basis(player);
        Location start = player.getEyeLocation().add(axes[0].clone().multiply(0.6)).add(axes[1].clone().multiply(side)).add(axes[2].clone().multiply(up));
        Vector direction = aimPoint(player).toVector().subtract(start.toVector());
        if (direction.lengthSquared() < 1.0E-6) direction = axes[0].clone();
        direction.normalize();
        if (spread > 0.0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            direction.add(axes[1].clone().multiply(random.nextDouble(-spread, spread))).add(axes[2].clone().multiply(random.nextDouble(-spread, spread))).normalize();
        }
        World world = player.getWorld();
        Element element = weapon.element();
        attackService.fireRangedProjectile(player, weapon, start, direction, rate, speedScale,
                (from, to) -> line(player, world, from, to, element, purple, 1.1f));
    }

    /**
     * Fast bolts from the sides and the diagonals, all aimed at the same point so their paths cross (3 hits of 30% with a
     * little purple, or 4 hits of 30% with more than half purple and extra bolts around each for the dense one).
     */
    private void scatter(Player player, RpgItemDefinition weapon, MeleeCombo.Step step, boolean dense) {
        int hits = step.extraHits() + 1;
        long gap = (long) number("multi-hit-gap-ticks", 1.0);
        double purple = dense ? 0.6 : 0.25;
        double[][] places = {{-1.4, 0.0}, {1.4, 0.0}, {-1.1, 1.0}, {1.1, 1.0}, {-1.1, -0.8}, {1.1, -0.8}};
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < hits; index++) {
            double[] place = places[random.nextInt(places.length)];
            double side = place[0] + random.nextDouble(-0.2, 0.2);
            double up = place[1] + random.nextDouble(-0.15, 0.15);
            later(gap * index, () -> {
                if (!player.isOnline() || player.isDead()) return;
                bolt(player, weapon, side, up, step.damageMultiplier(), dense ? 1.5 : 1.8, 0.0, purple);
                if (dense) {
                    // the dense scatter: a few more bolts around, for the look only
                    Vector[] axes = basis(player);
                    Location aim = aimPoint(player);
                    for (int extra = 0; extra < 3; extra++) {
                        Location start = player.getEyeLocation().add(axes[0].clone().multiply(0.6))
                                .add(axes[1].clone().multiply(side + random.nextDouble(-0.5, 0.5))).add(axes[2].clone().multiply(up + random.nextDouble(-0.4, 0.4)));
                        Location end = aim.clone().add(random.nextDouble(-0.5, 0.5), random.nextDouble(-0.4, 0.4), random.nextDouble(-0.5, 0.5));
                        line(player, player.getWorld(), start, end, weapon.element(), purple, 1.0f);
                    }
                }
            });
        }
    }

    /** The finisher: a purple burst of magic where the player aims, 3 hits of 60% with two vortexes of light. */
    private void burstSeries(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        int hits = step.extraHits() + 1;
        long gap = (long) number("multi-hit-gap-ticks", 1.0);
        Location center = aimPoint(player);
        double radius = number("burst-radius", 3.5);
        // the flourish of the finisher: a loud start
        World world = player.getWorld();
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.2f, 1.2f);
        world.playSound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 0.9f, 1.1f);
        world.playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.PLAYERS, 0.6f, 1.4f);
        vortexes(player, center, weapon);
        for (int index = 0; index < hits; index++) {
            int repeat = index;
            later(gap * index, () -> {
                if (!player.isOnline() || player.isDead()) return;
                burstAt(player, weapon, center, radius, step.damageMultiplier(), 0.9, repeat);
            });
        }
    }

    /** One burst of magic: damages every enemy within {@code radius} of {@code center}. */
    private void burstAt(Player player, RpgItemDefinition weapon, Location center, double radius, double rate, double purple, int repeat) {
        World world = center.getWorld();
        if (world == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // two rings that open and sparks thrown out, a little turned each time
        for (int ring = 0; ring < 2; ring++) {
            for (int index = 0; index < 24; index++) {
                double angle = Math.PI * 2.0 * index / 24.0 + repeat * 0.4 + ring * 0.13;
                Location point = center.clone().add(Math.cos(angle) * radius * (0.55 + 0.3 * ring), 0.2 + 0.25 * ring, Math.sin(angle) * radius * (0.55 + 0.3 * ring));
                dust(player, world, point, weapon.element(), purple, 1.4f);
            }
        }
        ParticleFx.to(player, world).spawnParticle(Particle.PORTAL, center, 30, radius * 0.3, 0.6, radius * 0.3, 0.8);
        ParticleFx.to(player, world).spawnParticle(Particle.END_ROD, center, 18, radius * 0.3, 0.5, radius * 0.3, 0.14);
        ParticleFx.to(player, world).spawnParticle(Particle.EXPLOSION_LARGE, center, 1);
        ParticleFx.to(player, world).spawnParticle(Particle.FLASH, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 1.5f + random.nextFloat() * 0.3f);
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.8f, 1.2f + repeat * 0.2f);
        effects.playBoltImpact(center, weapon.element());
        for (LivingEntity target : around(player, center, radius)) hit(player, weapon, target, rate);
    }

    /** Two vortexes of light around {@code center}, one turning left and one right, rising for a moment. */
    private void vortexes(Player player, Location center, RpgItemDefinition weapon) {
        World world = center.getWorld();
        if (world == null) return;
        new SafeTask(plugin) {
            private int tick;

            @Override
            protected void tick() {
                if (tick >= 10) {
                    cancel();
                    return;
                }
                for (int arm = 0; arm < 2; arm++) {
                    double direction = arm == 0 ? 1.0 : -1.0;
                    for (int step = 0; step < 8; step++) {
                        double along = step / 7.0;
                        double angle = direction * (tick * 0.7 + along * Math.PI * 2.0) + arm * Math.PI;
                        double radius = 0.4 + 1.6 * along;
                        Location point = center.clone().add(Math.cos(angle) * radius, 0.1 + along * 1.6, Math.sin(angle) * radius);
                        dust(player, world, point, weapon.element(), 0.85, 1.3f);
                        if (step % 3 == 0) ParticleFx.to(player, world).spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- heavy attacks -----------------------------------------------------------------------------------------------------

    /** Throws heavy attack {@code step.heavyIndex()}. */
    public void heavy(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        int index = step.heavyIndex();
        if (index == 0) {
            Pending current = pending.get(player.getUniqueId());
            if (current == null || System.currentTimeMillis() > current.until) {
                // nothing to trigger again
                pending.remove(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.4f);
                return;
            }
            current.uses--;
            if (current.uses <= 0) pending.remove(player.getUniqueId());
            current.action.run();
            return;
        }
        // a new heavy attack replaces what could be triggered again
        pending.remove(player.getUniqueId());
        switch (index) {
            case 1 -> orb(player, weapon, step.damageMultiplier());
            case 2 -> homing(player, weapon, 3, step.damageMultiplier(), 1.0, true);
            case 3 -> spikes(player, weapon, step.damageMultiplier());
            default -> sphere(player, weapon, step.damageMultiplier());
        }
    }

    // 1: a ball of magic that bursts on the first thing it meets, after 0.6 seconds, or when triggered again
    private void orb(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        Vector[] axes = basis(player);
        Location start = player.getEyeLocation().add(axes[0].clone().multiply(0.9)).subtract(0.0, 0.2, 0.0);
        Vector direction = axes[0].clone();
        double speed = number("orb-speed", 1.0);
        int life = (int) Math.max(1, Math.round(number("orb-seconds", 0.6) * 20.0));
        double radius = number("orb-radius", 2.5);
        Element element = weapon.element();
        world.playSound(start, Sound.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.0f, 1.3f);
        world.playSound(start, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.PLAYERS, 0.7f, 1.2f);
        SafeTask[] holder = new SafeTask[1];
        Location[] where = {start.clone()};
        boolean[] done = {false};
        Runnable burst = () -> {
            if (done[0]) return;
            done[0] = true;
            if (holder[0] != null) holder[0].cancel();
            burstAt(player, weapon, where[0], radius, rate, 0.75, 0);
        };
        holder[0] = new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                ParticleFx.mark(player);
                if (!player.isOnline() || done[0]) {
                    cancel();
                    return;
                }
                if (age++ >= life) {
                    burst.run();
                    return;
                }
                RayTraceResult result = world.rayTrace(where[0], direction, speed, FluidCollisionMode.NEVER, true, 0.5,
                        entity -> attackService.isAttackTarget(entity, player));
                Location next = result != null && result.getHitPosition() != null
                        ? result.getHitPosition().toLocation(world)
                        : where[0].clone().add(direction.clone().multiply(speed));
                // the ball: a small sphere of dust around the middle
                for (int index = 0; index < 14; index++) {
                    Vector offset = new Vector(ThreadLocalRandom.current().nextGaussian(), ThreadLocalRandom.current().nextGaussian(), ThreadLocalRandom.current().nextGaussian()).normalize().multiply(0.45);
                    dust(player, world, next.clone().add(offset), element, 0.75, 1.2f);
                }
                ParticleFx.to(player, world).spawnParticle(Particle.END_ROD, next, 2, 0.1, 0.1, 0.1, 0.01);
                where[0] = next;
                if (result != null) burst.run();
            }
        };
        holder[0].runTaskTimer(plugin, 0L, 1L);
        // triggered again: it bursts where it is
        offer(player, life * 50L, 1, burst);
    }

    // 2: homing bolts conjured behind the player (three of 15%, or five smaller ones of 5% when triggered again)
    private void homing(Player player, RpgItemDefinition weapon, int count, double rate, double size, boolean offerAgain) {
        World world = player.getWorld();
        Vector facing = facing(player);
        Vector right = facing.clone().crossProduct(new Vector(0.0, 1.0, 0.0)).normalize();
        Location behind = player.getEyeLocation().subtract(facing.clone().multiply(1.3)).add(0.0, 0.3, 0.0);
        world.playSound(behind, Sound.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.PLAYERS, 0.9f, 1.4f);
        long gap = (long) number("multi-hit-gap-ticks", 1.0);
        for (int index = 0; index < count; index++) {
            double side = (index - (count - 1) / 2.0) * 0.7 * size;
            Location start = behind.clone().add(right.clone().multiply(side)).add(0.0, ThreadLocalRandom.current().nextDouble(-0.2, 0.5), 0.0);
            Vector heading = facing.clone().add(new Vector(0.0, 0.35, 0.0)).add(right.clone().multiply(side * 0.5)).normalize();
            later(gap * index, () -> {
                if (!player.isOnline() || player.isDead()) return;
                homingBolt(player, weapon, start, heading, rate, size);
            });
        }
        if (offerAgain) {
            offer(player, (long) (number("reactivate-seconds", 3.0) * 1000.0), 1,
                    () -> homing(player, weapon, 5, number("homing-small-rate", 0.05), 0.65, false));
        }
    }

    private void homingBolt(Player player, RpgItemDefinition weapon, Location start, Vector heading, double rate, double size) {
        World world = start.getWorld();
        Element element = weapon.element();
        double speed = number("homing-speed", 1.3);
        double seek = number("homing-radius", 18.0);
        int life = (int) number("homing-ticks", 50.0);
        new SafeTask(plugin) {
            private Location position = start.clone();
            private Vector velocity = heading.clone().multiply(speed * 0.6);
            private LivingEntity target;
            private int age;

            @Override
            protected void tick() {
                ParticleFx.mark(player);
                if (!player.isOnline() || age++ > life) {
                    cancel();
                    return;
                }
                if (age >= 4) {
                    if (target == null || target.isDead() || !target.isValid() || age % 6 == 0) {
                        target = nearestTarget(player, position, seek);
                    }
                    if (target != null) {
                        Vector toward = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0).toVector().subtract(position.toVector());
                        if (toward.lengthSquared() > 1.0E-6) velocity.add(toward.normalize().multiply(speed).subtract(velocity).multiply(0.4));
                    }
                }
                double length = velocity.length();
                if (length > speed) velocity.multiply(speed / length);
                if (length < 1.0E-6) return;
                Vector direction = velocity.clone().normalize();
                double step = velocity.length();
                RayTraceResult result = world.rayTrace(position, direction, step, FluidCollisionMode.NEVER, true, 0.5 * size,
                        entity -> attackService.isAttackTarget(entity, player));
                Location next = result != null && result.getHitPosition() != null
                        ? result.getHitPosition().toLocation(world)
                        : position.clone().add(velocity);
                line(player, world, position, next, element, 0.7, (float) (1.1 * size));
                if (result != null) {
                    ParticleFx.to(player, world).spawnParticle(Particle.PORTAL, next, 8, 0.2 * size, 0.2 * size, 0.2 * size, 0.4);
                    world.playSound(next, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.6f, 1.6f);
                    if (result.getHitEntity() instanceof LivingEntity victim) hit(player, weapon, victim, rate);
                    cancel();
                    return;
                }
                position = next;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private LivingEntity nearestTarget(Player player, Location from, double radius) {
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (Entity entity : player.getWorld().getNearbyEntities(from, radius, radius, radius)) {
            if (!attackService.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            double distance = living.getLocation().add(0.0, living.getHeight() * 0.5, 0.0).distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    // 3: three fan-shaped areas, one beyond the other, that erupt in spikes after 8 ticks
    private void spikes(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        Vector facing = facing(player);
        Location origin = player.getLocation().clone();
        Element element = weapon.element();
        double[] near = {0.6, 3.2, 5.8};
        double[] far = {3.2, 5.8, 8.4};
        double[] angle = {50.0, 80.0, 110.0};
        long stage = (long) number("spike-stage-ticks", 3.0);
        long delay = (long) number("spike-delay-ticks", 8.0);
        Set<LivingEntity> struck = new LinkedHashSet<>();
        world.playSound(origin, Sound.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.PLAYERS, 1.0f, 1.2f);
        for (int zone = 0; zone < 3; zone++) {
            int which = zone;
            later(stage * which, () -> {
                if (!player.isOnline() || player.isDead()) return;
                outline(player, world, origin, facing, near[which], far[which], angle[which], element);
                later(delay, () -> {
                    if (!player.isOnline()) return;
                    erupt(player, weapon, world, origin, facing, near[which], far[which], angle[which], rate, struck);
                    if (!struck.isEmpty()) {
                        // enemies were hit: they can be stabbed again from the air
                        offer(player, (long) (number("reactivate-seconds", 3.0) * 1000.0), 1, () -> airSpikes(player, weapon, new ArrayList<>(struck)));
                    }
                });
            });
        }
    }

    private boolean inZone(Location origin, Vector facing, Location at, double near, double far, double angle) {
        Vector to = at.toVector().subtract(origin.toVector()).setY(0.0);
        double distance = to.length();
        if (distance < near || distance > far) return false;
        return distance < 0.01 || Math.toDegrees(facing.angle(to)) <= angle / 2.0;
    }

    private void outline(Player player, World world, Location origin, Vector facing, double near, double far, double angle, Element element) {
        int points = 18;
        for (int index = 0; index <= points; index++) {
            double turn = Math.toRadians(-angle / 2.0 + angle * index / points);
            Vector direction = facing.clone().rotateAroundY(turn);
            dust(player, world, origin.clone().add(direction.clone().multiply(near)).add(0.0, 0.1, 0.0), element, 0.7, 1.2f);
            dust(player, world, origin.clone().add(direction.clone().multiply(far)).add(0.0, 0.1, 0.0), element, 0.7, 1.2f);
            if (index % 3 == 0) dust(player, world, origin.clone().add(direction.clone().multiply((near + far) / 2.0)).add(0.0, 0.1, 0.0), element, 0.8, 1.0f);
        }
        world.playSound(origin.clone().add(facing.clone().multiply((near + far) / 2.0)), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.0f);
    }

    private void erupt(Player player, RpgItemDefinition weapon, World world, Location origin, Vector facing, double near, double far, double angle, double rate, Set<LivingEntity> struck) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int spikes = (int) number("spikes-per-area", 9.0);
        for (int index = 0; index < spikes; index++) {
            double distance = random.nextDouble(near, far);
            double turn = Math.toRadians(random.nextDouble(-angle / 2.0, angle / 2.0));
            Location base = origin.clone().add(facing.clone().rotateAroundY(turn).multiply(distance));
            spike(player, world, base, weapon.element(), random.nextDouble(1.3, 2.4), 0.8);
        }
        world.playSound(origin.clone().add(facing.clone().multiply((near + far) / 2.0)), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(origin.clone().add(facing.clone().multiply((near + far) / 2.0)), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.3f, 1.8f);
        for (Entity entity : world.getNearbyEntities(origin, far, 3.0, far)) {
            if (!attackService.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (!inZone(origin, facing, living.getLocation(), near - 0.5, far + 0.5, angle + 10.0)) continue;
            if (hit(player, weapon, living, rate)) struck.add(living);
        }
    }

    /** A spike of crystal rising from {@code base}. */
    private void spike(Player player, World world, Location base, Element element, double height, double purple) {
        for (double y = 0.0; y <= height; y += 0.25) {
            double width = (1.0 - y / height) * 0.3;
            dust(player, world, base.clone().add(0.0, y, 0.0), element, purple, (float) (1.0 + width * 2.0));
            if (width > 0.1) {
                dust(player, world, base.clone().add(width, y, 0.0), element, purple, 1.0f);
                dust(player, world, base.clone().add(-width, y, 0.0), element, purple, 1.0f);
            }
        }
        ParticleFx.to(player, world).spawnParticle(Particle.CRIT_MAGIC, base.clone().add(0.0, height, 0.0), 2, 0.1, 0.1, 0.1, 0.1);
    }

    /** Triggered again: each enemy a spike hit is stabbed (20%) from points of air within 2 blocks of it. */
    private void airSpikes(Player player, RpgItemDefinition weapon, List<LivingEntity> targets) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double rate = number("air-spike-rate", 0.2);
        int each = (int) number("air-spikes-each", 3.0);
        long gap = (long) number("multi-hit-gap-ticks", 1.0);
        int order = 0;
        for (LivingEntity target : targets) {
            for (int index = 0; index < each; index++) {
                later(gap * order++, () -> {
                    if (!player.isOnline() || target.isDead() || !target.isValid()) return;
                    World world = target.getWorld();
                    Location center = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
                    Location from = null;
                    for (int attempt = 0; attempt < 10 && from == null; attempt++) {
                        Vector offset = new Vector(random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0));
                        if (offset.lengthSquared() < 0.25) continue;
                        Location candidate = center.clone().add(offset.normalize().multiply(random.nextDouble(1.0, 2.0)));
                        if (candidate.getBlock().isPassable() && candidate.clone().add(0.0, 0.5, 0.0).getBlock().isPassable()) from = candidate;
                    }
                    if (from == null) return;
                    // the spike leaves the point and pierces the enemy
                    line(player, world, from, center.clone().add(center.toVector().subtract(from.toVector()).normalize().multiply(0.6)), weapon.element(), 0.85, 1.3f);
                    ParticleFx.to(player, world).spawnParticle(Particle.CRIT_MAGIC, center, 6, 0.2, 0.3, 0.2, 0.3);
                    world.playSound(center, Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.5f, 1.6f);
                    hit(player, weapon, target, rate);
                });
            }
        }
    }

    // 4: a sphere ahead that pulls enemies in gently and hurts them every 2 ticks; up to three rings when triggered again
    private void sphere(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        Vector facing = facing(player);
        Location center = player.getLocation().add(facing.clone().multiply(number("sphere-forward", 3.0))).add(0.0, 1.2, 0.0);
        int life = (int) number("sphere-ticks", 20.0);
        double pullRadius = number("sphere-pull-radius", 1.5);
        double pull = number("sphere-pull", 0.12);
        Element element = weapon.element();
        long millis = life * 50L;
        boolean[] gone = {false};
        world.playSound(center, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.7f);
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || age >= life) {
                    gone[0] = true;
                    cancel();
                    return;
                }
                // the sphere: a shell of dust turning slowly, with a bright core
                for (int index = 0; index < 22; index++) {
                    double yaw = Math.PI * 2.0 * index / 22.0 + age * 0.35;
                    double pitch = Math.sin(index * 1.7 + age * 0.4) * 1.2;
                    Location point = center.clone().add(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
                    dust(player, world, point, element, 0.8, 1.2f);
                }
                ParticleFx.to(player, world).spawnParticle(Particle.PORTAL, center, 6, 0.4, 0.4, 0.4, 0.6);
                ParticleFx.to(player, world).spawnParticle(Particle.END_ROD, center, 1, 0.1, 0.1, 0.1, 0.0);
                if (age % 2 == 0) {
                    for (LivingEntity target : around(player, center, pullRadius)) {
                        // pulled a little at a time: the enemy can still move
                        Vector toward = center.toVector().subtract(target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0).toVector());
                        if (toward.lengthSquared() > 0.04) {
                            Vector velocity = target.getVelocity().multiply(0.85).add(toward.normalize().multiply(pull));
                            target.setVelocity(velocity);
                        }
                        hit(player, weapon, target, rate);
                    }
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        offer(player, millis, 3, () -> {
            if (gone[0]) return;
            ring(player, weapon, center);
        });
    }

    /** The ring sent out by the sphere: enemies within 3 blocks take 5% and are pulled to it; the color is the weapon's. */
    private void ring(Player player, RpgItemDefinition weapon, Location center) {
        World world = center.getWorld();
        if (world == null) return;
        double radius = number("ring-radius", 3.0);
        double rate = number("ring-rate", 0.05);
        double pull = number("ring-pull", 0.55);
        Particle.DustOptions dust = new Particle.DustOptions(elementColor(weapon.element()), 1.5f);
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.9f);
        world.playSound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 0.6f, 1.6f);
        for (int step = 1; step <= 4; step++) {
            double r = radius * step / 4.0;
            later(step - 1, () -> {
                int points = (int) Math.max(18, r * 12);
                for (int index = 0; index < points; index++) {
                    double angle = Math.PI * 2.0 * index / points;
                    ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, center.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r), 1, 0.0, 0.0, 0.0, 0.0, dust);
                }
            });
        }
        for (LivingEntity target : around(player, center, radius)) {
            hit(player, weapon, target, rate);
            Vector toward = center.toVector().subtract(target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0).toVector());
            if (toward.lengthSquared() > 0.04) target.setVelocity(toward.normalize().multiply(pull).setY(0.1));
        }
    }
}
