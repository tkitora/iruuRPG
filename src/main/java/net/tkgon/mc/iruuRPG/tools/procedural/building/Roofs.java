package net.tkgon.mc.iruuRPG.tools.procedural.building;

import net.tkgon.mc.iruuRPG.tools.procedural.ornate.BlockShapes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * (t295) The roofs. A roof's height over its plan is worked out in half blocks: every side of the plan rises inward by its own profile from its outer face, and
 * the roof is the lowest of them (a weighted straight skeleton: for a convex plan the roof's planes meet in its ridges and hips). An L, T, U or cross plan is cut
 * into its rectangles, each roofed alone, and the roofs joined by the highest (so they meet in valleys); a plan that is one polygon (a tower, a cut rectangle) is
 * roofed whole. A side's profile is the slope of the roof's kind: upright (a gable), sloping, upright to a height then sloping (a half-hip), steep then gentle (a
 * gambrel), gentle then steep (bell-cast eaves). The slopes are whole ratios, and each is made the way builders make it: 1:2 a slab then a stair, 1:1 stairs,
 * 2:1 a full block then a stair.
 *
 * <p>The roof is a shell 2 to 3 blocks thick: each block is cut in 8 octants, an octant is filled when the shell covers it, and the block becomes the shape
 * nearest its octants (full 8, a stair's inner corner 7, a stair 6, its outer corner 5, a slab 4). The underside of the eaves comes out as upside-down stairs.
 * Then the trim by the kind of edge: the eaves' edge (a row of upside-down stairs and a log under it), the verges (stairs a block out of the gable, a block over
 * the roof), the ridge (slabs or walls, a post at each end), the hips (their own block), nothing in the valleys; the gables under the roof are filled with the
 * frame and the wall (a block out when asked).
 */
final class Roofs {

    enum Kind {
        VERTICAL, SLOPE, HALF, BROKEN
    }

    /** How a side rises inward: {@code k} (and {@code k2} past {@code at} blocks in; a half-hip rises upright to {@code at} first). */
    record Profile(Kind kind, double k, double k2, double at) {

        static final Profile UPRIGHT = new Profile(Kind.VERTICAL, 0, 0, 0);

        static Profile slope(double k) {
            return new Profile(Kind.SLOPE, k, 0, 0);
        }

        boolean sloped() {
            return kind != Kind.VERTICAL;
        }

        /** The height (a whole number of half blocks) at {@code d} blocks in from the outer face. */
        double height(double d) {
            return switch (kind) {
                case VERTICAL -> Double.POSITIVE_INFINITY;
                case SLOPE -> step(d, k);
                case HALF -> at + step(d, k);
                case BROKEN -> {
                    if (d < at) yield step(d, k);
                    yield step(at - 0.25, k) + step(d - at, k2);
                }
            };
        }
    }

    /**
     * The height of a roof of slope {@code k} at {@code d} blocks in, as builders lay it (the block {@code i} in from the eaves, and its outer or inner half): 1:2 a
     * slab, then a stair, a block up every two; k:1 a stair on k - 1 full blocks, k up every block.
     */
    static double step(double d, double k) {
        if (d < 0) d = 0;
        int i = (int) Math.floor(d);
        boolean inner = d - i >= 0.5;
        if (k < 0.75) {
            if (i % 2 == 0) return i / 2 + 0.5;
            return inner ? (i + 1) / 2 : (i - 1) / 2 + 0.5;
        }
        double whole = Math.max(1.0, Math.round(k));
        return whole * i + (inner ? whole : whole - 0.5);
    }

    /** A side of a piece: its outward normal, a point on its line (a block's middle), how far the outer face is past the line, its profile. */
    record Side(double nx, double nz, double px, double pz, double offset, Profile profile) {

        double distance(double x, double z) {
            return offset + (px - x) * nx + (pz - z) * nz;
        }
    }

    /** A piece of a roof: a convex plan (with the overhang) roofed alone, or a cone. */
    static final class Piece {
        int id;
        Mass mass;
        /** The mass whose field it joins (a dormer's: its parent's). */
        int field;
        Footprint plan;
        final List<Side> sides = new ArrayList<>();
        boolean cone;
        double cx;
        double cz;
        double reach;
        Profile coneProfile;
        int base;
        double thickness;
        double rise;
        /** A dormer's walls: inside them its roof takes the place of the parent's; outside them it lies over it. */
        Footprint walls;

        int eave() {
            return base;
        }

        int ridge() {
            return (int) Math.ceil(base + rise);
        }
    }

    /** A column of half a block's roof: from {@code low} to {@code top}, of piece {@code piece} (its face {@code face}, {@code d} in from it). */
    static final class Col {
        double top;
        double low;
        Piece piece;
        int face;
        double d;
    }

    private final SemanticVoxelGrid grid;
    private final List<Mass> masses;
    final List<Piece> pieces = new ArrayList<>();
    /** The columns of each piece (its own, before joining). */
    private final Map<Piece, Map<Long, Col>> own = new HashMap<>();
    /** The columns kept after joining, by half-column. */
    final Map<Long, List<Col>> kept = new HashMap<>();
    /** The column a roof block was made from (its highest), by block. */
    final Map<Long, Col> owners = new HashMap<>();

    Roofs(SemanticVoxelGrid grid, List<Mass> masses) {
        this.grid = grid;
        this.masses = masses;
    }

    static long half(int hx, int hz) {
        return ((long) (hx + 100_000) << 32) | (hz + 100_000);
    }

    // ---- the pieces --------------------------------------------------------------------------------------------------------------

    /** Cuts the roof of {@code m} into pieces and works out their heights. */
    void add(Mass m) {
        Mass.Roof r = m.roof;
        Footprint top = m.top();
        int o = Math.max(0, r.overhang);
        List<Piece> made = new ArrayList<>();
        if (top.circle()) {
            Piece piece = new Piece();
            piece.plan = top.expand(o);
            piece.cone = true;
            piece.cx = top.centerX();
            piece.cz = top.centerZ();
            piece.reach = piece.plan.radius() + 0.5;
            piece.coneProfile = r.type == Mass.RoofType.BELL ? new Profile(Kind.BROKEN, 0.5, Math.max(1.0, r.pitch), r.rise) : Profile.slope(r.pitch);
            made.add(piece);
        } else if (top.kind() == Footprint.Kind.RECT || top.kind() == Footprint.Kind.UNION) {
            for (int[] rect : top.rects()) {
                int w = rect[2] - rect[0] + 1;
                int d = rect[3] - rect[1] + 1;
                boolean alongX = r.ridge >= 0 && top.kind() == Footprint.Kind.RECT ? r.ridge == 0 : w >= d;
                Piece piece = new Piece();
                piece.plan = Footprint.rect(rect[0] - o, rect[1] - o, w + 2 * o, d + 2 * o);
                int x0 = rect[0] - o;
                int z0 = rect[1] - o;
                int x1 = rect[2] + o;
                int z1 = rect[3] + o;
                double[][] normals = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                double[][] points = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
                for (int way = 0; way < 4; way++) {
                    boolean end = alongX ? way % 2 == 1 : way % 2 == 0;
                    piece.sides.add(new Side(normals[way][0], normals[way][1], points[way][0], points[way][1], 0.5, profile(r, end, way)));
                }
                made.add(piece);
            }
        } else {
            Footprint plan = top.expand(o);
            int[] box = top.box();
            boolean alongX = r.ridge >= 0 ? r.ridge == 0 : box[2] - box[0] >= box[3] - box[1];
            Piece piece = new Piece();
            piece.plan = plan;
            for (Footprint.Edge edge : plan.edges()) {
                double nx = edge.dz();
                double nz = -edge.dx();
                double length = Math.hypot(nx, nz);
                nx /= length;
                nz /= length;
                boolean end = alongX ? Math.abs(nx) > 0.9 : Math.abs(nz) > 0.9;
                int way = Math.abs(nx) >= Math.abs(nz) ? (nx > 0 ? 1 : 3) : (nz > 0 ? 2 : 0);
                piece.sides.add(new Side(nx, nz, edge.x(), edge.z(), 0.5 * (Math.abs(nx) + Math.abs(nz)), profile(r, end, way)));
            }
            made.add(piece);
        }
        double steepest = r.pitch;
        double thickness = Math.max(1, r.thickness);
        if (steepest >= 2.5) thickness = Math.max(thickness, Math.round(steepest) + 0.5);
        for (Piece piece : made) {
            piece.id = pieces.size();
            piece.mass = m;
            piece.field = m.dormer && m.parent >= 0 ? m.parent : m.id;
            piece.thickness = thickness;
            if (m.dormer) piece.walls = m.top();
            Map<Long, Col> cols = heights(piece);
            double eaveAt = 0.0;
            for (Side side : piece.sides) {
                if (side.profile().sloped()) {
                    eaveAt = side.profile().height(o + 0.25);
                    break;
                }
            }
            if (piece.cone) eaveAt = piece.coneProfile.height(o + 0.25);
            // the eaves as high as they can be with the roof's underside on the top of the walls all along the eaves' wall line (none of it in the air)
            double limit = Double.POSITIVE_INFINITY;
            Footprint walls = m.top();
            for (Map.Entry<Long, Col> entry : cols.entrySet()) {
                Col col = entry.getValue();
                if (walls.depth(Math.floorDiv(hx(entry.getKey()), 2), Math.floorDiv(hz(entry.getKey()), 2)) != 1) continue;
                if (!piece.cone && (col.face < 0 || col.d > o + 1.0)) continue;
                limit = Math.min(limit, m.wallTop() + 1 + thickness - col.top);
            }
            if (Double.isInfinite(limit)) limit = m.wallTop() + 1 + thickness - eaveAt;
            piece.base = r.topAt >= 0 ? (int) Math.floor(r.topAt - piece.rise) : (int) Math.floor(limit);
            for (Col col : cols.values()) {
                col.top += piece.base;
                col.low = col.top - thickness;
            }
            own.put(piece, cols);
            pieces.add(piece);
        }
    }

    /** The profile of a side by the roof's kind: {@code end} is a side across the ridge (a gable's), {@code way} the way it faces. */
    private static Profile profile(Mass.Roof r, boolean end, int way) {
        double k = r.pitch;
        return switch (r.type) {
            case GABLE -> end ? Profile.UPRIGHT : Profile.slope(k);
            case HIP, CONE -> Profile.slope(k);
            case HALF_HIP -> end ? new Profile(Kind.HALF, k, 0, Math.max(1, r.rise)) : Profile.slope(k);
            case GAMBREL -> {
                double gentle = Math.min(k, 1.0);
                yield end ? Profile.UPRIGHT : new Profile(Kind.BROKEN, gentle * 2.0, gentle, Math.max(1, r.rise));
            }
            case BELL -> new Profile(Kind.BROKEN, 0.5, Math.max(1.0, k), Math.max(1, r.rise));
            case SHED -> way == r.way ? Profile.slope(k) : Profile.UPRIGHT;
        };
    }

    /** The heights (from the eaves, before the base is added) of every half-column of a piece: the lowest of its sides' (a cone's: by the distance from its middle). */
    private static Map<Long, Col> heights(Piece piece) {
        Map<Long, Col> cols = new HashMap<>();
        double rise = 0.0;
        for (int[] cell : piece.plan.cells()) {
            for (int ox = 0; ox < 2; ox++) {
                for (int oz = 0; oz < 2; oz++) {
                    double px = cell[0] - 0.25 + 0.5 * ox;
                    double pz = cell[1] - 0.25 + 0.5 * oz;
                    double h = Double.POSITIVE_INFINITY;
                    int face = -1;
                    double depth = 0.0;
                    if (piece.cone) {
                        depth = piece.reach - Math.hypot(px - piece.cx, pz - piece.cz);
                        h = piece.coneProfile.height(depth);
                        double angle = Math.atan2(pz - piece.cz, px - piece.cx);
                        face = Math.floorMod((int) Math.floor((angle + Math.PI / 8.0) / (Math.PI / 4.0)), 8);
                    } else {
                        for (int index = 0; index < piece.sides.size(); index++) {
                            Side side = piece.sides.get(index);
                            if (!side.profile().sloped()) continue;
                            double d = side.distance(px, pz);
                            double here = side.profile().height(d);
                            if (here < h - 1.0E-9) {
                                h = here;
                                face = index;
                                depth = d;
                            }
                        }
                        if (face < 0) h = 0.0;
                    }
                    Col col = new Col();
                    col.top = h;
                    col.piece = piece;
                    col.face = face;
                    col.d = depth;
                    cols.put(half(cell[0] * 2 + ox, cell[1] * 2 + oz), col);
                    rise = Math.max(rise, h);
                }
            }
        }
        piece.rise = rise;
        return cols;
    }

    /** The heights of a mass's roof field where it is at {@code (x, z)} (the highest of its pieces' half-columns there), or NaN. */
    double fieldTop(int mass, int x, int z) {
        double best = Double.NaN;
        for (Piece piece : pieces) {
            if (piece.field != mass) continue;
            Map<Long, Col> cols = own.get(piece);
            for (int ox = 0; ox < 2; ox++) {
                for (int oz = 0; oz < 2; oz++) {
                    Col col = cols.get(half(x * 2 + ox, z * 2 + oz));
                    if (col != null && (Double.isNaN(best) || col.top > best)) best = col.top;
                }
            }
        }
        return best;
    }

    /** The lowest underside of a mass's roof field at {@code (x, z)}, or NaN. */
    double fieldLow(int mass, int x, int z) {
        double best = Double.NaN;
        for (Piece piece : pieces) {
            if (piece.field != mass) continue;
            Map<Long, Col> cols = own.get(piece);
            for (int ox = 0; ox < 2; ox++) {
                for (int oz = 0; oz < 2; oz++) {
                    Col col = cols.get(half(x * 2 + ox, z * 2 + oz));
                    if (col != null && (Double.isNaN(best) || col.low < best)) best = col.low;
                }
            }
        }
        return best;
    }

    // ---- joining and the blocks ------------------------------------------------------------------------------------------------

    /**
     * Joins the pieces and makes the blocks. The pieces of a mass meet by the highest (valleys); a dormer's piece takes the place of its parent's inside its walls and
     * lies over it outside them. A mass's roof where it is over another mass's top plan, lower than that mass's roof there, is cut away (it would go through its
     * walls). Then every block of the shell, by its octants.
     */
    void build() {
        Map<Integer, Map<Long, List<Col>>> fields = new HashMap<>();
        for (Piece piece : pieces) {
            if (piece.walls != null) continue;
            Map<Long, List<Col>> field = fields.computeIfAbsent(piece.field, unused -> new HashMap<>());
            for (Map.Entry<Long, Col> entry : own.get(piece).entrySet()) {
                List<Col> list = field.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>());
                if (list.isEmpty()) list.add(entry.getValue());
                else if (entry.getValue().top > list.get(0).top) list.set(0, entry.getValue());
            }
        }
        for (Piece piece : pieces) {
            if (piece.walls == null) continue;
            Map<Long, List<Col>> field = fields.computeIfAbsent(piece.field, unused -> new HashMap<>());
            for (Map.Entry<Long, Col> entry : own.get(piece).entrySet()) {
                long key = entry.getKey();
                Col col = entry.getValue();
                List<Col> list = field.computeIfAbsent(key, unused -> new ArrayList<>());
                int cx = Math.floorDiv(hx(key), 2);
                int cz = Math.floorDiv(hz(key), 2);
                boolean inside = piece.walls.contains(cx, cz);
                if (list.isEmpty()) list.add(col);
                else if (col.top > list.get(0).top) {
                    if (inside) list.set(0, col);
                    else list.add(col);
                }
            }
        }
        // a mass's roof inside another mass's top plan, under that mass's roof, is cut away
        Set<Long> keys = new HashSet<>();
        for (Map<Long, List<Col>> field : fields.values()) keys.addAll(field.keySet());
        for (long key : keys) {
            int cx = Math.floorDiv(hx(key), 2);
            int cz = Math.floorDiv(hz(key), 2);
            List<Col> out = new ArrayList<>();
            for (Map.Entry<Integer, Map<Long, List<Col>>> entry : fields.entrySet()) {
                List<Col> list = entry.getValue().get(key);
                if (list == null || list.isEmpty()) continue;
                boolean cut = false;
                for (Map.Entry<Integer, Map<Long, List<Col>>> other : fields.entrySet()) {
                    if (other.getKey().equals(entry.getKey())) continue;
                    List<Col> theirs = other.getValue().get(key);
                    if (theirs == null || theirs.isEmpty()) continue;
                    Mass mass = masses.get(other.getKey());
                    if (mass.minor) continue;
                    if (mass.top().contains(cx, cz) && theirs.get(0).top > list.get(0).top) cut = true;
                }
                if (!cut) out.addAll(list);
            }
            if (!out.isEmpty()) kept.put(key, out);
        }
        // the blocks
        Set<Long> cells = new HashSet<>();
        for (long key : kept.keySet()) cells.add(SemanticVoxelGrid.pack(Math.floorDiv(hx(key), 2), 0, Math.floorDiv(hz(key), 2)));
        for (long cellKey : cells) {
            int x = SemanticVoxelGrid.x(cellKey);
            int z = SemanticVoxelGrid.z(cellKey);
            List<List<Col>> halves = new ArrayList<>();
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (int oz = 0; oz < 2; oz++) {
                for (int ox = 0; ox < 2; ox++) {
                    List<Col> list = kept.getOrDefault(half(x * 2 + ox, z * 2 + oz), List.of());
                    halves.add(list);
                    for (Col col : list) {
                        low = Math.min(low, col.low);
                        high = Math.max(high, col.top);
                    }
                }
            }
            if (low > high) continue;
            for (int y = (int) Math.floor(low); y < Math.ceil(high); y++) {
                int mask = 0;
                Col owner = null;
                for (int oz = 0; oz < 2; oz++) {
                    for (int ox = 0; ox < 2; ox++) {
                        for (Col col : halves.get(oz * 2 + ox)) {
                            for (int oy = 0; oy < 2; oy++) {
                                double bottom = y + 0.5 * oy;
                                if (col.low <= bottom + 1.0E-9 && col.top >= bottom + 0.5 - 1.0E-9) {
                                    mask |= 1 << (ox + 2 * oy + 4 * oz);
                                    if (owner == null || col.top > owner.top) owner = col;
                                }
                            }
                        }
                    }
                }
                Cell cell = Cell.of(Role.ROOF, BlockShapes.nearest(mask));
                if (cell == null) continue;
                grid.put(x, y, z, cell);
                owners.put(SemanticVoxelGrid.pack(x, y, z), owner);
            }
        }
    }

    static int hx(long key) {
        return (int) (key >> 32) - 100_000;
    }

    static int hz(long key) {
        return (int) (key & 0xFFFFFFFFL) - 100_000;
    }

    /** The kept columns of {@code piece} over the cell {@code (x, z)}. */
    List<Col> colsOf(Piece piece, int x, int z) {
        List<Col> out = new ArrayList<>();
        for (int oz = 0; oz < 2; oz++) {
            for (int ox = 0; ox < 2; ox++) {
                for (Col col : kept.getOrDefault(half(x * 2 + ox, z * 2 + oz), List.of())) if (col.piece == piece) out.add(col);
            }
        }
        return out;
    }

    /** The highest (or lowest) block of the roof in the column made from {@code piece}; Integer.MIN_VALUE when there is none. */
    int voxel(Piece piece, int x, int z, boolean highest) {
        int found = Integer.MIN_VALUE;
        for (int y = piece.base - 12; y <= piece.ridge() + 4; y++) {
            Col col = owners.get(SemanticVoxelGrid.pack(x, y, z));
            if (col == null || col.piece != piece) continue;
            Cell cell = grid.get(x, y, z);
            if (cell == null || !(cell.role().roof() || cell.role() == Role.HIP || cell.role() == Role.RIDGE || cell.role() == Role.EAVE)) continue;
            if (highest) found = y;
            else return y;
        }
        return found;
    }

    /** Whether any roof is over the half-column. */
    boolean covered(int hx, int hz) {
        List<Col> list = kept.get(half(hx, hz));
        return list != null && !list.isEmpty();
    }

    // ---- under the roof: the gables, the corbels under the eaves --------------------------------------------------------------

    /**
     * Closes every gap between the top of a mass's walls and its roof: on the wall line the posts go up (the bays open), a block behind it the wall (a window in the
     * middle of a high gable); with {@link Mass.Roof#gableOut} the frame stands a block out, the wall on the wall line, a corbel under each post. Then a corbel
     * under the eaves at every post of the top storey where the roof is just over the wall.
     */
    void underRoof(Mass m, Ring ring) {
        if (m.postsOnly || m.roof.open) return;
        Footprint top = m.top();
        int wallTop = m.wallTop();
        boolean out = m.roof.gableOut;
        for (int[] cell : top.cells()) {
            int x = cell[0];
            int z = cell[1];
            int d = top.depth(x, z);
            if (d > 2 || inTaller(m, x, z)) continue;
            Ring.Spot spot = ring.spot(x, z);
            int roof = roofOver(x, z, wallTop);
            if (roof == Integer.MIN_VALUE) continue;
            int gap = roof - wallTop - 1;
            if (gap <= 0) continue;
            boolean high = gap >= 2;
            if (d == 1) {
                int fx = x;
                int fz = z;
                if (out && high && spot != null && (spot.nx() == 0) != (spot.nz() == 0)) {
                    fx = x + spot.nx();
                    fz = z + spot.nz();
                    int roofOut = roofOver(fx, fz, wallTop);
                    int outGap = roofOut == Integer.MIN_VALUE ? 0 : roofOut - wallTop - 1;
                    if (spot.post()) {
                        for (int y = wallTop + 1; y <= wallTop + outGap; y++) grid.put(fx, y, fz, Cell.log(Role.POST, 1));
                        if (!grid.has(fx, wallTop, fz)) grid.put(fx, wallTop, fz, Walls.corbel(m.corbel, spot.nx(), spot.nz()));
                    }
                    for (int y = wallTop + 1; y <= wallTop + gap; y++) grid.put(x, y, z, wallOrGlass(spot, m, y - wallTop, gap));
                    continue;
                }
                if (spot != null && spot.post()) for (int y = wallTop + 1; y <= wallTop + gap; y++) grid.put(x, y, z, Cell.log(Role.POST, 1));
            } else if (!out || !high) {
                Ring.Spot front = spot;
                for (int y = wallTop + 1; y <= wallTop + gap; y++) grid.put(x, y, z, wallOrGlass(front, m, y - wallTop, gap));
            }
        }
    }

    /** The corbels under the eaves (after the trim): at the posts of the top storey, under the lowest block of the eaves a block out of the wall. */
    void eaveCorbels(Mass m, Ring ring) {
        if (m.postsOnly || m.dormer) return;
        Footprint top = m.top();
        int wallTop = m.wallTop();
        for (int[] cell : top.cells()) {
            if (top.depth(cell[0], cell[1]) != 1) continue;
            Ring.Spot spot = ring.spot(cell[0], cell[1]);
            if (spot == null || !spot.post() || spot.corner() || (spot.nx() == 0) == (spot.nz() == 0)) continue;
            int ox = cell[0] + spot.nx();
            int oz = cell[1] + spot.nz();
            int under = Integer.MIN_VALUE;
            for (int y = wallTop - 2; y <= wallTop + 3 && under == Integer.MIN_VALUE; y++) if (grid.has(ox, y, oz)) under = y;
            if (under == Integer.MIN_VALUE || under - 1 < wallTop - 3 || grid.has(ox, under - 1, oz)) continue;
            grid.put(ox, under - 1, oz, Walls.corbel(m.corbel, spot.nx(), spot.nz()));
        }
    }

    /** Whether the cell is inside the plan of another mass whose walls are higher than {@code m}'s (its walls and roof are that mass's there). */
    boolean inTaller(Mass m, int x, int z) {
        for (Mass other : masses) {
            if (other == m || other.minor || other.postsOnly || other.dormer || other.wallTop() <= m.wallTop()) continue;
            if (other.top().contains(x, z) || other.plan.contains(x, z)) return true;
        }
        return false;
    }

    /** The wall of a gable at {@code row} over the walls' top: glass in the middle of its bay, two high from the second row, when the gable is 5 or more high there. */
    private static Cell wallOrGlass(Ring.Spot spot, Mass m, int row, int gap) {
        if (spot != null && gap >= 5 && (row == 2 || row == 3) && spot.within(Walls.fit(spot, Math.min(2, m.windowWidth))) && spot.span() >= 2) {
            return Cell.full(Role.WINDOW);
        }
        return Cell.full(Role.INFILL);
    }

    /** The lowest block of a roof over {@code (x, z)} above {@code y}, within 48 blocks; Integer.MIN_VALUE when there is none. */
    int roofOver(int x, int z, int y) {
        for (int at = y + 1; at <= y + 48; at++) {
            Cell cell = grid.get(x, at, z);
            if (cell != null && owners.containsKey(SemanticVoxelGrid.pack(x, at, z))) return at;
        }
        return Integer.MIN_VALUE;
    }

    // ---- the trim -----------------------------------------------------------------------------------------------------------------

    /** The trim of every piece by its edges, then the stripes. */
    void trim() {
        for (Piece piece : pieces) {
            Mass.Roof r = piece.mass.roof;
            Set<Long> ridges = new HashSet<>();
            List<int[]> eaves = new ArrayList<>();
            for (int[] cell : piece.plan.cells()) {
                int x = cell[0];
                int z = cell[1];
                List<Col> cols = colsOf(piece, x, z);
                if (cols.isEmpty()) continue;
                boolean ridge = false;
                boolean hip = false;
                for (Col a : cols) {
                    for (Col b : cols) {
                        if (a.face < 0 || b.face < 0 || a.face == b.face || piece.cone) continue;
                        Side sa = piece.sides.get(a.face);
                        Side sb = piece.sides.get(b.face);
                        double dot = sa.nx() * sb.nx() + sa.nz() * sb.nz();
                        if (dot < -0.9) ridge = true;
                        else hip = true;
                    }
                }
                int top = voxel(piece, x, z, true);
                if (top == Integer.MIN_VALUE) continue;
                if (ridge) ridges.add(SemanticVoxelGrid.pack(x, 0, z));
                else if (hip) recolor(x, top, z, Role.HIP);
                // the eaves: the outer row of a sloping side
                for (Col col : cols) {
                    if (col.face >= 0 && !piece.cone && col.d < 1.0) {
                        eaves.add(new int[]{x, z, col.face});
                        break;
                    }
                    if (piece.cone && col.d < 1.0) {
                        eaves.add(new int[]{x, z, -1});
                        break;
                    }
                }
            }
            for (int[] eave : eaves) eave(piece, eave[0], eave[1], eave[2]);
            if (!piece.cone) verges(piece);
            ridge(piece, ridges, r);
            if (r.finial && (piece.cone || r.type == Mass.RoofType.CONE)) finial(piece);
        }
        bands();
    }

    private void recolor(int x, int y, int z, Role role) {
        Cell cell = grid.get(x, y, z);
        if (cell != null && (cell.role().roof() || cell.role() == Role.HIP)) grid.set(x, y, z, cell.withRole(role));
    }

    /** The eaves' edge at a cell of the outer row: its lowest block an upside-down stair hanging out, a log along the edge under it. */
    private void eave(Piece piece, int x, int z, int face) {
        int low = voxel(piece, x, z, false);
        if (low == Integer.MIN_VALUE) return;
        double nx;
        double nz;
        if (face >= 0) {
            nx = piece.sides.get(face).nx();
            nz = piece.sides.get(face).nz();
        } else {
            nx = x - piece.cx;
            nz = z - piece.cz;
        }
        int way = Math.abs(nx) >= Math.abs(nz) ? (nx > 0 ? 1 : 3) : (nz > 0 ? 2 : 0);
        Cell old = grid.get(x, low, z);
        if (old == null || !old.role().roof()) return;
        grid.set(x, low, z, Cell.stairs(Role.EAVE, way, true));
        if (!grid.has(x, low - 1, z)) grid.put(x, low - 1, z, Cell.log(Role.EAVE, Math.abs(nx) >= Math.abs(nz) ? 2 : 0));
    }

    /**
     * The verges: a block out of each upright side (a gable's end), a band of stairs over upside-down stairs following the roof's top, a block over it. Only where
     * the roof's end is this piece's and nothing roofs the cell out there.
     */
    private void verges(Piece piece) {
        for (Side side : piece.sides) {
            if (side.profile().sloped()) continue;
            int sx = (int) Math.round(side.nx());
            int sz = (int) Math.round(side.nz());
            if ((sx == 0) == (sz == 0)) continue;
            for (int[] cell : piece.plan.cells()) {
                int x = cell[0];
                int z = cell[1];
                if (side.distance(x, z) > 1.0) continue;
                int vx = x + sx;
                int vz = z + sz;
                boolean free = true;
                for (int o = 0; o < 4 && free; o++) free = !covered(vx * 2 + (o & 1), vz * 2 + (o >> 1));
                if (!free) continue;
                double[] tops = new double[4];
                boolean any = false;
                for (int oz = 0; oz < 2; oz++) {
                    for (int ox = 0; ox < 2; ox++) {
                        Col best = null;
                        for (Col col : kept.getOrDefault(half(x * 2 + ox, z * 2 + oz), List.of())) {
                            if (col.piece == piece && (best == null || col.top > best.top)) best = col;
                        }
                        tops[oz * 2 + ox] = best == null ? Double.NaN : best.top;
                        any |= best != null;
                    }
                }
                if (!any) continue;
                double low = Double.MAX_VALUE;
                double high = -Double.MAX_VALUE;
                for (double top : tops) {
                    if (Double.isNaN(top)) continue;
                    low = Math.min(low, top - 0.5);
                    high = Math.max(high, top + 1.0);
                }
                for (int y = (int) Math.floor(low); y < Math.ceil(high); y++) {
                    int mask = 0;
                    for (int oz = 0; oz < 2; oz++) {
                        for (int ox = 0; ox < 2; ox++) {
                            double top = tops[oz * 2 + ox];
                            if (Double.isNaN(top)) continue;
                            for (int oy = 0; oy < 2; oy++) {
                                double bottom = y + 0.5 * oy;
                                if (top - 0.5 <= bottom + 1.0E-9 && top + 1.0 >= bottom + 0.5 - 1.0E-9) mask |= 1 << (ox + 2 * oy + 4 * oz);
                            }
                        }
                    }
                    Cell verge = Cell.of(Role.VERGE, BlockShapes.nearest(mask));
                    if (verge != null) grid.fill(vx, y, vz, verge);
                }
            }
        }
    }

    /** The ridge: slabs (or walls) along it on its top blocks, and a post of walls at each of its ends. */
    private void ridge(Piece piece, Set<Long> ridges, Mass.Roof r) {
        for (long key : ridges) {
            int x = SemanticVoxelGrid.x(key);
            int z = SemanticVoxelGrid.z(key);
            int top = voxel(piece, x, z, true);
            if (top == Integer.MIN_VALUE) continue;
            Cell cell = grid.get(x, top, z);
            int neighbours = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) if ((dx != 0 || dz != 0) && ridges.contains(SemanticVoxelGrid.pack(x + dx, 0, z + dz))) neighbours++;
            }
            boolean end = neighbours <= 1 && ridges.size() > 1 && !piece.mass.dormer;
            if (end) {
                grid.set(x, top, z, Cell.full(Role.RIDGE));
                grid.set(x, top + 1, z, Cell.wall(Role.RIDGE));
                grid.set(x, top + 2, z, Cell.wall(Role.RIDGE));
            } else if (r.ridgeStyle == 1) {
                grid.set(x, top, z, Cell.full(Role.RIDGE));
                grid.fill(x, top + 1, z, Cell.wall(Role.RIDGE));
            } else if (cell.form() == Cell.Form.SLAB && !cell.top()) {
                grid.set(x, top, z, Cell.slab(Role.RIDGE, false));
            } else {
                grid.set(x, top, z, cell.form() == Cell.Form.FULL ? Cell.full(Role.RIDGE) : cell.withRole(Role.RIDGE));
                grid.fill(x, top + 1, z, Cell.slab(Role.RIDGE, false));
            }
        }
    }

    /** The finial on the top of a spire: a wall and a lightning rod on its highest block. */
    private void finial(Piece piece) {
        int bestX = 0;
        int bestZ = 0;
        int bestY = Integer.MIN_VALUE;
        for (int[] cell : piece.plan.cells()) {
            int top = voxel(piece, cell[0], cell[1], true);
            if (top > bestY || (top == bestY && Math.abs(cell[0] - piece.cx) + Math.abs(cell[1] - piece.cz) < Math.abs(bestX - piece.cx) + Math.abs(bestZ - piece.cz))) {
                bestY = top;
                bestX = cell[0];
                bestZ = cell[1];
            }
        }
        if (bestY == Integer.MIN_VALUE) return;
        grid.set(bestX, bestY, bestZ, Cell.full(Role.FINIAL));
        grid.set(bestX, bestY + 1, bestZ, Cell.wall(Role.FINIAL));
        grid.set(bestX, bestY + 2, bestZ, Cell.wall(Role.FINIAL));
        grid.set(bestX, bestY + 3, bestZ, Cell.rod(Role.FINIAL));
    }

    /** The stripes: the roof's blocks take the band of their height (every {@code bandStep} blocks from the eaves) or of their face. */
    private void bands() {
        for (Map.Entry<Long, Col> entry : owners.entrySet()) {
            long key = entry.getKey();
            Col col = entry.getValue();
            Mass.Roof r = col.piece.mass.roof;
            if (r.bands == 0) continue;
            int x = SemanticVoxelGrid.x(key);
            int y = SemanticVoxelGrid.y(key);
            int z = SemanticVoxelGrid.z(key);
            Cell cell = grid.get(x, y, z);
            if (cell == null || !cell.role().roof()) continue;
            int band = r.bands == 1 ? Math.floorDiv(y - col.piece.base, Math.max(1, r.bandStep)) : col.face + col.piece.id * 5;
            grid.set(x, y, z, cell.withRole(Role.band(band)));
        }
    }
}
