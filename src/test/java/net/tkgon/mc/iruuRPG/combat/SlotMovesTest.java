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
        // the ranger: 小 the piercing shot that sets off its traps (2), 中 the rapid shots (6), 大 the rain of arrows (5), util the backstep shot (1)
        assertArrayEquals(new int[]{2, 6, 5, 1}, SlotMoves.skillNumbers("gifted", AttackType.RANGE));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("gifted", AttackType.DEPLOY));
        // the warrior: 小 the colossus strike (4), 中 the whirlwind (3), 大 the shield bash and bulwark (1), util the shield rush (5)
        assertArrayEquals(new int[]{4, 3, 1, 5}, SlotMoves.skillNumbers("warrior", AttackType.MELEE));
        // a class whose moves are not made yet
        assertNull(SlotMoves.skillNumbers("assassin", AttackType.MELEE));
        assertNull(SlotMoves.favoriteSkillNumbers(null));
    }
}
