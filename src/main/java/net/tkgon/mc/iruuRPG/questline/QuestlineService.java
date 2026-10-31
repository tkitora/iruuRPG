package net.tkgon.mc.iruuRPG.questline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.tkgon.mc.iruuRPG.item.ItemIdentifier;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.questline.Questline.Act;
import net.tkgon.mc.iruuRPG.questline.Questline.Cond;
import net.tkgon.mc.iruuRPG.questline.Questline.Line;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.LineState;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.Status;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * The quest lines in the game: reads questlines/*.yml and flags/flags.yml (the yml repository's; the plugin never
 * writes there), turns what happens to players into {@link QuestEvent}s for the {@link QuestlineEngine}, and does
 * the actions that touch the game (talk, titles, sounds, items, rewards). Talk is paced per player. A step or status
 * change is saved at once; flag changes are saved every half minute (and on quit).
 */
public final class QuestlineService implements Listener {

    private static final long AREA_PERIOD = 10L;
    private static final long SETTLE_PERIOD = 20L;
    private static final long SAVE_PERIOD = 600L;
    private static final int MAX_LOGGED_WARNINGS = 40;

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final ItemIdentifier itemIdentifier;
    private final RpgItemRegistry itemRegistry;
    private final RpgItemFactory itemFactory;
    private final LevelService levelService;
    private QuestlineEngine engine = new QuestlineEngine(Map.of(), QuestlineParser.FlagBook.EMPTY);
    private List<String> lastWarnings = List.of();
    /** Every area of an area-enter condition, by its key. */
    private Map<String, Area> areas = Map.of();
    /** The areas each player is in now (an area event fires when one is entered). */
    private final Map<UUID, Set<String>> insideAreas = new HashMap<>();
    private final Map<UUID, Deque<Talk>> talkQueues = new HashMap<>();
    private final Set<UUID> talking = new HashSet<>();
    private final Set<UUID> dirty = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    /** The name of a speaker id (the NPCs, when they exist); the id itself until then. */
    private Function<String, String> speakerNames = id -> id;
    /** What an NPC says when no quest has anything for it (npcs.yml idle:). */
    private Function<String, List<String>> idleLines = id -> List.of();
    private final Map<UUID, Integer> idleTurn = new HashMap<>();
    /** The line each player was just offered (by an NPC), and until when the offer stands. */
    private final Map<UUID, Offer> offers = new HashMap<>();
    private static final long OFFER_MILLIS = 120_000L;

    private record Offer(String line, long until) {
    }

    private record Area(String key, String world, double x, double y, double z, double radius) {
    }

    /** One line of talk in a player's queue; npc and said: the NPC whose head shows it, and its words. */
    private record Talk(Component text, long ticks, Runnable after, String npc, String said) {
    }

    public QuestlineService(JavaPlugin plugin, PlayerProfileManager profileManager, ItemIdentifier itemIdentifier,
                            RpgItemRegistry itemRegistry, RpgItemFactory itemFactory, LevelService levelService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.itemIdentifier = itemIdentifier;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
        this.levelService = levelService;
    }

    /** Shows a line of talk over an NPC's head, to one player, for some ticks (the NPCs; nothing until they exist). */
    public interface SpeechBubbles {
        void show(Player player, String npc, String text, long ticks);
    }

    private SpeechBubbles bubbles = (player, npc, text, ticks) -> { };
    private Function<String, Set<String>> assignedNpcs = line -> Set.of();

    public void setSpeechBubbles(SpeechBubbles bubbles) {
        this.bubbles = bubbles == null ? (player, npc, text, ticks) -> { } : bubbles;
    }

    /** The NPCs each line is offered by besides its start.npc (npcs.yml quests:, chosen in /npc edit). */
    public void setAssignedNpcs(Function<String, Set<String>> assignedNpcs) {
        this.assignedNpcs = assignedNpcs == null ? line -> Set.of() : assignedNpcs;
        engine.setAssignedNpcs(this.assignedNpcs);
    }

    public void setSpeakerNames(Function<String, String> speakerNames) {
        this.speakerNames = speakerNames == null ? id -> id : speakerNames;
    }

    private net.tkgon.mc.iruuRPG.cutscene.CutsceneService cutscenes;

