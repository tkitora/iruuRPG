package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The fractal tree: a stack-based, non-recursive fractal for a voxel world. Nothing calls itself, so a tree of many levels cannot overflow the stack of the thread; the
 * work is a loop over an explicit stack (depth first, like a recursion written out), and a budget of branches keeps a huge setting from running away.
 *
 * <p>Main tree: the trunk is pushed first, from the base going up. Each node popped is drawn ({@link #drawBranch}: one block a step, its way bent by simplex noise
 * (the sway), pulled down by gravity (the droop) and normalised again every step, its radius tapering to {@code radiusDecay} of itself); below the last level it
 * pushes {@code branchesEach} children at its tip ({@link #childDirection}: spread evenly round the parent's way, jittered by the noise, lifted by the up bias),
 * each {@code lengthDecay} as long and {@code radiusDecay} as thick. A branch of the last level grows foliage at its tip.
 *
 * <p>Foliage ({@link #growFoliage}): not only a ball. A small stack loop of its own grows a miniature of the same fractal from the tip (twigs, {@code foliageLevels}
 * deep, {@code foliageBranches} each), with a ball of leaves at the tip and at the end of every twig; the balls take the leaf gradient by their height.
 *
 * <p>The tree is first worked out as points (wood) and balls (leaves); when it does not fit the box of the settings (the height and the width, which are maxima), the
 * whole of it is scaled down to fit, and only then is it turned into blocks. Coordinates as in {@link TreeVoxelizer}.
 */
final class FractalTree {

    /** At most this many main branches (and this many twigs in all the foliage): beyond it, nodes stop splitting. */
    static final int MAX_BRANCHES = 6000;
    static final int MAX_TWIGS = 20000;
    /** A step of the walk along a branch is one block; the balls of wood are put this far apart on it. */
    private static final double SAMPLE = 0.4;

    /** A branch waiting on the stack: where it starts, its way, its length and radius, its level (0 = the trunk). */
    record Node(double x, double y, double z, double dx, double dy, double dz, double length, double radius, int level) {
    }

    private final TreeSettings settings;
    private final Random random;
    private final TreeSimplex noise;
    private final double noiseAmp;
    private final double noiseScale;
    private final double gravity;
    private final double lengthDecay;
    private final double radiusDecay;
    private final double jitter;
    private final double upBias;
    private final double trunkRadius;
    private final double flare;
    private final double flareHeight;
    /** The wood: {x, y, z, radius, part ordinal, 1 for a twig of the foliage (else 0)}. */
    final List<double[]> wood = new ArrayList<>();
    /** The leaves: {x, y, z, radius}. */
    final List<double[]> leaves = new ArrayList<>();
    private int branches;
    private int twigs;
    /** The levels actually grown (the settings', or fewer when the whole tree would not fit in the budget). */
    private int mainLevels;
    private int foliageLevels;
    /** A different place in the noise for every branch, so two branches do not sway alike. */
    private int stream;

    FractalTree(TreeSettings settings, Random random) {
        this.settings = settings;
        this.random = random;
        this.noise = new TreeSimplex(random.nextLong());
        this.noiseAmp = settings.get(TreeSettings.Key.F_NOISE);
        this.noiseScale = settings.get(TreeSettings.Key.F_NOISE_SCALE);
        this.gravity = settings.get(TreeSettings.Key.F_GRAVITY);
        this.lengthDecay = settings.get(TreeSettings.Key.F_LENGTH_DECAY);
        this.radiusDecay = settings.get(TreeSettings.Key.F_RADIUS_DECAY);
        this.jitter = settings.get(TreeSettings.Key.F_JITTER);
        this.upBias = settings.get(TreeSettings.Key.F_UP_BIAS);
        this.trunkRadius = settings.get(TreeSettings.Key.TRUNK_RADIUS);
        this.flare = settings.get(TreeSettings.Key.FLARE);
        this.flareHeight = Math.max(2.0, trunkRadius * 3.0);
    }

    /** The blocks of the tree of {@code settings}. */
    static Map<Long, TreePart> build(TreeSettings settings, Random random) {
        FractalTree tree = new FractalTree(settings, random);
        tree.grow();
        tree.fit(settings.height(), settings.width() / 2.0);
        return tree.voxelize();
    }

    // ---- the main tree ---------------------------------------------------------------------------------------------------------------

    void grow() {
        int each = settings.getInt(TreeSettings.Key.F_BRANCHES);
        int levels = levelsWithin(settings.getInt(TreeSettings.Key.F_LEVELS), each, 1, MAX_BRANCHES);
        // the foliage of every tip shares the twigs: as many levels as all the tips can have alike
        long tips = (long) Math.pow(each, levels);
        foliageLevels = levelsWithin(settings.getInt(TreeSettings.Key.F_FOLIAGE_LEVELS), settings.getInt(TreeSettings.Key.F_FOLIAGE_BRANCHES), 0,
                (int) Math.max(0, MAX_TWIGS / Math.max(1L, tips)));
        mainLevels = levels;
        double spread = Math.toRadians(settings.get(TreeSettings.Key.F_SPREAD));
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(new Node(0.0, 0.0, 0.0, 0.0, 1.0, 0.0, settings.get(TreeSettings.Key.F_TRUNK_LENGTH), trunkRadius, 0));
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            branches++;
            double[] tip = drawBranch(node, false);
            double tipRadius = node.radius * radiusDecay;
            if (node.level < levels && branches + stack.size() + each <= MAX_BRANCHES) {
                double turn = random.nextDouble() * Math.PI * 2.0;
                for (int index = 0; index < each; index++) {
                    double[] way = childDirection(new double[]{tip[3], tip[4], tip[5]}, index, each, spread, turn, node.level);
                    double length = node.length * lengthDecay * (1.0 - 0.25 * jitter * random.nextDouble());
                    stack.push(new Node(tip[0], tip[1], tip[2], way[0], way[1], way[2], length, tipRadius, node.level + 1));
                }
            } else {
                growFoliage(tip, tipRadius);
            }
        }
    }

    /**
     * Draws a branch from its start along its way, a block a step. The branch keeps a heading, which gravity pulls down a little every step ({@code gravity}; not the
     * trunk, which stands), so a long branch hangs more and more; the way it actually goes at a step is the heading bent by the simplex noise of that place
     * ({@code noiseAmp}: the sway), normalised again every step, so neither the length nor the thickness is warped. (The noise bends the way and does not add up in
     * the heading: a smooth noise added up would curl the branch round.) The radius goes from the node's to {@code radiusDecay} of it. Returns the tip and the
     * heading there: {x, y, z, dx, dy, dz}.
     */
    double[] drawBranch(Node node, boolean twig) {
        int channel = stream++;
        double ox = channel * 17.31;
        double oy = channel * 7.77 + 100.0;
        double oz = channel * 3.19 + 200.0;
        double x = node.x(), y = node.y(), z = node.z();
        double hx = node.dx(), hy = node.dy(), hz = node.dz();
        int steps = Math.max(1, (int) Math.ceil(node.length()));
        double step = node.length() / steps;
        double r0 = node.radius();
        double r1 = node.radius() * radiusDecay;
        sample(x, y, z, r0, node.level(), twig);
        for (int index = 1; index <= steps; index++) {
            if (node.level() > 0) {
                hy -= gravity;
                double[] heading = unit(hx, hy, hz);
                hx = heading[0];
                hy = heading[1];
                hz = heading[2];
            }
            double sx = x * noiseScale, sy = y * noiseScale, sz = z * noiseScale;
            double[] way = unit(hx + noise.noise(sx + ox, sy, sz) * noiseAmp,
                    hy + noise.noise(sx, sy + oy, sz) * noiseAmp,
                    hz + noise.noise(sx, sy, sz + oz) * noiseAmp);
            double nx = x + way[0] * step, ny = y + way[1] * step, nz = z + way[2] * step;
            int parts = Math.max(1, (int) Math.ceil(step / SAMPLE));
            for (int part = 1; part <= parts; part++) {
                double t = part / (double) parts;
                double along = (index - 1 + t) / steps;
                sample(x + (nx - x) * t, y + (ny - y) * t, z + (nz - z) * t, r0 + (r1 - r0) * along, node.level(), twig);
            }
            x = nx;
            y = ny;
            z = nz;
        }
        return new double[]{x, y, z, hx, hy, hz};
    }

    private static double[] unit(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return length < 1.0E-6 ? new double[]{0.0, 1.0, 0.0} : new double[]{x / length, y / length, z / length};
    }

    private void sample(double x, double y, double z, double radius, int level, boolean twig) {
        TreePart part;
        if (twig) {
            part = TreePart.TWIG;
        } else if (level == 0) {
            part = TreePart.TRUNK;
            double low = Math.max(0.0, 1.0 - y / flareHeight);
            radius *= 1.0 + flare * low * low;
        } else {
            // the branches of the last level (the ones that carry the foliage) are the thin ones
            part = level < mainLevels ? TreePart.BRANCH : TreePart.TWIG;
        }
        wood.add(new double[]{x, y, z, radius, part.ordinal(), twig ? 1.0 : 0.0});
    }

    /**
     * The way of child {@code index} of {@code count}: {@code spread} off the parent's way, at an angle round it spaced evenly ({@code baseAzi}) from {@code turn},
     * the angle, the spread and so on moved by the noise ({@code jitter}), then lifted by the up bias and normalised.
     */
    /**
     * The most levels, up to {@code wanted}, for which a tree of {@code each} branches a node has at most {@code budget} branches in all ({@code first}: the branches
     * at level 0, 1 for the trunk and 0 for foliage, whose first level is already {@code each} twigs). A depth-first walk with a plain budget would spend all of it
     * on the first subtree and leave a lopsided tree; fewer levels keep it whole.
     */
    static int levelsWithin(int wanted, int each, int first, int budget) {
        long total = first;
        long row = 1;
        for (int level = 1; level <= wanted; level++) {
            row *= each;
            total += row;
            if (total > budget) return level - 1;
        }
        return wanted;
    }

    double[] childDirection(double[] parent, int index, int count, double spread, double turn, int level) {
        double key = stream * 0.618 + index * 1.7;
        double j1 = noise.noise(key, level * 3.1, 11.0);
        double j2 = noise.noise(level * 2.3, key, 23.0);
        double baseAzi = turn + Math.PI * 2.0 * index / count;
        double azimuth = baseAzi + jitter * j1 * Math.PI / count;
        double off = Math.max(0.0, spread * (1.0 + 0.5 * jitter * j2));
        double[] way = TreeAlgorithm.branchDirection(TreeAlgorithm.normalize(parent), off, azimuth);
        way[1] += upBias;
        double length = Math.sqrt(way[0] * way[0] + way[1] * way[1] + way[2] * way[2]);
        if (length < 1.0E-6) return new double[]{0.0, 1.0, 0.0};
        return new double[]{way[0] / length, way[1] / length, way[2] / length};
    }

    // ---- the foliage -------------------------------------------------------------------------------------------------------------------

    /**
     * The leaves at the tip of a last branch: a ball there, and a miniature fractal of twigs grown from it by a stack loop of its own (each twig ends in a smaller
     * ball, and below the foliage levels splits again). {@code tip}: {x, y, z, dx, dy, dz} of the branch's end.
     */
    void growFoliage(double[] tip, double radius) {
        double leaf = settings.get(TreeSettings.Key.LEAF_RADIUS);
        int levels = foliageLevels;
        int each = settings.getInt(TreeSettings.Key.F_FOLIAGE_BRANCHES);
        double spread = Math.toRadians(settings.get(TreeSettings.Key.F_FOLIAGE_SPREAD));
        if (leaf >= 0.4) leaves.add(new double[]{tip[0], tip[1], tip[2], leaf});
        if (levels <= 0) return;
        double twigRadius = Math.min(0.45, radius);
        Deque<Node> stack = new ArrayDeque<>();
        pushTwigs(stack, tip, settings.get(TreeSettings.Key.F_FOLIAGE_LENGTH), twigRadius, 0, each, spread);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            twigs++;
            double[] end = drawBranch(node, true);
            double size = leaf * Math.pow(0.82, node.level() + 1);
            if (size >= 0.4) leaves.add(new double[]{end[0], end[1], end[2], size});
            if (node.level() + 1 < levels && twigs + stack.size() + each <= MAX_TWIGS) {
                pushTwigs(stack, end, node.length() * lengthDecay, node.radius() * radiusDecay, node.level() + 1, each, spread);
            }
        }
    }

    private void pushTwigs(Deque<Node> stack, double[] from, double length, double radius, int level, int each, double spread) {
        if (twigs + stack.size() + each > MAX_TWIGS) return;
        double turn = random.nextDouble() * Math.PI * 2.0;
        for (int index = 0; index < each; index++) {
            double[] way = childDirection(new double[]{from[3], from[4], from[5]}, index, each, spread, turn, level + 10);
            stack.push(new Node(from[0], from[1], from[2], way[0], way[1], way[2], length * (1.0 - 0.25 * jitter * random.nextDouble()), radius, level));
        }
    }

    // ---- into the box, and into blocks ---------------------------------------------------------------------------------------------------

    /** Scales the whole tree down (about its base) when it reaches above {@code height} or more than {@code half} out from the trunk; a tree that fits is left as it is. */
    void fit(double height, double half) {
        double flatness = settings.get(TreeSettings.Key.LEAF_FLATNESS);
        double top = 1.0;
        double out = 0.5;
        for (double[] w : wood) {
            top = Math.max(top, w[1] + w[3] + 0.5);
            out = Math.max(out, Math.max(Math.abs(w[0]), Math.abs(w[2])) + w[3]);
        }
        for (double[] l : leaves) {
            top = Math.max(top, l[1] + Math.max(0.6, l[3] * flatness) * 1.4 + 0.5);
            out = Math.max(out, Math.max(Math.abs(l[0]), Math.abs(l[2])) + l[3] * 1.4);
        }
        double scale = Math.min(1.0, Math.min(height / top, half / out));
        if (scale >= 1.0) return;
        for (double[] w : wood) {
            w[0] *= scale;
            w[1] *= scale;
            w[2] *= scale;
            w[3] *= scale;
        }
        for (double[] l : leaves) {
            l[0] *= scale;
            l[1] *= scale;
            l[2] *= scale;
            l[3] *= scale;
        }
    }

    Map<Long, TreePart> voxelize() {
        TreeVoxelizer voxels = new TreeVoxelizer();
        TreeVoxelizer foliageTwigs = new TreeVoxelizer();
        TreePart[] parts = TreePart.values();
        for (double[] w : wood) (w[5] > 0.5 ? foliageTwigs : voxels).ball(w[0], w[1], w[2], w[3], parts[(int) w[4]]);
        double holes = (1.0 - settings.get(TreeSettings.Key.LEAF_DENSITY)) * 0.35;
        double flatness = settings.get(TreeSettings.Key.LEAF_FLATNESS);
        long seed = random.nextLong();
        int shades = settings.leafShades();
        for (double[] l : leaves) {
            double radius = l[3] * (0.92 + 0.16 * random.nextDouble());
            voxels.leafCloud(l[0], l[1], l[2], radius, Math.max(0.6, radius * flatness), holes, shades, seed, random);
        }
        // the twigs of the foliage are its frame: they show only where the leaves leave a gap (they do not dot the surface of the clumps)
        for (Map.Entry<Long, TreePart> entry : foliageTwigs.blocks().entrySet()) voxels.blocks().putIfAbsent(entry.getKey(), entry.getValue());
        voxels.clip(settings);
        return voxels.blocks();
    }

    int branches() {
        return branches;
    }

    int twigs() {
        return twigs;
    }

    int mainLevels() {
        return mainLevels;
    }

    int foliageLevels() {
        return foliageLevels;
    }
}
