package net.tkgon.mc.iruuRPG.item;

import java.util.List;
import java.util.Locale;

/**
 * The classes in the order the marks of a weapon are written, with the class id (the internal one) and the mark: the
 * emoji at the end of the class's name (keep them the same as the {@code name:} of classes/*.yml, without U+FE0F).
 */
public enum ClassMark {
    WARRIOR("warrior", "🗡", "warrior", "戦士"),
    MAGE("mage", "📖", "mage", "メイジ"),
    RANGER("gifted", "🏹", "ranger", "レンジャー"),
    CLERIC("healer", "✞", "cleric", "クレリック"),
    ASSASSIN("assassin", "⚔", "assassin", "アサシン");

    private final String classId;
    private final String mark;
    private final String alias;
    private final String japanese;

    ClassMark(String classId, String mark, String alias, String japanese) {
        this.classId = classId;
        this.mark = mark;
        this.alias = alias;
        this.japanese = japanese;
    }

    public String classId() {
        return classId;
    }

    public String mark() {
        return mark;
    }

    /**
     * What a weapon of this attack type is to a class when its yml does not say (the default of `classes:`): a ranged
     * weapon is favorite to the mage and the ranger, usable by the cleric, unusable by the warrior and the assassin; a melee
     * weapon is favorite to the warrior, the cleric and the assassin, usable by the others; a deployed one is favorite to
     * the ranger, usable by the others.
     */
    public static ClassUse defaultUse(net.tkgon.mc.iruuRPG.combat.AttackType type, String classId) {
        if (classId == null) return ClassUse.USABLE;
        return switch (type) {
            case RANGE -> switch (classId) {
                case "mage", "gifted" -> ClassUse.FAVORITE;
                case "warrior", "assassin" -> ClassUse.UNUSABLE;
                default -> ClassUse.USABLE;
            };
            case MELEE -> switch (classId) {
                case "warrior", "healer", "assassin" -> ClassUse.FAVORITE;
                default -> ClassUse.USABLE;
            };
            case DEPLOY -> "gifted".equals(classId) ? ClassUse.FAVORITE : ClassUse.USABLE;
            case SPECIAL -> ClassUse.USABLE;
        };
    }

    public static List<ClassMark> order() {
        return List.of(values());
    }

    /** The internal class id for what an item yml calls a class (the id, ranger / cleric, or the Japanese name); null if unknown. */
    public static String classIdOf(String key) {
        if (key == null) return null;
        String text = key.trim().toLowerCase(Locale.ROOT);
        for (ClassMark entry : values()) {
            if (entry.classId.equals(text) || entry.alias.equals(text) || entry.japanese.equals(key.trim())) return entry.classId;
        }
        return null;
    }
}
