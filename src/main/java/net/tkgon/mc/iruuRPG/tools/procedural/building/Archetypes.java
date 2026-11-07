package net.tkgon.mc.iruuRPG.tools.procedural.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * (t295) What a building is made as: one mass by the settings' numbers, or a pattern (archetype) whose masses are laid out by its rules, main mass first, then
 * the masses added to it, then the towers. Masses share a wall line or stand 2 or more blocks apart. The numbers a pattern draws ({@link Draw}) come from the
 * settings' ranges; when the building fails a check, one of them is drawn again ({@link Draw#change}).
 * <ul>
 *     <li>A 塔付きタウンハウス: a gabled main mass of 2 or 3 storeys facing the street, an octagonal or round tower 1 or 2 storeys over its ridge, a low wing
 *     with an arch passage.</li>
 *     <li>B 多層せり出し: one rectangle, every storey jettied a block out, a steep gable, a high stone podium, dormers.</li>
 *     <li>C 十字切妻の複合: an L or T plan, its gables crossing, a turret, a lean-to.</li>
 *     <li>D 広場の対称型: two masses mirrored, deep eaves, broad striped roofs, a lean-to roof along the ground storey's front.</li>
 *     <li>E 城郭の集合体: masses and towers on one thick podium, the spires stepped.</li>
 *     <li>F 特殊 (風車): B narrowed, gable to the front, the sails of a windmill on it.</li>
 * </ul>
 */
public final class Archetypes {

    private Archetypes() {
    }

    /** The patterns' names, 1 to 6 (0: one mass by the numbers). */
    public static String name(int pattern) {
        return switch (pattern) {
            case 1 -> "A 塔付きタウンハウス";
            case 2 -> "B 多層せり出し";
            case 3 -> "C 十字切妻の複合";
            case 4 -> "D 広場の対称型";
            case 5 -> "E 城郭の集合体";
            case 6 -> "F 特殊 (風車)";
            default -> "手動 (1棟)";
        };
    }

    /** The numbers a pattern draws (from the settings' ranges). */
    public static final class Draw {
        int floors;
        int module;
        int storey;
        double pitch;
        int overhang;
        /** A tower's storeys over the main mass's ridge (1 or 2). */
        int extra;
        boolean round;
        /** The pattern's own choice (C: L or T; D: hip or gable; E: the keep's roof). */
        int variant;

        Draw copy() {
            Draw copy = new Draw();
            copy.floors = floors;
            copy.module = module;
            copy.storey = storey;
            copy.pitch = pitch;
            copy.overhang = overhang;
            copy.extra = extra;
            copy.round = round;
            copy.variant = variant;
            return copy;
        }

        /** Draws one of the numbers again (the slope, the eaves, the storeys, a storey's height or the tower's height). */
        void change(Random random, BuildParams p) {
            switch (random.nextInt(5)) {
                case 0 -> pitch = pitch(p, random);
                case 1 -> overhang = between(random, p.overhangMin, p.overhangMax);
                case 2 -> floors = between(random, p.floorsMin, p.floorsMax);
                case 3 -> storey = 5 + random.nextInt(3);
                default -> extra = 1 + random.nextInt(2);
            }
        }
    }

    static int between(Random random, int a, int b) {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return low + random.nextInt(high - low + 1);
    }

    static double pitch(BuildParams p, Random random) {
        List<Double> allowed = new ArrayList<>();
        if ((p.pitchMask & 1) != 0) allowed.add(0.5);
        if ((p.pitchMask & 2) != 0) allowed.add(1.0);
        if ((p.pitchMask & 4) != 0) allowed.add(2.0);
        if (allowed.isEmpty()) allowed.add(1.0);
        return allowed.get(random.nextInt(allowed.size()));
    }

    public static Draw draw(BuildParams p, Random random) {
        Draw d = new Draw();
        d.floors = between(random, p.floorsMin, p.floorsMax);
        d.module = p.moduleChoice == 1 ? 4 : p.moduleChoice == 2 ? 5 : 4 + random.nextInt(2);
        d.storey = 5 + random.nextInt(3);
        d.pitch = pitch(p, random);
        d.overhang = between(random, p.overhangMin, p.overhangMax);
        d.extra = 1 + random.nextInt(2);
        d.round = random.nextBoolean();
        d.variant = random.nextInt(2);
        return d;
    }

    /** The cells of a side of {@code bays} bays of {@code module}: bays * module + 1. */
    static int span(int bays, int module) {
        return bays * module + 1;
    }

