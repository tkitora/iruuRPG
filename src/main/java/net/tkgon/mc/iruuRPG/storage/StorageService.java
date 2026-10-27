package net.tkgon.mc.iruuRPG.storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Personal storage behind the ender chests around the world: pages of 45 slots, the first ones free and the next ones
 * unlocked with gold (storage.* in config.yml). One file per player (plugins/iruuRPG/storage/&lt;uuid&gt;.yml), loaded
 * when first opened and saved whenever a page changes hands, is closed, or every half minute while open.
 */
public final class StorageService {

    public static final int PAGE_SLOTS = 45;

    /** One player's storage: how many pages are unlocked and the items of each page. */
    public static final class PlayerStorage {
        private int unlocked;
        private final List<ItemStack[]> pages = new ArrayList<>();

        public int unlocked() {
            return unlocked;
        }

        /** The items of a page (always {@link #PAGE_SLOTS} entries; null = empty). */
        public ItemStack[] page(int index) {
            while (pages.size() <= index) pages.add(new ItemStack[PAGE_SLOTS]);
            return pages.get(index);
        }

        public void setPage(int index, ItemStack[] items) {
            ItemStack[] copy = new ItemStack[PAGE_SLOTS];
            for (int slot = 0; slot < PAGE_SLOTS && slot < items.length; slot++) {
                ItemStack item = items[slot];
                copy[slot] = item == null || item.getType().isAir() ? null : item.clone();
            }
            while (pages.size() <= index) pages.add(new ItemStack[PAGE_SLOTS]);
            pages.set(index, copy);
        }
    }

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final File folder;
    private final Map<UUID, PlayerStorage> loaded = new HashMap<>();
    /** The item patch (items built again from a newer yaml), applied whenever a storage is read from its file. */
    private java.util.function.Function<ItemStack, ItemStack> patch;

    public StorageService(JavaPlugin plugin, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.folder = new File(plugin.getDataFolder(), "storage");
    }

    // ---- settings ---------------------------------------------------------------------------------------------------

    public int maxPages() {
        return Math.max(1, plugin.getConfig().getInt("storage.max-pages", 6));
    }

    public int freePages() {
        return Math.max(1, Math.min(maxPages(), plugin.getConfig().getInt("storage.free-pages", 1)));
    }

    /**
     * Price of unlocking page number {@code page} (1-based): storage.price-base for the first paid page, then each
     * next page costs storage.price-growth times the one before (a gentle exponential, default x1.8), rounded to
     * the nearest 50. With the defaults: 500, 900, 1600, 2900, 5250.
     */
    public long priceOf(int page) {
        if (page <= freePages()) return 0L;
        double base = Math.max(0L, plugin.getConfig().getLong("storage.price-base", 500L));
        double growth = Math.max(1.0, plugin.getConfig().getDouble("storage.price-growth", 1.8));
        double price = base * Math.pow(growth, page - freePages() - 1);
        return Math.max(0L, Math.round(price / 50.0) * 50L);
    }

    public String currency() {
        return plugin.getConfig().getString("quest.currency-name", "ゴールド");
    }

    public long gold(Player player) {
        return profileManager.getOrCreate(player).currency();
    }

    // ---- data -------------------------------------------------------------------------------------------------------

    public PlayerStorage of(Player player) {
        return of(player.getUniqueId());
    }

    public PlayerStorage of(UUID id) {
        PlayerStorage storage = loaded.get(id);
        if (storage != null) return storage;
        storage = load(id);
        loaded.put(id, storage);
        if (patch != null && patch(storage, patch) > 0) save(id);
        return storage;
    }

    /** Unlocks the next page for gold. Returns an error text, or null when bought. */
    public String unlockNext(Player player) {
        PlayerStorage storage = of(player);
        if (storage.unlocked >= maxPages()) return "もう全部のページを解放している。";
        int next = storage.unlocked + 1;
        long price = priceOf(next);
        PlayerProfile profile = profileManager.getOrCreate(player);
        if (profile.currency() < price) return currency() + "が足りない。(必要: " + price + " / 所持: " + profile.currency() + ")";

        profile.setCurrency(profile.currency() - price);
        profileManager.save(player);
        storage.unlocked = next;
        save(player.getUniqueId());
        player.sendMessage(Component.text("[ストレージ] ページ " + next + " を解放した。(-" + price + " " + currency() + ")", NamedTextColor.GREEN));
        plugin.getLogger().info("storage: " + player.getName() + " unlocked page " + next + " for " + price);
        return null;
    }

