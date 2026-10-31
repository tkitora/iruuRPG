package net.tkgon.mc.iruuRPG.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.mob.MobModels;
import net.tkgon.mc.iruuRPG.questline.QuestlineService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * The NPCs of the quests: npcs.yml (written here and by /npc edit; the yml repository does not hold it, so editing in
 * the game never stops its git pull), the entities standing for them (no AI, can't be hurt, not saved with the world:
 * made when their chunk loads and taken away when it unloads), right-clicks on them (the quest lines'
 * {@link QuestlineService#interact}), and the quest marks over their heads ("!" a line to take, "?" a line waiting on
 * it), shown to each player only for their own quests.
 */
public final class NpcService implements Listener {

    public static final String TAG = "iruu_npc";
    private static final double MARK_RANGE = 40.0;
    private static final double FACE_RANGE = 5.0;
    private static final long CLICK_GAP_MILLIS = 400L;

    private final JavaPlugin plugin;
    private final QuestlineService questlines;
    private final NamespacedKey idKey;
    private final File file;
    private MobModels models;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final Map<String, LivingEntity> entities = new HashMap<>();
    /** The two marks over each NPC ("!" and "?") and who sees each now. */
    private final Map<String, TextDisplay[]> marks = new HashMap<>();
    private final Map<UUID, Set<UUID>> markViewers = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private BiPredicate<Player, Npc> editorClick = (player, npc) -> false;
    private BukkitTask task;
    private int ticks;

    public NpcService(JavaPlugin plugin, QuestlineService questlines) {
        this.plugin = plugin;
        this.questlines = questlines;
        this.idKey = new NamespacedKey(plugin, "npc_id");
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
        questlines.setSpeakerNames(id -> find(id).map(Npc::name).orElse(id));
        questlines.setIdleLines(id -> find(id).map(Npc::idle).orElse(List.of()));
        questlines.setSpeechBubbles(this::speak);
        questlines.setAssignedNpcs(this::npcsOffering);
    }

    /** The NPCs that offer a line because it was chosen for them in the editor. */
    private Set<String> npcsOffering(String line) {
        Set<String> out = new HashSet<>();
        for (Npc npc : npcs.values()) {
            if (npc.quests().contains(line)) out.add(npc.id());
        }
        return out;
    }

    public void setModels(MobModels models) {
        this.models = models;
    }

    /** What the editor does with a click on an NPC (true = it used the click). */
    public void setEditorClick(BiPredicate<Player, Npc> editorClick) {
        this.editorClick = editorClick == null ? (player, npc) -> false : editorClick;
    }

    public static boolean isNpc(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(TAG);
    }

    public void start() {
        load();
        sweep();
        for (Npc npc : npcs.values()) spawnIfLoaded(npc);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (String id : new ArrayList<>(entities.keySet())) despawn(id);
    }

    // ---- the file -----------------------------------------------------------------------------------------------------

    public void load() {
        npcs.clear();
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("npcs");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;
            ConfigurationSection look = entry.getConfigurationSection("look");
            Npc.Look parsed = look == null ? Npc.Look.villager("FARMER", "PLAINS") : new Npc.Look(
                    look.getString("type", "villager"), look.getString("profession", "NONE"), look.getString("villager-type", "PLAINS"),
                    look.getString("entity"), look.getBoolean("baby"), look.getString("model"), look.getDouble("scale", 1.0));
            Npc npc = new Npc(id, entry.getString("name", id), new Location(null, 0, 0, 0), parsed);
            npc.set(entry.getString("world", "world"), entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"), (float) entry.getDouble("yaw"));
            npc.idle().addAll(entry.getStringList("idle"));
            npc.quests().addAll(entry.getStringList("quests"));
            npcs.put(id, npc);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("NPCs of the quest lines. Written by /npc edit in the game (not by the yml repository).",
                "look.type: villager (profession, villager-type) / entity (entity, baby) / model (model, scale)"));
        yaml.set("format", 1);
        for (Npc npc : npcs.values()) {
            String at = "npcs." + npc.id();
            yaml.set(at + ".name", npc.name());
            yaml.set(at + ".world", npc.worldName());
            yaml.set(at + ".x", round(npc.x()));
            yaml.set(at + ".y", round(npc.y()));
            yaml.set(at + ".z", round(npc.z()));
            yaml.set(at + ".yaw", round(npc.yaw()));
            Npc.Look look = npc.look();
            yaml.set(at + ".look.type", look.type());
            switch (look.type()) {
                case "entity" -> {
                    yaml.set(at + ".look.entity", look.entity());
                    if (look.baby()) yaml.set(at + ".look.baby", true);
                }
                case "model" -> {
                    yaml.set(at + ".look.model", look.model());
                    yaml.set(at + ".look.scale", look.scale());
                }
                default -> {
                    yaml.set(at + ".look.profession", look.profession());
                    yaml.set(at + ".look.villager-type", look.villagerType());
                }
            }
            if (!npc.idle().isEmpty()) yaml.set(at + ".idle", new ArrayList<>(npc.idle()));
            if (!npc.quests().isEmpty()) yaml.set(at + ".quests", new ArrayList<>(npc.quests()));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("npc: could not save npcs.yml: " + e.getMessage());
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ---- changes (the editor and /npc) --------------------------------------------------------------------------------

    public Collection<Npc> all() {
        return npcs.values();
    }

    public Optional<Npc> find(String id) {
        return Optional.ofNullable(id == null ? null : npcs.get(id));
    }

    public Npc add(Location at, Npc.Look look) {
        int number = npcs.size() + 1;
        while (npcs.containsKey("npc" + number)) number++;
        String id = "npc" + number;
        Npc npc = new Npc(id, "村人", at, look);
        npcs.put(id, npc);
        save();
        spawnIfLoaded(npc);
        return npc;
    }

    public void remove(Npc npc) {
        despawn(npc.id());
        npcs.remove(npc.id());
        save();
    }

    /** Gives an NPC another id (the quest lines that name the old one must be changed too). False when the new id is taken or not allowed. */
    public boolean rename(Npc npc, String newId) {
        // "client" is the quest lines' name for the NPC a line was taken from (依頼人): no NPC may have it
        if (!newId.matches("[a-z0-9_]{1,40}") || npcs.containsKey(newId) || newId.equals(net.tkgon.mc.iruuRPG.questline.QuestlineEngine.CLIENT)) return false;
        despawn(npc.id());
        npcs.remove(npc.id());
        Npc renamed = new Npc(newId, npc.name(), new Location(null, 0, 0, 0), npc.look());
        renamed.set(npc.worldName(), npc.x(), npc.y(), npc.z(), npc.yaw());
        renamed.idle().addAll(npc.idle());
        renamed.quests().addAll(npc.quests());
        npcs.put(newId, renamed);
        save();
        spawnIfLoaded(renamed);
        return true;
    }

    /** Saves and puts the NPC's entity up again (after its place, look or name changed). */
    public void changed(Npc npc) {
        save();
        despawn(npc.id());
        spawnIfLoaded(npc);
    }

    public LivingEntity entity(Npc npc) {
        LivingEntity entity = entities.get(npc.id());
        return entity != null && entity.isValid() ? entity : null;
    }

    public Npc byEntity(Entity entity) {
        if (!isNpc(entity)) return null;
        String id = entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id == null ? null : npcs.get(id);
    }

    // ---- the entities -------------------------------------------------------------------------------------------------

    private void spawnIfLoaded(Npc npc) {
        World world = Bukkit.getWorld(npc.worldName());
        if (world == null || !world.isChunkLoaded(npc.chunkX(), npc.chunkZ())) return;
        spawn(npc);
    }

    private void spawn(Npc npc) {
        despawn(npc.id());
        Location at = npc.location();
        if (at == null) return;
        LivingEntity entity;
        try {
            entity = create(npc, at);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("npc: " + npc.id() + " could not be made (" + npc.look().describe() + "): " + e.getMessage());
            return;
        }
        if (entity == null) return;
        entities.put(npc.id(), entity);
        if ("model".equals(npc.look().type()) && models != null && npc.look().model() != null) {
            Map<String, Object> section = new HashMap<>();
            section.put("id", npc.look().model());
            section.put("scale", npc.look().scale());
            section.put("hide-base", true);
            models.attach(entity, section);
        }
        double height = models != null && models.height(entity) > 0 ? models.height(entity) : entity.getHeight();
        Location over = at.clone().add(0, height + 0.75, 0);
        TextDisplay take = mark(over, "!", NamedTextColor.YELLOW);
        TextDisplay waiting = mark(over, "?", NamedTextColor.AQUA);
        TextDisplay ready = mark(over, "✔", YELLOW_GREEN);
        marks.put(npc.id(), new TextDisplay[]{take, waiting, ready});
    }

    private LivingEntity create(Npc npc, Location at) {
        World world = at.getWorld();
        Npc.Look look = npc.look();
        if ("entity".equals(look.type())) {
            EntityType type = EntityType.valueOf(String.valueOf(look.entity()).toUpperCase(Locale.ROOT));
            Class<? extends Entity> kind = type.getEntityClass();
            if (kind == null || !LivingEntity.class.isAssignableFrom(kind) || type == EntityType.PLAYER || type == EntityType.ARMOR_STAND) {
                throw new IllegalArgumentException("not a living entity: " + type);
            }
            @SuppressWarnings("unchecked")
            Class<? extends LivingEntity> living = (Class<? extends LivingEntity>) kind;
            return world.spawn(at, living, entity -> setUp(entity, npc));
        }
        return world.spawn(at, Villager.class, villager -> {
            if (!"model".equals(look.type())) {
                villager.setProfession(registered(Villager.Profession.class, look.profession(), Villager.Profession.NONE));
                villager.setVillagerType(registered(Villager.Type.class, look.villagerType(), Villager.Type.PLAINS));
                villager.setVillagerLevel(Math.max(1, villager.getVillagerLevel()));
            }
            setUp(villager, npc);
        });
    }

    private static <T extends Enum<T>> T registered(Class<T> kind, String name, T fallback) {
        try {
            return Enum.valueOf(kind, String.valueOf(name).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private void setUp(LivingEntity entity, Npc npc) {
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
        entity.setCollidable(false);
        entity.setGravity(false);
        entity.addScoreboardTag(TAG);
        entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, npc.id());
        entity.customName(Component.text(npc.name(), NamedTextColor.WHITE));
        entity.setCustomNameVisible(true);
        if (entity instanceof Ageable ageable) {
            if (npc.look().baby()) ageable.setBaby();
            else ageable.setAdult();
        }
        if (entity instanceof Zombie zombie) zombie.setShouldBurnInDay(false);
        if (entity instanceof AbstractSkeleton skeleton) skeleton.setShouldBurnInDay(false);
        entity.setRotation(npc.yaw(), 0f);
    }

    /** 黄緑 (yellow green): the "can be finished now" mark. */
    private static final net.kyori.adventure.text.format.TextColor YELLOW_GREEN = net.kyori.adventure.text.format.TextColor.color(0x9ACD32);

    private TextDisplay mark(Location at, String text, net.kyori.adventure.text.format.TextColor color) {
        return at.getWorld().spawn(at, TextDisplay.class, display -> {
            display.text(Component.text(text, color, TextDecoration.BOLD));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(true);
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.addScoreboardTag(TAG);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.2f, 2.2f, 2.2f), new AxisAngle4f()));
        });
    }

    private void despawn(String id) {
        LivingEntity entity = entities.remove(id);
        if (entity != null) {
            if (models != null) models.detach(entity);
            entity.remove();
        }
        TextDisplay[] pair = marks.remove(id);
        if (pair != null) {
            for (TextDisplay display : pair) {
                markViewers.remove(display.getUniqueId());
                display.remove();
            }
        }
    }

    /** Takes away NPC entities that are not ours (left over from before a restart). */
    private void sweep() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isNpc(entity) && !entities.containsValue(entity)) entity.remove();
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Npc npc : npcs.values()) {
            if (npc.worldName().equals(chunk.getWorld().getName()) && npc.chunkX() == chunk.getX() && npc.chunkZ() == chunk.getZ()
                    && entity(npc) == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (npcs.get(npc.id()) == npc && entity(npc) == null) spawnIfLoaded(npc);
                });
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Npc npc : npcs.values()) {
            if (npc.worldName().equals(chunk.getWorld().getName()) && npc.chunkX() == chunk.getX() && npc.chunkZ() == chunk.getZ()) {
                despawn(npc.id());
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isNpc(entity) && !entities.containsValue(entity)) entity.remove();
        }
    }

    // ---- clicks and harm ----------------------------------------------------------------------------------------------

    /** Registered before the weapon and skill listeners: a click on an NPC is cancelled first, so they skip it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Npc npc = byEntity(event.getRightClicked());
        if (npc == null) return;
        Player player = event.getPlayer();
        if (editorClick.test(player, npc)) return;
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < CLICK_GAP_MILLIS) return;
        try {
            questlines.interact(player, npc.id());
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "npc: talking to " + npc.id() + " failed", e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (isNpc(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombust(EntityCombustEvent event) {
        if (isNpc(event.getEntity())) event.setCancelled(true);
    }

    // ---- every quarter second: faces, and once a second the marks ------------------------------------------------------

    private void tick() {
        ticks++;
        for (Npc npc : npcs.values()) {
            LivingEntity entity = entity(npc);
            if (entity == null) continue;
            face(npc, entity);
            if (ticks % 4 == 0) updateMarks(npc, entity);
        }
    }

    /** Turns toward the nearest player close by, else back to its own way. */
    private void face(Npc npc, LivingEntity entity) {
        Player nearest = null;
        double best = FACE_RANGE * FACE_RANGE;
        for (Player player : entity.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest == null) {
            entity.setRotation(npc.yaw(), 0f);
            return;
        }
        Location eye = entity.getEyeLocation();
        org.bukkit.util.Vector to = nearest.getEyeLocation().toVector().subtract(eye.toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        float pitch = (float) Math.toDegrees(-Math.atan2(to.getY(), Math.hypot(to.getX(), to.getZ())));
        entity.setRotation(yaw, Math.max(-40f, Math.min(40f, pitch)));
    }

    /** The marks of the NPC, in the order of {@link #MARKS}: each player sees at most one, their own. */
    private static final List<String> MARKS = List.of("!", "?", "✔");

    private void updateMarks(Npc npc, LivingEntity entity) {
        TextDisplay[] displays = marks.get(npc.id());
        if (displays == null) return;
        Set<UUID> near = new HashSet<>();
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) > MARK_RANGE * MARK_RANGE) continue;
            near.add(player.getUniqueId());
            String mark;
            try {
                mark = questlines.marker(player, npc.id());
            } catch (RuntimeException e) {
                mark = null;
            }
            for (int index = 0; index < displays.length; index++) {
                Set<UUID> viewers = markViewers.computeIfAbsent(displays[index].getUniqueId(), id -> new HashSet<>());
                show(player, displays[index], viewers, MARKS.get(index).equals(mark));
            }
        }
        for (TextDisplay display : displays) {
            Set<UUID> viewers = markViewers.computeIfAbsent(display.getUniqueId(), id -> new HashSet<>());
            for (UUID id : new ArrayList<>(viewers)) {
                if (near.contains(id)) continue;
                Player gone = Bukkit.getPlayer(id);
                if (gone != null) gone.hideEntity(plugin, display);
                viewers.remove(id);
            }
        }
    }

    // ---- speech over the head (one player; the tutorial's look) ---------------------------------------------------------

    private final Map<UUID, Map<String, TextDisplay>> speech = new HashMap<>();
    private final Map<UUID, Long> speechUntil = new HashMap<>();

    /** Shows the words over the NPC's head to this player for {@code ticks}; the next line replaces it. */
    private void speak(Player player, String npcId, String text, long ticks) {
        Npc npc = npcs.get(npcId);
        LivingEntity entity = npc == null ? null : entity(npc);
        if (entity == null || !entity.getWorld().equals(player.getWorld())) return;
        double height = models != null && models.height(entity) > 0 ? models.height(entity) : entity.getHeight();
        Location over = entity.getLocation().add(0, height + 1.5, 0);
        Map<String, TextDisplay> mine = speech.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        TextDisplay display = mine.get(npcId);
        Component words = Component.text(text, NamedTextColor.WHITE);
        if (display == null || !display.isValid()) {
            display = over.getWorld().spawn(over, TextDisplay.class, spawned -> {
                spawned.setVisibleByDefault(false);
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setLineWidth(220);
                spawned.setBackgroundColor(org.bukkit.Color.fromARGB(205, 0, 0, 0));
                spawned.setShadowed(true);
                spawned.setSeeThrough(false);
                spawned.setPersistent(false);
                spawned.addScoreboardTag(TAG);
                spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.35f, 1.35f, 1.35f), new AxisAngle4f()));
                spawned.text(words);
            });
            player.showEntity(plugin, display);
            mine.put(npcId, display);
        } else {
            display.text(words);
            display.teleport(over);
        }
        long until = System.currentTimeMillis() + ticks * 50L + 600L;
        speechUntil.put(display.getUniqueId(), until);
        TextDisplay shown = display;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long end = speechUntil.get(shown.getUniqueId());
            if (end == null || System.currentTimeMillis() < end - 50L) return; // a newer line keeps it
            speechUntil.remove(shown.getUniqueId());
            shown.remove();
            Map<String, TextDisplay> left = speech.get(player.getUniqueId());
            if (left != null) left.values().remove(shown);
        }, ticks + 12L);
    }

    private void show(Player player, TextDisplay display, Set<UUID> viewers, boolean visible) {
        if (visible && viewers.add(player.getUniqueId())) {
            player.showEntity(plugin, display);
        } else if (!visible && viewers.remove(player.getUniqueId())) {
            player.hideEntity(plugin, display);
        }
    }
}
