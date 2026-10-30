package net.tkgon.mc.iruuRPG.mob.behavior;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** force-success: the node answers success also when its conditions keep it out or stop it. */
class ForceSuccessTest {

    /** A node that counts its runs and answers what it is told. */
    private static final class Probe extends Node {
        Status answer = Status.SUCCESS;
        int runs;

        Probe() {
            super("probe");
        }

        @Override
        Status tickNode(Ctx c) {
            runs++;
            return answer;
        }
    }

    private static Ctx ctx() {
        return new Ctx(null, new Blackboard(), Map.of());
    }

    @Test
    void aNodeItCannotEnterStillSucceeds() {
        Ctx c = ctx();
        Probe plain = new Probe();
        plain.decorators.add(new Decorators.BlackboardCondition("ready", "set", null));
        assertEquals(Status.FAILURE, plain.run(c));

        Probe forced = new Probe();
        forced.decorators.add(new Decorators.BlackboardCondition("ready", "set", null));
        forced.decorators.add(new Decorators.ForceSuccess());
        assertEquals(Status.SUCCESS, forced.run(c));
        assertEquals(0, forced.runs, "the node itself does not run");

        c.bb.set("ready", true);
        forced.answer = Status.FAILURE;
        assertEquals(Status.SUCCESS, forced.run(c));
        assertEquals(1, forced.runs);
    }

    @Test
    void aNodeStoppedByItsConditionOrTimeStillSucceeds() {
        Ctx c = ctx();
        c.bb.set("ready", true);
        Probe forced = new Probe();
        forced.answer = Status.RUNNING;
        Decorator condition = new Decorators.BlackboardCondition("ready", "set", null);
        condition.abortSelf = true;
        forced.decorators.add(condition);
        forced.decorators.add(new Decorators.ForceSuccess());
        assertEquals(Status.RUNNING, forced.run(c));
        c.bb.set("ready", false);
        assertEquals(Status.SUCCESS, forced.run(c));

        Probe timed = new Probe();
        timed.answer = Status.RUNNING;
        timed.decorators.add(new Decorators.TimeLimit(10));
        timed.decorators.add(new Decorators.ForceSuccess());
        assertEquals(Status.RUNNING, timed.run(c));
        c.now = 20;
        assertEquals(Status.SUCCESS, timed.run(c));
    }
}
