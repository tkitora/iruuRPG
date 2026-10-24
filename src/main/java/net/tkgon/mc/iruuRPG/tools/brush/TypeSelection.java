package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.Set;

/**
 * The selection used as a block mask: it lets the brush work on any block of the kinds that were selected (wherever the brush is), not only on the blocks that were
 * selected. Inverted, it lets pass every block that is none of those kinds.
 */
final class TypeSelection implements Selection {

    private final World world;
    private final Set<Material> kinds;
    private final boolean inverted;

    TypeSelection(World world, Set<Material> kinds, boolean inverted) {
        this.world = world;
        this.kinds = kinds;
        this.inverted = inverted;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
        Material type = world.getType(x, y, z);
        Material base = BlockColors.baseOf(type);
        return (kinds.contains(type) || (base != null && kinds.contains(base))) != inverted;
    }
}
