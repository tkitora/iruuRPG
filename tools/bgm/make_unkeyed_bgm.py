#!/usr/bin/env python3
"""Makes unkeyed_phase1.nbs and unkeyed_phase2.nbs: "Eyes Without a Key (Boss009)" and "..., Unwritten (Boss009)", a boss beyond human understanding, written again by the BGM制作ガイド.

    python3 tools/bgm/make_unkeyed_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_unkeyed_bgm.py outdir     writes them somewhere else

The guide's recipe row "腐敗・闇の地域" (whole-tone and diminished sounds, the world theme shifted by a half step and turned back to front, the chords of the home song BROKEN): there is no key. 4/4, 150 BPM, 80 bars (2:08);
intro 4 / A 16 / A' 16 / B 16 / A'' 16 (the peak, bar 52 = 65 %) / calm 12, loop from bar 5. The chords are augmented triads (they belong to no key) and their root FALLS A HALF STEP EVERY THREE BARS through the whole
song (the fall that never lands: C, B, B-flat, A ... all the way down), the melody is the world theme with its intervals made augmented (four semitones, four semitones: E G# C | C Db F) and its answer read backwards.
The device: the tune changes its instrument at every statement (harp, 8-bit, iron xylophone, banjo ...) so that it is never the same speaker, and in the peak the themes of the earlier bosses (Atlas, the reaper,
the spider) are quoted at once, wrongly. Phase 1 (natural, a half step... of nothing): quiet, the observation; Phase 2 (a half step higher): the same bars, the beat starts at once, the 8-bit goes over the tune.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossframe import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TONIC, SCALE = 0, [0, 2, 4, 6, 8, 10]            # the whole-tone scale on C: no key
K = key_pcs(TONIC, SCALE)
R = "r:16"
ROOTS = ["C", "B", "Bb", "A", "Ab", "G", "Gb", "F", "E", "Eb", "D", "Db"]


def falling(first_bar, bars):
    """The chord of each bar: a root a half step lower every three bars, from the first bar of the song."""
    return [ROOTS[((first_bar + i) // 3) % 12] + "aug" for i in range(bars)]


T = "E4:2 G#4:2 C5:4 C5:2 Db5:2 F5:4"             # the world theme with augmented steps, then a half step
U = "F5:4 Db5:4 B4:4 G4:4"                        # read backwards
a1 = [T, U, mv(T, K, -2), mv(U, K, -2), T, "G#4:4 C5:4 E5:8", mv(T, K, 1), "E5:8 r:8"]
a2 = [T, "Db5:4 C5:4 G#4:8", mv(T, K, -2), "B4:4 G#4:4 E4:8", mv(T, K, 2), R, mv(T, K, -1), "E4:8 r:8"]
a3 = [T, "F5:4 E5:4 Db5:8", mv(T, K, -1), "C5:4 B4:4 G4:8", mv(T, K, 2), "G#4:4 C5:4 E5:8", mv(T, K, -3), R]
b1 = [mv(T, K, -3), R, mv(T, K, -3), R, mv(T, K, -4), R, mv(T, K, -4), R]
b2 = [mv(T, K, -3), mv(U, K, -3), mv(T, K, -4), mv(U, K, -4), "C4:2 E4:2 G#4:4 C5:8", "E5:4 G#4:4 C4:8", "Db4:2 F4:2 A4:4 Db5:8", "A4:8 r:8"]
ATLAS = "D5:6 A4:2 F4:4 D4:4"                     # the earlier bosses, quoted wrongly
REAPER = "E5:2 D5:2 C5:4 C5:2 G4:2 E4:4"
SPIDER = "G4:3 Bb4:3 C5:3 Eb5:3 D5:2 C5:2"
p1 = [T, mv(T, K, 1), ATLAS, mv(ATLAS, K, 1), T, REAPER, mv(REAPER, K, 1), SPIDER]
p2 = [T, mv(T, K, 2), ATLAS, REAPER, SPIDER, mv(T, K, -2), mv(T, K, 3), "C5:16"]
c1 = ["E5:4 C5:4 G#4:8", R, "Db5:4 B4:4 G4:8", R, "C5:4 A4:4 F4:8", R, "B4:4 G4:4 Eb4:8", R, "A4:4 F4:4 Db4:8", R, "G4:4 E4:4 C4:8", "E4:4 G#4:4 C5:4 E5:4"]

SPEC = dict(
    bar=16, tps=10.0, tonic=TONIC, scale=SCALE, shift=[0, 1],
    sections=[("A", 16), ("A2", 16), ("B", 16), ("PEAK", 16), ("CALM", 12)],
    chords={
        "intro": falling(0, 4),
        "A": falling(4, 16),
        "A2": falling(20, 16),
        "B": falling(36, 16),
        "PEAK": falling(52, 16),
        "CALM": falling(68, 12),
    },
    melody={"intro": None, "A": a1 + a2, "A2": a1 + a3, "B": b1 + b2, "PEAK": p1 + p2, "CALM": c1},
    level={"intro": 0.8, "A": 0.9, "A2": 1.0, "B": 0.85, "PEAK": 1.1, "CALM": 0.75},
    arp_low=54, arp_high=72, stab_low=54, stab_high=68,
    run=[54, 56, 58, 60],
    voices=[
        {   # phase 1: the observation - no beat until the peak, the tune changes its voice
            "intro": ["bass:half@2", "arp:HARP:4"],
            "A": ["lead:HARP", "bass:half", "bell:head"],
            "A2": ["lead:BANJO", "dbl:HARP:0:0.5", "stab:GUITAR:0,8@8", "bass:walk", "drums:heart@8", "bell:head"],
            "B": ["lead:IRON_XYLOPHONE", "bass:half@8", "drone", "arp:HARP:4@8", "bell:head@8"],
            "PEAK": ["lead:HARP", "dbl:FLUTE:12:0.6", "cnt:GUITAR", "stab:GUITAR:0,8", "bass:pedal", "drone", "drums:march", "fill", "bell:hit", "arp:HARP:4@8"],
            "CALM": ["lead:HARP", "bass:half", "echo:3:CHIME@4", "bell:head"],
        },
        {   # phase 2: the beat starts at once, the 8-bit over the tune
            "intro": ["bass:half@2", "arp:PLING:4", "drums:heart"],
            "A": ["lead:BIT", "dbl:HARP:0:0.5", "bass:eighth", "drums:march", "bell:head"],
            "A2": ["lead:PLING", "dbl:BIT:0:0.5", "stab:BIT:0,6,10", "bass:sixteenth", "drums:drive", "bell:head"],
            "B": ["lead:BIT", "bass:eighth", "drone", "arp:PLING:2@8", "bell:head@8"],
            "PEAK": ["lead:PLING", "dbl:BIT:0:0.6", "dbl:FLUTE:12:0.6", "cnt:GUITAR", "stab:PLING:0,6,8,14", "bass:sixteenth", "drone", "drums:double", "fill", "bell:hit", "arp:BIT:4"],
            "CALM": ["lead:PLING", "bass:half", "echo:3:CHIME@4", "bell:head", "drums:heart"],
        },
    ],
)

TITLES = ("Eyes Without a Key (Boss009)", "Eyes Without a Key, Unwritten (Boss009)")
FILES = ("unkeyed_phase1.nbs", "unkeyed_phase2.nbs")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [out / f for f in FILES],
                 ["Boss without a key, phase 1: augmented chords falling a half step every three bars, the world theme with augmented steps, the tune changes its voice, the earlier boss themes quoted in the peak. 80 bars, loops from bar 5. Same bars as phase 2.",
                  "Boss without a key, phase 2 (a half step up): the same bars, the beat from the first bar, the 8-bit over the tune."],
                 TITLES)


if __name__ == "__main__":
    main()
