package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.building.BuildParams;
import net.tkgon.mc.iruuRPG.tools.procedural.building.BuildingGenerator;
import net.tkgon.mc.iruuRPG.tools.procedural.building.Cell;
import net.tkgon.mc.iruuRPG.tools.procedural.building.Ground;
import net.tkgon.mc.iruuRPG.tools.procedural.building.HouseSpec;
import net.tkgon.mc.iruuRPG.tools.procedural.building.Role;
import net.tkgon.mc.iruuRPG.tools.procedural.building.SemanticVoxelGrid;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (t295) The building tool's side in the procedural tool: the settings made the numbers of {@link BuildingGenerator}, the building made (its front toward the
 * player, turned on by quarters), and its blocks by part as every object of the tool is (so the preview, the placing, //undo and the presets are shared). A
 * block's role is its part; its shape (a stair, a slab, a log lying or standing, a wall, a fence, a trapdoor) and its states go with the blocks ({@link Blocks}):
 * a stair of the roof is the stairs of the block chosen for the roof, and so on ({@link #form}).
 */
final class BuildingShape {

    private BuildingShape() {
    }

    // ---- the names of the numbers ------------------------------------------------------------------------------------------------

    static boolean named(TreeSettings.Key key) {
        return switch (key) {
            case BLD_FOOTPRINT, BLD_CORBEL, BLD_RIDGE_STYLE, BLD_BANDS, BLD_PATTERN, BLD_GEN_MODULE, BLD_GEN_PITCH, BLD_GEN_JETTY, BLD_ROOF_LOOK, BLD_SHAPES -> true;
            default -> false;
        };
    }

    /** The name of a building number (null: shown as a number). */
    static String valueName(TreeSettings.Key key, double value) {
        int v = (int) Math.round(value);
        return switch (key) {
            case BLD_FOOTPRINT -> pick(v, "長方形", "L字", "T字", "U字", "十字", "面取り", "八角形", "円");
            case BLD_CORBEL -> pick(v, "逆さ階段", "横向きの原木");
            case BLD_RIDGE_STYLE -> pick(v, "ハーフ", "塀", "棟木");
            case BLD_BANDS -> pick(v, "なし", "高さごと", "屋根面ごと");
            case BLD_PATTERN -> net.tkgon.mc.iruuRPG.tools.procedural.building.Archetypes.name(v);
            case BLD_GEN_MODULE -> pick(v, "4 か 5", "4", "5");
            case BLD_ROOF_LOOK -> pick(v, "階段・ハーフ", "フルブロック");
            case BLD_SHAPES -> pick(v, "フルブロック (羊毛だけ)", "階段・ハーフ・フェンスも");
            case BLD_GEN_PITCH -> {
                StringBuilder text = new StringBuilder();
                String[] names = {"1:2", "1:1", "2:1"};
                for (int bit = 0; bit < 3; bit++) if ((v >> bit & 1) != 0) text.append(text.isEmpty() ? "" : ", ").append(names[bit]);
                yield text.isEmpty() ? "1:1" : text.toString();
            }
            case BLD_GEN_JETTY -> {
                if (v == 0) yield "全部の階";
                StringBuilder text = new StringBuilder();
                for (int bit = 0; bit < 3; bit++) if ((v >> bit & 1) != 0) text.append(text.isEmpty() ? "" : "・").append(bit + 2).append("階");
                yield text.toString();
            }
            default -> null;
        };
    }

    private static String pick(int index, String... names) {
        return names[Math.max(0, Math.min(names.length - 1, index))];
    }

    /** Whether the settings make the building of the house base's numbers (not a pattern): its parts list and the part wand work on it. */
    static boolean houseLike(TreeSettings s) {
        return s.kind() == ProcKind.HOUSE || s.kind() == ProcKind.BUILDING && s.getInt(TreeSettings.Key.BLD_PATTERN) == 0;
    }

    // ---- the patterns' starting numbers ------------------------------------------------------------------------------------------

    /**
     * Sets the ranges and the parts' chances a pattern starts from (the menu sets them when the pattern is changed): floors min and max, the module, the slopes,
     * the eaves, the jetties; chimney, dormer, bay window, balcony, turret, porch.
     */
    static void applyPattern(TreeSettings s, int pattern) {
        double[] numbers = switch (pattern) {
            case 1 -> new double[]{2, 3, 0, 6, 1, 2, 0, 0.6, 0.0, 0.3, 0.4, 0.0, 0.5};
            case 2 -> new double[]{3, 4, 0, 4, 1, 1, 7, 0.6, 0.8, 0.0, 0.0, 0.0, 0.5};
            case 3 -> new double[]{2, 3, 1, 6, 1, 2, 0, 0.5, 0.3, 0.3, 0.3, 1.0, 0.5};
            case 4 -> new double[]{2, 3, 1, 3, 2, 3, 0, 0.0, 0.3, 0.0, 0.0, 0.0, 1.0};
            case 5 -> new double[]{2, 3, 0, 6, 1, 2, 0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.6};
            case 6 -> new double[]{3, 4, 1, 4, 1, 1, 3, 0.3, 0.0, 0.0, 0.0, 0.0, 0.5};
            default -> null;
        };
        if (numbers == null) return;
        TreeSettings.Key[] keys = {TreeSettings.Key.BLD_GEN_FLOORS_MIN, TreeSettings.Key.BLD_GEN_FLOORS_MAX, TreeSettings.Key.BLD_GEN_MODULE, TreeSettings.Key.BLD_GEN_PITCH,
                TreeSettings.Key.BLD_GEN_OVERHANG_MIN, TreeSettings.Key.BLD_GEN_OVERHANG_MAX, TreeSettings.Key.BLD_GEN_JETTY, TreeSettings.Key.BLD_CHIMNEY,
                TreeSettings.Key.BLD_DORMER, TreeSettings.Key.BLD_BAY_WINDOW, TreeSettings.Key.BLD_BALCONY, TreeSettings.Key.BLD_TURRET, TreeSettings.Key.BLD_PORCH};
        for (int index = 0; index < keys.length; index++) s.set(keys[index], numbers[index]);
    }

    /** The numbers of the generator from the settings: (t302) the house base's numbers and its list of parts, and the building's own. */
    static BuildParams params(TreeSettings s) {
        BuildParams p = new BuildParams();
        HouseSpec h = p.house;
        h.width = s.getInt(TreeSettings.Key.HOUSE_WIDTH);
        h.depth = s.getInt(TreeSettings.Key.HOUSE_DEPTH);
        h.floors = s.getInt(TreeSettings.Key.HOUSE_FLOORS);
        h.storey = s.getInt(TreeSettings.Key.HOUSE_STOREY);
        h.spacing = s.getInt(TreeSettings.Key.HOUSE_PILLARS);
        h.mirror = s.on(TreeSettings.Key.HOUSE_PILLAR_MIRROR);
        h.jetty = s.getInt(TreeSettings.Key.HOUSE_JETTY);
        h.jettySides = s.on(TreeSettings.Key.HOUSE_JETTY_SIDES);
        h.brackets = s.on(TreeSettings.Key.HOUSE_BRACKETS);
        h.door = s.on(TreeSettings.Key.HOUSE_DOOR);
        h.foundation = s.on(TreeSettings.Key.HOUSE_FOUNDATION);
        h.raise = s.getInt(TreeSettings.Key.HOUSE_RAISE);
        h.terrace = s.getInt(TreeSettings.Key.HOUSE_TERRACE);
        h.stone = s.on(TreeSettings.Key.HOUSE_STONE);
        h.plinth = s.getInt(TreeSettings.Key.HOUSE_PLINTH);
        h.timber = s.on(TreeSettings.Key.HOUSE_TIMBER);
        h.recess = s.on(TreeSettings.Key.HOUSE_RECESS);
        h.windows = s.on(TreeSettings.Key.HOUSE_WINDOWS);
        h.glass = s.on(TreeSettings.Key.HOUSE_GLASS);
        h.skirt = s.getInt(TreeSettings.Key.HOUSE_SKIRT);
        h.roof = s.getInt(TreeSettings.Key.HOUSE_ROOF);
        h.pitch = s.get(TreeSettings.Key.HOUSE_PITCH);
        h.overhang = s.getInt(TreeSettings.Key.HOUSE_OVERHANG);
        h.ridge = s.getInt(TreeSettings.Key.HOUSE_RIDGE);
        h.ridgeBeam = s.on(TreeSettings.Key.HOUSE_RIDGE_BEAM);
        List<Integer> steps = s.roofSteps();
        h.steps = new int[Math.max(1, steps.size())];
        for (int index = 0; index < steps.size(); index++) h.steps[index] = steps.get(index);
        h.stepFit = s.on(TreeSettings.Key.HOUSE_STEP_FIT);
        for (HouseModule m : s.modules()) {
            h.modules.add(new HouseSpec.Module(m.type.id, m.side, m.along, m.offset, m.width, m.depth, m.height, m.level, m.on, m.roof, m.ridge, m.shape, m.jetty, m.raise,
                    m.spire));
        }
        p.footprint = s.getInt(TreeSettings.Key.BLD_FOOTPRINT);
        p.wingLength = s.getInt(TreeSettings.Key.BLD_WING);
        p.wingWidth = s.getInt(TreeSettings.Key.BLD_WING_WIDTH);
        p.chamfer = s.getInt(TreeSettings.Key.BLD_CHAMFER);
        p.slant = s.getInt(TreeSettings.Key.BLD_SLANT);
        p.plinth = s.get(TreeSettings.Key.BLD_PLINTH);
        p.fasciaOut = s.on(TreeSettings.Key.BLD_FASCIA_OUT);
        p.sill = s.getInt(TreeSettings.Key.BLD_SILL);
        p.windowHeight = s.getInt(TreeSettings.Key.BLD_WINDOW_HEIGHT);
        p.windowWidth = s.getInt(TreeSettings.Key.BLD_WINDOW_WIDTH);
        p.corbel = s.getInt(TreeSettings.Key.BLD_CORBEL);
        p.thickness = s.getInt(TreeSettings.Key.BLD_ROOF_THICKNESS);
        p.rise = s.getInt(TreeSettings.Key.BLD_ROOF_BREAK);
        p.ridgeStyle = s.getInt(TreeSettings.Key.BLD_RIDGE_STYLE);
        p.gableOut = s.on(TreeSettings.Key.BLD_GABLE_OUT);
        p.bands = s.getInt(TreeSettings.Key.BLD_BANDS);
        p.bandStep = s.getInt(TreeSettings.Key.BLD_BAND_STEP);
        p.endPitch = s.get(TreeSettings.Key.BLD_END_PITCH);
        p.backPitch = s.get(TreeSettings.Key.BLD_BACK_PITCH);
        p.curve = s.get(TreeSettings.Key.BLD_CURVE);
        p.crest = s.getInt(TreeSettings.Key.BLD_CREST);
        p.eaveDrop = s.getInt(TreeSettings.Key.BLD_EAVE_DROP);
        p.chimney = s.get(TreeSettings.Key.BLD_CHIMNEY);
        p.dormer = s.get(TreeSettings.Key.BLD_DORMER);
        p.bayWindow = s.get(TreeSettings.Key.BLD_BAY_WINDOW);
        p.balcony = s.get(TreeSettings.Key.BLD_BALCONY);
        p.turret = s.get(TreeSettings.Key.BLD_TURRET);
        p.porch = s.get(TreeSettings.Key.BLD_PORCH);
        p.finial = s.get(TreeSettings.Key.BLD_FINIAL);
        p.pattern = s.getInt(TreeSettings.Key.BLD_PATTERN);
        p.floorsMin = s.getInt(TreeSettings.Key.BLD_GEN_FLOORS_MIN);
        p.floorsMax = s.getInt(TreeSettings.Key.BLD_GEN_FLOORS_MAX);
        p.moduleChoice = s.getInt(TreeSettings.Key.BLD_GEN_MODULE);
        p.pitchMask = s.getInt(TreeSettings.Key.BLD_GEN_PITCH);
        p.overhangMin = s.getInt(TreeSettings.Key.BLD_GEN_OVERHANG_MIN);
        p.overhangMax = s.getInt(TreeSettings.Key.BLD_GEN_OVERHANG_MAX);
        p.patternJetty = s.getInt(TreeSettings.Key.BLD_GEN_JETTY);
        p.candidates = s.getInt(TreeSettings.Key.BLD_GEN_CANDIDATES);
        p.retries = s.getInt(TreeSettings.Key.BLD_GEN_RETRIES);
        return p;
    }

    /** How far round the click the terrain is read: as far as the building of the settings reaches (24 at least, 48 at most). */
    static int reach(TreeSettings s) {
        if (s.getInt(TreeSettings.Key.BLD_PATTERN) > 0) return 32;
        int wings = s.getInt(TreeSettings.Key.BLD_FOOTPRINT) >= 1 && s.getInt(TreeSettings.Key.BLD_FOOTPRINT) <= 4 ? s.getInt(TreeSettings.Key.BLD_WING) : 0;
        return Math.max(24, Math.min(48, HouseShape.reach(s) + wings + 4));
    }

    /** The quarter turns from the building's own way (its front +z) to the world's: its front toward {@code aim} (null: south), then the turn of the settings. */
    static int turns(Vector aim, int rotation) {
        double x = aim == null ? 0.0 : aim.getX();
        double z = aim == null ? 1.0 : aim.getZ();
        int facing;
        if (Math.abs(x) > Math.abs(z)) facing = x >= 0 ? 3 : 1;
        else facing = z >= 0 ? 0 : 2;
        return Math.floorMod(facing + rotation, 4);
    }

    /** The building's {@code (x, z)} turned {@code turns} quarters clockwise (south to west). */
    static int[] turn(int x, int z, int turns) {
        int tx = x;
        int tz = z;
        for (int turn = 0; turn < Math.floorMod(turns, 4); turn++) {
            int next = -tz;
            tz = tx;
            tx = next;
        }
        return new int[]{tx, tz};
    }

    /**
     * Where the building stands (t302: as the house base does): the building of the house's numbers in the house's own coordinates, its front toward
     * {@code aim}, turned by the house's rotation (any degree); a pattern's building about its middle the same way.
     */
    static HouseShape.Placement placement(TreeSettings s, Vector aim) {
        int rotation = s.getInt(TreeSettings.Key.HOUSE_ROTATION);
        if (s.getInt(TreeSettings.Key.BLD_PATTERN) > 0) return HouseShape.Placement.of(aim, rotation, 1, 1);
        return HouseShape.Placement.of(aim, rotation, s.getInt(TreeSettings.Key.HOUSE_WIDTH), s.getInt(TreeSettings.Key.HOUSE_DEPTH));
    }

    // ---- the building ----------------------------------------------------------------------------------------------------------

    static Blocks build(TreeSettings s, long seed, TreeGround ground, Vector aim) {
        return build(s, seed, ground, aim, -1, null);
    }

    /**
     * Makes the building of the settings at the click (the terrain {@code ground} round it; null: flat), its front toward {@code aim}. The blocks of part
     * {@code highlight} of the house's list are the glass of a part being edited; {@code owners}, when given, gets the part each block is of (in the house's own
     * coordinates, as the part wand reads them).
     */
    static Blocks build(TreeSettings s, long seed, TreeGround ground, Vector aim, int highlight, Map<Long, Integer> owners) {
        BuildParams p = params(s);
        HouseShape.Placement placement = placement(s, aim);
        Ground terrain = ground == null ? Ground.FLAT : (x, z) -> {
            int[] at = placement.block(x, z);
            int top = ground.top(at[0], at[1]);
            return top == TreeGround.NONE ? Ground.NONE : top;
        };
        BuildingGenerator.Result result;
        try {
            result = BuildingGenerator.generate(p, seed, terrain);
        } catch (SemanticVoxelGrid.TooBig exception) {
            throw new TreeVoxelizer.TooBig();
        }
        Blocks blocks = new Blocks();
        blocks.roofFull = s.getInt(TreeSettings.Key.BLD_ROOF_LOOK) == 1;
        blocks.shapesFull = s.getInt(TreeSettings.Key.BLD_SHAPES) == 0;
        int turns = Math.floorMod((int) Math.round(placement.degrees() / 90.0), 4);
        SemanticVoxelGrid grid = result.grid();
        if (placement.square()) {
            for (Map.Entry<Long, Cell> entry : grid.cells().entrySet()) {
                long key = entry.getKey();
                int[] at = placement.block(SemanticVoxelGrid.x(key), SemanticVoxelGrid.z(key));
                put(blocks, grid, key, TreeVoxelizer.pack(at[0], SemanticVoxelGrid.y(key), at[1]), entry.getValue().turned(turns), highlight, owners);
            }
        } else {
            // (t302: turned by other than a right angle: every block of the world over it takes the column of the building nearest it, so no holes open)
            Map<Long, List<Long>> columns = new HashMap<>();
            int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (long key : grid.cells().keySet()) {
                int x = SemanticVoxelGrid.x(key);
                int z = SemanticVoxelGrid.z(key);
                columns.computeIfAbsent(TreeVoxelizer.pack(x, 0, z), unused -> new java.util.ArrayList<>()).add(key);
                int[] at = placement.block(x, z);
                box[0] = Math.min(box[0], at[0] - 1);
                box[1] = Math.min(box[1], at[1] - 1);
                box[2] = Math.max(box[2], at[0] + 1);
                box[3] = Math.max(box[3], at[1] + 1);
            }
            for (int wx = box[0]; wx <= box[2]; wx++) {
                for (int wz = box[1]; wz <= box[3]; wz++) {
                    int[] local = placement.local(wx, wz);
                    List<Long> column = columns.get(TreeVoxelizer.pack(local[0], 0, local[1]));
                    if (column == null) continue;
                    for (long key : column) put(blocks, grid, key, TreeVoxelizer.pack(wx, SemanticVoxelGrid.y(key), wz), grid.cells().get(key).turned(turns), highlight, owners);
                }
            }
        }
        blocks.pattern = result.building().pattern;
        blocks.problems = result.problems();
        blocks.score = result.score();
        blocks.share = result.roofShare();
        blocks.tries = result.tries();
        return blocks;
    }

    /** One block of the building into the world's ({@code world}): its part (the glass of the part being edited), its look, its owner for the wand. */
    private static void put(Blocks blocks, SemanticVoxelGrid grid, long key, long world, Cell cell, int highlight, Map<Long, Integer> owners) {
        int owner = grid.ownerOf(key);
        boolean chosen = highlight >= 0 && owner == highlight;
        blocks.put(world, chosen ? TreePart.HOUSE_SELECTED : part(cell.role()));
        blocks.cells.put(world, cell);
        if (owners != null && owner >= 0) owners.put(key, owner);
    }

    /** The part a role's blocks are. */
    static TreePart part(Role role) {
        return switch (role) {
            case PLINTH -> TreePart.BLD_PLINTH;
            case POST -> TreePart.BLD_POST;
            case BEAM -> TreePart.BLD_BEAM;
            case INFILL -> TreePart.BLD_INFILL;
            case WINDOW -> TreePart.BLD_WINDOW;
            case CORBEL -> TreePart.BLD_CORBEL;
            case FLOOR -> TreePart.BLD_FLOOR;
            case ROOF -> TreePart.BLD_ROOF;
            case ROOF_2 -> TreePart.BLD_ROOF_2;
            case ROOF_3 -> TreePart.BLD_ROOF_3;
            case EAVE -> TreePart.BLD_EAVE;
            case VERGE -> TreePart.BLD_VERGE;
            case RIDGE -> TreePart.BLD_RIDGE;
            case HIP -> TreePart.BLD_HIP;
            case CHIMNEY -> TreePart.BLD_CHIMNEY;
            case RAIL -> TreePart.BLD_RAIL;
            case FINIAL -> TreePart.BLD_FINIAL;
        };
    }

    /** What the checks and the score said, for the messages. */
    static String report(Blocks blocks) {
        String checks = blocks.problems.isEmpty() ? "検証 OK" : "検証で残った問題: " + String.join(" / ", blocks.problems);
        return blocks.pattern + " / 点 " + String.format(java.util.Locale.ROOT, "%.2f", blocks.score) + " / 屋根の比率 " + String.format(java.util.Locale.ROOT, "%.2f",
                blocks.share) + " / 試した数 " + blocks.tries + " / " + checks;
    }

    // ---- the blocks ------------------------------------------------------------------------------------------------------------

    /**
     * The blocks of a building, by part as every object's are, with each block's shape and states. When it is shown or put, {@link #data} makes a block: the block
     * chosen for its part in the shape the block is ({@link #form}), with its states.
     */
    static final class Blocks extends HashMap<Long, TreePart> {

        final Map<Long, Cell> cells = new HashMap<>();
        String pattern = "";
        List<String> problems = List.of();
        double score;
        double share;
        int tries;
        private final Map<String, BlockData> made = new HashMap<>();

        /** (t302) The roof's blocks all full (its steps of wool); the other shapes all full (wool only, no stairs, slabs, fences). */
        boolean roofFull;
        boolean shapesFull;

        /** The block at (x, y, z) made of {@code chosen} (the part's block), or null when there is none. */
        BlockData data(BlockData chosen, TreePart part, int x, int y, int z) {
            Cell cell = cells.get(TreeVoxelizer.pack(x, y, z));
            if (cell == null || chosen == null) return null;
            // (the part being edited: its glass, whole)
            if (part == TreePart.HOUSE_SELECTED) return chosen;
            Cell.Form shape = cell.form();
            boolean roof = part == TreePart.BLD_ROOF || part == TreePart.BLD_ROOF_2 || part == TreePart.BLD_ROOF_3 || part == TreePart.BLD_EAVE
                    || part == TreePart.BLD_VERGE || part == TreePart.BLD_RIDGE || part == TreePart.BLD_HIP;
            boolean full = shape != Cell.Form.FULL && shape != Cell.Form.LOG && (roof ? roofFull : shapesFull);
            Material material = full ? form(chosen.getMaterial(), Cell.Form.FULL, part.defaultMaterial()) : form(chosen.getMaterial(), shape, part.defaultMaterial());
            String props = full ? "" : cell.props();
            String key = material.name() + "[" + props + "]";
            BlockData data = made.get(key);
            if (data == null) {
                data = create(material, props, chosen);
                made.put(key, data);
            }
            return data;
        }

        private static BlockData create(Material material, String props, BlockData chosen) {
            if (!props.isEmpty()) {
                try {
                    return Bukkit.createBlockData(material, "[" + props + "]");
                } catch (IllegalArgumentException exception) {
                    // (a block without those states, a full block chosen for the posts: as it is)
                }
            }
            if (material == chosen.getMaterial()) return chosen;
            try {
                BlockData data = material.createBlockData();
                if (data instanceof org.bukkit.block.data.type.Leaves leaves) leaves.setPersistent(true);
                return data;
            } catch (IllegalArgumentException exception) {
                return chosen;
            }
        }
    }

    private static final Map<Cell.Form, String> SUFFIX = new EnumMap<>(Cell.Form.class);

    static {
        SUFFIX.put(Cell.Form.STAIRS, "_STAIRS");
        SUFFIX.put(Cell.Form.SLAB, "_SLAB");
        SUFFIX.put(Cell.Form.WALL, "_WALL");
        SUFFIX.put(Cell.Form.FENCE, "_FENCE");
        SUFFIX.put(Cell.Form.TRAPDOOR, "_TRAPDOOR");
    }

    /**
     * The block of {@code chosen}'s family in the shape {@code form}: its stairs, slab, wall, fence or trapdoor (by name: the planks of a wood, the bricks and
     * tiles without their s), its log, its full block. When the chosen block has none, {@code fallback}'s (the part's default) in that shape, so the shape stays;
     * when that has none either, the chosen block itself. A lightning rod is always a lightning rod.
     */
    static Material form(Material chosen, Cell.Form form, Material fallback) {
        if (form == Cell.Form.ROD) return Material.LIGHTNING_ROD;
        Material own = family(chosen, form);
        if (own != null) return own;
        // (t302: a wool has no stairs: those of the colour nearest it)
        Material near = nearest(chosen, form);
        if (near != null) return near;
        Material other = fallback == null ? null : family(fallback, form);
        if (other != null) return other;
        Material otherNear = fallback == null ? null : nearest(fallback, form);
        return otherNear != null ? otherNear : chosen;
    }

    /** The colours of the wools (their textures' average), for the shapes nearest them. */
    private static final Map<Material, Integer> WOOL = new EnumMap<>(Material.class);

    static {
        int[] rgb = {0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C627, 0x70B919, 0xED8DAC, 0x3E4447, 0x8E8E86, 0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B,
                0xA12722, 0x141519};
        String[] names = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"};
        for (int index = 0; index < names.length; index++) {
            Material material = Material.getMaterial(names[index] + "_WOOL");
            if (material != null) WOOL.put(material, rgb[index]);
        }
    }

    /** (t302) The block of shape {@code form} whose colour is nearest a wool's (null: not a wool, or a full block or a log, which a wool is already). */
    static Material nearest(Material wool, Cell.Form form) {
        Integer rgb = WOOL.get(wool);
        if (rgb == null) return null;
        WoodPalette.Form shape = switch (form) {
            case STAIRS -> WoodPalette.Form.STAIRS;
            case SLAB -> WoodPalette.Form.SLAB;
            case WALL -> WoodPalette.Form.WALL;
            case FENCE -> WoodPalette.Form.FENCE;
            case TRAPDOOR -> WoodPalette.Form.TRAPDOOR;
            default -> null;
        };
        if (shape == null) return null;
        double[] lab = WoodPalette.lab(rgb);
        WoodPalette.Entry best = null;
        double bestScore = Double.MAX_VALUE;
        for (WoodPalette.Entry entry : WoodPalette.of(shape)) {
            double score = Math.pow(entry.l() - lab[0], 2) + 2.0 * (Math.pow(entry.a() - lab[1], 2) + Math.pow(entry.b() - lab[2], 2));
            if (score < bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        return best == null ? null : best.material();
    }

    private static Material family(Material material, Cell.Form form) {
        String name = material.name();
        String stem = stem(name);
        return switch (form) {
            case FULL -> isShaped(name) ? first(stem, stem + "S", stem + "_PLANKS", stem + "_BLOCK") : material;
            case LOG -> name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE") ? material
                    : first(stem + "_LOG", stem + "_STEM", name.equals("BAMBOO_PLANKS") ? "BAMBOO_BLOCK" : null, isShaped(name) ? null : name);
            default -> name.endsWith(SUFFIX.get(form)) && !(form == Cell.Form.FENCE && name.endsWith("_FENCE_GATE")) ? material : first(stem + SUFFIX.get(form));
        };
    }

    private static boolean isShaped(String name) {
        return name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_WALL") || name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE")
                || name.endsWith("_TRAPDOOR");
    }

    /** The name of a block's family: without stripped, without its shape's ending, a bricks' or tiles' s dropped (stone bricks: STONE_BRICK). */
    static String stem(String name) {
        String stem = name.startsWith("STRIPPED_") ? name.substring(9) : name;
        for (String end : new String[]{"_STAIRS", "_SLAB", "_WALL", "_FENCE_GATE", "_FENCE", "_TRAPDOOR", "_PLANKS", "_LOG", "_WOOD", "_STEM", "_HYPHAE", "_BLOCK"}) {
            if (stem.endsWith(end)) {
                stem = stem.substring(0, stem.length() - end.length());
                break;
            }
        }
        if (stem.endsWith("BRICKS") || stem.endsWith("TILES")) stem = stem.substring(0, stem.length() - 1);
        return stem;
    }

    private static Material first(String... names) {
        for (String name : names) {
            if (name == null) continue;
            Material material = Material.getMaterial(name);
            if (material != null && material.isBlock() && !material.isAir()) return material;
        }
        return null;
    }
}
