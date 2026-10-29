package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;

import java.util.List;
import java.util.Map;

/**
 * The room a mob may move in, laid out round the place it was set (its home): a {@code cylinder} of {@code radius} from {@code down} blocks below the home to {@code up} above
 * it, a {@code sphere} of {@code radius}, or a {@code box} reaching {@code radius} blocks east, west, north and south and {@code up} / {@code down}. All three are convex, so a
 * straight line between two places inside stays inside.
 */
public record Volume(String shape, double radius, double up, double down) {

    public Volume {
        shape = shape == null ? "cylinder" : shape;
        radius = Math.max(1.0, radius);
        up = Math.max(0.0, up);
        down = Math.max(0.0, down);
    }

    /** Whether {@code at} is inside the room of a mob whose home is {@code home}. */
    public boolean contains(Location home, Location at) {
        if (home == null || at == null || home.getWorld() != at.getWorld()) return false;
        return contains(home, at.getX(), at.getY(), at.getZ());
    }

    /** The same for a point of the home's world. */
    public boolean contains(Location home, double x, double y, double z) {
        double dx = x - home.getX();
        double dy = y - home.getY();
        double dz = z - home.getZ();
        return switch (shape) {
            case "sphere" -> dx * dx + dy * dy + dz * dz <= radius * radius;
            case "box" -> Math.abs(dx) <= radius && Math.abs(dz) <= radius && dy <= up && dy >= -down;
            default -> dx * dx + dz * dz <= radius * radius && dy <= up && dy >= -down;
        };
    }

    /** The place inside the room nearest to {@code at} ({@code at} itself when it is inside), half a block in from the edge. */
    public Location clamp(Location home, Location at) {
        if (home == null || at == null || home.getWorld() != at.getWorld() || contains(home, at)) return at == null ? null : at.clone();
        double dx = at.getX() - home.getX();
        double dy = at.getY() - home.getY();
        double dz = at.getZ() - home.getZ();
        double inner = Math.max(0.5, radius - 0.5);
        switch (shape) {
            case "sphere" -> {
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double scale = length <= inner ? 1.0 : inner / length;
                dx *= scale;
                dy *= scale;
                dz *= scale;
            }
            case "box" -> {
                dx = Math.max(-inner, Math.min(inner, dx));
                dz = Math.max(-inner, Math.min(inner, dz));
                dy = Math.max(-down, Math.min(up, dy));
            }
            default -> {
                double flat = Math.sqrt(dx * dx + dz * dz);
                if (flat > inner) {
                    dx *= inner / flat;
                    dz *= inner / flat;
                }
                dy = Math.max(-down, Math.min(up, dy));
            }
        }
        Location clamped = home.clone().add(dx, dy, dz);
        clamped.setYaw(at.getYaw());
        clamped.setPitch(at.getPitch());
        return clamped;
    }

    /** Reads {@code volume:}; {@code fallbackRadius} is used when it gives no radius. Null when the map is null. */
    static Volume parse(Map<?, ?> map, double fallbackRadius, List<String> errors, String owner) {
        if (map == null) return null;
        String shape = Cfg.lower(map, "shape", "cylinder");
        if (!List.of("cylinder", "sphere", "box").contains(shape)) {
            errors.add(owner + ": volume shape must be cylinder / sphere / box (" + shape + ")");
            shape = "cylinder";
        }
        double radius = Cfg.number(map, "radius", fallbackRadius);
        return new Volume(shape, radius, Cfg.number(map, "up", Math.min(24.0, radius)), Cfg.number(map, "down", 8.0));
    }
}
