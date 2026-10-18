package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AttackCooldowns {

    private final JavaPlugin plugin;
    private final Map<UUID, EnumMap<AttackType, Long>> lockedUntilMillis = new HashMap<>();
    /** How long the current / last lock of each type lasts in all (for a progress gauge). */
    private final Map<UUID, EnumMap<AttackType, Long>> totalMillis = new HashMap<>();

    public AttackCooldowns(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean tryStart(Player player, AttackType attackType) {
        return tryStart(player, attackType, cooldownTicks(attackType));
    }

    public boolean tryStart(Player player, AttackType attackType, int cooldownTicks) {
        long now = System.currentTimeMillis();
        EnumMap<AttackType, Long> playerLocks = lockedUntilMillis.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(AttackType.class));
        long lockedUntil = playerLocks.getOrDefault(attackType, 0L);
        if (lockedUntil > now) {
            return false;
        }

        long cooldownMillis = (long) Math.max(0, cooldownTicks) * 50L;
        playerLocks.put(attackType, now + cooldownMillis);
        totalMillis.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(AttackType.class)).put(attackType, cooldownMillis);
        return true;
    }

    /** Makes the current lock of {@code attackType} last {@code extraTicks} longer (a heavy attack's recovery). */
    public void extend(Player player, AttackType attackType, int extraTicks) {
        if (extraTicks <= 0) return;
        EnumMap<AttackType, Long> playerLocks = lockedUntilMillis.get(player.getUniqueId());
        if (playerLocks == null || !playerLocks.containsKey(attackType)) return;
        playerLocks.put(attackType, playerLocks.get(attackType) + extraTicks * 50L);
        EnumMap<AttackType, Long> totals = totalMillis.get(player.getUniqueId());
        if (totals != null && totals.containsKey(attackType)) totals.put(attackType, totals.get(attackType) + extraTicks * 50L);
    }

    /** How long the lock of {@code attackType} lasts in all (0 when none was ever set). */
    public long totalMillis(Player player, AttackType attackType) {
        EnumMap<AttackType, Long> totals = totalMillis.get(player.getUniqueId());
        return totals == null ? 0L : totals.getOrDefault(attackType, 0L);
    }

    public long remainingMillis(Player player, AttackType attackType) {
        EnumMap<AttackType, Long> playerLocks = lockedUntilMillis.get(player.getUniqueId());
        if (playerLocks == null) return 0L;

        return Math.max(0L, playerLocks.getOrDefault(attackType, 0L) - System.currentTimeMillis());
    }

    public int cooldownTicks(AttackType attackType) {
        long ticks = switch (attackType) {
            case MELEE -> plugin.getConfig().getLong("combat.cooldown-ticks.melee", 14L);
            case RANGE -> plugin.getConfig().getLong("combat.cooldown-ticks.range", 10L);
            case DEPLOY -> plugin.getConfig().getLong("combat.cooldown-ticks.deploy", 200L);
            case SPECIAL -> plugin.getConfig().getLong("combat.cooldown-ticks.special", 2L);
        };
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, ticks));
    }
}
