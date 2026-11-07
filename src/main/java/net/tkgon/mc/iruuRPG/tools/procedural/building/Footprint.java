package net.tkgon.mc.iruuRPG.tools.procedural.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (t295) The ground plan of a mass: a polygon whose corners are the middles of blocks and whose sides go straight or at 45 degrees (a rectangle, a union of
 * rectangles for L, T, U and cross plans, a rectangle with its corners cut, an octagon), or a circle taken from a table of circles drawn by hand (not computed).
 * The blocks inside are its cells; {@link #depth} is how far a cell is inside (1 the wall line, 2 the wall behind it, 3 the windows' glass...). A plan grown out
 * by a block on the grid ({@link #expand}) is the plan of a storey jettied out over the one below.
 *
 * <p>The polygon goes round clockwise seen from above with z down (x to the right): the inside of a side going (dx, dz) is (-dz, dx).
 */
public final class Footprint {

    public enum Kind {
        RECT, UNION, CHAMFER, OCTAGON, CIRCLE
    }

    /** A side of the polygon: from {@code (x, z)} {@code length} steps of {@code (dx, dz)} (a step is one block, or one block each way at 45 degrees). */
    public record Edge(int x, int z, int dx, int dz, int length) {

        /** The step into the plan. */
        public int inX() {
            return -dz;
        }

        public int inZ() {
            return dx;
        }

        public boolean diagonal() {
            return dx != 0 && dz != 0;
        }

        /** The cell {@code t} steps along. */
        public int[] at(int t) {
            return new int[]{x + dx * t, z + dz * t};
        }
    }

    /**
     * The circles by radius (3 to 10): how far each row reaches from the middle, from the top row to the bottom. Drawn as builders draw them (a row is never more than
     * a block narrower than the next one in except at the very top), not computed.
     */
    private static final int[][] CIRCLES = {
            {},
            {0, 1, 0},
            {1, 2, 2, 2, 1},
            {1, 2, 3, 3, 3, 2, 1},
            {2, 3, 4, 4, 4, 4, 4, 3, 2},
            {2, 3, 4, 5, 5, 5, 5, 5, 4, 3, 2},
            {3, 4, 5, 5, 6, 6, 6, 6, 6, 5, 5, 4, 3},
            {3, 4, 5, 6, 6, 7, 7, 7, 7, 7, 6, 6, 5, 4, 3},
            {3, 5, 6, 6, 7, 7, 8, 8, 8, 8, 8, 7, 7, 6, 6, 5, 3},
            {4, 5, 6, 7, 8, 8, 8, 9, 9, 9, 9, 9, 8, 8, 8, 7, 6, 5, 4},
            {4, 6, 7, 8, 8, 9, 9, 10, 10, 10, 10, 10, 10, 10, 9, 9, 8, 8, 7, 6, 4},
    };
    public static final int MAX_RADIUS = CIRCLES.length - 1;

    private final Kind kind;
    private final List<int[]> vertices;
    /** The rectangles of a rectangle or a union, {x0, z0, x1, z1} (cells, both ends in): the pieces its roof is made of. */
    private final List<int[]> rects;
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    /** The depth of every cell of the box grown by 1 (0 outside). */
    private final byte[] depths;

    private Footprint(Kind kind, List<int[]> vertices, List<int[]> rects, int centerX, int centerZ, int radius) {
        this.kind = kind;
        this.vertices = vertices;
        this.rects = rects;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        if (kind == Kind.CIRCLE) {
            x0 = centerX - radius;
            x1 = centerX + radius;
            z0 = centerZ - radius;
            z1 = centerZ + radius;
        } else {
            for (int[] v : vertices) {
                x0 = Math.min(x0, v[0]);
                x1 = Math.max(x1, v[0]);
                z0 = Math.min(z0, v[1]);
                z1 = Math.max(z1, v[1]);
            }
        }
        this.minX = x0 - 1;
        this.minZ = z0 - 1;
        this.sizeX = x1 - x0 + 3;
        this.sizeZ = z1 - z0 + 3;
        this.depths = new byte[sizeX * sizeZ];
        computeDepths();
    }

    // ---- making plans -------------------------------------------------------------------------------------------------------------

    /** A rectangle of {@code width} by {@code depth} cells with its top left cell at {@code (x, z)}. */
    public static Footprint rect(int x, int z, int width, int depth) {
        int[] r = {x, z, x + width - 1, z + depth - 1};
        return new Footprint(Kind.RECT, rectVertices(r), List.of(r), 0, 0, 0);
    }

    /** The union of rectangles ({x0, z0, x1, z1}, cells, both ends in), all in one piece. */
    public static Footprint union(List<int[]> rects) {
        if (rects.size() == 1) {
            int[] r = rects.get(0);
            return rect(r[0], r[1], r[2] - r[0] + 1, r[3] - r[1] + 1);
        }
        List<int[]> copy = new ArrayList<>();
        for (int[] r : rects) copy.add(r.clone());
        return new Footprint(Kind.UNION, trace(copy), copy, 0, 0, 0);
    }

    /** A rectangle with its four corners cut at 45 degrees, {@code cut} steps each way. */
    public static Footprint chamfer(int x, int z, int width, int depth, int cut) {
        int w = width - 1;
        int d = depth - 1;
        int c = Math.max(1, Math.min(cut, Math.min(w, d) / 2 - 1));
        List<int[]> v = List.of(new int[]{x + c, z}, new int[]{x + w - c, z}, new int[]{x + w, z + c}, new int[]{x + w, z + d - c}, new int[]{x + w - c, z + d},
                new int[]{x + c, z + d}, new int[]{x, z + d - c}, new int[]{x, z + c});
        return new Footprint(Kind.CHAMFER, v, List.of(), 0, 0, 0);
    }

    /** An octagon whose straight sides are {@code side} cells long and whose slanting sides are {@code slant} steps, with its top left at {@code (x, z)}. */
    public static Footprint octagon(int x, int z, int side, int slant) {
        int a = Math.max(1, side) - 1;
        int b = Math.max(1, slant);
        int s = a + 2 * b;
        List<int[]> v = List.of(new int[]{x + b, z}, new int[]{x + b + a, z}, new int[]{x + s, z + b}, new int[]{x + s, z + b + a}, new int[]{x + b + a, z + s},
                new int[]{x + b, z + s}, new int[]{x, z + b + a}, new int[]{x, z + b});
        return new Footprint(Kind.OCTAGON, v, List.of(), 0, 0, 0);
    }

    /** The circle of {@code radius} (2 to {@link #MAX_RADIUS}) round the cell {@code (x, z)}. */
    public static Footprint circle(int x, int z, int radius) {
        return new Footprint(Kind.CIRCLE, List.of(), List.of(), x, z, Math.max(2, Math.min(MAX_RADIUS, radius)));
    }

    private static List<int[]> rectVertices(int[] r) {
        return List.of(new int[]{r[0], r[1]}, new int[]{r[2], r[1]}, new int[]{r[2], r[3]}, new int[]{r[0], r[3]});
    }

    /**
     * The polygon round a union of rectangles: the sides between the cells inside and outside, on the corners of the cells, chained clockwise, then moved in by half a
     * block to the middles of the cells of the wall line (a corner by half a block along each of its two sides' inward steps).
     */
    private static List<int[]> trace(List<int[]> rects) {
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (int[] r : rects) {
            x0 = Math.min(x0, r[0]);
            z0 = Math.min(z0, r[1]);
            x1 = Math.max(x1, r[2]);
            z1 = Math.max(z1, r[3]);
        }
        int fx0 = x0;
        int fz0 = z0;
        int w = x1 - x0 + 1;
        int d = z1 - z0 + 1;
        boolean[] in = new boolean[w * d];
        for (int[] r : rects) {
            for (int x = r[0]; x <= r[2]; x++) for (int z = r[1]; z <= r[3]; z++) in[(x - x0) * d + (z - z0)] = true;
        }
        java.util.function.BiPredicate<Integer, Integer> inside = (x, z) -> x >= fx0 && z >= fz0 && x < fx0 + w && z < fz0 + d && in[(x - fx0) * d + (z - fz0)];
        // the corner (i, j) is the point (i - 0.5, j - 0.5); a side of an inside cell next to an outside one, going round the cell clockwise
        Map<Long, int[]> next = new HashMap<>();
        long start = Long.MIN_VALUE;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!inside.test(x, z)) continue;
                if (!inside.test(x, z - 1)) next.put(corner(x, z), new int[]{x + 1, z});
                if (!inside.test(x + 1, z)) next.put(corner(x + 1, z), new int[]{x + 1, z + 1});
                if (!inside.test(x, z + 1)) next.put(corner(x + 1, z + 1), new int[]{x, z + 1});
                if (!inside.test(x - 1, z)) next.put(corner(x, z + 1), new int[]{x, z});
                if (start == Long.MIN_VALUE && !inside.test(x, z - 1)) start = corner(x, z);
            }
        }
        List<int[]> corners = new ArrayList<>();
        long at = start;
        int guard = next.size() + 1;
        do {
            int[] to = next.get(at);
            if (to == null) break;
            corners.add(new int[]{cornerX(at), cornerZ(at)});
            at = corner(to[0], to[1]);
        } while (at != start && --guard > 0);
        // the corners where the way turns, each moved half a block in along both its sides
        List<int[]> out = new ArrayList<>();
        int n = corners.size();
        for (int index = 0; index < n; index++) {
            int[] before = corners.get((index + n - 1) % n);
            int[] here = corners.get(index);
            int[] after = corners.get((index + 1) % n);
            int dx1 = Integer.signum(here[0] - before[0]);
            int dz1 = Integer.signum(here[1] - before[1]);
            int dx2 = Integer.signum(after[0] - here[0]);
            int dz2 = Integer.signum(after[1] - here[1]);
            if (dx1 == dx2 && dz1 == dz2) continue;
            // (point - 0.5 + 0.5 * (in1 + in2), in = (-dz, dx): at a turn each sum is 1 or -1, so the middle is the corner's cell or the one before it)
            int ix = -dz1 - dz2;
            int iz = dx1 + dx2;
            out.add(new int[]{here[0] + (ix < 0 ? -1 : 0), here[1] + (iz < 0 ? -1 : 0)});
        }
        return out;
    }

    private static long corner(int i, int j) {
        return ((long) (i + 100_000) << 32) | (j + 100_000);
    }

    private static int cornerX(long key) {
        return (int) (key >> 32) - 100_000;
    }

    private static int cornerZ(long key) {
        return (int) (key & 0xFFFFFFFFL) - 100_000;
    }

    /** The plan grown out by {@code k} blocks on the grid (each side moved out a step; a slanting side keeps its length, a straight one grows by 2 a step). */
    public Footprint expand(int k) {
        if (k == 0) return this;
        return switch (kind) {
            case CIRCLE -> circle(centerX, centerZ, radius + k);
            case RECT, UNION -> {
                List<int[]> grown = new ArrayList<>();
                for (int[] r : rects) grown.add(new int[]{r[0] - k, r[1] - k, r[2] + k, r[3] + k});
                yield union(grown);
            }
            default -> new Footprint(kind, offset(vertices, k), List.of(), 0, 0, 0);
        };
    }

    /** The same plan moved by {@code (dx, dz)}. */
    public Footprint moved(int dx, int dz) {
        if (dx == 0 && dz == 0) return this;
        return switch (kind) {
            case CIRCLE -> circle(centerX + dx, centerZ + dz, radius);
            case RECT, UNION -> {
                List<int[]> moved = new ArrayList<>();
                for (int[] r : rects) moved.add(new int[]{r[0] + dx, r[1] + dz, r[2] + dx, r[3] + dz});
                yield union(moved);
            }
            default -> {
                List<int[]> moved = new ArrayList<>();
                for (int[] v : vertices) moved.add(new int[]{v[0] + dx, v[1] + dz});
                yield new Footprint(kind, moved, List.of(), 0, 0, 0);
            }
        };
    }

    /**
     * (t302) A rectangle's or a union's plan grown out by {@code front} blocks at the front (+z) and the back (-z) and by {@code sides} at the left and the right
     * (a jetty at the front and the back only); any other plan grows by {@code front} all round.
     */
    public Footprint expand(int front, int sides) {
        if (front == sides) return expand(front);
        if (kind != Kind.RECT && kind != Kind.UNION) return expand(Math.max(front, sides));
        List<int[]> grown = new ArrayList<>();
        for (int[] r : rects) grown.add(new int[]{r[0] - sides, r[1] - front, r[2] + sides, r[3] + front});
        return union(grown);
    }

    /** The polygon with every side moved out by {@code k} steps (out = (dz, -dx) for a side going (dx, dz)): its new corners where the moved sides meet. */
    private static List<int[]> offset(List<int[]> vertices, int k) {
        int n = vertices.size();
        List<int[]> out = new ArrayList<>();
        for (int index = 0; index < n; index++) {
            int[] a = vertices.get((index + n - 1) % n);
            int[] b = vertices.get(index);
            int[] c = vertices.get((index + 1) % n);
            int dx1 = Integer.signum(b[0] - a[0]);
            int dz1 = Integer.signum(b[1] - a[1]);
            int dx2 = Integer.signum(c[0] - b[0]);
            int dz2 = Integer.signum(c[1] - b[1]);
            // the two moved lines: through b + k * out1 along (dx1, dz1), and through b + k * out2 along (dx2, dz2)
            double px = b[0] + k * dz1;
            double pz = b[1] - k * dx1;
            double qx = b[0] + k * dz2;
            double qz = b[1] - k * dx2;
            double det = dx1 * (-dz2) - dz1 * (-dx2);
            if (Math.abs(det) < 1.0E-9) {
                out.add(new int[]{(int) Math.round(px), (int) Math.round(pz)});
                continue;
            }
            double s = ((qx - px) * (-dz2) - (qz - pz) * (-dx2)) / det;
            out.add(new int[]{(int) Math.round(px + s * dx1), (int) Math.round(pz + s * dz1)});
        }
        return out;
    }

    // ---- what a plan is ---------------------------------------------------------------------------------------------------------

    public Kind kind() {
        return kind;
    }

    public boolean circle() {
        return kind == Kind.CIRCLE;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int radius() {
        return radius;
    }

    /** The rectangles (a rectangle's one, a union's) {x0, z0, x1, z1}; empty for the other plans. */
    public List<int[]> rects() {
        return rects;
    }

    /** The corners (copies). */
    public List<int[]> vertices() {
        List<int[]> copy = new ArrayList<>();
        for (int[] v : vertices) copy.add(v.clone());
        return copy;
    }

    /** The sides of the polygon (none for a circle). */
    public List<Edge> edges() {
        List<Edge> edges = new ArrayList<>();
        int n = vertices.size();
        for (int index = 0; index < n; index++) {
            int[] a = vertices.get(index);
            int[] b = vertices.get((index + 1) % n);
            int dx = Integer.signum(b[0] - a[0]);
            int dz = Integer.signum(b[1] - a[1]);
            int length = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            if (length > 0) edges.add(new Edge(a[0], a[1], dx, dz, length));
        }
        return edges;
    }

    /** {minX, minZ, maxX, maxZ} of the cells. */
    public int[] box() {
        return new int[]{minX + 1, minZ + 1, minX + sizeX - 2, minZ + sizeZ - 2};
    }

    public boolean contains(int x, int z) {
        return depth(x, z) > 0;
    }

    /** How far the cell is inside: the steps (one block, or one each way) to the nearest cell outside; 0 outside. */
    public int depth(int x, int z) {
        int i = x - minX;
        int j = z - minZ;
        if (i < 0 || j < 0 || i >= sizeX || j >= sizeZ) return 0;
        return depths[i * sizeZ + j];
    }

    /** The cells, row by row. */
    public List<int[]> cells() {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeZ; j++) if (depths[i * sizeZ + j] > 0) out.add(new int[]{minX + i, minZ + j});
        }
        return out;
    }

    public int area() {
        int count = 0;
        for (byte depth : depths) if (depth > 0) count++;
        return count;
    }

    private boolean inside(int x, int z) {
        return switch (kind) {
            case CIRCLE -> {
                int dz = z - centerZ;
                if (Math.abs(dz) > radius) yield false;
                yield Math.abs(x - centerX) <= CIRCLES[radius][dz + radius];
            }
            case RECT, UNION -> {
                for (int[] r : rects) if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) yield true;
                yield false;
            }
            default -> {
                int n = vertices.size();
                for (int index = 0; index < n; index++) {
                    int[] a = vertices.get(index);
                    int[] b = vertices.get((index + 1) % n);
                    int dx = Integer.signum(b[0] - a[0]);
                    int dz = Integer.signum(b[1] - a[1]);
                    if ((x - a[0]) * -dz + (z - a[1]) * dx < 0) yield false;
                }
                yield true;
            }
        };
    }

    /** The depth of every cell: the steps to the nearest cell outside, round the 8 cells about it. */
    private void computeDepths() {
        int[] queue = new int[sizeX * sizeZ];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeZ; j++) {
                boolean in = i > 0 && j > 0 && i < sizeX - 1 && j < sizeZ - 1 && inside(minX + i, minZ + j);
                depths[i * sizeZ + j] = (byte) (in ? -1 : 0);
                if (!in) queue[tail++] = i * sizeZ + j;
            }
        }
        while (head < tail) {
            int index = queue[head++];
            int i = index / sizeZ;
            int j = index % sizeZ;
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    int ni = i + di;
                    int nj = j + dj;
                    if (ni < 0 || nj < 0 || ni >= sizeX || nj >= sizeZ) continue;
                    int other = ni * sizeZ + nj;
                    if (depths[other] != -1) continue;
                    depths[other] = (byte) Math.min(120, depths[index] + 1);
                    queue[tail++] = other;
                }
            }
        }
    }
}
