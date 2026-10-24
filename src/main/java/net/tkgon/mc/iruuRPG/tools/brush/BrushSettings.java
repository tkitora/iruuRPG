package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Everything one player has set on the brush: the mode and shape, the size, the pattern, the masks and the sun. Kept in memory while the player is online. */
final class BrushSettings {

    /** A block of the pattern and how often it is chosen against the others. */
    static final class Entry {
        Material material;
        int weight;

        Entry(Material material, int weight) {
            this.material = material;
            this.weight = weight;
        }
    }

    /** How the blocks painted are chosen: by weight from the list, or along a gradient between two blocks. */
    enum PatternMode { RANDOM, GRADIENT }

    /** What the place along the gradient (0 - 1) comes from. */
    enum GradientAxis {
        RADIAL("中心から縁へ", "ブラシの中心が最初のブロック、縁が最後のブロック"),
        VERTICAL("下から上へ", "ブラシの下が最初のブロック、上が最後のブロック"),
        DIRECTIONAL("向きに沿って", "使ったときに向いていた方向に、手前から奥へ"),
        NOISE("ノイズ", "ノイズ (種類・大きさ・細かさは「ノイズ」のページ) で斑にする"),
        STROKE("なぞった線に沿って", "右クリックを押したままなぞり、離したときに、なぞった全体の長さを 0〜1 として、最初→最後のブロックを置く"),
        ANGLE("表面の傾き", "平らな面が最初のブロック、垂直な壁が最後のブロック (0°〜90°)"),
        CURVATURE("曲率", "凹んだ所が最初のブロック、出っぱった所が最後のブロック"),
        AMBIENT("アンビエント(AO)", "開けた所が最初のブロック、囲まれた所が最後のブロック"),
        LIGHT("光 (陰影)", "日なたが最初のブロック、影が最後のブロック (太陽の向きは「陰影」のページ)"),
        BLOCK("特定のブロックから", "「高さ・位置」のページで決めたブロックを起点に、上下へ離れるほど最後のブロックへ");

        final String label;
        final String description;

