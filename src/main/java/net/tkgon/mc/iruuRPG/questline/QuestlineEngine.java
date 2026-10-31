package net.tkgon.mc.iruuRPG.questline;

import net.tkgon.mc.iruuRPG.questline.Questline.Act;
import net.tkgon.mc.iruuRPG.questline.Questline.Cond;
import net.tkgon.mc.iruuRPG.questline.Questline.FlagSpec;
import net.tkgon.mc.iruuRPG.questline.Questline.Handler;
import net.tkgon.mc.iruuRPG.questline.Questline.Line;
import net.tkgon.mc.iruuRPG.questline.Questline.Step;
import net.tkgon.mc.iruuRPG.questline.Questline.Transition;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.LineState;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.Status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the quest lines of one player at a time (no Bukkit here: what touches the game goes through {@link Game}).
 * <p>
 * An event first runs the shared rules (flags/flags.yml) and the {@code on:} of the current step of every active line,
 * then the lines settle: each one takes the first of its transitions whose conditions all hold (the event counts only
 * in that first pass), leaves the step ({@code exit:}), enters the next ({@code enter:}, then a {@code step-enter} event
 * of its own) and finishes on a step with {@code end}. Lines that start by themselves start when their conditions hold.
 * A flag written from outside settles the lines in the same way. A budget of moves per call stops a loop of steps.
 */
public final class QuestlineEngine {

    /** The moves one call may make, all lines together (a loop of steps that never waits stops here). */
    static final int MOVE_BUDGET = 64;

    /** The player's side: what the conditions read and what the actions do in the game. */
    public interface Game {
        long now();

        int level();

        String classId();

        int itemCount(String item);

        void takeItem(String item, int amount);

        /** An action the engine does not do itself (say, title, sound, give-item, take-item, reward, cutscene, teleport); line null = a shared rule. */
        void perform(Questline line, Act act);

        void started(Questline line);

        void stepChanged(Questline line, Step step);

        void finished(Questline line, Questline.End end);

        void warn(String message);
    }

    private final Map<String, Questline> lines;
    private final QuestlineParser.FlagBook book;

    public QuestlineEngine(Map<String, Questline> lines, QuestlineParser.FlagBook book) {
        this.lines = new LinkedHashMap<>(lines);
        this.book = book == null ? QuestlineParser.FlagBook.EMPTY : book;
    }

    public Map<String, Questline> lines() {
        return lines;
    }

    public QuestlineParser.FlagBook book() {
        return book;
    }

    public Questline line(String id) {
        return lines.get(id);
    }

    private static final class Run {
        final QuestlineData data;
        final Game game;
        int budget = MOVE_BUDGET;
        boolean changed;

        Run(QuestlineData data, Game game) {
            this.data = data;
            this.game = game;
        }
    }

    // ---- entry points -------------------------------------------------------------------------------------------

    /** Something happened to the player. True when anything of their quests changed. */
    public boolean fire(QuestlineData data, Game game, QuestEvent event) {
        Run run = new Run(data, game);
        dispatch(run, event);
        settle(run, event);
        return run.changed;
    }

    /** No event: the lines that can move on or start by themselves do (after a flag, an item or the level changed). */
    public boolean evaluate(QuestlineData data, Game game) {
        Run run = new Run(data, game);
        settle(run, null);
        return run.changed;
    }

    /** Starts a line ({@code force}: without its conditions, and again even when it is not repeatable). */
    public boolean start(QuestlineData data, Game game, String id, boolean force) {
        Run run = new Run(data, game);
        boolean started = start(run, id, force);
        if (started) settle(run, null);
        return started;
    }

    /** True when the line could be started now (not running, repeatable if done before, its conditions hold); nothing changes. */
    public boolean canStart(QuestlineData data, Game game, String id) {
        Questline line = lines.get(id);
        if (line == null) return false;
        LineState old = data.line(id);
        if (old != null && (old.status == Status.ACTIVE || old.status == Status.PAUSED || !line.repeatable())) return false;
        return allHold(new Run(data, game), line, line.start().when(), null);
    }

    /** The lines an NPC offers (their start.npc is it) that could be started now. */
    public List<Questline> offers(QuestlineData data, Game game, String npc) {
        List<Questline> out = new ArrayList<>();
        for (Questline line : lines.values()) {
            if (npc.equals(line.start().npc()) && canStart(data, game, line.id())) out.add(line);
        }
        return out;
    }

