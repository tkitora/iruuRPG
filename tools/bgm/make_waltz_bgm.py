#!/usr/bin/env python3
"""Makes waltz_phase1.nbs and waltz_phase2.nbs: "Waltz at the Edge of Dark (Boss008)" and "..., Out of Step (Boss008)", an elegant and hopeless waltz boss in two phases, written again by the BGM制作ガイド.

    python3 tools/bgm/make_waltz_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_waltz_bgm.py outdir     writes them somewhere else

A minor, 3/4, 150 BPM (10 ticks a second, a beat is 4 ticks, so a bar is 12 ticks; the intro is 4 ticks of silence and 5 bars so that the loop point is tick 64 as in every song), 96 bars (2:01); intro 5 /
A 24 / A' 24 / B 16 / A'' 16 (the peak, from bar 69 of 101 = 68 %) / calm 16, loop from tick 64. Every section is a multiple of four bars, so that the game's crossfade at the next 16-tick line meets a bar line
every fourth bar. The theme is the world theme as a waltz step (A4 C5 E5 D5: a rising triad, a turn): the first beat is the heavy one (the bass and the bass drum), beats two and three are light. The device is the STUMBLE:
every eighth bar the tune comes as two long beats (E5 C5 with three ticks of..., 6+6), the waltz losing its feet (a hemiola, the guide's "polymiddle"). Phase 1 (natural): harp, flute, bass, bass drum and snare on 2 and 3.
Phase 2 (a half step up, B-flat minor): the same bars and chords, the bass drum on every beat, the 8-bit chip, the hat, the tune in unison. Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.

FASTER (t269): written as above and PLAYED at 200 BPM - 20 ticks a second, a beat of 6 ticks, a bar of 18 - with the sections lengthened to 32 / 32 / 24 / 24 / 24 bars (136 bars, 2:06, every section a multiple of 8
bars so that the 16-tick line of the phase switch meets a bar line every eighth bar) (bossframe.widen).
"""
import sys
from pathlib import Path

from bossframe import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TONIC, SCALE = 9, [0, 2, 3, 5, 7, 8, 10]
K = key_pcs(TONIC, SCALE)
R = "r:12"

T = "A4:4 C5:4 E5:2 D5:2"
U = "E5:6 C5:6"                                   # the stumble: two long beats
m1 = seq_block(T, U, K, [0, "C5:4 B4:4 A4:4", -2, "A4:4 C5:4 F4:4", -2, ("u", -2), "G#4:4 B4:4 E5:2 D5:2", U])
m2 = seq_block(T, U, K, [0, "E5:4 D5:4 C5:4", -2, "C5:4 A4:4 F4:4", -2, "A4:4 C5:4 F5:4", "G#4:4 B4:4 E5:4", "A4:12"])
m3 = seq_block(T, U, K, [0, "E5:4 F5:4 E5:4", -2, "F5:4 E5:4 D5:4", -1, "F5:4 D5:4 A4:4", "G#4:4 B4:4 E5:4", "E5:6 A4:6"])
bq = seq_block(T, U, K, [-2, R, -4, R, -2, R, "G#4:4 B4:4 E5:4", R])
bp = seq_block(T, U, K, [-2, "F4:4 A4:4 C5:4", -4, "D4:4 F4:4 A4:4", -2, ("u", -2), "G#4:4 B4:4 E5:4", "E5:12"])
pk = seq_block(T, U, K, [0, "C5:4 E5:4 A4:4", 0, "C5:4 E5:4 A5:4".replace("A5", "A4"), -2, "A4:4 C5:4 F5:4", -2, "F5:4 E5:4 D5:4"])
pk2 = seq_block(T, U, K, [0, "C5:4 E5:4 A4:4", -2, "A4:4 C5:4 F5:4", -2, ("u", -2), "G#4:4 B4:4 E5:4", "A4:12"])
cm = ["E5:4 C5:4 A4:4", R, "D5:4 B4:4 G4:4", R, "C5:4 A4:4 F4:4", R, "B4:4 G#4:4 E4:4", R, "A4:4 C5:4 E5:4", R, "F4:4 A4:4 C5:4", R, "E5:6 C5:6", R, "G#4:4 B4:4 E5:4", "E4:2 G#4:2 B4:2 D5:2 E5:4"]


def chords_of(pattern, bars):
    return (pattern * 10)[:bars]


WALTZ8 = "Am Am Dm Dm F F E E".split()

SPEC = dict(
    bar=12, tps=10.0, faster=True, tonic=TONIC, scale=SCALE, shift=[0, 1],
    sections=[("A", 24), ("A2", 24), ("B", 16), ("PEAK", 16), ("CALM", 16)],
    chords={
        "intro": ["Am", "Am", "Dm", "F", "E"],
        "A": chords_of(WALTZ8, 24),
        "A2": chords_of(WALTZ8, 24),
        "B": chords_of("F F Dm Dm Bb Bb E E".split(), 16),
        "PEAK": [c for c in ("Am|F", "Am|F", "Dm|E", "Dm|E", "Am|F", "Am|F", "Dm|E", "A|A") for _ in (0, 1)],
        "CALM": "Dm Dm F F G G Dm Dm Bb Bb F F E E E E".split(),
    },
    melody={"intro": None, "A": m1 + m2 + m1, "A2": m1 + m3 + m2, "B": bq + bp, "PEAK": pk + pk2, "CALM": cm},
    pedal={"PEAK": "Am"},
    level={"intro": 0.8, "A": 0.9, "A2": 1.0, "B": 0.85, "PEAK": 1.1, "CALM": 0.75},
    arp_low=54, arp_high=72, stab_low=54, stab_high=68,
    run=[57, 59, 61, 63],
    voices=[heavy_voices(0, bass_all="waltz", drum_map={"stomp": "waltz", "half": "waltz", "gallop": "waltz2", "double": "waltz2", "heart": "waltz"}), heavy_voices(1, bass_all="waltz", drum_map={"stomp": "waltz2", "half": "waltz2", "gallop": "waltz2", "double": "waltz2", "heart": "waltz"})],
    heavy=True,
)

TITLES = ("Waltz at the Edge of Dark (Boss008)", "Waltz at the Edge of Dark, Out of Step (Boss008)")
FILES = ("waltz_phase1.nbs", "waltz_phase2.nbs")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [out / f for f in FILES],
                 ["Waltz boss, phase 1 (natural sounds): 3/4, A minor, the world theme as a waltz step, a stumble every eighth bar. 96 bars, loops from tick 64. Same bars as phase 2.",
                  "Waltz boss, phase 2 (electronic, a half step up): the same bars and chords, the bass drum on every beat, the 8-bit chip, the hat, the unison tune."],
                 TITLES)


if __name__ == "__main__":
    main()
