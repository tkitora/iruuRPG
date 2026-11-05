package net.tkgon.mc.iruuRPG.bgm;

/**
 * The clock of a channel that plans ahead: a song tick (a twentieth of a second of the song's time) is due at a wall-clock time, and each server tick plans every tick that falls
 * due before {@code now + lead}. A server that runs at 10 ticks a second so plans two song ticks per server tick, and one that stops for less than the lead has its notes already
 * waiting to be sent.
 */
final class BgmClock {

    static final long STEP_NANOS = 50_000_000L;
    /** A clock that is more than this late (a stop longer than the lead) does not hurry to catch up with a burst of notes: it goes on from now. */
    static final long MAX_LATE_NANOS = 250_000_000L;
    private static final int MOST_STEPS = 60;

    private final long leadNanos;
    private long nextDue;
    private boolean started;
    private long firstDue;

    BgmClock(int leadTicks) {
        this.leadNanos = Math.max(0, leadTicks) * STEP_NANOS;
    }

    /** How many song ticks to play now (those due before {@code now + lead}); their times are {@link #firstDue} + n * {@link #STEP_NANOS}. */
    int steps(long now) {
        if (!started || now - nextDue > MAX_LATE_NANOS) {
            nextDue = now;
            started = true;
        }
        firstDue = nextDue;
        int steps = 0;
        long horizon = now + leadNanos;
        while (nextDue <= horizon && steps < MOST_STEPS) {
            steps++;
            nextDue += STEP_NANOS;
        }
        return steps;
    }

    long firstDue() {
        return firstDue;
    }
}
