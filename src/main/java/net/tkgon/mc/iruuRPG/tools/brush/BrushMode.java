package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;

/** What the brush does where it is used. */
enum BrushMode {

    PAINT("塗る", Material.PAINTING, "模様 (パターン) のブロックで、範囲内の表面のブロックを置き換える"),
    AUTO_SHADE("自動陰影", Material.BLACK_DYE, "太陽の反対側や影になる面を、いまの色から自動で暗いブロックにする"),
    RAISE("盛る", Material.DIRT, "地面を盛り上げる (高さは強さ)"),
    LOWER("削る", Material.IRON_SHOVEL, "地面を削り下げる (高さは強さ)"),
    SMOOTH("なだらか", Material.SNOWBALL, "凸凹をならして滑らかにする"),
    FLATTEN("平らに", Material.SMOOTH_STONE_SLAB, "クリックした高さに地面をそろえる");

    private final String label;
    private final Material icon;
    private final String description;

    BrushMode(String label, Material icon, String description) {
        this.label = label;
        this.icon = icon;
        this.description = description;
    }

    String label() {
        return label;
    }

    Material icon() {
        return icon;
    }

    String description() {
        return description;
    }

    boolean terraform() {
        return this == RAISE || this == LOWER || this == SMOOTH || this == FLATTEN;
    }
}
