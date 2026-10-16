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

    /** Price of unlocking page number {@code page} (1-based): the base for the first paid page, then +step each. */
    public long priceOf(int page) {
        if (page <= freePages()) return 0L;
        long base = Math.max(0L, plugin.getConfig().getLong("storage.price-base", 500L));
        long step = Math.max(0L, plugin.getConfig().getLong("storage.price-step", 500L));
        return base + (long) (page - freePages() - 1) * step;
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
        return loaded.computeIfAbsent(id, this::load);
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

    public void save(UUID id) {
        PlayerStorage storage = loaded.get(id);
        if (storage == null) return;

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
                return;
            }
            // write to a temporary file first, so a stop in the middle never leaves a half file
            File target = new File(folder, id + ".yml");
            File temp = new File(folder, id + ".yml.tmp");
            yaml.save(temp);
            java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().warning("storage: could not save " + id + ": " + e.getMessage());
        }
    }

    /** Frees the memory of players who are not online (their file is the truth). */
    public void forget(UUID id) {
        loaded.remove(id);
    }
}
