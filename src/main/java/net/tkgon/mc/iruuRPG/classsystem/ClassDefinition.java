package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.Material;

import java.util.List;

/**
 * A class: per-level growth, SP spend nodes, and milestone nodes that unlock for free
 * (in this fixed order) as SP is spent. {@code innate} nodes ("固有0") are active from the moment the class is
 * chosen: passives that cost no SP. {@code catchphrase}, {@code description} and {@code features} are the words shown
 * wherever the class is introduced (choosing it, changing it, its skill tree); {@code comboGuide} is the short list of its
 * normal and heavy attacks per kind of weapon (melee / range / deploy), shown on the skill tree.
 */
public record ClassDefinition(
        String id,
        String name,
        Material icon,
        List<String> description,
        StatSet growth,
        ElementStatSet growthElements,
        List<SpendNodeDefinition> spendNodes,
        List<ClassNodeDefinition> milestones,
        List<ClassNodeDefinition> innate,
        java.util.Map<String, Object> comboStyle,
        String catchphrase,
        List<String> features,
        java.util.Map<String, List<String>> comboGuide
) {

    public ClassDefinition(String id, String name, Material icon, List<String> description, StatSet growth,
                           ElementStatSet growthElements, List<SpendNodeDefinition> spendNodes,
                           List<ClassNodeDefinition> milestones, List<ClassNodeDefinition> innate) {
        this(id, name, icon, description, growth, growthElements, spendNodes, milestones, innate, java.util.Map.of(), "", List.of(), java.util.Map.of());
    }

    public ClassDefinition {
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
        icon = icon == null ? Material.BOOK : icon;
        description = description == null ? List.of() : List.copyOf(description);
        growth = growth == null ? new StatSet() : growth.copy();
        growthElements = growthElements == null ? new ElementStatSet() : growthElements.copy();
        spendNodes = spendNodes == null ? List.of() : List.copyOf(spendNodes);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
        innate = innate == null ? List.of() : List.copyOf(innate);
        comboStyle = comboStyle == null ? java.util.Map.of() : comboStyle;
        catchphrase = catchphrase == null ? "" : catchphrase;
        features = features == null ? List.of() : List.copyOf(features);
        comboGuide = comboGuide == null ? java.util.Map.of() : java.util.Map.copyOf(comboGuide);
    }

    public SpendNodeDefinition spendNode(String nodeId) {
        for (SpendNodeDefinition node : spendNodes) {
            if (node.id().equals(nodeId)) return node;
        }
        return null;
    }
}
