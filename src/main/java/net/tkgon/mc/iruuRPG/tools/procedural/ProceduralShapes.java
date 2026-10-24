package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The makers of everything the procedural tool makes besides trees: rocks, spikes, crystals, basalt columns and arches. Each turns the settings and a random source into
 * blocks by part, the same shape of result a tree has (a map from the packed position to the part), so the preview, the placing, the undo and the saving are shared. Pure
 * arithmetic: it runs off the main thread. Coordinates are the tree's: x and z from the middle of the block the object stands on, y up from the bottom of that block
 * (a hanging object grows down from it).
 */
final class ProceduralShapes {

    private ProceduralShapes() {
    }

    /** The blocks being made: where two parts meet the stronger one stays. */
    private static final class Blocks {
        final Map<Long, TreePart> map = new HashMap<>();

        void put(int x, int y, int z, TreePart part) {
            long key = TreeVoxelizer.pack(x, y, z);
            TreePart old = map.get(key);
            if (old == null || part.priority() < old.priority()) map.put(key, part);
        }

        boolean has(int x, int y, int z) {
            return map.containsKey(TreeVoxelizer.pack(x, y, z));
        }
    }

    /**
     * @param aim the way a spike or a crystal points (from the block looked at toward the player's eye), null for straight up; rocks stand on the ground and ignore it
     */
    static Map<Long, TreePart> build(ProcKind kind, TreeSettings s, long seed, Random random, TreeGround ground, Vector aim) {
        Blocks blocks = new Blocks();
        Frame frame = Frame.of(aim);
        switch (kind) {
            case ROCK -> rock(blocks, s, seed, random);
            case SPIKE -> spike(blocks, s, seed, random, frame);
            case CRYSTAL -> crystals(blocks, s, seed, random, frame);
            default -> {
            }
        }
        // only a rock is set on the ground; a spike or a crystal points where it is aimed, and its tip must not be pulled down to the ground
        if (kind == ProcKind.ROCK) settle(blocks, ground);
        clip(blocks, s, kind);
        return blocks.map;
    }

    /** A frame of three unit vectors: {@code w} is the way the object points, {@code u} and {@code v} across it. */
    private record Frame(Vector u, Vector v, Vector w) {
        static Frame of(Vector aim) {
            Vector w = aim == null || aim.lengthSquared() < 1.0E-6 ? new Vector(0.0, 1.0, 0.0) : aim.clone().normalize();
            Vector reference = Math.abs(w.getY()) > 0.95 ? new Vector(1.0, 0.0, 0.0) : new Vector(0.0, 1.0, 0.0);
            Vector u = w.clone().crossProduct(reference).normalize();
            Vector v = w.clone().crossProduct(u).normalize();
            return new Frame(u, v, w);
        }

        /** The place {@code along} the axis and {@code a}, {@code b} across it, as offsets from the middle of the base block. */
        double[] at(double along, double a, double b) {
            return new double[]{w.getX() * along + u.getX() * a + v.getX() * b, w.getY() * along + u.getY() * a + v.getY() * b, w.getZ() * along + u.getZ() * a + v.getZ() * b};
        }
    }

    /** Puts a block at a point given as an offset from the middle of the base block. */
    private static void putAt(Blocks blocks, double[] offset, TreePart part) {
        blocks.put((int) Math.round(offset[0]), (int) Math.round(offset[1]), (int) Math.round(offset[2]), part);
    }

    // ---- shared ------------------------------------------------------------------------------------------------------------------

    /** A band of the object by height: with layers, the three kinds of rock take turns; without, a lighter top and a darker foot. */
    private static TreePart stone(TreeSettings s, double fraction, double speck) {
        int layers = s.getInt(TreeSettings.Key.LAYERS);
        if (layers > 0) {
            int band = (int) Math.floor(Math.max(0.0, Math.min(0.999, fraction)) * layers);
            return switch (band % 3) {
                case 0 -> TreePart.STONE;
                case 1 -> TreePart.STONE_LIGHT;
                default -> TreePart.STONE_DARK;
            };
        }
        if (speck > 0.72) return TreePart.STONE_LIGHT;
        if (speck < -0.72) return TreePart.STONE_DARK;
        return TreePart.STONE;
    }

