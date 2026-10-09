package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.item.ItemSkillVariant;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ClassSkillRegistry {

    private final JavaPlugin plugin;
    private final Map<String, ClassSkillDefinition> definitions = new HashMap<>();

    public ClassSkillRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        definitions.clear();

        File folder = new File(plugin.getDataFolder(), "cskill");
        ensureFolder(folder, "cskill/warrior_strike.yml");

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml") || name.toLowerCase().endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            loadFile(file);
        }
    }

    public Optional<ClassSkillDefinition> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(definitions.get(id.trim().toLowerCase()));
    }

    private void loadFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = yaml.getString("id", fileNameWithoutExtension(file.getName())).trim();
        String name = yaml.getString("name", id);
        double cost = yaml.getDouble("cost", yaml.getDouble("mp-cost", 0.0));
        int cooldownTicks = cooldownTicks(yaml);
        List<String> description = yaml.isList("description")
                ? yaml.getStringList("description")
                : yaml.isString("description") ? List.of(yaml.getString("description", "")) : List.of();
        Map<String, Double> values = values(yaml.getConfigurationSection("values"));

        definitions.put(id.toLowerCase(), new ClassSkillDefinition(id, name, cost, cooldownTicks, description, values));
    }

    private int cooldownTicks(YamlConfiguration yaml) {
        if (yaml.isDouble("cooldown-ticks") || yaml.isInt("cooldown-ticks")) {
            return Math.max(0, (int) Math.round(yaml.getDouble("cooldown-ticks")));
        }
        return Math.max(0, (int) Math.round(yaml.getDouble("cooldown", yaml.getDouble("cooldown-seconds", 0.0)) * 20.0));
    }

    private Map<String, Double> values(ConfigurationSection section) {
        Map<String, Double> values = new HashMap<>();
        if (section == null) return values;

        for (String key : section.getKeys(false)) {
            if (!section.isDouble(key) && !section.isInt(key) && !section.isLong(key)) continue;
            values.put(ItemSkillVariant.normalize(key), section.getDouble(key));
        }
        return values;
    }

    private void ensureFolder(File folder, String defaultResource) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create cskill folder: " + folder.getAbsolutePath());
        }

        File defaultFile = new File(plugin.getDataFolder(), defaultResource);
        if (defaultFile.exists()) return;

        try {
            plugin.saveResource(defaultResource, false);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Missing bundled class skill resource: " + defaultResource);
        }
    }

    private String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
