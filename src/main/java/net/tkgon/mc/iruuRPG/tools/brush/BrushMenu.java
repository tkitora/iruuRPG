package net.tkgon.mc.iruuRPG.tools.brush;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The menu of the brush, laid out like the tree tool's: tabs along the top for the pages (basics, pattern, mask, sun and shade, blacklist), the settings of a page in the
 * grid under them, and a bottom row that is the same on every page (close, preview, the brush, undo, redo). Every change is passed to the module, which bumps the revision the
 * live preview follows. A block of the player's own inventory clicked while the pattern, the block mask or the blacklist is open is added to it (or, after "from" / "to" of the
 * gradient was clicked, becomes that end).
 */
final class BrushMenu implements Listener {

    enum Page {
        MAIN(0, Material.PAINTING, "基本", "ブラシ"),
        PATTERN(1, Material.WHITE_CONCRETE, "模様", "模様"),
        MASK(2, Material.HOPPER, "マスク", "マスク"),
        POSITION(3, Material.LADDER, "高さ・位置", "高さと位置"),
        SUN(4, Material.SUNFLOWER, "陰影", "太陽と陰影"),
        NOISE(5, Material.AMETHYST_SHARD, "ノイズ", "ノイズとセル"),
        BLACKLIST(6, Material.BARRIER, "ブラックリスト", "ブラックリスト"),
        SELECT(7, Material.BLAZE_ROD, "選択", "選択ツール"),
        PRESETS(8, Material.BOOKSHELF, "プリセット管理", "ブロックの一覧のプリセット");

        final int tab;
        final Material icon;
        final String label;
        final String title;

        Page(int tab, Material icon, String label, String title) {
            this.tab = tab;
            this.icon = icon;
            this.label = label;
            this.title = title;
        }
    }

    private static final int SIZE = 54;
    /** The slots of the grid under the tabs, row by row (seven a row). */
    private static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int CLOSE = 45;
    private static final int PREVIEW = 47;
    private static final int GIVE = 48;
    private static final int UNDO = 51;
    private static final int REDO = 52;

    private final BrushModule module;

    BrushMenu(BrushModule module) {
        this.module = module;
    }

    private enum Pick { NONE, FROM, TO }

    /** What a biome chosen from the list of all biomes is for (the biome mode). */
    private enum BiomePick { NONE, ADD, FROM, TO }

    private static final class Holder implements MenuHolder {
        private Inventory inventory;
        private Page page;
        /** Which end of the gradient the next block clicked in the inventory becomes. */
        private Pick pick = Pick.NONE;
        /** The preset the cycling item shows (an index in the store's list; -1: none yet). */
        private int presetIndex = -1;
        /** The blacklist page edits the shade's list (true) or the gradient's (false). */
        private boolean shadeList;
        /** The page of the list of presets (21 to a page). */
        private int presetPage;
        /** The biome mode: the list of all biomes is open (to add one or to choose an end), and which page of it. */
        private BiomePick biomePick = BiomePick.NONE;
        private int biomeListPage;
        private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    void open(Player player, Page page) {
        Holder holder = new Holder();
        holder.page = page;
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text("ペイントブラシ - " + page.title));
        render(player, holder);
        player.openInventory(holder.inventory);
    }

