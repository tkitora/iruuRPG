package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (t313) The ground plan of a lot (or of one of its storeys): a rectangle, a circle or an octagon in its box, as the blocks it covers. {@link #depth} is how far
 * a block is in from the outside (1: the wall line, where the posts stand; 2: the plaster behind them; 3: the glass), counted over the eight blocks round
 * it, so the corners of a rectangle and the steps of a circle are wall line too.
 */
public final class Area {

    public final Plan.Shape shape;
    public final int x0;
    public final int z0;
    public final int x1;
    public final int z1;
    private final Map<Long, Integer> depths = new HashMap<>();
    private final List<int[]> cells = new ArrayList<>();

    private Area(Plan.Shape shape, int x0, int z0, int x1, int z1) {
        this.shape = shape;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) if (inside(x, z)) cells.add(new int[]{x, z});
        }
        measure();
    }

    public static Area of(Plan.Shape shape, int x0, int z0, int x1, int z1) {
        return new Area(shape, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
    }

    /** The plan grown by {@code out[side]} on each side (0 north, 1 east, 2 south, 3 west); a circle or an octagon by the most of them all round. */
    public Area grown(int[] out) {
        if (shape != Plan.Shape.RECT) {
            int most = Math.max(Math.max(out[0], out[1]), Math.max(out[2], out[3]));
            return of(shape, x0 - most, z0 - most, x1 + most, z1 + most);
        }
        return of(shape, x0 - out[3], z0 - out[0], x1 + out[1], z1 + out[2]);
    }

    public int width() {
        return x1 - x0 + 1;
    }

    public int depthSpan() {
        return z1 - z0 + 1;
    }

    public double centerX() {
        return (x0 + x1) / 2.0;
    }

    public double centerZ() {
        return (z0 + z1) / 2.0;
    }

    /** The radius of a circle (to the middle of its wall line). */
    public double radius() {
        return (Math.min(width(), depthSpan()) - 1) / 2.0;
    }

    /** How far an octagon's corners are cut (blocks off each end of a side). */
    public int chamfer() {
        return (int) Math.round((Math.min(width(), depthSpan()) - 1) * 0.2929);
    }

    public boolean contains(int x, int z) {
        return depths.containsKey(key(x, z));
    }

    /** 0 outside; 1 on the wall line; 2, 3 ... further in. */
    public int depth(int x, int z) {
        return depths.getOrDefault(key(x, z), 0);
    }

    public List<int[]> cells() {
        return cells;
    }

    private boolean inside(int x, int z) {
        return switch (shape) {
            case RECT -> true;
            case ROUND -> {
                double r = radius() + 0.45;
                double dx = x - centerX();
                double dz = z - centerZ();
                yield dx * dx + dz * dz <= r * r;
            }
            case OCTAGON -> {
                int c = chamfer();
                int ax = Math.min(x - x0, x1 - x);
                int az = Math.min(z - z0, z1 - z);
                yield ax + az >= c;
            }
        };
    }

    /** The depths: from the cells beside the outside inward, over the eight neighbours. */
    private void measure() {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        java.util.Set<Long> in = new java.util.HashSet<>();
        for (int[] cell : cells) in.add(key(cell[0], cell[1]));
        for (int[] cell : cells) {
            boolean edge = false;
            for (int dx = -1; dx <= 1 && !edge; dx++) {
                for (int dz = -1; dz <= 1 && !edge; dz++) if (!in.contains(key(cell[0] + dx, cell[1] + dz))) edge = true;
            }
            if (edge) {
                depths.put(key(cell[0], cell[1]), 1);
                queue.add(cell);
            }
        }
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int d = depths.get(key(cell[0], cell[1]));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long next = key(cell[0] + dx, cell[1] + dz);
                    if (!in.contains(next) || depths.containsKey(next)) continue;
                    depths.put(next, d + 1);
                    queue.add(new int[]{cell[0] + dx, cell[1] + dz});
                }
            }
        }
    }

    static long key(int x, int z) {
        return ((long) (x + 100_000) << 32) | (z + 100_000);
    }

    // ---- the sides of a rectangle ----------------------------------------------------------------------------------------------

    /** The length of side {@code side} of a rectangle (its blocks, both corners included). */
    public int sideLength(int side) {
        return side % 2 == 0 ? width() : depthSpan();
    }

    /**
     * The block {@code t} along side {@code side} of a rectangle, from the left to the right as one outside it sees it: the back (north) from its east end, the
     * east side from its south end, the front (south) from its west end, the west side from its north end.
     */
    public int[] along(int side, int t) {
        return switch (side) {
            case 0 -> new int[]{x1 - t, z0};
            case 1 -> new int[]{x1, z1 - t};
            case 2 -> new int[]{x0 + t, z1};
            default -> new int[]{x0, z0 + t};
        };
    }

    /** The step out of side {@code side} ({dx, dz}). */
    public static int[] out(int side) {
        return Cell.STEP[Math.floorMod(side, 4)];
    }

    /** The step along side {@code side} from the left to the right as seen from outside. */
    public static int[] right(int side) {
        return switch (Math.floorMod(side, 4)) {
            case 0 -> new int[]{-1, 0};
            case 1 -> new int[]{0, -1};
            case 2 -> new int[]{1, 0};
            default -> new int[]{0, 1};
        };
    }

    /** The axis of a log lying along side {@code side} (0 x, 2 z). */
    public static int axisAlong(int side) {
        return side % 2 == 0 ? 0 : 2;
    }

    /** The side of a rectangle a wall-line block is on, the way out of it ({@code -1} for a block not on a side). Corners are on the north or south side. */
    public int sideOf(int x, int z) {
        if (z == z0) return 0;
        if (z == z1) return 2;
        if (x == x1) return 1;
        if (x == x0) return 3;
        return -1;
    }
}
