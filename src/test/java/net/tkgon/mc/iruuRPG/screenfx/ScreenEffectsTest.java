package net.tkgon.mc.iruuRPG.screenfx;

import net.tkgon.mc.iruuRPG.screenfx.ScreenEffects.Effect;
import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** t125: the markers' glow colors and the words of /screenfx. */
class ScreenEffectsTest {

    @Test
    void aMarkerCarriesItsSlotAndStrength() {
        assertEquals(Color.fromRGB(177, 1, 100), ScreenEffects.markerColor(Effect.BLOOM, 100));
        assertEquals(Color.fromRGB(177, 1, 255), ScreenEffects.markerColor(Effect.BLOOM, 900));
        assertEquals(Color.fromRGB(177, 1, 1), ScreenEffects.markerColor(Effect.BLOOM, 0));
        assertEquals(Effect.BLOOM, Effect.find("Bloom"));
        assertNull(Effect.find("nothing"));
    }

    @Test
    void theWordsAfterTheEffect() {
        ScreenEffectsCommand.On plain = ScreenEffectsCommand.parseOn(new String[0]);
        assertEquals(ScreenEffects.NORMAL_STRENGTH, plain.strength());
        assertEquals(0.0, plain.seconds());
        assertNull(plain.target());
        ScreenEffectsCommand.On full = ScreenEffectsCommand.parseOn(new String[]{"150", "2.5", "@a"});
        assertEquals(150, full.strength());
        assertEquals(2.5, full.seconds());
        assertEquals("@a", full.target());
        assertEquals("Steve", ScreenEffectsCommand.parseOn(new String[]{"80", "Steve"}).target());
        assertNotNull(ScreenEffectsCommand.parseOn(new String[]{"0"}).error());
        assertNotNull(ScreenEffectsCommand.parseOn(new String[]{"1.5"}).error());
        assertNotNull(ScreenEffectsCommand.parseOn(new String[]{"100", "99999"}).error());
        assertNotNull(ScreenEffectsCommand.parseOn(new String[]{"100", "5", "a", "b"}).error());
    }
}
