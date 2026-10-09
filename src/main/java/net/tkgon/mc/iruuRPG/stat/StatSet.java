package net.tkgon.mc.iruuRPG.stat;

import java.util.EnumMap;
import java.util.Map;

public final class StatSet {

    private final EnumMap<StatType, Double> values = new EnumMap<>(StatType.class);

    public double get(StatType type) {
        return values.getOrDefault(type, 0.0);
    }

    public void set(StatType type, double value) {
        if (Math.abs(value) < 1.0E-9) {
            values.remove(type);
        } else {
            values.put(type, value);
        }
    }

    public void add(StatType type, double value) {
        set(type, get(type) + value);
    }

    public void addAll(StatSet other) {
        other.values.forEach(this::add);
    }

    public void replaceWith(StatSet other) {
        values.clear();
        values.putAll(other.values);
    }

    public void clear() {
        values.clear();
    }

    /** Returns a copy with every value multiplied by {@code factor}. */
    public StatSet scaled(double factor) {
        StatSet result = new StatSet();
        for (Map.Entry<StatType, Double> entry : asMap().entrySet()) {
            result.set(entry.getKey(), entry.getValue() * factor);
        }
        return result;
    }

    public StatSet copy() {
        StatSet copy = new StatSet();
        copy.values.putAll(values);
        return copy;
    }

    public Map<StatType, Double> asMap() {
        return Map.copyOf(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
