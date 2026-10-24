package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The gradient between two blocks: a ramp of blocks whose colours (the average colour of their textures, see {@link BlockColors}) go in even steps from the first block to the
 * second. Blocks of the blacklist are never used in between. A natural block is not mixed with dyed ones (concrete, wool, terracotta in a colour) unless an end is dyed.
 */
final class Gradients {

    /** One block of a preset and how often it is chosen against the others (or how long a stretch of the gradient it takes). */
    record Part(Material material, int weight) {
    }

    /**
     * A ready-made set of blocks: either made from two ends and the steps between ({@code parts} null: the ramp is worked out when it is used) or listed by hand ({@code parts}),
     * with whether it is laid as a gradient or scattered by weight.
     */
    record Preset(String name, Material from, Material to, int steps, List<Part> parts, boolean gradient) {
        Preset(String name, Material from, Material to, int steps) {
            this(name, from, to, steps, null, true);
        }

        /** A preset listed by hand. */
        static Preset manual(String name, List<Part> parts, boolean gradient) {
            return new Preset(name, null, null, 0, List.copyOf(parts), gradient);
        }

        boolean generated() {
            return parts == null;
        }
    }

    /** The presets that come with the brush; the player's own are kept in the yaml ({@link BrushStore}). */
    static final List<Preset> BUILT_IN = List.of(
            new Preset("石 → 深層岩", Material.STONE, Material.DEEPSLATE, 4),
            new Preset("安山岩 → 黒石", Material.ANDESITE, Material.BLACKSTONE, 4),
            new Preset("草 → 土", Material.MOSS_BLOCK, Material.DIRT, 3),
            new Preset("砂 → 赤い砂", Material.SAND, Material.RED_SAND, 3),
            new Preset("砂岩 → テラコッタ", Material.SANDSTONE, Material.TERRACOTTA, 4),
            new Preset("雪 → 氷", Material.SNOW_BLOCK, Material.BLUE_ICE, 3),
            new Preset("白 → 黒 (コンクリート)", Material.WHITE_CONCRETE, Material.BLACK_CONCRETE, 6),
            new Preset("夕焼け (橙 → 紫)", Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE, 5),
            new Preset("森 (黄緑 → 深緑)", Material.LIME_CONCRETE, Material.GREEN_CONCRETE, 4),
            new Preset("白樺 → ダークオーク", Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS, 4),
            new Preset("岩肌 (安山岩 → 玄武岩)", Material.ANDESITE, Material.BASALT, 4),
            new Preset("溶岩の冷え (赤 → 黒)", Material.RED_TERRACOTTA, Material.BLACKSTONE, 4));

    private Gradients() {
    }

    /** What kind of block it is, so a stone gradient does not pick up a log or a copper block on the way: wood, metal, dyed (concrete, wool, terracotta in a colour), ice or the rest (stone, earth, sand). */
    private static String kind(Material material) {
        String name = material.name();
        if (BlockColors.isDyed(material)) return "dyed";
        if (name.contains("LOG") || name.contains("WOOD") || name.contains("PLANKS") || name.contains("STEM") || name.contains("HYPHAE")) return "wood";
        if (name.contains("COPPER") || name.contains("IRON") || name.contains("GOLD") || name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("LAPIS") || name.contains("NETHERITE") || name.contains("REDSTONE")) return "metal";
        if (name.contains("ICE") || name.contains("SNOW")) return "ice";
        return "earth";
    }

