package net.tkgon.mc.iruuRPG.classsystem;

import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.Material;

/**
 * A node that can be levelled up without limit; every level costs 1 SP.
 * Each level adds {@code stats} / {@code elementStats} (values may be negative for trade-off nodes).
 */
public record SpendNodeDefinition(
        String id,
        String name,
        Material icon,
        StatSet stats,
        ElementStatSet elementStats
) {

    public SpendNodeDefinition {
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
        icon = icon == null ? Material.PAPER : icon;
        stats = stats == null ? new StatSet() : stats.copy();
        elementStats = elementStats == null ? new ElementStatSet() : elementStats.copy();
    }
}
