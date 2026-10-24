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
    private static final List<String> SUBS = List.of("kind", "give", "off", "menu", "set", "info", "save", "load", "list", "delete", "undo", "redo", "preview", "generate");

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
                    player.sendMessage(PREFIX + "/proceduraltool kind <tree|rock|spike|crystal>  (いま: " + session.settings.kind().name().toLowerCase(Locale.ROOT) + ")");
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
            case "list" -> {
                List<String> names = tool.store().list();
                player.sendMessage(PREFIX + "保存した設定 (" + names.size() + "): " + (names.isEmpty() ? "なし" : String.join(", ", names)));
            }
            case "delete" -> {
                String name = args.length > 1 ? TreeSettings.safeName(args[1]) : null;
                if (name == null) player.sendMessage(PREFIX + "/proceduraltool delete <名前>");
                else player.sendMessage(PREFIX + (tool.store().delete(name) ? "消しました: " + name : "そんな名前はありません: " + name));
            }
            default -> player.sendMessage(PREFIX + "/proceduraltool [give|off|menu|set <項目> <値>|info|save <名前>|load <名前>|list|delete <名前>|undo|redo|preview|generate]");
        }
        return true;
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
