package net.tkgon.mc.iruuRPG.command;

import net.tkgon.mc.iruuRPG.vfx.flow.FlowArgs;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowEffect;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowEffects;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowOrigin;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowRunner;
import net.tkgon.mc.iruuRPG.vfx.flow.Param;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /vfx list | help [id] | test <id> <x> <y> <z> <seconds> [args...]}: look at the light flow effects of {@link FlowEffects}. The coordinates of
 * {@code test} are plain numbers, {@code ~} (from where the player stands) or {@code ^} (from where the player faces: left, up, forward), as in vanilla.
 */
public final class VfxCommand implements TabExecutor {

    private static final String PREFIX = "[vfx] ";
    private static final double MAX_SECONDS = 60.0;

    private final JavaPlugin plugin;

    public VfxCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "help" -> help(sender, args.length > 1 ? args[1] : null);
            case "test" -> test(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(PREFIX + "/vfx list = 一覧 / /vfx help <名前> = 引数の説明 / /vfx test <名前> <x> <y> <z> <秒> [引数...] = その場で出す");
        sender.sendMessage(PREFIX + "座標は 数字・~ (いまの位置から)・^ (向いている方向から: 左 上 前)。引数は順番に並べるか name=値 で書く。");
    }

    private void list(CommandSender sender) {
        sender.sendMessage(PREFIX + "流体系の軽いエフェクト (" + FlowEffects.all().size() + "):");
        for (FlowEffect effect : FlowEffects.all()) {
            sender.sendMessage("  " + effect.id() + " - " + effect.summary());
        }
        sender.sendMessage(PREFIX + "/vfx help <名前> で引数、/vfx test <名前> ... で確認。");
    }

    private void help(CommandSender sender, String id) {
        if (id == null) {
            usage(sender);
            return;
        }
        FlowEffect effect = FlowEffects.find(id);
        if (effect == null) {
            sender.sendMessage(PREFIX + "そんなエフェクトはありません: " + id + " (/vfx list で一覧)");
            return;
        }
        List<Param> params = effect.params();
        StringBuilder line = new StringBuilder("/vfx test " + effect.id() + " <x> <y> <z> <秒>");
        for (int index = 0; index < params.size(); index++) line.append(" [").append(params.get(index).name()).append(']');
        sender.sendMessage(PREFIX + effect.id() + " - " + effect.summary());
        sender.sendMessage("  " + line);
        sender.sendMessage("  <秒>: 粒を出し続ける時間 (0.1〜" + (int) MAX_SECONDS + "。そのあと粒が消えるまで続く)");
        for (int index = 0; index < params.size(); index++) {
            Param param = params.get(index);
            sender.sendMessage("  arg" + (index + 1) + " " + param.shortText() + ": " + param.description());
        }
    }

    private void test(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "ゲーム内のプレイヤーだけが使えます。");
            return;
        }
        if (args.length < 6) {
            sender.sendMessage(PREFIX + "/vfx test <名前> <x> <y> <z> <秒> [引数...]  (/vfx help <名前> で引数)");
            return;
        }
        FlowEffect effect = FlowEffects.find(args[1]);
        if (effect == null) {
            sender.sendMessage(PREFIX + "そんなエフェクトはありません: " + args[1] + " (/vfx list で一覧)");
            return;
        }
        Location at;
        double seconds;
        FlowArgs flowArgs;
        try {
            at = parseLocation(player, args[2], args[3], args[4]);
            seconds = parseSeconds(args[5]);
            flowArgs = FlowArgs.parse(effect.params(), List.of(args).subList(6, args.length));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(PREFIX + exception.getMessage());
            return;
        }
        if (!FlowRunner.start(plugin, player, FlowOrigin.fixed(at), effect, flowArgs, (int) Math.round(seconds * 20.0))) {
            sender.sendMessage(PREFIX + "同時に動かせるのは6個までです。終わるまで待ってください。");
            return;
        }
        sender.sendMessage(PREFIX + effect.id() + " を " + format(at) + " に " + seconds + " 秒出します。");
    }

    private static String format(Location at) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", at.getX(), at.getY(), at.getZ());
    }

    private static double parseSeconds(String text) {
        try {
            double seconds = Double.parseDouble(text);
            if (Double.isNaN(seconds) || seconds < 0.1 || seconds > MAX_SECONDS) throw new NumberFormatException();
            return seconds;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("秒は 0.1〜" + (int) MAX_SECONDS + " の数字で指定してください: " + text);
        }
    }

    /** Three coordinates: numbers, {@code ~} / {@code ~n} (from the player's feet) or {@code ^} / {@code ^n} (left, up, forward; all three must be {@code ^}). */
    static Location parseLocation(Player player, String x, String y, String z) {
        int carets = (x.startsWith("^") ? 1 : 0) + (y.startsWith("^") ? 1 : 0) + (z.startsWith("^") ? 1 : 0);
        Location feet = player.getLocation();
        if (carets == 3) {
            double left = offset(x.substring(1), x);
            double up = offset(y.substring(1), y);
            double forward = offset(z.substring(1), z);
            Vector facing = feet.getDirection().normalize();
            Vector leftAxis = new Vector(0.0, 1.0, 0.0).crossProduct(facing);
            if (leftAxis.lengthSquared() < 1.0E-6) leftAxis = new Vector(1.0, 0.0, 0.0);
            leftAxis.normalize();
            Vector upAxis = facing.clone().crossProduct(leftAxis).normalize();
            return feet.clone().add(leftAxis.multiply(left)).add(upAxis.multiply(up)).add(facing.multiply(forward));
        }
        if (carets != 0) throw new IllegalArgumentException("^ は x y z の3つともに付けてください (~ や数字とは混ぜられません)");
        return new Location(feet.getWorld(), coordinate(x, feet.getX()), coordinate(y, feet.getY()), coordinate(z, feet.getZ()));
    }

    private static double offset(String text, String whole) {
        if (text.isEmpty()) return 0.0;
        try {
            double value = Double.parseDouble(text);
            if (Double.isNaN(value) || Double.isInfinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("座標が読めません: " + whole);
        }
    }

    private static double coordinate(String text, double current) {
        if (text.startsWith("~")) return current + offset(text.substring(1), text);
        return offset(text, text);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        List<String> options = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            options.addAll(List.of("list", "help", "test"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("test"))) {
            for (FlowEffect effect : FlowEffects.all()) options.add(effect.id());
        } else if (args[0].equalsIgnoreCase("test") && args.length >= 3 && args.length <= 5) {
            options.addAll(List.of("~", "^"));
        } else if (args[0].equalsIgnoreCase("test") && args.length == 6) {
            options.addAll(List.of("3", "5", "10"));
        } else if (args[0].equalsIgnoreCase("test") && args.length > 6) {
            FlowEffect effect = FlowEffects.find(args[1]);
            if (effect != null) {
                for (Param param : effect.params()) options.add(param.name() + "=");
            }
        }
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(last)) result.add(option);
        return result;
    }
}