    /** The bays nearest {@code bays} whose side is an odd number of cells (a gabled mass's short side: its ridge is one block). */
    static int odd(int bays, int module) {
        if (span(bays, module) % 2 == 1) return bays;
        return bays + 1;
    }

    private static Mass mass(Building b, Footprint plan, int floors, int storey, int module, double pitch, int overhang, BuildParams p) {
        Mass m = b.add();
        m.plan = plan;
        m.floors = Math.max(1, floors);
        m.storey = Math.max(5, Math.min(7, storey));
        m.module = module;
        m.sill = p.sill;
        m.windowHeight = p.windowHeight;
        m.windowWidth = p.windowWidth;
        m.corbel = p.corbel;
        m.fasciaOut = p.fasciaOut;
        m.circlePosts = 8;
        m.roof.type = Mass.RoofType.GABLE;
        m.roof.pitch = pitch;
        // (a steep roof's eaves hang far down over a deep overhang: a block for 2:1, two for 1:1)
        m.roof.overhang = pitch >= 2.0 ? Math.min(overhang, 1) : pitch >= 1.0 ? Math.min(overhang, 2) : overhang;
        m.roof.thickness = p.thickness;
        m.roof.rise = p.rise;
        m.roof.ridgeStyle = p.ridgeStyle;
        m.roof.gableOut = p.gableOut;
        m.roof.bands = p.bands;
        m.roof.bandStep = p.bandStep;
        return m;
    }

    private static int podium(BuildParams p, int storey, double ratio) {
        if (ratio <= 0.01) return 0;
        return Math.max(1, (int) Math.round(storey * Math.max(0.4, Math.min(0.8, ratio))));
    }

    /** A tower standing on the podium beside a mass (its ridge {@code over} storeys under the tower's top), with a spire. */
    private static Mass tower(Building b, Footprint plan, Mass main, int extra, BuildParams p, Random random, int y0) {
        double ridge = main.wallTop() + main.roof.pitch * (Math.min(span(1, main.module), 9) + main.roof.overhang);
        int floors = (int) Math.ceil((ridge - y0 + extra * main.storey + 1.0) / main.storey);
        Mass t = mass(b, plan, floors, main.storey, 3, random.nextBoolean() ? 2.0 : 3.0, 1, p);
        t.y0 = y0;
        t.tower = true;
        t.roof.type = Mass.RoofType.CONE;
        t.roof.bands = p.bands;
        t.roof.finial = random.nextDouble() < p.finial;
        t.windowWidth = 1;
        t.circlePosts = plan.circle() ? (plan.radius() <= 3 ? 4 : 6) : 8;
        t.jetty = new boolean[8];
        return t;
    }

    /** A round or octagonal tower sharing the wall line x = {@code wallX} on its {@code side} (1 east of it, -1 west), its front at {@code frontZ}. */
    private static Footprint towerPlan(boolean round, int wallX, int side, int frontZ, int size) {
        if (round) {
            int r = size;
            return Footprint.circle(wallX + side * r, frontZ - r, r);
        }
        int a = 3;
        int b = Math.max(1, (2 * size + 1 - a) / 2);
        int total = a - 1 + 2 * b;
        int x0 = side < 0 ? wallX - total : wallX;
        return Footprint.octagon(x0, frontZ - total, a, b);
    }

    /** A turret at a corner of a rectangle's plan ({@code sx}, {@code sz}: the corner's way out), from storey {@code from} up to a storey over the mass. */
    static Mass turret(Building b, Mass main, int sx, int sz, int from, boolean round, BuildParams p, Random random) {
        int[] box = main.plan(Math.min(from, main.floors - 1)).box();
        int cx = sx > 0 ? box[2] : box[0];
        int cz = sz > 0 ? box[3] : box[1];
        int r = 2;
        Footprint plan = round ? Footprint.circle(cx + sx * (r - 1), cz + sz * (r - 1), r)
                : Footprint.octagon(cx + sx * (r - 1) - 2, cz + sz * (r - 1) - 2, 3, 1);
        int start = Math.max(0, Math.min(from, main.floors - 1));
        Mass t = mass(b, plan, main.floors - start + 1, main.storey, 3, random.nextBoolean() ? 2.0 : 3.0, 1, p);
        t.y0 = main.y0 + start * main.storey;
        t.corbelBase = start > 0;
        t.tower = true;
        t.minor = false;
        t.roof.type = Mass.RoofType.CONE;
        t.roof.finial = random.nextDouble() < p.finial;
        t.windowWidth = 1;
        t.circlePosts = 4;
        return t;
    }

