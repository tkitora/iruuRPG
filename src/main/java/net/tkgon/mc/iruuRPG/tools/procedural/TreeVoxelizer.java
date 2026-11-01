package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.HashMap;
import java.util.Map;

/**
 * The blocks of a tree while it is grown: the part of every block by {@link #pack}ed position. The tree algorithms ({@link CanopyTree}, {@link TieredTree},
 * {@link PalmTree}) put their blocks the way the scripts they come from do: a later block takes the place of an earlier one ({@link #set}); the leaves take a shade
 * of the leaf gradient by their height in their clump ({@link #leaf}). Coordinates: x and z from the block the tree stands on, y up from it (0 is that block).
 * There is a limit on the blocks, so a setting far too big stops ({@link TooBig}) instead of filling the memory.
 */
final class TreeVoxelizer {

    /** More blocks than this and the tree is given up (the placing limit of the config is far below it). */
    static final int LIMIT = 1_500_000;

    /** A tree with more blocks than {@link #LIMIT}. */
    static final class TooBig extends RuntimeException {
        TooBig() {
            super("more than " + LIMIT + " blocks");
        }
    }

    private final Map<Long, TreePart> blocks = new HashMap<>();
    private final int shades;

    /** {@code shades}: the blocks of the leaf gradient (1 = no gradient). */
    TreeVoxelizer(int shades) {
        this.shades = Math.max(1, shades);
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x + 2048) & 0xFFFFL) | (((long) (y + 2048) & 0xFFFFL) << 16) | (((long) (z + 2048) & 0xFFFFL) << 32);
    }

    public static int unpackX(long key) {
        return (int) (key & 0xFFFFL) - 2048;
    }

    public static int unpackY(long key) {
        return (int) ((key >> 16) & 0xFFFFL) - 2048;
    }

    public static int unpackZ(long key) {
        return (int) ((key >> 32) & 0xFFFFL) - 2048;
    }

    Map<Long, TreePart> blocks() {
        return blocks;
    }

    TreePart get(int x, int y, int z) {
        return blocks.get(pack(x, y, z));
    }

    /** Puts {@code part} at the block (what was there goes). */
    void set(int x, int y, int z, TreePart part) {
        blocks.put(pack(x, y, z), part);
        if (blocks.size() > LIMIT) throw new TooBig();
    }

    /** A leaf at the block, of the shade for {@code height} (0 = the bottom of its clump, 1 = the top); {@code jitter} moves the border a little. */
    void leaf(int x, int y, int z, double height, double jitter) {
        set(x, y, z, TreePart.leafAt(height, shades, jitter));
    }

    int shades() {
        return shades;
    }

    /** Drops what is outside the box: {@code height} up, 6 blocks down and {@code half} each way from the base. */
    void clip(int height, double half) {
        blocks.keySet().removeIf(key -> {
            int y = unpackY(key);
            return y < -6 || y >= height || Math.abs(unpackX(key)) > half || Math.abs(unpackZ(key)) > half;
        });
    }

    /** A small jitter in -1..1 for the border of the leaf shades: the same block always gets the same. */
    static double jitter(int x, int y, int z) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return ((h & 0xFFFFL) / 32767.5) - 1.0;
    }
}
