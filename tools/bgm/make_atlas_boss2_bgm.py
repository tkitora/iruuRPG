#!/usr/bin/env python3
"""Makes atlas_vindicator_2.nbs: "Raise the Axe (Boss002)", the boss of the Atlas cave (one song, no second phase).

    python3 tools/bgm/make_atlas_boss2_bgm.py            writes atlas_vindicator_2.nbs into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss2_bgm.py outdir     writes them somewhere else

Written for DRIVE (t271: the earlier versions were dark but slow, falling and not like a boss) with tools/bgm/bossdrive.py: 300 BPM (20 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line),
the tune a cell of eight eighth notes repeated higher and higher, every eighth bar ending in a run that is left hanging UP, a pumping bass, a never-silent didgeridoo, a chugging guitar, drums that drive,
an ensemble hit every eighth bar and a snare roll before each block. The device: the axe is raised again and again - the cell climbs the E minor scale, a Phrygian F under a pedal E in B, and the peak turns bright (C, D, B) and runs.
Phase 2 is the same bars and chords a half step higher with the electric piano and the 8-bit chip, the bass in sixteenths, the drums doubled, the guitar chug twice as fast in the peak.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossdrive import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

SPEC = drive_spec(
    tonic=4, scale=[0, 2, 3, 5, 7, 8, 10],
    cell="E4:2 G4:2 B4:2 G4:2 E5:2 B4:2 G4:2 B4:2", answer="B4:2 E5:2 D5:2 B4:2 C5:2 D5:2 E5:2 B4:2",
    tail1="G4:2 A4:2 B4:2 C5:2 D5:2 E5:2 D5:2 E5:2", tail2="B4:2 C5:2 B4:2 C5:2 D5:2 D#5:2 E5:2 E5:2",
    high1="E4:2 E5:2 E4:2 B4:2 E4:2 E5:2 D5:2 B4:2", high2="G4:2 B4:2 E5:2 B4:2 A4:2 C5:2 E5:2 C5:2",
    trill=" ".join(["B3:2", "C4:2"] * 4),
    prog_a="Em Em C D Em Em C B".split(), prog_b="Em F Em F Em F B B".split(), prog_p="C D Em Em C D B B".split(), prog_c="Em Em F F Em Em B B".split(),
    pedal_b="Em", run=(55, 57, 59, 61),
)
SPEC["shift"] = [0]
SPEC["voices"] = SPEC["voices"][:1]
TITLES = ("Raise the Axe (Boss002)",)
FILES = ("atlas_vindicator_2.nbs",)
DESCRIPTIONS = ("Boss of the Atlas cave, written for drive: 300 BPM, a rising cell in E minor, a pumping bass, ensemble hits, a bright run in the peak. Loops from tick 64.",)
SINGLE = True


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
