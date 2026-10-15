package net.tkgon.mc.iruuRPG.hud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sidebar on the right of the screen: a blinking "iruuRPG" on top and the server address at the bottom, with
 * the player's name, level, gold and where they are in between. Each player has their own scoreboard; lines are team
 * prefixes so they update without flicker, and the score numbers are hidden.
 */
public final class SidebarService implements Listener {

    private static final String[] ENTRIES = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9"};
    private static final String NO_COLLISION_TEAM = "iruurpg_nocollide";
    private static final TextColor[] BLINK = {
            NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.WHITE, NamedTextColor.YELLOW
    };

    private record Area(String name, String world, double x, double z, double radius) {
    }

    private static final class Board {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final List<Team> lines = new ArrayList<>();

        private Board(Scoreboard scoreboard, Objective objective) {
            this.scoreboard = scoreboard;
            this.objective = objective;
        }
    }

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final Map<UUID, Board> boards = new HashMap<>();
    private DungeonService dungeonService;
    private int frame;

    public SidebarService(JavaPlugin plugin, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
    }

    public void setDungeonService(DungeonService dungeonService) {
        this.dungeonService = dungeonService;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("sidebar.enabled", true);
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 10L);
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
        frame++;
        for (Player player : Bukkit.getOnlinePlayers()) render(player);
    }

    private void render(Player player) {
        if (!enabled()) {
            if (boards.remove(player.getUniqueId()) != null) player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        Board board = boards.computeIfAbsent(player.getUniqueId(), id -> create(player));
        if (player.getScoreboard() != board.scoreboard) player.setScoreboard(board.scoreboard);

        String title = plugin.getConfig().getString("sidebar.title", "iruuRPG");
        String address = plugin.getConfig().getString("sidebar.address", "mc.tkgon.net");
        TextColor blink = BLINK[frame % BLINK.length];
        TextColor blinkOther = BLINK[(frame + 2) % BLINK.length];
        board.objective.displayName(Component.text("✦ ", blinkOther)
                .append(Component.text(title, blink, TextDecoration.BOLD))
                .append(Component.text(" ✦", blinkOther)));

        var profile = profileManager.get(player).orElse(null);
        String currency = plugin.getConfig().getString("quest.currency-name", "ゴールド");
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(label("名前", Component.text(player.getName(), NamedTextColor.WHITE)));
        lines.add(label("レベル", Component.text(profile == null ? "-" : "Lv " + profile.level(), NamedTextColor.GREEN)));
        lines.add(label("所持金", Component.text(profile == null ? "-" : String.format("%,d", profile.currency()) + " " + currency, NamedTextColor.GOLD)));
        lines.add(label("現在地", Component.text(placeOf(player), NamedTextColor.AQUA)));
        lines.add(Component.text(" "));
        lines.add(Component.text(address, blinkOther));

        // one team per line; the score orders the lines from the top
        while (board.lines.size() < lines.size()) {
            int index = board.lines.size();
            Team team = board.scoreboard.registerNewTeam("iruurpg_line_" + index);
            team.addEntry(ENTRIES[index]);
            board.lines.add(team);
        }
        for (int index = 0; index < lines.size(); index++) {
            board.lines.get(index).prefix(lines.get(index));
            board.objective.getScore(ENTRIES[index]).setScore(lines.size() - index);
        }

        syncNoCollision(board);
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

    private List<Area> areas() {
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
        return areas;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
