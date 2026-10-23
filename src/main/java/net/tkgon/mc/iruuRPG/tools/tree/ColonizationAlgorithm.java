package net.tkgon.mc.iruuRPG.tools.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Space colonization (Runions, Lane and Prusinkiewicz, "Modeling Trees with a Space Colonization Algorithm", 2007). Points ("attractors") are scattered
 * in the volume the crown should fill. The tree is a set of nodes, first a bare trunk. Each round every attractor that is within reach of some node pulls
 * on the nearest one; every node pulled grows a new node a step away in the average direction of its attractors; an attractor that a node has come within
 * the kill distance of is removed. When no attractor is left in reach the tree is finished: its branches have grown toward the crown and stopped
 * where the space is used up, so the shape of the crown (the volume of the attractors) and the density of the points decide how it branches.
 *
 * <p>Cost: each attractor remembers its nearest node and is compared only with the nodes made since, so the whole run is attractors x nodes,
 * not attractors x nodes x rounds.
 */
final class ColonizationAlgorithm implements TreeAlgorithm {

    private static final double STEP = 1.0;
    private static final double KILL = 1.3;
    private static final int MAX_ROUNDS = 600;

    @Override
    public TreeSkeleton grow(TreeSettings settings, Random random) {
        double top = TreeAlgorithm.reachUp(settings);
        double reach = TreeAlgorithm.reachOut(settings);
        double ratio = settings.get(TreeSettings.Key.TRUNK_RATIO);
        String crown = settings.crown();
        double bareTop = Math.max(2.0, Math.round(top * ratio));
        double crownBase = bareTop;
        double verticalRadius = 0.0;
        if ("umbrella".equals(crown)) {
            // a flat dome on a long trunk: the trunk runs up to near the dome
            verticalRadius = Math.max(1.5, (top - bareTop) * 0.45);
            crownBase = Math.max(bareTop, top - verticalRadius * 2.0);
            bareTop = Math.max(bareTop, crownBase * 0.9);
        }
        double crownHeight = Math.max(2.0, top - crownBase);

        List<double[]> attractors = scatter(crown, reach, crownBase, top, crownHeight, verticalRadius, settings.get(TreeSettings.Key.DENSITY), random);
        TreeSkeleton skeleton = new TreeSkeleton();
        // the bare trunk
        int parent = -1;
        int steps = (int) Math.ceil(bareTop / STEP);
        // the trunk sways a little (the curve setting) instead of standing as a straight post
        double curve = settings.get(TreeSettings.Key.CURVE);
        double swayPhase = random.nextDouble() * Math.PI * 2.0;
        double swayDirection = random.nextDouble() * Math.PI * 2.0;
        for (int step = 0; step <= steps; step++) {
            double y = Math.min(bareTop, step * STEP);
            double sway = curve * 0.9 * Math.sin(y * 0.28 + swayPhase) * Math.min(1.0, y / 3.0);
            parent = skeleton.add(Math.cos(swayDirection) * sway, y, Math.sin(swayDirection) * sway, parent);
        }
        // the lower trunk does not branch: the nearest-node search skips nodes lower than this
        double minBranchY = bareTop * 0.6;
        double influence = Math.max(top, reach * 2.0) * 1.3;
        double droop = settings.get(TreeSettings.Key.DROOP);

        int count = attractors.size();
        int[] nearest = new int[count];
        double[] nearestDistance = new double[count];
        boolean[] alive = new boolean[count];
        Arrays.fill(nearest, -1);
        Arrays.fill(nearestDistance, Double.MAX_VALUE);
        Arrays.fill(alive, true);
        compare(skeleton, attractors, alive, nearest, nearestDistance, 0, minBranchY);

        for (int round = 0; round < MAX_ROUNDS; round++) {
            int size = skeleton.nodes.size();
            double[] sumX = new double[size];
            double[] sumY = new double[size];
            double[] sumZ = new double[size];
            int[] pulls = new int[size];
            for (int index = 0; index < count; index++) {
                if (!alive[index] || nearest[index] < 0) continue;
                if (nearestDistance[index] < KILL * KILL) {
                    alive[index] = false;
                    continue;
                }
                if (nearestDistance[index] > influence * influence) continue;
                TreeSkeleton.Node node = skeleton.nodes.get(nearest[index]);
                double[] point = attractors.get(index);
                double dx = point[0] - node.x;
                double dy = point[1] - node.y;
                double dz = point[2] - node.z;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                sumX[nearest[index]] += dx / length;
                sumY[nearest[index]] += dy / length;
                sumZ[nearest[index]] += dz / length;
                pulls[nearest[index]]++;
            }
            int before = size;
            for (int index = 0; index < before; index++) {
                if (pulls[index] == 0) continue;
                double dx = sumX[index] / pulls[index] + (random.nextDouble() - 0.5) * 0.25;
                double dy = sumY[index] / pulls[index] - droop * 0.35 + (random.nextDouble() - 0.5) * 0.25;
                double dz = sumZ[index] / pulls[index] + (random.nextDouble() - 0.5) * 0.25;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < 1.0E-6) continue;
                TreeSkeleton.Node node = skeleton.nodes.get(index);
                double x = node.x + dx / length * STEP;
                double y = node.y + dy / length * STEP;
                double z = node.z + dz / length * STEP;
                // never outside the box the tree is kept in
                if (y > top + 0.5 || y < 0.5 || x * x + z * z > (reach + 0.5) * (reach + 0.5)) continue;
                skeleton.add(x, y, z, index);
            }
            if (skeleton.nodes.size() == before) break;
            compare(skeleton, attractors, alive, nearest, nearestDistance, before, minBranchY);
        }
        skeleton.computeRadii(settings.get(TreeSettings.Key.TRUNK_RADIUS), 0.5, 0.45);
        TreeAlgorithm.taperBareTrunk(skeleton, steps, settings.get(TreeSettings.Key.TAPER));
        TreeAlgorithm.leafAtTips(skeleton, settings.get(TreeSettings.Key.LEAF_FLATNESS), random);
        TreeAlgorithm.addRoots(skeleton, settings, random);
        return skeleton;
    }

    /** Compares every attractor still alive with the nodes from {@code from} on, keeping the nearest. */
    private static void compare(TreeSkeleton skeleton, List<double[]> attractors, boolean[] alive, int[] nearest, double[] nearestDistance, int from, double minBranchY) {
        int size = skeleton.nodes.size();
        for (int index = 0; index < attractors.size(); index++) {
            if (!alive[index]) continue;
            double[] point = attractors.get(index);
            for (int node = from; node < size; node++) {
                TreeSkeleton.Node candidate = skeleton.nodes.get(node);
                if (candidate.y < minBranchY) continue;
                double dx = point[0] - candidate.x;
                double dy = point[1] - candidate.y;
                double dz = point[2] - candidate.z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < nearestDistance[index]) {
                    nearestDistance[index] = distance;
                    nearest[index] = node;
                }
            }
        }
    }

    /** Attractors in the volume of the crown: an ellipsoid, a cone (wide at the bottom) or a flat dome, as many as the density asks for. */
    private static List<double[]> scatter(String crown, double reach, double crownBase, double top, double crownHeight, double domeRadius, double density, Random random) {
        double volume = switch (crown) {
            case "cone" -> Math.PI * reach * reach * crownHeight / 3.0;
            case "columnar" -> Math.PI * Math.pow(reach * 0.55, 2) * crownHeight;
            case "umbrella" -> 4.0 / 3.0 * Math.PI * reach * reach * domeRadius;
            default -> 4.0 / 3.0 * Math.PI * reach * reach * crownHeight / 2.0;
        };
        int wanted = (int) Math.max(40, Math.min(2200, volume * 0.6 * density));
        List<double[]> points = new ArrayList<>(wanted);
        double centreY = switch (crown) {
            case "umbrella" -> top - domeRadius;
            default -> crownBase + crownHeight / 2.0;
        };
        int attempts = wanted * 40;
        while (points.size() < wanted && attempts-- > 0) {
            double x = (random.nextDouble() * 2.0 - 1.0) * reach;
            double z = (random.nextDouble() * 2.0 - 1.0) * reach;
            double y = crownBase + random.nextDouble() * crownHeight;
            double horizontal = x * x + z * z;
            boolean inside = switch (crown) {
                case "cone" -> {
                    double fraction = (y - crownBase) / crownHeight;
                    double allowed = reach * Math.pow(Math.max(0.0, 1.0 - fraction), 0.9);
                    yield horizontal <= allowed * allowed;
                }
                case "columnar" -> horizontal <= Math.pow(reach * 0.55, 2);
                case "umbrella" -> {
                    double dy = (y - centreY) / domeRadius;
                    yield horizontal / (reach * reach) + dy * dy <= 1.0;
                }
                default -> {
                    double dy = (y - centreY) / (crownHeight / 2.0);
                    yield horizontal / (reach * reach) + dy * dy <= 1.0;
                }
            };
            if (inside) points.add(new double[]{x, y, z});
        }
        return points;
    }
}
