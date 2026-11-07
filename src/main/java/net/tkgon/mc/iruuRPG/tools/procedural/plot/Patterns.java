package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * (t313, spec 6/6) Buildings of lots made by themselves: a pattern says what lots there are, the grammar lays them out (the main block taking half to
 * seven tenths of the site; a wing on a side, one or two storeys lower and no wider than the main block's short side; a tower at a corner, at most half as
 * wide and one or two storeys higher; a porch or stairs on a wing; any lot on stilts by a chance), and the rest is drawn at random (storeys, roofs, walls,
 * parts), so each one is different. {@link Designer} checks the balance, makes it again on a failure and keeps the best of several.
 */
public final class Patterns {

    private Patterns() {
    }

    public enum Pattern {
        SINGLE("単棟"), LEAN_TO("本棟 + 下屋"), CROSS("L字・T字の交差"), TOWER("塔付き"), STILTS("高床・桟橋"), PAGODA("多層の塔"), TERRACED("段状"), WINDMILL("風車");

        public final String label;

        Pattern(String label) {
            this.label = label;
        }

        /** The pattern of a number (1 to 8; anything else: none). */
        public static Pattern of(int number) {
            return number >= 1 && number <= values().length ? values()[number - 1] : null;
        }
    }

    /** What the tool's settings give the maker: the site, the module, the chance of stilts, how many parts, the look. */
    public static final class Rules {
        public int siteWidth = 29;
        public int siteDepth = 25;
        public int module = 4;
        /** The chance of a lot standing on stilts. */
        public double stilts = 0.1;
        /** How many parts (dormers, chimneys, porches, balconies): 0 few to 3 many. */
        public int detail = 2;
        /** The steepest slope it uses (the place in {@link Plan#PITCHES}). */
        public int steepest = 2;
    }

