package net.tkgon.mc.iruuRPG.markpack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Keeps the pack of the ground marks' font ({@code resourcepacks/iruumark.zip}, see {@link MarkGlyphs}; with the shaders of the screen effects
 * unless {@code screen-effects.enabled} is false) in the pack folder. The pictures are drawn
 * when the plugin starts, off the main thread; the file is written only when its bytes changed, and then {@code onChanged} runs on the main thread
 * (to send the pack again to the players online). Never throws: a problem is a warning.
 */
public final class MarkPackService {

    public static final String PACK_FILE = "iruumark.zip";

    private MarkPackService() {
    }

    public static void rebuild(JavaPlugin plugin, Runnable onChanged) {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    byte[] bytes = MarkGlyphs.pack(plugin.getConfig().getBoolean("screen-effects.enabled", true));
                    File folder = new File(plugin.getDataFolder(), "resourcepacks");
                    if (!folder.exists() && !folder.mkdirs()) {
                        plugin.getLogger().warning("markpack: could not create " + folder);
                        return;
                    }
                    File zip = new File(folder, PACK_FILE);
                    if (zip.isFile() && java.util.Arrays.equals(Files.readAllBytes(zip.toPath()), bytes)) return;
                    // (written by a temporary file and renamed, so a client never fetches half a pack)
                    File temp = new File(folder, PACK_FILE + ".tmp");
                    Files.write(temp.toPath(), bytes);
                    Files.move(temp.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("markpack: " + PACK_FILE + " updated (" + MarkGlyphs.all().size() + " glyphs, " + bytes.length + " bytes)");
                    if (onChanged != null && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, onChanged);
                } catch (Exception | LinkageError exception) {
                    plugin.getLogger().warning("markpack: the pack of the ground marks could not be made: " + exception);
                }
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("markpack: not started: " + exception);
        }
    }
}
