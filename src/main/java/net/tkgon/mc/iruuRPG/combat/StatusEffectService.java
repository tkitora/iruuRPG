package net.tkgon.mc.iruuRPG.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class StatusEffectService {

    private static final double EPSILON = 1.0E-9;
    private static final long SLOW_DURATION_MILLIS = 3000L;
    private static final long CORROSION_DURATION_MILLIS = 3000L;
    private static final long DECAY_DURATION_MILLIS = 4000L;
    private static final long EXPLOSION_DURATION_MILLIS = 30000L;
    private static final int EXPLOSION_TRIGGER_STACKS = 10;
    private static final double CONFUSION_MAX = 100.0;
    private static final UUID SLOW_MODIFIER_ID = UUID.fromString("5a15657a-2d7b-4d7a-9d5f-0f47b3d2456e");

    private final JavaPlugin plugin;
    private final EquipmentService equipmentService;
    private final PlayerBars playerBars;
    private final Map<UUID, EnumMap<StatusEffectType, ActiveEffect>> singleEffects = new HashMap<>();
    private final Map<UUID, BleedEffect> bleedEffects = new HashMap<>();
    private final Map<UUID, List<TimedStack>> corrosionEffects = new HashMap<>();
    private final Map<UUID, List<TimedStack>> decayEffects = new HashMap<>();
    private final Map<UUID, ExplosionEffect> explosionEffects = new HashMap<>();
    private final Map<UUID, Long> stunUntil = new HashMap<>();
    private final Set<UUID> awareDisabled = new HashSet<>();
    private RpgMobService mobService;
    private AttackEffects attackEffects;

    public StatusEffectService(JavaPlugin plugin, EquipmentService equipmentService, PlayerBars playerBars) {
        this.plugin = plugin;
        this.equipmentService = equipmentService;
        this.playerBars = playerBars;
    }

    public void setMobService(RpgMobService mobService) {
        this.mobService = mobService;
    }

    public void setAttackEffects(AttackEffects attackEffects) {
        this.attackEffects = attackEffects;
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void onDamageDealt(Player attacker, LivingEntity target, RpgItemDefinition weapon, double damage, boolean critical) {
        if (attacker == null || target == null || weapon == null || damage <= 0.0) return;
        if (target instanceof Player || DeployService.isDeployEntity(target) || target.isDead() || !target.isValid()) return;

        PlayerProfile attackerProfile = equipmentService.recalculate(attacker);
        StatSet stats = attackerProfile.finalStats();
        UUID sourceId = attacker.getUniqueId();

        applyOffensiveEffects(target, attackerProfile, stats, sourceId);
        if (!target.isValid() || target.isDead()) return;

        long selfDurationMillis = durationMillis(attackerProfile, "duration-ticks", 100);
        applySingle(attacker, StatusEffectType.ADRENALINE, stats.get(StatType.ADRENALINE), selfDurationMillis, sourceId, false);
        applySingle(attacker, StatusEffectType.PROTECTION, stats.get(StatType.PROTECTION), selfDurationMillis, sourceId, false);
        if (weapon.damageKind() == DamageKind.MAGIC || weapon.attackType() == AttackType.RANGE) {
            applySingle(attacker, StatusEffectType.MAGIC_AMPLIFY, stats.get(StatType.MAGIC_OVERLOAD), selfDurationMillis, sourceId, false);
        }

        applyAbsorb(attacker, damage, stats.get(StatType.ABSORB_PERCENT));
        refreshDisplay(target);
    }

    public void onMobDamageDealt(LivingEntity attacker, Player target, RpgItemDefinition weapon, PlayerProfile attackerProfile, double damage) {
        if (attacker == null || target == null || weapon == null || attackerProfile == null || damage <= 0.0) return;
        if (DeployService.isDeployEntity(target) || target.isDead() || !target.isValid()) return;

        applyOffensiveEffects(target, attackerProfile, attackerProfile.finalStats(), attacker.getUniqueId());
    }

    private void applyOffensiveEffects(LivingEntity target, PlayerProfile attackerProfile, StatSet stats, UUID sourceId) {
        triggerLaceration(target, stats.get(StatType.LACERATION), sourceId);
        if (!target.isValid() || target.isDead()) return;

        applyBleed(target, stats.get(StatType.BLEED), sourceId);
        applySlow(target, stats.get(StatType.SLOW_PERCENT), sourceId);
        applyTimedStack(corrosionEffects, target, StatusEffectType.CORROSION, stats.get(StatType.CORROSION), CORROSION_DURATION_MILLIS, sourceId);
        applyTimedStack(decayEffects, target, StatusEffectType.DECAY, stats.get(StatType.DECAY), DECAY_DURATION_MILLIS, sourceId);
        applyConfusionStack(target, stats.get(StatType.CONFUSION), durationMillis(attackerProfile, "duration-ticks", 100), sourceId);
        applyExplosion(target, stats.get(StatType.EXPLOSION), sourceId);
        refreshDisplay(target);
    }

    public double modifyOutgoingDamage(Player attacker, RpgItemDefinition weapon, double damage) {
        if (attacker == null || weapon == null || damage <= 0.0) return damage;

        double multiplier = 1.0;
        ActiveEffect adrenaline = active(attacker, StatusEffectType.ADRENALINE);
        if (adrenaline != null) {
            multiplier += Math.max(0.0, adrenaline.value()) / 200.0;
        }

        ActiveEffect magicAmplify = active(attacker, StatusEffectType.MAGIC_AMPLIFY);
        if (magicAmplify != null && (weapon.damageKind() == DamageKind.MAGIC || weapon.attackType() == AttackType.RANGE)) {
            multiplier += Math.max(0.0, magicAmplify.value()) / 100.0;
        }

        return damage * multiplier;
    }

    public double modifyIncomingDamage(LivingEntity victim, double damage) {
        if (victim == null || damage <= 0.0) return damage;

        long now = System.currentTimeMillis();
        pruneExpired(victim, now);

        // Decay: each time the victim takes damage, the decay value is added as special damage
        // (no ceiling). Corrosion is not applied here: it lowers defense inside the damage formula.
        double decay = timedTotal(decayEffects.get(victim.getUniqueId()), now);
        double total = damage + Math.max(0.0, decay);

        double multiplier = 1.0;
        ActiveEffect protection = active(victim, StatusEffectType.PROTECTION);
        if (protection != null) {
            multiplier *= 1.0 - Math.min(80.0, Math.max(0.0, protection.value())) / 100.0;
        }

        return Math.max(0.0, total * multiplier);
    }

    /** Current corrosion on the target: the defense it removes in the damage formula. */
    public double corrosionOf(LivingEntity target) {
        if (target == null) return 0.0;

        long now = System.currentTimeMillis();
        pruneExpired(target, now);
        return Math.max(0.0, timedTotal(corrosionEffects.get(target.getUniqueId()), now));
    }

    public Component statusLine(LivingEntity entity) {
        if (entity == null) return null;

        long now = System.currentTimeMillis();
        pruneExpired(entity, now);

        Component line = Component.empty();
        int visible = 0;

        BleedEffect bleed = bleedEffects.get(entity.getUniqueId());
        if (bleed != null && bleed.total() > EPSILON) {
            line = appendStatus(line, visible++, StatusEffectType.BLEED, compact(bleed.total()));
        }

        ActiveEffect slow = active(entity, StatusEffectType.SLOW);
        if (slow != null) {
            line = appendStatus(line, visible++, StatusEffectType.SLOW, compact(slow.value()) + "/" + remainingSeconds(slow, now) + "s");
        }

        double corrosion = timedTotal(corrosionEffects.get(entity.getUniqueId()), now);
        if (corrosion > EPSILON) {
            line = appendStatus(line, visible++, StatusEffectType.CORROSION, compact(corrosion));
        }

        double decay = timedTotal(decayEffects.get(entity.getUniqueId()), now);
        if (decay > EPSILON) {
            line = appendStatus(line, visible++, StatusEffectType.DECAY, compact(decay));
        }

        ActiveEffect confusion = active(entity, StatusEffectType.CONFUSION);
        if (confusion != null) {
            line = appendStatus(line, visible++, StatusEffectType.CONFUSION, compact(confusion.value()) + "/" + remainingSeconds(confusion, now) + "s");
        }

        ExplosionEffect explosion = explosionEffects.get(entity.getUniqueId());
        if (explosion != null && !explosion.expired(now) && explosion.stacks() > 0) {
            line = appendStatus(line, visible++, StatusEffectType.EXPLOSION, explosion.stacks() + "/" + EXPLOSION_TRIGGER_STACKS);
        }

        return visible == 0 ? null : line;
    }

    public int statusLineCount(LivingEntity entity) {
        if (entity == null) return 0;

        long now = System.currentTimeMillis();
        pruneExpired(entity, now);

        int visible = 0;
        BleedEffect bleed = bleedEffects.get(entity.getUniqueId());
        if (bleed != null && bleed.total() > EPSILON) visible++;
        if (active(entity, StatusEffectType.SLOW) != null) visible++;
        if (timedTotal(corrosionEffects.get(entity.getUniqueId()), now) > EPSILON) visible++;
        if (timedTotal(decayEffects.get(entity.getUniqueId()), now) > EPSILON) visible++;
        if (active(entity, StatusEffectType.CONFUSION) != null) visible++;

        ExplosionEffect explosion = explosionEffects.get(entity.getUniqueId());
        if (explosion != null && !explosion.expired(now) && explosion.stacks() > 0) visible++;

        return visible == 0 ? 0 : (visible + 1) / 2;
    }

    public void clear(Entity entity) {
        if (entity == null) return;

        clear(entity.getUniqueId(), entity instanceof LivingEntity living ? living : null);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID uuid : effectIds()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                clear(uuid, livingOrNull(entity));
                continue;
            }

            pruneExpired(living, now);
            tickEffects(living, now);
            pruneEmpty(uuid);
            refreshDisplay(living);
        }
    }

    private void tickEffects(LivingEntity target, long now) {
        UUID targetId = target.getUniqueId();

        Long stunnedUntil = stunUntil.get(targetId);
        if (stunnedUntil != null) {
            if (stunnedUntil > now) {
                holdStunned(target);
                drainConfusion(targetId, stunnedUntil, now);
            } else {
                endStun(target);
            }
        }

        BleedEffect bleed = bleedEffects.get(targetId);
        if (bleed != null && bleed.total() > EPSILON) {
            playAmbientEffect(target, StatusEffectType.BLEED, bleed.total(), -1);
            applyStatusDamage(target, StatusEffectType.BLEED, bleed.sourceAmounts());
            bleed.decay();
        }

        double corrosion = timedTotal(corrosionEffects.get(targetId), now);
        if (corrosion > EPSILON) {
            playAmbientEffect(target, StatusEffectType.CORROSION, corrosion, -1);
        }

        List<TimedStack> decayStacks = decayEffects.get(targetId);
        double decay = timedTotal(decayStacks, now);
        if (decay > EPSILON) {
            playAmbientEffect(target, StatusEffectType.DECAY, decay, -1);
            applyStatusDamage(target, StatusEffectType.DECAY, sourceDamageMap(decayStacks, now, 0.55));
        }

        ExplosionEffect explosion = explosionEffects.get(targetId);
        if (explosion != null && !explosion.expired(now)) {
            playAmbientEffect(target, StatusEffectType.EXPLOSION, explosion.stacks(), -1);
        }

        EnumMap<StatusEffectType, ActiveEffect> active = singleEffects.get(targetId);
        if (active == null || active.isEmpty()) return;

        for (ActiveEffect effect : active.values()) {
            if (effect.expired(now)) continue;

            playAmbientEffect(target, effect.type(), effect.value(), remainingSeconds(effect, now));
            switch (effect.type()) {
                case SLOW -> applySlowAttribute(target, effect.value());
                case CONFUSION -> applyConfusion(target, effect.value());
                case ADRENALINE -> applyAdrenaline(target, effect.value());
                default -> {
                }
            }
        }
    }

    private void applyBleed(LivingEntity target, double value, UUID sourceId) {
        if (value <= EPSILON) return;

        BleedEffect bleed = bleedEffects.computeIfAbsent(target.getUniqueId(), ignored -> new BleedEffect());
        bleed.add(sourceId, value);
    }

    /** Applies a slow for a fixed time. 100 or more stops the target completely. */
    public void applyTimedSlow(LivingEntity target, double percent, long durationMillis, UUID sourceId) {
        applySingle(target, StatusEffectType.SLOW, percent, durationMillis, sourceId, true);
    }

    private void applySlow(LivingEntity target, double value, UUID sourceId) {
        if (value <= EPSILON) return;

        applySingle(target, StatusEffectType.SLOW, value, SLOW_DURATION_MILLIS, sourceId, true);
    }

    private void applyTimedStack(Map<UUID, List<TimedStack>> targetMap, LivingEntity target, StatusEffectType type, double value, long durationMillis, UUID sourceId) {
        if (value <= EPSILON || durationMillis <= 0L) return;

        long expiresAt = System.currentTimeMillis() + durationMillis;
        targetMap.computeIfAbsent(target.getUniqueId(), ignored -> new ArrayList<>())
                .add(new TimedStack(type, value, expiresAt, sourceId));
    }

    private void applyExplosion(LivingEntity target, double value, UUID sourceId) {
        if (value <= EPSILON) return;

        ExplosionEffect explosion = explosionEffects.computeIfAbsent(target.getUniqueId(), ignored -> new ExplosionEffect());
        explosion.add(sourceId, value, System.currentTimeMillis() + EXPLOSION_DURATION_MILLIS);
        if (explosion.stacks() >= EXPLOSION_TRIGGER_STACKS) {
            explode(target, explosion);
        }
    }

    private void triggerLaceration(LivingEntity target, double value, UUID sourceId) {
        if (value <= EPSILON) return;

        BleedEffect bleed = bleedEffects.get(target.getUniqueId());
        double bleedAmount = bleed == null ? 0.0 : bleed.total();
        if (bleedAmount <= EPSILON) return;

        applyStatusDamage(target, StatusEffectType.LACERATION, Map.of(sourceId, bleedAmount * value));
    }

    private void applySingle(Entity target, StatusEffectType type, double value, long durationMillis, UUID sourceId, boolean refreshEvenWhenLower) {
        if (value <= EPSILON || durationMillis <= 0L) return;

        long expiresAt = System.currentTimeMillis() + durationMillis;
        EnumMap<StatusEffectType, ActiveEffect> active = singleEffects.computeIfAbsent(target.getUniqueId(), ignored -> new EnumMap<>(StatusEffectType.class));
        ActiveEffect current = active.get(type);
        if (current == null) {
            active.put(type, new ActiveEffect(type, value, expiresAt, sourceId));
            return;
        }

        if (refreshEvenWhenLower) {
            current.setValue(Math.max(current.value(), value));
        } else {
            current.setValue(Math.max(current.value(), value));
        }
        current.setExpiresAtMillis(Math.max(current.expiresAtMillis(), expiresAt));
        current.setSourceId(sourceId);
    }

    private void applyAbsorb(Player player, double damage, double percent) {
        if (damage <= EPSILON || percent <= EPSILON || player.isDead()) return;

        PlayerProfile profile = equipmentService.recalculate(player);
        double amount = damage * Math.max(0.0, percent) / 100.0;
        double before = profile.currentHp();
        profile.setCurrentHp(before + amount);
        if (profile.currentHp() > before + EPSILON) {
            playerBars.sync(player, profile);
        }
    }

    // ---- API used by class effects (ClassEffectService) ---------------------

    /** Adds bleed to the target as if a weapon with this bleed value hit it. */
    public void addBleed(LivingEntity target, double value, UUID sourceId) {
        applyBleed(target, value, sourceId);
        refreshDisplay(target);
    }

    public void addCorrosion(LivingEntity target, double value, UUID sourceId) {
        applyTimedStack(corrosionEffects, target, StatusEffectType.CORROSION, value, CORROSION_DURATION_MILLIS, sourceId);
        refreshDisplay(target);
    }

    public void addDecay(LivingEntity target, double value, UUID sourceId) {
        applyTimedStack(decayEffects, target, StatusEffectType.DECAY, value, DECAY_DURATION_MILLIS, sourceId);
        refreshDisplay(target);
    }

    /** Stacks confusion (100 stuns). Returns nothing; the stun is handled by the status task. */
    public void addConfusion(LivingEntity target, double add, UUID sourceId) {
        applyConfusionStack(target, add, plugin.getConfig().getLong("combat.status-effects.class-confusion-millis", 5000L), sourceId);
        refreshDisplay(target);
    }

    /** Current slow percent on the target (0 when not slowed). 100 or more means stopped. */
    public double slowOf(LivingEntity target) {
        ActiveEffect slow = active(target, StatusEffectType.SLOW);
        return slow == null ? 0.0 : slow.value();
    }

    public double confusionOf(LivingEntity target) {
        ActiveEffect confusion = active(target, StatusEffectType.CONFUSION);
        return confusion == null ? 0.0 : confusion.value();
    }

    public void addExplosion(LivingEntity target, double value, UUID sourceId) {
        applyExplosion(target, value, sourceId);
        refreshDisplay(target);
    }

    /** Flat extra damage shown as a status damage number (e.g. a laceration burst). */
    public void dealStatusDamage(LivingEntity target, StatusEffectType type, double amount, UUID sourceId) {
        applyStatusDamage(target, type, Map.of(sourceId, amount));
    }

    private void applyStatusDamage(LivingEntity target, StatusEffectType type, Map<UUID, Double> amounts) {
        if (target == null || target.isDead() || !target.isValid() || amounts == null || amounts.isEmpty()) return;

        double total = 0.0;
        for (Map.Entry<UUID, Double> entry : amounts.entrySet()) {
            double amount = Math.max(0.0, entry.getValue());
            if (amount <= EPSILON || target.isDead() || !target.isValid()) continue;

            total += amount;
            damageWithoutRecursion(target, amount, entry.getKey());
        }

        if (total > EPSILON && hasRemainingHealth(target) && attackEffects != null) {
            attackEffects.playRangeHurt(target);
            attackEffects.playStatusDamageNumber(target, type, total);
        }
    }

    private void damageWithoutRecursion(LivingEntity target, double amount, UUID sourceId) {
        Player attacker = sourceId == null ? null : Bukkit.getPlayer(sourceId);
        if (mobService != null && mobService.applyDamage(target, amount, attacker)) {
            return;
        }

        if (target instanceof Player player && player.isValid() && !player.isDead()) {
            PlayerProfile profile = equipmentService.recalculate(player);
            profile.setCurrentHp(profile.currentHp() - amount);
            if (profile.currentHp() <= 0.0 && !player.isDead()) {
                player.setHealth(0.0);
                return;
            }
            playerBars.sync(player, profile);
            return;
        }

        if (!(target instanceof Player) && target.isValid() && !target.isDead()) {
            target.damage(amount);
        }
    }

    /**
     * Confusion stacks up to 100. When it fills, the target is stunned for a short time
     * (config combat.status-effects.confusion-stun-ticks, default 40 = 2s) and the stack is cleared.
     */
    private void applyConfusionStack(LivingEntity target, double add, long durationMillis, UUID sourceId) {
        if (add <= EPSILON || durationMillis <= 0L) return;
        if (isStunned(target)) return;

        long expiresAt = System.currentTimeMillis() + durationMillis;
        EnumMap<StatusEffectType, ActiveEffect> active = singleEffects.computeIfAbsent(target.getUniqueId(), ignored -> new EnumMap<>(StatusEffectType.class));
        ActiveEffect current = active.get(StatusEffectType.CONFUSION);
        double total = Math.min(CONFUSION_MAX, (current == null ? 0.0 : current.value()) + add);
        if (total >= CONFUSION_MAX - EPSILON) {
            // Full stack: stun, and let the stack drain from 100 to 0 over the stun (see tickEffects).
            long stunEnd = stun(target);
            active.put(StatusEffectType.CONFUSION, new ActiveEffect(StatusEffectType.CONFUSION, CONFUSION_MAX, stunEnd, sourceId));
            return;
        }

        if (current == null) {
            active.put(StatusEffectType.CONFUSION, new ActiveEffect(StatusEffectType.CONFUSION, total, expiresAt, sourceId));
        } else {
            current.setValue(total);
            current.setExpiresAtMillis(Math.max(current.expiresAtMillis(), expiresAt));
            current.setSourceId(sourceId);
        }
    }

    private boolean isStunned(Entity entity) {
        Long until = stunUntil.get(entity.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private long stun(LivingEntity target) {
        long ticks = Math.max(1, plugin.getConfig().getInt("combat.status-effects.confusion-stun-ticks", 40));
        long end = System.currentTimeMillis() + ticks * 50L;
        stunUntil.put(target.getUniqueId(), end);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAware(false);
            awareDisabled.add(target.getUniqueId());
        }
        World world = target.getWorld();
        Location head = target.getLocation().add(0.0, target.getHeight() + 0.2, 0.0);
        world.playSound(head, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.6f);
        world.spawnParticle(Particle.PORTAL, head, 24, 0.3, 0.2, 0.3, 0.2);
        holdStunned(target);
        return end;
    }

    /** Keeps a stunned target from moving or acting. */
    private void holdStunned(LivingEntity target) {
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        Vector velocity = target.getVelocity();
        target.setVelocity(new Vector(0.0, Math.min(0.0, velocity.getY()), 0.0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 6, 7, false, false, false));
        Location head = target.getLocation().add(0.0, target.getHeight() + 0.15, 0.0);
        target.getWorld().spawnParticle(Particle.REDSTONE, head, 3, 0.2, 0.08, 0.2, 0.0,
                new Particle.DustOptions(Color.fromRGB(170, 70, 230), 1.0f));
    }

    /** During the stun the confusion stack falls linearly from 100 to 0. */
    private void drainConfusion(UUID targetId, long stunEnd, long now) {
        EnumMap<StatusEffectType, ActiveEffect> active = singleEffects.get(targetId);
        ActiveEffect confusion = active == null ? null : active.get(StatusEffectType.CONFUSION);
        if (confusion == null) return;

        long totalMillis = Math.max(1, plugin.getConfig().getInt("combat.status-effects.confusion-stun-ticks", 40)) * 50L;
        double remaining = Math.max(0.0, Math.min(1.0, (stunEnd - now) / (double) totalMillis));
        confusion.setValue(CONFUSION_MAX * remaining);
    }

    private void endStun(LivingEntity target) {
        stunUntil.remove(target.getUniqueId());
        restoreAware(target);
    }

    private void restoreAware(LivingEntity target) {
        if (awareDisabled.remove(target.getUniqueId()) && target instanceof Mob mob) {
            mob.setAware(true);
        }
    }

    /**
     * Ten stacked explosion hits detonate around the target: everything nearby takes the
     * accumulated amount. Radius and fuse come from combat.status-effects.explosion-radius / -delay-ticks.
     */
    private void explode(LivingEntity target, ExplosionEffect explosion) {
        explosionEffects.remove(target.getUniqueId());
        Map<UUID, Double> amounts = explosion.sourceAmounts();
        long delay = Math.max(0, plugin.getConfig().getInt("combat.status-effects.explosion-delay-ticks", 40));
        double radius = Math.max(0.5, plugin.getConfig().getDouble("combat.status-effects.explosion-radius", 3.0));

        Runnable detonate = () -> detonate(target, amounts, radius);
        if (delay <= 0L) {
            detonate.run();
            return;
        }

        World world = target.getWorld();
        Location fuse = target.getLocation().add(0.0, target.getHeight() * 0.6, 0.0);
        world.playSound(fuse, Sound.ENTITY_CREEPER_PRIMED, 0.8f, 1.2f);
        world.spawnParticle(Particle.SMOKE_NORMAL, fuse, 12, 0.3, 0.3, 0.3, 0.02);
        plugin.getServer().getScheduler().runTaskLater(plugin, detonate, delay);
    }

    private void detonate(LivingEntity origin, Map<UUID, Double> amounts, double radius) {
        World world = origin.getWorld();
        Location center = origin.getLocation().add(0.0, origin.getHeight() * 0.45, 0.0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.3f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, center, 2, 0.4, 0.3, 0.4, 0.0);
        world.spawnParticle(Particle.REDSTONE, center, 30, radius * 0.4, 0.5, radius * 0.4, 0.0,
                new Particle.DustOptions(Color.fromRGB(255, 150, 40), 1.4f));

        List<LivingEntity> victims = new ArrayList<>();
        if (origin.isValid() && !origin.isDead()) {
            victims.add(origin);
        }
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(origin)) continue;
            if (living instanceof Player || living instanceof ArmorStand || DeployService.isDeployEntity(living)) continue;
            if (!living.isValid() || living.isDead()) continue;
            if (living.getLocation().distanceSquared(center) > radius * radius) continue;
            victims.add(living);
        }
        for (LivingEntity victim : victims) {
            applyStatusDamage(victim, StatusEffectType.EXPLOSION, amounts);
            refreshDisplay(victim);
        }
    }

    private void applySlowAttribute(LivingEntity target, double value) {
        double percent = Math.max(0.0, Math.min(100.0, value));
        AttributeInstance movement = target.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (movement == null) {
            int amplifier = Math.max(0, Math.min(4, (int) Math.ceil(percent / 20.0) - 1));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 45, amplifier, false, false, true));
            return;
        }

        movement.removeModifier(SLOW_MODIFIER_ID);
        movement.addTransientModifier(new AttributeModifier(
                SLOW_MODIFIER_ID,
                "iruuRPG slow",
                -percent / 100.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1
        ));
    }

    private void removeSlowAttribute(LivingEntity target) {
        AttributeInstance movement = target.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(SLOW_MODIFIER_ID);
        }
    }

    private void applyAdrenaline(LivingEntity target, double value) {
        if (!(target instanceof Player player)) return;

        int amplifier = Math.max(0, Math.min(3, (int) Math.floor(Math.max(0.0, value) / 35.0)));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, amplifier, false, false, true));
    }

    private void applyConfusion(LivingEntity target, double value) {
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        if (value <= 0.0 || target.isOnGround()) return;

        Vector velocity = target.getVelocity();
        target.setVelocity(new Vector(-velocity.getZ() * 0.25, velocity.getY(), velocity.getX() * 0.25));
    }

    private void playAmbientEffect(LivingEntity target, StatusEffectType type, double value, int remainingSeconds) {
        if (!target.isValid() || target.isDead()) return;

        Location center = target.getLocation().add(0.0, target.getHeight() * 0.55, 0.0);
        World world = target.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        switch (type) {
            case BLEED -> {
                Location drop = center.clone().add(randomOffset(random, 0.22), 0.12, randomOffset(random, 0.22));
                world.spawnParticle(Particle.REDSTONE, drop, 3, 0.035, 0.18, 0.035, 0.0,
                        new Particle.DustOptions(Color.fromRGB(190, 0, 20), 0.9f));
                world.spawnParticle(Particle.DAMAGE_INDICATOR, drop.clone().add(0.0, 0.12, 0.0), 1, 0.06, 0.02, 0.06, 0.0);
            }
            case SLOW -> {
                Location feet = target.getLocation().add(0.0, 0.18, 0.0);
                world.spawnParticle(Particle.SNOWFLAKE, feet, 4, 0.28, 0.08, 0.28, 0.01);
                world.spawnParticle(Particle.CLOUD, feet, 1, 0.22, 0.02, 0.22, 0.0);
            }
            case CORROSION -> world.spawnParticle(Particle.REDSTONE, center, 5, 0.3, 0.35, 0.3, 0.0,
                    new Particle.DustOptions(Color.fromRGB(38, 135, 42), 0.8f));
            case DECAY -> {
                world.spawnParticle(Particle.ASH, center, 5, 0.3, 0.45, 0.3, 0.01);
                world.spawnParticle(Particle.REDSTONE, center, 2, 0.22, 0.3, 0.22, 0.0,
                        new Particle.DustOptions(Color.fromRGB(100, 100, 100), 0.75f));
            }
            case CONFUSION -> {
                Location head = target.getLocation().add(0.0, target.getHeight() + 0.15, 0.0);
                world.spawnParticle(Particle.PORTAL, head, 6, 0.22, 0.12, 0.22, 0.03);
                world.spawnParticle(Particle.REDSTONE, head, 2, 0.18, 0.08, 0.18, 0.0,
                        new Particle.DustOptions(Color.fromRGB(170, 70, 230), 0.8f));
            }
            case EXPLOSION -> {
                Location fuse = target.getLocation().add(0.0, target.getHeight() * 0.75, 0.0);
                int dust = value >= 8.0 ? 5 : 2;
                world.spawnParticle(Particle.SMOKE_NORMAL, fuse, 5, 0.25, 0.25, 0.25, 0.01);
                world.spawnParticle(Particle.REDSTONE, fuse, dust, 0.22, 0.18, 0.22, 0.0,
                        new Particle.DustOptions(Color.fromRGB(255, 125, 15), 0.95f));
            }
            case ADRENALINE -> world.spawnParticle(Particle.CRIT, center, 5, 0.28, 0.35, 0.28, 0.02);
            case MAGIC_AMPLIFY -> world.spawnParticle(Particle.SPELL_WITCH, center, 5, 0.28, 0.35, 0.28, 0.02);
            case PROTECTION -> world.spawnParticle(Particle.REDSTONE, center, 5, 0.35, 0.45, 0.35, 0.0,
                    new Particle.DustOptions(Color.fromRGB(55, 120, 255), 0.85f));
            case LACERATION -> {
            }
        }
    }

    private void pruneExpired(LivingEntity entity, long now) {
        UUID id = entity.getUniqueId();

        EnumMap<StatusEffectType, ActiveEffect> active = singleEffects.get(id);
        if (active != null) {
            Iterator<Map.Entry<StatusEffectType, ActiveEffect>> iterator = active.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<StatusEffectType, ActiveEffect> entry = iterator.next();
                if (!entry.getValue().expired(now)) continue;

                if (entry.getKey() == StatusEffectType.SLOW) {
                    removeSlowAttribute(entity);
                }
                iterator.remove();
            }
            if (active.isEmpty()) {
                singleEffects.remove(id);
            }
        }

        pruneTimed(corrosionEffects, id, now);
        pruneTimed(decayEffects, id, now);

        BleedEffect bleed = bleedEffects.get(id);
        if (bleed != null && bleed.total() <= EPSILON) {
            bleedEffects.remove(id);
        }

        ExplosionEffect explosion = explosionEffects.get(id);
        if (explosion != null && explosion.expired(now)) {
            explosionEffects.remove(id);
        }
    }

    private void pruneTimed(Map<UUID, List<TimedStack>> targetMap, UUID id, long now) {
        List<TimedStack> stacks = targetMap.get(id);
        if (stacks == null) return;

        stacks.removeIf(stack -> stack.expired(now));
        if (stacks.isEmpty()) {
            targetMap.remove(id);
        }
    }

    private void pruneEmpty(UUID id) {
        BleedEffect bleed = bleedEffects.get(id);
        if (bleed != null && bleed.total() <= EPSILON) {
            bleedEffects.remove(id);
        }

        if (singleEffects.getOrDefault(id, new EnumMap<>(StatusEffectType.class)).isEmpty()) {
            singleEffects.remove(id);
        }
        if (corrosionEffects.getOrDefault(id, List.of()).isEmpty()) {
            corrosionEffects.remove(id);
        }
        if (decayEffects.getOrDefault(id, List.of()).isEmpty()) {
            decayEffects.remove(id);
        }
    }

    private Set<UUID> effectIds() {
        Set<UUID> ids = new HashSet<>();
        ids.addAll(singleEffects.keySet());
        ids.addAll(bleedEffects.keySet());
        ids.addAll(corrosionEffects.keySet());
        ids.addAll(decayEffects.keySet());
        ids.addAll(explosionEffects.keySet());
        ids.addAll(stunUntil.keySet());
        return ids;
    }

    private void clear(UUID id, LivingEntity living) {
        singleEffects.remove(id);
        bleedEffects.remove(id);
        corrosionEffects.remove(id);
        decayEffects.remove(id);
        explosionEffects.remove(id);
        stunUntil.remove(id);
        if (living != null) {
            restoreAware(living);
            removeSlowAttribute(living);
        } else {
            awareDisabled.remove(id);
        }
    }

    private LivingEntity livingOrNull(Entity entity) {
        return entity instanceof LivingEntity living ? living : null;
    }

    private ActiveEffect active(Entity entity, StatusEffectType type) {
        EnumMap<StatusEffectType, ActiveEffect> active = singleEffects.get(entity.getUniqueId());
        if (active == null) return null;

        ActiveEffect effect = active.get(type);
        return effect == null || effect.expired(System.currentTimeMillis()) ? null : effect;
    }

    private Component appendStatus(Component line, int visible, StatusEffectType type, String value) {
        if (visible > 0) {
            line = line.append(visible % 2 == 0
                    ? Component.newline()
                    : Component.text("  ", NamedTextColor.DARK_GRAY));
        }

        return line.append(Component.text(type.icon(), type.color()))
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private Map<UUID, Double> sourceDamageMap(List<TimedStack> stacks, long now, double multiplier) {
        Map<UUID, Double> result = new HashMap<>();
        if (stacks == null) return result;

        for (TimedStack stack : stacks) {
            if (stack.expired(now)) continue;
            result.merge(stack.sourceId(), Math.max(0.0, stack.value() * multiplier), Double::sum);
        }
        return result;
    }

    private double timedTotal(List<TimedStack> stacks, long now) {
        if (stacks == null || stacks.isEmpty()) return 0.0;

        double total = 0.0;
        for (TimedStack stack : stacks) {
            if (!stack.expired(now)) {
                total += stack.value();
            }
        }
        return total;
    }

    private boolean hasRemainingHealth(LivingEntity victim) {
        return victim.isValid() && !victim.isDead() && victim.getHealth() > 0.0;
    }

    private void refreshDisplay(LivingEntity entity) {
        if (mobService != null && mobService.isRpgMob(entity)) {
            mobService.refreshDisplay(entity);
        }
    }

    private long durationMillis(PlayerProfile profile, String configKey, int fallbackTicks) {
        int baseTicks = Math.max(1, plugin.getConfig().getInt("combat.status-effects." + configKey, fallbackTicks));
        double duration = profile.finalStats().get(StatType.DURATION);
        double multiplier = Math.max(0.1, 1.0 + duration / 100.0);
        return Math.max(50L, Math.round(baseTicks * multiplier) * 50L);
    }

    private int remainingSeconds(ActiveEffect effect, long now) {
        return Math.max(0, (int) Math.ceil(Math.max(0L, effect.expiresAtMillis() - now) / 1000.0));
    }

    private double randomOffset(ThreadLocalRandom random, double radius) {
        return random.nextDouble(-radius, radius);
    }

    private String compact(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000.0) {
            return compactUnit(value / 1_000_000.0, "M");
        }
        if (absolute >= 1_000.0) {
            return compactUnit(value / 1_000.0, "K");
        }
        return format(value);
    }

    private String compactUnit(double value, String suffix) {
        double absolute = Math.abs(value);
        String pattern = absolute >= 100.0 || Math.abs(value - Math.rint(value)) < 1.0E-9 ? "%.0f%s" : "%.1f%s";
        return String.format(Locale.ROOT, pattern, value, suffix);
    }

    private String format(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.abs(value - Math.rint(value)) < 1.0E-9 ? 0 : 1);
        return format.format(value);
    }

    private static final class BleedEffect {
        private final Map<UUID, Double> sourceAmounts = new HashMap<>();

        private void add(UUID sourceId, double value) {
            double cap = Math.max(0.0, value * 3.0);
            double current = total();
            if (current + EPSILON >= cap) return;

            double added = Math.min(value, cap - current);
            if (added <= EPSILON) return;

            sourceAmounts.merge(sourceId, added, Double::sum);
        }

        private double total() {
            double total = 0.0;
            for (double value : sourceAmounts.values()) {
                total += value;
            }
            return total;
        }

        private Map<UUID, Double> sourceAmounts() {
            return new HashMap<>(sourceAmounts);
        }

        private void decay() {
            double current = total();
            double next = Math.floor(current * 2.0 / 3.0);
            if (next <= EPSILON || current <= EPSILON) {
                sourceAmounts.clear();
                return;
            }

            double scale = next / current;
            sourceAmounts.replaceAll((ignored, value) -> value * scale);
            sourceAmounts.values().removeIf(value -> value <= EPSILON);
        }
    }

    private static final class ExplosionEffect {
        private final Map<UUID, Double> sourceAmounts = new HashMap<>();
        private int stacks;
        private long expiresAtMillis;

        private void add(UUID sourceId, double value, long expiresAtMillis) {
            sourceAmounts.merge(sourceId, value, Double::sum);
            stacks++;
            this.expiresAtMillis = expiresAtMillis;
        }

        private int stacks() {
            return stacks;
        }

        private Map<UUID, Double> sourceAmounts() {
            return new HashMap<>(sourceAmounts);
        }

        private boolean expired(long now) {
            return expiresAtMillis <= now;
        }
    }

    private record TimedStack(StatusEffectType type, double value, long expiresAtMillis, UUID sourceId) {
        private boolean expired(long now) {
            return expiresAtMillis <= now;
        }
    }

    private static final class ActiveEffect {
        private final StatusEffectType type;
        private double value;
        private long expiresAtMillis;
        private UUID sourceId;

        private ActiveEffect(StatusEffectType type, double value, long expiresAtMillis, UUID sourceId) {
            this.type = type;
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
            this.sourceId = sourceId;
        }

        private StatusEffectType type() {
            return type;
        }

        private double value() {
            return value;
        }

        private void setValue(double value) {
            this.value = value;
        }

        private long expiresAtMillis() {
            return expiresAtMillis;
        }

        private void setExpiresAtMillis(long expiresAtMillis) {
            this.expiresAtMillis = expiresAtMillis;
        }

        private void setSourceId(UUID sourceId) {
            this.sourceId = sourceId;
        }

        private boolean expired(long now) {
            return expiresAtMillis <= now;
        }
    }
}
