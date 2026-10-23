package net.tkgon.mc.iruuRPG.tools.tree;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The terrain round the foot of a tree, read once on the main thread so the (off-thread) generation can lay the roots on it: for every column within {@code radius} of the
 * base, the height of the top solid block (not leaves or logs, which are plants or an earlier tree) relative to the base block, or {@link #NONE} when there is none within
 * the search (from 4 blocks above the base to 8 below it) or the chunk is not loaded.
 */
interface TreeGround {

    int NONE = Integer.MIN_VALUE;

    /** Height of the top solid block of the column ({@code x}, {@code z}) relative to the base block (the block the trunk stands in is 0; the ground under it is -1). */
    int top(int x, int z);

    /** A snapshot of the terrain: {@code radius} blocks each way from the base. */
    static TreeGround sample(Location base, int radius) {
        World world = base.getWorld();
        int size = radius * 2 + 1;
        int[] heights = new int[size * size];
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                heights[(dx + radius) * size + (dz + radius)] = column(world, base.getBlockX() + dx, base.getBlockY(), base.getBlockZ() + dz);
            }
        }
        return (x, z) -> Math.abs(x) > radius || Math.abs(z) > radius ? NONE : heights[(x + radius) * size + (z + radius)];
    }

    private static int column(World world, int x, int baseY, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return NONE;
        for (int dy = 4; dy >= -8; dy--) {
            int y = baseY + dy;
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (!type.isSolid() || block.isReplaceable() || Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) continue;
            return dy;
        }
        return NONE;
    }
}
