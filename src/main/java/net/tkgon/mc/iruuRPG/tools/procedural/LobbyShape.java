package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.ProceduralShapes.Blocks;
import net.tkgon.mc.iruuRPG.tools.procedural.TreeSettings.Key;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The lobby of the procedural tool (t190, after the central spawns, the "hub" builds, of servers): a plaza in the middle (its paving, the border and
 * the grid of its tiles as paths, a fountain or a pedestal in the middle, raised flower beds along its edge), houses round it made by the random houses
 * ({@link HouseDesigner}: by default mostly the lobby-style house and the house of volumes) each turned to face it, a path from each door to the plaza,
 * trees of the tree tool in the gaps, and the ground filled under the plaza and the paths.
 * <p>
 * No house covers another: each takes its own sector of the circle round the plaza (as wide as it is seen from the middle, with a margin), its front as
 * far from the plaza's edge as asked; what is left of the circle is shared out between the gaps. How unlike each other the sizes of the houses, their
 * distances, their turns and the gaps are is the "organic" number (0: all alike and evenly spaced). Houses that do not fit round the plaza are left out.
 * The same seed and settings give the same lobby. Pure arithmetic: it runs off the main thread.
 */
final class LobbyShape {

    private LobbyShape() {
    }

    /** How many houses are made for each house of a lobby, the best kept (fewer than for one house alone: there are many). */
    static final int TRIES = 4;
    /** How far from the block clicked the terrain is read at most. */
    static final int MAX_REACH = 48;

    static String shapeName(int shape) {
        return switch (shape) {
            case 1 -> "八角";
            case 2 -> "四角";
            default -> "丸";
        };
    }

    static String centerName(int center) {
        return switch (center) {
            case 1 -> "噴水";
            case 2 -> "台座";
            default -> "なし";
        };
    }

    /** How far from the block clicked the lobby reaches (the terrain is read that far for the foundations and the fill). */
    static int reach(TreeSettings s) {
        return Math.min(MAX_REACH, s.getInt(Key.LOBBY_RADIUS) + s.getInt(Key.LOBBY_GAP) + 24);
    }

    /** A house of the lobby: its settings, the columns it covers as it is made (its front toward +z), how far its front is out from its middle; where it stands. */
    static final class Lot {
        final TreeSettings settings;
        final List<int[]> footprint;
        final int front;
        double distance;
        double angle;
        double left;
        double right;
        int x;
        int z;
        int rotation;

        Lot(TreeSettings settings, List<int[]> footprint, int front) {
            this.settings = settings;
            this.footprint = footprint;
            this.front = front;
        }
    }

    /** The patterns of the houses of a lobby when none is chosen, by how often: mostly the lobby-style house and the house of volumes. */
    private static HouseDesigner.Pattern lobbyPattern(Random r) {
        HouseDesigner.Pattern[] patterns = {HouseDesigner.Pattern.HUB, HouseDesigner.Pattern.COMPOUND, HouseDesigner.Pattern.TOWNHOUSE, HouseDesigner.Pattern.COTTAGE,
                HouseDesigner.Pattern.TOWER};
        int[] weights = {35, 25, 15, 15, 10};
        int roll = r.nextInt(100);
        for (int index = 0; index < patterns.length; index++) {
            roll -= weights[index];
            if (roll < 0) return patterns[index];
        }
        return HouseDesigner.Pattern.HUB;
    }

