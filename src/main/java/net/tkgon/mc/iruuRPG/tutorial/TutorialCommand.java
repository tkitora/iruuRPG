package net.tkgon.mc.iruuRPG.tutorial;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /tutorialstart and /tutorialstop (debug tutorial, op only). */
public final class TutorialCommand implements CommandExecutor {

    private final TutorialService service;

    public TutorialCommand(TutorialService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("tutorialstop")) {
            service.stop(player, "チュートリアルを中断しました。");
        } else {
            service.start(player);
        }
        return true;
    }
}
