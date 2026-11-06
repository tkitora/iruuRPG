package net.tkgon.mc.iruuRPG.tools.procedural.ornate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The shapes of the blocks an ornate tree is made of, as the boxes of their vanilla models in pixels (16 = a block), and the three tables made from them once:
 * <ul>
 *     <li>the octants each shape fills (a block cut in 2x2x2): the thick wood is measured in octants and the 256 masks are turned into the nearest shape
 *     ({@link #nearest}: full 8, the inner corner of a stair 7, a stair 6, its outer corner 5, a slab 4);</li>
 *     <li>the pixels each shape has on each face of its block (a 16x16 bitmap): two blocks side by side are joined when the pixels of the faces they share overlap in
 *     16 or more pixels, all in one piece and nowhere thinner than 2 ({@link #joins}). So fences side by side are not joined (their bars are thin), a fence on a
 *     bottom slab is not (the slab does not reach the top of its block), fences one on another are;</li>
 *     <li>which shapes are joined across each face ({@link #joined}).</li>
 * </ul>
 * Faces: 0 west (-x), 1 east (+x), 2 down, 3 up, 4 north (-z), 5 south (+z). On a face the pixels are (z, y) for west and east, (x, z) for down and up, (x, y) for north
 * and south, so the two faces two blocks share have the same pixels.
 */
public final class BlockShapes {

    public enum Kind {
        EMPTY, FULL, SLAB, STAIRS, FENCE, WALL, TRAPDOOR, GATE;

        /** How thick the shape is, for the rule that a branch does not get thicker away from the trunk. */
        public int rank() {
            return switch (this) {
                case FULL -> 6;
                case STAIRS -> 5;
                case SLAB, WALL -> 4;
                case FENCE -> 2;
                case TRAPDOOR, GATE -> 1;
                case EMPTY -> 0;
            };
        }
    }

    /** One shape: its kind, its block states ({@code facing=north,half=bottom,...}; the axis of a log and the arms of a fence are added when it is put), its boxes. */
    public static final class Shape {
        public final int id;
        public final Kind kind;
        public final String props;
        /** {x0, y0, z0, x1, y1, z1} in pixels. */
        final int[][] boxes;
        /** Bit {@code x + 2y + 4z} (each 0 or 1): the octant is at least half filled. */
        public final int octants;
        /** The pixels on each face: 4 longs of 64 bits, bit {@code v * 16 + u}. */
        final long[][] faces;
        /** How much of the block is filled (0..1). */
        public final double volume;

        private Shape(int id, Kind kind, String props, int[][] boxes) {
            this.id = id;
            this.kind = kind;
            this.props = props;
            this.boxes = boxes;
            boolean[] filled = new boolean[16 * 16 * 16];
            for (int[] box : boxes) {
                for (int x = box[0]; x < box[3]; x++) {
                    for (int y = box[1]; y < box[4]; y++) {
                        for (int z = box[2]; z < box[5]; z++) filled[(x * 16 + y) * 16 + z] = true;
                    }
                }
            }
            int[] count = new int[8];
            int total = 0;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (!filled[(x * 16 + y) * 16 + z]) continue;
                        total++;
                        count[(x >> 3) + 2 * (y >> 3) + 4 * (z >> 3)]++;
                    }
                }
            }
            int mask = 0;
            for (int octant = 0; octant < 8; octant++) if (count[octant] * 2 >= 512) mask |= 1 << octant;
            this.octants = mask;
            this.volume = total / 4096.0;
            this.faces = new long[6][4];
            for (int[] box : boxes) {
                if (box[0] == 0) paint(faces[0], box[2], box[5], box[1], box[4]);
                if (box[3] == 16) paint(faces[1], box[2], box[5], box[1], box[4]);
                if (box[1] == 0) paint(faces[2], box[0], box[3], box[2], box[5]);
                if (box[4] == 16) paint(faces[3], box[0], box[3], box[2], box[5]);
                if (box[2] == 0) paint(faces[4], box[0], box[3], box[1], box[4]);
                if (box[5] == 16) paint(faces[5], box[0], box[3], box[1], box[4]);
            }
        }

        private static void paint(long[] face, int u0, int u1, int v0, int v1) {
            for (int v = v0; v < v1; v++) {
                for (int u = u0; u < u1; u++) {
                    int bit = v * 16 + u;
                    face[bit >> 6] |= 1L << (bit & 63);
                }
            }
        }

        /** The boxes in pixels (a copy). */
        public int[][] boxes() {
            int[][] copy = new int[boxes.length][];
            for (int index = 0; index < boxes.length; index++) copy[index] = boxes[index].clone();
            return copy;
        }

        @Override
        public String toString() {
            return kind + (props.isEmpty() ? "" : "[" + props + "]");
        }
    }

    public static final String[] FACINGS = {"north", "east", "south", "west"};
    /** The step of each face: west, east, down, up, north, south. */
    public static final int[][] STEPS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

    private static final List<Shape> ALL = new ArrayList<>();
    public static final Shape EMPTY;
    public static final Shape FULL;
    public static final Shape SLAB_BOTTOM;
    public static final Shape SLAB_TOP;
    public static final Shape FENCE;
    public static final Shape WALL;
    public static final Shape TRAPDOOR_BOTTOM;
    public static final Shape TRAPDOOR_TOP;
    /** The stairs: [half 0 bottom / 1 top][facing north, east, south, west][shape straight, inner_left, inner_right, outer_left, outer_right]. */
    private static final Shape[][][] STAIRS = new Shape[2][4][5];
    /** An open trapdoor standing against the side its facing turns from: [north, east, south, west]. */
    private static final Shape[] TRAPDOOR_OPEN = new Shape[4];
    /** An open fence gate: [north, east, south, west]. */
    private static final Shape[] GATE_OPEN = new Shape[4];
    private static final Shape[] LUT = new Shape[256];
    /** The shapes that fill octants: the empty block, the full block, the slabs, the stairs. */
    private static final List<Shape> SOLIDS = new ArrayList<>();
    private static boolean[][][] joined;

    /** The upper octets of a stair by the octet bits the game gives each facing and shape (1 = -x -z, 2 = +x -z, 4 = -x +z, 8 = +x +z). */
    private static final int[] SHAPE_BY_STATE = {12, 5, 3, 10, 14, 13, 7, 11, 13, 7, 11, 14, 8, 4, 1, 2, 4, 1, 2, 8};
    private static final String[] STAIR_SHAPES = {"straight", "inner_left", "inner_right", "outer_left", "outer_right"};

    static {
        EMPTY = add(Kind.EMPTY, "", new int[][]{});
        FULL = add(Kind.FULL, "", new int[][]{{0, 0, 0, 16, 16, 16}});
        SLAB_BOTTOM = add(Kind.SLAB, "type=bottom", new int[][]{{0, 0, 0, 16, 8, 16}});
        SLAB_TOP = add(Kind.SLAB, "type=top", new int[][]{{0, 8, 0, 16, 16, 16}});
        for (int half = 0; half < 2; half++) {
            for (int facing = 0; facing < 4; facing++) {
                for (int shape = 0; shape < 5; shape++) {
                    // (the game's 2D value of a facing: south 0, west 1, north 2, east 3)
                    int value = switch (facing) {
                        case 0 -> 2;
                        case 1 -> 3;
                        case 2 -> 0;
                        default -> 1;
                    };
                    int octets = SHAPE_BY_STATE[shape * 4 + value];
                    int y0 = half == 0 ? 8 : 0;
                    List<int[]> boxes = new ArrayList<>();
                    boxes.add(half == 0 ? new int[]{0, 0, 0, 16, 8, 16} : new int[]{0, 8, 0, 16, 16, 16});
                    if ((octets & 1) != 0) boxes.add(new int[]{0, y0, 0, 8, y0 + 8, 8});
                    if ((octets & 2) != 0) boxes.add(new int[]{8, y0, 0, 16, y0 + 8, 8});
                    if ((octets & 4) != 0) boxes.add(new int[]{0, y0, 8, 8, y0 + 8, 16});
                    if ((octets & 8) != 0) boxes.add(new int[]{8, y0, 8, 16, y0 + 8, 16});
                    STAIRS[half][facing][shape] = add(Kind.STAIRS, "facing=" + FACINGS[facing] + ",half=" + (half == 0 ? "bottom" : "top") + ",shape=" + STAIR_SHAPES[shape],
                            boxes.toArray(new int[0][]));
                }
            }
        }
        FENCE = add(Kind.FENCE, "", new int[][]{{6, 0, 6, 10, 16, 10}});
        // (a wall: its post, and an arm to every side, as it is put: its arms go to the blocks of the tree it is joined to)
        WALL = add(Kind.WALL, "up=true", new int[][]{{4, 0, 4, 12, 16, 12}, {5, 0, 0, 11, 14, 8}, {5, 0, 8, 11, 14, 16}, {0, 0, 5, 8, 14, 11}, {8, 0, 5, 16, 14, 11}});
        TRAPDOOR_BOTTOM = add(Kind.TRAPDOOR, "facing=north,half=bottom,open=false", new int[][]{{0, 0, 0, 16, 3, 16}});
        TRAPDOOR_TOP = add(Kind.TRAPDOOR, "facing=north,half=top,open=false", new int[][]{{0, 13, 0, 16, 16, 16}});
        TRAPDOOR_OPEN[0] = add(Kind.TRAPDOOR, "facing=north,half=bottom,open=true", new int[][]{{0, 0, 13, 16, 16, 16}});
        TRAPDOOR_OPEN[1] = add(Kind.TRAPDOOR, "facing=east,half=bottom,open=true", new int[][]{{0, 0, 0, 3, 16, 16}});
        TRAPDOOR_OPEN[2] = add(Kind.TRAPDOOR, "facing=south,half=bottom,open=true", new int[][]{{0, 0, 0, 16, 16, 3}});
        TRAPDOOR_OPEN[3] = add(Kind.TRAPDOOR, "facing=west,half=bottom,open=true", new int[][]{{13, 0, 0, 16, 16, 16}});
        // (the open gate's model faces south; the game turns it a quarter clockwise for west, half for north, three quarters for east)
        int[][] gate = {{0, 5, 7, 2, 16, 9}, {14, 5, 7, 16, 16, 9}, {0, 6, 13, 2, 15, 15}, {14, 6, 13, 16, 15, 15}, {0, 6, 9, 2, 9, 13}, {0, 12, 9, 2, 15, 13},
                {14, 6, 9, 16, 9, 13}, {14, 12, 9, 16, 15, 13}};
        int[] turns = {2, 3, 0, 1};
        for (int facing = 0; facing < 4; facing++) {
            int[][] boxes = gate;
            for (int turn = 0; turn < turns[facing]; turn++) boxes = quarter(boxes);
            GATE_OPEN[facing] = add(Kind.GATE, "facing=" + FACINGS[facing] + ",open=true,in_wall=false", boxes);
        }
        // the 256 masks of octants to the nearest shape that fills octants (the empty block first: a tie keeps the wood out)
        List<Shape> solids = SOLIDS;
        solids.add(EMPTY);
        solids.add(FULL);
        solids.add(SLAB_BOTTOM);
        solids.add(SLAB_TOP);
        for (int shape = 0; shape < 5; shape++) {
            for (int half = 0; half < 2; half++) {
                for (int facing = 0; facing < 4; facing++) solids.add(STAIRS[half][facing][shape]);
            }
        }
        for (int mask = 0; mask < 256; mask++) {
            Shape best = null;
            int bestDistance = Integer.MAX_VALUE;
            int bestCount = Integer.MAX_VALUE;
            for (Shape shape : solids) {
                int distance = Integer.bitCount(mask ^ shape.octants);
                int count = Math.abs(Integer.bitCount(shape.octants) - Integer.bitCount(mask));
                if (distance < bestDistance || (distance == bestDistance && count < bestCount)) {
                    best = shape;
                    bestDistance = distance;
                    bestCount = count;
                }
            }
            LUT[mask] = best;
        }
        int n = ALL.size();
        joined = new boolean[6][n][n];
        for (int face = 0; face < 6; face++) {
            for (Shape a : ALL) {
                for (Shape b : ALL) joined[face][a.id][b.id] = joins(a.faces[face], b.faces[face ^ 1]);
            }
        }
    }

    private BlockShapes() {
    }

    private static Shape add(Kind kind, String props, int[][] boxes) {
        Shape shape = new Shape(ALL.size(), kind, props, boxes);
        ALL.add(shape);
        return shape;
    }

    /** The boxes turned a quarter clockwise seen from above (south to west): x' = 16 - z, z' = x. */
    private static int[][] quarter(int[][] boxes) {
        int[][] turned = new int[boxes.length][];
        for (int index = 0; index < boxes.length; index++) {
            int[] b = boxes[index];
            turned[index] = new int[]{16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
        }
        return turned;
    }

    /** The shapes that fill octants (the empty block, the full block, the slabs, the stairs). */
    public static List<Shape> solids() {
        return Collections.unmodifiableList(SOLIDS);
    }

    public static List<Shape> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Shape byId(int id) {
        return ALL.get(id);
    }

    /** {@code half} 0 bottom / 1 top, {@code facing} 0 north, 1 east, 2 south, 3 west; {@code shape} 0 straight, 1 inner_left, 2 inner_right, 3 outer_left, 4 outer_right. */
    public static Shape stairs(int half, int facing, int shape) {
        return STAIRS[half][facing][shape];
    }

    /** An open trapdoor whose board stands against the side {@code side} (0 north, 1 east, 2 south, 3 west) of its block. */
    public static Shape trapdoorAgainst(int side) {
        // (an open trapdoor facing north stands against the south side of its block)
        return TRAPDOOR_OPEN[(side + 2) % 4];
    }

    /** An open fence gate facing {@code facing} (0 north, 1 east, 2 south, 3 west): facing north or south its posts are on the west and east sides. */
    public static Shape gate(int facing) {
        return GATE_OPEN[facing];
    }

    /** The shape whose octants are nearest {@code mask} (the table of the 256 masks). */
    public static Shape nearest(int mask) {
        return LUT[mask & 0xFF];
    }

    /** Whether {@code a} is joined to {@code b} put next to it across face {@code face} of {@code a}. */
    public static boolean joined(Shape a, int face, Shape b) {
        return joined[face][a.id][b.id];
    }

    /** The face of the block whose step is (dx, dy, dz) (one of the 6 neighbours), or -1. */
    public static int face(int dx, int dy, int dz) {
        for (int face = 0; face < 6; face++) {
            if (STEPS[face][0] == dx && STEPS[face][1] == dy && STEPS[face][2] == dz) return face;
        }
        return -1;
    }

    /**
     * Whether two faces are joined: their shared pixels are 16 or more, all in one piece (by the 4 neighbours of a pixel), and every one of them is in a 2x2 square of them
     * (nowhere 1 pixel thin).
     */
    static boolean joins(long[] a, long[] b) {
        long[] both = new long[4];
        int count = 0;
        for (int word = 0; word < 4; word++) {
            both[word] = a[word] & b[word];
            count += Long.bitCount(both[word]);
        }
        if (count < 16) return false;
        // one piece
        int start = -1;
        for (int bit = 0; bit < 256 && start < 0; bit++) if (has(both, bit)) start = bit;
        boolean[] seen = new boolean[256];
        int[] queue = new int[256];
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        seen[start] = true;
        int reached = 0;
        while (head < tail) {
            int bit = queue[head++];
            reached++;
            int u = bit & 15;
            int v = bit >> 4;
            int[][] around = {{u - 1, v}, {u + 1, v}, {u, v - 1}, {u, v + 1}};
            for (int[] next : around) {
                if (next[0] < 0 || next[0] > 15 || next[1] < 0 || next[1] > 15) continue;
                int other = next[1] * 16 + next[0];
                if (!seen[other] && has(both, other)) {
                    seen[other] = true;
                    queue[tail++] = other;
                }
            }
        }
        if (reached != count) return false;
        // at least 2 thick everywhere
        boolean[] covered = new boolean[256];
        for (int v = 0; v < 15; v++) {
            for (int u = 0; u < 15; u++) {
                int bit = v * 16 + u;
                if (has(both, bit) && has(both, bit + 1) && has(both, bit + 16) && has(both, bit + 17)) {
                    covered[bit] = covered[bit + 1] = covered[bit + 16] = covered[bit + 17] = true;
                }
            }
        }
        for (int bit = 0; bit < 256; bit++) if (has(both, bit) && !covered[bit]) return false;
        return true;
    }

    private static boolean has(long[] bits, int bit) {
        return (bits[bit >> 6] & (1L << (bit & 63))) != 0;
    }
}
