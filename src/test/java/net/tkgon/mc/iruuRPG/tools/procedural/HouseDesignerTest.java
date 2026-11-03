package net.tkgon.mc.iruuRPG.tools.procedural;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The random houses (t185): made by the rules of a pattern, the parts kept off each other and the door, the same house from the same seed. */
class HouseDesignerTest {

    private static TreeSettings rules(HouseDesigner.Pattern pattern, int size, int detail) {
        TreeSettings settings = new TreeSettings();
        settings.setKind(ProcKind.HOUSE);
        settings.set(TreeSettings.Key.HOUSE_GEN_PATTERN, pattern.ordinal());
        settings.set(TreeSettings.Key.HOUSE_GEN_SIZE, size);
        settings.set(TreeSettings.Key.HOUSE_GEN_DETAIL, detail);
        return settings;
    }

    private static Map<Long, TreePart> build(TreeSettings settings) {
        return ProceduralShapes.build(ProcKind.HOUSE, settings, 1L, new Random(1), null, null);
    }

    private static long count(TreeSettings settings, HouseModule.Type type) {
        return settings.modules().stream().filter(module -> module.type == type).count();
    }

    @Test
    void everyPatternMakesAHouseThatFits() {
        for (HouseDesigner.Pattern pattern : HouseDesigner.Pattern.values()) {
            if (pattern == HouseDesigner.Pattern.ANY) continue;
            for (int size = 0; size <= 2; size++) {
                for (int detail : new int[]{0, 2, 3}) {
                    for (long seed = 1; seed <= 3; seed++) {
                        HouseDesigner.Result result = HouseDesigner.design(rules(pattern, size, detail), seed, false);
                        TreeSettings made = result.settings();
                        String what = pattern + " size " + size + " detail " + detail + " seed " + seed;
                        assertEquals(pattern, result.pattern());
                        assertTrue(made.modules().size() <= TreeSettings.MAX_MODULES, what);
                        for (HouseModule module : made.modules()) {
                            HouseModule kept = module.copy();
                            kept.limit();
                            assertEquals(kept.toMap(), module.toMap(), what);
                        }
                        Map<Long, TreePart> blocks = build(made);
                        assertTrue(blocks.size() < 30000, what + ": " + blocks.size());
                        assertTrue(result.conflicts() <= 40, what + ": " + result.conflicts());
                        // the way in: the door and three blocks in front of it, 2 high, are clear
                        int width = made.getInt(TreeSettings.Key.HOUSE_WIDTH);
                        int depth = made.getInt(TreeSettings.Key.HOUSE_DEPTH);
                        HouseShape.House house = new HouseShape.House(made);
                        // (t195: all of it lifted by the podium)
                        int lift = made.getInt(TreeSettings.Key.HOUSE_RAISE);
                        // (t193) odd both ways, and so the volumes and the towers: every roof has a ridge or a point of one block
                        assertEquals(1, width % 2, what);
                        assertEquals(1, depth % 2, what);
                        for (HouseModule module : made.modules()) {
                            if (module.type == HouseModule.Type.TOWER || module.type == HouseModule.Type.VOLUME) assertEquals(1, module.width % 2, what + " " + module.toMap());
                            if (module.type == HouseModule.Type.VOLUME) assertEquals(1, module.depth % 2, what + " " + module.toMap());
                            // stairs reach a doorway in a wall: the landing before it, the wall opened over it
                            if (module.type == HouseModule.Type.STAIR) {
                                HouseShape.Side side = house.side(module.side, module.level);
                                int floor = module.level * house.storey + lift;
                                int cx = -(width - 1) / 2;
                                int cz = -(depth - 1) / 2;
                                assertEquals(TreePart.HOUSE_STONE, blocks.get(TreeVoxelizer.pack(side.a(module.along, 1) + cx, floor, side.b(module.along, 1) + cz)), what);
                                assertNull(blocks.get(TreeVoxelizer.pack(side.a(module.along, 0) + cx, floor + 1, side.b(module.along, 0) + cz)), what);
                            }
                        }
                        int x = house.plan(0).doorAt() - (width - 1) / 2;
                        for (int z = depth - 1; z <= depth + 2; z++) {
                            for (int y = 1; y <= 2; y++) assertNull(blocks.get(TreeVoxelizer.pack(x, y + lift, z - (depth - 1) / 2)), what + " door at " + x + "," + y + "," + z);
                        }
                        // the parts the pattern is about
                        switch (pattern) {
                            case FARM -> assertTrue(count(made, HouseModule.Type.WING) >= 1, what);
                            case TOWER -> assertTrue(count(made, HouseModule.Type.TOWER) >= 1, what);
                            case MANOR -> assertTrue(count(made, HouseModule.Type.WING) >= 2, what);
                            case HUB -> assertTrue(count(made, HouseModule.Type.VOLUME) + count(made, HouseModule.Type.TOWER) >= 1, what);
                            case COMPOUND -> assertTrue(count(made, HouseModule.Type.VOLUME) >= 1, what);
                            case STORYBOOK -> {
                                // (t239) the gable to the front and the tower with its spire
                                assertTrue(made.modules().stream().anyMatch(m -> m.type == HouseModule.Type.VOLUME && m.side == 0 && m.ridge == 2), what);
                                assertTrue(made.modules().stream().anyMatch(m -> m.type == HouseModule.Type.TOWER && m.spire > 0), what);
                            }
                            case PLAN -> {
                                // (t195) blocks of the plan, each its own roof: more than one shape of roof
                                assertTrue(count(made, HouseModule.Type.VOLUME) >= 1, what);
                                Set<Integer> roofs = new HashSet<>();
                                roofs.add(made.getInt(TreeSettings.Key.HOUSE_ROOF));
                                for (HouseModule module : made.modules()) if (module.type == HouseModule.Type.VOLUME) roofs.add(module.roof < 0 ? made.getInt(TreeSettings.Key.HOUSE_ROOF) : module.roof);
                                assertTrue(roofs.size() >= 2, what + " " + roofs);
                            }
                            default -> {
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void theShapeIsKeptWhenItIsNotToBeChosenAndTheTowersFitWhatIsRoundThem() {
        TreeSettings rules = rules(HouseDesigner.Pattern.HUB, 1, 2);
        rules.set(TreeSettings.Key.HOUSE_GEN_SHAPE, 0);
        rules.set(TreeSettings.Key.HOUSE_WIDTH, 14);
        rules.set(TreeSettings.Key.HOUSE_DEPTH, 10);
        rules.set(TreeSettings.Key.HOUSE_FLOORS, 3);
        TreeSettings made = HouseDesigner.design(rules, 21L, false).settings();
        assertEquals(14, made.getInt(TreeSettings.Key.HOUSE_WIDTH));
        assertEquals(10, made.getInt(TreeSettings.Key.HOUSE_DEPTH));
        assertEquals(3, made.getInt(TreeSettings.Key.HOUSE_FLOORS));
        // the towers of the tower house: of several sizes and heights, not one
        Set<Integer> sizes = new HashSet<>();
        Set<Integer> heights = new HashSet<>();
        for (long seed = 1; seed <= 12; seed++) {
            for (HouseModule module : HouseDesigner.design(rules(HouseDesigner.Pattern.TOWER, (int) (seed % 3), 1), seed, false).settings().modules()) {
                if (module.type != HouseModule.Type.TOWER || module.level != 0) continue;
                sizes.add(module.width);
                heights.add(module.height);
            }
        }
        assertTrue(sizes.size() >= 2, sizes.toString());
        assertTrue(heights.size() >= 4, heights.toString());
    }

    @Test
    void housesOfPlansStandOnFoundationsOfSeveralHeights() {
        int raised = 0;
        int stepped = 0;
        for (long seed = 1; seed <= 12; seed++) {
            TreeSettings made = HouseDesigner.design(rules(HouseDesigner.Pattern.PLAN, 1, 2), seed, false).settings();
            if (made.getInt(TreeSettings.Key.HOUSE_RAISE) > 0) raised++;
            for (HouseModule module : made.modules()) {
                if (module.type == HouseModule.Type.VOLUME && module.level == 0 && module.raise != 0) {
                    stepped++;
                    break;
                }
            }
        }
        assertTrue(raised >= 4, Integer.toString(raised));
        assertTrue(stepped >= 4, Integer.toString(stepped));
    }

    @Test
    void theSameSeedGivesTheSameHouse() {
        TreeSettings rules = rules(HouseDesigner.Pattern.ANY, 1, 2);
        HouseDesigner.Result first = HouseDesigner.design(rules, 1234L, false);
        HouseDesigner.Result again = HouseDesigner.design(rules, 1234L, false);
        assertEquals(first.settings().toMap(), again.settings().toMap());
        assertEquals(1234L, first.settings().designSeed());
        assertNotEquals(first.settings().toMap(), HouseDesigner.design(rules, 4321L, false).settings().toMap());
        // what the rules do not make is kept: the turn, the blocks chosen, the foundation
        rules.set(TreeSettings.Key.HOUSE_ROTATION, 33);
        rules.setBlock(TreePart.HOUSE_ROOF, "BRICKS");
        rules.set(TreeSettings.Key.HOUSE_FOUNDATION, 0);
        TreeSettings made = HouseDesigner.design(rules, 99L, false).settings();
        assertEquals(33, made.getInt(TreeSettings.Key.HOUSE_ROTATION));
        assertEquals("BRICKS", made.block(TreePart.HOUSE_ROOF));
        assertEquals(0, made.getInt(TreeSettings.Key.HOUSE_FOUNDATION));
    }

    @Test
    void aHouseTheSameBothWaysIsMirrored() {
        for (HouseDesigner.Pattern pattern : HouseDesigner.Pattern.values()) {
            if (pattern == HouseDesigner.Pattern.ANY) continue;
            for (long seed = 1; seed <= 4; seed++) {
                TreeSettings rules = rules(pattern, 1, 3);
                rules.set(TreeSettings.Key.HOUSE_GEN_SYMMETRY, 1);
                TreeSettings made = HouseDesigner.design(rules, seed, false).settings();
                assertEquals(1, made.getInt(TreeSettings.Key.HOUSE_WIDTH) % 2, pattern + " seed " + seed);
                Map<Long, TreePart> blocks = build(made);
                for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
                    int x = TreeVoxelizer.unpackX(entry.getKey());
                    int y = TreeVoxelizer.unpackY(entry.getKey());
                    int z = TreeVoxelizer.unpackZ(entry.getKey());
                    assertEquals(entry.getValue(), blocks.get(TreeVoxelizer.pack(-x, y, z)), pattern + " seed " + seed + " at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void withNoOverlapThePartsCoverNothingOfEachOther() {
        for (HouseDesigner.Pattern pattern : HouseDesigner.Pattern.values()) {
            TreeSettings rules = rules(pattern, 1, 3);
            rules.set(TreeSettings.Key.HOUSE_GEN_OVERLAP, 0);
            for (long seed = 1; seed <= 3; seed++) assertEquals(0, HouseDesigner.design(rules, seed, false).conflicts(), pattern + " seed " + seed);
        }
    }

    @Test
    void newPartsOnlyKeepTheBaseAndTheRulesAreFollowed() {
        TreeSettings base = HouseDesigner.design(rules(HouseDesigner.Pattern.MANOR, 1, 2), 7L, false).settings();
        TreeSettings parts = HouseDesigner.design(base, 8L, true).settings();
        for (TreeSettings.Key key : TreeSettings.Key.values()) assertEquals(base.get(key), parts.get(key), key.id());
        assertEquals(base.roofSteps(), parts.roofSteps());
        assertNotEquals(base.modules().stream().map(HouseModule::toMap).toList(), parts.modules().stream().map(HouseModule::toMap).toList());
        // no towers when none are asked for; the roof kept when it is not to be chosen
        TreeSettings rules = rules(HouseDesigner.Pattern.HUB, 1, 3);
        rules.set(TreeSettings.Key.HOUSE_GEN_TOWERS, 1);
        rules.set(TreeSettings.Key.HOUSE_GEN_ROOF, 0);
        rules.set(TreeSettings.Key.HOUSE_ROOF, 6);
        TreeSettings made = HouseDesigner.design(rules, 5L, false).settings();
        assertEquals(0, count(made, HouseModule.Type.TOWER));
        assertEquals(6, made.getInt(TreeSettings.Key.HOUSE_ROOF));
        // few parts and many
        assertTrue(HouseDesigner.design(rules(HouseDesigner.Pattern.COTTAGE, 1, 0), 3L, false).settings().modules().size()
                < HouseDesigner.design(rules(HouseDesigner.Pattern.COTTAGE, 1, 3), 3L, false).settings().modules().size());
    }

    @Test
    void thePartsAreSizedFromTheShapeEvenWhenItIsSetByHand() {
        // (t239) a big house with pillars far apart, and a small one with them close: the shape kept ("形もおまかせ" off), the parts fitted to it
        Map<String, Set<Integer>> big = parts(21, 13, 3, 6, 6);
        Map<String, Set<Integer>> small = parts(9, 9, 2, 4, 3);
        // dormers as wide as a stretch between pillars (5 between pillars 6 apart; 3 between pillars 3 apart), their walls as high as the storey allows
        assertTrue(big.get("dormer").stream().allMatch(w -> w >= 5), big.toString());
        assertTrue(small.get("dormer").stream().allMatch(w -> w == 3), small.toString());
        assertTrue(big.get("dormer-height").stream().allMatch(h -> h >= 3), big.toString());
        // bay windows the width of the stretch between pillars
        assertTrue(big.get("bay").stream().allMatch(w -> w == 5 || w == 7), big.toString());
        assertTrue(small.get("bay").stream().allMatch(w -> w == 2 || w == 4), small.toString());
        // the porch from pillar to pillar more often than not on the big house, never wider than the small one
        assertTrue(big.get("porch").stream().anyMatch(w -> w == 7 || w == 13), big.toString());
        assertTrue(small.get("porch").stream().allMatch(w -> w <= 5), small.toString());
    }

    /** The widths (and the dormers' heights) of the parts of 24 houses of several patterns made on a shape set by hand. */
    private static Map<String, Set<Integer>> parts(int width, int depth, int floors, int storey, int spacing) {
        Map<String, Set<Integer>> found = new java.util.HashMap<>();
        for (String key : new String[]{"dormer", "dormer-height", "bay", "porch"}) found.put(key, new HashSet<>());
        for (HouseDesigner.Pattern pattern : new HouseDesigner.Pattern[]{HouseDesigner.Pattern.COTTAGE, HouseDesigner.Pattern.MANOR, HouseDesigner.Pattern.STORYBOOK}) {
            for (long seed = 1; seed <= 8; seed++) {
                TreeSettings rules = rules(pattern, 0, 3);
                rules.set(TreeSettings.Key.HOUSE_GEN_SHAPE, 0);
                rules.set(TreeSettings.Key.HOUSE_WIDTH, width);
                rules.set(TreeSettings.Key.HOUSE_DEPTH, depth);
                rules.set(TreeSettings.Key.HOUSE_FLOORS, floors);
                rules.set(TreeSettings.Key.HOUSE_STOREY, storey);
                rules.set(TreeSettings.Key.HOUSE_PILLARS, spacing);
                TreeSettings made = HouseDesigner.design(rules, seed, false).settings();
                assertEquals(width, made.getInt(TreeSettings.Key.HOUSE_WIDTH));
                assertEquals(spacing, made.getInt(TreeSettings.Key.HOUSE_PILLARS));
                for (HouseModule module : made.modules()) {
                    switch (module.type) {
                        case DORMER -> {
                            found.get("dormer").add(module.width);
                            found.get("dormer-height").add(module.height);
                        }
                        case BAY -> found.get("bay").add(module.width);
                        case PORCH -> found.get("porch").add(module.width);
                        default -> {
                        }
                    }
                }
            }
        }
        return found;
    }

    @Test
    void housesOfEveryPatternCanBeOfSeveralBlocksOrOne() {
        // (t246) the plan rule: of several blocks (on the sides and the back), or one rectangle
        for (HouseDesigner.Pattern pattern : HouseDesigner.Pattern.values()) {
            if (pattern == HouseDesigner.Pattern.ANY || pattern == HouseDesigner.Pattern.PLAN) continue;
            int several = 0;
            for (long seed = 1; seed <= 4; seed++) {
                TreeSettings rules = rules(pattern, 1, 2);
                rules.set(TreeSettings.Key.HOUSE_GEN_PLAN, 2);
                HouseDesigner.Result result = HouseDesigner.design(rules, seed, false);
                TreeSettings made = result.settings();
                String what = pattern + " seed " + seed;
                if (made.modules().stream().anyMatch(m -> m.type == HouseModule.Type.VOLUME && m.level == 0 && m.side != 0 && m.width >= 5)) several++;
                assertTrue(result.conflicts() <= 40, what);
                // still a house that fits: the parts in range, odd blocks, the way in at the door clear
                for (HouseModule module : made.modules()) {
                    HouseModule kept = module.copy();
                    kept.limit();
                    assertEquals(kept.toMap(), module.toMap(), what);
                    if (module.type == HouseModule.Type.VOLUME) assertEquals(1, module.width % 2, what + " " + module.toMap());
                }
                Map<Long, TreePart> blocks = build(made);
                assertTrue(blocks.size() < 30000, what);
                int width = made.getInt(TreeSettings.Key.HOUSE_WIDTH);
                int depth = made.getInt(TreeSettings.Key.HOUSE_DEPTH);
                int lift = made.getInt(TreeSettings.Key.HOUSE_RAISE);
                int x = new HouseShape.House(made).plan(0).doorAt() - (width - 1) / 2;
                for (int z = depth - 1; z <= depth + 2; z++) {
                    for (int y = 1; y <= 2; y++) assertNull(blocks.get(TreeVoxelizer.pack(x, y + lift, z - (depth - 1) / 2)), what + " door");
                }
            }
            assertTrue(several >= 3, pattern + ": " + several);
        }
        // one rectangle: no blocks of the plan on a cottage (which has none of its own)
        for (long seed = 1; seed <= 6; seed++) {
            TreeSettings rules = rules(HouseDesigner.Pattern.COTTAGE, 1, 3);
            rules.set(TreeSettings.Key.HOUSE_GEN_PLAN, 1);
            assertEquals(0, count(HouseDesigner.design(rules, seed, false).settings(), HouseModule.Type.VOLUME), "seed " + seed);
        }
    }
}
