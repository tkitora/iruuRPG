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
}
