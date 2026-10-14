package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.crafting.BonusOption;
import net.tkgon.mc.iruuRPG.crafting.CraftRecipe;
import net.tkgon.mc.iruuRPG.crafting.CraftService;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.stat.StatLabels;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The crafting screens, all inside the inventory and framed with glass panes (one calm color, rainbow while crafting):
 * list (wipes in when opened) -> confirm (wipes in after picking a recipe) -> working (anvil hits, progress bar,
 * flashing panes) -> result (the item and its random stats).
 */
public final class CraftMenu implements Listener {

    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int[] RECIPE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int CENTER = 22;
    private static final int BUTTON_LEFT = 38;
    private static final int BUTTON_RIGHT = 42;
    // confirm screen: materials in a column on the left, the finished item top right, yes / no bottom right
    private static final int[] MATERIAL_SLOTS = {10, 19, 28, 37, 11, 20, 29, 38};
    private static final int PREVIEW_SLOT = 15;
    private static final int YES_SLOT = 42;
    private static final int NO_SLOT = 43;
    /** The one calm color of the list and confirm screens (and of their transitions). */
    private static final Material CALM = Material.CYAN_STAINED_GLASS_PANE;
    private static final Material[] RAINBOW = {
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE
    };

    private enum Screen { LIST, CONFIRM, WORKING, RESULT }

    private final JavaPlugin plugin;
    private final CraftService service;
    private final RpgItemRegistry itemRegistry;
    private final RpgItemFactory itemFactory;

    public CraftMenu(JavaPlugin plugin, CraftService service, RpgItemRegistry itemRegistry, RpgItemFactory itemFactory) {
        this.plugin = plugin;
        this.service = service;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
    }

    // ---- entry ---------------------------------------------------------------------------------------------

