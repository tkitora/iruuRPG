package net.tkgon.mc.iruuRPG.tool.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A way of growing the skeleton of a tree from its settings. Pure arithmetic (no server calls), so it can run off the main thread. */
public interface TreeAlgorithm {

    TreeSkeleton grow(TreeSettings settings, Random random);

    static TreeAlgorithm of(String name) {
        return "lsystem".equals(name) ? new LSystemAlgorithm() : new ColonizationAlgorithm();
    }

    /** The leaf blobs may take this much of the height and of the half width; the branches stop that far from the edge of the box. */
    static double reachUp(TreeSettings settings) {
        return Math.max(4.0, settings.height() - settings.get(TreeSettings.Key.LEAF_RADIUS) * 0.8);
    }

    static double reachOut(TreeSettings settings) {
        return Math.max(1.5, settings.width() / 2.0 - settings.get(TreeSettings.Key.LEAF_RADIUS));
    }

    /**
     * The roots: {@code roots} paths leaving the foot of the trunk evenly round it (with a little jitter), going out and a little down in three
     * steps that wander, thinning from about the trunk's radius to half a block.
     */
    static void addRoots(TreeSkeleton skeleton, TreeSettings settings, Random random) {
        int count = settings.getInt(TreeSettings.Key.ROOTS);
        double trunk = settings.get(TreeSettings.Key.TRUNK_RADIUS);
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0 * (index + random.nextDouble() * 0.5) / count;
            double length = trunk * 2.5 + 1.5 + random.nextDouble() * trunk;
            List<double[]> points = new ArrayList<>();
            double x = Math.cos(angle) * trunk * 0.5;
            double z = Math.sin(angle) * trunk * 0.5;
            double y = 0.4;
            points.add(new double[]{x, y, z, Math.max(0.6, trunk * 0.7)});
            for (int step = 1; step <= 3; step++) {
                angle += (random.nextDouble() - 0.5) * 0.5;
                x += Math.cos(angle) * length / 3.0;
                z += Math.sin(angle) * length / 3.0;
                y -= 0.35;
                points.add(new double[]{x, y, z, Math.max(0.5, trunk * 0.7 * (1.0 - step / 3.5))});
            }
            skeleton.roots.add(new TreeSkeleton.RootPath(points));
        }
    }
}
