package net.tkgon.mc.iruuRPG.vfx.flow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** The flow effects there are, by name. */
public final class FlowEffects {

    private static final Map<String, FlowEffect> ALL = new LinkedHashMap<>();

    static {
        register(new Turbulence());
        register(new Vortex());
        register(new Ripple());
        register(new Orbit());
        register(new Fountain());
    }

    private FlowEffects() {
    }

    public static void register(FlowEffect effect) {
        ALL.put(effect.id(), effect);
    }

    public static FlowEffect find(String id) {
        return id == null ? null : ALL.get(id.toLowerCase(Locale.ROOT));
    }

    public static List<FlowEffect> all() {
        return new ArrayList<>(ALL.values());
    }

    // ---- turbulence ---------------------------------------------------------------------------------------------------------

    /** Smoke-like churning: three noise reads a sample. */
    static final class Turbulence extends BaseFlow {
        @Override
        public String id() {
            return "turbulence";
        }

        @Override
        public String summary() {
            return "ノイズ (sine / value) の乱流で漂う、煙のような流れ。cache=1 で速度場を格子に焼いて軽くする";
        }

        @Override
        protected List<Param> own() {
            return List.of(
                    Param.choice("noise", "sine", NoiseSource.TYPES, "ノイズの種類。sine = 三角関数の重ね合わせ (最も軽い) / value = 格子の値ノイズ"),
                    Param.number("octaves", 2, 1, 4, "ノイズのオクターブ数 (多いほど細かい乱れ、重い)"),
                    Param.number("frequency", 0.45, 0.05, 3.0, "ノイズの空間周波数 (大きいほど渦が小さい)"),
                    Param.number("strength", 0.12, 0.005, 0.6, "流れの強さ (ブロック/tick)"),
                    Param.number("drift", 0.04, 0.0, 0.5, "ノイズの模様が流れる速さ (時間変化)"),
                    Param.number("rise", 0.03, -0.1, 0.2, "ずっと上向きにかける速さ (煙なら正)"),
                    Param.number("cache", 0, 0, 1, "1 で速度場を最初に格子 (24x24x24) に焼いて読む (時間変化は止まる)"));
        }

        @Override
        public VectorField field(FlowArgs args, long seed) {
            NoiseSource noise = NoiseSource.create(args.choice("noise"), args.integer("octaves"), seed);
            VectorField field = new TurbulenceField(noise, args.number("frequency"), args.number("strength"), args.number("drift"), args.number("rise"));
            if (args.integer("cache") == 1) return new GridCachedField(field, 8.0, 24, 0.0);
            return field;
        }

        @Override
        public void spawn(Random random, FlowArgs args, double[] position, double[] velocity) {
            inBall(random, args.number("radius"), position);
            velocity[0] = 0.0;
            velocity[1] = 0.0;
            velocity[2] = 0.0;
        }
    }

    // ---- vortex --------------------------------------------------------------------------------------------------------------

    /** A swirl round the vertical axis through the centre, drawn in a little and lifted. */
    static final class Vortex extends BaseFlow {
        @Override
        public String id() {
            return "vortex";
        }

        @Override
        public String summary() {
            return "中心の縦軸のまわりを渦巻いて、中へ引き込まれながら昇る流れ (トルネード・吸い込み)";
        }

        @Override
        protected List<Param> own() {
            return List.of(
                    Param.number("swirl", 0.22, 0.0, 1.0, "回る速さ (ブロック/tick)"),
                    Param.number("pull", 0.03, -0.2, 0.2, "中心へ引く速さ (負だと外へ広がる)"),
                    Param.number("rise", 0.05, -0.1, 0.3, "昇る速さ"),
                    Param.number("spread", 2.5, 0.0, 8.0, "生まれる輪の半径 (ブロック)"));
        }

        @Override
        public VectorField field(FlowArgs args, long seed) {
            double swirl = args.number("swirl");
            double pull = args.number("pull");
            double rise = args.number("rise");
            return (x, y, z, t, out) -> {
                double r = Math.sqrt(x * x + z * z);
                if (r < 0.05) {
                    out[0] = 0.0;
                    out[1] = rise;
                    out[2] = 0.0;
                    return;
                }
                double fall = 1.0 / (1.0 + 0.08 * r);
                out[0] = (-z / r * swirl - x / r * pull) * fall;
                out[1] = rise;
                out[2] = (x / r * swirl - z / r * pull) * fall;
            };
        }

