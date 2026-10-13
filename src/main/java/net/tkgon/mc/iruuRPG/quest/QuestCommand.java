package net.tkgon.mc.iruuRPG.quest;

import net.tkgon.mc.iruuRPG.gui.QuestBoardMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /quest [board [town]] opens the board (for now there is no board block, the command stands in).
 * Admin helpers: /quest money [add n], /quest reset, /quest reroll.
 */
public final class QuestCommand implements CommandExecutor, TabCompleter {

    private final QuestService service;
    private final QuestBoardMenu menu;

    public QuestCommand(QuestService service, QuestBoardMenu menu) {
        this.service = service;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        String sub = args.length == 0 ? "board" : args[0].toLowerCase();
        switch (sub) {
            case "board" -> menu.open(player, args.length > 1 ? args[1] : "atlas");
            case "money" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("add")) {
                    if (!admin(player)) return true;
                    try {
                        service.addCurrency(player, Long.parseLong(args[2]));
                    } catch (NumberFormatException e) {
                        player.sendMessage("[iruuRPG] 数を入れてください。");
                        return true;
                    }
                }
                player.sendMessage("[iruuRPG] 所持金: " + service.currency(player) + " " + service.currencyName());
            }
            case "reset" -> {
                if (!admin(player)) return true;
                service.reset(player);
                player.sendMessage("[iruuRPG] 依頼の記録をリセットしました。");
            }
            case "reroll" -> {
                if (!admin(player)) return true;
                service.rerollNow();
                player.sendMessage("[iruuRPG] サブ依頼を作り直しました (同じ日付の内容に戻ります)。");
            }
            default -> player.sendMessage("[iruuRPG] /quest [board [町]] | money [add 数] | reset | reroll");
        }
        return true;
    }

    private boolean admin(Player player) {
        if (player.hasPermission("iruurpg.admin")) return true;
        player.sendMessage("[iruuRPG] この操作は管理者のみです。");
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) return List.of("board", "money", "reset", "reroll");
        if (args.length == 2 && args[0].equalsIgnoreCase("board")) return List.of("atlas");
        if (args.length == 2 && args[0].equalsIgnoreCase("money")) return List.of("add");
        return List.of();
    }
}
