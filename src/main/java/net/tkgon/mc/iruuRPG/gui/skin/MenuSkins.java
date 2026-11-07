package net.tkgon.mc.iruuRPG.gui.skin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.markpack.MarkGlyphs;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * (t291, trial) The menus' own looks: {@code ui/<menu>.yml} of the data folder (the yml repository's {@code ui/}), its pictures beside it, and the pack they make
 * ({@code resourcepacks/iruuui.zip}, {@link UiPackBuilder}). A menu asks for its title ({@link #title}), dresses its items once they are in ({@link #dress}) and
 * turns a clicked slot into the slot of the button it is part of ({@link #button}). A menu with no file, or one only testers see, looks and works as before.
 * <pre>
 * enabled: true               # false: as before
 * testers-only: true          # only operators (and iruurpg.uitest) see it
 * background: main/back.png   # behind the window, from its top left (176 wide; 222 high for a menu of 6 rows)
 * background-origin: [6, 20]   # (t292) where in the picture the window's top left is: the picture reaches that far out on the left and over the top
 * title: ""                   # the title over it ("" none; left out: the menu's own)
 * title-color: "#404040"
 * hide-frame: true            # the menu's glass panes outside the parts are taken away
 * parts:
 *   travel:
 *     slots: 23-24            # numbers and ranges (a list too)
 *     picture: main/travel.png  # 18 pixels a slot over the rectangle of the slots
 *     button: 24              # the menu's button they all are (its name and lore on each; left out: the first slot)
 *     overflow: [0, 4, 0, 0]  # (t292) how far the picture reaches out of the slots on the left, the top, the right, the bottom (one number: all four; 15 at most)
 * </pre>
 * Every problem is a warning, and the part (or the file) is left out. The pictures' names are kept to {@code a-z 0-9 _ - . /}.
 */
public final class MenuSkins {

    public static final String PACK_FILE = "iruuui.zip";
    public static final String TESTER_PERMISSION = "iruurpg.uitest";
    /** The chest of the menus: 6 rows. */
    private static final int SIZE = 54;

    private final File dataFolder;
    private final Consumer<String> warn;
    private volatile Map<String, MenuSkin> skins = Map.of();
    private volatile UiPackBuilder.Built built;

    public MenuSkins(File dataFolder, Consumer<String> warn) {
        this.dataFolder = dataFolder;
        this.warn = warn;
    }

    /** Reads {@code ui/} again and writes the pack (taken away when no menu has a look); true when the pack's file changed. Never throws. */
    public boolean reload() {
        try {
            Map<String, MenuSkin> read = read(new File(dataFolder, "ui"));
            UiPackBuilder.Built made = read.isEmpty() ? null : UiPackBuilder.build(new ArrayList<>(read.values()));
            boolean changed = write(made);
            skins = read;
            built = made;
            return changed;
        } catch (IOException | RuntimeException exception) {
            warn.accept("ui: the menus' looks could not be read (" + exception + "); the menus look as before");
            skins = Map.of();
            built = null;
            return false;
        }
    }

    private Map<String, MenuSkin> read(File folder) {
        Map<String, MenuSkin> out = new HashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return out;
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            if (!id.matches("[a-z0-9_]+")) {
                warn.accept("ui/" + file.getName() + ": the name of a menu is a-z 0-9 _ only; left out");
                continue;
            }
            MenuSkin skin = skin(id, YamlConfiguration.loadConfiguration(file), folder);
            if (skin != null) out.put(id, skin);
        }
        return out;
    }

    private MenuSkin skin(String id, YamlConfiguration yaml, File folder) {
        if (!yaml.getBoolean("enabled", true)) return null;
        String where = "ui/" + id + ".yml";
        BufferedImage background = yaml.isString("background") ? picture(folder, yaml.getString("background"), where + " background") : null;
        if (background != null && (background.getHeight() > 256 || background.getWidth() > 256)) {
            warn.accept(where + ": the background is at most 256 x 256; left out");
            background = null;
        }
        int[] origin = {0, 0};
        if (background != null && yaml.contains("background-origin")) {
            int[] read = numbers(yaml.get("background-origin"), 2);
            if (read == null || read[0] < 0 || read[1] < 0 || read[0] >= background.getWidth() || read[1] + UiPackBuilder.ASCENT > background.getHeight()) {
                warn.accept(where + ": background-origin " + yaml.get("background-origin") + " is not [x, y] inside the picture (y at most its height - 13); the top left is used");
            } else {
                origin = read;
            }
        }
        TextColor color = TextColor.color(0x404040);
        String colorText = yaml.getString("title-color");
        if (colorText != null) {
            TextColor parsed = colorText.startsWith("#") ? TextColor.fromHexString(colorText) : NamedTextColor.NAMES.value(colorText.toLowerCase(Locale.ROOT));
            if (parsed == null) warn.accept(where + ": title-color " + colorText + " is no colour");
            else color = parsed;
        }
        List<MenuSkin.Part> parts = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("parts");
        Set<Integer> taken = new HashSet<>();
        if (section != null) {
            for (String name : section.getKeys(false)) {
                String at = where + " parts." + name;
                ConfigurationSection part = section.getConfigurationSection(name);
                if (part == null || !name.matches("[a-z0-9_]+")) {
                    warn.accept(at + ": a part is named a-z 0-9 _ and has slots and a picture; left out");
                    continue;
                }
                List<Integer> slots = MenuSkin.slots(part.get("slots"), SIZE);
                if (slots == null) {
                    warn.accept(at + ": slots " + part.get("slots") + " are not slots of the menu (0 - " + (SIZE - 1) + "); left out");
                    continue;
                }
                if (slots.stream().anyMatch(taken::contains)) {
                    warn.accept(at + ": a slot of it is another part's; left out");
                    continue;
                }
                BufferedImage picture = picture(folder, part.getString("picture"), at);
                if (picture == null) continue;
                int[] overflow = new int[4];
                if (part.contains("overflow")) {
                    Object value = part.get("overflow");
                    int[] read = value instanceof Number number ? new int[]{number.intValue(), number.intValue(), number.intValue(), number.intValue()} : numbers(value, 4);
                    if (read == null || java.util.Arrays.stream(read).anyMatch(n -> n < 0 || n > MenuSkin.MAX_OVERFLOW)) {
                        warn.accept(at + ": overflow " + value + " is not 0 - " + MenuSkin.MAX_OVERFLOW + " (one number, or [left, top, right, bottom]); none is used");
                    } else {
                        overflow = read;
                    }
                }
                List<MenuSkin.Piece> tiles = MenuSkin.cut(picture, slots, overflow);
                if (tiles == null) {
                    warn.accept(at + ": the picture is not 18 pixels a slot over the rectangle of its slots (and its overflow); left out");
                    continue;
                }
                int button = part.getInt("button", slots.get(0));
                if (!slots.contains(button)) {
                    warn.accept(at + ": button " + button + " is not one of its slots; the first slot is the button");
                    button = slots.get(0);
                }
                taken.addAll(slots);
                parts.add(new MenuSkin.Part(name, slots, button, tiles));
            }
        }
        String title = yaml.isString("title") ? yaml.getString("title") : null;
        return new MenuSkin(id, background, origin[0], origin[1], title, color, yaml.getBoolean("hide-frame", true), yaml.getBoolean("testers-only", false), parts);
    }

    /** {@code count} whole numbers of a list, or null. */
    private static int[] numbers(Object value, int count) {
        if (!(value instanceof List<?> list) || list.size() != count) return null;
        int[] out = new int[count];
        for (int index = 0; index < count; index++) {
            if (!(list.get(index) instanceof Number number)) return null;
            out[index] = number.intValue();
        }
        return out;
    }

    private BufferedImage picture(File folder, String name, String where) {
        if (name == null || !name.matches("[a-z0-9_./-]+") || name.contains("..")) {
            warn.accept(where + ": the picture " + name + " is not a name of a-z 0-9 _ - . / in ui/");
            return null;
        }
        File file = new File(folder, name);
        try {
            BufferedImage image = file.isFile() ? ImageIO.read(file) : null;
            if (image == null) warn.accept(where + ": no picture ui/" + name);
            return image;
        } catch (IOException exception) {
            warn.accept(where + ": ui/" + name + " could not be read (" + exception.getMessage() + ")");
            return null;
        }
    }

    /** Writes the pack when its bytes changed (by a temporary file and a rename), or takes it away when there is none; true when the file changed. */
    private boolean write(UiPackBuilder.Built made) throws IOException {
        File folder = new File(dataFolder, "resourcepacks");
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("could not create " + folder);
        File zip = new File(folder, PACK_FILE);
        if (made == null) return zip.isFile() && zip.delete();
        byte[] bytes = MarkGlyphs.zip(made.files());
        if (zip.isFile() && java.util.Arrays.equals(Files.readAllBytes(zip.toPath()), bytes)) return false;
        File temp = new File(folder, PACK_FILE + ".tmp");
        Files.write(temp.toPath(), bytes);
        Files.move(temp.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** The look of {@code menu} for {@code viewer}, or null when they see the menu as before. */
    private MenuSkin of(String menu, Player viewer) {
        MenuSkin skin = skins.get(menu);
        if (skin == null || built == null) return null;
        if (skin.testersOnly() && (viewer == null || !(viewer.isOp() || viewer.hasPermission(TESTER_PERMISSION)))) return null;
        return skin;
    }

    /** Whether {@code viewer} sees {@code menu} in its own look. */
    public boolean dressed(String menu, Player viewer) {
        return of(menu, viewer) != null;
    }

    /** The title of the window: the background moved to the window's top left, then the title (the skin's, else {@code own}); {@code own} as it is without a look. */
    public Component title(String menu, Player viewer, Component own) {
        MenuSkin skin = of(menu, viewer);
        if (skin == null) return own;
        UiPackBuilder.Built made = built;
        Component text = skin.title() == null ? own.colorIfAbsent(skin.titleColor()) : Component.text(skin.title(), skin.titleColor());
        Character glyph = made.backgrounds().get(menu);
        if (glyph == null) return text;
        int width = made.widths().get(menu);
        int left = made.lefts().getOrDefault(menu, 0);
        return Component.empty()
                .append(Component.text(UiPackBuilder.move(-UiPackBuilder.TITLE_X - left) + glyph + UiPackBuilder.move(UiPackBuilder.TITLE_X + left - (width + 1)))
                        .font(UiPackBuilder.FONT).color(NamedTextColor.WHITE))
                .append(text);
    }

    /**
     * Puts the pieces of the parts in their slots (each shows the name and the lore of its button's item; the button's item gives way to its own piece) and, with
     * {@code hide-frame}, takes the glass panes away from the slots outside the parts.
     */
    public void dress(String menu, Player viewer, Inventory inventory) {
        MenuSkin skin = of(menu, viewer);
        if (skin == null) return;
        Map<Integer, Integer> numbers = built.numbers().getOrDefault(menu, Map.of());
        Set<Integer> covered = new HashSet<>();
        for (MenuSkin.Part part : skin.parts()) {
            ItemStack button = inventory.getItem(part.button());
            for (int slot : part.slots()) {
                covered.add(slot);
                Integer number = numbers.get(slot);
                if (number != null && slot < inventory.getSize()) inventory.setItem(slot, piece(button, number));
            }
        }
        if (!skin.hideFrame()) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!covered.contains(slot) && item != null && (item.getType() == Material.GLASS_PANE || item.getType().name().endsWith("_STAINED_GLASS_PANE"))) {
                inventory.setItem(slot, null);
            }
        }
    }

    /** The slot of the button that {@code slot} is part of ({@code slot} itself when it is in no part, or the menu has no look for {@code viewer}). */
    public int button(String menu, Player viewer, int slot) {
        MenuSkin skin = of(menu, viewer);
        if (skin == null) return slot;
        for (MenuSkin.Part part : skin.parts()) if (part.slots().contains(slot)) return part.button();
        return slot;
    }

    /** A piece: the item of the pieces with its number, the name and the lore of {@code button} (a blank name without one). */
    static ItemStack piece(ItemStack button, int number) {
        ItemStack item = new ItemStack(UiPackBuilder.BASE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        ItemMeta from = button == null ? null : button.getItemMeta();
        if (from != null && from.hasDisplayName()) meta.displayName(from.displayName());
        else meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        if (from != null && from.hasLore()) meta.lore(from.lore());
        meta.setCustomModelData(number);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** What is read, for {@code /iruurpg pack} and the like: the menus with a look. */
    public List<String> menus() {
        return List.copyOf(skins.keySet());
    }
}
