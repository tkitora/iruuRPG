package net.tkgon.mc.iruuRPG.tools.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A tree made the way a Minecraft builder makes one by hand (not the way a botanist would model it): the usual rules of tree tutorials for builds.
 * <ul>
 *   <li>The trunk is a column built in straight runs of 3 to 6 blocks that shift one block sideways now and then, with a thickness that drops in steps (never a smooth cone),
 *       a flared foot and a few roots.</li>
 *   <li>Only a few big branches (2 to 8), spread evenly round the trunk with a little jitter, each a short chain of straight stretches of 2 to 5 blocks that
 *       turns at the joints and climbs, one or two blocks thick, so it reads as stairs of blocks; the branch forks near its end.</li>
 *   <li>The leaves are big solid clouds on the branch ends (and one on the top of the trunk), joined into one canopy with a flat underside, a rough outline and a few
 *       hanging blocks (see {@link TreeVoxelizer}); never single leaves scattered along a thin twig.</li>
 * </ul>
 * The size of a cloud is 1.5 times the leaf radius of the settings. The box of the settings counts the clouds: the branches stop that far from its edge.
 */
final class BuilderAlgorithm implements TreeAlgorithm {

    private static final double GOLDEN = Math.toRadians(137.5);
    private static final double CLOUD = 1.5;

