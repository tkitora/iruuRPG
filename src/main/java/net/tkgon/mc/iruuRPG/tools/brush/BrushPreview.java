package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows what a stroke would do to the player alone: the new blocks are sent to that player's client as block changes (nothing in the world changes, nothing is
 * sent to anyone else) and sent back to the real blocks after a few seconds. At most a set number of blocks is shown, so it stays light however big the brush is.
 */
final class BrushPreview {

    private final JavaPlugin plugin;
    private final Map<UUID, Shown> running = new HashMap<>();

    private record Shown(World world, List<Location> places, BukkitTask task) {
    }

    BrushPreview(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Shows {@code changes} (the first {@code cap} of them) for {@code ticks} ticks; a preview still showing is taken back first. */
    int show(Player player, World world, List<BrushEngine.Change> changes, int cap, int ticks) {
        clear(player);
        List<Location> places = new ArrayList<>();
        int shown = Math.min(cap, changes.size());
        // spread over the whole list when it is longer than the cap, so the preview covers the brush instead of one corner of it
        double step = changes.size() <= cap ? 1.0 : changes.size() / (double) cap;
        for (int index = 0; index < shown; index++) {
            BrushEngine.Change change = changes.get((int) Math.min(changes.size() - 1, Math.floor(index * step)));
            Location at = new Location(world, change.x(), change.y(), change.z());
            player.sendBlockChange(at, change.data());
            places.add(at);
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clear(player), ticks);
        running.put(player.getUniqueId(), new Shown(world, places, task));
        return shown;
    }

    /** Sends the real blocks back. */
    void clear(Player player) {
        Shown shown = running.remove(player.getUniqueId());
        if (shown == null) return;
        shown.task().cancel();
        if (!player.isOnline() || !player.getWorld().equals(shown.world())) return;
        for (Location at : shown.places()) player.sendBlockChange(at, shown.world().getBlockData(at));
    }

    void clearAll() {
        for (UUID id : new ArrayList<>(running.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) clear(player);
        }
    }
}
