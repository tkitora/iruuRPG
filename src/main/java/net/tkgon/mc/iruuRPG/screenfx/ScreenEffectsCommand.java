package net.tkgon.mc.iruuRPG.screenfx;

import net.tkgon.mc.iruuRPG.screenfx.ScreenEffects.Effect;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /screenfx <effect> [strength] [seconds] [target] | <effect> off [target] | off [target] | status [target] | list}: switches the screen
 * effects of {@link ScreenEffects} on and off. The target is a player's name or a selector ({@code @a}, {@code @p}, ...); with none, the sender.
 */
public final class ScreenEffectsCommand implements TabExecutor {

    private static final String PREFIX = "[画面効果] ";
    static final double MAX_SECONDS = 3600.0;

    private final ScreenEffects effects;

    ScreenEffectsCommand(ScreenEffects effects) {
        this.effects = effects;
    }

    /** What {@code /screenfx <effect> [strength] [seconds] [target]} asks for, or the error to tell. */
    record On(int strength, double seconds, String target, String error) {
    }

    /** Reads the words after the effect's name. */
    static On parseOn(String[] words) {
        int strength = ScreenEffects.NORMAL_STRENGTH;
        double seconds = 0.0;
        String target = null;
        int index = 0;
        if (index < words.length && isNumber(words[index])) {
            try {
                strength = Integer.parseInt(words[index]);
            } catch (NumberFormatException exception) {
                return new On(0, 0, null, "強さは 1〜" + ScreenEffects.MAX_STRENGTH + " の整数です: " + words[index]);
            }
            if (strength < 1 || strength > ScreenEffects.MAX_STRENGTH) return new On(0, 0, null, "強さは 1〜" + ScreenEffects.MAX_STRENGTH + " です: " + strength);
            index++;
        }
        if (index < words.length && isNumber(words[index])) {
            seconds = Double.parseDouble(words[index]);
            if (seconds < 0 || seconds > MAX_SECONDS) return new On(0, 0, null, "秒は 0〜" + (int) MAX_SECONDS + " です (0 = 消すまで): " + words[index]);
            index++;
        }
        if (index < words.length) target = words[index++];
        if (index < words.length) return new On(0, 0, null, "多すぎる引数: " + words[index]);
        return new On(strength, seconds, target, null);
    }

    private static boolean isNumber(String word) {
        return word.matches("-?\\d+(\\.\\d+)?");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (first) {
            case "", "help" -> usage(sender);
            case "list" -> {
                for (Effect effect : Effect.values()) sender.sendMessage(PREFIX + effect.id() + " - " + effect.summary());
            }
            case "off" -> {
                List<Player> players = targets(sender, args.length > 1 ? args[1] : null);
                int ended = 0;
                for (Player player : players) ended += effects.hideAll(player);
                if (!players.isEmpty()) sender.sendMessage(PREFIX + players.size() + " 人の画面効果を消しました (" + ended + " 個)。");
            }
            case "status" -> {
                for (Player player : targets(sender, args.length > 1 ? args[1] : null)) {
                    Map<Effect, Integer> shown = effects.shown(player);
                    var status = effects.packStatus(player);
                    sender.sendMessage(PREFIX + player.getName() + ": " + (shown.isEmpty() ? "なし" : shown.toString().toLowerCase(Locale.ROOT))
                            + " / パック (iruumark.zip): " + (status == null ? "不明" : status.name().toLowerCase(Locale.ROOT))
                            + (effects.hasShaders(player) ? "" : " → 目印を送っていない")
                            + " / クライアント: " + player.getClientBrandName());
                }
            }
            default -> effect(sender, args);
        }
        return true;
    }

    private void effect(CommandSender sender, String[] args) {
        Effect effect = Effect.find(args[0]);
        if (effect == null) {
            sender.sendMessage(PREFIX + "そんな効果はありません: " + args[0] + " (/screenfx list)");
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("off")) {
            List<Player> players = targets(sender, args.length > 2 ? args[2] : null);
            int ended = 0;
            for (Player player : players) if (effects.hide(player, effect)) ended++;
            if (!players.isEmpty()) sender.sendMessage(PREFIX + effect.id() + " を消しました (" + ended + " 人)。");
            return;
        }
        On on = parseOn(java.util.Arrays.copyOfRange(args, 1, args.length));
        if (on.error() != null) {
            sender.sendMessage(PREFIX + on.error());
            return;
        }
        List<Player> players = targets(sender, on.target());
        List<String> held = new ArrayList<>();
        for (Player player : players) {
            boolean sent;
            if (effect.positional()) {
                // (a place to try it: 4 blocks in front of the player's eyes; it runs its own time, as on a critical hit)
                org.bukkit.Location at = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(4.0));
                sent = effects.showAt(player, effect, at, on.strength());
            } else {
                sent = effects.show(player, effect, on.strength(), Math.round(on.seconds() * 20.0));
            }
            if (!sent) held.add(player.getName());
        }
        if (!players.isEmpty()) {
            sender.sendMessage(PREFIX + effect.id() + " (強さ " + on.strength() + ") を " + (players.size() - held.size()) + " 人に出しました"
                    + (effect.positional() ? " (目の前 4 ブロックから。秒は使いません)。"
                    : on.seconds() > 0 ? " (" + on.seconds() + " 秒)。" : "。/screenfx " + effect.id() + " off で消えます。"));
            if (!held.isEmpty()) {
                sender.sendMessage(PREFIX + "シェーダー入りのパック (iruumark.zip) を読み込めていない人には、目印を送っていません (読み込めたら出ます): "
                        + String.join(", ", held));
            }
            sender.sendMessage(PREFIX + "シェーダーパック (Iris・OptiFine) を使っている人には見えません (サーバーからは分かりません)。");
        }
    }

    /** The players {@code target} names (a name or a selector), or the sender; tells the sender when there are none. */
    private static List<Player> targets(CommandSender sender, String target) {
        List<Player> players = new ArrayList<>();
        if (target == null) {
            if (sender instanceof Player player) players.add(player);
            else sender.sendMessage(PREFIX + "コンソールからは、対象のプレイヤーを書いてください。");
            return players;
        }
        try {
            for (Entity entity : Bukkit.selectEntities(sender, target)) if (entity instanceof Player player) players.add(player);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(PREFIX + "対象が読めません: " + target);
            return players;
        }
        if (players.isEmpty()) sender.sendMessage(PREFIX + "対象のプレイヤーがいません: " + target);
        return players;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(PREFIX + "/screenfx <効果> [強さ 1〜255 (100 が標準)] [秒 (0 = 消すまで)] [対象] = 出す");
        sender.sendMessage(PREFIX + "/screenfx <効果> off [対象] / /screenfx off [対象] = 消す、/screenfx status [対象]、/screenfx list = 効果の一覧");
        sender.sendMessage(PREFIX + "例: /screenfx chromatic 150 10 @a (全員に 10 秒、強め)");
        sender.sendMessage(PREFIX + "impact は、目の前 4 ブロックを中心に出す (クリティカルでは、当たった所が中心。screen-effects.critical)");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (Effect effect : Effect.values()) options.add(effect.id());
            options.addAll(List.of("off", "status", "list", "help"));
        } else if (args.length == 2 && Effect.find(args[0]) != null) {
            options.addAll(List.of("100", "150", "off"));
        } else if (args.length == 3 && Effect.find(args[0]) != null && !args[1].equalsIgnoreCase("off")) {
            options.addAll(List.of("0", "5", "10"));
        } else if (args.length >= 2) {
            options.add("@a");
            for (Player player : Bukkit.getOnlinePlayers()) options.add(player.getName());
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }
}
