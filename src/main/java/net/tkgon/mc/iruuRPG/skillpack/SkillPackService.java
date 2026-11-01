package net.tkgon.mc.iruuRPG.skillpack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Keeps the pack of the skill icons' font ({@code resourcepacks/iruuskill.zip}, see {@link SkillGlyphs}) in the pack folder. A picture in
 * {@code skill-icons/<name>.png} takes the stand-in's place. Drawn off the main thread; the file is written only when its bytes changed, and then
 * {@code onChanged} runs on the main thread (to send the pack again to the players online). Never throws: a problem is a warning.
 */
public final class SkillPackService {

    public static final String PACK_FILE = "iruuskill.zip";

    private SkillPackService() {
    }

    public static void rebuild(JavaPlugin plugin, Runnable onChanged) {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    File own = new File(plugin.getDataFolder(), "skill-icons");
                    byte[] bytes = SkillGlyphs.pack(name -> {
                        File file = new File(own, name + ".png");
                        try {
                            return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
                        } catch (IOException exception) {
                            return null;
                        }
                    });
                    File folder = new File(plugin.getDataFolder(), "resourcepacks");
                    if (!folder.exists() && !folder.mkdirs()) {
                        plugin.getLogger().warning("skillpack: could not create " + folder);
                        return;
                    }
                    File zip = new File(folder, PACK_FILE);
                    if (zip.isFile() && java.util.Arrays.equals(Files.readAllBytes(zip.toPath()), bytes)) return;
                    // (written by a temporary file and renamed, so a client never fetches half a pack)
                    File temp = new File(folder, PACK_FILE + ".tmp");
                    Files.write(temp.toPath(), bytes);
                    Files.move(temp.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("skillpack: " + PACK_FILE + " updated (" + SkillGlyphs.all().size() + " icons, " + bytes.length + " bytes)");
                    if (onChanged != null && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, onChanged);
                } catch (Exception | LinkageError exception) {
                    plugin.getLogger().warning("skillpack: the pack of the skill icons could not be made: " + exception);
                }
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("skillpack: not started: " + exception);
        }
    }
}