        GradientAxis(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /** The page of the menu that was open last: the menu opens on it next time. */
    BrushMenu.Page lastPage = BrushMenu.Page.MAIN;

    BrushMode mode = BrushMode.PAINT;
    BrushShape shape = BrushShape.SPHERE;
    int radius = 6;
    /** The share of the blocks in the brush that are painted at the centre (0.1 - 1). */
    double strength = 1.0;
    PatternMode patternMode = PatternMode.RANDOM;
    Material gradientFrom = Material.STONE;
    Material gradientTo = Material.DEEPSLATE;
    int gradientSteps = 4;
    GradientAxis gradientAxis = GradientAxis.RADIAL;
    /** How much the gradient is broken up from block to block (0 = clean bands, 1 = very grainy). */
    double dither = 0.25;
    /** Painting again on a place moves its colour further along the gradient instead of replacing it. */
    boolean additive;
    /** How far one application moves a block's colour toward the gradient (0.1 - 1). */
    double additiveRate = 0.5;
    /** Toon shading: the number of bands the shade is cut into (0 = smooth, 2 - 6). */
    int toonSteps;
    /** With toon steps on and the shade mask on: the least band (1 = the lightest shadow) that passes. */
    int shadeBand = 1;
    /** The horizontal direction the player faces when the brush is used (for a gradient along it); set by the module. */
    org.bukkit.util.Vector facing = new Vector(0.0, 0.0, 1.0);
    // the sources of the shade besides the light: ambient occlusion, curvature, noise and cells (each can be on or off; they add up, 0 - 1 in all)
    boolean lightOn = true;
    boolean ambientOn;
    double ambientStrength = 0.6;
    boolean curvatureOn;
    /** true: the ridges and edges (convex) are darkened; false: the crevices (concave). */
    boolean curvatureConvex;
    double curvatureStrength = 0.6;
    boolean noiseShadeOn;
    double noiseShadeStrength = 0.5;
    double noiseShadeScale = 4.0;
    boolean cellOn;
    double cellStrength = 0.5;
    double cellSize = 5.0;
    /** true: the borders of the cells are darkened; false: every other cell is darkened as a whole. */
    boolean cellEdges = true;

    /** The selection the brush is held to (null: none); set by the module from the player's selection tool. */
    Selection selection;
    // noise (one set of settings for the pattern's noise, the gradient's and the shade's)
    Procedural.NoiseType noiseType = Procedural.NoiseType.VALUE;
    int noiseOctaves = 1;
    /** How strong each finer layer is next to the one before (0.2 - 0.8). */
    double noisePersistence = 0.5;
    /** How hard the noise is pushed toward light and dark (0.5 - 3). */
    double noiseContrast = 1.0;
    int noiseSeed;
    /** Whether the cell shade measures with a cube (angular shards) instead of a ball. */
    boolean cellAngular;

    // the stroke (a button held down while the brush moves)
    /** How far apart the points of a stroke are, as a share of the radius (0.2 - 1.5). */
    double strokeSpacing = 0.6;
    /** How much the path of a stroke is smoothed (0 = as it was drawn, 1 = very smooth). */
    double strokeSmooth;
    /** The size of the brush at the start and at the end of a stroke, as a share of the radius (0.2 - 1; in between it changes evenly). */
    double taperStart = 1.0;
    double taperEnd = 1.0;
    /** How far each point of a stroke is moved at random sideways, as a share of the radius (0 - 1). */
    double strokeScatter;
    /** Every held stroke is drawn when the button is let go, as one edit (one //undo), also when the gradient is not along the stroke. */
    boolean strokeGroup;
    /** The share of the radius the brush has now (set by the module along a stroke with a taper). */
    double radiusScale = 1.0;

    // ambient occlusion
    /** How far from the block the rays look (1 - 6). */
    int aoRadius = 3;
    int aoSamples = 24;
    /** The curve of the result (0.4 - 3): above 1 only the deepest corners darken, below 1 the open places darken too. */
    double aoPower = 1.0;
    /** How enclosed a place must be before it darkens (0 - 0.8): below this share the place counts as open. */
    double aoBias;
    /** Whether leaves and logs count as walls for the ambient occlusion (off: only stone, earth and built blocks do). */
    boolean aoCountPlants = true;

    // gradient application
    boolean gradientReverse;
    /** The gradient starting from a block: the blocks it starts from, and whether it goes down from them (they are above) or up (they are below). */
    final Set<Material> referenceBlocks = new LinkedHashSet<>();
    boolean referenceDown = true;
    /** How many blocks away from the starting block the gradient reaches its last block. */
    int referenceSpan = 8;

    /** Counts every change of a setting, so the live preview can tell when to start over. */
    int revision;
    /** Where on the gradient (0 - 1) the stroke is now, for the gradient along the stroke; set by the module. */
    double strokeT;
    /** 0 = the whole brush is the same, 1 = the effect fades to nothing at the edge. */
    double falloff = 0.3;
    boolean surfaceOnly = true;
    boolean noise;
    double noiseScale = 3.0;
    boolean preview;
    final List<Entry> pattern = new ArrayList<>();

    // masks: each is off, or on (it lets pass what it describes) or on and inverted (it lets pass what it does not describe)
    boolean blockMaskOn;
    boolean blockMaskInvert;
    final Set<Material> blockMask = new LinkedHashSet<>();
    boolean angleMaskOn;
    boolean angleMaskInvert;
    int angleMin;
    int angleMax = 45;
    // height mask: only blocks at or above (or at or below) a height
    boolean heightMaskOn;
    int heightValue = 64;
    /** true: blocks at {@code heightValue} or higher pass; false: at or lower. */
    boolean heightAbove = true;
    // "next to a block" mask: only blocks that have one of these blocks directly above them (or, in the other direction, below), within {@code relationReach}
    boolean relationMaskOn;
    final Set<Material> relationBlocks = new LinkedHashSet<>();
    /** true: only blocks under one of the blocks (it is above); false: only blocks on top of one of them (it is below). */
    boolean relationUnder = true;
    int relationReach = 12;
    boolean shadeMaskOn;
    /** false: only the shaded surfaces pass; true (inverted): only the lit ones. */
    boolean shadeMaskInvert;

    // the sun: compass degrees (0 = north, 90 = east) and the height above the horizon
    int sunAzimuth = 135;
    int sunElevation = 45;
    /** How dark a fully shaded surface is made (0.1 - 0.8). */
    double shadeDepth = 0.4;
    /** Whether a block in the way (a wall, a tree) casts a shadow, not only the turn of the surface away from the sun. */
    boolean castShadows = true;

    /** The direction from the surface toward the sun. */
    Vector sunVector() {
        double azimuth = Math.toRadians(sunAzimuth);
        double elevation = Math.toRadians(sunElevation);
        return new Vector(Math.sin(azimuth) * Math.cos(elevation), Math.sin(elevation), -Math.cos(azimuth) * Math.cos(elevation)).normalize();
    }

    Entry find(Material material) {
        for (Entry entry : pattern) if (entry.material == material) return entry;
        return null;
    }

    int totalWeight() {
        int total = 0;
        for (Entry entry : pattern) total += entry.weight;
        return total;
    }

    Set<Material> patternMaterials() {
        Set<Material> set = new LinkedHashSet<>();
        for (Entry entry : pattern) set.add(entry.material);
        return set;
    }
}
