package net.tkgon.mc.iruuRPG.combat;

import java.util.Locale;

public enum DamageKind {
    PHYSICAL("physical", "物理"),
    MAGIC("magic", "魔法"),
    /** Not a weapon; shown as 防具 for armor materials and 素材 for everything else (decided from the item's material). */
    NONE("none", "防具"),
    /** Not a weapon; shown as 素材. */
    MATERIAL("material", "素材"),
    /** Not a weapon; shown as 防具. */
    ARMOR("armor", "防具"),
    /** Not shown on the item unless the item has its own `kind-label`. */
    SPECIAL("special", "特殊");

    private final String key;
    private final String displayName;

    DamageKind(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /** True for the kinds that are not a weapon at all (armor, materials, the default). */
    public boolean noDamage() {
        return this == NONE || this == MATERIAL || this == ARMOR;
    }

    public static DamageKind fromConfig(String raw, DamageKind fallback) {
        if (raw == null || raw.isBlank()) return fallback;

        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AD" -> PHYSICAL;
            case "AP" -> MAGIC;
            default -> {
                for (DamageKind kind : values()) {
                    if (kind.name().equals(normalized) || kind.key.equalsIgnoreCase(raw.trim())) {
                        yield kind;
                    }
                }
                yield fallback;
            }
        };
    }
}
