package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * (t313) Draws a part of the kit on a face of the building: a wall panel between two posts, an arc of a round wall, a gable under its roof. The face is a
 * {@link Canvas}: {@code u} along it from the left (as one outside sees it), {@code v} up, {@code d} the depth (0 the frame: posts, beams, the timbers of the
 * pattern; -1 the wall: plaster or stone; -2 the glass of a window).
 */
public final class Painter {

    private Painter() {
    }

    /** A face of the building to draw on. */
    public interface Canvas {
        void put(int u, int v, int d, Cell cell);

        /** A block of a template put as the game's block itself (its state). */
        void literal(int u, int v, int d, String state);

        void clear(int u, int v, int d);

        /** The way out of the face (0 north, 1 east, 2 south, 3 west). */
        int out();

        /** The way along {@code u}. */
        int right();

        /** The axis of a log lying along {@code u} (0 x, 2 z; -1: worked out). */
        default int axis() {
            return right() % 2 == 1 ? 0 : 2;
        }
    }

    /**
     * A wall panel {@code w} wide and {@code h} rows high (its rows from the floor's top or from the sill beam to under the beam of its storey). {@code plaster}
     * is the plaster's colour (its role); {@code walk} the row a door stands on; {@code mirror} turns the lines over (the panels of the right half of a face
     * lean the other way, so the face is the same both ways).
     */
    public static void panel(Kit.Part part, Canvas c, int w, int h, Role plaster, int walk, boolean mirror) {
        if (part.kind != Kit.Kind.PROCEDURAL) {
            template(part, c, w, h);
            return;
        }
        Role fill = part.fill == Role.INFILL ? plaster : part.fill;
        for (int u = 0; u < w; u++) for (int v = 0; v < h; v++) c.put(u, v, -1, Cell.full(fill));
        // the opening: a window (in the middle, its glass back at -2 with the wall round it, a sill and a lintel in the frame), a door, a round window
        int[] hole = null;
        switch (part.opening) {
            case WINDOW -> {
                int ow = w <= 3 ? w : Math.max(2, Math.min(3, w - 2));
                int oh = h >= 6 ? 3 : h >= 3 ? 2 : 1;
                int u0 = (w - ow) / 2;
                int v0 = Math.max(h >= 4 ? 1 : 0, (h - oh) / 2);
                if (v0 + oh > h) v0 = Math.max(0, h - oh);
                hole = new int[]{u0, v0, u0 + ow - 1, v0 + oh - 1};
                window(c, hole, fill);
                for (int u = hole[0]; u <= hole[2]; u++) {
                    if (hole[1] - 1 >= 0) c.put(u, hole[1] - 1, 0, Cell.log(Role.BEAM, c.axis()));
                    if (hole[3] + 1 < h) c.put(u, hole[3] + 1, 0, Cell.log(Role.BEAM, c.axis()));
                }
            }
            case ROUND -> {
                int cu = (w - 1) / 2;
                int cv = Math.max(1, Math.min(h - 2, h / 2));
                hole = new int[]{cu - 1, cv - 1, cu + 1, cv + 1};
                for (int du = -1; du <= 1; du++) {
                    for (int dv = -1; dv <= 1; dv++) {
                        if (du != 0 && dv != 0) {
                            // (the corners of the round window: its frame, in steps)
                            c.put(cu + du, cv + dv, 0, Cell.stairs(Role.BEAM, dv > 0 ? c.out() : (c.out() + 2) % 4, dv > 0));
                            continue;
                        }
                        c.clear(cu + du, cv + dv, -1);
                        c.put(cu + du, cv + dv, -2, Cell.full(Role.WINDOW));
                    }
                }
            }
            case DOOR -> {
                int dw = w >= 5 ? 2 : 1;
                int u0 = (w - dw) / 2;
                int v0 = Math.max(0, Math.min(walk, h - 2));
                hole = new int[]{u0, v0, u0 + dw - 1, v0 + 1};
                for (int u = u0; u < u0 + dw; u++) {
                    for (int v = 0; v < v0; v++) c.put(u, v, -1, Cell.full(fill));
                    boolean right = dw == 2 && u == u0 + 1;
                    c.put(u, v0, -1, Cell.door(Role.DOOR, c.out(), false, right));
                    c.put(u, v0 + 1, -1, Cell.door(Role.DOOR, c.out(), true, right));
                    c.clear(u, v0, 0);
                    c.clear(u, v0 + 1, 0);
                    if (v0 + 2 < h) c.put(u, v0 + 2, 0, Cell.log(Role.BEAM, c.axis()));
                }
            }
            default -> {
            }
        }
        lines(part, c, w, h, hole, mirror, null);
    }

