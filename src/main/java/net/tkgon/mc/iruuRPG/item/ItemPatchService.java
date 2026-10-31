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
 * <p>
 * The move to items that never stack ({@code item-migration.mode}, see design/item-foundation-2026-10-31.md):
 * <ul>
 *   <li>{@code off} (the default): as above; weapons and armor made from now on carry their own mark (UniqueItems).</li>
 *   <li>{@code dry-run}: nothing is changed at all (also no ordinary patch): what would be done is written to
 *       plugins/iruuRPG/item-migration-report.txt (the market and storages at the switch, each player when they join).</li>
 *   <li>{@code apply}: weapons and armor without a mark get one and their stacks are split (one per slot; what does not
 *       fit goes to the ender chest, then to the player's feet). Before a player's items are changed, their inventory
 *       and ender chest are saved to plugins/iruuRPG/backups/item-migration/&lt;time&gt;/&lt;uuid&gt;.yml; before the market
 *       and the storages, their files are copied there. While on, every join checks the player again.</li>
 * </ul>
 * A change of mode counts as a new version (everyone is looked at again).
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
    /** Where the pieces of a split stack go when the inventory and the ender chest are full (the market's pick-up box). */
    private java.util.function.BiConsumer<java.util.UUID, java.util.List<ItemStack>> overflowBox;
    private int version;
    private String mode = "off";
    /** The backup folder of this run of the migration (one per reload / start in apply mode). */
    private File backupFolder;

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

    /** Sets the box for split pieces with no room (without one, they are dropped at the player's feet, for that player only). */
    public void setOverflowBox(java.util.function.BiConsumer<java.util.UUID, java.util.List<ItemStack>> overflowBox) {
        this.overflowBox = overflowBox;
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
        // a fault of the patch never stops the plugin (it is called from onEnable and the reload): it only stops the patch
        try {
            return refreshNow();
        } catch (RuntimeException | LinkageError e) {
            patcher.setEnabled(false);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "items: the item patch failed and is off until the next reload; items are left as they are", e);
            return Set.of();
        }
    }

    private static String readMode(String raw) {
        String value = raw == null ? "off" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "dry-run", "dryrun", "dry" -> "dry-run";
            case "apply" -> "apply";
            default -> "off";
        };
    }

    public String mode() {
        return mode;
    }

    private Set<String> refreshNow() {
        patcher.setEnabled(plugin.getConfig().getBoolean("item-patch.enabled", true));
        mode = readMode(plugin.getConfig().getString("item-migration.mode", "off"));
        patcher.setMigrate(!mode.equals("off"));
        backupFolder = null;
        Map<String, String> now = patcher.computeFingerprints();
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : null;
        Map<String, String> before = new HashMap<>();
        String modeBefore = yaml == null ? "off" : yaml.getString("migration-mode", "off");
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
        boolean modeChanged = !mode.equals(modeBefore);
        if (!changed.isEmpty() || modeChanged) version++;
        if (!changed.isEmpty() || modeChanged || !now.equals(before)) save(now);
        if (changed.isEmpty() && !modeChanged) return changed;
        if (modeChanged) plugin.getLogger().info("items: item-migration.mode " + modeBefore + " -> " + mode + ", item version " + version);

        plugin.getLogger().info("items: " + changed.size() + " item definition(s) changed, item version " + version
                + " (" + String.join(", ", changed.stream().limit(10).toList()) + (changed.size() > 10 ? ", ..." : "") + ")");
        if (!patcher.enabled()) {
            plugin.getLogger().info("items: item-patch.enabled is false; the items are left as they are");
            return changed;
        }
        patcher.takeCounts();
        if (mode.equals("dry-run")) {
            // only looked at: every result is thrown away
            for (Keeper keeper : keepers) keeper.patchItems(item -> {
                patcher.patch(item == null ? null : item.clone());
                return null;
            });
        } else {
            if (mode.equals("apply")) backupFiles();
            for (Keeper keeper : keepers) keeper.patchItems(patcher::patch);
        }
        ItemPatcher.Counts stored = patcher.takeCounts();
        report("market and storages", stored);
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
        yaml.set("migration-mode", mode);
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
        if (mode.equals("dry-run")) {
            ItemPatcher.Counts counts = lookAtPlayer(player);
            report("player " + player.getName() + " (" + player.getUniqueId() + ")", counts);
            if (total != null) total.add(counts);
            return 0;
        }
        if (mode.equals("apply")) {
            ItemPatcher.Counts would = lookAtPlayer(player);
            if (!would.any()) return 0;
            backupPlayer(player);
            report("player " + player.getName() + " (" + player.getUniqueId() + ")", would);
        }
        patcher.takeCounts();
        int changed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        int count = patcher.patchAll(contents);
        java.util.List<ItemStack> overflow = patcher.split(contents);
        boolean split = !overflow.isEmpty() || hasSplit(contents, player.getInventory().getContents());
        if (count > 0 || split) player.getInventory().setContents(contents);
        changed += count;
        // the pieces of a split stack that found no room: the ender chest, then the market's pick-up box (nothing on the ground)
        java.util.List<ItemStack> rest = new java.util.ArrayList<>(player.getEnderChest().addItem(overflow.toArray(new ItemStack[0])).values());
        if (!rest.isEmpty()) keepOverflow(player, rest);

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

    private void keepOverflow(Player player, java.util.List<ItemStack> rest) {
        int pieces = rest.stream().mapToInt(ItemStack::getAmount).sum();
        if (overflowBox != null) {
            try {
                overflowBox.accept(player.getUniqueId(), rest);
                player.sendMessage(Component.text("[アイテム] 重ならなくなったアイテムが " + pieces + " 個、持ち物とエンダーチェストに入りきらなかった。フリマ (/market) の受け取りから受け取れる。", NamedTextColor.YELLOW));
                plugin.getLogger().info("items: " + player.getName() + ": " + pieces + " split piece(s) put in the market pick-up box");
                return;
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "items: could not put split pieces in the pick-up box; dropping them for " + player.getName(), e);
            }
        }
        for (ItemStack stack : rest) {
            org.bukkit.entity.Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), stack);
            dropped.setOwner(player.getUniqueId());
        }
        player.sendMessage(Component.text("[アイテム] 入りきらなかったアイテム " + pieces + " 個を、足元に落とした (拾えるのは本人だけ)。", NamedTextColor.YELLOW));
    }

    /** Patches item arrays read from somewhere (a storage file), logging what was done; answers how many stacks changed. */
    public int patchLoaded(String where, java.util.List<ItemStack[]> arrays) {
        if (!patcher.enabled()) return 0;
        try {
            patcher.takeCounts();
            int changed = 0;
            if (mode.equals("dry-run")) {
                for (ItemStack[] array : arrays) {
                    ItemStack[] copy = new ItemStack[array.length];
                    for (int slot = 0; slot < array.length; slot++) copy[slot] = array[slot] == null ? null : array[slot].clone();
                    patcher.patchAll(copy);
                }
                report(where, patcher.takeCounts());
                return 0;
            }
            for (ItemStack[] array : arrays) changed += patcher.patchAll(array);
            ItemPatcher.Counts counts = patcher.takeCounts();
            report(where, counts);
            if (counts.any()) plugin.getLogger().info("items: " + where + ": " + counts);
            return changed;
        } catch (RuntimeException e) {
            // the storage opens as it is
            plugin.getLogger().log(java.util.logging.Level.WARNING, "items: could not patch " + where, e);
            return 0;
        }
    }

    private int patchInventory(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        int count = patcher.patchAll(contents);
        java.util.List<ItemStack> overflow = patcher.split(contents);
        if (count > 0 || !overflow.isEmpty() || hasSplit(contents, inventory.getContents())) inventory.setContents(contents);
        // what a split stack cannot place in the same container stays there as it was (one stack): put back on its first piece
        for (ItemStack rest : overflow) {
            for (ItemStack item : contents) {
                if (item != null && item.isSimilar(rest)) {
                    item.setAmount(item.getAmount() + rest.getAmount());
                    break;
                }
            }
        }
        if (!overflow.isEmpty()) {
            inventory.setContents(contents);
            plugin.getLogger().warning("items: a container had no room to split " + overflow.size() + " stacked item(s); left in it");
        }
        return count;
    }

    private static boolean hasSplit(ItemStack[] after, ItemStack[] before) {
        for (int slot = 0; slot < after.length && slot < before.length; slot++) {
            ItemStack a = after[slot];
            ItemStack b = before[slot];
            if ((a == null) != (b == null)) return true;
            if (a != null && a.getAmount() != b.getAmount()) return true;
        }
        return false;
    }

    // ---- the migration: looking only, the report, the backups --------------------------------------------------------------

    /** What patching the player would do (on copies: nothing changes). */
    private ItemPatcher.Counts lookAtPlayer(Player player) {
        patcher.takeCounts();
        for (ItemStack[] contents : java.util.List.of(player.getInventory().getContents(), player.getEnderChest().getContents())) {
            ItemStack[] copy = new ItemStack[contents.length];
            for (int slot = 0; slot < contents.length; slot++) copy[slot] = contents[slot] == null ? null : contents[slot].clone();
            patcher.patchAll(copy);
            patcher.split(copy);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) patcher.patch(cursor.clone());
        return patcher.takeCounts();
    }

    private void report(String where, ItemPatcher.Counts counts) {
        if (mode.equals("off") || !counts.any()) return;
        File out = new File(plugin.getDataFolder(), "item-migration-report.txt");
        String line = java.time.LocalDateTime.now().withNano(0) + " [" + mode + "] " + where + ": " + counts + System.lineSeparator();
        try {
            java.nio.file.Files.writeString(out.toPath(), line, java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("items: could not write " + out.getName() + ": " + e.getMessage());
        }
    }

    private File backupFolder() {
        if (backupFolder == null) {
            String stamp = java.time.LocalDateTime.now().withNano(0).toString().replace(':', '-');
            backupFolder = new File(plugin.getDataFolder(), "backups/item-migration/" + stamp);
        }
        return backupFolder;
    }

    /** The player's inventory and ender chest, as they are, before the migration changes them. */
    private void backupPlayer(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", player.getName());
        yaml.set("inventory", java.util.Arrays.asList(player.getInventory().getContents()));
        yaml.set("ender-chest", java.util.Arrays.asList(player.getEnderChest().getContents()));
        try {
            File folder = backupFolder();
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("cannot make " + folder);
            yaml.save(new File(folder, player.getUniqueId() + "-" + System.currentTimeMillis() + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("items: could not back up " + player.getName() + " before the migration: " + e.getMessage());
            throw new IllegalStateException("no backup, no migration", e);
        }
    }

    /** The market's and the storages' files, copied before the migration changes them. */
    private void backupFiles() {
        File folder = backupFolder();
        for (String name : new String[]{"market", "storage"}) {
            File source = new File(plugin.getDataFolder(), name);
            if (!source.isDirectory()) continue;
            File target = new File(folder, name);
            try (var files = java.nio.file.Files.walk(source.toPath())) {
                for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) files::iterator) {
                    java.nio.file.Path to = target.toPath().resolve(source.toPath().relativize(path).toString());
                    if (java.nio.file.Files.isDirectory(path)) java.nio.file.Files.createDirectories(to);
                    else java.nio.file.Files.copy(path, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("items: could not back up " + name + "/ before the migration: " + e.getMessage());
                throw new IllegalStateException("no backup, no migration", e);
            }
        }
        plugin.getLogger().info("items: backed up market/ and storage/ to " + folder.getPath());
    }

    /** Puts a player's inventory and ender chest back as a backup file has them (the restore of the migration). */
    public boolean restore(Player player, File backup) {
        if (!backup.isFile()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(backup);
        java.util.List<?> inventory = yaml.getList("inventory");
        java.util.List<?> ender = yaml.getList("ender-chest");
        if (inventory == null || ender == null) return false;
        player.getInventory().setContents(inventory.stream().map(item -> item instanceof ItemStack stack ? stack : null).toArray(ItemStack[]::new));
        player.getEnderChest().setContents(ender.stream().map(item -> item instanceof ItemStack stack ? stack : null).toArray(ItemStack[]::new));
        return true;
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
            // while the migration applies, every join looks again (stacks taken out of the market or a storage are split then)
            if (checked != null && checked >= version && !mode.equals("apply")) return;
            if (!patcher.enabled()) return; // checked again once the patch is on
            patchPlayer(player);
            player.getPersistentDataContainer().set(checkedKey, PersistentDataType.INTEGER, version);
        }, 20L);
    }

    // ---- a placed container: when it is opened ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!patcher.enabled() || mode.equals("dry-run")) return;
        // only the container opened (no look through the world)
        Inventory top = event.getInventory();
        if (!isPlaced(top.getHolder(false))) return;
        patcher.takeCounts();
        patchInventory(top);
        ItemPatcher.Counts counts = patcher.takeCounts();
        if (counts.any()) plugin.getLogger().info("items: a container opened by " + event.getPlayer().getName() + ": " + counts);
    }
}
