package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.market.MarketBuyOffer;
import net.tkgon.mc.iruuRPG.market.MarketListing;
import net.tkgon.mc.iruuRPG.market.MarketService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The free market screens: home (pick buy or sell) -> listings (buy) -> amount and buy; or sell (pick an item from
 * the inventory, set price / amount / delay / duration, see the fee) and "my listings" (take back, pick up).
 */
public final class MarketMenu implements Listener {

    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final Material FRAME = Material.CYAN_STAINED_GLASS_PANE;

    private enum Screen { HOME, BROWSE, BUY, SELL, MINE, OFFERS, OFFER_QTY, OFFER_EDIT, ADMIN, HOUSE }

    private enum Sort {
        NEWEST("新しい順"), CHEAPEST("安い順"), ENDING("終わりが近い順");

        private final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    private final MarketService service;
    private final Predicate<ItemStack> sellable;
    private MainMenu mainMenu;

    public MarketMenu(MarketService service, Predicate<ItemStack> sellable) {
        this.service = service;
        this.sellable = sellable;
    }

    public void setMainMenu(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
    }

    // ---- entry -----------------------------------------------------------------------------------------------------

    public void open(Player player) {
        service.expire();
        service.claim(player); // waiting sale money and returned items that fit are handed over
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("フリーマーケット"));
        holder.inventory = inventory;
        renderHome(player, holder);
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.4f, 1.3f);
    }

    // ---- screens -----------------------------------------------------------------------------------------------------

    private void renderHome(Player player, Holder holder) {
        begin(holder, Screen.HOME);
        Inventory inventory = holder.inventory;
        frame(inventory);
        inventory.setItem(4, goldItem(player));
        inventory.setItem(20, item(Material.CHEST, "出品一覧 (買う)", NamedTextColor.GREEN, List.of(
                text("みんなの出品を見て、" + service.currencyName() + "で買います", NamedTextColor.GRAY),
                text("出品数: " + availableCount() + " 件", NamedTextColor.YELLOW))));
        inventory.setItem(24, item(Material.GOLD_INGOT, "出品する (売る)", NamedTextColor.GOLD, List.of(
                text("持ち物から選んで、値段・個数・期間を決めて出品します", NamedTextColor.GRAY),
                text("設定に応じて、出品時に手数料がかかります", NamedTextColor.RED))));
        List<Component> mine = new ArrayList<>();
        mine.add(text("自分の出品中: " + service.listingsOf(holder.ownerId).size() + " 件", NamedTextColor.YELLOW));
        long money = service.pendingProceeds(holder.ownerId);
        int stacks = service.pendingReturnStacks(holder.ownerId);
        mine.add(text("受け取れる売上: " + money + " " + service.currencyName(), NamedTextColor.GOLD));
        mine.add(text("受け取れる返却品: " + stacks + " 組", NamedTextColor.AQUA));
        inventory.setItem(31, item(Material.BOOK, "自分の出品・受け取り", NamedTextColor.AQUA, mine));
        inventory.setItem(29, item(Material.GOLD_BLOCK, "買取 (運営に売る)", NamedTextColor.YELLOW, List.of(
                text("運営 (iruuRPG) が決まった値段で買い取ります", NamedTextColor.GRAY),
                text("買取の種類: " + service.offers().size() + " 件", NamedTextColor.YELLOW))));
        holder.actions.put(29, "offers");
        if (player.hasPermission(MarketService.ADMIN_PERMISSION)) {
            inventory.setItem(33, item(Material.NETHER_STAR, "運営メニュー", NamedTextColor.LIGHT_PURPLE, List.of(
                    text("iruuRPG として出品・買取・回収をします", NamedTextColor.GRAY))));
            holder.actions.put(33, "admin");
        }
        inventory.setItem(45, item(Material.ARROW, "メインメニューへ", NamedTextColor.GRAY, List.of()));
        holder.actions.put(20, "browse");
        holder.actions.put(24, "sell");
        holder.actions.put(31, "mine");
        holder.actions.put(45, "main");
    }

    private void renderBrowse(Player player, Holder holder) {
        begin(holder, Screen.BROWSE);
        Inventory inventory = holder.inventory;
        long now = System.currentTimeMillis();

        List<MarketListing> shown = new ArrayList<>();
        for (MarketListing listing : service.all()) {
            if (!listing.isExpired(now)) shown.add(listing);
        }
        switch (holder.sort) {
            case NEWEST -> shown.sort(Comparator.comparingLong(MarketListing::listedAt).reversed());
            case CHEAPEST -> shown.sort(Comparator.comparingLong(MarketListing::unitPrice));
            case ENDING -> shown.sort(Comparator.comparingLong(MarketListing::expiresAt));
        }
        int pages = Math.max(1, (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(holder.page, pages - 1));

        for (int index = 0; index < PAGE_SIZE; index++) {
            int at = holder.page * PAGE_SIZE + index;
            if (at >= shown.size()) break;
            MarketListing listing = shown.get(at);
            ItemStack shownItem = listingItem(listing, now, holder.ownerId);
            if (holder.manage) {
                ItemMeta shownMeta = shownItem.getItemMeta();
                List<Component> shownLore = new ArrayList<>(shownMeta.lore() == null ? List.<Component>of() : shownMeta.lore());
                shownLore.add(text("クリックで取り下げ (残りは出品者の受け取り場所へ)", NamedTextColor.RED));
                shownMeta.lore(shownLore);
                shownItem.setItemMeta(shownMeta);
            }
            inventory.setItem(index, shownItem);
            holder.actions.put(index, (holder.manage ? "M:" : "L:") + listing.id());
        }
        for (int slot = PAGE_SIZE; slot < SIZE; slot++) inventory.setItem(slot, pane());
        inventory.setItem(45, item(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of()));
        holder.actions.put(45, holder.manage ? "admin" : "home");
        if (holder.page > 0) {
            inventory.setItem(47, item(Material.SPECTRAL_ARROW, "前のページ", NamedTextColor.YELLOW, List.of()));
            holder.actions.put(47, "prev");
        }
        inventory.setItem(49, item(Material.GOLD_INGOT, "所持金 " + service.gold(player) + " " + service.currencyName(), NamedTextColor.GOLD, List.of(
                text("ページ " + (holder.page + 1) + " / " + pages + "  (" + shown.size() + " 件)", NamedTextColor.GRAY),
                text(shown.isEmpty() ? "いまは出品がありません" : "買いたいものをクリック", NamedTextColor.WHITE))));
        if (holder.page < pages - 1) {
            inventory.setItem(51, item(Material.SPECTRAL_ARROW, "次のページ", NamedTextColor.YELLOW, List.of()));
            holder.actions.put(51, "next");
        }
        inventory.setItem(53, item(Material.HOPPER, "並び順: " + holder.sort.label, NamedTextColor.AQUA, List.of(text("クリックで切り替え", NamedTextColor.GRAY))));
        holder.actions.put(53, "sort");
    }

    private void renderBuy(Player player, Holder holder) {
        MarketListing listing = service.find(holder.buyId);
        long now = System.currentTimeMillis();
        if (listing == null || listing.isExpired(now)) {
            player.sendMessage(Component.text("[フリマ] その出品はもう終わっています。", NamedTextColor.RED));
            renderBrowse(player, holder);
            return;
        }
        begin(holder, Screen.BUY);
        Inventory inventory = holder.inventory;
        frame(inventory);
        holder.buyQty = Math.max(1, Math.min(holder.buyQty, listing.infinite() ? 64 * 9 : listing.remaining()));
        long total = listing.unitPrice() * holder.buyQty;

        inventory.setItem(13, listingItem(listing, now, holder.ownerId));
        // a button is green / red only while pressing it stays within the stock and the gold the player has
        int cap = listing.infinite() ? 64 * 9 : listing.remaining();
        long afford = listing.unitPrice() <= 0 ? cap : service.gold(player) / listing.unitPrice();
        int maxBuy = (int) Math.max(1, Math.min(cap, afford));
        inventory.setItem(20, stepButton(false, "-10", holder.buyQty - 10 >= 1));
        inventory.setItem(21, stepButton(false, "-1", holder.buyQty - 1 >= 1));
        ItemStack qty = item(Material.PAPER, holder.buyQty + " 個", NamedTextColor.WHITE, List.of(text(listing.infinite() ? "買う個数" : "買う個数 (残り " + listing.remaining() + ")", NamedTextColor.GRAY)));
        inventory.setItem(22, qty);
        inventory.setItem(23, stepButton(true, "+1", holder.buyQty + 1 <= maxBuy));
        inventory.setItem(24, stepButton(true, "+10", holder.buyQty + 10 <= maxBuy));
        boolean canMax = holder.buyQty < maxBuy;
        inventory.setItem(25, item(canMax ? Material.EMERALD : Material.GRAY_DYE, "買える最大", canMax ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of(text("所持金で買える数 (残りの範囲): " + maxBuy + " 個", NamedTextColor.GRAY))));
        inventory.setItem(31, item(Material.GOLD_INGOT, "合計 " + total + " " + service.currencyName(), NamedTextColor.GOLD, List.of(
                text("単価 " + listing.unitPrice() + " × " + holder.buyQty, NamedTextColor.GRAY),
                text("所持金 " + service.gold(player) + " " + service.currencyName(), service.gold(player) >= total ? NamedTextColor.WHITE : NamedTextColor.RED))));
        boolean ok = listing.isAvailable(now) && !listing.sellerId().equals(holder.ownerId) && service.gold(player) >= total;
        inventory.setItem(40, item(ok ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "買う", ok ? NamedTextColor.GREEN : NamedTextColor.GRAY, List.of(
                text(ok ? "クリックで購入" : listing.sellerId().equals(holder.ownerId) ? "自分の出品は買えません" : !listing.isAvailable(now) ? "まだ購入できません" : service.currencyName() + "が足りません",
                        ok ? NamedTextColor.WHITE : NamedTextColor.RED))));
        inventory.setItem(42, item(Material.RED_CONCRETE, "やめる", NamedTextColor.RED, List.of(text("一覧に戻る", NamedTextColor.GRAY))));
        if (holder.buyQty - 10 >= 1) holder.actions.put(20, "q:-10");
        if (holder.buyQty - 1 >= 1) holder.actions.put(21, "q:-1");
        if (holder.buyQty + 1 <= maxBuy) holder.actions.put(23, "q:+1");
        if (holder.buyQty + 10 <= maxBuy) holder.actions.put(24, "q:+10");
        if (canMax) holder.actions.put(25, "q:max");
        holder.actions.put(40, "buy");
        holder.actions.put(42, "browse");
    }

    private static final String[] SETTINGS = {"price", "qty", "delay", "duration"};

    private void renderSell(Player player, Holder holder) {
        begin(holder, Screen.SELL);
        Inventory inventory = holder.inventory;
        frame(inventory);

        boolean house = holder.admin && holder.infinite;
        int owned = holder.template == null ? 0 : service.count(player, holder.template);
        if (holder.template != null && owned <= 0 && !house) holder.template = null;
        holder.qty = Math.max(1, Math.min(holder.qty, house ? service.maxQuantity() : Math.max(1, Math.min(owned, service.maxQuantity()))));
        holder.price = Math.max(1, Math.min(holder.price, service.maxPrice()));
        holder.delay = Math.max(0, Math.min(holder.delay, service.maxDelayHours()));
        holder.duration = Math.max(1, Math.min(holder.duration, service.maxDurationHours()));

        if (holder.template == null) {
            inventory.setItem(4, item(Material.PAPER, "売るアイテムを選ぶ", NamedTextColor.YELLOW, List.of(
                    text("下の自分の持ち物の中の、売りたいアイテムをクリック", NamedTextColor.GRAY))));
        } else {
            ItemStack shown = holder.template.clone();
            ItemMeta meta = shown.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
                lore.add(text("- - - - - - - - - - - -", NamedTextColor.DARK_GRAY));
                lore.add(text("持っている数: " + owned, NamedTextColor.GRAY));
                lore.add(text("別のアイテムは、持ち物からクリックで選び直せる", NamedTextColor.GRAY));
                meta.lore(lore);
                shown.setItemMeta(meta);
            }
            inventory.setItem(4, shown);
        }

        // four setting rows: [-big][-mid][-small] value [+small][+mid][+big]
        settingRow(player, inventory, holder, 1, "price", Material.GOLD_INGOT, "単価 " + holder.price + " " + service.currencyName(),
                List.of(text("1個あたりの値段", NamedTextColor.GRAY), text("最大 " + service.maxPrice(), NamedTextColor.GRAY)), new int[]{1, 10, 100});
        settingRow(player, inventory, holder, 2, "qty", holder.template == null ? Material.CHEST : holder.template.getType(), "個数 " + holder.qty + " 個",
                List.of(text("売る数 (持っている数まで)", NamedTextColor.GRAY), text("持っている数: " + owned, NamedTextColor.GRAY)), new int[]{1, 5, 10});
        settingRow(player, inventory, holder, 3, "delay", Material.CLOCK, "購入可能までの期間 " + (holder.delay == 0 ? "なし (すぐ)" : hours(holder.delay)),
                List.of(text("出品してから、買えるようになるまでの待ち時間", NamedTextColor.GRAY), text("最大 " + hours(service.maxDelayHours()), NamedTextColor.GRAY)), new int[]{1, 6, 24});
        settingRow(player, inventory, holder, 4, "duration", Material.REPEATER, "購入可能期間 " + hours(holder.duration),
                List.of(text("買えるようになってから、売れ残りが返ってくるまで", NamedTextColor.GRAY), text("最大 " + hours(service.maxDurationHours()), NamedTextColor.GRAY)), new int[]{1, 6, 24});

        MarketService.Fee fee = holder.admin ? new MarketService.Fee(0, 0, 0, 0, 0) : service.fee(holder.price, holder.qty, holder.delay, holder.duration);
        long value = holder.price * holder.qty;
        long gold = service.gold(player);
        List<Component> feeLore = new ArrayList<>();
        feeLore.add(text("値段の分: " + fee.price() + " " + service.currencyName(), NamedTextColor.GRAY));
        feeLore.add(text("個数の分: " + fee.quantity() + " " + service.currencyName(), NamedTextColor.GRAY));
        feeLore.add(text("待ち時間の分: " + fee.delay() + " " + service.currencyName(), NamedTextColor.GRAY));
        feeLore.add(text("販売期間の分: " + fee.duration() + " " + service.currencyName(), NamedTextColor.GRAY));
        feeLore.add(text("手数料の合計: " + fee.total() + " " + service.currencyName(), NamedTextColor.GOLD));
        feeLore.add(text("全部売れたときの売上: " + value + " " + service.currencyName(), NamedTextColor.GREEN));
        feeLore.add(text("所持金: " + gold + " " + service.currencyName(), gold >= fee.total() ? NamedTextColor.WHITE : NamedTextColor.RED));
        for (int slot = 36 + 9; slot < SIZE; slot++) inventory.setItem(slot, pane());
        inventory.setItem(45, item(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of()));
        inventory.setItem(47, item(Material.PAPER, "手数料", NamedTextColor.GOLD, feeLore));
        boolean ready = holder.template != null && (house || owned >= holder.qty) && gold >= fee.total();
        inventory.setItem(51, item(ready ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, holder.admin ? "iruuRPG として出品する" : "出品する", ready ? NamedTextColor.GREEN : NamedTextColor.GRAY, List.of(
                text(ready ? (holder.admin ? "クリックで出品 (手数料なし)" : "クリックで出品 (手数料 " + fee.total() + ")") : holder.template == null ? "アイテムを選んでください" : owned < holder.qty ? "持っている数が足りません" : "手数料が足りません",
                        ready ? NamedTextColor.WHITE : NamedTextColor.RED))));
        if (holder.admin) {
            inventory.setItem(49, item(holder.infinite ? Material.ENDER_CHEST : Material.CHEST, holder.infinite ? "在庫: 無限" : "在庫: 持ち物から出す", holder.infinite ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.WHITE, List.of(
                    text("無限: 持ち物を使わず、売れても減らない", NamedTextColor.GRAY),
                    text("クリックで切り替え", NamedTextColor.GRAY))));
            holder.actions.put(49, "inf");
        }
        holder.actions.put(45, holder.admin ? "admin" : "home");
        holder.actions.put(51, "list");
    }

    private void settingRow(Player player, Inventory inventory, Holder holder, int row, String key, Material icon, String title, List<Component> lore, int[] steps) {
        int base = row * 9;
        List<Component> valueLore = new ArrayList<>(lore);
        valueLore.add(text("左クリック ×1 / 右クリック ×10 / Shift ×100", NamedTextColor.DARK_GRAY));
        inventory.setItem(base + 4, item(icon, title, NamedTextColor.WHITE, valueLore));
        for (int i = 0; i < 3; i++) {
            int minus = base + 1 + i;
            int plus = base + 7 - i;
            int step = steps[2 - i];
            boolean canMinus = adjustAllowed(player, holder, key, -step);
            boolean canPlus = adjustAllowed(player, holder, key, step);
            inventory.setItem(minus, stepButton(false, "-" + step, canMinus));
            inventory.setItem(plus, stepButton(true, "+" + step, canPlus));
            if (canMinus) holder.actions.put(minus, "adj:" + key + ":-" + step);
            if (canPlus) holder.actions.put(plus, "adj:" + key + ":" + step);
        }
    }

    private void renderMine(Player player, Holder holder) {
        begin(holder, Screen.MINE);
        Inventory inventory = holder.inventory;
        long now = System.currentTimeMillis();
        List<MarketListing> mine = service.listingsOf(holder.ownerId);
        for (int index = 0; index < mine.size() && index < 36; index++) {
            MarketListing listing = mine.get(index);
            ItemStack stack = listingItem(listing, now, holder.ownerId);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
                lore.add(text("クリックで取り下げ (残りが返却品に入る。手数料は戻らない)", NamedTextColor.RED));
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            inventory.setItem(index, stack);
            holder.actions.put(index, "c:" + listing.id());
        }
        for (int slot = 36; slot < SIZE; slot++) inventory.setItem(slot, pane());
        inventory.setItem(45, item(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of()));
        holder.actions.put(45, "home");
        long money = service.pendingProceeds(holder.ownerId);
        int stacks = service.pendingReturnStacks(holder.ownerId);
        inventory.setItem(49, item(Material.CHEST_MINECART, "受け取る", NamedTextColor.AQUA, List.of(
                text("売上: " + money + " " + service.currencyName(), NamedTextColor.GOLD),
                text("返却品: " + stacks + " 組", NamedTextColor.AQUA),
                text("クリックで、持ち物に入る分を受け取る", NamedTextColor.GRAY))));
        holder.actions.put(49, "claim");
        if (mine.isEmpty()) inventory.setItem(22, item(Material.PAPER, "出品中のものはありません", NamedTextColor.GRAY, List.of()));
    }

    private void renderOffers(Player player, Holder holder) {
        begin(holder, Screen.OFFERS);
        Inventory inventory = holder.inventory;
        List<MarketBuyOffer> offers = service.offers();
        for (int index = 0; index < offers.size() && index < PAGE_SIZE; index++) {
            MarketBuyOffer offer = offers.get(index);
            ItemStack stack = offer.item();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
                lore.add(text("- - - - - - - - - - - -", NamedTextColor.DARK_GRAY));
                lore.add(text("買取価格: " + offer.unitPrice() + " " + service.currencyName() + " (1個)", NamedTextColor.GOLD));
                lore.add(text(offer.unlimited() ? "買取の残り: 無制限" : "買取の残り: " + offer.remaining() + " 個", NamedTextColor.YELLOW));
                if (holder.manage) {
                    lore.add(text("クリックで買取をやめる", NamedTextColor.RED));
                } else {
                    lore.add(text("持っている数: " + service.count(player, offer.item()), NamedTextColor.GRAY));
                    lore.add(text("クリックで売る", NamedTextColor.GREEN));
                }
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            inventory.setItem(index, stack);
            holder.actions.put(index, (holder.manage ? "OM:" : "O:") + offer.id());
        }
        for (int slot = PAGE_SIZE; slot < SIZE; slot++) inventory.setItem(slot, pane());
        inventory.setItem(45, item(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of()));
        holder.actions.put(45, holder.manage ? "admin" : "home");
        inventory.setItem(49, item(Material.GOLD_INGOT, "所持金 " + service.gold(player) + " " + service.currencyName(), NamedTextColor.GOLD, List.of(
                text(offers.isEmpty() ? "いまは買取がありません" : holder.manage ? "やめたい買取をクリック" : "売りたいものをクリック", NamedTextColor.WHITE))));
    }

    private void renderOfferQty(Player player, Holder holder) {
        MarketBuyOffer offer = service.findOffer(holder.offerId);
        if (offer == null) {
            player.sendMessage(Component.text("[フリマ] その買取はもう終わっています。", NamedTextColor.RED));
            renderOffers(player, holder);
            return;
        }
        begin(holder, Screen.OFFER_QTY);
        Inventory inventory = holder.inventory;
        frame(inventory);
        int owned = service.count(player, offer.item());
        int cap = Math.max(1, offer.unlimited() ? owned : Math.min(owned, offer.remaining()));
        holder.offerQty = Math.max(1, Math.min(holder.offerQty, cap));
        long total = offer.unitPrice() * holder.offerQty;

        ItemStack shown = offer.item();
        ItemMeta meta = shown.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
            lore.add(text("- - - - - - - - - - - -", NamedTextColor.DARK_GRAY));
            lore.add(text("買取価格: " + offer.unitPrice() + " " + service.currencyName() + " (1個)", NamedTextColor.GOLD));
            lore.add(text("持っている数: " + owned, NamedTextColor.GRAY));
            meta.lore(lore);
            shown.setItemMeta(meta);
        }
        inventory.setItem(13, shown);
        inventory.setItem(20, stepButton(false, "-10", holder.offerQty - 10 >= 1));
        inventory.setItem(21, stepButton(false, "-1", holder.offerQty - 1 >= 1));
        inventory.setItem(22, item(Material.PAPER, holder.offerQty + " 個", NamedTextColor.WHITE, List.of(text("売る個数", NamedTextColor.GRAY))));
        inventory.setItem(23, stepButton(true, "+1", holder.offerQty + 1 <= cap));
        inventory.setItem(24, stepButton(true, "+10", holder.offerQty + 10 <= cap));
        boolean canMaxSell = holder.offerQty < cap;
        inventory.setItem(25, item(canMaxSell ? Material.EMERALD : Material.GRAY_DYE, "売れる最大", canMaxSell ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of(text("持っている数 (買取の残りの範囲): " + cap + " 個", NamedTextColor.GRAY))));
        inventory.setItem(31, item(Material.GOLD_INGOT, "受け取り " + total + " " + service.currencyName(), NamedTextColor.GOLD, List.of(
                text("単価 " + offer.unitPrice() + " × " + holder.offerQty, NamedTextColor.GRAY))));
        boolean ok = owned >= holder.offerQty && owned > 0;
        inventory.setItem(40, item(ok ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "売る", ok ? NamedTextColor.GREEN : NamedTextColor.GRAY, List.of(
                text(ok ? "クリックで売却" : "持っていません", ok ? NamedTextColor.WHITE : NamedTextColor.RED))));
        inventory.setItem(42, item(Material.RED_CONCRETE, "やめる", NamedTextColor.RED, List.of(text("一覧に戻る", NamedTextColor.GRAY))));
        if (holder.offerQty - 10 >= 1) holder.actions.put(20, "q:-10");
        if (holder.offerQty - 1 >= 1) holder.actions.put(21, "q:-1");
        if (holder.offerQty + 1 <= cap) holder.actions.put(23, "q:+1");
        if (holder.offerQty + 10 <= cap) holder.actions.put(24, "q:+10");
        if (canMaxSell) holder.actions.put(25, "q:max");
        holder.actions.put(40, "sell");
        holder.actions.put(42, "offers");
    }

    private void renderOfferEdit(Player player, Holder holder) {
        begin(holder, Screen.OFFER_EDIT);
        Inventory inventory = holder.inventory;
        frame(inventory);
        holder.price = Math.max(1, Math.min(holder.price, service.maxPrice()));
        holder.limit = Math.max(0, holder.limit);

        if (holder.template == null) {
            inventory.setItem(4, item(Material.PAPER, "買い取るアイテムを選ぶ", NamedTextColor.YELLOW, List.of(
                    text("下の自分の持ち物の中から、アイテムをクリック", NamedTextColor.GRAY))));
        } else {
            inventory.setItem(4, holder.template.clone());
        }
        settingRow(player, inventory, holder, 1, "price", Material.GOLD_INGOT, "買取単価 " + holder.price + " " + service.currencyName(),
                List.of(text("1個あたりの買取価格", NamedTextColor.GRAY)), new int[]{1, 10, 100});
        settingRow(player, inventory, holder, 2, "limit", Material.HOPPER, holder.limit == 0 ? "買取の上限 なし" : "買取の上限 " + holder.limit + " 個",
                List.of(text("これだけ買い取ったら終わり (0 は無制限)", NamedTextColor.GRAY)), new int[]{1, 10, 100});
        for (int slot = 45; slot < SIZE; slot++) inventory.setItem(slot, pane());
        inventory.setItem(45, item(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of()));
        boolean ready = holder.template != null;
        inventory.setItem(51, item(ready ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "買取を始める", ready ? NamedTextColor.GREEN : NamedTextColor.GRAY, List.of(
                text(ready ? "クリックで、この値段で買い取る" : "アイテムを選んでください", ready ? NamedTextColor.WHITE : NamedTextColor.RED))));
        holder.actions.put(45, "admin");
        holder.actions.put(51, "create");
    }

    private void renderAdmin(Player player, Holder holder) {
        if (!player.hasPermission(MarketService.ADMIN_PERMISSION)) {
            renderHome(player, holder);
            return;
        }
        begin(holder, Screen.ADMIN);
        holder.manage = false;
        holder.admin = false;
        Inventory inventory = holder.inventory;
        frame(inventory);
        inventory.setItem(4, item(Material.NETHER_STAR, "運営メニュー (iruuRPG)", NamedTextColor.LIGHT_PURPLE, List.of(
                text("プレイヤーには iruuRPG の名前で見えます", NamedTextColor.GRAY))));
        inventory.setItem(20, item(Material.GOLD_INGOT, "iruuRPG として出品", NamedTextColor.GOLD, List.of(
                text("手数料なし。在庫を無限にもできる", NamedTextColor.GRAY))));
        inventory.setItem(22, item(Material.GOLD_BLOCK, "買取を始める", NamedTextColor.YELLOW, List.of(
                text("決まった値段で、プレイヤーから買い取る", NamedTextColor.GRAY))));
        inventory.setItem(24, item(Material.HOPPER, "買取の管理", NamedTextColor.AQUA, List.of(
                text("買取の一覧。クリックでやめる", NamedTextColor.GRAY), text("買取中: " + service.offers().size() + " 件", NamedTextColor.YELLOW))));
        inventory.setItem(29, item(Material.CHEST, "全出品の管理", NamedTextColor.RED, List.of(
                text("全員の出品の一覧。クリックで取り下げ", NamedTextColor.GRAY), text("出品中: " + service.all().size() + " 件", NamedTextColor.YELLOW))));
        inventory.setItem(31, item(Material.CHEST_MINECART, "売上・在庫の回収", NamedTextColor.GREEN, List.of(
                text("iruuRPG の売上: " + service.pendingProceeds(MarketService.SYSTEM_ID) + " " + service.currencyName(), NamedTextColor.GOLD),
                text("買い取った・売れ残った品: " + service.pendingReturnStacks(MarketService.SYSTEM_ID) + " 組", NamedTextColor.AQUA))));
        inventory.setItem(45, item(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of()));
        holder.actions.put(20, "house-sell");
        holder.actions.put(22, "offer-new");
        holder.actions.put(24, "offer-manage");
        holder.actions.put(29, "listing-manage");
        holder.actions.put(31, "house");
        holder.actions.put(45, "home");
    }

    private void renderHouse(Player player, Holder holder) {
        begin(holder, Screen.HOUSE);
        Inventory inventory = holder.inventory;
        frame(inventory);
        long money = service.pendingProceeds(MarketService.SYSTEM_ID);
        int stacks = service.pendingReturnStacks(MarketService.SYSTEM_ID);
        inventory.setItem(4, item(Material.NETHER_STAR, "iruuRPG の売上・在庫", NamedTextColor.LIGHT_PURPLE, List.of()));
        inventory.setItem(22, item(Material.CHEST_MINECART, "回収する", NamedTextColor.GREEN, List.of(
                text("売上: " + money + " " + service.currencyName() + " (自分の所持金に入る)", NamedTextColor.GOLD),
                text("品物: " + stacks + " 組 (自分の持ち物に入る分だけ)", NamedTextColor.AQUA),
                text("クリックで回収", NamedTextColor.GRAY))));
        inventory.setItem(45, item(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of()));
        holder.actions.put(22, "claimhouse");
        holder.actions.put(45, "admin");
    }

    private void onOffersClick(Player player, Holder holder, String action) {
        if (action.equals("home")) {
            renderHome(player, holder);
        } else if (action.equals("admin")) {
            renderAdmin(player, holder);
        } else if (action.startsWith("O:")) {
            holder.offerId = Long.parseLong(action.substring(2));
            holder.offerQty = 1;
            renderOfferQty(player, holder);
        } else if (action.startsWith("OM:")) {
            String error = service.removeOffer(player, Long.parseLong(action.substring(3)));
            if (error != null) player.sendMessage(Component.text("[フリマ] " + error, NamedTextColor.RED));
            renderOffers(player, holder);
        }
    }

    private void onOfferQtyClick(Player player, Holder holder, String action) {
        MarketBuyOffer offer = service.findOffer(holder.offerId);
        switch (action) {
            case "offers" -> renderOffers(player, holder);
            case "sell" -> {
                String error = service.sellToHouse(player, holder.offerId, holder.offerQty);
                if (error != null) {
                    player.sendMessage(Component.text("[フリマ] " + error, NamedTextColor.RED));
                    renderOfferQty(player, holder);
                } else {
                    player.sendMessage(Component.text("[フリマ] 売った! (" + holder.offerQty + " 個)", NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
                    renderOffers(player, holder);
                }
            }
            case "q:max" -> {
                if (offer != null) {
                    int owned = service.count(player, offer.item());
                    holder.offerQty = Math.max(1, offer.unlimited() ? owned : Math.min(owned, offer.remaining()));
                }
                renderOfferQty(player, holder);
            }
            default -> {
                if (action.startsWith("q:")) holder.offerQty += Integer.parseInt(action.substring(2).replace("+", ""));
                renderOfferQty(player, holder);
            }
        }
    }

    private void onOfferEditClick(Player player, Holder holder, String action, ClickType click) {
        if (action.equals("admin")) {
            renderAdmin(player, holder);
        } else if (action.equals("create")) {
            String error = service.addOffer(player, holder.template, holder.price, holder.limit == 0 ? -1 : holder.limit);
            if (error != null) {
                player.sendMessage(Component.text("[フリマ] " + error, NamedTextColor.RED));
                renderOfferEdit(player, holder);
            } else {
                player.sendMessage(Component.text("[フリマ] 買取を始めた。", NamedTextColor.GREEN));
                holder.template = null;
                renderAdmin(player, holder);
            }
        } else if (action.startsWith("adj:")) {
            String[] parts = action.split(":");
            long delta = allowedDelta(player, holder, parts[1], Long.parseLong(parts[2]), click);
            if (parts[1].equals("price")) holder.price += delta;
            else if (parts[1].equals("limit")) holder.limit += (int) delta;
            renderOfferEdit(player, holder);
        }
    }

    private void onAdminClick(Player player, Holder holder, String action) {
        if (!player.hasPermission(MarketService.ADMIN_PERMISSION)) return;
        switch (action) {
            case "home" -> renderHome(player, holder);
            case "house-sell" -> {
                holder.admin = true;
                holder.infinite = true;
                holder.template = null;
                renderSell(player, holder);
            }
            case "offer-new" -> {
                holder.template = null;
                holder.price = 100;
                holder.limit = 0;
                renderOfferEdit(player, holder);
            }
            case "offer-manage" -> {
                holder.manage = true;
                renderOffers(player, holder);
            }
            case "listing-manage" -> {
                holder.manage = true;
                holder.page = 0;
                renderBrowse(player, holder);
            }
            case "house" -> renderHouse(player, holder);
            default -> {
            }
        }
    }

    // ---- clicks -------------------------------------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId.equals(player.getUniqueId())) return;

        int top = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() >= top) {
            // clicking an item in the own inventory picks it to sell
            if (holder.screen == Screen.SELL || holder.screen == Screen.OFFER_EDIT) selectItem(player, holder, event.getCurrentItem());
            return;
        }

        String action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);

        switch (holder.screen) {
            case HOME -> {
                switch (action) {
                    case "browse" -> {
                        holder.manage = false;
                        renderBrowse(player, holder);
                    }
                    case "sell" -> {
                        holder.admin = false;
                        holder.infinite = false;
                        renderSell(player, holder);
                    }
                    case "offers" -> {
                        holder.manage = false;
                        renderOffers(player, holder);
                    }
                    case "admin" -> {
                        if (player.hasPermission(MarketService.ADMIN_PERMISSION)) renderAdmin(player, holder);
                    }
                    case "mine" -> renderMine(player, holder);
                    case "main" -> {
                        if (mainMenu != null) mainMenu.open(player);
                    }
                    default -> {
                    }
                }
            }
            case BROWSE -> {
                if (action.startsWith("L:")) {
                    holder.buyId = Long.parseLong(action.substring(2));
                    holder.buyQty = 1;
                    renderBuy(player, holder);
                } else if (action.startsWith("M:")) {
                    String error = service.removeListing(player, Long.parseLong(action.substring(2)));
                    if (error != null) player.sendMessage(Component.text("[フリマ] " + error, NamedTextColor.RED));
                    renderBrowse(player, holder);
                } else {
                    switch (action) {
                        case "home" -> renderHome(player, holder);
                        case "admin" -> renderAdmin(player, holder);
                        case "prev" -> {
                            holder.page--;
                            renderBrowse(player, holder);
                        }
                        case "next" -> {
                            holder.page++;
                            renderBrowse(player, holder);
                        }
                        case "sort" -> {
                            holder.sort = Sort.values()[(holder.sort.ordinal() + 1) % Sort.values().length];
                            holder.page = 0;
                            renderBrowse(player, holder);
                        }
                        default -> {
                        }
                    }
                }
            }
            case BUY -> onBuyClick(player, holder, action);
            case SELL -> onSellClick(player, holder, action, event.getClick());
            case OFFERS -> onOffersClick(player, holder, action);
            case OFFER_QTY -> onOfferQtyClick(player, holder, action);
            case OFFER_EDIT -> onOfferEditClick(player, holder, action, event.getClick());
            case ADMIN -> onAdminClick(player, holder, action);
            case HOUSE -> {
                if (action.equals("claimhouse")) {
                    service.claimHouse(player);
                    renderHouse(player, holder);
                } else if (action.equals("admin")) {
                    renderAdmin(player, holder);
                }
            }
            case MINE -> {
                if (action.startsWith("c:")) {
                    String error = service.cancel(player, Long.parseLong(action.substring(2)));
                    if (error != null) player.sendMessage(Component.text("[フリマ] " + error, NamedTextColor.RED));
                    else player.sendMessage(Component.text("[フリマ] 出品を取り下げた。残りは返却品に入っている。", NamedTextColor.YELLOW));
                    renderMine(player, holder);
                } else if (action.equals("claim")) {
                    service.claim(player);
                    renderMine(player, holder);
                } else if (action.equals("home")) {
                    renderHome(player, holder);
                }
            }
        }
    }

    private void onBuyClick(Player player, Holder holder, String action) {
        MarketListing listing = service.find(holder.buyId);
        switch (action) {
            case "browse" -> renderBrowse(player, holder);
            case "buy" -> {
                String error = service.buy(player, holder.buyId, holder.buyQty);
                if (error != null) {
                    player.sendMessage(Component.text("[フリマ] " + error, NamedTextColor.RED));
                    renderBuy(player, holder);
                } else {
                    player.sendMessage(Component.text("[フリマ] 買った! (" + holder.buyQty + " 個)", NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
                    renderBrowse(player, holder);
                }
            }
            case "q:max" -> {
                if (listing != null) {
                    long gold = service.gold(player);
                    long afford = listing.unitPrice() <= 0 ? listing.remaining() : gold / listing.unitPrice();
                    holder.buyQty = (int) Math.max(1, Math.min(listing.infinite() ? 64 * 9 : listing.remaining(), afford));
                }
                renderBuy(player, holder);
            }
            default -> {
                if (action.startsWith("q:")) holder.buyQty += Integer.parseInt(action.substring(2).replace("+", ""));
                renderBuy(player, holder);
            }
        }
    }

    private void onSellClick(Player player, Holder holder, String action, ClickType click) {
        if (action.equals("home")) {
            renderHome(player, holder);
        } else if (action.equals("admin")) {
            renderAdmin(player, holder);
        } else if (action.equals("inf")) {
            holder.infinite = !holder.infinite;
            renderSell(player, holder);
        } else if (action.equals("list")) {
            String error = holder.admin
                    ? service.listAsHouse(player, holder.template, holder.qty, holder.price, holder.delay, holder.duration, holder.infinite)
                    : service.list(player, holder.template, holder.qty, holder.price, holder.delay, holder.duration);
            if (error != null) {
                player.sendMessage(Component.text("[フリマ] " + error, NamedTextColor.RED));
                renderSell(player, holder);
            } else {
                player.sendMessage(Component.text("[フリマ] 出品した。", NamedTextColor.GREEN));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                holder.template = null;
                if (holder.admin) renderAdmin(player, holder); else renderMine(player, holder);
            }
        } else if (action.startsWith("adj:")) {
            String[] parts = action.split(":");
            long delta = allowedDelta(player, holder, parts[1], Long.parseLong(parts[2]), click);
            switch (parts[1]) {
                case "price" -> holder.price += delta;
                case "qty" -> holder.qty += (int) delta;
                case "delay" -> holder.delay += (int) delta;
                case "duration" -> holder.duration += (int) delta;
                default -> {
                }
            }
            renderSell(player, holder);
        }
    }

    /** The change for a click: the click's multiplier (x1 / x10 / x100) if allowed, else the largest smaller one that is; 0 if none. */
    private long allowedDelta(Player player, Holder holder, String key, long base, ClickType click) {
        long wanted = click.isShiftClick() ? 100 : click.isRightClick() ? 10 : 1;
        for (long multiplier : new long[]{wanted, 10, 1}) {
            if (multiplier > wanted) continue;
            if (adjustAllowed(player, holder, key, base * multiplier)) return base * multiplier;
        }
        return 0;
    }

    private void selectItem(Player player, Holder holder, ItemStack clicked) {
        if (clicked == null || clicked.getType().isAir()) return;
        if (!sellable.test(clicked)) {
            player.sendMessage(Component.text("[フリマ] それは出品できません。", NamedTextColor.RED));
            return;
        }
        holder.template = clicked.clone();
        holder.template.setAmount(1);
        holder.qty = 1;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
        if (holder.screen == Screen.OFFER_EDIT) renderOfferEdit(player, holder); else renderSell(player, holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    // ---- items ----------------------------------------------------------------------------------------------------------

    private void begin(Holder holder, Screen screen) {
        holder.screen = screen;
        holder.actions.clear();
        holder.inventory.clear();
    }

    private int availableCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (MarketListing listing : service.all()) {
            if (!listing.isExpired(now)) count++;
        }
        return count;
    }

    private ItemStack listingItem(MarketListing listing, long now, UUID viewer) {
        ItemStack stack = listing.item();
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), listing.remaining())));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
        lore.add(text("- - - - - - - - - - - -", NamedTextColor.DARK_GRAY));
        lore.add(text("出品者: " + listing.sellerName() + (listing.sellerId().equals(viewer) ? " (自分)" : ""), NamedTextColor.GRAY));
        lore.add(text("単価: " + listing.unitPrice() + " " + service.currencyName(), NamedTextColor.GOLD));
        lore.add(text(listing.infinite() ? "在庫: 無限 (運営)" : "残り: " + listing.remaining() + " 個", NamedTextColor.YELLOW));
        if (!listing.isAvailable(now)) {
            lore.add(text("あと " + remaining(listing.availableAt() - now) + " で購入できる", NamedTextColor.RED));
            lore.add(text("購入できる期間: " + remaining(listing.expiresAt() - listing.availableAt()), NamedTextColor.GRAY));
        } else {
            lore.add(text("購入できる残り時間: " + remaining(listing.expiresAt() - now), NamedTextColor.GREEN));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack goldItem(Player player) {
        return item(Material.GOLD_INGOT, "所持金 " + service.gold(player) + " " + service.currencyName(), NamedTextColor.GOLD, List.of());
    }

    /**
     * Whether changing a setting by {@code delta} stays inside what is possible: inside the limits, within the
     * number of items the player has and (when it raises the fee) within the gold the player has.
     */
    private boolean adjustAllowed(Player player, Holder holder, String key, long delta) {
        long price = holder.price;
        int qty = holder.qty;
        int delay = holder.delay;
        int duration = holder.duration;
        switch (key) {
            case "price" -> price += delta;
            case "qty" -> qty += (int) delta;
            case "delay" -> delay += (int) delta;
            case "duration" -> duration += (int) delta;
            case "limit" -> {
                return holder.limit + delta >= 0;
            }
            default -> {
                return false;
            }
        }
        if (price < 1 || price > service.maxPrice()) return false;
        if (holder.screen == Screen.OFFER_EDIT) return true;

        boolean house = holder.admin && holder.infinite;
        int owned = holder.template == null ? 0 : service.count(player, holder.template);
        int maxQty = house ? service.maxQuantity() : Math.min(owned, service.maxQuantity());
        if (qty < 1 || qty > Math.max(1, maxQty)) return false;
        if (delay < 0 || delay > service.maxDelayHours()) return false;
        if (duration < 1 || duration > service.maxDurationHours()) return false;
        if (delta > 0 && !holder.admin) {
            return service.fee(price, qty, delay, duration).total() <= service.gold(player);
        }
        return true;
    }

    /** +/- button: green / red while it can be used, grey (and dead) when it would go past what is possible. */
    private ItemStack stepButton(boolean plus, String label, boolean allowed) {
        if (allowed) return step(plus, label);
        return item(Material.GRAY_STAINED_GLASS_PANE, label, NamedTextColor.DARK_GRAY, List.of(text("これ以上は選べません", NamedTextColor.RED)));
    }

    private ItemStack step(boolean plus, String label) {
        return item(plus ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, label, plus ? NamedTextColor.GREEN : NamedTextColor.RED, List.of());
    }

    private void frame(Inventory inventory) {
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) inventory.setItem(slot, pane());
        }
    }

    private ItemStack pane() {
        ItemStack stack = new ItemStack(FRAME);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String hours(int hours) {
        if (hours >= 24) return (hours / 24) + "日" + (hours % 24 == 0 ? "" : (hours % 24) + "時間");
        return hours + "時間";
    }

    private static String remaining(long millis) {
        long minutes = Math.max(1, (millis + 59_999) / 60_000);
        if (minutes >= 24 * 60) return (minutes / (24 * 60)) + "日" + ((minutes / 60) % 24) + "時間";
        if (minutes >= 60) return (minutes / 60) + "時間" + (minutes % 60) + "分";
        return minutes + "分";
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
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final class Holder implements MenuHolder {
        private final UUID ownerId;
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;
        private Screen screen = Screen.HOME;
        private int page;
        private Sort sort = Sort.NEWEST;
        private long buyId;
        private int buyQty = 1;
        private ItemStack template;
        private int qty = 1;
        private long price = 100;
        private int delay;
        private int duration = 24;
        private boolean admin;     // selling as "iruuRPG"
        private boolean infinite;  // operator listing that never runs out
        private boolean manage;    // operator manage mode in the list screens
        private long offerId;
        private int offerQty = 1;
        private int limit;         // buy offer limit, 0 = none

        private Holder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
