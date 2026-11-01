package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /proceduraltool}: the tool, and the settings by command (for exact values and for saving under a name). */
final class ProceduralCommand implements TabExecutor {

    private static final String PREFIX = "[木] ";
    private static final List<String> SUBS = List.of("kind", "give", "off", "menu", "set", "info", "save", "load", "list", "delete", "undo", "redo", "preview", "generate", "point", "block");

    private final ProceduralModule tool;

    ProceduralCommand(ProceduralModule tool) {
        this.tool = tool;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String sub = args.length == 0 ? "give" : args[0].toLowerCase(Locale.ROOT);
        TreeSession session = tool.session(player);
        switch (sub) {
            case "kind" -> {
                ProcKind kind = args.length > 1 ? ProcKind.byId(args[1]) : null;
                if (kind == null) {
                    player.sendMessage(PREFIX + "/proceduraltool kind <tree|rock|spike|crystal|web>  (いま: " + session.settings.kind().name().toLowerCase(Locale.ROOT) + ")");
                } else {
                    session.settings.setKind(kind);
                    tool.limit(session.settings);
                    tool.changed(player);
                    player.sendMessage(PREFIX + "作るもの: " + kind.label() + " (" + kind.description() + ")");
                }
            }
            case "give", "on" -> tool.give(player);
            case "off", "remove" -> tool.take(player);
            case "menu" -> {
                if (session.base == null) player.sendMessage(PREFIX + "置きたい場所のブロックを、ツールでクリックしてください。");
                else tool.menu().open(player);
            }
            case "generate" -> tool.generate(player);
            case "undo" -> tool.undo(player);
            case "redo" -> tool.redo(player);
            case "preview" -> tool.preview().toggle(player);
            case "info" -> info(player, session.settings);
            case "set" -> set(player, session.settings, args);
            case "save" -> save(player, session.settings, args);
            case "load" -> load(player, session, args);
            case "point" -> {
                String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
                switch (what) {
                    case "clear" -> tool.clearPoints(player);
                    case "undo" -> {
                        if (session.points.isEmpty()) {
                            player.sendMessage(PREFIX + "消す点がありません。");
                        } else {
                            session.points.remove(session.points.size() - 1);
                            tool.changed(player);
                            tool.markPoints(player, session);
                            player.sendMessage(PREFIX + "最後の点を消しました。残り " + session.points.size() + " 点。");
                        }
                    }
                    case "shape" -> {
                        String value = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                        if (!TreeSettings.WEB_SHAPES.contains(value)) {
                            player.sendMessage(PREFIX + "/proceduraltool point shape <" + String.join("|", TreeSettings.WEB_SHAPES) + ">  (いま: " + session.settings.webShape() + ")");
                        } else {
                            session.settings.setWebShape(value);
                            tool.changed(player);
                            player.sendMessage(PREFIX + "巣の形: " + ProceduralMenu.webShapeLabel(value));
                        }
                    }
                    default -> {
                        player.sendMessage(PREFIX + "巣の点 (" + session.points.size() + "): " + (session.points.isEmpty() ? "なし" : ""));
                        for (int index = 0; index < session.points.size(); index++) {
                            int[] point = session.points.get(index);
                            player.sendMessage("  " + (index + 1) + ": " + point[0] + ", " + point[1] + ", " + point[2]);
                        }
                        player.sendMessage(PREFIX + "/proceduraltool point <clear|undo|shape <形>>  (kind web にしてから、ツールでブロックをクリックして点を足す)");
                    }
                }
            }
            case "block" -> block(player, session.settings, args);
            case "list" -> {
                List<String> names = tool.store().list();
                player.sendMessage(PREFIX + "保存した設定 (" + names.size() + "): " + (names.isEmpty() ? "なし" : String.join(", ", names)));
            }
            case "delete" -> {
                String name = args.length > 1 ? TreeSettings.safeName(args[1]) : null;
                if (name == null) player.sendMessage(PREFIX + "/proceduraltool delete <名前>");
                else player.sendMessage(PREFIX + (tool.store().delete(name) ? "消しました: " + name : "そんな名前はありません: " + name));
            }
            default -> player.sendMessage(PREFIX + "/proceduraltool [give|off|menu|set <項目> <値>|info|save <名前>|load <名前>|list|delete <名前>|undo|redo|preview|generate|block <部位> <ブロック>]");
        }
        return true;
    }

