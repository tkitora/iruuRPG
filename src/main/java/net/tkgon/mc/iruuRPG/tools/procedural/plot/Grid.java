package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (t313) The building while it is made: a role-tagged block ({@link Cell}) by position, in the site's own coordinates (x along the front from its left, y up from
 * the ground, z from the back toward the front). {@link #put} keeps the stronger role where two meet; {@link #set} and {@link #clear} are for what must be there
 * (or not) whatever was. Each block knows the lot it was put for ({@link #owner}). {@link #finish} works out what the game would of the blocks next to one
 * another: a log's axis, a stair's corner shape, the arms of walls and fences. There is a limit on the blocks, so settings far too big stop ({@link TooBig}).
 */
public final class Grid {

    /** More blocks than this and the building is given up. */
    public static final int LIMIT = 400_000;

    /** A building with more blocks than {@link #LIMIT}. */
    public static final class TooBig extends RuntimeException {
        TooBig() {
            super("more than " + LIMIT + " blocks");
        }
    }

    private final Map<Long, Cell> cells = new HashMap<>();
    private final Map<Long, Integer> owners = new HashMap<>();
    /** The lot the blocks put now are for (-1: none). */
    public int owner = -1;

    public static long pack(int x, int y, int z) {
        return ((long) (x + 2048) & 0xFFFFL) | (((long) (y + 2048) & 0xFFFFL) << 16) | (((long) (z + 2048) & 0xFFFFL) << 32);
    }

    public static int x(long key) {
        return (int) (key & 0xFFFFL) - 2048;
    }

    public static int y(long key) {
        return (int) ((key >> 16) & 0xFFFFL) - 2048;
    }

    public static int z(long key) {
        return (int) ((key >> 32) & 0xFFFFL) - 2048;
    }

    public Map<Long, Cell> cells() {
        return cells;
    }

    public Cell get(int x, int y, int z) {
        return cells.get(pack(x, y, z));
    }

    public boolean has(int x, int y, int z) {
        return cells.containsKey(pack(x, y, z));
    }

    public int size() {
        return cells.size();
    }

    /** Puts {@code cell} unless a stronger role (or the same) is there. */
    public void put(int x, int y, int z, Cell cell) {
        if (cell == null) return;
        long key = pack(x, y, z);
        Cell old = cells.get(key);
        if (old == null || cell.role().priority() < old.role().priority()) store(key, cell);
    }

    /** Puts {@code cell} only where there is nothing. */
    public void fill(int x, int y, int z, Cell cell) {
        if (cell == null) return;
        long key = pack(x, y, z);
        if (!cells.containsKey(key)) store(key, cell);
    }

    /** Puts {@code cell} whatever is there (null: takes the block away). */
    public void set(int x, int y, int z, Cell cell) {
        if (cell == null) {
            clear(x, y, z);
            return;
        }
        store(pack(x, y, z), cell);
    }

    public void clear(int x, int y, int z) {
        long key = pack(x, y, z);
        cells.remove(key);
        owners.remove(key);
        literals.remove(key);
    }

    /** The blocks of templates put as the game's blocks themselves (their states), by position: the cell there holds the place. */
    private final Map<Long, String> literals = new HashMap<>();

    /** A template's block as the game's block itself (its state, {@code minecraft:lantern[hanging=true]}), over what is there. */
    public void literal(int x, int y, int z, String state) {
        if (state == null || state.isBlank()) return;
        long key = pack(x, y, z);
        store(key, Cell.full(Role.STONE));
        literals.put(key, state);
    }

    /** The state of a template's block at {@code key}, or null. */
    public String literalAt(long key) {
        return literals.get(key);
    }

    private void store(long key, Cell cell) {
        cells.put(key, cell);
        literals.remove(key);
        if (owner >= 0) owners.put(key, owner);
        else owners.remove(key);
        if (cells.size() > LIMIT) throw new TooBig();
    }

    /** The lot a block was put for (-1: none). */
    public int ownerOf(long key) {
        return owners.getOrDefault(key, -1);
    }

    /** Copies every block of {@code other} in (keeping their owners), over what is here by role as {@link #put} does. */
    public void putAll(Grid other) {
        int keep = owner;
        for (Map.Entry<Long, Cell> entry : other.cells.entrySet()) {
            long key = entry.getKey();
            owner = other.ownerOf(key);
            String state = other.literalAt(key);
            if (state != null) literal(x(key), y(key), z(key), state);
            else put(x(key), y(key), z(key), entry.getValue());
        }
        owner = keep;
    }

    /** The box of the blocks: {minX, minY, minZ, maxX, maxY, maxZ}; null when there are none. */
    public int[] bounds() {
        if (cells.isEmpty()) return null;
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (long key : cells.keySet()) {
            int[] at = {x(key), y(key), z(key)};
            for (int axis = 0; axis < 3; axis++) {
                box[axis] = Math.min(box[axis], at[axis]);
                box[axis + 3] = Math.max(box[axis + 3], at[axis]);
            }
        }
        return box;
    }

    /**
     * Works out what the game would of blocks next to one another, the blocks being put without its updates: a log left to be laid lies along the logs next to it
     * (standing when there are none); a stair left to be shaped takes the inner or outer corner the stairs before and behind it make; walls and fences are joined
     * to the walls, fences and full blocks beside them.
     */
    public void finish() {
        List<Long> keys = new ArrayList<>(cells.keySet());
        for (long key : keys) {
            Cell cell = cells.get(key);
            if (cell.form() == Cell.Form.LOG && cell.axis() < 0) cells.put(key, cell.withAxis(lay(x(key), y(key), z(key))));
        }
        for (long key : keys) {
            Cell cell = cells.get(key);
            if (cell.form() == Cell.Form.STAIRS && cell.shape() < 0) cells.put(key, cell.withShape(stairShape(x(key), y(key), z(key), cell)));
        }
        for (long key : keys) {
            Cell cell = cells.get(key);
            if (cell.form() != Cell.Form.WALL && cell.form() != Cell.Form.FENCE) continue;
            int joins = 0;
            for (int way = 0; way < 4; way++) {
                Cell next = get(x(key) + Cell.STEP[way][0], y(key), z(key) + Cell.STEP[way][1]);
                if (next != null && (next.form() == cell.form() || next.solid())) joins |= 1 << way;
            }
            cells.put(key, cell.withJoins(joins));
        }
    }

    /** The axis of a log to be laid: along x when a log is beside it that way, else along z, else standing. */
    private int lay(int x, int y, int z) {
        if (isLog(x - 1, y, z) || isLog(x + 1, y, z)) return 0;
        if (isLog(x, y, z - 1) || isLog(x, y, z + 1)) return 2;
        return 1;
    }

    private boolean isLog(int x, int y, int z) {
        Cell cell = get(x, y, z);
        return cell != null && cell.form() == Cell.Form.LOG && cell.axis() != 1;
    }

    /** The game's shape of a stair: an outer corner from the stair it faces, an inner corner from the stair behind it (see the game's StairBlock). */
    private int stairShape(int x, int y, int z, Cell cell) {
        int facing = cell.facing();
        Cell ahead = stairAt(x + Cell.STEP[facing][0], y, z + Cell.STEP[facing][1], cell.top());
        if (ahead != null && ahead.facing() % 2 != facing % 2 && canTake(x, y, z, cell, (ahead.facing() + 2) % 4)) {
            return ahead.facing() == (facing + 3) % 4 ? 3 : 4;
        }
        int back = (facing + 2) % 4;
        Cell behind = stairAt(x + Cell.STEP[back][0], y, z + Cell.STEP[back][1], cell.top());
        if (behind != null && behind.facing() % 2 != facing % 2 && canTake(x, y, z, cell, behind.facing())) {
            return behind.facing() == (facing + 3) % 4 ? 1 : 2;
        }
        return 0;
    }

    private Cell stairAt(int x, int y, int z, boolean top) {
        Cell cell = get(x, y, z);
        return cell != null && cell.form() == Cell.Form.STAIRS && cell.top() == top ? cell : null;
    }

    /** The game's check: the stair on that side is not one facing the same way in the same half. */
    private boolean canTake(int x, int y, int z, Cell cell, int side) {
        Cell next = get(x + Cell.STEP[side][0], y, z + Cell.STEP[side][1]);
        return next == null || next.form() != Cell.Form.STAIRS || next.facing() != cell.facing() || next.top() != cell.top();
    }
}
