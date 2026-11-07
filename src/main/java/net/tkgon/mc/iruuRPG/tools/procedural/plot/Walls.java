package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * (t313, spec 2/6) The walls. Each side of a lot is cut where other lots cover it, and each piece is decided storey by storey: where no lot is beside it, or the
 * lot beside it has no storey that high (the part over a lower lot's eaves), it is an outside wall; where the lot beside it has a storey at the same height,
 * it is inside and no wall is made. An outside wall: a post (a log) at both ends of the piece and at the corners, a beam on the top of each storey, the
 * panels between the posts laid out to fill it exactly (as coins make a sum), and three depths: the frame (posts and beams) at 0, the wall at -1, the glass at
 * -2. A storey jutting out stands a block further out, with a corbel under each of its posts. A round or an octagonal lot's walls are cut into equal arcs.
 */
final class Walls {

    private Walls() {
    }

    /** One panel's place in a wall: {@code from} along it, {@code width} wide; a stretched one is plain wall that fills what the others leave. */
    record Slot(int from, int width, boolean stretched) {
    }

    /** What the panels of a face are: the part for each slot and whether its lines are turned over. */
    record Face(List<Slot> slots, Kit.Part[] parts, boolean[] mirror) {
    }

    /** The walls of one lot (they depend on it and the lots beside it only). */
    static void lot(Layout layout, Layout.LotGeo geo, Grid grid, Kit kit, Random random, Marks marks) {
        grid.owner = geo.index;
        for (int k = 0; k < geo.floors(); k++) {
            if (geo.plans[k].shape == Plan.Shape.RECT) {
                for (int side = 0; side < 4; side++) side(layout, geo, k, side, grid, kit, random, marks);
            } else {
                ring(layout, geo, k, grid, kit, random, marks);
            }
            floor(layout, geo, k, grid);
            if (geo.lot.floors.get(k).skirt && k < geo.floors() - 1) skirt(layout, geo, k, grid);
        }
        grid.owner = -1;
    }

    // ---- a side of a rectangle ---------------------------------------------------------------------------------------------------

    private static void side(Layout layout, Layout.LotGeo geo, int k, int side, Grid grid, Kit kit, Random random, Marks marks) {
        Area plan = geo.plans[k];
        Plan.Floor floor = geo.lot.floors.get(k);
        int length = plan.sideLength(side);
        int bottom = geo.bottom[k];
        int beam = geo.beamRow(k);
        boolean jettied = k > 0 && geo.out[k][side] > geo.out[k - 1][side];
        boolean sill = jettied || k == 0 && groundSill(geo);
        int[] out = Area.out(side);
        // each block along the side: outside (true) or inside, by the storey's top row (the rows a lot beside it covers are cleared after)
        boolean[] outside = new boolean[length];
        for (int t = 0; t < length; t++) {
            int[] at = plan.along(side, t);
            outside[t] = layout.coveringAt(geo.index, at[0], beam, at[1]).isEmpty();
        }
        int t = 0;
        while (t < length) {
            if (!outside[t]) {
                t++;
                continue;
            }
            int a = t;
            while (t + 1 < length && outside[t + 1]) t++;
            int b = t;
            t++;
            SideCanvas frame = new SideCanvas(grid, plan, side, 0, bottom);
            // the posts at both ends, the beams over and (on the ground or jutting out) under, the wall closed behind the frame
            for (int u = a; u <= b; u++) {
                frame.put(u, beam - bottom, 0, Cell.log(Role.BEAM, Area.axisAlong(side)));
                frame.put(u, beam - bottom, -1, Cell.full(plaster(floor)));
                if (sill) {
                    frame.put(u, 0, 0, Cell.log(Role.BEAM, Area.axisAlong(side)));
                    frame.put(u, 0, -1, Cell.full(plaster(floor)));
                }
            }
            int base = bottom + (sill ? 1 : 0);
            int rows = beam - base;
            if (rows <= 0 || b - a < 2) continue;
            Face face = face(geo, k, side, a, b, rows, kit, random, layout.plan.module);
            // the posts between the panels (set before the panels and over the beams: a post runs the storey's whole height)
            marks.post(geo.index, k, side, a);
            marks.post(geo.index, k, side, b);
            for (int i = 1; i < face.slots.size(); i++) {
                Slot slot = face.slots.get(i);
                marks.post(geo.index, k, side, slot.from - 1);
                for (int y = bottom; y <= beam; y++) frame.set(slot.from - 1, y - bottom, 0, Cell.log(Role.FRAME, 1));
            }
            for (int i = 0; i < face.slots.size(); i++) {
                Slot slot = face.slots.get(i);
                for (int y = bottom; y <= beam; y++) frame.put(slot.from - 1, y - bottom, -1, Cell.full(plaster(floor)));
                SideCanvas canvas = new SideCanvas(grid, plan, side, slot.from, base);
                Painter.panel(face.parts[i], canvas, slot.width, rows, plaster(floor), sill ? 0 : 1, face.mirror[i]);
                if (face.parts[i].opening == Kit.Opening.DOOR) {
                    int dw = slot.width >= 5 ? 2 : 1;
                    int[] at = plan.along(side, slot.from + (slot.width - dw) / 2);
                    int walk = Math.max(0, Math.min(sill ? 0 : 1, rows - 2));
                    marks.doors.add(new Marks.Door(geo.index, k, side, at[0] - out[0], base + walk, at[1] - out[1], side, dw));
                }
            }
            for (int y = bottom; y <= beam; y++) frame.put(b, y - bottom, -1, Cell.full(plaster(floor)));
            for (int y = bottom; y <= beam; y++) {
                frame.set(a, y - bottom, 0, Cell.log(Role.FRAME, 1));
                frame.set(b, y - bottom, 0, Cell.log(Role.FRAME, 1));
            }
            // the rows a lot beside it covers with its storeys: no wall there
            for (int u = a; u <= b; u++) {
                int[] at = plan.along(side, u);
                for (int y = bottom; y <= beam; y++) {
                    if (layout.coveringAt(geo.index, at[0], y, at[1]).isEmpty()) continue;
                    for (int d = 0; d >= -2; d--) {
                        int x = at[0] + d * out[0];
                        int z = at[1] + d * out[1];
                        if (plan.depth(x, z) == 1 - d) grid.clear(x, y, z);
                    }
                }
            }
        }
        // under a storey jutting out: a corbel under each of its posts
        if (jettied) {
            int row = bottom - 1;
            for (int u = 0; u < length; u++) {
                int[] at = plan.along(side, u);
                Cell post = grid.get(at[0], bottom, at[1]);
                if (post == null || post.role() != Role.FRAME) continue;
                if (u == 0 || u == length - 1) continue;
                grid.put(at[0], row, at[1], Cell.stairs(Role.CORBEL, (side + 2) % 4, true));
            }
        }
    }

    /** Whether the ground storey has a sill beam on its foundation: on stilts (the floor's beam), or when it is high enough to spare the row. */
    static boolean groundSill(Layout.LotGeo geo) {
        return geo.lot.foundation == Plan.Foundation.STILTS || geo.lot.floors.get(0).height >= 6;
    }

    /** The plaster's role of a storey (its colour). */
    static Role plaster(Plan.Floor floor) {
        return switch (floor.color) {
            case 1 -> Role.INFILL_2;
            case 2 -> Role.INFILL_3;
            default -> Role.INFILL;
        };
    }

    /**
     * The panels of a piece of wall from post {@code a} to post {@code b}: their widths (spec 2/6: the length filled exactly by panels and the posts between
     * them, as coins make a sum, the same both ways where it can be, the same widths liked best), then what each one is: the door in the middle when the
     * storey's door is on this side, the windows as the storey sets them, the rest wall of the storey's set.
     */
    static Face face(Layout.LotGeo geo, int k, int side, int a, int b, int rows, Kit kit, Random random, int module) {
        Plan.Floor floor = geo.lot.floors.get(k);
        String set = set(geo, k);
        List<Slot> slots = slots(a + 1, b - a - 1, module - 1, widths(kit, Kit.Category.WALL, set, rows));
        int n = slots.size();
        Kit.Part[] parts = new Kit.Part[n];
        boolean[] mirror = new boolean[n];
        String[] kinds = new String[n];
        boolean[] windows = windows(floor.windows, slots);
        int door = -1;
        boolean doorHere = floor.door == side && (k == 0 || floor.stairs || geo.lot.floors.get(k).balcony[side]);
        if (doorHere && n > 0) door = middle(slots);
        for (int i = 0; i < n; i++) {
            kinds[i] = i == door ? "door" : windows[i] && !slots.get(i).stretched ? "window" : "solid";
            if (i == door && rows < 3) kinds[i] = "solid";
        }
        // the same part on both sides of the middle, its lines turned over on the right
        for (int i = 0; i < n; i++) {
            int twin = n - 1 - i;
            Slot slot = slots.get(i);
            if (twin < i && kinds[twin].equals(kinds[i]) && slots.get(twin).width == slot.width) {
                parts[i] = parts[twin];
                mirror[i] = true;
                continue;
            }
            Kit.Part part = slot.stretched ? null : kit.pick(Kit.Category.WALL, slot.width, rows, random, set, kinds[i]);
            if (part == null) part = kit.pick(Kit.Category.WALL, slot.width, rows, random, slot.stretched ? "plaster" : set, slot.stretched ? "solid" : kinds[i]);
            if (part == null) part = kit.pick(Kit.Category.WALL, slot.width, rows, random, "plaster", "solid");
            if (part == null) part = fallback();
            parts[i] = part;
        }
        return new Face(slots, parts, mirror);
    }

    /** The set of panels of storey {@code k}: its own, or by the storey ("auto": stone under timber; one storey alone of plaster). */
    static String set(Layout.LotGeo geo, int k) {
        String set = geo.lot.floors.get(k).panels;
        if (set != null && !set.isBlank() && !set.equals("auto")) return set;
        if (geo.floors() == 1) return "plaster";
        return k == 0 ? "stone" : "timber";
    }

    /** The widths the set's panels come in for {@code rows} (every width a procedural one fits; a template's own). */
    static int[] widths(Kit kit, Kit.Category category, String set, int rows) {
        boolean[] ok = new boolean[10];
        for (Kit.Part part : kit.find(category, set)) {
            for (int w = 1; w <= 9; w++) if (part.fits(w, rows)) ok[w] = true;
        }
        int count = 0;
        for (boolean b : ok) if (b) count++;
        if (count == 0) return new int[]{1, 2, 3, 4, 5, 6, 7};
        int[] out = new int[count];
        int index = 0;
        for (int w = 1; w <= 9; w++) if (ok[w]) out[index++] = w;
        return out;
    }

    /**
     * Panels from {@code start} filling {@code n} blocks with a post between each two: by the fewest panels nearest {@code prefer} wide (the change of coins),
     * set the same both ways. When the widths cannot fill it exactly, the panels of the width liked fill the middle and two plain ones stretch to fill the ends.
     */
    static List<Slot> slots(int start, int n, int prefer, int[] widths) {
        List<Slot> out = new ArrayList<>();
        if (n <= 0) return out;
        // (the same both ways first: a half by the change of coins, mirrored, round a middle panel or a middle post)
        int[] best = mirrored(n, prefer, widths);
        if (best == null) best = change(n, prefer, widths);
        if (best != null) {
            int[] order = palindrome(best) ? best : symmetric(best);
            int at = start;
            for (int w : order) {
                out.add(new Slot(at, w, false));
                at += w + 1;
            }
            return out;
        }
        int w = widths.length == 0 ? prefer : nearest(widths, prefer);
        int count = Math.max(0, (n + 1) / (w + 1) - 1);
        int left = n - count * (w + 1);
        int head = (left - 1) / 2;
        int tail = left - 1 - head;
        int at = start;
        if (head > 0) {
            out.add(new Slot(at, head, true));
            at += head + 1;
        } else if (head == 0 && tail > 0) {
            tail -= 1;
        }
        for (int i = 0; i < count; i++) {
            out.add(new Slot(at, w, false));
            at += w + 1;
        }
        if (tail > 0) out.add(new Slot(at, tail, true));
        return out;
    }

    /**
     * The cheapest widths that are the same both ways (spec 2/6: the symmetry first): a half and its mirror round a middle panel of each width the set has, or
     * round a middle post; null when none fills {@code n}.
     */
    static int[] mirrored(int n, int prefer, int[] widths) {
        int[] best = null;
        double bestCost = Double.MAX_VALUE;
        // round a middle post: the halves' panels and posts are n + 1 together
        if ((n + 1) % 2 == 0) {
            int[] half = change((n + 1) / 2 - 1, prefer, widths);
            if (half != null) {
                double cost = 2 * cost(half, prefer);
                if (cost < bestCost) {
                    bestCost = cost;
                    best = concat(half, null, half);
                }
            }
        }
        // round a middle panel of width c
        for (int c : widths) {
            if (c > n || (n - c) % 2 != 0) continue;
            int rest = (n - c) / 2;
            int[] half = rest == 0 ? new int[0] : change(rest - 1, prefer, widths);
            if (half == null) continue;
            double cost = 2 * cost(half, prefer) + cost(new int[]{c}, prefer);
            if (cost < bestCost - 1e-9) {
                bestCost = cost;
                best = concat(half, c, half);
            }
        }
        return best;
    }

    private static double cost(int[] widths, int prefer) {
        double total = 0;
        for (int w : widths) total += 1.0 + 0.6 * Math.abs(w - prefer);
        return total;
    }

    private static int[] concat(int[] half, Integer middle, int[] other) {
        int[] out = new int[half.length * 2 + (middle == null ? 0 : 1)];
        int at = 0;
        for (int w : half) out[at++] = w;
        if (middle != null) out[at++] = middle;
        for (int i = other.length - 1; i >= 0; i--) out[at++] = other[i];
        return out;
    }

    /** The widths (fewest, nearest {@code prefer}) whose sum with a post between each two is {@code n}; null when there are none. */
    static int[] change(int n, int prefer, int[] widths) {
        // (the sum of width + 1 over the panels is n + 1)
        int total = n + 1;
        double[] cost = new double[total + 1];
        int[] last = new int[total + 1];
        Arrays.fill(cost, Double.MAX_VALUE);
        cost[0] = 0;
        for (int s = 1; s <= total; s++) {
            for (int w : widths) {
                int step = w + 1;
                if (step > s || cost[s - step] == Double.MAX_VALUE) continue;
                double c = cost[s - step] + 1.0 + 0.6 * Math.abs(w - prefer);
                if (c < cost[s]) {
                    cost[s] = c;
                    last[s] = w;
                }
            }
        }
        if (cost[total] == Double.MAX_VALUE) return null;
        List<Integer> out = new ArrayList<>();
        for (int s = total; s > 0; s -= last[s] + 1) out.add(last[s]);
        int[] array = new int[out.size()];
        for (int i = 0; i < array.length; i++) array[i] = out.get(i);
        return array;
    }

    private static boolean palindrome(int[] widths) {
        for (int i = 0; i < widths.length / 2; i++) if (widths[i] != widths[widths.length - 1 - i]) return false;
        return true;
    }

    /** The widths set the same both ways where they can be: the pairs at the ends, an odd one in the middle. */
    static int[] symmetric(int[] widths) {
        int[] sorted = widths.clone();
        Arrays.sort(sorted);
        List<Integer> pairs = new ArrayList<>();
        List<Integer> singles = new ArrayList<>();
        for (int i = 0; i < sorted.length; i++) {
            if (i + 1 < sorted.length && sorted[i] == sorted[i + 1]) {
                pairs.add(sorted[i]);
                i++;
            } else {
                singles.add(sorted[i]);
            }
        }
        int[] out = new int[widths.length];
        int head = 0;
        int tail = widths.length - 1;
        // (the widest pairs at the ends, the odd ones in the middle)
        for (int i = pairs.size() - 1; i >= 0; i--) {
            out[head++] = pairs.get(i);
            out[tail--] = pairs.get(i);
        }
        for (int single : singles) out[head++] = single;
        return out;
    }

    private static int nearest(int[] widths, int prefer) {
        int best = widths[0];
        for (int w : widths) if (Math.abs(w - prefer) < Math.abs(best - prefer)) best = w;
        return best;
    }

    /** The slot nearest the middle of the face (of two, the one on the left). */
    static int middle(List<Slot> slots) {
        int best = 0;
        double center = (slots.get(0).from + slots.get(slots.size() - 1).from + slots.get(slots.size() - 1).width - 1) / 2.0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            double distance = Math.abs(slot.from + (slot.width - 1) / 2.0 - center) + (slot.stretched ? 100 : 0);
            if (distance < bestDistance - 0.01) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** Which slots have windows, as the storey sets them, the same both ways. */
    static boolean[] windows(Plan.Windows windows, List<Slot> slots) {
        int n = slots.size();
        boolean[] out = new boolean[n];
        double center = (n - 1) / 2.0;
        for (int i = 0; i < n; i++) {
            double from = Math.abs(i - center);
            out[i] = switch (windows) {
                case ALL -> true;
                case ALTERNATE -> n % 2 == 1 ? ((int) Math.round(from)) % 2 == 0 : ((int) Math.floor(from)) % 2 == 0;
                case CENTER -> from < 1.0;
                case ENDS -> i == 0 || i == n - 1;
                case NONE -> false;
            };
        }
        return out;
    }

    /** A template's block state with the quarter turns from its frame (front south) to a face out of {@code out}: {@code rot:<turns>|<state>}. */
    static String turned(String state, int out) {
        int turns = Math.floorMod(out - 2, 4);
        return turns == 0 ? state : "rot:" + turns + "|" + state;
    }

    private static Kit.Part fallback() {
        Kit.Part part = new Kit.Part();
        part.id = "plaster";
        return part;
    }

    /** The face of side {@code side} of a plan: {@code u} along it from {@code from}, {@code v} up from {@code base}; a depth only where the plan has that depth. */
    static final class SideCanvas implements Painter.Canvas {
        private final Grid grid;
        private final Area plan;
        private final int side;
        private final int from;
        private final int base;
        private final int[] out;

        SideCanvas(Grid grid, Area plan, int side, int from, int base) {
            this.grid = grid;
            this.plan = plan;
            this.side = side;
            this.from = from;
            this.base = base;
            this.out = Area.out(side);
        }

        private int[] at(int u, int d) {
            int[] cell = plan.along(side, from + u);
            int x = cell[0] + d * out[0];
            int z = cell[1] + d * out[1];
            return plan.depth(x, z) == 1 - d ? new int[]{x, z} : null;
        }

        @Override
        public void put(int u, int v, int d, Cell cell) {
            if (from + u < 0 || from + u >= plan.sideLength(side)) return;
            int[] at = at(u, d);
            if (at != null) grid.put(at[0], base + v, at[1], cell);
        }

        /** Puts {@code cell} whatever is there (a post over a beam). */
        void set(int u, int v, int d, Cell cell) {
            if (from + u < 0 || from + u >= plan.sideLength(side)) return;
            int[] at = at(u, d);
            if (at != null) grid.set(at[0], base + v, at[1], cell);
        }

        @Override
        public void literal(int u, int v, int d, String state) {
            if (from + u < 0 || from + u >= plan.sideLength(side)) return;
            int[] at = at(u, d);
            // (a template's block is in its own frame, its front south: turned to this face)
            if (at != null) grid.literal(at[0], base + v, at[1], turned(state, side));
        }

        @Override
        public void clear(int u, int v, int d) {
            if (from + u < 0 || from + u >= plan.sideLength(side)) return;
            int[] at = at(u, d);
            if (at != null) grid.clear(at[0], base + v, at[1]);
        }

        @Override
        public int out() {
            return side;
        }

        @Override
        public int right() {
            int[] r = Area.right(side);
            for (int way = 0; way < 4; way++) if (Cell.STEP[way][0] == r[0] && Cell.STEP[way][1] == r[1]) return way;
            return 1;
        }
    }

    // ---- a round or octagonal lot -------------------------------------------------------------------------------------------------

    /** A circle's or an octagon's walls: posts at equal angles (a panel in the middle of the front), arcs between them. */
    private static void ring(Layout layout, Layout.LotGeo geo, int k, Grid grid, Kit kit, Random random, Marks marks) {
        Area plan = geo.plans[k];
        Plan.Floor floor = geo.lot.floors.get(k);
        int bottom = geo.bottom[k];
        int beam = geo.beamRow(k);
        boolean sill = k == 0 ? groundSill(geo) : geo.out[k][0] > geo.out[k - 1][0];
        List<int[]> line = new ArrayList<>();
        for (int[] cell : plan.cells()) if (plan.depth(cell[0], cell[1]) == 1) line.add(cell);
        double cx = plan.centerX();
        double cz = plan.centerZ();
        int count = Math.max(4, (int) Math.round(line.size() / (double) layout.plan.module));
        if (plan.shape == Plan.Shape.OCTAGON) count = Math.max(8, (int) Math.round(count / 8.0) * 8);
        double sector = Math.PI * 2.0 / count;
        // (the front, +z, is the angle pi/2: a panel's middle there)
        double first = Math.PI / 2.0 + sector / 2.0;
        List<List<int[]>> arcs = new ArrayList<>();
        for (int i = 0; i < count; i++) arcs.add(new ArrayList<>());
        List<int[]> posts = new ArrayList<>();
        for (int[] cell : line) {
            double angle = Math.atan2(cell[1] - cz, cell[0] - cx);
            double along = Math.floorMod((long) Math.floor((angle - first) / (Math.PI * 2.0) * 1_000_000), 1_000_000) / 1_000_000.0 * Math.PI * 2.0;
            int index = (int) Math.floor(along / sector);
            double into = along - index * sector;
            double r = Math.hypot(cell[0] - cx, cell[1] - cz);
            if (into * r < 0.6 || (sector - into) * r < 0.6) posts.add(cell);
            else arcs.get(Math.min(count - 1, index)).add(cell);
        }
        // order each arc by angle, so u runs from its left to its right as one outside sees it (clockwise from above is to the right... of one facing in)
        for (List<int[]> arc : arcs) {
            arc.sort((p, q) -> Double.compare(angleOf(q, cx, cz, first), angleOf(p, cx, cz, first)));
        }
        for (int[] post : posts) {
            if (!layout.coveringAt(geo.index, post[0], beam, post[1]).isEmpty()) continue;
            for (int y = bottom; y <= beam; y++) {
                grid.put(post[0], y, post[1], Cell.log(Role.FRAME, 1));
                int[] in = inward(plan, post, cx, cz, 1);
                if (in != null) grid.put(in[0], y, in[1], Cell.full(plaster(floor)));
            }
        }
        for (int[] cell : line) {
            if (!layout.coveringAt(geo.index, cell[0], beam, cell[1]).isEmpty()) continue;
            int axis = tangentAxis(cell, cx, cz);
            grid.put(cell[0], beam, cell[1], Cell.log(Role.BEAM, axis));
            if (sill) grid.put(cell[0], bottom, cell[1], Cell.log(Role.BEAM, axis));
            int[] in = inward(plan, cell, cx, cz, 1);
            if (in != null) {
                grid.put(in[0], beam, in[1], Cell.full(plaster(floor)));
                if (sill) grid.put(in[0], bottom, in[1], Cell.full(plaster(floor)));
            }
        }
        int base = bottom + (sill ? 1 : 0);
        int rows = beam - base;
        if (rows <= 0) return;
        String set = set(geo, k);
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) slots.add(new Slot(i * 10, arcs.get(i).size(), false));
        boolean[] windows = windows(floor.windows, slots);
        // the door: the arc nearest the side the storey's door is on
        int door = -1;
        if (floor.door >= 0 && (k == 0 || floor.stairs)) {
            double want = switch (floor.door) {
                case 0 -> -Math.PI / 2;
                case 1 -> 0.0;
                case 3 -> Math.PI;
                default -> Math.PI / 2;
            };
            double best = Double.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                if (arcs.get(i).isEmpty()) continue;
                int[] mid = arcs.get(i).get(arcs.get(i).size() / 2);
                double angle = Math.atan2(mid[1] - cz, mid[0] - cx);
                double diff = Math.abs(Math.atan2(Math.sin(angle - want), Math.cos(angle - want)));
                if (diff < best) {
                    best = diff;
                    door = i;
                }
            }
        }
        for (int i = 0; i < count; i++) {
            List<int[]> arc = arcs.get(i);
            if (arc.isEmpty()) continue;
            String kind = i == door && rows >= 3 ? "door" : windows[i] ? "window" : "solid";
            Kit.Part part = kit.pick(Kit.Category.ROUND_WALL, arc.size(), rows, random, set, kind);
            if (part == null) part = kit.pick(Kit.Category.ROUND_WALL, arc.size(), rows, random, kind);
            if (part == null) part = kit.pick(Kit.Category.WALL, arc.size(), rows, random, set, kind);
            if (part == null) part = fallback();
            ArcCanvas canvas = new ArcCanvas(grid, plan, arc, base, cx, cz);
            Painter.panel(part, canvas, arc.size(), rows, plaster(floor), sill ? 0 : 1, false);
            if (part.opening == Kit.Opening.DOOR) {
                int[] mid = arc.get(arc.size() / 2);
                int[] in = inward(plan, mid, cx, cz, 1);
                int walk = Math.max(0, Math.min(sill ? 0 : 1, rows - 2));
                if (in != null) marks.doors.add(new Marks.Door(geo.index, k, canvas.out(), in[0], base + walk, in[1], canvas.out(), 1));
            }
            // (what a lot beside it covers is no wall)
            for (int[] cell : arc) {
                for (int y = bottom; y <= beam; y++) {
                    if (layout.coveringAt(geo.index, cell[0], y, cell[1]).isEmpty()) continue;
                    for (int d = 0; d <= 2; d++) {
                        int[] in = d == 0 ? cell : inward(plan, cell, cx, cz, d);
                        if (in != null) grid.clear(in[0], y, in[1]);
                    }
                }
            }
        }
    }

    private static double angleOf(int[] cell, double cx, double cz, double first) {
        double angle = Math.atan2(cell[1] - cz, cell[0] - cx);
        return Math.floorMod((long) Math.floor((angle - first) / (Math.PI * 2.0) * 1_000_000), 1_000_000) / 1_000_000.0;
    }

    /** The axis of a log lying along the wall at {@code cell} (across the way to the middle). */
    static int tangentAxis(int[] cell, double cx, double cz) {
        return Math.abs(cell[0] - cx) > Math.abs(cell[1] - cz) ? 2 : 0;
    }

    /** The block {@code steps} in from a wall-line block toward the middle, at that depth of the plan (null: none there). */
    static int[] inward(Area plan, int[] cell, double cx, double cz, int steps) {
        double dx = cx - cell[0];
        double dz = cz - cell[1];
        double length = Math.max(1e-6, Math.hypot(dx, dz));
        int bestX = 0;
        int bestZ = 0;
        double best = Double.MAX_VALUE;
        // (the block of that depth nearest the line toward the middle)
        for (int ox = -steps; ox <= steps; ox++) {
            for (int oz = -steps; oz <= steps; oz++) {
                int x = cell[0] + ox;
                int z = cell[1] + oz;
                if (plan.depth(x, z) != 1 + steps) continue;
                double along = (ox * dx + oz * dz) / length;
                double across = Math.abs(ox * dz - oz * dx) / length;
                double score = across * 2 + Math.abs(along - steps);
                if (score < best) {
                    best = score;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        return best == Double.MAX_VALUE ? null : new int[]{bestX, bestZ};
    }

    /** An arc of a round wall as a face: {@code u} its blocks in order, depth inward toward the middle. */
    static final class ArcCanvas implements Painter.Canvas {
        private final Grid grid;
        private final Area plan;
        private final List<int[]> arc;
        private final int base;
        private final double cx;
        private final double cz;
        private final int out;
        private final int right;

        ArcCanvas(Grid grid, Area plan, List<int[]> arc, int base, double cx, double cz) {
            this.grid = grid;
            this.plan = plan;
            this.arc = arc;
            this.base = base;
            this.cx = cx;
            this.cz = cz;
            int[] mid = arc.get(arc.size() / 2);
            double dx = mid[0] - cx;
            double dz = mid[1] - cz;
            this.out = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
            // (to the right of one outside looking in)
            this.right = (out + 3) % 4;
        }

        private int[] at(int u, int d) {
            if (u < 0 || u >= arc.size()) return null;
            int[] cell = arc.get(u);
            return d == 0 ? cell : inward(plan, cell, cx, cz, -d);
        }

        @Override
        public void put(int u, int v, int d, Cell cell) {
            int[] at = at(u, d);
            if (at == null) return;
            // (a log lying along an arc follows it)
            if (cell.form() == Cell.Form.LOG && cell.axis() != 1) cell = Cell.log(cell.role(), tangentAxis(at, cx, cz));
            grid.put(at[0], base + v, at[1], cell);
        }

        @Override
        public void literal(int u, int v, int d, String state) {
            int[] at = at(u, d);
            if (at != null) grid.literal(at[0], base + v, at[1], turned(state, out));
        }

        @Override
        public void clear(int u, int v, int d) {
            int[] at = at(u, d);
            if (at != null) grid.clear(at[0], base + v, at[1]);
        }

        @Override
        public int out() {
            return out;
        }

        @Override
        public int right() {
            return right;
        }
    }

    // ---- floors and the little roofs round a storey --------------------------------------------------------------------------------

    /** The floor of storey {@code k}: its bottom row inside the walls (where no lot beside it has one already). */
    private static void floor(Layout layout, Layout.LotGeo geo, int k, Grid grid) {
        Area plan = geo.plans[k];
        int row = geo.bottom[k];
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) < 3) continue;
            grid.put(cell[0], row, cell[1], Cell.full(Role.FLOOR));
        }
    }

    /** A little roof round the top of storey {@code k} (spec 1/6, 腰屋根): a ring of stairs a block out, its high side on the wall, on every outside face. */
    private static void skirt(Layout layout, Layout.LotGeo geo, int k, Grid grid) {
        Area plan = geo.plans[k];
        int row = geo.beamRow(k);
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            for (int way = 0; way < 4; way++) {
                int x = cell[0] + Cell.STEP[way][0];
                int z = cell[1] + Cell.STEP[way][1];
                if (plan.contains(x, z) || !done.add(Area.key(x, z))) continue;
                // (not under the storey above where it juts out over it, nor inside a lot beside it)
                if (geo.plans[k + 1].contains(x, z) || !layout.coveringAt(geo.index, x, row, z).isEmpty()) continue;
                grid.put(x, row, z, Cell.stairs(Role.ROOF, (way + 2) % 4, false));
            }
        }
        // the corners: a stair that the game turns into the outer corner of the two beside it
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    int x = cell[0] + dx;
                    int z = cell[1] + dz;
                    if (plan.contains(x, z) || done.contains(Area.key(x, z))) continue;
                    if (!done.contains(Area.key(cell[0] + dx, cell[1])) || !done.contains(Area.key(cell[0], cell[1] + dz))) continue;
                    if (geo.plans[k + 1].contains(x, z) || !layout.coveringAt(geo.index, x, row, z).isEmpty()) continue;
                    done.add(Area.key(x, z));
                    grid.put(x, row, z, Cell.stairs(Role.ROOF, dz > 0 ? 0 : 2, false));
                }
            }
        }
    }
}
