package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One part added to the house base (t181): a dormer, a chimney, a tower, a wing, a porch, a balcony, a bay window; (t190) a volume, stairs. It is kept with the settings as a list,
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
        BAY("bay", "出窓", 3, 1, 3, 1, 0),
        /** (t190) a whole box of storeys with its own roof, anywhere: on the ground, on the house, jutting out from it, through its roof. */
        VOLUME("volume", "棟", 7, 5, 2, 0, 0),
        /** (t190) stairs outside along a wall, up to a doorway of an upper storey. */
        STAIR("stair", "外階段", 2, 1, 0, 1, 0);

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
                case VOLUME -> "階数";
                case STAIR -> "(使わない)";
            };
        }

        /** Whether the level (the storey it starts at) means anything for this kind. */
        boolean usesLevel() {
            return this == TOWER || this == BALCONY || this == BAY || this == VOLUME || this == STAIR;
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
                case VOLUME -> "階と屋根を持つ箱 (棟) を、好きな位置・高さに。家と重なる所は中がつながり、家より高ければ屋根を突き抜ける。階を上げると、家に載る・宙に張り出す";
                case STAIR -> "壁に沿って上の階の入口まで上がる、石の外階段。上に踊り場と入口";
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
    // (t185: ±20, so a tower can stand in the middle of a large house, or at the end of a long wing)
    static final int MIN_OFFSET = -20;
    static final int MAX_OFFSET = 20;
    static final int MAX_WIDTH = 25;
    static final int MAX_DEPTH = 20;
    static final int MAX_HEIGHT = 30;
    static final int MAX_LEVEL = 5;

    /** The roof of a volume: -1 the house's shape, else the shape (0 to 10). */
    int roof = -1;
    /** The way the ridge of a volume runs (0 auto, 1 along the side it stands on, 2 across). */
    int ridge;
    /** The shape of a tower (0 round, 1 octagonal, 2 square); the way stairs go up (0 to the right, 1 to the left). */
    int shape;
    /** How far the top storey of a tower juts out; the jetty of a volume (-1 the house's). */
    int jetty;
    /** (t195) How much higher (lower when negative) a volume's floor is than the storey it starts at: a foundation of its own height. */
    int raise;

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
        roof = -1;
        ridge = 0;
        shape = 0;
        jetty = type == Type.VOLUME ? -1 : 0;
        raise = 0;
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
        copy.roof = roof;
        copy.ridge = ridge;
        copy.shape = shape;
        copy.jetty = jetty;
        copy.raise = raise;
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
        roof = clamp(roof, -1, 10);
        ridge = clamp(ridge, 0, 2);
        shape = clamp(shape, 0, 2);
        jetty = clamp(jetty, -1, 3);
        raise = clamp(raise, -8, 8);
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
        map.put("roof", roof);
        map.put("ridge", ridge);
        map.put("shape", shape);
        map.put("jetty", jetty);
        map.put("raise", raise);
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
        if (map.get("roof") instanceof Number number) module.roof = number.intValue();
        if (map.get("ridge") instanceof Number number) module.ridge = number.intValue();
        if (map.get("shape") instanceof Number number) module.shape = number.intValue();
        if (map.get("jetty") instanceof Number number) module.jetty = number.intValue();
        if (map.get("raise") instanceof Number number) module.raise = number.intValue();
        module.limit();
        return module;
    }
}
