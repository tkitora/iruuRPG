package net.tkgon.mc.iruuRPG.combat;

import java.util.Locale;

public enum AttackType {
    MELEE("melee", "近接"),
    RANGE("range", "遠距離"),
    DEPLOY("deploy", "設置"),
    SPECIAL("special", "特殊");

    private final String key;
    private final String displayName;

    AttackType(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static AttackType fromConfig(String raw, AttackType fallback) {
        if (raw == null || raw.isBlank()) return fallback;

        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MELEES", "MELEE_AOE" -> MELEE;
            case "RANGES", "RANGE_AOE" -> RANGE;
            case "LANTERN", "TURRET", "DEPLOYED" -> DEPLOY;
            default -> {
                for (AttackType type : values()) {
                    if (type.name().equals(normalized) || type.key.equalsIgnoreCase(raw.trim())) {
                        yield type;
                    }
                }
                yield fallback;
            }
        };
    }
}
