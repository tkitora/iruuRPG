package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;

/** What the brush does where it is used. (Raising, lowering, smoothing and flattening the ground are left to WorldEdit.) */
enum BrushMode {

    PAINT("塗る", Material.PAINTING, "模様 (パターン / グラデーション) のブロックで、範囲内の表面のブロックを置き換える"),
    AUTO_SHADE("自動陰影", Material.BLACK_DYE, "太陽の反対側や影になる面を、いまの色から自動で暗いブロックにする"),
    BIOME("バイオーム", Material.GRASS_BLOCK, "ブロックではなくバイオームを塗る (草・葉・水の色)。「模様」のページでバイオームの一覧を作る。色の近い順に並べたグラデーションにもできる");

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
}
