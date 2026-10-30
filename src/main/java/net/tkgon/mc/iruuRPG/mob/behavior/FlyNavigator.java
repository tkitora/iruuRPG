package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Moves a flying mob (`navigation: fly`) in three dimensions, inside its room ({@link Volume}): a straight line when nothing is in the way, else a path found on the blocks
 * (A* over the air cells the mob's box fits in, up, down and across, without cutting corners), pulled tight into as few straight legs as the blocks allow. The mob has no
 * gravity and the vanilla path finder is not used: it is steered every tick by its velocity, turned to the way it flies (or to the target it keeps an eye on).
 */
public final class FlyNavigator {

    /** How many cells one search may look at (a path that needs more is cut short at the cell nearest to the goal). */
    private static final int BUDGET = 3000;
    /** The same for the searches of the queries (EQS `pathable`), which only need to know whether a place can be reached. */
    private static final int CHECK_BUDGET = 1200;
    private static final int[][] STEPS = steps();

    private final Mob mob;
    private final Location home;
    private final Volume volume;
    private final double blocksPerTick;
    private List<Vector> path;
    private int index;
    private double speed = 1.0;
    private Location goal;
    private Entity facing;

    /** {@code volume} may be null (anywhere); {@code blocksPerSecond} is the pace at speed 1. */
    public FlyNavigator(Mob mob, Location home, Volume volume, double blocksPerSecond) {
        this.mob = mob;
        this.home = home.clone();
        this.volume = volume;
        this.blocksPerTick = Math.max(0.02, blocksPerSecond / 20.0);
    }

    public Volume volume() {
        return volume;
    }

    /** Flies toward {@code at} (moved into the room when it is outside: a target that left the room is followed to its edge); false when there is no way. */
    public boolean moveTo(Location at, double speed) {
        if (at == null || at.getWorld() != mob.getWorld()) return false;
        this.speed = speed;
        Location want = volume == null ? at.clone() : volume.clamp(home, at);
        Map<Long, Boolean> cache = new HashMap<>();
        Location spot = fitNear(want, cache);
        if (spot == null) return false;
        Vector from = mob.getLocation().toVector();
        if (clear(from, spot.toVector(), cache)) {
            path = new ArrayList<>(List.of(spot.toVector()));
            index = 0;
            goal = want;
            return true;
        }
        // the way is blocked: keep the path while the goal stays where it was
        if (path != null && goal != null && goal.getWorld() == want.getWorld() && goal.distanceSquared(want) < 1.0) return true;
        List<Vector> found = search(mob.getLocation(), spot, cache, BUDGET, true);
        if (found == null || found.isEmpty()) return false;
        path = found;
        index = 0;
        goal = want;
        return true;
    }

    /** Whether the mob could fly to {@code at} (inside its room, a place it fits, a way there). */
    public boolean canReach(Location at) {
        if (at == null || at.getWorld() != mob.getWorld()) return false;
        if (volume != null && !volume.contains(home, at)) return false;
        Map<Long, Boolean> cache = new HashMap<>();
        Location spot = fitNear(at, cache);
        if (spot == null) return false;
        if (clear(mob.getLocation().toVector(), spot.toVector(), cache)) return true;
        return search(mob.getLocation(), spot, cache, CHECK_BUDGET, false) != null;
    }

    /** A place at or right round {@code at} (a block or two up or down, then aside) where the mob fits, inside its room; null when there is none. */
    public Location airSpot(Location at) {
        if (at == null || at.getWorld() != mob.getWorld()) return null;
        return fitNear(at, new HashMap<>());
    }

    /** After a stop, the speed left over from the flight is braked away for this many ticks (see {@link #coast}). */
    private static final int BRAKE_TICKS = 8;
    /** The velocity the mob had after the last brake (null: nothing to brake). */
    private Vector coasting;
    private int brakeTicks;

    public void stop() {
        if (path != null) {
            Vector left = mob.getVelocity().multiply(0.3);
            mob.setVelocity(left);
            coasting = left.clone();
            brakeTicks = BRAKE_TICKS;
        }
        path = null;
        goal = null;
    }

    /**
     * With no path, the speed left from the flight it stopped would carry it on (a dive that missed would slide on
     * for blocks): it is halved each tick for a few ticks. A push from something else (a knockback, a skill's lunge)
     * changes the velocity more than the air does, and is left alone.
     */
    private void coast() {
        if (brakeTicks <= 0 || coasting == null) return;
        Vector now = mob.getVelocity();
        if (now.distanceSquared(coasting) > 0.04) {
            brakeTicks = 0;
            coasting = null;
            return;
        }
        now.multiply(0.5);
        mob.setVelocity(now);
        coasting = now.clone();
        brakeTicks--;
    }

    public boolean hasPath() {
        return path != null && index < path.size();
    }

    /** Keeps the mob turned to {@code target} while it flies (null: it turns to the way it goes again). */
    public void face(Entity target) {
        facing = target;
    }

    /** The points still ahead, from where the mob is (for the debug view). */
    public List<Vector> remaining() {
        if (path == null) return List.of();
        List<Vector> list = new ArrayList<>();
        list.add(mob.getLocation().toVector());
        for (int at = index; at < path.size(); at++) list.add(path.get(at).clone());
        return list;
    }

    /** One tick: no gravity, and the velocity toward the next point of the path. {@code frozen}: the mob does not move now (it flinches, a skill holds it). */
    public void tick(boolean frozen) {
        if (!mob.isValid()) return;
        if (mob.hasGravity()) mob.setGravity(false);
        if (frozen) return;
        Vector here = mob.getLocation().toVector();
        if (path == null) {
            coast();
            turnToFacing();
            return;
        }
        while (index < path.size()) {
            double near = index == path.size() - 1 ? 0.35 : 0.8;
            if (path.get(index).distance(here) > near) break;
            index++;
        }
        if (index >= path.size()) {
            stop();
            turnToFacing();
            return;
        }
        Vector way = path.get(index).clone().subtract(here);
        double distance = way.length();
        double pace = blocksPerTick * speed;
        // it slows down for the last point instead of overshooting it
        if (index == path.size() - 1) pace = Math.min(pace, Math.max(0.04, distance * 0.35));
        Vector want = way.multiply(pace / Math.max(1.0E-6, distance));
        mob.setVelocity(mob.getVelocity().multiply(0.35).add(want.clone().multiply(0.65)));
        if (!turnToFacing() && want.getX() * want.getX() + want.getZ() * want.getZ() > 1.0E-4) {
            float yaw = (float) Math.toDegrees(Math.atan2(-want.getX(), want.getZ()));
            mob.setRotation(yaw, 0.0f);
            mob.setBodyYaw(yaw);
        }
    }

    /** Turns to the target it keeps an eye on; false when it has none. */
    private boolean turnToFacing() {
        if (facing == null || !facing.isValid() || facing.getWorld() != mob.getWorld()) return false;
        double eye = facing instanceof LivingEntity living ? living.getEyeHeight() : facing.getHeight() * 0.85;
        Vector direction = facing.getLocation().add(0.0, eye, 0.0).toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() < 1.0E-6) return true;
        Location look = mob.getLocation();
        look.setDirection(direction);
        mob.setRotation(look.getYaw(), look.getPitch());
        mob.setBodyYaw(look.getYaw());
        return true;
    }

    // ---- the blocks -------------------------------------------------------------------------------------------------

    /** How high {@code at} is above the first solid block under it (up to {@code max}; {@code max} when there is none). */
    public static double heightAboveGround(Location at, int max) {
        World world = at.getWorld();
        if (world == null) return max;
        int x = at.getBlockX();
        int z = at.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return max;
        for (int dy = 0; dy <= max; dy++) {
            int y = at.getBlockY() - dy;
            if (y < world.getMinHeight()) return max;
            if (world.getBlockAt(x, y, z).getType().isSolid()) return Math.max(0.0, at.getY() - (y + 1));
        }
        return max;
    }

    private boolean free(int x, int y, int z, Map<Long, Boolean> cache) {
        long key = key(x, y, z);
        Boolean known = cache.get(key);
        if (known != null) return known;
        World world = mob.getWorld();
        boolean free;
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
            free = false;
        } else {
            Block block = world.getBlockAt(x, y, z);
            free = block.isPassable() && !block.isLiquid();
        }
        cache.put(key, free);
        return free;
    }

    /** Whether the mob's box, its feet at {@code (x, y, z)}, is in air only. */
    private boolean fitsBody(double x, double y, double z, Map<Long, Boolean> cache) {
        double half = mob.getWidth() / 2.0;
        int x0 = (int) Math.floor(x - half);
        int x1 = (int) Math.floor(x + half - 1.0E-3);
        int z0 = (int) Math.floor(z - half);
        int z1 = (int) Math.floor(z + half - 1.0E-3);
        int y0 = (int) Math.floor(y);
        int y1 = (int) Math.floor(y + mob.getHeight() - 1.0E-3);
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int by = y0; by <= y1; by++) if (!free(bx, by, bz, cache)) return false;
            }
        }
        return true;
    }

    /** Whether the mob fits with its feet in the middle of the bottom of block {@code (x, y, z)}. */
    private boolean fitsCell(int x, int y, int z, Map<Long, Boolean> cache) {
        return fitsBody(x + 0.5, y, z + 0.5, cache);
    }

    private boolean inside(double x, double y, double z) {
        return volume == null || volume.contains(home, x, y, z);
    }

    /** Whether the mob can fly straight from {@code a} to {@code b} (its box checked every 0.3 blocks of the way). */
    private boolean clear(Vector a, Vector b, Map<Long, Boolean> cache) {
        Vector way = b.clone().subtract(a);
        double length = way.length();
        if (length < 1.0E-6) return true;
        int steps = (int) Math.ceil(length / 0.3);
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            if (!fitsBody(a.getX() + way.getX() * t, a.getY() + way.getY() * t, a.getZ() + way.getZ() * t, cache)) return false;
        }
        return true;
    }

    /** {@code at} if the mob fits there, else the nearest cell round it that it fits in (up first: a target on the ground is met from above); inside the room. */
    private Location fitNear(Location at, Map<Long, Boolean> cache) {
        if (fitsBody(at.getX(), at.getY(), at.getZ(), cache) && inside(at.getX(), at.getY(), at.getZ())) return at.clone();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        for (int dy : new int[]{0, 1, 2, -1, 3, -2}) {
            if (fitsCell(x, y + dy, z, cache) && inside(x + 0.5, y + dy, z + 0.5)) return new Location(at.getWorld(), x + 0.5, y + dy, z + 0.5);
        }
        for (int dy : new int[]{0, 1, 2, -1}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && fitsCell(x + dx, y + dy, z + dz, cache) && inside(x + dx + 0.5, y + dy, z + dz + 0.5)) {
                        return new Location(at.getWorld(), x + dx + 0.5, y + dy, z + dz + 0.5);
                    }
                }
            }
        }
        return null;
    }

    private record Cell(int x, int y, int z, double g, double f, Cell parent) {
    }

    /**
     * A* from the mob's cell to the cell of {@code spot} over the cells it fits in, inside the room, within {@code budget} cells. With {@code partial}, a search that runs out
     * goes to the cell nearest to the goal (if it is at least 2 blocks nearer than the start); without, it gives null. The path is pulled tight: the points where it turns.
     */
    private List<Vector> search(Location start, Location spot, Map<Long, Boolean> cache, int budget, boolean partial) {
        int sx = start.getBlockX();
        int sy = (int) Math.floor(start.getY() + 0.01);
        int sz = start.getBlockZ();
        int gx = spot.getBlockX();
        int gy = (int) Math.floor(spot.getY() + 0.01);
        int gz = spot.getBlockZ();
        PriorityQueue<Cell> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Map<Long, Double> bestG = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        Cell first = new Cell(sx, sy, sz, 0.0, distance(sx, sy, sz, gx, gy, gz), null);
        open.add(first);
        bestG.put(key(sx, sy, sz), 0.0);
        Cell best = first;
        double bestH = first.f();
        boolean reached = false;
        int looked = 0;
        while (!open.isEmpty() && looked < budget) {
            Cell cell = open.poll();
            if (!closed.add(key(cell.x(), cell.y(), cell.z()))) continue;
            looked++;
            double h = distance(cell.x(), cell.y(), cell.z(), gx, gy, gz);
            if (h < bestH) {
                best = cell;
                bestH = h;
            }
            if (cell.x() == gx && cell.y() == gy && cell.z() == gz) {
                best = cell;
                reached = true;
                break;
            }
            for (int[] step : STEPS) {
                int nx = cell.x() + step[0];
                int ny = cell.y() + step[1];
                int nz = cell.z() + step[2];
                long nk = key(nx, ny, nz);
                if (closed.contains(nk)) continue;
                if (!fitsCell(nx, ny, nz, cache) || !inside(nx + 0.5, ny, nz + 0.5)) continue;
                // no corner cutting: every straight part of a slanted step must be free too
                if (step[3] > 1 && ((step[0] != 0 && !fitsCell(cell.x() + step[0], cell.y(), cell.z(), cache))
                        || (step[1] != 0 && !fitsCell(cell.x(), cell.y() + step[1], cell.z(), cache))
                        || (step[2] != 0 && !fitsCell(cell.x(), cell.y(), cell.z() + step[2], cache)))) continue;
                double g = cell.g() + Math.sqrt(step[3]);
                Double old = bestG.get(nk);
                if (old != null && old <= g) continue;
                bestG.put(nk, g);
                open.add(new Cell(nx, ny, nz, g, g + distance(nx, ny, nz, gx, gy, gz) * 1.1, cell));
            }
        }
        if (!reached && (!partial || bestH > first.f() - 2.0)) return null;
        List<Vector> cells = new ArrayList<>();
        for (Cell cell = best; cell != null && cell.parent() != null; cell = cell.parent()) cells.add(new Vector(cell.x() + 0.5, cell.y(), cell.z() + 0.5));
        Collections.reverse(cells);
        if (reached) {
            if (cells.isEmpty()) cells.add(spot.toVector());
            else cells.set(cells.size() - 1, spot.toVector());
        }
        if (cells.isEmpty()) return null;
        // pulled tight: from each point, straight to the farthest later point in sight
        List<Vector> tight = new ArrayList<>();
        Vector anchor = start.toVector();
        int at = 0;
        while (at < cells.size()) {
            int far = at;
            for (int next = cells.size() - 1; next > at; next--) {
                if (clear(anchor, cells.get(next), cache)) {
                    far = next;
                    break;
                }
            }
            tight.add(cells.get(far));
            anchor = cells.get(far);
            at = far + 1;
        }
        return tight;
    }

    private static double distance(int x, int y, int z, int gx, int gy, int gz) {
        double dx = x - gx;
        double dy = y - gy;
        double dz = z - gz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) ((y + 2048) & 0xFFF);
    }

    /** The 26 steps to the cells round a cell: {dx, dy, dz, how many of the three are not 0}. */
    private static int[][] steps() {
        List<int[]> list = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int moved = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (moved > 0) list.add(new int[]{dx, dy, dz, moved});
                }
            }
        }
        return list.toArray(new int[0][]);
    }
}