    /**
     * {@code /proceduraltool block <part> <block|hand|reset>}: the block of a part; {@code block leaf <block,block,...>} the leaf gradient (bottom to top, one block is
     * one leaf block). Names a hand cannot hold (a potted fern) can be given this way.
     */
    private void block(Player player, TreeSettings settings, String[] args) {
        if (args.length < 3) {
            StringBuilder text = new StringBuilder(PREFIX + "/proceduraltool block <部位> <ブロック名|hand|reset>  (leaf はカンマ区切りで、葉のグラデーション: 下,…,上)  部位: ");
            for (TreePart part : settings.kind().parts()) {
                text.append(part.key()).append('=').append(tool.material(part, settings).getKey().getKey()).append(' ');
            }
            player.sendMessage(text.toString().trim());
            return;
        }
        TreePart part = null;
        for (TreePart candidate : TreePart.values()) if (candidate.shade() <= 0 && candidate.key().equalsIgnoreCase(args[1])) part = candidate;
        if (part == null) {
            player.sendMessage(PREFIX + "そんな部位はありません: " + args[1]);
            return;
        }
        String value = args[2];
        if (value.equalsIgnoreCase("hand")) value = player.getInventory().getItemInMainHand().getType().name();
        if (value.equalsIgnoreCase("reset")) {
            settings.setBlock(part, null);
            if (part == TreePart.LEAF) settings.setLeafGradient(List.of());
        } else if (part == TreePart.LEAF && value.contains(",")) {
            List<String> names = new ArrayList<>();
            for (String name : value.split(",")) {
                if (TreeSettings.blockName(name) == null) {
                    player.sendMessage(PREFIX + "置けるブロックではありません: " + name);
                    return;
                }
                names.add(name);
            }
            settings.setLeafGradient(names);
        } else {
            if (TreeSettings.blockName(value) == null) {
                player.sendMessage(PREFIX + "置けるブロックではありません: " + value);
                return;
            }
            settings.setBlock(part, value);
            if (part == TreePart.LEAF) settings.setLeafGradient(List.of());
        }
        tool.changed(player);
        player.sendMessage(PREFIX + part.label() + ": " + (part == TreePart.LEAF && !settings.leafGradient().isEmpty()
                ? String.join(" → ", settings.leafGradient()).toLowerCase(Locale.ROOT) + " (下 → 上)"
                : tool.material(part, settings).getKey().getKey()));
    }

    private void info(Player player, TreeSettings settings) {
        player.sendMessage(PREFIX + "設定 (" + settings.name() + "): 作るもの " + settings.kind().name().toLowerCase(Locale.ROOT) + " / アルゴリズム " + settings.algorithm() + " / 樹冠 " + settings.crown() + " / シード " + (settings.seed() == 0L ? "ランダム" : settings.seed()));
        for (TreeSettings.Key key : TreeSettings.Key.values()) {
            player.sendMessage("  " + key.id() + " = " + (key.whole() ? Integer.toString(settings.getInt(key)) : Double.toString(settings.get(key)))
                    + "  (" + key.min() + " 〜 " + key.max() + ") " + key.label());
        }
    }

