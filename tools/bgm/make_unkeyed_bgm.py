#!/usr/bin/env python3
"""Makes unkeyed_phase1.nbs and unkeyed_phase2.nbs: "Eyes Without a Key (Boss009)" and "..., Unwritten (Boss009)", a boss beyond human understanding.

    python3 tools/bgm/make_unkeyed_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_unkeyed_bgm.py outdir     writes them somewhere else

Written for DRIVE (t271: the earlier versions were dark but slow, falling and not like a boss) with tools/bgm/bossdrive.py: 300 BPM (20 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line),
the tune a cell of eight eighth notes repeated higher and higher, every eighth bar ending in a run that is left hanging UP, a pumping bass, a never-silent didgeridoo, a chugging guitar, drums that drive,
an ensemble hit every eighth bar and a snare roll before each block. The device: no key - the whole-tone scale, augmented chords that fall a half step EVERY BAR (a fall that never lands, twice as fast as before), and a tune that changes its voice (banjo, iron xylophone) from block to block.
Phase 2 is the same bars and chords a half step higher with the electric piano and the 8-bit chip, the bass in sixteenths, the drums doubled, the guitar chug twice as fast in the peak.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from bossdrive import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

SPEC = drive_spec(
    tonic=0, scale=[0, 2, 4, 6, 8, 10],
    cell="C4:2 D4:2 E4:2 F#4:2 G#4:2 F#4:2 E4:2 G#4:2", answer="A#4:2 G#4:2 A#4:2 C5:2 D5:2 C5:2 D5:2 E5:2",
    tail1="E4:2 F#4:2 G#4:2 A#4:2 C5:2 D5:2 E5:2 E5:2", tail2="A#4:2 C5:2 A#4:2 C5:2 D5:2 D#5:2 E5:2 E5:2",
    high1="C4:2 C5:2 C4:2 E4:2 C4:2 C5:2 A#4:2 G#4:2", high2="E4:2 G#4:2 C5:2 G#4:2 F#4:2 A#4:2 D5:2 A#4:2",
    trill=" ".join(["C4:2", "C#4:2"] * 4),
    prog_a="Caug Baug Bbaug Aaug Abaug Gaug Gbaug Faug".split(), prog_b="Eaug Ebaug Daug Dbaug Caug Baug Bbaug Aaug".split(),
    prog_p="Caug Baug Bbaug Aaug Abaug Gaug Gbaug Faug".split(), prog_c="Faug Eaug Ebaug Daug Dbaug Caug Baug Bbaug".split(),
    run=(54, 56, 58, 60), extra_voices={"A2": ["dbl:BANJO:0:0.6"], "B": ["dbl:IRON_XYLOPHONE:0:0.6"]},
)
TITLES = ("Eyes Without a Key (Boss009)", "Eyes Without a Key, Unwritten (Boss009)")
FILES = ("unkeyed_phase1.nbs", "unkeyed_phase2.nbs")
DESCRIPTIONS = ("Boss without a key, phase 1, written for drive: 300 BPM, whole-tone cell, augmented chords falling a half step every bar. Same bars as phase 2.", "Boss without a key, phase 2: the same bars a half step higher, electric piano and 8-bit, sixteenth bass, doubled drums.")
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
