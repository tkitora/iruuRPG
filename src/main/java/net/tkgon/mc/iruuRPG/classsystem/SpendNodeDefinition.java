package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Material;

/** A node that can be levelled up without limit; every level costs 1 SP and adds {@code perLevel} of {@code stat}. */
public record SpendNodeDefinition(
        String id,
        String name,
        Material icon,
        StatType stat,
        double perLevel
) {

    public SpendNodeDefinition {
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
        icon = icon == null ? Material.PAPER : icon;
    }
}