    private void set(Player player, TreeSettings settings, String[] args) {
        if (args.length < 3) {
            player.sendMessage(PREFIX + "/proceduraltool set <項目> <値>  項目: algorithm, crown, seed, " + keyIds());
            return;
        }
        String item = args[1].toLowerCase(Locale.ROOT);
        String value = args[2].toLowerCase(Locale.ROOT);
        switch (item) {
            case "algorithm" -> {
                if (!TreeSettings.ALGORITHMS.contains(value)) {
                    player.sendMessage(PREFIX + "algorithm は " + String.join(" / ", TreeSettings.ALGORITHMS));
                    return;
                }
                settings.setAlgorithm(value);
            }
            case "crown" -> {
                if (!TreeSettings.CROWNS.contains(value)) {
                    player.sendMessage(PREFIX + "crown は " + String.join(" / ", TreeSettings.CROWNS));
                    return;
                }
                settings.setCrown(value);
            }
            case "seed" -> {
                try {
                    settings.setSeed(Long.parseLong(value));
                } catch (NumberFormatException exception) {
                    player.sendMessage(PREFIX + "seed は整数 (0 = 毎回ランダム)");
                    return;
                }
            }
            default -> {
                TreeSettings.Key key = TreeSettings.Key.byId(item);
                if (key == null) {
                    player.sendMessage(PREFIX + "そんな項目はありません。項目: algorithm, crown, seed, " + keyIds());
                    return;
                }
                try {
                    settings.set(key, Double.parseDouble(value));
                } catch (NumberFormatException exception) {
                    player.sendMessage(PREFIX + key.id() + " は数字で指定してください (" + key.min() + " 〜 " + key.max() + ")");
                    return;
                }
                tool.limit(settings);
            }
        }
        tool.changed(player);
        player.sendMessage(PREFIX + "設定しました: " + item + " = " + value);
    }

    private static String keyIds() {
        List<String> ids = new ArrayList<>();
        for (TreeSettings.Key key : TreeSettings.Key.values()) ids.add(key.id());
        return String.join(", ", ids);
    }

    private void save(Player player, TreeSettings settings, String[] args) {
        String name = TreeSettings.safeName(args.length > 1 ? args[1] : settings.name());
        if (name == null) {
            player.sendMessage(PREFIX + "名前は英小文字・数字・- _ の32文字まで: /proceduraltool save <名前>");
            return;
        }
        settings.setName(name);
        player.sendMessage(PREFIX + (tool.store().save(name, settings) ? "保存しました: " + name : "保存できませんでした (ログを見てください)。"));
    }

    private void load(Player player, TreeSession session, String[] args) {
        String name = args.length > 1 ? TreeSettings.safeName(args[1]) : null;
        if (name == null) {
            player.sendMessage(PREFIX + "/proceduraltool load <名前>  (/proceduraltool list で一覧)");
            return;
        }
        TreeSettings loaded = tool.store().load(name);
        if (loaded == null) {
            player.sendMessage(PREFIX + "そんな名前はありません: " + name);
            return;
        }
        tool.limit(loaded);
        session.settings = loaded;
        session.autoSeed = false;
        tool.changed(player);
        player.sendMessage(PREFIX + "読み込みました: " + name + " (/proceduraltool info で中身)");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> options = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            options.addAll(SUBS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            options.addAll(List.of("algorithm", "crown", "seed"));
            for (TreeSettings.Key key : TreeSettings.Key.values()) options.add(key.id());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("point")) {
            options.addAll(List.of("list", "clear", "undo", "shape"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("point") && args[1].equalsIgnoreCase("shape")) {
            options.addAll(TreeSettings.WEB_SHAPES);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("block")) {
            for (TreePart part : TreePart.values()) if (part.shade() <= 0) options.add(part.key());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("block")) {
            options.addAll(List.of("hand", "reset"));
            String typed = args[2].toUpperCase(Locale.ROOT);
            String head = typed.contains(",") ? typed.substring(0, typed.lastIndexOf(',') + 1) : "";
            String tail = typed.substring(head.length());
            int found = 0;
            for (org.bukkit.Material material : org.bukkit.Material.values()) {
                if (found >= 40 || material.isLegacy() || !material.isBlock() || material.isAir() || !material.name().startsWith(tail)) continue;
                options.add((head + material.name()).toLowerCase(Locale.ROOT));
                found++;
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("kind")) {
            for (ProcKind kind : ProcKind.values()) options.add(kind.name().toLowerCase(Locale.ROOT));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            if (args[1].equalsIgnoreCase("algorithm")) options.addAll(TreeSettings.ALGORITHMS);
            else if (args[1].equalsIgnoreCase("crown")) options.addAll(TreeSettings.CROWNS);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("load") || args[0].equalsIgnoreCase("delete"))) {
            options.addAll(tool.store().list());
        }
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(last)) result.add(option);
        return result;
    }
}
