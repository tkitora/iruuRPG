package net.tkgon.mc.iruuRPG.model;

import net.tkgon.mc.iruuRPG.mob.MobModels;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The model feature (like ModelEngine): Blockbench models ({@code .bbmodel}, in {@code plugins/iruuRPG/models/}) are turned into a resource pack of their own
 * ({@code resourcepacks/iruumodel.zip}) and shown on mobs as item displays that follow the mob and play the model's animations. A mob uses a model with a {@code model:} section in
 * its yaml (id, scale); behavior trees play animations with the {@code play-animation} task; the attack and skill of a behavior-tree mob play the animations of those names.
 *
 * <p>Self-contained, like the procedural tool: this package, the folder {@code models/}, the zip {@code resourcepacks/iruumodel.zip}, the config section {@code model:} and the command
 * {@code /iruumodel} are all there is (docs/models.md lists the few places that call into it). {@code /iruumodel uninstall} removes every display and file it made.
 */
public final class ModelModule implements Listener, MobModels {

    private final JavaPlugin plugin;
    private final ModelRegistry registry;
    private final NamespacedKey modelKey;
    private final NamespacedKey scaleKey;
    private final NamespacedKey partKey;
    private final Map<UUID, ModelInstance> instances = new HashMap<>();
    private final List<UUID> demos = new ArrayList<>();
    private BukkitTask task;
    private final Map<String, Boolean> warned = new HashMap<>();

