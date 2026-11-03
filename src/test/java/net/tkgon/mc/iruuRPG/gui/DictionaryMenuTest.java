package net.tkgon.mc.iruuRPG.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** (t254) The admins' dictionary: the search and the kinds of item. */
class DictionaryMenuTest {

    @Test
    void aSearchFindsEveryWordInTheIdOrTheNameHoweverTyped() {
        assertTrue(DictionaryMenu.matches("bone", "bone_dagger", "骨の短剣"));
        assertTrue(DictionaryMenu.matches("短剣", "bone_dagger", "骨の短剣"));
        // full width and capitals are the same; every word must be there
        assertTrue(DictionaryMenu.matches("ＢＯＮＥ  短剣", "bone_dagger", "骨の短剣"));
        assertFalse(DictionaryMenu.matches("bone 剣士", "bone_dagger", "骨の短剣"));
        // colour codes in a name are not searched
        assertTrue(DictionaryMenu.matches("スライム", "slime", "§aスライム"));
        assertTrue(DictionaryMenu.matches("", "anything", "何でも"));
    }

    @Test
    void anItemIsOfAKindByTheWordsOnIt() {
        assertEquals(DictionaryMenu.ItemKind.MELEE, DictionaryMenu.ItemKind.of("物理", "近接"));
        assertEquals(DictionaryMenu.ItemKind.RANGE, DictionaryMenu.ItemKind.of("魔法", "遠距離"));
        assertEquals(DictionaryMenu.ItemKind.DEPLOY, DictionaryMenu.ItemKind.of("魔法", "設置"));
        assertEquals(DictionaryMenu.ItemKind.ARMOR, DictionaryMenu.ItemKind.of("防具", ""));
        assertEquals(DictionaryMenu.ItemKind.MATERIAL, DictionaryMenu.ItemKind.of("素材", ""));
        assertEquals(DictionaryMenu.ItemKind.OTHER, DictionaryMenu.ItemKind.of("", ""));
        assertEquals(DictionaryMenu.ItemKind.OTHER, DictionaryMenu.ItemKind.of("物理", "特殊"));
    }
}
