package net.tkgon.mc.iruuRPG.mob.behavior;

import java.util.Map;
import java.util.Random;

/** What a node is ticked with: the mob (the host), the blackboard, the tick, a random source and the queries of environment (EQS). */
final class Ctx {

    final BehaviorHost host;
    final Blackboard bb;
    final Map<String, EqsQuery> queries;
    final Random random = new Random();
    int now;
    /** Where the queries leave what they found (for the debug view); may be null. */
    java.util.function.Consumer<EqsResult> eqsSink;
    /** A state change asked for by a task ({@code set-state}) or a transition: applied by the runner after the tick. */
    String requestedState;

    Ctx(BehaviorHost host, Blackboard bb, Map<String, EqsQuery> queries) {
        this.host = host;
        this.bb = bb;
        this.queries = queries;
    }
}
