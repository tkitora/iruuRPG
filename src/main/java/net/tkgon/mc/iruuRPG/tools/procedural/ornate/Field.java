package net.tkgon.mc.iruuRPG.tools.procedural.ornate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape of the thick wood as a signed distance (negative inside): a cone round every segment of the bones whose radius is {@code min} or more (cut where it gets
 * thinner), all of them together as they are (the forks stay sharp), except at the foot: the trunk near the ground and the roots near the trunk melt into each other
 * (a smooth minimum of {@code smooth} blocks), so the roots flare out of the trunk.
 */
public final class Field {

    /** One cone: from a (radius ra) to b (radius rb), and the node it ends at. */
    record Cone(double ax, double ay, double az, double ra, double bx, double by, double bz, double rb, int node, boolean smooth) {
    }

    /** The distance at a point and the node of the nearest cone (-1 when there is none near). */
    public record Hit(double distance, int node) {
    }

    private static final double CELL = 4.0;

    private final List<Cone> cones = new ArrayList<>();
    private final Map<Long, List<Cone>> grid = new HashMap<>();
    private final double smooth;
    private double minX = Double.MAX_VALUE;
    private double minY = Double.MAX_VALUE;
    private double minZ = Double.MAX_VALUE;
    private double maxX = -Double.MAX_VALUE;
    private double maxY = -Double.MAX_VALUE;
    private double maxZ = -Double.MAX_VALUE;

    /** The thick wood of {@code bones}: the parts of segments {@code min} or more thick. */
    public Field(Skeleton bones, double min, double smooth) {
        this.smooth = Math.max(0.0, smooth);
        double base = bones.size() > 0 ? bones.radius(0) : 1.0;
        for (int node = 1; node < bones.size(); node++) {
            int from = bones.parent(node);
            double ra = bones.startRadius(node);
            double rb = bones.radius(node);
            if (ra < min) continue;
            double ax = bones.x(from);
            double ay = bones.y(from);
            double az = bones.z(from);
            double bx = bones.x(node);
            double by = bones.y(node);
            double bz = bones.z(node);
            if (rb < min) {
                // (only the thick part: the rest is drawn as thin wood)
                double t = (ra - min) / Math.max(1.0e-9, ra - rb);
                bx = ax + (bx - ax) * t;
                by = ay + (by - ay) * t;
                bz = az + (bz - az) * t;
                rb = min;
            }
            int kind = bones.kind(node);
            boolean foot = (kind == Skeleton.TRUNK && Math.min(ay, by) < base * 2.0 + 1.0)
                    || (kind == Skeleton.ROOT && Math.hypot(ax - 0.5, az - 0.5) < base + 3.0);
            add(new Cone(ax, ay, az, ra, bx, by, bz, rb, node, foot && this.smooth > 0.0));
        }
    }

    private void add(Cone cone) {
        cones.add(cone);
        double reach = Math.max(cone.ra(), cone.rb()) + smooth + 1.0;
        double x0 = Math.min(cone.ax(), cone.bx()) - reach;
        double y0 = Math.min(cone.ay(), cone.by()) - reach;
        double z0 = Math.min(cone.az(), cone.bz()) - reach;
        double x1 = Math.max(cone.ax(), cone.bx()) + reach;
        double y1 = Math.max(cone.ay(), cone.by()) + reach;
        double z1 = Math.max(cone.az(), cone.bz()) + reach;
        minX = Math.min(minX, x0);
        minY = Math.min(minY, y0);
        minZ = Math.min(minZ, z0);
        maxX = Math.max(maxX, x1);
        maxY = Math.max(maxY, y1);
        maxZ = Math.max(maxZ, z1);
        for (int cx = (int) Math.floor(x0 / CELL); cx <= (int) Math.floor(x1 / CELL); cx++) {
            for (int cy = (int) Math.floor(y0 / CELL); cy <= (int) Math.floor(y1 / CELL); cy++) {
                for (int cz = (int) Math.floor(z0 / CELL); cz <= (int) Math.floor(z1 / CELL); cz++) {
                    grid.computeIfAbsent(key(cx, cy, cz), k -> new ArrayList<>()).add(cone);
                }
            }
        }
    }

    private static long key(int cx, int cy, int cz) {
        return ((long) (cx + 8192) & 0x3FFFL) | (((long) (cy + 8192) & 0x3FFFL) << 14) | (((long) (cz + 8192) & 0x3FFFL) << 28);
    }

    public boolean empty() {
        return cones.isEmpty();
    }

    /** The box of the thick wood (with room for the smoothing): {minX, minY, minZ, maxX, maxY, maxZ}. */
    public int[] bounds() {
        return new int[]{(int) Math.floor(minX), (int) Math.floor(minY), (int) Math.floor(minZ), (int) Math.ceil(maxX), (int) Math.ceil(maxY), (int) Math.ceil(maxZ)};
    }

    /** The signed distance at (x, y, z) (a large number far from the wood). */
    public double distance(double x, double y, double z) {
        return hit(x, y, z).distance();
    }

    public Hit hit(double x, double y, double z) {
        List<Cone> near = grid.get(key((int) Math.floor(x / CELL), (int) Math.floor(y / CELL), (int) Math.floor(z / CELL)));
        if (near == null) return new Hit(1.0e9, -1);
        double sharp = 1.0e9;
        double soft = 1.0e9;
        boolean anySoft = false;
        double best = 1.0e9;
        int node = -1;
        for (Cone cone : near) {
            double d = cone(cone, x, y, z);
            if (d < best) {
                best = d;
                node = cone.node();
            }
            if (cone.smooth()) {
                soft = anySoft ? smoothMin(soft, d, smooth) : d;
                anySoft = true;
            } else {
                sharp = Math.min(sharp, d);
            }
        }
        return new Hit(Math.min(sharp, soft), node);
    }

    /** The distance to the nearest cone whose node is not {@code mine} (another limb's wood: two limbs this near make a fork's inner angle). */
    public double distanceOther(double x, double y, double z, java.util.function.IntPredicate mine) {
        List<Cone> near = grid.get(key((int) Math.floor(x / CELL), (int) Math.floor(y / CELL), (int) Math.floor(z / CELL)));
        if (near == null) return 1.0e9;
        double best = 1.0e9;
        for (Cone cone : near) if (!mine.test(cone.node())) best = Math.min(best, cone(cone, x, y, z));
        return best;
    }

    /** The polynomial smooth minimum: the two meet in a fillet about {@code k} wide. */
    static double smoothMin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0.0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    /** The distance to a cone: to the nearest point of its axis, less the radius there. */
    private static double cone(Cone c, double x, double y, double z) {
        double ux = c.bx() - c.ax();
        double uy = c.by() - c.ay();
        double uz = c.bz() - c.az();
        double length2 = ux * ux + uy * uy + uz * uz;
        double t = length2 <= 1.0e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, ((x - c.ax()) * ux + (y - c.ay()) * uy + (z - c.az()) * uz) / length2));
        double dx = x - c.ax() - ux * t;
        double dy = y - c.ay() - uy * t;
        double dz = z - c.az() - uz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - (c.ra() + (c.rb() - c.ra()) * t);
    }
}
