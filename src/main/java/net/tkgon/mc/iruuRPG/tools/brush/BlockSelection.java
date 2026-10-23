package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A set of blocks in one world, kept as packed positions. Inverting does not build the complement (which has no end): it only flips a flag, and {@link #contains} answers the
 * other way round, so inverting costs nothing however big the selection is. What is shown of it is always the edge of the set itself.
 */
final class BlockSelection implements Selection {

    final UUID world;
    final Set<Long> blocks = new HashSet<>();
    boolean inverted;

    BlockSelection(World world) {
        this.world = world.getUID();
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return blocks.contains(SurfaceProbe.pack(x, y, z)) != inverted;
    }

    boolean has(int x, int y, int z) {
        return blocks.contains(SurfaceProbe.pack(x, y, z));
    }

    void add(int x, int y, int z) {
        blocks.add(SurfaceProbe.pack(x, y, z));
    }

    static int unpackX(long key) {
        return (int) (key >> 38);
    }

    static int unpackZ(long key) {
        return (int) ((key << 26) >> 38);
    }

    static int unpackY(long key) {
        return (int) (key & 0xFFFL) - 2048;
    }
}
