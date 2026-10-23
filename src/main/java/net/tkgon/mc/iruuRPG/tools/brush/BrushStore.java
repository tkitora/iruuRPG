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
 * What the brush keeps in a file, shared by everyone who uses it ({@code plugins/iruuRPG/brush.yml}): the blacklist (blocks never used in a gradient, nor chosen by the
 * automatic shade) and the gradient presets made by hand. Read when the plugin starts, written when the menu or the command saves.
 */
final class BrushStore {

    private final JavaPlugin plugin;
    private final Set<Material> blacklist = new LinkedHashSet<>();
    private final List<Gradients.Preset> presets = new ArrayList<>();

    BrushStore(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private File file() {
        return new File(plugin.getDataFolder(), "brush.yml");
    }

    private void load() {
        blacklist.clear();
        presets.clear();
        File file = file();
        if (!file.isFile()) {
            // (before anything was saved: blocks that look wrong in the middle of a gradient)
            for (String name : List.of("MYCELIUM", "GLOWSTONE", "SHROOMLIGHT", "SEA_LANTERN", "MAGMA_BLOCK", "SLIME_BLOCK", "HONEY_BLOCK", "TNT", "REDSTONE_LAMP")) {
                Material material = Material.matchMaterial(name);
                if (material != null) blacklist.add(material);
            }
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String name : yaml.getStringList("gradient-blacklist")) {
            Material material = Material.matchMaterial(name);
            if (material != null) blacklist.add(material);
            else plugin.getLogger().warning("brush.yml: unknown block in gradient-blacklist: " + name);
        }
        ConfigurationSection section = yaml.getConfigurationSection("presets");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection preset = section.getConfigurationSection(key);
            if (preset == null) continue;
            Material from = Material.matchMaterial(preset.getString("from", ""));
            Material to = Material.matchMaterial(preset.getString("to", ""));
            if (from == null || to == null) {
                plugin.getLogger().warning("brush.yml: preset " + key + " needs 'from' and 'to' blocks");
                continue;
            }
            presets.add(new Gradients.Preset(key, from, to, Math.max(0, Math.min(14, preset.getInt("steps", 4)))));
        }
    }

    /** Writes the file; false when it cannot be written. */
    boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("ペイントブラシの共通データ (全員で共有)。gradient-blacklist: グラデーションと自動陰影で使わないブロック / presets: 自作のグラデーション (from, to, steps)");
        List<String> names = new ArrayList<>();
        for (Material material : blacklist) names.add(material.name());
        yaml.set("gradient-blacklist", names);
        for (Gradients.Preset preset : presets) {
            yaml.set("presets." + preset.name() + ".from", preset.from().name());
            yaml.set("presets." + preset.name() + ".to", preset.to().name());
            yaml.set("presets." + preset.name() + ".steps", preset.steps());
        }
        try {
            yaml.save(file());
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save brush.yml: " + exception);
            return false;
        }
    }

    Set<Material> blacklist() {
        return blacklist;
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

    /** The name made safe for the yaml key (letters, digits, - and _, and Japanese letters are dropped), or null when nothing is left of it. */
    static String safeName(String name) {
        if (name == null) return null;
        String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return clean.isEmpty() || clean.length() > 32 ? null : clean;
    }

    void addPreset(String name, Material from, Material to, int steps) {
        presets.removeIf(preset -> preset.name().equals(name));
        presets.add(new Gradients.Preset(name, from, to, steps));
    }
}
