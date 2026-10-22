package net.tkgon.mc.iruuRPG.vfx.flow;

import net.tkgon.mc.iruuRPG.combat.ParticleFx;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Runs one flow: a few hundred particles in plain arrays (no objects per particle), moved by the effect's field every tick and drawn as dust along
 * the gradient from {@code color1} (born) to {@code color2} (dying), through {@link Fx} (so far-away flows are culled and players' particle settings hold).
 *
 * <p>The frame: positions are kept relative to a centre. In {@code relative} mode that is the centre as it moves, so the whole flow travels with it;
 * in {@code absolute} mode it is the centre where the flow began, so the particles are left behind when the centre moves.
 * The field always sees the position in that frame.
 */
public final class FlowRunner {

    /** At most this many running flows for one player (the test command is for admins, but a loop of them should not bring the server down). */
    private static final int MAX_PER_OWNER = 6;
    private static final Map<UUID, Integer> RUNNING = new HashMap<>();

    private FlowRunner() {
    }

    /** Starts a flow for {@code ticks} ticks of birth (the last particles then live out their lives). False when the player already has too many. */
    public static boolean start(JavaPlugin plugin, Player owner, FlowOrigin origin, FlowEffect effect, FlowArgs args, int ticks) {
        UUID ownerId = owner == null ? null : owner.getUniqueId();
        if (ownerId != null && RUNNING.getOrDefault(ownerId, 0) >= MAX_PER_OWNER) return false;
        if (ownerId != null) RUNNING.merge(ownerId, 1, Integer::sum);

        int capacity = args.integer("count") + 32;
        int life = args.integer("life");
        double size = args.number("size");
        boolean relative = "relative".equals(args.choice("mode"));
        Gradient gradient = Gradient.of(args.color("color1"), args.color("color2"));
        double perTick = args.integer("count") / (double) life;
        double move = args.number("move");
        Random random = new Random();
        VectorField field = effect.field(args, random.nextLong());
        boolean accelerates = effect.accelerates();
        double inertia = effect.inertia();
        Location start = (move > 0.0 ? FlowOrigin.circling(origin.current(0), 3.0, move) : origin).current(0);
        FlowOrigin centre = move > 0.0 ? FlowOrigin.circling(start, 3.0, move) : origin;

        double[] px = new double[capacity];
        double[] py = new double[capacity];
        double[] pz = new double[capacity];
        double[] vx = new double[capacity];
        double[] vy = new double[capacity];
        double[] vz = new double[capacity];
        int[] age = new int[capacity];
        int[] span = new int[capacity];
        double[] position = new double[3];
        double[] velocity = new double[3];
        double[] out = new double[3];

        new BukkitRunnable() {
            private int tick;
            private int alive;
            private double owed;

            @Override
            public void run() {
                try {
                    step();
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("vfx flow stopped after an error: " + exception);
                    finish();
                }
            }

            private void finish() {
                cancel();
                if (ownerId != null) RUNNING.merge(ownerId, -1, (a, b) -> Math.max(0, a + b));
            }

            private void step() {
                World world = start.getWorld();
                if (world == null || (owner != null && !owner.isOnline())) {
                    finish();
                    return;
                }
                Location now = centre.current(tick);
                // the centre's place in the flow's frame: the origin of the frame is the centre now (relative) or where the flow began (absolute)
                double shiftX = relative ? 0.0 : now.getX() - start.getX();
                double shiftY = relative ? 0.0 : now.getY() - start.getY();
                double shiftZ = relative ? 0.0 : now.getZ() - start.getZ();
                if (tick < ticks) {
                    owed += perTick;
                    while (owed >= 1.0 && alive < capacity) {
                        owed -= 1.0;
                        effect.spawn(random, args, position, velocity);
                        px[alive] = position[0] + shiftX;
                        py[alive] = position[1] + shiftY;
                        pz[alive] = position[2] + shiftZ;
                        vx[alive] = velocity[0];
                        vy[alive] = velocity[1];
                        vz[alive] = velocity[2];
                        age[alive] = 0;
                        span[alive] = (int) Math.max(3, Math.round(life * (0.8 + 0.4 * random.nextDouble())));
                        alive++;
                    }
                }
                if (alive == 0 && tick >= ticks) {
                    finish();
                    return;
                }
                if (owner != null) ParticleFx.mark(owner);
                double baseX = relative ? now.getX() : start.getX();
                double baseY = relative ? now.getY() : start.getY();
                double baseZ = relative ? now.getZ() : start.getZ();
                Fx fx = Fx.of(owner, world, now);
                for (int index = 0; index < alive; ) {
                    field.sample(px[index], py[index], pz[index], tick, out);
                    if (accelerates) {
                        vx[index] = (vx[index] + out[0]) * 0.98;
                        vy[index] = (vy[index] + out[1]) * 0.98;
                        vz[index] = (vz[index] + out[2]) * 0.98;
                    } else {
                        vx[index] += (out[0] - vx[index]) * inertia;
                        vy[index] += (out[1] - vy[index]) * inertia;
                        vz[index] += (out[2] - vz[index]) * inertia;
                    }
                    px[index] += vx[index];
                    py[index] += vy[index];
                    pz[index] += vz[index];
                    age[index]++;
                    if (age[index] >= span[index]) {
                        // swap the last one into this place (the order does not matter)
                        alive--;
                        px[index] = px[alive];
                        py[index] = py[alive];
                        pz[index] = pz[alive];
                        vx[index] = vx[alive];
                        vy[index] = vy[alive];
                        vz[index] = vz[alive];
                        age[index] = age[alive];
                        span[index] = span[alive];
                        continue;
                    }
                    fx.dust(baseX + px[index], baseY + py[index], baseZ + pz[index], gradient, age[index] / (double) span[index], size);
                    index++;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }
}
