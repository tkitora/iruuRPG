package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        // the timber frame: a beam at the foot of the upper storey's walls, braces in them
        assertEquals(TreePart.HOUSE_PILLAR, at(blocks, 2, 4, 5));
        assertTrue(count(blocks, TreePart.HOUSE_PILLAR) > count(build(house(11, 9, 2, 4, 1), SOUTH), TreePart.HOUSE_PILLAR) + 20);
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
        // a part of a kind that is not known is left out; out of range is brought in
        TreeSettings odd = TreeSettings.fromMap(Map.of("kind", "house", "house-modules", List.of(Map.of("type", "moat"), Map.of("type", "chimney", "width", 99))), "odd");
        assertEquals(1, odd.modules().size());
        assertEquals(HouseModule.MAX_WIDTH, odd.modules().get(0).width);
    }
}
