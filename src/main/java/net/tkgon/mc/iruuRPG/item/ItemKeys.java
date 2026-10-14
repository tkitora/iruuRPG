package net.tkgon.mc.iruuRPG.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemKeys {

    private final NamespacedKey itemId;
    private final NamespacedKey bonusStats;

    public ItemKeys(JavaPlugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item_id");
        this.bonusStats = new NamespacedKey(plugin, "bonus_stats");
    }

    public NamespacedKey itemId() {
        return itemId;
    }

    /** Per-item extra stats (crafting rolls), stored as "stat-key=value;stat-key=value". */
    public NamespacedKey bonusStats() {
        return bonusStats;
    }
}
