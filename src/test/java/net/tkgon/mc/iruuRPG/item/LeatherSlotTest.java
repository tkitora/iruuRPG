package net.tkgon.mc.iruuRPG.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** material: "leather:helm:29, 179, 154" is the leather helmet (not the leather material item). */
class LeatherSlotTest {

    @Test
    void slotWordsGiveTheLeatherArmor() {
        assertEquals("LEATHER_HELMET", RpgItemRegistry.leatherSlot("helm"));
        assertEquals("LEATHER_CHESTPLATE", RpgItemRegistry.leatherSlot("Chest"));
        assertEquals("LEATHER_LEGGINGS", RpgItemRegistry.leatherSlot("legs"));
        assertEquals("LEATHER_BOOTS", RpgItemRegistry.leatherSlot("boots"));
        assertNull(RpgItemRegistry.leatherSlot("29, 179, 154"), "a color is not a slot");
        assertNull(RpgItemRegistry.leatherSlot("glint"));
    }
}
