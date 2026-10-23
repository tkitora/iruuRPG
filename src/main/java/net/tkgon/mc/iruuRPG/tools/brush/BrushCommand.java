package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /ibrush}: gives the brush, opens its menu, sets its values by command, handles presets and the blacklist, and explains itself ({@code /ibrush help}). */
final class BrushCommand implements TabExecutor {

    private static final String PREFIX = BrushModule.PREFIX;
    private static final List<String> SUBS = List.of("help", "give", "menu", "off", "undo", "redo", "use", "peek", "preview", "set", "preset", "blacklist", "select");
    private static final List<String> SET_KEYS = List.of("mode", "shape", "radius", "strength", "falloff", "surface", "pattern", "from", "to", "steps", "axis", "additive", "toon", "depth", "sun", "light", "ao", "curvature", "shadenoise", "cell");

    private final BrushModule module;

    BrushCommand(BrushModule module) {
        this.module = module;
    }

    /** The help: every command with its arguments. */
    static final List<String> HELP = List.of(
            "/ibrush help [コマンド名]         この一覧。コマンド名を付けるとその使い方",
            "/ibrush give                     ブラシのアイテムを受け取る",
            "/ibrush off                      ブラシのアイテムを片づけ、プレビューも消す",
            "/ibrush menu [main|pattern|mask|sun|blacklist]   設定画面を開く (ページ指定可)",
            "/ibrush use                      見ている所にいま使う",
            "/ibrush peek                     見ている所に使った結果を1回だけ見る (あなたにだけ)",
            "/ibrush preview                  追従プレビューのオン/オフ (設定や見る所を変えると更新)",
            "/ibrush undo | redo              直前の編集の取り消し/やり直し (//undo, //redo と同じ)",
            "/ibrush set <項目> <値>           設定を数値や名前で直接変える (項目は /ibrush help set)",
            "/ibrush preset list|apply <名前>|save <名前>   グラデーションのプリセット",
            "/ibrush blacklist list|add [ブロック]|remove <ブロック>|save   グラデーション・自動陰影で使わないブロック",
            "/ibrush select give|fuzzy|adjacent|invert|clear|show|blend|shade|threshold <0〜100>|radius <1〜64>|info   選択ツール (help select で詳しく)");

    private static final List<String> HELP_SET = List.of(
            "/ibrush set mode <paint|shade>            塗る / 自動陰影",
            "/ibrush set shape <形>                    形 (" + shapeNames() + ")",
            "/ibrush set radius <1〜最大>              半径",
            "/ibrush set strength <10〜100>            強さ (%)",
            "/ibrush set falloff <0〜100>              縁のぼかし (%)",
            "/ibrush set surface <on|off>              表面だけ",
            "/ibrush set pattern <random|gradient>     模様の作り方",
            "/ibrush set from <ブロック> / to <ブロック>   グラデーションの両端 (省略すると手に持つブロック)",
            "/ibrush set steps <0〜14>                 グラデーションの間のブロック数",
            "/ibrush set axis <radial|vertical|directional|noise>   グラデーションの向き",
            "/ibrush set additive <on|off>             加算 (上乗せ)",
            "/ibrush set toon <0|2〜6>                 トゥーン調の段数 (0 でオフ)",
            "/ibrush set depth <10〜80>                陰の濃さ (%)",
            "/ibrush set sun <方位> <高さ>              太陽 (0=北 90=東、高さ 5〜90)",
            "/ibrush set light <on|off>                光(太陽)の影",
            "/ibrush set ao <on|off|10〜100>           アンビエント(AO)。数字は強さ(%)で、オンにもなる",
            "/ibrush set curvature <on|off|convex|concave|10〜100>   曲率 (convex=出っぱりを暗く / concave=凹みを暗く / 数字=強さ)",
            "/ibrush set shadenoise <on|off|10〜100>   ノイズの影",
            "/ibrush set cell <on|off|edges|cells|10〜100>   セルの影 (edges=境目 / cells=セルごと / 数字=強さ)");

