package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.HashSet;
import java.util.Set;

/**
 * (t313, spec 1/6 and 5/6) What a lot stands on: a stone plinth (down to the ground, its ledge a block out stepping in at the top), or stilts (posts at the
 * corners and every module down to the ground, a beam round under the floor, braces at the posts if chosen); and the steps from a door on the ground storey down
 * to the ground.
 */
final class Base {

    private Base() {
    }

    /** The foundation of one lot. */
    static void lot(Layout layout, Layout.LotGeo geo, Grid grid, Ground ground, Kit kit) {
        Kit.Part plinth = kit.get("plinth_stone");
        int ledge = plinth == null ? 1 : (int) Math.round(plinth.number("out", 1));
        grid.owner = geo.index;
        switch (geo.lot.foundation) {
            case STILTS -> stilts(layout, geo, grid, ground);
            default -> plinth(layout, geo, grid, ground, geo.lot.foundation == Plan.Foundation.PLINTH ? ledge : 0);
        }
        grid.owner = -1;
    }

    /** The stone under the plan down to the ground, and its ledge: a block out, full down to the ground, its top row stairs stepping in to the wall. */
    private static void plinth(Layout layout, Layout.LotGeo geo, Grid grid, Ground ground, int ledge) {
        Area plan = geo.base;
        int top = geo.y0 - 1;
        Role role = geo.lot.foundation == Plan.Foundation.NONE ? Role.FLOOR : Role.PLINTH;
        for (int[] cell : plan.cells()) {
            int from = ground.at(cell[0], cell[1]) + 1;
            for (int y = from; y <= top; y++) grid.put(cell[0], y, cell[1], Cell.full(role));
        }
        if (ledge <= 0 || geo.lot.foundation != Plan.Foundation.PLINTH || geo.y0 <= 0) return;
        Set<Long> ring = new HashSet<>();
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            for (int way = 0; way < 4; way++) {
                int x = cell[0] + Cell.STEP[way][0];
                int z = cell[1] + Cell.STEP[way][1];
                if (plan.contains(x, z) || inOther(layout, geo, x, z) || !ring.add(Area.key(x, z))) continue;
                int from = ground.at(x, z) + 1;
                for (int y = from; y < top; y++) grid.put(x, y, z, Cell.full(Role.PLINTH));
                if (from <= top) grid.put(x, top, z, Cell.stairs(Role.PLINTH, (way + 2) % 4, false));
            }
        }
        // the outer corners: a stair the game turns into the corner of the two beside it
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    int x = cell[0] + dx;
                    int z = cell[1] + dz;
                    if (plan.contains(x, z) || ring.contains(Area.key(x, z)) || inOther(layout, geo, x, z)) continue;
                    if (!ring.contains(Area.key(cell[0] + dx, cell[1])) || !ring.contains(Area.key(cell[0], cell[1] + dz))) continue;
                    ring.add(Area.key(x, z));
                    int from = ground.at(x, z) + 1;
                    for (int y = from; y < top; y++) grid.put(x, y, z, Cell.full(Role.PLINTH));
                    if (from <= top) grid.put(x, top, z, Cell.stairs(Role.PLINTH, dz > 0 ? 0 : 2, false));
                }
            }
        }
    }

    /** Whether another lot's ground plan has the column. */
    static boolean inOther(Layout layout, Layout.LotGeo geo, int x, int z) {
        for (Layout.LotGeo other : layout.lots) if (other.index != geo.index && other.base.contains(x, z)) return true;
        return false;
    }

    /** Posts down to the ground at the corners and every module, a beam round under the floor, a brace each side of each post. */
    private static void stilts(Layout layout, Layout.LotGeo geo, Grid grid, Ground ground) {
        Area plan = geo.base;
        int beam = geo.y0 - 1;
        int m = layout.plan.module;
        Set<Long> posts = new HashSet<>();
        if (plan.shape == Plan.Shape.RECT) {
            for (int side = 0; side < 4; side++) {
                int length = plan.sideLength(side);
                for (int t = 0; t < length; t++) {
                    int[] at = plan.along(side, t);
                    grid.put(at[0], beam, at[1], Cell.log(Role.BEAM, Area.axisAlong(side)));
                    if (t % m == 0 || t == length - 1) posts.add(Area.key(at[0], at[1]));
                }
            }
            // (and inside, a post every module both ways, so a wide floor is held up)
            for (int x = plan.x0 + m; x < plan.x1; x += m) {
                for (int z = plan.z0 + m; z < plan.z1; z += m) posts.add(Area.key(x, z));
            }
        } else {
            int count = 0;
            for (int[] cell : plan.cells()) {
                if (plan.depth(cell[0], cell[1]) != 1) continue;
                grid.put(cell[0], beam, cell[1], Cell.log(Role.BEAM, Walls.tangentAxis(cell, plan.centerX(), plan.centerZ())));
            }
            for (int[] cell : ringOrder(plan)) {
                if (count++ % m == 0) posts.add(Area.key(cell[0], cell[1]));
            }
            posts.add(Area.key((int) Math.round(plan.centerX()), (int) Math.round(plan.centerZ())));
        }
        for (long key : posts) {
            int x = (int) ((key >> 32) - 100_000);
            int z = (int) ((key & 0xFFFFFFFFL) - 100_000);
            int from = ground.at(x, z) + 1;
            for (int y = from; y < beam; y++) grid.put(x, y, z, Cell.log(Role.FRAME, 1));
            if (!geo.lot.braces || beam - from < 3 || plan.depth(x, z) != 1) continue;
            // (a knee brace each way along the wall, under the beam)
            for (int way = 0; way < 4; way++) {
                int bx = x + Cell.STEP[way][0];
                int bz = z + Cell.STEP[way][1];
                if (plan.depth(bx, bz) != 1) continue;
                grid.put(bx, beam - 1, bz, Cell.stairs(Role.BRACE, (way + 2) % 4, true));
            }
        }
    }

    /** A ring's wall-line blocks in order round it. */
    static java.util.List<int[]> ringOrder(Area plan) {
        java.util.List<int[]> line = new java.util.ArrayList<>();
        for (int[] cell : plan.cells()) if (plan.depth(cell[0], cell[1]) == 1) line.add(cell);
        double cx = plan.centerX();
        double cz = plan.centerZ();
        line.sort((p, q) -> Double.compare(Math.atan2(p[1] - cz, p[0] - cx), Math.atan2(q[1] - cz, q[0] - cx)));
        return line;
    }
}
