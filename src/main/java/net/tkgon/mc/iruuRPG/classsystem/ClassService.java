package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ClassService {

    private final ClassRegistry classRegistry;
    private final SkillTreeShapeRegistry shapeRegistry;

    public ClassService(ClassRegistry classRegistry, SkillTreeShapeRegistry shapeRegistry) {
        this.classRegistry = classRegistry;
        this.shapeRegistry = shapeRegistry;
    }

    public Optional<ClassDefinition> currentClass(PlayerProfile profile) {
        ensureClassId(profile);
        return classRegistry.find(profile.classId());
    }

    public void ensureProfile(PlayerProfile profile) {
        ensureClassId(profile);
        syncEarnedSkillPoints(profile);
        updateActiveBindings(profile);
    }

    public boolean acquire(Player player, PlayerProfile profile, String nodeId) {
        ensureProfile(profile);
        ClassDefinition definition = currentClass(profile).orElse(null);
        if (definition == null) return false;

        ClassNodeDefinition node = definition.node(nodeId);
        if (node == null || profile.classNodes().contains(nodeId)) return false;
        if (profile.skillPoints() < node.cost()) return false;
        if (!parentAcquired(profile, nodeId)) return false;

        profile.classNodes().add(nodeId);
        profile.setSkillPoints(profile.skillPoints() - node.cost());
        profile.setSpentSkillPoints(profile.spentSkillPoints() + node.cost());
        updateActiveBindings(profile);
        return true;
    }

    public boolean release(Player player, PlayerProfile profile, String nodeId) {
        ensureProfile(profile);
        ClassDefinition definition = currentClass(profile).orElse(null);
        if (definition == null || !profile.classNodes().contains(nodeId)) return false;

        List<String> removed = acquiredDescendants(profile, nodeId);
        removed.sort(Comparator.comparingInt(this::order).reversed());
        int refund = 0;
        for (String removedId : removed) {
            ClassNodeDefinition node = definition.node(removedId);
            if (node == null) continue;

            profile.classNodes().remove(removedId);
            refund += node.cost();
        }

        profile.setSpentSkillPoints(profile.spentSkillPoints() - refund);
        profile.setSkillPoints(profile.skillPoints() + refund);
        updateActiveBindings(profile);
        return true;
    }

    public void reset(PlayerProfile profile) {
        ensureProfile(profile);
        ClassDefinition definition = currentClass(profile).orElse(null);
        if (definition == null) return;

        int refund = 0;
        for (String nodeId : new ArrayList<>(profile.classNodes())) {
            ClassNodeDefinition node = definition.node(nodeId);
            if (node != null) {
                refund += node.cost();
            }
        }
        profile.classNodes().clear();
        profile.setSpentSkillPoints(profile.spentSkillPoints() - refund);
        profile.setSkillPoints(profile.skillPoints() + refund);
        updateActiveBindings(profile);
    }

    public boolean canAcquire(PlayerProfile profile, String nodeId) {
        ensureProfile(profile);
        ClassDefinition definition = currentClass(profile).orElse(null);
        if (definition == null) return false;

        ClassNodeDefinition node = definition.node(nodeId);
        return node != null
                && !profile.classNodes().contains(nodeId)
                && profile.skillPoints() >= node.cost()
                && parentAcquired(profile, nodeId);
    }

    public void applyClassStats(Player player, PlayerProfile profile) {
        ensureProfile(profile);
        ClassDefinition definition = currentClass(profile).orElse(null);
        StatSet stats = new StatSet();
        if (definition != null) {
            for (String nodeId : profile.classNodes()) {
                ClassNodeDefinition node = definition.node(nodeId);
                if (node == null) continue;
                if (node.nodeType() != ClassNodeType.PASSIVE || node.effectType() != ClassPassiveEffectType.STATUS) continue;

                stats.addAll(node.status());
            }
        }
        profile.classStats().replaceWith(stats);
    }

    public String activeSkill(PlayerProfile profile, ClassHotKey hotKey) {
        ensureProfile(profile);
        if (hotKey == null) return null;
        return profile.activeClassSkills().get(hotKey.key());
    }

    public SkillTreeShape shape() {
        return shapeRegistry.shape();
    }

    private boolean parentAcquired(PlayerProfile profile, String nodeId) {
        SkillTreeNodeShape shape = shapeRegistry.shape().node(nodeId);
        if (shape == null || shape.parentId() == null) return true;
        return profile.classNodes().contains(shape.parentId());
    }

    private List<String> acquiredDescendants(PlayerProfile profile, String nodeId) {
        Set<String> result = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(nodeId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!result.add(current)) continue;

            for (SkillTreeNodeShape shape : shapeRegistry.shape().nodes().values()) {
                if (!current.equals(shape.parentId())) continue;
                if (profile.classNodes().contains(shape.id())) {
                    queue.addLast(shape.id());
                }
            }
        }
        return new ArrayList<>(result);
    }

    private void updateActiveBindings(PlayerProfile profile) {
        profile.activeClassSkills().clear();
        ClassDefinition definition = classRegistry.find(profile.classId()).orElse(null);
        if (definition == null) return;

        profile.classNodes().stream()
                .sorted(Comparator.comparingInt(this::order))
                .forEach(nodeId -> {
                    ClassNodeDefinition node = definition.node(nodeId);
                    if (node == null) return;
                    if (node.nodeType() != ClassNodeType.ACTIVE || node.hotKey() == null || node.skillName().isBlank()) return;
                    profile.activeClassSkills().put(node.hotKey().key(), node.skillName());
                });
    }

    private void syncEarnedSkillPoints(PlayerProfile profile) {
        int earned = Math.max(0, profile.level() - 1);
        int currentTotal = profile.skillPoints() + profile.spentSkillPoints();
        if (currentTotal < earned) {
            profile.addSkillPoints(earned - currentTotal);
        }
    }

    private void ensureClassId(PlayerProfile profile) {
        if ((profile.classId() == null || profile.classId().isBlank()) && classRegistry.first().isPresent()) {
            profile.setClassId(classRegistry.first().get().id());
        }
    }

    private int order(String nodeId) {
        SkillTreeNodeShape shape = shapeRegistry.shape().node(nodeId);
        return shape == null ? Integer.MAX_VALUE : shape.order();
    }
}
