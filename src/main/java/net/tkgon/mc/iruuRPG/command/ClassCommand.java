package net.tkgon.mc.iruuRPG.command;

import net.tkgon.mc.iruuRPG.gui.ClassSelectMenu;
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
    private final ClassSelectMenu classSelectMenu;

    public ClassCommand(SkillTreeMenu skillTreeMenu, ClassSelectMenu classSelectMenu) {
        this.skillTreeMenu = skillTreeMenu;
        this.classSelectMenu = classSelectMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("select") || args[0].equalsIgnoreCase("change"))) {
            classSelectMenu.open(player);
            return true;
        }

        skillTreeMenu.open(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        return args.length == 1 ? List.of("select") : List.of();
    }
}
