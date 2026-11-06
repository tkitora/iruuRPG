package net.tkgon.mc.iruuRPG.bgm;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The BGM of one player: the songs that play for them now (more than one while one fades out and the next fades in) and the volume they follow. The module calls
 * {@link #follow} when the player's place may have changed and {@link #tick} every server tick.
 */
final class BgmChannel implements BgmVoice.Sink {

    private static final double PAN_WIDTH = 2.0;
    /** A player that moves farther than this in a tick (0.02 blocks) is walking. */
    private static final double MOVING_SQUARED = 0.0004;
    private static final double DEFAULT_FADE = 3.0;
    /** The ticks of a bar of the songs (4/4 in sixteenths): a change between the phases of a song waits for the next bar line. */
    static final int BAR_TICKS = 16;
    static final String MANUAL = "manual";
    static final String SCENE = "scene";

    /** The sound of every instrument of Note Block Studio (0 to 15); the instruments after them are custom ones. */
    private static final Sound[] SOUNDS = {
            Sound.BLOCK_NOTE_BLOCK_HARP, Sound.BLOCK_NOTE_BLOCK_BASS, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, Sound.BLOCK_NOTE_BLOCK_SNARE,
            Sound.BLOCK_NOTE_BLOCK_HAT, Sound.BLOCK_NOTE_BLOCK_GUITAR, Sound.BLOCK_NOTE_BLOCK_FLUTE, Sound.BLOCK_NOTE_BLOCK_BELL,
            Sound.BLOCK_NOTE_BLOCK_CHIME, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_COW_BELL,
            Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Sound.BLOCK_NOTE_BLOCK_BIT, Sound.BLOCK_NOTE_BLOCK_BANJO, Sound.BLOCK_NOTE_BLOCK_PLING
    };

    /** Where the notes go to be played at a time of the clock (a thread of its own); the tests use their own. */
    interface Later {
        void at(long dueNanos, Runnable task);
    }

    /** The notes are older than this when they are sent (the sender was stuck): they are not played at all rather than late. */
    private static final long STALE_NANOS = 250_000_000L;

    private final List<BgmVoice> voices = new ArrayList<>();
    private Later later;
    private BgmClock clock;
    private java.util.function.LongSupplier nanos = System::nanoTime;
    /** Goes up when everything is stopped: the notes that are still waiting to be sent are then not sent. */
    private volatile int generation;
    /** At most this many notes a second reach the player (0: no limit); the quietest are left out first (see {@link #flush}). */
    private int noteCap;
    private final int[] recent = new int[20];       // the notes sent in each of the last 20 steps (a second)
    private int recentPos;
    private int recentSum;
    private final List<Planned> batch = new ArrayList<>();
    private long sentNotes;
    private long thinnedNotes;
    private int stalls;
    private long longestStallMs;
    private long stalledMs;
    private int peakNotesInSecond;

    /** A note that has been worked out and waits for the end of the step, to be counted against the limit. */
    private record Planned(Sound sound, float volume, float pitch, Location at, double late) {
    }
    /** The wall-clock time of the song tick that is being played (when there is a {@link #later}). */
    private long dueNanos;
    private BgmVolume current;
    private NbsSong followedSong;
    private boolean manual;
    /** The song of a scene of the game (the dungeon): played instead of the volumes, under a listen. */
    private NbsSong sceneSong;
    private boolean sceneLoop;
    /** The song a listen (a test or the settings menu) plays now. */
    private NbsSong manualSong;
    /** A change of the scene song that waits for the next bar line of the song that plays (see {@link #playSceneSynced}). */
    private Pending pending;

    private static final class Pending {
        final NbsSong song;
        final double fade;
        final double startTick;
        /** Song ticks of 1/20 s (one per server tick when the notes are not planned ahead) until the bar line. */
        int serverTicksLeft;

        Pending(NbsSong song, double fade, double startTick, int serverTicksLeft) {
            this.song = song;
            this.fade = fade;
            this.startTick = startTick;
            this.serverTicksLeft = serverTicksLeft;
        }
    }

    // (set for the length of one tick, for the sink)
    private Player player;
    private Location base;
    private double rightX;
    private double rightZ;
    /** True while the player walks (they moved this tick): the notes then follow them (see {@link #note}). */
    private boolean moving;
    /** Where the player was at the last tick, to see whether they move. */
    private Location lastBase;

    /**
     * Plans the notes {@code leadTicks} ticks (twentieths of a second) ahead and has {@code later} play them at their time of the wall clock, so the song keeps its tempo and goes on through
     * a lag spike of up to that long. Without it (the default), a note is played in the server tick it falls due in.
     */
    void planAhead(Later later, int leadTicks, java.util.function.LongSupplier nanos) {
        this.later = later;
        this.clock = later == null ? null : new BgmClock(leadTicks);
        if (nanos != null) this.nanos = nanos;
    }

    /** At most {@code perSecond} notes a second are sent (0: no limit): the client mixes only so many sounds at once, and a song that is too dense makes it drop notes at random. */
    void limitNotes(int perSecond) {
        this.noteCap = Math.max(0, perSecond);
    }

    /** What happened since the channel was made: notes sent, notes left out for the limit, stops of the server longer than the lead, the longest of them (ms), the most notes in one second. */
    String stats() {
        return "sent " + sentNotes + ", left out " + thinnedNotes + " (limit " + (noteCap == 0 ? "none" : noteCap + "/s") + "), busiest second " + peakNotesInSecond
                + " notes, server stops over the lead " + stalls + " (longest " + longestStallMs + " ms)";
    }

    /** The length (ms) of a stop of the server found since the last call, or 0 (to be told to the log once). */
    long takeStall() {
        long value = stalledMs;
        stalledMs = 0L;
        return value;
    }

    boolean manual() {
        return manual;
    }

    /** The song of the scene, or null. */
    NbsSong scene() {
        return sceneSong;
    }

    /** The song of the listen, or null. */
    NbsSong manualSong() {
        return manual ? manualSong : null;
    }

    /** Where the song of this id is (a tick of the song) in the voice that is not on its way out, or -1; for the tests. */
    double positionOf(String songId) {
        for (BgmVoice voice : voices) if (!voice.fadingOut() && voice.song().id().equalsIgnoreCase(songId)) return voice.position();
        return -1.0;
    }

    /** True while a change to the next phase waits for the bar line. */
    boolean changePending() {
        return pending != null;
    }

    /** The volume the player was last found in (null: none). */
    BgmVolume current() {
        return current;
    }

    /** How many songs are going now (a song that fades out while the next fades in counts too). */
    int voiceCount() {
        return voices.size();
    }

    boolean silent() {
        return voices.isEmpty();
    }

    /** The title of the song that is playing (the one that is not on its way out), or null. */
    String nowPlaying() {
        for (BgmVoice voice : voices) if (!voice.fadingOut()) return voice.song().title();
        return null;
    }

    /** The volume the player stands in (null: none). Starts, changes or stops the song when it is another than before. */
    void follow(BgmVolume volume, BgmRegistry registry) {
        if (manual || sceneSong != null) return;
        NbsSong song = volume == null ? null : registry.song(volume.song());
        if (volume != null && volume == current && song == followedSong) {
            for (BgmVoice voice : voices) if (voice.origin().equals(volume.id())) voice.regionVolume(volume.volume() / 100.0f);
            return;
        }
        BgmVolume previous = current;
        current = volume;
        followedSong = song;
        double fade = volume != null ? volume.fade() : previous != null ? previous.fade() : DEFAULT_FADE;
        BgmVoice same = null;
        for (BgmVoice voice : voices) {
            if (song != null && voice.song() == song && same == null && !voice.done()) {
                same = voice;
            } else if (!voice.fadingOut()) {
                voice.fadeOut(fade);
            }
        }
        if (song == null) return;
        if (same != null) {
            same.regionVolume(volume.volume() / 100.0f);
            if (same.fadingOut()) same.fadeIn(fade);
        } else {
            voices.add(new BgmVoice(song, volume.id(), volume.loop(), volume.volume() / 100.0f, fade));
        }
    }

    /** Plays a song by command (a test) or for a listen: the volumes and the scene are not followed until {@link #stopManual}. */
    void playManual(NbsSong song, boolean loop, double fade) {
        pending = null;
        manual = true;
        manualSong = song;
        for (BgmVoice voice : voices) if (!voice.fadingOut()) voice.fadeOut(fade);
        voices.add(new BgmVoice(song, MANUAL, loop, 1.0f, fade));
    }

    /** Ends the test: the song fades out; a scene comes back, or the volumes are followed again. */
    void stopManual(double fade) {
        manual = false;
        manualSong = null;
        current = null;
        followedSong = null;
        for (BgmVoice voice : voices) if (!voice.fadingOut()) voice.fadeOut(fade);
        if (sceneSong != null) startScene(fade);
    }

    /**
     * Starts the song of a scene: the song that plays fades out and this one fades in. The same song (still playing, or fading out) goes on; another one is gone over to.
     * Under a listen only the song is kept, and it starts when the listen ends.
     */
    void playScene(NbsSong song, boolean loop, double fade) {
        pending = null;
        sceneSong = song;
        sceneLoop = loop;
        if (!manual) startScene(fade);
    }

    /**
     * Goes over to the next song of the same piece (the phase of a boss fight): at the next bar line of the song that plays, that one fades out and this one fades in from the same
     * bar, so the music goes on. When the songs do not fit (another tempo or length), or nothing plays, or a listen is going on, it is {@link #playScene}.
     */
    void playSceneSynced(NbsSong song, boolean loop, double fade) {
        BgmVoice old = null;
        for (BgmVoice voice : voices) if (voice.origin().equals(SCENE) && !voice.fadingOut() && !voice.done()) old = voice;
        if (manual || old == null || old.song() == song || !fits(old.song(), song)) {
            playScene(song, loop, fade);
            return;
        }
        double position = old.position();
        double line = Math.ceil(position / BAR_TICKS) * BAR_TICKS;  // the next bar line of the song that plays
        double barTick = line >= song.length() ? line - (song.length() - song.loopStart()) : line;  // (it wraps round to its loop point)
        int serverTicks = (int) Math.ceil((line - position) / (old.song().ticksPerSecond() / 20.0));
        sceneSong = song;
        sceneLoop = loop;
        pending = new Pending(song, fade, barTick, Math.max(0, serverTicks));
    }

    /** Two songs are the same piece when they have the same tempo and length (and so the same bars). */
    private static boolean fits(NbsSong a, NbsSong b) {
        return a.length() == b.length() && Math.abs(a.ticksPerSecond() - b.ticksPerSecond()) < 1.0E-6;
    }

    private void startScene(double fade) {
        startScene(fade, 0.0);
    }

    private void startScene(double fade, double startTick) {
        current = null;
        followedSong = null;
        BgmVoice same = null;
        for (BgmVoice voice : voices) {
            if (voice.origin().equals(SCENE) && voice.song() == sceneSong && same == null && !voice.done()) same = voice;
            else if (!voice.fadingOut()) voice.fadeOut(fade);
        }
        if (same != null) {
            if (same.fadingOut()) same.fadeIn(fade);
        } else {
            voices.add(new BgmVoice(sceneSong, SCENE, sceneLoop, 1.0f, fade, startTick));
        }
    }

    /** Ends the scene: its song fades out and the volumes are followed again (after a listen, if there is one now). */
    void endScene(double fade) {
        pending = null;
        if (sceneSong == null) return;
        sceneSong = null;
        current = null;
        followedSong = null;
        if (manual) return;
        for (BgmVoice voice : voices) if (!voice.fadingOut()) voice.fadeOut(fade);
    }

    void stopAll() {
        generation++;
        pending = null;
        voices.clear();
        current = null;
        followedSong = null;
        manual = false;
        manualSong = null;
        sceneSong = null;
    }

    void tick(Player target, float master) {
        int steps = 1;
        long first = 0L;
        if (later != null) {
            steps = clock.steps(nanos.getAsLong());
            first = clock.firstDue();
            long stalled = clock.stalledNanos() / 1_000_000L;
            if (stalled > 0L) {
                stalls++;
                stalledMs = stalled;
                longestStallMs = Math.max(longestStallMs, stalled);
            }
        }
        boolean prepared = false;
        try {
            for (int step = 0; step < steps; step++) {
                if (pending != null) {
                    if (voices.isEmpty() || pending.song != sceneSong) {
                        pending = null;
                    } else if (--pending.serverTicksLeft <= 0) {
                        Pending go = pending;
                        pending = null;
                        startScene(go.fade, go.startTick);
                    }
                }
                if (voices.isEmpty()) {
                    advance(0);
                    continue;
                }
                if (!prepared) {
                    prepared = true;
                    prepare(target);
                }
                this.dueNanos = first + step * BgmClock.STEP_NANOS;
                for (BgmVoice voice : voices) voice.tick(this, master);
                voices.removeIf(BgmVoice::done);
                flush();
            }
        } finally {
            batch.clear();
            this.player = null;
            this.base = null;
        }
    }

    /** What the sink needs to know about the player this tick: where they are, which way they face, whether they walk. */
    private void prepare(Player target) {
        this.player = target;
        this.base = target.getEyeLocation();
        double dx = lastBase == null ? 0.0 : base.getX() - lastBase.getX();
        double dy = lastBase == null ? 0.0 : base.getY() - lastBase.getY();
        double dz = lastBase == null ? 0.0 : base.getZ() - lastBase.getZ();
        moving = lastBase != null && lastBase.getWorld() == base.getWorld() && dx * dx + dy * dy + dz * dz > MOVING_SQUARED;
        lastBase = base.clone();
        double yaw = Math.toRadians(base.getYaw());
        this.rightX = -Math.cos(yaw);
        this.rightZ = -Math.sin(yaw);
    }

    @Override
    public void note(int instrument, int key, float volume, float pan) {
        noteAt(instrument, key, volume, pan, 0.0);
    }

    @Override
    public void noteAt(int instrument, int key, float volume, float pan, double late) {
        if (instrument < 0 || instrument >= SOUNDS.length) return;
        int folded = key;
        while (folded < 33) folded += 12;
        while (folded > 57) folded -= 12;
        float pitch = (float) Math.pow(2.0, (folded - 45) / 12.0);
        Location at = null;                    // null: from the player themself (a walking player takes the notes with them: a sound played at a place stays there and rings out behind them)
        if (!moving) {
            at = base;
            if (pan != 0.0f) at = base.clone().add(rightX * pan * PAN_WIDTH, 0.0, rightZ * pan * PAN_WIDTH);
        }
        batch.add(new Planned(SOUNDS[instrument], volume, pitch, at, late));
    }

    /**
     * The end of a step: sends the notes of the step, but no more than the limit allows in the last second; where there are too many the quietest are left out (the hats, the echoes,
     * the doubles), the tune and the bass stay.
     */
    private void flush() {
        if (batch.isEmpty()) {
            advance(0);
            return;
        }
        int allowed = noteCap == 0 ? batch.size() : Math.max(0, noteCap - recentSum);
        if (batch.size() > allowed) {
            batch.sort((a, b) -> Float.compare(b.volume, a.volume));
            thinnedNotes += batch.size() - allowed;
            while (batch.size() > allowed) batch.remove(batch.size() - 1);
        }
        for (Planned note : batch) play(player, note.at, note.sound, note.volume, note.pitch, note.late);
        sentNotes += batch.size();
        advance(batch.size());
        batch.clear();
    }

    private void advance(int sent) {
        recentPos = (recentPos + 1) % recent.length;
        recentSum += sent - recent[recentPos];
        recent[recentPos] = sent;
        peakNotesInSecond = Math.max(peakNotesInSecond, recentSum);
    }

    /** Plays the note now, or (when planning ahead) hands it to the sender for its time. {@code at} null: from the player themself. */
    private void play(Player target, Location at, Sound sound, float volume, float pitch, double late) {
        if (later == null) {
            send(target, at, sound, volume, pitch);
            return;
        }
        int planned = generation;
        // (a note that fell due between two ticks is sent at its own time: the beat does not move to the 50 ms grid of the server)
        long due = dueNanos - (long) (late * BgmClock.STEP_NANOS);
        later.at(due, () -> {
            if (planned != generation || nanos.getAsLong() - due > STALE_NANOS) return;
            send(target, at, sound, volume, pitch);
        });
    }

    private static void send(Player target, Location at, Sound sound, float volume, float pitch) {
        if (at == null) target.playSound(target, sound, SoundCategory.MUSIC, volume, pitch);
        else target.playSound(at, sound, SoundCategory.MUSIC, volume, pitch);
    }
}
