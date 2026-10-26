package net.tkgon.mc.iruuRPG.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.tkgon.mc.iruuRPG.combat.StatusEffectService;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.skill.SkillSpec;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RpgMobService {

    private static final long TARGET_LOCK_MILLIS = 15_000L;

    private final JavaPlugin plugin;
    private final RpgMobRegistry mobRegistry;
    private final RpgItemRegistry itemRegistry;
    private final RpgItemFactory itemFactory;
    private final LevelService levelService;
    private final StatusEffectService statusEffectService;
    private final NamespacedKey mobIdKey;
    private final NamespacedKey mobHpKey;
    private final NamespacedKey dropOwnerKey;
    private final NamespacedKey statusDisplayKey;
    private final Map<UUID, MobRuntime> runtimes = new HashMap<>();
    private final Map<UUID, UUID> statusDisplays = new HashMap<>();
    private final Map<UUID, TargetLock> targetLocks = new HashMap<>();
    private java.util.function.BiConsumer<Player, String> killHook;

    public RpgMobService(
            JavaPlugin plugin,
            RpgMobRegistry mobRegistry,
            RpgItemRegistry itemRegistry,
            RpgItemFactory itemFactory,
            LevelService levelService,
            StatusEffectService statusEffectService
    ) {
        this.plugin = plugin;
        this.mobRegistry = mobRegistry;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
        this.levelService = levelService;
        this.statusEffectService = statusEffectService;
        this.mobIdKey = new NamespacedKey(plugin, "rpg_mob_id");
        this.mobHpKey = new NamespacedKey(plugin, "rpg_mob_hp");
        this.dropOwnerKey = new NamespacedKey(plugin, "rpg_drop_owner");
        this.statusDisplayKey = new NamespacedKey(plugin, "rpg_status_display");
    }

    public Optional<LivingEntity> spawn(Player player, String id) {
        RpgMobDefinition definition = mobRegistry.find(id).orElse(null);
        if (definition == null) return Optional.empty();

        return Optional.of(spawnEntity(spawnLocation(player), definition));
    }

    /** Spawns an RPG mob at an exact location (dungeons place their mobs this way). */
    public Optional<LivingEntity> spawnAt(Location location, String id) {
        RpgMobDefinition definition = mobRegistry.find(id).orElse(null);
        if (definition == null || location.getWorld() == null) return Optional.empty();

        return Optional.of(spawnEntity(location, definition));
    }

    private LivingEntity spawnEntity(Location location, RpgMobDefinition definition) {
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, definition.entityType());
        configure(entity, definition);

        applyEquipment(entity, definition);
        PlayerProfile profile = buildProfile(entity, definition);
        applyHealth(entity, profile);
        storeCurrentHp(entity, profile.currentHp());
        runtimes.put(entity.getUniqueId(), new MobRuntime(definition, profile, new HashMap<>()));
        startBehavior(entity, definition);
        // a mob with a `model:` section is dressed in its model (the model feature, if it is there)
        if (models != null && !definition.model().isEmpty()) models.attach(entity, definition.model());
        updateName(entity);
        selectRandomTarget(entity, definition, null, System.currentTimeMillis());
        return entity;
    }

    public void startTargetTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickTargets, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRanged, 5L, 5L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickChase, 10L, CHASE_PERIOD_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickSkills, 15L, 5L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBehaviors, 12L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBehaviorView, 14L, 2L);
        long statusPeriod = Math.max(1L, plugin.getConfig().getLong("mob.status-display.update-period-ticks", 1L));
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickStatusDisplays, 1L, statusPeriod);
    }

    public void retargetToNearestPlayer(LivingEntity entity) {
        MobRuntime runtime = runtime(entity);
        if (runtime == null) return;

        TargetLock lock = targetLocks.get(entity.getUniqueId());
        selectRandomTarget(entity, runtime.definition(), lock == null ? null : lock.playerId(), System.currentTimeMillis());
    }

    public Optional<PlayerProfile> profile(LivingEntity entity) {
        MobRuntime runtime = runtime(entity);
        return runtime == null ? Optional.empty() : Optional.of(runtime.profile());
    }

    public Optional<RpgItemDefinition> attackWeapon(LivingEntity entity) {
        MobRuntime runtime = runtime(entity);
        if (runtime == null) return Optional.empty();

        MobEquipmentItem hand = runtime.definition().equipment().get(EquipmentSlot.HAND);
        if (hand == null || !hand.hasItem()) return Optional.empty();

        return itemRegistry.find(hand.itemId());
    }

    public boolean applyDamage(LivingEntity entity, double damage) {
        return applyDamage(entity, damage, null);
    }

    public boolean applyDamage(LivingEntity entity, double damage, Player attacker) {
        MobRuntime runtime = runtime(entity);
        if (runtime == null) return false;
        if (damage <= 0.0) return true;

        PlayerProfile profile = runtime.profile();
        double beforeHp = profile.currentHp();
        profile.setCurrentHp(profile.currentHp() - damage);
        recordDamage(runtime, attacker, beforeHp - profile.currentHp());
        storeCurrentHp(entity, profile.currentHp());
        if (profile.currentHp() <= 0.0 && !entity.isDead()) {
            entity.setHealth(0.0);
            return true;
        }

        applyHealth(entity, profile);
        updateName(entity);
        // (the damage of the game's mobs goes round the vanilla damage event, so the model is told here)
        if (models != null) models.hurt(entity);
        return true;
    }

    /** Called once per player who gets credit for a kill (dealt at least 5% of the mob's max HP), with the mob's id. */
    public void setKillHook(java.util.function.BiConsumer<Player, String> killHook) {
        this.killHook = killHook;
    }

    public void creditKill(LivingEntity entity) {
        MobRuntime runtime = runtime(entity);
        if (runtime == null || killHook == null) return;

        double minimumDamage = Math.max(0.0, runtime.profile().maxHp() * 0.05);
        for (Map.Entry<UUID, Double> entry : runtime.damageByPlayer().entrySet()) {
            if (entry.getValue() + 1.0E-9 < minimumDamage) continue;

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                killHook.accept(player, runtime.definition().id());
            }
        }
    }

    private net.tkgon.mc.iruuRPG.party.PartyService partyService;

    /** Party members near a player who earned a kill's XP get the same XP. */
    public void setPartyService(net.tkgon.mc.iruuRPG.party.PartyService partyService) {
        this.partyService = partyService;
    }

    public void dropRewards(LivingEntity entity) {
        MobRuntime runtime = runtime(entity);
        if (runtime == null) return;
        if (runtime.definition().drops().isEmpty() && runtime.definition().experience() <= 0L) return;

        double minimumDamage = Math.max(0.0, runtime.profile().maxHp() * 0.05);
        if (minimumDamage <= 0.0) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Map.Entry<UUID, Double> entry : runtime.damageByPlayer().entrySet()) {
            if (entry.getValue() + 1.0E-9 < minimumDamage) continue;

            for (MobDropItem drop : runtime.definition().drops()) {
                if (!drop.rolls(random)) continue;

                dropReward(entity, entry.getKey(), drop, random);
            }
        }
        giveExperience(entity, runtime, minimumDamage);
    }

    /**
     * Kill XP. The players who did at least 5% of the mob's HP in damage earned it: in a party they split it evenly
     * among the party's earners; a player not in a party gets all of it. Party members who did not earn it, nearby
     * (online, same world, within party.xp-share-range of the mob), get party.xp-bystander-percent (5%) of it.
     * Earners get it as their own XP orbs; bystanders directly. Items are not shared.
     */
    private void giveExperience(LivingEntity entity, MobRuntime runtime, double minimumDamage) {
        long experience = runtime.definition().experience();
        if (experience <= 0L) return;
        List<UUID> earners = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : runtime.damageByPlayer().entrySet()) {
            if (entry.getValue() + 1.0E-9 >= minimumDamage) earners.add(entry.getKey());
        }
        // group the earners by party (someone without a party is a group of one)
        Map<Object, List<UUID>> groups = new LinkedHashMap<>();
        for (UUID earner : earners) {
            Object key = partyService == null ? earner : partyService.partyOf(earner);
            groups.computeIfAbsent(key == null ? earner : key, k -> new ArrayList<>()).add(earner);
        }
        java.util.Set<UUID> earned = new java.util.HashSet<>(earners);
        java.util.Set<UUID> bystanders = new java.util.HashSet<>();
        double range = plugin.getConfig().getDouble("party.xp-share-range", 40.0);
        for (Map.Entry<Object, List<UUID>> group : groups.entrySet()) {
            long share = Math.max(1L, Math.round((double) experience / group.getValue().size()));
            for (UUID earner : group.getValue()) levelService.dropExperience(entity.getLocation(), earner, share);
            if (group.getKey() instanceof net.tkgon.mc.iruuRPG.party.PartyService.Party party) {
                for (org.bukkit.entity.Player member : party.online()) {
                    if (earned.contains(member.getUniqueId()) || !member.getWorld().equals(entity.getWorld())) continue;
                    if (member.getLocation().distanceSquared(entity.getLocation()) > range * range) continue;
                    bystanders.add(member.getUniqueId());
                }
            }
        }
        double percent = plugin.getConfig().getDouble("party.xp-bystander-percent", 5.0);
        long bystanderXp = Math.max(1L, Math.round(experience * percent / 100.0));
        for (UUID id : bystanders) {
            org.bukkit.entity.Player member = org.bukkit.Bukkit.getPlayer(id);
            if (member != null) levelService.addXp(member, bystanderXp);
        }
    }

    public boolean isRewardForOtherPlayer(Item item, Player player) {
        String owner = item.getPersistentDataContainer().get(dropOwnerKey, PersistentDataType.STRING);
        if (owner == null) return false;

        return !owner.equals(player.getUniqueId().toString());
    }

    public void syncExternalDamage(LivingEntity entity) {
        MobRuntime runtime = runtime(entity);
        if (runtime == null || entity.isDead()) return;

        applyHealth(entity, runtime.profile());
        updateName(entity);
    }

    public boolean isRpgMob(Entity entity) {
        return entity.getPersistentDataContainer().has(mobIdKey, PersistentDataType.STRING);
    }

    public boolean isFireProof(Entity entity) {
        MobRuntime runtime = runtime(entity);
        return runtime != null && runtime.definition().fireProof();
    }

    public boolean isCurrentTarget(LivingEntity entity, Player player) {
        return isCurrentTarget(entity, player, System.currentTimeMillis());
    }

    public void remove(Entity entity) {
        forget(entity.getUniqueId());
        statusEffectService.clear(entity);
    }

    /** Drops what is kept for a mob that is gone (dead, or its chunk unloaded) and cannot be looked up any more. */
    public void forget(UUID uuid) {
        runtimes.remove(uuid);
        targetLocks.remove(uuid);
        drivenMelee.remove(uuid);
        nextMelee.remove(uuid);
        lastSeen.remove(uuid);
        skillBusy.remove(uuid);
        skillStarted.remove(uuid);
        behaviors.remove(uuid);
        removeStatusDisplay(uuid);
    }

    public void clear() {
        for (UUID uuid : runtimes.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                statusEffectService.clear(entity);
                if ((attackMode.contains(uuid) || skillBusy.contains(uuid)) && entity instanceof Mob mob) mob.setAI(true);
            }
        }
        attackMode.clear();
        skillBusy.clear();
        drivenMelee.clear();
        behaviors.clear();
        btViewers.clear();
        btViewDisplays.clear();
        runtimes.clear();
        targetLocks.clear();
        clearStatusDisplays();
    }

    public void refreshDisplay(LivingEntity entity) {
        updateName(entity);
    }

    // ---- chasing -----------------------------------------------------------------------------------------------

    private static final long CHASE_PERIOD_TICKS = 5L;
    private long chaseCounter;

    /**
     * Every 5 ticks every mob with AI keeps its target set and walks to it with its own pathfinding on top of the
     * vanilla goals (which stop on their own now and then: they idle when the path is lost or the target was
     * dropped), so a mob never wanders or stands still while there is a player to go after. The pathing is spread
     * over four ticks per mob to keep the cost low.
     */
    private void tickChase() {
        chaseCounter++;
        for (UUID uuid : new ArrayList<>(runtimes.keySet())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof Mob mob) || mob.isDead() || !mob.isValid()) continue;
            MobRuntime runtime = runtimes.get(uuid);
            if (runtime == null || !runtime.definition().ai() || attackMode.contains(uuid) || behaviors.containsKey(uuid)) continue;

            Player target = chaseTarget(mob);
            if (target == null) continue;
            if (!target.equals(mob.getTarget())) mob.setTarget(target);

            double distanceSquared = mob.getLocation().distanceSquared(target.getLocation());
            if (drivenMelee.contains(uuid)) driveMelee(mob, target, distanceSquared);
            if (distanceSquared <= 2.25) continue; // already next to it
            int phase = (uuid.hashCode() & 0x7fffffff) % 4;
            boolean due = chaseCounter % 4 == phase;
            boolean stuck = !mob.getPathfinder().hasPath() && (chaseCounter + phase) % 2 == 0;
            if (due || stuck) mob.getPathfinder().moveTo(target, 1.0);
        }
    }

    // ---- mobs that cannot attack by themselves (pigs, villagers, ...) ------------------------------------------------

    /** Mobs whose vanilla goals were removed: the plugin walks them to the player and does their melee hits. */
    private final java.util.Set<UUID> drivenMelee = new java.util.HashSet<>();
    private final Map<UUID, Long> nextMelee = new HashMap<>();

    /**
     * A mob without an attack of its own (no attack-damage attribute: pigs, cows, villagers, ...) can still be an
     * enemy: its vanilla goals (panic, wandering, ...) are removed and the chase loop moves it and hits for it.
     */
    private void configureDriven(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return;
        if (mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) return;
        Bukkit.getMobGoals().removeAllGoals(mob);
        drivenMelee.add(mob.getUniqueId());
    }

    private void driveMelee(Mob mob, Player target, double distanceSquared) {
        if (distanceSquared > 2.9 * 2.9 / 2.0) return; // about 2 blocks
        long now = Bukkit.getCurrentTick();
        if (now < nextMelee.getOrDefault(mob.getUniqueId(), 0L)) return;
        nextMelee.put(mob.getUniqueId(), now + 20L);
        mob.swingMainHand();
        hurt(mob, target, 1.0);
    }

    private MobModels models;

    /** The model feature (net.tkgon.mc.iruuRPG.model), if it is running. */
    public void setModels(MobModels models) {
        this.models = models;
    }

    // ---- behavior trees and state machines ---------------------------------------------------------------------------

    /** A mob run by a behavior tree: the runner, the host it acts through and when it is ticked next. */
    private static final class BehaviorMob {
        private final net.tkgon.mc.iruuRPG.mob.behavior.BehaviorRunner runner;
        private final BtHost host;
        private int nextTick;

        private BehaviorMob(net.tkgon.mc.iruuRPG.mob.behavior.BehaviorRunner runner, BtHost host) {
            this.runner = runner;
            this.host = host;
        }

        private net.tkgon.mc.iruuRPG.mob.behavior.BehaviorRunner runner() {
            return runner;
        }
    }

    private final Map<UUID, BehaviorMob> behaviors = new HashMap<>();
    /** The skills a behavior asked for that do not exist (each is reported once). */
    private final java.util.Set<String> missingSkills = new java.util.HashSet<>();
    /** What the behavior-tree mobs share: attack slots and shared cooldowns. */
    private final net.tkgon.mc.iruuRPG.mob.behavior.Coordinator btCoordinator = new net.tkgon.mc.iruuRPG.mob.behavior.Coordinator();

    /** A mob whose yaml has a `behavior:` section with `mode: bt` is run by it: its vanilla goals go and the tree moves it, hits and shoots for it. */
    private void startBehavior(LivingEntity entity, RpgMobDefinition definition) {
        if (!definition.ai() || !definition.usesBehaviorTree() || !(entity instanceof Mob mob)) return;
        BtHost host = new BtHost(mob, entity.getLocation().clone());
        List<String> problems = new ArrayList<>();
        net.tkgon.mc.iruuRPG.mob.behavior.BehaviorRunner runner =
                net.tkgon.mc.iruuRPG.mob.behavior.BehaviorRunner.create(definition.id(), definition.behavior(), host, problems);
        if (runner == null) {
            plugin.getLogger().warning("mobs: " + definition.id() + " keeps its older behavior: " + String.join("; ", problems));
            return;
        }
        Bukkit.getMobGoals().removeAllGoals(mob);
        BehaviorMob behavior = new BehaviorMob(runner, host);
        behavior.nextTick = Bukkit.getCurrentTick() + 1 + (int) ((entity.getUniqueId().hashCode() & 0x7fffffff) % Math.max(1, runner.interval()));
        behaviors.put(entity.getUniqueId(), behavior);
    }

    // ---- the behavior view: a command shows the state of every behavior-tree mob (and its query points) to the player who asked ---------

    private final java.util.Set<UUID> btViewers = new java.util.HashSet<>();
    /** The debug text of each behavior-tree mob (shown only to the viewers), by mob. */
    private final Map<UUID, UUID> btViewDisplays = new HashMap<>();

    /** Turns the behavior view on or off for the player (it shows the state and the running nodes above every behavior-tree mob, and the points its queries chose). True when it is on now. */
    public boolean toggleBehaviorView(Player player) {
        return setBehaviorView(player, !btViewers.contains(player.getUniqueId()));
    }

    public boolean setBehaviorView(Player player, boolean on) {
        if (on) btViewers.add(player.getUniqueId());
        else btViewers.remove(player.getUniqueId());
        if (btViewers.isEmpty()) clearBehaviorView();
        return on;
    }

    public boolean behaviorViewOn(Player player) {
        return btViewers.contains(player.getUniqueId());
    }

    private void clearBehaviorView() {
        for (UUID displayId : new ArrayList<>(btViewDisplays.values())) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null) display.remove();
        }
        btViewDisplays.clear();
    }

    /** Every 2 ticks while somebody has the view on: the debug text follows each behavior-tree mob and the viewers near it see it; query points are drawn every 10 ticks. */
    private void tickBehaviorView() {
        btViewers.removeIf(id -> Bukkit.getPlayer(id) == null);
        if (btViewers.isEmpty()) {
            if (!btViewDisplays.isEmpty()) clearBehaviorView();
            return;
        }
        double range = plugin.getConfig().getDouble("mob.behavior-view.range", 40.0);
        boolean points = Bukkit.getCurrentTick() % 10 < 2;
        List<Player> viewers = new ArrayList<>();
        for (UUID id : btViewers) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null) viewers.add(viewer);
        }
        btViewDisplays.keySet().removeIf(id -> !behaviors.containsKey(id));
        for (Map.Entry<UUID, BehaviorMob> entry : new ArrayList<>(behaviors.entrySet())) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || mob.isDead() || !mob.isValid()) continue;
            List<Player> near = new ArrayList<>();
            for (Player viewer : viewers) {
                if (viewer.getWorld().equals(mob.getWorld()) && viewer.getLocation().distanceSquared(mob.getLocation()) <= range * range) near.add(viewer);
            }
            TextDisplay display = behaviorViewDisplay(mob);
            if (near.isEmpty()) {
                if (display != null) {
                    for (Player viewer : viewers) viewer.hideEntity(plugin, display);
                }
                continue;
            }
            if (display == null) display = spawnBehaviorViewDisplay(mob);
            display.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize("§6[BT] §f" + mob.getName() + "\n" + String.join("\n", entry.getValue().runner.debugLines())));
            display.teleport(statusDisplayLocation(mob).add(0.0, 0.6, 0.0));
            for (Player viewer : viewers) {
                if (near.contains(viewer)) viewer.showEntity(plugin, display);
                else viewer.hideEntity(plugin, display);
            }
            if (points) drawQueryPoints(entry.getValue().runner, near);
        }
    }

    private TextDisplay behaviorViewDisplay(LivingEntity mob) {
        UUID displayId = btViewDisplays.get(mob.getUniqueId());
        if (displayId == null) return null;
        Entity display = Bukkit.getEntity(displayId);
        if (display instanceof TextDisplay textDisplay && display.isValid() && display.getWorld().equals(mob.getWorld())) return textDisplay;
        if (display != null) display.remove();
        btViewDisplays.remove(mob.getUniqueId());
        return null;
    }

    private TextDisplay spawnBehaviorViewDisplay(LivingEntity mob) {
        TextDisplay display = mob.getWorld().spawn(statusDisplayLocation(mob).add(0.0, 0.6, 0.0), TextDisplay.class, spawned -> {
            spawned.setVisibleByDefault(false);
            spawned.getPersistentDataContainer().set(statusDisplayKey, PersistentDataType.STRING, "bt-view:" + mob.getUniqueId());
            spawned.text(Component.empty());
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(true);
            spawned.setShadowed(true);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            spawned.setLineWidth(220);
            spawned.setViewRange(1.0f);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTeleportDuration(3);
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
        });
        btViewDisplays.put(mob.getUniqueId(), display.getUniqueId());
        return display;
    }

    /**
     * The points the queries of a mob chose, drawn to the viewers near it: every candidate as a dust speck (red for a low score to green for a high one, grey when a test threw it
     * out), the place chosen as a column of light with a line to it from the mob.
     */
    private void drawQueryPoints(net.tkgon.mc.iruuRPG.mob.behavior.BehaviorRunner runner, List<Player> viewers) {
        int now = Bukkit.getCurrentTick();
        for (net.tkgon.mc.iruuRPG.mob.behavior.EqsResult result : runner.eqsResults()) {
            if (now - result.tick() > 100) continue;
            for (net.tkgon.mc.iruuRPG.mob.behavior.EqsResult.Candidate candidate : result.candidates()) {
                Location at = candidate.place().clone().add(0.0, 0.3, 0.0);
                Color color = candidate.kept()
                        ? Color.fromRGB((int) (255 * (1.0 - candidate.score())), (int) (60 + 195 * candidate.score()), 40)
                        : Color.fromRGB(110, 110, 110);
                for (Player viewer : viewers) viewer.spawnParticle(Particle.REDSTONE, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, candidate.kept() ? 1.3f : 0.8f));
            }
            Location chosen = result.chosen();
            if (chosen == null) continue;
            for (Player viewer : viewers) {
                for (double y = 0.0; y <= 2.0; y += 0.4) viewer.spawnParticle(Particle.END_ROD, chosen.clone().add(0.0, y, 0.0), 1, 0, 0, 0, 0);
            }
        }
    }

    /** Every tick: each mob run by a behavior is ticked when its turn comes (every `tick-interval` ticks, spread so that they do not all run in the same tick). */
    private void tickBehaviors() {
        if (behaviors.isEmpty()) return;
        int now = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, BehaviorMob> entry : new ArrayList<>(behaviors.entrySet())) {
            BehaviorMob behavior = entry.getValue();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || mob.isDead() || !mob.isValid()) {
                behaviors.remove(entry.getKey());
                continue;
            }
            if (now < behavior.nextTick) continue;
            behavior.nextTick = now + behavior.runner.interval();
            try {
                behavior.runner.tick();
            } catch (RuntimeException exception) {
                // a tree that breaks stops, and the mob goes back to its older behavior
                plugin.getLogger().log(java.util.logging.Level.WARNING, "mobs: the behavior of " + mob.getType() + " stopped after an error", exception);
                behaviors.remove(entry.getKey());
            }
        }
    }

    /** What a behavior tree can do with a mob, through the same hit, skills and shots as the older behavior. */
    private final class BtHost implements net.tkgon.mc.iruuRPG.mob.behavior.BehaviorHost {
        private final Mob mob;
        private final Location home;

        private BtHost(Mob mob, Location home) {
            this.mob = mob;
            this.home = home;
        }

        @Override
        public Mob mob() {
            return mob;
        }

        @Override
        public int tick() {
            return Bukkit.getCurrentTick();
        }

        @Override
        public Player findTarget(double range) {
            Player best = null;
            double bestDistance = range * range;
            for (Player player : mob.getWorld().getPlayers()) {
                if (!canTarget(mob, player)) continue;
                double distance = player.getLocation().distanceSquared(mob.getLocation());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
            if (best != null) mob.setTarget(best);
            return best;
        }

        @Override
        public boolean valid(Player player, double range) {
            return canTarget(mob, player) && player.getLocation().distanceSquared(mob.getLocation()) <= range * range;
        }

        @Override
        public boolean moveTo(Location at, double speed) {
            return mob.getPathfinder().moveTo(at, speed);
        }

        @Override
        public void stopMoving() {
            mob.getPathfinder().stopPathfinding();
        }

        @Override
        public boolean hasPath() {
            return mob.getPathfinder().hasPath();
        }

        @Override
        public void face(Location at) {
            Vector direction = at.toVector().subtract(mob.getEyeLocation().toVector());
            if (direction.lengthSquared() < 1.0E-6) return;
            Location look = mob.getLocation().clone();
            look.setDirection(direction);
            mob.setRotation(look.getYaw(), look.getPitch());
            mob.setBodyYaw(look.getYaw());
        }

        @Override
        public void lookAt(Location at) {
            mob.lookAt(at);
        }

        @Override
        public void attack(Player target, double rate) {
            mob.swingMainHand();
            if (models != null) models.play(mob, "attack");
            hurt(mob, target, rate);
        }

        @Override
        public int areaAttack(net.tkgon.mc.iruuRPG.mob.behavior.Area area, double rate, double knockback) {
            Location base = mob.getLocation();
            Vector facing = base.getDirection().setY(0.0);
            if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
            facing.normalize();
            Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
            Location centre = base.clone().add(facing.clone().multiply(area.forward()));
            mob.swingMainHand();
            int hit = 0;
            for (Player player : mob.getWorld().getPlayers()) {
                if (!canTarget(mob, player)) continue;
                Location at = player.getLocation();
                if (Math.abs(at.getY() - base.getY()) > area.height()) continue;
                boolean inside = switch (area.shape()) {
                    case "cone" -> {
                        Vector offset = at.toVector().subtract(base.toVector()).setY(0.0);
                        double distance = offset.length();
                        yield distance <= area.radius() && (distance < 0.5 || Math.toDegrees(facing.angle(offset)) <= area.angle() / 2.0);
                    }
                    case "line" -> {
                        Vector offset = at.toVector().subtract(base.toVector()).setY(0.0);
                        double along = offset.dot(facing) - area.forward();
                        yield along >= 0.0 && along <= area.radius() && Math.abs(offset.dot(right)) <= area.width() / 2.0;
                    }
                    default -> Math.hypot(at.getX() - centre.getX(), at.getZ() - centre.getZ()) <= area.radius();
                };
                if (!inside) continue;
                hurt(mob, player, rate);
                if (knockback > 0.0) {
                    Vector away = at.toVector().subtract(base.toVector()).setY(0.0);
                    if (away.lengthSquared() < 1.0E-6) away = facing.clone();
                    player.setVelocity(player.getVelocity().add(away.normalize().multiply(knockback).setY(0.25)));
                }
                hit++;
            }
            // the blow lands: the outline flashes and the ground shakes
            drawArea(area, base, facing, right, Particle.SWEEP_ATTACK, 1.0f, true);
            mob.getWorld().playSound(centre, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
            mob.getWorld().playSound(centre, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.4f);
            return hit;
        }

        @Override
        public void telegraph(net.tkgon.mc.iruuRPG.mob.behavior.Area area, int ticks) {
            // the outline of the area on the ground, drawn every 4 ticks until the hit (it follows the way the mob faced when it began)
            Location base = mob.getLocation();
            Vector facing = base.getDirection().setY(0.0);
            if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
            facing.normalize();
            Vector right = new Vector(-facing.getZ(), 0.0, facing.getX());
            Vector way = facing;
            for (int tick = 0; tick < ticks; tick += 4) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (mob.isValid() && !mob.isDead()) drawArea(area, base, way, right, Particle.REDSTONE, 1.0f, false);
                }, tick);
            }
        }

        /** The outline of an area: a ring, a fan or a box on the ground (red dust as a warning; the blow itself with sweeps). */
        private void drawArea(net.tkgon.mc.iruuRPG.mob.behavior.Area area, Location base, Vector facing, Vector right, Particle particle, float size, boolean blow) {
            World world = base.getWorld();
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(230, 40, 30), 1.2f * size);
            List<Location> points = new ArrayList<>();
            double y = base.getY() + 0.15;
            switch (area.shape()) {
                case "cone" -> {
                    double half = Math.toRadians(area.angle() / 2.0);
                    for (double a = -half; a <= half + 1.0E-6; a += Math.max(0.05, half / 10.0)) {
                        Vector dir = facing.clone().multiply(Math.cos(a)).add(right.clone().multiply(Math.sin(a)));
                        points.add(base.clone().add(dir.multiply(area.radius())).add(0.0, 0.15, 0.0));
                    }
                    for (double d = 0.5; d < area.radius(); d += 0.6) {
                        for (double a : new double[]{-half, half}) {
                            Vector dir = facing.clone().multiply(Math.cos(a)).add(right.clone().multiply(Math.sin(a)));
                            points.add(base.clone().add(dir.multiply(d)).add(0.0, 0.15, 0.0));
                        }
                    }
                }
                case "line" -> {
                    Location start = base.clone().add(facing.clone().multiply(area.forward()));
                    for (double d = 0.0; d <= area.radius(); d += 0.5) {
                        for (double s : new double[]{-area.width() / 2.0, area.width() / 2.0}) points.add(start.clone().add(facing.clone().multiply(d)).add(right.clone().multiply(s)).add(0.0, 0.15, 0.0));
                    }
                    for (double s = -area.width() / 2.0; s <= area.width() / 2.0; s += 0.5) points.add(start.clone().add(facing.clone().multiply(area.radius())).add(right.clone().multiply(s)).add(0.0, 0.15, 0.0));
                }
                default -> {
                    Location centre = base.clone().add(facing.clone().multiply(area.forward()));
                    int count = (int) Math.max(16, area.radius() * 10);
                    for (int index = 0; index < count; index++) {
                        double a = Math.PI * 2.0 * index / count;
                        points.add(new Location(world, centre.getX() + Math.cos(a) * area.radius(), y, centre.getZ() + Math.sin(a) * area.radius()));
                    }
                }
            }
            for (Location point : points) {
                if (particle == Particle.REDSTONE) world.spawnParticle(Particle.REDSTONE, point, 1, 0, 0, 0, 0, dust);
                else if (blow && ThreadLocalRandom.current().nextInt(3) == 0) world.spawnParticle(particle, point.clone().add(0.0, 0.4, 0.0), 1, 0, 0, 0, 0);
            }
        }

        @Override
        public boolean playAnimation(String name) {
            return models != null && models.play(mob, name);
        }

        @Override
        public double animationSeconds(String name) {
            return models == null ? 0.0 : models.length(mob, name);
        }

        @Override
        public int skillCount() {
            MobRuntime runtime = runtimes.get(mob.getUniqueId());
            return runtime == null ? 0 : runtime.definition().skills().size();
        }

        @Override
        public boolean startSkill(int index, Player target) {
            return startSkill(index, target, false);
        }

        @Override
        public boolean startSkill(int index, Player target, boolean track) {
            MobRuntime runtime = runtimes.get(mob.getUniqueId());
            if (runtime == null || skillRunner == null || skillBusy.contains(mob.getUniqueId())) return false;
            List<SkillSpec> skills = runtime.definition().skills();
            if (index < 0 || index >= skills.size()) return false;
            RpgMobService.this.startSkill(mob, target, skills.get(index), track);
            if (models != null && !models.play(mob, "skill")) models.play(mob, "attack");
            return true;
        }

        @Override
        public boolean startSkill(String id, Player target) {
            return startSkill(id, target, false);
        }

        @Override
        public boolean startSkill(String id, Player target, boolean track) {
            MobRuntime runtime = runtimes.get(mob.getUniqueId());
            if (runtime == null || skillRunner == null || skillBusy.contains(mob.getUniqueId())) return false;
            SkillSpec spec = null;
            for (SkillSpec own : runtime.definition().skills()) if (id.equals(own.id())) spec = own;
            if (spec == null) spec = mobRegistry.skillRegistry().find(id).orElse(null);
            if (spec == null) {
                if (missingSkills.add(runtime.definition().id() + "|" + id)) {
                    plugin.getLogger().warning("mobs: " + runtime.definition().id() + ": use-skill: there is no skill '" + id + "' (skills/*.yml)");
                }
                return false;
            }
            RpgMobService.this.startSkill(mob, target, spec, track);
            return true;
        }

        @Override
        public boolean startUsableSkill(Player target, boolean track) {
            MobRuntime runtime = runtimes.get(mob.getUniqueId());
            if (runtime == null || skillRunner == null || skillBusy.contains(mob.getUniqueId())) return false;
            List<SkillSpec> skills = runtime.definition().skills();
            if (skills.isEmpty()) return false;
            long now = Bukkit.getCurrentTick();
            if (now < skillLockUntil.getOrDefault(mob.getUniqueId(), 0L)) return false;
            long[] ready = skillReady.get(mob.getUniqueId());
            if (ready == null || ready.length != skills.size()) {
                // the first use comes a little after the fight starts, as for the mobs without a tree
                ready = new long[skills.size()];
                for (int index = 0; index < ready.length; index++) ready[index] = now + ThreadLocalRandom.current().nextInt(40, 100);
                skillReady.put(mob.getUniqueId(), ready);
            }
            double distance = mob.getLocation().distance(target.getLocation());
            boolean sees = mob.hasLineOfSight(target);
            List<Integer> usable = new ArrayList<>();
            for (int index = 0; index < skills.size(); index++) {
                if (now >= ready[index] && skillUsable(mob, target, skills.get(index), distance, sees)) usable.add(index);
            }
            if (usable.isEmpty()) return false;
            int chosen = usable.get(ThreadLocalRandom.current().nextInt(usable.size()));
            ready[chosen] = now + skills.get(chosen).cooldownTicks();
            skillLockUntil.put(mob.getUniqueId(), now + 40L);
            RpgMobService.this.startSkill(mob, target, skills.get(chosen), track);
            if (models != null && !models.play(mob, "skill")) models.play(mob, "attack");
            return true;
        }

        @Override
        public boolean skillRunning() {
            return skillBusy.contains(mob.getUniqueId());
        }

        @Override
        public boolean shoot(Player target, double rate) {
            MobRuntime runtime = runtimes.get(mob.getUniqueId());
            RangedSpec spec = runtime == null ? null : runtime.definition().ranged();
            if (spec == null) return false;
            RpgMobService.this.shoot(mob, target, spec, rate);
            return true;
        }

        @Override
        public double hpRatio() {
            MobRuntime runtime = runtimes.get(mob.getUniqueId());
            if (runtime == null || runtime.profile().maxHp() <= 0.0) return 1.0;
            return Math.max(0.0, Math.min(1.0, runtime.profile().currentHp() / runtime.profile().maxHp()));
        }

        @Override
        public boolean canSee(Player player) {
            return mob.hasLineOfSight(player);
        }

        @Override
        public Location home() {
            return home.clone();
        }

        @Override
        public String typeId() {
            MobRuntime runtime = runtimes.get(mob.getUniqueId());
            return runtime == null ? "" : runtime.definition().id();
        }

        private boolean sameKind(UUID other, boolean sameType) {
            if (!sameType) return true;
            MobRuntime mine = runtimes.get(mob.getUniqueId());
            MobRuntime theirs = runtimes.get(other);
            return mine != null && theirs != null && mine.definition().id().equals(theirs.definition().id());
        }

        @Override
        public int alliesNear(double radius, boolean sameType) {
            return allyLocations(radius, sameType).size();
        }

        @Override
        public List<Location> allyLocations(double radius, boolean sameType) {
            List<Location> list = new ArrayList<>();
            for (UUID id : behaviors.keySet()) {
                if (id.equals(mob.getUniqueId()) || !sameKind(id, sameType)) continue;
                Entity other = Bukkit.getEntity(id);
                if (!(other instanceof LivingEntity living) || living.isDead() || !living.isValid() || !other.getWorld().equals(mob.getWorld())) continue;
                if (other.getLocation().distanceSquared(mob.getLocation()) <= radius * radius) list.add(other.getLocation());
            }
            return list;
        }

        @Override
        public net.tkgon.mc.iruuRPG.mob.behavior.Coordinator coordinator() {
            return btCoordinator;
        }

        @Override
        public int crowdNear(Location at, double radius) {
            int count = 0;
            for (UUID id : behaviors.keySet()) {
                if (id.equals(mob.getUniqueId())) continue;
                Entity other = Bukkit.getEntity(id);
                if (other != null && other.getWorld().equals(at.getWorld()) && other.getLocation().distanceSquared(at) <= radius * radius) count++;
            }
            return count;
        }
    }

    // ---- skills ----------------------------------------------------------------------------------------------------

    /** The damage rate of the skill hit that is being delivered right now (1 for a normal hit). */
    private final Map<UUID, Double> pendingRate = new HashMap<>();
    private final Map<UUID, long[]> skillReady = new HashMap<>();
    private final Map<UUID, Long> skillLockUntil = new HashMap<>();
    /** Mobs that are in the middle of a skill (AI off). */
    private final java.util.Set<UUID> skillBusy = new java.util.HashSet<>();

    /** The damage rate of the hit being delivered by this mob (1.0 when it is a normal hit). */
    public double pendingDamageRate(Entity mob) {
        return mob == null ? 1.0 : pendingRate.getOrDefault(mob.getUniqueId(), 1.0);
    }

    /**
     * Hurts a player as this mob: an ordinary damage event (so the mob's RPG damage, the player's defense and the
     * status effects all apply), with the damage multiplied by {@code rate}.
     */
    private void hurt(Mob mob, Player victim, double rate) {
        if (victim.isDead() || !victim.isValid()) return;
        pendingRate.put(mob.getUniqueId(), rate);
        try {
            victim.damage(1.0, mob);
        } finally {
            pendingRate.remove(mob.getUniqueId());
        }
    }

    /** Every 5 ticks: a mob with skills that is ready and has its target in reach starts one. */
    private void tickSkills() {
        long now = Bukkit.getCurrentTick();
        skillBusy.removeIf(id -> !runtimes.containsKey(id));
        skillStarted.keySet().removeIf(id -> !skillBusy.contains(id));
        // watchdog: a skill that has been going for 20 seconds is stuck (no skill is that long): free the mob
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(skillStarted.entrySet())) {
            if (now - entry.getValue() < SKILL_WATCHDOG_TICKS) continue;
            Entity stuck = Bukkit.getEntity(entry.getKey());
            if (stuck instanceof Mob stuckMob) {
                plugin.getLogger().warning("mobs: a skill of " + stuckMob.getType() + " ran for 20 s; ended it");
                endSkill(stuckMob);
            } else {
                skillBusy.remove(entry.getKey());
                skillStarted.remove(entry.getKey());
            }
        }
        skillReady.keySet().removeIf(id -> !runtimes.containsKey(id));
        skillLockUntil.keySet().removeIf(id -> !runtimes.containsKey(id));
        for (UUID uuid : new ArrayList<>(runtimes.keySet())) {
            MobRuntime runtime = runtimes.get(uuid);
            if (runtime == null || runtime.definition().skills().isEmpty() || !runtime.definition().ai()) continue;
            if (skillBusy.contains(uuid) || behaviors.containsKey(uuid)) continue;
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof Mob mob) || mob.isDead() || !mob.isValid()) continue;
            if (now < skillLockUntil.getOrDefault(uuid, 0L)) continue;
            Player target = mob.getTarget() instanceof Player player && canTarget(mob, player) ? player : null;
            if (target == null) continue;

            List<SkillSpec> skills = runtime.definition().skills();
            long[] ready = skillReady.get(uuid);
            if (ready == null) {
                // the first use comes a little after the fight starts, not at once and not in lock step
                ready = new long[skills.size()];
                for (int index = 0; index < ready.length; index++) {
                    ready[index] = now + ThreadLocalRandom.current().nextInt(40, 100);
                }
                skillReady.put(uuid, ready);
            }

            double distance = mob.getLocation().distance(target.getLocation());
            boolean sees = mob.hasLineOfSight(target);
            List<Integer> usable = new ArrayList<>();
            for (int index = 0; index < skills.size(); index++) {
                if (now >= ready[index] && skillUsable(mob, target, skills.get(index), distance, sees)) usable.add(index);
            }
            if (usable.isEmpty()) continue;

            int chosen = usable.get(ThreadLocalRandom.current().nextInt(usable.size()));
            SkillSpec spec = skills.get(chosen);
            ready[chosen] = now + spec.cooldownTicks();
            skillLockUntil.put(uuid, now + 40L); // a pause between two skills
            startSkill(mob, target, spec);
        }
    }

    private boolean skillUsable(Mob mob, Player target, SkillSpec whole, double distance, boolean sees) {
        SkillSpec spec = whole.first(); // a combo can be used when its first step can
        return switch (spec.template()) {
            case STRIKE -> distance <= spec.radius() + 1.0;
            case SWEEP -> distance <= spec.range() && inFront(mob, target, Math.min(90.0, spec.angle() / 2.0));
            case BARRAGE, DASH -> sees && distance >= 4.0 && distance <= spec.range();
            case WAVE, BEAM -> sees && distance >= 3.0 && distance <= spec.range();
            case VOLLEY -> sees && distance >= 4.0 && distance <= spec.range();
            case NOVA -> distance <= spec.radius();
            case TELEPORT -> sees && distance >= 4.0 && distance <= spec.range();
            case COMBO -> false;
        };
    }

    private boolean inFront(Mob mob, Player target, double halfAngleDegrees) {
        Vector toTarget = target.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0.0);
        if (toTarget.lengthSquared() < 1.0E-6) return true;
        Vector facing = mob.getLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) return true;
        return Math.toDegrees(facing.normalize().angle(toTarget.normalize())) <= halfAngleDegrees;
    }

    /** When each busy mob's skill began (server tick): a skill that runs far too long is ended by force. */
    private final Map<UUID, Long> skillStarted = new HashMap<>();
    private static final long SKILL_WATCHDOG_TICKS = 400L; // 20 seconds

    private void beginSkill(Mob mob) {
        skillBusy.add(mob.getUniqueId());
        skillStarted.put(mob.getUniqueId(), (long) Bukkit.getCurrentTick());
        mob.getPathfinder().stopPathfinding();
        mob.setAI(false);
    }

    private void endSkill(Mob mob) {
        skillBusy.remove(mob.getUniqueId());
        skillStarted.remove(mob.getUniqueId());
        if (mob.isValid() && !mob.isDead() && !attackMode.contains(mob.getUniqueId())) mob.setAI(true);
    }

    private net.tkgon.mc.iruuRPG.skill.SkillRunner skillRunner;

    public void setSkillRunner(net.tkgon.mc.iruuRPG.skill.SkillRunner skillRunner) {
        this.skillRunner = skillRunner;
    }

    private void startSkill(Mob mob, Player target, SkillSpec spec) {
        startSkill(mob, target, spec, false);
    }

    /**
     * Starts a skill of a mob at the player. Without {@code track} the mob turns to the player once, when the skill starts, and keeps that direction until it is over (the
     * warning of where it lands stays where it was shown; the player can step out of it); with {@code track} it keeps turning to the player while it winds up (a behavior tree
     * asks for that with `track: true`). A skill whose own `lock-aim` is on is locked either way.
     */
    private void startSkill(Mob mob, Player target, SkillSpec spec, boolean track) {
        if (skillRunner == null) return;
        beginSkill(mob);
        SkillSpec run = track ? spec : spec.withLockedAim();
        skillRunner.start(new MobCaster(mob), run, () -> target.isOnline() && !target.isDead()
                        ? target.getLocation().add(0.0, target.getHeight() * 0.6, 0.0) : null,
                () -> endSkill(mob));
    }

    /** A mob as the caster of a skill. */
    private final class MobCaster implements net.tkgon.mc.iruuRPG.skill.SkillCaster {
        private final Mob mob;

        private MobCaster(Mob mob) {
            this.mob = mob;
        }

        @Override
        public World world() {
            return mob.getWorld();
        }

        @Override
        public Location location() {
            return mob.getLocation();
        }

        @Override
        public Location eye() {
            return mob.getEyeLocation();
        }

        @Override
        public boolean valid() {
            return mob.isValid() && !mob.isDead();
        }

        @Override
        public net.tkgon.mc.iruuRPG.stat.Element element() {
            return boltElement(mob);
        }

        @Override
        public void freeze() {
            mob.getPathfinder().stopPathfinding();
            mob.setAI(false);
        }

        @Override
        public void release() {
            // the mob is free again; endSkill (at the very end) clears the busy mark
            if (mob.isValid() && !mob.isDead() && !attackMode.contains(mob.getUniqueId())) mob.setAI(true);
        }

        @Override
        public void face(Location at) {
            Location look = mob.getLocation().clone();
            look.setDirection(at.toVector().subtract(mob.getEyeLocation().toVector()));
            mob.setRotation(look.getYaw(), look.getPitch());
            mob.setBodyYaw(look.getYaw());
        }

        @Override
        public void swing() {
            mob.swingMainHand();
        }

        @Override
        public void lunge(Vector velocity) {
            mob.setVelocity(velocity);
        }

        @Override
        public void teleport(Location to) {
            mob.setFallDistance(0.0f);
            mob.teleport(to);
        }

        @Override
        public List<Player> candidates() {
            List<Player> result = new ArrayList<>();
            for (Player player : mob.getWorld().getPlayers()) {
                if (canTarget(mob, player)) result.add(player);
            }
            return result;
        }

        @Override
        public boolean applyEffects() {
            return true;
        }

        @Override
        public void damage(org.bukkit.entity.LivingEntity victim, double rate) {
            if (victim instanceof Player player) hurt(mob, player, rate);
        }
    }

    /** The player a mob goes after: its locked target while that is valid, else the nearest player in range. */
    private Player chaseTarget(Mob mob) {
        TargetLock lock = targetLocks.get(mob.getUniqueId());
        if (lock != null) {
            Player locked = Bukkit.getPlayer(lock.playerId());
            if (locked != null && canTarget(mob, locked) && inTargetRange(mob, locked)) return locked;
        }
        MobRuntime runtime = runtimes.get(mob.getUniqueId());
        if (runtime == null) return null;
        selectRandomTarget(mob, runtime.definition(), null, System.currentTimeMillis());
        TargetLock fresh = targetLocks.get(mob.getUniqueId());
        return fresh == null ? null : Bukkit.getPlayer(fresh.playerId());
    }

    private final Map<UUID, Long> lastSeen = new HashMap<>();

    /** How far a mob can see a player to notice them (needs a clear line): mob-ai.sight-range. */
    private double sightRange() {
        return Math.max(2.0, plugin.getConfig().getDouble("mob-ai.sight-range", 16.0));
    }

    /** Within this distance a mob notices a player even without a clear line (it hears them): mob-ai.close-range. */
    private double closeRange() {
        return Math.max(0.0, plugin.getConfig().getDouble("mob-ai.close-range", 5.0));
    }

    /** Farther than this a mob gives up on its target: mob-ai.lose-range. */
    private double loseRange() {
        return Math.max(sightRange(), plugin.getConfig().getDouble("mob-ai.lose-range", 28.0));
    }

    /** How long a mob remembers where it last saw its target when it is out of sight: mob-ai.memory-ticks. */
    private long memoryTicks() {
        return Math.max(0L, plugin.getConfig().getLong("mob-ai.memory-ticks", 100L));
    }

    private void tickTargets() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(runtimes.keySet())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                runtimes.remove(uuid);
                targetLocks.remove(uuid);
                removeStatusDisplay(uuid);
                continue;
            }

            MobRuntime runtime = runtimes.get(uuid);
            if (runtime == null) continue;

            maintainTarget(living, runtime.definition(), now);
        }
    }

    // ---- ranged mobs: explore mode and attack mode -------------------------------------------------------------------

    /** Mobs that are in attack mode right now (standing still, AI off, shooting). */
    private final java.util.Set<UUID> attackMode = new java.util.HashSet<>();
    private final Map<UUID, Long> nextShotTick = new HashMap<>();
    private final Map<UUID, Integer> lostSightTicks = new HashMap<>();

    /** A mob with a "ranged:" section: vanilla projectile launches are replaced by the plugin's own shots. */
    public boolean isRangedMob(Entity entity) {
        MobRuntime runtime = runtime(entity);
        return runtime != null && runtime.definition().ranged() != null;
    }

    /**
     * Every 5 ticks, for each ranged mob. Explore mode: the normal AI walks it toward its target. When that target is
     * within range with a clear line, it switches to attack mode: AI off, facing the target, shooting on a timer.
     * When the target moves away (or out of sight for 1.5 s) it goes back to exploring. It never retreats.
     */
    private void tickRanged() {
        long now = Bukkit.getCurrentTick();
        // a mob that is gone leaves nothing behind
        attackMode.removeIf(id -> !runtimes.containsKey(id));
        nextShotTick.keySet().removeIf(id -> !runtimes.containsKey(id));
        lostSightTicks.keySet().removeIf(id -> !runtimes.containsKey(id));
        for (UUID uuid : new ArrayList<>(runtimes.keySet())) {
            MobRuntime runtime = runtimes.get(uuid);
            RangedSpec spec = runtime == null ? null : runtime.definition().ranged();
            if (spec == null || behaviors.containsKey(uuid)) continue;
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof Mob mob) || mob.isDead() || !mob.isValid() || !runtime.definition().ai()) {
                attackMode.remove(uuid);
                nextShotTick.remove(uuid);
                lostSightTicks.remove(uuid);
                continue;
            }

            Player target = mob.getTarget() instanceof Player player && canTarget(mob, player) ? player : null;
            boolean attacking = attackMode.contains(uuid);
            if (target == null) {
                if (attacking) leaveAttackMode(mob);
                continue;
            }

            double distance = target.getLocation().distance(mob.getLocation());
            boolean sees = mob.hasLineOfSight(target);
            if (!attacking) {
                if (distance <= spec.range() && sees) enterAttackMode(mob);
                continue;
            }

            // attack mode
            if (distance > spec.range() * 1.2) {
                leaveAttackMode(mob);
                continue;
            }
            if (!sees) {
                int lost = lostSightTicks.merge(uuid, 5, Integer::sum);
                if (lost >= 30) {
                    leaveAttackMode(mob);
                    continue;
                }
            } else {
                lostSightTicks.remove(uuid);
            }
            if (skillBusy.contains(uuid)) continue; // a skill is running: no facing, no shot
            face(mob, target);
            if (sees && now >= nextShotTick.getOrDefault(uuid, 0L)) {
                nextShotTick.put(uuid, now + spec.intervalTicks());
                shoot(mob, target, spec, 1.0);
            }
        }
    }

    private void enterAttackMode(Mob mob) {
        attackMode.add(mob.getUniqueId());
        lostSightTicks.remove(mob.getUniqueId());
        // the first shot comes a moment after it stops, not at once
        nextShotTick.put(mob.getUniqueId(), Bukkit.getCurrentTick() + 10L);
        mob.getPathfinder().stopPathfinding();
        mob.setAI(false);
    }

    private void leaveAttackMode(Mob mob) {
        attackMode.remove(mob.getUniqueId());
        lostSightTicks.remove(mob.getUniqueId());
        mob.setAI(true);
    }

    private void face(Mob mob, Player target) {
        Location from = mob.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(from.toVector());
        Location look = mob.getLocation().clone();
        look.setDirection(direction);
        mob.setRotation(look.getYaw(), look.getPitch());
        mob.setBodyYaw(look.getYaw());
    }

    private net.tkgon.mc.iruuRPG.combat.AttackEffects attackEffects;

    public void setAttackEffects(net.tkgon.mc.iruuRPG.combat.AttackEffects attackEffects) {
        this.attackEffects = attackEffects;
    }

    /** The color of a mob's bolt: its strongest element damage, else its weapon's element, else white. */
    private net.tkgon.mc.iruuRPG.stat.Element boltElement(LivingEntity mob) {
        MobRuntime runtime = runtime(mob);
        net.tkgon.mc.iruuRPG.stat.Element best = null;
        double bestDamage = 0.0;
        if (runtime != null) {
            for (net.tkgon.mc.iruuRPG.stat.Element element : net.tkgon.mc.iruuRPG.stat.Element.values()) {
                double damage = runtime.profile().finalElementStats().damage(element);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    best = element;
                }
            }
        }
        if (best != null) return best;
        return attackWeapon(mob).map(RpgItemDefinition::element).orElse(net.tkgon.mc.iruuRPG.stat.Element.WHITE);
    }

    /**
     * One shot: a bolt of dust (the same look as a player's ranged beam, colored by the mob's stats) leaves the mob
     * toward where the target is now and flies at the spec's speed. It stops at the first block, and hits the first
     * player it reaches: the mob's normal RPG damage through an ordinary damage event (so defense, resists and status
     * effects apply). A player who keeps moving can dodge it.
     */
    private void shoot(Mob mob, Player target, RangedSpec spec, double damageRate) {
        World world = mob.getWorld();
        Location from = mob.getEyeLocation().add(mob.getLocation().getDirection().multiply(0.4)).add(0, -0.2, 0);
        Vector direction = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1.0E-6) return;
        direction.normalize();
        net.tkgon.mc.iruuRPG.stat.Element element = boltElement(mob);
        double perTick = spec.speed() / 20.0;
        double maxDistance = spec.range() * 1.5;
        mob.swingMainHand();
        if (attackEffects != null) attackEffects.sounds().boltLaunch(null, from, element);

        new org.bukkit.scheduler.BukkitRunnable() {
            private Location position = from.clone();
            private double travelled;

            @Override
            public void run() {
                if (mob.isDead() || !mob.isValid()) {
                    cancel(); // the shooter is gone: the bolt fades
                    return;
                }
                Vector step = direction.clone().multiply(perTick);
                Location next = position.clone().add(step);

                // a wall stops it
                org.bukkit.util.RayTraceResult wall = world.rayTraceBlocks(position, direction, perTick, org.bukkit.FluidCollisionMode.NEVER, true);
                double wallDistance = wall == null ? Double.MAX_VALUE : wall.getHitPosition().distance(position.toVector());
                // the first player on the way
                Player hit = null;
                double hitDistance = Double.MAX_VALUE;
                for (Player player : world.getPlayers()) {
                    if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) continue;
                    org.bukkit.util.RayTraceResult result = player.getBoundingBox().expand(0.25)
                            .rayTrace(position.toVector(), direction, perTick);
                    if (result == null) continue;
                    double distance = result.getHitPosition().distance(position.toVector());
                    if (distance < hitDistance) {
                        hitDistance = distance;
                        hit = player;
                    }
                }

                if (hit != null && hitDistance <= wallDistance) {
                    Location at = position.clone().add(direction.clone().multiply(hitDistance));
                    if (attackEffects != null) {
                        attackEffects.playBoltSegment(position, at, element);
                        attackEffects.playBoltImpact(at, element);
                    }
                    hurt(mob, hit, damageRate);
                    cancel();
                    return;
                }
                if (wall != null) {
                    Location at = wall.getHitPosition().toLocation(world);
                    if (attackEffects != null) {
                        attackEffects.playBoltSegment(position, at, element);
                        attackEffects.playBoltImpact(at, element);
                    }
                    world.playSound(at, org.bukkit.Sound.BLOCK_STONE_HIT, 0.6f, 1.4f);
                    cancel();
                    return;
                }
                if (attackEffects != null) attackEffects.playBoltSegment(position, next, element);
                position = next;
                travelled += perTick;
                if (travelled >= maxDistance) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void tickStatusDisplays() {
        for (UUID uuid : new ArrayList<>(runtimes.keySet())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                removeStatusDisplay(uuid);
                continue;
            }

            updateStatusDisplay(living);
        }
    }

    private void configure(LivingEntity entity, RpgMobDefinition definition) {
        entity.getPersistentDataContainer().set(mobIdKey, PersistentDataType.STRING, definition.id());
        entity.setAI(definition.ai());
        entity.setGravity(definition.gravity());
        entity.setSilent(definition.silent());
        entity.setGlowing(definition.glowing());
        entity.setInvulnerable(definition.invulnerable());
        entity.setPersistent(definition.persistent());
        entity.setRemoveWhenFarAway(definition.removeWhenFarAway());
        entity.setFireTicks(0);
        // undead do not burn in the sun (the fire-proof flag is only for fire and lava)
        if (entity instanceof org.bukkit.entity.Zombie zombie) zombie.setShouldBurnInDay(false);
        if (entity instanceof org.bukkit.entity.AbstractSkeleton skeleton) skeleton.setShouldBurnInDay(false);
        if (entity instanceof org.bukkit.entity.Phantom phantom) phantom.setShouldBurnInDay(false);
        if (definition.ai()) configureDriven(entity);

        if (entity instanceof Ageable ageable) {
            if (definition.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        }
    }

    private java.util.function.Predicate<Player> tauntCheck;

    /** Who draws the mobs' attention (the warrior's innate "taunt"); set by the plugin. */
    public void setTauntCheck(java.util.function.Predicate<Player> tauntCheck) {
        this.tauntCheck = tauntCheck;
    }

    private boolean isTaunting(Player player) {
        return tauntCheck != null && tauntCheck.test(player);
    }

    /** A taunting player within class-effects.warrior.taunt-radius (14) that the mob can see, the nearest one; else null. */
    private Player nearbyTaunter(LivingEntity entity) {
        if (tauntCheck == null) return null;
        double radius = Math.max(1.0, plugin.getConfig().getDouble("class-effects.warrior.taunt-radius", 14.0));
        Player best = null;
        double bestDistance = radius * radius;
        for (Player candidate : entity.getWorld().getPlayers()) {
            if (!canTarget(entity, candidate) || !isTaunting(candidate)) continue;
            double distance = candidate.getLocation().distanceSquared(entity.getLocation());
            if (distance > bestDistance || !entity.hasLineOfSight(candidate)) continue;
            bestDistance = distance;
            best = candidate;
        }
        return best;
    }

    private void maintainTarget(LivingEntity entity, RpgMobDefinition definition, long now) {
        // (a mob run by a behavior tree keeps its own target)
        if (behaviors.containsKey(entity.getUniqueId())) return;
        if (!definition.ai() || !(entity instanceof Mob mob)) {
            targetLocks.remove(entity.getUniqueId());
            return;
        }

        TargetLock lock = targetLocks.get(entity.getUniqueId());
        if (lock != null && lock.expiresAtMillis() > now) {
            Player lockedTarget = Bukkit.getPlayer(lock.playerId());
            if (lockedTarget != null && canTarget(entity, lockedTarget) && inTargetRange(entity, lockedTarget)) {
                // a taunting player (warrior) close by pulls the mob over once; it is then locked on like any target,
                // so the mob does not flip between players
                Player taunter = isTaunting(lockedTarget) ? null : nearbyTaunter(entity);
                if (taunter != null) {
                    targetLocks.put(entity.getUniqueId(), new TargetLock(taunter.getUniqueId(), now + TARGET_LOCK_MILLIS));
                    lockedTarget = taunter;
                }
                if (!lockedTarget.equals(mob.getTarget())) {
                    mob.setTarget(lockedTarget);
                }
                return;
            }
        }

        selectRandomTarget(entity, definition, lock == null ? null : lock.playerId(), now);
    }

    private void selectRandomTarget(LivingEntity entity, RpgMobDefinition definition, UUID previousTargetId, long now) {
        if (!definition.ai()) return;
        if (!(entity instanceof Mob mob)) return;

        ArrayList<Player> candidates = nearbyAdventurePlayers(entity);
        if (candidates.isEmpty()) {
            targetLocks.remove(entity.getUniqueId());
            mob.setTarget(null);
            return;
        }

        // the nearest player (not a random one, which made mobs turn away from a player right next to them); a taunting
        // player counts as closer (their distance is multiplied by class-effects.warrior.taunt-distance-factor)
        double tauntFactor = Math.max(0.05, plugin.getConfig().getDouble("class-effects.warrior.taunt-distance-factor", 0.5));
        Player target = candidates.get(0);
        double best = Double.MAX_VALUE;
        for (Player candidate : candidates) {
            double distance = candidate.getLocation().distanceSquared(entity.getLocation());
            if (isTaunting(candidate)) distance *= tauntFactor * tauntFactor;
            if (distance < best) {
                best = distance;
                target = candidate;
            }
        }
        targetLocks.put(entity.getUniqueId(), new TargetLock(target.getUniqueId(), now + TARGET_LOCK_MILLIS));
        mob.setTarget(target);
    }

    /** The players a mob notices: within sight range with a clear line, or right next to it (it hears them). */
    private ArrayList<Player> nearbyAdventurePlayers(LivingEntity entity) {
        double sight = sightRange();
        double sightSquared = sight * sight;
        double close = closeRange();
        double closeSquared = close * close;
        ArrayList<Player> players = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canTarget(entity, player)) continue;

            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance > sightSquared) continue;
            if (distance > closeSquared && !entity.hasLineOfSight(player)) continue;
            players.add(player);
        }

        return players;
    }

    private boolean canTarget(LivingEntity entity, Player player) {
        return player.isOnline()
                && player.isValid()
                && !player.isDead()
                && player.getGameMode() == GameMode.ADVENTURE
                && player.getWorld().equals(entity.getWorld());
    }

    /**
     * A target it already has is kept while it is within the losing range and was seen in the last few seconds (a
     * mob does not forget a player who ducks behind a corner, but it does not chase one through walls forever).
     */
    private boolean inTargetRange(LivingEntity entity, Player player) {
        if (!player.getWorld().equals(entity.getWorld())) return false;

        double range = loseRange();
        double distanceSquared = player.getLocation().distanceSquared(entity.getLocation());
        if (distanceSquared > range * range) return false;
        long now = Bukkit.getCurrentTick();
        if (distanceSquared <= closeRange() * closeRange() || entity.hasLineOfSight(player)) {
            lastSeen.put(entity.getUniqueId(), now);
            return true;
        }
        Long seen = lastSeen.get(entity.getUniqueId());
        return seen != null && now - seen <= memoryTicks();
    }

    private boolean isCurrentTarget(LivingEntity entity, Player player, long now) {
        if (entity == null || player == null) return false;

        TargetLock lock = targetLocks.get(entity.getUniqueId());
        return lock != null
                && lock.expiresAtMillis() > now
                && lock.playerId().equals(player.getUniqueId())
                && canTarget(entity, player)
                && inTargetRange(entity, player);
    }

    private Map<EquipmentSlot, ItemStack> applyEquipment(LivingEntity entity, RpgMobDefinition definition) {
        Map<EquipmentSlot, ItemStack> applied = new EnumMap<>(EquipmentSlot.class);
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return applied;

        for (Map.Entry<EquipmentSlot, MobEquipmentItem> entry : definition.equipment().entrySet()) {
            ItemStack item = item(entry.getValue());
            if (item == null) continue;

            EquipmentSlot slot = entry.getKey();
            switch (slot) {
                case HAND -> {
                    equipment.setItemInMainHand(item);
                    equipment.setItemInMainHandDropChance(0.0f);
                }
                case OFF_HAND -> {
                    equipment.setItemInOffHand(item);
                    equipment.setItemInOffHandDropChance(0.0f);
                }
                case HEAD -> {
                    equipment.setHelmet(item);
                    equipment.setHelmetDropChance(0.0f);
                }
                case CHEST -> {
                    equipment.setChestplate(item);
                    equipment.setChestplateDropChance(0.0f);
                }
                case LEGS -> {
                    equipment.setLeggings(item);
                    equipment.setLeggingsDropChance(0.0f);
                }
                case FEET -> {
                    equipment.setBoots(item);
                    equipment.setBootsDropChance(0.0f);
                }
                default -> {
                }
            }
            applied.put(slot, item);
        }

        return applied;
    }

    private ItemStack item(MobEquipmentItem equipmentItem) {
        if (equipmentItem.hasItem()) {
            RpgItemDefinition itemDefinition = itemRegistry.find(equipmentItem.itemId()).orElse(null);
            if (itemDefinition == null) {
                plugin.getLogger().warning("mobs: unknown RPG item id '" + equipmentItem.itemId() + "'");
                return null;
            }
            return itemFactory.create(itemDefinition);
        }

        if (equipmentItem.hasMaterial()) {
            ItemStack item = new ItemStack(equipmentItem.material());
            applyVisualOptions(item, equipmentItem);
            return item;
        }

        return null;
    }

    private void applyVisualOptions(ItemStack item, MobEquipmentItem equipmentItem) {
        if (!equipmentItem.hasLeatherColor()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leatherArmorMeta) {
            leatherArmorMeta.setColor(equipmentItem.leatherColor());
            item.setItemMeta(leatherArmorMeta);
        }
    }

    private PlayerProfile buildProfile(LivingEntity entity, RpgMobDefinition definition) {
        StatSet stats = definition.stats().copy();
        ElementStatSet elementStats = definition.elementStats().copy();

        for (MobEquipmentItem equipmentItem : definition.equipment().values()) {
            if (!equipmentItem.hasItem()) continue;

            itemRegistry.find(equipmentItem.itemId()).ifPresent(item -> {
                stats.addAll(item.stats());
                elementStats.addAll(item.elementStats());
            });
        }

        double maxHp = stats.get(StatType.MAX_HP);
        if (maxHp <= 0.0) {
            maxHp = plugin.getConfig().getDouble("mob.default-max-hp", 20.0);
            stats.set(StatType.MAX_HP, maxHp);
        }

        double maxMp = stats.get(StatType.MAX_MP);
        if (maxMp <= 0.0) {
            maxMp = plugin.getConfig().getDouble("mob.default-max-mp", 1.0);
            stats.set(StatType.MAX_MP, maxMp);
        }

        PlayerProfile profile = new PlayerProfile(entity.getUniqueId(), maxHp, maxMp);
        profile.setLevel(definition.level());
        profile.baseStats().replaceWith(stats);
        profile.baseElementStats().replaceWith(elementStats);
        profile.recalculate();
        profile.setCurrentHp(profile.maxHp());
        profile.setCurrentMp(profile.maxMp());
        return profile;
    }

    private MobRuntime runtime(Entity entity) {
        MobRuntime runtime = runtimes.get(entity.getUniqueId());
        if (runtime != null) return runtime;

        String id = entity.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
        if (id == null) return null;

        RpgMobDefinition definition = mobRegistry.find(id).orElse(null);
        if (definition == null || !(entity instanceof LivingEntity living)) return null;

        PlayerProfile profile = buildProfile(living, definition);
        double storedHp = entity.getPersistentDataContainer().getOrDefault(mobHpKey, PersistentDataType.DOUBLE, profile.maxHp());
        profile.setCurrentHp(storedHp);
        runtime = new MobRuntime(definition, profile, new HashMap<>());
        runtimes.put(entity.getUniqueId(), runtime);
        applyHealth(living, profile);
        updateName(living);
        return runtime;
    }

    private void applyHealth(LivingEntity entity, PlayerProfile profile) {
        if (profile.currentHp() <= 0.0) {
            if (!entity.isDead()) {
                entity.setHealth(0.0);
            }
            return;
        }

        double proxyHealth = vanillaHealthProxy(profile);
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(proxyHealth);
        }

        if (!entity.isDead()) {
            entity.setHealth(Math.max(0.1, Math.min(proxyHealth, maxHealth(entity))));
        }
    }

    private void storeCurrentHp(LivingEntity entity, double hp) {
        entity.getPersistentDataContainer().set(mobHpKey, PersistentDataType.DOUBLE, Math.max(0.0, hp));
    }

    private void recordDamage(MobRuntime runtime, Player attacker, double damage) {
        if (attacker == null || damage <= 0.0) return;

        runtime.damageByPlayer().merge(attacker.getUniqueId(), damage, Double::sum);
    }

    private void dropReward(LivingEntity entity, UUID ownerId, MobDropItem drop, ThreadLocalRandom random) {
        RpgItemDefinition definition = itemRegistry.find(drop.itemId()).orElse(null);
        if (definition == null) {
            plugin.getLogger().warning("mobs: unknown drop RPG item id '" + drop.itemId() + "'");
            return;
        }

        World world = entity.getWorld();
        Location location = entity.getLocation();
        int amount = drop.rollAmount(random);
        int maxStackSize = Math.max(1, definition.material().getMaxStackSize());
        while (amount > 0) {
            int stackAmount = Math.min(amount, maxStackSize);
            ItemStack itemStack = itemFactory.create(definition);
            itemStack.setAmount(stackAmount);

            Item dropped = world.dropItemNaturally(location, itemStack);
            dropped.setOwner(ownerId);
            dropped.setCanMobPickup(false);
            dropped.setPickupDelay(10);
            dropped.getPersistentDataContainer().set(dropOwnerKey, PersistentDataType.STRING, ownerId.toString());

            amount -= stackAmount;
        }
    }

    private double vanillaHealthProxy(PlayerProfile profile) {
        double configured = plugin.getConfig().getDouble("mob.vanilla-health-proxy", 20.0);
        double proxy = Math.max(1.0, configured);
        return Math.max(1.0, Math.min(proxy, profile.maxHp()));
    }

    private void updateName(LivingEntity entity) {
        MobRuntime runtime = runtime(entity);
        if (runtime == null || !runtime.definition().showHealth()) {
            removeStatusDisplay(entity.getUniqueId());
            return;
        }

        PlayerProfile profile = runtime.profile();
        Component name = attackTag(runtime)
                .append(Component.text(runtime.definition().name(), NamedTextColor.RED))
                .append(Component.text(" Lv" + runtime.definition().level(), NamedTextColor.YELLOW))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(Component.text(format(profile.currentHp()), NamedTextColor.YELLOW))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(format(profile.maxHp()), NamedTextColor.YELLOW))
                .append(Component.text(" HP", NamedTextColor.RED));

        entity.customName(name);
        // (a mob in a model: its name is drawn above the model by the model feature; the game does not draw an invisible mob's name anyway)
        entity.setCustomNameVisible(models == null || models.height(entity) <= 0.0);
        updateStatusDisplay(entity);
    }

    /**
     * "[物・近] " before a mob's name: physical / magic and melee / ranged at a glance. It follows what the mob really
     * does: the kind of its weapon (without a weapon, the plain physical hit), "ranged" when the mob shoots (a
     * ranged: section) or its weapon is a ranged one. Empty when mob.attack-tags is off.
     */
    private Component attackTag(MobRuntime runtime) {
        if (!plugin.getConfig().getBoolean("mob.attack-tags", true)) return Component.empty();

        RpgMobDefinition definition = runtime.definition();
        net.tkgon.mc.iruuRPG.item.RpgItemDefinition weapon = null;
        net.tkgon.mc.iruuRPG.mob.MobEquipmentItem hand = definition.equipment().get(EquipmentSlot.HAND);
        if (hand != null && hand.hasItem()) {
            weapon = itemRegistry.find(hand.itemId()).filter(net.tkgon.mc.iruuRPG.item.RpgItemDefinition::isWeaponLike).orElse(null);
        }
        net.tkgon.mc.iruuRPG.combat.DamageKind kind = weapon == null ? net.tkgon.mc.iruuRPG.combat.DamageKind.PHYSICAL : weapon.damageKind();
        net.tkgon.mc.iruuRPG.combat.AttackType type = weapon == null ? net.tkgon.mc.iruuRPG.combat.AttackType.MELEE : weapon.attackType();
        if (definition.ranged() != null) type = net.tkgon.mc.iruuRPG.combat.AttackType.RANGE;

        Component kindMark = switch (kind) {
            case PHYSICAL -> Component.text("物", NamedTextColor.RED);
            case MAGIC -> Component.text("魔", TextColor.color(0x5599FF));
            case SPECIAL -> Component.text("特", NamedTextColor.LIGHT_PURPLE);
            default -> null;
        };
        Component typeMark = switch (type) {
            case MELEE -> Component.text("近", NamedTextColor.GOLD);
            case RANGE -> Component.text("遠", NamedTextColor.GREEN);
            case DEPLOY -> Component.text("設", NamedTextColor.AQUA);
            default -> null;
        };
        if (kindMark == null && typeMark == null) return Component.empty();

        Component tag = Component.text("[", NamedTextColor.DARK_GRAY);
        if (kindMark != null) tag = tag.append(kindMark);
        if (kindMark != null && typeMark != null) tag = tag.append(Component.text("・", NamedTextColor.GRAY));
        if (typeMark != null) tag = tag.append(typeMark);
        return tag.append(Component.text("] ", NamedTextColor.DARK_GRAY));
    }

    private void updateStatusDisplay(LivingEntity entity) {
        if (!plugin.getConfig().getBoolean("mob.status-display.enabled", true)) {
            removeStatusDisplay(entity.getUniqueId());
            return;
        }

        Component statuses = statusEffectService.statusLine(entity);
        // a mob run by a behavior tree with `debug: true` also shows its state and the nodes that are running
        BehaviorMob bt = behaviors.get(entity.getUniqueId());
        if (bt != null && bt.runner().debug()) {
            Component debug = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(String.join("\n", bt.runner().debugLines()));
            statuses = statuses == null ? debug : debug.append(Component.newline()).append(statuses);
        }
        if (statuses == null) {
            removeStatusDisplay(entity.getUniqueId());
            return;
        }

        TextDisplay display = statusDisplay(entity);
        if (display == null) {
            display = spawnStatusDisplay(entity);
        }

        display.text(statuses);
        display.teleport(statusDisplayLocation(entity));
    }

    private TextDisplay statusDisplay(LivingEntity entity) {
        UUID displayId = statusDisplays.get(entity.getUniqueId());
        if (displayId == null) return null;

        Entity display = Bukkit.getEntity(displayId);
        if (display instanceof TextDisplay textDisplay && display.isValid() && display.getWorld().equals(entity.getWorld())) {
            return textDisplay;
        }

        if (display != null) {
            display.remove();
        }
        statusDisplays.remove(entity.getUniqueId());
        return null;
    }

    private TextDisplay spawnStatusDisplay(LivingEntity entity) {
        Location location = statusDisplayLocation(entity);
        TextDisplay display = entity.getWorld().spawn(location, TextDisplay.class, spawned -> {
            spawned.getPersistentDataContainer().set(statusDisplayKey, PersistentDataType.STRING, entity.getUniqueId().toString());
            spawned.text(Component.empty());
            spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setSeeThrough(true);
            spawned.setShadowed(true);
            spawned.setDefaultBackground(false);
            spawned.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            spawned.setTextOpacity((byte) -1);
            spawned.setLineWidth(Math.max(20, plugin.getConfig().getInt("mob.status-display.line-width", 90)));
            spawned.setViewRange((float) Math.max(1.0, plugin.getConfig().getDouble("mob.status-display.view-range", 24.0)));
            spawned.setBrightness(new Display.Brightness(15, 15));
            // The mob is interpolated by the client; the display is moved every tick, so it is interpolated too
            // (a duration of 0 made it jump while the mob glided).
            spawned.setTeleportDuration(Math.max(1, Math.min(59, plugin.getConfig().getInt("mob.status-display.interpolation-ticks", 3))));
            spawned.setGravity(false);
            spawned.setSilent(true);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
        });

        statusDisplays.put(entity.getUniqueId(), display.getUniqueId());
        return display;
    }

    private Location statusDisplayLocation(LivingEntity entity) {
        double yOffset = plugin.getConfig().getDouble("mob.status-display.y-offset", 0.0);
        double anchorLift = plugin.getConfig().getDouble("mob.status-display.anchor-lift", 0.45);
        // (above the model when the mob wears one, and above its nameplate)
        double top = models == null ? 0.0 : models.height(entity);
        double height = top > 0.0 ? top + 0.6 : entity.getHeight();
        return entity.getLocation().add(0.0, Math.max(0.6, height) + yOffset + anchorLift, 0.0);
    }

    private void removeStatusDisplay(UUID mobId) {
        UUID displayId = statusDisplays.remove(mobId);
        if (displayId == null) return;

        Entity display = Bukkit.getEntity(displayId);
        if (display != null) {
            display.remove();
        }
    }

    private void clearStatusDisplays() {
        for (UUID displayId : new ArrayList<>(statusDisplays.values())) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null) {
                display.remove();
            }
        }
        statusDisplays.clear();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(statusDisplayKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    private Location spawnLocation(Player player) {
        Vector direction = player.getLocation().getDirection();
        direction.setY(0.0);
        if (direction.lengthSquared() <= 1.0E-9) {
            direction = new Vector(0.0, 0.0, 1.0);
        } else {
            direction.normalize();
        }

        double distance = plugin.getConfig().getDouble("mob.spawn-distance", 3.0);
        Location location = player.getLocation().add(direction.multiply(distance));
        location.setPitch(0.0f);
        location.setYaw(player.getLocation().getYaw() + 180.0f);
        return location;
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : Math.max(1.0, entity.getHealth());
    }

    private String format(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.abs(value - Math.rint(value)) < 1.0E-9 ? 0 : 1);
        return format.format(value);
    }

    private record MobRuntime(RpgMobDefinition definition, PlayerProfile profile, Map<UUID, Double> damageByPlayer) {
    }

    private record TargetLock(UUID playerId, long expiresAtMillis) {
    }
}
