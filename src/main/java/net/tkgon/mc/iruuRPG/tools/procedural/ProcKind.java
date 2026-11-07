package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.Material;

import java.util.List;

/**
 * What the procedural tool makes. Every kind is turned into blocks by part (the block of each part chosen in the menu, see {@link TreePart}), shown
 * by the same preview, put in through the same edit (one //undo) and saved the same way; only the making of the blocks differs. New kinds are added here and in
 * {@link ProceduralShapes}.
 */
enum ProcKind {

    TREE("木", Material.OAK_SAPLING, "幹・枝・葉の木 (樹冠 / 段の枝 / ヤシ / 細い木 / 樹形 / 装飾の木 の6つのアルゴリズム)", List.of(TreePart.TRUNK, TreePart.BRANCH, TreePart.ROOT,
            TreePart.LEAF, TreePart.SNOW, TreePart.BRANCH_WALL, TreePart.BRANCH_FENCE, TreePart.BRANCH_SLAB, TreePart.BRANCH_STAIR, TreePart.BRANCH_TRAPDOOR, TreePart.BRANCH_GATE)),
    ROCK("岩", Material.COBBLESTONE, "ごつごつした岩。数を増やすと、大小の岩のかたまり。層・苔つき", List.of(TreePart.STONE, TreePart.STONE_LIGHT, TreePart.STONE_DARK, TreePart.MOSS)),
    SPIKE("スパイク", Material.POINTED_DRIPSTONE, "見ているブロックから、こちら(視点)へ向かって生える1本の尖った柱。角の数・ねじれ・筋・先の鋭さを調整できる", List.of(TreePart.STONE, TreePart.STONE_LIGHT, TreePart.STONE_DARK)),
    CRYSTAL("結晶", Material.AMETHYST_CLUSTER, "見ているブロックから、こちら(視点)へ向かって生える結晶。かたまり・大きな1本・薄い刃から作り方を選べる", List.of(TreePart.CRYSTAL, TreePart.CRYSTAL_CORE, TreePart.STONE)),
    WEB("クモの巣", Material.COBWEB, "ブロックをクリックして点を2つ以上選び、その間と周りの地形に合わせて巣を張る (円網・棚網・皿網・漏斗網・不規則網・条網)。空気のブロックにだけ置く", List.of(TreePart.WEB_FRAME, TreePart.WEB)),
    HOUSE("家の土台", Material.BRICKS, "家の骨組み (柱・壁・床・屋根) をウールで。幅・奥行き・階数・屋根の形を決めると、正面がこちら(視点)を向いて建つ。回転・ドーマー窓や塔や棟などの追加パーツ・おまかせ生成も", List.of(TreePart.HOUSE_PILLAR,
            TreePart.HOUSE_WALL, TreePart.HOUSE_FLOOR, TreePart.HOUSE_ROOF, TreePart.HOUSE_STONE, TreePart.HOUSE_BEAM, TreePart.HOUSE_WINDOW)),
    /** (t190) a plaza with houses round it, as the lobbies (central spawns) of servers are built. */
    LOBBY("ロビー (街並み)", Material.BELL, "中央の広場 (床の模様・噴水か台座・花壇) のまわりに、おまかせ生成の家を、広場を向けて高さ・大きさ・角度を少しずつ変えて並べる。家への道、すきまの木、地形へのなじませも", List.of(
            TreePart.LOBBY_PAVE, TreePart.LOBBY_PATH, TreePart.LOBBY_BED, TreePart.LOBBY_SOIL, TreePart.LOBBY_WATER, TreePart.HOUSE_PILLAR, TreePart.HOUSE_WALL,
            TreePart.HOUSE_FLOOR, TreePart.HOUSE_ROOF, TreePart.HOUSE_STONE, TreePart.HOUSE_BEAM, TreePart.HOUSE_WINDOW, TreePart.TRUNK, TreePart.LEAF)),
    /**
     * (t295) A building's outer shape for fantasy builds (hub build): plan, storeys, walls of three depths, roofs by the straight skeleton, the parts, and the
     * patterns that put them together and check them; in one block per role, the gradients and the fine decoration left to the hand.
     */
    BUILDING("建物 (外形)", Material.STONE_BRICKS, "土台の多角形・階の積み重ね・柱間に分けた壁面 (柱と梁が前、壁 -1、窓 -2)・屋根 (直線骨格、切妻〜尖塔、軒・破風・棟・隅棟の縁取り)・装飾パーツを、役割ごとの単色で。パターン A〜F で、検証して採点した建物を自動生成",
            List.of(TreePart.BLD_PLINTH, TreePart.BLD_POST, TreePart.BLD_BEAM, TreePart.BLD_INFILL, TreePart.BLD_WINDOW, TreePart.BLD_CORBEL, TreePart.BLD_FLOOR,
                    TreePart.BLD_ROOF, TreePart.BLD_ROOF_2, TreePart.BLD_ROOF_3, TreePart.BLD_EAVE, TreePart.BLD_VERGE, TreePart.BLD_RIDGE, TreePart.BLD_HIP,
                    TreePart.BLD_CHIMNEY, TreePart.BLD_RAIL, TreePart.BLD_FINIAL));

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
                s.set(TreeSettings.Key.WIDTH, 5);
                s.set(TreeSettings.Key.ROUGHNESS, 0.2);
                s.set(TreeSettings.Key.SPIKE_TAPER, 1.3);
                s.set(TreeSettings.Key.SPIKE_SIDES, 5);
                s.set(TreeSettings.Key.SPIKE_TWIST, 0.5);
                s.set(TreeSettings.Key.SPIKE_STAR, 0.25);
                s.set(TreeSettings.Key.CURVE, 0.0);
                s.set(TreeSettings.Key.LAYERS, 0);
            }
            case CRYSTAL -> {
                s.set(TreeSettings.Key.HEIGHT, 9);
                s.set(TreeSettings.Key.WIDTH, 7);
                s.set(TreeSettings.Key.COUNT, 6);
                s.set(TreeSettings.Key.ROUGHNESS, 0.15);
                s.set(TreeSettings.Key.SPREAD, 0.6);
                s.set(TreeSettings.Key.CRYSTAL_TIP, 0.3);
                s.set(TreeSettings.Key.CRYSTAL_FACETS, 6);
                s.set(TreeSettings.Key.CRYSTAL_THICK, 1.0);
                s.set(TreeSettings.Key.CRYSTAL_VARIANCE, 0.5);
            }
            case WEB -> {
                s.set(TreeSettings.Key.WEB_RINGS, 8);
                s.set(TreeSettings.Key.WEB_SPOKES, 12);
                s.set(TreeSettings.Key.WEB_SAG, 0.25);
                s.set(TreeSettings.Key.WEB_IRREG, 0.3);
                s.set(TreeSettings.Key.WEB_REACH, 5);
            }
            default -> {
            }
        }
    }

    /** Whether the object is made between points chosen one by one (a web) and not on the one block clicked. */
    boolean web() {
        return this == WEB;
    }
}
