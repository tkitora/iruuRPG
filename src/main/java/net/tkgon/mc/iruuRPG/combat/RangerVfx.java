package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** The ranger's showy effects: a rising double helix aura, and the charge and the spiral beam of the sniper shot. */
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

    /** A magic circle under the sniper: two turning rings with spokes (one frame; {@code charge} 0..1 brightens it). */
    public static void sniperCircle(World world, Location feet, int tick, double charge) {
        double turn = tick * 0.08;
        for (int ring = 0; ring < 2; ring++) {
            double radius = ring == 0 ? 1.3 : 2.0;
            int points = ring == 0 ? 20 : 30;
            for (int index = 0; index < points; index++) {
                double angle = Math.PI * 2.0 * index / points + (ring == 0 ? turn : -turn);
                Location point = feet.clone().add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
                world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, index % 2 == 0 ? GOLD : ICE, true);
            }
        }
        for (int spoke = 0; spoke < 4; spoke++) {
            double angle = turn + spoke * Math.PI / 2.0;
            for (double r = 0.3; r < 2.0; r += 0.45) {
                world.spawnParticle(Particle.REDSTONE, feet.clone().add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r), 1, 0.0, 0.0, 0.0, 0.0, WHITE, true);
            }
        }
        if (charge > 0.0 && tick % 2 == 0) {
            world.spawnParticle(Particle.END_ROD, feet.clone().add(0.0, 0.1, 0.0), 1 + (int) (charge * 3), 1.0, 0.05, 1.0, 0.03);
        }
    }

    /**
     * One frame of the charge in front of the muzzle: motes are pulled in from a wide sphere that shrinks as the
     * charge ({@code progress} 0..1) fills, around a growing white core, with a turning ring facing the target.
     */
    public static void charge(World world, Location muzzle, Vector direction, double progress, int tick) {
        double sphere = 3.2 * (1.0 - progress) + 0.25;
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        int motes = 6 + (int) (progress * 10);
        for (int index = 0; index < motes; index++) {
            Vector offset = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (offset.lengthSquared() < 1.0E-6) continue;
            offset.normalize().multiply(sphere * (0.7 + 0.3 * random.nextDouble()));
            Location point = muzzle.clone().add(offset);
            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, index % 2 == 0 ? GOLD : ICE, true);
            if (index % 4 == 0) world.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.spawnParticle(Particle.REDSTONE, muzzle, 2, 0.04, 0.04, 0.04, 0.0,
                new Particle.DustOptions(Color.fromRGB(255, 255, 255), (float) (0.8 + progress * 2.2)), true);
        Vector[] axes = basis(direction.clone().normalize());
        double radius = 0.5 + 0.7 * progress;
        for (int index = 0; index < 16; index++) {
            double angle = Math.PI * 2.0 * index / 16.0 + tick * 0.35;
            Location point = muzzle.clone().add(direction.clone().normalize().multiply(0.9))
                    .add(axes[0].clone().multiply(Math.cos(angle) * radius)).add(axes[1].clone().multiply(Math.sin(angle) * radius));
            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, GOLD, true);
        }
    }

    /**
     * A long spiral beam, drawn out from the muzzle over a few ticks like a shot leaving: three strands and a fourth
     * thinner one wind around a white core, rings open along it, and it ends in a flash with shock rings.
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
                    double radius = 0.95 + 0.3 * Math.sin(distance * 0.4);
                    for (int strand = 0; strand < 4; strand++) {
                        double a = angle + strand * (Math.PI / 2.0);
                        double r = strand == 3 ? radius * 0.5 : radius;
                        Location point = center.clone().add(axes[0].clone().multiply(Math.cos(a) * r)).add(axes[1].clone().multiply(Math.sin(a) * r));
                        world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, strand % 2 == 0 ? GOLD : ICE, true);
                    }
                    world.spawnParticle(Particle.REDSTONE, center, 2, 0.05, 0.05, 0.05, 0.0, WHITE, true);
                    if (step % 3 == 0) world.spawnParticle(Particle.END_ROD, center, 1, 0.0, 0.0, 0.0, 0.0);
                    if (step % 10 == 0) {
                        for (int index = 0; index < 14; index++) {
                            double a = Math.PI * 2.0 * index / 14.0;
                            Location point = center.clone().add(axes[0].clone().multiply(Math.cos(a) * 1.7)).add(axes[1].clone().multiply(Math.sin(a) * 1.7));
                            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, WHITE, true);
                        }
                    }
                }
                if (++tick >= ticks) {
                    Location end = from.clone().add(dir.clone().multiply(length));
                    world.spawnParticle(Particle.FLASH, end, 1);
                    world.spawnParticle(Particle.END_ROD, end, 24, 0.5, 0.5, 0.5, 0.2);
                    world.spawnParticle(Particle.EXPLOSION_LARGE, end, 3, 0.6, 0.6, 0.6, 0.0);
                    for (int ring = 1; ring <= 3; ring++) {
                        for (int index = 0; index < 24; index++) {
                            double a = Math.PI * 2.0 * index / 24.0;
                            Location point = end.clone().add(axes[0].clone().multiply(Math.cos(a) * ring * 0.8)).add(axes[1].clone().multiply(Math.sin(a) * ring * 0.8));
                            world.spawnParticle(Particle.REDSTONE, point, 1, 0.0, 0.0, 0.0, 0.0, ring % 2 == 0 ? ICE : GOLD, true);
                        }
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
