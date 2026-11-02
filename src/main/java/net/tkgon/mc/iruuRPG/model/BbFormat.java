package net.tkgon.mc.iruuRPG.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The two ways a Blockbench file keeps its groups. Up to 4.10 a group is an object of the outliner with its name, origin, rotation and the rest;
 * from 5.0 (meta.format_version "5.0": what the web Blockbench and the headless one of the remote modelling save) the groups are a top-level
 * {@code groups} array and the outliner keeps only {@code {uuid, isOpen, children}}. {@link #nestGroups} puts the values of the 5.0 groups back into
 * the outliner (what an object of the outliner has itself comes first), so that the readers ({@link BbModel}, the items' {@code BbItemModel}) read
 * both the same. A 4.10 file is left as it is. The animators name the groups by uuid in both, so they need nothing.
 */
public final class BbFormat {

    private BbFormat() {
    }

    /** Fills the objects of the outliner of {@code root} with the values of the top-level group of the same uuid (in place). */
    public static void nestGroups(JsonObject root) {
        if (!root.has("groups") || !root.get("groups").isJsonArray() || !root.has("outliner") || !root.get("outliner").isJsonArray()) return;
        Map<String, JsonObject> groups = new HashMap<>();
        for (JsonElement item : root.getAsJsonArray("groups")) {
            if (!item.isJsonObject()) continue;
            JsonObject group = item.getAsJsonObject();
            if (group.has("uuid") && group.get("uuid").isJsonPrimitive()) groups.put(group.get("uuid").getAsString(), group);
        }
        if (groups.isEmpty()) return;
        for (JsonElement node : root.getAsJsonArray("outliner")) fill(node, groups);
    }

    private static void fill(JsonElement node, Map<String, JsonObject> groups) {
        if (!node.isJsonObject()) return;
        JsonObject object = node.getAsJsonObject();
        JsonObject group = object.has("uuid") && object.get("uuid").isJsonPrimitive() ? groups.get(object.get("uuid").getAsString()) : null;
        if (group != null) {
            for (Map.Entry<String, JsonElement> entry : group.entrySet()) {
                if (!object.has(entry.getKey())) object.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        if (object.has("children") && object.get("children").isJsonArray()) {
            for (JsonElement child : object.getAsJsonArray("children")) fill(child, groups);
        }
    }
}
