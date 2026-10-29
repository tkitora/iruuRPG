package net.tkgon.mc.iruuRPG.bgm;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** How loud the BGM is for each player (0 to 100 %, saved in the player's own data, cached while online). The settings menu changes it; the player of the BGM reads it every tick. */
public final class BgmSettings implements Listener {

    public static final int STEP = 10;

    private final Map<UUID, Integer> cache = new ConcurrentHashMap<>();
    private final NamespacedKey percentKey;
    private volatile Function<Player, String> nowPlaying = player -> null;
    private volatile Bgm bgm = Bgm.NONE;

    public BgmSettings(JavaPlugin plugin) {
        this.percentKey = new NamespacedKey(plugin, "bgm_percent");
    }

    /** The loudness the player chose, in percent (100 = as the song is). */
    public int percent(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> {
            Integer stored = player.getPersistentDataContainer().get(percentKey, PersistentDataType.INTEGER);
            return stored == null ? 100 : Math.max(0, Math.min(100, stored));
        });
    }

    public void setPercent(Player player, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        cache.put(player.getUniqueId(), clamped);
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (clamped == 100) container.remove(percentKey); else container.set(percentKey, PersistentDataType.INTEGER, clamped);
    }

    /** The title of the song the player hears now, or null (the settings menu shows it). */
    public String nowPlaying(Player player) {
        return nowPlaying.apply(player);
    }

    /** What the rest of the plugin may ask of the music ({@link Bgm#NONE} while the BGM is not running). */
    public Bgm bgm() {
        return bgm;
    }

    void bgm(Bgm bgm) {
        this.bgm = bgm == null ? Bgm.NONE : bgm;
    }

    void nowPlaying(Function<Player, String> source) {
        this.nowPlaying = source == null ? player -> null : source;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
