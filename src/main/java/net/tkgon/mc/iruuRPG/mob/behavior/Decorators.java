package net.tkgon.mc.iruuRPG.mob.behavior;

/** The decorators: conditions (blackboard, distance, hp, line of sight, chance) and wrappers (cooldown, loop, time limit, inverter, force success). */
final class Decorators {

    private Decorators() {
    }

    /** A condition on a blackboard key: {@code ==} {@code !=} {@code <} {@code <=} {@code >} {@code >=}, or {@code set} / {@code not-set}. */
    static final class BlackboardCondition extends Decorator {
        private final String key;
        private final String op;
        private final Object expected;
        /** Compare with the value of this other key instead of {@code expected} (a bound that each mob sets in its blackboard). */
        private final String valueKey;

        BlackboardCondition(String key, String op, Object expected) {
            this(key, op, expected, null);
        }

        BlackboardCondition(String key, String op, Object expected, String valueKey) {
            this.key = key;
            this.op = op;
            this.expected = expected;
            this.valueKey = valueKey == null || valueKey.isBlank() ? null : valueKey;
            this.label = "bb " + key + " " + op + (this.valueKey != null ? " @" + this.valueKey : expected == null ? "" : " " + expected);
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            if (valueKey != null && c.bb.has(valueKey)) return compare(c, c.bb.get(valueKey));
            return compare(c, expected);
        }

        private boolean compare(Ctx c, Object expected) {
            switch (op) {
                case "set":
                    return c.bb.has(key) && c.bb.flag(key);
                case "not-set":
                    return !c.bb.has(key) || !c.bb.flag(key);
                default:
                    break;
            }
            if (expected instanceof Boolean flag) {
                boolean now = c.bb.flag(key);
                return op.equals("!=") ? now != flag : now == flag;
            }
            if (expected instanceof Number number) {
                double now = c.bb.number(key, Double.NaN);
                if (Double.isNaN(now)) return false;
                double wanted = number.doubleValue();
                return switch (op) {
                    case "!=" -> now != wanted;
                    case "<" -> now < wanted;
                    case "<=" -> now <= wanted;
                    case ">" -> now > wanted;
                    case ">=" -> now >= wanted;
                    default -> now == wanted;
                };
            }
            String now = c.bb.text(key);
            String wanted = String.valueOf(expected);
            return op.equals("!=") ? !now.equals(wanted) : now.equals(wanted);
        }
    }

    /** The target is between {@code min} and {@code max} blocks away (false without a target). */
    static final class Distance extends Decorator {
        private final double min;
        private final double max;
        private final String minKey;
        private final String maxKey;

        Distance(double min, double max, String minKey, String maxKey) {
            this.min = min;
            this.max = max;
            this.minKey = minKey == null || minKey.isBlank() ? null : minKey;
            this.maxKey = maxKey == null || maxKey.isBlank() ? null : maxKey;
            this.label = "distance " + (this.minKey != null ? "@" + this.minKey : min > 0 ? min : "") + "〜" + (this.maxKey != null ? "@" + this.maxKey : max < Double.MAX_VALUE ? max : "");
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            if (c.bb.target == null) return false;
            double distance = c.bb.number("distance", Double.MAX_VALUE);
            // (min-key / max-key: the bounds from the blackboard, when the mob has them; one tree, a reach for each mob)
            double low = minKey != null ? c.bb.number(minKey, min) : min;
            double high = maxKey != null ? c.bb.number(maxKey, max) : max;
            return distance >= low && distance <= high;
        }
    }

    /** The mob's life is below {@code below} and above {@code above} (0 - 1). */
    static final class Hp extends Decorator {
        private final double below;
        private final double above;

        Hp(double below, double above) {
            this.below = below;
            this.above = above;
            this.label = "hp " + (above > 0 ? above + "〜" : "〜") + below;
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            double hp = c.bb.number("hp", 1.0);
            return hp <= below && hp >= above;
        }
    }

    /** The mob sees its target (or, inverted, does not). */
    static final class LineOfSight extends Decorator {
        private final boolean wanted;

        LineOfSight(boolean wanted) {
            this.wanted = wanted;
            this.label = wanted ? "sees target" : "no sight";
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            return c.bb.target != null && c.bb.flag("in-sight") == wanted;
        }
    }

    /** Lets the node be entered with the chance given (checked only at entry). */
    static final class Chance extends Decorator {
        private final double chance;

        Chance(double chance) {
            this.chance = chance;
            this.label = "chance " + Math.round(chance * 100) + "%";
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            return c.random.nextDouble() < chance;
        }
    }

    /** The number of allies within {@code radius} blocks (the same kind of mob, or any behavior-tree mob) is between {@code min} and {@code max}. */
    static final class Allies extends Decorator {
        private final double radius;
        private final int min;
        private final int max;
        private final boolean sameType;

        Allies(double radius, int min, int max, boolean sameType) {
            this.radius = radius;
            this.min = min;
            this.max = max;
            this.sameType = sameType;
            this.label = "allies " + min + "〜" + (max == Integer.MAX_VALUE ? "" : max) + " (" + radius + ")";
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            int count = c.host.alliesNear(radius, sameType);
            return count >= min && count <= max;
        }
    }

