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

/** /spawner edit | list | reload: the field spawners (admins only). */
public final class FieldSpawnerCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("edit", "list", "reload");

    private final FieldSpawnerService service;
    private final FieldSpawnerEditor editor;

    public FieldSpawnerCommand(FieldSpawnerService service, FieldSpawnerEditor editor) {
        this.service = service;
        this.editor = editor;
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
            default -> sender.sendMessage(Component.text("/spawner [edit | list | reload]", NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(DungeonService.ADMIN_PERMISSION)) return List.of();
        return SUBS.stream().filter(sub -> sub.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
