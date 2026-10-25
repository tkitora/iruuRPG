package net.tkgon.mc.iruuRPG.mob.behavior;

/** Work that runs every {@code interval} ticks while the node it hangs on runs (Unreal's services): looking for a target, running a query of the environment. */
abstract class Service {

    int interval = 10;
    private int nextAt;

    final void reset() {
        nextAt = 0;
    }

    final void tick(Ctx c, Node node) {
        if (c.now < nextAt) return;
        nextAt = c.now + Math.max(1, interval);
        run(c, node);
    }

    abstract void run(Ctx c, Node node);

    abstract String label();
}
