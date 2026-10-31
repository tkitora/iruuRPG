package net.tkgon.mc.iruuRPG.questline;

import java.util.List;
import java.util.Map;

/**
 * One quest line (questlines/&lt;id&gt;.yml): a state machine whose states are the steps of the quest. A step moves on by
 * its transitions ({@code to} + the conditions of {@code when}, all of which must hold, the first one that holds wins: the
 * same shape as a mob's FSM). Steps with {@code end} finish the line. See design/quest-system-design-2026-10-31.md.
 */
public record Questline(
        String id,
        String name,
        String category,
        boolean exclusive,
        List<String> description,
        boolean repeatable,
        Start start,
        Map<String, FlagSpec> flags,
        String initial,
        Map<String, Step> steps
) {

    /** A main line: only one runs at a time (starting another puts the running one on hold). */
    public boolean main() {
        return "main".equals(category);
    }

    public Step step(String stepId) {
        return stepId == null ? null : steps.get(stepId);
    }

    /** How the line is taken: from an NPC (talking to it) or by itself once {@code when} holds (no npc). */
    public record Start(String npc, List<Cond> when, List<Line> offer) {
    }

    /**
     * One step: what it shows (name: the goal on the menu, null when not given), what exists while it lasts (spawns),
     * what it does on enter / exit / events, where it goes.
     */
    public record Step(
            String id,
            String name,
            Progress progress,
            End end,
            Map<String, List<Line>> talk,
            List<Map<String, Object>> spawns,
            List<Act> enter,
            List<Handler> on,
            List<Act> exit,
            List<Transition> transitions
    ) {
    }

    public enum End { COMPLETE, FAIL }

    /**
     * "3 / 5" on the menu: the value of a flag, or the number of an item the player has ({@code item}; with
     * {@code ignoreExisting} only what they got since the step began), against a maximum; label is what the chat says it
     * counts ("討伐:イノシシ").
     */
    public record Progress(String flag, String item, boolean ignoreExisting, int max, String label) {
    }

    /** One line of talk: an NPC id, "narration" or "player", the text and how long it stays (-1 = from its length). */
    public record Line(String speaker, String text, int ticks) {
    }

    public record Transition(String to, List<Cond> when) {
    }

    /** What a step does when an event matching {@code event} reaches it. */
    public record Handler(Cond event, List<Act> actions) {
    }

    /** A condition: its type, its arguments, and the inner conditions of not / any. */
    public record Cond(String type, Map<String, Object> args, List<Cond> children) {

        public String string(String key) {
            Object value = args.get(key);
            return value == null ? null : value.toString();
        }

        public double number(String key, double fallback) {
            return Questline.number(args.get(key), fallback);
        }
    }

    /** An action: its type and arguments. */
    public record Act(String type, Map<String, Object> args) {

        public String string(String key) {
            Object value = args.get(key);
            return value == null ? null : value.toString();
        }

        public double number(String key, double fallback) {
            return Questline.number(args.get(key), fallback);
        }
    }

    /** A declared flag: a yes/no or a number (clamped to min..max); external = set from outside quests too. */
    public record FlagSpec(String id, boolean number, double min, double max, String description, boolean external) {
    }

    public static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Boolean bool) return bool ? 1.0 : 0.0;
        if (value != null) {
            try {
                return Double.parseDouble(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
