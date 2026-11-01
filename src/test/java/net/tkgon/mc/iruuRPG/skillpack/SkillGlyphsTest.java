package net.tkgon.mc.iruuRPG.skillpack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.tkgon.mc.iruuRPG.markpack.MarkGlyphs;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The icons of the strong attacks: the table of characters and names (agreed with whoever draws them in the game) and the pack. */
class SkillGlyphsTest {

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) files.put(entry.getName(), in.readAllBytes());
        }
        return files;
    }

    private static byte[] png(int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void theTableIsFixed() {
        assertEquals(25, SkillGlyphs.all().size());
        assertEquals('', SkillGlyphs.of("warrior", 1));
        assertEquals('', SkillGlyphs.of("warrior", 5));
        assertEquals('', SkillGlyphs.of("mage", 1));
        assertEquals('', SkillGlyphs.of("healer", 1));
        assertEquals('', SkillGlyphs.of("gifted", 1));
        assertEquals('', SkillGlyphs.of("assassin", 5));
        assertEquals(0, SkillGlyphs.of("nobody", 1));
        assertEquals(0, SkillGlyphs.of("warrior", 6));
        assertEquals("mage_3", SkillGlyphs.name("mage", 3));
    }

    @Test
    void everyCharacterIsOnlyOnceAndNotInTheMarksFont() {
        Set<Character> seen = new HashSet<>();
        for (SkillGlyphs.Glyph glyph : SkillGlyphs.all()) {
            assertTrue(seen.add(glyph.ch()), glyph.name());
            assertTrue(glyph.ch() >= 0xF000 && glyph.ch() <= 0xF8FF, glyph.name());
        }
        for (MarkGlyphs.Glyph mark : MarkGlyphs.all()) assertFalse(seen.contains(mark.ch()), "the marks' character " + (int) mark.ch());
    }

    @Test
    void thePackHasTheFontAndAPictureForEveryIcon() throws IOException {
        Map<String, byte[]> files = unzip(SkillGlyphs.pack(name -> null));
        assertNotNull(files.get("pack.mcmeta"));
        JsonObject font = JsonParser.parseString(new String(files.get("assets/iruurpg/font/skillicons.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(25, font.getAsJsonArray("providers").size());
        for (SkillGlyphs.Glyph glyph : SkillGlyphs.all()) {
            byte[] picture = files.get("assets/iruurpg/textures/font/skills/" + glyph.name() + ".png");
            assertNotNull(picture, glyph.name());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(picture));
            assertEquals(SkillGlyphs.SIZE, image.getWidth());
            assertEquals(SkillGlyphs.SIZE, image.getHeight());
        }
        assertArrayEquals(SkillGlyphs.pack(name -> null), SkillGlyphs.pack(name -> null));
    }

    @Test
    void aPictureOfItsOwnTakesTheStandInsPlace() throws IOException {
        byte[] own = png(32);
        byte[] notSquare;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB), "png", out);
        notSquare = out.toByteArray();
        Map<String, byte[]> files = unzip(SkillGlyphs.pack(name -> name.equals("mage_2") ? own : name.equals("mage_3") ? notSquare : null));
        assertArrayEquals(own, files.get("assets/iruurpg/textures/font/skills/mage_2.png"));
        BufferedImage stand = ImageIO.read(new ByteArrayInputStream(files.get("assets/iruurpg/textures/font/skills/mage_3.png")));
        assertEquals(SkillGlyphs.SIZE, stand.getWidth());
    }
}
