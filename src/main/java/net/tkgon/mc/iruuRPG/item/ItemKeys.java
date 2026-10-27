package net.tkgon.mc.iruuRPG.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemKeys {

    private final NamespacedKey itemId;
    private final NamespacedKey bonusStats;
    private final NamespacedKey bonusElements;
    private final NamespacedKey itemRev;
    private final NamespacedKey rarity;

    public ItemKeys(JavaPlugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item_id");
        this.bonusStats = new NamespacedKey(plugin, "bonus_stats");
        this.bonusElements = new NamespacedKey(plugin, "bonus_elements");
        this.itemRev = new NamespacedKey(plugin, "item_rev");
        this.rarity = new NamespacedKey(plugin, "rarity");
    }

    public NamespacedKey itemId() {
        return itemId;
    }

    /** Per-item extra stats (crafting rolls), stored as "stat-key=value;stat-key=value". */
    public NamespacedKey bonusStats() {
        return bonusStats;
    }

    /** Per-item extra element stats (crafting rolls), stored as "kind:element=value;...". */
    public NamespacedKey bonusElements() {
        return bonusElements;
    }

    /** The fingerprint of the item's yaml the item was built from (see ItemPatcher): another one means it is rebuilt. */
    public NamespacedKey itemRev() {
        return itemRev;
    }

    /** The rarity tier of a crafted item (its own: it may be above the item's base rarity). */
    public NamespacedKey rarity() {
        return rarity;
    }
}
