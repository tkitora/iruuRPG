package net.tkgon.mc.iruuRPG.gui.skin;

import net.kyori.adventure.text.format.TextColor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * (t291, trial) The look of one menu of chest size: a picture behind it (in place of the chest's), the title over that, and pictures over the slots ({@link Part}),
 * each cut into one piece a slot so that the pieces of slots side by side join into one picture. Read from {@code ui/<id>.yml} by {@link MenuSkins}.
 *
 * @param id          the menu ({@code main}: the main menu)
 * @param background  the picture behind the menu, from the top left of the chest's window (176 wide; 222 high for 6 rows with the player's inventory), or null
 * @param title       the text of the title (null: the menu's own)
 * @param titleColor  its colour
 * @param hideFrame   whether the menu's glass panes outside the parts are taken away (the background shows there)
 * @param testersOnly whether only the testers (operators, and players with {@code iruurpg.uitest}) see it; the others see the menu as before
 * @param parts       the pictures over the slots
 */
public record MenuSkin(String id, BufferedImage background, String title, TextColor titleColor, boolean hideFrame, boolean testersOnly, List<Part> parts) {

    /** A slot is 18 pixels on a side in the window (a 16 pixel item and its rim). */
    public static final int SLOT = 18;
    /** The slots of a row of a chest. */
    public static final int COLUMNS = 9;

    /**
     * A picture over some slots: {@code tiles} has the piece of each of {@code slots} (null for a slot with nothing over it), and a click on any of them is a click
     * on {@code button}, whose item's name and lore every piece shows.
     */
    public record Part(String name, List<Integer> slots, int button, List<BufferedImage> tiles) {
    }

    /**
     * The slots of a text: numbers and ranges by commas ({@code "19-20, 28"}), or a list of them. Null when a piece cannot be read or a slot is outside
     * {@code 0 .. size - 1}.
     */
    public static List<Integer> slots(Object value, int size) {
        List<Object> pieces = new ArrayList<>();
        if (value instanceof List<?> list) pieces.addAll(list);
        else if (value != null) for (String piece : value.toString().split(",")) pieces.add(piece);
        Set<Integer> out = new LinkedHashSet<>();
        for (Object piece : pieces) {
            String text = String.valueOf(piece).trim();
            if (text.isEmpty()) continue;
            try {
                int dash = text.indexOf('-', 1);
                int from = Integer.parseInt((dash < 0 ? text : text.substring(0, dash)).trim());
                int to = dash < 0 ? from : Integer.parseInt(text.substring(dash + 1).trim());
                if (from < 0 || to >= size || to < from) return null;
                for (int slot = from; slot <= to; slot++) out.add(slot);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return out.isEmpty() ? null : List.copyOf(out);
    }

    /**
     * Cuts {@code picture} into the pieces of {@code slots}: the picture covers the rectangle round them (its top left is the top left slot of it), 18 pixels a slot;
     * null when it is not that big.
     */
    public static List<BufferedImage> cut(BufferedImage picture, List<Integer> slots) {
        int top = Integer.MAX_VALUE;
        int left = Integer.MAX_VALUE;
        int bottom = 0;
        int right = 0;
        for (int slot : slots) {
            top = Math.min(top, slot / COLUMNS);
            left = Math.min(left, slot % COLUMNS);
            bottom = Math.max(bottom, slot / COLUMNS);
            right = Math.max(right, slot % COLUMNS);
        }
        if (picture.getWidth() != (right - left + 1) * SLOT || picture.getHeight() != (bottom - top + 1) * SLOT) return null;
        List<BufferedImage> out = new ArrayList<>();
        for (int slot : slots) {
            BufferedImage tile = new BufferedImage(SLOT, SLOT, BufferedImage.TYPE_INT_ARGB);
            int x0 = (slot % COLUMNS - left) * SLOT;
            int y0 = (slot / COLUMNS - top) * SLOT;
            for (int x = 0; x < SLOT; x++) for (int y = 0; y < SLOT; y++) tile.setRGB(x, y, picture.getRGB(x0 + x, y0 + y));
            out.add(tile);
        }
        return out;
    }
}
