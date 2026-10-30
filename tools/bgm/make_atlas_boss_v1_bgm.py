#!/usr/bin/env python3
"""[The first version, kept because Dungeon002/Boss002 are written from its tune; the song of the game is now made by the new script.] Makes atlas_vindicator.nbs: "The Axe at the Gate (Boss001)", the boss theme of the Atlas cave (the vindicator, the master of the first dungeon).

    python3 tools/bgm/make_atlas_boss_bgm.py            writes src/main/resources/bgm/atlas_vindicator.nbs
    python3 tools/bgm/make_atlas_boss_bgm.py out.nbs    writes somewhere else

E minor, 4/4, 150 BPM (one tick = a sixteenth; 10 ticks a second, so a song tick is exactly 2 server ticks), 80 bars (2:08). One piece, no phases (the dungeon is short):
intro, A, B (the chorus), a bridge in half time, A, B with everything, and a four-bar run-up (a snare roll) that jumps back to A. Drums are thick: a kick that follows the
bass, a snare on 2 and 4, a hat; the bass and the guitar play the 3-3-2 pattern (ticks 0, 3, 6, 8, 11, 14). The melody is the motif of Atlas, played fast.

The motif of Atlas (the place): E5 B4 G4 E4 - a falling E minor triad, the plains theme turned upside down. It is the same as in the cave theme ("Where the Titan Sleeps"), here
in eighths and driving. Nothing here is random: the same file comes out every time. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from nbslib import *  # noqa: F401,F403 - the shared helpers

TPS = 10.0          # ticks a second (a divisor of 20: a song tick is 2 server ticks); a tick is a sixteenth note, a quarter is 4 ticks: 150 BPM
LOOP_START_BAR = 4  # the intro (bars 0-3) is played once
TITLE = "The Axe at the Gate (Boss001)"
E_MINOR = scale("E3 F#3 G3 A3 B3 C4 D4 E4 F#4 G4 A4 B4 C5 D5 E5 F#5")

CHORDS = {  # the notes for the harp, low to high; the root and the fifth for the bass and the guitar
    "Em": ([59, 64, 67, 71, 76], 64, 71),
    "C": ([60, 64, 67, 72, 76], 60, 67),
    "G": ([55, 59, 62, 67, 71], 55, 62),
    "D": ([57, 62, 66, 69, 74], 62, 69),
    "Am": ([57, 60, 64, 69, 72], 57, 64),
    "B7": ([59, 63, 66, 69, 71], 59, 66),
}
TRESILLO = [0, 3, 6, 8, 11, 14]   # 3 + 3 + 2, twice

INTRO_CHORDS = ["Em", "Em", "Em", "B7"]
INTRO_MELODY = ["r:16", "r:16", "E5:4 B4:4 G4:4 E4:4", "B4:4 D#5:4 F#5:8"]
A_CHORDS = ["Em", "Em", "C", "D", "Em", "Em", "C", "B7", "Em", "Em", "C", "D", "Am", "C", "D", "B7"]
A_MELODY = [
    "E5:2 B4:2 G4:2 E4:2 E5:2 B4:2 G4:4",     # the motif, twice
    "B4:4 G4:2 A4:2 B4:4 E5:4",
    "C5:2 G4:2 E4:2 G4:2 C5:4 E5:4",
    "D5:2 A4:2 F#4:2 A4:2 D5:4 F#5:4",
    "E5:2 B4:2 G4:2 E4:2 E5:2 B4:2 G4:4",
    "B4:4 E5:4 G4:4 B4:4",
    "C5:2 E5:2 G4:2 C5:2 E5:8",
    "D#5:2 F#5:2 B4:2 D#5:2 F#5:4 B4:4",
    "E5:4 B4:4 E5:4 G4:4",
    "G4:2 B4:2 E5:4 D5:4 B4:4",
    "C5:4 G4:4 E4:2 G4:2 C5:4",
    "D5:4 F#5:4 A4:4 D5:4",
    "A4:2 C5:2 E5:4 C5:4 A4:4",
    "G4:2 C5:2 E5:4 G4:4 C5:4",
    "A4:2 D5:2 F#5:4 D5:4 A4:4",
    "B4:2 D#5:2 F#5:4 D#5:4 B4:4",
]
B_CHORDS = ["C", "D", "Em", "Em", "C", "D", "B7", "B7", "Am", "G", "D", "Em", "Am", "B7", "Em", "Em"]
B_MELODY = [
    "E5:8 G4:4 C5:4",
    "F#5:8 D5:4 A4:4",
    "E5:4 G4:4 B4:4 E5:4",
    "B4:6 G4:2 E4:8",
    "E5:8 C5:4 G4:4",
    "F#5:8 A4:4 D5:4",
    "D#5:4 F#5:4 B4:4 D#5:4",
    "F#5:6 D#5:2 B4:8",
    "A4:4 C5:4 E5:8",
    "D5:4 B4:4 G4:8",
    "A4:4 D5:4 F#5:8",
    "E5:8 B4:8",
    "C5:2 E5:2 A4:4 C5:2 E5:2 A4:4",
    "D#5:2 F#5:2 B4:4 D#5:2 F#5:2 B4:4",
    "E5:2 B4:2 G4:2 E4:2 E5:2 B4:2 G4:4",
    "E4:12 r:4",
]
BRIDGE_CHORDS = ["Em", "Em", "Am", "Am", "C", "D", "B7", "B7"]
BRIDGE_MELODY = [
    "B4:8 E5:8",
    "G4:8 B4:8",
    "A4:8 C5:8",
    "E5:8 C5:8",
    "G4:8 C5:8",
    "A4:8 D5:8",
    "F#4:4 B4:4 D#5:4 F#5:4",
    "F#5:8 r:8",
]
OUTRO_CHORDS = ["Em", "C", "D", "B7"]
OUTRO_MELODY = ["E5:4 B4:4 G4:4 E4:4", "C5:4 G4:4 E4:4 G4:4", "D5:4 A4:4 F#4:4 A4:4", "D#5:2 F#5:2 B4:4 r:8"]

# (name, chords, melody, drums, guitar, the extras)
#   drums:  "kick" kick only | "full" | "half" (half time) | "climax" (the kick follows the bass, louder hat) | "roll" (a snare roll in the last bar)
#   guitar: None | "muted" (ticks 0 and 8) | "tresillo"
#   extras: "double" (bell on the melody) | "harmony" (flute a third below) | "chime" (an accent on every bar's first tick) | "harp-tension" (the last two bars rise in sixteenths)
#           | "harp-arp" (eighths) | "bass-off" | "soft-lead" / "soft-bass" (quieter in the bridge) | "roll-intro" (snare in the last two bars)
SECTIONS = [
    ("intro", INTRO_CHORDS, INTRO_MELODY, "kick", None, {"roll-intro"}),
    ("A", A_CHORDS, A_MELODY, "full", "muted", set()),
    ("B", B_CHORDS, B_MELODY, "full", "tresillo", {"double", "chime"}),
    ("bridge", BRIDGE_CHORDS, BRIDGE_MELODY, "half", None, {"harp-tension", "soft-bass", "soft-lead"}),
    ("A2", A_CHORDS, A_MELODY, "full", "tresillo", {"harmony"}),
    ("B2", B_CHORDS, B_MELODY, "climax", "tresillo", {"double", "chime", "harp-arp"}),
    ("outro", OUTRO_CHORDS, OUTRO_MELODY, "roll", "muted", set()),
]


def build():
    lead = Layer("Flute (melody)", 100, 100)
    double = Layer("Bell (doubles the melody)", 45, 120)
    harmony = Layer("Flute (a third below)", 55, 80)
    harp = Layer("Harp (arpeggio)", 65, 90)
    guitar_root = Layer("Guitar (root)", 75, 60)
    guitar_fifth = Layer("Guitar (fifth)", 70, 70)
    bass = Layer("Bass", 90, 100)
    kick = Layer("Kick", 90, 100)
    snare = Layer("Snare", 75, 105)
    hat = Layer("Hat", 55, 115)
    chime = Layer("Chime (accent)", 50, 125)
    layers = [lead, double, harmony, harp, guitar_root, guitar_fifth, bass, kick, snare, hat, chime]

    bar_number = 0
    for name, chords, tune, drums, guitar, extras in SECTIONS:
        last = len(chords) - 1
        for index, chord in enumerate(chords):
            start = bar_number * BAR
            tones, root, fifth = CHORDS[chord]

            # bass: the 3-3-2 pattern on the root, the fifth on the third hit of each half
            if name != "intro" or index >= 2:
                hits = TRESILLO if "soft-bass" not in extras else [0, 8]
                for number, tick in enumerate(hits):
                    note = fifth if tick in (6, 14) else root
                    bass.put(start + tick, BASS, note, (78 if tick in (0, 8) else 64) if "soft-bass" not in extras else 56)

            # guitar: power chord stabs
            if guitar is not None:
                stabs = [0, 8] if guitar == "muted" else TRESILLO
                for tick in stabs:
                    guitar_root.put(start + tick, GUITAR, root, 56 if tick in (0, 8) else 48)
                    if tick in (0, 8):
                        guitar_fifth.put(start + tick, GUITAR, fifth, 44)

            # drums
            if drums == "kick":
                kick.put(start, BASEDRUM, 62, 80)
                kick.put(start + 8, BASEDRUM, 62, 70)
            elif drums in ("full", "roll"):
                for tick in (0, 6, 8, 14):
                    kick.put(start + tick, BASEDRUM, 62, 84 if tick == 0 else 70)
                for tick in (4, 12):
                    snare.put(start + tick, SNARE, 66, 70)
                for tick in range(0, 16, 2):
                    hat.put(start + tick, HAT, 66, 34 if tick in (4, 12) else 24)
            elif drums == "half":
                kick.put(start, BASEDRUM, 62, 80)
                snare.put(start + 8, SNARE, 66, 66)
                for tick in range(0, 16, 4):
                    hat.put(start + tick, HAT, 66, 22)
            elif drums == "climax":
                for tick in TRESILLO:
                    kick.put(start + tick, BASEDRUM, 62, 86 if tick == 0 else 70)
                for tick in (4, 12):
                    snare.put(start + tick, SNARE, 66, 74)
                for tick in range(0, 16, 2):
                    hat.put(start + tick, HAT, 66, 38 if tick % 4 == 0 else 22)
            # snare rolls that run up to the next section: the last bar of the outro, the last two of the intro
            roll_bar = (drums == "roll" and index == last) or ("roll-intro" in extras and index >= last - 1)
            if roll_bar:
                for tick in list(snare.notes):
                    if start <= tick < start + BAR:
                        del snare.notes[tick]
                for tick in range(16):
                    if "roll-intro" in extras and index == last - 1 and tick % 2:
                        continue
                    progress = (tick + (16 if index == last else 0)) / 32.0
                    snare.put(start + tick, SNARE, 66, 30 + int(48 * progress))
                if drums == "roll":
                    for tick in list(hat.notes):
                        if start <= tick < start + BAR:
                            del hat.notes[tick]

            if "chime" in extras and index % 4 == 0:
                chime.put(start + 2, CHIME, 78 if index % 8 == 0 else 74, 38)
            if "harp-tension" in extras and index >= last - 1:
                for tick in range(16):
                    harp.put(start + tick, HARP, tones[tick % 4 + (1 if index == last else 0)], 30 + (16 if index == last else 0) + tick)
            if "harp-arp" in extras:
                for step, tone_index in enumerate([0, 1, 2, 3, 4, 3, 2, 1]):
                    harp.put(start + step * 2 + 1, HARP, tones[tone_index], 34)

            # the melody
            tick = 0
            for note, length in parse_bar(tune[index]):
                if note is not None:
                    lead.put(start + tick, FLUTE, note, velocity(90 if tick == 0 else 84, 0.65 if "soft-lead" in extras else 1.0))
                    if "double" in extras:
                        double.put(start + tick, BELL, note, 40)
                    if "harmony" in extras or ("double" in extras and drums == "climax"):
                        harmony.put(start + tick, FLUTE, third_below(note, E_MINOR), 44)
                tick += length
            bar_number += 1
    return layers, bar_number * BAR


def main():
    layers, length = build()
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("atlas_vindicator_v1.nbs")
    target.parent.mkdir(parents=True, exist_ok=True)
    write_nbs(target, layers, length, TPS, TITLE, "Boss theme of the Atlas cave (the vindicator). Fast, heavy drums, 80 bars, loops from bar 5 (the intro is played once).", LOOP_START_BAR)
    report(target, layers, length, TPS)


if __name__ == "__main__":
    main()
