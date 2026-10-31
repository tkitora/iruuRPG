package net.tkgon.mc.iruuRPG.cutscene;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Short scripted scenes for one player (cutscenes/&lt;id&gt;.yml, the yml repository's), written like the tutorial's
 * script: {@code steps:} of {@code say} (an actor or the narration speaks, paced by its length), {@code narrate} (a
 * note for the writer: only its time counts) and {@code do} (an action). Actors are declared under {@code actors:}
 * (name, look, where) and only the player sees them; places are relative to where the player stood when the scene
 * began ({@code forward}, {@code right}, {@code up}) or absolute ({@code world, x, y, z}). While it plays the player
 * cannot move (unless {@code lock-move: false}), hurt anything or be hurt. The quest lines call it and hear its end.
 *
 * <pre>
 * actors:
 *   ben: { name: "農夫ベン", look: { type: villager, profession: FARMER }, at: { forward: 3 } }
 * steps:
 *   - do: { action: show, actor: ben }
 *   - say: "来てくれたか"
 *     speaker: ben
 *   - do: { action: move, actor: ben, to: { forward: 2, right: 2 }, ticks: 30 }
 *   - narrate: "ベンが畑を指さす"
 *     ticks: 20
 *   - do: { action: blackout, ticks: 40 }
 * </pre>
 * Actions: show, hide, move, face (an actor toward the player), look (the player toward an actor), wait, title, sound,
 * blackout, teleport.
 */
public final class CutsceneService implements Listener {

    public static final String TAG = "iruu_cutscene";

    private final JavaPlugin plugin;
    private final NamespacedKey markKey;
    private final Map<UUID, Scene> scenes = new HashMap<>();

    private static final class Scene {
        private final String id;
        private final Location origin;
        private final List<Map<?, ?>> steps;
        private final Map<?, ?> actorsSpec;
        private final boolean lockMove;
        private final Consumer<Boolean> onEnd;
        private final Map<String, LivingEntity> actors = new HashMap<>();
        private final Map<String, TextDisplay> bubbles = new HashMap<>();
        private final List<BukkitTask> tasks = new ArrayList<>();
        private int index;
        private boolean over;

        private Scene(String id, Location origin, Map<?, ?> raw, Consumer<Boolean> onEnd) {
            this.id = id;
            this.origin = origin;
            List<Map<?, ?>> list = new ArrayList<>();
            if (raw.get("steps") instanceof List<?> items) {
                for (Object item : items) if (item instanceof Map<?, ?> map) list.add(map);
            }
            this.steps = list;
            this.actorsSpec = raw.get("actors") instanceof Map<?, ?> map ? map : Map.of();
            this.lockMove = !Boolean.FALSE.equals(raw.get("lock-move"));
            this.onEnd = onEnd;
        }
    }

    public CutsceneService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.markKey = new NamespacedKey(plugin, "cutscene");
    }

    /** True when cutscenes/&lt;id&gt;.yml exists. */
    public boolean exists(String id) {
        return id != null && id.matches("[A-Za-z0-9_\\-]+") && file(id).isFile();
    }

    public boolean playing(Player player) {
        return scenes.containsKey(player.getUniqueId());
    }

    private File file(String id) {
        return new File(plugin.getDataFolder(), "cutscenes/" + id + ".yml");
    }

    /**
     * Plays a scene for the player; {@code onEnd} gets true when it played to its end, false when it could not start or
     * was cut short (the player left, or another scene began). Never throws.
     */
    public void play(Player player, String id, Consumer<Boolean> onEnd) {
        Consumer<Boolean> end = onEnd == null ? done -> { } : onEnd;
        Map<?, ?> raw = null;
        if (exists(id)) {
            try (Reader reader = Files.newBufferedReader(file(id).toPath(), StandardCharsets.UTF_8)) {
                if (new Yaml().load(reader) instanceof Map<?, ?> map) raw = map;
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("cutscene: " + id + " could not be read: " + e.getMessage());
            }
        }
        if (raw == null) {
            plugin.getLogger().warning("cutscene: no cutscenes/" + id + ".yml");
            end.accept(false);
            return;
        }
        stop(player, false);
        Scene scene = new Scene(id, player.getLocation().clone(), raw, end);
        scenes.put(player.getUniqueId(), scene);
        scene.tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> hold(player, scene), 1L, 1L));
        next(player, scene);
    }

    /** Cuts the player's scene short (its callback gets false). */
    public void stop(Player player, boolean announce) {
        Scene scene = scenes.get(player.getUniqueId());
        if (scene == null) return;
        finish(player, scene, false);
        if (announce) player.sendMessage(Component.text("[カットシーン] 中断した。", NamedTextColor.GRAY));
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(scenes.keySet())) {
            Player player = Bukkit.getPlayer(id);
            Scene scene = scenes.get(id);
            if (scene != null) finish(player, scene, false);
        }
    }

    // ---- running the steps ------------------------------------------------------------------------------------------

    private void next(Player player, Scene scene) {
        if (scene.over) return;
        if (!player.isOnline()) {
            finish(player, scene, false);
            return;
        }
        if (scene.index >= scene.steps.size()) {
            finish(player, scene, true);
            return;
        }
        Map<?, ?> step = scene.steps.get(scene.index++);
        long delay = number(step.get("ticks"), -1);
        try {
            if (step.get("say") != null) {
                String text = step.get("say").toString();
                String speaker = step.get("speaker") == null ? "narration" : step.get("speaker").toString();
                say(player, scene, speaker, text);
                if (delay < 0) delay = readTicks(text);
            }
            if (step.get("narrate") != null && delay < 0) delay = 30;
            if (step.get("do") != null) {
                Map<?, ?> action = step.get("do") instanceof Map<?, ?> map ? map : Map.of("action", step.get("do"));
                long took = act(player, scene, action);
                delay = Math.max(delay, took);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("cutscene: " + scene.id + " step " + scene.index + ": " + e.getMessage());
        }
        scene.tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> next(player, scene), Math.max(1L, delay)));
    }

    /** One action; returns how long it takes (the next step waits for it). */
    private long act(Player player, Scene scene, Map<?, ?> action) {
        String type = String.valueOf(action.get("action")).toLowerCase(Locale.ROOT);
        String actor = action.get("actor") == null ? null : action.get("actor").toString();
        long ticks = number(action.get("ticks"), 0);
        switch (type) {
            case "show" -> show(player, scene, actor);
            case "hide" -> hide(scene, actor);
            case "move" -> {
                LivingEntity entity = scene.actors.get(actor);
                Location to = place(scene, action.get("to"));
                if (entity == null || to == null) return 0;
                long total = Math.max(1, ticks == 0 ? 20 : ticks);
                Location from = entity.getLocation();
                scene.tasks.add(new org.bukkit.scheduler.BukkitRunnable() {
                    private int tick;

                    @Override
                    public void run() {
                        if (scene.over || !entity.isValid()) {
                            cancel();
                            return;
                        }
                        tick++;
                        double t = Math.min(1.0, tick / (double) total);
                        double eased = t * t * (3 - 2 * t);
                        Location at = from.clone().add(to.toVector().subtract(from.toVector()).multiply(eased));
                        Vector way = to.toVector().subtract(from.toVector());
                        if (way.lengthSquared() > 1.0E-6) at.setDirection(way);
                        entity.teleport(at);
                        if (t >= 1.0) cancel();
                    }
                }.runTaskTimer(plugin, 1L, 1L));
                return total;
            }
            case "face" -> {
                LivingEntity entity = scene.actors.get(actor);
                if (entity != null) entity.teleport(entity.getLocation().setDirection(player.getEyeLocation().toVector().subtract(entity.getEyeLocation().toVector())));
            }
            case "look" -> {
                LivingEntity entity = scene.actors.get(actor);
                if (entity != null) {
                    Vector to = entity.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
                    Location turned = player.getLocation().setDirection(to);
                    player.setRotation(turned.getYaw(), turned.getPitch());
                }
            }
            case "wait" -> {
                return ticks;
            }
            case "title" -> player.showTitle(Title.title(
                    Component.text(String.valueOf(action.get("title") == null ? "" : action.get("title")), NamedTextColor.GOLD),
                    Component.text(String.valueOf(action.get("subtitle") == null ? "" : action.get("subtitle")), NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(Math.max(1000, ticks * 50)), Duration.ofMillis(600))));
            case "sound" -> {
                try {
                    Sound sound = Sound.valueOf(String.valueOf(action.get("sound")).toUpperCase(Locale.ROOT).replace('.', '_'));
                    player.playSound(player.getLocation(), sound, (float) number(action.get("volume"), 1), (float) Math.max(0.5, Math.min(2.0, numberD(action.get("pitch"), 1.0))));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("cutscene: " + scene.id + ": no sound " + action.get("sound"));
                }
            }
            case "blackout" -> {
                long length = Math.max(20, ticks == 0 ? 40 : ticks);
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (int) length + 10, 0, false, false, false));
                return length;
            }
            case "teleport" -> {
                Location to = place(scene, action.get("to"));
                if (to != null) {
                    to.setYaw(player.getLocation().getYaw());
                    to.setPitch(player.getLocation().getPitch());
                    player.teleport(to);
                    scene.origin.setWorld(to.getWorld());
                    scene.origin.setX(to.getX());
                    scene.origin.setY(to.getY());
                    scene.origin.setZ(to.getZ());
                }
            }
            default -> plugin.getLogger().warning("cutscene: " + scene.id + ": unknown action '" + type + "'");
        }
        return ticks;
    }

    private void say(Player player, Scene scene, String speaker, String text) {
        if (speaker.equalsIgnoreCase("narration")) {
            player.sendMessage(Component.text(text, NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            return;
        }
        String name = speaker;
        if (scene.actorsSpec.get(speaker) instanceof Map<?, ?> spec && spec.get("name") != null) name = spec.get("name").toString();
        player.sendMessage(Component.text(name + " ", NamedTextColor.GOLD).append(Component.text("「" + text + "」", NamedTextColor.WHITE)));
        LivingEntity entity = scene.actors.get(speaker);
        if (entity == null) return;
        TextDisplay bubble = scene.bubbles.get(speaker);
        Location over = entity.getLocation().add(0, entity.getHeight() + 0.6, 0);
        if (bubble == null || !bubble.isValid()) {
            bubble = over.getWorld().spawn(over, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setVisibleByDefault(false);
                display.setPersistent(false);
                display.setLineWidth(180);
                mark(display);
            });
            player.showEntity(plugin, bubble);
            scene.bubbles.put(speaker, bubble);
        }
        bubble.text(Component.text(text));
    }

    private void show(Player player, Scene scene, String actor) {
        if (actor == null || !(scene.actorsSpec.get(actor) instanceof Map<?, ?> spec)) {
            plugin.getLogger().warning("cutscene: " + scene.id + ": no actor '" + actor + "'");
            return;
        }
        hide(scene, actor);
        Location at = place(scene, spec.get("at"));
        if (at == null) at = scene.origin.clone().add(scene.origin.getDirection().setY(0).normalize().multiply(3));
        at.setDirection(player.getLocation().toVector().subtract(at.toVector()));
        Map<?, ?> look = spec.get("look") instanceof Map<?, ?> map ? map : Map.of();
        String type = String.valueOf(look.get("type") == null ? "villager" : look.get("type"));
        LivingEntity entity;
        if (type.equals("entity") && look.get("entity") != null) {
            EntityType kind = EntityType.valueOf(look.get("entity").toString().toUpperCase(Locale.ROOT));
            if (kind.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(kind.getEntityClass()) || kind == EntityType.PLAYER) return;
            @SuppressWarnings("unchecked")
            Class<? extends LivingEntity> living = (Class<? extends LivingEntity>) kind.getEntityClass();
            entity = at.getWorld().spawn(at, living, spawned -> spawned.setVisibleByDefault(false));
        } else {
            entity = at.getWorld().spawn(at, Villager.class, villager -> {
                villager.setVisibleByDefault(false);
                try {
                    villager.setProfession(Villager.Profession.valueOf(String.valueOf(look.get("profession") == null ? "NONE" : look.get("profession")).toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    villager.setProfession(Villager.Profession.NONE);
                }
            });
        }
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(false);
        entity.setCollidable(false);
        entity.setGravity(false);
        if (spec.get("name") != null) {
            entity.customName(Component.text(spec.get("name").toString()));
            entity.setCustomNameVisible(true);
        }
        mark(entity);
        player.showEntity(plugin, entity);
        scene.actors.put(actor, entity);
    }

    private void hide(Scene scene, String actor) {
        LivingEntity entity = scene.actors.remove(actor);
        if (entity != null) entity.remove();
        TextDisplay bubble = scene.bubbles.remove(actor);
        if (bubble != null) bubble.remove();
    }

    /** A place of the script: absolute when it has x, else forward / right / up from where the scene began. */
    private Location place(Scene scene, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return null;
        if (map.get("x") != null) {
            World world = map.get("world") == null ? scene.origin.getWorld() : Bukkit.getWorld(map.get("world").toString());
            if (world == null) return null;
            return new Location(world, numberD(map.get("x"), 0), numberD(map.get("y"), 64), numberD(map.get("z"), 0));
        }
        Vector forward = scene.origin.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-6) forward = new Vector(0, 0, 1);
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        return scene.origin.clone().add(forward.multiply(numberD(map.get("forward"), 0)))
                .add(right.multiply(numberD(map.get("right"), 0))).add(0, numberD(map.get("up"), 0), 0);
    }

    /** Every tick: the speech bubbles follow their actors, and the player stays where the scene began. */
    private void hold(Player player, Scene scene) {
        if (scene.over || !player.isOnline()) return;
        for (var entry : scene.bubbles.entrySet()) {
            LivingEntity entity = scene.actors.get(entry.getKey());
            if (entity != null && entry.getValue().isValid()) entry.getValue().teleport(entity.getLocation().add(0, entity.getHeight() + 0.6, 0));
        }
        if (!scene.lockMove) return;
        Location here = player.getLocation();
        if (here.getWorld().equals(scene.origin.getWorld()) && here.distanceSquared(scene.origin) > 0.04) {
            Location back = scene.origin.clone();
            back.setYaw(here.getYaw());
            back.setPitch(here.getPitch());
            player.teleport(back);
            player.setVelocity(new Vector());
        }
    }

    private void finish(Player player, Scene scene, boolean completed) {
        if (scene.over) return;
        scene.over = true;
        scenes.values().remove(scene);
        for (BukkitTask task : scene.tasks) task.cancel();
        for (String actor : new ArrayList<>(scene.actors.keySet())) hide(scene, actor);
        for (TextDisplay bubble : scene.bubbles.values()) bubble.remove();
        if (player != null && player.isOnline()) player.removePotionEffect(PotionEffectType.BLINDNESS);
        try {
            scene.onEnd.accept(completed);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "cutscene: the end of " + scene.id + " failed", e);
        }
    }

    private void mark(Entity entity) {
        entity.addScoreboardTag(TAG);
        entity.getPersistentDataContainer().set(markKey, org.bukkit.persistence.PersistentDataType.STRING, "1");
    }

    /** Takes away actors left over from before a restart. */
    public void sweep() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(TAG)) entity.remove();
            }
        }
    }

    // ---- while a scene plays: no harm either way --------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && playing(player)) event.setCancelled(true);
        if (event.getEntity().getScoreboardTags().contains(TAG)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && playing(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getScoreboardTags().contains(TAG)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Scene scene = scenes.get(event.getPlayer().getUniqueId());
        if (scene != null) finish(event.getPlayer(), scene, false);
    }

    // ---- small helpers ------------------------------------------------------------------------------------------------

    static long readTicks(String text) {
        double seconds = 1.2 + 0.15 * text.length();
        return Math.max(40L, Math.min(160L, Math.round(seconds * 20.0)));
    }

    private static long number(Object raw, long fallback) {
        return Math.round(numberD(raw, fallback));
    }

    private static double numberD(Object raw, double fallback) {
        if (raw instanceof Number number) return number.doubleValue();
        if (raw != null) {
            try {
                return Double.parseDouble(raw.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
