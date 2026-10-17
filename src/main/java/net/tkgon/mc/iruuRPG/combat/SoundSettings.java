package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's sound settings: how loud every {@link SoundKind} is (0 - 100 %) and whether the attack sounds of
 * other players are heard at all. Saved in the player's own data (so it survives logins and restarts) and cached
 * while the player is online.
 */
public final class SoundSettings implements Listener {

    public static final int STEP = 10;

    private final JavaPlugin plugin;
    private final Map<UUID, Data> cache = new ConcurrentHashMap<>();
    private final Map<SoundKind, NamespacedKey> volumeKeys = new EnumMap<>(SoundKind.class);
    private final NamespacedKey muteOthersKey;

    public SoundSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        for (SoundKind kind : SoundKind.values()) {
            volumeKeys.put(kind, new NamespacedKey(plugin, "sound_volume_" + kind.name().toLowerCase(java.util.Locale.ROOT)));
        }
        this.muteOthersKey = new NamespacedKey(plugin, "sound_mute_others");
    }

    /** Volume of one kind in percent (100 = as designed). */
    public int volume(Player player, SoundKind kind) {
        return data(player).volume.getOrDefault(kind, 100);
    }

    public void setVolume(Player player, SoundKind kind, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        data(player).volume.put(kind, clamped);
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (clamped == 100) {
            container.remove(volumeKeys.get(kind));
        } else {
            container.set(volumeKeys.get(kind), PersistentDataType.INTEGER, clamped);
        }
    }

    public void resetVolumes(Player player) {
        for (SoundKind kind : SoundKind.values()) {
            setVolume(player, kind, 100);
        }
    }

    /** True when the attack sounds of other players are heard (the default). */
    public boolean hearOthers(Player player) {
        return data(player).hearOthers;
    }

    public void setHearOthers(Player player, boolean hear) {
        data(player).hearOthers = hear;
        if (hear) {
            player.getPersistentDataContainer().remove(muteOthersKey);
        } else {
            player.getPersistentDataContainer().set(muteOthersKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    private Data data(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> load(player));
    }

    private Data load(Player player) {
        Data data = new Data();
        PersistentDataContainer container = player.getPersistentDataContainer();
        for (SoundKind kind : SoundKind.values()) {
            Integer stored = container.get(volumeKeys.get(kind), PersistentDataType.INTEGER);
            data.volume.put(kind, stored == null ? 100 : Math.max(0, Math.min(100, stored)));
        }
        data.hearOthers = !container.has(muteOthersKey, PersistentDataType.BYTE);
        return data;
    }

    private static final class Data {
        private final Map<SoundKind, Integer> volume = new EnumMap<>(SoundKind.class);
        private boolean hearOthers = true;
    }
}
