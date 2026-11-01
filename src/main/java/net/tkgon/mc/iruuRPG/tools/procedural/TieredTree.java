package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.Map;
import java.util.Random;

/**
 * The tiered tree: a tapering trunk (it can twist into a corkscrew, or two strands braided round a core, and bend down wind), tiers of straight branches every
 * {@code tierSpacing} blocks above the stump (shorter and more upright higher up, sagging, leaves all along them, snow on top, willow vines from the outer
 * leaves), five shrinking leaf clusters for the top, and roots flaring out of the foot. Ported from the Lua tree script nekorozv gave (t114, pastebin 844aFmQQ).
 */
final class TieredTree {

    private static final double CORKSCREW_RATIO = 1.45;
    private static final double SECONDARY_RADIUS_RATIO = 1.0;

    private final TreeVoxelizer voxels;
    private final Random random;
    private final double leafDensity;
    private final boolean willow;
    private final double willowScale;
    private final boolean snow;
    private final double snowAmount;

    private TieredTree(TreeSettings s, Random random) {
        this.voxels = new TreeVoxelizer(s.leafShades());
        this.random = random;
        this.leafDensity = s.get(TreeSettings.Key.TIER_LEAF_DENSITY);
        this.willow = s.on(TreeSettings.Key.TIER_WILLOW);
        this.willowScale = s.get(TreeSettings.Key.TIER_WILLOW_SCALE);
        this.snow = s.on(TreeSettings.Key.TIER_SNOW);
        this.snowAmount = s.get(TreeSettings.Key.TIER_SNOW_AMOUNT);
    }

    static Map<Long, TreePart> build(TreeSettings s, Random random) {
        TieredTree tree = new TieredTree(s, random);
        tree.grow(s);
        return tree.voxels.blocks();
    }

    private static int round(double v) {
        return (int) Math.floor(v + 0.5);
    }

