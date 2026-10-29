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
    private static final double DEFAULT_FADE = 3.0;
    static final String MANUAL = "manual";

    /** The sound of every instrument of Note Block Studio (0 to 15); the instruments after them are custom ones. */
    private static final Sound[] SOUNDS = {
            Sound.BLOCK_NOTE_BLOCK_HARP, Sound.BLOCK_NOTE_BLOCK_BASS, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, Sound.BLOCK_NOTE_BLOCK_SNARE,
            Sound.BLOCK_NOTE_BLOCK_HAT, Sound.BLOCK_NOTE_BLOCK_GUITAR, Sound.BLOCK_NOTE_BLOCK_FLUTE, Sound.BLOCK_NOTE_BLOCK_BELL,
            Sound.BLOCK_NOTE_BLOCK_CHIME, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_COW_BELL,
            Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Sound.BLOCK_NOTE_BLOCK_BIT, Sound.BLOCK_NOTE_BLOCK_BANJO, Sound.BLOCK_NOTE_BLOCK_PLING
    };

    private final List<BgmVoice> voices = new ArrayList<>();
    private BgmVolume current;
    private NbsSong followedSong;
    private boolean manual;

    // (set for the length of one tick, for the sink)
    private Player player;
    private Location base;
    private double rightX;
    private double rightZ;

    boolean manual() {
        return manual;
    }

    /** The volume the player was last found in (null: none). */
    BgmVolume current() {
        return current;
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
        if (manual) return;
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

    /** Plays a song by command (a test): the volumes are not followed until {@link #stopManual}. */
    void playManual(NbsSong song, boolean loop, double fade) {
        manual = true;
        for (BgmVoice voice : voices) if (!voice.fadingOut()) voice.fadeOut(fade);
        voices.add(new BgmVoice(song, MANUAL, loop, 1.0f, fade));
    }

    /** Ends the test: the song fades out and the volumes are followed again. */
    void stopManual(double fade) {
        manual = false;
        current = null;
        followedSong = null;
        for (BgmVoice voice : voices) if (!voice.fadingOut()) voice.fadeOut(fade);
    }

    void stopAll() {
        voices.clear();
        current = null;
        followedSong = null;
        manual = false;
    }

    void tick(Player target, float master) {
        if (voices.isEmpty()) return;
        this.player = target;
        this.base = target.getEyeLocation();
        double yaw = Math.toRadians(base.getYaw());
        this.rightX = -Math.cos(yaw);
        this.rightZ = -Math.sin(yaw);
        try {
            for (BgmVoice voice : voices) voice.tick(this, master);
        } finally {
            this.player = null;
            this.base = null;
        }
        voices.removeIf(BgmVoice::done);
    }

    @Override
    public void note(int instrument, int key, float volume, float pan) {
        if (instrument < 0 || instrument >= SOUNDS.length) return;
        int folded = key;
        while (folded < 33) folded += 12;
        while (folded > 57) folded -= 12;
        float pitch = (float) Math.pow(2.0, (folded - 45) / 12.0);
        Location at = base;
        if (pan != 0.0f) at = base.clone().add(rightX * pan * PAN_WIDTH, 0.0, rightZ * pan * PAN_WIDTH);
        player.playSound(at, SOUNDS[instrument], SoundCategory.MUSIC, volume, pitch);
    }
}
