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
    BRANCH("branch", "枝", 2, Material.OAK_WOOD),
    TWIG("twig", "細い枝", 3, Material.OAK_WOOD),
    LEAF("leaf", "葉", 4, Material.OAK_LEAVES),
    /** The snow on the leaves of a tiered tree. */
    SNOW("snow", "雪", 3, Material.SNOW_BLOCK),
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
    /** The parts of the house base (t179: a block-out in wool, no materials to choose: the shape only). */
    HOUSE_PILLAR("house-pillar", "柱", 0, Material.BROWN_WOOL),
    HOUSE_FLOOR("house-floor", "床", 1, Material.LIGHT_GRAY_WOOL),
    HOUSE_ROOF("house-roof", "屋根", 2, Material.RED_WOOL),
    HOUSE_WALL("house-wall", "壁", 3, Material.WHITE_WOOL),
    /** (t181) the stone of a stone ground storey, a chimney and a tower. */
    HOUSE_STONE("house-stone", "石", 0, Material.GRAY_WOOL),
    /** (t190) the beams: the timber frame's beams and braces, the brackets under a jetty, the ridge beam (the posts are {@link #HOUSE_PILLAR}). */
    HOUSE_BEAM("house-beam", "梁", 0, Material.ORANGE_WOOL),
    /** (t190) the glass of a window, set back from the face of the wall. */
    HOUSE_WINDOW("house-window", "窓", 4, Material.LIGHT_BLUE_WOOL),
    /** (t190) the lobby: the paving of the plaza, its paths (the border, the grid of the tiles, the ways to the doors), the rims of the raised beds and of a fountain, their soil, the water. */
    LOBBY_PAVE("lobby-pave", "広場の床", 5, Material.LIGHT_GRAY_CONCRETE),
    LOBBY_PATH("lobby-path", "道", 5, Material.GRAY_CONCRETE),
    LOBBY_BED("lobby-bed", "花壇・噴水の縁", 3, Material.POLISHED_ANDESITE),
    LOBBY_SOIL("lobby-soil", "花壇の土", 5, Material.GREEN_WOOL),
    LOBBY_WATER("lobby-water", "水", 5, Material.LIGHT_BLUE_CONCRETE),
    /** The shades of the leaf gradient above the lowest ({@link #LEAF}): their blocks are the entries of the gradient of the settings. */
    LEAF_2("leaf-2", "葉 (2段目)", 4, Material.OAK_LEAVES),
    LEAF_3("leaf-3", "葉 (3段目)", 4, Material.OAK_LEAVES),
    LEAF_4("leaf-4", "葉 (4段目)", 4, Material.OAK_LEAVES),
    LEAF_5("leaf-5", "葉 (5段目)", 4, Material.OAK_LEAVES),
    LEAF_6("leaf-6", "葉 (6段目)", 4, Material.OAK_LEAVES),
    /** (t246) The part of a house being edited, in the preview only (never put in the world): stained glass, so it stands out from the wool. */
    HOUSE_SELECTED("house-selected", "編集中のパーツ (プレビュー)", 0, Material.LIME_STAINED_GLASS),
    /** (t252) the thin branches of a thin tree: a post near the trunk (a wall), a post further out (a fence), a branch along (a slab, its bottom or top half). */
    BRANCH_WALL("branch-wall", "枝 (壁)", 2, Material.MUD_BRICK_WALL),
    BRANCH_FENCE("branch-fence", "細い枝 (フェンス)", 3, Material.OAK_FENCE),
    BRANCH_SLAB("branch-slab", "横の枝 (ハーフ)", 3, Material.OAK_SLAB),
    /** The upper half of a block a slab branch passes: the block of {@link #BRANCH_SLAB}, as its top half. */
    BRANCH_SLAB_TOP("branch-slab-top", "横の枝 (ハーフの上)", 3, Material.OAK_SLAB),
    /** (t258) a stair of the thin tree: where a log steps up as it goes over, and round the foot. Its way: the back toward north (this), east, south, west; one block for all. */
    BRANCH_STAIR("branch-stair", "幹・枝の階段", 1, Material.OAK_STAIRS),
    BRANCH_STAIR_EAST("branch-stair-east", "幹・枝の階段 (東)", 1, Material.OAK_STAIRS),
    BRANCH_STAIR_SOUTH("branch-stair-south", "幹・枝の階段 (南)", 1, Material.OAK_STAIRS),
    BRANCH_STAIR_WEST("branch-stair-west", "幹・枝の階段 (西)", 1, Material.OAK_STAIRS),
    /** (the silhouette trees) the stairs upside down (their top half full), the same four ways; and logs lying along x and z. */
    BRANCH_STAIR_TOP("branch-stair-top", "幹・枝の階段 (上付き・北)", 1, Material.OAK_STAIRS),
    BRANCH_STAIR_TOP_EAST("branch-stair-top-east", "幹・枝の階段 (上付き・東)", 1, Material.OAK_STAIRS),
    BRANCH_STAIR_TOP_SOUTH("branch-stair-top-south", "幹・枝の階段 (上付き・南)", 1, Material.OAK_STAIRS),
    BRANCH_STAIR_TOP_WEST("branch-stair-top-west", "幹・枝の階段 (上付き・西)", 1, Material.OAK_STAIRS),
    TRUNK_X("trunk-x", "幹 (東西に寝た丸太)", 0, Material.OAK_WOOD),
    TRUNK_Z("trunk-z", "幹 (南北に寝た丸太)", 0, Material.OAK_WOOD),
    /** (t282) the ornate tree: trapdoors (the level tips of thin branches, the streaks of bark) and open fence gates (level tips). */
    BRANCH_TRAPDOOR("branch-trapdoor", "枝先・樹皮 (トラップドア)", 3, Material.OAK_TRAPDOOR),
    BRANCH_GATE("branch-gate", "枝先 (フェンスゲート)", 3, Material.OAK_FENCE_GATE),
    /**
     * (t295) The roles of a building (one block each: its stairs, slabs, walls, fences and logs are the same block's, see {@code BuildingShape}): the podium, the
     * frame's posts and beams, the wall, the glass, the corbels, the floors, the roof and its stripes, the eaves' edge, the verges, the ridge, the hips, the chimney,
     * the rails, the finial. (t302) Each in a wool of its own colour, as the house base's parts are (no building blocks; the shapes, where they are wanted, from the
     * blocks nearest the wool's colour).
     */
    BLD_PLINTH("bld-plinth", "基壇・石 (plinth)", 0, Material.GRAY_WOOL),
    BLD_POST("bld-post", "柱 (frame_post)", 0, Material.BROWN_WOOL),
    BLD_BEAM("bld-beam", "梁・幕板・まぐさ (frame_beam)", 0, Material.ORANGE_WOOL),
    BLD_INFILL("bld-infill", "壁 (infill)", 0, Material.WHITE_WOOL),
    BLD_WINDOW("bld-window", "窓 (window)", 0, Material.LIGHT_BLUE_WOOL),
    BLD_CORBEL("bld-corbel", "持ち送り (corbel)", 0, Material.YELLOW_WOOL),
    BLD_FLOOR("bld-floor", "床 (floor)", 0, Material.LIGHT_GRAY_WOOL),
    BLD_ROOF("bld-roof", "屋根 (roof_band_0)", 0, Material.RED_WOOL),
    BLD_ROOF_2("bld-roof-2", "屋根の縞 2 (roof_band_1)", 0, Material.PINK_WOOL),
    BLD_ROOF_3("bld-roof-3", "屋根の縞 3 (roof_band_2)", 0, Material.MAGENTA_WOOL),
    BLD_EAVE("bld-eave", "軒先 (eave)", 0, Material.BLACK_WOOL),
    BLD_VERGE("bld-verge", "破風 (verge)", 0, Material.CYAN_WOOL),
    BLD_RIDGE("bld-ridge", "棟 (ridge)", 0, Material.PURPLE_WOOL),
    BLD_HIP("bld-hip", "隅棟 (hip)", 0, Material.GREEN_WOOL),
    BLD_CHIMNEY("bld-chimney", "煙突 (chimney)", 0, Material.BLUE_WOOL),
    BLD_RAIL("bld-rail", "手すり (rail)", 0, Material.LIME_WOOL),
    BLD_FINIAL("bld-finial", "頂華 (finial)", 0, Material.YELLOW_WOOL);

    /** (t258) The part whose block this one is put in (a stair's other ways and a slab's top half are their first part's block). */
    TreePart blockOf() {
        return switch (this) {
            case BRANCH_SLAB_TOP -> BRANCH_SLAB;
            case BRANCH_STAIR_EAST, BRANCH_STAIR_SOUTH, BRANCH_STAIR_WEST, BRANCH_STAIR_TOP, BRANCH_STAIR_TOP_EAST, BRANCH_STAIR_TOP_SOUTH, BRANCH_STAIR_TOP_WEST -> BRANCH_STAIR;
            case TRUNK_X, TRUNK_Z -> TRUNK;
            default -> this;
        };
    }

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
