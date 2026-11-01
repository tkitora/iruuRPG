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
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
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
 * <p>A marker is sent only to a player whose client has the shaders (t128): the client tells the server how the marks' pack went (by the pack's
 * id); while it was declined, could not be downloaded or failed to load, the effect is kept but its marker is not sent (it is when the pack
 * loads). Whether a shader pack of Iris or OptiFine is on cannot be known: their APIs run in the client only and tell the server nothing (a
 * marker sent to such a player shows nothing, as it has no size).
 *
 * <p>Limits: vanilla rendering only (a shader pack of Iris or OptiFine replaces it); the effect is made from the opaque world (water,
 * particles, clouds and the hand are drawn after it, so they are not shifted themselves). (The bloom of t125 and the darkening of t126 were
 * taken away in t129.)
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
        /** Chromatic aberration (t129): red shifted out from the middle of the screen, blue in, more towards the sides; 100 shifts 1.2 % of the distance. */
        CHROMATIC(1, "色収差 (赤と青が、画面の外側ほどずれる。強さ 100 で、中心からの距離の 1.2 %)", false),
        /**
         * An impact at a place of the world (t130, the critical hits): the picture bulges out from where the place is on the screen, less and less
         * further from it; 100 takes the pixel at the place from 30 % nearer to it. Its marker stays at the place.
         */
        IMPACT(2, "衝撃 (ワールドの位置を中心に、画面が放射状にふくらむ。離れるほど弱い。クリティカルで出る)", true);

        private final int slot;
        private final String summary;
        private final boolean positional;

        Effect(int slot, String summary, boolean positional) {
            this.slot = slot;
            this.summary = summary;
            this.positional = positional;
        }

        public int slot() {
            return slot;
        }

        /** True for an effect centred on a place of the world: its marker is put there ({@link #showAt}) instead of following its player. */
        public boolean positional() {
            return positional;
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
        /** The place it is centred on (a positional effect), or null: it follows its player. */
        Location at;
        /** The tick its strength starts to fall from {@link #peak} to nothing at {@link #until}, or -1. */
        long fadeFrom = -1;
        int peak;
    }

    /** The strength at {@code now} of an effect at {@code peak} that fades from {@code fadeFrom} to nothing at {@code until} (at least 1 while it lasts). */
    static int fadedStrength(int peak, long fadeFrom, long until, long now) {
        if (fadeFrom < 0 || now < fadeFrom || until <= fadeFrom) return peak;
        return Math.max(1, (int) Math.round(peak * (double) (until - now) / (until - fadeFrom)));
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Map<Effect, Marker>> markers = new HashMap<>();
    /** The last status of the marks' pack (the one with the shaders) each player's client told. */
    private final Map<UUID, Status> packStatus = new HashMap<>();
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
            effects.task = Bukkit.getScheduler().runTaskTimer(plugin, effects::tick, 1L, 1L);
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
     * Whether a client with the marks' pack in this status has the shaders: yes when it loaded it or is getting it, and when nothing is known (no
     * pack was offered, or not yet); no when it declined it, could not download it, or it failed to load (a shader that does not compile does that).
     */
    static boolean hasShaders(Status status) {
        if (status == null) return true;
        return switch (status) {
            case SUCCESSFULLY_LOADED, ACCEPTED, DOWNLOADED -> true;
            default -> false;
        };
    }

    public boolean hasShaders(Player player) {
        return hasShaders(packStatus.get(player.getUniqueId()));
    }

    /** The last status of the marks' pack the player's client told (null: nothing yet). */
    public Status packStatus(Player player) {
        return packStatus.get(player.getUniqueId());
    }

    /**
     * Shows {@code effect} to {@code player} at {@code strength} (1 - 255, 100 usual) for {@code durationTicks} (0 or less: until it is switched
     * off); an effect already shown takes the new strength and time. False when the player's client has not the shaders: the effect is kept, and
     * its marker is sent when the pack loads.
     */
    public boolean show(Player player, Effect effect, int strength, long durationTicks) {
        Marker marker = markers.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Effect.class)).computeIfAbsent(effect, e -> new Marker());
        marker.strength = clampStrength(strength);
        marker.until = durationTicks > 0 ? ticks + durationTicks : -1;
        marker.at = null;
        marker.fadeFrom = -1;
        if (!hasShaders(player)) {
            remove(marker);
            return false;
        }
        if (marker.display != null && marker.display.isValid()) marker.display.setGlowColorOverride(markerColor(effect, marker.strength));
        else marker.display = spawn(player, effect, marker.strength, player.getLocation());
        return true;
    }

    /**
     * Shows {@code effect} to {@code player} centred on the place {@code at} (a positional effect: its marker is put there): at {@code strength}
     * for {@code holdTicks}, then fading to nothing over {@code fadeTicks}. Another call before it ends moves it and starts it again. False when
     * the player's client has not the shaders (as {@link #show}), or the place is in another world than the player.
     */
    public boolean showAt(Player player, Effect effect, Location at, int strength, int holdTicks, int fadeTicks) {
        if (at == null || at.getWorld() == null || !at.getWorld().equals(player.getWorld())) return false;
        Marker marker = markers.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Effect.class)).computeIfAbsent(effect, e -> new Marker());
        marker.peak = clampStrength(strength);
        marker.strength = marker.peak;
        marker.at = at.clone();
        marker.fadeFrom = ticks + Math.max(0, holdTicks);
        marker.until = marker.fadeFrom + Math.max(1, fadeTicks);
        if (!hasShaders(player)) {
            remove(marker);
            return false;
        }
        if (marker.display != null && marker.display.isValid() && marker.display.getWorld().equals(at.getWorld())) {
            marker.display.teleport(marker.at);
            marker.display.setGlowColorOverride(markerColor(effect, marker.strength));
        } else {
            remove(marker);
            marker.display = spawn(player, effect, marker.strength, marker.at);
        }
        return true;
    }

    /** A critical hit of {@code attacker} at {@code hit} (the middle of the one hit): the impact there, as {@code screen-effects.critical} says. */
    public void onCritical(Player attacker, Location hit) {
        if (attacker == null || hit == null || !plugin.getConfig().getBoolean("screen-effects.critical.enabled", true)) return;
        int strength = plugin.getConfig().getInt("screen-effects.critical.strength", NORMAL_STRENGTH);
        int hold = Math.max(0, Math.min(40, plugin.getConfig().getInt("screen-effects.critical.hold-ticks", 2)));
        int fade = Math.max(1, Math.min(60, plugin.getConfig().getInt("screen-effects.critical.fade-ticks", 6)));
        showAt(attacker, Effect.IMPACT, hit, strength, hold, fade);
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

    /**
     * Every tick: ends the effects whose time is over and fades the fading ones; every 5 ticks: keeps each marker near its player or at its
     * place (a new one in another world, or after it was unloaded). A marker is taken away while the player's client has not the shaders, and
     * made again when it has.
     */
    private void tick() {
        ticks++;
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
                int strength = fadedStrength(marker.peak, marker.fadeFrom, marker.until, ticks);
                if (marker.fadeFrom >= 0 && strength != marker.strength) {
                    marker.strength = strength;
                    if (marker.display != null && marker.display.isValid()) marker.display.setGlowColorOverride(markerColor(byEffect.getKey(), strength));
                }
                if (ticks % 5 == 0) follow(player, byEffect.getKey(), marker);
            }
            if (entry.getValue().isEmpty()) players.remove();
        }
    }

    private void follow(Player player, Effect effect, Marker marker) {
        if (!hasShaders(player)) {
            remove(marker);
            return;
        }
        ItemDisplay display = marker.display;
        if (marker.at != null) {
            // a positional effect stays at its place (gone when the player went to another world)
            if (display != null && display.isValid() && display.getWorld().equals(marker.at.getWorld())) return;
            remove(marker);
            if (marker.at.getWorld() != null && marker.at.getWorld().equals(player.getWorld())) marker.display = spawn(player, effect, marker.strength, marker.at);
            return;
        }
        if (display != null && display.isValid() && display.getWorld().equals(player.getWorld())) {
            if (display.getLocation().distanceSquared(player.getLocation()) > FOLLOW * FOLLOW) display.teleport(player.getLocation());
            return;
        }
        remove(marker);
        marker.display = spawn(player, effect, marker.strength, player.getLocation());
    }

    private ItemDisplay spawn(Player player, Effect effect, int strength, Location at) {
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, spawned -> {
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
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!net.tkgon.mc.iruuRPG.pack.ResourcePackService.packId("marks").equals(event.getID())) return;
        packStatus.put(event.getPlayer().getUniqueId(), event.getStatus());
        if (event.getStatus() == Status.FAILED_RELOAD) {
            plugin.getLogger().warning("screenfx: " + event.getPlayer().getName() + " could not load the marks' pack (FAILED_RELOAD): if the shaders"
                    + " do not compile on that client, screen-effects.enabled: false takes them out of the pack");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideAll(event.getPlayer());
        packStatus.remove(event.getPlayer().getUniqueId());
    }
}
