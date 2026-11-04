#!/usr/bin/env python3
"""Makes colossus_phase1.nbs and colossus_phase2.nbs: "Shoulders of Stone (Boss003)" and "..., Unbound (Boss003)", the stone giant in two phases.

    python3 tools/bgm/make_atlas_boss3_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss3_bgm.py outdir     writes them somewhere else

Written for DRIVE (t271: the earlier versions were dark but slow, falling and not like a boss) with tools/bgm/bossdrive.py: 300 BPM (20 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line),
the tune a cell of eight eighth notes repeated higher and higher, every eighth bar ending in a run that is left hanging UP, a pumping bass, a never-silent didgeridoo, a chugging guitar, drums that drive,
an ensemble hit every eighth bar and a snare roll before each block. The device: the stone does not stop - a D pedal under Eb (the Phrygian second) in B, the cell climbs the D minor scale in a stubborn rhythm (D D F A).
Phase 2 is the same bars and chords a half step higher with the electric piano and the 8-bit chip, the bass in sixteenths, the drums doubled, the guitar chug twice as fast in the peak.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossdrive import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

SPEC = drive_spec(
    tonic=2, scale=[0, 2, 3, 5, 7, 8, 10],
    cell="D4:2 D4:2 F4:2 A4:2 D5:2 A4:2 F4:2 A4:2", answer="A4:2 D5:2 C5:2 A4:2 Bb4:2 C5:2 D5:2 E5:2",
    tail1="F4:2 G4:2 A4:2 Bb4:2 C5:2 D5:2 C5:2 E5:2", tail2="A4:2 Bb4:2 A4:2 Bb4:2 C5:2 C#5:2 D5:2 E5:2",
    high1="D4:2 D5:2 D4:2 A4:2 D4:2 D5:2 C5:2 A4:2", high2="F4:2 A4:2 D5:2 A4:2 G4:2 Bb4:2 D5:2 Bb4:2",
    trill=" ".join(["A3:2", "Bb3:2"] * 4),
    prog_a="Dm Dm Bb C Dm Dm Bb A".split(), prog_b="Dm Eb Dm Eb Dm Eb A A".split(), prog_p="Bb C Dm Dm Bb C A A".split(), prog_c="Dm Dm Eb Eb Dm Dm A A".split(),
    pedal_b="Dm", run=(54, 57, 60, 62),
)
TITLES = ("Shoulders of Stone (Boss003)", "Shoulders of Stone, Unbound (Boss003)")
FILES = ("colossus_phase1.nbs", "colossus_phase2.nbs")
DESCRIPTIONS = ("Stone giant, phase 1, written for drive: 300 BPM, a stubborn rising cell in D minor over a D pedal, ensemble hits. Same bars as phase 2.", "Stone giant, phase 2: the same bars and chords a half step higher, electric piano and 8-bit, sixteenth bass, doubled drums.")
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
