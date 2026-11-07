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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * (t302, t304) A sea only one player sees, for building by the shore: water at one height (81 unless set), in the air blocks there only (nothing else is
 * covered), sent to that player's client alone (nothing in the world changes). It is laid chunk by chunk over the square of chunks round the player's
 * ({@code sea.chunks}, 6 each way), the nearest first and a few chunks a second; what was laid stays as the player moves on (the client keeps it until it is
 * sent that chunk again, which lays it again when it is in reach). Turned off (or moved to another height), the water is taken back (the real blocks sent) over
 * the square round the player, a few chunks a second; what lies beyond it stays until the client is sent those chunks again. {@code /iruurpg sea <y>} sets the
 * height and shows it, {@code /iruurpg sea true|false} shows or hides it. Kept while the player is on (not across a restart).
 */
public final class SeaView implements Listener {

    /** The height of the sea when none is set. */
    static final int DEFAULT_HEIGHT = 81;
    /** How many chunks each way from the player's the sea is laid (a square; {@code sea.chunks} of the config). */
    static final int DEFAULT_CHUNKS = 6;
    /** How many chunks are laid or taken back each update ({@code sea.chunks-per-update}). */
    static final int DEFAULT_BUDGET = 16;
    /** Ticks between the updates. */
    static final long PERIOD = 20L;

    private final JavaPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final BlockData water = Material.WATER.createBlockData();
    private BukkitTask task;

    /** A player's sea: its height, whether it is shown, the chunks laid with water (in {@link #world}), and the chunks to take it back from (by the height laid). */
    static final class State {
        int y = DEFAULT_HEIGHT;
        boolean on;
        World world;
        final Set<Long> laid = new HashSet<>();
        final Map<Long, Integer> clearing = new LinkedHashMap<>();
    }

    private SeaView(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the sea view: its listener and its task. Never throws: a failure is logged and there is no sea view. */
    public static SeaView start(JavaPlugin plugin) {
        try {
            SeaView view = new SeaView(plugin);
            plugin.getServer().getPluginManager().registerEvents(view, plugin);
            view.task = Bukkit.getScheduler().runTaskTimer(plugin, view::tick, 20L, PERIOD);
            return view;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Sea view not started: " + exception);
            return null;
        }
    }

    /** The plugin stops: the water round every player shown it is taken back at once. */
    public void stop() {
        if (task != null) task.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            State state = states.get(player.getUniqueId());
            if (state == null || state.world == null || !player.getWorld().equals(state.world)) continue;
            for (long chunk : square(player)) if (state.laid.contains(chunk)) clear(player, state.world, chunk, state.y);
        }
        states.clear();
    }

    int chunks() {
        return Math.max(1, Math.min(12, plugin.getConfig().getInt("sea.chunks", DEFAULT_CHUNKS)));
    }

    int budget() {
        return Math.max(1, Math.min(64, plugin.getConfig().getInt("sea.chunks-per-update", DEFAULT_BUDGET)));
    }

