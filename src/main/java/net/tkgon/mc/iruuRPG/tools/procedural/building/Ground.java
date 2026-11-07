package net.tkgon.mc.iruuRPG.tools.procedural.building;

/** (t295) The terrain under a building, in its own coordinates: the y of the top solid block of a column (the block over the one clicked is 0), or {@link #NONE}. */
@FunctionalInterface
public interface Ground {

    int NONE = Integer.MIN_VALUE;

    /** Flat ground: its top is the block clicked (-1). */
    Ground FLAT = (x, z) -> -1;

    int top(int x, int z);
}
