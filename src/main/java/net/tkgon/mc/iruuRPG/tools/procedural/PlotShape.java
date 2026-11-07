package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.plot.Cell;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Grid;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Ground;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Kit;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Patterns;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Plan;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.PlotGenerator;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Role;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * (t313) The building of lots in the procedural tool: the settings made a plan (the lots as they are set, or a building made from a pattern), the building
 * made (its front toward the player, turned by any degree as the house base is), and its blocks by part as every object of the tool is (so the preview, the
 * placing, //undo and the presets are shared). A block's role is its part; its shape (a stair, a slab, a log lying or standing, a wall, a fence, a trapdoor,
 * a door) and its states go with the blocks ({@link Blocks}): a stair of the roof is the stairs of the block chosen for the roof, and so on ({@link #form}).
 */
final class PlotShape {

    private PlotShape() {
    }

    /** The kit the buildings are made of (set by {@link KitStore}; the kit's own parts until it is read). */
    private static volatile Kit kit = Kit.builtin();
    /** The lots' foundations and walls kept between previews, so a change remakes only the lots it touches. */
    static final PlotGenerator.Cache CACHE = new PlotGenerator.Cache();

    static Kit kit() {
        return kit;
    }

    static void useKit(Kit next) {
        if (next != null) kit = next;
    }

    /** The lot a new building of lots starts with: a main block of two storeys, its door at the front. */
    static Plan.Lot starter() {
        Plan.Lot main = new Plan.Lot("main", Plan.Shape.RECT, 8, 8, 13, 9, 2);
        main.floors.get(0).door = 2;
        return main;
    }

    // ---- the numbers ----------------------------------------------------------------------------------------------------------

    static boolean named(TreeSettings.Key key) {
        return switch (key) {
            case PLOT_MODE, PLOT_PATTERN, PLOT_STEEP, PLOT_ROOF_LOOK, PLOT_SHAPES, PLOT_PALETTE -> true;
            default -> false;
        };
    }

    /** The name of a number of a building of lots (null: shown as a number). */
    static String valueName(TreeSettings.Key key, double value) {
        int v = (int) Math.round(value);
        return switch (key) {
            case PLOT_MODE -> pick(v, "区画どおり", "毎回おまかせ");
            case PLOT_PATTERN -> v == 0 ? "どれか" : Patterns.Pattern.of(v).label;
            case PLOT_STEEP -> Plan.PITCH_NAMES[Math.max(0, Math.min(Plan.PITCH_NAMES.length - 1, v))];
            case PLOT_ROOF_LOOK -> pick(v, "階段・ハーフ", "フルブロック");
            case PLOT_SHAPES -> pick(v, "フルブロックだけ", "階段・ハーフ・フェンスも");
            case PLOT_PALETTE -> pick(v, "いまのまま", "羊毛 (役割ごと)", "木組みと漆喰", "石の館");
            default -> null;
        };
    }

    static String pick(int index, String... names) {
        return names[Math.max(0, Math.min(names.length - 1, index))];
    }

    /** The plan of the lots as they are set. */
    static Plan plan(TreeSettings s) {
        Plan plan = new Plan();
        plan.siteWidth = s.getInt(TreeSettings.Key.PLOT_SITE_W);
        plan.siteDepth = s.getInt(TreeSettings.Key.PLOT_SITE_D);
        plan.module = s.getInt(TreeSettings.Key.PLOT_MODULE);
        for (Plan.Lot lot : s.lots()) plan.lots.add(lot.copy());
        if (plan.lots.isEmpty()) plan.lots.add(starter());
        return plan;
    }

    /** What the settings give the maker of buildings by themselves. */
    static Patterns.Rules rules(TreeSettings s) {
        Patterns.Rules rules = new Patterns.Rules();
        rules.siteWidth = s.getInt(TreeSettings.Key.PLOT_SITE_W);
        rules.siteDepth = s.getInt(TreeSettings.Key.PLOT_SITE_D);
        rules.module = s.getInt(TreeSettings.Key.PLOT_MODULE);
        rules.stilts = s.get(TreeSettings.Key.PLOT_STILTS);
        rules.detail = s.getInt(TreeSettings.Key.PLOT_DETAIL);
        rules.steepest = s.getInt(TreeSettings.Key.PLOT_STEEP);
        return rules;
    }

    /** A building made by itself from the settings' pattern with {@code seed} (on flat ground: its plan is what is kept). */
    static Patterns.Design design(TreeSettings s, long seed) {
        int pattern = s.getInt(TreeSettings.Key.PLOT_PATTERN);
        return Patterns.design(Patterns.Pattern.of(pattern), rules(s), kit, seed, s.getInt(TreeSettings.Key.PLOT_CANDIDATES), s.getInt(TreeSettings.Key.PLOT_RETRIES),
                Ground.FLAT);
    }

    /** Writes a plan made by itself into the settings (its site, module and lots), so it can be changed by hand. */
    static void take(TreeSettings s, Plan plan) {
        s.set(TreeSettings.Key.PLOT_SITE_W, plan.siteWidth);
        s.set(TreeSettings.Key.PLOT_SITE_D, plan.siteDepth);
        s.set(TreeSettings.Key.PLOT_MODULE, plan.module);
        s.lots().clear();
        for (Plan.Lot lot : plan.lots) s.lots().add(lot.copy());
    }

    /** How far round the click the terrain is read: as far as the site reaches (24 at least, 48 at most). */
    static int reach(TreeSettings s) {
        int site = Math.max(s.getInt(TreeSettings.Key.PLOT_SITE_W), s.getInt(TreeSettings.Key.PLOT_SITE_D));
        return Math.max(24, Math.min(48, site / 2 + 8));
    }

    static HouseShape.Placement placement(TreeSettings s, Vector aim) {
        return HouseShape.Placement.of(aim, s.getInt(TreeSettings.Key.PLOT_ROTATION), s.getInt(TreeSettings.Key.PLOT_SITE_W), s.getInt(TreeSettings.Key.PLOT_SITE_D));
    }

    // ---- the building -----------------------------------------------------------------------------------------------------------

    /**
     * Makes the building of the settings at the click (the terrain {@code ground} round it; null: flat), its front toward {@code aim}: the lots as set, or
     * (made each time, "毎回おまかせ") a building of the settings' pattern made from {@code seed}.
     */
    static Blocks build(TreeSettings s, long seed, TreeGround ground, Vector aim) {
        HouseShape.Placement placement = placement(s, aim);
        Ground terrain = ground == null ? Ground.FLAT : (x, z) -> {
            int[] at = placement.block(x, z);
            int top = ground.top(at[0], at[1]);
            return top == TreeGround.NONE ? Ground.NONE : top;
        };
        PlotGenerator.Result result;
        String pattern = "";
        try {
            if (s.getInt(TreeSettings.Key.PLOT_MODE) == 1) {
                Patterns.Design design = design(s, seed);
                result = PlotGenerator.build(design.plan(), kit, seed, terrain, CACHE);
                result = new PlotGenerator.Result(result.grid(), result.layout(), result.problems(), result.notes(), design.result().score(), design.pattern().label,
                        design.tries(), result.rebuilt());
                pattern = design.pattern().label;
            } else {
                result = PlotGenerator.build(plan(s), kit, seed, terrain, CACHE);
            }
        } catch (Grid.TooBig exception) {
            throw new TreeVoxelizer.TooBig();
        }
        Blocks blocks = new Blocks();
        blocks.roofFull = s.getInt(TreeSettings.Key.PLOT_ROOF_LOOK) == 1;
        blocks.shapesFull = s.getInt(TreeSettings.Key.PLOT_SHAPES) == 0;
        int turns = Math.floorMod((int) Math.round(placement.degrees() / 90.0), 4);
        Grid grid = result.grid();
        if (placement.square()) {
            for (Map.Entry<Long, Cell> entry : grid.cells().entrySet()) {
                long key = entry.getKey();
                int[] at = placement.block(Grid.x(key), Grid.z(key));
                put(blocks, grid, key, TreeVoxelizer.pack(at[0], Grid.y(key), at[1]), entry.getValue().turned(turns), turns);
            }
        } else {
            // (turned by other than a right angle: every block of the world over it takes the column of the building nearest it, so no holes open)
            Map<Long, List<Long>> columns = new HashMap<>();
            int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (long key : grid.cells().keySet()) {
                int x = Grid.x(key);
                int z = Grid.z(key);
                columns.computeIfAbsent(TreeVoxelizer.pack(x, 0, z), unused -> new ArrayList<>()).add(key);
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
                    for (long key : column) put(blocks, grid, key, TreeVoxelizer.pack(wx, Grid.y(key), wz), grid.cells().get(key).turned(turns), turns);
                }
            }
        }
        blocks.pattern = pattern;
        blocks.problems = result.problems();
        blocks.notes = result.notes();
        blocks.score = result.score();
        blocks.tries = result.tries();
        blocks.rebuilt = result.rebuilt();
        blocks.lots = result.layout().lots.size();
        return blocks;
    }

    private static void put(Blocks blocks, Grid grid, long key, long world, Cell cell, int turns) {
        blocks.put(world, part(cell.role()));
        blocks.cells.put(world, cell);
        String state = grid.literalAt(key);
        if (state != null) {
            blocks.literals.put(world, state);
            blocks.turns = turns;
        }
    }

    /** The part a role's blocks are. */
    static TreePart part(Role role) {
        return switch (role) {
            case FRAME -> TreePart.PLOT_FRAME;
            case BEAM -> TreePart.PLOT_BEAM;
            case BRACE -> TreePart.PLOT_BRACE;
            case PLINTH -> TreePart.PLOT_PLINTH;
            case STONE -> TreePart.PLOT_STONE;
            case INFILL -> TreePart.PLOT_INFILL;
            case INFILL_2 -> TreePart.PLOT_INFILL_2;
            case INFILL_3 -> TreePart.PLOT_INFILL_3;
            case WINDOW -> TreePart.PLOT_WINDOW;
            case DOOR -> TreePart.PLOT_DOOR;
            case FLOOR -> TreePart.PLOT_FLOOR;
            case CORBEL -> TreePart.PLOT_CORBEL;
            case ROOF -> TreePart.PLOT_ROOF;
            case EAVE -> TreePart.PLOT_EAVE;
            case RIDGE -> TreePart.PLOT_RIDGE;
            case FLASHING -> TreePart.PLOT_FLASHING;
            case CHIMNEY -> TreePart.PLOT_CHIMNEY;
            case RAIL -> TreePart.PLOT_RAIL;
            case STAIR -> TreePart.PLOT_STAIR;
            case FINIAL -> TreePart.PLOT_FINIAL;
            case SAIL -> TreePart.PLOT_SAIL;
            case BELL -> TreePart.PLOT_BELL;
        };
    }

    /** What the checks and the score said, for the messages. */
    static String report(Blocks blocks) {
        String checks = blocks.problems.isEmpty() ? "検証 OK" : "検証で残った問題: " + String.join(" / ", blocks.problems);
        String made = blocks.pattern.isEmpty() ? "区画 " + blocks.lots + " (作り直し " + blocks.rebuilt + ")" : blocks.pattern + " / 試した数 " + blocks.tries;
        String notes = blocks.notes.isEmpty() ? "" : " / " + String.join(" / ", blocks.notes);
        return made + " / 点 " + String.format(Locale.ROOT, "%.2f", blocks.score) + " / " + checks + notes;
    }

    // ---- the palettes ----------------------------------------------------------------------------------------------------------

    /** Sets the blocks of every role at once (1: wool by role, the defaults; 2: timber and plaster; 3: a stone hall); 0 changes nothing. */
    static void applyPalette(TreeSettings s, int palette) {
        if (palette <= 0) return;
        for (TreePart part : ProcKind.PLOT.parts()) s.setBlock(part, null);
        if (palette == 1) return;
        String[][] blocks = palette == 2 ? new String[][]{
                {"PLOT_FRAME", "DARK_OAK_LOG"}, {"PLOT_BEAM", "DARK_OAK_LOG"}, {"PLOT_BRACE", "DARK_OAK_WOOD"}, {"PLOT_PLINTH", "COBBLESTONE"},
                {"PLOT_STONE", "STONE_BRICKS"}, {"PLOT_INFILL", "WHITE_TERRACOTTA"}, {"PLOT_INFILL_2", "SMOOTH_SANDSTONE"}, {"PLOT_INFILL_3", "BIRCH_PLANKS"},
                {"PLOT_WINDOW", "GLASS"}, {"PLOT_DOOR", "SPRUCE_DOOR"}, {"PLOT_FLOOR", "SPRUCE_PLANKS"}, {"PLOT_CORBEL", "DARK_OAK_PLANKS"},
                {"PLOT_ROOF", "DEEPSLATE_TILES"}, {"PLOT_EAVE", "DARK_OAK_PLANKS"}, {"PLOT_RIDGE", "POLISHED_DEEPSLATE"}, {"PLOT_FLASHING", "DEEPSLATE_TILES"},
                {"PLOT_CHIMNEY", "BRICKS"}, {"PLOT_RAIL", "DARK_OAK_FENCE"}, {"PLOT_STAIR", "SPRUCE_PLANKS"}, {"PLOT_FINIAL", "DARK_OAK_FENCE"},
                {"PLOT_SAIL", "WHITE_WOOL"}, {"PLOT_BELL", "BELL"}}
                : new String[][]{
                {"PLOT_FRAME", "STRIPPED_SPRUCE_LOG"}, {"PLOT_BEAM", "SPRUCE_LOG"}, {"PLOT_BRACE", "SPRUCE_WOOD"}, {"PLOT_PLINTH", "STONE_BRICKS"},
                {"PLOT_STONE", "STONE_BRICKS"}, {"PLOT_INFILL", "CALCITE"}, {"PLOT_INFILL_2", "SMOOTH_QUARTZ"}, {"PLOT_INFILL_3", "MUD_BRICKS"},
                {"PLOT_WINDOW", "GLASS"}, {"PLOT_DOOR", "DARK_OAK_DOOR"}, {"PLOT_FLOOR", "STONE_BRICKS"}, {"PLOT_CORBEL", "STONE_BRICKS"},
                {"PLOT_ROOF", "BRICKS"}, {"PLOT_EAVE", "MUD_BRICKS"}, {"PLOT_RIDGE", "COBBLED_DEEPSLATE"}, {"PLOT_FLASHING", "BRICKS"},
                {"PLOT_CHIMNEY", "STONE_BRICKS"}, {"PLOT_RAIL", "STONE_BRICK_WALL"}, {"PLOT_STAIR", "STONE_BRICKS"}, {"PLOT_FINIAL", "IRON_BARS"},
                {"PLOT_SAIL", "WHITE_WOOL"}, {"PLOT_BELL", "BELL"}};
        for (String[] pair : blocks) s.setBlock(TreePart.valueOf(pair[0]), pair[1]);
    }

    // ---- the blocks ------------------------------------------------------------------------------------------------------------

    /**
     * The blocks of a building, by part as every object's are, with each block's shape and states. When it is shown or put, {@link #data} makes a block: the block
     * chosen for its part in the shape the block is ({@link #form}), with its states; a template's own block as it is (turned with the building).
     */
    static final class Blocks extends HashMap<Long, TreePart> {

        final Map<Long, Cell> cells = new HashMap<>();
        final Map<Long, String> literals = new HashMap<>();
        int turns;
        String pattern = "";
        List<String> problems = List.of();
        List<String> notes = List.of();
        double score;
        int tries;
        int rebuilt;
        int lots;
        boolean roofFull;
        boolean shapesFull;
        private final Map<String, BlockData> made = new HashMap<>();

        /** The block at (x, y, z) made of {@code chosen} (the part's block), or null when there is none. */
        BlockData data(BlockData chosen, TreePart part, int x, int y, int z) {
            long key = TreeVoxelizer.pack(x, y, z);
            String state = literals.get(key);
            if (state != null) return literal(state);
            Cell cell = cells.get(key);
            if (cell == null || chosen == null) return null;
            Cell.Form shape = cell.form();
            boolean keep = shape == Cell.Form.FULL || shape == Cell.Form.LOG || shape == Cell.Form.DOOR || shape == Cell.Form.BELL || shape == Cell.Form.LADDER
                    || shape == Cell.Form.ROD;
            boolean full = !keep && (cell.role().roof() ? roofFull : shapesFull);
            Material material = full ? form(chosen.getMaterial(), Cell.Form.FULL, part.defaultMaterial()) : form(chosen.getMaterial(), shape, part.defaultMaterial());
            String props = full ? "" : cell.props();
            String name = material.name() + "[" + props + "]";
            BlockData data = made.get(name);
            if (data == null) {
                data = create(material, props, chosen);
                made.put(name, data);
            }
            return data;
        }

        private BlockData literal(String state) {
            BlockData data = made.get("literal:" + state);
            if (data != null) return data;
            // (a template's block turned to its face ("rot:<turns>|"), then with the building)
            int quarter = turns;
            String plain = state;
            if (state.startsWith("rot:") && state.indexOf('|') > 4) {
                quarter += Integer.parseInt(state.substring(4, state.indexOf('|')));
                plain = state.substring(state.indexOf('|') + 1);
            }
            try {
                data = Bukkit.createBlockData(plain);
                if (Math.floorMod(quarter, 4) != 0) data.rotate(switch (Math.floorMod(quarter, 4)) {
                    case 1 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_90;
                    case 2 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_180;
                    default -> org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;
                });
            } catch (IllegalArgumentException exception) {
                data = Material.STONE.createBlockData();
            }
            made.put("literal:" + state, data);
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
        SUFFIX.put(Cell.Form.DOOR, "_DOOR");
    }

    /**
     * The block of {@code chosen}'s family in the shape {@code form}: its stairs, slab, wall, fence, trapdoor or door (by name: the planks of a wood, the
     * bricks and tiles without their s), its log, its full block. When the chosen block has none, the stairs (and so on) of the colour nearest it for a wool;
     * else {@code fallback}'s (the part's default) in that shape, so the shape stays; when that has none either, the chosen block itself. A lightning rod, a
     * ladder and a bell are always themselves.
     */
    static Material form(Material chosen, Cell.Form form, Material fallback) {
        if (form == Cell.Form.ROD) return Material.LIGHTNING_ROD;
        if (form == Cell.Form.LADDER) return Material.LADDER;
        if (form == Cell.Form.BELL) return Material.BELL;
        Material own = family(chosen, form);
        if (own != null) return own;
        Material near = nearest(chosen, form);
        if (near != null) return near;
        Material other = fallback == null ? null : family(fallback, form);
        if (other != null) return other;
        Material otherNear = fallback == null ? null : nearest(fallback, form);
        if (otherNear != null) return otherNear;
        return form == Cell.Form.DOOR ? Material.SPRUCE_DOOR : chosen;
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

    /** The block of shape {@code form} whose colour is nearest a wool's (null: not a wool, or a full block or a log, which a wool is already). */
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
            case DOOR -> name.endsWith("_DOOR") ? material : first(stem + "_DOOR");
            default -> name.endsWith(SUFFIX.get(form)) && !(form == Cell.Form.FENCE && name.endsWith("_FENCE_GATE")) ? material : first(stem + SUFFIX.get(form));
        };
    }

    private static boolean isShaped(String name) {
        return name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_WALL") || name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE")
                || name.endsWith("_TRAPDOOR") || name.endsWith("_DOOR");
    }

    /** The name of a block's family: without stripped, without its shape's ending, a bricks' or tiles' s dropped (stone bricks: STONE_BRICK). */
    static String stem(String name) {
        String stem = name.startsWith("STRIPPED_") ? name.substring(9) : name;
        for (String end : new String[]{"_STAIRS", "_SLAB", "_WALL", "_FENCE_GATE", "_FENCE", "_TRAPDOOR", "_DOOR", "_PLANKS", "_LOG", "_WOOD", "_STEM", "_HYPHAE", "_BLOCK"}) {
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
