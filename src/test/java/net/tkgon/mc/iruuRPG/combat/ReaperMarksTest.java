package net.tkgon.mc.iruuRPG.combat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reaper's marks of death (t167): each reaper's own, up to a number, lasting from the last one, and taken off when reaped. */
class ReaperMarksTest {

    @Test
    void marksAddUpToTheMostAndAreTakenOffWhenReaped() {
        UUID reaper = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID enemy = UUID.randomUUID();
        assertEquals(1, ReaperMarks.add(reaper, enemy, 1, 3, 8_000L, 0L));
        assertEquals(2, ReaperMarks.add(reaper, enemy, 1, 3, 9_000L, 1_000L));
        // no more than the most
        assertEquals(3, ReaperMarks.add(reaper, enemy, 5, 3, 10_000L, 2_000L));
        assertEquals(3, ReaperMarks.count(reaper, enemy, 9_500L));
        // another reaper's marks are their own
        assertEquals(0, ReaperMarks.count(other, enemy, 2_000L));
        // reaped: all of them come off at once
        assertEquals(3, ReaperMarks.take(reaper, enemy, 3_000L));
        assertEquals(0, ReaperMarks.count(reaper, enemy, 3_000L));
        assertEquals(0, ReaperMarks.take(reaper, enemy, 3_000L));
        ReaperMarks.forget(reaper);
    }

    @Test
    void marksGoWhenTheirTimeIsOverAndANewOneStartsAgain() {
        UUID reaper = UUID.randomUUID();
        UUID enemy = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ReaperMarks.add(reaper, enemy, 2, 3, 8_000L, 0L);
        ReaperMarks.add(reaper, second, 1, 3, 20_000L, 0L);
        // past their time: none, and nothing to reap
        assertEquals(0, ReaperMarks.count(reaper, enemy, 8_001L));
        assertEquals(1, ReaperMarks.add(reaper, enemy, 1, 3, 17_000L, 9_000L));
        Map<UUID, Integer> all = ReaperMarks.all(reaper, 18_000L);
        assertEquals(Map.of(second, 1), all);
        assertTrue(ReaperMarks.all(reaper, 21_000L).isEmpty());
        ReaperMarks.forget(reaper);
        assertEquals(0, ReaperMarks.count(reaper, second, 0L));
    }
}
