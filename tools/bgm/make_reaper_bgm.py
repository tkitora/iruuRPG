#!/usr/bin/env python3
"""Makes reaper_phase1.nbs and reaper_phase2.nbs: "Silence of the Scythe (Boss005)" and "..., Unleashed (Boss005)", another reaper fight in two phases.

    python3 tools/bgm/make_reaper_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_reaper_bgm.py outdir     writes them somewhere else

Written for DRIVE (t271: the earlier versions were dark but slow, falling and not like a boss) with tools/bgm/bossdrive.py: 300 BPM (20 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line),
the tune a cell of eight eighth notes repeated higher and higher, every eighth bar ending in a run that is left hanging UP, a pumping bass, a never-silent didgeridoo, a chugging guitar, drums that drive,
an ensemble hit every eighth bar and a snare roll before each block. The device: stabs and rests - the cell is a syncopated stab with holes in it (the silence between the blows is the threat), and a half-step trill climbs over a B pedal.
Phase 2 is the same bars and chords a half step higher with the electric piano and the 8-bit chip, the bass in sixteenths, the drums doubled, the guitar chug twice as fast in the peak.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossdrive import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

SPEC = drive_spec(
    tonic=11, scale=[0, 2, 3, 5, 7, 8, 10],
    cell="B3:2 r:2 B3:2 D4:2 r:2 F#4:2 B4:2 r:2", answer="D5:2 r:2 B4:2 D5:2 E5:2 r:2 D5:2 E5:2",
    tail1="F#4:2 G4:2 A4:2 B4:2 C#5:2 D5:2 E5:2 E5:2", tail2="A#4:2 B4:2 A#4:2 B4:2 C#5:2 D5:2 D#5:2 E5:2",
    high1="B4:2 B4:2 r:2 D5:2 B4:2 B4:2 r:2 E5:2", high2="D5:2 D5:2 r:2 E5:2 D5:2 B4:2 r:2 E5:2",
    trill=" ".join(["F#4:2", "G4:2"] * 4),
    prog_a="Bm Bm G A Bm Bm G F#".split(), prog_b="Bm C Bm C Bm C F# F#".split(), prog_p="G A Bm Bm G A F# F#".split(), prog_c="Bm Bm C C Bm Bm F# F#".split(),
    pedal_b="Bm", run=(54, 56, 58, 60),
)
TITLES = ("Silence of the Scythe (Boss005)", "Silence of the Scythe, Unleashed (Boss005)")
FILES = ("reaper_phase1.nbs", "reaper_phase2.nbs")
DESCRIPTIONS = ("Reaper fight, phase 1, written for drive: 300 BPM, a syncopated stab with holes in B minor over a B pedal, ensemble hits. Same bars as phase 2.", "Reaper fight, phase 2: the same bars and chords a half step higher, electric piano and 8-bit, sixteenth bass, doubled drums.")
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
