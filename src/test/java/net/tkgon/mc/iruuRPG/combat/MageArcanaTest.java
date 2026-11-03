package net.tkgon.mc.iruuRPG.combat;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mage (t168, t198): the order of 小・中・大 that makes a completion, the criticality of the lantern and its stages, the times of the ranged skills. */
class MageArcanaTest {

    @Test
    void aCompletionComesWhenSmallMediumAndLargeWereAllCastWithinTheWindow() {
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
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 5.0), MageArcana.mechanics(each, SkillSlots.Slot.SMALL));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 9.0), MageArcana.mechanics(each, SkillSlots.Slot.MEDIUM));
            assertEquals(new SkillSlots.Mechanics(1, 0.0, 0.0, 20.0), MageArcana.mechanics(each, SkillSlots.Slot.LARGE));
            assertEquals(new SkillSlots.Mechanics(2, 0.0, 5.0, 9.0), MageArcana.mechanics(each, SkillSlots.Slot.UTIL));
        }
    }

    /** t198: the flow shortens what is left of a cooldown (the wait for all charges when none is left, else the next refill), never below now. */
    @Test
    void theFlowShortensWhatIsLeftOfACooldown() {
        SkillSlots.State cooling = new SkillSlots.State(0);
        cooling.emptyUntil = 10000L;
        cooling.emptyMillis = 9000L;
        SkillSlots.shorten(cooling, 1500L, 1000L);
        assertEquals(8500L, cooling.emptyUntil);
        SkillSlots.shorten(cooling, 20000L, 1000L);
        assertEquals(1000L, cooling.emptyUntil);
        SkillSlots.State refilling = new SkillSlots.State(1);
        refilling.refillStart = 2000L;
        refilling.refillMillis = 5000L;
        SkillSlots.shorten(refilling, 1500L, 3000L);
        assertEquals(500L, refilling.refillStart);
    }

    /** t198: the stages of the lantern's criticality and what they add to the attacks. */
    @Test
    void theLanternGrowsByTheStageOfItsCriticality() {
        assertEquals(0, MageDeployCombo.stage(0, 8));
        assertEquals(0, MageDeployCombo.stage(2, 8));
        assertEquals(1, MageDeployCombo.stage(3, 8));
        assertEquals(2, MageDeployCombo.stage(6, 8));
        assertEquals(3, MageDeployCombo.stage(8, 8));
        // the bolts and the orbs: +1 from stage 1, +2 from stage 2; the jumps of the lightning +1, then +3; the rest none
        assertEquals(0, MageDeployCombo.more(1, 0));
        assertEquals(1, MageDeployCombo.more(1, 1));
        assertEquals(2, MageDeployCombo.more(3, 3));
        assertEquals(1, MageDeployCombo.more(4, 1));
        assertEquals(3, MageDeployCombo.more(4, 2));
        assertEquals(0, MageDeployCombo.more(5, 3));
    }
}
