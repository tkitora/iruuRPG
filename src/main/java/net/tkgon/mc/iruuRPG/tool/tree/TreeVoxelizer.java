package net.tkgon.mc.iruuRPG.tool.tree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Turns a skeleton into blocks: every branch is walked in steps of 0.4 block and a ball of its radius is filled at each step. The part comes from the level of the
 * stem (trunk, branch, twig; for a skeleton without levels, from the thickness). So a trunk does not come out as a square post, its radius is changed on the way:
 * it flares out near the ground (the flare setting), has a rough bark (a small change of the radius from place to place), and a ball only takes the blocks well
 * inside its radius, which keeps the corners off a thick stem. Leaves are clumps at the leaf places of the skeleton: ragged ellipsoids (the edge moved in and out
 * from block to block) thinned by the leaf density. Roots are balls along their paths. Where two parts meet the stronger stays ({@link TreePart#priority}).
 * Blocks outside the box of the settings are dropped, so the height and the width are a hard limit. Coordinates of a block: x and z from the block the tree
 * stands on, y up from its bottom (a block with integer coordinates (x, y, z) is centred on (x, y + 0.5, z)).
 */
public final class TreeVoxelizer {

    private final Map<Long, TreePart> blocks = new HashMap<>();

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

    /** The blocks of the tree: the part of every block, by {@link #pack}ed position. */
    public static Map<Long, TreePart> voxelize(TreeSkeleton skeleton, TreeSettings settings, Random random) {
        TreeVoxelizer voxelizer = new TreeVoxelizer();
        voxelizer.fillWood(skeleton, settings);
        voxelizer.fillRoots(skeleton);
        voxelizer.fillLeaves(skeleton, settings, random);
        voxelizer.clip(settings);
        return voxelizer.blocks;
    }

    private void put(int x, int y, int z, TreePart part) {
        long key = pack(x, y, z);
        TreePart old = blocks.get(key);
        if (old == null || part.priority() < old.priority()) blocks.put(key, part);
    }

    /** A ball of {@code radius} blocks round ({@code cx}, {@code cy}, {@code cz}) of {@code part}; the block the centre is in is always filled. */
    private void ball(double cx, double cy, double cz, double radius, TreePart part) {
        int bx = (int) Math.round(cx);
        int by = (int) Math.floor(cy);
        int bz = (int) Math.round(cz);
        put(bx, by, bz, part);
        if (radius < 0.55) return;
        int reach = (int) Math.ceil(radius);
        // only blocks well inside the radius: the corner of a 3 x 3 stays off a stem 1.5 thick
        double inside = radius >= 0.9 ? radius * 0.93 : radius;
        double limit = inside * inside;
        for (int x = bx - reach; x <= bx + reach; x++) {
            for (int y = by - reach; y <= by + reach; y++) {
                for (int z = bz - reach; z <= bz + reach; z++) {
                    double dx = x - cx;
                    double dy = y + 0.5 - cy;
                    double dz = z - cz;
                    if (dx * dx + dy * dy + dz * dz <= limit) put(x, y, z, part);
                }
            }
        }
    }

    private static double roughness(double x, double y, double z) {
        return 0.5 * (Math.sin(x * 2.3 + y * 1.1) + Math.sin(z * 1.9 - y * 2.7 + 1.3));
    }

    private void fillWood(TreeSkeleton skeleton, TreeSettings settings) {
        double trunk = settings.get(TreeSettings.Key.TRUNK_RADIUS);
        double flare = settings.get(TreeSettings.Key.FLARE);
        double flareHeight = Math.max(2.0, trunk * 3.0);
        List<TreeSkeleton.Node> nodes = skeleton.nodes;
        for (int index = 0; index < nodes.size(); index++) {
            TreeSkeleton.Node node = nodes.get(index);
            if (node.parent < 0) {
                ball(node.x, node.y, node.z, node.radius * (1.0 + flare), TreePart.TRUNK);
                continue;
            }
            TreeSkeleton.Node parent = nodes.get(node.parent);
            double dx = node.x - parent.x;
            double dy = node.y - parent.y;
            double dz = node.z - parent.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(length / 0.4));
            for (int step = 0; step <= steps; step++) {
                double t = step / (double) steps;
                double radius = parent.radius + (node.radius - parent.radius) * t;
                double x = parent.x + dx * t;
                double y = parent.y + dy * t;
                double z = parent.z + dz * t;
                TreePart part;
                if (node.level >= 0) {
                    part = node.level == 0 ? TreePart.TRUNK : node.level == 1 || (node.level == 2 && radius >= 0.55) ? TreePart.BRANCH : TreePart.TWIG;
                } else {
                    part = radius >= trunk * 0.6 ? TreePart.TRUNK : radius >= Math.max(0.52, trunk * 0.3) ? TreePart.BRANCH : TreePart.TWIG;
                }
                if (part == TreePart.TRUNK) {
                    // flare near the ground, rough bark
                    double low = Math.max(0.0, 1.0 - y / flareHeight);
                    radius *= (1.0 + flare * low * low) * (1.0 + 0.12 * roughness(x, y, z));
                }
                ball(x, y, z, radius, part);
            }
        }
    }

    private void fillRoots(TreeSkeleton skeleton) {
        for (TreeSkeleton.RootPath path : skeleton.roots) {
            List<double[]> points = path.points();
            for (int index = 1; index < points.size(); index++) {
                double[] a = points.get(index - 1);
                double[] b = points.get(index);
                double length = Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
                int steps = Math.max(1, (int) Math.ceil(length / 0.4));
                for (int step = 0; step <= steps; step++) {
                    double t = step / (double) steps;
                    ball(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, a[3] + (b[3] - a[3]) * t, TreePart.ROOT);
                }
            }
        }
    }

    /** Ragged clumps of leaves at the leaf places of the skeleton, thinned by the leaf density. */
    private void fillLeaves(TreeSkeleton skeleton, TreeSettings settings, Random random) {
        double leaf = settings.get(TreeSettings.Key.LEAF_RADIUS);
        if (leaf < 0.4) return;
        double density = settings.get(TreeSettings.Key.LEAF_DENSITY);
        for (double[] place : skeleton.leaves) {
            double radius = leaf * place[3] * (0.8 + 0.3 * random.nextDouble());
            double vertical = Math.max(0.5, radius * place[4]);
            int bx = (int) Math.round(place[0]);
            int by = (int) Math.floor(place[1]);
            int bz = (int) Math.round(place[2]);
            int reach = (int) Math.ceil(radius) + 1;
            for (int x = bx - reach; x <= bx + reach; x++) {
                for (int y = by - reach; y <= by + reach; y++) {
                    for (int z = bz - reach; z <= bz + reach; z++) {
                        double dx = (x - place[0]) / radius;
                        double dy = (y + 0.5 - place[1]) / vertical;
                        double dz = (z - place[2]) / radius;
                        // the edge of the clump moves in and out from block to block
                        double edge = 1.0 + 0.45 * roughness(x * 1.7, y * 1.3, z * 1.9);
                        if (dx * dx + dy * dy + dz * dz > edge) continue;
                        if (random.nextDouble() > density) continue;
                        put(x, y, z, TreePart.LEAF);
                    }
                }
            }
        }
    }

    /** Drops what is outside the box: the height, and half the width each way from the middle of the trunk (and nothing deeper than 2 blocks into the ground). */
    private void clip(TreeSettings settings) {
        int height = settings.height();
        double half = settings.width() / 2.0;
        blocks.keySet().removeIf(key -> {
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            return y < -2 || y >= height || Math.abs(x) > half || Math.abs(z) > half;
        });
    }
}
