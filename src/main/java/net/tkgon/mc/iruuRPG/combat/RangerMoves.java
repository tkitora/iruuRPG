package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The ranger's combo (see {@link MeleeCombo}, plan {@code ranger}) and the loop of its three kinds of attack.
 *
 * <p>The loop: a melee hit powers up the next ranged normal attack (+10% damage, faster bullet); a ranged hit makes the
 * player's deployed weapons resonate (2% of their damage on the enemies around each of them); damage from a deployed
 * weapon (or a trap) powers up melee attacks for 5 seconds (+10%, and faster movement while attacking).
 *
 * <p>Normal attacks (a ranged weapon's right click; a melee weapon's left click for the fifth): 1 and 2 a fast shot,
 * 3 three quick shots (40% each) that waver, 4 a fan of three shots (35% each), 5 a burst at close range (three hits
 * of 40%, thrown with a melee weapon, nothing happens with another one and the count goes on), 6 one strong shot (100%)
 * with a piercing beam.
 *
 * <p>Heavy attacks (the other click, the number of normal attacks so far): 0 a step back that leaves a trap (5 s
 * cooldown), 1 a fan of arrows (70 degrees, 5 blocks, 80%), 2 a hop back with two wires forward (40% each), 3 arrows
 * raining on the ground all around while the player hangs in the air (60%), 4 a downpour of 20 hits of 7% in front.
 * Heavy 0 with a deployed weapon within 2 seconds of heavy 2's hop is heavy 3.
 */
public final class RangerMoves {

    /** Heavy 0 this soon after heavy 2's hop (with a deployed weapon in hand) is heavy 3. */
    private static final long HOP_CHAIN_MILLIS = 2000L;

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final AttackEffects effects;
    private DeployService deployService;
    private final Map<UUID, Long> rangeBoostUntil = new HashMap<>();
    private final Map<UUID, Long> meleeBoostUntil = new HashMap<>();
    private final Map<UUID, Long> lastHop = new HashMap<>();
    private final Map<UUID, Integer> lastResonanceTick = new HashMap<>();
    private final List<Trap> traps = new ArrayList<>();
    private BukkitTask trapTask;

    private static final class Trap {
        private final UUID owner;
        private final Location at;
        private final RpgItemDefinition weapon;
        private final long expires;

        private Trap(UUID owner, Location at, RpgItemDefinition weapon, long expires) {
            this.owner = owner;
            this.at = at;
            this.weapon = weapon;
            this.expires = expires;
        }
    }

