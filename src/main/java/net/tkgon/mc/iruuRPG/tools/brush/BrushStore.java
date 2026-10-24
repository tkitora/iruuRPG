package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the brush keeps in a file, shared by everyone who uses it ({@code plugins/iruuRPG/brush.yml}): two blacklists (blocks never used in a gradient made from two blocks, and
 * blocks the automatic shade never chooses) and the presets made by hand (a gradient from two ends, or a list of blocks with their weights, laid as a gradient or by weight).
 * Read when the plugin starts, written when the menu or the command saves.
 */
final class BrushStore {

    private final JavaPlugin plugin;
    private final Set<Material> gradientBlacklist = new LinkedHashSet<>();
    private final Set<Material> shadeBlacklist = new LinkedHashSet<>();
    private final List<Gradients.Preset> presets = new ArrayList<>();

    BrushStore(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private File file() {
        return new File(plugin.getDataFolder(), "brush.yml");
    }

    private static void read(List<String> names, Set<Material> into, JavaPlugin plugin, String where) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) into.add(material);
            else plugin.getLogger().warning("brush.yml: unknown block in " + where + ": " + name);
        }
    }

    private void load() {
        gradientBlacklist.clear();
        shadeBlacklist.clear();
        presets.clear();
        File file = file();
        if (!file.isFile()) {
            // (before anything was saved: blocks that look wrong in the middle of a gradient)
            for (String name : List.of("MYCELIUM", "GLOWSTONE", "SHROOMLIGHT", "SEA_LANTERN", "MAGMA_BLOCK", "SLIME_BLOCK", "HONEY_BLOCK", "TNT", "REDSTONE_LAMP")) {
                Material material = Material.matchMaterial(name);
                if (material != null) {
                    gradientBlacklist.add(material);
                    shadeBlacklist.add(material);
                }
            }
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        read(yaml.getStringList("gradient-blacklist"), gradientBlacklist, plugin, "gradient-blacklist");
        // (a file from before the two lists: the one list served both)
        if (yaml.contains("shade-blacklist")) read(yaml.getStringList("shade-blacklist"), shadeBlacklist, plugin, "shade-blacklist");
        else shadeBlacklist.addAll(gradientBlacklist);
        ConfigurationSection section = yaml.getConfigurationSection("presets");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection preset = section.getConfigurationSection(key);
            if (preset == null) continue;
            if (preset.isList("blocks")) {
                List<Gradients.Part> parts = new ArrayList<>();
                for (String entry : preset.getStringList("blocks")) {
                    String[] pair = entry.split(":");
                    Material material = Material.matchMaterial(pair[0].trim());
                    if (material == null) {
                        plugin.getLogger().warning("brush.yml: preset " + key + ": unknown block " + pair[0]);
                        continue;
                    }
                    int weight = 1;
                    try {
                        if (pair.length > 1) weight = Math.max(1, Math.min(99, Integer.parseInt(pair[1].trim())));
                    } catch (NumberFormatException ignored) {
                        weight = 1;
                    }
                    parts.add(new Gradients.Part(material, weight));
                }
                if (!parts.isEmpty()) presets.add(Gradients.Preset.manual(key, parts, !"ratio".equalsIgnoreCase(preset.getString("mode", "gradient"))));
                continue;
            }
            Material from = Material.matchMaterial(preset.getString("from", ""));
            Material to = Material.matchMaterial(preset.getString("to", ""));
            if (from == null || to == null) {
                plugin.getLogger().warning("brush.yml: preset " + key + " needs 'from' and 'to' blocks, or a 'blocks' list");
                continue;
            }
            presets.add(new Gradients.Preset(key, from, to, Math.max(0, Math.min(14, preset.getInt("steps", 4)))));
        }
    }

    /** Writes the file; false when it cannot be written. */
    boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("ペイントブラシの共通データ (全員で共有)。gradient-blacklist: 2つのブロックからグラデーションを作るとき途中に使わないブロック / shade-blacklist: 自動陰影の暗いブロックに選ばないブロック / "
                + "presets: 自作のブロックの組 (from, to, steps = 2つの端から作る / blocks: [ブロック:割合, ...] と mode: gradient か ratio)");
        yaml.set("gradient-blacklist", names(gradientBlacklist));
        yaml.set("shade-blacklist", names(shadeBlacklist));
        for (Gradients.Preset preset : presets) {
            String base = "presets." + preset.name();
            if (preset.generated()) {
                yaml.set(base + ".from", preset.from().name());
                yaml.set(base + ".to", preset.to().name());
                yaml.set(base + ".steps", preset.steps());
            } else {
                List<String> blocks = new ArrayList<>();
                for (Gradients.Part part : preset.parts()) blocks.add(part.material().name() + ":" + part.weight());
                yaml.set(base + ".blocks", blocks);
                yaml.set(base + ".mode", preset.gradient() ? "gradient" : "ratio");
            }
        }
        try {
            yaml.save(file());
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save brush.yml: " + exception);
            return false;
        }
    }

    private static List<String> names(Set<Material> set) {
        List<String> names = new ArrayList<>();
        for (Material material : set) names.add(material.name());
        return names;
    }

    /** Blocks never used in the middle of a gradient made from two blocks. */
    Set<Material> gradientBlacklist() {
        return gradientBlacklist;
    }

    /** Blocks the automatic shade never chooses. */
    Set<Material> shadeBlacklist() {
        return shadeBlacklist;
    }

    List<Gradients.Preset> presets() {
        return presets;
    }

    /** Every preset, the built-in ones first. */
    List<Gradients.Preset> allPresets() {
        List<Gradients.Preset> all = new ArrayList<>(Gradients.BUILT_IN);
        all.addAll(presets);
        return all;
    }

    /** The name made safe for the yaml key (letters, digits, - and _), or null when nothing is left of it. */
    static String safeName(String name) {
        if (name == null) return null;
        String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return clean.isEmpty() || clean.length() > 32 ? null : clean;
    }

    /** Adds (or replaces) a preset listed by hand from the block list of the settings. */
    void addPreset(String name, BrushSettings s) {
        presets.removeIf(preset -> preset.name().equals(name));
        presets.add(Gradients.fromSettings(name, s));
    }

    /** Adds (or replaces) a preset made from two ends. */
    void addPreset(String name, Material from, Material to, int steps) {
        presets.removeIf(preset -> preset.name().equals(name));
        presets.add(new Gradients.Preset(name, from, to, steps));
    }
}
