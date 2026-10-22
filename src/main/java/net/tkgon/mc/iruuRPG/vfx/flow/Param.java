package net.tkgon.mc.iruuRPG.vfx.flow;

import java.util.List;

/** One argument of a flow effect: its name, kind, default and what it does (the text of {@code /vfx help}). */
public record Param(String name, Type type, String def, double min, double max, List<String> choices, String description) {

    public enum Type { NUMBER, COLOR, CHOICE }

    public static Param number(String name, double def, double min, double max, String description) {
        return new Param(name, Type.NUMBER, trim(def), min, max, List.of(), description);
    }

    public static Param color(String name, String def, String description) {
        return new Param(name, Type.COLOR, def, 0.0, 0.0, List.of(), description);
    }

    public static Param choice(String name, String def, List<String> choices, String description) {
        return new Param(name, Type.CHOICE, def, 0.0, 0.0, choices, description);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /** "name=default" with the range or the choices, for the usage line. */
    public String shortText() {
        return switch (type) {
            case NUMBER -> name + " (" + trim(min) + "〜" + trim(max) + ", 既定 " + def + ")";
            case COLOR -> name + " (色 #rrggbb か r,g,b, 既定 " + def + ")";
            case CHOICE -> name + " (" + String.join(" | ", choices) + ", 既定 " + def + ")";
        };
    }
}
