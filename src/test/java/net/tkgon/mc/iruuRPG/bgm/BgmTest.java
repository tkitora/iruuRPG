package net.tkgon.mc.iruuRPG.bgm;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BgmTest {

    // ---- a small .nbs file made here: version 5, one layer, one note per tick at 20 ticks a second (one song tick for every server tick) -------------------

    private static void le(ByteArrayOutputStream out, int value, int bytes) {
        for (int i = 0; i < bytes; i++) out.write((value >> (8 * i)) & 0xff);
    }

    private static void text(ByteArrayOutputStream out, String value) {
        byte[] data = value.getBytes(StandardCharsets.ISO_8859_1);
        le(out, data.length, 4);
        out.writeBytes(data);
    }

    /** Notes at ticks 0..length-1, the key of the note at tick t is 33 + t. */
    private static NbsSong song(int length, boolean loop, int loopStart) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        le(out, 0, 2);
        out.write(5);
        out.write(16);
        le(out, length - 1, 2);
        le(out, 1, 2);
        text(out, "Test");
        text(out, "");
        text(out, "");
        text(out, "");
        le(out, 2000, 2);
        out.write(0);
        out.write(10);
        out.write(4);
        for (int i = 0; i < 5; i++) le(out, 0, 4);
        text(out, "");
        out.write(loop ? 1 : 0);
        out.write(0);
        le(out, loopStart, 2);
        for (int tick = 0; tick < length; tick++) {
            le(out, 1, 2);                 // one tick on from the last
            le(out, 1, 2);                 // layer 0
            out.write(0);                  // harp
            out.write(33 + tick);
            out.write(100);
            out.write(100);
            le(out, 0, 2);
            le(out, 0, 2);                 // no more layers on this tick
        }
        le(out, 0, 2);                     // no more ticks
        text(out, "Layer");
        out.write(0);
        out.write(100);
        out.write(100);
        out.write(0);
        return NbsSong.read("test", out.toByteArray());
    }

    private static List<Integer> play(BgmVoice voice, int serverTicks) {
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < serverTicks; i++) voice.tick((instrument, key, volume, pan) -> keys.add(key), 1.0f);
        return keys;
    }

    @Test
    void readsTheSongThatComesWithThePlugin() throws IOException {
        byte[] data;
        try (InputStream in = BgmTest.class.getResourceAsStream("/bgm/plains_adventure.nbs")) {
            assertNotNull(in, "the bundled song is in the jar");
            data = in.readAllBytes();
        }
        NbsSong song = NbsSong.read("plains_adventure", data);
        assertEquals("Under an Endless Sky (Field001)", song.title());
        assertEquals(20.0 / 3.0, song.ticksPerSecond(), 1.0E-9);
        assertEquals(1024, song.length());
        assertEquals(64 * 16, song.length(), "64 bars");
        assertTrue(song.seconds() > 150.0, "a field song is 2:30 or longer");
        assertTrue(song.loops());
        assertEquals(64, song.loopStart());
        assertTrue(song.noteCount() > 1000);
        for (int i = 0; i < song.noteCount(); i++) {
            NbsSong.Note note = song.note(i);
            assertTrue(note.key() >= 33 && note.key() <= 57, "key " + note.key() + " is outside a note block");
            assertTrue(note.instrument() >= 0 && note.instrument() < 16);
            assertTrue(note.volume() > 0.05f && note.volume() <= 1.0f, "volume " + note.volume());
            assertTrue(note.tick() >= 0 && note.tick() < song.length());
            if (i > 0) assertTrue(song.note(i - 1).tick() <= note.tick(), "notes are in order");
        }
        assertEquals(0, song.firstNoteAt(0));
        assertEquals(song.noteCount(), song.firstNoteAt(song.length()));
    }

    @Test
    void theTempoOfTheBundledSongIsADivisorOfTwenty() throws IOException {
        NbsSong song = bundled();
        double serverTicksPerSongTick = 20.0 / song.ticksPerSecond();
        assertEquals(Math.rint(serverTicksPerSongTick), serverTicksPerSongTick, 1.0E-9, "a song tick is a whole number of server ticks");
    }

    @Test
    void theBundledSongKeepsTheNotesTogetherLow() throws IOException {
        NbsSong song = bundled();
        int[] perTick = new int[song.length()];
        for (int i = 0; i < song.noteCount(); i++) perTick[song.note(i).tick()]++;
        int peak = 0;
        for (int count : perTick) peak = Math.max(peak, count);
        assertTrue(peak <= 8, "at most 8 notes at the same time (" + peak + ")");
    }

    @Test
    void theBundledSongLoopsOnAQuietEnd() throws IOException {
        // the last two bars are only the guitar, the harp and the chime: no flute, no bass, no drum (the jump back to the loop point is smooth)
        NbsSong song = bundled();
        int from = song.length() - 32;
        java.util.Set<Integer> instruments = new java.util.TreeSet<>();
        for (int i = song.firstNoteAt(from); i < song.noteCount(); i++) instruments.add(song.note(i).instrument());
        assertTrue(java.util.Set.of(0, 5, 8).containsAll(instruments), "only guitar, harp and chime at the end: " + instruments);
        assertTrue(instruments.contains(5) && instruments.contains(0));
    }

    private static NbsSong bundled() throws IOException {
        return bundled("plains_adventure");
    }

    private static NbsSong bundled(String id) throws IOException {
        try (InputStream in = BgmTest.class.getResourceAsStream("/bgm/" + id + ".nbs")) {
            assertNotNull(in, id);
            return NbsSong.read(id, in.readAllBytes());
        }
    }

    private static final List<String> BUNDLED = List.of("plains_adventure", "atlas_cave", "atlas_vindicator", "atlas_dungeon", "atlas_dungeon_2", "atlas_vindicator_2", "cave_depths", "colossus_phase1", "colossus_phase2", "necromancer_phase1", "necromancer_phase2", "reaper_phase1", "reaper_phase2", "broodmother_phase1", "broodmother_phase2", "mirror_phase1", "mirror_phase2", "waltz_phase1", "waltz_phase2", "unkeyed_phase1", "unkeyed_phase2", "circuit_phase1", "circuit_phase2", "cloud_light", "cloud_remembered", "gate_gravity");

    @Test
    void everyBundledSongKeepsTheRules() throws IOException {
        for (String id : BUNDLED) {
            NbsSong song = bundled(id);
            double serverTicks = 20.0 / song.ticksPerSecond();
            assertEquals(Math.rint(serverTicks), serverTicks, 1.0E-9, id + ": the tempo divides 20");
            assertTrue(song.seconds() >= 120.0, id + ": at least two minutes (" + song.seconds() + ")");
            assertTrue(song.loops() && song.loopStart() == 64, id + ": loops from bar 5");
            assertTrue(song.title().matches(".+ \\((Field|Boss|Dungeon)\\d{3}\\)"), id + ": named like 'Title (Field001)': " + song.title());
            int[] perTick = new int[song.length()];
            for (int i = 0; i < song.noteCount(); i++) {
                NbsSong.Note note = song.note(i);
                assertTrue(note.key() >= 33 && note.key() <= 57, id + ": key " + note.key());
                perTick[note.tick()]++;
            }
            int peak = 0;
            for (int count : perTick) peak = Math.max(peak, count);
            assertTrue(peak <= 8, id + ": at most 8 notes at once (" + peak + ")");
        }
    }

    @Test
    void theAtlasSongsAreTheTwoLengthsWeMade() throws IOException {
        assertEquals(768, bundled("atlas_cave").length());
        assertEquals(1280, bundled("atlas_vindicator").length());
        assertEquals("Where the Titan Sleeps (Field002)", bundled("atlas_cave").title());
        assertEquals("The Axe at the Gate (Boss001)", bundled("atlas_vindicator").title());
        assertEquals(896, bundled("atlas_dungeon").length());
        assertEquals("Hollow Beneath the Giant (Dungeon001)", bundled("atlas_dungeon").title());
    }

    // ---- the songs of t35: a cave of crystals, and a boss in two phases that go over from bar to bar ------------------------------------------

    @Test
    void theBossPhasesAreOnePieceAndTheCaveSongIsOurs() throws IOException {
        NbsSong one = bundled("colossus_phase1");
        NbsSong two = bundled("colossus_phase2");
        assertEquals("Shoulders of Stone (Boss003)", one.title());
        assertEquals("Shoulders of Stone, Unbound (Boss003)", two.title());
        assertEquals(one.length(), two.length(), "the same bars");
        assertEquals(one.ticksPerSecond(), two.ticksPerSecond(), 1.0E-9, "the same tempo");
        assertEquals(one.loopStart(), two.loopStart(), "the same loop point");
        assertEquals(0, one.length() % 16, "whole bars (the change waits for a bar line)");
        assertEquals("Where the Crystals Hum (Dungeon003)", bundled("cave_depths").title());
        assertEquals(704, bundled("cave_depths").length());
    }

    @Test
    void aVoiceCanBeginInTheMiddleOfASong() throws IOException {
        NbsSong song = song(100, true, 0);
        List<Integer> keys = play(new BgmVoice(song, "scene", true, 1.0f, 0.0, 40.0), 2);
        assertEquals(List.of(33 + 40, 33 + 41), keys, "it begins at the tick it was told to, not at the start");
    }

    @Test
    void thePhaseGoesOverAtTheNextBarLineAndFromTheSameBar(@TempDir File folder) throws IOException {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        BgmChannel channel = new BgmChannel();
        Player player = player();
        channel.playScene(registry.song("colossus_phase1"), true, 0.0);
        for (int tick = 0; tick < 25; tick++) channel.tick(player, 1.0f);  // 10 song ticks a second: the clock is at 12.5
        channel.playSceneSynced(registry.song("colossus_phase2"), true, 1.5);
        assertTrue(channel.changePending());
        assertEquals("Shoulders of Stone (Boss003)", channel.nowPlaying(), "phase 1 goes on until the bar line");
        assertEquals(1, channel.voiceCount());

        for (int tick = 0; tick < 8; tick++) channel.tick(player, 1.0f);
        assertFalse(channel.changePending());
        assertEquals("Shoulders of Stone, Unbound (Boss003)", channel.nowPlaying());
        assertEquals(2, channel.voiceCount(), "phase 1 fades out while phase 2 fades in");
        double at = channel.positionOf("colossus_phase2");
        assertTrue(at >= 16.0 && at < 18.0, "phase 2 is at the bar after the one phase 1 was in (bar line 16): " + at);
    }

    @Test
    void songsThatDoNotFitGoOverAtOnceAndEndingTheSceneCancelsTheWait(@TempDir File folder) throws IOException {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        BgmChannel channel = new BgmChannel();
        Player player = player();
        channel.playScene(registry.song("atlas_dungeon"), true, 0.0);
        for (int tick = 0; tick < 10; tick++) channel.tick(player, 1.0f);
        channel.playSceneSynced(registry.song("atlas_vindicator"), true, 1.0);
        assertFalse(channel.changePending(), "another tempo and length: no waiting");
        assertEquals("The Axe at the Gate (Boss001)", channel.nowPlaying());

        channel.playScene(registry.song("colossus_phase1"), true, 0.0);
        for (int tick = 0; tick < 25; tick++) channel.tick(player, 1.0f);
        channel.playSceneSynced(registry.song("colossus_phase2"), true, 1.5);
        assertTrue(channel.changePending());
        channel.endScene(1.0);
        assertFalse(channel.changePending());
        for (int tick = 0; tick < 40; tick++) channel.tick(player, 1.0f);
        assertTrue(channel.silent(), "nothing comes back after the scene has ended");
    }

    // ---- songs without a restart --------------------------------------------------------------------------------------------------------

    private static BgmRegistry registryIn(File folder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        when(plugin.getResource(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> BgmTest.class.getResourceAsStream("/" + call.getArgument(0, String.class)));
        return new BgmRegistry(plugin);
    }

    private static byte[] resource(String id) throws IOException {
        try (InputStream in = BgmTest.class.getResourceAsStream("/bgm/" + id + ".nbs")) {
            return in.readAllBytes();
        }
    }

    /** What a note of the file really sounds as: the instruments do not sound in the same octaves (the sound of key 33 of each, as a MIDI number). */
    private static int sounding(int instrument, int key) {
        int[] base = {54, 30, -1, -1, -1, 42, 78, 90, 90, 90, 54, 66, 30, 54, 54, 54};
        return base[instrument] < 0 ? -1 : base[instrument] + key - 33;
    }

    @Test
    void theNewerSongsHaveNoFluteBellOrChimeAndSoundInTheLowerRegister() throws IOException {
        for (String id : List.of("atlas_dungeon", "atlas_vindicator", "atlas_dungeon_2", "atlas_vindicator_2", "cave_depths", "colossus_phase1", "colossus_phase2", "necromancer_phase1", "necromancer_phase2", "reaper_phase1", "reaper_phase2", "broodmother_phase1", "broodmother_phase2")) {
            NbsSong song = bundled(id);
            int lowest = Integer.MAX_VALUE;
            int highest = 0;
            for (int i = 0; i < song.noteCount(); i++) {
                NbsSong.Note note = song.note(i);
                assertFalse(List.of(6, 7, 8, 9).contains(note.instrument()), id + ": the flute, bell, chime and xylophone sound far above the rest (instrument " + note.instrument() + ")");
                int sound = sounding(note.instrument(), note.key());
                if (sound < 0) continue;
                lowest = Math.min(lowest, sound);
                highest = Math.max(highest, sound);
            }
            assertTrue(highest <= 78, id + ": nothing above F#5 (" + highest + ")");
            assertTrue(lowest >= 30, id + ": nothing below F#1 (" + lowest + ")");
        }
        assertEquals(896, bundled("atlas_dungeon_2").length());
        assertEquals(1280, bundled("atlas_vindicator_2").length());
        assertEquals("Below the Sleeping Giant (Dungeon002)", bundled("atlas_dungeon_2").title());
        assertEquals("Raise the Axe (Boss002)", bundled("atlas_vindicator_2").title());
    }

    @Test
    void theRenewedFirstSongsKeepTheirNamesAndFormAndAreInTheLowRegisterWithAtMostEightNotesAtOnce() throws IOException {
        // Dungeon001 and Boss001 were written again (no flute, bell or chime: see the test above); the name, the length and the loop point are the same as before
        assertEquals("Hollow Beneath the Giant (Dungeon001)", bundled("atlas_dungeon").title());
        assertEquals(896, bundled("atlas_dungeon").length());
        assertEquals("The Axe at the Gate (Boss001)", bundled("atlas_vindicator").title());
        assertEquals(1280, bundled("atlas_vindicator").length());
        for (String id : List.of("atlas_dungeon", "atlas_vindicator")) {
            NbsSong song = bundled(id);
            assertEquals(64, song.loopStart(), id + ": loops from bar 5");
            int[] perTick = new int[song.length()];
            for (int i = 0; i < song.noteCount(); i++) perTick[song.note(i).tick()]++;
            int peak = 0;
            for (int count : perTick) peak = Math.max(peak, count);
            assertTrue(peak <= 8, id + ": at most 8 notes at the same time (" + peak + ")");
        }
    }

    @Test
    void theNecromancerPhasesAreOnePiece() throws IOException {
        NbsSong one = bundled("necromancer_phase1");
        NbsSong two = bundled("necromancer_phase2");
        assertEquals("Reaper's Arc (Boss004)", one.title());
        assertEquals("Reaper's Arc, Sundered (Boss004)", two.title());
        assertEquals(1280, one.length());
        assertEquals(10.0, one.ticksPerSecond(), 1.0E-9, "fast: 150 BPM");
        assertEquals(one.length(), two.length(), "the same bars");
        assertEquals(one.ticksPerSecond(), two.ticksPerSecond(), 1.0E-9, "the same tempo");
        assertEquals(one.loopStart(), two.loopStart(), "the same loop point");
        assertEquals(0, one.length() % 16, "whole bars (the change waits for a bar line)");
    }

    @Test
    void theNecromancerTuneIsNarrowAndGoesByStepsAndPhaseTwoIsAHalfStepHigher() throws IOException {
        int[] top = new int[2];
        for (int phase = 1; phase <= 2; phase++) {
            NbsSong song = bundled("necromancer_phase" + phase);
            int lowest = Integer.MAX_VALUE;
            int highest = 0;
            int previousTick = -100;
            int previous = 0;
            for (int i = 0; i < song.noteCount(); i++) {   // the tune: the loud notes of the piano
                NbsSong.Note note = song.note(i);
                if (note.instrument() != 0 || note.volume() < 0.8f) continue;
                int sound = sounding(note.instrument(), note.key());
                lowest = Math.min(lowest, sound);
                highest = Math.max(highest, sound);
                if (note.tick() - previousTick <= 8) assertTrue(Math.abs(sound - previous) < 6, "phase " + phase + ": a leap of " + Math.abs(sound - previous) + " semitones at tick " + note.tick());
                previousTick = note.tick();
                previous = sound;
            }
            assertTrue(highest - lowest <= 14, "phase " + phase + ": the tune is at most a ninth wide (" + (highest - lowest) + ")");
            top[phase - 1] = highest;
        }
        assertEquals(top[0] + 1, top[1], "phase 2 is a half step higher");
    }

    @Test
    void theSecondReaperThemeIsOnePieceAHalfStepHigherAndNarrow() throws IOException {
        NbsSong one = bundled("reaper_phase1");
        NbsSong two = bundled("reaper_phase2");
        assertEquals("Silence of the Scythe (Boss005)", one.title());
        assertEquals("Silence of the Scythe, Unleashed (Boss005)", two.title());
        assertEquals(1280, one.length());
        assertEquals(one.length(), two.length(), "the same bars");
        assertEquals(one.ticksPerSecond(), two.ticksPerSecond(), 1.0E-9, "the same tempo");
        assertEquals(one.loopStart(), two.loopStart(), "the same loop point");
        int[] top = new int[2];
        for (int phase = 1; phase <= 2; phase++) {
            NbsSong song = phase == 1 ? one : two;
            int lowest = Integer.MAX_VALUE;
            int highest = 0;
            for (int i = 0; i < song.noteCount(); i++) {   // the melody: the loud notes of the piano
                NbsSong.Note note = song.note(i);
                if (note.instrument() != 0 || note.volume() < 0.8f) continue;
                int sound = sounding(note.instrument(), note.key());
                lowest = Math.min(lowest, sound);
                highest = Math.max(highest, sound);
            }
            assertTrue(highest - lowest <= 15, "phase " + phase + ": the melody is at most an octave and a minor third wide (" + (highest - lowest) + ")");
            top[phase - 1] = highest;
        }
        assertEquals(top[0] + 1, top[1], "phase 2 is a half step higher");
    }

    @Test
    void theBroodmotherPhasesAreOnePieceWithOddBarsAndPhaseTwoIsAHalfStepHigher() throws IOException {
        NbsSong one = bundled("broodmother_phase1");
        NbsSong two = bundled("broodmother_phase2");
        assertEquals("Mother of a Thousand Legs (Boss006)", one.title());
        assertEquals("Mother of a Thousand Legs, Swarming (Boss006)", two.title());
        assertEquals(1280, one.length());
        assertEquals(one.length(), two.length(), "the same bars");
        assertEquals(10.0, one.ticksPerSecond(), 1.0E-9, "150 BPM");
        assertEquals(one.loopStart(), two.loopStart(), "the same loop point");
        assertEquals(0, one.length() % 16, "whole bars of 16 ticks in all (the odd bars add up: 3 + 5 beats, eight of 3 and two of 4)");
        int[] top = new int[2];
        for (int phase = 1; phase <= 2; phase++) {
            NbsSong song = phase == 1 ? one : two;
            int highest = 0;
            for (int i = 0; i < song.noteCount(); i++) {   // the melody: the loud notes of the piano
                NbsSong.Note note = song.note(i);
                if (note.instrument() != 0 || note.volume() < 0.8f) continue;
                highest = Math.max(highest, sounding(note.instrument(), note.key()));
            }
            top[phase - 1] = highest;
        }
        assertEquals(top[0] + 1, top[1], "phase 2 is a half step higher");
    }

    @Test
    void theMirrorPhasesAreOnePieceAHalfStepHigherAndTheGlassStaysBelowTheHighestNotes() throws IOException {
        NbsSong one = bundled("mirror_phase1");
        NbsSong two = bundled("mirror_phase2");
        assertEquals("The Cracked Mirror Laughs (Boss007)", one.title());
        assertEquals("The Cracked Mirror Laughs, Shattered (Boss007)", two.title());
        assertEquals(1216, one.length());
        assertEquals(one.length(), two.length(), "the same bars");
        assertEquals(one.ticksPerSecond(), two.ticksPerSecond(), 1.0E-9, "the same tempo");
        assertEquals(one.loopStart(), two.loopStart(), "the same loop point");
        assertEquals(0, one.length() % 16, "the odd bars (seven beats, five beats) add up to whole bars of 16 ticks");
        int[] top = new int[2];
        boolean bell = false;
        boolean chime = false;
        boolean flute = false;
        for (int phase = 1; phase <= 2; phase++) {
            NbsSong song = phase == 1 ? one : two;
            int highest = 0;
            for (int i = 0; i < song.noteCount(); i++) {
                NbsSong.Note note = song.note(i);
                int sound = sounding(note.instrument(), note.key());
                if (note.instrument() == 10 && note.volume() >= 0.8f) highest = Math.max(highest, sound);   // the crack: the loud notes of the iron xylophone
                if (note.instrument() == 7 || note.instrument() == 8 || note.instrument() == 6) assertTrue(sound <= 99, "phase " + phase + ": the glass and the laugh stay below D#7 (" + sound + ")");
                bell |= note.instrument() == 7;
                chime |= note.instrument() == 8;
                flute |= note.instrument() == 6;
            }
            top[phase - 1] = highest;
        }
        assertTrue(bell && chime && flute, "the bell and the chime trill and the flute laughs");
        assertEquals(top[0] + 1, top[1], "phase 2 is a half step higher");
    }

    @Test
    void theWaltzPhasesAreOnePieceInThreeBeatsAndPhaseTwoIsAHalfStepHigher() throws IOException {
        NbsSong one = bundled("waltz_phase1");
        NbsSong two = bundled("waltz_phase2");
        assertEquals("Waltz at the Edge of Dark (Boss008)", one.title());
        assertEquals("Waltz at the Edge of Dark, Out of Step (Boss008)", two.title());
        assertEquals(1216, one.length());
        assertEquals(one.length(), two.length(), "the same bars");
        assertEquals(10.0, one.ticksPerSecond(), 1.0E-9, "150 BPM");
        assertEquals(one.loopStart(), two.loopStart(), "the same loop point");
        assertEquals(64, one.loopStart(), "the pickup and five bars of three beats are played once, then the loop starts on a bar line of the game");
        assertEquals(0, one.length() % 16, "bars of three beats (12 ticks) add up to whole bars of 16 ticks");
        int[] top = new int[2];
        for (int phase = 1; phase <= 2; phase++) {
            NbsSong song = phase == 1 ? one : two;
            int highest = 0;
            int firstBeats = 0;
            for (int i = 0; i < song.noteCount(); i++) {
                NbsSong.Note note = song.note(i);
                int sound = sounding(note.instrument(), note.key());
                if (note.instrument() == 0 && note.volume() >= 0.8f) highest = Math.max(highest, sound);   // the melody: the loud notes of the harp
                if (note.instrument() == 6) assertTrue(sound <= 91, "phase " + phase + ": the flute only an octave over the melody (" + sound + ")");
                if (note.instrument() == 1 && note.tick() >= 64 && (note.tick() - 64) % 12 == 0 && note.volume() >= 0.8f) firstBeats++;
            }
            assertTrue(firstBeats >= 60, "phase " + phase + ": the bass lands on the first beat of the bars (" + firstBeats + ")");
            top[phase - 1] = highest;
        }
        assertEquals(top[0] + 1, top[1], "phase 2 is a half step higher");
    }

    @Test
    void theUnkeyedPhasesAreOnePieceThatBuildsUpFromASilenceAndPhaseTwoHasTheBeatFromTheStart() throws IOException {
        NbsSong one = bundled("unkeyed_phase1");
        NbsSong two = bundled("unkeyed_phase2");
        assertEquals("Eyes Without a Key (Boss009)", one.title());
        assertEquals("Eyes Without a Key, Unwritten (Boss009)", two.title());
        assertEquals(1216, one.length());
        assertEquals(one.length(), two.length(), "the same bars");
        assertEquals(one.ticksPerSecond(), two.ticksPerSecond(), 1.0E-9, "the same tempo");
        assertEquals(one.loopStart(), two.loopStart(), "the same loop point");
        assertEquals(64, one.loopStart(), "the click of the intro is played once");
        int[] drumsInObservation = new int[2];
        int[] drumsInCollapse = new int[2];
        for (int phase = 1; phase <= 2; phase++) {
            NbsSong song = phase == 1 ? one : two;
            for (int i = 0; i < song.noteCount(); i++) {
                NbsSong.Note note = song.note(i);
                int sound = sounding(note.instrument(), note.key());
                if (note.instrument() == 7 || note.instrument() == 8) assertTrue(sound <= 99, "phase " + phase + ": the bell and the glass stay below D#7 (" + sound + ")");
                boolean drum = note.instrument() == 2 || note.instrument() == 3;
                if (drum && note.tick() >= 64 && note.tick() < 64 + 384) drumsInObservation[phase - 1]++;
                if (drum && note.tick() >= 64 + 768) drumsInCollapse[phase - 1]++;
            }
        }
        assertEquals(0, drumsInObservation[0], "phase 1: the observation has no drums");
        assertTrue(drumsInObservation[1] > 20, "phase 2: the beat is there from the first bar");
        assertTrue(drumsInCollapse[0] > 100 && drumsInCollapse[1] > 100, "both: the collapse has the whole drum kit");
    }

    @Test
    void theCircuitPhasesUseFiveInstrumentsFewRolesAndABreakWithoutBassAndDrums() throws IOException {
        NbsSong one = bundled("circuit_phase1");
        NbsSong two = bundled("circuit_phase2");
        assertEquals("Holy Circuit (Boss010)", one.title());
        assertEquals("Holy Circuit, Overclocked (Boss010)", two.title());
        assertEquals(2464, one.length());
        assertEquals(20.0, one.ticksPerSecond(), 1.0E-9, "200 BPM: a quarter note is 6 ticks");
        assertEquals(one.length(), two.length(), "the same bars");
        assertEquals(one.ticksPerSecond(), two.ticksPerSecond(), 1.0E-9, "the same tempo");
        assertEquals(one.loopStart(), two.loopStart(), "the same loop point");
        int breakStart = 64 + 64 * 24;   // the break: bars 64 to 71 of the loop (bars of 24 ticks)
        int breakEnd = breakStart + 8 * 24;
        for (int phase = 1; phase <= 2; phase++) {
            NbsSong song = phase == 1 ? one : two;
            java.util.Set<Integer> instruments = new java.util.TreeSet<>();
            java.util.Map<Integer, Integer> perTick = new java.util.HashMap<>();
            for (int i = 0; i < song.noteCount(); i++) {
                NbsSong.Note note = song.note(i);
                instruments.add(note.instrument());
                perTick.merge(note.tick(), 1, Integer::sum);
                int sound = sounding(note.instrument(), note.key());
                if (note.instrument() == 1) assertTrue(sound < 48 + phase - 1, "phase " + phase + ": the bass stays below C3 (" + sound + ")");
                if (note.instrument() == 5) assertTrue(sound >= 53 && sound <= 66, "phase " + phase + ": the guitar is in its own band (" + sound + ")");
                if (note.instrument() == 15) assertTrue(sound >= 66 + phase - 1 && sound <= 78, "phase " + phase + ": the pling is in the lead band (" + sound + ")");
                if (note.tick() >= breakStart && note.tick() < breakEnd) assertTrue(note.instrument() == 15 || note.instrument() == 5, "phase " + phase + ": the break has no bass and no drums (instrument " + note.instrument() + " at " + note.tick() + ")");
            }
            assertEquals(java.util.Set.of(1, 2, 3, 5, 15), instruments, "phase " + phase + ": only the bass, kick, snare, guitar and pling");
            assertTrue(java.util.Collections.max(perTick.values()) <= 5, "phase " + phase + ": few notes at once");
        }
    }

    @Test
    void theCloudSongIsSlowAiryAndWithoutDrums() throws IOException {
        NbsSong song = bundled("cloud_light");
        assertEquals("Light Between the Clouds (Field003)", song.title());
        assertEquals(768, song.length());
        assertEquals(5.0, song.ticksPerSecond(), 1.0E-9, "75 BPM");
        int bells = 0;
        for (int i = 0; i < song.noteCount(); i++) {
            NbsSong.Note note = song.note(i);
            assertTrue(note.instrument() != 2 && note.instrument() != 3 && note.instrument() != 4, "no drums");
            assertTrue(note.instrument() != 6 && note.instrument() != 8, "no flute and no chime (they sound too high)");
            if (note.instrument() == 7) {
                bells++;
                assertTrue(sounding(note.instrument(), note.key()) <= 91 && note.volume() <= 0.4f, "the bell is one low glint, quiet");
            }
        }
        assertTrue(bells > 0 && bells < 12, "a few glints of the bell (" + bells + ")");
    }

    @Test
    void theRememberedCloudSongRipplesWithoutDrumsAndLiftsAHalfStepAtTwoThirds() throws IOException {
        NbsSong song = bundled("cloud_remembered");
        assertEquals("Clouds Remembered (Field004)", song.title());
        assertEquals(832, song.length());
        assertEquals(20.0 / 3.0, song.ticksPerSecond(), 1.0E-9, "100 BPM");
        int lift = 64 + 32 * 16;   // intro 4 bars, then A, A, calm, A (8 bars each), then the lift
        int guitar = 0;
        int before = 0;
        int after = 0;
        for (int i = 0; i < song.noteCount(); i++) {
            NbsSong.Note note = song.note(i);
            assertTrue(note.instrument() != 2 && note.instrument() != 3 && note.instrument() != 4, "no drums");
            assertTrue(note.instrument() != 6 && note.instrument() != 8, "no flute and no chime");
            if (note.instrument() == 5) guitar++;
            if (note.instrument() == 0 && note.volume() >= 0.8f) {
                int sound = sounding(note.instrument(), note.key());
                if (note.tick() < lift) before = Math.max(before, sound);
                else after = Math.max(after, sound);
            }
        }
        assertTrue(guitar > 500, "the ripple of sixteenths never stops (" + guitar + ")");
        assertEquals(before + 1, after, "the lift is a half step higher");
    }

    @Test
    void theGateSongIsDeepHasADipWithoutBassAndAClimaxOverADPedal() throws IOException {
        NbsSong song = bundled("gate_gravity");
        assertEquals("The Gate Where Gravity Turns (Dungeon004)", song.title());
        assertEquals(832, song.length());
        assertEquals(20.0 / 3.0, song.ticksPerSecond(), 1.0E-9, "100 BPM");
        int dip = 14 * 16;       // intro 4 bars + A 10 bars, then the dip of 4 bars
        int climax = 26 * 16;    // ... + the rise of 8 bars, then the climax of 8 bars
        int deep = 0;
        for (int i = 0; i < song.noteCount(); i++) {
            NbsSong.Note note = song.note(i);
            int sound = sounding(note.instrument(), note.key());
            assertTrue(note.instrument() != 6 && note.instrument() != 7 && note.instrument() != 8, "no flute, bell or chime: it is a deep piece");
            if (note.instrument() == 1 || note.instrument() == 12) deep++;
            if (note.tick() >= dip && note.tick() < dip + 4 * 16) assertTrue(note.instrument() != 1 && note.instrument() != 12, "the bass is gone in the dip");
            if (note.tick() >= climax && note.tick() < climax + 8 * 16 && (note.instrument() == 1 || note.instrument() == 12)) assertEquals(38, sound, "the climax stands on a D pedal");
        }
        assertTrue(deep * 3 >= song.noteCount(), "a third of the notes are the bass and the drone (" + deep + " of " + song.noteCount() + ")");
    }

    @Test
    void bundledSongsGoToAFolderOfTheirOwn(@TempDir File folder) {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        assertEquals(26, registry.songs().size());
        assertTrue(new File(folder, "bgm-bundled/atlas_cave.nbs").isFile());
        assertFalse(new File(folder, "bgm/atlas_cave.nbs").exists(), "nothing in bgm/ that the yml repository could clash with");
        assertEquals("bundled", registry.origin("atlas_cave"));
    }

    @Test
    void aSongOfTheFolderComesWithoutARestartAndReplacesTheBundledOne(@TempDir File folder) throws IOException {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        assertNull(registry.refreshIfChanged(), "nothing changed");
        NbsSong bundledCave = registry.song("atlas_cave");

        // a new song comes (the yml repository was pulled)
        java.nio.file.Files.write(new File(folder, "bgm/new_song.nbs").toPath(), resource("atlas_dungeon"));
        String changes = registry.refreshIfChanged();
        assertNotNull(changes);
        assertTrue(changes.contains("+new_song"), changes);
        assertEquals("bgm", registry.origin("new_song"));
        assertSame(bundledCave, registry.song("atlas_cave"), "a song that did not change is not read again (a playing song does not start over)");
        assertNull(registry.refreshIfChanged());

        // a song of the same id as a bundled one replaces it, and the bundled one is back when the file goes
        java.nio.file.Files.write(new File(folder, "bgm/atlas_cave.nbs").toPath(), resource("atlas_dungeon"));
        assertTrue(registry.refreshIfChanged().contains("~atlas_cave"));
        assertEquals("bgm", registry.origin("atlas_cave"));
        assertEquals(896, registry.song("atlas_cave").length());
        assertTrue(new File(folder, "bgm/atlas_cave.nbs").delete());
        assertTrue(registry.refreshIfChanged().contains("~atlas_cave"));
        assertEquals("bundled", registry.origin("atlas_cave"));
        assertEquals(768, registry.song("atlas_cave").length());
    }

    @Test
    void aSongThatCannotBeReadIsLeftOutAndTheRestStays(@TempDir File folder) throws IOException {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        java.nio.file.Files.write(new File(folder, "bgm/broken.nbs").toPath(), new byte[]{1, 2, 3});
        registry.refreshIfChanged();
        assertNull(registry.song("broken"));
        assertEquals(26, registry.songs().size());
        assertFalse(registry.problems().isEmpty());
    }

    @Test
    void copiesOfTheBundledSongsOfAnEarlierVersionAreTakenAwayAndEditedOnesStay(@TempDir File folder) throws IOException {
        File bgm = new File(folder, "bgm");
        assertTrue(bgm.mkdirs());
        java.nio.file.Files.write(new File(bgm, "atlas_cave.nbs").toPath(), resource("atlas_cave"));      // as the earlier version wrote it
        byte[] edited = java.util.Arrays.copyOf(resource("atlas_vindicator"), resource("atlas_vindicator").length - 1);
        java.nio.file.Files.write(new File(bgm, "atlas_vindicator.nbs").toPath(), edited);                 // someone changed it
        java.nio.file.Files.writeString(new File(bgm, ".bundled.yml").toPath(), "atlas_cave: x\n");
        registryIn(folder).reload();
        assertFalse(new File(bgm, "atlas_cave.nbs").exists(), "an unedited copy is taken away");
        assertTrue(new File(bgm, "atlas_vindicator.nbs").isFile(), "an edited one stays");
        assertFalse(new File(bgm, ".bundled.yml").exists());
    }

    @Test
    void volumesCanBeEditedInTheFileAndOurOwnSaveIsNotAChange(@TempDir File folder) throws IOException {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        registry.put(new BgmVolume("a", "world", new int[]{0, 0, 0}, new int[]{9, 9, 9}, "atlas_cave"));
        assertNull(registry.refreshIfChanged(), "our own write");

        java.nio.file.Files.writeString(new File(folder, "bgm/volumes.yml").toPath(),
                "volumes:\n  b:\n    world: dungeon_*_cave*\n    min: [0, 0, 0]\n    max: [20, 30, 20]\n    song: atlas_dungeon\n    priority: 2\n");
        String changes = registry.refreshIfChanged();
        assertNotNull(changes);
        assertNull(registry.volume("a"));
        assertNotNull(registry.volume("b"));
        assertEquals(2, registry.volume("b").priority());
    }

    @Test
    void aPlayerGoesOverToASongThatWasReplacedAndKeepsAnUnchangedOne(@TempDir File folder) throws IOException {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        BgmVolume volume = new BgmVolume("cave", "world", new int[]{0, 0, 0}, new int[]{9, 9, 9}, "atlas_cave");
        BgmChannel channel = new BgmChannel();
        channel.follow(volume, registry);
        assertEquals("Where the Titan Sleeps (Field002)", channel.nowPlaying());
        assertEquals(1, channel.voiceCount());

        // the volumes are read again, the songs did not change: the same song goes on (no second voice)
        java.nio.file.Files.writeString(new File(folder, "bgm/volumes.yml").toPath(), "volumes: {}\n");
        registry.refreshIfChanged();
        channel.follow(volume, registry);
        assertEquals(1, channel.voiceCount());

        // the song is replaced by a file of the folder: the new one starts, the old one fades out
        java.nio.file.Files.write(new File(folder, "bgm/atlas_cave.nbs").toPath(), resource("atlas_dungeon"));
        registry.refreshIfChanged();
        channel.follow(volume, registry);
        assertEquals(2, channel.voiceCount());
        assertEquals("Hollow Beneath the Giant (Dungeon001)", channel.nowPlaying());
    }

    // ---- scenes (a dungeon run) and listens --------------------------------------------------------------------------------------------

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getEyeLocation()).thenReturn(new Location(null, 0, 64, 0));
        return player;
    }

    @Test
    void aSceneSongPlaysInsteadOfTheVolumesAndGivesWayToAListen(@TempDir File folder) throws IOException {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        BgmVolume volume = new BgmVolume("plains", "world", new int[]{0, 0, 0}, new int[]{9, 9, 9}, "plains_adventure");
        BgmChannel channel = new BgmChannel();
        channel.follow(volume, registry);
        assertEquals("Under an Endless Sky (Field001)", channel.nowPlaying());

        // the scene: its song goes over, and the volumes are not followed while it lasts
        channel.playScene(registry.song("atlas_dungeon"), true, 2.0);
        assertEquals("Hollow Beneath the Giant (Dungeon001)", channel.nowPlaying());
        assertEquals(2, channel.voiceCount(), "the song of the volume fades out");
        channel.follow(null, registry);
        channel.follow(volume, registry);
        assertEquals("Hollow Beneath the Giant (Dungeon001)", channel.nowPlaying(), "the volumes do not change the song of the scene");
        assertEquals(2, channel.voiceCount());

        // the same song again changes nothing; another one is gone over to
        channel.playScene(registry.song("atlas_dungeon"), true, 2.0);
        assertEquals(2, channel.voiceCount());
        channel.playScene(registry.song("atlas_vindicator"), true, 2.0);
        assertEquals("The Axe at the Gate (Boss001)", channel.nowPlaying());
        assertEquals(3, channel.voiceCount());

        // a listen is over everything; when it ends the scene comes back
        channel.playManual(registry.song("atlas_cave"), true, 0.5);
        assertEquals("Where the Titan Sleeps (Field002)", channel.nowPlaying());
        assertEquals("atlas_cave", channel.manualSong().id());
        channel.stopManual(0.5);
        assertEquals("The Axe at the Gate (Boss001)", channel.nowPlaying(), "the scene song is back");
        assertNull(channel.manualSong());

        // the end of the scene: its song fades out and the volume is followed again
        channel.endScene(1.0);
        assertNull(channel.nowPlaying());
        channel.follow(volume, registry);
        assertEquals("Under an Endless Sky (Field001)", channel.nowPlaying());
    }

    @Test
    void aFadedOutSongIsTakenAway(@TempDir File folder) throws IOException {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        BgmChannel channel = new BgmChannel();
        channel.playScene(registry.song("atlas_dungeon"), true, 0.0);
        channel.endScene(1.0);
        Player player = player();
        for (int tick = 0; tick < 25; tick++) channel.tick(player, 1.0f);
        assertEquals(0, channel.voiceCount());
        assertTrue(channel.silent());
    }

    @Test
    void aSceneSongStartsAfterAListenIfItWasAskedForDuringIt(@TempDir File folder) throws IOException {
        BgmRegistry registry = registryIn(folder);
        registry.reload();
        BgmChannel channel = new BgmChannel();
        channel.playManual(registry.song("atlas_cave"), true, 0.5);
        channel.playScene(registry.song("atlas_dungeon"), true, 2.0);
        assertEquals("Where the Titan Sleeps (Field002)", channel.nowPlaying(), "the listen goes on");
        channel.stopManual(0.5);
        assertEquals("Hollow Beneath the Giant (Dungeon001)", channel.nowPlaying());
    }

    @Test
    void theNameOfASongTellsItsKind() {
        Bgm.SongInfo field = new Bgm.SongInfo("a", "Under an Endless Sky (Field001)", 154.0, "bundled");
        assertEquals("フィールド", field.category());
        assertEquals("フィールド001", field.code());
        assertEquals("Under an Endless Sky", field.shortTitle());
        assertEquals("2:34", field.length());
        assertEquals("ボス", new Bgm.SongInfo("b", "The Axe at the Gate (Boss001)", 128.0, "bundled").category());
        assertEquals("ダンジョン002", new Bgm.SongInfo("c", "Deep (Dungeon002)", 60.0, "bgm").code());
        Bgm.SongInfo plain = new Bgm.SongInfo("d", "Some Song", 59.6, "bgm");
        assertEquals("", plain.category());
        assertEquals("", plain.code());
        assertEquals("Some Song", plain.shortTitle());
        assertEquals("1:00", plain.length());
    }

    @Test
    void aWorldPatternFitsTheTemplateAndEveryCopyOfAnInstanceDungeon() {
        BgmVolume volume = new BgmVolume("cave", "dungeon_*_cave*", new int[]{0, 0, 0}, new int[]{9, 9, 9}, "atlas_dungeon");
        assertTrue(volume.worldIsPattern());
        assertTrue(volume.inWorld("dungeon_template_cave"));
        assertTrue(volume.inWorld("dungeon_instance_cave_1"));
        assertTrue(volume.inWorld("dungeon_instance_cave_27"));
        assertFalse(volume.inWorld("dungeon_instance_mine_1"));
        assertFalse(volume.inWorld("world"));
        BgmVolume plain = new BgmVolume("p", "world", new int[]{0, 0, 0}, new int[]{9, 9, 9}, "a");
        assertFalse(plain.worldIsPattern());
        assertTrue(plain.inWorld("world"));
        assertFalse(plain.inWorld("world_nether"));
        assertTrue(new BgmVolume("s", "*_nether", new int[]{0, 0, 0}, new int[]{1, 1, 1}, "a").inWorld("world_nether"));
        assertFalse(new BgmVolume("s", "*_nether", new int[]{0, 0, 0}, new int[]{1, 1, 1}, "a").inWorld("world"));
    }

    @Test
    void refusesAFileThatIsNotASong() {
        assertThrows(IOException.class, () -> NbsSong.read("x", new byte[]{1, 2, 3}));
        assertThrows(IOException.class, () -> NbsSong.read("x", new byte[0]));
    }

    @Test
    void aSongPlaysOnceAndEnds() throws IOException {
        BgmVoice voice = new BgmVoice(song(4, false, 0), "t", false, 1.0f, 0.0);
        assertEquals(List.of(33, 34, 35, 36), play(voice, 10));
        assertTrue(voice.done());
    }

    @Test
    void aLoopGoesBackToTheLoopPoint() throws IOException {
        BgmVoice voice = new BgmVoice(song(4, true, 2), "t", true, 1.0f, 0.0);
        assertEquals(List.of(33, 34, 35, 36, 35, 36, 35, 36, 35, 36), play(voice, 10));
        assertFalse(voice.done());
    }

    @Test
    void theNotesAreAsLoudAsTheGainsMultiplied() throws IOException {
        BgmVoice voice = new BgmVoice(song(2, false, 0), "t", false, 0.5f, 0.0);
        List<Float> volumes = new ArrayList<>();
        for (int i = 0; i < 4; i++) voice.tick((instrument, key, volume, pan) -> volumes.add(volume), 0.8f);
        assertEquals(2, volumes.size());
        assertEquals(0.4f, volumes.get(0), 1.0E-6f);
    }

    @Test
    void aSongAtZeroLoudnessIsSilentButKeepsItsPlace() throws IOException {
        BgmVoice voice = new BgmVoice(song(4, false, 0), "t", false, 1.0f, 0.0);
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 2; i++) voice.tick((instrument, key, volume, pan) -> keys.add(key), 0.0f);
        for (int i = 0; i < 6; i++) voice.tick((instrument, key, volume, pan) -> keys.add(key), 1.0f);
        assertEquals(List.of(35, 36), keys);
    }

    @Test
    void fadesInAndOut() throws IOException {
        BgmVoice voice = new BgmVoice(song(40, true, 0), "t", true, 1.0f, 1.0);
        assertEquals(0.0f, voice.gain(), 1.0E-6f);
        play(voice, 10);
        assertEquals(0.5f, voice.gain(), 0.01f);
        play(voice, 10);
        assertEquals(1.0f, voice.gain(), 1.0E-6f);
        voice.fadeOut(0.5);
        assertTrue(voice.fadingOut());
        play(voice, 5);
        assertEquals(0.5f, voice.gain(), 0.01f);
        assertFalse(voice.done());
        play(voice, 5);
        assertTrue(voice.done());
        // coming back before it is gone
        BgmVoice back = new BgmVoice(song(40, true, 0), "t", true, 1.0f, 0.0);
        back.fadeOut(1.0);
        play(back, 10);
        back.fadeIn(1.0);
        assertFalse(back.fadingOut());
        play(back, 20);
        assertEquals(1.0f, back.gain(), 1.0E-6f);
    }

    // ---- the boxes ---------------------------------------------------------------------------------------------------------------------

    @Test
    void theBoxThatWinsIsTheOneWithTheHigherPriorityThenTheSmaller(@TempDir File folder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        BgmRegistry registry = new BgmRegistry(plugin);
        BgmVolume big = new BgmVolume("big", "world", new int[]{0, 0, 0}, new int[]{9, 9, 9}, "a");
        BgmVolume small = new BgmVolume("small", "world", new int[]{2, 2, 2}, new int[]{4, 4, 4}, "b");
        registry.put(big);
        registry.put(small);

        Location both = new Location(world, 3.5, 3.5, 3.5);
        Location onlyBig = new Location(world, 8.5, 3.5, 3.5);
        Location outside = new Location(world, 30.5, 3.5, 3.5);
        Location justOutside = new Location(world, 10.5, 3.5, 3.5);

        assertSame(small, registry.at(both, null, 1.5), "equal priority: the smaller box");
        big.setPriority(2);
        assertSame(big, registry.at(both, null, 1.5), "the higher priority");
        assertSame(big, registry.at(onlyBig, null, 1.5));
        assertNull(registry.at(outside, null, 1.5));
        assertNull(registry.at(justOutside, null, 1.5), "not in a box when none was playing");
        assertSame(big, registry.at(justOutside, big, 1.5), "a box that plays is kept a little outside its edge");
        assertNull(registry.at(outside, big, 1.5), "but not far outside");
        assertSame(big, registry.at(both, small, 1.5), "a box that wins takes over");

        World other = mock(World.class);
        when(other.getName()).thenReturn("nether");
        assertNull(registry.at(new Location(other, 3.5, 3.5, 3.5), null, 1.5), "another world");
    }

    @Test
    void theBoxesAreKeptInAFile(@TempDir File folder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        BgmRegistry registry = new BgmRegistry(plugin);
        BgmVolume volume = new BgmVolume("plains", "world", new int[]{10, 60, -5}, new int[]{-10, 90, 5}, "plains_adventure");
        volume.setPriority(3);
        volume.setVolume(70);
        volume.setFade(2.5);
        volume.setLoop(false);
        registry.put(volume);

        BgmRegistry again = new BgmRegistry(plugin);
        again.reload();
        BgmVolume read = again.volume("plains");
        assertNotNull(read);
        assertEquals("world", read.world());
        assertEquals(-10, read.min()[0]);
        assertEquals(10, read.max()[0]);
        assertEquals(3, read.priority());
        assertEquals(70, read.volume());
        assertEquals(2.5, read.fade(), 1.0E-9);
        assertFalse(read.loop());
        assertEquals("plains_adventure", read.song());
    }
}
