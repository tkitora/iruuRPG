package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.Material;

public record ClassNodeDefinition(
        String id,
        String nodeName,
        int cost,
        ClassNodeType nodeType,
        ClassPassiveEffectType effectType,
        StatSet status,
        String skillName,
        ClassHotKey hotKey,
        Material icon
) {

    public ClassNodeDefinition {
        id = id == null ? "" : id;
        nodeName = nodeName == null || nodeName.isBlank() ? id : nodeName;
        cost = Math.max(0, cost);
        nodeType = nodeType == null ? ClassNodeType.PASSIVE : nodeType;
        effectType = effectType == null ? ClassPassiveEffectType.NONE : effectType;
        status = status == null ? new StatSet() : status.copy();
        skillName = skillName == null ? "" : skillName.trim();
        icon = icon == null ? Material.GRAY_DYE : icon;
    }
}
