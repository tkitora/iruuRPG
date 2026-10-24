package net.tkgon.mc.iruuRPG.tools.procedural;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The procedural tree tool (admins). {@code /proceduraltool} gives a placement tool (a sapling); clicking a block with it opens the settings menu ({@link ProceduralMenu}) for a tree
 * standing on that block. The menu can turn the preview on ({@link TreePreview}: the tree as stained glass, to that player alone, following the settings), and starts the
 * generation: the skeleton of the tree is grown and turned into blocks off the main thread ({@link TreeGenerator}), and the blocks are put in the world through
 * FastAsyncWorldEdit in the player's own edit session ({@link TreeWorldEdit}), so {@code //undo} and {@code //redo} take the whole tree back and put it again. Every part of
 * the tree is a wool of its own colour ({@link TreePart}) to be changed into real blocks by hand. Only air and plants are replaced (not terrain), unless the config says
 * otherwise. Like the brush this is a tool, not game logic: it lives in this package, has its own command, item and listeners, and the rest of the plugin only starts it.
 */
public final class ProceduralModule implements Listener {

    static final String PREFIX = "[木] ";

    private final JavaPlugin plugin;
    private final NamespacedKey toolKey;
    private final TreeStore store;
    private final ProceduralMenu menu;
    private final TreePreview preview;
    private final boolean worldEdit;
    private final Map<UUID, TreeSession> sessions = new HashMap<>();

    /** Starts the tree tool: registers its listeners and its command. Never throws: a failure is logged and the tool is simply not there. */
    public static ProceduralModule start(JavaPlugin plugin) {
        try {
            ProceduralModule module = new ProceduralModule(plugin);
            plugin.getServer().getPluginManager().registerEvents(module, plugin);
            plugin.getServer().getPluginManager().registerEvents(module.menu, plugin);
            org.bukkit.command.PluginCommand command = plugin.getCommand("proceduraltool");
            if (command != null) {
                ProceduralCommand handler = new ProceduralCommand(module);
                command.setExecutor(handler);
                command.setTabCompleter(handler);
            }
            return module;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Tree tool not started: " + exception);
            return null;
        }
    }

