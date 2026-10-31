package net.tkgon.mc.iruuRPG.questline;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Something that happened to a player, for the quest lines: its name and what it was about (the mob, the item, the
 * NPC ...). A condition {@code {type: event, event: <name>, <key>: <value>...}} matches it when the names are the same and
 * every key it names has the same value (keys it leaves out match anything).
 */
public record QuestEvent(String name, Map<String, Object> args) {

    /** The events of the format (3-3 of the design), with the keys a condition may filter on. */
    public static final Map<String, List<String>> KNOWN = Map.ofEntries(
            Map.entry("mob-kill", List.of("mob")),
            Map.entry("item-pickup", List.of("item")),
            Map.entry("npc-talk", List.of("npc")),
            Map.entry("item-give", List.of("npc", "item", "amount")),
            Map.entry("dungeon-clear", List.of("dungeon")),
            Map.entry("area-enter", List.of("world", "x", "y", "z", "radius")),
            Map.entry("craft", List.of("item")),
            Map.entry("level-up", List.of("level")),
            Map.entry("cutscene-end", List.of("cutscene")),
            Map.entry("quest-complete", List.of("quest")),
            Map.entry("step-enter", List.of())
    );

    public static final String STEP_ENTER = "step-enter";

    public QuestEvent {
        name = name == null ? "" : name.toLowerCase(Locale.ROOT);
        args = args == null ? Map.of() : Map.copyOf(args);
    }

    public static QuestEvent of(String name, Object... keysAndValues) {
        Map<String, Object> args = new HashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            if (keysAndValues[index + 1] != null) args.put(keysAndValues[index].toString(), keysAndValues[index + 1]);
        }
        return new QuestEvent(name, args);
    }

    /** True when {@code filter} (a condition of type event) names this event and all its keys agree with it. */
    public boolean matches(Questline.Cond filter) {
        if (!name.equals(Objects.requireNonNullElse(filter.string("event"), "").toLowerCase(Locale.ROOT))) return false;
        if (name.equals("area-enter")) return sameArea(filter);
        for (String key : KNOWN.getOrDefault(name, List.of())) {
            Object want = filter.args().get(key);
            if (want == null) continue;
            Object have = args.get(key);
            if (have == null) return false;
            if (key.equals("amount")) {
                if (Questline.number(have, 0) < Questline.number(want, 0)) return false;
            } else if (want instanceof Number || have instanceof Number) {
                if (Math.abs(Questline.number(have, Double.NaN) - Questline.number(want, Double.NaN)) > 1.0E-9) return false;
            } else if (!want.toString().equalsIgnoreCase(have.toString())) {
                return false;
            }
        }
        return true;
    }

    /** An area event carries the area it was (its key); the filter's world / x / y / z / radius make the same key. */
    private boolean sameArea(Questline.Cond filter) {
        return areaKey(filter.args()).equals(args.get("area"));
    }

    /** The key of an area: world, the center and the radius, rounded to tenths. */
    public static String areaKey(Map<String, Object> area) {
        return String.format(Locale.ROOT, "%s@%.1f,%.1f,%.1f~%.1f",
                Objects.requireNonNullElse(area.get("world"), "world"),
                Questline.number(area.get("x"), 0), Questline.number(area.get("y"), 0), Questline.number(area.get("z"), 0),
                Questline.number(area.get("radius"), 3));
    }
}
