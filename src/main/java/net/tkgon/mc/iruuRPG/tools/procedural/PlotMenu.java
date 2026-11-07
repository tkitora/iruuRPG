package net.tkgon.mc.iruuRPG.tools.procedural;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Kit;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Plan;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

import static net.tkgon.mc.iruuRPG.tools.procedural.ProceduralMenu.glint;
import static net.tkgon.mc.iruuRPG.tools.procedural.ProceduralMenu.icon;
import static net.tkgon.mc.iruuRPG.tools.procedural.ProceduralMenu.item;
import static net.tkgon.mc.iruuRPG.tools.procedural.ProceduralMenu.text;

/**
 * (t313) The pages of the menu for a building of lots: the lots (the list, and the numbers of the one chosen), the storeys of the lot chosen, the building made by
 * itself, the blocks of every role, and the parts of the kit. The numbers of a lot and of a storey are {@link Field}s, the same for the menu (a click: the next
 * or the one before) and the command ({@code /proceduraltool lot <lot> <field> <value>}).
 */
final class PlotMenu {

    private PlotMenu() {
    }

    // ---- the slots ---------------------------------------------------------------------------------------------------------------
    static final int INFO = 10;
    static final int[] LOT_SLOTS = {11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
    static final int LOT_ADD = 25;
    static final int[] VALUE_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 46, 48, 50, 53};
    static final int[] FLOOR_SLOTS = {11, 12, 13, 14, 15, 16, 19, 20};
    static final int FLOOR_ADD = 21;
    static final int FLOOR_REMOVE = 22;
    static final int LOT_REMOVE = 26;
    static final int[] BLOCK_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    static final int[] KIT_SLOTS = BLOCK_SLOTS;
    static final int KIT_PREV = 46;
    static final int KIT_FILTER = 48;
    static final int KIT_NEXT = 50;
    static final int KIT_HELP = 53;
    static final int AUTO_NEW = 11;
    static final int AUTO_MODE = 13;
    static final int AUTO_GUIDE = 15;
    static final int AUTO_REPORT = 31;

    static final String[] SIDES = {"北 (裏)", "東 (右)", "南 (正面)", "西 (左)"};
    static final String[] SIDE_IDS = {"n", "e", "s", "w"};

    /**
     * A number of a lot or a storey: its name for the command, its label, its range, how it is read and set, and the names of its values (null: a number;
     * the first name is for {@code min}).
     */
    record Field<T>(String id, String label, String help, int min, int max, ToIntFunction<T> get, BiConsumer<T, Integer> set, String[] names) {

        String show(T target) {
            int value = get.applyAsInt(target);
            if (names != null) return names[Math.max(0, Math.min(names.length - 1, value - min))];
            return Integer.toString(value);
        }

        void step(T target, int delta) {
            int value = get.applyAsInt(target);
            int next = value + delta;
            // (a field of names goes round; a number stops at its ends)
            if (names != null) next = min + Math.floorMod(next - min, max - min + 1);
            set.accept(target, Math.max(min, Math.min(max, next)));
        }

