package net.tkgon.mc.iruuRPG.tools.procedural.ornate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bones of a tree: nodes at points between blocks (block (0, 0, 0) is the cube from 0 to 1), each with the node it grows from, and the radius of the wood there.
 * A node is added after the node it grows from, so the nodes in their order are always a parent before its children. The radii come from the pipe model
 * ({@link #pipe}): the radius of a node to the power n is the sum of those of its children (n about 2 to 2.5), the trunk and the roots each from their own tips.
 */
public final class Skeleton {

    public static final int TRUNK = 0;
    public static final int BRANCH = 1;
    public static final int ROOT = 2;

    private double[] x = new double[256];
    private double[] y = new double[256];
    private double[] z = new double[256];
    private double[] r = new double[256];
    private int[] parent = new int[256];
    private int[] kind = new int[256];
    /** The limb a node is on: the trunk 0, else the number of the branch or root that leaves the trunk (for the forks and the checks of the decoration). */
    private int[] limb = new int[256];
    /** How far along the wood from the base each node is, and how many nodes from it. */
    private double[] path = new double[256];
    private int[] depth = new int[256];
    private int size;
    private int limbs;
    private List<List<Integer>> children;
    private double[] toTip;
    /** The segments for the spacing of the branches: a grid of 2 blocks. */
    private final Map<Long, List<Integer>> spacing = new HashMap<>();

    /** Adds a node; returns its index. {@code parent} -1 for the base. */
    public int add(double px, double py, double pz, int from, int nodeKind, int nodeLimb) {
        if (size == x.length) {
            int grown = size * 2;
            x = Arrays.copyOf(x, grown);
            y = Arrays.copyOf(y, grown);
            z = Arrays.copyOf(z, grown);
            r = Arrays.copyOf(r, grown);
            parent = Arrays.copyOf(parent, grown);
            kind = Arrays.copyOf(kind, grown);
            limb = Arrays.copyOf(limb, grown);
            path = Arrays.copyOf(path, grown);
            depth = Arrays.copyOf(depth, grown);
        }
        x[size] = px;
        y[size] = py;
        z[size] = pz;
        parent[size] = from;
        kind[size] = nodeKind;
        limb[size] = nodeLimb;
        path[size] = from < 0 ? 0.0 : path[from] + Math.sqrt(sq(px - x[from]) + sq(py - y[from]) + sq(pz - z[from]));
        depth[size] = from < 0 ? 0 : depth[from] + 1;
        children = null;
        if (from >= 0) index(size);
        return size++;
    }

    /** A new limb number (a branch or a root that leaves the trunk). */
    public int newLimb() {
        return ++limbs;
    }

    public int size() {
        return size;
    }

    public double x(int node) {
        return x[node];
    }

    public double y(int node) {
        return y[node];
    }

    public double z(int node) {
        return z[node];
    }

    public double radius(int node) {
        return r[node];
    }

    public int parent(int node) {
        return parent[node];
    }

    public int kind(int node) {
        return kind[node];
    }

    public int limb(int node) {
        return limb[node];
    }

    public List<Integer> children(int node) {
        if (children == null) {
            children = new ArrayList<>(size);
            for (int index = 0; index < size; index++) children.add(new ArrayList<>(2));
            for (int index = 0; index < size; index++) if (parent[index] >= 0) children.get(parent[index]).add(index);
        }
        return children.get(node);
    }

    public boolean tip(int node) {
        return children(node).isEmpty();
    }

    /** The length of the segment from the parent to {@code node} (0 for the base). */
    public double length(int node) {
        int from = parent[node];
        if (from < 0) return 0.0;
        return Math.sqrt(sq(x[node] - x[from]) + sq(y[node] - y[from]) + sq(z[node] - z[from]));
    }

    /** How far along the wood the nearest tip beyond {@code node} is (0 at a tip). */
    public double toTip(int node) {
        if (toTip == null || toTip.length != size) {
            toTip = new double[size];
            for (int index = size - 1; index >= 0; index--) {
                List<Integer> next = children(index);
                if (next.isEmpty()) {
                    toTip[index] = 0.0;
                    continue;
                }
                double best = Double.MAX_VALUE;
                for (int child : next) best = Math.min(best, toTip[child] + length(child));
                toTip[index] = best;
            }
        }
        return toTip[node];
    }

    /** The radius at the parent's end of the segment to {@code node}: as thick as the parent on a plain stretch, as thick as the child's own pipe at a fork. */
    public double startRadius(int node) {
        int from = parent[node];
        if (from < 0) return r[node];
        return children(from).size() > 1 || kind[from] != kind[node] ? Math.min(r[from], r[node] * (1.0 + taper * length(node))) : r[from];
    }

    private double taper;

    /**
     * The radii by the pipe model: a tip is 1, a node with one child is its child's radius thickened by {@code taper} a block, a fork is the n-th root of the sum of
     * the radii its children start with to the power n (so at every fork, r^n of the parent is the sum of r^n of the children). The trunk (the base and every node that is not a root) is scaled so the base is {@code base} thick; the roots so the n-th
     * root of the sum of their first radii to the power n is {@code base * rootShare}.
     */
    public void pipe(double n, double taper, double base, double rootShare) {
        this.taper = taper;
        double[] rel = new double[size];
        for (int index = size - 1; index >= 0; index--) {
            List<Integer> next = children(index);
            if (next.isEmpty()) {
                rel[index] = 1.0;
                continue;
            }
            double sum = 0.0;
            int counted = 0;
            double single = 0.0;
            for (int child : next) {
                // (the base sums only the trunk's side: the roots are scaled on their own)
                if (index == 0 && kind[child] == ROOT) continue;
                single = rel[child] * (1.0 + taper * length(child));
                sum += Math.pow(single, n);
                counted++;
            }
            if (counted == 0) {
                rel[index] = 1.0;
                continue;
            }
            rel[index] = counted == 1 ? single : Math.pow(sum, 1.0 / n);
        }
        double trunkScale = base / Math.max(1.0e-6, rel[0]);
        double rootSum = 0.0;
        for (int child : children(0)) if (kind[child] == ROOT) rootSum += Math.pow(rel[child] * (1.0 + taper * length(child)), n);
        double rootScale = rootSum <= 0.0 ? trunkScale : base * rootShare / Math.pow(rootSum, 1.0 / n);
        for (int index = 0; index < size; index++) r[index] = rel[index] * (kind[index] == ROOT ? rootScale : trunkScale);
    }

    // ---- the spacing of the branches -------------------------------------------------------------------------------------------------------

    private static long cell(int cx, int cy, int cz) {
        return ((long) (cx + 4096) & 0x3FFFL) | (((long) (cy + 4096) & 0x3FFFL) << 14) | (((long) (cz + 4096) & 0x3FFFL) << 28);
    }

    private void index(int node) {
        int from = parent[node];
        int x0 = (int) Math.floor(Math.min(x[from], x[node]) / 2.0) - 1;
        int x1 = (int) Math.floor(Math.max(x[from], x[node]) / 2.0) + 1;
        int y0 = (int) Math.floor(Math.min(y[from], y[node]) / 2.0) - 1;
        int y1 = (int) Math.floor(Math.max(y[from], y[node]) / 2.0) + 1;
        int z0 = (int) Math.floor(Math.min(z[from], z[node]) / 2.0) - 1;
        int z1 = (int) Math.floor(Math.max(z[from], z[node]) / 2.0) + 1;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                for (int cz = z0; cz <= z1; cz++) spacing.computeIfAbsent(cell(cx, cy, cz), key -> new ArrayList<>()).add(node);
            }
        }
    }

    /** The nearest node both {@code a} and {@code b} grow from (themselves included). */
    private int common(int a, int b) {
        while (a != b && a >= 0 && b >= 0) {
            if (depth[a] >= depth[b]) a = parent[a];
            else b = parent[b];
        }
        return a == b ? a : -1;
    }

    /**
     * Whether a segment from {@code from} to (px, py, pz) keeps {@code gap} blocks from the wood that is there: branches 2 blocks apart do not join by chance. The wood
     * it grows out of does not count (its own segments), nor the wood of a fork both are still near (within {@link #NEAR_FORK} blocks along each from the fork).
     */
    public boolean clear(int from, double px, double py, double pz, double gap) {
        int up = parent[from];
        int upper = up >= 0 ? parent[up] : -1;
        double reach = path[from] + Math.sqrt(sq(px - x[from]) + sq(py - y[from]) + sq(pz - z[from]));
        int cx = (int) Math.floor(px / 2.0);
        int cy = (int) Math.floor(py / 2.0);
        int cz = (int) Math.floor(pz / 2.0);
        double gap2 = gap * gap;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<Integer> nodes = spacing.get(cell(cx + dx, cy + dy, cz + dz));
                    if (nodes == null) continue;
                    for (int node : nodes) {
                        int start = parent[node];
                        if (node == from || start == from || node == up || start == up || node == upper || start == upper) continue;
                        if (distance2(px, py, pz, start, node) >= gap2) continue;
                        int fork = common(from, start);
                        if (fork >= 0 && reach - path[fork] <= NEAR_FORK && path[start] - path[fork] <= NEAR_FORK) continue;
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** The squared distance from a point to the segment from node {@code a} to node {@code b}. */
    private double distance2(double px, double py, double pz, int a, int b) {
        double ux = x[b] - x[a];
        double uy = y[b] - y[a];
        double uz = z[b] - z[a];
        double length2 = ux * ux + uy * uy + uz * uz;
        double t = length2 <= 1.0e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, ((px - x[a]) * ux + (py - y[a]) * uy + (pz - z[a]) * uz) / length2));
        return sq(px - x[a] - ux * t) + sq(py - y[a] - uy * t) + sq(pz - z[a] - uz * t);
    }

    /** How far from a fork two branches of it may still be near each other. */
    static final double NEAR_FORK = 4.0;

    private static double sq(double value) {
        return value * value;
    }
}
