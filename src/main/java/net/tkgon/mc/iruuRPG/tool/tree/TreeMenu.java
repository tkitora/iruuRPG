package net.tkgon.mc.iruuRPG.tool.tree;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The settings screen of the tree tool: one button for every number (left click up, right click down, shift for bigger steps), the algorithm, the shape of
 * the crown, the seed, and the buttons that save and load the settings, generate the tree and take it back.
 */
public final class TreeMenu implements Listener {

    private static final int SIZE = 54;
    private static final int[] KEY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29};
    private static final Material[] KEY_ICONS = {
            Material.LADDER, Material.LEAD, Material.OAK_LOG, Material.STICK, Material.VINE, Material.WEEPING_VINES,
            Material.OAK_LEAVES, Material.AZALEA_LEAVES, Material.MANGROVE_ROOTS,
            Material.BAMBOO, Material.OAK_FENCE, Material.FERN, Material.CHAIN, Material.STRIPPED_OAK_LOG, Material.DARK_OAK_LOG,
            Material.FLOWERING_AZALEA_LEAVES};
    private static final int ALGORITHM = 30;
    private static final int CROWN = 31;
    private static final int SEED = 32;
    private static final int INFO = 33;
    private static final int[] LEGEND_SLOTS = {38, 39, 40, 41, 42};
    private static final int CLOSE = 45;
    private static final int LOAD = 47;
    private static final int SAVE = 48;
    private static final int GENERATE = 49;
    private static final int UNDO = 50;
    private static final int RESET = 52;

    private final TreeTool tool;

    TreeMenu(TreeTool tool) {
        this.tool = tool;
    }

    private static final class Holder implements MenuHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("木の生成ツール"));
        holder.inventory = inventory;
        render(player, inventory);
        player.openInventory(inventory);
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String number(TreeSettings.Key key, double value) {
        return key.whole() ? Integer.toString((int) Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
    }

    private void render(Player player, Inventory inventory) {
        TreeTool.Session session = tool.session(player);
        TreeSettings settings = session.settings;
        inventory.clear();
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) inventory.setItem(slot, frame);
        }

        TreeSettings.Key[] keys = TreeSettings.Key.values();
        for (int index = 0; index < keys.length; index++) {
            TreeSettings.Key key = keys[index];
            List<Component> lore = new ArrayList<>();
            lore.add(text(key.description(), NamedTextColor.GRAY));
            lore.add(text("現在: " + number(key, settings.get(key)) + "  (" + number(key, key.min()) + " 〜 " + number(key, key.max()) + ")", NamedTextColor.WHITE));
            lore.add(text("左クリック +" + number(key, key.step()) + " / 右クリック -" + number(key, key.step()), NamedTextColor.YELLOW));
            lore.add(text("Shift で " + number(key, key.bigStep()) + " ずつ", NamedTextColor.YELLOW));
            inventory.setItem(KEY_SLOTS[index], item(KEY_ICONS[index], key.label() + ": " + number(key, settings.get(key)), NamedTextColor.GREEN, lore));
        }

        inventory.setItem(ALGORITHM, item(Material.KNOWLEDGE_BOOK, "アルゴリズム: " + TreeTool.algorithmLabel(settings.algorithm()), NamedTextColor.AQUA, List.of(
                text("パラメトリック: 幹から黄金角でらせん状に枝が出て、枝が枝を出す (Sapling / treegen 型。標準)", NamedTextColor.GRAY),
                text("針葉樹: 幹のまわりに輪になって枝が出る、円錐形 (pinegen 型)", NamedTextColor.GRAY),
                text("空間コロニー化: 樹冠の中の目標点へ枝が伸びる (自然な不規則さ)", NamedTextColor.GRAY),
                text("L-system: Honda 型の分岐規則の繰り返し (規則的な形)", NamedTextColor.GRAY),
                text("クリックで切り替え", NamedTextColor.YELLOW))));
        inventory.setItem(CROWN, item(Material.FLOWERING_AZALEA, "樹冠の形: " + TreeTool.crownLabel(settings.crown()), NamedTextColor.AQUA, List.of(
                text("枝の長さの分布・枝が埋める空間の形 (L-system 以外)", NamedTextColor.GRAY),
                text("楕円体 / 円錐 / 傘 (高いところの枝が長い) / 円柱", NamedTextColor.GRAY),
                text("クリックで切り替え", NamedTextColor.YELLOW))));
        inventory.setItem(SEED, item(Material.WHEAT_SEEDS, "シード: " + (settings.seed() == 0L ? "毎回ランダム" : Long.toString(settings.seed())), NamedTextColor.AQUA, List.of(
                text("同じシードと設定なら、同じ木ができる", NamedTextColor.GRAY),
                text("左クリック: 新しい番号に固定", NamedTextColor.YELLOW),
                text("右クリック: 毎回ランダムに戻す", NamedTextColor.YELLOW))));

        Location base = session.base;
        inventory.setItem(INFO, item(Material.COMPASS, "木を立てる場所", NamedTextColor.GOLD, List.of(
                text(base == null ? "まだ決まっていません (ブロックをクリック)" : base.getWorld().getName() + " " + base.getBlockX() + ", " + base.getBlockY() + ", " + base.getBlockZ(), NamedTextColor.WHITE),
                text("クリックしたブロックの真上が、幹の根元", NamedTextColor.GRAY),
                text("設定の名前: " + settings.name(), NamedTextColor.GRAY))));
        TreePart[] parts = TreePart.values();
        for (int index = 0; index < parts.length; index++) {
            Material material = tool.material(parts[index]);
            inventory.setItem(LEGEND_SLOTS[index], item(material, parts[index].label(), NamedTextColor.WHITE, List.of(
                    text("このウールで作る (あとで手動で本物のブロックに変える)", NamedTextColor.GRAY))));
        }

        inventory.setItem(CLOSE, item(Material.ARROW, "閉じる", NamedTextColor.YELLOW, List.of()));
        List<String> saved = tool.store().list();
        List<Component> loadLore = new ArrayList<>();
        loadLore.add(text("保存した木の設定を読み込む (" + saved.size() + "件)", NamedTextColor.GRAY));
        for (int index = 0; index < Math.min(8, saved.size()); index++) {
            loadLore.add(text((saved.get(index).equals(settings.name()) ? "▶ " : "  ") + saved.get(index), NamedTextColor.WHITE));
        }
        loadLore.add(text("左クリック: 次へ / 右クリック: 前へ (その場で読み込む)", NamedTextColor.YELLOW));
        inventory.setItem(LOAD, item(Material.BOOKSHELF, "読み込み", NamedTextColor.AQUA, loadLore));
        inventory.setItem(SAVE, item(Material.WRITABLE_BOOK, "保存: " + settings.name(), NamedTextColor.AQUA, List.of(
                text("いまの設定を plugins/iruuRPG/treetools/" + settings.name() + ".yml に保存", NamedTextColor.GRAY),
                text("名前を付けるには /treetool save <名前>", NamedTextColor.GRAY),
                text("クリックで保存", NamedTextColor.YELLOW))));
        inventory.setItem(GENERATE, item(Material.LIME_WOOL, "生成する", NamedTextColor.GREEN, List.of(
                text("この設定の木を、決めた場所に作る", NamedTextColor.GRAY),
                text("高さ " + settings.height() + " / 幅 " + settings.width() + " の箱の中に収まる", NamedTextColor.GRAY),
                text("空気と草花だけを置き換える", NamedTextColor.GRAY),
                text("クリックで生成", NamedTextColor.YELLOW))));
        inventory.setItem(UNDO, item(Material.BARRIER, "直前の木を元に戻す", NamedTextColor.RED, List.of(
                text("最近の木を新しいほうから戻す (最大" + tool.plugin().getConfig().getInt("treetool.undo-steps", 5) + "本)", NamedTextColor.GRAY))));
        inventory.setItem(RESET, item(Material.TNT, "設定を初期値に戻す", NamedTextColor.RED, List.of(
                text("数字・アルゴリズム・樹冠・シードを初期値にする", NamedTextColor.GRAY))));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory) return;
        TreeTool.Session session = tool.session(player);
        TreeSettings settings = session.settings;
        int slot = event.getSlot();
        ClickType click = event.getClick();
        boolean left = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT;
        boolean right = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        boolean big = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        if (!left && !right) return;

        for (int index = 0; index < KEY_SLOTS.length; index++) {
            if (KEY_SLOTS[index] != slot) continue;
            TreeSettings.Key key = TreeSettings.Key.values()[index];
            settings.adjust(key, (left ? 1 : -1) * (big ? key.bigStep() : key.step()));
            tool.limit(settings);
            render(player, holder.inventory);
            return;
        }
        switch (slot) {
            case ALGORITHM -> settings.cycleAlgorithm();
            case CROWN -> settings.cycleCrown();
            case SEED -> settings.setSeed(left ? 1 + (new Random().nextLong() & 0xFFFFFFL) : 0L);
            case LOAD -> {
                List<String> saved = tool.store().list();
                if (saved.isEmpty()) {
                    player.sendMessage("[木] 保存した設定がありません。メニューの「保存」か /treetool save <名前> で保存できます。");
                    return;
                }
                int current = saved.indexOf(settings.name());
                int next = Math.floorMod(current + (left ? 1 : -1), saved.size());
                TreeSettings loaded = tool.store().load(saved.get(next));
                if (loaded == null) {
                    player.sendMessage("[木] 読み込めませんでした: " + saved.get(next));
                    return;
                }
                tool.limit(loaded);
                session.settings = loaded;
            }
            case SAVE -> {
                String name = TreeSettings.safeName(settings.name());
                if (name == null) name = "tree";
                settings.setName(name);
                player.sendMessage(tool.store().save(name, settings) ? "[木] 保存しました: " + name : "[木] 保存できませんでした (ログを見てください)。");
            }
            case GENERATE -> {
                player.closeInventory();
                tool.generate(player);
                return;
            }
            case UNDO -> {
                player.closeInventory();
                tool.undo(player);
                return;
            }
            case RESET -> {
                TreeSettings fresh = new TreeSettings();
                fresh.setName(settings.name());
                session.settings = fresh;
            }
            case CLOSE -> {
                player.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        render(player, holder.inventory);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
