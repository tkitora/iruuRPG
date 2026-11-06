package net.tkgon.mc.iruuRPG.tools.procedural.ornate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t282: the shapes of the blocks, the table of the 256 octant masks and the joins of faces. */
class BlockShapesTest {

    /** The octant bit of (x, y, z), each 0 or 1. */
    private static int octant(int x, int y, int z) {
        return 1 << (x + 2 * y + 4 * z);
    }

    @Test
    void theOctantsOfEachShapeAreTheDesignsCounts() {
        assertEquals(8, Integer.bitCount(BlockShapes.FULL.octants));
        assertEquals(4, Integer.bitCount(BlockShapes.SLAB_BOTTOM.octants));
        assertEquals(4, Integer.bitCount(BlockShapes.SLAB_TOP.octants));
        int[] counts = {6, 7, 7, 5, 5};
        for (int half = 0; half < 2; half++) {
            for (int facing = 0; facing < 4; facing++) {
                for (int shape = 0; shape < 5; shape++) assertEquals(counts[shape], Integer.bitCount(BlockShapes.stairs(half, facing, shape).octants));
            }
        }
        // (a fence, a wall, a trapdoor fill no octant to half)
        assertEquals(0, BlockShapes.FENCE.octants);
        assertEquals(0, BlockShapes.TRAPDOOR_BOTTOM.octants);
    }

    @Test
    void theStairsAreTheGamesShapes() {
        // facing north, bottom, straight: the bottom half and the north (-z) half of the top
        int straight = BlockShapes.SLAB_BOTTOM.octants | octant(0, 1, 0) | octant(1, 1, 0);
        assertEquals(straight, BlockShapes.stairs(0, 0, 0).octants);
        // inner_left facing north: the north half and the south-west quarter on top
        assertEquals(straight | octant(0, 1, 1), BlockShapes.stairs(0, 0, 1).octants);
        // outer_left facing north: the north-west quarter on top
        assertEquals(BlockShapes.SLAB_BOTTOM.octants | octant(0, 1, 0), BlockShapes.stairs(0, 0, 3).octants);
        // facing east, top half: the top half and the east (+x) half of the bottom
        assertEquals(BlockShapes.SLAB_TOP.octants | octant(1, 0, 0) | octant(1, 0, 1), BlockShapes.stairs(1, 1, 0).octants);
        assertEquals("facing=east,half=top,shape=straight", BlockShapes.stairs(1, 1, 0).props);
    }

    @Test
    void theMasksGoToTheNearestShape() {
        assertSame(BlockShapes.FULL, BlockShapes.nearest(0xFF));
        assertSame(BlockShapes.EMPTY, BlockShapes.nearest(0));
        assertSame(BlockShapes.EMPTY, BlockShapes.nearest(octant(1, 1, 1)));
        assertSame(BlockShapes.SLAB_BOTTOM, BlockShapes.nearest(BlockShapes.SLAB_BOTTOM.octants));
        assertSame(BlockShapes.SLAB_TOP, BlockShapes.nearest(BlockShapes.SLAB_TOP.octants));
        // three octants of the bottom: the bottom slab
        assertSame(BlockShapes.SLAB_BOTTOM, BlockShapes.nearest(octant(0, 0, 0) | octant(1, 0, 0) | octant(0, 0, 1)));
        for (int half = 0; half < 2; half++) {
            for (int facing = 0; facing < 4; facing++) {
                for (int shape = 0; shape < 5; shape++) {
                    BlockShapes.Shape stair = BlockShapes.stairs(half, facing, shape);
                    // (an inner corner is the same block as the inner corner of the other hand turned a quarter)
                    assertEquals(stair.octants, BlockShapes.nearest(stair.octants).octants, stair.toString());
                    assertSame(BlockShapes.Kind.STAIRS, BlockShapes.nearest(stair.octants).kind);
                }
            }
        }
        // a mask one octant off a shape goes to a shape at most one octant off (a full block with a notch, a slab with a bump)
        for (int mask = 0; mask < 256; mask++) {
            boolean near = false;
            for (BlockShapes.Shape shape : BlockShapes.all()) {
                if ((shape.kind == BlockShapes.Kind.FULL || shape.kind == BlockShapes.Kind.SLAB || shape.kind == BlockShapes.Kind.STAIRS) && Integer.bitCount(mask ^ shape.octants) == 1) near = true;
            }
            if (near) assertTrue(Integer.bitCount(mask ^ BlockShapes.nearest(mask).octants) <= 1, Integer.toBinaryString(mask));
        }
    }