    /** Sets the player's sea height and shows it (the water at the height before is taken back round them). */
    public String setHeight(Player player, int y) {
        State state = state(player);
        World world = player.getWorld();
        int clamped = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, y));
        if (clamped != state.y) takeBack(player, state);
        state.y = clamped;
        state.on = true;
        return "海を y=" + clamped + " に見せます (あなたにだけ、まわり " + chunks() + " チャンクの空気だけ、近い所から少しずつ)。/iruurpg sea false で消す";
    }

    /** Shows or hides the player's sea. */
    public String show(Player player, boolean on) {
        State state = state(player);
        if (on == state.on) return on ? "海はもう見えています (y=" + state.y + ")。" : "海はもう消えています。";
        state.on = on;
        if (on) return "海を見せます (y=" + state.y + ")。";
        takeBack(player, state);
        return "海を消していきます (まわり " + chunks() + " チャンク。その外に残った水は、チャンクを読み直すと消えます)。";
    }

    /** What the player's sea is. */
    public String status(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null) return "海: 表示なし (/iruurpg sea <y> で、その高さに見せる。/iruurpg sea true で y=" + DEFAULT_HEIGHT + ")";
        return "海: y=" + state.y + (state.on ? " 表示中" : " 非表示") + " (/iruurpg sea <y> / true / false)";
    }

    private State state(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), unused -> new State());
        if (state.world == null) state.world = player.getWorld();
        return state;
    }

    /** The water laid in the square round the player is to be taken back (the chunks laid beyond it keep theirs, as the client does). */
    private void takeBack(Player player, State state) {
        for (long chunk : square(player)) if (state.laid.remove(chunk)) state.clearing.put(chunk, state.y);
        state.laid.clear();
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            State state = states.get(player.getUniqueId());
            if (state != null) update(player, state);
        }
    }

    /** Takes back a few chunks of water to take back, then lays a few chunks of water missing in the square round the player, the nearest first. */
    private void update(Player player, State state) {
        World world = player.getWorld();
        if (state.world != world) {
            state.laid.clear();
            state.clearing.clear();
            state.world = world;
        }
        int budget = budget();
        Iterator<Map.Entry<Long, Integer>> clearing = state.clearing.entrySet().iterator();
        while (budget > 0 && clearing.hasNext()) {
            Map.Entry<Long, Integer> entry = clearing.next();
            clearing.remove();
            clear(player, world, entry.getKey(), entry.getValue());
            budget--;
        }
        if (!state.on || state.y < world.getMinHeight() || state.y >= world.getMaxHeight()) return;
        for (long chunk : square(player)) {
            if (budget <= 0) break;
            if (state.laid.contains(chunk) || !world.isChunkLoaded(chunkX(chunk), chunkZ(chunk))) continue;
            lay(player, world, chunk, state.y);
            state.laid.add(chunk);
            state.clearing.remove(chunk);
            budget--;
        }
    }

    /** The chunks of the square round the player's, the nearest first. */
    List<Long> square(Player player) {
        int cx = player.getLocation().getBlockX() >> 4;
        int cz = player.getLocation().getBlockZ() >> 4;
        return square(cx, cz, chunks());
    }

    /** The chunks of the square {@code reach} chunks each way from {@code (cx, cz)}, the nearest first (by rings). */
    static List<Long> square(int cx, int cz, int reach) {
        List<Long> out = new ArrayList<>();
        out.add(chunk(cx, cz));
        for (int ring = 1; ring <= reach; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) out.add(chunk(cx + dx, cz + dz));
                }
            }
        }
        return out;
    }

    /** Water in the air blocks of the chunk at {@code y}, to the player. */
    private void lay(Player player, World world, long chunk, int y) {
        Map<Position, BlockData> changes = new HashMap<>();
        int x0 = chunkX(chunk) << 4;
        int z0 = chunkZ(chunk) << 4;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) if (world.getBlockAt(x, y, z).getType().isAir()) changes.put(Position.block(x, y, z), water);
        }
        if (!changes.isEmpty()) player.sendMultiBlockChange(changes);
    }

    /** The real blocks of the chunk at {@code y} back to the player (where the water was: the air). */
    private void clear(Player player, World world, long chunk, int y) {
        if (!world.isChunkLoaded(chunkX(chunk), chunkZ(chunk)) || y < world.getMinHeight() || y >= world.getMaxHeight()) return;
        Map<Position, BlockData> changes = new HashMap<>();
        int x0 = chunkX(chunk) << 4;
        int z0 = chunkZ(chunk) << 4;
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                if (world.getBlockAt(x, y, z).getType().isAir()) changes.put(Position.block(x, y, z), world.getBlockData(x, y, z));
            }
        }
        if (!changes.isEmpty()) player.sendMultiBlockChange(changes);
    }

    static long chunk(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int chunkX(long key) {
        return (int) (key >> 32);
    }

    static int chunkZ(long key) {
        return (int) key;
    }

    /** A chunk was sent to the player again (it overwrote the water there): it is laid again when it is in reach, and has nothing to take back. */
    @EventHandler
    public void onChunk(PlayerChunkLoadEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state == null) return;
        long chunk = chunk(event.getChunk().getX(), event.getChunk().getZ());
        state.laid.remove(chunk);
        state.clearing.remove(chunk);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state == null) return;
        state.laid.clear();
        state.clearing.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}
