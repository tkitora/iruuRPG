package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The house base of the procedural tool (t179): pillars, walls with a door and windows, floors and the roofs, the front toward the player. */
class HouseShapeTest {

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

    private static TreePart at(Map<Long, TreePart> blocks, int x, int y, int z) {
        return blocks.get(TreeVoxelizer.pack(x, y, z));
    }

    @Test
    void aHouseHasPillarsWallsFloorsADoorAndARoof() {
        // 11 wide, 9 deep, 2 storeys of 4, a gable, the player to the south (+z): the front is at z = 4, x from -5 to 5
        Map<Long, TreePart> blocks = ProceduralShapes.build(ProcKind.HOUSE, house(11, 9, 2, 4, 1), 1L, new Random(1), null, new Vector(0.0, 0.0, 1.0));
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
        Map<Long, TreePart> blocks = ProceduralShapes.build(ProcKind.HOUSE, house(11, 9, 1, 4, 0), 1L, new Random(1), null, new Vector(1.0, 0.0, 0.0));
        assertNull(at(blocks, 4, 1, 0));
        assertEquals(TreePart.HOUSE_WALL, at(blocks, -4, 1, 0));
        // a flat roof on top
        assertEquals(TreePart.HOUSE_ROOF, at(blocks, 0, 4, 0));
    }

    @Test
    void theRoofsRiseAsTheirShapeSays() {
        // 10 x 6 with no overhang, pitch 1
        assertEquals(0, ProceduralShapes.roofRise(0, 3, 3, 10, 6, 0, 1.0));
        // gable: across the 6 deep side; the eaves 0, the middle 2
        assertEquals(0, ProceduralShapes.roofRise(1, 4, 0, 10, 6, 0, 1.0));
        assertEquals(2, ProceduralShapes.roofRise(1, 0, 2, 10, 6, 0, 1.0));
        // hip: from every side, so the ends slope too
        assertEquals(0, ProceduralShapes.roofRise(2, 0, 2, 10, 6, 0, 1.0));
        assertEquals(2, ProceduralShapes.roofRise(2, 4, 2, 10, 6, 0, 1.0));
        // shed: from the front (the high b) up to the back at half the pitch
        assertEquals(0, ProceduralShapes.roofRise(3, 4, 5, 10, 6, 0, 1.0));
        assertTrue(ProceduralShapes.roofRise(3, 4, 0, 10, 6, 0, 1.0) > 0);
        assertFalse(ProceduralShapes.roofName(1).isEmpty());
    }
}
