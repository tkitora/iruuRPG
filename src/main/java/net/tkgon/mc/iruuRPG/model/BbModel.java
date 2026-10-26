package net.tkgon.mc.iruuRPG.model;

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

/**
 * A model read from a Blockbench file (.bbmodel): the textures, the bones (the groups of the outliner) with their cubes, and the animations. Only what the feature draws is read:
 * cubes (with their rotation and box or free UV), bone origins and rotations, texture images, and animation keyframes of rotation, position and scale. Everything else Blockbench
 * keeps in the file (meshes, locators, effects, molang) is ignored, with a warning where something is dropped.
 */
final class BbModel {

    record Texture(int index, String name, byte[] png) {
    }

    record Face(double[] uv, int texture, int rotation) {
    }

    static final class Cube {
        String uuid;
        String name = "cube";
        double[] from = new double[3];
        double[] to = new double[3];
        double[] origin = new double[3];
        double[] rotation = new double[3];
        double inflate;
        boolean boxUv;
        double[] uvOffset = new double[2];
        final Map<String, Face> faces = new LinkedHashMap<>();

        boolean rotated() {
            return Math.abs(rotation[0]) > 1.0E-6 || Math.abs(rotation[1]) > 1.0E-6 || Math.abs(rotation[2]) > 1.0E-6;
        }
    }

    static final class Bone {
        String uuid;
        String name;
        /** A name that is safe in a file name and unique in the model. */
        String key;
        double[] origin = new double[3];
        double[] rotation = new double[3];
        Bone parent;
        final List<Bone> children = new ArrayList<>();
        final List<Cube> cubes = new ArrayList<>();
    }

    record Keyframe(double time, double[] value, String interpolation) {
    }

    static final class Animation {
        String name;
        double length = 1.0;
        /** loop, once or hold. */
        String loop = "once";
        /** Bone uuid, then channel (rotation, position, scale), then the keyframes by time. */
        final Map<String, Map<String, List<Keyframe>>> channels = new HashMap<>();
    }

    final String id;
    double uvWidth = 16;
    double uvHeight = 16;
    final List<Texture> textures = new ArrayList<>();
    final List<Bone> roots = new ArrayList<>();
    final List<Bone> bones = new ArrayList<>();
    final Map<String, Bone> byUuid = new HashMap<>();
    final Map<String, Animation> animations = new LinkedHashMap<>();
    final List<String> warnings = new ArrayList<>();

    BbModel(String id) {
        this.id = id;
    }

    /**
     * The animation for a name the feature asks for (idle, walk, attack, skill, hurt, death), or one the yaml names: the exact name, else the name without a prefix such as
     * {@code animation.} (the way Bedrock-style files name them), else a usual other name (walk: move / run; idle: stand; attack or skill: any whose name begins with it, one at
     * random). Null when there is none.
     */
    Animation find(String name, java.util.Random random) {
        if (name == null) return null;
        Animation exact = animations.get(name);
        if (exact != null) return exact;
        Map<String, Animation> shortNames = new LinkedHashMap<>();
        for (Map.Entry<String, Animation> entry : animations.entrySet()) shortNames.putIfAbsent(shortName(entry.getKey()), entry.getValue());
        String wanted = shortName(name);
        if (shortNames.containsKey(wanted)) return shortNames.get(wanted);
        List<String> others = switch (wanted) {
            case "walk" -> List.of("move", "run", "walking", "moving");
            case "idle" -> List.of("stand", "standing", "idling");
            case "skill" -> List.of("cast", "special");
            default -> List.of();
        };
        for (String other : others) if (shortNames.containsKey(other)) return shortNames.get(other);
        List<Animation> starting = new ArrayList<>();
        for (Map.Entry<String, Animation> entry : shortNames.entrySet()) if (entry.getKey().startsWith(wanted)) starting.add(entry.getValue());
        if (starting.isEmpty() && wanted.equals("skill")) return find("attack", random);
        return starting.isEmpty() ? null : starting.get(random.nextInt(starting.size()));
    }