    /** The houses of the lobby, made and laid out round the plaza, none covering another (those that do not fit left out). */
    static List<Lot> layout(TreeSettings s, Random r) {
        int radius = s.getInt(Key.LOBBY_RADIUS);
        int gap = s.getInt(Key.LOBBY_GAP);
        double organic = s.get(Key.LOBBY_ORGANIC);
        int count = s.getInt(Key.LOBBY_HOUSES);
        boolean terrain = s.on(Key.LOBBY_TERRAIN);
        int chosen = s.getInt(Key.HOUSE_GEN_PATTERN);
        List<Lot> lots = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            TreeSettings rules = s.copy();
            rules.setKind(ProcKind.HOUSE);
            HouseDesigner.Pattern pattern = chosen == 0 ? lobbyPattern(r) : HouseDesigner.Pattern.of(chosen);
            rules.set(Key.HOUSE_GEN_PATTERN, pattern.ordinal());
            // (all of a size when they are to be alike; smaller and larger ones among them the more organic)
            double roll = r.nextDouble();
            rules.set(Key.HOUSE_GEN_SIZE, roll < organic * 0.3 ? 0 : roll > 1.0 - organic * 0.25 ? 2 : 1);
            rules.set(Key.HOUSE_GEN_SYMMETRY, 0);
            rules.set(Key.HOUSE_GEN_TOWERS, 0);
            rules.set(Key.HOUSE_GEN_ROOF, 1);
            rules.set(Key.HOUSE_GEN_LOOK, 1);
            TreeSettings house = HouseDesigner.design(rules, 1L + (r.nextLong() & 0xFFFFFFL), false, TRIES).settings();
            house.set(Key.HOUSE_ROTATION, 0);
            house.set(Key.HOUSE_FOUNDATION, terrain ? 1 : 0);
            Blocks probe = new Blocks();
            HouseShape.build(probe, house, null, null);
            Set<Long> columns = new HashSet<>();
            List<int[]> footprint = new ArrayList<>();
            int front = 0;
            for (long key : probe.map.keySet()) {
                int x = TreeVoxelizer.unpackX(key);
                int z = TreeVoxelizer.unpackZ(key);
                if (!columns.add(HouseShape.column(x, z))) continue;
                footprint.add(new int[]{x, z});
                front = Math.max(front, z);
            }
            lots.add(new Lot(house, footprint, front));
        }
        // the sectors: each house as far out as its front is from the plaza's edge; how wide it is seen from the middle, and half a gap each side
        double total = 0.0;
        for (Lot lot : lots) {
            lot.distance = radius + gap + lot.front + (organic > 0.0 ? r.nextInt(1 + (int) Math.round(organic * 3.0)) : 0);
            double margin = (1.5 + organic) / lot.distance;
            for (int[] point : lot.footprint) {
                // (the house turned to face the middle, from the east: its front is toward -x, its right toward -z)
                double angle = Math.atan2(point[0], lot.distance - point[1]);
                lot.right = Math.max(lot.right, angle);
                lot.left = Math.max(lot.left, -angle);
            }
            lot.left += margin;
            lot.right += margin;
            total += lot.left + lot.right;
        }
        while (total > 2.0 * Math.PI && !lots.isEmpty()) {
            Lot last = lots.remove(lots.size() - 1);
            total -= last.left + last.right;
        }
        // what is left of the circle shared out between the gaps, the more unevenly the more organic
        double slack = 2.0 * Math.PI - total;
        double[] shares = new double[lots.size()];
        double sum = 0.0;
        for (int index = 0; index < shares.length; index++) {
            shares[index] = 1.0 + organic * 0.9 * (r.nextDouble() * 2.0 - 1.0);
            sum += shares[index];
        }
        double cursor = r.nextDouble() * 2.0 * Math.PI;
        for (int index = 0; index < lots.size(); index++) {
            Lot lot = lots.get(index);
            lot.angle = cursor + lot.left;
            cursor = lot.angle + lot.right + (sum > 0.0 ? slack * shares[index] / sum : 0.0);
            lot.x = (int) Math.round(lot.distance * Math.cos(lot.angle));
            lot.z = (int) Math.round(lot.distance * Math.sin(lot.angle));
            // (turned so its front faces the middle: the front of a house made facing south, turned by the angle and a quarter)
            int jitter = (int) Math.round(organic * 8.0 * (r.nextDouble() * 2.0 - 1.0));
            lot.rotation = Math.floorMod((int) Math.round(Math.toDegrees(lot.angle)) + 90 + jitter, 360);
        }
        return lots;
    }

    /** Makes the lobby of {@code s} into {@code blocks}, its plaza round the block over the one clicked; {@code ground} for the foundations and the fill (or null). */
    static void build(Blocks blocks, TreeSettings s, long seed, TreeGround ground) {
        Random r = new Random(seed);
        int radius = s.getInt(Key.LOBBY_RADIUS);
        int shape = s.getInt(Key.LOBBY_SHAPE);
        int gap = s.getInt(Key.LOBBY_GAP);
        double organic = s.get(Key.LOBBY_ORGANIC);
        boolean terrain = s.on(Key.LOBBY_TERRAIN) && ground != null;
        Set<Long> paved = new HashSet<>();
        // the plaza: the paving, its border and the grid of its tiles as paths
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                double d = HouseShape.House.metric(shape, x, z);
                if (d > radius + 0.5) continue;
                boolean line = d > radius - 0.5 || Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
                blocks.put(x, 0, z, line ? TreePart.LOBBY_PATH : TreePart.LOBBY_PAVE);
                paved.add(HouseShape.column(x, z));
            }
        }
        middle(blocks, s.getInt(Key.LOBBY_CENTER), radius);
        // the houses, each with its foundation on the ground where it stands, and the paths from their doors
        List<Lot> lots = layout(s, r);
        Set<Long> built = new HashSet<>();
        Set<Long> ways = new HashSet<>();
        for (Lot lot : lots) {
            lot.settings.set(Key.HOUSE_ROTATION, lot.rotation);
            Blocks house = new Blocks();
            int ox = lot.x;
            int oz = lot.z;
            HouseShape.build(house, lot.settings, null, terrain ? (x, z) -> ground.top(x + ox, z + oz) : null);
            for (Map.Entry<Long, TreePart> entry : house.map.entrySet()) {
                long key = entry.getKey();
                int x = TreeVoxelizer.unpackX(key) + ox;
                int z = TreeVoxelizer.unpackZ(key) + oz;
                blocks.put(x, TreeVoxelizer.unpackY(key), z, entry.getValue());
                built.add(HouseShape.column(x, z));
            }
            if (s.on(Key.LOBBY_PATHS)) path(blocks, ways, lot, radius, shape);
        }
        paved.addAll(ways);
        beds(blocks, ways, s.getInt(Key.LOBBY_BEDS), radius, shape, organic, r);
        trees(blocks, built, paved, s.getInt(Key.LOBBY_TREES), radius, gap, lots, organic, r, terrain ? ground : null);
        // the ground under the plaza and the paths, down to where it is
        if (terrain) {
            for (long column : paved) {
                int x = (int) (column >> 32);
                int z = (int) column;
                int top = ground.top(x, z);
                if (top == TreeGround.NONE) continue;
                for (int y = -1; y > top && y >= -10; y--) blocks.put(x, y, z, TreePart.HOUSE_STONE);
            }
        }
    }

    /** The middle of the plaza: a fountain (a round basin, a column, a small bowl on it) or a pedestal (stepped, a block on top), or nothing. */
    private static void middle(Blocks blocks, int center, int radius) {
        switch (center) {
            case 1 -> {
                int basin = radius >= 10 ? 3 : 2;
                for (int x = -basin - 1; x <= basin + 1; x++) {
                    for (int z = -basin - 1; z <= basin + 1; z++) {
                        double d = Math.hypot(x, z);
                        if (d > basin + 0.5) continue;
                        blocks.put(x, 1, z, d > basin - 0.5 ? TreePart.LOBBY_BED : TreePart.LOBBY_WATER);
                    }
                }
                blocks.put(0, 1, 0, TreePart.LOBBY_BED);
                blocks.put(0, 2, 0, TreePart.LOBBY_BED);
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) blocks.put(x, 3, z, x == 0 && z == 0 ? TreePart.LOBBY_WATER : TreePart.LOBBY_BED);
                }
            }
            case 2 -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        blocks.put(x, 1, z, TreePart.LOBBY_BED);
                        if (Math.abs(x) <= 1 && Math.abs(z) <= 1) blocks.put(x, 2, z, TreePart.LOBBY_BED);
                    }
                }
                blocks.put(0, 3, 0, TreePart.LOBBY_BED);
                blocks.put(0, 4, 0, TreePart.LOBBY_BED);
            }
            default -> {
            }
        }
    }

    /** A path 3 wide from the door of a house (one out in front of it) straight toward the middle, until it is on the plaza. */
    private static void path(Blocks blocks, Set<Long> ways, Lot lot, int radius, int shape) {
        HouseShape.House house = new HouseShape.House(lot.settings);
        if (!house.door) return;
        double lx = house.plan(0).doorAt() - (house.width - 1) / 2;
        double lz = (house.depth - 1) - (house.depth - 1) / 2 + 1;
        double turn = Math.toRadians(lot.rotation);
        double sx = lot.x + lx * Math.cos(turn) - lz * Math.sin(turn);
        double sz = lot.z + lx * Math.sin(turn) + lz * Math.cos(turn);
        double length = Math.hypot(sx, sz);
        if (length < 1.0) return;
        double ux = -sx / length;
        double uz = -sz / length;
        for (double t = 0.0; t <= length; t += 0.5) {
            double px = sx + ux * t;
            double pz = sz + uz * t;
            if (HouseShape.House.metric(shape, px, pz) <= radius - 0.5) break;
            for (int side = -1; side <= 1; side++) {
                int x = (int) Math.round(px - uz * side);
                int z = (int) Math.round(pz + ux * side);
                blocks.put(x, 0, z, TreePart.LOBBY_PATH);
                ways.add(HouseShape.column(x, z));
            }
        }
    }

    /** Raised flower beds along the edge of the plaza, evenly round it (the more organic the less evenly), 5 along the edge and 3 deep; none on a path to a door. */
    private static void beds(Blocks blocks, Set<Long> ways, int count, int radius, int shape, double organic, Random r) {
        if (count <= 0 || radius < 7) return;
        double start = r.nextDouble() * 2.0 * Math.PI;
        for (int index = 0; index < count; index++) {
            double angle = start + (index + 0.5) * 2.0 * Math.PI / count + organic * (r.nextDouble() - 0.5) * Math.PI / count;
            double cx = (radius - 2.5) * Math.cos(angle);
            double cz = (radius - 2.5) * Math.sin(angle);
            Set<Long> cells = new HashSet<>();
            boolean clear = true;
            for (int x = (int) Math.floor(cx) - 4; x <= (int) Math.ceil(cx) + 4; x++) {
                for (int z = (int) Math.floor(cz) - 4; z <= (int) Math.ceil(cz) + 4; z++) {
                    double dx = x - cx;
                    double dz = z - cz;
                    double t = -dx * Math.sin(angle) + dz * Math.cos(angle);
                    double n = dx * Math.cos(angle) + dz * Math.sin(angle);
                    if (Math.abs(t) > 2.5 || Math.abs(n) > 1.5) continue;
                    if (ways.contains(HouseShape.column(x, z)) || HouseShape.House.metric(shape, x, z) > radius - 0.5) clear = false;
                    cells.add(HouseShape.column(x, z));
                }
            }
            if (!clear) continue;
            for (long column : cells) {
                int x = (int) (column >> 32);
                int z = (int) column;
                boolean rim = !cells.contains(HouseShape.column(x + 1, z)) || !cells.contains(HouseShape.column(x - 1, z)) || !cells.contains(HouseShape.column(x, z + 1))
                        || !cells.contains(HouseShape.column(x, z - 1));
                blocks.put(x, 1, z, rim ? TreePart.LOBBY_BED : TreePart.LOBBY_SOIL);
            }
        }
    }

    /**
     * Trees of the tree tool (round crowns, some conifers; the more organic the more unlike): first in the middle of each gap between two houses, then
     * anywhere round the houses (t239: out to a little past the farthest of them, so deep houses leave room); not within 4 of a house, 3 of the plaza or
     * a path, 5 of another tree. On the ground where it is known.
     */
    private static void trees(Blocks blocks, Set<Long> built, Set<Long> paved, int count, int radius, int gap, List<Lot> lots, double organic, Random r, TreeGround ground) {
        if (count <= 0) return;
        List<double[]> spots = new ArrayList<>();
        for (int index = 0; index < lots.size(); index++) {
            Lot one = lots.get(index);
            Lot next = lots.get((index + 1) % lots.size());
            double from = one.angle + one.right;
            double to = next.angle - next.left;
            while (to < from) to += 2.0 * Math.PI;
            double angle = (from + to) / 2.0;
            double distance = radius + gap + 2 + r.nextInt(4);
            spots.add(new double[]{distance * Math.cos(angle), distance * Math.sin(angle)});
        }
        double far = 0.0;
        for (long column : built) far = Math.max(far, Math.hypot((int) (column >> 32), (int) column));
        // (not past where the ground is read)
        double reach = Math.min(MAX_REACH - 3.0 - radius - gap, Math.max(14.0, far + 6.0 - radius - gap));
        for (int extra = 0; extra < count * 4; extra++) {
            double angle = r.nextDouble() * 2.0 * Math.PI;
            double distance = radius + gap + 1 + r.nextDouble() * reach;
            spots.add(new double[]{distance * Math.cos(angle), distance * Math.sin(angle)});
        }
        Set<Long> trunks = new HashSet<>();
        int planted = 0;
        for (double[] spot : spots) {
            if (planted >= count) break;
            int x = (int) Math.round(spot[0]);
            int z = (int) Math.round(spot[1]);
            if (near(built, x, z, 4) || near(paved, x, z, 3) || near(trunks, x, z, 5)) continue;
            Map<Long, TreePart> tree = TreeGenerator.build(tree(r, organic), r.nextLong(), null);
            int top = ground == null ? TreeGround.NONE : ground.top(x, z);
            int baseY = top == TreeGround.NONE ? 0 : top + 1;
            for (Map.Entry<Long, TreePart> entry : tree.entrySet()) {
                long key = entry.getKey();
                blocks.put(TreeVoxelizer.unpackX(key) + x, TreeVoxelizer.unpackY(key) + baseY, TreeVoxelizer.unpackZ(key) + z, entry.getValue());
            }
            trunks.add(HouseShape.column(x, z));
            planted++;
        }
    }

    /** Whether a column of {@code columns} is within {@code reach} of {@code (x, z)} (each way). */
    private static boolean near(Set<Long> columns, int x, int z, int reach) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (columns.contains(HouseShape.column(x + dx, z + dz))) return true;
            }
        }
        return false;
    }

    /** The settings of a small tree for a lobby: a round crown (most), or a conifer. */
    private static TreeSettings tree(Random r, double organic) {
        TreeSettings tree = new TreeSettings();
        if (r.nextDouble() < 0.7) {
            tree.setAlgorithm("canopy");
            tree.set(Key.CANOPY_TRUNK_HEIGHT, 2 + r.nextInt(3));
            tree.set(Key.CANOPY_LENGTH, 4.5 + r.nextDouble() * (1.0 + 2.0 * organic));
            tree.set(Key.CANOPY_RADIUS, 0.8 + r.nextDouble() * 0.3);
            tree.set(Key.CANOPY_DEPTH, 3);
            tree.set(Key.CANOPY_BRANCHES, 3);
            tree.set(Key.CANOPY_LEAF_SIZE, 1.75 + r.nextDouble() * 0.5);
            tree.set(Key.CANOPY_ROOT_COUNT, 3);
            tree.set(Key.CANOPY_ROOT_LENGTH, 2.5);
        } else {
            tree.setAlgorithm("tiered");
            tree.set(Key.TIER_HEIGHT, 11 + r.nextInt(5));
            tree.set(Key.TIER_STUMP, 3);
            tree.set(Key.TIER_RADIUS, 0.8);
            tree.set(Key.TIER_SPACING, 3);
            tree.set(Key.TIER_BRANCHES, 5);
            tree.set(Key.TIER_BRANCH_LENGTH, 3.0 + r.nextDouble());
            tree.set(Key.TIER_LEAF_SIZE, 1.5);
        }
        return tree;
    }
}
