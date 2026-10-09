package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.gui.ClassSelectMenu;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
    private final ClassService classService;
    private final ClassSelectMenu classSelectMenu;

    public PlayerLifecycleListener(JavaPlugin plugin, PlayerProfileManager profileManager, EquipmentService equipmentService, PlayerBars playerBars, LevelService levelService, ClassService classService, ClassSelectMenu classSelectMenu) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.equipmentService = equipmentService;
        this.playerBars = playerBars;
        this.levelService = levelService;
        this.classService = classService;
        this.classSelectMenu = classSelectMenu;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profileManager.load(event.getPlayer());
        PlayerProfile profile = equipmentService.recalculate(event.getPlayer());
        playerBars.sync(event.getPlayer(), profile);
        levelService.syncBar(event.getPlayer(), profile);
        openClassSelectionIfNeeded(event.getPlayer(), profile);
    }

    private void openClassSelectionIfNeeded(Player player, PlayerProfile profile) {
        if (!plugin.getConfig().getBoolean("class-selection.open-on-join", true)) return;
        if (!classService.needsSelection(profile)) return;

        long delay = Math.max(1L, plugin.getConfig().getLong("class-selection.open-delay-ticks", 40L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && classService.needsSelection(profile)) {
                classSelectMenu.open(player);
            }
        }, delay);
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
