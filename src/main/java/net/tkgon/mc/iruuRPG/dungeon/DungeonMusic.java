package net.tkgon.mc.iruuRPG.dungeon;

/** The rules for which song a dungeon run plays (the part that needs no server). */
final class DungeonMusic {

    private DungeonMusic() {
    }

    /**
     * The boss song for a boss at {@code phase} (1 = the first phase; a boss that sets no phase is at 1): the first song in the first phase, the next one in the next phase,
     * and so on; with more phases than songs the last song plays on. 0 when there is no song.
     */
    static int songIndex(int songs, double phase) {
        if (songs <= 0) return 0;
        int index = (int) Math.round(phase) - 1;
        return Math.max(0, Math.min(songs - 1, index));
    }
}
