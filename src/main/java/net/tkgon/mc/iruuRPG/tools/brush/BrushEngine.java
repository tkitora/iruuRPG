package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Works out what one use of the brush changes: reads the world (through {@link SurfaceProbe}) and returns the new block for every place that changes,
 * without touching the world; {@link WorldEditBridge} does the drawing (and with it the undo) and {@link BrushPreview} shows the same list to the player alone.
 */
final class BrushEngine {

    /** One block to put. */
    record Change(int x, int y, int z, BlockData data) {
    }

    /** What a stroke comes to: the changes, and a note when it was cut short or could not be done. */
    record Result(List<Change> changes, String note) {
    }

    private BrushEngine() {
    }

    static Result compute(World world, int cx, int cy, int cz, BrushSettings s, int cap) {
        SurfaceProbe probe = new SurfaceProbe(world, s.sunVector(), s.castShadows);
        Map<Long, Change> changes = new LinkedHashMap<>();
        Random random = new Random();
        String note = switch (s.mode) {
            case PAINT -> paint(probe, cx, cy, cz, s, random, changes, cap);
            case AUTO_SHADE -> shade(probe, cx, cy, cz, s, random, changes, cap);
            default -> terraform(probe, cx, cy, cz, s, random, changes, cap);
        };
        return new Result(new ArrayList<>(changes.values()), note);
    }

    // ---- shared ---------------------------------------------------------------------------------------------------------------

    private static double smooth(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return c * c * (3.0 - 2.0 * c);
    }

    /** How strongly the brush acts at a place: 1 at the centre, less toward the edge by the falloff. */
    private static double weight(BrushSettings s, double distance) {
        return 1.0 - s.falloff * smooth(distance);
    }

    private static boolean passes(BrushSettings s, SurfaceProbe probe, int x, int y, int z, Material type) {
        if (s.blockMaskOn) {
            Material base = BlockColors.baseOf(type);
            boolean in = s.blockMask.contains(type) || (base != null && s.blockMask.contains(base));
            if (in == s.blockMaskInvert) return false;
        }
        if (s.angleMaskOn) {
            double angle = probe.angle(x, y, z);
            boolean in = angle >= s.angleMin && angle <= s.angleMax;
            if (in == s.angleMaskInvert) return false;
        }
        if (s.shadeMaskOn) {
            boolean shaded = probe.shade(x, y, z) >= 0.35;
            if (shaded == s.shadeMaskInvert) return false;
        }
        return true;
    }

