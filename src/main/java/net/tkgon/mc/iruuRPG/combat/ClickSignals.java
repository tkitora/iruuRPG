package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which button the player really pressed. The server learns of a click from arm-swing packets and interaction
 * packets, and several things that are not a press look like one:
 * <ul>
 *   <li>the client answers an arm swing the plugin plays for the off hand with a swing packet of its own, and the server
 *   turns that into a left click in the air (the animation event of the off hand follows it in the same tick);</li>
 *   <li>a right button held down sends the click again every few ticks, with arm swings in between;</li>
 *   <li>the first moments after a heavy attack.</li>
 * </ul>
 * So a left click only counts ({@link #left}) when no right click came just before, no heavy attack has just been
 * thrown, and, if the plugin has just swung the off hand, the click was not the off hand's swing coming back.
 */
public final class ClickSignals implements Listener {

    private static JavaPlugin plugin;
    private static final Map<UUID, Integer> LAST_RIGHT_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> OFF_SWING_SENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> OFF_SWING_SEEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LEFT_LOCKED_UNTIL_TICK = new ConcurrentHashMap<>();

    /** A held right button repeats its click every 4 ticks; a left click this soon after one is not a press. */
    private static final int RIGHT_HELD_TICKS = 6;

    public ClickSignals(JavaPlugin owner) {
        plugin = owner;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            LAST_RIGHT_TICK.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.OFF_ARM_SWING) {
            OFF_SWING_SEEN.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        LAST_RIGHT_TICK.remove(id);
        OFF_SWING_SENT.remove(id);
        OFF_SWING_SEEN.remove(id);
        LEFT_LOCKED_UNTIL_TICK.remove(id);
    }

    /** The plugin swings the off hand of {@code player} now (their client will answer with a swing packet). */
    public static void offSwingSent(Player player) {
        OFF_SWING_SENT.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** No left click counts for the next {@code ticks} ticks (the recovery of a heavy attack). */
    public static void lockLeft(Player player, int ticks) {
        LEFT_LOCKED_UNTIL_TICK.put(player.getUniqueId(), Bukkit.getCurrentTick() + Math.max(0, ticks));
    }

    /** False when a left click arriving now cannot be a press: a right click came just before, or a heavy attack just started. */
    public static boolean leftPossible(Player player) {
        int now = Bukkit.getCurrentTick();
        Integer locked = LEFT_LOCKED_UNTIL_TICK.get(player.getUniqueId());
        if (locked != null && now < locked) return false;
        Integer right = LAST_RIGHT_TICK.get(player.getUniqueId());
        return right == null || now - right > RIGHT_HELD_TICKS;
    }

    private static boolean offSwingRecentlySent(Player player) {
        Long sent = OFF_SWING_SENT.get(player.getUniqueId());
        return sent != null && System.currentTimeMillis() - sent <= Math.max(300L, player.getPing() * 2L + 200L);
    }

    /**
     * Runs {@code action} for a left click that really was one. When the plugin has just swung the off hand, the click
     * might be that swing coming back: it is decided one tick later, when the off hand's animation event (which follows
     * the click in the same packet) has been seen or not.
     */
    public static void left(Player player, Runnable action) {
        if (!leftPossible(player)) return;
        if (!offSwingRecentlySent(player)) {
            action.run();
            return;
        }
        long clickedAt = System.currentTimeMillis();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Long seen = OFF_SWING_SEEN.get(player.getUniqueId());
            if (seen != null && seen >= clickedAt - 2L) return; // the off hand's swing, not a press
            if (player.isOnline() && leftPossible(player)) action.run();
        });
    }

    /** True when a left click handled right now might be the off hand's swing coming back (for handlers that cannot wait). */
    public static boolean echoSuspected(Player player) {
        return offSwingRecentlySent(player);
    }
}