    // ---- building the items ------------------------------------------------------------------------------------------------------

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material.isItem() && !material.isAir() ? material : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(text(name, color));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) if (!line.isEmpty()) lines.add(text(line, NamedTextColor.GRAY));
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS);
            // a switch that is on shines like an enchanted item, so it can be told from one that is off at a glance
            if (name.endsWith(": オン")) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String onOff(boolean value) {
        return value ? "オン" : "オフ";
    }

    private static String name(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    private static void put(Holder holder, int slot, ItemStack item, BiConsumer<Player, ClickType> action) {
        holder.inventory.setItem(slot, item);
        if (action != null) holder.actions.put(slot, action);
    }

    private static boolean left(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.SHIFT_LEFT;
    }

    private static boolean big(ClickType click) {
        return click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
    }

    private void refresh(Player player, Page page) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
            holder.page = page;
            render(player, holder);
        }
    }

    private static boolean usable(Material material) {
        return material.isBlock() && material.isItem() && !material.isAir();
    }

    // ---- the frame, the tabs and the bottom row ---------------------------------------------------------------------------------

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.actions.clear();
        BrushSettings s = module.settings(player);
        s.lastPage = holder.page;
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) inventory.setItem(slot, frame);
        }
        for (Page page : Page.values()) {
            boolean here = holder.page == page;
            put(holder, page.tab, item(page.icon, (here ? "▶ " : "") + page.label, here ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(here ? "いま開いているページ" : "クリックで開く")),
                    (p, c) -> {
                        holder.pick = Pick.NONE;
                        holder.biomePick = BiomePick.NONE;
                        refresh(p, page);
                    });
        }
        put(holder, CLOSE, item(Material.ARROW, "閉じる", NamedTextColor.YELLOW, List.of()), (p, c) -> p.closeInventory());
        boolean live = module.livePreview().isOn(player);
        put(holder, PREVIEW, item(live ? Material.LIME_STAINED_GLASS : Material.GRAY_STAINED_GLASS, "プレビュー: " + onOff(live), live ? NamedTextColor.GREEN : NamedTextColor.GRAY, List.of(
                "オンにすると、見ている所に使ったときの結果が見える (あなたにだけ)",
                "設定を変えるか、見ている所が動くと、少し待って更新される",
                "重い設定のときは更新を見送る。半径が大きいと使えない",
                "クリックで切り替え")), (p, c) -> {
            module.livePreview().toggle(p);
            refresh(p, holder.page);
        });
        put(holder, GIVE, item(Material.BRUSH, "ブラシをもらう", NamedTextColor.AQUA, List.of("持っていなければブラシのアイテムを渡す")), (p, c) -> module.give(p));
        put(holder, UNDO, item(Material.BARRIER, "元に戻す (//undo)", NamedTextColor.RED, List.of("直前の編集を取り消す。ブラシも木も FAWE の履歴で戻る", module.hasWorldEdit() ? "" : "WorldEdit がないので使えません")), (p, c) -> {
            p.closeInventory();
            module.undo(p);
        });
        put(holder, REDO, item(Material.SPECTRAL_ARROW, "やり直し (//redo)", NamedTextColor.AQUA, List.of("取り消した編集をもう一度行う")), (p, c) -> {
            p.closeInventory();
            module.redo(p);
        });

        switch (holder.page) {
            case MAIN -> main(holder, s);
            case PATTERN -> pattern(holder, s);
            case MASK -> mask(holder, s);
            case POSITION -> position(holder, s);
            case NOISE -> noisePage(holder, s);
            case PRESETS -> presetsPage(holder, s);
            case SUN -> sun(holder, s);
            case BLACKLIST -> blacklist(holder);
            case SELECT -> select(holder, player);
        }
    }

    // ---- main ------------------------------------------------------------------------------------------------------------------------

    private void main(Holder holder, BrushSettings s) {
        BrushMode[] modes = BrushMode.values();
        for (int index = 0; index < modes.length; index++) {
            BrushMode mode = modes[index];
            boolean on = s.mode == mode;
            put(holder, GRID[index], item(mode.icon(), (on ? "▶ " : "") + mode.label(), on ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(mode.description(), on ? "選んでいます" : "クリックで選ぶ")),
                    (p, c) -> {
                        s.mode = mode;
                        refresh(p, Page.MAIN);
                    });
        }
        put(holder, GRID[3], item(Material.SPYGLASS, "大きさ (半径): " + s.radius, NamedTextColor.AQUA, List.of("左クリック +1 / 右クリック -1", "Shift で 5 ずつ", "1〜" + module.maxRadius())),
                (p, c) -> {
                    s.radius = Math.max(1, Math.min(module.maxRadius(), s.radius + (left(c) ? 1 : -1) * (big(c) ? 5 : 1)));
                    refresh(p, Page.MAIN);
                });
        put(holder, GRID[4], item(Material.BLAZE_POWDER, "強さ: " + Math.round(s.strength * 100) + "%", NamedTextColor.AQUA, List.of("範囲の中で、どれくらいのブロックを変えるか", "左クリック +10% / 右クリック -10%", "Shift で 25% ずつ")),
                (p, c) -> {
                    s.strength = Math.max(0.1, Math.min(1.0, s.strength + (left(c) ? 1 : -1) * (big(c) ? 0.25 : 0.1)));
                    refresh(p, Page.MAIN);
                });
        put(holder, GRID[5], item(Material.COBWEB, "ぼかし: " + Math.round(s.falloff * 100) + "%", NamedTextColor.AQUA, List.of("縁に行くほど効きを弱くする度合い", "左クリック +10% / 右クリック -10%")),
                (p, c) -> {
                    s.falloff = Math.max(0.0, Math.min(1.0, s.falloff + (left(c) ? 0.1 : -0.1)));
                    refresh(p, Page.MAIN);
                });
        put(holder, GRID[6], item(Material.GRASS_BLOCK, "表面だけ: " + onOff(s.surfaceOnly), NamedTextColor.AQUA, List.of("オン: 空気に触れているブロックだけ塗る", "オフ: 範囲の中のブロックを全部塗る", "クリックで切り替え")),
                (p, c) -> {
                    s.surfaceOnly = !s.surfaceOnly;
                    refresh(p, Page.MAIN);
                });
        // one item for the shape: click for the next, right click for the one before; the one in use is in the name, the rest in the lore
        BrushShape[] shapes = BrushShape.values();
        List<String> shapeLore = new ArrayList<>();
        shapeLore.add(s.shape.description());
        for (BrushShape shape : shapes) shapeLore.add((shape == s.shape ? "▶ " : "   ") + shape.label());
        shapeLore.add("左クリック: 次の形 / 右クリック: 前の形");
        put(holder, GRID[7], item(s.shape.icon(), "形: " + s.shape.label(), NamedTextColor.GOLD, shapeLore), (p, c) -> {
            s.shape = shapes[Math.floorMod(s.shape.ordinal() + (left(c) ? 1 : -1), shapes.length)];
            refresh(p, Page.MAIN);
        });
        put(holder, GRID[18], item(Material.AMETHYST_SHARD, "ノイズ: " + onOff(s.noise), NamedTextColor.AQUA, List.of("オン: 模様の選び方を滑らかなノイズにして、点々ではなく斑(まだら)の塊にする", "オフ: 一つずつランダム", "クリックで切り替え")),
                (p, c) -> {
                    s.noise = !s.noise;
                    refresh(p, Page.MAIN);
                });
        put(holder, GRID[19], item(Material.COMPARATOR, "ノイズの大きさ: " + s.noiseScale, NamedTextColor.AQUA, List.of("斑の塊の大きさ (大きいほど大きな塊)", "左クリック +0.5 / 右クリック -0.5")),
                (p, c) -> {
                    s.noiseScale = Math.max(0.5, Math.min(12.0, s.noiseScale + (left(c) ? 0.5 : -0.5)));
                    refresh(p, Page.MAIN);
                });
        // the stroke: what a button held down while the brush moves does
        put(holder, GRID[8], item(Material.CHAIN, "ストロークの間隔: " + Math.round(s.strokeSpacing * 100) + "%", NamedTextColor.AQUA, List.of(
                "なぞったとき、点を打つ間隔 (半径に対する割合。小さいほど隙間なく塗る)", "左クリック +10% / 右クリック -10% (20〜150%)")), (p, c) -> {
            s.strokeSpacing = Math.max(0.2, Math.min(1.5, Math.round((s.strokeSpacing + (left(c) ? 0.1 : -0.1)) * 10.0) / 10.0));
            refresh(p, Page.MAIN);
        });
        put(holder, GRID[9], item(Material.STRING, "ストロークの手ぶれ補正: " + Math.round(s.strokeSmooth * 100) + "%", NamedTextColor.AQUA, List.of(
                "なぞった道すじをなめらかにする (離したときに効く)", "左クリック +25% / 右クリック -25%")), (p, c) -> {
            s.strokeSmooth = Math.max(0.0, Math.min(1.0, s.strokeSmooth + (left(c) ? 0.25 : -0.25)));
            refresh(p, Page.MAIN);
        });
        put(holder, GRID[10], item(Material.ARROW, "始まりの太さ: " + Math.round(s.taperStart * 100) + "%", NamedTextColor.AQUA, List.of(
                "ストロークの始まりのブラシの大きさ (半径に対する割合)。終わりとの間は、なめらかに変わる", "左クリック +10% / 右クリック -10% (20〜100%)")), (p, c) -> {
            s.taperStart = Math.max(0.2, Math.min(1.0, Math.round((s.taperStart + (left(c) ? 0.1 : -0.1)) * 10.0) / 10.0));
            refresh(p, Page.MAIN);
        });
        put(holder, GRID[11], item(Material.SPECTRAL_ARROW, "終わりの太さ: " + Math.round(s.taperEnd * 100) + "%", NamedTextColor.AQUA, List.of(
                "ストロークの終わりのブラシの大きさ。始まりより小さくすると、先細りの線になる", "左クリック +10% / 右クリック -10% (20〜100%)")), (p, c) -> {
            s.taperEnd = Math.max(0.2, Math.min(1.0, Math.round((s.taperEnd + (left(c) ? 0.1 : -0.1)) * 10.0) / 10.0));
            refresh(p, Page.MAIN);
        });
        put(holder, GRID[12], item(Material.GUNPOWDER, "ストロークのばらつき: " + Math.round(s.strokeScatter * 100) + "%", NamedTextColor.AQUA, List.of(
                "ストロークの点を、ランダムに横へずらす (半径に対する割合)", "左クリック +10% / 右クリック -10%")), (p, c) -> {
            s.strokeScatter = Math.max(0.0, Math.min(1.0, Math.round((s.strokeScatter + (left(c) ? 0.1 : -0.1)) * 10.0) / 10.0));
            refresh(p, Page.MAIN);
        });
        put(holder, GRID[13], item(Material.HOPPER_MINECART, "ストロークを1回の編集にまとめる: " + onOff(s.strokeGroup), NamedTextColor.AQUA, List.of(
                "オン: 押しつづけてなぞった間は塗らずに道すじだけ記録し、離したときに全体を1回の編集で塗る (//undo 1回で全部戻る)",
                "オフ: なぞりながら次々に塗る (1回ごとに //undo が要る)", "(グラデーションの向きが「なぞった線に沿って」のときは、いつもオンと同じ)", "クリックで切り替え")), (p, c) -> {
            s.strokeGroup = !s.strokeGroup;
            refresh(p, Page.MAIN);
        });
        put(holder, GRID[21], item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
                "ブラシを持って 右クリック = 見ている所に使う (押しつづけると続けて使う)",
                "Shift + 右クリック = 1回だけプレビュー / 左クリック = この画面",
                "ぼかし・強さ・形は、塗るにも自動陰影にも効く",
                "コマンドは /ibrush help")), null);
    }

    // ---- pattern ---------------------------------------------------------------------------------------------------------------

    private void pattern(Holder holder, BrushSettings s) {
        if (s.mode == BrushMode.BIOME) {
            biomes(holder, s);
            return;
        }
        boolean gradient = s.patternMode == BrushSettings.PatternMode.GRADIENT;
        BrushStore store = module.store();
        put(holder, GRID[0], item(gradient ? Material.PRISMARINE_SHARD : Material.WHITE_CONCRETE, "置き方: " + (gradient ? "グラデーション" : "ばらまき (割合)"), NamedTextColor.GOLD, List.of(
                "ブロックの一覧は、どちらの置き方でも共通",
                "グラデーション: 一覧の順に、割合の分だけ長く、ブロックを並べる",
                "ばらまき: 割合に応じて、ばらばらに置く", "クリックで切り替え")), (p, c) -> {
            s.patternMode = gradient ? BrushSettings.PatternMode.RANDOM : BrushSettings.PatternMode.GRADIENT;
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[1], item(Material.EMERALD, "手に持つブロックを足す", NamedTextColor.GREEN, List.of("メインハンドのブロックを一覧の最後に足す", "自分のインベントリのブロックをクリックしても足せる")), (p, c) -> {
            addPattern(s, p.getInventory().getItemInMainHand().getType());
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[2], item(Material.CHEST, "ホットバーのブロックを全部足す", NamedTextColor.GREEN, List.of("ホットバーの9スロットのブロックを足す")), (p, c) -> {
            for (int slot = 0; slot < 9; slot++) addPattern(s, p.getInventory().getItem(slot) == null ? Material.AIR : p.getInventory().getItem(slot).getType());
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[3], item(Material.WATER_BUCKET, "割合をそろえる", NamedTextColor.AQUA, List.of("全部のブロックの割合を 1 にする")), (p, c) -> {
            for (BrushSettings.Entry entry : s.pattern) entry.weight = 1;
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[4], item(Material.LAVA_BUCKET, "全部消す", NamedTextColor.RED, List.of("一覧を空にする")), (p, c) -> {
            s.pattern.clear();
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[5], item(Material.WRITABLE_BOOK, "プリセットに保存", NamedTextColor.GREEN, List.of(
                "いまのブロックの一覧と割合と置き方を、自分のプリセット (brush.yml) に保存する",
                "名前は my-1, my-2 ... (/ibrush preset save <名前> でも保存できる)")), (p, c) -> {
            if (s.pattern.isEmpty()) {
                p.sendMessage(BrushModule.PREFIX + "一覧が空です。");
                return;
            }
            int number = store.presets().size() + 1;
            store.addPreset("my-" + number, s);
            p.sendMessage(BrushModule.PREFIX + (store.save() ? "プリセット my-" + number + " を保存しました。" : "保存できませんでした (ログを見てください)。"));
            refresh(p, Page.PATTERN);
        });
        // one item for the presets: click for the next (and use it), right click for the one before
        List<Gradients.Preset> presets = store.allPresets();
        Gradients.Preset current = holder.presetIndex >= 0 && holder.presetIndex < presets.size() ? presets.get(holder.presetIndex) : null;
        List<String> presetLore = new ArrayList<>();
        presetLore.add(current == null ? "まだ選んでいません" : describe(current));
        for (int index = 0; index < presets.size(); index++) presetLore.add((index == holder.presetIndex ? "▶ " : "   ") + presets.get(index).name());
        presetLore.add("左クリック: 次のプリセットを使う / 右クリック: 前のプリセットを使う");
        put(holder, GRID[6], item(Material.BOOKSHELF, "プリセット: " + (current == null ? "(選んでいない)" : current.name()), NamedTextColor.AQUA, presetLore), (p, c) -> {
            if (presets.isEmpty()) return;
            holder.presetIndex = Math.floorMod(holder.presetIndex + (left(c) ? 1 : -1), presets.size());
            Gradients.apply(presets.get(holder.presetIndex), s, store.gradientBlacklist());
            refresh(p, Page.PATTERN);
        });
        int total = Math.max(1, s.totalWeight());
        for (int index = 0; index < Math.min(13, s.pattern.size()); index++) {
            BrushSettings.Entry entry = s.pattern.get(index);
            int at = index;
            put(holder, GRID[7 + index], item(entry.material, (index + 1) + ". " + name(entry.material), NamedTextColor.WHITE, List.of(
                    "割合: " + entry.weight + " (" + Math.round(100.0 * entry.weight / total) + "%)",
                    "左 +1 / 右 -1 / Shift+左 1つ前へ / Shift+右 取り除く", "Q (ドロップ) で、手に持つブロックに置き換える")), (p, c) -> {
                if (c == ClickType.SHIFT_RIGHT) s.pattern.remove(entry);
                else if (c == ClickType.SHIFT_LEFT) {
                    if (at > 0) {
                        s.pattern.remove(at);
                        s.pattern.add(at - 1, entry);
                    }
                } else if (c == ClickType.DROP || c == ClickType.CONTROL_DROP) {
                    Material held = p.getInventory().getItemInMainHand().getType();
                    if (usable(held)) entry.material = held;
                } else entry.weight = Math.max(1, Math.min(99, entry.weight + (left(c) ? 1 : -1)));
                refresh(p, Page.PATTERN);
            });
        }
        // the two ends the list can be made from
        put(holder, GRID[21], item(s.gradientFrom, "作る元: 最初のブロック " + name(s.gradientFrom), holder.pick == Pick.FROM ? NamedTextColor.GOLD : NamedTextColor.GREEN, List.of(
                holder.pick == Pick.FROM ? "→ 自分のインベントリのブロックをクリックしてください" : "左クリック: 次にクリックするブロックにする",
                "右クリック: 手に持つブロックにする", "「2つのブロックから一覧を作る」で、一覧がこの2つの間のブロックで置き換わる")), (p, c) -> setEnd(p, holder, s, Pick.FROM, left(c)));
        put(holder, GRID[22], item(s.gradientTo, "作る元: 最後のブロック " + name(s.gradientTo), holder.pick == Pick.TO ? NamedTextColor.GOLD : NamedTextColor.GREEN, List.of(
                holder.pick == Pick.TO ? "→ 自分のインベントリのブロックをクリックしてください" : "左クリック: 次にクリックするブロックにする",
                "右クリック: 手に持つブロックにする")), (p, c) -> setEnd(p, holder, s, Pick.TO, left(c)));
        put(holder, GRID[23], item(Material.LADDER, "間のブロックの数: " + s.gradientSteps, NamedTextColor.AQUA, List.of("2つのブロックの間に入れる、近い色のブロックの数 (0〜14)", "左クリック +1 / 右クリック -1")), (p, c) -> {
            s.gradientSteps = Math.max(0, Math.min(14, s.gradientSteps + (left(c) ? 1 : -1)));
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[24], item(Material.CRAFTING_TABLE, "2つのブロックから一覧を作る", NamedTextColor.GREEN, List.of(
                "最初と最後のブロックの間を、色の近いブロックでつないで、一覧に入れる (グラデーションの置き方になる)",
                "できたあとは、ブロックの順・割合・入れ替えを自由に直せる", "ブラックリスト (グラデーション用) のブロックは使わない")), (p, c) -> {
            s.pattern.clear();
            for (Material material : Gradients.ramp(s.gradientFrom, s.gradientTo, s.gradientSteps, store.gradientBlacklist())) s.pattern.add(new BrushSettings.Entry(material, 1));
            s.patternMode = BrushSettings.PatternMode.GRADIENT;
            refresh(p, Page.PATTERN);
        });
        if (gradient) {
            put(holder, GRID[20], item(Material.REPEATER, "向きを逆にする: " + onOff(s.gradientReverse), NamedTextColor.AQUA, List.of("オン: 最初と最後のブロックの位置が入れ替わる (0〜1 が 1〜0 になる)", "クリックで切り替え")), (p, c) -> {
                s.gradientReverse = !s.gradientReverse;
                refresh(p, Page.PATTERN);
            });
            put(holder, GRID[25], item(Material.COMPASS, "向き: " + s.gradientAxis.label, NamedTextColor.AQUA, List.of(s.gradientAxis.description, "クリックで切り替え")), (p, c) -> {
                BrushSettings.GradientAxis[] all = BrushSettings.GradientAxis.values();
                s.gradientAxis = all[Math.floorMod(s.gradientAxis.ordinal() + (left(c) ? 1 : -1), all.length)];
                refresh(p, Page.PATTERN);
            });
            put(holder, GRID[26], item(Material.SUGAR, "ざらつき: " + Math.round(s.dither * 100) + "%", NamedTextColor.AQUA, List.of("0%: きれいな帯 / 大きいほど、境目がまだらに混ざる", "左クリック +10% / 右クリック -10%")), (p, c) -> {
                s.dither = Math.max(0.0, Math.min(1.0, s.dither + (left(c) ? 0.1 : -0.1)));
                refresh(p, Page.PATTERN);
            });
            put(holder, GRID[27], item(Material.GLOWSTONE_DUST, "加算: " + onOff(s.additive), NamedTextColor.AQUA, List.of(
                    "オン: 同じ所にもう一度塗ると、いまの色が一覧の色の方へ少しずつ進む (上乗せ)", "進み方: " + Math.round(s.additiveRate * 100) + "% (右クリックで 25 → 50 → 75 → 100%)",
                    "オフ: 塗るたびに、一覧のブロックで置き換える", "左クリックで切り替え")), (p, c) -> {
                if (left(c)) s.additive = !s.additive;
                else s.additiveRate = s.additiveRate >= 0.99 ? 0.25 : s.additiveRate + 0.25;
                refresh(p, Page.PATTERN);
            });
        }
    }

    private static String describe(Gradients.Preset preset) {
        if (preset.generated()) return name(preset.from()) + " → " + name(preset.to()) + " (間 " + preset.steps() + ")";
        StringBuilder text = new StringBuilder(preset.gradient() ? "グラデーション: " : "ばらまき: ");
        for (int index = 0; index < Math.min(4, preset.parts().size()); index++) {
            if (index > 0) text.append(", ");
            text.append(name(preset.parts().get(index).material())).append(" x").append(preset.parts().get(index).weight());
        }
        if (preset.parts().size() > 4) text.append(" ほか").append(preset.parts().size() - 4);
        return text.toString();
    }

    private void setEnd(Player player, Holder holder, BrushSettings s, Pick which, boolean arm) {
        if (arm) {
            holder.pick = holder.pick == which ? Pick.NONE : which;
        } else {
            Material held = player.getInventory().getItemInMainHand().getType();
            if (usable(held)) {
                if (which == Pick.FROM) s.gradientFrom = held;
                else s.gradientTo = held;
            }
            holder.pick = Pick.NONE;
        }
        refresh(player, Page.PATTERN);
    }

    private void addPattern(BrushSettings s, Material material) {
        if (!usable(material)) return;
        BrushSettings.Entry entry = s.find(material);
        if (entry != null) entry.weight = Math.min(99, entry.weight + 1);
        else if (s.pattern.size() < 13) s.pattern.add(new BrushSettings.Entry(material, 1));
    }

    // ---- mask ------------------------------------------------------------------------------------------------------------------

    private void mask(Holder holder, BrushSettings s) {
        put(holder, GRID[0], item(Material.HOPPER, "ブロックのマスク: " + onOff(s.blockMaskOn), NamedTextColor.AQUA, List.of("リストのブロックだけに効かせる", "クリックで切り替え")), (p, c) -> {
            s.blockMaskOn = !s.blockMaskOn;
            refresh(p, Page.MASK);
        });
        put(holder, GRID[1], item(Material.ENDER_EYE, "逆マスク (除外): " + onOff(s.blockMaskInvert), NamedTextColor.AQUA, List.of("オン: リストのブロック「以外」に効かせる", "クリックで切り替え")), (p, c) -> {
            s.blockMaskInvert = !s.blockMaskInvert;
            refresh(p, Page.MASK);
        });
        put(holder, GRID[2], item(Material.EMERALD, "手に持つブロックを足す", NamedTextColor.GREEN, List.of("自分のインベントリのブロックをクリックしても足せる")), (p, c) -> {
            Material held = p.getInventory().getItemInMainHand().getType();
            if (usable(held)) s.blockMask.add(held);
            refresh(p, Page.MASK);
        });
        put(holder, GRID[3], item(Material.LAVA_BUCKET, "リストを空にする", NamedTextColor.RED, List.of()), (p, c) -> {
            s.blockMask.clear();
            refresh(p, Page.MASK);
        });
        int index = 7;
        for (Material material : s.blockMask) {
            if (index >= 21) break;
            Material shown = material;
            put(holder, GRID[index++], item(shown, name(shown), NamedTextColor.WHITE, List.of("クリックで取り除く")), (p, c) -> {
                s.blockMask.remove(shown);
                refresh(p, Page.MASK);
            });
        }
        put(holder, GRID[21], item(Material.LEVER, "角度のマスク: " + onOff(s.angleMaskOn), NamedTextColor.AQUA, List.of("表面の傾き (0°=平ら、90°=垂直な壁) が範囲の中の所だけに効かせる", "クリックで切り替え")), (p, c) -> {
            s.angleMaskOn = !s.angleMaskOn;
            refresh(p, Page.MASK);
        });
        put(holder, GRID[22], item(Material.ENDER_EYE, "逆マスク (除外): " + onOff(s.angleMaskInvert), NamedTextColor.AQUA, List.of("オン: 範囲「以外」の傾きに効かせる")), (p, c) -> {
            s.angleMaskInvert = !s.angleMaskInvert;
            refresh(p, Page.MASK);
        });
        put(holder, GRID[23], item(Material.STICK, "最小の角度: " + s.angleMin + "°", NamedTextColor.AQUA, List.of("左クリック +5° / 右クリック -5°")), (p, c) -> {
            s.angleMin = Math.max(0, Math.min(s.angleMax, s.angleMin + (left(c) ? 5 : -5)));
            refresh(p, Page.MASK);
        });
        put(holder, GRID[24], item(Material.BLAZE_ROD, "最大の角度: " + s.angleMax + "°", NamedTextColor.AQUA, List.of("左クリック +5° / 右クリック -5°")), (p, c) -> {
            s.angleMax = Math.max(s.angleMin, Math.min(180, s.angleMax + (left(c) ? 5 : -5)));
            refresh(p, Page.MASK);
        });
        put(holder, GRID[25], item(Material.BLACK_CONCRETE, "陰影のマスク: " + onOff(s.shadeMaskOn), NamedTextColor.AQUA, List.of("オン: 影になっている面だけに効かせる (太陽の向きは「陰影」のページ)", "トゥーン調がオンなら、影の段で絞れる", "クリックで切り替え")), (p, c) -> {
            s.shadeMaskOn = !s.shadeMaskOn;
            refresh(p, Page.MASK);
        });
        put(holder, GRID[26], item(Material.ENDER_EYE, "逆マスク (除外): " + onOff(s.shadeMaskInvert), NamedTextColor.AQUA, List.of("オン: 影「以外」(日なた) だけに効かせる")), (p, c) -> {
            s.shadeMaskInvert = !s.shadeMaskInvert;
            refresh(p, Page.MASK);
        });
        put(holder, GRID[27], item(Material.GRAY_DYE, "影の段 (トゥーン): " + (s.toonSteps > 1 ? s.shadeBand + " 以上" : "トゥーン調オフ"), NamedTextColor.AQUA, List.of(
                "トゥーン調で影を段に分けているとき、何段目より濃い影に効かせるか", "左クリック +1 / 右クリック -1")), (p, c) -> {
            s.shadeBand = Math.max(1, Math.min(Math.max(1, s.toonSteps - 1), s.shadeBand + (left(c) ? 1 : -1)));
            refresh(p, Page.MASK);
        });
    }

    // ---- height and position masks ------------------------------------------------------------------------------------------------

    private void position(Holder holder, BrushSettings s) {
        put(holder, GRID[0], item(Material.LADDER, "高さのマスク: " + onOff(s.heightMaskOn), NamedTextColor.AQUA, List.of("ある高さより上 (または下) のブロックだけに効かせる", "クリックで切り替え")), (p, c) -> {
            s.heightMaskOn = !s.heightMaskOn;
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[1], item(s.heightAbove ? Material.PISTON : Material.STICKY_PISTON, "向き: Y=" + s.heightValue + (s.heightAbove ? " 以上" : " 以下"), NamedTextColor.AQUA, List.of(
                "以上: その高さと、それより上のブロックだけ / 以下: その高さと、それより下のブロックだけ", "クリックで切り替え")), (p, c) -> {
            s.heightAbove = !s.heightAbove;
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[2], item(Material.SPYGLASS, "高さ (Y): " + s.heightValue, NamedTextColor.AQUA, List.of("左クリック +1 / 右クリック -1", "Shift で 8 ずつ")), (p, c) -> {
            s.heightValue = Math.max(-64, Math.min(320, s.heightValue + (left(c) ? 1 : -1) * (big(c) ? 8 : 1)));
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[3], item(Material.ENDER_PEARL, "いまの高さにする", NamedTextColor.AQUA, List.of("あなたが立っている高さ (足元のブロックの Y) を、高さにする")), (p, c) -> {
            s.heightValue = p.getLocation().getBlockY() - 1;
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[7], item(Material.HOPPER, "ブロックの上下のマスク: " + onOff(s.relationMaskOn), NamedTextColor.AQUA, List.of(
                "リストのブロックのすぐ下にあるブロック (または、すぐ上に乗っているブロック) だけに効かせる", "斜めに隣り合う列も見る", "クリックで切り替え")), (p, c) -> {
            s.relationMaskOn = !s.relationMaskOn;
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[8], item(s.relationUnder ? Material.DROPPER : Material.DISPENSER, "向き: " + (s.relationUnder ? "リストのブロックの「下」にあるもの" : "リストのブロックの「上」に乗っているもの"), NamedTextColor.AQUA, List.of(
                "下: 上にリストのブロックがある所 (例: 草の下の土) / 上: 下にリストのブロックがある所 (例: 岩の上の苔)", "クリックで切り替え")), (p, c) -> {
            s.relationUnder = !s.relationUnder;
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[9], item(Material.COMPARATOR, "探す距離: " + s.relationReach, NamedTextColor.AQUA, List.of("リストのブロックを何ブロック先まで探すか", "左クリック +1 / 右クリック -1 (Shift で 4)")), (p, c) -> {
            s.relationReach = Math.max(1, Math.min(48, s.relationReach + (left(c) ? 1 : -1) * (big(c) ? 4 : 1)));
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[10], item(Material.EMERALD, "手に持つブロックを足す", NamedTextColor.GREEN, List.of("自分のインベントリのブロックをクリックしても足せる")), (p, c) -> {
            Material held = p.getInventory().getItemInMainHand().getType();
            if (usable(held)) s.relationBlocks.add(held);
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[11], item(Material.LAVA_BUCKET, "リストを空にする", NamedTextColor.RED, List.of()), (p, c) -> {
            s.relationBlocks.clear();
            refresh(p, Page.POSITION);
        });
        int index = 14;
        for (Material material : new ArrayList<>(s.relationBlocks)) {
            if (index >= 21) break;
            put(holder, GRID[index++], item(material, name(material), NamedTextColor.WHITE, List.of("クリックで取り除く")), (p, c) -> {
                s.relationBlocks.remove(material);
                refresh(p, Page.POSITION);
            });
        }
        // the block the gradient starts from (the gradient's direction "特定のブロックから", set on the pattern page)
        List<String> referenceLore = new ArrayList<>();
        referenceLore.add("模様のページで、グラデーションの向きを「特定のブロックから」にすると、このブロックが最初のブロックの位置になる");
        for (Material material : s.referenceBlocks) referenceLore.add("・" + name(material));
        referenceLore.add("左クリック: 手に持つブロックを足す / 右クリック: 最後の1つを取り除く / Shift+右クリック: 全部消す");
        put(holder, GRID[21], item(Material.TARGET, "グラデーションの起点のブロック (" + s.referenceBlocks.size() + ")", NamedTextColor.GOLD, referenceLore), (p, c) -> {
            if (c == ClickType.SHIFT_RIGHT) s.referenceBlocks.clear();
            else if (left(c)) {
                Material held = p.getInventory().getItemInMainHand().getType();
                if (usable(held)) s.referenceBlocks.add(held);
            } else if (!s.referenceBlocks.isEmpty()) {
                Material last = null;
                for (Material material : s.referenceBlocks) last = material;
                s.referenceBlocks.remove(last);
            }
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[22], item(s.referenceDown ? Material.DROPPER : Material.DISPENSER, "起点からの向き: " + (s.referenceDown ? "起点の「下」へ" : "起点の「上」へ"), NamedTextColor.AQUA, List.of(
                "下へ: 起点のブロックが上にあり、そこから下へ離れるほど最後のブロックへ (例: 草から下の土へ)",
                "上へ: 起点のブロックが下にあり、そこから上へ離れるほど最後のブロックへ", "クリックで切り替え")), (p, c) -> {
            s.referenceDown = !s.referenceDown;
            refresh(p, Page.POSITION);
        });
        put(holder, GRID[23], item(Material.COMPARATOR, "起点から最後までの距離: " + s.referenceSpan, NamedTextColor.AQUA, List.of("起点から何ブロック離れたら、最後のブロックになるか", "左クリック +1 / 右クリック -1 (Shift で 4)")), (p, c) -> {
            s.referenceSpan = Math.max(1, Math.min(48, s.referenceSpan + (left(c) ? 1 : -1) * (big(c) ? 4 : 1)));
            refresh(p, Page.POSITION);
        });
    }

    // ---- sun and shade -----------------------------------------------------------------------------------------------------------

    private static String compass(int azimuth) {
        String[] names = {"北", "北東", "東", "南東", "南", "南西", "西", "北西"};
        return names[Math.floorMod(Math.round(azimuth / 45.0f), 8)];
    }

    private void sun(Holder holder, BrushSettings s) {
        put(holder, GRID[0], item(Material.RED_STAINED_GLASS_PANE, "方位 -15°", NamedTextColor.RED, List.of("太陽を左回りに動かす (Shift で 45°)")), (p, c) -> {
            s.sunAzimuth = Math.floorMod(s.sunAzimuth - (big(c) ? 45 : 15), 360);
            refresh(p, Page.SUN);
        });
        put(holder, GRID[1], item(Material.COMPASS, "太陽の方位: " + s.sunAzimuth + "° (" + compass(s.sunAzimuth) + ")", NamedTextColor.GOLD, List.of("0°=北 90°=東 180°=南 270°=西", "影はこの反対側にできる")), null);
        put(holder, GRID[2], item(Material.LIME_STAINED_GLASS_PANE, "方位 +15°", NamedTextColor.GREEN, List.of("太陽を右回りに動かす (Shift で 45°)")), (p, c) -> {
            s.sunAzimuth = Math.floorMod(s.sunAzimuth + (big(c) ? 45 : 15), 360);
            refresh(p, Page.SUN);
        });
        put(holder, GRID[3], item(Material.ENDER_PEARL, "いま見ている方向に太陽を置く", NamedTextColor.AQUA, List.of("あなたが向いている方位を太陽の方位にする")), (p, c) -> {
            // minecraft yaw: 0 = south, 90 = west; the compass: 0 = north, 90 = east
            s.sunAzimuth = Math.floorMod((int) Math.round(p.getLocation().getYaw() + 180.0), 360);
            refresh(p, Page.SUN);
        });
        put(holder, GRID[4], item(Material.RED_STAINED_GLASS_PANE, "高さ -5°", NamedTextColor.RED, List.of("太陽を低くする (影が長くなる)")), (p, c) -> {
            s.sunElevation = Math.max(5, s.sunElevation - (big(c) ? 15 : 5));
            refresh(p, Page.SUN);
        });
        put(holder, GRID[5], item(Material.CLOCK, "太陽の高さ: " + s.sunElevation + "°", NamedTextColor.GOLD, List.of("5°=朝夕 90°=真上")), null);
        put(holder, GRID[6], item(Material.LIME_STAINED_GLASS_PANE, "高さ +5°", NamedTextColor.GREEN, List.of("太陽を高くする")), (p, c) -> {
            s.sunElevation = Math.min(90, s.sunElevation + (big(c) ? 15 : 5));
            refresh(p, Page.SUN);
        });
        put(holder, GRID[7], item(Material.ORANGE_DYE, "朝", NamedTextColor.GOLD, List.of("東の低い太陽 (方位90° 高さ20°)")), (p, c) -> preset(p, s, 90, 20));
        put(holder, GRID[8], item(Material.YELLOW_DYE, "昼", NamedTextColor.YELLOW, List.of("南の高い太陽 (方位180° 高さ65°)")), (p, c) -> preset(p, s, 180, 65));
        put(holder, GRID[9], item(Material.RED_DYE, "夕", NamedTextColor.RED, List.of("西の低い太陽 (方位270° 高さ20°)")), (p, c) -> preset(p, s, 270, 20));
        put(holder, GRID[10], item(Material.WHITE_DYE, "斜め上 (標準)", NamedTextColor.WHITE, List.of("南東の斜め上 (方位135° 高さ45°)")), (p, c) -> preset(p, s, 135, 45));
        put(holder, GRID[14], item(Material.BLACK_DYE, "陰の濃さ: " + Math.round(s.shadeDepth * 100) + "%", NamedTextColor.AQUA, List.of("真っ暗な面を、どれくらい暗い色のブロックにするか", "左クリック +5% / 右クリック -5%")), (p, c) -> {
            s.shadeDepth = Math.max(0.1, Math.min(0.8, s.shadeDepth + (left(c) ? 0.05 : -0.05)));
            refresh(p, Page.SUN);
        });
        put(holder, GRID[15], item(Material.OBSIDIAN, "影を落とす: " + onOff(s.castShadows), NamedTextColor.AQUA, List.of("オン: 壁や木に遮られた所も影にする (少し重い)", "オフ: 面の向きだけで決める", "クリックで切り替え")), (p, c) -> {
            s.castShadows = !s.castShadows;
            refresh(p, Page.SUN);
        });
        put(holder, GRID[16], item(Material.GRAY_CONCRETE, "トゥーン調: " + (s.toonSteps > 1 ? s.toonSteps + "段" : "オフ"), NamedTextColor.AQUA, List.of(
                "影をなめらかにせず、段で区切る (アニメ塗りのように、同じ段は同じ暗さ)", "自動陰影にも、陰影のマスクにも効く",
                "左クリック +1 段 / 右クリック -1 段 (2〜6段、1つ下げるとオフ)")), (p, c) -> {
            int next = s.toonSteps + (left(c) ? 1 : -1);
            s.toonSteps = next < 2 ? (left(c) ? 2 : 0) : Math.min(6, next);
            s.shadeBand = Math.max(1, Math.min(Math.max(1, s.toonSteps - 1), s.shadeBand));
            refresh(p, Page.SUN);
        });
        // the other sources of the shade
        put(holder, GRID[11], item(Material.SUNFLOWER, "光(太陽): " + onOff(s.lightOn), NamedTextColor.AQUA, List.of("太陽の向きと、遮る物による影", "オフにすると、下の源 (AO・曲率・ノイズ・セル) だけで影を決める", "クリックで切り替え")), (p, c) -> {
            s.lightOn = !s.lightOn;
            refresh(p, Page.SUN);
        });
        put(holder, GRID[12], item(Material.COAL, "アンビエント(AO): " + onOff(s.ambientOn), NamedTextColor.AQUA, List.of("まわりを囲まれた所 (くぼみ・隅・壁ぎわ) ほど暗くする", "太陽とは別の影", "クリックで切り替え")), (p, c) -> {
            s.ambientOn = !s.ambientOn;
            refresh(p, Page.SUN);
        });
        put(holder, GRID[13], item(Material.CHARCOAL, "AOの強さ: " + Math.round(s.ambientStrength * 100) + "%", NamedTextColor.AQUA, List.of("左クリック +10% / 右クリック -10%")), (p, c) -> {
            s.ambientStrength = Math.max(0.1, Math.min(1.0, s.ambientStrength + (left(c) ? 0.1 : -0.1)));
            refresh(p, Page.SUN);
        });
        put(holder, GRID[18], item(Material.STONE_PICKAXE, "曲率(岩肌): " + onOff(s.curvatureOn), NamedTextColor.AQUA, List.of("地形の凹凸で影をつける (岩肌の表現に)", "凹み (割れ目) を暗くするか、出っぱり (稜線) を暗くするかを選べる", "クリックで切り替え")), (p, c) -> {
            s.curvatureOn = !s.curvatureOn;
            refresh(p, Page.SUN);
        });
        put(holder, GRID[19], item(Material.IRON_PICKAXE, "曲率の向き: " + (s.curvatureConvex ? "出っぱりを暗く" : "凹みを暗く"), NamedTextColor.AQUA, List.of("凹み: 割れ目・隅に影 / 出っぱり: 稜線・角に影 (すり減った感じ)", "クリックで切り替え")), (p, c) -> {
            s.curvatureConvex = !s.curvatureConvex;
            refresh(p, Page.SUN);
        });
        put(holder, GRID[20], item(Material.FLINT, "曲率の強さ: " + Math.round(s.curvatureStrength * 100) + "%", NamedTextColor.AQUA, List.of("左クリック +10% / 右クリック -10%")), (p, c) -> {
            s.curvatureStrength = Math.max(0.1, Math.min(1.0, s.curvatureStrength + (left(c) ? 0.1 : -0.1)));
            refresh(p, Page.SUN);
        });
        put(holder, GRID[21], item(Material.SPYGLASS, "AOの届く距離: " + s.aoRadius, NamedTextColor.AQUA, List.of("光線を何ブロック先まで飛ばして、囲まれ具合を見るか (1〜6)", "大きいほど広い範囲の囲まれ方を見る", "左クリック +1 / 右クリック -1")), (p, c) -> {
            s.aoRadius = Math.max(1, Math.min(6, s.aoRadius + (left(c) ? 1 : -1)));
            refresh(p, Page.SUN);
        });
        put(holder, GRID[22], item(Material.COMPARATOR, "AOの光線の数: " + s.aoSamples, NamedTextColor.AQUA, List.of("多いほど滑らかで、重くなる (8〜48)", "左クリック +4 / 右クリック -4")), (p, c) -> {
            s.aoSamples = Math.max(8, Math.min(48, s.aoSamples + (left(c) ? 4 : -4)));
            refresh(p, Page.SUN);
        });
        put(holder, GRID[24], item(Material.SCULK_SENSOR, "AOの「囲まれている」しきい値: " + Math.round(s.aoBias * 100) + "%", NamedTextColor.AQUA, List.of(
                "どれだけ囲まれていたら暗くし始めるか (0〜80%)。大きいほど、深い隅だけが暗くなり、開けた所は暗くならない", "左クリック +5% / 右クリック -5%")), (p, c) -> {
            s.aoBias = Math.max(0.0, Math.min(0.8, Math.round((s.aoBias + (left(c) ? 0.05 : -0.05)) * 100.0) / 100.0));
            refresh(p, Page.SUN);
        });
        put(holder, GRID[25], item(Material.OAK_LEAVES, "AOで葉と木を壁に数える: " + onOff(s.aoCountPlants), NamedTextColor.AQUA, List.of(
                "オフ: 葉と丸太は「囲んでいるもの」に数えない (木の下が暗くならない)", "オン: 石や土と同じく壁として数える", "クリックで切り替え")), (p, c) -> {
            s.aoCountPlants = !s.aoCountPlants;
            refresh(p, Page.SUN);
        });
        put(holder, GRID[23], item(Material.REDSTONE_TORCH, "AOのくっきり度: " + String.format(java.util.Locale.ROOT, "%.1f", s.aoPower), NamedTextColor.AQUA, List.of("1より大きい: 深い隅だけが暗くなる / 1より小さい: 開けた所も暗くなる (0.4〜3)", "左クリック +0.2 / 右クリック -0.2")), (p, c) -> {
            s.aoPower = Math.max(0.4, Math.min(3.0, Math.round((s.aoPower + (left(c) ? 0.2 : -0.2)) * 10.0) / 10.0));
            refresh(p, Page.SUN);
        });
        put(holder, GRID[17], item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
                "影の源は足し合わさる (光・AO・曲率・ノイズ・セル)。陰影のマスクにも効く",
                "選択ツールの画面の「選択範囲に自動陰影」で、選んだ範囲全体にも使える",
                "基本のページで「自動陰影」を選び、ブラシを影にしたい所に使う",
                "太陽と反対側を向いた面や、遮られた面が、いまの色から",
                "自動で暗いブロックになる (階段・ハーフ・壁も形を保つ)",
                "模様にブロックを入れると、その中からだけ選ぶ")), null);
    }

    private void preset(Player player, BrushSettings s, int azimuth, int elevation) {
        s.sunAzimuth = azimuth;
        s.sunElevation = elevation;
        refresh(player, Page.SUN);
    }

    // ---- noise and cells ------------------------------------------------------------------------------------------------------------

    private void noisePage(Holder holder, BrushSettings s) {
        Procedural.NoiseType[] types = Procedural.NoiseType.values();
        List<String> typeLore = new ArrayList<>();
        typeLore.add(s.noiseType.description);
        for (Procedural.NoiseType type : types) typeLore.add((type == s.noiseType ? "▶ " : "   ") + type.label);
        typeLore.add("左クリック: 次の種類 / 右クリック: 前の種類");
        typeLore.add("ノイズを使う所すべてに効く: 模様のノイズ・グラデーションの向き「ノイズ」・影のノイズ");
        put(holder, GRID[0], item(Material.AMETHYST_SHARD, "ノイズの種類: " + s.noiseType.label, NamedTextColor.GOLD, typeLore), (p, c) -> {
            s.noiseType = types[Math.floorMod(s.noiseType.ordinal() + (left(c) ? 1 : -1), types.length)];
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[1], item(Material.COMPARATOR, "重ねる回数: " + s.noiseOctaves, NamedTextColor.AQUA, List.of("細かい層を何枚重ねるか (1〜5)。増やすと細かい凹凸が足される", "左クリック +1 / 右クリック -1")), (p, c) -> {
            s.noiseOctaves = Math.max(1, Math.min(5, s.noiseOctaves + (left(c) ? 1 : -1)));
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[2], item(Material.REPEATER, "細かい層の強さ: " + Math.round(s.noisePersistence * 100) + "%", NamedTextColor.AQUA, List.of("重ねた細かい層が、1つ前の層に比べてどれだけ強いか (20〜80%)", "左クリック +10% / 右クリック -10%")), (p, c) -> {
            s.noisePersistence = Math.max(0.2, Math.min(0.8, s.noisePersistence + (left(c) ? 0.1 : -0.1)));
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[3], item(Material.BLACK_CONCRETE, "コントラスト: " + String.format(java.util.Locale.ROOT, "%.2f", s.noiseContrast), NamedTextColor.AQUA, List.of("明暗をどれだけはっきり分けるか (0.5〜3)", "左クリック +0.25 / 右クリック -0.25")), (p, c) -> {
            s.noiseContrast = Math.max(0.5, Math.min(3.0, s.noiseContrast + (left(c) ? 0.25 : -0.25)));
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[4], item(Material.WHEAT_SEEDS, "ノイズの番号: " + s.noiseSeed, NamedTextColor.AQUA, List.of("変えると別の模様になる", "左クリック +1 / 右クリック -1 (Shift で 10)")), (p, c) -> {
            s.noiseSeed += (left(c) ? 1 : -1) * (big(c) ? 10 : 1);
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[5], item(Material.AMETHYST_BLOCK, "模様のノイズ: " + onOff(s.noise), NamedTextColor.AQUA, List.of("模様の選び方を、ばらばらではなく斑(まだら)の塊にする", "クリックで切り替え")), (p, c) -> {
            s.noise = !s.noise;
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[6], item(Material.SPYGLASS, "模様・グラデーションの大きさ: " + s.noiseScale, NamedTextColor.AQUA, List.of("模様のノイズと、グラデーションの向き「ノイズ」の、1つの塊の大きさ (ブロック)", "左クリック +0.5 / 右クリック -0.5")), (p, c) -> {
            s.noiseScale = Math.max(0.5, Math.min(16.0, s.noiseScale + (left(c) ? 0.5 : -0.5)));
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[7], item(Material.COAL_BLOCK, "影のノイズ: " + onOff(s.noiseShadeOn), NamedTextColor.AQUA, List.of("ノイズで影をつける (汚れ・むらの表現に)", "クリックで切り替え")), (p, c) -> {
            s.noiseShadeOn = !s.noiseShadeOn;
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[8], item(Material.REDSTONE, "影のノイズの強さ: " + Math.round(s.noiseShadeStrength * 100) + "%", NamedTextColor.AQUA, List.of("左クリック +10% / 右クリック -10%")), (p, c) -> {
            s.noiseShadeStrength = Math.max(0.1, Math.min(1.0, s.noiseShadeStrength + (left(c) ? 0.1 : -0.1)));
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[9], item(Material.COMPARATOR, "影のノイズの大きさ: " + s.noiseShadeScale, NamedTextColor.AQUA, List.of("左クリック +0.5 / 右クリック -0.5")), (p, c) -> {
            s.noiseShadeScale = Math.max(1.0, Math.min(16.0, s.noiseShadeScale + (left(c) ? 0.5 : -0.5)));
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[14], item(Material.HONEYCOMB, "セル: " + onOff(s.cellOn), NamedTextColor.AQUA, List.of("細胞状 (ボロノイ) の模様で影をつける (ひび・石畳・鱗の表現に)", "クリックで切り替え")), (p, c) -> {
            s.cellOn = !s.cellOn;
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[15], item(Material.GLOWSTONE_DUST, "セルの強さ: " + Math.round(s.cellStrength * 100) + "%", NamedTextColor.AQUA, List.of("左クリック +10% / 右クリック -10%")), (p, c) -> {
            s.cellStrength = Math.max(0.1, Math.min(1.0, s.cellStrength + (left(c) ? 0.1 : -0.1)));
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[16], item(Material.HONEYCOMB_BLOCK, "セルの大きさ: " + s.cellSize, NamedTextColor.AQUA, List.of("1つのセルのおよその大きさ (ブロック)", "左クリック +1 / 右クリック -1")), (p, c) -> {
            s.cellSize = Math.max(2.0, Math.min(20.0, s.cellSize + (left(c) ? 1.0 : -1.0)));
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[17], item(Material.COBBLESTONE_WALL, "セルの影: " + (s.cellEdges ? "境目" : "セルごと"), NamedTextColor.AQUA, List.of("境目: セルの境目を暗くする (ひび割れ風) / セルごと: 1つおきのセルを暗くする", "クリックで切り替え")), (p, c) -> {
            s.cellEdges = !s.cellEdges;
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[18], item(s.cellAngular ? Material.STONE_BRICKS : Material.SLIME_BALL, "セルの形: " + (s.cellAngular ? "角ばった破片" : "丸い細胞"), NamedTextColor.AQUA, List.of("角ばった破片: 立方体で距離をはかるので、平らな面の欠片になる", "クリックで切り替え")), (p, c) -> {
            s.cellAngular = !s.cellAngular;
            refresh(p, Page.NOISE);
        });
        put(holder, GRID[21], item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
                "ノイズの種類・重ねる回数・強さ・コントラスト・番号は、ノイズを使うすべての所で共通",
                "使う所: 模様のノイズ / グラデーションの向き「ノイズ」(模様のページ) / 影のノイズ",
                "セルは影の源。ノイズの種類「セルラー」「ボロノイ」「シャード」は、模様やグラデーションにも使える")), null);
    }

    // ---- preset management ----------------------------------------------------------------------------------------------------------

    private void presetsPage(Holder holder, BrushSettings s) {
        BrushStore store = module.store();
        List<Gradients.Preset> all = store.allPresets();
        int perPage = 21;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        holder.presetPage = Math.max(0, Math.min(pages - 1, holder.presetPage));
        put(holder, GRID[0], item(Material.WRITABLE_BOOK, "いまの一覧を新しいプリセットに保存", NamedTextColor.GREEN, List.of(
                "模様のページの、ブロックの一覧と割合と置き方を、新しいプリセット (my-1, my-2 ...) に保存する", "すぐ brush.yml に書く")), (p, c) -> {
            if (s.pattern.isEmpty()) {
                p.sendMessage(BrushModule.PREFIX + "一覧が空です。");
                return;
            }
            int number = 1;
            while (true) {
                final String candidate = "my-" + number;
                if (all.stream().noneMatch(preset -> preset.name().equals(candidate))) break;
                number++;
            }
            store.addPreset("my-" + number, s);
            p.sendMessage(BrushModule.PREFIX + (store.save() ? "プリセット my-" + number + " を保存しました。" : "保存できませんでした (ログを見てください)。"));
            holder.presetPage = pages;
            refresh(p, Page.PRESETS);
        });
        put(holder, GRID[1], item(Material.ANVIL, "ファイルに書く", NamedTextColor.GREEN, List.of("自作のプリセット全部を brush.yml に書く (削除・上書きは、そのたびに書くので、通常は要らない)")), (p, c) ->
                p.sendMessage(BrushModule.PREFIX + (store.save() ? "保存しました。" : "保存できませんでした (ログを見てください)。")));
        put(holder, GRID[2], item(Material.ARROW, "前のページ", NamedTextColor.WHITE, List.of()), (p, c) -> {
            holder.presetPage = Math.max(0, holder.presetPage - 1);
            refresh(p, Page.PRESETS);
        });
        put(holder, GRID[3], item(Material.ARROW, "次のページ", NamedTextColor.WHITE, List.of()), (p, c) -> {
            holder.presetPage = Math.min(pages - 1, holder.presetPage + 1);
            refresh(p, Page.PRESETS);
        });
        put(holder, GRID[4], item(Material.BOOK, "プリセット " + all.size() + "件 (" + (holder.presetPage + 1) + "/" + pages + ")", NamedTextColor.GOLD, List.of(
                "左クリック: 使う", "Shift+右クリック: 削除 (自作のみ)", "Q (ドロップ): いまの一覧で上書き (自作のみ)", "名前を変える: /ibrush preset rename <いまの名前> <新しい名前>")), null);
        for (int index = 0; index < perPage; index++) {
            int at = holder.presetPage * perPage + index;
            if (at >= all.size()) break;
            Gradients.Preset preset = all.get(at);
            boolean custom = store.presets().stream().anyMatch(own -> own.name().equals(preset.name()));
            Material icon = preset.generated() ? preset.from() : preset.parts().get(0).material();
            put(holder, GRID[7 + index], item(icon, preset.name(), custom ? NamedTextColor.GREEN : NamedTextColor.WHITE, List.of(
                    describe(preset), custom ? "自作" : "組み込み", "左クリック: 使う" + (custom ? " / Shift+右クリック: 削除 / Q: いまの一覧で上書き" : ""))), (p, c) -> {
                if (c == ClickType.SHIFT_RIGHT && custom) {
                    store.removePreset(preset.name());
                    p.sendMessage(BrushModule.PREFIX + (store.save() ? "削除しました: " + preset.name() : "削除しましたが、ファイルに書けませんでした。"));
                } else if ((c == ClickType.DROP || c == ClickType.CONTROL_DROP) && custom) {
                    if (s.pattern.isEmpty()) {
                        p.sendMessage(BrushModule.PREFIX + "一覧が空です。");
                        return;
                    }
                    store.addPreset(preset.name(), s);
                    p.sendMessage(BrushModule.PREFIX + (store.save() ? "上書きしました: " + preset.name() : "上書きしましたが、ファイルに書けませんでした。"));
                } else if (left(c)) {
                    Gradients.apply(preset, s, store.gradientBlacklist());
                    p.sendMessage(BrushModule.PREFIX + "使います: " + preset.name());
                }
                refresh(p, Page.PRESETS);
            });
        }
    }

    // ---- selection --------------------------------------------------------------------------------------------------------------

    private void select(Holder holder, Player player) {
        SelectionTool tool = module.selection();
        SelectionTool.State state = tool.state(player);
        put(holder, GRID[0], item(Material.COMPASS, "選び方: " + state.mode.label, NamedTextColor.GOLD, List.of(state.mode.description, "クリックで切り替え (選択ツールで右クリックすると選ぶ)")), (p, c) -> {
            SelectionTool.Mode[] all = SelectionTool.Mode.values();
            state.mode = all[(state.mode.ordinal() + 1) % all.length];
            refresh(p, Page.SELECT);
        });
        put(holder, GRID[1], item(Material.COMPARATOR, "しきい値 (ファジー): " + state.threshold, NamedTextColor.AQUA, List.of("大きいほど、色が離れたブロックまで選ぶ (0〜100)", "左クリック +5 / 右クリック -5 (Shift で 10)")), (p, c) -> {
            state.threshold = Math.max(0, Math.min(100, state.threshold + (left(c) ? 1 : -1) * (big(c) ? 10 : 5)));
            refresh(p, Page.SELECT);
        });
        put(holder, GRID[2], item(Material.SPYGLASS, "半径: " + state.radius, NamedTextColor.AQUA, List.of("クリックした所から、ここまでの範囲だけ探す (最大 " + tool.maxRadius() + ")", "左クリック +4 / 右クリック -4 (Shift で 16)")), (p, c) -> {
            state.radius = Math.max(1, Math.min(tool.maxRadius(), state.radius + (left(c) ? 1 : -1) * (big(c) ? 16 : 4)));
            refresh(p, Page.SELECT);
        });
        put(holder, GRID[3], item(Material.GRASS_BLOCK, "表面だけ (隣接): " + onOff(state.surfaceOnly), NamedTextColor.AQUA, List.of("オン: 空気に触れているブロックだけ選ぶ (ファジーはいつも表面だけ)", "クリックで切り替え")), (p, c) -> {
            state.surfaceOnly = !state.surfaceOnly;
            refresh(p, Page.SELECT);
        });
        put(holder, GRID[4], item(Material.SPECTRAL_ARROW, "選択を表示: " + onOff(state.show), NamedTextColor.AQUA, List.of("選んだ範囲の縁を、近くだけ粒子で見せる (あなたにだけ)", "水色=選択 / 橙=反転中", "クリックで切り替え")), (p, c) -> {
            tool.toggleShow(p);
            refresh(p, Page.SELECT);
        });
        boolean inverted = state.selection != null && state.selection.inverted;
        put(holder, GRID[5], item(Material.ENDER_EYE, "選択を反転: " + onOff(inverted), NamedTextColor.AQUA, List.of("オン: ブラシは、選んだ所の「外」だけに効く", "反転しても計算は増えない", "クリックで切り替え")), (p, c) -> {
            tool.invert(p);
            refresh(p, Page.SELECT);
        });
        put(holder, GRID[6], item(Material.LAVA_BUCKET, "選択を解除", NamedTextColor.RED, List.of("選んだ範囲を捨てる")), (p, c) -> {
            tool.clear(p);
            refresh(p, Page.SELECT);
        });
        put(holder, GRID[7], item(Material.SNOWBALL, "色をぼかす", NamedTextColor.GREEN, List.of("選んだ範囲の色を、まわりと平均してなじませる", "範囲の中にあるブロックだけを使う / //undo で戻せる", "反転中は使えない")), (p, c) -> {
            p.closeInventory();
            tool.blend(p);
        });
        put(holder, GRID[8], item(Material.HOPPER, "ブラシを選択に限る: " + onOff(state.limitBrush), NamedTextColor.AQUA, List.of("オン: ブラシは選んだ範囲の中だけに効く", "クリックで切り替え")), (p, c) -> {
            state.limitBrush = !state.limitBrush;
            refresh(p, Page.SELECT);
        });
        put(holder, GRID[12], item(state.blocksAsMask ? Material.GRASS_BLOCK : Material.MAP, "選択の使い方: " + (state.blocksAsMask ? "ブロックのマスク" : "範囲"), NamedTextColor.AQUA, List.of(
                "範囲: 選んだ場所のブロックだけにブラシが効く",
                "ブロックのマスク: 選んだブロックと同じ種類のブロックなら、どこにでもブラシが効く",
                "クリックで切り替え")), (p, c) -> {
            state.blocksAsMask = !state.blocksAsMask;
            refresh(p, Page.SELECT);
        });
        put(holder, GRID[9], item(Material.BLAZE_ROD, "選択ツールをもらう", NamedTextColor.GOLD, List.of("持っていなければ、選択ツールのアイテムを渡す")), (p, c) -> tool.give(p));
        put(holder, GRID[11], item(Material.BLACK_DYE, "選択範囲に自動陰影", NamedTextColor.GREEN, List.of("選んだ範囲全体に、いまの陰影の設定 (光・AO・曲率・ノイズ・セル・トゥーン) を適用する",
                "模様のブロックが入っていれば、その中からだけ選ぶ / //undo で戻せる", "反転中は使えない")), (p, c) -> {
            p.closeInventory();
            tool.shade(p);
        });
        put(holder, GRID[10], item(Material.BOOK, "いまの選択", NamedTextColor.GOLD, List.of(
                state.selection == null ? "選んでいません" : state.selection.blocks.size() + "ブロック" + (inverted ? " (反転中)" : ""),
                state.job != null ? "探している途中です…" : "")), null);
    }

    // ---- blacklist ------------------------------------------------------------------------------------------------------------------

    private void blacklist(Holder holder) {
        BrushStore store = module.store();
        Set<Material> list = holder.shadeList ? store.shadeBlacklist() : store.gradientBlacklist();
        put(holder, GRID[0], item(holder.shadeList ? Material.BLACK_DYE : Material.PRISMARINE_SHARD, "いま見ているリスト: " + (holder.shadeList ? "陰影" : "グラデーション") + " (" + list.size() + ")", NamedTextColor.GOLD, List.of(
                "グラデーション: 2つのブロックからグラデーションを作るとき、途中に使わないブロック",
                "陰影: 自動陰影が、暗いブロックとして選ばないブロック",
                "全員共通 (brush.yml)。「保存」を押すまでファイルには書かれない", "クリックで、もう一方のリストに切り替え")), (p, c) -> {
            holder.shadeList = !holder.shadeList;
            refresh(p, Page.BLACKLIST);
        });
        put(holder, GRID[1], item(Material.EMERALD, "手に持つブロックを足す", NamedTextColor.GREEN, List.of("自分のインベントリのブロックをクリックしても足せる")), (p, c) -> {
            Material held = p.getInventory().getItemInMainHand().getType();
            if (usable(held)) list.add(held);
            refresh(p, Page.BLACKLIST);
        });
        put(holder, GRID[2], item(Material.LAVA_BUCKET, "全部消す", NamedTextColor.RED, List.of("このリストを空にする (保存するまでファイルはそのまま)")), (p, c) -> {
            list.clear();
            refresh(p, Page.BLACKLIST);
        });
        put(holder, GRID[3], item(Material.WRITABLE_BOOK, "保存", NamedTextColor.GREEN, List.of("2つのリストを plugins/iruuRPG/brush.yml に書く", "クリックで保存")), (p, c) -> {
            p.sendMessage(BrushModule.PREFIX + (store.save() ? "ブラックリストを保存しました (グラデーション " + store.gradientBlacklist().size() + "件 / 陰影 " + store.shadeBlacklist().size() + "件)。" : "保存できませんでした (ログを見てください)。"));
            refresh(p, Page.BLACKLIST);
        });
        int index = 7;
        for (Material material : new ArrayList<>(list)) {
            if (index >= GRID.length) break;
            put(holder, GRID[index++], item(material, name(material), NamedTextColor.WHITE, List.of("クリックで取り除く")), (p, c) -> {
                list.remove(material);
                refresh(p, Page.BLACKLIST);
            });
        }
    }

    // ---- the biome mode's pattern page ---------------------------------------------------------------------------------------------

    /** A leather tunic in the biome's colour (of the kind the list follows), named after the biome. */
    private static ItemStack biomeItem(org.bukkit.block.Biome biome, BiomeColors.Kind kind, String prefix, NamedTextColor color, List<String> lore) {
        int rgb = BiomeColors.rgb(biome, kind);
        List<String> lines = new ArrayList<>();
        lines.add(BiomeColors.name(biome) + " / " + BiomeColors.sort(biome));
        lines.add(String.format(Locale.ROOT, "%s: #%06X", kind.label, rgb));
        lines.addAll(lore);
        ItemStack item = item(Material.LEATHER_CHESTPLATE, prefix + BiomeColors.label(biome), color, lines);
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta meta) {
            meta.setColor(org.bukkit.Color.fromRGB(rgb));
            meta.addItemFlags(ItemFlag.HIDE_DYE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void addBiome(BrushSettings s, org.bukkit.block.Biome biome) {
        if (!BiomeColors.known(biome)) return;
        for (BrushSettings.BiomeEntry entry : s.biomes) {
            if (entry.biome == biome) {
                entry.weight = Math.min(99, entry.weight + 1);
                return;
            }
        }
        if (s.biomes.size() < 13) s.biomes.add(new BrushSettings.BiomeEntry(biome, 1));
    }

    /**
     * The pattern page in the biome mode: the biomes to paint (as the blocks of the other modes: a list with weights, scattered or along a gradient), the tint they are ordered by,
     * and a list made from two biomes, the ones between them chosen by colour. The list of all biomes opens to add one or to choose an end.
     */
    private void biomes(Holder holder, BrushSettings s) {
        if (holder.biomePick != BiomePick.NONE) {
            biomeList(holder, s);
            return;
        }
        boolean gradient = s.patternMode == BrushSettings.PatternMode.GRADIENT;
        put(holder, GRID[0], item(gradient ? Material.PRISMARINE_SHARD : Material.WHITE_CONCRETE, "置き方: " + (gradient ? "グラデーション" : "ばらまき (割合)"), NamedTextColor.GOLD, List.of(
                "グラデーション: 一覧の順に、割合の分だけ長く、バイオームを並べる",
                "ばらまき: 割合に応じて、ばらばらに置く", "クリックで切り替え")), (p, c) -> {
            s.patternMode = gradient ? BrushSettings.PatternMode.RANDOM : BrushSettings.PatternMode.GRADIENT;
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[1], item(s.biomeKind == BiomeColors.Kind.WATER ? Material.WATER_BUCKET : s.biomeKind == BiomeColors.Kind.FOLIAGE ? Material.OAK_LEAVES : Material.GRASS_BLOCK,
                "色の基準: " + s.biomeKind.label, NamedTextColor.GOLD, List.of(
                        s.biomeKind.description,
                        "バイオームの色の表示と、「2つのバイオームから一覧を作る」で、どの色をつなぐか",
                        "クリックで 草 → 葉 → 水")), (p, c) -> {
            BiomeColors.Kind[] all = BiomeColors.Kind.values();
            s.biomeKind = all[Math.floorMod(s.biomeKind.ordinal() + (left(c) ? 1 : -1), all.length)];
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[2], item(Material.EMERALD, "バイオームを選んで足す", NamedTextColor.GREEN, List.of("すべてのバイオームの一覧 (色の順) を開いて、クリックしたものを一覧の最後に足す")), (p, c) -> {
            holder.biomePick = BiomePick.ADD;
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[3], item(Material.COMPASS, "見ている所のバイオームを足す", NamedTextColor.GREEN, List.of("ブラシで見ているブロック (無ければ自分の足元) のバイオーム")), (p, c) -> {
            org.bukkit.block.Block target = module.target(p);
            org.bukkit.block.Biome biome = target != null ? target.getBiome() : p.getLocation().getBlock().getBiome();
            if (!BiomeColors.known(biome)) p.sendMessage(BrushModule.PREFIX + "このバイオーム (" + biome + ") は塗れません (ネザー・エンド・カスタム)。");
            else addBiome(s, biome);
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[4], item(Material.WATER_BUCKET, "割合をそろえる", NamedTextColor.AQUA, List.of("全部のバイオームの割合を 1 にする")), (p, c) -> {
            for (BrushSettings.BiomeEntry entry : s.biomes) entry.weight = 1;
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[5], item(Material.LAVA_BUCKET, "全部消す", NamedTextColor.RED, List.of("バイオームの一覧を空にする")), (p, c) -> {
            s.biomes.clear();
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[6], item(Material.BOOK, "バイオームのブラシについて", NamedTextColor.AQUA, List.of(
                "ゲームはバイオームを 4x4x4 ブロックのかたまりで持つので、そのかたまりごとに塗る",
                "草・葉・水の色は、ゲームが数ブロックかけてなじませる (なめらかなグラデーションになる)",
                "「表面だけ」がオンなら、地面のあるかたまりだけを塗る",
                "雪のバイオームでは雪が降り、水が凍る。プレビューは無い。//undo で戻せる")), null);
        int total = 0;
        for (BrushSettings.BiomeEntry entry : s.biomes) total += Math.max(1, entry.weight);
        for (int index = 0; index < Math.min(13, s.biomes.size()); index++) {
            BrushSettings.BiomeEntry entry = s.biomes.get(index);
            int at = index;
            put(holder, GRID[7 + index], biomeItem(entry.biome, s.biomeKind, (index + 1) + ". ", NamedTextColor.WHITE, List.of(
                    "割合: " + entry.weight + " (" + Math.round(100.0 * Math.max(1, entry.weight) / Math.max(1, total)) + "%)",
                    "左 +1 / 右 -1 / Shift+左 1つ前へ / Shift+右 取り除く")), (p, c) -> {
                if (c == ClickType.SHIFT_RIGHT) s.biomes.remove(entry);
                else if (c == ClickType.SHIFT_LEFT) {
                    if (at > 0) {
                        s.biomes.remove(at);
                        s.biomes.add(at - 1, entry);
                    }
                } else entry.weight = Math.max(1, Math.min(99, entry.weight + (left(c) ? 1 : -1)));
                refresh(p, Page.PATTERN);
            });
        }
        // the two ends the list can be made from
        put(holder, GRID[21], biomeItem(s.biomeFrom, s.biomeKind, "作る元: 最初 ", NamedTextColor.GREEN, List.of("クリックで、一覧から選ぶ")), (p, c) -> {
            holder.biomePick = BiomePick.FROM;
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[22], biomeItem(s.biomeTo, s.biomeKind, "作る元: 最後 ", NamedTextColor.GREEN, List.of("クリックで、一覧から選ぶ")), (p, c) -> {
            holder.biomePick = BiomePick.TO;
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[23], item(Material.LADDER, "間のバイオームの数: " + s.biomeSteps, NamedTextColor.AQUA, List.of("2つのバイオームの間に入れる、近い色のバイオームの数 (0〜14)", "同じ色のバイオームは1つしか使わないので、少なくなることがある", "左クリック +1 / 右クリック -1")), (p, c) -> {
            s.biomeSteps = Math.max(0, Math.min(14, s.biomeSteps + (left(c) ? 1 : -1)));
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[24], item(Material.CRAFTING_TABLE, "2つのバイオームから一覧を作る", NamedTextColor.GREEN, List.of(
                "最初と最後のバイオームの間を、" + s.biomeKind.label + "が近いバイオームでつないで、一覧に入れる (グラデーションの置き方になる)",
                "間には、両端と同じ種類 (陸・水辺・雪・洞窟) のバイオームだけを使う",
                "できたあとは、順・割合を自由に直せる")), (p, c) -> {
            s.biomes.clear();
            for (org.bukkit.block.Biome biome : BiomeColors.ramp(s.biomeFrom, s.biomeTo, s.biomeSteps, s.biomeKind)) s.biomes.add(new BrushSettings.BiomeEntry(biome, 1));
            s.patternMode = BrushSettings.PatternMode.GRADIENT;
            refresh(p, Page.PATTERN);
        });
        if (gradient) {
            put(holder, GRID[20], item(Material.REPEATER, "向きを逆にする: " + onOff(s.gradientReverse), NamedTextColor.AQUA, List.of("オン: 最初と最後の位置が入れ替わる", "クリックで切り替え")), (p, c) -> {
                s.gradientReverse = !s.gradientReverse;
                refresh(p, Page.PATTERN);
            });
            put(holder, GRID[25], item(Material.COMPASS, "向き: " + s.gradientAxis.label, NamedTextColor.AQUA, List.of(s.gradientAxis.description, "(ブロックの模様と共通の設定)", "クリックで切り替え")), (p, c) -> {
                BrushSettings.GradientAxis[] all = BrushSettings.GradientAxis.values();
                s.gradientAxis = all[Math.floorMod(s.gradientAxis.ordinal() + (left(c) ? 1 : -1), all.length)];
                refresh(p, Page.PATTERN);
            });
            put(holder, GRID[26], item(Material.SUGAR, "ざらつき: " + Math.round(s.dither * 100) + "%", NamedTextColor.AQUA, List.of("0%: きれいな帯 / 大きいほど、境目がまだらに混ざる", "左クリック +10% / 右クリック -10%")), (p, c) -> {
                s.dither = Math.max(0.0, Math.min(1.0, s.dither + (left(c) ? 0.1 : -0.1)));
                refresh(p, Page.PATTERN);
            });
        }
    }

    /** The list of all biomes the brush can paint, in order of colour (of the kind chosen), 28 to a page: a click adds the biome or makes it an end. */
    private void biomeList(Holder holder, BrushSettings s) {
        List<org.bukkit.block.Biome> all = BiomeColors.all(s.biomeKind);
        int pages = Math.max(1, (all.size() + GRID.length - 1) / GRID.length);
        holder.biomeListPage = Math.max(0, Math.min(pages - 1, holder.biomeListPage));
        String why = switch (holder.biomePick) {
            case FROM -> "最初のバイオームにする";
            case TO -> "最後のバイオームにする";
            default -> "一覧の最後に足す";
        };
        for (int index = 0; index < GRID.length; index++) {
            int at = holder.biomeListPage * GRID.length + index;
            if (at >= all.size()) break;
            org.bukkit.block.Biome biome = all.get(at);
            put(holder, GRID[index], biomeItem(biome, s.biomeKind, "", NamedTextColor.WHITE, List.of("クリックで" + why)), (p, c) -> {
                switch (holder.biomePick) {
                    case FROM -> s.biomeFrom = biome;
                    case TO -> s.biomeTo = biome;
                    default -> addBiome(s, biome);
                }
                holder.biomePick = BiomePick.NONE;
                refresh(p, Page.PATTERN);
            });
        }
        put(holder, 46, item(Material.ARROW, "前のページ", NamedTextColor.YELLOW, List.of((holder.biomeListPage + 1) + " / " + pages)), (p, c) -> {
            holder.biomeListPage = Math.floorMod(holder.biomeListPage - 1, pages);
            refresh(p, Page.PATTERN);
        });
        put(holder, 53, item(Material.ARROW, "次のページ", NamedTextColor.YELLOW, List.of((holder.biomeListPage + 1) + " / " + pages)), (p, c) -> {
            holder.biomeListPage = Math.floorMod(holder.biomeListPage + 1, pages);
            refresh(p, Page.PATTERN);
        });
        put(holder, 49, item(Material.BARRIER, "選ばずに戻る", NamedTextColor.RED, List.of()), (p, c) -> {
            holder.biomePick = BiomePick.NONE;
            refresh(p, Page.PATTERN);
        });
    }

    // ---- events -----------------------------------------------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == holder.inventory) {
            BiConsumer<Player, ClickType> action = holder.actions.get(event.getSlot());
            if (action != null) {
                action.accept(player, event.getClick());
                module.changed(player);
            }
            return;
        }
        // a block of the player's own inventory: added to the pattern, the block mask or the blacklist, or made an end of the gradient
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        BrushSettings s = module.settings(player);
        Material material = clicked.getType();
        if (!usable(material)) return;
        switch (holder.page) {
            case PATTERN -> {
                // (the biome mode's list holds biomes, not blocks)
                if (s.mode == BrushMode.BIOME) return;
                if (holder.pick == Pick.FROM) s.gradientFrom = material;
                else if (holder.pick == Pick.TO) s.gradientTo = material;
                else addPattern(s, material);
                holder.pick = Pick.NONE;
                refresh(player, Page.PATTERN);
            }
            case POSITION -> {
                s.relationBlocks.add(material);
                refresh(player, Page.POSITION);
            }
            case MASK -> {
                s.blockMask.add(material);
                refresh(player, Page.MASK);
            }
            case BLACKLIST -> {
                (holder.shadeList ? module.store().shadeBlacklist() : module.store().gradientBlacklist()).add(material);
                refresh(player, Page.BLACKLIST);
            }
            default -> {
                return;
            }
        }
        module.changed(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
