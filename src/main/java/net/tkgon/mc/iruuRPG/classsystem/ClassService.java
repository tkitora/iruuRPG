package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        ClassDefinition definition = currentClass(profile).orElse(null);
        StatSet stats = new StatSet();
        if (definition != null) {
            stats.addAll(definition.growth().scaled(Math.max(0, profile.level())));
            for (SpendNodeDefinition node : definition.spendNodes()) {
                int levels = profile.classLevel(node.id());
                if (levels > 0) {
                    stats.add(node.stat(), node.perLevel() * levels);
                }
            }
            List<ClassNodeDefinition> milestones = definition.milestones();
            int unlocked = unlockedMilestoneCount(profile);
            for (int index = 0; index < unlocked; index++) {
                ClassNodeDefinition node = milestones.get(index);
                if (node.effectType() == ClassPassiveEffectType.STATUS) {
                    stats.addAll(node.status());
                }
            }
        }
        profile.classStats().replaceWith(stats);
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
