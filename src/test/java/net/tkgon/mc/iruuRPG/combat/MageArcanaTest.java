package net.tkgon.mc.iruuRPG.combat;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mage (t168): the order of 小・中・大 that opens the torrent, the criticality of the lantern and the times of the ranged skills. */
class MageArcanaTest {

    @Test
    void theTorrentOpensWhenSmallMediumAndLargeWereAllCastWithinTheWindow() {
        Map<SkillSlots.Slot, Long> casts = new EnumMap<>(SkillSlots.Slot.class);
        assertFalse(MageArcana.completes(casts, SkillSlots.Slot.SMALL, 0L, 10000L));
        assertFalse(MageArcana.completes(casts, SkillSlots.Slot.SMALL, 1000L, 10000L));
        assertFalse(MageArcana.completes(casts, SkillSlots.Slot.MEDIUM, 2000L, 10000L));
        assertTrue(MageArcana.completes(casts, SkillSlots.Slot.LARGE, 9000L, 10000L));
        // too late: 小 was 12 s before
        Map<SkillSlots.Slot, Long> late = new EnumMap<>(SkillSlots.Slot.class);
        MageArcana.completes(late, SkillSlots.Slot.SMALL, 0L, 10000L);
        MageArcana.completes(late, SkillSlots.Slot.MEDIUM, 5000L, 10000L);
        assertFalse(MageArcana.completes(late, SkillSlots.Slot.LARGE, 12000L, 10000L));
        assertTrue(MageArcana.completes(late, SkillSlots.Slot.SMALL, 13000L, 10000L));
    }

    @Test
    void criticalityGrowsWhileAttackingInsideAndIsLostOutsideOrIdle() {
        UUID mage = UUID.randomUUID();
        assertEquals(1, MageArcana.attackAt(mage, true, 3, 0L, 2000L));
        assertEquals(2, MageArcana.attackAt(mage, true, 3, 1000L, 2000L));
        assertEquals(3, MageArcana.attackAt(mage, true, 3, 2000L, 2000L));
        assertEquals(3, MageArcana.attackAt(mage, true, 3, 3000L, 2000L));
        // no attack for longer than the hold: all gone
        assertEquals(0, MageArcana.criticality(mage, 5001L, 2000L));
        assertEquals(1, MageArcana.attackAt(mage, true, 3, 6000L, 2000L));
        // an attack from outside the circle: all gone
        assertEquals(0, MageArcana.attackAt(mage, false, 3, 6500L, 2000L));
        assertEquals(0, MageArcana.criticality(mage, 6500L, 2000L));
        MageArcana.forget(mage);
    }

    @Test
    void theRangedSkillsHaveTheirChargesAndTimes() {
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        org.bukkit.configuration.file.YamlConfiguration none = new org.bukkit.configuration.file.YamlConfiguration();
        for (org.bukkit.configuration.file.YamlConfiguration each : java.util.List.of(config, none)) {
            assertEquals(new SkillSlots.Mechanics(2, 0.0, 3.0, 7.0), MageArcana.mechanics(each, SkillSlots.Slot.SMALL));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 8.0), MageArcana.mechanics(each, SkillSlots.Slot.MEDIUM));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 20.0), MageArcana.mechanics(each, SkillSlots.Slot.LARGE));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 5.0), MageArcana.mechanics(each, SkillSlots.Slot.UTIL));
        }
    }
}
