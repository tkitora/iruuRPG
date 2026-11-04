package net.tkgon.mc.iruuRPG.tools.procedural;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t263: a left click with the tool opens the menu and leaves the place as it was; a right click chooses the place. */
class ProceduralClickTest {

    @Test
    void leftClickKeepsThePlace() {
        assertTrue(ProceduralModule.menuOnly(true, false, true));
    }

    @Test
    void rightClickChoosesThePlace() {
        assertFalse(ProceduralModule.menuOnly(false, false, true));
        assertFalse(ProceduralModule.menuOnly(false, false, false));
    }

    @Test
    void leftClickChoosesThePlaceWhenThereIsNone() {
        assertFalse(ProceduralModule.menuOnly(true, false, false));
    }

    @Test
    void webClicksStillPutPoints() {
        assertFalse(ProceduralModule.menuOnly(true, true, true));
        assertFalse(ProceduralModule.menuOnly(true, true, false));
    }
}
