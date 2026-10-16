package net.tkgon.mc.iruuRPG.hud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.classsystem.ClassDefinition;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.dungeon.DungeonService;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sidebar on the right of the screen: an "iruuRPG" on top that flashes in quick bursts (then holds one color) and the server address at the bottom, with
 * the player's name, level, class, gold, where they are and the time in between. Each player has their own scoreboard; lines are team
 * prefixes so they update without flicker, and the score numbers are hidden.
 */
public final class SidebarService implements Listener {

    private static final String[] ENTRIES = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9"};
    private static final String NO_COLLISION_TEAM = "iruurpg_nocollide";
    /** The two colors that flash against each other during a burst. */
    private static final TextColor FLASH_A = NamedTextColor.WHITE;
    private static final TextColor FLASH_B = NamedTextColor.GOLD;
    /** The color held between bursts. */
    private static final TextColor STEADY = NamedTextColor.GOLD;
    private static final TextColor STEADY_DECORATION = NamedTextColor.YELLOW;
    /** The blink runs every 2 ticks, but only the title and the address are touched (and only when they change). */
    private static final long BLINK_STEP_TICKS = 2L;

    private record Area(String name, String world, double x, double z, double radius) {
    }

    private static final class Board {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final List<Team> lines = new ArrayList<>();
        private final List<Component> shown = new ArrayList<>();
        private Component shownTitle;

        private Board(Scoreboard scoreboard, Objective objective) {
            this.scoreboard = scoreboard;
            this.objective = objective;
        }
    }

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final Map<UUID, Board> boards = new HashMap<>();
    private DungeonService dungeonService;
    private ClassService classService;
    private long blinkTicks;

    public SidebarService(JavaPlugin plugin, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
    }

    public void setDungeonService(DungeonService dungeonService) {
        this.dungeonService = dungeonService;
    }

    public void setClassService(ClassService classService) {
        this.classService = classService;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("sidebar.enabled", true);
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::blink, 20L, BLINK_STEP_TICKS);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(boards.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) render(event.getPlayer());
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    private void update() {
        for (Player player : Bukkit.getOnlinePlayers()) render(player);
    }

    /** Fast tick for the title and the address only: a burst of quick flashes, then the same color for a while. */
    private void blink() {
        blinkTicks += BLINK_STEP_TICKS;
        if (!enabled()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Board board = boards.get(player.getUniqueId());
            if (board == null || board.lines.isEmpty()) continue;

            showTitle(board);
            int last = board.lines.size() - 1;
            Component address = addressLine();
            if (!address.equals(board.shown.get(last))) {
                board.lines.get(last).prefix(address);
                board.shown.set(last, address);
            }
        }
    }

    /** True while the burst of flashes is running. One cycle = {@code sidebar.blink.steady-seconds} of calm + {@code burst-seconds} of flashing. */
    private boolean flashing() {
        long burst = Math.max(0L, Math.round(plugin.getConfig().getDouble("sidebar.blink.burst-seconds", 1.5) * 20.0));
        long steady = Math.max(1L, Math.round(plugin.getConfig().getDouble("sidebar.blink.steady-seconds", 6.0) * 20.0));
        return blinkTicks % (burst + steady) < burst;
    }

    /** During the burst the two colors swap every step; otherwise the colors stay put. */
    private boolean flashSwap() {
        return (blinkTicks / BLINK_STEP_TICKS) % 2 == 0;
    }

    private Component titleComponent() {
        String title = plugin.getConfig().getString("sidebar.title", "iruuRPG");
        TextColor main = STEADY;
        TextColor decoration = STEADY_DECORATION;
        if (flashing()) {
            main = flashSwap() ? FLASH_A : FLASH_B;
            decoration = flashSwap() ? FLASH_B : FLASH_A;
        }
        return Component.text("✦ ", decoration)
                .append(Component.text(title, main, TextDecoration.BOLD))
                .append(Component.text(" ✦", decoration));
    }

    private Component addressLine() {
        String address = plugin.getConfig().getString("sidebar.address", "mc.tkgon.net");
        TextColor color = STEADY_DECORATION;
        if (flashing()) color = flashSwap() ? FLASH_B : FLASH_A;
        return Component.text(address, color);
    }

    private void showTitle(Board board) {
        Component titleText = titleComponent();
        if (!titleText.equals(board.shownTitle)) {
            board.objective.displayName(titleText);
            board.shownTitle = titleText;
        }
    }

