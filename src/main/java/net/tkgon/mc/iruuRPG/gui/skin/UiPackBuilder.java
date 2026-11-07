package net.tkgon.mc.iruuRPG.gui.skin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import net.tkgon.mc.iruuRPG.markpack.MarkGlyphs;
import org.bukkit.Material;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * (t291, trial) Makes the pack of the menus' looks ({@code iruuui.zip}) from the skins. Pure: the same skins give the same bytes.
 * <ul>
 *     <li>A background is a glyph of the font {@code iruurpg:ui} (U+E000 on, a skin each in the order of their ids), drawn as the window's title: the title is
 *     drawn 8 pixels right of the window's left and 6 down from its top, so the glyph is moved 8 left and stands {@link #ASCENT} over the line (its top at the
 *     window's top); (t292) a picture whose origin is not its top left is moved that much more left and stands that much higher, reaching out of the window.
 *     The spaces that move it are the font's own (U+F800 on: +2^i, U+F810 on: -2^i).</li>
 *     <li>A piece of a part is the item {@link #BASE} with a custom model data of its own ({@link #FIRST} on, in the order of the ids, the parts' names and
 *     their slots): its model is one flat square from -1 to 17, so in the menu it covers its whole slot (18 pixels with the rim, where the item is 16) and
 *     meets the pieces of the slots beside it; (t292) a piece at the edge of a part that overflows reaches that much farther (up to 15 pixels). It is lit
 *     from the front (flat, no shading).</li>
 * </ul>
 */
public final class UiPackBuilder {

    public static final Key FONT = Key.key("iruurpg", "ui");
    /** The item the pieces are: one nobody has (the item pack and the models use other items: no two packs change the same item's model). */
    public static final Material BASE = Material.STRUCTURE_VOID;
    public static final int FIRST = 7_000_000;
    /** How far over its line a background stands: the title's line is 6 + 7 under the window's top. */
    public static final int ASCENT = 13;
    /** Where the title is drawn in the window. */
    public static final int TITLE_X = 8;
    private static final int[] SPACES = {1, 2, 4, 8, 16, 32, 64, 128};

    private UiPackBuilder() {
    }

    /**
     * What was made: the files of the pack, the glyph of each skin's background, its width and how far it reaches out of the window on the left, the custom model data
     * of each skin's pieces by slot.
     */
    public record Built(Map<String, byte[]> files, Map<String, Character> backgrounds, Map<String, Integer> widths, Map<String, Integer> lefts,
                        Map<String, Map<Integer, Integer>> numbers) {
    }

