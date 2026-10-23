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
import java.util.function.BiConsumer;

/**
 * The menu of the brush, laid out like the tree tool's: tabs along the top for the pages (basics, pattern, mask, sun and shade, blacklist), the settings of a page in the
 * grid under them, and a bottom row that is the same on every page (close, preview, the brush, undo, redo). Every change is passed to the module, which bumps the revision the
 * live preview follows. A block of the player's own inventory clicked while the pattern, the block mask or the blacklist is open is added to it (or, after "from" / "to" of the
 * gradient was clicked, becomes that end).
 */
final class BrushMenu implements Listener {

    enum Page {
        MAIN(2, Material.PAINTING, "基本", "ブラシ"),
        PATTERN(3, Material.WHITE_CONCRETE, "模様", "模様"),
        MASK(4, Material.HOPPER, "マスク", "マスク"),
        SUN(5, Material.SUNFLOWER, "陰影", "太陽と陰影"),
        BLACKLIST(6, Material.BARRIER, "ブラックリスト", "ブラックリスト"),
        SELECT(7, Material.BLAZE_ROD, "選択", "選択ツール");

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

    private static final class Holder implements MenuHolder {
        private Inventory inventory;
        private Page page;
        /** Which end of the gradient the next block clicked in the inventory becomes. */
        private Pick pick = Pick.NONE;
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
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) inventory.setItem(slot, frame);
        }
        for (Page page : Page.values()) {
            boolean here = holder.page == page;
            put(holder, page.tab, item(page.icon, (here ? "▶ " : "") + page.label, here ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(here ? "いま開いているページ" : "クリックで開く")),
                    (p, c) -> {
                        holder.pick = Pick.NONE;
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
        BrushShape[] shapes = BrushShape.values();
        for (int index = 0; index < shapes.length; index++) {
            BrushShape shape = shapes[index];
            boolean on = s.shape == shape;
            put(holder, GRID[7 + index], item(shape.icon(), (on ? "▶ " : "") + shape.label(), on ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(shape.description(), on ? "選んでいます" : "クリックで形を選ぶ")),
                    (p, c) -> {
                        s.shape = shape;
                        refresh(p, Page.MAIN);
                    });
        }
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
        put(holder, GRID[21], item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
                "ブラシを持って 右クリック = 見ている所に使う (押しつづけると続けて使う)",
                "Shift + 右クリック = 1回だけプレビュー / 左クリック = この画面",
                "ぼかし・強さ・形は、塗るにも自動陰影にも効く",
                "コマンドは /ibrush help")), null);
    }

    // ---- pattern ---------------------------------------------------------------------------------------------------------------

    private void pattern(Holder holder, BrushSettings s) {
        boolean gradient = s.patternMode == BrushSettings.PatternMode.GRADIENT;
        put(holder, GRID[0], item(gradient ? Material.PRISMARINE_SHARD : Material.WHITE_CONCRETE, "模様の作り方: " + (gradient ? "グラデーション" : "ブロックの割合"), NamedTextColor.GOLD, List.of(
                "ブロックの割合: 選んだブロックを割合でばらまく",
                "グラデーション: 2つのブロックの間を、近い色のブロックでつなぐ",
                "クリックで切り替え")), (p, c) -> {
            s.patternMode = gradient ? BrushSettings.PatternMode.RANDOM : BrushSettings.PatternMode.GRADIENT;
            refresh(p, Page.PATTERN);
        });
        if (gradient) gradientControls(holder, s);
        else listControls(holder, s);
    }

