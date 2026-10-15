package net.tkgon.mc.iruuRPG.dungeon;

import net.tkgon.mc.iruuRPG.gui.DungeonMenu;
import net.tkgon.mc.iruuRPG.mob.RpgMobRegistry;
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
 * /dungeon (menu), /dungeon enter &lt;id&gt;, /dungeon leave. Admins: build, entry, spawn, spawns, clearspawns, save,
 * new, reload (see dungeons/cave.yml for how a dungeon is made).
 */
public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER = List.of("enter", "leave", "invite", "join");
    private static final List<String> ADMIN = List.of("build", "entry", "spawn", "spawns", "removespawn", "clearspawns", "exit", "boss", "save", "new", "reload");

    private final DungeonService service;
    private final DungeonMenu menu;
    private final RpgMobRegistry mobRegistry;

    public DungeonCommand(DungeonService service, DungeonMenu menu, RpgMobRegistry mobRegistry) {
        this.service = service;
        this.menu = menu;
        this.mobRegistry = mobRegistry;
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
        DungeonRegistry registry = service.registry();
        switch (sub) {
            case "enter" -> {
                if (args.length < 2) {
                    player.sendMessage("[ダンジョン] /dungeon enter <id>");
                } else {
                    service.enter(player, args[1].toLowerCase(Locale.ROOT));
                }
                return true;
            }
            case "leave" -> {
                service.leave(player);
                return true;
            }
            case "invite" -> {
                Player target = args.length >= 2 ? org.bukkit.Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    player.sendMessage("[ダンジョン] /dungeon invite <オンラインのプレイヤー名>");
                } else {
                    service.invite(player, target);
                }
                return true;
            }
            case "join" -> {
                service.join(player);
                return true;
            }
            default -> {
            }
        }

        if (!ADMIN.contains(sub)) {
            player.sendMessage("[ダンジョン] /dungeon [enter <id> | leave | invite <名前> | join]");
            return true;
        }
        if (!player.hasPermission(DungeonService.ADMIN_PERMISSION)) {
            player.sendMessage("[ダンジョン] この操作は管理者のみです。");
            return true;
        }
        if (sub.equals("reload")) {
            registry.reload();
            player.sendMessage("[ダンジョン] 定義を読み直した。(" + registry.all().size() + " 件)");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("[ダンジョン] /dungeon " + sub + " <id>" + (sub.equals("spawn") ? " <mobId>" : sub.equals("new") ? " <名前>" : ""));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("new")) {
            if (!DungeonRegistry.validId(id)) {
                player.sendMessage("[ダンジョン] id は a-z・0-9・_ の 1〜24 文字です。");
                return true;
            }
            if (registry.find(id).isPresent()) {
                player.sendMessage("[ダンジョン] その id はもうあります。");
                return true;
            }
            String name = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : id;
            registry.create(id, name);
            player.sendMessage("[ダンジョン] 「" + name + "」(" + id + ") を作った。/dungeon build " + id + " でひな形へ。");
            return true;
        }
        DungeonDefinition definition = registry.find(id).orElse(null);
        if (definition == null) {
            player.sendMessage("[ダンジョン] そのダンジョンはありません: " + id);
            return true;
        }
        switch (sub) {
            case "build" -> service.build(player, definition);
            case "save" -> service.saveTemplate(player, definition);
            case "entry" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                registry.setEntry(definition, player.getLocation());
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] 今の位置を入口にした。");
            }
            case "spawn" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                if (args.length < 3 || mobRegistry.find(args[2]).isEmpty()) {
                    player.sendMessage("[ダンジョン] /dungeon spawn " + id + " <mobId> (モブの id を確認してください)");
                    return true;
                }
                registry.addSpawn(definition, args[2], player.getLocation());
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] #" + (definition.spawns().size() + 1) + " " + args[2] + " を置いた。(消すときは /dungeon removespawn " + id + " "
                        + (definition.spawns().size() + 1) + "、または近くで /dungeon removespawn " + id + ")");
            }
            case "exit" -> {
                if (!player.getWorld().getName().equals(definition.templateWorldName())) {
                    player.sendMessage("[ダンジョン] ひな形のワールド (" + definition.templateWorldName() + ") の中で使ってください。");
                    return true;
                }
                registry.setExit(definition, player.getLocation());
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] 今の位置を脱出口にした。(ボスを倒すと通れる)");
            }
            case "boss" -> {
                if (args.length < 3 || mobRegistry.find(args[2]).isEmpty()) {
                    player.sendMessage("[ダンジョン] /dungeon boss " + id + " <mobId> (このモブが倒れたらクリア)");
                    return true;
                }
                registry.setBoss(definition, args[2]);
                service.refreshMarkers(id);
                boolean placed = definition.spawns().stream().anyMatch(spawn -> spawn.mobId().equals(args[2]));
                player.sendMessage("[ダンジョン] ボスを " + args[2] + " にした。" + (placed ? "" : " (まだ置かれていない: /dungeon spawn " + id + " " + args[2] + ")"));
            }
            case "spawns" -> {
                player.sendMessage("[ダンジョン] ボス: " + (definition.boss() == null ? "なし (全滅でクリア)" : definition.boss())
                        + " / 脱出口: " + (definition.exit() == null ? "なし" : String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", definition.exit().x(), definition.exit().y(), definition.exit().z())));
                player.sendMessage("[ダンジョン] " + definition.name() + " のモブ: " + definition.spawns().size() + " 体");
                for (int index = 0; index < definition.spawns().size(); index++) {
                    DungeonDefinition.MobSpawn spawn = definition.spawns().get(index);
                    player.sendMessage(String.format(Locale.ROOT, "  #%d %s  (%.1f, %.1f, %.1f)", index + 1, spawn.mobId(), spawn.x(), spawn.y(), spawn.z()));
                }
            }
            case "removespawn" -> {
                int index = -1;
                if (args.length >= 3) {
                    try {
                        index = Integer.parseInt(args[2]) - 1;
                    } catch (NumberFormatException e) {
                        player.sendMessage("[ダンジョン] /dungeon removespawn " + id + " [番号] (番号は /dungeon spawns で確認。省くと一番近いモブ)");
                        return true;
                    }
                } else if (player.getWorld().getName().equals(definition.templateWorldName())) {
                    // no number: the placed mob nearest to the player (within 4 blocks)
                    double best = 16.0;
                    for (int i = 0; i < definition.spawns().size(); i++) {
                        double distance = definition.spawns().get(i).in(player.getWorld()).distanceSquared(player.getLocation());
                        if (distance <= best) {
                            best = distance;
                            index = i;
                        }
                    }
                    if (index < 0) {
                        player.sendMessage("[ダンジョン] 近く(4ブロック以内)に置いたモブがありません。番号で指定してください。");
                        return true;
                    }
                }
                if (index < 0 || index >= definition.spawns().size()) {
                    player.sendMessage("[ダンジョン] その番号のモブはありません。(/dungeon spawns " + id + " で確認)");
                    return true;
                }
                String removed = definition.spawns().get(index).mobId();
                registry.removeSpawn(definition, index);
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] #" + (index + 1) + " " + removed + " を消した。(番号は詰めて付け直される)");
            }
            case "clearspawns" -> {
                registry.clearSpawns(definition);
                service.refreshMarkers(id);
                player.sendMessage("[ダンジョン] モブの配置を全部消した。");
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission(DungeonService.ADMIN_PERMISSION);
        if (args.length == 1) {
            options.addAll(PLAYER);
            if (admin) options.addAll(ADMIN);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) if (!online.equals(sender)) options.add(online.getName());
        } else if (args.length == 2 && !List.of("leave", "reload", "new", "join").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (DungeonDefinition definition : service.registry().all()) options.add(definition.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("removespawn") && admin) {
            service.registry().find(args[1]).ifPresent(definition -> {
                for (int i = 1; i <= definition.spawns().size(); i++) options.add(String.valueOf(i));
            });
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("boss")) && admin) {
            options.addAll(mobRegistry.all().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
