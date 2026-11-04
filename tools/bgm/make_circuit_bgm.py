#!/usr/bin/env python3
"""Makes circuit_phase1.nbs and circuit_phase2.nbs: "Holy Circuit (Boss010)" and "Holy Circuit, Overclocked (Boss010)", a hymn that runs too fast, in two phases.

    python3 tools/bgm/make_circuit_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_circuit_bgm.py outdir     writes them somewhere else

Written for DRIVE (t271) with tools/bgm/bossdrive.py: 300 BPM (20 ticks a second, a beat of 4 ticks, a bar of 16), D Dorian, ONLY FIVE SOUNDS (the electric piano for the tune, the guitar for the chug, the bass,
the bass drum and the snare; at most five notes together; no hits, no power chords, no bells), the hymn a cell of eight eighth notes repeated higher and higher, every eighth bar ending in a run that is left
hanging up. The device: eight bars (in the third block of the bridge) with nothing but the tune and the guitar, then the bass and the drums come back. Phase 2 is the same bars a half step higher (E-flat Dorian),
the bass in sixteenths, the drums doubled. Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossdrive import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

SPEC = drive_spec(
    tonic=2, scale=[0, 2, 3, 5, 7, 9, 10],
    cell="D4:2 F4:2 A4:2 C5:2 D5:2 C5:2 A4:2 C5:2", answer="D5:2 E5:2 F5:2 E5:2 D5:2 C5:2 D5:2 E5:2",
    tail1="A4:2 B4:2 C5:2 D5:2 E5:2 D5:2 E5:2 E5:2", tail2="C5:2 D5:2 C5:2 D5:2 E5:2 F5:2 E5:2 E5:2",
    high1="D4:2 D5:2 D4:2 A4:2 D4:2 D5:2 C5:2 A4:2", high2="F4:2 A4:2 D5:2 A4:2 G4:2 B4:2 D5:2 B4:2",
    trill=" ".join(["A3:2", "A#3:2"] * 4),
    prog_a="Dm G Dm C Dm G Dm C".split(), prog_b="Dm G Dm C Am G Dm C".split(), prog_p="Dm G Dm C Dm G Dm C".split(), prog_c="G G C C A A C C".split(),
    breaks={"B": tuple(range(8, 16))}, run=(57, 59, 60, 62),
    voices=[
        {"intro": ["bass:pulse", "riff:GUITAR:4@1", "drums:four@1", "roll@3-4"],
         "A": ["lead:PLING", "bass:pulse", "riff:GUITAR:4", "drums:kicks", "fill"],
         "A2": ["lead:PLING", "bass:pulse", "riff:GUITAR:2", "drums:kicks", "fill"],
         "B": ["lead:PLING", "bass:pulse", "riff:GUITAR:4", "drums:half"] + ["roll@%d-%d" % (8 * k + 7, 8 * k + 8) for k in (0, 2, 3)],
         "PEAK": ["lead:PLING", "bass:pulse", "riff:GUITAR:2", "drums:kicks4", "fill"],
         "CALM": ["lead:PLING", "bass:half", "riff:GUITAR:8", "drums:heart"]},
        {"intro": ["bass:pulse", "riff:GUITAR:2@1", "drums:kicks@1", "roll@3-4"],
         "A": ["lead:PLING", "bass:pulse", "riff:GUITAR:2", "drums:kicks4", "fill"],
         "A2": ["lead:PLING", "bass:sixteenth", "riff:GUITAR:2", "drums:kicks4", "fill"],
         "B": ["lead:PLING", "bass:pulse", "riff:GUITAR:2", "drums:kicks"] + ["roll@%d-%d" % (8 * k + 7, 8 * k + 8) for k in (0, 2, 3)],
         "PEAK": ["lead:PLING", "bass:sixteenth", "riff:GUITAR:1", "drums:kicks4", "fill"],
         "CALM": ["lead:PLING", "bass:half", "riff:GUITAR:4", "drums:half"]},
    ],
)
SPEC["break_keep"] = ("lead", "riff")
TITLES = ("Holy Circuit (Boss010)", "Holy Circuit, Overclocked (Boss010)")
FILES = ("circuit_phase1.nbs", "circuit_phase2.nbs")
DESCRIPTIONS = ("Boss, phase 1, written for drive: five sounds only, D Dorian, 300 BPM, a hymn that runs too fast. Same bars as phase 2.", "Boss, phase 2: the same bars and chords a half step higher, bass in sixteenths, doubled drums.")
SINGLE = False


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [out / f for f in FILES], list(DESCRIPTIONS), TITLES)


if __name__ == "__main__":
    main()
