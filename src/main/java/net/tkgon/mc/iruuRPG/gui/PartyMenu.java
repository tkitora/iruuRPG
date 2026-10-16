package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.party.PartyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The party screen: members (leader, online or not), leave / disband, and how to invite. */
public final class PartyMenu implements Listener {

    private static final int SIZE = 27;
    private static final int[] MEMBER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int BACK = 18;
    private static final int HELP = 22;
    private static final int ACCEPT = 22;
    private static final int LEAVE = 24;
    private static final int DISBAND = 26;

    private final PartyService service;
    private MainMenu mainMenu;

    public PartyMenu(PartyService service) {
        this.service = service;
    }

    public void setMainMenu(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
    }

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("パーティー"));
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.members.clear();
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 18) inventory.setItem(slot, frame);
        }
        inventory.setItem(BACK, MenuButtons.back());

        PartyService.Party party = service.partyOf(player.getUniqueId());
        if (party == null) {
            String inviter = service.invitedBy(player.getUniqueId());
            if (inviter != null) {
                inventory.setItem(ACCEPT, item(Material.LIME_CONCRETE, inviter + " のパーティーに入る", NamedTextColor.GREEN,
                        List.of(text("クリックで参加", NamedTextColor.GRAY))));
                holder.acceptable = true;
            } else {
                inventory.setItem(HELP, item(Material.PAPER, "パーティーに入っていない", NamedTextColor.GRAY, List.of(
                        text("作る・誘う: /party invite <名前>", NamedTextColor.WHITE),
                        text("最大 " + service.maxSize() + " 人。ダンジョンに一緒に入り、", NamedTextColor.GRAY),
                        text("近くの仲間が倒した敵の経験値ももらえる", NamedTextColor.GRAY))));
            }
            return;
        }

        boolean leader = player.getUniqueId().equals(party.leader());
        List<UUID> members = party.members();
        for (int index = 0; index < members.size() && index < MEMBER_SLOTS.length; index++) {
            UUID id = members.get(index);
            OfflinePlayer member = Bukkit.getOfflinePlayer(id);
            boolean online = member.isOnline();
            boolean isLeader = id.equals(party.leader());
            List<Component> lore = new ArrayList<>();
            lore.add(text(isLeader ? "リーダー" : "メンバー", isLeader ? NamedTextColor.GOLD : NamedTextColor.WHITE));
            lore.add(text(online ? "オンライン" : "オフライン", online ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            if (leader && !id.equals(player.getUniqueId())) {
                lore.add(text("右クリック: リーダーを渡す", NamedTextColor.GRAY));
                lore.add(text("Shift+クリック: パーティーから外す", NamedTextColor.RED));
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta skull) skull.setOwningPlayer(member);
            if (meta != null) {
                meta.displayName(Component.text((isLeader ? "★ " : "") + PartyService.nameOf(id), online ? NamedTextColor.WHITE : NamedTextColor.GRAY, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                head.setItemMeta(meta);
            }
            inventory.setItem(MEMBER_SLOTS[index], head);
            holder.members.put(MEMBER_SLOTS[index], id);
        }
        inventory.setItem(HELP, item(Material.PAPER, "メンバー " + party.size() + " / " + service.maxSize(), NamedTextColor.AQUA, List.of(
                text(leader ? "誘う: /party invite <名前>" : "誘えるのはリーダーだけ", NamedTextColor.WHITE),
                text("パーティーだけで話す: /pc <メッセージ>", NamedTextColor.GRAY))));
        inventory.setItem(LEAVE, item(Material.OAK_DOOR, "パーティーを抜ける", NamedTextColor.RED, List.of()));
        if (leader) inventory.setItem(DISBAND, item(Material.BARRIER, "パーティーを解散する", NamedTextColor.DARK_RED, List.of()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot == BACK) {
            if (mainMenu != null) mainMenu.open(player);
            return;
        }
        PartyService.Party party = service.partyOf(player.getUniqueId());
        if (party == null) {
            if (slot == ACCEPT && holder.acceptable) {
                service.accept(player);
                render(player, holder);
            }
            return;
        }
        if (slot == LEAVE) {
            player.closeInventory();
            service.leave(player);
        } else if (slot == DISBAND && player.getUniqueId().equals(party.leader())) {
            player.closeInventory();
            service.disband(player);
        } else if (holder.members.containsKey(slot) && player.getUniqueId().equals(party.leader())) {
            UUID target = holder.members.get(slot);
            if (target.equals(player.getUniqueId())) return;
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                service.kick(player, PartyService.nameOf(target));
            } else if (event.getClick() == ClickType.RIGHT) {
                service.promote(player, PartyService.nameOf(target));
            }
            render(player, holder);
        }
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
        private final Map<Integer, UUID> members = new HashMap<>();
        private Inventory inventory;
        private boolean acceptable;

        private Holder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
