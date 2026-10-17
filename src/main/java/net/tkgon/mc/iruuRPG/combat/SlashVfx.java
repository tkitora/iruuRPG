package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Slash visuals: the points of an arc are worked out once when it is started and drawn by one shared task that
 * reveals the arc from its start to its end over the given time (so it looks like a blade sweeping through).
 *
 * <p>Shape (local axes, before the rotation): x runs across the arc, y is up, z is the way the slash faces. Several
 * {@link Layer}s (glow / body / core / sparks) are stacked on the same arc, and an optional tip layer is drawn at the
 * leading end every tick.
 */
public final class SlashVfx {

    private final Plugin plugin;
    private final List<Instance> active = new ArrayList<>();
    private BukkitTask task;

    public SlashVfx(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * One layer of particles along the arc. {@code thickness} lines, {@code spacing} apart, are drawn side by side;
     * only every {@code stride}-th sample point of the arc gets a particle (thin out sparks).
     */
    public record Layer(
            Particle particle,
            Color color,
            float size,
            int thickness,
            double spacing,
            double offsetX,
            double offsetY,
            double offsetZ,
            double startSeconds,
            double displaySeconds,
            int stride
    ) {
        public Layer {
            if (particle == null || size <= 0 || thickness < 1 || spacing < 0
                    || startSeconds < 0 || displaySeconds < 0 || stride < 1) {
                throw new IllegalArgumentException("bad layer");
            }
        }

        /** A dust layer (colored) that stays for the whole slash. */
        public static Layer dust(Color color, float size, int thickness, double spacing) {
            return new Layer(Particle.REDSTONE, color, size, thickness, spacing, 0, 0, 0, 0, 10, 1);
        }

        /** A plain particle layer (flame, end rod, ...) drawn at every {@code stride}-th point. */
        public static Layer spark(Particle particle, int stride) {
            return new Layer(particle, null, 1f, 1, 0, 0, 0, 0, 0, 10, stride);
        }
    }

    /** Where the arc goes: radius, how far around it sweeps, bulge up/forward, rotation (degrees). */
    public record Shape(
            double radius,
            double sweepDegrees,
            double height,
            double depth,
            double yaw,
            double pitch,
            double roll,
            int samplesPerBlock
    ) {
        public Shape {
            if (radius <= 0 || sweepDegrees <= 0 || sweepDegrees > 360 || samplesPerBlock < 1) {
                throw new IllegalArgumentException("bad shape");
            }
        }
    }

    public void play(Location origin, Layer[] layers, double duration, Shape shape, Layer tipLayer) {
        if (!plugin.isEnabled()) return;
        if (origin == null || origin.getWorld() == null || layers == null || shape == null || duration <= 0) return;

        active.add(new Instance(origin.getWorld(), origin.getX(), origin.getY(), origin.getZ(),
                layers.clone(), duration, shape, tipLayer));
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
        }
    }

