package net.tkgon.mc.iruuRPG.command;

import net.tkgon.mc.iruuRPG.gui.SkillTreeMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ClassCommand implements CommandExecutor, TabCompleter {

    private final SkillTreeMenu skillTreeMenu;

    public ClassCommand(SkillTreeMenu skillTreeMenu) {
        this.skillTreeMenu = skillTreeMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        skillTreeMenu.open(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        return List.of();
    }
}
