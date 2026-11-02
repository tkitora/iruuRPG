package net.tkgon.mc.iruuRPG.itempack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A Blockbench model (.bbmodel) read as one item model: its cubes as the elements of a game model (in the coordinates of a game model, where
 * a block is 0 - 16), its embedded textures, the display settings Blockbench keeps for a Java Block/Item model (Display mode: how the item
 * is held), and the point the hand holds (a group or locator named {@code grip}).
 * <p>
 * A game model can turn an element about one axis only, by -45, -22.5, 22.5 or 45 degrees. A cube turned otherwise (or inside a turned
 * group, which a Generic Model may have) is turned by the nearest of those, with a warning: the model of a Java Block/Item model needs nothing
 * of that. Meshes and other elements than cubes are left out (warned).
 */
final class BbItemModel {

    /** The names of a group or locator that marks the point the hand holds. */
    static final Set<String> GRIP_NAMES = Set.of("grip", "handle", "hand", "持ち手");
    /** The angles a game model may turn an element by. */
    private static final double[] ANGLES = {-45.0, -22.5, 22.5, 45.0};

    record Texture(int index, String name, byte[] png, double uvWidth, double uvHeight) {
    }

    record Face(double[] uv, int texture, int rotation) {
    }

    /**
     * An element of the game model: its box (game model coordinates, before its turn), the turn about its middle ({@code axis} x / y / z
     * and {@code angle}; axis null = none) and its faces by name.
     */
    static final class Element {
        String name;
        double[] from = new double[3];
        double[] to = new double[3];
        String axis;
        double angle;
        boolean rescale;
        final Map<String, Face> faces = new LinkedHashMap<>();

        double[] middle() {
            return new double[]{(from[0] + to[0]) / 2.0, (from[1] + to[1]) / 2.0, (from[2] + to[2]) / 2.0};
        }

        /** The 8 corners of the box, turned as the game turns it. */
        List<double[]> corners() {
            List<double[]> list = new ArrayList<>();
            double[] middle = middle();
            double[][] turn = axis == null ? Rotation.IDENTITY : Rotation.about(axis, angle);
            for (int corner = 0; corner < 8; corner++) {
                double[] point = {(corner & 1) == 0 ? from[0] : to[0], (corner & 2) == 0 ? from[1] : to[1], (corner & 4) == 0 ? from[2] : to[2]};
                double[] turned = Rotation.apply(turn, Rotation.minus(point, middle));
                list.add(Rotation.plus(turned, middle));
            }
            return list;
        }
    }

    /** One slot of Blockbench's display settings (the game's {@code display} of a model): degrees, model units (1/16 block), and size. */
    record Display(double[] rotation, double[] translation, double[] scale) {
    }

    final String id;
    final String format;
    double uvWidth = 16;
    double uvHeight = 16;
    final List<Texture> textures = new ArrayList<>();
    final List<Element> elements = new ArrayList<>();
    final Map<String, Display> display = new LinkedHashMap<>();
    /** The point the hand holds (game model coordinates), or null when the model marks none. */
    double[] grip;
    final List<String> warnings = new ArrayList<>();

    private BbItemModel(String id, String format) {
        this.id = id;
        this.format = format;
    }

    /** Whether Blockbench draws the model about the middle of its grid (every format but a Java block/item model, whose grid is a block's 0 - 16). */
    boolean centered() {
        return !format.equals("java_block");
    }

