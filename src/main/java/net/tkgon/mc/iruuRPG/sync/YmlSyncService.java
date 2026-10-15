package net.tkgon.mc.iruuRPG.sync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps plugins/iruuRPG up to date with the yml repository: every minute (off the main thread) it runs
 * {@code git pull --ff-only origin <branch>} in the data folder, and when HEAD is not the commit that was last loaded
 * it reloads the data on the main thread and writes reload-report.txt. Comparing with the last loaded commit (not just
 * before/after the pull) also catches files updated from outside the plugin (a script that pulled first), and a HEAD
 * that moved even when this pull failed. /iruurpg sync does the same at once and answers with the report.
 * A failing pull (local changes, no network, no git) is logged once, not every minute, and never stops the plugin.
 */
public final class YmlSyncService {

    /** Reloads the data and returns what it found (runs on the main thread). */
    public interface Reloader {
        ReloadReport reload(String before, String after, String trigger);
    }

    private static final int REPLY_LINES = 20;
    private static final long GIT_TIMEOUT_SECONDS = 30;

    private final JavaPlugin plugin;
    private final Reloader reloader;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile String lastWarning;
    /** The commit the loaded data comes from (set after the start-up load and after every reload). */
    private volatile String lastLoaded;

    public YmlSyncService(JavaPlugin plugin, Reloader reloader) {
        this.plugin = plugin;
        this.reloader = reloader;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("yml-sync.enabled", true);
    }

    private String branch() {
        return plugin.getConfig().getString("yml-sync.branch", "main");
    }

    public void start() {
        // the data was just loaded by onEnable: remember which commit that was
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File folder = plugin.getDataFolder();
            if (new File(folder, ".git").exists()) lastLoaded = revParse(folder);
        });
        long interval = Math.max(10, plugin.getConfig().getLong("yml-sync.interval-seconds", 60)) * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (enabled()) sync(null);
        }, interval, interval);
    }

    /** Pulls now; reloads when the commit changed. {@code requester} (may be null) gets the result in chat. */
    public void sync(CommandSender requester) {
        if (!running.compareAndSet(false, true)) {
            reply(requester, "[同期] いま同期中です。少し待ってください。", NamedTextColor.YELLOW);
            return;
        }
        String branch = branch();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean handedOver = false;
            try {
                File folder = plugin.getDataFolder();
                if (!new File(folder, ".git").exists()) {
                    warnOnce("yml-sync: " + folder + " is not a git repository; nothing to pull");
                    reply(requester, "[同期] プラグインのフォルダが git のリポジトリではありません。", NamedTextColor.RED);
                    return;
                }
                String before = revParse(folder);
                GitResult pull = git(folder, "pull", "--ff-only", "origin", branch);
                if (pull.code() != 0) {
                    warnOnce("yml-sync: git pull failed: " + firstLine(pull.output()));
                    reply(requester, "[同期] git pull に失敗: " + firstLine(pull.output()), NamedTextColor.RED);
                } else {
                    lastWarning = null; // the next failure is worth a log line again
                }
                String after = revParse(folder);
                String loaded = lastLoaded;
                // reload whenever HEAD is not what was loaded (pulled now, pulled from outside, or moved anyway)
                boolean changed = after != null && !after.equals(loaded);
                if (!changed) {
                    if (pull.code() == 0) reply(requester, "[同期] 変化なし (" + shortSha(after) + ")", NamedTextColor.GRAY);
                    return;
                }
                boolean pulledNow = before != null && !before.equals(after);
                String how = pulledNow ? "pull で更新" : "外部で更新 (プラグインの pull より前に、ファイルが更新されていた)";

                handedOver = true;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        String trigger = (requester == null ? "自動の同期" : "/iruurpg sync (" + requester.getName() + ")") + " / " + how;
                        ReloadReport report = reloader.reload(loaded, after, trigger);
                        lastLoaded = after;
                        plugin.getLogger().info("yml-sync: " + shortSha(loaded) + " -> " + shortSha(after) + " (" + (pulledNow ? "pulled" : "updated outside")
                                + (requester == null ? "" : ", by " + requester.getName()) + "), reloaded ("
                                + report.lines().get(4).replace("件数: ", "") + "), warnings " + report.warningCount());
                        if (requester != null) {
                            List<String> lines = report.lines();
                            for (String line : lines.subList(0, Math.min(REPLY_LINES, lines.size()))) {
                                requester.sendMessage(Component.text(line, NamedTextColor.WHITE));
                            }
                            if (lines.size() > REPLY_LINES) {
                                requester.sendMessage(Component.text("… 続きは plugins/iruuRPG/reload-report.txt", NamedTextColor.GRAY));
                            }
                        }
                    } finally {
                        running.set(false);
                    }
                });
            } catch (RuntimeException e) {
                warnOnce("yml-sync: " + e);
            } finally {
                if (!handedOver) running.set(false);
            }
        });
    }

    private void reply(CommandSender requester, String message, NamedTextColor color) {
        if (requester == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> requester.sendMessage(Component.text(message, color)));
    }

    private void warnOnce(String message) {
        if (message.equals(lastWarning)) return;
        lastWarning = message;
        plugin.getLogger().warning(message);
    }

    // ---- git ---------------------------------------------------------------------------------------------------------

    private record GitResult(int code, String output) {
    }

    private String revParse(File folder) {
        GitResult result = git(folder, "rev-parse", "HEAD");
        return result.code() == 0 ? result.output().trim() : null;
    }

    private GitResult git(File folder, String... args) {
        String[] command = new String[args.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = folder.getAbsolutePath();
        System.arraycopy(args, 0, command, 3, args.length);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0"); // never wait for a password
        try {
            Process process = builder.start();
            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new GitResult(-1, "timed out after " + GIT_TIMEOUT_SECONDS + "s");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new GitResult(process.exitValue(), output);
        } catch (IOException e) {
            return new GitResult(-1, "git could not run: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GitResult(-1, "interrupted");
        }
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) return "(出力なし)";
        for (String line : text.split("\n")) {
            if (!line.isBlank()) return line.trim();
        }
        return text.trim();
    }

    private static String shortSha(String sha) {
        if (sha == null) return "?";
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}
