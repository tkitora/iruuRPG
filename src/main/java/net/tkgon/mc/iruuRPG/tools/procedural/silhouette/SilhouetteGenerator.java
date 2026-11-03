package net.tkgon.mc.iruuRPG.tools.procedural.silhouette;

import java.util.*;

/**
 * (ユーザーの送った mc_tree_gen をプロシージャルツールの「樹形」アルゴリズムとして移したもの。手を入れたのは、クラス名・パッケージ・
 * {@link #generate(Shapes.Shape, Palette, int, long, java.util.function.UnaryOperator)} の葉の調整だけ。ブロックは記号のパレット
 * ({@link Palette#symbols}) で作り、ツールの部位に置き換える: {@code net.tkgon.mc.iruuRPG.tools.procedural.SilhouetteTree})
 *
 * 骨格(連続空間) → ブロック(離散) への変換パイプライン。
 *
 *  1. 木部: サブボクセル被覆率(4x4x4=64サンプルのビットマスク)で
 *           原木 / 階段 / ハーフ / 塀 / フェンス を選ぶ「アンチエイリアス量子化」
 *  2. 葉  : アンカーごとの異方性メタボール場 + 底面カット + 値ノイズ → 雲状の房
 *  3. 仕上げ: 表面の間引き・突起、孤立ブロック除去
 *  4. 陰影: 上方遮蔽 + 房内の高さ → 3Dベイヤー・ディザで葉の種類を段階的に混ぜる
 *  5. 垂れ: 下面から房を垂らす(外周ほど多い)
 *  6. 状態: 原木の軸、階段の向き、フェンス/塀の接続を自前で計算
 */
public final class SilhouetteGenerator {

    public record BlockPos(int x, int y, int z) {}

    public record Result(Map<BlockPos, String> blocks, long seed, String shape, String palette) {
        public int size() { return blocks.size(); }
    }

    enum Kind { LOG, PARTIAL, THIN, LEAF, HANG }

    static final class Cell {
        Kind kind;
        String block;       // 葉・垂れ用
        String state = "";  // 確定後のブロック文字列
        double thinR;
        double shade;
        Cell(Kind k) { kind = k; }
    }

    /** 被覆率計算用の一時セル */
    static final class Cov {
        long mask;
        double dx, dy, dz;  // 通過方向の重み付き累積(絶対値)
        boolean core;       // 中心線が通る → 必ず原木(連結性の保証)
    }

    static final double THIN_R = 0.42;   // これ未満の半径は「細枝」として中心線のみ描く
    static final double LOG_COV = 0.50;  // 被覆率がこれ以上 → 原木
    static final double PART_COV = 0.20; // これ以上 → 階段 / ハーフ

    static long key(int x, int y, int z) {
        return ((long) (x + 0x8000) << 32) | ((long) (y + 0x8000) << 16) | (z + 0x8000);
    }
    static int kx(long k) { return (int) (k >>> 32) - 0x8000; }
    static int ky(long k) { return (int) ((k >>> 16) & 0xFFFF) - 0x8000; }
    static int kz(long k) { return (int) (k & 0xFFFF) - 0x8000; }

    static final int[][] N6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final Map<Long, Cell> grid = new HashMap<>();
    private final Map<Long, Cov> cov = new HashMap<>();
    private final Palette pal;
    private final FoliageParams fp;
    private final long seed;

    private SilhouetteGenerator(Palette pal, FoliageParams fp, long seed) {
        this.pal = pal; this.fp = fp; this.seed = seed;
    }

    public static Result generate(Shapes.Shape shape, Palette palette, int height, long seed) {
        return generate(shape, palette, height, seed, java.util.function.UnaryOperator.identity());
    }

