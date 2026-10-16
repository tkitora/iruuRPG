package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.travel.TravelService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The travel list: points found so far (click to travel) and the ones not found yet. */
public final class TravelMenu implements Listener {

    private static final int SIZE = 54;
    private static final int BACK = 45;

    private final TravelService service;
    private MainMenu mainMenu;

    public TravelMenu(TravelService service) {
        this.service = service;
    }

    public void setMainMenu(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
    }

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("ファストトラベル"));
        holder.inventory = inventory;
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 45; slot < SIZE; slot++) inventory.setItem(slot, frame);
        inventory.setItem(BACK, MenuButtons.back());

        int slot = 0;
        for (TravelService.Waypoint point : service.all()) {
            if (slot >= 45) break;
            boolean known = service.known(player, point.id());
            List<Component> lore = new ArrayList<>();
            if (known) {
                Location at = point.location();
                if (at != null && at.getWorld().equals(player.getWorld())) {
                    lore.add(text("ここから " + Math.round(at.distance(player.getLocation())) + " ブロック", NamedTextColor.GRAY));
                }
                if (service.cost() > 0) lore.add(text("料金: " + service.cost() + " ゴールド", NamedTextColor.GOLD));
                lore.add(text("クリックで移動 (数秒その場で待つ。動くと中断)", NamedTextColor.GREEN));
                inventory.setItem(slot, item(Material.ENDER_EYE, point.name(), NamedTextColor.AQUA, lore));
                holder.points.put(slot, point.id());
            } else {
                lore.add(text("まだ訪れていない。近くまで歩くと使えるようになる", NamedTextColor.GRAY));
                inventory.setItem(slot, item(Material.GRAY_DYE, "???", NamedTextColor.DARK_GRAY, lore));
            }
            slot++;
        }
        if (slot == 0) inventory.setItem(22, item(Material.PAPER, "ファストトラベル地点はまだありません", NamedTextColor.GRAY, List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId.equals(player.getUniqueId())) return;
        if (event.getRawSlot() == BACK) {
            if (mainMenu != null) mainMenu.open(player);
            return;
        }
        String id = holder.points.get(event.getRawSlot());
        if (id != null) service.travel(player, id);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final class Holder implements MenuHolder {
        private final UUID ownerId;
        private final Map<Integer, String> points = new HashMap<>();
        private Inventory inventory;

        private Holder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
