package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * (t313) A plan made regular and measured (spec 1/6): the lots on the grid of the module (a rectangle n·module+1 long, so a post stands at each corner), inside
 * the site; each lot's storeys (its plan grown by its jetties) and heights, its own (ground + foundation, each storey's bottom, the eaves), so more storeys on
 * one lot change that lot only; and the sides the lots share, as intervals (from where to where along a side another lot covers it), which make the graph of
 * the lots next to one another that the walls and the roofs are decided by.
 */
public final class Layout {

    /** The regular plan (a copy: the plan given is not changed). */
    public final Plan plan;
    public final List<LotGeo> lots = new ArrayList<>();
    /** Where a side of a lot is covered by another lot (from {@code from} to {@code to} along it, as {@link Area#along} counts). */
    public final List<Shared> shared = new ArrayList<>();
    /** The lots next to each lot. */
    public final Map<Integer, Set<Integer>> graph = new TreeMap<>();
    /** What making it regular changed, for the messages. */
    public final List<String> notes = new ArrayList<>();

    /** Side {@code side} of lot {@code lot} is covered by lot {@code other} from {@code from} to {@code to} (a circle's or an octagon's whole wall: side -1). */
    public record Shared(int lot, int side, int from, int to, int other) {
    }

    /** One lot measured: its plans by storey and its heights. */
    public static final class LotGeo {
        public final int index;
        public final Plan.Lot lot;
        /** The ground storey's plan, and each storey's (grown by the jetties). */
        public final Area base;
        public final Area[] plans;
        /** The ground storey's floor (the top of the foundation), each storey's bottom, the eaves (the first row over the walls). */
        public final int y0;
        public final int[] bottom;
        public final int eave;
        /** How far each side of each storey is out of the ground storey's. */
        public final int[][] out;

        LotGeo(int index, Plan.Lot lot) {
            this.index = index;
            this.lot = lot;
            base = Area.of(lot.shape, lot.x, lot.z, lot.x + lot.w - 1, lot.z + lot.d - 1);
            int floors = lot.floors.size();
            plans = new Area[floors];
            out = new int[floors][4];
            bottom = new int[floors];
            plans[0] = base;
            for (int k = 1; k < floors; k++) {
                for (int side = 0; side < 4; side++) out[k][side] = Math.min(2, out[k - 1][side] + lot.floors.get(k).jetty[side]);
                plans[k] = base.grown(out[k]);
            }
            y0 = lot.foundation == Plan.Foundation.NONE ? 0 : lot.base;
            int y = y0;
            for (int k = 0; k < floors; k++) {
                bottom[k] = y;
                y += lot.floors.get(k).height;
            }
            eave = y;
        }

        public Area top() {
            return plans[plans.length - 1];
        }

        public int floors() {
            return plans.length;
        }

        /** The storey row {@code y} is in (-1: under or over the walls). */
        public int storeyAt(int y) {
            for (int k = plans.length - 1; k >= 0; k--) if (y >= bottom[k]) return y < eave ? k : -1;
            return -1;
        }

        /** The row of the beam on the top of storey {@code k}. */
        public int beamRow(int k) {
            return bottom[k] + lot.floors.get(k).height - 1;
        }

        /** Whether the lot covers the column at storey height {@code y} (its storey's plan has it). */
        public boolean covers(int x, int y, int z) {
            int k = storeyAt(y);
            return k >= 0 && plans[k].contains(x, z);
        }
    }

    private Layout(Plan plan) {
        this.plan = plan;
    }

    /** The plan made regular and measured. */
    public static Layout of(Plan input) {
        Plan plan = input.copy();
        Layout layout = new Layout(plan);
        layout.normalize();
        for (int index = 0; index < plan.lots.size(); index++) layout.lots.add(new LotGeo(index, plan.lots.get(index)));
        layout.share();
        return layout;
    }

