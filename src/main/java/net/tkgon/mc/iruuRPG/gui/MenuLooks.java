package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * How the entries of the travel and dungeon menus look (t220: edited on the tool site, kept in the yml repository): {@code menus/travel.yml}
 * and {@code menus/dungeons.yml} of the data folder, read with the rest of the data (start, reload, the yml sync). Where a point or a dungeon is,
 * and what a dungeon is, stay where they were ({@code travel/waypoints.yml} set in the game, {@code dungeons/*.yml}); only their look is here.
 * <pre>
 * title: ファストトラベル          # the menu's title (dungeons.yml: ダンジョン)
 * entries:                        # by the point's (the dungeon's) id
 *   atras:
 *     order: 1                    # the place in the menu (smaller first; those without one after them, as before)
 *     name: アトラス村            # the name shown (else the point's own)
 *     color: aqua                 # a colour of the name: a name of the game's (aqua, gold, ...) or #RRGGBB
 *     icon: ENDER_EYE             # the item shown
 *     lore: [港から北へ, ...]     # lines under the name (the dungeon's: in place of its description)
 *     recommended-level: 10       # (dungeons) the level shown as 推奨 (entering still needs its min-level)
 * unknown: {name: "???", color: dark_gray, icon: GRAY_DYE}   # (travel) a point not found yet
 * </pre>
 * A value that cannot be read (an unknown item or colour) is left out with a warning; nothing here can stop the plugin.
 */
public final class MenuLooks {

    /** The look of one entry (any part null: as before). */
    public record Look(Integer order, String name, TextColor color, Material icon, List<String> lore, Integer recommendedLevel) {
        static final Look NONE = new Look(null, null, null, null, null, null);
    }

    private final File folder;
    private final Consumer<String> warn;
    private String travelTitle;
    private String dungeonTitle;
    private Map<String, Look> travel = Map.of();
    private Map<String, Look> dungeons = Map.of();
    private Look travelUnknown = Look.NONE;

    public MenuLooks(File dataFolder, Consumer<String> warn) {
        this.folder = new File(dataFolder, "menus");
        this.warn = warn;
    }

    /** Reads the files again (none: the menus look as they always did). Never throws. */
    public void reload() {
        try {
            YamlConfiguration travelFile = load("travel.yml");
            YamlConfiguration dungeonFile = load("dungeons.yml");
            travelTitle = text(travelFile, "title");
            dungeonTitle = text(dungeonFile, "title");
            travel = entries(travelFile, "travel.yml");
            dungeons = entries(dungeonFile, "dungeons.yml");
            ConfigurationSection unknown = travelFile == null ? null : travelFile.getConfigurationSection("unknown");
            travelUnknown = unknown == null ? Look.NONE : look(unknown, "travel.yml unknown");
        } catch (RuntimeException e) {
            warn.accept("menus: could not be read (" + e.getMessage() + "); the menus look as before");
            travel = Map.of();
            dungeons = Map.of();
            travelUnknown = Look.NONE;
        }
    }

    public String travelTitle(String fallback) {
        return travelTitle == null ? fallback : travelTitle;
    }

    public String dungeonTitle(String fallback) {
        return dungeonTitle == null ? fallback : dungeonTitle;
    }

    public Look travel(String id) {
        return travel.getOrDefault(id, Look.NONE);
    }

    public Look dungeon(String id) {
        return dungeons.getOrDefault(id, Look.NONE);
    }

    /** The look of a travel point not found yet. */
    public Look travelUnknown() {
        return travelUnknown;
    }

    /** {@code list} in the menu's order: those with an order first (smaller first), then the rest as they were. */
    public <T> List<T> travelOrder(List<T> list, Function<T, String> id) {
        return ordered(list, item -> travel(id.apply(item)).order());
    }

    public <T> List<T> dungeonOrder(List<T> list, Function<T, String> id) {
        return ordered(list, item -> dungeon(id.apply(item)).order());
    }

    /** Sorted by the order {@code orderOf} gives (null: after those with one), keeping the first order otherwise. Pure, for the tests. */
    static <T> List<T> ordered(List<T> list, Function<T, Integer> orderOf) {
        List<T> sorted = new ArrayList<>(list);
        Map<T, Integer> position = new HashMap<>();
        for (int index = 0; index < list.size(); index++) position.putIfAbsent(list.get(index), index);
        sorted.sort(Comparator.<T, Integer>comparing(item -> orderOf.apply(item) == null ? Integer.MAX_VALUE : orderOf.apply(item))
                .thenComparing(position::get));
        return sorted;
    }

    // ---- reading ----------------------------------------------------------------------------------------------------------

    private YamlConfiguration load(String name) {
        File file = new File(folder, name);
        if (!file.isFile()) return null;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            warn.accept("menus/" + name + ": not yml (" + firstLine(e.getMessage()) + "); the menu looks as before");
            return null;
        }
        return yaml;
    }

    private Map<String, Look> entries(YamlConfiguration yaml, String where) {
        ConfigurationSection entries = yaml == null ? null : yaml.getConfigurationSection("entries");
        if (entries == null) return Map.of();
        Map<String, Look> looks = new HashMap<>();
        for (String id : entries.getKeys(false)) {
            ConfigurationSection section = entries.getConfigurationSection(id);
            if (section != null) looks.put(id, look(section, where + " " + id));
        }
        return Map.copyOf(looks);
    }

    private Look look(ConfigurationSection section, String where) {
        Integer order = section.isInt("order") ? Integer.valueOf(section.getInt("order")) : null;
        if (section.contains("order") && order == null) warn.accept("menus/" + where + ": order must be a whole number; left out");
        String name = text(section, "name");
        TextColor color = null;
        if (section.contains("color")) {
            color = color(section.getString("color"));
            if (color == null) warn.accept("menus/" + where + ": color '" + section.getString("color") + "' is not a colour (aqua, gold, ... or #RRGGBB); left out");
        }
        Material icon = null;
        if (section.contains("icon")) {
            icon = icon(section.getString("icon"));
            if (icon == null) warn.accept("menus/" + where + ": icon '" + section.getString("icon") + "' is not an item; left out");
        }
        List<String> lore = section.isList("lore") ? section.getStringList("lore")
                : section.isString("lore") ? List.of(section.getString("lore", "").split("\n")) : null;
        Integer level = section.isInt("recommended-level") ? Integer.valueOf(Math.max(1, section.getInt("recommended-level"))) : null;
        if (section.contains("recommended-level") && level == null) warn.accept("menus/" + where + ": recommended-level must be a whole number; left out");
        return new Look(order, name, color, icon, lore == null ? null : List.copyOf(lore), level);
    }

    private static String text(ConfigurationSection section, String key) {
        if (section == null || !section.isString(key)) return null;
        String value = section.getString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    /** A colour by the game's name (aqua, dark_gray, ...) or #RRGGBB, else null. Pure, for the tests. */
    static TextColor color(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("#")) return v.matches("#[0-9a-f]{6}") ? TextColor.fromHexString(v) : null;
        return NamedTextColor.NAMES.value(v);
    }

    /** An item by its name (ENDER_EYE, minecraft:ender_eye), an item that can be shown, else null. Pure, for the tests. */
    static Material icon(String value) {
        if (value == null) return null;
        Material material = Material.matchMaterial(value.trim());
        return material != null && material.isItem() && !material.isAir() ? material : null;
    }

    private static String firstLine(String message) {
        if (message == null) return "";
        int at = message.indexOf('\n');
        return at < 0 ? message : message.substring(0, at);
    }
}
