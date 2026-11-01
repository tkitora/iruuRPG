package net.tkgon.mc.iruuRPG.markpack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t116: the icons of the heavy attacks on the action bar (font iruurpg:hud). */
class HudGlyphsTest {

    @Test
    void theCodePointsAreTheAgreedOnes() {
        assertEquals('', HudGlyphs.icon("warrior", 1));
        assertEquals('', HudGlyphs.icon("gifted", 5));
        assertEquals('', HudGlyphs.icon("nobody", 3));
        assertEquals('', HudGlyphs.shade(16));
        assertEquals('', HudGlyphs.digit(7));
    }

    /** The advance of a text of the font: a glyph's width + 1 (an icon 17, a digit 6), a space its own. */
    private static int advance(Map<Character, Integer> spaces, Component component) {
        int sum = 0;
        if (component instanceof TextComponent text) {
            for (char ch : text.content().toCharArray()) {
                if (spaces.containsKey(ch)) sum += spaces.get(ch);
                else if (ch >= 0xE220 && ch <= 0xE229) sum += 6;
                else sum += 17;
            }
        }
        for (Component child : component.children()) sum += advance(spaces, child);
        return sum;
    }

    @Test
    void theRowTakesNoRoomInTheLine() throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        HudGlyphs.addTo(files);
        JsonObject font = JsonParser.parseString(new String(files.get("assets/iruurpg/font/hud.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<Character, Integer> spaces = new HashMap<>();
        for (var provider : font.getAsJsonArray("providers")) {
            JsonObject object = provider.getAsJsonObject();
            if (!object.get("type").getAsString().equals("space")) continue;
            for (var entry : object.getAsJsonObject("advances").entrySet()) spaces.put(entry.getKey().charAt(0), entry.getValue().getAsInt());
        }
        assertEquals(16, spaces.size());
        List<HudGlyphs.Icon> icons = List.of(new HudGlyphs.Icon("mage", 1, 0.0, 0), new HudGlyphs.Icon("mage", 2, 0.5, 3),
                new HudGlyphs.Icon("mage", 3, 1.0, 12), new HudGlyphs.Icon("mage", 4, 0.0, 0), new HudGlyphs.Icon("mage", 5, 0.1, 1));
        assertEquals(0, advance(spaces, HudGlyphs.row(icons)));
        // 30 icons, 16 shades, 10 digits
        assertEquals(56, files.keySet().stream().filter(name -> name.endsWith(".png")).count());
    }

    @Test
    void thePackOfTheMarksCarriesTheFont() throws IOException {
        boolean found = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(MarkGlyphs.pack()))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) found |= entry.getName().equals("assets/iruurpg/font/hud.json");
        }
        assertTrue(found);
    }
}
