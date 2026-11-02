package net.tkgon.mc.iruuRPG.itempack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.tkgon.mc.iruuRPG.item.ItemModelSpec;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An item that looks like a Blockbench model (t154): the model read, the game's model made of it, how it is held, and the pack. */
class ItemModelMakerTest {

    /** A 1 x 1 PNG, as Blockbench embeds a texture. */
    private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    private static String cube(String name, double[] from, double[] to, String extra) {
        String faces = "{\"north\":{\"uv\":[0,0,4,4],\"texture\":0},\"south\":{\"uv\":[0,0,4,4],\"texture\":0},\"east\":{\"uv\":[0,0,4,4],\"texture\":0},"
                + "\"west\":{\"uv\":[0,0,4,4],\"texture\":0},\"up\":{\"uv\":[0,0,4,4],\"texture\":0},\"down\":{\"uv\":[0,0,4,4],\"texture\":0}}";
        return "{\"name\":\"" + name + "\",\"type\":\"cube\",\"uuid\":\"" + name + "\",\"from\":[" + from[0] + "," + from[1] + "," + from[2] + "],\"to\":["
                + to[0] + "," + to[1] + "," + to[2] + "],\"faces\":" + faces + extra + "}";
    }

    /** A standing sword of a Generic Model: 2 wide, 24 tall, its grip marked at y = 4. */
    private static String sword(String format, String display) {
        return "{\"meta\":{\"model_format\":\"" + format + "\"},\"resolution\":{\"width\":16,\"height\":16},\"elements\":["
                + cube("grip", new double[]{-0.5, 0, -0.5}, new double[]{0.5, 7, 0.5}, "") + ","
                + cube("blade", new double[]{-1, 7, -0.5}, new double[]{1, 24, 0.5}, "")
                + "],\"outliner\":[{\"name\":\"sword\",\"origin\":[0,0,0],\"uuid\":\"g1\",\"children\":[\"grip\",\"blade\"]},{\"name\":\"grip\",\"origin\":[0,4,0],\"uuid\":\"g2\",\"children\":[]}],"
                + "\"textures\":[{\"name\":\"Sword.png\",\"source\":\"" + PNG + "\"}]" + display + "}";
    }

    private static double[] numbers(JsonArray array) {
        double[] values = new double[array.size()];
        for (int index = 0; index < values.length; index++) values[index] = array.get(index).getAsDouble();
        return values;
    }

    /** Where a point of the model (from its middle) goes by a slot's display setting, as the game does it. */
    private static double[] place(JsonObject slot, double[] point) {
        double[][] turn = Rotation.display(numbers(slot.getAsJsonArray("rotation")));
        double[] scale = numbers(slot.getAsJsonArray("scale"));
        double[] translation = numbers(slot.getAsJsonArray("translation"));
        return Rotation.plus(translation, Rotation.apply(turn, new double[]{point[0] * scale[0], point[1] * scale[1], point[2] * scale[2]}));
    }

    private static void near(double[] expected, double[] actual, double tolerance, String what) {
        for (int axis = 0; axis < 3; axis++) assertEquals(expected[axis], actual[axis], tolerance, what + " axis " + axis);
    }

    @Test
    void aGenericModelIsReadInTheGamesCoordinatesWithItsGrip() {
        BbItemModel model = BbItemModel.parse("sword", sword("free", ""));
        assertEquals(2, model.elements.size());
        // a Generic Model has its middle at x = z = 0: the game's model has it at 8
        assertEquals(7.5, model.elements.get(0).from[0], 1e-9);
        assertEquals(8.5, model.elements.get(0).to[2], 1e-9);
        assertEquals(4.0, model.grip[1], 1e-9);
        assertEquals(8.0, model.grip[0], 1e-9);
        // the texture's name is made safe for a pack path (no capital letters)
        assertEquals("sword", model.textures.get(0).name());
        assertTrue(model.warnings.isEmpty(), model.warnings.toString());
    }

