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
    private int ticks;
    private int lastTargetSearch = -100;

    private BehaviorRunner(Behavior behavior, BehaviorHost host) {
        this.behavior = behavior;
        this.host = host;
        this.ctx = new Ctx(host, new Blackboard(), behavior.queries);
        for (Map.Entry<String, Object> entry : behavior.blackboard.entrySet()) ctx.bb.set(entry.getKey(), entry.getValue());
        this.state = behavior.states.get(behavior.initial);
        ctx.bb.set("state", state.name());
    }

    /** Checks a {@code behavior:} section as it is loaded: the problems found (empty when it is fine). */
    public static List<String> validate(String mobId, Map<?, ?> section) {
        List<String> errors = new ArrayList<>();
        BehaviorParser.parse(mobId, section, errors);
        return errors;
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

    public String stateName() {
        return state.name();
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
        host.stopMoving();
        state = next;
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
        if (target != null) {
            ctx.bb.set("distance", host.mob().getLocation().distance(target.getLocation()));
            ctx.bb.set("in-sight", host.canSee(target));
        } else {
            ctx.bb.set("distance", null);
            ctx.bb.set("in-sight", false);
        }
    }

    /** The lines shown above the mob when `debug: true`: the state, the nodes that are running and the main values. */
    public List<String> debugLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§7状態 §e" + state.name());
        List<String> path = new ArrayList<>();
        if (state.tree().active) state.tree().path(path);
        lines.add("§7BT §f" + (path.isEmpty() ? "(待機)" : String.join(" §7›§f ", path)));
        Player target = ctx.bb.target;
        String hp = String.format(java.util.Locale.ROOT, "%.0f%%", host.hpRatio() * 100.0);
        lines.add(target == null ? "§7目標 なし  HP " + hp
                : "§7目標 §f" + target.getName() + " §7距離 §f" + String.format(java.util.Locale.ROOT, "%.1f", ctx.bb.number("distance", 0.0)) + " §7HP §f" + hp
                + (ctx.bb.flag("in-sight") ? "" : " §c視線なし"));
        return lines;
    }
}
