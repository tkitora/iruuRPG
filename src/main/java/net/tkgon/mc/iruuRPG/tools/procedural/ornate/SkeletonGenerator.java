package net.tkgon.mc.iruuRPG.tools.procedural.ornate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Grows the bones of an ornate tree by its style: 0 the ornate fractal (the canopy tree's fractal of forks, with swirling limbs and tips that curl up into a scroll),
 * 1 spreading (a bonsai or a pine: a leaning S trunk and flat tiers of nearly level branches), 2 natural (space colonization: the branches grow toward points
 * scattered in the crown), 3 dead (the fractal, gnarled and kinked, with broken stubs and sharp tips). The roots are grown the same way, down and out from the foot.
 * Then the radii by the pipe model ({@link Skeleton#pipe}).
 */
public final class SkeletonGenerator {

    /** The numbers the bones are grown by. */
    public record Params(int style, double height, double trunk, double curl, int roots, double rootReach, int branches, int depth, double length, double spread,
                         double droop, double tipCurl, double pipe) {
    }

    /**
     * How a stretch of wood bends while it grows: it swirls ({@code swirl} a step, the way turning {@code swirlRate} a step; round itself, or for the trunk round the
     * upright, so it sways round and stays up), sags or rises, curls its tip, kinks, wanders, and keeps {@code gap} from other wood.
     */
    private record Bend(double swirl, double swirlRate, double droop, double upturn, double tipCurl, double kink, double wander, double gap, boolean upright) {
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

    private void grow() {
        int base = s.add(0.5, 0.0, 0.5, -1, Skeleton.TRUNK, 0);
        switch (p.style()) {
            case 1 -> spreading(base);
            case 2 -> natural(base);
            default -> fractal(base, p.style() == 3);
        }
        roots(base);
        // (the fork of a dead tree tapers less: its stubs stay thick)
        s.pipe(p.pipe(), p.style() == 3 ? 0.01 : 0.025, p.trunk(), 1.0);
    }

    // ---- the styles ------------------------------------------------------------------------------------------------------------------

    /** A branch waiting to be grown. */
    private record Grow(int from, double[] way, double length, int depth, int limb) {
    }

    /** The ornate fractal (and the dead tree): a swirling trunk that forks into limbs, limbs that fork again {@code depth} times. */
    private void fractal(int base, boolean dead) {
        double lean = 0.08 + p.curl() * 0.12;
        double turn = random.nextDouble() * Math.PI * 2.0;
        double[] up = normal(new double[]{Math.cos(turn) * lean, 1.0, Math.sin(turn) * lean});
        Bend trunkBend = new Bend(p.curl() * 0.18, 0.3, 0.0, 0.12, 0.0, dead ? 0.06 : 0.0, 0.04, 0.0, true);
        int top = chain(base, up, p.height(), Skeleton.TRUNK, 0, trunkBend, false);
        Deque<Grow> stack = new ArrayDeque<>();
        double[] topWay = way(top);
        double roll = random.nextDouble() * Math.PI * 2.0;
        int forks = Math.max(2, p.branches());
        for (int limb = 0; limb < forks; limb++) {
            double[] way = turned(topWay, Math.toRadians(p.spread()) * (0.85 + 0.3 * random.nextDouble()), roll + limb * Math.PI * 2.0 / forks + (random.nextDouble() - 0.5) * 0.4);
            stack.push(new Grow(top, way, p.length() * (0.9 + 0.2 * random.nextDouble()), p.depth() - 1, s.newLimb()));
        }
        // limbs lower on the trunk (an ornate tree is not a broom)
        int sides = p.depth() >= 3 && p.height() >= 5 ? (dead ? 2 : 1 + (p.branches() >= 3 ? 1 : 0)) : 0;
        for (int side = 0; side < sides; side++) {
            int at = trunkNodeAt(base, top, (sides == 1 ? 0.62 : 0.5 + 0.3 * side / (sides - 1.0)) + 0.05 * random.nextDouble());
            double angle = roll + Math.PI / forks + side * SPIRAL * 2.0;
            double[] way = normal(new double[]{Math.cos(angle), 0.55 + 0.3 * random.nextDouble(), Math.sin(angle)});
            stack.push(new Grow(at, way, p.length() * 0.7, p.depth() - 2, s.newLimb()));
        }
        double lengthMult = dead ? 0.68 : 0.72;
        while (!stack.isEmpty()) {
            Grow g = stack.pop();
            boolean last = g.depth() <= 0;
            Bend bend = dead
                    ? new Bend(p.curl() * 0.06, 0.6, p.droop() * 0.1 + 0.03, 0.03, 0.0, 0.2, 0.06, 2.0, false)
                    : new Bend(p.curl() * 0.22, 0.45, p.droop() * 0.1, 0.07, last ? p.tipCurl() : 0.0, 0.0, 0.05, 2.0, false);
            int end = chain(g.from(), g.way(), g.length(), Skeleton.BRANCH, g.limb(), bend, true);
            if (last || end == g.from() || s.y(end) < 0.5) continue;
            double[] endWay = way(end);
            int count = Math.max(2, p.branches() - (random.nextDouble() < 0.25 ? 1 : 0));
            double childRoll = random.nextDouble() * Math.PI * 2.0;
            for (int child = 0; child < count; child++) {
                double spread = Math.toRadians(p.spread()) * (dead ? 1.15 : 1.0) * (0.8 + 0.4 * random.nextDouble());
                double[] way = turned(endWay, spread, childRoll + child * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.5);
                double length = g.length() * lengthMult * (0.85 + 0.25 * random.nextDouble());
                int depth = g.depth() - 1;
                // (a dead tree's broken branches: short stubs that end there)
                if (dead && random.nextDouble() < 0.22) {
                    length *= 0.15 + 0.2 * random.nextDouble();
                    depth = 0;
                }
                if (length >= 1.0) stack.push(new Grow(end, way, length, depth, g.limb()));
            }
        }
    }

    /** A bonsai or a pine: a leaning S trunk, tiers of nearly level branches from a third of the way up (longer below), their twigs spread level, a short top. */
    private void spreading(int base) {
        double turn = random.nextDouble() * Math.PI * 2.0;
        double lean = 0.35 + p.curl() * 0.35;
        double[] up = normal(new double[]{Math.cos(turn) * lean, 1.0, Math.sin(turn) * lean});
        Bend trunkBend = new Bend(p.curl() * 0.22, 0.22, 0.0, 0.1, 0.0, 0.0, 0.03, 0.0, true);
        int top = chain(base, up, p.height(), Skeleton.TRUNK, 0, trunkBend, false);
        int tiers = Math.max(2, p.branches() * 2);
        double angle = random.nextDouble() * Math.PI * 2.0;
        Deque<Grow> stack = new ArrayDeque<>();
        for (int tier = 0; tier < tiers; tier++) {
            double share = 0.35 + 0.6 * tier / (tiers - 1.0);
            int at = trunkNodeAt(base, top, share);
            angle += SPIRAL * 2.0 + (random.nextDouble() - 0.5) * 0.4;
            double[] way = normal(new double[]{Math.cos(angle), -0.05 + 0.1 * random.nextDouble(), Math.sin(angle)});
            stack.push(new Grow(at, way, p.length() * (1.15 - 0.6 * share) * (0.85 + 0.3 * random.nextDouble()), p.depth() - 1, s.newLimb()));
        }
        stack.push(new Grow(top, normal(new double[]{0.15, 1.0, 0.0}), p.length() * 0.35, Math.min(1, p.depth() - 1), s.newLimb()));
        while (!stack.isEmpty()) {
            Grow g = stack.pop();
            boolean last = g.depth() <= 0;
            Bend bend = new Bend(p.curl() * 0.08, 0.3, p.droop() * 0.1 + 0.02, 0.0, last ? p.tipCurl() * 0.4 : 0.0, 0.0, 0.04, 2.0, false);
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
     * A natural tree by space colonization: points are scattered in a crown (an egg {@code length} round above the trunk); each step, every point pulls the node
     * nearest it (within the reach), a pulled node grows a block toward the mean of its points, and points a new node comes near are used up.
     */
    private void natural(int base) {
        double[] up = normal(new double[]{(random.nextDouble() - 0.5) * 0.1, 1.0, (random.nextDouble() - 0.5) * 0.1});
        int top = chain(base, up, p.height(), Skeleton.TRUNK, 0, new Bend(p.curl() * 0.12, 0.3, 0.0, 0.12, 0.0, 0.0, 0.04, 0.0, true), false);
        double radius = Math.max(3.0, p.length());
        double cx = s.x(top);
        double cy = s.y(top) + radius * 0.7;
        double cz = s.z(top);
        int count = Math.max(150, Math.min(900, p.branches() * 120 + (int) (radius * radius * 2)));
        List<double[]> points = new ArrayList<>();
        int tries = 0;
        while (points.size() < count && tries++ < count * 20) {
            double x = (random.nextDouble() * 2 - 1);
            double y = (random.nextDouble() * 2 - 1);
            double z = (random.nextDouble() * 2 - 1);
            if (x * x + y * y + z * z > 1.0) continue;
            double py = cy + y * radius * 0.8;
            if (py < s.y(top) + 1.0) continue;
            points.add(new double[]{cx + x * radius, py, cz + z * radius});
        }
        double reach = Math.max(6.0, radius * 0.9);
        double kill = 2.0;
        int size = points.size();
        int[] nearest = new int[size];
        double[] nearestDistance = new double[size];
        boolean[] used = new boolean[size];
        java.util.Arrays.fill(nearest, -1);
        java.util.Arrays.fill(nearestDistance, Double.MAX_VALUE);
        int checked = 0;
        for (int round = 0; round < 260; round++) {
            int nodes = s.size();
            // (only the nodes added since the last step can be nearer than the nearest known)
            for (int index = 0; index < size; index++) {
                if (used[index]) continue;
                double[] point = points.get(index);
                for (int node = checked; node < nodes; node++) {
                    if (s.kind(node) == Skeleton.ROOT) continue;
                    double d = Math.sqrt(sq(point[0] - s.x(node)) + sq(point[1] - s.y(node)) + sq(point[2] - s.z(node)));
                    if (d < nearestDistance[index]) {
                        nearestDistance[index] = d;
                        nearest[index] = node;
                    }
                }
                if (nearestDistance[index] < kill) used[index] = true;
            }
            checked = nodes;
            java.util.Map<Integer, double[]> pulls = new java.util.LinkedHashMap<>();
            for (int index = 0; index < size; index++) {
                if (used[index] || nearest[index] < 0 || nearestDistance[index] > reach) continue;
                int node = nearest[index];
                double[] point = points.get(index);
                double[] pull = pulls.computeIfAbsent(node, key -> new double[3]);
                double d = Math.max(1.0e-6, nearestDistance[index]);
                pull[0] += (point[0] - s.x(node)) / d;
                pull[1] += (point[1] - s.y(node)) / d;
                pull[2] += (point[2] - s.z(node)) / d;
            }
            if (pulls.isEmpty()) break;
            boolean grew = false;
            for (java.util.Map.Entry<Integer, double[]> entry : pulls.entrySet()) {
                int node = entry.getKey();
                double[] pull = entry.getValue();
                double[] way = normal(new double[]{pull[0], pull[1] + 0.25, pull[2]});
                double nx = s.x(node) + way[0];
                double ny = s.y(node) + way[1];
                double nz = s.z(node) + way[2];
                if (ny < 0.6 || !s.clear(node, nx, ny, nz, 1.6) || closeChild(node, nx, ny, nz)) continue;
                int limb = s.kind(node) == Skeleton.TRUNK ? s.newLimb() : s.limb(node);
                s.add(nx, ny, nz, node, Skeleton.BRANCH, limb);
                grew = true;
            }
            if (!grew) break;
            if (s.size() > 6000) break;
        }
    }

    /** Whether {@code node} has a child near (x, y, z) already (a node pulled the same way twice). */
    private boolean closeChild(int node, double x, double y, double z) {
        for (int child : s.children(node)) {
            if (sq(s.x(child) - x) + sq(s.y(child) - y) + sq(s.z(child) - z) < 0.5) return true;
        }
        return false;
    }

    /** The roots: from the foot out and down to the ground, bending, each forking once on the way. */
    private void roots(int base) {
        int count = p.roots();
        if (count <= 0 || p.rootReach() <= 0.0) return;
        double start = random.nextDouble() * Math.PI * 2.0;
        double trunk = p.trunk();
        Bend bend = new Bend(p.curl() * 0.15, 0.35, 0.0, 0.0, 0.0, p.style() == 3 ? 0.1 : 0.0, 0.05, 1.5, false);
        for (int root = 0; root < count; root++) {
            double angle = start + root * Math.PI * 2.0 / count + (random.nextDouble() - 0.5) * 0.6;
            double reach = p.rootReach() * (0.75 + 0.5 * random.nextDouble()) * (p.style() == 3 ? 1.2 : 1.0);
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
     * dead tree's kinks sharply now and then; a last branch curls its tip up and over ({@code tipCurl}, from half its length). A root keeps near the ground. With
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
            if (bend.kink() > 0.0 && random.nextDouble() < bend.kink()) {
                double[] kinked = turned(d, Math.toRadians(25 + random.nextDouble() * 20), random.nextDouble() * Math.PI * 2.0);
                d = kinked;
            }
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
            if (kind != Skeleton.ROOT && ny < 0.6) {
                d[1] = Math.abs(d[1]);
                d = normal(d);
                ny = s.y(at) + d[1] * step;
            }
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
