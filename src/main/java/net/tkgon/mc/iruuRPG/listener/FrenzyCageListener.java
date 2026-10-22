package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.combat.ClassEffectService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * While the assassin's item swap skill (SATUJIN) lasts, the assassin cannot go out of a circle of {@code class-effects.assassin.frenzy-radius} blocks (15) round the place the
 * skill was used: a step that would cross it ends on the edge (the way a world border holds). Only the horizontal distance counts; the view is not touched.
 */
public final class FrenzyCageListener implements Listener {

    private final JavaPlugin plugin;
    private final ClassEffectService effects;

    public FrenzyCageListener(JavaPlugin plugin, ClassEffectService effects) {
        this.plugin = plugin;
        this.effects = effects;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location center = effects.frenzyCenter(player);
        if (center == null) return;
        Location to = event.getTo();
        if (to == null || to.getWorld() != center.getWorld()) return;
        double radius = plugin.getConfig().getDouble("class-effects.assassin.frenzy-radius", 15.0);
        double dx = to.getX() - center.getX();
        double dz = to.getZ() - center.getZ();
        double distanceSquared = dx * dx + dz * dz;
        if (distanceSquared <= radius * radius) return;
        double scale = (radius - 0.05) / Math.sqrt(distanceSquared);
        Location held = to.clone();
        held.setX(center.getX() + dx * scale);
        held.setZ(center.getZ() + dz * scale);
        event.setTo(held);
    }
}