    /** A plan of the pattern drawn with {@code random}. */
    public static Plan make(Pattern pattern, Rules rules, Random random) {
        Plan plan = new Plan();
        plan.siteWidth = rules.siteWidth;
        plan.siteDepth = rules.siteDepth;
        plan.module = rules.module;
        Look look = Look.draw(random);
        switch (pattern) {
            case SINGLE -> {
                Plan.Lot main = main(plan, rules, random, look, 0.55, 0.7, 2 + random.nextInt(2), 0, 0, 0);
                parts(main, rules, random, true);
            }
            case LEAN_TO -> {
                // (room kept on the side the lean-to goes)
                int side = sideFor(random, 1, 3);
                Plan.Lot main = main(plan, rules, random, look, 0.45, 0.6, 2 + random.nextInt(2), 2 * rules.module, 0, side);
                parts(main, rules, random, true);
                Plan.Lot wing = wing(plan, main, rules, random, look, side, 1 + random.nextInt(Math.min(2, main.floors.size() - 1)), false);
                if (wing != null) {
                    wing.roof.type = Plan.RoofType.SHED;
                    wing.roof.pitch = 0;
                }
            }
            case CROSS -> {
                Plan.Lot main = main(plan, rules, random, look, 0.4, 0.55, 2, 0, 2 * rules.module, 2);
                parts(main, rules, random, true);
                // (an L: a wing at an end coming forward; a T: in the middle of the front)
                boolean tee = random.nextBoolean();
                Plan.Lot wing = wing(plan, main, rules, random, look, 2, 0, tee);
                if (wing != null) {
                    wing.floors.get(0).door = 2;
                    wing.roof.dormers = 0;
                    if (random.nextDouble() < 0.4) wing.floors.get(wing.floors.size() - 1).balcony[2] = rules.detail >= 2;
                }
                if (!tee && random.nextDouble() < 0.5) {
                    Plan.Lot back = wing(plan, main, rules, random, look, 0, 0, false);
                    if (back != null) back.roof.dormers = 0;
                }
                main.floors.get(0).door = wing == null ? 2 : -1;
                if (wing != null) main.porch = false;
            }
            case TOWER -> {
                Plan.Lot main = main(plan, rules, random, look, 0.4, 0.55, 2, rules.module, rules.module, -1);
                parts(main, rules, random, true);
                tower(plan, main, rules, random, look, 1 + random.nextInt(2), random.nextBoolean());
            }
            case STILTS -> {
                Plan.Lot main = main(plan, rules, random, look, 0.4, 0.55, 1 + random.nextInt(2), 0, 2 * rules.module, 2);
                main.foundation = Plan.Foundation.STILTS;
                main.base = 3 + random.nextInt(3);
                for (int side = 0; side < 4; side++) main.floors.get(0).balcony[side] = true;
                main.floors.get(0).door = 2;
                main.roof.type = random.nextBoolean() ? Plan.RoofType.HIP : Plan.RoofType.GABLE;
                main.roof.overhang[0] = main.roof.overhang[1] = main.roof.overhang[2] = main.roof.overhang[3] = 2;
                main.porch = false;
                // (the deck round it is the gallery; a lower wing on stilts of its own beside it now and then)
                if (random.nextBoolean()) {
                    Plan.Lot side = wing(plan, main, rules, random, look, random.nextBoolean() ? 1 : 3, main.floors.size() > 1 ? 1 : 0, false);
                    if (side != null) {
                        side.foundation = Plan.Foundation.STILTS;
                        side.base = main.base;
                        side.porch = false;
                        side.floors.get(0).door = -1;
                    }
                }
            }
            case PAGODA -> {
                int size = fit(rules.module, Math.min(rules.siteWidth, rules.siteDepth) - 2 * rules.module, 2, 3);
                Plan.Lot tower = new Plan.Lot("tower", random.nextBoolean() ? Plan.Shape.ROUND : Plan.Shape.OCTAGON,
                        Layout.snap((rules.siteWidth - size) / 2, rules.module, rules.siteWidth - size), Layout.snap((rules.siteDepth - size) / 2, rules.module,
                        rules.siteDepth - size), size, size, 3 + random.nextInt(3));
                look.apply(tower, random);
                for (Plan.Floor floor : tower.floors) {
                    floor.skirt = true;
                    floor.height = 4 + random.nextInt(2);
                }
                tower.floors.get(0).height = 5;
                tower.floors.get(0).door = 2;
                tower.roof.type = Plan.RoofType.CONE;
                tower.roof.pitch = Math.min(rules.steepest + 1, 3);
                tower.roof.finial = true;
                plan.lots.add(tower);
            }
            case TERRACED -> {
                // three blocks in a row along the front, each lower than the one before
                int m = rules.module;
                int depth = Math.min(fit(m, (int) (rules.siteDepth * 0.5), 2, 3), Layout.fit(rules.siteDepth, m, 2, rules.siteDepth));
                int z = Layout.snap((rules.siteDepth - depth) / 2, m, rules.siteDepth - depth);
                int floors = 3;
                // (three blocks sharing their side walls fit the site: 3 widths less the 2 shared lines)
                int width = Math.max(m + 1, Layout.fit((rules.siteWidth + 2) / 3, m, 1, (rules.siteWidth + 2) / 3));
                int x = Layout.snap((rules.siteWidth - (3 * width - 2)) / 2, m, rules.siteWidth - (3 * width - 2));
                for (int i = 0; i < 3; i++) {
                    Plan.Lot lot = new Plan.Lot(i == 0 ? "main" : "step" + i, Plan.Shape.RECT, x, z, width, depth, Math.max(1, floors - i));
                    look.apply(lot, random);
                    lot.roof.type = i == 0 ? Plan.RoofType.GABLE : random.nextBoolean() ? Plan.RoofType.GABLE : Plan.RoofType.HIP;
                    lot.roof.ridge = 0;
                    if (i == 0) lot.floors.get(0).door = 2;
                    plan.lots.add(lot);
                    x += width - 1;
                }
                parts(plan.lots.get(0), rules, random, true);
            }
            case WINDMILL -> {
                int size = fit(rules.module, Math.min(rules.siteWidth, rules.siteDepth) / 2, 2, 2);
                Plan.Lot mill = new Plan.Lot("mill", random.nextBoolean() ? Plan.Shape.OCTAGON : Plan.Shape.ROUND,
                        Layout.snap((rules.siteWidth - size) / 2, rules.module, rules.siteWidth - size), Layout.snap((rules.siteDepth - size) / 2, rules.module,
                        rules.siteDepth - size), size, size, 3 + random.nextInt(2));
                look.apply(mill, random);
                mill.floors.get(0).door = 2;
                mill.roof.type = Plan.RoofType.CONE;
                mill.roof.pitch = 2;
                mill.roof.special = Plan.Special.WINDMILL;
                mill.roof.specialSide = 2;
                mill.roof.finial = random.nextBoolean();
                plan.lots.add(mill);
                if (random.nextBoolean()) {
                    Plan.Lot shed = wing(plan, mill, rules, random, look, random.nextBoolean() ? 1 : 3, mill.floors.size() - 1, false);
                    if (shed != null) {
                        shed.roof.type = Plan.RoofType.SHED;
                        shed.roof.pitch = 0;
                    }
                }
            }
        }
        // any lot may stand on stilts (spec 6/6: by the chance p)
        for (Plan.Lot lot : plan.lots) {
            if (lot.foundation != Plan.Foundation.STILTS && random.nextDouble() < rules.stilts && lot.shape == Plan.Shape.RECT && plan.lots.size() == 1) {
                lot.foundation = Plan.Foundation.STILTS;
                lot.base = 3 + random.nextInt(2);
            }
        }
        return plan;
    }

