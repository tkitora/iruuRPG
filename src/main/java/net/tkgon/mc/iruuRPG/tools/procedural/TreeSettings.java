package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings of one object: the numbers (kept in an array by {@link Key}, so the menu, the command and the yaml all read them the same way; on/off settings
 * are numbers 0 and 1), the kind, the algorithm of a tree, the seed and the blocks chosen for the parts. {@link #set} keeps every value in its range. The
 * height and width are the box of a rock, a spike or a crystal; a tree is as big as its own numbers make it.
 */
final class TreeSettings {

    /** The ways a tree is grown: canopy ({@link CanopyTree}), tiered ({@link TieredTree}), palm ({@link PalmTree}). */
    public static final List<String> ALGORITHMS = List.of("canopy", "tiered", "palm");

    /** A number setting: its name in the yaml and the command, the label of the menu, its range and the steps of a click and of a shift click. */
    public enum Key {
        HEIGHT("height", "高さ (最大)", 6, 80, 20, 1, 5, "木全体 (葉を含む) の高さの上限 (ブロック)"),
        WIDTH("width", "幅 (最大)", 4, 60, 20, 1, 5, "木全体 (葉を含む) の幅の上限 (ブロック。南北・東西とも)"),
        CURVE("curve", "曲がり", 0.0, 1.0, 0.35, 0.05, 0.2, "幹と枝のうねり・傾き (0 でまっすぐ)"),
        COUNT("count", "数", 1, 24, 3, 1, 4, "岩のかたまり・スパイク・結晶の数 (岩は 1 で1つだけ)"),
        ROUGHNESS("roughness", "荒さ", 0.0, 1.0, 0.5, 0.05, 0.2, "輪郭のでこぼこ (0 でなめらか、1 でごつごつ)"),
        LAYERS("layers", "層の数", 0, 10, 3, 1, 2, "横縞の層の数。層ごとに明るい岩・暗い岩が交互に入る (0 で層なし)"),
        MOSS("moss", "苔", 0.0, 1.0, 0.3, 0.05, 0.2, "上を向いた面に苔がつく量"),
        SPIKE_TAPER("spike-taper", "先の細さ", 0.4, 3.0, 1.2, 0.1, 0.5, "スパイク・結晶が先へ向かって細くなる度合い (大きいほど針のように尖る)"),
        SPREAD("spread", "傾きのばらつき", 0.0, 1.0, 0.4, 0.05, 0.2, "スパイク・結晶が外へ傾く度合い (0 でまっすぐ上)"),
        SPIKE_SIDES("spike-sides", "スパイクの角の数", 3, 8, 5, 1, 1, "断面の角の数 (3 = 三角、8 = ほぼ丸)。星形のくぼみと組み合わせると筋が出る"),
        SPIKE_TWIST("spike-twist", "スパイクのねじれ", -3.0, 3.0, 0.5, 0.1, 0.5, "先まででらせんに何回転ねじれるか (負で逆向き、0 でねじれなし)"),
        SPIKE_STAR("spike-star", "スパイクの筋の深さ", 0.0, 0.8, 0.25, 0.05, 0.2, "断面の角の間のくぼみ (0 で滑らかな柱、大きいと星形の鋭い筋)"),
        CRYSTAL_TIP("crystal-tip", "結晶の先の長さ", 0.1, 0.9, 0.3, 0.05, 0.1, "結晶の長さのうち、先が尖る部分の割合"),
        CRYSTAL_FACETS("crystal-facets", "結晶の面の数", 3, 8, 6, 1, 1, "結晶の断面の角の数 (6 で六角柱)"),
        CRYSTAL_THICK("crystal-thick", "結晶の太さ", 0.4, 2.5, 1.0, 0.1, 0.3, "結晶の太さの倍率"),
        CRYSTAL_VARIANCE("crystal-variance", "結晶の長さのばらつき", 0.0, 1.0, 0.5, 0.05, 0.2, "かたまりの結晶の長さのばらつき (0 でそろう)"),
        WEB_RINGS("web-rings", "糸の段数", 2, 24, 8, 1, 3, "円網のらせんの巻き数 / 棚網・皿網の格子の細かさ / 漏斗網の輪の数 / 不規則網の節の数 / 条網の横糸の間隔"),
        WEB_SPOKES("web-spokes", "放射糸の数", 4, 32, 12, 1, 4, "円網・漏斗網の放射状の糸の数。条網では、並べて張る糸の本数"),
        WEB_SAG("web-sag", "たるみ", 0.0, 1.0, 0.25, 0.05, 0.2, "糸が重みでたるむ度合い。皿網では皿の深さ"),
        WEB_IRREG("web-irregularity", "不規則さ", 0.0, 1.0, 0.3, 0.05, 0.2, "糸の位置・角度のばらつき (不規則網ではつながり方も)"),
        WEB_REACH("web-reach", "地形を探す距離", 1, 16, 5, 1, 3, "点のまわりでこの距離までの壁・地面・天井を探して、糸を固定する (ブロック)"),
        // ---- the house base (t179, ProceduralShapes#house): pillars, walls, floors and a roof, the front toward the player ----
        HOUSE_WIDTH("house-width", "幅 (正面)", 5, 40, 11, 1, 4, "正面の壁の長さ (ブロック)。正面は、クリックしたときのあなた(視点)の方を向く"),
        HOUSE_DEPTH("house-depth", "奥行き", 5, 40, 9, 1, 4, "正面から奥への長さ (ブロック)"),
        HOUSE_FLOORS("house-floors", "階数", 1, 6, 2, 1, 1, "何階建てか"),
        HOUSE_STOREY("house-storey", "1階の高さ", 3, 8, 4, 1, 1, "床から次の床までの高さ (ブロック。床の1段を含む)"),
        HOUSE_PILLARS("house-pillar-spacing", "柱の間隔", 0, 12, 4, 1, 2, "壁に柱を立てる間隔 (ブロック)。0 で四隅だけ"),
        HOUSE_ROOF("house-roof", "屋根の形", 0, 10, 1, 1, 1, "0 平ら / 1 切妻 / 2 寄棟 / 3 片流れ / 4 腰折れ / 5 マンサード / 6 入母屋 / 7 半切妻 / 8 反り屋根 / 9 尖り屋根 / 10 尖り寄棟 (9・10 は「屋根の段」で断面を決める)"),
        HOUSE_PITCH("house-pitch", "屋根の勾配", 0.25, 2.0, 0.75, 0.05, 0.25, "屋根が1ブロック内へ入るごとに上がる高さ (1 で 45 度)"),
        HOUSE_OVERHANG("house-overhang", "軒の出", 0, 3, 1, 1, 1, "屋根が壁から外へ出る長さ (ブロック)"),
        HOUSE_WINDOWS("house-windows", "窓", true, "柱と柱の間の壁に、階ごとに窓の穴をあける"),
        HOUSE_DOOR("house-door", "入口", true, "正面の1階のまん中に、入口の穴をあける"),
        HOUSE_FOUNDATION("house-foundation", "基礎", true, "地面が低い所では、柱を地面まで伸ばす"),
        // ---- (t181) the turn, the fantasy roofs and the storeys of a timber-framed house; the parts added on are the list of HouseModule ----
        HOUSE_RIDGE("house-ridge", "棟の向き", 0, 2, 0, 1, 1, "0 自動 (長い辺に沿う) / 1 正面と平行 (平入り) / 2 正面と直角 (妻入り: 正面に三角の破風)"),
        HOUSE_ROTATION("house-rotation", "回転", 0, 359, 0, 1, 5, "正面をこちらに向けたあと、さらに時計回りに回す角度 (度、上から見て)。1 度ずつ、359 の次は 0。90 の倍数以外は斜めに並ぶ"),
        HOUSE_JETTY("house-jetty", "上の階の張り出し", 0, 2, 0, 1, 1, "2階から上は、1階上がるごとに正面と裏へこれだけ張り出す (最大 3 ブロック)"),
        HOUSE_STONE("house-stone-base", "1階を石に", false, "1階の壁と柱を石の部位にする (基礎があれば、壁ごと地面まで)"),
        HOUSE_TIMBER("house-timber", "木組み", false, "石でない階の壁に、床の高さの梁と斜めの筋交いを柱の部位で入れる (ハーフティンバー)"),
        // ---- (t185) the roof of steps, and the random houses (HouseDesigner): the rules it makes a house by ----
        // ---- (t190) the depth and the layers of the walls and the roof: the techniques of the fantasy and lobby builds ----
        HOUSE_JETTY_SIDES("house-jetty-sides", "張り出しを4方向に", false, "オン: 上の階の張り出しを、正面・裏だけでなく左右にも出す"),
        HOUSE_BRACKETS("house-brackets", "持ち送り", false, "張り出した階・宙に出た棟の下に、斜めの支え (梁の部位) を入れる"),
        HOUSE_RECESS("house-recess", "壁を奥へ", false, "柱と梁の間の壁を 1 ブロック奥へ下げる (壁面の凹凸。骨組みが前に出る)"),
        HOUSE_GLASS("house-glass", "窓ガラス", false, "窓の穴に、壁の面から 1 ブロック奥へ窓の部位を入れる (奥まった窓)"),
        HOUSE_PLINTH("house-plinth", "石の基壇", 0, 3, 0, 1, 1, "1階の壁のまわりに、1 ブロック外へ出た石の段を、この高さまで (0 でなし)"),
        HOUSE_SKIRT("house-skirt", "階の間の庇", 0, 2, 0, 1, 1, "階と階の間に、壁から出る小さな屋根 (この長さ。張り出しのある面には付けない)"),
        HOUSE_RIDGE_BEAM("house-ridge-beam", "棟木", false, "屋根の棟に梁を通す。切妻の端では、破風の先へ突き出して先を上げる"),
        // ---- (t195) the foundations: the house raised on a stone podium with a terrace round it ----
        HOUSE_RAISE("house-raise", "土台の高さ", 0, 8, 0, 1, 2, "家の床を、この高さの石の土台の上に上げる。入口の前に、地面まで下りる階段"),
        HOUSE_TERRACE("house-terrace", "土台のテラス", 0, 4, 0, 1, 1, "土台を壁からこれだけ外へ広げたテラス (高い所の縁には低い手すり)。棟ごとの土台は、近い棟の高さになる"),
        HOUSE_PILLAR_MIRROR("house-pillar-mirror", "柱を両端から", false, "オン: 柱の間隔を壁の両端の角から数える (左右対称の柱・窓) / オフ: 左の角から数える"),
        HOUSE_STEP_FIT("house-roof-steps-fit", "段を大きさに合わせる", true, "オン: 段の列を、軒から棟までの長さに伸び縮みさせる / オフ: 棟から段の列のまま並べ、足りない軒側は最初の段を繰り返す"),
        HOUSE_GEN_PATTERN("house-gen-pattern", "パターン", 0, 9, 0, 1, 1, "どんな家を作るか: 0 おまかせ / 1 コテージ / 2 木組みの町家 / 3 L字の農家 / 4 館 / 5 塔の家 / 6 ハブの家 / 7 組み合わせの家 / 8 複合プランの家 / 9 物語の家"),
        HOUSE_GEN_SIZE("house-gen-size", "規模", 0, 2, 1, 1, 1, "0 小 / 1 中 / 2 大"),
        HOUSE_GEN_DETAIL("house-gen-detail", "パーツの多さ", 0, 3, 2, 1, 1, "追加パーツをいくつ付けるか (0 で 2 個ほど、3 で 10 個ほど)"),
        HOUSE_GEN_SYMMETRY("house-gen-symmetry", "対称", 0, 2, 0, 1, 1, "0 パターン次第 / 1 左右対称 / 2 崩す (非対称)"),
        HOUSE_GEN_TOWERS("house-gen-towers", "塔", 0, 3, 0, 1, 1, "0 パターン次第 / 1 なし / 2 少し / 3 多め"),
        HOUSE_GEN_OVERLAP("house-gen-overlap", "重なりの許容", 0, 2, 1, 1, 1, "パーツ同士が重なるのを、どこまで許すか: 0 なし / 1 少し / 2 多め"),
        HOUSE_GEN_SHAPE("house-gen-shape", "形もおまかせ", true, "オン: 幅・奥行き・階数・1階の高さ・柱の間隔もパターンから決める (幅と奥行きは奇数: 棟が 1 列) / オフ: いまの形のまま (t193)"),
        HOUSE_GEN_ROOF("house-gen-roof", "屋根もおまかせ", true, "オン: 屋根の形・勾配・段もパターンから決める / オフ: いまの屋根のまま"),
        HOUSE_GEN_LOOK("house-gen-look", "外観もおまかせ", true, "オン: 石の1階・木組み・張り出しもパターンから決める / オフ: いまのまま"),
        // ---- (t190) the lobby (LobbyShape): a plaza, houses round it, paths, beds, trees ----
        LOBBY_RADIUS("lobby-radius", "広場の半径", 4, 20, 10, 1, 3, "中央の広場の大きさ (ブロック)"),
        LOBBY_SHAPE("lobby-shape", "広場の形", 0, 2, 0, 1, 1, "0 丸 / 1 八角 / 2 四角"),
        LOBBY_HOUSES("lobby-houses", "家の数", 2, 12, 6, 1, 2, "広場のまわりに建てる家の数 (まわりに入りきらない分は建てない)"),
        LOBBY_GAP("lobby-gap", "広場から家まで", 1, 8, 3, 1, 2, "広場の縁から家の正面までのあき (ブロック)"),
        LOBBY_ORGANIC("lobby-organic", "ばらつき", 0.0, 1.0, 0.5, 0.05, 0.25, "家の大きさ・広場からの距離・向き・間隔をどれだけ不ぞろいにするか (0 で整列、1 で自然な街並み)"),
        LOBBY_CENTER("lobby-center", "広場の中央", 0, 2, 1, 1, 1, "0 なし / 1 噴水 / 2 台座"),
        LOBBY_BEDS("lobby-beds", "花壇の数", 0, 12, 4, 1, 2, "広場の縁に置く、一段高い花壇の数 (道の上には置かない)"),
        LOBBY_TREES("lobby-trees", "木の数", 0, 16, 6, 1, 2, "家と家のすきまやまわりに植える木の数 (丸い木と針葉樹。家に近すぎる所には植えない)"),
        LOBBY_PATHS("lobby-paths", "家への道", true, "広場から各家の入口まで道を通す"),
        LOBBY_TERRAIN("lobby-terrain", "地形になじませる", true, "広場・道の下を地面まで埋め、家の基礎を地面まで伸ばす。木は地面に植える"),
        // ---- the canopy tree (CanopyTree): a fractal of branches, canopies one above the other ----
        CANOPY_TRUNK_HEIGHT("canopy-trunk-height", "幹の高さ", 0, 40, 0, 1, 5, "最初の枝分かれまでの、まっすぐな幹の長さ (0 で根元からすぐ分かれる)"),
        CANOPY_LENGTH("canopy-length", "最初の長さ", 1, 40, 10, 0.5, 2, "樹冠の最初の枝 (主幹) の長さ。子の枝は、長さの倍率ずつ短くなる"),
        CANOPY_LENGTH_MULT("canopy-length-mult", "長さの倍率", 0.1, 1.0, 0.6, 0.05, 0.1, "子の枝の長さ = 親 × この値 (樹冠の間の幹・上の樹冠の大きさにもかかる)"),
        CANOPY_LEVELS("canopy-levels", "樹冠の数", 1, 4, 1, 1, 1, "樹冠を上へいくつ重ねるか (2 以上で、樹冠の間に幹が伸びる)"),
        CANOPY_LEVEL_DIST("canopy-level-dist", "樹冠の間隔", 0, 50, 0, 1, 5, "樹冠と樹冠の間の幹の長さに足す量 (基本 20)"),
        CANOPY_UPPER_SCALE("canopy-upper-scale", "上の樹冠の大きさ", 0.1, 3.0, 1.0, 0.05, 0.25, "2つ目より上の樹冠の、長さと太さの倍率"),
        CANOPY_RADIUS("canopy-radius", "最初の太さ", 0.1, 8.0, 2.0, 0.1, 0.5, "根元の幹の半径 (ブロック)"),
        CANOPY_RADIUS_MULT("canopy-radius-mult", "太さの倍率", 0.1, 1.0, 0.65, 0.05, 0.1, "子の枝の太さ = 親 × この値 (枝の中でも、根元から先へこの割合まで細る)"),
        CANOPY_DEPTH("canopy-depth", "枝分かれの深さ", 1, 6, 4, 1, 1, "枝が分かれる回数。一番深い枝の先に葉の塊ができる"),
        CANOPY_BRANCHES("canopy-branches", "分岐数", 1, 5, 4, 1, 1, "1つの分かれ目から出る枝の数"),
        CANOPY_SPREAD("canopy-spread", "広がりの角度", 0, 90, 45, 5, 15, "子の枝が、親の向きから開く角度 (度)"),
        CANOPY_LEAF_SIZE("canopy-leaf-size", "葉の大きさ", 1.0, 8.0, 1.5, 0.25, 1, "枝先の葉の塊の半径"),
        CANOPY_LEAF_START("canopy-leaf-start", "葉の付く深さ", 0, 3, 1, 1, 1, "この深さ以下 (先のほう) の枝には、途中にも3ブロックごとに葉が付く"),
        CANOPY_STAGGER("canopy-stagger", "枝のずれ", 0.0, 0.5, 0.0, 0.05, 0.1, "子の枝が出る位置を、枝の先から根元側へばらつかせる量 (長さに対する割合)"),
        CANOPY_TWIST("canopy-twist", "幹のねじれ", 0.0, 10.0, 0.0, 0.25, 1, "幹と最初の樹冠の主幹を、ランダムに曲げる強さ"),
        CANOPY_UPPER_TWIST("canopy-upper-twist", "上の幹のねじれ", 0.0, 1.0, 0.0, 0.05, 0.2, "樹冠と樹冠の間の幹を、ランダムに曲げる強さ"),
        CANOPY_CENTERING("canopy-centering", "中心へ戻す力", 0.0, 1.0, 0.0, 0.05, 0.2, "樹冠の間の幹が、根元の真上へ戻ろうとする強さ"),
        CANOPY_ROOT_COUNT("canopy-root-count", "根の数", 2, 8, 4, 1, 1, "根元から張り出す根の数"),
        CANOPY_ROOT_LENGTH("canopy-root-length", "根の長さ", 1.0, 15.0, 5.0, 0.5, 2, "根が外へ伸びる長さ"),
        CANOPY_ROOT_HEIGHT("canopy-root-height", "根の付け根の高さ", 0.5, 6.0, 2.0, 0.25, 1, "根が幹から出る高さ"),
        CANOPY_ROOT_SCALE("canopy-root-scale", "根の大きさ", 0.5, 5.0, 1.0, 0.1, 0.5, "根の長さ・高さ・太さの倍率"),
        CANOPY_GRAVITY("canopy-gravity", "重力", -1.0, 1.0, 0.0, 0.05, 0.2, "枝が伸びるほど下へ垂れる強さ (負で上へ反る)"),
        CANOPY_WIND("canopy-wind", "風の強さ", 0.0, 1.5, 0.0, 0.05, 0.25, "枝と幹が風下へ流れる強さ"),
        CANOPY_WIND_DIR("canopy-wind-dir", "風の向き", 0, 360, 0, 15, 45, "風が吹いていく向き (度。0 = 東、90 = 南)"),
        CANOPY_ELLIPSOID("canopy-ellipsoid", "葉を平たく", false, "葉の塊を、上下につぶれた楕円体にする"),
        CANOPY_WILLOW("canopy-willow", "柳のつる", false, "枝先の葉の塊から、葉のつるを垂らす"),
        CANOPY_WILLOW_SCALE("canopy-willow-scale", "つるの量", 0.1, 5.0, 1.0, 0.1, 0.5, "柳のつるの本数と長さの倍率"),
        CANOPY_BETA_LEAVES("canopy-beta-leaves", "ベータ版の葉", false, "Beta 1.7.3 のように、丸太から4ブロックより遠い葉を消す"),
        // ---- the tiered tree (TieredTree): a trunk with tiers of branches (conifers, snow) ----
        TIER_HEIGHT("tier-height", "木の高さ", 4, 200, 30, 1, 5, "幹の高さ (ブロック)。てっぺんに、葉の塊が5つ重なる"),
        TIER_STUMP("tier-stump", "枝のない幹", 0, 50, 8, 1, 5, "この高さまでは枝が出ない (ねじれも強め)"),
        TIER_RADIUS("tier-radius", "幹の太さ", 0.1, 8.0, 1.5, 0.1, 0.5, "根元の幹の半径 (上へ向かって細る)"),
        TIER_TWIST("tier-twist", "幹のねじれ", 0.0, 10.0, 0.0, 0.25, 1, "幹がらせんを描く強さ (0 でまっすぐ)"),
        TIER_BRAIDED("tier-braided", "編み込みの幹", false, "ねじれがあるとき、2本の幹がからみ合う"),
        TIER_BRAID_INTENSITY("tier-braid-intensity", "編み込みの強さ", 0.0, 2.0, 1.0, 0.05, 0.25, "編み込みの2本の幹が離れる量の倍率"),
        TIER_BRAID_WOBBLE("tier-braid-wobble", "枝も揺らす", true, "編み込みのとき、枝の付け根も幹のらせんに沿わせる (オフで中心から出る)"),
        TIER_ROOT_FLARE("tier-root-flare", "根の張り", 0.0, 10.0, 2.0, 0.25, 1, "根元から斜めに張り出す根の長さ (0.5 以下で根なし)"),
        TIER_SPACING("tier-spacing", "段の間隔", 1, 10, 4, 1, 1, "枝の段と段の間 (ブロック)"),
        TIER_BRANCHES("tier-branches", "1段の枝の数", 1, 21, 5, 1, 3, "1つの段から出る枝の数"),
        TIER_STAGGER("tier-stagger", "枝の高さのずれ", 0.0, 1.0, 0.7, 0.05, 0.25, "1つの段の枝の付け根を、段の間隔のこの割合の幅で上下にずらす (向きも少し崩す)。0 で全部同じ高さ (t242)"),
        TIER_BRANCH_LENGTH("tier-branch-length", "枝の長さ", 0.5, 50.0, 10.0, 0.5, 2, "一番下の段の枝の長さ (上の段ほど短い)"),
        TIER_SAG("tier-sag", "枝の垂れ", 0.0, 2.0, 0.0, 0.05, 0.25, "枝先が下へ垂れる量 (雪があるとさらに垂れる)"),
        TIER_LEAF_SIZE("tier-leaf-size", "葉の大きさ", 0.5, 8.0, 2.0, 0.25, 1, "枝に沿って付ける葉の塊の半径"),
        TIER_LEAF_DENSITY("tier-leaf-density", "葉の密度 (%)", 10, 100, 65, 5, 15, "葉の塊の中を、葉で埋める割合"),
        TIER_WILLOW("tier-willow", "柳のつる", false, "枝の外側の葉から、葉のつるを垂らす"),
        TIER_WILLOW_SCALE("tier-willow-scale", "つるの量", 0.1, 5.0, 1.0, 0.1, 0.5, "柳のつるの数と長さの倍率"),
        TIER_SNOW("tier-snow", "雪", false, "葉の上側に雪を積もらせる (雪の部位のブロック)"),
        TIER_SNOW_AMOUNT("tier-snow-amount", "雪の量", 0.0, 1.0, 0.5, 0.05, 0.2, "雪の多さ (枝の垂れも増える)"),
        TIER_WIND("tier-wind", "風の強さ", 0.0, 2.0, 0.0, 0.05, 0.25, "上へ行くほど幹が風下へ曲がる強さ"),
        TIER_WIND_DIR("tier-wind-dir", "風の向き", 0, 360, 0, 15, 45, "風が吹いていく向き (度。0 = 東、90 = 南)"),
        // ---- the palm (PalmTree): an S-curved trunk, a crown of fronds ----
        PALM_HEIGHT("palm-height", "幹の高さ", 5, 50, 16, 1, 5, "幹の高さ (ブロック)"),
        PALM_RADIUS("palm-radius", "幹の太さ", 0.5, 4.0, 1.25, 0.05, 0.25, "根元の幹の半径 (先は 0.6 まで細る)"),
        PALM_ROOT_REACH("palm-root-reach", "支柱の根", 0.0, 3.0, 0.0, 0.1, 0.5, "幹の下から、支柱のような根を張る広さ (0 で根なし)"),
        PALM_S_CURVE("palm-s-curve", "S字の曲がり", 0.0, 4.0, 0.75, 0.05, 0.25, "幹がS字に曲がる強さ"),
        PALM_SPLIT_CHANCE("palm-split-chance", "二股の確率", 0.0, 1.0, 0.0, 0.05, 0.25, "幹が途中で2本に分かれる確率 (1 で必ず)"),
        PALM_SPLIT_DIST("palm-split-dist", "二股の開き", 0.2, 3.0, 1.5, 0.1, 0.5, "分かれた2本の幹が離れる強さ"),
        PALM_LAYERS("palm-layers", "葉の層の数", 1, 5, 3, 1, 1, "てっぺんの葉の、層の数 (下の層ほど水平で、よく垂れる)"),
        PALM_FRONDS("palm-fronds", "1層の葉の数", 3, 16, 8, 1, 2, "1つの層の葉の数"),
        PALM_FROND_LENGTH("palm-frond-length", "葉の長さ", 3.0, 25.0, 7.5, 0.5, 2, "1枚の葉の長さ"),
        PALM_FROND_THICK("palm-frond-thick", "葉の厚み", 0.5, 4.0, 1.0, 0.1, 0.5, "葉の付け根の太さ (先へ細る)"),
        PALM_DROOP("palm-droop", "葉の垂れ", 0.0, 4.0, 1.5, 0.1, 0.5, "葉が先へ向かって垂れる強さ"),
        PALM_PITCH("palm-pitch", "葉の上向きの角度", 0, 90, 45, 5, 15, "一番上の層の葉が出る角度 (度。下の層ほど 18 度ずつ下がる)");

        private final String id;
        private final String label;
        private final double min;
        private final double max;
        private final double initial;
        private final double step;
        private final double bigStep;
        private final String description;
        private final boolean toggle;

        Key(String id, String label, double min, double max, double initial, double step, double bigStep, String description) {
            this(id, label, min, max, initial, step, bigStep, description, false);
        }

        /** A setting that is on or off (1 or 0): a click turns it over. */
        Key(String id, String label, boolean on, String description) {
            this(id, label, 0, 1, on ? 1 : 0, 1, 1, description, true);
        }

        private Key(String id, String label, double min, double max, double initial, double step, double bigStep, String description, boolean toggle) {
            this.id = id;
            this.label = label;
            this.min = min;
            this.max = max;
            this.initial = initial;
            this.step = step;
            this.bigStep = bigStep;
            this.description = description;
            this.toggle = toggle;
        }

        /** The value a fresh setting starts from. */
        public double initial() {
            return initial;
        }

        /** An on/off setting (shown as オン / オフ). */
        public boolean toggle() {
            return toggle;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public double min() {
            return min;
        }

        public double max() {
            return max;
        }

        public double step() {
            return step;
        }

        public double bigStep() {
            return bigStep;
        }

        public String description() {
            return description;
        }

        public boolean whole() {
            return step >= 1.0;
        }

        public static Key byId(String id) {
            for (Key key : values()) if (key.id.equalsIgnoreCase(id)) return key;
            return null;
        }
    }

    private final double[] values = new double[Key.values().length];

    {
        for (Key key : Key.values()) values[key.ordinal()] = key.initial;
    }
    private ProcKind kind = ProcKind.TREE;
    private String crystalType = "cluster";
    private String webShape = "orb";
    private String algorithm = "canopy";
    /** 0 = a new random tree each time. */
    private long seed;
    private String name = "tree";
    /** The blocks chosen for the parts (the name of the material); a part not here takes the block of the config. */
    private final Map<TreePart, String> blocks = new EnumMap<>(TreePart.class);
    /** The leaf gradient, from the bottom of a clump to its top (the names of the materials); empty: the leaves are the one block of the config. */
    private final List<String> leafGradient = new ArrayList<>();
    /** The parts added on the house base (t181), in the order they are built (a later one goes over an earlier one). */
    private final List<HouseModule> modules = new ArrayList<>();

    /** How many parts a house can have. */
    static final int MAX_MODULES = 12;
    /** (t185) The section of a roof of steps (9, 10): how many blocks each column shows, from the eaves to the ridge. */
    private final List<Integer> roofSteps = new ArrayList<>(DEFAULT_STEPS);
    /** (t185) The number of the random house these settings were made as (0: none); shown, and given back to make it again. */
    private long designSeed;

    /** The steps a roof of steps starts with: nekorozv's "1 1 1 2 3 3 3 2 1 1 1", half of it (the middle is the ridge). */
    static final List<Integer> DEFAULT_STEPS = List.of(1, 1, 1, 2, 3, 3);
    static final int MAX_STEPS = 12;
    static final int MAX_STEP = 8;

    public TreeSettings copy() {
        TreeSettings copy = new TreeSettings();
        System.arraycopy(values, 0, copy.values, 0, values.length);
        copy.kind = kind;
        copy.crystalType = crystalType;
        copy.webShape = webShape;
        copy.algorithm = algorithm;
        copy.seed = seed;
        copy.name = name;
        copy.blocks.putAll(blocks);
        copy.leafGradient.addAll(leafGradient);
        // (the generation reads the copy off the main thread while the menu may change the parts: each is copied)
        for (HouseModule module : modules) copy.modules.add(module.copy());
        copy.roofSteps.clear();
        copy.roofSteps.addAll(roofSteps);
        copy.designSeed = designSeed;
        return copy;
    }

    /** The steps of a roof of steps, from the eaves to the ridge (a copy). */
    List<Integer> roofSteps() {
        return List.copyOf(roofSteps);
    }

    /** Sets the steps (each 0 to {@link #MAX_STEP}, at most {@link #MAX_STEPS}; none: the default). */
    void setRoofSteps(List<Integer> steps) {
        roofSteps.clear();
        for (Integer step : steps) {
            if (step != null && roofSteps.size() < MAX_STEPS) roofSteps.add(Math.max(0, Math.min(MAX_STEP, step)));
        }
        if (roofSteps.isEmpty()) roofSteps.addAll(DEFAULT_STEPS);
    }

    /**
     * The steps written as numbers ({@code "1 1 1 2 3 3"}, or with commas); a whole section (the same both ways, 5 numbers or more: "1 1 1 2 3 3 3 2 1 1 1")
     * gives its half from the eaves to the middle. Null when it is not numbers.
     */
    static List<Integer> parseSteps(String text) {
        if (text == null || text.isBlank()) return null;
        List<Integer> steps = new ArrayList<>();
        for (String word : text.trim().split("[\\s,]+")) {
            try {
                steps.add(Integer.parseInt(word));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        boolean mirrored = steps.size() >= 5;
        for (int index = 0; mirrored && index < steps.size() / 2; index++) mirrored = steps.get(index).equals(steps.get(steps.size() - 1 - index));
        return mirrored ? new ArrayList<>(steps.subList(0, (steps.size() + 1) / 2)) : steps;
    }

    long designSeed() {
        return designSeed;
    }

    void setDesignSeed(long seed) {
        designSeed = Math.max(0L, seed);
    }

    /** The block chosen for {@code part} (a material name), or null when the config decides. */
    String block(TreePart part) {
        return blocks.get(part);
    }

    /** Chooses the block of {@code part}; null (or a name that is not a block) goes back to the config's. */
    void setBlock(TreePart part, String material) {
        String name = blockName(material);
        if (name == null) blocks.remove(part);
        else blocks.put(part, name);
    }

    /** The parts added on the house, the list itself (the menu changes it in place). */
    List<HouseModule> modules() {
        return modules;
    }

    List<String> leafGradient() {
        return List.copyOf(leafGradient);
    }

    /** How many shades the leaves have (1 when there is no gradient). */
    int leafShades() {
        return Math.max(1, leafGradient.size());
    }

    /** Sets shade {@code index} of the gradient (an index one past the end adds a shade); a name that is not a block is ignored. */
    void setLeafShade(int index, String material) {
        String name = blockName(material);
        if (name == null || index < 0 || index > leafGradient.size() || index >= TreePart.SHADES) return;
        if (index == leafGradient.size()) leafGradient.add(name);
        else leafGradient.set(index, name);
    }

    void removeLeafShade(int index) {
        if (index >= 0 && index < leafGradient.size()) leafGradient.remove(index);
    }

    /** Replaces the whole gradient (the names that are not blocks are left out; at most {@link TreePart#SHADES}). */
    void setLeafGradient(List<String> materials) {
        leafGradient.clear();
        for (String material : materials) {
            String name = blockName(material);
            if (name != null && leafGradient.size() < TreePart.SHADES) leafGradient.add(name);
        }
    }

    /**
     * The name of a block that can be placed (not air), made regular: a material ({@code OAK_LOG}), or a block with states
     * ({@code minecraft:oak_log[axis=x]}, the states as the game writes them); null when it is none. The states are checked when the block is made.
     */
    static String blockName(String material) {
        if (material == null) return null;
        String text = material.trim();
        String states = "";
        int bracket = text.indexOf('[');
        if (bracket >= 0) {
            if (!text.endsWith("]") || bracket == 0) return null;
            states = text.substring(bracket).toLowerCase(Locale.ROOT).replace(" ", "");
            text = text.substring(0, bracket);
        }
        org.bukkit.Material type = org.bukkit.Material.matchMaterial(text);
        if (type == null || !type.isBlock() || type.isAir()) return null;
        return states.isEmpty() || states.equals("[]") ? type.name() : type.getKey() + states;
    }

    /** The material of a block name (states left out), or null. */
    static org.bukkit.Material materialOf(String name) {
        if (name == null) return null;
        int bracket = name.indexOf('[');
        return org.bukkit.Material.matchMaterial(bracket >= 0 ? name.substring(0, bracket) : name);
    }

    public double get(Key key) {
        return values[key.ordinal()];
    }

    public int getInt(Key key) {
        return (int) Math.round(values[key.ordinal()]);
    }

    public void set(Key key, double value) {
        double clamped = Math.max(key.min, Math.min(key.max, value));
        values[key.ordinal()] = key.whole() ? Math.round(clamped) : Math.round(clamped * 1000.0) / 1000.0;
    }

    public void adjust(Key key, double delta) {
        // (the turn of a house goes round: one down from 0 is 359)
        if (key == Key.HOUSE_ROTATION) {
            set(key, Math.floorMod((int) Math.round(get(key) + delta), 360));
            return;
        }
        set(key, get(key) + delta);
    }

    public int height() {
        return getInt(Key.HEIGHT);
    }

    public int width() {
        return getInt(Key.WIDTH);
    }

    ProcKind kind() {
        return kind;
    }

    /** Changes what is made, and sets the numbers a fresh object of that kind starts from. */
    void setKind(ProcKind kind) {
        if (kind == null || kind == this.kind) return;
        this.kind = kind;
        kind.applyDefaults(this);
    }

    /** The way crystals are made: cluster (several round a foot), prism (one large faceted crystal), blades (thin flat shards). */
    static final List<String> CRYSTAL_TYPES = List.of("cluster", "prism", "blades");

    String crystalType() {
        return crystalType;
    }

    void setCrystalType(String type) {
        this.crystalType = CRYSTAL_TYPES.contains(type) ? type : "cluster";
    }

    void cycleCrystalType(boolean forward) {
        setCrystalType(CRYSTAL_TYPES.get(Math.floorMod(CRYSTAL_TYPES.indexOf(crystalType) + (forward ? 1 : -1), CRYSTAL_TYPES.size())));
    }

    /** The kinds of web: orb (円網), sheet (棚網), bowl (皿網), funnel (漏斗網), irregular (不規則網), strand (条網). */
    static final List<String> WEB_SHAPES = List.of("orb", "sheet", "bowl", "funnel", "irregular", "strand");

    String webShape() {
        return webShape;
    }

    void setWebShape(String shape) {
        this.webShape = WEB_SHAPES.contains(shape) ? shape : "orb";
    }

    void cycleWebShape(boolean forward) {
        setWebShape(WEB_SHAPES.get(Math.floorMod(WEB_SHAPES.indexOf(webShape) + (forward ? 1 : -1), WEB_SHAPES.size())));
    }

    public String algorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = ALGORITHMS.contains(algorithm) ? algorithm : "canopy";
    }

    public void cycleAlgorithm(boolean forward) {
        setAlgorithm(ALGORITHMS.get(Math.floorMod(ALGORITHMS.indexOf(algorithm) + (forward ? 1 : -1), ALGORITHMS.size())));
    }

    /** Whether an on/off setting is on. */
    public boolean on(Key key) {
        return get(key) >= 0.5;
    }

    /** The tree of these settings is grown by {@code name} (and the settings are those of a tree). */
    boolean grows(String name) {
        return kind.tree() && name.equals(algorithm);
    }

    public long seed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = Math.max(0L, seed);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "tree" : name;
    }

    /** The name made safe for a file name (letters, digits, - and _), or null when nothing is left of it. */
    public static String safeName(String name) {
        if (name == null) return null;
        String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return clean.isEmpty() || clean.length() > 32 ? null : clean;
    }

    /** The settings as a map for the yaml. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", kind.name().toLowerCase(Locale.ROOT));
        map.put("crystal-type", crystalType);
        map.put("web-shape", webShape);
        map.put("algorithm", algorithm);
        for (Key key : Key.values()) map.put(key.id, key.whole() ? (Object) getInt(key) : (Object) get(key));
        map.put("seed", seed);
        Map<String, Object> chosen = new LinkedHashMap<>();
        for (Map.Entry<TreePart, String> entry : blocks.entrySet()) chosen.put(entry.getKey().key(), entry.getValue());
        map.put("blocks", chosen);
        map.put("leaf-gradient", new ArrayList<>(leafGradient));
        List<Map<String, Object>> parts = new ArrayList<>();
        for (HouseModule module : modules) parts.add(module.toMap());
        map.put("house-modules", parts);
        map.put("house-roof-steps", new ArrayList<>(roofSteps));
        map.put("house-design-seed", designSeed);
        return map;
    }

    /** Reads the settings from a map (what is missing keeps the default; what is out of range is brought into it). */
    public static TreeSettings fromMap(Map<String, Object> map, String name) {
        TreeSettings settings = new TreeSettings();
        settings.setName(name);
        if (map.get("kind") instanceof String text && ProcKind.byId(text) != null) settings.kind = ProcKind.byId(text);
        if (map.get("crystal-type") instanceof String text) settings.setCrystalType(text);
        if (map.get("web-shape") instanceof String text) settings.setWebShape(text);
        if (map.get("algorithm") instanceof String text) settings.setAlgorithm(text);
        for (Key key : Key.values()) {
            if (map.get(key.id) instanceof Number number) settings.set(key, number.doubleValue());
        }
        if (map.get("seed") instanceof Number number) settings.setSeed(number.longValue());
        Object chosen = map.get("blocks");
        if (chosen instanceof org.bukkit.configuration.ConfigurationSection section) chosen = section.getValues(false);
        if (chosen instanceof Map<?, ?> parts) {
            for (Map.Entry<?, ?> entry : parts.entrySet()) {
                for (TreePart part : TreePart.values()) {
                    if (part.key().equals(String.valueOf(entry.getKey()))) settings.setBlock(part, String.valueOf(entry.getValue()));
                }
            }
        }
        if (map.get("leaf-gradient") instanceof List<?> list) {
            List<String> names = new ArrayList<>();
            for (Object entry : list) names.add(String.valueOf(entry));
            settings.setLeafGradient(names);
        }
        if (map.get("house-modules") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof org.bukkit.configuration.ConfigurationSection section) entry = section.getValues(false);
                if (!(entry instanceof Map<?, ?> part) || settings.modules.size() >= MAX_MODULES) continue;
                HouseModule module = HouseModule.fromMap(part);
                if (module != null) settings.modules.add(module);
            }
        }
        if (map.get("house-roof-steps") instanceof List<?> list) {
            List<Integer> steps = new ArrayList<>();
            for (Object entry : list) if (entry instanceof Number number) steps.add(number.intValue());
            settings.setRoofSteps(steps);
        }
        if (map.get("house-design-seed") instanceof Number number) settings.setDesignSeed(number.longValue());
        return settings;
    }
}
