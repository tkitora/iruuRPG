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
 * The menu of the brush, in pages like goPaint's: the main page (mode, shape, size, strength, falloff, noise), the pattern (the blocks painted, with weights), the
 * masks (blocks, angle, shade, each can be inverted), and the sun (the direction the shade is worked out from). A block of the player's own inventory clicked while the
 * pattern or the block mask is open is added to it.
 */
final class BrushMenu implements Listener {

    enum Page { MAIN, PATTERN, MASK, SUN }

    private static final int SIZE = 54;
    private static final int TAB_MAIN = 45;
    private static final int TAB_PATTERN = 46;
    private static final int TAB_MASK = 47;
    private static final int TAB_SUN = 48;
    private static final int UNDO = 51;
    private static final int GIVE = 52;
    private static final int CLOSE = 53;

    private final BrushModule module;

    BrushMenu(BrushModule module) {
        this.module = module;
    }

    private static final class Holder implements MenuHolder {
        private Inventory inventory;
        private Page page;
        private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    void open(Player player, Page page) {
        Holder holder = new Holder();
        holder.page = page;
        holder.inventory = Bukkit.createInventory(holder, SIZE, Component.text("ペイントブラシ - " + switch (page) {
            case MAIN -> "メイン";
            case PATTERN -> "模様 (パターン)";
            case MASK -> "マスク";
            case SUN -> "太陽と陰影";
        }));
        render(player, holder);
        player.openInventory(holder.inventory);
    }

    // ---- building the items ------------------------------------------------------------------------------------------------------

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material.isItem() ? material : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(text(name, color));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) lines.add(text(line, NamedTextColor.GRAY));
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_POTION_EFFECTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String onOff(boolean value) {
        return value ? "オン" : "オフ";
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

    // ---- pages -----------------------------------------------------------------------------------------------------------------

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.actions.clear();
        BrushSettings s = module.settings(player);
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 45; slot < SIZE; slot++) inventory.setItem(slot, frame);

        tab(holder, TAB_MAIN, Page.MAIN, Material.PAINTING, "メイン", "モード・形・大きさ・強さ");
        tab(holder, TAB_PATTERN, Page.PATTERN, Material.WHITE_CONCRETE, "模様 (パターン)", "塗るブロックと、その割合");
        tab(holder, TAB_MASK, Page.MASK, Material.HOPPER, "マスク", "ブロック・角度・陰影で、効く場所を絞る");
        tab(holder, TAB_SUN, Page.SUN, Material.SUNFLOWER, "太陽と陰影", "自動陰影の太陽の向き");
        put(holder, UNDO, item(Material.BARRIER, "元に戻す (//undo)", NamedTextColor.RED, List.of("直前に使ったブラシを取り消す", module.hasWorldEdit() ? "" : "WorldEdit がないので使えません")),
                (p, c) -> {
                    p.closeInventory();
                    module.undo(p);
                });
        put(holder, GIVE, item(Material.BRUSH, "ブラシをもらう", NamedTextColor.AQUA, List.of("持っていなければブラシのアイテムを渡す")), (p, c) -> module.give(p));
        put(holder, CLOSE, item(Material.ARROW, "閉じる", NamedTextColor.YELLOW, List.of()), (p, c) -> p.closeInventory());

