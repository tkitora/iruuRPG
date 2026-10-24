package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.Material;

import java.util.List;

/**
 * What the procedural tool makes. Every kind is turned into blocks of coloured wool by part (so the parts can be told apart and changed into real blocks by hand), shown
 * by the same preview, put in through the same edit (one //undo) and saved the same way; only the making of the blocks differs. New kinds are added here and in
 * {@link ProceduralShapes}.
 */
enum ProcKind {

    TREE("木", Material.OAK_SAPLING, "幹・枝・葉の木 (アルゴリズムと樹冠の形を選べる)", List.of(TreePart.TRUNK, TreePart.ROOT, TreePart.BRANCH, TreePart.TWIG, TreePart.LEAF)),
    ROCK("岩", Material.COBBLESTONE, "ごつごつした岩。数を増やすと、大小の岩のかたまり。層・苔つき", List.of(TreePart.STONE, TreePart.STONE_LIGHT, TreePart.STONE_DARK, TreePart.MOSS)),
    SPIKE("スパイク", Material.POINTED_DRIPSTONE, "地面から生える尖った柱 (石筍・棘・杭)。逆さにするとつらら", List.of(TreePart.STONE, TreePart.STONE_LIGHT, TreePart.STONE_DARK)),
    CRYSTAL("結晶", Material.AMETHYST_CLUSTER, "根元から外へ向かって生える結晶のかたまり", List.of(TreePart.CRYSTAL, TreePart.CRYSTAL_CORE, TreePart.STONE)),
    COLUMNS("柱状節理", Material.BASALT, "六角形の柱が並ぶ、玄武岩のような岩場", List.of(TreePart.STONE, TreePart.STONE_LIGHT, TreePart.STONE_DARK)),
    ARCH("アーチ", Material.STONE_BRICK_STAIRS, "二本の脚をつなぐ、自然の岩のアーチ", List.of(TreePart.STONE, TreePart.STONE_LIGHT, TreePart.STONE_DARK, TreePart.MOSS));

    private final String label;
    private final Material icon;
    private final String description;
    private final List<TreePart> parts;

    ProcKind(String label, Material icon, String description, List<TreePart> parts) {
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.parts = parts;
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

    List<TreePart> parts() {
        return parts;
    }

    boolean tree() {
        return this == TREE;
    }

    static ProcKind byId(String id) {
        for (ProcKind kind : values()) if (kind.name().equalsIgnoreCase(id)) return kind;
        return null;
    }

    /** Sets the numbers a fresh object of this kind starts from (the size, the count, how rough it is). */
    void applyDefaults(TreeSettings s) {
        switch (this) {
            case ROCK -> {
                s.set(TreeSettings.Key.HEIGHT, 6);
                s.set(TreeSettings.Key.WIDTH, 9);
                s.set(TreeSettings.Key.COUNT, 1);
                s.set(TreeSettings.Key.ROUGHNESS, 0.5);
                s.set(TreeSettings.Key.LAYERS, 3);
                s.set(TreeSettings.Key.MOSS, 0.3);
            }
            case SPIKE -> {
                s.set(TreeSettings.Key.HEIGHT, 12);
                s.set(TreeSettings.Key.WIDTH, 9);
                s.set(TreeSettings.Key.COUNT, 5);
                s.set(TreeSettings.Key.ROUGHNESS, 0.3);
                s.set(TreeSettings.Key.SPIKE_TAPER, 1.2);
                s.set(TreeSettings.Key.SPREAD, 0.4);
                s.set(TreeSettings.Key.LAYERS, 0);
            }
            case CRYSTAL -> {
                s.set(TreeSettings.Key.HEIGHT, 9);
                s.set(TreeSettings.Key.WIDTH, 9);
                s.set(TreeSettings.Key.COUNT, 7);
                s.set(TreeSettings.Key.ROUGHNESS, 0.2);
                s.set(TreeSettings.Key.SPREAD, 0.6);
            }
            case COLUMNS -> {
                s.set(TreeSettings.Key.HEIGHT, 8);
                s.set(TreeSettings.Key.WIDTH, 13);
                s.set(TreeSettings.Key.ROUGHNESS, 0.3);
                s.set(TreeSettings.Key.LAYERS, 0);
            }
            case ARCH -> {
                s.set(TreeSettings.Key.HEIGHT, 12);
                s.set(TreeSettings.Key.WIDTH, 16);
                s.set(TreeSettings.Key.ROUGHNESS, 0.4);
                s.set(TreeSettings.Key.LAYERS, 4);
                s.set(TreeSettings.Key.MOSS, 0.25);
            }
            default -> {
            }
        }
    }
}
