package net.tkgon.mc.iruuRPG.tool.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * The skeleton of a tree before it is made of blocks: points joined by branches (each point knows the one it grew from), each with a radius, and the
 * roots as paths. Coordinates are in blocks from the middle of the bottom of the block the tree stands on; y is up. Nodes are always added after
 * the node they grow from, so a pass from the last to the first reaches every child before its parent.
 */
public final class TreeSkeleton {

    /** One point of the skeleton. */
    public static final class Node {
        public final double x;
        public final double y;
        public final double z;
        public final int parent;
        public double radius;
        public int children;

        public Node(double x, double y, double z, int parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.parent = parent;
        }
    }

    /** A root: points with a radius each ({x, y, z, radius}). */
    public record RootPath(List<double[]> points) {
    }

    public final List<Node> nodes = new ArrayList<>();
    public final List<RootPath> roots = new ArrayList<>();

    public int add(double x, double y, double z, int parent) {
        nodes.add(new Node(x, y, z, parent));
        if (parent >= 0) nodes.get(parent).children++;
        return nodes.size() - 1;
    }

    /**
     * The thickness of every branch by the pipe model (Leonardo's rule): the cross sections of the branches a branch splits into add up to its own, as
     * area to the power {@code exponent} (2 is the area exactly; trees are nearer 2.5), starting from {@code tipRadius} at the ends. Then everything is
     * scaled so the root of the tree has the radius {@code baseRadius}; nothing gets thinner than {@code minRadius}.
     */
    public void computeRadii(double baseRadius, double tipRadius, double minRadius) {
        double exponent = 2.5;
        double[] sums = new double[nodes.size()];
        for (int index = nodes.size() - 1; index >= 0; index--) {
            Node node = nodes.get(index);
            node.radius = node.children == 0 ? tipRadius : Math.pow(sums[index], 1.0 / exponent);
            if (node.parent >= 0) sums[node.parent] += Math.pow(node.radius, exponent);
        }
        double scale = nodes.isEmpty() ? 1.0 : baseRadius / Math.max(1.0E-6, nodes.get(0).radius);
        for (Node node : nodes) node.radius = Math.max(minRadius, node.radius * scale);
    }
}