    @Test
    void aTurnTheGameCannotDoIsTheNearestOneWithAWarning() {
        String json = "{\"meta\":{\"model_format\":\"free\"},\"elements\":["
                + cube("ok", new double[]{0, 0, 0}, new double[]{2, 2, 2}, ",\"rotation\":[0,22.5,0],\"origin\":[1,1,1]") + ","
                + cube("odd", new double[]{0, 4, 0}, new double[]{2, 6, 2}, ",\"rotation\":[0,30,0],\"origin\":[1,5,1]") + ","
                + cube("inner", new double[]{0, 8, 0}, new double[]{2, 10, 2}, "")
                + "],\"outliner\":[\"ok\",\"odd\",{\"name\":\"tilted\",\"origin\":[1,8,1],\"rotation\":[0,0,45],\"uuid\":\"t\",\"children\":[\"inner\"]}],"
                + "\"textures\":[{\"name\":\"t\",\"source\":\"" + PNG + "\"}]}";
        BbItemModel model = BbItemModel.parse("m", json);
        assertEquals("y", model.elements.get(0).axis);
        assertEquals(22.5, model.elements.get(0).angle);
        // 30 degrees is not allowed: 22.5, and the player is told
        assertEquals(22.5, model.elements.get(1).angle);
        assertTrue(model.warnings.stream().anyMatch(w -> w.contains("'odd'")), model.warnings.toString());
        // a cube in a turned group takes the group's turn (about the group's origin)
        assertEquals("z", model.elements.get(2).axis);
        assertEquals(45.0, model.elements.get(2).angle);
        assertEquals(1, model.warnings.size(), model.warnings.toString());
    }

    @Test
    void displayAnglesAreTheInverseOfTheGamesTurn() {
        Random random = new Random(7);
        for (int run = 0; run < 200; run++) {
            double[] angles = {random.nextDouble() * 360 - 180, random.nextDouble() * 170 - 85, random.nextDouble() * 360 - 180};
            double[][] turn = Rotation.display(angles);
            double[][] again = Rotation.display(Rotation.displayAngles(turn));
            assertTrue(Rotation.distance(turn, again) < 1e-9, java.util.Arrays.toString(angles));
        }
        // straight up: still the same turn
        double[][] turn = Rotation.display(new double[]{30, 90, 10});
        assertTrue(Rotation.distance(turn, Rotation.display(Rotation.displayAngles(turn))) < 1e-9);
    }

