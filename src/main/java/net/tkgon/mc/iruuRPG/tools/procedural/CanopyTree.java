package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The canopy tree: a fractal of branches (each node splits into {@code branches} children, {@code lengthMult} as long and {@code radiusMult} as thick, spread round
 * its way, down to {@code depth}), leaf clusters at the tips (round or flattened, willow vines hanging from them), roots round the foot, a straight trunk below
 * and, with more than one canopy level, trunks between canopies stacked one above the other. Twist, centering, gravity and wind bend it. "Beta 1.7.3 leaves"
 * drop every leaf more than 4 blocks from a log, the way leaves decayed in that version. Ported from the Lua tree script nekorozv gave (t114,
 * pastebin EwrJCT0G); its recursion is a loop over a stack here, so no depth of settings can overflow the thread's stack.
 */
final class CanopyTree {

    /** At most this many branches are grown; past it, branches end in leaves. */
    static final int MAX_BRANCHES = 20000;

    /** A branch waiting to be grown: where it starts, its way, its length and radius, its depth (counting down to 0, the tips), and how it was asked for. */
    private record Grow(double x, double y, double z, double vx, double vy, double vz, double length, double radius, int depth, boolean topLevel, boolean canTwist) {
    }

    private final TreeVoxelizer voxels;
    private final Random random;
    private final double lengthStart;
    private final double lengthMult;
    private final double radiusMult;
    private final int depthMax;
    private final int branches;
    private final double spread;
    private final double leafSize;
    private final int leafStartDepth;
    private final double stagger;
    private final double trunkTwist;
    private final double gravity;
    private final double wx;
    private final double wz;
    private final boolean ellipsoid;
    private final boolean willow;
    private final double willowScale;
    private final boolean beta;
    /** With beta leaves: the logs, and the leaves waiting (with their shade) until every log is in. */
    private final Set<Long> logs = new HashSet<>();
    private final Map<Long, TreePart> waitingLeaves = new HashMap<>();
    private int grown;

    private CanopyTree(TreeSettings s, Random random, int shades) {
        this.voxels = new TreeVoxelizer(shades);
        this.random = random;
        this.lengthStart = s.get(TreeSettings.Key.CANOPY_LENGTH);
        this.lengthMult = s.get(TreeSettings.Key.CANOPY_LENGTH_MULT);
        this.radiusMult = s.get(TreeSettings.Key.CANOPY_RADIUS_MULT);
        this.depthMax = s.getInt(TreeSettings.Key.CANOPY_DEPTH);
        this.branches = s.getInt(TreeSettings.Key.CANOPY_BRANCHES);
        this.spread = s.get(TreeSettings.Key.CANOPY_SPREAD);
        this.leafSize = s.get(TreeSettings.Key.CANOPY_LEAF_SIZE);
        this.leafStartDepth = s.getInt(TreeSettings.Key.CANOPY_LEAF_START);
        this.stagger = s.get(TreeSettings.Key.CANOPY_STAGGER);
        this.trunkTwist = s.get(TreeSettings.Key.CANOPY_TWIST);
        this.gravity = s.get(TreeSettings.Key.CANOPY_GRAVITY);
        double windRad = Math.toRadians(s.get(TreeSettings.Key.CANOPY_WIND_DIR));
        double windStr = s.get(TreeSettings.Key.CANOPY_WIND);
        this.wx = Math.cos(windRad) * windStr;
        this.wz = Math.sin(windRad) * windStr;
        this.ellipsoid = s.on(TreeSettings.Key.CANOPY_ELLIPSOID);
        this.willow = s.on(TreeSettings.Key.CANOPY_WILLOW);
        this.willowScale = s.get(TreeSettings.Key.CANOPY_WILLOW_SCALE);
        this.beta = s.on(TreeSettings.Key.CANOPY_BETA_LEAVES);
    }

    static Map<Long, TreePart> build(TreeSettings s, Random random) {
        CanopyTree tree = new CanopyTree(s, random, s.leafShades());
        tree.grow(s);
        return tree.voxels.blocks();
    }

    // ---- blocks --------------------------------------------------------------------------------------------------------------------------

    private static int round(double v) {
        return (int) Math.floor(v + 0.5);
    }

