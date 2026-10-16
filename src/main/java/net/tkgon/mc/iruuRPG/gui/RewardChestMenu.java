package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The reward chest screen: rainbow glass flickers for a moment, then the three rewards (gold, XP, an item) appear.
 * {@code onReveal} runs when the rewards appear (the caller hands them over then); {@code onClose} runs once when the
 * screen is closed (the caller writes the contents in chat then, and hands the rewards over if that did not happen yet).
 */
public final class RewardChestMenu implements Listener {

    private static final int SIZE = 27;
    private static final int[] REWARD_SLOTS = {11, 13, 15};
    private static final int FLICKER_TICKS = 30;
    private static final Material[] RAINBOW = {
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE
    };

    private final JavaPlugin plugin;

    public RewardChestMenu(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param gold     the gold display item
     * @param xp       the XP display item
     * @param item     the item that was given (a copy), or null
     * @param rare     a rare item: a longer, golden finish
     */
    public void open(Player player, Component title, ItemStack gold, ItemStack xp, ItemStack item, boolean rare, Runnable onReveal, Runnable onClose) {
        Holder holder = new Holder();
        holder.onClose = onClose;
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.inventory = inventory;
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                    cancel();
                    return;
                }
                if (tick < FLICKER_TICKS) {
                    // every slot flickers through the rainbow, faster and faster
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, pane(RAINBOW[random.nextInt(RAINBOW.length)]));
                    if (tick % 3 == 0) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.6f + tick * 0.04f);
                    tick += 2;
                    return;
                }
                // reveal (the rewards are handed over now)
                if (onReveal != null) onReveal.run();
                Material frame = rare ? Material.YELLOW_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
                for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, pane(frame));
                inventory.setItem(REWARD_SLOTS[0], gold);
                inventory.setItem(REWARD_SLOTS[1], xp);
                inventory.setItem(REWARD_SLOTS[2], item == null ? pane(Material.GRAY_STAINED_GLASS_PANE) : item);
                if (rare) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
                }
                cancel();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** A display item with a name and lines. */
    public static ItemStack display(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder && holder.onClose != null) {
            Runnable run = holder.onClose;
            holder.onClose = null;
            run.run();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static final class Holder implements MenuHolder {
        private Inventory inventory;
        private Runnable onClose;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