    // ---- one mass by the numbers -------------------------------------------------------------------------------------------------

    /** One mass by the settings' numbers, its parts by their chances. */
    public static Building manual(BuildParams p, Random random) {
        Building b = new Building();
        b.pattern = name(0);
        int module = Math.max(4, Math.min(5, p.module));
        int w = span(Math.max(1, p.baysWide), module);
        int d = span(Math.max(1, p.baysDeep), module);
        int wingWidth = span(Math.max(1, Math.min(2, p.baysWide - 1)), module);
        int ext = Math.max(1, p.wingBays) * module;
        int zc = (d - 1) / 2 - Math.floorMod((d - 1) / 2, module);
        Footprint plan = switch (p.footprint) {
            case 1 -> Footprint.union(List.of(new int[]{0, 0, w - 1, d - 1}, new int[]{0, zc, wingWidth - 1, d - 1 + ext}));
            case 2 -> {
                int x0 = module * ((w - wingWidth) / 2 / module);
                yield Footprint.union(List.of(new int[]{0, 0, w - 1, d - 1}, new int[]{x0, zc, x0 + wingWidth - 1, d - 1 + ext}));
            }
            case 3 -> Footprint.union(List.of(new int[]{0, 0, w - 1, d - 1}, new int[]{0, zc, wingWidth - 1, d - 1 + ext},
                    new int[]{w - wingWidth, zc, w - 1, d - 1 + ext}));
            case 4 -> {
                int x0 = module * ((w - wingWidth) / 2 / module);
                yield Footprint.union(List.of(new int[]{0, 0, w - 1, d - 1}, new int[]{x0, -ext, x0 + wingWidth - 1, d - 1 + ext}));
            }
            case 5 -> Footprint.chamfer(0, 0, w, d, Math.max(2, Math.min(3, p.chamfer)));
            case 6 -> Footprint.octagon(0, 0, w, Math.max(1, p.slant));
            case 7 -> Footprint.circle(0, 0, Math.max(3, Math.min(7, p.radius)));
            default -> Footprint.rect(0, 0, w, d);
        };
        int storey = Math.max(5, Math.min(7, p.storey));
        Mass m = mass(b, plan, Math.max(1, Math.min(5, p.floors)), storey, module, BuildParams.pitchOf(p.pitch), Math.max(1, Math.min(3, p.overhang)), p);
        for (int s = 1; s < m.jetty.length; s++) m.jetty[s] = s <= 3 && (p.jetty >> (s - 1) & 1) != 0 && m.out(s - 1) < 3;
        m.roof.type = BuildParams.roofOf(p.roofType);
        if (plan.circle() || p.footprint == 6) {
            if (m.roof.type == Mass.RoofType.GABLE) m.roof.type = Mass.RoofType.CONE;
            m.circlePosts = Math.max(6, (int) Math.round(2 * Math.PI * p.radius / module / 2.0) * 2);
            m.tower = true;
        }
        m.roof.ridge = p.ridge == 1 ? 0 : p.ridge == 2 ? 1 : -1;
        m.roof.way = 2;
        m.roof.finial = random.nextDouble() < p.finial;
        m.door = true;
        int hp = podium(p, storey, p.plinth);
        m.y0 = hp;
        if (hp > 0) b.podiums.add(new Building.Podium(plan.expand(1), hp));
        parts(b, m, p, random, true);
        return b;
    }

    /** The parts of a mass by their chances: a chimney, dormers on the front and back, a bay window and a balcony, a turret, a porch. */
    private static void parts(Building b, Mass m, BuildParams p, Random random, boolean turrets) {
        if (random.nextDouble() < p.chimney) b.parts.add(new Building.Part(Building.PartType.CHIMNEY, m.id, 0, 0, 0));
        if (random.nextDouble() < p.dormer) {
            b.parts.add(new Building.Part(Building.PartType.DORMER, m.id, 2, -1, 0));
            b.parts.add(new Building.Part(Building.PartType.DORMER, m.id, 0, -1, 0));
        }
        boolean bay = random.nextDouble() < p.bayWindow;
        if (bay) b.parts.add(new Building.Part(Building.PartType.BAY_WINDOW, m.id, 1, -1, 1));
        if (random.nextDouble() < p.balcony) b.parts.add(new Building.Part(Building.PartType.BALCONY, m.id, bay ? 3 : 1, -1, 1));
        if (random.nextDouble() < p.porch) b.parts.add(new Building.Part(Building.PartType.PORCH, m.id, 2, -1, 0));
        if (turrets && !m.plan.circle() && m.plan.kind() != Footprint.Kind.OCTAGON && random.nextDouble() < p.turret) {
            turret(b, m, -1, -1, m.floors >= 2 ? 1 : 0, random.nextBoolean(), p, random);
        }
    }

