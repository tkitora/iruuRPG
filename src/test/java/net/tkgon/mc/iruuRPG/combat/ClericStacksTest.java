package net.tkgon.mc.iruuRPG.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The cleric (t168): its radiance and verses (stacks up to a cap, lost after a while, spent at once) and the new count of its pulse. */
class ClericStacksTest {

    @Test
    void stacksGrowToTheCapLastAndAreSpent() {
        Stacks stacks = new Stacks();
        UUID cleric = UUID.randomUUID();
        assertEquals(1, stacks.add(cleric, 1, 5, 6000L, 0L));
        assertEquals(3, stacks.add(cleric, 2, 5, 7000L, 1000L));
        assertEquals(5, stacks.add(cleric, 4, 5, 8000L, 2000L));
        assertEquals(5, stacks.count(cleric, 8000L));
        // gone after the last one's time
        assertEquals(0, stacks.count(cleric, 8001L));
        assertEquals(1, stacks.add(cleric, 1, 5, 20000L, 10000L));
        // spent at once
        assertEquals(1, stacks.take(cleric, 10000L));
        assertEquals(0, stacks.count(cleric, 10000L));
        assertEquals(0, stacks.take(cleric, 10000L));
    }

    @Test
    void thePulseCountsTheLifeHealedWholeAndTheOverhealALittle() {
        // 100 of a heal of 100 healed: all of it; 40 of 100 healed: 40 and a quarter of the 60 that did not go in
        assertEquals(100.0, ClassEffectService.pulseShare(100.0, 100.0, 0.25), 1.0E-9);
        assertEquals(55.0, ClassEffectService.pulseShare(100.0, 40.0, 0.25), 1.0E-9);
        assertEquals(25.0, ClassEffectService.pulseShare(100.0, 0.0, 0.25), 1.0E-9);
        assertEquals(0.0, ClassEffectService.pulseShare(100.0, 0.0, 0.0), 1.0E-9);
    }
}
