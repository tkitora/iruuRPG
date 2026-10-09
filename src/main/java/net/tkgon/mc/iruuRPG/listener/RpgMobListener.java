package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

public final class RpgMobListener implements Listener {

    private final JavaPlugin plugin;
    private final RpgMobService mobService;

    public RpgMobListener(JavaPlugin plugin, RpgMobService mobService) {
        this.plugin = plugin;
        this.mobService = mobService;
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (!mobService.isFireProof(event.getEntity())) return;

        event.setCancelled(true);
        event.getEntity().setFireTicks(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!mobService.isRpgMob(entity)) return;

        if (isBlockedVanillaDebuff(event.getCause())) {
            event.setCancelled(true);
            event.setDamage(0.0);
            clearBlockedVanillaDebuffs(entity);
            return;
        }

        double damage = Math.max(0.0, event.getFinalDamage());
        event.setCancelled(true);
        event.setDamage(0.0);

        if (damage <= 0.0) return;
        mobService.applyDamage(entity, damage, damageSourcePlayer(event));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item item = event.getItem();
        if (!mobService.isRewardForOtherPlayer(item, player)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!mobService.isRpgMob(entity)) return;
        if (event.getTarget() instanceof Player player && mobService.isCurrentTarget(entity, player)) return;

        event.setCancelled(true);
        event.setTarget(null);

        Bukkit.getScheduler().runTask(plugin, () -> mobService.retargetToNearestPlayer(entity));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (mobService.isRpgMob(event.getEntity())) {
            event.getDrops().clear();
            mobService.dropRewards(event.getEntity());
        }
        mobService.remove(event.getEntity());
    }

    private Player damageSourcePlayer(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return null;

        Entity damager = byEntity.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isBlockedVanillaDebuff(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.POISON
                || cause == EntityDamageEvent.DamageCause.WITHER;
    }

    private void clearBlockedVanillaDebuffs(LivingEntity entity) {
        entity.removePotionEffect(PotionEffectType.POISON);
        entity.removePotionEffect(PotionEffectType.WITHER);
    }
}