        /** Sets it from the command's word: a number, +n or -n, one of its names (or their ids), on / off. Null when it was set, else what is wrong. */
        String parse(T target, String word) {
            String w = word.trim().toLowerCase(Locale.ROOT);
            try {
                if (w.startsWith("+") || w.startsWith("-") && w.length() > 1 && names == null) {
                    step(target, Integer.parseInt(w.startsWith("+") ? w.substring(1) : w));
                    return null;
                }
                int value = Integer.parseInt(w);
                if (value < min || value > max) return id + " は " + min + " 〜 " + max;
                set.accept(target, value);
                return null;
            } catch (NumberFormatException ignored) {
                // (a name)
            }
            if (w.equals("on") || w.equals("true") || w.equals("オン")) w = "1";
            if (w.equals("off") || w.equals("false") || w.equals("オフ")) w = "0";
            if (w.equals("1") || w.equals("0")) {
                set.accept(target, Integer.parseInt(w));
                return null;
            }
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    String name = names[i].toLowerCase(Locale.ROOT);
                    if (name.equals(w) || name.startsWith(w + " ") || name.contains("(" + w + ")")) {
                        set.accept(target, min + i);
                        return null;
                    }
                }
            }
            return id + " に使えない値: " + word + (names == null ? "" : " (" + String.join(" / ", names) + ")");
        }
    }

    private static String[] names(Enum<?>[] values, java.util.function.Function<Enum<?>, String> label) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = label.apply(values[i]) + " (" + values[i].name().toLowerCase(Locale.ROOT) + ")";
        return out;
    }

    private static final String[] ON_OFF = {"オフ", "オン"};

    /** The numbers of a lot. */
    static final List<Field<Plan.Lot>> LOT_FIELDS = List.of(
            new Field<>("shape", "形", "長方形 / 円 / 八角形", 0, 2, lot -> lot.shape.ordinal(), (lot, v) -> lot.shape = Plan.Shape.values()[v],
                    names(Plan.Shape.values(), e -> ((Plan.Shape) e).label)),
            new Field<>("x", "位置 x", "敷地の左からの位置 (モジュールの格子にそろう)", 0, 96, lot -> lot.x, (lot, v) -> lot.x = v, null),
            new Field<>("z", "位置 z", "敷地の奥からの位置 (モジュールの格子にそろう)", 0, 96, lot -> lot.z, (lot, v) -> lot.z = v, null),
            new Field<>("w", "幅", "東西の長さ (モジュールの倍数 + 1 にそろう)", 3, 81, lot -> lot.w, (lot, v) -> lot.w = v, null),
            new Field<>("d", "奥行き", "南北の長さ (モジュールの倍数 + 1 にそろう)", 3, 81, lot -> lot.d, (lot, v) -> lot.d = v, null),
            new Field<>("foundation", "基礎", "石の基礎 / 高床の柱 / なし", 0, 2, lot -> lot.foundation.ordinal(), (lot, v) -> lot.foundation = Plan.Foundation.values()[v],
                    names(Plan.Foundation.values(), e -> ((Plan.Foundation) e).label)),
            new Field<>("base", "基礎の高さ", "地面から 1 階の床まで (ブロック)", 0, 12, lot -> lot.base, (lot, v) -> lot.base = v, null),
            new Field<>("floors", "階数", "この区画の階の数 (階の高さ・壁は「階」のページ)", 1, Plan.MAX_FLOORS, lot -> lot.floors.size(), PlotMenu::setFloors, null),
            new Field<>("roof", "屋根", "屋根の形", 0, Plan.RoofType.values().length - 1, lot -> lot.roof.type.ordinal(), (lot, v) -> lot.roof.type = Plan.RoofType.values()[v],
                    names(Plan.RoofType.values(), e -> ((Plan.RoofType) e).label)),
            new Field<>("ridge", "棟の向き", "自動 (長い辺に沿う・隣の区画へ向く) / 東西 / 南北", -1, 1, lot -> lot.roof.ridge, (lot, v) -> lot.roof.ridge = v,
                    new String[]{"自動 (auto)", "東西 (x)", "南北 (z)"}),
            new Field<>("pitch", "勾配", "屋根の勾配 (整数比)", 0, Plan.PITCHES.length - 1, lot -> lot.roof.pitch, (lot, v) -> lot.roof.pitch = v, Plan.PITCH_NAMES),
            new Field<>("overhang", "軒の出", "屋根が壁から出る長さ (4 辺とも。辺ごとは /proceduraltool lot <区画> overhang-n 2 など)", 0, 2, lot -> lot.roof.overhang[2],
                    (lot, v) -> java.util.Arrays.fill(lot.roof.overhang, v), null),
            new Field<>("dormers", "ドーマー", "屋根窓の数 (軒の側、柱の格子にそろえて左右対称に)", 0, 6, lot -> lot.roof.dormers, (lot, v) -> lot.roof.dormers = v, null),
            new Field<>("chimney", "煙突", "煙突の位置", 0, Plan.CHIMNEYS.length - 1, lot -> lot.roof.chimney, (lot, v) -> lot.roof.chimney = v, Plan.CHIMNEYS),
            new Field<>("group", "屋根のグループ", "軒の高さが同じで同じグループの区画は、屋根を結合する (谷ができる)", 0, 9, lot -> lot.roof.group, (lot, v) -> lot.roof.group = v, null),
            new Field<>("porch", "ポーチ", "1 階の扉の前に、柱と片流れの屋根", 0, 1, lot -> lot.porch ? 1 : 0, (lot, v) -> lot.porch = v == 1, ON_OFF),
            new Field<>("finial", "頂華・風見", "尖塔の先・棟の端に飾り", 0, 1, lot -> lot.roof.finial ? 1 : 0, (lot, v) -> lot.roof.finial = v == 1, ON_OFF),
            new Field<>("special", "特殊な部品", "面のアンカーに付ける部品 (向きは special-side)", 0, Plan.Special.values().length - 1, lot -> lot.roof.special.ordinal(),
                    (lot, v) -> lot.roof.special = Plan.Special.values()[v], names(Plan.Special.values(), e -> ((Plan.Special) e).label)),
            // (command only: not on the page)
            new Field<>("special-side", "特殊な部品の面", "", 0, 3, lot -> lot.roof.specialSide, (lot, v) -> lot.roof.specialSide = v, SIDES),
            new Field<>("braces", "高床の筋交い", "", 0, 1, lot -> lot.braces ? 1 : 0, (lot, v) -> lot.braces = v == 1, ON_OFF),
            new Field<>("thickness", "屋根の厚さ", "", 2, 3, lot -> lot.roof.thickness, (lot, v) -> lot.roof.thickness = v, null),
            new Field<>("low", "片流れの低い側", "", -1, 3, lot -> lot.roof.low, (lot, v) -> lot.roof.low = v, new String[]{"自動 (auto)", SIDES[0], SIDES[1], SIDES[2], SIDES[3]}),
            new Field<>("overhang-n", "軒の出 (北)", "", 0, 2, lot -> lot.roof.overhang[0], (lot, v) -> lot.roof.overhang[0] = v, null),
            new Field<>("overhang-e", "軒の出 (東)", "", 0, 2, lot -> lot.roof.overhang[1], (lot, v) -> lot.roof.overhang[1] = v, null),
            new Field<>("overhang-s", "軒の出 (南)", "", 0, 2, lot -> lot.roof.overhang[2], (lot, v) -> lot.roof.overhang[2] = v, null),
            new Field<>("overhang-w", "軒の出 (西)", "", 0, 2, lot -> lot.roof.overhang[3], (lot, v) -> lot.roof.overhang[3] = v, null));

    /** How many of {@link #LOT_FIELDS} are on the page (the rest by command). */
    static final int LOT_PAGE_FIELDS = 18;

    /** The numbers of a storey. */
    static final List<Field<Plan.Floor>> FLOOR_FIELDS = List.of(
            new Field<>("height", "階の高さ", "床から次の床まで (梁を含む、ブロック)", 3, 9, floor -> floor.height, (floor, v) -> floor.height = v, null),
            new Field<>("windows", "窓の並べ方", "壁パネルの窓", 0, Plan.Windows.values().length - 1, floor -> floor.windows.ordinal(), (floor, v) -> floor.windows = Plan.Windows.values()[v],
                    names(Plan.Windows.values(), e -> ((Plan.Windows) e).label)),
            new Field<>("jetty-n", "せり出し (北)", "この階の北の辺を下の階より 1 外へ (下に持ち送り)", 0, 1, floor -> floor.jetty[0], (floor, v) -> floor.jetty[0] = v, null),
            new Field<>("jetty-e", "せり出し (東)", "", 0, 1, floor -> floor.jetty[1], (floor, v) -> floor.jetty[1] = v, null),
            new Field<>("jetty-s", "せり出し (南)", "", 0, 1, floor -> floor.jetty[2], (floor, v) -> floor.jetty[2] = v, null),
            new Field<>("jetty-w", "せり出し (西)", "", 0, 1, floor -> floor.jetty[3], (floor, v) -> floor.jetty[3] = v, null),
            new Field<>("balcony-n", "バルコニー (北)", "この辺の床の高さに、バルコニー・回廊 (4 辺とも: 回廊)", 0, 1, floor -> floor.balcony[0] ? 1 : 0, (floor, v) -> floor.balcony[0] = v == 1, ON_OFF),
            new Field<>("balcony-e", "バルコニー (東)", "", 0, 1, floor -> floor.balcony[1] ? 1 : 0, (floor, v) -> floor.balcony[1] = v == 1, ON_OFF),
            new Field<>("balcony-s", "バルコニー (南)", "", 0, 1, floor -> floor.balcony[2] ? 1 : 0, (floor, v) -> floor.balcony[2] = v == 1, ON_OFF),
            new Field<>("balcony-w", "バルコニー (西)", "", 0, 1, floor -> floor.balcony[3] ? 1 : 0, (floor, v) -> floor.balcony[3] = v == 1, ON_OFF),
            new Field<>("skirt", "腰屋根", "この階の上端を一周する小さな屋根 (多層の塔に)", 0, 1, floor -> floor.skirt ? 1 : 0, (floor, v) -> floor.skirt = v == 1, ON_OFF),
            new Field<>("door", "扉", "扉のある辺 (2 階から上は、外階段かバルコニーへ)", -1, 3, floor -> floor.door, (floor, v) -> floor.door = v,
                    new String[]{"なし (none)", SIDES[0] + " (n)", SIDES[1] + " (e)", SIDES[2] + " (s)", SIDES[3] + " (w)"}),
            new Field<>("stairs", "外階段", "この階の扉から地面まで、外壁に沿う階段", 0, 1, floor -> floor.stairs ? 1 : 0, (floor, v) -> floor.stairs = v == 1, ON_OFF),
            new Field<>("color", "漆喰の色", "漆喰 / 漆喰 2 / 漆喰 3 の役割のブロック", 0, 2, floor -> floor.color, (floor, v) -> floor.color = v, new String[]{"漆喰", "漆喰 2", "漆喰 3"}));

    /** Sets a lot's storeys to {@code count}: new ones copy the top one (its door and stairs left out). */
    private static void setFloors(Plan.Lot lot, int count) {
        while (lot.floors.size() > count && lot.floors.size() > 1) lot.floors.remove(lot.floors.size() - 1);
        while (lot.floors.size() < count && lot.floors.size() < Plan.MAX_FLOORS) {
            Plan.Floor copy = lot.floors.get(lot.floors.size() - 1).copy();
            copy.door = -1;
            copy.stairs = false;
            lot.floors.add(copy);
        }
    }

    static Field<Plan.Lot> lotField(String id) {
        for (Field<Plan.Lot> field : LOT_FIELDS) if (field.id.equalsIgnoreCase(id)) return field;
        return null;
    }

    static Field<Plan.Floor> floorField(String id) {
        for (Field<Plan.Floor> field : FLOOR_FIELDS) if (field.id.equalsIgnoreCase(id)) return field;
        return null;
    }

    /** The sets of wall panels the kit has (its walls' tags but the role and storey ones), "auto" first. */
    static List<String> panelSets() {
        Set<String> sets = new LinkedHashSet<>();
        sets.add("auto");
        for (Kit.Part part : PlotShape.kit().find(Kit.Category.WALL)) {
            for (String tag : part.tags) if (!List.of("solid", "window", "door", "ground", "upper", "any", "round-window").contains(tag)) sets.add(tag);
        }
        return new ArrayList<>(sets);
    }

    // ---- the pages -------------------------------------------------------------------------------------------------------------

    /** The lot chosen (kept in range), or null when there are none. */
    static Plan.Lot lot(TreeSession session, TreeSettings s) {
        if (s.lots().isEmpty()) return null;
        session.plotLot = Math.max(0, Math.min(s.lots().size() - 1, session.plotLot));
        return s.lots().get(session.plotLot);
    }

    static Plan.Floor floor(TreeSession session, Plan.Lot lot) {
        session.plotFloor = Math.max(0, Math.min(lot.floors.size() - 1, session.plotFloor));
        return lot.floors.get(session.plotFloor);
    }

    static void renderLots(Inventory inventory, TreeSession session, TreeSettings s) {
        List<Plan.Lot> lots = s.lots();
        Plan.Lot chosen = lot(session, s);
        inventory.setItem(INFO, item(Material.MAP, "区画 (" + lots.size() + " / " + Plan.MAX_LOTS + ")", NamedTextColor.GOLD, List.of(
                text("建物は区画 (長方形・円・八角形) の集まり。区画ごとに基礎・階・屋根を決める", NamedTextColor.GRAY),
                text("最初の区画が本棟。区画どうしが接する辺は内側になり、屋根は隣に合わせて結合・壁当て", NamedTextColor.GRAY),
                text("区画をクリックで選ぶ。下の段がその区画の数字 (左クリック +、右クリック -、Shift で大きく)", NamedTextColor.GRAY),
                text("名前の変更など: /proceduraltool lot <区画> <項目> <値>", NamedTextColor.DARK_GRAY))));
        for (int i = 0; i < lots.size() && i < LOT_SLOTS.length; i++) {
            Plan.Lot lot = lots.get(i);
            boolean here = lot == chosen;
            Material shape = switch (lot.shape) {
                case ROUND -> Material.SNOWBALL;
                case OCTAGON -> Material.SCUTE;
                default -> i == 0 ? Material.OAK_DOOR : Material.OAK_PLANKS;
            };
            inventory.setItem(LOT_SLOTS[i], glint(item(shape, (here ? "▶ " : "") + lot.name + (i == 0 ? " (本棟)" : ""), here ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(
                    text(lot.shape.label + " " + lot.w + "x" + lot.d + " (" + lot.x + ", " + lot.z + ") / " + lot.floors.size() + " 階 / " + lot.roof.type.label, NamedTextColor.WHITE),
                    text("クリック: 選ぶ", NamedTextColor.YELLOW))), here));
        }
        if (lots.size() < Plan.MAX_LOTS) {
            inventory.setItem(LOT_ADD, item(Material.LIME_DYE, "区画を足す", NamedTextColor.GREEN, List.of(
                    text("左クリック: 長方形の区画を足す (選んだ区画の右隣に)", NamedTextColor.YELLOW),
                    text("右クリック: 選んだ区画の写しを足す", NamedTextColor.YELLOW))));
        }
        if (chosen != null && lots.size() > 1) {
            inventory.setItem(LOT_REMOVE, item(Material.BARRIER, "区画を消す: " + chosen.name, NamedTextColor.RED, List.of(
                    text("Shift + 右クリック: 選んだ区画を消す", NamedTextColor.YELLOW))));
        }
        if (chosen == null) return;
        for (int i = 0; i < LOT_PAGE_FIELDS && i < VALUE_SLOTS.length; i++) {
            Field<Plan.Lot> field = LOT_FIELDS.get(i);
            inventory.setItem(VALUE_SLOTS[i], fieldItem(field, chosen, lotIcon(field.id)));
        }
    }

    static void renderFloors(Inventory inventory, TreeSession session, TreeSettings s) {
        Plan.Lot lot = lot(session, s);
        if (lot == null) return;
        Plan.Floor chosen = floor(session, lot);
        inventory.setItem(INFO, item(Material.LADDER, lot.name + " の階 (" + lot.floors.size() + ")", NamedTextColor.GOLD, List.of(
                text("階をクリックで選ぶ。下の段がその階の数字", NamedTextColor.GRAY),
                text("壁パネルのセット: /proceduraltool lot " + lot.name + " floor <階> panels <セット>", NamedTextColor.DARK_GRAY),
                text("セット: " + String.join(", ", panelSets()), NamedTextColor.DARK_GRAY))));
        for (int k = 0; k < lot.floors.size() && k < FLOOR_SLOTS.length; k++) {
            Plan.Floor floor = lot.floors.get(k);
            boolean here = floor == chosen;
            inventory.setItem(FLOOR_SLOTS[k], glint(item(k == 0 ? Material.COBBLESTONE : Material.STRIPPED_SPRUCE_LOG, (here ? "▶ " : "") + (k + 1) + " 階",
                    here ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(
                            text("高さ " + floor.height + " / パネル " + floor.panels + " / 窓 " + floor.windows.label, NamedTextColor.WHITE),
                            text("クリック: 選ぶ", NamedTextColor.YELLOW))), here));
        }
        if (lot.floors.size() < Plan.MAX_FLOORS) {
            inventory.setItem(FLOOR_ADD, item(Material.LIME_DYE, "階を足す", NamedTextColor.GREEN, List.of(text("いちばん上の階の写しを、上に足す", NamedTextColor.YELLOW))));
        }
        if (lot.floors.size() > 1) {
            inventory.setItem(FLOOR_REMOVE, item(Material.RED_DYE, "いちばん上の階を消す", NamedTextColor.RED, List.of(text("Shift + 右クリック", NamedTextColor.YELLOW))));
        }
        List<String> sets = panelSets();
        inventory.setItem(VALUE_SLOTS[0], item(Material.WHITE_TERRACOTTA, "壁パネルのセット: " + chosen.panels, NamedTextColor.GREEN, List.of(
                text("部品キットの壁パネルのタグ。auto: 1 階は石、2 階からは木組み (1 階だけの区画は漆喰)", NamedTextColor.GRAY),
                text("セット: " + String.join(" / ", sets), NamedTextColor.WHITE),
                text("左クリック: 次 / 右クリック: 前", NamedTextColor.YELLOW))));
        for (int i = 0; i < FLOOR_FIELDS.size() && i + 1 < VALUE_SLOTS.length; i++) {
            Field<Plan.Floor> field = FLOOR_FIELDS.get(i);
            inventory.setItem(VALUE_SLOTS[i + 1], fieldItem(field, chosen, floorIcon(field.id)));
        }
    }

    private static <T> org.bukkit.inventory.ItemStack fieldItem(Field<T> field, T target, Material icon) {
        List<Component> lore = new ArrayList<>();
        if (!field.help.isEmpty()) lore.add(text(field.help, NamedTextColor.GRAY));
        String range = field.names == null ? "  (" + field.min + " 〜 " + field.max + ")" : "";
        lore.add(text("現在: " + field.show(target) + range, NamedTextColor.WHITE));
        lore.add(text(field.names == null ? "左クリック +1 / 右クリック -1 (Shift で 4 ずつ)" : "左クリック: 次 / 右クリック: 前", NamedTextColor.YELLOW));
        lore.add(text("/proceduraltool lot <区画> " + field.id + " <値>", NamedTextColor.DARK_GRAY));
        return item(icon, field.label + ": " + field.show(target), NamedTextColor.GREEN, lore);
    }

    private static Material lotIcon(String id) {
        return switch (id) {
            case "shape" -> Material.SNOWBALL;
            case "x", "z" -> Material.COMPASS;
            case "w", "d" -> Material.OAK_PLANKS;
            case "foundation", "base" -> Material.COBBLESTONE;
            case "floors" -> Material.LADDER;
            case "roof", "ridge", "pitch", "overhang" -> Material.DEEPSLATE_TILES;
            case "dormers" -> Material.GLASS_PANE;
            case "chimney" -> Material.BRICKS;
            case "group" -> Material.CHAIN;
            case "porch" -> Material.SPRUCE_FENCE;
            case "finial" -> Material.LIGHTNING_ROD;
            default -> Material.BELL;
        };
    }

    private static Material floorIcon(String id) {
        if (id.startsWith("jetty")) return Material.SPRUCE_STAIRS;
        if (id.startsWith("balcony")) return Material.SPRUCE_TRAPDOOR;
        return switch (id) {
            case "height" -> Material.LADDER;
            case "windows" -> Material.GLASS_PANE;
            case "skirt" -> Material.DEEPSLATE_TILE_STAIRS;
            case "door" -> Material.SPRUCE_DOOR;
            case "stairs" -> Material.COBBLESTONE_STAIRS;
            default -> Material.WHITE_DYE;
        };
    }

    static void renderAuto(Inventory inventory, TreeSession session, TreeSettings s) {
        inventory.setItem(INFO, item(Material.NETHER_STAR, "おまかせ生成", NamedTextColor.GOLD, List.of(
                text("生成パターンの区画の文法で区画を並べ、階・壁・屋根・部品をランダムに選んで建物を作る", NamedTextColor.GRAY),
                text("本棟は敷地の 5〜7 割、翼棟は 1〜2 階低く本棟の短辺まで、塔は本棟の半分の幅で 1〜2 階高く", NamedTextColor.GRAY),
                text("制約 (本棟がいちばん大きい・軒の 1 段差なし) と検証に合わなければ作り直し、候補を採点していちばん良いものを使う", NamedTextColor.GRAY),
                text("パターン・候補の数・作り直し・高床の確率・部品の多さ・勾配の上限は「敷地」のページ", NamedTextColor.DARK_GRAY))));
        inventory.setItem(AUTO_NEW, item(Material.NETHER_STAR, "新しい建物を作る", NamedTextColor.GREEN, List.of(
                text("毎回ちがう建物を作って、区画のページに入れる (手で直せる)", NamedTextColor.GRAY),
                text("作り方は「区画どおり」になる", NamedTextColor.GRAY),
                text("クリックで作る", NamedTextColor.YELLOW))));
        boolean auto = s.getInt(TreeSettings.Key.PLOT_MODE) == 1;
        inventory.setItem(AUTO_MODE, item(auto ? Material.CLOCK : Material.MAP, "作り方: " + (auto ? "毎回おまかせ" : "区画どおり"), NamedTextColor.AQUA, List.of(
                text("区画どおり: 区画のページの区画で作る", NamedTextColor.GRAY),
                text("毎回おまかせ: 生成のたびに、生成パターンからちがう建物を作る (シードがランダムのとき)", NamedTextColor.GRAY),
                text("クリックで切り替え", NamedTextColor.YELLOW))));
        inventory.setItem(AUTO_GUIDE, item(Material.BOOK, "パターン", NamedTextColor.WHITE, List.of(
                text("1 単棟 / 2 本棟 + 下屋 / 3 L字・T字の交差 / 4 塔付き", NamedTextColor.GRAY),
                text("5 高床・桟橋 / 6 多層の塔 / 7 段状 / 8 風車", NamedTextColor.GRAY),
                text("いま: " + PlotShape.valueName(TreeSettings.Key.PLOT_PATTERN, s.get(TreeSettings.Key.PLOT_PATTERN)), NamedTextColor.WHITE))));
        String report = session.plotReport == null || session.plotReport.isEmpty() ? "まだありません (プレビューか生成で出る)" : session.plotReport;
        List<Component> lore = new ArrayList<>();
        for (String part : report.split(" / ")) lore.add(text(part, NamedTextColor.WHITE));
        inventory.setItem(AUTO_REPORT, item(Material.PAPER, "いまの建物の検証と点", NamedTextColor.GOLD, lore));
    }

    static void renderBlocks(Inventory inventory, TreeSettings s, String picking, ProceduralModule tool) {
        inventory.setItem(INFO, null);
        List<TreePart> parts = ProcKind.PLOT.parts();
        for (int i = 0; i < parts.size() && i < BLOCK_SLOTS.length; i++) {
            TreePart part = parts.get(i);
            Material block = tool.material(part, s);
            List<Component> lore = new ArrayList<>();
            lore.add(text("ブロック: " + block.getKey().getKey() + (s.block(part) == null ? " (初期値)" : ""), NamedTextColor.WHITE));
            lore.add(text("左クリックのあと、自分のインベントリのブロックをクリックで選ぶ", NamedTextColor.YELLOW));
            lore.add(text("右クリック: 初期値に戻す (/proceduraltool block " + part.key() + " <ブロック名> でも選べる)", NamedTextColor.YELLOW));
            lore.add(text("階段・ハーフなどは、そのブロックの仲間 (羊毛は色のいちばん近いもの)", NamedTextColor.DARK_GRAY));
            boolean waiting = ("part:" + part.name()).equals(picking);
            if (waiting) lore.add(0, text("▶ インベントリのブロックをクリックしてください", NamedTextColor.GOLD));
            inventory.setItem(BLOCK_SLOTS[i], glint(item(icon(block), part.label() + ": " + block.getKey().getKey(), waiting ? NamedTextColor.GOLD : NamedTextColor.WHITE, lore), waiting));
        }
    }

    /** The role whose block is at {@code slot} of the blocks page, or null. */
    static TreePart blockPart(int slot) {
        List<TreePart> parts = ProcKind.PLOT.parts();
        for (int i = 0; i < parts.size() && i < BLOCK_SLOTS.length; i++) if (BLOCK_SLOTS[i] == slot) return parts.get(i);
        return null;
    }

    static void renderKit(Inventory inventory, TreeSession session) {
        List<Kit.Part> parts = kitParts(session);
        int pages = Math.max(1, (parts.size() + KIT_SLOTS.length - 1) / KIT_SLOTS.length);
        session.kitPage = Math.max(0, Math.min(pages - 1, session.kitPage));
        for (int i = 0; i < KIT_SLOTS.length; i++) {
            int index = session.kitPage * KIT_SLOTS.length + i;
            if (index >= parts.size()) break;
            Kit.Part part = parts.get(index);
            List<Component> lore = new ArrayList<>();
            lore.add(text(part.category.label + "・" + part.kind.label + (part.builtin ? " (キット)" : " (登録)"), NamedTextColor.GRAY));
            lore.add(text("タグ: " + (part.tags.isEmpty() ? "なし" : String.join(", ", part.tags)), NamedTextColor.WHITE));
            if (part.kind == Kit.Kind.PROCEDURAL) {
                lore.add(text("大きさ: 幅 " + part.minW + "〜" + part.maxW + " / 高さ " + part.minH + "〜" + part.maxH, NamedTextColor.WHITE));
                if (part.opening != Kit.Opening.NONE) lore.add(text("開口: " + part.opening.name().toLowerCase(Locale.ROOT), NamedTextColor.WHITE));
                if (!part.lines.isEmpty()) lore.add(text("線: " + part.lines.size() + " 本", NamedTextColor.WHITE));
            } else {
                lore.add(text("大きさ: " + part.sizeU + " x " + part.sizeV + " x " + part.sizeD + " / アンカー " + part.anchors.keySet(), NamedTextColor.WHITE));
            }
            if (!part.params.isEmpty()) lore.add(text("数字: " + part.params, NamedTextColor.WHITE));
            lore.add(text("/proceduraltool kit info|preview|set|tag " + part.id, NamedTextColor.DARK_GRAY));
            inventory.setItem(KIT_SLOTS[i], item(kitIcon(part), part.id, part.builtin ? NamedTextColor.WHITE : NamedTextColor.AQUA, lore));
        }
        inventory.setItem(INFO, null);
        Kit.Category filter = session.kitCategory < 0 ? null : Kit.Category.values()[session.kitCategory];
        inventory.setItem(KIT_PREV, item(Material.ARROW, "前のページ", NamedTextColor.YELLOW, List.of(text((session.kitPage + 1) + " / " + pages, NamedTextColor.GRAY))));
        inventory.setItem(KIT_NEXT, item(Material.ARROW, "次のページ", NamedTextColor.YELLOW, List.of(text((session.kitPage + 1) + " / " + pages, NamedTextColor.GRAY))));
        inventory.setItem(KIT_FILTER, item(Material.HOPPER, "カテゴリ: " + (filter == null ? "すべて" : filter.label), NamedTextColor.AQUA, List.of(
                text("左クリック: 次のカテゴリ / 右クリック: すべて", NamedTextColor.YELLOW))));
        inventory.setItem(KIT_HELP, item(Material.BOOK, "部品の登録・編集 (コマンド)", NamedTextColor.WHITE, List.of(
                text("/proceduraltool kit pos1 / pos2 (見ているブロック) → kit save <id> <カテゴリ> [タグ…]", NamedTextColor.GRAY),
                text("kit anchor <id> <名前> (見ているブロック) / kit slice <id> u|v <から> <まで> (9スライス)", NamedTextColor.GRAY),
                text("kit tag <id> +タグ -タグ / kit set <id> <数字> <値> / kit preview <id> [幅 高さ]", NamedTextColor.GRAY),
                text("kit list [カテゴリ|タグ] / kit info <id> / kit delete <id> / kit reload", NamedTextColor.GRAY))));
    }

    static List<Kit.Part> kitParts(TreeSession session) {
        List<Kit.Part> out = new ArrayList<>();
        for (Kit.Part part : PlotShape.kit().all()) if (session.kitCategory < 0 || part.category.ordinal() == session.kitCategory) out.add(part);
        return out;
    }

    private static Material kitIcon(Kit.Part part) {
        return switch (part.category) {
            case WALL -> part.opening == Kit.Opening.WINDOW ? Material.GLASS_PANE : part.opening == Kit.Opening.DOOR ? Material.OAK_DOOR
                    : part.fill == net.tkgon.mc.iruuRPG.tools.procedural.plot.Role.STONE ? Material.STONE_BRICKS : Material.WHITE_TERRACOTTA;
            case GABLE -> Material.SPRUCE_STAIRS;
            case CORNER -> Material.STRIPPED_SPRUCE_LOG;
            case ROUND_WALL -> Material.SNOWBALL;
            case BASE -> Material.COBBLESTONE;
            case ROOF -> Material.DEEPSLATE_TILES;
            case ROOF_PART -> Material.BRICKS;
            case OUTSIDE -> Material.SPRUCE_FENCE;
        };
    }

    // ---- clicks ------------------------------------------------------------------------------------------------------------------

    /** A click on the lots page; whether it changed something. */
    static boolean clickLots(TreeSession session, TreeSettings s, int slot, boolean left, boolean big, boolean shiftRight) {
        List<Plan.Lot> lots = s.lots();
        for (int i = 0; i < lots.size() && i < LOT_SLOTS.length; i++) {
            if (LOT_SLOTS[i] != slot) continue;
            session.plotLot = i;
            session.plotFloor = 0;
            return true;
        }
        Plan.Lot chosen = lot(session, s);
        if (slot == LOT_ADD && lots.size() < Plan.MAX_LOTS) {
            Plan plan = PlotShape.plan(s);
            Plan.Lot added;
            if (!left && chosen != null) {
                added = chosen.copy();
                added.name = plan.freeName(chosen.name);
            } else {
                int m = s.getInt(TreeSettings.Key.PLOT_MODULE);
                added = new Plan.Lot(plan.freeName("lot"), Plan.Shape.RECT, chosen == null ? 0 : chosen.x + chosen.w - 1, chosen == null ? 0 : chosen.z, 2 * m + 1,
                        chosen == null ? 2 * m + 1 : chosen.d, 1);
            }
            lots.add(added);
            session.plotLot = lots.size() - 1;
            return true;
        }
        if (slot == LOT_REMOVE && shiftRight && chosen != null && lots.size() > 1) {
            lots.remove(chosen);
            session.plotLot = Math.max(0, session.plotLot - 1);
            return true;
        }
        if (chosen == null) return false;
        for (int i = 0; i < LOT_PAGE_FIELDS && i < VALUE_SLOTS.length; i++) {
            if (VALUE_SLOTS[i] != slot) continue;
            Field<Plan.Lot> field = LOT_FIELDS.get(i);
            int step = field.names == null && big ? 4 : 1;
            field.step(chosen, left ? step : -step);
            return true;
        }
        return false;
    }

    /** A click on the storeys page; whether it changed something. */
    static boolean clickFloors(TreeSession session, TreeSettings s, int slot, boolean left, boolean big, boolean shiftRight) {
        Plan.Lot lot = lot(session, s);
        if (lot == null) return false;
        for (int k = 0; k < lot.floors.size() && k < FLOOR_SLOTS.length; k++) {
            if (FLOOR_SLOTS[k] != slot) continue;
            session.plotFloor = k;
            return true;
        }
        if (slot == FLOOR_ADD && lot.floors.size() < Plan.MAX_FLOORS) {
            setFloors(lot, lot.floors.size() + 1);
            session.plotFloor = lot.floors.size() - 1;
            return true;
        }
        if (slot == FLOOR_REMOVE && shiftRight && lot.floors.size() > 1) {
            setFloors(lot, lot.floors.size() - 1);
            return true;
        }
        Plan.Floor floor = floor(session, lot);
        if (slot == VALUE_SLOTS[0]) {
            List<String> sets = panelSets();
            int at = Math.max(0, sets.indexOf(floor.panels));
            floor.panels = sets.get(Math.floorMod(at + (left ? 1 : -1), sets.size()));
            return true;
        }
        for (int i = 0; i < FLOOR_FIELDS.size() && i + 1 < VALUE_SLOTS.length; i++) {
            if (VALUE_SLOTS[i + 1] != slot) continue;
            Field<Plan.Floor> field = FLOOR_FIELDS.get(i);
            int step = field.names == null && big ? 4 : 1;
            field.step(floor, left ? step : -step);
            return true;
        }
        return false;
    }

    /** A click on the kit page; whether to show it again. */
    static boolean clickKit(Player player, TreeSession session, int slot, boolean left) {
        switch (slot) {
            case KIT_PREV -> session.kitPage--;
            case KIT_NEXT -> session.kitPage++;
            case KIT_FILTER -> {
                session.kitCategory = left ? (session.kitCategory + 1 >= Kit.Category.values().length ? -1 : session.kitCategory + 1) : -1;
                session.kitPage = 0;
            }
            default -> {
                List<Kit.Part> parts = kitParts(session);
                for (int i = 0; i < KIT_SLOTS.length; i++) {
                    int index = session.kitPage * KIT_SLOTS.length + i;
                    if (KIT_SLOTS[i] != slot || index >= parts.size()) continue;
                    player.sendMessage(ProceduralModule.PREFIX + parts.get(index).summary());
                    return false;
                }
                return false;
            }
        }
        return true;
    }
}