    /** {@code tune}: the shape's foliage numbers as the tool's settings change them (how much leaf, how many drips). */
    public static Result generate(Shapes.Shape shape, Palette palette, int height, long seed, java.util.function.UnaryOperator<FoliageParams> tune) {
        Rng rng = new Rng(seed);
        Shapes.Built built = shape.build(rng, height);
        Palette pal = palette != null ? palette : Palette.get(shape.defaultPalette());
        SilhouetteGenerator g = new SilhouetteGenerator(pal, tune.apply(built.foliage()), seed);
        g.rasterWood(built.skeleton());
        g.classifyWood();
        g.foliage(built.skeleton());
        g.cleanupLeaves();
        g.shade();
        g.drips(built.skeleton());
        g.resolveStates();
        Map<BlockPos, String> out = new LinkedHashMap<>();
        // 下から順(設置時に支えのあるものから置かれる)
        List<Long> keys = new ArrayList<>(g.grid.keySet());
        keys.sort(Comparator.comparingInt(SilhouetteGenerator::ky));
        for (long k : keys) out.put(new BlockPos(kx(k), ky(k), kz(k)), g.grid.get(k).state);
        return new Result(out, seed, shape.id(), pal.name());
    }

    // ================= 1. 木部ラスタライズ =================

    void rasterWood(Skeleton sk) {
        for (Skeleton.Limb l : sk.limbs) {
            for (int i = 0; i + 1 < l.size(); i++) {
                Vec3 a = l.pts.get(i), b = l.pts.get(i + 1);
                double ra = l.radii.get(i), rb = l.radii.get(i + 1);
                if (Math.max(ra, rb) >= THIN_R) capsule(a, b, ra, rb);
                else thinWalk(a, b, Math.max(ra, rb));
            }
        }
    }

    /** カプセル(半径が線形変化する円柱)を64サンプル/ブロックで評価し、ビットマスクをORする。 */
    void capsule(Vec3 a, Vec3 b, double ra, double rb) {
        Vec3 ab = b.sub(a);
        double ab2 = Math.max(ab.dot(ab), 1e-9);
        Vec3 dir = ab.norm().abs();
        double rm = Math.max(ra, rb);
        int x0 = (int) Math.floor(Math.min(a.x(), b.x()) - rm), x1 = (int) Math.floor(Math.max(a.x(), b.x()) + rm);
        int y0 = (int) Math.floor(Math.min(a.y(), b.y()) - rm), y1 = (int) Math.floor(Math.max(a.y(), b.y()) + rm);
        int z0 = (int) Math.floor(Math.min(a.z(), b.z()) - rm), z1 = (int) Math.floor(Math.max(a.z(), b.z()) + rm);
        for (int x = x0; x <= x1; x++) for (int y = Math.max(0, y0); y <= y1; y++) for (int z = z0; z <= z1; z++) {
            long m = 0;
            int bit = 0;
            for (int sx = 0; sx < 4; sx++) for (int sy = 0; sy < 4; sy++) for (int sz = 0; sz < 4; sz++, bit++) {
                double px = x + (sx + 0.5) / 4, py = y + (sy + 0.5) / 4, pz = z + (sz + 0.5) / 4;
                double t = ((px - a.x()) * ab.x() + (py - a.y()) * ab.y() + (pz - a.z()) * ab.z()) / ab2;
                t = Math.max(0, Math.min(1, t));
                double cx = a.x() + ab.x() * t - px, cy = a.y() + ab.y() * t - py, cz = a.z() + ab.z() * t - pz;
                double r = ra + (rb - ra) * t;
                if (cx * cx + cy * cy + cz * cz <= r * r) m |= 1L << bit;
            }
            if (m == 0) continue;
            Cov c = cov.computeIfAbsent(key(x, y, z), k -> new Cov());
            long added = m & ~c.mask;
            c.mask |= m;
            double w = Long.bitCount(added) + 1;
            c.dx += dir.x() * w; c.dy += dir.y() * w; c.dz += dir.z() * w;
        }
        // 中心線は必ず原木にする(斜めだけで繋がる「点線」を防ぐ)
        manhattan(a, b, (x, y, z) -> {
            Cov c = cov.computeIfAbsent(key(x, y, z), k -> new Cov());
            c.core = true;
            c.dx += dir.x() * 8; c.dy += dir.y() * 8; c.dz += dir.z() * 8;
        });
    }

