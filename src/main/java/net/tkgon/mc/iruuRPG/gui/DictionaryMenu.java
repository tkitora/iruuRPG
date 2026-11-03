package net.tkgon.mc.iruuRPG.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.tkgon.mc.iruuRPG.item.Rarity;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.mob.RpgMobDefinition;
import net.tkgon.mc.iruuRPG.mob.RpgMobRegistry;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import net.tkgon.mc.iruuRPG.stat.StatType;
import net.tkgon.mc.iruuRPG.travel.PersonalPoints;
import net.tkgon.mc.iruuRPG.travel.TravelService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * (t254) The dictionary of the admins ({@code /iruurpg dictionary}, {@code /iruurpg dic}): every item of the game (filtered by kind and rarity, searched by
 * a word typed in the chat) to take into the inventory, every mob (filtered by where it lives or bosses only, searched) to spawn on the spot, every
 * fast-travel point to go to at once (no channel, no effects, no cost, found or not), and a page of the admin's own points: the place they stand added
 * under a typed name, gone to with a click, taken away with Shift+right click. Admins only; it changes nothing of the game but what it is asked to give,
 * spawn or go to.
 */
public final class DictionaryMenu implements Listener {

    private static final int SIZE = 54;
    private static final int PAGE = 45;
    private static final int BACK = 45;
    private static final int FILTER_A = 46;
    private static final int PREVIOUS = 47;
    private static final int FILTER_B = 48;
    private static final int SEARCH = 49;
    private static final int ADD = 50;
    private static final int NEXT = 51;
    private static final int COUNT = 52;
    private static final int OTHER = 53;
    private static final long INPUT_TIMEOUT_MILLIS = 60_000L;
    private static final String PREFIX = "[図鑑] ";

    /** The pages. */
    enum Page {
        HOME, ITEMS, MOBS, TRAVEL, PERSONAL
    }

    /** The kinds of item to show (the filter of the item page). */
    enum ItemKind {
        ALL("全部"), MELEE("武器 (近接)"), RANGE("武器 (遠距離)"), DEPLOY("武器 (設置)"), ARMOR("防具"), MATERIAL("素材"), OTHER("その他");

        final String label;

        ItemKind(String label) {
            this.label = label;
        }

        /** The kind an item is, by the words on it (物理 / 魔法 a weapon of its type, 防具, 素材, else other). */
        static ItemKind of(String kindText, String typeText) {
            if (kindText.equals("物理") || kindText.equals("魔法")) {
                return switch (typeText) {
                    case "近接" -> MELEE;
                    case "遠距離" -> RANGE;
                    case "設置" -> DEPLOY;
                    default -> OTHER;
                };
            }
            if (kindText.equals("防具")) return ARMOR;
            if (kindText.equals("素材")) return MATERIAL;
            return OTHER;
        }
    }

    private static final class Holder implements MenuHolder {
        private final UUID owner;
        private Inventory inventory;
        private Page page = Page.HOME;
        private int index;
        private ItemKind kind = ItemKind.ALL;
        /** The rarity shown (null: all). */
        private Rarity rarity;
        /** The mobs shown: "" all, "@boss" bosses only, "@none" none of an area, else an area. */
        private String area = "";
        /** The word searched for ("" none), on the item and mob pages. */
        private String query = "";
        /** What a slot of the page is: an item id, a mob id, a point id, or the place of a personal point. */
        private final Map<Integer, String> slots = new HashMap<>();

        private Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /** A word being typed in the chat: a search, or the name of a new point (where the admin stood then). */
    private record Pending(Holder holder, String what, Location at, long expiresAt) {
    }

