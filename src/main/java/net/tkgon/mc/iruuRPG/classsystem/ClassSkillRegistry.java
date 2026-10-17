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
        ensureFolder(folder, "cskill/warrior_strike.yml", "cskill/ice_lance.yml", "cskill/instinct_release.yml", "cskill/sanctuary.yml", "cskill/satsujin.yml");

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml") || name.toLowerCase().endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            loadFile(file);
        }
    }

    private net.tkgon.mc.iruuRPG.skill.SkillRegistry generalSkills;

    /** Skills made from templates (skills/*.yml) can be class active skills too: they answer to find() as well. */
    public void setGeneralSkills(net.tkgon.mc.iruuRPG.skill.SkillRegistry generalSkills) {
        this.generalSkills = generalSkills;
    }

    public Optional<ClassSkillDefinition> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        ClassSkillDefinition own = definitions.get(id.trim().toLowerCase());
        if (own != null) return Optional.of(own);
        return generalDefinition(id.trim().toLowerCase());
    }

    /** A skills/*.yml skill shown as a class skill: its MP, cooldown and description. */
    private Optional<ClassSkillDefinition> generalDefinition(String id) {
        if (generalSkills == null) return Optional.empty();
        net.tkgon.mc.iruuRPG.skill.SkillSpec spec = generalSkills.all().get(id);
        if (spec == null) return Optional.empty();
        List<String> description = spec.description().isEmpty()
                ? List.of("スキル: " + spec.template().id()) : spec.description();
        return Optional.of(new ClassSkillDefinition(spec.id(), spec.name(), spec.cost(), spec.cooldownTicks(), description, Map.of()));
    }

    /** True for a skill of skills/*.yml (not one of the built-in cskill files). */
    public boolean isGeneral(String id) {
        return id != null && definitions.get(id.trim().toLowerCase()) == null
                && generalSkills != null && generalSkills.all().containsKey(id.trim().toLowerCase());
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

    private void ensureFolder(File folder, String... defaultResources) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create folder: " + folder.getAbsolutePath());
        }

        for (String defaultResource : defaultResources) {
            File defaultFile = new File(plugin.getDataFolder(), defaultResource);
            if (defaultFile.exists()) continue;

            try {
                plugin.saveResource(defaultResource, false);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Missing bundled class skill resource: " + defaultResource);
            }
        }
    }

    private String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
