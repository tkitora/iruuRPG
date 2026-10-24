package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A conifer, after the whorl structure that pine generators (pinegen and the like) use: a straight leader, and branches in whorls round it at regular heights
 * (three to six in a whorl, the whorls turned against each other so the branches do not line up), each whorl's branches shorter than the one below so the crown is a
 * cone, spreading nearly level and drooping, the tips curling up; short twigs leave each branch in turns on both sides, and needles (flattened clumps of leaves)
 * hang along the branches and twigs.
 */
final class ConiferAlgorithm implements TreeAlgorithm {

    @Override
    public TreeSkeleton grow(TreeSettings settings, Random random) {
        double top = TreeAlgorithm.reachUp(settings);
        double reach = TreeAlgorithm.reachOut(settings);
        double trunkRadius = settings.get(TreeSettings.Key.TRUNK_RADIUS);
        double taper = Math.max(0.6, settings.get(TreeSettings.Key.TAPER));
        double curve = settings.get(TreeSettings.Key.CURVE);
        double density = settings.get(TreeSettings.Key.DENSITY);
        double droop = settings.get(TreeSettings.Key.DROOP);
        double flatness = settings.get(TreeSettings.Key.LEAF_FLATNESS) * 0.65;
        double leaf = settings.get(TreeSettings.Key.LEAF_RADIUS);
        int levels = settings.getInt(TreeSettings.Key.LEVELS);
        double ratio = settings.get(TreeSettings.Key.TRUNK_RATIO);
        String crown = "ellipsoid".equals(settings.crown()) ? "cone" : settings.crown();
        double angle = Math.max(Math.toRadians(62.0), Math.min(Math.toRadians(105.0), Math.toRadians(settings.get(TreeSettings.Key.BRANCH_ANGLE) + 30.0)));
        int leafEvery = Math.max(1, (int) Math.round(leaf * 0.7));

        TreeSkeleton skeleton = new TreeSkeleton();
        double[] up = {0.0, 1.0, 0.0};
        TreeAlgorithm.Growth leaderGrowth = new TreeAlgorithm.Growth(curve * 0.04, 0.04, 0.0, top, reach, 0.07);
        List<Integer> leader = TreeAlgorithm.growStem(skeleton, -1, new double[]{0, 0, 0}, up, top, trunkRadius, Math.max(0.45, trunkRadius * (1.0 - taper)), 0, leaderGrowth, random);
        if (leader.size() < 4) {
            TreeAlgorithm.addRoots(skeleton, settings, random);
            return skeleton;
        }

        double bare = Math.max(2.0, Math.round(leader.size() * ratio * 0.6));
        double spacing = Math.max(1.4, Math.min(3.5, top / (6.0 + density * 5.0)));
        double azimuth = random.nextDouble() * Math.PI * 2.0;
        for (double y = bare; y < leader.size() - 1.5; y += spacing * (0.85 + random.nextDouble() * 0.3)) {
            double t = (y - bare) / Math.max(1.0, leader.size() - 1.5 - bare);
            int at = (int) Math.min(leader.size() - 2, Math.round(y));
            TreeSkeleton.Node origin = skeleton.nodes.get(leader.get(at));
            int whorl = Math.max(3, (int) Math.round(5.0 - 2.0 * t + (random.nextDouble() - 0.5)));
            azimuth += Math.toRadians(25.0 + random.nextDouble() * 40.0);
            double base = Math.max(1.5, reach * TreeAlgorithm.shape(crown, t) * (crown.equals("cone") ? 1.0 : 0.9));
            for (int index = 0; index < whorl; index++) {
                double length = Math.max(1.4, base * (0.85 + random.nextDouble() * 0.3));
                double theta = azimuth + Math.PI * 2.0 * index / whorl + (random.nextDouble() - 0.5) * 0.4;
                double[] direction = TreeAlgorithm.branchDirection(up, angle + (random.nextDouble() - 0.5) * 0.2, theta);
                // the branches sag, and their tips curl up
                TreeAlgorithm.Growth growth = new TreeAlgorithm.Growth(curve * 0.1, -0.06 - droop * 0.1, 0.16, top, reach, 0.0);
                List<Integer> branch = TreeAlgorithm.growStem(skeleton, leader.get(at), new double[]{origin.x, origin.y, origin.z}, direction, length,
                        Math.max(0.55, origin.radius * 0.55), 0.45, 1, growth, random);
                if (branch.size() < 2) continue;
                // twigs in turns on both sides, shorter toward the tip
                if (levels >= 2) {
                    int side = random.nextBoolean() ? 1 : -1;
                    for (int node = 1; node < branch.size() - 1; node += 1) {
                        if (random.nextDouble() > 0.55 + 0.25 * density / 3.0) continue;
                        double u = node / (double) branch.size();
                        TreeSkeleton.Node point = skeleton.nodes.get(branch.get(node));
                        double[] tangent = TreeAlgorithm.tangentAt(skeleton, branch, node, direction);
                        double[] twig = TreeAlgorithm.branchDirection(tangent, Math.toRadians(55.0 + random.nextDouble() * 15.0), side > 0 ? 0.4 : Math.PI + 0.4);
                        side = -side;
                        double twigLength = Math.max(1.0, Math.min(3.5, length * (0.32 - 0.18 * u) * (0.8 + random.nextDouble() * 0.4)));
                        List<Integer> made = TreeAlgorithm.growStem(skeleton, branch.get(node), new double[]{point.x, point.y, point.z}, twig, twigLength,
                                0.45, 0.45, 2, new TreeAlgorithm.Growth(curve * 0.1, -0.05, 0.08, top, reach, 0.0), random);
                        if (!made.isEmpty()) TreeAlgorithm.leafAlong(skeleton, made, leafEvery, flatness, random, 0.8);
                    }
                }
                TreeAlgorithm.leafAlong(skeleton, branch, leafEvery, flatness, random, levels >= 2 ? 0.8 : 0.95);
            }
        }
        // the top: needles round the tip of the leader
        List<Integer> upper = new ArrayList<>(leader.subList(Math.max(0, leader.size() - 3), leader.size()));
        TreeAlgorithm.leafAlong(skeleton, upper, 1, flatness, random, 0.9);
        TreeAlgorithm.addRoots(skeleton, settings, random);
        return skeleton;
    }
}
