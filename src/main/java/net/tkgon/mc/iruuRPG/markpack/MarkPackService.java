package net.tkgon.mc.iruuRPG.markpack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Keeps the plugin's own drawn pack in the pack folder: the ground marks' font and the action bar's icons ({@code resourcepacks/iruumark.zip}, see {@link MarkGlyphs}).
 * The pictures are drawn when the plugin starts, off the main thread; the file is written only when its bytes changed, and then {@code onChanged} runs on the main
 * thread with the pack's name ({@code marks}: to send it again to the players online). Never throws: a problem is a warning.
 * <p>
 * The pack of the screen effects' shaders ({@code iruufx.zip}) is gone with the screen effects (t156: they depended on the client); a file of it left from before is
 * taken away, so nothing is left behind.
 */
public final class MarkPackService {

    public static final String PACK_FILE = "iruumark.zip";
    /** The pack of the screen effects that were taken out (t156): taken away when it is still there. */
    static final String OLD_FX_FILE = "iruufx.zip";

    private MarkPackService() {
    }

    public static void rebuild(JavaPlugin plugin, Consumer<String> onChanged) {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                File folder = new File(plugin.getDataFolder(), "resourcepacks");
                if (!folder.exists() && !folder.mkdirs()) {
                    plugin.getLogger().warning("markpack: could not create " + folder);
                    return;
                }
                try {
                    if (write(plugin, folder, PACK_FILE, MarkGlyphs.pack())) {
                        plugin.getLogger().info("markpack: " + PACK_FILE + " updated (" + MarkGlyphs.all().size() + " glyphs)");
                        changed(plugin, onChanged, "marks");
                    }
                } catch (Exception | LinkageError exception) {
                    plugin.getLogger().warning("markpack: the pack of the ground marks could not be made: " + exception);
                }
                File oldFx = new File(folder, OLD_FX_FILE);
                if (oldFx.isFile()) {
                    if (oldFx.delete()) plugin.getLogger().info("markpack: " + OLD_FX_FILE + " taken away (the screen effects are no longer in the plugin)");
                    else plugin.getLogger().warning("markpack: " + OLD_FX_FILE + " (the pack of the screen effects, no longer used) could not be taken away");
                }
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("markpack: not started: " + exception);
        }
    }

    /** Writes {@code bytes} as {@code name} when they are not what is there (by a temporary file and a rename, so a client never fetches half a pack). */
    private static boolean write(JavaPlugin plugin, File folder, String name, byte[] bytes) throws java.io.IOException {
        File zip = new File(folder, name);
        if (zip.isFile() && java.util.Arrays.equals(Files.readAllBytes(zip.toPath()), bytes)) return false;
        File temp = new File(folder, name + ".tmp");
        Files.write(temp.toPath(), bytes);
        Files.move(temp.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private static void changed(JavaPlugin plugin, Consumer<String> onChanged, String pack) {
        if (onChanged != null && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> onChanged.accept(pack));
    }
}