    /** True when an active line of the player waits on this NPC now (talk for it, or a talk / give event of it, in the current step). */
    public boolean waitsOn(QuestlineData data, String npc) {
        for (var entry : data.lines().entrySet()) {
            if (entry.getValue().status != Status.ACTIVE) continue;
            Questline line = lines.get(entry.getKey());
            Step step = line == null ? null : line.step(entry.getValue().step);
            if (step != null && mentions(step, npc)) return true;
        }
        return false;
    }

    /** The items an NPC takes now: the item-give events of the current steps of the active lines (npc, item, amount). */
    public List<Cond> gives(QuestlineData data, String npc) {
        List<Cond> out = new ArrayList<>();
        for (var entry : data.lines().entrySet()) {
            if (entry.getValue().status != Status.ACTIVE) continue;
            Questline line = lines.get(entry.getKey());
            Step step = line == null ? null : line.step(entry.getValue().step);
            if (step == null) continue;
            for (Cond condition : eventConditions(step)) {
                if ("item-give".equals(condition.string("event")) && npc.equals(condition.string("npc")) && condition.string("item") != null) out.add(condition);
            }
        }
        return out;
    }

    /** Where an NPC is used: "line 開始" for the lines it offers, "line › step (話す / 渡す)" for the steps that wait on it. */
    public List<String> usesOf(String npc) {
        List<String> out = new ArrayList<>();
        for (Questline line : lines.values()) {
            if (npc.equals(line.start().npc())) out.add(line.id() + " 開始");
            for (Step step : line.steps().values()) {
                if (!mentions(step, npc)) continue;
                boolean give = eventConditions(step).stream().anyMatch(c -> "item-give".equals(c.string("event")) && npc.equals(c.string("npc")));
                out.add(line.id() + " › " + step.id() + (give ? " (渡す)" : " (話す)"));
            }
        }
        return out;
    }

    private static boolean mentions(Step step, String npc) {
        if (step.talk().containsKey(npc)) return true;
        for (Cond condition : eventConditions(step)) {
            String event = condition.string("event");
            if (("npc-talk".equals(event) || "item-give".equals(event)) && npc.equals(condition.string("npc"))) return true;
        }
        return false;
    }

    private static List<Cond> eventConditions(Step step) {
        List<Cond> conditions = new ArrayList<>();
        for (Transition transition : step.transitions()) conditions.addAll(transition.when());
        for (Handler handler : step.on()) conditions.add(handler.event());
        List<Cond> out = new ArrayList<>();
        for (Cond condition : QuestlineParser.flatten(conditions)) {
            if (condition.type().equals("event")) out.add(condition);
        }
        return out;
    }

    /** Puts a line on a step (a debug tool: the old step's exit is not run, the new step's enter is). */
    public boolean setStep(QuestlineData data, Game game, String id, String stepId) {
        Questline line = lines.get(id);
        if (line == null || line.step(stepId) == null) return false;
        Run run = new Run(data, game);
        LineState state = data.line(id);
        if (state == null) {
            state = new LineState(Status.ACTIVE, stepId, game.now());
            data.lines().put(id, state);
        }
        state.status = Status.ACTIVE;
        state.waitingCutscene = null;
        enterStep(run, line, state, stepId);
        settle(run, null);
        return true;
    }

    public boolean pause(QuestlineData data, String id) {
        LineState state = data.line(id);
        if (state == null || state.status != Status.ACTIVE) return false;
        state.status = Status.PAUSED;
        return true;
    }

    public boolean resume(QuestlineData data, Game game, String id) {
        LineState state = data.line(id);
        if (state == null || state.status != Status.PAUSED) return false;
        state.status = Status.ACTIVE;
        evaluate(data, game);
        return true;
    }

    /** Writes a flag ({@code lineId} null: a shared one; value null clears it) and lets the lines settle. */
    public boolean writeFlag(QuestlineData data, Game game, String lineId, String flag, Object value) {
        Run run = new Run(data, game);
        Map<String, Object> store = store(run.data, lineId, flag);
        if (store == null) return false;
        if (value == null) {
            store.remove(flag);
        } else {
            store.put(flag, clamp(spec(lineId, flag), value));
        }
        settle(run, null);
        return true;
    }