    /** A window's hole in the wall: open at -1, glass at -2, the wall round the glass at -2 (so the hole has sides). */
    static void window(Canvas c, int[] hole, Role fill) {
        for (int u = hole[0] - 1; u <= hole[2] + 1; u++) {
            for (int v = hole[1] - 1; v <= hole[3] + 1; v++) {
                boolean in = u >= hole[0] && u <= hole[2] && v >= hole[1] && v <= hole[3];
                if (in) {
                    c.clear(u, v, -1);
                    c.put(u, v, -2, Cell.full(Role.WINDOW));
                } else {
                    c.put(u, v, -2, Cell.full(fill));
                }
            }
        }
    }

    /**
     * The lines of a part drawn in the frame (depth 0) over a box {@code w} by {@code h}: each from its start to its end, as near the straight line as blocks
     * go. {@code room}, when given, is how many rows each column has (a gable's), and what is above is left out.
     */
    static void lines(Kit.Part part, Canvas c, int w, int h, int[] hole, boolean mirror, IntUnaryOperator room) {
        for (double[] line : part.lines) {
            double x0 = mirror ? 1.0 - line[0] : line[0];
            double x1 = mirror ? 1.0 - line[2] : line[2];
            int u0 = (int) Math.round(x0 * (w - 1));
            int v0 = (int) Math.round(line[1] * (h - 1));
            int u1 = (int) Math.round(x1 * (w - 1));
            int v1 = (int) Math.round(line[3] * (h - 1));
            int axis = u0 == u1 ? 1 : v0 == v1 ? c.axis() : 1;
            int du = Math.abs(u1 - u0);
            int dv = Math.abs(v1 - v0);
            int su = u0 < u1 ? 1 : -1;
            int sv = v0 < v1 ? 1 : -1;
            int err = du - dv;
            int u = u0;
            int v = v0;
            while (true) {
                boolean blocked = hole != null && u >= hole[0] - 1 && u <= hole[2] + 1 && v >= hole[1] - 1 && v <= hole[3] + 1;
                boolean under = room == null || v < room.applyAsInt(u);
                if (!blocked && under && u >= 0 && u < w && v >= 0 && v < h) c.put(u, v, 0, Cell.log(part.lineRole, axis));
                if (u == u1 && v == v1) break;
                int e2 = 2 * err;
                if (e2 > -dv) {
                    err -= dv;
                    u += su;
                }
                if (e2 < du) {
                    err += du;
                    v += sv;
                }
            }
        }
    }

    /**
     * A gable: {@code w} columns along the wall, column {@code u} having {@code room(u)} rows under its roof (from the eaves). Its fill at -1, its lines in the
     * frame, a window in the middle when it is high enough.
     */
    public static void gable(Kit.Part part, Canvas c, int w, IntUnaryOperator room, Role plaster) {
        int h = 0;
        for (int u = 0; u < w; u++) h = Math.max(h, room.applyAsInt(u));
        if (h <= 0) return;
        Role fill = part.fill == Role.INFILL ? plaster : part.fill;
        for (int u = 0; u < w; u++) for (int v = 0; v < room.applyAsInt(u); v++) c.put(u, v, -1, Cell.full(fill));
        int[] hole = null;
        if (h >= 5) {
            int cu = (w - 1) / 2;
            int v0 = Math.max(1, h / 3);
            int oh = h >= 8 ? 2 : 1;
            if (v0 + oh < room.applyAsInt(cu) - 1) {
                // (one column in the middle of an odd gable, the two middle ones of an even one)
                hole = new int[]{cu, v0, w % 2 == 0 ? cu + 1 : cu, v0 + oh - 1};
                window(c, hole, fill);
                for (int u = hole[0]; u <= hole[2]; u++) {
                    c.put(u, hole[1] - 1, 0, Cell.log(Role.BEAM, c.axis()));
                    if (hole[3] + 1 < room.applyAsInt(u)) c.put(u, hole[3] + 1, 0, Cell.log(Role.BEAM, c.axis()));
                }
            }
        }
        lines(part, c, w, h + 1, hole, false, room);
    }

