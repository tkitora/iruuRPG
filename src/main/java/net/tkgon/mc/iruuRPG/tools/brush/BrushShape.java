package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;

import java.util.Random;

/**
 * The shapes a brush can have, after the brushes of EZ Edit / goPaint: which blocks round the centre are inside, and how far from the middle (0 at the centre, 1 at the edge)
 * each one is. A shape is turned into a {@link Volume} for one stroke ({@link #volume}): the random ones (the spray, the splatter, the blob) are drawn once per stroke, so
 * every block of the stroke sees the same shape.
 */
enum BrushShape {

    SPHERE("球", Material.SLIME_BALL, "丸い範囲"),
    DOME("半球 (上半分)", Material.TURTLE_EGG, "球の上半分。地面に置く丸い斑に"),
    DISC("円盤 (平ら)", Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "高さ ±1 の平らな円"),
    CYLINDER("円柱", Material.BARREL, "上下にまっすぐな円"),
    RING("輪", Material.LEAD, "半径の 6〜9 割だけの輪 (内側は塗らない)"),
    CUBE("立方体", Material.STONE, "四角い範囲"),
    CONE("円錐", Material.CARVED_PUMPKIN, "上が細い円錐"),
    DIAMOND("ひし形 (八面体)", Material.DIAMOND, "八面体"),
    SPRAY("スプレー", Material.GUNPOWDER, "球の中を、中心ほど濃く、縁ほどまばらに"),
    SPLATTER("スプラッター", Material.INK_SAC, "ばらばらに散る、大小の斑 (しぶき)"),
    BLOB("不定形", Material.CLAY_BALL, "輪郭がうねる、いびつな球 (岩・苔の斑に)");

    /** The shape for one stroke: how far a place is from the middle, and how likely a place inside is to be taken. */
    interface Volume {

        /** 0 at the centre, 1 at the edge, more than 1 outside. */
        double distance(int dx, int dy, int dz);

        /** The chance (0 - 1) that a block at {@code distance} inside the shape is taken; 1 for the solid shapes. */
        default double density(double distance) {
            return 1.0;
        }
    }

    private final String label;
    private final Material icon;
    private final String description;

    BrushShape(String label, Material icon, String description) {
        this.label = label;
        this.icon = icon;
        this.description = description;
    }

    String label() {
        return label;
    }

    Material icon() {
        return icon;
    }

    String description() {
        return description;
    }

    /** The shape of a brush of {@code radius} for one stroke ({@code seed} makes the random shapes). */
    Volume volume(double radius, long seed) {
        double r = Math.max(0.5, radius);
        return switch (this) {
            case SPHERE -> (dx, dy, dz) -> Math.sqrt(dx * dx + dy * dy + dz * dz) / r;
            case DOME -> (dx, dy, dz) -> dy < 0 ? 9.0 : Math.sqrt(dx * dx + dy * dy + dz * dz) / r;
            case DISC -> (dx, dy, dz) -> Math.abs(dy) > 1 ? 9.0 : Math.sqrt(dx * dx + dz * dz) / r;
            case CYLINDER -> (dx, dy, dz) -> Math.abs(dy) > r ? 9.0 : Math.sqrt(dx * dx + dz * dz) / r;
            case RING -> (dx, dy, dz) -> {
                if (Math.abs(dy) > Math.max(1.0, r * 0.35)) return 9.0;
                double flat = Math.sqrt(dx * dx + dz * dz) / r;
                return flat < 0.6 ? 9.0 : flat;
            };
            case CUBE -> (dx, dy, dz) -> Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) / r;
            case CONE -> (dx, dy, dz) -> {
                // the point at the top, the base at the bottom: the radius allowed shrinks upward
                double along = (dy + r) / (2.0 * r);
                if (along < 0.0 || along > 1.0) return 9.0;
                double allowed = Math.max(0.6, r * (1.0 - along));
                return Math.sqrt(dx * dx + dz * dz) / allowed;
            };
            case DIAMOND -> (dx, dy, dz) -> (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) / r;
            case SPRAY -> new Volume() {
                @Override
                public double distance(int dx, int dy, int dz) {
                    return Math.sqrt(dx * dx + dy * dy + dz * dz) / r;
                }

                @Override
                public double density(double distance) {
                    return Math.pow(Math.max(0.0, 1.0 - distance), 1.4);
                }
            };
            case SPLATTER -> splatter(r, seed);
            case BLOB -> blob(r, seed);
        };
    }

    /** A handful of round blobs of different sizes scattered inside the radius; a place is as far in as the nearest blob says. */
    private static Volume splatter(double r, long seed) {
        Random random = new Random(seed);
        int count = Math.max(4, (int) Math.round(3 + r * 0.9));
        double[][] blobs = new double[count][4];
        for (int index = 0; index < count; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double lift = random.nextDouble() * 2.0 - 1.0;
            double ring = Math.sqrt(random.nextDouble()) * r * 0.75;
            double flat = Math.sqrt(Math.max(0.0, 1.0 - lift * lift));
            blobs[index][0] = Math.cos(angle) * flat * ring;
            blobs[index][1] = lift * ring;
            blobs[index][2] = Math.sin(angle) * flat * ring;
            // a big one now and then, mostly small ones (smaller toward the edge)
            blobs[index][3] = Math.max(0.8, r * (0.12 + 0.3 * random.nextDouble() * random.nextDouble() * 2.0) * (1.0 - 0.4 * ring / Math.max(0.5, r)));
        }
        return (dx, dy, dz) -> {
            double best = 9.0;
            for (double[] blob : blobs) {
                double ex = dx - blob[0];
                double ey = dy - blob[1];
                double ez = dz - blob[2];
                best = Math.min(best, Math.sqrt(ex * ex + ey * ey + ez * ez) / blob[3]);
            }
            return best;
        };
    }

    /** A sphere whose radius changes from direction to direction by a smooth noise, so the outline is lumpy. */
    private static Volume blob(double r, long seed) {
        double[] phase = {(seed & 0xFF) * 0.37, ((seed >> 8) & 0xFF) * 0.41, ((seed >> 16) & 0xFF) * 0.29};
        return (dx, dy, dz) -> {
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-6) return 0.0;
            double nx = dx / length;
            double ny = dy / length;
            double nz = dz / length;
            double wobble = 0.5 * Math.sin(nx * 3.1 + phase[0]) * Math.cos(ny * 2.7 + phase[1]) + 0.35 * Math.sin(nz * 4.3 + nx * 2.1 + phase[2]) + 0.25 * Math.sin((nx + ny + nz) * 5.0 + phase[1]);
            return length / (r * (0.78 + 0.22 * wobble));
        };
    }

    static BrushShape next(BrushShape shape) {
        BrushShape[] all = values();
        return all[(shape.ordinal() + 1) % all.length];
    }
}
