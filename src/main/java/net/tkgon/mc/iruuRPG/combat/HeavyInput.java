package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Which heavy attack (強1 - 強5, index 0 - 4) the buttons choose. The weapon's normal button is the left one with a melee weapon and the right one
 * with a ranged or deployed one; the other button is the other one:
 * <ul>
 *   <li>other, other: 強1; other, normal: 強2 (the second within {@code combat.heavy-input.window-ticks} of the first; the first alone does nothing);</li>
 *   <li>shift + other: 強3; shift + normal: 強4;</li>
 *   <li>shift pressed twice within the window: 強5 (a press of shift that was used for a shift + click does not count).</li>
 * </ul>
 * A held button repeats its click every 4 ticks, so holding the other button throws 強1 again and again (as fast as its cooldown allows).
 */
public final class HeavyInput implements Listener {

    public enum Button { NORMAL, OTHER }

    /** What a click is: the weapon's normal attack. */
    public static final int NORMAL_ATTACK = -1;
    /** What a click is: nothing (the first of two buttons, or the same click arriving through another event of its tick). */
    public static final int NOTHING = -2;

    private static JavaPlugin plugin;
    /** The tick of the other button waiting for its second button. */
    private static final Map<UUID, Integer> WAITING = new ConcurrentHashMap<>();
    /** {tick, button} of the last click looked at (one click comes through several events in a tick). */
    private static final Map<UUID, int[]> LAST_CLICK = new ConcurrentHashMap<>();
    /** The tick shift was last pressed, and whether that press was used for a shift + click. */
    private static final Map<UUID, Integer> SNEAK_DOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SNEAK_USED = new ConcurrentHashMap<>();

    /** Throws heavy attack {@code index} of the weapon in hand for the double press of shift; false when the weapon has none. */
    private final BiPredicate<Player, Integer> doubleShift;

    public HeavyInput(JavaPlugin owner, BiPredicate<Player, Integer> doubleShift) {
        plugin = owner;
        this.doubleShift = doubleShift;
    }

    static int windowTicks() {
        return plugin == null ? 8 : Math.max(2, Math.min(40, plugin.getConfig().getInt("combat.heavy-input.window-ticks", 8)));
    }

    /**
     * A click of {@code button} with a weapon that has heavy attacks: {@link #NORMAL_ATTACK}, {@link #NOTHING}, or the index (0 - 4) of the heavy
     * attack it throws.
     */
    public static int click(Player player, Button button) {
        return click(player.getUniqueId(), player.isSneaking(), button, Bukkit.getCurrentTick());
    }

    static int click(UUID id, boolean sneaking, Button button, int now) {
        int[] last = LAST_CLICK.put(id, new int[]{now, button.ordinal()});
        if (last != null && last[0] == now && last[1] == button.ordinal()) return NOTHING;
        if (sneaking) {
            SNEAK_USED.put(id, true);
            WAITING.remove(id);
            return button == Button.OTHER ? 2 : 3;
        }
        Integer first = WAITING.remove(id);
        boolean open = first != null && now - first <= windowTicks();
        if (button == Button.OTHER) {
            if (open) return 0;
            WAITING.put(id, now);
            return NOTHING;
        }
        return open ? 1 : NORMAL_ATTACK;
    }

    /** True while a press of the other button waits for its second button. */
    public static boolean waiting(Player player) {
        Integer first = WAITING.get(player.getUniqueId());
        return first != null && Bukkit.getCurrentTick() - first <= windowTicks();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        if (plugin != null && !plugin.getConfig().getBoolean("combat.heavy-input.double-shift", true)) return;
        Player player = event.getPlayer();
        if (!sneakDown(player.getUniqueId(), Bukkit.getCurrentTick())) return;
        // (a third press right after is the first of a new pair)
        if (doubleShift.test(player, 4)) SNEAK_DOWN.remove(player.getUniqueId());
    }

    /** Shift is pressed: true when it is the second press of a pair (the first within the window, and not used for a shift + click). */
    static boolean sneakDown(UUID id, int now) {
        Integer before = SNEAK_DOWN.put(id, now);
        boolean used = Boolean.TRUE.equals(SNEAK_USED.put(id, false));
        if (before == null || used || now - before > windowTicks()) return false;
        WAITING.remove(id);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    public static void forget(UUID id) {
        WAITING.remove(id);
        LAST_CLICK.remove(id);
        SNEAK_DOWN.remove(id);
        SNEAK_USED.remove(id);
    }
}
