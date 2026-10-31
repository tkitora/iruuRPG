package net.tkgon.mc.iruuRPG.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * /itemmigration (iruurpg.admin): status (the mode, the report, the backups), and restore &lt;player&gt; [backup folder]:
 * puts an online player's inventory and ender chest back as the migration's backup has them (the newest backup of the
 * player when no folder is named).
 */
public final class ItemMigrationCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ItemPatchService service;

    public ItemMigrationCommand(JavaPlugin plugin, ItemPatchService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("iruurpg.admin")) return true;
        File root = new File(plugin.getDataFolder(), "backups/item-migration");
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text("[移行] item-migration.mode: " + service.mode() + " / item version " + service.version(), NamedTextColor.GOLD));
            sender.sendMessage(Component.text("一覧: plugins/iruuRPG/item-migration-report.txt", NamedTextColor.GRAY));
            String[] runs = root.list();
            sender.sendMessage(Component.text("バックアップ: " + (runs == null || runs.length == 0 ? "なし" : String.join(", ", runs)), NamedTextColor.GRAY));
            return true;
        }
        if (args[0].equalsIgnoreCase("restore") && args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("[移行] オンラインのプレイヤーだけ戻せます。", NamedTextColor.RED));
                return true;
            }
            File[] runs = args.length >= 3 ? new File[]{new File(root, args[2])} : root.listFiles(File::isDirectory);
            File backup = null;
            if (runs != null) {
                backup = Arrays.stream(runs)
                        .flatMap(run -> Arrays.stream(run.listFiles((dir, name) -> name.startsWith(target.getUniqueId().toString())) == null
                                ? new File[0] : run.listFiles((dir, name) -> name.startsWith(target.getUniqueId().toString()))))
                        .max(Comparator.comparingLong(File::lastModified)).orElse(null);
            }
            if (backup == null || !service.restore(target, backup)) {
                sender.sendMessage(Component.text("[移行] " + target.getName() + " のバックアップが見つかりません。", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("[移行] " + target.getName() + " を戻しました: " + backup.getParentFile().getName() + "/" + backup.getName(), NamedTextColor.GREEN));
            plugin.getLogger().info("items: " + sender.getName() + " restored " + target.getName() + " from " + backup.getPath());
            return true;
        }
        sender.sendMessage(Component.text("/itemmigration status | restore <player> [バックアップのフォルダ]", NamedTextColor.GRAY));
        return true;
    }
}
