package net.tkgon.mc.iruuRPG.item;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An item's {@code model:} (docs/item-models.md): the Blockbench model it looks like ({@code models/<id>.bbmodel}) and how it is held.
 *
 * <pre>
 * model: sample_sword            # just the model; held the way the item's kind is (melee: sword, ranged: staff, deployed: item)
 * model:
 *   id: sample_sword
 *   hold: sword                  # sword / staff / rod / bow / item (= lantern) / block / model (Blockbench's Display settings)
 *   scale: 1.2                   # in the hand, on the ground, in a frame and on a head (the inventory slot is fitted)
 *   hand: {rotation: [0, 0, 10], translation: [0, -2, 0], scale: 1}   # in the hands: turned about the grip and moved along the model
 *   gui:  {rotation: [0, 20, 0], translation: [0, 0, 0], scale: 0.9}  # in the inventory slot
 *   display:                     # exactly these values for a slot (the game's display of a model)
 *     thirdperson_righthand: {rotation: [0, -90, 55], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85]}
 * </pre>
 *
 * @param id      the model's file name without {@code .bbmodel}
 * @param hold    the way it is held, or null for the one of the item's kind (or Blockbench's Display settings when the model has them)
 * @param scale   the size in the hands, on the ground, in a frame and on a head
 * @param hand    the change in the hands
 * @param gui     the change in the inventory slot
 * @param display the slots given exactly, by the game's name of the slot
 */
public record ItemModelSpec(String id, String hold, double scale, Adjust hand, Adjust gui, Map<String, Slot> display) {

    /** The ways to hold a model. {@code lantern} is another name of {@code item}. */
    public static final List<String> HOLDS = List.of("sword", "staff", "rod", "bow", "item", "lantern", "block", "model");
    /** The slots of the game's display settings. */
    public static final Set<String> SLOTS = Set.of("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand",
            "gui", "head", "ground", "fixed");

    /** A change of where a model is: turned (degrees x, y, z), moved (1/16 block, along the model) and sized. */
    public record Adjust(double[] rotation, double[] translation, double scale) {
        public static final Adjust NONE = new Adjust(new double[]{0, 0, 0}, new double[]{0, 0, 0}, 1.0);

        public boolean none() {
            return scale == 1.0 && isZero(rotation) && isZero(translation);
        }
    }

    /** One slot of the game's display settings, as written. */
    public record Slot(double[] rotation, double[] translation, double[] scale) {
    }

    public ItemModelSpec {
        hold = hold == null || hold.isBlank() ? null : hold.strip().toLowerCase(Locale.ROOT);
        if ("lantern".equals(hold)) hold = "item";
        hand = hand == null ? Adjust.NONE : hand;
        gui = gui == null ? Adjust.NONE : gui;
        display = display == null ? Map.of() : Map.copyOf(display);
    }

    public static ItemModelSpec of(String id) {
        return new ItemModelSpec(id, null, 1.0, Adjust.NONE, Adjust.NONE, Map.of());
    }

