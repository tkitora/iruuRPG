package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.ProceduralShapes.Blocks;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The house base of the procedural tool (t179; t181 the turn, the fantasy roofs and the parts added on; t190 the depth and layers of walls and roofs,
 * volumes, stairs, the shapes of towers), a block-out in the parts' wool after the
 * building generators of Houdini. The house is made in its own coordinates ({@code a} along the front from its left, {@code b} from the back to the
 * front, y up from the block over the one clicked): first the base from its numbers (the storeys with their floors, walls, pillars and openings, a
 * timber frame and a stone ground floor when asked, the upper storeys jettied out, the roof), then every part of the list of {@link HouseModule} on it
 * in turn (a part may open what is under it, so its inside joins the house); then the whole is set in the world with its front toward the player,
 * turned on by the rotation. The base is never changed by the parts: they are made again from the list every time (non-destructive). Pure
 * arithmetic: it runs off the main thread.
 */
final class HouseShape {

    private HouseShape() {
    }

    /** The names of the roofs ({@link TreeSettings.Key#HOUSE_ROOF}). */
    static String roofName(int roof) {
        return switch (roof) {
            case 0 -> "平ら";
            case 2 -> "寄棟";
            case 3 -> "片流れ";
            case 4 -> "腰折れ";
            case 5 -> "マンサード";
            case 6 -> "入母屋";
            case 7 -> "半切妻";
            case 8 -> "反り屋根";
            case 9 -> "尖り屋根";
            case 10 -> "尖り寄棟";
            default -> "切妻";
        };
    }

    /** A section of a roof of steps that can be chosen from the menu (t185), and that the random houses use. */
    record StepPreset(String label, List<Integer> steps) {
    }

    static final List<StepPreset> STEP_PRESETS = List.of(
            new StepPreset("定番の尖り", List.of(1, 1, 1, 2, 3, 3)),
            new StepPreset("鋭い尖り", List.of(1, 1, 2, 2, 3, 4)),
            new StepPreset("裾の長い反り", List.of(1, 1, 1, 1, 2, 3)),
            new StepPreset("垂れた軒", List.of(2, 1, 1, 2, 3, 3)),
            new StepPreset("塔のような急勾配", List.of(1, 2, 3, 4, 5)),
            new StepPreset("ゆるい反り", List.of(1, 1, 1, 1, 1, 2, 2)));

    /** The names of the ways the ridge runs ({@link TreeSettings.Key#HOUSE_RIDGE}). */
    static String ridgeName(int ridge) {
        return switch (ridge) {
            case 1 -> "正面と平行";
            case 2 -> "正面と直角";
            default -> "自動";
        };
    }

    /** Makes the house of {@code s} into {@code blocks}: the base, its parts, the foundation down to {@code ground} (when asked and known), set in the world. */
    static void build(Blocks blocks, TreeSettings s, Vector aim, TreeGround ground) {
        build(blocks, s, aim, ground, -1, null);
    }

