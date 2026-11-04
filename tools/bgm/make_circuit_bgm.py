#!/usr/bin/env python3
"""Makes circuit_phase1.nbs and circuit_phase2.nbs: "Holy Circuit (Boss010)" and "Holy Circuit, Overclocked (Boss010)", a hymn that runs too fast, written again by the BGM制作ガイド.

    python3 tools/bgm/make_circuit_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_circuit_bgm.py outdir     writes them somewhere else

D Dorian (the guide's "哀しいけれど前向き"), 4/4, 200 BPM (20 ticks a second, a beat is 6 ticks, a bar 24: the eighth is 3 ticks and the triplet 2; the intro is 16 ticks of silence and 2 bars so that the loop point is tick 64),
102 bars (2:03): intro 2 / A 24 / A' 24 / B 24 (with eight bars of nothing but the tune and the guitar) / A'' 20 (the peak, from bar 74 of 104 = 71 %) / calm 8. The guide's "引き算" taken far: ONLY FIVE SOUNDS (the electric
piano for the tune, the guitar for the running pizzicato, the bass, the bass drum and the snare), at most five notes together, and a drone-free, bell-free, echo-free sound. The theme is the world theme as a hymn
(D4 F4 A4 | A4 B4 C5: the rising minor triad, then the Dorian sixth). Chords i - IV - i - bVII, one a bar. The device: the guitar never stops running (eighths, then triplets), the tune stays serene.
Phase 2 (a half step up, E-flat Dorian): the same bars and chords, the bass drum on every beat, the bass in eighths and the guitar in triplets from the start. Everything is written by what SOUNDS. Nothing is random.
The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossframe import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TONIC, SCALE = 2, [0, 2, 3, 5, 7, 9, 10]
K = key_pcs(TONIC, SCALE)
R = "r:24"

T = "D4:3 F4:3 A4:6 A4:3 B4:3 C5:6"               # the world theme as a hymn
U = "C5:6 B4:6 A4:6 F4:6"
a1 = seq_block(T, U, K, [0, ("u", 0), 3, ("u", 3), 0, ("u", 0), -1, "A4:6 G4:6 F4:12"])
a2 = seq_block(T, U, K, [0, "F5:6 E5:6 D5:12", 3, "G5:6 F5:6 E5:12".replace("G5", "G4").replace("F5", "F4").replace("E5", "E4"), 0, ("u", 0), -1, "A4:12 r:12"])
a3 = seq_block(T, U, K, [0, "A4:6 C5:6 D5:12", 3, "F4:6 A4:6 C5:12", 0, ("u", 0), -1, "A4:12 D5:12"])
b1 = seq_block(T, U, K, [0, R, 3, R, -2, R, -1, R])
b2 = seq_block(T, U, K, [0, ("u", 0), 3, ("u", 3), -2, ("u", -2), -1, "A4:12 r:12"])
b3 = seq_block(T, U, K, [0, ("u", 0), 3, ("u", 3), 0, ("u", 0), -1, "A4:12 D5:12"])
p1 = seq_block(T, U, K, [0, "A4:6 D5:6 F5:12", 3, "C5:6 F5:6 A4:12", 0, "A4:6 D5:6 F5:12", -1, "E5:6 D5:6 A4:12"])
p2 = seq_block(T, U, K, [0, "A4:6 D5:6 F5:12", 3, "C5:6 F5:6 A4:12", 0, ("u", 0), -1, "A4:12 D5:12", 0, ("u", 0), -1, "A4:12 r:12"])
c1 = ["D5:6 A4:6 F4:12", R, "C5:6 G4:6 E4:12", R, "A4:6 F4:6 D4:12", R, "A4:6 C5:6 E5:6 G4:6", "F4:3 A4:3 C5:6 E5:12"]


def cycle(pattern, bars):
    return (pattern * 10)[:bars]


FOUR = "Dm G Dm C".split()

SPEC = dict(
    bar=24, tps=20.0, tonic=TONIC, scale=SCALE, shift=[0, 1],
    sections=[("A", 24), ("A2", 24), ("B", 24), ("PEAK", 20), ("CALM", 8)],
    chords={
        "intro": ["Dm", "G"],
        "A": cycle(FOUR, 24),
        "A2": cycle(FOUR, 24),
        "B": cycle("Dm G Dm C Am G Dm C".split(), 24),
        "PEAK": cycle("Dm G Dm C".split(), 20),
        "CALM": "G G C C A A C C".split(),
    },
    melody={"intro": None, "A": a1 + a2 + a1, "A2": a1 + a3 + a2, "B": b1 + b2 + b3, "PEAK": p1 + p2, "CALM": c1},
    breaks={"B": tuple(range(0, 8))},
    break_keep=("lead", "arp"),
    level={"intro": 0.8, "A": 0.9, "A2": 1.0, "B": 0.9, "PEAK": 1.1, "CALM": 0.75},
    arp_low=54, arp_high=66, stab_low=54, stab_high=68,
    run=[57, 59, 60, 62],
    voices=[
        {   # phase 1: five sounds only
            "intro": ["bass:half", "arp:GUITAR:3@1"],
            "A": ["lead:PLING", "arp:GUITAR:3", "bass:half", "drums:kicks@8"],
            "A2": ["lead:PLING", "arp:GUITAR:3", "bass:eighth", "drums:kicks"],
            "B": ["lead:PLING", "arp:GUITAR:3", "bass:half@8", "drums:kicks@8"],
            "PEAK": ["lead:PLING", "arp:GUITAR:2", "bass:eighth", "drums:kicks"],
            "CALM": ["lead:PLING", "arp:GUITAR:6", "bass:half", "drums:kicks@4"],
        },
        {   # phase 2: overclocked
            "intro": ["bass:half", "arp:GUITAR:2@1", "drums:kicks"],
            "A": ["lead:PLING", "arp:GUITAR:2", "bass:eighth", "drums:kicks4"],
            "A2": ["lead:PLING", "arp:GUITAR:2", "bass:sixteenth", "drums:kicks4"],
            "B": ["lead:PLING", "arp:GUITAR:2", "bass:eighth@8", "drums:kicks4@8"],
            "PEAK": ["lead:PLING", "arp:GUITAR:2", "bass:sixteenth", "drums:kicks4", "fill"],
            "CALM": ["lead:PLING", "arp:GUITAR:6", "bass:half", "drums:kicks"],
        },
    ],
)

TITLES = ("Holy Circuit (Boss010)", "Holy Circuit, Overclocked (Boss010)")
FILES = ("circuit_phase1.nbs", "circuit_phase2.nbs")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [out / f for f in FILES],
                 ["Boss, phase 1: five sounds only (electric piano, guitar, bass, bass drum, snare), D Dorian, 200 BPM, a hymn that runs too fast, eight bars of tune and guitar alone. 2464 ticks, loops from tick 64. Same bars as phase 2.",
                  "Boss, phase 2 (overclocked, a half step up): the same bars and chords, the bass drum on every beat, sixteenth bass, the guitar in triplets."],
                 TITLES)


if __name__ == "__main__":
    main()
