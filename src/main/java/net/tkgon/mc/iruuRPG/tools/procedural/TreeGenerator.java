package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.Map;
import java.util.Random;

/** One tree from its settings, a seed and the terrain: skeleton, the fork of the trunk, blocks. Arithmetic only, so it runs off the main thread. */
final class TreeGenerator {

    private TreeGenerator() {
    }

    static Map<Long, TreePart> build(TreeSettings settings, long seed, TreeGround ground) {
        return build(settings, seed, ground, null);
    }

    /** {@code aim}: the way a spike or a crystal points (null: straight up). */
    static Map<Long, TreePart> build(TreeSettings settings, long seed, TreeGround ground, org.bukkit.util.Vector aim) {
        return build(settings, seed, ground, aim, null);
    }

    /** {@code web}: the points and the terrain of a web (null for anything else). */
    static Map<Long, TreePart> build(TreeSettings settings, long seed, TreeGround ground, org.bukkit.util.Vector aim, WebInput web) {
        Random random = new Random(seed);
        if (settings.kind().web()) return WebGenerator.build(settings, web, random);
        if (!settings.kind().tree()) return ProceduralShapes.build(settings.kind(), settings, seed, random, ground, aim);
        TreeSkeleton skeleton = TreeAlgorithm.of(settings.algorithm()).grow(settings, random);
        if (!"conifer".equals(settings.algorithm())) TrunkFork.apply(skeleton, settings, random);
        TreeShape.apply(skeleton, settings, random);
        return TreeVoxelizer.voxelize(skeleton, settings, random, ground);
    }

    /** How far from the base the terrain is read (the roots reach about 3.5 trunk radii and a little more). */
    static int groundRadius(TreeSettings settings) {
        double trunk = settings.get(TreeSettings.Key.TRUNK_RADIUS) * (1.0 + settings.get(TreeSettings.Key.FLARE) * 0.3);
        return Math.min(24, (int) Math.ceil(trunk * 3.5 + 6.0));
    }
}
