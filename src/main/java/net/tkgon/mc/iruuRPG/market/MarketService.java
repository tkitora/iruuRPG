package net.tkgon.mc.iruuRPG.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The free market: players list items for gold, set the price, amount, a delay before it can be bought and how long
 * it can be bought. Listing costs a fee that depends on each of those settings. Unsold items and sale money are kept
 * here until the seller picks them up (money is paid out at once when the seller is online).
 */
public final class MarketService implements Listener {

    /** The fee of one listing, split by setting. */
    public record Fee(long price, long quantity, long delay, long duration, long total) {
    }

    /** The operator account: listings and money of "iruuRPG" itself. */
    public static final UUID SYSTEM_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    public static final String SYSTEM_NAME = "iruuRPG";
    public static final String ADMIN_PERMISSION = "iruurpg.admin";

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final File file;
    private final Map<Long, MarketListing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> proceeds = new HashMap<>();
    private final Map<UUID, List<ItemStack>> returns = new HashMap<>();
    private final Map<Long, MarketBuyOffer> offers = new LinkedHashMap<>();
    private long nextId = 1;

    public MarketService(JavaPlugin plugin, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.file = new File(plugin.getDataFolder(), "market/market.yml");
    }

    // ---- settings -----------------------------------------------------------------------------------------------

    public String currencyName() {
        return plugin.getConfig().getString("quest.currency-name", "ゴールド");
    }

    public long maxPrice() {
        return Math.max(1L, plugin.getConfig().getLong("market.max-price", 1_000_000L));
    }

    public int maxQuantity() {
        return Math.max(1, plugin.getConfig().getInt("market.max-quantity", 9999));
    }

    public int maxDelayMinutes() {
        return Math.max(0, plugin.getConfig().getInt("market.max-delay-hours", 168)) * 60;
    }

    public int maxDurationMinutes() {
        return Math.max(1, plugin.getConfig().getInt("market.max-duration-hours", 168)) * 60;
    }

    public int maxListingsPerPlayer() {
        return Math.max(1, plugin.getConfig().getInt("market.max-listings-per-player", 10));
    }

