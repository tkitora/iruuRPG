package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.mob.DebugTargetService;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class DebugTargetListener implements Listener {

    private final JavaPlugin plugin;
    private final DebugTargetService debugTargetService;

    public DebugTargetListener(JavaPlugin plugin, DebugTargetService debugTargetService) {
        this.plugin = plugin;
        this.debugTargetService = debugTargetService;
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (!debugTargetService.isDebugTarget(event.getEntity())) return;

        event.setCancelled(true);
        event.getEntity().setFireTicks(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!debugTargetService.isDebugTarget(target)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!target.isValid() || target.isDead()) return;
            target.setFireTicks(0);
            debugTargetService.updateName(target);
        });
    }
}