    private PlayerStorage load(UUID id) {
        PlayerStorage storage = new PlayerStorage();
        storage.unlocked = freePages();
        File file = new File(folder, id + ".yml");
        if (!file.exists()) return storage;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        storage.unlocked = Math.max(freePages(), Math.min(maxPages(), yaml.getInt("unlocked", freePages())));
        ConfigurationSection pages = yaml.getConfigurationSection("pages");
        if (pages != null) {
            for (String key : pages.getKeys(false)) {
                int index;
                try {
                    index = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                ConfigurationSection page = pages.getConfigurationSection(key);
                if (page == null || index < 0 || index >= maxPages()) continue;
                ItemStack[] items = new ItemStack[PAGE_SLOTS];
                for (String slotKey : page.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        ItemStack item = page.getItemStack(slotKey);
                        if (item == null) {
                            plugin.getLogger().warning("storage: could not read an item in " + file.getName() + " page " + key + " slot " + slotKey);
                        } else if (slot >= 0 && slot < PAGE_SLOTS) {
                            items[slot] = item;
                        }
                    } catch (NumberFormatException ignored) {
                        // a damaged key is skipped
                    }
                }
                storage.setPage(index, items);
            }
        }
        return storage;
    }

    public boolean save(UUID id) {
        PlayerStorage storage = loaded.get(id);
        if (storage == null) return true;

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("unlocked", storage.unlocked);
        for (int index = 0; index < storage.pages.size(); index++) {
            ItemStack[] items = storage.pages.get(index);
            for (int slot = 0; slot < items.length; slot++) {
                if (items[slot] != null) yaml.set("pages." + index + "." + slot, items[slot]);
            }
        }
        try {
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("storage: could not create " + folder.getAbsolutePath());
                return false;
            }
            // write to a temporary file first, so a stop in the middle never leaves a half file
            File target = new File(folder, id + ".yml");
            File temp = new File(folder, id + ".yml.tmp");
            yaml.save(temp);
            java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("storage: could not save " + id + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Moves what the player already keeps in the vanilla ender chest into the storage pages (the first free slots of
     * the unlocked pages, in order). The vanilla slots are emptied only after the storage file has been written; if
     * the write fails, nothing moves. What does not fit stays in the vanilla chest and is tried again next time.
     * Returns {moved, left}.
     */
    public int[] migrateEnderChest(Player player) {
        org.bukkit.inventory.Inventory ender = player.getEnderChest();
        PlayerStorage storage = of(player);
        List<Integer> movedSlots = new ArrayList<>();
        int left = 0;
        for (int slot = 0; slot < ender.getSize(); slot++) {
            ItemStack item = ender.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            boolean placed = false;
            for (int page = 0; page < storage.unlocked() && !placed; page++) {
                ItemStack[] items = storage.page(page);
                for (int target = 0; target < PAGE_SLOTS; target++) {
                    if (items[target] == null) {
                        items[target] = item.clone();
                        placed = true;
                        break;
                    }
                }
            }
            if (placed) movedSlots.add(slot);
            else left++;
        }
        if (movedSlots.isEmpty()) return new int[]{0, left};

        if (!save(player.getUniqueId())) {
            // nothing was written: forget the in-memory copy so the file (the truth) is read again
            loaded.remove(player.getUniqueId());
            return new int[]{0, left + movedSlots.size()};
        }
        for (int slot : movedSlots) ender.setItem(slot, null);
        player.saveData();
        plugin.getLogger().info("storage: moved " + movedSlots.size() + " ender chest stacks of " + player.getName() + " into the storage");
        return new int[]{movedSlots.size(), left};
    }

    public void setPatch(java.util.function.Function<ItemStack, ItemStack> patch) {
        this.patch = patch;
    }

    /** The item patch on every storage in memory (written to its file when something changed); answers how many stacks changed. */
    public int patchLoaded(java.util.function.Function<ItemStack, ItemStack> patch) {
        int changed = 0;
        for (Map.Entry<UUID, PlayerStorage> entry : loaded.entrySet()) {
            int count = patch(entry.getValue(), patch);
            if (count > 0) save(entry.getKey());
            changed += count;
        }
        return changed;
    }

    private static int patch(PlayerStorage storage, java.util.function.Function<ItemStack, ItemStack> patch) {
        int changed = 0;
        for (ItemStack[] page : storage.pages) {
            for (int slot = 0; slot < page.length; slot++) {
                ItemStack patched = patch.apply(page[slot]);
                if (patched == null) continue;
                page[slot] = patched;
                changed++;
            }
        }
        return changed;
    }

    /** Frees the memory of players who are not online (their file is the truth). */
    public void forget(UUID id) {
        loaded.remove(id);
    }
}
