package net.tkgon.mc.iruuRPG.itempack;

import net.tkgon.mc.iruuRPG.item.ItemModelSpec;
import net.tkgon.mc.iruuRPG.model.BbFormatSamples;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** An item's model saved by Blockbench 5.0 is held, placed and fitted to the slots as the same model of 4.10. */
class BbItemFormatTest {

    private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    private static String cube(String name, String from, String to) {
        String faces = "{\"north\":{\"uv\":[0,0,4,4],\"texture\":0},\"south\":{\"uv\":[0,0,4,4],\"texture\":0},\"east\":{\"uv\":[0,0,4,4],\"texture\":0},"
                + "\"west\":{\"uv\":[0,0,4,4],\"texture\":0},\"up\":{\"uv\":[0,0,4,4],\"texture\":0},\"down\":{\"uv\":[0,0,4,4],\"texture\":0}}";
        return "{\"name\":\"" + name + "\",\"type\":\"cube\",\"uuid\":\"" + name + "\",\"from\":[" + from + "],\"to\":[" + to + "],\"faces\":" + faces + "}";
    }

    /** A sword of 4.10: the blade in a group turned 22.5 degrees inside the sword's group, and a grip group. */
    private static String sword() {
        return "{\"meta\":{\"model_format\":\"free\",\"format_version\":\"4.10\"},\"resolution\":{\"width\":16,\"height\":16},\"elements\":["
                + cube("handle", "-0.5,0,-0.5", "0.5,7,0.5") + "," + cube("blade", "-1,7,-0.5", "1,24,0.5") + "],"
                + "\"outliner\":[{\"name\":\"sword\",\"origin\":[0,2,0],\"rotation\":[0,0,0],\"uuid\":\"g1\",\"isOpen\":true,\"children\":[\"handle\","
                + "{\"name\":\"tip\",\"origin\":[0,7,0],\"rotation\":[0,0,22.5],\"uuid\":\"g3\",\"children\":[\"blade\"]}]},"
                + "{\"name\":\"grip\",\"origin\":[0,4,0],\"uuid\":\"g2\",\"children\":[]}],"
                + "\"textures\":[{\"name\":\"sword.png\",\"source\":\"" + PNG + "\"}]}";
    }

    /** What the game takes from an item's model: the grip, the elements (box, turn, faces), the display, and the model made for each way it is held. */
    private static String summary(String id, String json) {
        BbItemModel model = BbItemModel.parse(id, json);
        StringBuilder out = new StringBuilder("grip ").append(Arrays.toString(model.grip)).append('\n');
        for (BbItemModel.Element element : model.elements) {
            out.append("element ").append(element.name).append(' ').append(Arrays.toString(element.from)).append(' ').append(Arrays.toString(element.to))
                    .append(' ').append(element.axis).append(' ').append(element.angle).append(' ').append(element.rescale).append(' ').append(element.faces.keySet()).append('\n');
        }
        for (Map.Entry<String, BbItemModel.Display> slot : model.display.entrySet()) {
            out.append("display ").append(slot.getKey()).append(' ').append(Arrays.toString(slot.getValue().rotation())).append(' ')
                    .append(Arrays.toString(slot.getValue().translation())).append(' ').append(Arrays.toString(slot.getValue().scale())).append('\n');
        }
        for (String kind : new String[]{"sword", "staff", "lantern"}) {
            List<String> warnings = new ArrayList<>();
            out.append(kind).append(' ').append(ItemModelMaker.make(model, ItemModelSpec.of(kind), kind, "iruurpg:item/bbmodel/" + id + "/", warnings)).append(' ').append(warnings).append('\n');
        }
        return out.toString();
    }

    @Test
    void aSwordOf50IsHeldAndFittedAsTheSameSwordOf410() {
        String old = sword();
        String modern = BbFormatSamples.modern(old);
        BbItemModel model = BbItemModel.parse("sword", modern);
        assertNotNull(model.grip);
        // the grip group's origin (0, 4, 0) in the game's coordinates (a Generic Model's middle at 8)
        assertEquals("[8.0, 4.0, 8.0]", Arrays.toString(model.grip));
        assertEquals(summary("sword", old), summary("sword", modern));
    }

    /** {@code ./gradlew test -PbbmodelSamples=<folder of .bbmodel files>}: every model there is held the same in 4.10 and 5.0 (skipped without the folder). */
    @Test
    void theSamplesAreHeldTheSameIn410And50() throws IOException {
        for (File file : BbFormatSamples.samples()) {
            String json = Files.readString(file.toPath());
            String id = file.getName().replace(".bbmodel", "");
            assertEquals(summary(id, json), summary(id, BbFormatSamples.modern(json)), file.getName());
        }
    }
}
