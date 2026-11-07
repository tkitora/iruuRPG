package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * (t313) A building as nekorozv's spec has it: a site (its width and depth), the module (the width of a wall panel with its post) and the lots (区画) standing
 * on it, each a rectangle, a circle or an octagon with its own foundation, storeys and roof. Nothing else is shared: each lot's heights are its own. The palette
 * (the block of each role) is the tool's. Coordinates: x along the front from its left, z from the back to the front (the front, +z, toward the player).
 */
public final class Plan {

    public int siteWidth = 25;
    public int siteDepth = 21;
    public int module = 4;
    public final List<Lot> lots = new ArrayList<>();

    /** How many lots a building may have. */
    public static final int MAX_LOTS = 12;
    public static final int MAX_FLOORS = 8;

    public enum Shape {
        RECT("長方形"), ROUND("円"), OCTAGON("八角形");

        public final String label;

        Shape(String label) {
            this.label = label;
        }
    }

    public enum Foundation {
        PLINTH("石の基礎"), STILTS("高床の柱"), NONE("なし");

        public final String label;

        Foundation(String label) {
            this.label = label;
        }
    }

    /** How the windows of a storey are set in its panels. */
    public enum Windows {
        ALL("全部"), ALTERNATE("1つおき"), CENTER("中央対称"), ENDS("両端"), NONE("なし");

        public final String label;

        Windows(String label) {
            this.label = label;
        }
    }

    public enum RoofType {
        GABLE("切妻"), HIP("寄棟"), HALF_HIP("半切妻"), SHED("片流れ"), PYRAMID("四角錐"), CONE("円錐"), FLAT("平屋根"), NONE("なし");

        public final String label;

        RoofType(String label) {
            this.label = label;
        }
    }

    /** The slopes a roof may have (rise per block in): 1:2, 1:1, 2:1, and 3:1, 4:1 for towers. */
    public static final double[] PITCHES = {0.5, 1.0, 2.0, 3.0, 4.0};
    public static final String[] PITCH_NAMES = {"1:2", "1:1", "2:1", "3:1", "4:1"};

    /** Where a chimney stands. */
    public static final String[] CHIMNEYS = {"なし", "棟のそば", "左の端", "右の端", "外の壁 (左)", "外の壁 (右)"};

    /** The parts hung at a face of a lot. */
    public enum Special {
        NONE("なし"), WINDMILL("風車の羽根"), BELL("鐘");

        public final String label;

        Special(String label) {
            this.label = label;
        }
    }

    /** One storey of a lot. Sides are 0 north (the back), 1 east, 2 south (the front), 3 west. */
    public static final class Floor {
        public int height = 5;
        /** The panels of its walls: "auto" (by the storey: stone and the door below, timber and windows above) or a tag of the kit's panels. */
        public String panels = "auto";
        /** How far each side juts out over the storey below (0 or 1). */
        public final int[] jetty = new int[4];
        public Windows windows = Windows.ALTERNATE;
        public final boolean[] balcony = new boolean[4];
        /** A little roof round the top of the storey. */
        public boolean skirt;
        /** The side with the door (-1: none). */
        public int door = -1;
        /** Outside stairs from the door down to the ground (a storey over the ground's). */
        public boolean stairs;
        /** The colour of its plaster (0 to 2). */
        public int color;

        public Floor copy() {
            Floor copy = new Floor();
            copy.height = height;
            copy.panels = panels;
            System.arraycopy(jetty, 0, copy.jetty, 0, 4);
            copy.windows = windows;
            System.arraycopy(balcony, 0, copy.balcony, 0, 4);
            copy.skirt = skirt;
            copy.door = door;
            copy.stairs = stairs;
            copy.color = color;
            return copy;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("height", height);
            map.put("panels", panels);
            map.put("jetty", List.of(jetty[0], jetty[1], jetty[2], jetty[3]));
            map.put("windows", windows.name().toLowerCase(Locale.ROOT));
            map.put("balcony", List.of(balcony[0], balcony[1], balcony[2], balcony[3]));
            map.put("skirt", skirt);
            map.put("door", door);
            map.put("stairs", stairs);
            map.put("color", color);
            return map;
        }

