package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The preview of the tree, which the player toggles: while it is on, the tree of the settings is shown to that player alone as stained glass of the colour of each part's
 * wool (block changes sent to that client; nothing in the world changes, nobody else sees it), and it follows the settings as they are edited.
 *
 * <p>It is light by construction: the seed is fixed while it is on (so an edit changes the tree and nothing else); a change only starts the work after the settings were
 * left alone for {@code treetool.preview-delay-ticks} (15 ticks, 0.75 s), and a further change before that restarts the wait; the tree is worked out off the main thread;
 * if the previous preview is still being worked out when the wait ends, the new one is not started (and nothing is queued); the blocks are sent a few thousand a tick, and
 * only those that differ from what the client has. Every task is kept in the session and cancelled when the preview goes off, the player leaves or the plugin stops.
 */
final class TreePreview {

    private final ProceduralModule module;
    private final JavaPlugin plugin;

    TreePreview(ProceduralModule module) {
        this.module = module;
        this.plugin = module.plugin();
    }

    // ---- the key of a block ------------------------------------------------------------------------------------------------------

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFFL);
    }

    private static int keyX(long key) {
        return (int) (key >> 38);
    }

    private static int keyZ(long key) {
        return (int) ((key << 26) >> 38);
    }

    private static int keyY(long key) {
        return (int) (key & 0xFFFL) - 2048;
    }

    // ---- turning it on and off -----------------------------------------------------------------------------------------------------

    void toggle(Player player) {
        if (module.session(player).preview) off(player, true);
        else on(player);
    }

    void on(Player player) {
        TreeSession session = module.session(player);
        if (session.preview) return;
        if (session.base == null || session.base.getWorld() == null) {
            player.sendMessage(ProceduralModule.PREFIX + "木を立てたいブロックを、ツールでクリックしてから切り替えてください。");
            return;
        }
        session.preview = true;
        fixSeed(session);
        player.sendMessage(ProceduralModule.PREFIX + "プレビューをオンにしました (あなたにだけ見えます)。シードを固定し、設定を変えると " + delay() * 0.05 + " 秒後に反映します。");
        schedule(player, session, 1L);
    }

    void off(Player player, boolean tell) {
        TreeSession session = module.session(player);
        boolean was = session.preview;
        session.preview = false;
        session.job++;
        cancelTasks(session);
        restore(player, session);
        if (session.autoSeed) {
            session.autoSeed = false;
            session.settings.setSeed(0L);
        }
        if (tell && was) player.sendMessage(ProceduralModule.PREFIX + "プレビューをオフにしました。");
    }

    /** A setting (or the place) changed: the preview, if it is on, follows after the wait. */
    void touch(Player player) {
        TreeSession session = module.session(player);
        if (!session.preview) return;
        schedule(player, session, delay());
    }

    /** The player leaves, or the plugin stops: nothing stays running or shown. */
    void drop(Player player) {
        TreeSession session = module.session(player);
        session.preview = false;
        session.job++;
        cancelTasks(session);
        restore(player, session);
        session.autoSeed = false;
    }

    private long delay() {
        return Math.max(1L, plugin.getConfig().getLong("treetool.preview-delay-ticks", 15L));
    }

    private static void cancelTasks(TreeSession session) {
        if (session.timer != null) session.timer.cancel();
        session.timer = null;
        if (session.show != null) session.show.cancel();
        session.show = null;
    }

    /** While the preview is on the seed is fixed: a tree without one gets one (and loses it again when the preview goes off). */
    private static void fixSeed(TreeSession session) {
        if (session.settings.seed() != 0L) return;
        session.settings.setSeed(1L + (new Random().nextLong() & 0xFFFFFFL));
        session.autoSeed = true;
    }

    // ---- the wait, and the work ------------------------------------------------------------------------------------------------------

    private void schedule(Player player, TreeSession session, long ticks) {
        if (session.timer != null) session.timer.cancel();
        session.timer = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            session.timer = null;
            start(player, session);
        }, ticks);
    }

    private void start(Player player, TreeSession session) {
        if (!session.preview || !player.isOnline()) return;
        // the previous preview is still being worked out: this one is dropped, not queued
        if (session.computing) return;
        Location base = session.base == null ? null : session.base.clone();
        if (base == null || base.getWorld() == null) return;
        fixSeed(session);
        TreeSettings settings = session.settings.copy();
        module.limit(settings);
        long seed = settings.seed();
        TreeGround ground;
        try {
            ground = TreeGround.sample(base, TreeGenerator.groundRadius(settings));
        } catch (RuntimeException exception) {
            ground = null;
        }
        TreeGround terrain = ground;
        session.computing = true;
        int job = ++session.job;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Long, TreePart> blocks = null;
            try {
                blocks = TreeGenerator.build(settings, seed, terrain);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("tree preview failed: " + exception);
            }
            Map<Long, TreePart> result = blocks;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    session.computing = false;
                    if (result != null && session.preview && job == session.job && player.isOnline()) show(player, session, base, result);
                });
            } catch (RuntimeException exception) {
                // the plugin is being disabled: nothing more to do
                session.computing = false;
            }
        });
    }

    // ---- showing it ------------------------------------------------------------------------------------------------------------------------

    /** The glass of a part: the stained glass of the colour of its wool (the configured block when that is a wool, else the part's own wool). */
    private Material glass(TreePart part) {
        Material configured = module.material(part);
        String name = configured.name().endsWith("_WOOL") ? configured.name() : part.defaultMaterial().name();
        Material glass = Material.matchMaterial(name.replace("_WOOL", "_STAINED_GLASS"));
        return glass == null ? Material.WHITE_STAINED_GLASS : glass;
    }

    private void show(Player player, TreeSession session, Location base, Map<Long, TreePart> blocks) {
        if (session.show != null) session.show.cancel();
        session.show = null;
        World world = base.getWorld();
        if (session.shownWorld != null && !session.shownWorld.equals(world)) restore(player, session);
        session.shownWorld = world;
        int cap = Math.max(1000, plugin.getConfig().getInt("treetool.preview-max-blocks", 40000));
        if (blocks.size() > cap) {
            player.sendMessage(ProceduralModule.PREFIX + "プレビューするにはブロックが多すぎます (" + blocks.size() + " > " + cap + ")。高さ・幅・葉の大きさを小さくしてください。");
            blocks = Map.of();
        }
        Map<TreePart, Material> glasses = new HashMap<>();
        for (TreePart part : TreePart.values()) glasses.put(part, glass(part));
        Map<Long, Material> target = new HashMap<>();
        List<Long> adds = new ArrayList<>();
        for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
            int x = base.getBlockX() + TreeVoxelizer.unpackX(entry.getKey());
            int y = base.getBlockY() + TreeVoxelizer.unpackY(entry.getKey());
            int z = base.getBlockZ() + TreeVoxelizer.unpackZ(entry.getKey());
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
            long key = key(x, y, z);
            target.put(key, glasses.get(entry.getValue()));
            if (session.shown.get(key) != target.get(key)) adds.add(key);
        }
        List<Long> removes = new ArrayList<>();
        for (Long key : session.shown.keySet()) if (!target.containsKey(key)) removes.add(key);
        adds.sort((a, b) -> Integer.compare(keyY(a), keyY(b)));
        boolean replaceSolid = plugin.getConfig().getBoolean("treetool.replace-solid", false);
        int perTick = Math.max(200, plugin.getConfig().getInt("treetool.preview-blocks-per-tick", 3000));
        session.show = new BukkitRunnable() {
            private int removed;
            private int added;

            @Override
            public void run() {
                if (!player.isOnline() || !session.preview) {
                    cancel();
                    session.show = null;
                    return;
                }
                int budget = perTick;
                while (budget > 0 && removed < removes.size()) {
                    long key = removes.get(removed++);
                    session.shown.remove(key);
                    send(player, world, key, null);
                    budget--;
                }
                while (budget > 0 && added < adds.size()) {
                    long key = adds.get(added++);
                    budget--;
                    int x = keyX(key);
                    int z = keyZ(key);
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block block = world.getBlockAt(x, keyY(key), z);
                    if (!ProceduralModule.replaceable(block, replaceSolid)) {
                        if (session.shown.remove(key) != null) send(player, world, key, null);
                        continue;
                    }
                    Material glass = target.get(key);
                    session.shown.put(key, glass);
                    player.sendBlockChange(block.getLocation(), glass.createBlockData());
                }
                if (removed >= removes.size() && added >= adds.size()) {
                    cancel();
                    session.show = null;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Sends the block at {@code key} to the player: {@code glass} null sends the real block back. */
    private static void send(Player player, World world, long key, Material glass) {
        int x = keyX(key);
        int z = keyZ(key);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        Location at = new Location(world, x, keyY(key), z);
        player.sendBlockChange(at, glass == null ? world.getBlockData(at) : glass.createBlockData());
    }

    /** Sends the real blocks back to the player and forgets what was shown. */
    private void restore(Player player, TreeSession session) {
        World world = session.shownWorld;
        if (world != null && player.isOnline() && player.getWorld().equals(world)) {
            for (Long key : session.shown.keySet()) send(player, world, key, null);
        }
        session.shown.clear();
        session.shownWorld = null;
    }
}
