package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;

/**
 * The palm: a trunk bending in an S along a random axis and thinning to its top, which may split once into two (one leaning off each way), stilt roots, and at the
 * top of every trunk a crown of fronds in layers (each layer lower, longer and more drooping, turned half a frond from the one above). Ported from the Lua tree
 * script nekorozv gave (t114, pastebin 9evLpaSW); the trunk that splits calls itself there, a stack of trunks here.
 */
final class PalmTree {

    /** A trunk waiting to be drawn (the arguments of drawTrunk in the script). */
    private record Trunk(double x, double y, double z, double height, double startRad, boolean split, double splitTrigger, double crownScale, double sPow,
                         double kickX, double kickZ) {
    }

    private final TreeVoxelizer voxels;
    private final Random random;
    private final double sCurve;
    private final double splitDist;
    private final int layers;
    private final int fronds;
    private final double frondLength;
    private final double frondThickness;
    private final double droop;
    private final double crownPitch;
    private double axisX;
    private double axisZ;

    private PalmTree(TreeSettings s, Random random) {
        this.voxels = new TreeVoxelizer(s.leafShades());
        this.random = random;
        this.sCurve = s.get(TreeSettings.Key.PALM_S_CURVE);
        this.splitDist = s.get(TreeSettings.Key.PALM_SPLIT_DIST);
        this.layers = s.getInt(TreeSettings.Key.PALM_LAYERS);
        this.fronds = s.getInt(TreeSettings.Key.PALM_FRONDS);
        this.frondLength = s.get(TreeSettings.Key.PALM_FROND_LENGTH);
        this.frondThickness = s.get(TreeSettings.Key.PALM_FROND_THICK);
        this.droop = s.get(TreeSettings.Key.PALM_DROOP);
        this.crownPitch = s.get(TreeSettings.Key.PALM_PITCH);
    }

    static Map<Long, TreePart> build(TreeSettings s, Random random) {
        PalmTree tree = new PalmTree(s, random);
        tree.grow(s);
        return tree.voxels.blocks();
    }

