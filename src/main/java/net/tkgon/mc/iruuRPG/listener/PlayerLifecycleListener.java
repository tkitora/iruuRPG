package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerLifecycleListener implements Listener {

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final EquipmentService equipmentService;
    private final PlayerBars playerBars;
    private final LevelService levelService;

    public PlayerLifecycleListener(JavaPlugin plugin, PlayerProfileManager profileManager, EquipmentService equipmentService, PlayerBars playerBars, LevelService levelService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.equipmentService = equipmentService;
        this.playerBars = playerBars;
        this.levelService = levelService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profileManager.load(event.getPlayer());
        PlayerProfile profile = equipmentService.recalculate(event.getPlayer());
        playerBars.sync(event.getPlayer(), profile);
        levelService.syncBar(event.getPlayer(), profile);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profileManager.unload(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerProfile profile = equipmentService.recalculate(event.getPlayer());
            profile.setCurrentHp(profile.maxHp());
            profile.setCurrentMp(profile.maxMp());
            playerBars.sync(event.getPlayer(), profile);
            levelService.syncBar(event.getPlayer(), profile);
        });
    }
}
