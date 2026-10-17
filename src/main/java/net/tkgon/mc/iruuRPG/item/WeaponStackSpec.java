package net.tkgon.mc.iruuRPG.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;

/**
 * The stacks of a special weapon (the `stacks:` section of an item): every time the weapon's skill is cast it gains
 * {@code gain} stacks of its own; at {@code max} the stacks are spent and the skill {@code onMax} is cast as well.
 * Stacks fade away when the weapon's skill is not cast for {@code decaySeconds} (0 = they never fade).
 * {@code display} is what the player sees (a subtitle) and hears when the count changes.
 */
public record WeaponStackSpec(String name, int gain, int max, String onMax, double decaySeconds, Display display) {

    public WeaponStackSpec {
        name = name == null || name.isBlank() ? "スタック" : name;
        gain = Math.max(1, Math.min(20, gain));
        max = Math.max(2, Math.min(30, max));
        onMax = onMax == null ? "" : onMax.trim().toLowerCase(Locale.ROOT);
        decaySeconds = Math.max(0.0, Math.min(300.0, decaySeconds));
        display = display == null ? Display.defaults() : display;
    }

    /**
     * What is shown. Format variables: {name} {bar} {count} {max} {percent} ({percent} is 0 - 100 without a % sign).
     * {@code maxFormat} is the text when the stacks are full (empty: the normal format). {@code title} is shown as the
     * main title (empty: none). All times are in seconds.
     */
    public record Display(String format, String filled, String empty, TextColor filledColor, TextColor emptyColor,
                          TextColor textColor, String maxFormat, TextColor maxColor, boolean bold, String title,
                          double seconds, double fadeIn, double fadeOut, boolean sound) {

        public static final int MAX_FORMAT_LENGTH = 60;
        public static final int MAX_SYMBOL_LENGTH = 4;

        public static Display defaults() {
            return new Display("{name} {bar} {count}/{max}", "●", "○", TextColor.color(0xffaa00), TextColor.color(0x555555),
                    TextColor.color(0xffff55), "{name} {bar} MAX!", TextColor.color(0xffaa00), false, "", 0.9, 0.0, 0.25, true);
        }

        /** The subtitle for {@code count} stacks of {@code max}; the full one when {@code full}. */
        public Component subtitle(String name, int count, int max, boolean full) {
            String pattern = full && !maxFormat.isEmpty() ? maxFormat : format;
            return render(pattern, name, count, max, full);
        }

        /** The main title, or null when none is set. */
        public Component mainTitle(String name, int count, int max, boolean full) {
            if (title.isEmpty()) return null;
            return render(title, name, count, max, full);
        }

        private Component render(String pattern, String name, int count, int max, boolean full) {
            TextColor text = full ? maxColor : textColor;
            TextColor filledShade = full ? maxColor : filledColor;
            boolean strong = bold || full;
            Component result = Component.empty();
            StringBuilder literal = new StringBuilder();
            int index = 0;
            while (index < pattern.length()) {
                char ch = pattern.charAt(index);
                int close = ch == '{' ? pattern.indexOf('}', index) : -1;
                String variable = close > index ? pattern.substring(index + 1, close).toLowerCase(Locale.ROOT) : null;
                Component piece = null;
                if (variable != null) {
                    int shown = Math.max(0, Math.min(max, count));
                    piece = switch (variable) {
                        case "name" -> styled(name, text, strong);
                        case "bar" -> styled(filled.repeat(shown), filledShade, strong)
                                .append(styled(empty.repeat(Math.max(0, max - shown)), emptyColor, strong));
                        case "count" -> styled(String.valueOf(shown), text, strong);
                        case "max" -> styled(String.valueOf(max), text, strong);
                        case "percent" -> styled(String.valueOf(max <= 0 ? 0 : (int) Math.floor(100.0 * shown / max)), text, strong);
                        default -> null;
                    };
                }
                if (piece == null) {
                    literal.append(ch);
                    index++;
                    continue;
                }
                if (literal.length() > 0) {
                    result = result.append(styled(literal.toString(), text, strong));
                    literal.setLength(0);
                }
                result = result.append(piece);
                index = close + 1;
            }
            if (literal.length() > 0) result = result.append(styled(literal.toString(), text, strong));
            return result;
        }

        private static Component styled(String text, TextColor color, boolean bold) {
            return Component.text(text, color).decoration(TextDecoration.BOLD, bold).decoration(TextDecoration.ITALIC, false);
        }
    }
}
