package net.tkgon.mc.iruuRPG.item;

import java.util.Locale;

public enum ItemSkillType {
    NONE("none", ""),
    SWEEP("sweep", "凪払い"),
    HEAL("heal", "回復"),
    DASH("dash", "疾走"),
    STRIKE("strike", "強襲"),
    SLASH("slash", "斬撃");

    private final String key;
    private final String displayName;

    ItemSkillType(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public boolean enabled() {
        return this != NONE;
    }

    public static ItemSkillType fromConfig(String raw) {
        if (raw == null || raw.isBlank()) return NONE;

        String normalized = raw.trim().replace("-", "_").toUpperCase(Locale.ROOT);
        for (ItemSkillType type : values()) {
            if (type.name().equals(normalized) || type.key.equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }

        return NONE;
    }
}
