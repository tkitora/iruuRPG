package net.tkgon.mc.iruuRPG.crafting;

import net.tkgon.mc.iruuRPG.combat.ParticleFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.item.ItemIdentifier;
import net.tkgon.mc.iruuRPG.item.Rarity;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.stat.StatLabels;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crafting: pick a recipe you have the materials for, hammer it out, and get the item. The craft level (it grows
 * with every craft) decides whether random stats get attached, how many, and how strong they are - there can also
 * be none at all.
 */
public final class CraftService implements Listener {

    public static final int HAMMER_HITS = 4;
    private static final long HAMMER_INTERVAL_TICKS = 9L;

    private final JavaPlugin plugin;
    private final CraftRegistry registry;
    private final PlayerProfileManager profileManager;
    private final ItemIdentifier itemIdentifier;
    private final RpgItemRegistry itemRegistry;
    private final RpgItemFactory itemFactory;
    private final Map<UUID, Runnable> pending = new HashMap<>();

    public CraftService(
            JavaPlugin plugin,
            CraftRegistry registry,
            PlayerProfileManager profileManager,
            ItemIdentifier itemIdentifier,
            RpgItemRegistry itemRegistry,
            RpgItemFactory itemFactory
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.profileManager = profileManager;
        this.itemIdentifier = itemIdentifier;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
    }

    public CraftRegistry registry() {
        return registry;
    }

    // ---- craft level ---------------------------------------------------------------------------------

    private int craftsPerLevel() {
        return Math.max(1, plugin.getConfig().getInt("crafting.crafts-per-level", 3));
    }

