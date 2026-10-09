package net.tkgon.mc.iruuRPG.item;

import net.tkgon.mc.iruuRPG.combat.AttackType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record ItemSkillVariant(
        AttackType attackType,
        boolean enabled,
        String trigger,
        Double cost,
        Integer cooldownTicks,
        Integer durationTicks,
        List<String> description,
        List<String> lore,
        Map<String, Double> values
) {

    public ItemSkillVariant {
        attackType = attackType == null ? AttackType.SPECIAL : attackType;
        trigger = trigger == null ? "" : trigger;
        description = description == null ? List.of() : List.copyOf(description);
        lore = lore == null ? List.of() : List.copyOf(lore);
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static ItemSkillVariant disabled(AttackType attackType) {
        return new ItemSkillVariant(attackType, false, "", null, null, null, List.of(), List.of(), Map.of());
    }

    public double cost(double fallback) {
        return cost == null ? fallback : Math.max(0.0, cost);
    }

    public boolean hasCost() {
        return cost != null;
    }

    public int cooldownTicks(int fallback) {
        return cooldownTicks == null ? fallback : Math.max(0, cooldownTicks);
    }

    public boolean hasCooldown() {
        return cooldownTicks != null;
    }

    public int durationTicks(int fallback) {
        return durationTicks == null ? fallback : Math.max(0, durationTicks);
    }

    public boolean hasDuration() {
        return durationTicks != null;
    }

    public double value(String key, double fallback) {
        if (key == null || key.isBlank()) return fallback;

        return values.getOrDefault(normalize(key), fallback);
    }

    public int intValue(String key, int fallback) {
        return Math.max(0, (int) Math.round(value(key, fallback)));
    }

    public long longValue(String key, long fallback) {
        return Math.max(0L, Math.round(value(key, fallback)));
    }

    public boolean hasText() {
        return !trigger.isBlank() || !description.isEmpty() || !lore.isEmpty() || hasCost() || hasCooldown() || hasDuration();
    }

    public static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