    @Test
    void facesJoinOnlyWhenTheyShareEnoughInOnePieceAndNotThin() {
        int west = 0, east = 1, up = 3, north = 4;
        // fences side by side: their thin bars are no join
        assertFalse(BlockShapes.joined(BlockShapes.FENCE, east, BlockShapes.FENCE));
        // a fence on a bottom slab: the slab does not reach the top of its block
        assertFalse(BlockShapes.joined(BlockShapes.SLAB_BOTTOM, up, BlockShapes.FENCE));
        // fences one on another, a fence on a top slab, on a log, on a wall (4 x 4 = 16 pixels)
        assertTrue(BlockShapes.joined(BlockShapes.FENCE, up, BlockShapes.FENCE));
        assertTrue(BlockShapes.joined(BlockShapes.SLAB_TOP, up, BlockShapes.FENCE));
        assertTrue(BlockShapes.joined(BlockShapes.FULL, up, BlockShapes.FENCE));
        assertTrue(BlockShapes.joined(BlockShapes.WALL, up, BlockShapes.FENCE));
        // a fence beside a log: the bars are thin
        assertFalse(BlockShapes.joined(BlockShapes.FULL, east, BlockShapes.FENCE));
        // walls beside each other (their arms), slabs of the same half, trapdoors on slabs of the same half
        assertTrue(BlockShapes.joined(BlockShapes.WALL, north, BlockShapes.WALL));
        assertTrue(BlockShapes.joined(BlockShapes.SLAB_BOTTOM, east, BlockShapes.SLAB_BOTTOM));
        assertFalse(BlockShapes.joined(BlockShapes.SLAB_BOTTOM, east, BlockShapes.SLAB_TOP));
        assertTrue(BlockShapes.joined(BlockShapes.SLAB_BOTTOM, west, BlockShapes.TRAPDOOR_BOTTOM));
        assertFalse(BlockShapes.joined(BlockShapes.SLAB_TOP, west, BlockShapes.TRAPDOOR_BOTTOM));
        // an open gate facing north has its posts on the west and east: joined to the next along x, not along z
        assertTrue(BlockShapes.joined(BlockShapes.gate(0), east, BlockShapes.gate(0)));
        assertFalse(BlockShapes.joined(BlockShapes.gate(0), north, BlockShapes.gate(0)));
        // the open trapdoor against the east side of its block stands on the face it touches
        assertTrue(BlockShapes.joined(BlockShapes.FULL, west, BlockShapes.trapdoorAgainst(1)));
        assertFalse(BlockShapes.joined(BlockShapes.FULL, east, BlockShapes.trapdoorAgainst(1)));
        // a stair climbing east joins the next stair east and the one above its high half
        BlockShapes.Shape climb = BlockShapes.stairs(0, 1, 0);
        assertTrue(BlockShapes.joined(climb, east, climb));
        assertTrue(BlockShapes.joined(climb, up, climb));
    }

    @Test
    void joinsLookTheSameFromBothSides() {
        for (BlockShapes.Shape a : BlockShapes.all()) {
            for (BlockShapes.Shape b : BlockShapes.all()) {
                for (int face = 0; face < 6; face++) assertEquals(BlockShapes.joined(a, face, b), BlockShapes.joined(b, face ^ 1, a), a + " " + face + " " + b);
            }
        }
    }
}
