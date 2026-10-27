package net.tkgon.mc.iruuRPG.mob;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The poise gauge (no server needed): a flinch every few hits, not every hit, and not one after another. */
class PoiseTest {

    private static final Poise.Settings DEFAULTS = Poise.Settings.of(null, null);
    private final UUID mob = UUID.randomUUID();

    @Test
    void smallHitsFlinchEveryFourthHit() {
        Poise poise = new Poise();
        // a boss: each hit takes 1% of its life → 30 + 1 a hit
        int now = 0;
        for (int hit = 1; hit <= 3; hit++) assertFalse(poise.hit(mob, DEFAULTS, 10.0, 1000.0, now += 10), "hit " + hit);
        assertTrue(poise.hit(mob, DEFAULTS, 10.0, 1000.0, now += 10), "the fourth fills it");
        poise.flinched(mob, now);
        assertEquals(0.0, poise.value(mob));
    }

    @Test
    void bigHitsFlinchSooner() {
        Poise poise = new Poise();
        // a hit that takes 20% of the life → 30 + 20 a hit
        assertFalse(poise.hit(mob, DEFAULTS, 200.0, 1000.0, 10));
        assertTrue(poise.hit(mob, DEFAULTS, 200.0, 1000.0, 20));
    }

    @Test
    void notTwoFlinchesInARow() {
        Poise poise = new Poise();
        Poise.Settings quick = Poise.Settings.of(null, Map.of("per-hit", 100));
        assertTrue(poise.hit(mob, quick, 1.0, 1000.0, 100));
        poise.flinched(mob, 100);
        assertFalse(poise.hit(mob, quick, 1.0, 1000.0, 110), "1.5 s must pass first");
        assertTrue(poise.hit(mob, quick, 1.0, 1000.0, 130), "the full gauge waits for the next hit after the interval");
    }

    @Test
    void theGaugeDrainsWhenTheMobIsLeftAlone() {
        Poise poise = new Poise();
        poise.hit(mob, DEFAULTS, 0.0, 1000.0, 0);
        poise.hit(mob, DEFAULTS, 0.0, 1000.0, 10);
        assertEquals(60.0, poise.value(mob), 1.0E-9);
        // 2.5 s quiet, then 2 s of draining at 30 a second = -60
        assertFalse(poise.hit(mob, DEFAULTS, 0.0, 1000.0, 10 + 50 + 40));
        assertEquals(30.0, poise.value(mob), 1.0E-9);
    }

    @Test
    void aMobCanHaveItsOwnNumbers() {
        Poise.Settings tough = Poise.Settings.of(null, Map.of("max", 200, "flinch-seconds", 0.5));
        assertEquals(200.0, tough.max());
        assertEquals(10, tough.flinchTicks());
        assertEquals(30.0, tough.perHit());
    }
}
