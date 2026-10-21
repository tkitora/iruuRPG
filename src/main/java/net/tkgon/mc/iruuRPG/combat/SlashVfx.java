package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Curves;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
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
            int stride,
            Gradient gradient,
            double tailT,
            double acrossShare
    ) {
        /** A layer without a gradient (one color). */
        public Layer(Particle particle, Color color, float size, int thickness, double spacing, double offsetX, double offsetY,
                     double offsetZ, double startSeconds, double displaySeconds, int stride) {
            this(particle, color, size, thickness, spacing, offsetX, offsetY, offsetZ, startSeconds, displaySeconds, stride, null, 0.0, 0.0);
        }

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

        /**
         * A ribbon of {@code thickness} lines colored by {@code gradient}: across the band the color goes from the middle
         * (0) to the outside (1) by {@code acrossShare} of the way, and along the arc from {@code tailT} at the start to
         * the head (an exponential curve), so a slash has a dark tail, a bright head and a core that is lighter outside.
         */
        public static Layer ribbon(Gradient gradient, float size, int thickness, double spacing, double tailT, double acrossShare) {
            return new Layer(Particle.REDSTONE, null, size, thickness, spacing, 0, 0, 0, 0, 10, 1, gradient, tailT, acrossShare);
        }

        /** A plain particle layer (flame, end rod, ...) drawn at every {@code stride}-th point. */
        public static Layer spark(Particle particle, int stride) {
            return new Layer(particle, null, 1f, 1, 0, 0, 0, 0, 0, 10, stride);
        }
    }

    /** What the slash looks like: an {@code ARC} of even width, a {@code DROP} (a teardrop: fat at the leading end, a point at the tail) or a straight {@code LINE}. */
    public enum Kind {
        ARC, DROP, LINE;

        /** Reads arc / drop (teardrop) / line; null for anything else. */
        public static Kind parse(String word) {
            if (word == null) return null;
            return switch (word.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "arc", "circle", "円弧" -> ARC;
                case "drop", "teardrop", "雫", "雫型" -> DROP;
                case "line", "straight", "直線" -> LINE;
                default -> null;
            };
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
            int samplesPerBlock,
            boolean reverse,
            Kind kind
    ) {
        public Shape {
            if (radius <= 0 || sweepDegrees <= 0 || sweepDegrees > 360 || samplesPerBlock < 1) {
                throw new IllegalArgumentException("bad shape");
            }
            kind = kind == null ? Kind.ARC : kind;
        }

        public Shape(double radius, double sweepDegrees, double height, double depth, double yaw, double pitch, double roll,
                     int samplesPerBlock, boolean reverse) {
            this(radius, sweepDegrees, height, depth, yaw, pitch, roll, samplesPerBlock, reverse, Kind.ARC);
        }

        public Shape(double radius, double sweepDegrees, double height, double depth, double yaw, double pitch, double roll,
                     int samplesPerBlock) {
            this(radius, sweepDegrees, height, depth, yaw, pitch, roll, samplesPerBlock, false);
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
        /** For a gradient layer: the color position and the size of each point. */
        private final float[][][] colorAt;
        private final float[][][] sizeAt;
        private final Shape shape;
        /** The player whose slash this is (their particle settings and the others' hiding apply to it). */
        private final org.bukkit.entity.Player owner = ParticleFx.owner();

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
            this.colorAt = new float[layers.length][][];
            this.sizeAt = new float[layers.length][][];
            for (int i = 0; i < layers.length; i++) {
                Layer layer = layers[i];
                if (layer == null) {
                    cached[i] = new Vec3[0][];
                    continue;
                }
                cached[i] = new Vec3[sampleCount][];
                if (layer.gradient() != null) {
                    colorAt[i] = new float[sampleCount][];
                    sizeAt[i] = new float[sampleCount][];
                }
                for (int s = 0; s < sampleCount; s++) {
                    if (s % layer.stride() != 0) {
                        cached[i][s] = new Vec3[0];
                        continue;
                    }
                    cached[i][s] = new Vec3[layer.thickness()];
                    double u = (double) s / (sampleCount - 1);
                    if (layer.gradient() != null) {
                        colorAt[i][s] = new float[layer.thickness()];
                        sizeAt[i][s] = new float[layer.thickness()];
                    }
                    for (int j = 0; j < layer.thickness(); j++) {
                        double offset = (j - (layer.thickness() - 1) / 2.0) * layer.spacing();
                        cached[i][s][j] = calculatePoint(u, layer, offset);
                        if (layer.gradient() != null) {
                            // along the arc: the tail is deep and small, the head bright and full size (exponential curve)
                            double along = Curves.expOut(u, 2.5);
                            double half = (layer.thickness() - 1) / 2.0;
                            double across = half <= 0.0 ? 0.0 : Math.abs(j - half) / half;
                            double t = layer.tailT() + (1.0 - layer.tailT()) * along;
                            colorAt[i][s][j] = (float) Curves.clamp01(t * (1.0 - layer.acrossShare()) + across * layer.acrossShare());
                            sizeAt[i][s][j] = (float) (layer.size() * (0.55 + 0.45 * Math.pow(Math.sin(Math.PI * Math.min(1.0, 0.12 + 0.88 * u)), 0.5)));
                        }
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
            // a reversed arc is drawn from its other end, with the same bulge
            if (shape.reverse()) u = 1.0 - u;
            double sweep = Math.toRadians(shape.sweepDegrees());
            double theta = (u - 0.5) * sweep;

            double x;
            double y;
            double nx;
            double ny;
            if (shape.kind() == Kind.LINE) {
                // a straight cut as long as the chord of the arc, with the same bulge forward
                double half = shape.radius() * Math.sin(sweep / 2.0);
                x = half * (2.0 * u - 1.0);
                y = shape.height() * Math.sin(Math.PI * u);
                nx = 0.0;
                ny = 1.0;
            } else {
                x = shape.radius() * Math.sin(theta);
                y = shape.radius() * (Math.cos(theta) - Math.cos(sweep / 2.0))
                        + shape.height() * Math.sin(Math.PI * u);
                // normal of the centre line in the XY plane, for the thickness lines
                double dx = shape.radius() * Math.cos(theta);
                double dy = -shape.radius() * Math.sin(theta)
                        + shape.height() * Math.cos(Math.PI * u) * Math.PI / sweep;
                double length = Math.sqrt(dx * dx + dy * dy);
                nx = length == 0 ? 0 : -dy / length;
                ny = length == 0 ? 1 : dx / length;
            }
            double z = shape.depth() * Math.sin(Math.PI * u);
            // a teardrop: the band is a point at the tail, fattest near the leading end and round at the very end
            if (shape.kind() == Kind.DROP) thicknessOffset *= dropWidth(u) * 1.8;

            x += nx * thicknessOffset + layer.offsetX();
            y += ny * thicknessOffset + layer.offsetY();
            z += layer.offsetZ();

            Vec3 rotated = rotate(x, y, z);
            return new Vec3(ox + rotated.x(), oy + rotated.y(), oz + rotated.z());
        }

        /** Width of a teardrop along 0 (tail, a point) - 1 (the leading end, round): 0 at both ends, fattest at about 0.8. */
        private static double dropWidth(double u) {
            double rise = Math.sin(Math.PI / 2.0 * Math.min(1.0, u / 0.85));
            double round = u <= 0.85 ? 1.0 : Math.sqrt(Math.max(0.0, 1.0 - Math.pow((u - 0.85) / 0.15, 2.0)));
            return Math.pow(rise, 1.2) * round;
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
                    Vec3[] points = cached[layerIndex][s];
                    for (int j = 0; j < points.length; j++) {
                        if (layer.gradient() != null) {
                            ParticleFx.to(owner, world).spawnParticle(Particle.REDSTONE, points[j].x(), points[j].y(), points[j].z(), 1, 0, 0, 0, 0,
                                    layer.gradient().dust(colorAt[layerIndex][s][j], sizeAt[layerIndex][s][j]));
                        } else {
                            spawn(layer, points[j]);
                        }
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
                ParticleFx.to(owner, world).spawnParticle(Particle.REDSTONE, p.x(), p.y(), p.z(), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(layer.color(), layer.size()));
            } else {
                ParticleFx.to(owner, world).spawnParticle(layer.particle(), p.x(), p.y(), p.z(), 1, 0, 0, 0, 0);
            }
        }

        private void spawnTip(Layer layer, Vec3 p) {
            if (layer.particle() == Particle.REDSTONE && layer.color() != null) {
                ParticleFx.to(owner, world).spawnParticle(Particle.REDSTONE, p.x(), p.y(), p.z(), Math.max(1, layer.thickness()),
                        0.04, 0.04, 0.04, 0, new Particle.DustOptions(layer.color(), layer.size()));
            } else {
                ParticleFx.to(owner, world).spawnParticle(layer.particle(), p.x(), p.y(), p.z(), Math.max(1, layer.thickness()),
                        0.04, 0.04, 0.04, 0);
            }
        }
    }
}
