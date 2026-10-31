package net.tkgon.mc.iruuRPG.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

/**
 * /npc (iruurpg.admin): edit (the editor mode), list, reload, and what is typed rather than clicked: name, rename (the
 * id), idle (add / clear), look (villager / entity / model), tp.
 */
public final class NpcCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("edit", "list", "reload", "name", "rename", "idle", "look", "tp");

    private final NpcService npcs;
    private final NpcEditor editor;

    public NpcCommand(NpcService npcs, NpcEditor editor) {
        this.npcs = npcs;
        this.editor = editor;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("iruurpg.admin")) {
            sender.sendMessage(Component.text("[NPC] 権限がありません。", NamedTextColor.RED));
            return true;
        }
        String sub = args.length == 0 ? "edit" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "edit" -> {
                if (sender instanceof Player player) editor.toggle(player);
            }
            case "list" -> {
                for (Npc npc : npcs.all()) {
                    sender.sendMessage(Component.text(npc.id() + " " + npc.name() + " (" + npc.look().describe() + ") "
                            + npc.worldName() + " " + Math.round(npc.x()) + "," + Math.round(npc.y()) + "," + Math.round(npc.z()), NamedTextColor.GRAY));
                }
                if (npcs.all().isEmpty()) sender.sendMessage(Component.text("[NPC] まだありません。/npc edit で置けます。", NamedTextColor.GRAY));
            }
            case "reload" -> {
                npcs.shutdown();
                npcs.start();
                sender.sendMessage(Component.text("[NPC] npcs.yml を読み直しました (" + npcs.all().size() + " 体)。", NamedTextColor.GREEN));
            }
            case "name", "rename", "idle", "look", "tp" -> {
                Npc npc = args.length > 1 ? npcs.find(args[1]).orElse(null) : null;
                if (npc == null) {
                    sender.sendMessage(Component.text("[NPC] その id の NPC はありません。", NamedTextColor.RED));
                    return true;
                }
                change(sender, sub, npc, Arrays.copyOfRange(args, 2, args.length));
            }
            default -> usage(sender);
        }
        return true;
    }

    private void change(CommandSender sender, String sub, Npc npc, String[] rest) {
        String text = String.join(" ", rest).trim();
        switch (sub) {
            case "name" -> {
                if (text.isEmpty()) {
                    usage(sender);
                    return;
                }
                npc.name(text);
                npcs.changed(npc);
                ok(sender, npc.id() + " の名前を「" + text + "」にした。");
            }
            case "rename" -> {
                if (npcs.rename(npc, text)) {
                    ok(sender, npc.id() + " の id を " + text + " にした。クエストの yml で古い id を使っていれば、直してください。");
                } else {
                    sender.sendMessage(Component.text("[NPC] 使えない id か、もう使われています (英小文字・数字・_)。", NamedTextColor.RED));
                }
            }
            case "idle" -> {
                if (rest.length > 0 && rest[0].equalsIgnoreCase("clear")) {
                    npc.idle().clear();
                } else if (rest.length > 1 && rest[0].equalsIgnoreCase("add")) {
                    npc.idle().add(String.join(" ", Arrays.copyOfRange(rest, 1, rest.length)));
                } else {
                    usage(sender);
                    return;
                }
                npcs.save();
                ok(sender, npc.id() + " のアイドルの言葉: " + npc.idle().size() + " 行");
            }
            case "look" -> {
                Npc.Look look = null;
                if (rest.length >= 2 && rest[0].equalsIgnoreCase("villager")) {
                    look = Npc.Look.villager(rest[1].toUpperCase(Locale.ROOT), rest.length > 2 ? rest[2].toUpperCase(Locale.ROOT) : "PLAINS");
                } else if (rest.length >= 2 && rest[0].equalsIgnoreCase("entity")) {
                    look = new Npc.Look("entity", null, null, rest[1].toUpperCase(Locale.ROOT), rest.length > 2 && rest[2].equalsIgnoreCase("baby"), null, 1.0);
                } else if (rest.length >= 2 && rest[0].equalsIgnoreCase("model")) {
                    double scale = 1.0;
                    if (rest.length > 2) {
                        try {
                            scale = Double.parseDouble(rest[2]);
                        } catch (NumberFormatException ignored) {
                            scale = 1.0;
                        }
                    }
                    look = Npc.Look.model(rest[1], scale);
                }
                if (look == null) {
                    usage(sender);
                    return;
                }
                npc.look(look);
                npcs.changed(npc);
                ok(sender, npc.id() + " の見た目を " + look.describe() + " にした。");
            }
            case "tp" -> {
                if (sender instanceof Player player && npc.location() != null) player.teleport(npc.location());
            }
            default -> usage(sender);
        }
        editor.refresh();
    }

    private void ok(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[NPC] " + message, NamedTextColor.GREEN));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("""
                /npc edit | list | reload
                /npc name <id> <名前>
                /npc rename <id> <新しいid>
                /npc idle <id> add <言葉> | clear
                /npc look <id> villager <職業> [村の種類] | entity <種類> [baby] | model <モデルid> [大きさ]
                /npc tp <id>""", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("iruurpg.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2 && !List.of("edit", "list", "reload").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Npc npc : npcs.all()) options.add(npc.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("look")) {
            options.addAll(List.of("villager", "entity", "model"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("idle")) {
            options.addAll(List.of("add", "clear"));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
