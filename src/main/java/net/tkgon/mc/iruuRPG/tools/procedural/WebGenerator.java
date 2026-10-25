package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The spider web of the procedural tool: threads of cobweb between points chosen in the air, laid by the kind of web, and fixed to the terrain round the points. Pure arithmetic over
 * a snapshot of the terrain ({@link WebTerrain}): it runs off the main thread. Only air is ever used: a thread that comes to a block that is not air stops at the last block of air
 * before it, and that place is a contact with the surface, taken as a point of the web from then on (a thread that comes out of a wall on the other side starts again there, and the
 * contacts are tied to the nearest point or contact). The blocks are given relative to the first point.
 *
 * <p>The kinds: <b>orb</b> (円網) a hub, spokes to a frame round the points and a spiral between them; <b>sheet</b> (棚網) a flat sheet of crossing threads inside the frame;
 * <b>bowl</b> (皿網) the same sagging into a dish; <b>funnel</b> (漏斗網) a mouth narrowing to the lowest point with rings and threads down to it; <b>irregular</b> (不規則網) a tangle
 * of threads between the points and nodes in the space round them; <b>strand</b> (条網) bands of parallel threads with rungs from point to point.
 */
final class WebGenerator {

    private interface Drop {
        double at(double[] point, double t);
    }

    private final TreeSettings settings;
    private final WebInput input;
    private final Random random;
    private final int bx;
    private final int by;
    private final int bz;
    private final Map<Long, TreePart> blocks = new HashMap<>();
    private final List<int[]> contacts = new ArrayList<>();
    private final int reach;
    private final double sag;
    private final double irregularity;
    private final int rings;
    private final int spokes;

    // the frame of the points (set by polygon)
    private double[] centre = {0, 0, 0};
    private double[] axisU = {1, 0, 0};
    private double[] axisV = {0, 0, 1};
    private double meanRadius = 1.0;

    private WebGenerator(TreeSettings settings, WebInput input, Random random) {
        this.settings = settings;
        this.input = input;
        this.random = random;
        int[] first = input.points().get(0);
        this.bx = first[0];
        this.by = first[1];
        this.bz = first[2];
        this.reach = settings.getInt(TreeSettings.Key.WEB_REACH);
        this.sag = settings.get(TreeSettings.Key.WEB_SAG);
        this.irregularity = settings.get(TreeSettings.Key.WEB_IRREG);
        this.rings = settings.getInt(TreeSettings.Key.WEB_RINGS);
        this.spokes = settings.getInt(TreeSettings.Key.WEB_SPOKES);
    }

    /** The web of the settings between the points of the input, by part, relative to the first point. Empty with fewer than two points. */
    static Map<Long, TreePart> build(TreeSettings settings, WebInput input, Random random) {
        if (input == null || input.points().size() < 2) return Map.of();
        WebGenerator generator = new WebGenerator(settings, input, random);
        generator.make();
        return generator.blocks;
    }

    private void make() {
        List<double[]> points = new ArrayList<>();
        for (int[] point : input.points()) points.add(new double[]{point[0], point[1], point[2]});
        switch (settings.webShape()) {
            case "sheet" -> sheet(points, false);
            case "bowl" -> sheet(points, true);
            case "funnel" -> funnel(points);
            case "irregular" -> irregular(points);
            case "strand" -> strand(points);
            default -> orb(points);
        }
        guyLines(points);
        tieContacts(points);
    }

