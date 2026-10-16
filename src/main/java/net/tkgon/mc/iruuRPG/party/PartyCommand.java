package net.tkgon.mc.iruuRPG.party;

import net.tkgon.mc.iruuRPG.gui.PartyMenu;
import org.bukkit.Bukkit;
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
import java.util.UUID;

/**
 * /party (menu), /party invite|accept|deny|leave|kick|leader|disband|list|chat, and /pc &lt;message&gt; for party chat.
 */
public final class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("invite", "accept", "deny", "leave", "kick", "leader", "disband", "list", "chat");

    private final PartyService service;
    private final PartyMenu menu;

    public PartyCommand(PartyService service, PartyMenu menu) {
        this.service = service;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("pc")) {
            if (args.length == 0) {
                player.sendMessage("[パーティー] /pc <メッセージ> でパーティーの仲間だけに話す。");
            } else {
                service.chat(player, String.join(" ", args));
            }
            return true;
        }
        if (args.length == 0) {
            menu.open(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) player.sendMessage("[パーティー] /party invite <オンラインのプレイヤー名>");
                else service.invite(player, target);
            }
            case "accept" -> service.accept(player);
            case "deny" -> service.deny(player);
            case "leave" -> service.leave(player);
            case "kick" -> {
                if (args.length < 2) player.sendMessage("[パーティー] /party kick <名前>");
                else service.kick(player, args[1]);
            }
            case "leader" -> {
                if (args.length < 2) player.sendMessage("[パーティー] /party leader <名前>");
                else service.promote(player, args[1]);
            }
            case "disband" -> service.disband(player);
            case "list" -> service.list(player);
            case "chat" -> {
                if (args.length < 2) player.sendMessage("[パーティー] /party chat <メッセージ> (または /pc)");
                else service.chat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            default -> player.sendMessage("[パーティー] /party [invite <名前> | accept | deny | leave | kick <名前> | leader <名前> | disband | list | chat <メッセージ>]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("pc") || !(sender instanceof Player player)) return options;
        if (args.length == 1) {
            options.addAll(SUBS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(sender) && service.partyOf(online.getUniqueId()) == null) options.add(online.getName());
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("leader"))) {
            PartyService.Party party = service.partyOf(player.getUniqueId());
            if (party != null) {
                for (UUID id : party.members()) {
                    if (!id.equals(player.getUniqueId())) options.add(PartyService.nameOf(id));
                }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
