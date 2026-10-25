package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Which blocks of a box of the world are not air, read once on the main thread so that the (off-thread) making of a web can tell where a thread would have to overwrite
 * something. Outside the box, and in a chunk that is not loaded, every block counts as taken: a web is only ever put in the air it has seen.
 */
final class WebTerrain {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final boolean[] taken;

    private WebTerrain(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, boolean[] taken) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.taken = taken;
    }

    /** The number of blocks in the box from {@code min} to {@code max} (both included). */
    static long volume(int[] min, int[] max) {
        return (long) (max[0] - min[0] + 1) * (max[1] - min[1] + 1) * (max[2] - min[2] + 1);
    }

    static WebTerrain sample(World world, int[] min, int[] max) {
        int sx = max[0] - min[0] + 1;
        int sy = max[1] - min[1] + 1;
        int sz = max[2] - min[2] + 1;
        boolean[] taken = new boolean[sx * sy * sz];
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                int wx = min[0] + x;
                int wz = min[2] + z;
                boolean loaded = world.isChunkLoaded(wx >> 4, wz >> 4);
                for (int y = 0; y < sy; y++) {
                    int wy = min[1] + y;
                    boolean solid;
                    if (!loaded || wy < world.getMinHeight() || wy >= world.getMaxHeight()) {
                        solid = true;
                    } else {
                        Block block = world.getBlockAt(wx, wy, wz);
                        solid = !block.getType().isAir();
                    }
                    taken[(x * sy + y) * sz + z] = solid;
                }
            }
        }
        return new WebTerrain(min[0], min[1], min[2], sx, sy, sz, taken);
    }

    /** A terrain made from a rule (for trying the web makers without a world): {@code taken} says whether a block of the box is not air. */
    static WebTerrain of(int[] min, int[] max, java.util.function.Predicate<int[]> taken) {
        int sx = max[0] - min[0] + 1;
        int sy = max[1] - min[1] + 1;
        int sz = max[2] - min[2] + 1;
        boolean[] cells = new boolean[sx * sy * sz];
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) cells[(x * sy + y) * sz + z] = taken.test(new int[]{min[0] + x, min[1] + y, min[2] + z});
        return new WebTerrain(min[0], min[1], min[2], sx, sy, sz, cells);
    }

    /** True when the block is not air (or is outside what was read). */
    boolean blocked(int x, int y, int z) {
        int dx = x - minX;
        int dy = y - minY;
        int dz = z - minZ;
        if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ) return true;
        return taken[(dx * sizeY + dy) * sizeZ + dz];
    }
}
