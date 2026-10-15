package net.tkgon.mc.iruuRPG.trade;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /trade <player> asks for a trade; /trade accept [player] / deny [player] answers one. */
public final class TradeCommand implements CommandExecutor, TabCompleter {

    private final TradeService service;

    public TradeCommand(TradeService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("[トレード] /trade <プレイヤー名> で申し込み、/trade accept で受ける。");
            return true;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("accept") || first.equals("deny")) {
            Player requester = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : service.singleRequester(player);
            if (first.equals("accept")) service.accept(player, requester);
            else service.deny(player, requester);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("[トレード] " + args[0] + " はオンラインではありません。");
            return true;
        }
        service.request(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> names = new ArrayList<>();
        if (args.length == 1) {
            names.add("accept");
            names.add("deny");
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(sender)) names.add(online.getName());
            }
        } else if (args.length == 2) {
            for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        names.removeIf(name -> !name.toLowerCase(Locale.ROOT).startsWith(prefix));
        return names;
    }
}
