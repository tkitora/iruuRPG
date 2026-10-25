package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * The values a behavior reads and writes (Unreal's blackboard). The runner keeps these up to date each tick: {@code target} (the player), {@code has-target},
 * {@code distance} (to the target), {@code in-sight}, {@code hp} (0 - 1) and {@code state} (the state of the state machine); the rest is the mob's own ({@code blackboard:}
 * of the yaml, {@code set-blackboard}, and the places that {@code run-eqs} writes).
 */
final class Blackboard {

    private final Map<String, Object> values = new HashMap<>();
    Player target;

    Object get(String key) {
        return values.get(key);
    }

    void set(String key, Object value) {
        if (value == null) values.remove(key);
        else values.put(key, value);
    }

    boolean has(String key) {
        return values.containsKey(key);
    }

    double number(String key, double fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Boolean flag) return flag ? 1.0 : 0.0;
        return fallback;
    }

    boolean flag(String key) {
        Object value = values.get(key);
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.doubleValue() != 0.0;
        return value != null;
    }

    String text(String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    Location location(String key) {
        return values.get(key) instanceof Location location ? location : null;
    }

    Map<String, Object> all() {
        return values;
    }
}
