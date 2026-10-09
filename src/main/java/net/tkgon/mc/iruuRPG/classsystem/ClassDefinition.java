package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.Material;

import java.util.List;

/**
 * A class: per-level growth, SP spend nodes, and milestone nodes that unlock for free
 * (in this fixed order) as SP is spent.
 */
public record ClassDefinition(
        String id,
        String name,
        Material icon,
        StatSet growth,
        ElementStatSet growthElements,
        List<SpendNodeDefinition> spendNodes,
        List<ClassNodeDefinition> milestones
) {

    public ClassDefinition {
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
        icon = icon == null ? Material.BOOK : icon;
        growth = growth == null ? new StatSet() : growth.copy();
        growthElements = growthElements == null ? new ElementStatSet() : growthElements.copy();
        spendNodes = spendNodes == null ? List.of() : List.copyOf(spendNodes);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
    }

    public SpendNodeDefinition spendNode(String nodeId) {
        for (SpendNodeDefinition node : spendNodes) {
            if (node.id().equals(nodeId)) return node;
        }
        return null;
    }
}
