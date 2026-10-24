package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The fork of the trunk, for every algorithm alike: after the skeleton is grown, the main stem (from the foot, always into the heavier child) is cut at the fork height
 * and {@code trunk-forks} more leaders leave that point, evenly round it, leaning out by the fork angle, each growing up to the top of the box as a trunk part with leaf
 * clumps along it. The tree it was grown as stays as the first leader.
 */
final class TrunkFork {

    private TrunkFork() {
    }

    static void apply(TreeSkeleton skeleton, TreeSettings settings, Random random) {
        int forks = settings.getInt(TreeSettings.Key.TRUNK_FORKS);
        List<TreeSkeleton.Node> nodes = skeleton.nodes;
        if (forks <= 0 || nodes.size() < 4) return;
        // the main stem: from the foot, always into the child with the larger branch behind it
        int[] weight = new int[nodes.size()];
        List<List<Integer>> kids = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) kids.add(new ArrayList<>());
        for (int index = nodes.size() - 1; index >= 0; index--) {
            weight[index] += 1;
            int parent = nodes.get(index).parent;
            if (parent >= 0) {
                weight[parent] += weight[index];
                kids.get(parent).add(index);
            }
        }
        List<Integer> path = new ArrayList<>();
        int at = 0;
        while (true) {
            path.add(at);
            int heavy = -1;
            for (int child : kids.get(at)) if (heavy < 0 || weight[child] > weight[heavy]) heavy = child;
            if (heavy < 0) break;
            at = heavy;
        }
        double topY = nodes.get(path.get(path.size() - 1)).y;
        double forkY = Math.max(1.5, topY * settings.get(TreeSettings.Key.FORK_HEIGHT));
        int fork = -1;
        for (int index : path) {
            if (nodes.get(index).y >= forkY) {
                fork = index;
                break;
            }
        }
        if (fork < 0 || nodes.get(fork).parent < 0) return;
        TreeSkeleton.Node origin = nodes.get(fork);
        double top = TreeAlgorithm.reachUp(settings);
        double reach = TreeAlgorithm.reachOut(settings);
        double curve = settings.get(TreeSettings.Key.CURVE);
        double taper = settings.get(TreeSettings.Key.TAPER);
        double flatness = settings.get(TreeSettings.Key.LEAF_FLATNESS);
        boolean leaves = settings.get(TreeSettings.Key.LEAF_RADIUS) >= 0.4;
        double leafScale = "builder".equals(settings.algorithm()) ? 1.5 : 1.0;
        double lean = Math.toRadians(settings.get(TreeSettings.Key.FORK_ANGLE));
        double azimuth = random.nextDouble() * Math.PI * 2.0;
        double footReach = Math.max(1.5, reach - Math.hypot(origin.x, origin.z));
        for (int index = 0; index < forks; index++) {
            double phi = azimuth + Math.PI * 2.0 * index / forks + (random.nextDouble() - 0.5) * 0.5;
            double tilt = lean * (0.85 + 0.3 * random.nextDouble());
            double height = Math.max(3.0, (Math.max(topY, origin.y + 3.0) - origin.y) * (0.85 + 0.25 * random.nextDouble()));
            // how far out the leader ends: the lean says it, the box limits it. It goes out fast at first and then more slowly, but never back in: a leader that bent back to the
            // trunk would run along it
            double outward = Math.max(1.5, Math.min(footReach, height * Math.tan(tilt) * 1.2 + 1.0));
            double startRadius = Math.max(0.8, origin.radius * 0.75);
            double endRadius = Math.max(0.5, startRadius * (1.0 - taper * 0.8));
            int steps = Math.max(3, (int) Math.round(height));
            int parent = fork;
            List<Integer> made = new ArrayList<>();
            for (int step = 1; step <= steps; step++) {
                double t = step / (double) steps;
                double lateral = outward * (1.0 - Math.pow(1.0 - t, 1.7));
                double wiggle = curve * 0.6 * Math.sin(t * 5.0 + phi * 3.0);
                double x = origin.x + Math.cos(phi) * lateral - Math.sin(phi) * wiggle;
                double z = origin.z + Math.sin(phi) * lateral + Math.cos(phi) * wiggle;
                double y = origin.y + height * t;
                if (y > top) break;
                parent = skeleton.add(x, y, z, parent, 0);
                skeleton.nodes.get(parent).radius = startRadius + (endRadius - startRadius) * Math.pow(t, 0.9);
                made.add(parent);
            }
            if (!leaves || made.isEmpty()) continue;
            int before = skeleton.leaves.size();
            TreeAlgorithm.leafAlong(skeleton, made, 3, flatness, random, 0.85);
            for (int leaf = before; leaf < skeleton.leaves.size(); leaf++) skeleton.leaves.get(leaf)[3] *= leafScale;
        }
    }
}
