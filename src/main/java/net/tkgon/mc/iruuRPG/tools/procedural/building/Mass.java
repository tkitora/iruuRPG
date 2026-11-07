package net.tkgon.mc.iruuRPG.tools.procedural.building;

/**
 * (t295) One mass (棟) of a building: its ground plan, its storeys stacked on it (each the plan of the one below, or that grown out by a block: a jetty), the
 * walls cut into bays, and its roof. The numbers are set by the one who makes the building ({@link Archetypes}, or the settings for one mass).
 */
public final class Mass {

    /** The kinds of roof (they differ in the slope of each side, see {@link Roofs}). */
    public enum RoofType {
        /** 切妻: the two short sides (or the sides across the ridge) upright. */
        GABLE,
        /** 寄棟: every side slopes alike. */
        HIP,
        /** 半切妻: the gable sides upright up to {@link Roof#rise}, sloping above it. */
        HALF_HIP,
        /** 腰折れ: steep, then gentle from {@link Roof#rise} blocks in. */
        GAMBREL,
        /** 片流れ: one side slopes ({@link Roof#way}: the way the low side faces), the rest upright. */
        SHED,
        /** 円錐・尖塔: a circle's or an octagon's sides all steep. */
        CONE,
        /** 裾の反り: gentle at the eaves for {@link Roof#rise} blocks, then steep. */
        BELL,
        /** (t302, the house base's roofs) 平ら: flat. */
        FLAT,
        /** マンサード: steep, then near flat, from all four sides. */
        MANSARD,
        /** 入母屋: a hip low at the ends, a gable over it. */
        IRIMOYA,
        /** 反り屋根: concave from all four sides (the eaves sweep out, the top is steep). */
        CURVED,
        /** 尖り屋根: a gable whose section is {@link Roof#steps}. */
        STEPPED,
        /** 尖り寄棟: a hip whose section is {@link Roof#steps}. */
        STEPPED_HIP
    }

    /** The roof of a mass. */
    public static final class Roof {
        public RoofType type = RoofType.GABLE;
        /** The slope: rise per block in (0.5 = 1:2, 1 = 1:1, 2 = 2:1, 3 = 3:1 for towers). */
        public double pitch = 1.0;
        /** How far the roof reaches past the walls (1 to 3). */
        public int overhang = 1;
        /** How thick the roof is, up and down (2 to 3). */
        public int thickness = 2;
        /** The ridge runs along x (0) or z (1); -1: along the longer side. */
        public int ridge = -1;
        /** A half-hip's upright height, a gambrel's or a bell's break (blocks in). */
        public int rise = 3;
        /** A shed's low side: the way it faces (0 north, 1 east, 2 south, 3 west). */
        public int way = 2;
        /** The ridge: slabs (0) or walls (1). */
        public int ridgeStyle;
        /** The gables stand a block out over the wall below them. */
        public boolean gableOut;
        /** The stripes: 0 none, 1 by height, 2 by face. */
        public int bands;
        public int bandStep = 2;
        /** A finial on the top (a spire). */
        public boolean finial;
        /** (a lean-to, a porch) the top of its high side: the eaves are set from it, not from the walls. -1: from the walls. */
        public int topAt = -1;
        /** (a porch, a bay's roof) the gaps under its upright sides are left open. */
        public boolean open;
        /** (t302, the outline) the slope of the ends of a hip (0: the roof's), of the back (0: the front's). */
        public double endPitch;
        public double backPitch;
        /** (t302) how the slope curves from the eaves to the top: above 0 concave (反り: steep at the top), below 0 convex (むくり); -1 to 1. */
        public double curve;
        /** (t302) the highest the roof rises over its eaves (0: no limit; a flat top over it). */
        public int crest;
        /** (t302) the eaves moved up (or down) from where they rest on the walls. */
        public int eaveDrop;
        /** (t302) the section of a roof of steps: how high each block in from the eaves rises (1 1 1 2 3 3), stretched to the roof when {@link #stepFit}. */
        public int[] steps = {1, 1, 1, 2, 3, 3};
        public boolean stepFit = true;
        /** (t302) a ridge beam: a log along the ridge, out past the gables with its ends raised (the ridge style 2). */
        public boolean beam;

