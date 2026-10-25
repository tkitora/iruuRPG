package net.tkgon.mc.iruuRPG.mob.behavior;

import java.util.ArrayList;
import java.util.List;

/** The composite nodes: they run their children (selector, sequence, random selector, parallel). */
final class Composites {

    private Composites() {
    }

    abstract static class Composite extends Node {
        final List<Node> children = new ArrayList<>();
        int current = -1;

        Composite(String type) {
            super(type);
        }

        @Override
        void onEnter(Ctx c) {
            current = -1;
        }

        @Override
        void abortChildren(Ctx c) {
            if (current >= 0 && current < children.size()) children.get(current).abort(c);
            current = -1;
        }

        @Override
        void path(List<String> out) {
            out.add(label());
            if (current >= 0 && current < children.size() && children.get(current).active) children.get(current).path(out);
        }
    }

    /** Tries its children in order until one does not fail: succeeds with it, fails when all fail. A higher child with an {@code abort: lower} decorator takes over when it starts to hold. */
    static final class Selector extends Composite {
        Selector() {
            super("selector");
        }

        @Override
        Status tickNode(Ctx c) {
            if (current > 0) {
                for (int index = 0; index < current; index++) {
                    Node higher = children.get(index);
                    boolean watches = false;
                    for (Decorator decorator : higher.decorators) if (decorator.abortLower) watches = true;
                    if (watches && higher.wouldEnter(c)) {
                        children.get(current).abort(c);
                        current = -1;
                        break;
                    }
                }
            }
            for (int index = Math.max(0, current); index < children.size(); index++) {
                Status status = children.get(index).run(c);
                if (status == Status.RUNNING) {
                    current = index;
                    return Status.RUNNING;
                }
                if (status == Status.SUCCESS) {
                    current = -1;
                    return Status.SUCCESS;
                }
            }
            current = -1;
            return Status.FAILURE;
        }
    }

    /** Runs its children in order; fails at the first that fails, succeeds when all did. */
    static final class Sequence extends Composite {
        Sequence() {
            super("sequence");
        }

        @Override
        Status tickNode(Ctx c) {
            for (int index = Math.max(0, current); index < children.size(); index++) {
                Status status = children.get(index).run(c);
                if (status == Status.RUNNING) {
                    current = index;
                    return Status.RUNNING;
                }
                if (status == Status.FAILURE) {
                    current = -1;
                    return Status.FAILURE;
                }
            }
            current = -1;
            return Status.SUCCESS;
        }
    }

    /** Picks one child at random each time it is entered and runs it. */
    static final class RandomSelector extends Composite {
        RandomSelector() {
            super("random");
        }

        @Override
        void onEnter(Ctx c) {
            current = children.isEmpty() ? -1 : c.random.nextInt(children.size());
        }

        @Override
        Status tickNode(Ctx c) {
            if (current < 0) return Status.FAILURE;
            return children.get(current).run(c);
        }
    }

    /** Runs its first child (the main task) and its second (the background) at the same time; ends with the main task, stopping the background. */
    static final class Parallel extends Composite {
        Parallel() {
            super("parallel");
        }

        @Override
        Status tickNode(Ctx c) {
            if (children.isEmpty()) return Status.FAILURE;
            Status main = children.get(0).run(c);
            if (children.size() > 1) {
                Status background = children.get(1).run(c);
                if (main != Status.RUNNING) {
                    children.get(1).abort(c);
                    return main;
                }
                if (background != Status.RUNNING) {
                    // the background starts over while the main task goes on
                    children.get(1).abort(c);
                }
            }
            current = 0;
            return main;
        }

        @Override
        void abortChildren(Ctx c) {
            for (Node child : children) child.abort(c);
            current = -1;
        }

        @Override
        void path(List<String> out) {
            out.add(label());
            if (!children.isEmpty() && children.get(0).active) children.get(0).path(out);
            if (children.size() > 1 && children.get(1).active) {
                List<String> background = new ArrayList<>();
                children.get(1).path(background);
                out.add("+" + String.join("›", background));
            }
        }
    }
}
