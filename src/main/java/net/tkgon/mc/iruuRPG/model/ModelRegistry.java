package net.tkgon.mc.iruuRPG.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The models of the folder {@code plugins/iruuRPG/models/}: every {@code <id>.bbmodel} is read; the custom model data of the parts is kept in {@code models/index.yml} (a part keeps
 * its number when the model is edited, so packs stay compatible), and the pack zip is made from them. The id of a model is its file name.
 */
final class ModelRegistry {

    private final JavaPlugin plugin;
    private final Map<String, BbModel> models = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    ModelRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    File folder() {
        return new File(plugin.getDataFolder(), "models");
    }

    File packFile() {
        return new File(new File(plugin.getDataFolder(), "resourcepacks"), "iruumodel.zip");
    }

    Optional<BbModel> find(String id) {
        return Optional.ofNullable(models.get(id));
    }

    Map<String, BbModel> all() {
        return models;
    }

    List<String> problems() {
        return problems;
    }

    /** Puts the bundled sample in the folder the first time (when the folder is not there yet). */
    void extractSample() {
        File folder = folder();
        if (folder.exists()) return;
        if (!folder.mkdirs()) return;
        try (InputStream in = plugin.getResource("models/sample_crawler.bbmodel")) {
            if (in != null) Files.write(new File(folder, "sample_crawler.bbmodel").toPath(), in.readAllBytes());
        } catch (IOException exception) {
            plugin.getLogger().warning("models: could not put the sample model in place: " + exception);
        }
    }

    /** Reads the models again, numbers their parts, and (with {@code build}) makes the pack. The problems are in {@link #problems()}. */
    void reload(boolean build) {
        models.clear();
        problems.clear();
        File[] files = folder().listFiles((dir, name) -> name.endsWith(".bbmodel"));
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            String id = BbModel.safe(file.getName().substring(0, file.getName().length() - ".bbmodel".length()));
            try {
                BbModel model = BbModel.parse(id, Files.readString(file.toPath(), StandardCharsets.UTF_8));
                for (String warning : model.warnings) problems.add(file.getName() + ": " + warning);
                models.put(id, model);
            } catch (IOException | RuntimeException exception) {
                problems.add(file.getName() + ": could not be read (" + exception + ")");
            }
        }
        File indexFile = new File(folder(), "index.yml");
        Map<String, Integer> index = new LinkedHashMap<>();
        if (indexFile.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(indexFile);
            var section = yaml.getConfigurationSection("parts");
            if (section != null) for (String key : section.getKeys(false)) index.put(key.replace('|', '/'), section.getInt(key));
        }
        Map<String, Integer> before = new LinkedHashMap<>(index);
        ModelPackBuilder.assign(new ArrayList<>(models.values()), index, plugin.getConfig().getInt("model.cmd-base", 8100000));
        if (!index.equals(before)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().header("モデルの部品ごとの custom model data (編集しても同じ番号を保つ。消すと番号が変わるので、パックを作り直して配り直すこと)");
            for (Map.Entry<String, Integer> entry : index.entrySet()) yaml.set("parts." + entry.getKey().replace('/', '|'), entry.getValue());
            try {
                yaml.save(indexFile);
            } catch (IOException exception) {
                problems.add("index.yml could not be written: " + exception);
            }
        }
        if (build && !models.isEmpty()) buildPack();
    }

    /** Makes the zip from the models read (the problems go to {@link #problems()}). */
    boolean buildPack() {
        try {
            List<String> warnings = new ArrayList<>();
            ModelPackBuilder.build(new ArrayList<>(models.values()), packFile(), plugin.getConfig().getString("model.base-item", "PAPER"), warnings);
            problems.addAll(warnings);
            return true;
        } catch (IOException exception) {
            problems.add("the pack could not be written: " + exception);
            return false;
        }
    }
}
