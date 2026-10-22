package net.tkgon.mc.iruuRPG.vfx.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The parts every flow shares: the common arguments (frame, number, life, size, start radius, test motion, the two colors) and a sphere to be born in. */
abstract class BaseFlow implements FlowEffect {

    private static final List<Param> COMMON = List.of(
            Param.choice("mode", "absolute", List.of("absolute", "relative"),
                    "absolute = 粒はその場に残る (中心が動くと軌跡になる) / relative = 粒は中心について動く"),
            Param.number("count", 120, 12, 600, "同時にいる粒の数"),
            Param.number("life", 40, 5, 200, "粒の寿命 (tick)"),
            Param.number("size", 0.9, 0.3, 3.0, "粒の大きさ"),
            Param.number("radius", 0.4, 0.0, 6.0, "粒が生まれる球の半径 (ブロック)"),
            Param.number("move", 0, 0, 8, "確認用: 中心を半径3の円で動かす速さ (ブロック/秒)。0 で動かさない"),
            Param.color("color1", "#3a7bff", "グラデーションの始まりの色 (生まれたとき)"),
            Param.color("color2", "#ffffff", "グラデーションの終わりの色 (消えるとき)"));

    /** The arguments particular to the effect. */
    protected abstract List<Param> own();

    @Override
    public final List<Param> params() {
        List<Param> all = new ArrayList<>(own());
        all.addAll(COMMON);
        return all;
    }

    /** A random point in the ball of radius {@code radius} (even in volume), written into {@code out[0..2]}. */
    protected static void inBall(Random random, double radius, double[] out) {
        double x;
        double y;
        double z;
        do {
            x = random.nextDouble() * 2.0 - 1.0;
            y = random.nextDouble() * 2.0 - 1.0;
            z = random.nextDouble() * 2.0 - 1.0;
        } while (x * x + y * y + z * z > 1.0);
        out[0] = x * radius;
        out[1] = y * radius;
        out[2] = z * radius;
    }
}
