package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Class rules:
 * <ul>
 *   <li>SP earned = floor(level / levels-per-point), capped at max-points.</li>
 *   <li>Every spend-node level costs 1 SP and has no upper limit.</li>
 *   <li>One milestone node unlocks for free per milestone-interval SP spent, in the class's fixed order.</li>
 * </ul>
 */
public final class ClassService {

    private static final int DEFAULT_LEVELS_PER_POINT = 5;
    private static final int DEFAULT_MAX_POINTS = 40;
    private static final int DEFAULT_MILESTONE_INTERVAL = 5;

    private final JavaPlugin plugin;
    private final ClassRegistry classRegistry;

    public ClassService(JavaPlugin plugin, ClassRegistry classRegistry) {
        this.plugin = plugin;
        this.classRegistry = classRegistry;
    }

    public Optional<ClassDefinition> currentClass(PlayerProfile profile) {
        ensureClassId(profile);
        return classRegistry.find(profile.classId());
    }

    public boolean needsSelection(PlayerProfile profile) {
        return !profile.classChosen() && !classRegistry.all().isEmpty();
    }

    public Collection<ClassDefinition> selectableClasses() {
        return classRegistry.all().values();
    }

    /**
     * Sets the player's class. Changing to a different class clears the invested levels
     * (their SP is returned automatically because SP is derived from level).
     */
    public boolean select(PlayerProfile profile, String classId) {
        ClassDefinition target = classRegistry.find(classId).orElse(null);
        if (target == null) return false;

        if (!target.id().equals(profile.classId())) {
            profile.classLevels().clear();
            profile.setClassId(target.id());
        }
        profile.setClassChosen(true);
        updateActiveBindings(profile);
        return true;
    }

    public void ensureProfile(PlayerProfile profile) {
        ensureClassId(profile);
        trimOverspent(profile);
        updateActiveBindings(profile);
    }

    // ---- SP rules -------------------------------------------------------

    public int earnedPoints(PlayerProfile profile) {
        int perPoint = Math.max(1, config().getInt("class-skill-points.levels-per-point", DEFAULT_LEVELS_PER_POINT));
        int max = Math.max(0, config().getInt("class-skill-points.max-points", DEFAULT_MAX_POINTS));
        return Math.min(max, Math.max(0, profile.level()) / perPoint);
    }

    public int spentPoints(PlayerProfile profile) {
        return profile.spentSkillPoints();
    }

    public int availablePoints(PlayerProfile profile) {
        return Math.max(0, earnedPoints(profile) - spentPoints(profile));
    }

    public int milestoneInterval() {
        return Math.max(1, config().getInt("class-skill-points.milestone-interval", DEFAULT_MILESTONE_INTERVAL));
    }

    public int unlockedMilestoneCount(PlayerProfile profile) {
        ClassDefinition definition = currentClass(profile).orElse(null);
        if (definition == null) return 0;
        return Math.min(definition.milestones().size(), spentPoints(profile) / milestoneInterval());
    }

    public boolean isMilestoneUnlocked(PlayerProfile profile, int index) {
        return index >= 0 && index < unlockedMilestoneCount(profile);
    }

    // ---- actions --------------------------------------------------------

    public boolean levelUp(PlayerProfile profile, String nodeId) {
        ensureProfile(profile);
        ClassDefinition definition = currentClass(profile).orElse(null);
        if (definition == null || definition.spendNode(nodeId) == null) return false;
        if (availablePoints(profile) < 1) return false;

        profile.classLevels().merge(nodeId, 1, Integer::sum);
        updateActiveBindings(profile);
        return true;
    }

    public boolean levelDown(PlayerProfile profile, String nodeId) {
        ensureProfile(profile);
        int current = profile.classLevel(nodeId);
        if (current <= 0) return false;

        if (current == 1) {
            profile.classLevels().remove(nodeId);
        } else {
            profile.classLevels().put(nodeId, current - 1);
        }
        updateActiveBindings(profile);
        return true;
    }

    public void reset(PlayerProfile profile) {
        ensureClassId(profile);
        profile.classLevels().clear();
        updateActiveBindings(profile);
    }

    // ---- stats / skills -------------------------------------------------

    public void applyClassStats(Player player, PlayerProfile profile) {
        ensureProfile(profile);
        int level = Math.max(0, profile.level());
        StatSet stats = commonGrowth().scaled(level);
        ElementStatSet elements = new ElementStatSet();

        ClassDefinition definition = currentClass(profile).orElse(null);
        if (definition != null) {
            stats.addAll(definition.growth().scaled(level));
            elements.addAll(definition.growthElements().scaled(level));
            for (SpendNodeDefinition node : definition.spendNodes()) {
                int levels = profile.classLevel(node.id());
                if (levels <= 0) continue;

                stats.addAll(node.stats().scaled(levels));
                elements.addAll(node.elementStats().scaled(levels));
            }
            List<ClassNodeDefinition> milestones = definition.milestones();
            int unlocked = unlockedMilestoneCount(profile);
            for (int index = 0; index < unlocked; index++) {
                ClassNodeDefinition node = milestones.get(index);
                if (node.effectType() != ClassPassiveEffectType.STATUS) continue;
                if (!conditionActive(player, profile, node.condition())) continue;

                stats.addAll(node.status());
                elements.addAll(node.elementStatus());
            }
        }
        profile.classStats().replaceWith(stats);
        profile.classElementStats().replaceWith(elements);
    }

