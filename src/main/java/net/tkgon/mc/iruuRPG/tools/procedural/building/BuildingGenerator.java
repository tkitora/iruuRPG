package net.tkgon.mc.iruuRPG.tools.procedural.building;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * (t295) Makes a building: the podiums, the walls storey by storey, the roofs, the parts, then the blocks finished as the game would. A pattern is made as
 * {@code candidates} buildings from the seed; each is checked (the eaves and the ridges of the masses level or 2 or more apart, no hollow in the roof that
 * would hold water, no gap between the walls and the roof, nothing floating, the windows and the posts on the grid) and on a failure made again with one number
 * drawn again, up to {@code retries} times; the best by {@link #score} is kept. The same seed and settings always give the same building.
 */
public final class BuildingGenerator {

    private BuildingGenerator() {
    }

    /** What was made, and how it fared: the problems found (empty: it passed every check), the score, the share of its height the roof is. */
    public record Result(SemanticVoxelGrid grid, Building building, List<String> problems, double score, double roofShare, int tries) {
    }

    /** The made building with what the checks need. */
    record Made(SemanticVoxelGrid grid, Building building, Roofs roofs, List<List<Ring>> rings) {
    }

    public static Result generate(BuildParams p, long seed, Ground ground) {
        if (p.pattern == 0) {
            Building building = Archetypes.manual(p, new Random(seed));
            building.center();
            Made made = make(building, ground, new Random(seed ^ 0x5DEECE66DL));
            List<String> problems = check(made);
            return new Result(made.grid(), building, problems, score(made), roofShare(made), 1);
        }
        Result best = null;
        int tries = 0;
        int candidates = Math.max(1, Math.min(8, p.candidates));
        int retries = Math.max(0, Math.min(10, p.retries));
        for (int candidate = 0; candidate < candidates; candidate++) {
            Random random = new Random(seed * 31L + candidate * 7919L);
            Archetypes.Draw draw = Archetypes.draw(p, random);
            Made made = null;
            List<String> problems = null;
            for (int attempt = 0; attempt <= retries; attempt++) {
                tries++;
                long partSeed = seed * 131L + candidate * 17L + attempt;
                Building building = Archetypes.make(p, draw, new Random(partSeed));
                building.center();
                made = make(building, ground, new Random(partSeed ^ 0x5DEECE66DL));
                problems = check(made);
                if (problems.isEmpty()) break;
                draw = draw.copy();
                draw.change(random, p);
            }
            Result result = new Result(made.grid(), made.building(), problems, score(made), roofShare(made), tries);
            if (best == null || better(result, best)) best = result;
        }
        return new Result(best.grid(), best.building(), best.problems(), best.score(), best.roofShare(), tries);
    }

    private static boolean better(Result a, Result b) {
        if (a.problems().isEmpty() != b.problems().isEmpty()) return a.problems().isEmpty();
        if (a.problems().size() != b.problems().size()) return a.problems().size() < b.problems().size();
        return a.score() > b.score();
    }

    /** Makes the blocks of a building. */
    static Made make(Building b, Ground ground, Random random) {
        SemanticVoxelGrid g = new SemanticVoxelGrid();
        List<List<Ring>> rings = new ArrayList<>();
        for (Building.Podium podium : b.podiums) Walls.podium(g, podium, ground);
        int count = b.masses.size();
        for (int index = 0; index < count; index++) rings.add(Walls.mass(g, b.masses.get(index), ground));
        // the entrances, and the porches in front of them
        for (int index = 0; index < count; index++) {
            Mass m = b.masses.get(index);
            if (!m.door || m.postsOnly) continue;
            List<int[]> door = Walls.door(g, m, rings.get(m.id).get(0));
            Building.Part porch = null;
            for (Building.Part part : b.parts) if (part.type() == Building.PartType.PORCH && part.mass() == m.id) porch = part;
            Mass made = porch == null ? null : Parts.porch(b, g, m, rings.get(m.id).get(0), door, porch.at() == -2, ground);
            if (made != null) rings.add(Walls.mass(g, made, ground));
            else if (m.y0 > 0) Walls.stairsDown(g, door, m.doorWay, m.y0, ground);
        }
        for (Mass m : b.masses) Walls.arch(g, m);
        Roofs roofs = new Roofs(g, b.masses);
        for (Mass m : new ArrayList<>(b.masses)) roofs.add(m);
        for (Building.Part part : b.parts) {
            if (part.type() == Building.PartType.DORMER) Parts.dormers(b, g, roofs, rings, b.mass(part.mass()), part.side(), part.at(), ground);
        }
        roofs.build();
        for (Mass m : b.masses) roofs.underRoof(m, top(rings, m));
        roofs.trim();
        for (Mass m : b.masses) roofs.eaveCorbels(m, top(rings, m));
        for (Mass m : b.masses) if (m.postsOnly) Parts.porchPosts(g, roofs, m, b.mass(m.parent).doorWay);
        for (Building.Part part : b.parts) {
            Mass m = b.mass(part.mass());
            switch (part.type()) {
                case CHIMNEY -> Parts.chimney(g, roofs, m, random);
                case BAY_WINDOW -> Parts.bayWindow(g, m, rings.get(m.id), part.side(), part.at(), part.storey());
                case BALCONY -> Parts.balcony(g, m, rings.get(m.id), part.side(), part.at(), part.storey(), random.nextBoolean());
                case SAILS -> Parts.sails(g, roofs, m, part.side());
                default -> {
                }
            }
        }
        sweep(g);
        g.finish();
        return new Made(g, b, roofs, rings);
    }

    /**
     * Takes away the little pieces left hanging in the air (up to 16 blocks: the ends of eaves and verges cut off where a mass's roof was cut by a taller one's);
     * bigger ones stay, for the checks to find.
     */
    static void sweep(SemanticVoxelGrid g) {
        Set<Long> grounded = groundedSet(g);
        Set<Long> seen = new HashSet<>(grounded);
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (long start : new ArrayList<>(g.cells().keySet())) {
            if (seen.contains(start)) continue;
            List<Long> island = new ArrayList<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                long key = queue.poll();
                island.add(key);
                for (int[] step : steps) {
                    long next = SemanticVoxelGrid.pack(SemanticVoxelGrid.x(key) + step[0], SemanticVoxelGrid.y(key) + step[1], SemanticVoxelGrid.z(key) + step[2]);
                    if (g.cells().containsKey(next) && seen.add(next)) queue.add(next);
                }
            }
            if (island.size() <= 16) for (long key : island) g.cells().remove(key);
        }
    }

    /** The blocks joined face to face to the lowest layer (and so to the ground). */
    static Set<Long> groundedSet(SemanticVoxelGrid g) {
        int[] box = g.bounds();
        Set<Long> seen = new HashSet<>();
        if (box == null) return seen;
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long key : g.cells().keySet()) {
            if (SemanticVoxelGrid.y(key) <= Math.max(box[1], 0)) {
                seen.add(key);
                queue.add(key);
            }
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long key = queue.poll();
            for (int[] step : steps) {
                long next = SemanticVoxelGrid.pack(SemanticVoxelGrid.x(key) + step[0], SemanticVoxelGrid.y(key) + step[1], SemanticVoxelGrid.z(key) + step[2]);
                if (g.cells().containsKey(next) && seen.add(next)) queue.add(next);
            }
        }
        return seen;
    }

    private static Ring top(List<List<Ring>> rings, Mass m) {
        List<Ring> list = rings.get(m.id);
        return list.get(list.size() - 1);
    }

    // ---- the checks -------------------------------------------------------------------------------------------------------------------

    /** The masses that count for the outline: not a lean-to, a porch or a dormer. */
    private static boolean counts(Mass m) {
        return !m.minor && !m.dormer && !m.postsOnly;
    }

    /** What is wrong with a building (empty: nothing). */
    static List<String> check(Made made) {
        List<String> problems = new ArrayList<>();
        int steps = oneBlockSteps(made);
        if (steps > 0) problems.add("軒・棟の高さが1ブロックだけ違う所 " + steps);
        int pits = pits(made.roofs());
        if (pits > 0) problems.add("屋根の窪み " + pits);
        int gaps = gaps(made);
        if (gaps > 0) problems.add("壁と屋根の隙間 " + gaps);
        int floating = floating(made.grid());
        if (floating > 0) problems.add("浮いたブロック " + floating);
        int off = offGrid(made);
        if (off > 0) problems.add("柱・窓が格子からずれた辺 " + off);
        return problems;
    }

    /** The pairs of roof pieces of different masses whose eaves or ridges are 1 block apart. */
    static int oneBlockSteps(Made made) {
        List<Roofs.Piece> pieces = new ArrayList<>();
        for (Roofs.Piece piece : made.roofs().pieces) if (counts(piece.mass)) pieces.add(piece);
        int count = 0;
        for (int i = 0; i < pieces.size(); i++) {
            for (int j = i + 1; j < pieces.size(); j++) {
                Roofs.Piece a = pieces.get(i);
                Roofs.Piece b = pieces.get(j);
                if (a.mass == b.mass) continue;
                if (Math.abs(a.eave() - b.eave()) == 1) count++;
                if (Math.abs(a.ridge() - b.ridge()) == 1) count++;
            }
        }
        return count;
    }

    /** The half-columns of the roofs lower than the water would stand in them (flooding from the roofs' edges, lowest first). */
    static int pits(Roofs roofs) {
        Map<Long, Double> heights = new HashMap<>();
        for (Map.Entry<Long, List<Roofs.Col>> entry : roofs.kept.entrySet()) {
            double top = -Double.MAX_VALUE;
            for (Roofs.Col col : entry.getValue()) top = Math.max(top, col.top);
            heights.put(entry.getKey(), top);
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        Set<Long> seen = new HashSet<>();
        for (Map.Entry<Long, Double> entry : heights.entrySet()) {
            int hx = Roofs.hx(entry.getKey());
            int hz = Roofs.hz(entry.getKey());
            boolean edge = false;
            for (int[] step : steps) if (!heights.containsKey(Roofs.half(hx + step[0], hz + step[1]))) edge = true;
            if (edge) {
                queue.add(new double[]{entry.getValue(), hx, hz});
                seen.add(entry.getKey());
            }
        }
        int pits = 0;
        while (!queue.isEmpty()) {
            double[] at = queue.poll();
            for (int[] step : steps) {
                long key = Roofs.half((int) at[1] + step[0], (int) at[2] + step[1]);
                Double height = heights.get(key);
                if (height == null || !seen.add(key)) continue;
                if (height < at[0] - 0.01) pits++;
                queue.add(new double[]{Math.max(height, at[0]), (int) at[1] + step[0], (int) at[2] + step[1]});
            }
        }
        return pits;
    }

    /** The cells of the wall line of the top storeys whose first block over the walls' top is not right on it. */
    static int gaps(Made made) {
        int count = 0;
        SemanticVoxelGrid g = made.grid();
        for (Mass m : made.building().masses) {
            if (m.postsOnly || m.roof.open) continue;
            Footprint top = m.top();
            int wallTop = m.wallTop();
            for (int[] cell : top.cells()) {
                if (top.depth(cell[0], cell[1]) != 1 || made.roofs().inTaller(m, cell[0], cell[1])) continue;
                int first = Integer.MIN_VALUE;
                for (int y = wallTop + 1; y <= wallTop + 48 && first == Integer.MIN_VALUE; y++) if (g.has(cell[0], y, cell[1])) first = y;
                if (first == Integer.MIN_VALUE || first == wallTop + 1) continue;
                // (a gable's wall stands a block behind its frame: the cell behind closes it)
                Ring.Spot spot = top(made.rings(), m).spot(cell[0], cell[1]);
                if (spot != null && g.has(cell[0] - spot.nx(), wallTop + 1, cell[1] - spot.nz())) continue;
                count++;
            }
        }
        return count;
    }

    /** The blocks not joined (face to face) to the ground through other blocks. */
    static int floating(SemanticVoxelGrid g) {
        return g.size() - groundedSet(g).size();
    }

    /** The straight sides of the ground storeys (of masses with straight plans) that are not a whole number of bays long. */
    static int offGrid(Made made) {
        int count = 0;
        for (Mass m : made.building().masses) {
            if (!counts(m) || m.tower) continue;
            Footprint.Kind kind = m.plan.kind();
            if (kind != Footprint.Kind.RECT && kind != Footprint.Kind.UNION) continue;
            for (Footprint.Edge edge : m.plan.edges()) if (!edge.diagonal() && edge.length() % m.module != 0) count++;
        }
        return count;
    }

    // ---- the score --------------------------------------------------------------------------------------------------------------------

    /** The share of the main mass's height (from the ground to its ridge) its roof is. */
    static double roofShare(Made made) {
        Mass main = null;
        for (Mass m : made.building().masses) if (counts(m) && !m.tower) {
            main = m;
            break;
        }
        if (main == null) return 0.0;
        int eave = Integer.MAX_VALUE;
        int ridge = Integer.MIN_VALUE;
        for (Roofs.Piece piece : made.roofs().pieces) {
            if (piece.mass != main) continue;
            eave = Math.min(eave, piece.eave());
            ridge = Math.max(ridge, piece.ridge());
        }
        if (ridge == Integer.MIN_VALUE || ridge <= 0) return 0.0;
        return (ridge - Math.max(eave, 0)) / (double) (ridge + 1);
    }

    /**
     * How good the building looks: how near its roof's share of the height is to 0.35 to 0.55, how clearly its towers stand over its masses (2 or more), how even
     * its front is left and right; less for every 1-block step between the masses' eaves and ridges and for every face of its roofs.
     */
    static double score(Made made) {
        double share = roofShare(made);
        double roof = share >= 0.35 && share <= 0.55 ? 1.0 : Math.max(0.0, 1.0 - 4.0 * (share < 0.35 ? 0.35 - share : share - 0.55));
        // the outline: the towers over the main mass's ridge by 2 or more, and the heights in clear steps
        List<Integer> tops = new ArrayList<>();
        int mainRidge = Integer.MIN_VALUE;
        double towers = 0.0;
        for (Mass m : made.building().masses) {
            if (!counts(m)) continue;
            int ridge = Integer.MIN_VALUE;
            for (Roofs.Piece piece : made.roofs().pieces) if (piece.mass == m) ridge = Math.max(ridge, piece.ridge());
            if (ridge == Integer.MIN_VALUE) continue;
            if (!m.tower && mainRidge == Integer.MIN_VALUE) mainRidge = ridge;
            tops.add(ridge);
        }
        for (Mass m : made.building().masses) {
            if (!m.tower || !counts(m)) continue;
            int ridge = Integer.MIN_VALUE;
            for (Roofs.Piece piece : made.roofs().pieces) if (piece.mass == m) ridge = Math.max(ridge, piece.ridge());
            if (mainRidge != Integer.MIN_VALUE && ridge - mainRidge >= 2) towers += 0.5;
        }
        tops.sort(Integer::compare);
        int levels = 0;
        for (int index = 1; index < tops.size(); index++) if (tops.get(index) - tops.get(index - 1) >= 2) levels++;
        double outline = tops.size() <= 1 ? 0.5 : Math.min(1.0, towers) * 0.5 + Math.min(1.0, levels / 2.0) * 0.5;
        // the front's balance: the height of the outline seen from the front, left against right
        Map<Integer, Integer> heights = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (long key : made.grid().cells().keySet()) {
            int x = SemanticVoxelGrid.x(key);
            heights.merge(x, SemanticVoxelGrid.y(key), Math::max);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
        }
        double middle = (minX + maxX) / 2.0;
        double left = 0.0;
        double right = 0.0;
        for (Map.Entry<Integer, Integer> entry : heights.entrySet()) {
            if (entry.getKey() < middle) left += entry.getValue();
            else if (entry.getKey() > middle) right += entry.getValue();
        }
        double balance = left + right <= 0 ? 1.0 : 1.0 - Math.abs(left - right) / (left + right);
        // the faces of the roofs
        Set<Long> faces = new HashSet<>();
        for (List<Roofs.Col> list : made.roofs().kept.values()) for (Roofs.Col col : list) faces.add(col.piece.id * 64L + col.face);
        return roof + outline + balance - 0.5 * oneBlockSteps(made) - 0.02 * faces.size();
    }
}
