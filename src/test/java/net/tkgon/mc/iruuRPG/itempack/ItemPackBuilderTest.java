package net.tkgon.mc.iruuRPG.itempack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPackBuilderTest {

    /** The vanilla models the plugin carries. */
    private static String template(String base) {
        try (InputStream in = ItemPackBuilderTest.class.getResourceAsStream("/itempack/base/" + base + ".json")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Stands in for the game: block items and the items the game draws itself are not flat pictures. */
    private static boolean flat(String name) {
        return !Set.of("stone", "sea_lantern", "glowstone", "white_stained_glass", "carved_pumpkin", "spruce_fence_gate", "wither_skeleton_skull", "shield").contains(name);
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) files.put(entry.getName(), in.readAllBytes());
        }
        return files;
    }

    private static JsonObject json(Map<String, byte[]> files, String path) {
        byte[] bytes = files.get(path);
        assertNotNull(bytes, path + " is in the pack");
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static ItemPackBuilder.Result build(File folder, ItemPackBuilder.Look... looks) throws IOException {
        ItemCmdIndex index = new ItemCmdIndex(new File(folder, "index.yml"));
        index.load();
        ItemPackBuilder.Result result = ItemPackBuilder.build(List.of(looks), index, ItemPackBuilderTest::template, ItemPackBuilderTest::flat);
        index.save();
        return result;
    }

    @Test
    void everyVanillaModelTheBuilderNeedsIsCarried() {
        for (String base : List.of("iron_sword", "iron_axe", "iron_hoe", "bow", "crossbow", "blaze_rod", "stick", "glowstone", "sea_lantern",
                "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots")) {
            String text = template(base);
            assertNotNull(text, base);
            assertTrue(JsonParser.parseString(text).isJsonObject(), base);
        }
    }

    @Test
    void aSwordWithAnotherLookGetsAnOverrideAndASharedModel(@TempDir File folder) throws IOException {
        ItemPackBuilder.Result result = build(folder,
                new ItemPackBuilder.Look("bone_dagger", "IRON_SWORD", "BONE"),
                new ItemPackBuilder.Look("bone_dagger_2", "IRON_SWORD", "bone"),
                new ItemPackBuilder.Look("plain_sword", "IRON_SWORD", "IRON_SWORD"),
                new ItemPackBuilder.Look("no_look", "IRON_SWORD", ""));
        Map<String, byte[]> files = unzip(result.zip());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        assertEquals(Set.of("bone_dagger", "bone_dagger_2"), result.numbers().keySet(), "an item with its base's own look has no number");

        JsonObject sword = json(files, "assets/minecraft/models/item/iron_sword.json");
        assertEquals("minecraft:item/handheld", sword.get("parent").getAsString());
        JsonArray overrides = sword.getAsJsonArray("overrides");
        assertEquals(2, overrides.size());
        assertEquals(result.numbers().get("bone_dagger"), overrides.get(0).getAsJsonObject().getAsJsonObject("predicate").get("custom_model_data").getAsInt());
        assertEquals("iruurpg:item/handheld/bone", overrides.get(0).getAsJsonObject().get("model").getAsString());

        JsonObject model = json(files, "assets/iruurpg/models/item/handheld/bone.json");
        assertEquals("minecraft:item/handheld", model.get("parent").getAsString(), "held like a sword");
        assertEquals("minecraft:item/bone", model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void theNumbersAreStableWhenItemsAreAddedOrTheOrderChanges(@TempDir File folder) throws IOException {
        ItemPackBuilder.Result first = build(folder, new ItemPackBuilder.Look("a", "IRON_SWORD", "BONE"), new ItemPackBuilder.Look("b", "BOW", "STICK"));
        ItemPackBuilder.Result second = build(folder, new ItemPackBuilder.Look("c", "IRON_AXE", "STONE_AXE"),
                new ItemPackBuilder.Look("b", "BOW", "STICK"), new ItemPackBuilder.Look("a", "IRON_SWORD", "BONE"));
        assertEquals(first.numbers().get("a"), second.numbers().get("a"));
        assertEquals(first.numbers().get("b"), second.numbers().get("b"));
        assertTrue(second.numbers().get("c") > Math.max(first.numbers().get("a"), first.numbers().get("b")), "a new item gets a new number");
        assertEquals(3, new java.util.HashSet<>(second.numbers().values()).size(), "no number twice");
        assertTrue(first.numbers().values().stream().allMatch(n -> n >= ItemCmdIndex.FIRST));
    }

    @Test
    void theSameInputMakesTheSameBytes(@TempDir File folder) throws IOException {
        ItemPackBuilder.Look[] looks = {new ItemPackBuilder.Look("a", "IRON_SWORD", "BONE"), new ItemPackBuilder.Look("b", "STICK", "STRING")};
        assertArrayEquals(build(folder, looks).zip(), build(folder, looks).zip());
    }

    @Test
    void theBowKeepsItsDrawAndItsDisplayAndOnlyTheLookChanges(@TempDir File folder) throws IOException {
        int vanilla = JsonParser.parseString(template("bow")).getAsJsonObject().getAsJsonArray("overrides").size();
        ItemPackBuilder.Result result = build(folder, new ItemPackBuilder.Look("odd_bow", "BOW", "BONE"));
        Map<String, byte[]> files = unzip(result.zip());
        JsonObject bow = json(files, "assets/minecraft/models/item/bow.json");
        assertTrue(bow.has("display"), "the way a bow is held stays");
        assertEquals(vanilla + 1, bow.getAsJsonArray("overrides").size(), "the draw overrides of the game stay; ours is after them");
        JsonObject last = bow.getAsJsonArray("overrides").get(vanilla).getAsJsonObject();
        assertEquals("iruurpg:item/bow/bone", last.get("model").getAsString());
        assertEquals("minecraft:item/bow", json(files, "assets/iruurpg/models/item/bow/bone.json").get("parent").getAsString());
    }

    @Test
    void aBlockLookKeepsItsWholeVanillaModel(@TempDir File folder) throws IOException {
        ItemPackBuilder.Result result = build(folder,
                new ItemPackBuilder.Look("stone_turret", "SEA_LANTERN", "STONE"),
                new ItemPackBuilder.Look("rod_with_stone", "BLAZE_ROD", "STONE"));
        Map<String, byte[]> files = unzip(result.zip());
        JsonObject turret = json(files, "assets/iruurpg/models/item/flat/stone.json");
        assertEquals("minecraft:item/stone", turret.get("parent").getAsString());
        assertFalse(turret.has("textures"));
        // a rod held like a tool but looking like a block: the block's model, not a flat picture of it
        assertEquals("minecraft:item/stone", json(files, "assets/iruurpg/models/item/handheld/stone.json").get("parent").getAsString());
    }

    @Test
    void anArmorLookIsTheWholeVanillaModelOfTheOtherArmor(@TempDir File folder) throws IOException {
        ItemPackBuilder.Result result = build(folder, new ItemPackBuilder.Look("dive_chest", "LEATHER_CHESTPLATE", "DIAMOND_CHESTPLATE"));
        Map<String, byte[]> files = unzip(result.zip());
        assertEquals("minecraft:item/diamond_chestplate", json(files, "assets/iruurpg/models/item/flat/diamond_chestplate.json").get("parent").getAsString());
        JsonObject base = json(files, "assets/minecraft/models/item/leather_chestplate.json");
        assertTrue(base.getAsJsonArray("overrides").size() > 1, "the trims of the game stay");
    }

    @Test
    void problemsAreWarningsAndTheItemKeepsItsLook(@TempDir File folder) throws IOException {
        ItemPackBuilder.Result result = build(folder,
                new ItemPackBuilder.Look("bad_name", "IRON_SWORD", "my/texture"),
                new ItemPackBuilder.Look("odd_base", "PLAYER_HEAD", "BONE"),
                new ItemPackBuilder.Look("fine", "IRON_SWORD", "minecraft:Bone"));
        assertEquals(2, result.warnings().size(), result.warnings().toString());
        assertEquals(Set.of("fine"), result.numbers().keySet());
        assertNull(unzip(result.zip()).get("assets/minecraft/models/item/player_head.json"));
        assertTrue(unzip(result.zip()).containsKey("assets/iruurpg/models/item/handheld/bone.json"), "minecraft: and capitals are accepted");
    }

    @Test
    void theIndexIsKeptInAFileAndNeverReusesANumber(@TempDir File folder) throws IOException {
        File file = new File(folder, "index.yml");
        ItemCmdIndex first = new ItemCmdIndex(file);
        first.load();
        int a = first.assign("a");
        int odd = first.assign("odd:id \"x\"");
        first.save();
        ItemCmdIndex second = new ItemCmdIndex(file);
        second.load();
        assertEquals(a, second.get("a"));
        assertEquals(odd, second.get("odd:id \"x\""));
        assertEquals(odd + 1, second.assign("new"));
    }

    @Test
    void aLookWithAPictureOfItsOwnUsesThatPicture(@TempDir File folder) throws IOException {
        ItemCmdIndex index = new ItemCmdIndex(new File(folder, "index.yml"));
        index.load();
        byte[] green = {1, 2, 3};
        ItemPackBuilder.Result result = ItemPackBuilder.build(List.of(new ItemPackBuilder.Look("moon_herb", "STICK", "FERN")), index,
                ItemPackBuilderTest::template, ItemPackBuilderTest::flat, name -> name.equals("fern") ? green : null);
        Map<String, byte[]> files = unzip(result.zip());
        assertArrayEquals(green, files.get("assets/iruurpg/textures/item/custom/fern.png"));
        JsonObject model = json(files, "assets/iruurpg/models/item/flat/fern.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("iruurpg:item/custom/fern", model.getAsJsonObject("textures").get("layer0").getAsString());
    }
}
