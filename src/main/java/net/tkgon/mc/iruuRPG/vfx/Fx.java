package net.tkgon.mc.iruuRPG.vfx;

import net.tkgon.mc.iruuRPG.combat.ParticleFx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Draws the particles of one effect: gradient-colored dust along lines, rings, spirals and spheres, with the work kept
 * small. The shapes are templates: a function of a few numbers, drawn from the baked unit shapes ({@link Shapes}) and the
 * sine table ({@link Trig}), colored by a {@link Gradient} (the color of each point is worked out, never one color for
 * the whole effect).
 *
 * <p>Distance culling: made for a spot ({@link #of}), it looks once at how far the nearest player of the world is: no one
 * within 64 blocks and nothing is drawn; further than 40 blocks every other point is drawn (a far effect is small on the
 * screen), so packets that nobody would see are not sent. Everything still goes through {@link ParticleFx}, which keeps
 * the players' particle settings.
 */
public final class Fx {

    private static final double FAR_SKIP_ALL_SQUARED = 64.0 * 64.0;
    private static final double FAR_THIN_SQUARED = 40.0 * 40.0;

    private static final Fx NOTHING = new Fx(null, 1, false, 1.0, 1.0);

    /** Dust is drawn at this fraction of the size asked for (finer particles); {@code vfx.size-scale}. */
    private static volatile double sizeScale = 0.8;
    /** A lone point (a mote, a spark of dust: where the particles are few) is drawn at this fraction: close to the original size so it can be seen; {@code vfx.sparse-size-scale}. */
    private static volatile double sparseScale = 0.95;
    /** Points per block of a line and points of a ring / spiral / sphere / disc are this many times the number asked for; {@code vfx.density}. */
    private static volatile double density = 2.2 * 1.35;
    /** The points of every shape are packed this much closer than the setting says (the gaps between the particles were too wide). */
    private static final double DENSITY_BOOST = 1.35;
    private static final int MAX_RING = 320;
    private static final int MAX_SPHERE = 260;

    /** Reads {@code vfx.size-scale} and {@code vfx.density} (0.3 - 1.5 and 0.5 - 3) of the config. */
    public static void configure(org.bukkit.configuration.file.FileConfiguration config) {
        sizeScale = Math.max(0.3, Math.min(1.5, config.getDouble("vfx.size-scale", 0.8)));
        sparseScale = Math.max(0.3, Math.min(1.5, config.getDouble("vfx.sparse-size-scale", 0.95)));
        density = Math.max(0.5, Math.min(3.0, config.getDouble("vfx.density", 2.2))) * DENSITY_BOOST;
    }

    private double size(double asked) {
        return Math.max(0.3, asked * sizeScale * small);
    }

    private int count(int asked, int max) {
        return (int) Math.max(4, Math.min(max, Math.round(asked * density * thin)));
    }

    /** A count of loose particles after {@link #scaled} (0 stays 0: it means one particle thrown along its offset). */
    private int loose(int count) {
        return count <= 1 || thin >= 1.0 ? count : Math.max(1, (int) Math.round(count * thin));
    }

    private final ParticleFx.Sink sink;
    private final int step;
    private final boolean active;
    /** This effect's own share of the points and of their size ({@link #scaled}: a class whose effects fill the screen draws fewer, smaller ones). */
    private final double thin;
    private final double small;

    private Fx(ParticleFx.Sink sink, int step, boolean active, double thin, double small) {
        this.sink = sink;
        this.step = step;
        this.active = active;
        this.thin = thin;
        this.small = small;
    }

    /** The same effect with {@code points} times the points (shapes, lines and loose particles) and {@code size} times their size. */
    public Fx scaled(double points, double size) {
        if (!active || (points == 1.0 && size == 1.0)) return this;
        return new Fx(sink, step, true, thin * Math.max(0.1, Math.min(1.0, points)), small * Math.max(0.3, Math.min(1.5, size)));
    }

    /** For an effect around {@code hint} in {@code world} made by {@code owner} (null: nobody's). */
    public static Fx of(Player owner, World world, Location hint) {
        if (world == null || hint == null) return NOTHING;
        return of(owner, world, hint.getX(), hint.getY(), hint.getZ());
    }

    public static Fx of(Player owner, World world, double x, double y, double z) {
        if (world == null) return NOTHING;
        double nearest = Double.MAX_VALUE;
        for (Player viewer : world.getPlayers()) {
            Location at = viewer.getLocation();
            double dx = at.getX() - x;
            double dy = at.getY() - y;
            double dz = at.getZ() - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearest) nearest = distance;
        }
        if (nearest > FAR_SKIP_ALL_SQUARED) return NOTHING;
        return new Fx(ParticleFx.to(owner, world), nearest > FAR_THIN_SQUARED ? 2 : 1, true, 1.0, 1.0);
    }

    public boolean active() {
        return active;
    }

    /** The same effect, but everyone except its owner sees it. */
    public Fx exceptOwner() {
        return active ? new Fx(sink.exceptOwner(), step, true, thin, small) : this;
    }

    /** 1 normally, 2 for a far effect: how many of the points to step over in a loop of one's own. */
    public int step() {
        return step;
    }

    // ---- points ----------------------------------------------------------------------------------------------------------

    /** One dust particle at ({@code x}, {@code y}, {@code z}) of the color at {@code t} of {@code gradient}. */
    public void dust(double x, double y, double z, Gradient gradient, double t, double size) {
        if (!active) return;
        sink.spawnParticle(Particle.REDSTONE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, gradient.dust(t, Math.max(0.3, size * sparseScale * small)));
    }

    /** Any particle (sparks and the like), kept or dropped by the same distance culling. */
    public void particle(Particle particle, double x, double y, double z, int count, double ox, double oy, double oz, double extra) {
        if (!active) return;
        sink.spawnParticle(particle, x, y, z, loose(count), ox, oy, oz, extra);
    }

    /** A particle with data (a block's crack, dust of a given color, ...), kept or dropped by the same distance culling. */
    public <T> void particle(Particle particle, double x, double y, double z, int count, double ox, double oy, double oz, double extra, T data) {
        if (!active) return;
        sink.spawnParticle(particle, x, y, z, loose(count), ox, oy, oz, extra, data);
    }

    // ---- shapes ------------------------------------------------------------------------------------------------------------

    /**
     * A line from {@code from} to {@code to}: a point every {@code spacing} blocks, the color going from {@code t0} to
     * {@code t1} of the gradient along it (curved by {@code curve}: 0 = straight, above 0 = the change is quick at the
     * start and slow at the end, like an exponential fade). A spark every {@code sparkEvery} points when {@code spark} is
     * not null.
     */
    public void line(Location from, Location to, Gradient gradient, double t0, double t1, double curve, double size, double spacing,
                     Particle spark, int sparkEvery) {
        if (!active) return;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) return;
        int points = Math.max(1, (int) Math.ceil(length / spacing * density * thin));
        double fineSize = size(size);
        for (int index = 0; index <= points; index += step) {
            double along = index / (double) points;
            double t = Curves.lerp(t0, t1, Curves.expOut(along, curve));
            double x = from.getX() + dx * along;
            double y = from.getY() + dy * along;
            double z = from.getZ() + dz * along;
            sink.spawnParticle(Particle.REDSTONE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, gradient.dust(t, fineSize));
            if (spark != null && index % Math.max(1, Math.round(sparkEvery * density)) == 0) sink.spawnParticle(spark, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** A line with the whole gradient along it (0 at {@code from}, 1 at {@code to}). */
    public void line(Location from, Location to, Gradient gradient, double size, double spacing) {
        line(from, to, gradient, 0.0, 1.0, 0.0, size, spacing, null, 1);
    }

    /**
     * A ring of {@code count} points (a baked circle) of {@code radius} around {@code center} in the plane of {@code u} and
     * {@code v} (unit vectors at right angles). The color goes around with the angle ({@code t} at the start, {@code t +
     * spread} half way round and back: no seam where the ring closes); {@code spread} 0 = one color.
     */
    public void ring(Location center, Vector u, Vector v, double radius, int count, Gradient gradient, double t, double spread, double size) {
        if (!active) return;
        count = count(count, MAX_RING);
        float[] circle = Shapes.circle(count);
        double fineSize = size(size);
        for (int index = 0; index < count; index += step) {
            double c = circle[index * 2] * radius;
            double s = circle[index * 2 + 1] * radius;
            double colorT = spread == 0.0 ? t : t + spread * Curves.triangle(index / (double) count);
            sink.spawnParticle(Particle.REDSTONE, center.getX() + u.getX() * c + v.getX() * s, center.getY() + u.getY() * c + v.getY() * s,
                    center.getZ() + u.getZ() * c + v.getZ() * s, 1, 0.0, 0.0, 0.0, 0.0, gradient.dust(colorT, fineSize));
        }
    }

    /** A ring flat on the ground (horizontal), at the height of {@code center}. */
    public void flatRing(Location center, double radius, int count, Gradient gradient, double t, double spread, double size) {
        if (!active) return;
        count = count(count, MAX_RING);
        float[] circle = Shapes.circle(count);
        double fineSize = size(size);
        for (int index = 0; index < count; index += step) {
            double colorT = spread == 0.0 ? t : t + spread * Curves.triangle(index / (double) count);
            sink.spawnParticle(Particle.REDSTONE, center.getX() + circle[index * 2] * radius, center.getY(), center.getZ() + circle[index * 2 + 1] * radius,
                    1, 0.0, 0.0, 0.0, 0.0, gradient.dust(colorT, fineSize));
        }
    }

    /**
     * A spiral arm in the plane of {@code u} and {@code v}: the radius grows exponentially from {@code r0} to {@code r1}
     * (a logarithmic spiral) over {@code turns} turns from the angle {@code phase}; it also rises {@code lift} along
     * {@code normal} from start to end. The color goes from the first to the last of the gradient along it.
     */
    public void spiral(Location center, Vector u, Vector v, Vector normal, double r0, double r1, double turns, double phase, double lift,
                       int count, Gradient gradient, double size) {
        if (!active) return;
        count = count(count, MAX_RING);
        double fineSize = size(size);
        double ratio = r1 / Math.max(1.0E-6, r0);
        for (int index = 0; index < count; index += step) {
            double s = count == 1 ? 0.0 : index / (double) (count - 1);
            double radius = r0 * Math.pow(ratio, s);
            double angle = phase + Math.PI * 2.0 * turns * s;
            double c = Trig.cos(angle) * radius;
            double sn = Trig.sin(angle) * radius;
            double rise = lift * s;
            sink.spawnParticle(Particle.REDSTONE, center.getX() + u.getX() * c + v.getX() * sn + normal.getX() * rise,
                    center.getY() + u.getY() * c + v.getY() * sn + normal.getY() * rise,
                    center.getZ() + u.getZ() * c + v.getZ() * sn + normal.getZ() * rise, 1, 0.0, 0.0, 0.0, 0.0, gradient.dust(s, fineSize));
        }
    }

    /**
     * A shell of {@code count} points (a baked sphere) of {@code radius} around {@code center}, turned about the vertical
     * axis by {@code turn} radians. The color goes with the height: the bottom is 0 of the gradient, the top is 1.
     */
    public void sphereShell(Location center, double radius, double turn, int count, Gradient gradient, double size) {
        if (!active) return;
        count = count(count, MAX_SPHERE);
        double fineSize = size(size);
        float[] sphere = Shapes.sphere(count);
        double c = Trig.cos(turn);
        double s = Trig.sin(turn);
        for (int index = 0; index < count; index += step) {
            double x = sphere[index * 3];
            double y = sphere[index * 3 + 1];
            double z = sphere[index * 3 + 2];
            sink.spawnParticle(Particle.REDSTONE, center.getX() + (x * c - z * s) * radius, center.getY() + y * radius, center.getZ() + (x * s + z * c) * radius,
                    1, 0.0, 0.0, 0.0, 0.0, gradient.dust(y * 0.5 + 0.5, fineSize));
        }
    }

    /**
     * A filled disc in the plane of {@code u} and {@code v} (points spread evenly by the golden angle, so it has no
     * gaps and no rings): the color goes from the middle (0) to the edge (1) of the gradient with the distance, curved by
     * {@code curve}.
     */
    public void disc(Location center, Vector u, Vector v, double radius, int count, Gradient gradient, double curve, double size) {
        if (!active) return;
        count = count(count, MAX_RING);
        double fineSize = size(size);
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int index = 0; index < count; index += step) {
            double s = Math.sqrt((index + 0.5) / count);
            double angle = golden * index;
            double c = Trig.cos(angle) * radius * s;
            double sn = Trig.sin(angle) * radius * s;
            sink.spawnParticle(Particle.REDSTONE, center.getX() + u.getX() * c + v.getX() * sn, center.getY() + u.getY() * c + v.getY() * sn,
                    center.getZ() + u.getZ() * c + v.getZ() * sn, 1, 0.0, 0.0, 0.0, 0.0, gradient.dust(Curves.expOut(s, curve), fineSize));
        }
    }
}
