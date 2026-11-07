package net.tkgon.mc.iruuRPG.tools.procedural.building;

import java.util.ArrayList;
import java.util.List;

/**
 * (t295) The podiums and the walls of the masses. A storey's walls are three layers deep: the frame on the wall line (depth 1: the posts, the lintels over the
 * windows, the fascia on top), the wall a block behind it (depth 2) and the glass of the windows a block further in (depth 3). A bay is, from the bottom, the
 * sill wall, the window, the lintel, and wall up to the fascia. The corners' posts, the posts and the fascias are always there.
 */
final class Walls {

    private Walls() {
    }

    /** The way (0 north, 1 east, 2 south, 3 west) of a step; a slanting step counts its x. */
    static int way(int dx, int dz) {
        if (dx > 0) return 1;
        if (dx < 0) return 3;
        return dz < 0 ? 0 : 2;
    }

    /**
     * A podium: its ledge (depth 1) of stone topped with stairs facing out (their high side to the walls), the stone under the walls (depth 2, 3), a floor on
     * top inside; the outer layers go down to the ground.
     */
    static void podium(SemanticVoxelGrid g, Building.Podium podium, Ground ground) {
        Footprint plan = podium.plan();
        int h = podium.height();
        for (int[] cell : plan.cells()) {
            int x = cell[0];
            int z = cell[1];
            int d = plan.depth(x, z);
            if (d == 1 && podium.terrace()) {
                // (t302: a terrace: flat to its edge, a low rail round it when it is high)
                for (int y = 0; y < h; y++) g.put(x, y, z, Cell.full(Role.PLINTH));
                if (h >= 2) g.put(x, h, z, Cell.fence(Role.RAIL));
            } else if (d == 1) {
                for (int y = 0; y < h - 1; y++) g.put(x, y, z, Cell.full(Role.PLINTH));
                g.put(x, h - 1, z, Cell.stairs(Role.PLINTH, inward(plan, x, z), false));
            } else if (d <= 3) {
                for (int y = 0; y < h; y++) g.put(x, y, z, Cell.full(Role.PLINTH));
            } else {
                g.fill(x, h - 1, z, Cell.full(Role.FLOOR));
            }
            if (d <= 3) down(g, x, 0, z, ground, Role.PLINTH);
        }
    }

    /** Fills a column with {@code role} from under {@code y} down to the ground (where it is known; 24 blocks at most). */
    static void down(SemanticVoxelGrid g, int x, int y, int z, Ground ground, Role role) {
        int top = ground == null ? -1 : ground.top(x, z);
        if (top == Ground.NONE) top = -1;
        for (int at = y - 1; at > top && at >= y - 24; at--) g.fill(x, at, z, Cell.full(role));
    }

