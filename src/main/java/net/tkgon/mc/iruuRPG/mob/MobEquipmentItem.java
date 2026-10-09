package net.tkgon.mc.iruuRPG.mob;

import org.bukkit.Material;
import org.bukkit.Color;

public record MobEquipmentItem(
        String itemId,
        Material material,
        double dropChance,
        Color leatherColor
) {
    public MobEquipmentItem(String itemId, Material material, double dropChance) {
        this(itemId, material, dropChance, null);
    }

    public boolean hasItem() {
        return itemId != null && !itemId.isBlank();
    }

    public boolean hasMaterial() {
        return material != null && material != Material.AIR;
    }

    public boolean hasLeatherColor() {
        return leatherColor != null;
    }
}
