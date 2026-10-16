package net.tkgon.mc.iruuRPG.travel;

import net.tkgon.mc.iruuRPG.gui.TravelMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /travel (menu), /travel <id>. Admins: /travel set <id> [name], /travel remove <id>, /travel list. */
public final class TravelCommand implements CommandExecutor, TabCompleter {

    private final TravelService service;
    private final TravelMenu menu;

    public TravelCommand(TravelService service, TravelMenu menu) {
        this.service = service;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (args.length == 0) {
            menu.open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = player.hasPermission("iruurpg.admin");
        switch (sub) {
            case "set" -> {
                if (!admin) break;
                if (args.length < 2 || !TravelService.validId(args[1].toLowerCase(Locale.ROOT))) {
                    player.sendMessage("[ファストトラベル] /travel set <id (a-z・0-9・_)> [名前] (今の位置と向き)");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                String name = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : id;
                service.set(id, name, player.getLocation());
                player.sendMessage("[ファストトラベル] 「" + name + "」(" + id + ") を今の位置に置いた。");
                return true;
            }
            case "remove" -> {
                if (!admin) break;
                if (args.length < 2 || !service.remove(args[1].toLowerCase(Locale.ROOT))) {
                    player.sendMessage("[ファストトラベル] その地点はありません。");
                } else {
                    player.sendMessage("[ファストトラベル] 消した。");
                }
                return true;
            }
            case "list" -> {
                if (!admin) break;
                for (TravelService.Waypoint point : service.all()) {
                    player.sendMessage(String.format(Locale.ROOT, "  %s 「%s」 %s (%.0f, %.0f, %.0f)", point.id(), point.name(), point.world(), point.x(), point.y(), point.z()));
                }
                if (service.all().isEmpty()) player.sendMessage("[ファストトラベル] 地点はまだありません。");
                return true;
            }
            default -> {
                service.travel(player, sub);
                return true;
            }
        }
        player.sendMessage("[ファストトラベル] この操作は管理者のみです。");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (!(sender instanceof Player player)) return options;
        if (args.length == 1) {
            for (TravelService.Waypoint point : service.all()) {
                if (service.known(player, point.id())) options.add(point.id());
            }
            if (sender.hasPermission("iruurpg.admin")) options.addAll(List.of("set", "remove", "list"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove") && sender.hasPermission("iruurpg.admin")) {
            for (TravelService.Waypoint point : service.all()) options.add(point.id());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
