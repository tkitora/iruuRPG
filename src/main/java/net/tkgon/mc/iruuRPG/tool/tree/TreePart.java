package net.tkgon.mc.iruuRPG.tool.tree;

import org.bukkit.Material;

/**
 * The parts a generated tree is made of. Each is built from a wool of its own (so the parts can be told apart and turned into real blocks by hand
 * afterwards: log, leaves, ...). The lower the {@code priority}, the stronger the part where two meet. The materials can be changed in the config
 * under {@code treetool.materials}.
 */
public enum TreePart {

    TRUNK("trunk", "幹", 0, Material.BROWN_WOOL),
    ROOT("root", "根", 1, Material.BLACK_WOOL),
    BRANCH("branch", "太い枝", 2, Material.ORANGE_WOOL),
    TWIG("twig", "細い枝", 3, Material.YELLOW_WOOL),
    LEAF("leaf", "葉", 4, Material.GREEN_WOOL);

    private final String key;
    private final String label;
    private final int priority;
    private final Material defaultMaterial;

    TreePart(String key, String label, int priority, Material defaultMaterial) {
        this.key = key;
        this.label = label;
        this.priority = priority;
        this.defaultMaterial = defaultMaterial;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int priority() {
        return priority;
    }

    public Material defaultMaterial() {
        return defaultMaterial;
    }
}
