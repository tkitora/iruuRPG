package net.tkgon.mc.iruuRPG.spawner;

import net.tkgon.mc.iruuRPG.mob.RpgMobRegistry;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Mobs in the open field. A spawner is a fixed spot (set with the spawner editor, {@code /spawner edit}); when a
 * player gets close enough to see it, 1 - 2 mobs appear, and at most {@code max-alive} of its mobs live at once.
 * A mob that strays too far from its spawner, or has lived for {@code idle-despawn-minutes} with nobody fighting it,
 * is removed so the spawner can spawn a new one. Spawned mobs are not saved with the world.
 */
public final class FieldSpawnerService implements Listener {

    /** Scoreboard tag of every mob a field spawner made (so leftovers of a crash can be swept). */
    public static final String TAG = "iruurpg_field_spawn";

    private final JavaPlugin plugin;
    private final RpgMobService mobService;
    private final RpgMobRegistry mobRegistry;
    private final File file;
    private final Map<String, FieldSpawner> spawners = new LinkedHashMap<>();
    /** Players who are in an editor do not make mobs appear. */
    private Predicate<Player> editing = player -> false;
    private BukkitTask task;

    public FieldSpawnerService(JavaPlugin plugin, RpgMobService mobService, RpgMobRegistry mobRegistry) {
        this.plugin = plugin;
        this.mobService = mobService;
        this.mobRegistry = mobRegistry;
        this.file = new File(plugin.getDataFolder(), "spawners.yml");
    }

    public void setEditing(Predicate<Player> editing) {
        this.editing = editing;
    }

    public void start() {
        load();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    /** Removes every mob made here (the server stops, or the spawners are reloaded). */
    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (FieldSpawner spawner : spawners.values()) clearAlive(spawner);
    }

    public Collection<FieldSpawner> all() {
        return spawners.values();
    }

    public Optional<FieldSpawner> find(String id) {
        return Optional.ofNullable(spawners.get(id));
    }

