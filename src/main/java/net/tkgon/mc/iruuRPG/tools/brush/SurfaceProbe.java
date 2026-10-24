package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads the world for one stroke of the brush and remembers what it read: the block at a place, whether it is part of the ground, the direction its surface faces
 * (the way the solid blocks round it are not: the density gradient over a 5 x 5 x 5 box), the angle of that surface from the flat, and how much shade it is in
 * given the sun (from the turn of the surface away from the sun, and from a block in the way).
 */
final class SurfaceProbe {

    private final World world;
    private final Vector sun;
    private final boolean castShadows;
    private final Map<Long, Material> types = new HashMap<>();
    private final Map<Long, Vector> normals = new HashMap<>();
    private final Map<Long, Double> shades = new HashMap<>();
    private final Map<Long, Double> ambients = new HashMap<>();
    private final Map<Long, Double> curvatures = new HashMap<>();

    SurfaceProbe(World world, Vector sun, boolean castShadows) {
        this.world = world;
        this.sun = sun;
        this.castShadows = castShadows;
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) ((y + 2048) & 0xFFF);
    }

    World world() {
        return world;
    }

    Material type(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return Material.AIR;
        return types.computeIfAbsent(pack(x, y, z), key -> world.getType(x, y, z));
    }

    /** A block you stand on: solid, not a liquid. */
    boolean solid(int x, int y, int z) {
        Material type = type(x, y, z);
        return type.isSolid() && !type.isAir();
    }

    /** Whether the block touches air or something it can be seen through: on a face, an edge or a corner (the blocks round it in all 26 directions are looked at). */
    boolean exposed(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dy != 0 || dz != 0) && !solid(x + dx, y + dy, z + dz)) return true;
                }
            }
        }
        return false;
    }

    Vector normal(int x, int y, int z) {
        return normals.computeIfAbsent(pack(x, y, z), key -> {
            double nx = 0.0;
            double ny = 0.0;
            double nz = 0.0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (!solid(x + dx, y + dy, z + dz)) continue;
                        double weight = 1.0 / Math.sqrt(dx * dx + dy * dy + dz * dz);
                        nx -= dx * weight;
                        ny -= dy * weight;
                        nz -= dz * weight;
                    }
                }
            }
            Vector normal = new Vector(nx, ny, nz);
            return normal.lengthSquared() < 1.0E-6 ? new Vector(0.0, 1.0, 0.0) : normal.normalize();
        });
    }

    /** The angle of the surface from the flat in degrees: 0 for ground, 90 for a wall, more for an overhang. */
    double angle(int x, int y, int z) {
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, normal(x, y, z).getY()))));
    }

    /** The share of the sky round the surface block that is closed off by solid blocks (0 = open, 1 = shut in): rays over the hemisphere of the normal, 3 blocks each, near blocks counting more. */
    double ambient(int x, int y, int z) {
        return ambients.computeIfAbsent(pack(x, y, z), key -> {
            Vector normal = normal(x, y, z);
            double blocked = 0.0;
            double total = 0.0;
            int points = 24;
            double golden = Math.PI * (3.0 - Math.sqrt(5.0));
            for (int index = 0; index < points; index++) {
                double dy = 1.0 - 2.0 * (index + 0.5) / points;
                double ring = Math.sqrt(Math.max(0.0, 1.0 - dy * dy));
                double angle = golden * index;
                double dx = Math.cos(angle) * ring;
                double dz = Math.sin(angle) * ring;
                if (dx * normal.getX() + dy * normal.getY() + dz * normal.getZ() < 0.15) continue;
                total += 1.0;
                for (int step = 1; step <= 3; step++) {
                    if (solid((int) Math.round(x + dx * step), (int) Math.round(y + dy * step), (int) Math.round(z + dz * step))) {
                        blocked += 1.0 / step;
                        break;
                    }
                }
            }
            return total <= 0.0 ? 0.0 : Math.min(1.0, blocked / total * 1.6);
        });
    }

    /**
     * How much the surface block bulges (positive, a ridge or a corner) or sinks (negative, a crevice or an inner corner), -1 to 1: a flat surface has half of the box round it
     * solid; less than half is a bulge, more is a hollow.
     */
    double curvature(int x, int y, int z) {
        return curvatures.computeIfAbsent(pack(x, y, z), key -> {
            int solid = 0;
            int count = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        count++;
                        if (solid(x + dx, y + dy, z + dz)) solid++;
                    }
                }
            }
            return Math.max(-1.0, Math.min(1.0, (0.5 - solid / (double) count) * 3.0));
        });
    }

    /** 0 = in the sun, 1 = in full shade. */
    double shade(int x, int y, int z) {
        return shades.computeIfAbsent(pack(x, y, z), key -> {
            Vector normal = normal(x, y, z);
            double lambert = normal.dot(sun);
            double shade = Math.max(0.0, Math.min(1.0, (0.35 - lambert) / 0.9));
            if (castShadows && shade < 0.85) {
                Location from = new Location(world, x + 0.5 + normal.getX() * 0.9, y + 0.5 + normal.getY() * 0.9, z + 0.5 + normal.getZ() * 0.9);
                RayTraceResult hit = world.rayTraceBlocks(from, sun, 24.0, FluidCollisionMode.NEVER, true);
                if (hit != null) shade = Math.max(shade, 0.85);
            }
            return shade;
        });
    }
}
