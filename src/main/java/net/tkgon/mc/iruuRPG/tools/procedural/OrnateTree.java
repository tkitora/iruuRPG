package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.ornate.BlockShapes;
import net.tkgon.mc.iruuRPG.tools.procedural.ornate.OrnateBuilder;
import net.tkgon.mc.iruuRPG.tools.procedural.ornate.Skeleton;
import net.tkgon.mc.iruuRPG.tools.procedural.ornate.SkeletonGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The ornate tree (t282, nekorozv's design): a tree for decorated and fantasy builds, grown from the canopy tree's fractal ({@code ornate/}). Its bones have their radii
 * by the pipe model and roots grown the same way; the thick wood is measured in octants and made of logs, stairs and slabs; the thin wood of walls, fences, slabs,
 * stairs, trapdoors and open gates chosen so that no two blocks of a branch leave a gap; bark decoration on the trunk and roots; shading by ambient occlusion,
 * curvature and a point light, put into blocks of lighter and darker colours when the tree is put ({@link WoodPalette}).
 *
 * <p>The blocks of a part are the ones chosen for it ({@link TreePart#BRANCH_STAIR} for every stair, and so on); a block's exact state (the way a stair faces, the arms of a
 * fence) and its light level go with the blocks ({@link Blocks}), and {@link ThinTree#shape} asks them for it.
 */
final class OrnateTree {

    private OrnateTree() {
    }

    static String styleName(int index) {
        return switch (index) {
            case SkeletonGenerator.BONSAI -> "盆栽・松 (横に広がる)";
            case SkeletonGenerator.SPRUCE -> "トウヒ";
            case SkeletonGenerator.BIRCH -> "白樺";
            case SkeletonGenerator.WILLOW -> "柳";
            case SkeletonGenerator.CHERRY -> "桜";
            case SkeletonGenerator.ACACIA -> "アカシア";
            default -> "オーク (装飾・ファンタジー)";
        };
    }

    /**
     * (t286) The shape numbers a style starts from (the menu sets them when the style is changed; the blocks, the shading and the roots' snap are left as they are):
     * height, trunk, curl, roots, root reach, branches, depth, length, spread, droop, tip curl, leaf, leaf flatness, hang, hang length.
     */
    private static final double[][] STYLE_NUMBERS = {
            {10, 2.25, 0.6, 6, 7, 3, 4, 9, 42, 0.0, 0.7, 2.5, 0.35, 0.0, 5},
            {13, 1.5, 0.6, 4, 4, 3, 3, 7, 35, 0.1, 0.2, 1.75, 0.8, 0.0, 5},
            {20, 1.25, 0.15, 5, 4, 4, 2, 6, 50, 0.2, 0.3, 1.5, 0.5, 0.0, 5},
            {16, 0.9, 0.25, 3, 3, 3, 2, 6, 25, 0.0, 0.3, 2.0, 0.3, 0.0, 5},
            {6, 1.75, 0.5, 5, 5, 4, 3, 7, 60, 0.6, 0.0, 1.5, 0.3, 0.8, 6},
            {6, 1.5, 0.6, 5, 5, 3, 4, 8, 60, 0.1, 0.4, 2.25, 0.55, 0.15, 3},
            {8, 1.25, 0.5, 4, 4, 3, 2, 7, 40, 0.0, 0.0, 3.0, 0.85, 0.0, 5},
    };

    private static final TreeSettings.Key[] STYLE_KEYS = {TreeSettings.Key.ORN_HEIGHT, TreeSettings.Key.ORN_TRUNK, TreeSettings.Key.ORN_CURL, TreeSettings.Key.ORN_ROOTS,
            TreeSettings.Key.ORN_ROOT_REACH, TreeSettings.Key.ORN_BRANCHES, TreeSettings.Key.ORN_DEPTH, TreeSettings.Key.ORN_LENGTH, TreeSettings.Key.ORN_SPREAD,
            TreeSettings.Key.ORN_DROOP, TreeSettings.Key.ORN_TIP_CURL, TreeSettings.Key.ORN_LEAF, TreeSettings.Key.ORN_LEAF_FLAT, TreeSettings.Key.ORN_HANG,
            TreeSettings.Key.ORN_HANG_LENGTH};

    /** Sets the shape numbers of {@code style} (see {@link #STYLE_NUMBERS}). */
    static void applyStyle(TreeSettings s, int style) {
        double[] numbers = STYLE_NUMBERS[Math.max(0, Math.min(STYLE_NUMBERS.length - 1, style))];
        for (int index = 0; index < STYLE_KEYS.length; index++) s.set(STYLE_KEYS[index], numbers[index]);
    }

    static OrnateBuilder.Params params(TreeSettings s) {
        return params(s, null);
    }

    /** {@code ground}: the terrain round the foot (null: not read; the roots are then grown as on flat ground and the strands stop at the foot's height). */
    static OrnateBuilder.Params params(TreeSettings s, TreeGround ground) {
        SkeletonGenerator.Params bones = new SkeletonGenerator.Params(s.getInt(TreeSettings.Key.ORN_STYLE), s.get(TreeSettings.Key.ORN_HEIGHT),
                s.get(TreeSettings.Key.ORN_TRUNK), s.get(TreeSettings.Key.ORN_CURL), s.getInt(TreeSettings.Key.ORN_ROOTS), s.get(TreeSettings.Key.ORN_ROOT_REACH),
                s.getInt(TreeSettings.Key.ORN_BRANCHES), s.getInt(TreeSettings.Key.ORN_DEPTH), s.get(TreeSettings.Key.ORN_LENGTH), s.get(TreeSettings.Key.ORN_SPREAD),
                s.get(TreeSettings.Key.ORN_DROOP), s.get(TreeSettings.Key.ORN_TIP_CURL), s.get(TreeSettings.Key.ORN_PIPE));
        net.tkgon.mc.iruuRPG.tools.procedural.ornate.Ground terrain = ground == null ? null : (x, z) -> {
            int top = ground.top(x, z);
            return top == TreeGround.NONE ? net.tkgon.mc.iruuRPG.tools.procedural.ornate.Ground.NONE : top;
        };
        return new OrnateBuilder.Params(bones, s.get(TreeSettings.Key.ORN_FLARE), s.get(TreeSettings.Key.ORN_DECOR), s.get(TreeSettings.Key.ORN_AO),
                s.get(TreeSettings.Key.ORN_CURVATURE), s.get(TreeSettings.Key.ORN_LIGHT), s.get(TreeSettings.Key.ORN_LIGHT_DIR), s.get(TreeSettings.Key.ORN_LEAF),
                s.get(TreeSettings.Key.ORN_LEAF_FLAT), s.get(TreeSettings.Key.ORN_LEAF_DENSITY), s.get(TreeSettings.Key.ORN_STAIR_MIX), s.get(TreeSettings.Key.ORN_HANG),
                s.get(TreeSettings.Key.ORN_HANG_LENGTH), terrain, s.on(TreeSettings.Key.ORN_ROOT_SNAP));
    }

    /** Whether the tree needs the terrain read round its foot: its roots are laid on the ground, or strands hang down to it. */
    static boolean readsGround(TreeSettings s) {
        return s.kind().tree() && s.grows("ornate") && (s.on(TreeSettings.Key.ORN_ROOT_SNAP) || s.get(TreeSettings.Key.ORN_HANG) > 0.0);
    }

    static Blocks build(TreeSettings s, Random random) {
        return build(s, random, null);
    }

    static Blocks build(TreeSettings s, Random random, TreeGround ground) {
        OrnateBuilder.Result result;
        try {
            result = OrnateBuilder.build(params(s, ground), random);
        } catch (OrnateBuilder.TooBig exception) {
            throw new TreeVoxelizer.TooBig();
        }
        Blocks blocks = new Blocks(s.get(TreeSettings.Key.ORN_SHADE_STEP), s.on(TreeSettings.Key.ORN_MATCH), result.stats(), s.hangBlocks());
        for (Map.Entry<Long, OrnateBuilder.Wood> entry : result.wood().entrySet()) {
            OrnateBuilder.Wood wood = entry.getValue();
            blocks.put(entry.getKey(), part(wood));
            blocks.looks.put(entry.getKey(), new Look(form(wood.shape().kind), wood.props(), wood.level(), wood.part() == Skeleton.ROOT));
        }
        int shades = s.leafShades();
        for (Map.Entry<Long, Double> entry : result.leaves().entrySet()) {
            long key = entry.getKey();
            blocks.put(key, TreePart.leafAt(entry.getValue(), shades, TreeVoxelizer.jitter(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key), TreeVoxelizer.unpackZ(key))));
        }
        // the hanging strands: the blocks of the list mixed (the same block for the same place), or the lowest leaf
        for (Map.Entry<Long, Double> entry : result.hang().entrySet()) {
            long key = entry.getKey();
            if (blocks.containsKey(key)) continue;
            blocks.put(key, TreePart.LEAF);
            if (!blocks.hang.isEmpty()) {
                double pick = TreeVoxelizer.jitter(TreeVoxelizer.unpackX(key) * 7 + 3, TreeVoxelizer.unpackY(key) * 5 + 1, TreeVoxelizer.unpackZ(key) * 3 + 2) * 0.5 + 0.5;
                int index = Math.min(blocks.hang.size() - 1, (int) Math.floor(pick * blocks.hang.size()));
                blocks.looks.put(key, new Look(null, "", index, false));
            }
        }
        if (blocks.size() > TreeVoxelizer.LIMIT) throw new TreeVoxelizer.TooBig();
        return blocks;
    }

    /** The part a block of wood is (its block is the one chosen for the part). */
    static TreePart part(OrnateBuilder.Wood wood) {
        return switch (wood.shape().kind) {
            case STAIRS -> TreePart.BRANCH_STAIR;
            case SLAB -> TreePart.BRANCH_SLAB;
            case FENCE -> TreePart.BRANCH_FENCE;
            case WALL -> TreePart.BRANCH_WALL;
            case TRAPDOOR -> TreePart.BRANCH_TRAPDOOR;
            case GATE -> TreePart.BRANCH_GATE;
            default -> wood.part() == Skeleton.ROOT ? TreePart.ROOT : wood.part() == Skeleton.TRUNK ? TreePart.TRUNK : TreePart.BRANCH;
        };
    }

    static WoodPalette.Form form(BlockShapes.Kind kind) {
        return switch (kind) {
            case STAIRS -> WoodPalette.Form.STAIRS;
            case SLAB -> WoodPalette.Form.SLAB;
            case FENCE -> WoodPalette.Form.FENCE;
            case WALL -> WoodPalette.Form.WALL;
            case TRAPDOOR -> WoodPalette.Form.TRAPDOOR;
            case GATE -> WoodPalette.Form.GATE;
            default -> WoodPalette.Form.LOG;
        };
    }

    /**
     * What a block of wood is beyond its part: the shape its block comes in, its states, its light level, and whether it is of a root. A block of a hanging strand has
     * no form, and its level is the place of its block in the list of the strands' blocks.
     */
    record Look(WoodPalette.Form form, String props, int level, boolean root) {
    }

    /**
     * The blocks of an ornate tree, by part as every tree's are, with the look of each block of wood. When the tree is shown or put, the blocks chosen for the parts are
     * given ({@link #useParts}), and {@link #data} makes each block: the block of its shape for its light level (from the palette: near the trunk's colour when
     * {@code match}, else near the part's own block, lighter or darker by {@code step} a level), with its states.
     */
    static final class Blocks extends HashMap<Long, TreePart> {

        final Map<Long, Look> looks = new HashMap<>();
        final double step;
        final boolean match;
        final OrnateBuilder.Stats stats;
        /** The blocks of the hanging strands (names; empty: the strands are leaves). */
        final java.util.List<String> hang;
        private Map<TreePart, BlockData> parts;
        private final Map<String, BlockData> made = new HashMap<>();

        Blocks(double step, boolean match, OrnateBuilder.Stats stats, java.util.List<String> hang) {
            this.step = step;
            this.match = match;
            this.stats = stats;
            this.hang = java.util.List.copyOf(hang);
        }

        /** The blocks chosen for the parts (the trunk's colour is the reference of the others when {@code match}). */
        void useParts(Map<TreePart, BlockData> chosen) {
            parts = chosen;
            made.clear();
        }

        /** The block at (x, y, z) of the tree, or null when it is not wood of the ornate tree (leaves, and anything without a look). */
        BlockData data(BlockData own, TreePart part, int x, int y, int z) {
            Look look = looks.get(TreeVoxelizer.pack(x, y, z));
            if (look == null || own == null) return null;
            if (look.form() == null) return strand(look.level());
            double[] reference = null;
            if (match && parts != null) {
                BlockData trunk = parts.get(look.root() ? TreePart.ROOT : TreePart.TRUNK);
                reference = trunk == null ? null : WoodPalette.colour(trunk.getMaterial());
                if (reference == null && trunk != null && look.root()) {
                    BlockData main = parts.get(TreePart.TRUNK);
                    reference = main == null ? null : WoodPalette.colour(main.getMaterial());
                }
            }
            Material material = WoodPalette.pick(look.form(), own.getMaterial(), look.level(), step, reference);
            String key = material.name() + "[" + look.props() + "]";
            BlockData data = made.get(key);
            if (data == null) {
                data = create(material, look.props(), own);
                made.put(key, data);
            }
            return data;
        }

        /** The block {@code index} of the strands' list (leaves do not decay; a name that is no block: none). */
        private BlockData strand(int index) {
            if (index < 0 || index >= hang.size()) return null;
            String name = hang.get(index);
            BlockData data = made.get("strand:" + name);
            if (data == null) {
                try {
                    data = Bukkit.createBlockData(name.toLowerCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    return null;
                }
                if (data instanceof org.bukkit.block.data.type.Leaves leaves) leaves.setPersistent(true);
                made.put("strand:" + name, data);
            }
            return data;
        }

        private static BlockData create(Material material, String props, BlockData own) {
            if (!props.isEmpty()) {
                try {
                    return Bukkit.createBlockData(material, "[" + props + "]");
                } catch (IllegalArgumentException exception) {
                    // (a block chosen for the part that has no such states: as it is)
                }
            }
            if (material == own.getMaterial()) return own;
            try {
                return material.createBlockData();
            } catch (IllegalArgumentException exception) {
                return own;
            }
        }
    }
}