    private void listControls(Holder holder, BrushSettings s) {
        put(holder, GRID[1], item(Material.EMERALD, "手に持つブロックを足す", NamedTextColor.GREEN, List.of("メインハンドのブロックを模様に足す", "自分のインベントリのブロックをクリックしても足せる")), (p, c) -> {
            addPattern(s, p.getInventory().getItemInMainHand().getType());
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[2], item(Material.CHEST, "ホットバーのブロックを全部足す", NamedTextColor.GREEN, List.of("ホットバーの9スロットのブロックを足す")), (p, c) -> {
            for (int slot = 0; slot < 9; slot++) addPattern(s, p.getInventory().getItem(slot) == null ? Material.AIR : p.getInventory().getItem(slot).getType());
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[3], item(Material.WATER_BUCKET, "割合をそろえる", NamedTextColor.AQUA, List.of("全部のブロックの重みを 1 にする")), (p, c) -> {
            for (BrushSettings.Entry entry : s.pattern) entry.weight = 1;
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[4], item(Material.LAVA_BUCKET, "全部消す", NamedTextColor.RED, List.of("模様を空にする")), (p, c) -> {
            s.pattern.clear();
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[5], item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
                "ブロックのアイコン: 左クリック 割合 +1 / 右クリック -1",
                "Shift + 右クリックで取り除く (Shift + 左で +5)",
                "割合が大きいブロックほどたくさん使われる")), null);
        for (int index = 0; index < Math.min(GRID.length - 7, s.pattern.size()); index++) {
            BrushSettings.Entry entry = s.pattern.get(index);
            int total = Math.max(1, s.totalWeight());
            put(holder, GRID[7 + index], item(entry.material, name(entry.material), NamedTextColor.WHITE, List.of(
                    "割合: " + entry.weight + " (" + Math.round(100.0 * entry.weight / total) + "%)", "左 +1 / 右 -1 / Shift+左 +5 / Shift+右 取り除く")), (p, c) -> {
                if (c == ClickType.SHIFT_RIGHT) s.pattern.remove(entry);
                else entry.weight = Math.max(1, Math.min(99, entry.weight + (left(c) ? 1 : -1) * (big(c) ? 5 : 1)));
                refresh(p, Page.PATTERN);
            });
        }
    }

    private void gradientControls(Holder holder, BrushSettings s) {
        put(holder, GRID[1], item(s.gradientFrom, "最初のブロック: " + name(s.gradientFrom), holder.pick == Pick.FROM ? NamedTextColor.GOLD : NamedTextColor.GREEN, List.of(
                holder.pick == Pick.FROM ? "→ 自分のインベントリのブロックをクリックしてください" : "左クリック: 次にクリックするブロックを最初のブロックにする",
                "右クリック: 手に持つブロックを最初のブロックにする")), (p, c) -> setEnd(p, holder, s, Pick.FROM, left(c)));
        put(holder, GRID[2], item(s.gradientTo, "最後のブロック: " + name(s.gradientTo), holder.pick == Pick.TO ? NamedTextColor.GOLD : NamedTextColor.GREEN, List.of(
                holder.pick == Pick.TO ? "→ 自分のインベントリのブロックをクリックしてください" : "左クリック: 次にクリックするブロックを最後のブロックにする",
                "右クリック: 手に持つブロックを最後のブロックにする")), (p, c) -> setEnd(p, holder, s, Pick.TO, left(c)));
        put(holder, GRID[3], item(Material.LADDER, "間のブロックの数: " + s.gradientSteps, NamedTextColor.AQUA, List.of("2つのブロックの間に入れる、近い色のブロックの数 (0〜14)", "左クリック +1 / 右クリック -1")), (p, c) -> {
            s.gradientSteps = Math.max(0, Math.min(14, s.gradientSteps + (left(c) ? 1 : -1)));
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[4], item(Material.COMPASS, "向き: " + s.gradientAxis.label, NamedTextColor.AQUA, List.of(s.gradientAxis.description, "クリックで切り替え")), (p, c) -> {
            BrushSettings.GradientAxis[] all = BrushSettings.GradientAxis.values();
            s.gradientAxis = all[(s.gradientAxis.ordinal() + 1) % all.length];
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[5], item(Material.SUGAR, "ざらつき: " + Math.round(s.dither * 100) + "%", NamedTextColor.AQUA, List.of("0%: きれいな帯 / 大きいほど、境目がまだらに混ざる", "左クリック +10% / 右クリック -10%")), (p, c) -> {
            s.dither = Math.max(0.0, Math.min(1.0, s.dither + (left(c) ? 0.1 : -0.1)));
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[6], item(Material.GLOWSTONE_DUST, "加算: " + onOff(s.additive), NamedTextColor.AQUA, List.of(
                "オン: 同じ所にもう一度塗ると、いまの色がグラデーションの色の方へ少しずつ進む (上乗せ)",
                "オフ: 塗るたびに、グラデーションのブロックで置き換える", "クリックで切り替え")), (p, c) -> {
            s.additive = !s.additive;
            refresh(p, Page.PATTERN);
        });
        put(holder, GRID[7], item(Material.REDSTONE, "加算の進み方: " + Math.round(s.additiveRate * 100) + "%", NamedTextColor.AQUA, List.of("1回塗るごとに、色をどれだけ進めるか", "左クリック +10% / 右クリック -10%")), (p, c) -> {
            s.additiveRate = Math.max(0.1, Math.min(1.0, s.additiveRate + (left(c) ? 0.1 : -0.1)));
            refresh(p, Page.PATTERN);
        });
        List<String> rampLore = new ArrayList<>();
        for (Material block : Gradients.ramp(s.gradientFrom, s.gradientTo, s.gradientSteps, module.store().blacklist())) rampLore.add("・" + name(block));
        put(holder, GRID[8], item(Material.PAINTING, "いまのグラデーション", NamedTextColor.WHITE, rampLore), null);
        put(holder, GRID[9], item(Material.WRITABLE_BOOK, "プリセットに保存", NamedTextColor.GREEN, List.of("いまの2つのブロックと数を、自分のプリセット (brush.yml) に保存する", "名前は my-1, my-2 ... (/ibrush preset save <名前> でも保存できる)")), (p, c) -> {
            int number = module.store().presets().size() + 1;
            module.store().addPreset("my-" + number, s.gradientFrom, s.gradientTo, s.gradientSteps);
            p.sendMessage(BrushModule.PREFIX + (module.store().save() ? "プリセット my-" + number + " を保存しました。" : "保存できませんでした (ログを見てください)。"));
            refresh(p, Page.PATTERN);
        });
        List<Gradients.Preset> presets = module.store().allPresets();
        for (int index = 0; index < Math.min(GRID.length - 14, presets.size()); index++) {
            Gradients.Preset preset = presets.get(index);
            put(holder, GRID[14 + index], item(preset.from(), preset.name(), NamedTextColor.WHITE, List.of(name(preset.from()) + " → " + name(preset.to()) + " (間 " + preset.steps() + ")", "クリックで使う")), (p, c) -> {
                s.patternMode = BrushSettings.PatternMode.GRADIENT;
                s.gradientFrom = preset.from();
                s.gradientTo = preset.to();
                s.gradientSteps = preset.steps();
                refresh(p, Page.PATTERN);
            });
        }
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
        else if (s.pattern.size() < GRID.length - 7) s.pattern.add(new BrushSettings.Entry(material, 1));
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
        put(holder, GRID[17], item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
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
        put(holder, GRID[9], item(Material.BLAZE_ROD, "選択ツールをもらう", NamedTextColor.GOLD, List.of("持っていなければ、選択ツールのアイテムを渡す")), (p, c) -> tool.give(p));
        put(holder, GRID[10], item(Material.BOOK, "いまの選択", NamedTextColor.GOLD, List.of(
                state.selection == null ? "選んでいません" : state.selection.blocks.size() + "ブロック" + (inverted ? " (反転中)" : ""),
                state.job != null ? "探している途中です…" : "")), null);
    }

    // ---- blacklist ------------------------------------------------------------------------------------------------------------------

    private void blacklist(Holder holder) {
        BrushStore store = module.store();
        put(holder, GRID[0], item(Material.BOOK, "ブラックリスト (" + store.blacklist().size() + ")", NamedTextColor.GOLD, List.of(
                "ここにあるブロックは、グラデーションの途中にも、自動陰影の暗いブロックにも使われない",
                "全員で共通 (brush.yml)。「保存」を押すまでファイルには書かれない",
                "ブロックのアイコンをクリックで取り除く")), null);
        put(holder, GRID[1], item(Material.EMERALD, "手に持つブロックを足す", NamedTextColor.GREEN, List.of("自分のインベントリのブロックをクリックしても足せる")), (p, c) -> {
            Material held = p.getInventory().getItemInMainHand().getType();
            if (usable(held)) store.blacklist().add(held);
            refresh(p, Page.BLACKLIST);
        });
        put(holder, GRID[2], item(Material.LAVA_BUCKET, "全部消す", NamedTextColor.RED, List.of("リストを空にする (保存するまでファイルはそのまま)")), (p, c) -> {
            store.blacklist().clear();
            refresh(p, Page.BLACKLIST);
        });
        put(holder, GRID[3], item(Material.WRITABLE_BOOK, "保存", NamedTextColor.GREEN, List.of("いまのリストを plugins/iruuRPG/brush.yml に書く", "クリックで保存")), (p, c) -> {
            p.sendMessage(BrushModule.PREFIX + (store.save() ? "ブラックリストを保存しました (" + store.blacklist().size() + "件)。" : "保存できませんでした (ログを見てください)。"));
            refresh(p, Page.BLACKLIST);
        });
        int index = 7;
        for (Material material : new ArrayList<>(store.blacklist())) {
            if (index >= GRID.length) break;
            put(holder, GRID[index++], item(material, name(material), NamedTextColor.WHITE, List.of("クリックで取り除く")), (p, c) -> {
                store.blacklist().remove(material);
                refresh(p, Page.BLACKLIST);
            });
        }
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
                if (s.patternMode == BrushSettings.PatternMode.GRADIENT) {
                    if (holder.pick == Pick.FROM) s.gradientFrom = material;
                    else if (holder.pick == Pick.TO) s.gradientTo = material;
                    holder.pick = Pick.NONE;
                } else {
                    addPattern(s, material);
                }
                refresh(player, Page.PATTERN);
            }
            case MASK -> {
                s.blockMask.add(material);
                refresh(player, Page.MASK);
            }
            case BLACKLIST -> {
                module.store().blacklist().add(material);
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
