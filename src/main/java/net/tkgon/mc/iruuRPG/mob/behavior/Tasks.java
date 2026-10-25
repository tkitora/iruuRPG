package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** The task nodes (the leaves): move, wait, hit, shoot, use a skill, face, write a value, change state. */
final class Tasks {

    private Tasks() {
    }

    abstract static class Task extends Node {
        Task(String type) {
            super(type);
        }
    }

    /**
     * Walks to a place: the target (followed as it moves), the home, a place written in the blackboard ({@code key}) or the place a query chooses ({@code eqs}, chosen once when
     * the task starts). Succeeds within {@code acceptance} blocks of it; fails when there is no path, when it stops making progress for 2 s or after {@code timeout}.
     */
    static final class MoveTo extends Task {
        private final String mode;
        private final String key;
        private final String eqs;
        private final double speed;
        private final double acceptance;
        private final int timeout;
        private Location place;
        private int lastPath;
        private int failedPaths;
        private int checkAt;
        private Location checkFrom;

        MoveTo(String mode, String key, String eqs, double speed, double acceptance, int timeout) {
            super("move-to");
            this.mode = mode;
            this.key = key;
            this.eqs = eqs;
            this.speed = speed;
            this.acceptance = acceptance;
            this.timeout = timeout;
        }

        /** The query this task uses, or null. */
        String queryName() {
            return mode.equals("eqs") ? eqs : null;
        }

        @Override
        String label() {
            if (name != null && !name.isBlank()) return name;
            return "move-to(" + (mode.equals("eqs") ? "eqs:" + eqs : mode.equals("key") ? "@" + key : mode) + ")";
        }

        @Override
        void onEnter(Ctx c) {
            place = null;
            lastPath = -100;
            failedPaths = 0;
            checkAt = c.now + 40;
            checkFrom = c.host.mob().getLocation();
            switch (mode) {
                case "home" -> place = c.host.home();
                case "key" -> place = c.bb.location(key);
                case "eqs" -> {
                    EqsQuery query = c.queries.get(eqs);
                    place = query == null ? null : query.run(c);
                }
                default -> {
                }
            }
        }

        @Override
        void onAbort(Ctx c) {
            c.host.stopMoving();
        }

        @Override
        Status tickNode(Ctx c) {
            Location goal = mode.equals("target") ? (c.bb.target == null ? null : c.bb.target.getLocation()) : place;
            if (goal == null || goal.getWorld() == null) return Status.FAILURE;
            Location here = c.host.mob().getLocation();
            if (here.getWorld() != goal.getWorld()) return Status.FAILURE;
            if (here.distance(goal) <= acceptance) {
                c.host.stopMoving();
                return Status.SUCCESS;
            }
            if (c.now - startedTick > timeout) {
                c.host.stopMoving();
                return Status.FAILURE;
            }
            if (c.now - lastPath >= 8 || !c.host.hasPath()) {
                lastPath = c.now;
                if (!c.host.moveTo(goal, speed)) {
                    if (++failedPaths >= 3) return Status.FAILURE;
                } else {
                    failedPaths = 0;
                }
            }
            if (c.now >= checkAt) {
                if (checkFrom != null && checkFrom.getWorld() == here.getWorld() && checkFrom.distance(here) < 0.6) {
                    c.host.stopMoving();
                    return Status.FAILURE;
                }
                checkFrom = here;
                checkAt = c.now + 40;
            }
            return Status.RUNNING;
        }
    }

    /** Waits {@code seconds} (plus or minus up to {@code deviation}), standing still. */
    static final class Wait extends Task {
        private final int ticks;
        private final int deviation;
        private int endAt;

        Wait(int ticks, int deviation) {
            super("wait");
            this.ticks = ticks;
            this.deviation = deviation;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "wait " + ticks / 20.0 + "s";
        }

        @Override
        void onEnter(Ctx c) {
            int extra = deviation <= 0 ? 0 : c.random.nextInt(deviation * 2 + 1) - deviation;
            endAt = c.now + Math.max(1, ticks + extra);
            c.host.stopMoving();
        }

        @Override
        Status tickNode(Ctx c) {
            return c.now >= endAt ? Status.SUCCESS : Status.RUNNING;
        }
    }

    /** One melee hit on the target if it is within {@code reach} blocks; fails when it is not. */
    static final class Attack extends Task {
        private final double rate;
        private final double reach;

        Attack(double rate, double reach) {
            super("attack");
            this.rate = rate;
            this.reach = reach;
        }

        @Override
        Status tickNode(Ctx c) {
            Player target = c.bb.target;
            if (target == null || !c.host.valid(target, 64.0)) return Status.FAILURE;
            if (c.host.mob().getLocation().distance(target.getLocation()) > reach) return Status.FAILURE;
            c.host.stopMoving();
            c.host.face(target.getLocation());
            c.host.attack(target, rate);
            return Status.SUCCESS;
        }
    }

