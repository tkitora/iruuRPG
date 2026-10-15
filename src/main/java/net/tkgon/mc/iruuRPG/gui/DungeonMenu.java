package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.dungeon.DungeonDefinition;
import net.tkgon.mc.iruuRPG.dungeon.DungeonService;
import org.bukkit.Bukkit;
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

/** The dungeon list: what each dungeon is, whether it can be entered now, and a button to leave while inside. */
public final class DungeonMenu implements Listener {

    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int LEAVE_SLOT = 49;

    private final DungeonService service;
    private MainMenu mainMenu;

    public DungeonMenu(DungeonService service) {
        this.service = service;
    }

    public void setMainMenu(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
    }

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("ダンジョン"));
        holder.inventory = inventory;

        ItemStack frame = pane();
        for (int slot = 45; slot < SIZE; slot++) inventory.setItem(slot, frame);

        int slot = 0;
        for (DungeonDefinition definition : service.registry().all()) {
            if (slot >= 45) break;
            inventory.setItem(slot, dungeonItem(player, definition));
            holder.actions.put(slot, definition.id());
            slot++;
        }
        if (slot == 0) {
            inventory.setItem(22, item(Material.PAPER, "ダンジョンはまだありません", NamedTextColor.GRAY, List.of()));
        }
        inventory.setItem(BACK_SLOT, MenuButtons.back());
        if (service.instanceOf(player) != null) {
            inventory.setItem(LEAVE_SLOT, item(Material.OAK_DOOR, "ダンジョンから出る", NamedTextColor.RED, List.of(
                    text("元の場所に戻る (途中で出ると報酬はない)", NamedTextColor.GRAY))));
        }
        player.openInventory(inventory);
    }

    private ItemStack dungeonItem(Player player, DungeonDefinition definition) {
        String problem = service.whyNot(player, definition);
        boolean inside = service.instanceOf(player) != null;
        Material icon = !definition.ready() ? Material.GRAY_DYE : problem == null ? Material.SPAWNER : Material.IRON_BARS;
        List<Component> lore = new ArrayList<>();
        for (String line : definition.description()) lore.add(text(line, NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text("推奨 Lv" + definition.minLevel() + " 以上", NamedTextColor.YELLOW));
        lore.add(text("制限時間 " + (definition.timeLimitSeconds() / 60) + "分" + (definition.timeLimitSeconds() % 60 == 0 ? "" : (definition.timeLimitSeconds() % 60) + "秒"), NamedTextColor.WHITE));
        if (definition.deathMode() == DungeonDefinition.DeathMode.LIVES) {
            lore.add(text("残機 " + definition.lives(), NamedTextColor.AQUA));
        } else {
            lore.add(text("倒れたら、仲間が体の横に5秒立つと復活 (全員倒れたら失敗)", NamedTextColor.AQUA));
        }
        lore.add(text("最大 " + definition.maxPlayers() + " 人 (中から /dungeon invite <名前> で招待)", NamedTextColor.WHITE));
        if (definition.boss() != null) lore.add(text("ボスを倒すとクリア。脱出口から帰還", NamedTextColor.RED));
        if (definition.chest() != null) lore.add(text("クリア後、報酬部屋の宝箱からゴールド・経験値・アイテム (激レアも)", NamedTextColor.GOLD));
        if (definition.chest() == null) lore.add(text("報酬 " + definition.rewardGold() + " ゴールド / 経験値 " + definition.rewardXp(), NamedTextColor.GOLD));
        lore.add(Component.empty());
        if (!definition.ready()) {
            lore.add(text("準備中", NamedTextColor.DARK_GRAY));
        } else if (problem == null) {
            lore.add(text("クリックで入る", NamedTextColor.GREEN));
        } else {
            lore.add(text(inside ? "ダンジョンの中にいる" : problem, NamedTextColor.RED));
        }
        return item(icon, definition.name(), problem == null ? NamedTextColor.GREEN : NamedTextColor.GRAY, lore);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            if (mainMenu != null) mainMenu.open(player);
            return;
        }
        if (slot == LEAVE_SLOT && service.instanceOf(player) != null) {
            player.closeInventory();
            service.leave(player);
            return;
        }
        String id = holder.actions.get(slot);
        if (id != null) service.enter(player, id);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private ItemStack pane() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ITEM_SPECIFICS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final class Holder implements MenuHolder {
        private final UUID ownerId;
        private final Map<Integer, String> actions = new HashMap<>();
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
