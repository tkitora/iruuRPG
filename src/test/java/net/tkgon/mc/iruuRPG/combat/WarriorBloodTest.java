package net.tkgon.mc.iruuRPG.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The warrior's wounds (t168): a share of the damage stays, fades after the last blow and is drunk back by hits; the great bow's bonus (t198). */
class WarriorBloodTest {

    private static final long FADE = 5000L;

    @Test
    void aWoundFadesAfterTheLastBlowAndIsDrunkBack() {
        UUID warrior = UUID.randomUUID();
        // half of 100 stays; a second blow adds half of 40 to what is left and starts the fading again
        assertEquals(50.0, WarriorBlood.wound(warrior, 100.0, 0.5, 1000.0, FADE, 0L), 1.0E-9);
        assertEquals(25.0, WarriorBlood.left(warrior, FADE, 2500L), 1.0E-9);
        assertEquals(45.0, WarriorBlood.wound(warrior, 40.0, 0.5, 1000.0, FADE, 2500L), 1.0E-9);
        // never more than the cap
        assertEquals(60.0, WarriorBlood.wound(warrior, 1000.0, 0.5, 60.0, FADE, 2500L), 1.0E-9);
        // drinking takes what was asked, at most what is left; the rest keeps fading from where it was
        assertEquals(20.0, WarriorBlood.drink(warrior, 20.0, FADE, 2500L), 1.0E-9);
        assertEquals(40.0, WarriorBlood.left(warrior, FADE, 2500L), 1.0E-9);
        assertEquals(20.0, WarriorBlood.left(warrior, FADE, 5000L), 1.0E-9);
        assertEquals(20.0, WarriorBlood.drink(warrior, 100.0, FADE, 5000L), 1.0E-9);
        assertEquals(0.0, WarriorBlood.left(warrior, FADE, 5000L), 1.0E-9);
        assertEquals(0.0, WarriorBlood.drink(warrior, 10.0, FADE, 5000L), 1.0E-9);
        WarriorBlood.forget(warrior);
    }

    /** t198: the great bow's bonus: an unhurt enemy, each block past the start of the distance (to the cap), and the mark of 中 add up. */
    @Test
    void theGreatBowHitsTheUnhurtAndTheFarHarder() {
        assertEquals(1.0, WarriorSkills.bowBonus(false, 5.0, false, 0.5, 8.0, 0.025, 0.4, 0.25), 1.0E-9);
        assertEquals(1.5, WarriorSkills.bowBonus(true, 5.0, false, 0.5, 8.0, 0.025, 0.4, 0.25), 1.0E-9);
        assertEquals(1.1, WarriorSkills.bowBonus(false, 12.0, false, 0.5, 8.0, 0.025, 0.4, 0.25), 1.0E-9);
        assertEquals(1.4, WarriorSkills.bowBonus(false, 60.0, false, 0.5, 8.0, 0.025, 0.4, 0.25), 1.0E-9);
        assertEquals(2.15, WarriorSkills.bowBonus(true, 60.0, true, 0.5, 8.0, 0.025, 0.4, 0.25), 1.0E-9);
    }
}
