package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Changes the shape of a grown skeleton, for every algorithm alike: the length and the thickness of the branches (everything that is not the main stem), and the lean, the
 * twist and the bulge of the trunk. The branches are scaled from where they leave the stem (a branch moves with the branch it grows from), the leaves go with the branches
 * they hang on, and the trunk's lean and twist are a bend of the whole tree by height, so everything on the trunk follows it.
 */
final class TreeShape {

    private TreeShape() {
    }

    /** The nodes of the main stem: the ones the algorithm calls trunk, and the path from the foot always into the heavier child. */
    private static boolean[] mainStem(TreeSkeleton skeleton) {
        List<TreeSkeleton.Node> nodes = skeleton.nodes;
        boolean[] stem = new boolean[nodes.size()];
        int[] weight = new int[nodes.size()];
        for (int index = nodes.size() - 1; index >= 0; index--) {
            weight[index] += 1;
            int parent = nodes.get(index).parent;
            if (parent >= 0) weight[parent] += weight[index];
        }
        List<List<Integer>> kids = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) kids.add(new ArrayList<>());
        for (int index = 0; index < nodes.size(); index++) if (nodes.get(index).parent >= 0) kids.get(nodes.get(index).parent).add(index);
        int at = 0;
        while (!nodes.isEmpty()) {
            stem[at] = true;
            int heavy = -1;
            for (int child : kids.get(at)) if (heavy < 0 || weight[child] > weight[heavy]) heavy = child;
            if (heavy < 0) break;
            at = heavy;
        }
        for (int index = 0; index < nodes.size(); index++) if (nodes.get(index).level == 0) stem[index] = true;
        return stem;
    }

    static void apply(TreeSkeleton skeleton, TreeSettings settings, Random random) {
        double length = settings.get(TreeSettings.Key.BRANCH_LENGTH);
        double thickness = settings.get(TreeSettings.Key.BRANCH_THICKNESS);
        double lean = settings.get(TreeSettings.Key.TRUNK_LEAN);
        double twist = settings.get(TreeSettings.Key.TRUNK_TWIST);
        double bulge = settings.get(TreeSettings.Key.TRUNK_BULGE);
        List<TreeSkeleton.Node> nodes = skeleton.nodes;
        if (nodes.size() < 2) return;
        boolean changesBranches = Math.abs(length - 1.0) > 1.0E-6 || Math.abs(thickness - 1.0) > 1.0E-6;
        if (!changesBranches && lean <= 0.0 && twist <= 0.0 && bulge <= 0.0) return;
        boolean[] stem = mainStem(skeleton);

        // ---- the branches: length (from where they leave their parent), thickness
        if (changesBranches) {
            double[] oldX = new double[nodes.size()];
            double[] oldY = new double[nodes.size()];
            double[] oldZ = new double[nodes.size()];
            for (int index = 0; index < nodes.size(); index++) {
                TreeSkeleton.Node node = nodes.get(index);
                oldX[index] = node.x;
                oldY[index] = node.y;
                oldZ[index] = node.z;
            }
            for (int index = 0; index < nodes.size(); index++) {
                TreeSkeleton.Node node = nodes.get(index);
                if (node.parent < 0 || stem[index]) continue;
                TreeSkeleton.Node parent = nodes.get(node.parent);
                node.x = parent.x + (oldX[index] - oldX[node.parent]) * length;
                node.y = Math.max(0.3, parent.y + (oldY[index] - oldY[node.parent]) * length);
                node.z = parent.z + (oldZ[index] - oldZ[node.parent]) * length;
                node.radius = Math.max(0.5, node.radius * thickness);
            }
            // a clump of leaves goes with the nearest node (the end of the branch it hangs on)
            if (Math.abs(length - 1.0) > 1.0E-6 && !skeleton.leaves.isEmpty()) {
                int stride = Math.max(1, nodes.size() * skeleton.leaves.size() / 20_000_000);
                for (double[] leaf : skeleton.leaves) {
                    int best = -1;
                    double bestDistance = Double.MAX_VALUE;
                    for (int index = 0; index < nodes.size(); index += stride) {
                        double dx = leaf[0] - oldX[index];
                        double dy = leaf[1] - oldY[index];
                        double dz = leaf[2] - oldZ[index];
                        double d = dx * dx + dy * dy + dz * dz;
                        if (d < bestDistance) {
                            bestDistance = d;
                            best = index;
                        }
                    }
                    if (best < 0) continue;
                    TreeSkeleton.Node node = nodes.get(best);
                    leaf[0] += node.x - oldX[best];
                    leaf[1] += node.y - oldY[best];
                    leaf[2] += node.z - oldZ[best];
                }
            }
        }

        // ---- the trunk: lean and twist bend the whole tree by height; the bulge thickens the middle of the stem
        double top = Math.max(4.0, settings.height() * 0.9);
        if (lean > 0.0 || twist > 0.0) {
            double leanAngle = random.nextDouble() * Math.PI * 2.0;
            double phase = random.nextDouble() * Math.PI * 2.0;
            for (TreeSkeleton.Node node : nodes) {
                double[] shift = warp(node.y, top, lean, twist, leanAngle, phase);
                node.x += shift[0];
                node.z += shift[1];
            }
            for (double[] leaf : skeleton.leaves) {
                double[] shift = warp(leaf[1], top, lean, twist, leanAngle, phase);
                leaf[0] += shift[0];
                leaf[2] += shift[1];
            }
        }
        if (bulge > 0.0) {
            for (int index = 0; index < nodes.size(); index++) {
                if (!stem[index]) continue;
                TreeSkeleton.Node node = nodes.get(index);
                double t = Math.max(0.0, Math.min(1.0, node.y / top));
                node.radius *= 1.0 + bulge * 0.9 * Math.sin(Math.PI * t);
            }
        }
    }

    /** How far the lean and the twist move a point at height {@code y} (x and z), nothing at the ground. */
    private static double[] warp(double y, double top, double lean, double twist, double leanAngle, double phase) {
        double t = Math.max(0.0, Math.min(1.0, y / top));
        double lateral = lean * 0.4 * top * t * t;
        double x = Math.cos(leanAngle) * lateral;
        double z = Math.sin(leanAngle) * lateral;
        if (twist > 0.0) {
            double radius = twist * 1.6 * Math.pow(t, 0.7);
            double turn = phase + Math.PI * 2.0 * 1.5 * t;
            // (the radius is 0 at the ground, so the foot of the trunk stays where it is)
            x += Math.cos(turn) * radius;
            z += Math.sin(turn) * radius;
        }
        return new double[]{x, z};
    }
}