    private static double[] normalize(double vx, double vy, double vz) {
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz) + 0.0001;
        return new double[]{vx / length, vy / length, vz / length};
    }

    /** A ball of blocks (the script's own rounding: the offsets are added before the floor). {@code height}: the shade height of a frond, or -1 for wood. */
    private void sphere(double px, double py, double pz, double r, TreePart part, double height) {
        double r2 = r * r;
        int cr = (int) Math.ceil(r);
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dy = -cr; dy <= cr; dy++) {
                for (int dz = -cr; dz <= cr; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    int x = (int) Math.floor(px + dx), y = (int) Math.floor(py + dy), z = (int) Math.floor(pz + dz);
                    if (part == TreePart.LEAF) voxels.leaf(x, y, z, height, TreeVoxelizer.jitter(x, y, z));
                    else voxels.set(x, y, z, part);
                }
            }
        }
    }

    /** The fronds at the top of a trunk. The leaf gradient runs along a frond: its base is the top of the gradient, its drooping tip the bottom. */
    private void crown(double cx, double cy, double cz, double scale) {
        double sLen = Math.max(3.0, frondLength * scale);
        double sThick = Math.max(0.6, frondThickness * scale);
        for (int layer = 1; layer <= layers; layer++) {
            double lPitch = crownPitch - (layer - 1) * 18.0;
            double lDroop = droop + (layer - 1) * 0.4;
            double lLen = sLen * (1 + (layer - 1) * 0.1);
            double lOffset = (layer % 2) * (180.0 / fronds);
            for (int f = 1; f <= fronds; f++) {
                double yaw = Math.toRadians(((double) f / fronds) * 360.0 + lOffset);
                double vx = Math.cos(yaw) * Math.cos(Math.toRadians(lPitch));
                double vy = Math.sin(Math.toRadians(lPitch));
                double vz = Math.sin(yaw) * Math.cos(Math.toRadians(lPitch));
                double fx = cx, fy = cy, fz = cz;
                int steps = (int) Math.ceil(lLen * 2);
                for (int k = 1; k <= steps; k++) {
                    fx += vx * 0.5;
                    fy += vy * 0.5;
                    fz += vz * 0.5;
                    vy -= lDroop / steps;
                    double[] v = normalize(vx, vy, vz);
                    vx = v[0];
                    vy = v[1];
                    vz = v[2];
                    sphere(fx, fy, fz, Math.max(0.5, sThick * (1 - ((double) k / steps) * 0.6)), TreePart.LEAF, 1.0 - (double) k / steps);
                }
            }
        }
    }

    /** Stilt roots: from up the trunk, pushed out round it and down to the ground. */
    private void stiltRoots(double baseRad, double rootFlare) {
        if (rootFlare <= 0) return;
        int rootCount = 3 + (int) Math.floor(baseRad * 0.5);
        double attachHeight = 2.5 + baseRad * 1.2;
        double zz = Math.sin(0.1 * Math.PI) * sCurve;
        double[] trunk = normalize(axisX * zz, 1.2, axisZ * zz);
        for (int r = 1; r <= rootCount; r++) {
            double angle = ((double) r / rootCount) * 6.28;
            double rx = trunk[0] * attachHeight * 0.5, ry = attachHeight, rz = trunk[2] * attachHeight * 0.5;
            int steps = 15;
            for (int k = 1; k <= steps; k++) {
                double t = (double) k / steps;
                rx += Math.cos(angle) * (rootFlare * baseRad * 0.3) * t + trunk[0] * 0.2;
                ry -= attachHeight / steps;
                rz += Math.sin(angle) * (rootFlare * baseRad * 0.3) * t + trunk[2] * 0.2;
                sphere(rx, ry, rz, Math.max(0.4, baseRad * 0.5 * (1 - t * 0.5)), TreePart.ROOT, -1);
                if (ry <= 0.2) break;
            }
        }
    }

    private void grow(TreeSettings s) {
        double angle = random.nextDouble() * 6.28;
        axisX = Math.cos(angle);
        axisZ = Math.sin(angle);
        double splitVal = random.nextDouble() < s.get(TreeSettings.Key.PALM_SPLIT_CHANCE) ? random.nextDouble() * 0.4 + 0.2 : 0;
        double baseRadius = s.get(TreeSettings.Key.PALM_RADIUS);
        stiltRoots(baseRadius, s.get(TreeSettings.Key.PALM_ROOT_REACH));
        Deque<Trunk> stack = new ArrayDeque<>();
        stack.push(new Trunk(0, 0, 0, s.getInt(TreeSettings.Key.PALM_HEIGHT), baseRadius, false, splitVal, 1.0, 1.0, 0, 0));
        while (!stack.isEmpty()) {
            Trunk trunk = stack.pop();
            double cx = trunk.x(), cy = trunk.y(), cz = trunk.z();
            int steps = (int) Math.ceil(trunk.height() * 2.5);
            double endRad = 0.6;
            boolean forked = false;
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                double curRad = endRad + (trunk.startRad() - endRad) * (1.0 - t * t);
                double zz = Math.sin(t * Math.PI) * sCurve * trunk.sPow();
                double kick = trunk.split() ? Math.sin(t * Math.PI * 0.5) : 0;
                double[] v = normalize(axisX * zz + trunk.kickX() * kick, 1.2, axisZ * zz + trunk.kickZ() * kick);
                cx += v[0] * 0.4;
                cy += v[1] * 0.4;
                cz += v[2] * 0.4;
                sphere(cx, cy, cz, curRad, TreePart.TRUNK, -1);
                if (!trunk.split() && trunk.splitTrigger() > 0 && t >= trunk.splitTrigger()) {
                    double dirA = random.nextDouble() * 6.28;
                    double dirB = dirA + 3.14;
                    // (the second is drawn after the first, as in the script)
                    stack.push(new Trunk(cx, cy, cz, trunk.height() * (1 - t) * 1.1, curRad * 0.8, true, 0, 1.0, 0.2, Math.cos(dirB) * splitDist, Math.sin(dirB) * splitDist));
                    stack.push(new Trunk(cx, cy, cz, trunk.height() * (1 - t), curRad * 0.8, true, 0, 0.8, 1.0, Math.cos(dirA) * splitDist, Math.sin(dirA) * splitDist));
                    forked = true;
                    break;
                }
            }
            if (!forked) crown(cx, cy, cz, trunk.crownScale());
        }
    }
}
