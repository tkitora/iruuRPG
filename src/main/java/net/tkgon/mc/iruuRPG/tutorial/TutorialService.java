package net.tkgon.mc.iruuRPG.tutorial;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.combat.TutorialDamageHook;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.gui.ClassSelectMenu;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Debug tutorial. Runs the script in tutorial/debug.yml for one player, using coordinates relative to where
 * /tutorialstart was run. Lines in 「」 are the farmer NPC speaking (above his head and in chat); the other lines
 * narrate the scene and trigger what happens in it. Everything it creates is tagged and removed on stop/finish.
 */
public final class TutorialService implements TutorialDamageHook {

    private static final String NPC_NAME = "農夫";
    private static final int MAX_STEPS_PER_ADVANCE = 200;

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final EquipmentService equipmentService;
    private final ClassService classService;
    private final ClassSelectMenu classSelectMenu;
    private final RpgItemRegistry itemRegistry;
    private final RpgItemFactory itemFactory;
    private final PlayerBars playerBars;
    private final NamespacedKey markKey;
    private final Map<UUID, TutorialSession> sessions = new HashMap<>();
    private TutorialStep.Script script;

    public TutorialService(
            JavaPlugin plugin,
            PlayerProfileManager profileManager,
            EquipmentService equipmentService,
            ClassService classService,
            ClassSelectMenu classSelectMenu,
            RpgItemRegistry itemRegistry,
            RpgItemFactory itemFactory,
            PlayerBars playerBars
    ) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.equipmentService = equipmentService;
        this.classService = classService;
        this.classSelectMenu = classSelectMenu;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
        this.playerBars = playerBars;
        this.markKey = new NamespacedKey(plugin, "tutorial_entity");
    }

    // ---- public API ---------------------------------------------------------------------------------

    public boolean isRunning(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void start(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            stop(player, "チュートリアルを最初からやり直します。");
        }

        script = TutorialStep.load(plugin);
        if (script.steps().isEmpty()) {
            player.sendMessage("[iruuRPG] チュートリアルの台本が空です (tutorial/debug.yml)。");
            return;
        }

        PlayerProfile profile = profileManager.getOrCreate(player);
        TutorialSession.Snapshot snapshot = new TutorialSession.Snapshot(
                player.getLocation().clone(),
                player.getGameMode(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getInventory().getHeldItemSlot(),
                player.getInventory().getItem(0) == null ? null : player.getInventory().getItem(0).clone(),
                profile.classLevels(),
                profile.classId(),
                profile.classChosen()
        );
        TutorialSession session = new TutorialSession(player.getUniqueId(), player.getLocation(), snapshot, script.steps());
        sessions.put(player.getUniqueId(), session);

        player.sendMessage(Component.text("[iruuRPG] チュートリアルを開始します。(中断: /tutorialstop)", NamedTextColor.GREEN));
        session.tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(session), 1L, 1L));
        schedule(session, 20L, () -> advance(session));
    }

    /** Aborts the tutorial: removes everything it made and puts the player back (class counts as not chosen). */
    public void stop(Player player, String message) {
        TutorialSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            if (message != null) player.sendMessage("[iruuRPG] チュートリアルは実行中ではありません。");
            return;
        }

        cleanup(session, player);
        restore(session, player, true);
        if (message != null) {
            player.sendMessage(Component.text("[iruuRPG] " + message, NamedTextColor.YELLOW));
        }
    }

    /** Removes every tutorial entity still around (server start/stop, or after a crash). */
    public void sweepEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(markKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            TutorialSession session = sessions.get(id);
            if (session == null) continue;
            cleanup(session, player);
            if (player != null) restore(session, player, true);
        }
        sweepEntities();
    }

    // ---- state queried by the listener / damage hook -------------------------------------------------------

    TutorialSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    TutorialSession sessionOfNpc(Entity entity) {
        for (TutorialSession session : sessions.values()) {
            if (session.npc != null && session.npc.getUniqueId().equals(entity.getUniqueId())) return session;
        }
        return null;
    }

    boolean isTutorialEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(markKey, PersistentDataType.STRING);
    }

    @Override
    public boolean isActive(Player player) {
        return player != null && sessions.containsKey(player.getUniqueId());
    }

    @Override
    public boolean isAttackLocked(Player player) {
        TutorialSession session = player == null ? null : sessions.get(player.getUniqueId());
        return session != null && session.attackLocked;
    }

    @Override
    public boolean isNpc(LivingEntity entity) {
        return sessionOfNpc(entity) != null;
    }

    @Override
    public void onNpcHit(Player attacker) {
        TutorialSession session = sessions.get(attacker.getUniqueId());
        if (session != null) {
            onNpcHit(session);
        }
    }

    @Override
    public boolean isRat(LivingEntity entity) {
        for (TutorialSession session : sessions.values()) {
            if (session.rat != null && session.rat.getUniqueId().equals(entity.getUniqueId())) return true;
        }
        return false;
    }

    @Override
    public void onRatHit(Player attacker, LivingEntity rat) {
        TutorialSession session = sessions.get(attacker.getUniqueId());
        if (session == null || session.rat == null || session.stopped) return;

        session.ratHits++;
        session.lastRatHitMillis = System.currentTimeMillis();
        rat.customName(ratName(session.ratHits));
        rat.getWorld().spawnParticle(Particle.CRIT, rat.getLocation().add(0, 0.2, 0), 8, 0.2, 0.1, 0.2, 0.1);
        if (session.ratHits < 3 || !session.ratWaiting) return;

        session.ratWaiting = false;
        rat.setHealth(0.0);
        session.rat = null;
        resume(session, 30L);
    }

    /** "ねずみ" with a three-segment health bar (one segment per hit it can still take). */
    private static Component ratName(int hits) {
        int left = Math.max(0, 3 - hits);
        return Component.text("ねずみ ", NamedTextColor.WHITE)
                .append(Component.text("■".repeat(left), NamedTextColor.RED))
                .append(Component.text("■".repeat(3 - left), NamedTextColor.DARK_GRAY));
    }

    /** The player hit the NPC: he comments on it, once. */
    void onNpcHit(TutorialSession session) {
        if (session.hitNpcReplied || session.stopped) return;

        session.hitNpcReplied = true;
        String line = script == null ? "船酔いしたか？足ふらついてんぞ" : script.yaml().getString("extra.hit-npc", "船酔いしたか？足ふらついてんぞ");
        speak(session, line);
    }

    /** The class menu was closed without a choice: open it again. */
    void onClassMenuClosed(Player player) {
        TutorialSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.waitingClassMenu) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (session.waitingClassMenu && !session.stopped && player.isOnline()) {
                openClassMenu(session, player);
            }
        });
    }

    // ---- script engine ----------------------------------------------------------------------------------

    private void advance(TutorialSession session) {
        for (int guard = 0; guard < MAX_STEPS_PER_ADVANCE; guard++) {
            if (session.stopped) return;
            Player player = Bukkit.getPlayer(session.playerId);
            if (player == null) {
                return;
            }
            if (session.index >= session.steps.size()) {
                finish(session, player);
                return;
            }

            TutorialStep step = session.steps.get(session.index++);
            long delay = 0L;
            if (step.hasSay()) {
                speak(session, step.say());
                delay = step.ticks() >= 0 ? step.ticks() : readTicks(step.say());
            }
            if (step.hasNarrate()) {
                // The narration text is only a note for the script writer: it is never shown. It sets the pace.
                delay = Math.max(delay, step.ticks() >= 0 ? step.ticks() : 30L);
            }
            boolean blocking = step.action() != null && perform(session, player, step);
            if (blocking) return;
            if (step.action() != null && "finish".equals(step.action())) return;

            if (delay > 0L) {
                schedule(session, delay, () -> advance(session));
                return;
            }
        }
    }

    private void resume(TutorialSession session, long delayTicks) {
        schedule(session, delayTicks, () -> advance(session));
    }

    private void schedule(TutorialSession session, long delay, Runnable runnable) {
        if (session.stopped) return;
        session.tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!session.stopped) runnable.run();
        }, Math.max(1L, delay)));
    }

    /** 1.2 s + 0.15 s per character, between 2 s and 8 s. */
    static long readTicks(String text) {
        double seconds = 1.2 + 0.15 * text.length();
        return Math.max(40L, Math.min(160L, Math.round(seconds * 20.0)));
    }

    /** Runs the action; returns true when the script must wait (the action resumes it later). */
    private boolean perform(TutorialSession session, Player player, TutorialStep step) {
        switch (step.action()) {
            case "begin" -> begin(session, player);
            case "look_up" -> lookUp(session);
            case "head_shake" -> headShake(session);
            case "mist" -> setMist(session, player, step.args().get("colors"));
            case "stand_up" -> animate(session, 14, progress -> session.yDrop = 0.6f * (1.0f - progress), null);
            case "open_class_menu" -> {
                leaveSpectator(session, player);
                session.waitingClassMenu = true;
                openClassMenu(session, player);
                return true;
            }
            case "give_weapon" -> giveWeaponAndUnlock(session, player);
            case "npc_side_and_rat" -> npcSideAndRat(session, player);
            case "wait_rat" -> {
                session.ratWaiting = true;
                session.ratWaitStartMillis = System.currentTimeMillis();
                session.lastRatHitMillis = 0L;
                return true;
            }
            case "finish" -> {
                // the delay of the narrate line is used as the blackout time
                finish(session, player);
                return true;
            }
            default -> plugin.getLogger().warning("tutorial: unknown action '" + step.action() + "'");
        }
        return false;
    }

    // ---- actions ------------------------------------------------------------------------------------------

    private void begin(TutorialSession session, Player player) {
        session.lockYaw = session.originYaw;
        session.lockPitch = 80.0f;
        session.lookLocked = true;
        session.moveLocked = true;
        session.attackLocked = true;
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        // Spectator until the class menu (so the player cannot touch anything), and sitting a little low.
        session.yDrop = 0.6f;
        player.setGameMode(GameMode.SPECTATOR);
        Location low = session.origin.clone().add(0.0, -session.yDrop, 0.0);
        low.setYaw(session.lockYaw);
        low.setPitch(session.lockPitch);
        player.teleport(low);

        double distance = plugin.getConfig().getDouble("tutorial.npc-distance", 2.5);
        Location npcLocation = session.origin.clone().add(forward(session.originYaw).multiply(distance));
        npcLocation.setYaw(session.originYaw + 180.0f);
        npcLocation.setPitch(0.0f);
        Villager npc = player.getWorld().spawn(npcLocation, Villager.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistent(false);
            entity.setCollidable(false);
            entity.setProfession(Villager.Profession.FARMER);
            entity.setVillagerType(Villager.Type.PLAINS);
            entity.customName(Component.text(NPC_NAME));
            entity.setCustomNameVisible(false);
            mark(entity, session);
        });
        player.showEntity(plugin, npc);
        session.npc = npc;
    }

    private void lookUp(TutorialSession session) {
        animate(session, 20, step -> session.lockPitch = 80.0f * (1.0f - step), null);
    }

    private void headShake(TutorialSession session) {
        animate(session, 24, step -> session.lockYaw = session.originYaw + (float) (25.0 * Math.sin(2.0 * Math.PI * step)), () -> {
            session.lockYaw = session.originYaw;
            session.lookLocked = false;
        });
    }

    /** Runs an animation over {@code ticks} ticks; the callback gets progress 0..1. */
    private void animate(TutorialSession session, int ticks, java.util.function.Consumer<Float> frame, Runnable done) {
        BukkitRunnable runnable = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (session.stopped) {
                    cancel();
                    return;
                }
                tick++;
                frame.accept(Math.min(1.0f, tick / (float) ticks));
                if (tick >= ticks) {
                    if (done != null) done.run();
                    cancel();
                }
            }
        };
        session.tasks.add(runnable.runTaskTimer(plugin, 1L, 1L));
    }

    private static final double RAINBOW_RADIUS = 1.5;
    private static final double RAINBOW_CENTER_HEIGHT = 1.0;
    private static final int RAINBOW_SLOTS = 5;
    private static final int RAINBOW_TRAVEL_TICKS = 14;

    /**
     * A rainbow is always a half circle, so the colors are not drawn as bands: each color is a small dot placed at
     * an evenly spaced slot on one half circle (in the plane the player faces: his right and up). A dot is born right
     * beside the NPC on the player's left and travels along the half circle to its slot, then stays.
     * "off" pops everything at once.
     */
    private void setMist(TutorialSession session, Player player, Object colors) {
        if (!(colors instanceof List<?> list)) {
            popMist(session, player);
            return;
        }
        for (Object entry : list) {
            Color color = mistColor(String.valueOf(entry));
            if (color == null) continue;
            if (session.mistArcs.stream().anyMatch(arc -> arc.color().equals(color))) continue;
            if (session.mistArcs.size() >= RAINBOW_SLOTS) continue;

            double slot = 180.0 - 180.0 / (RAINBOW_SLOTS - 1) * session.mistArcs.size();
            session.mistArcs.add(new TutorialSession.MistArc(color, slot, session.mistClock));
            if (session.npc != null) {
                player.playSound(rainbowPoint(session, 180.0), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.8f + 0.2f * session.mistArcs.size());
            }
        }
    }

    /** A point of the half circle, {@code deg} degrees around the NPC's chest (0 = player's right, 180 = left). */
    private Location rainbowPoint(TutorialSession session, double deg) {
        double radians = Math.toRadians(deg);
        Vector right = right(session.originYaw);
        return session.npc.getLocation().add(
                right.getX() * RAINBOW_RADIUS * Math.cos(radians),
                RAINBOW_CENTER_HEIGHT + RAINBOW_RADIUS * Math.sin(radians),
                right.getZ() * RAINBOW_RADIUS * Math.cos(radians));
    }

    private void emitRainbow(TutorialSession session, Player player) {
        session.mistClock++;
        for (TutorialSession.MistArc dot : session.mistArcs) {
            int age = session.mistClock - dot.createdTick();
            Particle.DustOptions dust = new Particle.DustOptions(dot.color(), 1.5f);
            if (age < RAINBOW_TRAVEL_TICKS) {
                // travelling along the half circle from the NPC's side to the slot
                double deg = 180.0 + (dot.slotDeg() - 180.0) * age / RAINBOW_TRAVEL_TICKS;
                player.spawnParticle(Particle.REDSTONE, rainbowPoint(session, deg), 3, 0.05, 0.05, 0.05, 0.0, dust);
            } else if (session.mistClock % 3 == 0) {
                player.spawnParticle(Particle.REDSTONE, rainbowPoint(session, dot.slotDeg()), 4, 0.06, 0.06, 0.06, 0.0, dust);
            }
        }
    }

    private void popMist(TutorialSession session, Player player) {
        if (session.npc != null) {
            for (TutorialSession.MistArc dot : session.mistArcs) {
                Location at = rainbowPoint(session, dot.slotDeg());
                player.spawnParticle(Particle.REDSTONE, at, 14, 0.15, 0.15, 0.15, 0.0, new Particle.DustOptions(dot.color(), 1.3f));
                player.spawnParticle(Particle.CLOUD, at, 4, 0.1, 0.1, 0.1, 0.02);
            }
            if (!session.mistArcs.isEmpty()) {
                player.playSound(session.npc.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.5f, 1.6f);
            }
        }
        session.mistArcs.clear();
    }

    private static Color mistColor(String name) {
        return switch (name.toLowerCase()) {
            case "red" -> Color.fromRGB(225, 45, 45);
            case "blue" -> Color.fromRGB(55, 100, 235);
            case "white" -> Color.fromRGB(245, 245, 245);
            case "green" -> Color.fromRGB(65, 205, 95);
            case "orange" -> Color.fromRGB(255, 150, 30);
            default -> null;
        };
    }

    /** The class menu needs a normal game mode (spectators cannot click menus). Movement and attacks stay locked. */
    private void leaveSpectator(TutorialSession session, Player player) {
        session.yDrop = 0.0f;
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(session.snapshot.gameMode());
            Location back = session.origin.clone();
            back.setYaw(player.getLocation().getYaw());
            back.setPitch(player.getLocation().getPitch());
            player.teleport(back);
        }
    }

    private void openClassMenu(TutorialSession session, Player player) {
        Map<String, String> speech = new HashMap<>();
        var section = script.yaml().getConfigurationSection("extra.class-speech");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                speech.put(key, "「" + section.getString(key, "") + "」");
            }
        }
        classSelectMenu.open(player, speech, classId -> onClassChosen(session, classId));
    }

    private void onClassChosen(TutorialSession session, String classId) {
        if (session.stopped) return;
        session.chosenClass = classId;
        session.waitingClassMenu = false;
        resume(session, 10L);
    }

    private void giveWeaponAndUnlock(TutorialSession session, Player player) {
        String weaponId = script.yaml().getString("extra.class-weapon." + session.chosenClass, "starter_sword");
        var definition = itemRegistry.find(weaponId).orElse(null);
        if (definition == null) {
            player.sendMessage("[iruuRPG] 最初の武器が見つかりません: " + weaponId + " (items/starter.yml)");
        } else {
            ItemStack weapon = itemFactory.create(definition);
            player.getInventory().setItem(0, weapon);
            player.getInventory().setHeldItemSlot(0);
        }

        session.moveLocked = false;
        session.attackLocked = false;
        player.setWalkSpeed(session.snapshot.walkSpeed());
        player.setFlySpeed(session.snapshot.flySpeed());
        equipmentService.recalculate(player);
    }

    private void npcSideAndRat(TutorialSession session, Player player) {
        if (session.npc == null) return;

        Location from = session.npc.getLocation().clone();
        double side = plugin.getConfig().getDouble("tutorial.npc-side-offset", 2.0);
        session.npcFrom = from;
        session.npcTo = from.clone().add(right(session.originYaw).multiply(side));
        session.moveTick = 0;
        session.moveTotal = 30;

        Location ratLocation = from.clone();
        Silverfish rat = player.getWorld().spawn(ratLocation, Silverfish.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistent(false);
            entity.setCollidable(false);
            entity.customName(ratName(0));
            entity.setCustomNameVisible(true);
            mark(entity, session);
        });
        player.showEntity(plugin, rat);
        face(rat, player);
        session.rat = rat;
        session.ratHits = 0;
        player.getWorld().spawnParticle(Particle.CLOUD, ratLocation.clone().add(0, 0.1, 0), 10, 0.2, 0.05, 0.2, 0.02);
    }

    private void finish(TutorialSession session, Player player) {
        if (session.stopped) return;

        session.attackLocked = true;
        session.moveLocked = true;
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, false));
        schedule(session, 25L, () -> {
            cleanup(session, player);
            restore(session, player, false);
            teleportToEnd(player);
            player.sendMessage(Component.text("[iruuRPG] チュートリアルが終わりました。", NamedTextColor.GREEN));
        });
    }

    private void teleportToEnd(Player player) {
        var config = plugin.getConfig();
        String worldName = config.getString("tutorial.end-location.world", "");
        World world = worldName == null || worldName.isBlank() ? player.getWorld() : Bukkit.getWorld(worldName);
        if (world == null) world = player.getWorld();
        Location end = new Location(
                world,
                config.getDouble("tutorial.end-location.x", 60.5),
                config.getDouble("tutorial.end-location.y", 80.0),
                config.getDouble("tutorial.end-location.z", 170.5),
                (float) config.getDouble("tutorial.end-location.yaw", 0.0),
                (float) config.getDouble("tutorial.end-location.pitch", 0.0)
        );
        player.teleport(end);
    }

    // ---- cleanup / restore --------------------------------------------------------------------------------

    /** Removes everything the tutorial created and stops its tasks. */
    private void cleanup(TutorialSession session, Player player) {
        session.stopped = true;
        session.waitingClassMenu = false;
        for (var task : session.tasks) {
            task.cancel();
        }
        session.tasks.clear();
        removeEntity(session.npc);
        removeEntity(session.rat);
        removeEntity(session.speech);
        session.npc = null;
        session.rat = null;
        session.speech = null;
        sessions.remove(session.playerId);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        // anything tagged with this session that slipped through
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String tag = entity.getPersistentDataContainer().get(markKey, PersistentDataType.STRING);
                if (session.id.equals(tag)) entity.remove();
            }
        }
    }

    /**
     * Puts the player's state back. On an abort the weapon is taken back and the class counts as not chosen;
     * after a normal finish the weapon and class stay.
     */
    private void restore(TutorialSession session, Player player, boolean aborted) {
        player.setWalkSpeed(session.snapshot.walkSpeed());
        player.setFlySpeed(session.snapshot.flySpeed());
        player.setGameMode(session.snapshot.gameMode());

        PlayerProfile profile = profileManager.getOrCreate(player);
        if (aborted) {
            player.getInventory().setItem(0, session.snapshot.slotItem());
            player.getInventory().setHeldItemSlot(session.snapshot.heldSlot());
            player.teleport(session.snapshot.location());

            profile.classLevels().clear();
            profile.setClassId("");
            profile.setClassChosen(false);
        } else if (session.snapshot.slotItem() != null) {
            // the starter weapon took the first hotbar slot: give the old item back
            var leftover = player.getInventory().addItem(session.snapshot.slotItem());
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        PlayerProfile refreshed = equipmentService.recalculate(player);
        playerBars.sync(player, refreshed);
        profileManager.save(player);
    }

    // ---- per-tick work ---------------------------------------------------------------------------------------

    private void tick(TutorialSession session) {
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (session.lookLocked) {
            player.setRotation(session.lockYaw, session.lockPitch);
        }
        Location hold = session.origin.clone().add(0.0, -session.yDrop, 0.0);
        if (session.moveLocked && player.getLocation().distanceSquared(hold) > 0.01) {
            Location back = hold.clone();
            back.setYaw(player.getLocation().getYaw());
            back.setPitch(player.getLocation().getPitch());
            player.teleport(back);
            player.setVelocity(new Vector(0, 0, 0));
        }

        if (session.npc != null && session.npc.isValid()) {
            tickNpc(session, player);
        }
        if (session.rat != null && session.rat.isValid()) {
            face(session.rat, player);
        }

        long now = System.currentTimeMillis();
        if (session.speech != null && now > session.speechUntilMillis) {
            removeEntity(session.speech);
            session.speech = null;
        }

        if (session.ratWaiting && !session.nudged && session.lastRatHitMillis == 0L) {
            long waited = now - session.ratWaitStartMillis;
            long limit = plugin.getConfig().getLong("tutorial.idle-nudge-seconds", 20L) * 1000L;
            if (waited >= limit) {
                session.nudged = true;
                speak(session, script.yaml().getString("extra.idle-nudge", "どうした、遠慮するな"));
            }
        }
    }

    private void tickNpc(TutorialSession session, Player player) {
        Villager npc = session.npc;
        if (session.npcFrom != null && session.moveTick < session.moveTotal) {
            session.moveTick++;
            double t = session.moveTick / (double) session.moveTotal;
            t = t * t * (3.0 - 2.0 * t); // ease in/out
            Location next = session.npcFrom.clone().add(session.npcTo.clone().subtract(session.npcFrom).toVector().multiply(t));
            npc.teleport(next);
        }
        face(npc, player);

        if (session.speech != null) {
            session.speech.teleport(npc.getLocation().add(0, 2.6, 0));
        }

        if (!session.mistArcs.isEmpty()) {
            emitRainbow(session, player);
        }
    }

    // ---- messages ------------------------------------------------------------------------------------------

    /** The NPC speaks: above his head (only the player sees it) and in chat. */
    private void speak(TutorialSession session, String text) {
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null || session.stopped) return;

        String line = "「" + text + "」";
        player.sendMessage(Component.text(NPC_NAME + " ", NamedTextColor.GOLD).append(Component.text(line, NamedTextColor.WHITE)));
        if (session.npc == null) return;

        long ticks = readTicks(text);
        session.speechUntilMillis = System.currentTimeMillis() + ticks * 50L;
        Component component = Component.text(line, NamedTextColor.WHITE);
        if (session.speech == null || !session.speech.isValid()) {
            Location location = session.npc.getLocation().add(0, 2.6, 0);
            TextDisplay display = player.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.setVisibleByDefault(false);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setLineWidth(220);
                entity.setBackgroundColor(Color.fromARGB(205, 0, 0, 0));
                entity.setShadowed(true);
                entity.setSeeThrough(false);
                entity.setPersistent(false);
                entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.35f, 1.35f, 1.35f), new AxisAngle4f()));
                entity.text(component);
                mark(entity, session);
            });
            player.showEntity(plugin, display);
            session.speech = display;
        } else {
            session.speech.text(component);
        }
        player.playSound(session.npc.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.4f, 1.0f);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private void mark(Entity entity, TutorialSession session) {
        entity.getPersistentDataContainer().set(markKey, PersistentDataType.STRING, session.id);
    }

    private void removeEntity(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private void face(Entity entity, Player player) {
        Vector direction = player.getEyeLocation().toVector().subtract(entity.getLocation().toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        entity.setRotation(yaw, 0.0f);
    }

    /** Forward in the executor's frame (the direction he faced when starting). */
    static Vector forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    static Vector right(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.cos(radians), 0.0, -Math.sin(radians));
    }
}
