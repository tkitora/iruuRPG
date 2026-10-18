package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.skill.PlayerSkillCaster;
import net.tkgon.mc.iruuRPG.skill.SkillRegistry;
import net.tkgon.mc.iruuRPG.skill.SkillRunner;
import net.tkgon.mc.iruuRPG.skill.SkillSpec;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The mage's combo (see {@link MeleeCombo}, plan {@code mage}) on the ranged weapon.
 *
 * <p>Normal attacks (right click): 1 and 2 a magic bolt from the upper left / upper right, 3 a fast scatter of bolts
 * (3 hits of 40%) and 4 a dense one (4 hits of 30%), both coming from the sides and diagonals so they cross, 5 a burst
 * of magic (3 hits of 60%) with two vortexes of light turning opposite ways. Runs of more than one effect are 2 ticks
 * apart and each a little turned from the one before.
 *
 * <p>Heavy attacks (left click, the number of normal attacks so far): 0 a short teleport along the view, 1 a ring of
 * flame all around, 2 a hop back with two ice waves forward, 3 lightning in front that pulls enemies in, 4 a tornado that
 * lifts them with wind blades. Heavy 0 within 2 seconds of heavy 1 - 4 is that element's own move (1-2 a flaming
 * dash, 2-2 ice bursts, 3-2 chain lightning, 4-2 a backstep that leaves a vacuum behind); hitting with it turns the
 * normal attacks into that element's look for 5 seconds.
 */
public final class MageMoves {

    private enum Style { MAGIC, FIRE, ICE, THUNDER, WIND }

    /** Heavy 0 this soon after heavy 1 - 4 is that heavy's own move. */
    private static final long CHAIN_MILLIS = 2000L;

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final AttackEffects effects;
    private final SkillRegistry skills;
    private final SkillRunner runner;
    private final Map<UUID, long[]> lastHeavy = new HashMap<>();
    private final Map<UUID, Style> modeStyle = new HashMap<>();
    private final Map<UUID, Long> modeUntil = new HashMap<>();