    interface CellFn { void at(int x, int y, int z); }

    /** 6近傍で連結な線分ボクセル化。斜め移動は「水平→垂直」の順に分解する(フェンスの段差が自然になる)。 */
    static void manhattan(Vec3 a, Vec3 b, CellFn fn) {
        double len = b.sub(a).len();
        int n = Math.max(1, (int) Math.ceil(len / 0.2));
        int cx = (int) Math.floor(a.x()), cy = (int) Math.floor(a.y()), cz = (int) Math.floor(a.z());
        if (cy >= 0) fn.at(cx, cy, cz);
        for (int i = 1; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n);
            int tx = (int) Math.floor(p.x()), ty = (int) Math.floor(p.y()), tz = (int) Math.floor(p.z());
            while (cx != tx || cy != ty || cz != tz) {
                if (cx != tx) cx += Integer.signum(tx - cx);
                else if (cz != tz) cz += Integer.signum(tz - cz);
                else cy += Integer.signum(ty - cy);
                if (cy >= 0) fn.at(cx, cy, cz);
            }
        }
    }

    void thinWalk(Vec3 a, Vec3 b, double r) {
        manhattan(a, b, (x, y, z) -> {
            long k = key(x, y, z);
            Cov c = cov.get(k);
            if (c != null && (c.core || Long.bitCount(c.mask) >= 32)) return; // 既に太い部分
            Cell cell = grid.computeIfAbsent(k, kk -> new Cell(Kind.THIN));
            if (cell.kind == Kind.THIN) cell.thinR = Math.max(cell.thinR, r);
        });
    }

    /** 被覆率 → 部材。部分被覆はマスクの重心で「どちら側に材がある」かを判定する。 */
    void classifyWood() {
        // まず原木
        for (Map.Entry<Long, Cov> e : cov.entrySet()) {
            Cov c = e.getValue();
            double f = Long.bitCount(c.mask) / 64.0;
            if (c.core || f >= LOG_COV) {
                Cell cell = new Cell(Kind.LOG);
                Vec3 d = new Vec3(c.dx, c.dy, c.dz);
                double l = d.len();
                if (d.y() >= 0.78 * l) cell.state = pal.log() + "[axis=y]";
                else if (d.x() >= 0.78 * l) cell.state = pal.log() + "[axis=x]";
                else if (d.z() >= 0.78 * l) cell.state = pal.log() + "[axis=z]";
                else cell.state = pal.wood() + "[axis=y]"; // 斜め部分は全面樹皮ブロックで年輪を見せない
                grid.put(e.getKey(), cell);
            }
        }
        // 次に部分ブロック(原木に接しているものだけ)
        for (Map.Entry<Long, Cov> e : cov.entrySet()) {
            long k = e.getKey();
            if (grid.containsKey(k) && grid.get(k).kind == Kind.LOG) continue;
            Cov c = e.getValue();
            double f = Long.bitCount(c.mask) / 64.0;
            if (f < PART_COV) continue;
            int x = kx(k), y = ky(k), z = kz(k);
            double l = Math.sqrt(c.dx * c.dx + c.dy * c.dy + c.dz * c.dz);
            boolean vertical = c.dy >= 0.85 * l;
            if (vertical && y > 1) continue; // 垂直幹の側面に階段を貼ると梯子状になるので根元だけ
            if (!touches(x, y, z, Kind.LOG)) continue;
            // マスク重心
            double gx = 0, gy = 0, gz = 0;
            int n = 0, bit = 0;
            for (int sx = 0; sx < 4; sx++) for (int sy = 0; sy < 4; sy++) for (int sz = 0; sz < 4; sz++, bit++) {
                if ((c.mask >>> bit & 1) == 0) continue;
                gx += (sx - 1.5) / 4; gy += (sy - 1.5) / 4; gz += (sz - 1.5) / 4; n++;
            }
            gx /= n; gy /= n; gz /= n;
            Cell cell = new Cell(Kind.PARTIAL);
            double ah = Math.max(Math.abs(gx), Math.abs(gz));
            if (Math.abs(gy) >= ah * 1.2) {
                cell.state = pal.slab() + "[type=" + (gy < 0 ? "bottom" : "top") + "]";
            } else {
                String facing = Math.abs(gx) > Math.abs(gz) ? (gx > 0 ? "east" : "west") : (gz > 0 ? "south" : "north");
                cell.state = pal.stairs() + "[facing=" + facing + ",half=" + (gy <= 0.02 ? "bottom" : "top") + ",shape=straight]";
            }
            grid.put(k, cell);
        }
    }

    boolean touches(int x, int y, int z, Kind kind) {
        for (int[] d : N6) {
            Cell c = grid.get(key(x + d[0], y + d[1], z + d[2]));
            if (c != null && c.kind == kind) return true;
        }
        return false;
    }

    boolean isWood(Cell c) { return c != null && (c.kind == Kind.LOG || c.kind == Kind.THIN || c.kind == Kind.PARTIAL); }

    // ================= 2. 葉: 異方性メタボール =================

    record Blob(double x, double y, double z, double r, double sq, double cutY, double baseY, double top) {}

    void foliage(Skeleton sk) {
        if (sk.anchors.isEmpty()) return;
        Rng rng = new Rng(seed ^ 0x5EEDL);
        List<Blob> blobs = new ArrayList<>();
        double minX = 1e9, minY = 1e9, minZ = 1e9, maxX = -1e9, maxY = -1e9, maxZ = -1e9;
        for (Skeleton.Anchor a : sk.anchors) {
            int n = Math.max(1, fp.blobs() + (rng.chance(0.4) ? 1 : 0));
            double cutY = a.pos().y() - a.radius() * a.squash() * a.bottomCut();
            for (int i = 0; i < n; i++) {
                double ang = rng.range(0, Math.PI * 2);
                double d = i == 0 ? 0 : a.radius() * fp.spread() * rng.range(0.5, 1.0);
                double r = a.radius() * (i == 0 ? 0.85 : rng.range(0.5, 0.75));
                double bx = a.pos().x() + Math.cos(ang) * d;
                double by = a.pos().y() + (i == 0 ? 0 : rng.range(-0.1, 0.45) * a.radius() * a.squash());
                double bz = a.pos().z() + Math.sin(ang) * d;
                double ry = r * a.squash();
                blobs.add(new Blob(bx, by, bz, r, a.squash(), cutY,
                        a.pos().y() - a.radius() * a.squash(), a.pos().y() + a.radius() * a.squash()));
                minX = Math.min(minX, bx - r); maxX = Math.max(maxX, bx + r);
                minY = Math.min(minY, by - ry); maxY = Math.max(maxY, by + ry);
                minZ = Math.min(minZ, bz - r); maxZ = Math.max(maxZ, bz + r);
            }
        }
        double ns = fp.noiseScale();
        for (int x = (int) Math.floor(minX) - 1; x <= (int) maxX + 1; x++)
            for (int y = Math.max(0, (int) Math.floor(minY) - 1); y <= (int) maxY + 1; y++)
                for (int z = (int) Math.floor(minZ) - 1; z <= (int) maxZ + 1; z++) {
                    double px = x + 0.5, py = y + 0.5, pz = z + 0.5;
                    double f = 0, best = 0, localH = 0.5;
                    for (Blob b : blobs) {
                        double dx = (px - b.x) / b.r, dy = (py - b.y) / (b.r * b.sq), dz = (pz - b.z) / b.r;
                        double d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 >= 1) continue;
                        double w = (1 - d2) * (1 - d2);
                        // 底面カット: アンカーごとに下面を平らにする(雲の形)
                        double below = b.cutY - py;
                        if (below > 0) w *= Math.max(0, 1 - below * 1.6);
                        f += w;
                        if (w > best) { best = w; localH = (py - b.baseY) / Math.max(0.5, b.top - b.baseY); }
                    }
                    if (f <= 0) continue;
                    f += (Rng.noise(seed, px * ns, py * ns * 1.4, pz * ns) - 0.5) * fp.noiseAmp();
                    if (f < fp.threshold()) continue;
                    long k = key(x, y, z);
                    if (grid.containsKey(k)) continue; // 木部優先
                    Cell c = new Cell(Kind.LEAF);
                    c.shade = Math.max(0, Math.min(1, localH));
                    grid.put(k, c);
                }
    }

    int leafNeighbors(int x, int y, int z) {
        int n = 0;
        for (int[] d : N6) if (grid.containsKey(key(x + d[0], y + d[1], z + d[2]))) n++;
        return n;
    }

    void cleanupLeaves() {
        // 表面の間引き(上面は残しやすく、側面・下面は削りやすく)
        List<Long> remove = new ArrayList<>();
        for (Map.Entry<Long, Cell> e : grid.entrySet()) {
            if (e.getValue().kind != Kind.LEAF) continue;
            long k = e.getKey(); int x = kx(k), y = ky(k), z = kz(k);
            int n = leafNeighbors(x, y, z);
            if (n >= 6) continue;
            boolean topOpen = !grid.containsKey(key(x, y + 1, z));
            double p = fp.erode() * (topOpen ? 0.5 : 1.0) * (n <= 3 ? 1.6 : 1.0);
            if (Rng.hash(seed + 11, x, y, z) < p) remove.add(k);
        }
        remove.forEach(grid::remove);

        // 突起: 表面から外向きに1ブロック(下向きは除く)
        List<long[]> add = new ArrayList<>();
        int[][] out = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (Map.Entry<Long, Cell> e : grid.entrySet()) {
            if (e.getValue().kind != Kind.LEAF) continue;
            long k = e.getKey(); int x = kx(k), y = ky(k), z = kz(k);
            if (Rng.hash(seed + 23, x, y, z) >= fp.protrude()) continue;
            int[] d = out[(int) (Rng.hash(seed + 29, x, y, z) * out.length)];
            long nk = key(x + d[0], y + d[1], z + d[2]);
            if (!grid.containsKey(nk)) add.add(new long[]{nk, Double.doubleToLongBits(e.getValue().shade)});
        }
        for (long[] a : add) { Cell c = new Cell(Kind.LEAF); c.shade = Double.longBitsToDouble(a[1]); grid.putIfAbsent(a[0], c); }

        // 孤立した葉を除去(浮いた粉を防ぐ)
        remove.clear();
        for (Map.Entry<Long, Cell> e : grid.entrySet()) {
            if (e.getValue().kind != Kind.LEAF) continue;
            long k = e.getKey();
            if (leafNeighbors(kx(k), ky(k), kz(k)) == 0) remove.add(k);
        }
        remove.forEach(grid::remove);
    }

    // ================= 4. 陰影ディザ =================

    void shade() {
        String[] tiers = pal.leaves();
        for (Map.Entry<Long, Cell> e : grid.entrySet()) {
            Cell c = e.getValue();
            if (c.kind != Kind.LEAF) continue;
            long k = e.getKey(); int x = kx(k), y = ky(k), z = kz(k);
            int above = 0;
            for (int i = 1; i <= 4; i++) if (grid.containsKey(key(x, y + i, z))) above++;
            double sky = 1 - above / 4.0;
            double s = 0.55 * c.shade + 0.45 * sky;
            if (leafNeighbors(x, y, z) == 6) s -= 0.15;               // 内部は暗く
            if (!grid.containsKey(key(x, y - 1, z))) s -= 0.15;        // 下面は暗く
            s = 0.55 + (s - 0.55) * 1.7;                               // コントラスト強調
            double v = (1 - s) * tiers.length + (Rng.bayer(x, y, z) - 0.5) * 1.1;
            int idx = Math.max(0, Math.min(tiers.length - 1, (int) Math.floor(v)));
            c.block = tiers[idx];
        }
    }

    // ================= 5. 垂れ房 =================

    void drips(Skeleton sk) {
        if (fp.dripChance() <= 0 || pal.hang() == null) return;
        Vec3 base = sk.limbs.get(0).pts.get(0);
        double maxH = 1;
        for (long k : grid.keySet()) if (grid.get(k).kind == Kind.LEAF)
            maxH = Math.max(maxH, Math.hypot(kx(k) + 0.5 - base.x(), kz(k) + 0.5 - base.z()));
        List<Long> leaves = new ArrayList<>();
        for (Map.Entry<Long, Cell> e : grid.entrySet()) if (e.getValue().kind == Kind.LEAF) leaves.add(e.getKey());
        // 位置ハッシュ順に処理 → 走査方向の偏りが出ない
        leaves.sort(Comparator.comparingDouble(k -> Rng.hash(seed + 43, kx(k), ky(k), kz(k))));
        Set<Long> cols = new HashSet<>();
        for (long k : leaves) {
            int x = kx(k), y = ky(k), z = kz(k);
            if (grid.containsKey(key(x, y - 1, z))) continue;
            if (hangNear(cols, x, z)) continue; // 房同士を密着させない(壁化を防ぐ)
            double outer = Math.hypot(x + 0.5 - base.x(), z + 0.5 - base.z()) / maxH;
            double p = fp.dripChance() * ((1 - fp.dripOuter()) + fp.dripOuter() * outer * outer * 1.6);
            if (Rng.hash(seed + 37, x, y, z) >= p) continue;
            double u = Math.pow(Rng.hash(seed + 41, x, y, z), 1.6);
            int len = fp.dripMin() + (int) (u * (fp.dripMax() - fp.dripMin() + 1));
            for (int i = 1; i <= len; i++) {
                int yy = y - i;
                if (yy < 1 || grid.containsKey(key(x, yy, z))) break;
                if (yy - 1 >= 0 && grid.containsKey(key(x, yy - 1, z)) && i < len) { /* 次で衝突 */ }
                Cell c = new Cell(Kind.HANG);
                boolean last = i == len || yy - 1 < 1 || grid.containsKey(key(x, yy - 1, z));
                c.block = last && pal.hangTip() != null ? pal.hangTip() : pal.hang();
                grid.put(key(x, yy, z), c);
                cols.add(key(x, 0, z));
                if (last) break;
            }
        }
    }

    static boolean hangNear(Set<Long> cols, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (cols.contains(key(x + dx, 0, z + dz))) return true;
        return false;
    }

    // ================= 6. ブロック状態の確定 =================

    void resolveStates() {
        for (Map.Entry<Long, Cell> e : grid.entrySet()) {
            Cell c = e.getValue();
            long k = e.getKey(); int x = kx(k), y = ky(k), z = kz(k);
            switch (c.kind) {
                case LOG, PARTIAL -> { }
                case LEAF -> c.state = withLeafState(c.block);
                case HANG -> c.state = withLeafState(c.block);
                case THIN -> {
                    boolean useWall = pal.wall() != null && c.thinR >= 0.30;
                    String[] dirs = {"north", "south", "east", "west"};
                    int[][] off = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};
                    StringBuilder sb = new StringBuilder(useWall ? pal.wall() : pal.fence()).append('[');
                    for (int i = 0; i < 4; i++) {
                        Cell n = grid.get(key(x + off[i][0], y, z + off[i][2]));
                        boolean con = n != null && (n.kind == Kind.THIN || n.kind == Kind.LOG);
                        if (useWall) sb.append(dirs[i]).append('=').append(con ? "low" : "none").append(',');
                        else sb.append(dirs[i]).append('=').append(con).append(',');
                    }
                    if (useWall) sb.append("up=true,");
                    sb.append("waterlogged=false]");
                    c.state = sb.toString();
                }
            }
        }
    }

    static String withLeafState(String id) {
        if (id.contains("[")) return id;
        if (id.endsWith("_leaves")) return id + "[persistent=true]";
        if (id.endsWith("_pane")) return id + "[north=false,south=false,east=false,west=false,waterlogged=false]";
        return id;
    }
}