    /** Moss on the faces that look up: where the block above is empty, in the patches the noise says, as much as the setting asks. */
    private static void moss(Blocks blocks, TreeSettings s, long seed) {
        double amount = s.get(TreeSettings.Key.MOSS);
        if (amount <= 0.0) return;
        for (Map.Entry<Long, TreePart> entry : new HashMap<>(blocks.map).entrySet()) {
            int x = TreeVoxelizer.unpackX(entry.getKey());
            int y = TreeVoxelizer.unpackY(entry.getKey());
            int z = TreeVoxelizer.unpackZ(entry.getKey());
            if (blocks.has(x, y + 1, z)) continue;
            double n = 0.5 + 0.5 * TreeNoise.value(x * 0.45, y * 0.45, z * 0.45, seed + 91);
            if (n < 1.0 - amount * 1.2) continue;
            blocks.put(x, y, z, TreePart.MOSS);
        }
    }

    /** Fills down from the lowest block of every column to the ground (the terrain where it is known, else the base level), so nothing floats over a slope. */
    private static void settle(Blocks blocks, TreeGround ground) {
        Map<Long, int[]> lowest = new HashMap<>();
        Map<Long, TreePart> lowestPart = new HashMap<>();
        for (Map.Entry<Long, TreePart> entry : blocks.map.entrySet()) {
            int x = TreeVoxelizer.unpackX(entry.getKey());
            int y = TreeVoxelizer.unpackY(entry.getKey());
            int z = TreeVoxelizer.unpackZ(entry.getKey());
            long column = TreeVoxelizer.pack(x, 0, z);
            int[] at = lowest.get(column);
            if (at == null || y < at[0]) {
                lowest.put(column, new int[]{y});
                lowestPart.put(column, entry.getValue());
            }
        }
        for (Map.Entry<Long, int[]> entry : lowest.entrySet()) {
            int x = TreeVoxelizer.unpackX(entry.getKey());
            int z = TreeVoxelizer.unpackZ(entry.getKey());
            int floor = 0;
            if (ground != null) {
                int top = ground.top(x, z);
                if (top != TreeGround.NONE && top + 1 >= -6 && top + 1 <= 6) floor = top + 1;
            }
            TreePart part = lowestPart.get(entry.getKey());
            for (int y = entry.getValue()[0] - 1; y >= floor; y--) blocks.put(x, y, z, part);
        }
    }

    private static void clip(Blocks blocks, TreeSettings s, ProcKind kind) {
        int height = s.height();
        double half = s.width() / 2.0 + 1.0;
        int reach = height + s.width() + 2;
        blocks.map.keySet().removeIf(key -> {
            int x = TreeVoxelizer.unpackX(key);
            int y = TreeVoxelizer.unpackY(key);
            int z = TreeVoxelizer.unpackZ(key);
            if (kind == ProcKind.ROCK) return y < -6 || y >= height || Math.abs(x) > half || Math.abs(z) > half;
            return Math.abs(x) > reach || Math.abs(y) > reach || Math.abs(z) > reach;
        });
    }

    // ---- rock ------------------------------------------------------------------------------------------------------------------------

    /** A rough ellipsoid (flat underneath, half buried), the edge moved in and out by noise; with a count, a big one in the middle and smaller ones round it. */
    private static void rock(Blocks blocks, TreeSettings s, long seed, Random random) {
        int count = s.getInt(TreeSettings.Key.COUNT);
        double rough = s.get(TreeSettings.Key.ROUGHNESS);
        double height = s.height();
        double half = s.width() / 2.0;
        for (int index = 0; index < count; index++) {
            double size = index == 0 ? 1.0 : 0.3 + 0.4 * random.nextDouble();
            double ax = Math.max(1.5, (half - 0.5) * size * (index == 0 ? (count > 1 ? 0.6 : 1.0) : 0.8));
            double az = ax * (0.75 + 0.5 * random.nextDouble());
            double top = height * size;
            double ry = top * 0.62;
            double cy = top * 0.38;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = index == 0 ? 0.0 : (half - ax * 0.6) * (0.55 + 0.4 * random.nextDouble());
            double cx = Math.cos(angle) * distance;
            double cz = Math.sin(angle) * distance;
            double turn = random.nextDouble() * Math.PI;
            double cos = Math.cos(turn);
            double sin = Math.sin(turn);
            int reach = (int) Math.ceil(Math.max(ax, az)) + 2;
            for (int x = (int) Math.floor(cx) - reach; x <= (int) Math.ceil(cx) + reach; x++) {
                for (int z = (int) Math.floor(cz) - reach; z <= (int) Math.ceil(cz) + reach; z++) {
                    for (int y = 0; y <= (int) Math.ceil(top) + 1; y++) {
                        double dx = x - cx;
                        double dz = z - cz;
                        double u = (dx * cos + dz * sin) / ax;
                        double v = (-dx * sin + dz * cos) / az;
                        double w = (y + 0.5 - cy) / ry;
                        if (w > 0.0) w *= 1.18;
                        double d = Math.sqrt(u * u + v * v + w * w);
                        double edge = 1.0 + rough * 0.75 * TreeNoise.value(x * 0.38, y * 0.38, z * 0.38, seed + index) + rough * 0.18 * (random.nextDouble() - 0.5);
                        if (d > edge) continue;
                        blocks.put(x, y, z, stone(s, y / Math.max(1.0, top), TreeNoise.value(x * 0.6, y * 0.6, z * 0.6, seed + 7)));
                    }
                }
            }
        }
        moss(blocks, s, seed);
    }

