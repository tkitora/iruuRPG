package net.tkgon.mc.iruuRPG.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One right click on an entity reaches the server as an entity-interact event and, when the entity does not take the
 * click, as a normal use-item event right after (same tick or the next). Whichever handles the click first marks it
 * here, so the second event of the same click does not fire the weapon or the class skill a second time.
 * A new click is at least 4 ticks later (the client's right-click delay), so a 2-tick window never swallows one.
 */
public final class RightClickGuard {

    private static final int WINDOW_TICKS = 2;

    private final Map<UUID, Integer> weaponTicks = new HashMap<>();
    private final Map<UUID, Integer> classTicks = new HashMap<>();

    /** The weapon's right-click (ranged attack, deploy, manual skill) was just used for this click. */
    public void markWeapon(Player player) {
        weaponTicks.put(player.getUniqueId(), Bukkit.getCurrentTick());
    }

    public boolean weaponRecent(Player player) {
        return recent(weaponTicks, player);
    }

    /** A class skill on the right-click key (R / sneak+R) was just used for this click. */
    public void markClass(Player player) {
        classTicks.put(player.getUniqueId(), Bukkit.getCurrentTick());
    }

    public boolean classRecent(Player player) {
        return recent(classTicks, player);
    }

    public void forget(Player player) {
        weaponTicks.remove(player.getUniqueId());
        classTicks.remove(player.getUniqueId());
    }

    private static boolean recent(Map<UUID, Integer> marks, Player player) {
        Integer tick = marks.get(player.getUniqueId());
        return tick != null && Bukkit.getCurrentTick() - tick <= WINDOW_TICKS;
    }
}