    /** The way into the plan from a cell of its edge: toward a deeper cell beside it, else the x of a deeper one at a corner. */
    static int inward(Footprint plan, int x, int z) {
        int d = plan.depth(x, z);
        for (int way = 0; way < 4; way++) {
            if (plan.depth(x + Cell.STEP[way][0], z + Cell.STEP[way][1]) > d) return way;
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) if (plan.depth(x + dx, z + dz) > d) return way(dx, 0);
        }
        for (int way = 0; way < 4; way++) {
            if (plan.depth(x + Cell.STEP[way][0], z + Cell.STEP[way][1]) >= d) return way;
        }
        return 0;
    }

    /** The cells of a bay of {@code width} middle cells, fitted to the bay (a bay of an odd number of cells has an odd window). */
    static int fit(Ring.Spot spot, int width) {
        int span = (int) Math.round(spot.span());
        if (span < 1 || width <= 0) return 0;
        int w = Math.min(width, span);
        if ((span - w) % 2 != 0) w = w + 1 <= span ? w + 1 : w - 1;
        return w;
    }

    /** The walls of a mass, storey by storey; the rings of its storeys (for the parts) are returned. */
    static List<Ring> mass(SemanticVoxelGrid g, Mass m, Ground ground) {
        g.owner = m.owner;
        List<Ring> rings = new ArrayList<>();
        Ring below = null;
        for (int s = 0; s < m.floors; s++) {
            Footprint plan = m.plan(s);
            Ring ring = below == null ? Ring.ground(plan, m.posts(), m.mirror, m.circlePosts) : Ring.above(below, plan, m.circlePosts);
            if (!m.postsOnly) storey(g, m, plan, ring, s);
            rings.add(ring);
            below = ring;
        }
        if (m.postsOnly) return rings;
        // the corbels under every jetty: under its posts (not its corners), where nothing holds the storey up
        for (int s = 1; s < m.floors && m.brackets; s++) {
            if (s >= m.jetty.length || m.jetty[s] <= 0) continue;
            corbels(g, m, m.plan(s), rings.get(s), m.y0 + s * m.storey);
        }
        Footprint plan = m.plan;
        if (m.foundation) {
            // (the wall line stands on what is under it, down to the ground where it is lower)
            for (int[] cell : plan.cells()) if (plan.depth(cell[0], cell[1]) == 1) down(g, cell[0], m.y0, cell[1], ground, Role.PLINTH);
        } else if (m.brackets && m.y0 > 0 && !m.corbelBase) {
            // (a mass out over the air: a corbel under each post of its walls)
            corbels(g, m, plan, rings.get(0), m.y0);
        }
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) >= 2) g.fill(cell[0], m.y0 - 1, cell[1], Cell.full(Role.FLOOR));
        }
        plinthBand(g, m, ground);
        skirts(g, m, rings);
        if (m.corbelBase) {
            // (a turret: rings of upside-down stairs, each a block in, under it)
            for (int j = 1; j <= 8; j++) {
                boolean any = false;
                for (int[] cell : plan.cells()) {
                    if (plan.depth(cell[0], cell[1]) != j) continue;
                    any = true;
                    g.put(cell[0], m.y0 - j, cell[1], Cell.stairs(Role.CORBEL, inward(plan, cell[0], cell[1]), true));
                }
                if (!any) break;
            }
        }
        return rings;
    }

    /** A corbel under each post (not a corner) of a storey's wall line at {@code yb}, where nothing holds it up (a block lower when its fascia is there). */
    private static void corbels(SemanticVoxelGrid g, Mass m, Footprint plan, Ring ring, int yb) {
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            Ring.Spot spot = ring.spot(cell[0], cell[1]);
            if (spot == null || !spot.post() || spot.corner()) continue;
            int y = !g.has(cell[0], yb - 1, cell[1]) ? yb - 1 : !g.has(cell[0], yb - 2, cell[1]) ? yb - 2 : Integer.MIN_VALUE;
            if (y != Integer.MIN_VALUE) g.put(cell[0], y, cell[1], corbel(m.corbel, spot.nx(), spot.nz()));
        }
    }

    /** (t302) The stone band a block out round the ground storey, {@link Mass#plinthBand} high, its top stairs facing out; down to the ground under it. */
    private static void plinthBand(SemanticVoxelGrid g, Mass m, Ground ground) {
        if (m.plinthBand <= 0) return;
        Footprint out = m.plan.expand(1);
        for (int[] cell : out.cells()) {
            if (out.depth(cell[0], cell[1]) != 1) continue;
            int top = m.y0 + m.plinthBand - 1;
            for (int y = m.y0; y < top; y++) g.put(cell[0], y, cell[1], Cell.full(Role.PLINTH));
            g.put(cell[0], top, cell[1], Cell.stairs(Role.PLINTH, inward(out, cell[0], cell[1]), false));
            if (m.foundation) down(g, cell[0], m.y0, cell[1], ground, Role.PLINTH);
        }
    }

    /**
     * (t302) The skirts: a little roof of stairs out of the wall between the storeys, {@link Mass#skirt} blocks out, sloping down from the fascia; not on a face
     * the storey over it is jettied out on.
     */
    private static void skirts(SemanticVoxelGrid g, Mass m, List<Ring> rings) {
        if (m.skirt <= 0) return;
        for (int s = 0; s < m.floors - 1; s++) {
            Footprint plan = m.plan(s);
            Ring ring = rings.get(s);
            boolean frontOut = m.out(s + 1) > m.out(s);
            boolean sideOut = m.outSides(s + 1) > m.outSides(s);
            int top = m.y0 + (s + 1) * m.storey - 1;
            for (int[] cell : plan.cells()) {
                if (plan.depth(cell[0], cell[1]) != 1) continue;
                Ring.Spot spot = ring.spot(cell[0], cell[1]);
                if (spot == null) continue;
                int nx = spot.nx();
                int nz = spot.nz();
                if (nz != 0 && frontOut || nx != 0 && sideOut) continue;
                for (int k = 1; k <= m.skirt; k++) {
                    int x = cell[0] + nx * k;
                    int z = cell[1] + nz * k;
                    if (plan.depth(x, z) > 0) continue;
                    g.fill(x, top - (k - 1), z, Cell.stairs(Role.ROOF, way(-nx, -nz), false));
                }
            }
        }
    }

    /** A corbel under a post facing out ({@code nx}, {@code nz}): an upside-down stair whose high side is the wall's, or a log lying out of the wall. */
    static Cell corbel(int style, int nx, int nz) {
        if (style == 1) return Cell.log(Role.CORBEL, nx != 0 ? 0 : 2);
        return Cell.stairs(Role.CORBEL, way(-nx, -nz), true);
    }

    /**
     * A storey's walls. With the walls set back (the default) the frame is on the wall line, the wall a block behind it and the glass a block behind that;
     * otherwise the wall is on the wall line between the posts and the glass a block behind it. A stone ground storey is stone through, its windows deep in it.
     * The timber frame's braces rise from the foot of the posts toward the middle of each bay, in front of the wall (and of the glass behind it).
     */
    private static void storey(SemanticVoxelGrid g, Mass m, Footprint plan, Ring ring, int s) {
        int h = m.storey;
        int yb = m.y0 + s * h;
        int top = yb + h - 1;
        int sill = Math.max(0, m.sill);
        int window = Math.max(1, m.windowHeight);
        if (sill + window > h - 1) window = Math.max(1, h - 1 - sill);
        if (sill + window > h - 1) sill = Math.max(0, h - 1 - window);
        // (the lintel over the windows; when the storey has no room for it, the fascia is the lintel)
        int lintel = sill + window <= h - 2 ? sill + window : -1;
        boolean stone = s == 0 && m.stoneGround;
        // the depth of the wall and of the glass
        int wall = stone || !m.recess ? 1 : 2;
        int pane = wall + 1;
        for (int[] cell : plan.cells()) {
            int x = cell[0];
            int z = cell[1];
            int d = plan.depth(x, z);
            if (d <= pane) {
                Ring.Spot spot = ring.spot(x, z);
                int width = spot == null || !m.windows ? 0 : fit(spot, m.windowWidth);
                for (int r = 0; r < h - 1; r++) {
                    int y = yb + r;
                    boolean opening = spot != null && width > 0 && r >= sill && r < sill + window && spot.within(width) && spot.span() >= 2.0;
                    boolean reveal = spot != null && width > 0 && spot.span() >= 2.0 && spot.within(width + 2) && r >= sill - 1 && r <= sill + window;
                    if (stone) {
                        if (d < pane && !opening) g.put(x, y, z, Cell.full(Role.PLINTH));
                        else if (d == pane && opening && m.glass) g.put(x, y, z, Cell.full(Role.WINDOW));
                        else if (d == pane && reveal) g.put(x, y, z, Cell.full(Role.PLINTH));
                        continue;
                    }
                    if (d == 1 && spot != null && spot.post()) {
                        g.put(x, y, z, Cell.log(Role.POST, 1));
                    } else if (d == 1 && r == lintel) {
                        g.put(x, y, z, Cell.log(Role.BEAM, -1));
                    } else if (d == 1 && m.timber && spot != null && brace(spot, r)) {
                        g.put(x, y, z, Cell.log(Role.BEAM, -1));
                    } else if (d == wall) {
                        if (!opening) g.put(x, y, z, Cell.full(Role.INFILL));
                    } else if (d == pane) {
                        if (opening && m.glass) g.put(x, y, z, Cell.full(Role.WINDOW));
                        // (the reveal round the glass, a block behind the wall: the glass is held in it)
                        else if (!opening && reveal) g.put(x, y, z, Cell.full(Role.INFILL));
                    }
                }
            }
            // the fascia on the wall line; the floor of the storey above (the ceiling of the top one) inside
            if (d == 1) g.put(x, top, z, Cell.log(Role.BEAM, -1));
            else g.put(x, top, z, Cell.full(Role.FLOOR));
        }
        if (m.fasciaOut) {
            Footprint out = plan.expand(1);
            for (int[] cell : out.cells()) if (out.depth(cell[0], cell[1]) == 1) g.put(cell[0], top, cell[1], Cell.log(Role.BEAM, -1));
        }
    }

    /** (t302) Whether the wall-line cell of a bay at row {@code r} is on a brace: two diagonals rising from the foot of the posts either side toward the middle. */
    static boolean brace(Ring.Spot spot, int r) {
        if (spot.post()) return false;
        int inside = (int) Math.round(spot.to() - spot.from()) - 1;
        if (inside < 2) return false;
        int place = (int) Math.round(spot.t() - spot.from());
        return place <= (inside + 1) / 2 ? place == r + 1 : inside + 1 - place == r + 1;
    }

    /**
     * The entrance of a mass: the middle bay of its longest side facing {@link Mass#doorWay} on the ground storey, opened three high through its three layers
     * (one cell wide in a bay of 3, two in a bay of 4, three in a bay of 5 or more odd), a lintel over it. The cells just outside it (and one more each side) are
     * returned, for the stairs down from a podium; null when there is no such side.
     */
    static List<int[]> door(SemanticVoxelGrid g, Mass m, Ring ring) {
        Footprint plan = m.plan;
        int[] step = Cell.STEP[m.doorWay];
        int bestSide = -1;
        int bestCount = 0;
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            Ring.Spot spot = ring.spot(cell[0], cell[1]);
            if (spot == null || spot.nx() != step[0] || spot.nz() != step[1]) continue;
            int count = counts.merge(spot.side(), 1, Integer::sum);
            if (count > bestCount) {
                bestCount = count;
                bestSide = spot.side();
            }
        }
        if (bestSide < 0) return null;
        // the side's cells in order along it, and its middle
        List<int[]> line = new ArrayList<>();
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            Ring.Spot spot = ring.spot(cell[0], cell[1]);
            if (spot == null || spot.side() != bestSide || spot.nx() != step[0] || spot.nz() != step[1]) continue;
            line.add(cell);
            low = Math.min(low, spot.t());
            high = Math.max(high, spot.t());
        }
        double middle = (low + high) / 2.0;
        Ring.Spot bay = null;
        for (int[] cell : line) {
            Ring.Spot spot = ring.spot(cell[0], cell[1]);
            if (!spot.post() && spot.from() <= middle && spot.to() >= middle) bay = spot;
        }
        if (bay == null) return null;
        int span = (int) Math.round(bay.span());
        int width = span % 2 == 0 ? 2 : span >= 5 ? 3 : 1;
        List<int[]> out = new ArrayList<>();
        int height = Math.min(3, m.storey - 2);
        for (int[] cell : line) {
            Ring.Spot spot = ring.spot(cell[0], cell[1]);
            if (spot.post() || spot.from() != bay.from() || Math.abs(spot.t() - bay.middle()) > (width - 1) / 2.0 + 0.01) continue;
            for (int k = 0; k < 3; k++) {
                for (int y = m.y0; y < m.y0 + height; y++) g.clear(cell[0] - step[0] * k, y, cell[1] - step[1] * k);
            }
            g.put(cell[0], m.y0 + height, cell[1], Cell.log(Role.BEAM, -1));
            out.add(new int[]{cell[0] + step[0], cell[1] + step[1]});
        }
        // (one more cell each side of the stairs)
        if (!out.isEmpty()) {
            int[] first = out.get(0);
            int[] last = out.get(out.size() - 1);
            int ax = Math.abs(step[1]);
            int az = Math.abs(step[0]);
            int sign = (last[0] - first[0]) * ax + (last[1] - first[1]) * az >= 0 ? 1 : -1;
            out.add(0, new int[]{first[0] - ax * sign, first[1] - az * sign});
            out.add(new int[]{last[0] + ax * sign, last[1] + az * sign});
        }
        return out;
    }

    /** Stairs down from {@code height} to the ground, from each of {@code cells} outward along the way {@code way}; stone under them down to the ground. */
    static void stairsDown(SemanticVoxelGrid g, List<int[]> cells, int way, int height, Ground ground) {
        if (cells == null || height <= 0) return;
        int[] step = Cell.STEP[way];
        for (int[] cell : cells) {
            for (int k = 1; k <= height; k++) {
                int x = cell[0] + step[0] * (k - 1);
                int z = cell[1] + step[1] * (k - 1);
                int y = height - k;
                g.set(x, y, z, Cell.stairs(Role.PLINTH, (way + 2) % 4, false));
                for (int under = y - 1; under >= 0; under--) g.set(x, under, z, Cell.full(Role.PLINTH));
                down(g, x, 0, z, ground, Role.PLINTH);
                // (nothing over the stairs: the podium's stone, a terrace's rail)
                for (int over = y + 1; over < height + 3; over++) {
                    Cell above = g.get(x, over, z);
                    if (above != null && (above.role() == Role.PLINTH || above.role() == Role.RAIL)) g.clear(x, over, z);
                }
            }
        }
    }

    /**
     * An arch passage through the ground storey (and the podium) from front to back: {@code width} cells (odd) in the middle of the plan, open from the ground to
     * under the fascia, its top corners upside-down stairs; walls along its sides inside.
     */
    static void arch(SemanticVoxelGrid g, Mass m) {
        if (m.arch <= 0) return;
        int[] box = m.plan.box();
        int cx = (box[0] + box[2]) / 2;
        int half = m.arch / 2;
        int top = m.y0 + m.storey - 2;
        for (int z = box[1] - 2; z <= box[3] + 2; z++) {
            for (int x = cx - half; x <= cx + half; x++) {
                for (int y = 0; y <= top; y++) g.clear(x, y, z);
            }
            if (z < box[1] || z > box[3]) continue;
            g.set(cx - half, top, z, Cell.stairs(Role.PLINTH, 3, true));
            g.set(cx + half, top, z, Cell.stairs(Role.PLINTH, 1, true));
            if (m.plan.depth(cx, z) >= 2) {
                for (int y = 0; y <= top; y++) {
                    Role role = y < m.y0 ? Role.PLINTH : Role.INFILL;
                    g.fill(cx - half - 1, y, z, Cell.full(role));
                    g.fill(cx + half + 1, y, z, Cell.full(role));
                }
            }
        }
    }
}