        @Override
        public void spawn(Random random, FlowArgs args, double[] position, double[] velocity) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = args.number("spread") * (0.6 + 0.4 * random.nextDouble());
            position[0] = Math.cos(angle) * radius;
            position[1] = random.nextDouble() * 0.4;
            position[2] = Math.sin(angle) * radius;
            velocity[0] = 0.0;
            velocity[1] = 0.0;
            velocity[2] = 0.0;
        }
    }

    // ---- ripple --------------------------------------------------------------------------------------------------------------

    /** Rings that spread over the ground, the particles rising and falling in the wave. */
    static final class Ripple extends BaseFlow {
        @Override
        public String id() {
            return "ripple";
        }

        @Override
        public String summary() {
            return "中心から広がる水面の波紋のような流れ (三角関数だけ、とても軽い)";
        }

        @Override
        protected List<Param> own() {
            return List.of(
                    Param.number("speed", 0.12, 0.01, 0.5, "外へ広がる速さ (ブロック/tick)"),
                    Param.number("wavelength", 2.5, 0.5, 8.0, "波の間隔 (ブロック)"),
                    Param.number("amplitude", 0.5, 0.0, 2.0, "波の高さ (ブロック)"),
                    Param.number("frequency", 0.25, 0.05, 1.0, "波の進む速さ (ラジアン/tick)"));
        }

        @Override
        public VectorField field(FlowArgs args, long seed) {
            double speed = args.number("speed");
            double k = Math.PI * 2.0 / args.number("wavelength");
            double amplitude = args.number("amplitude");
            double w = args.number("frequency");
            return (x, y, z, t, out) -> {
                double r = Math.sqrt(x * x + z * z);
                double dx = r < 0.05 ? 0.0 : x / r;
                double dz = r < 0.05 ? 0.0 : z / r;
                out[0] = dx * speed;
                out[1] = amplitude * 0.5 * speed * k * net.tkgon.mc.iruuRPG.vfx.Trig.cos(k * r - w * t);
                out[2] = dz * speed;
            };
        }

        @Override
        public void spawn(Random random, FlowArgs args, double[] position, double[] velocity) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = args.number("radius") * random.nextDouble();
            position[0] = Math.cos(angle) * radius;
            position[1] = 0.1;
            position[2] = Math.sin(angle) * radius;
            velocity[0] = 0.0;
            velocity[1] = 0.0;
            velocity[2] = 0.0;
        }
    }

    // ---- orbit ---------------------------------------------------------------------------------------------------------------

    /** Particles on a sphere that turns about an axis that itself wanders: a few sines and a cross product. */
    static final class Orbit extends BaseFlow {
        @Override
        public String id() {
            return "orbit";
        }

        @Override
        public String summary() {
            return "中心のまわりを、回る軸が少しずつ変わりながら周回する球殻状の流れ (リサージュ風)";
        }

        @Override
        protected List<Param> own() {
            return List.of(
                    Param.number("orbit", 2.0, 0.5, 8.0, "周回する球の半径 (ブロック)"),
                    Param.number("speed", 0.12, 0.01, 0.4, "回る速さ (ラジアン/tick)"),
                    Param.number("wobble", 0.6, 0.0, 1.5, "回る軸のゆらぎの大きさ"));
        }

        @Override
        public VectorField field(FlowArgs args, long seed) {
            double radius = args.number("orbit");
            double speed = args.number("speed");
            double wobble = args.number("wobble");
            Random random = new Random(seed);
            double a = 0.017 + random.nextDouble() * 0.02;
            double b = 0.011 + random.nextDouble() * 0.02;
            return (x, y, z, t, out) -> {
                double wx = wobble * net.tkgon.mc.iruuRPG.vfx.Trig.sin(a * t);
                double wy = 1.0;
                double wz = wobble * net.tkgon.mc.iruuRPG.vfx.Trig.cos(b * t);
                double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
                wx = wx / wl * speed;
                wy = wy / wl * speed;
                wz = wz / wl * speed;
                // v = w x p, plus a pull back toward the shell
                double vx = wy * z - wz * y;
                double vy = wz * x - wx * z;
                double vz = wx * y - wy * x;
                double length = Math.sqrt(x * x + y * y + z * z);
                if (length > 1.0E-3) {
                    double k = (radius - length) * 0.08 / length;
                    vx += x * k;
                    vy += y * k;
                    vz += z * k;
                }
                out[0] = vx;
                out[1] = vy;
                out[2] = vz;
            };
        }

        @Override
        public void spawn(Random random, FlowArgs args, double[] position, double[] velocity) {
            inBall(random, 1.0, position);
            double length = Math.max(1.0E-3, Math.sqrt(position[0] * position[0] + position[1] * position[1] + position[2] * position[2]));
            double radius = args.number("orbit");
            for (int axis = 0; axis < 3; axis++) position[axis] = position[axis] / length * radius;
            velocity[0] = 0.0;
            velocity[1] = 0.0;
            velocity[2] = 0.0;
        }
    }

    // ---- fountain ------------------------------------------------------------------------------------------------------------

    /** Thrown up and falling back: the field is an acceleration (gravity and a light sway), the particles keep their velocity. */
    static final class Fountain extends BaseFlow {
        @Override
        public String id() {
            return "fountain";
        }

        @Override
        public String summary() {
            return "上へ噴き上がって落ちる流れ (重力 + 三角関数の横ゆれ。加速度で動く)";
        }

        @Override
        protected List<Param> own() {
            return List.of(
                    Param.number("power", 0.35, 0.05, 1.0, "噴き上がる初速 (ブロック/tick)"),
                    Param.number("spread", 0.12, 0.0, 1.0, "横への広がり"),
                    Param.number("gravity", 0.025, 0.0, 0.1, "重力 (ブロック/tick^2)"),
                    Param.number("sway", 0.01, 0.0, 0.1, "横ゆれの強さ"));
        }

        @Override
        public boolean accelerates() {
            return true;
        }

        @Override
        public VectorField field(FlowArgs args, long seed) {
            double gravity = args.number("gravity");
            double sway = args.number("sway");
            return (x, y, z, t, out) -> {
                out[0] = sway * net.tkgon.mc.iruuRPG.vfx.Trig.sin(0.9 * y + 0.13 * t + z);
                out[1] = -gravity;
                out[2] = sway * net.tkgon.mc.iruuRPG.vfx.Trig.cos(0.8 * y + 0.11 * t + x);
            };
        }

        @Override
        public void spawn(Random random, FlowArgs args, double[] position, double[] velocity) {
            inBall(random, args.number("radius"), position);
            double angle = random.nextDouble() * Math.PI * 2.0;
            double outward = args.number("spread") * random.nextDouble();
            velocity[0] = Math.cos(angle) * outward;
            velocity[1] = args.number("power") * (0.75 + 0.25 * random.nextDouble());
            velocity[2] = Math.sin(angle) * outward;
        }
    }
}
