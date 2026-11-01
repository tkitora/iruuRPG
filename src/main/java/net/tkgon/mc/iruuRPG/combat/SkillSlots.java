package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * The five skills of a class (the user's redesign of 2026-11-02, which replaces the skills 1 - 6, the special and the 8 patterns of two keys of
 * t138): 小 (Q → the weapon's normal button), 中 (Q → its other button), 大 (Q → Q), util (F) and ULT (shift + F); the keys are read by
 * {@link SkillInput}. Each skill cools down on its own, for {@code combat.skill-slots.cooldown-seconds} (the class's own, else the
 * {@code default}; times the class skills' cooldown multiplier of the player), and apart for each kind of weapon (melee, ranged, deployed: the
 * user's wish of 2026-11-02, so 小 of the sword and 小 of the bow cool down each on their own).
 *
 * <p>A class's move of a skill is registered ({@link #register}). A skill whose move is not made yet does what it did before ({@link Fallback}:
 * 小・中・大 the weapon's skills 1 - 3 with their own cooldowns, ULT the class's special with the ULT's cooldown, util nothing).
 */
public final class SkillSlots implements Listener {

    public enum Slot {
        SMALL("小", "small", 7), MEDIUM("中", "medium", 12), LARGE("大", "large", 25), UTIL("util", "util", 5), ULT("ULT", "ult", 100);

        private final String label;
        private final String key;
        private final int seconds;

        Slot(String label, String key, int seconds) {
            this.label = label;
            this.key = key;
            this.seconds = seconds;
        }

        public String label() {
            return label;
        }

        /** Its name in the config. */
        public String key() {
            return key;
        }

        /** The skill of the weapon (0 - 2) it throws until the class's move is made (小・中・大), -1 for the others. */
        public int heavyIndex() {
            return ordinal() <= LARGE.ordinal() ? ordinal() : -1;
        }
    }

    /** A class's move of a skill: true when it was done (its cooldown starts), false when it was not (nothing cools down). */
    public interface Move {
        boolean use(Player player, RpgItemDefinition weapon);
    }

    /** What a skill with no move of the class does: what it did before t150. */
    public interface Fallback {
        /** False when there is nothing (the player is told so). */
        boolean use(Player player, Slot slot, RpgItemDefinition weapon);

        /** {milliseconds left, the whole cooldown} ({0, 0}: ready), or null when there is nothing (the icon is locked). */
        long[] cooldown(Player player, Slot slot, RpgItemDefinition weapon);
    }

    private static final Fallback NONE = new Fallback() {
        @Override
        public boolean use(Player player, Slot slot, RpgItemDefinition weapon) {
            return false;
        }

        @Override
        public long[] cooldown(Player player, Slot slot, RpgItemDefinition weapon) {
            return null;
        }
    };

    /**
     * The cooldowns of the design (seconds): 小 7 / 中 12 / 大 25 / util 5 / ULT 100, and the classes' own. The config's come first. (Class ids:
     * レンジャー = gifted, クレリック = healer.)
     */
    private static final Map<String, Map<Slot, Integer>> DESIGN = Map.of(
            "warrior", Map.of(Slot.MEDIUM, 10, Slot.LARGE, 30),
            "healer", Map.of(Slot.UTIL, 15),
            "gifted", Map.of(Slot.SMALL, 3, Slot.MEDIUM, 7, Slot.LARGE, 20),
            "assassin", Map.of(Slot.LARGE, 20));

    private final JavaPlugin plugin;
    private final Function<Player, String> classOf;
    private final ToDoubleFunction<Player> cooldownMultiplier;
    private final Map<String, Map<Slot, Move>> moves = new HashMap<>();
    /** {when it is ready, the whole cooldown} of each skill of each kind of weapon, per player. */
    private final Map<UUID, Map<Kept, long[]>> cooldowns = new ConcurrentHashMap<>();

    /** What a cooldown is kept for: a skill with a kind of weapon. */
    record Kept(AttackType kind, Slot slot) {
    }

    /** The kind of weapon a cooldown is kept for: melee, ranged or deployed (anything else as melee). */
    static AttackType kindOf(RpgItemDefinition weapon) {
        AttackType type = weapon == null ? null : weapon.attackType();
        return type == AttackType.RANGE || type == AttackType.DEPLOY ? type : AttackType.MELEE;
    }
    private Fallback fallback = NONE;

    /** {@code classOf}: the class of a player (its id); {@code cooldownMultiplier}: what the player's cooldowns are multiplied by. */
    public SkillSlots(JavaPlugin plugin, Function<Player, String> classOf, ToDoubleFunction<Player> cooldownMultiplier) {
        this.plugin = plugin;
        this.classOf = classOf;
        this.cooldownMultiplier = cooldownMultiplier;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback == null ? NONE : fallback;
    }

    /** The move of {@code slot} of class {@code classId}. */
    public void register(String classId, Slot slot, Move move) {
        moves.computeIfAbsent(classId.toLowerCase(Locale.ROOT), ignored -> new EnumMap<>(Slot.class)).put(slot, move);
    }

    public boolean has(String classId, Slot slot) {
        return move(classId, slot) != null;
    }

    private Move move(String classId, Slot slot) {
        Map<Slot, Move> own = classId == null ? null : moves.get(classId.toLowerCase(Locale.ROOT));
        return own == null ? null : own.get(slot);
    }

    /** The keys of {@code slot} were pressed with {@code weapon} in hand. */
    public void use(Player player, Slot slot, RpgItemDefinition weapon) {
        Move move = move(classOf.apply(player), slot);
        if (move == null) {
            if (!fallback.use(player, slot, weapon)) player.sendMessage("[iruuRPG] " + slot.label() + " のスキルは、まだありません。");
            return;
        }
        Kept kept = new Kept(kindOf(weapon), slot);
        if (left(player.getUniqueId(), kept, System.currentTimeMillis()) > 0L) {
            coolingClick(player);
            return;
        }
        if (move.use(player, weapon)) start(player.getUniqueId(), kept, Math.round(cooldownMillis(player, slot)), System.currentTimeMillis());
    }

    /** {milliseconds left, the whole cooldown} of {@code slot} with the kind of {@code weapon} ({0, 0}: ready), or null when the player has nothing on it. */
    public long[] cooldown(Player player, Slot slot, RpgItemDefinition weapon) {
        if (move(classOf.apply(player), slot) == null) return fallback.cooldown(player, slot, weapon);
        return cooldown(player.getUniqueId(), new Kept(kindOf(weapon), slot), System.currentTimeMillis());
    }

    long[] cooldown(UUID id, Kept kept, long now) {
        long left = left(id, kept, now);
        if (left <= 0L) return new long[]{0L, 0L};
        long[] state = cooldowns.get(id).get(kept);
        return new long[]{left, Math.max(left, state[1])};
    }

    private long left(UUID id, Kept kept, long now) {
        Map<Kept, long[]> own = cooldowns.get(id);
        long[] state = own == null ? null : own.get(kept);
        return state == null ? 0L : Math.max(0L, state[0] - now);
    }

    void start(UUID id, Kept kept, long millis, long now) {
        cooldowns.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>()).put(kept, new long[]{now + millis, millis});
    }

    private double cooldownMillis(Player player, Slot slot) {
        double multiplier = Math.max(0.0, cooldownMultiplier.applyAsDouble(player));
        return cooldownSeconds(plugin.getConfig().getConfigurationSection("combat.skill-slots.cooldown-seconds"), classOf.apply(player), slot) * 1000.0 * multiplier;
    }

    /** The cooldown of {@code slot} of {@code classId} in seconds: the config's (the class's, else its {@code default}), else the design's. */
    public static double cooldownSeconds(ConfigurationSection config, String classId, Slot slot) {
        String id = classId == null ? "" : classId.toLowerCase(Locale.ROOT);
        if (config != null) {
            if (!id.isEmpty() && config.get(id + "." + slot.key()) instanceof Number number) return Math.max(0.0, Math.min(3600.0, number.doubleValue()));
            if (config.get("default." + slot.key()) instanceof Number number) return Math.max(0.0, Math.min(3600.0, number.doubleValue()));
        }
        Map<Slot, Integer> own = DESIGN.get(id);
        return own != null && own.containsKey(slot) ? own.get(slot) : slot.seconds;
    }

    private final Map<UUID, Integer> lastCoolingClick = new HashMap<>();

    /** A skill still cooling down: a dull click (not again and again for a held key), nothing done. */
    private void coolingClick(Player player) {
        int tick = org.bukkit.Bukkit.getCurrentTick();
        Integer last = lastCoolingClick.put(player.getUniqueId(), tick);
        if (last != null && tick - last <= 10) return;
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.4f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastCoolingClick.remove(event.getPlayer().getUniqueId());
    }
}
