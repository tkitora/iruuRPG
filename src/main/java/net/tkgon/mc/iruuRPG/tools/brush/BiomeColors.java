package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.block.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The colours the game tints with in each biome of the overworld (Java 1.20.4): the grass, the leaves and the water. The grass and the leaves come from the biome's temperature
 * and downfall on the game's colour maps (or the biome's own colour: badlands, swamps, cherry grove, dark forest); the water is the biome's water colour. The game blends them
 * over a few blocks, so a ramp of biomes shows as a smooth gradient of grass. The nether, the end and the void are left out (they change the sky, the fog and the sounds).
 */
final class BiomeColors {

    /** Which tint the gradient of biomes follows. */
    enum Kind {
        GRASS("草の色", "草ブロック・草・つるの色"),
        FOLIAGE("葉の色", "葉 (オーク・ジャングル・アカシア・ダークオーク・マングローブ)・つたの色"),
        WATER("水の色", "水の色");

        final String label;
        final String description;

        Kind(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /** The three tints of a biome, and what sort of biome it is (a gradient keeps to the sorts of its two ends). */
    record Tint(int grass, int foliage, int water, String sort) {
        int of(Kind kind) {
            return switch (kind) {
                case GRASS -> grass;
                case FOLIAGE -> foliage;
                case WATER -> water;
            };
        }
    }

    private static final int WATER = 0x3F76E4;
    private static final Map<Biome, Tint> TINTS = new EnumMap<>(Biome.class);
    private static final Map<Biome, String> LABELS = new EnumMap<>(Biome.class);

    static {
        // land
        land(Biome.PLAINS, 0x91BD59, 0x77AB2F);
        land(Biome.SUNFLOWER_PLAINS, 0x91BD59, 0x77AB2F);
        land(Biome.MEADOW, 0x83BB6D, 0x63A948, 0x0E4ECF);
        land(Biome.CHERRY_GROVE, 0xB6DB61, 0xB6DB61, 0x5DB7EF);
        land(Biome.FOREST, 0x79C05A, 0x59AE30);
        land(Biome.FLOWER_FOREST, 0x79C05A, 0x59AE30);
        land(Biome.BIRCH_FOREST, 0x88BB67, 0x6BA941);
        land(Biome.OLD_GROWTH_BIRCH_FOREST, 0x88BB67, 0x6BA941);
        land(Biome.DARK_FOREST, 0x507A32, 0x59AE30);
        land(Biome.TAIGA, 0x86B783, 0x68A464);
        land(Biome.OLD_GROWTH_PINE_TAIGA, 0x86B87F, 0x68A55F);
        land(Biome.OLD_GROWTH_SPRUCE_TAIGA, 0x86B783, 0x68A464);
        land(Biome.JUNGLE, 0x59C93C, 0x30BB0B);
        land(Biome.BAMBOO_JUNGLE, 0x59C93C, 0x30BB0B);
        land(Biome.SPARSE_JUNGLE, 0x64C73F, 0x3EB80F);
        land(Biome.MUSHROOM_FIELDS, 0x55C93F, 0x2BBB0F);
        land(Biome.SAVANNA, 0xBFB755, 0xAEA42A);
        land(Biome.SAVANNA_PLATEAU, 0xBFB755, 0xAEA42A);
        land(Biome.WINDSWEPT_SAVANNA, 0xBFB755, 0xAEA42A);
        land(Biome.DESERT, 0xBFB755, 0xAEA42A);
        land(Biome.BADLANDS, 0x90814D, 0x9E814D);
        land(Biome.WOODED_BADLANDS, 0x90814D, 0x9E814D);
        land(Biome.ERODED_BADLANDS, 0x90814D, 0x9E814D);
        land(Biome.WINDSWEPT_HILLS, 0x8AB689, 0x6DA36B);
        land(Biome.WINDSWEPT_GRAVELLY_HILLS, 0x8AB689, 0x6DA36B);
        land(Biome.WINDSWEPT_FOREST, 0x8AB689, 0x6DA36B);
        land(Biome.STONY_PEAKS, 0x9ABE4B, 0x82AC1E);
        land(Biome.SWAMP, 0x6A7039, 0x6A7039, 0x617B64);
        land(Biome.MANGROVE_SWAMP, 0x6A7039, 0x8DB127, 0x3A7A6A);
        // shores and water
        put(Biome.BEACH, 0x91BD59, 0x77AB2F, WATER, "water");
        put(Biome.STONY_SHORE, 0x8AB689, 0x6DA36B, WATER, "water");
        put(Biome.RIVER, 0x8EB971, 0x71A74D, WATER, "water");
        put(Biome.OCEAN, 0x8EB971, 0x71A74D, WATER, "water");
        put(Biome.DEEP_OCEAN, 0x8EB971, 0x71A74D, WATER, "water");
        put(Biome.LUKEWARM_OCEAN, 0x8EB971, 0x71A74D, 0x45ADF2, "water");
        put(Biome.DEEP_LUKEWARM_OCEAN, 0x8EB971, 0x71A74D, 0x45ADF2, "water");
        put(Biome.WARM_OCEAN, 0x8EB971, 0x71A74D, 0x43D5EE, "water");
        put(Biome.COLD_OCEAN, 0x8EB971, 0x71A74D, 0x3D57D6, "water");
        put(Biome.DEEP_COLD_OCEAN, 0x8EB971, 0x71A74D, 0x3D57D6, "water");
        // snow (snow falls and water freezes in these; the frozen rivers and oceans are with the water)
        put(Biome.SNOWY_PLAINS, 0x80B497, 0x60A17B, WATER, "snow");
        put(Biome.ICE_SPIKES, 0x80B497, 0x60A17B, WATER, "snow");
        put(Biome.SNOWY_TAIGA, 0x80B497, 0x60A17B, 0x3D57D6, "snow");
        put(Biome.SNOWY_BEACH, 0x83B593, 0x64A278, 0x3D57D6, "snow");
        put(Biome.GROVE, 0x80B497, 0x60A17B, WATER, "snow");
        put(Biome.SNOWY_SLOPES, 0x80B497, 0x60A17B, WATER, "snow");
        put(Biome.FROZEN_PEAKS, 0x80B497, 0x60A17B, WATER, "snow");
        put(Biome.JAGGED_PEAKS, 0x80B497, 0x60A17B, WATER, "snow");
        put(Biome.FROZEN_RIVER, 0x80B497, 0x60A17B, 0x3938C9, "water");
        put(Biome.FROZEN_OCEAN, 0x80B497, 0x60A17B, 0x3938C9, "water");
        put(Biome.DEEP_FROZEN_OCEAN, 0x8EB971, 0x71A74D, 0x3938C9, "water");
        // under the ground
        put(Biome.LUSH_CAVES, 0x8EB971, 0x71A74D, WATER, "cave");
        put(Biome.DRIPSTONE_CAVES, 0x91BD59, 0x77AB2F, WATER, "cave");
        put(Biome.DEEP_DARK, 0x91BD59, 0x77AB2F, WATER, "cave");

        LABELS.put(Biome.PLAINS, "平原");
        LABELS.put(Biome.SUNFLOWER_PLAINS, "ヒマワリ平原");
        LABELS.put(Biome.MEADOW, "牧草地");
        LABELS.put(Biome.CHERRY_GROVE, "桜の林");
        LABELS.put(Biome.FOREST, "森林");
        LABELS.put(Biome.FLOWER_FOREST, "花の森");
        LABELS.put(Biome.BIRCH_FOREST, "シラカバの森");
        LABELS.put(Biome.OLD_GROWTH_BIRCH_FOREST, "シラカバの原生林");
        LABELS.put(Biome.DARK_FOREST, "暗い森");
        LABELS.put(Biome.TAIGA, "タイガ");
        LABELS.put(Biome.OLD_GROWTH_PINE_TAIGA, "マツの原生タイガ");
        LABELS.put(Biome.OLD_GROWTH_SPRUCE_TAIGA, "トウヒの原生タイガ");
        LABELS.put(Biome.JUNGLE, "ジャングル");
        LABELS.put(Biome.BAMBOO_JUNGLE, "竹林");
        LABELS.put(Biome.SPARSE_JUNGLE, "まばらなジャングル");
        LABELS.put(Biome.MUSHROOM_FIELDS, "キノコ島");
        LABELS.put(Biome.SAVANNA, "サバンナ");
        LABELS.put(Biome.SAVANNA_PLATEAU, "サバンナの台地");
        LABELS.put(Biome.WINDSWEPT_SAVANNA, "吹きさらしのサバンナ");
        LABELS.put(Biome.DESERT, "砂漠");
        LABELS.put(Biome.BADLANDS, "荒野");
        LABELS.put(Biome.WOODED_BADLANDS, "森のある荒野");
        LABELS.put(Biome.ERODED_BADLANDS, "侵食された荒野");
        LABELS.put(Biome.WINDSWEPT_HILLS, "吹きさらしの丘");
        LABELS.put(Biome.WINDSWEPT_GRAVELLY_HILLS, "吹きさらしの砂利の丘");
        LABELS.put(Biome.WINDSWEPT_FOREST, "吹きさらしの森");
        LABELS.put(Biome.STONY_PEAKS, "石だらけの山頂");
        LABELS.put(Biome.SWAMP, "湿地帯");
        LABELS.put(Biome.MANGROVE_SWAMP, "マングローブの沼地");
        LABELS.put(Biome.BEACH, "砂浜");
        LABELS.put(Biome.STONY_SHORE, "石だらけの海岸");
        LABELS.put(Biome.RIVER, "川");
        LABELS.put(Biome.OCEAN, "海洋");
        LABELS.put(Biome.DEEP_OCEAN, "深海");
        LABELS.put(Biome.LUKEWARM_OCEAN, "ぬるい海");
        LABELS.put(Biome.DEEP_LUKEWARM_OCEAN, "ぬるい深海");
        LABELS.put(Biome.WARM_OCEAN, "暖かい海");
        LABELS.put(Biome.COLD_OCEAN, "冷たい海");
        LABELS.put(Biome.DEEP_COLD_OCEAN, "冷たい深海");
        LABELS.put(Biome.SNOWY_PLAINS, "雪原");
        LABELS.put(Biome.ICE_SPIKES, "樹氷");
        LABELS.put(Biome.SNOWY_TAIGA, "雪のタイガ");
        LABELS.put(Biome.SNOWY_BEACH, "雪の砂浜");
        LABELS.put(Biome.GROVE, "林");
        LABELS.put(Biome.SNOWY_SLOPES, "雪の斜面");
        LABELS.put(Biome.FROZEN_PEAKS, "凍った山頂");
        LABELS.put(Biome.JAGGED_PEAKS, "尖った山頂");
        LABELS.put(Biome.FROZEN_RIVER, "凍った川");
        LABELS.put(Biome.FROZEN_OCEAN, "凍った海");
        LABELS.put(Biome.DEEP_FROZEN_OCEAN, "凍った深海");
        LABELS.put(Biome.LUSH_CAVES, "繁茂した洞窟");
        LABELS.put(Biome.DRIPSTONE_CAVES, "鍾乳洞");
        LABELS.put(Biome.DEEP_DARK, "ディープダーク");
    }

    private BiomeColors() {
    }

    private static void land(Biome biome, int grass, int foliage) {
        put(biome, grass, foliage, WATER, "land");
    }

    private static void land(Biome biome, int grass, int foliage, int water) {
        put(biome, grass, foliage, water, "land");
    }

    private static void put(Biome biome, int grass, int foliage, int water, String sort) {
        TINTS.put(biome, new Tint(grass, foliage, water, sort));
    }

    /** Whether the brush can paint the biome (an overworld one). */
    static boolean known(Biome biome) {
        return biome != null && TINTS.containsKey(biome);
    }

    static int rgb(Biome biome, Kind kind) {
        Tint tint = TINTS.get(biome);
        return tint == null ? 0x808080 : tint.of(kind);
    }

    /** Every biome the brush can paint, from the coldest to the warmest colour of the kind (by hue, then lightness), so a list of them reads as a band of colour. */
    static List<Biome> all(Kind kind) {
        List<Biome> list = new ArrayList<>(TINTS.keySet());
        list.sort(Comparator.comparingDouble((Biome biome) -> BlockColors.hueOf(rgb(biome, kind))).thenComparingInt(biome -> brightness(rgb(biome, kind))));
        return list;
    }

    private static int brightness(int rgb) {
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
    }

    /** The Japanese name of the biome (as the game shows it), else its id. */
    static String label(Biome biome) {
        return LABELS.getOrDefault(biome, name(biome));
    }

    /** What sort of biome it is: land, water (shores, rivers, oceans), snow (snow falls there) or cave. */
    static String sort(Biome biome) {
        Tint tint = TINTS.get(biome);
        return tint == null ? "" : switch (tint.sort()) {
            case "water" -> "水辺・海";
            case "snow" -> "雪 (雪が降り、水が凍る)";
            case "cave" -> "洞窟";
            default -> "陸";
        };
    }

    /** The name the game uses (minecraft:...), shorter: "snowy_plains". */
    static String name(Biome biome) {
        return biome.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The ramp from {@code from} to {@code to} with up to {@code steps} biomes between whose colours (of the kind) go in even steps from one end's to the other's. Only biomes of
     * the sorts of the two ends are used in between (no snowy biome in a gradient of green land unless an end is snowy: snow would fall there), and no colour twice in a row.
     */
    static List<Biome> ramp(Biome from, Biome to, int steps, Kind kind) {
        List<Biome> ramp = new ArrayList<>();
        ramp.add(from);
        int a = rgb(from, kind);
        int b = rgb(to, kind);
        String sortFrom = TINTS.containsKey(from) ? TINTS.get(from).sort() : "land";
        String sortTo = TINTS.containsKey(to) ? TINTS.get(to).sort() : "land";
        java.util.Set<Integer> used = new java.util.HashSet<>(List.of(a, b));
        double reached = 0.0;
        int count = Math.max(0, Math.min(14, steps));
        for (int index = 1; index <= count; index++) {
            int wanted = BlockColors.mix(a, b, index / (double) (count + 1));
            double wantedHue = BlockColors.hueOf(wanted);
            double wantedSaturation = BlockColors.saturationOf(wanted);
            Biome best = null;
            double bestDistance = Double.MAX_VALUE;
            double bestAlong = reached;
            for (Map.Entry<Biome, Tint> entry : TINTS.entrySet()) {
                String sort = entry.getValue().sort();
                if (!sort.equals(sortFrom) && !sort.equals(sortTo)) continue;
                int rgb = entry.getValue().of(kind);
                if (used.contains(rgb)) continue;
                // always a step on from the last one toward the other end (never back), and of the family of colour wanted
                double along = along(a, b, rgb);
                if (along <= reached || along >= 1.0) continue;
                if (wantedSaturation > 0.15 && BlockColors.saturationOf(rgb) > 0.12 && BlockColors.hueGapOf(wantedHue, BlockColors.hueOf(rgb)) > 40.0) continue;
                double d = BlockColors.colorDistance(rgb, wanted);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = entry.getKey();
                    bestAlong = along;
                }
            }
            if (best == null || bestDistance > BlockColors.colorDistance(a, b) / (count + 1) * 1.5) continue;
            ramp.add(best);
            used.add(rgb(best, kind));
            reached = bestAlong;
        }
        ramp.add(to);
        return ramp;
    }

    /** How far along the line from colour {@code a} to colour {@code b} the colour {@code c} lies (0 at a, 1 at b), as the RGB values go. */
    private static double along(int a, int b, int c) {
        double[] from = {(a >> 16) & 0xFF, (a >> 8) & 0xFF, a & 0xFF};
        double[] to = {(b >> 16) & 0xFF, (b >> 8) & 0xFF, b & 0xFF};
        double[] at = {(c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF};
        double dot = 0.0;
        double length = 0.0;
        for (int axis = 0; axis < 3; axis++) {
            dot += (at[axis] - from[axis]) * (to[axis] - from[axis]);
            length += (to[axis] - from[axis]) * (to[axis] - from[axis]);
        }
        return length <= 0.0 ? 0.0 : dot / length;
    }

    /** The biome at {@code t} (0 - 1) along the list, each taking a stretch as long as its weight. */
    static Biome pickAlong(List<BrushSettings.BiomeEntry> list, double t) {
        int total = 0;
        for (BrushSettings.BiomeEntry entry : list) total += Math.max(1, entry.weight);
        double target = Math.max(0.0, Math.min(0.9999, t)) * total;
        double sum = 0.0;
        for (BrushSettings.BiomeEntry entry : list) {
            sum += Math.max(1, entry.weight);
            if (target < sum) return entry.biome;
        }
        return list.get(list.size() - 1).biome;
    }
}
