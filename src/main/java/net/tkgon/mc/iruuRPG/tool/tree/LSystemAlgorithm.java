package net.tkgon.mc.iruuRPG.tool.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * A parametric L-system after Honda's tree model (1971), read by a 3D turtle. The word starts as one module {@code A(length, width)} and is rewritten
 * a few times with the production
 * <pre>A(l, w) -> F(l, w) [ + A(l r1, w wr) ] [ - A(l r2, w wr) ]</pre>
 * where {@code F} draws a branch of that length, {@code +} / {@code -} turn the turtle by the branching angles a1 / a2 and {@code [ ]} save and
 * restore its place; between two branchings the turtle rolls by the divergence angle so the branches do not all lie in one plane. The
 * ratios, angles and the divergence are drawn near typical values (and the density and droop settings move them), so no two trees are alike. The
 * result is scaled to fit inside the height and width of the settings.
 */
public final class LSystemAlgorithm implements TreeAlgorithm {

    private record Module(char symbol, double length, double width) {
    }

    @Override
    public TreeSkeleton grow(TreeSettings settings, Random random) {
        double density = settings.get(TreeSettings.Key.DENSITY);
        double droop = settings.get(TreeSettings.Key.DROOP);
        int iterations = Math.max(6, Math.min(11, (int) Math.round(8 + (density - 1.0) * 2.0)));
        double r1 = 0.78 + random.nextDouble() * 0.12;
        double r2 = 0.66 + random.nextDouble() * 0.14;
        double a1 = Math.toRadians(18.0 + random.nextDouble() * 12.0);
        double a2 = Math.toRadians(34.0 + random.nextDouble() * 16.0 + droop * 15.0);
        double divergence = Math.toRadians(137.5);
        double widthRatio = 0.7;

        // the rewriting: the word as a list of modules; '[' and ']' and the turns have no parameters
        List<Module> word = new ArrayList<>();
        // a bare trunk first, as long as the "trunk ratio" asks (the crown of this model is about 5 times the first branch long)
        double ratio = settings.get(TreeSettings.Key.TRUNK_RATIO);
        word.add(new Module('F', ratio / (1.0 - ratio) * 5.0, 1.0));
        word.add(new Module('A', 1.0, 1.0));
        for (int iteration = 0; iteration < iterations; iteration++) {
            List<Module> next = new ArrayList<>(word.size() * 8);
            for (Module module : word) {
                if (module.symbol() != 'A') {
                    next.add(module);
                    continue;
                }
                double l = module.length();
                double w = module.width();
                next.add(new Module('F', l, w));
                next.add(new Module('[', 0, 0));
                next.add(new Module('+', 0, 0));
                next.add(new Module('A', l * r1, w * widthRatio));
                next.add(new Module(']', 0, 0));
                next.add(new Module('/', 0, 0));
                next.add(new Module('[', 0, 0));
                next.add(new Module('-', 0, 0));
                next.add(new Module('A', l * r2, w * widthRatio));
                next.add(new Module(']', 0, 0));
            }
            word = next;
            if (word.size() > 40000) break;
        }

        // the turtle: a position and three axes (heading, left, up); '+' and '-' turn about the up axis, '/' rolls about the heading
        TreeSkeleton skeleton = new TreeSkeleton();
        double[] position = {0.0, 0.0, 0.0};
        double[][] frame = {{0, 1, 0}, {-1, 0, 0}, {0, 0, 1}};
        int node = skeleton.add(0.0, 0.0, 0.0, -1);
        Deque<Object[]> stack = new ArrayDeque<>();
        for (Module module : word) {
            switch (module.symbol()) {
                case 'F' -> {
                    position[0] += frame[0][0] * module.length();
                    position[1] += frame[0][1] * module.length();
                    position[2] += frame[0][2] * module.length();
                    node = skeleton.add(position[0], position[1], position[2], node);
                }
                case '+' -> rotate(frame, 2, a1);
                case '-' -> rotate(frame, 2, -a2);
                case '/' -> rotate(frame, 0, divergence + (random.nextDouble() - 0.5) * 0.4);
                case '[' -> stack.push(new Object[]{position.clone(), new double[][]{frame[0].clone(), frame[1].clone(), frame[2].clone()}, node});
                case ']' -> {
                    Object[] saved = stack.pop();
                    position = ((double[]) saved[0]).clone();
                    double[][] f = (double[][]) saved[1];
                    frame = new double[][]{f[0].clone(), f[1].clone(), f[2].clone()};
                    node = (Integer) saved[2];
                }
                default -> {
                }
            }
        }
        fit(skeleton, settings);
        skeleton.computeRadii(settings.get(TreeSettings.Key.TRUNK_RADIUS), 0.5, 0.45);
        TreeAlgorithm.taperBareTrunk(skeleton, 1, settings.get(TreeSettings.Key.TAPER));
        TreeAlgorithm.leafAtTips(skeleton, settings.get(TreeSettings.Key.LEAF_FLATNESS), random);
        TreeAlgorithm.addRoots(skeleton, settings, random);
        return skeleton;
    }

    /** Turns the frame by {@code angle} about one of its axes (0 heading, 1 left, 2 up) by Rodrigues' formula applied to the other two. */
    private static void rotate(double[][] frame, int axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        double[] va = frame[a].clone();
        double[] vb = frame[b].clone();
        for (int index = 0; index < 3; index++) {
            frame[a][index] = va[index] * cos + vb[index] * sin;
            frame[b][index] = -va[index] * sin + vb[index] * cos;
        }
    }

    /** Scales the skeleton (the height and the width apart) so it fills the box of the settings exactly. */
    private static void fit(TreeSkeleton skeleton, TreeSettings settings) {
        double maxY = 1.0E-6;
        double maxOut = 1.0E-6;
        for (TreeSkeleton.Node node : skeleton.nodes) {
            maxY = Math.max(maxY, node.y);
            maxOut = Math.max(maxOut, Math.max(Math.abs(node.x), Math.abs(node.z)));
        }
        double scaleUp = TreeAlgorithm.reachUp(settings) / maxY;
        double scaleOut = TreeAlgorithm.reachOut(settings) / maxOut;
        List<TreeSkeleton.Node> scaled = new ArrayList<>(skeleton.nodes.size());
        for (TreeSkeleton.Node node : skeleton.nodes) {
            TreeSkeleton.Node copy = new TreeSkeleton.Node(node.x * scaleOut, Math.max(0.0, node.y * scaleUp), node.z * scaleOut, node.parent, node.level);
            copy.children = node.children;
            scaled.add(copy);
        }
        skeleton.nodes.clear();
        skeleton.nodes.addAll(scaled);
    }
}