    private void render(Player player) {
        if (!enabled()) {
            if (boards.remove(player.getUniqueId()) != null) player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        Board board = boards.computeIfAbsent(player.getUniqueId(), id -> create(player));
        if (player.getScoreboard() != board.scoreboard) player.setScoreboard(board.scoreboard);

        showTitle(board);

        List<Component> dungeon = dungeonService == null ? null : dungeonService.sidebarLines(player);
        if (dungeon != null) {
            // in a dungeon: the dungeon's own status (time, enemies, boss room, revive...) instead of the usual lines
            List<Component> lines = new ArrayList<>();
            lines.add(Component.empty());
            for (Component line : dungeon) {
                if (lines.size() >= ENTRIES.length - 2) break;
                lines.add(line);
            }
            lines.add(Component.text(" "));
            lines.add(addressLine());
            show(board, lines);
            syncNoCollision(board);
            return;
        }

        var profile = profileManager.get(player).orElse(null);
        String currency = plugin.getConfig().getString("quest.currency-name", "ゴールド");
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(label("名前", Component.text(player.getName(), NamedTextColor.WHITE)));
        lines.add(label("レベル", Component.text(profile == null ? "-" : "Lv " + profile.level(), NamedTextColor.GREEN)));
        String className = profile == null || classService == null ? "-"
                : classService.currentClass(profile).map(ClassDefinition::name).orElse("未設定");
        lines.add(label("クラス", Component.text(className, NamedTextColor.LIGHT_PURPLE)));
        lines.add(label("所持金", Component.text(profile == null ? "-" : String.format("%,d", profile.currency()) + " " + currency, NamedTextColor.GOLD)));
        lines.add(label("現在地", Component.text(placeOf(player), NamedTextColor.AQUA)));
        lines.add(label("時刻", Component.text(clock(player), NamedTextColor.WHITE)));
        lines.add(Component.text(" "));
        lines.add(addressLine());

        show(board, lines);
        syncNoCollision(board);
    }

    private String zoneSource = "";
    private ZoneId zoneCache = ZoneId.of("Asia/Tokyo");

    /** The configured time zone; a wrong value falls back to Japan time (and is only parsed when it changes). */
    private ZoneId zone() {
        String configured = plugin.getConfig().getString("sidebar.time-zone", "Asia/Tokyo");
        if (!configured.equals(zoneSource)) {
            zoneSource = configured;
            try {
                zoneCache = ZoneId.of(configured);
            } catch (java.time.DateTimeException exception) {
                plugin.getLogger().warning("sidebar.time-zone '" + configured + "' is not a valid time zone; using Asia/Tokyo");
                zoneCache = ZoneId.of("Asia/Tokyo");
            }
        }
        return zoneCache;
    }

    /** The time of day in the player's world by default (sidebar.clock: game); "real" shows real time (time-zone). */
    private String clock(Player player) {
        if (!"real".equalsIgnoreCase(plugin.getConfig().getString("sidebar.clock", "game"))) {
            long ticks = (player.getWorld().getTime() + 6000) % 24000; // tick 0 is 6:00
            long hour = ticks / 1000;
            long minute = (ticks % 1000) * 60 / 1000;
            return String.format("%02d:%02d", hour, minute);
        }
        ZonedDateTime now = ZonedDateTime.now(zone());
        return String.format("%02d:%02d", now.getHour(), now.getMinute());
    }

    /** Puts these lines on the board (only what changed is sent again). */
    private void show(Board board, List<Component> lines) {
        // one team per line; the score orders the lines from the top
        // (only what changed is sent again)
        if (board.lines.size() != lines.size()) {
            for (Team team : board.lines) {
                team.unregister();
            }
            for (String entry : ENTRIES) board.scoreboard.resetScores(entry);
            board.lines.clear();
            board.shown.clear();
            for (int index = 0; index < lines.size(); index++) {
                Team team = board.scoreboard.registerNewTeam("iruurpg_line_" + index);
                team.addEntry(ENTRIES[index]);
                board.lines.add(team);
                board.shown.add(null);
                board.objective.getScore(ENTRIES[index]).setScore(lines.size() - index);
            }
        }
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).equals(board.shown.get(index))) continue;
            board.lines.get(index).prefix(lines.get(index));
            board.shown.set(index, lines.get(index));
        }

    }

    private Component label(String name, Component value) {
        return Component.text(name + ": ", NamedTextColor.GRAY).append(value);
    }

    private Board create(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("iruurpg_side", Criteria.DUMMY, Component.text("iruuRPG"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        return new Board(scoreboard, objective);
    }

    /**
     * The no-collision team lives on the main scoreboard; a player with their own scoreboard needs a copy of it,
     * or their client would predict pushing again.
     */
    private void syncNoCollision(Board board) {
        Team main = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(NO_COLLISION_TEAM);
        if (main == null) return;
        Team copy = board.scoreboard.getTeam(NO_COLLISION_TEAM);
        if (copy == null) {
            copy = board.scoreboard.registerNewTeam(NO_COLLISION_TEAM);
            copy.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        for (String entry : main.getEntries()) {
            if (!copy.hasEntry(entry)) copy.addEntry(entry);
        }
    }

    // ---- where the player is -------------------------------------------------------------------------------------

    private String placeOf(Player player) {
        Location location = player.getLocation();
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        if (world.startsWith("dungeon_instance_") && dungeonService != null) {
            var instance = dungeonService.instanceOf(player);
            if (instance != null) return instance.definition().name();
            return "ダンジョン";
        }
        if (world.startsWith("dungeon_template_")) return "ダンジョン (編集中)";

        Area best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Area area : areas()) {
            if (!area.world().isEmpty() && !area.world().equals(world)) continue;
            double dx = location.getX() - area.x();
            double dz = location.getZ() - area.z();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance <= area.radius() && distance < bestDistance) {
                best = area;
                bestDistance = distance;
            }
        }
        if (best != null) return best.name();
        return plugin.getConfig().getString("sidebar.default-area", "Iruu大陸");
    }

    private Object areasSource;
    private List<Area> areasCache = List.of();

    /** The configured areas, read again only when the config was reloaded. */
    private List<Area> areas() {
        if (areasSource == plugin.getConfig()) return areasCache;
        areasSource = plugin.getConfig();
        List<Area> areas = new ArrayList<>();
        for (Map<?, ?> raw : plugin.getConfig().getMapList("sidebar.areas")) {
            Object name = raw.get("name");
            if (name == null) continue;
            areas.add(new Area(String.valueOf(name), raw.get("world") == null ? "" : String.valueOf(raw.get("world")),
                    number(raw.get("x")), number(raw.get("z")), Math.max(1.0, number(raw.get("radius")))));
        }
        if (areas.isEmpty() && !plugin.getConfig().isSet("sidebar.areas")) {
            areas.add(new Area("アトラス港", "", 60, 170, 60));
        }
        areasCache = areas;
        return areas;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
