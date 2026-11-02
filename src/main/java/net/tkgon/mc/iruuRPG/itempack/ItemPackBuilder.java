package net.tkgon.mc.iruuRPG.itempack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.tkgon.mc.iruuRPG.item.ItemModelSpec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Makes the resource pack that dresses the items: every item is made of a base item (a sword, a bow, a stick, a leather
 * chestplate, ...) and wears the look of another vanilla item by custom model data. The pack carries
 * <ul>
 *   <li>the model of each base item as the game has it (the plugin carries the vanilla file, {@code itempack/base/<item>.json}) with an
 *       override added for each look: {@code custom_model_data} number to a model;</li>
 *   <li>one model for each look: it points at the vanilla item (its texture, or the whole vanilla model), nothing is drawn anew;</li>
 *   <li>for an item that looks like a Blockbench model ({@code model:}), the game's model made from it ({@link ItemModelMaker}) and its
 *       textures, under {@code bbmodel/<model>/}.</li>
 * </ul>
 * An item whose look is its base item's own needs nothing. The numbers come from {@link ItemCmdIndex}. The zip is the same bytes
 * for the same input (fixed times, sorted entries), so its hash changes only when a look does.
 */
public final class ItemPackBuilder {

    public static final String NAMESPACE = "iruurpg";
    private static final Gson GSON = new GsonBuilder().create();

    /** Base items held like a tool: the look is drawn in the hand the way a sword is. */
    private static final Set<String> HANDHELD = Set.of("iron_sword", "iron_axe", "iron_hoe", "blaze_rod");
    /** Base items whose model carries the draw of a bow / crossbow: the look takes its place inside that model. */
    private static final Set<String> BOWS = Set.of("bow", "crossbow");
    /** Items whose flat texture has another name than the item. */
    private static final Map<String, String> TEXTURE_OF = Map.of(
            "crossbow", "crossbow_standby", "compass", "compass_00", "clock", "clock_00", "recovery_compass", "recovery_compass_00");

    private ItemPackBuilder() {
    }

    /**
     * An item and the look it wears: {@code base} is the item's base material, {@code texture} the vanilla item to look like (empty = its own
     * look), {@code model} the Blockbench model it looks like instead (null = none; a model that cannot be made leaves the texture's look) and
     * {@code attackType} the item's kind (MELEE / RANGE / DEPLOY / ...: the way a model is held when its yml does not say).
     */
    public record Look(String itemId, String base, String texture, ItemModelSpec model, String attackType) {
        public Look(String itemId, String base, String texture) {
            this(itemId, base, texture, null, null);
        }
    }

    /** What was built: the zip, the item ids that wear a look (with their numbers) and the problems found. */
    public record Result(byte[] zip, Map<String, Integer> numbers, List<String> warnings) {
    }

    /**
     * @param looks     the items
     * @param index     the numbers (new ones are given, and the caller saves the index)
     * @param templates the vanilla model of a base item by its lower case name ({@code "iron_sword"}), as JSON text; null when not known
     * @param flat      whether a vanilla item is a flat picture under {@code item/<name>} (not a block, not a model drawn by the game)
     */
    public static Result build(List<Look> looks, ItemCmdIndex index, Function<String, String> templates, Predicate<String> flat) throws IOException {
        return build(looks, index, templates, flat, name -> null);
    }

    /**
     * @param pictures a picture (PNG) the pack carries for a look by its name, or null for a look that is a vanilla item's own: an item's own
     *                 drawing (a texture name that is not a vanilla item, from the plugin's {@code item-textures} folder), or the
     *                 same picture already colored of a vanilla item the game colors by itself (a fern is gray in its file and
     *                 green in the game). It is put in the pack as the look's own texture.
     */
    public static Result build(List<Look> looks, ItemCmdIndex index, Function<String, String> templates, Predicate<String> flat,
                               Function<String, byte[]> pictures) throws IOException {
        return build(looks, index, templates, flat, pictures, id -> null);
    }

