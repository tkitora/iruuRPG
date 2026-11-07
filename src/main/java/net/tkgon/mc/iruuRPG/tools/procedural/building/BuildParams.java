package net.tkgon.mc.iruuRPG.tools.procedural.building;

/**
 * (t295) The numbers a building is made from: (t302) the house base's numbers for the one building of the settings ({@link #house}), the plan it is cut in,
 * its windows and corbels, the roof's look and outline, the chance of each part of a pattern, and the ranges the patterns draw from. Filled from the tool's
 * settings (and the saved presets); kept free of the server so the tests can make buildings.
 */
public final class BuildParams {

    /** (t302) The house base's numbers: the building of the settings is made from them (pattern 0). */
    public HouseSpec house = new HouseSpec();
    /** The plan the house's width and depth are cut in: 0 rectangle, 1 L, 2 T, 3 U, 4 cross, 5 cut corners, 6 octagon, 7 circle. */
    public int footprint;
    /** (t302) An L, T, U or cross plan's wing: how far it reaches out of the rectangle, and how wide it is (blocks). */
    public int wingLength = 6;
    public int wingWidth = 5;
    public int chamfer = 2;
    public int slant = 2;
    public boolean fasciaOut;
    /** The podium's height as a part of the ground storey's (the patterns; 0: none). */
    public double plinth = 0.6;
    public int sill = 1;
    public int windowHeight = 2;
    public int windowWidth = 2;
    public int corbel;
    public int thickness = 2;
    /** A half-hip's, a gambrel's, a mansard's, a hip-and-gable's or a bell's break (blocks; 0: as the house base works it out). */
    public int rise;
    /** The ridge: 0 slabs, 1 walls, 2 a ridge beam. */
    public int ridgeStyle;
    public boolean gableOut;
    public int bands;
    public int bandStep = 2;
    /** (t302) The roof's outline: the ends' and the back's slopes (0: the roof's), the curve (-1 convex .. 1 concave), the crest (0: none), the eaves moved. */
    public double endPitch;
    public double backPitch;
    public double curve;
    public int crest;
    public int eaveDrop;

    public double chimney = 0.6;
    public double dormer = 0.5;
    public double bayWindow = 0.3;
    public double balcony = 0.3;
    public double turret = 0.3;
    public double porch = 0.5;
    public double finial = 1.0;

    /** 0 the building of the house base's numbers; 1 to 6 the patterns A to F. */
    public int pattern;
    public int floorsMin = 2;
    public int floorsMax = 3;
    /** 0 a module of 4 or 5, 1 always 4, 2 always 5. */
    public int moduleChoice;
    /** The slopes a pattern may use: bit 0 1:2, bit 1 1:1, bit 2 2:1. */
    public int pitchMask = 6;
    public int overhangMin = 1;
    public int overhangMax = 2;
    /** The storeys a pattern's jetties are on: bit 0 the second storey, bit 1 the third, bit 2 the fourth. */
    public int patternJetty = 3;
    public int candidates = 4;
    public int retries = 4;
}
