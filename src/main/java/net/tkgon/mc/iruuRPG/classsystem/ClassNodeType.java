package net.tkgon.mc.iruuRPG.classsystem;

import java.util.Locale;

public enum ClassNodeType {
    PASSIVE,
    ACTIVE;

    public static ClassNodeType fromConfig(String raw) {
        if (raw == null || raw.isBlank()) return PASSIVE;

        String normalized = raw.trim().replace("-", "_").toUpperCase(Locale.ROOT);
        for (ClassNodeType type : values()) {
            if (type.name().equals(normalized)) return type;
        }
        return PASSIVE;
    }
}