    // ---- spike -----------------------------------------------------------------------------------------------------------------------

    /**
     * One spike pointing along the frame's axis: its section is a polygon of {@code spike-sides} corners with a star-shaped dip between them ({@code spike-star}), turned a
     * little more along the length ({@code spike-twist} turns in all, so the ridges spiral), thinning to a point by {@code spike-taper}, bent by the curve and roughened by noise.
     */
    private static void spike(Blocks blocks, TreeSettings s, long seed, Random random, Frame frame) {
        double length = s.height();
        double base = Math.max(1.0, s.width() * 0.45);
        int sides = s.getInt(TreeSettings.Key.SPIKE_SIDES);
        double star = s.get(TreeSettings.Key.SPIKE_STAR);
        double twist = s.get(TreeSettings.Key.SPIKE_TWIST) * Math.PI * 2.0;
        double taper = s.get(TreeSettings.Key.SPIKE_TAPER);
        double rough = s.get(TreeSettings.Key.ROUGHNESS);
        double bend = s.get(TreeSettings.Key.CURVE) * length * 0.4;
        double bendAngle = random.nextDouble() * Math.PI * 2.0;
        for (double along = 0.0; along <= length; along += 0.3) {
            double t = along / length;
            double r = base * Math.pow(Math.max(0.0, 1.0 - t), taper);
            if (r < 0.45) r = t < 0.97 ? 0.45 : 0.0;
            double shift = bend * t * t;
            double turn = twist * t;
            double reach = r * (1.0 + rough * 0.3);
            for (double a = -reach; a <= reach; a += 0.4) {
                for (double b = -reach; b <= reach; b += 0.4) {
                    double rho = Math.hypot(a, b);
                    double theta = Math.atan2(b, a) - turn;
                    double limit = r * (1.0 - star * (0.5 + 0.5 * Math.cos(sides * theta))) * (1.0 + rough * 0.45 * TreeNoise.value(a * 0.6 + along * 0.2, along * 0.5, b * 0.6, seed));
                    if (rho > limit) continue;
                    TreePart part = s.getInt(TreeSettings.Key.LAYERS) > 0 ? stone(s, t, 0.0) : t < 0.25 ? TreePart.STONE_DARK : t > 0.75 ? TreePart.STONE_LIGHT : TreePart.STONE;
                    putAt(blocks, frame.at(along, a + Math.cos(bendAngle) * shift, b + Math.sin(bendAngle) * shift), part);
                }
            }
        }
    }

    // ---- crystals --------------------------------------------------------------------------------------------------------------------

