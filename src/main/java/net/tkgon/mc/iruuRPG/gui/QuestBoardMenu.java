package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.quest.QuestDefinition;
import net.tkgon.mc.iruuRPG.quest.QuestService;
import net.tkgon.mc.iruuRPG.quest.QuestState;
import net.tkgon.mc.iruuRPG.quest.QuestType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The quest board of a town. The contents are the same in every town (some quests are tied to a town, e.g. the
 * Atlas tutorial ones). Main quests (story) on the upper rows, today's random sub quests on the lower row.
 */
public final class QuestBoardMenu implements Listener {

    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int MAIN_LABEL_SLOT = 9;
    private static final int SUB_LABEL_SLOT = 36;
    private static final int[] MAIN_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int[] SUB_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    private final QuestService questService;

    public QuestBoardMenu(QuestService questService) {
        this.questService = questService;
    }

    public void open(Player player, String town) {
        String key = town == null || town.isBlank() ? "atlas" : town.toLowerCase();
        Holder holder = new Holder(player.getUniqueId(), key);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("依頼ボード"));
        holder.setInventory(inventory);

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of(), false);
        for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, filler);

        inventory.setItem(INFO_SLOT, item(Material.GOLD_INGOT, "所持金: " + questService.currency(player) + " " + questService.currencyName(),
                NamedTextColor.GOLD, List.of(
                        text("サブ依頼は、あと約" + formatRefresh(questService.minutesUntilRefresh()) + "で入れ替わる", NamedTextColor.GRAY)), false));
        inventory.setItem(MAIN_LABEL_SLOT, item(Material.WRITTEN_BOOK, "メイン依頼", NamedTextColor.GOLD,
                List.of(text("物語に関わる依頼。一度きり", NamedTextColor.GRAY)), false));
        inventory.setItem(SUB_LABEL_SLOT, item(Material.CLOCK, "サブ依頼", NamedTextColor.AQUA,
                List.of(text("短い依頼。24時間で入れ替わる", NamedTextColor.GRAY)), false));

        place(inventory, holder, player, questService.mainEntries(player, key), MAIN_SLOTS);
        place(inventory, holder, player, questService.subEntries(player, key), SUB_SLOTS);
        player.openInventory(inventory);
    }

    private void place(Inventory inventory, Holder holder, Player player, List<QuestService.Entry> entries, int[] slots) {
        for (int index = 0; index < entries.size() && index < slots.length; index++) {
            QuestService.Entry entry = entries.get(index);
            inventory.setItem(slots[index], questItem(entry));
            holder.quests.put(slots[index], entry.quest().id());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId.equals(player.getUniqueId())) return;

        String questId = holder.quests.get(event.getRawSlot());
        if (questId == null) return;

        QuestDefinition quest = questService.find(questId).orElse(null);
        if (quest == null) return;

        var profileState = state(player, quest);
        switch (profileState) {
            case AVAILABLE -> questService.accept(player, quest);
            case IN_PROGRESS -> {
                if (quest.type() == QuestType.DELIVER) {
                    questService.deliver(player, quest);
                } else if (quest.type() == QuestType.INFO) {
                    questService.playExplanation(player, quest);
                    player.closeInventory();
                    return;
                }
            }
            case READY -> questService.claim(player, quest);
            case DONE -> {
                return;
            }
        }
        open(player, holder.town);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private QuestState state(Player player, QuestDefinition quest) {
        return questService.stateOf(questService.profileOf(player), quest);
    }

    private ItemStack questItem(QuestService.Entry entry) {
        QuestDefinition quest = entry.quest();
        QuestState state = entry.state();
        NamedTextColor color = switch (state) {
            case AVAILABLE -> NamedTextColor.YELLOW;
            case IN_PROGRESS -> NamedTextColor.AQUA;
            case READY -> NamedTextColor.GREEN;
            case DONE -> NamedTextColor.DARK_GRAY;
        };
        Material material = switch (quest.type()) {
            case KILL -> Material.IRON_SWORD;
            case DELIVER -> Material.CHEST;
            case INFO -> Material.BOOK;
        };

        List<Component> lore = new ArrayList<>();
        for (String line : quest.description()) lore.add(text(line, NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(text("目標: " + objective(entry), NamedTextColor.GRAY));
        lore.add(text("報酬: " + quest.reward() + " " + questService.currencyName(), NamedTextColor.GOLD));
        lore.add(Component.empty());
        lore.add(text(switch (state) {
            case AVAILABLE -> "クリックで受ける";
            case IN_PROGRESS -> switch (quest.type()) {
                case DELIVER -> "クリックで納品する";
                case INFO -> "クリックでもう一度聞く";
                case KILL -> "進行中";
            };
            case READY -> "クリックで報酬を受け取る";
            case DONE -> "達成済み";
        }, color));
        return item(material, quest.name(), color, lore, state == QuestState.READY);
    }

    private String objective(QuestService.Entry entry) {
        QuestDefinition quest = entry.quest();
        int progress = Math.min(entry.progress(), quest.amount());
        return switch (quest.type()) {
            case KILL -> quest.targetName() + "を倒す (" + progress + "/" + quest.amount() + ")";
            case DELIVER -> quest.targetName() + "を納品 (" + quest.amount() + "個)";
            case INFO -> "話を聞く";
        };
    }

    private static String formatRefresh(long minutes) {
        long hours = minutes / 60;
        return hours > 0 ? hours + "時間" + (minutes % 60) + "分" : minutes + "分";
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (glint) meta.addEnchant(Enchantment.LUCK, 1, true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final class Holder implements InventoryHolder {
        private final UUID ownerId;
        private final String town;
        private final Map<Integer, String> quests = new HashMap<>();
        private Inventory inventory;

        private Holder(UUID ownerId, String town) {
            this.ownerId = ownerId;
            this.town = town;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