    /**
     * Fee = price part (a share of the total value) + quantity part (per item) + delay part (a share of the value per
     * hour of waiting) + duration part (a share of the value per day on sale). Every part is rounded up; at least 1.
     * The delay and the duration are in minutes.
     */
    public Fee fee(long unitPrice, int quantity, int delayMinutes, int durationMinutes) {
        var config = plugin.getConfig();
        double value = (double) unitPrice * quantity;
        long price = (long) Math.ceil(value * config.getDouble("market.fee.price-percent", 1.0) / 100.0);
        long quantityFee = (long) Math.ceil(quantity * config.getDouble("market.fee.per-item", 1.0));
        long delay = (long) Math.ceil(value * config.getDouble("market.fee.delay-percent-per-hour", 0.1) / 100.0 * (delayMinutes / 60.0));
        long duration = (long) Math.ceil(value * config.getDouble("market.fee.duration-percent-per-day", 0.5) / 100.0 * (durationMinutes / 1440.0));
        long total = Math.max(Math.max(1L, config.getLong("market.fee.minimum", 1L)), price + quantityFee + delay + duration);
        return new Fee(price, quantityFee, delay, duration, total);
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    public List<MarketListing> all() {
        return new ArrayList<>(listings.values());
    }

    public MarketListing find(long id) {
        return listings.get(id);
    }

    public List<MarketListing> listingsOf(UUID seller) {
        List<MarketListing> mine = new ArrayList<>();
        for (MarketListing listing : listings.values()) {
            if (listing.sellerId().equals(seller)) mine.add(listing);
        }
        mine.sort(Comparator.comparingLong(MarketListing::listedAt).reversed());
        return mine;
    }

    public long gold(Player player) {
        return profileManager.getOrCreate(player).currency();
    }

    public long pendingProceeds(UUID seller) {
        return proceeds.getOrDefault(seller, 0L);
    }

    public int pendingReturnStacks(UUID seller) {
        return returns.getOrDefault(seller, List.of()).size();
    }

    /** How many of {@code template} (same item) the player carries. */
    public int count(Player player, ItemStack template) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(template)) total += stack.getAmount();
        }
        return total;
    }

    // ---- selling ----------------------------------------------------------------------------------------------------

    /** Lists the items. Returns an error text, or null on success. */
    public String list(Player seller, ItemStack template, int quantity, long unitPrice, int delayMinutes, int durationMinutes) {
        if (template == null || template.getType().isAir()) return "出品するアイテムを選んでください。";
        if (unitPrice < 1 || unitPrice > maxPrice()) return "単価は 1〜" + maxPrice() + " の範囲です。";
        if (quantity < 1 || quantity > maxQuantity()) return "個数は 1〜" + maxQuantity() + " の範囲です。";
        if (delayMinutes < 0 || delayMinutes > maxDelayMinutes()) return "購入可能までの期間は 0〜" + maxDelayMinutes() / 60 + " 時間です。";
        if (durationMinutes < 1 || durationMinutes > maxDurationMinutes()) return "購入可能期間は 1 分〜" + maxDurationMinutes() / 60 + " 時間です。";
        if (listingsOf(seller.getUniqueId()).size() >= maxListingsPerPlayer()) return "出品できるのは同時に " + maxListingsPerPlayer() + " 件までです。";
        if (count(seller, template) < quantity) return "持っている個数が足りません。";

        Fee fee = fee(unitPrice, quantity, delayMinutes, durationMinutes);
        PlayerProfile profile = profileManager.getOrCreate(seller);
        if (profile.currency() < fee.total()) return "手数料 " + fee.total() + " " + currencyName() + " が足りません。(所持: " + profile.currency() + ")";

        removeItems(seller, template, quantity);
        profile.setCurrency(profile.currency() - fee.total());
        profileManager.save(seller);

        long now = System.currentTimeMillis();
        long availableAt = now + delayMinutes * 60_000L;
        long expiresAt = availableAt + durationMinutes * 60_000L;
        MarketListing listing = new MarketListing(nextId++, seller.getUniqueId(), seller.getName(), template, quantity, unitPrice, now, availableAt, expiresAt);
        listings.put(listing.id(), listing);
        save();
        return null;
    }

    private void removeItems(Player player, ItemStack template, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !stack.isSimilar(template)) continue;

            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take >= stack.getAmount()) {
                player.getInventory().setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
    }

    /** The seller takes the rest of a listing back (it goes to the pick-up box). */
    public String cancel(Player seller, long id) {
        MarketListing listing = listings.get(id);
        if (listing == null) return "その出品はもうありません。";
        if (!listing.sellerId().equals(seller.getUniqueId())) return "自分の出品ではありません。";

        listings.remove(id);
        addReturn(listing.sellerId(), listing.item(), listing.remaining());
        save();
        return null;
    }

    // ---- buying -------------------------------------------------------------------------------------------------------

    /** Returns an error text, or null when bought. */
    public String buy(Player buyer, long id, int quantity) {
        long now = System.currentTimeMillis();
        MarketListing listing = listings.get(id);
        if (listing == null || listing.isExpired(now)) return "その出品はもう終わっています。";
        if (!listing.isAvailable(now)) return "まだ購入できません。";
        if (listing.sellerId().equals(buyer.getUniqueId())) return "自分の出品は買えません。";
        int cap = listing.infinite() ? Math.min(listing.remaining(), 64 * 9) : listing.remaining();
        if (quantity < 1 || quantity > cap) return "個数が正しくありません。(最大 " + cap + ")";

        long total = listing.unitPrice() * quantity;
        PlayerProfile profile = profileManager.getOrCreate(buyer);
        if (profile.currency() < total) return currencyName() + "が足りません。(必要: " + total + " / 所持: " + profile.currency() + ")";
        if (freeSpace(buyer, listing.item()) < quantity) return "持ち物に空きが足りません。";

        profile.setCurrency(profile.currency() - total);
        profileManager.save(buyer);
        giveItems(buyer, listing.item(), quantity);

        if (!listing.infinite()) {
            listing.setRemaining(listing.remaining() - quantity);
            if (listing.remaining() <= 0) listings.remove(id);
        }
        creditSeller(listing.sellerId(), total, buyer.getName(), listing.item(), quantity);
        save();
        return null;
    }

    private int freeSpace(Player player, ItemStack template) {
        int max = template.getMaxStackSize();
        int space = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                space += max;
            } else if (stack.isSimilar(template)) {
                space += Math.max(0, max - stack.getAmount());
            }
        }
        return space;
    }

    private void giveItems(Player player, ItemStack template, int quantity) {
        int left = quantity;
        int max = template.getMaxStackSize();
        while (left > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(max, left));
            left -= stack.getAmount();
            for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    private void creditSeller(UUID sellerId, long gold, String buyerName, ItemStack item, int quantity) {
        Player seller = Bukkit.getPlayer(sellerId);
        if (seller != null) {
            PlayerProfile profile = profileManager.getOrCreate(seller);
            profile.setCurrency(profile.currency() + gold);
            profileManager.save(seller);
            seller.sendMessage(Component.text("[フリマ] " + buyerName + " が " + itemName(item) + " ×" + quantity + " を買った。+" + gold + " " + currencyName(), NamedTextColor.GOLD));
        } else {
            proceeds.merge(sellerId, gold, Long::sum);
        }
    }

    // ---- pick-up box --------------------------------------------------------------------------------------------------

    private void addReturn(UUID owner, ItemStack template, int quantity) {
        int max = template.getMaxStackSize();
        int left = quantity;
        List<ItemStack> box = returns.computeIfAbsent(owner, key -> new ArrayList<>());
        while (left > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(max, left));
            left -= stack.getAmount();
            box.add(stack);
        }
    }

    /** Pays out waiting sale money and hands over returned items (what does not fit stays in the box). */
    public void claim(Player player) {
        claimFor(player, player.getUniqueId());
    }

    /** The operator picks up the money and items of the "iruuRPG" account. */
    public void claimHouse(Player admin) {
        if (!admin.hasPermission(ADMIN_PERMISSION)) return;
        claimFor(admin, SYSTEM_ID);
    }

    private void claimFor(Player player, UUID id) {
        Long money = proceeds.remove(id);
        if (money != null && money > 0) {
            PlayerProfile profile = profileManager.getOrCreate(player);
            profile.setCurrency(profile.currency() + money);
            profileManager.save(player);
            player.sendMessage(Component.text("[フリマ] 売上 " + money + " " + currencyName() + " を受け取った。", NamedTextColor.GOLD));
        }

        List<ItemStack> box = returns.get(id);
        if (box != null && !box.isEmpty()) {
            List<ItemStack> kept = new ArrayList<>();
            int given = 0;
            for (ItemStack stack : box) {
                if (freeSpace(player, stack) >= stack.getAmount()) {
                    player.getInventory().addItem(stack);
                    given++;
                } else {
                    kept.add(stack);
                }
            }
            if (kept.isEmpty()) {
                returns.remove(id);
            } else {
                returns.put(id, kept);
                player.sendMessage(Component.text("[フリマ] 持ち物がいっぱいで、" + kept.size() + " 組が受け取れていない。空けてからもう一度。", NamedTextColor.RED));
            }
            if (given > 0) player.sendMessage(Component.text("[フリマ] 返却されたアイテムを受け取った。", NamedTextColor.GREEN));
        }
        save();
    }

    // ---- operator (iruuRPG) -------------------------------------------------------------------------------------------

    private String adminCheck(Player admin) {
        return admin.hasPermission(ADMIN_PERMISSION) ? null : "運営だけの操作です。";
    }

    /**
     * Lists an item as "iruuRPG": no fee. With {@code infinite} nothing is taken from the operator and the listing
     * never runs out; otherwise the items are taken from the operator's inventory and unsold ones go to the house box.
     */
    public String listAsHouse(Player admin, ItemStack template, int quantity, long unitPrice, int delayMinutes, int durationMinutes, boolean infinite) {
        String denied = adminCheck(admin);
        if (denied != null) return denied;
        if (template == null || template.getType().isAir()) return "出品するアイテムを選んでください。";
        if (unitPrice < 1 || unitPrice > maxPrice()) return "単価は 1〜" + maxPrice() + " の範囲です。";
        if (quantity < 1 || quantity > maxQuantity()) return "個数は 1〜" + maxQuantity() + " の範囲です。";
        if (delayMinutes < 0 || delayMinutes > maxDelayMinutes()) return "購入可能までの期間は 0〜" + maxDelayMinutes() / 60 + " 時間です。";
        if (durationMinutes < 1 || durationMinutes > maxDurationMinutes()) return "購入可能期間は 1 分〜" + maxDurationMinutes() / 60 + " 時間です。";
        if (!infinite) {
            if (count(admin, template) < quantity) return "持っている個数が足りません。";
            removeItems(admin, template, quantity);
        }

        long now = System.currentTimeMillis();
        long availableAt = now + delayMinutes * 60_000L;
        long expiresAt = availableAt + durationMinutes * 60_000L;
        MarketListing listing = new MarketListing(nextId++, SYSTEM_ID, SYSTEM_NAME, template, quantity, unitPrice, now, availableAt, expiresAt, infinite);
        listings.put(listing.id(), listing);
        save();
        return null;
    }

    /** Takes any listing off the market; the rest goes to its seller's pick-up box. */
    public String removeListing(Player admin, long id) {
        String denied = adminCheck(admin);
        if (denied != null) return denied;
        MarketListing listing = listings.remove(id);
        if (listing == null) return "その出品はもうありません。";

        if (!listing.infinite()) addReturn(listing.sellerId(), listing.item(), listing.remaining());
        Player seller = Bukkit.getPlayer(listing.sellerId());
        if (seller != null && !listing.sellerId().equals(SYSTEM_ID)) {
            seller.sendMessage(Component.text("[フリマ] 運営が「" + itemName(listing.item()) + "」の出品を取り下げた。残りは受け取り場所に戻っている。", NamedTextColor.YELLOW));
        }
        save();
        return null;
    }

    public List<MarketBuyOffer> offers() {
        return new ArrayList<>(offers.values());
    }

    public MarketBuyOffer findOffer(long id) {
        return offers.get(id);
    }

    /** {@code limit} < 0 for no limit. */
    public String addOffer(Player admin, ItemStack template, long unitPrice, int limit) {
        String denied = adminCheck(admin);
        if (denied != null) return denied;
        if (template == null || template.getType().isAir()) return "買い取るアイテムを選んでください。";
        if (unitPrice < 1 || unitPrice > maxPrice()) return "単価は 1〜" + maxPrice() + " の範囲です。";

        MarketBuyOffer offer = new MarketBuyOffer(nextId++, template, unitPrice, limit);
        offers.put(offer.id(), offer);
        save();
        return null;
    }

    public String removeOffer(Player admin, long id) {
        String denied = adminCheck(admin);
        if (denied != null) return denied;
        if (offers.remove(id) == null) return "その買取はもうありません。";
        save();
        return null;
    }

    /** A player sells items to the operator at the offer's price. The items go to the house box. */
    public String sellToHouse(Player seller, long offerId, int quantity) {
        MarketBuyOffer offer = offers.get(offerId);
        if (offer == null) return "その買取はもう終わっています。";
        if (quantity < 1) return "個数が正しくありません。";
        if (!offer.unlimited() && quantity > offer.remaining()) return "買取の残りは " + offer.remaining() + " 個です。";
        ItemStack template = offer.item();
        if (count(seller, template) < quantity) return "持っている個数が足りません。";

        removeItems(seller, template, quantity);
        PlayerProfile profile = profileManager.getOrCreate(seller);
        profile.setCurrency(profile.currency() + offer.unitPrice() * quantity);
        profileManager.save(seller);
        addReturn(SYSTEM_ID, template, quantity);
        if (!offer.unlimited()) {
            offer.setRemaining(offer.remaining() - quantity);
            if (offer.remaining() <= 0) offers.remove(offerId);
        }
        save();
        return null;
    }

    // ---- expiry ---------------------------------------------------------------------------------------------------------

    /** Moves listings whose time is up into their seller's pick-up box. */
    public void expire() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (MarketListing listing : new ArrayList<>(listings.values())) {
            if (!listing.isExpired(now)) continue;

            listings.remove(listing.id());
            if (!listing.infinite()) addReturn(listing.sellerId(), listing.item(), listing.remaining());
            changed = true;
            Player seller = Bukkit.getPlayer(listing.sellerId());
            if (seller != null) {
                seller.sendMessage(Component.text("[フリマ] 「" + itemName(listing.item()) + "」の出品期間が終わった。残りは受け取り場所に戻っている。", NamedTextColor.YELLOW));
            }
        }
        if (changed) save();
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L * 30, 20L * 30);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // the profile is loaded by the lifecycle listener; wait a little
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (pendingProceeds(player.getUniqueId()) > 0 || pendingReturnStacks(player.getUniqueId()) > 0) {
                player.sendMessage(Component.text("[フリマ] 受け取れる売上・返却品がある。/market から受け取れる。", NamedTextColor.YELLOW));
            }
        }, 60L);
    }

    public static String itemName(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        }
        return item.getType().name();
    }

    // ---- storage --------------------------------------------------------------------------------------------------------

    public void load() {
        listings.clear();
        proceeds.clear();
        returns.clear();
        offers.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        nextId = yaml.getLong("next-id", 1L);
        ConfigurationSection section = yaml.getConfigurationSection("listings");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) continue;
                ItemStack item = entry.getItemStack("item");
                String seller = entry.getString("seller", "");
                if (item == null || seller.isBlank()) continue;
                try {
                    long id = Long.parseLong(key);
                    listings.put(id, new MarketListing(id, UUID.fromString(seller), entry.getString("seller-name", "?"), item,
                            entry.getInt("remaining"), entry.getLong("price"), entry.getLong("listed-at"),
                            entry.getLong("available-at"), entry.getLong("expires-at"), entry.getBoolean("infinite", false)));
                    nextId = Math.max(nextId, id + 1);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("market: skipped a damaged listing " + key);
                }
            }
        }
        ConfigurationSection offerSection = yaml.getConfigurationSection("offers");
        if (offerSection != null) {
            for (String key : offerSection.getKeys(false)) {
                ConfigurationSection entry = offerSection.getConfigurationSection(key);
                ItemStack item = entry == null ? null : entry.getItemStack("item");
                if (item == null) continue;
                try {
                    long id = Long.parseLong(key);
                    offers.put(id, new MarketBuyOffer(id, item, entry.getLong("price"), entry.getInt("remaining", -1)));
                    nextId = Math.max(nextId, id + 1);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("market: skipped a damaged buy offer " + key);
                }
            }
        }
        ConfigurationSection money = yaml.getConfigurationSection("proceeds");
        if (money != null) {
            for (String key : money.getKeys(false)) {
                try {
                    proceeds.put(UUID.fromString(key), money.getLong(key));
                } catch (IllegalArgumentException ignored) {
                    // skipped
                }
            }
        }
        ConfigurationSection box = yaml.getConfigurationSection("returns");
        if (box != null) {
            for (String key : box.getKeys(false)) {
                try {
                    List<ItemStack> stacks = new ArrayList<>();
                    for (Object raw : box.getList(key, List.of())) {
                        if (raw instanceof ItemStack stack) stacks.add(stack);
                    }
                    if (!stacks.isEmpty()) returns.put(UUID.fromString(key), stacks);
                } catch (IllegalArgumentException ignored) {
                    // skipped
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", nextId);
        for (MarketListing listing : listings.values()) {
            String base = "listings." + listing.id() + ".";
            yaml.set(base + "seller", listing.sellerId().toString());
            yaml.set(base + "seller-name", listing.sellerName());
            yaml.set(base + "item", listing.item());
            yaml.set(base + "remaining", listing.remaining());
            yaml.set(base + "price", listing.unitPrice());
            yaml.set(base + "listed-at", listing.listedAt());
            yaml.set(base + "available-at", listing.availableAt());
            yaml.set(base + "expires-at", listing.expiresAt());
            yaml.set(base + "infinite", listing.infinite());
        }
        for (MarketBuyOffer offer : offers.values()) {
            String base = "offers." + offer.id() + ".";
            yaml.set(base + "item", offer.item());
            yaml.set(base + "price", offer.unitPrice());
            yaml.set(base + "remaining", offer.remaining());
        }
        for (Map.Entry<UUID, Long> entry : proceeds.entrySet()) {
            yaml.set("proceeds." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, List<ItemStack>> entry : returns.entrySet()) {
            yaml.set("returns." + entry.getKey(), entry.getValue());
        }
        try {
            File folder = file.getParentFile();
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("market: could not create " + folder.getAbsolutePath());
                return;
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("market: could not save: " + e.getMessage());
        }
    }
}
