package net.tkgon.mc.iruuRPG.travel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** (t254) The admins' own teleport points: kept a player, up to 45, saved whole and read back; a file that cannot be read is not written over. */
class PersonalPointsTest {

    @TempDir
    File folder;

    @Test
    void pointsAreKeptByPlayerSavedAndReadBack() {
        List<String> warnings = new ArrayList<>();
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        PersonalPoints points = new PersonalPoints(folder, warnings::add);
        assertTrue(points.of(me).isEmpty());
        assertTrue(points.add(me, new PersonalPoints.Point("港", "world", 10.5, 64, -3.5, 90f, 10f)));
        assertTrue(points.add(me, new PersonalPoints.Point("塔の上", "world", 0, 120, 0, 0f, 0f)));
        assertTrue(points.add(other, new PersonalPoints.Point("村", "world_nether", 1, 2, 3, 0f, 0f)));
        PersonalPoints again = new PersonalPoints(folder, warnings::add);
        assertEquals(2, again.of(me).size());
        assertEquals(new PersonalPoints.Point("港", "world", 10.5, 64, -3.5, 90f, 10f), again.of(me).get(0));
        assertEquals("村", again.of(other).get(0).name());
        // taken away by its place in the list
        assertTrue(again.remove(me, 0));
        assertFalse(again.remove(me, 5));
        assertEquals("塔の上", new PersonalPoints(folder, warnings::add).of(me).get(0).name());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void noMoreThanAPageAndABrokenFileIsKept() throws IOException {
        UUID me = UUID.randomUUID();
        PersonalPoints points = new PersonalPoints(folder, message -> { });
        for (int n = 0; n < PersonalPoints.MAX; n++) assertTrue(points.add(me, new PersonalPoints.Point("p" + n, "world", n, 64, 0, 0f, 0f)));
        assertFalse(points.add(me, new PersonalPoints.Point("one more", "world", 0, 64, 0, 0f, 0f)));
        // a file that is not yml: read as empty, with a warning, and not written over by a change
        File file = new File(new File(folder, "travel"), "personal-points.yml");
        Files.writeString(file.toPath(), "players: [broken\n", StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();
        PersonalPoints broken = new PersonalPoints(folder, warnings::add);
        assertTrue(broken.of(me).isEmpty());
        assertFalse(warnings.isEmpty());
        broken.add(me, new PersonalPoints.Point("x", "world", 0, 0, 0, 0f, 0f));
        assertEquals("players: [broken\n", Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }
}
