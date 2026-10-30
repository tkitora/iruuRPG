#!/usr/bin/env python3
"""[The first version, kept because Dungeon002/Boss002 are written from its tune; the song of the game is now made by the new script.] Makes atlas_dungeon.nbs: "Hollow Beneath the Giant (Dungeon001)", the theme inside the Atlas cave dungeon (the first dungeon; a short one).

    python3 tools/bgm/make_atlas_dungeon_bgm.py            writes src/main/resources/bgm/atlas_dungeon.nbs
    python3 tools/bgm/make_atlas_dungeon_bgm.py out.nbs    writes somewhere else

E Phrygian / minor, 4/4, 100 BPM (one tick = a sixteenth; 6.67 ticks a second = 20 / 3, so a song tick is exactly 3 server ticks), 56 bars (2:14). Inside the cave: darker and
more tense than the theme of the way to it ("Where the Titan Sleeps"), with a steady pulse (the bass on every beat, a soft heartbeat), the half step above the tonic (F over E:
the Phrygian colour), water drops and the echo of the cave. Still the first dungeon: tense, not frightening, and one piece from start to end (no phases).
Form: intro, A, B (the pulse builds), a quiet interlude, A' (a second flute, a drone), B' (a bell on the melody), C (the swell), coda. It loops from bar 5, and the coda stops on
the dominant (B7) so that the jump back to A (E minor) is smooth.

The motif of Atlas (the place): E5 B4 G4 E4 - a falling E minor triad, the plains theme turned upside down; here it opens the piece in dotted notes and returns at the top of the swell.
Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 20.0 / 3.0    # ticks a second (a divisor of 20: a song tick is 3 server ticks); a tick is a sixteenth note, a quarter is 4 ticks: 100 BPM
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "Hollow Beneath the Giant (Dungeon001)"
E_MINOR = scale("E3 F#3 G3 A3 B3 C4 D4 E4 F#4 G4 A4 B4 C5 D5 E5 F#5")

CHORDS = {  # the notes of the chord for the harp, low to high; the bass root
    "Em": ([59, 64, 67, 71, 76], 64),
    "F": ([57, 60, 65, 69, 72], 65),
    "Dm": ([57, 62, 65, 69, 74], 62),
    "C": ([60, 64, 67, 72, 76], 60),
    "G": ([55, 59, 62, 67, 71], 55),
    "Am": ([57, 60, 64, 69, 72], 57),
    "B7": ([59, 63, 66, 69, 71], 59),
}

INTRO_CHORDS = ["Em", "Em", "F", "B7"]
INTRO_HARP = ["r:16", "r:16", "E5:8 B4:8", "C5:8 B4:8"]
A_CHORDS = ["Em", "F", "Em", "F", "Em", "Dm", "C", "B7"]
A_MELODY = [
    "E5:6 B4:2 G4:4 E4:4",     # the motif of Atlas, dotted
    "C5:4 A4:4 F4:8",
    "E5:4 B4:4 G4:4 B4:4",
    "A4:6 F4:2 E4:8",          # the half step: F falls to E
    "E5:6 B4:2 G4:4 E4:4",
    "D5:4 A4:4 F4:4 A4:4",
    "E5:4 C5:4 G4:8",
    "F#4:4 A4:4 B4:8",
]
B_CHORDS = ["Em", "Em", "F", "F", "Am", "B7", "Em", "Em"]
B_MELODY = [
    "E4:2 G4:2 B4:4 G4:4 E4:4",
    "B4:4 E5:4 B4:4 G4:4",
    "F4:2 A4:2 C5:4 A4:4 F4:4",
    "C5:4 F5:4 C5:4 A4:4",
    "A4:2 C5:2 E5:4 C5:4 A4:4",
    "D#5:4 F#5:4 D#5:4 B4:4",
    "E5:4 B4:4 G4:4 B4:4",
    "E4:8 r:8",
]
QUIET_CHORDS = ["Em", "Em", "F", "F", "Am", "Am", "B7", "B7"]
C_CHORDS = ["Em", "F", "G", "Am", "F", "B7", "Em", "B7"]
C_MELODY = [                  # the swell: the motif at the top, then double time
    "E5:4 B4:4 G4:4 E4:4",
    "F5:4 C5:4 A4:4 C5:4",
    "D5:4 G4:4 B4:4 D5:4",
    "E5:4 C5:4 A4:8",
    "F5:6 C5:2 A4:8",
    "D#5:2 F#5:2 B4:4 D#5:4 F#5:4",
    "E5:2 B4:2 G4:4 E5:2 B4:2 G4:4",
    "F#5:8 D#5:8",
]
CODA_CHORDS = ["Em", "F", "Em", "B7"]
CODA_HARP = ["E5:8 B4:8", "C5:8 A4:8", "G4:8 E4:8", "F#4:16"]

# (name, chords, melody, who plays the melody, the voices, the loudness of the accompaniment, of the melody)
SECTIONS = [
    ("intro", INTRO_CHORDS, None, None, {"harp", "bass", "drops", "harp-intro", "drone"}, 1.0, 1.0),
    ("A", A_CHORDS, A_MELODY, "flute", {"harp", "bass", "drops", "echo"}, 1.0, 1.0),
    ("B", B_CHORDS, B_MELODY, "flute", {"harp", "bass", "drops", "echo", "heart", "pulse"}, 1.0, 1.0),
    ("quiet", QUIET_CHORDS, None, None, {"harp-soft", "bass-soft", "drops-more"}, 0.7, 1.0),
    ("A2", A_CHORDS, A_MELODY, "flute", {"harp", "bass", "drops", "echo", "harmony", "heart", "drone"}, 1.0, 1.0),
    ("B2", B_CHORDS, B_MELODY, "flute", {"harp", "bass", "drops", "echo", "harmony", "heart", "pulse", "bell"}, 1.0, 1.05),
    ("C", C_CHORDS, C_MELODY, "flute", {"harp", "bass", "heart", "harmony", "bell", "pulse"}, 1.0, 1.15),
    ("coda", CODA_CHORDS, CODA_HARP, "harp", {"harp-soft", "bass-soft", "drops"}, 0.6, 0.8),
]

ARPEGGIO = [0, 2, 1, 3, 2, 4, 3, 1]   # eight eighths a bar through the five notes of the chord, low to high
ECHO_DELAY = 4                         # ticks (0.6 s)


def drop_ticks(bar_number, count):
    """Where the water drops fall in a bar: the same every time (a small fixed generator)."""
    state = (bar_number * 2654435761 + 54321) & 0xFFFFFFFF
    ticks = []
    for _ in range(count):
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        ticks.append(3 + (state >> 8) % 12)
    return sorted(set(ticks))


def build():
    melody = Layer("Flute (Atlas motif)", 100, 100)
    bell = Layer("Bell (on the melody)", 40, 125)
    harmony = Layer("Flute (a third below)", 55, 80)
    echo = Layer("Flute (echo of the cave)", 45, 150)
    harp = Layer("Harp (arpeggio)", 70, 90)
    harp_melody = Layer("Harp (melody, intro, coda)", 80, 110)
    bass = Layer("Bass (the pulse)", 85, 100)
    pulse = Layer("Guitar (muted pulse)", 55, 65)
    drops = Layer("Chime (water drops)", 50, 125)
    heart = Layer("Heartbeat (soft drum)", 60, 100)
    drone = Layer("Didgeridoo (the earth)", 40, 100)
    layers = [melody, bell, harmony, echo, harp, harp_melody, bass, pulse, drops, heart, drone]

    total = sum(len(section[1]) for section in SECTIONS) * BAR
    bar_number = 0
    for name, chords, tune, tune_instrument, voices, accompaniment, lead in SECTIONS:
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            tones, root = CHORDS[chord]
            soft = "harp-soft" in voices
            if "harp" in voices or soft:
                for step, tone_index in enumerate(ARPEGGIO):
                    base = 42 if step == 0 else 34
                    harp.put(start + step * 2, HARP, tones[tone_index], velocity(base * (0.7 if soft else 1.0), accompaniment))
            if "bass" in voices:  # a steady pulse on every beat
                for tick in (0, 4, 8, 12):
                    bass.put(start + tick, BASS, root, velocity(66 if tick == 0 else 50, accompaniment))
            if "bass-soft" in voices:
                for tick in (0, 8):
                    bass.put(start + tick, BASS, root, 48 if tick == 0 else 36)
            if "pulse" in voices:  # the muted guitar on the off beats
                for tick in (2, 6, 10, 14):
                    pulse.put(start + tick, GUITAR, root, 34)
            if "heart" in voices:
                for tick, vel in ((0, 46), (3, 30)) + (((8, 38), (11, 24)) if name == "C" else ()):
                    heart.put(start + tick, BASEDRUM, 62, vel)
            if "drone" in voices and index % 2 == 0:
                drone.put(start, DIDGERIDOO, root, 36)
            if "drops" in voices or "drops-more" in voices:
                count = 2 if "drops-more" in voices else 1 if bar_number % 2 == 0 else 0
                for offset, tick in enumerate(drop_ticks(bar_number, count)):
                    note = (76, 71, 74, 78)[(bar_number + offset) % 4]
                    drops.put(start + tick, CHIME, note, 28 + 6 * offset)
            if "harp-intro" in voices:
                tick = 0
                for note, length in parse_bar(INTRO_HARP[index]):
                    if note is not None:
                        harp_melody.put(start + tick, HARP, note, 56)
                    tick += length
            if tune is not None:
                tick = 0
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        if tune_instrument == "harp":
                            harp_melody.put(start + tick, HARP, note, velocity(70 if tick == 0 else 62, lead))
                        else:
                            melody.put(start + tick, FLUTE, note, velocity(66 if tick == 0 else 58, lead))
                            if "echo" in voices and start + tick + ECHO_DELAY < total:
                                echo.put(start + tick + ECHO_DELAY, FLUTE, note, 26)
                            if "harmony" in voices:
                                harmony.put(start + tick, FLUTE, third_below(note, E_MINOR), 34)
                            if "bell" in voices:
                                bell.put(start + tick, BELL, note, 26)
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("atlas_dungeon_v1.nbs")
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE, "Theme inside the Atlas cave dungeon, the first dungeon. Dark and tense but not frightening, 56 bars, loops from bar 5 (the intro is played once).",
              LOOP_START_BAR)
    report(target, layers, length, TPS)


if __name__ == "__main__":
    main()
