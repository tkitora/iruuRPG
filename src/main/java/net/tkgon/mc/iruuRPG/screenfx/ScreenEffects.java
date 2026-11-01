package net.tkgon.mc.iruuRPG.screenfx;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Screen effects drawn by the client's shaders (the shaders are in the marks' pack: {@link net.tkgon.mc.iruuRPG.markpack.ScreenShaders}).
 *
 * <p>The server cannot run a shader on a client; it can only show the client things its shaders recognise. An effect is switched on for one
 * player by a <b>marker</b>: an item display that only that player is sent, shrunk to nothing (so it is never seen), glowing with the color
 * (177, effect slot, strength). Because it glows, the game runs the post chain of the outlines every frame; the core shader of the outlines
 * draws the marker as a small square in the corner of the outline picture, wherever it is; the post chain reads the square there (the strength
 * from the blue), draws the effect, and takes the square away. The marker follows the player (a display with no size is never culled, so it
 * only has to be near).
 *
 * <p>Limits: vanilla rendering only (a shader pack of Iris or OptiFine replaces it); the effect is drawn over the opaque world (water, particles,
 * clouds, the hand and the GUI are drawn after it, so they do not glow themselves).
 */
public final class ScreenEffects implements Listener {

    /** The red of a marker's glow color (no team color has it). */
    public static final int MARKER_RED = 177;
    /** The strength of an effect: 1 - 255, 100 is the effect's usual strength. */
    public static final int MAX_STRENGTH = 255;
    public static final int NORMAL_STRENGTH = 100;
    private static final String TAG = "iruurpg_screenfx";
    /** How far the marker may be from its player before it is moved to them (blocks). */
    private static final double FOLLOW = 8.0;

    /** The effects; the slot is the marker's green and its place in the corner (slots 1 - 8 are read by the shaders). */
    public enum Effect {
        BLOOM(1, "ブルーム (明るいところが光ってにじむ)"),
        /** Black over the whole screen but the GUI, its alpha strength / 200 (100 gives 0.5): a plain check that the shaders work (t126). */
        DARKEN(2, "画面を暗くする (黒を重ねる。強さ 100 で不透明度 0.5、200 以上で真っ黒)");

        private final int slot;
        private final String summary;

        Effect(int slot, String summary) {
            this.slot = slot;
            this.summary = summary;
        }

        public int slot() {
            return slot;
        }

        public String summary() {
            return summary;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Effect find(String id) {
            for (Effect effect : values()) if (effect.id().equalsIgnoreCase(id)) return effect;
            return null;
        }
    }

    /** The glow color of an effect's marker. */
    public static Color markerColor(Effect effect, int strength) {
        return Color.fromRGB(MARKER_RED, effect.slot(), clampStrength(strength));
    }

    public static int clampStrength(int strength) {
        return Math.max(1, Math.min(MAX_STRENGTH, strength));
    }

    private static final class Marker {
        ItemDisplay display;
        int strength;
        /** The tick it ends, or -1 for when it is switched off. */
        long until;
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Map<Effect, Marker>> markers = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    private ScreenEffects(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the screen effects and their command ({@code /screenfx}); null when {@code screen-effects.enabled} is false. Never throws. */
    public static ScreenEffects start(JavaPlugin plugin) {
        try {
            if (!plugin.getConfig().getBoolean("screen-effects.enabled", true)) return null;
            ScreenEffects effects = new ScreenEffects(plugin);
            plugin.getServer().getPluginManager().registerEvents(effects, plugin);
            org.bukkit.command.PluginCommand command = plugin.getCommand("screenfx");
            if (command != null) {
                ScreenEffectsCommand handler = new ScreenEffectsCommand(effects);
                command.setExecutor(handler);
                command.setTabCompleter(handler);
            }
            effects.task = Bukkit.getScheduler().runTaskTimer(plugin, effects::tick, 5L, 5L);
            return effects;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Screen effects not started: " + exception);
            return null;
        }
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (Map<Effect, Marker> byEffect : markers.values()) for (Marker marker : byEffect.values()) remove(marker);
        markers.clear();
    }

    /**
     * Shows {@code effect} to {@code player} at {@code strength} (1 - 255, 100 usual) for {@code durationTicks} (0 or less: until it is switched
     * off); an effect already shown takes the new strength and time.
     */
    public void show(Player player, Effect effect, int strength, long durationTicks) {
        Marker marker = markers.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Effect.class)).computeIfAbsent(effect, e -> new Marker());
        marker.strength = clampStrength(strength);
        marker.until = durationTicks > 0 ? ticks + durationTicks : -1;
        if (marker.display != null && marker.display.isValid()) marker.display.setGlowColorOverride(markerColor(effect, marker.strength));
        else marker.display = spawn(player, effect, marker.strength);
    }

