package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.skill.PlayerSkillCaster;
import net.tkgon.mc.iruuRPG.skill.SkillRegistry;
import net.tkgon.mc.iruuRPG.skill.SkillRunner;
import net.tkgon.mc.iruuRPG.skill.SkillSpec;
import net.tkgon.mc.iruuRPG.skill.SkillTemplate;
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

    /** A cast whose re-cast is still to come: when it was cast and whether the re-cast was made. */
    private static final class Recast {
        private final long castMillis;
        private final long untilMillis;
        private boolean used;

        private Recast(long castMillis, long untilMillis) {
            this.castMillis = castMillis;
            this.untilMillis = untilMillis;
        }
    }

    private final Map<java.util.UUID, Recast> recasts = new java.util.HashMap<>();

    /**
     * Pressing the key again after a cast (from {@code recast-delay-seconds} to {@code recast-window-seconds} after it, once): the finale, one great meteor that falls
     * on the aim (a ring marks the place, the star comes down in 10 ticks) for {@code recast-damage-rate} times the meteor's rate on all within {@code recast-radius};
     * after the upgrade four smaller ones fall round it. True when the press was handled here (also when it is too early).
     */
    boolean tryRecast(Player player, Map<String, Double> values, RpgItemDefinition weapon, boolean upgraded) {
        Recast recast = recasts.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (recast == null || recast.used) return false;
        if (now > recast.untilMillis) {
            recasts.remove(player.getUniqueId());
            return false;
        }
        double delay = values.getOrDefault("recast-delay-seconds", 1.0);
        if (now - recast.castMillis < delay * 1000.0) {
            player.sendMessage("[iruuRPG] 流星の再発動まで あと" + String.format(java.util.Locale.ROOT, "%.1f", delay - (now - recast.castMillis) / 1000.0) + "秒");
            return true;
        }
        recast.used = true;
        attackService.beginAttack(player);
        finale(player, values, weapon, upgraded);
        return true;
    }

    private void finale(Player player, Map<String, Double> values, RpgItemDefinition weapon, boolean upgraded) {
        World world = player.getWorld();
        Location target = PlayerSkillCaster.aimPoint(player, values.getOrDefault("recast-range", 28.0));
        RayTraceResult ray = world.rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), 40.0, FluidCollisionMode.NEVER, true);
        if (ray != null && ray.getHitPosition() != null) target = ray.getHitPosition().toLocation(world);
        Location spot = target.clone();
        double rate = values.getOrDefault("damage-rate", 1.3) * values.getOrDefault("recast-damage-rate", 4.0);
        double radius = values.getOrDefault("recast-radius", 4.5);
        world.playSound(spot, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.9f);
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.6f);
        for (int tick = 0; tick < 10; tick++) {
            double r = radius - (radius - 1.5) * tick / 9.0;
            int which = tick;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                SkillFx.groundRing(player, spot, r, SkillFx.METEOR, 0.5 + 0.05 * which, 1.2);
                if (which % 3 == 0) SkillFx.circle(player, spot.clone().add(0.0, 0.2, 0.0), new Vector(1, 0, 0), new Vector(0, 0, 1), r * 0.8, which * 4, SkillFx.METEOR);
            }, tick);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            fall(player, spot, 18.0, 6);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                ParticleFx.mark(player);
                impact(player, weapon, spot, radius, rate, 1.6);
                world.playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
                world.playSound(spot, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 0.6f);
            }, 6L);
        }, 10L);
        if (upgraded) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int index = 0; index < 4; index++) {
                double angle = Math.PI * 0.5 * index + random.nextDouble(-0.3, 0.3);
                Location small = spot.clone().add(Math.cos(angle) * (radius * 0.9), 0.0, Math.sin(angle) * (radius * 0.9));
                small.setY(world.getHighestBlockYAt(small) + 1.0);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    fall(player, small, 12.0, 4);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;
                        ParticleFx.mark(player);
                        impact(player, weapon, small, 2.2, values.getOrDefault("damage-rate", 1.3), 1.0);
                    }, 4L);
                }, 14L + index * 3L);
            }
        }
    }

    /** A streak of light falling to {@code spot} from {@code height} blocks above it in {@code ticks} ticks. */
    private void fall(Player player, Location spot, double height, int ticks) {
        Location top = spot.clone().add(1.5, height, 1.0);
        for (int tick = 0; tick < ticks; tick++) {
            Location a = top.clone().add(spot.clone().subtract(top).toVector().multiply(tick / (double) ticks));
            Location b = top.clone().add(spot.clone().subtract(top).toVector().multiply((tick + 1) / (double) ticks));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> SkillFx.trail(player, a, b, SkillFx.METEOR, 2.0), tick);
        }
    }

    /** The finale's blow: {@code rate} on all within {@code radius}, a flash, rings and a glow that stays for a moment (see {@link #afterglow}). */
    private void impact(Player player, RpgItemDefinition weapon, Location at, double radius, double rate, double size) {
        World world = at.getWorld();
        ParticleFx.to(player, world).spawnParticle(Particle.FLASH, at, 1);
        SkillFx.burst(player, at.clone().add(0.0, 0.4, 0.0), radius, SkillFx.METEOR);
        for (int ring = 1; ring <= 3; ring++) {
            double r = radius * ring / 3.0;
            int which = ring;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> SkillFx.groundRing(player, at, r, SkillFx.METEOR, 1.0 - 0.1 * which, 1.4 * size), ring - 1);
        }
        for (Entity entity : world.getNearbyEntities(at, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity near) || near.isDead() || !attackService.isAttackTarget(near, player)) continue;
            if (near.getLocation().distanceSquared(at) > radius * radius) continue;
            hitVictim(player, weapon, near, rate, at);
            Vector away = near.getLocation().toVector().subtract(at.toVector()).setY(0.0);
            if (away.lengthSquared() > 1.0E-6) near.setVelocity(near.getVelocity().add(away.normalize().multiply(0.5).setY(0.3)));
        }
        afterglow(player, at, radius);
    }

    /** A crater's glow where a star came down: for 1.2 s a ring of light draws in and sparks rise from the middle. */
    private void afterglow(Player player, Location at, double radius) {
        for (int tick = 1; tick <= 6; tick++) {
            double r = radius * (1.0 - tick / 7.0);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                SkillFx.groundRing(player, at, r, SkillFx.METEOR, 0.6, 0.9);
                ParticleFx.to(player, at.getWorld()).spawnParticle(Particle.END_ROD, at.clone().add(0.0, 0.3, 0.0), 3, radius * 0.2, 0.3, radius * 0.2, 0.03);
            }, tick * 4L);
        }
    }

    void cast(Player player, Map<String, Double> values, RpgItemDefinition weapon, boolean upgraded) {
        long castNow = System.currentTimeMillis();
        recasts.put(player.getUniqueId(), new Recast(castNow, castNow + (long) (values.getOrDefault("recast-window-seconds", 8.0) * 1000.0)));
        int chargeTicks = (int) Math.round(values.getOrDefault("charge-ticks", 16.0));
        int count = Math.max(1, (int) Math.round(upgraded ? values.getOrDefault("upgrade-count", 12.0) : values.getOrDefault("count", 6.0)));
        // the meteors share a fixed firing time (more meteors = a shorter gap between them), so the skill lasts as long as before
        int fireTicks = Math.max(1, (int) Math.round(upgraded ? values.getOrDefault("upgrade-fire-ticks", 30.0) : values.getOrDefault("fire-ticks", 15.0)));
        double interval = (double) fireTicks / count;
        int beamCharge = (int) Math.round(values.getOrDefault("beam-charge-ticks", 30.0));
        int total = chargeTicks + fireTicks;

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.7f);
        // the circle stays where it was drawn: it does not follow the mage or turn with their view
        Frame frame = frameOf(player);
        new SafeTask(plugin) {
            private int age;
            private int launched;
            private boolean beamFired;

            @Override
            protected void tick() {
                ParticleFx.mark(player);
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                if (age < total) {
                    double grow = Math.min(1.0, (age + 1.0) / Math.max(1, chargeTicks));
                    drawCircle(player, frame, age, grow);
                    if (age % 4 == 0) {
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, (float) (0.8 + 0.9 * grow));
                    }
                    if (upgraded) drawCharge(player, age, 0.45);
                    // meteor k leaves k * interval ticks after the charge (a gap of 2 or 3 ticks, not always a whole number)
                    while (launched < count && age >= chargeTicks + (int) Math.round(launched * interval)) {
                        launch(player, frame, values, weapon, launched, count);
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

    /** The circle's place and the way it faces, fixed when the skill is cast. */
    private record Frame(Location center, Vector forward, Vector right, Vector up) {
    }

    private Frame frameOf(Player player) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Location center = eye.clone().add(forward.clone().multiply(1.9)).add(0.0, 0.2, 0.0);
        Vector right = forward.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 1.0E-6) right = new Vector(1.0, 0.0, 0.0);
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        return new Frame(center, forward, right, up);
    }

    private void drawCircle(Player player, Frame frame, int age, double grow) {
        double radius = 0.5 + 1.1 * grow;
        SkillFx.circle(player, frame.center(), frame.right(), frame.up(), radius, age, SkillFx.METEOR);
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
            Location to = from.clone().add(out.clone().multiply(-0.45));
            SkillFx.trail(player, from, to, SkillFx.METEOR, 0.8);
        }
    }

    // ---- meteors ----------------------------------------------------------------------------------------------------

    private void launch(Player player, Frame frame, Map<String, Double> values, RpgItemDefinition weapon, int number, int count) {
        World world = player.getWorld();
        Location center = frame.center();
        Vector forward = frame.forward();
        Vector right = frame.right();
        Vector up = frame.up();

        // meteors leave the circle on its upper half only (a meteor sent downward would hit the ground at once), in a fan
        // that is filled from both ends inward, alternately the right end and the left end; each first swings outward and up, then
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
        double blastRadius = values.getOrDefault("blast-radius", 2.2);
        double blastShare = values.getOrDefault("blast-share", 0.35);
        Vector aim = forward.clone();

        new SafeTask(plugin) {
            private Location position = start.clone();
            private Vector velocityNow = velocity.clone();
            private LivingEntity homing;
            private int age;

            @Override
            protected void tick() {
                ParticleFx.mark(player);
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
                drawMeteor(player, world, position, next);
                position = next;
                if (hit == null) return;
                cancel();
                ParticleFx.to(player, world).spawnParticle(Particle.FLASH, next, 1);
                SkillFx.burst(player, next, blastRadius, SkillFx.METEOR);
                world.playSound(next, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.7f);
                afterglow(player, next, blastRadius);
                if (hit.getHitEntity() instanceof LivingEntity victim) hitVictim(player, weapon, victim, rate, next);
                blast(player, weapon, next, blastRadius, rate * blastShare, hit.getHitEntity());
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * The direction a meteor leaves the circle in, as an angle in the circle's plane (0 = right, 90 degrees = straight up):
     * only the upper half (20 to 160 degrees), filled from both ends inward: the 1st meteor takes the right end, the 2nd the left end, the 3rd the next one in from the right, and so on.
     */
    private static double fanAngle(int number, int count) {
        int slot = number % 2 == 0 ? number / 2 : count - 1 - number / 2;
        double share = (slot + 0.5) / Math.max(1, count);
        return Math.toRadians(20.0 + 140.0 * share);
    }

    private void drawMeteor(Player player, World world, Location from, Location to) {
        // a tapered streak: deep blue tail, white-hot head, with a thin glow round it
        SkillFx.trail(player, from, to, SkillFx.METEOR, 1.3);
    }

    /** The blast of a meteor: enemies round the impact point (but the one it hit) take a share of the damage. */
    private void blast(Player player, RpgItemDefinition weapon, Location at, double radius, double rate, Entity struck) {
        if (radius <= 0.0 || rate <= 0.0) return;
        for (Entity entity : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (entity.equals(struck) || !(entity instanceof LivingEntity near) || near.isDead()) continue;
            if (!attackService.isAttackTarget(near, player)) continue;
            AttackService.AttackDamage attack = attackService.calculateDamage(player, near, weapon);
            double damage = Math.round(attack.result().damage() * rate * 10.0) / 10.0;
            attackService.applyRangeDamage(player, near, damage, weapon, false);
        }
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
        map.put("particle", "DUST");
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
