package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The house base of the procedural tool: pillars, walls with a door and windows, floors and the roofs, the front toward the player (t179); the turn, the
 * fantasy roofs, the jetty, the stone ground floor, the timber frame and the parts added on (t181).
 */
class HouseShapeTest {

    private static final Vector SOUTH = new Vector(0.0, 0.0, 1.0);

    private static TreeSettings house(int width, int depth, int floors, int storey, int roof) {
        TreeSettings settings = new TreeSettings();
        settings.setKind(ProcKind.HOUSE);
        settings.set(TreeSettings.Key.HOUSE_WIDTH, width);
        settings.set(TreeSettings.Key.HOUSE_DEPTH, depth);
        settings.set(TreeSettings.Key.HOUSE_FLOORS, floors);
        settings.set(TreeSettings.Key.HOUSE_STOREY, storey);
        settings.set(TreeSettings.Key.HOUSE_ROOF, roof);
        settings.set(TreeSettings.Key.HOUSE_OVERHANG, 0);
        settings.set(TreeSettings.Key.HOUSE_PITCH, 1.0);
        return settings;
    }

    private static Map<Long, TreePart> build(TreeSettings settings, Vector aim) {
        return ProceduralShapes.build(ProcKind.HOUSE, settings, 1L, new Random(1), null, aim);
    }

    private static TreePart at(Map<Long, TreePart> blocks, int x, int y, int z) {
        return blocks.get(TreeVoxelizer.pack(x, y, z));
    }

