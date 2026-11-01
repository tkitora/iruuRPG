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
    /** The tick of the last right click on a block (RIGHT_CLICK_BLOCK). */
    private static final Map<UUID, Integer> LAST_RIGHT_BLOCK_TICK = new ConcurrentHashMap<>();
    /** The right click before the last one (a held button repeats: two clicks close together mean it is held). */
    private static final Map<UUID, Integer> PREV_RIGHT_TICK = new ConcurrentHashMap<>();
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
            Integer before = LAST_RIGHT_TICK.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
            if (before != null) PREV_RIGHT_TICK.put(event.getPlayer().getUniqueId(), before);
            if (action == Action.RIGHT_CLICK_BLOCK) LAST_RIGHT_BLOCK_TICK.put(event.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
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
        LAST_RIGHT_BLOCK_TICK.remove(id);
        PREV_RIGHT_TICK.remove(id);
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

    /**
     * False when a left click arriving now cannot be a press: a heavy attack just started, or it comes with a right click (in the
     * same or the next tick), or the right button is being held (its clicks repeat every 4 ticks, with arm swings among them).
     * A single right click does not hold the left one off for longer: a ranged / deployed weapon's heavy attack (the left click)
     * thrown right after a normal one (the right click) in the air has to count, as it does on an enemy.
     */
    public static boolean leftPossible(Player player) {
        int now = Bukkit.getCurrentTick();
        UUID id = player.getUniqueId();
        Integer locked = LEFT_LOCKED_UNTIL_TICK.get(id);
        if (locked != null && now < locked) return false;
        // the right button waits for its second button (right, left: 強2): a left click 2 ticks or more after it is that press (the arm swings
        // that follow a right click come in its tick or the next), also on a block
        if (HeavyInput.waiting(player)) {
            Integer right = LAST_RIGHT_TICK.get(id);
            return right == null || now - right >= 2;
        }
        // A right click on a block (a melee weapon's heavy attack, a held button) is followed by arm swings of the
        // client and the plugin (the off hand's) for a while; none of them is a press of the left button.
        Integer onBlock = LAST_RIGHT_BLOCK_TICK.get(id);
        if (onBlock != null && now - onBlock <= blockLockTicks()) return false;
        Integer right = LAST_RIGHT_TICK.get(id);
        if (right == null) return true;
        int since = now - right;
        if (since <= 1) return false;
        if (since > RIGHT_HELD_TICKS) return true;
        Integer before = PREV_RIGHT_TICK.get(id);
        return before == null || right - before > RIGHT_HELD_TICKS;
    }

    /** combat.right-click-block-left-lock-ticks: how long after a right click on a block no left click counts (default 10). */
    private static int blockLockTicks() {
        return plugin == null ? 10 : Math.max(0, plugin.getConfig().getInt("combat.right-click-block-left-lock-ticks", 10));
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
