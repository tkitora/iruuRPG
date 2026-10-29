package net.tkgon.mc.iruuRPG.mob.behavior;

import java.util.List;
import java.util.Map;

/** The behavior of one mob: a state machine whose states each have a behavior tree, and the queries of the environment they use. A tree without a machine is one state. */
final class Behavior {

    record Transition(String to, List<Decorator> when) {
    }

    /** A state: its tree, the transitions out of it, and (may be null) what it does once each time it is entered, before its tree (a roar when a boss turns angry). */
    record State(String name, Node tree, List<Transition> transitions, Node enter) {
    }

    final boolean debug;
    final int tickInterval;
    final double aggroRange;
    final boolean autoTarget;
    final Map<String, EqsQuery> queries;
    final Map<String, State> states;
    final String initial;
    final Map<String, Object> blackboard;
    /** `navigation: fly`: the mob flies (see {@link FlyNavigator}); {@code flySpeed} is its pace in blocks a second. */
    final boolean fly;
    final double flySpeed;
    /** The room the mob moves in round its home (may be null: no limit; a flying mob always has one). */
    final Volume volume;
    /** The routes of `patrol`, by name. */
    final Map<String, Route> routes;

    /** A route of `patrol`: its points (from the home unless {@code absolute}), gone round ({@code loop}) or back and forth. */
    record Route(List<org.bukkit.util.Vector> points, boolean absolute, boolean loop) {
    }

    Behavior(boolean debug, int tickInterval, double aggroRange, boolean autoTarget, Map<String, EqsQuery> queries, Map<String, State> states, String initial,
             Map<String, Object> blackboard, boolean fly, double flySpeed, Volume volume, Map<String, Route> routes) {
        this.debug = debug;
        this.tickInterval = tickInterval;
        this.aggroRange = aggroRange;
        this.autoTarget = autoTarget;
        this.queries = queries;
        this.states = states;
        this.initial = initial;
        this.blackboard = blackboard;
        this.fly = fly;
        this.flySpeed = flySpeed;
        this.volume = volume;
        this.routes = routes;
    }
}
