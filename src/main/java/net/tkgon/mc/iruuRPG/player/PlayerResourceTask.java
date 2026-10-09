package net.tkgon.mc.iruuRPG.player;

import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerResourceTask {

    private PlayerResourceTask() {
    }

    public static void start(JavaPlugin plugin, PlayerProfileManager profileManager, PlayerBars playerBars) {
        long periodTicks = Math.max(1L, plugin.getConfig().getLong("player.mp-regen-period-ticks", 60L));

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                profileManager.get(player).ifPresent(profile -> regenerate(player, profile, playerBars));
            }
        }, periodTicks, periodTicks);
    }

    private static void regenerate(Player player, PlayerProfile profile, PlayerBars playerBars) {
        double hpRegen = profile.finalStats().get(StatType.HP_REGEN);
        if (hpRegen > 0.0 && profile.currentHp() < profile.maxHp()) {
            profile.setCurrentHp(profile.currentHp() + hpRegen);
        }

        double mpRegen = profile.finalStats().get(StatType.MP_REGEN);
        if (mpRegen > 0.0 && profile.currentMp() < profile.maxMp()) {
            profile.setCurrentMp(profile.currentMp() + mpRegen);
        }

        playerBars.sync(player, profile);
    }
}
