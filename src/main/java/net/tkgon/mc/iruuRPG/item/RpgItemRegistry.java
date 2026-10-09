package net.tkgon.mc.iruuRPG.item;

import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.combat.DamageKind;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RpgItemRegistry {

    private final JavaPlugin plugin;
    private final Map<String, RpgItemDefinition> definitions = new HashMap<>();

    public RpgItemRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        definitions.clear();

        File folder = new File(plugin.getDataFolder(), "items");
        ensureFolder(folder, "items/default.yml");

        File[] files = yamlFiles(folder);
        if (files.length == 0) {
            plugin.getLogger().warning("items folder has no yml files: " + folder.getAbsolutePath());
            return;
        }

        for (File file : files) {
            loadFile(file);
        }

        plugin.getLogger().info("Loaded " + definitions.size() + " RPG item definitions from " + files.length + " files.");
    }

    private void loadFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) {
            loadItemsSection(file, items);
            return;
        }

        if (yaml.contains("material")) {
            loadDefinition(idFromFile(file), yaml).ifPresent(definition -> definitions.put(definition.id(), definition));
            return;
        }

        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) continue;
            loadDefinition(id, section).ifPresent(definition -> definitions.put(id, definition));
        }
    }

    private void loadItemsSection(File file, ConfigurationSection items) {
        for (String id : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(id);
            if (section == null) continue;

            loadDefinition(id, section).ifPresent(definition -> definitions.put(id, definition));
        }
    }

    public Optional<RpgItemDefinition> find(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Map<String, RpgItemDefinition> all() {
        return Collections.unmodifiableMap(definitions);
    }

    private Optional<RpgItemDefinition> loadDefinition(String id, ConfigurationSection section) {
        MaterialSpec materialSpec = materialSpec(id, section);
        if (materialSpec == null) {
            return Optional.empty();
        }

        String name = section.getString("name", id);
        int requiredLevel = section.getInt("required-level", section.getInt("level", 0));
        Rarity rarity = Rarity.fromConfig(section.getString("rarity"), Rarity.COMMON);
        Element element = Element.fromConfig(section.getString("element", section.getString("colorType")), Element.WHITE);
        DamageKind damageKind = DamageKind.fromConfig(section.getString("damage-kind", section.getString("damageType")), DamageKind.NONE);
        AttackType attackType = AttackType.fromConfig(section.getString("attack-type", section.getString("type")), AttackType.SPECIAL);
        ItemSkillType skill = ItemSkillType.fromConfig(section.getString("skill"));
        List<String> description = section.getStringList("description");

        StatSet stats = loadStats(id, section.getConfigurationSection("stats"));
        ElementStatSet elementStats = loadElementStats(section);

        return Optional.of(new RpgItemDefinition(
                id,
                materialSpec.material(),
                materialSpec.visualOptions(),
                name,
                requiredLevel,
                rarity,
                element,
                damageKind,
                attackType,
                skill,
                stats,
                elementStats,
                description
        ));
    }

    private MaterialSpec materialSpec(String id, ConfigurationSection section) {
        String raw = section.getString("material", "").trim();
        if (raw.isBlank()) {
            plugin.getLogger().warning("items: missing material for item " + id);
            return null;
        }

        String[] parts = raw.split(":");
        String materialName = parts[0].trim().toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("items: unknown material '" + materialName + "' for item " + id);
            return null;
        }

        boolean glint = section.getBoolean("glint", false);
        String skinTexture = firstString(section, "skin", "head-skin", "headSkin", "texture");
        Color leatherColor = parseColor(firstString(section, "color", "leather-color", "leatherColor", "rgb", "dye-color"));

        for (int i = 1; i < parts.length; i++) {
            String token = parts[i].trim();
            if (token.isBlank()) continue;

            if (token.equalsIgnoreCase("glint")) {
                glint = true;
                continue;
            }

            if (token.equalsIgnoreCase("skin") && i + 1 < parts.length) {
                skinTexture = parts[++i].trim();
                continue;
            }

            if ((token.equalsIgnoreCase("color") || token.equalsIgnoreCase("leather-color")) && i + 1 < parts.length) {
                leatherColor = parseColor(parts[++i].trim());
                continue;
            }

            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.startsWith("skin=")) {
                skinTexture = token.substring("skin=".length()).trim();
            } else if (lower.startsWith("color=")) {
                leatherColor = parseColor(token.substring("color=".length()).trim());
            } else if (lower.startsWith("leather-color=")) {
                leatherColor = parseColor(token.substring("leather-color=".length()).trim());
            } else if (lower.startsWith("rgb=")) {
                leatherColor = parseColor(token.substring("rgb=".length()).trim());
            }
        }

        return new MaterialSpec(material, new ItemVisualOptions(glint, skinTexture, leatherColor));
    }

    private Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String value = raw.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }

        try {
            if (value.startsWith("#")) {
                int rgb = Integer.parseInt(value.substring(1), 16);
                return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
            if (value.toLowerCase(Locale.ROOT).startsWith("0x")) {
                int rgb = Integer.parseInt(value.substring(2), 16);
                return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }

            String[] parts = value.split("[,; ]+");
            if (parts.length == 3) {
                return Color.fromRGB(clampColor(Integer.parseInt(parts[0])), clampColor(Integer.parseInt(parts[1])), clampColor(Integer.parseInt(parts[2])));
            }
        } catch (NumberFormatException ignored) {
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "red" -> Color.fromRGB(255, 55, 45);
            case "blue" -> Color.fromRGB(70, 130, 255);
            case "white" -> Color.fromRGB(245, 245, 245);
            case "green" -> Color.fromRGB(45, 220, 95);
            case "orange" -> Color.fromRGB(255, 155, 35);
            case "black" -> Color.fromRGB(25, 25, 25);
            case "yellow" -> Color.fromRGB(255, 230, 55);
            case "purple" -> Color.fromRGB(165, 80, 255);
            case "pink" -> Color.fromRGB(255, 120, 190);
            case "aqua", "cyan" -> Color.fromRGB(80, 220, 255);
            case "gray", "grey" -> Color.fromRGB(150, 150, 150);
            default -> null;
        };
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private String firstString(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private StatSet loadStats(String itemId, ConfigurationSection section) {
        StatSet stats = new StatSet();
        if (section == null) return stats;

        for (String key : section.getKeys(false)) {
            Optional<StatType> type = StatType.fromConfigKey(key);
            if (type.isEmpty()) {
                if (!isElementStatSection(key)) {
                    plugin.getLogger().warning("items: unknown stat key '" + key + "' in item " + itemId);
                }
                continue;
            }

            stats.set(type.get(), section.getDouble(key, 0.0));
        }

        return stats;
    }

    private ElementStatSet loadElementStats(ConfigurationSection itemSection) {
        ElementStatSet stats = new ElementStatSet();

        ConfigurationSection elementStats = itemSection.getConfigurationSection("element-stats");
        if (elementStats != null) {
            readElementMap(elementStats.getConfigurationSection("damage"), stats::setDamage);
            readElementMap(elementStats.getConfigurationSection("damage-percent"), stats::setDamagePercent);
            readElementMap(elementStats.getConfigurationSection("resist"), stats::setResist);
        }

        ConfigurationSection legacyStats = itemSection.getConfigurationSection("stats");
        if (legacyStats != null) {
            readLegacyElementMap(legacyStats.getConfigurationSection("colorDmg"), stats::setDamage);
            readLegacyElementMap(legacyStats.getConfigurationSection("colorDmgS"), stats::setDamagePercent);
            readLegacyElementMap(legacyStats.getConfigurationSection("colorDef"), stats::setResist);
        }

        return stats;
    }

    private void readElementMap(ConfigurationSection section, ElementValueConsumer consumer) {
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            Element element = Element.fromConfig(key, null);
            if (element == null) {
                plugin.getLogger().warning("items: unknown element '" + key + "'");
                continue;
            }

            consumer.accept(element, section.getDouble(key, 0.0));
        }
    }

    private void readLegacyElementMap(ConfigurationSection section, ElementValueConsumer consumer) {
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                consumer.accept(Element.fromLegacyIndex(Integer.parseInt(key)), section.getDouble(key, 0.0));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("items: unknown legacy element index '" + key + "'");
            }
        }
    }

    private void ensureFolder(File folder, String defaultResource) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create items folder: " + folder.getAbsolutePath());
            return;
        }

        if (yamlFiles(folder).length == 0) {
            File defaultFile = new File(folder, "default.yml");
            if (!defaultFile.exists()) {
                plugin.saveResource(defaultResource, false);
            }
        }

        // The starter weapons given by the tutorial must exist even on servers that already have item files.
        if (!new File(folder, "starter.yml").exists()) {
            try {
                plugin.saveResource("items/starter.yml", false);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Missing bundled starter items resource.");
            }
        }
    }

    private File[] yamlFiles(File folder) {
        File[] files = folder.listFiles(file -> file.isFile()
                && (file.getName().toLowerCase(Locale.ROOT).endsWith(".yml")
                || file.getName().toLowerCase(Locale.ROOT).endsWith(".yaml")));
        if (files == null) return new File[0];

        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return files;
    }

    private String idFromFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private boolean isElementStatSection(String key) {
        return key.equals("colorDmg")
                || key.equals("colorDmgS")
                || key.equals("colorDef")
                || key.equals("element-stats");
    }

    private record MaterialSpec(Material material, ItemVisualOptions visualOptions) {
    }

    @FunctionalInterface
    private interface ElementValueConsumer {
        void accept(Element element, double value);
    }
}
