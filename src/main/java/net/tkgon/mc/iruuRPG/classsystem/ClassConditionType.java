package net.tkgon.mc.iruuRPG.classsystem;

import java.util.Locale;

/** When a milestone's stat bonus is active. */
public enum ClassConditionType {
    /** Always on. */
    ALWAYS,
    /** While current HP is below half of max HP. */
    HP_BELOW_HALF,
    /** While the player has not moved (the server cannot read WASD, so position is used). */
    STATIONARY;

    public static ClassConditionType fromConfig(String raw) {
        if (raw == null || raw.isBlank()) return ALWAYS;

        String normalized = raw.trim().replace("-", "_").toUpperCase(Locale.ROOT);
        for (ClassConditionType type : values()) {
            if (type.name().equals(normalized)) return type;
        }
        return ALWAYS;
    }
}
