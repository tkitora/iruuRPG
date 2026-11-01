package net.tkgon.mc.iruuRPG.screenfx;

import net.tkgon.mc.iruuRPG.screenfx.ScreenEffects.Effect;
import net.tkgon.mc.iruuRPG.pack.ResourcePackService;
import org.bukkit.Color;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t125: the markers' glow colors and the words of /screenfx; t128: who is sent markers. */
class ScreenEffectsTest {

    @Test
    void aMarkerCarriesItsSlotAndStrength() {
        assertEquals(Color.fromRGB(177, 1, 100), ScreenEffects.markerColor(Effect.BLOOM, 100));
        assertEquals(Color.fromRGB(177, 1, 255), ScreenEffects.markerColor(Effect.BLOOM, 900));
        assertEquals(Color.fromRGB(177, 1, 1), ScreenEffects.markerColor(Effect.BLOOM, 0));
        assertEquals(Color.fromRGB(177, 2, 100), ScreenEffects.markerColor(Effect.DARKEN, 100));
        assertEquals(Effect.BLOOM, Effect.find("Bloom"));
        assertEquals(Effect.DARKEN, Effect.find("darken"));
        assertNull(Effect.find("nothing"));
    }

    @Test
    void markersGoOnlyToClientsWithTheShaders() {
        // t128: by the status of the marks' pack the client tells; unknown (no pack offered yet) still sends
        assertTrue(ScreenEffects.hasShaders((Status) null));
        assertTrue(ScreenEffects.hasShaders(Status.SUCCESSFULLY_LOADED));
        assertTrue(ScreenEffects.hasShaders(Status.ACCEPTED));
        assertTrue(ScreenEffects.hasShaders(Status.DOWNLOADED));
        for (Status status : new Status[]{Status.DECLINED, Status.FAILED_DOWNLOAD, Status.INVALID_URL, Status.FAILED_RELOAD, Status.DISCARDED}) {
            assertFalse(ScreenEffects.hasShaders(status), status.name());
        }
        assertEquals(UUID.nameUUIDFromBytes("iruurpg:marks".getBytes(StandardCharsets.UTF_8)), ResourcePackService.packId("marks"));
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
