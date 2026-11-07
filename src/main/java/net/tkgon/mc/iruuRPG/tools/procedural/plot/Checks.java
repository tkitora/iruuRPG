package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * (t313, spec 4/6 and 5/6) What is checked of a building made: no two lots next to each other with eaves or ridges one block apart (0, or 2 and more), no
 * hollow in a roof where water would stand, no gap between the top of a wall and the roof over it, no door blocked in front, room over the steps of outside
 * stairs, nothing floating. Each problem found is a line of the report.
 */
final class Checks {

    private Checks() {
    }

    static List<String> run(Layout layout, Roofs.Result roofs, Marks marks, Grid grid, Ground ground) {
        List<String> problems = new ArrayList<>();
        int steps = steps(layout, roofs);
        if (steps > 0) problems.add("軒・棟の 1 ブロック差 " + steps);
        int pits = pits(roofs);
        if (pits > 0) problems.add("屋根の窪み " + pits);
        int gaps = gaps(layout, grid);
        if (gaps > 0) problems.add("壁と屋根の隙間 " + gaps);
        int blocked = doors(marks, grid);
        if (blocked > 0) problems.add("ふさがれた扉 " + blocked);
        int heads = headroom(grid);
        if (heads > 0) problems.add("頭のつかえる階段 " + heads);
        int floating = floating(grid, ground);
        if (floating > 0) problems.add("浮いたブロック " + floating);
        return problems;
    }

    /** The pairs of lots next to each other whose eaves, or ridges, are one block apart. */
    static int steps(Layout layout, Roofs.Result roofs) {
        int count = 0;
        for (Layout.LotGeo a : layout.lots) {
            for (int b : layout.neighbours(a.index)) {
                if (b <= a.index) continue;
                Layout.LotGeo other = layout.lots.get(b);
                if (Math.abs(a.eave - other.eave) == 1) count++;
                Roofs.LotRoof ra = roofs.of(a.index);
                Roofs.LotRoof rb = roofs.of(b);
                if (ra != null && rb != null && ra.ridgeRow > Integer.MIN_VALUE && rb.ridgeRow > Integer.MIN_VALUE && Math.abs(ra.ridgeRow - rb.ridgeRow) == 1) count++;
            }
        }
        return count;
    }

    /** The roof columns lower than the water would stand over them (a priority flood from each roof group's edges). */
    static int pits(Roofs.Result roofs) {
        int count = 0;
        for (Map<Long, Roofs.Col> group : roofs.tops.values()) count += pits(group);
        return count;
    }

