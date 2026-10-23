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
    private final BrushMenu menu;
    private final boolean worldEdit;

    /** Starts the brush: registers its listeners and its command. Never throws: a failure is logged and the brush is simply not there. */
    public static BrushModule start(JavaPlugin plugin) {
        try {
            BrushModule module = new BrushModule(plugin);
            plugin.getServer().getPluginManager().registerEvents(module, plugin);
            plugin.getServer().getPluginManager().registerEvents(module.menu, plugin);
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
        this.worldEdit = plugin.getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                || plugin.getServer().getPluginManager().isPluginEnabled("WorldEdit");
        this.menu = new BrushMenu(this);
        if (!worldEdit) plugin.getLogger().warning("Brush tool: WorldEdit / FastAsyncWorldEdit is not there, strokes cannot be undone.");
    }

    public void stop() {
        livePreview.shutdown();
        preview.clearAll();
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
            menu.open(player, BrushMenu.Page.MAIN);
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
        BrushEngine.Result result = evaluate(player, block, Math.max(1000, plugin.getConfig().getInt("brush.max-blocks", 120000)));
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
        int count;
        if (worldEdit) {
            try {
                count = WorldEditBridge.apply(player, block.getWorld(), result.changes());
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("Brush: WorldEdit failed, drawing without undo: " + exception);
                count = drawDirect(block.getWorld(), result.changes());
            }
        } else {
            count = drawDirect(block.getWorld(), result.changes());
        }
        player.sendTitle("", "§7" + s.mode.label() + ": " + count + "ブロック" + (worldEdit ? "" : " (undo なし)"), 0, 14, 6);
        if (result.note() != null) player.sendMessage(PREFIX + result.note());
    }

    /** What one use at {@code block} would change, with the facing of the player, the selection and the blacklist put in. */
    BrushEngine.Result evaluate(Player player, Block block, int cap) {
        BrushSettings s = settings(player);
        org.bukkit.util.Vector look = player.getLocation().getDirection().setY(0.0);
        s.facing = look.lengthSquared() < 1.0E-6 ? new org.bukkit.util.Vector(0.0, 0.0, 1.0) : look.normalize();
        return BrushEngine.compute(block.getWorld(), block.getX(), block.getY(), block.getZ(), s, cap, store.blacklist());
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
        menu.open(player, BrushMenu.Page.MAIN);
    }

    void menuOpen(Player player, BrushMenu.Page page) {
        menu.open(player, page);
    }

    boolean hasWorldEdit() {
        return worldEdit;
    }
}
