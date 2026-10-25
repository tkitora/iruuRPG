package net.tkgon.mc.iruuRPG.mob.behavior;

import java.util.ArrayList;
import java.util.List;

/**
 * A node of a behavior tree. It can carry decorators (conditions and wrappers: whether it may run, loops, time limits, cooldowns) and services (work done every so often
 * while it runs). {@link #run} is the whole life of a node: entering (the decorators say whether it may), the services, the work of the node itself ({@link #tickNode}) and the
 * decorators' say on the result. A node that answers RUNNING is run again next tick, from where it was.
 */
abstract class Node {

    final String type;
    String name;
    final List<Decorator> decorators = new ArrayList<>();
    final List<Service> services = new ArrayList<>();
    boolean active;
    int startedTick;

    Node(String type) {
        this.type = type;
    }

    /** The name shown in the debug line. */
    String label() {
        return name != null && !name.isBlank() ? name : type;
    }

    final Status run(Ctx c) {
        if (!active) {
            for (Decorator decorator : decorators) {
                if (!decorator.canEnter(c, this)) return Status.FAILURE;
            }
            active = true;
            startedTick = c.now;
            for (Service service : services) service.reset();
            for (Decorator decorator : decorators) decorator.entered(c, this);
            onEnter(c);
        } else {
            for (Decorator decorator : decorators) {
                if (decorator.abortSelf && !decorator.canEnter(c, this)) {
                    abort(c);
                    return Status.FAILURE;
                }
            }
            for (Decorator decorator : decorators) {
                Status verdict = decorator.during(c, this);
                if (verdict != null) {
                    abort(c);
                    return verdict;
                }
            }
        }
        for (Service service : services) service.tick(c, this);
        Status status = tickNode(c);
        if (status == Status.RUNNING) return status;
        Status out = status;
        for (Decorator decorator : decorators) out = decorator.after(c, this, out);
        if (out == Status.RUNNING) {
            // a loop: the node starts over
            abortChildren(c);
            startedTick = c.now;
            onEnter(c);
            return Status.RUNNING;
        }
        active = false;
        for (Decorator decorator : decorators) decorator.left(c, this);
        onFinish(c);
        return out;
    }

    /** Stops the node (and what runs under it) where it is. */
    final void abort(Ctx c) {
        if (!active) return;
        active = false;
        abortChildren(c);
        onAbort(c);
        for (Decorator decorator : decorators) decorator.left(c, this);
    }

    /** Whether the node may be entered now (the conditions of its decorators), without side effects. */
    final boolean wouldEnter(Ctx c) {
        for (Decorator decorator : decorators) if (!decorator.canEnter(c, this)) return false;
        return true;
    }

    abstract Status tickNode(Ctx c);

    void onEnter(Ctx c) {
    }

    void onAbort(Ctx c) {
    }

    void onFinish(Ctx c) {
    }

    void abortChildren(Ctx c) {
    }

    /** Adds the names of the nodes that are running, from this one down, to {@code out}. */
    void path(List<String> out) {
        out.add(label());
    }
}
