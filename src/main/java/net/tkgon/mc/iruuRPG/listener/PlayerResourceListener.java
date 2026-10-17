package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public final class PlayerResourceListener implements Listener {

    private final PlayerProfileManager profileManager;
    private final PlayerBars playerBars;

    public PlayerResourceListener(PlayerProfileManager profileManager, PlayerBars playerBars) {
        this.profileManager = profileManager;
        this.playerBars = playerBars;
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        event.setCancelled(true);
        profileManager.get(player).ifPresent(profile -> playerBars.sync(player, profile));
    }

    /**
     * A player's health is the RPG HP shown on the hearts, set by the plugin (which fires no event). Vanilla natural
     * regeneration (a high food bar heals a heart every few seconds) raised the hearts above the RPG HP, and the next
     * sync lowered them again: the client plays the hurt motion on every drop. So vanilla healing is not allowed.
     */
    @EventHandler
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player) event.setCancelled(true);
    }
}
