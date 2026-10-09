package net.tkgon.mc.iruuRPG.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public final class ItemIdentifier {

    private final ItemKeys keys;

    public ItemIdentifier(ItemKeys keys) {
        this.keys = keys;
    }

    public void setItemId(ItemStack item, String itemId) {
        if (item == null || itemId == null || itemId.isBlank()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(keys.itemId(), PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
    }

    public Optional<String> itemId(ItemStack item) {
        if (item == null || item.getType().isAir()) return Optional.empty();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();

        return Optional.ofNullable(
                meta.getPersistentDataContainer().get(keys.itemId(), PersistentDataType.STRING)
        );
    }
}
