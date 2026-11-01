package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.Map;
import java.util.Random;

/** One object from its settings, a seed and the terrain: a tree by its algorithm, a rock, a spike, a crystal or a web. Arithmetic only, so it runs off the main thread. */
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
        return switch (settings.algorithm()) {
            case "tiered" -> TieredTree.build(settings, random);
            case "palm" -> PalmTree.build(settings, random);
            default -> CanopyTree.build(settings, random);
        };
    }

    /** How far from the base the terrain is read (a rock is settled on it; a tree does not read it). */
    static int groundRadius(TreeSettings settings) {
        return Math.min(24, (int) Math.ceil(settings.width() / 2.0) + 3);
    }
}
