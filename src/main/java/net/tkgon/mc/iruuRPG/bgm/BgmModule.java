package net.tkgon.mc.iruuRPG.bgm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Background music. The songs are Note Block Studio files ({@link NbsSong}) in {@code plugins/iruuRPG/bgm/}; the places where they play are boxes ({@link BgmVolume}) kept
 * in {@code bgm/volumes.yml} and made with a wand or with {@code /bgm}. A player who walks into a box hears its song start; when they walk into another box, or out of
 * every box, the song fades away (and the next one fades in). Every player has their own {@link BgmChannel}, and their own loudness ({@link BgmSettings}, in the settings
 * menu). Like the other tools this lives in its own package and the rest of the plugin only starts it; starting never throws (a failure is logged and there is no BGM).
 */
public final class BgmModule implements Listener, Bgm {

    static final String PREFIX = "[BGM] ";
    private static final String PERMISSION = "iruurpg.admin";
    private static final double EDGE_MARGIN = 1.5;

    private final JavaPlugin plugin;
    private final BgmRegistry registry;
    private final BgmSettings settings;
    private final NamespacedKey wandKey;
    private final Map<UUID, BgmChannel> channels = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Set<UUID> warned = new HashSet<>();
    private BukkitTask task;
    private BukkitTask watchTask;
    private int counter;
    private final int checkInterval;
    private final int watchInterval;
    private boolean watchWarned;

    /** The two corners a player has picked with the wand (or with /bgm pos1 and pos2). */
    static final class Selection {
        String world;
        int[] first;
        int[] second;

        boolean complete() {
            return world != null && first != null && second != null;
        }
    }

    /** Starts the BGM: listeners, the command and the tick. Never throws. */
    public static BgmModule start(JavaPlugin plugin, BgmSettings settings) {
        try {
            if (!plugin.getConfig().getBoolean("bgm.enabled", true)) return null;
            BgmModule module = new BgmModule(plugin, settings);
            module.registry.reload();
            plugin.getServer().getPluginManager().registerEvents(module, plugin);
            org.bukkit.command.PluginCommand command = plugin.getCommand("bgm");
            if (command != null) {
                BgmCommand handler = new BgmCommand(module);
                command.setExecutor(handler);
                command.setTabCompleter(handler);
            }
            settings.bgm(module);
            settings.nowPlaying(player -> {
                BgmChannel channel = module.channels.get(player.getUniqueId());
                return channel == null ? null : channel.nowPlaying();
            });
            module.task = Bukkit.getScheduler().runTaskTimer(plugin, module::tick, 20L, 1L);
            module.watchTask = Bukkit.getScheduler().runTaskTimer(plugin, module::watch, module.watchInterval, module.watchInterval);
            plugin.getLogger().info("BGM: " + module.registry.songs().size() + " song(s), " + module.registry.volumes().size() + " volume(s).");
            return module;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("BGM not started: " + exception);
            return null;
        }
    }

    private BgmModule(JavaPlugin plugin, BgmSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.registry = new BgmRegistry(plugin);
        this.wandKey = new NamespacedKey(plugin, "bgm_wand");
        this.checkInterval = Math.max(2, plugin.getConfig().getInt("bgm.check-ticks", 10));
        this.watchInterval = Math.max(20, plugin.getConfig().getInt("bgm.watch-ticks", 100));
    }

    public void stop() {
        if (task != null) task.cancel();
        if (watchTask != null) watchTask.cancel();
        settings.bgm(Bgm.NONE);
        channels.clear();
        selections.clear();
    }

    BgmRegistry registry() {
        return registry;
    }

    BgmSettings settings() {
        return settings;
    }

    JavaPlugin plugin() {
        return plugin;
    }

    BgmChannel channel(Player player) {
        return channels.computeIfAbsent(player.getUniqueId(), id -> new BgmChannel());
    }