    // ---- the patterns -----------------------------------------------------------------------------------------------------------------

    public static Building make(BuildParams p, Draw d, Random random) {
        Building b = switch (p.pattern) {
            case 1 -> townhouse(p, d, random);
            case 2 -> jettied(p, d, random, false);
            case 3 -> crossed(p, d, random);
            case 4 -> symmetric(p, d, random);
            case 5 -> castle(p, d, random);
            case 6 -> jettied(p, d, random, true);
            default -> manual(p, random);
        };
        b.pattern = name(p.pattern);
        return b;
    }

    /** A: the main mass gable to the street, a tower on its left sharing its wall, a low wing on its right with an arch passage. */
    private static Building townhouse(BuildParams p, Draw d, Random random) {
        Building b = new Building();
        int m4 = d.module;
        int w = span(odd(2, m4), m4);
        int dd = span(3, m4);
        int floors = Math.max(2, Math.min(3, d.floors));
        int hp = podium(p, d.storey, p.plinth);
        Mass main = mass(b, Footprint.rect(0, 0, w, dd), floors, d.storey, m4, Math.max(1.0, d.pitch), d.overhang, p);
        main.roof.ridge = 1;
        main.y0 = hp;
        main.door = true;
        // the tower on the left, its front with the main mass's
        Footprint plan = towerPlan(d.round, 0, -1, dd - 1, 3);
        Mass tower = tower(b, plan, main, d.extra, p, random, hp);
        // the wing on the right: one storey, an arch passage through it
        int ww = span(2, m4);
        int wd = span(2, m4);
        Mass wing = mass(b, Footprint.rect(w - 1, dd - wd, ww, wd), 1, d.storey, m4, Math.min(1.0, d.pitch), d.overhang, p);
        wing.y0 = hp;
        wing.roof.ridge = 0;
        wing.arch = m4 == 4 ? 3 : 3;
        if (hp > 0) {
            b.podiums.add(new Building.Podium(main.plan.expand(1), hp));
            b.podiums.add(new Building.Podium(wing.plan.expand(1), hp));
            b.podiums.add(new Building.Podium(tower.plan.expand(1), hp));
        }
        commonParts(b, main, p, random);
        return b;
    }

    private static void commonParts(Building b, Mass main, BuildParams p, Random random) {
        if (random.nextDouble() < p.chimney) b.parts.add(new Building.Part(Building.PartType.CHIMNEY, main.id, 0, 0, 0));
        if (random.nextDouble() < p.porch) b.parts.add(new Building.Part(Building.PartType.PORCH, main.id, 2, -1, 0));
        if (main.floors >= 2 && random.nextDouble() < p.balcony) b.parts.add(new Building.Part(Building.PartType.BALCONY, main.id, 2, -1, 1));
        else if (main.floors >= 2 && random.nextDouble() < p.bayWindow) b.parts.add(new Building.Part(Building.PartType.BAY_WINDOW, main.id, 2, -1, 1));
    }

