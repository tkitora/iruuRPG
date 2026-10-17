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
        return OptionalDouble.of(scaleIncomingRpgMobDamage(Math.max(0.0, result.damage()), victimProfile));
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

    public RangeTrace traceRange(Player attacker) {
        double range = plugin.getConfig().getDouble("combat.range-distance", 16.0);
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
                attackEffects.playDamageNumber(victim.getLocation().clone(), victim.getHeight(), weapon, TUTORIAL_DAMAGE_CAP, false);
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
            attackEffects.playDamageNumber(numberLocation, numberHeight, weapon, finalDamage, critical);
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
                attackEffects.playDamageNumber(victim.getLocation().clone(), victim.getHeight(), weapon, TUTORIAL_DAMAGE_CAP, false);
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
            attackEffects.playDamageNumber(numberLocation, numberHeight, weapon, finalDamage, critical);
            statusEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical);
            if (classEffectService != null) {
                classEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical);
            }
        }
        return applied;
    }

    public void playMeleeSlash(Player attacker, RpgItemDefinition weapon) {
        attackEffects.playMeleeSlash(attacker, weapon);
    }

    public void playRangeTrail(Player attacker, RpgItemDefinition weapon, Location hitLocation) {
        attackEffects.playRangeTrail(attacker, weapon, hitLocation);
    }

    public void playRangeImpact(Location hitLocation, RpgItemDefinition weapon) {
        attackEffects.playRangeImpact(hitLocation, weapon);
    }

    /**
     * A melee swing that hit nothing directly still splashes: every enemy within the melee area, around a point just
     * in front of the player, takes the area share of the damage a direct hit on it would do (its own defense
     * counts). A direct hit never comes through here (it has its own area damage), so nothing is counted twice.
     */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon) {
        boolean baseMelee = !hasMeleeAreaSkill(attacker);
        double rate = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-damage-rate", 0.40)
                : areaDamageRate(AttackType.MELEE, false);
        double radius = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-radius", 2.0)
                : plugin.getConfig().getDouble("combat.area-radius", 3.0);
        if (rate <= 0.0 || radius <= 0.0) return;

        double reach = plugin.getConfig().getDouble("combat.melee-swing-area-reach", 1.8);
        Location center = attacker.getEyeLocation().add(attacker.getEyeLocation().getDirection().normalize().multiply(reach));
        PlayerProfile attackerProfile = equipmentService.recalculate(attacker);
        for (Entity nearby : attacker.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target)) continue;
            if (DeployService.isDeployEntity(target) || target instanceof Player || target.equals(attacker) || target.isInvulnerable()) continue;
            if (target.getLocation().add(0, target.getHeight() / 2.0, 0).distanceSquared(center) > radius * radius) continue;

            AttackDamage attack = calculateDamage(attackerProfile, target, weapon);
            double areaDamage = round(attack.result().damage() * rate);
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
            player.setHealth(0.0);
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
