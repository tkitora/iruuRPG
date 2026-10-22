package net.tkgon.mc.iruuRPG.tool.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A way of growing the skeleton of a tree from its settings. Pure arithmetic (no server calls), so it can run off the main thread. */
public interface TreeAlgorithm {

    TreeSkeleton grow(TreeSettings settings, Random random);

    static TreeAlgorithm of(String name) {
        return switch (name) {
            case "lsystem" -> new LSystemAlgorithm();
            case "colonization" -> new ColonizationAlgorithm();
            case "conifer" -> new ConiferAlgorithm();
            case "parametric" -> new ParametricAlgorithm();
            default -> new BuilderAlgorithm();
        };
    }

    /** The leaf blobs may take this much of the height and of the half width; the branches stop that far from the edge of the box. */
    static double reachUp(TreeSettings settings) {
        return Math.max(4.0, settings.height() - settings.get(TreeSettings.Key.LEAF_RADIUS) * 0.8);
    }

    static double reachOut(TreeSettings settings) {
        return Math.max(1.5, settings.width() / 2.0 - settings.get(TreeSettings.Key.LEAF_RADIUS));
    }

    /**
     * How long a branch is at a place on the trunk, as a share of the longest ({@code t}: 0 at the lowest branch, 1 at the top): the shape ratio of
     * Weber and Penn ("Creation and Rendering of Realistic Trees", 1995) for the crown shapes: round, conical, flat (long branches high up) and columnar.
     */
    static double shape(String crown, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return switch (crown) {
            case "cone" -> 0.2 + 0.8 * (1.0 - clamped);
            case "umbrella" -> 0.3 + 0.7 * Math.pow(clamped, 0.7);
            case "columnar" -> 0.3 + 0.15 * Math.sin(Math.PI * clamped);
            default -> 0.2 + 0.8 * Math.sin(Math.PI * (0.12 + 0.76 * clamped));
        };
    }

