package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The colour of the building blocks, as the average colour of their texture (written from memory, close enough to rank blocks from light to dark),
 * and what is done with it: finding the block that looks like another one made darker ({@link #darker}), finding the full block a stair, slab or wall
 * is made of and the stair, slab or wall made of another block ({@link #baseOf}, {@link #variantData}).
 */
final class BlockColors {

    private static final Map<Material, Integer> RGB = new EnumMap<>(Material.class);
    private static final String[] DYES = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"};
    private static final int[] DYE_RGB = {0xe9ecec, 0xf07613, 0xbd44b3, 0x3aafd9, 0xf8c527, 0x70b919, 0xed8dac, 0x3e4447, 0x8e8e86, 0x158991, 0x792aac, 0x35399d, 0x724728, 0x546d1b, 0xa12722, 0x141519};
    private static final int[] TERRACOTTA_RGB = {0xd1b2a1, 0xa15325, 0x95586c, 0x716c89, 0xba8523, 0x677535, 0xa14e4e, 0x392a23, 0x876b62, 0x575b5b, 0x764656, 0x4a3b5b, 0x4d3323, 0x4c532a, 0x8f3d2e, 0x251610};

    static {
        for (int index = 0; index < DYES.length; index++) {
            put(DYES[index] + "_CONCRETE", DYE_RGB[index]);
            put(DYES[index] + "_WOOL", scale(DYE_RGB[index], 0.97));
            put(DYES[index] + "_TERRACOTTA", TERRACOTTA_RGB[index]);
        }
        put("TERRACOTTA", 0x985e43);
        // stone
        put("STONE", 0x7d7d7d); put("COBBLESTONE", 0x7f7f7f); put("MOSSY_COBBLESTONE", 0x6c7f66); put("STONE_BRICKS", 0x7a7a7a); put("MOSSY_STONE_BRICKS", 0x73806b);
        put("CRACKED_STONE_BRICKS", 0x767676); put("CHISELED_STONE_BRICKS", 0x787878); put("SMOOTH_STONE", 0x9e9e9e);
        put("ANDESITE", 0x888888); put("POLISHED_ANDESITE", 0x848784); put("DIORITE", 0xbdbdb8); put("POLISHED_DIORITE", 0xc2c2bf);
        put("GRANITE", 0x956c58); put("POLISHED_GRANITE", 0x9a6a58);
        put("DEEPSLATE", 0x4d4d51); put("COBBLED_DEEPSLATE", 0x4d4d52); put("POLISHED_DEEPSLATE", 0x484849); put("DEEPSLATE_BRICKS", 0x464647); put("DEEPSLATE_TILES", 0x363637);
        put("BLACKSTONE", 0x2a2429); put("POLISHED_BLACKSTONE", 0x353139); put("POLISHED_BLACKSTONE_BRICKS", 0x302c33);
        put("BASALT", 0x4a4a50); put("SMOOTH_BASALT", 0x48484d); put("TUFF", 0x6c6d66); put("CALCITE", 0xdfe0dc); put("DRIPSTONE_BLOCK", 0x866b5c);
        // sand and dirt
        put("SAND", 0xdbcfa3); put("SANDSTONE", 0xd9cd9f); put("SMOOTH_SANDSTONE", 0xdacfa4); put("CUT_SANDSTONE", 0xd8cc9f);
        put("RED_SAND", 0xa95821); put("RED_SANDSTONE", 0xb5602a); put("SMOOTH_RED_SANDSTONE", 0xb4602a); put("CUT_RED_SANDSTONE", 0xb4602b);
        put("DIRT", 0x866043); put("COARSE_DIRT", 0x77553a); put("PODZOL", 0x5b4023); put("GRASS_BLOCK", 0x7aa04a); put("MYCELIUM", 0x6f6265);
        put("MUD", 0x3c3a40); put("PACKED_MUD", 0x8a6a50); put("MUD_BRICKS", 0x89694f); put("ROOTED_DIRT", 0x90684c); put("CLAY", 0x9fa4b3); put("GRAVEL", 0x837f7e);
        // wood
        put("OAK_PLANKS", 0xb8945f); put("SPRUCE_PLANKS", 0x7a5a35); put("BIRCH_PLANKS", 0xc8b77a); put("JUNGLE_PLANKS", 0xb88764); put("ACACIA_PLANKS", 0xba6337);
        put("DARK_OAK_PLANKS", 0x4b3220); put("MANGROVE_PLANKS", 0x765031); put("CHERRY_PLANKS", 0xe3b3a7); put("BAMBOO_PLANKS", 0xc9b247);
        put("OAK_LOG", 0x6b5130); put("SPRUCE_LOG", 0x3a2713); put("BIRCH_LOG", 0xd7d5cb); put("JUNGLE_LOG", 0x584419); put("ACACIA_LOG", 0x676157);
        put("DARK_OAK_LOG", 0x3c2e18); put("MANGROVE_LOG", 0x5d3f2c); put("CHERRY_LOG", 0x3b2230);
        put("CRIMSON_PLANKS", 0x6a344a); put("WARPED_PLANKS", 0x2b6b64);
        // brick, quartz, prismarine, nether, ice, metals
        put("BRICKS", 0x975b4b); put("NETHER_BRICKS", 0x2c161a); put("RED_NETHER_BRICKS", 0x450709);
        put("QUARTZ_BLOCK", 0xebe6df); put("SMOOTH_QUARTZ", 0xece8e1); put("QUARTZ_BRICKS", 0xeae5de); put("PURPUR_BLOCK", 0xa97ca9);
        put("PRISMARINE", 0x63a396); put("PRISMARINE_BRICKS", 0x63ab9e); put("DARK_PRISMARINE", 0x335c4a);
        put("NETHERRACK", 0x6f3636); put("CRIMSON_NYLIUM", 0x80222b); put("WARPED_NYLIUM", 0x2b7466); put("SOUL_SAND", 0x514034); put("END_STONE", 0xdbdfa5); put("END_STONE_BRICKS", 0xdae0a2);
        put("SNOW_BLOCK", 0xf8fefe); put("PACKED_ICE", 0x8eb3f1); put("BLUE_ICE", 0x74a8fd); put("BONE_BLOCK", 0xe1ddc7);
        put("COPPER_BLOCK", 0xc06b4c); put("EXPOSED_COPPER", 0xa17e68); put("WEATHERED_COPPER", 0x6c996e); put("OXIDIZED_COPPER", 0x52a385);
        put("CUT_COPPER", 0xbf6b4f); put("EXPOSED_CUT_COPPER", 0x9d7f6a); put("WEATHERED_CUT_COPPER", 0x6d916f); put("OXIDIZED_CUT_COPPER", 0x4f9f88);
        put("IRON_BLOCK", 0xdcdcdc); put("GOLD_BLOCK", 0xf6d03e); put("DIAMOND_BLOCK", 0x62ede4); put("EMERALD_BLOCK", 0x2acb58); put("LAPIS_BLOCK", 0x1f43a4);
        put("COAL_BLOCK", 0x101010); put("REDSTONE_BLOCK", 0xaa1a09); put("OBSIDIAN", 0x0f0a18); put("MOSS_BLOCK", 0x596e2d);
        put("GLOWSTONE", 0xab8654); put("SEA_LANTERN", 0xacc7be); put("SHROOMLIGHT", 0xf19040);
    }

    private BlockColors() {
    }

    private static void put(String name, int rgb) {
        Material material = Material.getMaterial(name);
        if (material != null) RGB.put(material, rgb);
    }

    private static int scale(int rgb, double factor) {
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * factor);
        int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * factor);
        int b = (int) Math.min(255, (rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    static boolean known(Material material) {
        return RGB.containsKey(material);
    }

    static int rgb(Material material) {
        return RGB.getOrDefault(material, 0x808080);
    }

    static Set<Material> all() {
        return RGB.keySet();
    }

    // ---- stairs, slabs and walls ---------------------------------------------------------------------------------------------

    private static final String[] SUFFIXES = {"_STAIRS", "_SLAB", "_WALL"};

    /** The suffix of a stair, slab or wall material ("" for any other). */
    static String suffixOf(Material material) {
        String name = material.name();
        for (String suffix : SUFFIXES) if (name.endsWith(suffix)) return suffix;
        return "";
    }

    /** The full block the material is made of, when it is in the colour table (itself for a full block; null when it is not a building block we know). */
    static Material baseOf(Material material) {
        if (RGB.containsKey(material)) return material;
        String suffix = suffixOf(material);
        if (suffix.isEmpty()) return null;
        String name = material.name().substring(0, material.name().length() - suffix.length());
        for (String candidate : new String[]{name, name + "S", name + "_BLOCK", name + "_PLANKS", name + "_BRICKS"}) {
            Material found = Material.getMaterial(candidate);
            if (found != null && RGB.containsKey(found)) return found;
        }
        return null;
    }

    /** The stair, slab or wall ({@code suffix}) made of the full block {@code base}, or null when there is none (concrete has no stairs). */
    static Material variantOf(Material base, String suffix) {
        if (suffix.isEmpty()) return base;
        String name = base.name();
        List<String> stems = new ArrayList<>();
        stems.add(name);
        if (name.endsWith("_PLANKS")) stems.add(name.substring(0, name.length() - 7));
        if (name.endsWith("_BLOCK")) stems.add(name.substring(0, name.length() - 6));
        if (name.endsWith("S")) stems.add(name.substring(0, name.length() - 1));
        for (String stem : stems) {
            Material found = Material.getMaterial(stem + suffix);
            if (found != null) return found;
        }
        return null;
    }

    /** {@code original} with its shape (facing, half, type, ...) in the stair, slab or wall of {@code variant}; null when the data cannot be made. */
    static BlockData variantData(BlockData original, Material variant) {
        String text = original.getAsString();
        int bracket = text.indexOf('[');
        String properties = bracket >= 0 ? text.substring(bracket) : "";
        try {
            return org.bukkit.Bukkit.createBlockData(variant.getKey() + properties);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // ---- shading -----------------------------------------------------------------------------------------------------------

    /**
     * The block that looks like {@code from} made darker by {@code amount} (0 - 1), chosen among {@code allowed} (all the known building blocks when it is
     * empty). Colours are compared as perceived (the "redmean" distance); a coloured block keeps its hue (within 35 degrees) and a grey one stays grey, so a red
     * does not turn into a brown stone nor a stone into a log; a block of the same family (the same first word of the name, or the same ending such as
     * _PLANKS) is preferred. With a {@code suffix} (_STAIRS, _SLAB, _WALL) only blocks that have that shape are considered.
     */
    static Material darker(Material from, double amount, Set<Material> allowed, String suffix) {
        int source = rgb(from);
        int r = (int) (((source >> 16) & 0xFF) * (1.0 - amount));
        int g = (int) (((source >> 8) & 0xFF) * (1.0 - amount));
        int b = (int) ((source & 0xFF) * (1.0 - amount));
        double sourceHue = hue(source);
        double sourceSaturation = saturation(source);
        String firstWord = from.name().contains("_") ? from.name().substring(0, from.name().indexOf('_')) : from.name();
        String lastWord = from.name().contains("_") ? from.name().substring(from.name().lastIndexOf('_')) : "";
        boolean sourceDyed = dyed(from.name());
        Material best = from;
        double bestDistance = Double.MAX_VALUE;
        Iterable<Material> candidates = allowed == null || allowed.isEmpty() ? RGB.keySet() : allowed;
        for (Material candidate : candidates) {
            Integer rgb = RGB.get(candidate);
            if (rgb == null || candidate == from) continue;
            // only a darker block can be chosen
            if (brightness(rgb) >= brightness(source) - 5.0) continue;
            double saturation = saturation(rgb);
            if (sourceSaturation > 0.15) {
                if (saturation > 0.1 && hueGap(sourceHue, hue(rgb)) > 35.0) continue;
            } else if (saturation > 0.12) {
                continue;
            }
            if (!suffix.isEmpty() && variantOf(candidate, suffix) == null) continue;
            double d = distance(rgb, r, g, b);
            // a grey block for a coloured one (or the other way round) is the last choice
            if (sourceSaturation > 0.15 && saturation < 0.06) d *= 2.5;
            String name = candidate.name();
            // a dyed block (concrete, wool, terracotta) is not chosen for a natural one
            if (!sourceDyed && dyed(name)) d *= 3.0;
            if (name.startsWith(firstWord + "_") || name.equals(firstWord)) d *= 0.6;
            if (!lastWord.isEmpty() && name.endsWith(lastWord)) d *= 0.7;
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean dyed(String name) {
        return name.endsWith("_CONCRETE") || name.endsWith("_WOOL") || (name.endsWith("_TERRACOTTA") && !name.equals("TERRACOTTA"));
    }

    private static double brightness(int rgb) {
        return 0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF);
    }

    private static double distance(int rgb, int r2, int g2, int b2) {
        int r1 = (rgb >> 16) & 0xFF;
        int g1 = (rgb >> 8) & 0xFF;
        int b1 = rgb & 0xFF;
        double meanRed = (r1 + r2) / 2.0;
        double dr = r1 - r2;
        double dg = g1 - g2;
        double db = b1 - b2;
        return (2.0 + meanRed / 256.0) * dr * dr + 4.0 * dg * dg + (2.0 + (255.0 - meanRed) / 256.0) * db * db;
    }

    private static double hue(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        if (delta < 1.0E-6) return 0.0;
        double h;
        if (max == r) h = ((g - b) / delta) % 6.0;
        else if (max == g) h = (b - r) / delta + 2.0;
        else h = (r - g) / delta + 4.0;
        h *= 60.0;
        return h < 0 ? h + 360.0 : h;
    }

    private static double saturation(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        return max < 1.0E-6 ? 0.0 : (max - min) / max;
    }

    private static double hueGap(double a, double b) {
        double gap = Math.abs(a - b) % 360.0;
        return gap > 180.0 ? 360.0 - gap : gap;
    }

    static String describe(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }
}