    public RangerMoves(JavaPlugin plugin, AttackService attackService, AttackEffects effects) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.effects = effects;
    }

    public void setDeployService(DeployService deployService) {
        this.deployService = deployService;
    }

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo.ranger." + key, fallback);
    }

    public void shutdown() {
        if (trapTask != null) trapTask.cancel();
        trapTask = null;
        traps.clear();
    }

    // ---- the loop ------------------------------------------------------------------------------------------------------

    /** Called after every hit of a ranger: the weapon's kind decides which of the three powers up. */
    public void onHit(Player attacker, LivingEntity victim, RpgItemDefinition weapon) {
        if (weapon == null) return;
        switch (weapon.attackType()) {
            case MELEE -> grantRangeBoost(attacker);
            case RANGE -> resonate(attacker);
            case DEPLOY -> grantMeleeBoost(attacker);
            default -> {
            }
        }
    }

    /** A melee hit (or heavy 1): the next ranged normal attack is stronger and faster. */
    public void grantRangeBoost(Player player) {
        long now = System.currentTimeMillis();
        Long until = rangeBoostUntil.get(player.getUniqueId());
        boolean fresh = until == null || until < now;
        rangeBoostUntil.put(player.getUniqueId(), now + Math.round(number("range-boost-seconds", 10.0) * 1000.0));
        if (fresh) {
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.8f);
            ParticleFx.to(player, world).spawnParticle(Particle.CRIT_MAGIC, player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.5)), 8, 0.2, 0.2, 0.2, 0.2);
        }
    }

    /** Damage from a deployed weapon or a trap: melee attacks are stronger for a while. */
    public void grantMeleeBoost(Player player) {
        long now = System.currentTimeMillis();
        Long until = meleeBoostUntil.get(player.getUniqueId());
        boolean fresh = until == null || until < now;
        meleeBoostUntil.put(player.getUniqueId(), now + Math.round(number("melee-boost-seconds", 5.0) * 1000.0));
        if (fresh) {
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.3f);
            ParticleFx.to(player, world).spawnParticle(Particle.FLAME, player.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** What a melee hit of {@code player} is multiplied by now (the boost from deployed damage; also speeds the player up). */
    public double meleeMultiplier(Player player) {
        Long until = meleeBoostUntil.get(player.getUniqueId());
        if (until == null) return 1.0;
        if (System.currentTimeMillis() > until) {
            meleeBoostUntil.remove(player.getUniqueId());
            return 1.0;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, false, false, true));
        return 1.0 + number("melee-boost-percent", 10.0) / 100.0;
    }

    /** {damage multiplier, bullet speed multiplier} of the next ranged normal attack; takes the boost. */
    private double[] takeRangeBoost(Player player) {
        Long until = rangeBoostUntil.remove(player.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return new double[]{1.0, 1.0};
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.5f, 1.9f);
        return new double[]{1.0 + number("range-boost-percent", 10.0) / 100.0, number("range-boost-speed", 1.5)};
    }

    /** A ranged hit: every deployed weapon of the player pulses once for 2% of its damage (not more than once in 4 ticks). */
    private void resonate(Player player) {
        if (deployService == null) return;
        int tick = org.bukkit.Bukkit.getCurrentTick();
        Integer last = lastResonanceTick.get(player.getUniqueId());
        if (last != null && tick - last < (int) number("resonance-gap-ticks", 4.0)) return;
        lastResonanceTick.put(player.getUniqueId(), tick);
        deployService.resonate(player, number("resonance-rate", 0.02));
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------------

    private static Color color(Element element) {
        return switch (element) {
            case RED -> Color.fromRGB(255, 80, 70);
            case BLUE -> Color.fromRGB(90, 150, 255);
            case GREEN -> Color.fromRGB(110, 235, 130);
            case ORANGE -> Color.fromRGB(255, 160, 50);
            default -> Color.fromRGB(245, 245, 255);
        };
    }

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

    private void later(long ticks, Runnable action) {
        if (ticks <= 0L) action.run();
        else plugin.getServer().getScheduler().runTaskLater(plugin, action, ticks);
    }

    /** One hit of {@code rate} times the damage of {@code weapon} (a ranged weapon hits as a ranged attack, else as the weapon's own kind). */
    private boolean dealt(Player player, LivingEntity target, RpgItemDefinition weapon, double rate) {
        if (weapon.attackType() == AttackType.RANGE) return attackService.strikeRanged(player, target, weapon, rate);
        return attackService.strike(player, target, weapon, rate);
    }

    private List<LivingEntity> around(Player player, Location center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, 2.5, radius)) {
            if (!attackService.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            double dx = living.getLocation().getX() - center.getX();
            double dz = living.getLocation().getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            result.add(living);
        }
        return result;
    }

    /** The enemies within {@code width} of the line from {@code from} along {@code direction} for {@code length}. */
    private List<LivingEntity> alongLine(Player player, Location from, Vector direction, double length, double width) {
        List<LivingEntity> result = new ArrayList<>();
        Vector end = from.toVector().add(direction.clone().multiply(length));
        Vector low = Vector.getMinimum(from.toVector(), end).subtract(new Vector(width, width, width));
        Vector high = Vector.getMaximum(from.toVector(), end).add(new Vector(width, width + 1.0, width));
        for (Entity entity : player.getWorld().getNearbyEntities(org.bukkit.util.BoundingBox.of(low, high))) {
            if (!attackService.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            Vector center = living.getLocation().toVector().add(new Vector(0.0, living.getHeight() * 0.5, 0.0));
            double along = center.clone().subtract(from.toVector()).dot(direction);
            if (along < -0.5 || along > length + 0.5) continue;
            Vector nearest = from.toVector().add(direction.clone().multiply(Math.max(0.0, Math.min(length, along))));
            if (nearest.distanceSquared(center) <= width * width) result.add(living);
        }
        return result;
    }

    /** A straight line of dust (and sparks) between two points. */
    private void line(Player player, World world, Location from, Location to, Color color, float size, Particle spark, int sparkEvery) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 1.0E-6) return;
        Vector direction = path.normalize();
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int points = Math.max(1, (int) Math.ceil(length / 0.3));
        for (int index = 0; index <= points; index++) {
            Location point = from.clone().add(direction.clone().multiply(length * index / points));
            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
            if (spark != null && index % sparkEvery == 0) ParticleFx.to(player, world).spawnParticle(spark, point, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // ---- normal attacks -------------------------------------------------------------------------------------------------

    /** Normal attack {@code step.stage()} thrown with the ranged weapon {@code weapon} (a right click). */
    public void normal(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        switch (step.stage()) {
            case 1 -> {
                double[] boost = takeRangeBoost(player);
                shot(player, weapon, 0.35, -0.12, step.damageMultiplier() * boost[0], 2.0 * boost[1], 0.0);
            }
            case 2 -> {
                double[] boost = takeRangeBoost(player);
                shot(player, weapon, -0.35, -0.12, step.damageMultiplier() * boost[0], 2.0 * boost[1], 0.0);
            }
            case 3 -> triple(player, weapon, step);
            case 4 -> fan(player, weapon, step);
            case 5 -> whiff(player);
            default -> strongShot(player, weapon, step);
        }
    }

    /** The fifth normal attack with a weapon that is not a melee one, or with nobody close: nothing is hit, the count goes on. */
    private void whiff(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 0.5f, 1.4f);
    }

    /** One fast bullet from {@code side} (right) and {@code up} off the eyes, flying to where the player aims. */
    private void shot(Player player, RpgItemDefinition weapon, double side, double up, double rate, double speedScale, double spread) {
        Vector[] axes = basis(player);
        Location start = player.getEyeLocation().add(axes[0].clone().multiply(0.6)).add(axes[1].clone().multiply(side)).add(axes[2].clone().multiply(up));
        Vector direction = attackService.traceRange(player).hitLocation().toVector().subtract(start.toVector());
        if (direction.lengthSquared() < 1.0E-6) direction = axes[0].clone();
        direction.normalize();
        if (spread > 0.0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            direction.add(axes[1].clone().multiply(random.nextDouble(-spread, spread))).add(axes[2].clone().multiply(random.nextDouble(-spread, spread))).normalize();
        }
        fire(player, weapon, start, direction, rate, speedScale);
    }

    private void fire(Player player, RpgItemDefinition weapon, Location start, Vector direction, double rate, double speedScale) {
        World world = player.getWorld();
        Color color = color(weapon.element());
        double scale = attackService.rangedIsInstant(player) ? speedScale * 8.0 : speedScale;
        attackService.fireRangedProjectile(player, weapon, start, direction, rate, scale,
                (from, to) -> line(player, world, from, to, color, 1.0f, Particle.CRIT, 2));
    }

    /** Three quick shots one tick apart; the second and the third waver. */
    private void triple(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        double[] boost = takeRangeBoost(player);
        int shots = step.extraHits() + 1;
        for (int index = 0; index < shots; index++) {
            int which = index;
            later(which, () -> {
                if (!player.isOnline() || player.isDead()) return;
                shot(player, weapon, which % 2 == 0 ? 0.3 : -0.3, -0.1, step.damageMultiplier() * boost[0], 2.2 * boost[1], which == 0 ? 0.0 : 0.08);
            });
        }
    }

    /** Three shots spread over a fan, all at once. */
    private void fan(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        double[] boost = takeRangeBoost(player);
        Vector[] axes = basis(player);
        Location start = player.getEyeLocation().add(axes[0].clone().multiply(0.6)).add(axes[2].clone().multiply(-0.1));
        double angle = number("fan-angle", 14.0);
        for (int index = 0; index < 3; index++) {
            double turn = Math.toRadians((index - 1) * angle);
            Vector direction = axes[0].clone().multiply(Math.cos(turn)).add(axes[1].clone().multiply(Math.sin(turn))).normalize();
            fire(player, weapon, start.clone(), direction, step.damageMultiplier() * boost[0], 2.0 * boost[1]);
        }
    }

    /** The finisher: one strong shot and a beam that goes on through everything. */
    private void strongShot(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        double[] boost = takeRangeBoost(player);
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = attackService.rangeDistance(player);
        AttackService.RangeTrace trace = attackService.traceRange(player);
        Color color = color(weapon.element());
        world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.9f, 1.4f);
        world.playSound(eye, Sound.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.9f, 0.8f);
        Location start = eye.clone().add(direction.clone().multiply(0.7)).subtract(0.0, 0.1, 0.0);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
        Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 1.1f);
        // the beam is drawn in four stretches, so it seems to run out of the muzzle
        for (int part = 0; part < 4; part++) {
            int which = part;
            later(which, () -> {
                if (!player.isOnline()) return;
                double from = range * which / 4.0;
                double to = range * (which + 1) / 4.0;
                for (double along = from; along < to; along += 0.25) {
                    Location point = start.clone().add(direction.clone().multiply(along));
                    ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
                    ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.05, 0.05, 0.05, 0.0, white);
                    if (((int) (along / 0.25)) % 3 == 0) ParticleFx.to(player, world).spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
                }
                // rings round the beam: the piercing look
                Vector[] axes = basis(player);
                for (double along = from + 1.0; along < to; along += 2.5) {
                    Location center = start.clone().add(direction.clone().multiply(along));
                    for (int index = 0; index < 10; index++) {
                        double angle = Math.PI * 2.0 * index / 10.0;
                        Location point = center.clone().add(axes[1].clone().multiply(Math.cos(angle) * 0.6)).add(axes[2].clone().multiply(Math.sin(angle) * 0.6));
                        ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
                    }
                }
            });
        }
        if (trace.victim() != null) {
            attackService.hitWithShot(player, weapon, trace.victim(), trace.hitLocation(), step.damageMultiplier() * boost[0]);
        } else {
            attackService.playRangeImpact(player, trace.hitLocation(), weapon);
        }
    }

    /**
     * The fifth normal attack with a melee weapon: three quick hits of 40% on the enemies close in front, drawn as
     * short streaks across, slanting and up and down, in random order. True when there was somebody to hit.
     */
    public boolean closeBurst(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        if (!hasTargetsClose(player)) {
            whiff(player);
            return false;
        }
        int hits = step.extraHits() + 1;
        double[] angles = {0.0, 45.0, 90.0, -45.0};
        List<Double> order = new ArrayList<>();
        for (double angle : angles) order.add(angle);
        java.util.Collections.shuffle(order);
        for (int index = 0; index < hits; index++) {
            int which = index;
            later(which, () -> {
                if (!player.isOnline() || player.isDead()) return;
                Vector[] axes = basis(player);
                Location center = player.getEyeLocation().add(axes[0].clone().multiply(1.6)).subtract(0.0, 0.3, 0.0);
                double angle = Math.toRadians(order.get(which % order.size()));
                Vector along = axes[1].clone().multiply(Math.cos(angle)).add(axes[2].clone().multiply(Math.sin(angle))).normalize();
                World world = player.getWorld();
                Color color = color(weapon.element());
                Location a = center.clone().add(along.clone().multiply(1.5));
                Location b = center.clone().subtract(along.clone().multiply(1.5));
                line(player, world, a, b, color, 1.3f, Particle.CRIT, 2);
                ParticleFx.to(player, world).spawnParticle(Particle.SWEEP_ATTACK, center, 1, 0.0, 0.0, 0.0, 0.0);
                world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.6f, 1.5f + which * 0.15f);
                for (LivingEntity target : closeTargets(player)) attackService.strike(player, target, weapon, step.damageMultiplier());
            });
        }
        return true;
    }

    private boolean hasTargetsClose(Player player) {
        return !closeTargets(player).isEmpty();
    }

    private List<LivingEntity> closeTargets(Player player) {
        double radius = number("close-radius", 3.5);
        double halfAngle = number("close-half-angle", 80.0);
        Vector facing = facing(player);
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity target : around(player, player.getLocation(), radius)) {
            Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
            if (to.lengthSquared() > 1.0 && Math.toDegrees(facing.angle(to)) > halfAngle) continue;
            result.add(target);
        }
        return result;
    }

    // ---- heavy attacks -----------------------------------------------------------------------------------------------------

    /** True within 2 seconds of heavy 2's hop with a deployed weapon in hand: a heavy 0 then is heavy 3. */
    public boolean hopChainActive(Player player, RpgItemDefinition weapon) {
        Long hopped = lastHop.get(player.getUniqueId());
        return weapon != null && weapon.attackType() == AttackType.DEPLOY && hopped != null && System.currentTimeMillis() - hopped <= HOP_CHAIN_MILLIS;
    }

    /** Throws heavy attack {@code step.heavyIndex()} (with whatever weapon is in hand). */
    public void heavy(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        switch (step.heavyIndex()) {
            case 0 -> {
                if (hopChainActive(player, weapon)) {
                    lastHop.remove(player.getUniqueId());
                    rain(player, weapon, attackService.heavyMultiplier(MeleeCombo.RANGER, 3));
                } else {
                    trap(player, weapon, step.damageMultiplier());
                }
            }
            case 1 -> arrowFan(player, weapon, step.damageMultiplier());
            case 2 -> wires(player, weapon, step.damageMultiplier());
            case 3 -> rain(player, weapon, step.damageMultiplier());
            default -> downpour(player, weapon, step.damageMultiplier());
        }
    }

    // 0: the step back and the trap
    private void trap(Player player, RpgItemDefinition weapon, double rate) {
        Vector facing = facing(player);
        Location at = player.getLocation().clone();
        at.setPitch(0.0f);
        player.setFallDistance(0.0f);
        player.setVelocity(facing.clone().multiply(-number("step-back-power", 0.7)).setY(0.15));
        effects.playStepTrail(player, weapon, at, facing.clone().multiply(-1.0));
        long life = Math.round(number("trap-seconds", 30.0) * 1000.0);
        // at most three traps of a player at once: the oldest goes
        int mine = 0;
        for (Trap trap : traps) if (trap.owner.equals(player.getUniqueId())) mine++;
        while (mine >= (int) number("trap-max", 3.0)) {
            for (Iterator<Trap> it = traps.iterator(); it.hasNext(); ) {
                Trap trap = it.next();
                if (trap.owner.equals(player.getUniqueId())) {
                    it.remove();
                    mine--;
                    break;
                }
            }
        }
        traps.add(new Trap(player.getUniqueId(), at, weapon, System.currentTimeMillis() + life));
        player.getWorld().playSound(at, Sound.BLOCK_TRIPWIRE_ATTACH, SoundCategory.PLAYERS, 0.8f, 0.8f);
        ensureTrapTask();
    }

    private void ensureTrapTask() {
        if (trapTask != null) return;
        trapTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickTraps, 5L, 5L);
    }

    private int trapTicks;

    private void tickTraps() {
        trapTicks++;
        double trigger = number("trap-trigger-radius", 1.6);
        long now = System.currentTimeMillis();
        for (Iterator<Trap> it = traps.iterator(); it.hasNext(); ) {
            Trap trap = it.next();
            Player owner = plugin.getServer().getPlayer(trap.owner);
            World world = trap.at.getWorld();
            if (owner == null || !owner.isOnline() || world == null || now > trap.expires || !owner.getWorld().equals(world)) {
                it.remove();
                continue;
            }
            // the sensor: a ring on the ground and a small blinking light
            if (trapTicks % 2 == 0) {
                Particle.DustOptions dust = new Particle.DustOptions(color(trap.weapon.element()), 1.0f);
                for (int index = 0; index < 12; index++) {
                    double angle = Math.PI * 2.0 * index / 12.0 + trapTicks * 0.2;
                    ParticleFx.to(owner, world).spawnParticle(Particle.REDSTONE, trap.at.clone().add(Math.cos(angle) * trigger * 0.8, 0.1, Math.sin(angle) * trigger * 0.8), 1, 0.0, 0.0, 0.0, 0.0, dust);
                }
                ParticleFx.to(owner, world).spawnParticle(Particle.ELECTRIC_SPARK, trap.at.clone().add(0.0, 0.25, 0.0), 1, 0.1, 0.05, 0.1, 0.0);
            }
            boolean sprung = false;
            for (Entity entity : world.getNearbyEntities(trap.at, trigger, 1.5, trigger)) {
                if (attackService.isAttackTarget(entity, owner)) {
                    sprung = true;
                    break;
                }
            }
            if (!sprung) continue;
            it.remove();
            spring(owner, trap);
        }
        if (traps.isEmpty() && trapTask != null) {
            trapTask.cancel();
            trapTask = null;
        }
    }

    /** An enemy stepped on a trap: everything around is slowed and takes damage over time. */
    private void spring(Player owner, Trap trap) {
        World world = trap.at.getWorld();
        double radius = number("trap-radius", 3.0);
        double rate = attackService.heavyMultiplier(MeleeCombo.RANGER, 0);
        int ticks = (int) number("trap-ticks", 5.0);
        long gap = (long) number("trap-tick-gap", 10.0);
        world.playSound(trap.at, Sound.BLOCK_PISTON_EXTEND, SoundCategory.PLAYERS, 0.9f, 1.4f);
        world.playSound(trap.at, Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.6f, 0.7f);
        ParticleFx.to(owner, world).spawnParticle(Particle.CRIT, trap.at.clone().add(0.0, 0.4, 0.0), 24, radius * 0.4, 0.3, radius * 0.4, 0.2);
        for (LivingEntity target : around(owner, trap.at, radius)) {
            attackService.slow(target, number("trap-slow-percent", 50.0), Math.round(number("trap-slow-seconds", 3.0) * 1000.0), owner.getUniqueId());
        }
        for (int index = 0; index < ticks; index++) {
            later(gap * index, () -> {
                if (!owner.isOnline() || owner.isDead()) return;
                Particle.DustOptions dust = new Particle.DustOptions(color(trap.weapon.element()), 1.2f);
                for (int point = 0; point < 16; point++) {
                    double angle = Math.PI * 2.0 * point / 16.0;
                    ParticleFx.to(owner, world).spawnParticle(Particle.REDSTONE, trap.at.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius), 1, 0.0, 0.0, 0.0, 0.0, dust);
                }
                boolean any = false;
                for (LivingEntity target : around(owner, trap.at, radius)) {
                    if (dealt(owner, target, trap.weapon, rate)) any = true;
                }
                // the trap's damage is a deployed weapon's: melee attacks get stronger
                if (any) grantMeleeBoost(owner);
            });
        }
    }

    // 1: the fan of arrows
    private void arrowFan(Player player, RpgItemDefinition weapon, double rate) {
        double radius = number("fan-radius", 5.0);
        double spread = number("fan-arrow-angle", 70.0);
        Vector facing = facing(player);
        World world = player.getWorld();
        Color color = color(weapon.element());
        Location origin = player.getLocation().add(0.0, 1.1, 0.0);
        world.playSound(origin, Sound.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.9f, 1.2f);
        world.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 1.6f);
        int arrows = 9;
        int ticks = 4;
        // arrows that spread outwards from the player over a few ticks
        for (int tick = 0; tick < ticks; tick++) {
            int which = tick;
            later(which, () -> {
                if (!player.isOnline()) return;
                double from = radius * which / ticks;
                double to = radius * (which + 1) / ticks;
                for (int index = 0; index < arrows; index++) {
                    double turn = Math.toRadians(-spread / 2.0 + spread * index / (arrows - 1));
                    Vector direction = facing.clone().rotateAroundY(turn);
                    line(player, world, origin.clone().add(direction.clone().multiply(from)), origin.clone().add(direction.clone().multiply(to)), color, 1.1f, Particle.CRIT, 2);
                }
            });
        }
        boolean any = false;
        for (LivingEntity target : around(player, player.getLocation(), radius)) {
            Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
            if (to.lengthSquared() > 1.0 && Math.toDegrees(facing.angle(to)) > spread / 2.0) continue;
            if (dealt(player, target, weapon, rate)) any = true;
        }
        // a close-range attack, whatever is in hand: the next ranged normal attack is stronger
        if (any) grantRangeBoost(player);
    }

    // 2: the hop back and two wires
    private void wires(Player player, RpgItemDefinition weapon, double rate) {
        Vector facing = facing(player);
        player.setFallDistance(0.0f);
        player.setVelocity(facing.clone().multiply(-number("hop-back-power", 0.8)).setY(number("hop-up-power", 0.75)));
        effects.playStepTrail(player, weapon, player.getLocation(), facing.clone().multiply(-1.0));
        lastHop.put(player.getUniqueId(), System.currentTimeMillis());
        double length = number("wire-length", 8.0);
        for (int index = 0; index < 2; index++) {
            int which = index;
            later(which, () -> {
                if (!player.isOnline() || player.isDead()) return;
                Vector[] axes = basis(player);
                Location start = player.getEyeLocation().add(axes[1].clone().multiply(which == 0 ? -0.4 : 0.4)).subtract(0.0, 0.3, 0.0);
                Vector direction = axes[0].clone().add(axes[1].clone().multiply(which == 0 ? 0.04 : -0.04)).normalize();
                World world = player.getWorld();
                Location end = start.clone().add(direction.clone().multiply(length));
                RayTraceResult wall = world.rayTraceBlocks(start, direction, length, FluidCollisionMode.NEVER, true);
                double reach = wall == null ? length : wall.getHitPosition().distance(start.toVector());
                end = start.clone().add(direction.clone().multiply(reach));
                line(player, world, start, end, Color.fromRGB(215, 225, 235), 0.8f, Particle.ELECTRIC_SPARK, 3);
                line(player, world, start, end, color(weapon.element()), 0.6f, null, 1);
                world.playSound(start, Sound.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.8f, 1.2f + which * 0.2f);
                for (LivingEntity target : alongLine(player, start, direction, reach, 0.9)) dealt(player, target, weapon, rate);
            });
        }
    }

    // 3: arrows raining on the ground all around while the player hangs in the air
    private void rain(Player player, RpgItemDefinition weapon, double rate) {
        World world = player.getWorld();
        double radius = number("rain-radius", 4.0);
        int arrows = (int) number("rain-arrows", 16.0);
        int ticks = (int) number("rain-ticks", 10.0);
        Location base = player.getLocation().clone();
        Color color = color(weapon.element());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        player.setFallDistance(0.0f);
        player.setVelocity(new Vector(0.0, 0.05, 0.0));
        world.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.9f, 1.2f);
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead() || age >= ticks + 3) {
                    cancel();
                    return;
                }
                // hanging in the air, looking where it was
                player.setFallDistance(0.0f);
                if (age < ticks) {
                    Vector velocity = player.getVelocity();
                    player.setVelocity(new Vector(velocity.getX() * 0.5, 0.03, velocity.getZ() * 0.5));
                    int from = arrows * age / ticks;
                    int to = arrows * (age + 1) / ticks;
                    for (int index = from; index < to; index++) {
                        double angle = random.nextDouble(Math.PI * 2.0);
                        double distance = Math.sqrt(random.nextDouble()) * radius;
                        Location ground = groundAt(world, base.getX() + Math.cos(angle) * distance, base.getY() + 1.0, base.getZ() + Math.sin(angle) * distance);
                        arrowFall(player, weapon, ground, color, rate);
                    }
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Location groundAt(World world, double x, double y, double z) {
        RayTraceResult result = world.rayTraceBlocks(new Location(world, x, y, z), new Vector(0.0, -1.0, 0.0), 10.0, FluidCollisionMode.NEVER, true);
        double groundY = result == null ? y - 1.0 : result.getHitPosition().getY();
        return new Location(world, x, groundY, z);
    }

    /** One arrow falling at a slant onto {@code ground}: drawn in two steps, then a burst that hurts what is near. */
    private void arrowFall(Player player, RpgItemDefinition weapon, Location ground, Color color, double rate) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = ground.getWorld();
        Location top = ground.clone().add(random.nextDouble(-1.5, 1.5), 9.0, random.nextDouble(-1.5, 1.5));
        Location middle = top.clone().add(ground.clone().subtract(top).multiply(0.5));
        line(player, world, top, middle, color, 1.0f, null, 1);
        later(1L, () -> {
            line(player, world, middle, ground, color, 1.2f, Particle.CRIT, 2);
            ParticleFx.to(player, world).spawnParticle(Particle.CRIT, ground.clone().add(0.0, 0.2, 0.0), 6, 0.2, 0.1, 0.2, 0.15);
            world.playSound(ground, Sound.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.35f, 1.3f + random.nextFloat() * 0.4f);
            for (LivingEntity target : around(player, ground, 1.6)) dealt(player, target, weapon, rate);
        });
    }

    // 4: the downpour in front
    private void downpour(Player player, RpgItemDefinition weapon, double rate) {
        Vector facing = facing(player);
        int hits = (int) Math.max(1, Math.round(number("downpour-hits", 20.0)));
        int perTick = (int) Math.max(1, Math.round(number("downpour-hits-per-tick", 2.0)));
        double range = number("downpour-radius", 4.0);
        double ahead = number("downpour-forward", 3.0);
        Color color = color(weapon.element());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location center = player.getLocation().add(facing.clone().multiply(ahead)).add(0.0, 1.0, 0.0);
        player.getWorld().playSound(center, Sound.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.9f, 0.9f);
        int ticks = (hits + perTick - 1) / perTick;
        for (int tick = 0; tick < ticks; tick++) {
            int count = Math.min(perTick, hits - tick * perTick);
            later(tick, () -> {
                if (!player.isOnline() || player.isDead()) return;
                World world = player.getWorld();
                for (int index = 0; index < count; index++) {
                    // a bullet of light from the sky or a slant, to a spot in the area (a little longer than the usual ones)
                    double angle = random.nextDouble(Math.PI * 2.0);
                    double distance = Math.sqrt(random.nextDouble()) * range;
                    Location target = center.clone().add(Math.cos(angle) * distance, -0.8 + random.nextDouble(0.0, 0.6), Math.sin(angle) * distance);
                    double fromAngle = random.nextDouble(Math.PI * 2.0);
                    double slant = random.nextDouble(0.2, 1.0);
                    Vector back = new Vector(Math.cos(fromAngle) * slant, 1.0, Math.sin(fromAngle) * slant).normalize().multiply(random.nextDouble(6.0, 8.0));
                    Location from = target.clone().add(back);
                    line(player, world, from, target, color, 1.0f, Particle.END_ROD, 4);
                    ParticleFx.to(player, world).spawnParticle(Particle.CRIT, target, 2, 0.1, 0.1, 0.1, 0.1);
                }
                for (LivingEntity victim : around(player, center, range)) {
                    for (int index = 0; index < count; index++) dealt(player, victim, weapon, rate);
                }
            });
        }
    }
}