    /** What the NPC says to the player now: the talk of the current step of each active line that has some for it. */
    public List<Line> talk(QuestlineData data, String npc) {
        List<Line> said = new ArrayList<>();
        for (var entry : data.lines().entrySet()) {
            if (entry.getValue().status != Status.ACTIVE) continue;
            Questline line = lines.get(entry.getKey());
            Step step = line == null ? null : line.step(entry.getValue().step);
            if (step != null && step.talk().containsKey(npc)) said.addAll(step.talk().get(npc));
        }
        return said;
    }

    /**
     * The lines that depend on {@code id} (they start after it, or wait for it to be completed): a reset of {@code id}
     * resets them too, so nothing is left ahead of what it needs.
     */
    public Set<String> dependents(String id) {
        Set<String> out = new java.util.LinkedHashSet<>();
        collectDependents(id, out);
        out.remove(id);
        return out;
    }

    private void collectDependents(String id, Set<String> out) {
        for (Questline line : lines.values()) {
            if (out.contains(line.id())) continue;
            if (refersTo(line, id)) {
                out.add(line.id());
                collectDependents(line.id(), out);
            }
        }
    }

    private static boolean refersTo(Questline line, String id) {
        List<Cond> conditions = new ArrayList<>(line.start().when());
        for (Step step : line.steps().values()) {
            for (Transition transition : step.transitions()) conditions.addAll(transition.when());
            for (Handler handler : step.on()) conditions.add(handler.event());
        }
        for (Cond condition : QuestlineParser.flatten(conditions)) {
            if (condition.type().equals("quest") && id.equals(condition.string("quest"))) return true;
            if (condition.type().equals("event") && "quest-complete".equals(condition.string("event")) && id.equals(condition.string("quest"))) return true;
        }
        return false;
    }

    /** "3 / 5" for a step with a progress, else null. */
    public String progress(QuestlineData data, String id) {
        Questline line = lines.get(id);
        LineState state = data.line(id);
        if (line == null || state == null) return null;
        Step step = line.step(state.step);
        if (step == null || step.progress() == null) return null;
        Object value = read(data, id, step.progress().flag());
        return (long) Questline.number(value, 0) + " / " + step.progress().max();
    }

    /** A flag's value: the line's own when it declares it, else the shared one. */
    public Object read(QuestlineData data, String lineId, String flag) {
        Map<String, Object> store = store(data, lineId, flag);
        return store == null ? null : store.get(flag);
    }

    // ---- the work -------------------------------------------------------------------------------------------------

    private void dispatch(Run run, QuestEvent event) {
        for (Handler rule : book.rules()) {
            if (event.matches(rule.event())) actions(run, null, rule.actions());
        }
        for (String id : new ArrayList<>(run.data.lines().keySet())) {
            LineState state = run.data.line(id);
            Questline line = lines.get(id);
            if (state == null || line == null || state.status != Status.ACTIVE) continue;
            if (state.waitingCutscene != null) {
                if (!event.name().equals("cutscene-end") || !state.waitingCutscene.equalsIgnoreCase(String.valueOf(event.args().get("cutscene")))) continue;
                state.waitingCutscene = null;
                run.changed = true;
            }
            Step step = line.step(state.step);
            if (step == null) continue;
            for (Handler handler : step.on()) {
                if (event.matches(handler.event())) actions(run, line, handler.actions());
            }
        }
    }

    private void settle(Run run, QuestEvent event) {
        QuestEvent current = event;
        boolean moved = true;
        while (moved && run.budget > 0) {
            moved = false;
            for (String id : new ArrayList<>(run.data.lines().keySet())) {
                if (advance(run, id, current)) moved = true;
            }
            current = null;
            if (autoStart(run)) moved = true;
        }
        if (run.budget <= 0) run.game.warn("quest lines: too many steps in one go (a loop?); stopped");
    }

    /** One transition of one line, if one holds. */
    private boolean advance(Run run, String id, QuestEvent event) {
        LineState state = run.data.line(id);
        Questline line = lines.get(id);
        if (state == null || line == null || state.status != Status.ACTIVE || state.waitingCutscene != null) return false;
        Step step = line.step(state.step);
        if (step == null || step.end() != null) return false;
        for (Transition transition : step.transitions()) {
            if (!allHold(run, line, transition.when(), event)) continue;
            for (Cond condition : transition.when()) {
                if (condition.type().equals("item") && Boolean.TRUE.equals(condition.args().get("take"))) {
                    run.game.takeItem(condition.string("item"), (int) condition.number("amount", 1));
                }
            }
            actions(run, line, step.exit());
            if (state.status != Status.ACTIVE || !step.id().equals(state.step)) return true; // an exit action moved it already
            enterStep(run, line, state, transition.to());
            return true;
        }
        return false;
    }

