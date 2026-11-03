package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.HouseShape.House;
import net.tkgon.mc.iruuRPG.tools.procedural.HouseShape.Side;

/**
 * (t246) Pointing at a house in the world with the part wand: the block the player looks at (the preview's, which only that player sees, or the world's), far
 * off too, and a part put, moved or stretched to the place pointed at. A part's place is kept as the house keeps it (a side, along it, out from its wall), so the
 * point is taken to the side of the house it is nearest, and each kind of part is fitted there the way it joins the house (a tower with its middle on the point,
 * a volume touching the wall, a dormer on the slope, a balcony at the storey pointed at). Arithmetic only, for the tests.
 */
final class HousePointer {

    private HousePointer() {
    }

    /** Whether a block is solid for the ray (in the world's coordinates). */
    interface Solid {
        boolean at(int x, int y, int z);
    }

    /** A block the ray hit, the empty block just before it (where something put against it goes), and whether it is the preview's. */
    record Hit(int x, int y, int z, int beforeX, int beforeY, int beforeZ, boolean preview) {
    }

    /**
     * The first block of {@code preview} or {@code world} on the ray from {@code (ox, oy, oz)} along {@code (dx, dy, dz)} within {@code reach} blocks (the
     * block the eye is in is not one), or null; a voxel walk (Amanatides and Woo), so no block on the way is missed.
     */
    static Hit ray(double ox, double oy, double oz, double dx, double dy, double dz, double reach, Solid preview, Solid world) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-9) return null;
        dx /= length;
        dy /= length;
        dz /= length;
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tMaxX = boundary(ox, x, dx);
        double tMaxY = boundary(oy, y, dy);
        double tMaxZ = boundary(oz, z, dz);
        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
        double tDeltaZ = dz == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);
        while (true) {
            int px = x;
            int py = y;
            int pz = z;
            double t;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                t = tMaxX;
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                t = tMaxY;
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                t = tMaxZ;
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (t > reach) return null;
            if (preview != null && preview.at(x, y, z)) return new Hit(x, y, z, px, py, pz, true);
            if (world != null && world.at(x, y, z)) return new Hit(x, y, z, px, py, pz, false);
        }
    }

    /** How far along the ray the first boundary of the block is on one axis. */
    private static double boundary(double origin, int block, double direction) {
        if (direction > 0) return (block + 1 - origin) / direction;
        if (direction < 0) return (origin - block) / -direction;
        return Double.POSITIVE_INFINITY;
    }

    /** A point of the house's own coordinates ({@code a}, {@code b}) seen from a side: along it ({@code u}) and out from its wall ({@code v}); the inverse of {@link Side#a}, {@link Side#b}. */
    static double[] uv(Side side, double a, double b) {
        return switch (side.index()) {
            case 1 -> new double[]{side.middle() - b, a - side.wall()};
            case 2 -> new double[]{side.middle() - a, side.wall() - b};
            case 3 -> new double[]{b - side.middle(), side.wall() - a};
            default -> new double[]{a - side.middle(), b - side.wall()};
        };
    }

    /** The side of the house a point is nearest to at storey {@code k}: the one it is farthest out of (for a point inside, the wall nearest it). */
    static int nearestSide(House house, double a, double b, int k) {
        int best = 0;
        double far = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < 4; index++) {
            double v = uv(house.side(index, k), a, b)[1];
            if (v > far + 1.0E-9) {
                far = v;
                best = index;
            }
        }
        return best;
    }

    /** The storey a height over the ground floor is in, from 0 to {@code most}. */
    private static int storey(House house, double y, int least, int most) {
        return Math.max(least, Math.min(most, (int) Math.floor(y / Math.max(1, house.storey))));
    }

    /**
     * Puts part {@code m} at the point {@code (a, y, b)} of the house (its own coordinates, {@code y} over its ground floor): on the side nearest to it, along
     * that side where the point is, and out from the wall as the kind joins the house: a tower with its middle on the point (from the storey pointed at: a
     * turret over the ground), a volume touching the wall (its near wall on the wall line or inside it) from the storey pointed at, a chimney against the
     * wall (or inside it, when the point is inside), a balcony, a bay or stairs at the storey pointed at, the rest on the wall. Its size is kept.
     */
    static void put(House house, HouseModule m, double a, double y, double b) {
        int k = storey(house, y, 0, house.floors - 1);
        switch (m.type) {
            case TOWER, BAY -> m.level = k;
            case VOLUME -> m.level = storey(house, y, 0, house.floors);
            case BALCONY, STAIR -> m.level = storey(house, y, 1, Math.max(1, house.floors - 1));
            default -> {
            }
        }
        m.side = nearestSide(house, a, b, house.storeyOf(m));
        double[] uv = uv(house.side(m.side, house.storeyOf(m)), a, b);
        int u = (int) Math.round(uv[0]);
        int v = (int) Math.round(uv[1]);
        m.along = u;
        m.offset = switch (m.type) {
            case TOWER -> v - (1 + Math.max(3, m.width)) / 2;
            case VOLUME -> {
                int d = Math.max(3, m.depth);
                yield Math.max(-(d - 1), Math.min(0, v - d / 2));
            }
            case CHIMNEY -> v >= 0 ? 0 : Math.min(-Math.max(1, m.depth), v - (m.depth + 1) / 2);
            default -> 0;
        };
        m.limit();
    }

    /**
     * Stretches part {@code m} to the point {@code (a, y, b)}: a box (a volume, a wing, a porch, a balcony, a bay, a dormer, a chimney) keeps the end of its
     * width away from the point and reaches to it along its side, and out from (into, for a dormer) the wall as far as the point; a tower keeps its middle and
     * is as wide as twice the way to the point.
     */
    static void stretch(House house, HouseModule m, double a, double y, double b) {
        double[] uv = uv(house.side(m.side, house.storeyOf(m)), a, b);
        int u = (int) Math.round(uv[0]);
        int v = (int) Math.round(uv[1]);
        if (m.type == HouseModule.Type.TOWER) {
            int old = Math.max(3, m.width);
            double middle = m.offset + (1 + old) / 2.0;
            int size = Math.max(3, 2 * (int) Math.round(Math.hypot(uv[0] - m.along, uv[1] - middle)) + 1);
            m.width = size;
            m.offset = (int) Math.round(middle) - (1 + size) / 2;
            m.limit();
            return;
        }
        int width = Math.max(1, m.width);
        int low = m.along - (width - 1) / 2;
        int high = low + width - 1;
        int from = u >= m.along ? low : u;
        int to = u >= m.along ? Math.max(u, low) : high;
        int stretched = to - from + 1;
        m.width = stretched;
        m.along = from + (stretched - 1) / 2;
        switch (m.type) {
            // (a volume's rows run from its near wall at the offset out; a wing's from the wall line; a dormer's back into the roof)
            case VOLUME -> m.depth = Math.max(3, v - m.offset + 1);
            case WING, PORCH, BALCONY, BAY -> m.depth = Math.max(1, v);
            case CHIMNEY -> m.depth = Math.max(1, v - m.offset);
            case DORMER -> m.depth = Math.max(1, 1 - v);
            default -> {
            }
        }
        m.limit();
    }

    /** Stretches the base to the point, about its middle: as wide and as deep as twice the way to it (odd, from 5 to 40). */
    static void stretchBase(TreeSettings s, double a, double b) {
        int width = s.getInt(TreeSettings.Key.HOUSE_WIDTH);
        int depth = s.getInt(TreeSettings.Key.HOUSE_DEPTH);
        int halfA = (int) Math.round(Math.abs(a - (width - 1) / 2.0));
        int halfB = (int) Math.round(Math.abs(b - (depth - 1) / 2.0));
        s.set(TreeSettings.Key.HOUSE_WIDTH, Math.max(5, Math.min(40, 2 * halfA + 1)));
        s.set(TreeSettings.Key.HOUSE_DEPTH, Math.max(5, Math.min(40, 2 * halfB + 1)));
    }
}
