package net.tkgon.mc.iruuRPG.trade;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.tkgon.mc.iruuRPG.gui.MenuHolder;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Trading between two players: /trade <player> sends a request, the other accepts, and both get a chest screen.
 * Each side picks items from their own inventory (and gold) which the other side sees live. Both press their ready
 * light; once both are lit, both press confirm and the trade happens. Any change to an offer turns both lights off.
 *
 * Offered items stay in the owner's inventory until the trade happens (nothing is held by the plugin, so nothing
 * can be lost on a crash); they are checked again and moved at the moment of the trade.
 */
public final class TradeService implements Listener {

    private static final int SIZE = 54;
    private static final int OFFER_SLOTS = 16;
    private static final long REQUEST_MILLIS = 60_000L;
    /** How long a typed gold amount is waited for. */
    private static final long INPUT_MILLIS = 60_000L;

    // own side (left), seen by each player
    private static final int GOLD_SHOW = 37;  // click: type the amount in the chat
    private static final int GOLD_ZERO = 38;
    private static final int MY_HEAD = 45;
    private static final int MY_LIGHT = 46;
    private static final int MY_CONFIRM = 47;
    private static final int CANCEL = 48;
    private static final int STATUS = 49;
    // partner side (right)
    private static final int PARTNER_GOLD = 42;
    private static final int PARTNER_CONFIRM = 51;
    private static final int PARTNER_LIGHT = 52;
    private static final int PARTNER_HEAD = 53;

    private enum Phase { EDIT, CONFIRM, DONE }

    /** One offered stack: an inventory slot of the owner and how many from it. */
    private record Entry(int slot, ItemStack snapshot, int amount) {
    }

    private static final class Side {
        private final UUID playerId;
        private final List<Entry> entries = new ArrayList<>();
        private long gold;
        private boolean ready;
        private boolean confirmed;
        private Inventory view;
        /** Set while the player is typing a gold amount: the screen is closed on purpose and the trade goes on. */
        private volatile GoldInput input;

        private Side(UUID playerId) {
            this.playerId = playerId;
        }

        private Player player() {
            return Bukkit.getPlayer(playerId);
        }
    }

    private static final class Session {
        private final Side a;
        private final Side b;
        private Phase phase = Phase.EDIT;

        private Session(Side a, Side b) {
            this.a = a;
            this.b = b;
        }

        private Side side(UUID id) {
            return a.playerId.equals(id) ? a : b;
        }

        private Side other(Side side) {
            return side == a ? b : a;
        }
    }

    private record GoldInput(long expiresAt) {
    }

