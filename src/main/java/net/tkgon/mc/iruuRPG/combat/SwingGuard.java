package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells a real click from the echo of an arm swing the plugin made itself. The client answers a swing animation sent
 * by the server (for its own player) with an arm-swing packet of its own, and the server turns every arm-swing packet
 * into a left click in the air - so a swing the plugin plays for a skill or for the off-hand would look like the player
 * had pressed the left button, and start the next attack. Swings of the plugin go through {@link #swingMain} /
 * {@link #swingOff}, and a left click that arrives right after one is not a click ({@link #isEcho}).
 */
public final class SwingGuard {

    private static final Map<UUID, Long> LAST_OWN_SWING = new ConcurrentHashMap<>();

    private SwingGuard() {
    }

    public static void swingMain(Player player) {
        mark(player);
        player.swingMainHand();
    }

    public static void swingOff(Player player) {
        mark(player);
        player.swingOffHand();
    }

    private static void mark(Player player) {
        LAST_OWN_SWING.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** True when a left click of {@code player} arriving now is (most likely) the answer to a swing the plugin sent. */
    public static boolean isEcho(Player player) {
        Long sent = LAST_OWN_SWING.get(player.getUniqueId());
        if (sent == null) return false;
        long window = Math.max(150L, player.getPing() + 120L);
        return System.currentTimeMillis() - sent <= window;
    }
}
