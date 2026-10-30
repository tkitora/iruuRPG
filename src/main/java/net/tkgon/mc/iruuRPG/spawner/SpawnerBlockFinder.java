package net.tkgon.mc.iruuRPG.spawner;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The game's own spawner blocks (/spawner blocks): the ones the world was made with (dungeons, mineshafts) and that are
 * hidden in the ground. A search loads the chunks round the player (only ones already made, none are made new), lights
 * every spawner block found with a glowing outline seen through the ground (only by the player who searched), and lists
 * them in chat with buttons: teleport there, delete one, delete all, undo the last delete, put the lights out.
 *
 * A deleted spawner becomes the block most of its neighbours are (stone in stone, cobblestone in a dungeon wall), or
 * air when none of them is a full block, so the ground is not left with a hole. The deleted blocks are kept (with what
 * they spawned) until the player leaves, for undo.
 */
public final class SpawnerBlockFinder implements Listener {

    public static final int DEFAULT_RADIUS = 8;
    public static final int MAX_RADIUS = 16;
    static final int PAGE_SIZE = 10;
    private static final long GLOW_TICKS = 20L * 60 * 5;
    private static final int MAX_GLOWING = 300;
    private static final Color GLOW = Color.fromRGB(0xFF5A1F);
    private static final BlockFace[] SIDES = {BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /** A spawner block found by a search; {@code deleted} once it was deleted from the list (the numbers stay the same). */
    private static final class Found {
        private final Block block;
        private final EntityType spawns;
        private final boolean buried;
        private BlockDisplay glow;
        private boolean deleted;

        private Found(Block block, EntityType spawns, boolean buried) {
            this.block = block;
            this.spawns = spawns;
            this.buried = buried;
        }
    }

    private static final class Search {
        private final List<Found> found = new ArrayList<>();
        private final List<List<BlockState>> undo = new ArrayList<>();
        private BukkitTask lightsOut;
        private boolean confirmAll;
        private boolean running;
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Search> searches = new HashMap<>();

    public SpawnerBlockFinder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        for (Search search : searches.values()) lightsOut(search);
        searches.clear();
    }

    // ---- the search --------------------------------------------------------------------------------------------------

    public void search(Player player, int radius) {
        Search search = searches.computeIfAbsent(player.getUniqueId(), id -> new Search());
        if (search.running) {
            player.sendMessage(Component.text("[スポナー] まだ探しています。", NamedTextColor.GRAY));
            return;
        }
        int r = Math.max(0, Math.min(MAX_RADIUS, radius));
        lightsOut(search);
        search.found.clear();
        search.confirmAll = false;
        search.running = true;
        World world = player.getWorld();
        Location center = player.getLocation();
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        player.sendMessage(Component.text("[スポナー] まわり " + r + " チャンクのスポナーブロックを探しています…", NamedTextColor.GRAY));
        List<Block> blocks = new ArrayList<>();
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                // a chunk that was never made comes back as null, and is not made
                loads.add(world.getChunkAtAsync(x, z, false).thenAccept(chunk -> {
                    if (chunk != null) onMain(() -> collect(chunk, blocks));
                }));
            }
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((done, error) -> onMain(() -> {
            search.running = false;
            if (!player.isOnline()) return;
            Location here = player.getLocation();
            blocks.sort(Comparator.comparingDouble(block -> block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(here)));
            for (Block block : blocks) {
                if (!(block.getState(false) instanceof CreatureSpawner spawner)) continue;
                search.found.add(new Found(block, spawner.getSpawnedType(), buried(neighbours(block))));
            }
            if (error != null) plugin.getLogger().warning("[スポナー] チャンクの読み込みに失敗したところがあります: " + error);
            glow(player, search);
            page(player, 1);
        }));
    }