    /**
     * @param bbmodels the text of a Blockbench model ({@code models/<id>.bbmodel}) by its id, or null when there is no such file
     */
    public static Result build(List<Look> looks, ItemCmdIndex index, Function<String, String> templates, Predicate<String> flat,
                               Function<String, byte[]> pictures, Function<String, String> bbmodels) throws IOException {
        List<String> warnings = new ArrayList<>();
        Map<String, BbItemModel> read = new TreeMap<>();
        TreeMap<String, byte[]> files = new TreeMap<>();
        JsonObject mcmeta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 22);
        pack.addProperty("description", "iruuRPG アイテムの見た目のパック (プラグインが作ります。手で直さないでください)");
        mcmeta.add("pack", pack);
        files.put("pack.mcmeta", json(mcmeta));

        // base item -> (number -> model)
        Map<String, TreeMap<Integer, String>> overrides = new TreeMap<>();
        Map<String, Integer> numbers = new TreeMap<>();
        for (Look look : looks) {
            String base = lower(look.base());
            if (look.model() != null && modelLook(look, base, index, templates, bbmodels, read, files, numbers, overrides, warnings)) continue;
            String texture = clean(look.texture());
            if (base.isEmpty() || texture.isEmpty() || texture.equals(base)) continue;
            if (texture == null || !texture.matches("[a-z0-9_]+")) {
                warnings.add(look.itemId() + ": texture '" + look.texture() + "' is not a vanilla item name (letters, digits, _): the item keeps the look of its base item");
                continue;
            }
            if (!base.matches("[a-z0-9_]+") || templates.apply(base) == null) {
                warnings.add(look.itemId() + ": the base item '" + look.base() + "' has no vanilla model in the plugin: the item keeps its look");
                continue;
            }
            String group = group(base);
            String modelPath = group + "/" + texture;
            byte[] picture = pictures.apply(texture);
            JsonObject model = picture != null ? pictureModel(base, texture) : lookModel(base, texture, flat);
            if (picture != null) files.put("assets/" + NAMESPACE + "/textures/item/custom/" + texture + ".png", picture);
            files.put("assets/" + NAMESPACE + "/models/item/" + modelPath + ".json", json(model));
            int number = index.assign(look.itemId());
            numbers.put(look.itemId(), number);
            overrides.computeIfAbsent(base, key -> new TreeMap<>()).put(number, NAMESPACE + ":item/" + modelPath);
        }

