package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.item.ItemIdentifier;
import net.tkgon.mc.iruuRPG.item.ItemSkillType;
import net.tkgon.mc.iruuRPG.item.ItemVisualOptions;
import net.tkgon.mc.iruuRPG.item.Rarity;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.mob.DebugTargetService;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.text.NumberFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

public final class AttackService {

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final EquipmentService equipmentService;
    private final ItemIdentifier itemIdentifier;
    private final RpgItemRegistry itemRegistry;
    private final DamageCalculator damageCalculator;
    private final AttackCooldowns attackCooldowns;
    private final AttackEffects attackEffects;
    private static final String MELEE_AREA_SKILL = "melee_area";
    private static final String RANGE_AREA_EXPAND_SKILL = "range_area_expand";

    private final PlayerBars playerBars;
    private final StatusEffectService statusEffectService;
    private RpgMobService mobService;
    private ClassService classService;
    private TutorialDamageHook tutorialHook;
    private static final double TUTORIAL_DAMAGE_CAP = 99.0;
    private ClassEffectService classEffectService;
    private DebugTargetService debugTargetService;
    private java.util.function.BiConsumer<Player, String> stackHook;

    /** Called with (player, "hit" | "damaged") when something a weapon's stacks can grow from happens. */
    public void setStackHook(java.util.function.BiConsumer<Player, String> stackHook) {
        this.stackHook = stackHook;
    }

    private void stackTrigger(Player player, String trigger) {
        if (stackHook != null) stackHook.accept(player, trigger);
    }

    /** A normal attack of the player landed on an enemy. */
    public void normalAttackHit(Player attacker) {
        stackTrigger(attacker, "hit");
    }