        switch (holder.page) {
            case MAIN -> main(holder, s);
            case PATTERN -> pattern(holder, s);
            case MASK -> mask(holder, s);
            case SUN -> sun(holder, s, player);
        }
    }

    private void tab(Holder holder, int slot, Page page, Material icon, String name, String description) {
        boolean here = holder.page == page;
        ItemStack tab = item(icon, (here ? "▶ " : "") + name, here ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(description));
        put(holder, slot, tab, (p, c) -> refresh(p, page));
    }

    private void main(Holder holder, BrushSettings s) {
        BrushMode[] modes = BrushMode.values();
        for (int index = 0; index < modes.length; index++) {
            BrushMode mode = modes[index];
            boolean on = s.mode == mode;
            put(holder, index, item(mode.icon(), (on ? "▶ " : "") + mode.label(), on ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(mode.description(), on ? "選んでいます" : "クリックで選ぶ")),
                    (p, c) -> {
                        s.mode = mode;
                        refresh(p, Page.MAIN);
                    });
        }
        BrushShape[] shapes = BrushShape.values();
        for (int index = 0; index < shapes.length; index++) {
            BrushShape shape = shapes[index];
            boolean on = s.shape == shape;
            put(holder, 9 + index, item(shape.icon(), (on ? "▶ " : "") + shape.label(), on ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(on ? "選んでいます" : "クリックで形を選ぶ")),
                    (p, c) -> {
                        s.shape = shape;
                        refresh(p, Page.MAIN);
                    });
        }
        put(holder, 18, item(Material.SPYGLASS, "大きさ (半径): " + s.radius, NamedTextColor.AQUA, List.of("左クリック +1 / 右クリック -1", "Shift で 5 ずつ", "1〜" + module.maxRadius())),
                (p, c) -> {
                    s.radius = Math.max(1, Math.min(module.maxRadius(), s.radius + (left(c) ? 1 : -1) * (big(c) ? 5 : 1)));
                    refresh(p, Page.MAIN);
                });
        if (s.mode.terraform()) {
            put(holder, 19, item(Material.PISTON, "高さ: " + s.height + " ブロック", NamedTextColor.AQUA, List.of("盛る・削る・平らに で動かす高さ (中心で)", "左クリック +1 / 右クリック -1")),
                    (p, c) -> {
                        s.height = Math.max(1, Math.min(12, s.height + (left(c) ? 1 : -1)));
                        refresh(p, Page.MAIN);
                    });
            if (s.mode == BrushMode.SMOOTH) {
                put(holder, 20, item(Material.SNOWBALL, "なだらかさ: " + Math.round(s.strength * 100) + "%", NamedTextColor.AQUA, List.of("ならす強さ", "左クリック +10% / 右クリック -10%")),
                        (p, c) -> {
                            s.strength = Math.max(0.1, Math.min(1.0, s.strength + (left(c) ? 0.1 : -0.1)));
                            refresh(p, Page.MAIN);
                        });
            }
        } else {
            put(holder, 19, item(Material.BLAZE_POWDER, "強さ: " + Math.round(s.strength * 100) + "%", NamedTextColor.AQUA, List.of("範囲の中で、どれくらいのブロックを変えるか", "左クリック +10% / 右クリック -10%", "Shift で 25% ずつ")),
                    (p, c) -> {
                        s.strength = Math.max(0.1, Math.min(1.0, s.strength + (left(c) ? 1 : -1) * (big(c) ? 0.25 : 0.1)));
                        refresh(p, Page.MAIN);
                    });
        }
        put(holder, 21, item(Material.COBWEB, "ぼかし: " + Math.round(s.falloff * 100) + "%", NamedTextColor.AQUA, List.of("縁に行くほど効きを弱くする度合い", "左クリック +10% / 右クリック -10%")),
                (p, c) -> {
                    s.falloff = Math.max(0.0, Math.min(1.0, s.falloff + (left(c) ? 0.1 : -0.1)));
                    refresh(p, Page.MAIN);
                });
        if (s.mode == BrushMode.PAINT) {
            put(holder, 22, item(Material.GRASS_BLOCK, "表面だけ: " + onOff(s.surfaceOnly), NamedTextColor.AQUA, List.of("オン: 空気に触れているブロックだけ塗る", "オフ: 範囲の中のブロックを全部塗る", "クリックで切り替え")),
                    (p, c) -> {
                        s.surfaceOnly = !s.surfaceOnly;
                        refresh(p, Page.MAIN);
                    });
            put(holder, 23, item(Material.AMETHYST_SHARD, "ノイズ: " + onOff(s.noise), NamedTextColor.AQUA, List.of("オン: 模様の選び方を滑らかなノイズにして、点々ではなく斑(まだら)の塊にする", "オフ: 一つずつランダム", "クリックで切り替え")),
                    (p, c) -> {
                        s.noise = !s.noise;
                        refresh(p, Page.MAIN);
                    });
            put(holder, 24, item(Material.COMPARATOR, "ノイズの大きさ: " + s.noiseScale, NamedTextColor.AQUA, List.of("斑の塊の大きさ (大きいほど大きな塊)", "左クリック +0.5 / 右クリック -0.5")),
                    (p, c) -> {
                        s.noiseScale = Math.max(0.5, Math.min(12.0, s.noiseScale + (left(c) ? 0.5 : -0.5)));
                        refresh(p, Page.MAIN);
                    });
        }
        put(holder, 27, item(Material.WHITE_CONCRETE, "模様: " + patternSummary(s), NamedTextColor.WHITE, List.of("クリックで模様のページへ")), (p, c) -> refresh(p, Page.PATTERN));
        put(holder, 28, item(Material.HOPPER, "マスク: " + maskSummary(s), NamedTextColor.WHITE, List.of("クリックでマスクのページへ")), (p, c) -> refresh(p, Page.MASK));
        put(holder, 29, item(Material.SUNFLOWER, "太陽: 方位 " + s.sunAzimuth + "° 高さ " + s.sunElevation + "°", NamedTextColor.WHITE, List.of("クリックで太陽のページへ")), (p, c) -> refresh(p, Page.SUN));
        inventoryHint(holder);
    }

    private void inventoryHint(Holder holder) {
        put(holder, 35, item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
                "ブラシを持って 右クリック = 見ている所に使う",
                "Shift + 右クリック = プレビュー (あなたにだけ見える)",
                "左クリック = この画面を開く",
                "使ったあとは //undo (または「元に戻す」)")), null);
    }

    private String patternSummary(BrushSettings s) {
        if (s.pattern.isEmpty()) return "なし";
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < Math.min(3, s.pattern.size()); index++) {
            if (index > 0) text.append(", ");
            text.append(s.pattern.get(index).material.name().toLowerCase(Locale.ROOT)).append(" x").append(s.pattern.get(index).weight);
        }
        if (s.pattern.size() > 3) text.append(" ほか").append(s.pattern.size() - 3);
        return text.toString();
    }

    private String maskSummary(BrushSettings s) {
        List<String> parts = new ArrayList<>();
        if (s.blockMaskOn) parts.add((s.blockMaskInvert ? "除外" : "") + "ブロック" + s.blockMask.size());
        if (s.angleMaskOn) parts.add((s.angleMaskInvert ? "除外" : "") + "角度" + s.angleMin + "〜" + s.angleMax);
        if (s.shadeMaskOn) parts.add(s.shadeMaskInvert ? "日なた" : "影");
        return parts.isEmpty() ? "なし" : String.join(" / ", parts);
    }

    private void pattern(Holder holder, BrushSettings s) {
        put(holder, 36, item(Material.EMERALD, "手に持つブロックを足す", NamedTextColor.GREEN, List.of("メインハンドのブロックを模様に足す", "自分のインベントリのブロックをクリックしても足せる")), (p, c) -> {
            addPattern(p, s, p.getInventory().getItemInMainHand().getType());
            refresh(p, Page.PATTERN);
        });
        put(holder, 37, item(Material.CHEST, "ホットバーのブロックを全部足す", NamedTextColor.GREEN, List.of("ホットバーの9スロットのブロックを足す")), (p, c) -> {
            for (int slot = 0; slot < 9; slot++) addPattern(p, s, p.getInventory().getItem(slot) == null ? Material.AIR : p.getInventory().getItem(slot).getType());
            refresh(p, Page.PATTERN);
        });
        put(holder, 38, item(Material.WATER_BUCKET, "割合をそろえる", NamedTextColor.AQUA, List.of("全部のブロックの重みを 1 にする")), (p, c) -> {
            for (BrushSettings.Entry entry : s.pattern) entry.weight = 1;
            refresh(p, Page.PATTERN);
        });
        put(holder, 39, item(Material.LAVA_BUCKET, "全部消す", NamedTextColor.RED, List.of("模様を空にする")), (p, c) -> {
            s.pattern.clear();
            refresh(p, Page.PATTERN);
        });
        put(holder, 44, item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
                "ブロックのアイコン: 左クリック 割合 +1 / 右クリック -1",
                "Shift + 右クリックで取り除く (Shift + 左で +5)",
                "割合が大きいブロックほどたくさん使われる",
                "ノイズをオンにすると斑の塊になる")), null);
        for (int index = 0; index < Math.min(36, s.pattern.size()); index++) {
            BrushSettings.Entry entry = s.pattern.get(index);
            int total = Math.max(1, s.totalWeight());
            put(holder, index, item(entry.material, entry.material.name().toLowerCase(Locale.ROOT), NamedTextColor.WHITE, List.of(
                    "割合: " + entry.weight + " (" + Math.round(100.0 * entry.weight / total) + "%)", "左 +1 / 右 -1 / Shift+左 +5 / Shift+右 取り除く")), (p, c) -> {
                if (c == ClickType.SHIFT_RIGHT) s.pattern.remove(entry);
                else entry.weight = Math.max(1, Math.min(99, entry.weight + (left(c) ? 1 : -1) * (big(c) ? 5 : 1)));
                refresh(p, Page.PATTERN);
            });
        }
    }

    private void addPattern(Player player, BrushSettings s, Material material) {
        if (!material.isBlock() || !material.isItem() || material.isAir()) return;
        BrushSettings.Entry entry = s.find(material);
        if (entry != null) entry.weight = Math.min(99, entry.weight + 1);
        else if (s.pattern.size() < 36) s.pattern.add(new BrushSettings.Entry(material, 1));
    }

    private void mask(Holder holder, BrushSettings s) {
        // block mask: row 0 and the list under it
        put(holder, 0, item(Material.HOPPER, "ブロックのマスク: " + onOff(s.blockMaskOn), NamedTextColor.AQUA, List.of("リストのブロックだけに効かせる", "クリックで切り替え")), (p, c) -> {
            s.blockMaskOn = !s.blockMaskOn;
            refresh(p, Page.MASK);
        });
        put(holder, 1, item(Material.ENDER_EYE, "逆マスク (除外): " + onOff(s.blockMaskInvert), NamedTextColor.AQUA, List.of("オン: リストのブロック「以外」に効かせる", "クリックで切り替え")), (p, c) -> {
            s.blockMaskInvert = !s.blockMaskInvert;
            refresh(p, Page.MASK);
        });
        put(holder, 2, item(Material.EMERALD, "手に持つブロックを足す", NamedTextColor.GREEN, List.of("自分のインベントリのブロックをクリックしても足せる")), (p, c) -> {
            Material held = p.getInventory().getItemInMainHand().getType();
            if (held.isBlock() && held.isItem() && !held.isAir()) s.blockMask.add(held);
            refresh(p, Page.MASK);
        });
        put(holder, 3, item(Material.LAVA_BUCKET, "リストを空にする", NamedTextColor.RED, List.of()), (p, c) -> {
            s.blockMask.clear();
            refresh(p, Page.MASK);
        });
        int slot = 9;
        for (Material material : s.blockMask) {
            if (slot >= 27) break;
            Material shown = material;
            put(holder, slot++, item(shown, shown.name().toLowerCase(Locale.ROOT), NamedTextColor.WHITE, List.of("クリックで取り除く")), (p, c) -> {
                s.blockMask.remove(shown);
                refresh(p, Page.MASK);
            });
        }
        // angle mask
        put(holder, 27, item(Material.LEVER, "角度のマスク: " + onOff(s.angleMaskOn), NamedTextColor.AQUA, List.of("表面の傾き (0°=平ら、90°=垂直な壁) が範囲の中の所だけに効かせる", "クリックで切り替え")), (p, c) -> {
            s.angleMaskOn = !s.angleMaskOn;
            refresh(p, Page.MASK);
        });
        put(holder, 28, item(Material.ENDER_EYE, "逆マスク (除外): " + onOff(s.angleMaskInvert), NamedTextColor.AQUA, List.of("オン: 範囲「以外」の傾きに効かせる")), (p, c) -> {
            s.angleMaskInvert = !s.angleMaskInvert;
            refresh(p, Page.MASK);
        });
        put(holder, 29, item(Material.STICK, "最小の角度: " + s.angleMin + "°", NamedTextColor.AQUA, List.of("左クリック +5° / 右クリック -5°")), (p, c) -> {
            s.angleMin = Math.max(0, Math.min(s.angleMax, s.angleMin + (left(c) ? 5 : -5)));
            refresh(p, Page.MASK);
        });
        put(holder, 30, item(Material.BLAZE_ROD, "最大の角度: " + s.angleMax + "°", NamedTextColor.AQUA, List.of("左クリック +5° / 右クリック -5°")), (p, c) -> {
            s.angleMax = Math.max(s.angleMin, Math.min(180, s.angleMax + (left(c) ? 5 : -5)));
            refresh(p, Page.MASK);
        });
        // shade mask
        put(holder, 36, item(Material.BLACK_CONCRETE, "陰影のマスク: " + onOff(s.shadeMaskOn), NamedTextColor.AQUA, List.of("オン: 影になっている面だけに効かせる (太陽の向きは「太陽と陰影」のページ)", "クリックで切り替え")), (p, c) -> {
            s.shadeMaskOn = !s.shadeMaskOn;
            refresh(p, Page.MASK);
        });
        put(holder, 37, item(Material.ENDER_EYE, "逆マスク (除外): " + onOff(s.shadeMaskInvert), NamedTextColor.AQUA, List.of("オン: 影「以外」(日なた) だけに効かせる")), (p, c) -> {
            s.shadeMaskInvert = !s.shadeMaskInvert;
            refresh(p, Page.MASK);
        });
        put(holder, 44, item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
                "マスクは全部同時に効く (全部を通った所だけ変わる)",
                "「逆マスク」は、その条件を満たさない所だけを通す",
                "模様・自動陰影・盛る削るのどれにも効く")), null);
    }

    private static String compass(int azimuth) {
        String[] names = {"北", "北東", "東", "南東", "南", "南西", "西", "北西"};
        return names[Math.floorMod(Math.round(azimuth / 45.0f), 8)];
    }

    private void sun(Holder holder, BrushSettings s, Player player) {
        put(holder, 10, item(Material.RED_STAINED_GLASS_PANE, "方位 -15°", NamedTextColor.RED, List.of("太陽を左回りに動かす")), (p, c) -> {
            s.sunAzimuth = Math.floorMod(s.sunAzimuth - (big(c) ? 45 : 15), 360);
            refresh(p, Page.SUN);
        });
        put(holder, 11, item(Material.COMPASS, "太陽の方位: " + s.sunAzimuth + "° (" + compass(s.sunAzimuth) + ")", NamedTextColor.GOLD, List.of("0°=北 90°=東 180°=南 270°=西", "影はこの反対側にできる")), null);
        put(holder, 12, item(Material.LIME_STAINED_GLASS_PANE, "方位 +15°", NamedTextColor.GREEN, List.of("太陽を右回りに動かす")), (p, c) -> {
            s.sunAzimuth = Math.floorMod(s.sunAzimuth + (big(c) ? 45 : 15), 360);
            refresh(p, Page.SUN);
        });
        put(holder, 13, item(Material.ENDER_PEARL, "いま見ている方向に太陽を置く", NamedTextColor.AQUA, List.of("あなたが向いている方位を太陽の方位にする")), (p, c) -> {
            double yaw = p.getLocation().getYaw();
            // minecraft yaw: 0 = south, 90 = west; the compass: 0 = north, 90 = east
            s.sunAzimuth = Math.floorMod((int) Math.round(yaw + 180.0), 360);
            refresh(p, Page.SUN);
        });
        put(holder, 14, item(Material.RED_STAINED_GLASS_PANE, "高さ -5°", NamedTextColor.RED, List.of("太陽を低くする (影が長くなる)")), (p, c) -> {
            s.sunElevation = Math.max(5, s.sunElevation - (big(c) ? 15 : 5));
            refresh(p, Page.SUN);
        });
        put(holder, 15, item(Material.CLOCK, "太陽の高さ: " + s.sunElevation + "°", NamedTextColor.GOLD, List.of("5°=朝夕 90°=真上")), null);
        put(holder, 16, item(Material.LIME_STAINED_GLASS_PANE, "高さ +5°", NamedTextColor.GREEN, List.of("太陽を高くする")), (p, c) -> {
            s.sunElevation = Math.min(90, s.sunElevation + (big(c) ? 15 : 5));
            refresh(p, Page.SUN);
        });
        put(holder, 19, item(Material.ORANGE_DYE, "朝", NamedTextColor.GOLD, List.of("東の低い太陽 (方位90° 高さ20°)")), (p, c) -> preset(p, s, 90, 20));
        put(holder, 20, item(Material.YELLOW_DYE, "昼", NamedTextColor.YELLOW, List.of("南の高い太陽 (方位180° 高さ65°)")), (p, c) -> preset(p, s, 180, 65));
        put(holder, 21, item(Material.RED_DYE, "夕", NamedTextColor.RED, List.of("西の低い太陽 (方位270° 高さ20°)")), (p, c) -> preset(p, s, 270, 20));
        put(holder, 22, item(Material.WHITE_DYE, "斜め上 (標準)", NamedTextColor.WHITE, List.of("南東の斜め上 (方位135° 高さ45°)")), (p, c) -> preset(p, s, 135, 45));
        put(holder, 28, item(Material.BLACK_DYE, "陰の濃さ: " + Math.round(s.shadeDepth * 100) + "%", NamedTextColor.AQUA, List.of("真っ暗な面を、どれくらい暗い色のブロックにするか", "左クリック +5% / 右クリック -5%")), (p, c) -> {
            s.shadeDepth = Math.max(0.1, Math.min(0.8, s.shadeDepth + (left(c) ? 0.05 : -0.05)));
            refresh(p, Page.SUN);
        });
        put(holder, 29, item(Material.OBSIDIAN, "影を落とす: " + onOff(s.castShadows), NamedTextColor.AQUA, List.of("オン: 壁や木に遮られた所も影にする (少し重い)", "オフ: 面の向きだけで決める", "クリックで切り替え")), (p, c) -> {
            s.castShadows = !s.castShadows;
            refresh(p, Page.SUN);
        });
        put(holder, 44, item(Material.BOOK, "つかいかた", NamedTextColor.GOLD, List.of(
                "メインで「自動陰影」を選び、ブラシを影にしたい所に使う",
                "太陽と反対側を向いた面や、遮られた面が、いまの色から",
                "自動で暗いブロックになる (階段・ハーフ・壁も形を保つ)",
                "模様にブロックを入れると、その中からだけ選ぶ")), null);
    }

    private void preset(Player player, BrushSettings s, int azimuth, int elevation) {
        s.sunAzimuth = azimuth;
        s.sunElevation = elevation;
        refresh(player, Page.SUN);
    }

    // ---- events -----------------------------------------------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == holder.inventory) {
            BiConsumer<Player, ClickType> action = holder.actions.get(event.getSlot());
            if (action != null) action.accept(player, event.getClick());
            return;
        }
        // a block of the player's own inventory: added to the pattern or the block mask
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        BrushSettings s = module.settings(player);
        if (holder.page == Page.PATTERN) {
            addPattern(player, s, clicked.getType());
            refresh(player, Page.PATTERN);
        } else if (holder.page == Page.MASK) {
            Material material = clicked.getType();
            if (material.isBlock() && material.isItem() && !material.isAir()) s.blockMask.add(material);
            refresh(player, Page.MASK);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
