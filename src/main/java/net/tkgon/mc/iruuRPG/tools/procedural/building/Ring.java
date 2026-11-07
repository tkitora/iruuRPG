package net.tkgon.mc.iruuRPG.tools.procedural.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (t295) The walls of one storey cut into bays: every cell of the wall line (depth 1) knows the side it is on, how far along it ({@code t}), whether a post
 * stands there and the bay it is in; the cells behind it (depth 2, the wall, and 3, the glass) are the bay of the wall-line cell they are behind. A side is
 * [post | bay | post | ... | post]: posts at its two ends and between them as the plan's posts say; on a circle the posts stand at the same angles all the way up.
 */
public final class Ring {

    /** Where a cell of the walls is: on side {@code side}, {@code t} along it, in the bay from post {@code from} to post {@code to}; {@code (nx, nz)} out of the plan. */
    public record Spot(int side, double t, boolean post, double from, double to, int nx, int nz, boolean corner) {

        public double middle() {
            return (from + to) / 2.0;
        }

        /** The cells of the bay between its posts. */
        public double span() {
            return to - from - 1.0;
        }

        /** Whether the cell is among the middle {@code width} cells of its bay (and not a post). */
        public boolean within(int width) {
            if (post || width <= 0) return false;
            return Math.abs(t - middle()) <= (width - 1) / 2.0 + 0.01;
        }
    }

    private final Footprint plan;
    private final Map<Long, Spot> spots = new HashMap<>();
    /** The posts of each side (t), for the storeys above. */
    private final List<double[]> posts = new ArrayList<>();

    private Ring(Footprint plan) {
        this.plan = plan;
    }

    public Footprint plan() {
        return plan;
    }

    public Spot spot(int x, int z) {
        return spots.get(key(x, z));
    }

    public List<double[]> posts() {
        return posts;
    }

    private static long key(int x, int z) {
        return ((long) (x + 100_000) << 32) | (z + 100_000);
    }

    /**
     * The ring of a ground storey: the posts of each straight side every {@code module} steps from its ends (a side n * module + 1 long has a post every
     * module), of a slanting side as evenly near a module as can be; a circle's posts at {@code circlePosts} angles evenly round, half a bay off the front.
     */
    public static Ring ground(Footprint plan, int module, int circlePosts) {
        return ground(plan, module, false, circlePosts);
    }

    /**
     * (t302) The ring of a ground storey whose posts stand every {@code spacing} steps along each straight side from its start (0: at the corners only); with
     * {@code mirror}, from both its ends toward its middle, the same both ways. A slanting side has its posts as evenly as can be about a spacing apart.
     */
    public static Ring ground(Footprint plan, int spacing, boolean mirror, int circlePosts) {
        Ring ring = new Ring(plan);
        if (plan.circle()) {
            ring.circle(circlePosts);
            return ring;
        }
        for (Footprint.Edge edge : plan.edges()) {
            int length = edge.length();
            List<Double> at = new ArrayList<>();
            if (spacing <= 0) {
                at.add(0.0);
                at.add((double) length);
            } else if (edge.diagonal()) {
                double step = spacing / Math.sqrt(2.0);
                int bays = Math.max(1, (int) Math.round(length / step));
                for (int i = 0; i <= bays; i++) at.add((double) Math.round((double) i * length / bays));
            } else if (mirror) {
                for (int t = 0; 2 * t <= length; t += spacing) at.add((double) t);
                for (int t = length; 2 * t > length; t -= spacing) at.add((double) t);
                at.sort(Double::compare);
            } else {
                for (int t = 0; t < length; t += spacing) at.add((double) t);
                at.add((double) length);
            }
            double[] array = new double[at.size()];
            for (int i = 0; i < array.length; i++) array[i] = at.get(i);
            ring.posts.add(array);
        }
        ring.fill();
        return ring;
    }

    /**
     * The ring of a storey whose plan is {@code below}'s grown out ({@code plan}): each side keeps the posts of the same side below where they are (moved by how
     * far its start moved along it), and has posts at its own two ends; the posts below that would stand next to a new corner are left out.
     */
    public static Ring above(Ring below, Footprint plan, int circlePosts) {
        Ring ring = new Ring(plan);
        if (plan.circle()) {
            ring.circle(circlePosts);
            return ring;
        }
        List<Footprint.Edge> under = below.plan.edges();
        List<Footprint.Edge> edges = plan.edges();
        for (Footprint.Edge edge : edges) {
            Footprint.Edge match = null;
            int matchIndex = -1;
            double best = Double.MAX_VALUE;
            for (int index = 0; index < under.size(); index++) {
                Footprint.Edge other = under.get(index);
                if (other.dx() != edge.dx() || other.dz() != edge.dz()) continue;
                // (the lines are apart along the inward step; the nearest of the same way is the side it grew from)
                double apart = Math.abs((edge.x() - other.x()) * edge.inX() + (edge.z() - other.z()) * edge.inZ());
                double along = Math.abs((edge.x() - other.x()) * edge.dx() + (edge.z() - other.z()) * edge.dz());
                double score = apart * 100 + along;
                if (score < best) {
                    best = score;
                    match = other;
                    matchIndex = index;
                }
            }
            List<Double> at = new ArrayList<>();
            at.add(0.0);
            if (match != null) {
                int unit = edge.diagonal() ? 2 : 1;
                double shift = ((match.x() - edge.x()) * edge.dx() + (match.z() - edge.z()) * edge.dz()) / (double) unit;
                double[] old = below.posts.get(matchIndex);
                for (int i = 1; i < old.length - 1; i++) {
                    double t = old[i] + shift;
                    if (t >= 2.0 && t <= edge.length() - 2.0) at.add(t);
                }
            }
            at.add((double) edge.length());
            double[] array = new double[at.size()];
            for (int i = 0; i < array.length; i++) array[i] = at.get(i);
            ring.posts.add(array);
        }
        ring.fill();
        return ring;
    }

