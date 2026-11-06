package net.tkgon.mc.iruuRPG.tools.procedural.ornate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Turns the bones of an ornate tree into blocks:
 * <ol>
 *     <li>the thick wood (radius 0.5 or more): its shape ({@link Field}) is measured in the 8 octants of every block and the mask is turned into the nearest block
 *     ({@link BlockShapes#nearest}: a log, a stair with its corners, a slab);</li>
 *     <li>the thin wood: the line of every thin segment is followed block by block, only through faces (6 neighbours), and each block gets the blocks it could be
 *     by its radius and its lean (upright: a wall, a fence; aslant: a stair, slabs stepping; level: a slab, a trapdoor, at a tip an open fence gate; the upper or
 *     lower half by where the line is in the block);</li>
 *     <li>the choice: a dynamic program over the tree of thin blocks picks one for each, so that every block is joined to the one it grows from
 *     ({@link BlockShapes#joined}: no gaps), a branch never gets thicker than what it grows from, and the choice follows the table; where nothing joins, a log is put
 *     as a knot;</li>
 *     <li>the check that every block hangs on the trunk through joined faces (a thick block that does not is made a full block, or goes);</li>
 *     <li>the decoration, only on the trunk and the roots (radius 1.5 or more, 3 blocks or more from a tip, not in the inner angle of a fork): open trapdoors as
 *     streaks of bark, stairs in the hollows, fences under the roots near the foot. It may not change a block by more than half a block of volume, and a limb whose
 *     outline (seen from the side, the front and above) or whose way (its main axis) changes too much gets none;</li>
 *     <li>the leaves by the style (clumps at the tips, flat pads, a spruce's layers along its branches, a willow's twigs), and strands hanging from them (t286);</li>
 *     <li>the shading: a light value from the ambient occlusion, the curvature and a point light (with shadows), turned into 5 levels (-2 dark .. 2 light):
 *     dithered on the thick wood, smoothed along the branches with a hysteresis on the thin wood. The blocks of each level are chosen when the tree is put, by the
 *     lightness of the blocks.</li>
 * </ol>
 * Arithmetic only: it runs off the main thread.
 */
public final class OrnateBuilder {

    /**
     * The numbers of the tree: the bones, the smoothing at the foot, the decoration, the weights of the shading, the light's way, the leaves, how often a stair
     * like the one next to it is made another block ({@code stairMix}, t286), the hanging strands under the leaves (how many, how long), the ground (null: not
     * known; the strands stop at it) and whether the roots are laid on it ({@code snap}).
     */
    public record Params(SkeletonGenerator.Params bones, double flare, double decor, double ao, double curvature, double light, double lightDir,
                         double leaf, double leafFlat, double leafDensity, double stairMix, double hang, double hangLength, Ground ground, boolean snap) {

        /** The same without stair mixing, strands or ground (t282's numbers). */
        public Params(SkeletonGenerator.Params bones, double flare, double decor, double ao, double curvature, double light, double lightDir, double leaf,
                      double leafFlat, double leafDensity) {
            this(bones, flare, decor, ao, curvature, light, lightDir, leaf, leafFlat, leafDensity, 0.0, 0.0, 0.0, null, false);
        }
    }

    /**
     * A block of wood: its shape, its block states beyond the shape's own (the axis of a log, the arms of a fence or a wall), its light level (-2..2), what it is part
     * of ({@link Skeleton#TRUNK}, {@link Skeleton#BRANCH}, {@link Skeleton#ROOT}) and whether it is a decoration.
     */
    public record Wood(BlockShapes.Shape shape, String props, int level, int part, boolean decoration) {
    }

    /** What was made: the blocks of thick and thin wood, the knots, the joins that could not be made, the decorations put and taken back, the leaves. */
    public record Stats(int thick, int thin, int knots, int gaps, int decorations, int reverted, int leaves) {
    }

    /**
     * The tree: the wood by {@link #pack}ed block, the leaves by block with their height in their clump (0 the bottom .. 1 the top), the hanging strands by block
     * with how far down the strand each is (0 its top .. 1 its end), the bones, and the block each thin block grows from (for the checks).
     */
    public record Result(Map<Long, Wood> wood, Map<Long, Double> leaves, Map<Long, Double> hang, Stats stats, Skeleton bones, Map<Long, Long> links) {
    }

    /** More blocks than this and the tree is given up. */
    public static final int LIMIT = 1_500_000;

    /** A tree with more blocks than {@link #LIMIT}. */
    public static final class TooBig extends RuntimeException {
        TooBig() {
            super("more than " + LIMIT + " blocks");
        }
    }

    /** The radius from which wood is thick (measured in octants). */
    static final double THICK = 0.5;
    /** The cost of two blocks that are not joined. */
    private static final double GAP = 100.0;
    private static final long NONE = Long.MIN_VALUE;

    private final Params p;
    private final Random random;
    private Skeleton bones;
    private Field field;

    /** A thick block: its shape, the node of the nearest cone, and the octants the wood fills in it. */
    private record Thick(BlockShapes.Shape shape, int node, int mask) {
    }

    private record Option(BlockShapes.Shape shape, double cost) {
    }

    /** A thin block: the block it grows from (thick or thin), the thin blocks that grow from it, what the line is like there, and its choices. */
    private static final class Thin {
        final long key;
        long parent = NONE;
        final List<Long> children = new ArrayList<>(2);
        double r;
        double dx;
        double dy;
        double dz;
        double fracY;
        double toTip;
        int node;
        int part;
        List<Option> options;
        double[] cost;
        BlockShapes.Shape shape;
        double light;
        int level;

        Thin(long key) {
            this.key = key;
        }
    }

    private final Map<Long, Thick> thick = new HashMap<>();
    private final Map<Long, Thin> thin = new LinkedHashMap<>();
    /** The decorations: their shape and the thick block they lean on. */
    private final Map<Long, BlockShapes.Shape> decorations = new HashMap<>();
    private final Map<Long, Long> decorationBase = new HashMap<>();
    private final Map<Long, Double> leaves = new HashMap<>();
    private final Map<Long, Double> hang = new HashMap<>();
    private int knots;
    private int gaps;
    private int reverted;

    private OrnateBuilder(Params p, Random random) {
        this.p = p;
        this.random = random;
    }

    public static Result build(Params p, Random random) {
        return new OrnateBuilder(p, random).run();
    }

    private Result run() {
        bones = SkeletonGenerator.grow(p.bones(), random, p.snap() ? p.ground() : null);
        field = new Field(bones, THICK, p.flare());
        thick();
        if (p.stairMix() > 0.0) breakRuns();
        thin();
        choose();
        cleanup();
        if (p.decor() > 0.0) decorate();
        if (p.leaf() > 0.0) leaves();
        if (p.hang() > 0.0 && p.hangLength() > 0.0) hang();
        Map<Long, Wood> wood = shade();
        Map<Long, Long> links = new HashMap<>();
        for (Thin v : thin.values()) if (v.parent != NONE) links.put(v.key, v.parent);
        return new Result(wood, leaves, hang, new Stats(thick.size(), thin.size(), knots, gaps, decorations.size(), reverted, leaves.size()), bones, links);
    }

    // ---- the blocks ----------------------------------------------------------------------------------------------------------------------

    /** A block as a key (the same as the tool's: x, y, z each in 16 bits from -2048). */
    public static long pack(int x, int y, int z) {
        return ((long) (x + 2048) & 0xFFFFL) | (((long) (y + 2048) & 0xFFFFL) << 16) | (((long) (z + 2048) & 0xFFFFL) << 32);
    }

    public static int unpackX(long key) {
        return (int) (key & 0xFFFFL) - 2048;
    }

    public static int unpackY(long key) {
        return (int) ((key >> 16) & 0xFFFFL) - 2048;
    }

    public static int unpackZ(long key) {
        return (int) ((key >> 32) & 0xFFFFL) - 2048;
    }

    private static long step(long key, int face) {
        int[] s = BlockShapes.STEPS[face];
        return pack(unpackX(key) + s[0], unpackY(key) + s[1], unpackZ(key) + s[2]);
    }

    /** The face of block {@code from} that block {@code to} (a neighbour) is across. */
    private static int faceBetween(long from, long to) {
        return BlockShapes.face(unpackX(to) - unpackX(from), unpackY(to) - unpackY(from), unpackZ(to) - unpackZ(from));
    }

    private BlockShapes.Shape shapeAt(long key) {
        Thick block = thick.get(key);
        if (block != null) return block.shape();
        Thin line = thin.get(key);
        if (line != null) return line.shape;
        return decorations.get(key);
    }

    private void limit() {
        if (thick.size() + thin.size() + leaves.size() + hang.size() > LIMIT) throw new TooBig();
    }

    // ---- 1. the thick wood ---------------------------------------------------------------------------------------------------------------

    private void thick() {
        if (field.empty()) return;
        int[] box = field.bounds();
        for (int x = box[0]; x <= box[3]; x++) {
            for (int y = box[1]; y <= box[4]; y++) {
                for (int z = box[2]; z <= box[5]; z++) {
                    Field.Hit centre = field.hit(x + 0.5, y + 0.5, z + 0.5);
                    // (an octant's middle is 0.44 from the block's: farther out than that, none is inside)
                    if (centre.distance() > 0.45) continue;
                    if (BlockShapes.nearest(mask(x, y, z, 0.0)).kind == BlockShapes.Kind.EMPTY) continue;
                    int mask = mask(x, y, z, 0.0);
                    thick.put(pack(x, y, z), new Thick(BlockShapes.nearest(mask), centre.node(), mask));
                }
            }
            limit();
        }
    }

    /** The octants of block (x, y, z) whose middles are inside the wood grown by {@code grow}. */
    private int mask(int x, int y, int z, double grow) {
        int mask = 0;
        for (int octant = 0; octant < 8; octant++) {
            double ox = x + ((octant & 1) != 0 ? 0.75 : 0.25);
            double oy = y + ((octant & 2) != 0 ? 0.75 : 0.25);
            double oz = z + ((octant & 4) != 0 ? 0.75 : 0.25);
            if (field.distance(ox, oy, oz) < grow) mask |= 1 << octant;
        }
        return mask;
    }

    /** The neighbours a run of the same stairs goes through that come before a block in the order of {@link #breakRuns}: below, west, north, and down aslant. */
    private static final int[][] RUN = {{0, -1, 0}, {-1, 0, 0}, {0, 0, -1}, {-1, -1, 0}, {1, -1, 0}, {0, -1, -1}, {0, -1, 1}};

    /**
     * (t286) Breaks the runs of the same stair in the thick wood (stairs stacked up a trunk, or along a limb at 45 degrees, all alike): going up through the blocks,
     * a stair {@link #alike} one kept before it next to it (below, beside or down aslant) is, with the chance {@code stairMix}, made another block whose octants are
     * near the wood's ({@link #alternative}: a log or a slab).
     */
    private void breakRuns() {
        List<Long> keys = new ArrayList<>(thick.keySet());
        keys.sort((a, b) -> {
            int y = Integer.compare(unpackY(a), unpackY(b));
            if (y != 0) return y;
            int x = Integer.compare(unpackX(a), unpackX(b));
            return x != 0 ? x : Integer.compare(unpackZ(a), unpackZ(b));
        });
        Set<Long> kept = new HashSet<>();
        for (long key : keys) {
            Thick block = thick.get(key);
            if (block.shape().kind != BlockShapes.Kind.STAIRS) continue;
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            boolean repeated = false;
            for (int[] d : RUN) {
                long other = pack(x + d[0], y + d[1], z + d[2]);
                Thick next = thick.get(other);
                if (next != null && alike(next.shape(), block.shape()) && kept.contains(other)) repeated = true;
            }
            if (!repeated || hash(x, y, z, 31) >= p.stairMix()) {
                kept.add(key);
                continue;
            }
            BlockShapes.Shape other = alternative(block, x, y, z);
            if (other == null) {
                kept.add(key);
                continue;
            }
            thick.put(key, new Thick(other, block.node(), block.mask()));
            if (other.kind == BlockShapes.Kind.STAIRS) kept.add(key);
        }
    }

    /** Whether two stairs look alike in a run: the same way and the same half (a corner of the same stair looks the same from most sides). */
    static boolean alike(BlockShapes.Shape a, BlockShapes.Shape b) {
        if (a.kind != BlockShapes.Kind.STAIRS || b.kind != BlockShapes.Kind.STAIRS) return false;
        return a.props.substring(0, a.props.indexOf(",shape=")).equals(b.props.substring(0, b.props.indexOf(",shape=")));
    }

    /**
     * Another block for a stair in a run: a full block or a slab whose octants are within two of the wood's (a full block twice as likely), chosen by chance; only when
     * there is none, a stair of another shape within one octant (a corner).
     */
    private BlockShapes.Shape alternative(Thick block, int x, int y, int z) {
        List<BlockShapes.Shape> choices = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0.0;
        for (BlockShapes.Shape shape : List.of(BlockShapes.FULL, BlockShapes.SLAB_BOTTOM, BlockShapes.SLAB_TOP)) {
            if (Integer.bitCount(shape.octants ^ block.mask()) > 2) continue;
            double weight = shape.kind == BlockShapes.Kind.FULL ? 2.0 : 1.0;
            choices.add(shape);
            weights.add(weight);
            total += weight;
        }
        if (choices.isEmpty()) {
            for (BlockShapes.Shape shape : BlockShapes.solids()) {
                if (shape.kind != BlockShapes.Kind.STAIRS || shape.octants == block.shape().octants || Integer.bitCount(shape.octants ^ block.mask()) > 1) continue;
                choices.add(shape);
                weights.add(1.0);
                total += 1.0;
            }
        }
        if (choices.isEmpty()) return null;
        double roll = hash(x, y, z, 37) * total;
        for (int index = 0; index < choices.size(); index++) {
            roll -= weights.get(index);
            if (roll <= 0.0) return choices.get(index);
        }
        return choices.get(choices.size() - 1);
    }

    // ---- 2. the thin wood ----------------------------------------------------------------------------------------------------------------

    private void thin() {
        for (int node = 1; node < bones.size(); node++) {
            int from = bones.parent(node);
            double ra = bones.startRadius(node);
            double rb = bones.radius(node);
            if (rb >= THICK) continue;
            double t0 = ra >= THICK ? (ra - THICK) / Math.max(1.0e-9, ra - rb) : 0.0;
            double ax = bones.x(from);
            double ay = bones.y(from);
            double az = bones.z(from);
            double ux = bones.x(node) - ax;
            double uy = bones.y(node) - ay;
            double uz = bones.z(node) - az;
            double length = Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (length < 1.0e-6) continue;
            long last = NONE;
            for (double[] cell : walk(ax + ux * t0, ay + uy * t0, az + uz * t0, bones.x(node), bones.y(node), bones.z(node))) {
                int x = (int) cell[0];
                int y = (int) cell[1];
                int z = (int) cell[2];
                long key = pack(x, y, z);
                if (thick.containsKey(key) || thin.containsKey(key)) {
                    last = key;
                    continue;
                }
                double t = t0 + (1.0 - t0) * cell[3];
                Thin v = new Thin(key);
                v.r = ra + (rb - ra) * t;
                v.dx = ux / length;
                v.dy = uy / length;
                v.dz = uz / length;
                v.fracY = Math.max(0.0, Math.min(0.999, ay + uy * t - y));
                v.toTip = bones.toTip(node) + (1.0 - t) * length;
                v.node = node;
                v.part = bones.kind(node);
                if (last == NONE) last = attach(key);
                if (last != NONE) {
                    v.parent = last;
                    Thin up = thin.get(last);
                    if (up != null) up.children.add(key);
                }
                thin.put(key, v);
                last = key;
            }
            limit();
        }
    }

    /** A block of wood next to {@code key} for a thin line that starts in the air to grow from (a thick one first), or NONE. */
    private long attach(long key) {
        for (int face = 0; face < 6; face++) if (thick.containsKey(step(key, face))) return step(key, face);
        for (int face = 0; face < 6; face++) if (thin.containsKey(step(key, face))) return step(key, face);
        return NONE;
    }

    /**
     * The blocks the segment from a to b passes, in order, each next to the one before through a face (the voxel walk of Amanatides and Woo): {x, y, z, t} with t
     * the middle of the stretch of the segment in the block (0 at a, 1 at b).
     */
    static List<double[]> walk(double ax, double ay, double az, double bx, double by, double bz) {
        List<double[]> out = new ArrayList<>();
        int x = (int) Math.floor(ax);
        int y = (int) Math.floor(ay);
        int z = (int) Math.floor(az);
        int ex = (int) Math.floor(bx);
        int ey = (int) Math.floor(by);
        int ez = (int) Math.floor(bz);
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double inf = Double.MAX_VALUE;
        double maxX = dx > 0 ? (x + 1 - ax) / dx : dx < 0 ? (x - ax) / dx : inf;
        double maxY = dy > 0 ? (y + 1 - ay) / dy : dy < 0 ? (y - ay) / dy : inf;
        double maxZ = dz > 0 ? (z + 1 - az) / dz : dz < 0 ? (z - az) / dz : inf;
        double deltaX = dx != 0 ? 1.0 / Math.abs(dx) : inf;
        double deltaY = dy != 0 ? 1.0 / Math.abs(dy) : inf;
        double deltaZ = dz != 0 ? 1.0 / Math.abs(dz) : inf;
        double t = 0.0;
        for (int guard = 0; guard < 4096; guard++) {
            double next = Math.min(maxX, Math.min(maxY, maxZ));
            out.add(new double[]{x, y, z, (t + Math.min(1.0, next)) / 2.0});
            if ((x == ex && y == ey && z == ez) || next >= 1.0) break;
            if (maxX <= maxY && maxX <= maxZ) {
                x += sx;
                t = maxX;
                maxX += deltaX;
            } else if (maxY <= maxZ) {
                y += sy;
                t = maxY;
                maxY += deltaY;
            } else {
                z += sz;
                t = maxZ;
                maxZ += deltaZ;
            }
        }
        return out;
    }

    // ---- 3. the choice of the thin blocks ---------------------------------------------------------------------------------------------------

    /** The kinds in the order of {@link #COSTS}. */
    private static final BlockShapes.Kind[] KINDS = {BlockShapes.Kind.FULL, BlockShapes.Kind.SLAB, BlockShapes.Kind.STAIRS, BlockShapes.Kind.FENCE, BlockShapes.Kind.WALL,
            BlockShapes.Kind.TRAPDOOR, BlockShapes.Kind.GATE};

    /**
     * What each kind costs, by [radius: 0.35 or more / 0.2 to 0.35 / a tip][lean: upright / aslant / level] (-1: not a choice). The table of the design: upright a wall
     * (thick) or a fence; aslant a stair (thick), slabs stepping, a fence (tip); level a slab, a trapdoor, at a tip an open gate. A full block is a knot.
     */
    private static final double[][][] COSTS = {
            {{20, 5, 4, 3, 0, 8, -1}, {20, 2, 0, 5, 3, 7, -1}, {20, 0, 2, 7, 6, 5, -1}},
            {{20, 6, 6, 0, 3, 8, -1}, {20, 0, 1, 3, 5, 4, -1}, {20, 0, 4, 6, 7, 0, 5}},
            {{20, 7, 8, 0, 4, 7, -1}, {20, 3, 5, 0, 5, 2, -1}, {20, 2, 6, 6, 8, 0, 0}},
    };

    private List<Option> options(Thin v) {
        double lean = Math.abs(v.dy);
        int way = lean >= 0.8 ? 0 : lean <= 0.45 ? 2 : 1;
        int size = v.toTip <= 1.5 || v.r < 0.2 ? 2 : v.r >= 0.35 ? 0 : 1;
        double[] costs = COSTS[size][way];
        boolean upper = v.fracY >= 0.5;
        int travel = facing(v.dx, v.dz);
        // (the side a stair's high half is on: the way the branch goes when it goes up, the other way when it goes down)
        int high = travel < 0 ? -1 : v.dy >= 0 ? travel : (travel + 2) % 4;
        boolean alongX = Math.abs(v.dx) >= Math.abs(v.dz);
        List<Option> out = new ArrayList<>();
        for (int index = 0; index < KINDS.length; index++) {
            double cost = costs[index];
            if (cost < 0) continue;
            switch (KINDS[index]) {
                case FULL -> out.add(new Option(BlockShapes.FULL, cost));
                case SLAB -> {
                    out.add(new Option(BlockShapes.SLAB_BOTTOM, cost + (upper ? 2 : 0)));
                    out.add(new Option(BlockShapes.SLAB_TOP, cost + (upper ? 0 : 2)));
                }
                case TRAPDOOR -> {
                    out.add(new Option(BlockShapes.TRAPDOOR_BOTTOM, cost + (upper ? 2 : 0)));
                    out.add(new Option(BlockShapes.TRAPDOOR_TOP, cost + (upper ? 0 : 2)));
                }
                case STAIRS -> {
                    for (int half = 0; half < 2; half++) {
                        for (int facing = 0; facing < 4; facing++) {
                            double fit = high < 0 ? 2 : facing == high ? 0 : facing == (high + 2) % 4 ? 5 : 3;
                            out.add(new Option(BlockShapes.stairs(half, facing, 0), cost + fit + half));
                        }
                    }
                }
                case FENCE -> out.add(new Option(BlockShapes.FENCE, cost));
                case WALL -> out.add(new Option(BlockShapes.WALL, cost));
                case GATE -> {
                    for (int facing = 0; facing < 4; facing++) {
                        // (facing north or south its posts are on the west and east: along x)
                        boolean postsAlongX = facing == 0 || facing == 2;
                        out.add(new Option(BlockShapes.gate(facing), cost + (postsAlongX == alongX ? 0 : 6)));
                    }
                }
                default -> {
                }
            }
        }
        return out;
    }

    /** The way a direction goes on the ground: 0 north, 1 east, 2 south, 3 west; -1 when it is (nearly) straight up or down. */
    static int facing(double dx, double dz) {
        if (Math.max(Math.abs(dx), Math.abs(dz)) < 0.15) return -1;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz > 0 ? 2 : 0;
    }

    /** The cost of {@code child} next to {@code parent} across face {@code face} of the parent: a gap, a branch getting thicker, a change of kind. */
    static double pair(BlockShapes.Shape parent, int face, BlockShapes.Shape child) {
        double cost = BlockShapes.joined(parent, face, child) ? 0.0 : GAP;
        if (child.kind != BlockShapes.Kind.FULL && child.kind.rank() > parent.kind.rank()) cost += 25.0 * (child.kind.rank() - parent.kind.rank());
        if (child.kind != parent.kind) cost += 0.5;
        return cost;
    }

    /** {@link #pair}, and (t286) a stair the same as the one it grows from costs more by the stair mixing: a row of the same stairs looks poor. */
    private double cost(BlockShapes.Shape parent, int face, BlockShapes.Shape child) {
        double cost = pair(parent, face, child);
        if (alike(child, parent)) cost += 8.0 * p.stairMix();
        return cost;
    }

    /** The dynamic program over the tree of thin blocks: the cheapest choice of every block given its parent's, from the tips back; then chosen from the trunk out. */
    private void choose() {
        List<Thin> order = new ArrayList<>(thin.values());
        for (Thin v : order) v.options = options(v);
        for (int index = order.size() - 1; index >= 0; index--) {
            Thin v = order.get(index);
            v.cost = new double[v.options.size()];
            for (int a = 0; a < v.options.size(); a++) {
                double total = v.options.get(a).cost();
                for (long childKey : v.children) {
                    Thin child = thin.get(childKey);
                    int face = faceBetween(v.key, child.key);
                    double best = Double.MAX_VALUE;
                    for (int b = 0; b < child.options.size(); b++) {
                        best = Math.min(best, cost(v.options.get(a).shape(), face, child.options.get(b).shape()) + child.cost[b]);
                    }
                    total += best;
                }
                v.cost[a] = total;
            }
        }
        for (Thin v : order) {
            BlockShapes.Shape from = v.parent == NONE ? null : shapeAt(v.parent);
            int face = v.parent == NONE ? -1 : faceBetween(v.parent, v.key);
            int best = 0;
            double bestCost = Double.MAX_VALUE;
            for (int b = 0; b < v.options.size(); b++) {
                double cost = v.cost[b] + (from == null ? 0.0 : cost(from, face, v.options.get(b).shape()));
                if (cost < bestCost) {
                    bestCost = cost;
                    best = b;
                }
            }
            v.shape = v.options.get(best).shape();
            if (v.shape.kind == BlockShapes.Kind.FULL) knots++;
            if (from != null && !BlockShapes.joined(from, face, v.shape)) gaps++;
        }
    }

    // ---- 4. everything hangs on the trunk ---------------------------------------------------------------------------------------------------

    /**
     * From the blocks on the ground, through joined faces: a thick block that is not reached but is next to one that is becomes a full block (a full block joins the
     * faces it meets), a few times; a thick half block still not reached and next to no wood goes.
     */
    private void cleanup() {
        for (int round = 0; round < 3; round++) {
            Set<Long> reached = reach();
            boolean changed = false;
            for (Map.Entry<Long, Thick> entry : new ArrayList<>(thick.entrySet())) {
                long key = entry.getKey();
                if (reached.contains(key) || entry.getValue().shape().kind == BlockShapes.Kind.FULL) continue;
                for (int face = 0; face < 6; face++) {
                    if (reached.contains(step(key, face))) {
                        thick.put(key, new Thick(BlockShapes.FULL, entry.getValue().node(), entry.getValue().mask()));
                        changed = true;
                        break;
                    }
                }
            }
            if (!changed) break;
        }
        Set<Long> reached = reach();
        for (Map.Entry<Long, Thick> entry : new ArrayList<>(thick.entrySet())) {
            long key = entry.getKey();
            if (reached.contains(key) || entry.getValue().shape().volume > 0.5) continue;
            boolean alone = true;
            for (int face = 0; face < 6 && alone; face++) if (shapeAt(step(key, face)) != null) alone = false;
            if (alone) thick.remove(key);
        }
    }

    /** The wood reached from the ground (the blocks at height 0 and below) through joined faces. */
    private Set<Long> reach() {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long key : thick.keySet()) {
            if (unpackY(key) <= 0) {
                seen.add(key);
                queue.add(key);
            }
        }
        for (long key : thin.keySet()) {
            if (unpackY(key) <= 0) {
                seen.add(key);
                queue.add(key);
            }
        }
        while (!queue.isEmpty()) {
            long key = queue.poll();
            BlockShapes.Shape shape = shapeAt(key);
            for (int face = 0; face < 6; face++) {
                long next = step(key, face);
                if (seen.contains(next)) continue;
                BlockShapes.Shape other = shapeAt(next);
                if (other == null || !BlockShapes.joined(shape, face, other)) continue;
                seen.add(next);
                queue.add(next);
            }
        }
        return seen;
    }

    // ---- 5. the decoration ---------------------------------------------------------------------------------------------------------------

    /** The faces round a block on the ground: west, east, north, south; and the side of the neighbour each is toward (0 north, 1 east, 2 south, 3 west). */
    private static final int[] AROUND = {0, 1, 4, 5};
    private static final int[] SIDE_OF = {1, 3, 2, 0};

    private boolean decorable(int node) {
        int kind = bones.kind(node);
        return (kind == Skeleton.TRUNK || kind == Skeleton.ROOT) && bones.radius(node) >= 1.5 && bones.toTip(node) >= 3.0;
    }

    /** Whether the air at (x, y, z) is in the inner angle of a fork: another limb's wood is near as well. */
    private boolean crotch(int x, int y, int z, int node) {
        int limb = bones.limb(node);
        return field.distanceOther(x + 0.5, y + 0.5, z + 0.5, other -> bones.limb(other) == limb) < 1.2;
    }

    private void decorate() {
        Map<Integer, List<Long>> byLimb = new HashMap<>();
        for (Map.Entry<Long, Thick> entry : new ArrayList<>(thick.entrySet())) {
            long key = entry.getKey();
            Thick block = entry.getValue();
            if (block.shape().kind != BlockShapes.Kind.FULL || !decorable(block.node())) continue;
            int node = block.node();
            int limb = bones.limb(node);
            for (int index = 0; index < 4; index++) {
                long air = step(key, AROUND[index]);
                if (shapeAt(air) != null || leaves.containsKey(air)) continue;
                int x = unpackX(air);
                int y = unpackY(air);
                int z = unpackZ(air);
                if (y < 0 || crotch(x, y, z, node)) continue;
                // (streaks of bark: by the angle round the limb's axis, broken now and then along it)
                if (!streak(x + 0.5, y + 0.5, z + 0.5, node)) continue;
                BlockShapes.Shape board = BlockShapes.trapdoorAgainst(SIDE_OF[index]);
                if (Math.abs(board.volume - Integer.bitCount(mask(x, y, z, 0.0)) / 8.0) > 0.5) continue;
                decorations.put(air, board);
                decorationBase.put(air, key);
                byLimb.computeIfAbsent(limb, k -> new ArrayList<>()).add(air);
            }
        }
        // the hollows: air with wood on three sides or more, filled with the stair its octants are nearest when the wood is grown a little
        Set<Long> hollows = new HashSet<>();
        for (Map.Entry<Long, Thick> entry : thick.entrySet()) {
            if (!decorable(entry.getValue().node())) continue;
            for (int face = 0; face < 6; face++) hollows.add(step(entry.getKey(), face));
        }
        for (long air : hollows) {
            if (shapeAt(air) != null || leaves.containsKey(air)) continue;
            int x = unpackX(air);
            int y = unpackY(air);
            int z = unpackZ(air);
            if (y < 0) continue;
            int walls = 0;
            long base = NONE;
            for (int face = 0; face < 6; face++) {
                Thick next = thick.get(step(air, face));
                if (next != null && decorable(next.node())) {
                    walls++;
                    base = step(air, face);
                }
            }
            if (walls < 3 || hash(x, y, z, 11) >= p.decor()) continue;
            int node = thick.get(base).node();
            if (crotch(x, y, z, node)) continue;
            BlockShapes.Shape fill = BlockShapes.nearest(mask(x, y, z, 0.3));
            if (fill.kind != BlockShapes.Kind.STAIRS) continue;
            if (Math.abs(fill.volume - Integer.bitCount(mask(x, y, z, 0.0)) / 8.0) > 0.5) continue;
            decorations.put(air, fill);
            decorationBase.put(air, base);
            byLimb.computeIfAbsent(bones.limb(node), k -> new ArrayList<>()).add(air);
        }
        // fences under the roots near the foot, down to the ground
        for (Map.Entry<Long, Thick> entry : new ArrayList<>(thick.entrySet())) {
            Thick block = entry.getValue();
            if (bones.kind(block.node()) != Skeleton.ROOT || bones.radius(block.node()) < 1.5) continue;
            long below = step(entry.getKey(), 2);
            int x = unpackX(below);
            int y = unpackY(below);
            int z = unpackZ(below);
            if (y < 0 || shapeAt(below) != null || hash(x, y, z, 17) >= p.decor() * 0.6) continue;
            if (!BlockShapes.joined(block.shape(), 2, BlockShapes.FENCE)) continue;
            for (int depth = 0; depth < 2 && y - depth >= 0; depth++) {
                long at = pack(x, y - depth, z);
                if (shapeAt(at) != null) break;
                decorations.put(at, BlockShapes.FENCE);
                decorationBase.put(at, entry.getKey());
                byLimb.computeIfAbsent(bones.limb(block.node()), k -> new ArrayList<>()).add(at);
            }
        }
        // the checks: a limb whose outline or way changed too much gets no decoration
        for (Map.Entry<Integer, List<Long>> entry : byLimb.entrySet()) {
            int limb = entry.getKey();
            List<Long> added = entry.getValue();
            List<Long> before = new ArrayList<>();
            for (Map.Entry<Long, Thick> block : thick.entrySet()) if (bones.limb(block.getValue().node()) == limb) before.add(block.getKey());
            if (!keepsShape(before, added)) {
                for (long key : added) {
                    decorations.remove(key);
                    decorationBase.remove(key);
                }
                reverted += added.size();
            }
        }
    }

    /** Whether a stretch of the trunk or a root has a streak of bark at this point: by the angle round its axis, in stretches along it. */
    private boolean streak(double x, double y, double z, int node) {
        int from = bones.parent(node);
        if (from < 0) return false;
        double[] axis = SkeletonGenerator.normal(new double[]{bones.x(node) - bones.x(from), bones.y(node) - bones.y(from), bones.z(node) - bones.z(from)});
        double[][] across = SkeletonGenerator.across(axis);
        double rx = x - bones.x(from);
        double ry = y - bones.y(from);
        double rz = z - bones.z(from);
        double along = rx * axis[0] + ry * axis[1] + rz * axis[2];
        double u = rx * across[0][0] + ry * across[0][1] + rz * across[0][2];
        double w = rx * across[1][0] + ry * across[1][1] + rz * across[1][2];
        double radius = Math.max(1.0, bones.radius(node));
        int streaks = Math.max(5, (int) Math.round(Math.PI * 2.0 * radius / 1.6));
        int streak = (int) Math.floor((Math.atan2(w, u) + Math.PI) / (Math.PI * 2.0) * streaks);
        int limb = bones.limb(node);
        if (hash(limb, streak, 0, 5) >= 0.25 + 0.6 * p.decor()) return false;
        // (along the limb: the height for the trunk, the length for a root)
        double position = bones.kind(node) == Skeleton.ROOT ? bones.toTip(node) + along : bones.y(from) + along;
        return hash(limb, streak, (int) Math.floor(position / 3.0), 7) < 0.8;
    }

    /**
     * Whether a limb with its decoration ({@code added}) keeps its shape: its outline seen along x, along z and from above, in octants (half blocks), changes by 3% or
     * less each, and its way (the main axis of its octants) turns by 10 degrees or less.
     */
    private boolean keepsShape(List<Long> before, List<Long> added) {
        List<double[]> plain = octants(before, null);
        List<Long> all = new ArrayList<>(before);
        all.addAll(added);
        List<double[]> decorated = octants(all, added);
        if (plain.isEmpty()) return true;
        for (int view = 0; view < 3; view++) {
            Set<Long> a = outline(plain, view);
            Set<Long> b = outline(decorated, view);
            int changed = 0;
            for (long pixel : a) if (!b.contains(pixel)) changed++;
            for (long pixel : b) if (!a.contains(pixel)) changed++;
            if (changed > Math.max(2, a.size() * 0.03)) return false;
        }
        double[] first = axis(plain);
        double[] second = axis(decorated);
        double cos = Math.abs(first[0] * second[0] + first[1] * second[1] + first[2] * second[2]);
        return cos >= Math.cos(Math.toRadians(10.0));
    }

    /** The middles of the filled octants of the blocks (a decoration by its own shape). */
    private List<double[]> octants(List<Long> keys, List<Long> decorated) {
        List<double[]> out = new ArrayList<>();
        for (long key : keys) {
            BlockShapes.Shape shape = decorated != null && decorated.contains(key) ? decorations.get(key) : thick.get(key) == null ? null : thick.get(key).shape();
            if (shape == null) continue;
            for (int octant = 0; octant < 8; octant++) {
                if ((shape.octants & (1 << octant)) == 0) continue;
                out.add(new double[]{unpackX(key) + ((octant & 1) != 0 ? 0.75 : 0.25), unpackY(key) + ((octant & 2) != 0 ? 0.75 : 0.25),
                        unpackZ(key) + ((octant & 4) != 0 ? 0.75 : 0.25)});
            }
        }
        return out;
    }

    /** The octants seen along x (view 0), along z (1) or from above (2), as pixels of half a block. */
    private static Set<Long> outline(List<double[]> points, int view) {
        Set<Long> out = new HashSet<>();
        for (double[] point : points) {
            int a = (int) Math.floor(point[view == 0 ? 2 : 0] * 2.0);
            int b = (int) Math.floor(point[view == 2 ? 2 : 1] * 2.0);
            out.add(((long) a << 32) ^ (b & 0xFFFFFFFFL));
        }
        return out;
    }

    /** The main axis of the points (the eigenvector of the largest eigenvalue of their covariance, by power iteration). */
    static double[] axis(List<double[]> points) {
        double mx = 0;
        double my = 0;
        double mz = 0;
        for (double[] point : points) {
            mx += point[0];
            my += point[1];
            mz += point[2];
        }
        int n = Math.max(1, points.size());
        mx /= n;
        my /= n;
        mz /= n;
        double[][] c = new double[3][3];
        for (double[] point : points) {
            double[] d = {point[0] - mx, point[1] - my, point[2] - mz};
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) c[i][j] += d[i] * d[j];
        }
        double[] v = {0.3, 1.0, 0.2};
        for (int iteration = 0; iteration < 40; iteration++) {
            double[] next = new double[3];
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) next[i] += c[i][j] * v[j];
            double length = SkeletonGenerator.norm(next);
            if (length < 1.0e-12) return new double[]{0.0, 1.0, 0.0};
            for (int i = 0; i < 3; i++) v[i] = next[i] / length;
        }
        return v;
    }

    // ---- 6. the leaves -------------------------------------------------------------------------------------------------------------------

    /**
     * The leaves by the style: clumps at the tips (the oak, the birch, the cherry wider and flatter); flat pads over the tips of a bonsai and an acacia; a spruce's
     * small clumps all along its branches (a cone of layers); a willow's small clumps along its hanging twigs.
     */
    private void leaves() {
        int style = p.bones().style();
        double flat = p.leafFlat();
        boolean[] hanging = style == SkeletonGenerator.WILLOW ? hangingTwigs() : null;
        for (int node = 1; node < bones.size(); node++) {
            if (bones.kind(node) == Skeleton.ROOT) continue;
            boolean tip = bones.tip(node);
            double size = p.leaf() * (0.85 + 0.3 * random.nextDouble());
            switch (style) {
                case SkeletonGenerator.BONSAI -> {
                    if (tip) clump(node, size * 1.35, Math.max(0.7, flat), 0.6);
                }
                case SkeletonGenerator.ACACIA -> {
                    if (tip) clump(node, size * 1.3, Math.max(0.85, flat), 0.5);
                }
                case SkeletonGenerator.SPRUCE -> {
                    if (bones.kind(node) == Skeleton.BRANCH) clump(node, size * (tip ? 0.9 : 0.8), Math.max(0.6, flat), 0.2);
                    else if (tip) clump(node, size * 0.8, 0.2, 0.3);
                }
                case SkeletonGenerator.WILLOW -> {
                    if (tip) clump(node, size * 0.65, flat, 0.1);
                    else if (hanging[node]) clump(node, size * 0.5, flat, 0.0);
                    else if (bones.kind(node) == Skeleton.BRANCH && node % 2 == 0) clump(node, size * 0.6, flat, 0.3);
                }
                case SkeletonGenerator.BIRCH -> {
                    if (tip) clump(node, size * 0.8, flat, 0.2);
                }
                case SkeletonGenerator.CHERRY -> {
                    if (tip) clump(node, size * 1.1, Math.max(0.45, flat), 0.3);
                }
                default -> {
                    if (tip) clump(node, size, flat, 0.2);
                }
            }
            limit();
        }
    }

    /** The nodes of a willow's hanging twigs: branches going down, past half the height of the tree's crown. */
    private boolean[] hangingTwigs() {
        boolean[] out = new boolean[bones.size()];
        for (int node = 1; node < bones.size(); node++) {
            int from = bones.parent(node);
            out[node] = bones.kind(node) == Skeleton.BRANCH && bones.y(node) < bones.y(from) - 0.3;
        }
        return out;
    }

    /** A clump of leaves round {@code node}: {@code size} round, flattened by {@code flat}, lifted {@code lift} over the node, a rough edge, not every block filled. */
    private void clump(int node, double size, double flat, double lift) {
        double squash = 1.0 - 0.65 * flat;
        double density = p.leafDensity() / 100.0;
        double cx = bones.x(node);
        double cy = bones.y(node) + lift;
        double cz = bones.z(node);
        double tall = size * squash;
        int reach = (int) Math.ceil(size + 1);
        int up = (int) Math.ceil(tall + 1);
        for (int x = (int) Math.floor(cx) - reach; x <= (int) Math.floor(cx) + reach; x++) {
            for (int y = (int) Math.floor(cy) - up; y <= (int) Math.floor(cy) + up; y++) {
                for (int z = (int) Math.floor(cz) - reach; z <= (int) Math.floor(cz) + reach; z++) {
                    double dx = x + 0.5 - cx;
                    double dy = (y + 0.5 - cy) / Math.max(0.2, squash);
                    double dz = z + 0.5 - cz;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz) + (hash(x, y, z, 23) - 0.5) * 0.9;
                    if (d > size || y < 0) continue;
                    long key = pack(x, y, z);
                    if (shapeAt(key) != null || hash(x, y, z, 29) >= density) continue;
                    double height = Math.max(0.0, Math.min(1.0, (y + 0.5 - (cy - tall)) / Math.max(0.5, 2.0 * tall)));
                    leaves.merge(key, height, Math::max);
                }
            }
        }
    }

    /**
     * (t286) The hanging strands: from leaves with air under them (and a willow's hanging tips), a share of them by {@code hang}, straight down {@code hangLength}
     * blocks give or take, through the air only, never into the ground.
     */
    private void hang() {
        List<Long> starts = new ArrayList<>();
        for (long key : leaves.keySet()) if (!leaves.containsKey(step(key, 2)) && shapeAt(step(key, 2)) == null) starts.add(key);
        if (p.bones().style() == SkeletonGenerator.WILLOW) {
            for (int node = 1; node < bones.size(); node++) {
                if (bones.tip(node) && bones.kind(node) == Skeleton.BRANCH) starts.add(pack((int) Math.floor(bones.x(node)), (int) Math.floor(bones.y(node)), (int) Math.floor(bones.z(node))));
            }
        }
        starts.sort(Long::compare);
        for (long start : starts) {
            int x = unpackX(start);
            int z = unpackZ(start);
            if (hash(x, unpackY(start), z, 41) >= p.hang() * 0.45) continue;
            int length = Math.max(1, (int) Math.round(p.hangLength() * (0.4 + 0.6 * hash(x, unpackY(start), z, 43))));
            int floor = p.ground() == null ? 0 : p.ground().top(x, z) == Ground.NONE ? 0 : p.ground().top(x, z) + 1;
            for (int down = 1; down <= length; down++) {
                int y = unpackY(start) - down;
                long key = pack(x, y, z);
                if (y < Math.max(0, floor) || shapeAt(key) != null || leaves.containsKey(key) || hang.containsKey(key)) break;
                hang.put(key, down / (double) length);
            }
        }
        limit();
    }

    // ---- 7. the shading ------------------------------------------------------------------------------------------------------------------

    private static final double[][] RAYS;

    static {
        List<double[]> rays = new ArrayList<>();
        for (int[] s : BlockShapes.STEPS) rays.add(new double[]{s[0], s[1], s[2]});
        for (int x = -1; x <= 1; x += 2) for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) rays.add(SkeletonGenerator.normal(new double[]{x, y, z}));
        RAYS = rays.toArray(new double[0][]);
    }

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private Map<Long, Wood> shade() {
        Map<Long, BlockShapes.Shape> all = new HashMap<>();
        for (Map.Entry<Long, Thick> entry : thick.entrySet()) all.put(entry.getKey(), entry.getValue().shape());
        for (Thin v : thin.values()) all.put(v.key, v.shape);
        all.putAll(decorations);
        Map<Long, Wood> out = new HashMap<>();
        if (all.isEmpty()) return out;
        Occupancy occupied = new Occupancy(all.keySet(), leaves.keySet());
        int top = occupied.maxY;
        double span = Math.max(10.0, Math.max(occupied.maxX - occupied.minX, Math.max(occupied.maxZ - occupied.minZ, occupied.maxY - occupied.minY)));
        double angle = Math.toRadians(p.lightDir());
        double[] lamp = {0.5 + Math.cos(angle) * span, top + span * 0.5, 0.5 + Math.sin(angle) * span};
        double weights = p.ao() + p.curvature() + p.light();
        Map<Long, Double> light = new HashMap<>();
        double sum = 0;
        double sum2 = 0;
        for (Map.Entry<Long, BlockShapes.Shape> entry : all.entrySet()) {
            long key = entry.getKey();
            double cx = unpackX(key) + 0.5;
            double cy = unpackY(key) + 0.5;
            double cz = unpackZ(key) + 0.5;
            double value = 0.5;
            if (weights > 0.0) {
                double ao = p.ao() > 0.0 ? occlusion(occupied, cx, cy, cz) : 0.0;
                double[] toLamp = SkeletonGenerator.normal(new double[]{lamp[0] - cx, lamp[1] - cy, lamp[2] - cz});
                double[] n;
                double curve = 0.6;
                Thin v = thin.get(key);
                if (v != null) {
                    // (a thin branch: lit on the side toward the light)
                    double along = toLamp[0] * v.dx + toLamp[1] * v.dy + toLamp[2] * v.dz;
                    n = SkeletonGenerator.normal(new double[]{toLamp[0] - along * v.dx, toLamp[1] - along * v.dy, toLamp[2] - along * v.dz});
                } else if (decorations.containsKey(key)) {
                    long base = decorationBase.get(key);
                    n = SkeletonGenerator.normal(new double[]{cx - unpackX(base) - 0.5, cy - unpackY(base) - 0.5, cz - unpackZ(base) - 0.5});
                    curve = 0.65;
                } else {
                    n = gradient(cx, cy, cz);
                    curve = curvature(cx, cy, cz);
                }
                double lit = Math.max(0.0, n[0] * toLamp[0] + n[1] * toLamp[1] + n[2] * toLamp[2]);
                if (lit > 0.0 && p.light() > 0.0 && shadowed(occupied, cx + n[0] * 0.6, cy + n[1] * 0.6, cz + n[2] * 0.6, lamp)) lit *= 0.35;
                value = (p.ao() * ao + p.curvature() * curve + p.light() * lit) / weights;
            }
            light.put(key, value);
            sum += value;
            sum2 += value * value;
        }
        double mean = sum / all.size();
        double spread = Math.max(0.05, Math.sqrt(Math.max(0.0, sum2 / all.size() - mean * mean)));
        for (Map.Entry<Long, BlockShapes.Shape> entry : all.entrySet()) {
            long key = entry.getKey();
            double level = Math.max(-2.49, Math.min(2.49, (light.get(key) - mean) / spread * 1.1));
            Thin v = thin.get(key);
            if (v != null) {
                v.light = level;
                continue;
            }
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            // (ordered dithering: the block goes to the level above with the chance of how far it is toward it)
            double threshold = BAYER[((x + 2 * z) & 3) + 4 * ((y + z) & 3)] / 16.0;
            int dithered = Math.max(-2, Math.min(2, (int) Math.floor(level + threshold)));
            Thick block = thick.get(key);
            if (block != null) {
                out.put(key, new Wood(block.shape(), block.shape().kind == BlockShapes.Kind.FULL ? axis(block.node()) : block.shape().props, dithered,
                        bones.kind(block.node()), false));
            } else {
                BlockShapes.Shape shape = entry.getValue();
                long base = decorationBase.get(key);
                Thick under = thick.get(base);
                out.put(key, new Wood(shape, props(shape, key, List.of(base)), dithered, under == null ? Skeleton.TRUNK : bones.kind(under.node()), true));
            }
        }
        // the thin wood: smoothed along the branch from the trunk out, and a block keeps the level of the one before unless the light moved well past it
        for (Thin v : thin.values()) {
            Thin up = v.parent == NONE ? null : thin.get(v.parent);
            double smooth = up == null ? v.light : up.light * 0.65 + v.light * 0.35;
            v.light = smooth;
            v.level = up == null || Math.abs(smooth - up.level) > 0.75 ? (int) Math.round(smooth) : up.level;
            v.level = Math.max(-2, Math.min(2, v.level));
            List<Long> neighbours = new ArrayList<>(v.children);
            if (v.parent != NONE) neighbours.add(v.parent);
            String props = v.shape.kind == BlockShapes.Kind.FULL ? axisOf(v.dx, v.dy, v.dz) : props(v.shape, v.key, neighbours);
            out.put(v.key, new Wood(v.shape, props, v.level, v.part, false));
        }
        return out;
    }

    /** The share of 14 rays from the block that leave the tree within 4 blocks. */
    private static double occlusion(Occupancy occupied, double cx, double cy, double cz) {
        int free = 0;
        for (double[] ray : RAYS) {
            boolean blocked = false;
            for (int distance = 1; distance <= 4 && !blocked; distance++) {
                if (occupied.at(cx + ray[0] * distance, cy + ray[1] * distance, cz + ray[2] * distance)) blocked = true;
            }
            if (!blocked) free++;
        }
        return free / (double) RAYS.length;
    }

    /** Whether wood or leaves are between the point and the lamp (the first block and a half is the block itself). */
    private static boolean shadowed(Occupancy occupied, double x, double y, double z, double[] lamp) {
        double dx = lamp[0] - x;
        double dy = lamp[1] - y;
        double dz = lamp[2] - z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (double t = 1.2; t < length; t += 0.7) {
            double px = x + dx / length * t;
            double py = y + dy / length * t;
            double pz = z + dz / length * t;
            if (!occupied.inside(px, py, pz)) return false;
            if (occupied.at(px, py, pz)) return true;
        }
        return false;
    }

    /** The outward way of the thick wood at a point (the gradient of its distance). */
    private double[] gradient(double x, double y, double z) {
        double h = 0.5;
        double gx = field.distance(x + h, y, z) - field.distance(x - h, y, z);
        double gy = field.distance(x, y + h, z) - field.distance(x, y - h, z);
        double gz = field.distance(x, y, z + h) - field.distance(x, y, z - h);
        if (Math.abs(gx) + Math.abs(gy) + Math.abs(gz) < 1.0e-6) return new double[]{0.0, 1.0, 0.0};
        return SkeletonGenerator.normal(new double[]{gx, gy, gz});
    }

    /** How the thick wood bends at a point: 0.5 flat, toward 1 on a ridge (convex), toward 0 in a hollow (the Laplacian of the distance). */
    private double curvature(double x, double y, double z) {
        double h = 0.5;
        double centre = field.distance(x, y, z);
        double sum = field.distance(x + h, y, z) + field.distance(x - h, y, z) + field.distance(x, y + h, z) + field.distance(x, y - h, z)
                + field.distance(x, y, z + h) + field.distance(x, y, z - h) - 6.0 * centre;
        double laplacian = sum / (h * h);
        return 0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, laplacian * 0.35));
    }

    /** The blocks of wood and leaves in a box, for the rays of the shading. */
    private static final class Occupancy {
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        private final boolean[] cells;

        Occupancy(Set<Long> wood, Set<Long> leaves) {
            int x0 = Integer.MAX_VALUE;
            int y0 = Integer.MAX_VALUE;
            int z0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE;
            int y1 = Integer.MIN_VALUE;
            int z1 = Integer.MIN_VALUE;
            List<Set<Long>> sets = List.of(wood, leaves);
            for (Set<Long> set : sets) {
                for (long key : set) {
                    x0 = Math.min(x0, unpackX(key));
                    y0 = Math.min(y0, unpackY(key));
                    z0 = Math.min(z0, unpackZ(key));
                    x1 = Math.max(x1, unpackX(key));
                    y1 = Math.max(y1, unpackY(key));
                    z1 = Math.max(z1, unpackZ(key));
                }
            }
            minX = x0;
            minY = y0;
            minZ = z0;
            maxX = x1;
            maxY = y1;
            maxZ = z1;
            cells = new boolean[(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)];
            for (Set<Long> set : sets) for (long key : set) cells[index(unpackX(key), unpackY(key), unpackZ(key))] = true;
        }

        private int index(int x, int y, int z) {
            return ((x - minX) * (maxY - minY + 1) + (y - minY)) * (maxZ - minZ + 1) + (z - minZ);
        }

        boolean inside(double x, double y, double z) {
            return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
        }

        boolean at(double x, double y, double z) {
            if (!inside(x, y, z)) return false;
            return cells[index((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))];
        }
    }

    // ---- the block states ------------------------------------------------------------------------------------------------------------

    /** The axis of a log on the segment to {@code node}. */
    private String axis(int node) {
        int from = bones.parent(node);
        if (from < 0) return "axis=y";
        return axisOf(bones.x(node) - bones.x(from), bones.y(node) - bones.y(from), bones.z(node) - bones.z(from));
    }

    static String axisOf(double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        // (upright from 45 degrees: a trunk that leans stays a standing log)
        if (ay >= Math.max(ax, az) * 0.8) return "axis=y";
        return ax >= az ? "axis=x" : "axis=z";
    }

    /**
     * The states of a fence (an arm to the fences, gates, logs, walls and stairs of the tree beside it) or a wall (its post, and a low arm to every block of the tree
     * beside it, as the joins were judged); other shapes keep their own.
     */
    private String props(BlockShapes.Shape shape, long key, List<Long> joinedTo) {
        if (shape.kind != BlockShapes.Kind.FENCE && shape.kind != BlockShapes.Kind.WALL) return shape.props;
        boolean[] arms = new boolean[4];
        for (long other : joinedTo) {
            int face = faceBetween(key, other);
            if (face < 0 || face == 2 || face == 3) continue;
            BlockShapes.Shape next = shapeAt(other);
            if (next == null) continue;
            if (shape.kind == BlockShapes.Kind.FENCE && (next.kind == BlockShapes.Kind.SLAB || next.kind == BlockShapes.Kind.TRAPDOOR)) continue;
            arms[SIDE[face]] = true;
        }
        StringBuilder out = new StringBuilder(shape.kind == BlockShapes.Kind.WALL ? "up=true" : "");
        for (int side = 0; side < 4; side++) {
            if (!out.isEmpty()) out.append(',');
            out.append(BlockShapes.FACINGS[side]).append('=');
            out.append(shape.kind == BlockShapes.Kind.WALL ? (arms[side] ? "low" : "none") : String.valueOf(arms[side]));
        }
        return out.toString();
    }

    /** The side (0 north, 1 east, 2 south, 3 west) of each face (-1 for down and up). */
    private static final int[] SIDE = {3, 1, -1, -1, 0, 2};

    /** A number in 0..1 from a block and a salt, always the same. */
    static double hash(int x, int y, int z, int salt) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L + salt * 1274126177L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (h & 0xFFFFFFL) / (double) 0x1000000;
    }
}
