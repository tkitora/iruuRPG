package net.tkgon.mc.iruuRPG.questline;

import net.tkgon.mc.iruuRPG.questline.Questline.Act;
import net.tkgon.mc.iruuRPG.questline.Questline.Cond;
import net.tkgon.mc.iruuRPG.questline.Questline.FlagSpec;
import net.tkgon.mc.iruuRPG.questline.Questline.Handler;
import net.tkgon.mc.iruuRPG.questline.Questline.Line;
import net.tkgon.mc.iruuRPG.questline.Questline.Step;
import net.tkgon.mc.iruuRPG.questline.Questline.Transition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the quest files (format 1, design/quest-system-design-2026-10-31.md chapter 3) from plain maps. Nothing here
 * throws: what is wrong is reported in {@code warnings} and left out (an unknown condition becomes one that never holds,
 * so a transition never fires by mistake); only a line without steps or a usable first step is not loaded at all.
 */
public final class QuestlineParser {

    /** Condition types, with the key each one needs. */
    static final Map<String, String> CONDITIONS = Map.ofEntries(
            Map.entry("flag", "flag"), Map.entry("event", "event"), Map.entry("item", "item"), Map.entry("level", ""),
            Map.entry("class", "class"), Map.entry("quest", "quest"), Map.entry("step-time", "seconds"),
            Map.entry("not", "when"), Map.entry("any", "when"));
    /** Action types, with the key each one needs. */
    static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("set-flag", "flag"), Map.entry("add-flag", "flag"), Map.entry("clear-flag", "flag"),
            Map.entry("say", "text"), Map.entry("title", ""), Map.entry("sound", "sound"), Map.entry("give-item", "item"),
            Map.entry("take-item", "item"), Map.entry("reward", ""), Map.entry("cutscene", "id"), Map.entry("teleport", "x"),
            Map.entry("start-quest", "quest"), Map.entry("complete", ""), Map.entry("fail", ""));
    /** The condition that stands in for one that could not be read: it never holds. */
    static final String NEVER = "never";

    private final String file;
    private final List<String> warnings;

    private QuestlineParser(String file, List<String> warnings) {
        this.file = file;
        this.warnings = warnings;
    }

    /** The shared flags (flags/flags.yml): the declarations and the rules that run outside the quests. */
    public record FlagBook(Map<String, FlagSpec> flags, List<Handler> rules) {
        public static final FlagBook EMPTY = new FlagBook(Map.of(), List.of());
    }

    public static FlagBook parseFlags(Map<?, ?> raw, List<String> warnings) {
        QuestlineParser parser = new QuestlineParser("flags/flags.yml", warnings);
        if (raw == null) return FlagBook.EMPTY;
        Map<String, FlagSpec> flags = parser.flags(raw.get("flags"), "flags");
        List<Handler> rules = parser.handlers(raw.get("rules"), "rules");
        return new FlagBook(flags, rules);
    }

    /** One quest line, or null when it cannot be used (no steps, no first step). */
    public static Questline parse(String fileId, Map<?, ?> raw, Set<String> sharedFlags, List<String> warnings) {
        return new QuestlineParser("questlines/" + fileId + ".yml", warnings).line(fileId, raw, sharedFlags);
    }

    private Questline line(String fileId, Map<?, ?> raw, Set<String> sharedFlags) {
        if (raw == null) {
            warn("", "empty file");
            return null;
        }
        String id = text(raw.get("id"), fileId);
        if (!id.equals(fileId)) warn("id", "id '" + id + "' is not the file name; the file name is used");
        id = fileId;
        Map<String, FlagSpec> flags = flags(raw.get("flags"), "flags");
        for (String name : flags.keySet()) {
            if (sharedFlags.contains(name)) warn("flags." + name, "also a shared flag (flags/flags.yml): this line uses its own");
        }

        Map<?, ?> rawSteps = map(raw.get("steps"));
        if (rawSteps == null || rawSteps.isEmpty()) {
            warn("steps", "no steps: the line is not loaded");
            return null;
        }
        Map<String, Step> steps = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawSteps.entrySet()) {
            String stepId = String.valueOf(entry.getKey());
            steps.put(stepId, step(stepId, map(entry.getValue()), "steps." + stepId));
        }
        String initial = text(raw.get("initial"), null);
        if (initial == null || !steps.containsKey(initial)) {
            warn("initial", "the first step '" + initial + "' is not one of the steps: the line is not loaded");
            return null;
        }
        // transitions to steps that do not exist are dropped
        for (Step step : steps.values()) {
            step.transitions().removeIf(transition -> {
                if (steps.containsKey(transition.to())) return false;
                warn("steps." + step.id() + ".transitions", "no step '" + transition.to() + "': the transition is dropped");
                return true;
            });
        }

        Map<?, ?> rawStart = map(raw.get("start"));
        Questline.Start start = new Questline.Start(
                rawStart == null ? null : text(rawStart.get("npc"), null),
                rawStart == null ? List.of() : conditions(rawStart.get("when"), "start.when"),
                rawStart == null ? List.of() : lines(rawStart.get("offer"), "start.offer"));

        Questline line = new Questline(id, text(raw.get("name"), id), text(raw.get("category"), "main"),
                strings(raw.get("description")), bool(raw.get("repeatable")), start, flags, initial, steps);
        checkFlags(line, sharedFlags);
        return line;
    }

    private Step step(String id, Map<?, ?> raw, String where) {
        if (raw == null) raw = Map.of();
        Questline.End end = null;
        String endText = text(raw.get("end"), null);
        if (endText != null) {
            switch (endText.toLowerCase(Locale.ROOT)) {
                case "complete", "true" -> end = Questline.End.COMPLETE;
                case "fail" -> end = Questline.End.FAIL;
                default -> warn(where + ".end", "'" + endText + "' is not complete / fail");
            }
        }
        Questline.Progress progress = null;
        Map<?, ?> rawProgress = map(raw.get("progress"));
        if (rawProgress != null && rawProgress.get("flag") != null) {
            progress = new Questline.Progress(rawProgress.get("flag").toString(), (int) Questline.number(rawProgress.get("max"), 0));
        }
        Map<String, List<Line>> talk = new LinkedHashMap<>();
        Map<?, ?> rawTalk = map(raw.get("talk"));
        if (rawTalk != null) {
            for (Map.Entry<?, ?> entry : rawTalk.entrySet()) {
                talk.put(String.valueOf(entry.getKey()), lines(entry.getValue(), where + ".talk." + entry.getKey()));
            }
        }
        List<Map<String, Object>> spawns = new ArrayList<>();
        for (Object item : list(raw.get("spawns"))) {
            Map<?, ?> spawn = map(item);
            if (spawn != null) spawns.add(stringKeys(spawn));
        }
        List<Transition> transitions = new ArrayList<>();
        List<Object> rawTransitions = list(raw.get("transitions"));
        for (int index = 0; index < rawTransitions.size(); index++) {
            Map<?, ?> transition = map(rawTransitions.get(index));
            String to = transition == null ? null : text(transition.get("to"), null);
            if (to == null) {
                warn(where + ".transitions[" + index + "]", "no 'to': dropped");
                continue;
            }
            List<Cond> when = conditions(transition.get("when"), where + ".transitions[" + index + "].when");
            if (when.isEmpty()) warn(where + ".transitions[" + index + "]", "no 'when': it moves on at once");
            transitions.add(new Transition(to, when));
        }
        if (end != null && !transitions.isEmpty()) warn(where, "an end step has transitions: they are never used");
        return new Step(id, text(raw.get("name"), null), progress, end, talk, spawns,
                actions(raw.get("enter"), where + ".enter"), handlers(onEvent(raw), where + ".on-event"),
                actions(raw.get("exit"), where + ".exit"), transitions);
    }

    /**
     * The handlers of a step are under {@code on-event:}. A bare {@code on:} is read too, but YAML 1.1 (the server's
     * reader) takes that key for the boolean true, so it is looked up both ways.
     */
    private static Object onEvent(Map<?, ?> raw) {
        if (raw.get("on-event") != null) return raw.get("on-event");
        if (raw.get("on") != null) return raw.get("on");
        return raw.get(Boolean.TRUE);
    }

    private Map<String, FlagSpec> flags(Object raw, String where) {
        Map<String, FlagSpec> flags = new LinkedHashMap<>();
        Map<?, ?> rawFlags = map(raw);
        if (rawFlags == null) return flags;
        for (Map.Entry<?, ?> entry : rawFlags.entrySet()) {
            String id = String.valueOf(entry.getKey());
            Map<?, ?> spec = map(entry.getValue());
            if (spec == null) spec = Map.of();
            String type = text(spec.get("type"), "bool").toLowerCase(Locale.ROOT);
            boolean number = switch (type) {
                case "number", "counter", "int" -> true;
                case "bool", "boolean" -> false;
                default -> {
                    warn(where + "." + id, "type '" + type + "' is not bool / number: bool is used");
                    yield false;
                }
            };
            flags.put(id, new FlagSpec(id, number, Questline.number(spec.get("min"), Double.NEGATIVE_INFINITY),
                    Questline.number(spec.get("max"), Double.POSITIVE_INFINITY), text(spec.get("description"), ""), bool(spec.get("external"))));
        }
        return flags;
    }

    private List<Handler> handlers(Object raw, String where) {
        List<Handler> handlers = new ArrayList<>();
        List<Object> items = list(raw);
        for (int index = 0; index < items.size(); index++) {
            Map<?, ?> handler = map(items.get(index));
            String at = where + "[" + index + "]";
            if (handler == null) continue;
            Object rawEvent = handler.get("event");
            Cond event;
            if (rawEvent instanceof String name) {
                event = condition(Map.of("type", "event", "event", name), at + ".event");
            } else {
                event = condition(map(rawEvent), at + ".event");
            }
            if (event == null || !event.type().equals("event")) {
                warn(at, "'event' must be a condition of type event: dropped");
                continue;
            }
            handlers.add(new Handler(event, actions(handler.get("do"), at + ".do")));
        }
        return handlers;
    }

    private List<Cond> conditions(Object raw, String where) {
        List<Cond> conditions = new ArrayList<>();
        if (raw == null) return conditions;
        List<Object> items = raw instanceof List<?> ? list(raw) : List.of(raw);
        for (int index = 0; index < items.size(); index++) {
            Cond condition = condition(map(items.get(index)), where + "[" + index + "]");
            if (condition != null) conditions.add(condition);
        }
        return conditions;
    }

    private Cond condition(Map<?, ?> raw, String where) {
        if (raw == null) {
            warn(where, "not a condition: it never holds");
            return new Cond(NEVER, Map.of(), List.of());
        }
        String type = text(raw.get("type"), "").toLowerCase(Locale.ROOT);
        if (type.isEmpty() && raw.get("flag") != null) type = "flag"; // { flag: x } alone: the flag is set
        String needs = CONDITIONS.get(type);
        if (needs == null) {
            warn(where, type.equals("choice") ? "choice is not in format 1 yet: it never holds" : "unknown condition '" + type + "': it never holds");
            return new Cond(NEVER, Map.of(), List.of());
        }
        if (!needs.isEmpty() && raw.get(needs) == null) {
            warn(where, type + " needs '" + needs + "': it never holds");
            return new Cond(NEVER, Map.of(), List.of());
        }
        if (type.equals("event")) {
            String event = raw.get("event").toString().toLowerCase(Locale.ROOT);
            if (!QuestEvent.KNOWN.containsKey(event)) warn(where, "event '" + event + "' is not supported by the plugin: it never happens");
        }
        List<Cond> children = type.equals("not") || type.equals("any") ? conditions(raw.get("when"), where + ".when") : List.of();
        Map<String, Object> args = stringKeys(raw);
        args.remove("type");
        args.remove("when");
        return new Cond(type, args, children);
    }

    private List<Act> actions(Object raw, String where) {
        List<Act> actions = new ArrayList<>();
        List<Object> items = list(raw);
        for (int index = 0; index < items.size(); index++) {
            String at = where + "[" + index + "]";
            Object item = items.get(index);
            Map<?, ?> act = item instanceof String name ? Map.of("type", name) : map(item);
            if (act == null) {
                warn(at, "not an action: dropped");
                continue;
            }
            String type = text(act.get("type"), text(act.get("do"), "")).toLowerCase(Locale.ROOT);
            String needs = ACTIONS.get(type);
            if (needs == null) {
                warn(at, "unknown action '" + type + "': dropped");
                continue;
            }
            if (!needs.isEmpty() && act.get(needs) == null) {
                warn(at, type + " needs '" + needs + "': dropped");
                continue;
            }
            Map<String, Object> args = stringKeys(act);
            args.remove("type");
            args.remove("do");
            actions.add(new Act(type, args));
        }
        return actions;
    }

    private List<Line> lines(Object raw, String where) {
        List<Line> lines = new ArrayList<>();
        for (Object item : list(raw)) {
            if (item instanceof String text) {
                lines.add(new Line("narration", text, -1));
                continue;
            }
            Map<?, ?> line = map(item);
            if (line == null || line.get("text") == null) {
                warn(where, "a line without text: dropped");
                continue;
            }
            lines.add(new Line(text(line.get("speaker"), "narration"), line.get("text").toString(), (int) Questline.number(line.get("ticks"), -1)));
        }
        return lines;
    }

    /** Every flag a line reads or writes must be its own or a shared one (else it is never set by anything declared). */
    private void checkFlags(Questline line, Set<String> sharedFlags) {
        java.util.function.Consumer<String> check = flag -> {
            if (flag != null && !line.flags().containsKey(flag) && !sharedFlags.contains(flag)) {
                warn("flags", "flag '" + flag + "' is not declared (here or in flags/flags.yml)");
            }
        };
        List<Cond> conditions = new ArrayList<>(line.start().when());
        List<Act> actions = new ArrayList<>();
        for (Step step : line.steps().values()) {
            for (Transition transition : step.transitions()) conditions.addAll(transition.when());
            actions.addAll(step.enter());
            actions.addAll(step.exit());
            for (Handler handler : step.on()) actions.addAll(handler.actions());
            if (step.progress() != null) check.accept(step.progress().flag());
        }
        for (Cond condition : flatten(conditions)) {
            if (condition.type().equals("flag")) check.accept(condition.string("flag"));
        }
        for (Act action : actions) {
            if (action.type().endsWith("-flag")) check.accept(action.string("flag"));
        }
    }

    static List<Cond> flatten(Collection<Cond> conditions) {
        List<Cond> all = new ArrayList<>();
        for (Cond condition : conditions) {
            all.add(condition);
            all.addAll(flatten(condition.children()));
        }
        return all;
    }

    private void warn(String where, String message) {
        warnings.add(file + (where.isEmpty() ? "" : ": " + where) + ": " + message);
    }

    private static Map<?, ?> map(Object raw) {
        return raw instanceof Map<?, ?> map ? map : null;
    }

    private static List<Object> list(Object raw) {
        if (raw instanceof List<?> list) return new ArrayList<>(list);
        return raw == null ? List.of() : List.of(raw);
    }

    private static Map<String, Object> stringKeys(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) out.put(String.valueOf(entry.getKey()), entry.getValue());
        return out;
    }

    private static List<String> strings(Object raw) {
        List<String> out = new ArrayList<>();
        for (Object item : list(raw)) out.add(String.valueOf(item));
        return out;
    }

    private static String text(Object raw, String fallback) {
        return raw == null ? fallback : raw.toString();
    }

    private static boolean bool(Object raw) {
        return raw instanceof Boolean bool ? bool : raw != null && Boolean.parseBoolean(raw.toString());
    }
}
