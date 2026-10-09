package net.tkgon.mc.iruuRPG.classsystem;

import java.util.List;
import java.util.Map;

public record ClassSkillDefinition(
        String id,
        String name,
        double cost,
        int cooldownTicks,
        List<String> description,
        Map<String, Double> values
) {

    public ClassSkillDefinition {
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
        cost = Math.max(0.0, cost);
        cooldownTicks = Math.max(0, cooldownTicks);
        description = description == null ? List.of() : List.copyOf(description);
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
