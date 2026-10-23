package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The preview that follows the player, which they toggle (the way the tree tool's does): while it is on, the result of one use of the brush where the player looks is shown
 * to that player alone (the new blocks, sent to that client; nothing in the world changes). It starts over when a setting changed or the block looked at moved, but only
 * after the player has left both alone for {@code brush.preview-delay-ticks} (15, 0.75 s), and the work is never started twice at once. The stroke is worked out on the
 * main thread (it reads the world), so it is held to a small radius ({@code brush.preview-max-radius}, 16) and to {@code brush.preview-blocks} blocks shown. One task serves all
 * players and stops with the last of them; it is cancelled when the plugin stops.
 */
final class BrushLivePreview {

    private static final class State {
        int revision = -1;
        long target = Long.MIN_VALUE;
        int dirtyAt;
        boolean pending;
        boolean tooBig;
    }

    private final BrushModule module;
    private final JavaPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private BukkitTask task;

    BrushLivePreview(BrushModule module) {
        this.module = module;
        this.plugin = module.plugin();
    }

    boolean isOn(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    void toggle(Player player) {
        if (isOn(player)) off(player, true);
        else on(player);
    }

    void on(Player player) {
        states.put(player.getUniqueId(), new State());
        player.sendMessage(BrushModule.PREFIX + "プレビューをオンにしました (あなたにだけ見えます)。見ている所に使った結果が、設定を変えるか見る所を動かして少し待つと更新されます。");
        ensureTask();
    }

    void off(Player player, boolean tell) {
        boolean was = states.remove(player.getUniqueId()) != null;
        module.preview().clear(player);
        if (tell && was) player.sendMessage(BrushModule.PREFIX + "プレビューをオフにしました。");
        if (states.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** A setting changed. */
    void touch(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null) return;
        state.dirtyAt = Bukkit.getCurrentTick();
        state.pending = true;
    }

    void shutdown() {
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) module.preview().clear(player);
        }
        states.clear();
        if (task != null) task.cancel();
        task = null;
    }

    private void ensureTask() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 3L, 3L);
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        int delay = Math.max(1, plugin.getConfig().getInt("brush.preview-delay-ticks", 15));
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            State state = states.get(id);
            if (player == null || !player.isOnline() || state == null) {
                states.remove(id);
                continue;
            }
            if (!player.getInventory().getItemInMainHand().hasItemMeta() || !module.isTool(player.getInventory().getItemInMainHand())) {
                // the brush is not in hand: nothing is shown
                if (state.target != Long.MIN_VALUE) {
                    module.preview().clear(player);
                    state.target = Long.MIN_VALUE;
                }
                continue;
            }
            Block block = module.target(player);
            long target = block == null ? Long.MIN_VALUE : SurfaceProbe.pack(block.getX(), block.getY(), block.getZ()) ^ block.getWorld().getUID().getMostSignificantBits();
            BrushSettings settings = module.settings(player);
            if (target != state.target || settings.revision != state.revision) {
                state.target = target;
                state.revision = settings.revision;
                state.dirtyAt = now;
                state.pending = true;
            }
            if (!state.pending || now - state.dirtyAt < delay) continue;
            state.pending = false;
            if (block == null) {
                module.preview().clear(player);
                continue;
            }
            int maxRadius = plugin.getConfig().getInt("brush.preview-max-radius", 16);
            if (settings.radius > maxRadius) {
                if (!state.tooBig) player.sendMessage(BrushModule.PREFIX + "半径が大きいので、プレビューは半径 " + maxRadius + " まで使えます。");
                state.tooBig = true;
                module.preview().clear(player);
                continue;
            }
            state.tooBig = false;
            BrushEngine.Result result = module.evaluate(player, block, Math.max(500, plugin.getConfig().getInt("brush.preview-blocks", 1500)));
            if (result.changes().isEmpty()) {
                module.preview().clear(player);
                continue;
            }
            module.preview().show(player, block.getWorld(), result.changes(), Math.max(100, plugin.getConfig().getInt("brush.preview-blocks", 1500)), 20 * 120);
        }
        if (states.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }
}
