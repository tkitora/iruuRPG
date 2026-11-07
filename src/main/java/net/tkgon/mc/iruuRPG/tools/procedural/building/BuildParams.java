package net.tkgon.mc.iruuRPG.tools.procedural.building;

/**
 * (t295) The numbers a building is made from: the one mass of the settings (its plan, storeys, walls and roof), the chance of each part, and the ranges the
 * patterns draw from. Filled from the tool's settings (and the saved presets); kept free of the server so the tests can make buildings.
 */
public final class BuildParams {

    /** The plans: 0 rectangle, 1 L, 2 T, 3 U, 4 cross, 5 cut corners, 6 octagon, 7 circle. */
    public int footprint;
    public int module = 4;
    public int baysWide = 2;
    public int baysDeep = 2;
    public int wingBays = 1;
    public int chamfer = 2;
    public int slant = 2;
    public int radius = 5;
    public int floors = 2;
    public int storey = 6;
    /** The storeys jettied a block out over the one below: bit 0 the second storey, bit 1 the third, bit 2 the fourth. */
    public int jetty;
    public boolean fasciaOut;
    /** The podium's height as a part of the ground storey's (0: none). */
    public double plinth = 0.6;
    public int sill = 1;
    public int windowHeight = 2;
    public int windowWidth = 2;
    public int corbel;
    /** The roof: 0 gable, 1 hip, 2 half-hip, 3 gambrel, 4 shed, 5 cone/spire, 6 bell-cast. */
    public int roofType;
    /** 0 1:2, 1 1:1, 2 2:1, 3 3:1. */
    public int pitch = 2;
    public int overhang = 1;
    public int thickness = 2;
    /** 0 along the longer side, 1 along the front, 2 across it. */
    public int ridge;
    public int rise = 3;
    public int ridgeStyle;
    public boolean gableOut;
    public int bands;
    public int bandStep = 2;
    /** 0 the front faces the player... turned on by quarters. */
    public int rotation;

    public double chimney = 0.6;
    public double dormer = 0.5;
    public double bayWindow = 0.3;
    public double balcony = 0.3;
    public double turret = 0.3;
    public double porch = 0.5;
    public double finial = 1.0;

    /** 0 one mass by the numbers above; 1 to 6 the patterns A to F. */
    public int pattern;
    public int floorsMin = 2;
    public int floorsMax = 3;
    /** 0 a module of 4 or 5, 1 always 4, 2 always 5. */
    public int moduleChoice;
    /** The slopes a pattern may use: bit 0 1:2, bit 1 1:1, bit 2 2:1. */
    public int pitchMask = 6;
    public int overhangMin = 1;
    public int overhangMax = 2;
    /** The jetties of a pattern (as {@link #jetty}). */
    public int patternJetty = 3;
    public int candidates = 4;
    public int retries = 4;

    public static double pitchOf(int index) {
        return switch (index) {
            case 0 -> 0.5;
            case 2 -> 2.0;
            case 3 -> 3.0;
            default -> 1.0;
        };
    }

    public static Mass.RoofType roofOf(int index) {
        return switch (index) {
            case 1 -> Mass.RoofType.HIP;
            case 2 -> Mass.RoofType.HALF_HIP;
            case 3 -> Mass.RoofType.GAMBREL;
            case 4 -> Mass.RoofType.SHED;
            case 5 -> Mass.RoofType.CONE;
            case 6 -> Mass.RoofType.BELL;
            default -> Mass.RoofType.GABLE;
        };
    }
}
