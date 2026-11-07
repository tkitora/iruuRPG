package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * (t313) Makes a building of lots: the plan made regular (spec 1/6), each lot's foundation and walls (2/6) of the kit's parts (3/6), the roofs (4/6), the
 * parts put on it and the stilts (5/6), then the blocks finished as the game would have them and the building checked. Each lot's foundation and walls are
 * drawn with a random of their own and kept ({@link Cache}) by what they depend on (the lot and the lots beside it): a lot changed makes again only itself and
 * the lots beside it (spec 1/6), the others stay as they were.
 */
public final class PlotGenerator {

    private PlotGenerator() {
    }

    /** A building made: its blocks, its plan as made regular, what the checks found, how it scores, and how many lots were made again (the rest kept). */
    public record Result(Grid grid, Layout layout, List<String> problems, List<String> notes, double score, String pattern, int tries, int rebuilt) {

        public Result(Grid grid, Layout layout, List<String> problems, List<String> notes, double score, String pattern, int tries) {
            this(grid, layout, problems, notes, score, pattern, tries, layout.lots.size());
        }
    }

    /** The lots' foundations and walls kept by what they were made from (the last {@value #KEEP}). */
    public static final class Cache {
        static final int KEEP = 48;
        private final Map<String, Piece> pieces = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Piece> eldest) {
                return size() > KEEP;
            }
        };
    }

    private record Piece(Grid grid, Marks marks) {
    }

    /** The building of {@code plan}, its parts chosen from {@code kit}, on {@code ground}. */
    public static Result build(Plan plan, Kit kit, long seed, Ground ground) {
        return build(plan, kit, seed, ground, null);
    }

    public static Result build(Plan plan, Kit kit, long seed, Ground ground, Cache cache) {
        Layout layout = Layout.of(plan);
        Grid grid = new Grid();
        List<String> notes = new ArrayList<>(layout.notes);
        Ground terrain = ground == null ? Ground.FLAT : ground;
        Marks marks = new Marks();
        int rebuilt = 0;
        for (Layout.LotGeo geo : layout.lots) {
            String key = cache == null ? null : signature(layout, geo, kit, seed, terrain);
            Piece piece;
            synchronized (cache == null ? new Object() : cache) {
                piece = cache == null ? null : cache.pieces.get(key);
            }
            if (piece == null) {
                rebuilt++;
                Grid own = new Grid();
                Marks mine = new Marks();
                Random random = new Random(seed * 31 + geo.lot.name.hashCode());
                Base.lot(layout, geo, own, terrain, kit);
                Walls.lot(layout, geo, own, kit, random, mine);
                piece = new Piece(own, mine);
                if (cache != null) {
                    synchronized (cache) {
                        cache.pieces.put(key, piece);
                    }
                }
            }
            grid.putAll(piece.grid());
            marks.addAll(piece.marks());
        }
        inside(layout, grid);
        Random random = new Random(seed);
        Roofs.Result roofs = Roofs.build(layout, grid, kit, random);
        Attach.build(layout, roofs, marks, grid, kit, random, terrain, notes);
        int swept = sweep(grid, terrain);
        if (swept > 0) notes.add("宙に浮いた小さなかけらを " + swept + " ブロック取りました");
        grid.finish();
        List<String> problems = Checks.run(layout, roofs, marks, grid, terrain);
        double score = Score.of(layout, roofs, grid, problems);
        return new Result(grid, layout, problems, notes, score, "", 1, rebuilt);
    }

    /**
     * What a lot's foundation and walls are made from: the lot itself, the lots beside it (their places, shapes, heights), the module, the kit, the seed and the
     * ground under it and round it.
     */
    static String signature(Layout layout, Layout.LotGeo geo, Kit kit, long seed, Ground ground) {
        StringBuilder text = new StringBuilder();
        text.append(seed).append('|').append(layout.plan.module).append('|').append(kit.version()).append('|').append(geo.index).append('|').append(geo.lot.toMap());
        for (int other : layout.neighbours(geo.index)) {
            Layout.LotGeo next = layout.lots.get(other);
            text.append("|n").append(other).append(':').append(next.lot.shape).append(next.lot.x).append(',').append(next.lot.z).append(',').append(next.lot.w).append(',')
                    .append(next.lot.d).append(',').append(next.y0).append(',').append(next.eave);
            for (Area area : next.plans) text.append('/').append(area.x0).append(',').append(area.z0).append(',').append(area.x1).append(',').append(area.z1);
            for (int b : next.bottom) text.append(';').append(b);
        }
        long sum = 0;
        for (int x = geo.base.x0 - 2; x <= geo.base.x1 + 2; x++) for (int z = geo.base.z0 - 2; z <= geo.base.z1 + 2; z++) sum = sum * 31 + ground.at(x, z);
        return text.append("|g").append(sum).toString();
    }

    /**
     * A lot's walls and floors inside another lot as high or higher (inside its storeys, at their rows) are taken away: inside a building there is no wall
     * (spec 2/6), whichever lot's wall it was (a tower standing over a corner of the main block, a wing's wall on the shared line).
     */
    static void inside(Layout layout, Grid grid) {
        for (long key : new ArrayList<>(grid.cells().keySet())) {
            int owner = grid.ownerOf(key);
            if (owner < 0) continue;
            int x = Grid.x(key);
            int y = Grid.y(key);
            int z = Grid.z(key);
            Layout.LotGeo self = layout.lots.get(owner);
            if (y < self.y0) continue;
            for (Layout.LotGeo other : layout.lots) {
                if (other.index == owner || other.eave < self.eave || !other.covers(x, y, z)) continue;
                // (on the other's wall line, its own frame is there: a block of this lot's there only when it is the other's interior side)
                int depth = other.plans[other.storeyAt(y)].depth(x, z);
                if (depth >= 2 || self.covers(x, y, z) && depth >= 1) {
                    grid.clear(x, y, z);
                    break;
                }
            }
        }
    }

    /** The small pieces held up by nothing (not joined to a block on the ground, 40 blocks or fewer) are taken away; how many blocks. */
    static int sweep(Grid grid, Ground ground) {
        java.util.Set<Long> held = new java.util.HashSet<>();
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
        for (long key : grid.cells().keySet()) {
            if (Grid.y(key) <= ground.at(Grid.x(key), Grid.z(key)) + 1) {
                held.add(key);
                queue.add(key);
            }
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        flood(grid, held, queue, steps);
        java.util.Set<Long> seen = new java.util.HashSet<>(held);
        int removed = 0;
        for (long start : new ArrayList<>(grid.cells().keySet())) {
            if (seen.contains(start)) continue;
            java.util.Set<Long> piece = new java.util.HashSet<>();
            piece.add(start);
            java.util.ArrayDeque<Long> next = new java.util.ArrayDeque<>();
            next.add(start);
            flood(grid, piece, next, steps);
            seen.addAll(piece);
            if (piece.size() > 40) continue;
            for (long key : piece) grid.clear(Grid.x(key), Grid.y(key), Grid.z(key));
            removed += piece.size();
        }
        return removed;
    }

    private static void flood(Grid grid, java.util.Set<Long> into, java.util.ArrayDeque<Long> queue, int[][] steps) {
        while (!queue.isEmpty()) {
            long key = queue.poll();
            for (int[] s : steps) {
                long n = Grid.pack(Grid.x(key) + s[0], Grid.y(key) + s[1], Grid.z(key) + s[2]);
                if (into.contains(n) || !grid.cells().containsKey(n)) continue;
                into.add(n);
                queue.add(n);
            }
        }
    }
}
