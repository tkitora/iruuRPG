package net.tkgon.mc.iruuRPG.vfx;

import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Color;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The gradients effects use, made once: for each element a gradient from a deep shade through the element's own color to a
 * bright core (0 = outside / old, 1 = center / new), a purple one, and each element mixed with purple (the mage).
 */
public final class Palettes {

    private static final Map<Element, Gradient> ELEMENTS = new EnumMap<>(Element.class);
    private static final Map<Element, Gradient> PURPLE_MIXED = new EnumMap<>(Element.class);
    /** Deep violet through purple to a pale lavender core. */
    public static final Gradient PURPLE = Gradient.of(Color.fromRGB(70, 20, 150), Color.fromRGB(150, 70, 235), Color.fromRGB(235, 200, 255));
    /** Iron: dark steel through light steel to white. */
    public static final Gradient STEEL = Gradient.of(Color.fromRGB(120, 130, 150), Color.fromRGB(215, 222, 235), Color.fromRGB(255, 255, 255));

    static {
        for (Element element : Element.values()) {
            Color base = base(element);
            Gradient own = Gradient.of(shade(base, 0.45), base, brighten(base, 0.55));
            ELEMENTS.put(element, own);
            PURPLE_MIXED.put(element, own.mix(PURPLE, 0.6));
        }
    }

    private static final ConcurrentHashMap<Integer, Gradient> FROM_COLOR = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Gradient> MIXED = new ConcurrentHashMap<>();

    private Palettes() {
    }

    /** A gradient built from one base color: a deep shade of it, the color itself, a bright core. Made once per color. */
    public static Gradient fromColor(Color base) {
        return FROM_COLOR.computeIfAbsent(base.asRGB(), rgb -> Gradient.of(shade(base, 0.5), base, brighten(base, 0.6)));
    }

    /** The element's gradient with {@code purple} (0 - 1, in steps of 0.1) of purple mixed in. Made once per element and step. */
    public static Gradient mixed(Element element, double purple) {
        int step = (int) Math.round(Curves.clamp01(purple) * 10.0);
        return MIXED.computeIfAbsent(element.ordinal() * 16 + step, key -> of(element).mix(PURPLE, step / 10.0));
    }

    /** The element's own color. */
    public static Color base(Element element) {
        return switch (element) {
            case RED -> Color.fromRGB(255, 70, 55);
            case BLUE -> Color.fromRGB(80, 140, 255);
            case WHITE -> Color.fromRGB(235, 238, 250);
            case GREEN -> Color.fromRGB(70, 225, 110);
            case ORANGE -> Color.fromRGB(255, 160, 45);
        };
    }

    public static Gradient of(Element element) {
        return ELEMENTS.get(element);
    }

    /** The element's gradient mixed with purple (60% purple). */
    public static Gradient purple(Element element) {
        return PURPLE_MIXED.get(element);
    }

    private static Color shade(Color color, double amount) {
        return Color.fromRGB((int) (color.getRed() * (1.0 - amount)), (int) (color.getGreen() * (1.0 - amount)), (int) (color.getBlue() * (1.0 - amount)));
    }

    private static Color brighten(Color color, double amount) {
        return Color.fromRGB((int) (color.getRed() + (255 - color.getRed()) * amount), (int) (color.getGreen() + (255 - color.getGreen()) * amount),
                (int) (color.getBlue() + (255 - color.getBlue()) * amount));
    }
}
