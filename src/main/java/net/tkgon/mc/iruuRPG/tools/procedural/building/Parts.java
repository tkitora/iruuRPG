package net.tkgon.mc.iruuRPG.tools.procedural.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

/**
 * (t295) The parts put on a building, each only where its anchor is: a dormer on a sloping side of the roof halfway up, in line with a bay below; a chimney on
 * a roof face near the ridge, two or more over it; a bay window in a bay of the second storey or above, with its own little roof; a balcony on a fascia; a
 * porch at the entrance of the ground storey with a lean-to roof; a windmill's sails on a front gable. (A turret, an arch passage and a finial are made with
 * the masses: see {@link Archetypes} and {@link Roofs}.)
 */
final class Parts {

    private Parts() {
    }

    /** The bays of a storey's side facing {@code way}: their first post's t and their last's, along the longest such side, in order. */
    static List<double[]> bays(Footprint plan, Ring ring, int way) {
        int[] step = Cell.STEP[way];
        TreeMap<Integer, Integer> sides = new TreeMap<>();
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            Ring.Spot spot = ring.spot(cell[0], cell[1]);
            if (spot != null && spot.nx() == step[0] && spot.nz() == step[1]) sides.merge(spot.side(), 1, Integer::sum);
        }
        int side = -1;
        int best = 0;
        for (var entry : sides.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                side = entry.getKey();
            }
        }
        TreeMap<Double, double[]> out = new TreeMap<>();
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            Ring.Spot spot = ring.spot(cell[0], cell[1]);
            if (spot == null || spot.side() != side || spot.post() || spot.nx() != step[0] || spot.nz() != step[1]) continue;
            out.put(spot.from(), new double[]{spot.from(), spot.to(), side});
        }
        return new ArrayList<>(out.values());
    }

    /** The cells of the wall line of a bay from its first post to its last (in order along the side). */
    static List<int[]> line(Footprint plan, Ring ring, double[] bay, int way) {
        int[] step = Cell.STEP[way];
        TreeMap<Double, int[]> out = new TreeMap<>();
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            Ring.Spot spot = ring.spot(cell[0], cell[1]);
            if (spot == null || spot.side() != (int) bay[2] || spot.nx() != step[0] || spot.nz() != step[1]) continue;
            if (spot.t() >= bay[0] - 0.01 && spot.t() <= bay[1] + 0.01) out.put(spot.t(), cell);
        }
        return new ArrayList<>(out.values());
    }

    /** The bay {@code at} of the list (-1 or past the end: the middle one). */
    static double[] pick(List<double[]> bays, int at) {
        if (bays.isEmpty()) return null;
        if (at < 0 || at >= bays.size()) return bays.get(bays.size() / 2);
        return bays.get(at);
    }

    // ---- dormers ---------------------------------------------------------------------------------------------------------------

    /**
     * Dormers on the side of {@code parent}'s roof facing {@code way}: in bay {@code at} (-1: every other bay from the middle, not the end ones). A dormer is a mass
     * of its own from the post to the post of its bay, standing on the roof where it is halfway up, as deep as to the ridge, with a gable roof across the ridge;
     * its walls go down to the roof's underside. It is left out when its ridge would not be 2 or more under the roof's.
     */
    static void dormers(Building building, SemanticVoxelGrid g, Roofs roofs, List<List<Ring>> rings, Mass parent, int way, int at, Ground ground) {
        if (parent.roof.type == Mass.RoofType.SHED || parent.roof.type == Mass.RoofType.CONE || parent.top().circle()) return;
        Footprint top = parent.top();
        Ring ring = rings.get(parent.id).get(parent.floors - 1);
        List<double[]> bays = bays(top, ring, way);
        if (bays.size() < 3 && at < 0) return;
        List<double[]> chosen = new ArrayList<>();
        if (at >= 0) {
            double[] bay = pick(bays, at);
            if (bay != null) chosen.add(bay);
        } else {
            int middle = bays.size() / 2;
            for (int index = 1; index < bays.size() - 1; index++) if ((index - middle) % 2 == 0) chosen.add(bays.get(index));
        }
        int[] step = Cell.STEP[way];
        Roofs.Piece main = null;
        for (Roofs.Piece piece : roofs.pieces) if (piece.mass == parent && (main == null || piece.plan.area() > main.plan.area())) main = piece;
        if (main == null) return;
        double eave = main.eave();
        double ridge = main.base + main.rise;
        for (double[] bay : chosen) {
            List<int[]> line = line(top, ring, bay, way);
            if (line.size() < 3) continue;
            int[] first = line.get(0);
            int[] last = line.get(line.size() - 1);
            int[] middle = line.get(line.size() / 2);
            // in from the wall line until the roof is a good part of the way up (its middle course); lower down when the dormer would be too high there
            int target = 1;
            double half = eave + (ridge - eave) * 0.4;
            while (target < 12) {
                double here = roofs.fieldTop(parent.id, middle[0] - step[0] * target, middle[1] - step[1] * target);
                if (Double.isNaN(here) || here >= half) break;
                target++;
            }
            boolean placed = false;
            for (int in = target; in >= 1 && !placed; in--) {
                placed = dormer(building, g, roofs, rings, parent, main, line, step, in, ground);
            }
        }
    }

    /** One dormer {@code in} blocks in from the wall line (its front there, as deep as to the ridge); false when it would be too high (left out). */
    private static boolean dormer(Building building, SemanticVoxelGrid g, Roofs roofs, List<List<Ring>> rings, Mass parent, Roofs.Piece main, List<int[]> line,
                                  int[] step, int in, Ground ground) {
        int[] first = line.get(0);
        int[] last = line.get(line.size() - 1);
        int[] middle = line.get(line.size() / 2);
        int deep = in;
        double previous = Double.NaN;
        while (deep < 30) {
            double here = roofs.fieldTop(parent.id, middle[0] - step[0] * deep, middle[1] - step[1] * deep);
            if (Double.isNaN(here) || (!Double.isNaN(previous) && here <= previous)) break;
            previous = here;
            deep++;
        }
        double floor = roofs.fieldTop(parent.id, middle[0] - step[0] * in, middle[1] - step[1] * in);
        if (Double.isNaN(floor)) return false;
        int x0 = Math.min(first[0], last[0]) - step[0] * in;
        int x1 = Math.max(first[0], last[0]) - step[0] * in;
        int z0 = Math.min(first[1], last[1]) - step[1] * in;
        int z1 = Math.max(first[1], last[1]) - step[1] * in;
        int length = Math.max(2, deep - in);
        if (step[0] != 0) {
            if (step[0] > 0) x0 -= length - 1;
            else x1 += length - 1;
        } else {
            if (step[1] > 0) z0 -= length - 1;
            else z1 += length - 1;
        }
        for (int height = 4; height >= 3; height--) {
            Mass dormer = new Mass(building.masses.size());
            dormer.plan = Footprint.rect(x0, z0, x1 - x0 + 1, z1 - z0 + 1);
            dormer.y0 = (int) Math.floor(floor);
            dormer.floors = 1;
            dormer.storey = height;
            dormer.sill = 0;
            dormer.windowHeight = 2;
            dormer.windowWidth = parent.windowWidth;
            dormer.module = Math.max(x1 - x0, z1 - z0) + 1;
            dormer.corbel = parent.corbel;
            dormer.dormer = true;
            dormer.parent = parent.id;
            dormer.minor = true;
            dormer.roof = parent.roof.copy();
            dormer.roof.type = Mass.RoofType.GABLE;
            dormer.roof.ridge = step[0] != 0 ? 0 : 1;
            // (a dormer on a steep roof is roofed at 1:1, so its ridge stays under the roof's)
            dormer.roof.pitch = parent.roof.pitch >= 2.0 ? 1.0 : parent.roof.pitch;
            dormer.roof.overhang = 1;
            dormer.roof.bands = 0;
            dormer.roof.finial = false;
            dormer.roof.gableOut = false;
            int before = roofs.pieces.size();
            roofs.add(dormer);
            Roofs.Piece piece = roofs.pieces.get(roofs.pieces.size() - 1);
            if (piece.ridge() > main.ridge() - 2) {
                roofs.pieces.subList(before, roofs.pieces.size()).clear();
                continue;
            }
            building.masses.add(dormer);
            rings.add(Walls.mass(g, dormer, ground));
            // the walls down to the roof's underside under them
            for (int[] cell : dormer.plan.cells()) {
                if (dormer.plan.depth(cell[0], cell[1]) > 2) continue;
                double low = roofs.fieldLow(parent.id, cell[0], cell[1]);
                if (Double.isNaN(low)) continue;
                boolean frame = dormer.plan.depth(cell[0], cell[1]) == 1;
                for (int y = (int) Math.floor(low); y < dormer.y0; y++) g.put(cell[0], y, cell[1], frame ? Cell.log(Role.POST, 1) : Cell.full(Role.INFILL));
            }
            return true;
        }
        return false;
    }

    // ---- chimneys ---------------------------------------------------------------------------------------------------------------

    /** A chimney on {@code mass}'s roof: on a face, 1 to 3 under the ridge, away from the gables and the hips; 2 by 2 when it fits; 2 or more over the ridge. */
    static void chimney(SemanticVoxelGrid g, Roofs roofs, Mass mass, Random random) {
        Roofs.Piece main = null;
        for (Roofs.Piece piece : roofs.pieces) if (piece.mass == mass && !piece.cone && (main == null || piece.plan.area() > main.plan.area())) main = piece;
        if (main == null) return;
        Footprint top = mass.top();
        int ridge = main.ridge();
        List<int[]> spots = new ArrayList<>();
        for (int[] cell : main.plan.cells()) {
            if (top.depth(cell[0], cell[1]) < 3) continue;
            if (!clean(roofs, main, cell[0], cell[1])) continue;
            int y = roofs.voxel(main, cell[0], cell[1], true);
            if (y == Integer.MIN_VALUE || y < ridge - 4 || y > ridge - 2) continue;
            boolean nearEnd = false;
            for (Roofs.Side side : main.sides) if (!side.profile().sloped() && side.distance(cell[0], cell[1]) < 3.0 + main.mass.roof.overhang) nearEnd = true;
            if (nearEnd) continue;
            spots.add(cell);
        }
        if (spots.isEmpty()) return;
        int[] at = spots.get(random.nextInt(spots.size()));
        int size = 1;
        if (top.depth(at[0] + 1, at[1]) >= 3 && top.depth(at[0], at[1] + 1) >= 3 && top.depth(at[0] + 1, at[1] + 1) >= 3) size = 2;
        int from = mass.wallTop() - 1;
        int to = Math.max(ridge + 2, roofs.voxel(main, at[0], at[1], true) + 3);
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                for (int y = from; y <= to; y++) g.set(at[0] + dx, y, at[1] + dz, Cell.full(Role.CHIMNEY));
            }
        }
    }

    /** Whether the cell's roof is one face of the piece (no ridge, no hip, no valley). */
    private static boolean clean(Roofs roofs, Roofs.Piece piece, int x, int z) {
        List<Roofs.Col> cols = roofs.colsOf(piece, x, z);
        if (cols.size() < 4) return false;
        for (Roofs.Col col : cols) if (col.face != cols.get(0).face) return false;
        return true;
    }

    // ---- bay windows and balconies --------------------------------------------------------------------------------------------

    /**
     * A bay window (出窓) in bay {@code at} of the side facing {@code way} of storey {@code storey} (1 or above): a box a block out from post to post, posts at
     * its corners, glass across its front, a floor under it on corbels, a little roof of stairs over it; the wall behind it opened into the room.
     */
    static void bayWindow(SemanticVoxelGrid g, Mass m, List<Ring> rings, int way, int at, int storey) {
        int s = Math.max(1, Math.min(m.floors - 1, storey));
        if (m.floors < 2) return;
        Footprint plan = m.plan(s);
        Ring ring = rings.get(s);
        double[] bay = pick(bays(plan, ring, way), at);
        if (bay == null) return;
        List<int[]> line = line(plan, ring, bay, way);
        if (line.size() < 4) return;
        int[] step = Cell.STEP[way];
        int yb = m.y0 + s * m.storey;
        int h = m.storey;
        int sill = Math.max(1, m.sill);
        int window = Math.min(m.windowHeight + 1, h - 2 - sill);
        int inward = (way + 2) % 4;
        for (int index = 0; index < line.size(); index++) {
            int[] wall = line.get(index);
            boolean end = index == 0 || index == line.size() - 1;
            int x = wall[0] + step[0];
            int z = wall[1] + step[1];
            for (int r = 0; r < h - 1; r++) {
                int y = yb + r;
                Cell cell;
                if (end) cell = Cell.log(Role.POST, 1);
                else if (r >= sill && r < sill + window) cell = Cell.full(Role.WINDOW);
                else if (r == sill + window) cell = Cell.log(Role.BEAM, -1);
                else cell = Cell.full(Role.INFILL);
                g.set(x, y, z, cell);
                if (!end) {
                    // (the wall behind opened into the room)
                    for (int k = 0; k < 3; k++) {
                        Cell behind = g.get(wall[0] - step[0] * k, y, wall[1] - step[1] * k);
                        if (behind != null && behind.role() != Role.POST) g.clear(wall[0] - step[0] * k, y, wall[1] - step[1] * k);
                    }
                }
            }
            g.set(x, yb - 1, z, Cell.full(Role.FLOOR));
            if (end) g.put(x, yb - 2, z, Walls.corbel(m.corbel, step[0], step[1]));
            g.set(x, yb + h - 1, z, Cell.stairs(Role.ROOF, inward, false));
        }
    }

    /**
     * A balcony on the fascia under storey {@code storey} (1 or above): a floor two blocks out from post to post of bay {@code at}, a rail of fences round it
     * (trapdoors when {@code trapdoors}), corbels under its posts, a door to it in the middle of the bay.
     */
    static void balcony(SemanticVoxelGrid g, Mass m, List<Ring> rings, int way, int at, int storey, boolean trapdoors) {
        int s = Math.max(1, Math.min(m.floors - 1, storey));
        if (m.floors < 2) return;
        Footprint plan = m.plan(s);
        Ring ring = rings.get(s);
        double[] bay = pick(bays(plan, ring, way), at);
        if (bay == null) return;
        List<int[]> line = line(plan, ring, bay, way);
        if (line.size() < 3) return;
        int[] step = Cell.STEP[way];
        int yb = m.y0 + s * m.storey;
        int depth = 2;
        for (int index = 0; index < line.size(); index++) {
            int[] wall = line.get(index);
            boolean end = index == 0 || index == line.size() - 1;
            for (int k = 1; k <= depth; k++) {
                int x = wall[0] + step[0] * k;
                int z = wall[1] + step[1] * k;
                g.set(x, yb - 1, z, Cell.full(Role.FLOOR));
                boolean edge = k == depth || end;
                if (edge && !g.has(x, yb, z)) {
                    if (trapdoors) {
                        int against = k == depth ? way : (index == 0 ? sideWay(line, way, false) : sideWay(line, way, true));
                        g.set(x, yb, z, Cell.trapdoor(Role.RAIL, (against + 2) % 4, false, true));
                    } else {
                        g.set(x, yb, z, Cell.fence(Role.RAIL));
                    }
                }
            }
            if (end) g.put(wall[0] + step[0], yb - 2, wall[1] + step[1], Walls.corbel(m.corbel, step[0], step[1]));
        }
        // the door: the middle cells of the bay, through the three layers, three high
        Ring.Spot middle = ring.spot(line.get(line.size() / 2)[0], line.get(line.size() / 2)[1]);
        int width = middle == null ? 1 : Math.max(1, Math.min(2, Walls.fit(middle, 1)));
        for (int[] wall : line) {
            Ring.Spot spot = ring.spot(wall[0], wall[1]);
            if (spot == null || spot.post() || Math.abs(spot.t() - spot.middle()) > (width - 1) / 2.0 + 0.01) continue;
            for (int k = 0; k < 3; k++) {
                for (int y = yb; y < yb + Math.min(3, m.storey - 2); y++) g.clear(wall[0] - step[0] * k, y, wall[1] - step[1] * k);
            }
        }
    }

    /** The way out of a balcony's side end (the first or the last of its line). */
    private static int sideWay(List<int[]> line, int way, boolean last) {
        int[] a = line.get(0);
        int[] b = line.get(line.size() - 1);
        int dx = Integer.signum(b[0] - a[0]);
        int dz = Integer.signum(b[1] - a[1]);
        if (!last) {
            dx = -dx;
            dz = -dz;
        }
        return Walls.way(dx, dz);
    }

    // ---- porches --------------------------------------------------------------------------------------------------------------------

    /**
     * A porch at the entrance ({@code door}: the cells just outside it): a deck at the floor's height {@code depth} blocks out, from post to post of the door's bay
     * (or the whole side when {@code wide}), stairs down from its front; posts at its outer corners and a lean-to roof whose top meets the ground storey's fascia.
     * The porch is a mass of its own (its roof is made with the others); its posts are put after the roofs ({@link #porchPosts}).
     */
    static Mass porch(Building building, SemanticVoxelGrid g, Mass m, Ring ring, List<int[]> door, boolean wide, Ground ground) {
        if (door == null || door.size() < 3) return null;
        int way = m.doorWay;
        int[] step = Cell.STEP[way];
        int[] cell = door.get(door.size() / 2);
        int[] wall = {cell[0] - step[0], cell[1] - step[1]};
        Footprint plan = m.plan;
        double[] bay = null;
        List<int[]> line;
        if (wide) {
            List<double[]> bays = bays(plan, ring, way);
            if (bays.isEmpty()) return null;
            line = new ArrayList<>();
            for (double[] each : bays) for (int[] c : line(plan, ring, each, way)) if (line.stream().noneMatch(o -> o[0] == c[0] && o[1] == c[1])) line.add(c);
        } else {
            Ring.Spot spot = ring.spot(wall[0], wall[1]);
            if (spot == null) return null;
            bay = new double[]{spot.from(), spot.to(), spot.side()};
            line = line(plan, ring, bay, way);
        }
        if (line.size() < 3) return null;
        int depth = 3;
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (int[] c : line) {
            for (int k = 1; k <= depth; k++) {
                int x = c[0] + step[0] * k;
                int z = c[1] + step[1] * k;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                z0 = Math.min(z0, z);
                z1 = Math.max(z1, z);
            }
        }
        // the deck
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 0; y < m.y0; y++) g.set(x, y, z, Cell.full(Role.PLINTH));
                Walls.down(g, x, 0, z, ground, Role.PLINTH);
                for (int y = m.y0; y < m.y0 + m.storey; y++) {
                    Cell over = g.get(x, y, z);
                    if (over != null && over.role() == Role.PLINTH) g.clear(x, y, z);
                }
            }
        }
        // the stairs down from the middle of its front
        List<int[]> front = new ArrayList<>();
        for (int[] c : door) front.add(new int[]{c[0] + step[0] * depth, c[1] + step[1] * depth});
        Walls.stairsDown(g, front, way, m.y0, ground);
        Mass porch = building.add();
        porch.plan = Footprint.rect(x0, z0, x1 - x0 + 1, z1 - z0 + 1);
        porch.y0 = m.y0;
        porch.floors = 1;
        porch.storey = m.storey;
        porch.postsOnly = true;
        porch.minor = true;
        porch.roof = m.roof.copy();
        porch.roof.type = Mass.RoofType.SHED;
        porch.roof.way = way;
        porch.roof.pitch = 0.5;
        porch.roof.overhang = 1;
        porch.roof.ridge = -1;
        porch.roof.topAt = m.y0 + m.storey;
        porch.roof.open = true;
        porch.roof.finial = false;
        porch.roof.gableOut = false;
        porch.parent = m.id;
        return porch;
    }

    /** The posts of a porch: at the outer corners of its deck, from the deck up to under its roof. */
    static void porchPosts(SemanticVoxelGrid g, Roofs roofs, Mass porch, int way) {
        int[] box = porch.plan.box();
        int[] step = Cell.STEP[way];
        List<int[]> corners = new ArrayList<>();
        int[][] all = {{box[0], box[1]}, {box[2], box[1]}, {box[0], box[3]}, {box[2], box[3]}};
        for (int[] corner : all) {
            // (the outer corners: those far from the wall)
            boolean outer = step[0] > 0 ? corner[0] == box[2] : step[0] < 0 ? corner[0] == box[0] : step[1] > 0 ? corner[1] == box[3] : corner[1] == box[1];
            if (outer) corners.add(corner);
        }
        for (int[] corner : corners) {
            int roof = roofs.roofOver(corner[0], corner[1], porch.y0 - 1);
            if (roof == Integer.MIN_VALUE) continue;
            for (int y = porch.y0; y < roof; y++) g.put(corner[0], y, corner[1], Cell.log(Role.POST, 1));
        }
    }

    // ---- sails -------------------------------------------------------------------------------------------------------------------------

    /**
     * A windmill's sails on the gable facing {@code way}: a hub of logs out of the gable's middle halfway up it, four arms of logs (up, down, left, right) and a
     * lattice of fences beside each arm (all on the same side going round), as long as the hub is high.
     */
    static void sails(SemanticVoxelGrid g, Roofs roofs, Mass m, int way) {
        Footprint top = m.top();
        int[] box = top.box();
        int[] step = Cell.STEP[way];
        int cx = step[0] == 0 ? (box[0] + box[2]) / 2 : step[0] > 0 ? box[2] : box[0];
        int cz = step[1] == 0 ? (box[1] + box[3]) / 2 : step[1] > 0 ? box[3] : box[1];
        int wallTop = m.wallTop();
        int roof = roofs.roofOver(cx, cz, wallTop);
        if (roof == Integer.MIN_VALUE) roof = wallTop + 4;
        int hub = wallTop + Math.max(2, (roof - wallTop) / 2);
        int arm = Math.min(10, hub - 3);
        if (arm < 4) return;
        int px = cx + step[0] * 2;
        int pz = cz + step[1] * 2;
        g.set(cx + step[0], hub, cz + step[1], Cell.log(Role.BEAM, step[0] != 0 ? 0 : 2));
        g.set(px, hub, pz, Cell.log(Role.BEAM, step[0] != 0 ? 0 : 2));
        // the arms in the gable's plane (dx, dz, dy): up, across, down, back across; the lattice beside each on the side of the next one round
        int hx = Math.abs(step[1]);
        int hz = Math.abs(step[0]);
        int[][] arms = {{0, 0, 1}, {hx, hz, 0}, {0, 0, -1}, {-hx, -hz, 0}};
        for (int index = 0; index < 4; index++) {
            int[] d = arms[index];
            int[] beside = arms[(index + 1) % 4];
            for (int k = 1; k <= arm; k++) {
                int x = px + d[0] * k;
                int z = pz + d[1] * k;
                int y = hub + d[2] * k;
                g.set(x, y, z, Cell.log(Role.BEAM, d[2] != 0 ? 1 : d[0] != 0 ? 0 : 2));
                if (k < 3) continue;
                for (int w = 1; w <= 2; w++) {
                    int sy = y + beside[2] * w;
                    if (sy < 1) continue;
                    g.fill(x + beside[0] * w, sy, z + beside[1] * w, Cell.fence(Role.RAIL));
                }
            }
        }
    }
}