    private static int highest(Map<Long, TreePart> blocks, TreePart part) {
        int high = Integer.MIN_VALUE;
        for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
            if (entry.getValue() == part) high = Math.max(high, TreeVoxelizer.unpackY(entry.getKey()));
        }
        return high;
    }

    private static long count(Map<Long, TreePart> blocks, TreePart part) {
        return blocks.values().stream().filter(part::equals).count();
    }

    private static HouseModule module(TreeSettings settings, HouseModule.Type type, int side) {
        HouseModule module = new HouseModule(type);
        module.side = side;
        settings.modules().add(module);
        return module;
    }

    @Test
    void aHouseHasPillarsWallsFloorsADoorAndARoof() {
        // 11 wide, 9 deep, 2 storeys of 4, a gable, the player to the south (+z): the front is at z = 4, x from -5 to 5
        Map<Long, TreePart> blocks = build(house(11, 9, 2, 4, 1), SOUTH);
        // the corners are pillars all the way up
        for (int y = 0; y < 8; y++) {
            assertEquals(TreePart.HOUSE_PILLAR, at(blocks, -5, y, -4));
            assertEquals(TreePart.HOUSE_PILLAR, at(blocks, 5, y, 4));
        }
        // a floor inside at the foot of each storey, nothing between
        assertEquals(TreePart.HOUSE_FLOOR, at(blocks, 0, 0, 0));
        assertEquals(TreePart.HOUSE_FLOOR, at(blocks, 0, 4, 0));
        assertNull(at(blocks, 0, 2, 0));
        // the door in the middle of the front, 2 high on the ground floor; the wall above it
        assertNull(at(blocks, 0, 1, 4));
        assertNull(at(blocks, 0, 2, 4));
        assertEquals(TreePart.HOUSE_WALL, at(blocks, 0, 3, 4));
        // the back wall has no door
        assertEquals(TreePart.HOUSE_WALL, at(blocks, 0, 1, -4));
        // a gable over the 9 deep side: the ridge along the front, highest over the middle (4 blocks in at pitch 1)
        assertEquals(TreePart.HOUSE_ROOF, at(blocks, 0, 8 + 4, 0));
        assertEquals(TreePart.HOUSE_ROOF, at(blocks, 0, 8, 4));
        // the gable end is walled up under the roof (where a pillar stands, the pillar goes on up)
        assertEquals(TreePart.HOUSE_WALL, at(blocks, -5, 9, 1));
        assertEquals(TreePart.HOUSE_PILLAR, at(blocks, -5, 9, 0));
    }

    @Test
    void theFrontTurnsTowardThePlayer() {
        // the player to the east (+x): the front is the +x side, so the door is at x = 4 (9 deep turned along x)
        Map<Long, TreePart> blocks = build(house(11, 9, 1, 4, 0), new Vector(1.0, 0.0, 0.0));
        assertNull(at(blocks, 4, 1, 0));
        assertEquals(TreePart.HOUSE_WALL, at(blocks, -4, 1, 0));
        // a flat roof on top
        assertEquals(TreePart.HOUSE_ROOF, at(blocks, 0, 4, 0));
    }

    @Test
    void theRoofsRiseAsTheirShapeSays() {
        // 10 x 6 with no overhang, pitch 1 (the eaves from 0 to 9 along a, 0 to 5 along b)
        assertEquals(0, HouseShape.roofRise(0, 0, 3, 3, 0, 9, 0, 5, 1.0));
        // gable: across the 6 deep side; the eaves 0, the middle 2
        assertEquals(0, HouseShape.roofRise(1, 0, 4, 0, 0, 9, 0, 5, 1.0));
        assertEquals(2, HouseShape.roofRise(1, 0, 0, 2, 0, 9, 0, 5, 1.0));
        // a ridge across the front: the slopes go along a instead
        assertEquals(0, HouseShape.roofRise(1, 2, 0, 2, 0, 9, 0, 5, 1.0));
        assertEquals(4, HouseShape.roofRise(1, 2, 4, 2, 0, 9, 0, 5, 1.0));
        // hip: from every side, so the ends slope too
        assertEquals(0, HouseShape.roofRise(2, 0, 0, 2, 0, 9, 0, 5, 1.0));
        assertEquals(2, HouseShape.roofRise(2, 0, 4, 2, 0, 9, 0, 5, 1.0));
        // shed: from the front (the high b) up to the back at half the pitch
        assertEquals(0, HouseShape.roofRise(3, 0, 4, 5, 0, 9, 0, 5, 1.0));
        assertTrue(HouseShape.roofRise(3, 0, 4, 0, 0, 9, 0, 5, 1.0) > 0);
        assertFalse(HouseShape.roofName(1).isEmpty());
    }

    @Test
    void theFantasyRoofsHaveTheirOwnProfiles() {
        // a 20 x 12 roof (b from 0 to 11: 6 in to the ridge), pitch 1
        // gambrel: the first steps from the eaves are steeper than those near the ridge
        int first = HouseShape.roofRise(4, 0, 10, 1, 0, 19, 0, 11, 1.0) - HouseShape.roofRise(4, 0, 10, 0, 0, 19, 0, 11, 1.0);
        int last = HouseShape.roofRise(4, 0, 10, 5, 0, 19, 0, 11, 1.0) - HouseShape.roofRise(4, 0, 10, 4, 0, 19, 0, 11, 1.0);
        assertTrue(first > last, first + " > " + last);
        // hip-and-gable: low at the very end (hipped), the full gable a few blocks in
        assertEquals(0, HouseShape.roofRise(6, 0, 0, 5, 0, 19, 0, 11, 1.0));
        assertEquals(5, HouseShape.roofRise(6, 0, 10, 5, 0, 19, 0, 11, 1.0));
        // clipped gable: the end is a gable up to about half, then hipped
        int end = HouseShape.roofRise(7, 0, 0, 5, 0, 19, 0, 11, 1.0);
        assertTrue(end > 0 && end < 5, "clipped: " + end);
        // curved: flat at the eaves, steep near the top
        int low = HouseShape.roofRise(8, 0, 10, 1, 0, 19, 0, 11, 1.0) - HouseShape.roofRise(8, 0, 10, 0, 0, 19, 0, 11, 1.0);
        int high = HouseShape.roofRise(8, 0, 10, 5, 0, 19, 0, 11, 1.0) - HouseShape.roofRise(8, 0, 10, 4, 0, 19, 0, 11, 1.0);
        assertTrue(high > low, high + " > " + low);
        // every roof can be built, and the ones with a step at the end (hip-and-gable, clipped gable) wall it up
        for (int roof = 0; roof <= 8; roof++) {
            Map<Long, TreePart> blocks = build(house(15, 9, 1, 4, roof), SOUTH);
            assertTrue(count(blocks, TreePart.HOUSE_ROOF) > 0, HouseShape.roofName(roof));
        }
        Map<Long, TreePart> irimoya = build(house(15, 9, 1, 4, 6), SOUTH);
        boolean gable = false;
        for (int x = -6; x <= 6; x++) {
            for (int y = 5; y < 12; y++) {
                for (int z = -3; z <= 3; z++) gable |= at(irimoya, x, y, z) == TreePart.HOUSE_WALL;
            }
        }
        assertTrue(gable, "the small gable of the hip-and-gable is walled");
    }

    @Test
    void aRidgeAcrossTheFrontPutsAGableOnTheFront() {
        TreeSettings settings = house(11, 9, 1, 4, 1);
        settings.set(TreeSettings.Key.HOUSE_RIDGE, 2);
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        // the front wall goes up under the roof in its middle (a gable), the roof is highest over the middle of the width
        assertEquals(TreePart.HOUSE_WALL, at(blocks, 1, 6, 4));
        assertEquals(TreePart.HOUSE_ROOF, at(blocks, 0, 4 + 5, 0));
    }

    @Test
    void theRotationTurnsTheHouseOnFromItsFront() {
        // 90 more (clockwise from above) from a front to the south: the front faces west, the door at x = -4
        TreeSettings settings = house(11, 9, 1, 4, 0);
        settings.set(TreeSettings.Key.HOUSE_ROTATION, 90);
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        assertNull(at(blocks, -4, 1, 0));
        assertEquals(TreePart.HOUSE_WALL, at(blocks, 4, 1, 0));
        // a turn between the four ways: about as many blocks, and the walls of a layer touch along a face, never only at an edge
        Map<Long, TreePart> square = build(house(11, 9, 1, 4, 1), SOUTH);
        TreeSettings turned = house(11, 9, 1, 4, 1);
        turned.set(TreeSettings.Key.HOUSE_ROTATION, 30);
        Map<Long, TreePart> slanted = build(turned, SOUTH);
        assertTrue(slanted.size() > square.size() * 0.8 && slanted.size() < square.size() * 1.6, slanted.size() + " vs " + square.size());
        for (Map.Entry<Long, TreePart> entry : slanted.entrySet()) {
            int x = TreeVoxelizer.unpackX(entry.getKey());
            int y = TreeVoxelizer.unpackY(entry.getKey());
            int z = TreeVoxelizer.unpackZ(entry.getKey());
            for (int dz = -1; dz <= 1; dz += 2) {
                if (!slanted.containsKey(TreeVoxelizer.pack(x + 1, y, z + dz))) continue;
                assertTrue(slanted.containsKey(TreeVoxelizer.pack(x + 1, y, z)) || slanted.containsKey(TreeVoxelizer.pack(x, y, z + dz)), "edge only at " + x + "," + y + "," + z);
            }
        }
    }

    @Test
    void upperStoreysJutOutAndTheGroundStoreyCanBeStoneWithATimberFrameAbove() {
        TreeSettings settings = house(11, 9, 2, 4, 1);
        settings.set(TreeSettings.Key.HOUSE_JETTY, 1);
        settings.set(TreeSettings.Key.HOUSE_STONE, 1);
        settings.set(TreeSettings.Key.HOUSE_TIMBER, 1);
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        // the ground storey: stone walls and corners at the front (z = 4)
        assertEquals(TreePart.HOUSE_STONE, at(blocks, -5, 1, 4));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 2, 1, 4));
        // the upper storey reaches one further out at the front and the back, on its floor
        assertEquals(TreePart.HOUSE_PILLAR, at(blocks, -5, 5, 5));
        assertEquals(TreePart.HOUSE_PILLAR, at(blocks, -5, 5, -5));
        assertEquals(TreePart.HOUSE_FLOOR, at(blocks, 0, 4, 4));
        // the timber frame: a beam at the foot of the upper storey's walls, braces in them (t190: of the beam part)
        assertEquals(TreePart.HOUSE_BEAM, at(blocks, 2, 4, 5));
        assertTrue(count(blocks, TreePart.HOUSE_BEAM) > 20);
        assertEquals(0, count(build(house(11, 9, 2, 4, 1), SOUTH), TreePart.HOUSE_BEAM));
    }

    @Test
    void theFoundationGoesDownToTheGround() {
        // the ground 3 below the house everywhere: the pillars go down to it; with a stone ground storey, its walls too
        TreeGround ground = (x, z) -> -3;
        TreeSettings settings = house(11, 9, 1, 4, 1);
        Map<Long, TreePart> blocks = ProceduralShapes.build(ProcKind.HOUSE, settings, 1L, new Random(1), ground, SOUTH);
        assertEquals(TreePart.HOUSE_PILLAR, at(blocks, -5, -2, 4));
        assertNull(at(blocks, -5, -3, 4));
        assertNull(at(blocks, -4, -1, 4));
        settings.set(TreeSettings.Key.HOUSE_STONE, 1);
        blocks = ProceduralShapes.build(ProcKind.HOUSE, settings, 1L, new Random(1), ground, SOUTH);
        assertEquals(TreePart.HOUSE_STONE, at(blocks, -4, -2, 4));
        // turned between the four ways, it still stands on the ground
        settings.set(TreeSettings.Key.HOUSE_ROTATION, 30);
        blocks = ProceduralShapes.build(ProcKind.HOUSE, settings, 1L, new Random(1), ground, SOUTH);
        assertTrue(count(blocks, TreePart.HOUSE_STONE) > 0);
        assertTrue(blocks.keySet().stream().anyMatch(key -> TreeVoxelizer.unpackY(key) == -2));
    }

    @Test
    void theRoofIsNotCoveredByTheFloorUnderIt() {
        // a curved roof is flat at its eaves, down on the floor under it: the roof shows there, not the floor
        Map<Long, TreePart> blocks = build(house(15, 11, 1, 4, 8), SOUTH);
        for (int x = -6; x <= 6; x++) {
            for (int z = -4; z <= 4; z++) {
                int y = 12;
                while (y > 0 && at(blocks, x, y, z) == null) y--;
                assertEquals(TreePart.HOUSE_ROOF, at(blocks, x, y, z), "the top of " + x + "," + z);
            }
        }
    }

    @Test
    void theAddedPartsAreBuiltOnTheBaseWithoutChangingIt() {
        TreeSettings plain = house(11, 9, 2, 4, 1);
        plain.set(TreeSettings.Key.HOUSE_PITCH, 0.75);
        plain.set(TreeSettings.Key.HOUSE_OVERHANG, 1);
        Map<Long, TreePart> base = build(plain, SOUTH);
        int roofTop = highest(base, TreePart.HOUSE_ROOF);

        // a dormer on the front: its face stands on the wall line over the eaves, with a window, and its roof over it
        TreeSettings dormer = plain.copy();
        module(dormer, HouseModule.Type.DORMER, 0);
        Map<Long, TreePart> withDormer = build(dormer, SOUTH);
        assertEquals(TreePart.HOUSE_WALL, at(withDormer, -1, 9, 4));
        assertNull(at(withDormer, 0, 9, 4));
        assertEquals(TreePart.HOUSE_WALL, at(withDormer, 0, 11, 4));
        assertEquals(TreePart.HOUSE_ROOF, at(withDormer, 0, 12, 4));
        assertNull(at(withDormer, 0, 9, 3), "the house's roof is opened behind the window");

        // a chimney on the right (+x seen from the front): stone from the ground to over the roof
        TreeSettings chimney = plain.copy();
        module(chimney, HouseModule.Type.CHIMNEY, 1);
        Map<Long, TreePart> withChimney = build(chimney, SOUTH);
        assertEquals(TreePart.HOUSE_STONE, at(withChimney, 6, 0, 0));
        assertTrue(highest(withChimney, TreePart.HOUSE_STONE) > roofTop);

        // a tower on the left: a stone ring and a cone over the house's roof
        TreeSettings tower = plain.copy();
        module(tower, HouseModule.Type.TOWER, 3);
        Map<Long, TreePart> withTower = build(tower, SOUTH);
        assertTrue(count(withTower, TreePart.HOUSE_STONE) > 30);
        assertTrue(highest(withTower, TreePart.HOUSE_ROOF) > roofTop);

        // a wing out of the front: its walls out past the front, its far end 5 out (z = 4 + 5)
        TreeSettings wing = plain.copy();
        module(wing, HouseModule.Type.WING, 0).height = 2;
        Map<Long, TreePart> withWing = build(wing, SOUTH);
        assertEquals(TreePart.HOUSE_PILLAR, at(withWing, -2, 1, 9));
        assertTrue(highest(withWing, TreePart.HOUSE_ROOF) >= roofTop - 1);
        assertNull(at(withWing, -1, 2, 4), "the house's wall where the wing joins it is opened");

        // a porch, a balcony and a bay window: their floors out from the front
        TreeSettings more = plain.copy();
        module(more, HouseModule.Type.PORCH, 0);
        module(more, HouseModule.Type.BALCONY, 2);
        module(more, HouseModule.Type.BAY, 1);
        Map<Long, TreePart> withMore = build(more, SOUTH);
        assertEquals(TreePart.HOUSE_FLOOR, at(withMore, 0, 0, 6));
        assertEquals(TreePart.HOUSE_FLOOR, at(withMore, 0, 4, -6));
        assertEquals(TreePart.HOUSE_FLOOR, at(withMore, 6, 5, 0));

        // a part turned off is not built; the base itself is never changed by the parts
        more.modules().forEach(module -> module.on = false);
        assertEquals(base, build(more, SOUTH));
        assertNotEquals(base, withMore);
    }

    /** How many roof blocks each column across the ridge has, at {@code x} (the front faces south: the section runs along z). */
    private static String section(Map<Long, TreePart> blocks, int x, int fromZ, int toZ) {
        StringBuilder text = new StringBuilder();
        for (int z = fromZ; z <= toZ; z++) {
            int roof = 0;
            for (int y = -5; y < 60; y++) if (at(blocks, x, y, z) == TreePart.HOUSE_ROOF) roof++;
            text.append(z == fromZ ? "" : " ").append(roof);
        }
        return text.toString();
    }

    @Test
    void theRoofOfStepsHasTheSectionItIsGiven() {
        // nekorozv's "1 1 1 2 3 3 3 2 1 1 1": 9 deep and out by 1 is 11 columns; its half from the eaves to the ridge is the steps
        HouseShape.Steps steps = HouseShape.Steps.of(List.of(1, 1, 1, 2, 3, 3), true);
        int[] heights = new int[11];
        for (int b = 0; b <= 10; b++) heights[b] = HouseShape.roofRise(9, 1, 3, b, 0, 10, 0, 10, 1.0, steps);
        assertEquals(List.of(0, 1, 2, 4, 7, 10, 7, 4, 2, 1, 0), java.util.Arrays.stream(heights).boxed().toList());
        TreeSettings settings = house(9, 9, 1, 4, 9);
        settings.set(TreeSettings.Key.HOUSE_OVERHANG, 1);
        settings.set(TreeSettings.Key.HOUSE_RIDGE, 1);
        assertEquals("1 1 1 2 3 3 3 2 1 1 1", section(build(settings, SOUTH), 0, -5, 5));
        // the hip of steps rises the same way from all four sides
        settings.set(TreeSettings.Key.HOUSE_ROOF, 10);
        Map<Long, TreePart> hip = build(settings, SOUTH);
        assertEquals("1 1 1 2 3 3 3 2 1 1 1", section(hip, 0, -5, 5));
        assertEquals(highest(hip, TreePart.HOUSE_ROOF), 4 + 10);
        // fitted to a narrower roof the steps are squeezed (the sharp top kept); not fitted they are laid from the ridge out
        assertEquals(6, HouseShape.roofRise(9, 1, 0, 3, 0, 6, 0, 6, 1.0, steps));
        assertEquals(8, HouseShape.roofRise(9, 1, 0, 3, 0, 6, 0, 6, 1.0, HouseShape.Steps.of(List.of(1, 1, 1, 2, 3, 3), false)));
        // a first step over 1 hangs the eaves down
        settings.set(TreeSettings.Key.HOUSE_ROOF, 9);
        settings.setRoofSteps(List.of(2, 1, 1, 2, 3, 3));
        Map<Long, TreePart> hanging = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_ROOF, at(hanging, 0, 3, 5));
        assertEquals("2 1 1 2 3 3 3 2 1 1 2", section(hanging, 0, -5, 5));
        // a dormer and a tower on a roof of steps take its steps (the tower's cone half as steep again)
        TreeSettings parts = house(9, 9, 1, 4, 9);
        parts.set(TreeSettings.Key.HOUSE_OVERHANG, 1);
        parts.set(TreeSettings.Key.HOUSE_RIDGE, 1);
        module(parts, HouseModule.Type.TOWER, 3);
        assertTrue(highest(build(parts, SOUTH), TreePart.HOUSE_ROOF) > 4 + 10);
    }

    @Test
    void theStepsAreReadAsTheyAreWrittenAndKept() {
        assertEquals(List.of(1, 1, 1, 2, 3, 3), TreeSettings.parseSteps("1 1 1 2 3 3 3 2 1 1 1"));
        assertEquals(List.of(1, 1, 2, 3), TreeSettings.parseSteps("1,1,2,3"));
        assertEquals(List.of(1, 2, 2, 1), TreeSettings.parseSteps("1 2 2 1"));
        assertNull(TreeSettings.parseSteps("1 a 2"));
        TreeSettings settings = house(9, 9, 1, 4, 9);
        settings.setRoofSteps(List.of(2, 1, 9, 0, -3));
        assertEquals(List.of(2, 1, TreeSettings.MAX_STEP, 0, 0), settings.roofSteps());
        settings.setRoofSteps(List.of());
        assertEquals(TreeSettings.DEFAULT_STEPS, settings.roofSteps());
        settings.setRoofSteps(List.of(1, 2, 3, 4));
        settings.setDesignSeed(4242L);
        TreeSettings read = TreeSettings.fromMap(settings.toMap(), "house");
        assertEquals(List.of(1, 2, 3, 4), read.roofSteps());
        assertEquals(4242L, read.designSeed());
        assertEquals(List.of(1, 2, 3, 4), settings.copy().roofSteps());
    }

    @Test
    void theTurnGoesRoundOneDegreeAtATime() {
        TreeSettings settings = house(11, 9, 1, 4, 1);
        settings.adjust(TreeSettings.Key.HOUSE_ROTATION, -TreeSettings.Key.HOUSE_ROTATION.step());
        assertEquals(359, settings.getInt(TreeSettings.Key.HOUSE_ROTATION));
        settings.adjust(TreeSettings.Key.HOUSE_ROTATION, TreeSettings.Key.HOUSE_ROTATION.bigStep());
        assertEquals(4, settings.getInt(TreeSettings.Key.HOUSE_ROTATION));
        // a small turn: about as many blocks, the walls of a layer touching along a face
        settings.set(TreeSettings.Key.HOUSE_ROTATION, 7);
        Map<Long, TreePart> slanted = build(settings, SOUTH);
        Map<Long, TreePart> square = build(house(11, 9, 1, 4, 1), SOUTH);
        assertTrue(slanted.size() > square.size() * 0.8 && slanted.size() < square.size() * 1.6, slanted.size() + " vs " + square.size());
        for (Map.Entry<Long, TreePart> entry : slanted.entrySet()) {
            int x = TreeVoxelizer.unpackX(entry.getKey());
            int y = TreeVoxelizer.unpackY(entry.getKey());
            int z = TreeVoxelizer.unpackZ(entry.getKey());
            for (int dz = -1; dz <= 1; dz += 2) {
                if (!slanted.containsKey(TreeVoxelizer.pack(x + 1, y, z + dz))) continue;
                assertTrue(slanted.containsKey(TreeVoxelizer.pack(x + 1, y, z)) || slanted.containsKey(TreeVoxelizer.pack(x, y, z + dz)), "edge only at " + x + "," + y + "," + z);
            }
        }
    }

    @Test
    void pillarsCountedFromBothCornersAreTheSameBothWays() {
        // 15 wide, a pillar every 4: from the left corner 0 4 8 12 14; from both corners 0 4 10 14, and the door in the middle
        TreeSettings settings = house(15, 9, 2, 4, 1);
        settings.set(TreeSettings.Key.HOUSE_PILLARS, 4);
        assertEquals(TreePart.HOUSE_PILLAR, at(build(settings, SOUTH), 1, 5, 4));
        settings.set(TreeSettings.Key.HOUSE_PILLAR_MIRROR, 1);
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_PILLAR, at(blocks, -3, 5, 4));
        assertEquals(TreePart.HOUSE_PILLAR, at(blocks, 3, 5, 4));
        assertNull(at(blocks, 0, 1, 4));
        for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
            int x = TreeVoxelizer.unpackX(entry.getKey());
            assertEquals(entry.getValue(), blocks.get(TreeVoxelizer.pack(-x, TreeVoxelizer.unpackY(entry.getKey()), TreeVoxelizer.unpackZ(entry.getKey()))), "at " + x);
        }
    }

    @Test
    void theUpperStoreysJutOutOnAllSidesOnBrackets() {
        TreeSettings settings = house(11, 9, 2, 4, 1);
        settings.set(TreeSettings.Key.HOUSE_JETTY, 1);
        settings.set(TreeSettings.Key.HOUSE_JETTY_SIDES, 1);
        settings.set(TreeSettings.Key.HOUSE_BRACKETS, 1);
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        // the upper storey reaches one out at the left side too (x = -6), over nothing
        assertNotNull(at(blocks, -6, 5, 0));
        assertNull(at(blocks, -6, 1, 0));
        // a bracket under it at each pillar of the wall below: the sides (z = -4: the back corner) and the front (x = -5: the left corner)
        assertEquals(TreePart.HOUSE_BEAM, at(blocks, -6, 3, -4));
        assertEquals(TreePart.HOUSE_BEAM, at(blocks, -5, 3, 5));
        assertNull(at(blocks, -6, 3, -3));
    }

    @Test
    void theWallsAreSetBackBehindTheFrameAndTheWindowsHaveGlass() {
        // one storey of 5: a window in each stretch of wall 2 and 3 over the floor; the front at z = 4, pillars at x = -5, -1, 3, 5
        TreeSettings settings = house(11, 9, 1, 5, 1);
        settings.set(TreeSettings.Key.HOUSE_RECESS, 1);
        settings.set(TreeSettings.Key.HOUSE_GLASS, 1);
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_PILLAR, at(blocks, -1, 1, 4));
        assertNull(at(blocks, -4, 1, 4));
        assertEquals(TreePart.HOUSE_WALL, at(blocks, -4, 1, 3));
        // the sill stays on the line of the frame
        assertEquals(TreePart.HOUSE_WALL, at(blocks, -4, 0, 4));
        // the glass of the window, in the plane of the wall set back
        assertEquals(TreePart.HOUSE_WINDOW, at(blocks, -3, 2, 3));
        assertNull(at(blocks, -3, 2, 4));
    }

    @Test
    void aPlinthRoofsBetweenTheStoreysAndARidgeBeam() {
        TreeSettings settings = house(11, 9, 2, 4, 1);
        settings.set(TreeSettings.Key.HOUSE_OVERHANG, 1);
        settings.set(TreeSettings.Key.HOUSE_RIDGE, 1);
        settings.set(TreeSettings.Key.HOUSE_PLINTH, 2);
        settings.set(TreeSettings.Key.HOUSE_SKIRT, 1);
        settings.set(TreeSettings.Key.HOUSE_RIDGE_BEAM, 1);
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        // the plinth: one out from the walls, 2 high; at the door only its foot
        assertEquals(TreePart.HOUSE_STONE, at(blocks, -6, 0, 0));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, -6, 1, 0));
        assertNull(at(blocks, -6, 2, 0));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 0, 0, 5));
        assertNull(at(blocks, 0, 1, 5));
        // a roof between the storeys at the foot of the upper one, one out
        assertEquals(TreePart.HOUSE_ROOF, at(blocks, 0, 4, 5));
        assertEquals(TreePart.HOUSE_ROOF, at(blocks, -6, 4, 0));
        // the ridge beam over the ridge (8 + 5), out two past the gables and turned up at the tips
        assertEquals(TreePart.HOUSE_BEAM, at(blocks, 0, 14, 0));
        assertEquals(TreePart.HOUSE_BEAM, at(blocks, -8, 14, 0));
        assertEquals(TreePart.HOUSE_BEAM, at(blocks, -8, 15, 0));
        assertEquals(TreePart.HOUSE_BEAM, at(blocks, 8, 15, 0));
        assertNull(at(blocks, -9, 14, 0));
    }

    @Test
    void aVolumeOpensTheHouseRisesThroughItsRoofAndJutsOutOnBrackets() {
        // a volume of 4 storeys, 5 wide and 5 deep, 3 into the house at the front: x -2..2, z 1..5
        TreeSettings settings = house(11, 9, 2, 4, 1);
        HouseModule volume = module(settings, HouseModule.Type.VOLUME, 0);
        volume.offset = -3;
        volume.width = 5;
        volume.depth = 5;
        volume.height = 4;
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        // its walls over the house's roof, its inside open through the house (the house's roof and front wall in it are gone)
        assertNotNull(at(blocks, -2, 13, 3));
        assertNull(at(blocks, 0, 9, 3));
        assertNull(at(blocks, 0, 2, 4));
        // its door in the middle of its front, in front of the house's
        assertNull(at(blocks, 0, 1, 5));
        assertEquals(TreePart.HOUSE_PILLAR, at(blocks, -2, 1, 5));
        // from the second storey out of the right side, on brackets
        TreeSettings jutting = house(11, 9, 2, 4, 1);
        jutting.set(TreeSettings.Key.HOUSE_BRACKETS, 1);
        HouseModule out = module(jutting, HouseModule.Type.VOLUME, 1);
        out.level = 1;
        out.offset = -1;
        out.width = 5;
        out.depth = 4;
        out.height = 1;
        Map<Long, TreePart> hanging = build(jutting, SOUTH);
        assertNotNull(at(hanging, 7, 5, 0));
        for (int y = 0; y < 3; y++) assertNull(at(hanging, 7, y, 0));
        assertEquals(TreePart.HOUSE_BEAM, at(hanging, 7, 3, -2));
    }

    @Test
    void stairsGoUpAlongTheWallToADoorway() {
        // on the right side (x = 5), up toward the back to a landing at z -1..-3, x = 6
        TreeSettings settings = house(11, 9, 2, 4, 1);
        HouseModule stair = module(settings, HouseModule.Type.STAIR, 1);
        stair.along = 2;
        stair.width = 1;
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 6, 4, -2));
        assertNull(at(blocks, 5, 5, -2));
        assertNull(at(blocks, 5, 6, -2));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 6, 3, 0));
        assertNull(at(blocks, 6, 4, 0));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 6, 1, 2));
        assertNull(at(blocks, 6, 2, 2));
    }

    @Test
    void towersAreRoundOctagonalOrSquareAndTheirTopStoreyCanJutOut() {
        // a tower 5 across at the front, half in the wall: x -2..2, z 3..7
        TreeSettings settings = house(11, 9, 2, 4, 1);
        HouseModule tower = module(settings, HouseModule.Type.TOWER, 0);
        assertNull(at(build(settings, SOUTH), 2, 1, 7));
        tower.shape = 2;
        assertEquals(TreePart.HOUSE_STONE, at(build(settings, SOUTH), 2, 1, 7));
        tower.shape = 1;
        Map<Long, TreePart> octagon = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_STONE, at(octagon, 2, 1, 6));
        assertNull(at(octagon, 2, 1, 7));
        // its top storey (from 10, a storey under its head at 14) one out on brackets
        tower.shape = 2;
        tower.jetty = 1;
        Map<Long, TreePart> jutting = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_WALL, at(jutting, 3, 11, 5));
        assertEquals(TreePart.HOUSE_BEAM, at(jutting, 3, 9, 5));
        assertNull(at(jutting, 3, 8, 5));
    }

    @Test
    void partsDoNotOpenHolesInEachOtherOrUnderTheEaves() {
        // (t193) a tower at the front, then a low volume over it: the volume neither takes away nor covers the tower's wall
        TreeSettings settings = house(11, 9, 2, 4, 1);
        module(settings, HouseModule.Type.TOWER, 0);
        HouseModule volume = module(settings, HouseModule.Type.VOLUME, 0);
        volume.offset = -3;
        volume.width = 7;
        volume.depth = 5;
        volume.height = 1;
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_STONE, at(blocks, -2, 1, 5));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 0, 1, 3));
        // the tower is a plain prism: no window in its wall
        for (int y = 0; y < 14; y++) assertEquals(TreePart.HOUSE_STONE, at(blocks, 0, y, 7), "y " + y);
        // a volume higher than the house against its gable: its wall is there under the eaves too
        TreeSettings eaves = house(11, 9, 2, 4, 1);
        eaves.set(TreeSettings.Key.HOUSE_OVERHANG, 1);
        HouseModule tall = module(eaves, HouseModule.Type.VOLUME, 1);
        tall.offset = 1;
        tall.width = 5;
        tall.depth = 3;
        tall.height = 3;
        assertNotNull(at(build(eaves, SOUTH), 6, 9, 0));
    }

    @Test
    void stairsUnderAJettiedStoreyStandOnTheWallOfTheGround() {
        // the back, the upper storey one out (z = -5): the stairs filled back to the ground storey's wall (z = -4)
        TreeSettings settings = house(11, 9, 2, 4, 1);
        settings.set(TreeSettings.Key.HOUSE_JETTY, 1);
        module(settings, HouseModule.Type.STAIR, 2).width = 1;
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 0, 4, -6));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 3, 1, -5));
        assertNull(at(blocks, 0, 5, -5));
        assertNull(at(blocks, 0, 6, -5));
    }

    @Test
    void aPodiumWithATerraceAParapetAndStairsFromTheDoor() {
        // (t195) raised 3 on a podium, a terrace 2 round it: the house x -5..5, z -4..4; the terrace to x -7..7, z -6..6
        TreeSettings settings = house(11, 9, 1, 4, 1);
        settings.set(TreeSettings.Key.HOUSE_RAISE, 3);
        settings.set(TreeSettings.Key.HOUSE_TERRACE, 2);
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_FLOOR, at(blocks, 0, 3, 0));
        for (int y = 0; y < 3; y++) assertEquals(TreePart.HOUSE_STONE, at(blocks, 0, y, 0));
        // the door 3 up; the terrace level with the floor; a parapet on its edge
        assertNull(at(blocks, 0, 4, 4));
        assertNull(at(blocks, 0, 5, 4));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, -7, 3, 0));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, -7, 4, 0));
        assertNull(at(blocks, -8, 3, 0));
        // the stairs: on from the door over the terrace, then one lower each block, 3 wide; no parapet across them
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 0, 2, 7));
        assertNull(at(blocks, 0, 3, 7));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 1, 0, 9));
        assertNull(at(blocks, 1, 1, 9));
        assertNull(at(blocks, 0, 4, 6));
        // without a podium nothing of it
        settings.set(TreeSettings.Key.HOUSE_RAISE, 0);
        assertEquals(TreePart.HOUSE_FLOOR, at(build(settings, SOUTH), 0, 0, 0));
    }

    @Test
    void volumesStandOnFoundationsOfTheirOwnHeights() {
        // a volume on the right, its floor 2 over the house's: on a podium of its own (x 5..9, z -2..2)
        TreeSettings settings = house(11, 9, 1, 4, 1);
        HouseModule volume = module(settings, HouseModule.Type.VOLUME, 1);
        volume.width = 5;
        volume.depth = 5;
        volume.height = 1;
        volume.raise = 2;
        Map<Long, TreePart> blocks = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 7, 0, 0));
        assertEquals(TreePart.HOUSE_STONE, at(blocks, 7, 1, 0));
        assertEquals(TreePart.HOUSE_FLOOR, at(blocks, 7, 2, 0));
        assertEquals(TreePart.HOUSE_FLOOR, at(blocks, 0, 0, 0));
        // the house on a podium of 2 with a terrace of 1, the volume one lower: each terrace as high as its own (a step between them)
        settings.set(TreeSettings.Key.HOUSE_RAISE, 2);
        settings.set(TreeSettings.Key.HOUSE_TERRACE, 1);
        volume.raise = -1;
        Map<Long, TreePart> stepped = build(settings, SOUTH);
        assertEquals(TreePart.HOUSE_FLOOR, at(stepped, 7, 1, 0));
        assertEquals(TreePart.HOUSE_STONE, at(stepped, 10, 1, 0));
        assertNull(at(stepped, 10, 3, 0));
        assertEquals(TreePart.HOUSE_STONE, at(stepped, -6, 2, 0));
    }

    @Test
    void thePartsAreSavedAndCopiedWithTheSettings() {
        TreeSettings settings = house(11, 9, 2, 4, 6);
        HouseModule tower = module(settings, HouseModule.Type.TOWER, 3);
        tower.along = -2;
        tower.level = 1;
        module(settings, HouseModule.Type.DORMER, 0).on = false;
        TreeSettings read = TreeSettings.fromMap(settings.toMap(), "house");
        assertEquals(2, read.modules().size());
        assertEquals(settings.modules().get(0).toMap(), read.modules().get(0).toMap());
        assertFalse(read.modules().get(1).on);
        assertEquals(6, read.getInt(TreeSettings.Key.HOUSE_ROOF));
        // the copy the generation reads is its own: a change in the menu afterwards does not reach it
        TreeSettings copy = settings.copy();
        settings.modules().get(0).width = 9;
        settings.modules().clear();
        assertEquals(2, copy.modules().size());
        assertEquals(HouseModule.Type.TOWER.width, copy.modules().get(0).width);
        // (t190) the numbers only some kinds have
        TreeSettings more = house(11, 9, 2, 4, 1);
        module(more, HouseModule.Type.TOWER, 0).shape = 1;
        HouseModule volume = module(more, HouseModule.Type.VOLUME, 2);
        volume.roof = 9;
        volume.ridge = 2;
        volume.jetty = 2;
        TreeSettings again = TreeSettings.fromMap(more.toMap(), "house");
        assertEquals(more.modules().get(1).toMap(), again.modules().get(1).toMap());
        assertEquals(1, again.modules().get(0).shape);
        assertEquals(-1, new HouseModule(HouseModule.Type.VOLUME).jetty);
        // a part of a kind that is not known is left out; out of range is brought in
        TreeSettings odd = TreeSettings.fromMap(Map.of("kind", "house", "house-modules", List.of(Map.of("type", "moat"), Map.of("type", "chimney", "width", 99))), "odd");
        assertEquals(1, odd.modules().size());
        assertEquals(HouseModule.MAX_WIDTH, odd.modules().get(0).width);
    }

    @Test
    void aVolumeLowerThanTheHouseTakesNothingOfTheHouseAboveItsStoreys() {
        // (t250) a storey of volume set 2 into the side of a house of 2: the house's wall, floor and roof over its storey stay whole
        TreeSettings bare = house(11, 9, 2, 4, 1);
        bare.set(TreeSettings.Key.HOUSE_WINDOWS, 0);
        Map<Long, TreePart> house = build(bare, SOUTH);
        TreeSettings joined = house(11, 9, 2, 4, 1);
        joined.set(TreeSettings.Key.HOUSE_WINDOWS, 0);
        HouseModule volume = new HouseModule(HouseModule.Type.VOLUME);
        volume.side = 1;
        volume.offset = -2;
        volume.width = 5;
        volume.depth = 5;
        volume.height = 1;
        volume.roof = 1;
        volume.ridge = 2;
        joined.modules().add(volume);
        Map<Long, TreePart> with = build(joined, SOUTH);
        for (Map.Entry<Long, TreePart> entry : house.entrySet()) {
            if (TreeVoxelizer.unpackY(entry.getKey()) >= 4) assertNotNull(with.get(entry.getKey()), "lost " + TreeVoxelizer.unpackX(entry.getKey()) + ","
                    + TreeVoxelizer.unpackY(entry.getKey()) + "," + TreeVoxelizer.unpackZ(entry.getKey()) + " " + entry.getValue());
        }
        // its own storey still opens into the house (the house's wall where it joins it is gone under its eaves)
        long opened = house.keySet().stream().filter(key -> TreeVoxelizer.unpackY(key) > 0 && TreeVoxelizer.unpackY(key) < 4 && !with.containsKey(key)).count();
        assertTrue(opened > 0);
    }

    @Test
    void theRoomOfAPartIsNotFilledByAPartAfterIt() {
        // (t250) a tower, then a volume (of a lower rank) across it and a chimney through it: nothing of them inside the tower
        TreeSettings settings = house(11, 9, 2, 4, 1);
        HouseModule tower = new HouseModule(HouseModule.Type.TOWER);
        tower.side = 0;
        tower.width = 7;
        tower.offset = -2;
        tower.height = 6;
        settings.modules().add(tower);
        Map<Long, TreePart> alone = build(settings, SOUTH);
        HouseModule volume = new HouseModule(HouseModule.Type.VOLUME);
        volume.side = 0;
        volume.offset = -1;
        volume.width = 9;
        volume.depth = 5;
        volume.height = 2;
        HouseModule chimney = new HouseModule(HouseModule.Type.CHIMNEY);
        chimney.side = 0;
        chimney.along = 1;
        chimney.offset = 1;
        chimney.width = 2;
        chimney.depth = 2;
        chimney.height = 3;
        settings.modules().add(volume);
        settings.modules().add(chimney);
        Map<Long, TreePart> with = build(settings, SOUTH);
        // the tower's middle: on the front's middle, 2 out of the wall (the house 11 by 9 set round its middle; the front at z 4)
        int inside = 0;
        for (int x = -3; x <= 3; x++) {
            for (int z = 3; z <= 9; z++) {
                if (Math.hypot(x, z - 6) > 2.5) continue;
                for (int y = 1; y < 14; y++) {
                    long key = TreeVoxelizer.pack(x, y, z);
                    if (alone.containsKey(key)) continue;
                    inside++;
                    assertNull(with.get(key), "in the tower at " + x + "," + y + "," + z);
                }
            }
        }
        assertTrue(inside > 100, Integer.toString(inside));
    }
}