    /**
     * The {@code model:} of an item: a model id, or a section with {@code id} and the ways it is held. Null when there is none or it cannot be
     * read (each problem goes to {@code warn}).
     */
    public static ItemModelSpec read(Object raw, Consumer<String> warn) {
        if (raw == null) return null;
        if (raw instanceof String text) {
            String id = cleanId(text, warn);
            return id == null ? null : of(id);
        }
        Map<String, Object> map = map(raw);
        if (map == null) {
            warn.accept("model: is neither a model id nor a section (id: ...)");
            return null;
        }
        String id = cleanId(String.valueOf(map.getOrDefault("id", "")), warn);
        if (id == null) return null;
        String hold = map.get("hold") == null ? null : String.valueOf(map.get("hold")).strip().toLowerCase(Locale.ROOT);
        if (hold != null && !HOLDS.contains(hold)) {
            warn.accept("model.hold '" + hold + "' is not one of " + String.join(" / ", HOLDS) + " (the one of the item's kind is used)");
            hold = null;
        }
        double scale = number(map.get("scale"), 1.0, "model.scale", warn);
        if (scale <= 0) {
            warn.accept("model.scale must be above 0 (1 is used)");
            scale = 1.0;
        }
        Adjust hand = adjust(map.get("hand"), "model.hand", warn);
        Adjust gui = adjust(map.get("gui"), "model.gui", warn);
        Map<String, Slot> display = new LinkedHashMap<>();
        Map<String, Object> slots = map(map.get("display"));
        if (slots != null) {
            for (Map.Entry<String, Object> entry : slots.entrySet()) {
                String slot = entry.getKey().toLowerCase(Locale.ROOT);
                if (!SLOTS.contains(slot)) {
                    warn.accept("model.display." + entry.getKey() + " is not a slot of the game (" + String.join(" / ", SLOTS.stream().sorted().toList()) + ")");
                    continue;
                }
                Map<String, Object> value = map(entry.getValue());
                if (value == null) continue;
                double[] scaleOf = value.get("scale") instanceof Number number
                        ? new double[]{number.doubleValue(), number.doubleValue(), number.doubleValue()}
                        : triple(value.get("scale"), new double[]{1, 1, 1}, "model.display." + slot + ".scale", warn);
                display.put(slot, new Slot(triple(value.get("rotation"), new double[]{0, 0, 0}, "model.display." + slot + ".rotation", warn),
                        triple(value.get("translation"), new double[]{0, 0, 0}, "model.display." + slot + ".translation", warn), scaleOf));
            }
        }
        return new ItemModelSpec(id, hold, scale, hand, gui, display);
    }

    private static String cleanId(String text, Consumer<String> warn) {
        String id = text == null ? "" : text.strip().replaceAll("(?i)\\.bbmodel$", "");
        if (id.isEmpty()) {
            warn.accept("model: has no id (the file name in models/ without .bbmodel)");
            return null;
        }
        if (!id.matches("[a-z0-9_]+")) {
            warn.accept("model '" + id + "' must be lower case letters, digits and _ (a resource pack path cannot have others)");
            return null;
        }
        return id;
    }

    private static Adjust adjust(Object raw, String name, Consumer<String> warn) {
        Map<String, Object> map = map(raw);
        if (map == null) return Adjust.NONE;
        double scale = number(map.get("scale"), 1.0, name + ".scale", warn);
        return new Adjust(triple(map.get("rotation"), new double[]{0, 0, 0}, name + ".rotation", warn),
                triple(map.containsKey("translation") ? map.get("translation") : map.get("move"), new double[]{0, 0, 0}, name + ".translation", warn),
                scale > 0 ? scale : 1.0);
    }

    private static double[] triple(Object raw, double[] fallback, String name, Consumer<String> warn) {
        if (raw == null) return fallback;
        if (raw instanceof List<?> list && list.size() == 3) {
            double[] result = new double[3];
            for (int index = 0; index < 3; index++) {
                if (!(list.get(index) instanceof Number number)) {
                    warn.accept(name + " must be 3 numbers [x, y, z]");
                    return fallback;
                }
                result[index] = number.doubleValue();
            }
            return result;
        }
        warn.accept(name + " must be 3 numbers [x, y, z]");
        return fallback;
    }

    private static double number(Object raw, double fallback, String name, Consumer<String> warn) {
        if (raw == null) return fallback;
        if (raw instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(raw).strip());
        } catch (NumberFormatException exception) {
            warn.accept(name + " must be a number");
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw) {
        if (raw instanceof ConfigurationSection section) return section.getValues(false);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
            return result;
        }
        return null;
    }

    private static boolean isZero(double[] values) {
        for (double value : values) if (value != 0.0) return false;
        return true;
    }

    /** What the yml says, for logs and the item's fingerprint. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add(id);
        if (hold != null) parts.add("hold=" + hold);
        if (scale != 1.0) parts.add("scale=" + scale);
        if (!hand.none()) parts.add("hand=" + java.util.Arrays.toString(hand.rotation()) + java.util.Arrays.toString(hand.translation()) + hand.scale());
        if (!gui.none()) parts.add("gui=" + java.util.Arrays.toString(gui.rotation()) + java.util.Arrays.toString(gui.translation()) + gui.scale());
        if (!display.isEmpty()) parts.add("display=" + display.keySet());
        return String.join(" ", parts);
    }
}