    /**
     * The main block: half to seven tenths of the site (between {@code low} and {@code high}), leaving {@code roomX} blocks free beside it and {@code roomZ}
     * before it for what goes there ({@code toward}: the side a wing goes on, 1 east or 3 west, 2 the front; -1 room all round), in the middle toward the back.
     */
    private static Plan.Lot main(Plan plan, Rules rules, Random random, Look look, double low, double high, int floors, int roomX, int roomZ, int toward) {
        int m = rules.module;
        double share = low + random.nextDouble() * (high - low);
        double area = rules.siteWidth * rules.siteDepth * share;
        double ratio = 1.3 + random.nextDouble() * 0.8;
        int w = fit(m, (int) Math.round(Math.sqrt(area * ratio)), 2, 99);
        int d = fit(m, (int) Math.round(area / Math.max(1, w)), 2, 99);
        int spareX = toward < 0 ? 2 * roomX : roomX;
        int spareZ = toward < 0 ? 2 * roomZ : roomZ;
        w = Math.min(w, Layout.fit(rules.siteWidth - spareX, m, 2, rules.siteWidth - spareX));
        d = Math.min(d, Layout.fit(rules.siteDepth - m - spareZ, m, 2, rules.siteDepth - spareZ));
        int x = switch (toward) {
            case 1 -> 0;
            case 3 -> Layout.snap(rules.siteWidth - w, m, rules.siteWidth - w);
            default -> Layout.snap((rules.siteWidth - w) / 2, m, rules.siteWidth - w);
        };
        int z = toward == 2 ? 0 : Layout.snap((rules.siteDepth - d) / 3, m, rules.siteDepth - d);
        Plan.Lot main = new Plan.Lot("main", Plan.Shape.RECT, x, z, w, d, floors);
        look.apply(main, random);
        main.floors.get(0).door = 2;
        plan.lots.add(main);
        return main;
    }

    /** n·m + 1 near {@code length}, n from {@code least} to {@code most}. */
    static int fit(int m, int length, int least, int most) {
        int n = Math.max(least, Math.min(most, (int) Math.round((length - 1) / (double) m)));
        return n * m + 1;
    }

    private static int sideFor(Random random, int... sides) {
        return sides[random.nextInt(sides.length)];
    }

