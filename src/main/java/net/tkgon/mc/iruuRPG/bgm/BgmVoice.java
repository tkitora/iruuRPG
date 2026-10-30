package net.tkgon.mc.iruuRPG.bgm;

/**
 * One song playing for one player, with its own loudness that fades in or out. It knows nothing of Bukkit: every server tick {@link #tick} moves the song on by
 * {@code ticksPerSecond / 20} song ticks and hands each note that falls due to a {@link Sink}.
 */
final class BgmVoice {

    /** Where the notes go; {@code volume} is 0 to 1 (everything already multiplied), {@code pan} is -1 (left) to 1 (right). */
    interface Sink {
        void note(int instrument, int key, float volume, float pan);
    }

    private final NbsSong song;
    /** What it plays for: the volume (the key of {@link BgmVolume}), or {@code "manual"}. */
    private final String origin;
    private final boolean loop;
    private float regionVolume;
    private double position;
    private int next;
    private float gain;
    private float target = 1.0f;
    private float step;
    private boolean finished;

    BgmVoice(NbsSong song, String origin, boolean loop, float regionVolume, double fadeInSeconds) {
        this(song, origin, loop, regionVolume, fadeInSeconds, 0.0);
    }

    /** {@code startPosition} is the tick of the song to begin at (a song that goes over from another one of the same form begins where that one is). */
    BgmVoice(NbsSong song, String origin, boolean loop, float regionVolume, double fadeInSeconds, double startPosition) {
        this.song = song;
        this.position = Math.max(0.0, startPosition);
        this.next = (int) Math.floor(this.position);
        this.origin = origin;
        this.loop = loop;
        this.regionVolume = regionVolume;
        fadeTo(1.0f, fadeInSeconds, 0.0f);
    }

    NbsSong song() {
        return song;
    }

    String origin() {
        return origin;
    }

    /** The tick of the song the clock is at (the notes before it have been played). */
    double position() {
        return position;
    }

    float gain() {
        return gain;
    }

    float target() {
        return target;
    }

    /** The loudness of the volume it plays for (0 to 1); it may change while the song plays. */
    void regionVolume(float value) {
        this.regionVolume = Math.max(0.0f, Math.min(1.0f, value));
    }

    /** True when it is on its way out (or has been told to be quiet). */
    boolean fadingOut() {
        return target <= 0.0f;
    }

    /** The song has ended (and does not loop), or has faded out completely: take it away. */
    boolean done() {
        return finished || (target <= 0.0f && gain <= 0.0005f);
    }

    /** Moves to {@code goal} (0 to 1) over {@code seconds}; with no time at all it is there at once. {@code from} is the loudness to start at (negative: where it is now). */
    void fadeTo(float goal, double seconds, float from) {
        if (from >= 0.0f) gain = from;
        target = Math.max(0.0f, Math.min(1.0f, goal));
        double ticks = seconds * 20.0;
        step = ticks < 1.0 ? 1.0f : (float) (1.0 / ticks);
        if (ticks < 1.0) gain = target;
    }

    /** Fades out (from where it is). */
    void fadeOut(double seconds) {
        fadeTo(0.0f, seconds, -1.0f);
    }

    /** Fades in again after a fade-out that has not finished (the player came back to the same song). */
    void fadeIn(double seconds) {
        fadeTo(1.0f, seconds, -1.0f);
    }

    /** One server tick: the fade, then the notes. {@code master} is the loudness the player chose. */
    void tick(Sink sink, float master) {
        if (gain < target) gain = Math.min(target, gain + step);
        else if (gain > target) gain = Math.max(target, gain - step);
        if (finished) return;

        // (the notes of the ticks that have been reached; then the clock moves on, so the first server tick plays the first tick of the song)
        int limit = (int) Math.floor(position);
        float loudness = gain * regionVolume * master;
        while (next <= limit) {
            if (next >= song.length()) {
                if (!loop) {
                    finished = true;
                    return;
                }
                // back to the loop point, keeping the part of the tick that is left over
                int start = song.loopStart();
                position -= song.length() - start;
                limit = (int) Math.floor(position);
                next = start;
                continue;
            }
            if (loudness > 0.01f) {
                int index = song.firstNoteAt(next);
                while (index < song.noteCount() && song.note(index).tick() == next) {
                    NbsSong.Note note = song.note(index);
                    float volume = note.volume() * loudness;
                    if (volume > 0.01f) sink.note(note.instrument(), note.key(), Math.min(1.0f, volume), note.pan());
                    index++;
                }
            }
            next++;
        }
        position += song.ticksPerSecond() / 20.0;
    }
}
