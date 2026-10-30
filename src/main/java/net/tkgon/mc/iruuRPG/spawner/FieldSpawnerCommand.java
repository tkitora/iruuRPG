package net.tkgon.mc.iruuRPG.spawner;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.dungeon.DungeonService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** /spawner edit | list | reload: the field spawners; /spawner blocks: the game's own spawner blocks in the ground (admins only). */
public final class FieldSpawnerCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("edit", "list", "reload", "blocks");
    private static final List<String> BLOCK_SUBS = List.of("page", "tp", "delete", "deleteall", "deleteburied", "undo", "clear");

    private final FieldSpawnerService service;
    private final FieldSpawnerEditor editor;
    private final SpawnerBlockFinder finder;

    public FieldSpawnerCommand(FieldSpawnerService service, FieldSpawnerEditor editor, SpawnerBlockFinder finder) {
        this.service = service;
        this.editor = editor;
        this.finder = finder;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(DungeonService.ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("[スポナー] 管理者だけが使えます。", NamedTextColor.RED));
            return true;
        }
        String sub = args.length == 0 ? "edit" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "edit" -> {
                if (sender instanceof Player player) {
                    editor.toggle(player);
                } else {
                    sender.sendMessage("[スポナー] ゲーム内で使ってください。");
                }
            }
            case "list" -> {
                sender.sendMessage(Component.text("[スポナー] " + service.all().size() + " か所", NamedTextColor.YELLOW));
                for (FieldSpawner spawner : service.all()) {
                    sender.sendMessage(Component.text(spawner.id() + "  " + spawner.mobId() + "  " + spawner.worldName() + " "
                            + Math.round(spawner.x()) + "," + Math.round(spawner.y()) + "," + Math.round(spawner.z())
                            + "  " + spawner.min() + "~" + spawner.max() + "体 / 同時" + spawner.maxAlive() + " / 半径" + spawner.radius()
                            + "  (今 " + spawner.aliveCount() + " 体)", NamedTextColor.GRAY));
                }
            }
            case "reload" -> {
                service.reload();
                sender.sendMessage(Component.text("[スポナー] spawners.yml を読み直した。", NamedTextColor.GREEN));
            }
            case "blocks" -> {
                if (sender instanceof Player player) {
                    blocks(player, args);
                } else {
                    sender.sendMessage("[スポナー] ゲーム内で使ってください。");
                }
            }
            default -> sender.sendMessage(Component.text("/spawner [edit | list | reload | blocks [半径チャンク]]", NamedTextColor.GRAY));
        }
        return true;
    }

    /** /spawner blocks [radius] | page n | tp n | delete n | deleteall | deleteburied | undo | clear */
    private void blocks(Player player, String[] args) {
        String what = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        int number = args.length < 3 ? -1 : number(args[2]);
        switch (what) {
            case "page" -> finder.page(player, Math.max(1, number));
            case "tp" -> finder.teleport(player, number);
            case "delete" -> finder.delete(player, number);
            case "deleteall", "deleteburied" -> finder.deleteAll(player, what.equals("deleteburied"),
                    args.length >= 3 && args[2].equalsIgnoreCase("confirm"));
            case "undo" -> finder.undo(player);
            case "clear" -> finder.clear(player);
            case "" -> finder.search(player, SpawnerBlockFinder.DEFAULT_RADIUS);
            default -> {
                int radius = number(what);
                if (radius < 0) {
                    player.sendMessage(Component.text("/spawner blocks [半径チャンク 0~" + SpawnerBlockFinder.MAX_RADIUS + "] | page | tp | delete | deleteall | deleteburied | undo | clear",
                            NamedTextColor.GRAY));
                } else {
                    finder.search(player, radius);
                }
            }
        }
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(DungeonService.ADMIN_PERMISSION)) return List.of();
        if (args.length == 2 && args[0].equalsIgnoreCase("blocks")) {
            return BLOCK_SUBS.stream().filter(sub -> sub.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length != 1) return List.of();
        return SUBS.stream().filter(sub -> sub.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
