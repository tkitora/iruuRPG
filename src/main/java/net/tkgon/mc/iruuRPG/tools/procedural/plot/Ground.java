package net.tkgon.mc.iruuRPG.tools.procedural.plot;

/** (t313) The terrain under the site: the row of the highest solid block of a column (in the site's rows: the clicked block's top is row -1). */
@FunctionalInterface
public interface Ground {

    /** No ground found in that column (as far as the terrain was read). */
    int NONE = Integer.MIN_VALUE;

    /** A flat ground: the clicked block's level everywhere. */
    Ground FLAT = (x, z) -> -1;

    int top(int x, int z);

    /** The ground's top, the flat level where none was found. */
    default int at(int x, int z) {
        int top = top(x, z);
        return top == NONE ? -1 : top;
    }
}
