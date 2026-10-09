package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ClassRegistry {

    private final JavaPlugin plugin;
    private final Map<String, ClassDefinition> definitions = new LinkedHashMap<>();

    public ClassRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        definitions.clear();

        File folder = new File(plugin.getDataFolder(), "classes");
        ensureFolder(folder, "classes/warrior.yml", "classes/mage.yml", "classes/gifted.yml", "classes/healer.yml", "classes/assassin.yml");

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml") || name.toLowerCase().endsWith(".yaml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("classes folder has no yml files: " + folder.getAbsolutePath());
            return;
        }

        for (File file : files) {
            loadFile(file);
        }
    }

    public Optional<ClassDefinition> first() {
        return definitions.values().stream().findFirst();
    }

    public Optional<ClassDefinition> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(definitions.get(normalize(id)));
    }

    public Map<String, ClassDefinition> all() {
        return Map.copyOf(definitions);
    }

    private void loadFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection classes = yaml.getConfigurationSection("classes");
        if (classes != null) {
            for (String id : classes.getKeys(false)) {
                ConfigurationSection section = classes.getConfigurationSection(id);
                if (section != null) {
                    loadClass(id, section);
                }
            }
            return;
        }

        String id = yaml.getString("id", fileNameWithoutExtension(file.getName()));
        loadClass(id, yaml);
    }

    private void loadClass(String id, ConfigurationSection section) {
        String normalizedId = normalize(id);
        String name = section.getString("name", id);
        Material icon = material(section.getString("icon"), Material.BOOK);
        ConfigurationSection growthSection = section.getConfigurationSection("growth");
        StatSet growth = loadStatus(normalizedId + ".growth", growthSection);
        ElementStatSet growthElements = loadElements(growthSection == null ? null : growthSection.getConfigurationSection("element-stats"));

        List<SpendNodeDefinition> spendNodes = new ArrayList<>();
        ConfigurationSection spendSection = section.getConfigurationSection("spend");
        if (spendSection != null) {
            for (String nodeId : spendSection.getKeys(false)) {
                ConfigurationSection node = spendSection.getConfigurationSection(nodeId);
                if (node == null) continue;
                ConfigurationSection perLevel = node.getConfigurationSection("per-level");
                spendNodes.add(new SpendNodeDefinition(
                        nodeId,
                        node.getString("name", nodeId),
                        material(node.getString("icon"), Material.PAPER),
                        node.getStringList("description"),
                        loadStatus(normalizedId + ".spend." + nodeId, perLevel),
                        loadElements(perLevel == null ? null : perLevel.getConfigurationSection("element-stats"))
                ));
            }
        }

        List<ClassNodeDefinition> milestones = new ArrayList<>();
        List<Map<?, ?>> milestoneList = section.getMapList("milestones");
        for (int index = 0; index < milestoneList.size(); index++) {
            ConfigurationSection node = new MemoryConfiguration().createSection("m", milestoneList.get(index));
            milestones.add(loadMilestone(normalizedId + ".milestone_" + (index + 1), node));
        }

        definitions.put(normalizedId, new ClassDefinition(normalizedId, name, icon, section.getStringList("description"), growth, growthElements, spendNodes, milestones));
    }

    private ClassNodeDefinition loadMilestone(String id, ConfigurationSection section) {
        ClassNodeType nodeType = ClassNodeType.fromConfig(section.getString("node_type", section.getString("node-type", "passive")));
        ClassPassiveEffectType effectType = ClassPassiveEffectType.fromConfig(section.getString("effect_type", section.getString("effect-type")));
        Material fallback = nodeType == ClassNodeType.ACTIVE ? Material.NETHER_STAR : Material.PAPER;

        return new ClassNodeDefinition(
                id,
                section.getString("node_name", section.getString("node-name", id)),
                nodeType,
                effectType,
                ClassConditionType.fromConfig(section.getString("condition")),
                loadStatus(id, section.getConfigurationSection("status")),
                loadElements(section.getConfigurationSection("element-stats")),
                section.getString("name", ""),
                ClassHotKey.fromConfig(section.getString("hot_key", section.getString("hot-key"))),
                material(section.getString("icon"), fallback),
                section.getStringList("description")
        );
    }

    private ElementStatSet loadElements(ConfigurationSection section) {
        ElementStatSet result = new ElementStatSet();
        if (section == null) return result;

        for (Element element : Element.values()) {
            String key = element.name().toLowerCase();
            ConfigurationSection damage = section.getConfigurationSection("damage");
            ConfigurationSection percent = section.getConfigurationSection("damage-percent");
            ConfigurationSection resist = section.getConfigurationSection("resist");
            if (damage != null) result.setDamage(element, damage.getDouble(key, 0.0));
            if (percent != null) result.setDamagePercent(element, percent.getDouble(key, 0.0));
            if (resist != null) result.setResist(element, resist.getDouble(key, 0.0));
        }
        return result;
    }

    private StatSet loadStatus(String nodeId, ConfigurationSection section) {
        StatSet stats = new StatSet();
        if (section == null) return stats;

        for (String key : section.getKeys(false)) {
            if (key.equals("element-stats")) continue;
            Optional<StatType> type = StatType.fromConfigKey(key);
            if (type.isEmpty()) {
                plugin.getLogger().warning("classes: unknown stat key '" + key + "' in node " + nodeId);
                continue;
            }
            stats.set(type.get(), section.getDouble(key, 0.0));
        }
        return stats;
    }

    private Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        Material material = Material.matchMaterial(raw.trim());
        return material == null ? fallback : material;
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
                plugin.getLogger().warning("Missing bundled class resource: " + defaultResource);
            }
        }
    }

    private String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase();
    }
}
