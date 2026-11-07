package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * (t313, spec 5/6) The parts put on the building, each by its rule: the steps up to a raised door, a porch (posts and a shed roof) before the ground door, the
 * balconies and galleries along a storey's sides, outside stairs from an upper door down to the ground (their way searched round the walls, with landings),
 * dormers on the roof in the posts' grid below the ridge, a chimney by the ridge (or up an outside wall from the ground) two blocks over the ridge, finials,
 * and the special parts (a windmill's sails, a bell) at a face's anchor. A part is laid out first and put only if it covers nothing it may not; one that does
 * is moved along and tried again, and left out when nowhere fits.
 */
final class Attach {

    private Attach() {
    }

    /** A part laid out before it is put: its blocks, the blocks it takes away, and the roles of what it may cover. */
    static final class Stamp {
        final String name;
        final Map<Long, Cell> cells = new LinkedHashMap<>();
        final Set<Long> clears = new HashSet<>();
        final Set<Role> over;

        Stamp(String name, Set<Role> over) {
            this.name = name;
            this.over = over;
        }

        void put(int x, int y, int z, Cell cell) {
            cells.put(Grid.pack(x, y, z), cell);
        }

        void clear(int x, int y, int z) {
            clears.add(Grid.pack(x, y, z));
        }

        /** Whether it covers only air and what it may cover. */
        boolean fits(Grid grid) {
            for (long key : cells.keySet()) {
                Cell old = grid.cells().get(key);
                if (old != null && !over.contains(old.role())) return false;
            }
            return true;
        }

        void apply(Grid grid, int owner) {
            int keep = grid.owner;
            grid.owner = owner;
            for (long key : clears) grid.clear(Grid.x(key), Grid.y(key), Grid.z(key));
            for (Map.Entry<Long, Cell> entry : cells.entrySet()) {
                long key = entry.getKey();
                grid.set(Grid.x(key), Grid.y(key), Grid.z(key), entry.getValue());
            }
            grid.owner = keep;
        }
    }

    private static final Set<Role> ROOFS = EnumSet.of(Role.ROOF, Role.EAVE, Role.RIDGE, Role.FLASHING);

    static void build(Layout layout, Roofs.Result roofs, Marks marks, Grid grid, Kit kit, Random random, Ground ground, List<String> notes) {
        for (Marks.Door door : marks.doors) {
            Layout.LotGeo geo = layout.lots.get(door.lot());
            if (door.storey() == 0) {
                if (geo.lot.porch) porch(layout, geo, door, grid, kit, ground, notes);
                else steps(layout, geo, door, grid, ground);
            }
        }
        for (Layout.LotGeo geo : layout.lots) balconies(layout, geo, grid, kit);
        for (Marks.Door door : marks.doors) {
            Layout.LotGeo geo = layout.lots.get(door.lot());
            if (door.storey() > 0 && geo.lot.floors.get(door.storey()).stairs) stairs(layout, geo, door, grid, ground, notes);
            else if (door.storey() == 0 && geo.lot.foundation == Plan.Foundation.STILTS && geo.y0 - ground.at(door.x(), door.z()) > 4) {
                // (a door high on stilts with no room for steps straight out: stairs round the walls)
                if (!clearAhead(grid, door)) stairs(layout, geo, door, grid, ground, notes);
            }
        }
        for (Layout.LotGeo geo : layout.lots) {
            Roofs.LotRoof roof = roofs.of(geo.index);
            if (roof == null) continue;
            dormers(layout, roof, roofs, marks, grid, kit, random, notes);
            chimney(layout, roof, roofs, grid, kit, ground, notes);
            finial(roof, roofs, grid, kit);
            special(layout, roof, roofs, grid, ground, notes);
        }
    }

    private static boolean clearAhead(Grid grid, Marks.Door door) {
        int[] o = Cell.STEP[door.out()];
        return grid.get(door.x() + 2 * o[0], door.y() - 1, door.z() + 2 * o[1]) != null;
    }

    // ---- steps and porches -----------------------------------------------------------------------------------------------------------

    /** The steps from a raised ground door straight out to the ground: a landing before the door, then a stair down each block, the stone under them. */
    private static void steps(Layout layout, Layout.LotGeo geo, Marks.Door door, Grid grid, Ground ground) {
        int[] o = Cell.STEP[door.out()];
        int[] r = Area.right(door.out());
        int floor = door.y() - 1;
        // (the door is in the wall, a block in from its frame: the steps start a block out of the frame)
        int fx = door.x() + 2 * o[0];
        int fz = door.z() + 2 * o[1];
        if (floor <= ground.at(fx, fz)) return;
        Stamp stamp = new Stamp("steps", EnumSet.of(Role.PLINTH));
        int half = 1;
        for (int dist = 1; dist < 12; dist++) {
            int row = floor - (dist - 1);
            boolean last = false;
            for (int s = -half; s <= half + (door.width() - 1); s++) {
                int x = door.x() + (dist + 1) * o[0] + s * r[0];
                int z = door.z() + (dist + 1) * o[1] + s * r[1];
                int g = ground.at(x, z);
                if (row <= g) {
                    last = true;
                    continue;
                }
                stamp.put(x, row, z, dist == 1 ? Cell.full(Role.PLINTH) : Cell.stairs(Role.PLINTH, (door.out() + 2) % 4, false));
                for (int y = g + 1; y < row; y++) stamp.put(x, y, z, Cell.full(Role.PLINTH));
                if (row - 1 <= g) last = true;
            }
            if (last && row - 1 <= ground.at(door.x() + (dist + 1) * o[0], door.z() + (dist + 1) * o[1])) break;
        }
        if (stamp.fits(grid)) stamp.apply(grid, geo.index);
    }

    /**
     * A porch before the ground door (spec 5/6): its floor at the door's, two posts at its outer corners and a shed roof from the wall over the door out past
     * them; the steps go on from its front when it is raised.
     */
    private static void porch(Layout layout, Layout.LotGeo geo, Marks.Door door, Grid grid, Kit kit, Ground ground, List<String> notes) {
        Kit.Part part = kit.get("porch");
        int depth = part == null ? 3 : Math.max(2, Math.min(4, (int) Math.round(part.number("depth", 3))));
        int width = part == null ? 5 : Math.max(3, Math.min(7, (int) Math.round(part.number("width", 5))));
        int[] o = Cell.STEP[door.out()];
        int[] r = Area.right(door.out());
        int floor = door.y() - 1;
        // (its roof starts against the wall over the door: under the storey above, or past it where that juts out)
        int beam = geo.beamRow(0);
        int jut = geo.floors() > 1 && door.side() >= 0 && door.side() < 4 ? geo.out[1][door.side()] : 0;
        if (jut > 0) beam -= 1;
        int half = (width - 1) / 2;
        int center = (door.width() - 1);
        for (int shift : new int[]{0, -2, 2}) {
            Stamp stamp = new Stamp("porch", EnumSet.of(Role.PLINTH));
            for (int dist = 1; dist <= depth; dist++) {
                for (int s = -half; s <= half + center; s++) {
                    int x = door.x() + (dist + 1) * o[0] + (s + shift) * r[0];
                    int z = door.z() + (dist + 1) * o[1] + (s + shift) * r[1];
                    stamp.put(x, floor, z, Cell.full(Role.FLOOR));
                    for (int y = ground.at(x, z) + 1; y < floor; y++) stamp.put(x, y, z, Cell.full(Role.PLINTH));
                }
            }
            // the roof: 1:2 from the wall down outward, a block past the posts and the sides
            for (int dist = 1 + jut; dist <= depth + 1; dist++) {
                int row = beam - (dist - 1 - jut) / 2;
                boolean slab = (dist - 1 - jut) % 2 == 1;
                for (int s = -half - 1; s <= half + center + 1; s++) {
                    int x = door.x() + (dist + 1) * o[0] + (s + shift) * r[0];
                    int z = door.z() + (dist + 1) * o[1] + (s + shift) * r[1];
                    stamp.put(x, row, z, slab ? Cell.slab(Role.ROOF, false) : Cell.stairs(Role.ROOF, (door.out() + 2) % 4, false));
                }
            }
            // the posts at the outer corners, up to under the roof
            int postTop = beam - (depth - 1 - jut) / 2 - 1;
            if (postTop - floor < 2) continue;
            for (int s : new int[]{-half, half + center}) {
                int x = door.x() + (depth + 1) * o[0] + (s + shift) * r[0];
                int z = door.z() + (depth + 1) * o[1] + (s + shift) * r[1];
                for (int y = floor + 1; y <= postTop; y++) stamp.put(x, y, z, Cell.log(Role.FRAME, 1));
            }
            if (!stamp.fits(grid)) continue;
            stamp.apply(grid, geo.index);
            // the steps down from its front
            Marks.Door front = new Marks.Door(door.lot(), 0, door.side(), door.x() + depth * o[0], door.y(), door.z() + depth * o[1], door.out(), door.width());
            steps(layout, geo, front, grid, ground);
            return;
        }
        notes.add(geo.lot.name + ": ポーチを置ける所がありません");
        steps(layout, geo, door, grid, ground);
    }

    // ---- balconies -------------------------------------------------------------------------------------------------------------------

    /**
     * The balconies of a lot's storeys (a gallery when on every side): boards a block out along the side at the storey's floor, a rail on their edge (open
     * trapdoors, or a fence on a deeper one), corbels under them at the posts.
     */
    private static void balconies(Layout layout, Layout.LotGeo geo, Grid grid, Kit kit) {
        Kit.Part part = kit.get("balcony");
        int depth = part == null ? 1 : Math.max(1, Math.min(3, (int) Math.round(part.number("depth", 1))));
        boolean fence = depth >= 2 || kit.get("rail_trapdoor") == null;
        for (int k = 0; k < geo.floors(); k++) {
            Plan.Floor floor = geo.lot.floors.get(k);
            if (k == 0 && geo.y0 < 2) continue;
            Area plan = geo.plans[k];
            if (plan.shape != Plan.Shape.RECT) {
                if (floor.balcony[0] || floor.balcony[1] || floor.balcony[2] || floor.balcony[3]) ringGallery(layout, geo, k, grid, fence);
                continue;
            }
            int row = geo.bottom[k];
            for (int side = 0; side < 4; side++) {
                if (!floor.balcony[side]) continue;
                int[] o = Area.out(side);
                int length = plan.sideLength(side);
                Stamp stamp = new Stamp("balcony", EnumSet.noneOf(Role.class));
                boolean all = floor.balcony[(side + 1) % 4];
                boolean before = floor.balcony[(side + 3) % 4];
                int from = before ? -depth : 0;
                int to = all ? length - 1 + depth : length - 1;
                for (int t = from; t <= to; t++) {
                    int[] at = alongExtended(plan, side, t);
                    for (int d = 1; d <= depth; d++) {
                        int x = at[0] + d * o[0];
                        int z = at[1] + d * o[1];
                        if (!layout.coveringAt(geo.index, x, row + 1, z).isEmpty() || plan.contains(x, z)) continue;
                        stamp.put(x, row, z, Cell.full(Role.FLOOR));
                        boolean edge = d == depth;
                        if (edge) {
                            stamp.put(x, row + 1, z, fence ? Cell.fence(Role.RAIL) : Cell.trapdoor(Role.RAIL, (side + 2) % 4, false, true));
                        }
                    }
                    // (the corbels: under the posts)
                    Cell post = t >= 0 && t < length ? grid.get(at[0], row + 1, at[1]) : null;
                    if (post != null && post.role() == Role.FRAME) {
                        stamp.put(at[0] + o[0], row - 1, at[1] + o[1], Cell.stairs(Role.CORBEL, (side + 2) % 4, true));
                    }
                }
                // (the rail's ends, back to the wall)
                for (int t : new int[]{from, to}) {
                    if (t == from && before || t == to && all) continue;
                    int[] at = alongExtended(plan, side, t);
                    for (int d = 1; d < depth; d++) {
                        int x = at[0] + d * o[0];
                        int z = at[1] + d * o[1];
                        if (stamp.cells.containsKey(Grid.pack(x, row, z))) stamp.put(x, row + 1, z, Cell.fence(Role.RAIL));
                    }
                }
                if (stamp.fits(grid)) stamp.apply(grid, geo.index);
            }
        }
    }

    /** Block {@code t} along a side, going on past its ends in a straight line. */
    static int[] alongExtended(Area plan, int side, int t) {
        int length = plan.sideLength(side);
        if (t >= 0 && t < length) return plan.along(side, t);
        int[] r = Area.right(side);
        int[] end = plan.along(side, t < 0 ? 0 : length - 1);
        int over = t < 0 ? t : t - (length - 1);
        return new int[]{end[0] + over * r[0], end[1] + over * r[1]};
    }

    /** A gallery round a round or octagonal storey: the ring of boards just outside it, a rail on their edge. */
    private static void ringGallery(Layout layout, Layout.LotGeo geo, int k, Grid grid, boolean fence) {
        Area plan = geo.plans[k];
        int row = geo.bottom[k];
        Stamp stamp = new Stamp("gallery", EnumSet.noneOf(Role.class));
        for (int[] cell : plan.cells()) {
            if (plan.depth(cell[0], cell[1]) != 1) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = cell[0] + dx;
                    int z = cell[1] + dz;
                    if (plan.contains(x, z) || !layout.coveringAt(geo.index, x, row + 1, z).isEmpty()) continue;
                    stamp.put(x, row, z, Cell.full(Role.FLOOR));
                    stamp.put(x, row + 1, z, Cell.fence(Role.RAIL));
                }
            }
        }
        if (stamp.fits(grid)) stamp.apply(grid, geo.index);
    }

    // ---- outside stairs ------------------------------------------------------------------------------------------------------------

    /**
     * Outside stairs from a door down to the ground (spec 5/6): a landing before the door, then a way searched round the walls (a step down each block, a
     * landing where it turns), never under the building nor through anything, with two blocks of room over every step; stone under every block down to the
     * ground. Left out (with a note) when there is no way.
     */
    private static void stairs(Layout layout, Layout.LotGeo geo, Marks.Door door, Grid grid, Ground ground, List<String> notes) {
        int[] o = Cell.STEP[door.out()];
        int lx = door.x() + 2 * o[0];
        int lz = door.z() + 2 * o[1];
        int start = door.y() - 1;
        int[] box = box(layout);
        record State(int x, int z, int row, int dir, boolean flat) {
        }
        record Node(State state, double cost) {
        }
        Map<State, Double> best = new HashMap<>();
        Map<State, State> from = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>((a, b) -> Double.compare(a.cost, b.cost));
        for (int dir : new int[]{(door.out() + 1) % 4, (door.out() + 3) % 4}) {
            State s = new State(lx, lz, start, dir, true);
            best.put(s, 0.0);
            queue.add(new Node(s, 0.0));
        }
        State goal = null;
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 60_000) {
            Node node = queue.poll();
            State s = node.state;
            if (node.cost > best.getOrDefault(s, Double.MAX_VALUE) + 1e-9) continue;
            if (s.row - 1 <= ground.at(s.x, s.z)) {
                goal = s;
                break;
            }
            List<State> nexts = new ArrayList<>();
            int[] step = Cell.STEP[s.dir];
            nexts.add(new State(s.x + step[0], s.z + step[1], s.row - 1, s.dir, false));
            nexts.add(new State(s.x + step[0], s.z + step[1], s.row, s.dir, true));
            if (s.flat) {
                nexts.add(new State(s.x, s.z, s.row, (s.dir + 1) % 4, true));
                nexts.add(new State(s.x, s.z, s.row, (s.dir + 3) % 4, true));
            }
            for (State n : nexts) {
                if (n.x < box[0] || n.x > box[2] || n.z < box[1] || n.z > box[3]) continue;
                boolean moved = n.x != s.x || n.z != s.z;
                if (moved) {
                    if (n.row <= ground.at(n.x, n.z)) continue;
                    if (inside(layout, n.x, n.z) || !free(layout, grid, n.x, n.row, n.z, 3)) continue;
                }
                double cost = node.cost + (moved ? (n.flat ? 2.5 : 1.0) : 0.5) + near(layout, n.x, n.z);
                if (cost < best.getOrDefault(n, Double.MAX_VALUE) - 1e-9) {
                    best.put(n, cost);
                    from.put(n, s);
                    queue.add(new Node(n, cost));
                }
            }
        }
        if (goal == null) {
            notes.add(geo.lot.name + ": " + (door.storey() + 1) + "階の扉からの外階段の道が見つかりません");
            return;
        }
        List<State> path = new ArrayList<>();
        for (State s = goal; s != null; s = from.get(s)) path.add(0, s);
        Stamp stamp = new Stamp("stairs", EnumSet.of(Role.PLINTH));
        Set<Long> placed = new HashSet<>();
        for (int i = 0; i < path.size(); i++) {
            State s = path.get(i);
            if (!placed.add(Area.key(s.x, s.z)) && i > 0 && path.get(i - 1).x == s.x && path.get(i - 1).z == s.z) continue;
            // (a stair rises toward where the path came from: it faces back along its way)
            Cell cell = s.flat ? Cell.full(Role.STAIR) : Cell.stairs(Role.STAIR, (s.dir + 2) % 4, false);
            stamp.put(s.x, s.row, s.z, cell);
            for (int y = ground.at(s.x, s.z) + 1; y < s.row; y++) stamp.put(s.x, y, s.z, Cell.full(Role.STAIR));
        }
        if (stamp.fits(grid)) stamp.apply(grid, geo.index);
        else notes.add(geo.lot.name + ": 外階段が他の部品と重なるので省きました");
    }

    /** The site round the lots, three blocks more each way. */
    private static int[] box(Layout layout) {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Layout.LotGeo geo : layout.lots) {
            for (Area plan : geo.plans) {
                box[0] = Math.min(box[0], plan.x0 - 4);
                box[1] = Math.min(box[1], plan.z0 - 4);
                box[2] = Math.max(box[2], plan.x1 + 4);
                box[3] = Math.max(box[3], plan.z1 + 4);
            }
        }
        return box;
    }

    /** Whether a column is under the building (any lot's storey plans). */
    private static boolean inside(Layout layout, int x, int z) {
        for (Layout.LotGeo geo : layout.lots) for (Area plan : geo.plans) if (plan.contains(x, z)) return true;
        return false;
    }

    /** A little cost to keep a way near the walls (a block off them), not wandering out. */
    private static double near(Layout layout, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (inside(layout, x + dx, z + dz)) return 0;
        return 0.6;
    }

    /** Whether the block and {@code room - 1} blocks over it are free. */
    private static boolean free(Layout layout, Grid grid, int x, int row, int z, int room) {
        for (int y = row; y < row + room; y++) if (grid.get(x, y, z) != null) return false;
        return true;
    }

    // ---- dormers -------------------------------------------------------------------------------------------------------------------

    /**
     * The dormers of a lot's roof (spec 5/6): on its eave sides (the front first), each in the middle of a bay of the top storey's posts, the same both ways,
     * their fronts halfway up the slope and their ridges under the roof's. A front of posts and a window, cheeks down to the roof, a little gable roof (or a
     * shed's, or a round window) running back into the roof.
     */
    private static void dormers(Layout layout, Roofs.LotRoof roof, Roofs.Result roofs, Marks marks, Grid grid, Kit kit, Random random, List<String> notes) {
        int count = roof.geo.lot.roof.dormers;
        if (count <= 0) return;
        if (roof.type != Plan.RoofType.GABLE && roof.type != Plan.RoofType.HIP && roof.type != Plan.RoofType.HALF_HIP) {
            notes.add(roof.geo.lot.name + ": この屋根の形にはドーマーを付けません");
            return;
        }
        Area top = roof.geo.top();
        int m = layout.plan.module;
        List<Kit.Part> types = kit.find(Kit.Category.ROOF_PART, "dormer");
        Kit.Part type = types.isEmpty() ? null : types.get(random.nextInt(types.size()));
        String style = type == null ? "gable" : type.tags.contains("shed") ? "shed" : type.tags.contains("round") ? "round" : "gable";
        int height = type == null ? 4 : Math.max(3, Math.min(5, (int) Math.round(type.number("height", 4))));
        int[] order = {2, 0, 1, 3};
        int placed = 0;
        for (int side : order) {
            if (placed >= count) break;
            if (roof.edges[side] != Roofs.Edge.EAVE || !roof.slopes(side)) continue;
            List<Integer> posts = marks.posts(roof.geo.index, roof.geo.floors() - 1, side);
            int length = top.sideLength(side);
            List<Integer> centers = new ArrayList<>();
            for (int i = 0; i + 1 < posts.size(); i++) {
                int a = posts.get(i);
                int b = posts.get(i + 1);
                int c = (a + b) / 2;
                // (not near the ends: a hip or a gable is there)
                if (c - m / 2 - 1 < 2 || c + m / 2 + 1 > length - 3) continue;
                if ((b - a) % 2 != 0) continue;
                centers.add(c);
            }
            if (centers.isEmpty()) continue;
            int want = Math.min(count - placed, centers.size());
            List<Integer> chosen = symmetric(centers, want, length);
            for (int c : chosen) {
                boolean done = false;
                for (int attempt = 0; attempt < 6 && !done; attempt++) {
                    int shift = attempt % 3 == 0 ? 0 : attempt % 3 == 1 ? -m : m;
                    int span = attempt < 3 ? m : 2;
                    Stamp stamp = dormer(roof, roofs, side, c + shift, span, height, style, grid);
                    if (stamp != null && stamp.fits(grid)) {
                        stamp.apply(grid, roof.geo.index);
                        placed++;
                        done = true;
                        break;
                    }
                }
                if (!done) notes.add(roof.geo.lot.name + ": ドーマーを1つ省きました (置ける所がありません)");
            }
        }
    }

    /** {@code want} of the places, the same both ways round the side's middle. */
    static List<Integer> symmetric(List<Integer> places, int want, int length) {
        double middle = (length - 1) / 2.0;
        List<Integer> sorted = new ArrayList<>(places);
        sorted.sort((a, b) -> Double.compare(Math.abs(a - middle), Math.abs(b - middle)));
        List<Integer> out = new ArrayList<>();
        // (an odd count takes the middle one, then pairs from the middle out)
        if (want % 2 == 1) {
            out.add(sorted.get(0));
        }
        for (int i = want % 2 == 1 ? 1 : 0; i < sorted.size() && out.size() < want; i++) {
            int a = sorted.get(i);
            int mirror = (int) Math.round(2 * middle - a);
            if (out.contains(a)) continue;
            if (sorted.contains(mirror) && !out.contains(mirror) && mirror != a && out.size() + 2 <= want) {
                out.add(a);
                out.add(mirror);
            } else if (out.size() + 1 == want && want % 2 == 0 && out.isEmpty()) {
                out.add(a);
            }
        }
        return out;
    }

    /** One dormer laid out on side {@code side} at {@code c} along it; null when the roof there is too low or too steep for it. */
    private static Stamp dormer(Roofs.LotRoof roof, Roofs.Result roofs, int side, int c, int m, int height, String style, Grid grid) {
        Area top = roof.geo.top();
        int[] o = Area.out(side);
        int[] r = Area.right(side);
        int half = m / 2;
        // (the main roof's top row at u along the side and w in from its wall line)
        java.util.function.BiFunction<Integer, Integer, Roofs.Col> col = (u, w) -> {
            int[] at = top.along(side, u);
            return roofs.top(roof.geo.index).get(Area.key(at[0] - w * o[0], at[1] - w * o[1]));
        };
        Roofs.Col wall = col.apply(c, 0);
        if (wall == null) return null;
        int ridge = roof.ridgeRow;
        int rise = ridge - wall.top;
        // (a low roof takes a lower dormer: three rows at the least)
        height = Math.min(height, rise - half - 1);
        if (height < 3) return null;
        // the front: where the roof is a third of the way up, lower when the dormer would not stay under the ridge
        int front = -1;
        for (int w = 0; w < 20; w++) {
            Roofs.Col here = col.apply(c, w);
            if (here == null) return null;
            if (here.top - wall.top >= Math.max(1, rise / 3)) {
                front = w;
                break;
            }
        }
        while (front > 0 && col.apply(c, front) != null && col.apply(c, front).top + height + half > ridge - 1) front--;
        if (front < 0) return null;
        Roofs.Col at = col.apply(c, front);
        if (at == null) return null;
        int base = at.top;
        int eave = base + height;
        if (eave + half > ridge) return null;
        Stamp stamp = new Stamp("dormer", ROOFS);
        int out = side;
        java.util.function.IntBinaryOperator xs = (u, w) -> top.along(side, u)[0] - w * o[0];
        java.util.function.IntBinaryOperator zs = (u, w) -> top.along(side, u)[1] - w * o[1];
        // the front: posts at its sides, a beam on top, a window between (round, or a pane of the bay's width)
        for (int y = base; y < eave; y++) {
            stamp.put(xs.applyAsInt(c - half, front), y, zs.applyAsInt(c - half, front), Cell.log(Role.FRAME, 1));
            stamp.put(xs.applyAsInt(c + half, front), y, zs.applyAsInt(c + half, front), Cell.log(Role.FRAME, 1));
        }
        for (int u = c - half; u <= c + half; u++) {
            stamp.put(xs.applyAsInt(u, front), base, zs.applyAsInt(u, front), Cell.log(Role.BEAM, Area.axisAlong(side)));
            stamp.put(xs.applyAsInt(u, front), eave - 1, zs.applyAsInt(u, front), Cell.log(Role.BEAM, Area.axisAlong(side)));
        }
        for (int u = c - half + 1; u <= c + half - 1; u++) {
            for (int y = base + 1; y < eave - 1; y++) {
                boolean glass = "round".equals(style) ? (u == c || y == base + (height - 1) / 2) && Math.abs(u - c) <= 1 : true;
                stamp.put(xs.applyAsInt(u, front + 1), y, zs.applyAsInt(u, front + 1), Cell.full(glass ? Role.WINDOW : Role.INFILL));
                if (!glass) continue;
                stamp.clear(xs.applyAsInt(u, front), y, zs.applyAsInt(u, front));
            }
        }
        // the cheeks: wall from the roof up to the eaves, back until the roof is as high
        for (int s : new int[]{-half, half}) {
            for (int w = front; w < front + 12; w++) {
                Roofs.Col here = col.apply(c + s, w);
                if (here == null || here.top >= eave - 1) break;
                for (int y = here.top + 1; y < eave; y++) stamp.put(xs.applyAsInt(c + s, w), y, zs.applyAsInt(c + s, w), Cell.full(Role.INFILL));
            }
        }
        // the roof: a gable from the dormer's sides up to its ridge over the middle, a block past the front and the sides, back into the main roof
        if ("shed".equals(style)) {
            for (int w = front - 1; w < front + 12; w++) {
                Roofs.Col middle = col.apply(c, w);
                if (middle == null || middle.ridge) break;
                int row = eave + Math.max(0, (w - front + 1) / 2);
                boolean any = false;
                for (int u = c - half - 1; u <= c + half + 1; u++) {
                    Roofs.Col here = col.apply(u, w);
                    if (here != null && here.top >= row) continue;
                    any = true;
                    boolean slab = (w - front + 1) % 2 == 0;
                    stamp.put(xs.applyAsInt(u, w), row, zs.applyAsInt(u, w), slab ? Cell.slab(Role.ROOF, false) : Cell.stairs(Role.ROOF, (out + 2) % 4, false));
                    if (w >= front + 2 && here != null) for (int y = here.top + 1; y < row; y++) stamp.put(xs.applyAsInt(u, w), y, zs.applyAsInt(u, w), Cell.full(Role.ROOF));
                }
                if (!any) break;
            }
        } else {
            for (int w = front - 1; w < front + 14; w++) {
                // (it runs back only until its ridge meets the roof, never over the main ridge)
                Roofs.Col middle = col.apply(c, w);
                if (middle == null || middle.ridge || w > front && middle.top >= eave - 1 + half + 1) break;
                boolean any = false;
                for (int u = c - half - 1; u <= c + half + 1; u++) {
                    int in = Math.min(u - (c - half - 1), (c + half + 1) - u);
                    int row = eave - 1 + in;
                    Roofs.Col here = col.apply(u, w);
                    if (here != null && here.top > row) continue;
                    any = true;
                    Cell cell;
                    if (u == c) cell = Cell.slab(Role.RIDGE, false);
                    else cell = Cell.stairs(Role.ROOF, u < c ? r(side, true) : r(side, false), false);
                    stamp.put(xs.applyAsInt(u, w), row, zs.applyAsInt(u, w), cell);
                    if (in > 0 && u != c) stamp.put(xs.applyAsInt(u, w), row - 1, zs.applyAsInt(u, w), Cell.full(Role.ROOF));
                    // (behind its window, it is filled down to the roof under it: it rests on the roof and joins it)
                    if (w >= front + 2 && here != null) {
                        for (int y = here.top + 1; y < row - (in > 0 && u != c ? 1 : 0); y++) stamp.put(xs.applyAsInt(u, w), y, zs.applyAsInt(u, w), Cell.full(Role.ROOF));
                    }
                }
                if (!any) break;
                // its gable over the front: the wall under its roof
                if (w == front) {
                    for (int u = c - half + 1; u <= c + half - 1; u++) {
                        int in = Math.min(u - (c - half - 1), (c + half + 1) - u);
                        for (int y = eave; y < eave - 1 + in; y++) stamp.put(xs.applyAsInt(u, front), y, zs.applyAsInt(u, front), Cell.full(Role.INFILL));
                    }
                }
            }
        }
        return stamp;
    }

    /** The way a stair on the left (or right) slope of a dormer's roof faces: up toward its middle. */
    private static int r(int side, boolean left) {
        int[] right = Area.right(side);
        int way = 0;
        for (int w = 0; w < 4; w++) if (Cell.STEP[w][0] == right[0] && Cell.STEP[w][1] == right[1]) way = w;
        return left ? way : (way + 2) % 4;
    }

    // ---- chimneys ------------------------------------------------------------------------------------------------------------------

    /**
     * A lot's chimney (spec 5/6): by the ridge (behind it, a third of the way along), at an end of the ridge, or up the outside of an end wall from the ground;
     * its top at least two blocks over the ridge, a slab round its top.
     */
    private static void chimney(Layout layout, Roofs.LotRoof roof, Roofs.Result roofs, Grid grid, Kit kit, Ground ground, List<String> notes) {
        int where = roof.geo.lot.roof.chimney;
        if (where <= 0 || roof.type == Plan.RoofType.NONE) return;
        Kit.Part part = kit.get("chimney");
        int width = part == null ? 2 : Math.max(1, Math.min(3, (int) Math.round(part.number("width", 2))));
        int above = part == null ? 2 : Math.max(2, (int) Math.round(part.number("above", 2)));
        Area top = roof.geo.top();
        int high = roof.ridgeRow + above;
        for (int attempt = 0; attempt < 5; attempt++) {
            int[] spot = chimneySpot(roof, top, where, width, attempt);
            if (spot == null) continue;
            Stamp stamp = new Stamp("chimney", EnumSet.of(Role.ROOF, Role.EAVE, Role.RIDGE, Role.FLASHING, Role.FLOOR, Role.INFILL, Role.INFILL_2, Role.INFILL_3, Role.BEAM,
                    Role.PLINTH));
            boolean outside = where >= 4;
            int from = outside ? Integer.MAX_VALUE : roof.geo.eave;
            for (int dx = 0; dx < spot[2]; dx++) {
                for (int dz = 0; dz < spot[3]; dz++) {
                    int x = spot[0] + dx;
                    int z = spot[1] + dz;
                    int low = outside ? ground.at(x, z) + 1 : from;
                    for (int y = low; y <= high; y++) stamp.put(x, y, z, Cell.full(Role.CHIMNEY));
                }
            }
            // (the cap: upside-down stairs round the top, a block out, leaning on the stack)
            for (int dx = -1; dx <= spot[2]; dx++) {
                for (int dz = -1; dz <= spot[3]; dz++) {
                    boolean in = dx >= 0 && dx < spot[2] && dz >= 0 && dz < spot[3];
                    if (in) continue;
                    int facing = dz < 0 ? 2 : dz >= spot[3] ? 0 : dx < 0 ? 1 : 3;
                    stamp.put(spot[0] + dx, high, spot[1] + dz, Cell.stairs(Role.CHIMNEY, facing, true));
                }
            }
            if (outside) {
                // (not over a window or a door of the wall behind it)
                boolean blocks = false;
                for (long key : stamp.cells.keySet()) {
                    for (int way = 0; way < 4 && !blocks; way++) {
                        Cell behind = grid.get(Grid.x(key) + Cell.STEP[way][0], Grid.y(key), Grid.z(key) + Cell.STEP[way][1]);
                        if (behind != null && (behind.role() == Role.WINDOW || behind.role() == Role.DOOR)) blocks = true;
                    }
                }
                if (blocks) continue;
            }
            if (stamp.fits(grid)) {
                stamp.apply(grid, roof.geo.index);
                return;
            }
        }
        notes.add(roof.geo.lot.name + ": 煙突を置ける所がありません");
    }

    /** Where a chimney goes: {x, z, wide in x, wide in z}; the {@code attempt}-th place tried (moved along a little each time). */
    private static int[] chimneySpot(Roofs.LotRoof roof, Area top, int where, int width, int attempt) {
        int shift = (attempt + 1) / 2 * (attempt % 2 == 1 ? 2 : -2);
        boolean alongX = top.shape != Plan.Shape.RECT || roof.ridge == 0;
        int cx = (int) Math.floor(top.centerX());
        int cz = (int) Math.floor(top.centerZ());
        if (top.shape != Plan.Shape.RECT) return new int[]{cx + 1 + shift, cz - 1, width, width};
        switch (where) {
            case 1 -> {
                // behind the ridge, a third of the way along
                if (alongX) return new int[]{top.x0 + top.width() / 3 + shift, cz - width, width, width};
                return new int[]{cx - width, top.z0 + top.depthSpan() / 3 + shift, width, width};
            }
            case 2, 3 -> {
                // on the ridge, near its left (or right) end, inside the end wall
                boolean left = where == 2;
                if (alongX) return new int[]{(left ? top.x0 + 2 : top.x1 - 1 - width) + shift / 2, cz - width / 2, width, width};
                return new int[]{cx - width / 2, (left ? top.z0 + 2 : top.z1 - 1 - width) + shift / 2, width, width};
            }
            default -> {
                // outside an end wall (left: west or north end, right: east or south), from the ground
                boolean left = where == 4;
                if (alongX) return new int[]{left ? top.x0 - 1 : top.x1 + 1, cz - width / 2 + shift, 1, width};
                return new int[]{cx - width / 2 + shift, left ? top.z0 - 1 : top.z1 + 1, width, 1};
            }
        }
    }

    // ---- finials and the special parts -------------------------------------------------------------------------------------------------

    /** A finial on the tip of a cone or a pyramid, a little post at each end of a ridge. */
    private static void finial(Roofs.LotRoof roof, Roofs.Result roofs, Grid grid, Kit kit) {
        if (!roof.geo.lot.roof.finial || roof.type == Plan.RoofType.NONE || roof.type == Plan.RoofType.FLAT) return;
        Kit.Part part = kit.get(roof.type == Plan.RoofType.CONE ? "weathervane" : "finial");
        int height = part == null ? 2 : Math.max(1, Math.min(5, (int) Math.round(part.number("height", 2))));
        List<int[]> tips = new ArrayList<>();
        int best = Integer.MIN_VALUE;
        for (Map.Entry<Long, Roofs.Col> entry : roofs.top(roof.geo.index).entrySet()) {
            Roofs.Col col = entry.getValue();
            if (col.lot != roof.geo.index || !col.ridge) continue;
            int x = (int) ((entry.getKey() >> 32) - 100_000);
            int z = (int) ((entry.getKey() & 0xFFFFFFFFL) - 100_000);
            if (col.top > best) {
                best = col.top;
                tips.clear();
            }
            if (col.top == best) tips.add(new int[]{x, z});
        }
        if (tips.isEmpty()) return;
        List<int[]> ends = new ArrayList<>();
        boolean point = roof.geo.top().shape != Plan.Shape.RECT || roof.type == Plan.RoofType.PYRAMID || tips.size() <= 2;
        if (point) {
            // (a tip: the one nearest the middle)
            double cx = roof.geo.top().centerX();
            double cz = roof.geo.top().centerZ();
            tips.sort((a, b) -> Double.compare(Math.hypot(a[0] - cx, a[1] - cz), Math.hypot(b[0] - cx, b[1] - cz)));
            ends.add(tips.get(0));
        } else {
            // (a ridge: its two ends)
            tips.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
            ends.add(tips.get(0));
            ends.add(tips.get(tips.size() - 1));
        }
        int keep = grid.owner;
        grid.owner = roof.geo.index;
        for (int[] at : ends) {
            int y = best + 1;
            while (grid.get(at[0], y, at[1]) != null) y++;
            for (int i = 0; i < height - 1; i++) grid.put(at[0], y + i, at[1], i == 0 && point ? Cell.wall(Role.FINIAL) : Cell.fence(Role.FINIAL));
            grid.put(at[0], y + height - 1, at[1], Cell.rod(Role.FINIAL));
        }
        grid.owner = keep;
    }

    /**
     * The special part of a lot (spec 5/6) at the anchor of a face: a windmill's sails on an axle out of the face, high up (the gable's middle on a gable
     * end), crossed; a bell hung under a beam out of the face near its top.
     */
    private static void special(Layout layout, Roofs.LotRoof roof, Roofs.Result roofs, Grid grid, Ground ground, List<String> notes) {
        Plan.Special special = roof.geo.lot.roof.special;
        if (special == Plan.Special.NONE) return;
        Layout.LotGeo geo = roof.geo;
        int side = geo.lot.roof.specialSide;
        Area plan = geo.top();
        int[] o = Area.out(side);
        int[] r = Area.right(side);
        int[] mid;
        if (plan.shape == Plan.Shape.RECT) {
            int length = plan.sideLength(side);
            mid = plan.along(side, (length - 1) / 2);
        } else {
            int cx = (int) Math.round(plan.centerX());
            int cz = (int) Math.round(plan.centerZ());
            mid = new int[]{cx, cz};
            while (plan.contains(mid[0] + o[0], mid[1] + o[1])) mid = new int[]{mid[0] + o[0], mid[1] + o[1]};
        }
        int anchor = geo.bottom[0] + (int) Math.round((geo.eave - geo.bottom[0]) * 0.75);
        if (roof.edges[side] == Roofs.Edge.GABLE) anchor = geo.eave + 1;
        if (special == Plan.Special.BELL) {
            Stamp stamp = new Stamp("bell", EnumSet.noneOf(Role.class));
            int row = anchor + 1;
            stamp.put(mid[0] + o[0], row, mid[1] + o[1], Cell.log(Role.BEAM, side % 2 == 0 ? 2 : 0));
            stamp.put(mid[0] + o[0], row - 1, mid[1] + o[1], Cell.bell(Role.BELL, side));
            if (stamp.fits(grid)) stamp.apply(grid, geo.index);
            else notes.add(geo.lot.name + ": 鐘を置ける所がありません");
            return;
        }
        // the sails: an axle two blocks out, four blades crossed (up, right, down, left), each a spar with its sail on one side
        int length = Math.max(4, Math.min(10, anchor - ground.at(mid[0], mid[1]) - 2));
        Stamp stamp = new Stamp("windmill", EnumSet.noneOf(Role.class));
        int hx = mid[0] + 2 * o[0];
        int hz = mid[1] + 2 * o[1];
        stamp.put(mid[0] + o[0], anchor, mid[1] + o[1], Cell.log(Role.BEAM, side % 2 == 0 ? 2 : 0));
        stamp.put(hx, anchor, hz, Cell.log(Role.BEAM, side % 2 == 0 ? 2 : 0));
        int[][] blades = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int[] blade : blades) {
            for (int i = 1; i <= length; i++) {
                int x = hx + blade[0] * i * r[0];
                int z = hz + blade[0] * i * r[1];
                int y = anchor + blade[1] * i;
                stamp.put(x, y, z, Cell.fence(Role.BEAM));
                if (i < 2) continue;
                // (the sail: two blocks wide, on the side the blade turns toward)
                for (int w = 1; w <= 2; w++) {
                    int sx = x + (blade[1] != 0 ? blade[1] * w * r[0] : 0);
                    int sz = z + (blade[1] != 0 ? blade[1] * w * r[1] : 0);
                    int sy = y - (blade[0] != 0 ? blade[0] * w : 0);
                    stamp.put(sx, sy, sz, Cell.full(Role.SAIL));
                }
            }
        }
        if (stamp.fits(grid)) stamp.apply(grid, geo.index);
        else notes.add(geo.lot.name + ": 風車の羽根を置ける所がありません");
    }
}
