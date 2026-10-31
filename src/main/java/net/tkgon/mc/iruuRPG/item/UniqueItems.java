package net.tkgon.mc.iruuRPG.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Items that never stack (every item but materials: weapons, armor). 1.20.4 cannot set a stack size per item, so each
 * such item carries its own random mark (PDC {@code iruurpg:uid}): two of them are never alike, so they never stack,
 * also on a base that stacks by itself (glowstone, sea lantern, blaze rod). The mark is left out of the item patch's
 * fingerprint (the factory's plain item has none). Code that hands out several of an item makes them one by one with
 * {@link #pieces}, each with a fresh mark.
 */
public final class UniqueItems {

    /** The mark (the plugin's namespace: "iruurpg"). */
    public static final NamespacedKey KEY = new NamespacedKey("iruurpg", "uid");

    private UniqueItems() {
    }

    public static boolean isUnique(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.STRING);
    }

    /** Gives the meta a new mark (a new, never-stacking item). */
    public static void mark(ItemMeta meta) {
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
    }

    public static void unmark(ItemMeta meta) {
        meta.getPersistentDataContainer().remove(KEY);
    }

    /** How many of this item one slot holds: 1 for a unique item, else the material's stack size. */
    public static int maxStack(ItemStack item) {
        return isUnique(item) ? 1 : Math.max(1, item.getMaxStackSize());
    }

    /**
     * {@code amount} of the item as the stacks to hand out: unique items one by one (each with its own new mark),
     * the others in full stacks.
     */
    public static List<ItemStack> pieces(ItemStack template, int amount) {
        List<ItemStack> out = new ArrayList<>();
        int max = maxStack(template);
        boolean unique = isUnique(template);
        int left = amount;
        while (left > 0) {
            ItemStack piece = template.clone();
            int size = Math.min(max, left);
            piece.setAmount(size);
            if (unique) {
                ItemMeta meta = piece.getItemMeta();
                mark(meta);
                piece.setItemMeta(meta);
            }
            out.add(piece);
            left -= size;
        }
        return out;
    }
}
