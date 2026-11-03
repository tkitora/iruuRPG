package net.tkgon.mc.iruuRPG.tools.procedural.silhouette;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 部材パレット。値は Bukkit.createBlockData にそのまま渡せる block id。
 * leaves は「明るい(上・外側) → 暗い(下・内側)」の順に並べる。
 * wall が null の場合、細枝は fence にフォールバックする。
 */
public record Palette(String name, String log, String wood, String stairs, String slab,
                      String fence, String wall, String[] leaves, String hang, String hangTip) {

    public static final Map<String, Palette> ALL = new LinkedHashMap<>();

    static void reg(Palette p) { ALL.put(p.name(), p); }

    static {
        reg(new Palette("oak", "minecraft:oak_log", "minecraft:oak_wood",
                "minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:spruce_fence", "minecraft:mud_brick_wall",
                new String[]{"minecraft:azalea_leaves", "minecraft:oak_leaves", "minecraft:oak_leaves", "minecraft:dark_oak_leaves"},
                "minecraft:oak_leaves", null));
        reg(new Palette("dark_oak", "minecraft:dark_oak_log", "minecraft:dark_oak_wood",
                "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab", "minecraft:dark_oak_fence", "minecraft:polished_blackstone_wall",
                new String[]{"minecraft:oak_leaves", "minecraft:dark_oak_leaves", "minecraft:dark_oak_leaves", "minecraft:spruce_leaves"},
                "minecraft:dark_oak_leaves", null));
        reg(new Palette("birch", "minecraft:birch_log", "minecraft:birch_wood",
                "minecraft:diorite_stairs", "minecraft:diorite_slab", "minecraft:birch_fence", "minecraft:diorite_wall",
                new String[]{"minecraft:azalea_leaves", "minecraft:birch_leaves", "minecraft:birch_leaves", "minecraft:oak_leaves"},
                "minecraft:birch_leaves", null));
        reg(new Palette("pine", "minecraft:spruce_log", "minecraft:spruce_wood",
                "minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:spruce_fence", null,
                new String[]{"minecraft:azalea_leaves", "minecraft:oak_leaves", "minecraft:spruce_leaves"},
                "minecraft:spruce_leaves", null));
        reg(new Palette("willow", "minecraft:dark_oak_log", "minecraft:dark_oak_wood",
                "minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:dark_oak_fence", "minecraft:mud_brick_wall",
                new String[]{"minecraft:azalea_leaves", "minecraft:oak_leaves", "minecraft:jungle_leaves", "minecraft:dark_oak_leaves"},
                "minecraft:oak_leaves", "minecraft:azalea_leaves"));
        reg(new Palette("cherry", "minecraft:cherry_log", "minecraft:cherry_wood",
                "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab", "minecraft:dark_oak_fence", null,
                new String[]{"minecraft:pink_stained_glass", "minecraft:cherry_leaves", "minecraft:cherry_leaves", "minecraft:magenta_stained_glass"},
                "minecraft:cherry_leaves", null));
        reg(new Palette("wisteria", "minecraft:spruce_log", "minecraft:spruce_wood",
                "minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:dark_oak_fence", null,
                new String[]{"minecraft:pink_stained_glass", "minecraft:magenta_stained_glass", "minecraft:purple_stained_glass", "minecraft:amethyst_block"},
                "minecraft:purple_stained_glass_pane", "minecraft:amethyst_cluster[facing=down]"));
        reg(new Palette("azalea", "minecraft:oak_log", "minecraft:oak_wood",
                "minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:spruce_fence", "minecraft:mud_brick_wall",
                new String[]{"minecraft:flowering_azalea_leaves", "minecraft:azalea_leaves", "minecraft:azalea_leaves", "minecraft:oak_leaves"},
                "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves"));
    }

    public static Palette get(String name) { return ALL.get(name); }

    /**
     * (プロシージャルツール用) 記号のパレット: 部材を名前で出し ("log" "wood" "stairs" "slab" "fence" "wall" "leaf:0".."leaf:3" "hang" "hangtip")、
     * ツールが部位に置き換える。{@code walls}: 細枝に塀を使うか (形の既定のパレットに塀がなければフェンスだけ)。
     */
    public static Palette symbols(boolean walls) {
        return new Palette("symbols", "log", "wood", "stairs", "slab", "fence", walls ? "wall" : null,
                new String[]{"leaf:0", "leaf:1", "leaf:2", "leaf:3"}, "hang", "hangtip");
    }
}
