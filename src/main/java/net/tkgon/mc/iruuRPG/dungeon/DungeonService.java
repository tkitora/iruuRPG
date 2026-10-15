package net.tkgon.mc.iruuRPG.dungeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Instance dungeons: every run copies the dungeon's template world (dungeon_template_&lt;id&gt;) into a world of its
 * own (dungeon_instance_&lt;id&gt;_&lt;n&gt;), places the mobs, and deletes the copy when the run ends. Clearing = every
 * placed mob is dead. Solo for now; the data is kept per player so parties can be added later.
 */
public final class DungeonService implements Listener {

    public static final String ADMIN_PERMISSION = "iruurpg.admin";
    private static final String INSTANCE_PREFIX = "dungeon_instance_";
    private static final Set<String> SKIPPED_FILES = Set.of("uid.dat", "session.lock");
    private static final Set<String> SKIPPED_FOLDERS = Set.of("playerdata", "stats", "advancements");

    public enum State { CREATING, ACTIVE, CLEARED, FAILED, CLOSING }

    /** One run of a dungeon in its own world. */
    public static final class Instance {
        private final int number;
        private final DungeonDefinition definition;
        private final String worldName;
        private World world;
        private State state = State.CREATING;
        private final Set<UUID> players = new HashSet<>();
        private final Map<UUID, Integer> lives = new HashMap<>();
        private final Set<UUID> mobs = new HashSet<>();
        private long deadline;
        private int emptySeconds;
        private int closeCountdown = -1;

        private Instance(int number, DungeonDefinition definition) {
            this.number = number;
            this.definition = definition;
            this.worldName = INSTANCE_PREFIX + definition.id() + "_" + number;
        }

        public DungeonDefinition definition() {
            return definition;
        }

        public State state() {
            return state;
        }
    }

    private final JavaPlugin plugin;
    private final DungeonRegistry registry;
    private final RpgMobService mobService;
    private final LevelService levelService;
    private final PlayerProfileManager profileManager;
    private final NamespacedKey instanceKey;
    private final File stateFile;

    private final Map<Integer, Instance> instances = new LinkedHashMap<>();
    private final Map<UUID, Instance> byPlayer = new HashMap<>();
    /** Where each player came from (also kept in dungeon-state.yml so a crash can be undone). */
    private final Map<UUID, Location> returnTo = new HashMap<>();
    /** Admins building a template: where to send them back. */
    private final Map<UUID, Location> builders = new HashMap<>();
    /** Players who died with no lives left: sent home when they respawn. */
    private final Map<UUID, Location> sendHomeOnRespawn = new HashMap<>();
    private int nextNumber = 1;
    private BukkitTask task;

    public DungeonService(JavaPlugin plugin, DungeonRegistry registry, RpgMobService mobService, LevelService levelService,
                          PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.mobService = mobService;
        this.levelService = levelService;
        this.profileManager = profileManager;
        this.instanceKey = new NamespacedKey(plugin, "dungeon_instance");
        this.stateFile = new File(plugin.getDataFolder(), "dungeon-state.yml");
    }

    public DungeonRegistry registry() {
        return registry;
    }

    // ---- settings --------------------------------------------------------------------------------------------------

    private int maxInstances() {
        return Math.max(1, plugin.getConfig().getInt("dungeon.max-instances", 5));
    }

    private int closeDelaySeconds() {
        return Math.max(1, plugin.getConfig().getInt("dungeon.close-delay-seconds", 20));
    }

    private int emptyCloseSeconds() {
        return Math.max(5, plugin.getConfig().getInt("dungeon.empty-close-seconds", 60));
    }

    private List<String> blockedCommands() {
        List<String> list = plugin.getConfig().getStringList("dungeon.blocked-commands");
        return list.isEmpty() && !plugin.getConfig().isSet("dungeon.blocked-commands")
                ? List.of("trade", "market", "fm", "tutorialstart") : list;
    }

    // ---- life cycle -------------------------------------------------------------------------------------------------

