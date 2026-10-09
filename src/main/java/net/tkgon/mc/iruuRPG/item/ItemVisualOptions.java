package net.tkgon.mc.iruuRPG.item;

import org.bukkit.Color;

public record ItemVisualOptions(
        boolean glint,
        String skinTexture,
        Color leatherColor
) {
    public static final ItemVisualOptions NONE = new ItemVisualOptions(false, null, null);

    public ItemVisualOptions(boolean glint, String skinTexture) {
        this(glint, skinTexture, null);
    }

    public ItemVisualOptions {
        skinTexture = skinTexture == null || skinTexture.isBlank() ? null : skinTexture.trim();
    }

    public boolean hasSkinTexture() {
        return skinTexture != null && !skinTexture.isBlank();
    }

    public boolean hasLeatherColor() {
        return leatherColor != null;
    }
}
