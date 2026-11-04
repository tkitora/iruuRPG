#!/usr/bin/env python3
"""Makes reaper_phase1.nbs and reaper_phase2.nbs: "Silence of the Scythe (Boss005)" and "Silence of the Scythe, Unleashed (Boss005)", another reaper fight in two phases, written again by the BGM制作ガイド.

    python3 tools/bgm/make_reaper_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_reaper_bgm.py outdir     writes them somewhere else

B minor, 4/4, 150 BPM, 80 bars (2:08); intro 4 / A 16 / A' 16 / B 16 / A'' 16 (the peak, from bar 52 = 65 %) / calm 12, loop from bar 5. The theme is the world theme of the plains in B minor, rising,
asked like a question and left unanswered (D4 F#4 B4 | B4 C#5 D5). The device is SILENCE (the guide's ghost rest): in A the melody and the bass are all there is and every other bar is empty; the rests are the music.
Phase 1 (natural): a piano (the harp) solo with the bass and a heartbeat; the bell only on the silent bars. Phase 2 (a half step up, C minor): the same bars and chords, with the silences filled with guitar chords,
the bass in eighths and sixteenths, the whole kit in the peak, an 8-bit solo in the bridge, the tune in unison. Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossframe import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TONIC, SCALE = 11, [0, 2, 3, 5, 7, 8, 10]
K = key_pcs(TONIC, SCALE)

T = "D4:2 F#4:2 B4:4 B4:2 C#5:2 D5:4"
U = "D5:4 C#5:4 B4:8"
R = "r:16"
a1 = seq_block(T, U, K, [0, ("u", 0), R, -2, -3, R, "A#4:6 C#5:2 F#5:4 C#5:4", "F#5:8 r:8"])
a2 = seq_block(T, U, K, [0, "F#4:2 A4:2 B4:4 D5:8", R, -2, -3, "G4:4 B4:4 E5:8", "A#4:6 C#5:2 F#5:4 C#5:4", R])
a3 = seq_block(T, U, K, [0, "F#4:2 A4:2 D5:4 B4:8", mv(T, K, 2), -2, -3, "G4:4 B4:4 E5:8", "A#4:6 C#5:2 F#5:4 C#5:4", "F#5:8 C#5:8"])
b1 = seq_block(T, U, K, [-2, R, -3, R, -1, R, 0, R])
b2 = seq_block(T, U, K, [-2, ("u", -2), -3, ("u", -3), "A4:6 E4:2 C#4:4 A3:4", "C#4:4 E4:4 A4:8", "A#4:6 C#5:2 F#5:4 C#5:4", "F#5:8 r:8"])
p1 = seq_block(T, U, K, [0, "F#4:2 A4:2 D5:4 F#5:8", 0, "F#4:2 A4:2 D5:4 B4:8", -2, "B3:2 D4:2 G4:4 B4:8", -3, "E4:2 G4:2 B4:4 E5:8"])
p2 = seq_block(T, U, K, [0, "F#4:2 A4:2 D5:4 F#5:8", -2, "B3:2 D4:2 G4:4 B4:8", -3, "E4:2 G4:2 B4:4 E5:8", "A#4:6 C#5:2 F#5:4 C#5:4", "B4:16"])
c1 = ["D5:6 B4:2 F#4:8", R, "B4:6 G4:2 D4:8", R, "G4:6 E4:2 B3:8", R, "A4:6 F#4:2 C#4:8", R, "B3:4 D4:4 F#4:8", R, "A#4:6 C#5:2 F#5:4 C#5:4", "F#3:4 A#3:4 C#4:4 F#4:4"]

SPEC = dict(
    bar=16, tps=10.0, tonic=TONIC, scale=SCALE, shift=[0, 1],
    sections=[("A", 16), ("A2", 16), ("B", 16), ("PEAK", 16), ("CALM", 12)],
    chords={
        "intro": ["Bm", "Bm", "G", "F#"],
        "A": "Bm Bm G G Em Em F# F# Bm Bm G G Em F# Bm Bm".split(),
        "A2": "Bm Bm G G Em Em F# F# Bm Bm G G Em Em F# F#".split(),
        "B": "G G Em Em A A F# F# G G Em Em D D F# F#".split(),
        "PEAK": ["Bm|G", "Bm|G", "Em|F#", "Em|F#", "Bm|G", "Bm|G", "Em|F#", "Em|F#", "Bm|G", "Bm|G", "Em|F#", "Em|F#", "G|A", "G|A", "F#|F#", "F#"],
        "CALM": "Em Em G G A A Em Em D D F# F#".split(),
    },
    melody={"intro": None, "A": a1 + a2, "A2": a1 + a3, "B": b1 + b2, "PEAK": p1 + p2, "CALM": c1},
    pedal={"PEAK": "Bm"},
    level={"intro": 0.8, "A": 0.9, "A2": 1.0, "B": 0.85, "PEAK": 1.1, "CALM": 0.75},
    arp_low=54, arp_high=72, stab_low=54, stab_high=68,
    run=[54, 56, 58, 60],
    voices=[
        {
            "intro": ["bass:half@2", "arp:HARP:4"],
            "A": ["lead:HARP", "bass:half", "drums:heart@8", "bell:head"],
            "A2": ["lead:HARP", "dbl:FLUTE:12:0.5@8", "stab:GUITAR:0@8", "bass:walk", "drums:march@8", "bell:head"],
            "B": ["lead:HARP", "bass:half@8", "drone", "arp:HARP:4@8", "bell:head"],
            "PEAK": ["lead:HARP", "dbl:FLUTE:12:0.6", "cnt:GUITAR", "stab:GUITAR:0,8", "bass:pedal", "drone", "drums:march", "fill", "bell:hit", "arp:HARP:2@8"],
            "CALM": ["lead:HARP", "bass:half", "echo:3:CHIME@4", "bell:head"],
        },
        {
            "intro": ["bass:half@2", "arp:PLING:4"],
            "A": ["lead:PLING", "dbl:BIT:0:0.45", "stab:GUITAR:0,8", "bass:eighth", "drums:march@8", "bell:head"],
            "A2": ["lead:PLING", "dbl:BIT:0:0.5", "dbl:FLUTE:12:0.5@8", "stab:BIT:0,6,10", "bass:sixteenth", "drums:drive", "bell:head"],
            "B": ["lead:BIT", "bass:eighth@8", "drone", "arp:PLING:2@8", "bell:head"],
            "PEAK": ["lead:PLING", "dbl:BIT:0:0.6", "dbl:FLUTE:12:0.6", "cnt:GUITAR", "stab:PLING:0,6,8,14", "bass:sixteenth", "drone", "drums:double", "fill", "bell:hit", "arp:BIT:4"],
            "CALM": ["lead:PLING", "bass:half", "echo:3:CHIME@4", "bell:head", "drums:heart@8"],
        },
    ],
)

TITLES = ("Silence of the Scythe (Boss005)", "Silence of the Scythe, Unleashed (Boss005)")
FILES = ("reaper_phase1.nbs", "reaper_phase2.nbs")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [out / f for f in FILES],
                 ["Reaper fight, phase 1 (natural sounds): the world theme as an unanswered question in B minor, every other bar silent. 80 bars, loops from bar 5. Same bars as phase 2.",
                  "Reaper fight, phase 2 (electronic, a half step up): the same bars and chords with the silences filled; bass in sixteenths, the whole kit, unison tune."],
                 TITLES)


if __name__ == "__main__":
    main()