    // ---- settings -------------------------------------------------------------------------------------------------

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("field-spawner." + key, fallback);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("field-spawner.enabled", true);
    }

    public int defaultRadius() {
        return (int) number("trigger-radius", 24);
    }

    // ---- editing the list -----------------------------------------------------------------------------------------

    public FieldSpawner add(Location at, String mobId) {
        int index = 1;
        while (spawners.containsKey("s" + index)) index++;
        FieldSpawner spawner = new FieldSpawner("s" + index, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), mobId,
                (int) number("min-count", 1), (int) number("max-count", 2), (int) number("max-alive", 2), defaultRadius());
        spawner.min(spawner.min());
        spawner.maxAlive(spawner.maxAlive());
        spawner.radius(spawner.radius());
        spawners.put(spawner.id(), spawner);
        save();
        return spawner;
    }

    public void remove(FieldSpawner spawner) {
        clearAlive(spawner);
        spawners.remove(spawner.id());
        save();
    }

    /** Throws away the mobs of a spawner whose settings changed, so it starts again with the new ones. */
    public void reset(FieldSpawner spawner) {
        clearAlive(spawner);
        spawner.nextSpawnAt = 0L;
    }

    // ---- the loop ---------------------------------------------------------------------------------------------------

    private void tick() {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead() || editing.test(player)) continue;
            players.add(player);
        }
        double leash = number("leash-distance", 64);
        long idleMillis = Math.round(number("idle-despawn-minutes", 10) * 60_000.0);
        double combat = number("combat-radius", 16);
        double respawn = number("respawn-seconds", 20);
        for (FieldSpawner spawner : spawners.values()) {
            World world = Bukkit.getWorld(spawner.worldName());
            if (world == null) continue;
            tend(spawner, world, players, now, leash, idleMillis, combat);
            if (spawner.aliveCount() >= spawner.maxAlive() || now < spawner.nextSpawnAt) continue;
            if (!world.isChunkLoaded((int) Math.floor(spawner.x()) >> 4, (int) Math.floor(spawner.z()) >> 4)) continue;
            if (!playerNear(spawner, world, players, spawner.radius())) continue;
            spawn(spawner, world);
            spawner.nextSpawnAt = now + Math.round(respawn * 1000.0);
        }
    }

    private boolean playerNear(FieldSpawner spawner, World world, List<Player> players, double radius) {
        Location center = spawner.in(world);
        for (Player player : players) {
            if (player.getWorld().equals(world) && player.getLocation().distanceSquared(center) <= radius * radius) return true;
        }
        return false;
    }

    /** Forgets the dead ones; removes the ones that wandered off or have been idle for too long. */
    private void tend(FieldSpawner spawner, World world, List<Player> players, long now, double leash, long idleMillis, double combat) {
        Location center = spawner.in(world);
        for (UUID uuid : new ArrayList<>(spawner.alive.keySet())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                mobService.forget(uuid);
                spawner.alive.remove(uuid);
                continue;
            }
            boolean far = !living.getWorld().equals(world) || living.getLocation().distanceSquared(center) > leash * leash;
            boolean idle = now - spawner.alive.get(uuid) >= idleMillis && !playerNear(living.getLocation(), world, players, combat);
            if (far || idle) {
                mobService.remove(living);
                living.remove();
                spawner.alive.remove(uuid);
            }
        }
    }

    private boolean playerNear(Location at, World world, List<Player> players, double radius) {
        for (Player player : players) {
            if (player.getWorld().equals(world) && player.getLocation().distanceSquared(at) <= radius * radius) return true;
        }
        return false;
    }

    private void spawn(FieldSpawner spawner, World world) {
        if (mobRegistry.find(spawner.mobId()).isEmpty()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int want = spawner.min() >= spawner.max() ? spawner.min() : random.nextInt(spawner.min(), spawner.max() + 1);
        int count = Math.min(want, spawner.maxAlive() - spawner.aliveCount());
        double spread = number("spread", 3.0);
        long now = System.currentTimeMillis();
        for (int index = 0; index < count; index++) {
            Location at = spot(spawner, world, spread, random);
            at.setYaw(random.nextFloat() * 360f - 180f);
            Optional<LivingEntity> spawned = mobService.spawnAt(at, spawner.mobId());
            if (spawned.isEmpty()) continue;
            LivingEntity entity = spawned.get();
            entity.setPersistent(false);
            entity.addScoreboardTag(TAG);
            spawner.alive.put(entity.getUniqueId(), now);
        }
    }

    /** A spot near the spawner on the ground (the spawner's own spot when nothing fits). */
    private Location spot(FieldSpawner spawner, World world, double spread, ThreadLocalRandom random) {
        Location center = spawner.in(world);
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = attempt == 0 && spread <= 0 ? 0 : Math.sqrt(random.nextDouble()) * spread;
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            for (int dy = 2; dy >= -4; dy--) {
                Block feet = world.getBlockAt((int) Math.floor(x), (int) Math.floor(center.getY()) + dy, (int) Math.floor(z));
                if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable()
                        && !feet.getRelative(0, -1, 0).isPassable() && !feet.getRelative(0, -1, 0).isLiquid()) {
                    return new Location(world, x, feet.getY(), z);
                }
            }
        }
        return center.clone();
    }

    private void clearAlive(FieldSpawner spawner) {
        for (UUID uuid : spawner.alive.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                mobService.remove(entity);
                entity.remove();
            } else {
                mobService.forget(uuid);
            }
        }
        spawner.alive.clear();
    }

    /** Mobs a crash left in the world (they are not saved normally) are removed when their chunk loads. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!entity.getScoreboardTags().contains(TAG)) continue;
            boolean known = false;
            for (FieldSpawner spawner : spawners.values()) {
                if (spawner.alive.containsKey(entity.getUniqueId())) {
                    known = true;
                    break;
                }
            }
            if (known) continue;
            mobService.remove(entity);
            entity.remove();
        }
    }

    // ---- file ---------------------------------------------------------------------------------------------------------

    private void load() {
        spawners.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("spawners");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null || entry.getString("world") == null || entry.getString("mob") == null) continue;
            FieldSpawner spawner = new FieldSpawner(id, entry.getString("world"), entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                    entry.getString("mob"), 1, 1, 1, 24);
            spawner.max(entry.getInt("max", 2));
            spawner.min(entry.getInt("min", 1));
            spawner.maxAlive(entry.getInt("max-alive", 2));
            spawner.radius(entry.getInt("radius", defaultRadius()));
            spawners.put(id, spawner);
        }
        plugin.getLogger().info("field spawners: " + spawners.size());
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (FieldSpawner spawner : spawners.values()) {
            String base = "spawners." + spawner.id() + ".";
            yaml.set(base + "world", spawner.worldName());
            yaml.set(base + "x", spawner.x());
            yaml.set(base + "y", spawner.y());
            yaml.set(base + "z", spawner.z());
            yaml.set(base + "mob", spawner.mobId());
            yaml.set(base + "min", spawner.min());
            yaml.set(base + "max", spawner.max());
            yaml.set(base + "max-alive", spawner.maxAlive());
            yaml.set(base + "radius", spawner.radius());
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("field spawners: could not save " + file.getName() + ": " + e.getMessage());
        }
    }

    /** Re-reads the file (the mobs are removed first, and appear again when someone comes close). */
    public void reload() {
        for (FieldSpawner spawner : spawners.values()) clearAlive(spawner);
        load();
    }
}
