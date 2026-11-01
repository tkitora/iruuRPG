package net.tkgon.mc.iruuRPG.pack;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends the server's resource packs to the players that join: the server's own pack ({@code server.zip}, bundled in the jar: see src/main/resourcepack) and the pack of the model
 * feature ({@code iruumodel.zip}, made by {@code /iruumodel build}). Both are plain zip files in {@code plugins/iruuRPG/resourcepacks/}; a pack is sent when it is switched on in
 * {@code resourcepack.packs.<name>} and its file is there. They are separate packs on purpose (the client keeps both, and either can be taken away without the other).
 *
 * <p>Where the files are served from is {@code resourcepack.base-url} (any web server that serves the folder), or the small built-in server of {@link PackHost} when
 * {@code resourcepack.host.enabled} is on. With neither, nothing is sent. Self-contained: it reads only its own part of the config and the folder above.
 */
public final class ResourcePackService implements Listener {

    /** The packs the jar carries: copied to the folder at start, replacing the old copy (they are the plugin's, not hand-edited). */
    private static final List<String> BUNDLED = List.of("server.zip");

    private final JavaPlugin plugin;
    private PackHost host;
    private final Map<String, String> hashes = new LinkedHashMap<>();
    private final Map<String, Long> hashedAt = new LinkedHashMap<>();

    public ResourcePackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the service: puts the bundled packs in the folder, starts the host when it is asked for, listens for joins. Never throws. */
    public static ResourcePackService start(JavaPlugin plugin) {
        try {
            ResourcePackService service = new ResourcePackService(plugin);
            service.extractBundled();
            plugin.getServer().getPluginManager().registerEvents(service, plugin);
            if (plugin.getConfig().getBoolean("resourcepack.host.enabled", false)) {
                service.host = PackHost.start(plugin, service.folder(), service::servable);
            }
            return service;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Resource pack service not started: " + exception);
            return null;
        }
    }

    public void stop() {
        if (host != null) host.stop();
        host = null;
    }

    File folder() {
        File folder = new File(plugin.getDataFolder(), "resourcepacks");
        if (!folder.exists() && !folder.mkdirs()) plugin.getLogger().warning("Could not create " + folder);
        return folder;
    }

    private void extractBundled() {
        for (String name : BUNDLED) {
            try (InputStream in = plugin.getResource("resourcepacks/" + name)) {
                if (in == null) continue;
                byte[] bytes = in.readAllBytes();
                File target = new File(folder(), name);
                if (target.isFile() && java.util.Arrays.equals(Files.readAllBytes(target.toPath()), bytes)) continue;
                Files.write(target.toPath(), bytes);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not put " + name + " in the resource pack folder: " + exception);
            }
        }
    }

    // ---- which packs, and where they are --------------------------------------------------------------------------------------

    /** A pack as the config describes it. */
    record Pack(String name, String file, boolean required) {
    }

    /**
     * The packs the plugin makes itself, with their file names: sent when the file is there, even if the server's config.yml does not list them
     * (the deploy only adds whole top-level sections, so a pack added to {@code resourcepack.packs} later is never written into an existing file).
     * An entry in the config ({@code enabled: false}, another file, {@code required}) still wins.
     */
    private static final Map<String, String> BUILT_IN = new LinkedHashMap<>(Map.of("server", "server.zip"));

    static {
        BUILT_IN.put("models", "iruumodel.zip");
        BUILT_IN.put("items", "iruuitem.zip");
        BUILT_IN.put("marks", "iruumark.zip");
        // the shaders of the screen effects, apart from the marks' fonts (a client that cannot compile them drops only this one)
        BUILT_IN.put("fx", "iruufx.zip");
    }

    private List<Pack> packs() {
        return resolve(plugin.getConfig().getConfigurationSection("resourcepack.packs"));
    }

    /** The packs to offer: the ones the config names and the built-in ones (see {@link #BUILT_IN}); {@code section} may be null. */
    static List<Pack> resolve(org.bukkit.configuration.ConfigurationSection section) {
        List<Pack> list = new java.util.ArrayList<>();
        java.util.Set<String> named = new java.util.LinkedHashSet<>(section == null ? java.util.Set.of() : section.getKeys(false));
        for (String name : BUILT_IN.keySet()) named.add(name);
        for (String name : named) {
            var pack = section == null ? null : section.getConfigurationSection(name);
            if (pack != null && !pack.getBoolean("enabled", true)) continue;
            String file = pack == null ? BUILT_IN.get(name) : pack.getString("file", BUILT_IN.getOrDefault(name, name + ".zip"));
            if (file == null || file.contains("/") || file.contains("\\") || file.contains("..")) continue;
            list.add(new Pack(name, file, pack != null && pack.getBoolean("required", false)));
        }
        return list;
    }

    /** Whether a file of the folder may be served by the built-in host: only the files of the packs that are switched on. */
    boolean servable(String file) {
        for (Pack pack : packs()) if (pack.file().equals(file)) return true;
        return false;
    }

