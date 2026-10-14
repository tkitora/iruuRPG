package net.tkgon.mc.iruuRPG.crafting;

import net.tkgon.mc.iruuRPG.gui.CraftMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** /craft opens the crafting list. Admin: /craft level <n> sets the craft level (testing). */
public final class CraftCommand implements CommandExecutor, TabCompleter {

    private final CraftService service;
    private final CraftMenu menu;

    public CraftCommand(CraftService service, CraftMenu menu) {
        this.service = service;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("level")) {
            if (!player.hasPermission("iruurpg.admin")) {
                player.sendMessage("[iruuRPG] この操作は管理者のみです。");
                return true;
            }
            try {
                service.setLevel(player, Integer.parseInt(args[1]));
                player.sendMessage("[iruuRPG] クラフトレベルを " + service.level(player) + " にしました。");
            } catch (NumberFormatException e) {
                player.sendMessage("[iruuRPG] 数を入れてください。");
            }
            return true;
        }
        menu.open(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        return args.length == 1 ? List.of("level") : List.of();
    }
}
