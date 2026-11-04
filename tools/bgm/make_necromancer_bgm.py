#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Reaper's Arc (Boss004)" and "Reaper's Arc, Sundered (Boss004)", the snow mountain reaper in two phases, written again by the BGM制作ガイド.

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

A minor (with the G# of the harmonic minor), 4/4, 150 BPM, 80 bars (2:08), the form of the guide: intro 4 / A 16 / A' 16 / B 16 / A'' 16 (the peak, bar 52 = 65 %) / calm 12, loop from bar 5.
The theme is the world theme of the plains BACKWARDS (the enemy's theme is made from the world's: here it is read from the end, a retrograde): E5 D5 C5 | C5 G4 E4 - it falls where the plains rise, like a scythe's arc.
The device: every eighth bar everything falls silent but the tune (a full break, the guide's "ghost rest"), the arc of the scythe cutting the music. Phase 1 (60-70 % of the density, natural sounds): guitar tune with the
harp, bass, bass drum and snare in the second half. Phase 2 is the same bars and chords a half step up (B-flat minor) with: the bass in sixteenths, the whole kit and the bell hits added, the tune in unison on the
8-bit, the flute and the electric piano, and an 8-bit solo in the bridge. Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossframe import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TONIC, SCALE = 9, [0, 2, 3, 5, 7, 8, 10]
K = key_pcs(TONIC, SCALE)

T = "E5:2 D5:2 C5:4 C5:2 G4:2 E4:4"               # the world theme read backwards, in A minor
U = "A4:4 C5:4 E5:8"
a1 = seq_block(T, U, K, [0, ("u", 0), -2, ("u", -2), -1, "E5:6 D5:2 B4:4 G#4:4", 0, "E5:4 G#4:4 B4:8"])
a2 = seq_block(T, U, K, [0, "C5:2 D5:2 E5:4 A4:8", -2, "A4:4 G4:4 F4:8", -3, "E5:6 D5:2 B4:4 G#4:4", 0, "A4:8 r:8"])
a3 = seq_block(T, U, K, [0, "E5:4 C5:4 A4:8", -2, "C5:4 A4:4 F4:8", -3, "B4:4 E5:4 G#5:8".replace("G#5", "G#4"), 0, "E5:8 A4:8"])
b1 = seq_block(T, U, K, [-2, ("u", -2), -3, ("u", -3), -1, ("u", -1), 0, "E4:4 G#4:4 B4:8"])
b2 = seq_block(T, U, K, [-2, ("u", -2), -3, ("u", -3), "F5:6 C5:2 A4:4 F4:4", "A4:4 C5:4 F5:8", "E5:6 D5:2 B4:4 G#4:4", "E5:8 r:8"])
p1 = seq_block(T, U, K, [0, "C5:2 E5:2 A4:4 C5:8", 0, "C5:2 E5:2 A4:4 E5:8", -2, "A4:2 C5:2 F4:4 A4:8", -3, "G4:2 B4:2 D5:4 G4:8"])
p2 = seq_block(T, U, K, [0, "C5:2 E5:2 A4:4 C5:8", -2, "A4:2 C5:2 F4:4 A4:8", -3, "G4:2 B4:2 D5:4 G4:8", "E5:6 D5:2 B4:4 G#4:4", "A4:16"])
c1 = ["E5:2 D5:2 C5:12", "r:16", "D5:2 C5:2 B4:12", "r:16", "C5:2 B4:2 A4:12", "r:16", "B4:2 A4:2 G4:12", "r:16", "A4:2 G4:2 F4:12", "r:16", "E5:6 D5:2 B4:4 G#4:4", "G#3:4 B3:4 E4:4 G#4:4"]
BREAKS = {"A": (7, 15), "A2": (7, 15), "PEAK": (7,)}

SPEC = dict(
    bar=16, tps=10.0, tonic=TONIC, scale=SCALE, shift=[0, 1],
    sections=[("A", 16), ("A2", 16), ("B", 16), ("PEAK", 16), ("CALM", 12)],
    chords={
        "intro": ["Am", "Am", "F", "E"],
        "A": "Am Am F F Dm Dm E E Am Am F F Dm E Am Am".split(),
        "A2": "Am Am F F Dm Dm E E Am Am F F Dm Dm E E".split(),
        "B": "F F Dm Dm G G E E F F Dm Dm Bb Bb E E".split(),
        "PEAK": ["Am|F", "Am|F", "Dm|E", "Dm|E", "Am|F", "Am|F", "Dm|E", "Dm|E", "Am|F", "Am|F", "Dm|E", "Dm|E", "F|G", "F|G", "E|E", "E"],
        "CALM": "Dm Dm F F G G Dm Dm Bb Bb E E".split(),
    },
    melody={"intro": None, "A": a1 + a2, "A2": a1 + a3, "B": b1 + b2, "PEAK": p1 + p2, "CALM": c1},
    pedal={"PEAK": "Am"}, breaks=BREAKS, break_keep=("lead", "echo"),
    level={"intro": 0.8, "A": 0.9, "A2": 1.0, "B": 0.85, "PEAK": 1.1, "CALM": 0.75},
    arp_low=54, arp_high=72, stab_low=54, stab_high=68,
    run=[53, 55, 57, 59],
    voices=[
        {   # phase 1: natural
            "intro": ["bass:half@2", "arp:HARP:4", "bell:head@0-1"],
            "A": ["lead:GUITAR", "dbl:HARP:0:0.7", "bass:half", "arp:HARP:4@8", "drums:heart@8"],
            "A2": ["lead:GUITAR", "dbl:HARP:0:0.7", "dbl:FLUTE:12:0.5@8", "stab:GUITAR:0,8", "bass:walk", "drums:march", "bell:head"],
            "B": ["lead:HARP", "bass:half", "drone", "bell:head@8", "arp:HARP:4@8"],
            "PEAK": ["lead:GUITAR", "dbl:HARP:0:0.7", "dbl:FLUTE:12:0.6", "cnt:HARP", "stab:GUITAR:0,8", "bass:pedal", "drums:march", "fill", "bell:hit", "arp:HARP:2@8"],
            "CALM": ["lead:HARP", "bass:half", "echo:3:CHIME@4", "bell:head"],
        },
        {   # phase 2: electronic, sundered
            "intro": ["bass:half@2", "arp:PLING:4", "bell:head@0-1"],
            "A": ["lead:PLING", "dbl:BIT:0:0.5", "bass:eighth", "arp:BIT:4@8", "drums:march@8"],
            "A2": ["lead:PLING", "dbl:BIT:0:0.5", "dbl:FLUTE:12:0.5@8", "stab:BIT:0,6,10", "bass:sixteenth", "drums:drive", "bell:head"],
            "B": ["lead:BIT", "bass:eighth", "drone", "bell:head@8", "arp:PLING:2@8"],
            "PEAK": ["lead:PLING", "dbl:BIT:0:0.6", "dbl:FLUTE:12:0.6", "cnt:GUITAR", "stab:PLING:0,6,8,14", "bass:sixteenth", "drone", "drums:double", "fill", "bell:hit", "arp:BIT:4"],
            "CALM": ["lead:PLING", "bass:half", "echo:3:CHIME@4", "bell:head", "drums:heart@8"],
        },
    ],
)

TITLES = ("Reaper's Arc (Boss004)", "Reaper's Arc, Sundered (Boss004)")
FILES = ("necromancer_phase1.nbs", "necromancer_phase2.nbs")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [out / f for f in FILES],
                 ["Snow mountain reaper, phase 1 (natural sounds): the world theme read backwards in A minor, a full silence every eighth bar. 80 bars, loops from bar 5. Same bars as phase 2.",
                  "Snow mountain reaper, phase 2 (electronic, a half step up): the same bars and chords, bass in sixteenths, the whole kit, unison tune, 8-bit solo."],
                 TITLES)


if __name__ == "__main__":
    main()
