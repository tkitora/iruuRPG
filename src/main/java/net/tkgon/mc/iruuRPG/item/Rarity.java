package net.tkgon.mc.iruuRPG.item;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

public enum Rarity {
    COMMON(0, "Common", NamedTextColor.GRAY),
    RARE(1, "Rare", NamedTextColor.AQUA),
    EPIC(2, "Epic", NamedTextColor.LIGHT_PURPLE),
    LEGENDARY(3, "Legendary", NamedTextColor.GOLD),
    MYTHIC(4, "Mythic", NamedTextColor.RED);

    private final int tier;
    private final String displayName;
    private final NamedTextColor color;

    Rarity(int tier, String displayName, NamedTextColor color) {
        this.tier = tier;
        this.displayName = displayName;
        this.color = color;
    }

    public NamedTextColor color() {
        return color;
    }

    /** One tier up (stops at the top). */
    public Rarity up(int steps) {
        return fromTier(Math.min(MYTHIC.tier, tier + Math.max(0, steps)));
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
