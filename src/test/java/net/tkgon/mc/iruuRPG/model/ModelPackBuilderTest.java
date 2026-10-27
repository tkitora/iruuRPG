package net.tkgon.mc.iruuRPG.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pack of the model feature, with more than one model (no server needed). */
class ModelPackBuilderTest {

    /** A small model of one bone with one cube (a box), as Blockbench writes it. */
    private static String box(String uuid) {
        return """
                {"meta":{"format_version":"4.5"},"resolution":{"width":16,"height":16},
                 "elements":[{"name":"cube","uuid":"%s","from":[-2,0,-2],"to":[2,4,2],"origin":[0,0,0],
                   "faces":{"north":{"uv":[0,0,4,4],"texture":0},"south":{"uv":[0,0,4,4],"texture":0}}}],
                 "outliner":[{"name":"%s","uuid":"b%s","origin":[0,0,0],"children":["%s"]}],
                 "textures":[{"name":"skin","source":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="}],
                 "animations":[]}
                """.formatted(uuid, "bone_" + uuid, uuid, uuid);
    }

    @Test
    void theOverridesGoFromLowToHighWhateverTheOrderOfTheModels() throws IOException {
        // the first model got its numbers first (low); the one added later has higher ones and comes first in the list
        BbModel old = BbModel.parse("skeleton_boss", box("a"));
        BbModel added = BbModel.parse("geode_reaver", box("c"));
        Map<String, Integer> index = new HashMap<>();
        ModelPackBuilder.assign(List.of(old), index, 100);
        ModelPackBuilder.assign(List.of(added, old), index, 100);
        assertTrue(added.parts.get(0).customModelData > old.parts.get(0).customModelData);

        File zip = Files.createTempFile("iruumodel", ".zip").toFile();
        zip.delete();
        List<String> warnings = new ArrayList<>();
        ModelPackBuilder.build(List.of(added, old), zip, "PAPER", warnings);

        JsonArray overrides;
        try (ZipFile file = new ZipFile(zip)) {
            String json = new String(file.getInputStream(file.getEntry("assets/minecraft/models/item/paper.json")).readAllBytes(), StandardCharsets.UTF_8);
            overrides = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("overrides");
        }
        int previous = Integer.MIN_VALUE;
        for (JsonElement element : overrides) {
            int value = element.getAsJsonObject().getAsJsonObject("predicate").get("custom_model_data").getAsInt();
            assertTrue(value > previous, "ascending: " + overrides);
            previous = value;
        }
        // what the game draws for each part: the last override whose number is not above the part's
        for (BbModel model : List.of(added, old)) {
            for (BbModel.Part part : model.parts) {
                String drawn = null;
                for (JsonElement element : overrides) {
                    JsonObject override = element.getAsJsonObject();
                    if (override.getAsJsonObject("predicate").get("custom_model_data").getAsInt() <= part.customModelData) drawn = override.get("model").getAsString();
                }
                assertEquals(ModelPackBuilder.NAMESPACE + ":" + model.id + "/" + part.key, drawn);
            }
        }
    }
}
