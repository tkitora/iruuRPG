package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.crafting.CraftRecipe;
import net.tkgon.mc.iruuRPG.crafting.CraftService;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The crafting list: pick what to make. Items you can make right now glow green. */
public final class CraftMenu implements Listener {

    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int[] RECIPE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final CraftService service;
    private final RpgItemRegistry itemRegistry;

    public CraftMenu(CraftService service, RpgItemRegistry itemRegistry) {
        this.service = service;
        this.itemRegistry = itemRegistry;
    }

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("クラフト"));
        holder.setInventory(inventory);

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of(), false);
        for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, filler);

        int level = service.level(player);
        List<Component> info = new ArrayList<>();
        info.add(text("クラフトレベルが上がると、完成品にランダムな強化が付きやすく、強くなる", NamedTextColor.GRAY));
        info.add(text("(強化が付かないこともある)", NamedTextColor.GRAY));
        int next = service.craftsToNextLevel(player);
        info.add(text(next > 0 ? "次のレベルまで、あと" + next + "回" : "最大レベル", NamedTextColor.YELLOW));
        inventory.setItem(INFO_SLOT, item(Material.ANVIL, "クラフトレベル " + level, NamedTextColor.GOLD, info, false));

        List<CraftRecipe> recipes = service.registry().all();
        for (int index = 0; index < recipes.size() && index < RECIPE_SLOTS.length; index++) {
            CraftRecipe recipe = recipes.get(index);
            inventory.setItem(RECIPE_SLOTS[index], recipeItem(player, recipe, level));
            holder.recipes.put(RECIPE_SLOTS[index], recipe.id());
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId.equals(player.getUniqueId())) return;

        String recipeId = holder.recipes.get(event.getRawSlot());
        if (recipeId == null) return;

        service.registry().find(recipeId).ifPresent(recipe -> {
            if (service.craft(player, recipe)) {
                player.closeInventory(); // watch the hammering; the list can be opened again afterwards
            } else {
                open(player);
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private ItemStack recipeItem(Player player, CraftRecipe recipe, int level) {
        Material icon = itemRegistry.find(recipe.resultItemId()).map(definition -> definition.material()).orElse(Material.PAPER);
        boolean levelOk = level >= recipe.minCraftLevel();
        boolean hasAll = service.hasIngredients(player, recipe);
        boolean ready = levelOk && hasAll;

        List<Component> lore = new ArrayList<>();
        lore.add(text("必要な素材", NamedTextColor.GRAY));
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            int have = service.count(player, entry.getKey());
            boolean enough = have >= entry.getValue();
            lore.add(text((enough ? "✔ " : "✖ ") + service.itemName(entry.getKey()) + " " + have + "/" + entry.getValue(),
                    enough ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        lore.add(Component.empty());
        lore.add(text("必要なクラフトレベル: " + recipe.minCraftLevel(), levelOk ? NamedTextColor.WHITE : NamedTextColor.RED));
        lore.add(text("ランダム強化の候補 (いまのレベルの上限)", NamedTextColor.AQUA));
        for (var type : recipe.bonusPool()) {
            lore.add(text("  " + net.tkgon.mc.iruuRPG.stat.StatLabels.name(type) + " +1〜" + service.capFor(type, level), NamedTextColor.AQUA));
        }
        lore.add(text("  (付かないこともある。付く数と値は完全にランダム)", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(text(ready ? "クリックで作る" : !levelOk ? "クラフトレベルが足りない" : "素材が足りない",
                ready ? NamedTextColor.GREEN : NamedTextColor.RED));
        return item(icon, recipe.name(), ready ? NamedTextColor.GREEN : NamedTextColor.GRAY, lore, ready);
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (glint) meta.addEnchant(Enchantment.LUCK, 1, true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static final class Holder implements InventoryHolder {
        private final UUID ownerId;
        private final Map<Integer, String> recipes = new HashMap<>();
        private Inventory inventory;

        private Holder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
