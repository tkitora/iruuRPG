package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * (t252, t258, t265 after the birches iru sent) The thin tree. Its skeleton grows as a young tree does, a block at a time: the stems turn back up as they
 * grow (they lean or open at first and then rise side by side) and sway slowly; the trunk runs up to the top (or splits at the height the side branches
 * start into stems that rise together), and short side branches leave it at heights round it by the golden angle, the longest a third up the crown, so
 * the crown is an egg; twigs off them. Over it the wood is dressed thinner the further it is from the foot: logs near the foot (the trunk the heaviest),
 * then walls where a stem runs up and slabs where wood runs out (the bottom or the top half, as high as it passes), fences on the side branches and the
 * thin tops; a log stepping up as it goes over is joined by a stair, and stairs round the foot give it weight. The leaves are laid as with a sphere brush
 * of a few blocks, filled only in part: over the tips and along the side branches, in passes from the darkest leaf to the brightest (each a little
 * higher, so the crown is lit from above), kept off the wood (the branches show in it) but where they hang on the tips, and no leaf left alone; vines can
 * hang from the lowest. The connections of the fences and walls, the halves of the slabs and the ways of the stairs are given as the blocks are put
 * ({@link #shape}).
 */
final class ThinTree {

    /** Where a thin branch passes in a block: its lower half, its upper half (bits), and how far from the foot it is there (the least). */
    private record Pass(int halves, double distance, int level) {
    }

    private final TreeVoxelizer voxels;
    private final Random random;
    private final double kink;
    private final double angle;
    private final double upturn;
    private final double curve;
    private final int logReach;
    private final boolean stairs;
    /** The blocks of the logs (by their distance from the foot along the wood), and those of them that are the trunk. */
    private final Map<Long, Double> logs = new HashMap<>();
    private final Set<Long> trunk = new HashSet<>();
    /** The stairs: the part of each (its way). */
    private final Map<Long, TreePart> steps = new HashMap<>();
    private final Map<Long, Pass> passes = new HashMap<>();
    /** The ends of the branches. */
    private final List<double[]> tips = new ArrayList<>();
    /** Points along the outer branches where leaves hang too. */
    private final List<double[]> along = new ArrayList<>();
    private final double alongEvery;

    private ThinTree(TreeSettings s, Random random) {
        this.voxels = new TreeVoxelizer(s.leafShades());
        this.random = random;
        this.kink = s.get(TreeSettings.Key.THIN_KINK);
        // (the angle a side branch leaves its stem at: 15 to 70 degrees from it)
        this.angle = Math.toRadians(70.0 - 55.0 * s.get(TreeSettings.Key.THIN_RISE));
        this.upturn = s.get(TreeSettings.Key.THIN_UPTURN);
        this.curve = s.get(TreeSettings.Key.THIN_CURVE);
        this.logReach = s.getInt(TreeSettings.Key.THIN_LOG_REACH);
        this.stairs = s.on(TreeSettings.Key.THIN_STAIRS);
        this.alongEvery = s.get(TreeSettings.Key.THIN_LEAF_ALONG);
    }

    static Map<Long, TreePart> build(TreeSettings s, Random random) {
        ThinTree tree = new ThinTree(s, random);
        tree.grow(s);
        tree.wood(s);
        tree.leaves(s);
        return tree.voxels.blocks();
    }

    // ---- the skeleton ------------------------------------------------------------------------------------------------------------

    /** The turn round a stem from one side branch to the next (the golden angle, as leaves and buds follow each other round a shoot). */
    private static final double SPIRAL = Math.PI * (3.0 - Math.sqrt(5.0));

    private void grow(TreeSettings s) {
        int clear = s.getInt(TreeSettings.Key.THIN_HEIGHT);
        int stems = s.getInt(TreeSettings.Key.THIN_STEMS);
        double lean = s.get(TreeSettings.Key.THIN_LEAN);
        int split = s.getInt(TreeSettings.Key.THIN_BRANCHES);
        int side = s.getInt(TreeSettings.Key.THIN_SIDE);
        double crown = s.get(TreeSettings.Key.THIN_LENGTH);
        double reach = s.get(TreeSettings.Key.THIN_SIDE_LENGTH);
        int depth = s.getInt(TreeSettings.Key.THIN_DEPTH);
        double turn = random.nextDouble() * Math.PI * 2.0;
        for (int stem = 0; stem < stems; stem++) {
            // (several stems lean away from each other from the foot and turn up as they rise, the first the most upright and the tallest)
            double azimuth = turn + stem * Math.PI * 2.0 / stems + (random.nextDouble() - 0.5) * 0.6;
            double tilt = lean * 0.5 + (stems > 1 ? 0.35 + 0.1 * stem : 0.0);
            double share = stems > 1 ? 1.0 - 0.18 * stem : 1.0;
            int tall = Math.max(1, (int) Math.round(clear * share));
            double[] way = {Math.sin(tilt) * Math.cos(azimuth), Math.cos(tilt), Math.sin(tilt) * Math.sin(azimuth)};
            double size = crown * share;
            if (split <= 1) {
                // one stem to the top: the trunk itself carries the side branches over its clear height
                leader(0, new double[]{0.0, 0.0, 0.0}, way, tall + size, 0.0, tall, side, reach, depth);
                continue;
            }
            List<double[]> trunk = walk(0, new double[]{0.0, 0.0, 0.0}, way, tall * (1.0 + 0.3 * tilt), 0.0, upturn * 0.3, curve);
            double[] at = trunk.get(trunk.size() - 1);
            double[] up = wayAt(trunk, 1.0);
            // the split: stems that open a little and turn back up, so they rise side by side (the first nearly straight on and the longest)
            double first = random.nextDouble() * Math.PI * 2.0;
            double length = tall * (1.0 + 0.3 * tilt);
            for (int part = 0; part < split; part++) {
                double around = first + part * Math.PI * 2.0 / split + (random.nextDouble() - 0.5) * 0.5;
                double longest = size * (part == 0 ? 1.0 : 0.7 + 0.2 * random.nextDouble());
                double spread = angle * (part == 0 ? 0.15 : 0.5);
                leader(1, at, turned(up, spread, around), longest, length, 0.0, Math.max(1, (int) Math.round(side * longest / Math.max(1.0, size))), reach, depth);
            }
        }
    }

    /**
     * A stem of {@code level} ({@code length} long, {@code distance} from the foot along the wood at {@code from}) that turns back up as it grows and sways
     * slowly; its side branches start {@code clear} up it: {@code side} of them round it by the golden angle at heights along it, the longest a third up
     * the crown and shorter to its top and its bottom, the crown so an egg. Leaves on its top.
     */
    private void leader(int level, double[] from, double[] way, double length, double distance, double clear, int side, double reach, int depth) {
        List<double[]> points = walk(level, from, way, length, distance, upturn * 0.3, curve);
        tips.add(points.get(points.size() - 1));
        double crown = Math.max(1.0, length - clear);
        double around = random.nextDouble() * Math.PI * 2.0;
        for (int n = 0; n < side; n++) {
            double share = (n + 0.5 + (random.nextDouble() - 0.5) * 0.6) / side * 0.9;
            double t = (clear + crown * share) / length;
            around += SPIRAL + (random.nextDouble() - 0.5) * 0.4;
            double shape = 0.25 + 0.75 * share;
            double long_ = reach * 4.0 * shape * (1.0 - shape) * (0.75 + 0.5 * random.nextDouble());
            if (long_ < 1.0) continue;
            double[] base = pointAt(points, t);
            branch(level + 1, base, outward(turned(wayAt(points, t), angle * (0.85 + 0.3 * random.nextDouble()), around), base), long_, distance + length * t, depth);
        }
    }

    /**
     * A side branch of {@code level}: it kinks a little and turns up a little at its end; {@code depth} more of twigs off it (one or two, round it away from each
     * other); leaves on its tip and every few blocks along it.
     */
    private void branch(int level, double[] from, double[] way, double length, double distance, int depth) {
        List<double[]> points = walk(level, from, way, length, distance, upturn * 0.1, 0.0);
        if (alongEvery > 0.0) {
            for (double t = alongEvery; t < length - 1.0; t += alongEvery) along.add(pointAt(points, t / length));
        }
        if (depth > 0 && length >= 2.5) {
            int twigs = length >= 4.0 ? 2 : 1;
            double around = random.nextDouble() * Math.PI * 2.0;
            for (int n = 0; n < twigs; n++) {
                double t = 0.35 + 0.45 * (n + random.nextDouble()) / twigs;
                around += Math.PI * (0.6 + 0.8 * random.nextDouble());
                double[] base = pointAt(points, t);
                double next = length * (1.0 - t) * (0.6 + 0.3 * random.nextDouble());
                if (next < 1.0) continue;
                branch(level + 1, base, outward(turned(wayAt(points, t), angle * (0.8 + 0.4 * random.nextDouble()), around), base), next, distance + length * t, depth - 1);
            }
        }
        tips.add(points.get(points.size() - 1));
    }

    /**
     * Draws wood from {@code from} a block at a time, {@code length} in all: each block the way turns up by {@code rise} (as a shoot turns to the light), sways
     * with a slow wander of {@code sway} (it keeps a while to one side, so a stem bends rather than shakes) and kinks a little; it never runs down. The points,
     * from the first.
     */
    private List<double[]> walk(int level, double[] from, double[] way, double length, double distance, double rise, double sway) {
        List<double[]> points = new ArrayList<>();
        points.add(from.clone());
        double[] at = from.clone();
        double[] d = normal(way);
        double wanderX = 0.0;
        double wanderZ = 0.0;
        int steps = Math.max(1, (int) Math.ceil(length));
        double step = length / steps;
        for (int n = 0; n < steps; n++) {
            wanderX = wanderX * 0.9 + (random.nextDouble() - 0.5) * 0.25;
            wanderZ = wanderZ * 0.9 + (random.nextDouble() - 0.5) * 0.25;
            d = normal(new double[]{d[0] + wanderX * sway * 0.8 + (random.nextDouble() - 0.5) * kink * 0.3, d[1] + rise + (random.nextDouble() - 0.5) * kink * 0.1,
                    d[2] + wanderZ * sway * 0.8 + (random.nextDouble() - 0.5) * kink * 0.3});
            if (d[1] < 0.0) d = normal(new double[]{d[0], 0.0, d[2]});
            double[] to = {at[0] + d[0] * step, at[1] + d[1] * step, at[2] + d[2] * step};
            line(at, to, level, distance + n * step, distance + (n + 1) * step);
            at = to;
            points.add(at.clone());
        }
        return points;
    }

    /** {@code way} turned by {@code by} away from itself, toward {@code around} (an azimuth round it). */
    private static double[] turned(double[] way, double by, double around) {
        double[] w = normal(way);
        // two ways across it
        double[] a = Math.abs(w[1]) < 0.9 ? normal(cross(w, new double[]{0.0, 1.0, 0.0})) : normal(cross(w, new double[]{1.0, 0.0, 0.0}));
        double[] b = cross(w, a);
        double sin = Math.sin(by);
        double cos = Math.cos(by);
        double[] off = {a[0] * Math.cos(around) + b[0] * Math.sin(around), a[1] * Math.cos(around) + b[1] * Math.sin(around), a[2] * Math.cos(around) + b[2] * Math.sin(around)};
        return normal(new double[]{w[0] * cos + off[0] * sin, w[1] * cos + off[1] * sin, w[2] * cos + off[2] * sin});
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normal(double[] v) {
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return length < 1.0e-9 ? new double[]{0.0, 1.0, 0.0} : new double[]{v[0] / length, v[1] / length, v[2] / length};
    }

    /** {@code way} leaned a little out of the crown (away from the tree's middle, flat), so the crown spreads as a real one does. */
    private static double[] outward(double[] way, double[] at) {
        double flat = Math.hypot(at[0], at[2]);
        if (flat < 0.5) return way;
        return normal(new double[]{way[0] + at[0] / flat * 0.35, way[1], way[2] + at[2] / flat * 0.35});
    }

    /** The point {@code share} of the way along a run of points (by the runs, each as long). */
    private static double[] pointAt(List<double[]> points, double share) {
        double place = Math.max(0.0, Math.min(1.0, share)) * (points.size() - 1);
        int index = Math.min(points.size() - 2, (int) Math.floor(place));
        double f = place - index;
        double[] a = points.get(index);
        double[] b = points.get(index + 1);
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    /** The way a run of points goes {@code share} of the way along it. */
    private static double[] wayAt(List<double[]> points, double share) {
        double place = Math.max(0.0, Math.min(1.0, share)) * (points.size() - 1);
        int index = Math.min(points.size() - 2, (int) Math.floor(place));
        double[] a = points.get(index);
        double[] b = points.get(index + 1);
        return normal(new double[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]});
    }

    /**
     * Draws the wood from {@code a} to {@code b}, {@code from} to {@code to} from the foot along it: a log while it is within {@link #logReach} of the foot
     * (and of the trunk or a main branch), else passes through the halves of the blocks. A block of a point is the one round it; a step that changes both x
     * and z goes through one of the two blocks between, so posts touch by a face; a log that goes up a block as it goes over one is joined by a stair under
     * the higher one (or steps up, without stairs).
     */
    private void line(double[] a, double[] b, int level, double from, double to) {
        double length = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
        int samples = Math.max(1, (int) Math.ceil(length * 4.0));
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        for (int sample = 0; sample <= samples; sample++) {
            double t = (double) sample / samples;
            double x = a[0] + (b[0] - a[0]) * t;
            double y = a[1] + (b[1] - a[1]) * t;
            double z = a[2] + (b[2] - a[2]) * t;
            double distance = from + (to - from) * t;
            int bx = (int) Math.round(x);
            int bz = (int) Math.round(z);
            int by = (int) Math.floor(y + 0.5);
            boolean log = level <= 1 && distance < logReach;
            if (lastX != Integer.MIN_VALUE && bx != lastX && bz != lastZ) mark(bx, lastY, lastZ, y, log, distance, level);
            if (log && lastX != Integer.MIN_VALUE && by == lastY + 1 && (bx != lastX || bz != lastZ)) {
                // (a log one up and one over: a stair under the higher one, its back to the lower, or the log steps up)
                if (stairs) steps(bx, lastY, bz, bx - lastX, bz - lastZ);
                else mark(lastX, by, lastZ, y, true, distance, level);
            }
            mark(bx, by, bz, y, log, distance, level);
            lastX = bx;
            lastY = by;
            lastZ = bz;
        }
    }

    /** A stair at {@code (x, y, z)} its back toward the way it came from ({@code -dx, -dz}). */
    private void steps(int x, int y, int z, int dx, int dz) {
        TreePart part = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? TreePart.BRANCH_STAIR_WEST : TreePart.BRANCH_STAIR_EAST)
                : (dz > 0 ? TreePart.BRANCH_STAIR : TreePart.BRANCH_STAIR_SOUTH);
        steps.putIfAbsent(TreeVoxelizer.pack(x, y, z), part);
    }

    private void mark(int x, int y, int z, double exact, boolean log, double distance, int level) {
        long key = TreeVoxelizer.pack(x, y, z);
        if (log) {
            logs.merge(key, distance, Math::min);
            if (level == 0) trunk.add(key);
            return;
        }
        // (the half of the block the branch passes through: its lower half under its middle, else its upper half)
        int half = exact + 0.5 - y < 0.5 ? 1 : 2;
        Pass before = passes.get(key);
        passes.put(key, before == null ? new Pass(half, distance, level) : new Pass(before.halves() | half, Math.min(before.distance(), distance), Math.min(before.level(), level)));
    }

    /**
     * The wood as blocks: logs (the trunk; the main branches near the foot); a block passed through in both halves a post, a wall within twice the logs'
     * reach of the foot, a fence further out; in one half a slab of that half; the stairs where nothing else is; stairs round the foot.
     */
    private void wood(TreeSettings s) {
        for (Map.Entry<Long, Double> entry : logs.entrySet()) {
            long key = entry.getKey();
            voxels.set(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key), TreeVoxelizer.unpackZ(key), trunk.contains(key) ? TreePart.TRUNK : TreePart.BRANCH);
        }
        for (Map.Entry<Long, Pass> entry : passes.entrySet()) {
            long key = entry.getKey();
            if (logs.containsKey(key)) continue;
            Pass pass = entry.getValue();
            TreePart part = pass.halves() == 3 ? (pass.distance() < logReach * 2.2 && pass.level() <= 1 ? TreePart.BRANCH_WALL : TreePart.BRANCH_FENCE)
                    : pass.halves() == 1 ? TreePart.BRANCH_SLAB : TreePart.BRANCH_SLAB_TOP;
            voxels.set(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key), TreeVoxelizer.unpackZ(key), part);
        }
        for (Map.Entry<Long, TreePart> entry : steps.entrySet()) {
            long key = entry.getKey();
            if (voxels.blocks().containsKey(key)) continue;
            voxels.set(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key), TreeVoxelizer.unpackZ(key), entry.getValue());
        }
        // the foot: stairs round the trunk, their backs to it (two to four of them; two rows for a heavier foot)
        int foot = s.getInt(TreeSettings.Key.THIN_FOOT);
        if (foot > 0) {
            int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            TreePart[] facing = {TreePart.BRANCH_STAIR_WEST, TreePart.BRANCH_STAIR_EAST, TreePart.BRANCH_STAIR, TreePart.BRANCH_STAIR_SOUTH};
            for (int row = 0; row < foot; row++) {
                for (int index = 0; index < 4; index++) {
                    if (row == 0 && foot == 1 && random.nextDouble() < 0.35) continue;
                    if (row == 1 && random.nextDouble() < 0.5) continue;
                    int x = sides[index][0];
                    int z = sides[index][1];
                    if (!voxels.blocks().containsKey(TreeVoxelizer.pack(x, row, z))) voxels.set(x, row, z, facing[index]);
                }
            }
        }
    }

    // ---- the leaves ------------------------------------------------------------------------------------------------------------

    /**
     * Blobs of leaves as a sphere brush lays them, filled in part ({@code THIN_LEAF_DENSITY} a pass): over every tip and every few blocks along the outer
     * branches, in {@code THIN_LEAF_PASSES} passes from the darkest leaf to the brightest, each a little higher and smaller (lit from above); not on the wood
     * nor within {@code THIN_CLEARANCE} of it but near a tip; then no leaf left alone (one with no leaf nor wood by a face). Vines from the first pass.
     */
    private void leaves(TreeSettings s) {
        double size = s.get(TreeSettings.Key.THIN_LEAF_SIZE);
        double density = s.get(TreeSettings.Key.THIN_LEAF_DENSITY) / 100.0;
        double flat = s.get(TreeSettings.Key.THIN_LEAF_FLAT);
        double clearance = s.get(TreeSettings.Key.THIN_CLEARANCE);
        int layers = s.getInt(TreeSettings.Key.THIN_LEAF_PASSES);
        boolean vines = s.on(TreeSettings.Key.THIN_VINES);
        double vineScale = s.get(TreeSettings.Key.THIN_VINE_SCALE);
        int shades = Math.max(1, voxels.shades());
        Set<Long> wood = new HashSet<>(voxels.blocks().keySet());
        Set<Long> clear = new HashSet<>();
        int reach = (int) Math.ceil(clearance);
        if (clearance > 0.0) {
            for (long key : wood) {
                int x = TreeVoxelizer.unpackX(key);
                int y = TreeVoxelizer.unpackY(key);
                int z = TreeVoxelizer.unpackZ(key);
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dy = -reach; dy <= reach; dy++) {
                        for (int dz = -reach; dz <= reach; dz++) {
                            if (dx * dx + dy * dy + dz * dz <= clearance * clearance) clear.add(TreeVoxelizer.pack(x + dx, y + dy, z + dz));
                        }
                    }
                }
            }
        }
        List<double[]> centres = new ArrayList<>();
        for (double[] tip : tips) centres.add(new double[]{tip[0], tip[1], tip[2], 1.0});
        for (double[] point : along) centres.add(new double[]{point[0], point[1], point[2], 0.75});
        Set<Long> leafy = new HashSet<>();
        for (int layer = 0; layer < layers; layer++) {
            int shade = layers <= 1 ? 0 : (int) Math.round(layer * (shades - 1) / (double) (layers - 1));
            for (double[] centre : centres) {
                double r = size * centre[3] * (1.0 - 0.12 * layer) * (0.85 + 0.3 * random.nextDouble());
                double ry = Math.max(0.8, r * (1.0 - 0.6 * flat));
                double cx = centre[0] + (random.nextDouble() - 0.5) * 0.8;
                double cy = centre[1] + ry * 0.3 + layer * 0.4;
                double cz = centre[2] + (random.nextDouble() - 0.5) * 0.8;
                int ri = (int) Math.ceil(r);
                int ryi = (int) Math.ceil(ry);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dy = -ryi; dy <= ryi; dy++) {
                        for (int dz = -ri; dz <= ri; dz++) {
                            int x = (int) Math.round(cx) + dx;
                            int y = (int) Math.round(cy) + dy;
                            int z = (int) Math.round(cz) + dz;
                            double ex = (x - cx) / r;
                            double ey = (y - cy) / ry;
                            double ez = (z - cz) / r;
                            if (ex * ex + ey * ey + ez * ez > 1.0) continue;
                            if (random.nextDouble() > density) continue;
                            long key = TreeVoxelizer.pack(x, y, z);
                            if (wood.contains(key)) continue;
                            boolean nearTip = Math.pow(x - centre[0], 2) + Math.pow(y - centre[1], 2) + Math.pow(z - centre[2], 2) <= 2.25 && centre[3] >= 1.0;
                            if (clear.contains(key) && !nearTip) continue;
                            voxels.set(x, y, z, TreePart.leaf(shade));
                            leafy.add(key);
                            if (vines && layer == 0 && ey < -0.3 && random.nextDouble() < 0.12 * vineScale) {
                                int hang = (int) Math.ceil((1.5 + random.nextDouble() * 4.0) * vineScale);
                                for (int down = 1; down <= hang; down++) {
                                    long below = TreeVoxelizer.pack(x, y - down, z);
                                    if (wood.contains(below)) break;
                                    voxels.set(x, y - down, z, TreePart.leaf(0));
                                    leafy.add(below);
                                }
                            }
                        }
                    }
                }
            }
        }
        // no leaf alone: one with no leaf nor wood by any face goes
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (long key : leafy) {
            int x = TreeVoxelizer.unpackX(key);
            int y = TreeVoxelizer.unpackY(key);
            int z = TreeVoxelizer.unpackZ(key);
            boolean touches = false;
            for (int[] face : faces) {
                if (voxels.blocks().containsKey(TreeVoxelizer.pack(x + face[0], y + face[1], z + face[2]))) {
                    touches = true;
                    break;
                }
            }
            if (!touches) voxels.blocks().remove(key);
        }
    }

    // ---- the shapes of the blocks ------------------------------------------------------------------------------------------------------

    /** Which of the four sides (north, east, south, west) a fence or a wall at {@code (x, y, z)} joins wood on. Pure, for the tests. */
    static boolean[] joined(Map<Long, TreePart> blocks, int x, int y, int z) {
        int[][] steps = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        boolean[] joined = new boolean[4];
        for (int index = 0; index < 4; index++) {
            TreePart next = blocks.get(TreeVoxelizer.pack(x + steps[index][0], y, z + steps[index][1]));
            joined[index] = next != null && joins(next);
        }
        return joined;
    }

    /** Whether a wall shows its post: unless it runs straight through (two opposite sides) with no wood over it, as the game makes it. Pure, for the tests. */
    static boolean post(Map<Long, TreePart> blocks, boolean[] joined, int x, int y, int z) {
        int count = 0;
        for (boolean side : joined) if (side) count++;
        boolean straight = count == 2 && (joined[0] && joined[2] || joined[1] && joined[3]);
        TreePart over = blocks.get(TreeVoxelizer.pack(x, y + 1, z));
        return !straight || over != null && joins(over);
    }

    /** The parts of wood a fence or a wall joins to. */
    private static boolean joins(TreePart part) {
        return part == TreePart.TRUNK || part == TreePart.TRUNK_X || part == TreePart.TRUNK_Z || part == TreePart.BRANCH || part == TreePart.ROOT
                || part == TreePart.BRANCH_WALL || part == TreePart.BRANCH_FENCE;
    }

    /**
     * The block a part is put as at {@code (x, y, z)} of {@code blocks}: a fence joined to the wood next to it, a wall too (its post up unless it runs
     * straight through with nothing over it), a top slab in the upper half; the rest as {@code look} is. Not shared: a new block data when it is shaped.
     */
    static org.bukkit.block.data.BlockData shape(org.bukkit.block.data.BlockData look, TreePart part, Map<Long, TreePart> blocks, int x, int y, int z) {
        // (t282: the blocks of an ornate tree carry their own states and light levels)
        if (blocks instanceof OrnateTree.Blocks ornate) {
            org.bukkit.block.data.BlockData own = ornate.data(look, part, x, y, z);
            if (own != null) return own;
        }
        // (t295: a building's blocks carry their shapes and states: a stair of the roof is the roof's block's stairs)
        if (blocks instanceof BuildingShape.Blocks building) {
            org.bukkit.block.data.BlockData own = building.data(look, part, x, y, z);
            if (own != null) return own;
        }
        if (part == TreePart.BRANCH_SLAB_TOP && look instanceof org.bukkit.block.data.type.Slab slab) {
            org.bukkit.block.data.type.Slab top = (org.bukkit.block.data.type.Slab) slab.clone();
            top.setType(org.bukkit.block.data.type.Slab.Type.TOP);
            return top;
        }
        // (t258) a stair turned its way, its bottom half full
        org.bukkit.block.BlockFace stairWay = switch (part) {
            case BRANCH_STAIR, BRANCH_STAIR_TOP -> org.bukkit.block.BlockFace.NORTH;
            case BRANCH_STAIR_EAST, BRANCH_STAIR_TOP_EAST -> org.bukkit.block.BlockFace.EAST;
            case BRANCH_STAIR_SOUTH, BRANCH_STAIR_TOP_SOUTH -> org.bukkit.block.BlockFace.SOUTH;
            case BRANCH_STAIR_WEST, BRANCH_STAIR_TOP_WEST -> org.bukkit.block.BlockFace.WEST;
            default -> null;
        };
        if (stairWay != null && look instanceof org.bukkit.block.data.type.Stairs stair) {
            boolean top = part == TreePart.BRANCH_STAIR_TOP || part == TreePart.BRANCH_STAIR_TOP_EAST || part == TreePart.BRANCH_STAIR_TOP_SOUTH
                    || part == TreePart.BRANCH_STAIR_TOP_WEST;
            org.bukkit.block.data.type.Stairs turned = (org.bukkit.block.data.type.Stairs) stair.clone();
            turned.setFacing(stairWay);
            turned.setHalf(top ? org.bukkit.block.data.Bisected.Half.TOP : org.bukkit.block.data.Bisected.Half.BOTTOM);
            return turned;
        }
        // (a log lying along x or z)
        if ((part == TreePart.TRUNK_X || part == TreePart.TRUNK_Z) && look instanceof org.bukkit.block.data.Orientable log
                && log.getAxes().contains(part == TreePart.TRUNK_X ? org.bukkit.Axis.X : org.bukkit.Axis.Z)) {
            org.bukkit.block.data.Orientable laid = (org.bukkit.block.data.Orientable) log.clone();
            laid.setAxis(part == TreePart.TRUNK_X ? org.bukkit.Axis.X : org.bukkit.Axis.Z);
            return laid;
        }
        if (part != TreePart.BRANCH_FENCE && part != TreePart.BRANCH_WALL) return look;
        org.bukkit.block.BlockFace[] faces = {org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST};
        boolean[] joined = joined(blocks, x, y, z);
        if (look instanceof org.bukkit.block.data.type.Wall wall) {
            org.bukkit.block.data.type.Wall shaped = (org.bukkit.block.data.type.Wall) wall.clone();
            for (int index = 0; index < 4; index++) {
                shaped.setHeight(faces[index], joined[index] ? org.bukkit.block.data.type.Wall.Height.LOW : org.bukkit.block.data.type.Wall.Height.NONE);
            }
            shaped.setUp(post(blocks, joined, x, y, z));
            return shaped;
        }
        if (look instanceof org.bukkit.block.data.MultipleFacing facing) {
            org.bukkit.block.data.MultipleFacing shaped = (org.bukkit.block.data.MultipleFacing) facing.clone();
            for (int index = 0; index < 4; index++) {
                if (shaped.getAllowedFaces().contains(faces[index])) shaped.setFace(faces[index], joined[index]);
            }
            return shaped;
        }
        return look;
    }
}
