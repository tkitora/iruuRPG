package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.combat.clone.CloneBody;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import net.tkgon.mc.iruuRPG.vfx.Trig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The assassin's clones: a figure of gradient dust (a head, a body with shoulders, two legs, a raised arm with a blade, a trail of shadow behind) that is there for
 * about a second. It takes the place of teleporting (a teleport throws the player's camera): the assassin stays where they are, and a clone steps in to the enemy and
 * strikes for them. A clone appears faintly at a distance, runs in, swings at the moments the caller counts as hits ({@code onHit}), holds a moment and fades. In
 * {@code quality} mode the figure is drawn with more points and an afterimage trail (for the item swap skill).
 */
final class Clones {

    /** The grey of a ghost (the clones of the heavy attack): pale and see-through where the figure is drawn in dust. */
    static final Gradient GHOST = Gradient.of(Color.fromRGB(78, 80, 88), Color.fromRGB(176, 178, 186));
    /** Red and black: the colours of everything the assassin does while the item swap skill lasts. */
    static final Gradient RED_BLACK = Gradient.of(Color.fromRGB(12, 0, 0), Color.fromRGB(150, 10, 16), Color.fromRGB(255, 58, 46));

    /**
     * How a clone looks: the gradient of its dust (trail, figure when it is not an entity, cuts), the outline of its body, whether it is drawn in high quality, and whether
     * its blows are drawn as straight horizontal lines (the slashes of the item swap's echo) instead of arcs.
     */
    record Look(Gradient gradient, CloneBody.Tint tint, boolean quality, boolean lineCuts) {
        static Look ghost() {
            return new Look(GHOST, CloneBody.Tint.GRAY, false, false);
        }

        static Look swap(boolean lineCuts) {
            return new Look(RED_BLACK, CloneBody.Tint.RED, true, lineCuts);
        }
    }

    private static final int APPEAR = 3;
    private static final int RUN = 4;
    private static final int FADE = 4;

    private Clones() {
    }

    /** The plugin stops: the standing clones go. */
    static void shutdown() {
        AssassinClones.shutdown();
    }

    /**
     * A clone that comes at the target from {@code angle} (radians round it), swings {@code hits} times {@code gap} ticks apart, and goes. {@code targetAt} gives the
     * place of the target each tick (the last place is kept if it returns null); {@code onHit} gets the number of the swing at the moment of its blow.
     */
    static void strike(JavaPlugin plugin, Player owner, Look look, Supplier<Location> targetAt, double angle, int hits, int gap, IntConsumer onHit, Runnable onEnd) {
        Gradient gradient = look.gradient();
        boolean quality = look.quality();
        Location first = targetAt.get();
        if (first == null || first.getWorld() == null) return;
        World world = first.getWorld();
        int swingStart = APPEAR + RUN;
        int total = swingStart + Math.max(0, hits - 1) * gap + 4 + FADE;
        new SafeTask(plugin) {
            private int tick;
            private Location last = first.clone();
            private int swung;
            /** The clone as an entity with the owner's skin (null: the figure of particles). */
            private CloneBody body;
            private boolean bodyTried;

            @Override
            protected void tick() {
                ParticleFx.mark(owner);
                if (tick >= total || !owner.isOnline()) {
                    if (body != null) body.remove();
                    cancel();
                    if (onEnd != null) onEnd.run();
                    return;
                }
                Location now = targetAt.get();
                if (now != null && now.getWorld() == world) last = now;
                double dirX = Math.cos(angle);
                double dirZ = Math.sin(angle);
                // from three blocks out to a block and a bit from the target
                double distance;
                double fade = 1.0;
                if (tick < APPEAR) {
                    distance = 3.4;
                    fade = (tick + 1) / (double) APPEAR;
                } else if (tick < swingStart) {
                    double t = (tick - APPEAR + 1) / (double) RUN;
                    distance = 3.4 - 2.2 * (1.0 - (1.0 - t) * (1.0 - t));
                } else {
                    distance = 1.2;
                }
                if (tick >= total - FADE) fade = (total - tick) / (double) FADE;
                double x = last.getX() + dirX * distance;
                double z = last.getZ() + dirZ * distance;
                // the clone faces the target (Minecraft's yaw: 0 is south, the facing is (-sin, cos))
                double yaw = Math.atan2(dirX, -dirZ);
                // the swing: the arm comes down over two ticks from the moment of each blow
                double pose = 0.0;
                int sinceStart = tick - swingStart;
                if (sinceStart >= 0 && gap > 0 || (sinceStart >= 0 && hits == 1)) {
                    int index = hits <= 1 ? 0 : Math.min(hits - 1, sinceStart / Math.max(1, gap));
                    int into = hits <= 1 ? sinceStart : sinceStart - index * gap;
                    if (into >= 0 && into <= 3) pose = into == 0 ? 0.55 : into == 1 ? 1.0 : into == 2 ? 0.8 : 0.4;
                    if (into == 0 && index >= swung && index < hits) {
                        swung = index + 1;
                        onHit.accept(index);
                        if (look.lineCuts()) lineCut(owner, gradient, last, ThreadLocalRandom.current().nextDouble(Math.PI), quality);
                        else cut(owner, gradient, last, dirX, dirZ, index, quality);
                        if (body != null) body.swing();
                    }
                }
                if (!bodyTried) {
                    bodyTried = true;
                    Location at = new Location(world, x, last.getY(), z, (float) Math.toDegrees(yaw), 0.0f);
                    body = CloneBody.entity(plugin, owner, at, 64.0, viewer -> ParticleFx.canSee(viewer, owner), look.tint());
                }
                if (body != null) {
                    body.moveTo(x, last.getY(), z, Math.toDegrees(yaw));
                    trail(owner, gradient, world, x, last.getY(), z, yaw, pose, fade, quality);
                } else {
                    figure(owner, gradient, world, x, last.getY(), z, yaw, pose, fade, quality, look.tint() == CloneBody.Tint.GRAY);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * A clone that runs from {@code from} to {@code to} in {@code ticks} ticks (a dash through enemies); {@code onStep} gets the tick (0 - ticks - 1), which the caller
     * can use to hit what the clone passes.
     */
    static void run(JavaPlugin plugin, Player owner, Look look, Location from, Location to, int ticks, IntConsumer onStep, Runnable onEnd) {
        World world = from.getWorld();
        if (world == null) return;
        Gradient gradient = look.gradient();
        boolean quality = look.quality();
        // facing the way it runs
        double yaw = Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ());
        new SafeTask(plugin) {
            private int tick;
            private CloneBody body;
            private boolean bodyTried;

            @Override
            protected void tick() {
                ParticleFx.mark(owner);
                if (tick >= ticks + FADE || !owner.isOnline()) {
                    if (body != null) body.remove();
                    cancel();
                    if (onEnd != null) onEnd.run();
                    return;
                }
                double t = Math.min(1.0, (tick + 1) / (double) ticks);
                double eased = 1.0 - (1.0 - t) * (1.0 - t);
                double x = from.getX() + (to.getX() - from.getX()) * eased;
                double y = from.getY() + (to.getY() - from.getY()) * eased;
                double z = from.getZ() + (to.getZ() - from.getZ()) * eased;
                double fade = tick < ticks ? Math.min(1.0, (tick + 1) / 2.0) : (ticks + FADE - tick) / (double) FADE;
                if (tick < ticks) onStep.accept(tick);
                if (!bodyTried) {
                    bodyTried = true;
                    Location at = new Location(world, x, y, z, (float) Math.toDegrees(yaw), 0.0f);
                    body = CloneBody.entity(plugin, owner, at, 64.0, viewer -> ParticleFx.canSee(viewer, owner), look.tint());
                }
                if (body != null) {
                    body.moveTo(x, y, z, Math.toDegrees(yaw));
                    trail(owner, gradient, world, x, y, z, yaw, tick < ticks ? 0.8 : 0.3, fade, quality);
                } else {
                    figure(owner, gradient, world, x, y, z, yaw, tick < ticks ? 0.8 : 0.3, fade, quality, look.tint() == CloneBody.Tint.GRAY);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The cut at the moment of a blow: a bright arc round the target (a different slant each time). */
    private static void cut(Player owner, Gradient gradient, Location target, double dirX, double dirZ, int index, boolean quality) {
        Fx fx = Fx.of(owner, target.getWorld(), target);
        if (!fx.active()) return;
        double base = Math.atan2(dirZ, dirX);
        double tilt = (index % 2 == 0 ? 1.0 : -1.0) * 0.6;
        int points = quality ? 22 : 14;
        for (int index2 = 0; index2 < points; index2++) {
            double t = index2 / (double) (points - 1);
            double a = base + (t - 0.5) * 2.2 * (index % 2 == 0 ? 1.0 : -1.0);
            double radius = 1.3;
            fx.dust(target.getX() + Trig.cos(a) * radius, target.getY() + 1.0 + tilt * (t - 0.5) * 1.4 + 0.2, target.getZ() + Trig.sin(a) * radius, gradient, 0.35 + 0.65 * t, quality ? 1.3 : 1.1);
        }
        fx.dust(target.getX(), target.getY() + 1.0, target.getZ(), gradient, 1.0, 1.8);
    }

    /**
     * A blow drawn as one straight horizontal line through the target at {@code yaw} (the slashes of the item swap's echo): a long thin streak, bright at the middle, with a
     * glint where it crosses the target.
     */
    static void lineCut(Player owner, Gradient gradient, Location target, double yaw, boolean quality) {
        Fx fx = Fx.of(owner, target.getWorld(), target);
        if (!fx.active()) return;
        double dx = Math.cos(yaw);
        double dz = Math.sin(yaw);
        double y = target.getY() + 1.0;
        Location from = new Location(target.getWorld(), target.getX() - dx * 2.6, y, target.getZ() - dz * 2.6);
        Location to = new Location(target.getWorld(), target.getX() + dx * 2.6, y, target.getZ() + dz * 2.6);
        fx.line(from, to, gradient, 0.2, 1.0, 0.0, quality ? 1.3 : 1.1, 0.12, null, 1);
        fx.line(from.clone().add(0.0, 0.1, 0.0), to.clone().add(0.0, 0.1, 0.0), gradient, 0.6, 0.9, 0.0, 0.8, 0.2, null, 1);
        fx.dust(target.getX(), y, target.getZ(), gradient, 1.0, 2.0);
    }

    /**
     * One frame of the figure at ({@code x}, {@code y}, {@code z}) turned {@code yaw} (the way it faces): {@code pose} 0 is the guard, 1 the blade fully down; {@code fade}
     * 0 - 1 takes the brightness down.
     */
    static void figure(Player owner, Gradient gradient, World world, double x, double y, double z, double yaw, double pose, double fade, boolean quality) {
        figure(owner, gradient, world, x, y, z, yaw, pose, fade, quality, false);
    }

    /** The same; {@code ghost} draws it sparser and fainter, grey and see-through. */
    static void figure(Player owner, Gradient gradient, World world, double x, double y, double z, double yaw, double pose, double fade, boolean quality, boolean ghost) {
        Fx fx = Fx.of(owner, world, x, y, z);
        if (!fx.active()) return;
        if (ghost) fade *= 0.65;
        double fx0 = -Math.sin(yaw);
        double fz0 = Math.cos(yaw);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double bright = 0.25 + 0.75 * fade;
        double size = (quality ? 1.15 : 1.0) * (0.7 + 0.3 * fade);
        // head
        Location head = new Location(world, x, y + 1.72, z);
        fx.sphereShell(head, 0.2, 0.0, quality ? 26 : 16, gradient, size * 0.9);
        // body: two edges and the line down the middle, the shoulders across
        double step = ghost ? 0.26 : quality ? 0.1 : 0.16;
        for (double h = 0.85; h <= 1.45; h += step) {
            double t = bright * (0.3 + 0.5 * (h - 0.85) / 0.6);
            fx.dust(x + rx * 0.17, y + h, z + rz * 0.17, gradient, t, size);
            fx.dust(x - rx * 0.17, y + h, z - rz * 0.17, gradient, t, size);
            if (quality) fx.dust(x, y + h, z, gradient, t * 0.8, size);
        }
        // legs: one a step forward when the blade comes down
        double stride = 0.1 + 0.35 * pose;
        fx.line(new Location(world, x + rx * 0.12, y + 0.85, z + rz * 0.12), new Location(world, x + rx * 0.16 + fx0 * stride, y, z + rz * 0.16 + fz0 * stride), gradient, 0.2 * bright, 0.7 * bright, 0.0, size, step, null, 1);
        fx.line(new Location(world, x - rx * 0.12, y + 0.85, z - rz * 0.12), new Location(world, x - rx * 0.16 - fx0 * stride * 0.5, y, z - rz * 0.16 - fz0 * stride * 0.5), gradient, 0.2 * bright, 0.7 * bright, 0.0, size, step, null, 1);
        // the arm with the blade: raised at first, coming down and forward as the pose rises
        double shoulderX = x + rx * 0.3;
        double shoulderZ = z + rz * 0.3;
        double handX = shoulderX + fx0 * (0.1 + 0.6 * pose) + rx * 0.1;
        double handY = y + 1.45 + 0.5 - 1.0 * pose;
        double handZ = shoulderZ + fz0 * (0.1 + 0.6 * pose) + rz * 0.1;
        fx.line(new Location(world, shoulderX, y + 1.45, shoulderZ), new Location(world, handX, handY, handZ), gradient, 0.4 * bright, 0.8 * bright, 0.0, size, step, null, 1);
        double bladeX = fx0 * (0.9 + 0.5 * pose);
        double bladeY = 0.9 * (1.0 - pose) + (-0.3) * pose;
        double bladeZ = fz0 * (0.9 + 0.5 * pose);
        fx.line(new Location(world, handX, handY, handZ), new Location(world, handX + bladeX, handY + bladeY, handZ + bladeZ), gradient, 0.7 * bright, 1.0, 0.0, size * 1.1, step, null, 1);
        // the other arm hangs
        fx.line(new Location(world, x - rx * 0.3, y + 1.45, z - rz * 0.3), new Location(world, x - rx * 0.36 + fx0 * 0.1, y + 0.9, z - rz * 0.36 + fz0 * 0.1), gradient, 0.3 * bright, 0.6 * bright, 0.0, size, step, null, 1);
        trail(owner, gradient, world, x, y, z, yaw, pose, fade, quality);
    }

    /** The trail of shadow behind a clone (long lines streaming back, and with quality afterimages of the head): the part drawn also when the body is an entity. */
    static void trail(Player owner, Gradient gradient, World world, double x, double y, double z, double yaw, double pose, double fade, boolean quality) {
        Fx fx = Fx.of(owner, world, x, y, z);
        if (!fx.active()) return;
        double fx0 = -Math.sin(yaw);
        double fz0 = Math.cos(yaw);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double bright = 0.25 + 0.75 * fade;
        double size = (quality ? 1.15 : 1.0) * (0.7 + 0.3 * fade);
        double step = quality ? 0.1 : 0.16;
        // a trail of shadow behind: long lines streaming back (and, with quality, afterimages of the head)
        int streams = quality ? 5 : 3;
        for (int stream = 0; stream < streams; stream++) {
            double spread = (stream - (streams - 1) / 2.0) * 0.18;
            double h = 1.2 + 0.1 * stream;
            Location from = new Location(world, x + rx * spread, y + h, z + rz * spread);
            Location to = new Location(world, x + rx * spread - fx0 * (0.9 + 0.3 * pose), y + h - 0.35, z + rz * spread - fz0 * (0.9 + 0.3 * pose));
            fx.line(from, to, gradient, 0.5 * bright, 0.05, 1.5, size * 0.8, step * 1.5, null, 1);
        }
        if (quality) {
            for (int image = 1; image <= 2; image++) {
                fx.sphereShell(new Location(world, x - fx0 * 0.45 * image, y + 1.72, z - fz0 * 0.45 * image), 0.2 - 0.04 * image, 0.0, 14, gradient, size * (0.8 - 0.2 * image));
            }
        }
    }
}
