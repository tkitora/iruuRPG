package net.tkgon.mc.iruuRPG.mob.behavior;

/**
 * A decorator hangs on a node (Unreal's decorators): a condition that decides whether the node may be entered, and that can stop it while it runs ({@code abort: self}) or stop
 * a lower sibling of a selector when it turns true ({@code abort: lower}); or a wrapper that changes what the node does (a loop, a time limit, a cooldown, an inverted result).
 */
abstract class Decorator {

    /** The node is stopped when this stops holding while it runs. */
    boolean abortSelf;
    /** A selector stops the lower child it is running when this (on a higher child) starts to hold. */
    boolean abortLower;
    String label = "";

    boolean canEnter(Ctx c, Node node) {
        return true;
    }

    /** Called every tick the node runs after entering; a status stops the node with that answer. */
    Status during(Ctx c, Node node) {
        return null;
    }

    /** The node finished with {@code status}; the answer it gives instead (RUNNING makes it start over). */
    Status after(Ctx c, Node node, Status status) {
        return status;
    }
}
