package net.tkgon.mc.iruuRPG.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Keeps the items in the game up to date with the item yaml (see {@link ItemPatcher}). The fingerprints of the
 * definitions and a version number are kept in plugins/iruuRPG/item-patch.yml; the version goes up whenever a
 * definition that existed before changed. Then:
 * <ul>
 *   <li>at once, the market, the loaded storages and the players online are patched (only the changed items);</li>
 *   <li>a player whose last checked version (in the player's data) is older is checked when joining, once per version
 *       (also the players patched at once: a second look in case something was missed);</li>
 *   <li>a storage is patched whenever it is read from its file, and a placed container (chest, barrel, shulker box...)
 *       whenever it is opened.</li>
 * </ul>
 */
public final class ItemPatchService implements Listener {

    /** Something else that keeps items (the market, the storages): patches its items with the function, answers how many changed. */
    public interface Keeper {
        int patchItems(Function<ItemStack, ItemStack> patch);
    }

    private final JavaPlugin plugin;
    private final ItemPatcher patcher;
    private final File file;
    private final NamespacedKey checkedKey;
    private final Set<Keeper> keepers = new LinkedHashSet<>();
    private Consumer<Player> afterPlayerPatch = player -> { };
    private int version;

    public ItemPatchService(JavaPlugin plugin, ItemPatcher patcher) {
        this.plugin = plugin;
        this.patcher = patcher;
        this.file = new File(plugin.getDataFolder(), "item-patch.yml");
        this.checkedKey = new NamespacedKey(plugin, "item_patch_version");
    }

    public void addKeeper(Keeper keeper) {
        keepers.add(keeper);
    }

    /** Run for a player whose items changed (stats and bars follow the new items). */
    public void setAfterPlayerPatch(Consumer<Player> afterPlayerPatch) {
        this.afterPlayerPatch = afterPlayerPatch == null ? player -> { } : afterPlayerPatch;
    }

    public ItemPatcher patcher() {
        return patcher;
    }

    public int version() {
        return version;
    }

    /**
     * Call after the items (and the skills and config their text uses) are loaded: compares the fingerprints with the
     * last ones and, if a definition changed, moves the version up and patches what can be patched at once.
     * Answers the ids that changed.
     */
    public Set<String> refresh() {
        patcher.setEnabled(plugin.getConfig().getBoolean("item-patch.enabled", true));
        Map<String, String> now = patcher.computeFingerprints();
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : null;
        Map<String, String> before = new HashMap<>();
        if (yaml != null) {
            version = yaml.getInt("version", 0);
            ConfigurationSection items = yaml.getConfigurationSection("items");
            if (items != null) for (String id : items.getKeys(false)) before.put(id, items.getString(id, ""));
        }
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : now.entrySet()) {
            // the first time (no file yet) every item counts as changed: the items made before carry no fingerprint
            String old = before.get(entry.getKey());
            if (yaml == null || (old != null && !old.equals(entry.getValue()))) changed.add(entry.getKey());
        }
        patcher.setFingerprints(now);
        if (!changed.isEmpty()) version++;
        if (!changed.isEmpty() || !now.equals(before)) save(now);
        if (changed.isEmpty()) return changed;

        plugin.getLogger().info("items: " + changed.size() + " item definition(s) changed, item version " + version
                + " (" + String.join(", ", changed.stream().limit(10).toList()) + (changed.size() > 10 ? ", ..." : "") + ")");
        if (!patcher.enabled()) {
            plugin.getLogger().info("items: item-patch.enabled is false; the items are left as they are");
            return changed;
        }
        patcher.takeCounts();
        for (Keeper keeper : keepers) keeper.patchItems(patcher::patch);
        ItemPatcher.Counts stored = patcher.takeCounts();
        int players = 0;
        ItemPatcher.Counts online = new ItemPatcher.Counts();
        for (Player player : Bukkit.getOnlinePlayers()) {
            // patched now; the version is not marked, so the next join looks once more
            if (patchPlayer(player, online) > 0) players++;
        }
        plugin.getLogger().info("items: market and storages: " + stored + "; players online (" + players + "): " + online);
        return changed;
    }

    private void save(Map<String, String> fingerprints) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", version);
        for (Map.Entry<String, String> entry : new java.util.TreeMap<>(fingerprints).entrySet()) yaml.set("items." + entry.getKey(), entry.getValue());
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("items: could not save " + file.getName() + ": " + e.getMessage());
        }
    }

    /** Patches the player's inventory (armor and off hand too), the item on the cursor, the vanilla ender chest and an open container. */
    public int patchPlayer(Player player) {
        return patchPlayer(player, null);
    }

    /** {@code total}: the counts are added there (one log line for all), else logged for this player. */
    private int patchPlayer(Player player, ItemPatcher.Counts total) {
        if (!patcher.enabled()) return 0;
        patcher.takeCounts();
        int changed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        int count = patcher.patchAll(contents);
        if (count > 0) player.getInventory().setContents(contents);
        changed += count;

        ItemStack cursor = patcher.patch(player.getItemOnCursor());
        if (cursor != null) {
            player.setItemOnCursor(cursor);
            changed++;
        }
        changed += patchInventory(player.getEnderChest());
        Inventory top = player.getOpenInventory().getTopInventory();
        if (isPlaced(top.getHolder(false))) changed += patchInventory(top);

        ItemPatcher.Counts counts = patcher.takeCounts();
        if (changed > 0) afterPlayerPatch.accept(player);
        // the player is told only of the items that look different; stamping the fingerprint on an item changes nothing they see
        if (counts.rebuilt > 0) {
            player.sendMessage(Component.text("[アイテム] " + counts.rebuilt + " 個のアイテムを、最新の内容に更新した。", NamedTextColor.GRAY));
        }
        if (total != null) total.add(counts);
        else if (counts.any()) plugin.getLogger().info("items: " + player.getName() + ": " + counts);
        return changed;
    }

    /** Patches item arrays read from somewhere (a storage file), logging what was done; answers how many stacks changed. */
    public int patchLoaded(String where, java.util.List<ItemStack[]> arrays) {
        if (!patcher.enabled()) return 0;
        patcher.takeCounts();
        int changed = 0;
        for (ItemStack[] array : arrays) changed += patcher.patchAll(array);
        ItemPatcher.Counts counts = patcher.takeCounts();
        if (counts.any()) plugin.getLogger().info("items: " + where + ": " + counts);
        return changed;
    }

    private int patchInventory(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        int count = patcher.patchAll(contents);
        if (count > 0) inventory.setContents(contents);
        return count;
    }

    private static boolean isPlaced(InventoryHolder holder) {
        return holder instanceof Container || holder instanceof DoubleChest;
    }

    // ---- when a player joins: once per version ---------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // a moment later, after the other join work (the profile, the menu item) is done
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Integer checked = player.getPersistentDataContainer().get(checkedKey, PersistentDataType.INTEGER);
            if (checked != null && checked >= version) return;
            if (!patcher.enabled()) return; // checked again once the patch is on
            patchPlayer(player);
            player.getPersistentDataContainer().set(checkedKey, PersistentDataType.INTEGER, version);
        }, 20L);
    }

    // ---- a placed container: when it is opened ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!patcher.enabled()) return;
        // only the container opened (no look through the world)
        Inventory top = event.getInventory();
        if (!isPlaced(top.getHolder(false))) return;
        patcher.takeCounts();
        patchInventory(top);
        ItemPatcher.Counts counts = patcher.takeCounts();
        if (counts.any()) plugin.getLogger().info("items: a container opened by " + event.getPlayer().getName() + ": " + counts);
    }
}
