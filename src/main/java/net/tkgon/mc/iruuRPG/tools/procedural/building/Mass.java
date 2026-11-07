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
        BELL
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
            return copy;
        }
    }

    public final int id;
    public Footprint plan;
    /** The bottom of the first storey (over a podium, its height). */
    public int y0;
    public int floors = 2;
    /** A storey's height, its fascia (the log course on its top) in (5 to 7). */
    public int storey = 6;
    /** Whether storey i is jettied a block out over the one below (index 0 is the ground storey: never). */
    public boolean[] jetty = new boolean[8];
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

    /** How far storey {@code s} is jettied out over the ground storey. */
    public int out(int s) {
        int total = 0;
        for (int i = 1; i <= s && i < jetty.length; i++) if (jetty[i]) total++;
        return total;
    }

    public Footprint plan(int s) {
        return plan.expand(out(s));
    }

    public Footprint top() {
        return plan(floors - 1);
    }

    /** The y of the top of the walls (the last fascia). */
    public int wallTop() {
        return y0 + floors * storey - 1;
    }
}