    @Override
    public TreeSkeleton grow(TreeSettings settings, Random random) {
        double leaf = settings.get(TreeSettings.Key.LEAF_RADIUS);
        double flatness = settings.get(TreeSettings.Key.LEAF_FLATNESS);
        double cloud = leaf * CLOUD;
        double top = Math.max(4.0, settings.height() - cloud * flatness * 0.9 - 0.5);
        double reach = Math.max(1.5, settings.width() / 2.0 - cloud);
        double trunkRadius = settings.get(TreeSettings.Key.TRUNK_RADIUS);
        double taper = settings.get(TreeSettings.Key.TAPER);
        double curve = settings.get(TreeSettings.Key.CURVE);
        double ratio = settings.get(TreeSettings.Key.TRUNK_RATIO);
        double droop = settings.get(TreeSettings.Key.DROOP);
        int levels = settings.getInt(TreeSettings.Key.LEVELS);
        String crown = settings.crown();
        int branchCount = Math.max(2, Math.min(8, (int) Math.round(settings.getInt(TreeSettings.Key.BRANCHES) * 0.5)));

        TreeSkeleton skeleton = new TreeSkeleton();
        // ---- the trunk: straight runs, a sideways step now and then, a thickness that drops in steps
        double x = 0.0;
        double z = 0.0;
        double y = 0.0;
        int last = -1;
        List<Integer> trunk = new ArrayList<>();
        double drift = Math.max(0.0, Math.min(1.5, reach * 0.35));
        while (y < top - 0.01) {
            double run = Math.min(top - y, 3.0 + random.nextInt(4));
            double middle = (y + run / 2.0) / top;
            // a plus-shaped column (at least) up to three quarters of the height, then a single block
            double radius = Math.max(middle > 0.75 ? 0.7 : 1.0, Math.round(trunkRadius * (1.0 - 0.5 * taper * Math.pow(middle, 1.2)) * 2.0) / 2.0);
            int start = skeleton.add(x, y, z, last, 0);
            skeleton.nodes.get(start).radius = radius;
            trunk.add(start);
            y += run;
            int end = skeleton.add(x, y, z, start, 0);
            skeleton.nodes.get(end).radius = radius;
            trunk.add(end);
            last = end;
            if (y < top - 1.0 && random.nextDouble() < curve * 1.1) {
                // one block to the side (never farther from the middle than the drift allows)
                int axis = random.nextInt(4);
                double nx = x + (axis == 0 ? 1 : axis == 1 ? -1 : 0);
                double nz = z + (axis == 2 ? 1 : axis == 3 ? -1 : 0);
                if (Math.abs(nx) <= drift && Math.abs(nz) <= drift) {
                    x = nx;
                    z = nz;
                    int shift = skeleton.add(x, y, z, last, 0);
                    skeleton.nodes.get(shift).radius = radius;
                    last = shift;
                    trunk.add(shift);
                }
            }
        }
        TreeSkeleton.Node crownTop = skeleton.nodes.get(last);
        double crownScale = CLOUD * 1.15;
        if (leaf >= 0.4) skeleton.leaves.add(new double[]{crownTop.x, crownTop.y + cloud * flatness * 0.25, crownTop.z, crownScale, flatness});

        // ---- the big branches
        double bare = Math.max(2.0, top * ratio);
        double azimuth = random.nextDouble() * Math.PI * 2.0;
        double baseAngle = Math.toRadians(settings.get(TreeSettings.Key.BRANCH_ANGLE));
        for (int index = 0; index < branchCount && levels >= 1; index++) {
            double t = (index + 0.2 + random.nextDouble() * 0.6) / branchCount;
            double height = bare + (top - 0.5 - bare) * Math.min(0.97, t);
            int at = nearestTrunkNode(skeleton, trunk, height);
            TreeSkeleton.Node origin = skeleton.nodes.get(at);
            azimuth += GOLDEN + (random.nextDouble() - 0.5) * 0.5;
            double length = Math.max(2.5, reach * 1.15 * TreeAlgorithm.shape(crown, t) * (0.8 + random.nextDouble() * 0.3));
            double elevation = Math.max(Math.toRadians(12.0), Math.toRadians(90.0) - baseAngle + (random.nextDouble() - 0.5) * 0.35);
            int stretches = length > 4.5 ? 3 : 2;
            double yaw = azimuth;
            double px = origin.x;
            double py = origin.y;
            double pz = origin.z;
            int parent = at;
            double radius = Math.max(0.9, origin.radius * 0.6);
            int joint = -1;
            for (int stretch = 0; stretch < stretches; stretch++) {
                double part = length / stretches * (0.85 + random.nextDouble() * 0.3);
                double flat = Math.cos(elevation);
                double nx = px + Math.cos(yaw) * flat * part;
                double nz = pz + Math.sin(yaw) * flat * part;
                double ny = py + Math.sin(elevation) * part - droop * 0.6 * part * 0.3;
                double[] clamped = clamp(nx, ny, nz, top, reach);
                nx = clamped[0];
                ny = clamped[1];
                nz = clamped[2];
                parent = skeleton.add(nx, ny, nz, parent, 1);
                skeleton.nodes.get(parent).radius = Math.max(0.7, radius * (1.0 - 0.18 * stretch));
                px = nx;
                py = ny;
                pz = nz;
                // turn at the joint, and climb a little more
                yaw += (random.nextDouble() - 0.5) * Math.toRadians(50.0);
                elevation = Math.min(Math.toRadians(75.0), elevation + Math.toRadians(8.0 + 12.0 * random.nextDouble()));
                if (stretch == 0) joint = parent;
            }
            // forks near the end of the branch, each ending in a cloud
            int forks = levels >= 2 ? 2 : 0;
            if (forks > 0) {
                for (int fork = 0; fork < forks; fork++) {
                    double forkYaw = yaw + (fork == 0 ? 1 : -1) * Math.toRadians(35.0 + random.nextDouble() * 25.0);
                    double forkLength = 2.0 + random.nextDouble() * 1.8;
                    double forkElevation = elevation + Math.toRadians(8.0);
                    double nx = px + Math.cos(forkYaw) * Math.cos(forkElevation) * forkLength;
                    double nz = pz + Math.sin(forkYaw) * Math.cos(forkElevation) * forkLength;
                    double ny = py + Math.sin(forkElevation) * forkLength;
                    double[] clamped = clamp(nx, ny, nz, top, reach);
                    int forkNode = skeleton.add(clamped[0], clamped[1], clamped[2], parent, 2);
                    skeleton.nodes.get(forkNode).radius = 0.7;
                    if (leaf >= 0.4) skeleton.leaves.add(new double[]{clamped[0], clamped[1], clamped[2], CLOUD * (0.8 + random.nextDouble() * 0.2), flatness});
                }
            }
            if (leaf >= 0.4) {
                skeleton.leaves.add(new double[]{px, py, pz, CLOUD * (0.95 + random.nextDouble() * 0.2), flatness});
                // a smaller cloud on the middle of the branch
                if (joint >= 0 && joint != parent && random.nextDouble() < 0.7) {
                    TreeSkeleton.Node middle = skeleton.nodes.get(joint);
                    skeleton.leaves.add(new double[]{middle.x, middle.y + 0.3, middle.z, CLOUD * 0.6, flatness});
                }
            }
            // a short stub on the branch, hanging down, with a small cloud (the third level)
            if (levels >= 3 && joint >= 0 && random.nextDouble() < 0.7) {
                TreeSkeleton.Node middle = skeleton.nodes.get(joint);
                double stubYaw = yaw + Math.PI * (random.nextBoolean() ? 0.5 : -0.5);
                double[] clamped = clamp(middle.x + Math.cos(stubYaw) * 1.8, middle.y - 0.6, middle.z + Math.sin(stubYaw) * 1.8, top, reach);
                int stub = skeleton.add(clamped[0], clamped[1], clamped[2], joint, 2);
                skeleton.nodes.get(stub).radius = 0.7;
                if (leaf >= 0.4) skeleton.leaves.add(new double[]{clamped[0], clamped[1], clamped[2], CLOUD * 0.55, flatness});
            }
        }
        TreeAlgorithm.addRoots(skeleton, settings, random);
        return skeleton;
    }

    private static int nearestTrunkNode(TreeSkeleton skeleton, List<Integer> trunk, double height) {
        int best = trunk.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (int index : trunk) {
            double distance = Math.abs(skeleton.nodes.get(index).y - height);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    /** Brings a point into the box: not higher than {@code top}, not lower than half a block, not farther out than {@code reach} from the middle. */
    private static double[] clamp(double x, double y, double z, double top, double reach) {
        double ny = Math.max(0.5, Math.min(top, y));
        double out = Math.sqrt(x * x + z * z);
        if (out > reach) {
            x = x / out * reach;
            z = z / out * reach;
        }
        return new double[]{x, ny, z};
    }
}
