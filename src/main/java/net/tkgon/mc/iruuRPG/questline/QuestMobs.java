package net.tkgon.mc.iruuRPG.questline;

import net.tkgon.mc.iruuRPG.mob.MobModels;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import net.tkgon.mc.iruuRPG.party.PartyService;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.LineState;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.Status;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The mobs of a step's {@code spawns:} (type mob): they appear for one player (and the members of their party) while
 * the step lasts and the player is near, and only those players can see them, be targeted or hurt by them, or hurt
 * them. The mob itself, its name / HP display and its model are hidden from everyone else; its target and its hits are
 * limited to the owners; other players' attacks, arrows and the plugin's area attacks skip it. (Its sounds still reach
 * players nearby: the game sends them to everyone around.) They are not saved: after a pause, a log out or a restart
 * they appear again; a mob killed with {@code respawn: 0} stays dead until the step is entered again.
 *
 * <pre>spawns: [ { type: mob, mob: wild_boar, at: {world, x, y, z}, count: 3, radius: 6, respawn: 20 } ]</pre>
 */
public final class QuestMobs implements Listener {

    public static final String TAG = "iruu_quest_mob";
    private static final double APPEAR_DISTANCE = 48.0;
    private static final double LEAVE_DISTANCE = 96.0;

    /** Who may touch each quest mob (mob id → its owner); read by the attack code through {@link #mayHit}. */
    private static final Map<UUID, UUID> OWNERS = new HashMap<>();
    private static PartyService parties;

    private final JavaPlugin plugin;
    private final QuestlineService questlines;
    private final RpgMobService mobService;
    private MobModels models;
    /** Per player, per spawn (line:step:index): the mobs alive, the ones killed for good, and when the next may appear. */
    private final Map<UUID, Map<String, Group>> groups = new HashMap<>();
    private BukkitTask task;

    private static final class Group {
        private final List<UUID> alive = new ArrayList<>();
        private int killed;
        private long nextSpawn;
    }

    public QuestMobs(JavaPlugin plugin, QuestlineService questlines, RpgMobService mobService) {
        this.plugin = plugin;
        this.questlines = questlines;
        this.mobService = mobService;
        mobService.setTargetFilter(QuestMobs::mayTouch);
    }

    public void setModels(MobModels models) {
        this.models = models;
    }

    public static void setParties(PartyService partyService) {
        parties = partyService;
    }

    /** False when {@code entity} is someone else's quest mob: the attack code skips it for {@code attacker}. */
    public static boolean mayHit(Player attacker, Entity entity) {
        return mayTouch(entity, attacker);
    }

    private static boolean mayTouch(Entity mob, Player player) {
        UUID owner = OWNERS.get(mob.getUniqueId());
        if (owner == null || player == null) return true;
        return owner.equals(player.getUniqueId()) || (parties != null && parties.together(owner, player.getUniqueId()));
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (UUID player : new ArrayList<>(groups.keySet())) clear(player);
    }

