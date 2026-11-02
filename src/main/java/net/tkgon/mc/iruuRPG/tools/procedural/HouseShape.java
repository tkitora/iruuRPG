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
 * The house base of the procedural tool (t179; t181 the turn, the fantasy roofs and the parts added on), a block-out in the parts' wool after the
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
            default -> "切妻";
        };
    }

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
        House house = new House(s);
        Blocks local = new Blocks();
        house.body(local);
        for (HouseModule module : s.modules()) {
            if (module.on) house.module(local, module);
        }
        Placement placement = Placement.of(aim, s.getInt(TreeSettings.Key.HOUSE_ROTATION), house.width, house.depth);
        if (s.on(TreeSettings.Key.HOUSE_FOUNDATION) && ground != null) foundation(local, placement, ground);
        placement.place(local, blocks, roofThickness(house.roof, house.pitch));
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
        return Math.min(32, (int) Math.ceil(far) + s.getInt(TreeSettings.Key.HOUSE_OVERHANG) + 2);
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
        /** The height of the eaves: the top of the walls of the last storey. */
        final int top;

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
            top = floors * storey;
        }

        /** How far storey {@code k} (0 = the ground) reaches out past the ground storey at the front and at the back (the jetty, at most 3). */
        int out(int k) {
            return Math.min(3, Math.max(0, k) * jetty);
        }

        /** The plan of storey {@code k}: as wide as the house, as deep as the ground storey and its jetty both ways; the door only on the ground. */
        HousePlan plan(int k) {
            return new HousePlan(width, depth + 2 * out(k), storey, spacing, windows, door && k == 0);
        }

        /**
         * The base: the storeys, each a floor at its foot, walls round its edge (with the door and the windows; a beam at the foot and braces between
         * the pillars with a timber frame; stone on the ground with a stone base), pillars at the corners and along the walls; on top the roof out past
         * the walls by the overhang, the floor under it and the walls up to it.
         */
        void body(Blocks out) {
            for (int k = 0; k < floors; k++) {
                HousePlan plan = plan(k);
                int e = out(k);
                for (int a = 0; a < width; a++) {
                    for (int pb = 0; pb < plan.depth(); pb++) {
                        boolean edge = a == 0 || pb == 0 || a == width - 1 || pb == plan.depth() - 1;
                        for (int level = 0; level < storey; level++) {
                            TreePart part = cell(plan, a, pb, level, edge, stone && k == 0);
                            if (part != null) out.put(a, k * storey + level, pb - e, part);
                        }
                    }
                }
            }
            int e = out(floors - 1);
            HousePlan plan = plan(floors - 1);
            int lowA = -overhang;
            int lowB = -e - overhang;
            int[][] bottom = roof(out::put, roof, ridge, pitch, top, lowA, width - 1 + overhang, lowB, depth - 1 + e + overhang);
            if (roof == 0) return;
            boolean stoneTop = stone && floors == 1;
            for (int a = 0; a < width; a++) {
                for (int pb = 0; pb < plan.depth(); pb++) {
                    boolean edge = a == 0 || pb == 0 || a == width - 1 || pb == plan.depth() - 1;
                    int b = pb - e;
                    // the floor of the storey that holds up the roof (a flat roof is that floor itself), where the roof is not down on it
                    if (!edge) {
                        if (!out.has(a, top, b)) out.put(a, top, b, TreePart.HOUSE_FLOOR);
                        continue;
                    }
                    // the gaps under the roof over the walls (the gables, the high side of a shed) are walled up; the pillars go on up
                    boolean pillar = plan.pillar(a, pb);
                    for (int y = top; y < top + bottom[a - lowA][b - lowB]; y++) {
                        TreePart part = stoneTop ? TreePart.HOUSE_STONE : pillar || timber && y == top ? TreePart.HOUSE_PILLAR : TreePart.HOUSE_WALL;
                        out.put(a, y, b, part);
                    }
                }
            }
        }

        /** The block of a storey at {@code (a, b)} of its plan, {@code level} over its floor; null for none (inside, or an opening). */
        TreePart cell(HousePlan plan, int a, int b, int level, boolean edge, boolean stoneStorey) {
            if (plan.pillar(a, b)) return stoneStorey ? TreePart.HOUSE_STONE : TreePart.HOUSE_PILLAR;
            if (!edge) return level == 0 ? TreePart.HOUSE_FLOOR : null;
            if (plan.opening(a, b, level)) return null;
            if (stoneStorey) return TreePart.HOUSE_STONE;
            if (timber && (level == 0 || brace(plan, a, b, level))) return TreePart.HOUSE_PILLAR;
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
            int e = out(Math.max(0, Math.min(floors - 1, k)));
            return switch (Math.floorMod(index, 4)) {
                case 1 -> new Side(1, width - 1, (depth - 1) / 2);
                case 2 -> new Side(2, -e, (width - 1) / 2);
                case 3 -> new Side(3, 0, (depth - 1) / 2);
                default -> new Side(0, depth - 1 + e, (width - 1) / 2);
            };
        }

        /** Half the house across from {@code side} to the ridge (how far back the roof of a wing on that side reaches into the house's). */
        int halfAcross(int side) {
            return side % 2 == 0 ? (depth - 1) / 2 + out(floors - 1) : (width - 1) / 2;
        }

        /** Builds one part on what is there ({@code out}: the base and the parts before it). */
        void module(Blocks out, HouseModule m) {
            int k = switch (m.type) {
                case DORMER -> floors - 1;
                case TOWER, BALCONY, BAY -> m.level;
                default -> 0;
            };
            Build build = new Build(out, side(m.side, k), m.along, m.offset);
            switch (m.type) {
                case DORMER -> dormer(build, m);
                case CHIMNEY -> chimney(build, m);
                case TOWER -> tower(build, m);
                case WING -> wing(build, m);
                case PORCH -> porch(build, m);
                case BALCONY -> balcony(build, m);
                case BAY -> bay(build, m);
            }
            build.apply();
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
            int thick = Math.max(1, (int) Math.ceil(pitch));
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

        /** How high the roof of a dormer from {@code lo} to {@code hi} is over its eaves at {@code u} (a gable, out by one each side). */
        private int dormerRise(int u, int lo, int hi) {
            return (int) Math.floor(Math.min(u - (lo - 1), hi + 1 - u) * pitch);
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
         * A round tower of {@code width} across, out from the wall (by default half in it), from storey {@code level} up to {@code height} over the house's
         * eaves, with a floor at every storey, windows to the front and the sides and a steep cone of a roof; from a storey over the ground it hangs on the
         * wall on a corbel (a turret). Its inside opens into the house.
         */
        private void tower(Build p, HouseModule m) {
            int size = Math.max(3, m.width);
            int lo = -(size - 1) / 2;
            double cu = (lo + lo + size - 1) / 2.0;
            double cv = (1 + size) / 2.0;
            double radius = size / 2.0;
            int level = Math.max(0, Math.min(floors - 1, m.level));
            int foot = level * storey;
            int head = Math.max(foot + storey, top + m.height);
            TreePart wall = level == 0 ? TreePart.HOUSE_STONE : TreePart.HOUSE_WALL;
            int reach = (int) Math.ceil(radius) + 2;
            for (int u = lo - 2; u <= lo + size + 1; u++) {
                for (int v = -1; v <= size + 2; v++) {
                    double du = u - cu;
                    double dv = v - cv;
                    double d = Math.hypot(du, dv);
                    if (d <= radius) {
                        boolean ring = !inside(u + 1, v, cu, cv, radius) || !inside(u - 1, v, cu, cv, radius) || !inside(u, v + 1, cu, cv, radius)
                                || !inside(u, v - 1, cu, cv, radius) || !inside(u + 1, v + 1, cu, cv, radius) || !inside(u - 1, v - 1, cu, cv, radius)
                                || !inside(u + 1, v - 1, cu, cv, radius) || !inside(u - 1, v + 1, cu, cv, radius);
                        double angle = Math.atan2(du, dv);
                        boolean facing = Math.abs(angle) < 0.35 || Math.abs(Math.abs(angle) - Math.PI / 2.0) < 0.35;
                        for (int y = foot; y < head; y++) {
                            int inStorey = Math.floorMod(y, storey);
                            if (ring) {
                                boolean window = facing && storey >= 4 && inStorey >= 2 && inStorey <= storey - 2;
                                if (window) p.carve(u, v, y);
                                else p.put(u, v, y, wall);
                            } else if (inStorey == 0) {
                                p.put(u, v, y, TreePart.HOUSE_FLOOR);
                            } else {
                                p.carve(u, v, y);
                            }
                        }
                        // the corbel of a turret: rings narrowing down under it
                        if (level > 0) {
                            for (int k = 1; k <= reach; k++) {
                                double r = radius - k;
                                if (r < 0.5 || d > r || foot - k < 0) continue;
                                if (d > r - 1.0) p.put(u, v, foot - k, wall);
                            }
                        }
                    }
                    // the cone, one block out past the walls
                    double eaves = radius + 1.0;
                    if (d <= eaves) {
                        double steep = Math.max(1.5, pitch * 2.5);
                        int rise = (int) Math.floor((eaves - d) * steep);
                        int thick = (int) Math.ceil(steep);
                        for (int y = head + rise; y > head + rise - thick && y >= head; y--) p.put(u, v, y, TreePart.HOUSE_ROOF);
                    }
                }
            }
        }

        private static boolean inside(int u, int v, double cu, double cv, double radius) {
            return Math.hypot(u - cu, v - cv) <= radius;
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
            HousePlan plan = new HousePlan(w, d, storey, spacing, windows, false);
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
            }, roof, 2, pitch, eaves, lowA, w - 1 + overhang, lowB, d - 1 + overhang);
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < d; j++) {
                    int under = bottom[i - lowA][j - lowB];
                    boolean edge = i == 0 || i == w - 1 || j == d - 1;
                    for (int y = eaves; y < eaves + under; y++) {
                        if (edge) p.put(lo + i, j, y, plan.pillar(i, j) || timber && y == eaves ? TreePart.HOUSE_PILLAR : TreePart.HOUSE_WALL);
                        // (inside its roof: what of the house's eaves is there goes; the house's own wall on the line it joins stays)
                        else if (y > eaves && j > 0) p.carve(lo + i, j, y);
                    }
                    if (!edge && j > 0 && under > 0) p.put(lo + i, j, eaves, TreePart.HOUSE_FLOOR);
                }
            }
        }

        /** A porch: a floor out in front of the wall, posts at its outer edge and a roof sloping away from the house at {@code height}. */
        private void porch(Build p, HouseModule m) {
            int lo = -(m.width - 1) / 2;
            int hi = lo + m.width - 1;
            int d = m.depth;
            int h = Math.max(2, m.height);
            for (int u = lo; u <= hi; u++) {
                for (int v = 1; v <= d; v++) {
                    p.put(u, v, 0, TreePart.HOUSE_FLOOR);
                    boolean post = v == d && (u == lo || u == hi || (u - lo) % 4 == 0);
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
                    if (y >= 0) p.put(u, v, y, TreePart.HOUSE_PILLAR);
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
                if ((u == lo || u == hi) && foot - 1 > 0) p.put(u, 1, foot - 1, TreePart.HOUSE_PILLAR);
            }
            int from = w >= 3 ? lo + 1 : lo;
            int to = w >= 3 ? hi - 1 : hi;
            for (int u = from; u <= to; u++) {
                for (int y = foot + 1; y < foot + h; y++) p.carveWall(u, y);
            }
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
        private final Blocks over = new Blocks();
        private final Blocks under = new Blocks();
        private final Set<Long> carved = new HashSet<>();
        /** The highest block of each column of what is there (by {@link #column}). */
        private final Map<Long, Integer> tops = new HashMap<>();

        Build(Blocks out, Side side, int along, int offset) {
            this.out = out;
            this.side = side;
            this.along = along;
            this.offset = offset;
            for (long key : out.map.keySet()) {
                tops.merge(column(TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackZ(key)), TreeVoxelizer.unpackY(key), Math::max);
            }
        }

        private int a(int u, int v) {
            return side.a(along + u, offset + v);
        }

        private int b(int u, int v) {
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

        /** Takes away the block of the house's wall line (whatever the offset) at {@code u}. */
        void carveWall(int u, int y) {
            carved.add(TreeVoxelizer.pack(side.a(along + u, 0), y, side.b(along + u, 0)));
        }

        /** The highest block of the house in the column at {@code (u, v)}; {@link Integer#MIN_VALUE} when there is none. */
        int topAt(int u, int v) {
            Integer top = tops.get(column(a(u, v), b(u, v)));
            return top == null ? Integer.MIN_VALUE : top;
        }

        void apply() {
            for (long key : carved) out.map.remove(key);
            out.map.putAll(over.map);
            for (Map.Entry<Long, TreePart> entry : under.map.entrySet()) out.map.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private static long column(int a, int b) {
        return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
    }

    // ---- the roofs --------------------------------------------------------------------------------------------------------------------

    /**
     * Puts a roof of {@code type} over the rectangle from ({@code lowA}, {@code lowB}) to ({@code highA}, {@code highB}) (the eaves, already out by the
     * overhang), its eaves at {@code base}: each column gets roof down to one over its lowest neighbour, at most as thick as the steepest slope of the
     * shape needs; below that (a step of the roof: the gable of a hip-and-gable or of a clipped gable) wall. Returns how high over {@code base} the lowest
     * block of each column is (by {@code [a - lowA][b - lowB]}).
     */
    static int[][] roof(Sink sink, int type, int ridge, double pitch, int base, int lowA, int highA, int lowB, int highB) {
        int sizeA = highA - lowA + 1;
        int sizeB = highB - lowB + 1;
        int[][] rise = new int[sizeA][sizeB];
        for (int i = 0; i < sizeA; i++) {
            for (int j = 0; j < sizeB; j++) rise[i][j] = roofRise(type, ridge, lowA + i, lowB + j, lowA, highA, lowB, highB, pitch);
        }
        int thick = roofThickness(type, pitch);
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
                bottom[i][j] = low;
                for (int y = r; y >= low; y--) sink.put(lowA + i, base + y, lowB + j, r - y < thick ? TreePart.HOUSE_ROOF : TreePart.HOUSE_WALL);
            }
        }
        return bottom;
    }

    /**
     * How high the roof is over the eaves at {@code (a, b)} of the rectangle of the eaves: flat 0; a gable rises toward a ridge (along the longer side, or
     * as {@code ridge} says: 1 along the front, 2 across it), a hip toward the middle from all four sides, a shed from the front to the back (at half the
     * pitch; across with ridge 2), a gambrel (a barn: steep, then gentle near the ridge) and a mansard (the same from all four sides, near flat on top), a
     * hip-and-gable (a hip low at the ends, a small gable over it), a clipped gable (a gable whose top is hipped) and a curved roof (concave from all four
     * sides: the eaves flare out, the top is steep).
     */
    static int roofRise(int roof, int ridge, int a, int b, int lowA, int highA, int lowB, int highB, double pitch) {
        int acrossA = Math.min(a - lowA, highA - a);
        int acrossB = Math.min(b - lowB, highB - b);
        int spanA = highA - lowA;
        int spanB = highB - lowB;
        boolean alongA = ridge == 1 || ridge == 0 && spanA >= spanB;
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
            default -> (int) Math.floor(across * pitch);
        };
    }

    /** A slope that is {@code steep} up to {@code knee} in from the eaves and {@code gentle} on from there. */
    private static int broken(int t, int knee, double steep, double gentle) {
        return (int) Math.floor(t <= knee ? t * steep : knee * steep + (t - knee) * gentle);
    }

    /** How thick a roof must be so its steepest slope has no holes (seen from the side). */
    static int roofThickness(int roof, double pitch) {
        double slope = switch (roof) {
            case 3 -> pitch * 0.5;
            case 4 -> pitch * 2.0;
            case 5 -> pitch * 2.5;
            case 8 -> pitch * 2.25;
            default -> pitch;
        };
        return Math.max(1, (int) Math.ceil(slope - 1.0E-9));
    }

    /** Where the pillars and the openings of a storey are (in its own coordinates: {@code a} along the front, {@code b} from the back to the front). */
    record HousePlan(int width, int depth, int storey, int spacing, boolean windows, boolean door) {

        /** A pillar: a corner, or every {@code spacing} along a wall (counted from the corner). */
        boolean pillar(int a, int b) {
            boolean sideA = a == 0 || a == width - 1;
            boolean sideB = b == 0 || b == depth - 1;
            if (sideA && sideB) return true;
            if (spacing <= 0) return false;
            if (sideB) return a % spacing == 0;
            if (sideA) return b % spacing == 0;
            return false;
        }

        /** Whether the wall block at {@code (a, b)}, {@code level} over the floor of the storey, is an opening: the door (the middle of the front, 2 high), or a window. */
        boolean opening(int a, int b, int level) {
            boolean front = b == depth - 1;
            if (door && front && (level == 1 || level == 2) && storey >= 3 && a == doorAt()) return true;
            if (!windows || storey < 4 || level < 2 || level > storey - 2) return false;
            // the window of the stretch of wall between two pillars: in its middle, 1 wide (2 when the stretch is 5 or more)
            int[] panel = panel(a, b);
            if (panel == null) return false;
            int start = panel[0];
            int end = panel[1];
            int inside = end - start - 1;
            if (inside < 3) return false;
            if (front && door && doorAt() > start && doorAt() < end) return false;
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

        /** The block of the front wall the door is in: the middle (moved off a pillar). */
        int doorAt() {
            int middle = (width - 1) / 2;
            return pillar(middle, depth - 1) ? middle + 1 : middle;
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