    /** Switches {@code effect} off for {@code player}; false when it was not on. */
    public boolean hide(Player player, Effect effect) {
        Map<Effect, Marker> byEffect = markers.get(player.getUniqueId());
        Marker marker = byEffect == null ? null : byEffect.remove(effect);
        if (marker == null) return false;
        remove(marker);
        if (byEffect.isEmpty()) markers.remove(player.getUniqueId());
        return true;
    }

    /** Switches every effect off for {@code player}; how many were on. */
    public int hideAll(Player player) {
        Map<Effect, Marker> byEffect = markers.remove(player.getUniqueId());
        if (byEffect == null) return 0;
        for (Marker marker : byEffect.values()) remove(marker);
        return byEffect.size();
    }

    /** The effects on for {@code player}, with their strength. */
    public Map<Effect, Integer> shown(Player player) {
        Map<Effect, Integer> shown = new EnumMap<>(Effect.class);
        Map<Effect, Marker> byEffect = markers.get(player.getUniqueId());
        if (byEffect != null) for (Map.Entry<Effect, Marker> entry : byEffect.entrySet()) shown.put(entry.getKey(), entry.getValue().strength);
        return shown;
    }

    /** Ends the effects whose time is over, and keeps each marker near its player (a new one in another world, or after it was unloaded). */
    private void tick() {
        ticks += 5;
        var players = markers.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Map<Effect, Marker>> entry = players.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            var effects = entry.getValue().entrySet().iterator();
            while (effects.hasNext()) {
                Map.Entry<Effect, Marker> byEffect = effects.next();
                Marker marker = byEffect.getValue();
                if (player == null || (marker.until >= 0 && ticks >= marker.until)) {
                    remove(marker);
                    effects.remove();
                    continue;
                }
                follow(player, byEffect.getKey(), marker);
            }
            if (entry.getValue().isEmpty()) players.remove();
        }
    }

    private void follow(Player player, Effect effect, Marker marker) {
        ItemDisplay display = marker.display;
        if (display != null && display.isValid() && display.getWorld().equals(player.getWorld())) {
            if (display.getLocation().distanceSquared(player.getLocation()) > FOLLOW * FOLLOW) display.teleport(player.getLocation());
            return;
        }
        remove(marker);
        marker.display = spawn(player, effect, marker.strength);
    }

    private ItemDisplay spawn(Player player, Effect effect, int strength) {
        Location at = player.getLocation();
        ItemDisplay display = player.getWorld().spawn(at, ItemDisplay.class, spawned -> {
            // seen by no one but the player it is shown to, and never saved with the world
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
            spawned.addScoreboardTag(TAG);
            spawned.setItemStack(new ItemStack(Material.STONE));
            // shrunk to nothing: it draws nothing in the world, only its marker in the outline picture
            spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0f, 0f, 0f), new AxisAngle4f()));
            spawned.setGlowing(true);
            spawned.setGlowColorOverride(markerColor(effect, strength));
        });
        player.showEntity(plugin, display);
        return display;
    }

    private static void remove(Marker marker) {
        if (marker.display != null && marker.display.isValid()) marker.display.remove();
        marker.display = null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideAll(event.getPlayer());
    }
}