    /**
     * A wing on side {@code side} of {@code parent} (spec 6/6): {@code lower} storeys fewer (0: as many, its eaves as high, joining the roofs), no wider than
     * the parent's short side; at the side's end, or in its middle ({@code middle}). Null when the site has no room for it.
     */
    private static Plan.Lot wing(Plan plan, Plan.Lot parent, Rules rules, Random random, Look look, int side, int lower, boolean middle) {
        int m = rules.module;
        int floors = Math.max(1, parent.floors.size() - lower);
        int short_ = Math.min(parent.w, parent.d);
        int across = fit(m, Math.min(short_, (short_ + 1) / 2 + m + random.nextInt(m)), 1, 99);
        across = Math.min(across, short_);
        int along = fit(m, 2 * m + 1 + random.nextInt(2) * m, 1, 99);
        Plan.Lot wing = new Plan.Lot(plan.freeName(lower == 0 ? "wing" : "lean"), Plan.Shape.RECT, 0, 0, 0, 0, floors);
        // (its storeys as high as the parent's, so the eaves line up when it has as many)
        for (int k = 0; k < floors; k++) wing.floors.get(k).height = parent.floors.get(k).height;
        look.apply(wing, random);
        for (int k = 0; k < floors; k++) wing.floors.get(k).height = parent.floors.get(k).height;
        wing.foundation = parent.foundation;
        wing.base = parent.base;
        wing.roof.group = parent.roof.group;
        wing.roof.pitch = parent.roof.pitch;
        switch (side) {
            case 0, 2 -> {
                wing.w = across;
                wing.d = along;
                int from = middle ? parent.x + (parent.w - across) / 2 : random.nextBoolean() ? parent.x : parent.x + parent.w - across;
                wing.x = Layout.snap(from, m, rules.siteWidth - across);
                wing.z = side == 2 ? parent.z + parent.d - 1 : parent.z - along + 1;
            }
            default -> {
                wing.w = along;
                wing.d = across;
                int from = middle ? parent.z + (parent.d - across) / 2 : parent.z + parent.d - across;
                wing.z = Layout.snap(from, m, rules.siteDepth - across);
                wing.x = side == 1 ? parent.x + parent.w - 1 : parent.x - along + 1;
            }
        }
        if (wing.x < 0 || wing.z < 0 || wing.x + wing.w > rules.siteWidth || wing.z + wing.d > rules.siteDepth) return null;
        wing.roof.type = lower == 0 ? Plan.RoofType.GABLE : random.nextBoolean() ? Plan.RoofType.GABLE : Plan.RoofType.SHED;
        if (random.nextDouble() < 0.5 && rules.detail >= 1) wing.floors.get(0).door = side;
        if (wing.floors.get(0).door >= 0 && rules.detail >= 2 && random.nextDouble() < 0.4) wing.porch = true;
        plan.lots.add(wing);
        return wing;
    }

    /** A tower at a corner of {@code parent} (spec 6/6): at most half its width, {@code higher} storeys more, round or square, a steep cone or pyramid. */
    private static Plan.Lot tower(Plan plan, Plan.Lot parent, Rules rules, Random random, Look look, int higher, boolean round) {
        int m = rules.module;
        int size = fit(m, Math.max(2 * m + 1, Math.min(parent.w, parent.d) / 2 + 1), 2, 2);
        Plan.Lot tower = new Plan.Lot(plan.freeName("tower"), round ? Plan.Shape.ROUND : Plan.Shape.RECT, 0, 0, size, size, parent.floors.size() + higher);
        look.apply(tower, random);
        tower.foundation = parent.foundation;
        tower.base = parent.base;
        for (int k = 0; k < tower.floors.size(); k++) tower.floors.get(k).height = k < parent.floors.size() ? parent.floors.get(k).height : 4 + random.nextInt(2);
        tower.roof.type = round ? Plan.RoofType.CONE : Plan.RoofType.PYRAMID;
        tower.roof.pitch = Math.max(2, Math.min(4, rules.steepest + 1));
        tower.roof.finial = random.nextDouble() < 0.7;
        tower.roof.group = parent.roof.group + 1;
        boolean right = random.nextBoolean();
        boolean front = random.nextDouble() < 0.6;
        // (at a corner: half over the parent's corner, half out)
        tower.x = right ? parent.x + parent.w - 1 - size / 2 + m / 2 : parent.x - size / 2 - m / 2;
        tower.z = front ? parent.z + parent.d - 1 - size / 2 : parent.z - size / 2;
        tower.x = Layout.snap(tower.x, m, rules.siteWidth - size);
        tower.z = Layout.snap(tower.z, m, rules.siteDepth - size);
        plan.lots.add(tower);
        return tower;
    }