    private static final List<String> HELP_SELECT = List.of(
            "/ibrush select give                選択ツールを受け取る (右クリック=選ぶ / Shift+右=足す / 左=設定画面)",
            "/ibrush select fuzzy | adjacent    選び方: 似た色 / 同じブロックでつながった所",
            "/ibrush select threshold <0〜100>  ファジーのしきい値 (大きいほど色が離れていても選ぶ)",
            "/ibrush select radius <1〜64>      クリックした所から探す半径",
            "/ibrush select invert              選択を反転 (ブラシは外側だけに効く。計算は増えない)",
            "/ibrush select clear               選択を解除",
            "/ibrush select show                選択の縁の表示のオン/オフ",
            "/ibrush select blend               選択範囲の色をぼかす (//undo で戻せる)",
            "/ibrush select shade               選択範囲に自動陰影 (光・AO・曲率・ノイズ・セル・トゥーンの設定で) を適用する",
            "/ibrush select limit <on|off>      ブラシを選択範囲に限るか",
            "/ibrush select info                いまの選択の大きさと設定");

    private void select(Player player, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "info";
        SelectionTool tool = module.selection();
        SelectionTool.State state = tool.state(player);
        try {
            switch (action) {
                case "give" -> tool.give(player);
                case "fuzzy" -> {
                    state.mode = SelectionTool.Mode.FUZZY;
                    player.sendMessage(PREFIX + "選び方: ファジー (似た色)");
                }
                case "adjacent" -> {
                    state.mode = SelectionTool.Mode.ADJACENT;
                    player.sendMessage(PREFIX + "選び方: 隣接 (同じブロック)");
                }
                case "threshold" -> {
                    state.threshold = Math.max(0, Math.min(100, Integer.parseInt(args[2])));
                    player.sendMessage(PREFIX + "しきい値: " + state.threshold);
                }
                case "radius" -> {
                    state.radius = Math.max(1, Math.min(tool.maxRadius(), Integer.parseInt(args[2])));
                    player.sendMessage(PREFIX + "半径: " + state.radius);
                }
                case "invert" -> tool.invert(player);
                case "clear" -> tool.clear(player);
                case "show" -> {
                    tool.toggleShow(player);
                    player.sendMessage(PREFIX + "選択の表示: " + (state.show ? "オン" : "オフ"));
                }
                case "blend" -> tool.blend(player);
                case "shade" -> tool.shade(player);
                case "limit" -> {
                    Boolean on = flag(args[2]);
                    if (on == null) throw new NumberFormatException();
                    state.limitBrush = on;
                    player.sendMessage(PREFIX + "ブラシを選択に限る: " + (on ? "オン" : "オフ"));
                }
                default -> player.sendMessage(PREFIX + state.mode.label + " / しきい値 " + state.threshold + " / 半径 " + state.radius + " / "
                        + (state.selection == null ? "選択なし" : state.selection.blocks.size() + "ブロック" + (state.selection.inverted ? " (反転中)" : "")));
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException exception) {
            help(player, new String[]{"help", "select"});
        }
    }

    private static String shapeNames() {
        List<String> names = new ArrayList<>();
        for (BrushShape shape : BrushShape.values()) names.add(shape.name().toLowerCase(Locale.ROOT));
        return String.join("|", names);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "give" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> module.give(player);
            case "menu" -> menu(player, args);
            case "off" -> {
                module.livePreview().off(player, false);
                module.take(player);
            }
            case "undo" -> module.undo(player);
            case "redo" -> module.redo(player);
            case "use" -> module.use(player, false);
            case "peek" -> module.use(player, true);
            case "preview" -> module.livePreview().toggle(player);
            case "set" -> set(player, args);
            case "preset" -> preset(player, args);
            case "blacklist" -> blacklist(player, args);
            case "select" -> select(player, args);
            default -> help(player, args);
        }
        return true;
    }

