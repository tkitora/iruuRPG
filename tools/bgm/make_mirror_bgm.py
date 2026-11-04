#!/usr/bin/env python3
"""Makes mirror_phase1.nbs and mirror_phase2.nbs: "The Cracked Mirror Laughs (Boss007)" and "..., Shattered (Boss007)", a boss of madness and mockery, written again by the BGM制作ガイド.

    python3 tools/bgm/make_mirror_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_mirror_bgm.py outdir     writes them somewhere else

F-sharp Phrygian dominant (F# G A# B C# D E: the guide's "異国・妖しさ", with the flat second), 4/4, 150 BPM, 80 bars (2:08); intro 4 / A 16 / A' 16 / B 16 / A'' 16 (the peak, bar 52 = 65 %) / calm 12, loop from bar 5.
The theme is the world theme CRACKED: the same first note and the same rise (F#4, then the third raised to A#, D5 E5 F#5) so that it is almost the song of the plains - almost. The device is the MIRROR:
a second voice plays the same tune upside down about F#4 (the guitar, the inversion of the melody), so the tune meets itself; the bells trill a half step (G over F#) and the flute laughs in short notes.
Phase 1 (natural): harp, flute, guitar mirror, bell, bass, snare march. Phase 2 (a half step up, G Phrygian dominant): the same bars and chords with the electric piano and 8-bit, the sixteenth bass, the whole kit, the
unison tune and the mirror on the 8-bit. Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossframe import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TONIC, SCALE = 6, [0, 1, 4, 5, 7, 8, 10]
K = key_pcs(TONIC, SCALE)
AXIS = 66

T = "F#4:2 A#4:2 D5:4 D5:2 E5:2 F#5:4"            # the world theme, cracked
U = "E5:4 D5:4 C#5:4 A#4:4"
R = "r:16"
a1 = seq_block(T, U, K, [0, ("u", 0), -1, ("u", -1), "G4:2 B4:2 D5:4 D5:2 E5:2 G5:4".replace("G5", "G4"), "F#5:4 E5:4 C#5:4 A#4:4", 0, "C#5:8 F#4:8"])
a2 = seq_block(T, U, K, [0, "E5:2 D5:2 C#5:4 A#4:8", -1, "D5:4 C#5:4 A#4:8", "G4:2 B4:2 D5:4 D5:2 E5:2 G4:4", "B4:4 C#5:4 A#4:8", 0, "F#4:8 r:8"])
a3 = seq_block(T, U, K, [0, "E5:4 F#5:4 D5:8", -1, "F#5:4 E5:4 D5:8", "G4:2 B4:2 D5:4 B4:2 D5:2 E5:4", "C#5:4 D5:4 E5:8", 0, "C#5:8 A#4:8"])
b1 = seq_block(T, U, K, [-1, R, 0, R, -3, R, 0, R])
b2 = seq_block(T, U, K, [-1, ("u", -1), 0, ("u", 0), "B4:6 D5:2 F#5:4 B4:4", "D5:4 F#5:4 B4:8", "C#5:6 E5:2 G4:4 C#5:4", "C#5:8 r:8"])
p1 = seq_block(T, U, K, [0, "A#4:2 D5:2 F#5:4 D5:8", 0, "A#4:2 D5:2 F#5:4 A#4:8", -1, "B4:2 D5:2 F#5:4 D5:8", -3, "C#5:2 E5:2 G4:4 C#5:8"])
p2 = seq_block(T, U, K, [0, "A#4:2 D5:2 F#5:4 D5:8", -1, "B4:2 D5:2 F#5:4 D5:8", -3, "C#5:2 E5:2 G4:4 C#5:8", "G4:2 B4:2 D5:4 D5:2 E5:2 G4:4", "F#5:16"])
c1 = ["F#5:4 A#4:4 F#4:8", R, "E5:4 G4:4 E4:8", R, "D5:4 B4:4 G4:8", R, "C#5:4 G4:4 E4:8", R, "A#4:4 F#4:4 C#4:8", R, "G4:2 B4:2 D5:4 D5:2 E5:2 G4:4", "C#4:4 E4:4 G4:4 B4:4"]

SPEC = dict(
    bar=16, tps=10.0, tonic=TONIC, scale=SCALE, shift=[0, 1], axis=AXIS,
    sections=[("A", 16), ("A2", 16), ("B", 16), ("PEAK", 16), ("CALM", 12)],
    chords={
        "intro": ["F#", "F#", "G", "C#"],
        "A": "F# F# G G Bm Bm C# C# F# F# G G Bm C# F# F#".split(),
        "A2": "F# F# G G Bm Bm C# C# F# F# G G Bm Bm C# C#".split(),
        "B": "G G F# F# Edim Edim C# C# G G F# F# Bm Bm C# C#".split(),
        "PEAK": ["F#|G", "F#|G", "Bm|C#", "Bm|C#", "F#|G", "F#|G", "Bm|C#", "Bm|C#", "F#|G", "F#|G", "Bm|C#", "Bm|C#", "G|G", "G|G", "C#|C#", "C#"],
        "CALM": "G G Bm Bm C# C# G G Bm Bm C# C#".split(),
    },
    melody={"intro": None, "A": a1 + a2, "A2": a1 + a3, "B": b1 + b2, "PEAK": p1 + p2, "CALM": c1},
    pedal={"PEAK": "F#"},
    level={"intro": 0.8, "A": 0.9, "A2": 1.0, "B": 0.85, "PEAK": 1.1, "CALM": 0.75},
    arp_low=54, arp_high=72, stab_low=54, stab_high=68,
    run=[56, 58, 60, 62],
    voices=[
        {
            "intro": ["bass:half@2", "arp:HARP:4"],
            "A": ["lead:HARP", "mir:GUITAR@8", "bass:half", "drums:heart@8", "bell:head"],
            "A2": ["lead:HARP", "mir:GUITAR", "dbl:FLUTE:12:0.5@8", "stab:GUITAR:0,8", "bass:walk", "drums:march", "bell:head"],
            "B": ["lead:HARP", "bass:half", "drone", "arp:HARP:4@8", "bell:head@8"],
            "PEAK": ["lead:HARP", "mir:GUITAR", "dbl:FLUTE:12:0.6", "stab:GUITAR:0,8", "bass:pedal", "drone", "drums:march", "fill", "bell:hit", "arp:HARP:2@8"],
            "CALM": ["lead:HARP", "bass:half", "echo:3:CHIME@4", "bell:head"],
        },
        {
            "intro": ["bass:half@2", "arp:PLING:4"],
            "A": ["lead:PLING", "dbl:BIT:0:0.45", "mir:BIT@8", "bass:eighth", "drums:march@8", "bell:head"],
            "A2": ["lead:PLING", "dbl:BIT:0:0.5", "mir:BIT", "dbl:FLUTE:12:0.5@8", "stab:BIT:0,6,10", "bass:sixteenth", "drums:drive", "bell:head"],
            "B": ["lead:BIT", "bass:eighth", "drone", "arp:PLING:2@8", "bell:head@8"],
            "PEAK": ["lead:PLING", "dbl:BIT:0:0.6", "mir:BIT", "dbl:FLUTE:12:0.6", "stab:PLING:0,6,8,14", "bass:sixteenth", "drone", "drums:double", "fill", "bell:hit", "arp:BIT:4"],
            "CALM": ["lead:PLING", "bass:half", "echo:3:CHIME@4", "bell:head", "drums:heart@8"],
        },
    ],
)

TITLES = ("The Cracked Mirror Laughs (Boss007)", "The Cracked Mirror Laughs, Shattered (Boss007)")
FILES = ("mirror_phase1.nbs", "mirror_phase2.nbs")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [out / f for f in FILES],
                 ["Mirror boss, phase 1 (natural sounds): the cracked world theme and its own reflection (the same tune upside down), F# Phrygian dominant. 80 bars, loops from bar 5. Same bars as phase 2.",
                  "Mirror boss, phase 2 (electronic, a half step up): the same bars and chords, the mirror on the 8-bit, sixteenth bass, the whole kit."],
                 TITLES)


if __name__ == "__main__":
    main()
