package net.tkgon.mc.iruuRPG.crafting;

import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementKind;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
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

    /** Reloads every recipe; if loading throws, the previous recipes stay (never half empty). */
    public void reload() {
        Map<String, CraftRecipe> previous = new LinkedHashMap<>(recipes);
        try {
            load();
        } catch (RuntimeException e) {
            recipes.clear();
            recipes.putAll(previous);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "crafting: reload failed, keeping the previous recipes", e);
        }
    }

    private void load() {
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
        // a fixed order (by name), so a recipe id found in two files always ends up the same
        java.util.Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));

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

        // the stats a craft can roll; how high they can go depends on the craft level (config crafting:)
        List<BonusOption> pool = new ArrayList<>();
        List<String> poolKeys = section.getStringList("bonus-pool");
        if (poolKeys.isEmpty() && section.getConfigurationSection("bonus-pool") != null) {
            poolKeys = new ArrayList<>(section.getConfigurationSection("bonus-pool").getKeys(false)); // old format
        }
        for (String key : poolKeys) {
            List<BonusOption> options = parseOptions(key);
            if (options.isEmpty()) {
                plugin.getLogger().warning("crafting: unknown bonus stat '" + key + "' in recipe " + id);
                continue;
            }
            pool.addAll(options);
        }
        recipes.put(id, new CraftRecipe(id, section.getString("name", id), result, ingredients, section.getInt("min-craft-level", 1), pool));
    }

    /**
     * A pool entry: a stat key ("strength"), one element stat ("red-damage", "blue-damage-percent", "green-resist"),
     * or all five colors of one kind ("all-element-damage", "all-element-damage-percent", "all-element-resist").
     */
    static List<BonusOption> parseOptions(String raw) {
        String key = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        List<BonusOption> options = new ArrayList<>();
        var stat = StatType.fromConfigKey(key);
        if (stat.isPresent()) {
            options.add(BonusOption.ofStat(stat.get()));
            return options;
        }
        for (ElementKind kind : ElementKind.values()) {
            if (key.equals("all-element-" + kind.key())) {
                for (Element element : Element.values()) options.add(BonusOption.ofElement(kind, element));
                return options;
            }
            for (Element element : Element.values()) {
                if (key.equals(element.key() + "-" + kind.key())) options.add(BonusOption.ofElement(kind, element));
            }
        }
        return options;
    }
}