    private void tick() {
        Iterator<Instance> iterator = active.iterator();
        while (iterator.hasNext()) {
            Instance effect = iterator.next();
            if (effect.finished()) {
                iterator.remove();
            } else {
                effect.renderTick();
            }
        }
        if (active.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        active.clear();
    }

    private record Vec3(double x, double y, double z) {
    }

    private static final class Instance {

        private final World world;
        private final double ox;
        private final double oy;
        private final double oz;
        private final Layer[] layers;
        private final Layer tipLayer;
        private final int totalTicks;
        private final int sampleCount;
        private final Vec3[][][] cached;
        private final Shape shape;

        private int tick;
        private int lastSample = -1;

        Instance(World world, double ox, double oy, double oz, Layer[] layers, double duration, Shape shape, Layer tipLayer) {
            this.world = world;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.layers = layers;
            this.shape = shape;
            this.tipLayer = tipLayer;
            this.totalTicks = Math.max(1, (int) Math.ceil(duration * 20.0));

            double arcLength = shape.radius() * Math.toRadians(shape.sweepDegrees());
            this.sampleCount = Math.max(2, (int) Math.ceil(arcLength * shape.samplesPerBlock()) + 1);

            this.cached = new Vec3[layers.length][][];
            for (int i = 0; i < layers.length; i++) {
                Layer layer = layers[i];
                if (layer == null) {
                    cached[i] = new Vec3[0][];
                    continue;
                }
                cached[i] = new Vec3[sampleCount][];
                for (int s = 0; s < sampleCount; s++) {
                    if (s % layer.stride() != 0) {
                        cached[i][s] = new Vec3[0];
                        continue;
                    }
                    cached[i][s] = new Vec3[layer.thickness()];
                    double u = (double) s / (sampleCount - 1);
                    for (int j = 0; j < layer.thickness(); j++) {
                        double offset = (j - (layer.thickness() - 1) / 2.0) * layer.spacing();
                        cached[i][s][j] = calculatePoint(u, layer, offset);
                    }
                }
            }
        }

        /** Rotates a local point (roll, then pitch, then yaw) and returns it in world axes. */
        private Vec3 rotate(double x, double y, double z) {
            double yaw = Math.toRadians(shape.yaw());
            double pitch = Math.toRadians(shape.pitch());
            double roll = Math.toRadians(shape.roll());

            double cr = Math.cos(roll);
            double sr = Math.sin(roll);
            double cp = Math.cos(pitch);
            double sp = Math.sin(pitch);
            double cy = Math.cos(yaw);
            double sy = Math.sin(yaw);

            double x1 = x * cr - y * sr;
            double y1 = x * sr + y * cr;

            double y2 = y1 * cp - z * sp;
            double z2 = y1 * sp + z * cp;

            double x3 = x1 * cy - z2 * sy;
            double z3 = x1 * sy + z2 * cy;

            return new Vec3(x3, y2, z3);
        }

        private Vec3 calculatePoint(double u, Layer layer, double thicknessOffset) {
            double sweep = Math.toRadians(shape.sweepDegrees());
            double theta = (u - 0.5) * sweep;

            double x = shape.radius() * Math.sin(theta);
            double y = shape.radius() * (Math.cos(theta) - Math.cos(sweep / 2.0))
                    + shape.height() * Math.sin(Math.PI * u);
            double z = shape.depth() * Math.sin(Math.PI * u);

            // normal of the centre line in the XY plane, for the thickness lines
            double dx = shape.radius() * Math.cos(theta);
            double dy = -shape.radius() * Math.sin(theta)
                    + shape.height() * Math.cos(Math.PI * u) * Math.PI / sweep;
            double length = Math.sqrt(dx * dx + dy * dy);
            double nx = length == 0 ? 0 : -dy / length;
            double ny = length == 0 ? 1 : dx / length;

            x += nx * thicknessOffset + layer.offsetX();
            y += ny * thicknessOffset + layer.offsetY();
            z += layer.offsetZ();

            Vec3 rotated = rotate(x, y, z);
            return new Vec3(ox + rotated.x(), oy + rotated.y(), oz + rotated.z());
        }

        boolean finished() {
            return tick >= totalTicks;
        }

        void renderTick() {
            if (!world.isChunkLoaded(((int) Math.floor(ox)) >> 4, ((int) Math.floor(oz)) >> 4)) {
                tick++;
                return;
            }

            double progress = Math.min(1.0, (double) (tick + 1) / totalTicks);
            int currentSample = Math.min(sampleCount - 1, (int) Math.floor(progress * (sampleCount - 1)));

            for (int layerIndex = 0; layerIndex < layers.length; layerIndex++) {
                Layer layer = layers[layerIndex];
                if (layer == null || layer.displaySeconds() <= 0) continue;

                double elapsed = (double) tick / 20.0;
                if (elapsed < layer.startSeconds() || elapsed >= layer.startSeconds() + layer.displaySeconds()) continue;

                int from = Math.max(0, lastSample + 1);
                for (int s = from; s <= currentSample; s++) {
                    for (Vec3 point : cached[layerIndex][s]) {
                        spawn(layer, point);
                    }
                }
            }

            if (tipLayer != null) {
                spawnTip(tipLayer, calculatePoint(progress, tipLayer, 0));
            }

            lastSample = Math.max(lastSample, currentSample);
            tick++;
        }

        private void spawn(Layer layer, Vec3 p) {
            if (layer.particle() == Particle.REDSTONE && layer.color() != null) {
                world.spawnParticle(Particle.REDSTONE, p.x(), p.y(), p.z(), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(layer.color(), layer.size()));
            } else {
                world.spawnParticle(layer.particle(), p.x(), p.y(), p.z(), 1, 0, 0, 0, 0);
            }
        }

        private void spawnTip(Layer layer, Vec3 p) {
            if (layer.particle() == Particle.REDSTONE && layer.color() != null) {
                world.spawnParticle(Particle.REDSTONE, p.x(), p.y(), p.z(), Math.max(1, layer.thickness()),
                        0.04, 0.04, 0.04, 0, new Particle.DustOptions(layer.color(), layer.size()));
            } else {
                world.spawnParticle(layer.particle(), p.x(), p.y(), p.z(), Math.max(1, layer.thickness()),
                        0.04, 0.04, 0.04, 0);
            }
        }
    }
}