    public AttackService(
            JavaPlugin plugin,
            PlayerProfileManager profileManager,
            EquipmentService equipmentService,
            ItemIdentifier itemIdentifier,
            RpgItemRegistry itemRegistry,
            DamageCalculator damageCalculator,
            AttackCooldowns attackCooldowns,
            AttackEffects attackEffects,
            PlayerBars playerBars,
            StatusEffectService statusEffectService
    ) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.equipmentService = equipmentService;
        this.itemIdentifier = itemIdentifier;
        this.itemRegistry = itemRegistry;
        this.damageCalculator = damageCalculator;
        this.attackCooldowns = attackCooldowns;
        this.attackEffects = attackEffects;
        this.playerBars = playerBars;
        this.statusEffectService = statusEffectService;
        this.meleeCombo = new MeleeCombo(plugin);
    }

    private final MeleeCombo meleeCombo;

    /**
     * The next melee attack of the light / heavy chain (shift + left click is heavy). Call it once per attack that
     * got through the cooldown; a heavy attack or the finisher also lengthens the cooldown (its recovery).
     */
    public MeleeCombo.Step nextMeleeStep(Player attacker) {
        MeleeCombo.Step step = meleeCombo.next(attacker, attacker.isSneaking(), comboPlan(attacker));
        if (step == null) {
            // a heavy attack that is still cooling down: a dull click, nothing thrown
            attacker.playSound(attacker.getLocation(), org.bukkit.Sound.BLOCK_DISPENSER_FAIL, org.bukkit.SoundCategory.PLAYERS, 0.5f, 1.4f);
            return null;
        }
        attackCooldowns.extend(attacker, AttackType.MELEE, step.recoveryTicks());
        return step;
    }

    /** Which combo plan the player follows: the assassin has its own, everybody else the usual one. */
    public String comboPlan(Player attacker) {
        return hasClassId(attacker, "assassin") ? MeleeCombo.ASSASSIN : MeleeCombo.USUAL;
    }

    private boolean hasClassId(Player attacker, String classId) {
        if (classService == null) return false;
        return classService.currentClass(profileManager.getOrCreate(attacker)).map(definition -> definition.id().equals(classId)).orElse(false);
    }

    private ComboMoves comboMoves;

    public void setComboMoves(ComboMoves comboMoves) {
        this.comboMoves = comboMoves;
    }

    /** Throws a heavy attack of the assassin plan (its movement and damage are in {@link ComboMoves}). */
    public void performHeavy(Player attacker, RpgItemDefinition weapon, MeleeCombo.Step step) {
        if (comboMoves != null) comboMoves.perform(attacker, weapon, step);
    }

    /**
     * The right click of an assassin with a melee weapon: heavy attack 0 (a step). False when the player is not an
     * assassin (the click is not used), true when it was used, also when the heavy attack still cools down.
     */
    public boolean tryHeavyZero(Player attacker, RpgItemDefinition weapon) {
        if (weapon == null || !weapon.isWeaponLike() || weapon.attackType() != AttackType.MELEE) return false;
        if (!MeleeCombo.ASSASSIN.equals(comboPlan(attacker)) || attacker.isSneaking()) return false;
        if (!canUseItem(attacker, weapon)) {
            sendLevelRequirement(attacker, weapon);
            return true;
        }
        MeleeCombo.Step step = meleeCombo.heavyZero(attacker);
        if (step == null) {
            attacker.playSound(attacker.getLocation(), org.bukkit.Sound.BLOCK_DISPENSER_FAIL, org.bukkit.SoundCategory.PLAYERS, 0.5f, 1.4f);
            return true;
        }
        performHeavy(attacker, weapon, step);
        return true;
    }

    /** One hit of {@code multiplier} times the weapon's damage on {@code target}, with its number and impact. */
    public boolean strike(Player attacker, LivingEntity target, RpgItemDefinition weapon, double multiplier) {
        if (multiplier <= 0.0 || target.isDead() || !target.isValid()) return false;
        AttackDamage attack = calculateDamage(attacker, target, weapon);
        double damage = round(attack.result().damage() * multiplier);
        boolean critical = attack.result().critical();
        if (!applyDirectDamage(attacker, target, damage, weapon, critical)) return false;
        playMeleeImpact(attacker, target);
        sendDamageDebug(attacker, AttackType.MELEE, target, damage, critical, false);
        return true;
    }

    /** {@code count} more hits of {@code rate} times the weapon's damage on {@code victim}, a few ticks apart. */
    public void scheduleExtraHits(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double rate, int count, long gapTicks) {
        for (int index = 1; index <= count; index++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!attacker.isOnline() || attacker.isDead() || victim.isDead() || !victim.isValid()) return;
                strike(attacker, victim, weapon, rate);
            }, gapTicks * index);
        }
    }

    private net.tkgon.mc.iruuRPG.skill.SkillRegistry comboStyleParser;

    /** The skill registry that reads `combo-style:` sections. */
    public void setComboStyleParser(net.tkgon.mc.iruuRPG.skill.SkillRegistry parser) {
        this.comboStyleParser = parser;
    }

    private ComboStyle comboStyleOf(String owner, java.util.Map<String, Object> raw) {
        return comboStyleParser == null || raw == null || raw.isEmpty() ? null : comboStyleParser.comboStyle(owner, raw);
    }

    public void setMobService(RpgMobService mobService) {
        this.mobService = mobService;
    }

    public void setDebugTargetService(DebugTargetService debugTargetService) {
        this.debugTargetService = debugTargetService;
    }

    public boolean isCustomWeaponAttack(Entity damager) {
        Player attacker = resolvePlayerAttacker(damager);
        if (attacker == null) return false;

        RpgItemDefinition weapon = weaponInMainHand(attacker);
        return weapon != null && weapon.isWeaponLike();
    }

    public boolean isPlayerVersusPlayer(Entity damager, Entity victim) {
        return victim instanceof Player && resolvePlayerAttacker(damager) != null;
    }

    public Player resolvePlayerAttacker(Entity damager) {
        Entity attacker = damager;

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }

        return attacker instanceof Player player ? player : null;
    }

    private LivingEntity resolveLivingAttacker(Entity damager) {
        Entity attacker = damager;

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }

        return attacker instanceof LivingEntity living ? living : null;
    }

    public RpgItemDefinition weaponInMainHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return itemIdentifier.itemId(item)
                .flatMap(itemRegistry::find)
                .orElse(null);
    }

    public boolean canUseItem(Player player, RpgItemDefinition definition) {
        return equipmentService.canUse(player, definition);
    }

    public void sendLevelRequirement(Player player, RpgItemDefinition definition) {
        if (definition == null) return;

        PlayerProfile profile = profileManager.getOrCreate(player);
        player.sendMessage("[iruuRPG] このアイテムはLv" + definition.requiredLevel()
                + "から使用できます。現在Lv" + profile.level() + "です。");
    }

    /** The tutorial's NPC and rat: only the player running that tutorial can interact with them. */
    private boolean isTutorialProtected(LivingEntity victim) {
        return tutorialHook != null && victim != null && tutorialHook.isProtected(victim);
    }

    public void setTutorialHook(TutorialDamageHook tutorialHook) {
        this.tutorialHook = tutorialHook;
    }

    public boolean tryStart(Player attacker, AttackType attackType) {
        if (tutorialHook != null && tutorialHook.isAttackLocked(attacker)) {
            return false;
        }
        int cooldownTicks = effectiveCooldownTicks(attacker, attackType);
        if (!attackCooldowns.tryStart(attacker, attackType, cooldownTicks)) {
            return false;
        }

        showItemCooldown(attacker, attackType, cooldownTicks);
        return true;
    }

    public long remainingCooldownMillis(Player attacker, AttackType attackType) {
        return attackCooldowns.remainingMillis(attacker, attackType);
    }

    public boolean consumeMp(Player player, double amount) {
        PlayerProfile profile = profileForPlayer(player);
        if (profile.currentMp() + 1.0E-9 < amount) {
            return false;
        }

        profile.setCurrentMp(profile.currentMp() - amount);
        playerBars.sync(player, profile);
        return true;
    }

    public AttackDamage calculateDamage(Player attacker, LivingEntity victim, RpgItemDefinition weapon) {
        PlayerProfile attackerProfile = equipmentService.recalculate(attacker);
        return calculateDamage(attackerProfile, victim, weapon);
    }

    public AttackDamage calculateDamage(PlayerProfile attackerProfile, LivingEntity victim, RpgItemDefinition weapon) {
        PlayerProfile victimProfile = profileForVictim(victim);
        DamageResult result = damageCalculator.calculate(new DamageInput(attackerProfile, victimProfile, weapon, statusEffectService.corrosionOf(victim)));
        return new AttackDamage(result, victimProfile);
    }

    public DamageResult calculateNeutralDamage(PlayerProfile attackerProfile, RpgItemDefinition weapon) {
        PlayerProfile neutralVictim = new PlayerProfile(UUID.nameUUIDFromBytes("iruurpg:neutral-victim".getBytes()), 1.0, 1.0);
        DamageResult result = damageCalculator.calculate(new DamageInput(attackerProfile, neutralVictim, weapon));
        return result;
    }

    public OptionalDouble incomingRpgMobDamage(EntityDamageEvent event, Player victim) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return OptionalDouble.empty();
        if (mobService == null) return OptionalDouble.empty();

        LivingEntity attacker = resolveLivingAttacker(byEntity.getDamager());
        if (attacker == null || !mobService.isRpgMob(attacker)) return OptionalDouble.empty();

        PlayerProfile attackerProfile = mobService.profile(attacker).orElse(null);
        if (attackerProfile == null) return OptionalDouble.empty();

        PlayerProfile victimProfile = equipmentService.recalculate(victim);
        double corrosion = statusEffectService.corrosionOf(victim);
        RpgItemDefinition weapon = mobService.attackWeapon(attacker)
                .filter(RpgItemDefinition::isWeaponLike)
                .orElseGet(this::fallbackMobWeapon);
        DamageResult result = damageCalculator.calculate(new DamageInput(attackerProfile, victimProfile, weapon, corrosion));
        // a skill hit multiplies the mob's normal damage (see RpgMobService#hurt)
        double rate = mobService.pendingDamageRate(attacker);
        return OptionalDouble.of(scaleIncomingRpgMobDamage(Math.max(0.0, result.damage()) * rate, victimProfile));
    }

    public void applyRpgMobStatusEffects(EntityDamageEvent event, Player victim, double damage) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
        if (mobService == null || damage <= 0.0 || victim == null || victim.isDead() || !victim.isValid()) return;

        LivingEntity attacker = resolveLivingAttacker(byEntity.getDamager());
        if (attacker == null || !mobService.isRpgMob(attacker)) return;

        PlayerProfile attackerProfile = mobService.profile(attacker).orElse(null);
        if (attackerProfile == null) return;

        RpgItemDefinition weapon = mobService.attackWeapon(attacker)
                .filter(RpgItemDefinition::isWeaponLike)
                .orElseGet(this::fallbackMobWeapon);
        statusEffectService.onMobDamageDealt(attacker, victim, weapon, attackerProfile, damage);
        stackTrigger(victim, "damaged");
    }

    public PlayerProfile snapshotAttacker(Player attacker) {
        PlayerProfile live = equipmentService.recalculate(attacker);
        PlayerProfile snapshot = new PlayerProfile(attacker.getUniqueId(), live.maxHp(), live.maxMp());
        snapshot.setLevel(live.level());
        snapshot.setXp(live.xp());
        snapshot.baseStats().replaceWith(live.finalStats());
        snapshot.baseElementStats().replaceWith(live.finalElementStats());
        snapshot.recalculate();
        snapshot.setCurrentHp(live.currentHp());
        snapshot.setCurrentMp(live.currentMp());
        return snapshot;
    }

    public PlayerProfile profileForPlayer(Player player) {
        return profileManager.getOrCreate(player);
    }

    public PlayerProfile profileForVictim(LivingEntity victim) {
        if (victim instanceof Player player) {
            return equipmentService.recalculate(player);
        }

        if (mobService != null) {
            PlayerProfile profile = mobService.profile(victim).orElse(null);
            if (profile != null) return profile;
        }

        double maxHealth = maxHealth(victim);
        PlayerProfile profile = new PlayerProfile(victim.getUniqueId(), maxHealth, 1.0);
        profile.setCurrentHp(victim.getHealth());
        return profile;
    }

    public LivingEntity findRangeVictim(Player attacker) {
        return traceRange(attacker).victim();
    }

    /**
     * How far a ranged attack of this player reaches: every class has balance.range-multiplier (0.5) of
     * combat.range-distance; the mage's innate "range_double" doubles it back.
     */
    public double rangeDistance(Player attacker) {
        return plugin.getConfig().getDouble("combat.range-distance", 16.0)
                * Math.max(0.1, plugin.getConfig().getDouble("balance.range-multiplier", 0.5))
                * (hasSkill(attacker, RANGE_DOUBLE_SKILL) ? 2.0 : 1.0);
    }

    private static final String RANGE_DOUBLE_SKILL = "range_double";
    private static final String RANGE_INSTANT_SKILL = "range_instant";
    private static final String ATTACK_SPEED_PROJECTILE_SKILL = "attack_speed_cooldown";

    /** True when this player's ranged attack lands at once (no flight): the ranger's innate "range_instant". */
    public boolean rangedIsInstant(Player attacker) {
        return hasSkill(attacker, RANGE_INSTANT_SKILL);
    }

    /**
     * Blocks per second a ranged shot of this player flies: balance.projectile-speed (40); the mage's
     * attack-speed node also raises it by the attack speed %.
     */
    public double projectileSpeed(Player attacker) {
        double speed = Math.max(4.0, plugin.getConfig().getDouble("balance.projectile-speed", 40.0));
        if (hasSkill(attacker, ATTACK_SPEED_PROJECTILE_SKILL)) {
            double attackSpeed = equipmentService.recalculate(attacker).finalStats().get(StatType.ATTACK_SPEED);
            speed *= Math.max(1.0, 1.0 + attackSpeed / 100.0);
        }
        return Math.min(400.0, speed);
    }

    /**
     * A ranged shot with a speed: the shot flies from the eye along where the player looks and hits the first
     * enemy (or block) on the way; the damage is the one of an ordinary ranged hit at the moment it arrives.
     */
    public void fireRangedProjectile(Player attacker, RpgItemDefinition weapon) {
        double perTick = projectileSpeed(attacker) / 20.0;
        double maxDistance = rangeDistance(attacker);
        double raySize = plugin.getConfig().getDouble("combat.range-ray-size", 0.6);
        Location eye = attacker.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location start = eye.clone().add(direction.clone().multiply(plugin.getConfig().getDouble("combat.effects.range-trail.start-offset", 0.65)));
        attackEffects.playRangeLaunch(attacker, weapon, start);
        org.bukkit.World world = attacker.getWorld();

        new org.bukkit.scheduler.BukkitRunnable() {
            private Location position = start.clone();
            private double travelled;

            @Override
            public void run() {
                if (!attacker.isOnline() || !position.getWorld().equals(attacker.getWorld())) {
                    cancel();
                    return;
                }
                double step = Math.min(perTick, maxDistance - travelled);
                if (step <= 1.0E-6) {
                    cancel();
                    return;
                }
                RayTraceResult result = world.rayTrace(position, direction, step, FluidCollisionMode.NEVER, true, raySize,
                        entity -> isAttackTarget(entity, attacker));
                Location next = result != null && result.getHitPosition() != null
                        ? result.getHitPosition().toLocation(world)
                        : position.clone().add(direction.clone().multiply(step));
                attackEffects.playBoltSegment(position, next, weapon.element());
                if (result != null) {
                    cancel();
                    if (result.getHitEntity() instanceof LivingEntity victim) {
                        hitWithShot(attacker, weapon, victim, next);
                    } else {
                        attackEffects.playRangeImpact(attacker, next, weapon);
                        sendMissDebug(attacker, AttackType.RANGE);
                    }
                    return;
                }
                position = next;
                travelled += step;
                if (travelled >= maxDistance - 1.0E-6) {
                    cancel();
                    sendMissDebug(attacker, AttackType.RANGE);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The hit of a ranged shot that reached an enemy (also the one of an instant shot). */
    public void hitWithShot(Player attacker, RpgItemDefinition weapon, LivingEntity victim, Location hitLocation) {
        if (victim.isDead() || !victim.isValid()) return;
        AttackDamage attack = calculateDamage(attacker, victim, weapon);
        double scale = tripleScale(attacker);
        double damage = round(attack.result().damage() * scale);
        if (!applyRangeDamage(attacker, victim, damage, weapon, attack.result().critical())) {
            return;
        }
        playRangeImpact(attacker, hitLocation, weapon);
        sendDamageDebug(attacker, AttackType.RANGE, victim, damage, attack.result().critical(), false);
        applyAreaDamage(attacker, victim, damage, AttackType.RANGE, weapon);
        if (scale < 1.0) scheduleTripleHits(attacker, victim, weapon, AttackType.RANGE);
        normalAttackHit(attacker);
    }

    public RangeTrace traceRange(Player attacker) {
        double range = rangeDistance(attacker);
        double raySize = plugin.getConfig().getDouble("combat.range-ray-size", 0.6);
        Location start = attacker.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        Location end = start.clone().add(direction.clone().multiply(range));

        RayTraceResult result = attacker.getWorld().rayTrace(
                start,
                direction,
                range,
                FluidCollisionMode.NEVER,
                true,
                raySize,
                entity -> isAttackTarget(entity, attacker)
        );

        if (result == null || !(result.getHitEntity() instanceof LivingEntity victim)) {
            Location hitLocation = result != null && result.getHitPosition() != null
                    ? result.getHitPosition().toLocation(attacker.getWorld())
                    : end;
            return new RangeTrace(null, hitLocation);
        }

        Location hitLocation = result.getHitPosition() != null
                ? result.getHitPosition().toLocation(attacker.getWorld())
                : victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
        return new RangeTrace(victim, hitLocation);
    }

    public void applyDirectDamage(LivingEntity victim, double damage) {
        applyDirectDamage(null, victim, damage);
    }

    public boolean applyDirectDamage(Player attacker, LivingEntity victim, double damage) {
        if (DeployService.isDeployEntity(victim) || isTutorialProtected(victim)) {
            return false;
        }

        if (attacker != null && victim instanceof Player) {
            return false;
        }

        if (mobService != null && mobService.applyDamage(victim, damage, attacker)) {
            return true;
        }

        if (victim instanceof Player player) {
            applyPlayerDamage(player, profileManager.getOrCreate(player), damage);
            return true;
        }

        victim.damage(damage);
        return true;
    }

    public boolean applyDirectDamage(Player attacker, LivingEntity victim, double damage, RpgItemDefinition weapon, boolean critical) {
        if (isTutorialProtected(victim) && !tutorialHook.isOwnTarget(attacker, victim)) {
            return false;
        }
        if (tutorialHook != null && tutorialHook.isActive(attacker)) {
            if (tutorialHook.isNpc(victim)) {
                tutorialHook.onNpcHit(attacker);
                return false;
            }
            if (tutorialHook.isRat(victim)) {
                attackEffects.playDamageNumber(attacker, victim.getLocation().clone(), victim.getHeight(), weapon, TUTORIAL_DAMAGE_CAP, false);
                tutorialHook.onRatHit(attacker, victim);
                return true;
            }
            damage = Math.min(damage, TUTORIAL_DAMAGE_CAP);
        }
        Location numberLocation = victim.getLocation().clone();
        double numberHeight = victim.getHeight();
        double finalDamage = modifiedCustomDamage(attacker, victim, weapon, damage);
        boolean applied = applyDirectDamage(attacker, victim, finalDamage);
        if (applied && hasRemainingHealth(victim)) {
            attackEffects.playDamageNumber(attacker, numberLocation, numberHeight, weapon, finalDamage, critical);
            statusEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical);
            if (classEffectService != null) {
                classEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical);
            }
        }
        return applied;
    }

    /**
     * An extra hit on top of an attack (class effects): applies the damage and shows its number in the element's
     * color, also when it is the hit that kills (the position is taken before the damage).
     */
    public boolean applyExtraDamage(Player attacker, LivingEntity victim, double damage, net.tkgon.mc.iruuRPG.stat.Element element) {
        Location numberLocation = victim.getLocation().clone();
        double numberHeight = victim.getHeight();
        boolean applied = applyDirectDamage(attacker, victim, damage);
        if (applied) {
            attackEffects.playExtraDamageNumber(numberLocation, numberHeight, element, damage);
        }
        return applied;
    }

    public boolean applyRangeDamage(Player attacker, LivingEntity victim, double damage) {
        if (DeployService.isDeployEntity(victim) || isTutorialProtected(victim)) {
            return false;
        }

        if (victim instanceof Player) {
            return false;
        }

        Vector beforeVelocity = victim.getVelocity().clone();
        boolean applied = applyNoKnockbackDamage(attacker, victim, damage);
        if (applied) {
            attackEffects.playRangeHurt(victim);
            victim.setVelocity(beforeVelocity);
        }
        return applied;
    }

    public boolean applyRangeDamage(Player attacker, LivingEntity victim, double damage, RpgItemDefinition weapon, boolean critical) {
        if (isTutorialProtected(victim) && !tutorialHook.isOwnTarget(attacker, victim)) {
            return false;
        }
        if (tutorialHook != null && tutorialHook.isActive(attacker)) {
            if (tutorialHook.isNpc(victim)) {
                tutorialHook.onNpcHit(attacker);
                return false;
            }
            if (tutorialHook.isRat(victim)) {
                attackEffects.playDamageNumber(attacker, victim.getLocation().clone(), victim.getHeight(), weapon, TUTORIAL_DAMAGE_CAP, false);
                tutorialHook.onRatHit(attacker, victim);
                return true;
            }
            damage = Math.min(damage, TUTORIAL_DAMAGE_CAP);
        }
        Location numberLocation = victim.getLocation().clone();
        double numberHeight = victim.getHeight();
        double finalDamage = modifiedCustomDamage(attacker, victim, weapon, damage);
        boolean applied = applyRangeDamage(attacker, victim, finalDamage);
        if (applied && hasRemainingHealth(victim)) {
            attackEffects.playDamageNumber(attacker, numberLocation, numberHeight, weapon, finalDamage, critical);
            statusEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical);
            if (classEffectService != null) {
                classEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical);
            }
        }
        return applied;
    }

    /** The slash of an attack of the light / heavy chain: a heavy or the finisher is drawn big, the lights as usual. */
    public void playMeleeSlash(Player attacker, RpgItemDefinition weapon, MeleeCombo.Step step) {
        // an assassin with a weapon in each hand: the crossing slashes of the two hands for the first two lights (the
        // third is three cuts in front), two sweeps in opposite directions for the finisher
        boolean dualWield = dualWielding(attacker);
        if (step.finisher() && step.assassin() && dualWield) {
            attackEffects.playDualSweep(attacker, weapon);
            return;
        }
        boolean dualLight = !step.heavy() && !step.finisher() && dualWield && !(step.assassin() && step.stage() == 3);
        // a `combo-style` of the weapon, then of the class, decides first (each part falls back to the next level)
        ComboStyle itemStyle = comboStyleOf("item " + weapon.id(), weapon.comboStyle());
        ComboStyle classStyle = classService == null ? null
                : classService.currentClass(profileManager.getOrCreate(attacker))
                        .map(definition -> comboStyleOf("class " + definition.id(), definition.comboStyle())).orElse(null);
        for (ComboStyle style : new ComboStyle[]{itemStyle, classStyle}) {
            if (dualLight && style == classStyle) break;
            ComboStyle.Stage stage = style == null ? null : style.stageFor(step);
            if (stage == null) continue;
            ComboStyle.Stage chosen = stage;
            net.tkgon.mc.iruuRPG.skill.SkillLook look = style.look() != null ? style.look()
                    : itemStyle != null && itemStyle.look() != null ? itemStyle.look()
                    : classStyle != null ? classStyle.look() : null;
            attackEffects.playStyledSlash(attacker, weapon, chosen, look);
            return;
        }
        if (step.heavy()) {
            attackEffects.playHeavySlash(attacker, weapon);
            return;
        }
        if (step.finisher()) {
            attackEffects.playComboSlash(attacker, weapon, 3);
            return;
        }
        if (dualWielding(attacker)) {
            attackEffects.playDualSlash(attacker, weapon);
            return;
        }
        String classId = classService == null ? "" : classService.currentClass(profileManager.getOrCreate(attacker)).map(definition -> definition.id()).orElse("");
        if (classId.equals("warrior") || classId.equals("assassin")) {
            attackEffects.playComboSlash(attacker, weapon, step.visual());
            return;
        }
        attackEffects.playMeleeSlash(attacker, weapon);
    }

    public void playMeleeSlash(Player attacker, RpgItemDefinition weapon) {
        // an assassin with a melee weapon in each hand: the crossing slashes of the two hands, on every swing
        if (dualWielding(attacker)) {
            attackEffects.playDualSlash(attacker, weapon);
            return;
        }
        // warrior and assassin: a chain of slashes whose direction changes from stage to stage
        String classId = classService == null ? "" : classService.currentClass(profileManager.getOrCreate(attacker)).map(definition -> definition.id()).orElse("");
        if (classId.equals("warrior") || classId.equals("assassin")) {
            attackEffects.playComboSlash(attacker, weapon);
            return;
        }
        attackEffects.playMeleeSlash(attacker, weapon);
    }

    /** True for an assassin (固有0) who holds a usable melee weapon in the off hand. */
    public boolean dualWielding(Player attacker) {
        if (!hasSkill(attacker, ASSASSIN_DUAL_SKILL)) return false;
        RpgItemDefinition offWeapon = itemIdentifier.itemId(attacker.getInventory().getItemInOffHand()).flatMap(itemRegistry::find).orElse(null);
        return offWeapon != null && offWeapon.isWeaponLike() && offWeapon.attackType() == AttackType.MELEE && canUseItem(attacker, offWeapon);
    }

    public void playRangeTrail(Player attacker, RpgItemDefinition weapon, Location hitLocation) {
        attackEffects.playRangeTrail(attacker, weapon, hitLocation);
    }

    public void playRangeImpact(Player attacker, Location hitLocation, RpgItemDefinition weapon) {
        attackEffects.playRangeImpact(attacker, hitLocation, weapon);
    }

    /**
     * A melee swing that hit nothing directly still splashes: every enemy within the melee area, around a point just
     * in front of the player, takes the area share of the damage a direct hit on it would do (its own defense
     * counts). A direct hit never comes through here (it has its own area damage), so nothing is counted twice.
     */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon) {
        applySwingAreaDamage(attacker, weapon, null);
    }

    /** {@code profile} (null: the live one) is the attacker's profile with {@code weapon} in hand, for an off-hand swing. */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon, PlayerProfile profile) {
        applySwingAreaDamage(attacker, weapon, profile, 1.0);
    }

    /** {@code multiplier}: the damage multiplier of the attack in the light / heavy chain. */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon, PlayerProfile profile, double multiplier) {
        applySwingAreaDamage(attacker, weapon, profile, multiplier, 1.0);
    }

    /** {@code radiusScale}: how much wider the splash is (the assassin's finisher). */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon, PlayerProfile profile, double multiplier, double radiusScale) {
        boolean baseMelee = !hasMeleeAreaSkill(attacker);
        double rate = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-damage-rate", 0.40)
                : areaDamageRate(AttackType.MELEE, false);
        double radius = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-radius", 2.0)
                : plugin.getConfig().getDouble("combat.area-radius", 3.0);
        if (rate <= 0.0 || radius <= 0.0) return;
        radius *= Math.max(0.1, radiusScale);

        double reach = plugin.getConfig().getDouble("combat.melee-swing-area-reach", 1.8);
        Location center = attacker.getEyeLocation().add(attacker.getEyeLocation().getDirection().normalize().multiply(reach));
        PlayerProfile attackerProfile = profile != null ? profile : equipmentService.recalculate(attacker);
        for (Entity nearby : attacker.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target)) continue;
            if (DeployService.isDeployEntity(target) || target instanceof Player || target.equals(attacker) || target.isInvulnerable()) continue;
            if (target.getLocation().add(0, target.getHeight() / 2.0, 0).distanceSquared(center) > radius * radius) continue;

            AttackDamage attack = calculateDamage(attackerProfile, target, weapon);
            double areaDamage = round(attack.result().damage() * rate * multiplier);
            if (areaDamage <= 0.0) continue;
            if (applyDirectDamage(attacker, target, areaDamage, weapon, false)) {
                playMeleeImpact(attacker, target);
                sendDamageDebug(attacker, AttackType.MELEE, target, areaDamage, false, true);
            }
        }
    }

    public void applyAreaDamage(Player attacker, LivingEntity mainVictim, double sourceDamage, AttackType attackType) {
        applyAreaDamage(attacker, mainVictim, sourceDamage, attackType, null);
    }

    public void applyAreaDamage(Player attacker, LivingEntity mainVictim, double sourceDamage, AttackType attackType, RpgItemDefinition weapon) {
        applyAreaDamage(attacker, mainVictim, sourceDamage, attackType, weapon, 1.0);
    }

    /** {@code radiusScale}: how much wider the splash is (the assassin's finisher). */
    public void applyAreaDamage(Player attacker, LivingEntity mainVictim, double sourceDamage, AttackType attackType, RpgItemDefinition weapon, double radiusScale) {
        if (sourceDamage <= 0.0) return;

        // melee: everyone has a small area (a weaker copy of the warrior's); the warrior's milestone makes it full size
        boolean baseMelee = attackType == AttackType.MELEE && !hasMeleeAreaSkill(attacker);
        boolean expanded = attackType == AttackType.RANGE && hasSkill(attacker, RANGE_AREA_EXPAND_SKILL);
        double damageRate = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-damage-rate", 0.40)
                : areaDamageRate(attackType, expanded);
        if (damageRate <= 0.0) return;

        double radius = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-radius", 2.0)
                : expanded
                ? plugin.getConfig().getDouble("combat.range-area-expanded-radius", 5.0)
                : plugin.getConfig().getDouble("combat.area-radius", 3.0);
        radius *= Math.max(0.1, radiusScale);
        double radiusSquared = radius * radius;
        double areaDamage = round(sourceDamage * damageRate);
        if (areaDamage <= 0.0) return;

        for (Entity nearby : mainVictim.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target)) continue;
            if (DeployService.isDeployEntity(target)) continue;
            if (target instanceof Player) continue;
            if (target.equals(mainVictim) || target.equals(attacker)) continue;
            if (target.getLocation().distanceSquared(mainVictim.getLocation()) > radiusSquared) continue;
            if (!isInFront(attacker, target)) continue;

            boolean applied = attackType == AttackType.RANGE
                    ? applyRangeDamage(attacker, target, areaDamage, weapon, false)
                    : applyDirectDamage(attacker, target, areaDamage, weapon, false);
            if (applied) {
                if (attackType == AttackType.MELEE) {
                    playMeleeImpact(attacker, target);
                }
                sendDamageDebug(attacker, attackType, target, areaDamage, false, true);
            }
        }
    }

    /**
     * The hurt effect of a melee hit. A direct hit does not knock the target back; set
     * {@code combat.melee-area-knockback-horizontal / -vertical} above 0 to bring a push back.
     */
    public void playMeleeImpact(Player attacker, LivingEntity target) {
        if (target == null || target.isDead() || !target.isValid()) return;

        attackEffects.playRangeHurt(target);
        double horizontal = plugin.getConfig().getDouble("combat.melee-area-knockback-horizontal", 0.0);
        double vertical = plugin.getConfig().getDouble("combat.melee-area-knockback-vertical", 0.0);
        if (horizontal <= 0.0 && vertical <= 0.0) return;

        Vector direction = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() <= 1.0E-9) {
            direction = attacker.getLocation().getDirection();
            direction.setY(0.0);
        }
        if (direction.lengthSquared() <= 1.0E-9) {
            direction = new Vector(0.0, 0.0, 1.0);
        } else {
            direction.normalize();
        }
        target.setVelocity(direction.multiply(horizontal).setY(vertical));
    }

    public void sendDamageDebug(Player attacker, AttackType attackType, LivingEntity victim, double damage, boolean critical, boolean area) {
        if (!plugin.getConfig().getBoolean("combat.debug-damage-in-creative", true)) return;
        if (attacker.getGameMode() != GameMode.CREATIVE) return;

        String label = area ? "範囲" : "直撃";
        String criticalSuffix = critical ? " 会心" : "";
        attacker.sendMessage("[iruuRPG] " + attackType.displayName()
                + " " + label
                + " ダメージ: " + format(damage)
                + criticalSuffix
                + " -> " + victim.getName());
    }

    public void sendMissDebug(Player attacker, AttackType attackType) {
        if (!plugin.getConfig().getBoolean("combat.debug-damage-in-creative", true)) return;
        if (attacker.getGameMode() != GameMode.CREATIVE) return;

        attacker.sendMessage("[iruuRPG] " + attackType.displayName() + " は外れた。");
    }

    public void applyPlayerDamage(Player player, PlayerProfile profile, double damage) {
        double finalDamage = statusEffectService.modifyIncomingDamage(player, damage);
        profile.setCurrentHp(profile.currentHp() - finalDamage);

        if (profile.currentHp() <= 0.0 && !player.isDead()) {
            playerBars.kill(player, profile);
            return;
        }

        playerBars.sync(player, profile);
    }

    public boolean healPlayer(Player player, double amount) {
        if (amount <= 0.0 || player.isDead()) return false;

        PlayerProfile profile = equipmentService.recalculate(player);
        double before = profile.currentHp();
        profile.setCurrentHp(before + amount);
        double healed = profile.currentHp() - before;
        // Class effects that scale with healing (the healer's pulse) count the whole heal, overheal included.
        if (Math.abs(healed) <= 1.0E-9) {
            if (classEffectService != null) {
                classEffectService.onSelfHealed(player, amount);
            }
            return false;
        }

        playerBars.sync(player, profile);
        attackEffects.playHealNumber(player, healed);
        if (classEffectService != null) {
            classEffectService.onSelfHealed(player, amount);
        }
        return true;
    }

    /** A heal cast by {@code caster}: class bonuses to the caster's heals (e.g. the healer's protection) apply. */
    public boolean healPlayerBy(Player caster, Player target, double amount) {
        double multiplier = classEffectService == null ? 1.0 : classEffectService.healMultiplier(caster);
        return healPlayer(target, amount * multiplier);
    }

    /** The warrior's full melee area (milestone "melee_area"); everyone else has the smaller base area. */
    private boolean hasMeleeAreaSkill(Player attacker) {
        return hasSkill(attacker, MELEE_AREA_SKILL);
    }

    /** True when this player's class has the skill flag {@code skillName} (an innate or unlocked passive). */
    public boolean hasClassSkill(Player player, String skillName) {
        return hasSkill(player, skillName);
    }

    // ---- the assassin's two hands ----------------------------------------------------------------------------------

    private static final String ASSASSIN_DUAL_SKILL = "assassin_dual";
    private static final String ASSASSIN_DOUBLE_SKILL = "assassin_double";

    /**
     * The hand a melee attack is made with. {@code profile} is the attacker's profile as if that weapon were in the
     * main hand (null: the live profile). {@code other*} is the weapon of the other hand, for a double strike.
     */
    public record MeleeHand(RpgItemDefinition weapon, PlayerProfile profile, boolean offHand,
                            RpgItemDefinition otherWeapon, PlayerProfile otherProfile, boolean doubleStrike) {
    }

    private final java.util.Map<UUID, Boolean> nextOffHand = new java.util.HashMap<>();

    /**
     * Who hits this time. An assassin with 固有0 who holds a melee weapon in each hand hits with the main hand, then
     * the off hand, then the main hand again ... (the hotbar does not change); with 固有1 there is a 10% chance
     * (class-effects.assassin.double-chance) that both hands hit at once. Anybody else: the main-hand weapon.
     */
    public MeleeHand nextMeleeHand(Player attacker, RpgItemDefinition mainWeapon) {
        MeleeHand plain = new MeleeHand(mainWeapon, null, false, null, null, false);
        if (!hasSkill(attacker, ASSASSIN_DUAL_SKILL)) return plain;

        ItemStack offItem = attacker.getInventory().getItemInOffHand();
        RpgItemDefinition offWeapon = itemIdentifier.itemId(offItem).flatMap(itemRegistry::find).orElse(null);
        if (offWeapon == null || !offWeapon.isWeaponLike() || offWeapon.attackType() != AttackType.MELEE
                || !canUseItem(attacker, offWeapon)) {
            nextOffHand.remove(attacker.getUniqueId());
            return plain;
        }

        boolean useOff = nextOffHand.getOrDefault(attacker.getUniqueId(), false);
        nextOffHand.put(attacker.getUniqueId(), !useOff);
        boolean both = hasSkill(attacker, ASSASSIN_DOUBLE_SKILL)
                && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("class-effects.assassin.double-chance", 0.10);
        PlayerProfile offProfile = equipmentService.profileHolding(attacker, offItem);
        return useOff
                ? new MeleeHand(offWeapon, offProfile, true, mainWeapon, null, both)
                : new MeleeHand(mainWeapon, null, false, offWeapon, offProfile, both);
    }

    /** Damage of a melee hit made with {@code hand}. */
    public AttackDamage calculateMeleeDamage(Player attacker, LivingEntity victim, MeleeHand hand) {
        PlayerProfile profile = hand.profile() != null ? hand.profile() : equipmentService.recalculate(attacker);
        return calculateDamage(profile, victim, hand.weapon());
    }

    /**
     * The other hand's hit of a double strike: lands at the same time as the first, shown as two slashes crossing.
     * (It is an ordinary melee hit with the other weapon; the area splash and the status effects work as usual.)
     */
    public void doubleStrikeHit(Player attacker, LivingEntity victim, MeleeHand hand) {
        if (hand.otherWeapon() == null || victim.isDead() || !victim.isValid()) return;
        PlayerProfile profile = hand.otherProfile() != null ? hand.otherProfile() : equipmentService.recalculate(attacker);
        AttackDamage attack = calculateDamage(profile, victim, hand.otherWeapon());
        double damage = round(attack.result().damage() * tripleScale(attacker));
        boolean critical = attack.result().critical();
        if (!applyDirectDamage(attacker, victim, damage, hand.otherWeapon(), critical)) return;
        sendDamageDebug(attacker, AttackType.MELEE, victim, damage, critical, false);
        applyAreaDamage(attacker, victim, damage, AttackType.MELEE, hand.otherWeapon());
    }

    // ---- the ranger's enhanced state: every normal attack is three hits of 70% ---------------------------------------

    private static final double TRIPLE_RATE = 0.7;
    private static final long TRIPLE_GAP_TICKS = 3L;

    /** True while the ranger's 本能開放 is on: normal attacks (and deployed weapons) hit three times for 70%. */
    public boolean tripleActive(Player player) {
        return classEffectService != null && classEffectService.instinctActive(player.getUniqueId());
    }

    /** What the first hit of a normal attack is multiplied by (0.7 in the enhanced state, else 1). */
    public double tripleScale(Player player) {
        return tripleActive(player) ? TRIPLE_RATE : 1.0;
    }

    /** The two further 70% hits of an enhanced normal attack, a few ticks apart (the first one is the normal hit). */
    public void scheduleTripleHits(Player attacker, LivingEntity victim, RpgItemDefinition weapon, AttackType type) {
        for (int index = 1; index <= 2; index++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!attacker.isOnline() || attacker.isDead() || victim.isDead() || !victim.isValid()) return;
                AttackDamage attack = calculateDamage(attacker, victim, weapon);
                double damage = round(attack.result().damage() * TRIPLE_RATE);
                boolean critical = attack.result().critical();
                boolean applied = type == AttackType.RANGE
                        ? applyRangeDamage(attacker, victim, damage, weapon, critical)
                        : applyDirectDamage(attacker, victim, damage, weapon, critical);
                if (!applied) return;
                if (type == AttackType.MELEE) {
                    playMeleeImpact(attacker, victim);
                    playMeleeSlash(attacker, weapon);
                }
                sendDamageDebug(attacker, type, victim, damage, critical, false);
            }, TRIPLE_GAP_TICKS * index);
        }
    }

    private boolean hasSkill(Player attacker, String skillName) {
        if (classService == null) return false;
        return classService.hasSkill(profileManager.getOrCreate(attacker), skillName);
    }

    public void setClassEffectService(ClassEffectService classEffectService) {
        this.classEffectService = classEffectService;
    }

    public void setClassService(ClassService classService) {
        this.classService = classService;
    }

    private double areaDamageRate(AttackType attackType, boolean expanded) {
        return switch (attackType) {
            case MELEE -> plugin.getConfig().getDouble("combat.melee-area-damage-rate", 0.65);
            case RANGE -> expanded
                    ? plugin.getConfig().getDouble("combat.range-area-expanded-damage-rate", 0.60)
                    : plugin.getConfig().getDouble("combat.range-area-damage-rate", 0.45);
            case DEPLOY, SPECIAL -> 0.0;
        };
    }

    private RpgItemDefinition fallbackMobWeapon() {
        return new RpgItemDefinition(
                "__mob_attack__",
                Material.STONE_SWORD,
                ItemVisualOptions.NONE,
                "Mob Attack",
                0,
                Rarity.COMMON,
                Element.WHITE,
                DamageKind.PHYSICAL,
                AttackType.MELEE,
                ItemSkillType.NONE,
                "",
                java.util.Map.of(),
                null,
                new StatSet(),
                new ElementStatSet(),
                List.of()
        );
    }

    private double scaleIncomingRpgMobDamage(double damage, PlayerProfile victimProfile) {
        if (damage <= 0.0) return 0.0;

        double multiplier = Math.max(0.0, plugin.getConfig().getDouble("mob.player-damage-multiplier", 0.10));
        return round(damage * multiplier);
    }

    private static final String ATTACK_SPEED_COOLDOWN_SKILL = "attack_speed_cooldown";

    /**
     * What a skill's cooldown is multiplied by for this player: with the mage's "attack_speed_cooldown" node it is
     * 1 - attack speed % (never below 0.2); for everybody else 1 (attack speed does nothing to skill cooldowns).
     */
    public double skillCooldownMultiplier(Player player) {
        if (!hasSkill(player, ATTACK_SPEED_COOLDOWN_SKILL)) return 1.0;
        double attackSpeed = equipmentService.recalculate(player).finalStats().get(StatType.ATTACK_SPEED);
        return Math.max(0.2, Math.min(1.0, 1.0 - attackSpeed / 100.0));
    }

    private int effectiveCooldownTicks(Player player, AttackType attackType) {
        int baseTicks = attackCooldowns.cooldownTicks(attackType);
        if (baseTicks <= 0 || attackType == AttackType.SPECIAL) return baseTicks;
        if (attackType == AttackType.DEPLOY) return baseTicks;

        PlayerProfile profile = equipmentService.recalculate(player);
        double attackSpeed = profile.finalStats().get(StatType.ATTACK_SPEED);
        double multiplier = Math.max(0.1, 1.0 + attackSpeed / 100.0);
        int minTicks = Math.max(1, plugin.getConfig().getInt("combat.min-cooldown-ticks", 1));
        return Math.max(minTicks, (int) Math.round(baseTicks / multiplier));
    }

    public void showItemCooldown(Player player, AttackType attackType, int ticks) {
        if (attackType == AttackType.SPECIAL) return;

        if (ticks <= 0) return;

        for (Material material : cooldownMaterials(attackType)) {
            player.setCooldown(material, ticks);
        }
    }

    private Set<Material> cooldownMaterials(AttackType attackType) {
        Set<Material> materials = new HashSet<>();
        for (RpgItemDefinition definition : itemRegistry.all().values()) {
            if (!definition.isWeaponLike()) continue;
            if (definition.attackType() != attackType) continue;
            materials.add(definition.material());
        }
        return materials;
    }

    private boolean isInFront(Player attacker, LivingEntity target) {
        if (!plugin.getConfig().getBoolean("combat.area-requires-facing", true)) {
            return true;
        }

        Vector toTarget = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (toTarget.lengthSquared() <= 1.0E-9) return false;
        return attacker.getLocation().getDirection().dot(toTarget.normalize()) >= 0.0;
    }

    /**
     * What a ranged shot can hit. Players (a shot can never hurt one), armor stands and the tutorial's NPC / rat of
     * someone else are left out of the trace, so they do not block a shot at a mob behind them.
     */
    public boolean isAttackTarget(Entity entity, Player attacker) {
        return entity instanceof LivingEntity living
                && !entity.equals(attacker)
                && !entity.isDead()
                && !(entity instanceof Player)
                && !(entity instanceof ArmorStand)
                && !DeployService.isDeployEntity(entity)
                && !(isTutorialProtected(living) && !tutorialHook.isOwnTarget(attacker, living));
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : Math.max(1.0, entity.getHealth());
    }

    private boolean applyNoKnockbackDamage(Player attacker, LivingEntity victim, double damage) {
        if (DeployService.isDeployEntity(victim)) {
            return false;
        }

        if (mobService != null && mobService.applyDamage(victim, damage, attacker)) {
            return true;
        }

        if (victim.isDead() || !victim.isValid()) return false;

        double nextHealth = victim.getHealth() - Math.max(0.0, damage);
        if (nextHealth <= 0.0) {
            victim.setHealth(0.0);
            return true;
        }

        victim.setHealth(Math.min(nextHealth, maxHealth(victim)));
        updateDebugTargetName(victim);
        return true;
    }

    private boolean hasRemainingHealth(LivingEntity victim) {
        return victim.isValid() && !victim.isDead() && victim.getHealth() > 0.0;
    }

    private double modifiedCustomDamage(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double damage) {
        double modified = statusEffectService.modifyIncomingDamage(victim, damage);
        return statusEffectService.modifyOutgoingDamage(attacker, weapon, modified);
    }

    private void updateDebugTargetName(LivingEntity victim) {
        if (debugTargetService == null) return;
        if (!debugTargetService.isDebugTarget(victim)) return;
        if (!victim.isValid() || victim.isDead()) return;

        debugTargetService.updateName(victim);
    }

    private double round(double value) {
        return Math.floor(value * 10.0 + 0.5) / 10.0;
    }

    private String format(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.abs(value - Math.rint(value)) < 1.0E-9 ? 0 : 1);
        return format.format(value);
    }

    public record AttackDamage(DamageResult result, PlayerProfile victimProfile) {
    }

    public record RangeTrace(LivingEntity victim, Location hitLocation) {
    }
}