        static Floor fromMap(Map<?, ?> map) {
            Floor floor = new Floor();
            floor.height = clamp(number(map.get("height"), 5), 3, 9);
            if (map.get("panels") instanceof String text && !text.isBlank()) floor.panels = text.trim().toLowerCase(Locale.ROOT);
            ints(map.get("jetty"), floor.jetty, 0, 1);
            floor.windows = named(Windows.class, map.get("windows"), Windows.ALTERNATE);
            bools(map.get("balcony"), floor.balcony);
            floor.skirt = Boolean.TRUE.equals(map.get("skirt"));
            floor.door = clamp(number(map.get("door"), -1), -1, 3);
            floor.stairs = Boolean.TRUE.equals(map.get("stairs"));
            floor.color = clamp(number(map.get("color"), 0), 0, 2);
            return floor;
        }
    }

    /** A lot's roof. */
    public static final class LotRoof {
        public RoofType type = RoofType.GABLE;
        /** The ridge along x (0) or z (1); -1: along the longer side. */
        public int ridge = -1;
        /** The place of the slope in {@link #PITCHES}. */
        public int pitch = 1;
        /** How far it reaches past the walls on each side (0 to 2). */
        public final int[] overhang = {1, 1, 1, 1};
        public int dormers;
        /** Where its chimney stands (see {@link #CHIMNEYS}). */
        public int chimney;
        /** Lots of the same group whose eaves are as high join their roofs. */
        public int group;
        /** A shed's low side (-1: away from the wall it leans on, else the front). */
        public int low = -1;
        public boolean finial;
        public Special special = Special.NONE;
        /** The side the special part is on. */
        public int specialSide = 2;
        /** How thick the roof is (2 or 3). */
        public int thickness = 2;
        /** The lines of the gables: a tag of the kit's gables ("auto": by the walls). */
        public String gable = "auto";

        public double slope() {
            return PITCHES[clamp(pitch, 0, PITCHES.length - 1)];
        }

        public LotRoof copy() {
            LotRoof copy = new LotRoof();
            copy.type = type;
            copy.ridge = ridge;
            copy.pitch = pitch;
            System.arraycopy(overhang, 0, copy.overhang, 0, 4);
            copy.dormers = dormers;
            copy.chimney = chimney;
            copy.group = group;
            copy.low = low;
            copy.finial = finial;
            copy.special = special;
            copy.specialSide = specialSide;
            copy.thickness = thickness;
            copy.gable = gable;
            return copy;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type.name().toLowerCase(Locale.ROOT));
            map.put("ridge", ridge);
            map.put("pitch", PITCH_NAMES[clamp(pitch, 0, PITCHES.length - 1)]);
            map.put("overhang", List.of(overhang[0], overhang[1], overhang[2], overhang[3]));
            map.put("dormers", dormers);
            map.put("chimney", chimney);
            map.put("group", group);
            map.put("low", low);
            map.put("finial", finial);
            map.put("special", special.name().toLowerCase(Locale.ROOT));
            map.put("special-side", specialSide);
            map.put("thickness", thickness);
            map.put("gable", gable);
            return map;
        }