    private void onMain(Runnable work) {
        if (Bukkit.isPrimaryThread()) {
            work.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, work);
        }
    }

    private static void collect(Chunk chunk, List<Block> into) {
        for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.SPAWNER, false)) {
            into.add(state.getBlock());
        }
    }

    private static List<Material> neighbours(Block block) {
        List<Material> list = new ArrayList<>(SIDES.length);
        for (BlockFace side : SIDES) list.add(block.getRelative(side).getType());
        return list;
    }

    /** Buried: every side is against a solid block, so it cannot be seen from anywhere. */
    static boolean buried(List<Material> neighbours) {
        for (Material material : neighbours) {
            if (!material.isSolid()) return false;
        }
        return true;
    }

    /** What a deleted spawner becomes: the full block most of its sides touch (the one below wins a tie), else air. */
    static Material fill(List<Material> neighbours) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        Material best = Material.AIR;
        int bestCount = 0;
        for (Material material : neighbours) {
            if (material == Material.SPAWNER || !material.isOccluding()) continue;
            int count = counts.merge(material, 1, Integer::sum);
            if (count > bestCount) {
                best = material;
                bestCount = count;
            }
        }
        return best;
    }

    // ---- the lights ----------------------------------------------------------------------------------------------------

    private void glow(Player player, Search search) {
        int lit = 0;
        for (Found found : search.found) {
            if (lit++ >= MAX_GLOWING) break;
            found.glow = found.block.getWorld().spawn(found.block.getLocation(), BlockDisplay.class, display -> {
                display.setBlock(Material.SPAWNER.createBlockData());
                display.setPersistent(false);
                display.setVisibleByDefault(false);
                display.setGlowing(true);
                display.setGlowColorOverride(GLOW);
                display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                display.setViewRange(4.0f);
                // a little bigger than the block, so the outline is not hidden inside it
                display.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-0.01f), new org.joml.Quaternionf(),
                        new org.joml.Vector3f(1.02f), new org.joml.Quaternionf()));
            });
            player.showEntity(plugin, found.glow);
        }
        search.lightsOut = Bukkit.getScheduler().runTaskLater(plugin, () -> lightsOut(search), GLOW_TICKS);
    }

    private static void lightsOut(Search search) {
        if (search.lightsOut != null) search.lightsOut.cancel();
        search.lightsOut = null;
        for (Found found : search.found) {
            if (found.glow != null) found.glow.remove();
            found.glow = null;
        }
    }

    public void clear(Player player) {
        Search search = searches.get(player.getUniqueId());
        if (search != null) lightsOut(search);
        player.sendMessage(Component.text("[スポナー] 光を消した。", NamedTextColor.GRAY));
    }

    // ---- the list ----------------------------------------------------------------------------------------------------------

    public void page(Player player, int page) {
        Search search = searches.get(player.getUniqueId());
        if (search == null || search.found.isEmpty()) {
            player.sendMessage(Component.text("[スポナー] スポナーブロックは見つかっていません。/spawner blocks [半径チャンク] で探す", NamedTextColor.YELLOW));
            return;
        }
        int pages = (search.found.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int shown = Math.max(1, Math.min(pages, page));
        long left = search.found.stream().filter(found -> !found.deleted).count();
        long buried = search.found.stream().filter(found -> !found.deleted && found.buried).count();
        player.sendMessage(Component.text("[スポナー] スポナーブロック " + left + " 個 (うち埋まっている " + buried + " 個)。光は 5 分、自分にだけ見える"
                + (search.found.size() > MAX_GLOWING ? " (光るのは近い " + MAX_GLOWING + " 個)" : ""), NamedTextColor.YELLOW));
        Location here = player.getLocation();
        for (int index = (shown - 1) * PAGE_SIZE; index < Math.min(search.found.size(), shown * PAGE_SIZE); index++) {
            Found found = search.found.get(index);
            int number = index + 1;
            Block block = found.block;
            String where = block.getX() + ", " + block.getY() + ", " + block.getZ();
            TextComponent.Builder line = Component.text().append(Component.text("#" + number + " ", NamedTextColor.GOLD));
            if (found.deleted) {
                line.append(Component.text(where + "  消した", NamedTextColor.DARK_GRAY));
            } else {
                String distance = here.getWorld().equals(block.getWorld())
                        ? Math.round(here.distance(block.getLocation().add(0.5, 0.5, 0.5))) + "m" : block.getWorld().getName();
                line.append(Component.text(where + "  " + spawnsName(found.spawns) + "  " + distance, NamedTextColor.GRAY));
                if (found.buried) line.append(Component.text("  埋まっている", NamedTextColor.RED));
                line.append(Component.text("  ")).append(button("[TP]", NamedTextColor.AQUA, "/spawner blocks tp " + number, "そこへテレポート"));
                line.append(Component.text(" ")).append(button("[消す]", NamedTextColor.RED, "/spawner blocks delete " + number,
                        "このスポナーを消す (まわりと同じブロックになる。/spawner blocks undo で戻せる)"));
            }
            player.sendMessage(line.build());
        }
        TextComponent.Builder footer = Component.text();
        if (pages > 1) {
            if (shown > 1) footer.append(button("[前へ]", NamedTextColor.WHITE, "/spawner blocks page " + (shown - 1), shown - 1 + " ページ目")).append(Component.text(" "));
            footer.append(Component.text(shown + "/" + pages + " ", NamedTextColor.GRAY));
            if (shown < pages) footer.append(button("[次へ]", NamedTextColor.WHITE, "/spawner blocks page " + (shown + 1), shown + 1 + " ページ目")).append(Component.text(" "));
        }
        footer.append(button("[全部消す]", NamedTextColor.DARK_RED, "/spawner blocks deleteall", "一覧のスポナーを全部消す (確認あり)"))
                .append(Component.text(" "))
                .append(button("[埋まっているのだけ消す]", NamedTextColor.RED, "/spawner blocks deleteburied", "埋まっているスポナーだけ消す (確認あり)"))
                .append(Component.text(" "))
                .append(button("[元に戻す]", NamedTextColor.GREEN, "/spawner blocks undo", "最後に消したものを戻す"))
                .append(Component.text(" "))
                .append(button("[光を消す]", NamedTextColor.GRAY, "/spawner blocks clear", "光る枠を消す"));
        player.sendMessage(footer.build());
    }

    private static Component button(String text, NamedTextColor color, String command, String hover) {
        return Component.text(text, color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private static String spawnsName(EntityType type) {
        return type == null ? "(空)" : type.getKey().getKey();
    }

    // ---- teleport, delete, undo ----------------------------------------------------------------------------------------

    private Found at(Player player, int number) {
        Search search = searches.get(player.getUniqueId());
        if (search == null || number < 1 || number > search.found.size()) {
            player.sendMessage(Component.text("[スポナー] その番号はありません。/spawner blocks で探し直してください。", NamedTextColor.RED));
            return null;
        }
        return search.found.get(number - 1);
    }

    public void teleport(Player player, int number) {
        Found found = at(player, number);
        if (found == null) return;
        Block block = found.block;
        // the first two open blocks above it (a buried spawner: on the ground over it), looking down at it
        Block feet = block.getRelative(BlockFace.UP);
        int top = block.getWorld().getMaxHeight();
        while (feet.getY() < top - 1 && !(feet.isPassable() && feet.getRelative(BlockFace.UP).isPassable())) {
            feet = feet.getRelative(BlockFace.UP);
        }
        Location to = feet.getLocation().add(0.5, 0.0, 0.5);
        to.setPitch(90.0f);
        to.setYaw(player.getLocation().getYaw());
        player.teleport(to);
    }

    public void delete(Player player, int number) {
        Found found = at(player, number);
        if (found == null) return;
        if (found.deleted) {
            player.sendMessage(Component.text("[スポナー] #" + number + " はもう消してあります。", NamedTextColor.GRAY));
            return;
        }
        List<BlockState> batch = new ArrayList<>();
        if (remove(found, batch)) {
            searches.get(player.getUniqueId()).undo.add(batch);
            player.sendMessage(Component.text("[スポナー] #" + number + " を消した。", NamedTextColor.GREEN)
                    .append(Component.text(" ")).append(button("[元に戻す]", NamedTextColor.GREEN, "/spawner blocks undo", "戻す")));
        } else {
            player.sendMessage(Component.text("[スポナー] #" + number + " はもうスポナーではありません (誰かが変えた)。", NamedTextColor.GRAY));
        }
    }

    /** Deletes every one in the list (or only the buried ones): the first click asks, the second within 30 seconds does it. */
    public void deleteAll(Player player, boolean onlyBuried, boolean confirmed) {
        Search search = searches.get(player.getUniqueId());
        if (search == null || search.found.isEmpty()) {
            page(player, 1);
            return;
        }
        long count = search.found.stream().filter(found -> !found.deleted && (!onlyBuried || found.buried)).count();
        if (count == 0) {
            player.sendMessage(Component.text("[スポナー] 消すものはありません。", NamedTextColor.GRAY));
            return;
        }
        String command = onlyBuried ? "/spawner blocks deleteburied" : "/spawner blocks deleteall";
        if (!confirmed || !search.confirmAll) {
            search.confirmAll = true;
            Bukkit.getScheduler().runTaskLater(plugin, () -> search.confirmAll = false, 20L * 30);
            player.sendMessage(Component.text("[スポナー] " + (onlyBuried ? "埋まっている" : "一覧の") + "スポナー " + count + " 個を消します。", NamedTextColor.YELLOW)
                    .append(Component.text(" ")).append(button("[消す]", NamedTextColor.DARK_RED, command + " confirm", "30 秒以内にクリック")));
            return;
        }
        search.confirmAll = false;
        List<BlockState> batch = new ArrayList<>();
        int done = 0;
        for (Found found : search.found) {
            if (found.deleted || (onlyBuried && !found.buried)) continue;
            if (remove(found, batch)) done++;
        }
        if (!batch.isEmpty()) search.undo.add(batch);
        player.sendMessage(Component.text("[スポナー] " + done + " 個を消した。", NamedTextColor.GREEN)
                .append(Component.text(" ")).append(button("[元に戻す]", NamedTextColor.GREEN, "/spawner blocks undo", "今消した分を全部戻す")));
    }

    private static boolean remove(Found found, List<BlockState> batch) {
        found.deleted = true;
        if (found.glow != null) found.glow.remove();
        found.glow = null;
        Block block = found.block;
        if (block.getType() != Material.SPAWNER) return false;
        // the block as it is now (with what it spawns and its timings), to put back on undo
        batch.add(block.getState());
        block.setType(fill(neighbours(block)), false);
        return true;
    }

    public void undo(Player player) {
        Search search = searches.get(player.getUniqueId());
        if (search == null || search.undo.isEmpty()) {
            player.sendMessage(Component.text("[スポナー] 戻せるものはありません。", NamedTextColor.GRAY));
            return;
        }
        List<BlockState> batch = search.undo.remove(search.undo.size() - 1);
        for (BlockState state : batch) {
            state.update(true, false);
            for (Found found : search.found) {
                if (found.block.equals(state.getBlock())) found.deleted = false;
            }
        }
        player.sendMessage(Component.text("[スポナー] " + batch.size() + " 個を戻した (光は /spawner blocks で探し直すと付く)。", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Search search = searches.remove(event.getPlayer().getUniqueId());
        if (search != null) lightsOut(search);
    }
}
