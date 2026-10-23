package net.tkgon.mc.iruuRPG.tools.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A parametric tree in the manner of Weber and Penn ("Creation and Rendering of Realistic Trees", 1995), the model behind Blender's Sapling and Arbaro/treegen:
 * the trunk is a curving stem that thins toward the top; the branches of the first level leave it spiralling round by the golden angle (137.5 degrees), each
 * at the branch angle, with a length given by the shape of the crown (long in the widest part of the crown, short at the ends); every stem has the stems of
 * the next level on it, shorter and thinner, up to three levels (trunk, branches, twigs, small twigs); every stem curves a little at random and is pulled up
 * (or down, by the droop setting) a little each step. Leaves hang on the last level. The result is the usual forked, layered tree, and the numbers of the
 * settings (angle, count, levels, curve, taper, flare) are the ones such tools have.
 */
final class ParametricAlgorithm implements TreeAlgorithm {

    private static final double GOLDEN = Math.toRadians(137.5);

    @Override
    public TreeSkeleton grow(TreeSettings settings, Random random) {
        double top = TreeAlgorithm.reachUp(settings);
        double reach = TreeAlgorithm.reachOut(settings);
        double trunkRadius = settings.get(TreeSettings.Key.TRUNK_RADIUS);
        double taper = settings.get(TreeSettings.Key.TAPER);
        double curve = settings.get(TreeSettings.Key.CURVE);
        double density = settings.get(TreeSettings.Key.DENSITY);
        double droop = settings.get(TreeSettings.Key.DROOP);
        double flatness = settings.get(TreeSettings.Key.LEAF_FLATNESS);
        double leaf = settings.get(TreeSettings.Key.LEAF_RADIUS);
        int levels = settings.getInt(TreeSettings.Key.LEVELS);
        int count = settings.getInt(TreeSettings.Key.BRANCHES);
        double ratio = settings.get(TreeSettings.Key.TRUNK_RATIO);
        double angle = Math.toRadians(settings.get(TreeSettings.Key.BRANCH_ANGLE));
        String crown = settings.crown();
        int leafEvery = Math.max(1, (int) Math.round(leaf * 0.8));

        TreeSkeleton skeleton = new TreeSkeleton();
        // the trunk: leans a little to one side and wanders, stays upright on the whole
        double lean = Math.min(curve * Math.toRadians(14.0), Math.atan2(reach * 0.4, top)) * (0.4 + random.nextDouble() * 0.6);
        double leanAzimuth = random.nextDouble() * Math.PI * 2.0;
        double[] up = {Math.sin(lean) * Math.cos(leanAzimuth), Math.cos(lean), Math.sin(lean) * Math.sin(leanAzimuth)};
        double tipRadius = Math.max(0.5, trunkRadius * (1.0 - taper));
        // the trunk is as long as the box allows, and grows past the top of the last branch a little
        TreeAlgorithm.Growth trunkGrowth = new TreeAlgorithm.Growth(curve * 0.09, 0.035, 0.0, top, reach, 0.07);
        List<Integer> trunk = TreeAlgorithm.growStem(skeleton, -1, new double[]{0, 0, 0}, up, top, trunkRadius, tipRadius, 0, trunkGrowth, random);
        if (levels <= 0 || trunk.size() < 3) {
            finish(skeleton, settings, random);
            return skeleton;
        }

        // first-level branches: along the trunk above its bare part, spiralling round
        List<List<Integer>> parents = new ArrayList<>();
        List<double[]> parentInfo = new ArrayList<>();
        double bare = Math.max(1.0, ratio * trunk.size());
        double azimuth = random.nextDouble() * Math.PI * 2.0;
        for (int index = 0; index < count; index++) {
            double t = (index + 0.3 + random.nextDouble() * 0.5) / count;
            int at = (int) Math.min(trunk.size() - 2, Math.round(bare + t * (trunk.size() - 1 - bare)));
            azimuth += GOLDEN + (random.nextDouble() - 0.5) * 0.5;
            double down = angle + (random.nextDouble() - 0.5) * Math.toRadians(20.0);
            double length = Math.max(2.0, reach * 1.05 * TreeAlgorithm.shape(crown, t) * (0.85 + random.nextDouble() * 0.3));
            TreeSkeleton.Node origin = skeleton.nodes.get(trunk.get(at));
            double[] tangent = TreeAlgorithm.tangentAt(skeleton, trunk, at, up);
            double[] direction = TreeAlgorithm.branchDirection(tangent, down, azimuth);
            TreeAlgorithm.Growth growth = new TreeAlgorithm.Growth(curve * 0.12, 0.05 - droop * 0.12, droop < 0 ? 0.03 : 0.0, top, reach, 0.0);
            List<Integer> stem = TreeAlgorithm.growStem(skeleton, trunk.get(at), new double[]{origin.x, origin.y, origin.z}, direction, length,
                    Math.max(0.55, origin.radius * 0.62), 0.45, 1, growth, random);
            if (stem.isEmpty()) continue;
            parents.add(stem);
            parentInfo.add(new double[]{length});
        }
        if (levels == 1) {
            for (List<Integer> stem : parents) TreeAlgorithm.leafAlong(skeleton, stem, leafEvery, flatness, random, 0.9);
            finish(skeleton, settings, random);
            return skeleton;
        }

        // the next levels: on every stem of the level before, spiralling, shorter toward the tip of the parent and shorter at every level
        List<List<Integer>> level = parents;
        List<double[]> info = parentInfo;
        for (int depth = 2; depth <= levels; depth++) {
            List<List<Integer>> next = new ArrayList<>();
            List<double[]> nextInfo = new ArrayList<>();
            boolean last = depth == levels;
            for (int p = 0; p < level.size(); p++) {
                if (skeleton.nodes.size() > TreeAlgorithm.MAX_NODES) break;
                List<Integer> stem = level.get(p);
                double parentLength = info.get(p)[0];
                int children = Math.max(stem.size() >= 4 ? 2 : 1, (int) Math.round(stem.size() * (depth == 2 ? 0.9 : 0.8) * density));
                if (stem.size() < 2) {
                    TreeAlgorithm.leafAlong(skeleton, stem, leafEvery, flatness, random, 0.9);
                    continue;
                }
                double childAzimuth = random.nextDouble() * Math.PI * 2.0;
                for (int child = 0; child < children; child++) {
                    double u = 0.25 + 0.75 * (child + 0.5) / children;
                    int at = (int) Math.min(stem.size() - 1, Math.floor(stem.size() * u));
                    childAzimuth += GOLDEN + (random.nextDouble() - 0.5) * 0.4;
                    double down = Math.toRadians((depth == 2 ? 50.0 : 55.0) + (random.nextDouble() - 0.5) * 24.0);
                    double length = Math.max(1.5, Math.min(depth == 2 ? 99.0 : 3.8, parentLength * (depth == 2 ? 0.62 : 0.6) * (1.0 - 0.45 * u) * (0.85 + random.nextDouble() * 0.3)));
                    TreeSkeleton.Node origin = skeleton.nodes.get(stem.get(at));
                    double[] tangent = TreeAlgorithm.tangentAt(skeleton, stem, at, new double[]{0, 1, 0});
                    double[] direction = TreeAlgorithm.branchDirection(tangent, down, childAzimuth);
                    TreeAlgorithm.Growth growth = new TreeAlgorithm.Growth(curve * 0.14, 0.04 - droop * 0.1, 0.0, top, reach, 0.0);
                    List<Integer> made = TreeAlgorithm.growStem(skeleton, stem.get(at), new double[]{origin.x, origin.y, origin.z}, direction, length,
                            Math.max(0.45, origin.radius * 0.7), 0.45, depth, growth, random);
                    if (made.isEmpty()) continue;
                    next.add(made);
                    nextInfo.add(new double[]{length});
                }
                // the stem keeps its own leaves too, where the children leave room
                if (last) TreeAlgorithm.leafAlong(skeleton, stem, Math.max(2, leafEvery * 2), flatness, random, 0.5);
            }
            level = next;
            info = nextInfo;
            if (last) {
                for (List<Integer> stem : level) TreeAlgorithm.leafAlong(skeleton, stem, leafEvery, flatness, random, 0.9);
            }
        }
        finish(skeleton, settings, random);
        return skeleton;
    }

    private static void finish(TreeSkeleton skeleton, TreeSettings settings, Random random) {
        TreeAlgorithm.addRoots(skeleton, settings, random);
    }
}
