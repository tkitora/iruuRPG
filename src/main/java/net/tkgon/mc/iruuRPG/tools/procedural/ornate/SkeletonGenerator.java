package net.tkgon.mc.iruuRPG.tools.procedural.ornate;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Grows the bones of an ornate tree by its style (t282, t286): 0 the oak (the ornate fantasy tree: the canopy tree's fractal of forks, with swirling limbs and tips
 * that curl up into a scroll), 1 spreading (a bonsai or a pine), 2 a spruce, 3 a birch, 4 a willow, 5 a cherry, 6 an acacia. The roots are grown the same way, down
 * and out from the foot, and can be laid on the ground. Then the radii by the pipe model ({@link Skeleton#pipe}).
 *
 * <p>(t288) The first branch's height, when it is given, is where the branches start: an oak's lower limbs, a bonsai's lowest tier, a spruce's lowest whorl, a
 * birch's lowest branch (the trunk grows at least two blocks over it); a willow, a cherry and an acacia fork there (it is their trunk's height).
 */
public final class SkeletonGenerator {

    /** The numbers the bones are grown by; {@code branchStart} (t288): how high the first branch leaves the trunk (0: where the style puts it). */
    public record Params(int style, double height, double trunk, double curl, int roots, double rootReach, int branches, int depth, double length, double spread,
                         double droop, double tipCurl, double pipe, double branchStart) {

        /** The same with the first branch where the style puts it. */
        public Params(int style, double height, double trunk, double curl, int roots, double rootReach, int branches, int depth, double length, double spread,
                      double droop, double tipCurl, double pipe) {
            this(style, height, trunk, curl, roots, rootReach, branches, depth, length, spread, droop, tipCurl, pipe, 0.0);
        }
    }

    /**
     * How a stretch of wood bends while it grows: it swirls ({@code swirl} a step, the way turning {@code swirlRate} a step; round itself, or for the trunk round the
     * upright, so it sways round and stays up), sags or rises, curls its tip, wanders, and keeps {@code gap} from other wood.
     */
    private record Bend(double swirl, double swirlRate, double droop, double upturn, double tipCurl, double wander, double gap, boolean upright) {
    }

    private static final double SPIRAL = Math.PI * (3.0 - Math.sqrt(5.0));

    private final Params p;
    private final Random random;
    private final Skeleton s = new Skeleton();

    private SkeletonGenerator(Params p, Random random) {
        this.p = p;
        this.random = random;
    }

    public static Skeleton grow(Params p, Random random) {
        SkeletonGenerator generator = new SkeletonGenerator(p, random);
        generator.grow();
        return generator.s;
    }

    /** The bones with their roots laid on {@code ground} (when it is not null): see {@link #snapRoots}. */
    public static Skeleton grow(Params p, Random random, Ground ground) {
        SkeletonGenerator generator = new SkeletonGenerator(p, random);
        generator.grow();
        if (ground != null) generator.snapRoots(ground);
        return generator.s;
    }

    /** The styles, in the order of the setting. */
    public static final int OAK = 0;
    public static final int BONSAI = 1;
    public static final int SPRUCE = 2;
    public static final int BIRCH = 3;
    public static final int WILLOW = 4;
    public static final int CHERRY = 5;
    public static final int ACACIA = 6;
    public static final int STYLES = 7;

    private void grow() {
        int base = s.add(0.5, 0.0, 0.5, -1, Skeleton.TRUNK, 0);
        switch (p.style()) {
            case BONSAI -> spreading(base);
            case SPRUCE -> spruce(base);
            case BIRCH -> birch(base);
            case WILLOW -> willow(base);
            case CHERRY -> cherry(base);
            case ACACIA -> acacia(base);
            default -> oak(base);
        }
        roots(base);
        s.pipe(p.pipe(), 0.025, p.trunk(), 1.0);
    }

    // ---- the styles ------------------------------------------------------------------------------------------------------------------

    /** A branch waiting to be grown. */
    private record Grow(int from, double[] way, double length, int depth, int limb) {
    }

    /** The trunk: from the foot up {@code length}, leaning {@code lean} toward a side of chance, swaying round the upright by {@code sway}. */
    private int trunk(int base, double length, double lean, double sway) {
        double turn = random.nextDouble() * Math.PI * 2.0;
        double[] up = normal(new double[]{Math.cos(turn) * lean, 1.0, Math.sin(turn) * lean});
        return chain(base, up, length, Skeleton.TRUNK, 0, new Bend(sway, 0.3, 0.0, 0.12, 0.0, 0.04, 0.0, true), false);
    }

    /**
     * Grows the branches waiting on {@code stack}: each a stretch bent by {@code bend} (the last ones, depth 0, by {@code last}), then {@code count} children
     * {@code spread} degrees off its end, {@code lengthMult} as long, round it.
     */
    private void forks(Deque<Grow> stack, Bend bend, Bend last, double spread, double lengthMult) {
        while (!stack.isEmpty()) {
            Grow g = stack.pop();
            boolean tip = g.depth() <= 0;
            int end = chain(g.from(), g.way(), g.length(), Skeleton.BRANCH, g.limb(), tip ? last : bend, true);
            if (tip || end == g.from() || s.y(end) < 0.5) continue;
            double[] endWay = way(end);
            int count = Math.max(2, p.branches() - (random.nextDouble() < 0.25 ? 1 : 0));
            double childRoll = random.nextDouble() * Math.PI * 2.0;
            for (int child = 0; child < count; child++) {
                double[] way = turned(endWay, Math.toRadians(spread) * (0.8 + 0.4 * random.nextDouble()), childRoll + child * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.5);
                double length = g.length() * lengthMult * (0.85 + 0.25 * random.nextDouble());
                if (length >= 1.0) stack.push(new Grow(end, way, length, g.depth() - 1, g.limb()));
            }
        }
    }

    /**
     * The oak (the ornate fantasy tree): a swirling trunk that forks into limbs at the top, a limb or two lower on it, forks {@code depth} times, tips curling up. With
     * the first branch's height (t288) the lower limbs leave from there up (a trunk shorter than that grows to it and forks there, with no lower limbs).
     */
    private void oak(int base) {
        double start = p.branchStart();
        double lean = 0.08 + p.curl() * 0.12;
        double height = start > 0.0 && start >= p.height() ? climb(start, lean) : p.height();
        int top = trunk(base, height, lean, p.curl() * 0.18);
        Deque<Grow> stack = new ArrayDeque<>();
        double[] topWay = way(top);
        double roll = random.nextDouble() * Math.PI * 2.0;
        int forks = Math.max(2, p.branches());
        for (int limb = 0; limb < forks; limb++) {
            double[] way = turned(topWay, Math.toRadians(p.spread()) * (0.85 + 0.3 * random.nextDouble()), roll + limb * Math.PI * 2.0 / forks + (random.nextDouble() - 0.5) * 0.4);
            stack.push(new Grow(top, way, p.length() * (0.9 + 0.2 * random.nextDouble()), p.depth() - 1, s.newLimb()));
        }
        // limbs lower on the trunk (an ornate tree is not a broom)
        int sides = p.depth() >= 3 && height >= 5 ? 1 + (p.branches() >= 3 ? 1 : 0) : 0;
        double high = s.y(top) * 0.85;
        if (start > 0.0) sides = start < high && p.depth() >= 2 ? Math.max(1, sides) : 0;
        for (int side = 0; side < sides; side++) {
            double share = start > 0.0 ? shareOf(base, top, start + (high - start) * 0.5 * side)
                    : (sides == 1 ? 0.62 : 0.5 + 0.3 * side / (sides - 1.0)) + 0.05 * random.nextDouble();
            int at = trunkNodeAt(base, top, share);
            double angle = roll + Math.PI / forks + side * SPIRAL * 2.0;
            double[] way = normal(new double[]{Math.cos(angle), 0.55 + 0.3 * random.nextDouble(), Math.sin(angle)});
            stack.push(new Grow(at, way, p.length() * 0.7, p.depth() - 2, s.newLimb()));
        }
        Bend bend = new Bend(p.curl() * 0.22, 0.45, p.droop() * 0.1, 0.07, 0.0, 0.05, 2.0, false);
        Bend last = new Bend(p.curl() * 0.22, 0.45, p.droop() * 0.1, 0.07, p.tipCurl(), 0.05, 2.0, false);
        forks(stack, bend, last, p.spread(), 0.72);
    }

    /**
     * A bonsai or a pine: a leaning S trunk, tiers of nearly level branches from a third of the way up (or from the first branch's height, t288; longer below), their
     * twigs spread level, a short top.
     */
    private void spreading(int base) {
        double lean = 0.35 + p.curl() * 0.35;
        double height = p.branchStart() > 0.0 ? Math.max(p.height(), climb(p.branchStart() + 2.0, lean)) : p.height();
        int top = trunk(base, height, lean, p.curl() * 0.22);
        int tiers = Math.max(2, p.branches() * 2);
        double first = p.branchStart() > 0.0 ? Math.min(0.95, shareOf(base, top, p.branchStart())) : 0.35;
        double angle = random.nextDouble() * Math.PI * 2.0;
        Deque<Grow> stack = new ArrayDeque<>();
        for (int tier = 0; tier < tiers; tier++) {
            double share = first + (0.95 - first) * tier / (tiers - 1.0);
            int at = trunkNodeAt(base, top, share);
            angle += SPIRAL * 2.0 + (random.nextDouble() - 0.5) * 0.4;
            double[] way = normal(new double[]{Math.cos(angle), -0.05 + 0.1 * random.nextDouble(), Math.sin(angle)});
            stack.push(new Grow(at, way, p.length() * (1.15 - 0.6 * share) * (0.85 + 0.3 * random.nextDouble()), p.depth() - 1, s.newLimb()));
        }
        stack.push(new Grow(top, normal(new double[]{0.15, 1.0, 0.0}), p.length() * 0.35, Math.min(1, p.depth() - 1), s.newLimb()));
        while (!stack.isEmpty()) {
            Grow g = stack.pop();
            boolean last = g.depth() <= 0;
            Bend bend = new Bend(p.curl() * 0.08, 0.3, p.droop() * 0.1 + 0.02, 0.0, last ? p.tipCurl() * 0.4 : 0.0, 0.04, 2.0, false);
            int end = chain(g.from(), g.way(), g.length(), Skeleton.BRANCH, g.limb(), bend, true);
            if (last || end == g.from()) continue;
            double[] endWay = way(end);
            double heading = Math.atan2(endWay[2], endWay[0]);
            for (int side = -1; side <= 1; side += 2) {
                double spread = Math.toRadians(p.spread()) * (0.7 + 0.4 * random.nextDouble());
                double h = heading + side * spread;
                double[] way = normal(new double[]{Math.cos(h), (random.nextDouble() - 0.5) * 0.12, Math.sin(h)});
                stack.push(new Grow(end, way, g.length() * 0.6 * (0.85 + 0.3 * random.nextDouble()), g.depth() - 1, g.limb()));
            }
            // (and a twig on along the way it went)
            if (g.depth() > 1) stack.push(new Grow(end, normal(new double[]{endWay[0], 0.05, endWay[2]}), g.length() * 0.55, g.depth() - 2, g.limb()));
        }
    }

    /**
     * A spruce: a straight trunk to the top, whorls of {@code branches + 1} branches every block and a half from a fifth of the way up, the lowest {@code length}
     * long and shorter up to the top (a cone), going out a little down and turning up at the tips; their twigs ({@code depth - 1}) spread level.
     */
    private void spruce(int base) {
        double height = p.branchStart() > 0.0 ? Math.max(p.height(), p.branchStart() + 2.0) : p.height();
        int top = trunk(base, height, 0.03, p.curl() * 0.04);
        double low = p.branchStart() > 0.0 ? p.branchStart() : Math.max(2.0, height * 0.2);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int count = Math.max(3, p.branches() + 1);
        Deque<Grow> stack = new ArrayDeque<>();
        for (double y = low; y < s.y(top) - 0.8; y += 1.5) {
            double share = (y - low) / Math.max(1.0, s.y(top) - low);
            int at = trunkNodeAt(base, top, (y - s.y(base)) / Math.max(1.0, s.y(top) - s.y(base)));
            angle += SPIRAL;
            for (int n = 0; n < count; n++) {
                double a = angle + n * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.3;
                double down = -0.1 - 0.25 * p.droop() + 0.15 * share;
                double length = p.length() * (1.0 - share) * (0.85 + 0.25 * random.nextDouble()) + 0.8;
                stack.push(new Grow(at, normal(new double[]{Math.cos(a), down, Math.sin(a)}), length, Math.min(2, p.depth() - 1), s.newLimb()));
            }
        }
        // (a spruce's whorls are close: the branches keep a block and a bit apart)
        Bend bend = new Bend(p.curl() * 0.03, 0.4, p.droop() * 0.04, 0.0, 0.0, 0.03, 1.2, false);
        Bend last = new Bend(p.curl() * 0.03, 0.4, p.droop() * 0.03, 0.04 + p.tipCurl() * 0.06, 0.0, 0.03, 1.2, false);
        while (!stack.isEmpty()) {
            Grow g = stack.pop();
            boolean tip = g.depth() <= 0;
            int end = chain(g.from(), g.way(), g.length(), Skeleton.BRANCH, g.limb(), tip ? last : bend, true);
            if (tip || end == g.from()) continue;
            double[] endWay = way(end);
            double heading = Math.atan2(endWay[2], endWay[0]);
            for (int side = -1; side <= 1; side += 2) {
                double h = heading + side * Math.toRadians(p.spread()) * (0.8 + 0.3 * random.nextDouble());
                stack.push(new Grow(end, normal(new double[]{Math.cos(h), endWay[1] * 0.5, Math.sin(h)}), g.length() * 0.45, g.depth() - 1, g.limb()));
            }
        }
    }

    /**
     * A birch: a slim straight trunk to the top; from under halfway up, side branches by the golden angle, steep (the spread is their angle from upright), the
     * longest a third of the way up the crown (an egg), forking {@code depth - 1} times.
     */
    private void birch(int base) {
        double height = p.branchStart() > 0.0 ? Math.max(p.height(), p.branchStart() + 2.0) : p.height();
        int top = trunk(base, height, 0.05 + p.curl() * 0.08, p.curl() * 0.08);
        double from = p.branchStart() > 0.0 ? p.branchStart() : height * 0.35;
        int count = Math.max(4, p.branches() * 3);
        double angle = random.nextDouble() * Math.PI * 2.0;
        Deque<Grow> stack = new ArrayDeque<>();
        for (int n = 0; n < count; n++) {
            double share = (n + 0.5) / count;
            int at = trunkNodeAt(base, top, p.branchStart() > 0.0 ? shareOf(base, top, from + (s.y(top) - from) * share * 0.95)
                    : (from + (height - from) * share * 0.95) / Math.max(1.0, height));
            angle += SPIRAL + (random.nextDouble() - 0.5) * 0.3;
            double reach = p.length() * 2.4 * (0.25 + 0.75 * share) * (1.0 - share) * (0.8 + 0.4 * random.nextDouble());
            if (reach < 1.0) continue;
            double[] way = turned(new double[]{0.0, 1.0, 0.0}, Math.toRadians(p.spread()) * (0.9 + 0.3 * (1.0 - share)), angle);
            stack.push(new Grow(at, way, reach, p.depth() - 1, s.newLimb()));
        }
        Bend bend = new Bend(p.curl() * 0.12, 0.5, p.droop() * 0.08, 0.05, 0.0, 0.05, 2.0, false);
        Bend last = new Bend(p.curl() * 0.12, 0.5, p.droop() * 0.08, 0.05, p.tipCurl() * 0.5, 0.05, 2.0, false);
        forks(stack, bend, last, p.spread() * 0.8, 0.65);
    }

    /**
     * A willow: a short thick trunk, limbs that rise wide and arch over, their branches going out and down, the last twigs hanging nearly straight down (the
     * {@code droop} weighs them more the farther out they are).
     */
    private void willow(int base) {
        int top = trunk(base, forkHeight(0.1 + p.curl() * 0.15), 0.1 + p.curl() * 0.15, p.curl() * 0.15);
        Deque<Grow> stack = new ArrayDeque<>();
        double roll = random.nextDouble() * Math.PI * 2.0;
        int limbs = Math.max(3, p.branches());
        for (int limb = 0; limb < limbs; limb++) {
            // (the limbs rise first: they arch over above the trunk)
            double[] way = turned(new double[]{0.0, 1.0, 0.0}, Math.toRadians(p.spread()) * 0.6 * (0.85 + 0.3 * random.nextDouble()), roll + limb * Math.PI * 2.0 / limbs + (random.nextDouble() - 0.5) * 0.4);
            stack.push(new Grow(top, way, p.length() * (0.9 + 0.2 * random.nextDouble()), p.depth() - 1, s.newLimb()));
        }
        Bend arch = new Bend(p.curl() * 0.12, 0.4, 0.015 + p.droop() * 0.02, 0.0, 0.0, 0.04, 2.0, false);
        Bend hang = new Bend(p.curl() * 0.05, 0.4, 0.1 + p.droop() * 0.25, 0.0, 0.0, 0.03, 1.5, false);
        while (!stack.isEmpty()) {
            Grow g = stack.pop();
            boolean tip = g.depth() <= 0;
            int end = chain(g.from(), g.way(), g.length(), Skeleton.BRANCH, g.limb(), tip ? hang : arch, true);
            if (tip || end == g.from() || s.y(end) < 1.5) continue;
            double[] endWay = way(end);
            int count = Math.max(2, p.branches() - 1);
            double childRoll = random.nextDouble() * Math.PI * 2.0;
            for (int child = 0; child < count; child++) {
                double[] way = turned(endWay, Math.toRadians(p.spread()) * 0.5 * (0.8 + 0.4 * random.nextDouble()), childRoll + child * Math.PI * 2.0 / count);
                // (the next ones lean out and down more, the last hang)
                way[1] -= g.depth() <= 1 ? 0.6 : 0.2;
                double length = g.length() * (g.depth() <= 1 ? 1.1 : 0.7) * (0.85 + 0.3 * random.nextDouble());
                stack.push(new Grow(end, normal(way), length, g.depth() - 1, g.limb()));
            }
        }
    }

    /** A cherry: a short leaning trunk that soon forks into wide, nearly level limbs; their branches spread out and turn up at the tips (a wide umbrella). */
    private void cherry(int base) {
        int top = trunk(base, forkHeight(0.2 + p.curl() * 0.25), 0.2 + p.curl() * 0.25, p.curl() * 0.2);
        Deque<Grow> stack = new ArrayDeque<>();
        double roll = random.nextDouble() * Math.PI * 2.0;
        int limbs = Math.max(2, p.branches());
        for (int limb = 0; limb < limbs; limb++) {
            double[] way = turned(new double[]{0.0, 1.0, 0.0}, Math.toRadians(Math.max(45.0, p.spread() * 0.85)) * (0.9 + 0.2 * random.nextDouble()), roll + limb * Math.PI * 2.0 / limbs + (random.nextDouble() - 0.5) * 0.5);
            stack.push(new Grow(top, way, p.length() * (0.9 + 0.2 * random.nextDouble()), p.depth() - 1, s.newLimb()));
        }
        Bend bend = new Bend(p.curl() * 0.15, 0.4, 0.02 + p.droop() * 0.08, 0.04, 0.0, 0.05, 2.0, false);
        Bend last = new Bend(p.curl() * 0.15, 0.4, p.droop() * 0.05, 0.1, p.tipCurl() * 0.6, 0.05, 2.0, false);
        forks(stack, bend, last, p.spread() * 0.8, 0.7);
    }

    /**
     * An acacia: a bare leaning trunk to more than half its height, then a few limbs going up and out at a slant, each bending up once halfway (an elbow), forking
     * near the top into short twigs under the flat crown.
     */
    private void acacia(int base) {
        int top = trunk(base, forkHeight(0.15 + p.curl() * 0.2), 0.15 + p.curl() * 0.2, p.curl() * 0.1);
        double roll = random.nextDouble() * Math.PI * 2.0;
        int limbs = Math.max(2, p.branches());
        Deque<Grow> stack = new ArrayDeque<>();
        for (int limb = 0; limb < limbs; limb++) {
            double angle = roll + limb * Math.PI * 2.0 / limbs + (random.nextDouble() - 0.5) * 0.5;
            double[] out = turned(new double[]{0.0, 1.0, 0.0}, Math.toRadians(Math.max(35.0, p.spread())) * (1.0 + 0.3 * random.nextDouble()), angle);
            Bend straight = new Bend(p.curl() * 0.04, 0.4, 0.0, 0.0, 0.0, 0.03, 2.0, false);
            int elbow = chain(top, out, p.length() * 0.55, Skeleton.BRANCH, s.newLimb(), straight, true);
            if (elbow == top) continue;
            double[] up = turned(new double[]{0.0, 1.0, 0.0}, Math.toRadians(20.0 + 15.0 * random.nextDouble()), angle);
            stack.push(new Grow(elbow, up, p.length() * 0.5, p.depth() - 1, s.limb(elbow)));
        }
        Bend bend = new Bend(p.curl() * 0.04, 0.4, 0.0, 0.0, 0.0, 0.03, 2.0, false);
        Bend last = new Bend(p.curl() * 0.04, 0.4, 0.05, 0.0, 0.0, 0.03, 1.5, false);
        forks(stack, bend, last, Math.max(35.0, p.spread()), 0.6);
    }

    /**
     * Lays the roots on the ground: away from the trunk (over two blocks past its radius) each root node goes to the top of the ground in its column, sunk to a third
     * of its radius above it; near the trunk it is between where it grew and that. A column with no ground known keeps its node.
     */
    private void snapRoots(Ground ground) {
        double radius = s.radius(0);
        for (int node = 1; node < s.size(); node++) {
            if (s.kind(node) != Skeleton.ROOT) continue;
            int top = ground.top((int) Math.floor(s.x(node)), (int) Math.floor(s.z(node)));
            if (top == Ground.NONE) continue;
            double out = Math.hypot(s.x(node) - 0.5, s.z(node) - 0.5);
            double weight = Math.max(0.0, Math.min(1.0, (out - radius * 0.8) / 2.0));
            double surface = top + 1.0 + s.radius(node) * 0.35;
            s.moveY(node, s.y(node) * (1.0 - weight) + surface * weight);
        }
    }

    /** The roots: from the foot out and down to the ground, bending, each forking once on the way. */
    private void roots(int base) {
        int count = p.roots();
        if (count <= 0 || p.rootReach() <= 0.0) return;
        double start = random.nextDouble() * Math.PI * 2.0;
        double trunk = p.trunk();
        Bend bend = new Bend(p.curl() * 0.15, 0.35, 0.0, 0.0, 0.0, 0.05, 1.5, false);
        for (int root = 0; root < count; root++) {
            double angle = start + root * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.6;
            double reach = p.rootReach() * (0.75 + 0.5 * random.nextDouble());
            int limb = s.newLimb();
            double sx = 0.5 + Math.cos(angle) * trunk * 0.35;
            double sz = 0.5 + Math.sin(angle) * trunk * 0.35;
            int first = s.add(sx, Math.max(0.6, trunk * 0.55 + 0.3), sz, base, Skeleton.ROOT, limb);
            double[] way = normal(new double[]{Math.cos(angle), -0.45, Math.sin(angle)});
            int end = rootChain(first, way, reach * 0.6, limb, bend);
            if (end == first) continue;
            double[] endWay = way(end);
            for (int side = -1; side <= 1; side += 2) {
                double h = Math.atan2(endWay[2], endWay[0]) + side * (0.3 + 0.3 * random.nextDouble());
                double[] next = normal(new double[]{Math.cos(h), -0.08, Math.sin(h)});
                rootChain(end, next, reach * (side < 0 ? 0.45 : 0.3), limb, bend);
            }
        }
    }

    private int rootChain(int from, double[] way, double length, int limb, Bend bend) {
        return chain(from, way, length, Skeleton.ROOT, limb, bend, true);
    }

    // ---- one stretch of wood ------------------------------------------------------------------------------------------------------------

    /**
     * Grows a stretch from node {@code from} along {@code way}, about a block a step, {@code length} long; returns its last node ({@code from} when it could not grow
     * at all). The way swirls round itself ({@code swirl}, turning {@code swirlRate} a step), sags ({@code droop}) or rises ({@code upturn}), wanders a little, a
     * last branch curls its tip up and over ({@code tipCurl}, from half its length). A root keeps near the ground. With
     * {@code spacing}, a step that comes within {@code gap} of other wood tries other ways, and the stretch stops if none is free.
     */
    private int chain(int from, double[] start, double length, int kind, int limb, Bend bend, boolean spacing) {
        int steps = Math.max(1, (int) Math.round(length));
        double step = length / steps;
        double[] d = start.clone();
        double phase = random.nextDouble() * Math.PI * 2.0;
        double[] curlAxis = null;
        int at = from;
        for (int index = 0; index < steps; index++) {
            double t = (index + 1.0) / steps;
            double[][] across = bend.upright() ? new double[][]{{1.0, 0.0, 0.0}, {0.0, 0.0, 1.0}} : across(d);
            phase += bend.swirlRate();
            for (int k = 0; k < 3; k++) d[k] += bend.swirl() * (Math.cos(phase) * across[0][k] + Math.sin(phase) * across[1][k]);
            for (int k = 0; k < 3; k++) d[k] += (random.nextDouble() - 0.5) * 2.0 * bend.wander();
            d[1] -= bend.droop() * (0.5 + t);
            d[1] += bend.upturn() * (1.0 - d[1]);
            if (bend.tipCurl() > 0.0 && t > 0.5) {
                if (curlAxis == null) {
                    double[] axis = cross(normal(d), new double[]{0.0, 1.0, 0.0});
                    curlAxis = norm(axis) < 0.2 ? across[0] : normal(axis);
                }
                d = rotate(normal(d), curlAxis, bend.tipCurl() * 0.5);
            }
            if (kind == Skeleton.ROOT) {
                // (a root flattens out toward the ground and does not go deep)
                d[1] += (-0.06 - d[1]) * 0.35;
                if (s.y(at) < 0.1) d[1] = Math.max(d[1], 0.0);
            }
            d = normal(d);
            double nx = s.x(at) + d[0] * step;
            double ny = s.y(at) + d[1] * step;
            double nz = s.z(at) + d[2] * step;
            // (a branch that comes down to the ground ends there)
            if (kind != Skeleton.ROOT && ny < 0.6) break;
            if (spacing && !s.clear(at, nx, ny, nz, bend.gap())) {
                double[] free = null;
                for (int attempt = 0; attempt < 6 && free == null; attempt++) {
                    double[] other = turned(d, Math.toRadians(20 + 10 * attempt), random.nextDouble() * Math.PI * 2.0);
                    double ox = s.x(at) + other[0] * step;
                    double oy = s.y(at) + other[1] * step;
                    double oz = s.z(at) + other[2] * step;
                    if ((kind == Skeleton.ROOT || oy >= 0.6) && s.clear(at, ox, oy, oz, bend.gap())) free = other;
                }
                if (free == null) break;
                d = free;
                nx = s.x(at) + d[0] * step;
                ny = s.y(at) + d[1] * step;
                nz = s.z(at) + d[2] * step;
            }
            at = s.add(nx, ny, nz, at, kind, limb);
        }
        return at;
    }

    /**
     * (t288) The length of a trunk that forks into its limbs at the top (a willow, a cherry, an acacia): its height, or the length that leaning {@code lean} rises to
     * the first branch's height when it is given.
     */
    private double forkHeight(double lean) {
        return p.branchStart() > 0.0 ? climb(p.branchStart(), lean) : p.height();
    }

    /** (t288) How long a trunk leaning {@code lean} (sideways to up) grows to rise {@code rise}. */
    private static double climb(double rise, double lean) {
        return rise * Math.sqrt(1.0 + lean * lean);
    }

    /** (t288) The share of the way from {@code base} up to {@code top} that height {@code y} is (for {@link #trunkNodeAt}). */
    private double shareOf(int base, int top, double y) {
        return (y - s.y(base)) / Math.max(1.0, s.y(top) - s.y(base));
    }

    /** The trunk node about {@code share} of the way from {@code base} up to {@code top}. */
    private int trunkNodeAt(int base, int top, double share) {
        double wanted = s.y(base) + (s.y(top) - s.y(base)) * share;
        int best = top;
        double bestGap = Double.MAX_VALUE;
        for (int node = base; node <= top; node++) {
            if (s.kind(node) != Skeleton.TRUNK) continue;
            double gap = Math.abs(s.y(node) - wanted);
            if (gap < bestGap) {
                bestGap = gap;
                best = node;
            }
        }
        return best;
    }

    /** The way the wood goes at {@code node} (from its parent; up at the base). */
    private double[] way(int node) {
        int from = s.parent(node);
        if (from < 0) return new double[]{0.0, 1.0, 0.0};
        return normal(new double[]{s.x(node) - s.x(from), s.y(node) - s.y(from), s.z(node) - s.z(from)});
    }

    // ---- vectors -------------------------------------------------------------------------------------------------------------------------

    /** {@code way} turned away from itself by {@code angle}, toward the side {@code roll} round it. */
    static double[] turned(double[] way, double angle, double roll) {
        double[] w = normal(way);
        double[][] across = across(w);
        double c = Math.cos(angle);
        double sn = Math.sin(angle);
        double[] out = new double[3];
        for (int k = 0; k < 3; k++) out[k] = w[k] * c + (across[0][k] * Math.cos(roll) + across[1][k] * Math.sin(roll)) * sn;
        return normal(out);
    }

    /** Two unit vectors across {@code way} and across each other. */
    static double[][] across(double[] way) {
        double[] w = normal(way);
        double[] helper = Math.abs(w[1]) < 0.9 ? new double[]{0.0, 1.0, 0.0} : new double[]{1.0, 0.0, 0.0};
        double[] a = normal(cross(w, helper));
        double[] b = normal(cross(w, a));
        return new double[][]{a, b};
    }

    /** {@code v} turned by {@code angle} round the unit {@code axis} (Rodrigues). */
    static double[] rotate(double[] v, double[] axis, double angle) {
        double c = Math.cos(angle);
        double sn = Math.sin(angle);
        double[] k = axis;
        double dot = k[0] * v[0] + k[1] * v[1] + k[2] * v[2];
        double[] kv = cross(k, v);
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) out[i] = v[i] * c + kv[i] * sn + k[i] * dot * (1 - c);
        return out;
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double norm(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    static double[] normal(double[] v) {
        double length = norm(v);
        if (length < 1.0e-9) return new double[]{0.0, 1.0, 0.0};
        return new double[]{v[0] / length, v[1] / length, v[2] / length};
    }

    private static double sq(double value) {
        return value * value;
    }
}
