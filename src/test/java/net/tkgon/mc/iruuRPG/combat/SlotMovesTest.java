package net.tkgon.mc.iruuRPG.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The classes' moves of their skills (the redesign of 2026-11-02): which skills of t138 they throw. */
class SlotMovesTest {

    @Test
    void theMageThrowsItsOwnWithARangedWeaponAndTheTemplateWithAnother() {
        // 小 the chain of lightning (6), 中 the crystal (4), 大 the black hole (5), util the re-activation (1)
        assertArrayEquals(new int[]{6, 4, 5, 1}, SlotMoves.skillNumbers("mage", AttackType.RANGE));
        assertEquals(AttackType.RANGE, SlotMoves.favorite("mage"));
        // another weapon: skills 1 - 4
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("mage", AttackType.MELEE));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("mage", AttackType.DEPLOY));
        // the ranger: 小 the piercing shot that sets off its traps (2), 中 the rapid shots (6), 大 the rain of arrows (5), util the backstep shot (1)
        assertArrayEquals(new int[]{2, 6, 5, 1}, SlotMoves.skillNumbers("gifted", AttackType.RANGE));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("gifted", AttackType.DEPLOY));
        // the warrior: 小 the colossus strike (4), 中 the whirlwind (3), 大 the shield bash and bulwark (1), util the shield rush (5)
        assertArrayEquals(new int[]{4, 3, 1, 5}, SlotMoves.skillNumbers("warrior", AttackType.MELEE));
        // the cleric: 小 the shield bash (2), 中 the pillar of light (4), 大 the spinning prayer (3), util the sanctuary (1)
        assertArrayEquals(new int[]{2, 4, 3, 1}, SlotMoves.skillNumbers("healer", AttackType.MELEE));
        // the assassin's melee skills are its own since t161 (no old skill numbers); with a ranged weapon the template's
        assertTrue(SlotMoves.reworked("assassin", AttackType.MELEE));
        assertFalse(SlotMoves.reworked("assassin", AttackType.RANGE));
        assertFalse(SlotMoves.reworked("mage", AttackType.RANGE));
        assertNull(SlotMoves.skillNumbers("assassin", AttackType.MELEE));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("assassin", AttackType.RANGE));
        // a class with no moves (no class)
        assertNull(SlotMoves.skillNumbers("nobody", AttackType.MELEE));
        assertNull(SlotMoves.favorite(null));
    }

    /** t161: the assassin's melee 小 has 3 charges (3 s each, 8 s when all are used) and 中 a re-activation of 3 s; from the config, the others none. */
    @Test
    void theAssassinsMeleeSkillsHaveTheirChargesAndReActivation() {
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        assertEquals(new SkillSlots.Mechanics(3, 0.0, 3.0, 8.0), AssassinSkills.mechanics(config, SkillSlots.Slot.SMALL, AttackType.MELEE));
        assertEquals(new SkillSlots.Mechanics(1, 3.0, 0.0, 0.0), AssassinSkills.mechanics(config, SkillSlots.Slot.MEDIUM, AttackType.MELEE));
        assertEquals(SkillSlots.Mechanics.NONE, AssassinSkills.mechanics(config, SkillSlots.Slot.LARGE, AttackType.MELEE));
        assertEquals(SkillSlots.Mechanics.NONE, AssassinSkills.mechanics(config, SkillSlots.Slot.SMALL, AttackType.RANGE));
        // (the same without the config: the defaults of the code)
        assertEquals(new SkillSlots.Mechanics(3, 0.0, 3.0, 8.0), AssassinSkills.mechanics(new org.bukkit.configuration.file.YamlConfiguration(), SkillSlots.Slot.SMALL, AttackType.MELEE));
    }
}