    /**
     * Crystals pointing along the frame's axis, made the way {@code crystal-type} says: a {@code cluster} (one along the axis and the rest leaning out of a foot of rock by the
     * spread), one large {@code prism}, or thin {@code blades}. A crystal is a prism of {@code crystal-facets} sides that ends in a point over the last {@code crystal-tip} of its
     * length, with a bright core. Nothing is pulled down to the ground: the tips stay where they point.
     */
    private static void crystals(Blocks blocks, TreeSettings s, long seed, Random random, Frame frame) {
        String type = s.crystalType();
        int count = type.equals("prism") ? 1 : s.getInt(TreeSettings.Key.COUNT);
        double length = s.height();
        double thick = s.get(TreeSettings.Key.CRYSTAL_THICK);
        double spread = s.get(TreeSettings.Key.SPREAD);
        double tip = s.get(TreeSettings.Key.CRYSTAL_TIP);
        double variance = s.get(TreeSettings.Key.CRYSTAL_VARIANCE);
        double rough = s.get(TreeSettings.Key.ROUGHNESS);
        int facets = s.getInt(TreeSettings.Key.CRYSTAL_FACETS);
        double scale = Math.sqrt(s.width() / 7.0);
        for (int index = 0; index < count; index++) {
            boolean first = index == 0;
            double crystalLength = first ? length : length * (1.0 - variance * (0.15 + 0.6 * random.nextDouble()));
            double radius = Math.max(0.8, (type.equals("prism") ? 2.2 : 1.1 + random.nextDouble() * 0.8) * scale * thick * (first ? 1.15 : 1.0));
            boolean blade = type.equals("blades");
            double aspect = blade ? 2.8 : 1.0;
            // the way this crystal leans from the axis, as two offsets across it
            double phi = random.nextDouble() * Math.PI * 2.0;
            double tilt = first ? 0.0 : Math.toRadians(12.0 + 60.0 * spread * (0.35 + 0.65 * random.nextDouble()));
            double leanA = Math.tan(tilt) * Math.cos(phi);
            double leanB = Math.tan(tilt) * Math.sin(phi);
            double footAngle = random.nextDouble() * Math.PI * 2.0;
            double footDistance = first ? 0.0 : random.nextDouble() * 1.3 * scale;
            double spin = random.nextDouble() * Math.PI;
            for (double along = 0.0; along <= crystalLength; along += 0.3) {
                double t = along / crystalLength;
                double envelope = blade ? Math.max(0.0, 1.0 - t * 0.9) : t < 1.0 - tip ? 1.0 : Math.max(0.0, 1.0 - (t - (1.0 - tip)) / tip);
                double r = Math.max(blade ? 0.0 : 0.3, radius * envelope);
                if (r <= 0.0) continue;
                double centreA = Math.cos(footAngle) * footDistance + leanA * along;
                double centreB = Math.sin(footAngle) * footDistance + leanB * along;
                double reach = r * aspect * (1.0 + rough * 0.3);
                for (double a = -reach; a <= reach; a += 0.4) {
                    for (double b = -reach; b <= reach; b += 0.4) {
                        // across the crystal: a polygon of the facets (a regular one: its corners at r, its sides at r * cos(pi / facets)), a blade being long one way and thin the other
                        double ca = Math.cos(spin);
                        double sa = Math.sin(spin);
                        double pa = a * ca + b * sa;
                        double pb = (-a * sa + b * ca) * aspect;
                        double rho = Math.hypot(pa, pb);
                        double theta = Math.atan2(pb, pa);
                        double sector = Math.PI * 2.0 / facets;
                        double inside = Math.cos(sector / 2.0) / Math.cos(((theta % sector) + sector) % sector - sector / 2.0);
                        double limit = r * inside * (1.0 + rough * 0.3 * TreeNoise.value(a * 0.7 + index, along * 0.6, b * 0.7, seed));
                        if (rho > limit) continue;
                        TreePart part = rho < r * 0.36 && t > 0.08 && t < 0.9 && !blade ? TreePart.CRYSTAL_CORE : TreePart.CRYSTAL;
                        putAt(blocks, frame.at(along, centreA + a, centreB + b), part);
                    }
                }
            }
        }
        // the rock they come out of: a low mound across the axis at the foot
        if (!type.equals("blades") && count > 1) {
            double footRadius = Math.max(1.8, s.width() * 0.3);
            for (double a = -footRadius - 1; a <= footRadius + 1; a += 0.5) {
                for (double b = -footRadius - 1; b <= footRadius + 1; b += 0.5) {
                    for (double along = -1.0; along <= 1.0; along += 0.5) {
                        double d = Math.hypot(a, b) / footRadius + Math.abs(along + 0.2) * 0.9;
                        double edge = 1.0 + rough * 0.6 * TreeNoise.value(a * 0.5, along * 0.5, b * 0.5, seed + 13);
                        if (d <= edge) putAt(blocks, frame.at(along, a, b), TreePart.STONE);
                    }
                }
            }
        }
    }
}
