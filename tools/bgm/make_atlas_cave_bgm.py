#!/usr/bin/env python3
"""Makes atlas_cave.nbs: "Where the Titan Sleeps (Field002)", the theme of the Atlas cave (the first dungeon; a short one).

    python3 tools/bgm/make_atlas_cave_bgm.py            writes src/main/resources/bgm/atlas_cave.nbs
    python3 tools/bgm/make_atlas_cave_bgm.py out.nbs    writes somewhere else

E minor, 4/4, 75 BPM (one tick = a sixteenth; 5 ticks a second, so a song tick is exactly 4 server ticks), 48 bars (2:34). A cave: slow, a little mysterious, not frightening
(it is the first dungeon). Low flute melody, a harp that arpeggiates slowly, a bass that is struck again every half bar, water drops on the chime, an echo (the melody again
0.8 s later, quiet, on the right), and a soft heartbeat from the middle on. Form: intro, A, B, quiet interlude, A', C (the swell), coda; one piece from start to end (no phases).
It loops from bar 5, and the coda stops on the dominant (B7) so that the jump back to A (E minor) is smooth.

The motif of Atlas (the place): E5 B4 G4 E4 - a falling E minor triad, the plains theme turned upside down (the plains theme of "Under an Endless Sky" climbs the D major triad
into the sky; Atlas goes down into the earth). Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 5.0           # ticks a second (a divisor of 20: a song tick is 4 server ticks); a tick is a sixteenth note, a quarter is 4 ticks: 75 BPM
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "Where the Titan Sleeps (Field002)"
E_MINOR = scale("E3 F#3 G3 A3 B3 C4 D4 E4 F#4 G4 A4 B4 C5 D5 E5 F#5")

CHORDS = {  # the notes of the chord for the harp, low to high; the bass root
    "Em": ([59, 64, 67, 71, 76], 64),
    "C": ([60, 64, 67, 72, 76], 60),
    "G": ([55, 59, 62, 67, 71], 55),
    "D": ([57, 62, 66, 69, 74], 62),
    "Am": ([57, 60, 64, 69, 72], 57),
    "B7": ([59, 63, 66, 69, 71], 59),
}

INTRO_CHORDS = ["Em", "Em", "C", "B7"]
INTRO_HARP = ["r:16", "r:16", "E5:8 B4:8", "G4:8 F#4:8"]
A_CHORDS = ["Em", "C", "G", "D", "Em", "C", "Am", "B7"]
A_MELODY = [
    "E5:4 B4:4 G4:4 E4:4",     # the motif of Atlas
    "E5:4 C5:4 G4:8",
    "D5:4 B4:4 G4:4 B4:4",
    "A4:6 F#4:2 D4:8",
    "E5:4 B4:4 G4:4 B4:4",     # the motif again, the end turned up
    "C5:4 E5:4 G4:8",
    "A4:4 C5:4 E5:4 C5:4",
    "B4:4 A4:4 F#4:8",
]
B_CHORDS = ["Em", "G", "Am", "Em", "C", "G", "D", "B7"]
B_MELODY = [                  # the motif's shape on a rising line, in eighths
    "E4:2 G4:2 B4:4 E5:4 B4:4",
    "D5:2 B4:2 G4:4 D5:4 B4:4",
    "C5:2 E5:2 A4:4 C5:4 E5:4",
    "B4:2 G4:2 E4:4 G4:4 B4:4",
    "C5:4 G4:4 E5:4 G4:4",
    "B4:4 D5:4 G4:8",
    "F#4:4 A4:4 D5:4 A4:4",
    "B4:2 A4:2 F#4:4 D#4:4 F#4:4",
]
QUIET_CHORDS = ["Em", "Em", "C", "C", "Am", "Am", "B7", "B7"]
C_CHORDS = ["Am", "B7", "Em", "C", "Am", "D", "B7", "B7"]
C_MELODY = [                  # the swell: high, the motif at the top
    "A4:4 C5:4 E5:8",
    "D#5:4 F#5:4 D#5:4 B4:4",
    "E5:4 B4:4 G4:4 B4:4",
    "C5:4 E5:4 G4:4 C5:4",
    "E5:4 C5:4 A4:8",
    "F#5:4 D5:4 A4:8",
    "D#5:6 F#5:2 B4:8",
    "D#5:4 B4:4 F#4:8",
]
CODA_CHORDS = ["Em", "C", "Am", "B7"]
CODA_HARP = ["E5:8 B4:8", "G4:8 E4:8", "C5:8 A4:8", "F#4:16"]

# (name, chords, melody, who plays the melody, the voices, the loudness of the accompaniment, of the melody)
SECTIONS = [
    ("intro", INTRO_CHORDS, None, None, {"harp", "bass", "drops", "harp-intro"}, 1.0, 1.0),
    ("A", A_CHORDS, A_MELODY, "flute", {"harp", "bass", "drops", "echo"}, 1.0, 1.0),
    ("B", B_CHORDS, B_MELODY, "flute", {"harp", "bass", "drops", "echo", "heart"}, 1.0, 1.0),
    ("quiet", QUIET_CHORDS, None, None, {"harp-soft", "bass", "drops-more"}, 0.7, 1.0),
    ("A2", A_CHORDS, A_MELODY, "flute", {"harp", "bass", "drops", "echo", "harmony", "heart"}, 1.0, 1.0),
    ("C", C_CHORDS, C_MELODY, "flute", {"harp", "bass", "drops", "echo", "harmony", "heart", "didgeridoo"}, 1.0, 1.15),
    ("coda", CODA_CHORDS, CODA_HARP, "harp", {"harp-soft", "bass", "drops"}, 0.6, 0.8),
]

ARPEGGIO = [0, 2, 4, 2, 1, 3, 4, 3]   # eight eighths a bar through the five notes of the chord, low to high
ECHO_DELAY = 4                         # ticks (0.8 s)


def drop_ticks(bar_number, count):
    """Where the water drops fall in a bar: the same every time (a small fixed generator)."""
    state = (bar_number * 2654435761 + 12345) & 0xFFFFFFFF
    ticks = []
    for _ in range(count):
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        ticks.append(3 + (state >> 8) % 12)
    return sorted(set(ticks))


def build():
    melody = Layer("Flute (Atlas motif)", 100, 100)
    harmony = Layer("Flute (a third below)", 55, 80)
    echo = Layer("Flute (echo of the cave)", 45, 150)
    harp = Layer("Harp (arpeggio)", 70, 90)
    harp_melody = Layer("Harp (melody, intro, coda)", 80, 110)
    bass = Layer("Bass", 80, 100)
    drops = Layer("Chime (water drops)", 50, 125)
    heart = Layer("Heartbeat (soft drum)", 60, 100)
    drone = Layer("Didgeridoo (the earth)", 40, 100)
    layers = [melody, harmony, echo, harp, harp_melody, bass, drops, heart, drone]

    total = sum(len(section[1]) for section in SECTIONS) * BAR
    bar_number = 0
    for name, chords, tune, tune_instrument, voices, accompaniment, lead in SECTIONS:
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            tones, root = CHORDS[chord]
            soft = "harp-soft" in voices
            if "harp" in voices or soft:
                for step, tone_index in enumerate(ARPEGGIO):
                    base = 44 if step == 0 else 36
                    harp.put(start + step * 2, HARP, tones[tone_index], velocity(base * (0.7 if soft else 1.0), accompaniment))
            if "bass" in voices:
                bass.put(start, BASS, root, velocity(70, accompaniment))
                bass.put(start + 8, BASS, root, velocity(48, accompaniment))
            if "heart" in voices:
                for tick, vel in ((0, 46), (2, 30), (8, 38), (10, 24)):
                    heart.put(start + tick, BASEDRUM, 62, vel)
            if "didgeridoo" in voices and index % 2 == 0:
                drone.put(start, DIDGERIDOO, root if root >= 54 else root + 12, 36)
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
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/atlas_cave.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE, "Theme of the Atlas cave, the first dungeon. Slow and a little mysterious, 48 bars, loops from bar 5 (the intro is played once).",
              LOOP_START_BAR)
    report(target, layers, length, TPS)


if __name__ == "__main__":
    main()
