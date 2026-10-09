package net.tkgon.mc.iruuRPG.classsystem;

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
        ensureFolder(folder, "classes/warrior.yml");

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
        StatSet growth = loadStatus(normalizedId + ".growth", section.getConfigurationSection("growth"));

        List<SpendNodeDefinition> spendNodes = new ArrayList<>();
        ConfigurationSection spendSection = section.getConfigurationSection("spend");
        if (spendSection != null) {
            for (String nodeId : spendSection.getKeys(false)) {
                ConfigurationSection node = spendSection.getConfigurationSection(nodeId);
                if (node == null) continue;
                SpendNodeDefinition spend = loadSpendNode(normalizedId, nodeId, node);
                if (spend != null) spendNodes.add(spend);
            }
        }

        List<ClassNodeDefinition> milestones = new ArrayList<>();
        List<Map<?, ?>> milestoneList = section.getMapList("milestones");
        for (int index = 0; index < milestoneList.size(); index++) {
            ConfigurationSection node = new MemoryConfiguration().createSection("m", milestoneList.get(index));
            milestones.add(loadMilestone("milestone_" + (index + 1), node));
        }

        definitions.put(normalizedId, new ClassDefinition(normalizedId, name, icon, growth, spendNodes, milestones));
    }

    private SpendNodeDefinition loadSpendNode(String classId, String id, ConfigurationSection section) {
        Optional<StatType> stat = StatType.fromConfigKey(section.getString("stat", id));
        if (stat.isEmpty()) {
            plugin.getLogger().warning("classes: unknown stat '" + section.getString("stat", id) + "' in " + classId + ".spend." + id);
            return null;
        }
        return new SpendNodeDefinition(
                id,
                section.getString("name", id),
                material(section.getString("icon"), Material.PAPER),
                stat.get(),
                section.getDouble("per-level", 0.0)
        );
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
                loadStatus(id, section.getConfigurationSection("status")),
                section.getString("name", ""),
                ClassHotKey.fromConfig(section.getString("hot_key", section.getString("hot-key"))),
                material(section.getString("icon"), fallback)
        );
    }

    private StatSet loadStatus(String nodeId, ConfigurationSection section) {
        StatSet stats = new StatSet();
        if (section == null) return stats;

        for (String key : section.getKeys(false)) {
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

    private void ensureFolder(File folder, String defaultResource) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create classes folder: " + folder.getAbsolutePath());
        }

        File defaultFile = new File(plugin.getDataFolder(), defaultResource);
        if (defaultFile.exists()) return;

        try {
            plugin.saveResource(defaultResource, false);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Missing bundled class resource: " + defaultResource);
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
