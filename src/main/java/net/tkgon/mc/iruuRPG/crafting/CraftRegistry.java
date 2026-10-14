package net.tkgon.mc.iruuRPG.crafting;

import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Loads crafting/*.yml (recipes). The bundled default file is created when the folder is empty. */
public final class CraftRegistry {

    private final JavaPlugin plugin;
    private final Map<String, CraftRecipe> recipes = new LinkedHashMap<>();

    public CraftRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        recipes.clear();

        File folder = new File(plugin.getDataFolder(), "crafting");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create crafting folder: " + folder.getAbsolutePath());
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.saveResource("crafting/recipes.yml", false);
            files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        }
        if (files == null) return;

        for (File file : files) {
            ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("recipes");
            if (section == null) continue;
            for (String id : section.getKeys(false)) {
                ConfigurationSection recipe = section.getConfigurationSection(id);
                if (recipe != null) load(id, recipe, file.getName());
            }
        }
    }

    public List<CraftRecipe> all() {
        return List.copyOf(recipes.values());
    }

    public Optional<CraftRecipe> find(String id) {
        return Optional.ofNullable(recipes.get(id));
    }

    private void load(String id, ConfigurationSection section, String fileName) {
        String result = section.getString("result", "");
        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredients");
        if (result.isBlank() || ingredientSection == null) {
            plugin.getLogger().warning("crafting: recipe '" + id + "' in " + fileName + " needs result and ingredients");
            return;
        }

        Map<String, Integer> ingredients = new LinkedHashMap<>();
        for (String itemId : ingredientSection.getKeys(false)) {
            ingredients.put(itemId, Math.max(1, ingredientSection.getInt(itemId, 1)));
        }

        Map<StatType, int[]> pool = new LinkedHashMap<>();
        ConfigurationSection poolSection = section.getConfigurationSection("bonus-pool");
        if (poolSection != null) {
            for (String key : poolSection.getKeys(false)) {
                var type = StatType.fromConfigKey(key);
                List<Integer> range = poolSection.getIntegerList(key);
                if (type.isEmpty() || range.size() < 2) {
                    plugin.getLogger().warning("crafting: bad bonus '" + key + "' in recipe " + id);
                    continue;
                }
                pool.put(type.get(), new int[]{Math.min(range.get(0), range.get(1)), Math.max(range.get(0), range.get(1))});
            }
        }
        recipes.put(id, new CraftRecipe(id, section.getString("name", id), result, ingredients, section.getInt("min-craft-level", 1), pool));
    }
}
