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

        BlackboardCondition(String key, String op, Object expected) {
            this.key = key;
            this.op = op;
            this.expected = expected;
            this.label = "bb " + key + " " + op + (expected == null ? "" : " " + expected);
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
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

        Distance(double min, double max) {
            this.min = min;
            this.max = max;
            this.label = "distance " + (min > 0 ? min : "") + "〜" + (max < Double.MAX_VALUE ? max : "");
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            if (c.bb.target == null) return false;
            double distance = c.bb.number("distance", Double.MAX_VALUE);
            return distance >= min && distance <= max;
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

    /** After the node finishes it cannot be entered for {@code ticks}. */
    static final class Cooldown extends Decorator {
        private final int ticks;
        private int readyAt;

        Cooldown(int ticks) {
            this.ticks = ticks;
            this.label = "cooldown " + ticks / 20.0 + "s";
        }

        @Override
        boolean canEnter(Ctx c, Node node) {
            return c.now >= readyAt;
        }

        @Override
        Status after(Ctx c, Node node, Status status) {
            readyAt = c.now + ticks;
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