    /** Gives every cell of the wall line its spot from the sides and their posts, then the cells behind the wall line theirs. */
    private void fill() {
        List<Footprint.Edge> edges = plan.edges();
        // the cells on the sides' lines first (a corner is the side that starts there), then the second cells of the slanting sides' steps
        for (int pass = 0; pass < 2; pass++) {
            for (int side = 0; side < edges.size(); side++) {
                Footprint.Edge edge = edges.get(side);
                if (pass == 1 && !edge.diagonal()) continue;
                double[] at = posts.get(side);
                for (int t = 0; t <= edge.length(); t++) {
                    int[] cell = edge.at(t);
                    int x = cell[0];
                    int z = cell[1];
                    if (pass == 1) {
                        // (the block beside the step, into the plan along x: the slanting wall is two blocks a step)
                        x += edge.inX();
                    }
                    if (plan.depth(x, z) != 1 || spots.containsKey(key(x, z))) continue;
                    spots.put(key(x, z), spot(side, t, at, -edge.inX(), -edge.inZ(), t == 0 || t == edge.length()));
                }
            }
        }
        spread();
    }

    private static Spot spot(int side, double t, double[] at, int nx, int nz, boolean corner) {
        boolean post = false;
        double from = at[0];
        double to = at[at.length - 1];
        for (int i = 0; i < at.length; i++) {
            if (Math.abs(at[i] - t) < 0.5) post = true;
            if (at[i] <= t) from = at[i];
            if (at[i] >= t) {
                to = at[i];
                break;
            }
        }
        if (post) {
            from = t;
            to = t;
        }
        return new Spot(side, t, post, from, to, nx, nz, corner);
    }

    /** A circle's wall line cut by angle: the posts at {@code count} angles evenly round (half a bay off the front, +z), the arc along from one to the next. */
    private void circle(int count) {
        int n = Math.max(4, count);
        double cx = plan.centerX();
        double cz = plan.centerZ();
        double r = plan.radius();
        double sector = Math.PI * 2.0 / n;
        // (the front, +z, is angle pi/2; a bay is centred on it)
        double first = Math.PI / 2.0 + sector / 2.0;
        double[] at = new double[n + 1];
        for (int i = 0; i <= n; i++) at[i] = i * sector * r;
        posts.add(at);
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            double angle = Math.atan2(cell[1] - cz, cell[0] - cx);
            double along = Math.floorMod((long) Math.floor((angle - first) / (Math.PI * 2.0) * 1_000_000), 1_000_000) / 1_000_000.0 * Math.PI * 2.0;
            double t = along * r;
            int index = (int) Math.floor(along / sector);
            double from = index * sector * r;
            double to = (index + 1) * sector * r;
            boolean post = t - from < 0.6 || to - t < 0.6;
            double length = Math.hypot(cell[0] - cx, cell[1] - cz);
            int nx = (int) Math.round((cell[0] - cx) / Math.max(1.0, length));
            int nz = (int) Math.round((cell[1] - cz) / Math.max(1.0, length));
            spots.put(key(cell[0], cell[1]), post ? new Spot(0, t, true, t, t, nx, nz, false) : new Spot(0, t, false, from, to, nx, nz, false));
        }
        spread();
    }

    /** The cells of the wall line left without a spot take one beside them; the cells behind (depth 2, 3) the spot of the one in front of them. */
    private void spread() {
        for (int depth = 1; depth <= 3; depth++) {
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int[] cell : plan.cells()) {
                    if (plan.depth(cell[0], cell[1]) != depth || spots.containsKey(key(cell[0], cell[1]))) continue;
                    Spot near = null;
                    for (int pass = 0; pass < 2 && near == null; pass++) {
                        for (int dx = -1; dx <= 1 && near == null; dx++) {
                            for (int dz = -1; dz <= 1 && near == null; dz++) {
                                if ((dx == 0) == (dz == 0) && pass == 0) continue;
                                if (dx == 0 && dz == 0) continue;
                                int d = plan.depth(cell[0] + dx, cell[1] + dz);
                                if (d != depth - 1 && !(d == depth && depth == 1)) continue;
                                near = spots.get(key(cell[0] + dx, cell[1] + dz));
                            }
                        }
                    }
                    if (near != null) {
                        spots.put(key(cell[0], cell[1]), near);
                        changed = true;
                    }
                }
            }
        }
    }
}
