package net.tkgon.mc.iruuRPG.tools.sea;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t302: the sea's columns: a round reach, and a column's key both ways (negative places too). */
class SeaViewTest {

    @Test
    void theSeaIsRoundAndItsColumnsAreKeptByPlace() {
        assertTrue(SeaView.within(48, 0, 48));
        assertTrue(SeaView.within(0, -48, 48));
        assertFalse(SeaView.within(40, 40, 48));
        assertFalse(SeaView.within(49, 0, 48));
        for (int[] at : new int[][]{{0, 0}, {-5, 7}, {30000000, -30000000}, {-1, -1}}) {
            long key = SeaView.key(at[0], at[1]);
            assertEquals(at[0], SeaView.x(key));
            assertEquals(at[1], SeaView.z(key));
        }
    }
}