    // ---- vectors ---------------------------------------------------------------------------------------------------------------

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] mul(double[] a, double f) {
        return new double[]{a[0] * f, a[1] * f, a[2] * f};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double length(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    private static double distance(double[] a, double[] b) {
        return length(sub(a, b));
    }

    private static double[] unit(double[] a) {
        double l = length(a);
        return l < 1.0E-9 ? new double[]{0, 1, 0} : mul(a, 1.0 / l);
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private double jitter(double amount) {
        return (random.nextDouble() - 0.5) * 2.0 * amount * irregularity;
    }

    // ---- threads ---------------------------------------------------------------------------------------------------------------

    private boolean blocked(int x, int y, int z) {
        return input.terrain().blocked(x, y, z);
    }

    private void put(int x, int y, int z, TreePart part) {
        long key = TreeVoxelizer.pack(x - bx, y - by, z - bz);
        TreePart old = blocks.get(key);
        if (old == null || part.priority() < old.priority()) blocks.put(key, part);
    }

    /** A thread from {@code a} to {@code b} that sags {@code amplitude} blocks in the middle. */
    private void thread(double[] a, double[] b, TreePart part, double amplitude) {
        thread(a, b, part, (p, t) -> amplitude * 4.0 * t * (1.0 - t));
    }

    /**
     * A thread from {@code a} to {@code b}, dropped by {@code drop} along the way. The voxels it passes are set when they are air; at the first block that is not air it stops, the
     * last air before it is a contact, and when it comes out into air again that place is a contact too and the thread goes on from there.
     */
    private void thread(double[] a, double[] b, TreePart part, Drop drop) {
        double length = distance(a, b);
        int steps = Math.max(2, (int) Math.ceil(length / 0.45));
        boolean drawing = true;
        int[] last = null;
        int[] lastAir = null;
        for (int index = 0; index <= steps; index++) {
            double t = index / (double) steps;
            double[] p = lerp(a, b, t);
            p[1] -= drop.at(p, t);
            int x = (int) Math.round(p[0]);
            int y = (int) Math.round(p[1]);
            int z = (int) Math.round(p[2]);
            if (last != null && last[0] == x && last[1] == y && last[2] == z) continue;
            last = new int[]{x, y, z};
            if (blocked(x, y, z)) {
                if (drawing && lastAir != null) contacts.add(lastAir);
                drawing = false;
            } else {
                if (!drawing) contacts.add(last);
                drawing = true;
                lastAir = last;
                put(x, y, z, part);
            }
        }
    }

    /** The fraction of the way from {@code a} to {@code b} that is air before the first block that is not (1 when all of it is). */
    private double usable(double[] a, double[] b) {
        double length = distance(a, b);
        int steps = Math.max(2, (int) Math.ceil(length / 0.45));
        for (int index = 1; index <= steps; index++) {
            double[] p = lerp(a, b, index / (double) steps);
            if (blocked((int) Math.round(p[0]), (int) Math.round(p[1]), (int) Math.round(p[2]))) return Math.max(0.0, (index - 1) / (double) steps);
        }
        return 1.0;
    }

    /** The nearest block of air to {@code p} within 2 blocks (p itself when none). */
    private double[] nearestAir(double[] p) {
        int px = (int) Math.round(p[0]);
        int py = (int) Math.round(p[1]);
        int pz = (int) Math.round(p[2]);
        if (!blocked(px, py, pz)) return p;
        double best = Double.MAX_VALUE;
        double[] found = p;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (blocked(px + dx, py + dy, pz + dz)) continue;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < best) {
                        best = d;
                        found = new double[]{px + dx, py + dy, pz + dz};
                    }
                }
            }
        }
        return found;
    }

    // ---- the frame of the points ---------------------------------------------------------------------------------------------------

    /**
     * The points as a polygon in a plane, ordered round their middle. Three or more points: the plane through them (the sum of the crosses of neighbours), the points ordered by angle.
     * Two points (or in a line): a diamond standing in the vertical plane through them. Sets the middle, the two axes and the mean radius.
     */
    private List<double[]> polygon(List<double[]> points) {
        double[] c = {0, 0, 0};
        for (double[] p : points) c = add(c, p);
        c = mul(c, 1.0 / points.size());
        double[] up = {0, 1, 0};
        double[] normal = null;
        List<double[]> ordered = new ArrayList<>(points);
        if (points.size() >= 3) {
            double[] cc = c;
            ordered.sort(Comparator.comparingDouble(p -> Math.atan2(p[2] - cc[2], p[0] - cc[0])));
            double[] sum = {0, 0, 0};
            for (int index = 0; index < ordered.size(); index++) {
                sum = add(sum, cross(sub(ordered.get(index), c), sub(ordered.get((index + 1) % ordered.size()), c)));
            }
            if (length(sum) > 1.0E-6) {
                normal = unit(sum);
                if (normal[1] < 0.0) normal = mul(normal, -1.0);
            }
        }
        if (normal == null) {
            // two points, or in a line: a diamond in the vertical plane through the ends
            double[] a = points.get(0);
            double[] b = points.get(0);
            double far = -1.0;
            for (double[] p : points) {
                double d = distance(p, points.get(0));
                if (d > far) {
                    far = d;
                    b = p;
                }
            }
            double[] d = unit(sub(b, a));
            double[] w = sub(up, mul(d, dot(up, d)));
            if (length(w) < 0.2) w = unit(cross(d, new double[]{1, 0, 0}));
            w = unit(w);
            double half = Math.max(1.5, Math.min(reach + 2.0, far * 0.55));
            double[] m = lerp(a, b, 0.5);
            ordered = new ArrayList<>(List.of(a, add(m, mul(w, half)), b, sub(m, mul(w, half))));
            normal = unit(cross(d, w));
            c = m;
        }
        axisU = unit(sub(ordered.get(0), c));
        axisU = unit(sub(axisU, mul(normal, dot(normal, axisU))));
        axisV = unit(cross(normal, axisU));
        double[] cc = c;
        double[] u = axisU;
        double[] v = axisV;
        ordered.sort(Comparator.comparingDouble(p -> Math.atan2(dot(sub(p, cc), v), dot(sub(p, cc), u))));
        double mean = 0.0;
        for (double[] p : ordered) mean += distance(p, c);
        centre = c;
        meanRadius = Math.max(1.0, mean / ordered.size());
        return ordered;
    }

    /** The point at {@code fraction} (0 - 1) of the way round the polygon. */
    private static double[] along(List<double[]> polygon, double fraction) {
        double perimeter = 0.0;
        for (int index = 0; index < polygon.size(); index++) perimeter += distance(polygon.get(index), polygon.get((index + 1) % polygon.size()));
        double target = (((fraction % 1.0) + 1.0) % 1.0) * perimeter;
        for (int index = 0; index < polygon.size(); index++) {
            double[] a = polygon.get(index);
            double[] b = polygon.get((index + 1) % polygon.size());
            double edge = distance(a, b);
            if (target <= edge || index == polygon.size() - 1) return lerp(a, b, edge < 1.0E-9 ? 0.0 : Math.min(1.0, target / edge));
            target -= edge;
        }
        return polygon.get(0);
    }

    private void frame(List<double[]> polygon, double amplitude) {
        for (int index = 0; index < polygon.size(); index++) {
            double[] a = polygon.get(index);
            double[] b = polygon.get((index + 1) % polygon.size());
            thread(a, b, TreePart.WEB_FRAME, amplitude * distance(a, b) * 0.1);
        }
    }

    // ---- the kinds -------------------------------------------------------------------------------------------------------------------

    /** 円網: a hub a little above the middle, spokes to the frame, a spiral from the hub out (it follows the terrain where a spoke is cut short). */
    private void orb(List<double[]> points) {
        List<double[]> polygon = polygon(points);
        double[] hub = nearestAir(add(centre, new double[]{0, 0.12 * meanRadius, 0}));
        double[][] ends = new double[spokes][];
        double[] usable = new double[spokes];
        for (int k = 0; k < spokes; k++) {
            ends[k] = along(polygon, (k + jitter(0.4)) / spokes);
            usable[k] = usable(hub, ends[k]);
        }
        frame(polygon, sag * 0.6);
        for (int k = 0; k < spokes; k++) thread(hub, ends[k], TreePart.WEB_FRAME, sag * 0.1);
        int total = spokes * rings;
        double[] previous = null;
        for (int j = 0; j <= total; j++) {
            int k = j % spokes;
            double f = 0.1 + 0.9 * (j / (double) total);
            double[] point = lerp(hub, ends[k], Math.min(f, usable[k] * 0.96));
            point = add(point, mul(axisU, jitter(0.25)));
            if (previous != null) thread(previous, point, TreePart.WEB, sag * 0.25);
            previous = point;
        }
    }

    /** 棚網 (a flat sheet of crossing threads) and 皿網 (the same sagging into a dish whose depth is the sag). */
    private void sheet(List<double[]> points, boolean bowl) {
        List<double[]> polygon = polygon(points);
        double radius = 0.0;
        for (double[] p : polygon) radius = Math.max(radius, distance(p, centre));
        radius = Math.max(1.0, radius);
        double depth = bowl ? Math.max(1.0, sag * radius * 0.9) : 0.0;
        double[] c = centre;
        double rMax = radius;
        Drop dish = (p, t) -> {
            double r = Math.min(1.0, distance(new double[]{p[0], 0, p[2]}, new double[]{c[0], 0, c[2]}) / rMax);
            return bowl ? depth * (1.0 - r * r) : sag * 0.12 * rMax * 4.0 * t * (1.0 - t);
        };
        int lines = rings + 2;
        List<double[]> flat = new ArrayList<>();
        for (double[] p : polygon) flat.add(new double[]{dot(sub(p, centre), axisU), dot(sub(p, centre), axisV)});
        double turn = jitter(0.5);
        for (int pass = 0; pass < 2; pass++) {
            double angle = turn + pass * Math.PI / 2.0;
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);
            double nx = -dy;
            double ny = dx;
            for (int line = 0; line < lines; line++) {
                double offset = ((line + 0.5) / lines - 0.5) * 2.0 * radius + jitter(radius / lines * 0.4);
                double ox = nx * offset;
                double oy = ny * offset;
                List<Double> crossings = new ArrayList<>();
                for (int index = 0; index < flat.size(); index++) {
                    double[] p1 = flat.get(index);
                    double[] p2 = flat.get((index + 1) % flat.size());
                    double ex = p2[0] - p1[0];
                    double ey = p2[1] - p1[1];
                    double denominator = dx * ey - dy * ex;
                    if (Math.abs(denominator) < 1.0E-9) continue;
                    double t = ((p1[0] - ox) * ey - (p1[1] - oy) * ex) / denominator;
                    double e = ((p1[0] - ox) * dy - (p1[1] - oy) * dx) / denominator;
                    if (e >= 0.0 && e < 1.0) crossings.add(t);
                }
                crossings.sort(Double::compare);
                for (int pair = 0; pair + 1 < crossings.size(); pair += 2) {
                    double[] a = add(centre, add(mul(axisU, ox + dx * crossings.get(pair)), mul(axisV, oy + dy * crossings.get(pair))));
                    double[] b = add(centre, add(mul(axisU, ox + dx * crossings.get(pair + 1)), mul(axisV, oy + dy * crossings.get(pair + 1))));
                    thread(a, b, TreePart.WEB, dish);
                }
            }
        }
        frame(polygon, bowl ? 0.0 : sag * 0.5);
    }

    /** 漏斗網: a mouth round the points narrowing to the lowest point (or a point below the middle), with rings and threads running down to it. */
    private void funnel(List<double[]> points) {
        List<double[]> polygon = polygon(points);
        double[] low = points.get(0);
        for (double[] p : points) if (p[1] < low[1]) low = p;
        double[] apex = low[1] < centre[1] - 1.5 ? low : add(centre, new double[]{0, -Math.max(2.0, 0.9 * meanRadius), 0});
        apex = nearestAir(apex);
        double[][] rim = new double[spokes][];
        double[] usable = new double[spokes];
        for (int k = 0; k < spokes; k++) {
            rim[k] = along(polygon, (k + jitter(0.4)) / spokes);
            usable[k] = usable(rim[k], apex);
        }
        frame(polygon, sag * 0.4);
        for (int k = 0; k < spokes; k++) thread(rim[k], apex, TreePart.WEB_FRAME, sag * 0.1);
        for (int ring = 1; ring <= rings; ring++) {
            double t = Math.pow(ring / (double) (rings + 1), 0.85);
            double[] previous = null;
            double[] first = null;
            for (int k = 0; k <= spokes; k++) {
                int index = k % spokes;
                double[] point = lerp(rim[index], apex, Math.min(t, usable[index] * 0.96));
                if (previous != null) thread(previous, point, TreePart.WEB, sag * 0.15);
                if (first == null) first = point;
                previous = point;
            }
        }
    }

    /** 不規則網: threads between the points and between nodes in the air round them, each joined to its nearest neighbours. */
    private void irregular(List<double[]> points) {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : points) {
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], p[axis]);
                max[axis] = Math.max(max[axis], p[axis]);
            }
        }
        double margin = Math.max(1.0, reach * 0.5);
        List<double[]> nodes = new ArrayList<>(points);
        int extra = rings * 2 + spokes / 2;
        for (int attempt = 0; attempt < extra * 4 && nodes.size() < points.size() + extra; attempt++) {
            double[] node = {min[0] - margin + random.nextDouble() * (max[0] - min[0] + 2 * margin),
                    min[1] - margin + random.nextDouble() * (max[1] - min[1] + 2 * margin),
                    min[2] - margin + random.nextDouble() * (max[2] - min[2] + 2 * margin)};
            if (!blocked((int) Math.round(node[0]), (int) Math.round(node[1]), (int) Math.round(node[2]))) nodes.add(node);
        }
        int neighbours = 2 + (int) Math.round(irregularity * 3.0);
        Set<Long> joined = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            double[] node = nodes.get(i);
            List<Integer> order = new ArrayList<>();
            for (int j = 0; j < nodes.size(); j++) if (j != i) order.add(j);
            order.sort(Comparator.comparingDouble(j -> distance(node, nodes.get(j))));
            for (int n = 0; n < Math.min(neighbours, order.size()); n++) {
                int j = order.get(n);
                long pair = ((long) Math.min(i, j) << 32) | Math.max(i, j);
                if (!joined.add(pair)) continue;
                boolean anchor = i < points.size() || j < points.size();
                thread(node, nodes.get(j), anchor ? TreePart.WEB_FRAME : TreePart.WEB, sag * distance(node, nodes.get(j)) * 0.08);
            }
        }
        for (int i = 0; i + 1 < points.size(); i++) thread(points.get(i), points.get(i + 1), TreePart.WEB_FRAME, sag * distance(points.get(i), points.get(i + 1)) * 0.1);
    }

    /** 条網: from point to point, a band of parallel threads (the outer two hold it) with rungs across. */
    private void strand(List<double[]> points) {
        int threads = Math.max(2, spokes / 3 + 1);
        int rungs = Math.max(1, rings / 2);
        double[] up = {0, 1, 0};
        for (int index = 0; index + 1 < points.size(); index++) {
            double[] a = points.get(index);
            double[] b = points.get(index + 1);
            double[] d = unit(sub(b, a));
            double[] w = cross(d, up);
            w = length(w) < 0.2 ? new double[]{1, 0, 0} : unit(w);
            double length = distance(a, b);
            double amplitude = sag * length * 0.1;
            double[][] starts = new double[threads][];
            double[][] ends = new double[threads][];
            for (int j = 0; j < threads; j++) {
                double offset = (j - (threads - 1) / 2.0) * 0.8;
                starts[j] = add(a, mul(w, offset));
                ends[j] = add(b, mul(w, offset * (1.0 + jitter(0.6))));
                thread(starts[j], ends[j], j == 0 || j == threads - 1 ? TreePart.WEB_FRAME : TreePart.WEB, amplitude);
            }
            for (int rung = 1; rung <= rungs; rung++) {
                double t = rung / (double) (rungs + 1) + jitter(0.05);
                for (int j = 0; j + 1 < threads; j++) {
                    double[] p = lerp(starts[j], ends[j], t);
                    double[] q = lerp(starts[j + 1], ends[j + 1], t);
                    p[1] -= amplitude * 4.0 * t * (1.0 - t);
                    q[1] -= amplitude * 4.0 * t * (1.0 - t);
                    thread(p, q, TreePart.WEB, 0.0);
                }
            }
        }
    }

    // ---- the terrain -------------------------------------------------------------------------------------------------------------------

    /** The directions a point looks for a wall, a floor or a ceiling in: the 26 round it. */
    private static final int[][] DIRECTIONS;

    static {
        List<int[]> list = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) if (x != 0 || y != 0 || z != 0) list.add(new int[]{x, y, z});
        DIRECTIONS = list.toArray(new int[0][]);
    }

    /** Every chosen point is fixed to the nearest surfaces within {@code web-reach}: a thread to the air next to the first block that is not air, in the nearest three directions. */
    private void guyLines(List<double[]> points) {
        for (double[] point : points) {
            List<double[]> found = new ArrayList<>();
            for (int[] direction : DIRECTIONS) {
                double[] way = unit(new double[]{direction[0], direction[1], direction[2]});
                double[] lastAir = null;
                for (double step = 0.5; step <= reach; step += 0.5) {
                    int x = (int) Math.round(point[0] + way[0] * step);
                    int y = (int) Math.round(point[1] + way[1] * step);
                    int z = (int) Math.round(point[2] + way[2] * step);
                    if (blocked(x, y, z)) {
                        if (lastAir != null) found.add(lastAir);
                        break;
                    }
                    lastAir = new double[]{x, y, z};
                }
            }
            found.sort(Comparator.comparingDouble(p -> distance(p, point)));
            int made = 0;
            Set<Long> seen = new HashSet<>();
            for (double[] contact : found) {
                if (made >= 3 || distance(contact, point) < 1.0) continue;
                if (!seen.add(TreeVoxelizer.pack((int) contact[0], (int) contact[1], (int) contact[2]))) continue;
                thread(point, contact, TreePart.WEB_FRAME, 0.0);
                made++;
            }
        }
    }

    /** The contacts (places where a thread met the terrain) are points of the web now: each is tied to the nearest point or contact within the reach (at most 40 ties). */
    private void tieContacts(List<double[]> points) {
        List<double[]> unique = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int[] contact : contacts) {
            if (seen.add(TreeVoxelizer.pack(contact[0], contact[1], contact[2]))) unique.add(new double[]{contact[0], contact[1], contact[2]});
        }
        List<double[]> pool = new ArrayList<>(points);
        pool.addAll(unique);
        int ties = 0;
        for (double[] contact : unique) {
            if (ties >= 40) break;
            double[] nearest = null;
            double best = reach * 1.5;
            for (double[] other : pool) {
                double d = distance(contact, other);
                if (d < 1.5 || d >= best) continue;
                best = d;
                nearest = other;
            }
            if (nearest == null) continue;
            thread(contact, nearest, TreePart.WEB_FRAME, 0.0);
            ties++;
        }
    }
}
