package net.tkgon.mc.iruuRPG.tool.tree;

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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The procedural tree tool (admins). {@code /treetool} gives a placement tool (a sapling); clicking a block with it opens the settings menu
 * ({@link TreeMenu}) for a tree standing on that block; the menu starts the generation. The skeleton of the tree is grown and turned into blocks off
 * the main thread ({@link TreeAlgorithm}, {@link TreeVoxelizer}: arithmetic only), then the blocks are put in the world a few thousand a tick, bottom
 * first. Every part of the tree is a wool of its own colour ({@link TreePart}) to be changed into real blocks by hand. Only air and plants are
 * replaced (not terrain), unless the config says otherwise, and the last few trees can be taken back.
 */
public final class TreeTool implements Listener {

    private static final String PREFIX = "[木] ";

    /** What one player has in hand: the settings being edited, where the tree would stand and the trees that can be taken back. */
    public static final class Session {
        public TreeSettings settings = new TreeSettings();
        public Location base;
        boolean busy;
        final Deque<List<Undo>> undo = new ArrayDeque<>();
    }

    private record Undo(int x, int y, int z, BlockData old) {
    }

    private final JavaPlugin plugin;
    private final NamespacedKey toolKey;
    private final TreeStore store;
    private final TreeMenu menu;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public TreeTool(JavaPlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "tree_tool");
        this.store = new TreeStore(plugin);
        this.menu = new TreeMenu(this);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public TreeStore store() {
        return store;
    }

    public TreeMenu menu() {
        return menu;
    }