    /**
     * A template (or a 9-slice) drawn to {@code w} by {@code h}: its blocks as they are, a 9-slice's middle columns and rows repeated to fill. Depth {@code d}
     * of the template (0 its front layer) is the face's depth {@code -d}.
     */
    public static void template(Kit.Part part, Canvas c, int w, int h) {
        int[] mapU = map(part.sizeU, w, part.kind == Kit.Kind.SLICE ? part.sliceU : new int[]{-1, -1});
        int[] mapV = map(part.sizeV, h, part.kind == Kit.Kind.SLICE ? part.sliceV : new int[]{-1, -1});
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < h; v++) {
                for (int d = 0; d < Math.max(1, part.sizeD); d++) {
                    Kit.Block block = part.blocks.get(Kit.Part.key(mapU[u], mapV[v], d));
                    if (block == null) continue;
                    if (block.role() == null) c.literal(u, v, -d, block.literal());
                    else c.put(u, v, -d, cell(block, c));
                }
            }
        }
    }

    /** For each place of the size wanted, the place of the template it shows: the ends as they are, the middle repeated. */
    static int[] map(int size, int wanted, int[] slice) {
        int[] out = new int[wanted];
        if (slice[0] < 0 || slice[1] < slice[0] || size <= 0) {
            for (int i = 0; i < wanted; i++) out[i] = Math.min(size - 1, i);
            return out;
        }
        int head = slice[0];
        int tail = size - 1 - slice[1];
        int middle = slice[1] - slice[0] + 1;
        for (int i = 0; i < wanted; i++) {
            if (i < head) out[i] = i;
            else if (i >= wanted - tail) out[i] = size - (wanted - i);
            else out[i] = slice[0] + Math.floorMod(i - head, middle);
        }
        return out;
    }

    /** The cell of a template's block in a role, turned to the face (its facing given out of the template's front: 0 toward the viewer). */
    static Cell cell(Kit.Block block, Canvas c) {
        String props = block.props() == null ? "" : block.props();
        int facing = c.out();
        Map<String, String> states = parse(props);
        String way = states.get("facing");
        if (way != null) {
            // (in a template, "south" is out of its front, "east" to the right: turned to the face)
            int local = switch (way) {
                case "north" -> 2;
                case "east" -> 1;
                case "west" -> 3;
                default -> 0;
            };
            facing = switch (local) {
                case 1 -> c.right();
                case 2 -> (c.out() + 2) % 4;
                case 3 -> (c.right() + 2) % 4;
                default -> c.out();
            };
        }
        boolean top = "top".equals(states.get("half")) || "top".equals(states.get("type")) || "upper".equals(states.get("half"));
        return switch (block.form()) {
            case STAIRS -> Cell.stairs(block.role(), facing, top);
            case SLAB -> Cell.slab(block.role(), top);
            case LOG -> Cell.log(block.role(), "y".equals(states.get("axis")) ? 1 : states.containsKey("axis") ? c.axis() : -1);
            case WALL -> Cell.wall(block.role());
            case FENCE -> Cell.fence(block.role());
            case TRAPDOOR -> Cell.trapdoor(block.role(), facing, top, "true".equals(states.get("open")));
            case ROD -> Cell.rod(block.role());
            case DOOR -> Cell.door(block.role(), facing, top, false);
            case LADDER -> Cell.ladder(block.role(), facing);
            case BELL -> Cell.bell(block.role(), facing);
            default -> Cell.full(block.role());
        };
    }

    static Map<String, String> parse(String props) {
        Map<String, String> out = new java.util.HashMap<>();
        for (String pair : props.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) out.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return out;
    }
}
