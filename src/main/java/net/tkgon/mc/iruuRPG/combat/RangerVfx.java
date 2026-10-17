package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** The ranger's showy effects: a rising double helix aura, a spiral beam and a spiral trail for the instant shots. */
public final class RangerVfx {

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 70), 1.2f);
    private static final Particle.DustOptions ICE = new Particle.DustOptions(Color.fromRGB(190, 235, 255), 1.1f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.9f);

    private RangerVfx() {
    }

    /** One frame of the enhanced state's aura: three strands climbing around the body, a turning ring, sparks. */
    public static void aura(World world, Location base, int tick) {
        for (int strand = 0; strand < 3; strand++) {
            double phase = (tick * 0.06 + strand / 3.0) % 1.0;
            double height = phase * 2.2;
            double angle = height * 5.0 + strand * (Math.PI * 2.0 / 3.0) + tick * 0.12;
            double radius = 0.85 - 0.25 * phase;
            Location point = base.clone().add(Math.cos(angle) * radius, 0.1 + height, Math.sin(angle) * radius);
            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, strand % 2 == 0 ? GOLD : ICE, true);
            world.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (tick % 6 == 0) {
            double radius = 1.0 + 0.5 * Math.sin(tick * 0.1);
            for (int index = 0; index < 20; index++) {
                double angle = Math.PI * 2.0 * index / 20.0 + tick * 0.2;
                world.spawnParticle(Particle.REDSTONE, base.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius), 1, 0.0, 0.0, 0.0, 0.0, WHITE, true);
            }
        }
    }

    /** The burst when the enhanced state starts: a shock ring and a column of light. */
    public static void activation(World world, Location base) {
        for (int ring = 1; ring <= 3; ring++) {
            double radius = ring * 0.9;
            for (int index = 0; index < 28; index++) {
                double angle = Math.PI * 2.0 * index / 28.0;
                world.spawnParticle(Particle.REDSTONE, base.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius), 1, 0.0, 0.0, 0.0, 0.0, ring % 2 == 0 ? ICE : GOLD, true);
            }
        }
        for (int step = 0; step < 14; step++) {
            world.spawnParticle(Particle.END_ROD, base.clone().add(0.0, step * 0.25, 0.0), 2, 0.15, 0.0, 0.15, 0.02);
        }
        world.spawnParticle(Particle.FLASH, base.clone().add(0.0, 1.0, 0.0), 1);
    }

    private static Vector[] basis(Vector direction) {
        Vector u = direction.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (u.lengthSquared() < 1.0E-6) u = new Vector(1.0, 0.0, 0.0);
        u.normalize();
        Vector v = direction.clone().crossProduct(u).normalize();
        return new Vector[]{u, v};
    }

    /** A short double-helix trail from {@code from} to {@code to} (the ranger's instant shots). */
    public static void spiralTrail(World world, Location from, Location to) {
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 0.5) return;
        Vector direction = path.clone().normalize();
        Vector[] axes = basis(direction);
        for (double distance = 0.0; distance <= length; distance += 0.35) {
            Location center = from.clone().add(direction.clone().multiply(distance));
            double angle = distance * 3.2;
            double radius = 0.28;
            for (int strand = 0; strand < 2; strand++) {
                double a = angle + strand * Math.PI;
                Location point = center.clone().add(axes[0].clone().multiply(Math.cos(a) * radius)).add(axes[1].clone().multiply(Math.sin(a) * radius));
                world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, strand == 0 ? GOLD : ICE);
            }
            world.spawnParticle(Particle.REDSTONE, center, 1, 0.0, 0.0, 0.0, 0.0, WHITE);
        }
    }

    /**
     * A long spiral beam, drawn out from the muzzle over a few ticks like a shot leaving: two strands and a third
     * thinner one wind around a white core, rings open along it, and it ends in a burst.
     */
    public static void spiralBeam(JavaPlugin plugin, World world, Location from, Vector direction, double length) {
        Vector dir = direction.clone().normalize();
        Vector[] axes = basis(dir);
        int steps = (int) Math.max(2, Math.ceil(length / 0.5));
        int ticks = 5;
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                int first = steps * tick / ticks;
                int last = steps * (tick + 1) / ticks;
                for (int step = first; step < last; step++) {
                    double distance = step * 0.5;
                    Location center = from.clone().add(dir.clone().multiply(distance));
                    double angle = distance * 2.6;
                    double radius = 0.6 + 0.2 * Math.sin(distance * 0.4);
                    for (int strand = 0; strand < 3; strand++) {
                        double a = angle + strand * (Math.PI * 2.0 / 3.0);
                        double r = strand == 2 ? radius * 0.55 : radius;
                        Location point = center.clone().add(axes[0].clone().multiply(Math.cos(a) * r)).add(axes[1].clone().multiply(Math.sin(a) * r));
                        world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, strand == 0 ? GOLD : ICE, true);
                    }
                    world.spawnParticle(Particle.REDSTONE, center, 1, 0.0, 0.0, 0.0, 0.0, WHITE, true);
                    if (step % 3 == 0) world.spawnParticle(Particle.END_ROD, center, 1, 0.0, 0.0, 0.0, 0.0);
                    if (step % 10 == 0) {
                        for (int index = 0; index < 14; index++) {
                            double a = Math.PI * 2.0 * index / 14.0;
                            Location point = center.clone().add(axes[0].clone().multiply(Math.cos(a) * 1.1)).add(axes[1].clone().multiply(Math.sin(a) * 1.1));
                            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, WHITE, true);
                        }
                    }
                }
                if (++tick >= ticks) {
                    Location end = from.clone().add(dir.clone().multiply(length));
                    world.spawnParticle(Particle.FLASH, end, 1);
                    world.spawnParticle(Particle.END_ROD, end, 24, 0.5, 0.5, 0.5, 0.2);
                    world.spawnParticle(Particle.EXPLOSION_LARGE, end, 1);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
