package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;

/** The shapes a brush can have: which blocks round the centre are inside, and how far from the middle (0 at the centre, 1 at the edge) each one is. */
enum BrushShape {

    SPHERE("球", Material.SLIME_BALL),
    DISC("円盤 (平ら)", Material.HEAVY_WEIGHTED_PRESSURE_PLATE),
    CYLINDER("円柱", Material.BARREL),
    CUBE("立方体", Material.STONE),
    CONE("円錐", Material.CARVED_PUMPKIN),
    DIAMOND("ひし形 (八面体)", Material.DIAMOND);

    private final String label;
    private final Material icon;

    BrushShape(String label, Material icon) {
        this.label = label;
        this.icon = icon;
    }

    String label() {
        return label;
    }

    Material icon() {
        return icon;
    }

    /**
     * How far ({@code dx}, {@code dy}, {@code dz}) is from the middle of a brush of {@code radius}: 0 at the centre, 1 at the edge, more than 1 outside the shape.
     */
    double distance(int dx, int dy, int dz, double radius) {
        double r = Math.max(0.5, radius);
        return switch (this) {
            case SPHERE -> Math.sqrt(dx * dx + dy * dy + dz * dz) / r;
            case DISC -> Math.abs(dy) > 1 ? 9.0 : Math.sqrt(dx * dx + dz * dz) / r;
            case CYLINDER -> Math.abs(dy) > r ? 9.0 : Math.sqrt(dx * dx + dz * dz) / r;
            case CUBE -> Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) / r;
            case CONE -> {
                // the point at the top, the base at the bottom: the radius allowed shrinks upward
                double along = (dy + r) / (2.0 * r);
                if (along < 0.0 || along > 1.0) yield 9.0;
                double allowed = r * (1.0 - along);
                yield allowed < 0.5 ? 9.0 : Math.sqrt(dx * dx + dz * dz) / allowed;
            }
            case DIAMOND -> (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) / r;
        };
    }

    static BrushShape next(BrushShape shape) {
        BrushShape[] all = values();
        return all[(shape.ordinal() + 1) % all.length];
    }
}
