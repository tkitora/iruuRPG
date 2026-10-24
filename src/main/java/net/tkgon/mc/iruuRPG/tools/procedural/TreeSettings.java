package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings of one tree: the numbers (kept in an array by {@link Key}, so the menu, the command and the yaml all read them the same way), the
 * algorithm, the shape of the crown and the seed. {@link #clamp} keeps every value in its range. The maximum height and width are the size of
 * the box the whole tree (leaves included) is kept inside.
 */
final class TreeSettings {

    public static final List<String> ALGORITHMS = List.of("builder", "conifer", "parametric", "colonization", "lsystem");
    public static final List<String> CROWNS = List.of("ellipsoid", "cone", "umbrella", "columnar");

    /** A number setting: its name in the yaml and the command, the label of the menu, its range and the steps of a click and of a shift click. */
    public enum Key {
        HEIGHT("height", "高さ (最大)", 6, 80, 1, 5, "木全体 (葉を含む) の高さの上限 (ブロック)"),
        WIDTH("width", "幅 (最大)", 4, 60, 1, 5, "木全体 (葉を含む) の幅の上限 (ブロック。南北・東西とも)"),
        TRUNK_RADIUS("trunk-radius", "幹の太さ", 0.5, 5.0, 0.1, 0.5, "根元の幹の半径 (ブロック)。枝は分かれるごとに細くなる"),
        TRUNK_RATIO("trunk-ratio", "枝のない幹の割合", 0.15, 0.7, 0.05, 0.1, "高さのうち、枝が出る前の幹の割合"),
        DENSITY("density", "枝の多さ", 0.2, 3.0, 0.1, 0.5, "大きいほど枝が多く、密になる (樹冠の中の目標点の密度)"),
        DROOP("droop", "枝の垂れ", -0.5, 0.8, 0.05, 0.2, "枝が下へ垂れる向き (負だと上へ伸びる)"),
        LEAF_RADIUS("leaf-radius", "葉の大きさ", 0.0, 5.0, 0.25, 1.0, "枝の先にできる葉のかたまりの半径 (0 で葉なし)"),
        LEAF_DENSITY("leaf-density", "葉の密度", 0.2, 1.0, 0.05, 0.2, "かたまりの中を葉で埋める割合"),
        ROOTS("roots", "根の数", 0, 8, 1, 2, "地面に張り出す根の数"),
        BRANCH_ANGLE("branch-angle", "枝の開き角", 20, 110, 5, 15, "幹から枝が出る角度 (度。90で水平、小さいほど上向き)"),
        BRANCHES("branches", "太い枝の数", 3, 40, 1, 5, "幹から出る一番太い枝の数 (パラメトリック・針葉樹)"),
        LEVELS("levels", "枝分かれの段数", 1, 3, 1, 1, "枝がさらに枝を出す段数 (1 = 幹と枝だけ、3 = 小枝まで)"),
        CURVE("curve", "曲がり", 0.0, 1.0, 0.05, 0.2, "幹と枝のうねり・傾き (0 でまっすぐ)"),
        TAPER("taper", "先細り", 0.2, 1.0, 0.05, 0.2, "幹が先へ向かって細くなる度合い (1 で先端まで細る)"),
        FLARE("flare", "根元の広がり", 0.0, 2.0, 0.1, 0.5, "地面に近い幹の太くなり方"),
        LEAF_FLATNESS("leaf-flatness", "葉の厚み", 0.3, 1.3, 0.05, 0.2, "葉のかたまりの縦と横の比 (小さいほど平たい)"),
        TRUNK_FORKS("trunk-forks", "幹の分岐数", 0, 3, 1, 1, "幹が途中で分かれる本数 (0 = 分かれない。針葉樹は分かれない)"),
        FORK_HEIGHT("fork-height", "分岐の高さ", 0.2, 0.9, 0.05, 0.1, "幹が分かれる高さ (幹の高さに対する割合)"),
        FORK_ANGLE("fork-angle", "分岐の開き角", 10, 60, 2, 10, "分かれた幹が、真上から外へ開く角度 (度)"),
        BRANCH_LENGTH("branch-length", "枝の長さ", 0.4, 2.0, 0.05, 0.2, "幹から出る枝全体の長さの倍率 (1 = そのまま。箱 (幅) からはみ出た分は切られる)"),
        BRANCH_THICKNESS("branch-thickness", "枝の太さ", 0.4, 2.0, 0.05, 0.2, "枝全体の太さの倍率 (1 = そのまま。幹は変わらない)"),
        TRUNK_LEAN("trunk-lean", "幹の傾き", 0.0, 1.0, 0.05, 0.2, "幹が上へ行くほど一方へ傾く度合い (0 でまっすぐ)"),
        TRUNK_TWIST("trunk-twist", "幹のねじれ", 0.0, 1.0, 0.05, 0.2, "幹がらせんを描くようにねじれる度合い (0 でねじれなし)"),
        TRUNK_BULGE("trunk-bulge", "幹のふくらみ", 0.0, 1.0, 0.05, 0.2, "幹の中ほどが太くなる度合い (0 でふくらみなし)"),
        COUNT("count", "数", 1, 24, 1, 4, "岩のかたまり・スパイク・結晶の数 (岩は 1 で1つだけ)"),
        ROUGHNESS("roughness", "荒さ", 0.0, 1.0, 0.05, 0.2, "輪郭のでこぼこ (0 でなめらか、1 でごつごつ)"),
        LAYERS("layers", "層の数", 0, 10, 1, 2, "横縞の層の数。層ごとに明るい岩・暗い岩が交互に入る (0 で層なし)"),
        MOSS("moss", "苔", 0.0, 1.0, 0.05, 0.2, "上を向いた面に苔がつく量"),
        SPIKE_TAPER("spike-taper", "先の細さ", 0.4, 3.0, 0.1, 0.5, "スパイク・結晶が先へ向かって細くなる度合い (大きいほど針のように尖る)"),
        SPREAD("spread", "傾きのばらつき", 0.0, 1.0, 0.05, 0.2, "スパイク・結晶が外へ傾く度合い (0 でまっすぐ上)"),
        HANGING("hanging", "逆さ (つらら)", 0, 1, 1, 1, "1 にすると上下が逆になる (上から垂れ下がるスパイク)");

        private final String id;
        private final String label;
        private final double min;
        private final double max;
        private final double step;
        private final double bigStep;
        private final String description;

        Key(String id, String label, double min, double max, double step, double bigStep, String description) {
            this.id = id;
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.bigStep = bigStep;
            this.description = description;
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

    private final double[] values = {20, 12, 1.2, 0.35, 1.0, 0.0, 2.0, 0.8, 4, 55, 12, 3, 0.35, 0.75, 0.8, 0.8, 0, 0.45, 28, 1.0, 1.0, 0.0, 0.0, 0.0, 3, 0.5, 3, 0.3, 1.2, 0.4, 0};
    private ProcKind kind = ProcKind.TREE;
    private String algorithm = "builder";
    private String crown = "ellipsoid";
    /** 0 = a new random tree each time. */
    private long seed;
    private String name = "tree";

    public TreeSettings copy() {
        TreeSettings copy = new TreeSettings();
        System.arraycopy(values, 0, copy.values, 0, values.length);
        copy.kind = kind;
        copy.algorithm = algorithm;
        copy.crown = crown;
        copy.seed = seed;
        copy.name = name;
        return copy;
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

    public String algorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = ALGORITHMS.contains(algorithm) ? algorithm : "builder";
    }

    public void cycleAlgorithm() {
        setAlgorithm(ALGORITHMS.get((ALGORITHMS.indexOf(algorithm) + 1) % ALGORITHMS.size()));
    }

    public String crown() {
        return crown;
    }

    public void setCrown(String crown) {
        this.crown = CROWNS.contains(crown) ? crown : "ellipsoid";
    }

    public void cycleCrown() {
        setCrown(CROWNS.get((CROWNS.indexOf(crown) + 1) % CROWNS.size()));
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
        map.put("algorithm", algorithm);
        map.put("crown", crown);
        for (Key key : Key.values()) map.put(key.id, key.whole() ? (Object) getInt(key) : (Object) get(key));
        map.put("seed", seed);
        return map;
    }

    /** Reads the settings from a map (what is missing keeps the default; what is out of range is brought into it). */
    public static TreeSettings fromMap(Map<String, Object> map, String name) {
        TreeSettings settings = new TreeSettings();
        settings.setName(name);
        if (map.get("kind") instanceof String text && ProcKind.byId(text) != null) settings.kind = ProcKind.byId(text);
        if (map.get("algorithm") instanceof String text) settings.setAlgorithm(text);
        if (map.get("crown") instanceof String text) settings.setCrown(text);
        for (Key key : Key.values()) {
            if (map.get(key.id) instanceof Number number) settings.set(key, number.doubleValue());
        }
        if (map.get("seed") instanceof Number number) settings.setSeed(number.longValue());
        return settings;
    }
}
