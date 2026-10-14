package net.tkgon.mc.iruuRPG.crafting;

import java.util.List;
import java.util.Map;

/** One recipe: ingredients -> a result item, plus the pool of random stats it can roll. */
public record CraftRecipe(
        String id,
        String name,
        String resultItemId,
        Map<String, Integer> ingredients,
        int minCraftLevel,
        List<BonusOption> bonusPool
) {

    public CraftRecipe {
        name = name == null || name.isBlank() ? id : name;
        ingredients = Map.copyOf(ingredients);
        minCraftLevel = Math.max(1, minCraftLevel);
        bonusPool = List.copyOf(bonusPool);
    }
}
