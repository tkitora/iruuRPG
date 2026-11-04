#!/usr/bin/env python3
"""Makes waltz_phase1.nbs and waltz_phase2.nbs: "Waltz at the Edge of Dark (Boss008)" and "..., Out of Step (Boss008)", a waltz boss in two phases.

    python3 tools/bgm/make_waltz_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_waltz_bgm.py outdir     writes them somewhere else

Written for DRIVE (t271: the earlier versions were dark but slow, falling and not like a boss) with tools/bgm/bossdrive.py: 300 BPM (20 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line),
the tune a cell of eight eighth notes repeated higher and higher, every eighth bar ending in a run that is left hanging UP, a pumping bass, a never-silent didgeridoo, a chugging guitar, drums that drive,
an ensemble hit every eighth bar and a snare roll before each block. The device: three beats and no mercy - 3/4 (a bar of 12 ticks, 200 bars, every section a multiple of eight bars), a heavy first beat (bass and bass drum) and a rising cell of six notes.
Phase 2 is the same bars and chords a half step higher with the electric piano and the 8-bit chip, the bass in sixteenths, the drums doubled, the guitar chug twice as fast in the peak.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossdrive import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

SPEC = drive_spec(
    bar=12, tonic=9, scale=[0, 2, 3, 5, 7, 8, 10],
    cell="A3:2 C4:2 E4:2 A4:2 C5:2 E5:2", answer="E5:2 D5:2 C5:2 B4:2 C5:2 E5:2",
    tail1="A4:2 B4:2 C5:2 D5:2 E5:2 E5:2", tail2="G#4:2 A4:2 B4:2 C5:2 D5:2 E5:2",
    high1="A4:2 E5:2 A4:2 C5:2 A4:2 E5:2", high2="C5:2 E5:2 C5:2 E5:2 D5:2 E5:2",
    trill=" ".join(["E4:2", "F4:2"] * 3),
    prog_a="Am Am Dm Dm F F E E".split(), prog_b="Am Bb Am Bb Am Bb E E".split(), prog_p="F G Am Am F G E E".split(), prog_c="Am Am Bb Bb Am Am E E".split(),
    blocks=(6, 6, 5, 5, 3), pedal_b="Am", run=(57, 59, 61, 63),
)
for phase in (0, 1):
    for tags in SPEC["voices"][phase].values():
        for i, tag in enumerate(tags):
            body, _, window = tag.partition("@")
            parts = body.split(":")
            if parts[0] == "bass":
                parts[1] = "waltz"
            if parts[0] == "drums":
                parts[1] = "waltz2" if phase == 1 or parts[1] in ("double", "drive") else "waltz"
            tags[i] = ":".join(parts) + ("@" + window if window else "")
TITLES = ("Waltz at the Edge of Dark (Boss008)", "Waltz at the Edge of Dark, Out of Step (Boss008)")
FILES = ("waltz_phase1.nbs", "waltz_phase2.nbs")
DESCRIPTIONS = ("Waltz boss, phase 1, written for drive: 3/4 at 300 BPM, a rising cell in A minor, a heavy first beat. Same bars as phase 2.", "Waltz boss, phase 2: the same bars and chords a half step higher, electric piano and 8-bit, doubled drums.")
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
