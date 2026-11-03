package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * (t252) The thin tree: a trunk one block thick (one stem or a few from the foot), leaning and bending a little, and thin branches drawn in the blocks
 * builders use for them: where a branch runs up a block it is a post (a wall near the trunk, a fence further out), where it runs along it is a slab (the bottom
 * or the top half, as high as it passes), so a slanting branch steps up in half blocks; the first blocks of the main branches are logs. The branches fork and
 * kink, and each end carries a flattish clump of leaves over it; round the wood the leaves keep a space (but where they hang on the tips), so the branches
 * are seen under and in the crown; a clump can let vines down (a willow). The connections of the fences and the walls and the halves of the slabs are given
 * as the blocks are put ({@link #shape}).
 */
final class ThinTree {

    /** Where a thin branch passes in a block: its lower half, its upper half (bits), and how thin it is there (the least level). */
    private record Pass(int halves, int level) {
    }

    private final TreeVoxelizer voxels;
    private final Random random;
    private final double kink;
    private final double rise;
    private final int logReach;
    /** The blocks of the trunk (level 0) and the log ends of the main branches (level 1). */
    private final Map<Long, Integer> logs = new HashMap<>();
    private final Map<Long, Pass> passes = new HashMap<>();
    /** The ends of the branches: where the clumps of leaves are. */
    private final List<double[]> tips = new ArrayList<>();

    private ThinTree(TreeSettings s, Random random) {
        this.voxels = new TreeVoxelizer(s.leafShades());
        this.random = random;
        this.kink = s.get(TreeSettings.Key.THIN_KINK);
        this.rise = s.get(TreeSettings.Key.THIN_RISE);
        this.logReach = s.getInt(TreeSettings.Key.THIN_LOG_REACH);
    }

    static Map<Long, TreePart> build(TreeSettings s, Random random) {
        ThinTree tree = new ThinTree(s, random);
        tree.grow(s);
        tree.wood();
        tree.leaves(s);
        return tree.voxels.blocks();
    }

    // ---- the skeleton ------------------------------------------------------------------------------------------------------------

    private void grow(TreeSettings s) {
        int height = s.getInt(TreeSettings.Key.THIN_HEIGHT);
        int stems = s.getInt(TreeSettings.Key.THIN_STEMS);
        double lean = s.get(TreeSettings.Key.THIN_LEAN);
        double curve = s.get(TreeSettings.Key.THIN_CURVE);
        int branches = s.getInt(TreeSettings.Key.THIN_BRANCHES);
        int side = s.getInt(TreeSettings.Key.THIN_SIDE);
        double length = s.get(TreeSettings.Key.THIN_LENGTH);
        int depth = s.getInt(TreeSettings.Key.THIN_DEPTH);
        double turn = random.nextDouble() * Math.PI * 2.0;
        for (int stem = 0; stem < stems; stem++) {
            // (several stems lean away from each other from the foot, the first the most upright)
            double azimuth = turn + stem * Math.PI * 2.0 / stems + (random.nextDouble() - 0.5) * 0.6;
            double tilt = lean * 0.45 + (stems > 1 ? 0.18 + 0.1 * stem : 0.0);
            int tall = stems > 1 ? (int) Math.round(height * (1.0 - 0.15 * stem)) : height;
            double phase = random.nextDouble() * Math.PI * 2.0;
            double[] at = {0.0, 0.0, 0.0};
            List<double[]> path = new ArrayList<>();
            path.add(at.clone());
            for (int y = 1; y <= tall; y++) {
                double up = (double) y / tall;
                double sway = curve * 0.9 * Math.sin(phase + y * 0.55);
                at = new double[]{
                        Math.sin(tilt) * Math.cos(azimuth) * y * up + sway * Math.cos(azimuth + Math.PI / 2.0),
                        y,
                        Math.sin(tilt) * Math.sin(azimuth) * y * up + sway * Math.sin(azimuth + Math.PI / 2.0)};
                path.add(at);
            }
            for (int index = 1; index < path.size(); index++) line(path.get(index - 1), path.get(index), 0, 0.0);
            double[] top = path.get(path.size() - 1);
            // branches from the upper trunk, under the crown
            for (int n = 0; n < side; n++) {
                int y = (int) Math.round(tall * (0.45 + 0.4 * random.nextDouble()));
                double[] from = path.get(Math.max(1, Math.min(path.size() - 1, y)));
                double az = random.nextDouble() * Math.PI * 2.0;
                branch(1, from, direction(az, rise * 0.6), length * (0.45 + 0.25 * random.nextDouble()), Math.max(0, depth - 1));
            }
            // the crown: the main branches from the top, evenly round with a little play
            int crown = Math.max(1, stems > 1 ? Math.max(1, branches - stem) : branches);
            double first = random.nextDouble() * Math.PI * 2.0;
            for (int n = 0; n < crown; n++) {
                double az = first + n * Math.PI * 2.0 / crown + (random.nextDouble() - 0.5) * 0.7;
                branch(1, top, direction(az, rise + (random.nextDouble() - 0.5) * 0.25), length * (0.8 + 0.4 * random.nextDouble()), depth);
            }
            // (the stem goes on a little over its fork, a leader)
            tips.add(new double[]{top[0], top[1] + 1.0, top[2]});
        }
    }

    /** A way out at azimuth {@code az} rising by {@code up} (0 level, 1 straight up). */
    private static double[] direction(double az, double up) {
        double elevation = Math.max(-0.2, Math.min(1.0, up)) * Math.PI / 2.0 * 0.85;
        return new double[]{Math.cos(az) * Math.cos(elevation), Math.sin(elevation), Math.sin(az) * Math.cos(elevation)};
    }

    /** A branch of {@code level} from {@code from}: two or three kinked runs, then forks (to {@code depth} more), and a tip at the end. */
    private void branch(int level, double[] from, double[] way, double length, int depth) {
        int runs = length > 5.0 ? 3 : 2;
        double[] at = from.clone();
        double[] d = normal(way);
        double done = 0.0;
        for (int run = 0; run < runs; run++) {
            double part = length / runs;
            // a kink: turned a little, and drawn back toward the rise the branch has
            d = normal(new double[]{d[0] + (random.nextDouble() - 0.5) * kink, d[1] + (random.nextDouble() - 0.5) * kink * 0.6 + (rise - 0.3) * 0.15,
                    d[2] + (random.nextDouble() - 0.5) * kink});
            double[] to = {at[0] + d[0] * part, at[1] + d[1] * part, at[2] + d[2] * part};
            line(at, to, level, done);
            done += part;
            at = to;
        }
        if (depth <= 0 || length < 1.5) {
            tips.add(at);
            return;
        }
        int children = 2 + (random.nextDouble() < 0.25 ? 1 : 0);
        double az = Math.atan2(d[2], d[0]);
        for (int child = 0; child < children; child++) {
            double spread = (child - (children - 1) / 2.0) * (0.55 + 0.35 * random.nextDouble()) + (random.nextDouble() - 0.5) * 0.3;
            double up = Math.asin(Math.max(-1.0, Math.min(1.0, d[1]))) / (Math.PI / 2.0 * 0.85) + (random.nextDouble() - 0.3) * 0.3;
            branch(level + 1, at, direction(az + spread, up), length * (0.55 + 0.2 * random.nextDouble()), depth - 1);
        }
    }

    private static double[] normal(double[] v) {
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return length < 1.0e-9 ? new double[]{0.0, 1.0, 0.0} : new double[]{v[0] / length, v[1] / length, v[2] / length};
    }

    /**
     * Draws the wood from {@code a} to {@code b}: the trunk (level 0) and the first {@link #logReach} blocks of a main branch in logs, the rest as passes
     * through the halves of the blocks. A block of a point is the one round it ({@code round}); a step that changes both x and z goes through one of the
     * two blocks between, so posts touch by a face.
     */
    private void line(double[] a, double[] b, int level, double from) {
        double length = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
        int steps = Math.max(1, (int) Math.ceil(length * 4.0));
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            double x = a[0] + (b[0] - a[0]) * t;
            double y = a[1] + (b[1] - a[1]) * t;
            double z = a[2] + (b[2] - a[2]) * t;
            int bx = (int) Math.round(x);
            int bz = (int) Math.round(z);
            int by = (int) Math.floor(y + 0.5);
            boolean log = level == 0 || level == 1 && from + length * t < logReach;
            if (lastX != Integer.MIN_VALUE && bx != lastX && bz != lastZ) mark(bx, lastY, lastZ, y, log, level);
            // (logs that go up a block as they go over one would touch only by an edge: they go up first, a step; slabs meet so, half over half)
            if (log && lastX != Integer.MIN_VALUE && by != lastY && (bx != lastX || bz != lastZ)) mark(lastX, by, lastZ, y, true, level);
            mark(bx, by, bz, y, log, level);
            lastX = bx;
            lastY = by;
            lastZ = bz;
        }
    }

    private void mark(int x, int y, int z, double exact, boolean log, int level) {
        long key = TreeVoxelizer.pack(x, y, z);
        if (log) {
            logs.merge(key, level, Math::min);
            return;
        }
        // (the half of the block the branch passes through: its lower half under its middle, else its upper half)
        int half = exact + 0.5 - y < 0.5 ? 1 : 2;
        Pass before = passes.get(key);
        passes.put(key, before == null ? new Pass(half, level) : new Pass(before.halves() | half, Math.min(before.level(), level)));
    }

    /** The wood as blocks: logs (the trunk, the log ends of the branches); a block passed through in both halves a post (a wall near the trunk, a fence further out), in one a slab of that half. */
    private void wood() {
        for (Map.Entry<Long, Integer> entry : logs.entrySet()) {
            long key = entry.getKey();
            voxels.set(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key), TreeVoxelizer.unpackZ(key), entry.getValue() == 0 ? TreePart.TRUNK : TreePart.BRANCH);
        }
        for (Map.Entry<Long, Pass> entry : passes.entrySet()) {
            long key = entry.getKey();
            if (logs.containsKey(key)) continue;
            Pass pass = entry.getValue();
            TreePart part = pass.halves() == 3 ? (pass.level() <= 1 ? TreePart.BRANCH_WALL : TreePart.BRANCH_FENCE)
                    : pass.halves() == 1 ? TreePart.BRANCH_SLAB : TreePart.BRANCH_SLAB_TOP;
            voxels.set(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key), TreeVoxelizer.unpackZ(key), part);
        }
    }

    // ---- the leaves ------------------------------------------------------------------------------------------------------------

    /**
     * A clump over each tip: flattened by {@code THIN_LEAF_FLAT}, thinned by the density; no leaf on the wood nor within {@code THIN_CLEARANCE} of it, but
     * near a tip (the clump hangs on it); with vines, strings of leaves down from the underside.
     */
    private void leaves(TreeSettings s) {
        double size = s.get(TreeSettings.Key.THIN_LEAF_SIZE);
        double density = s.get(TreeSettings.Key.THIN_LEAF_DENSITY);
        double flat = s.get(TreeSettings.Key.THIN_LEAF_FLAT);
        double clearance = s.get(TreeSettings.Key.THIN_CLEARANCE);
        boolean vines = s.on(TreeSettings.Key.THIN_VINES);
        double vineScale = s.get(TreeSettings.Key.THIN_VINE_SCALE);
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
        for (double[] tip : tips) {
            double r = size * (0.75 + 0.5 * random.nextDouble());
            double ry = Math.max(0.8, r * (1.0 - 0.6 * flat));
            double cx = tip[0];
            double cy = tip[1] + ry * 0.35;
            double cz = tip[2];
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
                        double inside = ex * ex + ey * ey + ez * ez;
                        if (inside > 1.0 + 0.25 * TreeVoxelizer.jitter(x, y, z)) continue;
                        if (random.nextDouble() * 100.0 > density) continue;
                        long key = TreeVoxelizer.pack(x, y, z);
                        if (wood.contains(key)) continue;
                        boolean nearTip = Math.pow(x - tip[0], 2) + Math.pow(y - tip[1], 2) + Math.pow(z - tip[2], 2) <= 2.25;
                        if (clear.contains(key) && !nearTip) continue;
                        voxels.leaf(x, y, z, (y - (cy - ry)) / (2.0 * ry), TreeVoxelizer.jitter(x, y, z));
                        if (vines && ey < -0.3 && random.nextDouble() < 0.12 * vineScale) {
                            int hang = (int) Math.ceil((1.5 + random.nextDouble() * 4.0) * vineScale);
                            for (int down = 1; down <= hang; down++) {
                                long below = TreeVoxelizer.pack(x, y - down, z);
                                if (wood.contains(below)) break;
                                voxels.leaf(x, y - down, z, 0.0, 0.0);
                            }
                        }
                    }
                }
            }
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
        return part == TreePart.TRUNK || part == TreePart.BRANCH || part == TreePart.ROOT || part == TreePart.BRANCH_WALL || part == TreePart.BRANCH_FENCE;
    }

    /**
     * The block a part is put as at {@code (x, y, z)} of {@code blocks}: a fence joined to the wood next to it, a wall too (its post up unless it runs
     * straight through with nothing over it), a top slab in the upper half; the rest as {@code look} is. Not shared: a new block data when it is shaped.
     */
    static org.bukkit.block.data.BlockData shape(org.bukkit.block.data.BlockData look, TreePart part, Map<Long, TreePart> blocks, int x, int y, int z) {
        if (part == TreePart.BRANCH_SLAB_TOP && look instanceof org.bukkit.block.data.type.Slab slab) {
            org.bukkit.block.data.type.Slab top = (org.bukkit.block.data.type.Slab) slab.clone();
            top.setType(org.bukkit.block.data.type.Slab.Type.TOP);
            return top;
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
