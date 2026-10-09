package net.tkgon.mc.iruuRPG.classsystem;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record SkillTreeShape(
        int size,
        int visibleRows,
        int scrollUpSlot,
        int scrollDownSlot,
        int resetSlot,
        int pointSlot,
        Map<String, SkillTreeNodeShape> nodes
) {

    public SkillTreeShape {
        size = Math.max(9, Math.min(54, size));
        visibleRows = Math.max(1, Math.min(5, visibleRows));
        nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
    }

    public List<SkillTreeNodeShape> orderedNodes() {
        return nodes.values().stream()
                .sorted(Comparator.comparingInt(SkillTreeNodeShape::order))
                .toList();
    }

    public SkillTreeNodeShape node(String id) {
        return nodes.get(id);
    }

    public int maxScrollOffset() {
        return Math.max(0, orderedNodes().size() - visibleRows);
    }
}
