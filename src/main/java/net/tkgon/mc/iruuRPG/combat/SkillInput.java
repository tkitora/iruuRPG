package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The keys of the skills ({@link SkillSlots}, the user's redesign of 2026-11-02; it replaces the styles of t116 / t119 / t138): Q and then,
 * within {@code combat.skill-slots.key-window-ticks}, the left button (小), the right button (中) or Q again (大); F alone is util, shift + F
 * the ULT. These are the same with every weapon (t160: the skills of a ranged weapon were Q → right / Q → left before, and that was hard to
 * follow). With no Q before it, the weapon's normal button is the normal attack and its other button the weapon's skill: the normal button is
 * the left one with a melee weapon and the right one with a ranged or deployed one. The listener reads the keys and asks this what they are.
 *
 * <p>A player may swap Q and F (t162, the keys' screen of the settings; kept in the player's data): F then waits for the second key (F → left
 * 小, F → right 中, F → F 大, shift + F takes a placement away) and Q is util (shift + Q the ULT). {@link #pressQ} and {@link #pressF} are the
 * roles; the listener gives the key's role to them.
 */
public final class SkillInput implements Listener {

    public enum Button { NORMAL, OTHER }

    /** What a click is: the normal attack, the weapon's skill, a skill (a Q waited for it), or nothing (the same click again, a held button). */
    public enum Kind { NORMAL_ATTACK, ITEM_SKILL, SKILL, NOTHING }

    public record Click(Kind kind, SkillSlots.Slot slot) {
        static final Click NORMAL_ATTACK = new Click(Kind.NORMAL_ATTACK, null);
        static final Click ITEM_SKILL = new Click(Kind.ITEM_SKILL, null);
        static final Click NOTHING = new Click(Kind.NOTHING, null);
    }

    /** A held button repeats its click every 4 ticks: the other button this soon after the last one is held, not pressed again. */
    static final int HELD_TICKS = 6;

    private static JavaPlugin plugin;
    /** The tick of the Q that waits for its second key. */
    private static final Map<UUID, Integer> Q_WAITING = new ConcurrentHashMap<>();
    /** {tick, button} of the last click looked at (one click comes through several events in a tick). */
    private static final Map<UUID, int[]> LAST_CLICK = new ConcurrentHashMap<>();
    /** The tick of the last click of the other button. */
    private static final Map<UUID, Integer> LAST_OTHER = new ConcurrentHashMap<>();
    /** The players online who swapped Q and F (t162). */
    private static final java.util.Set<UUID> SWAPPED = ConcurrentHashMap.newKeySet();
    private static final String SWAP_KEY = "keys_swap_q_f";

    public SkillInput(JavaPlugin owner) {
        plugin = owner;
        // (the players online when the plugin is loaded again)
        if (owner.getServer() != null) for (Player player : owner.getServer().getOnlinePlayers()) loadSwap(player);
    }

    /** Whether the player swapped Q and F (t162): F is the key of 小・中・大 and Q of util / the ULT. */
    public static boolean swapped(Player player) {
        return player != null && SWAPPED.contains(player.getUniqueId());
    }

    public static boolean swapped(UUID player) {
        return player != null && SWAPPED.contains(player);
    }

    /** Swaps Q and F for the player, or back (kept in the player's data). */
    public static void setSwapped(Player player, boolean swap) {
        if (swap) SWAPPED.add(player.getUniqueId()); else SWAPPED.remove(player.getUniqueId());
        Q_WAITING.remove(player.getUniqueId());
        if (plugin == null) return;
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, SWAP_KEY);
        if (swap) player.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        else player.getPersistentDataContainer().remove(key);
    }

    private static void loadSwap(Player player) {
        if (plugin == null) return;
        if (player.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, SWAP_KEY), org.bukkit.persistence.PersistentDataType.BYTE)) SWAPPED.add(player.getUniqueId());
        else SWAPPED.remove(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadSwap(event.getPlayer());
    }

    static int windowTicks() {
        return plugin == null ? 20 : Math.max(4, Math.min(60, plugin.getConfig().getInt("combat.skill-slots.key-window-ticks", 20)));
    }

    /** Q is pressed: 大 when a Q waits (Q → Q), else null (this one waits for its second key). */
    public static SkillSlots.Slot pressQ(Player player) {
        return pressQ(player.getUniqueId(), Bukkit.getCurrentTick());
    }

    static SkillSlots.Slot pressQ(UUID id, int now) {
        Integer first = Q_WAITING.remove(id);
        if (first != null && now - first <= windowTicks()) return SkillSlots.Slot.LARGE;
        Q_WAITING.put(id, now);
        return null;
    }

    /** F is pressed: util, or the ULT with shift. A Q that waits is let go. */
    public static SkillSlots.Slot pressF(Player player) {
        return pressF(player.getUniqueId(), player.isSneaking());
    }

    static SkillSlots.Slot pressF(UUID id, boolean sneaking) {
        Q_WAITING.remove(id);
        return sneaking ? SkillSlots.Slot.ULT : SkillSlots.Slot.UTIL;
    }

    /** A click of the weapon's {@code button}, the {@code right} button or the left one. */
    public static Click click(Player player, Button button, boolean right) {
        return click(player.getUniqueId(), button, right, Bukkit.getCurrentTick());
    }

    static Click click(UUID id, Button button, boolean right, int now) {
        int[] last = LAST_CLICK.put(id, new int[]{now, button.ordinal()});
        if (last != null && last[0] == now && last[1] == button.ordinal()) return Click.NOTHING;
        Integer other = button == Button.OTHER ? LAST_OTHER.put(id, now) : null;
        Integer first = Q_WAITING.remove(id);
        if (first != null && now - first <= windowTicks()) {
            // (the same with every weapon: Q → left 小, Q → right 中)
            return new Click(Kind.SKILL, right ? SkillSlots.Slot.MEDIUM : SkillSlots.Slot.SMALL);
        }
        if (button == Button.NORMAL) return Click.NORMAL_ATTACK;
        // (a held other button is one press of the weapon's skill)
        return other != null && now - other <= HELD_TICKS ? Click.NOTHING : Click.ITEM_SKILL;
    }

    /** True while a Q waits for its second key. */
    public static boolean qWaiting(Player player) {
        Integer first = Q_WAITING.get(player.getUniqueId());
        return first != null && Bukkit.getCurrentTick() - first <= windowTicks();
    }

    /** Lets a Q that waits go (it was used for something else). */
    public static void forgetQ(Player player) {
        Q_WAITING.remove(player.getUniqueId());
    }

    /** The keys of a skill (the same with every weapon, t160): "Q→左". */
    public static String keys(SkillSlots.Slot slot) {
        return keys(slot, false);
    }

    /** The keys of a skill, with Q and F swapped or not (t162): "Q→左" or "F→左". */
    public static String keys(SkillSlots.Slot slot, boolean swapped) {
        String first = swapped ? "F" : "Q";
        String other = swapped ? "Q" : "F";
        return switch (slot) {
            case SMALL -> first + "→左";
            case MEDIUM -> first + "→右";
            case LARGE -> first + "→" + first;
            case UTIL -> other;
            case ULT -> "Shift+" + other;
        };
    }

    /** The key that takes a deployed weapon's placement away: shift and the key of 小・中・大 (t162). */
    public static String removeKeys(boolean swapped) {
        return "Shift+" + (swapped ? "F" : "Q");
    }

    /** The button of the weapon's skill: the right one with a melee weapon, the left one with another. */
    public static String itemSkillKeys(boolean melee) {
        return melee ? "右クリック" : "左クリック";
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    public static void forget(UUID id) {
        SWAPPED.remove(id);
        Q_WAITING.remove(id);
        LAST_CLICK.remove(id);
        LAST_OTHER.remove(id);
    }
}
