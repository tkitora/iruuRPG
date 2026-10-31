package net.tkgon.mc.iruuRPG.questline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.LineState;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * /questadmin (iruurpg.admin): looks at and changes the quest lines of an online player — info, start, step, flag,
 * pause / resume, reset (with the lines that depend on it, after a confirm), talk / event (to try a line without its NPC
 * or its event), reload and the warnings of the last load.
 */
public final class QuestAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("info", "start", "step", "flag", "pause", "resume", "reset", "talk", "event", "cutscene", "reload", "warnings");

    private final QuestlineService service;
    private net.tkgon.mc.iruuRPG.cutscene.CutsceneService cutscenes;

    public void setCutscenes(net.tkgon.mc.iruuRPG.cutscene.CutsceneService cutscenes) {
        this.cutscenes = cutscenes;
    }

    public QuestAdminCommand(QuestlineService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("iruurpg.admin")) {
            sender.sendMessage(Component.text("[クエスト] 権限がありません。", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            int count = service.reload();
            sender.sendMessage(Component.text("[クエスト] " + count + " 本を読み込みました (警告 " + service.lastWarnings().size() + " 件)。", NamedTextColor.GREEN));
            return true;
        }
        if (sub.equals("warnings")) {
            if (service.lastWarnings().isEmpty()) sender.sendMessage(Component.text("[クエスト] 警告はありません。", NamedTextColor.GREEN));
            for (String warning : service.lastWarnings()) sender.sendMessage(Component.text(warning, NamedTextColor.YELLOW));
            return true;
        }
        if (args.length < 2) {
            usage(sender);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("[クエスト] オンラインのプレイヤー " + args[1] + " が見つかりません。", NamedTextColor.RED));
            return true;
        }
        QuestlineEngine engine = service.engine();
        switch (sub) {
            case "info" -> info(sender, target, args.length > 2 ? args[2] : null);
            case "start" -> {
                if (args.length < 3 || engine.line(args[2]) == null) {
                    sender.sendMessage(Component.text("[クエスト] そのクエストラインはありません。", NamedTextColor.RED));
                    return true;
                }
                boolean force = args.length > 3 && args[3].equalsIgnoreCase("force");
                boolean started = service.start(target, args[2], force);
                sender.sendMessage(Component.text(started ? "[クエスト] 開始しました。" : "[クエスト] 開始できません (進行中・条件を満たさない・繰り返し不可)。末尾に force で条件を無視します。",
                        started ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "step" -> {
                if (args.length < 4) {
                    usage(sender);
                    return true;
                }
                boolean done = service.setStep(target, args[2], args[3]);
                sender.sendMessage(Component.text(done ? "[クエスト] ステップを移しました。" : "[クエスト] そのクエストライン・ステップはありません。",
                        done ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case "flag" -> flag(sender, target, args);
            case "pause", "resume" -> {
                if (args.length < 3) {
                    usage(sender);
                    return true;
                }
                boolean done = sub.equals("pause") ? service.pause(target, args[2]) : service.resume(target, args[2]);
                sender.sendMessage(Component.text(done ? "[クエスト] " + (sub.equals("pause") ? "中断しました。" : "再開しました。") : "[クエスト] その状態ではありません。",
                        done ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "reset" -> reset(sender, target, args);
            case "cutscene" -> {
                if (args.length < 3 || cutscenes == null) {
                    usage(sender);
                    return true;
                }
                if (args[2].equalsIgnoreCase("stop")) {
                    cutscenes.stop(target, true);
                    return true;
                }
                if (!cutscenes.exists(args[2])) {
                    sender.sendMessage(Component.text("[クエスト] cutscenes/" + args[2] + ".yml がありません。", NamedTextColor.RED));
                    return true;
                }
                cutscenes.play(target, args[2], done -> sender.sendMessage(Component.text("[クエスト] カットシーン " + args[2]
                        + (done ? " が終わりました。" : " は途中で終わりました。"), NamedTextColor.GRAY)));
            }
            case "talk" -> {
                if (args.length < 3) {
                    usage(sender);
                    return true;
                }
                service.talk(target, args[2]);
                sender.sendMessage(Component.text("[クエスト] " + args[2] + " に話しかけたことにしました。", NamedTextColor.GREEN));
            }
            case "event" -> {
                if (args.length < 3) {
                    usage(sender);
                    return true;
                }
                Map<String, Object> eventArgs = new java.util.HashMap<>();
                for (int index = 3; index < args.length; index++) {
                    int eq = args[index].indexOf('=');
                    if (eq > 0) eventArgs.put(args[index].substring(0, eq), parse(args[index].substring(eq + 1)));
                }
                if (args[2].equalsIgnoreCase("area-enter")) eventArgs.put("area", QuestEvent.areaKey(eventArgs));
                service.fire(target, new QuestEvent(args[2], eventArgs));
                sender.sendMessage(Component.text("[クエスト] イベント " + args[2] + " " + eventArgs + " を起こしました。", NamedTextColor.GREEN));
            }
            default -> usage(sender);
        }
        return true;
    }

    private void info(CommandSender sender, Player target, String only) {
        QuestlineData data = service.dataOf(target);
        QuestlineEngine engine = service.engine();
        sender.sendMessage(Component.text("[クエスト] " + target.getName() + " のクエストライン", NamedTextColor.GOLD));
        if (data.lines().isEmpty()) sender.sendMessage(Component.text("  (なし)", NamedTextColor.GRAY));
        for (var entry : data.lines().entrySet()) {
            if (only != null && !only.equals(entry.getKey())) continue;
            LineState state = entry.getValue();
            Questline line = engine.line(entry.getKey());
            String progress = engine.progress(data, entry.getKey());
            sender.sendMessage(Component.text("  " + entry.getKey() + (line == null ? " (読み込まれていない)" : " 「" + line.name() + "」"), NamedTextColor.AQUA)
                    .append(Component.text(" " + state.status.key() + " › " + state.step + (progress == null ? "" : " (" + progress + ")")
                            + (state.runs > 0 ? " 完了" + state.runs + "回" : ""), NamedTextColor.WHITE)));
            if (!state.flags.isEmpty()) sender.sendMessage(Component.text("    flags " + state.flags, NamedTextColor.GRAY));
        }
        if (!data.flags().isEmpty() && only == null) sender.sendMessage(Component.text("  共通のフラグ " + data.flags(), NamedTextColor.GRAY));
    }

    /** flag &lt;player&gt; [line.]&lt;flag&gt; &lt;value | clear&gt;: a line's own flag is written as line.flag. */
    private void flag(CommandSender sender, Player target, String[] args) {
        if (args.length < 4) {
            usage(sender);
            return;
        }
        String name = args[2];
        String lineId = null;
        int dot = name.indexOf('.');
        if (dot > 0) {
            lineId = name.substring(0, dot);
            name = name.substring(dot + 1);
            if (service.engine().line(lineId) == null) {
                sender.sendMessage(Component.text("[クエスト] クエストライン " + lineId + " はありません。", NamedTextColor.RED));
                return;
            }
        }
        Object value = args[3].equalsIgnoreCase("clear") ? null : parse(args[3]);
        boolean done = service.writeFlag(target, lineId, name, value);
        sender.sendMessage(Component.text(done ? "[クエスト] フラグを書きました。" : "[クエスト] そのフラグは書けません (クエストラインが始まっていない?)。",
                done ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }

    /**
     * reset &lt;player&gt; &lt;line | all&gt; [confirm]: a line is reset with every line that depends on it (so nothing is left
     * ahead of what it needs); the list is shown first and the reset happens with confirm. Rewards are not taken back.
     */
    private void reset(CommandSender sender, Player target, String[] args) {
        if (args.length < 3) {
            usage(sender);
            return;
        }
        boolean confirm = args.length > 3 && args[3].equalsIgnoreCase("confirm");
        if (args[2].equalsIgnoreCase("all")) {
            if (!confirm) {
                sender.sendMessage(Component.text("[クエスト] " + target.getName() + " の全クエストラインと共通のフラグを消します。/questadmin reset "
                        + target.getName() + " all confirm で実行します。", NamedTextColor.YELLOW));
                return;
            }
            service.reset(target, List.of(), true);
            sender.sendMessage(Component.text("[クエスト] 全部リセットしました (報酬は取り戻していません)。", NamedTextColor.GREEN));
            return;
        }
        String id = args[2];
        Set<String> ids = new LinkedHashSet<>();
        ids.add(id);
        ids.addAll(service.engine().dependents(id));
        QuestlineData data = service.dataOf(target);
        ids.removeIf(each -> data.line(each) == null);
        if (ids.isEmpty()) {
            sender.sendMessage(Component.text("[クエスト] リセットするものがありません。", NamedTextColor.YELLOW));
            return;
        }
        if (!confirm) {
            sender.sendMessage(Component.text("[クエスト] 次をリセットします: " + String.join(", ", ids) + " (依存しているものを含む)。共通のフラグは残ります。/questadmin reset "
                    + target.getName() + " " + id + " confirm で実行します。", NamedTextColor.YELLOW));
            return;
        }
        service.reset(target, ids, false);
        sender.sendMessage(Component.text("[クエスト] リセットしました: " + String.join(", ", ids) + " (報酬は取り戻していません)。", NamedTextColor.GREEN));
    }

    private static Object parse(String text) {
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) return Boolean.parseBoolean(text);
        try {
            double number = Double.parseDouble(text);
            return number == Math.rint(number) ? (Object) (long) number : (Object) number;
        } catch (NumberFormatException e) {
            return text;
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("""
                /questadmin info <player> [line]
                /questadmin start <player> <line> [force]
                /questadmin step <player> <line> <step>
                /questadmin flag <player> [line.]<flag> <value|clear>
                /questadmin pause|resume <player> <line>
                /questadmin reset <player> <line|all> [confirm]
                /questadmin talk <player> <npc>
                /questadmin event <player> <event> [key=value ...]
                /questadmin cutscene <player> <id|stop>
                /questadmin reload | warnings""", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("iruurpg.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("warnings")) {
            for (Player player : Bukkit.getOnlinePlayers()) options.add(player.getName());
        } else if (args.length == 3) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "info", "start", "step", "pause", "resume" -> options.addAll(service.engine().lines().keySet());
                case "reset" -> {
                    options.add("all");
                    options.addAll(service.engine().lines().keySet());
                }
                case "event" -> options.addAll(QuestEvent.KNOWN.keySet());
                case "flag" -> {
                    options.addAll(service.engine().book().flags().keySet());
                    for (Questline line : service.engine().lines().values()) {
                        for (String flag : line.flags().keySet()) options.add(line.id() + "." + flag);
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 4) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "step" -> {
                    Questline line = service.engine().line(args[2]);
                    if (line != null) options.addAll(line.steps().keySet());
                }
                case "start" -> options.add("force");
                case "reset" -> options.add("confirm");
                case "flag" -> options.addAll(List.of("true", "false", "clear"));
                default -> {
                }
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
