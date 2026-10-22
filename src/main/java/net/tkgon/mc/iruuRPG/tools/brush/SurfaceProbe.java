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

    boolean exposed(int x, int y, int z) {
        return !solid(x + 1, y, z) || !solid(x - 1, y, z) || !solid(x, y + 1, z) || !solid(x, y - 1, z) || !solid(x, y, z + 1) || !solid(x, y, z - 1);
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
