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

    Behavior(boolean debug, int tickInterval, double aggroRange, boolean autoTarget, Map<String, EqsQuery> queries, Map<String, State> states, String initial,
             Map<String, Object> blackboard) {
        this.debug = debug;
        this.tickInterval = tickInterval;
        this.aggroRange = aggroRange;
        this.autoTarget = autoTarget;
        this.queries = queries;
        this.states = states;
        this.initial = initial;
        this.blackboard = blackboard;
    }
}
