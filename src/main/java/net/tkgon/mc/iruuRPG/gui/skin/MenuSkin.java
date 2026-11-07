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
 * @param background  the picture behind the menu (176 wide; 222 high for 6 rows with the player's inventory), or null
 * @param originX     (t292) where in the picture the window's top left is: the picture reaches this far out of the window on the left
 * @param originY     (t292) and this far over its top (a tab standing out of the window)
 * @param title       the text of the title (null: the menu's own)
 * @param titleX      (t293) how far from the window's left the title's text starts (the game's: 8)
 * @param titleColor  its colour
 * @param hideFrame   whether the menu's glass panes outside the parts are taken away (the background shows there)
 * @param testersOnly whether only the testers (operators, and players with {@code iruurpg.uitest}) see it; the others see the menu as before
 * @param parts       the pictures over the slots
 */
public record MenuSkin(String id, BufferedImage background, int originX, int originY, String title, int titleX, TextColor titleColor, boolean hideFrame,
                       boolean testersOnly, List<Part> parts) {

    /** A slot is 18 pixels on a side in the window (a 16 pixel item and its rim). */
    public static final int SLOT = 18;
    /** The slots of a row of a chest. */
    public static final int COLUMNS = 9;
    /** (t292) How far a piece can reach out of its slot (a model's square is from -16 to 32; a slot is from -1 to 17). */
    public static final int MAX_OVERFLOW = 15;

    /**
     * A picture over some slots: {@code tiles} has the piece of each of {@code slots} (null for a slot with nothing over it), and a click on any of them is a click
     * on {@code button}, whose item's name and lore every piece shows. (t293) The button may be a slot outside the part: the part is the button moved there (its
     * own slot is emptied, and a click on it does nothing), so a look can lay the menu's buttons out anew.
     */
    public record Part(String name, List<Integer> slots, int button, List<Piece> tiles) {
    }

    /** (t292) The piece of a slot: its picture, and how far it reaches out of the slot on each side (the pieces at the edge of a part that overflows). */
    public record Piece(BufferedImage image, int left, int top, int right, int bottom) {
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

    /** {@link #cut(BufferedImage, List, int[])} with nothing out of the slots. */
    public static List<Piece> cut(BufferedImage picture, List<Integer> slots) {
        return cut(picture, slots, new int[4]);
    }

    /**
     * Cuts {@code picture} into the pieces of {@code slots}: the picture covers the rectangle round them, 18 pixels a slot, and (t292) {@code overflow} pixels more
     * on the left, the top, the right and the bottom, which go to the pieces at that edge; null when it is not that big.
     */
    public static List<Piece> cut(BufferedImage picture, List<Integer> slots, int[] overflow) {
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
        int l = overflow[0];
        int t = overflow[1];
        int r = overflow[2];
        int b = overflow[3];
        if (picture.getWidth() != (right - left + 1) * SLOT + l + r || picture.getHeight() != (bottom - top + 1) * SLOT + t + b) return null;
        List<Piece> out = new ArrayList<>();
        for (int slot : slots) {
            int column = slot % COLUMNS;
            int row = slot / COLUMNS;
            int pl = column == left ? l : 0;
            int pt = row == top ? t : 0;
            int pr = column == right ? r : 0;
            int pb = row == bottom ? b : 0;
            BufferedImage tile = new BufferedImage(SLOT + pl + pr, SLOT + pt + pb, BufferedImage.TYPE_INT_ARGB);
            int x0 = l + (column - left) * SLOT - pl;
            int y0 = t + (row - top) * SLOT - pt;
            for (int x = 0; x < tile.getWidth(); x++) for (int y = 0; y < tile.getHeight(); y++) tile.setRGB(x, y, picture.getRGB(x0 + x, y0 + y));
            out.add(new Piece(tile, pl, pt, pr, pb));
        }
        return out;
    }
}