    /** The lots on the grid, n·module+1 long, inside the site; the storeys and the names kept in their ranges. */
    private void normalize() {
        int m = Math.max(3, Math.min(6, plan.module));
        plan.module = m;
        plan.siteWidth = Math.max(m + 1, Math.min(97, plan.siteWidth));
        plan.siteDepth = Math.max(m + 1, Math.min(97, plan.siteDepth));
        while (plan.lots.size() > Plan.MAX_LOTS) plan.lots.remove(plan.lots.size() - 1);
        Set<String> names = new java.util.HashSet<>();
        for (Plan.Lot lot : plan.lots) {
            String before = lot.x + "," + lot.z + " " + lot.w + "x" + lot.d;
            if (lot.shape != Plan.Shape.RECT) {
                int size = Math.max(lot.w, lot.d);
                lot.w = size;
                lot.d = size;
            }
            int least = lot.shape == Plan.Shape.RECT ? 1 : 2;
            lot.w = fit(lot.w, m, least, plan.siteWidth);
            lot.d = fit(lot.d, m, least, plan.siteDepth);
            if (lot.shape != Plan.Shape.RECT) {
                int size = Math.min(lot.w, lot.d);
                lot.w = size;
                lot.d = size;
            }
            lot.x = snap(lot.x, m, plan.siteWidth - lot.w);
            lot.z = snap(lot.z, m, plan.siteDepth - lot.d);
            String after = lot.x + "," + lot.z + " " + lot.w + "x" + lot.d;
            if (!before.equals(after)) notes.add(lot.name + ": " + before + " → " + after);
            while (lot.floors.size() > Plan.MAX_FLOORS) lot.floors.remove(lot.floors.size() - 1);
            if (lot.floors.isEmpty()) lot.floors.add(new Plan.Floor());
            for (Plan.Floor floor : lot.floors) floor.height = Math.max(3, Math.min(9, floor.height));
            if (lot.foundation == Plan.Foundation.NONE) lot.base = 0;
            if (lot.foundation == Plan.Foundation.STILTS) lot.base = Math.max(2, lot.base);
            lot.base = Math.max(0, Math.min(12, lot.base));
            if (lot.shape != Plan.Shape.RECT && (lot.roof.type == Plan.RoofType.GABLE || lot.roof.type == Plan.RoofType.HIP
                    || lot.roof.type == Plan.RoofType.HALF_HIP || lot.roof.type == Plan.RoofType.SHED)) {
                lot.roof.type = Plan.RoofType.CONE;
            }
            if (lot.shape == Plan.Shape.RECT && lot.roof.type == Plan.RoofType.CONE) lot.roof.type = Plan.RoofType.PYRAMID;
            String name = Plan.safeName(lot.name, "lot");
            String unique = name;
            for (int n = 2; names.contains(unique); n++) unique = name + n;
            lot.name = unique;
            names.add(unique);
        }
    }

    /** A length made n·module+1 (n at least {@code least}), no longer than {@code room}. */
    static int fit(int length, int m, int least, int room) {
        int n = Math.max(least, (int) Math.round((length - 1) / (double) m));
        while (n > least && n * m + 1 > room) n--;
        return n * m + 1;
    }

    /** A place on the grid of the module, from 0 to {@code room} (the room left for it). */
    static int snap(int at, int m, int room) {
        int snapped = (int) Math.round(at / (double) m) * m;
        int most = Math.max(0, room) / m * m;
        return Math.max(0, Math.min(most, snapped));
    }

    /** The intervals of the sides covered by other lots, and the graph of the lots next to one another. */
    private void share() {
        for (LotGeo geo : lots) graph.put(geo.index, new TreeSet<>());
        for (LotGeo geo : lots) {
            if (geo.base.shape == Plan.Shape.RECT) {
                for (int side = 0; side < 4; side++) {
                    int length = geo.base.sideLength(side);
                    int runFrom = -1;
                    int runOther = -1;
                    for (int t = 0; t <= length; t++) {
                        int other = -1;
                        if (t < length) {
                            int[] cell = geo.base.along(side, t);
                            other = coverer(geo.index, cell[0], cell[1]);
                        }
                        if (other != runOther) {
                            if (runOther >= 0) add(geo.index, side, runFrom, t - 1, runOther);
                            runFrom = t;
                            runOther = other;
                        }
                    }
                }
            } else {
                Set<Integer> others = new TreeSet<>();
                for (int[] cell : geo.base.cells()) {
                    if (geo.base.depth(cell[0], cell[1]) != 1) continue;
                    int other = coverer(geo.index, cell[0], cell[1]);
                    if (other >= 0) others.add(other);
                }
                for (int other : others) add(geo.index, -1, -1, -1, other);
            }
        }
    }

    private void add(int lot, int side, int from, int to, int other) {
        shared.add(new Shared(lot, side, from, to, other));
        graph.get(lot).add(other);
        graph.get(other).add(lot);
    }

    /** The first other lot whose ground plan has the block (-1: none). */
    private int coverer(int self, int x, int z) {
        for (LotGeo other : lots) if (other.index != self && other.base.contains(x, z)) return other.index;
        return -1;
    }

    /** The lots next to lot {@code index}. */
    public Set<Integer> neighbours(int index) {
        return graph.getOrDefault(index, Collections.emptySet());
    }

    /** The lots other than {@code self} that cover the block at height {@code y} with a storey. */
    public List<LotGeo> coveringAt(int self, int x, int y, int z) {
        List<LotGeo> out = new ArrayList<>();
        for (LotGeo other : lots) if (other.index != self && other.covers(x, y, z)) out.add(other);
        return out;
    }

    /** The lot (other than {@code self}) whose ground plan has the column, the highest first (-1: none). */
    public LotGeo over(int self, int x, int z) {
        LotGeo best = null;
        for (LotGeo other : lots) {
            if (other.index == self || !other.base.contains(x, z)) continue;
            if (best == null || other.eave > best.eave) best = other;
        }
        return best;
    }
}