    /** Startup: remove copies left behind by a crash, start the timer. */
    public void start() {
        File container = Bukkit.getWorldContainer();
        File[] leftovers = container.listFiles((dir, name) -> name.startsWith(INSTANCE_PREFIX));
        if (leftovers != null) {
            for (File folder : leftovers) {
                World loaded = Bukkit.getWorld(folder.getName());
                if (loaded != null) Bukkit.unloadWorld(loaded, false);
                deleteFolder(folder);
                plugin.getLogger().info("dungeon: removed a leftover instance " + folder.getName());
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Shutdown: everyone out, every copy unloaded and deleted (synchronously). */
    public void shutdown() {
        if (task != null) task.cancel();
        for (Instance instance : new ArrayList<>(instances.values())) {
            closeNow(instance, false);
        }
        for (Map.Entry<UUID, Location> entry : new HashMap<>(builders).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.teleport(entry.getValue());
        }
        builders.clear();
    }

    public Instance instanceOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public boolean isInstanceWorld(World world) {
        return world != null && world.getName().startsWith(INSTANCE_PREFIX);
    }

    // ---- entering ---------------------------------------------------------------------------------------------------

    /** Why the player cannot enter now, or null. */
    public String whyNot(Player player, DungeonDefinition definition) {
        if (byPlayer.containsKey(player.getUniqueId())) return "すでにダンジョンの中にいます。";
        if (!definition.ready()) return "このダンジョンは準備中です。";
        if (profileManager.getOrCreate(player).level() < definition.minLevel()) return "レベルが足りません。(必要: Lv" + definition.minLevel() + ")";
        if (instances.size() >= maxInstances()) return "いまは混み合っています。少し待ってください。";
        if (!new File(Bukkit.getWorldContainer(), definition.templateWorldName()).isDirectory()) return "このダンジョンのワールドがまだありません。";
        return null;
    }

    public void enter(Player player, String id) {
        DungeonDefinition definition = registry.find(id).orElse(null);
        if (definition == null) {
            player.sendMessage(Component.text("[ダンジョン] そのダンジョンはありません: " + id, NamedTextColor.RED));
            return;
        }
        String problem = whyNot(player, definition);
        if (problem != null) {
            player.sendMessage(Component.text("[ダンジョン] " + problem, NamedTextColor.RED));
            return;
        }

        Instance instance = new Instance(nextFreeNumber(definition), definition);
        instances.put(instance.number, instance);
        instance.players.add(player.getUniqueId());
        instance.lives.put(player.getUniqueId(), definition.lives());
        byPlayer.put(player.getUniqueId(), instance);
        remember(player, player.getLocation());
        player.sendMessage(Component.text("[ダンジョン] 「" + definition.name() + "」を準備しています…", NamedTextColor.YELLOW));
        player.closeInventory();

        // the template is saved first (it may be loaded while someone builds it), then copied off the main thread
        World template = Bukkit.getWorld(definition.templateWorldName());
        if (template != null) template.save();
        File source = new File(Bukkit.getWorldContainer(), definition.templateWorldName());
        File target = new File(Bukkit.getWorldContainer(), instance.worldName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean copied = copyWorld(source.toPath(), target.toPath());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!copied) {
                    abort(instance, "ワールドのコピーに失敗しました。");
                    return;
                }
                open(instance);
            });
        });
    }

    private int nextFreeNumber(DungeonDefinition definition) {
        while (true) {
            int number = nextNumber++;
            if (instances.containsKey(number)) continue;
            if (new File(Bukkit.getWorldContainer(), INSTANCE_PREFIX + definition.id() + "_" + number).exists()) continue;
            return number;
        }
    }

    /** Main thread: load the copied world, place the mobs, bring the player in. */
    private void open(Instance instance) {
        if (instance.state != State.CREATING) return;
        World world = new WorldCreator(instance.worldName).generator(new VoidChunkGenerator()).createWorld();
        if (world == null) {
            abort(instance, "ワールドを開けませんでした。");
            return;
        }
        instance.world = world;
        world.setAutoSave(false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);

        for (DungeonDefinition.MobSpawn spawn : instance.definition.spawns()) {
            Location location = spawn.in(world);
            location.getChunk().load();
            mobService.spawnAt(location, spawn.mobId()).ifPresentOrElse(entity -> {
                entity.getPersistentDataContainer().set(instanceKey, PersistentDataType.INTEGER, instance.number);
                instance.mobs.add(entity.getUniqueId());
            }, () -> plugin.getLogger().warning("dungeon " + instance.definition.id() + ": unknown mob '" + spawn.mobId() + "'"));
        }
        if (instance.mobs.isEmpty()) {
            abort(instance, "敵を置けませんでした。(設定を確認してください)");
            return;
        }

        instance.state = State.ACTIVE;
        instance.deadline = System.currentTimeMillis() + instance.definition.timeLimitSeconds() * 1000L;
        Location entry = instance.definition.entry().in(world);
        for (UUID id : new ArrayList<>(instance.players)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            player.teleport(entry);
            player.showTitle(Title.title(Component.text(instance.definition.name(), NamedTextColor.GOLD),
                    Component.text("敵をすべて倒せ (制限 " + clock(instance.definition.timeLimitSeconds()) + ")", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.7f, 0.8f);
        }
        plugin.getLogger().info("dungeon: opened " + instance.worldName + " (" + instance.mobs.size() + " mobs)");
    }

    /** Something went wrong before the run began: everyone back, copy removed. */
    private void abort(Instance instance, String reason) {
        for (UUID id : new ArrayList<>(instance.players)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(Component.text("[ダンジョン] 入れませんでした: " + reason, NamedTextColor.RED));
        }
        closeNow(instance, true);
    }

    // ---- leaving / ending -------------------------------------------------------------------------------------------------

    public void leave(Player player) {
        Instance instance = byPlayer.get(player.getUniqueId());
        if (instance == null) {
            player.sendMessage(Component.text("[ダンジョン] ダンジョンの中にいません。", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("[ダンジョン] ダンジョンから出た。", NamedTextColor.GRAY));
        sendHome(player, instance);
        if (instance.players.isEmpty() && instance.state == State.ACTIVE) {
            instance.state = State.FAILED;
            instance.closeCountdown = 0;
        }
    }

    /** Takes the player out of the run and back to where they came from. */
    private void sendHome(Player player, Instance instance) {
        UUID id = player.getUniqueId();
        instance.players.remove(id);
        instance.lives.remove(id);
        byPlayer.remove(id);
        Location home = returnTo.remove(id);
        forget(id);
        if (player.isOnline() && player.isDead()) {
            // still on the death screen: send them on respawn
            sendHomeOnRespawn.put(id, home == null ? fallback() : home);
            return;
        }
        if (player.isOnline()) player.teleport(home == null ? fallback() : home);
    }

    private void clear(Instance instance) {
        if (instance.state != State.ACTIVE) return;
        instance.state = State.CLEARED;
        instance.closeCountdown = closeDelaySeconds();
        DungeonDefinition definition = instance.definition;
        for (UUID id : instance.players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            if (definition.rewardGold() > 0) {
                PlayerProfile profile = profileManager.getOrCreate(player);
                profile.setCurrency(profile.currency() + definition.rewardGold());
                profileManager.save(player);
            }
            if (definition.rewardXp() > 0) levelService.addXp(player, definition.rewardXp());
            player.showTitle(Title.title(Component.text("CLEAR!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("報酬: " + definition.rewardGold() + " " + currency() + " / 経験値 " + definition.rewardXp(), NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(800))));
            player.sendMessage(Component.text("[ダンジョン] クリア! " + closeDelaySeconds() + " 秒後に元の場所へ戻る。(/dungeon leave ですぐ戻れる)", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
        }
        plugin.getLogger().info("dungeon: cleared " + instance.worldName);
    }

    private void fail(Instance instance, String reason) {
        if (instance.state != State.ACTIVE) return;
        instance.state = State.FAILED;
        instance.closeCountdown = 0;
        for (UUID id : new ArrayList<>(instance.players)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            player.showTitle(Title.title(Component.text("FAILED", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(reason, NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
            player.sendMessage(Component.text("[ダンジョン] 失敗: " + reason, NamedTextColor.RED));
            sendHome(player, instance);
        }
        plugin.getLogger().info("dungeon: failed " + instance.worldName + " (" + reason + ")");
    }

    /** Ends the run right away: players out, mobs removed, world unloaded and deleted. */
    private void closeNow(Instance instance, boolean async) {
        if (instance.state == State.CLOSING) return;
        instance.state = State.CLOSING;
        for (UUID id : new ArrayList<>(instance.players)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                sendHome(player, instance);
            } else {
                // offline: they are sent home when they log in (dungeon-state.yml still has the place)
                instance.players.remove(id);
                byPlayer.remove(id);
            }
        }
        instances.remove(instance.number);

        World world = instance.world != null ? instance.world : Bukkit.getWorld(instance.worldName);
        if (world != null) {
            for (Player player : new ArrayList<>(world.getPlayers())) {
                Location home = returnTo.remove(player.getUniqueId());
                forget(player.getUniqueId());
                player.teleport(home == null ? fallback() : home);
            }
            for (Entity entity : world.getEntities()) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    mobService.remove(entity);
                    entity.remove();
                }
            }
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().warning("dungeon: could not unload " + instance.worldName + "; it is deleted at the next start");
                return;
            }
        }
        File folder = new File(Bukkit.getWorldContainer(), instance.worldName);
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteFolder(folder));
        } else {
            deleteFolder(folder);
        }
    }

    // ---- every second ------------------------------------------------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        for (Instance instance : new ArrayList<>(instances.values())) {
            switch (instance.state) {
                case ACTIVE -> tickActive(instance, now);
                case CLEARED, FAILED -> {
                    if (instance.state == State.CLEARED) {
                        for (UUID id : instance.players) {
                            Player player = Bukkit.getPlayer(id);
                            if (player != null) player.sendActionBar(Component.text("クリア! あと " + instance.closeCountdown + " 秒で戻る", NamedTextColor.GOLD));
                        }
                    }
                    // a player still on the death screen cannot be moved; wait until they respawn
                    boolean someoneDead = instance.world != null && instance.world.getPlayers().stream().anyMatch(Player::isDead);
                    if (instance.closeCountdown <= 0 && someoneDead) continue;
                    if (instance.closeCountdown-- <= 0) closeNow(instance, true);
                }
                default -> {
                }
            }
        }
    }

    private void tickActive(Instance instance, long now) {
        instance.mobs.removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            return entity == null || entity.isDead() || !entity.isValid();
        });
        if (instance.mobs.isEmpty()) {
            clear(instance);
            return;
        }
        long left = instance.deadline - now;
        if (left <= 0) {
            fail(instance, "時間切れ");
            return;
        }

        int present = 0;
        for (UUID id : instance.players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.getWorld().equals(instance.world)) continue;
            present++;
            int lives = instance.lives.getOrDefault(id, 0);
            player.sendActionBar(Component.text("残り " + clock((int) (left / 1000)), left < 60_000 ? NamedTextColor.RED : NamedTextColor.WHITE)
                    .append(Component.text("  敵 " + instance.mobs.size(), NamedTextColor.GOLD))
                    .append(Component.text("  残機 " + lives, NamedTextColor.AQUA)));
        }
        if (present == 0) {
            if (++instance.emptySeconds >= emptyCloseSeconds()) {
                instance.state = State.FAILED;
                instance.closeCountdown = 0;
                plugin.getLogger().info("dungeon: closing empty " + instance.worldName);
            }
        } else {
            instance.emptySeconds = 0;
        }
    }

    // ---- mobs and players dying ----------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        Integer number = event.getEntity().getPersistentDataContainer().get(instanceKey, PersistentDataType.INTEGER);
        if (number == null) return;
        Instance instance = instances.get(number);
        if (instance == null) return;
        instance.mobs.remove(event.getEntity().getUniqueId());
        if (instance.mobs.isEmpty()) clear(instance);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Instance instance = byPlayer.get(player.getUniqueId());
        if (instance == null) return;

        // nothing is lost inside a dungeon
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (instance.state != State.ACTIVE) return;

        // lives: each death uses one and the player comes back at the entry; with none left they are out
        UUID id = player.getUniqueId();
        int left = instance.lives.getOrDefault(id, 0);
        if (left > 0) {
            instance.lives.put(id, left - 1);
            player.sendMessage(Component.text("[ダンジョン] 倒れた。残機 " + (left - 1), NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("[ダンジョン] 残機がなくなった。元の場所へ戻る。", NamedTextColor.RED));
        instance.players.remove(id);
        instance.lives.remove(id);
        byPlayer.remove(id);
        Location home = returnTo.remove(id);
        forget(id);
        sendHomeOnRespawn.put(id, home == null ? fallback() : home);
        if (instance.players.isEmpty()) {
            instance.state = State.FAILED;
            instance.closeCountdown = 0;
            plugin.getLogger().info("dungeon: failed " + instance.worldName + " (no lives left)");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location home = sendHomeOnRespawn.remove(player.getUniqueId());
        if (home != null) {
            event.setRespawnLocation(home);
            return;
        }
        Instance instance = byPlayer.get(player.getUniqueId());
        if (instance != null && instance.world != null && instance.state == State.ACTIVE) {
            event.setRespawnLocation(instance.definition.entry().in(instance.world));
        }
    }

    // ---- leaving by other means, logging in and out ------------------------------------------------------------------------

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Instance instance = byPlayer.get(player.getUniqueId());
        if (instance == null || instance.world == null) return;
        if (event.getFrom().equals(instance.world) && !player.getWorld().equals(instance.world)) {
            // left the instance world some other way: treat it as leaving (no teleport back)
            instance.players.remove(player.getUniqueId());
            instance.lives.remove(player.getUniqueId());
            byPlayer.remove(player.getUniqueId());
            returnTo.remove(player.getUniqueId());
            forget(player.getUniqueId());
            player.sendMessage(Component.text("[ダンジョン] ダンジョンから出た。", NamedTextColor.GRAY));
            if (instance.players.isEmpty() && instance.state == State.ACTIVE) {
                instance.state = State.FAILED;
                instance.closeCountdown = 0;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location saved = savedReturn(player.getUniqueId());
        if (saved == null) return;
        Instance instance = byPlayer.get(player.getUniqueId());
        if (instance != null && instance.state == State.ACTIVE && player.getWorld().equals(instance.world)) {
            return; // came back into a run that is still going
        }
        // the run is gone (closed, or the server restarted): back to where they came from
        if (instance != null) {
            instance.players.remove(player.getUniqueId());
            byPlayer.remove(player.getUniqueId());
        }
        returnTo.remove(player.getUniqueId());
        forget(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.teleport(saved);
            player.sendMessage(Component.text("[ダンジョン] ダンジョンが終わっていたので、元の場所に戻った。", NamedTextColor.GRAY));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // the run keeps going for a while (it closes when nobody is inside); the saved place stays in the file
        builders.remove(event.getPlayer().getUniqueId());
    }

    // ---- rules inside an instance ---------------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isInstanceWorld(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isInstanceWorld(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isInstanceWorld(event.getLocation().getWorld())) event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isInstanceWorld(event.getBlock().getWorld())) event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isInstanceWorld(event.getFrom().getWorld())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || !isInstanceWorld(player.getWorld())) return;
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (blockedCommands().contains(label)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("[ダンジョン] ダンジョンの中では使えません。", NamedTextColor.RED));
        }
    }

    // ---- building templates (admins) -----------------------------------------------------------------------------------------

    /** Opens (or creates) the template world and takes the admin there. */
    public void build(Player admin, DungeonDefinition definition) {
        World world = Bukkit.getWorld(definition.templateWorldName());
        boolean fresh = !new File(Bukkit.getWorldContainer(), definition.templateWorldName()).isDirectory();
        if (world == null) {
            world = new WorldCreator(definition.templateWorldName()).generator(new VoidChunkGenerator()).createWorld();
        }
        if (world == null) {
            admin.sendMessage(Component.text("[ダンジョン] ワールドを作れませんでした。", NamedTextColor.RED));
            return;
        }
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        if (fresh) {
            // a small floor to stand on
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) world.getBlockAt(x, 64, z).setType(Material.STONE);
            }
        }
        if (!world.equals(admin.getWorld())) builders.putIfAbsent(admin.getUniqueId(), admin.getLocation());
        admin.teleport(definition.entry().in(world));
        admin.sendMessage(Component.text("[ダンジョン] 「" + definition.name() + "」のひな形にいる。建てたら /dungeon entry・spawn、終わったら /dungeon save " + definition.id(), NamedTextColor.YELLOW));
    }

    /** Saves the template world, sends the admin back, and unloads the world if nobody is left in it. */
    public void saveTemplate(Player admin, DungeonDefinition definition) {
        World world = Bukkit.getWorld(definition.templateWorldName());
        if (world == null) {
            admin.sendMessage(Component.text("[ダンジョン] ひな形のワールドは開かれていません。(保存済み)", NamedTextColor.GRAY));
            return;
        }
        world.save();
        Location back = builders.remove(admin.getUniqueId());
        if (admin.getWorld().equals(world)) admin.teleport(back == null ? fallback() : back);
        if (world.getPlayers().isEmpty()) Bukkit.unloadWorld(world, true);
        admin.sendMessage(Component.text("[ダンジョン] ひな形を保存した。", NamedTextColor.GREEN));
    }

    // ---- state file (where to send players back after a crash) ----------------------------------------------------------------

    private void remember(Player player, Location location) {
        returnTo.put(player.getUniqueId(), location.clone());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        String base = "players." + player.getUniqueId() + ".";
        yaml.set(base + "world", location.getWorld() == null ? null : location.getWorld().getName());
        yaml.set(base + "x", location.getX());
        yaml.set(base + "y", location.getY());
        yaml.set(base + "z", location.getZ());
        yaml.set(base + "yaw", (double) location.getYaw());
        yaml.set(base + "pitch", (double) location.getPitch());
        saveState(yaml);
    }

    private void forget(UUID id) {
        if (!stateFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        if (!yaml.contains("players." + id)) return;
        yaml.set("players." + id, null);
        saveState(yaml);
    }

    private Location savedReturn(UUID id) {
        Location live = returnTo.get(id);
        if (live != null) return live;
        if (!stateFile.exists()) return null;
        ConfigurationSection section = YamlConfiguration.loadConfiguration(stateFile).getConfigurationSection("players." + id);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return fallback();
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    private void saveState(YamlConfiguration yaml) {
        try {
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("dungeon: could not save " + stateFile.getName() + ": " + e.getMessage());
        }
    }

    // ---- files -------------------------------------------------------------------------------------------------------------------

    /** Copies a world folder, leaving out what must not be shared (uid.dat, the lock, player data). */
    private boolean copyWorld(Path source, Path target) {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path relative = source.relativize(path);
                if (relative.getNameCount() > 0 && SKIPPED_FOLDERS.contains(relative.getName(0).toString())) continue;
                if (SKIPPED_FILES.contains(path.getFileName().toString())) continue;
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("dungeon: copy failed " + source + " -> " + target + ": " + e.getMessage());
            deleteFolder(target.toFile());
            return false;
        }
    }

    /** Deletes an instance folder; only folders named dungeon_instance_* right inside the world container. */
    private void deleteFolder(File folder) {
        File container = Bukkit.getWorldContainer().getAbsoluteFile();
        File absolute = folder.getAbsoluteFile();
        if (!absolute.getName().startsWith(INSTANCE_PREFIX) || !container.equals(absolute.getParentFile())) {
            plugin.getLogger().warning("dungeon: refusing to delete " + absolute);
            return;
        }
        if (!absolute.exists()) return;
        try (Stream<Path> paths = Files.walk(absolute.toPath())) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("dungeon: could not delete " + absolute + ": " + e.getMessage());
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------------------

    private Location fallback() {
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private String currency() {
        return plugin.getConfig().getString("quest.currency-name", "ゴールド");
    }

    static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        return (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }
}