    public int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("craft-caps.max-level", 255));
    }

    public int level(Player player) {
        long xp = profileManager.getOrCreate(player).craftXp();
        return (int) Math.min(maxLevel(), 1 + xp / craftsPerLevel());
    }

    /** Crafts still needed for the next level (0 at the maximum). */
    public int craftsToNextLevel(Player player) {
        if (level(player) >= maxLevel()) return 0;
        long xp = profileManager.getOrCreate(player).craftXp();
        return (int) (craftsPerLevel() - xp % craftsPerLevel());
    }

    public void setLevel(Player player, int level) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        profile.setCraftXp((long) (Math.max(1, Math.min(maxLevel(), level)) - 1) * craftsPerLevel());
        profileManager.save(player);
    }

    // ---- ingredients -----------------------------------------------------------------------------------

    public int count(Player player, String itemId) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            if (itemIdentifier.itemId(stack).filter(itemId::equals).isPresent()) total += stack.getAmount();
        }
        return total;
    }

    public boolean hasIngredients(Player player, CraftRecipe recipe) {
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            if (count(player, entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    public String itemName(String itemId) {
        return itemRegistry.find(itemId).map(RpgItemDefinition::name).orElse(itemId);
    }

    private void consume(Player player, CraftRecipe recipe) {
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            int left = entry.getValue();
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length && left > 0; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || itemIdentifier.itemId(stack).filter(entry.getKey()::equals).isEmpty()) continue;

                int take = Math.min(left, stack.getAmount());
                left -= take;
                if (take >= stack.getAmount()) {
                    player.getInventory().setItem(slot, null);
                } else {
                    stack.setAmount(stack.getAmount() - take);
                }
            }
        }
    }

    // ---- crafting --------------------------------------------------------------------------------------

    /** Starts crafting. Returns false (with a message) if it cannot be done right now. */
    public boolean craft(Player player, CraftRecipe recipe) {
        return craft(player, recipe, null, null);
    }

    /**
     * Starts crafting. {@code onHit} gets the number of the anvil hit (1..4) and {@code onComplete} the finished item
     * (already in the player's inventory), so a menu can animate the crafting.
     */
    public boolean craft(Player player, CraftRecipe recipe, java.util.function.IntConsumer onHit, java.util.function.Consumer<ItemStack> onComplete) {
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("[クラフト] いま作っている最中だ。", NamedTextColor.RED));
            return false;
        }
        if (level(player) < recipe.minCraftLevel()) {
            player.sendMessage(Component.text("[クラフト] クラフトレベルが足りない。(必要: " + recipe.minCraftLevel() + ")", NamedTextColor.RED));
            return false;
        }
        if (!hasIngredients(player, recipe)) {
            player.sendMessage(Component.text("[クラフト] 素材が足りない。", NamedTextColor.RED));
            return false;
        }
        RpgItemDefinition definition = itemRegistry.find(recipe.resultItemId()).orElse(null);
        if (definition == null) {
            player.sendMessage(Component.text("[クラフト] 完成品が見つからない: " + recipe.resultItemId(), NamedTextColor.RED));
            return false;
        }

        // materials are used up right away; the result is decided now and handed over after the hammering
        consume(player, recipe);
        int level = level(player);
        ItemStack result = createResult(definition, recipe, level);
        Runnable finish = () -> {
            complete(player, recipe, result);
            if (onComplete != null) onComplete.accept(result);
        };
        pending.put(player.getUniqueId(), finish);

        new BukkitRunnable() {
            private int hit;

            @Override
            public void run() {
                if (!pending.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                hit++;
                Location spot = player.getLocation().add(player.getLocation().getDirection().multiply(0.8)).add(0, 1.0, 0);
                player.getWorld().playSound(spot, Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.0f + 0.08f * hit);
                ParticleFx.to(player, player.getWorld()).spawnParticle(Particle.CRIT, spot, 8, 0.15, 0.1, 0.15, 0.3);
                player.sendActionBar(Component.text("カン".repeat(hit) + "…", NamedTextColor.GOLD));
                if (onHit != null) onHit.accept(hit);
                if (hit >= HAMMER_HITS) {
                    cancel();
                    Runnable done = pending.remove(player.getUniqueId());
                    if (done != null) done.run();
                }
            }
        }.runTaskTimer(plugin, HAMMER_INTERVAL_TICKS, HAMMER_INTERVAL_TICKS);
        return true;
    }

    private void complete(Player player, CraftRecipe recipe, ItemStack result) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        int before = level(player);
        profile.setCraftXp(profile.craftXp() + 1);
        int after = level(player);

        var leftover = player.getInventory().addItem(result);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);

        CraftBonus bonus = bonusOf(result);
        player.sendMessage(Component.text("[クラフト] 「" + recipe.name() + "」が完成した!", NamedTextColor.GREEN));
        List<String> parts = bonus.lines();
        if (parts.isEmpty()) {
            player.sendMessage(Component.text("  ステータスの強化: なし", NamedTextColor.GRAY));
        } else {
            for (String line : parts) {
                player.sendMessage(Component.text("  ▲ " + line, NamedTextColor.AQUA));
            }
        }
        // a big title so the enhancement (what and how much) cannot be missed
        player.showTitle(Title.title(
                Component.text(recipe.name() + (parts.isEmpty() ? "" : " " + "★".repeat(parts.size())), parts.isEmpty() ? NamedTextColor.WHITE : NamedTextColor.GOLD),
                Component.text(parts.isEmpty() ? "強化なし" : "強化: " + String.join(" / ", parts), parts.isEmpty() ? NamedTextColor.GRAY : NamedTextColor.AQUA),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(800))));
        if (after > before) {
            player.sendMessage(Component.text("[クラフト] クラフトレベルが " + after + " に上がった!", NamedTextColor.GOLD));
        }
        profileManager.save(player);
    }

    /** Builds the result item: every two stat bonuses lift it one rarity tier above the plain recipe item. */
    private ItemStack createResult(RpgItemDefinition definition, CraftRecipe recipe, int level) {
        CraftBonus bonus = rollBonus(recipe, level, definition);
        Rarity rarity = definition.rarity().up(bonus.count() / 2);
        ItemStack item = itemFactory.createCrafted(definition, rarity, bonus.stats(), bonus.elements());
        itemIdentifier.setBonusElementStats(item, bonus.elements());
        return item;
    }

    // ---- random stats: cap by the game's current scale, value fully random below the cap -----------------------

    private static final java.util.Set<StatType> DEBUFF_STATS = java.util.EnumSet.of(
            StatType.BLEED, StatType.CORROSION, StatType.DECAY, StatType.LACERATION, StatType.CONFUSION, StatType.EXPLOSION);

    /** The maximum of each stat at the top craft level (config craft-caps.stat-max overrides these). */
    private static final Map<String, Double> DEFAULT_MAX = Map.ofEntries(
            Map.entry("max-hp", 2000.0), Map.entry("max-mp", 2000.0), Map.entry("hp-regen", 100.0), Map.entry("mp-regen", 100.0),
            Map.entry("weapon-damage", 50.0), Map.entry("strength", 1000.0), Map.entry("magic", 1000.0),
            Map.entry("strength-percent", 10.0), Map.entry("magic-percent", 10.0),
            Map.entry("melee-damage", 2000.0), Map.entry("range-damage", 2000.0), Map.entry("deploy-damage", 2000.0),
            Map.entry("melee-damage-percent", 20.0), Map.entry("range-damage-percent", 20.0), Map.entry("deploy-damage-percent", 20.0),
            Map.entry("crit-damage", 100.0), Map.entry("crit-chance", 20.0), Map.entry("magic-overload", 200.0),
            Map.entry("stability", 20.0), Map.entry("duration", 20.0), Map.entry("attack-speed", 20.0), Map.entry("move-speed", 50.0),
            Map.entry("defense", 2000.0), Map.entry("physical-resist", 100.0), Map.entry("magic-resist", 100.0),
            Map.entry("damage-reduction", 20.0), Map.entry("special-damage", 2000.0),
            Map.entry("absorb-percent", 5.0), Map.entry("slow-percent", 30.0),
            Map.entry("element-damage", 2000.0), Map.entry("element-damage-percent", 15.0), Map.entry("element-resist", 20.0));

    /** The largest value of a stat at craft level {@code maxLevel}, as shown to the player (resists in percent). */
    public double maxAtTopLevel(BonusOption option, RpgItemDefinition definition) {
        var config = plugin.getConfig();
        if (!option.isElement() && DEBUFF_STATS.contains(option.stat())) {
            // other debuffs: a multiple of the weapon's own weapon damage
            double weaponDamage = definition == null ? 0.0 : definition.stats().get(StatType.WEAPON_DAMAGE);
            return config.getDouble("craft-caps.debuff-weapon-multiplier", 200.0) * weaponDamage;
        }
        // config.yml may be an older copy without newer keys, so every key has a built-in default
        return config.getDouble("craft-caps.stat-max." + option.capKey(), DEFAULT_MAX.getOrDefault(option.capKey(), 0.0));
    }

    /**
     * The highest value a random stat can get at this craft level: the stat's maximum at the top craft level,
     * scaled in proportion to the level (so a low craft level never gets a top-level value). Shown in whole numbers.
     */
    public int capFor(BonusOption option, int craftLevel, RpgItemDefinition definition) {
        double share = Math.max(0.0, Math.min(1.0, craftLevel / (double) maxLevel()));
        return (int) Math.max(1L, Math.round(maxAtTopLevel(option, definition) * share));
    }

    /**
     * Random stats: each of up to (1 + level / step) slots is filled with a chance that grows with the craft level
     * (so there may be none), with a stat picked at random from the recipe's pool, and a value that is completely
     * random from 1 up to that stat's cap at this level (the cap grows in proportion to the craft level).
     */
    CraftBonus rollBonus(CraftRecipe recipe, int level, RpgItemDefinition definition) {
        var config = plugin.getConfig();
        double chance = Math.min(config.getDouble("crafting.max-chance", 0.85),
                config.getDouble("crafting.base-chance", 0.2) + config.getDouble("crafting.chance-per-level", 0.05) * level);
        int slots = Math.min(Math.max(1, config.getInt("crafting.max-slots", 4)),
                1 + level / Math.max(1, config.getInt("crafting.slots-level-step", 4)));

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<BonusOption> pool = new ArrayList<>(recipe.bonusPool());
        Collections.shuffle(pool, random);

        CraftBonus bonus = CraftBonus.empty();
        int filled = 0;
        for (int slot = 0; slot < slots && filled < pool.size(); slot++) {
            if (random.nextDouble() >= chance) continue;

            BonusOption option = pool.get(filled++);
            double value = 1 + random.nextInt(capFor(option, level, definition));
            if (option.isElement()) {
                option.kind().add(bonus.elements(), option.element(), value);
            } else {
                // resists are stored as fractions (0.01 = 1%) but rolled in percent
                bonus.stats().add(option.stat(), StatLabels.isFraction(option.stat()) ? value / 100.0 : value);
            }
        }
        return bonus;
    }

    /** The random stats stored on a crafted item (for the result screen). */
    public CraftBonus bonusOf(ItemStack item) {
        return new CraftBonus(itemIdentifier.bonusStats(item), itemIdentifier.bonusElementStats(item));
    }

    /** A craft in progress is finished at once if the player leaves (so nothing is lost). */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Runnable done = pending.remove(event.getPlayer().getUniqueId());
        if (done != null) done.run();
    }
}
