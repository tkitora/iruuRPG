#!/usr/bin/env python3
"""Makes mirror_phase1.nbs and mirror_phase2.nbs: "The Cracked Mirror Laughs (Boss007)" and "..., Shattered (Boss007)", a boss of madness and mockery.

    python3 tools/bgm/make_mirror_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_mirror_bgm.py outdir     writes them somewhere else

Written for DRIVE (t271: the earlier versions were dark but slow, falling and not like a boss) with tools/bgm/bossdrive.py: 300 BPM (20 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line),
the tune a cell of eight eighth notes repeated higher and higher, every eighth bar ending in a run that is left hanging UP, a pumping bass, a never-silent didgeridoo, a chugging guitar, drums that drive,
an ensemble hit every eighth bar and a snare roll before each block. The device: the mirror - a second voice plays the cell upside down about F#4 (the guitar, then the 8-bit chip) so that the tune meets itself; F# Phrygian dominant with its half step (F#, G) and its major third (A#).
Phase 2 is the same bars and chords a half step higher with the electric piano and the 8-bit chip, the bass in sixteenths, the drums doubled, the guitar chug twice as fast in the peak.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossdrive import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

SPEC = drive_spec(
    tonic=6, scale=[0, 1, 4, 5, 7, 8, 10],
    cell="F#4:2 G4:2 A#4:2 G4:2 F#4:2 C#5:2 B4:2 C#5:2", answer="C#5:2 D5:2 C#5:2 A#4:2 B4:2 C#5:2 D5:2 E5:2",
    tail1="A#4:2 B4:2 C#5:2 D5:2 E5:2 D5:2 E5:2 E5:2", tail2="C#5:2 D5:2 C#5:2 D5:2 E5:2 D#5:2 E5:2 E5:2",
    high1="F#4:2 C#5:2 F#4:2 A#4:2 F#4:2 C#5:2 D5:2 A#4:2", high2="A#4:2 C#5:2 E5:2 C#5:2 B4:2 D5:2 E5:2 D5:2",
    trill=" ".join(["C#4:2", "D4:2"] * 4),
    prog_a="F# G F# G Bm G F# C#".split(), prog_b="F# G F# G F# G C# C#".split(), prog_p="G Bm F# F# G Bm C# C#".split(), prog_c="F# F# G G F# F# C# C#".split(),
    pedal_b="F#", axis=66, run=(56, 58, 60, 62),
)
for tag in ("A2", "PEAK"):
    SPEC["voices"][0][tag].append("mir:GUITAR")
    SPEC["voices"][1][tag].append("mir:BIT")
TITLES = ("The Cracked Mirror Laughs (Boss007)", "The Cracked Mirror Laughs, Shattered (Boss007)")
FILES = ("mirror_phase1.nbs", "mirror_phase2.nbs")
DESCRIPTIONS = ("Mirror boss, phase 1, written for drive: 300 BPM, a cell and its own reflection upside down, F# Phrygian dominant over an F# pedal. Same bars as phase 2.", "Mirror boss, phase 2: the same bars and chords a half step higher, the mirror on the 8-bit chip, sixteenth bass, doubled drums.")
SINGLE = False


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    if SINGLE:
        targets = [out if out.suffix == ".nbs" else (out / FILES[0])]
    else:
        out.mkdir(parents=True, exist_ok=True)
        targets = [out / f for f in FILES]
    if SINGLE and not targets[0].parent.exists():
        targets[0].parent.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, targets, list(DESCRIPTIONS), TITLES)


if __name__ == "__main__":
    main()
