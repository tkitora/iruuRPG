package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * (t313, spec 4/6) The roofs, each lot's from its own eaves, as a height field: a gable rises from its eaves by its slope, a hip and a pyramid from the
 * nearest of their four sides, a cone from its rim. The slopes are whole ratios (1:2, 1:1, 2:1, 3:1 and more for towers) and the surface is laid as builders
 * lay it (1:2 a slab then a stair, 1:1 stairs, k:1 full blocks then a stair), in a shell two or three blocks thick. How each side of a roof ends is decided by
 * the lot beside it: none, or a lower one: an eave (or a gable, by the roof's shape); a higher wall: the roof stops against it (and the blocks along the wall
 * are flashing); the same eaves in the same roof group: the roofs join, each running on past the shared side by half its width, the higher of the two kept
 * (so the valleys come by themselves). A shed leaning on a wall is high on that side.
 */
final class Roofs {

    private Roofs() {
    }

    /** How a side of a roof ends. */
    enum Edge {
        EAVE, GABLE, ABUT, JOIN
    }

    /** One lot's roof: its shape, its sides, the field over its plan grown by the overhangs (or run on past joined sides). */
    static final class LotRoof {
        final Layout.LotGeo geo;
        final Plan.RoofType type;
        double p;
        final int thick;
        /** The ridge along x (0) or z (1). */
        int ridge;
        final Edge[] edges = new Edge[4];
        final int[] ext = new int[4];
        final int[] next = {-1, -1, -1, -1};
        Area domain;
        int group;
        final Map<Long, Col> cols = new HashMap<>();
        /** The highest row of the roof (its ridge or its tip), for the checks. */
        int ridgeRow = Integer.MIN_VALUE;
        /** The ridge's cap (the kit's ridge cap: slab, full, full_cap). */
        String cap = "slab";

        LotRoof(Layout.LotGeo geo) {
            this.geo = geo;
            this.type = geo.lot.roof.type;
            this.p = geo.lot.roof.slope();
            this.thick = Math.max(2, Math.min(3, geo.lot.roof.thickness));
        }

        /**
         * Whether the roof slopes up from side {@code side}: a gable's sides along the ridge, every side of a hip or a pyramid, a shed's low side. A side joined
         * to another roof still slopes when it runs along the ridge (the main block's eaves, the valley made by the joined roof); one across the ridge runs on
         * into the other roof instead, and a side against a higher wall stops there.
         */
        boolean slopes(int side) {
            boolean along = ridge == 0 ? side % 2 == 0 : side % 2 == 1;
            if (edges[side] == Edge.ABUT && type != Plan.RoofType.SHED) return false;
            if (edges[side] == Edge.JOIN && !along) return false;
            return switch (type) {
                case GABLE, HALF_HIP -> along;
                case HIP, PYRAMID -> true;
                case SHED -> side == low();
                default -> false;
            };
        }

        /** A shed's low side: the one given, else away from a wall it leans on, else the front. */
        int low() {
            int given = geo.lot.roof.low;
            if (given >= 0) return given;
            for (int side = 0; side < 4; side++) if (edges[side] == Edge.ABUT) return (side + 2) % 4;
            return 2;
        }
    }

    /** A column of a roof: the row of its top block and the shape of it, the way it rises, its height (for the higher of two), and what it is. */
    static final class Col {
        int lot;
        double h;
        int top;
        Cell.Form shape;
        int facing;
        boolean edge;
        boolean ridge;
        boolean hip;
    }

    /** What the roofs made, for the walls of the gables, the parts on the roofs and the checks. */
    static final class Result {
        final List<LotRoof> roofs = new ArrayList<>();
        /** By roof group (lots whose roofs join), the columns kept and their lowest rows. */
        final Map<Integer, Map<Long, Col>> tops = new HashMap<>();
        final Map<Integer, Map<Long, Integer>> bottoms = new HashMap<>();

        LotRoof of(int lot) {
            for (LotRoof roof : roofs) if (roof.geo.index == lot) return roof;
            return null;
        }

        /** The columns of the roof group lot {@code lot}'s roof is in. */
        Map<Long, Col> top(int lot) {
            LotRoof roof = of(lot);
            return roof == null ? Map.of() : tops.getOrDefault(roof.group, Map.of());
        }

