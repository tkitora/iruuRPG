package net.tkgon.mc.iruuRPG.dungeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.tkgon.mc.iruuRPG.gui.RewardChestMenu;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.mob.RpgMobDefinition;
import net.tkgon.mc.iruuRPG.mob.RpgMobRegistry;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Particle;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.event.ClickEvent;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.block.Action;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerMoveEvent;
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
    /** Mobs with this tag drop nothing when they die (dungeon bosses: the rewards are in the reward room). */
    public static final String NO_DROPS_TAG = "iruurpg_no_drops";
    private static final double BOSS_TRIGGER_RANGE = 3.0;
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
        private final Set<UUID> bosses = new HashSet<>();
        private final Map<UUID, Downed> downed = new HashMap<>();
        private final Map<UUID, Long> exitNotice = new HashMap<>();
        private final Set<UUID> chestOpened = new HashSet<>();
        /** road chest index -> players who opened it */
        private final Map<Integer, Set<UUID>> roadOpened = new HashMap<>();
        private boolean bossRoomOpened;
        private TextDisplay triggerLabel;
        private TextDisplay exitLabel;
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

        public int playerCount() {
            return players.size();
        }
    }

    /** A fallen player in a revive dungeon: a spectator near the body until a teammate stands by it for 5 seconds. */
    private static final class Downed {
        private final Location corpse;
        private final GameMode before;
        private TextDisplay label;
        private int progress;

        private Downed(Location corpse, GameMode before) {
            this.corpse = corpse;
            this.before = before;
        }
    }

    private record Invite(int instance, UUID inviter, long until) {
    }

    private static final int REVIVE_SECONDS = 5;
    private static final double REVIVE_RANGE = 2.0;
    private static final double SPECTATE_RANGE = 24.0;
    private static final double EXIT_RANGE = 1.6;

    private final JavaPlugin plugin;
    private final DungeonRegistry registry;
    private final RpgMobService mobService;
    private final RpgMobRegistry mobRegistry;
    /** template world name -> the marker labels shown while building it */
    private final Map<String, List<Entity>> markers = new HashMap<>();
    /** The clickable box of a placed-mob marker -> which dungeon and which spawn (0-based). */
    private final Map<UUID, MarkerRef> markerRefs = new HashMap<>();

    public record MarkerRef(String dungeonId, int index) {
    }
    private RpgItemRegistry itemRegistry;
    private RpgItemFactory itemFactory;
    private RewardChestMenu rewardChestMenu;
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
    /** invited player -> the invite */
    private final Map<UUID, Invite> invites = new HashMap<>();
    private int nextNumber = 1;
    private BukkitTask task;

    public DungeonService(JavaPlugin plugin, DungeonRegistry registry, RpgMobService mobService, RpgMobRegistry mobRegistry,
                          LevelService levelService, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.mobRegistry = mobRegistry;
        this.mobService = mobService;
        this.levelService = levelService;
        this.profileManager = profileManager;
        this.instanceKey = new NamespacedKey(plugin, "dungeon_instance");
        this.stateFile = new File(plugin.getDataFolder(), "dungeon-state.yml");
    }

    /** What the reward chest needs to hand out items. */
    public void setRewardTools(RpgItemRegistry itemRegistry, RpgItemFactory itemFactory, RewardChestMenu rewardChestMenu) {
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
        this.rewardChestMenu = rewardChestMenu;
    }

    public DungeonRegistry registry() {
        return registry;
    }

    // ---- settings --------------------------------------------------------------------------------------------------

    private int maxInstances() {
        return Math.max(1, plugin.getConfig().getInt("dungeon.max-instances", 5));
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
        for (String world : new ArrayList<>(markers.keySet())) clearMarkers(world);
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
                if (spawn.mobId().equals(instance.definition.boss())) {
                    instance.bosses.add(entity.getUniqueId());
                    entity.addScoreboardTag(NO_DROPS_TAG); // the boss's rewards are in the reward room
                }
            }, () -> plugin.getLogger().warning("dungeon " + instance.definition.id() + ": unknown mob '" + spawn.mobId() + "'"));
        }
        if (instance.mobs.isEmpty() || (instance.definition.boss() != null && instance.bosses.isEmpty())) {
            abort(instance, "敵を置けませんでした。(設定を確認してください)");
            return;
        }
        for (DungeonDefinition.Point road : instance.definition.roadChests()) {
            Block block = road.in(world).getBlock();
            block.getChunk().load();
            if (block.getType() != Material.CHEST) block.setType(Material.CHEST);
        }
        if (instance.definition.bossTrigger() != null && instance.definition.bossRoom() != null) {
            Location at = instance.definition.bossTrigger().in(world).add(0, 2.0, 0);
            at.getChunk().load();
            instance.triggerLabel = world.spawn(at, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setPersistent(false);
                display.text(triggerText(0, 1));
            });
        }
        if (instance.definition.chest() != null) {
            Block block = instance.definition.chest().in(world).getBlock();
            block.getChunk().load();
            if (block.getType() != Material.CHEST) block.setType(Material.CHEST);
        }
        if (instance.definition.exit() != null) {
            Location at = instance.definition.exit().in(world).add(0, 1.6, 0);
            at.getChunk().load();
            instance.exitLabel = world.spawn(at, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setPersistent(false);
                display.text(exitText(false));
            });
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
        if (player.isOnline()) undown(instance, player);
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
        instance.closeCountdown = instance.definition.closeDelaySeconds();
        DungeonDefinition definition = instance.definition;
        for (UUID id : new ArrayList<>(instance.downed.keySet())) revive(instance, id, false);
        if (instance.exitLabel != null && instance.exitLabel.isValid()) instance.exitLabel.text(exitText(true));
        boolean chest = definition.chest() != null;
        boolean room = definition.rewardRoom() != null;
        for (UUID id : instance.players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            if (!chest) {
                // no reward chest: the rewards are given right away
                if (definition.rewardGold() > 0) {
                    PlayerProfile profile = profileManager.getOrCreate(player);
                    profile.setCurrency(profile.currency() + definition.rewardGold());
                    profileManager.save(player);
                }
                if (definition.rewardXp() > 0) levelService.addXp(player, definition.rewardXp());
            }
            String sub = room ? "5秒後に報酬部屋へ移動する" : chest ? "報酬の宝箱を開けよう"
                    : "報酬: " + definition.rewardGold() + " " + currency() + " / 経験値 " + definition.rewardXp();
            player.showTitle(Title.title(Component.text("CLEAR!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(sub, NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(800))));
            player.sendMessage(Component.text("[ダンジョン] クリア! " + (room ? "まもなく報酬部屋へ移動する。" : "")
                    + (chest ? "宝箱を開けると報酬が手に入る。" : "")
                    + (definition.exit() != null ? "脱出口からも帰れる。" : "")
                    + clock(definition.closeDelaySeconds()) + " 後に全員が元の場所へ戻される。", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
        }
        if (room) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (instance.state != State.CLEARED || instance.world == null) return;
                Location target = definition.rewardRoom().in(instance.world);
                target.getChunk().load();
                for (UUID id : new ArrayList<>(instance.players)) {
                    Player player = Bukkit.getPlayer(id);
                    if (player == null || player.isDead() || !player.getWorld().equals(instance.world)) continue;
                    player.teleport(target);
                    player.playSound(target, Sound.BLOCK_PORTAL_TRAVEL, 0.3f, 1.6f);
                    player.sendMessage(Component.text("[ダンジョン] 報酬部屋に着いた。" + (chest ? "宝箱を開けよう!" : ""), NamedTextColor.GOLD));
                }
            }, 100L);
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
        tickMarkers();
        long now = System.currentTimeMillis();
        for (Instance instance : new ArrayList<>(instances.values())) {
            switch (instance.state) {
                case ACTIVE -> tickActive(instance, now);
                case CLEARED, FAILED -> {
                    if (instance.state == State.CLEARED) {
                        exitParticles(instance, true);
                        for (UUID id : instance.players) {
                            Player player = Bukkit.getPlayer(id);
                            if (player != null) player.sendActionBar(Component.text("クリア! "
                                    + (instance.definition.chest() != null && !instance.chestOpened.contains(id) ? "宝箱を開けよう / " : "")
                                    + (instance.definition.exit() != null ? "脱出口から帰れる / " : "")
                                    + "あと " + clock(Math.max(0, instance.closeCountdown)) + " で強制送還", NamedTextColor.GOLD));
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
        instance.bosses.removeIf(id -> !instance.mobs.contains(id));
        boolean done = instance.definition.boss() != null ? instance.bosses.isEmpty() : instance.mobs.isEmpty();
        if (done) {
            clear(instance);
            return;
        }
        exitParticles(instance, false);
        tickBossTrigger(instance);
        tickDowned(instance);
        if (instance.state != State.ACTIVE) return;
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
            if (instance.downed.containsKey(id)) continue; // the downed see their own revive progress
            Component bar = Component.text("残り " + clock((int) (left / 1000)), left < 60_000 ? NamedTextColor.RED : NamedTextColor.WHITE)
                    .append(Component.text("  敵 " + instance.mobs.size(), NamedTextColor.GOLD));
            if (instance.definition.boss() != null) bar = bar.append(Component.text("  ボス 生存", NamedTextColor.RED));
            if (instance.definition.deathMode() == DungeonDefinition.DeathMode.LIVES) {
                bar = bar.append(Component.text("  残機 " + instance.lives.getOrDefault(id, 0), NamedTextColor.AQUA));
            } else if (!instance.downed.isEmpty()) {
                bar = bar.append(Component.text("  倒れた仲間 " + instance.downed.size(), NamedTextColor.LIGHT_PURPLE));
            }
            player.sendActionBar(bar);
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
        UUID dead = event.getEntity().getUniqueId();
        instance.mobs.remove(dead);
        boolean wasBoss = instance.bosses.remove(dead);
        if (instance.definition.boss() != null) {
            if (wasBoss && instance.bosses.isEmpty()) clear(instance);
        } else if (instance.mobs.isEmpty()) {
            clear(instance);
        }
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

        UUID id = player.getUniqueId();
        if (instance.definition.deathMode() == DungeonDefinition.DeathMode.REVIVE) {
            onDownedDeath(instance, player);
            return;
        }
        // lives: each death uses one and the player comes back at the entry; with none left they are out
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
        if (instance == null || instance.world == null || instance.state != State.ACTIVE) return;
        Downed down = instance.downed.get(player.getUniqueId());
        if (down != null) {
            // back at the body, watching as a spectator until someone revives them
            event.setRespawnLocation(down.corpse.clone().add(0, 1.0, 0));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && instance.downed.containsKey(player.getUniqueId())) {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.showTitle(Title.title(Component.text("倒れた", NamedTextColor.RED),
                            Component.text("仲間が体の横に " + REVIVE_SECONDS + " 秒立つと復活する", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(600))));
                }
            });
            return;
        }
        event.setRespawnLocation(instance.definition.entry().in(instance.world));
    }

    // ---- revive -------------------------------------------------------------------------------------------------------------

    private void onDownedDeath(Instance instance, Player player) {
        UUID id = player.getUniqueId();
        Location corpse = player.getLocation().clone();
        Downed down = new Downed(corpse, player.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : player.getGameMode());
        instance.downed.put(id, down);
        saveGameMode(id, down.before);
        down.label = corpse.getWorld().spawn(corpse.clone().add(0, 0.6, 0), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            display.text(downedText(player.getName(), 0));
        });
        for (UUID other : instance.players) {
            Player teammate = Bukkit.getPlayer(other);
            if (teammate != null && !other.equals(id)) {
                teammate.sendMessage(Component.text("[ダンジョン] " + player.getName() + " が倒れた! 体の横に " + REVIVE_SECONDS + " 秒立つと蘇生できる。", NamedTextColor.LIGHT_PURPLE));
            }
        }
        // straight back as a spectator (no death screen)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isDead()) player.spigot().respawn();
        });
        checkWipe(instance);
    }

    /** Nobody left standing: the run fails. */
    private void checkWipe(Instance instance) {
        if (instance.state != State.ACTIVE || instance.definition.deathMode() != DungeonDefinition.DeathMode.REVIVE) return;
        for (UUID id : instance.players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && !instance.downed.containsKey(id) && player.getWorld().equals(instance.world)) return;
        }
        if (!instance.players.isEmpty()) fail(instance, "全員が倒れた");
    }

    private void tickDowned(Instance instance) {
        for (UUID id : new ArrayList<>(instance.downed.keySet())) {
            Downed down = instance.downed.get(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getGameMode() == GameMode.SPECTATOR && player.getWorld().equals(instance.world)
                    && player.getLocation().distanceSquared(down.corpse) > SPECTATE_RANGE * SPECTATE_RANGE) {
                player.teleport(down.corpse.clone().add(0, 1.0, 0)); // watch around, but stay near the body
            }
            down.corpse.getWorld().spawnParticle(Particle.SOUL, down.corpse.clone().add(0, 0.3, 0), 3, 0.3, 0.1, 0.3, 0.01);

            List<Player> helpers = new ArrayList<>();
            for (UUID other : instance.players) {
                if (other.equals(id) || instance.downed.containsKey(other)) continue;
                Player teammate = Bukkit.getPlayer(other);
                if (teammate == null || teammate.getGameMode() == GameMode.SPECTATOR || teammate.isDead()) continue;
                if (!teammate.getWorld().equals(instance.world)) continue;
                if (teammate.getLocation().distanceSquared(down.corpse) <= REVIVE_RANGE * REVIVE_RANGE) helpers.add(teammate);
            }
            down.progress = helpers.isEmpty() ? 0 : down.progress + 1;
            String name = player == null ? "?" : player.getName();
            if (down.label != null && down.label.isValid()) down.label.text(downedText(name, down.progress));
            for (Player helper : helpers) {
                helper.sendActionBar(Component.text("蘇生中… " + down.progress + " / " + REVIVE_SECONDS, NamedTextColor.LIGHT_PURPLE));
            }
            if (player != null) {
                player.sendActionBar(down.progress > 0
                        ? Component.text("蘇生されている… " + down.progress + " / " + REVIVE_SECONDS, NamedTextColor.LIGHT_PURPLE)
                        : Component.text("倒れている (仲間が体の横に " + REVIVE_SECONDS + " 秒立つと復活)", NamedTextColor.GRAY));
            }
            if (down.progress >= REVIVE_SECONDS) revive(instance, id, true);
        }
    }

    /** Brings a fallen player back at their body. */
    private void revive(Instance instance, UUID id, boolean announce) {
        Downed down = instance.downed.remove(id);
        if (down == null) return;
        if (down.label != null && down.label.isValid()) down.label.remove();
        saveGameMode(id, null);
        Player player = Bukkit.getPlayer(id);
        if (player == null) return;
        player.setGameMode(down.before);
        player.teleport(down.corpse);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 1.4f);
        if (announce) {
            for (UUID other : instance.players) {
                Player p = Bukkit.getPlayer(other);
                if (p != null) p.sendMessage(Component.text("[ダンジョン] " + player.getName() + " が復活した!", NamedTextColor.GREEN));
            }
        }
    }

    /** Takes a player out of the downed state without reviving (leaving, failing): normal game mode again. */
    private void undown(Instance instance, Player player) {
        Downed down = instance.downed.remove(player.getUniqueId());
        if (down == null) return;
        if (down.label != null && down.label.isValid()) down.label.remove();
        saveGameMode(player.getUniqueId(), null);
        player.setGameMode(down.before);
    }

    private Component downedText(String name, int progress) {
        Component head = Component.text("☠ " + name, NamedTextColor.RED, TextDecoration.BOLD);
        Component line = progress > 0
                ? Component.text("蘇生中 " + progress + " / " + REVIVE_SECONDS, NamedTextColor.LIGHT_PURPLE)
                : Component.text("横に " + REVIVE_SECONDS + " 秒立つと蘇生", NamedTextColor.GRAY);
        return head.append(Component.newline()).append(line);
    }

    // ---- exit ---------------------------------------------------------------------------------------------------------------

    private Component exitText(boolean open) {
        return open
                ? Component.text("脱出口", NamedTextColor.GREEN, TextDecoration.BOLD).append(Component.newline())
                .append(Component.text("触れると帰還", NamedTextColor.WHITE))
                : Component.text("脱出口", NamedTextColor.GRAY, TextDecoration.BOLD).append(Component.newline())
                .append(Component.text("ボスを倒すと開く", NamedTextColor.RED));
    }

    private void exitParticles(Instance instance, boolean open) {
        if (instance.definition.exit() == null || instance.world == null) return;
        Location at = instance.definition.exit().in(instance.world).add(0, 1.0, 0);
        instance.world.spawnParticle(open ? Particle.END_ROD : Particle.SMOKE_NORMAL, at, open ? 12 : 6, 0.4, 0.6, 0.4, 0.01);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isInstanceWorld(event.getTo().getWorld())) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        Instance instance = byPlayer.get(player.getUniqueId());
        if (instance == null || instance.world == null || instance.definition.exit() == null) return;
        if (player.getGameMode() == GameMode.SPECTATOR || instance.downed.containsKey(player.getUniqueId())) return;
        Location exit = instance.definition.exit().in(instance.world);
        if (event.getTo().distanceSquared(exit) > EXIT_RANGE * EXIT_RANGE) return;

        if (instance.state == State.CLEARED) {
            player.sendMessage(Component.text("[ダンジョン] 脱出口から帰還した。", NamedTextColor.GREEN));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (byPlayer.get(player.getUniqueId()) == instance) sendHome(player, instance);
            });
        } else if (instance.state == State.ACTIVE) {
            long now = System.currentTimeMillis();
            Long last = instance.exitNotice.get(player.getUniqueId());
            if (last == null || now - last > 3000) {
                instance.exitNotice.put(player.getUniqueId(), now);
                player.sendActionBar(Component.text("脱出口は、ボスを倒すまで開かない", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.4f, 0.8f);
            }
        }
    }

    // ---- reward chest -------------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (!isInstanceWorld(block.getWorld())) return;
        Player player = event.getPlayer();
        Instance instance = byPlayer.get(player.getUniqueId());
        if (instance == null) return;
        int road = instance.definition.roadChestAt(block.getX(), block.getY(), block.getZ());
        if (road >= 0) {
            event.setCancelled(true);
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
            if (!instance.roadOpened.computeIfAbsent(road, key -> new HashSet<>()).add(player.getUniqueId())) {
                player.sendMessage(Component.text("[ダンジョン] この宝箱はもう開けた。", NamedTextColor.GRAY));
                return;
            }
            openChest(player, instance, DungeonLoot.loadRoad(plugin, instance.definition), "宝箱");
            return;
        }
        DungeonDefinition.Point chest = instance.definition.chest();
        if (chest == null) return;
        if (block.getX() != (int) Math.floor(chest.x()) || block.getY() != (int) Math.floor(chest.y()) || block.getZ() != (int) Math.floor(chest.z())) return;

        event.setCancelled(true);
        // one right click also sends an off-hand event: handle the main hand only (the off-hand one stays cancelled,
        // so the vanilla chest never opens)
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (instance.state != State.CLEARED) {
            player.sendMessage(Component.text("[ダンジョン] ボスを倒すまで開かない。", NamedTextColor.RED));
            return;
        }
        if (!instance.chestOpened.add(player.getUniqueId())) {
            player.sendMessage(Component.text("[ダンジョン] 宝箱はもう開けた。(1人1回)", NamedTextColor.GRAY));
            return;
        }
        openChest(player, instance, DungeonLoot.load(plugin, instance.definition), "報酬の宝箱");
    }

    /** Rolls a chest, gives everything right away, then shows it with the flicker. */
    private void openChest(Player player, Instance instance, DungeonLoot loot, String title) {
        DungeonLoot.Roll roll = loot.roll();
        PlayerProfile profile = profileManager.getOrCreate(player);
        profile.setCurrency(profile.currency() + roll.gold());
        profileManager.save(player);
        if (roll.xp() > 0) levelService.addXp(player, roll.xp());

        ItemStack shownItem = null;
        boolean rare = false;
        if (roll.item() != null && itemRegistry != null && itemFactory != null) {
            RpgItemDefinition definition = itemRegistry.find(roll.item().itemId()).orElse(null);
            if (definition == null) {
                plugin.getLogger().warning("dungeon loot: unknown item '" + roll.item().itemId() + "' in loot/" + instance.definition.id() + ".yml");
            } else {
                ItemStack item = itemFactory.create(definition);
                int left = roll.amount();
                while (left > 0) {
                    ItemStack stack = item.clone();
                    stack.setAmount(Math.min(left, stack.getMaxStackSize()));
                    left -= stack.getAmount();
                    for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), rest);
                    }
                }
                shownItem = item.clone();
                shownItem.setAmount(Math.max(1, Math.min(roll.amount(), shownItem.getMaxStackSize())));
                rare = roll.item().announce() || definition.rarity().tier() >= 3;
                if (rare) {
                    Bukkit.broadcast(Component.text("✦ " + player.getName() + " が「" + instance.definition.name() + "」の宝箱から ", NamedTextColor.GOLD)
                            .append(Component.text(definition.name(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                            .append(Component.text(" を手に入れた! ✦", NamedTextColor.GOLD)));
                }
            }
        }
        player.sendMessage(Component.text("[ダンジョン] 宝箱: +" + roll.gold() + " " + currency() + " / 経験値 +" + roll.xp()
                + (shownItem != null ? " / アイテム ×" + roll.amount() : ""), NamedTextColor.GOLD));
        if (rewardChestMenu != null) {
            rewardChestMenu.open(player, Component.text(title),
                    RewardChestMenu.display(Material.GOLD_INGOT, "+" + roll.gold() + " " + currency(), NamedTextColor.GOLD, List.of()),
                    RewardChestMenu.display(Material.EXPERIENCE_BOTTLE, "経験値 +" + roll.xp(), NamedTextColor.GREEN, List.of()),
                    shownItem, rare);
        }
    }

    // ---- boss room ------------------------------------------------------------------------------------------------------------

    private Component triggerText(int here, int need) {
        return Component.text("ボス部屋への入口", NamedTextColor.RED, TextDecoration.BOLD).append(Component.newline())
                .append(Component.text("ここに集まる: " + here + " / " + need + " 人", NamedTextColor.WHITE));
    }

    /**
     * When enough of the party stands at the trigger (party size - 1, at least 1), everyone is taken to the boss room
     * a few seconds later (fallen teammates too: their body moves with them, so they can still be revived).
     */
    private void tickBossTrigger(Instance instance) {
        DungeonDefinition definition = instance.definition;
        if (instance.bossRoomOpened || definition.bossTrigger() == null || definition.bossRoom() == null) return;
        Location trigger = definition.bossTrigger().in(instance.world);
        instance.world.spawnParticle(Particle.FLAME, trigger.clone().add(0, 0.2, 0), 10, 1.2, 0.1, 1.2, 0.0);

        int need = Math.max(1, instance.players.size() - 1);
        int here = 0;
        for (UUID id : instance.players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || instance.downed.containsKey(id) || player.getGameMode() == GameMode.SPECTATOR) continue;
            if (!player.getWorld().equals(instance.world)) continue;
            if (player.getLocation().distanceSquared(trigger) <= BOSS_TRIGGER_RANGE * BOSS_TRIGGER_RANGE) here++;
        }
        if (instance.triggerLabel != null && instance.triggerLabel.isValid()) instance.triggerLabel.text(triggerText(here, need));
        if (here < need) return;

        instance.bossRoomOpened = true;
        for (UUID id : instance.players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            player.showTitle(Title.title(Component.text("ボス部屋へ", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("3秒後に全員で移動する", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.2f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (instance.state != State.ACTIVE || instance.world == null) return;
            Location room = definition.bossRoom().in(instance.world);
            room.getChunk().load();
            for (UUID id : new ArrayList<>(instance.players)) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || !player.getWorld().equals(instance.world) || player.isDead()) continue;
                Downed down = instance.downed.get(id);
                if (down != null) {
                    // the body goes with the party
                    down.corpse.setX(room.getX());
                    down.corpse.setY(room.getY());
                    down.corpse.setZ(room.getZ());
                    if (down.label != null && down.label.isValid()) down.label.teleport(room.clone().add(0, 0.6, 0));
                }
                player.teleport(room);
                player.playSound(room, Sound.BLOCK_PORTAL_TRAVEL, 0.3f, 1.4f);
            }
            if (instance.triggerLabel != null && instance.triggerLabel.isValid()) instance.triggerLabel.remove();
        }, 60L);
    }

    // ---- team -----------------------------------------------------------------------------------------------------------------

    /** A player inside a run invites someone to join it. */
    public void invite(Player inviter, Player target) {
        Instance instance = byPlayer.get(inviter.getUniqueId());
        if (instance == null || instance.state != State.ACTIVE) {
            inviter.sendMessage(Component.text("[ダンジョン] 挑戦中のダンジョンの中から招待してください。", NamedTextColor.RED));
            return;
        }
        if (target.equals(inviter) || byPlayer.containsKey(target.getUniqueId())) {
            inviter.sendMessage(Component.text("[ダンジョン] その人は招待できません。(すでにダンジョンの中)", NamedTextColor.RED));
            return;
        }
        if (instance.players.size() >= instance.definition.maxPlayers()) {
            inviter.sendMessage(Component.text("[ダンジョン] これ以上は入れません。(最大 " + instance.definition.maxPlayers() + " 人)", NamedTextColor.RED));
            return;
        }
        invites.put(target.getUniqueId(), new Invite(instance.number, inviter.getUniqueId(), System.currentTimeMillis() + 60_000L));
        inviter.sendMessage(Component.text("[ダンジョン] " + target.getName() + " を招待した。(60秒)", NamedTextColor.YELLOW));
        target.sendMessage(Component.text("[ダンジョン] " + inviter.getName() + " から「" + instance.definition.name() + "」への招待 ", NamedTextColor.YELLOW)
                .append(Component.text("[参加する]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/dungeon join " + inviter.getName()))));
    }

    /** Accepts an invite and goes into the inviter's run. */
    public void join(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        Instance instance = invite == null ? null : instances.get(invite.instance());
        if (invite == null || invite.until() < System.currentTimeMillis() || instance == null || instance.state != State.ACTIVE) {
            player.sendMessage(Component.text("[ダンジョン] 参加できる招待がありません。(期限切れか、もう終わっています)", NamedTextColor.RED));
            return;
        }
        if (byPlayer.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("[ダンジョン] すでにダンジョンの中にいます。", NamedTextColor.RED));
            return;
        }
        if (profileManager.getOrCreate(player).level() < instance.definition.minLevel()) {
            player.sendMessage(Component.text("[ダンジョン] レベルが足りません。(必要: Lv" + instance.definition.minLevel() + ")", NamedTextColor.RED));
            return;
        }
        if (instance.players.size() >= instance.definition.maxPlayers()) {
            player.sendMessage(Component.text("[ダンジョン] もう満員です。", NamedTextColor.RED));
            return;
        }
        instance.players.add(player.getUniqueId());
        instance.lives.put(player.getUniqueId(), instance.definition.lives());
        byPlayer.put(player.getUniqueId(), instance);
        remember(player, player.getLocation());
        player.closeInventory();
        player.teleport(instance.definition.entry().in(instance.world));
        for (UUID id : instance.players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(Component.text("[ダンジョン] " + player.getName() + " が参加した。(" + instance.players.size() + " 人)", NamedTextColor.GREEN));
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
        GameMode savedMode = savedGameMode(player.getUniqueId());
        if (savedMode != null && byPlayer.get(player.getUniqueId()) == null) {
            player.setGameMode(savedMode); // the server stopped while they were down as a spectator
            saveGameMode(player.getUniqueId(), null);
        }
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
        Player player = event.getPlayer();
        builders.remove(player.getUniqueId());
        invites.remove(player.getUniqueId());
        Instance instance = byPlayer.get(player.getUniqueId());
        if (instance != null && instance.downed.containsKey(player.getUniqueId())) {
            // a fallen player who leaves is out of the run (game mode restored now; sent home at the next login)
            undown(instance, player);
            instance.players.remove(player.getUniqueId());
            byPlayer.remove(player.getUniqueId());
            checkWipe(instance);
        } else if (instance != null) {
            Bukkit.getScheduler().runTask(plugin, () -> checkWipe(instance));
        }
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
        // a fallen player watching as a spectator stays in the dungeon
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE
                && (event.getTo() == null || !event.getFrom().getWorld().equals(event.getTo().getWorld()))) {
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
        refreshMarkers(definition.id());
        admin.sendMessage(Component.text("[ダンジョン] 「" + definition.name() + "」のひな形にいる。建てたら /dungeon entry・spawn、終わったら /dungeon save " + definition.id(), NamedTextColor.YELLOW));
    }

    /** Saves the template world, sends the admin back, and unloads the world if nobody is left in it. */
    public void saveTemplate(Player admin, DungeonDefinition definition) {
        World world = Bukkit.getWorld(definition.templateWorldName());
        if (world == null) {
            admin.sendMessage(Component.text("[ダンジョン] ひな形のワールドは開かれていません。(保存済み)", NamedTextColor.GRAY));
            return;
        }
        clearMarkers(definition.templateWorldName());
        world.save();
        Location back = builders.remove(admin.getUniqueId());
        if (admin.getWorld().equals(world)) admin.teleport(back == null ? fallback() : back);
        if (world.getPlayers().isEmpty()) Bukkit.unloadWorld(world, true);
        admin.sendMessage(Component.text("[ダンジョン] ひな形を保存した。", NamedTextColor.GREEN));
    }

    // ---- markers while building ------------------------------------------------------------------------------------------

    /**
     * Labels over every placed mob (number, name, id; the boss in red), the entry and the exit, in the template world
     * while it is open. They are not saved with the world (not persistent), so the copies never contain them.
     */
    public void refreshMarkers(String id) {
        DungeonDefinition definition = registry.find(id).orElse(null);
        if (definition == null) return;
        World world = Bukkit.getWorld(definition.templateWorldName());
        clearMarkers(definition.templateWorldName());
        if (world == null) return;

        List<Entity> labels = new ArrayList<>();
        List<DungeonDefinition.MobSpawn> spawns = definition.spawns();
        for (int index = 0; index < spawns.size(); index++) {
            DungeonDefinition.MobSpawn spawn = spawns.get(index);
            boolean boss = spawn.mobId().equals(definition.boss());
            String name = mobRegistry.find(spawn.mobId()).map(RpgMobDefinition::name).orElse("? 不明なモブ");
            Component text = Component.text("#" + (index + 1) + " " + name + (boss ? " (ボス)" : ""), boss ? NamedTextColor.RED : NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .append(Component.newline())
                    .append(Component.text(spawn.mobId(), NamedTextColor.GRAY));
            labels.add(label(spawn.in(world).add(0, 1.8, 0), text));
            // an invisible box over the spot, so the editor can click the placed mob
            Location at = spawn.in(world);
            org.bukkit.entity.Interaction box = world.spawn(at, org.bukkit.entity.Interaction.class, interaction -> {
                interaction.setInteractionWidth(0.9f);
                interaction.setInteractionHeight(1.8f);
                interaction.setResponsive(true);
                interaction.setPersistent(false);
            });
            markerRefs.put(box.getUniqueId(), new MarkerRef(definition.id(), index));
            labels.add(box);
        }
        labels.add(label(definition.entry().in(world).add(0, 2.2, 0), Component.text("入口", NamedTextColor.GREEN, TextDecoration.BOLD)));
        if (definition.exit() != null) {
            labels.add(label(definition.exit().in(world).add(0, 2.2, 0), Component.text("脱出口", NamedTextColor.AQUA, TextDecoration.BOLD)));
        }
        if (definition.rewardRoom() != null) {
            labels.add(label(definition.rewardRoom().in(world).add(0, 2.2, 0), Component.text("報酬部屋 (クリア後ここへ)", NamedTextColor.GOLD, TextDecoration.BOLD)));
        }
        if (definition.chest() != null) {
            labels.add(label(definition.chest().in(world).add(0.5, 1.5, 0.5), Component.text("報酬の宝箱", NamedTextColor.GOLD, TextDecoration.BOLD)));
        }
        for (int index = 0; index < definition.roadChests().size(); index++) {
            labels.add(label(definition.roadChests().get(index).in(world).add(0.5, 1.5, 0.5),
                    Component.text("道中の宝箱 #" + (index + 1), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        }
        if (definition.bossTrigger() != null) {
            labels.add(label(definition.bossTrigger().in(world).add(0, 2.2, 0),
                    Component.text("ボス部屋への入口 (集まると移動)", NamedTextColor.RED, TextDecoration.BOLD)));
        }
        if (definition.bossRoom() != null) {
            labels.add(label(definition.bossRoom().in(world).add(0, 2.2, 0), Component.text("ボス部屋 (移動先)", NamedTextColor.RED, TextDecoration.BOLD)));
        }
        markers.put(definition.templateWorldName(), labels);
    }

    /** The placed mob behind a clicked marker box, or null. */
    public MarkerRef markerOf(Entity entity) {
        return entity == null ? null : markerRefs.get(entity.getUniqueId());
    }

    private Entity label(Location at, Component text) {
        at.getChunk().load();
        return at.getWorld().spawn(at, TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            display.setSeeThrough(true); // visible through walls while building
            display.text(text);
        });
    }

    private void clearMarkers(String templateWorld) {
        List<Entity> old = markers.remove(templateWorld);
        if (old == null) return;
        for (Entity entity : old) {
            markerRefs.remove(entity.getUniqueId());
            if (entity.isValid()) entity.remove();
        }
    }

    /** Every second: a column of particles at each placed mob, the entry and the exit, while someone is building. */
    private void tickMarkers() {
        for (String worldName : markers.keySet()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null || world.getPlayers().isEmpty()) continue;
            String id = worldName.substring("dungeon_template_".length());
            DungeonDefinition definition = registry.find(id).orElse(null);
            if (definition == null) continue;
            for (DungeonDefinition.MobSpawn spawn : definition.spawns()) {
                boolean boss = spawn.mobId().equals(definition.boss());
                world.spawnParticle(boss ? Particle.FLAME : Particle.VILLAGER_HAPPY, spawn.in(world).add(0, 0.8, 0), 6, 0.2, 0.6, 0.2, 0.0);
            }
            world.spawnParticle(Particle.COMPOSTER, definition.entry().in(world).add(0, 0.8, 0), 6, 0.2, 0.6, 0.2, 0.0);
            if (definition.exit() != null) {
                world.spawnParticle(Particle.END_ROD, definition.exit().in(world).add(0, 0.8, 0), 6, 0.2, 0.6, 0.2, 0.0);
            }
            if (definition.bossTrigger() != null) {
                world.spawnParticle(Particle.FLAME, definition.bossTrigger().in(world).add(0, 0.2, 0), 10, 1.2, 0.1, 1.2, 0.0);
            }
        }
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

    private void saveGameMode(UUID id, GameMode mode) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        yaml.set("gamemode." + id, mode == null ? null : mode.name());
        saveState(yaml);
    }

    private GameMode savedGameMode(UUID id) {
        if (!stateFile.exists()) return null;
        String raw = YamlConfiguration.loadConfiguration(stateFile).getString("gamemode." + id);
        if (raw == null) return null;
        try {
            return GameMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
