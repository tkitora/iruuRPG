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

/** The icons of the skills on the action bar (font iruurpg:hud; t116, the redesign of 2026-11-02). */
class HudGlyphsTest {

    @Test
    void theCodePointsAreTheAgreedOnes() {
        assertEquals('', HudGlyphs.icon("warrior", 1));
        assertEquals('\uE134', HudGlyphs.icon("gifted", 4));
        assertEquals('', HudGlyphs.icon("nobody", 3));
        assertEquals('', HudGlyphs.shade(16));
        assertEquals('', HudGlyphs.digit(7));
    }

    @Test
    void aKeyLabelStandsJustAboveTheIcon() throws IOException {
        // t138: the labels of two keys, U+E240 + 4 first + second
        // the labels of the keys, U+E240 on
        assertEquals('\uE240', HudGlyphs.keyLabel("ql"));
        assertEquals('\uE244', HudGlyphs.keyLabel("sf"));
        assertEquals('\uE246', HudGlyphs.keyLabel("r"));
        // 小・中・大・util are icons 1 - 4 of each class's 16, the ULT is the special of t140 (its 7th); the weapon's skill and the lock on their own
        assertEquals('\uE101', HudGlyphs.skill("warrior", 0));
        assertEquals('\uE134', HudGlyphs.skill("gifted", 3));
        assertEquals('\uE107', HudGlyphs.skill("warrior", 4));
        assertEquals('\uE107', HudGlyphs.special("warrior"));
        assertEquals('\uE137', HudGlyphs.special("gifted"));
        assertEquals('\uE157', HudGlyphs.special("nobody"));
        assertEquals('\uE180', HudGlyphs.itemSkill());
        assertEquals('\uE181', HudGlyphs.itemSkillLocked());
        assertEquals('\uE230', HudGlyphs.lock());
        assertTrue(HudGlyphs.icon("generic", 6) < HudGlyphs.special("generic"));
        assertEquals(HudGlyphs.icon("generic", 4), HudGlyphs.icon("generic", 6), "a class has 4 icons besides the ULT's");
        Map<String, byte[]> files = new HashMap<>();
        HudGlyphs.addTo(files);
        JsonObject font = JsonParser.parseString(new String(files.get("assets/iruurpg/font/hud.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        int iconAscent = -1, keyAscent = -1, keyHeight = -1;
        for (var provider : font.getAsJsonArray("providers")) {
            JsonObject object = provider.getAsJsonObject();
            if (!object.get("type").getAsString().equals("bitmap")) continue;
            String file = object.get("file").getAsString();
            if (file.endsWith("hud/small_mage.png")) iconAscent = object.get("ascent").getAsInt();
            if (file.endsWith("key_qr.png")) {
                keyAscent = object.get("ascent").getAsInt();
                keyHeight = object.get("height").getAsInt();
                assertEquals(String.valueOf(HudGlyphs.keyLabel("qr")), object.getAsJsonArray("chars").get(0).getAsString());
            }
        }
        // the same bottom as an icon's picture, 8 rows taller: the keys are in those rows
        assertEquals(iconAscent + 8, keyAscent);
        assertEquals(keyAscent, keyHeight);
        java.awt.image.BufferedImage label = HudGlyphs.keyImage("ql");
        assertEquals(16, label.getWidth());
        assertTrue((label.getRGB(2, 1) >>> 24) > 0, "the Q's corner is drawn");
        // one key in the middle: F's top-left corner at x 6
        java.awt.image.BufferedImage alone = HudGlyphs.keyImage("f");
        assertEquals(0xFFFFFFFF, alone.getRGB(6, 1), "the F is drawn in the middle");
        assertEquals(0, alone.getRGB(2, 1) >>> 24, "and nothing at the left");
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
        // with the labels of the keys over the icons (t138): still no room
        List<HudGlyphs.Icon> labelled = List.of(new HudGlyphs.Icon("mage", 1, 0.0, 0, HudGlyphs.keyLabel("ql"), 0xFFFFFF),
                new HudGlyphs.Icon("mage", 2, 0.5, 3, HudGlyphs.keyLabel("qr"), 0xFFE14D), new HudGlyphs.Icon("mage", 3, 1.0, 12, HudGlyphs.keyLabel("f"), 0x707070));
        assertEquals(0, advance(spaces, HudGlyphs.row(labelled)));
        // t140: the special and the weapon's skill half an icon after the skills, one of them locked: still no room
        List<HudGlyphs.Icon> extras = List.of(new HudGlyphs.Icon(HudGlyphs.special("mage"), 0.4, 9, HudGlyphs.keyLabel("sf"), 0xFFFFFF, false),
                new HudGlyphs.Icon(HudGlyphs.itemSkill(), 0.0, 0, HudGlyphs.keyLabel("r"), 0xFFFFFF, true));
        assertEquals(0, advance(spaces, HudGlyphs.row(labelled, extras)));
        assertEquals(0, advance(spaces, HudGlyphs.row(List.of(), extras)));
        // 24 icons (6 classes x 小・中・大・util), 6 ULTs, the weapon's skill and its locked icon, the lock, 16 shades, 10 digits, 7 key labels
        assertEquals(66, files.keySet().stream().filter(name -> name.endsWith(".png")).count());
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
