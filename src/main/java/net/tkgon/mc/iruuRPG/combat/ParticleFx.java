package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Where every particle of the plugin goes out. Players can see fewer particles ({@link ParticleSettings#percent}) and
 * hide the particles of other players' attacks, so a particle is sent to each viewer on its own (to everyone at once
 * as long as nobody has changed the settings).
 *
 * <p>"Whose" a particle is: the player who made the effect. It is given with {@link #to(Player, World)}, or, for the
 * many effects that are drawn a moment after a player acts, it is the player marked with {@link #mark(Player)} in the
 * same tick ({@link #to(World)}); effects of enemies and of the world have no owner.
 */
public final class ParticleFx {

    private static volatile ParticleSettings settings;
    private static Player markedOwner;
    private static int markedTick = -1;

    private ParticleFx() {
    }

    public static void install(ParticleSettings particleSettings) {
        settings = particleSettings;
    }

    /** False when {@code viewer} chose not to see the effects of other players and {@code owner} is another player (a clone entity follows it too). */
    public static boolean canSee(Player viewer, Player owner) {
        ParticleSettings current = settings;
        if (current == null || owner == null || viewer.getUniqueId().equals(owner.getUniqueId())) return true;
        return !current.hideOthers(viewer);
    }

    /** The effects drawn in this tick (without an owner given) belong to {@code owner}. */
    public static void mark(Player owner) {
        markedOwner = owner;
        markedTick = owner == null ? -1 : org.bukkit.Bukkit.getCurrentTick();
    }

    /** The player marked in this tick, or null. */
    public static Player owner() {
        return markedOwner != null && markedTick == org.bukkit.Bukkit.getCurrentTick() ? markedOwner : null;
    }

    /** Particles in {@code world}, owned by the player marked in this tick (if any). */
    public static Sink to(World world) {
        return new Sink(owner(), world);
    }

    /** Particles in {@code world} made by {@code owner}. */
    public static Sink to(Player owner, World world) {
        return new Sink(owner != null ? owner : owner(), world);
    }

    /** The same calls as {@code World#spawnParticle}, so a call site only changes its receiver. */
    public static final class Sink {
        private final Player owner;
        private final World world;
        /** The share (0..1) of these particles the owner sees; everyone else sees all (the owner's own settings apply on top). */
        private final double ownerShare;

        private Sink(Player owner, World world) {
            this(owner, world, 1.0);
        }

        private Sink(Player owner, World world, double ownerShare) {
            this.owner = owner;
            this.world = world;
            this.ownerShare = ownerShare;
        }

        /**
         * The same sink, but the owner of the effect sees only {@code share} of the particles (0 = none, 1 = all), so an
         * effect around the player does not block their own view while others still see all of it.
         */
        public Sink dimOwner(double share) {
            return new Sink(owner, world, Math.max(0.0, Math.min(1.0, share)));
        }

        public void spawnParticle(Particle particle, Location at, int count) {
            send(particle, at.getX(), at.getY(), at.getZ(), count, 0, 0, 0, 0, null, false);
        }

        public void spawnParticle(Particle particle, Location at, int count, double ox, double oy, double oz) {
            send(particle, at.getX(), at.getY(), at.getZ(), count, ox, oy, oz, 0, null, false);
        }

        public void spawnParticle(Particle particle, Location at, int count, double ox, double oy, double oz, double extra) {
            send(particle, at.getX(), at.getY(), at.getZ(), count, ox, oy, oz, extra, null, false);
        }

        public <T> void spawnParticle(Particle particle, Location at, int count, double ox, double oy, double oz, double extra, T data) {
            send(particle, at.getX(), at.getY(), at.getZ(), count, ox, oy, oz, extra, data, false);
        }

        public <T> void spawnParticle(Particle particle, Location at, int count, double ox, double oy, double oz, double extra, T data, boolean force) {
            send(particle, at.getX(), at.getY(), at.getZ(), count, ox, oy, oz, extra, data, force);
        }

        public <T> void spawnParticle(Particle particle, Location at, int count, T data) {
            send(particle, at.getX(), at.getY(), at.getZ(), count, 0, 0, 0, 0, data, false);
        }

        public void spawnParticle(Particle particle, double x, double y, double z, int count) {
            send(particle, x, y, z, count, 0, 0, 0, 0, null, false);
        }

        public void spawnParticle(Particle particle, double x, double y, double z, int count, double ox, double oy, double oz) {
            send(particle, x, y, z, count, ox, oy, oz, 0, null, false);
        }

        public void spawnParticle(Particle particle, double x, double y, double z, int count, double ox, double oy, double oz, double extra) {
            send(particle, x, y, z, count, ox, oy, oz, extra, null, false);
        }

        public <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double ox, double oy, double oz, double extra, T data) {
            send(particle, x, y, z, count, ox, oy, oz, extra, data, false);
        }

        private <T> void send(Particle particle, double x, double y, double z, int count, double ox, double oy, double oz,
                              double extra, T data, boolean force) {
            ParticleSettings current = settings;
            boolean dimmed = owner != null && ownerShare < 1.0;
            if (!dimmed && (current == null || !current.anyCustom())) {
                world.spawnParticle(particle, x, y, z, count, ox, oy, oz, extra, data, force);
                return;
            }
            double range = force ? 128.0 : 32.0;
            double rangeSquared = range * range;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (Player viewer : world.getPlayers()) {
                Location where = viewer.getLocation();
                double dx = where.getX() - x;
                double dy = where.getY() - y;
                double dz = where.getZ() - z;
                if (dx * dx + dy * dy + dz * dz > rangeSquared) continue;
                boolean isOwner = owner != null && viewer.getUniqueId().equals(owner.getUniqueId());
                if (owner != null && !isOwner && current != null && current.hideOthers(viewer)) continue;
                int percent = current == null ? 100 : current.percent(viewer);
                if (isOwner && ownerShare < 1.0) percent = (int) Math.round(percent * ownerShare);
                if (percent <= 0) continue;
                int shown = count;
                if (percent < 100) {
                    if (count <= 1) {
                        // one particle (or a directed one): shown or not, by chance
                        if (random.nextInt(100) >= percent) continue;
                    } else {
                        shown = (int) Math.round(count * percent / 100.0);
                        if (shown <= 0) continue;
                    }
                }
                viewer.spawnParticle(particle, x, y, z, shown, ox, oy, oz, extra, data);
            }
        }
    }
}