    public void setCutscenes(net.tkgon.mc.iruuRPG.cutscene.CutsceneService cutscenes) {
        this.cutscenes = cutscenes;
    }

    /** Plays a cutscene for a line; its end fires cutscene-end. One that does not exist counts as ended at once. */
    private void playCutscene(Player player, String id) {
        if (cutscenes == null || !cutscenes.exists(id)) {
            plugin.getLogger().warning("quest: no cutscenes/" + id + ".yml; it counts as ended");
            Bukkit.getScheduler().runTask(plugin, () -> fire(player, QuestEvent.of("cutscene-end", "cutscene", id)));
            return;
        }
        // played to its end: the line goes on; cut short (the player left): it stays waiting and is played again on the next join
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) cutscenes.play(player, id, done -> {
                if (done && player.isOnline()) fire(player, QuestEvent.of("cutscene-end", "cutscene", id));
            });
        });
    }

    public void setIdleLines(Function<String, List<String>> idleLines) {
        this.idleLines = idleLines == null ? id -> List.of() : idleLines;
    }

    public void start() {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tickAreas, AREA_PERIOD, AREA_PERIOD));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tickSettle, SETTLE_PERIOD, SETTLE_PERIOD));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::flush, SAVE_PERIOD, SAVE_PERIOD));
    }

    public void shutdown() {
        for (BukkitTask task : tasks) task.cancel();
        tasks.clear();
        flush();
    }

    public QuestlineEngine engine() {
        return engine;
    }

    public List<String> lastWarnings() {
        return lastWarnings;
    }

    // ---- loading ----------------------------------------------------------------------------------------------------

    /** Reads the quest files again. Never throws: what is wrong is logged and left out. Returns the number of lines. */
    public int reload() {
        List<String> warnings = new ArrayList<>();
        QuestlineParser.FlagBook book = QuestlineParser.FlagBook.EMPTY;
        File flagsFile = new File(plugin.getDataFolder(), "flags/flags.yml");
        if (flagsFile.isFile()) {
            Object raw = read(flagsFile, warnings);
            if (raw instanceof Map<?, ?> map) book = QuestlineParser.parseFlags(map, warnings);
        }
        Map<String, Questline> lines = new LinkedHashMap<>();
        File folder = new File(plugin.getDataFolder(), "questlines");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - 4);
                Object raw = read(file, warnings);
                if (!(raw instanceof Map<?, ?> map)) {
                    if (raw != null) warnings.add("questlines/" + file.getName() + ": not a map");
                    continue;
                }
                try {
                    Questline line = QuestlineParser.parse(id, map, book.flags().keySet(), warnings);
                    if (line != null) lines.put(id, line);
                } catch (RuntimeException e) {
                    warnings.add("questlines/" + file.getName() + ": could not be read (" + e + ")");
                }
            }
        }
        engine = new QuestlineEngine(lines, book);
        engine.setAssignedNpcs(assignedNpcs);
        areas = collectAreas(lines.values(), book);
        lastWarnings = List.copyOf(warnings);
        for (int index = 0; index < warnings.size() && index < MAX_LOGGED_WARNINGS; index++) {
            plugin.getLogger().warning("quest: " + warnings.get(index));
        }
        if (warnings.size() > MAX_LOGGED_WARNINGS) {
            plugin.getLogger().warning("quest: ... and " + (warnings.size() - MAX_LOGGED_WARNINGS) + " more (/questadmin warnings)");
        }
        plugin.getLogger().info("quest: " + lines.size() + " quest line(s), " + book.flags().size() + " shared flag(s)");
        return lines.size();
    }

    private Object read(File file, List<String> warnings) {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return new Yaml().load(reader);
        } catch (IOException | RuntimeException e) {
            warnings.add(file.getName() + ": not valid yml (" + e.getMessage() + ")");
            return null;
        }
    }

    private static Map<String, Area> collectAreas(java.util.Collection<Questline> lines, QuestlineParser.FlagBook book) {
        List<Cond> conditions = new ArrayList<>();
        for (Questline line : lines) {
            for (Questline.Step step : line.steps().values()) {
                for (Questline.Transition transition : step.transitions()) conditions.addAll(transition.when());
                for (Questline.Handler handler : step.on()) conditions.add(handler.event());
            }
        }
        for (Questline.Handler rule : book.rules()) conditions.add(rule.event());
        Map<String, Area> out = new LinkedHashMap<>();
        for (Cond condition : QuestlineParser.flatten(conditions)) {
            if (!condition.type().equals("event") || !"area-enter".equals(condition.string("event"))) continue;
            String key = QuestEvent.areaKey(condition.args());
            out.put(key, new Area(key, condition.string("world") == null ? "world" : condition.string("world"),
                    condition.number("x", 0), condition.number("y", 0), condition.number("z", 0), condition.number("radius", 3)));
        }
        return out;
    }

    // ---- events -----------------------------------------------------------------------------------------------------

    /** Something happened to the player: the quest lines hear it. */
    public void fire(Player player, QuestEvent event) {
        if (player == null || !player.isOnline()) return;
        QuestlineData data = data(player);
        try {
            PlayerGame game = new PlayerGame(player);
            if (engine.fire(data, game, event)) changed(player, game);
            tellProgress(player);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "quest: " + event.name() + " for " + player.getName() + " failed", e);
        }
    }

    /** The progress last told to each player, per line: the step and its value (so only a change is told). */
    private final Map<UUID, Map<String, String[]>> toldProgress = new HashMap<>();

    /** "3 / 5" of a line for the player (a flag, or the items they have), or null. */
    public String progressText(Player player, String id) {
        return engine.progress(data(player), new PlayerGame(player), id);
    }

    /**
     * Tells in the chat every progress that changed since it was last told: [クエスト名]  討伐:イノシシ [1/5]. It is read
     * from the step the line was in when last told, so the last one (which moves the line on) is told too; a progress
     * seen for the first time is only remembered.
     */
    private void tellProgress(Player player) {
        QuestlineData data = data(player);
        PlayerGame game = new PlayerGame(player);
        Map<String, String[]> told = toldProgress.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        for (var entry : new ArrayList<>(told.entrySet())) {
            Questline line = engine.line(entry.getKey());
            Questline.Step step = line == null ? null : line.step(entry.getValue()[0]);
            LineState state = data.line(entry.getKey());
            if (step == null || step.progress() == null || state == null || state.status == Status.PAUSED) continue;
            long now = engine.progressValue(data, game, line, step);
            if (String.valueOf(now).equals(entry.getValue()[1])) continue;
            entry.getValue()[1] = String.valueOf(now);
            String label = step.progress().label() != null ? step.progress().label() : step.name() != null ? step.name() : "進み具合";
            boolean full = now >= step.progress().max();
            player.sendMessage(Component.text("[" + line.name() + "]  ", NamedTextColor.AQUA)
                    .append(Component.text(label + " ", NamedTextColor.WHITE))
                    .append(Component.text("[" + Math.min(now, step.progress().max()) + "/" + step.progress().max() + "]", full ? NamedTextColor.GOLD : NamedTextColor.GREEN)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, full ? 2.0f : 1.6f);
        }
        // remember the steps as they are now (a new step starts from its value now, untold)
        told.clear();
        for (var entry : data.lines().entrySet()) {
            if (entry.getValue().status != Status.ACTIVE) continue;
            Questline line = engine.line(entry.getKey());
            Questline.Step step = line == null ? null : line.step(entry.getValue().step);
            if (step == null || step.progress() == null) continue;
            told.put(line.id(), new String[]{step.id(), String.valueOf(engine.progressValue(data, game, line, step))});
        }
    }

    public void onMobKilled(Player player, String mobId) {
        fire(player, QuestEvent.of("mob-kill", "mob", mobId));
    }

    public void onDungeonCleared(Player player, String dungeonId) {
        fire(player, QuestEvent.of("dungeon-clear", "dungeon", dungeonId));
    }

    public void onCrafted(Player player, String itemId) {
        fire(player, QuestEvent.of("craft", "item", itemId));
    }

    public void onLevelUp(Player player, int level) {
        fire(player, QuestEvent.of("level-up", "level", level));
    }

    /**
     * The player talks to an NPC: the talk of the current steps for it is said (paced), then npc-talk fires. While a
     * talk is being said, another click on the NPC does nothing.
     */
    public void talk(Player player, String npc) {
        if (talking.contains(player.getUniqueId())) return;
        List<Line> lines = engine.talk(data(player), npc);
        for (Line line : lines) say(player, line, null);
        say(player, null, () -> fire(player, QuestEvent.of("npc-talk", "npc", npc)));
    }

    /**
     * The player right-clicked an NPC. What the active lines want of it comes first: the items it takes now are taken
     * (item-give), its talk of the current steps is said and npc-talk fires; then, if it offers a line that can be
     * started, its offer is said with the buttons to take it or not. With neither, one of its idle lines. (So a line
     * that waits on this NPC never gets stuck behind an offer the player keeps turning down.)
     */
    public void interact(Player player, String npc) {
        if (talking.contains(player.getUniqueId())) return;
        QuestlineData data = data(player);
        List<Cond> gives = new ArrayList<>();
        for (Cond give : engine.gives(data, npc)) {
            int amount = (int) give.number("amount", 1);
            if (count(player, give.string("item")) >= amount) gives.add(give);
        }
        List<Line> lines = new ArrayList<>(engine.talk(data, npc));
        // a line this NPC gave whose step has nothing for it to say: it says at least where the player stands
        for (Questline line : engine.statusFor(data, npc)) {
            Questline.Step step = line.step(data.line(line.id()).step);
            String progress = engine.progress(data, new PlayerGame(player), line.id());
            String goal = step.name() != null ? step.name() : "続きを進めよう";
            lines.add(new Line(npc, "「" + line.name() + "」の件だな。" + goal + (progress == null ? "" : " (" + progress.replace(" ", "") + ")"), -1));
        }
        boolean waits = engine.waitsOn(data, npc) || !lines.isEmpty();
        if (!waits && lines.isEmpty() && gives.isEmpty()) {
            if (!offer(player, npc)) {
                List<String> idle = idleLines.apply(npc);
                if (!idle.isEmpty()) {
                    int turn = idleTurn.merge(player.getUniqueId(), 1, Integer::sum);
                    say(player, new Line(npc, idle.get(Math.floorMod(turn - 1, idle.size())), -1), null);
                }
                say(player, null, () -> fire(player, QuestEvent.of("npc-talk", "npc", npc)));
            }
            return;
        }
        for (Line line : lines) say(player, line, null);
        say(player, null, () -> {
            for (Cond give : gives) {
                String item = give.string("item");
                int amount = (int) give.number("amount", 1);
                if (count(player, item) < amount) continue;
                take(player, item, amount);
                fire(player, QuestEvent.of("item-give", "npc", npc, "item", item, "amount", amount));
            }
            fire(player, QuestEvent.of("npc-talk", "npc", npc));
            // what the NPC offers comes after (paced after the talk), once the lines it waited on had their turn
            if (player.isOnline()) offer(player, npc);
        });
    }

    /** Says the offer of the first line this NPC offers now, then the buttons. False when it offers none. */
    private boolean offer(Player player, String npc) {
        List<Questline> offered = engine.offers(data(player), new PlayerGame(player), npc);
        if (offered.isEmpty()) return false;
        Questline line = offered.get(0);
        for (Line said : line.start().offer()) say(player, said, null);
        say(player, null, () -> offerButtons(player, line));
        return true;
    }

    private void offerButtons(Player player, Questline line) {
        offers.put(player.getUniqueId(), new Offer(line.id(), System.currentTimeMillis() + OFFER_MILLIS));
        Component accept = Component.text("[受ける]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/questline accept " + line.id()))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("「" + line.name() + "」を受ける")));
        Component decline = Component.text("[やめておく]", NamedTextColor.GRAY)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/questline decline"));
        player.sendMessage(Component.text("[クエスト] 「" + line.name() + "」 ", NamedTextColor.AQUA).append(accept).append(Component.text(" ")).append(decline));
    }

    /** The player takes the line they were just offered (the [受ける] button). */
    public void accept(Player player, String id) {
        Offer offer = offers.remove(player.getUniqueId());
        if (offer == null || !offer.line().equals(id) || offer.until() < System.currentTimeMillis()) {
            player.sendMessage(Component.text("[クエスト] もう一度、話しかけてください。", NamedTextColor.GRAY));
            return;
        }
        if (!start(player, id, false)) player.sendMessage(Component.text("[クエスト] 今は受けられません。", NamedTextColor.GRAY));
    }

    public void decline(Player player) {
        if (offers.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("[クエスト] また今度にした。", NamedTextColor.GRAY));
        }
    }

    /**
     * The mark over an NPC for this player: "✔" when talking to it now finishes a line, "?" when an active line waits on
     * it, "!" when it offers a line, else null.
     */
    public String marker(Player player, String npc) {
        QuestlineData data = data(player);
        PlayerGame game = new PlayerGame(player);
        if (engine.ready(data, game, npc)) return "✔";
        if (engine.waitsOn(data, npc)) return "?";
        for (Questline line : engine.lines().values()) {
            if (engine.offeredBy(line, npc) && engine.canStart(data, game, line.id())) return "!";
        }
        return null;
    }

    /** The name of the exclusive line running now for the player (other than {@code except}), or null. */
    public String exclusiveName(Player player, String except) {
        String id = engine.exclusiveRunning(data(player), except);
        Questline line = id == null ? null : engine.line(id);
        return line == null ? id : line.name();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        // one tick later, when the stack is in the inventory (an item condition or an item progress then counts it)
        itemIdentifier.itemId(stack).ifPresent(id -> {
            int amount = stack.getAmount();
            Bukkit.getScheduler().runTask(plugin, () -> fire(player, QuestEvent.of("item-pickup", "item", id, "amount", amount)));
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // after the profile is loaded: lines that start by themselves, or moved on while the player was away
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerGame game = new PlayerGame(player);
            if (engine.evaluate(data(player), game)) changed(player, game);
            for (LineState state : data(player).lines().values()) {
                if (state.status == Status.ACTIVE && state.waitingCutscene != null) {
                    playCutscene(player, state.waitingCutscene);
                    break;
                }
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        insideAreas.remove(id);
        talkQueues.remove(id);
        talking.remove(id);
        offers.remove(id);
        idleTurn.remove(id);
        toldProgress.remove(id);
        dirty.remove(id); // the profile is saved on quit anyway
    }

    private void tickAreas() {
        if (areas.isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasActive(player)) continue;
            Set<String> before = insideAreas.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
            Location at = player.getLocation();
            for (Area area : areas.values()) {
                boolean inside = at.getWorld() != null && at.getWorld().getName().equals(area.world())
                        && square(at.getX() - area.x()) + square(at.getY() - area.y()) + square(at.getZ() - area.z()) <= square(area.radius());
                if (inside && before.add(area.key())) {
                    fire(player, QuestEvent.of("area-enter", "area", area.key()));
                } else if (!inside) {
                    before.remove(area.key());
                }
            }
        }
    }

    /** Conditions that change without an event (time in the step, items, level) are looked at once a second. */
    private void tickSettle() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!hasActive(player)) continue;
            PlayerGame game = new PlayerGame(player);
            try {
                if (engine.evaluate(data(player), game)) changed(player, game);
                tellProgress(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "quest: settling " + player.getName() + " failed", e);
            }
        }
    }

    private boolean hasActive(Player player) {
        for (LineState state : data(player).lines().values()) {
            if (state.status == Status.ACTIVE) return true;
        }
        return false;
    }

    private static double square(double value) {
        return value * value;
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    private QuestlineData data(Player player) {
        return profileManager.getOrCreate(player).questlines();
    }

    private void changed(Player player, PlayerGame game) {
        if (game.moved) {
            profileManager.save(player);
            dirty.remove(player.getUniqueId());
        } else {
            dirty.add(player.getUniqueId());
        }
    }

    /** Saves the players whose flags changed since the last save. */
    public void flush() {
        for (UUID id : new ArrayList<>(dirty)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) profileManager.save(player);
        }
        dirty.clear();
    }

    public void save(Player player) {
        profileManager.save(player);
        dirty.remove(player.getUniqueId());
    }

    // ---- admin ------------------------------------------------------------------------------------------------------

    public boolean start(Player player, String id, boolean force) {
        PlayerGame game = new PlayerGame(player);
        boolean started = engine.start(data(player), game, id, force);
        if (started) save(player);
        return started;
    }

    public boolean setStep(Player player, String id, String step) {
        boolean done = engine.setStep(data(player), new PlayerGame(player), id, step);
        if (done) save(player);
        return done;
    }

    public boolean writeFlag(Player player, String lineId, String flag, Object value) {
        boolean done = engine.writeFlag(data(player), new PlayerGame(player), lineId, flag, value);
        if (done) save(player);
        return done;
    }

    public boolean pause(Player player, String id) {
        boolean done = engine.pause(data(player), new PlayerGame(player), id);
        if (done) save(player);
        return done;
    }

    public boolean resume(Player player, String id) {
        boolean done = engine.resume(data(player), new PlayerGame(player), id);
        if (done) save(player);
        return done;
    }

    /** Forgets the lines (their state and own flags); {@code all} also forgets the shared flags. */
    public void reset(Player player, java.util.Collection<String> ids, boolean all) {
        QuestlineData data = data(player);
        if (all) {
            data.lines().clear();
            data.flags().clear();
        } else {
            for (String id : ids) data.lines().remove(id);
        }
        insideAreas.remove(player.getUniqueId());
        save(player);
    }

    public QuestlineData dataOf(Player player) {
        return data(player);
    }

    // ---- what the actions do in the game ----------------------------------------------------------------------------

    /** Says one line of talk (or, line null, only runs {@code after} once the lines before it are said). */
    private void say(Player player, Line line, Runnable after) {
        Component text = line == null ? null : talkText(player, line);
        long ticks = line == null ? 0L : line.ticks() >= 0 ? line.ticks() : readTicks(line.text());
        String speaker = line == null ? null : line.speaker();
        boolean npc = speaker != null && !speaker.equalsIgnoreCase("narration") && !speaker.equalsIgnoreCase("player");
        Deque<Talk> queue = talkQueues.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        queue.add(new Talk(text, ticks, after, npc ? speaker : null, line == null ? null : "「" + line.text() + "」"));
        if (talking.add(player.getUniqueId())) nextTalk(player);
    }

    private void nextTalk(Player player) {
        Deque<Talk> queue = talkQueues.get(player.getUniqueId());
        Talk talk = queue == null ? null : queue.poll();
        if (talk == null || !player.isOnline()) {
            talking.remove(player.getUniqueId());
            return;
        }
        if (talk.text() != null) {
            player.sendMessage(talk.text());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.25f, 1.4f);
            if (talk.npc() != null) bubbles.show(player, talk.npc(), talk.said(), Math.max(20L, talk.ticks()));
        }
        if (talk.after() != null) talk.after().run();
        Bukkit.getScheduler().runTaskLater(plugin, () -> nextTalk(player), Math.max(1L, talk.ticks()));
    }

    private Component talkText(Player player, Line line) {
        String speaker = line.speaker() == null ? "narration" : line.speaker();
        if (speaker.equalsIgnoreCase("narration")) {
            return Component.text(line.text(), NamedTextColor.GRAY).decorate(TextDecoration.ITALIC);
        }
        String name = speaker.equalsIgnoreCase("player") ? player.getName() : speakerNames.apply(speaker);
        return Component.text(name + " ", NamedTextColor.GOLD).append(Component.text("「" + line.text() + "」", NamedTextColor.WHITE));
    }

    /** 1.2 s + 0.15 s per character, between 2 s and 8 s (the same pace as the tutorial). */
    static long readTicks(String text) {
        double seconds = 1.2 + 0.15 * text.length();
        return Math.max(40L, Math.min(160L, Math.round(seconds * 20.0)));
    }

    private int count(Player player, String item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            if (itemIdentifier.itemId(stack).map(item::equals).orElse(false)) total += stack.getAmount();
        }
        return total;
    }

    private void take(Player player, String item, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !itemIdentifier.itemId(stack).map(item::equals).orElse(false)) continue;
            int used = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - used);
            player.getInventory().setItem(slot, stack.getAmount() <= 0 ? null : stack);
            left -= used;
        }
    }

    /** Gives RPG items; what does not fit falls at the player's feet. False when the item does not exist. */
    private boolean give(Player player, String item, int amount) {
        var definition = itemRegistry.find(item).orElse(null);
        if (definition == null) {
            plugin.getLogger().warning("quest: no item '" + item + "' to give");
            return false;
        }
        int left = Math.max(1, amount);
        while (left > 0) {
            ItemStack stack = itemFactory.create(definition);
            int size = Math.min(left, stack.getMaxStackSize());
            stack.setAmount(size);
            left -= size;
            for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        return true;
    }

    private String currencyName() {
        return plugin.getConfig().getString("quest.currency-name", "ゴールド");
    }

    /** Completion: a fanfare and gold light rising around the player for a second and a half (only they see it). */
    private void celebrate(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.3f);
        org.bukkit.Particle.DustOptions gold = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 210, 80), 1.2f);
        new org.bukkit.scheduler.BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 30) {
                    cancel();
                    return;
                }
                Location feet = player.getLocation();
                double height = tick * 0.08;
                for (int arm = 0; arm < 3; arm++) {
                    double angle = tick * 0.45 + arm * (Math.PI * 2 / 3);
                    Location at = feet.clone().add(Math.cos(angle) * 0.9, height, Math.sin(angle) * 0.9);
                    player.spawnParticle(org.bukkit.Particle.REDSTONE, at, 2, 0.03, 0.03, 0.03, 0, gold);
                }
                if (tick == 30) {
                    player.spawnParticle(org.bukkit.Particle.TOTEM, feet.clone().add(0, 2.4, 0), 40, 0.4, 0.3, 0.4, 0.25);
                    player.spawnParticle(org.bukkit.Particle.END_ROD, feet.clone().add(0, 2.4, 0), 12, 0.2, 0.2, 0.2, 0.05);
                    player.playSound(feet, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.6f, 1.2f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The game of one player, for one call into the engine. */
    private final class PlayerGame implements QuestlineEngine.Game {
        private final Player player;
        private boolean moved;
        /** The line entering its end step now: its rewards are shown with the completion, not on their own. */
        private String ending;
        private final List<String> endingRewards = new ArrayList<>();

        private PlayerGame(Player player) {
            this.player = player;
        }

        @Override
        public long now() {
            return System.currentTimeMillis();
        }

        @Override
        public int level() {
            return profileManager.getOrCreate(player).level();
        }

        @Override
        public String classId() {
            return profileManager.getOrCreate(player).classId();
        }

        @Override
        public int itemCount(String item) {
            return item == null ? 0 : count(player, item);
        }

        @Override
        public void takeItem(String item, int amount) {
            if (item != null) take(player, item, amount);
        }

        @Override
        public void perform(Questline line, Act act) {
            if (ending != null && (line == null || !line.id().equals(ending))) flushRewards();
            switch (act.type()) {
                case "say" -> say(player, new Line(act.string("speaker"), act.string("text"), (int) act.number("ticks", -1)), null);
                case "title" -> {
                    long ticks = (long) act.number("ticks", 60);
                    player.showTitle(Title.title(Component.text(String.valueOf(act.args().getOrDefault("title", "")), NamedTextColor.GOLD),
                            Component.text(String.valueOf(act.args().getOrDefault("subtitle", "")), NamedTextColor.YELLOW),
                            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(ticks * 50L), Duration.ofMillis(600))));
                }
                case "sound" -> {
                    try {
                        Sound sound = Sound.valueOf(act.string("sound").toUpperCase(Locale.ROOT).replace('.', '_'));
                        player.playSound(player.getLocation(), sound, (float) act.number("volume", 1.0), (float) act.number("pitch", 1.0));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("quest: no sound '" + act.string("sound") + "'");
                    }
                }
                case "give-item" -> give(player, act.string("item"), (int) act.number("amount", 1));
                case "take-item" -> take(player, act.string("item"), (int) act.number("amount", 1));
                case "reward" -> reward(act);
                case "teleport" -> {
                    World world = act.string("world") == null ? player.getWorld() : Bukkit.getWorld(act.string("world"));
                    if (world == null) {
                        plugin.getLogger().warning("quest: no world '" + act.string("world") + "' to teleport to");
                        return;
                    }
                    player.teleport(new Location(world, act.number("x", 0), act.number("y", 64), act.number("z", 0),
                            (float) act.number("yaw", player.getLocation().getYaw()), (float) act.number("pitch", player.getLocation().getPitch())));
                }
                case "cutscene" -> playCutscene(player, act.string("id"));
                default -> plugin.getLogger().warning("quest: the action " + act.type() + " is not done by the game");
            }
        }

        private void reward(Act act) {
            List<String> parts = new ArrayList<>();
            long money = (long) act.number("money", 0);
            if (money > 0) {
                PlayerProfile profile = profileManager.getOrCreate(player);
                profile.setCurrency(profile.currency() + money);
                parts.add(money + " " + currencyName());
            }
            long exp = (long) act.number("exp", 0);
            if (exp > 0) {
                levelService.addXp(player, exp);
                parts.add("経験値 " + exp);
            }
            if (act.args().get("items") instanceof List<?> items) {
                for (Object raw : items) {
                    if (!(raw instanceof Map<?, ?> item) || item.get("item") == null) continue;
                    String id = item.get("item").toString();
                    int amount = (int) Questline.number(item.get("amount"), 1);
                    if (give(player, id, amount)) {
                        String name = itemRegistry.find(id).map(definition -> definition.name()).orElse(id);
                        parts.add(name + " ×" + amount);
                    }
                }
            }
            if (parts.isEmpty()) return;
            if (ending != null) {
                endingRewards.addAll(parts);
            } else {
                player.sendMessage(Component.text("[クエスト] 報酬: " + String.join(" / ", parts), NamedTextColor.GOLD));
            }
        }

        private void flushRewards() {
            if (!endingRewards.isEmpty()) {
                player.sendMessage(Component.text("[クエスト] 報酬: " + String.join(" / ", endingRewards), NamedTextColor.GOLD));
                endingRewards.clear();
            }
            ending = null;
        }

        @Override
        public void started(Questline line) {
            moved = true;
            player.showTitle(Title.title(Component.text("クエスト開始", NamedTextColor.AQUA),
                    Component.text(line.name(), NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(500))));
            player.sendMessage(Component.text("[クエスト] 「" + line.name() + "」を始めた。メニューの「クエスト」で確認できる。", NamedTextColor.AQUA));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
        }

        @Override
        public void stepChanged(Questline line, Questline.Step step) {
            moved = true;
            if (step.end() != null) {
                ending = line.id();
                return;
            }
            if (ending != null) flushRewards();
            if (step.end() == null && step.name() != null) {
                player.sendMessage(Component.text("[クエスト] " + line.name() + ": ", NamedTextColor.AQUA)
                        .append(Component.text(step.name(), NamedTextColor.WHITE)));
            }
        }

        /**
         * The end of a line, for the player alone: a title, a framed note in the chat with the rewards of the end step,
         * and (completed) a fanfare with light rising around them; (failed) a low sound.
         */
        @Override
        public void finished(Questline line, Questline.End end) {
            moved = true;
            boolean complete = end == Questline.End.COMPLETE;
            List<String> rewards = new ArrayList<>(endingRewards);
            endingRewards.clear();
            ending = null;
            NamedTextColor color = complete ? NamedTextColor.GOLD : NamedTextColor.RED;
            player.showTitle(Title.title(
                    Component.text(complete ? "クエスト達成" : "クエスト失敗", color, TextDecoration.BOLD),
                    Component.text(line.name(), NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
            String rule = "━━━━━━━━━━━━━━━━━━";
            player.sendMessage(Component.text(rule, color));
            player.sendMessage(Component.text(complete ? " クエスト達成! " : " クエスト失敗 ", color, TextDecoration.BOLD)
                    .append(Component.text("「" + line.name() + "」", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)));
            for (String reward : rewards) player.sendMessage(Component.text("  報酬  " + reward, NamedTextColor.YELLOW));
            player.sendMessage(Component.text(rule, color));
            if (complete) {
                celebrate(player);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.9f);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.2f);
            }
        }

        @Override
        public void warn(String message) {
            plugin.getLogger().warning("quest: " + player.getName() + ": " + message);
        }

        @Override
        public void held(Questline line, Questline by) {
            moved = true;
            player.sendMessage(Component.text("[クエスト] 「" + line.name() + "」は保留にした (「" + by.name() + "」"
                    + (by.exclusive() ? "が終わると、続きから再開する)。" : "を進めるため。メニューの「中断中」から再開できる)。"), NamedTextColor.YELLOW));
        }

        @Override
        public void released(Questline line) {
            moved = true;
            player.sendMessage(Component.text("[クエスト] 「" + line.name() + "」を再開した。", NamedTextColor.AQUA));
        }
    }
}