    /** B (and F, {@code mill}): one rectangle, every storey jettied, a steep gable, a high podium; dormers (B) or the sails of a windmill (F). */
    private static Building jettied(BuildParams p, Draw d, Random random, boolean mill) {
        Building b = new Building();
        int m4 = d.module;
        int floors = Math.max(mill ? 3 : 2, Math.min(4, Math.max(d.floors, 3)));
        int w = mill ? span(odd(2, m4), m4) : span(3, m4);
        int dd = mill ? span(2, m4) : span(odd(2, m4), m4);
        double steep = (p.pitchMask & 4) != 0 ? 2.0 : Math.max(1.0, d.pitch);
        int hp = podium(p, d.storey, Math.max(0.7, p.plinth));
        Mass main = mass(b, Footprint.rect(0, 0, w, dd), floors, d.storey, m4, steep, d.overhang, p);
        main.roof.ridge = mill ? 1 : 0;
        main.y0 = hp;
        main.door = true;
        int bits = p.patternJetty == 0 ? 7 : p.patternJetty;
        int total = 0;
        for (int s = 1; s < floors && s < main.jetty.length; s++) {
            boolean out = (bits >> (s - 1) & 1) != 0 && total < 3;
            main.jetty[s] = out;
            if (out) total++;
        }
        if (hp > 0) b.podiums.add(new Building.Podium(main.plan.expand(1), hp));
        if (mill) {
            b.parts.add(new Building.Part(Building.PartType.SAILS, main.id, 2, 0, 0));
            if (random.nextDouble() < p.chimney) b.parts.add(new Building.Part(Building.PartType.CHIMNEY, main.id, 0, 0, 0));
        } else {
            if (random.nextDouble() < Math.max(p.dormer, 0.5)) {
                b.parts.add(new Building.Part(Building.PartType.DORMER, main.id, 2, -1, 0));
                b.parts.add(new Building.Part(Building.PartType.DORMER, main.id, 0, -1, 0));
            }
            if (random.nextDouble() < p.chimney) b.parts.add(new Building.Part(Building.PartType.CHIMNEY, main.id, 0, 0, 0));
        }
        if (random.nextDouble() < p.porch) b.parts.add(new Building.Part(Building.PartType.PORCH, main.id, 2, -1, 0));
        return b;
    }

    /** C: an L or T plan of crossing gables, a turret at a back corner, a lean-to on the left. */
    private static Building crossed(BuildParams p, Draw d, Random random) {
        Building b = new Building();
        int m4 = d.module;
        int w = span(4, m4);
        int dd = span(odd(2, m4), m4);
        int ww = span(odd(2, m4), m4);
        int ext = 2 * m4;
        int zc = (dd - 1) / 2 - Math.floorMod((dd - 1) / 2, m4);
        boolean tee = d.variant == 1;
        int x0 = tee ? m4 : w - ww;
        Footprint plan = Footprint.union(List.of(new int[]{0, 0, w - 1, dd - 1}, new int[]{x0, zc, x0 + ww - 1, dd - 1 + ext}));
        int floors = Math.max(2, Math.min(3, d.floors));
        int hp = podium(p, d.storey, p.plinth);
        Mass main = mass(b, plan, floors, d.storey, m4, Math.max(1.0, d.pitch), d.overhang, p);
        main.y0 = hp;
        main.door = true;
        if (hp > 0) b.podiums.add(new Building.Podium(plan.expand(1), hp));
        // the lean-to on the left: one storey at 1:2, its roof's top at the ground storey's fascia, its walls as high as its eaves let them be
        Mass lean = mass(b, Footprint.rect(-m4, 0, m4 + 1, dd), 1, d.storey, m4, 0.5, 1, p);
        lean.y0 = hp;
        lean.minor = true;
        lean.roof.type = Mass.RoofType.SHED;
        lean.roof.way = 3;
        lean.roof.topAt = hp + d.storey;
        int eaves = (int) Math.floor(lean.roof.topAt - Roofs.step(m4 + 1 + 2 * lean.roof.overhang - 0.25, 0.5));
        lean.storey = Math.max(3, eaves - hp);
        lean.windowHeight = Math.min(lean.windowHeight, Math.max(1, lean.storey - 2));
        if (hp > 0) b.podiums.add(new Building.Podium(lean.plan.expand(1), hp));
        turret(b, main, 1, -1, 1, d.round, p, random);
        commonParts(b, main, p, random);
        return b;
    }

    /** D: two masses mirrored, deep eaves, broad striped roofs, a lean-to roof along each ground storey's front. */
    private static Building symmetric(BuildParams p, Draw d, Random random) {
        Building b = new Building();
        int m4 = d.module;
        int w = span(3, m4);
        int dd = span(odd(2, m4), m4);
        int overhang = Math.max(2, d.overhang);
        int gap = 2 * overhang + 1;
        int floors = Math.max(2, Math.min(3, d.floors));
        int hp = podium(p, d.storey, p.plinth);
        boolean hip = d.variant == 0;
        boolean chimney = random.nextDouble() < p.chimney;
        boolean dormer = random.nextDouble() < p.dormer;
        for (int side = 0; side < 2; side++) {
            Mass m = mass(b, Footprint.rect(side == 0 ? 0 : w + gap, 0, w, dd), floors, d.storey, m4, Math.max(0.5, Math.min(1.0, d.pitch)), overhang, p);
            m.roof.type = hip ? Mass.RoofType.HIP : Mass.RoofType.GABLE;
            m.roof.ridge = 0;
            m.roof.bands = p.bands == 0 ? 1 : p.bands;
            m.y0 = hp;
            m.door = true;
            if (hp > 0) b.podiums.add(new Building.Podium(m.plan.expand(1), hp));
            b.parts.add(new Building.Part(Building.PartType.PORCH, m.id, 2, -2, 0));
            if (dormer) b.parts.add(new Building.Part(Building.PartType.DORMER, m.id, 2, -1, 0));
            if (chimney) b.parts.add(new Building.Part(Building.PartType.CHIMNEY, m.id, 0, side, 0));
        }
        return b;
    }

