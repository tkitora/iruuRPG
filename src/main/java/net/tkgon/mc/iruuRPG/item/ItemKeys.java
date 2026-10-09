package net.tkgon.mc.iruuRPG.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemKeys {

    private final NamespacedKey itemId;

    public ItemKeys(JavaPlugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item_id");
    }

    public NamespacedKey itemId() {
        return itemId;
    }
}