    // ---- keeping the mobs in step with the quests ----------------------------------------------------------------------

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                sync(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "quest mobs: " + player.getName() + " failed", e);
            }
        }
        groups.keySet().removeIf(id -> Bukkit.getPlayer(id) == null && groups.get(id).isEmpty());
    }

    private void sync(Player player) {
        QuestlineData data = questlines.dataOf(player);
        Map<String, Group> mine = groups.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        Set<String> wanted = new HashSet<>();
        long now = System.currentTimeMillis();
        for (var entry : data.lines().entrySet()) {
            LineState state = entry.getValue();
            if (state.status != Status.ACTIVE) continue;
            Questline line = questlines.engine().line(entry.getKey());
            Questline.Step step = line == null ? null : line.step(state.step);
            if (step == null) continue;
            for (int index = 0; index < step.spawns().size(); index++) {
                Map<String, Object> spawn = step.spawns().get(index);
                if (!"mob".equals(String.valueOf(spawn.getOrDefault("type", "mob"))) || spawn.get("mob") == null) continue;
                String key = line.id() + ":" + step.id() + ":" + index;
                wanted.add(key);
                Group group = mine.computeIfAbsent(key, k -> new Group());
                keep(player, spawn, group, now);
            }
        }
        for (Iterator<Map.Entry<String, Group>> it = mine.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Group> entry = it.next();
            if (wanted.contains(entry.getKey())) continue;
            removeAll(entry.getValue());
            it.remove();
        }
    }

    private void keep(Player player, Map<String, Object> spawn, Group group, long now) {
        group.alive.removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) return false;
            OWNERS.remove(id);
            return true;
        });
        Location at = location(spawn.get("at"), player.getWorld());
        if (at == null) return;
        boolean sameWorld = at.getWorld().equals(player.getWorld());
        double distance = sameWorld ? player.getLocation().distance(at) : Double.MAX_VALUE;
        if (distance > LEAVE_DISTANCE) {
            removeAll(group);
            return;
        }
        for (UUID id : group.alive) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living) hideFromOthers(living, player);
        }
        int count = Math.max(1, (int) Questline.number(spawn.get("count"), 1));
        if (distance > APPEAR_DISTANCE || group.alive.size() + group.killed >= count || now < group.nextSpawn) return;
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        double radius = Questline.number(spawn.get("radius"), 0);
        Location place = at.clone();
        if (radius > 0) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double reach = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;
            place.add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
            place.setY(at.getWorld().getHighestBlockYAt(place) + 1.0);
            if (Math.abs(place.getY() - at.getY()) > 6) place.setY(at.getY());
        }
        LivingEntity mob = mobService.spawnAt(place, spawn.get("mob").toString()).orElse(null);
        if (mob == null) {
            plugin.getLogger().warning("quest mobs: no mob '" + spawn.get("mob") + "'");
            group.nextSpawn = now + 60_000L;
            return;
        }
        mob.setPersistent(false);
        mob.addScoreboardTag(TAG);
        OWNERS.put(mob.getUniqueId(), player.getUniqueId());
        group.alive.add(mob.getUniqueId());
        hideFromOthers(mob, player);
        group.nextSpawn = now + 500L;
    }

    /** The mob, its status display and its model: only the owner and their party see them. */
    private void hideFromOthers(LivingEntity mob, Player owner) {
        List<Entity> parts = new ArrayList<>();
        parts.add(mob);
        Entity status = mobService.statusDisplayOf(mob);
        if (status != null) parts.add(status);
        if (models != null) parts.addAll(models.parts(mob));
        for (Entity part : parts) {
            if (part.isVisibleByDefault()) part.setVisibleByDefault(false);
            for (Player player : part.getWorld().getPlayers()) {
                if (mayTouch(mob, player)) player.showEntity(plugin, part);
                else player.hideEntity(plugin, part);
            }
        }
    }

    private static Location location(Object raw, World fallback) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        World world = map.get("world") == null ? fallback : Bukkit.getWorld(map.get("world").toString());
        if (world == null) return null;
        return new Location(world, Questline.number(map.get("x"), 0), Questline.number(map.get("y"), 64), Questline.number(map.get("z"), 0));
    }

    private void removeAll(Group group) {
        for (UUID id : group.alive) {
            OWNERS.remove(id);
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living) mobService.remove(living);
            else if (entity != null) entity.remove();
        }
        group.alive.clear();
    }

    private void clear(UUID player) {
        Map<String, Group> mine = groups.remove(player);
        if (mine != null) mine.values().forEach(this::removeAll);
    }

    // ---- who may touch them --------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker != null && !mayTouch(event.getEntity(), attacker)) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player victim) {
            Entity source = event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter ? shooter : event.getDamager();
            if (!mayTouch(source, victim)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && !mayTouch(event.getEntity(), player)) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        UUID owner = OWNERS.remove(id);
        if (owner == null) return;
        Map<String, Group> mine = groups.get(owner);
        if (mine == null) return;
        for (var entry : mine.entrySet()) {
            Group group = entry.getValue();
            if (!group.alive.remove(id)) continue;
            Map<String, Object> spawn = spawnOf(owner, entry.getKey());
            double respawn = spawn == null ? 0 : Questline.number(spawn.get("respawn"), 0);
            if (respawn <= 0) group.killed++;
            else group.nextSpawn = System.currentTimeMillis() + (long) (respawn * 1000);
        }
    }

    private Map<String, Object> spawnOf(UUID owner, String key) {
        String[] parts = key.split(":");
        if (parts.length != 3) return null;
        Questline line = questlines.engine().line(parts[0]);
        Questline.Step step = line == null ? null : line.step(parts[1]);
        int index = Integer.parseInt(parts[2]);
        return step == null || index >= step.spawns().size() ? null : step.spawns().get(index);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }
}