    public Session session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session());
    }

    // ---- config ---------------------------------------------------------------------------------------------------------

    /** The material of a part: {@code treetool.materials.<part>} of the config, else its wool. */
    public Material material(TreePart part) {
        String name = plugin.getConfig().getString("treetool.materials." + part.key(), part.defaultMaterial().name());
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null && material.isBlock() ? material : part.defaultMaterial();
    }

    /** Keeps the settings inside the height and width the config allows. */
    public void limit(TreeSettings settings) {
        int maxHeight = plugin.getConfig().getInt("treetool.max-height", 80);
        int maxWidth = plugin.getConfig().getInt("treetool.max-width", 60);
        if (settings.height() > maxHeight) settings.set(TreeSettings.Key.HEIGHT, maxHeight);
        if (settings.width() > maxWidth) settings.set(TreeSettings.Key.WIDTH, maxWidth);
    }

    // ---- the tool item ----------------------------------------------------------------------------------------------------

    public ItemStack toolItem() {
        ItemStack item = new ItemStack(Material.OAK_SAPLING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("木の配置ツール", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                text("ブロックをクリック: その上に立てる木の設定画面を開く", NamedTextColor.GRAY),
                text("空中を右クリック: 前回の場所の設定画面を開く", NamedTextColor.GRAY),
                text("設定画面の「生成」で、部位ごとのウールで木を作る", NamedTextColor.GRAY),
                text("/treetool off でこのツールを消す", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public boolean isTool(ItemStack item) {
        if (item == null || item.getType() != Material.OAK_SAPLING || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    public void give(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTool(item)) {
                player.sendMessage(PREFIX + "もう持っています。ブロックをクリックすると設定画面が開きます。");
                return;
            }
        }
        player.getInventory().addItem(toolItem()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage(PREFIX + "木の配置ツールを渡しました。ブロックをクリックすると、その上に立てる木の設定画面が開きます。");
    }

    public void take(Player player) {
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
        Session session = session(player);
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) return;
            session.base = clicked.getRelative(BlockFace.UP).getLocation();
            menu.open(player);
        } else if (action == Action.RIGHT_CLICK_AIR) {
            if (session.base == null) {
                player.sendMessage(PREFIX + "木を立てたいブロックをクリックしてください。");
                return;
            }
            menu.open(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    // ---- generating ---------------------------------------------------------------------------------------------------------

    /** Grows the tree of the player's settings on their base block and puts it in the world. */
    public void generate(Player player) {
        Session session = session(player);
        if (session.base == null || session.base.getWorld() == null) {
            player.sendMessage(PREFIX + "木を立てたいブロックをクリックしてください。");
            return;
        }
        if (session.busy) {
            player.sendMessage(PREFIX + "前の木をまだ作っています。終わるまで待ってください。");
            return;
        }
        TreeSettings settings = session.settings.copy();
        limit(settings);
        long seed = settings.seed() != 0L ? settings.seed() : new Random().nextLong() & Long.MAX_VALUE;
        Location base = session.base.clone();
        session.busy = true;
        player.sendMessage(PREFIX + "木を作っています… (" + (settings.algorithm().equals("lsystem") ? "L-system" : "空間コロニー化") + ", 高さ " + settings.height() + " 幅 " + settings.width() + ")");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Long, TreePart> blocks;
            try {
                Random random = new Random(seed);
                TreeSkeleton skeleton = TreeAlgorithm.of(settings.algorithm()).grow(settings, random);
                blocks = TreeVoxelizer.voxelize(skeleton, settings, random);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("tree generation failed: " + exception);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    session.busy = false;
                    player.sendMessage(PREFIX + "木の生成に失敗しました (ログを見てください)。");
                });
                return;
            }
            Map<Long, TreePart> result = blocks;
            Bukkit.getScheduler().runTask(plugin, () -> place(player, session, base, result, seed));
        });
    }

    private void place(Player player, Session session, Location base, Map<Long, TreePart> blocks, long seed) {
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
        Map<TreePart, Material> materials = new EnumMap<>(TreePart.class);
        for (TreePart part : TreePart.values()) materials.put(part, material(part));
        boolean replaceSolid = plugin.getConfig().getBoolean("treetool.replace-solid", false);
        int perTick = Math.max(200, plugin.getConfig().getInt("treetool.blocks-per-tick", 4000));
        int undoSteps = Math.max(0, plugin.getConfig().getInt("treetool.undo-steps", 5));
        List<Undo> undo = new ArrayList<>();
        Map<TreePart, Integer> counts = new EnumMap<>(TreePart.class);

        new BukkitRunnable() {
            private int next;

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
                        Block block = world.getBlockAt(x, y, z);
                        if (!replaceable(block, replaceSolid)) continue;
                        TreePart part = blocks.get(key);
                        undo.add(new Undo(x, y, z, block.getBlockData()));
                        block.setType(materials.get(part), false);
                        counts.merge(part, 1, Integer::sum);
                    }
                    if (next >= order.size()) {
                        cancel();
                        finish();
                    }
                } catch (RuntimeException exception) {
                    cancel();
                    session.busy = false;
                    plugin.getLogger().warning("tree placing stopped: " + exception);
                    player.sendMessage(PREFIX + "木を置いている途中で失敗しました (ログを見てください)。");
                }
            }

            private void finish() {
                session.busy = false;
                if (undoSteps > 0 && !undo.isEmpty()) {
                    session.undo.push(undo);
                    while (session.undo.size() > undoSteps) session.undo.removeLast();
                }
                StringBuilder text = new StringBuilder(PREFIX + "できました (" + undo.size() + "ブロック, seed " + seed + "): ");
                for (TreePart part : TreePart.values()) {
                    text.append(part.label()).append(' ').append(counts.getOrDefault(part, 0)).append("  ");
                }
                player.sendMessage(text.toString().trim());
                player.sendMessage(PREFIX + "気に入らなければ /treetool undo (メニューの「元に戻す」)。seed を固定すると同じ木になります。");
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static boolean replaceable(Block block, boolean replaceSolid) {
        Material type = block.getType();
        if (type == Material.BEDROCK || type == Material.BARRIER) return false;
        if (replaceSolid) return true;
        if (block.isLiquid()) return false;
        return type.isAir() || block.isReplaceable();
    }

    /** Takes back the last tree of the player. */
    public void undo(Player player) {
        Session session = session(player);
        if (session.busy) {
            player.sendMessage(PREFIX + "木を作っている間は戻せません。");
            return;
        }
        List<Undo> last = session.undo.poll();
        if (last == null) {
            player.sendMessage(PREFIX + "戻せる木がありません。");
            return;
        }
        World world = player.getWorld();
        session.busy = true;
        int perTick = Math.max(200, plugin.getConfig().getInt("treetool.blocks-per-tick", 4000));
        new BukkitRunnable() {
            private int index = last.size() - 1;

            @Override
            public void run() {
                int stop = Math.max(-1, index - perTick);
                for (; index > stop; index--) {
                    Undo entry = last.get(index);
                    world.getBlockAt(entry.x(), entry.y(), entry.z()).setBlockData(entry.old(), false);
                }
                if (index < 0) {
                    cancel();
                    session.busy = false;
                    player.sendMessage(PREFIX + "戻しました (" + last.size() + "ブロック)。");
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The word the tool is called by in messages, for the menu and the command. */
    public static String algorithmLabel(String algorithm) {
        return "lsystem".equals(algorithm) ? "L-system (Honda 型)" : "空間コロニー化";
    }

    public static String crownLabel(String crown) {
        return switch (crown.toLowerCase(Locale.ROOT)) {
            case "cone" -> "円錐 (針葉樹)";
            case "umbrella" -> "傘 (平らなドーム)";
            default -> "楕円体";
        };
    }
}
