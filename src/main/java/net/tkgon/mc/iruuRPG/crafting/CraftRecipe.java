package net.tkgon.mc.iruuRPG.crafting;

import net.tkgon.mc.iruuRPG.stat.StatType;

import java.util.Map;

/** One recipe: ingredients -> a result item, plus the pool of random stats it can roll. */
public record CraftRecipe(
        String id,
        String name,
        String resultItemId,
        Map<String, Integer> ingredients,
        int minCraftLevel,
        Map<StatType, int[]> bonusPool
) {

    public CraftRecipe {
        name = name == null || name.isBlank() ? id : name;
        ingredients = Map.copyOf(ingredients);
        minCraftLevel = Math.max(1, minCraftLevel);
        bonusPool = Map.copyOf(bonusPool);
    }
}
