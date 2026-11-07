package net.tkgon.mc.iruuRPG.tools.procedural;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * (t286) The list of saved presets, by what they make (a tab for trees, rocks, spikes, crystals, webs, houses, lobbies), a page of 36 at a time. Two uses: loading a
 * preset into the settings (a click loads it and goes back to the settings), and choosing the presets of the placing tool with their weights (left click more,
 * right click less; the tool then puts one of them, by weight, where it is clicked, with a seed of chance). (t288) In the list for loading, a shift click deletes a
 * preset after a screen that asks first (the file goes to {@code trees/trash/}).
 */
final class PresetMenu implements Listener {

    enum Mode {
        LOAD, SCATTER
    }

    private static final int SIZE = 54;
    private static final int BACK = 0;
    private static final int[] TABS = {1, 2, 3, 4, 5, 6, 7, 8};
    /** (t313: the tabs took its slot when the building of lots came) */
    private static final int MODE = 52;
    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;
    private static final int PREV = 45;
    private static final int SELECTION = 47;
    private static final int GIVE = 48;
    private static final int PAGE = 49;
    private static final int CLEAR = 50;
    private static final int NEXT = 53;

    private final ProceduralModule tool;

    PresetMenu(ProceduralModule tool) {
        this.tool = tool;
    }

    private static final class Holder implements MenuHolder {
        private Inventory inventory;
        private Mode mode = Mode.LOAD;
        private ProcKind kind = ProcKind.TREE;
        private int page;
        /** The presets on the page, by slot (from {@link #FIRST}). */
        private final List<String> shown = new ArrayList<>();

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    void open(Player player, Mode mode) {
        open(player, mode, 0);
    }