        public Roof copy() {
            Roof copy = new Roof();
            copy.type = type;
            copy.pitch = pitch;
            copy.overhang = overhang;
            copy.thickness = thickness;
            copy.ridge = ridge;
            copy.rise = rise;
            copy.way = way;
            copy.ridgeStyle = ridgeStyle;
            copy.gableOut = gableOut;
            copy.bands = bands;
            copy.bandStep = bandStep;
            copy.finial = finial;
            copy.topAt = topAt;
            copy.open = open;
            copy.endPitch = endPitch;
            copy.backPitch = backPitch;
            copy.curve = curve;
            copy.crest = crest;
            copy.eaveDrop = eaveDrop;
            copy.steps = steps.clone();
            copy.stepFit = stepFit;
            copy.beam = beam;
            return copy;
        }
    }

    public final int id;
    public Footprint plan;
    /** The bottom of the first storey (over a podium, its height). */
    public int y0;
    public int floors = 2;
    /** A storey's height, its fascia (the log course on its top) in (5 to 7; 3 to 8 for the house base's numbers). */
    public int storey = 6;
    /** How far storey i is jettied out over the one below (index 0 is the ground storey: never), at the front and the back, and the sides too with {@link #jettySides}. */
    public int[] jetty = new int[8];
    public boolean jettySides = true;
    /** (t302) The posts every this many blocks along a side (0: at the corners only; -1: every {@link #module}), counted from both ends with {@link #mirror}. */
    public int spacing = -1;
    public boolean mirror;
    /** (t302, the house base's walls) the ground storey of stone; the timber frame's braces; the walls set back behind the frame; the windows; their glass. */
    public boolean stoneGround;
    public boolean timber;
    public boolean recess = true;
    public boolean windows = true;
    public boolean glass = true;
    /** (t302) a little roof out of the wall between the storeys (this many blocks; not on a jettied face). */
    public int skirt;
    /** (t302) a stone band a block out round the ground storey, this high (its top stairs). */
    public int plinthBand;
    /** (t302) corbels under the jetties and the eaves. */
    public boolean brackets = true;
    /** (t302) the walls go down to the ground where it is lower (a mass standing on the ground). */
    public boolean foundation = true;
    /** (t302) the part of the house base it is (its place in the list of parts; -1: the base): the wand chooses it by its blocks. */
    public int owner = -1;
    /** (t302) a wing: its roof joins {@link #parent}'s by the highest (valleys), as a dormer's does. */
    public boolean joined;
    /** (t302) the plan its roof is made over when not its top storey's (a wing's roof runs back into the house's to its ridge). */
    public Footprint roofPlan;
    /** The fascias stand a block out of the wall line. */
    public boolean fasciaOut;
    /** The width of a bay with its posts (4 or 5). */
    public int module = 4;
    public int sill = 1;
    public int windowHeight = 2;
    public int windowWidth = 2;
    /** Under a jetty and the eaves: upside-down stairs (0) or logs lying out (1). */
    public int corbel;
    /** A circle's posts round it. */
    public int circlePosts = 8;
    public Roof roof = new Roof();
    /** A tower: it counts for the outline of the building; its roof may be steeper than 2:1. */
    public boolean tower;
    /** A dormer: its walls go down to the roof under it, its roof is part of {@link #parent}'s. */
    public boolean dormer;
    public int parent = -1;
    /** The entrance: in the middle bay of the side facing {@link #doorWay} on the ground storey. */
    public boolean door;
    public int doorWay = 2;
    /** An arch passage through the ground storey, front to back (its width, odd; 0 none). */
    public int arch;
    /** A turret starting high up: a corbelled cone of upside-down stairs under it. */
    public boolean corbelBase;
    /** A lean-to or the like: it does not count as a mass of its own in the outline. */
    public boolean minor;
    /** A porch: no walls, only the posts at its corners under its roof. */
    public boolean postsOnly;

    public Mass(int id) {
        this.id = id;
    }

    /** How far storey {@code s} is jettied out over the ground storey (at the front and the back), at most 3. */
    public int out(int s) {
        int total = 0;
        for (int i = 1; i <= s && i < jetty.length; i++) total += Math.max(0, jetty[i]);
        return Math.min(3, total);
    }

    /** How far storey {@code s} is jettied out at the sides. */
    public int outSides(int s) {
        return jettySides ? out(s) : 0;
    }

    public Footprint plan(int s) {
        return plan.expand(out(s), outSides(s));
    }

    /** The post spacing of its walls. */
    public int posts() {
        return spacing < 0 ? module : spacing;
    }

    public Footprint top() {
        return plan(floors - 1);
    }

    /** The y of the top of the walls (the last fascia). */
    public int wallTop() {
        return y0 + floors * storey - 1;
    }
}
