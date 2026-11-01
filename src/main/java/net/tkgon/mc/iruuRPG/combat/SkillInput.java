package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The keys of the skills ({@link SkillSlots}, the user's redesign of 2026-11-02; it replaces the styles of t116 / t119 / t138): Q and then,
 * within {@code combat.skill-slots.key-window-ticks}, the weapon's normal button (小), its other button (中) or Q again (大); F alone is util,
 * shift + F the ULT. With no Q before it, the normal button is the normal attack and the other button the weapon's skill. The normal button is
 * the left one with a melee weapon and the right one with a ranged or deployed one, so with a melee weapon 小 is Q → left, 中 Q → right and the
 * weapon's skill right; with a ranged one 小 Q → right, 中 Q → left and the weapon's skill left. The listener reads the keys and asks this what
 * they are.
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

    public SkillInput(JavaPlugin owner) {
        plugin = owner;
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

    /** A click of the weapon's {@code button}. */
    public static Click click(Player player, Button button) {
        return click(player.getUniqueId(), button, Bukkit.getCurrentTick());
    }

    static Click click(UUID id, Button button, int now) {
        int[] last = LAST_CLICK.put(id, new int[]{now, button.ordinal()});
        if (last != null && last[0] == now && last[1] == button.ordinal()) return Click.NOTHING;
        Integer other = button == Button.OTHER ? LAST_OTHER.put(id, now) : null;
        Integer first = Q_WAITING.remove(id);
        if (first != null && now - first <= windowTicks()) {
            return new Click(Kind.SKILL, button == Button.NORMAL ? SkillSlots.Slot.SMALL : SkillSlots.Slot.MEDIUM);
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

    /** The keys of a skill as they are pressed with a weapon ({@code melee}: its normal button is the left one): "Q→左". */
    public static String keys(SkillSlots.Slot slot, boolean melee) {
        return switch (slot) {
            case SMALL -> "Q→" + (melee ? "左" : "右");
            case MEDIUM -> "Q→" + (melee ? "右" : "左");
            case LARGE -> "Q→Q";
            case UTIL -> "F";
            case ULT -> "Shift+F";
        };
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
        Q_WAITING.remove(id);
        LAST_CLICK.remove(id);
        LAST_OTHER.remove(id);
    }
}
