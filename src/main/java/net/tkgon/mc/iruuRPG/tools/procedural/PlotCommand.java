package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.plot.Kit;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Plan;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static net.tkgon.mc.iruuRPG.tools.procedural.ProceduralModule.PREFIX;

/**
 * (t313) The commands of a building of lots: {@code /proceduraltool lot ...} (the lots and their storeys, by the same fields as the menu: "区画 wing の階数を
 * +1" is {@code /proceduraltool lot wing floors +1}) and {@code /proceduraltool kit ...} (the parts of the kit: list, register from the game, anchors, 9-slice,
 * tags, numbers, preview, delete, reload).
 */
final class PlotCommand {

    private PlotCommand() {
    }

    static final List<String> LOT_SUBS = List.of("add", "remove", "select", "list");
    static final List<String> KIT_SUBS = List.of("list", "info", "pos1", "pos2", "save", "anchor", "slice", "tag", "set", "preview", "delete", "reload");

    // ---- lots -------------------------------------------------------------------------------------------------------------------

    static void lot(Player player, TreeSession session, ProceduralModule tool, String[] args) {
        TreeSettings s = session.settings;
        if (s.kind() != ProcKind.PLOT) {
            player.sendMessage(PREFIX + "区画は、作るものが「建物 (区画)」のときに使えます (/proceduraltool kind plot)。");
            return;
        }
        List<Plan.Lot> lots = s.lots();
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            player.sendMessage(PREFIX + "区画 (" + lots.size() + "): ");
            for (int i = 0; i < lots.size(); i++) {
                Plan.Lot lot = lots.get(i);
                player.sendMessage("  " + (i + 1) + ". " + lot.name + (i == session.plotLot ? " ◀" : "") + "  " + lot.shape.label + " " + lot.w + "x" + lot.d + " (" + lot.x
                        + ", " + lot.z + ") " + lot.floors.size() + "階 " + lot.foundation.label + " " + lot.base + " / 屋根 " + lot.roof.type.label + " "
                        + Plan.PITCH_NAMES[lot.roof.pitch]);
            }
            player.sendMessage(PREFIX + "/proceduraltool lot <区画> <項目> <値|+n|-n> / lot <区画> floor <階> <項目> <値> / lot add <名前> [rect|round|octagon] / lot remove <区画>");
            return;
        }
        String first = args[1].toLowerCase(Locale.ROOT);
        switch (first) {
            case "add" -> {
                if (lots.size() >= Plan.MAX_LOTS) {
                    player.sendMessage(PREFIX + "区画は " + Plan.MAX_LOTS + " までです。");
                    return;
                }
                Plan plan = PlotShape.plan(s);
                String name = plan.freeName(args.length > 2 ? args[2] : "lot");
                Plan.Shape shape = args.length > 3 ? named(args[3]) : Plan.Shape.RECT;
                int m = s.getInt(TreeSettings.Key.PLOT_MODULE);
                Plan.Lot last = lots.isEmpty() ? null : lots.get(lots.size() - 1);
                Plan.Lot lot = new Plan.Lot(name, shape, last == null ? 0 : last.x + last.w - 1, last == null ? 0 : last.z, 2 * m + 1, 2 * m + 1, 1);
                if (shape != Plan.Shape.RECT) lot.roof.type = Plan.RoofType.CONE;
                lots.add(lot);
                session.plotLot = lots.size() - 1;
                player.sendMessage(PREFIX + "区画を足しました: " + name + " (" + shape.label + ")");
            }
            case "remove" -> {
                Plan.Lot lot = args.length > 2 ? find(lots, args[2]) : null;
                if (lot == null || lots.size() <= 1) {
                    player.sendMessage(PREFIX + (lot == null ? "そんな区画はありません。" : "最後の区画は消せません。"));
                    return;
                }
                lots.remove(lot);
                session.plotLot = 0;
                player.sendMessage(PREFIX + "区画を消しました: " + lot.name);
            }
            case "select" -> {
                Plan.Lot lot = args.length > 2 ? find(lots, args[2]) : null;
                if (lot == null) {
                    player.sendMessage(PREFIX + "そんな区画はありません。");
                    return;
                }
                session.plotLot = lots.indexOf(lot);
                session.plotFloor = 0;
                player.sendMessage(PREFIX + "区画を選びました: " + lot.name);
                tool.menu().refresh(player);
                return;
            }
            default -> {
                Plan.Lot lot = find(lots, args[1]);
                if (lot == null) {
                    player.sendMessage(PREFIX + "そんな区画はありません: " + args[1] + " (/proceduraltool lot list)");
                    return;
                }
                if (args.length < 4) {
                    player.sendMessage(PREFIX + "/proceduraltool lot " + lot.name + " <項目> <値>  項目: " + lotFieldIds() + ", name, floor");
                    return;
                }
                String field = args[2].toLowerCase(Locale.ROOT);
                if (field.equals("name")) {
                    String name = Plan.safeName(args[3], lot.name);
                    if (find(lots, name) != null && find(lots, name) != lot) {
                        player.sendMessage(PREFIX + "その名前の区画はもうあります: " + name);
                        return;
                    }
                    lot.name = name;
                    player.sendMessage(PREFIX + "名前を変えました: " + name);
                } else if (field.equals("floor")) {
                    if (!floor(player, lot, Arrays.copyOfRange(args, 3, args.length))) return;
                } else {
                    PlotMenu.Field<Plan.Lot> f = PlotMenu.lotField(field);
                    if (f == null) {
                        player.sendMessage(PREFIX + "そんな項目はありません: " + field + "  項目: " + lotFieldIds());
                        return;
                    }
                    String wrong = f.parse(lot, args[3]);
                    if (wrong != null) {
                        player.sendMessage(PREFIX + wrong);
                        return;
                    }
                    player.sendMessage(PREFIX + lot.name + " の " + f.label() + ": " + f.show(lot));
                }
                session.plotLot = lots.indexOf(lot);
            }
        }
        tool.changed(player);
        tool.menu().refresh(player);
    }

    /** {@code <storey> <field> <value>} of a lot; false when nothing was set. */
    private static boolean floor(Player player, Plan.Lot lot, String[] args) {
        if (args.length < 3) {
            player.sendMessage(PREFIX + "/proceduraltool lot " + lot.name + " floor <階 (1〜" + lot.floors.size() + ")> <項目> <値>  項目: panels, " + floorFieldIds());
            return false;
        }
        int k;
        try {
            k = Integer.parseInt(args[0]) - 1;
        } catch (NumberFormatException exception) {
            k = -1;
        }
        if (k < 0 || k >= lot.floors.size()) {
            player.sendMessage(PREFIX + "階は 1〜" + lot.floors.size());
            return false;
        }
        Plan.Floor floor = lot.floors.get(k);
        String field = args[1].toLowerCase(Locale.ROOT);
        if (field.equals("panels")) {
            floor.panels = args[2].toLowerCase(Locale.ROOT);
            player.sendMessage(PREFIX + lot.name + " の " + (k + 1) + " 階の壁パネル: " + floor.panels + " (セット: " + String.join(", ", PlotMenu.panelSets()) + ")");
            return true;
        }
        PlotMenu.Field<Plan.Floor> f = PlotMenu.floorField(field);
        if (f == null) {
            player.sendMessage(PREFIX + "そんな項目はありません: " + field + "  項目: panels, " + floorFieldIds());
            return false;
        }
        String wrong = f.parse(floor, args[2]);
        if (wrong != null) {
            player.sendMessage(PREFIX + wrong);
            return false;
        }
        player.sendMessage(PREFIX + lot.name + " の " + (k + 1) + " 階の " + f.label() + ": " + f.show(floor));
        return true;
    }

    private static Plan.Lot find(List<Plan.Lot> lots, String name) {
        for (Plan.Lot lot : lots) if (lot.name.equalsIgnoreCase(name)) return lot;
        try {
            int index = Integer.parseInt(name) - 1;
            return index >= 0 && index < lots.size() ? lots.get(index) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Plan.Shape named(String word) {
        for (Plan.Shape shape : Plan.Shape.values()) if (shape.name().equalsIgnoreCase(word)) return shape;
        return Plan.Shape.RECT;
    }

    static String lotFieldIds() {
        List<String> ids = new ArrayList<>();
        for (PlotMenu.Field<Plan.Lot> field : PlotMenu.LOT_FIELDS) ids.add(field.id());
        return String.join(", ", ids);
    }

    static String floorFieldIds() {
        List<String> ids = new ArrayList<>();
        for (PlotMenu.Field<Plan.Floor> field : PlotMenu.FLOOR_FIELDS) ids.add(field.id());
        return String.join(", ", ids);
    }

    // ---- the kit ----------------------------------------------------------------------------------------------------------------

    static void kit(Player player, TreeSession session, ProceduralModule tool, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        KitStore store = tool.kits();
        Kit kit = PlotShape.kit();
        switch (sub) {
            case "list" -> {
                String filter = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                List<String> lines = new ArrayList<>();
                for (Kit.Part part : kit.all()) {
                    if (!filter.isEmpty() && !part.category.name().equalsIgnoreCase(filter) && !part.tags.contains(filter)) continue;
                    lines.add(part.id + (part.builtin ? "" : "*"));
                }
                player.sendMessage(PREFIX + "部品 (" + lines.size() + (filter.isEmpty() ? "" : ", " + filter) + "、* は登録したもの): " + String.join(", ", lines));
            }
            case "info" -> {
                Kit.Part part = part(player, kit, args);
                if (part == null) return;
                player.sendMessage(PREFIX + part.summary());
                if (!part.params.isEmpty()) player.sendMessage("  数字: " + part.params);
                if (part.kind != Kit.Kind.PROCEDURAL) {
                    player.sendMessage("  アンカー: " + (part.anchors.isEmpty() ? "なし" : part.anchors.keySet()) + " / 9スライス u " + Arrays.toString(part.sliceU) + " v "
                            + Arrays.toString(part.sliceV) + " / ブロック " + part.blocks.size());
                } else {
                    player.sendMessage("  埋め: " + part.fill.name().toLowerCase(Locale.ROOT) + " / 線 " + part.lines.size() + " / 開口 " + part.opening.name().toLowerCase(Locale.ROOT));
                }
            }
            case "pos1", "pos2" -> {
                Block block = player.getTargetBlockExact(96);
                if (block == null) {
                    player.sendMessage(PREFIX + "ブロックを見てください。");
                    return;
                }
                int[] at = {block.getX(), block.getY(), block.getZ()};
                if (session.kitWorld != null && !session.kitWorld.equals(block.getWorld())) {
                    session.kitFrom = null;
                    session.kitTo = null;
                }
                session.kitWorld = block.getWorld();
                if (sub.equals("pos1")) session.kitFrom = at;
                else session.kitTo = at;
                player.sendMessage(PREFIX + sub + ": " + at[0] + ", " + at[1] + ", " + at[2] + (session.kitFrom != null && session.kitTo != null ? "  (kit save <id> <カテゴリ> [タグ…] で登録)" : ""));
            }
            case "save" -> {
                if (args.length < 4 || session.kitFrom == null || session.kitTo == null) {
                    player.sendMessage(PREFIX + "/proceduraltool kit save <id> <カテゴリ> [タグ…]  (先に kit pos1 / pos2 で範囲を。正面はあなたの方)  カテゴリ: " + categories());
                    return;
                }
                String id = args[2].replaceAll("[^A-Za-z0-9_-]", "").isEmpty() ? "" : Kit.safeId(args[2]);
                Kit.Category category = category(args[3]);
                if (id.isEmpty() || category == null) {
                    player.sendMessage(PREFIX + (id.isEmpty() ? "id は英数字・-・_" : "カテゴリ: " + categories()));
                    return;
                }
                int volume = Math.abs(session.kitFrom[0] - session.kitTo[0]) + 1;
                volume *= Math.abs(session.kitFrom[1] - session.kitTo[1]) + 1;
                volume *= Math.abs(session.kitFrom[2] - session.kitTo[2]) + 1;
                if (volume > 4096) {
                    player.sendMessage(PREFIX + "範囲が大きすぎます (" + volume + " > 4096 ブロック)。");
                    return;
                }
                Kit.Part part = store.capture(player, session, tool, id, category, Arrays.asList(Arrays.copyOfRange(args, 4, args.length)));
                boolean saved = store.save(part);
                player.sendMessage(PREFIX + (saved ? "登録しました: " + part.summary() + "  役割のブロックは、パレットに合わせて色が変わります。" : "書き込めませんでした (ログを見てください)。"));
            }
            case "anchor" -> {
                Kit.Part part = part(player, kit, args);
                if (part == null) return;
                if (args.length < 4) {
                    player.sendMessage(PREFIX + "/proceduraltool kit anchor <id> <名前> (見ているブロックが取り付け点)");
                    return;
                }
                Block block = player.getTargetBlockExact(96);
                int[] local = block == null ? null : KitStore.local(part, block.getX(), block.getY(), block.getZ());
                if (local == null) {
                    player.sendMessage(PREFIX + "登録した部品の範囲のブロックを見てください (キットの部品にはアンカーを付けられません)。");
                    return;
                }
                part.anchors.put(Plan.safeName(args[3], "anchor"), local);
                store.save(part);
                player.sendMessage(PREFIX + "アンカー " + args[3] + ": " + local[0] + ", " + local[1] + ", " + local[2]);
            }
            case "slice" -> {
                Kit.Part part = part(player, kit, args);
                if (part == null) return;
                if (args.length < 6 || part.kind == Kit.Kind.PROCEDURAL) {
                    player.sendMessage(PREFIX + "/proceduraltool kit slice <id> <u|v> <から> <まで>  (登録した部品の、伸ばす列・段。-1 -1 で止める)");
                    return;
                }
                try {
                    int from = Integer.parseInt(args[4]);
                    int to = Integer.parseInt(args[5]);
                    int[] slice = args[3].equalsIgnoreCase("v") ? part.sliceV : part.sliceU;
                    slice[0] = from;
                    slice[1] = to;
                } catch (NumberFormatException exception) {
                    player.sendMessage(PREFIX + "から・まで は数字");
                    return;
                }
                boolean sliced = part.sliceU[0] >= 0 || part.sliceV[0] >= 0;
                part.kind = sliced ? Kit.Kind.SLICE : Kit.Kind.TEMPLATE;
                if (sliced) {
                    part.minW = part.sliceU[0] >= 0 ? part.sizeU - (part.sliceU[1] - part.sliceU[0] + 1) + 1 : part.sizeU;
                    part.maxW = part.sliceU[0] >= 0 ? 99 : part.sizeU;
                    part.minH = part.sliceV[0] >= 0 ? part.sizeV - (part.sliceV[1] - part.sliceV[0] + 1) + 1 : part.sizeV;
                    part.maxH = part.sliceV[0] >= 0 ? 99 : part.sizeV;
                }
                store.save(part);
                player.sendMessage(PREFIX + part.summary());
            }
            case "tag" -> {
                Kit.Part part = part(player, kit, args);
                if (part == null) return;
                for (int i = 3; i < args.length; i++) {
                    String tag = args[i].toLowerCase(Locale.ROOT);
                    if (tag.startsWith("-")) part.tags.remove(tag.substring(1));
                    else part.tags.add(tag.startsWith("+") ? tag.substring(1) : tag);
                }
                store.save(part);
                player.sendMessage(PREFIX + part.id + " のタグ: " + String.join(", ", part.tags));
            }
            case "set" -> {
                Kit.Part part = part(player, kit, args);
                if (part == null) return;
                if (args.length < 5) {
                    player.sendMessage(PREFIX + "/proceduraltool kit set <id> <数字の名前> <値>  (min-w max-w min-h max-h category も)  いま: " + part.params);
                    return;
                }
                String name = args[3].toLowerCase(Locale.ROOT);
                String value = args[4];
                try {
                    switch (name) {
                        case "min-w" -> part.minW = Integer.parseInt(value);
                        case "max-w" -> part.maxW = Integer.parseInt(value);
                        case "min-h" -> part.minH = Integer.parseInt(value);
                        case "max-h" -> part.maxH = Integer.parseInt(value);
                        case "category" -> {
                            Kit.Category category = category(value);
                            if (category == null) {
                                player.sendMessage(PREFIX + "カテゴリ: " + categories());
                                return;
                            }
                            part.category = category;
                        }
                        default -> {
                            Object number;
                            try {
                                number = value.contains(".") ? (Object) Double.parseDouble(value) : (Object) Integer.parseInt(value);
                            } catch (NumberFormatException exception) {
                                number = value;
                            }
                            part.params.put(name, number);
                        }
                    }
                } catch (NumberFormatException exception) {
                    player.sendMessage(PREFIX + name + " は数字");
                    return;
                }
                store.save(part);
                player.sendMessage(PREFIX + part.summary() + " / 数字: " + part.params);
            }
            case "preview" -> {
                Kit.Part part = part(player, kit, args);
                if (part == null) return;
                Block block = player.getTargetBlockExact(96);
                if (block == null) {
                    player.sendMessage(PREFIX + "置いてみる所のブロックを見てください。");
                    return;
                }
                int w = part.kind == Kit.Kind.TEMPLATE ? part.sizeU : Math.max(part.minW, 3);
                int h = part.kind == Kit.Kind.TEMPLATE ? part.sizeV : Math.max(part.minH, 4);
                try {
                    if (args.length > 3) w = Integer.parseInt(args[3]);
                    if (args.length > 4) h = Integer.parseInt(args[4]);
                } catch (NumberFormatException exception) {
                    player.sendMessage(PREFIX + "幅・高さは数字");
                    return;
                }
                if (!part.fits(w, h) && part.category != Kit.Category.GABLE) {
                    player.sendMessage(PREFIX + "この大きさには合いません (" + part.summary() + ")");
                    return;
                }
                store.preview(player, tool, part, Math.max(1, Math.min(32, w)), Math.max(1, Math.min(32, h)), block.getLocation(), 10);
                player.sendMessage(PREFIX + part.id + " を " + w + " x " + h + " で 10 秒見せます (あなたにだけ)。");
            }
            case "delete" -> {
                Kit.Part part = part(player, kit, args);
                if (part == null) return;
                boolean moved = store.delete(part.id);
                player.sendMessage(PREFIX + (moved ? "ごみ箱 (buildkit/trash/) へ移しました: " + part.id + (part.builtin ? " (キットの部品は、元の形で残ります)" : "") : "ファイルがありません: " + part.id));
            }
            case "reload" -> player.sendMessage(PREFIX + "部品を読み直しました: " + store.reload() + " ファイル");
            default -> player.sendMessage(PREFIX + "/proceduraltool kit [" + String.join("|", KIT_SUBS) + "]");
        }
        tool.menu().refresh(player);
    }

    private static Kit.Part part(Player player, Kit kit, String[] args) {
        Kit.Part part = args.length > 2 ? kit.get(Kit.safeId(args[2])) : null;
        if (part == null) player.sendMessage(PREFIX + "そんな部品はありません" + (args.length > 2 ? ": " + args[2] : "") + " (/proceduraltool kit list)");
        return part;
    }

    private static Kit.Category category(String word) {
        for (Kit.Category category : Kit.Category.values()) {
            if (category.name().equalsIgnoreCase(word.replace('-', '_')) || category.label.equals(word)) return category;
        }
        return null;
    }

    private static String categories() {
        List<String> names = new ArrayList<>();
        for (Kit.Category category : Kit.Category.values()) names.add(category.name().toLowerCase(Locale.ROOT) + " (" + category.label + ")");
        return String.join(", ", names);
    }

    // ---- completion -------------------------------------------------------------------------------------------------------------

    static List<String> complete(TreeSession session, String[] args) {
        List<String> options = new ArrayList<>();
        if (args[0].equalsIgnoreCase("lot")) {
            if (args.length == 2) {
                options.addAll(LOT_SUBS);
                for (Plan.Lot lot : session.settings.lots()) options.add(lot.name);
            } else if (args.length == 3 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("select"))) {
                for (Plan.Lot lot : session.settings.lots()) options.add(lot.name);
            } else if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
                for (Plan.Shape shape : Plan.Shape.values()) options.add(shape.name().toLowerCase(Locale.ROOT));
            } else if (args.length == 3) {
                for (PlotMenu.Field<Plan.Lot> field : PlotMenu.LOT_FIELDS) options.add(field.id());
                options.add("name");
                options.add("floor");
            } else if (args.length == 5 && args[2].equalsIgnoreCase("floor")) {
                options.add("panels");
                for (PlotMenu.Field<Plan.Floor> field : PlotMenu.FLOOR_FIELDS) options.add(field.id());
            } else if (args.length == 6 && args[2].equalsIgnoreCase("floor") && args[4].equalsIgnoreCase("panels")) {
                options.addAll(PlotMenu.panelSets());
            }
        } else if (args[0].equalsIgnoreCase("kit")) {
            if (args.length == 2) options.addAll(KIT_SUBS);
            else if (args.length == 3 && !args[1].equalsIgnoreCase("save") && !args[1].equalsIgnoreCase("pos1") && !args[1].equalsIgnoreCase("pos2")) {
                for (Kit.Part part : PlotShape.kit().all()) options.add(part.id);
            } else if (args.length == 4 && args[1].equalsIgnoreCase("save")) {
                for (Kit.Category category : Kit.Category.values()) options.add(category.name().toLowerCase(Locale.ROOT));
            } else if (args.length == 4 && args[1].equalsIgnoreCase("slice")) {
                options.addAll(List.of("u", "v"));
            }
        }
        return options;
    }
}
