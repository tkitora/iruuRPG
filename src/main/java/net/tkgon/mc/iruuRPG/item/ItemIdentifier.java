package net.tkgon.mc.iruuRPG.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;

import java.util.Map;
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

    /** Stores extra stats on this one item (e.g. the random stats of a crafted item). */
    public void setBonusStats(ItemStack item, StatSet stats) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<StatType, Double> entry : stats.asMap().entrySet()) {
            if (encoded.length() > 0) encoded.append(';');
            encoded.append(entry.getKey().key()).append('=').append(entry.getValue());
        }
        meta.getPersistentDataContainer().set(keys.bonusStats(), PersistentDataType.STRING, encoded.toString());
        item.setItemMeta(meta);
    }

    /** The extra stats of this one item (empty if none). */
    public StatSet bonusStats(ItemStack item) {
        StatSet stats = new StatSet();
        if (item == null || item.getType().isAir()) return stats;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return stats;

        String encoded = meta.getPersistentDataContainer().get(keys.bonusStats(), PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) return stats;

        for (String part : encoded.split(";")) {
            String[] pair = part.split("=");
            if (pair.length != 2) continue;
            StatType.fromConfigKey(pair[0]).ifPresent(type -> {
                try {
                    stats.add(type, Double.parseDouble(pair[1]));
                } catch (NumberFormatException ignored) {
                    // a damaged entry is skipped
                }
            });
        }
        return stats;
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
