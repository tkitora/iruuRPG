package net.tkgon.mc.iruuRPG.mob;

import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RpgMobRegistry {

    private final JavaPlugin plugin;
    private final Map<String, RpgMobDefinition> definitions = new HashMap<>();

    public RpgMobRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        definitions.clear();

        File folder = new File(plugin.getDataFolder(), "mobs");
        ensureFolder(folder, "mobs/default.yml");

        File[] files = yamlFiles(folder);
        if (files.length == 0) {
            plugin.getLogger().warning("mobs folder has no yml files: " + folder.getAbsolutePath());
            return;
        }

        for (File file : files) {
            loadFile(file);
        }

        plugin.getLogger().info("Loaded " + definitions.size() + " RPG mob definitions from " + files.length + " files.");
    }

    private void loadFile(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection mobs = yaml.getConfigurationSection("mobs");
        if (mobs != null) {
            loadMobsSection(mobs);
            return;
        }

        if (yaml.contains("type") || yaml.contains("entity-type")) {
            loadDefinition(idFromFile(file), yaml).ifPresent(definition -> definitions.put(definition.id(), definition));
            return;
        }

        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) continue;

            loadDefinition(id, section).ifPresent(definition -> definitions.put(id, definition));
        }
    }

    private void loadMobsSection(ConfigurationSection mobs) {
        for (String id : mobs.getKeys(false)) {
            ConfigurationSection section = mobs.getConfigurationSection(id);
            if (section == null) continue;

            loadDefinition(id, section).ifPresent(definition -> definitions.put(id, definition));
        }
    }

    public Optional<RpgMobDefinition> find(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Map<String, RpgMobDefinition> all() {
        return Collections.unmodifiableMap(definitions);
    }

    private Optional<RpgMobDefinition> loadDefinition(String id, ConfigurationSection section) {
        EntityType entityType = entityType(id, section);
        if (entityType == null || !entityType.isAlive()) {
            plugin.getLogger().warning("mobs: invalid living entity type for mob " + id);
            return Optional.empty();
        }

        StatSet stats = loadStats(id, section.getConfigurationSection("stats"));
        ElementStatSet elementStats = loadElementStats(section);

        return Optional.of(new RpgMobDefinition(
                id,
                entityType,
                section.getString("name", id),
                section.getInt("level", 1),
                section.getLong("experience", section.getLong("xp", 0L)),
                section.getBoolean("ai", true),
                section.getBoolean("gravity", true),
                section.getBoolean("silent", false),
                section.getBoolean("glowing", false),
                section.getBoolean("invulnerable", false),
                section.getBoolean("fire-proof", section.getBoolean("fireproof", false)),
                section.getBoolean("persistent", true),
                section.getBoolean("remove-when-far-away", false),
                section.getBoolean("baby", false),
                section.getBoolean("show-health", true),
                stats,
                elementStats,
                loadEquipment(section.getConfigurationSection("equipment")),
                loadDrops(id, section),
                section.getStringList("description")
        ));
    }

    private EntityType entityType(String id, ConfigurationSection section) {
        String raw = section.getString("type", section.getString("entity-type", "ZOMBIE"));
        if (raw == null || raw.isBlank()) return EntityType.ZOMBIE;

        try {
            return EntityType.valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("mobs: unknown entity type '" + raw + "' for mob " + id);
            return null;
        }
    }

    private StatSet loadStats(String mobId, ConfigurationSection section) {
        StatSet stats = new StatSet();
        if (section == null) return stats;

        for (String key : section.getKeys(false)) {
            Optional<StatType> type = StatType.fromConfigKey(key);
            if (type.isEmpty()) {
                if (!isElementStatSection(key)) {
                    plugin.getLogger().warning("mobs: unknown stat key '" + key + "' in mob " + mobId);
                }
                continue;
            }

            stats.set(type.get(), section.getDouble(key, 0.0));
        }

        return stats;
    }

    private ElementStatSet loadElementStats(ConfigurationSection mobSection) {
        ElementStatSet stats = new ElementStatSet();

        ConfigurationSection elementStats = mobSection.getConfigurationSection("element-stats");
        if (elementStats != null) {
            readElementMap(elementStats.getConfigurationSection("damage"), stats::setDamage);
            readElementMap(elementStats.getConfigurationSection("damage-percent"), stats::setDamagePercent);
            readElementMap(elementStats.getConfigurationSection("resist"), stats::setResist);
        }

        ConfigurationSection legacyStats = mobSection.getConfigurationSection("stats");
        if (legacyStats != null) {
            readLegacyElementMap(legacyStats.getConfigurationSection("colorDmg"), stats::setDamage);
            readLegacyElementMap(legacyStats.getConfigurationSection("colorDmgS"), stats::setDamagePercent);
            readLegacyElementMap(legacyStats.getConfigurationSection("colorDef"), stats::setResist);
        }

        return stats;
    }

    private Map<EquipmentSlot, MobEquipmentItem> loadEquipment(ConfigurationSection section) {
        if (section == null) return Map.of();

        Map<EquipmentSlot, MobEquipmentItem> equipment = new EnumMap<>(EquipmentSlot.class);
        ConfigurationSection dropChances = section.getConfigurationSection("drop-chances");

        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase("drop-chances")) continue;

            EquipmentSlot slot = slot(key);
            if (slot == null) {
                plugin.getLogger().warning("mobs: unknown equipment slot '" + key + "'");
                continue;
            }

            MobEquipmentItem item = equipmentItem(section, key, dropChances);
            if (item != null && (item.hasItem() || item.hasMaterial())) {
                equipment.put(slot, item);
            }
        }

        return equipment;
    }

    private MobEquipmentItem equipmentItem(ConfigurationSection equipment, String key, ConfigurationSection dropChances) {
        ConfigurationSection section = equipment.getConfigurationSection(key);
        double defaultDropChance = dropChances != null ? dropChances.getDouble(key, 0.0) : 0.0;

        if (section != null) {
            String itemId = section.getString("item", section.getString("item-id", section.getString("rpg-item")));
            Material material = material(section.getString("material"));
            double dropChance = section.getDouble("drop-chance", defaultDropChance);
            Color leatherColor = parseColor(firstString(section, "color", "leather-color", "leatherColor", "rgb", "dye-color"));
            return new MobEquipmentItem(blankToNull(itemId), material, dropChance, leatherColor);
        }

        String raw = equipment.getString(key);
        if (raw == null || raw.isBlank()) return null;

        Material material = material(raw);
        if (material != null) {
            return new MobEquipmentItem(null, material, defaultDropChance);
        }
        return new MobEquipmentItem(raw.trim(), null, defaultDropChance);
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

    private List<MobDropItem> loadDrops(String mobId, ConfigurationSection mobSection) {
        List<MobDropItem> drops = new ArrayList<>();

        ConfigurationSection dropsSection = mobSection.getConfigurationSection("drops");
        if (dropsSection != null) {
            for (String key : dropsSection.getKeys(false)) {
                ConfigurationSection entry = dropsSection.getConfigurationSection(key);
                if (entry != null) {
                    addDrop(mobId, drops, entry.getString("item", entry.getString("item-id", key)), chance(entry), minAmount(entry), maxAmount(entry));
                    continue;
                }

                Object value = dropsSection.get(key);
                if (value instanceof Number number) {
                    addDrop(mobId, drops, key, number.doubleValue(), 1, 1);
                } else if (value instanceof String raw && !raw.isBlank()) {
                    addDrop(mobId, drops, raw, 1.0, 1, 1);
                }
            }
            return drops;
        }

        for (Map<?, ?> entry : mobSection.getMapList("drops")) {
            String itemId = string(entry, "item", "item-id", "id", "rpg-item");
            double chance = number(entry, 1.0, "drop-chance", "dropchance", "chance");
            int minAmount = (int) Math.round(number(entry, 1.0, "min-amount", "min", "amount"));
            int maxAmount = (int) Math.round(number(entry, minAmount, "max-amount", "max", "amount"));
            addDrop(mobId, drops, itemId, chance, minAmount, maxAmount);
        }

        return drops;
    }

    private void addDrop(String mobId, List<MobDropItem> drops, String itemId, double chance, int minAmount, int maxAmount) {
        if (itemId == null || itemId.isBlank()) {
            plugin.getLogger().warning("mobs: blank drop item in mob " + mobId);
            return;
        }

        drops.add(new MobDropItem(itemId, chance, minAmount, maxAmount));
    }

    private double chance(ConfigurationSection section) {
        return section.getDouble("drop-chance",
                section.getDouble("dropchance",
                        section.getDouble("chance", 1.0)));
    }

    private int minAmount(ConfigurationSection section) {
        return Math.max(1, section.getInt("min-amount", section.getInt("min", section.getInt("amount", 1))));
    }

    private int maxAmount(ConfigurationSection section) {
        int minAmount = minAmount(section);
        return Math.max(minAmount, section.getInt("max-amount", section.getInt("max", section.getInt("amount", minAmount))));
    }

    private String string(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private double number(Map<?, ?> map, double fallback, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String raw) {
                try {
                    return Double.parseDouble(raw.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fallback;
    }

    private EquipmentSlot slot(String raw) {
        String normalized = raw.trim().replace("-", "_").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "main_hand", "hand", "weapon" -> EquipmentSlot.HAND;
            case "off_hand", "offhand" -> EquipmentSlot.OFF_HAND;
            case "helmet", "head" -> EquipmentSlot.HEAD;
            case "chestplate", "chest" -> EquipmentSlot.CHEST;
            case "leggings", "legs" -> EquipmentSlot.LEGS;
            case "boots", "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private Material material(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void readElementMap(ConfigurationSection section, ElementValueConsumer consumer) {
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            Element element = Element.fromConfig(key, null);
            if (element == null) {
                plugin.getLogger().warning("mobs: unknown element '" + key + "'");
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
                plugin.getLogger().warning("mobs: unknown legacy element index '" + key + "'");
            }
        }
    }

    private void ensureFolder(File folder, String defaultResource) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create mobs folder: " + folder.getAbsolutePath());
            return;
        }

        if (yamlFiles(folder).length == 0) {
            File defaultFile = new File(folder, "default.yml");
            if (!defaultFile.exists()) {
                plugin.saveResource(defaultResource, false);
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

    @FunctionalInterface
    private interface ElementValueConsumer {
        void accept(Element element, double value);
    }
}
