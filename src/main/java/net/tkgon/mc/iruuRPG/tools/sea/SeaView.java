package net.tkgon.mc.iruuRPG.tools.sea;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * (t302) A sea only one player sees, for building by the shore: water at one height round the player, in the air blocks there only (nothing else is covered),
 * sent to that player's client alone (nothing in the world changes). It follows the player: every half second the water is sent where it is missing near
 * them and taken back (the real block sent) where they have gone from; a chunk the client is sent again gets its water again. {@code /iruurpg sea <y>} sets the
 * height and shows it, {@code /iruurpg sea true|false} shows or hides it. Kept while the player is on (not across a restart).
 */
public final class SeaView implements Listener {

    /** How far round the player the sea reaches (blocks; {@code sea.radius} of the config). */
    static final int DEFAULT_RADIUS = 48;

    private final JavaPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final BlockData water = Material.WATER.createBlockData();
    private BukkitTask task;

    /** A player's sea: its height, whether it is shown, and the blocks shown as water now (in {@link #world}). */
    static final class State {
        int y = 62;
        boolean on;
        World world;
        final Set<Long> shown = new HashSet<>();
    }

    private SeaView(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the sea view: its listener and its task. Never throws: a failure is logged and there is no sea view. */
    public static SeaView start(JavaPlugin plugin) {
        try {
            SeaView view = new SeaView(plugin);
            plugin.getServer().getPluginManager().registerEvents(view, plugin);
            view.task = Bukkit.getScheduler().runTaskTimer(plugin, view::tick, 20L, 10L);
            return view;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Sea view not started: " + exception);
            return null;
        }
    }

    /** The plugin stops: every sea shown is taken back. */
    public void stop() {
        if (task != null) task.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            State state = states.get(player.getUniqueId());
            if (state != null) restore(player, state);
        }
        states.clear();
    }

    int radius() {
        return Math.max(8, Math.min(96, plugin.getConfig().getInt("sea.radius", DEFAULT_RADIUS)));
    }

    /** Sets the player's sea height and shows it. */
    public String setHeight(Player player, int y) {
        State state = states.computeIfAbsent(player.getUniqueId(), unused -> new State());
        World world = player.getWorld();
        int clamped = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, y));
        if (state.on && clamped != state.y) restore(player, state);
        state.y = clamped;
        state.on = true;
        refresh(player, state);
        return "海を y=" + clamped + " に見せます (あなたにだけ、まわり " + radius() + " ブロックの空気だけ)。/iruurpg sea false で消す";
    }

    /** Shows or hides the player's sea. */
    public String show(Player player, boolean on) {
        State state = states.computeIfAbsent(player.getUniqueId(), unused -> new State());
        if (on == state.on) return on ? "海はもう見えています (y=" + state.y + ")。" : "海はもう消えています。";
        state.on = on;
        if (on) {
            refresh(player, state);
            return "海を見せます (y=" + state.y + ")。";
        }
        restore(player, state);
        return "海を消しました。";
    }

    /** What the player's sea is. */
    public String status(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null) return "海: 表示なし (/iruurpg sea <y> で、その高さに見せる)";
        return "海: y=" + state.y + (state.on ? " 表示中" : " 非表示") + " (/iruurpg sea <y> / true / false)";
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            State state = states.get(player.getUniqueId());
            if (state != null && state.on) refresh(player, state);
        }
    }

    /** Sends the water missing near the player and takes back the water out of reach or where a block now is. */
    private void refresh(Player player, State state) {
        World world = player.getWorld();
        if (state.world != world) {
            state.shown.clear();
            state.world = world;
        }
        int y = state.y;
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;
        int radius = radius();
        int cx = player.getLocation().getBlockX();
        int cz = player.getLocation().getBlockZ();
        Map<Position, BlockData> changes = new HashMap<>();
        // what is shown and should not be any more
        List<Long> gone = new ArrayList<>();
        for (long key : state.shown) {
            int x = x(key);
            int z = z(key);
            boolean near = within(x - cx, z - cz, radius);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                gone.add(key);
                continue;
            }
            if (!near) {
                changes.put(Position.block(x, y, z), world.getBlockData(x, y, z));
                gone.add(key);
            } else if (!world.getBlockAt(x, y, z).getType().isAir()) {
                // (a block was put there: the client has it from the server already)
                gone.add(key);
            }
        }
        gone.forEach(state.shown::remove);
        // what should be shown and is not
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!within(dx, dz, radius)) continue;
                int x = cx + dx;
                int z = cz + dz;
                long key = key(x, z);
                if (state.shown.contains(key) || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
                if (!world.getBlockAt(x, y, z).getType().isAir()) continue;
                changes.put(Position.block(x, y, z), water);
                state.shown.add(key);
            }
        }
        if (!changes.isEmpty()) player.sendMultiBlockChange(changes);
    }

    /** Sends the real blocks back where water was shown, and forgets them. */
    private void restore(Player player, State state) {
        World world = state.world;
        if (world != null && player.isOnline() && player.getWorld().equals(world)) {
            Map<Position, BlockData> changes = new HashMap<>();
            for (long key : state.shown) {
                int x = x(key);
                int z = z(key);
                if (world.isChunkLoaded(x >> 4, z >> 4)) changes.put(Position.block(x, state.y, z), world.getBlockData(x, state.y, z));
            }
            if (!changes.isEmpty()) player.sendMultiBlockChange(changes);
        }
        state.shown.clear();
    }

    /** Whether {@code (dx, dz)} is within {@code radius} of the middle (a round sea). */
    static boolean within(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius + radius;
    }

    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int x(long key) {
        return (int) (key >> 32);
    }

    static int z(long key) {
        return (int) key;
    }

    /** A chunk was sent to the player again (it overwrote the water there): its water is sent again at the next refresh. */
    @EventHandler
    public void onChunk(PlayerChunkLoadEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state == null || state.shown.isEmpty()) return;
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        state.shown.removeIf(key -> x(key) >> 4 == chunkX && z(key) >> 4 == chunkZ);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state != null) state.shown.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}
