package net.tkgon.mc.iruuRPG.classsystem;

import java.util.Locale;

public enum ClassPassiveEffectType {
    STATUS,
    SKILL,
    NONE;

    public static ClassPassiveEffectType fromConfig(String raw) {
        if (raw == null || raw.isBlank()) return NONE;

        String normalized = raw.trim().replace("-", "_").toUpperCase(Locale.ROOT);
        for (ClassPassiveEffectType type : values()) {
            if (type.name().equals(normalized)) return type;
        }
        return NONE;
    }
}
