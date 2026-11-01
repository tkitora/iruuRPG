package net.tkgon.mc.iruuRPG.markpack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The shapes of the ground marks, as the glyphs of a font of our own ({@code iruurpg:marks}, private-use characters from U+E000; the game's own
 * font is not touched, so no other text changes). A text display of one glyph, laid flat and stretched, draws one piece of a mark; the pieces of a
 * mark never overlap, so a see-through mark has the same strength everywhere.
 * <ul>
 *   <li>a square (a box and its edges);</li>
 *   <li>a disc: whole, or its inside and its rim as a pair that fit together exactly, for each rim width in {@link #RIMS} (the rim's width over the
 *   radius);</li>
 *   <li>a wedge (a slice of a disc, its tip at the centre) of each angle in {@link #ANGLES}: whole, or its inside and its rim for each rim width,
 *   the rim along the arc and along none / the first / the second / both straight sides ({@link Sides}). A fan of any whole number of degrees is
 *   made of a few wedges side by side ({@link #split}).</li>
 * </ul>
 * Every glyph is a white square picture ({@link #SIZE} px) whose alpha is the shape (smoothed at the edges); the text color and opacity of the
 * display color it. The glyph is drawn 8 font pixels square (0.2 blocks at scale 1), its centre ({@link #CENTER_X}, 0) from the display's place:
 * height 8 with ascent 1 puts it from 0.1 below to 0.1 above the place, and a pixel of alpha 1 in the top-right corner makes the glyph's
 * advance its full width, which centres it half a font pixel right of the place.
 */
public final class MarkGlyphs {

    public static final String FONT = "iruurpg:marks";
    /** The side of a glyph's picture (pixels). */
    public static final int SIZE = 256;
    /** The drawn size of a glyph at scale 1 (blocks: 8 font pixels of 1/40). */
    public static final double GLYPH = 0.2;
    /** Where the glyph's centre is, from the display's place, at scale 1 (blocks). */
    public static final double CENTER_X = 0.0125;
    /** The rim widths, as a share of the radius (a disc or a wedge of radius 1 has a rim this wide). */
    public static final double[] RIMS = {0.03, 0.05, 0.08, 0.12, 0.18, 0.25};
    /** The wedge angles (degrees), large first: a fan is cut into these greedily. */
    public static final int[] ANGLES = {180, 90, 60, 45, 30, 15, 10, 5, 2, 1};

    public enum Kind { SQUARE, DISC, WEDGE }

    /** WHOLE = the shape without a rim; FILL = the inside of a shape with a rim; RIM = its rim. */
    public enum Part { WHOLE, FILL, RIM }

    /** Which straight sides of a wedge have a rim: FIRST = the side it starts from, SECOND = the side it turns to. */
    public enum Sides {
        NONE, FIRST, SECOND, BOTH;

        public static Sides of(boolean first, boolean second) {
            return first ? (second ? BOTH : FIRST) : (second ? SECOND : NONE);
        }

        boolean first() {
            return this == FIRST || this == BOTH;
        }

        boolean second() {
            return this == SECOND || this == BOTH;
        }
    }

    /** One glyph: its character and how its picture is made. {@code rim} is an index of {@link #RIMS} (-1 for WHOLE and the square). */
    public record Glyph(char ch, Kind kind, int angle, Part part, int rim, Sides sides) {
        String name() {
            return switch (kind) {
                case SQUARE -> "square";
                case DISC -> "disc_" + part.name().toLowerCase(Locale.ROOT) + (rim >= 0 ? "_" + rim : "");
                case WEDGE -> String.format(Locale.ROOT, "wedge_%03d_%s%s", angle, part.name().toLowerCase(Locale.ROOT),
                        rim >= 0 ? "_" + rim + "_" + sides.name().toLowerCase(Locale.ROOT) : "");
            };
        }
    }

    private static final List<Glyph> ALL;
    private static final Map<String, Glyph> BY_KEY = new LinkedHashMap<>();

    static {
        List<Glyph> all = new ArrayList<>();
        add(all, Kind.SQUARE, 0, Part.WHOLE, -1, Sides.NONE);
        add(all, Kind.DISC, 360, Part.WHOLE, -1, Sides.NONE);
        for (int rim = 0; rim < RIMS.length; rim++) {
            add(all, Kind.DISC, 360, Part.FILL, rim, Sides.NONE);
            add(all, Kind.DISC, 360, Part.RIM, rim, Sides.NONE);
        }
        for (int angle : ANGLES) {
            add(all, Kind.WEDGE, angle, Part.WHOLE, -1, Sides.NONE);
            for (int rim = 0; rim < RIMS.length; rim++) {
                for (Sides sides : Sides.values()) {
                    add(all, Kind.WEDGE, angle, Part.FILL, rim, sides);
                    add(all, Kind.WEDGE, angle, Part.RIM, rim, sides);
                }
            }
        }
        ALL = Collections.unmodifiableList(all);
    }

    private static void add(List<Glyph> all, Kind kind, int angle, Part part, int rim, Sides sides) {
        Glyph glyph = new Glyph((char) (0xE000 + all.size()), kind, angle, part, rim, sides);
        all.add(glyph);
        BY_KEY.put(key(kind, angle, part, rim, sides), glyph);
    }

    private static String key(Kind kind, int angle, Part part, int rim, Sides sides) {
        return kind + "/" + angle + "/" + part + "/" + rim + "/" + sides;
    }

    private MarkGlyphs() {
    }

    public static List<Glyph> all() {
        return ALL;
    }

    public static char square() {
        return BY_KEY.get(key(Kind.SQUARE, 0, Part.WHOLE, -1, Sides.NONE)).ch();
    }

    /** A disc: WHOLE (rim ignored), or the FILL / RIM of rim width {@code rim} (an index of {@link #RIMS}). */
    public static char disc(Part part, int rim) {
        return part == Part.WHOLE ? BY_KEY.get(key(Kind.DISC, 360, Part.WHOLE, -1, Sides.NONE)).ch()
                : BY_KEY.get(key(Kind.DISC, 360, part, rim, Sides.NONE)).ch();
    }

    /** A wedge of {@code angle} (one of {@link #ANGLES}). */
    public static char wedge(int angle, Part part, int rim, Sides sides) {
        Glyph glyph = part == Part.WHOLE ? BY_KEY.get(key(Kind.WEDGE, angle, Part.WHOLE, -1, Sides.NONE)) : BY_KEY.get(key(Kind.WEDGE, angle, part, rim, sides));
        if (glyph == null) throw new IllegalArgumentException("no wedge of " + angle + " degrees");
        return glyph.ch();
    }

    /** The index of the rim width nearest to {@code share} (rim width over radius). */
    public static int nearestRim(double share) {
        int best = 0;
        for (int index = 1; index < RIMS.length; index++) {
            if (Math.abs(RIMS[index] - share) < Math.abs(RIMS[best] - share)) best = index;
        }
        return best;
    }

    /** {@code degrees} (1..359) as wedge angles, large first (their sum is {@code degrees}). */
    public static List<Integer> split(int degrees) {
        List<Integer> parts = new ArrayList<>();
        int left = Math.max(0, degrees);
        for (int angle : ANGLES) {
            while (left >= angle) {
                parts.add(angle);
                left -= angle;
            }
        }
        return parts;
    }

    // ---- pictures -----------------------------------------------------------------------------------------------------

    /**
     * How far a point is outside the glyph's shape (negative inside), in the picture's units: the picture is the square from -1 to 1, {@code u}
     * right and {@code v} up; a disc / wedge has radius 1, a wedge starts from straight up and turns clockwise (towards +u).
     */
    public static double outside(Glyph glyph, double u, double v) {
        double r = Math.hypot(u, v);
        return switch (glyph.kind()) {
            case SQUARE -> Math.max(Math.abs(u), Math.abs(v)) - 1.0;
            case DISC -> {
                if (glyph.part() == Part.WHOLE) yield r - 1.0;
                double inner = 1.0 - RIMS[glyph.rim()];
                yield glyph.part() == Part.FILL ? r - inner : Math.max(r - 1.0, inner - r);
            }
            case WEDGE -> {
                double a = Math.toRadians(glyph.angle());
                double first = u;                                       // distance inside the starting side (straight up)
                double second = v * Math.sin(a) - u * Math.cos(a);       // distance inside the other side
                double whole = Math.max(r - 1.0, Math.max(-first, -second));
                if (glyph.part() == Part.WHOLE) yield whole;
                double k = RIMS[glyph.rim()];
                double fill = Math.max(r - (1.0 - k), Math.max(-(first - (glyph.sides().first() ? k : 0.0)), -(second - (glyph.sides().second() ? k : 0.0))));
                yield glyph.part() == Part.FILL ? fill : Math.max(whole, -fill);
            }
        };
    }

    /** The picture: white, the alpha the share of each pixel inside the shape. */
    public static BufferedImage image(Glyph glyph) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        double pixel = 2.0 / SIZE;
        for (int y = 0; y < SIZE; y++) {
            double v = 1.0 - (y + 0.5) * pixel;
            for (int x = 0; x < SIZE; x++) {
                double u = (x + 0.5) * pixel - 1.0;
                double cover = Math.max(0.0, Math.min(1.0, 0.5 - outside(glyph, u, v) / pixel));
                int alpha = (int) Math.round(cover * 255.0);
                if (alpha > 0) image.setRGB(x, y, alpha << 24 | 0xFFFFFF);
            }
        }
        // the game sizes a glyph by its last column with any alpha: this keeps every glyph's advance its full width (see the class note)
        if ((image.getRGB(SIZE - 1, 0) >>> 24) == 0) image.setRGB(SIZE - 1, 0, 1 << 24 | 0xFFFFFF);
        return image;
    }

    /**
     * The resource pack: pack.mcmeta, the font and the pictures (and the font of the action bar's icons, {@link HudGlyphs}). The bytes are the
     * same every time for the same glyphs. (The shaders of the screen effects are a pack of their own, {@link ScreenShaders#pack}: a client
     * whose GPU cannot take them must still have the fonts.)
     */
    public static byte[] pack() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", meta("iruuRPG: ground marks (font iruurpg:marks), heavy attack icons (font iruurpg:hud)"));
        JsonArray providers = new JsonArray();
        for (Glyph glyph : ALL) {
            JsonObject provider = new JsonObject();
            provider.addProperty("type", "bitmap");
            provider.addProperty("file", "iruurpg:font/marks/" + glyph.name() + ".png");
            provider.addProperty("ascent", 1);
            provider.addProperty("height", 8);
            JsonArray chars = new JsonArray();
            chars.add(String.valueOf(glyph.ch()));
            provider.add("chars", chars);
            providers.add(provider);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image(glyph), "png", png);
            files.put("assets/iruurpg/textures/font/marks/" + glyph.name() + ".png", png.toByteArray());
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        files.put("assets/iruurpg/font/marks.json", new GsonBuilder().disableHtmlEscaping().create().toJson(font).getBytes(StandardCharsets.UTF_8));
        HudGlyphs.addTo(files);
        return zip(files);
    }

    /** pack.mcmeta of a pack of this server (1.20.4). */
    static byte[] meta(String description) {
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 22);
        pack.addProperty("description", description);
        meta.add("pack", pack);
        return new GsonBuilder().setPrettyPrinting().create().toJson(meta).getBytes(StandardCharsets.UTF_8);
    }

    /** The files as a zip (the same bytes every time for the same files: the times are 0). */
    static byte[] zip(Map<String, byte[]> files) throws IOException {
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
