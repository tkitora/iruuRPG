package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MainMenuItemService {

    public static final int MENU_SLOT = 8;

    private final JavaPlugin plugin;
    private final NamespacedKey menuItemKey;
    private final NamespacedKey hiddenKey;

    public MainMenuItemService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.menuItemKey = new NamespacedKey(plugin, "main_menu_item");
        this.hiddenKey = new NamespacedKey(plugin, "main_menu_item_hidden");
    }

    /** True when this player chose to have no menu item in the hotbar (they open the menu with /menu instead). */
    public boolean isHidden(Player player) {
        return player.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE);
    }

    /** Hides or shows the hotbar menu item, remembered across logins; returns the new hidden state. */
    public boolean setHidden(Player player, boolean hidden) {
        if (hidden) {
            player.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(hiddenKey);
        }
        ensure(player);
        player.updateInventory();
        return hidden;
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ensure(player);
            }
        }, 20L, 40L);
    }

    public void ensure(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (isHidden(player)) {
            removeAll(inventory);
            return;
        }
        removeDuplicates(inventory);

        ItemStack current = inventory.getItem(MENU_SLOT);
        if (isMenuItem(current)) {
            inventory.setItem(MENU_SLOT, createItem());
            return;
        }

        if (current != null && !current.getType().isAir()) {
            inventory.setItem(MENU_SLOT, null);
            int empty = firstEmptyOutsideMenu(inventory);
            if (empty >= 0) {
                inventory.setItem(empty, current);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), current);
            }
        }

        inventory.setItem(MENU_SLOT, createItem());
    }

    public boolean isMenuItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return meta.getPersistentDataContainer().has(menuItemKey, PersistentDataType.BYTE);
    }

    private ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("iruuRPG メニュー", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("右クリック / 左クリックで開く", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("このアイテムは移動・破棄できません", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(menuItemKey, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void removeAll(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isMenuItem(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
    }

    private void removeDuplicates(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == MENU_SLOT) continue;
            if (isMenuItem(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
    }

    private int firstEmptyOutsideMenu(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == MENU_SLOT) continue;

            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }
}