    @Test
    void aSwordIsHeldByItsGripAlongTheVanillaSwordsPicture() {
        BbItemModel model = BbItemModel.parse("sword", sword("free", ""));
        List<String> warnings = new ArrayList<>();
        JsonObject json = ItemModelMaker.make(model, ItemModelSpec.of("sword"), "sword", "iruurpg:item/bbmodel/sword/", warnings);
        assertNotNull(json);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals("iruurpg:item/bbmodel/sword/sword", json.getAsJsonObject("textures").get("0").getAsString());
        // the elements fit the game's model (-16 to 32)
        for (JsonElement element : json.getAsJsonArray("elements")) {
            for (double value : numbers(element.getAsJsonObject().getAsJsonArray("from"))) assertTrue(value >= -16 && value <= 32);
            for (double value : numbers(element.getAsJsonObject().getAsJsonArray("to"))) assertTrue(value >= -16 && value <= 32);
        }
        // the model's middle is (8, 8, 8): the grip (y = 4 of a 0 - 24 sword) is 8 below it
        double[] grip = {0, -8, 0};
        JsonObject display = json.getAsJsonObject("display");
        // in the third person the grip is where the vanilla setting puts the vanilla sword's grip: at the hand (the origin)
        near(new double[]{0, 0, 0}, place(display.getAsJsonObject("thirdperson_righthand"), grip), 1e-2, "grip at the hand");
        // and the blade goes the way the vanilla sword's does (the picture's diagonal)
        double[] tip = place(display.getAsJsonObject("thirdperson_righthand"), new double[]{0, 4, 0});
        double[] vanillaDirection = Rotation.apply(ItemModelMaker.HANDHELD_3P.turn(), new double[]{Math.sqrt(0.5), Math.sqrt(0.5), 0});
        double[] direction = Rotation.minus(tip, place(display.getAsJsonObject("thirdperson_righthand"), grip));
        double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
        near(vanillaDirection, Rotation.times(direction, 1.0 / length), 1e-3, "blade direction");
        // its own size: 0.85 like the vanilla sword
        assertEquals(0.85, display.getAsJsonObject("thirdperson_righthand").getAsJsonArray("scale").get(0).getAsDouble(), 1e-6);
        // the slot: the whole model inside the 16 x 16 square
        JsonObject gui = display.getAsJsonObject("gui");
        double highest = 0;
        for (JsonElement element : json.getAsJsonArray("elements")) {
            JsonObject e = element.getAsJsonObject();
            double[] from = numbers(e.getAsJsonArray("from"));
            double[] to = numbers(e.getAsJsonArray("to"));
            for (int corner = 0; corner < 8; corner++) {
                double[] point = {((corner & 1) == 0 ? from[0] : to[0]) - 8, ((corner & 2) == 0 ? from[1] : to[1]) - 8, ((corner & 4) == 0 ? from[2] : to[2]) - 8};
                double[] placed = place(gui, point);
                assertTrue(Math.abs(placed[0]) <= 8.001 && Math.abs(placed[1]) <= 8.001, java.util.Arrays.toString(placed));
                highest = Math.max(highest, Math.max(Math.abs(placed[0]), Math.abs(placed[1])));
            }
        }
        assertEquals(8.0, highest, 1e-2, "the model fills the slot");
        assertEquals("front", json.get("gui_light").getAsString());
    }

    @Test
    void theYmlChangesHowItIsHeld() {
        BbItemModel model = BbItemModel.parse("sword", sword("free", ""));
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("model.id", "sword");
        yml.set("model.hold", "staff");
        yml.set("model.scale", 2);
        yml.set("model.hand.rotation", List.of(0, 0, 0));
        yml.set("model.hand.translation", List.of(0, -2, 0));
        yml.set("model.display.ground.rotation", List.of(1, 2, 3));
        yml.set("model.display.ground.scale", 0.25);
        yml.set("model.display.nowhere.scale", 1);
        List<String> problems = new ArrayList<>();
        ItemModelSpec spec = ItemModelSpec.read(yml.get("model"), problems::add);
        assertEquals("staff", spec.hold());
        assertEquals(1, problems.size(), problems.toString());
        JsonObject json = ItemModelMaker.make(model, spec, "sword", "p/", new ArrayList<>());
        JsonObject display = json.getAsJsonObject("display");
        // twice the size, and held 2 lower on the model (the grip moves up the model by 2, so the point 2 above the grip is at the hand)
        assertEquals(1.7, display.getAsJsonObject("thirdperson_righthand").getAsJsonArray("scale").get(0).getAsDouble(), 1e-6);
        near(new double[]{0, 0, 0}, place(display.getAsJsonObject("thirdperson_righthand"), new double[]{0, -8 + 2, 0}), 1e-2, "held 2 higher");
        // a slot given exactly is exactly that
        JsonObject ground = display.getAsJsonObject("ground");
        assertEquals("[1.0,2.0,3.0]", ground.getAsJsonArray("rotation").toString());
        assertEquals("[0.25,0.25,0.25]", ground.getAsJsonArray("scale").toString());
        // a lantern is held like an item's picture: fitted into its square, standing
        JsonObject lantern = ItemModelMaker.make(model, ItemModelSpec.read(Map.of("id", "sword", "hold", "lantern"), problems::add), "sword", "p/", new ArrayList<>());
        JsonObject third = lantern.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand");
        assertEquals("[0.0,0.0,0.0]", third.getAsJsonArray("rotation").toString());
        assertEquals(0.55 * 16 / 24, third.getAsJsonArray("scale").get(0).getAsDouble(), 1e-3);
        assertEquals("side", lantern.get("gui_light").getAsString());
        // a name only
        assertEquals(ItemModelSpec.of("sword"), ItemModelSpec.read("sword", problems::add));
        assertNull(ItemModelSpec.read("Sword", problems::add));
    }

