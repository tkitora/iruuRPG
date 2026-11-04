#!/usr/bin/env python3
"""Makes necromancer_phase1.nbs and necromancer_phase2.nbs: "Reaper's Arc (Boss004)" and "..., Sundered (Boss004)", the snow mountain reaper in two phases.

    python3 tools/bgm/make_necromancer_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_necromancer_bgm.py outdir     writes them somewhere else

Written for DRIVE (t271: the earlier versions were dark but slow, falling and not like a boss) with tools/bgm/bossdrive.py: 300 BPM (20 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line),
the tune a cell of eight eighth notes repeated higher and higher, every eighth bar ending in a run that is left hanging UP, a pumping bass, a never-silent didgeridoo, a chugging guitar, drums that drive,
an ensemble hit every eighth bar and a snare roll before each block. The device: the scythe cuts - every eighth bar everything stops but the tune (a full silence, then the hit), and the cell arcs up and over (A C E C A C B G#).
Phase 2 is the same bars and chords a half step higher with the electric piano and the 8-bit chip, the bass in sixteenths, the drums doubled, the guitar chug twice as fast in the peak.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossdrive import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

SPEC = drive_spec(
    tonic=9, scale=[0, 2, 3, 5, 7, 8, 10],
    cell="A4:2 C5:2 E5:2 C5:2 A4:2 C5:2 B4:2 G#4:2", answer="E5:2 A4:2 B4:2 C5:2 D5:2 E5:2 C5:2 E5:2",
    tail1="A4:2 B4:2 C5:2 D5:2 E5:2 D5:2 E5:2 E5:2", tail2="G#4:2 A4:2 G#4:2 A4:2 B4:2 C5:2 D5:2 E5:2",
    high1="A4:2 E5:2 A4:2 C5:2 A4:2 E5:2 D5:2 C5:2", high2="C5:2 E5:2 C5:2 E5:2 D5:2 E5:2 D5:2 E5:2",
    trill=" ".join(["E4:2", "F4:2"] * 4),
    prog_a="Am Am F G Am Am F E".split(), prog_b="Am Bb Am Bb Am Bb E E".split(), prog_p="F G Am Am F G E E".split(), prog_c="Am Am Bb Bb Am Am E E".split(),
    pedal_b="Am", breaks={"A": (7, 15, 23, 31), "A2": (7, 15, 23, 31), "PEAK": (15, 31)}, run=(53, 55, 57, 59),
)
TITLES = ("Reaper's Arc (Boss004)", "Reaper's Arc, Sundered (Boss004)")
FILES = ("necromancer_phase1.nbs", "necromancer_phase2.nbs")
DESCRIPTIONS = ("Snow mountain reaper, phase 1, written for drive: 300 BPM, an arching cell in A minor, a full silence every eighth bar. Same bars as phase 2.", "Snow mountain reaper, phase 2: the same bars and chords a half step higher, electric piano and 8-bit, sixteenth bass, doubled drums.")
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