        static LotRoof fromMap(Map<?, ?> map) {
            LotRoof roof = new LotRoof();
            roof.type = named(RoofType.class, map.get("type"), RoofType.GABLE);
            roof.ridge = clamp(number(map.get("ridge"), -1), -1, 1);
            Object pitch = map.get("pitch");
            roof.pitch = 1;
            if (pitch instanceof Number number) {
                double best = Double.MAX_VALUE;
                for (int index = 0; index < PITCHES.length; index++) {
                    if (Math.abs(PITCHES[index] - number.doubleValue()) < best) {
                        best = Math.abs(PITCHES[index] - number.doubleValue());
                        roof.pitch = index;
                    }
                }
            } else if (pitch != null) {
                for (int index = 0; index < PITCH_NAMES.length; index++) if (PITCH_NAMES[index].equals(String.valueOf(pitch).trim())) roof.pitch = index;
            }
            ints(map.get("overhang"), roof.overhang, 0, 2);
            roof.dormers = clamp(number(map.get("dormers"), 0), 0, 6);
            roof.chimney = clamp(number(map.get("chimney"), 0), 0, CHIMNEYS.length - 1);
            roof.group = clamp(number(map.get("group"), 0), 0, 9);
            roof.low = clamp(number(map.get("low"), -1), -1, 3);
            roof.finial = Boolean.TRUE.equals(map.get("finial"));
            roof.special = named(Special.class, map.get("special"), Special.NONE);
            roof.specialSide = clamp(number(map.get("special-side"), 2), 0, 3);
            roof.thickness = clamp(number(map.get("thickness"), 2), 2, 3);
            if (map.get("gable") instanceof String text && !text.isBlank()) roof.gable = text.trim().toLowerCase(Locale.ROOT);
            return roof;
        }
    }

    /** A lot (区画): its shape and place, its foundation, its storeys and its roof. */
    public static final class Lot {
        public String name = "main";
        public Shape shape = Shape.RECT;
        public int x;
        public int z;
        public int w = 13;
        public int d = 9;
        public Foundation foundation = Foundation.PLINTH;
        /** The foundation's height over the ground (the ground storey's floor stands on it). */
        public int base = 1;
        /** Stilts: braces between the posts. */
        public boolean braces = true;
        /** A porch before the ground storey's door: posts and a shed roof. */
        public boolean porch;
        public final List<Floor> floors = new ArrayList<>();
        public LotRoof roof = new LotRoof();

        public Lot() {
        }

        public Lot(String name, Shape shape, int x, int z, int w, int d, int floors) {
            this.name = name;
            this.shape = shape;
            this.x = x;
            this.z = z;
            this.w = w;
            this.d = d;
            for (int index = 0; index < floors; index++) this.floors.add(new Floor());
        }

        public Lot copy() {
            Lot copy = new Lot();
            copy.name = name;
            copy.shape = shape;
            copy.x = x;
            copy.z = z;
            copy.w = w;
            copy.d = d;
            copy.foundation = foundation;
            copy.base = base;
            copy.braces = braces;
            copy.porch = porch;
            for (Floor floor : floors) copy.floors.add(floor.copy());
            copy.roof = roof.copy();
            return copy;
        }

        /** The height of its walls from its foundation's top to its eaves. */
        public int wallHeight() {
            int total = 0;
            for (Floor floor : floors) total += floor.height;
            return total;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("shape", shape.name().toLowerCase(Locale.ROOT));
            map.put("x", x);
            map.put("z", z);
            map.put("w", w);
            map.put("d", d);
            map.put("foundation", foundation.name().toLowerCase(Locale.ROOT));
            map.put("base", base);
            map.put("braces", braces);
            map.put("porch", porch);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Floor floor : floors) list.add(floor.toMap());
            map.put("floors", list);
            map.put("roof", roof.toMap());
            return map;
        }

        public static Lot fromMap(Map<?, ?> map) {
            Lot lot = new Lot();
            if (map.get("name") instanceof String text && !text.isBlank()) lot.name = safeName(text, "lot");
            lot.shape = named(Shape.class, map.get("shape"), Shape.RECT);
            lot.x = clamp(number(map.get("x"), 0), -200, 200);
            lot.z = clamp(number(map.get("z"), 0), -200, 200);
            lot.w = clamp(number(map.get("w"), 13), 3, 101);
            lot.d = clamp(number(map.get("d"), 9), 3, 101);
            lot.foundation = named(Foundation.class, map.get("foundation"), Foundation.PLINTH);
            lot.base = clamp(number(map.get("base"), 1), 0, 12);
            lot.braces = !Boolean.FALSE.equals(map.get("braces"));
            lot.porch = Boolean.TRUE.equals(map.get("porch"));
            if (map.get("floors") instanceof List<?> list) {
                for (Object entry : list) {
                    Map<?, ?> floor = asMap(entry);
                    if (floor != null && lot.floors.size() < MAX_FLOORS) lot.floors.add(Floor.fromMap(floor));
                }
            }
            if (lot.floors.isEmpty()) lot.floors.add(new Floor());
            Map<?, ?> roof = asMap(map.get("roof"));
            if (roof != null) lot.roof = LotRoof.fromMap(roof);
            return lot;
        }
    }

    public Plan copy() {
        Plan copy = new Plan();
        copy.siteWidth = siteWidth;
        copy.siteDepth = siteDepth;
        copy.module = module;
        for (Lot lot : lots) copy.lots.add(lot.copy());
        return copy;
    }

    /** The lot named {@code name} (or of that number, from 1), or null. */
    public Lot lot(String name) {
        if (name == null) return null;
        for (Lot lot : lots) if (lot.name.equalsIgnoreCase(name.trim())) return lot;
        try {
            int index = Integer.parseInt(name.trim()) - 1;
            return index >= 0 && index < lots.size() ? lots.get(index) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** A name not taken by another lot, from {@code wanted} (wing, wing2, wing3 ...). */
    public String freeName(String wanted) {
        String stem = safeName(wanted, "lot");
        String name = stem;
        for (int n = 2; lot(name) != null; n++) name = stem + n;
        return name;
    }

    /** The lots as a list of maps for the yaml. */
    public List<Map<String, Object>> lotsToList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Lot lot : lots) list.add(lot.toMap());
        return list;
    }

    /** Reads lots from the yaml's list (what is broken is left out). */
    public static List<Lot> lotsFromList(Object value) {
        List<Lot> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        for (Object entry : list) {
            Map<?, ?> map = asMap(entry);
            if (map != null && out.size() < MAX_LOTS) out.add(Lot.fromMap(map));
        }
        return out;
    }

    // ---- reading the yaml's values ----------------------------------------------------------------------------------------------

    static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) return map;
        // (a section of Bukkit's yaml: its values; read by reflection so this stays free of the server)
        if (value == null) return null;
        try {
            java.lang.reflect.Method method = value.getClass().getMethod("getValues", boolean.class);
            Object values = method.invoke(value, false);
            return values instanceof Map<?, ?> map ? map : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    static int number(Object value, int fallback) {
        if (value instanceof Number number) return (int) Math.round(number.doubleValue());
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
        return fallback;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static <E extends Enum<E>> E named(Class<E> type, Object value, E fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (E constant : type.getEnumConstants()) if (constant.name().equals(text)) return constant;
        if (value instanceof Number number) {
            E[] all = type.getEnumConstants();
            int index = number.intValue();
            if (index >= 0 && index < all.length) return all[index];
        }
        return fallback;
    }

    private static void ints(Object value, int[] into, int min, int max) {
        if (!(value instanceof List<?> list)) return;
        for (int index = 0; index < Math.min(list.size(), into.length); index++) into[index] = clamp(number(list.get(index), into[index]), min, max);
    }

    private static void bools(Object value, boolean[] into) {
        if (!(value instanceof List<?> list)) return;
        for (int index = 0; index < Math.min(list.size(), into.length); index++) into[index] = Boolean.TRUE.equals(list.get(index));
    }

    /** A name made safe (letters, digits, - and _), or {@code fallback}. */
    public static String safeName(String name, String fallback) {
        String clean = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (clean.length() > 16) clean = clean.substring(0, 16);
        return clean.isEmpty() ? fallback : clean;
    }
}
