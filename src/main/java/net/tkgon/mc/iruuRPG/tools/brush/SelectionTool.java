package net.tkgon.mc.iruuRPG.tools.brush;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The selection tool, an item of its own beside the brush. Its selection holds the brush to it: the brush only works on selected blocks (or, inverted, only outside them).
 * <ul>
 *   <li><b>Fuzzy</b>: from the block clicked, the surface blocks that touch it and whose colour is like its colour (the threshold), within a radius (64 at most).</li>
 *   <li><b>Adjacent</b>: the blocks of the same kind that touch the block clicked, within the radius.</li>
 *   <li>Clicking replaces the selection; sneaking while clicking adds to it. It can be inverted, cleared, shown, and its colours blended.</li>
 * </ul>
 * The work of finding blocks is spread over ticks (a breadth-first search, {@code brush.selection.blocks-per-tick} blocks a tick), one job at a time per player, cancelled when
 * a new one starts, the player leaves or the plugin stops. What is shown is only the edge of the selection near the player, a few hundred particles, so showing never grows with
 * the size of the selection.
 */
final class SelectionTool implements Listener {

    enum Mode {
        FUZZY("ファジー (似た色)", "クリックしたブロックと色が似ていて、つながっている表面のブロックを選ぶ"),
        ADJACENT("隣接 (同じブロック)", "クリックしたブロックと同じ種類で、つながっているブロックを選ぶ");

        final String label;
        final String description;

        Mode(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /** What one player has set on the selection tool, and the selection itself. */
    static final class State {
        Mode mode = Mode.FUZZY;
        /** 0 - 100: how different a colour may be and still count as like (a share of the distance from black to white, squared). */
        int threshold = 20;
        int radius = 16;
        boolean surfaceOnly = true;
        boolean show = true;
        /** The brush is held to the selection. */
        boolean limitBrush = true;
        /** How the brush uses the selection: false, only on the blocks selected (a range); true, on any block of the kinds that were selected (a block mask). */
        boolean blocksAsMask;
        /** The kinds of block the selection holds (worked out once, dropped when the selection changes). */
        Set<Material> kinds;
        BlockSelection selection;
        BukkitTask job;
    }

    private static final String PREFIX = "[選択] ";
    private static final double MAX_DISTANCE = BlockColors.colorDistance(0x000000, 0xFFFFFF);

    private final BrushModule module;
    private final JavaPlugin plugin;
    private final NamespacedKey toolKey;
    private final Map<UUID, State> states = new HashMap<>();
    private BukkitTask displayTask;

    SelectionTool(BrushModule module) {
        this.module = module;
        this.plugin = module.plugin();
        this.toolKey = new NamespacedKey(plugin, "brush_select_tool");
    }

    State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
    }

    /** The selection that holds the brush, or null when there is none (or it is in another world, or the brush is not held to it). */
    Selection active(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null || !state.limitBrush || state.selection == null) return null;
        if (!state.selection.world.equals(player.getWorld().getUID())) return null;
        if (!state.blocksAsMask) return state.selection;
        World world = player.getWorld();
        if (state.kinds == null) {
            // the kinds of block the selection holds
            Set<Material> kinds = new HashSet<>();
            for (long key : state.selection.blocks) {
                Material type = world.getType(BlockSelection.unpackX(key), BlockSelection.unpackY(key), BlockSelection.unpackZ(key));
                Material base = BlockColors.baseOf(type);
                kinds.add(base != null ? base : type);
                if (kinds.size() > 64) break;
            }
            state.kinds = kinds;
        }
        return new TypeSelection(world, state.kinds, state.selection.inverted);
    }

    void stop() {
        for (State state : states.values()) if (state.job != null) state.job.cancel();
        states.clear();
        if (displayTask != null) displayTask.cancel();
        displayTask = null;
    }

    // ---- the item ----------------------------------------------------------------------------------------------------------------

