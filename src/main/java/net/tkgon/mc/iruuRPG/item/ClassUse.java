package net.tkgon.mc.iruuRPG.item;

import java.util.Locale;

/**
 * What a weapon is to a class (the item's {@code classes:}): {@code UNUSABLE} (red: the class cannot use it),
 * {@code USABLE} (white: it can, with ordinary attacks) or {@code FAVORITE} (yellow: it can, and the class's combo comes
 * with it).
 */
public enum ClassUse {
    UNUSABLE,
    USABLE,
    FAVORITE;

    /** Reads a value of the item yml (English, Japanese or the color); null when it is not one of them. */
    public static ClassUse parse(Object raw) {
        if (raw == null) return null;
        String text = raw.toString().trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "favorite", "favourite", "good", "得意", "yellow", "gold", "yellowgreen" -> FAVORITE;
            case "usable", "ok", "white", "使用可", "使用可能", "true", "yes" -> USABLE;
            case "unusable", "no", "red", "none", "使用不可", "不可", "false" -> UNUSABLE;
            default -> null;
        };
    }
}
