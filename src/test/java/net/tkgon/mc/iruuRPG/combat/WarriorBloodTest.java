package net.tkgon.mc.iruuRPG.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The warrior's wounds (t168): a share of the damage stays, fades after the last blow, is drunk back by hits and held by the oath. */
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

    @Test
    void theOathHoldsTheWoundUntilItEnds() {
        UUID warrior = UUID.randomUUID();
        WarriorBlood.wound(warrior, 100.0, 0.5, 1000.0, FADE, 0L);
        WarriorBlood.hold(warrior, 8000L, FADE, 1000L);
        // held: what was left at the hold (40) stays, and new wounds add to it without fading
        assertEquals(40.0, WarriorBlood.left(warrior, FADE, 7000L), 1.0E-9);
        assertEquals(50.0, WarriorBlood.wound(warrior, 20.0, 0.5, 1000.0, FADE, 7000L), 1.0E-9);
        assertEquals(50.0, WarriorBlood.left(warrior, FADE, 8000L), 1.0E-9);
        // then it fades as usual
        assertEquals(25.0, WarriorBlood.left(warrior, FADE, 10500L), 1.0E-9);
        WarriorBlood.forget(warrior);
    }
}
