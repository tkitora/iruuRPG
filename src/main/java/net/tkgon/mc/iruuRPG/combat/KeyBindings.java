package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which two keys do what (t138, the style {@code keys} of {@link HeavyInput}): F or Q, and then F, Q, the weapon's other button or its normal
 * button, within {@code combat.heavy-input.key-window-ticks}. The 8 patterns are given to the 8 actions (skills 1 - 6, the class's special
 * and the weapon's skill), each player's own choice (the settings menu): saved in the player's data when it is not the defaults, cached while
 * they are online.
 */
public final class KeyBindings implements Listener {

    /** A key of a pattern. OTHER and NORMAL are the weapon's buttons: the normal one is the left with a melee weapon, the right with any other. */
    public enum Key { F, Q, OTHER, NORMAL }

    /** What a pattern does. */
    public enum Action {
        SKILL1("スキル1"), SKILL2("スキル2"), SKILL3("スキル3"), SKILL4("スキル4"), SKILL5("スキル5"), SKILL6("スキル6"),
        /** The class's special: its active skill (the one that was on F alone before t138). */
        ULTIMATE("必殺"),
        /** The skill of the weapon in hand (the one that was on Q alone before t138). */
        ITEM("アイテムスキル");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** The heavy attack (0 - 5) of a skill, -1 for the others. */
        public int heavyIndex() {
            return ordinal() <= SKILL6.ordinal() ? ordinal() : -1;
        }
    }

    /** Two keys: F or Q, and then any key. */
    public record Pattern(Key first, Key second) {

        /** All 8, in the order of the actions they do by default (skill 1 = F, F ... the weapon's skill = Q, Q). */
        public static final List<Pattern> ALL = List.of(
                new Pattern(Key.F, Key.F), new Pattern(Key.F, Key.OTHER), new Pattern(Key.F, Key.NORMAL), new Pattern(Key.F, Key.Q),
                new Pattern(Key.Q, Key.OTHER), new Pattern(Key.Q, Key.NORMAL), new Pattern(Key.Q, Key.F), new Pattern(Key.Q, Key.Q));

        /** Two letters: F / Q, and F / Q / O (other) / N (normal). */
        public String code() {
            return first.name().substring(0, 1) + second.name().substring(0, 1);
        }

        public static Pattern parse(String code) {
            for (Pattern pattern : ALL) if (pattern.code().equalsIgnoreCase(code == null ? "" : code.trim())) return pattern;
            return null;
        }

        /** The keys as they are pressed with a weapon: "F→右" (the other button of a melee weapon is the right one). */
        public String label(boolean melee) {
            return keyLabel(first, melee) + "→" + keyLabel(second, melee);
        }

        static String keyLabel(Key key, boolean melee) {
            return switch (key) {
                case F -> "F";
                case Q -> "Q";
                case OTHER -> melee ? "右" : "左";
                case NORMAL -> melee ? "左" : "右";
            };
        }
    }

    /**
     * The bindings as lines of text for a weapon ({@code melee}: its normal button is the left one): skills 1 - 3, skills 4 - 6, the special
     * and the weapon's skill. "F→F: スキル1 / F→右: スキル2 / F→左: スキル3".
     */
    public static List<String> describe(Map<Action, Pattern> bindings, boolean melee) {
        List<String> lines = new ArrayList<>();
        Action[][] groups = {{Action.SKILL1, Action.SKILL2, Action.SKILL3}, {Action.SKILL4, Action.SKILL5, Action.SKILL6}, {Action.ULTIMATE, Action.ITEM}};
        for (Action[] group : groups) {
            List<String> parts = new ArrayList<>();
            for (Action action : group) parts.add(bindings.get(action).label(melee) + ": " + action.label());
            lines.add(String.join(" / ", parts));
        }
        return lines;
    }

    public static final Map<Action, Pattern> DEFAULTS;

    static {
        Map<Action, Pattern> defaults = new EnumMap<>(Action.class);
        for (Action action : Action.values()) defaults.put(action, Pattern.ALL.get(action.ordinal()));
        DEFAULTS = java.util.Collections.unmodifiableMap(defaults);
    }

    private final NamespacedKey dataKey;
    private final Map<UUID, Map<Action, Pattern>> cache = new ConcurrentHashMap<>();

    public KeyBindings(JavaPlugin plugin) {
        this.dataKey = new NamespacedKey(plugin, "key_bindings");
    }

    /** The bindings of a player (a copy). */
    public Map<Action, Pattern> of(Player player) {
        return new EnumMap<>(data(player));
    }

    public Pattern patternOf(Player player, Action action) {
        return data(player).get(action);
    }

    /** The action of a pattern for a player (every pattern has one). */
    public Action actionOf(Player player, Pattern pattern) {
        return actionOf(data(player), pattern);
    }

    static Action actionOf(Map<Action, Pattern> bindings, Pattern pattern) {
        for (Map.Entry<Action, Pattern> entry : bindings.entrySet()) if (entry.getValue().equals(pattern)) return entry.getKey();
        return null;
    }

    /** Gives {@code pattern} to {@code action}; the action that had it takes the action's old pattern (so every pattern still has one action). */
    public void set(Player player, Action action, Pattern pattern) {
        Map<Action, Pattern> bindings = data(player);
        swap(bindings, action, pattern);
        save(player, bindings);
    }

    static void swap(Map<Action, Pattern> bindings, Action action, Pattern pattern) {
        Action holder = actionOf(bindings, pattern);
        Pattern old = bindings.get(action);
        bindings.put(action, pattern);
        if (holder != null && holder != action) bindings.put(holder, old);
    }

    public void reset(Player player) {
        Map<Action, Pattern> bindings = data(player);
        bindings.clear();
        bindings.putAll(DEFAULTS);
        save(player, bindings);
    }

    /** The bindings as saved: the 8 patterns' codes in the order of the actions, "FF,FO,...". */
    static String format(Map<Action, Pattern> bindings) {
        List<String> codes = new ArrayList<>();
        for (Action action : Action.values()) codes.add(bindings.get(action).code());
        return String.join(",", codes);
    }

    /** The bindings saved as {@link #format} says; null when they are not 8 different patterns. */
    static Map<Action, Pattern> parse(String text) {
        if (text == null) return null;
        String[] codes = text.split(",");
        if (codes.length != Action.values().length) return null;
        Map<Action, Pattern> bindings = new EnumMap<>(Action.class);
        Set<Pattern> seen = new HashSet<>();
        for (Action action : Action.values()) {
            Pattern pattern = Pattern.parse(codes[action.ordinal()]);
            if (pattern == null || !seen.add(pattern)) return null;
            bindings.put(action, pattern);
        }
        return bindings;
    }

    private Map<Action, Pattern> data(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> {
            Map<Action, Pattern> saved = parse(player.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING));
            return saved != null ? saved : new EnumMap<>(DEFAULTS);
        });
    }

    private void save(Player player, Map<Action, Pattern> bindings) {
        if (bindings.equals(DEFAULTS)) player.getPersistentDataContainer().remove(dataKey);
        else player.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, format(bindings));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
