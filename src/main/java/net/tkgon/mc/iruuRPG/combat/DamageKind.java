package net.tkgon.mc.iruuRPG.combat;

import java.util.Locale;

public enum DamageKind {
    PHYSICAL("physical", "物理"),
    MAGIC("magic", "魔法"),
    NONE("none", "防具"),
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
