package net.tkgon.mc.iruuRPG.tools.brush;

/** A set of blocks the brush may be held to (the selection tool makes one). */
interface Selection {

    boolean contains(int x, int y, int z);
}
