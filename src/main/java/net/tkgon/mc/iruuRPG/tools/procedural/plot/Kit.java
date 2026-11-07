package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * (t313, spec 3/6) The parts the buildings are put together from, as a fantasy building kit has them: wall panels, gables, posts and corners, round walls,
 * foundations, roofs, the roof's parts and the parts outside. A part is one of three kinds:
 * <ul>
 * <li>a template: blocks as they were registered in the game (in a role, so they take the palette, or as they are), with its anchors (named points it is put
 * by) and the sizes it fits;</li>
 * <li>a 9-slice template: the same, whose corners and sides stay and whose middle is repeated to fit any size;</li>
 * <li>procedural: made from its numbers (a panel's fill, the lines of its timbers as rules such as "from the top to a quarter of the bottom", its opening).</li>
 * </ul>
 * Parts carry tags (the style, the storey, the colour), and the maker chooses them by their tags. The kit's own parts are written into the server's folder once
 * and may be changed there; the ones registered in the game are added beside them.
 */
public final class Kit {

    public enum Category {
        WALL("壁パネル"), GABLE("妻壁"), CORNER("角・柱"), ROUND_WALL("円形の壁"), BASE("土台"), ROOF("屋根"), ROOF_PART("屋根の付属物"), OUTSIDE("外部の部品");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public enum Kind {
        TEMPLATE("定型"), SLICE("9スライス"), PROCEDURAL("手続き型");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** The opening of a panel. */
    public enum Opening {
        NONE, WINDOW, DOOR, ROUND
    }

    /** A block of a template: in a role (its shape and states given) or as the game's block itself ({@code literal}). */
    public record Block(Role role, Cell.Form form, String props, String literal) {

        /** {@code role:INFILL}, {@code role:ROOF|stairs|facing=north,half=bottom,shape=straight}, or a block's state ({@code minecraft:lantern[hanging=true]}). */
        public static Block parse(String text) {
            if (text == null) return null;
            String value = text.trim();
            if (value.startsWith("role:")) {
                String[] parts = value.substring(5).split("\\|", 3);
                Role role = Role.byName(parts[0]);
                if (role == null) return null;
                Cell.Form form = parts.length > 1 ? Plan.named(Cell.Form.class, parts[1], Cell.Form.FULL) : Cell.Form.FULL;
                return new Block(role, form, parts.length > 2 ? parts[2] : "", null);
            }
            return value.isEmpty() ? null : new Block(null, null, null, value);
        }

        public String write() {
            if (role == null) return literal;
            return "role:" + role.name().toLowerCase(Locale.ROOT) + (form == Cell.Form.FULL && props.isEmpty() ? "" : "|" + form.name().toLowerCase(Locale.ROOT) + (props.isEmpty() ? "" : "|" + props));
        }
    }

    /** One part. */
    public static final class Part {
        public String id;
        public Category category = Category.WALL;
        public Kind kind = Kind.PROCEDURAL;
        public final Set<String> tags = new LinkedHashSet<>();
        /** The sizes it fits (width along the wall, height). */
        public int minW = 1;
        public int maxW = 9;
        public int minH = 1;
        public int maxH = 12;
        /** Its numbers (a dormer's width, a chimney's height over the ridge ...): the maker reads those it knows. */
        public final Map<String, Object> params = new LinkedHashMap<>();
        // ---- procedural ----
        public Role fill = Role.INFILL;
        public Role lineRole = Role.BRACE;
        /** Its lines, each {x0, y0, x1, y1} from 0 to 1 over the panel (x from the left, y from the bottom; a gable's top is its apex). */
        public final List<double[]> lines = new ArrayList<>();
        public Opening opening = Opening.NONE;
        public int openW = 1;
        public int openH = 2;
        // ---- template ----
        /** Its size along the wall (u), up (v) and into the wall (d: 0 the front layer, the next ones behind it). */
        public int sizeU;
        public int sizeV;
        public int sizeD;
        public final Map<Long, Block> blocks = new LinkedHashMap<>();
        public final Map<String, int[]> anchors = new LinkedHashMap<>();
        /** The columns and rows repeated to fit a size (a 9-slice), from and to; -1: none. */
        public final int[] sliceU = {-1, -1};
        public final int[] sliceV = {-1, -1};
        /** The kit's own part (written into the folder once). */
        public boolean builtin;

        public boolean fits(int w, int h) {
            if (kind == Kind.TEMPLATE) return w == sizeU && h == sizeV;
            return w >= minW && w <= maxW && h >= minH && h <= maxH;
        }

        public boolean tagged(String tag) {
            return tag == null || tag.isEmpty() || tags.contains(tag);
        }

        public double number(String name, double fallback) {
            Object value = params.get(name);
            if (value instanceof Number n) return n.doubleValue();
            if (value instanceof String text) {
                try {
                    return Double.parseDouble(text.trim());
                } catch (NumberFormatException exception) {
                    return fallback;
                }
            }
            return fallback;
        }

        public String text(String name, String fallback) {
            Object value = params.get(name);
            return value == null ? fallback : String.valueOf(value);
        }

        public static long key(int u, int v, int d) {
            return ((long) (u + 512) & 0x3FF) | (((long) (v + 512) & 0x3FF) << 10) | (((long) (d + 512) & 0x3FF) << 20);
        }

        public static int u(long key) {
            return (int) (key & 0x3FF) - 512;
        }

        public static int v(long key) {
            return (int) ((key >> 10) & 0x3FF) - 512;
        }

        public static int d(long key) {
            return (int) ((key >> 20) & 0x3FF) - 512;
        }

        public String summary() {
            String size = kind == Kind.TEMPLATE ? sizeU + "x" + sizeV + "x" + sizeD : minW + "〜" + maxW + " x " + minH + "〜" + maxH;
            return id + " (" + category.label + "・" + kind.label + ", " + size + (tags.isEmpty() ? "" : ", " + String.join(" ", tags)) + ")";
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("category", category.name().toLowerCase(Locale.ROOT));
            map.put("kind", kind.name().toLowerCase(Locale.ROOT));
            map.put("tags", new ArrayList<>(tags));
            map.put("size", List.of(minW, maxW, minH, maxH));
            if (!params.isEmpty()) map.put("params", new LinkedHashMap<>(params));
            if (kind == Kind.PROCEDURAL) {
                map.put("fill", fill.name().toLowerCase(Locale.ROOT));
                map.put("line-role", lineRole.name().toLowerCase(Locale.ROOT));
                List<String> rules = new ArrayList<>();
                for (double[] line : lines) rules.add(fmt(line[0]) + " " + fmt(line[1]) + " " + fmt(line[2]) + " " + fmt(line[3]));
                map.put("lines", rules);
                map.put("opening", opening.name().toLowerCase(Locale.ROOT));
                map.put("opening-size", List.of(openW, openH));
            } else {
                map.put("template-size", List.of(sizeU, sizeV, sizeD));
                Map<String, Object> list = new LinkedHashMap<>();
                for (Map.Entry<Long, Block> entry : blocks.entrySet()) {
                    long k = entry.getKey();
                    list.put(u(k) + "," + v(k) + "," + d(k), entry.getValue().write());
                }
                map.put("blocks", list);
                Map<String, Object> points = new LinkedHashMap<>();
                for (Map.Entry<String, int[]> entry : anchors.entrySet()) points.put(entry.getKey(), List.of(entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]));
                map.put("anchors", points);
                if (kind == Kind.SLICE) {
                    map.put("slice-u", List.of(sliceU[0], sliceU[1]));
                    map.put("slice-v", List.of(sliceV[0], sliceV[1]));
                }
            }
            return map;
        }

        public static Part fromMap(Map<?, ?> map) {
            Part part = new Part();
            Object id = map.get("id");
            if (id == null) return null;
            part.id = safeId(String.valueOf(id));
            part.category = Plan.named(Category.class, map.get("category"), Category.WALL);
            part.kind = Plan.named(Kind.class, map.get("kind"), Kind.PROCEDURAL);
            if (map.get("tags") instanceof List<?> list) for (Object tag : list) part.tags.add(String.valueOf(tag).trim().toLowerCase(Locale.ROOT));
            if (map.get("size") instanceof List<?> size && size.size() >= 4) {
                part.minW = Math.max(1, Plan.number(size.get(0), 1));
                part.maxW = Math.max(part.minW, Plan.number(size.get(1), 9));
                part.minH = Math.max(1, Plan.number(size.get(2), 1));
                part.maxH = Math.max(part.minH, Plan.number(size.get(3), 12));
            }
            Map<?, ?> params = Plan.asMap(map.get("params"));
            if (params != null) for (Map.Entry<?, ?> entry : params.entrySet()) part.params.put(String.valueOf(entry.getKey()), entry.getValue());
            if (part.kind == Kind.PROCEDURAL) {
                Role fill = Role.byName(String.valueOf(map.get("fill")));
                if (fill != null) part.fill = fill;
                Role lineRole = Role.byName(String.valueOf(map.get("line-role")));
                if (lineRole != null) part.lineRole = lineRole;
                if (map.get("lines") instanceof List<?> lines) {
                    for (Object rule : lines) {
                        double[] line = parseLine(String.valueOf(rule));
                        if (line != null) part.lines.add(line);
                    }
                }
                part.opening = Plan.named(Opening.class, map.get("opening"), Opening.NONE);
                if (map.get("opening-size") instanceof List<?> size && size.size() >= 2) {
                    part.openW = Math.max(1, Plan.number(size.get(0), 1));
                    part.openH = Math.max(1, Plan.number(size.get(1), 2));
                }
            } else {
                if (map.get("template-size") instanceof List<?> size && size.size() >= 3) {
                    part.sizeU = Math.max(1, Plan.number(size.get(0), 1));
                    part.sizeV = Math.max(1, Plan.number(size.get(1), 1));
                    part.sizeD = Math.max(1, Plan.number(size.get(2), 1));
                }
                Map<?, ?> blocks = Plan.asMap(map.get("blocks"));
                if (blocks != null) {
                    for (Map.Entry<?, ?> entry : blocks.entrySet()) {
                        String[] at = String.valueOf(entry.getKey()).split(",");
                        Block block = Block.parse(String.valueOf(entry.getValue()));
                        if (at.length != 3 || block == null) continue;
                        try {
                            part.blocks.put(key(Integer.parseInt(at[0].trim()), Integer.parseInt(at[1].trim()), Integer.parseInt(at[2].trim())), block);
                        } catch (NumberFormatException exception) {
                            // (a broken entry is left out)
                        }
                    }
                }
                Map<?, ?> anchors = Plan.asMap(map.get("anchors"));
                if (anchors != null) {
                    for (Map.Entry<?, ?> entry : anchors.entrySet()) {
                        if (entry.getValue() instanceof List<?> at && at.size() >= 3) {
                            part.anchors.put(String.valueOf(entry.getKey()), new int[]{Plan.number(at.get(0), 0), Plan.number(at.get(1), 0), Plan.number(at.get(2), 0)});
                        }
                    }
                }
                ints(map.get("slice-u"), part.sliceU);
                ints(map.get("slice-v"), part.sliceV);
                if (part.kind == Kind.SLICE && (part.sliceU[0] < 0 && part.sliceV[0] < 0)) part.kind = Kind.TEMPLATE;
                if (part.kind == Kind.TEMPLATE) {
                    part.minW = part.maxW = part.sizeU;
                    part.minH = part.maxH = part.sizeV;
                }
            }
            return part;
        }

        private static void ints(Object value, int[] into) {
            if (value instanceof List<?> list && list.size() >= 2) {
                into[0] = Plan.number(list.get(0), -1);
                into[1] = Plan.number(list.get(1), -1);
            }
        }
    }

    /** A part's id made safe (letters, digits, - and _, 40 at most), or "part". */
    public static String safeId(String id) {
        String clean = id == null ? "" : id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (clean.length() > 40) clean = clean.substring(0, 40);
        return clean.isEmpty() ? "part" : clean;
    }

    /** A line rule {@code "0.5 1 0.25 0"} (x0 y0 x1 y1), or null. */
    public static double[] parseLine(String text) {
        String[] words = text.trim().split("[\\s,]+");
        if (words.length != 4) return null;
        double[] line = new double[4];
        try {
            for (int index = 0; index < 4; index++) line[index] = Math.max(0.0, Math.min(1.0, Double.parseDouble(words[index])));
        } catch (NumberFormatException exception) {
            return null;
        }
        return line;
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "");
    }

    // ---- the kit ---------------------------------------------------------------------------------------------------------------

    private final Map<String, Part> parts = new LinkedHashMap<>();
    /** A new number at every change (of any kit), so what was made from the kit before is not kept. */
    private int version;
    private static final java.util.concurrent.atomic.AtomicInteger CHANGES = new java.util.concurrent.atomic.AtomicInteger();

    public Kit() {
    }

    public int version() {
        return version;
    }

    public void add(Part part) {
        if (part != null && part.id != null) {
            parts.put(part.id, part);
            version = CHANGES.incrementAndGet();
        }
    }

    public Part get(String id) {
        return id == null ? null : parts.get(id);
    }

    public boolean remove(String id) {
        version = CHANGES.incrementAndGet();
        return parts.remove(id) != null;
    }

    public List<Part> all() {
        return new ArrayList<>(parts.values());
    }

    /** The parts of a category with all the tags given (null or empty: any). */
    public List<Part> find(Category category, String... tags) {
        List<Part> out = new ArrayList<>();
        for (Part part : parts.values()) {
            if (part.category != category) continue;
            boolean ok = true;
            for (String tag : tags) if (!part.tagged(tag)) ok = false;
            if (ok) out.add(part);
        }
        return out;
    }

    /** A part of the category with the tags that fits the size, chosen by {@code random} among the fitting ones (the templates first); null when none fits. */
    public Part pick(Category category, int w, int h, Random random, String... tags) {
        List<Part> fit = new ArrayList<>();
        List<Part> registered = new ArrayList<>();
        for (Part part : find(category, tags)) {
            if (!part.fits(w, h)) continue;
            fit.add(part);
            if (!part.builtin) registered.add(part);
        }
        List<Part> from = registered.isEmpty() ? fit : registered;
        if (from.isEmpty()) return null;
        return from.get(random == null ? 0 : random.nextInt(from.size()));
    }

    /** The kit with every part of the kit's own (the folder adds to it and changes it). */
    public static Kit builtin() {
        Kit kit = new Kit();
        for (Part part : Builtin.parts()) {
            part.builtin = true;
            kit.add(part);
        }
        return kit;
    }

    public Kit copy() {
        Kit copy = new Kit();
        copy.parts.putAll(parts);
        copy.version = version;
        return copy;
    }

    /** The kit's own parts, as the folder starts with them. */
    static final class Builtin {

        private Builtin() {
        }

        static List<Part> parts() {
            List<Part> out = new ArrayList<>();
            // ---- wall panels: plaster, stone, timber (stud, brace, cross), with a window, with a door ----
            out.add(panel("plaster", Role.INFILL, Opening.NONE, "plaster", "solid", "any"));
            out.add(panel("plaster_window", Role.INFILL, Opening.WINDOW, "plaster", "window", "any"));
            out.add(panel("plaster_door", Role.INFILL, Opening.DOOR, "plaster", "door", "ground"));
            out.add(panel("stone", Role.STONE, Opening.NONE, "stone", "solid", "ground"));
            out.add(panel("stone_window", Role.STONE, Opening.WINDOW, "stone", "window", "ground"));
            out.add(panel("stone_door", Role.STONE, Opening.DOOR, "stone", "door", "ground"));
            out.add(panel("timber_stud", Role.INFILL, Opening.NONE, "timber", "solid", "upper", "0.5 0 0.5 1"));
            out.add(panel("timber_brace", Role.INFILL, Opening.NONE, "timber", "solid", "upper", "0 0 1 1"));
            out.add(panel("timber_cross", Role.INFILL, Opening.NONE, "timber", "solid", "upper", "0 0 1 1", "1 0 0 1"));
            out.add(panel("timber_window", Role.INFILL, Opening.WINDOW, "timber", "window", "upper"));
            out.add(panel("timber_door", Role.INFILL, Opening.DOOR, "timber", "door", "any"));
            out.add(panel("cross_brace", Role.INFILL, Opening.NONE, "cross", "solid", "upper", "0 0 1 1", "1 0 0 1"));
            out.add(panel("cross_window", Role.INFILL, Opening.WINDOW, "cross", "window", "upper"));
            out.add(panel("cross_door", Role.INFILL, Opening.DOOR, "cross", "door", "any"));
            out.add(panel("brace_brace", Role.INFILL, Opening.NONE, "brace", "solid", "upper", "0 0 1 1"));
            out.add(panel("brace_window", Role.INFILL, Opening.WINDOW, "brace", "window", "upper"));
            out.add(panel("brace_door", Role.INFILL, Opening.DOOR, "brace", "door", "any"));
            Part round = panel("plaster_round_window", Role.INFILL, Opening.ROUND, "plaster", "window", "upper");
            round.tags.add("round-window");
            round.minW = 3;
            round.minH = 4;
            out.add(round);
            // ---- gables (triangles): plaster, timber, stone, by the lines drawn in them ----
            out.add(gable("gable_plaster", Role.INFILL, "plaster", "0.5 1 0.5 0"));
            out.add(gable("gable_timber", Role.INFILL, "timber", "0.5 1 0.5 0", "0.5 1 0.25 0", "0.5 1 0.75 0"));
            out.add(gable("gable_cross", Role.INFILL, "cross", "0.5 1 0.5 0", "0 0.5 1 0.5"));
            out.add(gable("gable_brace", Role.INFILL, "brace", "0.5 1 0.5 0", "0.25 0 0.5 0.6", "0.75 0 0.5 0.6"));
            out.add(gable("gable_stone", Role.STONE, "stone"));
            // ---- posts and corners: the corner post, a stone corner, a post, a beam, a brace, a curved bracket ----
            out.add(param(part("corner_post", Category.CORNER, "post", "corner"), "section", 1));
            out.add(param(part("corner_stone", Category.CORNER, "stone", "corner"), "every", 2));
            out.add(param(part("post", Category.CORNER, "post"), "section", 1));
            out.add(part("beam", Category.CORNER, "beam"));
            out.add(part("brace", Category.CORNER, "brace"));
            out.add(param(part("bracket_curved", Category.CORNER, "bracket"), "out", 1));
            // ---- round walls (towers): the arcs of a circle's walls ----
            out.add(arc("arc_plaster", Role.INFILL, Opening.NONE, "plaster", "solid"));
            out.add(arc("arc_window", Role.INFILL, Opening.WINDOW, "plaster", "window"));
            out.add(arc("arc_door", Role.INFILL, Opening.DOOR, "plaster", "door"));
            out.add(arc("arc_stone", Role.STONE, Opening.NONE, "stone", "solid"));
            out.add(arc("arc_stone_window", Role.STONE, Opening.WINDOW, "stone", "window"));
            out.add(arc("arc_stone_door", Role.STONE, Opening.DOOR, "stone", "door"));
            // ---- foundations: a stone plinth (its ledge), boards, cobbles ----
            out.add(param(part("plinth_stone", Category.BASE, "plinth"), "out", 1));
            out.add(part("floor_boards", Category.BASE, "floor"));
            out.add(part("cobbles", Category.BASE, "cobbles"));
            // ---- the roof: its face, the ridge's cap, the eaves, a cone or a spire ----
            out.add(param(param(part("roof_face", Category.ROOF, "face"), "thickness", 2), "look", "stairs"));
            out.add(param(part("ridge_cap", Category.ROOF, "ridge"), "style", "slab"));
            out.add(param(part("eave_edge", Category.ROOF, "eave"), "corbels", 1));
            out.add(param(part("spire", Category.ROOF, "cone"), "tip", 2));
            // ---- the roof's parts: dormers (a gable, a round window, a shed), a chimney, a weathervane, a finial ----
            out.add(param(param(part("dormer_gable", Category.ROOF_PART, "dormer", "gable"), "width", 5), "height", 4));
            out.add(param(param(part("dormer_round", Category.ROOF_PART, "dormer", "round"), "width", 5), "height", 4));
            out.add(param(param(part("dormer_shed", Category.ROOF_PART, "dormer", "shed"), "width", 5), "height", 3));
            out.add(param(param(part("chimney", Category.ROOF_PART, "chimney"), "width", 2), "above", 2));
            out.add(param(part("weathervane", Category.ROOF_PART, "weathervane"), "height", 3));
            out.add(param(part("finial", Category.ROOF_PART, "finial"), "height", 2));
            // ---- outside: stairs, a ladder, rails (a fence, a trapdoor), a balcony, a porch ----
            out.add(param(part("stairs", Category.OUTSIDE, "stairs"), "landing", 1));
            out.add(part("ladder", Category.OUTSIDE, "ladder"));
            out.add(param(part("rail_fence", Category.OUTSIDE, "rail", "fence"), "style", "fence"));
            out.add(param(part("rail_trapdoor", Category.OUTSIDE, "rail", "trapdoor"), "style", "trapdoor"));
            out.add(param(part("balcony", Category.OUTSIDE, "balcony"), "depth", 1));
            out.add(param(param(part("porch", Category.OUTSIDE, "porch"), "depth", 3), "width", 5));
            return out;
        }

        private static Part panel(String id, Role fill, Opening opening, String set, String role, String storey, String... lines) {
            Part part = part(id, Category.WALL, set, role, storey);
            part.fill = fill;
            part.opening = opening;
            part.openW = 1;
            part.openH = 2;
            part.minW = opening == Opening.NONE ? 1 : opening == Opening.DOOR ? 1 : 1;
            part.maxW = 9;
            part.minH = opening == Opening.NONE ? 1 : 3;
            part.maxH = 12;
            for (String line : lines) part.lines.add(parseLine(line));
            return part;
        }

        private static Part gable(String id, Role fill, String set, String... lines) {
            Part part = part(id, Category.GABLE, set);
            part.fill = fill;
            part.maxW = 99;
            part.maxH = 99;
            for (String line : lines) part.lines.add(parseLine(line));
            return part;
        }

        private static Part arc(String id, Role fill, Opening opening, String set, String role) {
            Part part = part(id, Category.ROUND_WALL, set, role);
            part.fill = fill;
            part.opening = opening;
            part.minH = opening == Opening.NONE ? 1 : 3;
            return part;
        }

        private static Part part(String id, Category category, String... tags) {
            Part part = new Part();
            part.id = id;
            part.category = category;
            for (String tag : tags) part.tags.add(tag);
            return part;
        }

        private static Part param(Part part, String name, Object value) {
            part.params.put(name, value);
            return part;
        }
    }
}