    @Test
    void aJavaModelKeepsItsPlaceAndItsBlockbenchDisplay() {
        String display = ",\"display\":{\"thirdperson_righthand\":{\"rotation\":[10,20,30],\"translation\":[1,2,3],\"scale\":[0.5,0.6,0.7]}}";
        BbItemModel model = BbItemModel.parse("sword", sword("java_block", display));
        // a Java block/item model's grid is a block's 0 - 16: nothing is moved
        assertEquals(-0.5, model.elements.get(0).from[0], 1e-9);
        JsonObject json = ItemModelMaker.make(model, ItemModelSpec.of("sword"), "sword", "p/", new ArrayList<>());
        JsonObject third = json.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand");
        assertEquals("[10.0,20.0,30.0]", third.getAsJsonArray("rotation").toString());
        assertEquals("[0.5,0.6,0.7]", third.getAsJsonArray("scale").toString());
        assertEquals(-0.5, json.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("from").get(0).getAsDouble(), 1e-9);
        // the slots it leaves out are made (the slot, the ground, ...)
        assertTrue(json.getAsJsonObject("display").has("gui"));
        // asked for another way to hold, it is moved to the middle and held that way
        JsonObject held = ItemModelMaker.make(model, new ItemModelSpec("sword", "sword", 1, null, null, null), "item", "p/", new ArrayList<>());
        assertEquals(8.0, numbers(held.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("from"))[0] + 0.5, 1e-9);
    }

    @Test
    void aModelLongerThanAGameModelIsMadeSmallerAndShownAsBigAgain() {
        String json = "{\"meta\":{\"model_format\":\"free\"},\"elements\":[" + cube("pole", new double[]{-1, 0, -1}, new double[]{1, 92, 1}, "")
                + "],\"outliner\":[\"pole\"],\"textures\":[{\"name\":\"t\",\"source\":\"" + PNG + "\"}]}";
        BbItemModel model = BbItemModel.parse("pole", json);
        JsonObject made = ItemModelMaker.make(model, ItemModelSpec.of("pole"), "staff", "p/", new ArrayList<>());
        JsonObject element = made.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(-15.0, element.getAsJsonArray("from").get(1).getAsDouble(), 1e-6);
        assertEquals(31.0, element.getAsJsonArray("to").get(1).getAsDouble(), 1e-6);
        // half as long in the model, twice as big in the hand: 0.85 x 2
        assertEquals(1.7, made.getAsJsonObject("display").getAsJsonObject("thirdperson_righthand").getAsJsonArray("scale").get(0).getAsDouble(), 1e-6);
    }

    @Test
    void theHoldOfAnItemsKind() {
        assertEquals("sword", ItemModelMaker.defaultHold("MELEE", "IRON_SWORD"));
        assertEquals("staff", ItemModelMaker.defaultHold("RANGE", "BLAZE_ROD"));
        assertEquals("bow", ItemModelMaker.defaultHold("RANGE", "BOW"));
        assertEquals("item", ItemModelMaker.defaultHold("DEPLOY", "SEA_LANTERN"));
        assertEquals("item", ItemModelMaker.defaultHold(null, "STICK"));
    }

