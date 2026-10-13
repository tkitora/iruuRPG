package net.tkgon.mc.iruuRPG.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.combat.StatusEffectService;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemFactory;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
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

        Location location = spawnLocation(player);
        LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(location, definition.entityType());
        configure(entity, definition);

        applyEquipment(entity, definition);
        PlayerProfile profile = buildProfile(entity, definition);
        applyHealth(entity, profile);
        storeCurrentHp(entity, profile.currentHp());
        runtimes.put(entity.getUniqueId(), new MobRuntime(definition, profile, new HashMap<>()));
        updateName(entity);
        selectRandomTarget(entity, definition, null, System.currentTimeMillis());
        return Optional.of(entity);
    }

    public void startTargetTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickTargets, 20L, 20L);
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

    public void dropRewards(LivingEntity entity) {
        MobRuntime runtime = runtime(entity);
        if (runtime == null) return;
        if (runtime.definition().drops().isEmpty() && runtime.definition().experience() <= 0L) return;

        double minimumDamage = Math.max(0.0, runtime.profile().maxHp() * 0.05);
        if (minimumDamage <= 0.0) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Map.Entry<UUID, Double> entry : runtime.damageByPlayer().entrySet()) {
            if (entry.getValue() + 1.0E-9 < minimumDamage) continue;

            if (runtime.definition().experience() > 0L) {
                levelService.dropExperience(entity.getLocation(), entry.getKey(), runtime.definition().experience());
            }

            for (MobDropItem drop : runtime.definition().drops()) {
                if (!drop.rolls(random)) continue;

                dropReward(entity, entry.getKey(), drop, random);
            }
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
        runtimes.remove(entity.getUniqueId());
        targetLocks.remove(entity.getUniqueId());
        removeStatusDisplay(entity.getUniqueId());
        statusEffectService.clear(entity);
    }

    public void clear() {
        for (UUID uuid : runtimes.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                statusEffectService.clear(entity);
            }
        }
        runtimes.clear();
        targetLocks.clear();
        clearStatusDisplays();
    }

    public void refreshDisplay(LivingEntity entity) {
        updateName(entity);
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

        if (entity instanceof Ageable ageable) {
            if (definition.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        }
    }

    private void maintainTarget(LivingEntity entity, RpgMobDefinition definition, long now) {
        if (!definition.ai() || !(entity instanceof Mob mob)) {
            targetLocks.remove(entity.getUniqueId());
            return;
        }

        TargetLock lock = targetLocks.get(entity.getUniqueId());
        if (lock != null && lock.expiresAtMillis() > now) {
            Player lockedTarget = Bukkit.getPlayer(lock.playerId());
            if (lockedTarget != null && canTarget(entity, lockedTarget) && inTargetRange(entity, lockedTarget)) {
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

        if (previousTargetId != null && candidates.size() > 1) {
            candidates.removeIf(player -> player.getUniqueId().equals(previousTargetId));
        }

        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        targetLocks.put(entity.getUniqueId(), new TargetLock(target.getUniqueId(), now + TARGET_LOCK_MILLIS));
        mob.setTarget(target);
    }

    private ArrayList<Player> nearbyAdventurePlayers(LivingEntity entity) {
        double range = Math.max(1.0, plugin.getConfig().getDouble("mob.target-range", 24.0));
        double rangeSquared = range * range;
        ArrayList<Player> players = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canTarget(entity, player)) continue;

            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance > rangeSquared) continue;
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

    private boolean inTargetRange(LivingEntity entity, Player player) {
        if (!player.getWorld().equals(entity.getWorld())) return false;

        double range = Math.max(1.0, plugin.getConfig().getDouble("mob.target-range", 24.0));
        return player.getLocation().distanceSquared(entity.getLocation()) <= range * range;
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
        Component name = Component.text(runtime.definition().name(), NamedTextColor.RED)
                .append(Component.text(" Lv" + runtime.definition().level(), NamedTextColor.YELLOW))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(Component.text(format(profile.currentHp()), NamedTextColor.YELLOW))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(format(profile.maxHp()), NamedTextColor.YELLOW))
                .append(Component.text(" HP", NamedTextColor.RED));

        entity.customName(name);
        entity.setCustomNameVisible(true);
        updateStatusDisplay(entity);
    }

    private void updateStatusDisplay(LivingEntity entity) {
        if (!plugin.getConfig().getBoolean("mob.status-display.enabled", true)) {
            removeStatusDisplay(entity.getUniqueId());
            return;
        }

        Component statuses = statusEffectService.statusLine(entity);
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
            spawned.setTeleportDuration(Math.max(0, plugin.getConfig().getInt("mob.status-display.teleport-duration-ticks", 0)));
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
        return entity.getLocation().add(0.0, Math.max(0.6, entity.getHeight()) + yOffset + anchorLift, 0.0);
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
