package net.tkgon.mc.iruuRPG.model;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /iruumodel}: list the models, read them again and make the pack, look at a model and its animations, and take the whole feature out. */
final class ModelCommand implements TabExecutor {

    private static final String PREFIX = "[モデル] ";
    private static final List<String> SUBS = List.of("list", "reload", "build", "demo", "play", "clear", "uninstall");

    private final ModelModule module;
    private boolean confirmUninstall;

    ModelCommand(ModelModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("iruurpg.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        ModelRegistry registry = module.registry();
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                sender.sendMessage(PREFIX + registry.all().size() + " 個のモデル (" + module.active() + " 体に着せています)");
                for (BbModel model : registry.all().values()) {
                    sender.sendMessage("  " + model.id + ": 部品 " + model.parts.size() + " / アニメーション " + String.join(", ", model.animations.keySet()));
                }
                sender.sendMessage(PREFIX + "パック: " + registry.packFile().getName() + (registry.packFile().isFile() ? " (" + registry.packFile().length() + " バイト)" : " (まだありません: /iruumodel build)"));
            }
            case "reload", "build" -> {
                registry.reload(true);
                sender.sendMessage(PREFIX + registry.all().size() + " 個のモデルを読み、パックを作りました。配り直すには /iruurpg pack send");
                for (String problem : registry.problems()) sender.sendMessage("  §e! " + problem);
            }
            case "demo" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                String id = args.length > 1 ? args[1] : "";
                double scale = args.length > 2 ? parse(args[2]) : 1.0;
                sender.sendMessage(PREFIX + (module.demo(player, id, scale) ? "モデル " + id + " を、いる場所に出しました (/iruumodel play <アニメーション> / clear)。パックを入れていないと、見えません。" : "そんなモデルはありません: " + id));
            }
            case "play" -> {
                String animation = args.length > 1 ? args[1] : "";
                int count = module.playDemos(animation);
                sender.sendMessage(PREFIX + (count > 0 ? animation + " を再生しました" : "再生できませんでした (demo を出してから。アニメーション名を確かめてください)"));
            }
            case "clear" -> {
                module.removeDemos();
                sender.sendMessage(PREFIX + "demo を消しました。");
            }
            case "uninstall" -> {
                if (!confirmUninstall || args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    confirmUninstall = true;
                    sender.sendMessage(PREFIX + "モデル機能の、ディスプレイ・モデルのファイル(models/)・パック(iruumodel.zip)をすべて消します。敵の model: の記述は消えません(無視されます)。本当に消すなら、もう一度 /iruumodel uninstall confirm");
                    return true;
                }
                confirmUninstall = false;
                int removed = module.uninstall();
                sender.sendMessage(PREFIX + "消しました (" + removed + " 個)。コードを外すには docs/models.md の「消すには」を見てください。");
            }
            default -> sender.sendMessage(PREFIX + "/iruumodel <" + String.join("|", SUBS) + ">");
        }
        return true;
    }

    private static double parse(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            return 1.0;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.addAll(SUBS);
        else if (args.length == 2 && args[0].equalsIgnoreCase("demo")) options.addAll(module.registry().all().keySet());
        else if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
            for (BbModel model : module.registry().all().values()) options.addAll(model.animations.keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("uninstall")) options.add("confirm");
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(last)) result.add(option);
        return result;
    }
}