    /** The ramp from {@code from} to {@code to} with up to {@code steps} blocks between (fewer when the blocks run out). Always starts with {@code from} and ends with {@code to}. */
    static List<Material> ramp(Material from, Material to, int steps, Set<Material> blacklist) {
        List<Material> ramp = new ArrayList<>();
        ramp.add(from);
        int a = BlockColors.rgb(from);
        int b = BlockColors.rgb(to);
        String kindFrom = kind(from);
        String kindTo = kind(to);
        double hueFrom = BlockColors.hueOf(a);
        double hueTo = BlockColors.hueOf(b);
        int count = Math.max(0, Math.min(14, steps));
        for (int index = 1; index <= count; index++) {
            int wanted = BlockColors.mix(a, b, index / (double) (count + 1));
            double wantedSaturation = BlockColors.saturationOf(wanted);
            double wantedHue = BlockColors.hueOf(wanted);
            Material best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Material candidate : BlockColors.all()) {
                if (candidate == from || candidate == to || blacklist.contains(candidate) || ramp.contains(candidate)) continue;
                // the same kind of block as an end, and the same family of colour as the colour wanted
                String kind = kind(candidate);
                if (!kind.equals(kindFrom) && !kind.equals(kindTo)) continue;
                int rgb = BlockColors.rgb(candidate);
                double saturation = BlockColors.saturationOf(rgb);
                if (Math.abs(saturation - wantedSaturation) > 0.3) continue;
                if (wantedSaturation > 0.15 && saturation > 0.12 && BlockColors.hueGapOf(wantedHue, BlockColors.hueOf(rgb)) > 40.0) continue;
                double d = BlockColors.colorDistance(rgb, wanted);
                // a block that shares a word with an end (stone → stone bricks, deepslate → deepslate tiles) is the better choice
                String word = candidate.name().contains("_") ? candidate.name().substring(0, candidate.name().indexOf('_')) : candidate.name();
                if (from.name().startsWith(word) || to.name().startsWith(word)) d *= 0.6;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = candidate;
                }
            }
            if (best != null) ramp.add(best);
        }
        if (!ramp.contains(to)) ramp.add(to);
        return ramp;
    }

    /** Puts the preset in the settings' block list (the one both ways of laying blocks share) and the way of laying it. A generated preset works out its ramp now. */
    static void apply(Preset preset, BrushSettings s, Set<Material> blacklist) {
        s.pattern.clear();
        if (preset.generated()) {
            for (Material material : ramp(preset.from(), preset.to(), preset.steps(), blacklist)) s.pattern.add(new BrushSettings.Entry(material, 1));
            s.gradientFrom = preset.from();
            s.gradientTo = preset.to();
            s.gradientSteps = preset.steps();
            s.patternMode = BrushSettings.PatternMode.GRADIENT;
        } else {
            for (Part part : preset.parts()) s.pattern.add(new BrushSettings.Entry(part.material(), part.weight()));
            s.patternMode = preset.gradient() ? BrushSettings.PatternMode.GRADIENT : BrushSettings.PatternMode.RANDOM;
        }
    }

    /** The settings' block list as a preset of that name. */
    static Preset fromSettings(String name, BrushSettings s) {
        List<Part> parts = new ArrayList<>();
        for (BrushSettings.Entry entry : s.pattern) parts.add(new Part(entry.material, entry.weight));
        return Preset.manual(name, parts, s.patternMode == BrushSettings.PatternMode.GRADIENT);
    }

    /** The block at {@code t} (0 - 1) along the list, each taking a stretch as long as its weight. */
    static Material pickAlong(List<BrushSettings.Entry> list, double t) {
        int total = 0;
        for (BrushSettings.Entry entry : list) total += Math.max(1, entry.weight);
        double target = Math.max(0.0, Math.min(0.9999, t)) * total;
        double sum = 0.0;
        for (BrushSettings.Entry entry : list) {
            sum += Math.max(1, entry.weight);
            if (target < sum) return entry.material;
        }
        return list.get(list.size() - 1).material;
    }

    /** The colour (0xRRGGBB) at {@code t} (0 - 1) along the ramp, going smoothly from one block's colour to the next. */
    static int colorAt(List<Material> ramp, double t) {
        if (ramp.size() == 1) return BlockColors.rgb(ramp.get(0));
        double position = Math.max(0.0, Math.min(1.0, t)) * (ramp.size() - 1);
        int index = Math.min(ramp.size() - 2, (int) Math.floor(position));
        return BlockColors.mix(BlockColors.rgb(ramp.get(index)), BlockColors.rgb(ramp.get(index + 1)), position - index);
    }

    /** The block of the ramp nearest in colour to {@code rgb}. */
    static Material nearest(List<Material> ramp, int rgb) {
        Material best = ramp.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (Material candidate : ramp) {
            double d = BlockColors.colorDistance(BlockColors.rgb(candidate), rgb);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    /** The block of the ramp at {@code t} (0 - 1). */
    static Material at(List<Material> ramp, double t) {
        int index = (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * (ramp.size() - 1));
        return ramp.get(index);
    }
}