    @Test
    void thePackHasTheModelItsTexturesAndTheOverride(@TempDir File folder) throws IOException {
        ItemCmdIndex index = new ItemCmdIndex(new File(folder, "index.yml"));
        index.load();
        Map<String, String> bbmodels = Map.of("sword", sword("free", ""));
        ItemPackBuilder.Result result = ItemPackBuilder.build(List.of(
                        new ItemPackBuilder.Look("blade_a", "IRON_SWORD", null, ItemModelSpec.of("sword"), "MELEE"),
                        new ItemPackBuilder.Look("blade_b", "BLAZE_ROD", "stick", new ItemModelSpec("sword", "staff", 1, null, null, null), "RANGE"),
                        new ItemPackBuilder.Look("lost", "IRON_SWORD", "bone", ItemModelSpec.of("missing"), "MELEE")),
                index, ItemModelMakerTest::template, name -> true, name -> null, bbmodels::get);
        Map<String, byte[]> files = unzip(result.zip());
        int a = result.numbers().get("blade_a");
        int b = result.numbers().get("blade_b");
        assertNotNull(files.get("assets/iruurpg/models/item/bbmodel/sword/" + a + ".json"));
        assertNotNull(files.get("assets/iruurpg/models/item/bbmodel/sword/" + b + ".json"));
        // the texture once, decoded from the model
        assertEquals(Base64.getDecoder().decode(PNG.substring(PNG.indexOf(',') + 1)).length, files.get("assets/iruurpg/textures/item/bbmodel/sword/sword.png").length);
        String sword = new String(files.get("assets/minecraft/models/item/iron_sword.json"), StandardCharsets.UTF_8);
        assertTrue(sword.contains("\"iruurpg:item/bbmodel/sword/" + a + "\""), sword);
        assertTrue(new String(files.get("assets/minecraft/models/item/blaze_rod.json"), StandardCharsets.UTF_8).contains("iruurpg:item/bbmodel/sword/" + b));
        // a model that is not there: the item wears its texture's look, and the log says why
        assertTrue(result.numbers().containsKey("lost"));
        assertTrue(files.containsKey("assets/iruurpg/models/item/handheld/bone.json"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("models/missing.bbmodel")), result.warnings().toString());
        // every path of the pack is lower case (one capital letter makes the game drop a pack's font)
        for (String path : files.keySet()) assertTrue(path.matches("[a-z0-9/._-]+"), path);
    }

    /**
     * {@code ./gradlew test -PitemModelSamples=<folder of .bbmodel files>}: makes the pack of those models (held each way) into
     * {@code build/item-model-samples/}, to look at; skipped without the folder.
     */
    @Test
    void samples() throws IOException {
        String folder = System.getProperty("itemModel.samples", "");
        if (folder.isBlank()) return;
        File[] files = new File(folder).listFiles((dir, name) -> name.endsWith(".bbmodel"));
        assertNotNull(files, folder);
        File out = new File("build/item-model-samples");
        Files.createDirectories(out.toPath());
        ItemCmdIndex index = new ItemCmdIndex(new File(out, "index.yml"));
        index.load();
        Map<String, String> texts = new TreeMap<>();
        List<ItemPackBuilder.Look> looks = new ArrayList<>();
        for (File file : files) {
            String id = file.getName().replace(".bbmodel", "");
            texts.put(id, Files.readString(file.toPath()));
            String kind = id.contains("lantern") ? "DEPLOY" : id.contains("staff") ? "RANGE" : "MELEE";
            String base = id.contains("lantern") ? "SEA_LANTERN" : id.contains("staff") ? "BLAZE_ROD" : "IRON_SWORD";
            looks.add(new ItemPackBuilder.Look(id, base, null, ItemModelSpec.of(id), kind));
        }
        ItemPackBuilder.Result result = ItemPackBuilder.build(looks, index, ItemModelMakerTest::template, name -> true, name -> null, texts::get);
        Files.write(new File(out, "iruuitem.zip").toPath(), result.zip());
        Files.writeString(new File(out, "numbers.txt").toPath(), result.numbers() + "\n" + String.join("\n", result.warnings()));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    private static String template(String base) {
        try (InputStream in = ItemModelMakerTest.class.getResourceAsStream("/itempack/base/" + base + ".json")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) files.put(entry.getName(), in.readAllBytes());
        }
        return files;
    }
}
