package net.tkgon.mc.iruuRPG.item;

import net.tkgon.mc.iruuRPG.combat.AttackType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ItemSkillRegistry {

    private static final String FOLDER_NAME = "iskill";

    private final JavaPlugin plugin;
    private final Map<ItemSkillType, ItemSkillDefinition> definitions = new EnumMap<>(ItemSkillType.class);

    public ItemSkillRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        definitions.clear();

        File folder = new File(plugin.getDataFolder(), FOLDER_NAME);
        ensureFolder(folder);
        ensureDefaultFiles();

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml") || name.toLowerCase().endsWith(".yaml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("iskill folder has no yml files: " + folder.getAbsolutePath());
            return;
        }

        for (File file : files) {
            loadFile(file);
        }
    }

    public ItemSkillDefinition definition(ItemSkillType type) {
        if (type == null || !type.enabled()) {
            return new ItemSkillDefinition(ItemSkillType.NONE, "", Map.of());
        }

        return definitions.getOrDefault(type, new ItemSkillDefinition(type, type.displayName(), Map.of()));
    }

    public ItemSkillVariant variant(ItemSkillType type, AttackType attackType) {
        return definition(type).variant(attackType);
    }

    private void loadFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = yaml.getString("id", fileNameWithoutExtension(file.getName()));
        ItemSkillType type = ItemSkillType.fromConfig(id);
        if (!type.enabled()) {
            plugin.getLogger().warning("iskill: unknown skill id '" + id + "' in " + file.getName());
            return;
        }

        ConfigurationSection root = yaml.getConfigurationSection(type.key());
        if (root == null) {
            root = yaml;
        }

        String displayName = root.getString("name", root.getString("display-name", type.displayName()));
        Map<AttackType, ItemSkillVariant> variants = new EnumMap<>(AttackType.class);
        for (AttackType attackType : List.of(AttackType.MELEE, AttackType.RANGE, AttackType.DEPLOY)) {
            ConfigurationSection section = root.getConfigurationSection(attackType.key());
            if (section == null) continue;

            variants.put(attackType, loadVariant(attackType, section));
        }

        definitions.put(type, new ItemSkillDefinition(type, displayName, variants));
    }

    private ItemSkillVariant loadVariant(AttackType attackType, ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        String trigger = section.getString("trigger", "");
        Double cost = readOptionalDouble(section, "cost", "mp-cost", "mana-cost");
        Integer cooldownTicks = readTicks(section, "cooldown", "cooldown-seconds", "cooldown-ticks", "cd", "cd-seconds", "cd-ticks");
        Integer durationTicks = readTicks(section, "duration", "duration-seconds", "duration-ticks", "effect-duration", "effect-duration-seconds", "effect-duration-ticks");
        List<String> description = readStringList(section, "description");
        List<String> lore = readStringList(section, "lore");
        Map<String, Double> values = readValues(section);

        return new ItemSkillVariant(attackType, enabled, trigger, cost, cooldownTicks, durationTicks, description, lore, values);
    }

    private Map<String, Double> readValues(ConfigurationSection section) {
        Map<String, Double> values = new HashMap<>();
        ConfigurationSection nested = section.getConfigurationSection("values");
        if (nested != null) {
            for (String key : nested.getKeys(false)) {
                if (!nested.isDouble(key) && !nested.isInt(key) && !nested.isLong(key)) continue;
                values.put(ItemSkillVariant.normalize(key), nested.getDouble(key));
            }
        }

        for (String key : section.getKeys(false)) {
            if (isReservedKey(key)) continue;
            if (!section.isDouble(key) && !section.isInt(key) && !section.isLong(key)) continue;

            values.put(ItemSkillVariant.normalize(key), section.getDouble(key));
        }
        return values;
    }

    private boolean isReservedKey(String key) {
        String normalized = ItemSkillVariant.normalize(key);
        return switch (normalized) {
            case "enabled", "trigger", "cost", "mp-cost", "mana-cost",
                    "cooldown", "cooldown-seconds", "cooldown-ticks", "cd", "cd-seconds", "cd-ticks",
                    "duration", "duration-seconds", "duration-ticks", "effect-duration",
                    "effect-duration-seconds", "effect-duration-ticks",
                    "description", "lore", "values" -> true;
            default -> false;
        };
    }

    private Double readOptionalDouble(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.isDouble(key) || section.isInt(key) || section.isLong(key)) {
                return section.getDouble(key);
            }
        }
        return null;
    }

    private Integer readTicks(ConfigurationSection section, String secondsKey, String secondsAlias, String ticksKey, String shortKey, String shortSecondsKey, String shortTicksKey) {
        Integer ticks = readTicksValue(section, ticksKey, shortTicksKey);
        if (ticks != null) return ticks;

        Double seconds = readOptionalDouble(section, secondsKey, secondsAlias, shortKey, shortSecondsKey);
        if (seconds == null) return null;
        return Math.max(0, (int) Math.round(seconds * 20.0));
    }

    private Integer readTicksValue(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.isInt(key) || section.isLong(key) || section.isDouble(key)) {
                return Math.max(0, (int) Math.round(section.getDouble(key)));
            }
        }
        return null;
    }

    private List<String> readStringList(ConfigurationSection section, String key) {
        if (section.isList(key)) {
            return section.getStringList(key);
        }
        if (section.isString(key)) {
            String value = section.getString(key);
            return value == null || value.isBlank() ? List.of() : List.of(value);
        }
        return List.of();
    }

    private void ensureFolder(File folder) {
        if (folder.exists()) return;
        if (!folder.mkdirs()) {
            plugin.getLogger().warning("Could not create iskill folder: " + folder.getAbsolutePath());
        }
    }

    private void ensureDefaultFiles() {
        for (ItemSkillType type : ItemSkillType.values()) {
            if (!type.enabled()) continue;

            String resource = FOLDER_NAME + "/" + type.key() + ".yml";
            File target = new File(plugin.getDataFolder(), resource);
            if (target.exists()) continue;

            try {
                plugin.saveResource(resource, false);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Missing bundled item skill resource: " + resource);
            }
        }
    }

    private String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
