package net.tkgon.mc.iruuRPG.tools.procedural.silhouette;

import net.tkgon.mc.iruuRPG.tools.procedural.silhouette.Skeleton.Limb;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * 樹形ごとの「ジェスチャー」定義。
 * 再帰分岐ではなく、シルエット(包絡形状)を先に決め、そこへ向かって枝を曲線で引く。
 * 小さい木は枝の本数が少ないので、1本1本の線の美しさ = 見た目の良さになる。
 */
public final class Shapes {

    public record Built(Skeleton skeleton, FoliageParams foliage) {}

    public interface Shape {
        String id();
        String defaultPalette();
        int defaultHeight();
        Built build(Rng rng, int h);
    }

    public static final Map<String, Shape> ALL = new LinkedHashMap<>();
    static final double GOLDEN = Math.PI * (3 - Math.sqrt(5)); // 137.5°

    static void reg(Shape s) { ALL.put(s.id(), s); }
    public static Shape get(String id) { return ALL.get(id); }

    /** 太い幹は格子点上(2x2)、細い幹はブロック中心(1x1)に置くと量子化がきれい。 */
    static Vec3 baseFor(double r0) { return r0 >= 0.85 ? Vec3.ZERO : new Vec3(0.5, 0, 0.5); }

    static Vec3 rnd(Rng r, double s) { return new Vec3(r.signed() * s, 0, r.signed() * s); }

    /** 地表を這う根。幹の根元から放射状に短く伸ばし、被覆率でハーフ/階段になる。 */
    static void roots(Skeleton sk, Vec3 base, double r0, int count, double len, Rng rng) {
        double a0 = rng.range(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            Vec3 d = Vec3.horizontal(a0 + i * Math.PI * 2 / count + rng.signed() * 0.4);
            Vec3 s = base.add(new Vec3(0, 0.9, 0));
            Vec3 e = base.add(d.mul(r0 + len * rng.range(0.7, 1.2))).withY(0.15);
            sk.add(Skeleton.bezier(s, s.add(d.mul(r0 * 0.6)), e.add(new Vec3(0, 0.2, 0)), e,
                    t -> 0.5 - 0.07 * t, 0.3)); // 先端も THIN_R 以上 → 柱にならずハーフ/階段で終わる
        }
    }

