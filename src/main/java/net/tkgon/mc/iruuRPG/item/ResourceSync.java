package net.tkgon.mc.iruuRPG.item;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Keeps plugin-owned data files (craft items, recipes) up to date on a server that already has older copies:
 * each file has a top-level {@code version:}; when the jar's file has a higher one, the old copy is kept as a
 * backup and replaced. A missing file is simply created. A file tracked by the yml repository (the data folder is a
 * git working copy) is never replaced: the repository is the source of truth for it.
 */
public final class ResourceSync {

    private ResourceSync() {
    }

    public static void sync(JavaPlugin plugin, String path) {
        if (plugin.getResource(path) == null) {
            plugin.getLogger().warning("Missing bundled resource: " + path);
            return;
        }

        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
            return;
        }

        int current = YamlConfiguration.loadConfiguration(file).getInt("version", 0);
        int bundled = bundledVersion(plugin, path);
        if (current >= bundled) return;
        if (trackedByGit(plugin, path)) {
            // the yml repository owns this file now (it is synced by git): never overwrite it from the jar
            plugin.getLogger().info("Not updating " + path + " (version " + current + " < " + bundled + "): the yml repository manages it");
            return;
        }

        File backup = new File(file.getParentFile(), file.getName() + ".bak-" + System.currentTimeMillis());
        if (!file.renameTo(backup)) {
            plugin.getLogger().warning("Could not back up " + path + "; keeping the old copy");
            return;
        }
        plugin.saveResource(path, false);
        plugin.getLogger().info("Updated " + path + " to version " + bundled + " (old copy: " + backup.getName() + ")");
    }

    /**
     * True when the data folder is a git working copy and {@code path} is tracked there (git ls-files --error-unmatch).
     * No git, not a repository, or any failure: false (the jar keeps updating the file as before).
     */
    private static boolean trackedByGit(JavaPlugin plugin, String path) {
        File folder = plugin.getDataFolder();
        if (!new File(folder, ".git").exists()) return false;
        try {
            ProcessBuilder builder = new ProcessBuilder("git", "-C", folder.getAbsolutePath(), "ls-files", "--error-unmatch", "--", path)
                    .redirectErrorStream(true);
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process process = builder.start();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            process.getInputStream().readAllBytes();
            return process.exitValue() == 0;
        } catch (java.io.IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static int bundledVersion(JavaPlugin plugin, String path) {
        try (Reader reader = new InputStreamReader(plugin.getResource(path), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader).getInt("version", 0);
        } catch (Exception e) {
            return 0;
        }
    }
}