    private ProceduralModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "tree_tool");
        this.store = new TreeStore(plugin);
        this.menu = new ProceduralMenu(this);
        this.preview = new TreePreview(this);
        this.worldEdit = plugin.getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                || plugin.getServer().getPluginManager().isPluginEnabled("WorldEdit");
        if (!worldEdit) plugin.getLogger().warning("Tree tool: WorldEdit / FastAsyncWorldEdit is not there, trees cannot be undone.");
    }

    /** The plugin stops: every preview is taken back and no task is left. */
    public void stop() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (sessions.containsKey(player.getUniqueId())) preview.drop(player);
        }
    }

    JavaPlugin plugin() {
        return plugin;
    }

    TreeStore store() {
        return store;
    }

    ProceduralMenu menu() {
        return menu;
    }

    TreePreview preview() {
        return preview;
    }

    TreeSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), ignored -> new TreeSession());
    }

    /** A setting or the place changed: the preview, when it is on, follows after its wait. */
    void changed(Player player) {
        syncBase(session(player));
        preview.touch(player);
    }

    /** The place of the object: above the block clicked (a tree, a rock), or on the face that was clicked (a spike or a crystal, which grow out of it toward the player). */
    private static void syncBase(TreeSession session) {
        if (session.clicked == null) return;
        boolean pointing = session.settings.kind() == ProcKind.SPIKE || session.settings.kind() == ProcKind.CRYSTAL;
        BlockFace face = pointing ? session.face : BlockFace.UP;
        session.base = session.clicked.clone().add(face.getModX(), face.getModY(), face.getModZ());
    }

    // ---- config ---------------------------------------------------------------------------------------------------------

    /** The material of a part: {@code treetool.materials.<part>} of the config, else its wool. */
    Material material(TreePart part) {
        String name = plugin.getConfig().getString("treetool.materials." + part.key(), part.defaultMaterial().name());
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null && material.isBlock() ? material : part.defaultMaterial();
    }

    /** Keeps the settings inside the height and width the config allows. */
    void limit(TreeSettings settings) {
        int maxHeight = plugin.getConfig().getInt("treetool.max-height", 80);
        int maxWidth = plugin.getConfig().getInt("treetool.max-width", 60);
        if (settings.height() > maxHeight) settings.set(TreeSettings.Key.HEIGHT, maxHeight);
        if (settings.width() > maxWidth) settings.set(TreeSettings.Key.WIDTH, maxWidth);
    }

    // ---- the tool item ----------------------------------------------------------------------------------------------------

    ItemStack toolItem() {
        ItemStack item = new ItemStack(Material.OAK_SAPLING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("プロシージャルツール", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                text("ブロックをクリック: そこに置くもの (木・岩・スパイク・結晶) の設定画面を開く", NamedTextColor.GRAY),
                text("空中を右クリック: 前回の場所の設定画面を開く", NamedTextColor.GRAY),
                text("設定画面の「生成」で、部位ごとのウールで木を作る", NamedTextColor.GRAY),
                text("設定画面の「プレビュー」で、あなたにだけ見えるガラスの木を確かめられる", NamedTextColor.GRAY),
                text("作った木は //undo で戻せる", NamedTextColor.GRAY),
                text("/proceduraltool off でこのツールを消す", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    boolean isTool(ItemStack item) {
        if (item == null || item.getType() != Material.OAK_SAPLING || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    void give(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTool(item)) {
                player.sendMessage(PREFIX + "もう持っています。ブロックをクリックすると設定画面が開きます。");
                return;
            }
        }
        player.getInventory().addItem(toolItem()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage(PREFIX + "プロシージャルツールを渡しました。ブロックをクリックすると、置くもの (木・岩・スパイク・結晶) の設定画面が開きます。");
    }

    void take(Player player) {
        preview.off(player, false);
        int removed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isTool(contents[slot])) {
                player.getInventory().setItem(slot, null);
                removed++;
            }
        }
        player.sendMessage(PREFIX + (removed > 0 ? "ツールを片づけました。" : "ツールは持っていません。"));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isTool(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("iruurpg.admin")) {
            player.sendMessage(PREFIX + "このツールは管理者だけが使えます。");
            return;
        }
        TreeSession session = session(player);
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) return;
            session.clicked = clicked.getLocation();
            session.face = event.getBlockFace() == null || event.getBlockFace() == BlockFace.SELF ? BlockFace.UP : event.getBlockFace();
            syncBase(session);
            if (session.base != null) {
                org.bukkit.util.Vector toEye = player.getEyeLocation().toVector().subtract(session.base.toVector().add(new org.bukkit.util.Vector(0.5, 0.5, 0.5)));
                session.aim = toEye.lengthSquared() < 1.0E-6 ? null : toEye.normalize();
            }
            changed(player);
            menu.open(player);
        } else if (action == Action.RIGHT_CLICK_AIR) {
            if (session.base == null) {
                player.sendMessage(PREFIX + "置きたい場所のブロックをクリックしてください。");
                return;
            }
            menu.open(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        preview.drop(event.getPlayer());
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        TreeSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.preview) preview.off(event.getPlayer(), true);
    }

    // ---- generating ---------------------------------------------------------------------------------------------------------

    /** Grows the tree of the player's settings on their base block and puts it in the world. */
    void generate(Player player) {
        TreeSession session = session(player);
        if (session.base == null || session.base.getWorld() == null) {
            player.sendMessage(PREFIX + "置きたい場所のブロックをクリックしてください。");
            return;
        }
        if (session.busy) {
            player.sendMessage(PREFIX + "前の木をまだ作っています。終わるまで待ってください。");
            return;
        }
        TreeSettings settings = session.settings.copy();
        limit(settings);
        // (with the preview on the seed is fixed: the tree put in the world is the one that was shown)
        long seed = settings.seed() != 0L ? settings.seed() : new Random().nextLong() & Long.MAX_VALUE;
        Location base = session.base.clone();
        boolean wasPreview = session.preview;
        if (wasPreview) preview.off(player, false);
        session.busy = true;
        player.sendMessage(PREFIX + "作っています… (" + session.settings.kind().label() + ", " + algorithmLabel(settings.algorithm()) + ", 高さ " + settings.height() + " 幅 " + settings.width() + ")");
        TreeGround ground;
        try {
            ground = TreeGround.sample(base, TreeGenerator.groundRadius(settings));
        } catch (RuntimeException exception) {
            ground = null;
        }
        TreeGround terrain = ground;
        org.bukkit.util.Vector aim = session.aim == null ? null : session.aim.clone();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Long, TreePart> blocks;
            try {
                blocks = TreeGenerator.build(settings, seed, terrain, aim);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("tree generation failed: " + exception);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    session.busy = false;
                    player.sendMessage(PREFIX + "生成に失敗しました (ログを見てください)。");
                });
                return;
            }
            Map<Long, TreePart> result = blocks;
            Bukkit.getScheduler().runTask(plugin, () -> place(player, session, base, result, seed));
        });
    }

    /**
     * Puts the blocks in the world. The blocks that can be replaced are found a few thousand a tick (reading the world is the part that must stay on the main thread), then
     * all of them go through one WorldEdit edit session of the player, so that one //undo takes the tree back. Without WorldEdit they are set directly, as they are found.
     */
    private void place(Player player, TreeSession session, Location base, Map<Long, TreePart> blocks, long seed) {
        int cap = plugin.getConfig().getInt("treetool.max-blocks", 60000);
        if (blocks.size() > cap) {
            session.busy = false;
            player.sendMessage(PREFIX + "ブロックが多すぎます (" + blocks.size() + " > " + cap + ")。高さ・幅・葉の大きさを小さくしてください。");
            return;
        }
        World world = base.getWorld();
        List<Long> order = new ArrayList<>(blocks.keySet());
        order.sort((a, b) -> {
            int y = Integer.compare(TreeVoxelizer.unpackY(a), TreeVoxelizer.unpackY(b));
            if (y != 0) return y;
            int x = Integer.compare(TreeVoxelizer.unpackX(a), TreeVoxelizer.unpackX(b));
            return x != 0 ? x : Integer.compare(TreeVoxelizer.unpackZ(a), TreeVoxelizer.unpackZ(b));
        });
        Map<TreePart, BlockData> materials = new EnumMap<>(TreePart.class);
        for (TreePart part : TreePart.values()) materials.put(part, material(part).createBlockData());
        boolean replaceSolid = plugin.getConfig().getBoolean("treetool.replace-solid", false);
        int perTick = Math.max(200, plugin.getConfig().getInt("treetool.blocks-per-tick", 4000));
        List<TreeWorldEdit.Change> changes = new ArrayList<>();
        Map<TreePart, Integer> counts = new EnumMap<>(TreePart.class);
        List<TreePart> kindParts = session.settings.kind().parts();

        new BukkitRunnable() {
            private int next;
            private int direct;

            @Override
            public void run() {
                try {
                    int end = Math.min(order.size(), next + perTick);
                    for (; next < end; next++) {
                        long key = order.get(next);
                        int x = base.getBlockX() + TreeVoxelizer.unpackX(key);
                        int y = base.getBlockY() + TreeVoxelizer.unpackY(key);
                        int z = base.getBlockZ() + TreeVoxelizer.unpackZ(key);
                        if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                        if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                        Block block = world.getBlockAt(x, y, z);
                        if (!replaceable(block, replaceSolid)) continue;
                        TreePart part = blocks.get(key);
                        counts.merge(part, 1, Integer::sum);
                        if (worldEdit) {
                            changes.add(new TreeWorldEdit.Change(x, y, z, materials.get(part)));
                        } else {
                            block.setBlockData(materials.get(part), false);
                            direct++;
                        }
                    }
                    if (next >= order.size()) {
                        cancel();
                        finish();
                    }
                } catch (RuntimeException exception) {
                    cancel();
                    session.busy = false;
                    plugin.getLogger().warning("tree placing stopped: " + exception);
                    player.sendMessage(PREFIX + "置いている途中で失敗しました (ログを見てください)。");
                }
            }

            private void finish() {
                int count = direct;
                boolean undoable = worldEdit;
                if (worldEdit) {
                    try {
                        count = TreeWorldEdit.apply(player, world, changes);
                    } catch (RuntimeException | LinkageError exception) {
                        plugin.getLogger().warning("Tree: WorldEdit failed, putting the tree without undo: " + exception);
                        for (TreeWorldEdit.Change change : changes) world.getBlockAt(change.x(), change.y(), change.z()).setBlockData(change.data(), false);
                        count = changes.size();
                        undoable = false;
                    }
                }
                session.busy = false;
                StringBuilder text = new StringBuilder(PREFIX + "できました (" + count + "ブロック, seed " + seed + "): ");
                for (TreePart part : kindParts) {
                    text.append(part.label()).append(' ').append(counts.getOrDefault(part, 0)).append("  ");
                }
                player.sendMessage(text.toString().trim());
                player.sendMessage(PREFIX + (undoable ? "気に入らなければ //undo (やり直しは //redo)。" : "WorldEdit がないので戻せません。") + "seed を固定すると同じ木になります。");
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    static boolean replaceable(Block block, boolean replaceSolid) {
        Material type = block.getType();
        if (type == Material.BEDROCK || type == Material.BARRIER) return false;
        if (replaceSolid) return true;
        if (block.isLiquid()) return false;
        return type.isAir() || block.isReplaceable();
    }

    /** Takes the last edit of the player back: the same as //undo (a tree, or a stroke of the brush). */
    void undo(Player player) {
        if (!worldEdit) {
            player.sendMessage(PREFIX + "WorldEdit がないので戻せません。");
            return;
        }
        player.performCommand("/undo");
    }

    void redo(Player player) {
        if (!worldEdit) {
            player.sendMessage(PREFIX + "WorldEdit がないので使えません。");
            return;
        }
        player.performCommand("/redo");
    }

    /** The word the tool is called by in messages, for the menu and the command. */
    static String algorithmLabel(String algorithm) {
        return switch (algorithm) {
            case "lsystem" -> "L-system (Honda 型)";
            case "colonization" -> "空間コロニー化";
            case "conifer" -> "針葉樹 (輪生)";
            case "parametric" -> "パラメトリック (Weber-Penn 型)";
            default -> "建築向け (ブロック風)";
        };
    }

    static String crownLabel(String crown) {
        return switch (crown.toLowerCase(Locale.ROOT)) {
            case "cone" -> "円錐 (針葉樹)";
            case "umbrella" -> "傘 (平らなドーム)";
            case "columnar" -> "円柱 (細長い)";
            default -> "楕円体";
        };
    }
}
