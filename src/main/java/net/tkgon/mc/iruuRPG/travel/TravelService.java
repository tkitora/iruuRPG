package net.tkgon.mc.iruuRPG.travel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fast travel: travel points placed by admins (/travel set). A point is unlocked by walking up to it once; unlocked
 * points can be reached from the travel menu. Travelling is not instant: the player channels for a few seconds
 * inside rising rings of light (moving or taking damage stops it), the screen fades, and they arrive in a burst of
 * light. Not inside dungeons.
 */
public final class TravelService implements Listener {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,24}");

    public record Waypoint(String id, String name, String world, double x, double y, double z, float yaw) {

        public Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, 0.0f);
        }
    }

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final File waypointFile;
    private final File discoveredFile;
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> discovered = new HashMap<>();
    private final Map<UUID, BukkitTask> channels = new HashMap<>();
    private final List<Entity> labels = new ArrayList<>();
    private int tick;

    public TravelService(JavaPlugin plugin, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.waypointFile = new File(plugin.getDataFolder(), "travel/waypoints.yml");
        this.discoveredFile = new File(plugin.getDataFolder(), "travel/discovered.yml");
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    // ---- settings --------------------------------------------------------------------------------------------------

    private int channelTicks() {
        return Math.max(20, plugin.getConfig().getInt("travel.channel-ticks", 60));
    }

    public long cost() {
        return Math.max(0L, plugin.getConfig().getLong("travel.cost", 0L));
    }

    private double discoverRadius() {
        return Math.max(1.0, plugin.getConfig().getDouble("travel.discover-radius", 6.0));
    }

    private String currency() {
        return plugin.getConfig().getString("quest.currency-name", "ゴールド");
    }

    // ---- reading ---------------------------------------------------------------------------------------------------

    public List<Waypoint> all() {
        return new ArrayList<>(waypoints.values());
    }

    public Waypoint find(String id) {
        return waypoints.get(id);
    }

    public boolean known(Player player, String id) {
        return discovered.getOrDefault(player.getUniqueId(), Set.of()).contains(id);
    }

    public boolean channeling(Player player) {
        return channels.containsKey(player.getUniqueId());
    }

    // ---- start / stop ----------------------------------------------------------------------------------------------

    public void start() {
        load();
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshLabels, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void shutdown() {
        for (BukkitTask task : channels.values()) task.cancel();
        channels.clear();
        for (Entity label : labels) {
            if (label.isValid()) label.remove();
        }
        labels.clear();
    }

    /** Every half second: points glitter; players near a point they have not found yet find it. */
    private void tick() {
        tick++;
        double radius = discoverRadius();
        for (Waypoint point : waypoints.values()) {
            Location at = point.location();
            if (at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            double angle = tick * 0.6;
            for (int k = 0; k < 3; k++) {
                double a = angle + k * (Math.PI * 2 / 3);
                at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(Math.cos(a) * 0.8, 0.3 + (tick % 6) * 0.3, Math.sin(a) * 0.8), 1, 0, 0, 0, 0);
            }
            for (Player player : at.getWorld().getPlayers()) {
                if (known(player, point.id())) continue;
                if (player.getLocation().distanceSquared(at) > radius * radius) continue;
                discover(player, point);
            }
        }
    }

    private void discover(Player player, Waypoint point) {
        discovered.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>()).add(point.id());
        saveDiscovered();
        player.sendMessage(Component.text("◆ ファストトラベル地点「" + point.name() + "」を見つけた。メニューの「ファストトラベル」から来られる。", NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.05);
    }

    // ---- travelling ------------------------------------------------------------------------------------------------

    /** Starts the channel to {@code id}; it is cancelled by moving or taking damage. */
    public void travel(Player player, String id) {
        Waypoint point = waypoints.get(id);
        Location target = point == null ? null : point.location();
        if (point == null || target == null) {
            player.sendMessage(Component.text("[ファストトラベル] その地点はありません。", NamedTextColor.RED));
            return;
        }
        if (!known(player, id)) {
            player.sendMessage(Component.text("[ファストトラベル] まだ見つけていない地点です。一度歩いて訪れてください。", NamedTextColor.RED));
            return;
        }
        String world = player.getWorld().getName();
        if (world.startsWith("dungeon_")) {
            player.sendMessage(Component.text("[ファストトラベル] ダンジョンの中では使えません。", NamedTextColor.RED));
            return;
        }
        if (channels.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("[ファストトラベル] いま移動の準備中です。", NamedTextColor.RED));
            return;
        }
        if (player.getWorld().equals(target.getWorld()) && player.getLocation().distanceSquared(target) < 16 * 16) {
            player.sendMessage(Component.text("[ファストトラベル] もう「" + point.name() + "」のすぐ近くにいる。", NamedTextColor.GRAY));
            return;
        }
        PlayerProfile profile = profileManager.getOrCreate(player);
        if (profile.currency() < cost()) {
            player.sendMessage(Component.text("[ファストトラベル] " + currency() + "が足りません。(必要: " + cost() + ")", NamedTextColor.RED));
            return;
        }
        player.closeInventory();

        Location origin = player.getLocation().clone();
        int total = channelTicks();
        player.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        BukkitTask task = new BukkitRunnable() {
            private int t;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    stop(player);
                    return;
                }
                Location now = player.getLocation();
                if (!now.getWorld().equals(origin.getWorld()) || now.distanceSquared(origin) > 0.6 * 0.6) {
                    player.sendMessage(Component.text("[ファストトラベル] 動いたので中断した。", NamedTextColor.GRAY));
                    player.playSound(now, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.2f);
                    stop(player);
                    return;
                }
                t++;
                channelEffects(player, origin, t, total, point.name());
                if (t >= total) {
                    stop(player);
                    arrive(player, origin, point, target);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        channels.put(player.getUniqueId(), task);
    }

    /** Rising double helix of light, a ring that tightens at the feet, rising chimes, a progress subtitle. */
    private void channelEffects(Player player, Location origin, int t, int total, String name) {
        World world = origin.getWorld();
        double progress = (double) t / total;
        double angle = t * 0.35;
        double height = (t % 20) / 20.0 * 2.4;
        Particle.DustOptions cyan = new Particle.DustOptions(Color.fromRGB(90, 230, 255), 1.2f);
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.9f);
        for (int k = 0; k < 2; k++) {
            double a = angle + k * Math.PI;
            world.spawnParticle(Particle.REDSTONE, origin.clone().add(Math.cos(a) * 1.1, height, Math.sin(a) * 1.1), 1, 0, 0, 0, 0, cyan);
            world.spawnParticle(Particle.REDSTONE, origin.clone().add(Math.cos(a + 0.4) * 1.1, height * 0.7, Math.sin(a + 0.4) * 1.1), 1, 0, 0, 0, 0, white);
        }
        if (t % 4 == 0) {
            double radius = 1.6 * (1.0 - progress) + 0.3;
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                world.spawnParticle(Particle.END_ROD, origin.clone().add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius), 1, 0, 0, 0, 0);
            }
            world.spawnParticle(Particle.PORTAL, origin.clone().add(0, 1.0, 0), 12, 0.4, 0.6, 0.4, 0.4);
        }
        if (t % 10 == 0) {
            player.playSound(origin, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 0.6f + (float) progress * 1.2f);
            int filled = (int) Math.round(progress * 10);
            player.showTitle(Title.title(Component.text("ファストトラベル", NamedTextColor.AQUA, TextDecoration.BOLD),
                    Component.text("→ " + name + "  ", NamedTextColor.WHITE)
                            .append(Component.text("■".repeat(filled), NamedTextColor.AQUA))
                            .append(Component.text("■".repeat(10 - filled), NamedTextColor.DARK_GRAY)),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(200))));
        }
    }

    /** Fade out, a flash where they stood, the jump, and a column of light where they land. */
    private void arrive(Player player, Location origin, Waypoint point, Location target) {
        if (cost() > 0) {
            PlayerProfile profile = profileManager.getOrCreate(player);
            if (profile.currency() < cost()) {
                player.sendMessage(Component.text("[ファストトラベル] " + currency() + "が足りません。", NamedTextColor.RED));
                return;
            }
            profile.setCurrency(profile.currency() - cost());
            profileManager.save(player);
        }
        World from = origin.getWorld();
        from.spawnParticle(Particle.FLASH, origin.clone().add(0, 1, 0), 1);
        from.spawnParticle(Particle.END_ROD, origin.clone().add(0, 1, 0), 60, 0.3, 1.2, 0.3, 0.25);
        from.playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));

        player.teleport(target);
        World to = target.getWorld();
        to.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        to.playSound(target, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        player.showTitle(Title.title(Component.text(point.name(), NamedTextColor.WHITE, TextDecoration.BOLD),
                Component.text("に着いた", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(600), Duration.ofSeconds(2), Duration.ofMillis(600))));
        // a column of light falling in around them
        new BukkitRunnable() {
            private int t;

            @Override
            public void run() {
                if (t++ >= 12) {
                    cancel();
                    return;
                }
                double y = 3.0 - t * 0.25;
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + t * 0.3;
                    to.spawnParticle(Particle.END_ROD, target.clone().add(Math.cos(a) * 0.9, y, Math.sin(a) * 0.9), 1, 0, 0, 0, 0);
                }
                if (t == 12) to.spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 0.5, 0), 40, 0.5, 0.3, 0.5, 0.05);
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    private void stop(Player player) {
        BukkitTask task = channels.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !channels.containsKey(player.getUniqueId())) return;
        stop(player);
        player.sendMessage(Component.text("[ファストトラベル] 攻撃を受けたので中断した。", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.2f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
    }

    // ---- admin ------------------------------------------------------------------------------------------------------

    public void set(String id, String name, Location location) {
        waypoints.put(id, new Waypoint(id, name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw()));
        saveWaypoints();
        refreshLabels();
    }

    public boolean remove(String id) {
        if (waypoints.remove(id) == null) return false;
        saveWaypoints();
        refreshLabels();
        return true;
    }

    /** Labels over each point; not saved with the world. */
    public void refreshLabels() {
        for (Entity label : labels) {
            if (label.isValid()) label.remove();
        }
        labels.clear();
        for (Waypoint point : waypoints.values()) {
            Location at = point.location();
            if (at == null) continue;
            at.add(0, 2.4, 0).getChunk().load();
            labels.add(at.getWorld().spawn(at, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setPersistent(false);
                display.text(Component.text("◆ " + point.name(), NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.newline()).append(Component.text("ファストトラベル地点", NamedTextColor.GRAY)));
            }));
        }
    }

    // ---- storage ----------------------------------------------------------------------------------------------------

    private void load() {
        waypoints.clear();
        discovered.clear();
        YamlConfiguration points = YamlConfiguration.loadConfiguration(waypointFile);
        ConfigurationSection section = points.getConfigurationSection("waypoints");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null || !validId(id)) continue;
                waypoints.put(id, new Waypoint(id, entry.getString("name", id), entry.getString("world", "world"),
                        entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"), (float) entry.getDouble("yaw")));
            }
        }
        YamlConfiguration found = YamlConfiguration.loadConfiguration(discoveredFile);
        ConfigurationSection players = found.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                try {
                    discovered.put(UUID.fromString(key), new HashSet<>(players.getStringList(key)));
                } catch (IllegalArgumentException ignored) {
                    // a damaged entry is skipped
                }
            }
        }
    }

    private void saveWaypoints() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Waypoint point : waypoints.values()) {
            String base = "waypoints." + point.id() + ".";
            yaml.set(base + "name", point.name());
            yaml.set(base + "world", point.world());
            yaml.set(base + "x", point.x());
            yaml.set(base + "y", point.y());
            yaml.set(base + "z", point.z());
            yaml.set(base + "yaw", (double) point.yaw());
        }
        save(yaml, waypointFile);
    }

    private void saveDiscovered() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : discovered.entrySet()) {
            yaml.set("players." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        save(yaml, discoveredFile);
    }

    private void save(YamlConfiguration yaml, File file) {
        try {
            File folder = file.getParentFile();
            if (!folder.exists() && !folder.mkdirs()) return;
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("travel: could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
