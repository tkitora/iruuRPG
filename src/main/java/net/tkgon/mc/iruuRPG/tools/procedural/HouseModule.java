package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One part added to the house base (t181): a dormer, a chimney, a tower, a wing, a porch, a balcony or a bay window. It is kept with the settings as a list,
 * apart from the base: the base is made from its numbers first, then every part is built on it (and may open the base where it joins it), so a part can be
 * moved, changed, turned off or taken away at any time without anything of the base being lost (non-destructive). Its place is given from a side of the house
 * ({@link #side}: 0 front, 1 right, 2 back, 3 left, seen from the front), {@link #along} that side from its middle (to the right of someone outside facing it)
 * and {@link #offset} out from its wall (negative: into the house).
 */
final class HouseModule {

    /** The kinds of part: the id in the yaml, the label, and what a new one starts with (width, depth, height, level, offset). */
    enum Type {
        DORMER("dormer", "ドーマー窓", 3, 3, 3, 0, 0),
        CHIMNEY("chimney", "煙突", 2, 2, 2, 0, 0),
        TOWER("tower", "塔", 5, 5, 6, 0, -2),
        WING("wing", "翼棟", 5, 5, 1, 0, 0),
        PORCH("porch", "ポーチ", 5, 3, 3, 0, 0),
        BALCONY("balcony", "バルコニー", 5, 2, 1, 1, 0),
        BAY("bay", "出窓", 3, 1, 3, 1, 0);

        final String id;
        final String label;
        final int width;
        final int depth;
        final int height;
        final int level;
        final int offset;

        Type(String id, String label, int width, int depth, int height, int level, int offset) {
            this.id = id;
            this.label = label;
            this.width = width;
            this.depth = depth;
            this.height = height;
            this.level = level;
            this.offset = offset;
        }

        static Type byId(String id) {
            for (Type type : values()) if (type.id.equalsIgnoreCase(id)) return type;
            return null;
        }

        /** What the height means for this kind (the menu shows it as the name of the number). */
        String heightLabel() {
            return switch (this) {
                case DORMER -> "壁の高さ";
                case CHIMNEY -> "屋根から上の高さ";
                case TOWER -> "家の壁より上の高さ";
                case WING -> "階数";
                case PORCH -> "屋根の高さ";
                case BALCONY -> "手すりの高さ";
                case BAY -> "高さ";
            };
        }

        /** Whether the level (the storey it starts at) means anything for this kind. */
        boolean usesLevel() {
            return this == TOWER || this == BALCONY || this == BAY;
        }

        /** What the part is, for the menu. */
        String description() {
            return switch (this) {
                case DORMER -> "屋根の斜面から突き出す、小さな切妻屋根つきの窓。後ろの屋根は中が見えるように開く";
                case CHIMNEY -> "壁の外に、地面から屋根の上まで石の煙突";
                case TOWER -> "丸い石の塔と尖った円錐の屋根。始まる階を 1 以上にすると、壁から持ち出した小塔になる";
                case WING -> "横に張り出す棟。屋根は本体と直角の切妻で、本体の屋根と谷でつながる";
                case PORCH -> "入口の前の、柱と片流れの屋根の下の床";
                case BALCONY -> "上の階から張り出す床と手すり。壁に出入り口をあける";
                case BAY -> "壁から張り出す窓の箱";
            };
        }
    }

    /** The names of the sides (0 front, 1 right, 2 back, 3 left). */
    static final List<String> SIDES = List.of("front", "right", "back", "left");

    static String sideLabel(int side) {
        return switch (side) {
            case 1 -> "右";
            case 2 -> "裏";
            case 3 -> "左";
            default -> "正面";
        };
    }

    static final int MIN_ALONG = -30;
    static final int MAX_ALONG = 30;
    static final int MIN_OFFSET = -10;
    static final int MAX_OFFSET = 10;
    static final int MAX_WIDTH = 25;
    static final int MAX_DEPTH = 20;
    static final int MAX_HEIGHT = 30;
    static final int MAX_LEVEL = 5;

    Type type;
    int side;
    int along;
    int offset;
    int width;
    int depth;
    int height;
    int level;
    /** Off: kept in the list but not built (to see the house without it). */
    boolean on = true;

    HouseModule(Type type) {
        this.type = type;
        reset();
    }

    /** The numbers a new part of its kind starts with (the side and the place along it are kept). */
    void reset() {
        width = type.width;
        depth = type.depth;
        height = type.height;
        level = type.level;
        offset = type.offset;
    }

    /** Changes the kind; the numbers become those of the new kind. */
    void setType(Type type) {
        if (type == null || type == this.type) return;
        this.type = type;
        reset();
    }

    HouseModule copy() {
        HouseModule copy = new HouseModule(type);
        copy.side = side;
        copy.along = along;
        copy.offset = offset;
        copy.width = width;
        copy.depth = depth;
        copy.height = height;
        copy.level = level;
        copy.on = on;
        return copy;
    }

    /** Keeps every number in its range. */
    void limit() {
        side = Math.floorMod(side, 4);
        along = clamp(along, MIN_ALONG, MAX_ALONG);
        offset = clamp(offset, MIN_OFFSET, MAX_OFFSET);
        width = clamp(width, 1, MAX_WIDTH);
        depth = clamp(depth, 1, MAX_DEPTH);
        height = clamp(height, 0, MAX_HEIGHT);
        level = clamp(level, 0, MAX_LEVEL);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.id);
        map.put("side", SIDES.get(Math.floorMod(side, 4)));
        map.put("along", along);
        map.put("offset", offset);
        map.put("width", width);
        map.put("depth", depth);
        map.put("height", height);
        map.put("level", level);
        map.put("on", on);
        return map;
    }

    /** A part read from the yaml, or null when its kind is not known (what is missing is the kind's default, what is out of range is brought in). */
    static HouseModule fromMap(Map<?, ?> map) {
        Type type = map.get("type") == null ? null : Type.byId(String.valueOf(map.get("type")));
        if (type == null) return null;
        HouseModule module = new HouseModule(type);
        Object side = map.get("side");
        if (side instanceof Number number) module.side = number.intValue();
        else if (side != null) module.side = Math.max(0, SIDES.indexOf(String.valueOf(side).toLowerCase(Locale.ROOT)));
        if (map.get("along") instanceof Number number) module.along = number.intValue();
        if (map.get("offset") instanceof Number number) module.offset = number.intValue();
        if (map.get("width") instanceof Number number) module.width = number.intValue();
        if (map.get("depth") instanceof Number number) module.depth = number.intValue();
        if (map.get("height") instanceof Number number) module.height = number.intValue();
        if (map.get("level") instanceof Number number) module.level = number.intValue();
        if (map.get("on") instanceof Boolean value) module.on = value;
        module.limit();
        return module;
    }
}