    /**
     * A direction {@code down} radians off {@code tangent}, turned {@code azimuth} radians round it (0 is toward a fixed side): the way a branch leaves a stem.
     */
    static double[] branchDirection(double[] tangent, double down, double azimuth) {
        double[] reference = Math.abs(tangent[1]) > 0.95 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] u = normalize(cross(tangent, reference));
        double[] v = cross(tangent, u);
        double cd = Math.cos(down);
        double sd = Math.sin(down);
        double ca = Math.cos(azimuth);
        double sa = Math.sin(azimuth);
        return normalize(new double[]{
                tangent[0] * cd + (u[0] * ca + v[0] * sa) * sd,
                tangent[1] * cd + (u[1] * ca + v[1] * sa) * sd,
                tangent[2] * cd + (u[2] * ca + v[2] * sa) * sd});
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] normalize(double[] v) {
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length < 1.0E-9) return new double[]{0, 1, 0};
        return new double[]{v[0] / length, v[1] / length, v[2] / length};
    }

    /**
     * How a stem wanders: the random turn each step, the pull up (or down, negative) each step, the extra pull up over the last third, where it must stop,
     * and how hard it is pulled back toward the vertical line through the foot of the tree (for a trunk, so that its lean cannot carry it out of the box).
     */
    record Growth(double wander, double upward, double tipUp, double top, double reach, double center) {
    }

    /** No more nodes than this in one skeleton (a safety limit for extreme settings). */
    int MAX_NODES = 40000;

    /**
     * Grows one stem from {@code from} (a point of the parent, or the ground) along {@code direction} for {@code length} blocks in steps of one block: each step turns
     * the direction a little at random ({@code wander}), pulls it up or down, and moves on; the stem stops short where it would leave the box. The radius goes from
     * {@code startRadius} to {@code endRadius}. Returns the nodes made, in order. A node added at the ground has no parent ({@code parent} -1).
     */
    static List<Integer> growStem(TreeSkeleton skeleton, int parent, double[] from, double[] direction, double length, double startRadius, double endRadius,
                                  int level, Growth growth, Random random) {
        List<Integer> made = new ArrayList<>();
        int steps = Math.max(1, (int) Math.round(length));
        double[] position = from.clone();
        double[] heading = normalize(direction);
        int previous = parent;
        if (parent < 0) {
            previous = skeleton.add(position[0], position[1], position[2], -1, level);
            skeleton.nodes.get(previous).radius = startRadius;
            made.add(previous);
        }
        for (int step = 1; step <= steps; step++) {
            double t = step / (double) steps;
            heading[0] += random.nextGaussian() * growth.wander();
            heading[1] += random.nextGaussian() * growth.wander() + growth.upward() + (t > 0.66 ? growth.tipUp() : 0.0);
            heading[2] += random.nextGaussian() * growth.wander();
            heading[0] -= position[0] * growth.center();
            heading[2] -= position[2] * growth.center();
            heading = normalize(heading);
            position[0] += heading[0];
            position[1] += heading[1];
            position[2] += heading[2];
            if (position[1] > growth.top() || position[1] < 0.3 || position[0] * position[0] + position[2] * position[2] > (growth.reach() + 0.5) * (growth.reach() + 0.5)) break;
            previous = skeleton.add(position[0], position[1], position[2], previous, level);
            skeleton.nodes.get(previous).radius = startRadius + (endRadius - startRadius) * Math.pow(t, 0.9);
            made.add(previous);
        }
        return made;
    }

    /** The direction a stem is going at node {@code index} of {@code made}: from the node before it (or the first step's own direction). */
    static double[] tangentAt(TreeSkeleton skeleton, List<Integer> made, int index, double[] fallback) {
        if (made.size() < 2) return fallback;
        int at = Math.max(1, Math.min(made.size() - 1, index));
        TreeSkeleton.Node a = skeleton.nodes.get(made.get(at - 1));
        TreeSkeleton.Node b = skeleton.nodes.get(made.get(at));
        return normalize(new double[]{b.x - a.x, b.y - a.y, b.z - a.z});
    }

    /** Clumps of leaves along a stem: one every {@code every} nodes (the end always), a little smaller toward the base. */
    static void leafAlong(TreeSkeleton skeleton, List<Integer> made, int every, double flatness, Random random, double chance) {
        for (int index = 0; index < made.size(); index++) {
            boolean end = index == made.size() - 1;
            if (!end && (index % every != 0 || random.nextDouble() > chance)) continue;
            TreeSkeleton.Node node = skeleton.nodes.get(made.get(index));
            double along = made.size() <= 1 ? 1.0 : index / (double) (made.size() - 1);
            skeleton.leaves.add(new double[]{node.x, node.y, node.z, 0.65 + 0.35 * along, flatness});
        }
    }

    /**
     * The bare trunk (nodes 0 to {@code last}) gets thinner on the way up (the taper setting): the pipe model keeps a stem as thick as everything above it needs, which
     * leaves the trunk a post of one thickness from the ground to the first branch.
     */
    static void taperBareTrunk(TreeSkeleton skeleton, int last, double taper) {
        if (last < 1 || last >= skeleton.nodes.size()) return;
        double topY = Math.max(1.0E-6, skeleton.nodes.get(last).y);
        for (int index = 0; index <= last; index++) {
            TreeSkeleton.Node node = skeleton.nodes.get(index);
            node.radius = Math.max(0.5, node.radius * (1.0 - 0.4 * taper * Math.min(1.0, node.y / topY)));
        }
    }

    /**
     * Leaf clumps for a skeleton whose algorithm does not place them: at every end of a branch, and at a share of the thin branches before them.
     */
    static void leafAtTips(TreeSkeleton skeleton, double flatness, Random random) {
        for (TreeSkeleton.Node node : skeleton.nodes) {
            boolean tip = node.children == 0;
            if (!tip && !(node.radius <= 0.6 && random.nextDouble() < 0.45)) continue;
            skeleton.leaves.add(new double[]{node.x, node.y, node.z, 0.8 + 0.3 * random.nextDouble(), flatness});
        }
    }

    /**
     * The roots: {@code roots} paths leaving the foot of the trunk evenly round it (with a little jitter), going out and a little down in three
     * steps that wander, thinning from about the trunk's radius to half a block.
     */
    static void addRoots(TreeSkeleton skeleton, TreeSettings settings, Random random) {
        int count = settings.getInt(TreeSettings.Key.ROOTS);
        double trunk = settings.get(TreeSettings.Key.TRUNK_RADIUS) * (1.0 + settings.get(TreeSettings.Key.FLARE) * 0.3);
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