    /** Opens the crafting list; the colored border wipes in from the left. */
    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("クラフト"));
        holder.setInventory(inventory);
        holder.screen = Screen.LIST;
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.3f, 1.6f);
        wipeIn(player, holder, () -> renderList(player, holder));
    }

    // ---- screens ------------------------------------------------------------------------------------------------

    private void renderList(Player player, Holder holder) {
        holder.screen = Screen.LIST;
        holder.slots.clear();
        Inventory inventory = holder.inventory;
        clearInside(inventory);
        decorate(holder, 0, false);

        int level = service.level(player);
        List<Component> info = new ArrayList<>();
        info.add(text("クラフトを重ねると、完成品のステータスが強化されやすく、強くなる", NamedTextColor.GRAY));
        info.add(text("(強化が付かないこともある)", NamedTextColor.GRAY));
        int next = service.craftsToNextLevel(player);
        info.add(text(next > 0 ? "次のレベルまで、あと" + next + "回" : "最大レベル", NamedTextColor.YELLOW));
        holder.header = item(Material.ANVIL, "クラフトレベル " + level, NamedTextColor.GOLD, info, false);
        inventory.setItem(INFO_SLOT, holder.header);

        List<CraftRecipe> recipes = service.registry().all();
        for (int index = 0; index < recipes.size() && index < RECIPE_SLOTS.length; index++) {
            CraftRecipe recipe = recipes.get(index);
            inventory.setItem(RECIPE_SLOTS[index], recipeItem(player, recipe, level));
            holder.slots.put(RECIPE_SLOTS[index], recipe.id());
        }
    }

    private void renderConfirm(Player player, Holder holder, CraftRecipe recipe) {
        holder.screen = Screen.CONFIRM;
        holder.recipe = recipe;
        holder.slots.clear();
        Inventory inventory = holder.inventory;
        clearInside(inventory);
        decorate(holder, 0, false);

        int level = service.level(player);
        holder.header = item(Material.PAPER, "この装備を作りますか?", NamedTextColor.YELLOW, List.of(
                text("素材は、作り始めると消えます", NamedTextColor.GRAY)), false);
        inventory.setItem(4, holder.header);

        // left: the materials, one icon each, top to bottom
        int index = 0;
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            if (index >= MATERIAL_SLOTS.length) break;
            int have = service.count(player, entry.getKey());
            Material icon = itemRegistry.find(entry.getKey()).map(RpgItemDefinition::material).orElse(Material.PAPER);
            ItemStack stack = item(icon, service.itemName(entry.getKey()), have >= entry.getValue() ? NamedTextColor.GREEN : NamedTextColor.RED,
                    List.of(text("所持 " + have + " / 必要 " + entry.getValue(), NamedTextColor.GRAY)), false);
            stack.setAmount(Math.max(1, Math.min(64, entry.getValue())));
            inventory.setItem(MATERIAL_SLOTS[index++], stack);
        }

        // top right: the finished item, with the enhancements it might get
        inventory.setItem(PREVIEW_SLOT, previewItem(recipe, level));

        // bottom right: yes / no side by side
        boolean ready = level >= recipe.minCraftLevel() && service.hasIngredients(player, recipe);
        inventory.setItem(YES_SLOT, item(ready ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "はい", ready ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of(text(ready ? "クリックで、カンカンと叩き始める" : !service.hasIngredients(player, recipe) ? "素材が足りない" : "いまは作れない",
                        ready ? NamedTextColor.WHITE : NamedTextColor.RED)), ready));
        inventory.setItem(NO_SLOT, item(Material.RED_CONCRETE, "いいえ", NamedTextColor.RED, List.of(text("リストに戻る", NamedTextColor.GRAY)), false));
        holder.slots.put(YES_SLOT, "craft");
        holder.slots.put(NO_SLOT, "back");
    }

    private ItemStack previewItem(CraftRecipe recipe, int level) {
        RpgItemDefinition definition = itemRegistry.find(recipe.resultItemId()).orElse(null);
        if (definition == null) return item(Material.PAPER, recipe.name(), NamedTextColor.WHITE, List.of(), false);

        ItemStack stack = itemFactory.create(definition);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
            lore.add(Component.empty());
            lore.add(text("- - - - - - - - - - - -", NamedTextColor.DARK_GRAY));
            addCandidates(lore, recipe, definition, level);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void startWorking(Player player, Holder holder, CraftRecipe recipe) {
        boolean started = service.craft(player, recipe,
                hit -> onHit(player, holder, hit),
                result -> onComplete(player, holder, recipe, result));
        if (!started) {
            renderConfirm(player, holder, recipe);
            return;
        }
        holder.screen = Screen.WORKING;
        holder.slots.clear();
        clearInside(holder.inventory);
        holder.header = item(Material.ANVIL, "カンカン…", NamedTextColor.GOLD, List.of(text("叩いて、形にしている", NamedTextColor.GRAY)), true);
        decorate(holder, 0, true);
        // the finished item stays in view, top right (what it gets is revealed when the hammering is done)
        holder.inventory.setItem(PREVIEW_SLOT, previewItem(recipe, service.level(player)));
        holder.inventory.setItem(CENTER, item(Material.ANVIL, "カンカン…", NamedTextColor.GOLD, List.of(text("叩いて、形にしている", NamedTextColor.GRAY)), false));
        int index = 0;
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            if (index >= 3) break; // rows 1-3 only: the progress bar takes the bottom row
            Material icon = itemRegistry.find(entry.getKey()).map(definition -> definition.material()).orElse(Material.PAPER);
            holder.inventory.setItem(MATERIAL_SLOTS[index++], item(icon, service.itemName(entry.getKey()), NamedTextColor.GRAY, List.of(), false));
        }
        for (int slot = 37; slot <= 43; slot++) {
            holder.inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, "…", NamedTextColor.DARK_GRAY, List.of(), false));
        }

        // between the hits the border shimmers and the anvil "bounces"
        BukkitTask shimmer = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (holder.screen != Screen.WORKING || !isViewing(player, holder)) {
                    cancel();
                    return;
                }
                tick++;
                decorate(holder, tick, true);
                holder.inventory.setItem(CENTER - 9 * (tick % 2), null);
                holder.inventory.setItem(CENTER - 9 * ((tick + 1) % 2), item(Material.ANVIL, "カンカン…", NamedTextColor.GOLD,
                        List.of(text("叩いて、形にしている", NamedTextColor.GRAY)), false));
            }
        }.runTaskTimer(plugin, 2L, 2L);
        holder.tasks.add(shimmer);
    }

    private void onHit(Player player, Holder holder, int hit) {
        if (holder.screen != Screen.WORKING || !isViewing(player, holder)) return;

        for (int slot : progressSlots(hit)) {
            holder.inventory.setItem(slot, item(Material.LIME_STAINED_GLASS_PANE, hit + " / 4", NamedTextColor.GREEN, List.of(), true));
        }
        // a flash of orange/yellow panes on every hit
        Material flash = hit % 2 == 0 ? Material.ORANGE_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
        for (int slot = 0; slot < SIZE; slot++) {
            if (isBorder(slot) && slot != INFO_SLOT) holder.inventory.setItem(slot, pane(flash));
        }
    }

    private void onComplete(Player player, Holder holder, CraftRecipe recipe, ItemStack result) {
        holder.result = result;
        if (holder.screen != Screen.WORKING || !isViewing(player, holder)) return;

        holder.screen = Screen.RESULT;
        holder.slots.clear();
        Inventory inventory = holder.inventory;
        clearInside(inventory);
        decorate(holder, 7, true);
        inventory.setItem(CENTER, result.clone());
        holder.header = item(Material.NETHER_STAR, "完成!", NamedTextColor.GOLD, List.of(
                text(recipe.name() + " を作った。持ち物に入っている", NamedTextColor.GRAY)), true);
        inventory.setItem(4, holder.header);

        // the random stats, spelled out
        ItemMeta meta = result.getItemMeta();
        List<Component> lines = new ArrayList<>();
        boolean any = false;
        for (String line : service.bonusOf(result).lines()) {
            lines.add(text("  ▲ " + line, NamedTextColor.AQUA));
            any = true;
        }
        if (!any) lines.add(text("  ステータスの強化は付かなかった", NamedTextColor.GRAY));
        inventory.setItem(31, item(any ? Material.AMETHYST_SHARD : Material.GRAY_DYE,
                any ? "ステータスが強化された" : "強化なし", any ? NamedTextColor.AQUA : NamedTextColor.GRAY, lines, any));
        inventory.setItem(BUTTON_LEFT, item(Material.ANVIL, "もう一度作る", NamedTextColor.GREEN, List.of(text("リストに戻る", NamedTextColor.GRAY)), false));
        inventory.setItem(BUTTON_RIGHT, item(Material.BARRIER, "閉じる", NamedTextColor.RED, List.of(), false));
        holder.slots.put(BUTTON_LEFT, "back");
        holder.slots.put(BUTTON_RIGHT, "close");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);

        // sparkle: the border runs through the rainbow for a moment
        BukkitTask sparkle = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (holder.screen != Screen.RESULT || !isViewing(player, holder) || tick > 20) {
                    cancel();
                    return;
                }
                decorate(holder, tick++, true);
            }
        }.runTaskTimer(plugin, 2L, 2L);
        holder.tasks.add(sparkle);
    }

    // ---- clicks ----------------------------------------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.ownerId.equals(player.getUniqueId())) return;
        if (holder.transition) return; // ignore clicks while a wipe is running

        String action = holder.slots.get(event.getRawSlot());
        if (action == null) return;

        switch (holder.screen) {
            case LIST -> service.registry().find(action).ifPresent(recipe -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                wipeIn(player, holder, () -> renderConfirm(player, holder, recipe));
            });
            case CONFIRM -> {
                if (action.equals("craft") && holder.recipe != null) {
                    startWorking(player, holder, holder.recipe);
                } else if (action.equals("back")) {
                    wipeIn(player, holder, () -> renderList(player, holder));
                }
            }
            case RESULT -> {
                if (action.equals("back")) {
                    wipeIn(player, holder, () -> renderList(player, holder));
                } else if (action.equals("close")) {
                    player.closeInventory();
                }
            }
            case WORKING -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder) {
            holder.cancelTasks();
        }
    }

    // ---- animation helpers -------------------------------------------------------------------------------------------

    /**
     * Screen change: colored glass sweeps in from the left and covers the screen, then the new screen is revealed
     * from the left, column by column, as the glass clears away.
     */
    private void wipeIn(Player player, Holder holder, Runnable then) {
        holder.cancelTasks();
        holder.transition = true;
        BukkitTask task = new BukkitRunnable() {
            private int tick = -1;
            private ItemStack[] target;

            @Override
            public void run() {
                if (!isViewing(player, holder)) {
                    holder.transition = false;
                    cancel();
                    return;
                }
                tick++;
                if (tick <= 8) {
                    // cover: one column of glass per tick
                    for (int slot = 0; slot < SIZE; slot++) {
                        if (slot % 9 == tick) holder.inventory.setItem(slot, pane(CALM));
                    }
                    if (tick == 0) player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.0f);
                    return;
                }
                if (tick == 9) {
                    // hold a beat, then build the new screen off to the side and cover its inside with glass again
                    then.run();
                    target = holder.inventory.getContents().clone();
                    for (int slot = 0; slot < SIZE; slot++) {
                        if (!isBorder(slot)) holder.inventory.setItem(slot, pane(CALM));
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, 1.4f);
                    return;
                }
                // reveal: two ticks per column, columns 1..7 (the border stays)
                int column = 1 + (tick - 10) / 2;
                if ((tick - 10) % 2 == 0 && column <= 7) {
                    for (int slot = 0; slot < SIZE; slot++) {
                        if (slot % 9 == column && !isBorder(slot)) holder.inventory.setItem(slot, target[slot]);
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25f, 0.8f + 0.1f * column);
                }
                if (column > 7) {
                    holder.transition = false;
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        holder.tasks.add(task);
    }

    /**
     * Border of glass panes. Calm screens (list, confirm) use one color; only the crafting and the finished screen
     * run through the rainbow.
     */
    private void decorate(Holder holder, int phase, boolean colorful) {
        Inventory inventory = holder.inventory;
        for (int slot = 0; slot < SIZE; slot++) {
            if (isBorder(slot)) inventory.setItem(slot, pane(colorful ? RAINBOW[(slot + phase) % RAINBOW.length] : CALM));
        }
        // the title item sits in the top border; repainting must not wipe it
        if (holder.header != null) inventory.setItem(INFO_SLOT, holder.header);
    }

    private static boolean isBorder(int slot) {
        int row = slot / 9;
        int column = slot % 9;
        return row == 0 || row == 5 || column == 0 || column == 8;
    }

    private void clearInside(Inventory inventory) {
        for (int slot = 0; slot < SIZE; slot++) {
            if (!isBorder(slot)) inventory.setItem(slot, null);
        }
    }

    /** The progress bar is the middle row of seven slots, filling from the center outward so it is always symmetric. */
    private static int[] progressSlots(int hit) {
        return switch (hit) {
            case 1 -> new int[]{40};
            case 2 -> new int[]{39, 41};
            case 3 -> new int[]{38, 42};
            default -> new int[]{37, 43};
        };
    }

    private boolean isViewing(Player player, Holder holder) {
        return player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    // ---- items -------------------------------------------------------------------------------------------------------

    /** The stats a craft might enhance, with the highest value possible at the current craft level. */
    private void addCandidates(List<Component> lore, CraftRecipe recipe, RpgItemDefinition definition, int level) {
        lore.add(text("作ると、次のステータスが強化されることがある", NamedTextColor.AQUA));
        for (BonusOption option : recipe.bonusPool()) {
            lore.add(text("  " + option.label() + " +1〜" + service.capFor(option, level, definition)
                    + (option.percent() ? "%" : ""), NamedTextColor.AQUA));
        }
        lore.add(text("  (付かないこともある。付く数と値は完全にランダム)", NamedTextColor.GRAY));
    }

    /** A list entry: the finished item itself (its stats and description), then what it needs (kept short: tall tooltips get cut off). */
    private ItemStack recipeItem(Player player, CraftRecipe recipe, int level) {
        RpgItemDefinition definition = itemRegistry.find(recipe.resultItemId()).orElse(null);
        boolean levelOk = level >= recipe.minCraftLevel();
        boolean hasAll = service.hasIngredients(player, recipe);
        boolean ready = levelOk && hasAll;

        ItemStack stack = definition == null ? new ItemStack(Material.PAPER) : itemFactory.create(definition);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
        lore.add(text("- - - - - - - - - - - -", NamedTextColor.DARK_GRAY));
        lore.add(text("必要な素材", NamedTextColor.GRAY));
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            int have = service.count(player, entry.getKey());
            boolean enough = have >= entry.getValue();
            lore.add(text((enough ? "✔ " : "✖ ") + service.itemName(entry.getKey()) + " " + have + "/" + entry.getValue(),
                    enough ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        if (recipe.minCraftLevel() > 1) {
            lore.add(text("必要なクラフトレベル: " + recipe.minCraftLevel(), levelOk ? NamedTextColor.WHITE : NamedTextColor.RED));
        }
        lore.add(Component.empty());
        lore.add(text(ready ? "クリックで作る" : !levelOk ? "クラフトレベルが足りない" : "素材が足りない",
                ready ? NamedTextColor.GREEN : NamedTextColor.RED));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (ready) meta.addEnchant(Enchantment.LUCK, 1, true);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            stack.setItemMeta(meta);
        }
        return stack;
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
        private final Map<Integer, String> slots = new HashMap<>();
        private final List<BukkitTask> tasks = new ArrayList<>();
        private Inventory inventory;
        private Screen screen = Screen.LIST;
        private CraftRecipe recipe;
        private ItemStack result;
        private ItemStack header;
        private boolean transition;

        private Holder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void cancelTasks() {
            for (BukkitTask task : tasks) task.cancel();
            tasks.clear();
            transition = false;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
