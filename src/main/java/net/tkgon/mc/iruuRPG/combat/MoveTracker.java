package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Remembers which way each player last walked (the direction of the assassin's step). */
public final class MoveTracker implements Listener {

    /** A walk older than this does not count as "the way I am moving". */
    private static final long FRESH_MILLIS = 350L;

    private record Move(Vector direction, long atMillis) {
    }

    private final Map<UUID, Move> last = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        Vector delta = new Vector(to.getX() - from.getX(), 0.0, to.getZ() - from.getZ());
        if (delta.lengthSquared() < 0.0004) return;
        last.put(event.getPlayer().getUniqueId(), new Move(delta.normalize(), System.currentTimeMillis()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        last.remove(event.getPlayer().getUniqueId());
    }

    /** The horizontal direction the player is walking in, or null when they are standing still. */
    public Vector direction(Player player) {
        Move move = last.get(player.getUniqueId());
        if (move == null || System.currentTimeMillis() - move.atMillis() > FRESH_MILLIS) return null;
        return move.direction().clone();
    }
}