        /** The lowest rows of the columns of lot {@code lot}'s roof group. */
        Map<Long, Integer> bottom(int lot) {
            LotRoof roof = of(lot);
            return roof == null ? Map.of() : bottoms.getOrDefault(roof.group, Map.of());
        }
    }

    static Result build(Layout layout, Grid grid, Kit kit, Random random) {
        Result result = new Result();
        Kit.Part ridgeCap = kit.get("ridge_cap");
        for (Layout.LotGeo geo : layout.lots) {
            LotRoof roof = new LotRoof(geo);
            if (ridgeCap != null) roof.cap = ridgeCap.text("style", "slab");
            result.roofs.add(roof);
        }
        for (LotRoof roof : result.roofs) sides(layout, roof, result);
        groups(result);
        for (LotRoof roof : result.roofs) {
            field(roof);
            lean(layout, roof);
        }
        // the roofs of a group put together, the higher kept; each group's shell
        Map<Integer, Map<Long, Col>> merged = new HashMap<>();
        for (LotRoof roof : result.roofs) {
            Map<Long, Col> into = merged.computeIfAbsent(roof.group, unused -> new HashMap<>());
            for (Map.Entry<Long, Col> entry : roof.cols.entrySet()) {
                Col old = into.get(entry.getKey());
                Col col = entry.getValue();
                if (old == null || col.h > old.h + 1e-9 || Math.abs(col.h - old.h) <= 1e-9 && col.lot < old.lot) into.put(entry.getKey(), col);
            }
        }
        for (Map.Entry<Integer, Map<Long, Col>> group : merged.entrySet()) {
            result.tops.put(group.getKey(), group.getValue());
            result.bottoms.put(group.getKey(), shell(layout, result, group.getValue(), grid));
        }
        // the walls of the gables, under their roofs
        for (LotRoof roof : result.roofs) gables(layout, roof, result, grid, kit, random);
        clip(layout, result, grid);
        return result;
    }

    // ---- the sides ---------------------------------------------------------------------------------------------------------------

