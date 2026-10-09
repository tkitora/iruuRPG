package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.Material;

/** A milestone node: unlocked for free once enough SP has been spent. */
public record ClassNodeDefinition(
        String id,
        String nodeName,
        ClassNodeType nodeType,
        ClassPassiveEffectType effectType,
        ClassConditionType condition,
        StatSet status,
        ElementStatSet elementStatus,
        String skillName,
        ClassHotKey hotKey,
        Material icon,
        java.util.List<String> description
) {

    public ClassNodeDefinition {
        id = id == null ? "" : id;
        nodeName = nodeName == null || nodeName.isBlank() ? id : nodeName;
        nodeType = nodeType == null ? ClassNodeType.PASSIVE : nodeType;
        effectType = effectType == null ? ClassPassiveEffectType.NONE : effectType;
        condition = condition == null ? ClassConditionType.ALWAYS : condition;
        status = status == null ? new StatSet() : status.copy();
        elementStatus = elementStatus == null ? new ElementStatSet() : elementStatus.copy();
        skillName = skillName == null ? "" : skillName.trim();
        icon = icon == null ? Material.GRAY_DYE : icon;
        description = description == null ? java.util.List.of() : java.util.List.copyOf(description);
    }
}
