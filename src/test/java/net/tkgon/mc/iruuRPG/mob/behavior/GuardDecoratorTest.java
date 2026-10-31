package net.tkgon.mc.iruuRPG.mob.behavior;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** guard: raised while its node runs, lowered when the node ends or is stopped. */
class GuardDecoratorTest {

    private static final class Probe extends Node {
        Status answer = Status.RUNNING;

        Probe() {
            super("probe");
        }

        @Override
        Status tickNode(Ctx c) {
            return answer;
        }
    }

    /** A host that only records the guard (everything else answers a default). */
    private static BehaviorHost host(List<Object> guards) {
        return (BehaviorHost) Proxy.newProxyInstance(BehaviorHost.class.getClassLoader(), new Class<?>[]{BehaviorHost.class}, (proxy, method, args) -> {
            if (method.getName().equals("guard")) {
                guards.add(args[0] == null ? "down" : args[0]);
                return null;
            }
            Class<?> type = method.getReturnType();
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == double.class) return 0.0;
            return null;
        });
    }

    @Test
    void theGuardLastsAsLongAsTheNode() {
        List<Object> guards = new ArrayList<>();
        Ctx c = new Ctx(host(guards), new Blackboard(), Map.of());
        BehaviorHost.Guard spec = new BehaviorHost.Guard(0.6, 0.5, 0.1, 4);
        Probe walk = new Probe();
        walk.decorators.add(new Decorators.Guard(spec));
        assertEquals(Status.RUNNING, walk.run(c));
        assertEquals(List.of(spec), guards, "raised on entering");
        walk.run(c);
        assertEquals(1, guards.size(), "raised once");
        walk.answer = Status.SUCCESS;
        walk.run(c);
        assertEquals("down", guards.get(1), "lowered when it ends");

        walk.answer = Status.RUNNING;
        walk.run(c);
        walk.abort(c);
        assertEquals("down", guards.get(guards.size() - 1), "lowered when it is stopped");
    }

    @Test
    void theParserReadsIt() {
        List<String> errors = new ArrayList<>();
        Map<String, Object> raw = Map.of("tree", Map.of("type", "wait", "seconds", 1.0,
                "decorators", List.of(Map.of("type", "guard", "reduction", 0.6, "recoil", Map.of("power", 0.5, "lift", 0.1), "recoil-cooldown", 0.2))));
        Behavior behavior = BehaviorParser.parse("knight", raw, errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertTrue(behavior != null);
    }
}
