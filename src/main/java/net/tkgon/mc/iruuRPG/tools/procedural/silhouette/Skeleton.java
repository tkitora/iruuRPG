package net.tkgon.mc.iruuRPG.tools.procedural.silhouette;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * 木の「骨格」= 半径付きポリライン(Limb)の集合 + 葉塊のアンカー。
 * ブロックへの変換とは完全に分離しておく(連続空間で形を決めてから量子化する)。
 */
public final class Skeleton {

    public static final class Limb {
        public final List<Vec3> pts = new ArrayList<>();
        public final List<Double> radii = new ArrayList<>();
        public void add(Vec3 p, double r) { pts.add(p); radii.add(r); }
        public int size() { return pts.size(); }
        public Vec3 at(double t) {
            double f = t * (pts.size() - 1);
            int i = Math.min((int) f, pts.size() - 2);
            return pts.get(i).lerp(pts.get(i + 1), f - i);
        }
        public double radiusAt(double t) {
            double f = t * (radii.size() - 1);
            int i = Math.min((int) f, radii.size() - 2);
            return radii.get(i) + (radii.get(i + 1) - radii.get(i)) * (f - i);
        }
        public Vec3 dirAt(double t) {
            double f = t * (pts.size() - 1);
            int i = Math.min((int) f, pts.size() - 2);
            return pts.get(i + 1).sub(pts.get(i)).norm();
        }
        public Vec3 end() { return pts.get(pts.size() - 1); }
    }

    /** 葉塊の中心。squash<1 で上下に潰れた塊(パッド)になる。 */
    public record Anchor(Vec3 pos, double radius, double squash, double bottomCut) {}

    public final List<Limb> limbs = new ArrayList<>();
    public final List<Anchor> anchors = new ArrayList<>();

    public Limb add(Limb l) { limbs.add(l); return l; }
    public void anchor(Vec3 p, double r, double squash, double bottomCut) { anchors.add(new Anchor(p, r, squash, bottomCut)); }

    // ---------- 骨格の生成ヘルパ ----------

    /** 3次ベジェで「ジェスチャー曲線」を引く。radius(t) は 0..1 の位置で半径を返す。 */
    public static Limb bezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, DoubleUnaryOperator radius, double step) {
        double approx = p0.sub(p1).len() + p1.sub(p2).len() + p2.sub(p3).len();
        int n = Math.max(2, (int) Math.ceil(approx / step));
        Limb l = new Limb();
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            l.add(Vec3.bezier(p0, p1, p2, p3, t), radius.applyAsDouble(t));
        }
        return l;
    }

    /**
     * タートル成長: 方向に「光屈性(上向き)」「重力(下向き)」「揺らぎ」を毎ステップ加える。
     * gravity は先端ほど強く効く(t^2)ので、しだれ・垂れ下がりが自然な弧になる。
     */
    public static Limb grow(Vec3 start, Vec3 dir, double length, double r0, double r1,
                            double upBias, double gravity, double wobble, Rng rng) {
        Limb l = new Limb();
        double step = 0.4;
        int n = Math.max(2, (int) Math.ceil(length / step));
        Vec3 p = start, d = dir.norm();
        l.add(p, r0);
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n;
            Vec3 jitter = new Vec3(rng.signed(), rng.signed() * 0.5, rng.signed()).mul(wobble);
            d = d.add(Vec3.UP.mul(upBias - gravity * t * t)).add(jitter).norm();
            p = p.add(d.mul(step));
            l.add(p, r0 + (r1 - r0) * t);
        }
        return l;
    }

    /** 半径関数: 根元フレア + べき乗テーパー。 */
    public static DoubleUnaryOperator taper(double r0, double r1, double exp, double flare, double flareLen) {
        return t -> r1 + (r0 - r1) * Math.pow(1 - t, exp) + flare * Math.exp(-t / flareLen);
    }
}
