package net.tkgon.mc.iruuRPG.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The rarity of a crafted item from before rarities were stored is read from the marks of its name (no server needed). */
class ItemPatcherNameTest {

    @Test
    void rarityMarksAreReadFromTheName() {
        assertEquals(3, ItemPatcher.rarityMarks("剣 [***]", "*"));
        assertEquals(0, ItemPatcher.rarityMarks("剣", "*"));
        assertEquals(2, ItemPatcher.rarityMarks("剣 [★★]", "★"));
        assertEquals(2, ItemPatcher.rarityMarks("剣 [🙥🙥]", "🙥"));
        assertNull(ItemPatcher.rarityMarks("剣 [**]", "★"), "another mark: cannot be told");
    }
}
