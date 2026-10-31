package net.tkgon.mc.iruuRPG.questline;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /questline accept &lt;line&gt; | decline: the buttons under an NPC's offer (players). */
public final class QuestlineCommand implements CommandExecutor {

    private final QuestlineService service;

    public QuestlineCommand(QuestlineService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player) || args.length == 0) return true;
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "accept" -> {
                if (args.length > 1) service.accept(player, args[1]);
            }
            case "decline" -> service.decline(player);
            default -> {
            }
        }
        return true;
    }
}