    public static Built build(List<MenuSkin> skins) throws IOException {
        List<MenuSkin> sorted = new ArrayList<>(skins);
        sorted.sort(Comparator.comparing(MenuSkin::id));
        Map<String, byte[]> files = new TreeMap<>();
        files.put("pack.mcmeta", MarkGlyphs.meta("iruuRPG: the menus' own looks (font iruurpg:ui, pieces on " + BASE.getKey().getKey() + ")"));
        Map<String, Character> backgrounds = new HashMap<>();
        Map<String, Integer> widths = new HashMap<>();
        Map<String, Integer> lefts = new HashMap<>();
        Map<String, Map<Integer, Integer>> numbers = new HashMap<>();
        JsonArray providers = new JsonArray();
        JsonArray overrides = new JsonArray();
        char next = '';
        int number = FIRST;
        for (MenuSkin skin : sorted) {
            if (skin.background() != null) {
                String name = "ui/" + skin.id();
                JsonObject provider = new JsonObject();
                provider.addProperty("type", "bitmap");
                provider.addProperty("file", "iruurpg:font/" + name + ".png");
                provider.addProperty("ascent", ASCENT + skin.originY());
                provider.addProperty("height", skin.background().getHeight());
                JsonArray chars = new JsonArray();
                chars.add(String.valueOf(next));
                provider.add("chars", chars);
                providers.add(provider);
                files.put("assets/iruurpg/textures/font/" + name + ".png", png(skin.background()));
                backgrounds.put(skin.id(), next);
                widths.put(skin.id(), skin.background().getWidth());
                lefts.put(skin.id(), skin.originX());
                next++;
            }
            Map<Integer, Integer> slots = new HashMap<>();
            List<MenuSkin.Part> parts = new ArrayList<>(skin.parts());
            parts.sort(Comparator.comparing(MenuSkin.Part::name));
            for (MenuSkin.Part part : parts) {
                for (int index = 0; index < part.slots().size(); index++) {
                    MenuSkin.Piece tile = part.tiles().get(index);
                    if (tile == null) continue;
                    String name = "ui/" + skin.id() + "/" + part.name() + "_" + index;
                    files.put("assets/iruurpg/textures/item/" + name + ".png", png(tile.image()));
                    files.put("assets/iruurpg/models/item/" + name + ".json", json(model("iruurpg:item/" + name, tile)));
                    JsonObject override = new JsonObject();
                    JsonObject predicate = new JsonObject();
                    predicate.addProperty("custom_model_data", number);
                    override.add("predicate", predicate);
                    override.addProperty("model", "iruurpg:item/" + name);
                    overrides.add(override);
                    slots.put(part.slots().get(index), number);
                    number++;
                }
            }
            numbers.put(skin.id(), slots);
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
        files.put("assets/iruurpg/font/ui.json", json(font));
        // the pieces' item: its own look, and a model for every piece (the game takes the last override a number reaches: they go up)
        String base = BASE.getKey().getKey();
        JsonObject item = new JsonObject();
        item.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", "minecraft:item/" + base);
        item.add("textures", textures);
        item.add("overrides", overrides);
        files.put("assets/minecraft/models/item/" + base + ".json", json(item));
        return new Built(files, backgrounds, widths, lefts, numbers);
    }

    /** One flat square from -1 to 17 facing both ways, its picture the whole texture, lit from the front, as it is in a slot. */
    static JsonObject model(String texture) {
        return model(texture, new MenuSkin.Piece(null, 0, 0, 0, 0));
    }

    /** (t292) The square reaching out of the slot as far as the piece does on each side (the model's y goes up: the top is its high end). */
    static JsonObject model(String texture, MenuSkin.Piece piece) {
        JsonObject model = new JsonObject();
        model.addProperty("gui_light", "front");
        JsonObject textures = new JsonObject();
        textures.addProperty("0", texture);
        textures.addProperty("particle", texture);
        model.add("textures", textures);
        JsonObject element = new JsonObject();
        element.add("from", numbers(-1 - piece.left(), -1 - piece.bottom(), 8));
        element.add("to", numbers(17 + piece.right(), 17 + piece.top(), 8));
        element.addProperty("shade", false);
        JsonObject faces = new JsonObject();
        faces.add("south", face(0, 0, 16, 16));
        faces.add("north", face(16, 0, 0, 16));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        model.add("elements", elements);
        JsonObject display = new JsonObject();
        JsonObject gui = new JsonObject();
        gui.add("rotation", numbers(0, 0, 0));
        gui.add("translation", numbers(0, 0, 0));
        gui.add("scale", numbers(1, 1, 1));
        display.add("gui", gui);
        model.add("display", display);
        return model;
    }

    private static JsonObject face(int u0, int v0, int u1, int v1) {
        JsonObject face = new JsonObject();
        JsonArray uv = new JsonArray();
        uv.add(u0);
        uv.add(v0);
        uv.add(u1);
        uv.add(v1);
        face.add("uv", uv);
        face.addProperty("texture", "#0");
        return face;
    }

    private static JsonArray numbers(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) array.add(value);
        return array;
    }

    /** A space of +-2^i pixels of the font ({@code negative}: to the left). */
    static char space(int i, boolean negative) {
        return (char) (0xF800 + (negative ? 0x10 : 0) + i);
    }

    /** Spaces of the font that move the next glyph {@code pixels} to the right (to the left when negative). */
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

    /** How far the spaces of {@code text} move (the other characters count nothing). */
    static int advance(String text) {
        int out = 0;
        for (char ch : text.toCharArray()) {
            for (int i = 0; i < SPACES.length; i++) {
                if (ch == space(i, false)) out += SPACES[i];
                if (ch == space(i, true)) out -= SPACES[i];
            }
        }
        return out;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] json(JsonObject object) {
        return new GsonBuilder().disableHtmlEscaping().create().toJson(object).getBytes(StandardCharsets.UTF_8);
    }
}
