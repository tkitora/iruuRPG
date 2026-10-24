package net.tkgon.mc.iruuRPG.tools.procedural;

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

    static Map<Long, TreePart> build(ProcKind kind, TreeSettings s, long seed, Random random, TreeGround ground) {
        Blocks blocks = new Blocks();
        switch (kind) {
            case ROCK -> rock(blocks, s, seed, random);
            case SPIKE -> spikes(blocks, s, seed, random);
            case CRYSTAL -> crystals(blocks, s, seed, random);
            case COLUMNS -> columns(blocks, s, seed, random);
            case ARCH -> arch(blocks, s, seed, random);
            default -> {
            }
        }
        boolean hanging = s.getInt(TreeSettings.Key.HANGING) == 1 && kind != ProcKind.ROCK && kind != ProcKind.COLUMNS && kind != ProcKind.ARCH;
        if (!hanging) settle(blocks, ground);
        clip(blocks, s, hanging);
        return blocks.map;
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

    private static void clip(Blocks blocks, TreeSettings s, boolean hanging) {
        int height = s.height();
        double half = s.width() / 2.0 + 1.0;
        blocks.map.keySet().removeIf(key -> {
            int x = TreeVoxelizer.unpackX(key);
            int y = TreeVoxelizer.unpackY(key);
            int z = TreeVoxelizer.unpackZ(key);
            boolean outsideHeight = hanging ? (y > 0 || y < -height) : (y < -6 || y >= height);
            return outsideHeight || Math.abs(x) > half || Math.abs(z) > half;
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

    // ---- spikes ----------------------------------------------------------------------------------------------------------------------

    /** Tapering columns standing up (or, hanging, down) from the ground, leaning out by the spread, bending by the curve, lighter at the tip and darker at the foot. */
    private static void spikes(Blocks blocks, TreeSettings s, long seed, Random random) {
        int count = s.getInt(TreeSettings.Key.COUNT);
        double height = s.height();
        double half = s.width() / 2.0;
        double rough = s.get(TreeSettings.Key.ROUGHNESS);
        double taper = s.get(TreeSettings.Key.SPIKE_TAPER);
        double spread = s.get(TreeSettings.Key.SPREAD);
        double curve = s.get(TreeSettings.Key.CURVE);
        boolean hanging = s.getInt(TreeSettings.Key.HANGING) == 1;
        double scale = Math.sqrt(s.width() / 9.0);
        for (int index = 0; index < count; index++) {
            double h = index == 0 ? height : height * (0.3 + 0.6 * random.nextDouble());
            double base = Math.max(1.0, (1.0 + 1.7 * h / height) * scale * (index == 0 ? 1.0 : 0.85));
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = index == 0 ? 0.0 : Math.sqrt(random.nextDouble()) * Math.max(1.0, half - base - 1.0);
            double x0 = Math.cos(angle) * distance;
            double z0 = Math.sin(angle) * distance;
            double lean = random.nextDouble() * Math.PI * 2.0;
            double tilt = spread * 0.55 * random.nextDouble() * (index == 0 ? 0.3 : 1.0);
            double bend = (random.nextDouble() - 0.5) * curve * 3.0;
            for (double y = 0.0; y < h; y += 0.5) {
                double t = y / h;
                double r = base * Math.pow(1.0 - t, taper);
                if (r < 0.35 && t < 0.97) r = 0.35;
                double offset = Math.tan(tilt) * y + bend * Math.sin(t * Math.PI);
                double cx = x0 + Math.cos(lean) * offset;
                double cz = z0 + Math.sin(lean) * offset;
                int reach = (int) Math.ceil(r) + 1;
                for (int x = (int) Math.floor(cx) - reach; x <= (int) Math.ceil(cx) + reach; x++) {
                    for (int z = (int) Math.floor(cz) - reach; z <= (int) Math.ceil(cz) + reach; z++) {
                        double d = Math.hypot(x - cx, z - cz);
                        double edge = r * (1.0 + rough * 0.5 * TreeNoise.value(x * 0.5, y * 0.5, z * 0.5, seed + index));
                        if (d > edge) continue;
                        int yy = (int) Math.floor(y);
                        TreePart part = s.getInt(TreeSettings.Key.LAYERS) > 0 ? stone(s, t, 0.0) : t < 0.28 ? TreePart.STONE_DARK : t > 0.78 ? TreePart.STONE_LIGHT : TreePart.STONE;
                        blocks.put(x, hanging ? -yy : yy, z, part);
                    }
                }
            }
        }
    }

    // ---- crystals --------------------------------------------------------------------------------------------------------------------

    /** A cluster of crystals leaving a rock at the foot, one tall and nearly upright and the others leaning out; each is a prism that ends in a point, with a bright core. */
    private static void crystals(Blocks blocks, TreeSettings s, long seed, Random random) {
        int count = s.getInt(TreeSettings.Key.COUNT);
        double height = s.height();
        double spread = s.get(TreeSettings.Key.SPREAD);
        double rough = s.get(TreeSettings.Key.ROUGHNESS);
        double taper = s.get(TreeSettings.Key.SPIKE_TAPER);
        double scale = Math.sqrt(s.width() / 9.0);
        boolean hanging = s.getInt(TreeSettings.Key.HANGING) == 1;
        for (int index = 0; index < count; index++) {
            double length = index == 0 ? height : height * (0.35 + 0.55 * random.nextDouble());
            double radius = Math.max(0.9, (1.0 + random.nextDouble() * 0.9) * scale * (index == 0 ? 1.15 : 1.0));
            double phi = random.nextDouble() * Math.PI * 2.0;
            double tilt = Math.toRadians(index == 0 ? 8.0 * spread : (14.0 + 62.0 * spread * (0.4 + 0.6 * random.nextDouble())));
            double dx = Math.sin(tilt) * Math.cos(phi);
            double dy = Math.cos(tilt);
            double dz = Math.sin(tilt) * Math.sin(phi);
            double startAngle = random.nextDouble() * Math.PI * 2.0;
            double startDistance = index == 0 ? 0.0 : random.nextDouble() * 1.4 * scale;
            double sx = Math.cos(startAngle) * startDistance;
            double sz = Math.sin(startAngle) * startDistance;
            for (double step = 0.0; step <= length; step += 0.4) {
                double t = step / length;
                // a straight prism for 62% of its length, then a point
                double r = t < 0.62 ? radius : radius * Math.pow(Math.max(0.0, 1.0 - (t - 0.62) / 0.38), 0.8 * taper);
                if (r < 0.3) r = 0.3;
                double cx = sx + dx * step;
                double cy = dy * step;
                double cz = sz + dz * step;
                int reach = (int) Math.ceil(r) + 1;
                for (int x = (int) Math.floor(cx) - reach; x <= (int) Math.ceil(cx) + reach; x++) {
                    for (int y = (int) Math.floor(cy) - reach; y <= (int) Math.ceil(cy) + reach; y++) {
                        for (int z = (int) Math.floor(cz) - reach; z <= (int) Math.ceil(cz) + reach; z++) {
                            double d = Math.sqrt((x - cx) * (x - cx) + (y + 0.5 - cy) * (y + 0.5 - cy) + (z - cz) * (z - cz));
                            double edge = r * (1.0 + rough * 0.35 * TreeNoise.value(x * 0.7, y * 0.7, z * 0.7, seed + index));
                            if (d > edge || y < 0) continue;
                            TreePart part = d < r * 0.38 && t > 0.1 && t < 0.88 ? TreePart.CRYSTAL_CORE : TreePart.CRYSTAL;
                            blocks.put(x, hanging ? -y : y, z, part);
                        }
                    }
                }
            }
        }
        // the rock they come out of
        double footRadius = Math.max(2.0, s.width() * 0.28);
        for (int x = (int) -footRadius - 1; x <= footRadius + 1; x++) {
            for (int z = (int) -footRadius - 1; z <= footRadius + 1; z++) {
                for (int y = 0; y <= 2; y++) {
                    double d = Math.sqrt(x * x + z * z) / footRadius + y * 0.42;
                    double edge = 1.0 + rough * 0.5 * TreeNoise.value(x * 0.5, y * 0.5, z * 0.5, seed + 13);
                    if (d <= edge) blocks.put(x, hanging ? -y : y, z, TreePart.STONE);
                }
            }
        }
    }

    // ---- columns ---------------------------------------------------------------------------------------------------------------------

    /** Columns two blocks across on a staggered grid (a hexagonal look), as tall as a noise says and lower toward the edge, with a lighter top. */
    private static void columns(Blocks blocks, TreeSettings s, long seed, Random random) {
        double height = s.height();
        double half = s.width() / 2.0;
        double rough = s.get(TreeSettings.Key.ROUGHNESS);
        int rows = (int) Math.ceil(half / 1.8) + 1;
        for (int j = -rows; j <= rows; j++) {
            for (int i = -rows; i <= rows; i++) {
                double cx = i * 2.0 + (Math.floorMod(j, 2) == 1 ? 1.0 : 0.0);
                double cz = j * 1.8;
                double distance = Math.hypot(cx, cz);
                if (distance > half) continue;
                double fall = 1.0 - Math.pow(distance / half, 2.0) * 0.75;
                double n = 0.5 + 0.5 * TreeNoise.value(cx * 0.3, 0, cz * 0.3, seed);
                int h = (int) Math.round(height * fall * (0.3 + 0.7 * n) * (1.0 - rough * 0.25 * random.nextDouble()));
                if (h < 1) continue;
                int x0 = (int) Math.floor(cx);
                int z0 = (int) Math.floor(cz - 0.4);
                for (int x = x0; x <= x0 + 1; x++) {
                    for (int z = z0; z <= z0 + 1; z++) {
                        for (int y = 0; y < h; y++) {
                            TreePart part = y == h - 1 ? TreePart.STONE_LIGHT : y < h / 3 && s.getInt(TreeSettings.Key.LAYERS) == 0 ? TreePart.STONE_DARK : s.getInt(TreeSettings.Key.LAYERS) > 0 ? stone(s, y / (double) height, 0.0) : TreePart.STONE;
                            blocks.put(x, y, z, part);
                        }
                    }
                }
            }
        }
    }

    // ---- arch ------------------------------------------------------------------------------------------------------------------------

    /** The upper half of an ellipse ring turned a random way, as thick as the width allows, the surface moved in and out by noise, in layers, with moss on top. */
    private static void arch(Blocks blocks, TreeSettings s, long seed, Random random) {
        double height = s.height();
        double a = Math.max(3.0, s.width() / 2.0 - 1.0);
        double b = Math.max(3.0, height * 0.92);
        double rough = s.get(TreeSettings.Key.ROUGHNESS);
        double thick = Math.max(1.4, s.width() * 0.11);
        double depth = Math.max(2.0, thick * 1.7);
        double turn = random.nextDouble() * Math.PI;
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        int reach = (int) Math.ceil(a + thick) + 2;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double u = x * cos + z * sin;
                double v = -x * sin + z * cos;
                for (int y = 0; y <= (int) Math.ceil(b + thick) + 1; y++) {
                    double ring = Math.abs(Math.sqrt((u / a) * (u / a) + ((y + 0.5) / b) * ((y + 0.5) / b)) - 1.0) * Math.min(a, b);
                    double n = TreeNoise.value(x * 0.4, y * 0.4, z * 0.4, seed);
                    if (ring > thick * 0.5 * (1.0 + rough * 0.6 * n)) continue;
                    if (Math.abs(v) > depth * 0.5 * (1.0 + rough * 0.5 * TreeNoise.value(x * 0.3, y * 0.3, z * 0.3, seed + 5))) continue;
                    blocks.put(x, y, z, stone(s, y / Math.max(1.0, b), TreeNoise.value(x * 0.6, y * 0.6, z * 0.6, seed + 7)));
                }
            }
        }
        moss(blocks, s, seed);
    }
}