    private void open(Player player, Mode mode, int page) {
        Holder holder = new Holder();
        holder.mode = mode;
        holder.page = page;
        TreeSession session = tool.session(player);
        holder.kind = session.presetKind != null ? session.presetKind : mode == Mode.SCATTER ? ProcKind.TREE : session.settings.kind();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text(mode == Mode.LOAD ? "プリセットの読み込み" : "配置ツールのプリセット"));
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (glint) meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The presets of a kind: trees by algorithm and then name, the rest by name. */
    List<String> presets(ProcKind kind) {
        List<String> names = new ArrayList<>();
        for (String name : tool.store().list()) {
            TreeStore.Info info = tool.store().info(name);
            if (info != null && info.kind() == kind) names.add(name);
        }
        names.sort(Comparator.comparing((String name) -> kind.tree() ? TreeSettings.ALGORITHMS.indexOf(tool.store().info(name).algorithm()) : 0).thenComparing(name -> name));
        return names;
    }

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        TreeSession session = tool.session(player);
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of(), false);
        for (int slot = 45; slot < SIZE; slot++) inventory.setItem(slot, frame);
        inventory.setItem(BACK, item(Material.ARROW, holder.mode == Mode.LOAD ? "設定画面へ戻る" : "閉じる", NamedTextColor.YELLOW, List.of(), false));
        ProcKind[] kinds = ProcKind.values();
        for (int index = 0; index < TABS.length && index < kinds.length; index++) {
            ProcKind kind = kinds[index];
            int count = presets(kind).size();
            inventory.setItem(TABS[index], item(kind.icon(), kind.label() + " (" + count + ")", kind == holder.kind ? NamedTextColor.GOLD : NamedTextColor.WHITE,
                    List.of(text(kind.description(), NamedTextColor.GRAY), text("クリック: この種類のプリセットを見る", NamedTextColor.YELLOW)), kind == holder.kind));
        }
        inventory.setItem(MODE, holder.mode == Mode.LOAD
                ? item(Material.BIRCH_SAPLING, "配置ツールのプリセットを選ぶ", NamedTextColor.AQUA, List.of(
                        text("配置ツール: 選んだプリセットのどれかを、重みの割合で、クリックした所にランダムなシードで置く", NamedTextColor.GRAY),
                        text("クリック: 選ぶ画面へ", NamedTextColor.YELLOW)), false)
                : item(Material.BOOKSHELF, "読み込みの画面へ", NamedTextColor.AQUA, List.of(text("クリック: プリセットを設定に読み込む画面へ", NamedTextColor.YELLOW)), false));
        List<String> names = presets(holder.kind);
        int pages = Math.max(1, (names.size() + PER_PAGE - 1) / PER_PAGE);
        holder.page = Math.max(0, Math.min(pages - 1, holder.page));
        holder.shown.clear();
        for (int index = holder.page * PER_PAGE; index < Math.min(names.size(), (holder.page + 1) * PER_PAGE); index++) {
            String name = names.get(index);
            holder.shown.add(name);
            inventory.setItem(FIRST + holder.shown.size() - 1, presetItem(holder, session, name));
        }
        if (names.isEmpty()) {
            inventory.setItem(FIRST + 13, item(Material.BARRIER, "この種類のプリセットはありません", NamedTextColor.GRAY, List.of(
                    text("設定を /proceduraltool save <名前> で保存すると、ここに並ぶ", NamedTextColor.GRAY)), false));
        }
        if (holder.page > 0) inventory.setItem(PREV, item(Material.SPECTRAL_ARROW, "前のページ", NamedTextColor.YELLOW, List.of(), false));
        if (holder.page < pages - 1) inventory.setItem(NEXT, item(Material.SPECTRAL_ARROW, "次のページ", NamedTextColor.YELLOW, List.of(), false));
        inventory.setItem(PAGE, item(Material.PAPER, "ページ " + (holder.page + 1) + " / " + pages, NamedTextColor.WHITE, List.of(
                text(holder.kind.label() + " のプリセット " + names.size() + " 件", NamedTextColor.GRAY)), false));
        if (holder.mode == Mode.SCATTER) {
            List<Component> lore = new ArrayList<>();
            int total = 0;
            for (int weight : session.scatter.values()) total += weight;
            for (Map.Entry<String, Integer> entry : session.scatter.entrySet()) {
                lore.add(text(entry.getKey() + " × " + entry.getValue() + String.format(java.util.Locale.ROOT, " (%.0f%%)", 100.0 * entry.getValue() / Math.max(1, total)), NamedTextColor.WHITE));
            }
            if (lore.isEmpty()) lore.add(text("まだ選んでいない", NamedTextColor.GRAY));
            inventory.setItem(SELECTION, item(Material.BUNDLE, "選んだプリセット (" + session.scatter.size() + ")", NamedTextColor.GOLD, lore, false));
            inventory.setItem(GIVE, item(Material.BIRCH_SAPLING, "配置ツールを受け取る", NamedTextColor.GREEN, List.of(
                    text("この選び方の配置ツールを渡す (手に配置ツールを持っていれば、それを書き換える)", NamedTextColor.GRAY),
                    text("配置ツールで右クリック: 選んだどれかを置く / 左クリック: この画面", NamedTextColor.GRAY)), false));
            inventory.setItem(CLEAR, item(Material.TNT, "選択をすべて外す", NamedTextColor.RED, List.of(), false));
        }
    }

    private ItemStack presetItem(Holder holder, TreeSession session, String name) {
        TreeStore.Info info = tool.store().info(name);
        Material icon = holder.kind.icon();
        List<Component> lore = new ArrayList<>();
        if (info != null && info.kind().tree()) {
            Material trunk = info.trunk() == null ? null : TreeSettings.materialOf(info.trunk());
            if (trunk != null && trunk.isItem()) icon = trunk;
            lore.add(text("アルゴリズム: " + ProceduralModule.algorithmLabel(info.algorithm()), NamedTextColor.GRAY));
            if ("ornate".equals(info.algorithm())) lore.add(text("様式: " + OrnateTree.styleName(info.style()), NamedTextColor.GRAY));
        }
        boolean chosen = false;
        if (holder.mode == Mode.LOAD) {
            boolean current = name.equals(session.settings.name());
            if (current) lore.add(text("▶ いま読み込んでいるもの", NamedTextColor.GOLD));
            lore.add(text("クリック: 読み込んで設定画面へ", NamedTextColor.YELLOW));
            lore.add(text("Shift+クリック: 削除 (確かめる画面のあと)", NamedTextColor.RED));
            chosen = current;
        } else if (holder.kind.web()) {
            lore.add(text("クモの巣は点を選んで張るので、配置ツールでは使えません", NamedTextColor.RED));
        } else {
            int weight = session.scatter.getOrDefault(name, 0);
            chosen = weight > 0;
            lore.add(text(chosen ? "重み: " + weight : "選んでいない", chosen ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            lore.add(text("左クリック: 重み +1 / 右クリック: 重み -1 (Shift で ±5、0 で外れる)", NamedTextColor.YELLOW));
        }
        ItemStack item = item(icon, name, chosen ? NamedTextColor.GOLD : NamedTextColor.WHITE, lore, chosen);
        if (holder.mode == Mode.SCATTER && chosen) item.setAmount(Math.max(1, Math.min(64, session.scatter.get(name))));
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory) return;
        ClickType click = event.getClick();
        boolean left = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT;
        boolean right = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        boolean big = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        if (!left && !right) return;
        int slot = event.getSlot();
        TreeSession session = tool.session(player);
        if (slot == BACK) {
            if (holder.mode == Mode.LOAD && session.base != null) later(player, () -> tool.menu().open(player));
            else player.closeInventory();
            return;
        }
        for (int index = 0; index < TABS.length && index < ProcKind.values().length; index++) {
            if (TABS[index] != slot) continue;
            holder.kind = ProcKind.values()[index];
            session.presetKind = holder.kind;
            holder.page = 0;
            render(player, holder);
            return;
        }
        switch (slot) {
            case MODE -> {
                holder.mode = holder.mode == Mode.LOAD ? Mode.SCATTER : Mode.LOAD;
                holder.page = 0;
                // (the title says the use: the screen is opened again)
                Mode mode = holder.mode;
                later(player, () -> open(player, mode));
                return;
            }
            case PREV -> holder.page--;
            case NEXT -> holder.page++;
            case GIVE -> {
                if (holder.mode == Mode.SCATTER) {
                    tool.giveScatter(player, session.scatter);
                    player.closeInventory();
                    return;
                }
            }
            case CLEAR -> {
                if (holder.mode == Mode.SCATTER) session.scatter.clear();
            }
            default -> {
                int index = slot - FIRST;
                if (index < 0 || index >= holder.shown.size()) return;
                String name = holder.shown.get(index);
                if (holder.mode == Mode.LOAD && big) {
                    int page = holder.page;
                    later(player, () -> confirm(player, name, page));
                    return;
                }
                if (holder.mode == Mode.LOAD) {
                    load(player, session, name);
                    return;
                }
                if (holder.kind.web()) return;
                int weight = session.scatter.getOrDefault(name, 0) + (left ? 1 : -1) * (big ? 5 : 1);
                if (weight <= 0) session.scatter.remove(name);
                else session.scatter.put(name, Math.min(99, weight));
            }
        }
        render(player, holder);
    }

    /** Loads a preset into the player's settings and goes back to the settings (or closes, when there is no place yet). */
    private void load(Player player, TreeSession session, String name) {
        TreeSettings loaded = tool.store().load(name);
        if (loaded == null) {
            player.sendMessage(ProceduralModule.PREFIX + "読み込めませんでした: " + name);
            return;
        }
        tool.limit(loaded);
        session.settings = loaded;
        session.autoSeed = false;
        tool.changed(player);
        player.sendMessage(ProceduralModule.PREFIX + "読み込みました: " + name);
        if (session.base != null) later(player, () -> tool.menu().open(player));
        else player.closeInventory();
    }

    // ---- (t288) deleting a preset, after asking ----------------------------------------------------------------------------------------

    private static final int YES = 11;
    private static final int WHAT = 13;
    private static final int NO = 15;

    /** The screen that asks before a preset is deleted: the preset, and the list's page to go back to. */
    private static final class Confirm implements MenuHolder {
        private Inventory inventory;
        private String name;
        private int page;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private void confirm(Player player, String name, int page) {
        Confirm holder = new Confirm();
        holder.name = name;
        holder.page = page;
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("削除の確認: " + name));
        holder.inventory = inventory;
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of(), false);
        for (int slot = 0; slot < 27; slot++) inventory.setItem(slot, frame);
        inventory.setItem(YES, item(Material.RED_CONCRETE, "削除する", NamedTextColor.RED, List.of(
                text("プリセット「" + name + "」を一覧から消す", NamedTextColor.GRAY),
                text("(ファイルは trees/trash/ へ移す。戻すときは、手で trees/ へ戻す)", NamedTextColor.GRAY)), false));
        inventory.setItem(WHAT, item(Material.PAPER, name, NamedTextColor.WHITE, List.of(), false));
        inventory.setItem(NO, item(Material.LIME_CONCRETE, "やめる", NamedTextColor.GREEN, List.of(text("消さずに一覧へ戻る", NamedTextColor.GRAY)), false));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onConfirm(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Confirm holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory) return;
        int slot = event.getSlot();
        if (slot != YES && slot != NO) return;
        if (slot == YES) {
            boolean deleted = tool.store().delete(holder.name);
            if (deleted) tool.session(player).scatter.remove(holder.name);
            player.sendMessage(ProceduralModule.PREFIX + (deleted ? "消しました: " + holder.name + " (trees/trash/ に移しました)" : "消せませんでした: " + holder.name));
        }
        int page = holder.page;
        later(player, () -> open(player, Mode.LOAD, page));
    }

    /** Another screen, opened on the next tick (not inside the click). */
    private void later(Player player, Runnable open) {
        Bukkit.getScheduler().runTask(tool.plugin(), () -> {
            if (player.isOnline()) open.run();
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder || event.getView().getTopInventory().getHolder() instanceof Confirm) event.setCancelled(true);
    }
}
