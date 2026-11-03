package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.HouseModule.Type;
import net.tkgon.mc.iruuRPG.tools.procedural.HouseShape.Build;
import net.tkgon.mc.iruuRPG.tools.procedural.HouseShape.House;
import net.tkgon.mc.iruuRPG.tools.procedural.HouseShape.HousePlan;
import net.tkgon.mc.iruuRPG.tools.procedural.HouseShape.Side;
import net.tkgon.mc.iruuRPG.tools.procedural.ProceduralShapes.Blocks;
import net.tkgon.mc.iruuRPG.tools.procedural.TreeSettings.Key;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * The random houses of the procedural tool (t185): a house of several parts made by the rules of a pattern (a cottage, a timber townhouse, an L-shaped
 * farmhouse, a manor, a tower house; t190 a house in the style of the lobby (hub) builds, a house of volumes of several heights), not by chance alone. It only writes the numbers: the base and the list of parts of the settings (the roof and
 * the look too when asked), so the house is then the same as one set up by hand, every part can be moved, changed or taken away, and a house that is liked
 * is grown on from there.
 * <p>
 * How a house is made: the base from the pattern's ranges (a house that is the same both ways gets an odd width, the pillars counted from both corners and
 * the door in the middle); then the parts in the order they are built (wings, towers, turrets, the porch, bays, balconies, dormers, chimneys), each tried in
 * a few places and sizes. Every try is built for real on the house as it is so far, and is thrown away when it does not fit (a dormer on a slope that does
 * not rise from its eaves or that it would stick out over, a wing whose roof is over the house's ridge, a balcony with no storey under it, anything in the
 * way in at the door, a porch or a chimney that runs into the house) or when it covers more of the parts before it than {@link Key#HOUSE_GEN_OVERLAP}
 * allows; of the rest, the one that covers least is kept. A house that is the same both ways gets its parts in mirrored pairs. The whole is made
 * {@link #TRIES} times and the best is kept: the fewest blocks covered, the number of parts asked for, the parts the pattern is about (the wings of a
 * farmhouse, the tower of a tower house), the kinds of parts, a roofline of several heights, and small enough to be previewed. The same seed and settings
 * give the same house. Pure arithmetic: it runs off the main thread.
 */
final class HouseDesigner {

    private HouseDesigner() {
    }

    /** How many houses are made for one, the best kept. */
    static final int TRIES = 12;

    /** The patterns ({@link Key#HOUSE_GEN_PATTERN}): the rules of a house. */
    enum Pattern {
        ANY("any", "おまかせ", "下のどれかを、毎回ランダムに選ぶ"),
        COTTAGE("cottage", "コテージ", "小さな 1〜2 階の家。急な屋根、煙突、ドーマー窓、入口のポーチ、出窓"),
        TOWNHOUSE("townhouse", "木組みの町家", "間口が狭く背の高い家。正面に破風の尖った屋根、上の階の張り出しと木組み、上の階の出窓、横の屋根のドーマー窓"),
        FARM("farm", "L字の農家", "横長の本体に翼棟を足した L 字 (多いと U 字や T 字)。煙突、ドーマー窓、ポーチ"),
        MANOR("manor", "館", "幅の広い左右対称の屋敷。両端の翼棟、中央の入口とバルコニー、並んだドーマー窓、両端の煙突"),
        TOWER("tower", "塔の家", "小さめの本体の角に高い丸塔、別の角に小塔。尖った屋根、バルコニー"),
        HUB("hub", "ハブの家", "ロビー・スポーン (hub) の建築スタイルの家 (t190)。急で大きな屋根 (棟木・階の間の庇・屋根の上の四角い塔で屋根を重ねる)、石の1階と基壇、木組みと奥まった壁・窓、張り出した上の階、正面の破風の棟"),
        COMPOUND("compound", "組み合わせの家", "高さの違う棟を組み合わせた、家の形のひな型 (t190)。背の高い本体、低い横の棟、上の階から宙に張り出した棟、上の階が張り出す細い塔、外階段、急な屋根"),
        PLAN("plan", "複合プランの家", "長方形をいくつも組み合わせた平面 (L・T・U・十字・ずらし) の家 (t195)。棟ごとに違う形の屋根、棟ごとに高さの違う土台とテラス、背の高い塔の棟、内角の塔、片流れの下屋");

        /** The name in the command ({@code /proceduraltool random <id>}). */
        final String id;
        final String label;
        final String description;

        Pattern(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }

        static Pattern of(int index) {
            Pattern[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, index))];
        }

        static Pattern byId(String id) {
            for (Pattern pattern : values()) if (pattern.id.equalsIgnoreCase(id)) return pattern;
            return null;
        }
    }

    static String sizeName(int size) {
        return switch (size) {
            case 0 -> "小";
            case 2 -> "大";
            default -> "中";
        };
    }

    static String symmetryName(int symmetry) {
        return switch (symmetry) {
            case 1 -> "左右対称";
            case 2 -> "崩す";
            default -> "パターン次第";
        };
    }

    static String towersName(int towers) {
        return switch (towers) {
            case 1 -> "なし";
            case 2 -> "少し";
            case 3 -> "多め";
            default -> "パターン次第";
        };
    }

    static String overlapName(int overlap) {
        return switch (overlap) {
            case 0 -> "なし";
            case 2 -> "多め";
            default -> "少し";
        };
    }

    /** A house made: its settings, the pattern it was made by, its seed, and how many blocks of its parts cover others'. */
    record Result(TreeSettings settings, Pattern pattern, long seed, int conflicts) {
    }

    /** About how many parts each step of {@link Key#HOUSE_GEN_DETAIL} asks for (besides the parts the pattern is about). */
    private static final int[] TARGET = {2, 4, 7, 10};

    /**
     * Makes a house from {@code from} (its pattern and rules; the rest of it, the blocks, the turn, the foundation, is kept) and {@code seed}. With
     * {@code partsOnly} the base stays as it is and only the parts are made again.
     */
    static Result design(TreeSettings from, long seed, boolean partsOnly) {
        return design(from, seed, partsOnly, TRIES);
    }

    /** {@link #design(TreeSettings, long, boolean)} with {@code tries} houses made for one (fewer for the many houses of a lobby). */
    static Result design(TreeSettings from, long seed, boolean partsOnly, int tries) {
        Random root = new Random(seed);
        Pattern chosen = Pattern.of(from.getInt(Key.HOUSE_GEN_PATTERN));
        Pattern pattern = chosen == Pattern.ANY ? Pattern.values()[1 + root.nextInt(Pattern.values().length - 1)] : chosen;
        Maker best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < Math.max(1, tries); attempt++) {
            Maker maker = new Maker(from, pattern, new Random(root.nextLong()), partsOnly);
            maker.make();
            double score = maker.score();
            if (score > bestScore) {
                bestScore = score;
                best = maker;
            }
        }
        best.s.setDesignSeed(seed);
        return new Result(best.s, pattern, seed, best.plan.conflicts);
    }

    // ---- one house -------------------------------------------------------------------------------------------------------------------

    /** The steps of the roofs of steps a house may be given, by how often. */
    private static final int[] STEP_WEIGHTS = {40, 20, 15, 10, 5, 10};

    /** One house being made by the rules of its pattern. */
    private static final class Maker {
        final TreeSettings s;
        final Pattern pattern;
        final Random r;
        final boolean partsOnly;
        final int size;
        final int detail;
        final int towers;
        final int overlap;
        final boolean roof;
        final boolean look;
        /** (t193) the shape (the size, the storeys, the pillars) chosen too; off: as it is. */
        final boolean shape;
        final boolean symmetric;
        /** The kind of plan of a house of several blocks (-1: not one). */
        final int planType;
        /** The blocks of the plan put so far (where their inner corners are). */
        final List<HouseModule> blocks = new ArrayList<>();
        final int target;
        Plan plan;
        /** Parts put that the pattern is not about (counted against {@link #target}); the pattern's own parts that found no place. */
        int optional;
        int missed;

        Maker(TreeSettings from, Pattern pattern, Random r, boolean partsOnly) {
            this.s = from.copy();
            if (s.kind() != ProcKind.HOUSE) s.setKind(ProcKind.HOUSE);
            s.modules().clear();
            this.pattern = pattern;
            this.r = r;
            this.partsOnly = partsOnly;
            size = s.getInt(Key.HOUSE_GEN_SIZE);
            detail = s.getInt(Key.HOUSE_GEN_DETAIL);
            towers = s.getInt(Key.HOUSE_GEN_TOWERS);
            overlap = s.getInt(Key.HOUSE_GEN_OVERLAP);
            roof = s.on(Key.HOUSE_GEN_ROOF);
            look = s.on(Key.HOUSE_GEN_LOOK);
            shape = s.on(Key.HOUSE_GEN_SHAPE);
            int symmetry = s.getInt(Key.HOUSE_GEN_SYMMETRY);
            // (t195: the plan of a house of several blocks: 0 L, 1 T, 2 U, 3 a cross, 4 staggered)
            planType = pattern == Pattern.PLAN ? new int[]{0, 0, 0, 1, 1, 2, 2, 3, 3, 4}[r.nextInt(10)] : -1;
            symmetric = symmetry == 1 || symmetry == 0 && switch (pattern) {
                case MANOR -> true;
                case TOWNHOUSE, HUB -> r.nextDouble() < 0.5;
                case COTTAGE -> r.nextDouble() < 0.2;
                case PLAN -> (planType == 1 || planType == 2 || planType == 3) && r.nextDouble() < 0.6;
                default -> false;
            };
            target = TARGET[Math.max(0, Math.min(TARGET.length - 1, detail))];
        }

        void make() {
            if (!partsOnly) base();
            plan = new Plan(s, overlap);
            switch (pattern) {
                case TOWNHOUSE -> townhouse();
                case FARM -> farm();
                case MANOR -> manor();
                case TOWER -> towerHouse();
                case HUB -> hub();
                case COMPOUND -> compound();
                case PLAN -> plan();
                default -> cottage();
            }
        }

        // ---- the base -------------------------------------------------------------------------------------------------------------

        private void base() {
            int spacing;
            int width;
            int depth;
            int floors;
            int storey = 4;
            switch (pattern) {
                case TOWNHOUSE -> {
                    spacing = 3 + r.nextInt(2);
                    floors = new int[]{2, 3, 3}[size] + (size == 2 && r.nextBoolean() ? 1 : 0);
                    width = width(7, 9 + (size == 2 ? 2 : 0), spacing);
                    depth = range(9 + 2 * size, 11 + 2 * size);
                }
                case FARM -> {
                    spacing = 4;
                    floors = size == 2 ? 2 : 1 + (r.nextDouble() < 0.4 ? 1 : 0);
                    width = width(11 + 2 * size, 13 + 2 * size, spacing);
                    depth = range(7 + size / 2, 8 + size);
                }
                case MANOR -> {
                    spacing = 4;
                    floors = size == 2 ? 3 : 2;
                    storey = r.nextDouble() < 0.4 ? 5 : 4;
                    width = width(15 + 4 * size, 17 + 4 * size, spacing);
                    depth = range(9 + 2 * size, 10 + 2 * size);
                }
                case TOWER -> {
                    spacing = 3 + r.nextInt(2);
                    floors = size == 2 ? 3 : 2;
                    width = width(7 + 2 * size, 8 + 2 * size, spacing);
                    depth = range(7 + size, 8 + size);
                }
                case HUB -> {
                    spacing = 4;
                    floors = size == 0 ? 2 : 2 + r.nextInt(2);
                    storey = 5;
                    width = width(9 + 2 * size, 11 + 2 * size, spacing);
                    depth = range(8 + size, 10 + size);
                }
                case PLAN -> {
                    spacing = 3 + r.nextInt(2);
                    floors = size == 0 ? 1 + r.nextInt(2) : size == 1 ? 2 : 2 + r.nextInt(2);
                    storey = r.nextDouble() < 0.4 ? 5 : 4;
                    width = width(9 + 2 * size, 11 + 2 * size, spacing);
                    depth = range(7 + size, 9 + size);
                }
                case COMPOUND -> {
                    spacing = 3 + r.nextInt(2);
                    floors = 2 + (size >= 1 ? r.nextInt(2) : 0) + (size == 2 ? 1 : 0);
                    storey = r.nextDouble() < 0.3 ? 5 : 4;
                    width = width(7 + size, 9 + size, spacing);
                    depth = range(7 + size, 9 + size);
                }
                default -> {
                    spacing = 3 + r.nextInt(2);
                    floors = size == 0 ? 1 : size == 1 ? 1 + r.nextInt(2) : 2;
                    storey = r.nextDouble() < 0.2 ? 5 : 4;
                    width = width(7 + 2 * size, 9 + 2 * size, spacing);
                    depth = range(6 + size, 8 + size);
                }
            }
            // (t193: the shape kept as it is when it is not to be chosen; odd both ways, so every roof has a ridge of one block)
            if (shape) {
                s.set(Key.HOUSE_WIDTH, odd(width));
                s.set(Key.HOUSE_DEPTH, odd(depth));
                s.set(Key.HOUSE_FLOORS, floors);
                s.set(Key.HOUSE_STOREY, storey);
                s.set(Key.HOUSE_PILLARS, spacing);
                s.set(Key.HOUSE_PILLAR_MIRROR, 1);
                s.set(Key.HOUSE_WINDOWS, 1);
                s.set(Key.HOUSE_DOOR, 1);
            }
            if (roof) roof();
            if (look) look(s.getInt(Key.HOUSE_FLOORS));
        }

        /** {@code n} made odd (one more or one less, at least 5). */
        private int odd(int n) {
            if (n % 2 == 1) return n;
            return n <= 5 || r.nextBoolean() ? n + 1 : n - 1;
        }

        /** An odd size from {@code min} to {@code max} (at least 3): a tower or a volume with a point or a ridge of one block on top. */
        private int oddSize(int min, int max) {
            int low = Math.max(3, min) | 1;
            int high = Math.max(low, max);
            if (high % 2 == 0) high--;
            return low + 2 * r.nextInt((high - low) / 2 + 1);
        }

        /** A width in the range; a house the same both ways needs an odd one whose middle is not a pillar (so the door is in the middle). */
        private int width(int min, int max, int spacing) {
            if (!symmetric) return range(min, max);
            List<Integer> fits = new ArrayList<>();
            for (int grow = 0; fits.isEmpty() && grow <= 8; grow += 2) {
                for (int w = Math.max(5, min - grow); w <= Math.min(40, max + grow); w++) {
                    if (w % 2 == 1 && (spacing <= 0 || (w - 1) / 2 % spacing != 0)) fits.add(w);
                }
            }
            return fits.isEmpty() ? (min | 1) : fits.get(r.nextInt(fits.size()));
        }

        private int range(int min, int max) {
            return max <= min ? min : min + r.nextInt(max - min + 1);
        }

        private boolean chance(double p) {
            return r.nextDouble() < p;
        }

        /** One of {@code values} by its weight ({@code weights}). */
        private int pick(int[] values, int[] weights) {
            int total = 0;
            for (int weight : weights) total += weight;
            int roll = r.nextInt(total);
            for (int index = 0; index < values.length; index++) {
                roll -= weights[index];
                if (roll < 0) return values[index];
            }
            return values[0];
        }

        private void roof() {
            int type = switch (pattern) {
                case TOWNHOUSE -> pick(new int[]{9, 1, 4, 7}, new int[]{45, 35, 10, 10});
                case FARM -> pick(new int[]{1, 9, 7, 4, 6}, new int[]{30, 25, 15, 15, 15});
                case MANOR -> pick(new int[]{10, 2, 6, 5, 9}, new int[]{30, 20, 20, 15, 15});
                case TOWER -> pick(new int[]{9, 8, 1, 7}, new int[]{40, 25, 20, 15});
                case HUB -> pick(new int[]{9, 4, 1, 7, 6}, new int[]{30, 25, 25, 10, 10});
                case COMPOUND -> pick(new int[]{9, 1, 7}, new int[]{50, 35, 15});
                case PLAN -> pick(new int[]{1, 9, 7, 4, 6}, new int[]{35, 30, 15, 10, 10});
                default -> pick(new int[]{9, 1, 8, 7, 6}, new int[]{35, 25, 15, 15, 10});
            };
            boolean steep = pattern == Pattern.HUB || pattern == Pattern.COMPOUND;
            double pitch = switch (type) {
                case 4, 5 -> 0.6 + 0.3 * r.nextDouble();
                case 8 -> 0.8 + 0.3 * r.nextDouble();
                default -> steep ? 1.2 + 0.4 * r.nextDouble() : 0.9 + 0.5 * r.nextDouble();
            };
            s.set(Key.HOUSE_ROOF, type);
            s.set(Key.HOUSE_PITCH, Math.round(pitch * 20.0) / 20.0);
            s.set(Key.HOUSE_OVERHANG, pattern == Pattern.HUB && chance(0.4) ? 2 : 1);
            s.set(Key.HOUSE_RIDGE, switch (pattern) {
                case TOWNHOUSE -> chance(0.8) ? 2 : 1;
                case TOWER, COTTAGE -> chance(0.8) ? 1 : 0;
                case HUB -> chance(0.6) ? 1 : 2;
                case COMPOUND -> chance(0.5) ? 2 : 1;
                default -> 1;
            });
            if (type == 9 || type == 10) {
                int[] presets = new int[HouseShape.STEP_PRESETS.size()];
                for (int index = 0; index < presets.length; index++) presets[index] = index;
                s.setRoofSteps(HouseShape.STEP_PRESETS.get(pick(presets, STEP_WEIGHTS)).steps());
                s.set(Key.HOUSE_STEP_FIT, 1);
            }
        }

        /**
         * The look: how likely each is for the pattern, in the order stone ground storey, timber frame, jetty, jetty on all four sides, walls set back, glass,
         * plinth, roofs between the storeys, ridge beam (t190).
         */
        private static final Map<Pattern, double[]> LOOKS = Map.of(
                Pattern.COTTAGE, new double[]{0.5, 0.35, 0.2, 0.2, 0.3, 0.5, 0.3, 0.2, 0.3},
                Pattern.TOWNHOUSE, new double[]{0.7, 0.9, 0.8, 0.3, 0.5, 0.6, 0.3, 0.2, 0.3},
                Pattern.FARM, new double[]{0.4, 0.4, 0.0, 0.0, 0.2, 0.4, 0.3, 0.1, 0.2},
                Pattern.MANOR, new double[]{0.7, 0.3, 0.0, 0.0, 0.3, 0.6, 0.6, 0.3, 0.2},
                Pattern.TOWER, new double[]{0.6, 0.5, 0.4, 0.3, 0.4, 0.5, 0.5, 0.2, 0.3},
                Pattern.HUB, new double[]{0.9, 0.85, 0.5, 0.4, 0.7, 0.8, 0.75, 0.65, 0.75},
                Pattern.COMPOUND, new double[]{0.8, 0.85, 0.6, 0.5, 0.6, 0.7, 0.5, 0.3, 0.4},
                Pattern.PLAN, new double[]{0.7, 0.7, 0.4, 0.3, 0.5, 0.6, 0.2, 0.3, 0.4});

        private void look(int floors) {
            double[] odds = LOOKS.getOrDefault(pattern, LOOKS.get(Pattern.COTTAGE));
            boolean jetty = floors >= 2 && chance(odds[2]);
            s.set(Key.HOUSE_STONE, chance(odds[0]) ? 1 : 0);
            s.set(Key.HOUSE_TIMBER, chance(odds[1]) ? 1 : 0);
            s.set(Key.HOUSE_JETTY, jetty ? 1 : 0);
            s.set(Key.HOUSE_JETTY_SIDES, jetty && chance(odds[3]) ? 1 : 0);
            s.set(Key.HOUSE_BRACKETS, jetty || chance(0.5) ? 1 : 0);
            s.set(Key.HOUSE_RECESS, chance(odds[4]) ? 1 : 0);
            s.set(Key.HOUSE_GLASS, chance(odds[5]) ? 1 : 0);
            s.set(Key.HOUSE_PLINTH, chance(odds[6]) ? (chance(0.3) ? 2 : 1) : 0);
            s.set(Key.HOUSE_SKIRT, floors >= 2 && chance(odds[7]) ? (chance(0.25) ? 2 : 1) : 0);
            s.set(Key.HOUSE_RIDGE_BEAM, chance(odds[8]) ? 1 : 0);
            // (t195) the podium and its terrace
            double podium = switch (pattern) {
                case PLAN -> 0.75;
                case MANOR -> 0.5;
                case HUB -> 0.4;
                case COMPOUND, TOWER -> 0.3;
                default -> 0.15;
            };
            int raise = chance(podium) ? range(1, 3) : 0;
            s.set(Key.HOUSE_RAISE, raise);
            s.set(Key.HOUSE_TERRACE, raise > 0 ? range(pattern == Pattern.PLAN ? 1 : 0, 2) : 0);
        }

        // ---- the patterns ---------------------------------------------------------------------------------------------------------

        private void cottage() {
            int many = towerCount(0);
            if (many > 0) cornerTowers(many, false, true);
            if (detail >= 1 && chance(0.65)) porch(false);
            if (detail >= 2 && chance(0.5)) bays(0, 1);
            if (detail >= 3 && chance(0.5)) leanTo();
            if (detail >= 1) dormers(symmetric ? 2 : 1 + r.nextInt(2), false);
            if (chance(0.85)) chimneys(1, false);
            if (detail >= 3 && chance(0.3)) chimneys(1, false);
        }

        private void townhouse() {
            int many = towerCount(0);
            if (detail >= 3 && chance(0.4)) wing(2, 0, plan.floors - 1, false);
            if (many > 0) cornerTowers(many, false, true);
            if (detail >= 1) {
                for (int level = 1; level < plan.floors; level++) {
                    if (level == 1 || chance(0.6)) bay(0, level, 0, 3 + (plan.width >= 9 && chance(0.4) ? 2 : 0), level == 1);
                }
            }
            if (detail >= 2 && chance(0.4)) porch(false);
            if (detail >= 2 && chance(0.4)) balcony(0, plan.floors - 1, false);
            if (detail >= 1) dormers(symmetric ? 2 : 1 + r.nextInt(2), false);
            if (chance(0.8)) chimneys(1, false);
        }

        private void farm() {
            // the wing that makes the L (the pattern's own part)
            int side = pick(new int[]{1, 3, 2}, new int[]{40, 40, 20});
            wing(side, chance(0.5) ? -1 : 1, plan.floors == 1 || chance(0.5) ? plan.floors : plan.floors - 1, true);
            if (detail >= 3 && chance(0.35)) wing(side == 2 ? (chance(0.5) ? 1 : 3) : 4 - side, chance(0.5) ? -1 : 1, Math.max(1, plan.floors - 1), false);
            int many = towerCount(detail >= 3 && chance(0.3) ? 1 : 0);
            if (many > 0) cornerTowers(many, false, true);
            if (detail >= 1 && chance(0.6)) porch(false);
            if (detail >= 2 && chance(0.4)) bays(0, 1);
            if (detail >= 1) dormers(1 + r.nextInt(2), false);
            if (chance(0.85)) chimneys(1, false);
        }

        private void manor() {
            // the wings at both ends: forward (a U toward the front) or out to the sides
            if (chance(0.55)) {
                wingPair(0, w -> endAlong(0, w, false), range(5, 7) | 1, range(3, 5 + size), plan.floors, true);
            } else {
                wingPair(1, w -> 0, Math.max(5, plan.depth - 2 - 2 * r.nextInt(2)) | 1, range(4, 6 + size), Math.max(1, plan.floors - (chance(0.5) ? 1 : 0)), true);
            }
            int many = towerCount(detail >= 3 ? 2 : 0);
            if (many > 0) cornerTowers(many, false, true);
            porch(true);
            if (detail >= 2 && chance(0.5)) balcony(0, 1, true);
            if (detail >= 2 && chance(0.5)) bays(0, 1);
            dormers(detail >= 2 ? 4 : 2, false);
            if (detail >= 3) dormers(2, true);
            chimneys(2, false);
        }

        private void towerHouse() {
            int many = towerCount(1 + (detail >= 1 && chance(0.6) ? 1 : 0));
            if (many > 0) {
                // the tower the house is named for, higher than the roof, and turrets at other corners
                int corner = r.nextInt(2) + (chance(0.25) ? 2 : 0);
                bigTower(corner, true);
                for (int more = 1; more < many; more++) turret((corner + 1 + r.nextInt(3)) % 4, false);
            }
            if (detail >= 2 && chance(0.5)) balcony(r.nextInt(4), plan.floors - 1, false);
            if (detail >= 2 && chance(0.4)) porch(false);
            if (detail >= 3 && chance(0.4)) bays(r.nextInt(4), 1);
            if (detail >= 1) dormers(1, false);
            if (chance(0.7)) chimneys(1, false);
        }

        private void hub() {
            // the gable to the front (a box as high as the house, its ridge out to the front: the pattern's own), the layers of roofs (a small square
            // tower on the roof, its top storey jutting out under a pyramid)
            frontGable(chance(0.75));
            int many = towerCount(1);
            if (many > 0) stackedTower();
            if (many > 1) cornerTowers(many - 1, false, false);
            if (detail >= 1 && chance(0.6)) porch(false);
            if (detail >= 1 && chance(0.5)) sideBlock(chance(0.5) ? 1 : 3, false);
            if (detail >= 2 && chance(0.5)) balcony(0, plan.floors - 1, symmetric);
            dormers(detail >= 2 ? 3 : 2, false);
            if (detail >= 3) dormers(2, true);
            chimneys(1, false);
        }

        private void compound() {
            // the blocks of other heights (the pattern's own): a lower one to a side, one jutting out from an upper storey over the ground
            int side = chance(0.5) ? 1 : 3;
            sideBlock(side, true);
            if (plan.floors >= 2) cantilever(true);
            int many = towerCount(1);
            if (many > 0) slimTower(true);
            if (many > 1) turret(2 + r.nextInt(2), false);
            if (detail >= 1) stairs(false);
            if (detail >= 2 && chance(0.5)) porch(false);
            if (detail >= 2 && chance(0.5)) balcony(r.nextInt(4), plan.floors - 1, false);
            if (detail >= 1) dormers(1 + r.nextInt(2), false);
            chimneys(detail >= 2 ? 2 : 1, false);
        }

        private void plan() {
            // the blocks of the plan (the pattern's own): big volumes, each its own roof, each on a foundation of its own height
            switch (planType) {
                case 1 -> {
                    int side = chance(0.7) ? 2 : 0;
                    block(side, w -> 0, true);
                }
                case 2 -> {
                    int side = chance(0.5) ? 0 : 2;
                    if (symmetric) {
                        block(side, w -> endAlong(side, w, false), true);
                    } else {
                        block(side, w -> endAlong(side, w, false), true);
                        block(side, w -> endAlong(side, w, true), true);
                    }
                }
                case 3 -> {
                    if (symmetric) {
                        block(1, w -> 0, true);
                    } else {
                        block(1, w -> 0, true);
                        block(3, w -> 0, true);
                    }
                }
                case 4 -> {
                    // (staggered: a block at an end of a side, deep, and one at the other end of the other side)
                    boolean front = chance(0.5);
                    block(1, w -> endAlong(1, w, front), true);
                    block(3, w -> endAlong(3, w, front), false);
                }
                default -> {
                    int side = chance(0.5) ? 1 : 3;
                    block(side, w -> endAlong(side, w, chance(0.5)), true);
                }
            }
            if (detail >= 1 && chance(0.55)) tallBlock();
            if (towerCount(detail >= 2 ? 1 : 0) > 0) jointTower();
            if (detail >= 1 && chance(0.5)) shed(false);
            if (detail >= 1 && chance(0.5)) porch(false);
            if (detail >= 1) dormers(symmetric ? 2 : 1 + r.nextInt(2), false);
            chimneys(detail >= 2 ? 2 : 1, false);
        }

        /** The roofs a block of a plan may have: other shapes than the house's (and than the blocks before it, while there are others). */
        private int blockRoof() {
            int[] shapes = {1, 2, 4, 6, 7, 8, 9, 10};
            int[] weights = {20, 15, 10, 10, 15, 10, 15, 10};
            Set<Integer> used = new HashSet<>();
            used.add(s.getInt(Key.HOUSE_ROOF));
            for (HouseModule block : blocks) used.add(block.roof < 0 ? s.getInt(Key.HOUSE_ROOF) : block.roof);
            for (int index = 0; index < shapes.length; index++) if (used.contains(shapes[index])) weights[index] = used.size() >= shapes.length ? weights[index] : 0;
            return pick(shapes, weights);
        }

        /**
         * A block of the plan on {@code side} at {@code along} (from its width): as deep as the house is about, its storeys the house's or one fewer, a roof
         * of its own (a gable turned out to the side: a cross gable), its floor on a foundation of its own height (up or down from the house's).
         */
        private void block(int side, IntUnaryOperator along, boolean own) {
            HouseModule put = place(own, 8, () -> {
                // (as wide as the house is deep at most: its cross gable then stays under the house's ridge; at least two thirds of that, an arm of the plan)
                int most = Math.max(5, Math.min(plan.length(side) - 2, plan.depth));
                int w = oddSize(Math.max(5, most * 2 / 3), most);
                int d = oddSize(5, 7 + 2 * size);
                int storeys = Math.max(1, plan.floors - (chance(0.4) ? 1 : 0));
                int shape = blockRoof();
                int ridge = shape == 2 || shape == 8 || shape == 10 || shape == 5 ? 0 : 2;
                HouseModule block = volume(side, along.applyAsInt(w), -1, w, d, storeys, 0, shape, ridge);
                block.raise = Math.max(-s.getInt(Key.HOUSE_RAISE), pick(new int[]{-2, -1, 0, 1, 2}, new int[]{15, 25, 25, 25, 10}));
                return group(block);
            });
            if (put != null) {
                blocks.add(put);
                if (symmetric) blocks.add(s.modules().get(s.modules().size() - 1));
            }
        }

        /** A tower house: a square block higher than the house (a storey or two), a hipped or curved roof (a pyramid on it), at a corner of the house. */
        private void tallBlock() {
            place(false, 6, () -> {
                int across = oddSize(5, Math.min(7, Math.min(plan.width, plan.depth) - 2));
                HouseModule block = plan.blockAtCorner(r.nextInt(4), across, plan.floors + 1 + (chance(0.4) ? 1 : 0));
                block.roof = pick(new int[]{2, 8, 10}, new int[]{40, 30, 30});
                block.raise = Math.max(-s.getInt(Key.HOUSE_RAISE), r.nextInt(2));
                plan.tall.add(block);
                return group(block);
            });
        }

        /** A round tower at an inner corner of the plan: where a block meets the house's wall. */
        private void jointTower() {
            if (blocks.isEmpty()) {
                cornerTowers(1, true, false);
                return;
            }
            // (a tower at the corner a block makes with the house stands in both: the blocks may be covered)
            Set<Integer> exempt = new HashSet<>();
            for (HouseModule block : blocks) exempt.add(s.modules().indexOf(block));
            place(false, 6, () -> {
                HouseModule block = blocks.get(r.nextInt(blocks.size()));
                int lo = -(Math.max(3, block.width) - 1) / 2;
                int u = block.along + (chance(0.5) ? lo - 1 : lo + Math.max(3, block.width));
                int size = oddSize(3, 5);
                HouseModule tower = new HouseModule(Type.TOWER);
                tower.side = block.side;
                tower.width = size;
                tower.along = u;
                tower.offset = 1 - (1 + size) / 2;
                tower.shape = pick(new int[]{0, 1}, new int[]{70, 30});
                tower.height = plan.towerHeight(tower, Math.max(2, (int) Math.round(size * (0.6 + 1.0 * r.nextDouble()))));
                return group(tower);
            }, exempt);
        }

        /** A lean-to: a one-storey block with a roof sloping away from the house, on a side with no block of the plan. */
        private void shed(boolean own) {
            place(own, 6, () -> {
                int side = r.nextInt(4);
                for (HouseModule block : blocks) if (block.side == side) return null;
                int w = oddSize(3, Math.min(7, plan.length(side) - 4));
                int along = range(plan.low(side) + w / 2 + 1, Math.max(plan.low(side) + w / 2 + 1, plan.high(side) - w / 2 - 1));
                return group(volume(side, along, 0, w, 3, 1, 0, 3, 0));
            });
        }

        /** A volume to the front as high as the house, its ridge out to the front (a cross gable), with its own door in front of the house's. */
        private void frontGable(boolean own) {
            place(own, 6, () -> {
                int w = 5 + 2 * r.nextInt(2);
                int d = 3 + 2 * r.nextInt(2);
                int in = range(1, d - 2);
                return List.of(volume(0, plan.doorU(), -in, w, d, plan.floors, 0, -1, 2));
            });
        }

        /** A small square tower in the middle of the roof, from the top storey up over the ridge, its top storey jutting out (a second layer of roof). */
        private void stackedTower() {
            place(false, 4, () -> {
                // (as big as the house allows; its top storey, the one that juts out, all over what is round it)
                HouseModule tower = plan.spire(oddSize(3, Math.min(plan.width, plan.depth) - 6), 1);
                tower.shape = 2;
                tower.jetty = 1;
                tower.height = plan.towerHeight(tower, plan.storey + 1 + r.nextInt(Math.max(1, tower.width / 2 + 1)));
                return List.of(tower);
            });
        }

        /** A volume lower than the house on {@code side}, at an end of it or in its middle (a mirrored pair on a house the same both ways). */
        private void sideBlock(int side, boolean own) {
            place(own, 8, () -> {
                int length = plan.length(side);
                int w = oddSize(5, Math.max(5, length - 2));
                int end = r.nextInt(3) - 1;
                int along = end == 0 ? 0 : endAlong(side, w, end < 0);
                int storeys = Math.max(1, plan.floors - 1 - (plan.floors >= 3 && chance(0.4) ? 1 : 0));
                int roofShape = chance(0.5) ? -1 : pick(new int[]{1, 9, 3}, new int[]{50, 35, 15});
                return group(volume(side, along, -range(0, 1), w, 3 + 2 * r.nextInt(2), storeys, 0, roofShape, r.nextInt(3)));
            });
        }

        /** A volume from an upper storey jutting out over the ground on brackets, its gable to the outside. */
        private void cantilever(boolean own) {
            place(own, 8, () -> {
                int side = r.nextInt(4);
                int level = range(1, plan.floors - 1);
                int w = 5 + 2 * r.nextInt(2);
                int along = range(plan.low(side) + w / 2 + 1, Math.max(plan.low(side) + w / 2 + 1, plan.high(side) - w / 2 - 1));
                return group(volume(side, along, -1, w, 3, Math.max(1, Math.min(2, plan.floors - level)), level, chance(0.5) ? -1 : 9, 2));
            });
        }

        /** A slender tower from the ground at a corner, over the ridge, its top storey jutting out. */
        private void slimTower(boolean own) {
            place(own, 6, () -> {
                HouseModule tower = plan.cornerTower(r.nextInt(4), oddSize(3, Math.min(plan.width, plan.depth) / 2), 0, 1, 1);
                tower.shape = pick(new int[]{0, 1, 2}, new int[]{50, 30, 20});
                tower.jetty = 1;
                tower.height = plan.towerHeight(tower, Math.max(3, (int) Math.round(tower.width * (0.8 + 1.2 * r.nextDouble()))));
                return group(tower);
            });
        }

        /** Stairs outside along a side up to the second storey: the flight where the side has room for it. */
        private void stairs(boolean own) {
            if (plan.floors < 2) return;
            place(own, 8, () -> {
                int side = r.nextInt(4);
                int length = plan.storey + 2;
                int up = chance(0.5) ? 1 : -1;
                int low = plan.low(side) + 1 + (up > 0 ? length : 1);
                int high = plan.high(side) - 1 - (up > 0 ? 1 : length);
                if (high < low) return null;
                HouseModule stair = part(Type.STAIR, side, range(low, high), 0, range(1, 2), 1, 0, 1);
                stair.shape = up > 0 ? 0 : 1;
                return group(stair);
            });
        }

        /** A volume: on {@code side}, {@code along} it, its near wall {@code offset} out from the wall, so big, from {@code level}, with its roof and ridge. */
        private static HouseModule volume(int side, int along, int offset, int width, int depth, int storeys, int level, int roof, int ridge) {
            HouseModule volume = part(Type.VOLUME, side, along, offset, width, depth, storeys, level);
            volume.roof = roof;
            volume.ridge = ridge;
            return volume;
        }

        /** The number of towers: as the pattern has it, none, a few (1 or 2), or many (4). */
        private int towerCount(int byPattern) {
            return switch (towers) {
                case 1 -> 0;
                case 2 -> Math.max(1, Math.min(2, byPattern));
                case 3 -> Math.max(4, byPattern);
                default -> byPattern;
            };
        }

        // ---- the parts --------------------------------------------------------------------------------------------------------------

        private static HouseModule part(Type type, int side, int along, int offset, int width, int depth, int height, int level) {
            HouseModule module = new HouseModule(type);
            module.side = side;
            module.along = along;
            module.offset = offset;
            module.width = width;
            module.depth = depth;
            module.height = height;
            module.level = level;
            return module;
        }

        /** The part and, on a house the same both ways, its mirror (once, when it is in the middle). */
        private List<HouseModule> group(HouseModule module) {
            List<HouseModule> group = new ArrayList<>();
            group.add(module);
            if (symmetric) {
                HouseModule mirror = plan.mirror(module);
                if (mirror.side != module.side || mirror.along != module.along) {
                    group.add(mirror);
                    if (plan.tall.contains(module)) plan.tall.add(mirror);
                }
            }
            return group;
        }

        /**
         * Puts the best of {@code tries} groups from {@code make} (null: no group this time); the pattern's own parts ({@code own}) are not counted
         * against the number asked for, and are missed when none fits. Returns the first part put, or null.
         */
        private HouseModule place(boolean own, int tries, Supplier<List<HouseModule>> make, Set<Integer> exempt) {
            Plan.Trial best = null;
            for (int attempt = 0; attempt < tries; attempt++) {
                List<HouseModule> group = make.get();
                if (group == null || group.isEmpty() || !room(group.size(), own)) continue;
                Plan.Trial trial = plan.trial(group, exempt);
                if (trial != null && (best == null || trial.hits() < best.hits())) best = trial;
                if (best != null && best.hits() == 0) break;
            }
            if (best == null) {
                if (own) missed++;
                return null;
            }
            plan.apply(best);
            if (!own) optional += best.group().size();
            return best.group().get(0);
        }

        private HouseModule place(boolean own, int tries, Supplier<List<HouseModule>> make) {
            return place(own, tries, make, Set.of());
        }

        private boolean room(int count, boolean own) {
            return s.modules().size() + count <= TreeSettings.MAX_MODULES && (own || optional + count <= target);
        }

        /** The {@code along} of a part {@code width} wide flush with the low end (u small) or the high end of {@code side}. */
        private int endAlong(int side, int width, boolean low) {
            int lo = -(width - 1) / 2;
            int hi = lo + width - 1;
            return low ? plan.low(side) - lo : plan.high(side) - hi;
        }

        /** A wing on {@code side}: at an end of it ({@code end} -1 the low end, 1 the high end) or in its middle (0). */
        private HouseModule wing(int side, int end, int storeys, boolean own) {
            return place(own, 8, () -> {
                int length = plan.length(side);
                int w = Math.max(5, Math.min(length - 2, range(5, Math.max(5, Math.min(9, length - 4)))));
                if (w % 2 == 0) w--;
                // (at an end it is flush with the corner: the house reads as an L; mirrored on a house the same both ways, a U)
                int along = end == 0 ? 0 : endAlong(side, w, end < 0);
                return group(part(Type.WING, side, along, 0, w, range(3, 5 + 2 * size), Math.max(1, storeys), 0));
            });
        }

        /** A wing and its mirror (on the other side, or the other end of the front), its place from its width ({@code along}). Returns the first, or null. */
        private HouseModule wingPair(int side, IntUnaryOperator along, int width, int depth, int storeys, boolean own) {
            int[] w = {width};
            return place(own, 8, () -> {
                HouseModule wing = part(Type.WING, side, along.applyAsInt(w[0]), 0, w[0], depth, storeys, 0);
                // (each try a little narrower, so a wing too high for the roof gets lower)
                w[0] = Math.max(5, w[0] - 2);
                List<HouseModule> group = new ArrayList<>(List.of(wing));
                HouseModule mirror = plan.mirror(wing);
                if (mirror.side != wing.side || mirror.along != wing.along) group.add(mirror);
                return group;
            });
        }

        /** A small wing of one storey to a side, its roof low against the house (a lean-to). */
        private void leanTo() {
            int side = chance(0.5) ? 1 : 3;
            wing(side, chance(0.5) ? -1 : 1, 1, false);
        }

        /** Towers at the corners ({@code count} of them, the front first): full towers ({@code big}) or turrets. */
        private void cornerTowers(int count, boolean big, boolean own) {
            int[] order = symmetric ? new int[]{0, 3} : new int[]{r.nextInt(2), 2 + r.nextInt(2)};
            int placed = 0;
            for (int corner : order) {
                if (placed >= count) break;
                HouseModule tower = big ? bigTower(corner, own) : turret(corner, own);
                if (tower != null) placed += symmetric ? 2 : 1;
            }
        }

        /** A round tower from the ground at {@code corner} (0 front left, 1 front right, 2 back right, 3 back left), above the ridge. */
        private HouseModule bigTower(int corner, boolean own) {
            return place(own, 6, () -> {
                // (as wide as a third to two thirds of the house; over what is round it by half to one and a half of its width)
                int across = Math.min(plan.width, plan.depth);
                HouseModule tower = plan.cornerTower(corner, oddSize(across / 3 + 1, across * 2 / 3), 0, 1, 1);
                tower.height = plan.towerHeight(tower, Math.max(2, (int) Math.round(tower.width * (0.5 + 1.1 * r.nextDouble()))));
                return group(tower);
            });
        }

        /** A turret on a corbel at {@code corner}, from the top storey (from the second when the house is tall). */
        private HouseModule turret(int corner, boolean own) {
            return place(own, 6, () -> {
                int level = Math.max(1, plan.floors - 1 - (plan.floors >= 3 && chance(0.5) ? 1 : 0));
                HouseModule tower = plan.cornerTower(corner, oddSize(3, Math.min(plan.width, plan.depth) / 3), level, 1, r.nextInt(2));
                tower.height = plan.towerHeight(tower, 1 + r.nextInt(tower.width / 2 + 2));
                return group(tower);
            });
        }

        /** A porch at the door (the middle of the front). */
        private void porch(boolean own) {
            place(own, 6, () -> {
                int w = 3 + 2 * r.nextInt(size >= 1 ? 3 : 2);
                // (under a jettied storey the roof must stay low: a shallow porch)
                int d = s.getInt(Key.HOUSE_JETTY) > 0 ? range(1, 2) : range(2, 3);
                return List.of(part(Type.PORCH, 0, plan.doorU(), 0, w, d, Math.max(3, plan.storey - 1), 0));
            });
        }

        /** {@code count} bay windows on {@code side} on the ground (on a house the same both ways, in pairs). */
        private void bays(int side, int count) {
            for (int n = 0; n < count; n++) bay(side, 0, Integer.MIN_VALUE, 3, false);
        }

        /** A bay window on {@code side} at {@code level}: in the middle of a stretch of wall between pillars ({@code along}: that one, or one at random). */
        private void bay(int side, int level, int along, int width, boolean own) {
            place(own, 6, () -> {
                List<Integer> windows = plan.windows(side, level);
                int at = along != Integer.MIN_VALUE ? along : windows.isEmpty() ? range(plan.low(side) + 2, plan.high(side) - 2) : windows.get(r.nextInt(windows.size()));
                return group(part(Type.BAY, side, at, 0, width, 1, Math.max(2, plan.storey - 2), level));
            });
        }

        /** A balcony on {@code side} at {@code level} (from the floor of that storey): in the middle ({@code middle}) or at a window. */
        private void balcony(int side, int level, boolean middle) {
            if (level < 1 || level >= plan.floors) return;
            place(false, 6, () -> {
                List<Integer> windows = plan.windows(side, level);
                int at = middle || windows.isEmpty() ? (side == 0 ? plan.doorU() : 0) : windows.get(r.nextInt(windows.size()));
                return group(part(Type.BALCONY, side, at, 0, 3 + 2 * r.nextInt(2), range(1, 2), 1, level));
            });
        }

        /** Dormers on the slopes of the roof: {@code count} on the front (or the side the roof slopes down to), or on the back ({@code back}). */
        private void dormers(int count, boolean back) {
            List<Integer> sides = plan.slopes();
            if (sides.isEmpty()) return;
            int side = back ? (sides.contains(2) ? 2 : sides.contains(3) ? 3 : -1) : sides.get(sides.get(0) == 1 && !symmetric && chance(0.5) ? 1 : 0);
            if (side < 0) return;
            int w = 3 + (plan.length(side) >= 15 && chance(0.3) ? 2 : 0);
            int h = range(2, 3);
            int d = range(2, 3);
            int usable = plan.length(side) - 4;
            int fit = Math.max(1, usable / (w + 2));
            int n = Math.min(count, fit);
            if (symmetric) {
                // in mirrored pairs, evenly along the side (one in the middle when they are odd)
                double step = usable / (double) n;
                for (int i = 0; i < (n + 1) / 2; i++) {
                    int along = (int) (Math.signum((i - (n - 1) / 2.0)) * Math.round(Math.abs((i - (n - 1) / 2.0) * step)));
                    place(false, 3, () -> group(part(Type.DORMER, side, along, 0, w, d, h, 0)));
                }
                return;
            }
            for (int i = 0; i < n; i++) {
                place(false, 6, () -> List.of(part(Type.DORMER, side, range(plan.low(side) + 2, plan.high(side) - 2), 0, w, d, h, 0)));
            }
        }

        /**
         * {@code count} chimneys (a mirrored pair on a house the same both ways): outside a gable end, or up through the roof near the back (a hipped roof
         * has no gable).
         */
        private void chimneys(int count, boolean own) {
            for (int n = 0; n < count; n += symmetric ? 2 : 1) {
                place(own, 6, () -> {
                    List<Integer> gables = plan.gables();
                    int w = range(2, 3);
                    int d = range(1, 2);
                    int h = range(2, 3);
                    if (!gables.isEmpty() && chance(0.65)) {
                        int side = gables.get(r.nextInt(gables.size()));
                        // (on a side that is its own mirror, a house the same both ways has one chimney, in the middle)
                        if (symmetric && plan.mirrors(side)) return group(part(Type.CHIMNEY, side, 0, 0, w | 1, d, h, 0));
                        return group(part(Type.CHIMNEY, side, r.nextInt(5) - 2, 0, w, d, h, 0));
                    }
                    int quarter = Math.max(1, plan.length(2) / 4);
                    int along = (chance(0.5) ? 1 : -1) * range(Math.max(1, quarter - 1), quarter + 1);
                    return group(part(Type.CHIMNEY, 2, along, -d - range(1, 3), w, d, h, 0));
                });
            }
        }

        // ---- how good it is -------------------------------------------------------------------------------------------------------

        /**
         * The higher the better: the fewest blocks covered between parts, as many parts as asked for, the pattern's own parts all there, several kinds of
         * part, a roofline of more than one height (towers over the ridge, wings under it); a house too big to preview or to read the ground under is out.
         */
        double score() {
            double score = -plan.conflicts * 1.5 - Math.abs(optional - target) * 3.0 - missed * 25.0;
            Set<Type> kinds = EnumSet.noneOf(Type.class);
            for (HouseModule module : s.modules()) kinds.add(module.type);
            score += kinds.size();
            score += Math.min(3, plan.peaks()) * 1.5;
            if (plan.local.map.size() > 30000) score -= 100.0;
            if (plan.radius() > 30) score -= 20.0;
            return score;
        }
    }

    // ---- the house as it is made -----------------------------------------------------------------------------------------------------

    /**
     * The house being made: its base and the parts put on so far, with which part each block is of (to tell how much a part tried would cover of the
     * others), the way in at the door that nothing may block, and the geometry the rules ask about (the ends of the sides, the slopes, the gables, the
     * windows).
     */
    static final class Plan {
        final TreeSettings s;
        final House house;
        final Blocks local = new Blocks();
        final int width;
        final int depth;
        final int floors;
        final int storey;
        /** The highest block of the base (the ridge). */
        final int ridge;
        private final int midA;
        private final int midB;
        private final int tolerance;
        private Map<Long, Integer> tops;
        /** The part each block is of (its place in the list); the base's blocks are not in it. */
        private final Map<Long, Integer> owner = new HashMap<>();
        /** The door and the three blocks in front of it, 2 high: kept clear. */
        private final Set<Long> door = new HashSet<>();
        /** (t195) The blocks of a plan meant to be higher than the house's ridge (a tower house). */
        final Set<HouseModule> tall = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        /** The rank of the part each block is of (see {@link House#rank}). */
        private final Map<Long, Integer> ranks = new HashMap<>();
        int conflicts;

        Plan(TreeSettings s, int tolerance) {
            this.s = s;
            this.tolerance = tolerance;
            house = new House(s);
            house.body(local);
            width = house.width;
            depth = house.depth;
            floors = house.floors;
            storey = house.storey;
            midA = (width - 1) / 2;
            midB = (depth - 1) / 2;
            int high = house.top;
            for (long key : local.map.keySet()) high = Math.max(high, TreeVoxelizer.unpackY(key));
            ridge = high;
            if (house.door && storey >= 3) {
                int a = house.plan(0).doorAt();
                for (int b = depth - 1; b <= depth + 2; b++) {
                    for (int y = 1; y <= 2; y++) door.add(TreeVoxelizer.pack(a, y, b));
                }
            }
            tops = Build.tops(local);
        }

        /** How high the ridge is over the eaves. */
        int ridgeRise() {
            return Math.max(1, ridge - house.top);
        }

        /** The {@code along} of the door (from the middle of the front). */
        int doorU() {
            return house.plan(0).doorAt() - midA;
        }

        /** The length of a side (the width for the front and the back). */
        int length(int side) {
            return side % 2 == 0 ? width : depth;
        }

        /** The {@code u} of the first and the last block of a side (as a part's frame counts it; the front and the back of the ground storey). */
        int low(int side) {
            return switch (side) {
                case 1 -> -(depth - 1 - midB);
                case 2 -> -(width - 1 - midA);
                case 3 -> -midB;
                default -> -midA;
            };
        }

        int high(int side) {
            return switch (side) {
                case 1 -> midB;
                case 2 -> midA;
                case 3 -> depth - 1 - midB;
                default -> width - 1 - midA;
            };
        }

        /** Whether the mirror (left and right) of a side is itself (the front and the back). */
        boolean mirrors(int side) {
            return side % 2 == 0;
        }

        /** The part mirrored left to right (a → width - 1 - a): the same side for the front and the back, the other side for the right and the left. */
        HouseModule mirror(HouseModule module) {
            HouseModule mirror = module.copy();
            int w = switch (module.type) {
                case DORMER, TOWER, WING, VOLUME -> Math.max(3, module.width);
                case BAY -> Math.max(2, module.width);
                case STAIR -> 3;
                default -> module.width;
            };
            // (stairs mirrored go up the other way)
            if (module.type == Type.STAIR) mirror.shape = 1 - module.shape;
            int lo = -(w - 1) / 2;
            int hi = lo + w - 1;
            int shift = switch (module.side) {
                case 0 -> width - 1 - 2 * midA;
                case 2 -> -(width - 1 - 2 * midA);
                default -> 0;
            };
            mirror.along = shift - module.along - hi - lo;
            if (module.side % 2 == 1) mirror.side = 4 - module.side;
            return mirror;
        }

        /** The sides the roof slopes down to (where dormers go), the longer first: none for a flat roof. */
        List<Integer> slopes() {
            List<Integer> sides = new ArrayList<>();
            boolean alongA = house.ridge == 1 || house.ridge == 0 && width >= depth;
            switch (house.roof) {
                case 0 -> {
                }
                case 3 -> sides.add(house.ridge == 2 ? 1 : 0);
                case 2, 5, 8, 10 -> {
                    sides.addAll(width >= depth ? List.of(0, 2) : List.of(1, 3));
                    sides.addAll(width >= depth ? List.of(1, 3) : List.of(0, 2));
                }
                default -> sides.addAll(alongA ? List.of(0, 2) : List.of(1, 3));
            }
            return sides;
        }

        /** The sides with a gable over them (a chimney stands outside one): none for a hipped or a flat roof. */
        List<Integer> gables() {
            boolean alongA = house.ridge == 1 || house.ridge == 0 && width >= depth;
            return switch (house.roof) {
                case 0, 2, 3, 5, 8, 10 -> List.of();
                default -> alongA ? List.of(1, 3) : List.of(2);
            };
        }

        /** The {@code u} of the windows of a side at a storey (the middle of each stretch of wall between pillars with a window). */
        List<Integer> windows(int side, int level) {
            int k = Math.max(0, Math.min(floors - 1, level));
            HousePlan plan = house.plan(k);
            int e = house.out(k);
            int eA = house.outA(k);
            List<Integer> found = new ArrayList<>();
            int length = side % 2 == 0 ? plan.width() : plan.depth();
            for (int along = 1; along < length - 1; along++) {
                int a = side % 2 == 0 ? along : side == 1 ? plan.width() - 1 : 0;
                int pb = side % 2 == 0 ? (side == 0 ? plan.depth() - 1 : 0) : along;
                int[] panel = plan.panel(a, pb);
                if (panel == null || panel[1] - panel[0] - 1 < 3) continue;
                int middle = panel[0] + 1 + (panel[1] - panel[0] - 2) / 2;
                if (middle != along) continue;
                found.add(switch (side) {
                    case 1 -> midB - (along - e);
                    case 2 -> midA - (along - eA);
                    case 3 -> along - e - midB;
                    default -> along - eA - midA;
                });
            }
            return found;
        }

        /** A tower of {@code size} at {@code corner} (0 front left, 1 front right, 2 back right, 3 back left), its middle {@code out} past both walls. */
        HouseModule cornerTower(int corner, int size, int level, int height, int out) {
            boolean front = corner == 0 || corner == 1;
            int side = front ? 0 : 2;
            int u = switch (corner) {
                case 1 -> high(0) + out;
                case 2 -> low(2) - out;
                case 3 -> high(2) + out;
                default -> low(0) - out;
            };
            HouseModule tower = new HouseModule(Type.TOWER);
            tower.side = side;
            tower.width = size;
            tower.along = u;
            tower.offset = out - (1 + size) / 2;
            tower.level = level;
            tower.height = height;
            return tower;
        }

        /** (t195) A square volume of {@code storeys} with its middle on corner {@code corner} of the house (half in it, half out). */
        HouseModule blockAtCorner(int corner, int size, int storeys) {
            boolean front = corner == 0 || corner == 1;
            int u = switch (corner) {
                case 1 -> high(0);
                case 2 -> low(2);
                case 3 -> high(2);
                default -> low(0);
            };
            HouseModule block = new HouseModule(Type.VOLUME);
            block.side = front ? 0 : 2;
            block.along = u;
            block.width = size;
            block.depth = size;
            block.offset = -(size - 1) / 2;
            block.height = storeys;
            return block;
        }

        /** A tower in the middle of the house from its top storey, through the roof to {@code height} over the eaves. */
        HouseModule spire(int size, int height) {
            HouseModule tower = new HouseModule(Type.TOWER);
            int level = floors - 1;
            tower.side = 0;
            tower.width = size;
            tower.along = 0;
            tower.offset = midB - (depth - 1 + house.out(level)) - (1 + size) / 2;
            tower.level = level;
            tower.height = height;
            return tower;
        }

        /** A group of parts tried on the house: its builds and how many blocks of the parts before it covers. */
        record Trial(List<HouseModule> group, List<Build> builds, int hits) {
        }

        /**
         * Tries a group of parts on the house as it is (each worked out without being put in); null when one does not fit or the group covers more of the
         * parts before it than allowed. The parts in {@code exempt} (their places in the list) may be covered (a tower standing on the corner of its wing).
         */
        Trial trial(List<HouseModule> group, Set<Integer> exempt) {
            List<Build> builds = new ArrayList<>();
            int hits = 0;
            int cells = 0;
            for (HouseModule module : group) {
                if (!inRange(module) || !fits(module)) return null;
                Build build = house.prepare(local, tops, module);
                // (a wing, or a block of the plan on the ground, under the house's ridge; a block meant to be high, a tower house, may be over it)
                boolean under = module.type == Type.WING || module.type == Type.VOLUME && module.level == 0 && !tall.contains(module);
                if (blocksDoor(build) || under && highest(build) > ridge || !connected(module, build)) return null;
                hits += hits(build, exempt) + intoHouse(module, build);
                cells += build.over.map.size() + build.under.map.size();
                builds.add(build);
            }
            for (int i = 0; i < builds.size(); i++) {
                for (int j = i + 1; j < builds.size(); j++) hits += between(builds.get(i), builds.get(j));
            }
            int allowed = switch (tolerance) {
                case 0 -> 0;
                case 2 -> Math.max(6, (int) (cells * 0.10));
                default -> Math.max(2, (int) (cells * 0.03));
            };
            return hits > allowed ? null : new Trial(group, builds, hits);
        }

        /** Puts a group in (its blocks become theirs) and counts what it covered. */
        void apply(Trial trial) {
            for (int index = 0; index < trial.group().size(); index++) {
                HouseModule module = trial.group().get(index);
                Build build = trial.builds().get(index);
                int at = s.modules().size();
                int rank = House.rank(module.type);
                s.modules().add(module);
                // (as the house is built: not over nor out of a part of a higher rank)
                for (long key : build.carved) if (ranks.getOrDefault(key, 0) <= rank) owner.remove(key);
                for (long key : build.over.map.keySet()) if (ranks.getOrDefault(key, 0) <= rank) owner.put(key, at);
                for (long key : build.under.map.keySet()) if (!local.map.containsKey(key)) owner.put(key, at);
                build.apply(ranks, rank);
                tops = Build.tops(local);
            }
            conflicts += trial.hits();
        }

        /**
         * (t193) The height of a tower over the eaves so its walls end {@code over} blocks over the highest of what is round it (the base and the parts so
         * far, within 3 of its wall): a tower as high as what it stands among asks.
         */
        int towerHeight(HouseModule tower, int over) {
            Side side = house.side(tower.side, house.storeyOf(tower));
            int size = Math.max(3, tower.width);
            int lo = -(size - 1) / 2;
            double cu = tower.along + (2 * lo + size - 1) / 2.0;
            double cv = tower.offset + (1 + size) / 2.0;
            int reach = size / 2 + 3;
            int high = house.top;
            for (int du = -reach; du <= reach; du++) {
                for (int dv = -reach; dv <= reach; dv++) {
                    int u = (int) Math.round(cu + du);
                    int v = (int) Math.round(cv + dv);
                    Integer top = tops.get(HouseShape.column(side.a(u, v), side.b(u, v)));
                    if (top != null) high = Math.max(high, top);
                }
            }
            return Math.max(1, high + over - house.top);
        }

        /**
         * (t193) Whether a part is joined to the house as such a part is: stairs and a balcony lead through a wall into a room (the wall there to open, the
         * room behind it); a bay window stands on a wall with a room behind; a chimney outside leans on a wall; a volume or a tower over the ground touches
         * what is there.
         */
        private boolean connected(HouseModule module, Build build) {
            return switch (module.type) {
                case STAIR -> doorway(module, Math.max(1, Math.min(Math.max(1, floors - 1), module.level)) * storey);
                case BALCONY -> doorway(module, Math.max(0, Math.min(floors, module.level)) * storey);
                case BAY -> doorway(module, Math.max(0, Math.min(floors - 1, module.level)) * storey);
                case CHIMNEY -> module.offset < 0 || leans(module);
                case VOLUME, TOWER -> module.level == 0 || touches(build);
                default -> true;
            };
        }

        /** A wall of the house to open at the middle of a part over {@code floor} (2 high; on its line or set back one), and a room behind it. */
        private boolean doorway(HouseModule module, int floor) {
            Side side = house.side(module.side, house.storeyOf(module));
            for (int y = floor + 1; y <= floor + 2; y++) {
                int wall = local.has(side.a(module.along, 0), y, side.b(module.along, 0)) ? 0 : local.has(side.a(module.along, -1), y, side.b(module.along, -1)) ? -1 : 1;
                if (wall > 0) return false;
                int ia = side.a(module.along, wall - 1);
                int ib = side.b(module.along, wall - 1);
                if (local.has(ia, y, ib) || !house.inside(ia, ib, y)) return false;
            }
            return true;
        }

        /** The wall of the house behind the whole width of a chimney (from the ground up 2). */
        private boolean leans(HouseModule module) {
            Side side = house.side(module.side, 0);
            int lo = -(module.width - 1) / 2;
            for (int u = lo; u < lo + module.width; u++) {
                for (int y = 1; y <= 2; y++) {
                    if (!local.has(side.a(module.along + u, 0), y, side.b(module.along + u, 0))) return false;
                }
            }
            return true;
        }

        /** Whether a part has a block on, in or next to what is there. */
        private boolean touches(Build build) {
            for (long key : build.over.map.keySet()) {
                int x = TreeVoxelizer.unpackX(key);
                int y = TreeVoxelizer.unpackY(key);
                int z = TreeVoxelizer.unpackZ(key);
                if (local.has(x, y, z) || local.has(x + 1, y, z) || local.has(x - 1, y, z) || local.has(x, y, z + 1) || local.has(x, y, z - 1) || local.has(x, y - 1, z)) {
                    return true;
                }
            }
            return false;
        }

        /** The numbers of a part are as it would keep them (nothing to bring into range). */
        private static boolean inRange(HouseModule module) {
            HouseModule kept = module.copy();
            kept.limit();
            return kept.toMap().equals(module.toMap());
        }

        /** The rules of a kind of part against the house. */
        private boolean fits(HouseModule module) {
            return switch (module.type) {
                case DORMER -> dormerSite(module);
                case BALCONY -> floors >= 2 && module.level >= 1 && module.level < floors;
                case BAY -> module.level < floors;
                case CHIMNEY -> module.offset >= 0 || inside(module);
                case VOLUME -> module.level <= floors;
                case STAIR -> floors >= 2 && module.level >= 1 && module.level < floors;
                default -> true;
            };
        }

        /**
         * A dormer needs a slope that rises from its eaves away from the wall at every block of its face (not a gable end, not the corner of a hip), and the
         * roof behind it as high as its own ridge (it must not stick out over the house's).
         */
        private boolean dormerSite(HouseModule module) {
            if (house.roof == 0) return false;
            Side side = house.side(module.side, floors - 1);
            int w = Math.max(3, module.width);
            int lo = -(w - 1) / 2;
            int hi = lo + w - 1;
            int eaves = house.top + house.thick() + 1;
            for (int u = lo - 1; u <= hi + 1; u++) {
                int edge = top(side, module.along + u, module.offset);
                int in = top(side, module.along + u, module.offset - 2);
                if (edge == Integer.MIN_VALUE || edge > eaves || in <= edge) return false;
            }
            int ridgeY = house.top + Math.max(2, module.height) + house.dormerRise(0, lo, hi);
            for (int k = 1; k <= 20; k++) {
                if (top(side, module.along, module.offset - k) >= ridgeY) return true;
            }
            return false;
        }

        /** A chimney inside the walls stands where the house is, all of it. */
        private boolean inside(HouseModule module) {
            Side side = house.side(module.side, 0);
            int lo = -(module.width - 1) / 2;
            for (int u = lo; u < lo + module.width; u++) {
                for (int v = 1; v <= module.depth; v++) {
                    int a = side.a(module.along + u, module.offset + v);
                    int b = side.b(module.along + u, module.offset + v);
                    if (a <= 0 || a >= width - 1 || b <= -house.out(0) || b >= depth - 1) return false;
                }
            }
            return true;
        }

        private int top(Side side, int u, int v) {
            Integer top = tops.get(HouseShape.column(side.a(u, v), side.b(u, v)));
            return top == null ? Integer.MIN_VALUE : top;
        }

        private boolean blocksDoor(Build build) {
            for (long key : build.over.map.keySet()) if (door.contains(key)) return true;
            for (long key : build.under.map.keySet()) if (door.contains(key) && !local.map.containsKey(key)) return true;
            return false;
        }

        private static int highest(Build build) {
            int high = Integer.MIN_VALUE;
            for (long key : build.over.map.keySet()) high = Math.max(high, TreeVoxelizer.unpackY(key));
            return high;
        }

        /** The blocks of the parts before (but those in {@code exempt}) that a part would put over or take away. */
        private int hits(Build build, Set<Integer> exempt) {
            int hits = 0;
            for (long key : build.over.map.keySet()) {
                Integer who = owner.get(key);
                if (who != null && !exempt.contains(who)) hits++;
            }
            for (long key : build.carved) {
                Integer who = owner.get(key);
                if (who != null && !exempt.contains(who)) hits++;
            }
            return hits;
        }

        /**
         * The blocks of the house a part that stands outside it would put over: a porch, a bay or a balcony running into a jettied storey or the eaves, a
         * chimney into a storey (through the eaves it may go). The parts that join the house (towers, wings, dormers) are meant to.
         */
        private int intoHouse(HouseModule module, Build build) {
            boolean outside = switch (module.type) {
                case PORCH, BAY, BALCONY, STAIR -> true;
                case CHIMNEY -> module.offset >= 0;
                default -> false;
            };
            if (!outside) return 0;
            int hits = 0;
            for (long key : build.over.map.keySet()) {
                if (!local.map.containsKey(key) || owner.containsKey(key)) continue;
                if (module.type == Type.CHIMNEY && TreeVoxelizer.unpackY(key) >= house.top) continue;
                hits++;
            }
            return hits;
        }

        /** The blocks two parts of one group would both have (the second put over the first, or taking it away). */
        private static int between(Build first, Build second) {
            int hits = 0;
            for (long key : second.over.map.keySet()) if (first.over.map.containsKey(key) || first.under.map.containsKey(key)) hits++;
            for (long key : second.carved) if (first.over.map.containsKey(key) || first.under.map.containsKey(key)) hits++;
            return hits;
        }

        /** How many parts make the roofline more than one height: towers over the ridge, wings with their roofs under it. */
        int peaks() {
            Map<Integer, Integer> high = new HashMap<>();
            for (Map.Entry<Long, Integer> entry : owner.entrySet()) high.merge(entry.getValue(), TreeVoxelizer.unpackY(entry.getKey()), Math::max);
            int peaks = 0;
            for (Map.Entry<Integer, Integer> entry : high.entrySet()) {
                if (entry.getKey() >= s.modules().size()) continue;
                Type type = s.modules().get(entry.getKey()).type;
                if (type == Type.TOWER && entry.getValue() > ridge + 1 || type == Type.WING && entry.getValue() < ridge - 1
                        || type == Type.VOLUME && Math.abs(entry.getValue() - ridge) >= 2) peaks++;
            }
            return peaks;
        }

        /** How far the house reaches from its middle (its footprint, the parts and the eaves). */
        double radius() {
            double far = 0.0;
            for (long key : local.map.keySet()) {
                far = Math.max(far, Math.hypot(TreeVoxelizer.unpackX(key) - midA, TreeVoxelizer.unpackZ(key) - midB));
            }
            return far;
        }
    }
}