    /** The name without a prefix up to the last dot ({@code animation.idle} → {@code idle}), in lower case. */
    private static String shortName(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0 ? name.substring(dot + 1) : name).toLowerCase(java.util.Locale.ROOT);
    }

    /** A part of the model that is drawn by one display: the unrotated cubes of a bone together ({@code cube} null), or one rotated cube. */
    static final class Part {
        final Bone bone;
        final Cube cube;
        String key;
        int customModelData;
        /**
         * Where the middle of the part's model is, from the pivot (model units), and how much its cubes are made smaller in the model (and made big again by the display): a
         * game model must stay within -16 to 32 units, so a part whose cubes reach far from the pivot (a long sword held in a hand) is drawn about its own middle, and one longer
         * than that box is drawn smaller and scaled up.
         */
        double[] offset = {0, 0, 0};
        double shrink = 1.0;

        Part(Bone bone, Cube cube) {
            this.bone = bone;
            this.cube = cube;
        }

        List<Cube> cubes() {
            if (cube != null) return List.of(cube);
            List<Cube> list = new ArrayList<>();
            for (Cube each : bone.cubes) if (!each.rotated()) list.add(each);
            return list;
        }

        /** Works out the offset and the shrink from the cubes. */
        void fit() {
            double[] pivot = pivot();
            double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (Cube each : cubes()) {
                for (int axis = 0; axis < 3; axis++) {
                    low[axis] = Math.min(low[axis], Math.min(each.from[axis], each.to[axis]) - each.inflate - pivot[axis]);
                    high[axis] = Math.max(high[axis], Math.max(each.from[axis], each.to[axis]) + each.inflate - pivot[axis]);
                }
            }
            boolean outside = false;
            double size = 0.0;
            for (int axis = 0; axis < 3; axis++) {
                if (low[axis] < -23.5 || high[axis] > 23.5) outside = true;
                size = Math.max(size, high[axis] - low[axis]);
            }
            if (!outside) return;
            for (int axis = 0; axis < 3; axis++) offset[axis] = (low[axis] + high[axis]) / 2.0;
            if (size > 46.0) shrink = 46.0 / size;
        }

        /** The place the part turns about, in model units: the bone's origin, or the origin of the rotated cube. */
        double[] pivot() {
            return cube == null ? bone.origin : cube.origin;
        }
    }

    final List<Part> parts = new ArrayList<>();

    /** How tall the model stands (the top of its highest cube, model units, in the pose it was made in). */
    double height() {
        double top = 0.0;
        for (Bone bone : bones) for (Cube cube : bone.cubes) top = Math.max(top, Math.max(cube.from[1], cube.to[1]) + cube.inflate);
        return top;
    }

    /** The parts to draw, worked out once after reading. */
    private void makeParts() {
        parts.clear();
        for (Bone bone : bones) {
            boolean any = false;
            for (Cube cube : bone.cubes) if (!cube.rotated()) any = true;
            if (any) {
                Part part = new Part(bone, null);
                part.key = bone.key;
                parts.add(part);
            }
            int count = 0;
            for (Cube cube : bone.cubes) {
                if (!cube.rotated()) continue;
                Part part = new Part(bone, cube);
                part.key = bone.key + "_r" + (++count);
                parts.add(part);
            }
        }
        for (Part part : parts) part.fit();
    }

    // ---- reading ----------------------------------------------------------------------------------------------------------------

    static BbModel parse(String id, String json) {
        BbModel model = new BbModel(id);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject resolution = root.getAsJsonObject("resolution");
            model.uvWidth = number(resolution, "width", 16);
            model.uvHeight = number(resolution, "height", 16);
        }
        if (root.has("textures") && root.get("textures").isJsonArray()) {
            int index = 0;
            for (JsonElement item : root.getAsJsonArray("textures")) {
                JsonObject texture = item.getAsJsonObject();
                String source = texture.has("source") ? texture.get("source").getAsString() : "";
                int comma = source.indexOf("base64,");
                if (comma < 0) {
                    model.warnings.add("texture " + index + " has no embedded image (save the model with the textures embedded)");
                    index++;
                    continue;
                }
                String name = safe(texture.has("name") ? texture.get("name").getAsString().replaceAll("\\.png$", "") : "texture" + index);
                model.textures.add(new Texture(index, name, Base64.getDecoder().decode(source.substring(comma + 7))));
                index++;
            }
        }
        Map<String, Cube> cubes = new HashMap<>();
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            for (JsonElement item : root.getAsJsonArray("elements")) {
                JsonObject element = item.getAsJsonObject();
                String type = element.has("type") ? element.get("type").getAsString() : "cube";
                if (!type.equals("cube")) {
                    model.warnings.add("element '" + (element.has("name") ? element.get("name").getAsString() : "?") + "' is a " + type + " (only cubes are drawn)");
                    continue;
                }
                Cube cube = new Cube();
                cube.uuid = element.get("uuid").getAsString();
                cube.name = element.has("name") ? element.get("name").getAsString() : "cube";
                cube.from = vector(element, "from", 3);
                cube.to = vector(element, "to", 3);
                cube.origin = element.has("origin") ? vector(element, "origin", 3) : new double[]{0, 0, 0};
                cube.rotation = element.has("rotation") ? vector(element, "rotation", 3) : new double[]{0, 0, 0};
                cube.inflate = number(element, "inflate", 0.0);
                cube.boxUv = element.has("box_uv") && element.get("box_uv").getAsBoolean();
                cube.uvOffset = element.has("uv_offset") ? vector(element, "uv_offset", 2) : new double[]{0, 0};
                if (element.has("faces") && element.get("faces").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> face : element.getAsJsonObject("faces").entrySet()) {
                        JsonObject f = face.getValue().getAsJsonObject();
                        if (!f.has("texture") || f.get("texture").isJsonNull()) continue;
                        double[] uv = f.has("uv") ? vector(f, "uv", 4) : null;
                        cube.faces.put(face.getKey(), new Face(uv, f.get("texture").getAsInt(), (int) number(f, "rotation", 0)));
                    }
                }
                if (cube.boxUv) fillBoxUv(cube);
                cubes.put(cube.uuid, cube);
            }
        }
        // the bones: the groups of the outliner; cubes outside any group are in a bone of their own at the origin
        Bone loose = new Bone();
        loose.uuid = "root";
        loose.name = "root";
        List<Cube> placed = new ArrayList<>();
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            for (JsonElement item : root.getAsJsonArray("outliner")) readNode(model, item, null, loose, cubes, placed);
        }
        for (Cube cube : cubes.values()) if (!placed.contains(cube)) loose.cubes.add(cube);
        if (!loose.cubes.isEmpty()) {
            model.roots.add(0, loose);
            model.bones.add(0, loose);
            model.byUuid.put(loose.uuid, loose);
        }
        // unique keys
        Map<String, Integer> used = new HashMap<>();
        for (Bone bone : model.bones) {
            String base = safe(bone.name);
            int count = used.merge(base, 1, Integer::sum);
            bone.key = count == 1 ? base : base + "_" + count;
        }
        if (root.has("animations") && root.get("animations").isJsonArray()) {
            for (JsonElement item : root.getAsJsonArray("animations")) readAnimation(model, item.getAsJsonObject());
        }
        model.makeParts();
        if (model.parts.isEmpty()) model.warnings.add("the model has no cubes");
        return model;
    }

    private static void readNode(BbModel model, JsonElement node, Bone parent, Bone loose, Map<String, Cube> cubes, List<Cube> placed) {
        if (node.isJsonPrimitive()) {
            Cube cube = cubes.get(node.getAsString());
            if (cube == null) return;
            placed.add(cube);
            (parent == null ? loose : parent).cubes.add(cube);
            return;
        }
        JsonObject group = node.getAsJsonObject();
        Bone bone = new Bone();
        bone.uuid = group.has("uuid") ? group.get("uuid").getAsString() : "bone" + model.bones.size();
        bone.name = group.has("name") ? group.get("name").getAsString() : "bone";
        bone.origin = group.has("origin") ? vector(group, "origin", 3) : new double[]{0, 0, 0};
        bone.rotation = group.has("rotation") ? vector(group, "rotation", 3) : new double[]{0, 0, 0};
        bone.parent = parent;
        if (parent == null) model.roots.add(bone);
        else parent.children.add(bone);
        model.bones.add(bone);
        model.byUuid.put(bone.uuid, bone);
        if (group.has("children") && group.get("children").isJsonArray()) {
            for (JsonElement child : group.getAsJsonArray("children")) readNode(model, child, bone, loose, cubes, placed);
        }
    }

    private static void readAnimation(BbModel model, JsonObject json) {
        Animation animation = new Animation();
        animation.name = json.has("name") ? json.get("name").getAsString() : "animation";
        animation.length = number(json, "length", 1.0);
        animation.loop = json.has("loop") ? json.get("loop").getAsString() : "once";
        if (json.has("animators") && json.get("animators").isJsonObject()) {
            for (Map.Entry<String, JsonElement> animator : json.getAsJsonObject("animators").entrySet()) {
                JsonObject object = animator.getValue().getAsJsonObject();
                if (!object.has("keyframes") || !object.get("keyframes").isJsonArray()) continue;
                Map<String, List<Keyframe>> channels = new HashMap<>();
                for (JsonElement item : object.getAsJsonArray("keyframes")) {
                    JsonObject key = item.getAsJsonObject();
                    String channel = key.has("channel") ? key.get("channel").getAsString() : "";
                    if (!channel.equals("rotation") && !channel.equals("position") && !channel.equals("scale")) continue;
                    JsonArray points = key.has("data_points") ? key.getAsJsonArray("data_points") : new JsonArray();
                    if (points.size() == 0) continue;
                    JsonObject point = points.get(0).getAsJsonObject();
                    double[] value = {numberOrZero(point, "x", channel.equals("scale") ? 1.0 : 0.0), numberOrZero(point, "y", channel.equals("scale") ? 1.0 : 0.0),
                            numberOrZero(point, "z", channel.equals("scale") ? 1.0 : 0.0)};
                    String interpolation = key.has("interpolation") ? key.get("interpolation").getAsString() : "linear";
                    channels.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(new Keyframe(number(key, "time", 0.0), value, interpolation));
                }
                for (List<Keyframe> keys : channels.values()) keys.sort((a, b) -> Double.compare(a.time(), b.time()));
                animation.channels.put(animator.getKey(), channels);
            }
        }
        model.animations.put(animation.name, animation);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------------------

    /** The UV of each face of a cube with box UV from its offset and size (the layout of a Minecraft skin). */
    private static void fillBoxUv(Cube cube) {
        double w = Math.abs(cube.to[0] - cube.from[0]);
        double h = Math.abs(cube.to[1] - cube.from[1]);
        double d = Math.abs(cube.to[2] - cube.from[2]);
        double u = cube.uvOffset[0];
        double v = cube.uvOffset[1];
        double[][] uv = {
                {u + d, v + d, u + d + w, v + d + h},                 // north
                {u, v + d, u + d, v + d + h},                         // east
                {u + d + w + d, v + d, u + d + w + d + w, v + d + h}, // south
                {u + d + w, v + d, u + d + w + d, v + d + h},         // west
                {u + d, v, u + d + w, v + d},                         // up
                {u + d + w, v + d, u + d + w + w, v}                  // down
        };
        String[] names = {"north", "east", "south", "west", "up", "down"};
        for (int index = 0; index < 6; index++) {
            Face face = cube.faces.get(names[index]);
            // (a face the file already has a UV for keeps it)
            if (face != null && face.uv() == null) cube.faces.put(names[index], new Face(uv[index], face.texture(), face.rotation()));
            if (face == null) continue;
        }
    }

    static double[] vector(JsonObject object, String key, int size) {
        JsonArray array = object.getAsJsonArray(key);
        double[] result = new double[size];
        for (int index = 0; index < size && index < array.size(); index++) result[index] = array.get(index).isJsonPrimitive() && array.get(index).getAsJsonPrimitive().isNumber() ? array.get(index).getAsDouble() : 0.0;
        return result;
    }

    private static double number(JsonObject object, String key, double fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber() ? object.get(key).getAsDouble() : fallback;
    }

    /** A number, or the number in a text, or the fallback (a keyframe value can be a formula, which is not evaluated). */
    private static double numberOrZero(JsonObject object, String key, double fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsDouble();
        if (value.isJsonPrimitive()) {
            try {
                return Double.parseDouble(value.getAsString().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static String safe(String name) {
        String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
        return clean.isEmpty() ? "x" : clean;
    }
}
