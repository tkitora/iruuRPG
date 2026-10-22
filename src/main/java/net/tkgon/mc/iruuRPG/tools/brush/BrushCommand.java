package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /ibrush}: gives the brush, opens its menu, puts it away, or undoes the last stroke. */
final class BrushCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("give", "menu", "off", "undo", "use", "preview");

    private final BrushModule module;

    BrushCommand(BrushModule module) {
        this.module = module;
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
            case "menu" -> module.menuOpen(player);
            case "off" -> module.take(player);
            case "undo" -> module.undo(player);
            case "use" -> module.use(player, false);
            case "preview" -> module.use(player, true);
            default -> player.sendMessage("[ブラシ] /ibrush [give|menu|off|undo|use|preview]  (ブラシを持って 右クリック = 使う / Shift+右 = プレビュー / 左クリック = 設定画面)");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1) return result;
        for (String sub : SUBS) if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(sub);
        return result;
    }
}
