package net.tkgon.mc.iruuRPG.market;

import net.tkgon.mc.iruuRPG.gui.MarketMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /market (/fm) opens the free market. */
public final class MarketCommand implements CommandExecutor {

    private final MarketMenu menu;

    public MarketCommand(MarketMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        menu.open(player);
        return true;
    }
}