    /**
     * {@link #build(Blocks, TreeSettings, Vector, TreeGround)} for the preview (t246): the blocks of part {@code highlight} (its place in the list; -1 none)
     * become {@link TreePart#HOUSE_SELECTED}, and {@code owners} (when not null) is given the part each block of the house is of, by the house's own
     * coordinates (as {@link TreeVoxelizer#pack}, lifted with the podium; the base's blocks are not in it).
     */
    static void build(Blocks blocks, TreeSettings s, Vector aim, TreeGround ground, int highlight, Map<Long, Integer> owners) {
        House house = new House(s);
        Blocks local = new Blocks();
        house.body(local);
        Map<Long, Integer> ranks = new HashMap<>();
        Map<Long, Integer> mine = owners == null && highlight < 0 ? null : new HashMap<>();
        for (int index = 0; index < s.modules().size(); index++) {
            HouseModule module = s.modules().get(index);
            if (!module.on) continue;
            Build build = house.module(local, ranks, module);
            if (mine == null) continue;
            // (a block is the part's that put it last, as it stands after the ranks; what a later part took away is nobody's)
            for (long key : build.carved) mine.remove(key);
            for (Map.Entry<Long, TreePart> entry : build.over.map.entrySet()) {
                if (local.map.get(entry.getKey()) == entry.getValue()) mine.put(entry.getKey(), index);
            }
        }
        if (mine != null && highlight >= 0) {
            for (Map.Entry<Long, Integer> entry : mine.entrySet()) {
                if (entry.getValue() == highlight && local.map.containsKey(entry.getKey())) local.map.put(entry.getKey(), TreePart.HOUSE_SELECTED);
            }
        }
        Blocks before = local;
        local = house.podium(local);
        if (owners != null) {
            // (the podium lifts the whole house when it is there)
            int lift = local == before ? 0 : house.raise;
            for (Map.Entry<Long, Integer> entry : mine.entrySet()) {
                long key = entry.getKey();
                owners.put(TreeVoxelizer.pack(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key) + lift, TreeVoxelizer.unpackZ(key)), entry.getValue());
            }
        }
        Placement placement = Placement.of(aim, s.getInt(TreeSettings.Key.HOUSE_ROTATION), house.width, house.depth);
        if (s.on(TreeSettings.Key.HOUSE_FOUNDATION) && ground != null) foundation(local, placement, ground);
        placement.place(local, blocks, house.thick());
    }

    /** How far from the block clicked the terrain must be read for the foundation of this house (its footprint, the eaves and the parts). */
    static int reach(TreeSettings s) {
        double width = s.getInt(TreeSettings.Key.HOUSE_WIDTH);
        double depth = s.getInt(TreeSettings.Key.HOUSE_DEPTH) + 6;
        double far = Math.hypot(width / 2.0, depth / 2.0);
        for (HouseModule module : s.modules()) {
            double out = Math.max(width, depth) / 2.0 + module.depth + Math.max(0, module.offset) + module.width / 2.0;
            far = Math.max(far, Math.hypot(out, Math.abs(module.along) + module.width / 2.0));
        }
        return Math.min(32, (int) Math.ceil(far) + s.getInt(TreeSettings.Key.HOUSE_OVERHANG) + s.getInt(TreeSettings.Key.HOUSE_TERRACE) + s.getInt(TreeSettings.Key.HOUSE_RAISE) + 2);
    }

    // ---- the house in its own coordinates -----------------------------------------------------------------------------------------------

    /** The numbers of one house and the making of its base and its parts. */
    static final class House {
        final int width;
        final int depth;
        final int floors;
        final int storey;
        final int spacing;
        final int roof;
        final int ridge;
        final int overhang;
        final int jetty;
        final double pitch;
        final boolean windows;
        final boolean door;
        final boolean stone;
        final boolean timber;
        /** The pillars are counted from both corners of a wall (t185): a house the same both ways. */
        final boolean mirror;
        /** The section of a roof of steps (9, 10; t185). */
        final Steps steps;
        /** (t190) the jetty on all four sides, brackets under it, the walls set back behind the frame, glass in the windows, a stone plinth, roofs between the storeys, a ridge beam. */
        final boolean jettySides;
        final boolean brackets;
        final boolean recess;
        final boolean glass;
        final int plinth;
        final int skirt;
        final boolean ridgeBeam;
        /** (t195) the height of the podium the house stands on, how far its terrace reaches out past the walls. */
        final int raise;
        final int terrace;
        /** (t195) What stands on the ground, by column (its floor over the house's), the terraces round the volumes, the doors: for the podium. */
        private final Map<Long, Integer> standing = new HashMap<>();
        private final List<Map<Long, Integer>> aprons = new ArrayList<>();
        private final List<Integer> apronFloors = new ArrayList<>();
        private final List<int[]> doors = new ArrayList<>();
        /** The height of the eaves: the top of the walls of the last storey. */
        final int top;
        /** The base: the box of the house's own storeys and roof. */
        final Box base;
        /** The highest block of each column of the base (by {@link #column}), once it is made: what is "in the house" for a volume. */
        private Map<Long, Integer> baseTops = Map.of();

        House(TreeSettings s) {
            width = s.getInt(TreeSettings.Key.HOUSE_WIDTH);
            depth = s.getInt(TreeSettings.Key.HOUSE_DEPTH);
            floors = s.getInt(TreeSettings.Key.HOUSE_FLOORS);
            storey = s.getInt(TreeSettings.Key.HOUSE_STOREY);
            spacing = s.getInt(TreeSettings.Key.HOUSE_PILLARS);
            roof = s.getInt(TreeSettings.Key.HOUSE_ROOF);
            ridge = s.getInt(TreeSettings.Key.HOUSE_RIDGE);
            overhang = s.getInt(TreeSettings.Key.HOUSE_OVERHANG);
            jetty = s.getInt(TreeSettings.Key.HOUSE_JETTY);
            pitch = s.get(TreeSettings.Key.HOUSE_PITCH);
            windows = s.on(TreeSettings.Key.HOUSE_WINDOWS);
            door = s.on(TreeSettings.Key.HOUSE_DOOR);
            stone = s.on(TreeSettings.Key.HOUSE_STONE);
            timber = s.on(TreeSettings.Key.HOUSE_TIMBER);
            mirror = s.on(TreeSettings.Key.HOUSE_PILLAR_MIRROR);
            steps = Steps.of(s.roofSteps(), s.on(TreeSettings.Key.HOUSE_STEP_FIT));
            jettySides = s.on(TreeSettings.Key.HOUSE_JETTY_SIDES);
            brackets = s.on(TreeSettings.Key.HOUSE_BRACKETS);
            recess = s.on(TreeSettings.Key.HOUSE_RECESS);
            glass = s.on(TreeSettings.Key.HOUSE_GLASS);
            plinth = s.getInt(TreeSettings.Key.HOUSE_PLINTH);
            skirt = s.getInt(TreeSettings.Key.HOUSE_SKIRT);
            ridgeBeam = s.on(TreeSettings.Key.HOUSE_RIDGE_BEAM);
            raise = s.getInt(TreeSettings.Key.HOUSE_RAISE);
            terrace = s.getInt(TreeSettings.Key.HOUSE_TERRACE);
            top = floors * storey;
            base = new Box(width, depth, floors, roof, ridge, jetty, door, stone, true);
        }

        /** How thick the roof is (its steepest slope with no holes). */
        int thick() {
            return roofThickness(roof, pitch, steps);
        }

        /** Whether the roof is one of steps (its section from {@link #steps}). */
        boolean stepped() {
            return roof == 9 || roof == 10;
        }

        /** How far storey {@code k} (0 = the ground) of the base reaches out past its ground storey at the front and at the back (the jetty, at most 3). */
        int out(int k) {
            return base.out(k);
        }

        /** How far storey {@code k} of the base reaches out at the sides (with the jetty on all four sides; else 0). */
        int outA(int k) {
            return base.outA(k);
        }

        /** The plan of storey {@code k} of the base. */
        HousePlan plan(int k) {
            return base.plan(k);
        }

        /** The base, into {@code out}; what of it is "in the house" is kept for the volumes. */
        void body(Blocks out) {
            base.build(out);
            baseTops = Build.tops(out);
        }

        /**
         * Whether the house's block {@code (a, y, b)} is in the base: inside (or on) the walls of the storey at that height, or under its roof (inside its
         * walls, below the top of the roof there).
         */
        boolean inside(int a, int b, int y) {
            if (y < 0) return false;
            if (y < top) {
                int k = y / storey;
                return a >= -outA(k) && a <= width - 1 + outA(k) && b >= -out(k) && b <= depth - 1 + out(k);
            }
            // (within the walls only, not under the eaves out past them: a volume's wall there is put, so it has no hole under the eaves)
            Integer high = baseTops.get(column(a, b));
            int e = out(floors - 1);
            int eA = outA(floors - 1);
            return high != null && y < high && a >= -eA && a <= width - 1 + eA && b >= -e && b <= depth - 1 + e;
        }

        /**
         * A box of storeys under a roof (t190): the base of the house, or a volume added on it. Each storey a floor at its foot, walls round its edge (with
         * the door and the windows, glass set back in them; the walls set back behind the frame; a beam at the foot and braces between the pillars with a
         * timber frame; stone on the ground with a stone base), pillars at the corners and along the walls; the upper storeys jettied out (on all four sides
         * when asked) on brackets; roofs between the storeys; a stone plinth round the ground storey; on top the roof out past the walls by the overhang,
         * with a ridge beam, the floor under it and the walls up to it.
         */
        final class Box {
            final int width;
            final int depth;
            final int floors;
            final int roof;
            final int ridge;
            final int jetty;
            final boolean door;
            final boolean stoneGround;
            final boolean ground;
            final int top;

            Box(int width, int depth, int floors, int roof, int ridge, int jetty, boolean door, boolean stoneGround, boolean ground) {
                this.width = width;
                this.depth = depth;
                this.floors = floors;
                this.roof = roof;
                this.ridge = ridge;
                this.jetty = jetty;
                this.door = door;
                this.stoneGround = stoneGround;
                this.ground = ground;
                top = floors * storey;
            }

            int out(int k) {
                return Math.min(3, Math.max(0, k) * jetty);
            }

            int outA(int k) {
                return jettySides ? out(k) : 0;
            }

            /** The plan of storey {@code k}: as wide and as deep as the ground storey and its jetty both ways; the door only on the ground. */
            HousePlan plan(int k) {
                return new HousePlan(width + 2 * outA(k), depth + 2 * out(k), storey, spacing, windows, door && k == 0, mirror);
            }

            /** Whether {@code (a, b)} (in the box) is a corner of storey {@code k}. */
            boolean corner(int a, int b, int k) {
                int e = out(k);
                int eA = outA(k);
                return (a == -eA || a == width - 1 + eA) && (b == -e || b == depth - 1 + e);
            }

            void build(Blocks out) {
                for (int k = 0; k < floors; k++) {
                    HousePlan plan = plan(k);
                    int e = out(k);
                    int eA = outA(k);
                    for (int pa = 0; pa < plan.width(); pa++) {
                        for (int pb = 0; pb < plan.depth(); pb++) {
                            boolean edge = pa == 0 || pb == 0 || pa == plan.width() - 1 || pb == plan.depth() - 1;
                            for (int level = 0; level < storey; level++) {
                                int y = k * storey + level;
                                TreePart part = cell(plan, pa, pb, level, edge, stoneGround && k == 0);
                                if (part == null) {
                                    // (a window's glass one block in from the face of the wall)
                                    if (glass && edge && plan.window(pa, pb, level)) {
                                        int[] in = inward(plan, pa, pb);
                                        out.put(pa - eA + in[0], y, pb - e + in[1], TreePart.HOUSE_WINDOW);
                                    }
                                    continue;
                                }
                                if (recess && edge && level > 0 && part == TreePart.HOUSE_WALL) {
                                    int[] in = inward(plan, pa, pb);
                                    out.put(pa - eA + in[0], y, pb - e + in[1], part);
                                } else {
                                    out.put(pa - eA, y, pb - e, part);
                                }
                            }
                        }
                    }
                    if (k > 0 && brackets) brackets(out, k);
                }
                int e = out(floors - 1);
                int eA = outA(floors - 1);
                HousePlan plan = plan(floors - 1);
                int lowA = -eA - overhang;
                int lowB = -e - overhang;
                int highA = width - 1 + eA + overhang;
                int highB = depth - 1 + e + overhang;
                int[][] bottom = HouseShape.roof(out::put, roof, ridge, pitch, steps, top, lowA, highA, lowB, highB);
                if (ridgeBeam) HouseShape.ridgeBeam(out::put, roof, ridge, pitch, steps, top, lowA, highA, lowB, highB);
                if (roof != 0) {
                    boolean stoneTop = stoneGround && floors == 1;
                    for (int pa = 0; pa < plan.width(); pa++) {
                        for (int pb = 0; pb < plan.depth(); pb++) {
                            boolean edge = pa == 0 || pb == 0 || pa == plan.width() - 1 || pb == plan.depth() - 1;
                            int a = pa - eA;
                            int b = pb - e;
                            // the floor of the storey that holds up the roof (a flat roof is that floor itself), where the roof is not down on it
                            if (!edge) {
                                if (!out.has(a, top, b)) out.put(a, top, b, TreePart.HOUSE_FLOOR);
                                continue;
                            }
                            // the gaps under the roof over the walls (the gables, the high side of a shed) are walled up; the pillars go on up
                            boolean pillar = plan.pillar(pa, pb);
                            int[] in = inward(plan, pa, pb);
                            for (int y = top; y < top + bottom[a - lowA][b - lowB]; y++) {
                                TreePart part = stoneTop ? TreePart.HOUSE_STONE : pillar ? TreePart.HOUSE_PILLAR : timber && y == top ? TreePart.HOUSE_BEAM : TreePart.HOUSE_WALL;
                                // (set back over the line of the ceiling, as the walls of a storey are over its sill)
                                if (recess && part == TreePart.HOUSE_WALL && y > top) out.put(a + in[0], y, b + in[1], part);
                                else out.put(a, y, b, part);
                            }
                        }
                    }
                }
                skirts(out);
                if (ground) plinth(out);
            }

            /** The brackets under the storey {@code k} where it juts out past the one under it: from the pillars below, up and out to its floor. */
            private void brackets(Blocks out, int k) {
                HousePlan below = plan(k - 1);
                int e = out(k - 1);
                int eA = outA(k - 1);
                int j = out(k) - e;
                int jA = outA(k) - eA;
                int floor = k * storey;
                for (int s = 1; s <= j; s++) {
                    for (int pa = 0; pa < below.width(); pa++) {
                        if (!below.pillar(pa, 0)) continue;
                        out.put(pa - eA, floor - 1 - (j - s), depth - 1 + e + s, TreePart.HOUSE_BEAM);
                        out.put(pa - eA, floor - 1 - (j - s), -e - s, TreePart.HOUSE_BEAM);
                    }
                }
                for (int s = 1; s <= jA; s++) {
                    for (int pb = 0; pb < below.depth(); pb++) {
                        if (!below.pillar(0, pb)) continue;
                        out.put(-eA - s, floor - 1 - (jA - s), pb - e, TreePart.HOUSE_BEAM);
                        out.put(width - 1 + eA + s, floor - 1 - (jA - s), pb - e, TreePart.HOUSE_BEAM);
                    }
                }
            }

            /**
             * The roofs between the storeys: at the foot of each upper storey, out from the walls of the one under it by {@link #skirt}, one lower each block
             * out (the corners hipped); not on a side the upper storey juts out on.
             */
            private void skirts(Blocks out) {
                if (skirt <= 0) return;
                for (int k = 1; k < floors; k++) {
                    int floor = k * storey;
                    boolean frontBack = out(k) == out(k - 1);
                    boolean sides = outA(k) == outA(k - 1);
                    int lowA = -outA(k - 1);
                    int highA = width - 1 + outA(k - 1);
                    int lowB = -out(k - 1);
                    int highB = depth - 1 + out(k - 1);
                    for (int a = lowA - skirt; a <= highA + skirt; a++) {
                        for (int b = lowB - skirt; b <= highB + skirt; b++) {
                            int da = a < lowA ? lowA - a : a > highA ? a - highA : 0;
                            int db = b < lowB ? lowB - b : b > highB ? b - highB : 0;
                            if (da == 0 && db == 0 || da > 0 && !sides || db > 0 && !frontBack) continue;
                            int y = floor - (Math.max(da, db) - 1);
                            if (!out.has(a, y, b)) out.put(a, y, b, TreePart.HOUSE_ROOF);
                        }
                    }
                }
            }

            /** The stone plinth: one block out round the ground storey, {@link #plinth} high (the way in at the door left open over its foot). */
            private void plinth(Blocks out) {
                if (plinth <= 0) return;
                int doorAt = door ? plan(0).doorAt() : Integer.MIN_VALUE;
                for (int a = -1; a <= width; a++) {
                    for (int b = -1; b <= depth; b++) {
                        if (a >= 0 && a < width && b >= 0 && b < depth) continue;
                        for (int y = 0; y < plinth; y++) {
                            if (y > 0 && a == doorAt && b == depth) continue;
                            if (!out.has(a, y, b)) out.put(a, y, b, TreePart.HOUSE_STONE);
                        }
                    }
                }
            }
        }

        /** One block in from the edge of a plan at {@code (a, b)}, toward the inside: {da, db}. */
        private static int[] inward(HousePlan plan, int a, int b) {
            if (b == plan.depth() - 1) return new int[]{0, -1};
            if (b == 0) return new int[]{0, 1};
            if (a == 0) return new int[]{1, 0};
            if (a == plan.width() - 1) return new int[]{-1, 0};
            return new int[]{0, 0};
        }

        /** The block of a storey at {@code (a, b)} of its plan, {@code level} over its floor; null for none (inside, or an opening). */
        TreePart cell(HousePlan plan, int a, int b, int level, boolean edge, boolean stoneStorey) {
            if (plan.pillar(a, b)) return stoneStorey ? TreePart.HOUSE_STONE : TreePart.HOUSE_PILLAR;
            if (!edge) return level == 0 ? TreePart.HOUSE_FLOOR : null;
            if (plan.opening(a, b, level)) return null;
            if (stoneStorey) return TreePart.HOUSE_STONE;
            if (timber && (level == 0 || brace(plan, a, b, level))) return TreePart.HOUSE_BEAM;
            return TreePart.HOUSE_WALL;
        }

        /** Whether a wall block is on a brace of the timber frame: two diagonals from the foot of the pillars either side of its stretch of wall, up toward the middle. */
        private static boolean brace(HousePlan plan, int a, int b, int level) {
            int[] panel = plan.panel(a, b);
            if (panel == null) return false;
            int inside = panel[1] - panel[0] - 1;
            if (inside < 2) return false;
            int place = panel[2] - panel[0];
            return place <= (inside + 1) / 2 ? place == level : inside + 1 - place == level;
        }

        /** Side {@code index} of storey {@code k} as a frame for a part (0 front, 1 right, 2 back, 3 left; right and left as seen from the front). */
        Side side(int index, int k) {
            int storeyK = Math.max(0, Math.min(floors - 1, k));
            int e = out(storeyK);
            int eA = outA(storeyK);
            return switch (Math.floorMod(index, 4)) {
                case 1 -> new Side(1, width - 1 + eA, (depth - 1) / 2);
                case 2 -> new Side(2, -e, (width - 1) / 2);
                case 3 -> new Side(3, -eA, (depth - 1) / 2);
                default -> new Side(0, depth - 1 + e, (width - 1) / 2);
            };
        }

        /** Half the house across from {@code side} to the ridge (how far back the roof of a wing on that side reaches into the house's). */
        int halfAcross(int side) {
            return side % 2 == 0 ? (depth - 1) / 2 + out(floors - 1) : (width - 1) / 2 + outA(floors - 1);
        }

        /** Builds one part on what is there ({@code out}: the base and the parts before it; {@code ranks}: the rank of the part each of their blocks is of). */
        Build module(Blocks out, Map<Long, Integer> ranks, HouseModule m) {
            Build build = prepare(out, Build.tops(out), m);
            build.apply(ranks, rank(m.type));
            // (what of it stands on the ground: its lowest layer, for the podium; a volume's terrace and door)
            if (build.floor != Integer.MIN_VALUE) {
                for (long key : build.over.map.keySet()) {
                    if (TreeVoxelizer.unpackY(key) == build.floor) standing.merge(column(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackZ(key)), build.floor, Math::max);
                }
            }
            if (!build.apron.isEmpty()) {
                aprons.add(build.apron);
                apronFloors.add(build.floor);
            }
            doors.addAll(build.doors);
            return build;
        }

        /**
         * (t195) The foundations. Everything is lifted by the height of the house's podium; under all that stands on the ground (the house, the volumes
         * at their own floors, the towers, the wings, the porch, the chimneys and the stairs) stone up to its floor; round the house and each volume on the
         * ground its terrace (each column of a terrace as high as the nearest of them: where two meet at different heights, a step), a low parapet on its
         * edge where it is 2 or more over what is beside it; from each door over the ground, stairs down: on from the door over what is there (a terrace, a
         * porch) to its edge, then one lower each block to the ground. Without a podium and with all floors on the ground nothing changes.
         */
        Blocks podium(Blocks local) {
            int lift = raise;
            // the base, its terrace
            for (int a = 0; a < width; a++) {
                for (int b = 0; b < depth; b++) standing.merge(column(a, b), 0, Math::max);
            }
            Map<Long, Integer> baseApron = new HashMap<>();
            for (int a = -terrace; a < width + terrace; a++) {
                for (int b = -terrace; b < depth + terrace; b++) {
                    int ring = Math.max(Math.max(-a, a - width + 1), Math.max(-b, b - depth + 1));
                    if (ring > 0) baseApron.put(column(a, b), ring);
                }
            }
            if (terrace > 0) {
                aprons.add(0, baseApron);
                apronFloors.add(0, 0);
            }
            if (door) doors.add(0, new int[]{plan(0).doorAt(), depth - 1, 0, 0, 1});
            boolean flat = lift == 0;
            for (int floor : standing.values()) flat &= floor <= 0;
            if (flat) return local;
            Blocks lifted = new Blocks();
            for (Map.Entry<Long, TreePart> entry : local.map.entrySet()) {
                long key = entry.getKey();
                lifted.map.put(TreeVoxelizer.pack(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key) + lift, TreeVoxelizer.unpackZ(key)), entry.getValue());
            }
            // under what stands: stone up to its floor
            for (Map.Entry<Long, Integer> entry : standing.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = (int) (long) entry.getKey();
                for (int y = 0; y < lift + entry.getValue(); y++) {
                    if (!lifted.has(x, y, z)) lifted.put(x, y, z, TreePart.HOUSE_STONE);
                }
            }
            // the terraces: each column to the floor of the nearest (of two as near, the lower)
            Map<Long, Integer> tops = new HashMap<>();
            Map<Long, Integer> nearest = new HashMap<>();
            for (int index = 0; index < aprons.size(); index++) {
                int top = lift + apronFloors.get(index);
                for (Map.Entry<Long, Integer> entry : aprons.get(index).entrySet()) {
                    long column = entry.getKey();
                    if (standing.containsKey(column)) continue;
                    Integer near = nearest.get(column);
                    if (near == null || entry.getValue() < near || entry.getValue().equals(near) && top < tops.get(column)) {
                        nearest.put(column, entry.getValue());
                        tops.put(column, top);
                    }
                }
            }
            for (Map.Entry<Long, Integer> entry : tops.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = (int) (long) entry.getKey();
                for (int y = 0; y <= entry.getValue(); y++) {
                    if (!lifted.has(x, y, z)) lifted.put(x, y, z, TreePart.HOUSE_STONE);
                }
            }
            // stairs from the doors over the ground
            Set<Long> flights = new HashSet<>();
            for (int[] at : doors) {
                int floor = lift + at[2];
                if (floor <= 0) continue;
                int t = 1;
                while (t < 16 && lifted.has(at[0] + at[3] * t, floor, at[1] + at[4] * t)) t++;
                for (int k = 0; floor - 1 - k >= 0; k++) {
                    int x = at[0] + at[3] * (t + k);
                    int z = at[1] + at[4] * (t + k);
                    for (int side = -1; side <= 1; side++) {
                        int sx = x + at[4] * side;
                        int sz = z + at[3] * side;
                        for (int y = 0; y <= floor - 1 - k; y++) {
                            if (!lifted.has(sx, y, sz)) lifted.put(sx, y, sz, TreePart.HOUSE_STONE);
                        }
                        flights.add(column(sx, sz));
                    }
                }
            }
            // the parapet on the edges of the terraces, where they are high over what is beside them (not by the stairs)
            for (Map.Entry<Long, Integer> entry : tops.entrySet()) {
                int x = (int) (entry.getKey() >> 32);
                int z = (int) (long) entry.getKey();
                int top = entry.getValue();
                if (top < 1) continue;
                boolean edge = false;
                for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long next = column(x + step[0], z + step[1]);
                    if (flights.contains(next)) {
                        edge = false;
                        break;
                    }
                    if (!standing.containsKey(next) && tops.getOrDefault(next, -1) < top - 1) edge = true;
                }
                if (edge && !flights.contains(entry.getKey()) && !lifted.has(x, top + 1, z)) lifted.put(x, top + 1, z, TreePart.HOUSE_STONE);
            }
            return lifted;
        }

        /**
         * (t193) The rank of a kind of part: a part never takes away nor covers the blocks of a part of a higher rank (of the same rank, the later one
         * wins), so where parts meet no holes are opened. The base is under them all (the parts open it where they join it).
         */
        static int rank(HouseModule.Type type) {
            return switch (type) {
                case TOWER -> 5;
                case VOLUME, WING -> 4;
                case DORMER, CHIMNEY -> 3;
                case BAY, BALCONY, STAIR -> 2;
                case PORCH -> 1;
            };
        }

        /** The storey a part's place is taken from (the jetty of that storey moves the front and back walls). */
        int storeyOf(HouseModule m) {
            return switch (m.type) {
                case DORMER -> floors - 1;
                case TOWER, BALCONY, BAY, STAIR -> m.level;
                case VOLUME -> Math.min(m.level, floors - 1);
                default -> 0;
            };
        }

        /** Works out one part on what is there without putting it in ({@code tops}: the highest block of each column of {@code out}, see {@link Build#tops}). */
        Build prepare(Blocks out, Map<Long, Integer> tops, HouseModule m) {
            Build build = new Build(out, tops, side(m.side, storeyOf(m)), m.along, m.offset);
            switch (m.type) {
                case DORMER -> dormer(build, m);
                case CHIMNEY -> chimney(build, m);
                case TOWER -> tower(build, m);
                case WING -> wing(build, m);
                case PORCH -> porch(build, m);
                case BALCONY -> balcony(build, m);
                case BAY -> bay(build, m);
                case VOLUME -> volume(build, m);
                case STAIR -> stair(build, m);
            }
            // (t195: what stands on the ground from its foot: a tower from the ground, a wing, a porch, a chimney, stairs)
            if (m.type == HouseModule.Type.WING || m.type == HouseModule.Type.PORCH || m.type == HouseModule.Type.CHIMNEY || m.type == HouseModule.Type.STAIR
                    || m.type == HouseModule.Type.TOWER && m.level == 0) {
                build.floor = 0;
            }
            return build;
        }

        /**
         * A dormer: a box on the roof from the wall line (moved by the offset) back into the roof, its own gable roof (the ridge running into the house's
         * roof), a window in its face; it reaches back at least its depth and on until the house's roof is as high as its ridge, and the house's roof inside it
         * is opened.
         */
        private void dormer(Build p, HouseModule m) {
            int w = Math.max(3, m.width);
            int lo = -(w - 1) / 2;
            int hi = lo + w - 1;
            int h = Math.max(2, m.height);
            int eaves = top + h;
            int thick = stepped() ? steps.highest() : Math.max(1, (int) Math.ceil(pitch));
            int ridgeY = eaves + dormerRise(0, lo, hi);
            int back = Math.max(1, m.depth);
            for (int k = 1; k <= 20; k++) {
                if (p.topAt(0, -k) >= ridgeY) {
                    back = Math.max(back, k);
                    break;
                }
            }
            for (int v = -(back - 1); v <= 0; v++) {
                for (int u = lo; u <= hi; u++) {
                    boolean cheek = u == lo || u == hi;
                    int rise = dormerRise(u, lo, hi);
                    for (int y = top; y < eaves + rise - thick + 1; y++) {
                        if (v == 0) {
                            // the face: a sill, the window, the gable over it
                            if (!cheek && y > top && y < eaves) p.carve(u, v, y);
                            else p.put(u, v, y, TreePart.HOUSE_WALL);
                        } else if (cheek) {
                            if (y < eaves) p.soft(u, v, y, TreePart.HOUSE_WALL);
                        } else if (y > top) {
                            p.carve(u, v, y);
                        }
                    }
                }
            }
            for (int v = -(back - 1); v <= 1; v++) {
                for (int u = lo - 1; u <= hi + 1; u++) {
                    int rise = dormerRise(u, lo, hi);
                    for (int y = eaves + rise; y > eaves + rise - thick && y >= eaves; y--) p.put(u, v, y, TreePart.HOUSE_ROOF);
                }
            }
        }

        /** How high the roof of a dormer from {@code lo} to {@code hi} is over its eaves at {@code u} (a gable, out by one each side; of steps on a roof of steps). */
        int dormerRise(int u, int lo, int hi) {
            int in = Math.min(u - (lo - 1), hi + 1 - u);
            if (stepped()) return steps.rise(in, (hi - lo + 2) / 2);
            return (int) Math.floor(in * pitch);
        }

        /** A chimney: a stone stack outside the wall (inside with a negative offset), from the ground to {@code height} over the highest of the house near it. */
        private void chimney(Build p, HouseModule m) {
            int lo = -(m.width - 1) / 2;
            int hi = lo + m.width - 1;
            int high = top;
            for (int u = lo; u <= hi; u++) {
                for (int v = -2; v <= m.depth; v++) high = Math.max(high, p.topAt(u, v));
            }
            int tip = high + Math.max(1, m.height);
            for (int u = lo; u <= hi; u++) {
                for (int v = 1; v <= m.depth; v++) {
                    for (int y = 0; y <= tip; y++) p.put(u, v, y, TreePart.HOUSE_STONE);
                }
            }
        }

        /**
         * A tower of {@code width} across (round, octagonal or square: {@code shape}), out from the wall (by default half in it), from storey {@code level} up
         * to {@code height} over the house's eaves, a plain prism (t193: no holes in its wall) with a floor at every storey and a steep cone of a roof (a pyramid on
         * a square one); from a storey over the ground it hangs on the wall on a corbel (a turret). Its top storey may jut out by {@code jetty} on brackets
         * (t190). Its inside opens into the house.
         */
        private void tower(Build p, HouseModule m) {
            int size = Math.max(3, m.width);
            int lo = -(size - 1) / 2;
            double cu = (lo + lo + size - 1) / 2.0;
            double cv = (1 + size) / 2.0;
            double radius = size / 2.0;
            int shape = m.shape;
            int flare = Math.max(0, Math.min(2, m.jetty));
            int level = Math.max(0, Math.min(floors - 1, m.level));
            int foot = level * storey;
            int head = Math.max(foot + storey, top + m.height);
            // (the top storey that juts out starts a storey under the head)
            int jut = flare > 0 ? Math.max(foot + 1, head - storey) : head;
            TreePart wall = level == 0 ? TreePart.HOUSE_STONE : TreePart.HOUSE_WALL;
            int reach = (int) Math.ceil(radius) + 2;
            for (int u = lo - 2 - flare; u <= lo + size + 1 + flare; u++) {
                for (int v = -1 - flare; v <= size + 2 + flare; v++) {
                    double du = u - cu;
                    double dv = v - cv;
                    double d = metric(shape, du, dv);
                    double angle = Math.atan2(du, dv);
                    boolean facing = Math.abs(angle) < 0.35 || Math.abs(Math.abs(angle) - Math.PI / 2.0) < 0.35;
                    boolean ringLow = edge(shape, u, v, cu, cv, radius);
                    boolean ringHigh = edge(shape, u, v, cu, cv, radius + flare);
                    for (int y = foot; y < head; y++) {
                        boolean high = y >= jut;
                        if (d > (high ? radius + flare : radius)) continue;
                        int inStorey = Math.floorMod(y, storey);
                        // (t193: a plain prism, no holes in its wall)
                        if (high ? ringHigh : ringLow) {
                            p.put(u, v, y, high && flare > 0 ? TreePart.HOUSE_WALL : wall);
                        } else if (inStorey == 0 || y == jut) {
                            p.put(u, v, y, TreePart.HOUSE_FLOOR);
                        } else {
                            p.carve(u, v, y);
                        }
                    }
                    // the brackets under the top storey that juts out
                    if (flare > 0 && d > radius && d <= radius + 1.0 && facing && jut - 1 >= foot) p.put(u, v, jut - 1, TreePart.HOUSE_BEAM);
                    // the corbel of a turret: rings narrowing down under it
                    if (level > 0 && d <= radius) {
                        for (int k = 1; k <= reach; k++) {
                            double r = radius - k;
                            if (r < 0.5 || d > r || foot - k < 0) continue;
                            if (d > r - 1.0) p.put(u, v, foot - k, wall);
                        }
                    }
                    // the cone, one block out past the walls (on a roof of steps: its steps, half as steep again, a witch's hat)
                    double eaves = radius + flare + 1.0;
                    if (d <= eaves) {
                        int rise;
                        int thick;
                        // (a round cone is a witch's hat; an octagonal one less steep; a square one a pyramid about as steep as the house's roof;
                        // t239: all of them higher by the spire, up to two and a half times)
                        double sharp = shape == 2 ? 1.25 / 2.5 : shape == 1 ? 2.0 / 2.5 : 1.0;
                        if (m.spire > 0) {
                            // (t239: a spire, a straight cone as high as the tower is wide times 1.1 to 1.9, whatever the house's roof: steps would make a
                            // needle of so small a cone)
                            double high = eaves * (1.5 + 0.75 * Math.min(3, m.spire)) * (shape == 2 ? 0.8 : 1.0);
                            rise = (int) Math.round((eaves - d) / eaves * high);
                            thick = (int) Math.ceil(high / eaves) + 1;
                        } else if (stepped()) {
                            rise = (int) Math.round(steps.rise((int) Math.floor(eaves - d), (int) Math.floor(eaves)) * CONE_STEEP * sharp);
                            thick = (int) Math.ceil(steps.highest() * CONE_STEEP * sharp);
                        } else {
                            double steep = Math.max(shape == 2 ? 1.0 : 1.5, pitch * 2.5 * sharp);
                            rise = (int) Math.floor((eaves - d) * steep);
                            thick = (int) Math.ceil(steep);
                        }
                        for (int y = head + rise; y > head + rise - thick && y >= head; y--) p.put(u, v, y, TreePart.HOUSE_ROOF);
                    }
                }
            }
        }

        /** How much steeper the cone of a tower is than the house's roof of steps. */
        private static final double CONE_STEEP = 1.5;

        /** The distance from the middle of a tower in its shape (0 round, 1 octagonal, 2 square): what is within its radius is in it. */
        static double metric(int shape, double x, double y) {
            double ax = Math.abs(x);
            double ay = Math.abs(y);
            return switch (shape) {
                case 1 -> Math.max(Math.max(ax, ay), (ax + ay) / Math.sqrt(2.0));
                case 2 -> Math.max(ax, ay);
                default -> Math.hypot(x, y);
            };
        }

        /** Whether {@code (u, v)} is on the wall of a tower of {@code radius}: in it, with one of its eight neighbours out of it. */
        private static boolean edge(int shape, int u, int v, double cu, double cv, double radius) {
            if (metric(shape, u - cu, v - cv) > radius) return false;
            for (int du = -1; du <= 1; du++) {
                for (int dv = -1; dv <= 1; dv++) {
                    if ((du != 0 || dv != 0) && metric(shape, u + du - cu, v + dv - cv) > radius) return true;
                }
            }
            return false;
        }

        /**
         * A wing: a smaller house of {@code height} storeys out from the side, {@code depth} long, joined to the house (its wall there is opened), with a roof
         * of the house's shape whose ridge runs out from the house (a cross gable) and back into the house's roof as far as it is over it (the valleys).
         */
        private void wing(Build p, HouseModule m) {
            int w = Math.max(3, m.width);
            int lo = -(w - 1) / 2;
            int d = Math.max(1, m.depth) + 1;
            int stories = Math.max(1, Math.min(6, m.height));
            int eaves = stories * storey;
            HousePlan plan = new HousePlan(w, d, storey, spacing, windows, false, mirror);
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < d; j++) {
                    boolean edge = i == 0 || j == 0 || i == w - 1 || j == d - 1;
                    for (int k = 0; k < stories; k++) {
                        for (int level = 0; level < storey; level++) {
                            int y = k * storey + level;
                            if (j == 0 && i > 0 && i < w - 1) {
                                // the house's wall where the wing joins it is opened (its floors go on)
                                if (level == 0) p.put(lo + i, 0, y, TreePart.HOUSE_FLOOR);
                                else p.carve(lo + i, 0, y);
                                continue;
                            }
                            TreePart part = cell(plan, i, j, level, edge, stone && k == 0);
                            if (part != null) p.put(lo + i, j, y, part);
                        }
                    }
                }
            }
            if (roof == 0) {
                for (int i = -overhang; i < w + overhang; i++) {
                    for (int j = 0; j < d + overhang; j++) p.put(lo + i, j, eaves, TreePart.HOUSE_ROOF);
                }
                return;
            }
            int back = halfAcross(m.side);
            int lowA = -overhang;
            int lowB = -back;
            // (out from the house's wall the wing's roof is put over what is there; back in the house only where it is over the house's roof)
            int[][] bottom = roof((i, y, j, part) -> {
                if (j >= 0) p.put(lo + i, j, y, part);
                else if (y > p.topAt(lo + i, j)) p.put(lo + i, j, y, part);
            }, roof, 2, pitch, steps, eaves, lowA, w - 1 + overhang, lowB, d - 1 + overhang);
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < d; j++) {
                    int under = bottom[i - lowA][j - lowB];
                    boolean edge = i == 0 || i == w - 1 || j == d - 1;
                    for (int y = eaves; y < eaves + under; y++) {
                        if (edge) p.put(lo + i, j, y, plan.pillar(i, j) ? TreePart.HOUSE_PILLAR : timber && y == eaves ? TreePart.HOUSE_BEAM : TreePart.HOUSE_WALL);
                        // (inside its roof: what of the house's eaves is there goes; the house's own wall on the line it joins stays)
                        else if (y > eaves && j > 0) p.carve(lo + i, j, y);
                    }
                    if (!edge && j > 0 && under > 0) p.put(lo + i, j, eaves, TreePart.HOUSE_FLOOR);
                }
            }
        }

        /** A porch: a floor out in front of the wall, posts at its outer edge (the same both ways) and a roof sloping away from the house at {@code height}. */
        private void porch(Build p, HouseModule m) {
            int lo = -(m.width - 1) / 2;
            int hi = lo + m.width - 1;
            int d = m.depth;
            int h = Math.max(2, m.height);
            for (int u = lo; u <= hi; u++) {
                for (int v = 1; v <= d; v++) {
                    p.put(u, v, 0, TreePart.HOUSE_FLOOR);
                    // (posts at the corners and every 4 from both of them, the same both ways; none in the middle, where the door is)
                    int k = u - lo;
                    int last = hi - lo;
                    boolean post = v == d && (k == 0 || k == last || Math.min(k, last - k) % 4 == 0 && 2 * k != last);
                    if (post) {
                        for (int y = 0; y < h; y++) p.put(u, v, y, TreePart.HOUSE_PILLAR);
                    }
                }
            }
            double slope = pitch * 0.5;
            int thick = Math.max(1, (int) Math.ceil(slope));
            for (int u = lo - 1; u <= hi + 1; u++) {
                for (int v = 1; v <= d + 1; v++) {
                    int y = h + (int) Math.floor((d + 1 - v) * slope);
                    for (int t = 0; t < thick && y - t >= h; t++) p.put(u, v, y - t, TreePart.HOUSE_ROOF);
                }
            }
        }

        /** A balcony: a floor out from storey {@code level}, a railing of {@code height} round it, knee braces under it and a doorway in the wall to it. */
        private void balcony(Build p, HouseModule m) {
            int level = Math.max(0, Math.min(floors, m.level));
            int floorY = level * storey;
            int lo = -(m.width - 1) / 2;
            int hi = lo + m.width - 1;
            int d = m.depth;
            int rail = Math.max(1, m.height);
            for (int u = lo; u <= hi; u++) {
                for (int v = 1; v <= d; v++) {
                    p.put(u, v, floorY, TreePart.HOUSE_FLOOR);
                    if (u == lo || u == hi || v == d) {
                        for (int y = floorY + 1; y <= floorY + rail; y++) p.put(u, v, y, TreePart.HOUSE_PILLAR);
                    }
                }
            }
            for (int u : new int[]{lo, hi}) {
                for (int v = 1; v <= d; v++) {
                    int y = floorY - 1 - (d - v);
                    if (y >= 0) p.put(u, v, y, TreePart.HOUSE_BEAM);
                }
            }
            if (level > 0 && storey >= 3) {
                for (int y = floorY + 1; y <= floorY + 2; y++) p.carveWall(0, y);
            }
        }

        /** A bay window: a box out from the wall of storey {@code level}, glass on its faces between posts at its corners, a floor and a roof; open to the room. */
        private void bay(Build p, HouseModule m) {
            int level = Math.max(0, Math.min(floors - 1, m.level));
            int foot = level * storey + 1;
            int w = Math.max(2, m.width);
            int lo = -(w - 1) / 2;
            int hi = lo + w - 1;
            int d = m.depth;
            int h = Math.max(2, m.height);
            for (int u = lo; u <= hi; u++) {
                for (int v = 1; v <= d; v++) {
                    p.put(u, v, foot, TreePart.HOUSE_FLOOR);
                    p.put(u, v, foot + h, TreePart.HOUSE_ROOF);
                    boolean corner = (u == lo || u == hi) && v == d;
                    if (corner) {
                        for (int y = foot + 1; y < foot + h; y++) p.put(u, v, y, TreePart.HOUSE_PILLAR);
                    }
                }
                // a bracket under each end
                if ((u == lo || u == hi) && foot - 1 > 0) p.put(u, 1, foot - 1, TreePart.HOUSE_BEAM);
            }
            int from = w >= 3 ? lo + 1 : lo;
            int to = w >= 3 ? hi - 1 : hi;
            for (int u = from; u <= to; u++) {
                for (int y = foot + 1; y < foot + h; y++) p.carveWall(u, y);
            }
        }

        /**
         * A volume (t190): a box of {@code height} storeys, {@code width} along the side and {@code depth} out (from the wall line moved by the offset), from
         * storey {@code level} up, with its own roof ({@code roof}, -1 the house's; {@code ridge}) and the house's walls, windows, timber, jetty ({@code jetty},
         * -1 the house's) and so on. Where it is in the house its walls are left out and the house is opened into it (its corners stay); inside it, what of
         * the house is there is taken away, so it stands through the house's roof when it is higher; out of the house over the ground it stands on brackets.
         */
        private void volume(Build p, HouseModule m) {
            int w = Math.max(3, m.width);
            int d = Math.max(3, m.depth);
            int storeys = Math.max(1, Math.min(6, m.height));
            int level = Math.max(0, m.level);
            // (t195: its floor raised or lowered from the storey by its own foundation, not under the ground)
            int baseY = Math.max(-raise, level * storey + m.raise);
            // (one on the ground at the front has a door of its own in the middle of its front, so a volume in front of the house's door does not shut it)
            boolean front = door && level == 0 && Math.floorMod(m.side, 4) == 0;
            Box box = new Box(w, d, storeys, m.roof < 0 ? roof : m.roof, m.ridge, m.jetty < 0 ? jetty : m.jetty, front, stone && level == 0, level == 0);
            Blocks mass = new Blocks();
            box.build(mass);
            int lo = -(w - 1) / 2;
            Map<Long, int[]> spans = new HashMap<>();
            for (Map.Entry<Long, TreePart> entry : mass.map.entrySet()) {
                long key = entry.getKey();
                int a = TreeVoxelizer.unpackX(key);
                int y = TreeVoxelizer.unpackY(key);
                int b = TreeVoxelizer.unpackZ(key);
                spans.merge(column(a, b), new int[]{y, y}, (one, two) -> new int[]{Math.min(one[0], two[0]), Math.max(one[1], two[1])});
                int u = lo + a;
                int wy = y + baseY;
                TreePart part = entry.getValue();
                boolean corner = y < box.top && box.corner(a, b, Math.min(storeys - 1, y / storey));
                if (part != TreePart.HOUSE_FLOOR && !corner && inside(p.a(u, b), p.b(u, b), wy)) {
                    // (t250: its storeys open into the house; its roof and gables over them, where they are in the house, are the house's: a volume lower
                    // than the house takes nothing of the house's walls, floors or roof above its own storeys)
                    if (y < box.top) p.carve(u, b, wy);
                    continue;
                }
                p.put(u, b, wy, part);
            }
            // its inside: in each of its columns, between its lowest and highest blocks, what it has not (t250: under its roof, what of the house is there
            // only up to its own storeys)
            for (Map.Entry<Long, int[]> entry : spans.entrySet()) {
                int a = (int) (entry.getKey() >> 32);
                int b = (int) (long) entry.getKey();
                for (int y = entry.getValue()[0]; y <= entry.getValue()[1]; y++) {
                    if (mass.has(a, y, b)) continue;
                    if (y >= box.top && inside(p.a(lo + a, b), p.b(lo + a, b), y + baseY)) continue;
                    p.carve(lo + a, b, y + baseY);
                }
            }
            // (t195) on the ground: what of it stands there (its lowest layer), its terrace, its door
            if (level == 0) {
                p.floor = baseY;
                for (int a = -terrace; a < w + terrace; a++) {
                    for (int b = -terrace; b < d + terrace; b++) {
                        int ring = Math.max(Math.max(-a, a - w + 1), Math.max(-b, b - d + 1));
                        if (ring > 0) p.apron.put(column(p.a(lo + a, b), p.b(lo + a, b)), ring);
                    }
                }
                if (front) {
                    int at = lo + box.plan(0).doorAt();
                    int x = p.a(at, d - 1);
                    int z = p.b(at, d - 1);
                    p.doors.add(new int[]{x, z, baseY, p.a(at, d) - x, p.b(at, d) - z});
                }
            }
            // over the ground and out of the house: a bracket under each pillar of its outer walls
            if (level > 0 && brackets) {
                HousePlan plan = box.plan(0);
                for (int pa = 0; pa < plan.width(); pa++) {
                    for (int pb = 0; pb < plan.depth(); pb++) {
                        boolean edge = pa == 0 || pb == 0 || pa == plan.width() - 1 || pb == plan.depth() - 1;
                        if (!edge || !plan.pillar(pa, pb)) continue;
                        int u = lo + pa;
                        if (inside(p.a(u, pb), p.b(u, pb), baseY - 1)) continue;
                        int[] in = inward(plan, pa, pb);
                        p.soft(u, pb, baseY - 1, TreePart.HOUSE_BEAM);
                        if (baseY - 2 >= 0) p.soft(u + in[0], pb + in[1], baseY - 2, TreePart.HOUSE_BEAM);
                    }
                }
            }
        }

        /**
         * Stairs outside (t190): a stone landing in front of a doorway of storey {@code level} (opened in the wall), and the flight down along the wall from
         * it, {@code width} wide, going up to the right ({@code shape} 0) or to the left (1); solid under it down to the ground.
         */
        private void stair(Build p, HouseModule m) {
            int level = Math.max(1, Math.min(Math.max(1, floors - 1), m.level));
            int floorY = level * storey;
            int w = Math.max(1, Math.min(3, m.width));
            int up = m.shape == 1 ? -1 : 1;
            for (int u = -1; u <= 1; u++) {
                for (int v = 1; v <= w; v++) {
                    for (int y = 0; y <= floorY; y++) p.put(u, v, y, TreePart.HOUSE_STONE);
                }
            }
            for (int k = 1; k < floorY; k++) {
                int u = -up * (1 + k);
                for (int v = 1; v <= w; v++) {
                    for (int y = 0; y <= floorY - k; y++) p.put(u, v, y, TreePart.HOUSE_STONE);
                }
            }
            // (t193: where the storey it goes up to juts out, the stairs are filled back to the wall of the ground storey under it, so they stand on the house)
            int gap = Math.floorMod(m.side, 4) % 2 == 0 ? out(level) : outA(level);
            for (int k = 0; k < floorY; k++) {
                int top = k == 0 ? floorY : floorY - k;
                for (int u : k == 0 ? new int[]{-1, 0, 1} : new int[]{-up * (1 + k)}) {
                    for (int v = 1 - gap; v <= 0; v++) {
                        for (int y = 0; y <= Math.min(top, storey - 1); y++) p.soft(u, v, y, TreePart.HOUSE_STONE);
                    }
                }
            }
            for (int y = floorY + 1; y <= floorY + 2; y++) p.carveWall(0, y);
        }
    }

    /**
     * A side of a house as a frame for a part: {@code u} along the side (to the right of someone outside facing it), {@code v} out from its wall line
     * ({@code wall}: the {@code b} of the front or back wall, the {@code a} of a side wall; {@code middle}: the middle of the side).
     */
    record Side(int index, int wall, int middle) {

        int a(int u, int v) {
            return switch (index) {
                case 1 -> wall + v;
                case 2 -> middle - u;
                case 3 -> wall - v;
                default -> middle + u;
            };
        }

        int b(int u, int v) {
            return switch (index) {
                case 1 -> middle - u;
                case 2 -> wall - v;
                case 3 -> middle + u;
                default -> wall + v;
            };
        }
    }

    /** A sink of blocks in some coordinates. */
    interface Sink {
        void put(int a, int y, int b, TreePart part);
    }

    /**
     * A part being built on the house: the blocks it puts over what is there, the blocks it puts only where nothing is, and the blocks it takes away, all
     * from its own frame ({@code u} from its middle along the side, {@code v} out from the wall line moved by its offset); {@link #apply} puts them in.
     */
    static final class Build {
        private final Blocks out;
        private final Side side;
        private final int along;
        private final int offset;
        final Blocks over = new Blocks();
        final Blocks under = new Blocks();
        final Set<Long> carved = new HashSet<>();
        /** (t195) The floor of a part that stands on the ground (its lowest layer), or MIN_VALUE; the terrace round it (column → how far out); its doors. */
        int floor = Integer.MIN_VALUE;
        final Map<Long, Integer> apron = new HashMap<>();
        final List<int[]> doors = new ArrayList<>();
        /** The highest block of each column of what is there (by {@link #column}). */
        private final Map<Long, Integer> tops;

        Build(Blocks out, Map<Long, Integer> tops, Side side, int along, int offset) {
            this.out = out;
            this.tops = tops;
            this.side = side;
            this.along = along;
            this.offset = offset;
        }

        /** The highest block of each column of {@code blocks} (by {@link #column}). */
        static Map<Long, Integer> tops(Blocks blocks) {
            Map<Long, Integer> tops = new HashMap<>();
            for (long key : blocks.map.keySet()) {
                tops.merge(column(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackZ(key)), TreeVoxelizer.unpackY(key), Math::max);
            }
            return tops;
        }

        /** The house's {@code a} and {@code b} of the part's {@code (u, v)}. */
        int a(int u, int v) {
            return side.a(along + u, offset + v);
        }

        int b(int u, int v) {
            return side.b(along + u, offset + v);
        }

        void put(int u, int v, int y, TreePart part) {
            over.put(a(u, v), y, b(u, v), part);
        }

        /** Puts a block only where nothing of the house is. */
        void soft(int u, int v, int y, TreePart part) {
            under.put(a(u, v), y, b(u, v), part);
        }

        void carve(int u, int v, int y) {
            carved.add(TreeVoxelizer.pack(a(u, v), y, b(u, v)));
        }

        /** Takes away the block of the house's wall line (whatever the offset) at {@code u}, and the one behind it (a wall set back, t193). */
        void carveWall(int u, int y) {
            carved.add(TreeVoxelizer.pack(side.a(along + u, 0), y, side.b(along + u, 0)));
            carved.add(TreeVoxelizer.pack(side.a(along + u, -1), y, side.b(along + u, -1)));
        }

        /** The highest block of the house in the column at {@code (u, v)}; {@link Integer#MIN_VALUE} when there is none. */
        int topAt(int u, int v) {
            Integer top = tops.get(column(a(u, v), b(u, v)));
            return top == null ? Integer.MIN_VALUE : top;
        }

        /**
         * Puts the part in: what it takes away, what it puts over, what it puts where nothing is; not over or out of a part of a higher {@code ranks}.
         * (t250) What a part takes away stays its own, as empty: no part after it of its rank or lower puts anything there (a chimney through a tower,
         * the wall of a volume in the room of another), so the room of a part is never filled by another; a part's own blocks in its own room are put.
         */
        void apply(Map<Long, Integer> ranks, int rank) {
            for (long key : carved) {
                Integer owner = ranks.get(key);
                if (owner != null && owner > rank) continue;
                out.map.remove(key);
                ranks.put(key, rank);
            }
            for (Map.Entry<Long, TreePart> entry : over.map.entrySet()) {
                long key = entry.getKey();
                Integer owner = ranks.get(key);
                if (owner != null && (owner > rank || owner == rank && emptied(key))) continue;
                out.map.put(key, entry.getValue());
                ranks.put(key, rank);
            }
            for (Map.Entry<Long, TreePart> entry : under.map.entrySet()) {
                long key = entry.getKey();
                if (ranks.containsKey(key) && emptied(key)) continue;
                if (out.map.putIfAbsent(key, entry.getValue()) == null) ranks.put(key, rank);
            }
        }

        /** (t250) Whether a block was taken away by a part before this one (it is empty and some part's, not this part's own room). */
        private boolean emptied(long key) {
            return !out.map.containsKey(key) && !carved.contains(key);
        }
    }

    static long column(int a, int b) {
        return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
    }

    // ---- the roofs --------------------------------------------------------------------------------------------------------------------

    /**
     * Puts a roof of {@code type} over the rectangle from ({@code lowA}, {@code lowB}) to ({@code highA}, {@code highB}) (the eaves, already out by the
     * overhang), its eaves at {@code base}: each column gets roof down to one over its lowest neighbour, at most as thick as the steepest slope of the
     * shape needs; below that (a step of the roof: the gable of a hip-and-gable or of a clipped gable) wall. A roof of steps whose first step is more than
     * 1 hangs that much down at its eaves. Returns how high over {@code base} the lowest block of each column is (by {@code [a - lowA][b - lowB]}).
     */
    static int[][] roof(Sink sink, int type, int ridge, double pitch, Steps steps, int base, int lowA, int highA, int lowB, int highB) {
        int sizeA = highA - lowA + 1;
        int sizeB = highB - lowB + 1;
        int[][] rise = new int[sizeA][sizeB];
        for (int i = 0; i < sizeA; i++) {
            for (int j = 0; j < sizeB; j++) rise[i][j] = roofRise(type, ridge, lowA + i, lowB + j, lowA, highA, lowB, highB, pitch, steps);
        }
        int thick = roofThickness(type, pitch, steps);
        // (the eaves of a roof of steps: all four edges of the hipped one, the two along the ridge of the gabled one)
        boolean stepped = type == 9 || type == 10;
        boolean alongA = ridgeAlongA(ridge, highA - lowA, highB - lowB);
        int drop = stepped ? steps.at(0, (type == 10 ? Math.min(highA - lowA, highB - lowB) : alongA ? highB - lowB : highA - lowA) / 2) - 1 : 0;
        int[][] bottom = new int[sizeA][sizeB];
        for (int i = 0; i < sizeA; i++) {
            for (int j = 0; j < sizeB; j++) {
                int r = rise[i][j];
                int lowest = Integer.MAX_VALUE;
                if (i > 0) lowest = Math.min(lowest, rise[i - 1][j]);
                if (i < sizeA - 1) lowest = Math.min(lowest, rise[i + 1][j]);
                if (j > 0) lowest = Math.min(lowest, rise[i][j - 1]);
                if (j < sizeB - 1) lowest = Math.min(lowest, rise[i][j + 1]);
                int low = lowest == Integer.MAX_VALUE ? r : Math.min(r, lowest + 1);
                boolean eaves = type == 10 ? i == 0 || j == 0 || i == sizeA - 1 || j == sizeB - 1
                        : alongA ? j == 0 || j == sizeB - 1 : i == 0 || i == sizeA - 1;
                if (drop > 0 && eaves) low = Math.min(low, r - drop);
                bottom[i][j] = low;
                for (int y = r; y >= low; y--) sink.put(lowA + i, base + y, lowB + j, r - y < thick || y < 0 ? TreePart.HOUSE_ROOF : TreePart.HOUSE_WALL);
            }
        }
        return bottom;
    }

    /**
     * The ridge beam (t190): a beam over the highest blocks of a roof (the ridge, or the top of a hip); where the ridge runs out to a gable, it juts two blocks
     * past it and its tip is turned up.
     */
    static void ridgeBeam(Sink sink, int type, int ridge, double pitch, Steps steps, int base, int lowA, int highA, int lowB, int highB) {
        if (type == 0 || type == 3) return;
        int sizeA = highA - lowA + 1;
        int sizeB = highB - lowB + 1;
        int[][] rise = new int[sizeA][sizeB];
        int high = Integer.MIN_VALUE;
        for (int i = 0; i < sizeA; i++) {
            for (int j = 0; j < sizeB; j++) {
                rise[i][j] = roofRise(type, ridge, lowA + i, lowB + j, lowA, highA, lowB, highB, pitch, steps);
                high = Math.max(high, rise[i][j]);
            }
        }
        int y = base + high + 1;
        for (int i = 0; i < sizeA; i++) {
            for (int j = 0; j < sizeB; j++) {
                if (rise[i][j] != high) continue;
                int a = lowA + i;
                int b = lowB + j;
                sink.put(a, y, b, TreePart.HOUSE_BEAM);
                // (the ends of a ridge at a gable)
                int da = i == 0 ? -1 : i == sizeA - 1 ? 1 : 0;
                int db = j == 0 ? -1 : j == sizeB - 1 ? 1 : 0;
                if (sizeA > 1 && sizeB > 1 && (da != 0) == (db != 0)) continue;
                if (da != 0 || db != 0) {
                    sink.put(a + da, y, b + db, TreePart.HOUSE_BEAM);
                    sink.put(a + 2 * da, y, b + 2 * db, TreePart.HOUSE_BEAM);
                    sink.put(a + 2 * da, y + 1, b + 2 * db, TreePart.HOUSE_BEAM);
                }
            }
        }
    }

    /** Whether the ridge runs along {@code a} (the front): as {@code ridge} says (1 along, 2 across), or along the longer side. */
    private static boolean ridgeAlongA(int ridge, int spanA, int spanB) {
        return ridge == 1 || ridge == 0 && spanA >= spanB;
    }

    /** {@link #roofRise(int, int, int, int, int, int, int, int, double, Steps)} of a roof that is not one of steps. */
    static int roofRise(int roof, int ridge, int a, int b, int lowA, int highA, int lowB, int highB, double pitch) {
        return roofRise(roof, ridge, a, b, lowA, highA, lowB, highB, pitch, Steps.DEFAULT);
    }

    /**
     * How high the roof is over the eaves at {@code (a, b)} of the rectangle of the eaves: flat 0; a gable rises toward a ridge (along the longer side, or
     * as {@code ridge} says: 1 along the front, 2 across it), a hip toward the middle from all four sides, a shed from the front to the back (at half the
     * pitch; across with ridge 2), a gambrel (a barn: steep, then gentle near the ridge) and a mansard (the same from all four sides, near flat on top), a
     * hip-and-gable (a hip low at the ends, a small gable over it), a clipped gable (a gable whose top is hipped), a curved roof (concave from all four
     * sides: the eaves flare out, the top is steep), and the roofs of steps (t185): a gable (9) and a hip (10) whose section is {@code steps}, how many
     * blocks each column shows from the eaves to the ridge (1 1 1 2 3 3: sharp, curving up).
     */
    static int roofRise(int roof, int ridge, int a, int b, int lowA, int highA, int lowB, int highB, double pitch, Steps steps) {
        int acrossA = Math.min(a - lowA, highA - a);
        int acrossB = Math.min(b - lowB, highB - b);
        int spanA = highA - lowA;
        int spanB = highB - lowB;
        boolean alongA = ridgeAlongA(ridge, spanA, spanB);
        // (from the eaves toward the ridge, and from the ends of the ridge)
        int across = alongA ? acrossB : acrossA;
        int end = alongA ? acrossA : acrossB;
        double half = (alongA ? spanB : spanA) / 2.0;
        int hip = Math.min(acrossA, acrossB);
        return switch (roof) {
            case 0 -> 0;
            case 2 -> (int) Math.floor(hip * pitch);
            case 3 -> (int) Math.floor((ridge == 2 ? highA - a : highB - b) * pitch * 0.5);
            case 4 -> broken(across, Math.max(1, (int) Math.round(half * 0.45)), pitch * 2.0, pitch * 0.5);
            case 5 -> broken(hip, Math.max(1, (int) Math.round(Math.min(spanA, spanB) / 2.0 * 0.4)), pitch * 2.5, pitch * 0.35);
            case 6 -> {
                int gable = Math.max(1, (int) Math.round(half * 0.5));
                yield (int) Math.floor((end < gable ? Math.min(across, end) : across) * pitch);
            }
            case 7 -> (int) Math.floor(Math.min(across * pitch, end * pitch + Math.round(half * pitch * 0.55)));
            case 8 -> {
                double inner = Math.max(1.0, Math.min(spanA, spanB) / 2.0);
                yield (int) Math.floor(inner * pitch * 1.25 * Math.pow(Math.min(1.0, hip / inner), 1.8));
            }
            case 9 -> steps.rise(across, (alongA ? spanB : spanA) / 2);
            case 10 -> steps.rise(hip, Math.min(spanA, spanB) / 2);
            default -> (int) Math.floor(across * pitch);
        };
    }

    /** A slope that is {@code steep} up to {@code knee} in from the eaves and {@code gentle} on from there. */
    private static int broken(int t, int knee, double steep, double gentle) {
        return (int) Math.floor(t <= knee ? t * steep : knee * steep + (t - knee) * gentle);
    }

    /** How thick a roof must be so its steepest slope has no holes (seen from the side; a roof of steps: its highest step). */
    static int roofThickness(int roof, double pitch, Steps steps) {
        if (roof == 9 || roof == 10) return steps.highest();
        double slope = switch (roof) {
            case 3 -> pitch * 0.5;
            case 4 -> pitch * 2.0;
            case 5 -> pitch * 2.5;
            case 8 -> pitch * 2.25;
            default -> pitch;
        };
        return Math.max(1, (int) Math.ceil(slope - 1.0E-9));
    }

    /**
     * The section of a roof of steps (t185): how many blocks each column shows, from the eaves ({@code values[0]}: more than 1 hangs down) to the ridge.
     * Fitted ({@code fit}) it is stretched or squeezed to the columns from the eaves to the ridge; else it is laid from the ridge out, and the first step goes
     * on where the roof is wider than it.
     */
    record Steps(int[] values, boolean fit) {

        static final Steps DEFAULT = of(TreeSettings.DEFAULT_STEPS, true);

        static Steps of(List<Integer> values, boolean fit) {
            int[] array = new int[Math.max(1, values.size())];
            array[0] = 1;
            for (int index = 0; index < values.size(); index++) array[index] = Math.max(0, values.get(index));
            return new Steps(array, fit);
        }

        /** The step of the column {@code d} in from the eaves, on a roof whose ridge is {@code reach} in. */
        int at(int d, int reach) {
            int n = values.length;
            int index = fit ? reach <= 0 ? n - 1 : (int) Math.round(d * (n - 1) / (double) reach) : n - 1 - (reach - d);
            return values[Math.max(0, Math.min(n - 1, index))];
        }

        /** How high the column {@code d} in from the eaves is over the eaves (the steps of the columns between added up). */
        int rise(int d, int reach) {
            int sum = 0;
            for (int k = 1; k <= d; k++) sum += at(k, reach);
            return sum;
        }

        /** The highest step (how thick the roof is), at least 1. */
        int highest() {
            int high = 1;
            for (int value : values) high = Math.max(high, value);
            return high;
        }
    }

    /** Where the pillars and the openings of a storey are (in its own coordinates: {@code a} along the front, {@code b} from the back to the front). */
    record HousePlan(int width, int depth, int storey, int spacing, boolean windows, boolean door, boolean mirror) {

        /** A pillar: a corner, or every {@code spacing} along a wall (counted from the corner; with {@code mirror} from the nearer corner, the same both ways). */
        boolean pillar(int a, int b) {
            boolean sideA = a == 0 || a == width - 1;
            boolean sideB = b == 0 || b == depth - 1;
            if (sideA && sideB) return true;
            if (spacing <= 0) return false;
            if (sideB) return (mirror ? Math.min(a, width - 1 - a) : a) % spacing == 0;
            if (sideA) return (mirror ? Math.min(b, depth - 1 - b) : b) % spacing == 0;
            return false;
        }

        /** Whether the wall block at {@code (a, b)}, {@code level} over the floor of the storey, is an opening: the door or a window. */
        boolean opening(int a, int b, int level) {
            return doorway(a, b, level) || window(a, b, level);
        }

        /** The door: the middle of the front, 2 high. */
        boolean doorway(int a, int b, int level) {
            return door && b == depth - 1 && (level == 1 || level == 2) && storey >= 3 && a == doorAt();
        }

        /** A window: in the middle of a stretch of wall between two pillars (not the one with the door), from 2 over the floor to 2 under the next. */
        boolean window(int a, int b, int level) {
            boolean front = b == depth - 1;
            if (!windows || storey < 4 || level < 2 || level > storey - 2) return false;
            // the window of the stretch of wall between two pillars: in its middle, 1 wide (2 when the stretch is 5 or more)
            int[] panel = panel(a, b);
            if (panel == null) return false;
            int start = panel[0];
            int end = panel[1];
            int inside = end - start - 1;
            if (inside < 3) return false;
            if (front && door && doorAt() > start && doorAt() < end) return false;
            if (mirror) {
                // (the same both ways: in the very middle, 1 wide (3 from 7) on an odd stretch, 2 wide on an even one)
                int wide = inside % 2 == 1 ? (inside >= 7 ? 3 : 1) : 2;
                return Math.abs(2 * panel[2] - (start + end)) < wide;
            }
            int middle = start + 1 + (inside - 1) / 2;
            return inside >= 5 ? panel[2] == middle || panel[2] == middle + 1 : panel[2] == middle;
        }

        /**
         * The stretch of wall a block of the edge is in: the pillars at its two ends and the block's place, counted along {@code a} on the front and the back
         * and along {@code b} on the sides; null inside.
         */
        int[] panel(int a, int b) {
            boolean alongA = b == 0 || b == depth - 1;
            if (!alongA && a != 0 && a != width - 1) return null;
            int along = alongA ? a : b;
            int length = alongA ? width : depth;
            int start = along;
            while (start > 0 && !pillar(alongA ? start : a, alongA ? b : start)) start--;
            int end = along;
            while (end < length - 1 && !pillar(alongA ? end : a, alongA ? b : end)) end++;
            return new int[]{start, end, along};
        }

        /** The block of the front wall the door is in: the middle, or the nearest block to it that is not a pillar (one to the right first). */
        int doorAt() {
            int middle = (width - 1) / 2;
            for (int k = 0; k < width; k++) {
                int a = middle + (k % 2 == 1 ? (k + 1) / 2 : -(k / 2));
                if (a > 0 && a < width - 1 && !pillar(a, depth - 1)) return a;
            }
            return middle;
        }
    }

    // ---- into the world --------------------------------------------------------------------------------------------------------------

    /** The foundation: under every pillar and stone block on the floor of the ground storey, down to the ground where the block goes (at most 12). */
    private static void foundation(Blocks local, Placement placement, TreeGround ground) {
        List<long[]> feet = new ArrayList<>();
        for (Map.Entry<Long, TreePart> entry : local.map.entrySet()) {
            TreePart part = entry.getValue();
            if (TreeVoxelizer.unpackY(entry.getKey()) != 0 || part != TreePart.HOUSE_PILLAR && part != TreePart.HOUSE_STONE) continue;
            feet.add(new long[]{TreeVoxelizer.unpackX(entry.getKey()), TreeVoxelizer.unpackZ(entry.getKey()), part.ordinal()});
        }
        for (long[] foot : feet) {
            int a = (int) foot[0];
            int b = (int) foot[1];
            int[] at = placement.block(a, b);
            int floor = ground.top(at[0], at[1]);
            if (floor == TreeGround.NONE) continue;
            TreePart part = TreePart.values()[(int) foot[2]];
            for (int y = -1; y > floor && y >= -12; y--) local.put(a, y, b, part);
        }
    }

    /**
     * Where the house's own coordinates go in the tree's: the front toward the player (the nearest of the four ways), turned on clockwise (from above) by
     * the rotation, the footprint centred on the block clicked. {@code degrees}: the whole turn from a house whose front faces south (+z).
     */
    record Placement(int degrees, int ca, int cb) {

        /** {@code aim}: from the block clicked toward the player's eye (null: the front faces south). */
        static Placement of(Vector aim, int rotation, int width, int depth) {
            double x = aim == null ? 0.0 : aim.getX();
            double z = aim == null ? 1.0 : aim.getZ();
            int facing;
            if (Math.abs(x) > Math.abs(z)) facing = x >= 0 ? 270 : 90;
            else facing = z >= 0 ? 0 : 180;
            return new Placement(Math.floorMod(facing + rotation, 360), -(width - 1) / 2, -(depth - 1) / 2);
        }

        boolean square() {
            return degrees % 90 == 0;
        }

        /** The tree's x and z of the house's block {@code (a, b)} (to the nearest block when the turn is not square). */
        int[] block(int a, int b) {
            int x = a + ca;
            int z = b + cb;
            if (square()) {
                for (int turn = 0; turn < degrees / 90; turn++) {
                    int next = -z;
                    z = x;
                    x = next;
                }
                return new int[]{x, z};
            }
            double angle = Math.toRadians(degrees);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new int[]{(int) Math.round(x * cos - z * sin), (int) Math.round(x * sin + z * cos)};
        }

        /** The house's {@code (a, b)} (to the nearest block) that is at the tree's {@code (x, z)}. */
        int[] local(int x, int z) {
            double angle = Math.toRadians(degrees);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new int[]{(int) Math.round(x * cos + z * sin) - ca, (int) Math.round(-x * sin + z * cos) - cb};
        }

        /**
         * Puts the house's blocks into the tree's. A square turn moves every block; any other takes for each block of the tree the house's block nearest
         * to it (so the walls have no holes), then fills one of each two blocks that touch only at an edge and closes the steps of the roofs it opened
         * (at most {@code 2 * thick + 1} high).
         */
        void place(Blocks local, Blocks into, int thick) {
            if (square()) {
                for (Map.Entry<Long, TreePart> entry : local.map.entrySet()) {
                    long key = entry.getKey();
                    int[] at = block(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackZ(key));
                    into.put(at[0], TreeVoxelizer.unpackY(key), at[1], entry.getValue());
                }
                return;
            }
            Map<Long, List<long[]>> columns = new HashMap<>();
            int minA = Integer.MAX_VALUE;
            int maxA = Integer.MIN_VALUE;
            int minB = Integer.MAX_VALUE;
            int maxB = Integer.MIN_VALUE;
            for (Map.Entry<Long, TreePart> entry : local.map.entrySet()) {
                long key = entry.getKey();
                int a = TreeVoxelizer.unpackX(key);
                int b = TreeVoxelizer.unpackZ(key);
                columns.computeIfAbsent(column(a, b), unused -> new ArrayList<>()).add(new long[]{TreeVoxelizer.unpackY(key), entry.getValue().ordinal()});
                minA = Math.min(minA, a);
                maxA = Math.max(maxA, a);
                minB = Math.min(minB, b);
                maxB = Math.max(maxB, b);
            }
            if (columns.isEmpty()) return;
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int[] corner : new int[][]{{minA, minB}, {minA, maxB}, {maxA, minB}, {maxA, maxB}}) {
                int[] at = block(corner[0], corner[1]);
                minX = Math.min(minX, at[0]);
                maxX = Math.max(maxX, at[0]);
                minZ = Math.min(minZ, at[1]);
                maxZ = Math.max(maxZ, at[1]);
            }
            TreePart[] parts = TreePart.values();
            for (int x = minX - 2; x <= maxX + 2; x++) {
                for (int z = minZ - 2; z <= maxZ + 2; z++) {
                    int[] from = local(x, z);
                    List<long[]> cells = columns.get(column(from[0], from[1]));
                    if (cells == null) continue;
                    for (long[] cell : cells) into.put(x, (int) cell[0], z, parts[(int) cell[1]]);
                }
            }
            seal(into);
            closeRoofs(into, 2 * thick + 1);
        }
    }

    /** Fills one of each two blocks of a layer that touch only at an edge (the one nearer the middle), with the stronger of the two parts. */
    private static void seal(Blocks blocks) {
        for (Map.Entry<Long, TreePart> entry : new ArrayList<>(blocks.map.entrySet())) {
            long key = entry.getKey();
            int x = TreeVoxelizer.unpackX(key);
            int y = TreeVoxelizer.unpackY(key);
            int z = TreeVoxelizer.unpackZ(key);
            for (int dz = -1; dz <= 1; dz += 2) {
                TreePart other = blocks.get(x + 1, y, z + dz);
                if (other == null || blocks.has(x + 1, y, z) || blocks.has(x, y, z + dz)) continue;
                TreePart fill = entry.getValue().priority() <= other.priority() ? entry.getValue() : other;
                long first = (long) (x + 1) * (x + 1) + (long) z * z;
                long second = (long) x * x + (long) (z + dz) * (z + dz);
                if (first <= second) blocks.put(x + 1, y, z, fill);
                else blocks.put(x, y, z + dz, fill);
            }
        }
    }

    /** Where a column of roof is higher than the roof of the column next to it by no more than {@code most}, roof down to one over it where nothing is. */
    private static void closeRoofs(Blocks blocks, int most) {
        Map<Long, Integer> tops = new HashMap<>();
        for (Map.Entry<Long, TreePart> entry : blocks.map.entrySet()) {
            if (entry.getValue() != TreePart.HOUSE_ROOF) continue;
            long key = entry.getKey();
            tops.merge(column(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackZ(key)), TreeVoxelizer.unpackY(key), Math::max);
        }
        for (Map.Entry<Long, Integer> entry : tops.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (long) entry.getKey();
            int top = entry.getValue();
            int lowest = top;
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Integer next = tops.get(column(x + step[0], z + step[1]));
                if (next != null && top - next <= most) lowest = Math.min(lowest, next);
            }
            for (int y = top - 1; y > lowest; y--) {
                if (!blocks.has(x, y, z)) blocks.put(x, y, z, TreePart.HOUSE_ROOF);
            }
        }
    }
}
