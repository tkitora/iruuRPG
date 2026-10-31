package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the behavior of one mob: a state machine (the states each have a behavior tree and the transitions to other states) over the blackboard that the runner keeps up to date.
 * Each {@link #tick}: the target is kept or looked for, the blackboard is refreshed, the transitions of the state are checked in order, then the tree of the state is ticked once.
 * The older behavior of the mobs is not touched: a mob has a runner only when its yaml has a {@code behavior:} section with {@code mode: bt}.
 */
public final class BehaviorRunner {

    private final Behavior behavior;
    private final BehaviorHost host;
    private final Ctx ctx;
    private Behavior.State state;
    /** The state's `enter` is running (it runs to its end once, before the state's tree and its transitions). */
    private boolean entering;
    private int ticks;
    private final Map<String, EqsResult> eqsResults = new java.util.LinkedHashMap<>();
    private int lastTargetSearch = -100;

    private BehaviorRunner(Behavior behavior, BehaviorHost host) {
        this.behavior = behavior;
        this.host = host;
        this.ctx = new Ctx(host, new Blackboard(), behavior.queries);
        this.ctx.eqsSink = result -> eqsResults.put(result.query(), result);
        this.ctx.volume = behavior.volume;
        this.ctx.routes = behavior.routes;
        for (Map.Entry<String, Object> entry : behavior.blackboard.entrySet()) ctx.bb.set(entry.getKey(), entry.getValue());
        this.state = behavior.states.get(behavior.initial);
        this.entering = state.enter() != null;
        ctx.bb.set("state", state.name());
    }

    /** Checks a {@code behavior:} section as it is loaded: the problems found (empty when it is fine). */
    public static List<String> validate(String mobId, Map<?, ?> section) {
        List<String> errors = new ArrayList<>();
        BehaviorParser.parse(mobId, section, errors);
        return errors;
    }

    /** Whether the section can be made into a behavior at all (it has a tree or a state machine, and the first state exists); the problems go to {@code errors}. */
    public static boolean usable(String id, Map<?, ?> section, List<String> errors) {
        return BehaviorParser.parse(id, section, errors) != null;
    }

    /** Whether the section asks for the tree (`mode: bt`, or no mode at all); `mode: legacy` keeps the older behavior. */
    public static boolean wanted(Map<?, ?> section) {
        return section != null && !section.isEmpty() && !Cfg.lower(section, "mode", "bt").equals("legacy");
    }

    /** A runner for the mob of {@code host}, or null when the section cannot be used (the problems go to {@code errors}). */
    public static BehaviorRunner create(String mobId, Map<?, ?> section, BehaviorHost host, List<String> errors) {
        Behavior behavior = BehaviorParser.parse(mobId, section, errors);
        return behavior == null ? null : new BehaviorRunner(behavior, host);
    }

    public int interval() {
        return behavior.tickInterval;
    }

    public boolean debug() {
        return behavior.debug;
    }

    /** Whether the mob flies from the start (`navigation: fly`). */
    public boolean flying() {
        return behavior.fly;
    }

    /** The pace of the mob walking at speed 1, blocks a second; 0 leaves the one of its kind. */
    public double walkSpeed() {
        return behavior.walkSpeed;
    }

    /** The pace of a flying mob at speed 1, blocks a second. */
    public double flySpeed() {
        return behavior.flySpeed;
    }

    /** The room the mob moves in round its home, or null (no limit; never for a mob that flies, from the start or by `set-navigation`). */
    public Volume volume() {
        return behavior.volume;
    }

    public String stateName() {
        return state.name();
    }

    /** A number of the blackboard (the memory of the mob), or {@code fallback} when it is not set. Other systems read it, e.g. {@code phase} for the music of a boss fight. */
    public double blackboardNumber(String key, double fallback) {
        return ctx.bb.number(key, fallback);
    }

    public boolean hasBlackboard(String key) {
        return ctx.bb.has(key);
    }

    /** The player the mob has as its target now, or null. */
    public Player target() {
        return ctx.bb.target;
    }

    /** One step of the behavior; call it every {@link #interval()} ticks. */
    public void tick() {
        ctx.now = host.tick();
        ticks++;
        refresh();
        Behavior.State current = state;
        if (entering) {
            // what the state does once when it is entered: to its end, before anything else of the state (a `set-state` in it still moves on)
            if (current.enter().run(ctx) != Status.RUNNING) entering = false;
            if (ctx.requestedState != null) {
                String next = ctx.requestedState;
                ctx.requestedState = null;
                Behavior.State target = behavior.states.get(next);
                if (target != null && target != state) change(target);
            }
            return;
        }
        for (Behavior.Transition transition : current.transitions()) {
            boolean holds = true;
            for (Decorator decorator : transition.when()) if (!decorator.canEnter(ctx, current.tree())) holds = false;
            if (holds && behavior.states.containsKey(transition.to()) && !transition.to().equals(current.name())) {
                ctx.requestedState = transition.to();
                break;
            }
        }
        if (ctx.requestedState == null) current.tree().run(ctx);
        if (ctx.requestedState != null) {
            String next = ctx.requestedState;
            ctx.requestedState = null;
            Behavior.State target = behavior.states.get(next);
            if (target != null && target != state) change(target);
        }
    }

    private void change(Behavior.State next) {
        state.tree().abort(ctx);
        if (state.enter() != null) state.enter().abort(ctx);
        host.stopMoving();
        state = next;
        entering = next.enter() != null;
        ctx.bb.set("state", next.name());
    }

    /** Keeps the target and the values the trees read. */
    private void refresh() {
        Player target = ctx.bb.target;
        double aggro = behavior.aggroRange;
        if (target != null && !host.valid(target, aggro * 1.6 + 4.0)) target = null;
        if (target == null && behavior.autoTarget && ctx.now >= (int) ctx.bb.number("no-target-until", 0) && ctx.now - lastTargetSearch >= 10) {
            lastTargetSearch = ctx.now;
            target = host.findTarget(aggro);
        }
        ctx.bb.target = target;
        ctx.bb.set("has-target", target != null);
        ctx.bb.set("hp", host.hpRatio());
        ctx.bb.set("ranged-hits", host.rangedHits());
        org.bukkit.Location home = host.home();
        org.bukkit.Location here = host.mob().getLocation();
        ctx.bb.set("home-distance", home == null || home.getWorld() != here.getWorld() ? 9999.0 : home.distance(here));
        ctx.bb.set("in-volume", behavior.volume == null || behavior.volume.contains(home, here));
        // (only a flying mob needs its height above the ground: the blocks under a walking one are not looked at)
        boolean flying = host.flying();
        ctx.bb.set("flying", flying);
        if (flying) ctx.bb.set("altitude", FlyNavigator.heightAboveGround(here, 32));
        // the group: how many of the same kind are near, and how many hold an attack slot against the target
        ctx.bb.set("allies-near", host.alliesNear(ctx.bb.number("ally-range", 8.0), true));
        ctx.bb.set("attackers", target == null ? 0 : host.coordinator().attackers(target.getUniqueId().toString(), ctx.now));
        if (target != null) {
            ctx.bb.set("distance", host.mob().getLocation().distance(target.getLocation()));
            ctx.bb.set("in-sight", host.canSee(target));
        } else {
            ctx.bb.set("distance", null);
            ctx.bb.set("in-sight", false);
        }
    }

    /** What each query of the mob found the last time it ran, newest data only (the debug view draws it). */
    public List<EqsResult> eqsResults() {
        return new ArrayList<>(eqsResults.values());
    }

    /** The lines shown above the mob when `debug: true`: the state, the nodes that are running and the main values. */
    public List<String> debugLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§7状態 §e" + state.name());
        List<String> path = new ArrayList<>();
        if (entering && state.enter() != null) {
            path.add("enter");
            if (state.enter().active) state.enter().path(path);
        } else if (state.tree().active) {
            state.tree().path(path);
        }
        lines.add("§7BT §f" + (path.isEmpty() ? "(待機)" : String.join(" §7›§f ", path)));
        Player target = ctx.bb.target;
        String hp = String.format(java.util.Locale.ROOT, "%.0f%%", host.hpRatio() * 100.0);
        lines.add(target == null ? "§7目標 なし  HP " + hp
                : "§7目標 §f" + target.getName() + " §7距離 §f" + String.format(java.util.Locale.ROOT, "%.1f", ctx.bb.number("distance", 0.0)) + " §7HP §f" + hp
                + (ctx.bb.flag("in-sight") ? "" : " §c視線なし"));
        for (EqsResult result : eqsResults.values()) {
            if (ctx.now - result.tick() > 100) continue;
            int kept = 0;
            for (EqsResult.Candidate candidate : result.candidates()) if (candidate.kept()) kept++;
            lines.add("§7EQS §b" + result.query() + " §7" + kept + "/" + result.candidates().size() + " §7→ "
                    + (result.chosen() == null ? "§cなし" : String.format(java.util.Locale.ROOT, "§f%.0f, %.0f, %.0f", result.chosen().getX(), result.chosen().getY(), result.chosen().getZ())));
        }
        return lines;
    }
}
