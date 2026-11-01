package net.tkgon.mc.iruuRPG.skillpack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The icons of the strong attacks (強1〜強5 of each class) as the glyphs of a font of our own ({@code iruurpg:skillicons}, private-use characters
 * from U+F000; the ground marks' font {@code iruurpg:marks} is U+E000..U+E1F7, so they never meet). The pictures are colored: show them in white text.
 * <ul>
 *   <li>name: {@code <classId>_<n>} ({@code warrior_1}); character: {@code 0xF000 + class number * 0x10 + n} for the classes of {@link #CLASSES}
 *   and n = 1..{@link #STRONG} (n = 0 and 6..F of each class are kept for later icons);</li>
 *   <li>the picture is a stand-in (the class's color, a rounded square, the number of the attack); a real picture takes its place by its name:
 *   {@code skill-icons/<name>.png} in the plugin's folder (the yml repository's {@code skill-icons/}), any square PNG.</li>
 * </ul>
 * The glyph is {@link #HEIGHT} font pixels high and sits on the text's baseline up to {@link #ASCENT} (the action bar's own letters are 8 high).
 */
public final class SkillGlyphs {

    public static final String FONT = "iruurpg:skillicons";
    public static final int FIRST = 0xF000;
    public static final int STRONG = 5;
    /** The side of a stand-in picture (pixels). */
    public static final int SIZE = 64;
    public static final int HEIGHT = 16;
    public static final int ASCENT = 15;

    /** The classes in the order of their numbers, with the color of their stand-in icons. */
    public static final Map<String, Integer> CLASSES = new LinkedHashMap<>();

    static {
        CLASSES.put("warrior", 0xC84038);
        CLASSES.put("mage", 0x4870E8);
        CLASSES.put("healer", 0x50B86E);
        CLASSES.put("gifted", 0xE89634);
        CLASSES.put("assassin", 0x8050B0);
    }

    /** One icon: its character, its name and the class and number it is for. */
    public record Glyph(char ch, String name, String classId, int number) {
    }

    private static final List<Glyph> ALL;
    private static final Map<String, Glyph> BY_NAME = new LinkedHashMap<>();

    static {
        List<Glyph> all = new ArrayList<>();
        int index = 0;
        for (String classId : CLASSES.keySet()) {
            for (int n = 1; n <= STRONG; n++) {
                Glyph glyph = new Glyph((char) (FIRST + index * 0x10 + n), name(classId, n), classId, n);
                all.add(glyph);
                BY_NAME.put(glyph.name(), glyph);
            }
            index++;
        }
        ALL = Collections.unmodifiableList(all);
    }

    private SkillGlyphs() {
    }

    public static String name(String classId, int n) {
        return classId + "_" + n;
    }

    public static List<Glyph> all() {
        return ALL;
    }

    /** The character of the icon of strong attack {@code n} (1..5) of a class, or 0 when there is none (an unknown class). */
    public static char of(String classId, int n) {
        Glyph glyph = BY_NAME.get(name(classId == null ? "" : classId, n));
        return glyph == null ? 0 : glyph.ch();
    }

    // ---- pictures -----------------------------------------------------------------------------------------------------

    /** The digits 1..5 as 5 x 7 pixels. */
    private static final String[][] DIGITS = {
            {"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."},
            {".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"},
            {".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."},
            {"...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."},
            {"#####", "#....", "####.", "....#", "....#", "#...#", ".###."},
    };

    /** The stand-in picture: a rounded square in the class's color with the number of the attack. */
    public static BufferedImage image(Glyph glyph) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color base = new Color(CLASSES.get(glyph.classId()));
            Color light = mix(base, Color.WHITE, 0.28);
            Color dark = mix(base, Color.BLACK, 0.45);
            RoundRectangle2D shape = new RoundRectangle2D.Double(3, 3, SIZE - 6, SIZE - 6, 22, 22);
            g.setPaint(new GradientPaint(0, 3, light, 0, SIZE - 3, base));
            g.fill(shape);
            g.setPaint(dark);
            g.setStroke(new BasicStroke(4.0f));
            g.draw(shape);
            // the number: 5 x 7 pixels of 5 picture pixels, with a shadow
            String[] rows = DIGITS[glyph.number() - 1];
            int cell = 5;
            int left = (SIZE - 5 * cell) / 2;
            int top = (SIZE - 7 * cell) / 2;
            g.setPaint(new Color(0, 0, 0, 110));
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 5; x++) {
                    if (rows[y].charAt(x) == '#') g.fillRect(left + x * cell + 2, top + y * cell + 2, cell, cell);
                }
            }
            g.setPaint(Color.WHITE);
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 5; x++) {
                    if (rows[y].charAt(x) == '#') g.fillRect(left + x * cell, top + y * cell, cell, cell);
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static Color mix(Color a, Color b, double share) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * share),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * share),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * share));
    }

    /** A picture a person drew is a square PNG; anything else is not used. */
    static boolean usable(byte[] png) {
        if (png == null) return false;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            return image != null && image.getWidth() == image.getHeight() && image.getWidth() >= 8;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    /**
     * The resource pack: pack.mcmeta, the font and the pictures. {@code own} gives the picture of an icon by its name (null for the stand-in).
     * The bytes are the same every time for the same input.
     */
    public static byte[] pack(Function<String, byte[]> own) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 22);
        pack.addProperty("description", "iruuRPG: skill icons (font iruurpg:skillicons)");
        meta.add("pack", pack);
        files.put("pack.mcmeta", new GsonBuilder().setPrettyPrinting().create().toJson(meta).getBytes(StandardCharsets.UTF_8));
        JsonArray providers = new JsonArray();
        for (Glyph glyph : ALL) {
            JsonObject provider = new JsonObject();
            provider.addProperty("type", "bitmap");
            provider.addProperty("file", "iruurpg:font/skills/" + glyph.name() + ".png");
            provider.addProperty("ascent", ASCENT);
            provider.addProperty("height", HEIGHT);
            JsonArray chars = new JsonArray();
            chars.add(String.valueOf(glyph.ch()));
            provider.add("chars", chars);
            providers.add(provider);
            byte[] picture = own == null ? null : own.apply(glyph.name());
            if (!usable(picture)) {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(image(glyph), "png", png);
                picture = png.toByteArray();
            }
            files.put("assets/iruurpg/textures/font/skills/" + glyph.name() + ".png", picture);
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        files.put("assets/iruurpg/font/skillicons.json", new GsonBuilder().disableHtmlEscaping().create().toJson(font).getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0L);
                out.putNextEntry(entry);
                out.write(file.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
