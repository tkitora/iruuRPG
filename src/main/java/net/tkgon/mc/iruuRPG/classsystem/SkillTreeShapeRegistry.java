package net.tkgon.mc.iruuRPG.classsystem;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class SkillTreeShapeRegistry {

    private final JavaPlugin plugin;
    private SkillTreeShape shape = defaultShape();

    public SkillTreeShapeRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "skill-tree-shape.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("skill-tree-shape.yml", false);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Missing bundled skill-tree-shape.yml");
            }
        }
        if (!file.exists()) {
            shape = defaultShape();
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection tree = yaml.getConfigurationSection("tree");
        if (tree == null) {
            shape = defaultShape();
            return;
        }

        ConfigurationSection controls = tree.getConfigurationSection("controls");
        ConfigurationSection nodes = tree.getConfigurationSection("nodes");
        Map<String, SkillTreeNodeShape> loadedNodes = new HashMap<>();
        if (nodes != null) {
            for (String id : nodes.getKeys(false)) {
                ConfigurationSection section = nodes.getConfigurationSection(id);
                if (section == null) continue;
                loadedNodes.put(id, new SkillTreeNodeShape(
                        id,
                        section.getInt("order", loadedNodes.size() + 1),
                        section.getString("parent")
                ));
            }
        }

        shape = new SkillTreeShape(
                tree.getInt("size", 54),
                tree.getInt("visible-rows", 5),
                controls == null ? 8 : controls.getInt("scroll-up-slot", 8),
                controls == null ? 17 : controls.getInt("scroll-down-slot", 17),
                controls == null ? 44 : controls.getInt("reset-slot", 44),
                controls == null ? 53 : controls.getInt("point-slot", 53),
                loadedNodes.isEmpty() ? defaultShape().nodes() : loadedNodes
        );
    }

    public SkillTreeShape shape() {
        return shape;
    }

    private static SkillTreeShape defaultShape() {
        Map<String, SkillTreeNodeShape> nodes = new HashMap<>();
        String parent = null;
        for (int index = 1; index <= 13; index++) {
            String id = "node_" + index;
            nodes.put(id, new SkillTreeNodeShape(id, index, parent));
            parent = id;
        }
        return new SkillTreeShape(54, 5, 8, 17, 44, 53, nodes);
    }
}