    /** One shot of the mob's ranged attack at the target. */
    static final class Shoot extends Task {
        private final double rate;

        Shoot(double rate) {
            super("shoot");
            this.rate = rate;
        }

        @Override
        Status tickNode(Ctx c) {
            Player target = c.bb.target;
            if (target == null || !c.host.valid(target, 64.0)) return Status.FAILURE;
            c.host.stopMoving();
            c.host.face(target.getLocation());
            return c.host.shoot(target, rate) ? Status.SUCCESS : Status.FAILURE;
        }
    }

    /** Uses a skill of the mob ({@code index}, else one chosen at random) at the target and runs until it is over; fails if it cannot start. */
    static final class UseSkill extends Task {
        private final int index;
        private boolean started;

        UseSkill(int index) {
            super("use-skill");
            this.index = index;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "use-skill" + (index >= 0 ? "#" + index : "");
        }

        @Override
        void onEnter(Ctx c) {
            started = false;
        }

        @Override
        Status tickNode(Ctx c) {
            if (!started) {
                Player target = c.bb.target;
                int count = c.host.skillCount();
                if (target == null || count == 0) return Status.FAILURE;
                int chosen = index >= 0 ? Math.min(index, count - 1) : c.random.nextInt(count);
                if (!c.host.startSkill(chosen, target)) return Status.FAILURE;
                started = true;
                return Status.RUNNING;
            }
            return c.host.skillRunning() && c.now - startedTick < 400 ? Status.RUNNING : Status.SUCCESS;
        }
    }

    /** Turns to face the target. */
    static final class FaceTarget extends Task {
        FaceTarget() {
            super("face-target");
        }

        @Override
        Status tickNode(Ctx c) {
            if (c.bb.target == null) return Status.FAILURE;
            c.host.face(c.bb.target.getLocation());
            return Status.SUCCESS;
        }
    }

    /** Writes a value to a blackboard key. */
    static final class SetBlackboard extends Task {
        private final String key;
        private final Object value;

        SetBlackboard(String key, Object value) {
            super("set-blackboard");
            this.key = key;
            this.value = value;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "set " + key + "=" + value;
        }

        @Override
        Status tickNode(Ctx c) {
            c.bb.set(key, value);
            return Status.SUCCESS;
        }
    }

    /** Changes the state of the state machine (applied when this tick is over). */
    static final class SetState extends Task {
        private final String state;

        SetState(String state) {
            super("set-state");
            this.state = state;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "set-state " + state;
        }

        @Override
        Status tickNode(Ctx c) {
            c.requestedState = state;
            return Status.SUCCESS;
        }
    }

    /** Forgets the target and does not look for another for {@code seconds}. */
    static final class ClearTarget extends Task {
        private final int ticks;

        ClearTarget(int ticks) {
            super("clear-target");
            this.ticks = ticks;
        }

        @Override
        Status tickNode(Ctx c) {
            c.bb.target = null;
            c.bb.set("no-target-until", (double) (c.now + ticks));
            c.host.stopMoving();
            return Status.SUCCESS;
        }
    }

    /** Plays a sound at the mob. */
    static final class PlaySound extends Task {
        private final Sound sound;
        private final float volume;
        private final float pitch;

        PlaySound(Sound sound, float volume, float pitch) {
            super("play-sound");
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }

        @Override
        Status tickNode(Ctx c) {
            Location at = c.host.mob().getLocation();
            at.getWorld().playSound(at, sound, volume, pitch);
            return Status.SUCCESS;
        }
    }

    /** Plays an animation of the mob's model; with `wait: true` it runs until the animation is over. Succeeds without a model (nothing to play), so a tree works with or without one. */
    static final class PlayAnimation extends Task {
        private final String animation;
        private final boolean wait;
        private int endAt;

        PlayAnimation(String animation, boolean wait) {
            super("play-animation");
            this.animation = animation;
            this.wait = wait;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "play-animation " + animation;
        }

        @Override
        void onEnter(Ctx c) {
            boolean played = c.host.playAnimation(animation);
            endAt = wait && played ? c.now + (int) Math.round(c.host.animationSeconds(animation) * 20.0) : c.now;
        }

        @Override
        Status tickNode(Ctx c) {
            return c.now >= endAt ? Status.SUCCESS : Status.RUNNING;
        }
    }

    /** Answers success (or failure) at once: a placeholder for a branch that does nothing. */
    static final class Fixed extends Task {
        private final Status answer;

        Fixed(String type, Status answer) {
            super(type);
            this.answer = answer;
        }

        @Override
        Status tickNode(Ctx c) {
            return answer;
        }
    }
}
