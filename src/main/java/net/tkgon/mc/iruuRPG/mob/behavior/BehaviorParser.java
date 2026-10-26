package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the behavior of a mob from the {@code behavior:} section of its yaml (see docs/mob-behavior.md). A tree is made again for every mob (the nodes keep their own state);
 * the section is checked once when the mobs are loaded, with the same code, and what is wrong is reported in {@code errors} (the node is left out or replaced by one that fails).
 */
final class BehaviorParser {

    private static final int MAX_DEPTH = 14;
    private static final int MAX_NODES = 400;

    private final List<String> errors;
    private final String mobId;
    private int nodes;

    private BehaviorParser(String mobId, List<String> errors) {
        this.mobId = mobId;
        this.errors = errors;
    }

    static Behavior parse(String mobId, Map<?, ?> map, List<String> errors) {
        return new BehaviorParser(mobId, errors).behavior(map);
    }

    private void error(String text) {
        errors.add("mob " + mobId + " behavior: " + text);
    }

    private Behavior behavior(Map<?, ?> map) {
        Map<String, EqsQuery> queries = new LinkedHashMap<>();
        if (map.get("eqs") instanceof Map<?, ?> eqsMap) {
            for (Map.Entry<?, ?> entry : eqsMap.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> queryMap)) continue;
                EqsQuery query = EqsQuery.parse(String.valueOf(entry.getKey()), queryMap, errors);
                if (query != null) queries.put(query.name, query);
            }
        }
        // the built-in query that `flee` uses: a place round the mob, far from the target, that it can walk to
        queries.computeIfAbsent("flee", name -> new EqsQuery(name, "ring", "self", 9.0, 16, 1, 2.0, List.of(
                new EqsQuery.Test("distance", "target", "far", 0.0, 2.0, 0.0, Double.MAX_VALUE, false, true),
                new EqsQuery.Test("pathable", "self", "near", 0.0, 1.0, 0.0, Double.MAX_VALUE, true, true)), false, 1));

        Map<String, Behavior.State> states = new LinkedHashMap<>();
        String initial;
        if (map.get("fsm") instanceof Map<?, ?> fsm && fsm.get("states") instanceof Map<?, ?> stateMap) {
            for (Map.Entry<?, ?> entry : stateMap.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if (!(entry.getValue() instanceof Map<?, ?> stateSpec)) continue;
                Node tree = stateSpec.get("tree") instanceof Map<?, ?> treeMap ? node(treeMap, 0) : new Tasks.Fixed("succeed", Status.SUCCESS);
                if (stateSpec.get("services") instanceof List<?> services) services(services, tree);
                if (stateSpec.get("decorators") instanceof List<?> decorators) decorators(decorators, tree);
                List<Behavior.Transition> transitions = new ArrayList<>();
                if (stateSpec.get("transitions") instanceof List<?> list) {
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> transition)) continue;
                        String to = Cfg.text(transition, "to", "");
                        List<Decorator> when = new ArrayList<>();
                        Object condition = transition.get("when");
                        if (condition instanceof Map<?, ?> single) addDecorator(single, when);
                        else if (condition instanceof List<?> many) for (Object one : many) if (one instanceof Map<?, ?> m) addDecorator(m, when);
                        transitions.add(new Behavior.Transition(to, when));
                    }
                }
                Node enter = stateSpec.get("enter") instanceof Map<?, ?> enterMap ? node(enterMap, 0) : null;
                states.put(name, new Behavior.State(name, tree, transitions, enter));
            }
            initial = Cfg.text(fsm, "initial", states.isEmpty() ? "root" : states.keySet().iterator().next());
            for (Behavior.State state : states.values()) {
                for (Behavior.Transition transition : state.transitions()) {
                    if (!states.containsKey(transition.to())) error("state " + state.name() + " goes to a state that does not exist: " + transition.to());
                }
            }
            if (!states.containsKey(initial)) {
                error("the first state '" + initial + "' does not exist");
                return null;
            }
        } else if (map.get("tree") instanceof Map<?, ?> treeMap) {
            states.put("root", new Behavior.State("root", node(treeMap, 0), List.of(), null));
            initial = "root";
        } else {
            error("it needs a `tree:` or an `fsm:`");
            return null;
        }
        Map<String, Object> blackboard = new LinkedHashMap<>();
        if (map.get("blackboard") instanceof Map<?, ?> initialValues) {
            for (Map.Entry<?, ?> entry : initialValues.entrySet()) blackboard.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        // (a query or a state that nothing uses is not an error; a task that names a query that does not exist is)
        for (Behavior.State state : states.values()) {
            checkQueries(state.tree(), queries);
            if (state.enter() != null) checkQueries(state.enter(), queries);
        }
        return new Behavior(Cfg.flag(map, "debug", false), Math.max(1, (int) Cfg.number(map, "tick-interval", 4.0)), Cfg.number(map, "aggro-range", Cfg.number(blackboard, "aggro-range", 20.0)),
                Cfg.flag(map, "auto-target", true), queries, states, initial, blackboard);
    }

    private void checkQueries(Node node, Map<String, EqsQuery> queries) {
        if (node instanceof Tasks.MoveTo moveTo && moveTo.queryName() != null && !queries.containsKey(moveTo.queryName())) {
            error("move-to uses a query that does not exist: " + moveTo.queryName());
        }
        for (Service service : node.services) {
            if (service instanceof Services.RunEqs run && !queries.containsKey(run.query())) error("run-eqs uses a query that does not exist: " + run.query());
        }
        if (node instanceof Composites.Composite composite) for (Node child : composite.children) checkQueries(child, queries);
    }

    private Node node(Map<?, ?> map, int depth) {
        if (depth > MAX_DEPTH || ++nodes > MAX_NODES) {
            error("the tree is too deep or too big (" + MAX_DEPTH + " levels, " + MAX_NODES + " nodes at most)");
            return new Tasks.Fixed("fail", Status.FAILURE);
        }
        String type = Cfg.lower(map, "type", "");
        Node node;
        switch (type) {
            case "selector", "sequence", "random", "parallel" -> {
                Composites.Composite composite = switch (type) {
                    case "selector" -> new Composites.Selector();
                    case "sequence" -> new Composites.Sequence();
                    case "random" -> new Composites.RandomSelector();
                    default -> new Composites.Parallel();
                };
                if (map.get("children") instanceof List<?> children) {
                    for (Object child : children) if (child instanceof Map<?, ?> m) composite.children.add(node(m, depth + 1));
                }
                if (composite.children.isEmpty()) error(type + " has no children");
                node = composite;
            }
            case "move-to", "move" -> {
                String mode = map.containsKey("eqs") ? "eqs" : map.containsKey("key") ? "key" : Cfg.lower(map, "target", "target");
                if (!List.of("target", "home", "key", "eqs").contains(mode)) {
                    error("move-to target must be target / home (" + mode + ")");
                    mode = "target";
                }
                node = new Tasks.MoveTo(mode, Cfg.text(map, "key", ""), Cfg.text(map, "eqs", ""), Cfg.number(map, "speed", 1.0), Cfg.number(map, "acceptance", 1.5),
                        Cfg.ticks(map, "timeout", 10.0));
            }
            case "flee" -> node = new Tasks.MoveTo("eqs", "", Cfg.text(map, "eqs", "flee"), Cfg.number(map, "speed", 1.3), Cfg.number(map, "acceptance", 1.5), Cfg.ticks(map, "timeout", 6.0));
            case "wait" -> node = new Tasks.Wait(Cfg.ticks(map, "seconds", 1.0), Cfg.ticks(map, "random-deviation", 0.0), Cfg.text(map, "key", ""));
            case "attack" -> node = new Tasks.Attack(Cfg.number(map, "rate", 1.0), Cfg.number(map, "range", -1.0));
            case "shoot" -> node = new Tasks.Shoot(Cfg.number(map, "rate", 1.0));
            case "use-skill", "skill" -> node = new Tasks.UseSkill((int) Cfg.number(map, "index", -1.0), Cfg.text(map, "skill", ""), Cfg.text(map, "animation", ""), Cfg.flag(map, "track", false));
            case "orbit", "circle" -> node = new Tasks.Orbit(Cfg.number(map, "radius", 0.0), Cfg.number(map, "speed", 0.9), Cfg.ticks(map, "seconds", 2.0), Cfg.ticks(map, "random-deviation", 0.0),
                    Cfg.lower(map, "direction", "random"));
            case "face-target", "face" -> node = new Tasks.FaceTarget();
            case "set-blackboard", "set" -> node = new Tasks.SetBlackboard(Cfg.text(map, "key", ""), map.get("value"));
            case "set-state" -> node = new Tasks.SetState(Cfg.text(map, "state", ""));
            case "clear-target" -> node = new Tasks.ClearTarget(Cfg.ticks(map, "seconds", 5.0));
            case "play-sound" -> {
                Sound sound;
                try {
                    sound = Sound.valueOf(Cfg.text(map, "sound", "ENTITY_ZOMBIE_AMBIENT").toUpperCase(Locale.ROOT).replace('.', '_'));
                } catch (IllegalArgumentException exception) {
                    error("play-sound: no such sound " + Cfg.text(map, "sound", ""));
                    sound = Sound.ENTITY_ZOMBIE_AMBIENT;
                }
                node = new Tasks.PlaySound(sound, (float) Cfg.number(map, "volume", 1.0), (float) Cfg.number(map, "pitch", 1.0));
            }
            case "play-animation", "animation" -> node = new Tasks.PlayAnimation(Cfg.text(map, "animation", "idle"), Cfg.flag(map, "wait", false));
            case "animation-attack", "attack-animation" -> {
                java.util.List<Tasks.AnimationAttack.Hit> hits = new ArrayList<>();
                if (map.get("hits") instanceof List<?> list) {
                    for (Object item : list) if (item instanceof Map<?, ?> hit) hits.add(hit(hit));
                } else {
                    hits.add(hit(map));
                }
                hits.sort((a, b) -> Integer.compare(a.at(), b.at()));
                node = new Tasks.AnimationAttack(Cfg.text(map, "animation", "attack"), hits, Cfg.ticks(map, "duration", 1.0), Cfg.flag(map, "face", true), Cfg.flag(map, "telegraph", false));
            }
            case "succeed" -> node = new Tasks.Fixed("succeed", Status.SUCCESS);
            case "fail" -> node = new Tasks.Fixed("fail", Status.FAILURE);
            default -> {
                error("unknown node type '" + type + "'");
                node = new Tasks.Fixed("fail", Status.FAILURE);
            }
        }
        if (map.get("name") != null) node.name = String.valueOf(map.get("name"));
        if (map.get("decorators") instanceof List<?> list) decorators(list, node);
        if (map.get("services") instanceof List<?> list) services(list, node);
        return node;
    }

    /** One hit of an animation attack: when ({@code at}, seconds), where (the shape and its size) and how hard. */
    private Tasks.AnimationAttack.Hit hit(Map<?, ?> map) {
        String shape = Cfg.lower(map, "shape", "circle");
        if (!List.of("circle", "cone", "line").contains(shape)) {
            error("animation-attack: shape must be circle / cone / line (" + shape + ")");
            shape = "circle";
        }
        Area area = new Area(shape, Cfg.number(map, "forward", shape.equals("circle") ? 1.5 : 0.0), Cfg.number(map, "radius", 3.0), Cfg.number(map, "angle", 90.0),
                Cfg.number(map, "width", 2.0), Cfg.number(map, "height", 3.0));
        return new Tasks.AnimationAttack.Hit(Cfg.ticks(map, "at", 0.5), area, Cfg.number(map, "rate", 1.0), Cfg.number(map, "knockback", 0.4));
    }

    private void decorators(List<?> list, Node node) {
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) addDecorator(map, node.decorators);
        }
    }

    private void addDecorator(Map<?, ?> map, List<Decorator> into) {
        String type = Cfg.lower(map, "type", "");
        Decorator decorator;
        switch (type) {
            case "blackboard", "bb" -> {
                String key = Cfg.text(map, "key", "");
                String op = Cfg.text(map, "op", "==");
                if (map.containsKey("is-set")) op = Cfg.flag(map, "is-set", true) ? "set" : "not-set";
                decorator = new Decorators.BlackboardCondition(key, op, map.get("value"), Cfg.text(map, "value-key", ""));
            }
            case "distance" -> decorator = new Decorators.Distance(Cfg.number(map, "min", 0.0), Cfg.number(map, "max", Double.MAX_VALUE), Cfg.text(map, "min-key", ""), Cfg.text(map, "max-key", ""));
            case "hp" -> decorator = new Decorators.Hp(Cfg.number(map, "below", 1.0), Cfg.number(map, "above", 0.0));
            case "line-of-sight", "sight" -> decorator = new Decorators.LineOfSight(Cfg.flag(map, "visible", true));
            case "chance" -> decorator = new Decorators.Chance(Math.max(0.0, Math.min(1.0, Cfg.number(map, "percent", 50.0) / 100.0)));
            case "cooldown" -> decorator = new Decorators.Cooldown(Cfg.ticks(map, "seconds", 5.0), Cfg.lower(map, "on", "any").equals("success"));
            case "allies" -> decorator = new Decorators.Allies(Cfg.number(map, "radius", 8.0), (int) Cfg.number(map, "min", 0.0), (int) Math.min(Integer.MAX_VALUE, Cfg.number(map, "max", Integer.MAX_VALUE)),
                    Cfg.flag(map, "same-type", true));
            case "slot" -> decorator = new Decorators.Slot(Cfg.text(map, "name", "attack"), (int) Cfg.number(map, "max", 2.0), Cfg.ticks(map, "hold", 3.0), Cfg.lower(map, "per", "target"));
            case "group-cooldown" -> decorator = new Decorators.GroupCooldown(Cfg.text(map, "key", "group"), Cfg.ticks(map, "seconds", 6.0), Cfg.lower(map, "scope", "target"));
            case "loop" -> decorator = new Decorators.Loop((int) Cfg.number(map, "count", 2.0), Cfg.flag(map, "infinite", false));
            case "time-limit" -> decorator = new Decorators.TimeLimit(Cfg.ticks(map, "seconds", 5.0));
            case "not", "inverter" -> decorator = new Decorators.Inverter();
            case "force-success" -> decorator = new Decorators.ForceSuccess();
            default -> {
                error("unknown decorator '" + type + "'");
                return;
            }
        }
        String abort = Cfg.lower(map, "abort", "none");
        decorator.abortSelf = abort.equals("self") || abort.equals("both");
        decorator.abortLower = abort.equals("lower") || abort.equals("both");
        into.add(decorator);
    }

    private void services(List<?> list, Node node) {
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String type = Cfg.lower(map, "type", "");
            switch (type) {
                case "update-target", "target" -> node.services.add(new Services.UpdateTarget(Cfg.number(map, "range", 20.0), Cfg.ticks(map, "interval", 0.5)));
                case "run-eqs", "eqs" -> node.services.add(new Services.RunEqs(Cfg.text(map, "query", ""), Cfg.text(map, "key", "point"), Cfg.ticks(map, "interval", 1.0)));
                default -> error("unknown service '" + type + "'");
            }
        }
    }
}