    static BbItemModel parse(String id, String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String format = "free";
        if (root.has("meta") && root.get("meta").isJsonObject()) {
            JsonObject meta = root.getAsJsonObject("meta");
            if (meta.has("model_format") && meta.get("model_format").isJsonPrimitive()) format = meta.get("model_format").getAsString();
        }
        BbItemModel model = new BbItemModel(id, format);
        // a Generic Model has its middle at x = z = 0 and its feet at y = 0; the game's model has a block's corner there
        double[] shift = model.centered() ? new double[]{8, 0, 8} : new double[]{0, 0, 0};
        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject resolution = root.getAsJsonObject("resolution");
            model.uvWidth = number(resolution, "width", 16);
            model.uvHeight = number(resolution, "height", 16);
        }
        if (root.has("textures") && root.get("textures").isJsonArray()) {
            int index = 0;
            Set<String> names = new java.util.HashSet<>();
            for (JsonElement item : root.getAsJsonArray("textures")) {
                JsonObject texture = item.getAsJsonObject();
                String source = texture.has("source") && texture.get("source").isJsonPrimitive() ? texture.get("source").getAsString() : "";
                int comma = source.indexOf("base64,");
                if (comma < 0) {
                    model.warnings.add("texture " + index + " has no embedded image (save the model with the textures embedded)");
                    index++;
                    continue;
                }
                String name = safe(texture.has("name") ? texture.get("name").getAsString().replaceAll("(?i)\\.png$", "") : "texture" + index);
                while (!names.add(name)) name = name + "_" + index;
                double width = number(texture, "uv_width", model.uvWidth);
                double height = number(texture, "uv_height", model.uvHeight);
                model.textures.add(new Texture(index, name, Base64.getDecoder().decode(source.substring(comma + 7).trim()), width, height));
                index++;
            }
        }
        // the cubes by uuid, and the groups each is in (outermost first)
        Map<String, JsonObject> cubes = new LinkedHashMap<>();
        Map<String, double[]> locators = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            for (JsonElement item : root.getAsJsonArray("elements")) {
                JsonObject element = item.getAsJsonObject();
                String type = element.has("type") ? element.get("type").getAsString() : "cube";
                String name = element.has("name") ? element.get("name").getAsString() : type;
                String uuid = element.has("uuid") ? element.get("uuid").getAsString() : "e" + cubes.size();
                names.put(uuid, name);
                if (type.equals("locator") || type.equals("null_object")) {
                    if (element.has("position")) locators.put(uuid, vector(element, "position", 3));
                    else if (element.has("from")) locators.put(uuid, vector(element, "from", 3));
                    continue;
                }
                if (!type.equals("cube")) {
                    model.warnings.add("'" + name + "' is a " + type + " (only cubes are drawn)");
                    continue;
                }
                cubes.put(uuid, element);
            }
        }
        Map<String, List<JsonObject>> groupsOf = new HashMap<>();
        List<double[]> grips = new ArrayList<>();
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            for (JsonElement node : root.getAsJsonArray("outliner")) walk(node, new ArrayList<>(), groupsOf, grips, locators, names);
        }
        for (Map.Entry<String, double[]> locator : locators.entrySet()) {
            if (GRIP_NAMES.contains(names.getOrDefault(locator.getKey(), "").toLowerCase(Locale.ROOT))) {
                double[] point = transformed(locator.getValue(), groupsOf.getOrDefault(locator.getKey(), List.of()));
                grips.add(0, point);
            }
        }
        if (!grips.isEmpty()) model.grip = Rotation.plus(grips.get(0), shift);

        for (Map.Entry<String, JsonObject> entry : cubes.entrySet()) {
            Element element = element(model, entry.getValue(), groupsOf.getOrDefault(entry.getKey(), List.of()), shift);
            if (element != null) model.elements.add(element);
        }
        if (model.elements.isEmpty()) model.warnings.add("the model has no cubes with a texture");

        if (root.has("display") && root.get("display").isJsonObject()) {
            for (Map.Entry<String, JsonElement> slot : root.getAsJsonObject("display").entrySet()) {
                if (!slot.getValue().isJsonObject()) continue;
                JsonObject value = slot.getValue().getAsJsonObject();
                double[] rotation = value.has("rotation") ? vector(value, "rotation", 3) : new double[]{0, 0, 0};
                double[] translation = value.has("translation") ? vector(value, "translation", 3) : new double[]{0, 0, 0};
                double[] scale = value.has("scale") ? vector(value, "scale", 3) : new double[]{1, 1, 1};
                model.display.put(slot.getKey(), new Display(rotation, translation, scale));
            }
        }
        return model;
    }

    /** Goes through the outliner: which groups each cube and locator is in, and the groups that mark the grip. */
    private static void walk(JsonElement node, List<JsonObject> path, Map<String, List<JsonObject>> groupsOf, List<double[]> grips,
                             Map<String, double[]> locators, Map<String, String> names) {
        if (node.isJsonPrimitive()) {
            groupsOf.put(node.getAsString(), List.copyOf(path));
            return;
        }
        if (!node.isJsonObject()) return;
        JsonObject group = node.getAsJsonObject();
        String name = group.has("name") ? group.get("name").getAsString() : "";
        if (GRIP_NAMES.contains(name.toLowerCase(Locale.ROOT)) && group.has("origin")) {
            grips.add(transformed(vector(group, "origin", 3), path));
        }
        List<JsonObject> inner = new ArrayList<>(path);
        inner.add(group);
        if (group.has("children") && group.get("children").isJsonArray()) {
            for (JsonElement child : group.getAsJsonArray("children")) walk(child, inner, groupsOf, grips, locators, names);
        }
    }

    /** A point of the model moved by the turns of the groups it is in (innermost first, each about its origin). */
    private static double[] transformed(double[] point, List<JsonObject> groups) {
        double[] result = point.clone();
        for (int index = groups.size() - 1; index >= 0; index--) {
            JsonObject group = groups.get(index);
            double[] rotation = group.has("rotation") ? vector(group, "rotation", 3) : new double[]{0, 0, 0};
            if (Rotation.isZero(rotation)) continue;
            double[] origin = group.has("origin") ? vector(group, "origin", 3) : new double[]{0, 0, 0};
            result = Rotation.plus(Rotation.apply(Rotation.blockbench(rotation), Rotation.minus(result, origin)), origin);
        }
        return result;
    }

    /** A cube as an element of the game model, or null when none of its faces has a texture. */
    private static Element element(BbItemModel model, JsonObject json, List<JsonObject> groups, double[] shift) {
        Element element = new Element();
        element.name = json.has("name") ? json.get("name").getAsString() : "cube";
        double[] from = vector(json, "from", 3);
        double[] to = vector(json, "to", 3);
        double inflate = number(json, "inflate", 0.0);
        double[] low = new double[3];
        double[] high = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            low[axis] = Math.min(from[axis], to[axis]) - inflate;
            high[axis] = Math.max(from[axis], to[axis]) + inflate;
        }
        double[] middle = {(low[0] + high[0]) / 2.0, (low[1] + high[1]) / 2.0, (low[2] + high[2]) / 2.0};
        // the whole turn of the cube: its own about its origin, then each group's about the group's origin (innermost first)
        double[][] turn = Rotation.IDENTITY;
        double[] moved = middle;
        double[] rotation = json.has("rotation") ? vector(json, "rotation", 3) : new double[]{0, 0, 0};
        if (!Rotation.isZero(rotation)) {
            double[] origin = json.has("origin") ? vector(json, "origin", 3) : new double[]{0, 0, 0};
            double[][] own = Rotation.blockbench(rotation);
            turn = own;
            moved = Rotation.plus(Rotation.apply(own, Rotation.minus(moved, origin)), origin);
        }
        for (int index = groups.size() - 1; index >= 0; index--) {
            JsonObject group = groups.get(index);
            double[] groupRotation = group.has("rotation") ? vector(group, "rotation", 3) : new double[]{0, 0, 0};
            if (Rotation.isZero(groupRotation)) continue;
            double[] origin = group.has("origin") ? vector(group, "origin", 3) : new double[]{0, 0, 0};
            double[][] groupTurn = Rotation.blockbench(groupRotation);
            turn = Rotation.multiply(groupTurn, turn);
            moved = Rotation.plus(Rotation.apply(groupTurn, Rotation.minus(moved, origin)), origin);
        }
        // the box keeps its size and goes where its middle went; it turns about its middle by the nearest turn the game allows
        for (int axis = 0; axis < 3; axis++) {
            double half = (high[axis] - low[axis]) / 2.0;
            element.from[axis] = moved[axis] - half + shift[axis];
            element.to[axis] = moved[axis] + half + shift[axis];
        }
        if (!Rotation.isIdentity(turn)) {
            Object[] nearest = nearest(turn);
            element.axis = (String) nearest[0];
            element.angle = (Double) nearest[1];
            if ((Double) nearest[2] > 1.0E-3) {
                model.warnings.add("'" + element.name + "' is turned in a way a game model cannot (one axis, by -45, -22.5, 22.5 or 45 degrees): it is drawn turned "
                        + (element.axis == null ? "not at all" : "by " + element.angle + " degrees about " + element.axis));
            }
        }
        if (json.has("rescale") && json.get("rescale").isJsonPrimitive()) element.rescale = json.get("rescale").getAsBoolean();

        boolean boxUv = json.has("box_uv") && json.get("box_uv").getAsBoolean();
        double[] uvOffset = json.has("uv_offset") ? vector(json, "uv_offset", 2) : new double[]{0, 0};
        double[] size = {Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]), Math.abs(to[2] - from[2])};
        Map<String, double[]> boxUvs = boxUv ? boxUv(size, uvOffset) : Map.of();
        if (json.has("faces") && json.get("faces").isJsonObject()) {
            for (Map.Entry<String, JsonElement> face : json.getAsJsonObject("faces").entrySet()) {
                if (!face.getValue().isJsonObject()) continue;
                JsonObject f = face.getValue().getAsJsonObject();
                if (!f.has("texture") || f.get("texture").isJsonNull() || !f.get("texture").isJsonPrimitive()) continue;
                int texture = f.get("texture").getAsInt();
                if (model.texture(texture) == null) continue;
                double[] uv = f.has("uv") ? vector(f, "uv", 4) : boxUvs.get(face.getKey());
                if (uv == null) continue;
                element.faces.put(face.getKey(), new Face(uv, texture, (int) number(f, "rotation", 0)));
            }
        }
        return element.faces.isEmpty() ? null : element;
    }

    /** The turn the game allows that is nearest to {@code turn}: {axis or null, angle, how far it is}. */
    private static Object[] nearest(double[][] turn) {
        Object[] best = {null, 0.0, Rotation.distance(turn, Rotation.IDENTITY)};
        for (String axis : new String[]{"x", "y", "z"}) {
            for (double angle : ANGLES) {
                double distance = Rotation.distance(turn, Rotation.about(axis, angle));
                if (distance < (Double) best[2]) best = new Object[]{axis, angle, distance};
            }
        }
        return best;
    }

    Texture texture(int index) {
        for (Texture texture : textures) if (texture.index() == index) return texture;
        return null;
    }

    /** The UV of each face of a box-UV cube from its offset and size (the layout of a Minecraft skin). */
    private static Map<String, double[]> boxUv(double[] size, double[] offset) {
        double w = size[0];
        double h = size[1];
        double d = size[2];
        double u = offset[0];
        double v = offset[1];
        Map<String, double[]> uv = new HashMap<>();
        uv.put("north", new double[]{u + d, v + d, u + d + w, v + d + h});
        uv.put("east", new double[]{u, v + d, u + d, v + d + h});
        uv.put("south", new double[]{u + d + w + d, v + d, u + d + w + d + w, v + d + h});
        uv.put("west", new double[]{u + d + w, v + d, u + d + w + d, v + d + h});
        uv.put("up", new double[]{u + d, v, u + d + w, v + d});
        uv.put("down", new double[]{u + d + w, v + d, u + d + w + w, v});
        return uv;
    }

    static double[] vector(JsonObject object, String key, int size) {
        double[] result = new double[size];
        if (!object.has(key) || !object.get(key).isJsonArray()) return result;
        JsonArray array = object.getAsJsonArray(key);
        for (int index = 0; index < size && index < array.size(); index++) {
            JsonElement value = array.get(index);
            result[index] = value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : 0.0;
        }
        return result;
    }

    private static double number(JsonObject object, String key, double fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber() ? object.get(key).getAsDouble() : fallback;
    }

    /** A name that is safe in a resource pack path ({@code [a-z0-9_]}: one capital letter makes the game drop a whole pack's font). */
    static String safe(String name) {
        String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
        return clean.isEmpty() || clean.equals("_") ? "x" : clean;
    }
}
