package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.gui.MainMenu;
import net.tkgon.mc.iruuRPG.gui.MenuHolder;
import net.tkgon.mc.iruuRPG.gui.MainMenuItemService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
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
        // right click or left click with the menu item opens the menu (a left click on a block must not break it)
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        if (!menuItemService.isMenuItem(event.getItem())) return;
        if ((action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
                && (!net.tkgon.mc.iruuRPG.combat.ClickSignals.leftPossible(event.getPlayer()) || net.tkgon.mc.iruuRPG.combat.ClickSignals.echoSuspected(event.getPlayer()))) return;
        // the off hand fires the event again for a right click: only the main hand opens it
        if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

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
            openFromInventory(event, player);
        }
    }

    /**
     * Clicking the menu item inside the inventory screen opens the menu too (like using it from the hotbar), but only
     * with an empty cursor: swapping the inventory screen for the menu while an item is held is what lost and
     * duplicated items, so a held item just stays where it is and the click does nothing.
     */
    private void openFromInventory(InventoryClickEvent event, Player player) {
        if (!menuItemService.isMenuItem(event.getCurrentItem())) return;
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) return;
        if (holdsItem(event.getCursor())) return;
        switch (event.getClick()) {
            case LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, CREATIVE -> openNextTick(player);
            default -> {
            }
        }
    }

    /** True for a real item on the cursor (the menu item itself and an empty cursor do not count). */
    private boolean holdsItem(ItemStack cursor) {
        return cursor != null && !cursor.getType().isAir() && !menuItemService.isMenuItem(cursor);
    }

    private void openNextTick(Player player) {
        // opening on the next tick: the click event itself is still being processed
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (holdsItem(player.getItemOnCursor())) return;

            mainMenu.open(player);
        });
    }

    /**
     * Creative mode: the client takes and places items by itself, so cancelling the click is not enough
     * (the item stays on the cursor and can be put in another slot as a real copy). Every creative action
     * that involves the menu item, or happens inside an iruuRPG menu, is cancelled and the client is resynced.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreativeClick(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean inOurMenu = event.getView().getTopInventory().getHolder() instanceof MenuHolder;
        if (inOurMenu
                || menuItemService.isMenuItem(event.getCursor())
                || menuItemService.isMenuItem(event.getCurrentItem())) {
            event.setCancelled(true);
            scheduleEnsure(player);
            // creative inventories send this event instead of a normal click: clicking the menu item opens the menu
            if (!inOurMenu && menuItemService.isMenuItem(event.getCurrentItem()) && !holdsItem(event.getCursor())) openNextTick(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (menuItemService.isMenuItem(event.getOldCursor()) || (!menuItemService.isHidden(player) && touchesMenuSlot(event))) {
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
        // with the hotbar item switched off the last slot is an ordinary slot
        if (event.getWhoClicked() instanceof Player player && menuItemService.isHidden(player)) {
            return false;
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
                if (menuItemService.isMenuItem(player.getItemOnCursor())) {
                    player.setItemOnCursor(null);
                }
                menuItemService.ensure(player);
                player.updateInventory();
            }
        });
    }
}
