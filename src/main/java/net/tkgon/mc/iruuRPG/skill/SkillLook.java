package net.tkgon.mc.iruuRPG.skill;

import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.List;

/**
 * What a skill looks like: up to three colors (body / edge / core), the sparkle particle, size and width multipliers,
 * the sound style and the skill's own sounds (null: the style's). A null color list or particle means "from the
 * caster's element" (the old look).
 */
public record SkillLook(List<Color> colors, Particle particle, double size, double width, SoundStyle sound, SkillSounds sounds) {

    public static final double MIN_SIZE = 0.2;
    public static final double MAX_SIZE = 3.0;
    public static final double MIN_WIDTH = 0.2;
    public static final double MAX_WIDTH = 3.0;

    public SkillLook {
        colors = colors == null ? null : List.copyOf(colors);
        size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
    }

    /** The look of a skill that sets nothing: colors and sparks from the element. */
    public static SkillLook plain() {
        return new SkillLook(null, null, 1.0, 1.0, null, null);
    }

    /** Body color: the first color, or {@code fallback} (the element's color) when none is set. */
    public Color body(Color fallback) {
        return colors == null || colors.isEmpty() ? fallback : colors.get(0);
    }

    /** Edge color: the second color, or the body color made lighter. */
    public Color edge(Color fallback) {
        if (colors == null || colors.isEmpty()) return fallback;
        return colors.size() > 1 ? colors.get(1) : lighter(colors.get(0), 0.35);
    }

    /** Core color: the third color, or the body color made much lighter. */
    public Color core(Color fallback) {
        if (colors == null || colors.isEmpty()) return fallback;
        return colors.size() > 2 ? colors.get(2) : lighter(colors.get(0), 0.75);
    }

    public Particle sparkOr(Element element, java.util.function.Function<Element, Particle> byElement) {
        return particle != null ? particle : byElement.apply(element);
    }

    private static Color lighter(Color color, double toWhite) {
        return Color.fromRGB(
                (int) Math.round(color.getRed() + (255 - color.getRed()) * toWhite),
                (int) Math.round(color.getGreen() + (255 - color.getGreen()) * toWhite),
                (int) Math.round(color.getBlue() + (255 - color.getBlue()) * toWhite));
    }
}