    private void enterStep(Run run, Questline line, LineState state, String stepId) {
        if (--run.budget < 0) return;
        run.changed = true;
        Step step = line.step(stepId);
        state.step = stepId;
        state.stepSince = run.game.now();
        run.game.stepChanged(line, step);
        actions(run, line, step.enter());
        if (state.status != Status.ACTIVE || !stepId.equals(state.step)) return; // an enter action finished or moved it
        if (step.end() != null) {
            finish(run, line, state, step.end());
            return;
        }
        QuestEvent entered = QuestEvent.of(QuestEvent.STEP_ENTER);
        for (Handler handler : step.on()) {
            if (entered.matches(handler.event())) actions(run, line, handler.actions());
        }
        advance(run, line.id(), entered);
    }

    private void finish(Run run, Questline line, LineState state, Questline.End end) {
        state.status = end == Questline.End.COMPLETE ? Status.COMPLETED : Status.FAILED;
        state.waitingCutscene = null;
        if (end == Questline.End.COMPLETE) state.runs++;
        run.changed = true;
        run.game.finished(line, end);
        if (end == Questline.End.COMPLETE) {
            QuestEvent done = QuestEvent.of("quest-complete", "quest", line.id());
            dispatch(run, done);
            for (String id : new ArrayList<>(run.data.lines().keySet())) {
                if (!id.equals(line.id())) advance(run, id, done);
            }
        }
    }

    private boolean start(Run run, String id, boolean force) {
        Questline line = lines.get(id);
        if (line == null) return false;
        LineState old = run.data.line(id);
        if (old != null && (old.status == Status.ACTIVE || old.status == Status.PAUSED)) return false;
        if (!force) {
            if (old != null && !line.repeatable()) return false;
            if (!allHold(run, line, line.start().when(), null)) return false;
        }
        LineState state = new LineState(Status.ACTIVE, line.initial(), run.game.now());
        state.runs = old == null ? 0 : old.runs;
        run.data.lines().put(id, state);
        run.changed = true;
        run.game.started(line);
        enterStep(run, line, state, line.initial());
        return true;
    }

    private boolean autoStart(Run run) {
        boolean any = false;
        for (Questline line : lines.values()) {
            if (line.start().npc() != null || run.data.line(line.id()) != null) continue;
            if (start(run, line.id(), false)) any = true;
        }
        return any;
    }

    // ---- actions ----------------------------------------------------------------------------------------------------

    private void actions(Run run, Questline line, List<Act> actions) {
        String lineId = line == null ? null : line.id();
        for (Act act : actions) {
            switch (act.type()) {
                case "set-flag" -> {
                    Map<String, Object> store = store(run.data, lineId, act.string("flag"));
                    if (store != null) store.put(act.string("flag"), clamp(spec(lineId, act.string("flag")), act.args().getOrDefault("value", true)));
                    run.changed = true;
                }
                case "add-flag" -> {
                    String flag = act.string("flag");
                    Map<String, Object> store = store(run.data, lineId, flag);
                    if (store != null) {
                        double next = Questline.number(store.get(flag), 0) + act.number("value", 1);
                        store.put(flag, clamp(spec(lineId, flag), next));
                    }
                    run.changed = true;
                }
                case "clear-flag" -> {
                    Map<String, Object> store = store(run.data, lineId, act.string("flag"));
                    if (store != null) store.remove(act.string("flag"));
                    run.changed = true;
                }
                case "start-quest" -> start(run, act.string("quest"), false);
                case "complete", "fail" -> {
                    LineState state = lineId == null ? null : run.data.line(lineId);
                    if (state != null && state.status == Status.ACTIVE) {
                        finish(run, line, state, act.type().equals("complete") ? Questline.End.COMPLETE : Questline.End.FAIL);
                    }
                }
                case "cutscene" -> {
                    run.game.perform(line, act);
                    LineState state = lineId == null ? null : run.data.line(lineId);
                    if (state != null && !Boolean.FALSE.equals(act.args().get("wait"))) state.waitingCutscene = act.string("id");
                }
                default -> run.game.perform(line, act);
            }
        }
    }

