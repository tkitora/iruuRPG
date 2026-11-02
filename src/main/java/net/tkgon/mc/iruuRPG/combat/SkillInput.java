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
 * The keys of the skills ({@link SkillSlots}; nekorozv's default of t164, after the redesign of 2026-11-02): the <b>first key</b> (F) and then,
 * within {@code combat.skill-slots.key-window-ticks}, the left button (小), the right button (中) or the first key again (大); the <b>second
 * key</b> (Q) alone is the weapon's skill, shift + it the ULT; the weapon's other button alone is util (the dodge: the right one with a melee
 * weapon, the left one with a ranged or deployed one); its normal button alone is the normal attack. The keys of 小・中・大 are the same with
 * every weapon (t160). A player may swap the two keys (t162, the keys' screen of the settings; kept in the player's data): Q is then the first
 * key and F the second. {@link #pressFirst} and {@link #pressSecond} are the roles; the listener gives each key its role.
 */
public final class SkillInput implements Listener {

    public enum Button { NORMAL, OTHER }

    /** What a key is: the normal attack, the weapon's skill, a skill (小・中 after the first key, util, the ULT), or nothing (the same click again, a held button). */
    public enum Kind { NORMAL_ATTACK, ITEM_SKILL, SKILL, NOTHING }

    public record Click(Kind kind, SkillSlots.Slot slot) {
        static final Click NORMAL_ATTACK = new Click(Kind.NORMAL_ATTACK, null);
        static final Click ITEM_SKILL = new Click(Kind.ITEM_SKILL, null);
        static final Click NOTHING = new Click(Kind.NOTHING, null);
    }

    /** A held button repeats its click every 4 ticks: the other button this soon after the last one is held, not pressed again. */
    static final int HELD_TICKS = 6;

    private static JavaPlugin plugin;
    /** The tick of the first key that waits for its second key. */
    private static final Map<UUID, Integer> WAITING = new ConcurrentHashMap<>();
    /** {tick, button} of the last click looked at (one click comes through several events in a tick). */
    private static final Map<UUID, int[]> LAST_CLICK = new ConcurrentHashMap<>();
    /** The tick of the last click of the other button. */
    private static final Map<UUID, Integer> LAST_OTHER = new ConcurrentHashMap<>();
    /** The players online who swapped the two keys (t162): Q is their first key and F their second. */
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
        WAITING.remove(player.getUniqueId());
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

    /** The first key is pressed: 大 when one waits (F → F), else null (this one waits for its second key). */
    public static SkillSlots.Slot pressFirst(Player player) {
        return pressFirst(player.getUniqueId(), Bukkit.getCurrentTick());
    }

    static SkillSlots.Slot pressFirst(UUID id, int now) {
        Integer first = WAITING.remove(id);
        if (first != null && now - first <= windowTicks()) return SkillSlots.Slot.LARGE;
        WAITING.put(id, now);
        return null;
    }

    /** The second key is pressed: the weapon's skill, or the ULT with shift. A first key that waits is let go. */
    public static Click pressSecond(Player player) {
        return pressSecond(player.getUniqueId(), player.isSneaking());
    }

    static Click pressSecond(UUID id, boolean sneaking) {
        WAITING.remove(id);
        return sneaking ? new Click(Kind.SKILL, SkillSlots.Slot.ULT) : Click.ITEM_SKILL;
    }

    /** A click of the weapon's {@code button}, the {@code right} button or the left one. */
    public static Click click(Player player, Button button, boolean right) {
        return click(player.getUniqueId(), button, right, Bukkit.getCurrentTick());
    }

    static Click click(UUID id, Button button, boolean right, int now) {
        int[] last = LAST_CLICK.put(id, new int[]{now, button.ordinal()});
        if (last != null && last[0] == now && last[1] == button.ordinal()) return Click.NOTHING;
        Integer other = button == Button.OTHER ? LAST_OTHER.put(id, now) : null;
        Integer first = WAITING.remove(id);
        if (first != null && now - first <= windowTicks()) {
            // (the same with every weapon: first key → left 小, → right 中)
            return new Click(Kind.SKILL, right ? SkillSlots.Slot.MEDIUM : SkillSlots.Slot.SMALL);
        }
        if (button == Button.NORMAL) return Click.NORMAL_ATTACK;
        // the other button is util (the dodge, t164); a held one is one press
        return other != null && now - other <= HELD_TICKS ? Click.NOTHING : new Click(Kind.SKILL, SkillSlots.Slot.UTIL);
    }

    /** True while a first key waits for its second key. */
    public static boolean waiting(Player player) {
        Integer first = WAITING.get(player.getUniqueId());
        return first != null && Bukkit.getCurrentTick() - first <= windowTicks();
    }

    /** Lets a first key that waits go (it was used for something else). */
    public static void forgetFirst(Player player) {
        WAITING.remove(player.getUniqueId());
    }

    /** The first key: F, or Q when swapped. */
    public static String firstKey(boolean swapped) {
        return swapped ? "Q" : "F";
    }

    /** The second key: Q, or F when swapped. */
    public static String secondKey(boolean swapped) {
        return swapped ? "F" : "Q";
    }

    /** The keys of a skill with a melee weapon as they are by default: "F→左". */
    public static String keys(SkillSlots.Slot slot) {
        return keys(slot, false, true);
    }

    /**
     * The keys of a skill, the keys swapped or not (t162), with a melee weapon or not: "F→左" ...; util is the weapon's other button ("右クリック"
     * with a melee weapon, "左クリック" with another).
     */
    public static String keys(SkillSlots.Slot slot, boolean swapped, boolean melee) {
        String first = firstKey(swapped);
        return switch (slot) {
            case SMALL -> first + "→左";
            case MEDIUM -> first + "→右";
            case LARGE -> first + "→" + first;
            case UTIL -> melee ? "右クリック" : "左クリック";
            case ULT -> "Shift+" + secondKey(swapped);
        };
    }

    /** The keys of a skill for any weapon: util as "右クリック (近接) / 左クリック (遠距離)". */
    public static String keys(SkillSlots.Slot slot, boolean swapped) {
        return slot == SkillSlots.Slot.UTIL ? "右クリック (近接) / 左クリック (遠距離)" : keys(slot, swapped, true);
    }

    /** The key that takes a deployed weapon's placement away: shift and the first key. */
    public static String removeKeys(boolean swapped) {
        return "Shift+" + firstKey(swapped);
    }

    /** The key of the weapon's skill: the second key alone. */
    public static String itemSkillKeys(boolean swapped) {
        return secondKey(swapped);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    public static void forget(UUID id) {
        SWAPPED.remove(id);
        WAITING.remove(id);
        LAST_CLICK.remove(id);
        LAST_OTHER.remove(id);
    }
}