    ItemStack toolItem() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("選択ツール", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                text("右クリック: 見ているブロックから選ぶ (ファジー / 隣接)", NamedTextColor.GRAY),
                text("Shift + 右クリック: いまの選択に足す", NamedTextColor.GRAY),
                text("左クリック: 選択の設定画面を開く", NamedTextColor.GRAY),
                text("選択はブラシに効く (ブラシは選んだ所だけに使える)", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    boolean isTool(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    void give(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTool(item)) {
                player.sendMessage(PREFIX + "もう持っています。");
                return;
            }
        }
        player.getInventory().addItem(toolItem()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage(PREFIX + "選択ツールを渡しました。右クリックで選び、左クリックで設定画面です。");
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
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            module.menuOpen(player, BrushMenu.Page.SELECT);
            return;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        double range = Math.max(8.0, Math.min(256.0, plugin.getConfig().getDouble("brush.range", 120.0)));
        RayTraceResult hit = player.rayTraceBlocks(range, FluidCollisionMode.NEVER);
        Block block = hit == null ? null : hit.getHitBlock();
        if (block == null) {
            player.sendMessage(PREFIX + "ブロックを見てください (遠すぎます)。");
            return;
        }
        select(player, block, player.isSneaking());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        State state = states.remove(event.getPlayer().getUniqueId());
        if (state != null && state.job != null) state.job.cancel();
    }

    // ---- selecting -----------------------------------------------------------------------------------------------------------------

    private int cap() {
        return Math.max(1000, plugin.getConfig().getInt("brush.selection.max-blocks", 300000));
    }

    int maxRadius() {
        return Math.max(4, Math.min(64, plugin.getConfig().getInt("brush.selection.max-radius", 64)));
    }

    /** Starts the search from {@code origin}: replaces the selection, or (when {@code add}) adds to it. Returns at once; the job finishes over the next ticks. */
    void select(Player player, Block origin, boolean add) {
        State state = state(player);
        if (state.job != null) {
            state.job.cancel();
            state.job = null;
        }
        World world = origin.getWorld();
        BlockSelection target = add && state.selection != null && state.selection.world.equals(world.getUID()) ? state.selection : new BlockSelection(world);
        Material type = origin.getType();
        if (type.isAir() || !type.isSolid()) {
            player.sendMessage(PREFIX + "固いブロックを見てください。");
            return;
        }
        if (state.mode == Mode.FUZZY && !BlockColors.known(BlockColors.baseOf(type) != null ? BlockColors.baseOf(type) : type)) {
            player.sendMessage(PREFIX + "このブロックは色が分からないので、ファジーでは選べません (隣接 なら選べます)。");
            return;
        }
        int radius = Math.min(state.radius, maxRadius());
        double allowed = Math.pow(state.threshold / 100.0, 2.0) * MAX_DISTANCE;
        int originColor = BlockColors.rgb(BlockColors.baseOf(type) != null ? BlockColors.baseOf(type) : type);
        Mode mode = state.mode;
        boolean surface = state.surfaceOnly || mode == Mode.FUZZY;
        int perTick = Math.max(1000, plugin.getConfig().getInt("brush.selection.blocks-per-tick", 20000));
        int cap = cap();
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        long radiusSquared = (long) radius * radius;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{ox, oy, oz});
        seen.add(SurfaceProbe.pack(ox, oy, oz));
        player.sendMessage(PREFIX + (mode == Mode.FUZZY ? "似た色を探しています…" : "同じブロックを探しています…") + " (半径 " + radius + (mode == Mode.FUZZY ? ", しきい値 " + state.threshold : "") + ")");
        int[][] neighbours = mode == Mode.FUZZY
                ? ALL_NEIGHBOURS
                : ALL_NEIGHBOURS;
        state.job = new org.bukkit.scheduler.BukkitRunnable() {
            private int added;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                int budget = perTick;
                while (budget > 0 && !queue.isEmpty()) {
                    int[] at = queue.poll();
                    budget--;
                    if (!world.isChunkLoaded(at[0] >> 4, at[2] >> 4)) continue;
                    Material here = world.getType(at[0], at[1], at[2]);
                    if (!matches(world, here, at[0], at[1], at[2])) continue;
                    target.add(at[0], at[1], at[2]);
                    added++;
                    if (target.blocks.size() >= cap) {
                        queue.clear();
                        player.sendMessage(PREFIX + "多すぎるので途中で止めました (" + cap + "ブロック)。しきい値か半径を下げてください。");
                        break;
                    }
                    for (int[] step : neighbours) {
                        int nx = at[0] + step[0];
                        int ny = at[1] + step[1];
                        int nz = at[2] + step[2];
                        long dx = nx - ox;
                        long dy = ny - oy;
                        long dz = nz - oz;
                        if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                        if (ny < world.getMinHeight() || ny >= world.getMaxHeight()) continue;
                        if (seen.add(SurfaceProbe.pack(nx, ny, nz))) queue.add(new int[]{nx, ny, nz});
                    }
                }
                if (queue.isEmpty()) {
                    cancel();
                    state.job = null;
                    state.selection = target;
                    state.kinds = null;
                    player.sendMessage(PREFIX + "選びました: " + target.blocks.size() + "ブロック" + (target.inverted ? " (反転中)" : "") + "。ブラシはこの範囲だけに効きます。");
                    ensureDisplay();
                }
            }

            private boolean matches(World w, Material here, int x, int y, int z) {
                if (here.isAir() || !here.isSolid()) return false;
                if (mode == Mode.ADJACENT) {
                    if (here != type) return false;
                } else {
                    Material base = BlockColors.baseOf(here) != null ? BlockColors.baseOf(here) : here;
                    if (!BlockColors.known(base)) return false;
                    if (BlockColors.colorDistance(BlockColors.rgb(base), originColor) > allowed) return false;
                }
                if (surface) {
                    for (int[] step : ALL_NEIGHBOURS) if (!solidAt(w, x + step[0], y + step[1], z + step[2])) return true;
                    return false;
                }
                return true;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The 26 blocks round a block: the faces, the edges and the corners (a condition on what is round a block looks at the diagonal ones too). */
    private static final int[][] ALL_NEIGHBOURS = neighbours();

    private static int[][] neighbours() {
        int[][] all = new int[26][];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) all[index++] = new int[]{dx, dy, dz};
                }
            }
        }
        return all;
    }

    private static boolean solidAt(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
        Material type = world.getType(x, y, z);
        return type.isSolid() && !type.isAir();
    }

    void invert(Player player) {
        State state = state(player);
        if (state.selection == null) {
            player.sendMessage(PREFIX + "まだ何も選んでいません。");
            return;
        }
        state.selection.inverted = !state.selection.inverted;
        player.sendMessage(PREFIX + (state.selection.inverted ? "選択を反転しました (ブラシは、選んだ所の「外」だけに効きます)。" : "反転を戻しました。"));
    }

    void clear(Player player) {
        State state = state(player);
        if (state.job != null) state.job.cancel();
        state.job = null;
        state.selection = null;
        state.kinds = null;
        player.sendMessage(PREFIX + "選択を解除しました。");
    }

    // ---- showing it ----------------------------------------------------------------------------------------------------------------

    private void ensureDisplay() {
        if (displayTask != null) return;
        displayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::display, 10L, 10L);
    }

    /** The edge of each player's selection near them, as dust only they see: at most {@code brush.selection.display-particles} a refresh, however big the selection. */
    private void display() {
        boolean any = false;
        double range = Math.max(6.0, plugin.getConfig().getDouble("brush.selection.display-range", 20.0));
        int limit = Math.max(100, plugin.getConfig().getInt("brush.selection.display-particles", 800));
        for (Map.Entry<UUID, State> entry : states.entrySet()) {
            State state = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !state.show || state.selection == null || state.selection.blocks.isEmpty()) continue;
            any = true;
            BlockSelection selection = state.selection;
            if (!selection.world.equals(player.getWorld().getUID())) continue;
            Location eye = player.getLocation();
            double rangeSquared = range * range;
            Particle.DustOptions dust = new Particle.DustOptions(selection.inverted ? Color.fromRGB(255, 140, 30) : Color.fromRGB(40, 220, 255), 1.1f);
            int shown = 0;
            for (long key : selection.blocks) {
                int x = BlockSelection.unpackX(key);
                int y = BlockSelection.unpackY(key);
                int z = BlockSelection.unpackZ(key);
                double dx = x + 0.5 - eye.getX();
                double dy = y + 0.5 - eye.getY();
                double dz = z + 0.5 - eye.getZ();
                if (dx * dx + dy * dy + dz * dz > rangeSquared) continue;
                // only the edge of the set: a block with a neighbour that is not selected
                boolean edge = false;
                for (int[] step : ALL_NEIGHBOURS) {
                    if (!selection.has(x + step[0], y + step[1], z + step[2])) {
                        edge = true;
                        break;
                    }
                }
                if (!edge) continue;
                // on the face that looks out (up when open above, else sideways)
                double px = x + 0.5;
                double py = y + 0.5;
                double pz = z + 0.5;
                if (!solidAt(player.getWorld(), x, y + 1, z)) py = y + 1.05;
                else if (!solidAt(player.getWorld(), x + 1, y, z)) px = x + 1.05;
                else if (!solidAt(player.getWorld(), x - 1, y, z)) px = x - 0.05;
                else if (!solidAt(player.getWorld(), x, y, z + 1)) pz = z + 1.05;
                else if (!solidAt(player.getWorld(), x, y, z - 1)) pz = z - 0.05;
                else continue;
                player.spawnParticle(Particle.REDSTONE, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0, dust);
                if (++shown >= limit) break;
            }
        }
        if (!any && states.values().stream().noneMatch(s -> s.selection != null)) {
            if (displayTask != null) displayTask.cancel();
            displayTask = null;
        }
    }

    void toggleShow(Player player) {
        State state = state(player);
        state.show = !state.show;
        if (state.show) ensureDisplay();
    }

    // ---- shading the whole selection ------------------------------------------------------------------------------------------------

    /** Applies the automatic shade (with every source switched on in the settings) to every selected surface block, in one edit (one //undo). Not available while inverted. */
    void shade(Player player) {
        State state = state(player);
        BlockSelection selection = state.selection;
        if (selection == null || selection.blocks.isEmpty()) {
            player.sendMessage(PREFIX + "まだ何も選んでいません。");
            return;
        }
        if (selection.inverted) {
            player.sendMessage(PREFIX + "反転中は使えません。反転を戻してください。");
            return;
        }
        World world = player.getWorld();
        if (!selection.world.equals(world.getUID())) {
            player.sendMessage(PREFIX + "選択は別のワールドにあります。");
            return;
        }
        BrushSettings settings = module.settings(player);
        SurfaceProbe probe = new SurfaceProbe(world, settings);
        Set<Material> allowed = BrushEngine.shadePalette(settings);
        List<BrushEngine.Change> changes = new ArrayList<>();
        for (long key : selection.blocks) {
            BrushEngine.Change change = BrushEngine.shadeOne(probe, settings, BlockSelection.unpackX(key), BlockSelection.unpackY(key), BlockSelection.unpackZ(key), allowed, module.store().shadeBlacklist());
            if (change != null) changes.add(change);
        }
        if (changes.isEmpty()) {
            player.sendMessage(PREFIX + "変わる所がありません (影の源・陰影のマスクを見直してください)。");
            return;
        }
        int count = module.draw(player, world, changes);
        player.sendMessage(PREFIX + "選択範囲に自動陰影を適用しました: " + count + "ブロック (//undo で戻せます)。");
    }

    // ---- blending ------------------------------------------------------------------------------------------------------------------

    /**
     * Blends the colours inside the selection: every selected surface block becomes the block, among those in the selection, whose colour is nearest to the average colour of
     * the blocks round it (3 x 3 x 3, the block itself counted twice). The result is drawn through the same edit as the brush (one //undo). Not available while inverted.
     */
    void blend(Player player) {
        State state = state(player);
        BlockSelection selection = state.selection;
        if (selection == null || selection.blocks.isEmpty()) {
            player.sendMessage(PREFIX + "まだ何も選んでいません。");
            return;
        }
        if (selection.inverted) {
            player.sendMessage(PREFIX + "反転中はぼかせません。反転を戻してください。");
            return;
        }
        World world = player.getWorld();
        if (!selection.world.equals(world.getUID())) {
            player.sendMessage(PREFIX + "選択は別のワールドにあります。");
            return;
        }
        // the blocks the result may be: the ones already in the selection
        Set<Material> palette = new HashSet<>();
        for (long key : selection.blocks) {
            Material type = world.getType(BlockSelection.unpackX(key), BlockSelection.unpackY(key), BlockSelection.unpackZ(key));
            Material base = BlockColors.baseOf(type) != null ? BlockColors.baseOf(type) : type;
            if (BlockColors.known(base)) palette.add(base);
        }
        if (palette.size() < 2) {
            player.sendMessage(PREFIX + "選んだ範囲に色の違うブロックが2種類以上ないので、ぼかせません。");
            return;
        }
        List<BrushEngine.Change> changes = new ArrayList<>();
        for (long key : selection.blocks) {
            int x = BlockSelection.unpackX(key);
            int y = BlockSelection.unpackY(key);
            int z = BlockSelection.unpackZ(key);
            Material type = world.getType(x, y, z);
            Material base = BlockColors.baseOf(type) != null ? BlockColors.baseOf(type) : type;
            if (!BlockColors.known(base)) continue;
            double r = 0;
            double g = 0;
            double b = 0;
            double total = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Material near = world.getType(x + dx, y + dy, z + dz);
                        Material nearBase = BlockColors.baseOf(near) != null ? BlockColors.baseOf(near) : near;
                        if (!BlockColors.known(nearBase)) continue;
                        double weight = dx == 0 && dy == 0 && dz == 0 ? 2.0 : 1.0;
                        int rgb = BlockColors.rgb(nearBase);
                        r += ((rgb >> 16) & 0xFF) * weight;
                        g += ((rgb >> 8) & 0xFF) * weight;
                        b += (rgb & 0xFF) * weight;
                        total += weight;
                    }
                }
            }
            if (total <= 0.0) continue;
            int average = ((int) Math.round(r / total) << 16) | ((int) Math.round(g / total) << 8) | (int) Math.round(b / total);
            Material best = base;
            double bestDistance = BlockColors.colorDistance(BlockColors.rgb(base), average);
            for (Material candidate : palette) {
                double d = BlockColors.colorDistance(BlockColors.rgb(candidate), average);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = candidate;
                }
            }
            if (best == base) continue;
            BlockData data = BrushEngine.restyle(world.getBlockAt(x, y, z).getBlockData(), best);
            if (data != null) changes.add(new BrushEngine.Change(x, y, z, data));
        }
        if (changes.isEmpty()) {
            player.sendMessage(PREFIX + "ぼかしても変わる所がありません (すでに滑らかです)。");
            return;
        }
        int count = module.draw(player, world, changes);
        player.sendMessage(PREFIX + "色をぼかしました: " + count + "ブロック (//undo で戻せます)。");
    }
}