    /** Starts the feature: reads the models, makes the pack, listens, runs the update. Never throws: a failure is logged and the feature is simply not there. */
    public static ModelModule start(JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("model.enabled", true)) return null;
        try {
            ModelModule module = new ModelModule(plugin);
            module.registry.extractSample();
            module.registry.reload(plugin.getConfig().getBoolean("model.auto-build", true));
            for (String problem : module.registry.problems()) plugin.getLogger().warning("models: " + problem);
            plugin.getLogger().info("Loaded " + module.registry.all().size() + " models.");
            plugin.getServer().getPluginManager().registerEvents(module, plugin);
            org.bukkit.command.PluginCommand command = plugin.getCommand("iruumodel");
            if (command != null) {
                ModelCommand handler = new ModelCommand(module);
                command.setExecutor(handler);
                command.setTabCompleter(handler);
            }
            int period = Math.max(1, plugin.getConfig().getInt("model.update-ticks", 2));
            module.task = plugin.getServer().getScheduler().runTaskTimer(plugin, module::tick, 20L, period);
            return module;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Model feature not started: " + exception);
            return null;
        }
    }

    private ModelModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.registry = new ModelRegistry(plugin);
        this.modelKey = new NamespacedKey(plugin, "model_id");
        this.scaleKey = new NamespacedKey(plugin, "model_scale");
        this.partKey = new NamespacedKey(plugin, "model_part");
    }

    /** The plugin stops: every display is taken off (the mobs keep their model id, and get it again when they load). */
    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (ModelInstance instance : instances.values()) instance.remove();
        instances.clear();
        removeDemos();
    }

    ModelRegistry registry() {
        return registry;
    }

    JavaPlugin plugin() {
        return plugin;
    }

    // ---- the mobs' side (MobModels) --------------------------------------------------------------------------------------------

    @Override
    public void attach(LivingEntity entity, Map<String, Object> section) {
        String id = section.get("id") == null ? "" : String.valueOf(section.get("id"));
        double scale = section.get("scale") instanceof Number number ? number.doubleValue() : 1.0;
        boolean hide = !(section.get("hide-base") instanceof Boolean flag) || flag;
        attach(entity, id, scale, hide);
    }

    boolean attach(LivingEntity entity, String id, double scale, boolean hideBase) {
        BbModel model = registry.find(id).orElse(null);
        if (model == null) {
            if (warned.putIfAbsent(id, true) == null) plugin.getLogger().warning("models: there is no model '" + id + "' (a mob uses it)");
            return false;
        }
        ModelInstance old = instances.remove(entity.getUniqueId());
        if (old != null) old.remove();
        entity.getPersistentDataContainer().set(modelKey, PersistentDataType.STRING, id);
        entity.getPersistentDataContainer().set(scaleKey, PersistentDataType.DOUBLE, scale);
        Material item = Material.matchMaterial(plugin.getConfig().getString("model.base-item", "PAPER"));
        ModelInstance instance = new ModelInstance(model, entity, Math.max(0.05, scale), Math.max(1, plugin.getConfig().getInt("model.update-ticks", 2)),
                item == null ? Material.PAPER : item, partKey, hideBase, plugin.getConfig().getDouble("model.walk-speed", 1.0));
        instances.put(entity.getUniqueId(), instance);
        instance.update(Bukkit.getCurrentTick());
        return true;
    }

    @Override
    public boolean play(LivingEntity entity, String animation) {
        ModelInstance instance = instances.get(entity.getUniqueId());
        return instance != null && instance.play(animation, Bukkit.getCurrentTick());
    }

    @Override
    public double length(LivingEntity entity, String animation) {
        ModelInstance instance = instances.get(entity.getUniqueId());
        return instance == null ? 0.0 : instance.length(animation);
    }

    @Override
    public double height(LivingEntity entity) {
        ModelInstance instance = instances.get(entity.getUniqueId());
        return instance == null ? 0.0 : instance.top();
    }

    @Override
    public boolean hurt(LivingEntity entity) {
        ModelInstance instance = instances.get(entity.getUniqueId());
        return instance != null && !instance.busy(Bukkit.getCurrentTick()) && instance.play("hurt", Bukkit.getCurrentTick());
    }

    @Override
    public void detach(LivingEntity entity) {
        ModelInstance instance = instances.remove(entity.getUniqueId());
        if (instance != null) {
            instance.remove();
            instance.showBase();
        }
        entity.getPersistentDataContainer().remove(modelKey);
        entity.getPersistentDataContainer().remove(scaleKey);
    }

    // ---- running -----------------------------------------------------------------------------------------------------------------

    private void tick() {
        int now = Bukkit.getCurrentTick();
        double view = plugin.getConfig().getDouble("model.view-distance", 48.0);
        for (Map.Entry<UUID, ModelInstance> entry : new ArrayList<>(instances.entrySet())) {
            ModelInstance instance = entry.getValue();
            LivingEntity base = instance.base();
            if (!instance.dying() && !base.isValid()) {
                instance.remove();
                instances.remove(entry.getKey());
                continue;
            }
            if (instance.finished(now)) {
                instance.remove();
                instances.remove(entry.getKey());
                continue;
            }
            if (!anyoneNear(base, view)) continue;
            // every 2 seconds the equipment is hidden again from the players near (the server sends it again when it changes)
            if (now % 40 < Math.max(1, plugin.getConfig().getInt("model.update-ticks", 2))) {
                for (Player player : base.getWorld().getPlayers()) {
                    if (player.getLocation().distanceSquared(base.getLocation()) <= view * view) instance.hideEquipment(player);
                }
            }
            try {
                instance.update(now);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "models: a model stopped after an error", exception);
                instance.remove();
                instances.remove(entry.getKey());
            }
        }
    }

    private static boolean anyoneNear(Entity base, double range) {
        World world = base.getWorld();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(base.getLocation()) <= range * range) return true;
        }
        return false;
    }

    /** A player starts to see a mob with a model: its equipment is hidden from them (a tick later, after the server sent the real one). */
    @EventHandler
    public void onTrack(io.papermc.paper.event.player.PlayerTrackEntityEvent event) {
        ModelInstance instance = instances.get(event.getEntity().getUniqueId());
        if (instance == null) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) instance.hideEquipment(player);
        }, 1L);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        ModelInstance instance = instances.get(event.getEntity().getUniqueId());
        if (instance != null) instance.die(Bukkit.getCurrentTick());
    }

    /** A mob that loads with a model id (it was saved with one) gets its model again. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof LivingEntity living) || instances.containsKey(entity.getUniqueId())) continue;
            String id = entity.getPersistentDataContainer().get(modelKey, PersistentDataType.STRING);
            if (id == null) continue;
            Double scale = entity.getPersistentDataContainer().get(scaleKey, PersistentDataType.DOUBLE);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (living.isValid() && !instances.containsKey(living.getUniqueId())) attach(living, id, scale == null ? 1.0 : scale, true);
            });
        }
    }

    // ---- for the command ---------------------------------------------------------------------------------------------------------

    /** Shows a model standing where the player is, on an invisible marker, for looking at it and its animations. */
    boolean demo(Player player, String id, double scale) {
        ArmorStand stand = player.getWorld().spawn(player.getLocation(), ArmorStand.class, spawned -> {
            spawned.setMarker(true);
            spawned.setGravity(false);
            spawned.setPersistent(false);
            spawned.setInvisible(true);
            spawned.setBasePlate(false);
        });
        demos.add(stand.getUniqueId());
        if (!attach(stand, id, scale, true)) {
            stand.remove();
            demos.remove(stand.getUniqueId());
            return false;
        }
        return true;
    }

    /** Plays an animation on the demo models. */
    int playDemos(String animation) {
        int count = 0;
        for (UUID id : demos) {
            ModelInstance instance = instances.get(id);
            if (instance != null && instance.play(animation, Bukkit.getCurrentTick())) count++;
        }
        return count;
    }

    void removeDemos() {
        for (UUID id : new ArrayList<>(demos)) {
            ModelInstance instance = instances.remove(id);
            if (instance != null) instance.remove();
            Entity stand = Bukkit.getEntity(id);
            if (stand != null) stand.remove();
        }
        demos.clear();
    }

    /**
     * Takes the whole feature out: every display of the models (also any left in unloaded-and-loaded worlds), the model of every mob that has one (it gets its own body back), the
     * demos, and the files it made (models/ and the pack). The mobs' `model:` sections in their yaml are not touched (they are ignored once the feature is gone).
     */
    int uninstall() {
        int removed = 0;
        for (ModelInstance instance : new ArrayList<>(instances.values())) {
            instance.remove();
            instance.showBase();
            removed++;
        }
        instances.clear();
        removeDemos();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(partKey, PersistentDataType.STRING)) {
                    entity.remove();
                    removed++;
                }
                if (entity instanceof LivingEntity living && entity.getPersistentDataContainer().has(modelKey, PersistentDataType.STRING)) {
                    entity.getPersistentDataContainer().remove(modelKey);
                    entity.getPersistentDataContainer().remove(scaleKey);
                    living.setInvisible(false);
                }
            }
        }
        deleteTree(registry.folder());
        registry.packFile().delete();
        return removed;
    }

    private static void deleteTree(java.io.File file) {
        java.io.File[] children = file.listFiles();
        if (children != null) for (java.io.File child : children) deleteTree(child);
        file.delete();
    }

    /** The number of models on mobs now. */
    int active() {
        return instances.size();
    }
}
