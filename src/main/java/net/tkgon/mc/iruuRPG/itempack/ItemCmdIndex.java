package net.tkgon.mc.iruuRPG.itempack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The custom model data number of every item that is drawn with another item's texture. The numbers are kept in a file
 * ({@code id: number}, one per line) so that an item keeps its number when items are added or removed, and the number an item
 * had in the items already in the players' hands stays the right one. A number is never given to a second item.
 */
public final class ItemCmdIndex {

    /** The first number (the model feature's parts are above 8100000; the items stay below). */
    public static final int FIRST = 1_000_000;

    private final File file;
    private final Map<String, Integer> numbers = new TreeMap<>();
    private int next = FIRST;
    private boolean dirty;

    public ItemCmdIndex(File file) {
        this.file = file;
    }

    /** Reads the file (a missing file is an empty index). Lines that are not {@code id: number} are skipped. */
    public void load() throws IOException {
        numbers.clear();
        next = FIRST;
        if (file != null && file.isFile()) {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#")) continue;
                int colon = text.lastIndexOf(':');
                if (colon <= 0) continue;
                String id = unquote(text.substring(0, colon).strip());
                try {
                    int number = Integer.parseInt(text.substring(colon + 1).strip());
                    if (!id.isEmpty() && number >= FIRST) numbers.put(id, number);
                } catch (NumberFormatException ignored) {
                    // not a number: skipped
                }
            }
        }
        for (int number : numbers.values()) next = Math.max(next, number + 1);
        dirty = false;
    }

    /** The number of the item, or null when it has none yet. */
    public Integer get(String itemId) {
        return numbers.get(itemId);
    }

    /** The number of the item; a new one (the next free) when it has none. */
    public int assign(String itemId) {
        Integer known = numbers.get(itemId);
        if (known != null) return known;
        int number = next++;
        numbers.put(itemId, number);
        dirty = true;
        return number;
    }

    public Map<String, Integer> all() {
        return java.util.Collections.unmodifiableMap(numbers);
    }

    /** Writes the file when something was assigned since it was read. */
    public void save() throws IOException {
        if (!dirty || file == null) return;
        List<String> lines = new ArrayList<>();
        lines.add("# custom model data of the items that wear another item's texture (made by the plugin; never reuse or change a number)");
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) lines.add(quote(entry.getKey()) + ": " + entry.getValue());
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("could not create " + parent);
        File temp = new File(file.getPath() + ".tmp");
        Files.write(temp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        dirty = false;
    }

    private static String quote(String id) {
        boolean plain = !id.isEmpty() && id.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.');
        return plain ? id : "\"" + id.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return text;
    }
}
