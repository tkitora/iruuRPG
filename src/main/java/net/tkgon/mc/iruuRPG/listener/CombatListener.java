package net.tkgon.mc.iruuRPG.listener;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.tkgon.mc.iruuRPG.combat.AttackService;
import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.combat.DeployService;
import net.tkgon.mc.iruuRPG.combat.ItemSkillService;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;

import java.util.OptionalDouble;

public final class CombatListener implements Listener {

    private final AttackService attackService;
    private final DeployService deployService;
    private final ItemSkillService itemSkillService;

    public CombatListener(AttackService attackService, DeployService deployService, ItemSkillService itemSkillService) {
        this.attackService = attackService;
        this.deployService = deployService;
        this.itemSkillService = itemSkillService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeployDamage(EntityDamageEvent event) {
        if (!DeployService.isDeployEntity(event.getEntity())) return;

        event.setCancelled(true);
        event.setDamage(0.0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreCustomRpgAttack(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack()) return;
        if (DeployService.isDeployEntity(event.getAttacked())) {
            event.setCancelled(true);
            return;
        }

        Player attacker = event.getPlayer();
        RpgItemDefinition weapon = attackService.weaponInMainHand(attacker);
        if (weapon == null || !weapon.isWeaponLike()) return;

        event.setCancelled(true);
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }
        if (!(event.getAttacked() instanceof LivingEntity victim)) return;

        handleCustomMelee(attacker, victim, weapon);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVanillaPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isBlockedVanillaDebuff(event.getCause())) {
            event.setCancelled(true);
            event.setDamage(0.0);
            clearBlockedVanillaDebuffs(player);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (attackService.isPlayerVersusPlayer(byEntity.getDamager(), byEntity.getEntity())) {
                event.setCancelled(true);
                event.setDamage(0.0);
                return;
            }
            if (attackService.isCustomWeaponAttack(byEntity.getDamager())) return;
        }

        OptionalDouble rpgMobDamage = attackService.incomingRpgMobDamage(event, player);
        double damage = rpgMobDamage.orElse(Math.max(0.0, event.getFinalDamage()));
        if (damage <= 0.0) {
            if (rpgMobDamage.isPresent()) {
                event.setDamage(0.0);
            }
            return;
        }

        event.setDamage(0.0);
        attackService.applyPlayerDamage(player, attackService.profileForPlayer(player), damage);
        if (rpgMobDamage.isPresent()) {
            attackService.applyRpgMobStatusEffects(event, player, damage);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedVanillaDebuff(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (event.getNewEffect() == null) return;
        if (!isBlockedVanillaDebuff(event.getNewEffect().getType())) return;

        event.setCancelled(true);
        clearBlockedVanillaDebuffs(entity);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCustomRpgAttack(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            event.setDamage(0.0);
            return;
        }

        Player attacker = attackService.resolvePlayerAttacker(event.getDamager());
        if (attacker == null) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (DeployService.isDeployEntity(victim)) {
            event.setCancelled(true);
            event.setDamage(0.0);
            return;
        }

        RpgItemDefinition weapon = attackService.weaponInMainHand(attacker);
        if (weapon == null || !weapon.isWeaponLike()) return;

        event.setCancelled(true);
        event.setDamage(0.0);
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }
        handleCustomMelee(attacker, victim, weapon);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdventureRightClickBlock(PlayerInteractEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.ADVENTURE) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (itemSkillService.handleManualUse(player, weapon)) {
            event.setCancelled(true);
            return;
        }
        if (isRangedRightClickWeapon(weapon)) {
            event.setCancelled(true);
            handleRpgRightClick(player, weapon);
            return;
        }

        if (player.getGameMode() != GameMode.ADVENTURE) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDamage(PlayerItemDamageEvent event) {
        event.setDamage(0);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMeleeSwing(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player attacker = event.getPlayer();
        RpgItemDefinition weapon = attackService.weaponInMainHand(attacker);
        if (weapon == null || !weapon.isWeaponLike()) return;
        if (weapon.attackType() != AttackType.MELEE) return;

        event.setCancelled(true);
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }
        if (!attackService.tryStart(attacker, AttackType.MELEE)) return;

        attackService.playMeleeSlash(attacker, weapon);
    }

    private void handleCustomMelee(Player attacker, LivingEntity victim, RpgItemDefinition weapon) {
        if (victim instanceof Player) return;
        if (DeployService.isDeployEntity(victim)) return;
        if (weapon.attackType() != AttackType.MELEE) return;
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }
        if (!attackService.tryStart(attacker, AttackType.MELEE)) return;

        AttackService.AttackDamage attack = attackService.calculateDamage(attacker, victim, weapon);
        if (!attackService.applyDirectDamage(attacker, victim, attack.result().damage(), weapon, attack.result().critical())) {
            return;
        }

        attackService.playMeleeImpact(attacker, victim);
        attackService.playMeleeSlash(attacker, weapon);
        attackService.sendDamageDebug(attacker, AttackType.MELEE, victim, attack.result().damage(), attack.result().critical(), false);
        attackService.applyAreaDamage(attacker, victim, attack.result().damage(), AttackType.MELEE, weapon);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRangeUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player attacker = event.getPlayer();
        RpgItemDefinition weapon = attackService.weaponInMainHand(attacker);
        if (itemSkillService.handleManualUse(attacker, weapon)) {
            event.setCancelled(true);
            return;
        }
        if (!isRangedRightClickWeapon(weapon)) return;

        event.setCancelled(true);
        handleRpgRightClick(attacker, weapon);
    }

    private boolean isRangedRightClickWeapon(RpgItemDefinition weapon) {
        return weapon != null
                && weapon.isWeaponLike()
                && (weapon.attackType() == AttackType.RANGE || weapon.attackType() == AttackType.DEPLOY);
    }

    private boolean isBlockedVanillaDebuff(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.POISON
                || cause == EntityDamageEvent.DamageCause.WITHER;
    }

    private boolean isBlockedVanillaDebuff(PotionEffectType type) {
        return type == PotionEffectType.POISON
                || type == PotionEffectType.WITHER;
    }

    private void clearBlockedVanillaDebuffs(LivingEntity entity) {
        entity.removePotionEffect(PotionEffectType.POISON);
        entity.removePotionEffect(PotionEffectType.WITHER);
    }

    private void handleRpgRightClick(Player attacker, RpgItemDefinition weapon) {
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }

        if (weapon.attackType() == AttackType.DEPLOY) {
            if (!attackService.tryStart(attacker, AttackType.DEPLOY)) {
                return;
            }
            deployService.deploy(attacker, weapon);
            return;
        }

        if (!attackService.tryStart(attacker, AttackType.RANGE)) {
            return;
        }

        AttackService.RangeTrace trace = attackService.traceRange(attacker);
        attackService.playRangeTrail(attacker, weapon, trace.hitLocation());

        LivingEntity victim = trace.victim();
        if (victim == null) {
            attackService.sendMissDebug(attacker, AttackType.RANGE);
            return;
        }

        AttackService.AttackDamage attack = attackService.calculateDamage(attacker, victim, weapon);
        if (!attackService.applyRangeDamage(attacker, victim, attack.result().damage(), weapon, attack.result().critical())) {
            return;
        }
        attackService.playRangeImpact(trace.hitLocation(), weapon);
        attackService.sendDamageDebug(attacker, AttackType.RANGE, victim, attack.result().damage(), attack.result().critical(), false);
        attackService.applyAreaDamage(attacker, victim, attack.result().damage(), AttackType.RANGE, weapon);
    }
}
