package net.tkgon.mc.iruuRPG.tutorial;

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Enforces the tutorial locks (view, movement, attacks) and cleans up when the player leaves or dies.
 * It must be registered before the combat listeners so its cancellations come first.
 */
public final class TutorialListener implements Listener {

    private final TutorialService service;

    public TutorialListener(TutorialService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        TutorialSession session = service.session(event.getPlayer());
        if (session == null || !session.moveLocked) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        boolean turned = session.lookLocked && (from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch());
        if (!moved && !turned) return;

        Location fixed = from.clone();
        if (session.lookLocked) {
            fixed.setYaw(session.lockYaw);
            fixed.setPitch(session.lockPitch);
        } else {
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
        }
        event.setTo(fixed);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        // the NPC must never open the villager trade menu
        if (service.isTutorialEntity(event.getRightClicked()) || locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null) return;

        TutorialSession session = service.session(attacker);
        if (session == null) return;

        if (session.npc != null && session.npc.getUniqueId().equals(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            service.onNpcHit(session);
            return;
        }
        if (session.attackLocked) {
            event.setCancelled(true);
        }
    }

    /**
     * Melee swings are resolved on this event (the damage event never fires for custom weapons), so the NPC
     * reaction and the attack lock have to be handled here. Registered first, so combat never sees a cancelled swing.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        TutorialSession session = service.session(event.getPlayer());
        if (session == null) return;

        if (session.npc != null && session.npc.getUniqueId().equals(event.getAttacked().getUniqueId())) {
            event.setCancelled(true);
            service.onNpcHit(session);
            return;
        }
        if (session.attackLocked) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTutorialEntityDamage(EntityDamageEvent event) {
        // the NPC and the rat are invincible: only the tutorial itself ever removes them
        if (service.isTutorialEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // Spectator keeps the player still and out of everything until the class menu: no possessing, no teleport menu.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onStartSpectating(PlayerStartSpectatingEntityEvent event) {
        if (service.isRunning(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        if (service.isRunning(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE
                && event.getPlayer().getGameMode() == GameMode.SPECTATOR
                && service.isRunning(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeld(PlayerItemHeldEvent event) {
        if (locked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            service.onClassMenuClosed(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (service.isRunning(event.getPlayer())) {
            service.stop(event.getPlayer(), null);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (service.isRunning(event.getEntity())) {
            service.stop(event.getEntity(), "チュートリアル中に倒れたので、中断しました。");
        }
    }

    private boolean locked(Player player) {
        TutorialSession session = service.session(player);
        return session != null && session.attackLocked;
    }

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
