package net.tkgon.mc.iruuRPG.tools.procedural.building;

import net.tkgon.mc.iruuRPG.tools.procedural.ornate.BlockShapes;

/**
 * (t295) One block of a building: its role, the shape its block comes in, and that shape's states, given outright (a stair's way and shape, a log's axis): the
 * blocks are put without the game working them out. Ways are 0 north (-z), 1 east (+x), 2 south (+z), 3 west (-x); a stair faces its high side.
 *
 * @param facing a stair's or a trapdoor's way
 * @param top    a stair or a trapdoor in the upper half; a slab of the upper half
 * @param shape  a stair's shape (0 straight, 1 inner_left, 2 inner_right, 3 outer_left, 4 outer_right; -1: worked out from the stairs round it when the building is
 *               finished, as the game would)
 * @param axis   a log's axis (0 x, 1 y, 2 z; -1: along the logs next to it, when the building is finished)
 * @param joins  the sides a wall or a fence is joined to (bit 0 north, 1 east, 2 south, 3 west; worked out when the building is finished)
 * @param open   a trapdoor standing open
 */
public record Cell(Role role, Form form, int facing, boolean top, int shape, int axis, int joins, boolean open) {

    /** The shapes a role's block comes in. */
    public enum Form {
        FULL, STAIRS, SLAB, LOG, WALL, FENCE, TRAPDOOR, ROD
    }

    static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final String[] WAYS = {"north", "east", "south", "west"};
    private static final String[] SHAPES = {"straight", "inner_left", "inner_right", "outer_left", "outer_right"};

    public static Cell full(Role role) {
        return new Cell(role, Form.FULL, 0, false, 0, 1, 0, false);
    }

    /** A log along {@code axis} (-1: along the logs next to it). */
    public static Cell log(Role role, int axis) {
        return new Cell(role, Form.LOG, 0, false, 0, axis, 0, false);
    }

    /** A stair facing {@code facing}, its shape worked out from the stairs round it. */
    public static Cell stairs(Role role, int facing, boolean top) {
        return new Cell(role, Form.STAIRS, Math.floorMod(facing, 4), top, -1, 1, 0, false);
    }

    public static Cell stairs(Role role, int facing, boolean top, int shape) {
        return new Cell(role, Form.STAIRS, Math.floorMod(facing, 4), top, shape, 1, 0, false);
    }

    public static Cell slab(Role role, boolean top) {
        return new Cell(role, Form.SLAB, 0, top, 0, 1, 0, false);
    }

    public static Cell wall(Role role) {
        return new Cell(role, Form.WALL, 0, false, 0, 1, 0, false);
    }

    public static Cell fence(Role role) {
        return new Cell(role, Form.FENCE, 0, false, 0, 1, 0, false);
    }

    public static Cell trapdoor(Role role, int facing, boolean top, boolean open) {
        return new Cell(role, Form.TRAPDOOR, Math.floorMod(facing, 4), top, 0, 1, 0, open);
    }

    public static Cell rod(Role role) {
        return new Cell(role, Form.ROD, 0, false, 0, 1, 0, false);
    }

    /** The block of an octant shape ({@link BlockShapes}: empty, full, a slab or a stair) in {@code role}; null for the empty one. */
    public static Cell of(Role role, BlockShapes.Shape shape) {
        return switch (shape.kind) {
            case FULL -> full(role);
            case SLAB -> slab(role, shape.props.contains("top"));
            case STAIRS -> {
                int facing = 0;
                for (int way = 0; way < 4; way++) if (shape.props.contains("facing=" + WAYS[way])) facing = way;
                int form = 0;
                for (int index = 0; index < SHAPES.length; index++) if (shape.props.contains("shape=" + SHAPES[index])) form = index;
                yield stairs(role, facing, shape.props.contains("half=top"), form);
            }
            default -> null;
        };
    }

    public Cell withRole(Role other) {
        return new Cell(other, form, facing, top, shape, axis, joins, open);
    }

    Cell withShape(int value) {
        return new Cell(role, form, facing, top, value, axis, joins, open);
    }

    Cell withAxis(int value) {
        return new Cell(role, form, facing, top, shape, value, joins, open);
    }

    Cell withJoins(int value) {
        return new Cell(role, form, facing, top, shape, axis, value, open);
    }

    /** Whether the block fills its whole cube (a wall or a fence joins to it). */
    public boolean solid() {
        return form == Form.FULL || form == Form.LOG;
    }

    /** The octants the block fills (bit x + 2y + 4z, as {@link BlockShapes}); a wall, a fence, a trapdoor and a rod fill none. */
    public int octants() {
        return switch (form) {
            case FULL, LOG -> 0xFF;
            case SLAB -> top ? BlockShapes.SLAB_TOP.octants : BlockShapes.SLAB_BOTTOM.octants;
            case STAIRS -> BlockShapes.stairs(top ? 1 : 0, facing, Math.max(0, shape)).octants;
            default -> 0;
        };
    }

    /** The block turned {@code turns} quarters clockwise seen from above (north to east). */
    public Cell turned(int turns) {
        int k = Math.floorMod(turns, 4);
        if (k == 0) return this;
        int newAxis = axis == 1 || axis < 0 || k % 2 == 0 ? axis : 2 - axis;
        int newJoins = ((joins << k) | (joins >> (4 - k))) & 0xF;
        return new Cell(role, form, (facing + k) % 4, top, shape, newAxis, newJoins, open);
    }

    /** The block's states as the game writes them ({@code facing=north,half=bottom,shape=straight}); empty for a full block. */
    public String props() {
        return switch (form) {
            case FULL -> "";
            case STAIRS -> "facing=" + WAYS[facing] + ",half=" + (top ? "top" : "bottom") + ",shape=" + SHAPES[Math.max(0, shape)];
            case SLAB -> "type=" + (top ? "top" : "bottom");
            case LOG -> "axis=" + (axis == 0 ? "x" : axis == 2 ? "z" : "y");
            case WALL -> {
                StringBuilder text = new StringBuilder();
                for (int way = 0; way < 4; way++) text.append(WAYS[way]).append('=').append((joins >> way & 1) != 0 ? "low" : "none").append(',');
                yield text.append("up=").append(wallUp()).toString();
            }
            case FENCE -> {
                StringBuilder text = new StringBuilder();
                for (int way = 0; way < 4; way++) text.append(way == 0 ? "" : ",").append(WAYS[way]).append('=').append((joins >> way & 1) != 0);
                yield text.toString();
            }
            case TRAPDOOR -> "facing=" + WAYS[facing] + ",half=" + (top ? "top" : "bottom") + ",open=" + open;
            case ROD -> "facing=up";
        };
    }

    /** A wall's post shows unless the wall runs straight through (joined on two opposite sides only). */
    private boolean wallUp() {
        return !(joins == 0b0101 || joins == 0b1010);
    }
}
