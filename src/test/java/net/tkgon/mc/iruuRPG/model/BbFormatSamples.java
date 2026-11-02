package net.tkgon.mc.iruuRPG.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** For the tests: a Blockbench file of 4.10 turned into the shape of 5.0 (the groups in a top-level array, the outliner only uuids), and the sample files. */
public final class BbFormatSamples {

    private BbFormatSamples() {
    }

    /** The same model as Blockbench 5.0 saves it: each group of the outliner moved to {@code groups}, the outliner keeping {uuid, isOpen, children}. */
    public static String modern(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray groups = new JsonArray();
        JsonArray outliner = new JsonArray();
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            for (JsonElement node : root.getAsJsonArray("outliner")) outliner.add(strip(node, groups));
        }
        root.add("outliner", outliner);
        root.add("groups", groups);
        JsonObject meta = root.has("meta") && root.get("meta").isJsonObject() ? root.getAsJsonObject("meta") : new JsonObject();
        meta.addProperty("format_version", "5.0");
        root.add("meta", meta);
        return root.toString();
    }

    private static JsonElement strip(JsonElement node, JsonArray groups) {
        if (!node.isJsonObject()) return node;
        JsonObject group = node.getAsJsonObject();
        JsonObject entry = new JsonObject();
        JsonObject kept = new JsonObject();
        for (Map.Entry<String, JsonElement> field : group.entrySet()) {
            switch (field.getKey()) {
                case "children" -> {
                }
                case "isOpen" -> kept.add("isOpen", field.getValue());
                default -> entry.add(field.getKey(), field.getValue());
            }
        }
        kept.add("uuid", group.get("uuid"));
        JsonArray children = new JsonArray();
        if (group.has("children") && group.get("children").isJsonArray()) {
            for (JsonElement child : group.getAsJsonArray("children")) children.add(strip(child, groups));
        }
        kept.add("children", children);
        groups.add(entry);
        return kept;
    }

    /** The .bbmodel files of the folder of the system property {@code bbmodel.samples} ({@code -PbbmodelSamples=<folder>}); empty without one. */
    public static List<File> samples() {
        String folder = System.getProperty("bbmodel.samples", "");
        if (folder.isBlank()) return List.of();
        File[] files = new File(folder).listFiles((dir, name) -> name.endsWith(".bbmodel"));
        return files == null ? List.of() : Arrays.stream(files).sorted().toList();
    }
}
