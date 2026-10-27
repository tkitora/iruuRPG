package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The storage screen behind the ender chests: 45 item slots that work like a chest (put items in, take them out,
 * shift-click from the inventory), and a bottom row with the page buttons. Pages that are not unlocked yet ask for
 * a confirmation and cost gold. Items are written back whenever the page changes, the screen closes, and every
 * half minute while it is open.
 */
public final class StorageMenu implements Listener {

    private static final int SIZE = 54;
    private static final int PREV = 45;
    private static final int INFO = 49;
    private static final int NEXT = 53;
    private static final int[] FILLER = {46, 47, 48, 50, 51, 52};

    private final JavaPlugin plugin;
    private final StorageService service;
    private final Predicate<ItemStack> storable;
    private final Map<UUID, Holder> open = new HashMap<>();

    public StorageMenu(JavaPlugin plugin, StorageService service, Predicate<ItemStack> storable) {
        this.plugin = plugin;
        this.service = service;
        this.storable = storable;
    }

    /** Every 30 s: what is on screen is written to the files, so a crash loses at most that. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Holder holder : open.values()) {
                capture(holder);
                service.save(holder.ownerId);
            }
        }, 600L, 600L);
    }

    /**
     * The item patch: the pages on screen go into the data first (so the half-minute save does not bring the old
     * items back), every storage in memory is patched, then the pages on screen are drawn again.
     */
    public int patchItems(java.util.function.Function<ItemStack, ItemStack> patch) {
        for (Holder holder : open.values()) capture(holder);
        int changed = service.patchLoaded(patch);
        if (changed > 0) {
            for (Holder holder : open.values()) {
                Player player = Bukkit.getPlayer(holder.ownerId);
                if (player != null && holder.inventory != null && !holder.confirm) render(player, holder);
            }
        }
        return changed;
    }

    /** Server stop: everything on screen is saved. */
    public void shutdown() {
        for (Holder holder : new ArrayList<>(open.values())) {
            capture(holder);
            service.save(holder.ownerId);
        }
        for (UUID id : new ArrayList<>(open.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.closeInventory();
        }
        open.clear();
    }

    // ---- opening ------------------------------------------------------------------------------------------------------

    /** Right-clicking an ender chest opens the storage instead (sneaking with an item in hand still places blocks). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.ENDER_CHEST) return;
        Player player = event.getPlayer();
        if (player.isSneaking() && event.getItem() != null) return;

        event.setCancelled(true); // the vanilla ender chest never opens
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (player.getWorld().getName().startsWith("dungeon_")) {
            player.sendMessage(Component.text("[ストレージ] ダンジョンの中では使えない。", NamedTextColor.RED));
            return;
        }
        open(player, 0);
    }

    public void open(Player player, int page) {
        // what the player already kept in the vanilla ender chest moves into the storage first
        int[] moved = service.migrateEnderChest(player);
        if (moved[0] > 0) {
            player.sendMessage(Component.text("[ストレージ] エンダーチェストに入れていたアイテム " + moved[0] + " 組を、ストレージに移した。", NamedTextColor.GREEN));
        }
        if (moved[1] > 0) {
            player.sendMessage(Component.text("[ストレージ] 入りきらなかった " + moved[1] + " 組は、エンダーチェストに残っている。ページを解放するか、空きを作って、もう一度開くと移せる。", NamedTextColor.YELLOW));
        }
        StorageService.PlayerStorage storage = service.of(player);
        Holder holder = new Holder(player.getUniqueId());
        holder.page = Math.max(0, Math.min(page, storage.unlocked() - 1));
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text("ストレージ"));
        render(player, holder);
        open.put(player.getUniqueId(), holder);
        player.openInventory(holder.inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.1f);
    }

    /** Fills the item slots with the page and the bottom row with the buttons. */
    private void render(Player player, Holder holder) {
        StorageService.PlayerStorage storage = service.of(player);
        ItemStack[] items = storage.page(holder.page);
        for (int slot = 0; slot < StorageService.PAGE_SLOTS; slot++) holder.inventory.setItem(slot, items[slot] == null ? null : items[slot].clone());

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot : FILLER) holder.inventory.setItem(slot, filler);

        int pages = storage.unlocked();
        holder.inventory.setItem(PREV, holder.page > 0
                ? item(Material.ARROW, "前のページ (" + holder.page + ")", NamedTextColor.YELLOW, List.of())
                : filler);
        holder.inventory.setItem(INFO, item(Material.ENDER_CHEST, "ページ " + (holder.page + 1) + " / " + pages, NamedTextColor.LIGHT_PURPLE, List.of(
                text("所持金: " + service.gold(player) + " " + service.currency(), NamedTextColor.GOLD),
                text("解放済み: " + pages + " / " + service.maxPages() + " ページ", NamedTextColor.GRAY),
                text("アイテムを入れる・取り出す: 普通のチェストと同じ", NamedTextColor.GRAY))));
        if (holder.page + 1 < pages) {
            holder.inventory.setItem(NEXT, item(Material.ARROW, "次のページ (" + (holder.page + 2) + ")", NamedTextColor.YELLOW, List.of()));
        } else if (pages < service.maxPages()) {
            long price = service.priceOf(pages + 1);
            holder.inventory.setItem(NEXT, item(Material.GOLD_INGOT, "ページ " + (pages + 1) + " を解放", NamedTextColor.GOLD, List.of(
                    text("価格: " + price + " " + service.currency(), service.gold(player) >= price ? NamedTextColor.WHITE : NamedTextColor.RED),
                    text("クリックで確認画面", NamedTextColor.GRAY))));
        } else {
            holder.inventory.setItem(NEXT, filler);
        }
    }

