package net.tkgon.mc.iruuRPG.quest;

import java.util.List;
import java.util.Set;

/** One quest on the board. Sub quests are generated daily from templates, so their ids contain the day. */
public record QuestDefinition(
        String id,
        QuestCategory category,
        QuestType type,
        String name,
        List<String> description,
        String target,
        String targetName,
        int amount,
        long reward,
        Set<String> towns,
        List<String> requires,
        List<String> explanation
) {

    public QuestDefinition {
        name = name == null || name.isBlank() ? id : name;
        description = description == null ? List.of() : List.copyOf(description);
        targetName = targetName == null || targetName.isBlank() ? target : targetName;
        amount = Math.max(1, amount);
        reward = Math.max(0L, reward);
        towns = towns == null ? Set.of() : Set.copyOf(towns);
        requires = requires == null ? List.of() : List.copyOf(requires);
        explanation = explanation == null ? List.of() : List.copyOf(explanation);
    }

    public boolean availableIn(String town) {
        return towns.isEmpty() || towns.contains(town);
    }
}