    private record Holder(Session session, Side side) implements MenuHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return side.view;
        }
    }

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final Predicate<ItemStack> tradable;
    /** target -> (requester -> time the request ends) */
    private final Map<UUID, Map<UUID, Long>> requests = new HashMap<>();
    /** Read from the async chat thread too (typed gold amounts), so it must be thread-safe. */
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public TradeService(JavaPlugin plugin, PlayerProfileManager profileManager, Predicate<ItemStack> tradable) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.tradable = tradable;
    }

    private String currency() {
        return plugin.getConfig().getString("quest.currency-name", "ゴールド");
    }

    // ---- requests -----------------------------------------------------------------------------------------------

    public void request(Player from, Player to) {
        if (from.equals(to)) {
            from.sendMessage(Component.text("[トレード] 自分とはトレードできません。", NamedTextColor.RED));
            return;
        }
        if (sessions.containsKey(from.getUniqueId()) || sessions.containsKey(to.getUniqueId())) {
            from.sendMessage(Component.text("[トレード] どちらかが、すでにトレード中です。", NamedTextColor.RED));
            return;
        }
        // the other side already asked us: start right away
        if (pending(from.getUniqueId(), to.getUniqueId())) {
            accept(from, to);
            return;
        }
        requests.computeIfAbsent(to.getUniqueId(), key -> new HashMap<>()).put(from.getUniqueId(), System.currentTimeMillis() + REQUEST_MILLIS);
        from.sendMessage(Component.text("[トレード] " + to.getName() + " にトレードを申し込んだ。(60秒)", NamedTextColor.YELLOW));
        to.sendMessage(Component.text("[トレード] " + from.getName() + " からトレードの申し込み ", NamedTextColor.YELLOW)
                .append(Component.text("[受ける]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/trade accept " + from.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("クリックでトレード画面を開く"))))
                .append(Component.text(" "))
                .append(Component.text("[断る]", NamedTextColor.RED, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/trade deny " + from.getName()))));
        to.playSound(to.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);
    }

    private boolean pending(UUID target, UUID requester) {
        Map<UUID, Long> mine = requests.get(target);
        if (mine == null) return false;
        Long until = mine.get(requester);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            mine.remove(requester);
            return false;
        }
        return true;
    }

    /** {@code target} accepts the request of {@code requester}. */
    public void accept(Player target, Player requester) {
        if (requester == null || !requester.isOnline() || !pending(target.getUniqueId(), requester.getUniqueId())) {
            target.sendMessage(Component.text("[トレード] 受けられる申し込みがありません。(期限切れかもしれません)", NamedTextColor.RED));
            return;
        }
        if (sessions.containsKey(target.getUniqueId()) || sessions.containsKey(requester.getUniqueId())) {
            target.sendMessage(Component.text("[トレード] どちらかが、すでにトレード中です。", NamedTextColor.RED));
            return;
        }
        requests.get(target.getUniqueId()).remove(requester.getUniqueId());
        Session session = new Session(new Side(requester.getUniqueId()), new Side(target.getUniqueId()));
        sessions.put(requester.getUniqueId(), session);
        sessions.put(target.getUniqueId(), session);
        open(session, session.a);
        open(session, session.b);
    }

    public void deny(Player target, Player requester) {
        Map<UUID, Long> mine = requests.get(target.getUniqueId());
        if (requester == null || mine == null || mine.remove(requester.getUniqueId()) == null) {
            target.sendMessage(Component.text("[トレード] 断れる申し込みがありません。", NamedTextColor.RED));
            return;
        }
        target.sendMessage(Component.text("[トレード] 断った。", NamedTextColor.GRAY));
        requester.sendMessage(Component.text("[トレード] " + target.getName() + " に断られた。", NamedTextColor.GRAY));
    }

    /** The only requester waiting on {@code target}, if exactly one. */
    public Player singleRequester(Player target) {
        Map<UUID, Long> mine = requests.get(target.getUniqueId());
        if (mine == null) return null;
        mine.values().removeIf(until -> until < System.currentTimeMillis());
        if (mine.size() != 1) return null;
        return Bukkit.getPlayer(mine.keySet().iterator().next());
    }

    // ---- screen --------------------------------------------------------------------------------------------------

    private void open(Session session, Side side) {
        Player player = side.player();
        Player partner = session.other(side).player();
        if (player == null || partner == null) return;
        side.view = Bukkit.createInventory(new Holder(session, side), SIZE, Component.text("トレード: " + partner.getName()));
        render(session, side);
        player.openInventory(side.view);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    private void renderBoth(Session session) {
        render(session, session.a);
        render(session, session.b);
    }

    private void render(Session session, Side side) {
        Inventory view = side.view;
        if (view == null) return;
        Side partner = session.other(side);
        Player me = side.player();
        Player them = partner.player();
        view.clear();

        // divider down the middle and under the offers
        for (int row = 0; row < 6; row++) view.setItem(row * 9 + 4, pane(Material.GRAY_STAINED_GLASS_PANE));
        for (int slot : new int[]{36, 39, 40, 41, 43, 44, 50}) view.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE));

        for (int index = 0; index < side.entries.size(); index++) {
            view.setItem(offerSlot(index, false), offered(side.entries.get(index), true));
        }
        for (int index = 0; index < partner.entries.size(); index++) {
            view.setItem(offerSlot(index, true), offered(partner.entries.get(index), false));
        }

        long myGold = me == null ? 0 : profileManager.getOrCreate(me).currency();
        view.setItem(GOLD_SHOW, button(Material.GOLD_INGOT, "出す" + currency() + ": " + side.gold, NamedTextColor.GOLD, List.of(
                text("所持: " + myGold + " " + currency(), NamedTextColor.GRAY),
                text("クリックして、チャットに数字を入力", NamedTextColor.YELLOW))));
        view.setItem(GOLD_ZERO, button(Material.BUCKET, currency() + "を0に", NamedTextColor.GRAY, List.of()));
        view.setItem(PARTNER_GOLD, button(Material.GOLD_INGOT, "相手が出す" + currency() + ": " + partner.gold, NamedTextColor.GOLD, List.of()));

        view.setItem(MY_HEAD, head(me, "自分"));
        view.setItem(PARTNER_HEAD, head(them, "相手"));
        view.setItem(MY_LIGHT, light(side, true, session.phase));
        view.setItem(PARTNER_LIGHT, light(partner, false, session.phase));
        view.setItem(MY_CONFIRM, confirmButton(side, true, session.phase));
        view.setItem(PARTNER_CONFIRM, confirmButton(partner, false, session.phase));
        view.setItem(CANCEL, button(Material.BARRIER, "トレードをやめる", NamedTextColor.RED, List.of(text("画面を閉じてもやめられる", NamedTextColor.GRAY))));
        view.setItem(STATUS, button(Material.OAK_SIGN, session.phase == Phase.CONFIRM ? "確定待ち" : "準備中", NamedTextColor.WHITE, List.of(
                text("1. 持ち物をクリックして出す (左: 全部 / 右: 1個ずつ)", NamedTextColor.GRAY),
                text("   出したものをクリックで戻す", NamedTextColor.GRAY),
                text("2. 揃ったら、自分のライトを押して準備完了", NamedTextColor.GRAY),
                text("3. 両方のライトが点いたら、お互いに確定", NamedTextColor.GRAY),
                text("中身や" + currency() + "を変えると、両方のライトが消える", NamedTextColor.YELLOW))));
    }

    private static int offerSlot(int index, boolean partnerSide) {
        return (index / 4) * 9 + (index % 4) + (partnerSide ? 5 : 0);
    }

    private ItemStack offered(Entry entry, boolean mine) {
        ItemStack stack = entry.snapshot().clone();
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), entry.amount())));
        if (mine) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
                lore.add(text("- - - - - - - - - - - -", NamedTextColor.DARK_GRAY));
                lore.add(text("左クリック: 全部戻す / 右クリック: 1個戻す", NamedTextColor.GRAY));
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    /** The ready light: a lamp that lights up when its owner is ready. */
    private ItemStack light(Side side, boolean mine, Phase phase) {
        if (side.ready) {
            ItemStack stack = button(Material.GLOWSTONE, (mine ? "自分" : "相手") + ": 準備完了", NamedTextColor.YELLOW,
                    List.of(text(mine && phase == Phase.EDIT ? "クリックで準備を取り消す" : mine ? "クリックで準備を取り消す (確定前に戻る)" : "", NamedTextColor.GRAY)));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                stack.setItemMeta(meta);
            }
            return stack;
        }
        return button(Material.REDSTONE_LAMP, (mine ? "自分" : "相手") + ": 準備中", NamedTextColor.GRAY,
                List.of(text(mine ? "揃ったらクリックで準備完了 (ライトが点く)" : "相手の準備を待っている", NamedTextColor.GRAY)));
    }

    private ItemStack confirmButton(Side side, boolean mine, Phase phase) {
        if (phase != Phase.CONFIRM) {
            return button(Material.GRAY_CONCRETE, (mine ? "" : "相手の") + "確定", NamedTextColor.DARK_GRAY, List.of(text("両方の準備が完了すると押せる", NamedTextColor.GRAY)));
        }
        if (side.confirmed) {
            return button(Material.EMERALD_BLOCK, (mine ? "確定した" : "相手: 確定した"), NamedTextColor.GREEN, List.of(text(mine ? "相手の確定を待っている" : "", NamedTextColor.GRAY)));
        }
        return button(mine ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE, mine ? "確定する" : "相手: 確定待ち", mine ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                List.of(text(mine ? "クリックで確定 (両方が確定するとトレード成立)" : "", NamedTextColor.GRAY)));
    }

    // ---- clicks ------------------------------------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.side().playerId.equals(player.getUniqueId())) return;
        Session session = holder.session();
        if (session.phase == Phase.DONE) return;
        Side side = holder.side();
        ClickType click = event.getClick();

        if (event.getRawSlot() >= SIZE) {
            // own inventory: offer the clicked stack (left: all, right: one more)
            if (event.getClickedInventory() instanceof PlayerInventory) offer(session, side, player, event.getSlot(), click.isRightClick());
            return;
        }

        int slot = event.getRawSlot();
        int column = slot % 9;
        int row = slot / 9;
        if (row < 4 && column < 4) {
            int index = row * 4 + column;
            if (index < side.entries.size()) takeBack(session, side, index, click.isRightClick());
            return;
        }
        switch (slot) {
            case GOLD_SHOW -> askForGold(session, side, player);
            case GOLD_ZERO -> {
                if (side.gold != 0) {
                    side.gold = 0;
                    changed(session);
                }
            }
            case MY_LIGHT -> toggleReady(session, side, player);
            case MY_CONFIRM -> confirm(session, side, player);
            case CANCEL -> player.closeInventory();
            default -> {
            }
        }
    }

    // ---- typed gold amount (chat) -----------------------------------------------------------------------------------------

    /** Closes the screen (the trade stays open) and waits for the player to type the amount in the chat. */
    private void askForGold(Session session, Side side, Player player) {
        GoldInput input = new GoldInput(System.currentTimeMillis() + INPUT_MILLIS);
        side.input = input;
        long max = profileManager.getOrCreate(player).currency();
        player.closeInventory();
        player.sendMessage(Component.text("[トレード] 出す" + currency() + "をチャットに入力してください (0〜" + max + ")。やめるときは「キャンセル」", NamedTextColor.YELLOW));
        player.showTitle(Title.title(Component.empty(), Component.text("出す" + currency() + "をチャットに入力 (0〜" + max + ")", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(10), Duration.ofMillis(500))));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (side.input == input) finishInput(session, side, player, "入力の時間が切れた。");
        }, INPUT_MILLIS / 50L + 5L);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        Side side = session.side(player.getUniqueId());
        GoldInput input = side.input;
        if (input == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (side.input != input || session.phase == Phase.DONE) return;
            answerGold(session, side, player, input, message);
        });
    }

    private void answerGold(Session session, Side side, Player player, GoldInput input, String message) {
        if (System.currentTimeMillis() > input.expiresAt()) {
            finishInput(session, side, player, "入力の時間が切れた。");
        } else if (message.equalsIgnoreCase("キャンセル") || message.equalsIgnoreCase("cancel")) {
            finishInput(session, side, player, "入力をやめた。");
        } else {
            long max = profileManager.getOrCreate(player).currency();
            String normalized = Normalizer.normalize(message, Normalizer.Form.NFKC).replace(",", "").replace(" ", "");
            long value;
            try {
                value = Long.parseLong(normalized);
            } catch (NumberFormatException exception) {
                finishInput(session, side, player, "数字で入力してください。(入力: " + message + ")");
                return;
            }
            if (value < 0 || value > max) {
                finishInput(session, side, player, currency() + "は 0〜" + max + " の範囲です。");
                return;
            }
            side.input = null;
            player.clearTitle();
            if (value != side.gold) {
                side.gold = value;
                changed(session);
            }
            open(session, side);
        }
    }

    /** Ends the typing without a new amount and opens the screen again. */
    private void finishInput(Session session, Side side, Player player, String reason) {
        side.input = null;
        if (!player.isOnline() || session.phase == Phase.DONE) return;

        player.clearTitle();
        player.sendMessage(Component.text("[トレード] " + reason, NamedTextColor.RED));
        open(session, side);
    }

    private void offer(Session session, Side side, Player player, int inventorySlot, boolean one) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(inventorySlot);
        if (stack == null || stack.getType().isAir()) return;
        if (!tradable.test(stack)) {
            player.sendMessage(Component.text("[トレード] それはトレードに出せません。", NamedTextColor.RED));
            return;
        }
        for (int index = 0; index < side.entries.size(); index++) {
            Entry entry = side.entries.get(index);
            if (entry.slot() != inventorySlot) continue;
            int amount = one ? Math.min(stack.getAmount(), entry.amount() + 1) : stack.getAmount();
            if (amount == entry.amount()) return;
            side.entries.set(index, new Entry(inventorySlot, stack.clone(), amount));
            changed(session);
            return;
        }
        if (side.entries.size() >= OFFER_SLOTS) {
            player.sendMessage(Component.text("[トレード] 一度に出せるのは " + OFFER_SLOTS + " 組までです。", NamedTextColor.RED));
            return;
        }
        side.entries.add(new Entry(inventorySlot, stack.clone(), one ? 1 : stack.getAmount()));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.2f);
        changed(session);
    }

    private void takeBack(Session session, Side side, int index, boolean one) {
        Entry entry = side.entries.get(index);
        if (one && entry.amount() > 1) {
            side.entries.set(index, new Entry(entry.slot(), entry.snapshot(), entry.amount() - 1));
        } else {
            side.entries.remove(index);
        }
        changed(session);
    }

    /** Any change to an offer: both lights go off, back to editing. */
    private void changed(Session session) {
        boolean wasReady = session.a.ready || session.b.ready;
        session.a.ready = false;
        session.b.ready = false;
        session.a.confirmed = false;
        session.b.confirmed = false;
        session.phase = Phase.EDIT;
        renderBoth(session);
        if (wasReady) {
            for (Side side : new Side[]{session.a, session.b}) {
                Player player = side.player();
                if (player != null) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            }
        }
    }

    private void toggleReady(Session session, Side side, Player player) {
        if (side.ready) {
            side.ready = false;
            session.a.confirmed = false;
            session.b.confirmed = false;
            session.phase = Phase.EDIT;
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 0.6f);
        } else {
            side.ready = true;
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 1.2f);
            if (session.other(side).ready) {
                session.phase = Phase.CONFIRM;
                for (Side each : new Side[]{session.a, session.b}) {
                    Player p = each.player();
                    if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
                }
            }
        }
        renderBoth(session);
    }

    private void confirm(Session session, Side side, Player player) {
        if (session.phase != Phase.CONFIRM || side.confirmed) return;
        side.confirmed = true;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        if (session.other(side).confirmed) {
            complete(session);
        } else {
            renderBoth(session);
        }
    }

    // ---- the trade -----------------------------------------------------------------------------------------------------

    private void complete(Session session) {
        Player a = session.a.player();
        Player b = session.b.player();
        if (a == null || b == null) {
            end(session, "相手がいなくなった。");
            return;
        }
        String problem = check(session.a, a);
        if (problem == null) problem = check(session.b, b);
        if (problem != null) {
            for (Player p : new Player[]{a, b}) p.sendMessage(Component.text("[トレード] " + problem + " 中身を見直して、もう一度準備してください。", NamedTextColor.RED));
            changed(session);
            return;
        }

        List<ItemStack> fromA = take(session.a, a);
        List<ItemStack> fromB = take(session.b, b);
        // written down before giving: adding to an inventory can change the amounts of the stacks passed in
        Component gaveA = summary(fromA, session.a.gold);
        Component gaveB = summary(fromB, session.b.gold);
        String logA = describe(fromA);
        String logB = describe(fromB);
        give(b, fromA);
        give(a, fromB);
        PlayerProfile profileA = profileManager.getOrCreate(a);
        PlayerProfile profileB = profileManager.getOrCreate(b);
        profileA.setCurrency(profileA.currency() - session.a.gold + session.b.gold);
        profileB.setCurrency(profileB.currency() - session.b.gold + session.a.gold);
        profileManager.save(a);
        profileManager.save(b);

        plugin.getLogger().info("trade: " + a.getName() + " gave " + logA + " + " + session.a.gold + "G to "
                + b.getName() + ", got " + logB + " + " + session.b.gold + "G");
        session.phase = Phase.DONE;
        for (Player p : new Player[]{a, b}) {
            boolean isA = p == a;
            Player partner = isA ? b : a;
            p.sendMessage(Component.text("[トレード] トレード成立! (相手: " + partner.getName() + ")", NamedTextColor.GREEN, TextDecoration.BOLD));
            p.sendMessage(Component.text("  渡した: ", NamedTextColor.GRAY).append(isA ? gaveA : gaveB));
            p.sendMessage(Component.text("  受け取った: ", NamedTextColor.GRAY).append(isA ? gaveB : gaveA));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.3f);
        }
        sessions.remove(a.getUniqueId());
        sessions.remove(b.getUniqueId());
        a.closeInventory();
        b.closeInventory();
    }

    /** The offered stacks and gold are still there (nothing moved since they were offered). */
    private String check(Side side, Player player) {
        if (profileManager.getOrCreate(player).currency() < side.gold) return player.getName() + " の" + currency() + "が足りない。";
        for (Entry entry : side.entries) {
            ItemStack now = player.getInventory().getItem(entry.slot());
            if (now == null || !now.isSimilar(entry.snapshot()) || now.getAmount() < entry.amount()) {
                return player.getName() + " の出したアイテムが変わった。";
            }
        }
        return null;
    }

    private List<ItemStack> take(Side side, Player player) {
        List<ItemStack> taken = new ArrayList<>();
        for (Entry entry : side.entries) {
            ItemStack now = player.getInventory().getItem(entry.slot());
            if (now == null) continue;
            ItemStack moved = now.clone();
            moved.setAmount(entry.amount());
            taken.add(moved);
            if (now.getAmount() <= entry.amount()) {
                player.getInventory().setItem(entry.slot(), null);
            } else {
                now.setAmount(now.getAmount() - entry.amount());
            }
        }
        return taken;
    }

    private void give(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            for (ItemStack rest : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
                player.sendMessage(Component.text("[トレード] 持ち物がいっぱいで、足元に落ちた。", NamedTextColor.YELLOW));
            }
        }
    }

    /** "Item ×3, Item ×1, 100 ゴールド" (the same item is added up; hover an item name to see it), or "なし". */
    private Component summary(List<ItemStack> items, long gold) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack item : items) {
            boolean added = false;
            for (ItemStack existing : merged) {
                if (existing.isSimilar(item)) {
                    existing.setAmount(existing.getAmount() + item.getAmount());
                    added = true;
                    break;
                }
            }
            if (!added) merged.add(item.clone());
        }
        List<Component> parts = new ArrayList<>();
        for (ItemStack item : merged) {
            parts.add(item.displayName().append(Component.text(" ×" + item.getAmount(), NamedTextColor.WHITE)));
        }
        if (gold > 0) parts.add(Component.text(String.format("%,d", gold) + " " + currency(), NamedTextColor.GOLD));
        if (parts.isEmpty()) return Component.text("なし", NamedTextColor.DARK_GRAY);
        return Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), parts);
    }

    private static String describe(List<ItemStack> items) {
        if (items.isEmpty()) return "nothing";
        List<String> parts = new ArrayList<>();
        for (ItemStack item : items) parts.add(item.getType().name() + "x" + item.getAmount());
        return String.join(",", parts);
    }

    /** Ends a trade without trading (closing the screen, leaving). */
    private void end(Session session, String reason) {
        if (session.phase == Phase.DONE) return;
        session.phase = Phase.DONE;
        for (Side side : new Side[]{session.a, session.b}) {
            sessions.remove(side.playerId);
            Player player = side.player();
            if (player == null) continue;
            player.sendMessage(Component.text("[トレード] トレードは中止された。" + reason, NamedTextColor.GRAY));
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) player.closeInventory();
                });
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        Session session = holder.session();
        if (session.phase == Phase.DONE) return;
        if (holder.side().input != null) return; // closed on purpose to type an amount
        end(session, "(" + event.getPlayer().getName() + " が画面を閉じた)");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        requests.remove(id);
        for (Map<UUID, Long> mine : requests.values()) mine.remove(id);
        Session session = sessions.get(id);
        if (session != null) end(session, "(" + event.getPlayer().getName() + " が退出した)");
    }

    // ---- items ------------------------------------------------------------------------------------------------------------

    private ItemStack head(Player player, String label) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skull && player != null) skull.setOwningPlayer(player);
        if (meta != null) {
            meta.displayName(text(label + ": " + (player == null ? "?" : player.getName()), NamedTextColor.WHITE));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack button(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> shown = new ArrayList<>();
            for (Component line : lore) {
                if (!line.equals(text("", NamedTextColor.GRAY))) shown.add(line);
            }
            meta.lore(shown);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
}
