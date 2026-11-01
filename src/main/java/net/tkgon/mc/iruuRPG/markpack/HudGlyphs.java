package net.tkgon.mc.iruuRPG.markpack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The icons of the skills on the action bar, as the glyphs of a font of our own ({@code iruurpg:hud}, in the pack of the ground marks): a rounded
 * square for each skill of each class (小・中・大・util・ULT, the redesign of 2026-11-02) and the weapon's skill, the shade that covers an icon while
 * its skill cools down, small digits for the seconds left, the labels of the keys, and spaces (negative ones too) to put them in place. The glyphs
 * stand above the line of text (their pictures are taller than the icon, which is at their top), so the row of icons is drawn over the status line
 * without moving it.
 *
 * <p>Code points: 小・中・大・util of class c ({@link #CLASSES}) are U+E101 - U+E104 + 16 c, its ULT (the special of t140) U+E107 + 16 c; the
 * weapon's skill U+E180 and its locked icon U+E181, the lock laid over a skill there is not yet U+E230; shade k (1 - 16 rows of 16 covered)
 * U+E200 + k; digit d U+E220 + d; the key labels ({@link #KEY_LABELS}) U+E240 on; the spaces U+E2F0 on. The pictures are the texture tree's
 * ({@code hud/small_<class>.png}, {@code medium_}, {@code large_}, {@code util_}, {@code ultimate_<class>.png}, {@code itemskill.png},
 * {@code itemskill_locked.png}, {@code lock.png}; 16 x 16) when there are some; 小・中・大 else take the pictures of the skills 1 - 3 of t138
 * ({@code hud/heavy_<class>_<n>.png}), which they throw until the class's own moves are made, and the rest placeholders (the class's color with
 * the number or a mark in white; the locked weapon skill's: its icon greyed under the lock). A key label is a picture of its own (7 of them, not
 * one per icon), drawn over the icon's place: the keys stand just above the icon, so they hide neither the icon nor its cooldown.
 */
public final class HudGlyphs {

    public static final Key FONT = Key.key("iruurpg", "hud");
    /** The classes with icons of their own, in the order of their code points (generic: a weapon outside the class's combos). */
    public static final List<String> CLASSES = List.of("warrior", "assassin", "mage", "gifted", "healer", "generic");
    /** The placeholder colors of the classes (the same order). */
    private static final int[] COLORS = {0xD9483B, 0x8E4FD1, 0x3F7BE0, 0x4CAF50, 0xE0B43F, 0x8A8A8A};
    /** How many icons of their own a class's skills have besides the ULT's: 小・中・大・util. */
    public static final int ICONS = 4;
    /** The names of the skills' pictures, 小・中・大・util and the ULT ({@code hud/<name>_<class>.png}). */
    private static final String[] SKILL_NAMES = {"small", "medium", "large", "util", "ultimate"};
    /**
     * The labels of the keys (U+E240 on): Q → left, Q → right, Q → Q, F, shift + F, the left button, the right button. The letters: f, q, l (left),
     * r (right), s (shift).
     */
    public static final List<String> KEY_LABELS = List.of("ql", "qr", "qq", "f", "sf", "l", "r");
    /** The room between the skills' icons and the weapon skill's (t140: half an icon). */
    public static final int HALF = 8;
    /** The side of an icon (pixels = font pixels). */
    public static final int ICON = 16;
    /** The height of every picture of the font: the icon is its top 16 rows, so it stands 2 font pixels above the top of the text. */
    private static final int PICTURE = 25;
    /** The space between two icons. */
    public static final int GAP = 2;
    /** One icon and the gap after it (an icon's advance is its width + 1, and a space of 1 follows it). */
    public static final int SLOT = ICON + GAP;
    private static final int DIGIT_WIDTH = 5;
    /** How much taller a key label's picture is than an icon's: its keys are in these rows, just above the icon. */
    private static final int KEY_RAISE = 8;
    /** The keys of a label in a 3 x 5 pixel font: F, Q, the left button (L), the right button (R), shift (an arrow up), and the arrow between them. */
    private static final String KEY_KEYS = "fqlrs";
    private static final String[] KEY_LETTERS = {"111100110100100", "111101101110011", "100100100100111", "110101110101101", "010111010010010"};
    private static final String KEY_ARROW = "100010001010100";
    private static final int[] SPACES = {1, 2, 4, 8, 16, 32, 64, 128};
    private static final String[] DIGITS = {
            "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111"};

    private HudGlyphs() {
    }

    public static int classIndex(String classId) {
        int index = CLASSES.indexOf(classId);
        return index < 0 ? CLASSES.size() - 1 : index;
    }

    /** Icon {@code number} (1 - 4: 小・中・大・util) of a class. */
    public static char icon(String classId, int number) {
        return (char) (0xE100 + 16 * classIndex(classId) + Math.max(1, Math.min(ICONS, number)));
    }

    /** The icon of the ULT of a class (the special of t140). */
    public static char special(String classId) {
        return (char) (0xE107 + 16 * classIndex(classId));
    }

    /** The icon of skill {@code index} of a class: 0 - 3 小・中・大・util, 4 the ULT. */
    public static char skill(String classId, int index) {
        return index >= ICONS ? special(classId) : icon(classId, index + 1);
    }

    /** The icon of the weapon's skill (t140). */
    public static char itemSkill() {
        return '\uE180';
    }

    /** The icon of the weapon's skill when the weapon has none (t140): its own picture, drawn as it is. */
    public static char itemSkillLocked() {
        return '\uE181';
    }

    /** The lock laid over the greyed icon of a special there is not yet (t140). */
    public static char lock() {
        return '\uE230';
    }

    /** The shade covering {@code rows} (1 - 16) rows of an icon from its top. */
    public static char shade(int rows) {
        return (char) (0xE200 + Math.max(1, Math.min(ICON, rows)));
    }

    public static char digit(int digit) {
        return (char) (0xE220 + Math.max(0, Math.min(9, digit)));
    }

    /** The label of keys ({@link #KEY_LABELS}: "ql", "f" ...); its color is the text's. */
    public static char keyLabel(String keys) {
        int index = KEY_LABELS.indexOf(keys);
        if (index < 0) throw new IllegalArgumentException("no key label " + keys);
        return (char) (0xE240 + index);
    }

    /** A space of +-2^i pixels ({@code negative}: to the left). */
    private static char space(int i, boolean negative) {
        return (char) (0xE2F0 + (negative ? SPACES.length : 0) + i);
    }

    /** Spaces that move the next glyph {@code pixels} to the right (to the left when negative). */
    public static String move(int pixels) {
        StringBuilder text = new StringBuilder();
        int left = Math.abs(pixels);
        for (int i = SPACES.length - 1; i >= 0; i--) {
            while (left >= SPACES[i]) {
                text.append(space(i, pixels < 0));
                left -= SPACES[i];
            }
        }
        return text.toString();
    }

    /**
     * What one icon's state draws: its {@code glyph}, {@code coolingShare} 0 (ready) - 1 (just used), {@code seconds} left (shown when above 0), the label of the keys that
     * do it ({@link #keyLabel}, 0 for none) in {@code keyColor}, and {@code locked}: greyed under a lock (a skill there is not, t140).
     */
    public record Icon(char glyph, double coolingShare, int seconds, char key, int keyColor, boolean locked) {

        public Icon(String classId, int number, double coolingShare, int seconds, char key, int keyColor) {
            this(icon(classId, number), coolingShare, seconds, key, keyColor, false);
        }

        public Icon(String classId, int number, double coolingShare, int seconds) {
            this(classId, number, coolingShare, seconds, (char) 0, 0xFFFFFF);
        }
    }

    /**
     * The row of icons, centred where it is put in the text and taking no room in it (the text after it starts where it would without it). The
     * width of the row is always the same, so the bar never shifts as the cooldowns run.
     */
    public static Component row(List<Icon> icons) {
        return row(icons, List.of());
    }

    /**
     * The row of the skills' icons and, half an icon after them, {@code extras} (the special and the weapon's skill, t140); centred and taking no room in the line as
     * {@link #row(List)}.
     */
    public static Component row(List<Icon> icons, List<Icon> extras) {
        int count = icons.size() + extras.size();
        int between = icons.isEmpty() || extras.isEmpty() ? 0 : HALF - GAP;
        int width = count * SLOT - GAP + between;
        Component row = Component.text(move(-(width + 1) / 2));
        List<Icon> all = new java.util.ArrayList<>(icons);
        all.addAll(extras);
        for (int index = 0; index < all.size(); index++) {
            Icon icon = all.get(index);
            if (index == icons.size() && between > 0) row = row.append(Component.text(move(between)));
            boolean cooling = icon.coolingShare() > 0.0 && !icon.locked();
            // the advance of an icon is its width + 1
            row = row.append(Component.text(String.valueOf(icon.glyph()), TextColor.color(cooling || icon.locked() ? 0x8C8C8C : 0xFFFFFF)));
            if (icon.locked()) row = row.append(Component.text(move(-(ICON + 1)) + lock(), TextColor.color(0xFFFFFF)));
            if (cooling) {
                int rows = (int) Math.ceil(icon.coolingShare() * ICON);
                row = row.append(Component.text(move(-(ICON + 1)) + shade(rows), TextColor.color(0xFFFFFF)));
                String digits = String.valueOf(Math.min(99, Math.max(0, icon.seconds())));
                if (icon.seconds() > 0) {
                    int digitsWidth = digits.length() * (DIGIT_WIDTH + 1);
                    StringBuilder text = new StringBuilder(move(-(ICON + 1) + (ICON + 1 - digitsWidth) / 2));
                    for (char ch : digits.toCharArray()) text.append(digit(ch - '0'));
                    text.append(move(ICON + 1 - (ICON + 1 - digitsWidth) / 2 - digitsWidth));
                    row = row.append(Component.text(text.toString(), TextColor.color(0xFFFFFF)));
                }
            }
            if (icon.key() != 0) {
                // (the label's picture is as wide as the icon: back to the icon's place, and the label ends where the icon did)
                row = row.append(Component.text(move(-(ICON + 1)) + icon.key(), TextColor.color(icon.keyColor())));
            }
            row = row.append(Component.text(move(SLOT - ICON - 1)));
        }
        row = row.append(Component.text(move(-(count * SLOT + between) + (width + 1) / 2)));
        return row.font(FONT);
    }

    // ---- pictures -----------------------------------------------------------------------------------------------------

    /** A picture of the font's height with {@code top} (or nothing) at its top; its top-right pixel keeps the advance the full width. */
    private static BufferedImage picture(int width, BufferedImage top, int topY) {
        return picture(width, PICTURE, top, topY);
    }

    private static BufferedImage picture(int width, int height, BufferedImage top, int topY) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (top != null) {
            for (int y = 0; y < top.getHeight() && topY + y < height; y++) {
                for (int x = 0; x < Math.min(width, top.getWidth()); x++) image.setRGB(x, topY + y, top.getRGB(x, y));
            }
        }
        if ((image.getRGB(width - 1, 0) >>> 24) == 0) image.setRGB(width - 1, 0, 1 << 24 | 0xFFFFFF);
        return image;
    }

    /** Whether ({@code x}, {@code y}) is inside the rounded square of an icon. */
    private static boolean inside(int x, int y) {
        int r = 3;
        int cx = x < r ? r : x > ICON - 1 - r ? ICON - 1 - r : x;
        int cy = y < r ? r : y > ICON - 1 - r ? ICON - 1 - r : y;
        double dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= (r + 0.3) * (r + 0.3);
    }

    /** The placeholder icon: the rounded square in the class's color, a darker rim, the number in white (its 3 x 5 digit, twice the size). */
    static BufferedImage placeholder(int classIndex, int number) {
        int color = COLORS[classIndex];
        int rim = darker(color);
        BufferedImage image = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ICON; y++) {
            for (int x = 0; x < ICON; x++) {
                if (!inside(x, y)) continue;
                boolean edge = !inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1);
                image.setRGB(x, y, 0xFF000000 | (edge ? rim : color));
            }
        }
        String bits = DIGITS[number];
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                if (bits.charAt(row * 3 + col) != '1') continue;
                for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) image.setRGB(5 + col * 2 + dx, 3 + row * 2 + dy, 0xFFFFFFFF);
            }
        }
        return image;
    }

    private static int darker(int color) {
        int r = (color >> 16 & 0xFF) * 3 / 5, g = (color >> 8 & 0xFF) * 3 / 5, b = (color & 0xFF) * 3 / 5;
        return r << 16 | g << 8 | b;
    }

    /**
     * The icon of skill {@code number} (1 - 4: 小・中・大・util) of a class: the texture tree's picture ({@code hud/small_<class>.png} ...) when there
     * is a 16 x 16 one; 小・中・大 else the picture of skill 1 - 3 of t138 ({@code hud/heavy_<class>_<n>.png}); else the placeholder.
     */
    static BufferedImage iconImage(int classIndex, int number) {
        String classId = CLASSES.get(classIndex);
        BufferedImage fallback = number == 4 ? utilPlaceholder(classIndex) : resourceImage("heavy_" + classId + "_" + number, placeholder(classIndex, number));
        return resourceImage(SKILL_NAMES[number - 1] + "_" + classId, fallback);
    }

    /** A placeholder of util: the rounded square in the class's color with a white double arrow (a dash). */
    static BufferedImage utilPlaceholder(int classIndex) {
        BufferedImage image = placeholderSquare(COLORS[classIndex]);
        String[] arrows = {"1001000", "0100100", "0010010", "0001001", "0010010", "0100100", "1001000"};
        for (int row = 0; row < arrows.length; row++) {
            for (int col = 0; col < 7; col++) if (arrows[row].charAt(col) == '1') image.setRGB(4 + col, 4 + row, 0xFFFFFFFF);
        }
        return image;
    }

    /** A placeholder of the ULT (the special): the rounded square in the class's color with a white star. */
    static BufferedImage specialPlaceholder(int classIndex) {
        BufferedImage image = placeholderSquare(COLORS[classIndex]);
        String[] star = {"0001000", "0001000", "0011100", "1111111", "0111110", "0011100", "0110110", "1100011"};
        for (int row = 0; row < star.length; row++) {
            for (int col = 0; col < 7; col++) if (star[row].charAt(col) == '1') image.setRGB(4 + col, 4 + row, 0xFFFFFFFF);
        }
        return image;
    }

    /** A placeholder of the weapon's skill: a grey-blue rounded square with a white diamond. */
    static BufferedImage itemSkillPlaceholder() {
        BufferedImage image = placeholderSquare(0x5A6B7D);
        for (int row = 0; row < 9; row++) {
            int half = 4 - Math.abs(row - 4);
            for (int col = -half; col <= half; col++) image.setRGB(8 + col, 4 + row, 0xFFFFFFFF);
        }
        return image;
    }

    /** A placeholder of the lock: the icon darkened, and a padlock in its middle. */
    static BufferedImage lockPlaceholder() {
        BufferedImage image = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ICON; y++) for (int x = 0; x < ICON; x++) if (inside(x, y)) image.setRGB(x, y, 0x90000000);
        String[] padlock = {"0011100", "0100010", "0100010", "1111111", "1111111", "1110111", "1110111", "1111111"};
        for (int row = 0; row < padlock.length; row++) {
            for (int col = 0; col < 7; col++) if (padlock[row].charAt(col) == '1') image.setRGB(4 + col, 4 + row, 0xFFDADADA);
        }
        return image;
    }

    /** A locked icon made of {@code icon}: greyed as a cooling icon is, under the lock. */
    static BufferedImage lockedPlaceholder(BufferedImage icon) {
        BufferedImage lock = lockPlaceholder();
        BufferedImage image = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ICON; y++) {
            for (int x = 0; x < ICON; x++) {
                int argb = icon.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                int r = (argb >> 16 & 0xFF) * 0x8C / 255, g = (argb >> 8 & 0xFF) * 0x8C / 255, b = (argb & 0xFF) * 0x8C / 255;
                int over = lock.getRGB(x, y), alpha = over >>> 24;
                r = (r * (255 - alpha) + (over >> 16 & 0xFF) * alpha) / 255;
                g = (g * (255 - alpha) + (over >> 8 & 0xFF) * alpha) / 255;
                b = (b * (255 - alpha) + (over & 0xFF) * alpha) / 255;
                image.setRGB(x, y, argb & 0xFF000000 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    private static BufferedImage placeholderSquare(int color) {
        int rim = darker(color);
        BufferedImage image = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ICON; y++) {
            for (int x = 0; x < ICON; x++) {
                if (!inside(x, y)) continue;
                boolean edge = !inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1);
                image.setRGB(x, y, 0xFF000000 | (edge ? rim : color));
            }
        }
        return image;
    }

    /** A 16 x 16 picture of the resources ({@code hud/<name>.png}), else {@code fallback}. */
    static BufferedImage resourceImage(String name, BufferedImage fallback) {
        try (InputStream in = HudGlyphs.class.getResourceAsStream("/hud/" + name + ".png")) {
            if (in != null) {
                BufferedImage image = ImageIO.read(in);
                if (image != null && image.getWidth() == ICON && image.getHeight() == ICON) return image;
            }
        } catch (IOException ignored) {
            // the placeholder
        }
        return fallback;
    }

    private static BufferedImage shadeImage(int rows) {
        BufferedImage image = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows; y++) for (int x = 0; x < ICON; x++) if (inside(x, y)) image.setRGB(x, y, 0xA0000000);
        return image;
    }

    /** A digit, white with a dark rim (5 x 7), set in the middle of the icon's height. */
    private static BufferedImage digitImage(int digit) {
        BufferedImage image = new BufferedImage(DIGIT_WIDTH, 7, BufferedImage.TYPE_INT_ARGB);
        String bits = DIGITS[digit];
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                if (bits.charAt(row * 3 + col) != '1') continue;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int x = col + 1 + dx, y = row + 1 + dy;
                        if ((image.getRGB(x, y) >>> 24) == 0) image.setRGB(x, y, 0xFF202020);
                    }
                }
            }
        }
        for (int row = 0; row < 5; row++) for (int col = 0; col < 3; col++) if (bits.charAt(row * 3 + col) == '1') image.setRGB(col + 1, row + 1, 0xFFFFFFFF);
        return image;
    }

    /**
     * The label of keys ({@link #KEY_LABELS}; white with a dark rim, its color is the text's): Q, an arrow and the second key; or shift and F side
     * by side; or one key. In the middle of the icon's width, in the rows above the icon.
     */
    static BufferedImage keyImage(String keys) {
        BufferedImage image = new BufferedImage(ICON, 7, BufferedImage.TYPE_INT_ARGB);
        java.util.List<String> glyphs = new java.util.ArrayList<>();
        for (int at = 0; at < keys.length(); at++) {
            if (at > 0 && keys.charAt(0) == 'q') glyphs.add(KEY_ARROW);
            glyphs.add(KEY_LETTERS[KEY_KEYS.indexOf(keys.charAt(at))]);
        }
        int width = glyphs.size() * 4 - 1;
        int left = (ICON - width) / 2;
        for (int pass = 0; pass < 2; pass++) {
            for (int g = 0; g < glyphs.size(); g++) {
                for (int row = 0; row < 5; row++) {
                    for (int col = 0; col < 3; col++) {
                        if (glyphs.get(g).charAt(row * 3 + col) != '1') continue;
                        int x = left + g * 4 + col, y = 1 + row;
                        if (pass == 1) {
                            image.setRGB(x, y, 0xFFFFFFFF);
                            continue;
                        }
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if ((image.getRGB(x + dx, y + dy) >>> 24) == 0) image.setRGB(x + dx, y + dy, 0xFF202020);
                            }
                        }
                    }
                }
            }
        }
        return image;
    }

    private static void bitmap(JsonArray providers, Map<String, byte[]> files, String name, char ch, BufferedImage image) throws IOException {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", "iruurpg:font/hud/" + name + ".png");
        provider.addProperty("ascent", image.getHeight());
        provider.addProperty("height", image.getHeight());
        JsonArray chars = new JsonArray();
        chars.add(String.valueOf(ch));
        provider.add("chars", chars);
        providers.add(provider);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        files.put("assets/iruurpg/textures/font/hud/" + name + ".png", png.toByteArray());
    }

    /** Adds the font {@code iruurpg:hud} and its pictures to the files of a pack. */
    public static void addTo(Map<String, byte[]> files) throws IOException {
        JsonArray providers = new JsonArray();
        // (the names are the texture tree's: small_<class> ... ultimate_<class>, itemskill, itemskill_locked)
        for (int c = 0; c < CLASSES.size(); c++) {
            for (int n = 1; n <= ICONS; n++) {
                bitmap(providers, files, SKILL_NAMES[n - 1] + "_" + CLASSES.get(c), icon(CLASSES.get(c), n), picture(ICON, iconImage(c, n), 0));
            }
            bitmap(providers, files, "ultimate_" + CLASSES.get(c), special(CLASSES.get(c)), picture(ICON, resourceImage("ultimate_" + CLASSES.get(c), specialPlaceholder(c)), 0));
        }
        BufferedImage item = resourceImage("itemskill", itemSkillPlaceholder());
        bitmap(providers, files, "item_skill", itemSkill(), picture(ICON, item, 0));
        bitmap(providers, files, "item_skill_locked", itemSkillLocked(), picture(ICON, resourceImage("itemskill_locked", lockedPlaceholder(item)), 0));
        bitmap(providers, files, "lock", lock(), picture(ICON, resourceImage("lock", lockPlaceholder()), 0));
        for (int rows = 1; rows <= ICON; rows++) bitmap(providers, files, "shade_" + rows, shade(rows), picture(ICON, shadeImage(rows), 0));
        for (int d = 0; d <= 9; d++) bitmap(providers, files, "digit_" + d, digit(d), picture(DIGIT_WIDTH, digitImage(d), (ICON - 7) / 2));
        // (a key label's picture is KEY_RAISE taller with the same bottom: its keys are just above the icon)
        for (String keys : KEY_LABELS) {
            // (a file of a resource pack is named in lower case only: a capital makes the whole font unreadable for a vanilla client)
            bitmap(providers, files, "key_" + keys, keyLabel(keys), picture(ICON, PICTURE + KEY_RAISE, keyImage(keys), 0));
        }
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        for (int i = 0; i < SPACES.length; i++) {
            advances.addProperty(String.valueOf(space(i, false)), SPACES[i]);
            advances.addProperty(String.valueOf(space(i, true)), -SPACES[i]);
        }
        space.add("advances", advances);
        providers.add(space);
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        files.put("assets/iruurpg/font/hud.json", new GsonBuilder().disableHtmlEscaping().create().toJson(font).getBytes(StandardCharsets.UTF_8));
    }
}
