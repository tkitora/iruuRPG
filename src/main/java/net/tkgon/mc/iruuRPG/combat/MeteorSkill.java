package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.skill.PlayerSkillCaster;
import net.tkgon.mc.iruuRPG.skill.SkillRegistry;
import net.tkgon.mc.iruuRPG.skill.SkillRunner;
import net.tkgon.mc.iruuRPG.skill.SkillSpec;
import net.tkgon.mc.iruuRPG.skill.SkillTemplate;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The mage's class skill 流星: a magic circle is drawn in front of the mage and charges for a moment, then meteors
 * leave it one after another and fly where the mage looks along curved, streamlined paths; after a short while a
 * meteor with an enemy near it homes in on it. The upgrade (流星・極) sends more meteors, makes the mage charge
 * while they go out and, when the last one has left, fires a long, thick beam.
 */
final class MeteorSkill {

    private static final Particle.DustOptions TRAIL = new Particle.DustOptions(Color.fromRGB(80, 140, 255), 1.3f);
    private static final Particle.DustOptions TRAIL_LIGHT = new Particle.DustOptions(Color.fromRGB(190, 225, 255), 1.0f);
    private static final Particle.DustOptions CIRCLE = new Particle.DustOptions(Color.fromRGB(110, 170, 255), 1.1f);

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private SkillRegistry skillRegistry;
    private SkillRunner skillRunner;

    MeteorSkill(JavaPlugin plugin, AttackService attackService) {
        this.plugin = plugin;
        this.attackService = attackService;
    }

    void setBeam(SkillRegistry skillRegistry, SkillRunner skillRunner) {
        this.skillRegistry = skillRegistry;
        this.skillRunner = skillRunner;
    }

