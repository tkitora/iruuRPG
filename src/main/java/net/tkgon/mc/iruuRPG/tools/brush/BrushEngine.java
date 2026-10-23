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

    static Result compute(World world, int cx, int cy, int cz, BrushSettings s, int cap, Set<Material> blacklist) {
        SurfaceProbe probe = new SurfaceProbe(world, s.sunVector(), s.castShadows);
        Map<Long, Change> changes = new LinkedHashMap<>();
        Random random = new Random();
        String note = switch (s.mode) {
            case PAINT -> paint(probe, cx, cy, cz, s, random, changes, cap, blacklist);
            case AUTO_SHADE -> shade(probe, cx, cy, cz, s, random, changes, cap, blacklist);
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
            boolean shaded = s.toonSteps > 1 ? band(s, probe.shade(x, y, z)) >= s.shadeBand : probe.shade(x, y, z) >= 0.35;
            if (shaded == s.shadeMaskInvert) return false;
        }
        return true;
    }

    /** The toon band of a shade (0 - 1): 0 for the lit, up to {@code toonSteps - 1} for the darkest. */
    private static int band(BrushSettings s, double shade) {
        return Math.min(s.toonSteps - 1, (int) Math.floor(Math.max(0.0, Math.min(1.0, shade)) * s.toonSteps));
    }

    /** The shade as the brush uses it: smooth, or (toon) cut into steps with the same darkness all through a band. */
    private static double bandedShade(BrushSettings s, double shade) {
        if (s.toonSteps <= 1) return shade;
        int band = band(s, shade);
        return band == 0 ? 0.0 : band / (double) (s.toonSteps - 1);
    }

    /** Whether the selection (when there is one) lets the brush work at the place. */
    private static boolean inSelection(BrushSettings s, int x, int y, int z) {
        return s.selection == null || s.selection.contains(x, y, z);
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

    /** Where along the gradient (0 - 1) the place is, by the axis of the settings. */
    private static double gradientPosition(BrushSettings s, int dx, int dy, int dz, double distance, int x, int y, int z) {
        double r = Math.max(0.5, s.radius);
        return switch (s.gradientAxis) {
            case RADIAL -> distance;
            case VERTICAL -> (dy + r) / (2.0 * r);
            case DIRECTIONAL -> (dx * s.facing.getX() + dz * s.facing.getZ() + r) / (2.0 * r);
            case NOISE -> noise(x / s.noiseScale, y / s.noiseScale, z / s.noiseScale);
        };
    }

    private static String paint(SurfaceProbe probe, int cx, int cy, int cz, BrushSettings s, Random random, Map<Long, Change> changes, int cap, Set<Material> blacklist) {
        boolean gradient = s.patternMode == BrushSettings.PatternMode.GRADIENT;
        List<Material> ramp = gradient ? Gradients.ramp(s.gradientFrom, s.gradientTo, s.gradientSteps, blacklist) : List.of();
        if (!gradient && s.pattern.isEmpty()) return "模様 (パターン) のブロックがありません。メニューの「模様」で足してください。";
        int r = s.radius;
        BrushShape.Volume volume = s.shape.volume(r, random.nextLong());
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double distance = volume.distance(dx, dy, dz);
                    if (distance > 1.0) continue;
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    Material type = probe.type(x, y, z);
                    if (!paintable(type)) continue;
                    if (s.surfaceOnly && !probe.exposed(x, y, z)) continue;
                    if (!inSelection(s, x, y, z)) continue;
                    if (random.nextDouble() > s.strength * weight(s, distance) * volume.density(distance)) continue;
                    if (!passes(s, probe, x, y, z, type)) continue;
                    Material material;
                    if (gradient) {
                        double t = gradientPosition(s, dx, dy, dz, distance, x, y, z) + (random.nextDouble() - 0.5) * s.dither * 2.0 / Math.max(1, ramp.size() - 1);
                        if (s.additive && BlockColors.known(BlockColors.baseOf(type) != null ? BlockColors.baseOf(type) : type)) {
                            // painting again moves the colour that is there on toward the gradient's colour, a share at a time
                            Material current = BlockColors.baseOf(type) != null ? BlockColors.baseOf(type) : type;
                            int moved = BlockColors.mix(BlockColors.rgb(current), Gradients.colorAt(ramp, t), s.additiveRate * weight(s, distance));
                            material = Gradients.nearest(ramp, moved);
                            if (material == current) continue;
                        } else {
                            material = Gradients.at(ramp, t);
                        }
                    } else {
                        material = pick(s, x, y, z, random);
                    }
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

    private static String shade(SurfaceProbe probe, int cx, int cy, int cz, BrushSettings s, Random random, Map<Long, Change> changes, int cap, Set<Material> blacklist) {
        Set<Material> allowed = new java.util.HashSet<>();
        for (Material material : s.patternMaterials()) {
            Material base = BlockColors.baseOf(material);
            if (base != null) allowed.add(base);
        }
        int r = s.radius;
        BrushShape.Volume volume = s.shape.volume(r, random.nextLong());
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double distance = volume.distance(dx, dy, dz);
                    if (distance > 1.0) continue;
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    Material type = probe.type(x, y, z);
                    Material base = BlockColors.baseOf(type);
                    if (base == null || !probe.exposed(x, y, z)) continue;
                    if (!inSelection(s, x, y, z)) continue;
                    double shade = bandedShade(s, probe.shade(x, y, z));
                    if (shade < 0.15) continue;
                    if (random.nextDouble() > weight(s, distance) * volume.density(distance)) continue;
                    if (!passes(s, probe, x, y, z, type)) continue;
                    Material darker = BlockColors.darker(base, shade * s.shadeDepth, allowed, BlockColors.suffixOf(type), blacklist);
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
}