    private final JavaPlugin plugin;
    private final RpgItemRegistry items;
    private final RpgItemFactory factory;
    private final RpgMobRegistry mobs;
    private final RpgMobService mobService;
    private final TravelService travel;
    private final PersonalPoints points;
    private final Supplier<Set<String>> bosses;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public DictionaryMenu(JavaPlugin plugin, RpgItemRegistry items, RpgItemFactory factory, RpgMobRegistry mobs, RpgMobService mobService, TravelService travel,
                          PersonalPoints points, Supplier<Set<String>> bosses) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.mobs = mobs;
        this.mobService = mobService;
        this.travel = travel;
        this.points = points;
        this.bosses = bosses;
    }

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        show(player, holder);
    }

    private void show(Player player, Holder holder) {
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text(title(holder.page)));
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    private static String title(Page page) {
        return switch (page) {
            case ITEMS -> "図鑑: アイテム";
            case MOBS -> "図鑑: mob";
            case TRAVEL -> "図鑑: ファストトラベル";
            case PERSONAL -> "図鑑: 自分の地点";
            default -> "図鑑 (管理者)";
        };
    }

    // ---- what the pages hold -------------------------------------------------------------------------------------------------

    /** The words of a search, made the same however typed (full width, case). Pure, for the tests. */
    static String normal(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("§.", "").trim();
    }

    /** Whether an id or a name has every word of {@code query} in it. Pure, for the tests. */
    static boolean matches(String query, String id, String name) {
        String in = normal(id) + " " + normal(name);
        for (String word : normal(query).split("\\s+")) if (!word.isEmpty() && !in.contains(word)) return false;
        return true;
    }

    private List<RpgItemDefinition> shownItems(Holder holder) {
        List<RpgItemDefinition> shown = new ArrayList<>();
        for (RpgItemDefinition definition : items.all().values()) {
            if (holder.kind != ItemKind.ALL && ItemKind.of(definition.kindText(), definition.typeText()) != holder.kind) continue;
            if (holder.rarity != null && definition.rarity() != holder.rarity) continue;
            if (!holder.query.isEmpty() && !matches(holder.query, definition.id(), definition.name())) continue;
            shown.add(definition);
        }
        shown.sort(Comparator.comparing((RpgItemDefinition definition) -> ItemKind.of(definition.kindText(), definition.typeText()))
                .thenComparingInt(RpgItemDefinition::requiredLevel).thenComparing(definition -> definition.rarity().tier()).thenComparing(RpgItemDefinition::id));
        return shown;
    }

    private List<RpgMobDefinition> shownMobs(Holder holder) {
        Set<String> boss = bosses.get();
        List<RpgMobDefinition> shown = new ArrayList<>();
        for (RpgMobDefinition definition : mobs.all().values()) {
            if (holder.area.equals("@boss") && !boss.contains(definition.id())) continue;
            if (holder.area.equals("@none") && !definition.areas().isEmpty()) continue;
            if (!holder.area.isEmpty() && !holder.area.startsWith("@") && !definition.inArea(holder.area)) continue;
            if (!holder.query.isEmpty() && !matches(holder.query, definition.id(), definition.name())) continue;
            shown.add(definition);
        }
        shown.sort(Comparator.comparingInt(RpgMobDefinition::level).thenComparing(RpgMobDefinition::id));
        return shown;
    }

    /** The areas the mobs live in, in order (for the filter). */
    private List<String> areas() {
        Set<String> areas = new TreeSet<>();
        for (RpgMobDefinition definition : mobs.all().values()) for (String area : definition.areas()) areas.add(area.toLowerCase(Locale.ROOT));
        return new ArrayList<>(areas);
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------------------

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.slots.clear();
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = PAGE; slot < SIZE; slot++) inventory.setItem(slot, frame);
        switch (holder.page) {
            case ITEMS -> renderItems(holder);
            case MOBS -> renderMobs(holder);
            case TRAVEL -> renderTravel(player, holder);
            case PERSONAL -> renderPersonal(player, holder);
            default -> renderHome(inventory);
        }
        if (holder.page != Page.HOME) {
            inventory.setItem(BACK, item(Material.ARROW, "戻る", NamedTextColor.YELLOW, List.of(text("図鑑の最初のページへ", NamedTextColor.GRAY))));
        } else {
            inventory.setItem(BACK, item(Material.BARRIER, "閉じる", NamedTextColor.RED, List.of()));
        }
    }

    private void renderHome(Inventory inventory) {
        inventory.setItem(20, item(Material.CHEST, "アイテム", NamedTextColor.GOLD, List.of(
                text("ゲームのアイテムを全部見る (種類・レア度で絞る、名前で検索)", NamedTextColor.GRAY),
                text("クリックで、手持ちに入れる", NamedTextColor.GRAY))));
        inventory.setItem(22, item(Material.ZOMBIE_HEAD, "mob", NamedTextColor.GOLD, List.of(
                text("mob を全部見る (いる場所・ボスで絞る、名前で検索)", NamedTextColor.GRAY),
                text("クリックで、目の前に出す", NamedTextColor.GRAY))));
        inventory.setItem(24, item(Material.ENDER_EYE, "ファストトラベル", NamedTextColor.GOLD, List.of(
                text("全部の地点へ、演出なし・費用なし・未発見でもすぐ飛ぶ", NamedTextColor.GRAY),
                text("自分だけの地点も (追加・削除)", NamedTextColor.GRAY))));
        inventory.setItem(31, item(Material.COMPASS, "自分の地点", NamedTextColor.GOLD, List.of(
                text("今いる場所を名前を付けて残し、いつでも飛ぶ (" + PersonalPoints.MAX + " か所まで)", NamedTextColor.GRAY))));
    }

    private void renderItems(Holder holder) {
        List<RpgItemDefinition> shown = shownItems(holder);
        int pages = pages(shown.size());
        holder.index = Math.max(0, Math.min(holder.index, pages - 1));
        for (int i = 0; i < PAGE && holder.index * PAGE + i < shown.size(); i++) {
            RpgItemDefinition definition = shown.get(holder.index * PAGE + i);
            ItemStack icon;
            try {
                icon = factory.create(definition);
            } catch (RuntimeException exception) {
                icon = new ItemStack(definition.material().isItem() ? definition.material() : Material.BARRIER);
            }
            List<Component> extra = new ArrayList<>();
            extra.add(Component.empty());
            extra.add(text("id: " + definition.id(), NamedTextColor.DARK_GRAY));
            extra.add(text(definition.unstackable() ? "クリック: 1 個もらう" : "左クリック: 1 個 / Shift+左クリック: 64 個もらう", NamedTextColor.YELLOW));
            holder.inventory.setItem(i, withLore(icon, extra));
            holder.slots.put(i, definition.id());
        }
        List<Component> kinds = new ArrayList<>();
        for (ItemKind kind : ItemKind.values()) kinds.add(text((kind == holder.kind ? "▶ " : "   ") + kind.label, kind == holder.kind ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        kinds.add(text("左クリック: 次 / 右クリック: 前", NamedTextColor.YELLOW));
        holder.inventory.setItem(FILTER_A, item(Material.HOPPER, "種類: " + holder.kind.label, NamedTextColor.AQUA, kinds));
        List<Component> rarities = new ArrayList<>();
        rarities.add(text((holder.rarity == null ? "▶ " : "   ") + "全部", holder.rarity == null ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        for (Rarity rarity : Rarity.values()) rarities.add(text((rarity == holder.rarity ? "▶ " : "   ") + rarity.displayName(), rarity == holder.rarity ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        rarities.add(text("左クリック: 次 / 右クリック: 前", NamedTextColor.YELLOW));
        holder.inventory.setItem(FILTER_B, item(Material.AMETHYST_SHARD, "レア度: " + (holder.rarity == null ? "全部" : holder.rarity.displayName()), NamedTextColor.AQUA, rarities));
        paging(holder, shown.size(), pages, true);
    }

    private void renderMobs(Holder holder) {
        List<RpgMobDefinition> shown = shownMobs(holder);
        Set<String> boss = bosses.get();
        int pages = pages(shown.size());
        holder.index = Math.max(0, Math.min(holder.index, pages - 1));
        for (int i = 0; i < PAGE && holder.index * PAGE + i < shown.size(); i++) {
            RpgMobDefinition definition = shown.get(holder.index * PAGE + i);
            Material egg = Material.matchMaterial(definition.entityType().name() + "_SPAWN_EGG");
            List<Component> lore = new ArrayList<>();
            lore.add(text("Lv " + definition.level() + " / HP " + Math.round(definition.stats().get(StatType.MAX_HP)) + (boss.contains(definition.id()) ? " / ボス" : ""), NamedTextColor.WHITE));
            lore.add(text("いる場所: " + (definition.areas().isEmpty() ? "なし" : String.join(", ", definition.areas())), NamedTextColor.GRAY));
            lore.add(text("id: " + definition.id() + " (" + definition.entityType().name().toLowerCase(Locale.ROOT) + ")", NamedTextColor.DARK_GRAY));
            lore.add(text("左クリック: 目の前に 1 体 / Shift+左クリック: 5 体", NamedTextColor.YELLOW));
            holder.inventory.setItem(i, item(egg != null && egg.isItem() ? egg : Material.ZOMBIE_SPAWN_EGG, definition.name(), boss.contains(definition.id()) ? NamedTextColor.RED : NamedTextColor.WHITE, lore));
            holder.slots.put(i, definition.id());
        }
        List<String> choices = new ArrayList<>(List.of("", "@boss", "@none"));
        choices.addAll(areas());
        List<Component> lore = new ArrayList<>();
        for (String choice : choices) lore.add(text((choice.equals(holder.area) ? "▶ " : "   ") + areaLabel(choice), choice.equals(holder.area) ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        lore.add(text("左クリック: 次 / 右クリック: 前", NamedTextColor.YELLOW));
        holder.inventory.setItem(FILTER_A, item(Material.NAME_TAG, "いる場所: " + areaLabel(holder.area), NamedTextColor.AQUA, lore));
        paging(holder, shown.size(), pages, true);
    }

    private static String areaLabel(String area) {
        return switch (area) {
            case "" -> "全部";
            case "@boss" -> "ボスだけ";
            case "@none" -> "場所なし";
            default -> area;
        };
    }

    private void renderTravel(Player player, Holder holder) {
        List<TravelService.Waypoint> all = travel.all();
        all.sort(Comparator.comparing(TravelService.Waypoint::name).thenComparing(TravelService.Waypoint::id));
        int pages = pages(all.size());
        holder.index = Math.max(0, Math.min(holder.index, pages - 1));
        for (int i = 0; i < PAGE && holder.index * PAGE + i < all.size(); i++) {
            TravelService.Waypoint point = all.get(holder.index * PAGE + i);
            boolean known = travel.known(player, point.id());
            holder.inventory.setItem(i, item(known ? Material.ENDER_EYE : Material.ENDER_PEARL, point.name(), NamedTextColor.AQUA, List.of(
                    text(point.world() + " " + Math.round(point.x()) + ", " + Math.round(point.y()) + ", " + Math.round(point.z()), NamedTextColor.GRAY),
                    text(known ? "発見済み" : "まだ見つけていない (図鑑からは飛べる)", known ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                    text("id: " + point.id(), NamedTextColor.DARK_GRAY),
                    text("クリック: すぐ飛ぶ (演出なし・費用なし)", NamedTextColor.YELLOW))));
            holder.slots.put(i, point.id());
        }
        holder.inventory.setItem(OTHER, item(Material.COMPASS, "自分の地点へ", NamedTextColor.GOLD, List.of(text("自分だけのテレポート地点のページ", NamedTextColor.GRAY))));
        paging(holder, all.size(), pages, false);
    }

    private void renderPersonal(Player player, Holder holder) {
        List<PersonalPoints.Point> mine = points.of(player.getUniqueId());
        for (int i = 0; i < mine.size() && i < PAGE; i++) {
            PersonalPoints.Point point = mine.get(i);
            holder.inventory.setItem(i, item(Material.LODESTONE, point.name(), NamedTextColor.AQUA, List.of(
                    text(point.world() + " " + Math.round(point.x()) + ", " + Math.round(point.y()) + ", " + Math.round(point.z()), NamedTextColor.GRAY),
                    text("クリック: すぐ飛ぶ", NamedTextColor.YELLOW),
                    text("Shift+右クリック: この地点を消す", NamedTextColor.RED))));
            holder.slots.put(i, Integer.toString(i));
        }
        holder.inventory.setItem(ADD, item(mine.size() < PersonalPoints.MAX ? Material.LIME_DYE : Material.GRAY_DYE, "ここを追加", NamedTextColor.GREEN, List.of(
                text("今いる場所を、チャットに打った名前で残す", NamedTextColor.GRAY),
                text(mine.size() + " / " + PersonalPoints.MAX, NamedTextColor.WHITE))));
        holder.inventory.setItem(OTHER, item(Material.ENDER_EYE, "ファストトラベルへ", NamedTextColor.GOLD, List.of(text("全部の地点のページ", NamedTextColor.GRAY))));
    }

    private static int pages(int count) {
        return Math.max(1, (count + PAGE - 1) / PAGE);
    }

    private static void paging(Holder holder, int count, int pages, boolean search) {
        if (holder.index > 0) holder.inventory.setItem(PREVIOUS, item(Material.SPECTRAL_ARROW, "◀ 前のページ", NamedTextColor.AQUA, List.of()));
        if (holder.index < pages - 1) holder.inventory.setItem(NEXT, item(Material.SPECTRAL_ARROW, "次のページ ▶", NamedTextColor.AQUA, List.of()));
        holder.inventory.setItem(COUNT, item(Material.PAPER, (holder.index + 1) + " / " + pages + " ページ", NamedTextColor.WHITE, List.of(text(count + " 件", NamedTextColor.GRAY))));
        if (search) {
            holder.inventory.setItem(SEARCH, item(Material.OAK_SIGN, holder.query.isEmpty() ? "検索" : "検索: " + holder.query, NamedTextColor.GREEN, List.of(
                    text("名前か id の一部 (空白で区切ると、全部を含むもの)", NamedTextColor.GRAY),
                    text("左クリック: チャットに打って検索 / 右クリック: 検索をやめる", NamedTextColor.YELLOW))));
        }
    }

    // ---- clicks ------------------------------------------------------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner)) return;
        if (!player.hasPermission("iruurpg.admin")) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;
        ClickType click = event.getClick();
        boolean right = click.isRightClick();
        if (slot == BACK) {
            if (holder.page == Page.HOME) player.closeInventory();
            else to(player, holder, Page.HOME);
            return;
        }
        switch (holder.page) {
            case HOME -> {
                if (slot == 20) to(player, holder, Page.ITEMS);
                else if (slot == 22) to(player, holder, Page.MOBS);
                else if (slot == 24) to(player, holder, Page.TRAVEL);
                else if (slot == 31) to(player, holder, Page.PERSONAL);
            }
            case ITEMS -> {
                if (slot < PAGE && holder.slots.containsKey(slot)) give(player, holder.slots.get(slot), click.isShiftClick() && click.isLeftClick());
                else if (slot == FILTER_A) {
                    ItemKind[] kinds = ItemKind.values();
                    holder.kind = kinds[Math.floorMod(holder.kind.ordinal() + (right ? -1 : 1), kinds.length)];
                    holder.index = 0;
                } else if (slot == FILTER_B) {
                    Rarity[] rarities = Rarity.values();
                    int at = holder.rarity == null ? -1 : holder.rarity.ordinal();
                    int next = Math.floorMod(at + 1 + (right ? -1 : 1), rarities.length + 1) - 1;
                    holder.rarity = next < 0 ? null : rarities[next];
                    holder.index = 0;
                } else if (!pageClick(player, holder, slot, right)) return;
                render(player, holder);
            }
            case MOBS -> {
                if (slot < PAGE && holder.slots.containsKey(slot)) spawn(player, holder.slots.get(slot), click.isShiftClick() && click.isLeftClick());
                else if (slot == FILTER_A) {
                    List<String> choices = new ArrayList<>(List.of("", "@boss", "@none"));
                    choices.addAll(areas());
                    int at = Math.max(0, choices.indexOf(holder.area));
                    holder.area = choices.get(Math.floorMod(at + (right ? -1 : 1), choices.size()));
                    holder.index = 0;
                } else if (!pageClick(player, holder, slot, right)) return;
                render(player, holder);
            }
            case TRAVEL -> {
                if (slot < PAGE && holder.slots.containsKey(slot)) {
                    go(player, travel.find(holder.slots.get(slot)));
                    return;
                }
                if (slot == OTHER) {
                    to(player, holder, Page.PERSONAL);
                    return;
                }
                if (!pageClick(player, holder, slot, right)) return;
                render(player, holder);
            }
            case PERSONAL -> {
                if (slot < PAGE && holder.slots.containsKey(slot)) {
                    int index = Integer.parseInt(holder.slots.get(slot));
                    if (click == ClickType.SHIFT_RIGHT) {
                        List<PersonalPoints.Point> mine = points.of(player.getUniqueId());
                        String name = index < mine.size() ? mine.get(index).name() : "";
                        if (points.remove(player.getUniqueId(), index)) player.sendMessage(Component.text(PREFIX + "地点「" + name + "」を消しました。", NamedTextColor.GRAY));
                        render(player, holder);
                    } else {
                        List<PersonalPoints.Point> mine = points.of(player.getUniqueId());
                        if (index < mine.size()) go(player, mine.get(index));
                    }
                } else if (slot == ADD) {
                    if (points.of(player.getUniqueId()).size() >= PersonalPoints.MAX) {
                        player.sendMessage(Component.text(PREFIX + "地点は " + PersonalPoints.MAX + " か所までです。Shift+右クリックで消してから足してください。", NamedTextColor.RED));
                        return;
                    }
                    ask(player, holder, "point", "残す地点の名前", player.getLocation());
                } else if (slot == OTHER) {
                    to(player, holder, Page.TRAVEL);
                }
            }
        }
    }

    /** A click on the paging and the search at the bottom; false when it was on none of them. */
    private boolean pageClick(Player player, Holder holder, int slot, boolean right) {
        if (slot == PREVIOUS && holder.index > 0) holder.index--;
        else if (slot == NEXT) holder.index++;
        else if (slot == SEARCH && (holder.page == Page.ITEMS || holder.page == Page.MOBS)) {
            if (right) {
                holder.query = "";
                holder.index = 0;
            } else {
                ask(player, holder, "search", "検索する言葉 (名前か id の一部)", null);
                return false;
            }
        } else return false;
        return true;
    }

    private void to(Player player, Holder holder, Page page) {
        holder.page = page;
        holder.index = 0;
        holder.query = "";
        show(player, holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    // ---- what the clicks do ----------------------------------------------------------------------------------------------------------

    private void give(Player player, String id, boolean stack) {
        RpgItemDefinition definition = items.find(id).orElse(null);
        if (definition == null) {
            player.sendMessage(Component.text(PREFIX + "そのアイテムはもうありません (" + id + ")。", NamedTextColor.RED));
            return;
        }
        int count = stack && !definition.unstackable() ? 64 : 1;
        ItemStack made = factory.create(definition);
        made.setAmount(Math.min(count, Math.max(1, made.getMaxStackSize())));
        player.getInventory().addItem(made).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage(Component.text(PREFIX + definition.name() + " を " + made.getAmount() + " 個もらいました。", NamedTextColor.GREEN));
    }

    private void spawn(Player player, String id, boolean five) {
        int made = 0;
        for (int n = 0; n < (five ? 5 : 1); n++) if (mobService.spawn(player, id).isPresent()) made++;
        if (made == 0) player.sendMessage(Component.text(PREFIX + "出せませんでした (" + id + ")。", NamedTextColor.RED));
        else player.sendMessage(Component.text(PREFIX + id + " を " + made + " 体出しました。", NamedTextColor.GREEN));
    }

    private void go(Player player, TravelService.Waypoint point) {
        Location at = point == null ? null : point.location();
        if (at == null) {
            player.sendMessage(Component.text(PREFIX + "その地点のワールドがありません。", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.teleport(at);
        player.sendMessage(Component.text(PREFIX + point.name() + " へ飛びました。", NamedTextColor.AQUA));
    }

    private void go(Player player, PersonalPoints.Point point) {
        World world = Bukkit.getWorld(point.world());
        if (world == null) {
            player.sendMessage(Component.text(PREFIX + "その地点のワールド (" + point.world() + ") がありません。", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.teleport(new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch()));
        player.sendMessage(Component.text(PREFIX + point.name() + " へ飛びました。", NamedTextColor.AQUA));
    }

    // ---- words typed in the chat ------------------------------------------------------------------------------------------------------

    private void ask(Player player, Holder holder, String what, String prompt, Location at) {
        pending.put(player.getUniqueId(), new Pending(holder, what, at == null ? null : at.clone(), System.currentTimeMillis() + INPUT_TIMEOUT_MILLIS));
        player.closeInventory();
        player.sendMessage(Component.text(PREFIX + prompt + "をチャットに入力してください。やめるときは「キャンセル」", NamedTextColor.YELLOW));
        player.showTitle(Title.title(Component.empty(), Component.text(prompt + "をチャットに入力", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(10), Duration.ofMillis(500))));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending waiting = pending.get(player.getUniqueId());
        if (waiting == null) return;
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> answer(player, waiting, message));
    }

    private void answer(Player player, Pending waiting, String message) {
        if (pending.get(player.getUniqueId()) != waiting) return;
        pending.remove(player.getUniqueId());
        player.clearTitle();
        if (!player.isOnline()) return;
        String typed = message.trim();
        Holder holder = waiting.holder();
        if (System.currentTimeMillis() > waiting.expiresAt()) {
            player.sendMessage(Component.text(PREFIX + "入力の時間が切れました。", NamedTextColor.RED));
        } else if (typed.equalsIgnoreCase("キャンセル") || typed.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text(PREFIX + "入力をやめました。", NamedTextColor.GRAY));
        } else if (waiting.what().equals("search")) {
            holder.query = typed.length() > 40 ? typed.substring(0, 40) : typed;
            holder.index = 0;
        } else if (waiting.what().equals("point") && waiting.at() != null && waiting.at().getWorld() != null) {
            Location at = waiting.at();
            String name = typed.isEmpty() ? "地点" : typed.length() > 32 ? typed.substring(0, 32) : typed;
            if (points.add(player.getUniqueId(), new PersonalPoints.Point(name, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch()))) {
                player.sendMessage(Component.text(PREFIX + "地点「" + name + "」を残しました。", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(PREFIX + "地点は " + PersonalPoints.MAX + " か所までです。", NamedTextColor.RED));
            }
        }
        if (player.hasPermission("iruurpg.admin")) show(player, holder);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    // ---- items of the menu ----------------------------------------------------------------------------------------------------------

    private static ItemStack withLore(ItemStack stack, List<Component> extra) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.addAll(extra);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