    /** The parts of a main block: dormers, a chimney, a porch, by the detail wanted. */
    private static void parts(Plan.Lot lot, Rules rules, Random random, boolean main) {
        int detail = rules.detail;
        if (detail >= 1 && random.nextDouble() < 0.7) lot.roof.chimney = 1 + random.nextInt(5);
        if (detail >= 2 && lot.w >= 13 && random.nextDouble() < 0.6) lot.roof.dormers = 1 + random.nextInt(detail >= 3 ? 3 : 2);
        if (main && detail >= 1 && random.nextDouble() < 0.45) lot.porch = true;
        if (detail >= 2 && lot.floors.size() >= 2 && random.nextDouble() < 0.3) {
            int k = lot.floors.size() - 1;
            lot.floors.get(k).balcony[2] = true;
            lot.floors.get(k).door = 2;
        }
        if (detail >= 3 && lot.floors.size() >= 2 && random.nextDouble() < 0.35) {
            int k = 1;
            lot.floors.get(k).door = 0;
            lot.floors.get(k).stairs = true;
        }
    }

    /** The look drawn for a building: its walls' sets, its plaster, its windows, its roof, its jetties; each lot takes it with a little of its own. */
    static final class Look {
        String ground;
        String upper;
        int color;
        Plan.Windows windows;
        Plan.RoofType roof;
        int pitch;
        int overhang;
        boolean jetty;
        int height;

        static Look draw(Random random) {
            Look look = new Look();
            String[] grounds = {"stone", "stone", "plaster", "timber"};
            String[] uppers = {"timber", "timber", "cross", "brace", "plaster"};
            look.ground = grounds[random.nextInt(grounds.length)];
            look.upper = uppers[random.nextInt(uppers.length)];
            look.color = random.nextInt(3);
            Plan.Windows[] windows = {Plan.Windows.ALTERNATE, Plan.Windows.ALTERNATE, Plan.Windows.ALL, Plan.Windows.CENTER, Plan.Windows.ENDS};
            look.windows = windows[random.nextInt(windows.length)];
            Plan.RoofType[] roofs = {Plan.RoofType.GABLE, Plan.RoofType.GABLE, Plan.RoofType.HIP, Plan.RoofType.HALF_HIP};
            look.roof = roofs[random.nextInt(roofs.length)];
            look.pitch = random.nextDouble() < 0.7 ? 1 : 2;
            look.overhang = random.nextDouble() < 0.7 ? 1 : 2;
            look.jetty = random.nextDouble() < 0.55;
            look.height = 5 + (random.nextDouble() < 0.3 ? 1 : 0);
            return look;
        }

        void apply(Plan.Lot lot, Random random) {
            for (int k = 0; k < lot.floors.size(); k++) {
                Plan.Floor floor = lot.floors.get(k);
                floor.panels = k == 0 && lot.floors.size() > 1 ? ground : upper;
                floor.color = color;
                floor.windows = windows;
                floor.height = k == 0 ? height : 5;
                if (k > 0 && jetty && lot.shape == Plan.Shape.RECT) {
                    floor.jetty[2] = k == 1 ? 1 : 0;
                    floor.jetty[0] = k == 1 && random.nextBoolean() ? 1 : 0;
                }
            }
            lot.roof.type = lot.shape == Plan.Shape.RECT ? roof : Plan.RoofType.CONE;
            lot.roof.pitch = pitch;
            java.util.Arrays.fill(lot.roof.overhang, overhang);
        }
    }

    // ---- making and keeping the best ------------------------------------------------------------------------------------------------

    /** What the maker made: the plan kept, the building of it, which pattern, how many it tried. */
    public record Design(Plan plan, PlotGenerator.Result result, Pattern pattern, int tries) {
    }

