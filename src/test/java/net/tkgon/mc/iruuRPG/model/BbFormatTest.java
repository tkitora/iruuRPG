package net.tkgon.mc.iruuRPG.model;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The game reads a model saved by Blockbench 5.0 (its groups apart from the outliner) as it reads the same model of 4.10. */
class BbFormatTest {

    /** What the game takes from a model: its bones (name, origin, turn, parent, cubes), its animations and its parts. */
    static String summary(BbModel model) {
        StringBuilder out = new StringBuilder();
        for (BbModel.Bone bone : model.bones) {
            out.append("bone ").append(bone.key).append(' ').append(bone.name).append(' ').append(Arrays.toString(bone.origin)).append(' ')
                    .append(Arrays.toString(bone.rotation)).append(" parent=").append(bone.parent == null ? "-" : bone.parent.key)
                    .append(" cubes=").append(bone.cubes.size()).append(" children=").append(bone.children.size()).append('\n');
        }
        for (Map.Entry<String, BbModel.Animation> animation : model.animations.entrySet()) {
            Map<String, String> channels = new TreeMap<>();
            for (Map.Entry<String, Map<String, java.util.List<BbModel.Keyframe>>> bone : animation.getValue().channels.entrySet()) {
                BbModel.Bone owner = model.byUuid.get(bone.getKey());
                for (Map.Entry<String, java.util.List<BbModel.Keyframe>> channel : bone.getValue().entrySet()) {
                    channels.put((owner == null ? bone.getKey() : owner.key) + "." + channel.getKey(), Integer.toString(channel.getValue().size()));
                }
            }
            out.append("animation ").append(animation.getKey()).append(' ').append(animation.getValue().length).append(' ').append(animation.getValue().loop)
                    .append(' ').append(channels).append('\n');
        }
        for (BbModel.Part part : model.parts) {
            out.append(String.format(Locale.ROOT, "part %s %s %.4f%n", part.key, Arrays.toString(part.offset), part.shrink));
        }
        return out.toString();
    }

    @Test
    void aModelOfBlockbench5IsReadWithItsGroups() {
        String json = """
                {"meta":{"format_version":"5.0","model_format":"free","box_uv":false},
                 "elements":[{"uuid":"c1","type":"cube","name":"torso","from":[-3,6,-2],"to":[3,12,2],"faces":{"north":{"uv":[0,0,6,6],"texture":0}}}],
                 "groups":[{"name":"body","uuid":"g1","origin":[0,6,0],"rotation":[0,15,0],"export":true,"visibility":true}],
                 "outliner":[{"uuid":"g1","isOpen":false,"children":["c1"]}]}
                """;
        BbModel model = BbModel.parse("m", json);
        assertEquals(1, model.bones.size());
        BbModel.Bone bone = model.bones.get(0);
        assertEquals("body", bone.name);
        assertEquals("[0.0, 6.0, 0.0]", Arrays.toString(bone.origin));
        assertEquals("[0.0, 15.0, 0.0]", Arrays.toString(bone.rotation));
        assertEquals(1, bone.cubes.size());
    }

    @Test
    void whatTheOutlinerHasItselfComesFirst() {
        String json = """
                {"meta":{"format_version":"5.0"},
                 "elements":[{"uuid":"c1","type":"cube","name":"a","from":[0,0,0],"to":[1,1,1],"faces":{"north":{"uv":[0,0,1,1],"texture":0}}}],
                 "groups":[{"name":"from-groups","uuid":"g1","origin":[1,2,3]}],
                 "outliner":[{"uuid":"g1","name":"own","children":["c1"]}]}
                """;
        BbModel model = BbModel.parse("m", json);
        assertEquals("own", model.bones.get(0).name);
        assertEquals("[1.0, 2.0, 3.0]", Arrays.toString(model.bones.get(0).origin));
    }

    @Test
    void theSampleModelReadsTheSameIn410And50() throws IOException {
        String json;
        try (InputStream in = BbFormatTest.class.getResourceAsStream("/models/sample_crawler.bbmodel")) {
            assertNotNull(in);
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String old = summary(BbModel.parse("sample_crawler", json));
        assertFalse(old.isBlank());
        assertEquals(old, summary(BbModel.parse("sample_crawler", BbFormatSamples.modern(json))));
    }

    /** {@code ./gradlew test -PbbmodelSamples=<folder of .bbmodel files>}: every model there reads the same in 4.10 and 5.0 (skipped without the folder). */
    @Test
    void theSamplesReadTheSameIn410And50() throws IOException {
        for (File file : BbFormatSamples.samples()) {
            String json = Files.readString(file.toPath());
            String id = file.getName().replace(".bbmodel", "");
            assertEquals(summary(BbModel.parse(id, json)), summary(BbModel.parse(id, BbFormatSamples.modern(json))), file.getName());
        }
    }
}