    /** The page on screen goes back into the player's data (not to disk). */
    private void capture(Holder holder) {
        if (holder.inventory == null || holder.confirm) return;
        ItemStack[] items = new ItemStack[StorageService.PAGE_SLOTS];
        for (int slot = 0; slot < StorageService.PAGE_SLOTS; slot++) items[slot] = holder.inventory.getItem(slot);
        service.of(holder.ownerId).setPage(holder.page, items);
    }

    // ---- clicks -------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId.equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (holder.confirm) {
            event.setCancelled(true);
            if (event.getRawSlot() == 11) {
                String error = service.unlockNext(player);
                if (error != null) player.sendMessage(Component.text("[ストレージ] " + error, NamedTextColor.RED));
                open(player, service.of(player).unlocked() - 1);
            } else if (event.getRawSlot() == 15) {
                open(player, holder.page);
            }
            return;
        }

        int raw = event.getRawSlot();
        // nothing that is not a storable item goes in (the menu item), by click, hotbar key or shift
        if (event.getCurrentItem() != null && !storable.test(event.getCurrentItem()) && raw >= SIZE) {
            event.setCancelled(true);
            return;
        }
        if (event.getCursor() != null && !storable.test(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (hotbar != null && !storable.test(hotbar)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (!storable.test(offhand)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            // collecting matching items from both inventories could reach the button row: not allowed
            event.setCancelled(true);
            return;
        }

        if (raw >= StorageService.PAGE_SLOTS && raw < SIZE) {
            // the bottom row: buttons only
            event.setCancelled(true);
            if (raw == PREV && holder.page > 0) {
                switchPage(player, holder, holder.page - 1);
            } else if (raw == NEXT) {
                StorageService.PlayerStorage storage = service.of(player);
                if (holder.page + 1 < storage.unlocked()) {
                    switchPage(player, holder, holder.page + 1);
                } else if (storage.unlocked() < service.maxPages()) {
                    askUnlock(player, holder);
                }
            }
            return;
        }
        // the item slots and the player's own inventory work like a chest; remember to save soon
        holder.dirty = true;
    }

    private void switchPage(Player player, Holder holder, int page) {
        capture(holder);
        service.save(holder.ownerId);
        holder.page = page;
        render(player, holder);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.2f);
    }

    private void askUnlock(Player player, Holder holder) {
        capture(holder);
        service.save(holder.ownerId);
        int next = service.of(player).unlocked() + 1;
        long price = service.priceOf(next);
        Holder confirm = new Holder(player.getUniqueId());
        confirm.confirm = true;
        confirm.page = holder.page;
        confirm.inventory = Bukkit.createInventory(confirm, 27, Component.text("ページ " + next + " を解放しますか?"));
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < 27; slot++) confirm.inventory.setItem(slot, frame);
        confirm.inventory.setItem(11, item(Material.LIME_CONCRETE, "解放する (" + price + " " + service.currency() + ")", NamedTextColor.GREEN, List.of(
                text("所持金: " + service.gold(player) + " " + service.currency(), service.gold(player) >= price ? NamedTextColor.WHITE : NamedTextColor.RED))));
        confirm.inventory.setItem(15, item(Material.RED_CONCRETE, "やめる", NamedTextColor.RED, List.of()));
        open.put(player.getUniqueId(), confirm);
        player.openInventory(confirm.inventory);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        boolean touchesButtons = false;
        for (int raw : event.getRawSlots()) {
            if (raw >= StorageService.PAGE_SLOTS && raw < SIZE) touchesButtons = true;
        }
        if (holder.confirm || touchesButtons || (event.getOldCursor() != null && !storable.test(event.getOldCursor()))) {
            event.setCancelled(true);
            return;
        }
        holder.dirty = true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        capture(holder);
        service.save(holder.ownerId);
        // another screen of ours (the confirmation) may already have replaced this one
        if (open.get(holder.ownerId) == holder) open.remove(holder.ownerId);
        Player player = Bukkit.getPlayer(holder.ownerId);
        if (player != null && !holder.confirm) player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.6f, 1.1f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Holder holder = open.remove(id);
        if (holder != null) {
            capture(holder);
        }
        service.save(id);
        service.forget(id);
    }

    // ---- items -----------------------------------------------------------------------------------------------------------

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
        private Inventory inventory;
        private int page;
        private boolean confirm;
        private boolean dirty;

        private Holder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
