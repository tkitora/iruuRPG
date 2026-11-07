package net.tkgon.mc.iruuRPG.gui.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t291 (trial): the menus' own looks: their slots, the pieces of a picture over them, the pack, the title that puts the background in place. */
class MenuSkinsTest {

    @TempDir
    File folder;

    /** A picture whose every pixel says where it is: (x, y) as red and green. */
    static BufferedImage grid(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) image.setRGB(x, y, 0xFF000000 | (x << 16) | (y << 8));
        return image;
    }

    @Test
    void slotsAreNumbersAndRanges() {
        assertEquals(List.of(19, 20, 28), MenuSkin.slots("19-20, 28", 54));
        assertEquals(List.of(12, 13, 14), MenuSkin.slots(List.of(12, "13-14"), 54));
        assertNull(MenuSkin.slots("53-54", 54), "outside the menu");
        assertNull(MenuSkin.slots("a", 54));
        assertNull(MenuSkin.slots(null, 54));
    }

    @Test
    void aPictureIsCutIntoTheSlotsOfItsRectangle() {
        // slots 19, 20, 28, 29: two by two, 36 x 36 pixels
        List<Integer> slots = List.of(19, 20, 28, 29);
        List<BufferedImage> tiles = MenuSkin.cut(grid(36, 36), slots);
        assertNotNull(tiles);
        assertEquals(4, tiles.size());
        // slot 29 is the lower right: its top left pixel is (18, 18) of the picture
        assertEquals(0xFF000000 | (18 << 16) | (18 << 8), tiles.get(3).getRGB(0, 0));
        assertEquals(0xFF000000 | (35 << 16) | (35 << 8), tiles.get(3).getRGB(17, 17));
        // slot 20 the upper right
        assertEquals(0xFF000000 | (18 << 16), tiles.get(1).getRGB(0, 0));
        // a picture of another size is none
        assertNull(MenuSkin.cut(grid(32, 32), slots));
        assertNull(MenuSkin.cut(grid(36, 18), slots));
    }

    static MenuSkin skin(String id, BufferedImage background) {
        List<Integer> slots = List.of(23, 24);
        return new MenuSkin(id, background, null, TextColor.color(0x404040), true, false,
                List.of(new MenuSkin.Part("travel", slots, 24, MenuSkin.cut(grid(36, 18), slots))));
    }

    @Test
    void thePackHasTheBackgroundAsAGlyphAndAModelForEveryPiece() throws IOException {
        UiPackBuilder.Built built = UiPackBuilder.build(List.of(skin("main", grid(176, 222)), skin("stats", null)));
        // the background: a glyph standing 13 over the title's line, as high as its picture
        assertEquals('', (char) built.backgrounds().get("main"));
        assertFalse(built.backgrounds().containsKey("stats"));
        JsonObject font = JsonParser.parseString(new String(built.files().get("assets/iruurpg/font/ui.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject provider = font.getAsJsonArray("providers").get(0).getAsJsonObject();
        assertEquals(13, provider.get("ascent").getAsInt());
        assertEquals(222, provider.get("height").getAsInt());
        assertEquals("iruurpg:font/ui/main.png", provider.get("file").getAsString());
        assertNotNull(built.files().get("assets/iruurpg/textures/font/ui/main.png"));
        // the pieces: numbers in the order of the menus, the parts and the slots; one model each
        assertEquals(UiPackBuilder.FIRST, built.numbers().get("main").get(23));
        assertEquals(UiPackBuilder.FIRST + 1, built.numbers().get("main").get(24));
        assertEquals(UiPackBuilder.FIRST + 2, built.numbers().get("stats").get(23));
        BufferedImage tile = ImageIO.read(new ByteArrayInputStream(built.files().get("assets/iruurpg/textures/item/ui/main/travel_1.png")));
        assertEquals(18, tile.getWidth());
        assertEquals(0xFF000000 | (18 << 16), tile.getRGB(0, 0), "slot 24 is the right half");
        JsonObject model = JsonParser.parseString(new String(built.files().get("assets/iruurpg/models/item/ui/main/travel_1.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject element = model.getAsJsonArray("elements").get(0).getAsJsonObject();
        // from -1 to 17: the whole slot of 18 pixels where the item is 16, meeting the slot beside it
        assertEquals("[-1,-1,8]", element.get("from").toString());
        assertEquals("[17,17,8]", element.get("to").toString());
        assertEquals("front", model.get("gui_light").getAsString());
        // the item of the pieces: its overrides go up (the game takes the last one a number reaches)
        JsonObject item = JsonParser.parseString(new String(built.files().get("assets/minecraft/models/item/structure_void.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray overrides = item.getAsJsonArray("overrides");
        assertEquals(4, overrides.size());
        int last = 0;
        for (var override : overrides) {
            int number = override.getAsJsonObject().getAsJsonObject("predicate").get("custom_model_data").getAsInt();
            assertTrue(number > last);
            last = number;
        }
        // the same skins, the same bytes
        UiPackBuilder.Built again = UiPackBuilder.build(List.of(skin("stats", null), skin("main", grid(176, 222))));
        assertEquals(built.files().keySet(), again.files().keySet());
        for (String name : built.files().keySet()) assertArrayEquals(built.files().get(name), again.files().get(name), name);
        // every file of the pack is named in lower case (a capital makes the client drop the font)
        for (String name : built.files().keySet()) assertTrue(name.matches("[a-z0-9/._-]+"), name);
    }

    @Test
    void theTitlePutsTheBackgroundAtTheWindowsTopLeftAndTheTextWhereItWas() throws IOException {
        File ui = new File(folder, "ui");
        assertTrue(new File(ui, "main").mkdirs());
        ImageIO.write(grid(176, 222), "png", new File(ui, "main/back.png"));
        ImageIO.write(grid(36, 18), "png", new File(ui, "main/travel.png"));
        Files.writeString(new File(ui, "main.yml").toPath(), """
                background: main/back.png
                parts:
                  travel:
                    slots: 23-24
                    picture: main/travel.png
                    button: 24
                  broken:
                    slots: 30
                    picture: main/travel.png
                  outside:
                    slots: 60
                    picture: main/travel.png
                """);
        Files.writeString(new File(ui, "Bad.yml").toPath(), "background: main/back.png\n");
        List<String> warnings = new ArrayList<>();
        MenuSkins skins = new MenuSkins(folder, warnings::add);
        assertTrue(skins.reload(), "the pack is written");
        assertTrue(new File(folder, "resourcepacks/" + MenuSkins.PACK_FILE).isFile());
        assertFalse(skins.reload(), "the same pack: not written again");
        assertEquals(List.of("main"), skins.menus());
        // the picture of a part that is not 18 pixels a slot, a slot outside the menu and a name with a capital are told and left out
        assertEquals(6, warnings.size(), warnings.toString());
        // a click on either slot of the part is a click on its button
        assertEquals(24, skins.button("main", null, 23));
        assertEquals(24, skins.button("main", null, 24));
        assertEquals(30, skins.button("main", null, 30));
        assertEquals(23, skins.button("stats", null, 23), "a menu without a look");
        // the title: 8 left, the background (its advance is its width and 1), back to where the title is drawn, then the menu's own title
        Component own = Component.text("iruuRPG メニュー");
        Component title = skins.title("main", null, own);
        TextComponent shift = (TextComponent) title.children().get(0);
        String text = shift.content();
        int glyph = text.indexOf('');
        assertEquals(-UiPackBuilder.TITLE_X, UiPackBuilder.advance(text.substring(0, glyph)));
        assertEquals(0, UiPackBuilder.advance(text.substring(0, glyph)) + 176 + 1 + UiPackBuilder.advance(text.substring(glyph + 1)));
        assertEquals("iruurpg:ui", shift.font().asString());
        assertEquals("iruuRPG メニュー", ((TextComponent) title.children().get(1)).content());
        // a menu with no look keeps its own title
        assertSame(own, skins.title("stats", null, own));
        // a look for the testers only: nobody else sees it (no player is no tester)
        Files.writeString(new File(ui, "main.yml").toPath(), "testers-only: true\nbackground: main/back.png\n");
        skins.reload();
        assertSame(own, skins.title("main", null, own));
        assertFalse(skins.dressed("main", null));
        // no look left: the pack is taken away
        Files.delete(new File(ui, "main.yml").toPath());
        assertTrue(skins.reload());
        assertFalse(new File(folder, "resourcepacks/" + MenuSkins.PACK_FILE).exists());
    }
}