    Selection selection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), id -> new Selection());
    }

    /**
     * Songs and volumes are read again when a file changed on disk: a song that the yml repository brought (the plugin pulls it about every minute), one put in
     * {@code plugins/iruuRPG/bgm/} by hand, or volumes.yml edited by hand. Players who hear a song that changed go over to the new one with a fade.
     */
    private void watch() {
        try {
            String changed = registry.refreshIfChanged();
            if (changed != null) plugin.getLogger().info("BGM: changed on disk, read again: " + changed);
        } catch (RuntimeException exception) {
            if (!watchWarned) plugin.getLogger().warning("bgm: cannot look for changed files: " + exception);
            watchWarned = true;
        }
    }

    // ---- Bgm: what the rest of the plugin may ask --------------------------------------------------------------------------------------

    @Override
    public List<SongInfo> songs() {
        List<SongInfo> list = new java.util.ArrayList<>();
        for (NbsSong song : registry.songs().values()) list.add(info(song));
        return list;
    }

    @Override
    public SongInfo song(String id) {
        NbsSong song = registry.song(id);
        return song == null ? null : info(song);
    }

    private SongInfo info(NbsSong song) {
        return new SongInfo(song.id().toLowerCase(java.util.Locale.ROOT), song.title(), song.seconds(), registry.origin(song.id()));
    }

    @Override
    public void playScene(Player player, String songId, boolean loop, double fadeSeconds) {
        NbsSong song = registry.song(songId);
        if (song != null) channel(player).playScene(song, loop, fadeSeconds);
    }

    @Override
    public void playSceneSynced(Player player, String songId, boolean loop, double fadeSeconds) {
        NbsSong song = registry.song(songId);
        if (song != null) channel(player).playSceneSynced(song, loop, fadeSeconds);
    }

    @Override
    public void endScene(Player player, double fadeSeconds) {
        BgmChannel channel = channels.get(player.getUniqueId());
        if (channel != null) channel.endScene(fadeSeconds);
    }

    @Override
    public boolean preview(Player player, String songId) {
        NbsSong song = registry.song(songId);
        if (song == null) return false;
        channel(player).playManual(song, true, 0.6);
        return true;
    }

    @Override
    public void stopPreview(Player player) {
        BgmChannel channel = channels.get(player.getUniqueId());
        if (channel != null && channel.manual()) channel.stopManual(0.6);
    }

    @Override
    public String previewing(Player player) {
        BgmChannel channel = channels.get(player.getUniqueId());
        NbsSong song = channel == null ? null : channel.manualSong();
        return song == null ? null : song.id().toLowerCase(java.util.Locale.ROOT);
    }

    // ---- running ---------------------------------------------------------------------------------------------------------------------

    private void tick() {
        counter++;
        boolean check = counter % checkInterval == 0;
        boolean anyVolume = !registry.volumes().isEmpty();
        for (Player player : Bukkit.getOnlinePlayers()) {
            BgmChannel channel = channels.get(player.getUniqueId());
            if (channel == null) {
                if (!anyVolume) continue;
                channel = channel(player);
            }
            try {
                if (check && !channel.manual()) {
                    BgmVolume here = anyVolume ? registry.at(player.getLocation(), channel.current(), EDGE_MARGIN) : null;
                    channel.follow(here, registry);
                }
                channel.tick(player, settings.percent(player) / 100.0f);
            } catch (RuntimeException exception) {
                if (warned.add(player.getUniqueId())) plugin.getLogger().warning("bgm: " + player.getName() + ": " + exception);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        channels.remove(event.getPlayer().getUniqueId());
        selections.remove(event.getPlayer().getUniqueId());
        warned.remove(event.getPlayer().getUniqueId());
    }

    // ---- the wand --------------------------------------------------------------------------------------------------------------------

    ItemStack wand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("BGMの範囲ワンド", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("左クリック: 1つ目の角 (ブロック)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("右クリック: 2つ目の角 (ブロック)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("/bgm create <id> <曲> で範囲を作る", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem())) return;
        Player player = event.getPlayer();
        event.setCancelled(true);
        if (!player.hasPermission(PERMISSION)) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        setCorner(player, action == Action.LEFT_CLICK_BLOCK, block.getWorld().getName(), new int[]{block.getX(), block.getY(), block.getZ()});
    }

    /** Sets a corner of the player's selection ({@code first}: the first one). A corner in another world starts a new selection. */
    void setCorner(Player player, boolean first, String world, int[] point) {
        Selection selection = selection(player);
        if (!world.equals(selection.world)) {
            selection.first = null;
            selection.second = null;
        }
        selection.world = world;
        if (first) selection.first = point; else selection.second = point;
        player.sendMessage(Component.text(PREFIX + (first ? "1つ目" : "2つ目") + "の角: " + point[0] + " " + point[1] + " " + point[2]
                + (selection.complete() ? "  (" + size(selection.first, selection.second) + ")" : ""), NamedTextColor.AQUA));
    }

    static String size(int[] a, int[] b) {
        int x = Math.abs(a[0] - b[0]) + 1;
        int y = Math.abs(a[1] - b[1]) + 1;
        int z = Math.abs(a[2] - b[2]) + 1;
        return x + " x " + y + " x " + z + " ブロック";
    }

    // ---- showing the boxes -----------------------------------------------------------------------------------------------------------

    private final Map<UUID, BukkitTask> shows = new HashMap<>();

    /** Draws the edges of the boxes of the player's world (or of one box) with particles, to that player alone, for a while. */
    void show(Player player, String onlyId, int seconds) {
        hide(player);
        final int[] left = {Math.max(1, seconds) * 2};
        BukkitTask show = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || left[0]-- <= 0) {
                hide(player);
                return;
            }
            String world = player.getWorld().getName();
            for (BgmVolume volume : registry.volumes().values()) {
                if (!volume.inWorld(world)) continue;
                if (onlyId != null && !volume.id().equals(onlyId)) continue;
                outline(player, volume.min(), volume.max(), Color.fromRGB(80, 255, 120));
            }
            Selection selection = selections.get(player.getUniqueId());
            if (selection != null && selection.complete() && selection.world.equals(world)) outline(player, selection.first, selection.second, Color.fromRGB(80, 200, 255));
        }, 0L, 10L);
        shows.put(player.getUniqueId(), show);
    }

    void hide(Player player) {
        BukkitTask old = shows.remove(player.getUniqueId());
        if (old != null) old.cancel();
    }

    private void outline(Player player, int[] a, int[] b, Color color) {
        double x0 = Math.min(a[0], b[0]);
        double y0 = Math.min(a[1], b[1]);
        double z0 = Math.min(a[2], b[2]);
        double x1 = Math.max(a[0], b[0]) + 1;
        double y1 = Math.max(a[1], b[1]) + 1;
        double z1 = Math.max(a[2], b[2]) + 1;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        for (double y : new double[]{y0, y1}) {
            for (double z : new double[]{z0, z1}) edge(player, dust, x0, y, z, x1, y, z);
            for (double x : new double[]{x0, x1}) edge(player, dust, x, y, z0, x, y, z1);
        }
        for (double x : new double[]{x0, x1}) {
            for (double z : new double[]{z0, z1}) edge(player, dust, x, y0, z, x, y1, z);
        }
    }

    private void edge(Player player, Particle.DustOptions dust, double x0, double y0, double z0, double x1, double y1, double z1) {
        double length = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        int points = (int) Math.max(1, Math.min(40, Math.ceil(length / 2.0)));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            Location at = new Location(player.getWorld(), x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t);
            player.spawnParticle(Particle.REDSTONE, at, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }
}