    private void sphere(double bx, double by, double bz, double size, TreePart part) {
        int cx = round(bx), cy = round(by), cz = round(bz);
        if (size < 0.5) {
            voxels.set(cx, cy, cz, part);
            return;
        }
        int ceilRad = (int) Math.ceil(size);
        for (int dx = -ceilRad; dx <= ceilRad; dx++) {
            for (int dy = -ceilRad; dy <= ceilRad; dy++) {
                for (int dz = -ceilRad; dz <= ceilRad; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= size * size) voxels.set(cx + dx, cy + dy, cz + dz, part);
                }
            }
        }
    }

    /** A cluster of leaves, thinned by the density; snow on its upper half (instead of a leaf, or on top of it); willow vines from an outer cluster. */
    private void leaves(double bx, double by, double bz, double size, boolean outer) {
        int cx = round(bx), cy = round(by), cz = round(bz);
        int ceilRad = (int) Math.ceil(size);
        for (int dx = -ceilRad; dx <= ceilRad; dx++) {
            for (int dy = -ceilRad; dy <= ceilRad; dy++) {
                for (int dz = -ceilRad; dz <= ceilRad; dz++) {
                    if (dx * dx + dy * dy + dz * dz > size * size) continue;
                    if (random.nextDouble() * 100 > leafDensity) continue;
                    boolean snowHere = false;
                    if (snow && dy >= 0 && ceilRad > 0) {
                        if (random.nextDouble() < ((double) dy / ceilRad) * snowAmount * 1.3) {
                            if (random.nextDouble() < 0.4) snowHere = true;
                            else voxels.set(cx + dx, cy + dy + 1, cz + dz, TreePart.SNOW);
                        }
                    }
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (snowHere) voxels.set(x, y, z, TreePart.SNOW);
                    else voxels.leaf(x, y, z, ceilRad == 0 ? 0.5 : (dy + ceilRad) / (2.0 * ceilRad), TreeVoxelizer.jitter(x, y, z));
                    if (willow && outer && !snowHere && random.nextDouble() < 0.15 * willowScale) {
                        int length = (int) Math.ceil((size * 1.5 + random.nextDouble() * 5) * willowScale);
                        for (int v = 1; v <= length; v++) voxels.leaf(x, y - v, z, 0.0, 0.0);
                    }
                }
            }
        }
    }

    private void grow(TreeSettings s) {
        int treeHeight = s.getInt(TreeSettings.Key.TIER_HEIGHT);
        int stumpHeight = s.getInt(TreeSettings.Key.TIER_STUMP);
        double baseRadius = s.get(TreeSettings.Key.TIER_RADIUS);
        double twist = s.get(TreeSettings.Key.TIER_TWIST);
        boolean braided = s.on(TreeSettings.Key.TIER_BRAIDED);
        double braidIntensity = s.get(TreeSettings.Key.TIER_BRAID_INTENSITY);
        boolean wobble = s.on(TreeSettings.Key.TIER_BRAID_WOBBLE);
        double windRad = Math.toRadians(s.get(TreeSettings.Key.TIER_WIND_DIR));
        double wx = Math.cos(windRad) * s.get(TreeSettings.Key.TIER_WIND) * 1.2;
        double wz = Math.sin(windRad) * s.get(TreeSettings.Key.TIER_WIND) * 1.2;

        // ---- the trunk
        double activeIntensity = braided && twist > 0 ? braidIntensity : 1.0;
        double[][] trunk = new double[treeHeight + 1][];
        double[][] core = new double[treeHeight + 1][];
        double spiral = 0;
        double startOffX = 0, startOffZ = 0;
        if (twist > 0) {
            double startActive = baseRadius * CORKSCREW_RATIO * activeIntensity;
            startOffX = braided ? startActive * 1.5 : startActive;
        }
        double lastX = startOffX, lastZ = startOffZ;
        for (int i = 0; i <= treeHeight; i++) {
            double progress = (double) i / treeHeight;
            double curve = progress * progress;
            double windX = wx * curve * treeHeight * 0.5, windZ = wz * curve * treeHeight * 0.5;
            double offX = 0, offZ = 0, offX2 = 0, offZ2 = 0, coreX = 0, coreZ = 0;
            double coreRad = baseRadius * (1 - progress);
            double sharpness = Math.min(1.0, (1 - progress) * 8.0);
            double braidRad = baseRadius * Math.max(0.6 * sharpness, Math.sqrt(1 - progress));
            double spread = 0;
            if (twist > 0) {
                boolean stump = i <= stumpHeight;
                spiral += stump ? twist * 0.08 : twist * 0.06;
                double active = baseRadius * CORKSCREW_RATIO * (1 - progress) * activeIntensity;
                spread = braided ? active * 1.5 : active;
                offX = Math.cos(spiral) * spread;
                offZ = Math.sin(spiral) * spread;
                if (braided) {
                    offX2 = Math.cos(spiral + Math.PI) * spread;
                    offZ2 = Math.sin(spiral + Math.PI) * spread;
                }
                if (stump) {
                    double jitter = twist * 0.12;
                    coreX = (Math.sin(i * 1.1) + Math.cos(i * 0.7)) * jitter;
                    coreZ = (Math.cos(i * 1.3) + Math.sin(i * 0.4)) * jitter;
                    offX += coreX;
                    offZ += coreZ;
                    offX2 += coreX;
                    offZ2 += coreZ;
                }
            }
            double tX = offX + windX, tZ = offZ + windZ;
            trunk[i] = new double[]{tX, i, tZ};
            core[i] = new double[]{coreX + windX, i, coreZ + windZ};
            double stepDist = Math.hypot(tX - lastX, tZ - lastZ);
            if (stepDist > 0.3) {
                int steps = (int) Math.ceil(stepDist * 4);
                for (int k = 0; k <= steps; k++) sphere(lastX + (tX - lastX) * k / steps, i, lastZ + (tZ - lastZ) * k / steps, braidRad, TreePart.TRUNK);
            }
            sphere(tX, i, tZ, braidRad, TreePart.TRUNK);
            lastX = tX;
            lastZ = tZ;
            if (braided) {
                sphere(offX2 + windX, i, offZ2 + windZ, braidRad * SECONDARY_RADIUS_RATIO, TreePart.TRUNK);
                sphere(core[i][0], i, core[i][2], Math.max(coreRad, spread * 0.75) * sharpness, TreePart.TRUNK);
            }
        }

        // ---- the tiers of branches
        int tierSpacing = s.getInt(TreeSettings.Key.TIER_SPACING);
        int perTier = s.getInt(TreeSettings.Key.TIER_BRANCHES);
        double branchLength = s.get(TreeSettings.Key.TIER_BRANCH_LENGTH);
        double sagSetting = s.get(TreeSettings.Key.TIER_SAG);
        double leafRadius = s.get(TreeSettings.Key.TIER_LEAF_SIZE);
        double rotation = random.nextDouble() * Math.PI * 2;
        for (int currentY = stumpHeight; currentY <= treeHeight - 3; currentY += tierSpacing) {
            double hp = (double) currentY / treeHeight;
            double[] at = braided && !wobble ? core[currentY] : trunk[currentY];
            rotation += Math.toRadians(40 + random.nextInt(101));
            for (int b = 1; b <= perTier; b++) {
                double yaw = rotation + ((double) b / perTier) * Math.PI * 2;
                double pitch = Math.toRadians(105 - hp * 50);
                double dirX = Math.sin(pitch) * Math.cos(yaw), dirY = Math.cos(pitch), dirZ = Math.sin(pitch) * Math.sin(yaw);
                double len = branchLength * (1 - hp);
                int n = Math.max(1, (int) Math.ceil(len * 3));
                for (int step = 0; step <= n; step++) {
                    double t = (double) step / n;
                    double sag = sagSetting * (snow ? 1.0 + snowAmount * 1.5 : 1.0) * t * t * (1 - hp) * 5.0;
                    double px = at[0] + dirX * len * t, py = at[1] + dirY * len * t - sag, pz = at[2] + dirZ * len * t;
                    leaves(px, py, pz, leafRadius * (0.6 + t * 0.4), t > 0.6);
                    sphere(px, py, pz, baseRadius * 0.25 * (1 - t), TreePart.BRANCH);
                }
            }
        }

        // ---- the top
        for (int h = 1; h <= 5; h++) {
            double p = h / 5.0;
            double targetH = treeHeight + h;
            double grow = (targetH / treeHeight) * (targetH / treeHeight) * treeHeight * 0.5;
            leaves(wx * grow, targetH, wz * grow, leafRadius * 0.95 * (1 - p), false);
        }

        // ---- the roots
        double rootFlare = s.get(TreeSettings.Key.TIER_ROOT_FLARE);
        if (rootFlare > 0.5) {
            double startActive = baseRadius * CORKSCREW_RATIO * activeIntensity;
            double bottomSpread = braided && twist > 0 ? startActive * 1.5 : 0;
            double footprint = Math.max(baseRadius, bottomSpread * 0.75);
            int rootCount = (int) Math.floor(5 + footprint * 1.5);
            for (int r = 1; r <= rootCount; r++) {
                double ryaw = ((double) r / rootCount) * Math.PI * 2;
                double rlen = rootFlare * Math.max(1.0, baseRadius * 0.6) * (0.8 + random.nextDouble() * 0.4);
                for (int k = 0; k <= 20; k++) {
                    double progress = k / 20.0;
                    double thick = Math.max(Math.max(0.6, baseRadius * 0.25), footprint * 0.9 * Math.sqrt(1 - progress));
                    sphere(Math.cos(ryaw) * rlen * progress, rlen * 0.7 * (1 - progress), Math.sin(ryaw) * rlen * progress, thick, TreePart.ROOT);
                }
            }
        }
    }
}
