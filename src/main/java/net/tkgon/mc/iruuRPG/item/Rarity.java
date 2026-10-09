package net.tkgon.mc.iruuRPG.item;

import java.util.Locale;

public enum Rarity {
    COMMON(0, "Common"),
    RARE(1, "Rare"),
    EPIC(2, "Epic"),
    LEGENDARY(3, "Legendary"),
    MYTHIC(4, "Mythic");

    private final int tier;
    private final String displayName;

    Rarity(int tier, String displayName) {
        this.tier = tier;
        this.displayName = displayName;
    }

    public int tier() {
        return tier;
    }

    public String displayName() {
        return displayName;
    }

    public static Rarity fromConfig(String raw, Rarity fallback) {
        if (raw == null || raw.isBlank()) return fallback;

        String trimmed = raw.trim();
        try {
            int tier = Integer.parseInt(trimmed);
            return fromTier(tier);
        } catch (NumberFormatException ignored) {
            String normalized = trimmed.replace('-', '_').toUpperCase(Locale.ROOT);
            for (Rarity rarity : values()) {
                if (rarity.name().equals(normalized)) {
                    return rarity;
                }
            }
            return fallback;
        }
    }

    public static Rarity fromTier(int tier) {
        for (Rarity rarity : values()) {
            if (rarity.tier == tier) return rarity;
        }
        return tier <= 0 ? COMMON : MYTHIC;
    }
}
