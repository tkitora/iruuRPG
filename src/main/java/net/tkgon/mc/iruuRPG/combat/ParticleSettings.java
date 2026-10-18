package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's particle settings: how much of the particles they see (0 - 100 %) and whether the particles of other
 * players' attacks are shown at all. Saved in the player's own data and cached while they are online.
 */
public final class ParticleSettings implements Listener {

    public static final int STEP = 10;

    private final Map<UUID, Data> cache = new ConcurrentHashMap<>();
    /** The players whose settings are not the defaults (when nobody is in it, particles go out as usual, to everyone at once). */
    private final Set<UUID> custom = ConcurrentHashMap.newKeySet();
    private final NamespacedKey percentKey;
    private final NamespacedKey hideOthersKey;

    public ParticleSettings(JavaPlugin plugin) {
        this.percentKey = new NamespacedKey(plugin, "particle_percent");
        this.hideOthersKey = new NamespacedKey(plugin, "particle_hide_others");
        for (Player player : plugin.getServer().getOnlinePlayers()) data(player);
    }

    /** How much of the particles the player sees, in percent (100 = all). */
    public int percent(Player player) {
        return data(player).percent;
    }

    public void setPercent(Player player, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        Data data = data(player);
        data.percent = clamped;
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (clamped == 100) container.remove(percentKey); else container.set(percentKey, PersistentDataType.INTEGER, clamped);
        refresh(player.getUniqueId(), data);
    }

    /** True when the particles of other players' attacks are not shown to this player. */
    public boolean hideOthers(Player player) {
        return data(player).hideOthers;
    }

    public void setHideOthers(Player player, boolean hide) {
        Data data = data(player);
        data.hideOthers = hide;
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (hide) container.set(hideOthersKey, PersistentDataType.BYTE, (byte) 1); else container.remove(hideOthersKey);
        refresh(player.getUniqueId(), data);
    }

    /** True when at least one online player has settings other than the defaults. */
    public boolean anyCustom() {
        return !custom.isEmpty();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        data(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
        custom.remove(event.getPlayer().getUniqueId());
    }

    private void refresh(UUID id, Data data) {
        if (data.percent < 100 || data.hideOthers) custom.add(id); else custom.remove(id);
    }

    private Data data(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> {
            Data data = new Data();
            PersistentDataContainer container = player.getPersistentDataContainer();
            Integer stored = container.get(percentKey, PersistentDataType.INTEGER);
            data.percent = stored == null ? 100 : Math.max(0, Math.min(100, stored));
            data.hideOthers = container.has(hideOthersKey, PersistentDataType.BYTE);
            refresh(id, data);
            return data;
        });
    }

    private static final class Data {
        private int percent = 100;
        private boolean hideOthers;
    }
}
