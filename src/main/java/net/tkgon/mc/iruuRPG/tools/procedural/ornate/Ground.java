package net.tkgon.mc.iruuRPG.tools.procedural.ornate;

/** The terrain under a tree, for laying its roots on the ground: the height of the top solid block of a column relative to the block the trunk stands in (-1 on flat ground). */
public interface Ground {

    /** No ground known in the column (not loaded, or none near the foot's height). */
    int NONE = Integer.MIN_VALUE;

    int top(int x, int z);
}
