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
        // its own with a ranged weapon since t168 (the arcana: no old skill numbers)
        assertTrue(SlotMoves.reworked("mage", AttackType.RANGE));
        assertNull(SlotMoves.skillNumbers("mage", AttackType.RANGE));
        assertEquals(AttackType.RANGE, SlotMoves.favorite("mage"));
        // a scythe: the reaper's own since t167 (no old skill numbers); a deployed weapon: skills 1 - 4
        assertTrue(SlotMoves.reworked("mage", AttackType.MELEE));
        assertNull(SlotMoves.skillNumbers("mage", AttackType.MELEE));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("mage", AttackType.DEPLOY));
        // the ranger: its own with a melee weapon (the knife and wire) and with the bow (小 the power shot; the others its old 6, 5, 1) since t168
        assertTrue(SlotMoves.reworked("gifted", AttackType.MELEE));
        assertTrue(SlotMoves.reworked("gifted", AttackType.RANGE));
        assertNull(SlotMoves.skillNumbers("gifted", AttackType.RANGE));
        assertEquals(AttackType.RANGE, SlotMoves.favorite("gifted"));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("gifted", AttackType.DEPLOY));
        // the warrior: its own with a melee weapon (the blood knight) and a ranged one (the swords) since t168; a deployed weapon: skills 1 - 4
        assertTrue(SlotMoves.reworked("warrior", AttackType.MELEE));
        assertTrue(SlotMoves.reworked("warrior", AttackType.RANGE));
        assertFalse(SlotMoves.reworked("warrior", AttackType.DEPLOY));
        assertNull(SlotMoves.skillNumbers("warrior", AttackType.MELEE));
        assertNull(SlotMoves.skillNumbers("warrior", AttackType.RANGE));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("warrior", AttackType.DEPLOY));
        // the cleric: its own with a melee weapon (the mace and shield) and a ranged one (the staff) since t168
        assertTrue(SlotMoves.reworked("healer", AttackType.MELEE));
        assertTrue(SlotMoves.reworked("healer", AttackType.RANGE));
        assertNull(SlotMoves.skillNumbers("healer", AttackType.MELEE));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("healer", AttackType.DEPLOY));
        // the assassin's melee skills are its own since t161 (no old skill numbers); with a ranged weapon the template's
        assertTrue(SlotMoves.reworked("assassin", AttackType.MELEE));
        // (the shadow bow is its own too since t168)
        assertTrue(SlotMoves.reworked("assassin", AttackType.RANGE));
        assertNull(SlotMoves.skillNumbers("assassin", AttackType.MELEE));
        assertNull(SlotMoves.skillNumbers("assassin", AttackType.RANGE));
        assertArrayEquals(new int[]{1, 2, 3, 4}, SlotMoves.skillNumbers("assassin", AttackType.DEPLOY));
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
        // (the shadow bow's: 小 3 charges of 2.5 s / 7 s, 中 a re-activation of 3 s and 11 s, 大 22 s, util 6 s; t168)
        assertEquals(new SkillSlots.Mechanics(3, 0.0, 2.5, 7.0), AssassinSkills.mechanics(config, SkillSlots.Slot.SMALL, AttackType.RANGE));
        assertEquals(new SkillSlots.Mechanics(1, 3.0, 0.0, 11.0), AssassinSkills.mechanics(config, SkillSlots.Slot.MEDIUM, AttackType.RANGE));
        assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 22.0), AssassinSkills.mechanics(new org.bukkit.configuration.file.YamlConfiguration(), SkillSlots.Slot.LARGE, AttackType.RANGE));
        assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 6.0), AssassinSkills.mechanics(config, SkillSlots.Slot.UTIL, AttackType.RANGE));
        assertEquals(SkillSlots.Mechanics.NONE, AssassinSkills.mechanics(config, SkillSlots.Slot.SMALL, AttackType.DEPLOY));
        // (the same without the config: the defaults of the code)
        assertEquals(new SkillSlots.Mechanics(3, 0.0, 3.0, 8.0), AssassinSkills.mechanics(new org.bukkit.configuration.file.YamlConfiguration(), SkillSlots.Slot.SMALL, AttackType.MELEE));
    }

    /**
     * t167: the reaper's 小 has 2 charges (4 s each, 9 s when both are used), 中 a re-activation of 3 s and 10 s, 大 22 s, util 5 s; its own
     * times, not the class's (the mage's ranged skills keep those). The code's defaults are the config's (the server's config has none of them).
     */
    @Test
    void theReapersSkillsHaveTheirChargesAndTimes() {
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        org.bukkit.configuration.file.YamlConfiguration none = new org.bukkit.configuration.file.YamlConfiguration();
        for (org.bukkit.configuration.file.YamlConfiguration each : java.util.List.of(config, none)) {
            assertEquals(new SkillSlots.Mechanics(2, 0.0, 4.0, 9.0), MageReaper.mechanics(each, SkillSlots.Slot.SMALL, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 3.0, 0.0, 10.0), MageReaper.mechanics(each, SkillSlots.Slot.MEDIUM, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 22.0), MageReaper.mechanics(each, SkillSlots.Slot.LARGE, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 5.0), MageReaper.mechanics(each, SkillSlots.Slot.UTIL, AttackType.MELEE));
        }
        assertEquals(SkillSlots.Mechanics.NONE, MageReaper.mechanics(config, SkillSlots.Slot.SMALL, AttackType.RANGE));
        assertEquals(SkillSlots.Mechanics.NONE, MageReaper.mechanics(config, SkillSlots.Slot.LARGE, AttackType.DEPLOY));
    }

    /**
     * t168: the blood knight's 小 has 2 charges (5 s each, 10 s for both), 中 a re-activation of 3 s and 11 s, 大 26 s, util 6 s; the swords' 小 3
     * charges (3.5 s, 9 s), 中 a re-activation of 4 s and 10 s, 大 24 s, util 6 s; a deployed weapon none. The code's defaults are the config's.
     */
    @Test
    void theWarriorsSkillsHaveTheirChargesAndTimes() {
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        org.bukkit.configuration.file.YamlConfiguration none = new org.bukkit.configuration.file.YamlConfiguration();
        for (org.bukkit.configuration.file.YamlConfiguration each : java.util.List.of(config, none)) {
            assertEquals(new SkillSlots.Mechanics(2, 0.0, 5.0, 10.0), WarriorSkills.mechanics(each, SkillSlots.Slot.SMALL, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 3.0, 0.0, 11.0), WarriorSkills.mechanics(each, SkillSlots.Slot.MEDIUM, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 26.0), WarriorSkills.mechanics(each, SkillSlots.Slot.LARGE, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 6.0), WarriorSkills.mechanics(each, SkillSlots.Slot.UTIL, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(3, 0.0, 3.5, 9.0), WarriorSkills.mechanics(each, SkillSlots.Slot.SMALL, AttackType.RANGE));
            assertEquals(new SkillSlots.Mechanics(1, 4.0, 0.0, 10.0), WarriorSkills.mechanics(each, SkillSlots.Slot.MEDIUM, AttackType.RANGE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 24.0), WarriorSkills.mechanics(each, SkillSlots.Slot.LARGE, AttackType.RANGE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 6.0), WarriorSkills.mechanics(each, SkillSlots.Slot.UTIL, AttackType.RANGE));
        }
        assertEquals(SkillSlots.Mechanics.NONE, WarriorSkills.mechanics(config, SkillSlots.Slot.SMALL, AttackType.DEPLOY));
    }

    /**
     * t168: the mace's 小 has 2 charges (6 s, 12 s), 中 a re-activation of 2.5 s and 12 s, 大 28 s, util 6 s; the staff's 小 2 charges (4 s, 9 s), 中
     * a re-activation of 4 s and 14 s, 大 26 s, util 6 s. The code's defaults are the config's.
     */
    @Test
    void theClericsSkillsHaveTheirChargesAndTimes() {
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        org.bukkit.configuration.file.YamlConfiguration none = new org.bukkit.configuration.file.YamlConfiguration();
        for (org.bukkit.configuration.file.YamlConfiguration each : java.util.List.of(config, none)) {
            assertEquals(new SkillSlots.Mechanics(2, 0.0, 6.0, 12.0), ClericSkills.mechanics(each, SkillSlots.Slot.SMALL, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 2.5, 0.0, 12.0), ClericSkills.mechanics(each, SkillSlots.Slot.MEDIUM, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 28.0), ClericSkills.mechanics(each, SkillSlots.Slot.LARGE, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 6.0), ClericSkills.mechanics(each, SkillSlots.Slot.UTIL, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(2, 0.0, 4.0, 9.0), ClericSkills.mechanics(each, SkillSlots.Slot.SMALL, AttackType.RANGE));
            assertEquals(new SkillSlots.Mechanics(1, 4.0, 0.0, 14.0), ClericSkills.mechanics(each, SkillSlots.Slot.MEDIUM, AttackType.RANGE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 26.0), ClericSkills.mechanics(each, SkillSlots.Slot.LARGE, AttackType.RANGE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 6.0), ClericSkills.mechanics(each, SkillSlots.Slot.UTIL, AttackType.RANGE));
        }
        assertEquals(SkillSlots.Mechanics.NONE, ClericSkills.mechanics(config, SkillSlots.Slot.LARGE, AttackType.DEPLOY));
    }

    /**
     * t168: the knife's 小 has 2 charges (4 s, 9 s), 中 a re-activation of 3 s and 10 s, 大 22 s, util 5 s; the bow's 小 (the power shot) 2 charges
     * (3 s, 7 s) and its other skills the class's cooldowns (no mechanics of their own). The code's defaults are the config's.
     */
    @Test
    void theRangersSkillsHaveTheirChargesAndTimes() {
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        org.bukkit.configuration.file.YamlConfiguration none = new org.bukkit.configuration.file.YamlConfiguration();
        for (org.bukkit.configuration.file.YamlConfiguration each : java.util.List.of(config, none)) {
            assertEquals(new SkillSlots.Mechanics(2, 0.0, 4.0, 9.0), RangerSkills.mechanics(each, SkillSlots.Slot.SMALL, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 3.0, 0.0, 10.0), RangerSkills.mechanics(each, SkillSlots.Slot.MEDIUM, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 22.0), RangerSkills.mechanics(each, SkillSlots.Slot.LARGE, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 5.0), RangerSkills.mechanics(each, SkillSlots.Slot.UTIL, AttackType.MELEE));
            assertEquals(new SkillSlots.Mechanics(2, 0.0, 3.0, 7.0), RangerSkills.mechanics(each, SkillSlots.Slot.SMALL, AttackType.RANGE));
            assertEquals(SkillSlots.Mechanics.NONE, RangerSkills.mechanics(each, SkillSlots.Slot.MEDIUM, AttackType.RANGE));
            assertEquals(SkillSlots.Mechanics.NONE, RangerSkills.mechanics(each, SkillSlots.Slot.UTIL, AttackType.RANGE));
        }
        assertEquals(SkillSlots.Mechanics.NONE, RangerSkills.mechanics(config, SkillSlots.Slot.SMALL, AttackType.DEPLOY));
    }
}
