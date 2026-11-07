package net.tkgon.mc.iruuRPG.tools.procedural.building;

import java.util.ArrayList;
import java.util.List;

/**
 * (t302) The house base's numbers (the same settings the house base of the tool is made from: its size, storeys, posts, jetty, podium, walls, roof, and the
 * list of parts added to it), for the building to be made from them in its own way: the walls in bays and three depths, the roofs by the straight skeleton.
 * The house's own coordinates are kept: {@code a} (x) along the front from its left, {@code b} (z) from the back to the front, the front toward the player.
 */
public final class HouseSpec {

    public int width = 11;
    public int depth = 9;
    public int floors = 2;
    public int storey = 4;
    /** The posts every this many blocks (0: the corners only), from both ends with {@link #mirror}. */
    public int spacing = 4;
    public boolean mirror;
    /** Each storey over the ground juts out this much more at the front and the back (and the sides with {@link #jettySides}), 3 at most in all. */
    public int jetty;
    public boolean jettySides;
    public boolean brackets;
    public boolean door = true;
    public boolean foundation = true;
    /** The podium the house stands on (its height) and its terrace past the walls. */
    public int raise;
    public int terrace;
    public boolean stone;
    public int plinth;
    public boolean timber;
    public boolean recess;
    public boolean windows = true;
    public boolean glass;
    public int skirt;
    /** The house base's roof (0 flat, 1 gable, 2 hip, 3 shed, 4 gambrel, 5 mansard, 6 hip-and-gable, 7 half-hip, 8 curved, 9 steps, 10 steps hipped). */
    public int roof = 1;
    public double pitch = 0.75;
    public int overhang = 1;
    /** 0 along the longer side, 1 along the front, 2 across it. */
    public int ridge;
    public boolean ridgeBeam;
    public int[] steps = {1, 1, 1, 2, 3, 3};
    public boolean stepFit = true;
    public final List<Module> modules = new ArrayList<>();

    /**
     * A part added to the house, as the house base keeps it: its kind ({@code dormer}, {@code chimney}, {@code tower}, {@code wing}, {@code porch},
     * {@code balcony}, {@code bay}, {@code volume}, {@code stair}), the side it is on (0 front, 1 right, 2 back, 3 left), along that side from its middle,
     * out from its wall, its size, the storey it starts at, and the numbers only some kinds have.
     */
    public record Module(String type, int side, int along, int offset, int width, int depth, int height, int level, boolean on, int roof, int ridge, int shape,
                         int jetty, int raise, int spire) {
    }

    /** The roof type of a house base's roof number. */
    public static Mass.RoofType roofType(int roof) {
        return switch (roof) {
            case 0 -> Mass.RoofType.FLAT;
            case 2 -> Mass.RoofType.HIP;
            case 3 -> Mass.RoofType.SHED;
            case 4 -> Mass.RoofType.GAMBREL;
            case 5 -> Mass.RoofType.MANSARD;
            case 6 -> Mass.RoofType.IRIMOYA;
            case 7 -> Mass.RoofType.HALF_HIP;
            case 8 -> Mass.RoofType.CURVED;
            case 9 -> Mass.RoofType.STEPPED;
            case 10 -> Mass.RoofType.STEPPED_HIP;
            default -> Mass.RoofType.GABLE;
        };
    }

    /**
     * A side of the house at a storey as a frame for a part: {@code u} along it from the part's place, to the right of someone outside facing it; {@code v} out
     * from its wall line (moved by the part's offset).
     */
    public record Frame(int index, int wall, int middle, int along, int offset) {

        public int a(int u, int v) {
            int uu = along + u;
            int vv = offset + v;
            return switch (index) {
                case 1 -> wall + vv;
                case 2 -> middle - uu;
                case 3 -> wall - vv;
                default -> middle + uu;
            };
        }

        public int b(int u, int v) {
            int uu = along + u;
            int vv = offset + v;
            return switch (index) {
                case 1 -> middle - uu;
                case 2 -> wall - vv;
                case 3 -> middle + uu;
                default -> wall + vv;
            };
        }

        /** The way out of the side (0 north, 1 east, 2 south, 3 west). */
        public int way() {
            return switch (index) {
                case 1 -> 1;
                case 2 -> 0;
                case 3 -> 3;
                default -> 2;
            };
        }

        /** The box of the frame's cells from {@code (u0, v0)} to {@code (u1, v1)}, {x0, z0, x1, z1}. */
        public int[] box(int u0, int v0, int u1, int v1) {
            int ax = a(u0, v0);
            int bx = a(u1, v1);
            int az = b(u0, v0);
            int bz = b(u1, v1);
            return new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
        }
    }

    /** Side {@code index} of the house's storey {@code k} ({@code out}, {@code outSides}: how far that storey is jettied) as the frame of a part. */
    public Frame frame(Module m, int k, Mass base) {
        int e = base.out(Math.max(0, Math.min(base.floors - 1, k)));
        int eA = base.outSides(Math.max(0, Math.min(base.floors - 1, k)));
        return switch (Math.floorMod(m.side(), 4)) {
            case 1 -> new Frame(1, width - 1 + eA, (depth - 1) / 2, m.along(), m.offset());
            case 2 -> new Frame(2, -e, (width - 1) / 2, m.along(), m.offset());
            case 3 -> new Frame(3, -eA, (depth - 1) / 2, m.along(), m.offset());
            default -> new Frame(0, depth - 1 + e, (width - 1) / 2, m.along(), m.offset());
        };
    }
}
