package net.tkgon.mc.iruuRPG.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Loads quests/*.yml: the fixed main quests and the templates the daily sub quests are generated from. */
public final class QuestRegistry {

    /** A mob/item a sub quest can ask for. */
    public record Target(String id, String name, String unit) {
    }

    /** Pattern for generated sub quests. */
    public record SubTemplate(
            String id,
            QuestType type,
            String name,
            List<String> description,
            List<Target> targets,
            int minAmount,
            int maxAmount,
            double rewardPerUnit
    ) {
    }

    private final JavaPlugin plugin;
    private final Map<String, QuestDefinition> mainQuests = new LinkedHashMap<>();
    private final List<SubTemplate> subTemplates = new ArrayList<>();

    public QuestRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        mainQuests.clear();
        subTemplates.clear();

        File folder = new File(plugin.getDataFolder(), "quests");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create quests folder: " + folder.getAbsolutePath());
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.saveResource("quests/default.yml", false);
            files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        }
        if (files == null) return;

        for (File file : files) {
            load(YamlConfiguration.loadConfiguration(file), file.getName());
        }
    }

    public List<QuestDefinition> mainQuests() {
        return List.copyOf(mainQuests.values());
    }

    public List<SubTemplate> subTemplates() {
        return List.copyOf(subTemplates);
    }

    public Optional<QuestDefinition> findMain(String id) {
        return Optional.ofNullable(mainQuests.get(id));
    }

    private void load(YamlConfiguration yaml, String fileName) {
        ConfigurationSection quests = yaml.getConfigurationSection("quests");
        if (quests != null) {
            for (String id : quests.getKeys(false)) {
                ConfigurationSection section = quests.getConfigurationSection(id);
                if (section == null) continue;
                QuestDefinition definition = parse(id, section, fileName);
                if (definition != null) mainQuests.put(id, definition);
            }
        }
        for (Map<?, ?> raw : yaml.getMapList("sub-templates")) {
            SubTemplate template = parseTemplate(raw, fileName);
            if (template != null) subTemplates.add(template);
        }
    }

    private QuestDefinition parse(String id, ConfigurationSection section, String fileName) {
        QuestType type = type(section.getString("type", ""));
        if (type == null) {
            plugin.getLogger().warning("quests: unknown type in " + fileName + " / " + id);
            return null;
        }
        QuestCategory category = "sub".equalsIgnoreCase(section.getString("category", "main")) ? QuestCategory.SUB : QuestCategory.MAIN;
        Set<String> towns = new HashSet<>();
        for (String town : section.getStringList("towns")) towns.add(town.toLowerCase(Locale.ROOT));
        return new QuestDefinition(
                id,
                category,
                type,
                section.getString("name", id),
                section.getStringList("description"),
                section.getString("target", ""),
                section.getString("target-name", ""),
                section.getInt("amount", 1),
                section.getLong("reward", 0L),
                towns,
                section.getStringList("requires"),
                section.getStringList("explanation")
        );
    }

    private SubTemplate parseTemplate(Map<?, ?> raw, String fileName) {
        String id = String.valueOf(raw.get("id"));
        QuestType type = type(String.valueOf(raw.get("type")));
        if (type == null || type == QuestType.INFO) {
            plugin.getLogger().warning("quests: sub template '" + id + "' in " + fileName + " needs type kill or deliver");
            return null;
        }
        List<Target> targets = new ArrayList<>();
        if (raw.get("targets") instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) continue;
                targets.add(new Target(String.valueOf(map.get("id")), String.valueOf(map.get("name")),
                        map.get("unit") == null ? "" : String.valueOf(map.get("unit"))));
            }
        }
        if (targets.isEmpty()) return null;

        int min = 1;
        int max = 1;
        if (raw.get("amount") instanceof List<?> range && range.size() >= 2) {
            min = ((Number) range.get(0)).intValue();
            max = ((Number) range.get(1)).intValue();
        }
        List<String> description = new ArrayList<>();
        if (raw.get("description") instanceof List<?> lines) {
            for (Object line : lines) description.add(String.valueOf(line));
        }
        double perUnit = raw.get("reward-per-unit") instanceof Number number ? number.doubleValue() : 10.0;
        return new SubTemplate(id, type, String.valueOf(raw.get("name")), description, targets, Math.max(1, min), Math.max(min, max), perUnit);
    }

    private static QuestType type(String raw) {
        try {
            return QuestType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