    private static double noise(double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = smooth(x - ix);
        double fy = smooth(y - iy);
        double fz = smooth(z - iz);
        double a = lerp(lerp(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx), lerp(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx), fy);
        double b = lerp(lerp(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx), lerp(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx), fy);
        return lerp(a, b, fz);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double hash(int x, int y, int z) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFL) / 65535.0;
    }

    /** One block of the pattern for the place: at random by weight, or by a smooth noise (patches instead of speckles) when the noise is on. */
    private static Material pick(BrushSettings s, int x, int y, int z, Random random) {
        int total = s.totalWeight();
        if (total <= 0) return null;
        double u = s.noise ? Math.max(0.0, Math.min(0.999, 0.5 + 1.1 * (noise(x / s.noiseScale, y / s.noiseScale, z / s.noiseScale) - 0.5))) : random.nextDouble();
        double target = u * total;
        double sum = 0.0;
        for (BrushSettings.Entry entry : s.pattern) {
            sum += entry.weight;
            if (target < sum) return entry.material;
        }
        return s.pattern.get(s.pattern.size() - 1).material;
    }

    /** The data of {@code target} in the shape of {@code original} (a stair stays a stair, a slab a slab); null when {@code target} has no such shape. */
    private static BlockData restyle(BlockData original, Material target) {
        String suffix = BlockColors.suffixOf(original.getMaterial());
        if (suffix.isEmpty()) return target.createBlockData();
        Material base = BlockColors.baseOf(target);
        Material variant = BlockColors.variantOf(base != null ? base : target, suffix);
        if (variant == null) return null;
        return BlockColors.variantData(original, variant);
    }

    private static boolean paintable(Material type) {
        return type.isSolid() && !type.isAir() && (type.isOccluding() || BlockColors.baseOf(type) != null) && !Tag.LEAVES.isTagged(type);
    }

    private static void put(Map<Long, Change> changes, int x, int y, int z, BlockData data) {
        changes.put(SurfaceProbe.pack(x, y, z), new Change(x, y, z, data));
    }

    // ---- paint ----------------------------------------------------------------------------------------------------------------

    private static String paint(SurfaceProbe probe, int cx, int cy, int cz, BrushSettings s, Random random, Map<Long, Change> changes, int cap) {
        if (s.pattern.isEmpty()) return "模様 (パターン) のブロックがありません。メニューの「模様」で足してください。";
        int r = s.radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double distance = s.shape.distance(dx, dy, dz, r);
                    if (distance > 1.0) continue;
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    Material type = probe.type(x, y, z);
                    if (!paintable(type)) continue;
                    if (s.surfaceOnly && !probe.exposed(x, y, z)) continue;
                    if (random.nextDouble() > s.strength * weight(s, distance)) continue;
                    if (!passes(s, probe, x, y, z, type)) continue;
                    Material material = pick(s, x, y, z, random);
                    if (material == null) continue;
                    BlockData data = restyle(probe.world().getBlockAt(x, y, z).getBlockData(), material);
                    if (data == null) continue;
                    put(changes, x, y, z, data);
                    if (changes.size() >= cap) return "変更が多すぎるので途中で止めました (" + cap + "ブロック)。";
                }
            }
        }
        return null;
    }

    // ---- auto shade -----------------------------------------------------------------------------------------------------------

    private static String shade(SurfaceProbe probe, int cx, int cy, int cz, BrushSettings s, Random random, Map<Long, Change> changes, int cap) {
        Set<Material> allowed = new java.util.HashSet<>();
        for (Material material : s.patternMaterials()) {
            Material base = BlockColors.baseOf(material);
            if (base != null) allowed.add(base);
        }
        int r = s.radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double distance = s.shape.distance(dx, dy, dz, r);
                    if (distance > 1.0) continue;
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    Material type = probe.type(x, y, z);
                    Material base = BlockColors.baseOf(type);
                    if (base == null || !probe.exposed(x, y, z)) continue;
                    double shade = probe.shade(x, y, z);
                    if (shade < 0.15) continue;
                    if (random.nextDouble() > weight(s, distance)) continue;
                    if (!passes(s, probe, x, y, z, type)) continue;
                    Material darker = BlockColors.darker(base, shade * s.shadeDepth, allowed, BlockColors.suffixOf(type));
                    if (darker == base) continue;
                    BlockData data = restyle(probe.world().getBlockAt(x, y, z).getBlockData(), darker);
                    if (data == null) continue;
                    put(changes, x, y, z, data);
                    if (changes.size() >= cap) return "変更が多すぎるので途中で止めました (" + cap + "ブロック)。";
                }
            }
        }
        return null;
    }

    // ---- terraform ------------------------------------------------------------------------------------------------------------

    private static boolean ground(SurfaceProbe probe, int x, int y, int z) {
        Material type = probe.type(x, y, z);
        return type.isSolid() && !type.isAir() && !Tag.LEAVES.isTagged(type) && !Tag.LOGS.isTagged(type);
    }

    /** The y of the highest block of ground in the column within {@code reach} of {@code y}, or Integer.MIN_VALUE when there is none. */
    private static int surface(SurfaceProbe probe, int x, int z, int y, int reach) {
        for (int at = y + reach; at >= y - reach; at--) if (ground(probe, x, at, z)) return at;
        return Integer.MIN_VALUE;
    }

    private static boolean grassy(Material type) {
        return type == Material.GRASS_BLOCK || type == Material.PODZOL || type == Material.MYCELIUM || type == Material.DIRT_PATH
                || type == Material.CRIMSON_NYLIUM || type == Material.WARPED_NYLIUM;
    }

    private static String terraform(SurfaceProbe probe, int cx, int cy, int cz, BrushSettings s, Random random, Map<Long, Change> changes, int cap) {
        int r = s.radius;
        int reach = r + 4;
        // the heights of the columns, a ring wider than the brush for smoothing
        Map<Long, Integer> heights = new HashMap<>();
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                heights.put(SurfaceProbe.pack(cx + dx, 0, cz + dz), surface(probe, cx + dx, cz + dz, cy, reach));
            }
        }
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz) / Math.max(0.5, r);
                if (distance > 1.0) continue;
                int x = cx + dx;
                int z = cz + dz;
                int height = heights.get(SurfaceProbe.pack(x, 0, z));
                if (height == Integer.MIN_VALUE) continue;
                double w = weight(s, distance);
                int target = switch (s.mode) {
                    case RAISE -> height + (int) Math.round(s.height * w);
                    case LOWER -> height - (int) Math.round(s.height * w);
                    case SMOOTH -> {
                        double sum = 0.0;
                        int count = 0;
                        for (int ox = -1; ox <= 1; ox++) {
                            for (int oz = -1; oz <= 1; oz++) {
                                Integer neighbour = heights.get(SurfaceProbe.pack(x + ox, 0, z + oz));
                                if (neighbour == null || neighbour == Integer.MIN_VALUE) continue;
                                sum += neighbour;
                                count++;
                            }
                        }
                        double average = count == 0 ? height : sum / count;
                        yield (int) Math.round(height + (average - height) * Math.min(1.0, w * (0.4 + 0.6 * s.strength)));
                    }
                    default -> (int) Math.round(height + (cy - height) * w);
                };
                if (target == height) continue;
                if (!adjustColumn(probe, s, random, changes, x, z, height, target)) continue;
                if (changes.size() >= cap) return "変更が多すぎるので途中で止めました (" + cap + "ブロック)。";
            }
        }
        return null;
    }

    private static boolean adjustColumn(SurfaceProbe probe, BrushSettings s, Random random, Map<Long, Change> changes, int x, int z, int height, int target) {
        Material top = probe.type(x, height, z);
        Material below = probe.solid(x, height - 1, z) ? probe.type(x, height - 1, z) : top;
        Material fill = grassy(top) ? (grassy(below) ? Material.DIRT : below) : below;
        if (target > height) {
            for (int y = height + 1; y <= target; y++) {
                if (probe.solid(x, y, z)) return false;
                Material material;
                if (!s.pattern.isEmpty()) material = pick(s, x, y, z, random);
                else material = y == target ? top : fill;
                if (material == null) material = top;
                put(changes, x, y, z, material.createBlockData());
            }
            // the old top is covered: it becomes the filling under it
            if (s.pattern.isEmpty() && grassy(top)) put(changes, x, height, z, fill.createBlockData());
            return true;
        }
        for (int y = height; y > target; y--) put(changes, x, y, z, Material.AIR.createBlockData());
        // plants standing on the old top fall with it
        if (!probe.solid(x, height + 1, z) && !probe.type(x, height + 1, z).isAir()) put(changes, x, height + 1, z, Material.AIR.createBlockData());
        // the new top has the surface of the old one
        if (s.pattern.isEmpty() && grassy(top) && probe.solid(x, target, z)) put(changes, x, target, z, top.createBlockData());
        else if (!s.pattern.isEmpty() && probe.solid(x, target, z)) {
            Material material = pick(s, x, target, z, random);
            if (material != null) put(changes, x, target, z, material.createBlockData());
        }
        return true;
    }
}
