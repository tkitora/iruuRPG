package net.tkgon.mc.iruuRPG.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The classes' moves of their skills (the redesign of 2026-11-02): which skills of t138 they throw. */
class SlotMovesTest {

    @Test
    void theMageThrowsItsOwnWithARangedWeaponAndTheTemplateWithAnother() {
        // 小 the chain of lightning (6), 中 the crystal (4), 大 the black hole (5), util the re-activation (1)
        assertArrayEquals(new int[]{6, 4, 5, 1}, SlotMoves.skillNumbers("mage", AttackType.RANGE));
        assertArrayEquals(new int[]{6, 4, 5, 1}, SlotMoves.favoriteSkillNumbers("mage"));
        // another weapon: skills 1 - 4
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("mage", AttackType.MELEE));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("mage", AttackType.DEPLOY));
        // a class whose moves are not made yet
        assertNull(SlotMoves.skillNumbers("warrior", AttackType.MELEE));
        assertNull(SlotMoves.favoriteSkillNumbers(null));
    }
}
