package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.silhouette.FoliageParams;
import net.tkgon.mc.iruuRPG.tools.procedural.silhouette.Palette;
import net.tkgon.mc.iruuRPG.tools.procedural.silhouette.Shapes;
import net.tkgon.mc.iruuRPG.tools.procedural.silhouette.SilhouetteGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The silhouette trees: the tree generator the user sent ({@code silhouette/}, mc_tree_gen) as an algorithm of the tool. A shape (round, weeping,
 * conifer, swoop, dome) is drawn first as a skeleton of curved limbs of a radius and quantized at a 4x4x4 sampling of each block into logs (lying along
 * x or z where a limb lies along them, bark blocks where it slants), stairs and slabs (on the side the wood is), walls and fences (the thin limbs); its
 * leaves are metaball clouds cut flat underneath, eroded and with tufts, shaded from above with an ordered dither, and drip down from the outer edge. It
 * is made with a palette of names ({@link Palette#symbols}) and each name is turned here into a part of the tool, so the blocks are the ones chosen for the
 * parts and the leaf gradient (four leaf tiers, the brightest at the top of the gradient).
 */
final class SilhouetteTree {

    private SilhouetteTree() {
    }

    /** The shapes, in the order of the setting ({@code sil-shape}). */
    static final List<String> SHAPES = List.of("round", "weeping", "conifer", "swoop", "dome");

    static String shapeName(int index) {
        return switch (index) {
            case 1 -> "しだれ (柳)";
            case 2 -> "針葉樹 (松)";
            case 3 -> "流れる幹 (盆栽・桜)";
            case 4 -> "傘 (藤)";
            default -> "丸い広葉樹 (オーク)";
        };
    }

    static Map<Long, TreePart> build(TreeSettings s, Random random) {
        Shapes.Shape shape = Shapes.get(SHAPES.get(Math.max(0, Math.min(SHAPES.size() - 1, s.getInt(TreeSettings.Key.SIL_SHAPE)))));
        int height = s.getInt(TreeSettings.Key.SIL_HEIGHT);
        if (height <= 0) height = shape.defaultHeight();
        double leaf = s.get(TreeSettings.Key.SIL_LEAF);
        double rough = s.get(TreeSettings.Key.SIL_ROUGH);
        double drip = s.get(TreeSettings.Key.SIL_DRIP);
        Palette own = Palette.get(shape.defaultPalette());
        Palette symbols = Palette.symbols(own == null || own.wall() != null);
        SilhouetteGenerator.Result result = SilhouetteGenerator.generate(shape, symbols, height, random.nextLong(), foliage -> new FoliageParams(foliage.blobs(),
                foliage.spread(), Math.max(0.02, foliage.threshold() - 0.06 * leaf), foliage.noiseAmp(), foliage.noiseScale(), Math.min(0.9, foliage.erode() * rough),
                Math.min(0.5, foliage.protrude() * rough), Math.min(0.95, foliage.dripChance() * drip), foliage.dripMin(),
                Math.max(foliage.dripMin(), (int) Math.round(foliage.dripMax() * Math.max(0.5, Math.min(2.0, drip)))), foliage.dripOuter()));
        int shades = Math.max(1, s.leafShades());
        Map<Long, TreePart> blocks = new HashMap<>();
        for (Map.Entry<SilhouetteGenerator.BlockPos, String> entry : result.blocks().entrySet()) {
            TreePart part = part(entry.getValue(), shades);
            if (part == null) continue;
            SilhouetteGenerator.BlockPos at = entry.getKey();
            blocks.put(TreeVoxelizer.pack(at.x(), at.y(), at.z()), part);
            if (blocks.size() > TreeVoxelizer.LIMIT) throw new TreeVoxelizer.TooBig();
        }
        return blocks;
    }

    /** The part of a block the generator names (null: none known). Pure, for the tests. */
    static TreePart part(String state, int shades) {
        String name = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
        String states = state.contains("[") ? state.substring(state.indexOf('[') + 1, state.lastIndexOf(']')) : "";
        Map<String, String> values = new HashMap<>();
        for (String pair : states.split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) values.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
        }
        switch (name) {
            case "log" -> {
                return switch (values.getOrDefault("axis", "y")) {
                    case "x" -> TreePart.TRUNK_X;
                    case "z" -> TreePart.TRUNK_Z;
                    default -> TreePart.TRUNK;
                };
            }
            case "wood" -> {
                return TreePart.BRANCH;
            }
            case "slab" -> {
                return "top".equals(values.get("type")) ? TreePart.BRANCH_SLAB_TOP : TreePart.BRANCH_SLAB;
            }
            case "stairs" -> {
                boolean top = "top".equals(values.get("half"));
                return switch (values.getOrDefault("facing", "north")) {
                    case "east" -> top ? TreePart.BRANCH_STAIR_TOP_EAST : TreePart.BRANCH_STAIR_EAST;
                    case "south" -> top ? TreePart.BRANCH_STAIR_TOP_SOUTH : TreePart.BRANCH_STAIR_SOUTH;
                    case "west" -> top ? TreePart.BRANCH_STAIR_TOP_WEST : TreePart.BRANCH_STAIR_WEST;
                    default -> top ? TreePart.BRANCH_STAIR_TOP : TreePart.BRANCH_STAIR;
                };
            }
            case "wall" -> {
                return TreePart.BRANCH_WALL;
            }
            case "fence" -> {
                return TreePart.BRANCH_FENCE;
            }
            case "hang" -> {
                return TreePart.leaf(0);
            }
            case "hangtip" -> {
                return TreePart.leaf(Math.min(1, shades - 1));
            }
            default -> {
                if (!name.startsWith("leaf:")) return null;
                // (the generator's tiers run from the brightest (0) to the darkest (3); the gradient from the bottom (0) to the top)
                int tier;
                try {
                    tier = Integer.parseInt(name.substring(5));
                } catch (NumberFormatException exception) {
                    return null;
                }
                int dark = Math.max(0, Math.min(3, tier));
                return TreePart.leaf((int) Math.round((3 - dark) * (shades - 1) / 3.0));
            }
        }
    }
}
