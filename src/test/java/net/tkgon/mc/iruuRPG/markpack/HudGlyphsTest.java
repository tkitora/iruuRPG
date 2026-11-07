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
    void aChargedIconShowsItsRimItsChargesAndItsRefill() {
        String text = flat(HudGlyphs.row(List.of(new HudGlyphs.Icon(HudGlyphs.icon("mage", 1), 0.0, 0, (char) 0, 0xFFFFFF, false, false, true, 2, 3, 0.5))));
        assertTrue(text.indexOf(HudGlyphs.recast()) >= 0, "the rim of the open re-activation");
        assertTrue(text.indexOf(HudGlyphs.charges(2)) >= 0, "2 charges left");
        assertTrue(text.indexOf(HudGlyphs.refill(8)) >= 0, "half of the next charge: 8 of 16 pixels");
        // a skill of one charge shows neither its charges nor a refill
        String single = flat(HudGlyphs.row(List.of(new HudGlyphs.Icon(HudGlyphs.icon("mage", 1), 0.0, 0, (char) 0, 0xFFFFFF, false, false, false, 0, 1, 0.5))));
        assertTrue(single.chars().noneMatch(ch -> ch >= 0xE250 && ch <= 0xE270), single);
        // the gauge is the row under the icon: in the gap above the line of text
        java.awt.image.BufferedImage gauge = HudGlyphs.refillImage(4);
        assertEquals(1, gauge.getHeight());
        assertEquals(0xFFFFFFFF, gauge.getRGB(3, 0));
        assertTrue((gauge.getRGB(4, 0) & 0xFFFFFF) < 0x404040, "the rest is dark");
        // the charges at the lower right corner
        java.awt.image.BufferedImage count = HudGlyphs.chargesImage(1);
        assertEquals(0, count.getRGB(2, 2) >>> 24);
        assertTrue(count.getRGB(HudGlyphs.ICON - 3, HudGlyphs.ICON - 4) >>> 24 > 0, "the digit 1 is drawn in the corner");
    }

    private static String flat(Component component) {
        StringBuilder text = new StringBuilder(component instanceof TextComponent t ? t.content() : "");
        for (Component child : component.children()) text.append(flat(child));
        return text.toString();
    }

    @Test
    void theCodePointsAreTheAgreedOnes() {
        // 小・中・大・util of each class and kind of weapon: U+E400 + 16 class + 4 kind + 0 - 3
        assertEquals('\uE400', HudGlyphs.icon("warrior", 1));
        assertEquals('\uE400', HudGlyphs.icon("warrior", 0, 1));
        assertEquals('\uE437', HudGlyphs.icon("gifted", 1, 4));
        assertEquals('\uE456', HudGlyphs.icon("nobody", 1, 3));
        assertEquals('', HudGlyphs.shade(16));
        assertEquals('', HudGlyphs.digit(7));
        // t157
        assertEquals('\uE232', HudGlyphs.recast());
        assertEquals('\uE253', HudGlyphs.charges(3));
        assertEquals('\uE270', HudGlyphs.refill(16));
    }

    @Test
    void aKeyLabelStandsJustAboveTheIcon() throws IOException {
        // t138: the labels of two keys, U+E240 + 4 first + second
        // the labels of the keys, U+E240 on
        assertEquals('\uE240', HudGlyphs.keyLabel("ql"));
        assertEquals('\uE244', HudGlyphs.keyLabel("sf"));
        assertEquals('\uE246', HudGlyphs.keyLabel("r"));
        // 小・中・大・util are icons 1 - 4 of each class's 16, the ULT is the special of t140 (its 7th); the weapon's skill and the lock on their own
        assertEquals('\uE400', HudGlyphs.skill("warrior", 0, 0));
        assertEquals('\uE404', HudGlyphs.skill("warrior", 1, 0));
        assertEquals('\uE437', HudGlyphs.skill("gifted", 1, 3));
        // the ULT is one for every kind of weapon
        assertEquals('\uE107', HudGlyphs.skill("warrior", 1, 4));
        assertEquals('\uE107', HudGlyphs.special("warrior"));
        assertEquals('\uE137', HudGlyphs.special("gifted"));
        assertEquals('\uE157', HudGlyphs.special("nobody"));
        assertEquals('\uE180', HudGlyphs.itemSkill());
        assertEquals('\uE181', HudGlyphs.itemSkillLocked());
        assertEquals('\uE230', HudGlyphs.lock());
        // a deployed weapon has no 小・中・大・util (its row is the ULT and its own skill); the ULT's rim of a re-activation
        assertEquals(0, HudGlyphs.kindIndex(net.tkgon.mc.iruuRPG.combat.AttackType.DEPLOY));
        assertEquals('\uE231', HudGlyphs.highlight());
        assertEquals(0, HudGlyphs.kindIndex(net.tkgon.mc.iruuRPG.combat.AttackType.SPECIAL));
        assertEquals(HudGlyphs.icon("generic", 1, 4), HudGlyphs.icon("generic", 1, 6), "a class has 4 icons a kind besides the ULT's");
        Map<String, byte[]> files = new HashMap<>();
        HudGlyphs.addTo(files);
        JsonObject font = JsonParser.parseString(new String(files.get("assets/iruurpg/font/hud.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        int iconAscent = -1, keyAscent = -1, keyHeight = -1;
        for (var provider : font.getAsJsonArray("providers")) {
            JsonObject object = provider.getAsJsonObject();
            if (!object.get("type").getAsString().equals("bitmap")) continue;
            String file = object.get("file").getAsString();
            if (file.endsWith("hud/small_mage_range.png")) iconAscent = object.get("ascent").getAsInt();
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
        // a blinking ULT (its rim on) and a locked one: still no room
        assertEquals(0, advance(spaces, HudGlyphs.row(List.of(new HudGlyphs.Icon(HudGlyphs.special("mage"), 0.5, 40, HudGlyphs.keyLabel("sf"), 0xFFFFFF, false, true)), extras)));
        // t157: an open re-activation's rim, the charges left and the refill's gauge: still no room
        List<HudGlyphs.Icon> charged = List.of(
                new HudGlyphs.Icon(HudGlyphs.icon("mage", 1), 0.0, 0, HudGlyphs.keyLabel("ql"), 0xFFE14D, false, false, true, 2, 3, 0.4),
                new HudGlyphs.Icon(HudGlyphs.icon("mage", 2), 0.0, 0, HudGlyphs.keyLabel("qr"), 0xFFFFFF, false, false, false, 0, 2, 0.9),
                new HudGlyphs.Icon(HudGlyphs.icon("mage", 3), 0.0, 0, HudGlyphs.keyLabel("qq"), 0xFFFFFF, false, false, true, 1, 1, -1.0));
        assertEquals(0, advance(spaces, HudGlyphs.row(charged, extras)));
        // 48 icons (6 classes x melee and ranged x 小・中・大・util), 6 ULTs, the weapon's skill and its locked icon, the lock, the rim, 16 shades, 10 digits, 7 key labels;
        // t157: the rim of a re-activation, 10 charges, 17 gauges of a refill; t162: 5 key labels with Q and F swapped; t291: the veil
        assertEquals(125, files.keySet().stream().filter(name -> name.endsWith(".png")).count());
        // (F → left has the arrow after F as Q → left has after Q: the two differ only in their first letter, x 1 - 5)
        java.awt.image.BufferedImage fl = HudGlyphs.keyImage("fl");
        java.awt.image.BufferedImage ql = HudGlyphs.keyImage("ql");
        boolean differs = false;
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < HudGlyphs.ICON; x++) {
                if (x >= 6) assertEquals(ql.getRGB(x, y), fl.getRGB(x, y), "the arrow and the left button at " + x + ", " + y);
                else differs |= ql.getRGB(x, y) != fl.getRGB(x, y);
            }
        }
        assertTrue(differs, "F, not Q");
    }

    @Test
    void theVeilCoversTheScreenInATitle() throws IOException {
        assertEquals('\uE2E0', HudGlyphs.veil());
        // three veils overlapping by a pixel: no seam
        String veils = HudGlyphs.veils();
        assertEquals(3, veils.chars().filter(ch -> ch == HudGlyphs.veil()).count());
        assertEquals(HudGlyphs.move(-2), veils.substring(1, veils.indexOf(HudGlyphs.veil(), 1)));
        Map<String, byte[]> files = new HashMap<>();
        HudGlyphs.addTo(files);
        assertTrue(files.containsKey("assets/iruurpg/textures/font/hud/veil.png"));
        JsonObject font = JsonParser.parseString(new String(files.get("assets/iruurpg/font/hud.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        boolean found = false;
        for (var element : font.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (!provider.has("chars") || !provider.getAsJsonArray("chars").get(0).getAsString().equals(String.valueOf(HudGlyphs.veil()))) continue;
            found = true;
            assertEquals(HudGlyphs.VEIL, provider.get("height").getAsInt());
            // (a title is 4 times as big and its line 10 font pixels over the middle: the veil's top 7 - ascent over the line, ±512 of the screen round the middle)
            int top = -10 + 7 - provider.get("ascent").getAsInt();
            assertEquals(-512, top * 4, 16);
            assertEquals(512, (top + HudGlyphs.VEIL) * 4, 16);
            assertTrue(provider.get("ascent").getAsInt() <= HudGlyphs.VEIL);
        }
        assertTrue(found);
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
