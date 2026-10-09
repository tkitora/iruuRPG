package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.gui.MainMenu;
import net.tkgon.mc.iruuRPG.gui.MainMenuItemService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class MainMenuItemListener implements Listener {

    private final JavaPlugin plugin;
    private final MainMenu mainMenu;
    private final MainMenuItemService menuItemService;

    public MainMenuItemListener(JavaPlugin plugin, MainMenu mainMenu, MainMenuItemService menuItemService) {
        this.plugin = plugin;
        this.mainMenu = mainMenu;
        this.menuItemService = menuItemService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleEnsure(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleEnsure(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!menuItemService.isMenuItem(event.getItem())) return;

        event.setCancelled(true);
        mainMenu.open(event.getPlayer());
        scheduleEnsure(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (isProtectedMenuClick(event)) {
            event.setCancelled(true);
            scheduleEnsure(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (menuItemService.isMenuItem(event.getOldCursor()) || touchesMenuSlot(event)) {
            event.setCancelled(true);
            scheduleEnsure(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!menuItemService.isMenuItem(event.getItemDrop().getItemStack())) return;

        event.setCancelled(true);
        scheduleEnsure(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!menuItemService.isMenuItem(event.getMainHandItem()) && !menuItemService.isMenuItem(event.getOffHandItem())) {
            return;
        }

        event.setCancelled(true);
        scheduleEnsure(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(menuItemService::isMenuItem);
    }

    private boolean isProtectedMenuClick(InventoryClickEvent event) {
        if (menuItemService.isMenuItem(event.getCurrentItem()) || menuItemService.isMenuItem(event.getCursor())) {
            return true;
        }
        if (event.getHotbarButton() == MainMenuItemService.MENU_SLOT) {
            return true;
        }

        return event.getClickedInventory() instanceof PlayerInventory
                && event.getSlot() == MainMenuItemService.MENU_SLOT;
    }

    private boolean touchesMenuSlot(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) continue;
            if (event.getView().convertSlot(rawSlot) == MainMenuItemService.MENU_SLOT) {
                return true;
            }
        }
        return false;
    }

    private void scheduleEnsure(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                menuItemService.ensure(player);
            }
        });
    }
}