    // ---- conditions -------------------------------------------------------------------------------------------------

    private boolean allHold(Run run, Questline line, List<Cond> conditions, QuestEvent event) {
        for (Cond condition : conditions) {
            if (!holds(run, line, condition, event)) return false;
        }
        return true;
    }

    private boolean holds(Run run, Questline line, Cond condition, QuestEvent event) {
        String lineId = line == null ? null : line.id();
        return switch (condition.type()) {
            case "flag" -> flagHolds(run.data, lineId, condition);
            case "event" -> event != null && event.matches(condition);
            case "item" -> run.game.itemCount(condition.string("item")) >= condition.number("amount", 1);
            case "level" -> {
                int level = run.game.level();
                yield level >= condition.number("min", Double.NEGATIVE_INFINITY) && level <= condition.number("max", Double.POSITIVE_INFINITY);
            }
            case "class" -> condition.string("class").equalsIgnoreCase(String.valueOf(run.game.classId()));
            case "quest" -> {
                LineState other = run.data.line(condition.string("quest"));
                String want = condition.string("status") == null ? "completed" : condition.string("status");
                yield want.equalsIgnoreCase("none") ? other == null : other != null && other.status.key().equalsIgnoreCase(want);
            }
            case "step-time" -> {
                LineState state = lineId == null ? null : run.data.line(lineId);
                yield state != null && run.game.now() - state.stepSince >= condition.number("seconds", 0) * 1000.0;
            }
            case "not" -> !allHold(run, line, condition.children(), event);
            case "any" -> {
                for (Cond child : condition.children()) {
                    if (holds(run, line, child, event)) yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private boolean flagHolds(QuestlineData data, String lineId, Cond condition) {
        String flag = condition.string("flag");
        Object value = read(data, lineId, flag);
        String op = condition.string("op");
        Object want = condition.args().get("value");
        if (op == null && want == null) return truthy(value);
        if (op == null) op = "==";
        if (op.equals("is-set")) return value != null;
        FlagSpec spec = spec(lineId, flag);
        boolean bool = spec != null ? !spec.number() : (want instanceof Boolean || value instanceof Boolean);
        if (bool) {
            boolean have = truthy(value);
            boolean wanted = want == null || truthy(want);
            return switch (op) {
                case "==" -> have == wanted;
                case "!=" -> have != wanted;
                default -> false;
            };
        }
        double have = Questline.number(value, 0);
        double wanted = Questline.number(want, 0);
        return switch (op) {
            case "==" -> Math.abs(have - wanted) < 1.0E-9;
            case "!=" -> Math.abs(have - wanted) >= 1.0E-9;
            case ">=" -> have >= wanted;
            case "<=" -> have <= wanted;
            case ">" -> have > wanted;
            case "<" -> have < wanted;
            default -> false;
        };
    }

    static boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return Math.abs(number.doubleValue()) > 1.0E-9;
        return value != null && (value.toString().equalsIgnoreCase("true") || Questline.number(value, 0) != 0);
    }

    // ---- flags ------------------------------------------------------------------------------------------------------

    /** Where a flag lives for this line: the line's own map when the line declares it, else the shared map. */
    private Map<String, Object> store(QuestlineData data, String lineId, String flag) {
        if (flag == null) return null;
        if (lineId != null) {
            Questline line = lines.get(lineId);
            LineState state = data.line(lineId);
            if (line != null && line.flags().containsKey(flag)) return state == null ? null : state.flags;
        }
        return data.flags();
    }

    private FlagSpec spec(String lineId, String flag) {
        if (lineId != null) {
            Questline line = lines.get(lineId);
            if (line != null && line.flags().containsKey(flag)) return line.flags().get(flag);
        }
        return book.flags().get(flag);
    }

    /** A number flag is kept in its min..max and saved whole when it is whole; a yes/no flag as true / false. */
    static Object clamp(FlagSpec spec, Object value) {
        if (spec != null && !spec.number()) return truthy(value);
        if (spec == null && value instanceof Boolean) return value;
        double number = Questline.number(value, 0);
        if (spec != null) number = Math.max(spec.min(), Math.min(spec.max(), number));
        return number == Math.rint(number) && Math.abs(number) < 1.0E15 ? (Object) (long) number : (Object) number;
    }
}
