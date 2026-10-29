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
        /** Faces the target all the way (stepping back from it, say) instead of turning to the path. */
        private final boolean face;
        /** Goes this many blocks above the place (a flying mob that keeps above its target). */
        private final double height;
        private Location place;
        private int lastPath;
        private int failedPaths;
        private int checkAt;
        private Location checkFrom;

        MoveTo(String mode, String key, String eqs, double speed, double acceptance, int timeout, boolean face, double height) {
            super("move-to");
            this.mode = mode;
            this.key = key;
            this.eqs = eqs;
            this.speed = speed;
            this.acceptance = acceptance;
            this.timeout = timeout;
            this.face = face;
            this.height = height;
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
            if (face) c.host.faceWhileMoving(null);
        }

        @Override
        Status tickNode(Ctx c) {
            Status status = move(c);
            if (face) c.host.faceWhileMoving(status == Status.RUNNING && c.bb.target != null && c.bb.target.getWorld() == c.host.mob().getWorld() ? c.bb.target : null);
            return status;
        }

        private Status move(Ctx c) {
            Location goal = mode.equals("target") ? (c.bb.target == null ? null : c.bb.target.getLocation()) : place;
            if (goal == null || goal.getWorld() == null) return Status.FAILURE;
            if (height != 0.0) goal = goal.clone().add(0.0, height, 0.0);
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
        /** A blackboard key whose number (seconds) is used instead of {@code ticks} when the mob has it (one tree, a pace for each mob). */
        private final String key;
        private int endAt;

        Wait(int ticks, int deviation, String key) {
            super("wait");
            this.ticks = ticks;
            this.deviation = deviation;
            this.key = key == null || key.isBlank() ? null : key;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "wait " + ticks / 20.0 + "s";
        }

        @Override
        void onEnter(Ctx c) {
            int extra = deviation <= 0 ? 0 : c.random.nextInt(deviation * 2 + 1) - deviation;
            int base = key != null && c.bb.has(key) ? (int) Math.round(c.bb.number(key, ticks / 20.0) * 20.0) : ticks;
            endAt = c.now + Math.max(1, base + extra);
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
            // (no range given: the mob's `attack-range` from the blackboard, else 2.6)
            double limit = reach > 0.0 ? reach : c.bb.number("attack-range", 2.6);
            if (c.host.mob().getLocation().distance(target.getLocation()) > limit) return Status.FAILURE;
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
        private final String skill;
        private final String animation;
        private final boolean track;
        private boolean started;
        private int animationEnd;

        UseSkill(int index, String skill, String animation, boolean track) {
            super("use-skill");
            this.index = index;
            this.track = track;
            this.skill = skill == null || skill.isBlank() ? null : skill;
            this.animation = animation == null || animation.isBlank() ? null : animation;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "use-skill" + (skill != null ? " " + skill : index >= 0 ? "#" + index : "");
        }

        @Override
        void onEnter(Ctx c) {
            started = false;
            animationEnd = 0;
        }

        @Override
        Status tickNode(Ctx c) {
            if (!started) {
                Player target = c.bb.target;
                if (target == null) return Status.FAILURE;
                if (skill != null) {
                    // a skill of the skill tool by its id (the mob's own skills: with that id first)
                    if (!c.host.startSkill(skill, target, track)) return Status.FAILURE;
                } else if (index >= 0) {
                    int count = c.host.skillCount();
                    if (count == 0) return Status.FAILURE;
                    if (!c.host.startSkill(Math.min(index, count - 1), target, track)) return Status.FAILURE;
                } else {
                    // neither an id nor an index: one of the mob's own skills that is ready (its cooldown is over) and can reach the target from here
                    if (!c.host.startUsableSkill(target, track)) return Status.FAILURE;
                }
                // the model's animation of the skill, from the same moment (the skill's wind-up decides when it hits: set it to the moment the animation strikes)
                if (animation != null && c.host.playAnimation(animation)) animationEnd = c.now + (int) Math.round(c.host.animationSeconds(animation) * 20.0);
                started = true;
                return Status.RUNNING;
            }
            boolean running = (c.host.skillRunning() || c.now < animationEnd) && c.now - startedTick < 400;
            return running ? Status.RUNNING : Status.SUCCESS;
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

    /**
     * An attack made of an animation and the hits in it: the mob stops and faces its target, plays the animation (with a model; without one it just waits {@code duration}), and
     * at the moment of each hit ({@code at} seconds after the start) every player in that hit's area takes it. Runs until the animation (or the last hit) is over. With
     * {@code telegraph}, each hit's area shows on the ground from the start until it lands.
     */
    static final class AnimationAttack extends Task {
        record Hit(int at, Area area, double rate, double knockback) {
        }

        private final String animation;
        private final java.util.List<Hit> hits;
        private final int duration;
        private final boolean face;
        private final boolean telegraph;
        private int length;
        private int next;

        AnimationAttack(String animation, java.util.List<Hit> hits, int duration, boolean face, boolean telegraph) {
            super("animation-attack");
            this.animation = animation;
            this.hits = hits;
            this.duration = duration;
            this.face = face;
            this.telegraph = telegraph;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "animation-attack " + animation;
        }

        @Override
        void onEnter(Ctx c) {
            next = 0;
            c.host.stopMoving();
            if (face && c.bb.target != null) c.host.face(c.bb.target.getLocation());
            boolean played = c.host.playAnimation(animation);
            length = played ? (int) Math.round(c.host.animationSeconds(animation) * 20.0) : duration;
            for (Hit hit : hits) length = Math.max(length, hit.at());
            if (telegraph) for (Hit hit : hits) c.host.telegraph(hit.area(), Math.max(1, hit.at()));
        }

        @Override
        Status tickNode(Ctx c) {
            int age = c.now - startedTick;
            while (next < hits.size() && age >= hits.get(next).at()) {
                Hit hit = hits.get(next++);
                c.host.areaAttack(hit.area(), hit.rate(), hit.knockback());
            }
            return age >= length ? Status.SUCCESS : Status.RUNNING;
        }
    }

    /**
     * Circles the target for {@code seconds}, one way round, keeping an eye on it: what a mob does when it has nothing else to do
     * (its skills are not ready, it may not hit). {@code radius} 0 keeps the distance it had when it started. It turns the other way
     * when it is stuck (a wall) and fails when there is no target or it cannot move at all.
     */
    static final class Orbit extends Task {
        private static final double STEP = Math.toRadians(35.0);
        private final double radius;
        private final double speed;
        private final int ticks;
        private final int deviation;
        private final String direction;
        private double ring;
        private int turn;
        private int endAt;
        private int lastPath;
        private int failedPaths;
        private int checkAt;
        private Location checkFrom;

        Orbit(double radius, double speed, int ticks, int deviation, String direction) {
            super("orbit");
            this.radius = radius;
            this.speed = speed;
            this.ticks = ticks;
            this.deviation = deviation;
            this.direction = direction;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "orbit " + (radius > 0.0 ? "r" + radius + " " : "") + ticks / 20.0 + "s";
        }

        @Override
        void onEnter(Ctx c) {
            int extra = deviation <= 0 ? 0 : c.random.nextInt(deviation * 2 + 1) - deviation;
            endAt = c.now + Math.max(1, ticks + extra);
            turn = switch (direction) {
                case "left" -> 1;
                case "right" -> -1;
                default -> c.random.nextBoolean() ? 1 : -1;
            };
            ring = radius;
            if (ring <= 0.0 && c.bb.target != null && c.bb.target.getWorld() == c.host.mob().getWorld()) {
                ring = c.host.mob().getLocation().distance(c.bb.target.getLocation());
            }
            ring = Math.max(2.0, ring);
            lastPath = -100;
            failedPaths = 0;
            checkAt = c.now + 30;
            checkFrom = c.host.mob().getLocation();
        }

        @Override
        void onAbort(Ctx c) {
            c.host.stopMoving();
            c.host.faceWhileMoving(null);
        }

        @Override
        Status tickNode(Ctx c) {
            Status status = circle(c);
            // it faces the target all the way round (it steps sideways), not the way it goes
            c.host.faceWhileMoving(status == Status.RUNNING ? c.bb.target : null);
            return status;
        }

        private Status circle(Ctx c) {
            Player target = c.bb.target;
            if (target == null || !c.host.valid(target, 64.0)) return Status.FAILURE;
            if (c.now >= endAt) {
                c.host.stopMoving();
                c.host.face(target.getLocation());
                return Status.SUCCESS;
            }
            Location here = c.host.mob().getLocation();
            Location center = target.getLocation();
            if (here.getWorld() != center.getWorld()) return Status.FAILURE;
            if (c.now >= checkAt) {
                // hardly moved: something is in the way, go round the other way
                if (checkFrom != null && checkFrom.distance(here) < 0.5) {
                    turn = -turn;
                    lastPath = -100;
                }
                checkFrom = here;
                checkAt = c.now + 30;
            }
            if (c.now - lastPath >= 6 || !c.host.hasPath()) {
                lastPath = c.now;
                double angle = Math.atan2(here.getZ() - center.getZ(), here.getX() - center.getX()) + turn * STEP;
                Location next = new Location(center.getWorld(), center.getX() + Math.cos(angle) * ring, here.getY(), center.getZ() + Math.sin(angle) * ring);
                if (!c.host.moveTo(next, speed)) {
                    turn = -turn;
                    if (++failedPaths >= 3) {
                        c.host.stopMoving();
                        return Status.FAILURE;
                    }
                } else {
                    failedPaths = 0;
                }
            }
            return Status.RUNNING;
        }
    }

    /**
     * Goes round a route of `waypoints:` (from the point nearest to the mob), waiting {@code wait} ticks at each point; round and round (or back and forth), or {@code laps}
     * times and then succeeds. A point it cannot get to (no path, stuck for 2 s) is skipped. Fails when the route does not exist.
     */
    static final class Patrol extends Task {
        private final String route;
        private final double speed;
        private final int waitTicks;
        private final double acceptance;
        private final int laps;
        private int index;
        private int step;
        private int waitUntil;
        private int lapsDone;
        private int lastPath;
        private int failedPaths;
        private int checkAt;
        private Location checkFrom;

        Patrol(String route, double speed, int waitTicks, double acceptance, int laps) {
            super("patrol");
            this.route = route;
            this.speed = speed;
            this.waitTicks = waitTicks;
            this.acceptance = Math.max(0.5, acceptance);
            this.laps = Math.max(0, laps);
        }

        String route() {
            return route;
        }

        @Override
        String label() {
            return name != null && !name.isBlank() ? name : "patrol " + route + " #" + index;
        }

        @Override
        void onEnter(Ctx c) {
            step = 1;
            waitUntil = -1;
            lapsDone = 0;
            lastPath = -100;
            failedPaths = 0;
            checkAt = c.now + 40;
            Location here = c.host.mob().getLocation();
            checkFrom = here;
            index = 0;
            Behavior.Route r = c.routes.get(route);
            if (r == null) return;
            double nearest = Double.MAX_VALUE;
            for (int at = 0; at < r.points().size(); at++) {
                Location point = point(c, r, at);
                if (point == null || point.getWorld() != here.getWorld()) continue;
                double distance = point.distanceSquared(here);
                if (distance < nearest) {
                    nearest = distance;
                    index = at;
                }
            }
        }

        @Override
        void onAbort(Ctx c) {
            c.host.stopMoving();
        }

        @Override
        Status tickNode(Ctx c) {
            Behavior.Route r = c.routes.get(route);
            if (r == null) return Status.FAILURE;
            Location point = point(c, r, index);
            Location here = c.host.mob().getLocation();
            if (point == null || point.getWorld() != here.getWorld()) return Status.FAILURE;
            if (waitUntil >= 0) {
                if (c.now < waitUntil) return Status.RUNNING;
                waitUntil = -1;
                return advance(r) ? Status.SUCCESS : Status.RUNNING;
            }
            if (here.distance(point) <= acceptance) {
                c.host.stopMoving();
                if (waitTicks > 0) {
                    waitUntil = c.now + waitTicks;
                    return Status.RUNNING;
                }
                return advance(r) ? Status.SUCCESS : Status.RUNNING;
            }
            if (c.now >= checkAt) {
                boolean stuck = checkFrom != null && checkFrom.getWorld() == here.getWorld() && checkFrom.distance(here) < 0.6;
                checkFrom = here;
                checkAt = c.now + 40;
                if (stuck) {
                    c.host.stopMoving();
                    lastPath = -100;
                    return advance(r) ? Status.SUCCESS : Status.RUNNING;
                }
            }
            if (c.now - lastPath >= 8 || !c.host.hasPath()) {
                lastPath = c.now;
                if (!c.host.moveTo(point, speed)) {
                    if (++failedPaths >= 3) {
                        failedPaths = 0;
                        return advance(r) ? Status.SUCCESS : Status.RUNNING;
                    }
                } else {
                    failedPaths = 0;
                }
            }
            return Status.RUNNING;
        }

        /** On to the next point; true when the laps asked for are done. */
        private boolean advance(Behavior.Route r) {
            int count = r.points().size();
            if (count > 1) {
                if (r.loop()) {
                    index = (index + 1) % count;
                } else {
                    if (index + step < 0 || index + step >= count) step = -step;
                    index += step;
                }
            }
            if (index == 0 || count == 1) lapsDone++;
            return laps > 0 && lapsDone >= laps;
        }

        private static Location point(Ctx c, Behavior.Route r, int at) {
            org.bukkit.util.Vector v = r.points().get(Math.max(0, Math.min(r.points().size() - 1, at)));
            if (r.absolute()) return new Location(c.host.mob().getWorld(), v.getX(), v.getY(), v.getZ());
            Location home = c.host.home();
            return home == null ? null : home.clone().add(v);
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
