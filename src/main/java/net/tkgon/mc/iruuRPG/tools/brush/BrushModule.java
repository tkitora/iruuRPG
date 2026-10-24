package net.tkgon.mc.iruuRPG.tools.brush;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The painting brush for builders (admins), after goPaint: a brush item that paints, shades and shapes the terrain where the player looks; a menu with pages for
 * the settings; masks; terraforming (raise, lower, smooth, flatten); an automatic shade that darkens the surfaces turned from a sun the player sets; and a preview.
 * Strokes are drawn through FastAsyncWorldEdit so {@code //undo} takes them back.
 *
 * <p>This is a tool, not game logic: everything is in this package, it reads and writes only blocks the player points at, it has its own command, item and listener,
 * and the rest of the plugin does not know it exists beyond the one line that starts it. If WorldEdit is missing the brush works without undo (and says so).
 */
public final class BrushModule implements Listener {

    static final String PREFIX = "[ブラシ] ";

    private final JavaPlugin plugin;
    private final NamespacedKey toolKey;
    private final Map<UUID, BrushSettings> settings = new HashMap<>();
    private final Map<UUID, Integer> lastUse = new HashMap<>();
    private final BrushPreview preview;
    private final BrushLivePreview livePreview;
    private final BrushStore store;
    private final SelectionTool selection;
    private final Map<UUID, Stroke> strokes = new HashMap<>();

    /** A stroke held down for the gradient along the stroke: where it went, drawn all at once when the button is let go. */
    private static final class Path {
        final List<Block> points = new ArrayList<>();
        final List<Double> distances = new ArrayList<>();
        Block last;
        double distance;
        int tick;
        org.bukkit.scheduler.BukkitTask finisher;
    }

    private final Map<UUID, Path> paths = new HashMap<>();

    /** The path of a stroke held down: where it was last used and how far it has gone (for the gradient along the stroke and for filling the gap between two uses). */
    private static final class Stroke {
        Block last;
        int tick;
        double distance;
    }
    private final BrushMenu menu;
    private final boolean worldEdit;

    /** Starts the brush: registers its listeners and its command. Never throws: a failure is logged and the brush is simply not there. */
    public static BrushModule start(JavaPlugin plugin) {
        try {
            BrushModule module = new BrushModule(plugin);
            plugin.getServer().getPluginManager().registerEvents(module, plugin);
            plugin.getServer().getPluginManager().registerEvents(module.menu, plugin);
            plugin.getServer().getPluginManager().registerEvents(module.selection, plugin);
            org.bukkit.command.PluginCommand command = plugin.getCommand("ibrush");
            if (command != null) {
                BrushCommand handler = new BrushCommand(module);
                command.setExecutor(handler);
                command.setTabCompleter(handler);
            }
            return module;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Brush tool not started: " + exception);
            return null;
        }
    }

    private BrushModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "brush_tool");
        this.preview = new BrushPreview(plugin);
        this.store = new BrushStore(plugin);
        this.livePreview = new BrushLivePreview(this);
        this.selection = new SelectionTool(this);
        this.worldEdit = plugin.getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                || plugin.getServer().getPluginManager().isPluginEnabled("WorldEdit");
        this.menu = new BrushMenu(this);
        if (!worldEdit) plugin.getLogger().warning("Brush tool: WorldEdit / FastAsyncWorldEdit is not there, strokes cannot be undone.");
    }

    public void stop() {
        for (UUID id : new ArrayList<>(paths.keySet())) forgetStroke(id);
        selection.stop();
        livePreview.shutdown();
        preview.clearAll();
    }

    SelectionTool selection() {
        return selection;
    }

    BrushStore store() {
        return store;
    }

    BrushPreview preview() {
        return preview;
    }

    BrushLivePreview livePreview() {
        return livePreview;
    }

    /** A setting changed: the live preview, if it is on, starts over after its wait. */
    void changed(Player player) {
        settings(player).revision++;
        livePreview.touch(player);
    }

    JavaPlugin plugin() {
        return plugin;
    }

    BrushSettings settings(Player player) {
        return settings.computeIfAbsent(player.getUniqueId(), ignored -> {
            BrushSettings fresh = new BrushSettings();
            fresh.radius = Math.min(fresh.radius, maxRadius());
            return fresh;
        });
    }

    int maxRadius() {
        return Math.max(2, Math.min(64, plugin.getConfig().getInt("brush.max-radius", 32)));
    }

    // ---- the tool item -----------------------------------------------------------------------------------------------------------

    ItemStack toolItem() {
        ItemStack item = new ItemStack(Material.BRUSH);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("ペイントブラシ", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                text("右クリック: 見ている所に使う", NamedTextColor.GRAY),
                text("Shift + 右クリック: 使う前にプレビュー (自分にだけ見える)", NamedTextColor.GRAY),
                text("左クリック: 設定画面を開く", NamedTextColor.GRAY),
                text("使ったあとは //undo で戻せる", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    boolean isTool(ItemStack item) {
        if (item == null || item.getType() != Material.BRUSH || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    void give(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTool(item)) {
                player.sendMessage(PREFIX + "もう持っています。左クリックで設定画面が開きます。");
                return;
            }
        }
        player.getInventory().addItem(toolItem()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage(PREFIX + "ペイントブラシを渡しました。左クリックで設定画面、右クリックで使います。");
    }

    void take(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isTool(contents[slot])) player.getInventory().setItem(slot, null);
        }
        preview.clear(player);
        player.sendMessage(PREFIX + "ブラシを片づけました。");
    }

    // ---- using it ------------------------------------------------------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isTool(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("iruurpg.admin")) {
            player.sendMessage(PREFIX + "このツールは管理者だけが使えます。");
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            menu.open(player, settings(player).lastPage);
            return;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        // a held button repeats the click; one use a few ticks apart
        int tick = org.bukkit.Bukkit.getCurrentTick();
        Integer last = lastUse.put(player.getUniqueId(), tick);
        if (last != null && tick - last < Math.max(1, plugin.getConfig().getInt("brush.cooldown-ticks", 4))) return;
        use(player, player.isSneaking());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        livePreview.off(event.getPlayer(), false);
        strokes.remove(event.getPlayer().getUniqueId());
        forgetStroke(event.getPlayer().getUniqueId());
        preview.clear(event.getPlayer());
        settings.remove(event.getPlayer().getUniqueId());
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    Block target(Player player) {
        double range = Math.max(8.0, Math.min(256.0, plugin.getConfig().getDouble("brush.range", 120.0)));
        RayTraceResult hit = player.rayTraceBlocks(range, FluidCollisionMode.NEVER);
        return hit == null ? null : hit.getHitBlock();
    }

    /** One use of the brush where the player looks: draws it, or (previewing) only shows it. */
    void use(Player player, boolean previewOnly) {
        Block block = target(player);
        if (block == null) {
            player.sendMessage(PREFIX + "ブロックを見てください (遠すぎます)。");
            return;
        }
        BrushSettings s = settings(player);
        int cap = Math.max(1000, plugin.getConfig().getInt("brush.max-blocks", 120000));
        if (!previewOnly && (s.strokeGroup || (s.patternMode == BrushSettings.PatternMode.GRADIENT && s.gradientAxis == BrushSettings.GradientAxis.STROKE))) {
            // the gradient along the stroke is laid when the button is let go, over the whole length of the stroke (0 at its start, 1 at its end)
            collectStroke(player, block, s);
            return;
        }
        BrushEngine.Result result = previewOnly ? evaluate(player, block, cap) : stroke(player, block, s, cap);
        if (result.changes().isEmpty()) {
            player.sendMessage(PREFIX + (result.note() != null ? result.note() : "変わるブロックがありません (範囲・マスク・模様を見直してください)。"));
            return;
        }
        if (previewOnly) {
            int shown = preview.show(player, block.getWorld(), result.changes(), Math.max(100, plugin.getConfig().getInt("brush.preview-blocks", 1500)),
                    Math.max(20, plugin.getConfig().getInt("brush.preview-ticks", 80)));
            player.sendMessage(PREFIX + "プレビュー: " + result.changes().size() + "ブロックが変わります (" + shown + "個を表示、あなたにだけ見えます)。右クリックで実行。");
            return;
        }
        preview.clear(player);
        int count = draw(player, block.getWorld(), result.changes());
        player.sendTitle("", "§7" + s.mode.label() + ": " + count + "ブロック" + (worldEdit ? "" : " (undo なし)"), 0, 14, 6);
        if (result.note() != null) player.sendMessage(PREFIX + result.note());
    }

    /** Adds the place looked at to the stroke being drawn (with the points between it and the last), shows a few dots where the stroke has been, and waits for the release. */
    private void collectStroke(Player player, Block block, BrushSettings s) {
        int now = org.bukkit.Bukkit.getCurrentTick();
        Path path = paths.computeIfAbsent(player.getUniqueId(), id -> new Path());
        int release = Math.max(8, plugin.getConfig().getInt("brush.cooldown-ticks", 4) * 2 + 2);
        if (path.last != null && (now - path.tick > release || !path.last.getWorld().equals(block.getWorld()))) {
            finishStroke(player);
            path = paths.computeIfAbsent(player.getUniqueId(), id -> new Path());
        }
        List<Block> added = new ArrayList<>();
        if (path.last != null && !path.last.equals(block)) {
            double gap = path.last.getLocation().distance(block.getLocation());
            double step = Math.max(1.0, s.radius * s.strokeSpacing);
            int count = (int) Math.min(24, Math.ceil(gap / step));
            for (int index = 1; index < count; index++) {
                double t = index / (double) count;
                int x = (int) Math.round(path.last.getX() + (block.getX() - path.last.getX()) * t);
                int y = (int) Math.round(path.last.getY() + (block.getY() - path.last.getY()) * t);
                int z = (int) Math.round(path.last.getZ() + (block.getZ() - path.last.getZ()) * t);
                added.add(block.getWorld().getBlockAt(x, y, z));
                path.points.add(added.get(added.size() - 1));
                path.distances.add(path.distance + gap * t);
            }
            path.distance += gap;
        }
        path.points.add(block);
        path.distances.add(path.distance);
        added.add(block);
        path.last = block;
        path.tick = now;
        org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 200, 60), 1.3f);
        for (Block point : added) player.spawnParticle(org.bukkit.Particle.REDSTONE, point.getLocation().add(0.5, 1.3, 0.5), 2, 0.1, 0.1, 0.1, 0.0, dust);
        if (path.finisher == null) {
            path.finisher = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Path current = paths.get(player.getUniqueId());
                if (current == null || !player.isOnline()) {
                    forgetStroke(player.getUniqueId());
                    return;
                }
                if (org.bukkit.Bukkit.getCurrentTick() - current.tick > release) finishStroke(player);
            }, 2L, 2L);
        }
        if (path.points.size() >= 400) finishStroke(player);
    }

    /** The button was let go: the brush is worked out at every point of the stroke, the place on the gradient being how far along the whole stroke the point is, and drawn at once. */
    private void finishStroke(Player player) {
        Path path = paths.remove(player.getUniqueId());
        if (path == null) return;
        if (path.finisher != null) path.finisher.cancel();
        if (!player.isOnline() || path.points.isEmpty()) return;
        BrushSettings s = settings(player);
        int cap = Math.max(1000, plugin.getConfig().getInt("brush.max-blocks", 120000));
        Map<Long, BrushEngine.Change> merged = new java.util.LinkedHashMap<>();
        String note = null;
        List<Block> points = smoothPath(path.points, s.strokeSmooth);
        java.util.Random random = new java.util.Random();
        for (int index = 0; index < points.size(); index++) {
            s.strokeT = path.distance <= 1.0E-6 ? 0.5 : Math.min(1.0, path.distances.get(index) / path.distance);
            // the brush is smaller or larger along the stroke by the taper, and each point may be moved aside a little
            double along = points.size() <= 1 ? 0.0 : index / (double) (points.size() - 1);
            s.radiusScale = s.taperStart + (s.taperEnd - s.taperStart) * along;
            Block at = points.get(index);
            if (s.strokeScatter > 0.0) {
                int reach = (int) Math.round(s.radius * s.strokeScatter);
                at = surfaceNear(at.getWorld(), at.getX() + random.nextInt(2 * reach + 1) - reach, at.getY(), at.getZ() + random.nextInt(2 * reach + 1) - reach);
            }
            BrushEngine.Result part = evaluate(player, at, cap);
            if (part.note() != null) note = part.note();
            for (BrushEngine.Change change : part.changes()) merged.put(SurfaceProbe.pack(change.x(), change.y(), change.z()), change);
            if (merged.size() >= cap) break;
        }
        s.radiusScale = 1.0;
        preview.clear(player);
        if (merged.isEmpty()) {
            player.sendMessage(PREFIX + (note != null ? note : "変わるブロックがありません (範囲・マスク・模様を見直してください)。"));
            return;
        }
        int count = draw(player, path.points.get(0).getWorld(), new ArrayList<>(merged.values()));
        player.sendTitle("", "§7なぞった線: " + count + "ブロック (長さ " + Math.round(path.distance) + ")" + (worldEdit ? "" : " (undo なし)"), 0, 14, 6);
        if (note != null) player.sendMessage(PREFIX + note);
    }

    /** The path with each point replaced by the average of the points near it (more of them the more smoothing), moved onto the surface; the same number of points. */
    private List<Block> smoothPath(List<Block> points, double amount) {
        int window = (int) Math.round(Math.max(0.0, Math.min(1.0, amount)) * 4.0);
        if (window == 0 || points.size() < 3) return points;
        List<Block> smoothed = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            int count = 0;
            for (int near = Math.max(0, index - window); near <= Math.min(points.size() - 1, index + window); near++) {
                x += points.get(near).getX();
                y += points.get(near).getY();
                z += points.get(near).getZ();
                count++;
            }
            smoothed.add(surfaceNear(points.get(index).getWorld(), (int) Math.round(x / count), (int) Math.round(y / count), (int) Math.round(z / count)));
        }
        return smoothed;
    }

    /** The block at ({@code x}, {@code z}) nearest in height to {@code y} (within 3) that is solid with room above it; the block at {@code y} when there is none. */
    private static Block surfaceNear(org.bukkit.World world, int x, int y, int z) {
        for (int offset = 0; offset <= 3; offset++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int at = y + sign * offset;
                if (at < world.getMinHeight() + 1 || at >= world.getMaxHeight() - 1) continue;
                Material type = world.getType(x, at, z);
                if (type.isSolid() && !type.isAir() && !world.getType(x, at + 1, z).isSolid()) return world.getBlockAt(x, at, z);
            }
        }
        return world.getBlockAt(x, y, z);
    }

    private void forgetStroke(UUID id) {
        Path path = paths.remove(id);
        if (path != null && path.finisher != null) path.finisher.cancel();
    }

    /**
     * One use of the brush, or (when it is held and the block looked at has moved) the whole stretch from the last use to this one: points are laid along the line between
     * them, no farther apart than 60% of the radius, and the brush is worked out at each, so a fast drag leaves no gaps. With the gradient along the stroke, the place on the
     * gradient follows the distance travelled since the stroke began.
     */
    private BrushEngine.Result stroke(Player player, Block block, BrushSettings s, int cap) {
        int now = org.bukkit.Bukkit.getCurrentTick();
        Stroke stroke = strokes.computeIfAbsent(player.getUniqueId(), id -> new Stroke());
        boolean continuing = stroke.last != null && now - stroke.tick <= Math.max(8, plugin.getConfig().getInt("brush.cooldown-ticks", 4) * 3)
                && stroke.last.getWorld().equals(block.getWorld()) && stroke.last.getLocation().distanceSquared(block.getLocation()) < 96.0 * 96.0;
        if (!continuing) {
            stroke.distance = 0.0;
            stroke.last = null;
        }
        List<Block> points = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        if (stroke.last != null && !stroke.last.equals(block)) {
            double gap = stroke.last.getLocation().distance(block.getLocation());
            double step = Math.max(1.0, s.radius * s.strokeSpacing);
            int count = (int) Math.min(24, Math.ceil(gap / step));
            for (int index = 1; index < count; index++) {
                double t = index / (double) count;
                int x = (int) Math.round(stroke.last.getX() + (block.getX() - stroke.last.getX()) * t);
                int z = (int) Math.round(stroke.last.getZ() + (block.getZ() - stroke.last.getZ()) * t);
                int y = (int) Math.round(stroke.last.getY() + (block.getY() - stroke.last.getY()) * t);
                points.add(block.getWorld().getBlockAt(x, y, z));
                distances.add(stroke.distance + gap * t);
            }
            stroke.distance += gap;
        }
        points.add(block);
        distances.add(stroke.distance);
        stroke.last = block;
        stroke.tick = now;
        Map<Long, BrushEngine.Change> merged = new java.util.LinkedHashMap<>();
        String note = null;
        double length = Math.max(4.0, plugin.getConfig().getDouble("brush.stroke-length", 24.0));
        for (int index = 0; index < points.size(); index++) {
            s.strokeT = Math.min(1.0, distances.get(index) / length);
            BrushEngine.Result part = evaluate(player, points.get(index), cap);
            if (part.note() != null) note = part.note();
            for (BrushEngine.Change change : part.changes()) merged.put(SurfaceProbe.pack(change.x(), change.y(), change.z()), change);
            if (merged.size() >= cap) break;
        }
        return new BrushEngine.Result(new ArrayList<>(merged.values()), note);
    }

    /** Puts the changes in the world as one edit of the player (with undo when WorldEdit is there); returns how many blocks were put. */
    int draw(Player player, org.bukkit.World world, List<BrushEngine.Change> changes) {
        if (worldEdit) {
            try {
                return WorldEditBridge.apply(player, world, changes);
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("Brush: WorldEdit failed, drawing without undo: " + exception);
            }
        }
        return drawDirect(world, changes);
    }

    /** What one use at {@code block} would change, with the facing of the player, the selection and the blacklist put in. */
    BrushEngine.Result evaluate(Player player, Block block, int cap) {
        BrushSettings s = settings(player);
        org.bukkit.util.Vector look = player.getLocation().getDirection().setY(0.0);
        s.facing = look.lengthSquared() < 1.0E-6 ? new org.bukkit.util.Vector(0.0, 0.0, 1.0) : look.normalize();
        s.selection = selection.active(player);
        return BrushEngine.compute(block.getWorld(), block.getX(), block.getY(), block.getZ(), s, cap, store.shadeBlacklist());
    }

    private int drawDirect(org.bukkit.World world, List<BrushEngine.Change> changes) {
        int count = 0;
        for (BrushEngine.Change change : changes) {
            world.getBlockAt(change.x(), change.y(), change.z()).setBlockData(change.data(), false);
            count++;
        }
        return count;
    }

    void undo(Player player) {
        if (!worldEdit) {
            player.sendMessage(PREFIX + "WorldEdit がないので戻せません。");
            return;
        }
        preview.clear(player);
        player.performCommand("/undo");
    }

    void redo(Player player) {
        if (!worldEdit) {
            player.sendMessage(PREFIX + "WorldEdit がないので使えません。");
            return;
        }
        preview.clear(player);
        player.performCommand("/redo");
    }

    void menuOpen(Player player) {
        menu.open(player, settings(player).lastPage);
    }

    void menuOpen(Player player, BrushMenu.Page page) {
        menu.open(player, page);
    }

    boolean hasWorldEdit() {
        return worldEdit;
    }
}
