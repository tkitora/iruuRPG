package net.tkgon.mc.iruuRPG.itempack;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Keeps the pack of the items' looks ({@code resourcepacks/iruuitem.zip}) up to date with the items: after the items are read, {@link #rebuild}
 * makes the pack from every item that has a {@code texture:}, gives each its custom model data (kept in {@code resourcepacks/item-cmd-index.yml}),
 * and writes the zip only when its bytes changed. {@link #numberOf} is what the item factory puts on an item. It never throws: a problem is a warning,
 * and an item without a number simply looks like its base item.
 */
public final class ItemPackService {

    public static final String PACK_FILE = "iruuitem.zip";

    /** Items the game draws with a model of its own, not a flat picture under {@code item/}. */
    private static final Set<String> NOT_FLAT = Set.of("shield", "trident", "spyglass", "bundle", "elytra");

    private final JavaPlugin plugin;
    private final Supplier<Collection<RpgItemDefinition>> items;
    private volatile Map<String, Integer> numbers = Map.of();

    public ItemPackService(JavaPlugin plugin, Supplier<Collection<RpgItemDefinition>> items) {
        this.plugin = plugin;
        this.items = items;
    }

    /** The custom model data of the item, or null when it looks like its base item. */
    public Integer numberOf(RpgItemDefinition definition) {
        return definition == null ? null : numbers.get(definition.id());
    }

    private File folder() {
        File folder = new File(plugin.getDataFolder(), "resourcepacks");
        if (!folder.exists() && !folder.mkdirs()) plugin.getLogger().warning("Could not create " + folder);
        return folder;
    }

    /** Makes the pack from the items now. Safe to call again (after every reload of the items). Returns true when the pack file was written (changed). */
    public boolean rebuild() {
        try {
            List<ItemPackBuilder.Look> looks = looks(items.get());
            if (looks.isEmpty()) {
                numbers = Map.of();
                return false;
            }
            ItemCmdIndex index = new ItemCmdIndex(new File(folder(), "item-cmd-index.yml"));
            index.load();
            ItemPackBuilder.Result result = ItemPackBuilder.build(looks, index, this::template, ItemPackService::flat, this::tinted);
            index.save();
            for (String warning : result.warnings()) plugin.getLogger().warning("itempack: " + warning);
            numbers = Map.copyOf(result.numbers());
            File zip = new File(folder(), PACK_FILE);
            if (zip.isFile() && java.util.Arrays.equals(Files.readAllBytes(zip.toPath()), result.zip())) return false;
            // (written by a temporary file and renamed, so a client never fetches half a pack)
            File temp = new File(zip.getParentFile(), zip.getName() + ".tmp");
            Files.write(temp.toPath(), result.zip());
            Files.move(temp.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("itempack: " + PACK_FILE + " updated (" + result.numbers().size() + " item(s) with another look)");
            return true;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("itempack: the pack of the items' looks could not be made: " + exception);
            return false;
        }
    }

    /** The items that wear a look: their base material (the item's material) and the vanilla item they look like. */
    static List<ItemPackBuilder.Look> looks(Collection<RpgItemDefinition> definitions) {
        List<ItemPackBuilder.Look> looks = new ArrayList<>();
        for (RpgItemDefinition definition : definitions) {
            if (!definition.visualOptions().hasTexture()) continue;
            looks.add(new ItemPackBuilder.Look(definition.id(), definition.material().name(), definition.visualOptions().texture()));
        }
        return looks;
    }

    /** Whether a vanilla item is a flat picture under {@code item/<name>.png} (not a block, and not an item the game draws itself). */
    static boolean flat(String name) {
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material != null && material.isItem() && !material.isBlock() && !NOT_FLAT.contains(name);
    }

    /** The colored picture the plugin carries for a vanilla item the game colors itself ({@code itempack/tinted/<name>.png}), or null. */
    private byte[] tinted(String name) {
        if (!name.matches("[a-z0-9_]+")) return null;
        try (InputStream in = plugin.getResource("itempack/tinted/" + name + ".png")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException exception) {
            return null;
        }
    }

    private String template(String base) {
        try (InputStream in = plugin.getResource("itempack/base/" + base + ".json")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }
}
