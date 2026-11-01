package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.Material;

/**
 * The parts a generated object is made of. The block of each is chosen in the menu (or by command) and saved with the settings; what is not chosen comes from the
 * config ({@code treetool.materials}), else from the default here. The lower the {@code priority}, the stronger the part where two meet. The leaves are a
 * gradient of up to {@link #SHADES} blocks from the bottom of a clump to its top: {@link #LEAF} is the lowest shade, {@code LEAF_2}.. the ones above it.
 */
enum TreePart {

    TRUNK("trunk", "幹", 0, Material.OAK_WOOD),
    ROOT("root", "根", 1, Material.OAK_WOOD),
    BRANCH("branch", "太い枝", 2, Material.OAK_WOOD),
    TWIG("twig", "細い枝", 3, Material.OAK_WOOD),
    LEAF("leaf", "葉", 4, Material.OAK_LEAVES),
    STONE("stone", "岩", 5, Material.GRAY_WOOL),
    STONE_LIGHT("stone-light", "岩(明)", 6, Material.LIGHT_GRAY_WOOL),
    STONE_DARK("stone-dark", "岩(暗)", 7, Material.BLUE_WOOL),
    MOSS("moss", "苔", 3, Material.LIME_WOOL),
    CRYSTAL("crystal", "結晶", 8, Material.LIGHT_BLUE_WOOL),
    CRYSTAL_CORE("crystal-core", "結晶の芯", 2, Material.WHITE_WOOL),
    /** The threads of a web that hold it (the frame, the spokes, the lines to the terrain); the cobweb itself, so the preview and the result are the same. */
    WEB_FRAME("web-frame", "巣の枠糸", 0, Material.COBWEB),
    /** The threads that catch (the spiral, the grid, the tangle). */
    WEB("web", "巣の糸", 1, Material.COBWEB),
    /** The shades of the leaf gradient above the lowest ({@link #LEAF}): their blocks are the entries of the gradient of the settings. */
    LEAF_2("leaf-2", "葉 (2段目)", 4, Material.OAK_LEAVES),
    LEAF_3("leaf-3", "葉 (3段目)", 4, Material.OAK_LEAVES),
    LEAF_4("leaf-4", "葉 (4段目)", 4, Material.OAK_LEAVES),
    LEAF_5("leaf-5", "葉 (5段目)", 4, Material.OAK_LEAVES),
    LEAF_6("leaf-6", "葉 (6段目)", 4, Material.OAK_LEAVES);

    /** How many blocks the leaf gradient can have. */
    static final int SHADES = 6;
    private static final TreePart[] LEAF_SHADES = {LEAF, LEAF_2, LEAF_3, LEAF_4, LEAF_5, LEAF_6};

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

    /** The place of the part in the leaf gradient (0 = the lowest, {@link #LEAF}); -1 for a part that is not a leaf. */
    int shade() {
        for (int index = 0; index < LEAF_SHADES.length; index++) if (LEAF_SHADES[index] == this) return index;
        return -1;
    }

    /** The leaf of shade {@code index} (0 = the lowest), kept inside the gradient. */
    static TreePart leaf(int index) {
        return LEAF_SHADES[Math.max(0, Math.min(SHADES - 1, index))];
    }

    /**
     * The shade of a leaf block at {@code height} of its clump (0 = the bottom, 1 = the top) in a gradient of {@code shades} blocks: the bottom of a clump takes the
     * first block of the gradient, the top the last. {@code jitter} (about -1..1, from a noise) moves the borders a little, so the bands are not flat stripes.
     */
    static TreePart leafAt(double height, int shades, double jitter) {
        if (shades <= 1) return LEAF;
        double place = (height + 0.12 * jitter) * shades;
        return leaf((int) Math.floor(Math.max(0.0, Math.min(shades - 1.0e-9, place))));
    }
}
