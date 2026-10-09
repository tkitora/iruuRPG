package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.Material;

/** A milestone node: unlocked for free once enough SP has been spent. */
public record ClassNodeDefinition(
        String id,
        String nodeName,
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
        nodeType = nodeType == null ? ClassNodeType.PASSIVE : nodeType;
        effectType = effectType == null ? ClassPassiveEffectType.NONE : effectType;
        status = status == null ? new StatSet() : status.copy();
        skillName = skillName == null ? "" : skillName.trim();
        icon = icon == null ? Material.GRAY_DYE : icon;
    }
}
