package net.tkgon.mc.iruuRPG.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Makes the resource pack of the model feature from the models read: one item model for every part of every model (the cubes of a bone, or one rotated cube), each with its
 * cubes moved so that the part's pivot is the middle of the model (the display turns it about the middle), the textures, and the base item's model with an override
 * for each part (by custom model data). The textures are under {@code textures/item/}: the game only puts the textures of the folders {@code block/} and {@code item/} on the
 * atlas that item models draw from, and one anywhere else shows as the black and purple of a missing texture. The zip is the same bytes for the same models (fixed times, sorted entries), so its hash only changes when a model does.
 */
final class ModelPackBuilder {

    static final String NAMESPACE = "iruumodel";
    private static final Gson GSON = new GsonBuilder().create();

    private ModelPackBuilder() {
    }

    /** Gives every part a custom model data number: the one it had (by {@code index}), else the next free one from {@code base}. */
    static void assign(List<BbModel> models, Map<String, Integer> index, int base) {
        int next = base;
        for (int value : index.values()) next = Math.max(next, value + 1);
        for (BbModel model : models) {
            for (BbModel.Part part : model.parts) {
                String key = model.id + "/" + part.key;
                Integer known = index.get(key);
                if (known == null) {
                    known = next++;
                    index.put(key, known);
                }
                part.customModelData = known;
            }
        }
    }

    /** Writes the zip; the problems found go to {@code warnings}. */
    static void build(List<BbModel> models, File zip, String baseItem, List<String> warnings) throws IOException {
        TreeMap<String, byte[]> files = new TreeMap<>();
        JsonObject mcmeta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 22);
        pack.addProperty("description", "iruuRPG モデル専用パック (/iruumodel build が作ります。手で直さないでください)");
        mcmeta.add("pack", pack);
        files.put("pack.mcmeta", json(mcmeta));

        JsonArray overrides = new JsonArray();
        List<BbModel.Part> all = new ArrayList<>();
        for (BbModel model : models) {
            for (BbModel.Texture texture : model.textures) files.put("assets/" + NAMESPACE + "/textures/item/" + model.id + "/" + texture.name() + ".png", texture.png());
            for (BbModel.Part part : model.parts) {
                JsonObject json = partModel(model, part, warnings);
                if (json == null) continue;
                files.put("assets/" + NAMESPACE + "/models/" + model.id + "/" + part.key + ".json", json(json));
                JsonObject override = new JsonObject();
                JsonObject predicate = new JsonObject();
                predicate.addProperty("custom_model_data", part.customModelData);
                override.add("predicate", predicate);
                override.addProperty("model", NAMESPACE + ":" + model.id + "/" + part.key);
                overrides.add(override);
                all.add(part);
            }
        }
        // the model of the base item: as the game has it, with an override for each part
        JsonObject item = new JsonObject();
        item.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", "minecraft:item/" + baseItem.toLowerCase(Locale.ROOT));
        item.add("textures", textures);
        item.add("overrides", overrides);
        files.put("assets/minecraft/models/item/" + baseItem.toLowerCase(Locale.ROOT) + ".json", json(item));

        File parent = zip.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("could not create " + parent);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0L);
                out.putNextEntry(zipEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        if (zip.isFile() && java.util.Arrays.equals(Files.readAllBytes(zip.toPath()), bytes.toByteArray())) return;
        // (written by a temporary file and renamed, so a client never fetches half a pack)
        File temp = new File(zip.getParentFile(), zip.getName() + ".tmp");
        Files.write(temp.toPath(), bytes.toByteArray());
        Files.move(temp.toPath(), zip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] json(JsonObject object) {
        return GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
    }

    /** The item model of a part: its cubes with the pivot moved to the middle (8, 8, 8). Null when it has nothing to draw. */
    private static JsonObject partModel(BbModel model, BbModel.Part part, List<String> warnings) {
        double[] pivot = part.pivot();
        List<BbModel.Cube> cubes = part.cubes();
        JsonObject json = new JsonObject();
        JsonObject textures = new JsonObject();
        JsonArray elements = new JsonArray();
        boolean first = true;
        for (BbModel.Cube cube : cubes) {
            JsonObject element = new JsonObject();
            JsonArray from = new JsonArray();
            JsonArray to = new JsonArray();
            boolean outside = false;
            for (int axis = 0; axis < 3; axis++) {
                // (about the part's own middle, and smaller when it is longer than a model may be: see Part.fit; the display puts it back)
                double low = (Math.min(cube.from[axis], cube.to[axis]) - cube.inflate - pivot[axis] - part.offset[axis]) * part.shrink + 8.0;
                double high = (Math.max(cube.from[axis], cube.to[axis]) + cube.inflate - pivot[axis] - part.offset[axis]) * part.shrink + 8.0;
                if (low < -16.0 || high > 32.0) outside = true;
                from.add(round(low));
                to.add(round(high));
            }
            if (outside) {
                warnings.add(model.id + ": '" + cube.name + "' of '" + part.bone.name + "' is too far from the pivot of its bone (a cube must stay within -16 to 32 model units once the pivot is the middle): move the pivot or split the bone; the model may not load in the game");
            }
            element.add("from", from);
            element.add("to", to);
            JsonObject faces = new JsonObject();
            for (Map.Entry<String, BbModel.Face> face : cube.faces.entrySet()) {
                BbModel.Face f = face.getValue();
                if (f.uv() == null || !known(model, f.texture())) continue;
                JsonObject faceJson = new JsonObject();
                JsonArray uv = new JsonArray();
                uv.add(round(f.uv()[0] * 16.0 / model.uvWidth));
                uv.add(round(f.uv()[1] * 16.0 / model.uvHeight));
                uv.add(round(f.uv()[2] * 16.0 / model.uvWidth));
                uv.add(round(f.uv()[3] * 16.0 / model.uvHeight));
                faceJson.add("uv", uv);
                faceJson.addProperty("texture", "#" + f.texture());
                if (f.rotation() != 0) faceJson.addProperty("rotation", ((f.rotation() % 360) + 360) % 360);
                faces.add(face.getKey(), faceJson);
                if (!textures.has(String.valueOf(f.texture()))) textures.addProperty(String.valueOf(f.texture()), NAMESPACE + ":item/" + model.id + "/" + textureName(model, f.texture()));
                if (first) {
                    textures.addProperty("particle", NAMESPACE + ":item/" + model.id + "/" + textureName(model, f.texture()));
                    first = false;
                }
            }
            if (faces.size() == 0) continue;
            element.add("faces", faces);
            elements.add(element);
        }
        if (elements.size() == 0) return null;
        json.add("textures", textures);
        json.add("elements", elements);
        return json;
    }

    private static boolean known(BbModel model, int index) {
        for (BbModel.Texture texture : model.textures) if (texture.index() == index) return true;
        return false;
    }

    private static String textureName(BbModel model, int index) {
        for (BbModel.Texture texture : model.textures) if (texture.index() == index) return texture.name();
        return "missing";
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
