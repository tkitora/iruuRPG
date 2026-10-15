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
            openFromInventory(event, player);
        }
    }

    /** Clicking the menu item inside the inventory screen opens the menu too (like using it from the hotbar). */
    private void openFromInventory(InventoryClickEvent event, Player player) {
        if (!menuItemService.isMenuItem(event.getCurrentItem())) return;
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) return;
        switch (event.getClick()) {
            case LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, CREATIVE -> openNextTick(player, event.getCursor());
            default -> {
            }
        }
    }

    /**
     * @param cursorAtClick the item that was held when the menu item was clicked. Normally the server still has it on
     *                      the cursor next tick; if it does not (creative mode: the client holds the item by itself),
     *                      this copy is what gets given back.
     */
    private void openNextTick(Player player, ItemStack cursorAtClick) {
        ItemStack held = cursorAtClick == null || cursorAtClick.getType().isAir() || menuItemService.isMenuItem(cursorAtClick)
                ? null : cursorAtClick.clone();
        // opening on the next tick: the click event itself is still being processed
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            returnCursorItem(player, held);
            mainMenu.open(player);
        });
    }

    /** An item held on the cursor would be lost when the menu replaces the inventory screen: put it back in the inventory first. */
    private void returnCursorItem(Player player, ItemStack heldAtClick) {
        ItemStack onCursor = player.getItemOnCursor();
        boolean serverCursorEmpty = onCursor == null || onCursor.getType().isAir();
        // The click-time copy is only for creative mode (the client holds the item itself there). In survival an empty
        // server cursor means the item is already back in the inventory (e.g. the screen was closed in the same tick),
        // and giving the copy as well would duplicate it.
        ItemStack back = !serverCursorEmpty ? onCursor.clone()
                : player.getGameMode() == org.bukkit.GameMode.CREATIVE ? heldAtClick : null;
        player.setItemOnCursor(null);
        if (back == null || back.getType().isAir() || menuItemService.isMenuItem(back)) return;

        for (ItemStack leftover : player.getInventory().addItem(back).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.updateInventory();
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
            if (!inOurMenu && menuItemService.isMenuItem(event.getCurrentItem())) openNextTick(player, event.getCursor());
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
                if (menuItemService.isMenuItem(player.getItemOnCursor())) {
                    player.setItemOnCursor(null);
                }
                menuItemService.ensure(player);
                player.updateInventory();
            }
        });
    }
}