    private void log(int x, int y, int z, TreePart part) {
        voxels.set(x, y, z, part);
        if (beta) logs.add(TreeVoxelizer.pack(x, y, z));
    }

    private void sphere(double px, double py, double pz, double r, TreePart part) {
        int cx = round(px), cy = round(py), cz = round(pz);
        if (r < 0.75) {
            log(cx, cy, cz, part);
            return;
        }
        double r2 = r * r;
        int cr = (int) Math.ceil(r);
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dy = -cr; dy <= cr; dy++) {
                for (int dz = -cr; dz <= cr; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r2) log(cx + dx, cy + dy, cz + dz, part);
                }
            }
        }
    }

    private void leaf(int x, int y, int z, double height) {
        if (beta) waitingLeaves.put(TreeVoxelizer.pack(x, y, z), TreePart.leafAt(height, voxels.shades(), TreeVoxelizer.jitter(x, y, z)));
        else voxels.leaf(x, y, z, height, TreeVoxelizer.jitter(x, y, z));
    }

    private void leafCluster(double px, double py, double pz, double r, boolean tip) {
        int cx = round(px), cy = round(py), cz = round(pz);
        double r2 = r * r;
        int cr = (int) Math.ceil(r);
        double yInvScale = 1.0 / 0.6;
        int yLimit = ellipsoid ? (int) Math.ceil(r * 0.6) : cr;
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dy = -yLimit; dy <= yLimit; dy++) {
                for (int dz = -cr; dz <= cr; dz++) {
                    double dyVal = ellipsoid ? dy * yInvScale : dy;
                    if (dx * dx + dyVal * dyVal + dz * dz <= r2) leaf(cx + dx, cy + dy, cz + dz, yLimit == 0 ? 0.5 : (dy + yLimit) / (2.0 * yLimit));
                }
            }
        }
        if (willow && tip) {
            double strands = (3 + random.nextInt(3)) * willowScale;
            int reach = (int) Math.floor(r * 0.7);
            for (int n = 1; n <= (int) Math.floor(strands + 0.5); n++) {
                int ox = -reach + random.nextInt(2 * reach + 1);
                int oz = -reach + random.nextInt(2 * reach + 1);
                int length = (int) Math.ceil((lengthStart * 0.25 + random.nextDouble() * 3.0) * willowScale);
                for (int v = 1; v <= length; v++) leaf(cx + ox, cy - v, cz + oz, 0.0);
            }
        }
    }

    // ---- the growth ----------------------------------------------------------------------------------------------------------------------

    private static double[] normalize(double vx, double vy, double vz) {
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz) + 0.0001;
        return new double[]{vx / length, vy / length, vz / length};
    }

    /** A plain stretch of trunk (below the first canopy and between canopies); returns its end and way: {x, y, z, vx, vy, vz}. */
    private double[] logSegment(double sx, double sy, double sz, double vx, double vy, double vz, double len, double rad, double twist, double windFactor,
                                double anchorX, double anchorZ, double centering) {
        double cx = sx, cy = sy, cz = sz;
        int steps = (int) Math.ceil(len);
        for (int i = 1; i <= steps; i++) {
            if (twist > 0) {
                double rx = (random.nextDouble() * 2 - 1) * twist * 0.2;
                double rz = (random.nextDouble() * 2 - 1) * twist * 0.2;
                double pullX = (anchorX - cx) * centering * 0.05;
                double pullZ = (anchorZ - cz) * centering * 0.05;
                double[] v = normalize(vx + rx + pullX, vy, vz + rz + pullZ);
                vx = v[0];
                vy = v[1];
                vz = v[2];
            }
            double[] v = normalize(vx + wx * windFactor * 0.1, vy, vz + wz * windFactor * 0.1);
            vx = v[0];
            vy = v[1];
            vz = v[2];
            cx += vx;
            cy += vy;
            cz += vz;
            sphere(cx, cy, cz, rad, TreePart.TRUNK);
        }
        return new double[]{cx, cy, cz, vx, vy, vz};
    }

    /** One canopy: the fractal from (x, y, z), grown branch by branch from a stack. */
    private void canopy(Grow first) {
        Deque<Grow> stack = new ArrayDeque<>();
        stack.push(first);
        while (!stack.isEmpty()) {
            Grow node = stack.pop();
            grown++;
            double rad = node.radius();
            double len = node.length();
            double targetRad = rad * radiusMult;
            double vx = node.vx(), vy = node.vy(), vz = node.vz();
            double[] staggerOffsets = new double[branches];
            double maxStagger = 0;
            for (int b = 0; b < branches; b++) {
                staggerOffsets[b] = random.nextDouble() * stagger * len;
                maxStagger = Math.max(maxStagger, staggerOffsets[b]);
            }
            double effectiveLen = node.topLevel() ? len - maxStagger : len;
            int steps = (int) Math.ceil(effectiveLen);
            double currX = node.x(), currY = node.y(), currZ = node.z();
            TreePart part = node.depth() == depthMax ? TreePart.TRUNK : TreePart.BRANCH;
            for (int i = 1; i <= steps; i++) {
                if (node.depth() == depthMax && trunkTwist > 0 && node.canTwist()) {
                    double rx = (random.nextDouble() * 2 - 1) * trunkTwist * 0.05;
                    double rz = (random.nextDouble() * 2 - 1) * trunkTwist * 0.05;
                    double[] v = normalize(vx + rx, vy, vz + rz);
                    vx = v[0];
                    vy = v[1];
                    vz = v[2];
                }
                if (gravity != 0.0) {
                    vy -= gravity * 0.18 * ((double) i / steps);
                    double[] v = normalize(vx, vy, vz);
                    vx = v[0];
                    vy = v[1];
                    vz = v[2];
                }
                currX += vx;
                currY += vy;
                currZ += vz;
                double currRad = rad + (targetRad - rad) * (i / Math.ceil(len));
                sphere(currX, currY, currZ, Math.max(0.5, currRad), part);
                if (node.depth() <= leafStartDepth && i % 3 == 0) leafCluster(currX, currY, currZ, leafSize * 0.8, false);
            }
            if (node.depth() > 0 && grown + stack.size() + branches <= MAX_BRANCHES) {
                double refVX = vx, refVY = vy, refVZ = vz;
                if (node.depth() == depthMax) {
                    refVX = 0;
                    refVY = 1;
                    refVZ = 0;
                }
                // (two axes across the way: worked out the way the script does, oddities included, so the trees look the same)
                double ux = Math.abs(refVY) > 0.9 ? 1 : 0, uy = 1, uz = 0;
                double ax = uy * refVZ - uz * refVY, ay = uz * refVX - ux * refVZ, az = ux * refVY - uy * refVZ;
                double al = Math.sqrt(ax * ax + ay * ay + az * az) + 0.0001;
                ax /= al;
                ay /= al;
                az /= al;
                double bx = ay * refVZ - az * refVY, by = az * refVX - ax * refVZ, bz = ax * refVY - uy * refVZ;
                double bl = Math.sqrt(bx * bx + by * by + bz * bz) + 0.0001;
                bx /= bl;
                by /= bl;
                bz /= bl;
                Grow[] children = new Grow[branches];
                double s = Math.toRadians(spread);
                for (int b = 1; b <= branches; b++) {
                    double angle = ((double) b / branches) * 6.28 + random.nextDouble() * 0.5;
                    double nx = refVX * Math.cos(s) + ax * Math.sin(s) * Math.cos(angle) + bx * Math.sin(s) * Math.sin(angle);
                    double ny = refVY * Math.cos(s) + ay * Math.sin(s) * Math.cos(angle) + by * Math.sin(s) * Math.sin(angle);
                    double nz = refVZ * Math.cos(s) + az * Math.sin(s) * Math.cos(angle) + bz * Math.sin(s) * Math.sin(angle);
                    double[] d = normalize(nx + wx, ny, nz + wz);
                    double offsetFromEnd = maxStagger - staggerOffsets[b - 1];
                    double staggT = len <= 0 ? 0 : staggerOffsets[b - 1] / len;
                    children[b - 1] = new Grow(currX - refVX * offsetFromEnd, currY - refVY * offsetFromEnd, currZ - refVZ * offsetFromEnd, d[0], d[1], d[2],
                            len * lengthMult, targetRad + (rad - targetRad) * staggT, node.depth() - 1, true, false);
                }
                // (pushed last first, so they are grown in order, as the recursion would)
                for (int b = branches - 1; b >= 0; b--) stack.push(children[b]);
            } else {
                leafCluster(currX, currY, currZ, leafSize, true);
            }
        }
    }

    private void grow(TreeSettings s) {
        double radiusStart = s.get(TreeSettings.Key.CANOPY_RADIUS);
        for (int dy = -2; dy <= 1; dy++) sphere(0, dy, 0, radiusStart, TreePart.TRUNK);
        int rootCount = s.getInt(TreeSettings.Key.CANOPY_ROOT_COUNT);
        double rootLength = s.get(TreeSettings.Key.CANOPY_ROOT_LENGTH);
        double rootHeight = s.get(TreeSettings.Key.CANOPY_ROOT_HEIGHT);
        double rootScale = s.get(TreeSettings.Key.CANOPY_ROOT_SCALE);
        if (rootLength > 0) {
            for (int r = 1; r <= rootCount; r++) {
                double angle = ((double) r / rootCount) * 6.28 + (random.nextDouble() - 0.5);
                double dirX = Math.cos(angle), dirZ = Math.sin(angle);
                double sHeight = rootHeight * rootScale;
                double sLength = rootLength * rootScale;
                int steps = (int) Math.ceil(sLength * 1.5);
                for (int i = 0; i <= steps; i++) {
                    double t = (double) i / steps;
                    double currY = sHeight - sHeight * Math.pow(t, 0.7);
                    if (currY < -0.5) break;
                    double currRad = radiusStart * 0.6 * rootScale * (1 - t * 0.8);
                    sphere(dirX * t * sLength, currY, dirZ * t * sLength, Math.max(0.5, currRad), TreePart.ROOT);
                }
            }
        }
        double[] cur = {0, 0, 0, 0, 1, 0};
        double trunkHeight = s.get(TreeSettings.Key.CANOPY_TRUNK_HEIGHT);
        if (trunkHeight > 0) cur = logSegment(0, 0, 0, 0, 1, 0, trunkHeight, radiusStart, trunkTwist * 0.1, 0.2, 0, 0, 0.0);
        double curL = lengthStart;
        double curR = radiusStart;
        double curCDist = s.get(TreeSettings.Key.CANOPY_LEVEL_DIST) + 20;
        int levels = s.getInt(TreeSettings.Key.CANOPY_LEVELS);
        double upperScale = s.get(TreeSettings.Key.CANOPY_UPPER_SCALE);
        for (int level = 1; level <= levels; level++) {
            boolean top = level == levels;
            double sizeScale = level > 1 ? upperScale : 1.0;
            canopy(new Grow(cur[0], cur[1], cur[2], cur[3], cur[4], cur[5], curL * sizeScale, curR * sizeScale, depthMax, top, level == 1));
            if (!top) {
                double segmentDist = level == 1 ? curCDist * 0.8 : curCDist;
                cur = logSegment(cur[0], cur[1], cur[2], cur[3], cur[4], cur[5], segmentDist, curR * radiusMult, s.get(TreeSettings.Key.CANOPY_UPPER_TWIST), level * 0.5,
                        0, 0, s.get(TreeSettings.Key.CANOPY_CENTERING));
                curCDist *= lengthMult;
                curL *= lengthMult;
                curR *= Math.sqrt(radiusMult);
            }
        }
        if (beta) betaLeaves();
    }

    /** Beta 1.7.3 leaves: a leaf stays only where a log is within 4 steps (along the axes) and no log is there. */
    private void betaLeaves() {
        for (Map.Entry<Long, TreePart> entry : waitingLeaves.entrySet()) {
            long key = entry.getKey();
            if (logs.contains(key)) continue;
            int x = TreeVoxelizer.unpackX(key), y = TreeVoxelizer.unpackY(key), z = TreeVoxelizer.unpackZ(key);
            if (nearLog(x, y, z)) voxels.set(x, y, z, entry.getValue());
        }
    }

    private boolean nearLog(int x, int y, int z) {
        for (int dx = -4; dx <= 4; dx++) {
            int restX = 4 - Math.abs(dx);
            for (int dy = -restX; dy <= restX; dy++) {
                int restY = restX - Math.abs(dy);
                for (int dz = -restY; dz <= restY; dz++) {
                    if (logs.contains(TreeVoxelizer.pack(x + dx, y + dy, z + dz))) return true;
                }
            }
        }
        return false;
    }
}
