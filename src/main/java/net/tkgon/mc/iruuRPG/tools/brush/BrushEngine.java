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

    static Result compute(World world, int cx, int cy, int cz, BrushSettings s, int cap, Set<Material> shadeBlacklist) {
        SurfaceProbe probe = new SurfaceProbe(world, s);
        Map<Long, Change> changes = new LinkedHashMap<>();
        Random random = new Random();
        String note = switch (s.mode) {
            case PAINT -> paint(probe, cx, cy, cz, s, random, changes, cap);
            case AUTO_SHADE -> shade(probe, cx, cy, cz, s, random, changes, cap, shadeBlacklist);
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

    static boolean passes(BrushSettings s, SurfaceProbe probe, int x, int y, int z, Material type) {
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
        if (s.heightMaskOn && (s.heightAbove ? y < s.heightValue : y > s.heightValue)) return false;
        if (s.relationMaskOn && !related(s, probe, x, y, z)) return false;
        if (s.shadeMaskOn) {
            boolean shaded = s.toonSteps > 1 ? band(s, totalShade(probe, s, x, y, z)) >= s.shadeBand : totalShade(probe, s, x, y, z) >= 0.35;
            if (shaded == s.shadeMaskInvert) return false;
        }
        return true;
    }

    /**
     * Whether one of the blocks of the relation mask is above the place (or below it, the other way) within the reach, in its own column or the eight round it (so the diagonal
     * ones count too).
     */
    private static boolean related(BrushSettings s, SurfaceProbe probe, int x, int y, int z) {
        if (s.relationBlocks.isEmpty()) return false;
        int step = s.relationUnder ? 1 : -1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int k = 1; k <= s.relationReach; k++) {
                    Material type = probe.type(x + dx, y + step * k, z + dz);
                    if (s.relationBlocks.contains(type)) return true;
                    Material base = BlockColors.baseOf(type);
                    if (base != null && s.relationBlocks.contains(base)) return true;
                }
            }
        }
        return false;
    }

    /** The toon band of a shade (0 - 1): 0 for the lit, up to {@code toonSteps - 1} for the darkest. */
    private static int band(BrushSettings s, double shade) {
        return Math.min(s.toonSteps - 1, (int) Math.floor(Math.max(0.0, Math.min(1.0, shade)) * s.toonSteps));
    }

    /** The shade as the brush uses it: smooth, or (toon) cut into steps with the same darkness all through a band. */
    static double bandedShade(BrushSettings s, double shade) {
        if (s.toonSteps <= 1) return shade;
        int band = band(s, shade);
        return band == 0 ? 0.0 : band / (double) (s.toonSteps - 1);
    }

    /** Whether the selection (when there is one) lets the brush work at the place. */
    private static boolean inSelection(BrushSettings s, int x, int y, int z) {
        return s.selection == null || s.selection.contains(x, y, z);
    }

    /** The shade of the place from every source switched on, 0 - 1: the light, the ambient occlusion, the curvature, the noise and the cells added up. */
    static double totalShade(SurfaceProbe probe, BrushSettings s, int x, int y, int z) {
        double shade = s.lightOn ? probe.shade(x, y, z) : 0.0;
        if (s.ambientOn) shade += s.ambientStrength * probe.ambient(x, y, z);
        if (s.curvatureOn) {
            double curvature = probe.curvature(x, y, z);
            shade += s.curvatureStrength * Math.max(0.0, s.curvatureConvex ? curvature : -curvature);
        }
        if (s.noiseShadeOn) {
            double n = Procedural.sample(s, x + 0.5, y + 0.5, z + 0.5, s.noiseShadeScale);
            shade += s.noiseShadeStrength * Math.max(0.0, Math.min(1.0, (n - 0.45) * 2.5));
        }
        if (s.cellOn) {
            double[] cell = s.cellAngular
                    ? Procedural.cellBox((x + 0.5) / s.cellSize, (y + 0.5) / s.cellSize, (z + 0.5) / s.cellSize)
                    : Procedural.cell(x + 0.5, y + 0.5, z + 0.5, s.cellSize);
            double amount = s.cellEdges ? 1.0 - Math.min(1.0, (cell[1] - cell[0]) / 0.18) : (cell[2] > 0.5 ? 1.0 : 0.0);
            shade += s.cellStrength * amount;
        }
        return Math.max(0.0, Math.min(1.0, shade));
    }

    /** One block of the pattern for the place: at random by weight, or by a smooth noise (patches instead of speckles) when the noise is on. */
    private static Material pick(BrushSettings s, int x, int y, int z, Random random) {
        int total = s.totalWeight();
        if (total <= 0) return null;
        double u = s.noise ? Math.max(0.0, Math.min(0.999, 0.5 + 1.1 * (Procedural.sample(s, x + 0.5, y + 0.5, z + 0.5, s.noiseScale) - 0.5))) : random.nextDouble();
        double target = u * total;
        double sum = 0.0;
        for (BrushSettings.Entry entry : s.pattern) {
            sum += entry.weight;
            if (target < sum) return entry.material;
        }
        return s.pattern.get(s.pattern.size() - 1).material;
    }

    /** The data of {@code target} in the shape of {@code original} (a stair stays a stair, a slab a slab); null when {@code target} has no such shape. */
    static BlockData restyle(BlockData original, Material target) {
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
    private static double gradientPosition(BrushSettings s, SurfaceProbe probe, int dx, int dy, int dz, double distance, int x, int y, int z) {
        double r = Math.max(0.5, s.radius);
        double t = switch (s.gradientAxis) {
            case RADIAL -> distance;
            case VERTICAL -> (dy + r) / (2.0 * r);
            case DIRECTIONAL -> (dx * s.facing.getX() + dz * s.facing.getZ() + r) / (2.0 * r);
            case NOISE -> Procedural.sample(s, x + 0.5, y + 0.5, z + 0.5, s.noiseScale);
            case STROKE -> s.strokeT;
            case ANGLE -> probe.angle(x, y, z) / 90.0;
            case CURVATURE -> (probe.curvature(x, y, z) + 1.0) / 2.0;
            case AMBIENT -> probe.ambient(x, y, z);
            case LIGHT -> probe.shade(x, y, z);
            case BLOCK -> referenceT(s, probe, x, y, z);
        };
        t = Math.max(0.0, Math.min(1.0, t));
        return s.gradientReverse ? 1.0 - t : t;
    }

    /**
     * Where the place is along a gradient that starts from a block: how far from the nearest of the starting blocks, in its column or the eight round it, going up when the
     * gradient goes down from them and the other way round, over the reference span (0 at the block, 1 at the span or when none is found).
     */
    private static double referenceT(BrushSettings s, SurfaceProbe probe, int x, int y, int z) {
        if (s.referenceBlocks.isEmpty()) return 0.0;
        int step = s.referenceDown ? 1 : -1;
        int span = Math.max(1, s.referenceSpan);
        int best = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int k = 0; k <= span * 2 && k < best; k++) {
                    Material type = probe.type(x + dx, y + step * k, z + dz);
                    Material base = BlockColors.baseOf(type);
                    if (s.referenceBlocks.contains(type) || (base != null && s.referenceBlocks.contains(base))) {
                        best = k;
                        break;
                    }
                }
            }
        }
        return best == Integer.MAX_VALUE ? 1.0 : Math.min(1.0, best / (double) span);
    }

    private static String paint(SurfaceProbe probe, int cx, int cy, int cz, BrushSettings s, Random random, Map<Long, Change> changes, int cap) {
        boolean gradient = s.patternMode == BrushSettings.PatternMode.GRADIENT;
        if (s.pattern.isEmpty()) return "模様 (パターン) のブロックがありません。メニューの「模様」で足すか、プリセットを選んでください。";
        // (the block list is the same for both: laid along the gradient, each block as long a stretch as its weight, or scattered by weight)
        List<Material> ramp = new ArrayList<>();
        for (BrushSettings.Entry entry : s.pattern) ramp.add(entry.material);
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
                        double t = gradientPosition(s, probe, dx, dy, dz, distance, x, y, z) + (random.nextDouble() - 0.5) * s.dither * 2.0 / Math.max(1, ramp.size() - 1);
                        if (s.additive && BlockColors.known(BlockColors.baseOf(type) != null ? BlockColors.baseOf(type) : type)) {
                            // painting again moves the colour that is there on toward the gradient's colour, a share at a time
                            Material current = BlockColors.baseOf(type) != null ? BlockColors.baseOf(type) : type;
                            int moved = BlockColors.mix(BlockColors.rgb(current), Gradients.colorAt(ramp, t), s.additiveRate * weight(s, distance));
                            material = Gradients.nearest(ramp, moved);
                            if (material == current) continue;
                        } else {
                            material = Gradients.pickAlong(s.pattern, t);
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

    /** The blocks the automatic shade may choose from: the pattern's, or (when there is none) any. */
    static Set<Material> shadePalette(BrushSettings s) {
        Set<Material> allowed = new java.util.HashSet<>();
        for (Material material : s.patternMaterials()) {
            Material base = BlockColors.baseOf(material);
            if (base != null) allowed.add(base);
        }
        return allowed;
    }

    /** The change the automatic shade makes at one block (null when it stays as it is): used for a whole selection at once. */
    static Change shadeOne(SurfaceProbe probe, BrushSettings s, int x, int y, int z, Set<Material> allowed, Set<Material> blacklist) {
        Material type = probe.type(x, y, z);
        Material base = BlockColors.baseOf(type);
        if (base == null || !probe.exposed(x, y, z)) return null;
        double shade = bandedShade(s, totalShade(probe, s, x, y, z));
        if (shade < 0.15) return null;
        if (!passes(s, probe, x, y, z, type)) return null;
        Material darker = BlockColors.darker(base, shade * s.shadeDepth, allowed, BlockColors.suffixOf(type), blacklist);
        if (darker == base) return null;
        BlockData data = restyle(probe.world().getBlockAt(x, y, z).getBlockData(), darker);
        return data == null ? null : new Change(x, y, z, data);
    }

    private static String shade(SurfaceProbe probe, int cx, int cy, int cz, BrushSettings s, Random random, Map<Long, Change> changes, int cap, Set<Material> blacklist) {
        Set<Material> allowed = shadePalette(s);
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
                    double shade = bandedShade(s, totalShade(probe, s, x, y, z));
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
