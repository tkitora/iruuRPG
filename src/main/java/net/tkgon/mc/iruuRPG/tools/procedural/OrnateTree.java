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
            case 1 -> "横に広がる (盆栽・松)";
            case 2 -> "自然木 (空間植民)";
            case 3 -> "枯れ木";
            default -> "装飾 (ファンタジー)";
        };
    }

    static OrnateBuilder.Params params(TreeSettings s) {
        SkeletonGenerator.Params bones = new SkeletonGenerator.Params(s.getInt(TreeSettings.Key.ORN_STYLE), s.get(TreeSettings.Key.ORN_HEIGHT),
                s.get(TreeSettings.Key.ORN_TRUNK), s.get(TreeSettings.Key.ORN_CURL), s.getInt(TreeSettings.Key.ORN_ROOTS), s.get(TreeSettings.Key.ORN_ROOT_REACH),
                s.getInt(TreeSettings.Key.ORN_BRANCHES), s.getInt(TreeSettings.Key.ORN_DEPTH), s.get(TreeSettings.Key.ORN_LENGTH), s.get(TreeSettings.Key.ORN_SPREAD),
                s.get(TreeSettings.Key.ORN_DROOP), s.get(TreeSettings.Key.ORN_TIP_CURL), s.get(TreeSettings.Key.ORN_PIPE));
        return new OrnateBuilder.Params(bones, s.get(TreeSettings.Key.ORN_FLARE), s.get(TreeSettings.Key.ORN_DECOR), s.get(TreeSettings.Key.ORN_AO),
                s.get(TreeSettings.Key.ORN_CURVATURE), s.get(TreeSettings.Key.ORN_LIGHT), s.get(TreeSettings.Key.ORN_LIGHT_DIR), s.get(TreeSettings.Key.ORN_LEAF),
                s.get(TreeSettings.Key.ORN_LEAF_FLAT), s.get(TreeSettings.Key.ORN_LEAF_DENSITY));
    }

    static Blocks build(TreeSettings s, Random random) {
        OrnateBuilder.Result result;
        try {
            result = OrnateBuilder.build(params(s), random);
        } catch (OrnateBuilder.TooBig exception) {
            throw new TreeVoxelizer.TooBig();
        }
        Blocks blocks = new Blocks(s.get(TreeSettings.Key.ORN_SHADE_STEP), s.on(TreeSettings.Key.ORN_MATCH), result.stats());
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

    /** What a block of wood is beyond its part: the shape its block comes in, its states, its light level, and whether it is of a root. */
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
        private Map<TreePart, BlockData> parts;
        private final Map<String, BlockData> made = new HashMap<>();

        Blocks(double step, boolean match, OrnateBuilder.Stats stats) {
            this.step = step;
            this.match = match;
            this.stats = stats;
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