    // ---------------------------------------------------------------- round (広葉樹・オーク)
    static {
        reg(new Shape() {
            public String id() { return "round"; }
            public String defaultPalette() { return "oak"; }
            public int defaultHeight() { return 11; }
            public Built build(Rng rng, int h) {
                Skeleton sk = new Skeleton();
                double r0 = 0.6 + h * 0.045;
                Vec3 base = baseFor(r0);
                Vec3 top = base.add(rnd(rng, h * 0.08)).withY(h * 0.72);
                Vec3 c1 = base.add(rnd(rng, h * 0.06)).withY(h * 0.25);
                Vec3 c2 = top.add(rnd(rng, h * 0.08)).withY(h * 0.5);
                Limb trunk = sk.add(Skeleton.bezier(base, c1, c2, top, Skeleton.taper(r0, 0.35, 1.3, 0.45, 0.08), 0.35));
                if (h >= 10) roots(sk, base, r0, rng.irange(2, 3), 1.2, rng);

                Vec3 C = new Vec3(top.x(), h * 0.6, top.z());
                double Rx = h * 0.42, Ry = h * 0.36;
                int n = (h >= 10 ? 5 : 4) + rng.irange(0, 1);
                double az = rng.range(0, Math.PI * 2);
                for (int i = 0; i < n; i++) {
                    double t = 0.48 + 0.48 * i / (n - 1.0) + rng.signed() * 0.04;
                    Vec3 s = trunk.at(Math.min(t, 0.98));
                    double a = az + i * GOLDEN + rng.signed() * 0.3;
                    Vec3 dh = Vec3.horizontal(a);
                    // 包絡楕円体(ドーム)上に枝先を置く: 低い枝ほど水平、高い枝ほど上向き
                    double e = Math.toRadians(8 + 62 * i / (n - 1.0) + rng.signed() * 8);
                    double k = rng.range(0.7, 0.88);
                    Vec3 tgt = C.add(dh.mul(Math.cos(e) * Rx * k)).add(Vec3.UP.mul(Math.sin(e) * Ry * k));
                    double L = tgt.sub(s).len();
                    double rb = Math.min(trunk.radiusAt(t) * 0.75, 0.7);
                    Limb limb = sk.add(Skeleton.bezier(s, s.add(dh.mul(L * 0.25)).add(Vec3.UP.mul(L * 0.35)),
                            s.lerp(tgt, 0.7).add(Vec3.UP.mul(L * 0.12)), tgt, Skeleton.taper(rb, 0.22, 1, 0, 1), 0.35));
                    Vec3 end = limb.end(), ed = limb.dirAt(1);
                    double ar = h * rng.range(0.23, 0.28);
                    sk.anchor(end.add(new Vec3(0, ar * 0.2, 0)), ar, 0.72, 0.35);
                    int twigs = rng.irange(1, 2);
                    for (int j = 0; j < twigs; j++) {
                        Vec3 td = ed.add(new Vec3(rng.signed(), rng.range(0.1, 0.6), rng.signed()).mul(0.9)).norm();
                        Limb tw = sk.add(Skeleton.grow(end, td, rng.range(1.2, 2.4), 0.3, 0.18, 0.15, 0, 0.1, rng));
                        sk.anchor(tw.end(), h * rng.range(0.18, 0.23), 0.72, 0.4);
                    }
                    if (rng.chance(0.5)) sk.anchor(limb.at(0.55).add(Vec3.UP.mul(0.6)), h * 0.17, 0.62, 0.3);
                }
                sk.anchor(top.add(Vec3.UP.mul(h * 0.12)), h * 0.26, 0.8, 0.4);
                return new Built(sk, new FoliageParams(3, 0.45, 0.12, 0.5, 0.3, 0.22, 0.10, 0.02, 1, 2, 0.5));
            }
        });

        // ------------------------------------------------------------ weeping (しだれ・柳)
        reg(new Shape() {
            public String id() { return "weeping"; }
            public String defaultPalette() { return "willow"; }
            public int defaultHeight() { return 13; }
            public Built build(Rng rng, int h) {
                Skeleton sk = new Skeleton();
                double r0 = 0.7 + h * 0.03;
                Vec3 base = baseFor(r0);
                double ang = rng.range(0, Math.PI * 2);
                Vec3 s = Vec3.horizontal(ang);
                Vec3 c1 = base.add(s.mul(h * 0.2)).withY(h * 0.28);
                Vec3 c2 = base.add(s.mul(-h * 0.2)).withY(h * 0.55);
                Vec3 top = base.add(s.mul(h * 0.04)).add(rnd(rng, 0.6)).withY(h * 0.82);
                Limb trunk = sk.add(Skeleton.bezier(base, c1, c2, top, Skeleton.taper(r0, 0.32, 1.1, 0.6, 0.06), 0.35));
                roots(sk, base, r0, 3, 1.4, rng);

                int n = 4 + rng.irange(0, 2);
                double az = rng.range(0, Math.PI * 2);
                for (int i = 0; i < n; i++) {
                    double t = 0.58 + 0.4 * i / (n - 1.0);
                    Vec3 st = trunk.at(t);
                    Vec3 dh = Vec3.horizontal(az + i * GOLDEN + rng.signed() * 0.25);
                    double L = h * rng.range(0.22, 0.32);
                    Limb limb = sk.add(Skeleton.grow(st, dh.add(Vec3.UP.mul(0.55)), L,
                            Math.min(trunk.radiusAt(t) * 0.7, 0.55), 0.2, 0.04, 0.22, 0.08, rng));
                    double ar = h * rng.range(0.17, 0.22);
                    sk.anchor(limb.end().add(Vec3.UP.mul(ar * 0.35)), ar, 0.6, 0.15);
                    if (rng.chance(0.5)) sk.anchor(limb.at(0.5).add(Vec3.UP.mul(ar * 0.5)), ar * 0.7, 0.6, 0.15);
                }
                sk.anchor(top.add(Vec3.UP.mul(0.8)), h * 0.24, 0.65, 0.2);
                return new Built(sk, new FoliageParams(3, 0.5, 0.12, 0.4, 0.4, 0.18, 0.08, 0.55, 1, Math.max(3, (int) (h * 0.5)), 0.85));
            }
        });

        // ------------------------------------------------------------ conifer (針葉樹・松)
        reg(new Shape() {
            public String id() { return "conifer"; }
            public String defaultPalette() { return "pine"; }
            public int defaultHeight() { return 18; }
            public Built build(Rng rng, int h) {
                Skeleton sk = new Skeleton();
                Vec3 base = new Vec3(0.5, 0, 0.5);
                Vec3 top = base.add(rnd(rng, 0.6)).withY(h);
                Limb trunk = sk.add(Skeleton.bezier(base, base.withY(h * 0.33).add(rnd(rng, 0.4)),
                        base.withY(h * 0.66).add(rnd(rng, 0.4)), top, Skeleton.taper(0.5, 0.25, 0.7, 0.5, 0.03), 0.4));
                double y = h * rng.range(0.32, 0.4);
                double az = rng.range(0, Math.PI * 2);
                int k = 0;
                while (y < h * 0.93) {
                    double t = y / h;
                    if (!rng.chance(0.12)) {
                        int m = rng.irange(1, 3);
                        for (int i = 0; i < m; i++, k++) {
                            Vec3 dh = Vec3.horizontal(az + k * GOLDEN + rng.signed() * 0.3);
                            double L = 1.2 + Math.pow(1 - t, 0.8) * h * 0.34 * rng.range(0.75, 1.1);
                            Vec3 st = trunk.at(Math.min(t, 0.99));
                            Limb br = sk.add(Skeleton.grow(st, dh.add(Vec3.UP.mul(0.12)), L, 0.33, 0.2, 0.0, 0.08, 0.05, rng));
                            double pr = rng.range(1.4, 1.8) + L * 0.22;
                            sk.anchor(br.end().add(Vec3.UP.mul(0.55)), pr, 0.36, 0.0);
                            if (L > 3.2) sk.anchor(br.at(0.5).add(Vec3.UP.mul(0.55)), pr * 0.7, 0.36, 0.0);
                        }
                    }
                    y += rng.range(2.3, 3.3) * (0.7 + 0.3 * (h / 18.0));
                }
                sk.anchor(top.add(Vec3.UP.mul(-0.6)), 1.5, 1.2, 0.2);
                sk.anchor(trunk.at(0.88).add(Vec3.UP.mul(0.3)), 1.9, 0.42, 0.0);
                return new Built(sk, new FoliageParams(2, 0.5, 0.13, 0.30, 0.5, 0.28, 0.07, 0.0, 0, 0, 0));
            }
        });

        // ------------------------------------------------------------ swoop (盆栽・桜: 横に流れる幹)
        reg(new Shape() {
            public String id() { return "swoop"; }
            public String defaultPalette() { return "cherry"; }
            public int defaultHeight() { return 10; }
            public Built build(Rng rng, int h) {
                Skeleton sk = new Skeleton();
                double r0 = 0.7 + h * 0.035;
                Vec3 base = baseFor(r0);
                Vec3 s = Vec3.horizontal(rng.range(0, Math.PI * 2));
                Vec3 side = new Vec3(-s.z(), 0, s.x());
                Vec3 top = base.add(s.mul(h * 0.5)).withY(h * 0.62);
                Vec3 c1 = base.add(s.mul(-h * 0.18)).withY(h * 0.28);
                Vec3 c2 = base.add(s.mul(h * 0.55)).add(side.mul(h * 0.1)).withY(h * 0.3);
                Limb trunk = sk.add(Skeleton.bezier(base, c1, c2, top, Skeleton.taper(r0, 0.38, 1.2, 0.5, 0.06), 0.35));
                roots(sk, base, r0, 3, 1.2, rng);

                // 主枝: 進行方向・左右・後ろ(バランス用の逆枝)
                Vec3[] dirs = {s, side, side.mul(-1), s.mul(-1).add(side.mul(0.4 * rng.sign()))};
                double[] ts = {1.0, rng.range(0.55, 0.8), rng.range(0.55, 0.8), rng.range(0.4, 0.55)};
                double[] lens = {0.25, 0.28, 0.28, 0.42};
                for (int i = 0; i < dirs.length; i++) {
                    if (i == 2 && rng.chance(0.35)) continue;
                    Vec3 st = trunk.at(ts[i]);
                    double L = h * lens[i] * rng.range(0.85, 1.15);
                    Limb limb = sk.add(Skeleton.grow(st, dirs[i].norm().add(Vec3.UP.mul(0.35)), L,
                            Math.min(trunk.radiusAt(ts[i]) * 0.7, 0.6), 0.22, 0.05, 0.12, 0.05, rng));
                    double pr = h * rng.range(0.21, 0.26);
                    sk.anchor(limb.end().add(Vec3.UP.mul(pr * 0.3)), pr, 0.5, 0.15);
                    if (L > 3) sk.anchor(limb.at(0.55).add(Vec3.UP.mul(pr * 0.35)), pr * 0.7, 0.5, 0.15);
                }
                sk.anchor(top.add(Vec3.UP.mul(h * 0.12)), h * 0.25, 0.55, 0.15);
                return new Built(sk, new FoliageParams(3, 0.55, 0.13, 0.45, 0.38, 0.22, 0.09, 0.06, 1, 2, 0.4));
            }
        });

        // ------------------------------------------------------------ dome (藤・傘型)
        reg(new Shape() {
            public String id() { return "dome"; }
            public String defaultPalette() { return "wisteria"; }
            public int defaultHeight() { return 13; }
            public Built build(Rng rng, int h) {
                Skeleton sk = new Skeleton();
                double r0 = 0.8 + h * 0.03;
                Vec3 base = baseFor(r0);
                Vec3 split = base.add(rnd(rng, h * 0.05)).withY(h * 0.42);
                Limb trunk = sk.add(Skeleton.bezier(base, base.withY(h * 0.15).add(rnd(rng, 0.5)),
                        split.add(rnd(rng, 0.8)).withY(h * 0.3), split, Skeleton.taper(r0, r0 * 0.75, 1, 0.55, 0.08), 0.35));
                roots(sk, base, r0, 3, 1.4, rng);

                Vec3 C = split.withY(h * 0.56);
                double Rx = h * 0.46, Ry = h * 0.38;
                int n = 5 + rng.irange(0, 2);
                double az = rng.range(0, Math.PI * 2);
                Limb[] limbs = new Limb[n];
                for (int i = 0; i < n; i++) {
                    Vec3 dh = Vec3.horizontal(az + i * Math.PI * 2 / n + rng.signed() * 0.35);
                    double e = rng.range(0.15, 0.7);
                    Vec3 tgt = C.add(dh.mul(Math.cos(e) * Rx * 0.85)).add(Vec3.UP.mul(Math.sin(e) * Ry * 0.85));
                    Vec3 st = trunk.at(rng.range(0.85, 1.0));
                    double L = tgt.sub(st).len();
                    limbs[i] = sk.add(Skeleton.bezier(st, st.add(dh.mul(L * 0.15)).add(Vec3.UP.mul(L * 0.45)),
                            tgt.add(dh.mul(-L * 0.25)).add(Vec3.UP.mul(L * 0.2)), tgt,
                            Skeleton.taper(Math.min(r0 * 0.6, 0.65), 0.22, 1, 0, 1), 0.35));
                }
                // フィボナッチ半球で葉塊を均等に配置し、最寄りの枝から小枝で繋ぐ
                int m = 10 + h;
                for (int i = 0; i < m; i++) {
                    double yN = 1 - (i + 0.5) / m * 1.05;          // 1 → -0.05
                    double rr = Math.sqrt(Math.max(0, 1 - yN * yN));
                    double a = az + i * GOLDEN;
                    Vec3 p = C.add(new Vec3(Math.cos(a) * rr * Rx * 0.82, yN * Ry * 0.82, Math.sin(a) * rr * Rx * 0.82));
                    double ar = h * rng.range(0.16, 0.2);
                    sk.anchor(p, ar, 0.7, 0.3);
                    Vec3 best = null; double bd = 1e9;
                    for (Limb l : limbs) for (Vec3 q : l.pts) { double d = q.sub(p).len(); if (d < bd) { bd = d; best = q; } }
                    if (best != null && bd > 1.2) sk.add(Skeleton.grow(best, p.sub(best), bd * 0.75, 0.3, 0.18, 0.05, 0, 0.05, rng));
                }
                return new Built(sk, new FoliageParams(3, 0.5, 0.13, 0.4, 0.42, 0.18, 0.06, 0.5, 2, Math.max(3, (int) (h * 0.4)), 0.35));
            }
        });
    }
}
