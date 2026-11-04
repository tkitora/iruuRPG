#!/usr/bin/env python3
"""Makes colossus_phase1.nbs and colossus_phase2.nbs: "Shoulders of Stone (Boss003)" and "Shoulders of Stone, Unbound (Boss003)", the two phases of one boss fight, written again by the BGM制作ガイド.

    python3 tools/bgm/make_atlas_boss3_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss3_bgm.py outdir     writes them somewhere else

The piece (the guide's recipe row "ボス" and its chapter on the phases): D minor with the C# of the dominant, 4/4, 150 BPM (10 ticks a second, a tick is a sixteenth), 80 bars (2:08), the form
intro 4 / A 16 / A' 16 / B 16 (takes away) / A'' 16 (the peak, from bar 52 = 65 %) / calm 12, looping from bar 5. The theme is the motif of Atlas (D5 A4 F4 D4, the world theme of the plains turned upside down: the
enemy's theme is the world's, reversed). The chord changes every bar (a fight); in the peak twice a bar, over a D in the bass (a pedal: the guide's tenth progression). The one device: the weight of stone -
phase 1 is in half time (the bass walks in quarters, the snare falls on the third beat only), phase 2 in double time.
Phase 1 (60-70 % of the density, natural sounds): harp, guitar, flute an octave up, bass, didgeridoo, bass drum and snare; the bridge is a bass solo (the motif two octaves down).
Phase 2 is the SAME bars and chords, a half step higher (E-flat minor), with four of the guide's changes: the bass in sixteenths, all the drums and the drone and the bell hits added, the theme in unison on three
instruments (8-bit, flute, electric piano) and the bridge an 8-bit solo. Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossframe import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TONIC, SCALE = 2, [0, 2, 3, 5, 7, 8, 10]
K = key_pcs(TONIC, SCALE)

T = "D5:6 A4:2 F4:4 D4:4"                        # the motif of Atlas
U = "F4:4 A4:4 D5:8"                              # its answer
a1 = [T, U, mv(T, K, -2), "D4:4 F4:4 Bb4:8", mv(T, K, -4), "Bb3:4 D4:4 G4:8", "E5:6 C#5:2 A4:4 E4:4", "A4:4 C#5:4 E5:8"]
a2 = [T, "F4:2 G4:2 A4:4 D5:8", mv(T, K, -2), "D5:4 C5:4 Bb4:8", mv(T, K, -4), "E5:6 C#5:2 A4:8", "D5:8 A4:4 F4:4", "D4:8 r:8"]
a3 = [T, "A4:4 F4:4 D5:8", mv(T, K, -2), "F5:4 D5:4 Bb4:8", mv(T, K, -4), "G5:2 F5:2 D5:4 Bb4:8" .replace("G5", "G4"), "E5:6 C#5:2 A4:8", "E5:8 A4:8"]
# B: the motif on the root of each chord (a solo)
b1 = [mv(T, K, -2), mv(U, K, -2), mv(T, K, -4), mv(U, K, -4), "C5:6 A4:2 F4:4 C4:4", "A3:4 C4:4 F4:8", mv(T, K, -1), "E4:4 G4:4 C5:8"]
b2 = [mv(T, K, -2), mv(U, K, -2), mv(T, K, -4), mv(U, K, -4), "Eb5:6 Bb4:2 G4:4 Eb4:4", "G4:4 Bb4:4 Eb5:8", "A4:6 C#5:2 E5:4 A4:4", "E5:8 r:8"]
# the peak: the motif in its biggest form, over a D in the bass
p1 = [T, "F4:2 A4:2 D5:4 F5:8", "G5:0".replace("G5:0", "G4:2 Bb4:2 D5:4 G4:8"), "A4:2 C#5:2 E5:4 A4:8", T, "F4:2 A4:2 D5:4 F5:8", "Bb4:4 D5:4 F5:4 D5:4", "A4:4 C#5:4 E5:8"]
p2 = [T, "F5:4 E5:4 D5:8", mv(T, K, 2), "F5:4 E5:4 D5:8", "G4:2 Bb4:2 D5:4 G4:8", "A4:2 C#5:2 E5:4 A5:8".replace("A5", "A4"), "D5:6 A4:2 F4:4 D4:4", "D4:8 r:8"]
# the calm: fragments of the motif, getting lower
c1 = ["D5:6 A4:2 F4:8", "r:16", "Bb4:6 F4:2 D4:8", "r:16", "A4:6 F4:2 C4:8", "r:16", "G4:6 D4:2 Bb3:8", "r:16", "E4:6 G4:2 C5:8", "r:16", "E5:6 C#5:2 A4:8", "E4:4 A4:4 C#5:4 E5:4"]

SPEC = dict(
    bar=16, tps=10.0, tonic=TONIC, scale=SCALE, shift=[0, 1],
    sections=[("A", 16), ("A2", 16), ("B", 16), ("PEAK", 16), ("CALM", 12)],
    chords={
        "intro": ["Dm", "Dm", "Gm", "A"],
        "A": "Dm Dm Bb Bb Gm Gm A A Dm Dm Bb Bb Gm A Dm Dm".split(),
        "A2": "Dm Dm Bb Bb Gm Gm A A Dm Dm Bb Bb Gm Gm A A".split(),
        "B": "Bb Bb Gm Gm F F C C Bb Bb Gm Gm Eb Eb A A".split(),
        "PEAK": ["Dm|Bb", "Dm|Bb", "Gm|A", "Gm|A", "Dm|Bb", "Dm|Bb", "Gm|A", "Gm|A", "Dm|Bb", "Dm|Bb", "Gm|A", "Gm|A", "Dm|F", "Dm|F", "Gm|A", "A"],
        "CALM": "Gm Gm Bb Bb F F Gm Gm C C A A".split(),
    },
    melody={
        "intro": None,
        "A": a1 + a2,
        "A2": a1 + a3,
        "B": b1 + b2,
        "PEAK": p1 + p2,
        "CALM": c1,
    },
    pedal={"PEAK": "Dm"},
    level={"intro": 0.8, "A": 0.9, "A2": 1.0, "B": 0.85, "PEAK": 1.1, "CALM": 0.75},
    arp_low=54, arp_high=72, stab_low=54, stab_high=68,
    run=[57, 60, 62, 64],
    voices=[
        {   # phase 1: natural sounds, half time
            "intro": ["bass:half@2", "arp:HARP:4", "bell:head@0-1"],
            "A": ["lead:HARP", "bass:half", "drone@8", "drums:heart@8", "arp:HARP:4@0-8"],
            "A2": ["lead:HARP", "dbl:FLUTE:12:0.5@8", "stab:GUITAR:0,8", "bass:walk", "drone", "drums:march", "bell:head"],
            "B": ["lead:BASS", "drone", "bell:head@8", "arp:HARP:4@8"],
            "PEAK": ["lead:HARP", "dbl:FLUTE:12:0.6", "cnt:GUITAR", "stab:GUITAR:0,8", "bass:pedal", "drone", "drums:march", "fill", "bell:hit", "arp:HARP:2@8"],
            "CALM": ["lead:HARP", "bass:half", "echo:3:CHIME@4", "bell:head"],
        },
        {   # phase 2: electronic, double time, a half step up
            "intro": ["bass:half@2", "arp:PLING:4", "bell:head@0-1"],
            "A": ["lead:PLING", "dbl:BIT:0:0.45@8", "bass:eighth", "drone@8", "drums:march@8", "arp:BIT:2@0-8"],
            "A2": ["lead:PLING", "dbl:FLUTE:12:0.5@8", "stab:BIT:0,6,10", "bass:sixteenth", "drone", "drums:drive", "bell:head"],
            "B": ["lead:BIT", "bass:eighth@8", "drone", "arp:PLING:2@8", "bell:head@8"],
            "PEAK": ["lead:PLING", "dbl:BIT:0:0.6", "dbl:FLUTE:12:0.6", "cnt:GUITAR", "stab:PLING:0,6,8,14", "bass:sixteenth", "drone", "drums:double", "fill", "bell:hit", "arp:BIT:4"],
            "CALM": ["lead:PLING", "bass:half", "echo:3:CHIME@4", "bell:head", "drums:heart@8"],
        },
    ],
)

TITLES = ("Shoulders of Stone (Boss003)", "Shoulders of Stone, Unbound (Boss003)")
FILES = ("colossus_phase1.nbs", "colossus_phase2.nbs")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [out / f for f in FILES],
                 ["Boss phase 1 (natural sounds, half time): the motif of Atlas turned upside down from the world theme, D minor, 80 bars, bass solo in the bridge. Same bars as phase 2.",
                  "Boss phase 2 (electronic, double time, a half step up): the same bars and chords as phase 1, bass in sixteenths, all drums, drone and bell hits, unison theme, 8-bit solo."],
                 TITLES)


if __name__ == "__main__":
    main()
