package net.tkgon.mc.iruuRPG.tool.tree;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The settings of trees saved by name, one yaml file each in {@code plugins/iruuRPG/treetools/}. */
public final class TreeStore {

    private final JavaPlugin plugin;

    public TreeStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private File folder() {
        File folder = new File(plugin.getDataFolder(), "trees");
        if (!folder.exists() && !folder.mkdirs()) plugin.getLogger().warning("Could not create " + folder);
        return folder;
    }

    private File file(String name) {
        return new File(folder(), name + ".yml");
    }

    /** Saves under {@code name} (already made safe by {@link TreeSettings#safeName}); false when the file cannot be written. */
    public boolean save(String name, TreeSettings settings) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("木の設定 (iruuRPG の /treetool で読み込める)。数字は範囲の中に収められる。");
        yaml.set("settings", settings.toMap());
        try {
            yaml.save(file(name));
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save tree " + name + ": " + exception);
            return false;
        }
    }

    /** The settings saved as {@code name}, or null when there are none (or the file is broken). */
    public TreeSettings load(String name) {
        File file = file(name);
        if (!file.isFile()) return null;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("settings");
        if (section == null) return null;
        Map<String, Object> map = new LinkedHashMap<>(section.getValues(false));
        return TreeSettings.fromMap(map, name);
    }

    public boolean delete(String name) {
        File file = file(name);
        return file.isFile() && file.delete();
    }

    /** The names saved, in alphabetical order. */
    public List<String> list() {
        File[] files = folder().listFiles((dir, fileName) -> fileName.endsWith(".yml"));
        List<String> names = new ArrayList<>();
        if (files == null) return names;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 4));
        return names;
    }
}
