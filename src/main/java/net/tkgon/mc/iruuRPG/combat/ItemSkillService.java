package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.ItemSkillType;
import net.tkgon.mc.iruuRPG.item.ItemSkillRegistry;
import net.tkgon.mc.iruuRPG.item.ItemSkillVariant;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ItemSkillService {

    private static final double SWEEP_MELEE_COST = 10.0;
    private static final int SWEEP_MELEE_COOLDOWN_TICKS = 140;
    private static final double SWEEP_MELEE_RADIUS = 4.0;
    private static final double SWEEP_MELEE_DAMAGE_RATE = 1.2;

    private static final double SWEEP_RANGE_COST = 20.0;
    private static final int SWEEP_RANGE_COOLDOWN_TICKS = 300;
    private static final double SWEEP_RANGE_RADIUS = 5.0;
    private static final double SWEEP_RANGE_KNOCKBACK = 0.9;

    private static final double HEAL_MELEE_COST = 15.0;
    private static final int HEAL_MELEE_COOLDOWN_TICKS = 200;
    private static final double HEAL_MELEE_RATE = 0.10;

    private static final double HEAL_RANGE_COST = 20.0;
    private static final int HEAL_RANGE_COOLDOWN_TICKS = 240;
    private static final double HEAL_RANGE_RADIUS = 5.0;
    private static final double HEAL_RANGE_RATE = 0.07;

    private static final double DASH_MELEE_COST = 3.0;
    private static final int DASH_MELEE_COOLDOWN_TICKS = 100;
    private static final double DASH_MELEE_FORWARD_VELOCITY = 2.15;
    private static final double DASH_MELEE_UP_VELOCITY = 0.92;

    private static final double DASH_RANGE_COST = 5.0;
    private static final int DASH_RANGE_COOLDOWN_TICKS = 80;
    private static final double DASH_RANGE_DISTANCE = 15.0;

    private static final double STRIKE_MELEE_COST = 10.0;
    private static final int STRIKE_MELEE_COOLDOWN_TICKS = 100;
    private static final double STRIKE_MELEE_RADIUS = 2.6;
    private static final double STRIKE_MELEE_FORWARD_VELOCITY = 1.45;
    private static final long STRIKE_MELEE_ACTIVE_TICKS = 14L;

    private static final double STRIKE_RANGE_COST = 20.0;
    private static final int STRIKE_RANGE_COOLDOWN_TICKS = 400;
    private static final long STRIKE_RANGE_CHARGE_TICKS = 20L;
    private static final double STRIKE_RANGE_DISTANCE = 40.0;
    private static final double STRIKE_RANGE_STEP = 0.85;
    private static final double STRIKE_RANGE_HIT_RADIUS = 2.7;
    private static final double STRIKE_RANGE_DAMAGE_RATE = 4.0;

    private static final double SLASH_MELEE_COST = 15.0;
    private static final int SLASH_MELEE_COOLDOWN_TICKS = 600;
    private static final double SLASH_MELEE_RADIUS = 5.0;
    private static final int SLASH_MELEE_HITS = 6;
    private static final long SLASH_MELEE_INTERVAL_TICKS = 1L;

    private static final double SLASH_RANGE_COST = 20.0;
    private static final int SLASH_RANGE_COOLDOWN_TICKS = 600;
    private static final double SLASH_RANGE_DISTANCE = 10.0;
    private static final double SLASH_RANGE_STEP = 0.65;
    private static final double SLASH_RANGE_HIT_RADIUS = 1.25;

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final AttackEffects attackEffects;
    private final ItemSkillRegistry skillRegistry;
    private final Map<UUID, Map<String, Long>> cooldownUntilMillis = new HashMap<>();

    public ItemSkillService(JavaPlugin plugin, AttackService attackService, AttackEffects attackEffects, ItemSkillRegistry skillRegistry) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.attackEffects = attackEffects;
        this.skillRegistry = skillRegistry;
    }

    public boolean handleManualUse(Player player, RpgItemDefinition weapon) {
        if (weapon == null || !weapon.skill().enabled()) return false;
        if (!isManualSkillTrigger(player, weapon)) return false;
        if (!skill(weapon, weapon.attackType()).enabled()) return false;
        if (!attackService.canUseItem(player, weapon)) {
            attackService.sendLevelRequirement(player, weapon);
            return true;
        }

        switch (weapon.skill()) {
            case SWEEP -> useSweep(player, weapon);
            case HEAL -> useHeal(player, weapon);
            case DASH -> useDash(player, weapon);
            case STRIKE -> useStrike(player, weapon);
            case SLASH -> useSlash(player, weapon);
            case NONE -> {
            }
        }
        return true;
    }

    private boolean isManualSkillTrigger(Player player, RpgItemDefinition weapon) {
        return switch (weapon.attackType()) {
            case MELEE -> true;
            case RANGE -> player.isSneaking();
            case DEPLOY, SPECIAL -> false;
        };
    }

    private void useSweep(Player player, RpgItemDefinition weapon) {
        switch (weapon.attackType()) {
            case MELEE -> useSweepMelee(player, weapon);
            case RANGE -> useSweepRange(player, weapon);
            case DEPLOY, SPECIAL -> {
            }
        }
    }

    private void useSlash(Player player, RpgItemDefinition weapon) {
        switch (weapon.attackType()) {
            case MELEE -> useSlashMelee(player, weapon);
            case RANGE -> useSlashRange(player, weapon);
            case DEPLOY, SPECIAL -> {
            }
        }
    }

    private void useHeal(Player player, RpgItemDefinition weapon) {
        switch (weapon.attackType()) {
            case MELEE -> useHealMelee(player, weapon);
            case RANGE -> useHealRange(player, weapon);
            case DEPLOY, SPECIAL -> {
            }
        }
    }

    private void useDash(Player player, RpgItemDefinition weapon) {
        switch (weapon.attackType()) {
            case MELEE -> useDashMelee(player, weapon);
            case RANGE -> useDashRange(player, weapon);
            case DEPLOY, SPECIAL -> {
            }
        }
    }

    private void useStrike(Player player, RpgItemDefinition weapon) {
        switch (weapon.attackType()) {
            case MELEE -> useStrikeMelee(player, weapon);
            case RANGE -> useStrikeRange(player, weapon);
            case DEPLOY, SPECIAL -> {
            }
        }
    }

    private void useSweepMelee(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.MELEE);
        if (!canUse(player, weapon, AttackType.MELEE, skill.cost(SWEEP_MELEE_COST))) return;

        double radius = skill.value("radius", SWEEP_MELEE_RADIUS);
        double damageRate = skill.value("damage-rate", SWEEP_MELEE_DAMAGE_RATE);
        attackEffects.playSweepMelee(player, weapon);
        for (LivingEntity target : targetsInFront(player, radius)) {
            AttackService.AttackDamage attack = attackService.calculateDamage(player, target, weapon);
            double damage = round(attack.result().damage() * damageRate);
            if (attackService.applyDirectDamage(player, target, damage, weapon, attack.result().critical())) {
                attackService.sendDamageDebug(player, AttackType.MELEE, target, damage, attack.result().critical(), false);
            }
        }

        startCooldown(player, weapon, AttackType.MELEE, skill.cooldownTicks(SWEEP_MELEE_COOLDOWN_TICKS));
    }

    private void useSweepRange(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.RANGE);
        if (!canUse(player, weapon, AttackType.RANGE, skill.cost(SWEEP_RANGE_COST))) return;

        double radius = skill.value("radius", SWEEP_RANGE_RADIUS);
        double knockback = skill.value("knockback", SWEEP_RANGE_KNOCKBACK);
        attackEffects.playSweepRangeWave(player, weapon, radius);
        for (LivingEntity target : targetsAround(player.getLocation(), player, radius)) {
            AttackService.AttackDamage attack = attackService.calculateDamage(player, target, weapon);
            double damage = attack.result().damage();
            if (attackService.applyRangeDamage(player, target, damage, weapon, attack.result().critical())) {
                knockAway(player.getLocation(), target, knockback);
                attackService.sendDamageDebug(player, AttackType.RANGE, target, damage, attack.result().critical(), false);
            }
        }
        evadeBackward(player);

        startCooldown(player, weapon, AttackType.RANGE, skill.cooldownTicks(SWEEP_RANGE_COOLDOWN_TICKS));
    }

    private void useHealMelee(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.MELEE);
        if (!canUse(player, weapon, AttackType.MELEE, skill.cost(HEAL_MELEE_COST))) return;

        PlayerProfile snapshot = attackService.snapshotAttacker(player);
        double amount = round(snapshot.maxHp() * skill.value("heal-rate", HEAL_MELEE_RATE));
        if (attackService.healPlayer(player, amount)) {
            attackEffects.playHealBurst(player, weapon);
        }

        startCooldown(player, weapon, AttackType.MELEE, skill.cooldownTicks(HEAL_MELEE_COOLDOWN_TICKS));
    }

    private void useHealRange(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.RANGE);
        if (!canUse(player, weapon, AttackType.RANGE, skill.cost(HEAL_RANGE_COST))) return;

        PlayerProfile snapshot = attackService.snapshotAttacker(player);
        double radius = skill.value("radius", HEAL_RANGE_RADIUS);
        double amount = round(snapshot.maxHp() * skill.value("heal-rate", HEAL_RANGE_RATE));
        attackEffects.playHealRing(player.getLocation(), weapon, radius);
        for (Player target : playersAround(player.getLocation(), player, radius, false)) {
            if (attackService.healPlayer(target, amount)) {
                attackEffects.playHealBurst(target, weapon);
            }
        }

        startCooldown(player, weapon, AttackType.RANGE, skill.cooldownTicks(HEAL_RANGE_COOLDOWN_TICKS));
    }

    private void useDashMelee(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.MELEE);
        if (!canUse(player, weapon, AttackType.MELEE, skill.cost(DASH_MELEE_COST))) return;

        attackEffects.playDashBurst(player, weapon);
        Vector velocity = horizontalForward(player)
                .multiply(skill.value("forward-velocity", DASH_MELEE_FORWARD_VELOCITY))
                .setY(skill.value("up-velocity", DASH_MELEE_UP_VELOCITY));
        player.setFallDistance(0.0f);
        player.setVelocity(velocity);

        startCooldown(player, weapon, AttackType.MELEE, skill.cooldownTicks(DASH_MELEE_COOLDOWN_TICKS));
    }

    private void useDashRange(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.RANGE);
        if (!canUse(player, weapon, AttackType.RANGE, skill.cost(DASH_RANGE_COST))) return;

        attackEffects.playDashBurst(player, weapon);
        if (!teleportForward(player, skill.value("distance", DASH_RANGE_DISTANCE))) {
            player.sendMessage("[iruuRPG] テレポート先が見つかりません。");
        } else {
            attackEffects.playDashBurst(player, weapon);
        }

        startCooldown(player, weapon, AttackType.RANGE, skill.cooldownTicks(DASH_RANGE_COOLDOWN_TICKS));
    }

    private void useStrikeMelee(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.MELEE);
        if (!canUse(player, weapon, AttackType.MELEE, skill.cost(STRIKE_MELEE_COST))) return;

        double radius = skill.value("radius", STRIKE_MELEE_RADIUS);
        attackEffects.playStrikeMeleeBurst(player, weapon, radius);
        player.setVelocity(horizontalForward(player).multiply(skill.value("forward-velocity", STRIKE_MELEE_FORWARD_VELOCITY)).setY(skill.value("up-velocity", 0.28)));

        startStrikeMeleeDamageWindow(player, weapon, radius, skill.longValue("active-ticks", STRIKE_MELEE_ACTIVE_TICKS));

        startCooldown(player, weapon, AttackType.MELEE, skill.cooldownTicks(STRIKE_MELEE_COOLDOWN_TICKS));
    }

    private void startStrikeMeleeDamageWindow(Player player, RpgItemDefinition weapon, double radius, long activeTicks) {
        new BukkitRunnable() {
            private final Set<UUID> hit = new HashSet<>();
            private int ageTicks;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ageTicks >= activeTicks) {
                    cancel();
                    return;
                }

                if (ageTicks % 4 == 0) {
                    attackEffects.playStrikeMeleeBurst(player, weapon, radius);
                }

                for (LivingEntity target : targetsAround(player.getLocation(), player, radius)) {
                    if (!hit.add(target.getUniqueId())) continue;

                    AttackService.AttackDamage attack = attackService.calculateDamage(player, target, weapon);
                    double damage = attack.result().damage();
                    if (attackService.applyDirectDamage(player, target, damage, weapon, attack.result().critical())) {
                        knockAway(player.getLocation(), target, 1.05);
                        attackService.sendDamageDebug(player, AttackType.MELEE, target, damage, attack.result().critical(), false);
                    }
                }

                ageTicks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void useStrikeRange(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.RANGE);
        if (!canUse(player, weapon, AttackType.RANGE, skill.cost(STRIKE_RANGE_COST))) return;

        Location start = player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize().multiply(1.2));
        Vector direction = player.getEyeLocation().getDirection().normalize();
        double distance = skill.value("distance", STRIKE_RANGE_DISTANCE);
        double maxDistance = strikeRangeDistance(player.getWorld(), start, direction, distance);

        attackEffects.playStrikeRangeCharge(start, direction, weapon);
        launchStrikeRange(
                player,
                weapon,
                start,
                direction,
                maxDistance,
                skill.value("step", STRIKE_RANGE_STEP),
                skill.value("hit-radius", STRIKE_RANGE_HIT_RADIUS),
                skill.value("damage-rate", STRIKE_RANGE_DAMAGE_RATE),
                skill.longValue("charge-ticks", STRIKE_RANGE_CHARGE_TICKS)
        );
        startCooldown(player, weapon, AttackType.RANGE, skill.cooldownTicks(STRIKE_RANGE_COOLDOWN_TICKS));
    }

    private void launchStrikeRange(Player player, RpgItemDefinition weapon, Location start, Vector direction, double maxDistance, double step, double hitRadius, double damageRate, long chargeTicks) {
        new BukkitRunnable() {
            private final Set<UUID> hit = new HashSet<>();
            private double distance;

            @Override
            public void run() {
                if (!player.isOnline() || distance > maxDistance) {
                    cancel();
                    return;
                }

                Location point = start.clone().add(direction.clone().multiply(distance));
                attackEffects.playStrikeRangeBolt(point, direction, weapon);

                for (LivingEntity target : targetsAround(point, player, hitRadius)) {
                    if (!hit.add(target.getUniqueId())) continue;

                    AttackService.AttackDamage attack = attackService.calculateDamage(player, target, weapon);
                    double damage = round(attack.result().damage() * damageRate);
                    if (attackService.applyRangeDamage(player, target, damage, weapon, attack.result().critical())) {
                        attackService.sendDamageDebug(player, AttackType.RANGE, target, damage, attack.result().critical(), false);
                    }
                }

                distance += step;
            }
        }.runTaskTimer(plugin, chargeTicks, 1L);
    }

    private double strikeRangeDistance(World world, Location start, Vector direction, double distance) {
        RayTraceResult result = world.rayTraceBlocks(start, direction, distance, FluidCollisionMode.NEVER, true);
        if (result == null || result.getHitPosition() == null) {
            return distance;
        }

        return Math.max(0.0, result.getHitPosition().distance(start.toVector()) - 0.5);
    }

    private void useSlashMelee(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.MELEE);
        if (!canUse(player, weapon, AttackType.MELEE, skill.cost(SLASH_MELEE_COST))) return;

        LivingEntity target = nearestTargetInFront(player, skill.value("radius", SLASH_MELEE_RADIUS));
        if (target == null) {
            attackEffects.playSlashMelee(player, weapon, null, 0);
        } else {
            startSlashMeleeCombo(player, weapon, target, skill.intValue("hits", SLASH_MELEE_HITS), skill.longValue("interval-ticks", SLASH_MELEE_INTERVAL_TICKS));
        }

        startCooldown(player, weapon, AttackType.MELEE, skill.cooldownTicks(SLASH_MELEE_COOLDOWN_TICKS));
    }

    private void startSlashMeleeCombo(Player player, RpgItemDefinition weapon, LivingEntity target, int maxHits, long intervalTicks) {
        new BukkitRunnable() {
            private int hits;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || target.isDead() || !target.isValid() || hits >= maxHits) {
                    cancel();
                    return;
                }

                attackEffects.playSlashMelee(player, weapon, target, hits);
                AttackService.AttackDamage attack = attackService.calculateDamage(player, target, weapon);
                double damage = attack.result().damage();
                if (attackService.applyDirectDamage(player, target, damage, weapon, attack.result().critical())) {
                    attackService.sendDamageDebug(player, AttackType.MELEE, target, damage, attack.result().critical(), false);
                }

                hits++;
            }
        }.runTaskTimer(plugin, 0L, Math.max(1L, intervalTicks));
    }

    private void useSlashRange(Player player, RpgItemDefinition weapon) {
        ItemSkillVariant skill = skill(weapon, AttackType.RANGE);
        if (!canUse(player, weapon, AttackType.RANGE, skill.cost(SLASH_RANGE_COST))) return;

        Location start = player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize().multiply(1.1));
        Vector direction = player.getEyeLocation().getDirection().normalize();
        double maxDistance = slashRangeDistance(player.getWorld(), start, direction, skill.value("distance", SLASH_RANGE_DISTANCE));
        launchSlashRange(
                player,
                weapon,
                start,
                direction,
                maxDistance,
                skill.value("step", SLASH_RANGE_STEP),
                skill.value("hit-radius", SLASH_RANGE_HIT_RADIUS),
                skill.value("min-multiplier", 3.0),
                skill.value("max-multiplier", 4.0)
        );

        startCooldown(player, weapon, AttackType.RANGE, skill.cooldownTicks(SLASH_RANGE_COOLDOWN_TICKS));
    }

    private void launchSlashRange(Player player, RpgItemDefinition weapon, Location start, Vector direction, double maxDistance, double step, double hitRadius, double minMultiplier, double maxMultiplier) {
        new BukkitRunnable() {
            private double distance;

            @Override
            public void run() {
                if (!player.isOnline() || distance > maxDistance) {
                    cancel();
                    return;
                }

                Location point = start.clone().add(direction.clone().multiply(distance));
                attackEffects.playSlashProjectile(point, direction, weapon);
                LivingEntity target = nearestTargetAround(point, player, hitRadius);
                if (target != null) {
                    AttackService.AttackDamage attack = attackService.calculateDamage(player, target, weapon);
                    double lower = Math.min(minMultiplier, maxMultiplier);
                    double upper = Math.max(minMultiplier, maxMultiplier);
                    double multiplier = ThreadLocalRandom.current().nextDouble(lower, upper);
                    double damage = round(attack.result().damage() * multiplier);
                    if (attackService.applyRangeDamage(player, target, damage, weapon, attack.result().critical())) {
                        attackEffects.playSlashImpact(target, weapon);
                        attackService.sendDamageDebug(player, AttackType.RANGE, target, damage, attack.result().critical(), false);
                    }
                    cancel();
                    return;
                }

                distance += step;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private double slashRangeDistance(World world, Location start, Vector direction, double distance) {
        RayTraceResult result = world.rayTraceBlocks(start, direction, distance, FluidCollisionMode.NEVER, true);
        if (result == null || result.getHitPosition() == null) {
            return distance;
        }

        return Math.max(0.0, result.getHitPosition().distance(start.toVector()) - 0.5);
    }

    private ItemSkillVariant skill(RpgItemDefinition weapon, AttackType attackType) {
        return skillRegistry.variant(weapon.skill(), attackType);
    }

    private boolean canUse(Player player, RpgItemDefinition weapon, AttackType attackType, double cost) {
        long attackCooldown = attackService.remainingCooldownMillis(player, attackType);
        if (attackCooldown > 0L) {
            sendCooldown(player, "通常攻撃", attackCooldown);
            return false;
        }

        long skillCooldown = remainingSkillCooldownMillis(player, attackType);
        if (skillCooldown > 0L) {
            sendCooldown(player, attackType.displayName() + "スキル", skillCooldown);
            return false;
        }

        if (!attackService.consumeMp(player, cost)) {
            player.sendMessage("[iruuRPG] MPが足りません。");
            return false;
        }

        return true;
    }

    private void startCooldown(Player player, RpgItemDefinition weapon, AttackType attackType, int ticks) {
        cooldownUntilMillis
                .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(cooldownKey(attackType), System.currentTimeMillis() + ticks * 50L);
        attackService.showItemCooldown(player, attackType, ticks);
    }

    private long remainingSkillCooldownMillis(Player player, AttackType attackType) {
        Map<String, Long> playerCooldowns = cooldownUntilMillis.get(player.getUniqueId());
        if (playerCooldowns == null) return 0L;

        return Math.max(0L, playerCooldowns.getOrDefault(cooldownKey(attackType), 0L) - System.currentTimeMillis());
    }

    private String cooldownKey(AttackType attackType) {
        return attackType.key();
    }

    private void sendCooldown(Player player, String label, long remainingMillis) {
        player.sendMessage("[iruuRPG] " + label + "のクールダウン中: " + formatSeconds(remainingMillis) + "秒");
    }

    private Iterable<LivingEntity> targetsInFront(Player player, double radius) {
        Map<UUID, LivingEntity> targets = new HashMap<>();
        Location origin = player.getLocation();
        Vector forward = horizontalForward(player);
        double radiusSquared = radius * radius;

        for (LivingEntity target : targetsAround(origin, player, radius)) {
            Vector offset = target.getLocation().toVector().subtract(origin.toVector());
            offset.setY(0.0);
            if (offset.lengthSquared() > radiusSquared || offset.lengthSquared() <= 1.0E-9) continue;
            if (forward.dot(offset.normalize()) < 0.2) continue;

            targets.put(target.getUniqueId(), target);
        }
        return targets.values();
    }

    private LivingEntity nearestTargetInFront(Player player, double radius) {
        LivingEntity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        Location origin = player.getLocation();
        for (LivingEntity target : targetsInFront(player, radius)) {
            double distanceSquared = target.getLocation().distanceSquared(origin);
            if (distanceSquared >= nearestDistanceSquared) continue;

            nearest = target;
            nearestDistanceSquared = distanceSquared;
        }
        return nearest;
    }

    private LivingEntity nearestTargetAround(Location center, Player owner, double radius) {
        LivingEntity nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (LivingEntity target : targetsAround(center, owner, radius)) {
            double distanceSquared = target.getLocation().distanceSquared(center);
            if (distanceSquared >= nearestDistanceSquared) continue;

            nearest = target;
            nearestDistanceSquared = distanceSquared;
        }
        return nearest;
    }

    private Iterable<LivingEntity> targetsAround(Location center, Player owner, double radius) {
        Map<UUID, LivingEntity> targets = new HashMap<>();
        World world = center.getWorld();
        if (world == null) return targets.values();

        double radiusSquared = radius * radius;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (!isSkillTarget(owner, target)) continue;
            if (target.getLocation().distanceSquared(center) > radiusSquared) continue;

            targets.put(target.getUniqueId(), target);
        }
        return targets.values();
    }

    private Iterable<Player> playersAround(Location center, Player caster, double radius, boolean includeCaster) {
        Map<UUID, Player> players = new HashMap<>();
        World world = center.getWorld();
        if (world == null) return players.values();

        double radiusSquared = radius * radius;
        if (includeCaster && caster.getLocation().distanceSquared(center) <= radiusSquared) {
            players.put(caster.getUniqueId(), caster);
        }

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player player)) continue;
            if (!includeCaster && player.equals(caster)) continue;
            if (player.isDead() || !player.isValid()) continue;
            if (player.getLocation().distanceSquared(center) > radiusSquared) continue;

            players.put(player.getUniqueId(), player);
        }
        return players.values();
    }

    private boolean isSkillTarget(Player player, LivingEntity target) {
        return !target.equals(player)
                && !(target instanceof Player)
                && !(target instanceof ArmorStand)
                && !DeployService.isDeployEntity(target)
                && !target.isDead()
                && target.isValid();
    }

    private void knockAway(Location center, LivingEntity target, double strength) {
        Vector direction = target.getLocation().toVector().subtract(center.toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() <= 1.0E-9) {
            direction = new Vector(0.0, 0.0, 1.0);
        } else {
            direction.normalize();
        }

        target.setVelocity(direction.multiply(strength).setY(0.28));
    }

    private void evadeBackward(Player player) {
        Vector forward = horizontalForward(player);
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX()).normalize();
        Vector velocity = forward.multiply(-1.15).add(right.multiply(0.45)).setY(0.62);
        player.setVelocity(velocity);
    }

    private boolean teleportForward(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult result = player.getWorld().rayTraceBlocks(eye, direction, maxDistance, FluidCollisionMode.NEVER, true);
        double distance = result != null && result.getHitPosition() != null
                ? Math.max(0.0, result.getHitPosition().distance(eye.toVector()) - 0.8)
                : maxDistance;

        for (double current = distance; current >= 0.0; current -= 0.35) {
            Location feet = eye.clone()
                    .add(direction.clone().multiply(current))
                    .subtract(0.0, player.getEyeHeight(false), 0.0);
            feet.setYaw(player.getLocation().getYaw());
            feet.setPitch(player.getLocation().getPitch());
            if (!isSafeTeleportLocation(feet)) continue;

            player.setFallDistance(0.0f);
            return player.teleport(feet);
        }

        return false;
    }

    private boolean isSafeTeleportLocation(Location location) {
        World world = location.getWorld();
        if (world == null || !world.getWorldBorder().isInside(location)) return false;

        return location.getBlock().isPassable()
                && location.clone().add(0.0, 1.0, 0.0).getBlock().isPassable();
    }

    private Vector horizontalForward(Player player) {
        Vector forward = player.getLocation().getDirection();
        forward.setY(0.0);
        if (forward.lengthSquared() <= 1.0E-9) {
            return new Vector(0.0, 0.0, 1.0);
        }
        return forward.normalize();
    }

    private double round(double value) {
        return Math.floor(value * 10.0 + 0.5) / 10.0;
    }

    private String formatSeconds(long millis) {
        double seconds = millis / 1000.0;
        if (Math.abs(seconds - Math.rint(seconds)) < 1.0E-9) {
            return String.valueOf((long) Math.rint(seconds));
        }

        return String.format(Locale.ROOT, "%.1f", seconds)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }
}