    /** E: a keep and a hall on one thick podium, towers at their corners, each tower's spire a storey off the next. */
    private static Building castle(BuildParams p, Draw d, Random random) {
        Building b = new Building();
        int m4 = d.module;
        int w = span(odd(3, m4), m4);
        int dd = span(odd(3, m4), m4);
        int floors = Math.max(3, Math.min(4, d.floors + 1));
        int hp = podium(p, d.storey, 0.8) + 1;
        Mass keep = mass(b, Footprint.rect(0, 0, w, dd), floors, d.storey, m4, Math.max(1.0, d.pitch), d.overhang, p);
        keep.y0 = hp;
        keep.roof.type = d.variant == 0 ? Mass.RoofType.HIP : Mass.RoofType.GABLE;
        keep.door = true;
        int hw = span(3, m4);
        int hd = span(odd(2, m4), m4);
        Mass hall = mass(b, Footprint.rect(w - 1, dd - hd, hw, hd), floors - 1, d.storey, m4, Math.max(1.0, d.pitch), d.overhang, p);
        hall.y0 = hp;
        hall.roof.ridge = 0;
        // the towers: round ones at the keep's left corners, an octagonal one at the hall's front right, their tops a storey apart
        int r = 3;
        tower(b, Footprint.circle(-r + 1, -r + 1, r), keep, 1, p, random, hp);
        tower(b, Footprint.circle(-r + 1, dd - 1 + r - 1, r), keep, 2, p, random, hp);
        int hx = w - 1 + hw - 1;
        tower(b, Footprint.octagon(hx - 2, dd - 1 - 2, 3, 2), keep, 0, p, random, hp);
        // the podium round all of it, two blocks out
        List<int[]> rects = new ArrayList<>();
        for (Mass m : b.masses) {
            int[] box = m.plan.box();
            rects.add(new int[]{box[0] - 2, box[1] - 2, box[2] + 2, box[3] + 2});
        }
        b.podiums.add(new Building.Podium(Footprint.union(merge(rects)), hp));
        if (random.nextDouble() < p.chimney) b.parts.add(new Building.Part(Building.PartType.CHIMNEY, hall.id, 0, 0, 0));
        if (random.nextDouble() < p.porch) b.parts.add(new Building.Part(Building.PartType.PORCH, keep.id, 2, -1, 0));
        return b;
    }

    /** The rectangles made one piece: each one's box grown to touch the next (the podium's plan must be one piece). */
    private static List<int[]> merge(List<int[]> rects) {
        int[] all = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int[] r : rects) {
            all[0] = Math.min(all[0], r[0]);
            all[1] = Math.min(all[1], r[1]);
            all[2] = Math.max(all[2], r[2]);
            all[3] = Math.max(all[3], r[3]);
        }
        // (the keep's and the hall's boxes with the towers' overlap; their union is one piece, the outer box keeps it simple when it is not)
        List<int[]> out = new ArrayList<>(rects);
        if (!connected(out)) return List.of(all);
        return out;
    }

    private static boolean connected(List<int[]> rects) {
        boolean[] seen = new boolean[rects.size()];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(0);
        seen[0] = true;
        while (!queue.isEmpty()) {
            int[] a = rects.get(queue.poll());
            for (int index = 0; index < rects.size(); index++) {
                int[] b = rects.get(index);
                if (seen[index]) continue;
                if (a[0] <= b[2] + 1 && b[0] <= a[2] + 1 && a[1] <= b[3] + 1 && b[1] <= a[3] + 1) {
                    seen[index] = true;
                    queue.add(index);
                }
            }
        }
        for (boolean s : seen) if (!s) return false;
        return true;
    }
}
