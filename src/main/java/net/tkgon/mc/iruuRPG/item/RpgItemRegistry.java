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

    /** Reloads every item; if loading throws, the previous definitions stay (never half empty). */
    public void reload() {
        Map<String, RpgItemDefinition> previous = new HashMap<>(definitions);
        try {
            load();
        } catch (RuntimeException e) {
            definitions.clear();
            definitions.putAll(previous);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "items: reload failed, keeping the previous items", e);
        }
    }

    private void load() {
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

    private static final java.util.Set<String> STACK_VARIABLES = java.util.Set.of("name", "bar", "count", "max", "percent");

    /**
     * stacks.display: what the player sees and hears when the stacks change. Everything is optional (the default is
     * the old look). Too long text is cut, `&` / `§` colour codes are removed (colours are separate keys), a wrong
     * colour or an out-of-range number is fixed; each is reported.
     */
    private WeaponStackSpec.Display stackDisplay(String id, org.bukkit.configuration.ConfigurationSection section) {
        WeaponStackSpec.Display base = WeaponStackSpec.Display.defaults();
        if (section == null) return base;
        String where = "items: " + id + ": stacks.display.";
        return new WeaponStackSpec.Display(
                stackText(where + "format", section.getString("format"), base.format(), WeaponStackSpec.Display.MAX_FORMAT_LENGTH, true),
                stackText(where + "filled", section.getString("filled"), base.filled(), WeaponStackSpec.Display.MAX_SYMBOL_LENGTH, false),
                stackText(where + "empty", section.getString("empty"), base.empty(), WeaponStackSpec.Display.MAX_SYMBOL_LENGTH, false),
                stackColor(where + "filled-color", section.getString("filled-color"), base.filledColor()),
                stackColor(where + "empty-color", section.getString("empty-color"), base.emptyColor()),
                stackColor(where + "text-color", section.getString("text-color"), base.textColor()),
                section.contains("max-format")
                        ? stackText(where + "max-format", section.getString("max-format"), base.maxFormat(), WeaponStackSpec.Display.MAX_FORMAT_LENGTH, true)
                        : base.maxFormat(),
                stackColor(where + "max-color", section.getString("max-color"), base.maxColor()),
                section.getBoolean("bold", base.bold()),
                section.contains("title")
                        ? stackText(where + "title", section.getString("title"), "", WeaponStackSpec.Display.MAX_FORMAT_LENGTH, true)
                        : "",
                stackNumber(where + "seconds", section, "seconds", base.seconds(), 0.2, 5.0),
                stackNumber(where + "fade-in", section, "fade-in", base.fadeIn(), 0.0, 1.0),
                stackNumber(where + "fade-out", section, "fade-out", base.fadeOut(), 0.0, 2.0),
                section.getBoolean("sound", base.sound()));
    }

    /** stacks.gain-on: a list (or one word) of cast / hit / damaged / kill; unknown words are ignored with a warning. */
    private java.util.Set<String> stackTriggers(String id, org.bukkit.configuration.ConfigurationSection section) {
        java.util.List<String> raw = section.isList("gain-on") ? section.getStringList("gain-on")
                : section.isString("gain-on") ? java.util.List.of(section.getString("gain-on")) : java.util.List.of();
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (String word : raw) {
            String trigger = word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
            if (WeaponStackSpec.TRIGGERS.contains(trigger)) {
                result.add(trigger);
            } else {
                plugin.getLogger().warning("items: " + id + ": stacks.gain-on '" + word + "' is unknown (use cast / hit / damaged / kill)");
            }
        }
        return result;
    }

    private String stackText(String where, String raw, String fallback, int limit, boolean variables) {
        if (raw == null) return fallback;
        String text = raw.replace("&", "").replace("§", "");
        if (!text.equals(raw)) plugin.getLogger().warning(where + ": colour codes (& / §) are not used; set colours with the colour keys (removed)");
        if (text.length() > limit) {
            plugin.getLogger().warning(where + ": longer than " + limit + " characters (cut)");
            text = text.substring(0, limit);
        }
        if (variables) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{([^}]*)}").matcher(text);
            while (matcher.find()) {
                if (!STACK_VARIABLES.contains(matcher.group(1).toLowerCase(java.util.Locale.ROOT))) {
                    plugin.getLogger().warning(where + ": unknown variable {" + matcher.group(1) + "} (name / bar / count / max / percent); it is shown as it is");
                }
            }
        }
        return text;
    }

    private net.kyori.adventure.text.format.TextColor stackColor(String where, String raw, net.kyori.adventure.text.format.TextColor fallback) {
        if (raw == null) return fallback;
        if (!raw.matches("#[0-9a-fA-F]{6}")) {
            plugin.getLogger().warning(where + ": '" + raw + "' must look like #rrggbb (the default is used)");
            return fallback;
        }
        return net.kyori.adventure.text.format.TextColor.color(Integer.parseInt(raw.substring(1), 16));
    }

    private double stackNumber(String where, org.bukkit.configuration.ConfigurationSection section, String key, double fallback, double min, double max) {
        if (!section.contains(key)) return fallback;
        double value = section.getDouble(key, fallback);
        if (value < min || value > max) {
            double fixed = Math.max(min, Math.min(max, value));
            plugin.getLogger().warning(where + ": " + value + " is out of range (" + min + " - " + max + "), used " + fixed);
            return fixed;
        }
        return value;
    }

    private net.tkgon.mc.iruuRPG.skill.SkillRegistry generalSkills;

    /** The skills of skills/*.yml, so an item whose `skill:` names none of them is reported. */
    public void setGeneralSkills(net.tkgon.mc.iruuRPG.skill.SkillRegistry generalSkills) {
        this.generalSkills = generalSkills;
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
        String rawSkill = section.getString("skill", "").trim();
        ItemSkillType skill = ItemSkillType.fromConfig(rawSkill);
        // `skill:` is an item skill (sweep / heal / dash / strike / slash) or the id of a skill of skills/*.yml
        String skillId = skill == ItemSkillType.NONE && !rawSkill.isEmpty() && !rawSkill.equalsIgnoreCase("none")
                ? rawSkill.toLowerCase(Locale.ROOT) : "";
        java.util.Map<String, Object> skillOverrides = section.getConfigurationSection("skill-overrides") == null
                ? java.util.Map.of() : section.getConfigurationSection("skill-overrides").getValues(false);
        WeaponStackSpec stacks = null;
        org.bukkit.configuration.ConfigurationSection stackSection = section.getConfigurationSection("stacks");
        if (stackSection != null) {
            stacks = new WeaponStackSpec(stackSection.getString("name", "スタック"), stackSection.getInt("gain", 1),
                    stackSection.getInt("max", 5), stackSection.getString("on-max", ""), stackSection.getDouble("decay-seconds", 0.0),
                    stackDisplay(id, stackSection.getConfigurationSection("display")), stackTriggers(id, stackSection));
            if (skillId.isEmpty()) {
                plugin.getLogger().warning("items: " + id + ": stacks need a skill of skills/*.yml in `skill:` (the stacks are ignored)");
                stacks = null;
            } else if (stacks.onMax().isEmpty()) {
                plugin.getLogger().warning("items: " + id + ": stacks.on-max (the skill cast at the maximum) is missing (the stacks are ignored)");
                stacks = null;
            } else if (generalSkills != null && !generalSkills.all().containsKey(stacks.onMax())) {
                plugin.getLogger().warning("items: " + id + ": stacks.on-max '" + stacks.onMax() + "' is not a skill of skills/*.yml");
            }
        }
        if (!skillId.isEmpty() && generalSkills != null && !generalSkills.all().containsKey(skillId)) {
            plugin.getLogger().warning("items: " + id + ": skill '" + skillId + "' is neither an item skill nor a skill of skills/*.yml");
        }
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
                skillId,
                skillOverrides,
                stacks,
                stats,
                elementStats,
                description,
                section.getConfigurationSection("combo-style") == null ? java.util.Map.of() : section.getConfigurationSection("combo-style").getValues(false)
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

        // The starter weapons (tutorial) and the quest materials must exist even on servers that already have item files.
        for (String extra : new String[]{"starter.yml", "quest.yml", "craft.yml"}) {
            if (!new File(folder, extra).exists()) {
                try {
                    plugin.saveResource("items/" + extra, false);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Missing bundled items resource: " + extra);
                }
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
