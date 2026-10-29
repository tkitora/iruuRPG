package net.tkgon.mc.iruuRPG.bgm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /bgm}: the loudness for everyone; the songs, the boxes and the tests for admins ({@code iruurpg.admin}). */
final class BgmCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "iruurpg.admin";
    private static final List<String> PLAYER_SUBS = List.of("volume", "now");
    private static final List<String> ADMIN_SUBS = List.of("wand", "pos1", "pos2", "create", "redefine", "set", "remove", "list", "info", "tp", "show", "play", "stop", "songs", "reload");
    private static final List<String> FIELDS = List.of("song", "priority", "volume", "fade", "loop", "world");

    private final BgmModule module;

    BgmCommand(BgmModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player player)) {
            if (sub.equals("list")) list(sender);
            else if (sub.equals("songs")) songs(sender);
            else if (sub.equals("reload")) reload(sender);
            else sender.sendMessage("[BGM] ゲームの中から使います (list / songs / reload はここでも使えます)");
            return true;
        }
        if (PLAYER_SUBS.contains(sub)) {
            if (sub.equals("volume")) volume(player, args);
            else now(player);
            return true;
        }
        if (sub.equals("help") || !ADMIN_SUBS.contains(sub)) {
            help(player);
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            message(player, "管理者だけが使えます (自分の音量は /bgm volume)", NamedTextColor.RED);
            return true;
        }
        switch (sub) {
            case "wand" -> {
                player.getInventory().addItem(module.wand());
                message(player, "ワンドを渡しました。左クリックで1つ目、右クリックで2つ目の角を選びます", NamedTextColor.AQUA);
            }
            case "pos1", "pos2" -> {
                Location at = player.getLocation();
                module.setCorner(player, sub.equals("pos1"), at.getWorld().getName(), new int[]{at.getBlockX(), at.getBlockY(), at.getBlockZ()});
            }
            case "create" -> create(player, args);
            case "redefine" -> redefine(player, args);
            case "set" -> set(player, args);
            case "remove" -> remove(player, args);
            case "list" -> list(player);
            case "info" -> info(player, args);
            case "tp" -> teleport(player, args);
            case "show" -> show(player, args);
            case "play" -> play(player, args);
            case "stop" -> {
                module.channel(player).stopManual(1.0);
                message(player, "試し聴きを止めました (範囲に入っていれば、その曲に戻ります)", NamedTextColor.AQUA);
            }
            case "songs" -> songs(player);
            case "reload" -> reload(player);
            default -> help(player);
        }
        return true;
    }

    // ---- everyone --------------------------------------------------------------------------------------------------------------------

    private void volume(Player player, String[] args) {
        BgmSettings settings = module.settings();
        if (args.length >= 2) {
            Integer value = integer(args[1]);
            if (value == null || value < 0 || value > 100) {
                message(player, "0 から 100 の数で指定します (例: /bgm volume 60)", NamedTextColor.RED);
                return;
            }
            settings.setPercent(player, value);
        }
        message(player, "BGMの音量: " + settings.percent(player) + "% (メニューの「設定」からも変えられます)", NamedTextColor.AQUA);
    }

    private void now(Player player) {
        String title = module.settings().nowPlaying(player);
        message(player, title == null ? "いまは、BGMが流れていません" : "いまのBGM: " + title, NamedTextColor.AQUA);
    }

    private void help(Player player) {
        message(player, "/bgm volume [0-100]  自分のBGMの音量 / /bgm now  いまの曲", NamedTextColor.AQUA);
        if (!player.hasPermission(PERMISSION)) return;
        message(player, "範囲: wand | pos1 | pos2 | create <id> <曲> [優先度] | redefine <id> | set <id> <song|priority|volume|fade|loop|world> <値> | remove <id> | list | info <id> | tp <id> | show [id|off]", NamedTextColor.GRAY);
        message(player, "曲: songs | play <曲> [loop] | stop | reload   (曲は plugins/iruuRPG/bgm/ の .nbs)", NamedTextColor.GRAY);
    }

    // ---- boxes -----------------------------------------------------------------------------------------------------------------------

    private void create(Player player, String[] args) {
        if (args.length < 3) {
            message(player, "使い方: /bgm create <id> <曲> [優先度]  (先にワンドで2つの角を選びます)", NamedTextColor.RED);
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_\\-]{1,40}")) {
            message(player, "id は英小文字・数字・_ - の40文字まで", NamedTextColor.RED);
            return;
        }
        if (module.registry().volume(id) != null) {
            message(player, id + " はもうあります (範囲を変えるのは redefine)", NamedTextColor.RED);
            return;
        }
        String song = args[2].toLowerCase(Locale.ROOT);
        if (module.registry().song(song) == null) {
            message(player, "曲 '" + song + "' がありません (/bgm songs)", NamedTextColor.RED);
            return;
        }
        BgmModule.Selection selection = module.selection(player);
        if (!selection.complete()) {
            message(player, "先にワンドで2つの角を選んでください (/bgm wand)", NamedTextColor.RED);
            return;
        }
        BgmVolume volume = new BgmVolume(id, selection.world, selection.first, selection.second, song);
        if (args.length >= 4) {
            Integer priority = integer(args[3]);
            if (priority == null) {
                message(player, "優先度は整数です", NamedTextColor.RED);
                return;
            }
            volume.setPriority(priority);
        }
        module.registry().put(volume);
        message(player, id + " を作りました: " + selection.world + " " + BgmModule.size(selection.first, selection.second) + " / 曲 " + song, NamedTextColor.GREEN);
        module.show(player, id, 8);
    }

    private void redefine(Player player, String[] args) {
        BgmVolume volume = args.length >= 2 ? module.registry().volume(args[1]) : null;
        if (volume == null) {
            message(player, "使い方: /bgm redefine <id>  (ワンドで選んだ角に、範囲を変えます)", NamedTextColor.RED);
            return;
        }
        BgmModule.Selection selection = module.selection(player);
        if (!selection.complete()) {
            message(player, "先にワンドで2つの角を選んでください", NamedTextColor.RED);
            return;
        }
        volume.setBox(selection.world, selection.first, selection.second);
        module.registry().save();
        message(player, volume.id() + " の範囲を変えました: " + BgmModule.size(selection.first, selection.second), NamedTextColor.GREEN);
        module.show(player, volume.id(), 8);
    }

    private void set(Player player, String[] args) {
        BgmVolume volume = args.length >= 2 ? module.registry().volume(args[1]) : null;
        if (volume == null || args.length < 4 || !FIELDS.contains(args[2].toLowerCase(Locale.ROOT))) {
            message(player, "使い方: /bgm set <id> <song|priority|volume|fade|loop|world> <値>", NamedTextColor.RED);
            return;
        }
        String field = args[2].toLowerCase(Locale.ROOT);
        String value = args[3];
        switch (field) {
            case "song" -> {
                if (module.registry().song(value) == null) {
                    message(player, "曲 '" + value + "' がありません (/bgm songs)", NamedTextColor.RED);
                    return;
                }
                volume.setSong(value.toLowerCase(Locale.ROOT));
            }
            case "priority" -> {
                Integer number = integer(value);
                if (number == null) {
                    message(player, "整数で指定します", NamedTextColor.RED);
                    return;
                }
                volume.setPriority(number);
            }
            case "volume" -> {
                Integer number = integer(value);
                if (number == null || number < 0 || number > 100) {
                    message(player, "0 から 100 で指定します", NamedTextColor.RED);
                    return;
                }
                volume.setVolume(number);
            }
            case "fade" -> {
                Double number = decimal(value);
                if (number == null || number < 0 || number > 60) {
                    message(player, "0 から 60 (秒) で指定します", NamedTextColor.RED);
                    return;
                }
                volume.setFade(number);
            }
            case "world" -> {
                if (!value.matches("[A-Za-z0-9_.\\-*]{1,80}")) {
                    message(player, "ワールド名で指定します。* が使えます (例: dungeon_*_cave* = 洞窟ダンジョンのテンプレートと全部のコピー)", NamedTextColor.RED);
                    return;
                }
                volume.setWorld(value);
            }
            default -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    message(player, "true か false で指定します", NamedTextColor.RED);
                    return;
                }
                volume.setLoop(Boolean.parseBoolean(value));
            }
        }
        module.registry().save();
        message(player, volume.id() + " の " + field + " を " + value + " にしました", NamedTextColor.GREEN);
    }

    private void remove(Player player, String[] args) {
        if (args.length < 2 || !module.registry().remove(args[1])) {
            message(player, "使い方: /bgm remove <id> (ある範囲の id)", NamedTextColor.RED);
            return;
        }
        message(player, args[1].toLowerCase(Locale.ROOT) + " を消しました", NamedTextColor.GREEN);
    }

    private void list(CommandSender sender) {
        List<BgmVolume> volumes = module.registry().sorted();
        if (volumes.isEmpty()) {
            sender.sendMessage(Component.text(BgmModule.PREFIX + "範囲はまだありません (/bgm wand と /bgm create)", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text(BgmModule.PREFIX + volumes.size() + " 個の範囲", NamedTextColor.AQUA));
        for (BgmVolume volume : volumes) {
            sender.sendMessage(Component.text(" " + volume.id() + "  " + volume.world() + "  曲 " + volume.song() + "  優先度 " + volume.priority()
                    + "  音量 " + volume.volume() + "%  フェード " + volume.fade() + "秒" + (volume.loop() ? "" : "  1回だけ"), NamedTextColor.GRAY));
        }
    }

    private void info(Player player, String[] args) {
        BgmVolume volume = args.length >= 2 ? module.registry().volume(args[1]) : null;
        if (volume == null) {
            message(player, "使い方: /bgm info <id>", NamedTextColor.RED);
            return;
        }
        int[] min = volume.min();
        int[] max = volume.max();
        NbsSong song = module.registry().song(volume.song());
        message(player, volume.id() + ": " + volume.world() + " (" + min[0] + " " + min[1] + " " + min[2] + ") - (" + max[0] + " " + max[1] + " " + max[2] + ")  "
                + BgmModule.size(min, max), NamedTextColor.AQUA);
        message(player, "曲 " + volume.song() + (song == null ? " (見つかりません)" : " 「" + song.title() + "」" + String.format(Locale.ROOT, " %.0f秒", song.seconds()))
                + "  優先度 " + volume.priority() + "  音量 " + volume.volume() + "%  フェード " + volume.fade() + "秒  ループ " + volume.loop(), NamedTextColor.GRAY);
    }

    private void teleport(Player player, String[] args) {
        BgmVolume volume = args.length >= 2 ? module.registry().volume(args[1]) : null;
        World world = null;
        if (volume != null) {
            for (World candidate : player.getServer().getWorlds()) {
                if (volume.inWorld(candidate.getName())) {
                    world = candidate;
                    break;
                }
            }
        }
        if (volume == null || world == null) {
            message(player, volume == null ? "使い方: /bgm tp <id> (その範囲の真ん中へ)" : "その範囲のワールドは、いまありません (ダンジョンのコピーは、入っているときだけあります)", NamedTextColor.RED);
            return;
        }
        int[] min = volume.min();
        int[] max = volume.max();
        double x = (min[0] + max[0] + 1) / 2.0;
        double z = (min[2] + max[2] + 1) / 2.0;
        int y = Math.min(max[1], Math.max(min[1], world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1));
        player.teleport(new Location(world, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch()));
    }

    private void show(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            module.hide(player);
            message(player, "範囲の表示を止めました", NamedTextColor.AQUA);
            return;
        }
        String id = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
        if (id != null && module.registry().volume(id) == null) {
            message(player, "範囲 '" + id + "' がありません (/bgm list)", NamedTextColor.RED);
            return;
        }
        module.show(player, id, 20);
        message(player, "範囲を20秒、パーティクルで表示します (緑: 作った範囲、水色: いま選んでいる角)", NamedTextColor.AQUA);
    }

    // ---- songs -----------------------------------------------------------------------------------------------------------------------

    private void songs(CommandSender sender) {
        var songs = module.registry().songs();
        if (songs.isEmpty()) {
            sender.sendMessage(Component.text(BgmModule.PREFIX + "曲がありません (plugins/iruuRPG/bgm/ に .nbs を置いて /bgm reload)", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text(BgmModule.PREFIX + songs.size() + " 曲", NamedTextColor.AQUA));
        for (NbsSong song : songs.values()) {
            sender.sendMessage(Component.text(" " + song.id() + "  「" + song.title() + "」  " + String.format(Locale.ROOT, "%.0f秒  %.1f ticks/秒", song.seconds(), song.ticksPerSecond())
                    + (song.loops() ? "  ループ位置あり" : "") + (module.registry().origin(song.id()).equals("bgm") ? "  (bgm フォルダ)" : "  (同梱)"), NamedTextColor.GRAY));
        }
    }

    private void play(Player player, String[] args) {
        NbsSong song = args.length >= 2 ? module.registry().song(args[1]) : null;
        if (song == null) {
            message(player, "使い方: /bgm play <曲> [loop]  (曲は /bgm songs)", NamedTextColor.RED);
            return;
        }
        boolean loop = args.length >= 3 && args[2].equalsIgnoreCase("loop");
        module.channel(player).playManual(song, loop, 1.0);
        message(player, "試し聴き: 「" + song.title() + "」" + (loop ? " (ループ)" : "") + "  止めるのは /bgm stop", NamedTextColor.AQUA);
    }

    private void reload(CommandSender sender) {
        String changes = module.registry().reload();
        sender.sendMessage(Component.text(BgmModule.PREFIX + "曲 " + module.registry().songs().size() + " / 範囲 " + module.registry().volumes().size() + " を読み直しました"
                + (changes.isEmpty() ? "" : "  (" + changes + ")"), NamedTextColor.GREEN));
        for (String problem : module.registry().problems()) sender.sendMessage(Component.text(" ! " + problem, NamedTextColor.RED));
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------------------------

    private static void message(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(BgmModule.PREFIX + text, color));
    }

    private static Integer integer(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Double decimal(String text) {
        try {
            double value = Double.parseDouble(text);
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        boolean admin = sender.hasPermission(PERMISSION);
        if (args.length == 1) {
            add(result, PLAYER_SUBS, args[0]);
            if (admin) add(result, ADMIN_SUBS, args[0]);
            return result;
        }
        if (!admin) return result;
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "redefine", "set", "remove", "info", "tp" -> add(result, new ArrayList<>(module.registry().volumes().keySet()), args[1]);
                case "show" -> {
                    add(result, new ArrayList<>(module.registry().volumes().keySet()), args[1]);
                    add(result, List.of("off"), args[1]);
                }
                case "play" -> add(result, new ArrayList<>(module.registry().songs().keySet()), args[1]);
                default -> {
                }
            }
        } else if (args.length == 3) {
            if (sub.equals("create")) add(result, new ArrayList<>(module.registry().songs().keySet()), args[2]);
            else if (sub.equals("set")) add(result, FIELDS, args[2]);
            else if (sub.equals("play")) add(result, List.of("loop"), args[2]);
        } else if (args.length == 4 && sub.equals("set")) {
            String field = args[2].toLowerCase(Locale.ROOT);
            if (field.equals("song")) add(result, new ArrayList<>(module.registry().songs().keySet()), args[3]);
            else if (field.equals("loop")) add(result, List.of("true", "false"), args[3]);
        }
        return result;
    }

    private static void add(List<String> out, List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) if (option.startsWith(lower)) out.add(option);
    }
}