    /**
     * An attack slot (a token): at most {@code max} mobs of the group hold the slot {@code name} at once. The group is the mobs going after the same target ({@code per: target}), the
     * same kind of mob ({@code type}) or everyone ({@code world}). The node may be entered while a slot is free; it holds the slot while it runs and gives it back when it ends, or
     * after {@code hold} seconds (the node is stopped then). With no slot free the condition fails, and a selector goes on to its next branch.
     */
    static final class Slot extends Decorator {
        private final String name;
        private final int max;
        private final int hold;
        private final String per;
        private String key;

        Slot(String name, int max, int hold, String per) {
            this.name = name;
            this.max = Math.max(1, max);
            this.hold = Math.max(1, hold);
            this.per = per;
            this.label = "slot " + name + " ×" + this.max;
        }

        private String key(Ctx c) {
            String scope = switch (per) {
                case "type" -> c.host.typeId();
                case "world" -> "world";
                default -> c.bb.target == null ? "none" : c.bb.target.getUniqueId().toString();
            };
            return name + ":" + c.host.typeId() + "@" + scope;
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            return c.host.coordinator().available(key(c), c.host.id(), max, c.now);
        }

        @Override
        void entered(Ctx c, Node node) {
            key = key(c);
            c.host.coordinator().acquire(key, c.host.id(), max, hold + 20, c.now);
        }

        @Override
        void left(Ctx c, Node node) {
            if (key != null) c.host.coordinator().release(key, c.host.id());
            key = null;
        }

        @Override
        Status during(Ctx c, Node node) {
            return c.now - node.startedTick >= hold ? Status.FAILURE : null;
        }
    }

    /** A cooldown shared by a group: once one of them enters the node, none can for {@code seconds}. Scope: the same target, the same kind of mob, or everyone. */
    static final class GroupCooldown extends Decorator {
        private final String name;
        private final int ticks;
        private final String scope;

        GroupCooldown(String name, int ticks, String scope) {
            this.name = name;
            this.ticks = ticks;
            this.scope = scope;
            this.label = "group-cooldown " + name + " " + ticks / 20.0 + "s";
        }

        private String key(Ctx c) {
            String id = switch (scope) {
                case "type" -> c.host.typeId();
                case "world" -> "world";
                default -> c.bb.target == null ? "none" : c.bb.target.getUniqueId().toString();
            };
            return "cd:" + name + ":" + c.host.typeId() + "@" + id;
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            return c.host.coordinator().ready(key(c), c.now);
        }

        @Override
        void entered(Ctx c, Node node) {
            c.host.coordinator().start(key(c), c.now, ticks);
        }
    }

    /** After the node finishes it cannot be entered for {@code ticks}. */
    static final class Cooldown extends Decorator {
        private final int ticks;
        /** Only a success starts the wait (a skill that was used, not one that was tried and was not ready). */
        private final boolean successOnly;
        private int readyAt;

        Cooldown(int ticks, boolean successOnly) {
            this.ticks = ticks;
            this.successOnly = successOnly;
            this.label = "cooldown " + ticks / 20.0 + "s" + (successOnly ? " (success)" : "");
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            return c.now >= readyAt;
        }

        @Override
        Status after(Ctx c, Node node, Status status) {
            if (!successOnly || status == Status.SUCCESS) readyAt = c.now + ticks;
            return status;
        }
    }

    /** Runs the node again and again: {@code count} times, or for ever; the node answers RUNNING meanwhile. */
    static final class Loop extends Decorator {
        private final int count;
        private final boolean infinite;
        private int done;

        Loop(int count, boolean infinite) {
            this.count = count;
            this.infinite = infinite;
            this.label = infinite ? "loop ∞" : "loop ×" + count;
        }

        @Override
        Status after(Ctx c, Node node, Status status) {
            done++;
            if (infinite || done < count) return Status.RUNNING;
            done = 0;
            return Status.SUCCESS;
        }
    }

    /** Fails the node when it has run for {@code ticks}. */
    static final class TimeLimit extends Decorator {
        private final int ticks;

        TimeLimit(int ticks) {
            this.ticks = ticks;
            this.label = "limit " + ticks / 20.0 + "s";
        }

        @Override
        Status during(Ctx c, Node node) {
            return c.now - node.startedTick >= ticks ? Status.FAILURE : null;
        }
    }

    /** Turns success into failure and failure into success. */
    static final class Inverter extends Decorator {
        Inverter() {
            this.label = "not";
        }

        @Override
        Status after(Ctx c, Node node, Status status) {
            return status == Status.SUCCESS ? Status.FAILURE : status == Status.FAILURE ? Status.SUCCESS : status;
        }
    }

    /** The node counts as a success whatever it did. */
    static final class ForceSuccess extends Decorator {
        ForceSuccess() {
            this.label = "force success";
        }

        @Override
        Status after(Ctx c, Node node, Status status) {
            return status == Status.RUNNING ? status : Status.SUCCESS;
        }
    }
}
