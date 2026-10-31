package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.questline.Questline;
import net.tkgon.mc.iruuRPG.questline.QuestlineData;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.LineState;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.Status;
import net.tkgon.mc.iruuRPG.questline.QuestlineService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.List;
import java.util.UUID;

/**
 * The player's quest lines (main menu → クエスト): three tabs (進行中 / 中断中 / 完了), each line with its description,
 * its goal now and how far it is; a line in progress is paused with a shift-click and a paused one resumed with a click
 * (a paused line hears nothing until resumed).
 */
public final class QuestLogMenu implements Listener {

    private static final int SIZE = 54;
    private static final int[] TAB_SLOTS = {2, 4, 6};
    private static final int LIST_START = 9;
    private static final int LIST_END = 45;
    private static final int BACK_SLOT = 45;

    private enum Tab {
        ACTIVE("進行中", Material.WRITABLE_BOOK, NamedTextColor.AQUA),
        PAUSED("中断中", Material.CLOCK, NamedTextColor.YELLOW),
        DONE("完了", Material.ENCHANTED_BOOK, NamedTextColor.GOLD);

        private final String label;
        private final Material icon;
        private final NamedTextColor color;

        Tab(String label, Material icon, NamedTextColor color) {
            this.label = label;
            this.icon = icon;
            this.color = color;
        }

        boolean holds(Status status) {
            return switch (this) {
                case ACTIVE -> status == Status.ACTIVE;
                case PAUSED -> status == Status.PAUSED;
                case DONE -> status == Status.COMPLETED || status == Status.FAILED;
            };
        }
    }

    private final QuestlineService questlines;
    private MainMenu mainMenu;

    public QuestLogMenu(QuestlineService questlines) {
        this.questlines = questlines;
    }

    public void setMainMenu(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
    }

    public void open(Player player) {
        open(player, Tab.ACTIVE);
    }

    private void open(Player player, Tab tab) {
        Holder holder = new Holder(player.getUniqueId(), tab);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("クエスト"));
        holder.inventory = inventory;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of(), false);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, filler);
        for (int slot = 45; slot < SIZE; slot++) inventory.setItem(slot, filler);

        QuestlineData data = questlines.dataOf(player);
        for (Tab each : Tab.values()) {
            long count = data.lines().values().stream().filter(state -> each.holds(state.status)).count();
            inventory.setItem(TAB_SLOTS[each.ordinal()], item(each.icon, each.label + " (" + count + ")", each.color,
                    List.of(text(each == tab ? "表示中" : "クリックで表示", NamedTextColor.GRAY)), each == tab));
        }
        int slot = LIST_START;
        for (var entry : data.lines().entrySet()) {
            if (slot >= LIST_END) break;
            LineState state = entry.getValue();
            if (!tab.holds(state.status)) continue;
            Questline line = questlines.engine().line(entry.getKey());
            if (line == null) continue;
            holder.ids.add(entry.getKey());
            inventory.setItem(slot++, lineItem(data, line, state, tab));
        }
        if (holder.ids.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER, tab == Tab.ACTIVE ? "進行中のクエストはありません" : "ありません", NamedTextColor.GRAY,
                    tab == Tab.ACTIVE ? List.of(text("頭上に「!」が出ている人に話しかけてみよう", NamedTextColor.GRAY)) : List.of(), false));
        }
        if (mainMenu != null) inventory.setItem(BACK_SLOT, MenuButtons.back());
        player.openInventory(inventory);
    }

    private ItemStack lineItem(QuestlineData data, Questline line, LineState state, Tab tab) {
        List<Component> lore = new ArrayList<>();
        for (String description : line.description()) lore.add(text(description, NamedTextColor.WHITE));
        Questline.Step step = line.step(state.step);
        if (tab != Tab.DONE) {
            lore.add(Component.empty());
            if (step != null && step.name() != null) lore.add(text("目標: " + step.name(), NamedTextColor.AQUA));
            String progress = questlines.engine().progress(data, line.id());
            if (progress != null) lore.add(text("進み具合: " + progress, NamedTextColor.GREEN));
            lore.add(Component.empty());
            lore.add(text(tab == Tab.ACTIVE ? "シフト+クリック: 中断する (進み具合は残る)" : "クリック: 再開する", NamedTextColor.GRAY));
        } else {
            lore.add(Component.empty());
            lore.add(text(state.status == Status.COMPLETED ? "達成" + (state.runs > 1 ? " (" + state.runs + "回)" : "") : "失敗",
                    state.status == Status.COMPLETED ? NamedTextColor.GOLD : NamedTextColor.RED));
        }
        Material material = switch (tab) {
            case ACTIVE -> "main".equals(line.category()) ? Material.WRITABLE_BOOK : Material.BOOK;
            case PAUSED -> Material.CLOCK;
            case DONE -> state.status == Status.COMPLETED ? Material.ENCHANTED_BOOK : Material.RED_DYE;
        };
        NamedTextColor color = "main".equals(line.category()) ? NamedTextColor.GOLD : NamedTextColor.YELLOW;
        return item(material, line.name(), color, lore, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        for (Tab tab : Tab.values()) {
            if (slot == TAB_SLOTS[tab.ordinal()]) {
                open(player, tab);
                return;
            }
        }
        if (slot == BACK_SLOT && mainMenu != null) {
            mainMenu.open(player);
            return;
        }
        int index = slot - LIST_START;
        if (index < 0 || index >= holder.ids.size()) return;
        String id = holder.ids.get(index);
        if (holder.tab == Tab.ACTIVE && event.isShiftClick()) {
            if (questlines.pause(player, id)) {
                player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.6f, 1.0f);
                player.sendMessage(Component.text("[クエスト] 中断した。メニューの「中断中」から再開できる。", NamedTextColor.YELLOW));
            }
            open(player, Tab.ACTIVE);
        } else if (holder.tab == Tab.PAUSED) {
            if (questlines.resume(player, id)) {
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
                player.sendMessage(Component.text("[クエスト] 再開した。", NamedTextColor.AQUA));
            } else {
                String running = questlines.exclusiveName(player, id);
                if (running != null) player.sendMessage(Component.text("[クエスト] 「" + running + "」の途中なので、今は再開できない。", NamedTextColor.GRAY));
            }
            open(player, Tab.PAUSED);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glow) meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static final class Holder implements MenuHolder {
        private final UUID ownerId;
        private final Tab tab;
        private final List<String> ids = new ArrayList<>();
        private Inventory inventory;

        private Holder(UUID ownerId, Tab tab) {
            this.ownerId = ownerId;
            this.tab = tab;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