        // the model of each base item: the vanilla file with our overrides after its own (the last one that matches wins)
        for (Map.Entry<String, TreeMap<Integer, String>> entry : overrides.entrySet()) {
            JsonObject vanilla;
            try {
                JsonElement parsed = JsonParser.parseString(templates.apply(entry.getKey()));
                vanilla = parsed.getAsJsonObject();
            } catch (RuntimeException e) {
                warnings.add("the vanilla model of '" + entry.getKey() + "' could not be read: " + e.getMessage());
                continue;
            }
            JsonArray list = vanilla.has("overrides") ? vanilla.getAsJsonArray("overrides") : new JsonArray();
            for (Map.Entry<Integer, String> look : entry.getValue().entrySet()) {
                JsonObject override = new JsonObject();
                JsonObject predicate = new JsonObject();
                predicate.addProperty("custom_model_data", look.getKey());
                override.add("predicate", predicate);
                override.addProperty("model", look.getValue());
                list.add(override);
            }
            vanilla.add("overrides", list);
            files.put("assets/minecraft/models/item/" + entry.getKey() + ".json", json(vanilla));
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(file.getKey());
                zipEntry.setTime(0L);
                out.putNextEntry(zipEntry);
                out.write(file.getValue());
                out.closeEntry();
            }
        }
        return new Result(bytes.toByteArray(), numbers, warnings);
    }

    /**
     * Puts the look of an item that looks like a Blockbench model in the pack: its model (one for each item, as each holds it its own way), the
     * model's textures (once for all its items) and the override. False when it cannot be made (the item then wears its texture's look, if any).
     */
    private static boolean modelLook(Look look, String base, ItemCmdIndex index, Function<String, String> templates, Function<String, String> bbmodels,
                                     Map<String, BbItemModel> read, TreeMap<String, byte[]> files, Map<String, Integer> numbers,
                                     Map<String, TreeMap<Integer, String>> overrides, List<String> warnings) {
        String id = look.model().id();
        if (!base.matches("[a-z0-9_]+") || templates.apply(base) == null) {
            warnings.add(look.itemId() + ": the base item '" + look.base() + "' has no vanilla model in the plugin: the model '" + id + "' is not used");
            return false;
        }
        BbItemModel model = read.get(id);
        if (model == null && !read.containsKey(id)) {
            String text = bbmodels.apply(id);
            if (text == null) {
                warnings.add(look.itemId() + ": there is no models/" + id + ".bbmodel: the item keeps its look");
            } else {
                try {
                    model = BbItemModel.parse(id, text);
                    for (String warning : model.warnings) warnings.add("model " + id + ": " + warning);
                } catch (RuntimeException exception) {
                    warnings.add(look.itemId() + ": models/" + id + ".bbmodel could not be read (" + exception.getMessage() + "): the item keeps its look");
                }
            }
            read.put(id, model);
        }
        if (model == null) return false;
        String folder = "bbmodel/" + id;
        List<String> problems = new ArrayList<>();
        JsonObject json;
        try {
            json = ItemModelMaker.make(model, look.model(), ItemModelMaker.defaultHold(look.attackType(), base), NAMESPACE + ":item/" + folder + "/", problems);
        } catch (RuntimeException exception) {
            warnings.add(look.itemId() + ": the model '" + id + "' could not be made (" + exception + "): the item keeps its look");
            return false;
        }
        for (String problem : problems) warnings.add(look.itemId() + " (model " + id + "): " + problem);
        if (json == null) {
            warnings.add(look.itemId() + ": the model '" + id + "' has nothing to draw: the item keeps its look");
            return false;
        }
        for (BbItemModel.Texture texture : model.textures) files.put("assets/" + NAMESPACE + "/textures/item/" + folder + "/" + texture.name() + ".png", texture.png());
        int number = index.assign(look.itemId());
        numbers.put(look.itemId(), number);
        files.put("assets/" + NAMESPACE + "/models/item/" + folder + "/" + number + ".json", json(json));
        overrides.computeIfAbsent(base, key -> new TreeMap<>()).put(number, NAMESPACE + ":item/" + folder + "/" + number);
        return true;
    }

    /** The folder of the models of a base item's looks (items of one pose share a model for a look). */
    static String group(String base) {
        if (HANDHELD.contains(base)) return "handheld";
        if (BOWS.contains(base)) return base;
        return "flat";
    }

    /** The model of a look on a base item. */
    static JsonObject lookModel(String base, String texture, Predicate<String> flat) {
        JsonObject model = new JsonObject();
        boolean picture = flat.test(texture);
        String file = TEXTURE_OF.getOrDefault(texture, texture);
        if (HANDHELD.contains(base) && picture) {
            model.addProperty("parent", "minecraft:item/handheld");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "minecraft:item/" + file);
            model.add("textures", textures);
        } else if (BOWS.contains(base) && picture) {
            // (the bow's own model keeps the way it is held; only its picture is another)
            model.addProperty("parent", "minecraft:item/" + base);
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "minecraft:item/" + file);
            model.add("textures", textures);
        } else {
            // the whole vanilla model of the look: a block stays a block, a flat item stays flat
            model.addProperty("parent", "minecraft:item/" + texture);
        }
        return model;
    }

    /** The model of a look whose picture is in the pack ({@code textures/item/custom/<name>.png}). */
    static JsonObject pictureModel(String base, String texture) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", HANDHELD.contains(base) ? "minecraft:item/handheld" : BOWS.contains(base) ? "minecraft:item/" + base : "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", NAMESPACE + ":item/custom/" + texture);
        model.add("textures", textures);
        return model;
    }

    private static String lower(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    /** A vanilla item name as written in a yml: any case, with or without {@code minecraft:}. */
    static String clean(String text) {
        String value = lower(text);
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    private static byte[] json(JsonObject object) {
        return GSON.toJson(object).getBytes(StandardCharsets.UTF_8);
    }
}
