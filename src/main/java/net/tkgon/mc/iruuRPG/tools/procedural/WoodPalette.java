package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks an ornate tree can be shaded with, by the shape they come in (the table of what material comes in what shape: logs, stairs, slabs, fences, gates and
 * trapdoors of every wood; walls only of stone, so the wall stage of a branch is a stone near the wood's colour, mud bricks for oak), each with the average colour of
 * its texture as L*a*b*. {@link #pick} finds the block of a shape for a light level: the one nearest a colour made lighter or darker by the level.
 */
final class WoodPalette {

    /** The shapes the palette has blocks in. */
    enum Form {
        LOG, STAIRS, SLAB, FENCE, GATE, TRAPDOOR, WALL
    }

    /**
     * A block of the palette: its shape, its colour (L*, a*, b*; and the average colour of its texture), whether it is wood, and which of a log's looks it is (bark log,
     * bark wood, stripped log, stripped wood).
     */
    record Entry(Material material, Form form, double l, double a, double b, boolean wood, int look, int rgb) {
    }

    private static final Map<Form, List<Entry>> BY_FORM = new EnumMap<>(Form.class);
    private static final Map<Material, Entry> BY_MATERIAL = new EnumMap<>(Material.class);

    static {
        for (Form form : Form.values()) BY_FORM.put(form, new ArrayList<>());
        // the woods: the bark, the planks (stairs, slabs, fences, gates, trapdoors) and the stripped log, by the average colour of their textures
        wood("OAK", "_LOG", "_WOOD", 0x6b5130, 0xb8945f, 0xb29157);
        wood("SPRUCE", "_LOG", "_WOOD", 0x3a2713, 0x7a5a35, 0x74593a);
        wood("BIRCH", "_LOG", "_WOOD", 0xd7d5cb, 0xc8b77a, 0xc4b07b);
        wood("JUNGLE", "_LOG", "_WOOD", 0x584419, 0xb88764, 0xab8551);
        wood("ACACIA", "_LOG", "_WOOD", 0x676157, 0xba6337, 0xaf5c3b);
        wood("DARK_OAK", "_LOG", "_WOOD", 0x3c2e18, 0x4b3220, 0x60492d);
        wood("MANGROVE", "_LOG", "_WOOD", 0x5d3f2c, 0x765031, 0x783635);
        wood("CHERRY", "_LOG", "_WOOD", 0x3b2230, 0xe3b3a7, 0xd7918d);
        wood("CRIMSON", "_STEM", "_HYPHAE", 0x5c1a1e, 0x6a344a, 0x8a3a5a);
        wood("WARPED", "_STEM", "_HYPHAE", 0x3a3a4d, 0x2b6b64, 0x3a9796);
        planks("BAMBOO", 0xc9b247);
        // the stones: stairs, slabs, walls (and the fence of nether bricks)
        stone("MUD_BRICK", 0x89694f);
        stone("GRANITE", 0x956c58);
        stone("POLISHED_GRANITE", 0x9a6a58);
        stone("BRICK", 0x975b4b);
        stone("STONE", 0x7d7d7d);
        stone("COBBLESTONE", 0x7f7f7f);
        stone("MOSSY_COBBLESTONE", 0x6c7f66);
        stone("STONE_BRICK", 0x7a7a7a);
        stone("MOSSY_STONE_BRICK", 0x73806b);
        stone("ANDESITE", 0x888888);
        stone("POLISHED_ANDESITE", 0x848784);
        stone("DIORITE", 0xbdbdb8);
        stone("POLISHED_DIORITE", 0xc2c2bf);
        stone("COBBLED_DEEPSLATE", 0x4d4d52);
        stone("POLISHED_DEEPSLATE", 0x484849);
        stone("DEEPSLATE_BRICK", 0x464647);
        stone("DEEPSLATE_TILE", 0x363637);
        stone("BLACKSTONE", 0x2a2429);
        stone("POLISHED_BLACKSTONE", 0x353139);
        stone("POLISHED_BLACKSTONE_BRICK", 0x302c33);
        stone("SANDSTONE", 0xd9cd9f);
        stone("RED_SANDSTONE", 0xb5602a);
        stone("NETHER_BRICK", 0x2c161a);
        stone("RED_NETHER_BRICK", 0x450709);
        stone("END_STONE_BRICK", 0xdae0a2);
        stone("PRISMARINE", 0x63a396);
        stone("QUARTZ", 0xebe6df);
        stone("PURPUR", 0xa97ca9);
        add("NETHER_BRICK_FENCE", Form.FENCE, 0x2c161a, false, 0);
    }

    private WoodPalette() {
    }

    private static void wood(String name, String log, String wood, int bark, int planks, int stripped) {
        add(name + log, Form.LOG, bark, true, 0);
        add(name + wood, Form.LOG, bark, true, 1);
        add("STRIPPED_" + name + log, Form.LOG, stripped, true, 2);
        add("STRIPPED_" + name + wood, Form.LOG, stripped, true, 3);
        planks(name, planks);
    }

    private static void planks(String name, int rgb) {
        add(name + "_STAIRS", Form.STAIRS, rgb, true, 0);
        add(name + "_SLAB", Form.SLAB, rgb, true, 0);
        add(name + "_FENCE", Form.FENCE, rgb, true, 0);
        add(name + "_FENCE_GATE", Form.GATE, rgb, true, 0);
        add(name + "_TRAPDOOR", Form.TRAPDOOR, rgb, true, 0);
    }

    private static void stone(String name, int rgb) {
        add(name + "_STAIRS", Form.STAIRS, rgb, false, 0);
        add(name + "_SLAB", Form.SLAB, rgb, false, 0);
        add(name + "_WALL", Form.WALL, rgb, false, 0);
    }

    private static void add(String name, Form form, int rgb, boolean wood, int look) {
        Material material = Material.getMaterial(name);
        if (material == null) return;
        double[] lab = lab(rgb);
        Entry entry = new Entry(material, form, lab[0], lab[1], lab[2], wood, look, rgb);
        BY_FORM.get(form).add(entry);
        BY_MATERIAL.put(material, entry);
    }

    /** The palette's blocks of a shape, from the darkest to the lightest. */
    static List<Entry> of(Form form) {
        List<Entry> list = new ArrayList<>(BY_FORM.get(form));
        list.sort((a, b) -> Double.compare(a.l(), b.l()));
        return list;
    }

    /** The palette's entry for a block (null when it is not in it). */
    static Entry entry(Material material) {
        return material == null ? null : BY_MATERIAL.get(material);
    }

    /** The colour of a block as L*a*b* (null when it is not in the palette). */
    static double[] colour(Material material) {
        Entry entry = entry(material);
        return entry == null ? null : new double[]{entry.l(), entry.a(), entry.b()};
    }

    /**
     * The block of shape {@code form} for light {@code level} (-2 dark .. 2 light): the one nearest the colour {@code reference} with its lightness moved by
     * {@code level * step} (the hue weighs twice the lightness). Logs keep the look of {@code base} (bark or stripped, log or wood); walls are stone; the other shapes
     * keep to wood or stone as {@code base} is. With no reference, the reference is {@code base}'s own colour; {@code base} itself when it is not in the palette (or
     * nothing would move it: step 0 and no reference). When nothing of the shape is within {@link #FAR} of the reference's hue, {@code base} shaded round itself.
     */
    static Material pick(Form form, Material base, int level, double step, double[] reference) {
        Entry own = entry(base);
        double[] target = reference != null ? reference : own == null ? null : new double[]{own.l(), own.a(), own.b()};
        if (target == null) return base;
        if (step <= 0.0 && reference == null) return base;
        boolean wood = form == Form.WALL ? false : own != null ? own.wood() : true;
        int look = form == Form.LOG && own != null ? own.look() : 0;
        if (form != Form.WALL && own != null && own.form() != form) return base;
        double lightness = target[0] + level * Math.max(0.0, step);
        Entry best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entry entry : BY_FORM.get(form)) {
            if (entry.wood() != wood || entry.look() != look) continue;
            double score = sq(entry.l() - lightness) + 2.0 * (sq(entry.a() - target[1]) + sq(entry.b() - target[2]));
            if (score < bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        // (nothing of that shape near the colour, a grey bark among wood planks: the part's own block, shaded round itself)
        if (best == null || (reference != null && sq(best.a() - target[1]) + sq(best.b() - target[2]) > FAR * FAR)) {
            return reference != null ? pick(form, base, level, step, null) : base;
        }
        return best.material();
    }

    /** How far apart in hue (a*, b*) a block may be from the colour it is matched to. */
    static final double FAR = 15.0;

    /** sRGB to L*a*b* (D65). */
    static double[] lab(int rgb) {
        double r = linear(((rgb >> 16) & 0xFF) / 255.0);
        double g = linear(((rgb >> 8) & 0xFF) / 255.0);
        double b = linear((rgb & 0xFF) / 255.0);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        return new double[]{116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)};
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 216.0 / 24389.0 ? Math.cbrt(t) : (24389.0 / 27.0 * t + 16.0) / 116.0;
    }

    private static double sq(double value) {
        return value * value;
    }
}
