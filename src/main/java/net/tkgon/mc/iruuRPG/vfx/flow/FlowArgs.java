package net.tkgon.mc.iruuRPG.vfx.flow;

import org.bukkit.Color;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The arguments of a flow effect, read from the words of a command: the words without an "=" are taken in the order of the effect's
 * parameters (arg1, arg2, ...), those written {@code name=value} are put where they belong; whatever is missing is the default.
 */
public final class FlowArgs {

    private final Map<String, Object> values = new HashMap<>();

    private FlowArgs() {
    }

    /** Reads {@code words} for {@code params}; throws {@link IllegalArgumentException} (with a message to show) when a word cannot be read. */
    public static FlowArgs parse(List<Param> params, List<String> words) {
        FlowArgs args = new FlowArgs();
        Map<String, String> given = new HashMap<>();
        int position = 0;
        for (String word : words) {
            int equals = word.indexOf('=');
            if (equals > 0) {
                String name = word.substring(0, equals).toLowerCase(Locale.ROOT);
                if (params.stream().noneMatch(param -> param.name().equals(name))) throw new IllegalArgumentException("そんな引数はありません: " + name);
                given.put(name, word.substring(equals + 1));
            } else {
                if (position >= params.size()) throw new IllegalArgumentException("引数が多すぎます (" + params.size() + "個まで)");
                given.put(params.get(position).name(), word);
                position++;
            }
        }
        for (Param param : params) {
            String text = given.getOrDefault(param.name(), param.def());
            args.values.put(param.name(), switch (param.type()) {
                case NUMBER -> number(param, text);
                case COLOR -> color(param, text);
                case CHOICE -> choice(param, text);
            });
        }
        return args;
    }

    private static Double number(Param param, String text) {
        try {
            double value = Double.parseDouble(text);
            if (Double.isNaN(value) || Double.isInfinite(value)) throw new NumberFormatException();
            return Math.max(param.min(), Math.min(param.max(), value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(param.name() + " は数字で指定してください: " + text);
        }
    }

    /** "#rrggbb", "rrggbb" or "r,g,b" (0 - 255). */
    public static Color parseColor(String text) {
        String clean = text.trim();
        if (clean.contains(",")) {
            String[] parts = clean.split(",");
            if (parts.length != 3) throw new IllegalArgumentException("色は r,g,b か #rrggbb で指定してください: " + text);
            try {
                return Color.fromRGB(channel(parts[0]), channel(parts[1]), channel(parts[2]));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("色は r,g,b か #rrggbb で指定してください: " + text);
            }
        }
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.length() != 6) throw new IllegalArgumentException("色は r,g,b か #rrggbb で指定してください: " + text);
        try {
            return Color.fromRGB(Integer.parseInt(clean, 16));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("色は r,g,b か #rrggbb で指定してください: " + text);
        }
    }

    private static int channel(String text) {
        return Math.max(0, Math.min(255, Integer.parseInt(text.trim())));
    }

    private static Color color(Param param, String text) {
        return parseColor(text);
    }

    private static String choice(Param param, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (!param.choices().contains(lower)) throw new IllegalArgumentException(param.name() + " は " + String.join(" / ", param.choices()) + " のどれかです: " + text);
        return lower;
    }

    public double number(String name) {
        return (Double) values.get(name);
    }

    public int integer(String name) {
        return (int) Math.round(number(name));
    }

    public Color color(String name) {
        return (Color) values.get(name);
    }

    public String choice(String name) {
        return (String) values.get(name);
    }
}