    private void help(Player player, String[] args) {
        String topic = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        List<String> lines = new ArrayList<>();
        if (topic.equals("set")) {
            lines.addAll(HELP_SET);
        } else if (topic.equals("select")) {
            lines.addAll(HELP_SELECT);
        } else if (topic.isEmpty() || topic.equals("help")) {
            lines.addAll(HELP);
            lines.add("ブラシを持って: 右クリック=使う / Shift+右クリック=1回だけプレビュー / 左クリック=設定画面");
        } else {
            for (String line : HELP) if (line.startsWith("/ibrush " + topic)) lines.add(line);
            if (lines.isEmpty()) lines.add("そんなコマンドはありません: " + topic + "  (/ibrush help で一覧)");
        }
        player.sendMessage(PREFIX + "使い方:");
        for (String line : lines) player.sendMessage("  " + line);
    }

    private void menu(Player player, String[] args) {
        BrushMenu.Page page = BrushMenu.Page.MAIN;
        if (args.length > 1) {
            for (BrushMenu.Page candidate : BrushMenu.Page.values()) if (candidate.name().equalsIgnoreCase(args[1])) page = candidate;
        }
        module.menuOpen(player, page);
    }

    private static Boolean flag(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "on", "true", "オン", "1" -> true;
            case "off", "false", "オフ", "0" -> false;
            default -> null;
        };
    }

    /** "on" / "off" as a flag; anything else is a mistake. */
    private static boolean onOff(String text) {
        Boolean on = flag(text);
        if (on == null) throw new NumberFormatException();
        return on;
    }

    /** A number 10 - 100 as a share 0.1 - 1 (null when it is not a number). */
    private static Double percent(String text) {
        try {
            return Math.max(0.1, Math.min(1.0, Double.parseDouble(text) / 100.0));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Material block(Player player, String[] args, int index) {
        Material material = args.length > index ? Material.matchMaterial(args[index]) : player.getInventory().getItemInMainHand().getType();
        return material != null && material.isBlock() && material.isItem() && !material.isAir() ? material : null;
    }

    private void set(Player player, String[] args) {
        if (args.length < 2) {
            help(player, new String[]{"help", "set"});
            return;
        }
        BrushSettings s = module.settings(player);
        String key = args[1].toLowerCase(Locale.ROOT);
        String value = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        try {
            switch (key) {
                case "mode" -> s.mode = value.startsWith("s") ? BrushMode.AUTO_SHADE : BrushMode.PAINT;
                case "shape" -> {
                    BrushShape shape = null;
                    for (BrushShape candidate : BrushShape.values()) if (candidate.name().equalsIgnoreCase(value)) shape = candidate;
                    if (shape == null) {
                        player.sendMessage(PREFIX + "形は " + shapeNames());
                        return;
                    }
                    s.shape = shape;
                }
                case "radius" -> s.radius = Math.max(1, Math.min(module.maxRadius(), Integer.parseInt(value)));
                case "strength" -> s.strength = Math.max(0.1, Math.min(1.0, Double.parseDouble(value) / 100.0));
                case "falloff" -> s.falloff = Math.max(0.0, Math.min(1.0, Double.parseDouble(value) / 100.0));
                case "surface" -> {
                    Boolean on = flag(value);
                    if (on == null) throw new NumberFormatException();
                    s.surfaceOnly = on;
                }
                case "pattern" -> s.patternMode = value.startsWith("g") ? BrushSettings.PatternMode.GRADIENT : BrushSettings.PatternMode.RANDOM;
                case "from", "to" -> {
                    Material material = block(player, args, 2);
                    if (material == null) {
                        player.sendMessage(PREFIX + "ブロックを指定するか、手に持ってください。");
                        return;
                    }
                    if (key.equals("from")) s.gradientFrom = material;
                    else s.gradientTo = material;
                    s.patternMode = BrushSettings.PatternMode.GRADIENT;
                }
                case "steps" -> s.gradientSteps = Math.max(0, Math.min(14, Integer.parseInt(value)));
                case "axis" -> {
                    BrushSettings.GradientAxis axis = null;
                    for (BrushSettings.GradientAxis candidate : BrushSettings.GradientAxis.values()) if (candidate.name().equalsIgnoreCase(value)) axis = candidate;
                    if (axis == null) throw new NumberFormatException();
                    s.gradientAxis = axis;
                }
                case "additive" -> {
                    Boolean on = flag(value);
                    if (on == null) throw new NumberFormatException();
                    s.additive = on;
                }
                case "toon" -> {
                    int steps = Integer.parseInt(value);
                    s.toonSteps = steps < 2 ? 0 : Math.min(6, steps);
                }
                case "light" -> s.lightOn = onOff(value);
                case "ao" -> {
                    Double strength = percent(value);
                    s.ambientOn = strength != null ? true : onOff(value);
                    if (strength != null) s.ambientStrength = strength;
                }
                case "curvature" -> {
                    Double strength = percent(value);
                    if (value.equals("convex")) s.curvatureConvex = true;
                    if (value.equals("concave")) s.curvatureConvex = false;
                    if (value.equals("convex") || value.equals("concave")) s.curvatureOn = true;
                    else if (strength != null) {
                        s.curvatureOn = true;
                        s.curvatureStrength = strength;
                    } else s.curvatureOn = onOff(value);
                }
                case "shadenoise" -> {
                    Double strength = percent(value);
                    s.noiseShadeOn = strength != null ? true : onOff(value);
                    if (strength != null) s.noiseShadeStrength = strength;
                }
                case "cell" -> {
                    Double strength = percent(value);
                    if (value.equals("edges") || value.equals("cells")) {
                        s.cellEdges = value.equals("edges");
                        s.cellOn = true;
                    } else if (strength != null) {
                        s.cellOn = true;
                        s.cellStrength = strength;
                    } else s.cellOn = onOff(value);
                }
                case "depth" -> s.shadeDepth = Math.max(0.1, Math.min(0.8, Double.parseDouble(value) / 100.0));
                case "sun" -> {
                    s.sunAzimuth = Math.floorMod(Integer.parseInt(value), 360);
                    if (args.length > 3) s.sunElevation = Math.max(5, Math.min(90, Integer.parseInt(args[3])));
                }
                default -> {
                    player.sendMessage(PREFIX + "そんな項目はありません: " + key + "  (/ibrush help set)");
                    return;
                }
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(PREFIX + "値が正しくありません: " + key + " " + value + "  (/ibrush help set)");
            return;
        }
        module.changed(player);
        player.sendMessage(PREFIX + "設定しました: " + key + " " + value);
    }

    private void preset(Player player, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        BrushStore store = module.store();
        BrushSettings s = module.settings(player);
        switch (action) {
            case "list" -> {
                player.sendMessage(PREFIX + "グラデーションのプリセット:");
                for (Gradients.Preset preset : store.allPresets()) {
                    player.sendMessage("  " + preset.name() + "  (" + preset.from().name().toLowerCase(Locale.ROOT) + " → " + preset.to().name().toLowerCase(Locale.ROOT) + ", 間 " + preset.steps() + ")");
                }
            }
            case "apply" -> {
                String name = args.length > 2 ? args[2] : "";
                for (Gradients.Preset preset : store.allPresets()) {
                    if (!preset.name().equalsIgnoreCase(name)) continue;
                    s.patternMode = BrushSettings.PatternMode.GRADIENT;
                    s.gradientFrom = preset.from();
                    s.gradientTo = preset.to();
                    s.gradientSteps = preset.steps();
                    module.changed(player);
                    player.sendMessage(PREFIX + "プリセットを使います: " + preset.name());
                    return;
                }
                player.sendMessage(PREFIX + "そんなプリセットはありません: " + name + "  (/ibrush preset list)");
            }
            case "save" -> {
                String name = BrushStore.safeName(args.length > 2 ? args[2] : null);
                if (name == null) {
                    player.sendMessage(PREFIX + "名前は英小文字・数字・- _ の32文字まで: /ibrush preset save <名前>");
                    return;
                }
                store.addPreset(name, s.gradientFrom, s.gradientTo, s.gradientSteps);
                player.sendMessage(PREFIX + (store.save() ? "保存しました: " + name : "保存できませんでした (ログを見てください)。"));
            }
            default -> help(player, new String[]{"help", "preset"});
        }
    }

    private void blacklist(Player player, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        BrushStore store = module.store();
        switch (action) {
            case "list" -> {
                List<String> names = new ArrayList<>();
                for (Material material : store.blacklist()) names.add(material.name().toLowerCase(Locale.ROOT));
                player.sendMessage(PREFIX + "ブラックリスト (" + names.size() + "): " + (names.isEmpty() ? "なし" : String.join(", ", names)));
            }
            case "add" -> {
                Material material = block(player, args, 2);
                if (material == null) {
                    player.sendMessage(PREFIX + "ブロックを指定するか、手に持ってください。");
                    return;
                }
                store.blacklist().add(material);
                module.changed(player);
                player.sendMessage(PREFIX + "足しました: " + material.name().toLowerCase(Locale.ROOT) + " (/ibrush blacklist save で保存)");
            }
            case "remove" -> {
                Material material = args.length > 2 ? Material.matchMaterial(args[2]) : null;
                if (material == null || !store.blacklist().remove(material)) {
                    player.sendMessage(PREFIX + "リストにありません。");
                    return;
                }
                module.changed(player);
                player.sendMessage(PREFIX + "取り除きました: " + material.name().toLowerCase(Locale.ROOT) + " (/ibrush blacklist save で保存)");
            }
            case "save" -> player.sendMessage(PREFIX + (store.save() ? "保存しました (" + store.blacklist().size() + "件)。" : "保存できませんでした (ログを見てください)。"));
            default -> help(player, new String[]{"help", "blacklist"});
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> options = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            options.addAll(SUBS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "help" -> {
                    options.addAll(SUBS);
                }
                case "set" -> options.addAll(SET_KEYS);
                case "menu" -> {
                    for (BrushMenu.Page page : BrushMenu.Page.values()) options.add(page.name().toLowerCase(Locale.ROOT));
                }
                case "preset" -> options.addAll(List.of("list", "apply", "save"));
                case "blacklist" -> options.addAll(List.of("list", "add", "remove", "save"));
                case "select" -> options.addAll(List.of("give", "fuzzy", "adjacent", "threshold", "radius", "invert", "clear", "show", "blend", "shade", "limit", "info"));
                default -> {
                }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String key = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("set")) {
                switch (key) {
                    case "mode" -> options.addAll(List.of("paint", "shade"));
                    case "shape" -> {
                        for (BrushShape shape : BrushShape.values()) options.add(shape.name().toLowerCase(Locale.ROOT));
                    }
                    case "surface", "additive" -> options.addAll(List.of("on", "off"));
                    case "pattern" -> options.addAll(List.of("random", "gradient"));
                    case "axis" -> {
                        for (BrushSettings.GradientAxis axis : BrushSettings.GradientAxis.values()) options.add(axis.name().toLowerCase(Locale.ROOT));
                    }
                    default -> {
                    }
                }
            } else if (sub.equals("preset") && key.equals("apply")) {
                for (Gradients.Preset preset : module.store().allPresets()) options.add(preset.name());
            } else if (sub.equals("blacklist") && key.equals("remove")) {
                for (Material material : module.store().blacklist()) options.add(material.name().toLowerCase(Locale.ROOT));
            }
        }
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(last)) result.add(option);
        return result;
    }
}
