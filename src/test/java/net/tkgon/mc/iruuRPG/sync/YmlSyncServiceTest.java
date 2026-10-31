package net.tkgon.mc.iruuRPG.sync;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YmlSyncServiceTest {

    /** git's error comes after "From ...": the log shows the end of the output, not its first line. */
    @Test
    void theEndOfGitsOutputIsWhatTheLogShows() {
        String output = """
                From github.com:iruuRPG/iruu_rpg_yml_file
                 * branch            main       -> FETCH_HEAD
                error: Your local changes to the following files would be overwritten by merge:
                \tbt-templates/ruinedknight.yml
                Please commit your changes or stash them before you merge.
                Aborting
                """;
        assertEquals("error: Your local changes to the following files would be overwritten by merge: | bt-templates/ruinedknight.yml"
                + " | Please commit your changes or stash them before you merge. | Aborting", YmlSyncService.lastLines(output, 4));
        assertEquals("(出力なし)", YmlSyncService.lastLines("  \n", 5));
    }

    @Test
    void untrackedFilesInTheWayAreFound() {
        String output = """
                error: The following untracked working tree files would be overwritten by merge:
                \tquestlines/a.yml
                \tnpcs/b.yml
                Please move or remove them before you merge.
                """;
        assertEquals(List.of("questlines/a.yml", "npcs/b.yml"), YmlSyncService.untrackedInTheWay(output));
    }

    /** The automatic sync lands on second 30 of the clock, away from jobs at the top of the minute. */
    @Test
    void theNextRunIsAtSecondThirty() {
        long minute = 60_000L;
        long base = 1_793_437_200_000L; // a whole minute
        assertEquals(30_000L, YmlSyncService.millisToNext(base, minute, 30_000L));
        assertEquals(25_000L, YmlSyncService.millisToNext(base + 5_000L, minute, 30_000L));
        assertEquals(minute, YmlSyncService.millisToNext(base + 30_000L, minute, 30_000L), "not again at once");
        assertEquals(minute + 500L, YmlSyncService.millisToNext(base + 29_500L, minute, 30_000L), "less than a second: the one after");
    }

    @Test
    void aLockedRepositoryIsRecognized() {
        assertEquals(true, YmlSyncService.lockedOut("fatal: Unable to create '/x/.git/index.lock': File exists.\nAnother git process seems to be running"));
        assertEquals(true, YmlSyncService.lockedOut("error: cannot lock ref 'refs/remotes/origin/main': is at a but expected b"));
        assertEquals(false, YmlSyncService.lockedOut("fatal: Not possible to fast-forward, aborting."));
    }
}
