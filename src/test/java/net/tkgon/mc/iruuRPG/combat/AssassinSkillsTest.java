package net.tkgon.mc.iruuRPG.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t165: out of the invisibility of the assassin's 大, the next normal attack or skill hits harder (for the time it lasts) and ends it. */
class AssassinSkillsTest {

    @Test
    void theNextAttackOutOfTheInvisibilityHitsHarder() {
        UUID id = UUID.randomUUID();
        // waiting in ambush until 4 s: nothing is harder yet
        AssassinSkills.armAmbush(id, 4000L, 1.5);
        assertEquals(1.0, AssassinSkills.ambushMultiplier(id, 1000L));
        // the attack at 2 s goes into it: 1.5 times for 1.5 s
        assertTrue(AssassinSkills.takeAmbush(id, 2000L, 1.5));
        assertEquals(1.5, AssassinSkills.ambushMultiplier(id, 2500L));
        // a second attack does not take it again (it is the one attack's)
        assertFalse(AssassinSkills.takeAmbush(id, 2600L, 1.5));
        // over after 1.5 s
        assertEquals(1.0, AssassinSkills.ambushMultiplier(id, 3600L));
        assertEquals(1.0, AssassinSkills.ambushMultiplier(id, 2500L));
    }

    @Test
    void anAmbushThatRanOutIsNoneAndAPlayerLeavingForgetsIt() {
        UUID id = UUID.randomUUID();
        AssassinSkills.armAmbush(id, 4000L, 1.5);
        // the invisibility ended (4 s) before any attack
        assertFalse(AssassinSkills.takeAmbush(id, 4001L, 1.5));
        assertEquals(1.0, AssassinSkills.ambushMultiplier(id, 4100L));
        // a player who waits for none, and one who left
        assertFalse(AssassinSkills.takeAmbush(UUID.randomUUID(), 0L, 1.5));
        UUID gone = UUID.randomUUID();
        AssassinSkills.armAmbush(gone, 4000L, 1.5);
        assertTrue(AssassinSkills.takeAmbush(gone, 100L, 1.5));
        AssassinSkills.forget(gone);
        assertEquals(1.0, AssassinSkills.ambushMultiplier(gone, 200L));
    }

    /** t198: a blow at the assassin in the veil strikes an afterimage at the chance of veil-dodge (a roll below it), never at 0, always at 1. */
    @Test
    void aBlowInTheVeilStrikesAnAfterimageAtItsChance() {
        assertTrue(AssassinDeployCombo.dodged(0.1, 0.25));
        assertFalse(AssassinDeployCombo.dodged(0.3, 0.25));
        assertFalse(AssassinDeployCombo.dodged(0.0, 0.0));
        assertTrue(AssassinDeployCombo.dodged(0.99, 1.0));
    }
}