    private static int pits(Map<Long, Roofs.Col> cols) {
        Map<Long, Double> height = new HashMap<>();
        for (Map.Entry<Long, Roofs.Col> entry : cols.entrySet()) height.put(entry.getKey(), (double) entry.getValue().top + (entry.getValue().shape == Cell.Form.SLAB ? 0.5 : 1.0));
        Map<Long, Double> level = new HashMap<>();
        PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Double.compare(Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1])));
        for (Map.Entry<Long, Double> entry : height.entrySet()) {
            long key = entry.getKey();
            int x = (int) ((key >> 32) - 100_000);
            int z = (int) ((key & 0xFFFFFFFFL) - 100_000);
            boolean edge = false;
            for (int way = 0; way < 4; way++) if (!height.containsKey(Area.key(x + Cell.STEP[way][0], z + Cell.STEP[way][1]))) edge = true;
            if (edge) {
                level.put(key, entry.getValue());
                queue.add(new long[]{key, Double.doubleToLongBits(entry.getValue())});
            }
        }
        while (!queue.isEmpty()) {
            long[] item = queue.poll();
            long key = item[0];
            double here = Double.longBitsToDouble(item[1]);
            int x = (int) ((key >> 32) - 100_000);
            int z = (int) ((key & 0xFFFFFFFFL) - 100_000);
            for (int way = 0; way < 4; way++) {
                long next = Area.key(x + Cell.STEP[way][0], z + Cell.STEP[way][1]);
                Double h = height.get(next);
                if (h == null || level.containsKey(next)) continue;
                double water = Math.max(h, here);
                level.put(next, water);
                queue.add(new long[]{next, Double.doubleToLongBits(water)});
            }
        }
        int count = 0;
        for (Map.Entry<Long, Double> entry : height.entrySet()) if (level.getOrDefault(entry.getKey(), entry.getValue()) > entry.getValue() + 0.6) count++;
        return count;
    }

    /** The wall-line blocks of a lot's top storey with nothing over their beam (no roof, no gable, no wall of another lot). */
    static int gaps(Layout layout, Grid grid) {
        int count = 0;
        for (Layout.LotGeo geo : layout.lots) {
            if (geo.lot.roof.type == Plan.RoofType.NONE) continue;
            Area top = geo.top();
            int beam = geo.eave - 1;
            for (int[] cell : top.cells()) {
                if (top.depth(cell[0], cell[1]) != 1) continue;
                Cell wall = grid.get(cell[0], beam, cell[1]);
                // (this lot's own wall only: another lot's may stand on its line, inside that one)
                if (wall == null || grid.ownerOf(Grid.pack(cell[0], beam, cell[1])) != geo.index) continue;
                // (a gable's wall stands a block in from its frame: the block behind counts)
                boolean closed = covered(grid, cell[0], beam, cell[1]);
                for (int way = 0; way < 4 && !closed; way++) {
                    int x = cell[0] + Cell.STEP[way][0];
                    int z = cell[1] + Cell.STEP[way][1];
                    if (top.depth(x, z) == 2 && covered(grid, x, beam, z)) closed = true;
                }
                if (!closed) count++;
            }
        }
        return count;
    }

    private static boolean covered(Grid grid, int x, int beam, int z) {
        return grid.get(x, beam + 1, z) != null || grid.get(x, beam + 2, z) != null;
    }

    /** The doors with something in front of them (the two blocks before them, at their height). */
    static int doors(Marks marks, Grid grid) {
        int count = 0;
        for (Marks.Door door : marks.doors) {
            int[] o = Cell.STEP[door.out()];
            int x = door.x() + 2 * o[0];
            int z = door.z() + 2 * o[1];
            Cell low = grid.get(x, door.y(), z);
            Cell high = grid.get(x, door.y() + 1, z);
            if (low != null && low.role() != Role.RAIL && low.form() != Cell.Form.TRAPDOOR || high != null && high.role() != Role.RAIL) count++;
        }
        return count;
    }

    /** The steps of outside stairs with a block in the two over them. */
    static int headroom(Grid grid) {
        int count = 0;
        for (Map.Entry<Long, Cell> entry : grid.cells().entrySet()) {
            Cell cell = entry.getValue();
            if (cell.role() != Role.STAIR || cell.form() != Cell.Form.STAIRS) continue;
            long key = entry.getKey();
            Cell over = grid.get(Grid.x(key), Grid.y(key) + 1, Grid.z(key));
            Cell over2 = grid.get(Grid.x(key), Grid.y(key) + 2, Grid.z(key));
            if (over != null || over2 != null && over2.form() != Cell.Form.STAIRS) count++;
        }
        return count;
    }

    /** The blocks not held up: not joined (through the blocks round them) to one standing on the ground. */
    static int floating(Grid grid, Ground ground) {
        Set<Long> held = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long key : grid.cells().keySet()) {
            int x = Grid.x(key);
            int y = Grid.y(key);
            int z = Grid.z(key);
            if (y <= ground.at(x, z) + 1) {
                held.add(key);
                queue.add(key);
            }
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long key = queue.poll();
            for (int[] s : steps) {
                long next = Grid.pack(Grid.x(key) + s[0], Grid.y(key) + s[1], Grid.z(key) + s[2]);
                if (held.contains(next) || !grid.cells().containsKey(next)) continue;
                held.add(next);
                queue.add(next);
            }
        }
        return grid.size() - held.size();
    }
}
