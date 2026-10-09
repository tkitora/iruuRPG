package net.tkgon.mc.iruuRPG.hud;

import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.player.LevelService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerHudTask {

    private PlayerHudTask() {
    }

    public static void start(JavaPlugin plugin, PlayerProfileManager profileManager, PlayerBars playerBars, LevelService levelService, PlayerHud playerHud) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                profileManager.get(player).ifPresent(profile -> {
                    playerBars.sync(player, profile);
                    levelService.syncBar(player, profile);
                    playerHud.send(player, profile);
                });
            }
        }, 0L, 10L);
    }
}
