package net.tkgon.mc.iruuRPG.listener;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import net.tkgon.mc.iruuRPG.player.LevelService;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

public final class ExperienceListener implements Listener {

    private final LevelService levelService;

    public ExperienceListener(LevelService levelService) {
        this.levelService = levelService;
    }

    @EventHandler
    public void onPickup(PlayerPickupExperienceEvent event) {
        ExperienceOrb orb = event.getExperienceOrb();
        if (!levelService.isRpgExperienceOrb(orb)) return;

        event.setCancelled(true);
        if (levelService.isOrbForOtherPlayer(orb, event.getPlayer())) return;

        levelService.collectOrb(event.getPlayer(), orb);
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent event) {
        event.setAmount(0);
    }
}
