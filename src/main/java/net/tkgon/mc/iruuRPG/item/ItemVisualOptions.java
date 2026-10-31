package net.tkgon.mc.iruuRPG.item;

import org.bukkit.Color;

/**
 * How an item looks beyond its material: the enchantment glint, a player head's skin, a leather armor's color, and
 * {@code texture} (the yml's {@code texture:}): the vanilla item whose look the resource pack puts over the item's base
 * material (by custom model data; null = the base material's own look).
 */
public record ItemVisualOptions(
        boolean glint,
        String skinTexture,
        Color leatherColor,
        String texture
) {
    public static final ItemVisualOptions NONE = new ItemVisualOptions(false, null, null, null);

    public ItemVisualOptions(boolean glint, String skinTexture) {
        this(glint, skinTexture, null, null);
    }

    public ItemVisualOptions(boolean glint, String skinTexture, Color leatherColor) {
        this(glint, skinTexture, leatherColor, null);
    }

    public ItemVisualOptions {
        skinTexture = skinTexture == null || skinTexture.isBlank() ? null : skinTexture.trim();
        texture = texture == null || texture.isBlank() ? null : texture.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean hasTexture() {
        return texture != null;
    }

    public boolean hasSkinTexture() {
        return skinTexture != null && !skinTexture.isBlank();
    }

    public boolean hasLeatherColor() {
        return leatherColor != null;
    }
}
