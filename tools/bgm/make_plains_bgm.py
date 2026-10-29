#!/usr/bin/env python3
"""Makes plains_adventure.nbs: the field theme "Under an Endless Sky" for the great plain outside the first town (Note Block Studio v5 file).

    python3 tools/bgm/make_plains_bgm.py            writes src/main/resources/bgm/plains_adventure.nbs
    python3 tools/bgm/make_plains_bgm.py out.nbs    writes somewhere else

The song: D major, 4/4, 100 BPM (one tick = a sixteenth; 6.67 ticks a second = 20 / 3, so a song tick is exactly 3 server ticks), 64 bars (2:34), in the style of an
Irish / Celtic field tune: a flute melody, a fingerpicked guitar, a soft bass, a harp that sparkles on the off beats, a second flute a third below (standing in for a
fiddle - Minecraft has no violin) and a quiet bodhran-like drum. Form: intro, A, A', quiet interlude (harp), B, B', A, quiet reprise (flute), coda; it loops from bar 5
(tick 64), and the end is quiet and on the dominant chord, so the jump back to A is smooth. Every note is between F#3 and F#5 (the range of a note block).
Nothing here is random: the same file comes out every time. The design record (motifs, log) is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 20.0 / 3.0    # ticks a second (a divisor of 20: a song tick is 3 server ticks); a tick is a sixteenth note, a quarter is 4 ticks: 100 BPM
BAR = 16            # ticks in a bar
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "Under an Endless Sky (Field001)"

D_MAJOR = scale("D3 E3 F#3 G3 A3 B3 C#4 D4 E4 F#4 G4 A4 B4 C#5 D5 E5 F#5")


# ---- the tune ---------------------------------------------------------------------------------------------------------------------

CHORDS = {  # the notes of the chord, low to high, for the guitar; the bass root and fifth
    "D": ([62, 66, 69, 74], 62, 69),
    "A": ([57, 64, 69, 73], 57, 64),
    "Bm": ([59, 66, 71, 74], 59, 66),
    "G": ([55, 62, 67, 71], 55, 62),
    "Em": ([55, 64, 67, 71], 64, 59),
}

A_CHORDS = ["D", "A", "Bm", "G", "D", "A", "G", "A"]
A_MELODY = [
    "F#4:2 A4:2 D5:4 D5:2 E5:2 F#5:4",
    "E5:6 C#5:2 A4:4 C#5:4",
    "D5:4 B4:2 D5:2 E5:4 D5:4",
    "B4:6 A4:2 G4:4 B4:4",
    "A4:4 D5:2 E5:2 F#5:6 E5:2",
    "E5:4 C#5:2 D5:2 C#5:4 A4:4",
    "B4:2 D5:2 D5:4 B4:4 A4:4",
    "E5:4 C#5:4 E5:8",
]
A2_CHORDS = ["D", "A", "Bm", "G", "D", "Bm", "Em", "A"]
A2_MELODY = A_MELODY[:4] + [
    "F#5:4 E5:4 D5:4 A4:4",
    "D5:4 F#5:2 E5:2 D5:4 B4:4",
    "B4:2 G4:2 B4:4 E5:4 D5:4",
    "E5:4 C#5:4 A4:8",
]
# the quiet interlude: low and slow, on the harp; it opens like the main theme (a rising figure) but a fourth and a third instead of the leaps
Q_CHORDS = ["Bm", "G", "D", "A", "Bm", "G", "A", "A"]
Q_MELODY = [
    "F#4:4 B4:4 D5:8",
    "B4:6 A4:2 G4:8",
    "F#4:4 A4:4 D5:4 A4:4",
    "E5:8 C#5:8",
    "D5:6 B4:2 F#4:8",
    "G4:4 B4:4 D5:4 B4:4",
    "C#5:4 E5:4 A4:8",
    "E4:4 A4:4 C#5:8",
]
B_CHORDS = ["G", "D", "Em", "A", "G", "D", "Em", "A"]
B_MELODY = [
    "B4:8 D5:4 E5:4",
    "F#5:8 E5:4 D5:4",
    "E5:6 D5:2 B4:4 G4:4",
    "C#5:4 E5:4 C#5:8",
    "B4:4 D5:4 E5:4 D5:4",
    "F#5:6 E5:2 D5:4 A4:4",
    "B4:4 E5:4 D5:4 B4:4",
    "C#5:4 D5:2 E5:2 A4:8",
]
# B again, busier (eighth notes), and it ends on the long E over A
B2_MELODY = [
    "B4:4 D5:2 E5:2 D5:4 B4:4",
    "A4:4 D5:2 F#5:2 E5:4 D5:4",
    "G4:4 B4:2 E5:2 D5:4 B4:4",
    "C#5:4 E5:2 C#5:2 A4:8",
    "B4:4 D5:2 E5:2 F#5:4 E5:4",
    "F#5:4 E5:2 D5:2 E5:4 F#5:4",
    "E5:4 D5:2 B4:2 G4:4 B4:4",
    "C#5:4 D5:2 E5:2 E5:8",
]
INTRO_CHORDS = ["D", "A", "Bm", "G"]
INTRO_HARP = ["r:16", "r:16", "B4:8 D5:8", "G4:4 B4:4 D5:8"]
# the coda: sparse, on the harp, ending on the dominant (A) so that the jump back to A (D chord) is smooth
CODA_CHORDS = ["Bm", "G", "A", "A"]
CODA_HARP = ["D5:6 B4:2 F#4:8", "B4:6 A4:2 G4:8", "C#5:6 E5:2 A4:8", "E5:8 r:8"]

# the sections, in order: (name, chords, melody, melody instrument ("flute" / "harp"), the voices that play, loudness of the accompaniment 0-1, melody loudness 0-1)
SECTIONS = [
    ("intro", INTRO_CHORDS, None, None, {"guitar", "bass", "harp-intro", "chime"}, 1.0, 1.0),
    ("A", A_CHORDS, A_MELODY, "flute", {"guitar", "bass", "melody"}, 1.0, 1.0),
    ("A2", A2_CHORDS, A2_MELODY, "flute", {"guitar", "bass", "melody", "harmony-late", "drums"}, 1.0, 1.0),
    ("Q", Q_CHORDS, Q_MELODY, "harp", {"guitar", "bass-soft", "melody", "chime-soft"}, 0.8, 0.75),
    ("B", B_CHORDS, B_MELODY, "flute", {"guitar", "bass", "melody", "harmony", "sparkle", "drums", "chime"}, 1.0, 1.0),
    ("B2", B_CHORDS, B2_MELODY, "flute", {"guitar", "bass", "melody", "harmony", "sparkle", "drums", "chime"}, 1.0, 1.0),
    ("A3", A_CHORDS, A_MELODY, "flute", {"guitar", "bass", "melody", "harmony", "sparkle", "drums"}, 1.0, 1.0),
    ("Q2", Q_CHORDS, Q_MELODY, "flute", {"guitar", "bass-soft", "melody", "sparkle", "chime-soft"}, 0.75, 0.6),
    ("coda", CODA_CHORDS, CODA_HARP, "harp", {"guitar", "melody", "chime-soft"}, 0.65, 0.6),
]

PICK = [0, 2, 3, 2, 1, 2, 3, 2]  # the fingerpicking: root, fifth, octave, fifth, third, fifth, octave, fifth


def build():
    melody = Layer("Flute (melody)", 100, 100)
    harmony = Layer("Flute (fiddle part, a third below)", 60, 80)
    guitar = Layer("Guitar (fingerpicking)", 80, 65)
    bass = Layer("Bass", 80, 100)
    harp = Layer("Harp", 70, 135)
    chime = Layer("Chime", 55, 120)
    kick = Layer("Drum (soft)", 55, 100)
    hat = Layer("Hat (soft)", 45, 110)
    layers = [melody, harmony, guitar, bass, harp, chime, kick, hat]

    bar_number = 0
    for name, chords, tune, tune_instrument, voices, accompaniment, lead in SECTIONS:
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            tones, root, fifth = CHORDS[chord]
            if "guitar" in voices:
                for step, tone_index in enumerate(PICK):
                    base = 70 if step == 0 else 62 if step == 4 else 52
                    guitar.put(start + step * 2, GUITAR, tones[tone_index], velocity(base, accompaniment))
            if "bass" in voices:
                bass.put(start, BASS, root, 80)
                bass.put(start + 8, BASS, fifth, 66)
            if "bass-soft" in voices:
                bass.put(start, BASS, root, 56)
            if "drums" in voices:
                kick.put(start, BASEDRUM, 66, 42)
                kick.put(start + 8, BASEDRUM, 66, 34)
                hat.put(start + 4, HAT, 66, 24)
                hat.put(start + 12, HAT, 66, 24)
            if "sparkle" in voices:
                for tick in (3, 7, 11, 15):  # the harp on the off beats
                    harp.put(start + tick, HARP, tones[3], 30 if tick != 15 else 26)
            if "chime" in voices and name == "intro" and index == 0:
                chime.put(start, CHIME, 74, 45)
            if "chime" in voices and name in ("B", "B2") and index % 2 == 0:
                chime.put(start, CHIME, 78 if index % 4 == 0 else 74, 38)
            if "chime-soft" in voices and index % 4 == 0:
                chime.put(start, CHIME, 78 if name == "coda" else 74, 30)
            if "harp-intro" in voices:
                tick = 0
                for note, length in parse_bar(INTRO_HARP[index]):
                    if note is not None:
                        harp.put(start + tick, HARP, note, 62)
                    tick += length
            if tune is not None:
                tick = 0
                late = "harmony-late" in voices and index >= 4
                for note, length in parse_bar(tune[index]):
                    if note is not None:
                        if tune_instrument == "harp":
                            harp.put(start + tick, HARP, note, velocity(88 if tick == 0 else 80, lead))
                        else:
                            melody.put(start + tick, FLUTE, note, velocity(88 if tick == 0 else 80, lead))
                            if "harmony" in voices or late:
                                harmony.put(start + tick, FLUTE, third_below(note, D_MAJOR), 50)
                    tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "src/main/resources/bgm/plains_adventure.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE, "Field theme of the great plain outside the first town. Irish / Celtic, medium tempo, 64 bars, loops from bar 5 (the intro is played once).",
              LOOP_START_BAR)
    report(target, layers, length, TPS)


if __name__ == "__main__":
    main()
