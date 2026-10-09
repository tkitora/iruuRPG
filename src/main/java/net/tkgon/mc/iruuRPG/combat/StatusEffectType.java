package net.tkgon.mc.iruuRPG.combat;

import net.kyori.adventure.text.format.NamedTextColor;

public enum StatusEffectType {
    BLEED("❣", NamedTextColor.RED, true),
    ADRENALINE("✦", NamedTextColor.YELLOW, false),
    MAGIC_AMPLIFY("✹", NamedTextColor.AQUA, false),
    PROTECTION("◆", NamedTextColor.BLUE, false),
    SLOW("❄", NamedTextColor.BLUE, true),
    CORROSION("☣", NamedTextColor.DARK_GREEN, true),
    DECAY("☠", NamedTextColor.GRAY, true),
    LACERATION("✧", NamedTextColor.DARK_RED, false),
    CONFUSION("❖", NamedTextColor.LIGHT_PURPLE, true),
    EXPLOSION("✷", NamedTextColor.GOLD, true);

    private final String icon;
    private final NamedTextColor color;
    private final boolean visibleOnEnemy;

    StatusEffectType(String icon, NamedTextColor color, boolean visibleOnEnemy) {
        this.icon = icon;
        this.color = color;
        this.visibleOnEnemy = visibleOnEnemy;
    }

    public String icon() {
        return icon;
    }

    public NamedTextColor color() {
        return color;
    }

    public boolean visibleOnEnemy() {
        return visibleOnEnemy;
    }
}