    /**
     * Makes {@code candidates} buildings of the pattern (null: any), each checked against the balance rules and made again with one thing changed while it
     * fails (at most {@code retries} times), and keeps the one that scores best.
     */
    public static Design design(Pattern pattern, Rules rules, Kit kit, long seed, int candidates, int retries, Ground ground) {
        Random random = new Random(seed);
        Design best = null;
        int tries = 0;
        for (int c = 0; c < Math.max(1, candidates); c++) {
            Pattern chosen = pattern != null ? pattern : Pattern.values()[random.nextInt(Pattern.values().length)];
            Plan plan = make(chosen, rules, random);
            PlotGenerator.Result result = null;
            for (int attempt = 0; attempt <= Math.max(0, retries); attempt++) {
                tries++;
                List<String> broken = balance(plan);
                result = PlotGenerator.build(plan, kit, random.nextLong(), ground);
                if (broken.isEmpty() && result.problems().isEmpty()) break;
                if (attempt < retries) mend(plan, broken, random);
            }
            double score = result.score() - 0.5 * balance(plan).size();
            if (best == null || score > best.result().score()) {
                best = new Design(plan, new PlotGenerator.Result(result.grid(), result.layout(), result.problems(), result.notes(), score, chosen.label, tries), chosen, tries);
            }
        }
        return best == null ? null : new Design(best.plan(), new PlotGenerator.Result(best.result().grid(), best.result().layout(), best.result().problems(),
                best.result().notes(), best.result().score(), best.pattern().label, tries), best.pattern(), tries);
    }

    /**
     * The balance rules (spec 6/6) a plan breaks: the main block the biggest (a tower aside); each other lot lower than the main block or slender and taller;
     * the eaves of lots next to each other one block apart.
     */
    public static List<String> balance(Plan plan) {
        List<String> out = new ArrayList<>();
        Layout layout = Layout.of(plan);
        if (layout.lots.isEmpty()) return out;
        Layout.LotGeo main = layout.lots.get(0);
        int mainArea = main.base.cells().size();
        for (Layout.LotGeo geo : layout.lots) {
            if (geo == main) continue;
            boolean tower = geo.lot.name.startsWith("tower") || geo.lot.shape != Plan.Shape.RECT;
            int area = geo.base.cells().size();
            if (!tower && area > mainArea) out.add(geo.lot.name + " が本棟より大きい");
            boolean lower = geo.floors() < main.floors();
            boolean slender = Math.max(geo.base.width(), geo.base.depthSpan()) <= Math.min(main.base.width(), main.base.depthSpan()) && geo.eave > main.eave;
            if (!lower && !slender && geo.eave != main.eave) out.add(geo.lot.name + " が低くも細くもない");
        }
        for (Layout.LotGeo a : layout.lots) {
            for (int b : layout.neighbours(a.index)) {
                if (b > a.index && Math.abs(a.eave - layout.lots.get(b).eave) == 1) out.add(a.lot.name + " と " + layout.lots.get(b).lot.name + " の軒が 1 段違い");
            }
        }
        // (two lots over each other more than by their shared wall line: one of them was pushed into the other)
        for (Layout.LotGeo a : layout.lots) {
            for (Layout.LotGeo b : layout.lots) {
                if (b.index <= a.index || a.base.shape != Plan.Shape.RECT || b.base.shape != Plan.Shape.RECT) continue;
                // (a tower stands over its block's corner on purpose)
                if (a.lot.name.startsWith("tower") || b.lot.name.startsWith("tower")) continue;
                int wide = Math.min(a.base.x1, b.base.x1) - Math.max(a.base.x0, b.base.x0) + 1;
                int deep = Math.min(a.base.z1, b.base.z1) - Math.max(a.base.z0, b.base.z0) + 1;
                if (wide > 1 && deep > 1) out.add(a.lot.name + " と " + b.lot.name + " が重なる");
            }
        }
        return out;
    }

    /** One thing changed toward the rules: a storey's height, a lot's storeys. */
    private static void mend(Plan plan, List<String> broken, Random random) {
        if (plan.lots.size() < 2) {
            // (the problems came from the parts: another draw of where they go)
            Plan.Lot main = plan.lots.get(0);
            main.roof.dormers = Math.max(0, main.roof.dormers - 1);
            if (random.nextBoolean()) main.roof.chimney = 0;
            return;
        }
        Plan.Lot lot = plan.lots.get(1 + random.nextInt(plan.lots.size() - 1));
        Plan.Lot main = plan.lots.get(0);
        if (lot.floors.size() >= main.floors.size() && lot.shape == Plan.Shape.RECT && lot.floors.size() > 1) {
            lot.floors.remove(lot.floors.size() - 1);
            return;
        }
        Plan.Floor top = lot.floors.get(lot.floors.size() - 1);
        top.height = top.height >= 6 ? top.height - 1 : top.height + 1;
    }
}
