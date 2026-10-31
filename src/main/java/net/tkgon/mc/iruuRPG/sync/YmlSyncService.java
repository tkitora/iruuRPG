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
 * The pull is done in two steps, {@code git fetch origin <branch>} then {@code git merge --ff-only origin/<branch>},
 * so a failure says which step failed. A failure (local changes, no network, no git) is logged with the last lines of
 * git's output; the same failure is logged again at most every ten minutes, and it never stops the plugin.
 */
public final class YmlSyncService {

    /** Reloads the data and returns what it found (runs on the main thread). */
    public interface Reloader {
        ReloadReport reload(String before, String after, String trigger);
    }

    private static final int REPLY_LINES = 20;
    private static final long GIT_TIMEOUT_SECONDS = 60;
    /** How many of the last lines of git's output a failure shows in the log. */
    private static final int ERROR_LINES = 5;
    /** The same failure is logged again after this long (it was logged only once before, which hid what went on). */
    private static final long REPEAT_WARNING_MILLIS = 10 * 60 * 1000L;

    private final JavaPlugin plugin;
    private final Reloader reloader;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile String lastWarning;
    private volatile long lastWarningAt;
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
                GitResult pull = pull(folder, branch);
                if (pull.code() != 0) {
                    // a new file in the repository clashes with an untracked file of the same name (for example a
                    // file the jar put there): the repository wins; the local file is kept as <name>.bak-<millis>
                    List<String> blocking = untrackedInTheWay(pull.output());
                    if (!blocking.isEmpty() && moveAside(folder, blocking)) {
                        plugin.getLogger().info("yml-sync: moved aside untracked files in the way: " + String.join(", ", blocking));
                        pull = pull(folder, branch);
                    }
                }
                if (pull.code() != 0) {
                    warnOnce("yml-sync: " + pull.step() + " failed (exit " + pull.code() + "): " + lastLines(pull.output(), ERROR_LINES));
                    reply(requester, "[同期] " + pull.step() + " に失敗: " + lastLines(pull.output(), ERROR_LINES), NamedTextColor.RED);
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

    /** The paths git lists under "untracked working tree files would be overwritten by merge:". */
    static List<String> untrackedInTheWay(String output) {
        List<String> paths = new java.util.ArrayList<>();
        if (output == null || !output.contains("untracked working tree files would be overwritten")) return paths;
        boolean inList = false;
        for (String line : output.split("\n")) {
            if (line.contains("untracked working tree files would be overwritten")) {
                inList = true;
                continue;
            }
            if (!inList) continue;
            if (!line.startsWith("\t") && !line.startsWith("    ")) {
                if (!paths.isEmpty()) break; // "Please move or remove them..." ends the list
                continue;
            }
            String path = line.trim();
            if (!path.isEmpty()) paths.add(path);
        }
        return paths;
    }

    /** Renames each file to <name>.bak-<millis>; only files inside the data folder. False if any could not be moved. */
    private boolean moveAside(File folder, List<String> paths) {
        long stamp = System.currentTimeMillis();
        try {
            java.nio.file.Path root = folder.getCanonicalFile().toPath();
            for (String path : paths) {
                File file = new File(folder, path).getCanonicalFile();
                if (!file.toPath().startsWith(root) || !file.isFile()) {
                    warnOnce("yml-sync: refusing to move " + path);
                    return false;
                }
                File backup = new File(file.getParentFile(), file.getName() + ".bak-" + stamp);
                if (!file.renameTo(backup)) {
                    warnOnce("yml-sync: could not move aside " + path);
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            warnOnce("yml-sync: could not move aside: " + e.getMessage());
            return false;
        }
    }

    private void reply(CommandSender requester, String message, NamedTextColor color) {
        if (requester == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> requester.sendMessage(Component.text(message, color)));
    }

    /** Logs a warning; the same one again only after {@link #REPEAT_WARNING_MILLIS}. */
    private void warnOnce(String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastWarning) && now - lastWarningAt < REPEAT_WARNING_MILLIS) return;
        lastWarning = message;
        lastWarningAt = now;
        plugin.getLogger().warning(message);
    }

    /** The pull in two steps (fetch, then a fast-forward merge of what was fetched): the result says which step it stopped at. */
    private GitResult pull(File folder, String branch) {
        GitResult fetch = git(folder, "fetch", "origin", branch);
        if (fetch.code() != 0) return fetch.as("git fetch");
        GitResult merge = git(folder, "merge", "--ff-only", "origin/" + branch);
        return merge.as("git merge --ff-only");
    }

    // ---- git ---------------------------------------------------------------------------------------------------------

    private record GitResult(int code, String output, String step) {
        GitResult(int code, String output) {
            this(code, output, "git");
        }

        GitResult as(String step) {
            return new GitResult(code, output, step);
        }
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
            // read while it runs (a full pipe would make git wait for ever)
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(buffer);
                } catch (IOException ignored) {
                    // the process ended or was killed
                }
            }, "iruuRPG-yml-sync-git");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reader.join(1000L);
                return new GitResult(-1, buffer.toString(StandardCharsets.UTF_8) + "\ntimed out after " + GIT_TIMEOUT_SECONDS + "s");
            }
            reader.join(5000L);
            return new GitResult(process.exitValue(), buffer.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new GitResult(-1, "git could not run: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GitResult(-1, "interrupted");
        }
    }

    /** The last {@code count} non-blank lines, joined with " | " (git's error is at the end, after "From ..."). */
    static String lastLines(String text, int count) {
        if (text == null || text.isBlank()) return "(出力なし)";
        List<String> lines = new java.util.ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.isBlank()) lines.add(line.trim());
        }
        return String.join(" | ", lines.subList(Math.max(0, lines.size() - count), lines.size()));
    }

    private static String shortSha(String sha) {
        if (sha == null) return "?";
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}