    public MageMoves(JavaPlugin plugin, AttackService attackService, AttackEffects effects, SkillRegistry skills, SkillRunner runner) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.effects = effects;
        this.skills = skills;
        this.runner = runner;
    }

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo.mage." + key, fallback);
    }

    /** True within 2 seconds of heavy 1 - 4: a heavy 0 then is that heavy's own move (no teleport cooldown). */
    public boolean chainActive(Player player) {
        long[] last = lastHeavy.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last[1] <= CHAIN_MILLIS;
    }

    // ---- styles ----------------------------------------------------------------------------------------------------------

    private Style style(Player player) {
        Long until = modeUntil.get(player.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return Style.MAGIC;
        return modeStyle.getOrDefault(player.getUniqueId(), Style.MAGIC);
    }

    /** The normal attacks look like {@code style} for the next 5 seconds. */
    private void startMode(Player player, Style style) {
        modeStyle.put(player.getUniqueId(), style);
        modeUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.round(number("element-mode-seconds", 5.0) * 1000.0));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.6f, 1.6f);
    }

    private Color dustColor(Style style) {
        return switch (style) {
            case FIRE -> Color.fromRGB(255, 120, 30);
            case ICE -> Color.fromRGB(150, 230, 255);
            case THUNDER -> Color.fromRGB(255, 240, 90);
            case WIND -> Color.fromRGB(190, 255, 215);
            default -> Color.fromRGB(190, 120, 255);
        };
    }

    private Particle sparkParticle(Style style) {
        return switch (style) {
            case FIRE -> Particle.FLAME;
            case ICE -> Particle.SNOWFLAKE;
            case THUNDER -> Particle.ELECTRIC_SPARK;
            case WIND -> Particle.CLOUD;
            default -> Particle.END_ROD;
        };
    }

    /** A line of light in {@code style}'s look. */
    private void line(Player player, World world, Location from, Location to, Style style, RpgItemDefinition weapon) {
        if (style == Style.MAGIC) {
            effects.playBoltSegment(from, to, weapon.element());
            return;
        }
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 1.0E-6) return;
        Vector direction = path.normalize();
        Particle.DustOptions dust = new Particle.DustOptions(dustColor(style), 1.1f);
        int points = Math.max(1, (int) Math.ceil(length / 0.3));
        for (int index = 0; index <= points; index++) {
            Location point = from.clone().add(direction.clone().multiply(length * index / points));
            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
            if (index % 3 == 0) ParticleFx.to(player, world).spawnParticle(sparkParticle(style), point, 1, 0.03, 0.03, 0.03, 0.0);
        }
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

    // ---- normal attacks ----------------------------------------------------------------------------------------------------

    /** The normal attack {@code step.stage()} (1 - 5) of the combo. */
    public void normal(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        switch (step.stage()) {
            case 1 -> bolt(player, weapon, -1.2, 0.8, step.damageMultiplier(), 1.0, 0.0);
            case 2 -> bolt(player, weapon, 1.2, 0.8, step.damageMultiplier(), 1.0, 0.0);
            case 3 -> scatter(player, weapon, step, false);
            case 4 -> scatter(player, weapon, step, true);
            default -> burstSeries(player, weapon, step);
        }
    }

    /** One bolt from {@code side} (right, negative = left) and {@code up} blocks off the eyes, flying to where the player aims. */
    private void bolt(Player player, RpgItemDefinition weapon, double side, double up, double rate, double speedScale, double spread) {
        Vector[] axes = basis(player);
        Location start = player.getEyeLocation().add(axes[0].clone().multiply(0.6)).add(axes[1].clone().multiply(side)).add(axes[2].clone().multiply(up));
        Vector direction = aimPoint(player).toVector().subtract(start.toVector());
        if (direction.lengthSquared() < 1.0E-6) direction = axes[0].clone();
        direction.normalize();
        if (spread > 0.0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            direction.add(axes[1].clone().multiply(random.nextDouble(-spread, spread))).add(axes[2].clone().multiply(random.nextDouble(-spread, spread))).normalize();
        }
        Style style = style(player);
        World world = player.getWorld();
        attackService.fireRangedProjectile(player, weapon, start, direction, rate, speedScale,
                (from, to) -> line(player, world, from, to, style, weapon));
    }

    /**
     * Fast bolts from the sides and the diagonals, all aimed at the same point so their paths cross (3 hits of 40%,
     * or 4 hits of 30% with extra bolts around each for the dense one).
     */
    private void scatter(Player player, RpgItemDefinition weapon, MeleeCombo.Step step, boolean dense) {
        int hits = step.extraHits() + 1;
        long gap = (long) number("multi-hit-gap-ticks", 4.0);
        double[][] places = {{-1.4, 0.0}, {1.4, 0.0}, {-1.1, 1.0}, {1.1, 1.0}};
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < hits; index++) {
            double[] place = places[random.nextInt(places.length)];
            double side = place[0] + random.nextDouble(-0.2, 0.2);
            double up = place[1] + random.nextDouble(-0.15, 0.15);
            later(gap * index, () -> {
                if (!player.isOnline() || player.isDead()) return;
                bolt(player, weapon, side, up, step.damageMultiplier(), dense ? 1.5 : 1.8, 0.0);
                if (dense) {
                    // the dense scatter: a few more bolts around, for the look only
                    Vector[] axes = basis(player);
                    Location aim = aimPoint(player);
                    Style style = style(player);
                    for (int extra = 0; extra < 3; extra++) {
                        Location start = player.getEyeLocation().add(axes[0].clone().multiply(0.6))
                                .add(axes[1].clone().multiply(side + random.nextDouble(-0.5, 0.5))).add(axes[2].clone().multiply(up + random.nextDouble(-0.4, 0.4)));
                        Location end = aim.clone().add(random.nextDouble(-0.5, 0.5), random.nextDouble(-0.4, 0.4), random.nextDouble(-0.5, 0.5));
                        line(player, player.getWorld(), start, end, style, weapon);
                    }
                }
            });
        }
    }

    /** The finisher: a burst of magic where the player aims, 3 hits of 60% with two vortexes of light. */
    private void burstSeries(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        int hits = step.extraHits() + 1;
        long gap = (long) number("multi-hit-gap-ticks", 4.0);
        Location center = aimPoint(player);
        double radius = number("burst-radius", 3.5);
        Style style = style(player);
        // the flourish of the finisher: a loud start
        World world = player.getWorld();
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0f, 1.4f);
        vortexes(player, center, style, weapon);
        for (int index = 0; index < hits; index++) {
            int repeat = index;
            later(gap * index, () -> {
                if (!player.isOnline() || player.isDead()) return;
                burstAt(player, weapon, center, radius, step.damageMultiplier(), style, repeat);
            });
        }
    }

    /** One burst of magic: damages every enemy within {@code radius} of {@code center} (the finisher, and heavy 2-2's). */
    private void burstAt(Player player, RpgItemDefinition weapon, Location center, double radius, double rate, Style style, int repeat) {
        World world = center.getWorld();
        if (world == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Particle.DustOptions dust = new Particle.DustOptions(dustColor(style), 1.3f);
        // a ring that opens and sparks thrown out, a little turned each time
        for (int index = 0; index < 24; index++) {
            double angle = Math.PI * 2.0 * index / 24.0 + repeat * 0.4;
            Location point = center.clone().add(Math.cos(angle) * radius * 0.7, 0.2, Math.sin(angle) * radius * 0.7);
            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
        ParticleFx.to(player, world).spawnParticle(sparkParticle(style), center, 18, radius * 0.3, 0.5, radius * 0.3, 0.12);
        ParticleFx.to(player, world).spawnParticle(Particle.FLASH, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.6f + random.nextFloat() * 0.3f);
        effects.playBoltImpact(center, weapon.element());
        for (LivingEntity target : around(player, center, radius)) hit(player, weapon, target, rate);
    }

    /** Two vortexes of light around {@code center}, one turning left and one right, rising for a moment. */
    private void vortexes(Player player, Location center, Style style, RpgItemDefinition weapon) {
        World world = center.getWorld();
        if (world == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(dustColor(style), 1.2f);
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
                        ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
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
        long now = System.currentTimeMillis();
        if (index == 0) {
            long[] last = lastHeavy.remove(player.getUniqueId());
            if (last != null && now - last[1] <= CHAIN_MILLIS) {
                switch ((int) last[0]) {
                    case 1 -> flameDash(player, weapon);
                    case 2 -> iceBursts(player, weapon);
                    case 3 -> chainLightning(player, weapon);
                    default -> vacuumBackstep(player, weapon);
                }
            } else {
                teleport(player, weapon);
            }
            return;
        }
        lastHeavy.put(player.getUniqueId(), new long[]{index, now});
        switch (index) {
            case 1 -> flameWave(player, weapon, step.damageMultiplier());
            case 2 -> iceWaves(player, weapon, step.damageMultiplier());
            case 3 -> thunder(player, weapon, step.damageMultiplier());
            default -> tornado(player, weapon, step.damageMultiplier());
        }
    }

    // 0: the teleport
    private void teleport(Player player, RpgItemDefinition weapon) {
        double distance = number("teleport-distance", 6.0);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        World world = player.getWorld();
        RayTraceResult wall = world.rayTraceBlocks(eye, direction, distance, FluidCollisionMode.NEVER, true);
        double reach = wall == null ? distance : Math.max(0.0, wall.getHitPosition().distance(eye.toVector()) - 0.7);
        Location destination = null;
        for (double d = reach; d >= 1.0; d -= 0.5) {
            Location feet = eye.clone().add(direction.clone().multiply(d)).subtract(0.0, 1.62, 0.0);
            if (feet.getBlock().isPassable() && feet.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
                destination = feet;
                break;
            }
        }
        if (destination == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.4f);
            return;
        }
        Location from = player.getLocation().clone();
        destination.setYaw(from.getYaw());
        destination.setPitch(from.getPitch());
        player.teleport(destination);
        player.setFallDistance(0.0f);
        Particle.DustOptions dust = new Particle.DustOptions(dustColor(Style.MAGIC), 1.2f);
        for (Location at : new Location[]{from, destination}) {
            for (int index = 0; index < 14; index++) {
                ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, at.clone().add(0.0, index * 0.15, 0.0), 2, 0.3, 0.05, 0.3, 0.0, dust);
            }
            ParticleFx.to(player, world).spawnParticle(Particle.PORTAL, at.clone().add(0.0, 1.0, 0.0), 24, 0.4, 0.7, 0.4, 0.4);
        }
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.5f);
        world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.3f);
    }

    // 1: the ring of flame
    private void flameWave(Player player, RpgItemDefinition weapon, double rate) {
        double radius = number("flame-radius", 4.0);
        World world = player.getWorld();
        Location center = player.getLocation().add(0.0, 0.6, 0.0);
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 0.7f);
        Particle.DustOptions dust = new Particle.DustOptions(dustColor(Style.FIRE), 1.4f);
        for (int ring = 1; ring <= 6; ring++) {
            double r = radius * ring / 6.0;
            later(ring - 1, () -> {
                int points = (int) Math.max(16, r * 10);
                for (int index = 0; index < points; index++) {
                    double angle = Math.PI * 2.0 * index / points;
                    Location point = center.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r);
                    ParticleFx.to(player, world).spawnParticle(Particle.FLAME, point, 1, 0.05, 0.1, 0.05, 0.02);
                    if (index % 2 == 0) ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point.clone().add(0.0, 0.3, 0.0), 1, 0.0, 0.0, 0.0, 0.0, dust);
                }
            });
        }
        ParticleFx.to(player, world).spawnParticle(Particle.EXPLOSION_LARGE, center, 1);
        for (LivingEntity target : around(player, player.getLocation(), radius)) {
            if (hit(player, weapon, target, rate)) {
                Vector away = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
                if (away.lengthSquared() > 1.0E-6) target.setVelocity(away.normalize().multiply(0.6).setY(0.3));
            }
        }
    }

    // 2: the hop back and two ice waves
    private void iceWaves(Player player, RpgItemDefinition weapon, double rate) {
        Vector facing = player.getLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
        facing.normalize();
        // the hop and the first wave start together
        player.setFallDistance(0.0f);
        player.setVelocity(facing.clone().multiply(-number("hop-back-power", 0.8)).setY(number("hop-up-power", 0.75)));
        SkillSpec base = skills.all().get(plugin.getConfig().getString("combat.combo.mage.ice-wave-skill", "mage_ice_wave"));
        if (base == null) {
            player.sendMessage("[iruuRPG] 氷の波動のスキルが見つかりません。");
            return;
        }
        SkillSpec spec = rate > 0.0 ? base.withDamageRate(rate) : base;
        long gap = (long) number("wave-gap-ticks", 5.0);
        for (int index = 0; index < 2; index++) {
            later(gap * index, () -> {
                if (!player.isOnline() || player.isDead()) return;
                runner.start(new PlayerSkillCaster(plugin, attackService, player, weapon, spec), spec,
                        () -> PlayerSkillCaster.aimPoint(player, spec.range()), () -> {
                        });
            });
        }
    }

    // 3: lightning in front that pulls enemies in
    private void thunder(Player player, RpgItemDefinition weapon, double rate) {
        Vector[] axes = basis(player);
        Vector facing = axes[0].clone().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
        facing.normalize();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double range = number("thunder-radius", 4.0);
        Location center = player.getLocation().add(facing.clone().multiply(number("thunder-forward", 4.0)))
                .add(random.nextDouble(-1.5, 1.5), 0.0, random.nextDouble(-1.5, 1.5));
        World world = player.getWorld();
        world.strikeLightningEffect(center);
        Particle.DustOptions dust = new Particle.DustOptions(dustColor(Style.THUNDER), 1.3f);
        for (int index = 0; index < 20; index++) {
            Location point = center.clone().add(random.nextDouble(-0.4, 0.4), index * 0.35, random.nextDouble(-0.4, 0.4));
            ParticleFx.to(player, world).spawnParticle(Particle.ELECTRIC_SPARK, point, 2, 0.1, 0.1, 0.1, 0.1);
            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
        for (LivingEntity target : around(player, center, range)) {
            if (!hit(player, weapon, target, rate)) continue;
            Vector toward = center.toVector().subtract(target.getLocation().toVector()).setY(0.0);
            double distance = toward.length();
            if (distance > 0.6) target.setVelocity(toward.normalize().multiply(Math.min(0.7, distance * 0.35)).setY(0.15));
        }
    }

    // 4: the tornado
    private void tornado(Player player, RpgItemDefinition weapon, double rate) {
        Vector facing = player.getLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
        facing.normalize();
        int hits = (int) Math.max(1, Math.round(number("tornado-hits", 20.0)));
        int perTick = (int) Math.max(1, Math.round(number("tornado-hits-per-tick", 2.0)));
        double range = number("tornado-radius", 4.0);
        Vector ahead = facing.clone().multiply(number("tornado-forward", 2.0));
        int ticks = (hits + perTick - 1) / perTick;
        for (int tick = 0; tick < ticks; tick++) {
            int count = Math.min(perTick, hits - tick * perTick);
            later(tick, () -> {
                if (!player.isOnline() || player.isDead()) return;
                Location center = player.getLocation().add(ahead).add(0.0, 1.0, 0.0);
                effects.playWindBlades(player, center, range, count);
                for (LivingEntity target : around(player, center, range)) {
                    for (int hit = 0; hit < count; hit++) hit(player, weapon, target, rate);
                    // lifted into the air
                    target.setVelocity(target.getVelocity().setY(Math.max(target.getVelocity().getY(), 0.42)));
                }
            });
        }
    }

    // ---- 1-4 (heavy 0 within 2 seconds of heavy 1-4) -----------------------------------------------------------------------

    // 1-2: a dash wrapped in flame, 5% every 2 ticks to what is on the way
    private void flameDash(Player player, RpgItemDefinition weapon) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        double rate = number("flame-dash-rate", 0.05);
        int ticks = (int) Math.max(4, Math.round(number("flame-dash-ticks", 8.0)));
        double power = number("flame-dash-power", 1.0);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        new SafeTask(plugin) {
            private int age;
            private boolean landedHit;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead() || age >= ticks) {
                    cancel();
                    return;
                }
                player.setVelocity(direction.clone().multiply(power));
                player.setFallDistance(0.0f);
                Location body = player.getLocation().add(0.0, 1.0, 0.0);
                ParticleFx.to(player, world).spawnParticle(Particle.FLAME, body, 14, 0.5, 0.7, 0.5, 0.05);
                ParticleFx.to(player, world).spawnParticle(Particle.LAVA, body, 2, 0.4, 0.6, 0.4, 0.0);
                if (age % 2 == 0) {
                    for (LivingEntity target : around(player, body, 2.2)) {
                        if (hit(player, weapon, target, rate)) landedHit = true;
                    }
                    if (landedHit) {
                        landedHit = false;
                        if (!modeActive(player, Style.FIRE)) startMode(player, Style.FIRE);
                    }
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean modeActive(Player player, Style style) {
        return style(player) == style;
    }

    // 2-2: bursts of ice, at random places in front
    private void iceBursts(Player player, RpgItemDefinition weapon) {
        Vector[] axes = basis(player);
        Location middle = player.getEyeLocation().add(axes[0].clone().multiply(number("ice-burst-forward", 4.0)));
        double radius = number("ice-burst-radius", 3.0);
        double rate = number("ice-burst-rate", 0.6);
        int count = (int) Math.max(1, Math.round(number("ice-burst-count", 3.0)));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long gap = (long) number("multi-hit-gap-ticks", 4.0);
        for (int index = 0; index < count; index++) {
            int repeat = index;
            Location at = middle.clone().add(random.nextDouble(-radius, radius), random.nextDouble(-0.5, 1.0), random.nextDouble(-radius, radius));
            later(gap * index, () -> {
                if (!player.isOnline() || player.isDead()) return;
                World world = at.getWorld();
                int before = around(player, at, 2.2).size();
                burstAt(player, weapon, at, 2.2, rate, Style.ICE, repeat);
                if (before > 0 && !modeActive(player, Style.ICE)) startMode(player, Style.ICE);
            });
        }
    }

    // 3-2: fast chain lightning, 50% twice on each of up to three targets
    private void chainLightning(Player player, RpgItemDefinition weapon) {
        Location eye = player.getEyeLocation();
        LivingEntity first = attackService.traceRange(player).victim();
        if (first == null) {
            double best = Double.MAX_VALUE;
            for (LivingEntity target : around(player, eye.clone().add(eye.getDirection().multiply(8.0)), 10.0)) {
                double distance = target.getLocation().distanceSquared(eye);
                if (distance < best) {
                    best = distance;
                    first = target;
                }
            }
        }
        if (first == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.4f);
            return;
        }
        double rate = number("chain-rate", 0.5);
        int jumps = (int) Math.max(1, Math.round(number("chain-targets", 3.0)));
        List<LivingEntity> chain = new ArrayList<>();
        chain.add(first);
        while (chain.size() < jumps) {
            LivingEntity last = chain.get(chain.size() - 1);
            LivingEntity next = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity candidate : around(player, last.getLocation().add(0.0, 1.0, 0.0), number("chain-radius", 6.0))) {
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
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 0.8f, 1.6f);
        Location from = eye.clone().add(eye.getDirection().multiply(0.6)).subtract(0.0, 0.3, 0.0);
        boolean landed = false;
        for (int index = 0; index < chain.size(); index++) {
            LivingEntity target = chain.get(index);
            Location to = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
            Location start = from.clone();
            later(index, () -> lightning(player, world, start, to));
            later(index, () -> {
                hit(player, weapon, target, rate);
                hit(player, weapon, target, rate);
            });
            from = to;
            landed = true;
        }
        if (landed) later(1L, () -> {
            if (!modeActive(player, Style.THUNDER)) startMode(player, Style.THUNDER);
        });
    }

    /** A jagged bolt of lightning between two points. */
    private void lightning(Player player, World world, Location from, Location to) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 0.1) return;
        Vector direction = path.clone().normalize();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Particle.DustOptions dust = new Particle.DustOptions(dustColor(Style.THUNDER), 1.2f);
        int points = Math.max(3, (int) (length / 0.35));
        for (int index = 0; index <= points; index++) {
            Location point = from.clone().add(direction.clone().multiply(length * index / points))
                    .add(random.nextDouble(-0.25, 0.25), random.nextDouble(-0.25, 0.25), random.nextDouble(-0.25, 0.25));
            ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
            ParticleFx.to(player, world).spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // 4-2: a backstep that leaves a vacuum behind, 15% six times
    private void vacuumBackstep(Player player, RpgItemDefinition weapon) {
        Vector back = player.getEyeLocation().getDirection().setY(0.0);
        if (back.lengthSquared() < 1.0E-6) back = new Vector(0.0, 0.0, 1.0);
        back.normalize().multiply(-1.0);
        Location spot = player.getLocation().add(0.0, 1.0, 0.0);
        double radius = number("vacuum-radius", 2.8);
        double rate = number("vacuum-rate", 0.15);
        int hits = (int) Math.max(1, Math.round(number("vacuum-hits", 6.0)));
        player.setFallDistance(0.0f);
        player.setVelocity(back.multiply(number("backstep-power", 1.1)).setY(0.3));
        World world = player.getWorld();
        world.playSound(spot, Sound.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.0f, 0.7f);
        Particle.DustOptions dust = new Particle.DustOptions(dustColor(Style.WIND), 1.2f);
        for (int tick = 0; tick < hits; tick++) {
            int age = tick;
            later(tick * 2L, () -> {
                if (!player.isOnline() || player.isDead()) return;
                // a ball of wind turning in the air
                for (int index = 0; index < 22; index++) {
                    double angle = Math.PI * 2.0 * index / 22.0 + age * 0.9;
                    double height = Math.sin(index * 0.9 + age) * radius * 0.4;
                    Location point = spot.clone().add(Math.cos(angle) * radius * 0.8, height, Math.sin(angle) * radius * 0.8);
                    ParticleFx.to(player, world).spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, dust);
                    if (index % 4 == 0) ParticleFx.to(player, world).spawnParticle(Particle.CLOUD, point, 1, 0.05, 0.05, 0.05, 0.0);
                }
                boolean landed = false;
                for (LivingEntity target : around(player, spot, radius)) {
                    if (hit(player, weapon, target, rate)) landed = true;
                    Vector pull = spot.toVector().subtract(target.getLocation().toVector()).multiply(0.15);
                    target.setVelocity(pull.setY(0.1));
                }
                if (landed && !modeActive(player, Style.WIND)) startMode(player, Style.WIND);
            });
        }
    }
}