    void cast(Player player, Map<String, Double> values, RpgItemDefinition weapon, boolean upgraded) {
        int chargeTicks = (int) Math.round(values.getOrDefault("charge-ticks", 16.0));
        int count = Math.max(1, (int) Math.round(upgraded ? values.getOrDefault("upgrade-count", 12.0) : values.getOrDefault("count", 6.0)));
        // the meteors share a fixed firing time (more meteors = a shorter gap between them), so the skill lasts as long as before
        int fireTicks = Math.max(1, (int) Math.round(upgraded ? values.getOrDefault("upgrade-fire-ticks", 30.0) : values.getOrDefault("fire-ticks", 15.0)));
        double interval = (double) fireTicks / count;
        int beamCharge = (int) Math.round(values.getOrDefault("beam-charge-ticks", 30.0));
        int total = chargeTicks + fireTicks;

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.7f);
        new SafeTask(plugin) {
            private int age;
            private int launched;
            private boolean beamFired;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                if (age < total) {
                    double grow = Math.min(1.0, (age + 1.0) / Math.max(1, chargeTicks));
                    drawCircle(player, age, grow);
                    if (age % 4 == 0) {
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, (float) (0.8 + 0.9 * grow));
                    }
                    if (upgraded) drawCharge(player, age, 0.45);
                    // meteor k leaves k * interval ticks after the charge (a gap of 2 or 3 ticks, not always a whole number)
                    while (launched < count && age >= chargeTicks + (int) Math.round(launched * interval)) {
                        launch(player, values, weapon, launched, count);
                        launched++;
                    }
                } else if (upgraded && age < total + beamCharge) {
                    // the light gathers faster and faster toward the beam
                    double ramp = 1.0 + 2.0 * (age - total) / Math.max(1, beamCharge);
                    drawCharge(player, age, ramp);
                    if ((age - total) % 3 == 0) {
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, (float) (0.7 + 1.1 * (age - total) / Math.max(1, beamCharge)));
                    }
                } else {
                    if (upgraded && !beamFired) {
                        beamFired = true;
                        fireBeam(player, values, weapon);
                    }
                    cancel();
                    return;
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- the magic circle and the charge ----------------------------------------------------------------------------

    private Location circleCenter(Player player) {
        Location eye = player.getEyeLocation();
        return eye.clone().add(eye.getDirection().normalize().multiply(1.9)).add(0.0, 0.2, 0.0);
    }

    private void drawCircle(Player player, int age, double grow) {
        World world = player.getWorld();
        Location center = circleCenter(player);
        Vector forward = player.getEyeLocation().getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1.0, 0.0, 0.0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        double radius = 0.5 + 1.1 * grow;
        int points = 28;
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points + age * 0.12;
            Location point = center.clone().add(right.clone().multiply(Math.cos(angle) * radius)).add(up.clone().multiply(Math.sin(angle) * radius));
            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, CIRCLE);
            if (index % 7 == 0) world.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // an inner star turning the other way
        for (int ray = 0; ray < 5; ray++) {
            double angle = Math.PI * 2.0 * ray / 5.0 - age * 0.09;
            for (int step = 1; step <= 3; step++) {
                double r = radius * step / 3.5;
                Location point = center.clone().add(right.clone().multiply(Math.cos(angle) * r)).add(up.clone().multiply(Math.sin(angle) * r));
                world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, TRAIL_LIGHT);
            }
        }
    }

    /** Light drawn in toward the mage while they charge (the upgrade's beam). */
    private void drawCharge(Player player, int age, double strength) {
        World world = player.getWorld();
        Location chest = player.getLocation().add(0.0, 1.1, 0.0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int motes = (int) Math.max(1, Math.round(4 * strength));
        for (int index = 0; index < motes; index++) {
            Vector out = new Vector(random.nextDouble(-1, 1), random.nextDouble(-0.6, 0.8), random.nextDouble(-1, 1));
            if (out.lengthSquared() < 1.0E-6) continue;
            out.normalize().multiply(random.nextDouble(1.2, 2.4));
            Location from = chest.clone().add(out);
            Vector in = out.clone().multiply(-0.18);
            world.spawnParticle(Particle.END_ROD, from, 0, in.getX(), in.getY(), in.getZ(), 1.0);
        }
    }

    // ---- meteors ----------------------------------------------------------------------------------------------------

    private void launch(Player player, Map<String, Double> values, RpgItemDefinition weapon, int number, int count) {
        World world = player.getWorld();
        Location center = circleCenter(player);
        Vector forward = player.getEyeLocation().getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1.0, 0.0, 0.0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();

        // meteors leave the circle on its upper half only (a meteor sent downward would hit the ground at once), in a fan
        // that is filled from the middle, alternately to the right and the left; each first swings outward and up, then
        // curves in an arc toward the front (away from the center line, so none of them starts into the ground)
        double angle = fanAngle(number, count) + ThreadLocalRandom.current().nextDouble(-0.1, 0.1);
        Vector outward = right.clone().multiply(Math.cos(angle)).add(up.clone().multiply(Math.sin(angle)));
        Location start = center.clone().add(outward.clone().multiply(1.1));
        for (int lift = 0; lift < 4 && !(start.getBlock().isPassable()); lift++) start.add(0.0, 0.5, 0.0);
        double perTick = values.getOrDefault("speed", 22.0) / 20.0;
        Vector velocity = forward.clone().multiply(perTick * 0.4).add(outward.clone().multiply(perTick * 1.0));
        world.playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.4f);

        double rate = values.getOrDefault("damage-rate", 1.3);
        double homingRadius = values.getOrDefault("homing-radius", 8.0);
        int homingDelay = (int) Math.round(values.getOrDefault("homing-delay-ticks", 7.0));
        int maxTicks = (int) Math.round(values.getOrDefault("max-ticks", 70.0));
        double hitRadius = values.getOrDefault("hit-radius", 0.9);
        Vector aim = forward.clone();

        new SafeTask(plugin) {
            private Location position = start.clone();
            private Vector velocityNow = velocity.clone();
            private LivingEntity homing;
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || age++ > maxTicks) {
                    cancel();
                    return;
                }
                Vector desired = aim.clone().multiply(perTick);
                double turn = Math.min(0.22, 0.07 + 0.02 * age); // a wide swing first, then the curve toward the front
                if (age >= homingDelay) {
                    if (homing == null || homing.isDead() || !homing.isValid() || age % 6 == 0) homing = nearestTarget(player, position, homingRadius);
                    if (homing != null) {
                        Vector toward = homing.getLocation().add(0.0, homing.getHeight() * 0.5, 0.0).toVector().subtract(position.toVector());
                        if (toward.lengthSquared() > 1.0E-6) {
                            desired = toward.normalize().multiply(perTick);
                            turn = 0.45;
                        }
                    }
                }
                velocityNow.add(desired.subtract(velocityNow).multiply(turn));
                double length = velocityNow.length();
                if (length > perTick * 1.2) velocityNow.multiply(perTick * 1.2 / length);
                if (length < 1.0E-6) return;
                Vector direction = velocityNow.clone().normalize();
                // blocks do not count during the first ticks, so a meteor never ends against the ground it just left
                RayTraceResult entityHit = world.rayTraceEntities(position, direction, length, hitRadius,
                        entity -> attackService.isAttackTarget(entity, player));
                RayTraceResult blockHit = age > 3 ? world.rayTraceBlocks(position, direction, length, FluidCollisionMode.NEVER, true) : null;
                RayTraceResult hit = entityHit;
                if (blockHit != null && blockHit.getHitPosition() != null
                        && (entityHit == null || entityHit.getHitPosition() == null
                        || blockHit.getHitPosition().distanceSquared(position.toVector()) < entityHit.getHitPosition().distanceSquared(position.toVector()))) {
                    hit = blockHit;
                }
                Location next = hit != null && hit.getHitPosition() != null
                        ? hit.getHitPosition().toLocation(world) : position.clone().add(velocityNow);
                drawMeteor(world, position, next);
                position = next;
                if (hit == null) return;
                cancel();
                world.spawnParticle(Particle.FLASH, next, 1);
                world.spawnParticle(Particle.END_ROD, next, 14, 0.4, 0.4, 0.4, 0.15);
                world.spawnParticle(Particle.REDSTONE, next, 16, 0.5, 0.5, 0.5, 0.0, TRAIL);
                world.playSound(next, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.7f);
                if (hit.getHitEntity() instanceof LivingEntity victim) hitVictim(player, weapon, victim, rate, next);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * The direction a meteor leaves the circle in, as an angle in the circle's plane (0 = right, 90 degrees = straight up):
     * only the upper half (20 to 160 degrees), starting in the middle and alternating to the right and the left.
     */
    private static double fanAngle(int number, int count) {
        int slot = number % 2 == 0 ? number / 2 : count - 1 - number / 2;
        double share = (slot + 0.5) / Math.max(1, count);
        return Math.toRadians(20.0 + 140.0 * share);
    }

    private void drawMeteor(World world, Location from, Location to) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 1.0E-6) return;
        Vector direction = path.normalize();
        int points = Math.max(1, (int) Math.ceil(length / 0.25));
        for (int index = 0; index <= points; index++) {
            Location point = from.clone().add(direction.clone().multiply(length * index / points));
            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, index % 2 == 0 ? TRAIL : TRAIL_LIGHT);
        }
        world.spawnParticle(Particle.END_ROD, to, 2, 0.04, 0.04, 0.04, 0.0);
    }

    private LivingEntity nearestTarget(Player player, Location from, double radius) {
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (Entity entity : from.getWorld().getNearbyEntities(from, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player) || living.isDead()) continue;
            if (!attackService.isAttackTarget(living, player)) continue;
            double distance = living.getLocation().add(0.0, living.getHeight() * 0.5, 0.0).distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    private void hitVictim(Player player, RpgItemDefinition weapon, LivingEntity victim, double rate, Location at) {
        AttackService.AttackDamage attack = attackService.calculateDamage(player, victim, weapon);
        double damage = Math.round(attack.result().damage() * rate * 10.0) / 10.0;
        boolean critical = attack.result().critical();
        if (!attackService.applyRangeDamage(player, victim, damage, weapon, critical)) return;
        attackService.sendDamageDebug(player, AttackType.RANGE, victim, damage, critical, false);
        attackService.applyAreaDamage(player, victim, damage, AttackType.RANGE, weapon);
    }

    // ---- the upgrade's beam -----------------------------------------------------------------------------------------

    private void fireBeam(Player player, Map<String, Double> values, RpgItemDefinition weapon) {
        if (skillRegistry == null || skillRunner == null) return;
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("radius", values.getOrDefault("beam-radius", 1.2));
        map.put("range", values.getOrDefault("beam-range", 26.0));
        map.put("windup-ticks", 4);
        map.put("damage-rate", values.getOrDefault("beam-damage-rate", 5.0));
        map.put("colors", List.of("#4a8cff", "#bfe0ff", "#ffffff"));
        map.put("particle", "END_ROD");
        map.put("size", 1.4);
        map.put("width", 1.8);
        map.put("sound", "magic");
        SkillSpec spec = skillRegistry.parse("class skill meteor_upgrade", map, SkillSpec.defaults(SkillTemplate.BEAM), "");
        if (spec == null) return;
        skillRunner.start(new PlayerSkillCaster(plugin, attackService, player, weapon, spec), spec,
                () -> PlayerSkillCaster.aimPoint(player, spec.range()), () -> {
                });
    }
}
