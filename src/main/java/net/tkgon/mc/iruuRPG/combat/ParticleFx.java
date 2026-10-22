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

    private enum Audience { EVERYONE, ONLY_OWNER, EXCEPT_OWNER }

    /** The same calls as {@code World#spawnParticle}, so a call site only changes its receiver. */
    public static final class Sink {
        private final Player owner;
        private final World world;
        /** Who these particles go to (the owner of the effect, or everyone except them). Without an owner it is always everyone. */
        private final Audience audience;

        private Sink(Player owner, World world) {
            this(owner, world, Audience.EVERYONE);
        }

        private Sink(Player owner, World world, Audience audience) {
            this.owner = owner;
            this.world = world;
            this.audience = audience;
        }

        /** The same sink, but only the owner of the effect sees these particles. */
        public Sink onlyOwner() {
            return new Sink(owner, world, Audience.ONLY_OWNER);
        }

        /** The same sink, but everyone except the owner of the effect sees these particles. */
        public Sink exceptOwner() {
            return new Sink(owner, world, Audience.EXCEPT_OWNER);
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
            boolean split = owner != null && audience != Audience.EVERYONE;
            if (!split && (current == null || !current.anyCustom())) {
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
                if (split && audience == Audience.ONLY_OWNER && !isOwner) continue;
                if (split && audience == Audience.EXCEPT_OWNER && isOwner) continue;
                if (owner != null && !isOwner && current != null && current.hideOthers(viewer)) continue;
                int percent = current == null ? 100 : current.percent(viewer);
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