    /** Level growth shared by every class (config: level-growth). */
    private StatSet commonGrowth() {
        StatSet growth = new StatSet();
        ConfigurationSection section = config().getConfigurationSection("level-growth");
        if (section == null) return growth;

        for (String key : section.getKeys(false)) {
            StatType.fromConfigKey(key).ifPresent(type -> growth.set(type, section.getDouble(key, 0.0)));
        }
        return growth;
    }

    // ---- conditional passives -------------------------------------------

    private final Map<UUID, Location> lastLocations = new HashMap<>();
    private final Map<UUID, Integer> stillSamples = new HashMap<>();
    private final Map<UUID, Integer> conditionMasks = new HashMap<>();

    public boolean conditionActive(Player player, PlayerProfile profile, ClassConditionType condition) {
        return switch (condition) {
            case ALWAYS -> true;
            case HP_BELOW_HALF -> profile.currentHp() < profile.maxHp() * 0.5;
            case STATIONARY -> player != null && stillSamples.getOrDefault(player.getUniqueId(), 0) >= stationarySamples();
        };
    }

    private int stationarySamples() {
        return Math.max(1, config().getInt("class-conditions.stationary-samples", 2));
    }

    /**
     * Polls conditional passives (HP below half, standing still) and recalculates stats
     * when a player's condition state changes.
     */
    public void startConditionTask(EquipmentService equipmentService, PlayerBars playerBars) {
        long period = Math.max(1L, config().getLong("class-conditions.check-ticks", 5L));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                trackMovement(player);
                int mask = conditionMask(player, equipmentService);
                Integer previous = conditionMasks.put(player.getUniqueId(), mask);
                if (previous != null && previous == mask) continue;

                PlayerProfile profile = equipmentService.recalculate(player);
                playerBars.sync(player, profile);
            }
            conditionMasks.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        }, period, period);
    }

    private void trackMovement(Player player) {
        Location now = player.getLocation();
        Location before = lastLocations.put(player.getUniqueId(), now);
        boolean still = before != null && before.getWorld() == now.getWorld()
                && before.distanceSquared(now) < 1.0E-4
                && !player.isInsideVehicle();
        stillSamples.put(player.getUniqueId(), still ? stillSamples.getOrDefault(player.getUniqueId(), 0) + 1 : 0);
    }

    private int conditionMask(Player player, EquipmentService equipmentService) {
        PlayerProfile profile = equipmentService.profile(player);
        if (profile == null) return 0;

        int mask = 0;
        for (ClassConditionType type : ClassConditionType.values()) {
            if (type != ClassConditionType.ALWAYS && conditionActive(player, profile, type)) {
                mask |= 1 << type.ordinal();
            }
        }
        return mask;
    }

    public String activeSkill(PlayerProfile profile, ClassHotKey hotKey) {
        ensureProfile(profile);
        if (hotKey == null) return null;
        return profile.activeClassSkills().get(hotKey.key());
    }

    /** True when a milestone that grants {@code skillName} (passive skill effect) is unlocked. */
    public boolean hasSkill(PlayerProfile profile, String skillName) {
        ClassDefinition definition = currentClass(profile).orElse(null);
        if (definition == null || skillName == null) return false;

        int unlocked = unlockedMilestoneCount(profile);
        for (int index = 0; index < unlocked; index++) {
            ClassNodeDefinition node = definition.milestones().get(index);
            if (node.effectType() == ClassPassiveEffectType.SKILL && node.skillName().equalsIgnoreCase(skillName)) {
                return true;
            }
        }
        return false;
    }

    private void updateActiveBindings(PlayerProfile profile) {
        profile.activeClassSkills().clear();
        ClassDefinition definition = classRegistry.find(profile.classId()).orElse(null);
        if (definition == null) return;

        int unlocked = unlockedMilestoneCount(profile);
        for (int index = 0; index < unlocked; index++) {
            ClassNodeDefinition node = definition.milestones().get(index);
            if (node.nodeType() != ClassNodeType.ACTIVE || node.hotKey() == null || node.skillName().isBlank()) continue;
            profile.activeClassSkills().put(node.hotKey().key(), node.skillName());
        }
    }

    /** If the level dropped below what was spent (admin command, reset), remove levels until it fits. */
    private void trimOverspent(PlayerProfile profile) {
        int excess = spentPoints(profile) - earnedPoints(profile);
        if (excess <= 0) return;

        Map<String, Integer> levels = profile.classLevels();
        while (excess > 0 && !levels.isEmpty()) {
            String largest = null;
            for (Map.Entry<String, Integer> entry : levels.entrySet()) {
                if (largest == null || entry.getValue() > levels.get(largest)) largest = entry.getKey();
            }
            int value = levels.get(largest);
            if (value <= 1) levels.remove(largest); else levels.put(largest, value - 1);
            excess--;
        }
    }

    private void ensureClassId(PlayerProfile profile) {
        if ((profile.classId() == null || profile.classId().isBlank()) && classRegistry.first().isPresent()) {
            profile.setClassId(classRegistry.first().get().id());
        }
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }
}
