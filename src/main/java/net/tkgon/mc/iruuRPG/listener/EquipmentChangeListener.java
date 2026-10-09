package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EquipmentChangeListener implements Listener {

    private final JavaPlugin plugin;
    private final EquipmentService equipmentService;
    private final PlayerBars playerBars;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public EquipmentChangeListener(JavaPlugin plugin, EquipmentService equipmentService, PlayerBars playerBars) {
        this.plugin = plugin;
        this.equipmentService = equipmentService;
        this.playerBars = playerBars;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (isEquipmentRawSlot(event.getRawSlot())
                || event.isShiftClick()
                || isEquippable(event.getCurrentItem())
                || isEquippable(event.getCursor())) {
            scheduleRecalculate(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        for (int rawSlot : event.getRawSlots()) {
            if (isEquipmentRawSlot(rawSlot)) {
                scheduleRecalculate(player);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduleRecalculate(player);
        }
    }

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        scheduleRecalculate(event.getPlayer());
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        scheduleRecalculate(event.getPlayer());
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        scheduleRecalculate(event.getPlayer());
    }

    private void scheduleRecalculate(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pending.add(uuid)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!player.isOnline()) return;

                PlayerProfile profile = equipmentService.recalculate(player);
                playerBars.sync(player, profile);
            } finally {
                pending.remove(uuid);
            }
        });
    }

    private boolean isEquipmentRawSlot(int rawSlot) {
        return rawSlot >= 36 && rawSlot <= 40;
    }

    private boolean isEquippable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        try {
            return item.getType().getEquipmentSlot() != null;
        } catch (Throwable ignored) {
            String name = item.getType().name();
            return name.endsWith("_HELMET")
                    || name.endsWith("_CHESTPLATE")
                    || name.endsWith("_LEGGINGS")
                    || name.endsWith("_BOOTS")
                    || name.endsWith("_SWORD")
                    || name.endsWith("_AXE")
                    || name.equals("BOW")
                    || name.equals("CROSSBOW")
                    || name.equals("TRIDENT");
        }
    }
}
