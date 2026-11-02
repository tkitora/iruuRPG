package net.tkgon.mc.iruuRPG.itempack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.tkgon.mc.iruuRPG.item.ItemModelSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Makes the game's item model of an item that looks like a Blockbench model ({@link BbItemModel}, docs/item-models.md): the model's cubes as
 * elements, its textures, and the display settings of each slot (in the hands, the inventory slot, on the ground, in a frame, on a head).
 * <p>
 * The ways to hold ({@code hold:}) are made from the game's own items, so the model is where their picture would be:
 * <ul>
 *   <li>{@code sword} / {@code staff} / {@code rod} / {@code bow}: held like a sword (and a stick), a fishing rod, a bow. The model is made
 *       standing up (it points to +Y) and its flat side faces the front or the back (±Z); the hand holds its grip (a group or locator named
 *       {@code grip}, else a point on its middle line, low for a sword, higher for a staff, the middle for a bow), and it keeps its own size
 *       (16 = a block; a vanilla sword is about 22 long).</li>
 *   <li>{@code item} (= {@code lantern}): held like an item's picture (a lantern, a flower): the model is fitted into the picture's square.</li>
 *   <li>{@code block}: held like a block: the model is fitted into a block.</li>
 *   <li>{@code model}: the display settings saved in the model (Blockbench's Display mode of a Java Block/Item model).</li>
 * </ul>
 * The inventory slot, the ground and a frame always fit the model into the square of a picture (a block for {@code block}), so a model of any
 * size shows whole. {@code hand}, {@code gui} and {@code scale} change the result, and {@code display} gives a slot exactly.
 */
final class ItemModelMaker {

    /** A display setting of a vanilla model (degrees, 1/16 block, one size for all axes). */
    record Ref(double[] rotation, double[] translation, double scale) {
        double[][] turn() {
            return Rotation.display(rotation);
        }
    }

    static final Ref NONE = ref(0, 0, 0, 0, 0, 0, 1);
    // item/handheld (a sword, a stick)
    static final Ref HANDHELD_3P = ref(0, -90, 55, 0, 4.0, 0.5, 0.85);
    static final Ref HANDHELD_1P = ref(0, -90, 25, 1.13, 3.2, 1.13, 0.68);
    // item/handheld_rod (a fishing rod)
    static final Ref ROD_3P = ref(0, 90, 55, 0, 4.0, 2.5, 0.85);
    static final Ref ROD_1P = ref(0, 90, 25, 0, 1.6, 0.8, 0.68);
    // item/bow (its first person is the handheld one)
    static final Ref BOW_3P = ref(-80, 260, -40, -1, -2, 2.5, 0.9);
    // item/generated (a flat item)
    static final Ref ITEM_3P = ref(0, 0, 0, 0, 3, 1, 0.55);
    static final Ref ITEM_GROUND = ref(0, 0, 0, 0, 2, 0, 0.5);
    static final Ref ITEM_FIXED = ref(0, 180, 0, 0, 0, 0, 1);
    // block/block
    static final Ref BLOCK_3P = ref(75, 45, 0, 0, 2.5, 0, 0.375);
    static final Ref BLOCK_1P = ref(0, 45, 0, 0, 0, 0, 0.40);
    static final Ref BLOCK_GUI = ref(30, 225, 0, 0, 0, 0, 0.625);
    static final Ref BLOCK_GROUND = ref(0, 0, 0, 0, 3, 0, 0.25);
    static final Ref BLOCK_FIXED = ref(0, 0, 0, 0, 0, 0, 0.5);

    /** A standing model laid along the picture of a tool (from the lower left to the upper right). */
    static final double[][] TOOL = Rotation.z(-45);
    /** The inventory slot of an item-like model: a little from the side and above, so it shows as a thing, not a flat face. */
    static final double[][] ITEM_GUI = Rotation.display(new double[]{15, -30, 0});

    private static final double[] MIDDLE = {8, 8, 8};
    /** The size a fitted model is made: a picture's square, a block. */
    private static final double FIT = 16.0;

    private ItemModelMaker() {
    }

    private static Ref ref(double rx, double ry, double rz, double tx, double ty, double tz, double scale) {
        return new Ref(new double[]{rx, ry, rz}, new double[]{tx, ty, tz}, scale);
    }

    /** The way an item is held when its yml does not say: by the kind of the item (melee, ranged, deployed) and its base item. */
    static String defaultHold(String attackType, String base) {
        String kind = attackType == null ? "" : attackType.toUpperCase(java.util.Locale.ROOT);
        String material = base == null ? "" : base.toLowerCase(java.util.Locale.ROOT);
        return switch (kind) {
            case "MELEE" -> "sword";
            case "RANGE" -> material.equals("bow") || material.equals("crossbow") ? "bow" : "staff";
            default -> "item";
        };
    }

    /** How high on the model (0 = its bottom, 1 = its top) the hand holds it, for a model that marks no grip. */
    static double gripHeight(String hold) {
        return switch (hold) {
            case "sword" -> 0.18;
            case "staff" -> 0.3;
            case "rod" -> 0.12;
            default -> 0.5;
        };
    }

    /**
     * The game's model of the item.
     *
     * @param model       the Blockbench model
     * @param spec        the item's {@code model:}
     * @param defaultHold the way to hold when {@code spec} names none ({@link #defaultHold})
     * @param texturePath where the model's textures are in the pack ({@code iruurpg:item/bbmodel/<model>/}; a texture's name follows)
     * @param warnings    what could not be done as asked
     * @return the model, or null when it has nothing to draw
     */
    static JsonObject make(BbItemModel model, ItemModelSpec spec, String defaultHold, String texturePath, List<String> warnings) {
        if (model.elements.isEmpty()) return null;
        String hold = spec.hold();
        if (hold == null) hold = model.display.isEmpty() ? defaultHold : "model";
        if (hold.equals("model") && model.display.isEmpty()) {
            warnings.add("the model has no display settings (Blockbench's Display mode of a Java Block/Item model): held as '" + defaultHold + "'");
            hold = defaultHold;
        }

        // where the elements go: a model held by its own display settings stays where it was made (those settings were made for it there);
        // any other is moved so its middle is the model's middle, and made smaller when it is longer than a game model may be (the display makes it big again)
        List<BbItemModel.Element> elements = copy(model.elements);
        double[] grip = model.grip == null ? null : model.grip.clone();
        double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (BbItemModel.Element element : elements) {
            for (int axis = 0; axis < 3; axis++) {
                low[axis] = Math.min(low[axis], element.from[axis]);
                high[axis] = Math.max(high[axis], element.to[axis]);
            }
        }
        double size = Math.max(high[0] - low[0], Math.max(high[1] - low[1], high[2] - low[2]));
        boolean outside = low[0] < -16 || low[1] < -16 || low[2] < -16 || high[0] > 32 || high[1] > 32 || high[2] > 32;
        double shrink = size > 46.0 ? 46.0 / size : 1.0;
        if (!hold.equals("model") || outside) {
            if (hold.equals("model")) warnings.add("the model reaches out of the space of a game model (-16 to 32): it is moved and made smaller, so Blockbench's display settings may be off");
            double[] middle = {(low[0] + high[0]) / 2.0, (low[1] + high[1]) / 2.0, (low[2] + high[2]) / 2.0};
            for (BbItemModel.Element element : elements) {
                for (int axis = 0; axis < 3; axis++) {
                    element.from[axis] = MIDDLE[axis] + (element.from[axis] - middle[axis]) * shrink;
                    element.to[axis] = MIDDLE[axis] + (element.to[axis] - middle[axis]) * shrink;
                }
            }
            if (grip != null) for (int axis = 0; axis < 3; axis++) grip[axis] = MIDDLE[axis] + (grip[axis] - middle[axis]) * shrink;
            if (grip == null) {
                double height = gripHeight(hold);
                grip = new double[]{MIDDLE[0], MIDDLE[1] + (low[1] + (high[1] - low[1]) * height - middle[1]) * shrink, MIDDLE[2]};
            }
        } else {
            shrink = 1.0;
            if (grip == null) grip = new double[]{(low[0] + high[0]) / 2.0, low[1] + (high[1] - low[1]) * gripHeight(hold), (low[2] + high[2]) / 2.0};
        }

        JsonObject json = new JsonObject();
        JsonObject textures = new JsonObject();
        boolean first = true;
        for (BbItemModel.Texture texture : model.textures) {
            textures.addProperty(String.valueOf(texture.index()), texturePath + texture.name());
            if (first) {
                textures.addProperty("particle", texturePath + texture.name());
                first = false;
            }
        }
        json.add("textures", textures);
        JsonArray list = new JsonArray();
        for (BbItemModel.Element element : elements) list.add(element(model, element));
        json.add("elements", list);
        json.addProperty("gui_light", hold.equals("item") || hold.equals("block") || hold.equals("model") ? "side" : "front");
        json.add("display", display(model, spec, hold, elements, Rotation.minus(grip, MIDDLE), shrink, warnings));
        return json;
    }

    private static List<BbItemModel.Element> copy(List<BbItemModel.Element> elements) {
        List<BbItemModel.Element> copies = new ArrayList<>();
        for (BbItemModel.Element element : elements) {
            BbItemModel.Element copy = new BbItemModel.Element();
            copy.name = element.name;
            copy.from = element.from.clone();
            copy.to = element.to.clone();
            copy.axis = element.axis;
            copy.angle = element.angle;
            copy.rescale = element.rescale;
            copy.faces.putAll(element.faces);
            copies.add(copy);
        }
        return copies;
    }

    private static JsonObject element(BbItemModel model, BbItemModel.Element element) {
        JsonObject json = new JsonObject();
        json.add("from", array(element.from));
        json.add("to", array(element.to));
        if (element.axis != null) {
            JsonObject rotation = new JsonObject();
            rotation.addProperty("angle", element.angle);
            rotation.addProperty("axis", element.axis);
            rotation.add("origin", array(element.middle()));
            if (element.rescale) rotation.addProperty("rescale", true);
            json.add("rotation", rotation);
        }
        JsonObject faces = new JsonObject();
        for (Map.Entry<String, BbItemModel.Face> entry : element.faces.entrySet()) {
            BbItemModel.Face face = entry.getValue();
            BbItemModel.Texture texture = model.texture(face.texture());
            JsonObject f = new JsonObject();
            f.add("uv", array(new double[]{face.uv()[0] * 16.0 / texture.uvWidth(), face.uv()[1] * 16.0 / texture.uvHeight(),
                    face.uv()[2] * 16.0 / texture.uvWidth(), face.uv()[3] * 16.0 / texture.uvHeight()}));
            f.addProperty("texture", "#" + face.texture());
            int rotation = ((face.rotation() % 360) + 360) % 360;
            if (rotation != 0) f.addProperty("rotation", rotation);
            faces.add(entry.getKey(), f);
        }
        json.add("faces", faces);
        return json;
    }

    /** The display settings of every slot. {@code grip} is from the model's middle (8, 8, 8). */
    private static JsonObject display(BbItemModel model, ItemModelSpec spec, String hold, List<BbItemModel.Element> elements, double[] grip,
                                      double shrink, List<String> warnings) {
        List<double[]> corners = new ArrayList<>();
        for (BbItemModel.Element element : elements) for (double[] corner : element.corners()) corners.add(Rotation.minus(corner, MIDDLE));
        double k = spec.scale();
        ItemModelSpec.Adjust hand = spec.hand();
        ItemModelSpec.Adjust gui = spec.gui();
        double[][] handTurn = Rotation.display(hand.rotation());
        double[][] guiTurn = Rotation.display(gui.rotation());
        JsonObject display = new JsonObject();
        // held by its own display settings: those, and the item's for the slots they leave out
        String fallback = hold.equals("model") ? "item" : hold;
        boolean tool = fallback.equals("sword") || fallback.equals("staff") || fallback.equals("rod") || fallback.equals("bow");
        boolean block = fallback.equals("block");

        if (tool) {
            Ref third = switch (fallback) {
                case "rod" -> ROD_3P;
                case "bow" -> BOW_3P;
                default -> HANDHELD_3P;
            };
            Ref firstPerson = fallback.equals("rod") ? ROD_1P : HANDHELD_1P;
            // the point of the vanilla picture the hand holds: what the third person setting puts at the hand, on the picture
            double[] held = Rotation.times(Rotation.apply(Rotation.transpose(third.turn()), third.translation()), -1.0 / third.scale());
            held[2] = 0.0;
            double[][] turn = Rotation.multiply(TOOL, handTurn);
            double[] at = Rotation.plus(held, Rotation.apply(TOOL, Rotation.times(hand.translation(), k)));
            double size = k * hand.scale() / shrink;
            display.add("thirdperson_righthand", slot(third, turn, size, grip, at, warnings));
            display.add("firstperson_righthand", slot(firstPerson, turn, size, grip, at, warnings));
        } else {
            double[][] turn = handTurn;
            double fit = fit(corners, turn, block ? 3 : 2);
            double size = fit * k * hand.scale();
            double[] from = fitMiddle(corners, turn);
            display.add("thirdperson_righthand", slot(block ? BLOCK_3P : ITEM_3P, turn, size, from, hand.translation(), warnings));
            display.add("firstperson_righthand", slot(block ? BLOCK_1P : HANDHELD_1P, turn, size, from, hand.translation(), warnings));
        }
        double[][] look = tool ? TOOL : block ? Rotation.IDENTITY : ITEM_GUI;
        double[][] guiLook = Rotation.multiply(look, guiTurn);
        display.add("gui", slot(block ? BLOCK_GUI : NONE, guiLook, fit(corners, guiLook, block ? 3 : 2) * gui.scale(), fitMiddle(corners, guiLook), gui.translation(), warnings));
        double[][] flat = tool ? TOOL : Rotation.IDENTITY;
        double flatFit = fit(corners, flat, block ? 3 : 2) * k;
        display.add("ground", slot(block ? BLOCK_GROUND : ITEM_GROUND, flat, flatFit, fitMiddle(corners, flat), new double[]{0, 0, 0}, warnings));
        display.add("fixed", slot(block ? BLOCK_FIXED : ITEM_FIXED, flat, flatFit, fitMiddle(corners, flat), new double[]{0, 0, 0}, warnings));
        // on a head (a mob's helmet, a deployed weapon's stand): standing, the size of a block
        display.add("head", slot(NONE, Rotation.IDENTITY, fit(corners, Rotation.IDENTITY, 3) * k, fitMiddle(corners, Rotation.IDENTITY), new double[]{0, 0, 0}, warnings));

        if (hold.equals("model")) for (Map.Entry<String, BbItemModel.Display> entry : model.display.entrySet()) {
            if (!ItemModelSpec.SLOTS.contains(entry.getKey())) continue;
            BbItemModel.Display value = entry.getValue();
            display.add(entry.getKey(), exact(value.rotation(), value.translation(), value.scale()));
        }
        for (Map.Entry<String, ItemModelSpec.Slot> entry : spec.display().entrySet()) {
            ItemModelSpec.Slot value = entry.getValue();
            display.add(entry.getKey(), exact(value.rotation(), value.translation(), value.scale()));
        }
        return display;
    }

    /**
     * One slot: a model point {@code p} (from the middle) goes to {@code ref} applied to {@code at + size · turn · (p - from)}: the model is
     * turned and sized about {@code from}, which is put at {@code at} of the vanilla item's space, and the vanilla item's setting does the rest.
     */
    static JsonObject slot(Ref ref, double[][] turn, double size, double[] from, double[] at, List<String> warnings) {
        double[][] whole = Rotation.multiply(ref.turn(), turn);
        double scale = ref.scale() * size;
        if (scale > 4.0) {
            warnings.add("a display needs the model " + round(scale) + " times as big, more than the game allows (4): it is drawn smaller");
            scale = 4.0;
        }
        double[] translation = Rotation.minus(Rotation.plus(ref.translation(), Rotation.times(Rotation.apply(ref.turn(), at), ref.scale())),
                Rotation.times(Rotation.apply(whole, from), scale));
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(translation[axis]) > 80.0) {
                warnings.add("a display moves the model more than the game allows (80): it is drawn nearer");
                translation[axis] = Math.copySign(80.0, translation[axis]);
            }
        }
        return exact(Rotation.displayAngles(whole), translation, new double[]{scale, scale, scale});
    }

    /** How much a model turned by {@code turn} is sized to fit the square of a picture (the first {@code axes} axes: 2 = x and y, 3 = a block). */
    static double fit(List<double[]> corners, double[][] turn, int axes) {
        double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] corner : corners) {
            double[] turned = Rotation.apply(turn, corner);
            for (int axis = 0; axis < 3; axis++) {
                low[axis] = Math.min(low[axis], turned[axis]);
                high[axis] = Math.max(high[axis], turned[axis]);
            }
        }
        double size = 0.0;
        for (int axis = 0; axis < axes; axis++) size = Math.max(size, high[axis] - low[axis]);
        return size <= 1.0E-6 ? 1.0 : FIT / size;
    }

    /** The point of the model (from the middle) that is the middle of its box once turned by {@code turn}. */
    static double[] fitMiddle(List<double[]> corners, double[][] turn) {
        double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] corner : corners) {
            double[] turned = Rotation.apply(turn, corner);
            for (int axis = 0; axis < 3; axis++) {
                low[axis] = Math.min(low[axis], turned[axis]);
                high[axis] = Math.max(high[axis], turned[axis]);
            }
        }
        double[] middle = {(low[0] + high[0]) / 2.0, (low[1] + high[1]) / 2.0, (low[2] + high[2]) / 2.0};
        return Rotation.apply(Rotation.transpose(turn), middle);
    }

    private static JsonObject exact(double[] rotation, double[] translation, double[] scale) {
        JsonObject json = new JsonObject();
        json.add("rotation", array(rotation));
        json.add("translation", array(translation));
        json.add("scale", array(scale));
        return json;
    }

    private static JsonArray array(double[] values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(round(value));
        return array;
    }

    private static double round(double value) {
        double rounded = Math.round(value * 1000.0) / 1000.0;
        return rounded == 0.0 ? 0.0 : rounded;
    }
}