    private String baseUrl() {
        String base = plugin.getConfig().getString("resourcepack.base-url", "");
        if ((base == null || base.isBlank()) && host != null) base = host.publicUrl();
        if (base == null || base.isBlank()) return null;
        return base.endsWith("/") ? base : base + "/";
    }

    /** The SHA-1 of the file (40 hex digits), worked out again only when the file changed. */
    private String hash(File file) {
        long modified = file.lastModified();
        String known = hashes.get(file.getName());
        if (known != null && hashedAt.getOrDefault(file.getName(), -1L) == modified) return known;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(Files.readAllBytes(file.toPath()))) hex.append(String.format("%02x", b));
            hashes.put(file.getName(), hex.toString());
            hashedAt.put(file.getName(), modified);
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            plugin.getLogger().warning("Could not read " + file + ": " + exception);
            return null;
        }
    }

    // ---- sending ---------------------------------------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // (a moment after the join: the client is ready for the offer then)
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) send(player);
        }, 20L);
    }

    /**
     * The id the pack {@code name} is offered with (the client tells its status by it, see PlayerResourcePackStatusEvent#getID). One id per
     * pack: a changed pack replaces the old one on the client instead of adding another.
     */
    public static UUID packId(String name) {
        return UUID.nameUUIDFromBytes(("iruurpg:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Offers the player every pack that is switched on and there; returns how many were offered (0 with the service off or no address). */
    public int send(Player player) {
        if (!plugin.getConfig().getBoolean("resourcepack.enabled", false)) return 0;
        String base = baseUrl();
        if (base == null) return 0;
        String prompt = plugin.getConfig().getString("resourcepack.prompt", "iruuRPG のリソースパック");
        int sent = 0;
        for (Pack pack : packs()) {
            File file = new File(folder(), pack.file());
            if (!file.isFile()) continue;
            String hash = hash(file);
            if (hash == null) continue;
            byte[] bytes = new byte[20];
            for (int index = 0; index < 20; index++) bytes[index] = (byte) Integer.parseInt(hash.substring(index * 2, index * 2 + 2), 16);
            player.addResourcePack(packId(pack.name()), base + pack.file() + "?h=" + hash.substring(0, 8), bytes, prompt, pack.required());
            sent++;
        }
        return sent;
    }

    /**
     * Offers every player online the pack {@code name} again (when its file changed while they play: the pack of the items is made again after the
     * yml changes, and a player who joined earlier has the old one). The client replaces the old one (one id per pack). Returns how many players.
     */
    public int resend(String name) {
        if (!plugin.getConfig().getBoolean("resourcepack.enabled", false)) return 0;
        String base = baseUrl();
        if (base == null) return 0;
        Pack pack = null;
        for (Pack candidate : packs()) if (candidate.name().equals(name)) pack = candidate;
        if (pack == null) return 0;
        File file = new File(folder(), pack.file());
        if (!file.isFile()) return 0;
        String hash = hash(file);
        if (hash == null) return 0;
        byte[] bytes = new byte[20];
        for (int index = 0; index < 20; index++) bytes[index] = (byte) Integer.parseInt(hash.substring(index * 2, index * 2 + 2), 16);
        UUID id = packId(pack.name());
        String prompt = plugin.getConfig().getString("resourcepack.prompt", "iruuRPG のリソースパック");
        int sent = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.addResourcePack(id, base + pack.file() + "?h=" + hash.substring(0, 8), bytes, prompt, pack.required());
            sent++;
        }
        return sent;
    }

    /**
     * What each client did with each pack, in the log: the end of it (loaded) as info, a pack it did not take (declined, not downloaded, a bad
     * address, failed to load, thrown away) as a warning, with the player, the pack, the status and the client's brand. (The steps between, accepted
     * and downloaded, are not written.)
     */
    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        if (status == PlayerResourcePackStatusEvent.Status.ACCEPTED || status == PlayerResourcePackStatusEvent.Status.DOWNLOADED) return;
        String name = "?";
        for (Pack pack : packs()) if (packId(pack.name()).equals(event.getID())) name = pack.name() + " (" + pack.file() + ")";
        Player player = event.getPlayer();
        String line = "pack " + name + ": " + player.getName() + " " + status.name() + " (client " + player.getClientBrandName() + ")";
        if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) plugin.getLogger().info(line);
        else plugin.getLogger().warning(line + (name.startsWith("fx ") && status == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD
                ? ": the shaders may not compile on that client (screen-effects.enabled: false takes them away)" : ""));
    }

    /** For /iruurpg pack: where each pack is and its hash. */
    public void describe(CommandSender sender) {
        sender.sendMessage("[リソースパック] " + (plugin.getConfig().getBoolean("resourcepack.enabled", false) ? "オン" : "オフ (resourcepack.enabled)")
                + " / 場所: " + (baseUrl() == null ? "未設定 (resourcepack.base-url か host)" : baseUrl()));
        for (Pack pack : packs()) {
            File file = new File(folder(), pack.file());
            sender.sendMessage("  " + pack.name() + ": " + pack.file() + (file.isFile() ? " (" + file.length() + " バイト, sha1 " + hash(file) + ")" : " (ファイルなし)")
                    + (pack.required() ? " 必須" : ""));
        }
    }
}
