package net.tkgon.mc.iruuRPG.markpack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Keeps the plugin's own drawn packs in the pack folder: the ground marks' font and the action bar's icons ({@code resourcepacks/iruumark.zip}, see {@link MarkGlyphs})
 * and the shaders of the screen effects ({@code resourcepacks/iruufx.zip}, see {@link ScreenShaders}; only while {@code screen-effects.enabled} is on, else the file is
 * taken away so it is not offered). The pictures are drawn when the plugin starts, off the main thread; a file is written only when its bytes changed, and then
 * {@code onChanged} runs on the main thread with the pack's name ({@code marks} / {@code fx}: to send it again to the players online). Never throws: a problem is a warning.
 */
public final class MarkPackService {

    public static final String PACK_FILE = "iruumark.zip";
    public static final String FX_FILE = "iruufx.zip";

    private MarkPackService() {
    }

    public static void rebuild(JavaPlugin plugin, Consumer<String> onChanged) {
        try {
            boolean effects = plugin.getConfig().getBoolean("screen-effects.enabled", true);
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
                try {
                    byte[] fx = effects ? ScreenShaders.pack() : null;
                    File fxFile = new File(folder, FX_FILE);
                    if (fx == null) {
                        if (fxFile.isFile() && fxFile.delete()) plugin.getLogger().info("markpack: " + FX_FILE + " taken away (screen-effects.enabled is off)");
                    } else if (write(plugin, folder, FX_FILE, fx)) {
                        plugin.getLogger().info("markpack: " + FX_FILE + " updated (" + ScreenShaders.FILES.size() + " shader files)");
                        changed(plugin, onChanged, "fx");
                    }
                } catch (Exception | LinkageError exception) {
                    plugin.getLogger().warning("markpack: the pack of the screen effects could not be made: " + exception);
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