    /** How each side of a lot's roof ends, by the lot beside it (the one covering most of it), and the ridge's way. */
    private static void sides(Layout layout, LotRoof roof, Result result) {
        Layout.LotGeo geo = roof.geo;
        Area top = geo.top();
        int[] covered = new int[4];
        for (int side = 0; side < 4; side++) {
            if (top.shape != Plan.Shape.RECT) {
                roof.edges[side] = Edge.EAVE;
                continue;
            }
            Map<Integer, Integer> counts = new HashMap<>();
            int length = top.sideLength(side);
            for (int t = 0; t < length; t++) {
                int[] at = top.along(side, t);
                int[] out = Area.out(side);
                // (the lot whose plan has the side's block or the one beyond it)
                Layout.LotGeo other = layout.over(geo.index, at[0], at[1]);
                if (other == null) other = layout.over(geo.index, at[0] + out[0], at[1] + out[1]);
                if (other != null) counts.merge(other.index, 1, Integer::sum);
            }
            int best = -1;
            int most = 0;
            for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > most) {
                    most = entry.getValue();
                    best = entry.getKey();
                }
            }
            covered[side] = most;
            roof.edges[side] = null;
            if (best >= 0 && most * 2 >= length) {
                Layout.LotGeo other = layout.lots.get(best);
                roof.next[side] = best;
                if (other.eave > geo.eave) roof.edges[side] = Edge.ABUT;
                else if (other.eave == geo.eave && other.lot.roof.group == geo.lot.roof.group && other.lot.roof.type != Plan.RoofType.NONE
                        && other.lot.roof.type != Plan.RoofType.FLAT && roof.type != Plan.RoofType.FLAT) roof.edges[side] = Edge.JOIN;
            }
        }
        // the ridge: as set; else across a side joined to or leaning on a lot at least as wide there (a wing's gable end runs into the main block, whose own
        // ridge stays along it); else along the longer side
        int ridge = geo.lot.roof.ridge;
        if (ridge < 0) {
            ridge = top.width() >= top.depthSpan() ? 0 : 1;
            for (int side = 0; side < 4; side++) {
                if (roof.edges[side] != Edge.JOIN && roof.edges[side] != Edge.ABUT) continue;
                Layout.LotGeo other = layout.lots.get(roof.next[side]);
                int mine = top.sideLength(side);
                int theirs = side % 2 == 0 ? other.top().width() : other.top().depthSpan();
                if (mine <= theirs) ridge = side % 2 == 0 ? 1 : 0;
            }
        }
        roof.ridge = ridge;
        for (int side = 0; side < 4; side++) {
            if (roof.edges[side] != null) continue;
            roof.edges[side] = switch (roof.type) {
                case GABLE, HALF_HIP -> (ridge == 0) == (side % 2 == 0) ? Edge.EAVE : Edge.GABLE;
                case SHED -> side == roof.low() ? Edge.EAVE : Edge.GABLE;
                default -> Edge.EAVE;
            };
        }
        // how far past the walls each side reaches: the overhang (none against a wall); a joined side runs on half the roof's width into the other's
        int[] overhang = geo.lot.roof.overhang;
        for (int side = 0; side < 4; side++) {
            int extra = switch (roof.edges[side]) {
                case ABUT -> 0;
                case JOIN -> {
                    int across = (top.sideLength(side) - 1) / 2;
                    Layout.LotGeo other = layout.lots.get(roof.next[side]);
                    int room = side % 2 == 0 ? other.top().depthSpan() - 1 : other.top().width() - 1;
                    yield Math.max(0, Math.min(across, room));
                }
                default -> Math.max(0, Math.min(2, overhang[side]));
            };
            // (a steep roof hangs its eaves low: at most a block out)
            if (roof.edges[side] == Edge.EAVE && roof.p >= 2.0) extra = Math.min(extra, 1);
            roof.ext[side] = extra;
        }
        if (top.shape != Plan.Shape.RECT) {
            int most = 0;
            for (int value : overhang) most = Math.max(most, value);
            java.util.Arrays.fill(roof.ext, Math.min(most, 1));
        }
        roof.domain = top.grown(roof.ext);
    }

    /** The lots whose roofs join, put in groups (a lot alone is a group of its own). */
    private static void groups(Result result) {
        int n = result.roofs.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            LotRoof roof = result.roofs.get(i);
            for (int side = 0; side < 4; side++) {
                if (roof.edges[side] != Edge.JOIN) continue;
                int a = find(parent, i);
                int b = find(parent, roof.next[side]);
                if (a != b) parent[a] = b;
            }
        }
        for (int i = 0; i < n; i++) result.roofs.get(i).group = find(parent, i);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) i = parent[i] = parent[parent[i]];
        return i;
    }

    // ---- the field -----------------------------------------------------------------------------------------------------------------

    /** The columns of a lot's roof: how far each is in from the eaves, and from that its top block. */
    private static void field(LotRoof roof) {
        Plan.RoofType type = roof.type;
        if (type == Plan.RoofType.NONE) return;
        Area d = roof.domain;
        Area top = roof.geo.top();
        int eave = roof.geo.eave;
        if (type == Plan.RoofType.FLAT) {
            for (int[] cell : d.cells()) {
                Col col = new Col();
                col.lot = roof.geo.index;
                col.top = eave;
                col.h = eave + 1;
                col.shape = Cell.Form.FULL;
                col.edge = d.depth(cell[0], cell[1]) == 1;
                roof.cols.put(Area.key(cell[0], cell[1]), col);
            }
            roof.ridgeRow = eave + 1;
            return;
        }
        // the wall line's distance in from the edge (the same for every sloping side: the sides with less overhang start further in)
        int reference = 0;
        for (int side = 0; side < 4; side++) if (roof.slopes(side)) reference = Math.max(reference, roof.ext[side]);
        if (d.shape != Plan.Shape.RECT) reference = roof.ext[0];
        double base = eave + roof.thick - (reference + 1) * roof.p;
        double halfX = (d.x1 - d.x0) / 2.0;
        double halfZ = (d.z1 - d.z0) / 2.0;
        double most = Math.max(halfX, halfZ);
        double hipDrop = 0;
        if (type == Plan.RoofType.HALF_HIP) hipDrop = Math.max(1, Math.round((roof.ridge == 0 ? halfZ : halfX) * 0.5));
        double cx = d.centerX();
        double cz = d.centerZ();
        for (int[] cell : d.cells()) {
            int x = cell[0];
            int z = cell[1];
            double[] dist = {z - d.z0, d.x1 - x, d.z1 - z, x - d.x0};
            double i = Double.MAX_VALUE;
            int facing = 0;
            int ties = 0;
            if (d.shape == Plan.Shape.ROUND) {
                // (the rim a ring all as high, so the eaves go round evenly; inside it by the distance from the middle, a cone)
                i = !top.contains(x, z) ? 0 : Math.max(1.0, d.radius() - Math.hypot(x - cx, z - cz));
                double dx = cx - x;
                double dz = cz - z;
                facing = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
            } else if (d.shape == Plan.Shape.OCTAGON) {
                i = d.depth(x, z) - 1;
                double dx = cx - x;
                double dz = cz - z;
                facing = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
            } else {
                for (int side = 0; side < 4; side++) {
                    double value;
                    if (roof.slopes(side)) {
                        value = dist[side] - roof.ext[side] + reference;
                        if (type == Plan.RoofType.PYRAMID) value = (dist[side] - roof.ext[side] + reference) * (most / Math.max(0.5, side % 2 == 0 ? halfZ : halfX));
                    } else if (type == Plan.RoofType.HALF_HIP && roof.edges[side] == Edge.GABLE) {
                        // (the top of a half-hipped gable leans back: a hip from this high up)
                        value = dist[side] - roof.ext[side] + reference + hipDrop;
                    } else {
                        continue;
                    }
                    if (value < i - 1e-9) {
                        i = value;
                        facing = (side + 2) % 4;
                        ties = 1;
                    } else if (Math.abs(value - i) <= 1e-9) {
                        ties++;
                    }
                }
            }
            if (i == Double.MAX_VALUE || i < -1e-9) continue;
            // (a steep roof's eaves are not hung down the walls: no lower than the beam's row, flaring out there)
            double h = Math.max(eave, base + (i + 1) * roof.p);
            Col col = new Col();
            col.lot = roof.geo.index;
            col.h = h;
            col.top = (int) Math.ceil(h - 1e-9) - 1;
            double frac = h - col.top;
            col.shape = roof.p < 1.0 && frac <= 0.5 + 1e-9 ? Cell.Form.SLAB : Cell.Form.STAIRS;
            col.facing = facing;
            col.hip = ties >= 2;
            roof.cols.put(Area.key(x, z), col);
        }
        // the ridge: a column as high as both its neighbours across the slope (its top a full block and a cap over it), the tip of a cone or a pyramid
        double highest = Double.NEGATIVE_INFINITY;
        for (Col col : roof.cols.values()) highest = Math.max(highest, col.h);
        for (Map.Entry<Long, Col> entry : roof.cols.entrySet()) {
            Col col = entry.getValue();
            int x = (int) ((entry.getKey() >> 32) - 100_000);
            int z = (int) ((entry.getKey() & 0xFFFFFFFFL) - 100_000);
            boolean peak = true;
            for (int way = 0; way < 4; way++) {
                Col next = roof.cols.get(Area.key(x + Cell.STEP[way][0], z + Cell.STEP[way][1]));
                if (next != null && next.h > col.h + 1e-9) peak = false;
            }
            // (only the top: a half-hip's ends, lower, are slopes)
            col.ridge = peak && type != Plan.RoofType.SHED && col.h >= highest - roof.p / 2 - 1e-9;
            // (a cone's, an octagon's or a pyramid's ridge is its tip only)
            if (d.shape != Plan.Shape.RECT || type == Plan.RoofType.PYRAMID) col.ridge = col.h >= highest - 1e-9;
            col.edge = !roof.cols.containsKey(Area.key(x + 1, z)) || !roof.cols.containsKey(Area.key(x - 1, z)) || !roof.cols.containsKey(Area.key(x, z + 1))
                    || !roof.cols.containsKey(Area.key(x, z - 1));
            roof.ridgeRow = Math.max(roof.ridgeRow, col.top);
        }
    }

    /**
     * A shed leaning on a higher wall stays under that lot's eaves: a gentler slope while one fits (1:1, then 1:2), else its top cut flat under them.
     */
    private static void lean(Layout layout, LotRoof roof) {
        if (roof.type != Plan.RoofType.SHED) return;
        int under = Integer.MAX_VALUE;
        for (int side = 0; side < 4; side++) {
            if (roof.edges[side] == Edge.ABUT) under = Math.min(under, layout.lots.get(roof.next[side]).eave - 1);
        }
        if (under == Integer.MAX_VALUE) return;
        while (roof.ridgeRow > under && roof.p > Plan.PITCHES[0]) {
            double lower = Plan.PITCHES[0];
            for (double pitch : Plan.PITCHES) if (pitch < roof.p) lower = pitch;
            roof.p = lower;
            roof.cols.clear();
            roof.ridgeRow = Integer.MIN_VALUE;
            field(roof);
        }
        if (roof.ridgeRow <= under) return;
        for (Col col : roof.cols.values()) {
            if (col.top < under) continue;
            col.top = under;
            col.h = under + 1;
            col.shape = Cell.Form.FULL;
        }
        roof.ridgeRow = under;
    }

    // ---- the shell -----------------------------------------------------------------------------------------------------------------

    /**
     * A group's roof as blocks: each column's top in its shape, the blocks under it down far enough that the shell is {@code thick} blocks and closes on the
     * columns round it (a steep roof's steps have no holes); its edges eaves, its ridge capped, its hips marked.
     */
    private static Map<Long, Integer> shell(Layout layout, Result result, Map<Long, Col> cols, Grid grid) {
        Map<Long, Integer> bottoms = new HashMap<>();
        for (Map.Entry<Long, Col> entry : cols.entrySet()) {
            long key = entry.getKey();
            Col col = entry.getValue();
            int x = (int) ((key >> 32) - 100_000);
            int z = (int) ((key & 0xFFFFFFFFL) - 100_000);
            LotRoof roof = result.of(col.lot);
            // (down to the top of the lowest column beside it: the steps of a steep roof touch face to face, no gap at their corners)
            int lowest = col.top;
            for (int way = 0; way < 4; way++) {
                Col next = cols.get(Area.key(x + Cell.STEP[way][0], z + Cell.STEP[way][1]));
                if (next != null) lowest = Math.min(lowest, next.top);
            }
            int bottom = Math.min(col.top - (roof.thick - 1), lowest);
            if (roof.type == Plan.RoofType.FLAT) bottom = col.top;
            grid.owner = col.lot;
            Role role = col.edge ? Role.EAVE : Role.ROOF;
            for (int y = bottom; y < col.top; y++) grid.put(x, y, z, Cell.full(role));
            Role topRole = col.ridge || col.hip ? Role.RIDGE : role;
            if (roof.type == Plan.RoofType.FLAT) {
                grid.put(x, col.top, z, Cell.full(Role.ROOF));
                if (col.edge) grid.put(x, col.top + 1, z, Cell.slab(Role.EAVE, false));
            } else if (col.ridge) {
                // (the ridge's cap, as the kit's ridge cap says: a slab (the roof's line goes on), a full block, a full block with a slab on it)
                switch (roof.cap) {
                    case "full" -> grid.put(x, col.top, z, Cell.full(Role.RIDGE));
                    case "full_cap" -> {
                        grid.put(x, col.top, z, Cell.full(Role.RIDGE));
                        grid.put(x, col.top + 1, z, Cell.slab(Role.RIDGE, false));
                    }
                    default -> grid.put(x, col.top, z, Cell.slab(Role.RIDGE, false));
                }
            } else if (col.shape == Cell.Form.SLAB) {
                grid.put(x, col.top, z, Cell.slab(topRole, false));
            } else {
                grid.put(x, col.top, z, Cell.stairs(topRole, col.facing, false));
            }
            bottoms.put(key, bottom);
        }
        grid.owner = -1;
        return bottoms;
    }

    // ---- the gables ----------------------------------------------------------------------------------------------------------------

    /** The wall of each gable end that is outside (no lot joined to it or higher beside it), under the roof, drawn as the kit's gable of the walls' set. */
    private static void gables(Layout layout, LotRoof roof, Result result, Grid grid, Kit kit, Random random) {
        Area top = roof.geo.top();
        if (top.shape != Plan.Shape.RECT || roof.type == Plan.RoofType.NONE || roof.type == Plan.RoofType.FLAT) return;
        int k = roof.geo.floors() - 1;
        Plan.Floor floor = roof.geo.lot.floors.get(k);
        String set = roof.geo.lot.roof.gable;
        if (set == null || set.isBlank() || set.equals("auto")) set = Walls.set(roof.geo, k);
        grid.owner = roof.geo.index;
        for (int side = 0; side < 4; side++) {
            if (roof.edges[side] != Edge.GABLE) continue;
            int length = top.sideLength(side);
            int eave = roof.geo.eave;
            int[] room = new int[length];
            for (int t = 0; t < length; t++) {
                int[] at = top.along(side, t);
                Integer bottom = result.bottom(roof.geo.index).get(Area.key(at[0], at[1]));
                room[t] = bottom == null ? 0 : Math.max(0, bottom - eave);
                // (where another lot covers it, no gable)
                if (!layout.coveringAt(roof.geo.index, at[0], eave, at[1]).isEmpty()) room[t] = 0;
            }
            Kit.Part part = kit.pick(Kit.Category.GABLE, length, 99, random, set);
            if (part == null) part = kit.pick(Kit.Category.GABLE, length, 99, random, "plaster");
            if (part == null) continue;
            Walls.SideCanvas canvas = new Walls.SideCanvas(grid, top, side, 0, eave);
            Painter.gable(part, canvas, length, u -> room[Math.max(0, Math.min(length - 1, u))], Walls.plaster(floor));
            // (the corner posts of the gable: up to the roof)
            for (int t : new int[]{0, length - 1}) {
                int[] at = top.along(side, t);
                for (int y = eave; y < eave + room[t]; y++) grid.put(at[0], y, at[1], Cell.log(Role.FRAME, 1));
            }
        }
        grid.owner = -1;
    }

    // ---- against higher walls ------------------------------------------------------------------------------------------------------

    /** A roof's blocks inside a higher lot's walls are taken away; the ones left beside such a wall are flashing. */
    private static void clip(Layout layout, Result result, Grid grid) {
        List<Long> keys = new ArrayList<>(grid.cells().keySet());
        for (long key : keys) {
            Cell cell = grid.cells().get(key);
            if (cell == null || !cell.role().roof()) continue;
            int owner = grid.ownerOf(key);
            if (owner < 0) continue;
            Layout.LotGeo self = layout.lots.get(owner);
            int x = Grid.x(key);
            int y = Grid.y(key);
            int z = Grid.z(key);
            for (Layout.LotGeo other : layout.lots) {
                if (other.index == owner || other.eave <= self.eave) continue;
                if (y >= other.y0 && y < other.eave && other.covers(x, y, z)) {
                    grid.clear(x, y, z);
                    break;
                }
            }
        }
        for (long key : new ArrayList<>(grid.cells().keySet())) {
            Cell cell = grid.cells().get(key);
            if (cell == null || cell.role() != Role.ROOF) continue;
            int owner = grid.ownerOf(key);
            if (owner < 0) continue;
            int x = Grid.x(key);
            int y = Grid.y(key);
            int z = Grid.z(key);
            Layout.LotGeo self = layout.lots.get(owner);
            for (int way = 0; way < 4; way++) {
                int nx = x + Cell.STEP[way][0];
                int nz = z + Cell.STEP[way][1];
                boolean wall = false;
                for (Layout.LotGeo other : layout.lots) {
                    if (other.index != owner && other.eave > self.eave && other.covers(nx, y, nz)) wall = true;
                }
                if (wall && grid.get(x, y + 1, z) == null) {
                    int keep = grid.owner;
                    grid.owner = owner;
                    grid.set(x, y, z, cell.withRole(Role.FLASHING));
                    grid.owner = keep;
                    break;
                }
            }
        }
    }
}
