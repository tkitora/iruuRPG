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
 *
 * <p>Re-activation and charges (t157, nekorozv's design; {@link Mechanics}): a move may have <b>charges</b> (stacks: it is used while one is left;
 * using one starts the refill of the next, one at a time, each taking the skill's cooldown) and a <b>re-activation</b> (after it is used, the same
 * keys within {@code recastSeconds} do its second part, {@link Move#recast}). The same keys go by these states, in this order:
 * <ol>
 *   <li>a re-activation is open: the second part, no charge used (whether charges are left or not), and the re-activation closes;</li>
 *   <li>a charge is left: one is used, the move is done, and its re-activation opens (when it has one);</li>
 *   <li>no charge left: nothing (a dull click), the refill goes on.</li>
 * </ol>
 * A move with one charge and no re-activation is a skill with a cooldown, as before. The icons show the open re-activation (a colored rim), the
 * charges left and the refill of the next one ({@link #view}). {@code /iruurpg skilltest} gives a player's skill charges and a re-activation to try
 * them (until the player leaves; its second part is the move again).
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

        /** The second part of the move (its re-activation, {@link Mechanics#recastSeconds}); true when something was done. */
        default boolean recast(Player player, RpgItemDefinition weapon) {
            return false;
        }
    }

    /**
     * How a move is used (t157): {@code charges} (1 = a skill with a cooldown; more = it keeps that many and each refills one at a time),
     * {@code recastSeconds} the time its re-activation stays open after it is used (0 = none), and {@code chargeSeconds} how long one charge takes
     * to refill (0 = the skill's cooldown, {@code combat.skill-slots.cooldown-seconds}).
     */
    public record Mechanics(int charges, double recastSeconds, double chargeSeconds) {
        public static final Mechanics NONE = new Mechanics(1, 0.0, 0.0);

        public Mechanics {
            charges = Math.max(1, Math.min(9, charges));
            recastSeconds = Math.max(0.0, recastSeconds);
            chargeSeconds = Math.max(0.0, chargeSeconds);
        }

        public boolean recasts() {
            return recastSeconds > 0.0;
        }
    }

    /**
     * What an icon shows of a skill: {@code cooldown} {milliseconds left, the whole} ({0, 0} ready; null: nothing on it, the icon is locked),
     * {@code charges} left of {@code maxCharges} (a skill of one charge shows its cooldown, not its charges), {@code refill} how far the next charge
     * is (0 - 1; -1 when none refills) and {@code recasting} (its re-activation is open).
     */
    public record View(long[] cooldown, int charges, int maxCharges, double refill, boolean recasting) {
        static View of(long[] cooldown) {
            return new View(cooldown, 1, 1, -1.0, false);
        }

        public boolean charged() {
            return maxCharges > 1;
        }
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
    private final Map<String, Map<Slot, Mechanics>> mechanics = new HashMap<>();
    /** The charges and re-activation of each skill of each kind of weapon, per player. */
    private final Map<UUID, Map<Kept, State>> states = new ConcurrentHashMap<>();
    /** /iruurpg skilltest: the mechanics a player tries on their skills (until they leave). */
    private final Map<UUID, Map<Slot, Mechanics>> trials = new ConcurrentHashMap<>();

    /**
     * The charges and the re-activation of a skill: {@code charges} left (counted again from the refill when read: {@link #refresh}), the refill
     * of the next one ({@code refillStart} + {@code refillMillis}, running while a charge is missing) and the end of the open re-activation.
     */
    static final class State {
        int charges;
        long refillStart;
        long refillMillis;
        long recastUntil;

        State(int charges) {
            this.charges = charges;
        }
    }

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

    /** The move of {@code slot} of class {@code classId} (one charge, no re-activation). */
    public void register(String classId, Slot slot, Move move) {
        register(classId, slot, move, Mechanics.NONE);
    }

    /** The move of {@code slot} of class {@code classId} with its charges and re-activation (t157). */
    public void register(String classId, Slot slot, Move move, Mechanics mechanics) {
        moves.computeIfAbsent(classId.toLowerCase(Locale.ROOT), ignored -> new EnumMap<>(Slot.class)).put(slot, move);
        this.mechanics.computeIfAbsent(classId.toLowerCase(Locale.ROOT), ignored -> new EnumMap<>(Slot.class)).put(slot, mechanics == null ? Mechanics.NONE : mechanics);
    }

    /** /iruurpg skilltest: the player's {@code slot} tries {@code trial} (null: back to the move's own), until the player leaves. */
    public void trial(UUID player, Slot slot, Mechanics trial) {
        if (trial == null) {
            Map<Slot, Mechanics> own = trials.get(player);
            if (own != null) own.remove(slot);
        } else {
            trials.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>()).put(slot, trial);
        }
        // (the charges are counted anew with the new mechanics)
        Map<Kept, State> own = states.get(player);
        if (own != null) own.keySet().removeIf(kept -> kept.slot() == slot);
    }

    /** /iruurpg skilltest off: every skill of the player back to its move's own. */
    public void endTrials(UUID player) {
        Map<Slot, Mechanics> own = trials.remove(player);
        if (own != null) for (Slot slot : own.keySet()) trial(player, slot, null);
    }

    /** The mechanics of the player's {@code slot}: the one they try, else the move's. */
    Mechanics mechanics(UUID player, String classId, Slot slot) {
        Map<Slot, Mechanics> trying = trials.get(player);
        if (trying != null && trying.containsKey(slot)) return trying.get(slot);
        Map<Slot, Mechanics> own = classId == null ? null : mechanics.get(classId.toLowerCase(Locale.ROOT));
        Mechanics found = own == null ? null : own.get(slot);
        return found == null ? Mechanics.NONE : found;
    }

    private boolean trying(UUID player, Slot slot) {
        Map<Slot, Mechanics> trying = trials.get(player);
        return trying != null && trying.containsKey(slot);
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
        use(player, slot, weapon, System.currentTimeMillis());
    }

    /** What the keys did: the second part, the move (a charge used), nothing (no charge left), or the fallback's (no move of the class). */
    enum Done { RECAST, USED, NOTHING, FALLBACK }

    Done use(Player player, Slot slot, RpgItemDefinition weapon, long now) {
        String classId = classOf.apply(player);
        Move move = move(classId, slot);
        if (move == null) {
            if (!fallback.use(player, slot, weapon)) player.sendMessage("[iruuRPG] " + slot.label() + " のスキルは、まだありません。");
            return Done.FALLBACK;
        }
        UUID id = player.getUniqueId();
        Mechanics mechanics = mechanics(id, classId, slot);
        State state = state(id, new Kept(kindOf(weapon), slot), mechanics, now);
        // 1. an open re-activation comes first: its second part, no charge used
        if (mechanics.recasts() && state.recastUntil > now) {
            state.recastUntil = 0L;
            // (a skill tried with /iruurpg skilltest whose move has no second part does the move again)
            if (!move.recast(player, weapon) && trying(id, slot)) move.use(player, weapon);
            return Done.RECAST;
        }
        // 3. no charge left: nothing
        if (state.charges <= 0) {
            coolingClick(player, now);
            return Done.NOTHING;
        }
        // 2. a charge: the move, then its re-activation opens
        if (!move.use(player, weapon)) return Done.NOTHING;
        if (state.charges >= mechanics.charges()) {
            state.refillStart = now;
            state.refillMillis = refillMillis(player, slot, mechanics);
        }
        state.charges--;
        if (mechanics.recasts()) state.recastUntil = now + Math.round(mechanics.recastSeconds() * 1000.0);
        return Done.USED;
    }

    /** {milliseconds left, the whole cooldown} of {@code slot} with the kind of {@code weapon} ({0, 0}: ready), or null when the player has nothing on it. */
    public long[] cooldown(Player player, Slot slot, RpgItemDefinition weapon) {
        return view(player, slot, weapon).cooldown();
    }

    /** What the icon of {@code slot} with the kind of {@code weapon} shows (t157): its cooldown, charges, refill and re-activation. */
    public View view(Player player, Slot slot, RpgItemDefinition weapon) {
        String classId = classOf.apply(player);
        if (move(classId, slot) == null) return View.of(fallback.cooldown(player, slot, weapon));
        UUID id = player.getUniqueId();
        return view(id, new Kept(kindOf(weapon), slot), mechanics(id, classId, slot), System.currentTimeMillis());
    }

    View view(UUID id, Kept kept, Mechanics mechanics, long now) {
        State state = state(id, kept, mechanics, now);
        boolean refilling = state.charges < mechanics.charges() && state.refillMillis > 0L;
        long left = refilling ? Math.max(0L, state.refillStart + state.refillMillis - now) : 0L;
        // (a skill of one charge: the refill of its only charge is its cooldown)
        long[] cooldown = state.charges > 0 || !refilling ? new long[]{0L, 0L} : new long[]{left, state.refillMillis};
        double refill = refilling ? 1.0 - (double) left / state.refillMillis : -1.0;
        return new View(cooldown, state.charges, mechanics.charges(), refill, mechanics.recasts() && state.recastUntil > now);
    }

    long[] cooldown(UUID id, Kept kept, long now) {
        return view(id, kept, Mechanics.NONE, now).cooldown();
    }

    /** The state of a skill, its charges counted again from the refill (a full skill has none running). */
    private State state(UUID id, Kept kept, Mechanics mechanics, long now) {
        State state = states.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>()).computeIfAbsent(kept, ignored -> new State(mechanics.charges()));
        refresh(state, mechanics.charges(), now);
        return state;
    }

    static void refresh(State state, int max, long now) {
        if (state.charges > max) state.charges = max;
        if (state.charges >= max) return;
        if (state.refillMillis <= 0L) {
            state.charges = max;
            return;
        }
        while (state.charges < max && now >= state.refillStart + state.refillMillis) {
            state.charges++;
            state.refillStart += state.refillMillis;
        }
    }

    /** A skill of one charge used at {@code now}: its cooldown is {@code millis} (for tests and the fallback's look). */
    void start(UUID id, Kept kept, long millis, long now) {
        State state = new State(0);
        state.refillStart = now;
        state.refillMillis = millis;
        states.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>()).put(kept, state);
    }

    /** How long one charge takes to refill: the mechanics' own, else the skill's cooldown; times the player's cooldown multiplier. */
    private long refillMillis(Player player, Slot slot, Mechanics mechanics) {
        double multiplier = Math.max(0.0, cooldownMultiplier.applyAsDouble(player));
        double seconds = mechanics.chargeSeconds() > 0.0 ? mechanics.chargeSeconds()
                : cooldownSeconds(plugin.getConfig().getConfigurationSection("combat.skill-slots.cooldown-seconds"), classOf.apply(player), slot);
        return Math.round(seconds * 1000.0 * multiplier);
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

    private final Map<UUID, Long> lastCoolingClick = new HashMap<>();

    /** A skill still cooling down: a dull click (not again and again for a held key: half a second apart), nothing done. */
    private void coolingClick(Player player, long now) {
        Long last = lastCoolingClick.put(player.getUniqueId(), now);
        if (last != null && now - last <= 500L) return;
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5f, 1.4f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastCoolingClick.remove(event.getPlayer().getUniqueId());
        endTrials(event.getPlayer().getUniqueId());
    }
}
