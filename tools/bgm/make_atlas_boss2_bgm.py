#!/usr/bin/env python3
"""Makes atlas_vindicator_2.nbs: "Raise the Axe (Boss002)", the boss of the Atlas cave in a low, heavy dress (one song, no second phase), written again by the BGM制作ガイド.

    python3 tools/bgm/make_atlas_boss2_bgm.py            writes src/main/resources/bgm/atlas_vindicator_2.nbs
    python3 tools/bgm/make_atlas_boss2_bgm.py out.nbs    writes somewhere else

E minor, 4/4, 150 BPM (10 ticks a second, a tick is a sixteenth), 80 bars (2:08), loop from bar 5. The form of the guide: intro 4 / A 16 / A' 16 / B 16 (takes away) / A'' 16 (the peak, from bar 52 = 65 %) /
calm 12. The theme is the motif of Atlas (E5 B4 G4 E4: the falling tonic triad, the world theme of the plains turned upside down). The chords change every bar (twice a bar in the peak, over an E in the bass), the
progression is i - bVI - bVII - V with the Phrygian F in B. The one device: the axe gets heavier - a bass drum heartbeat (every second beat) from the start; the peak is the one place that turns BRIGHT
(the electric piano in thirds, the iron xylophone), and the last chord of the peak is E major (the guide's Picardy ending). The sounds: piano (harp) and bass for the base, flute an octave up, electric piano (pling), guitar.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.

FASTER (t269, nekorozv: "everything is too slow, a little faster"): the song is written as above (80 bars of 16 ticks, 150 BPM) and PLAYED at 200 BPM - 20 ticks a second, a beat of 6 ticks, a bar of 24 -
with the sections lengthened to 24 / 24 / 24 / 20 / 8 bars (100 bars, 2:03) so that it still lasts two minutes (bossframe.widen). The only clean step up from 150 is 200: the tempo is a whole number of ticks to the beat.
"""
import sys
from pathlib import Path

from bossframe import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm/atlas_vindicator_2.nbs"
TONIC, SCALE = 4, [0, 2, 3, 5, 7, 8, 10]
K = key_pcs(TONIC, SCALE)

T = "E5:6 B4:2 G4:4 E4:4"                       # the motif of Atlas
U = "G4:4 B4:4 E5:8"
a1 = seq_block(T, U, K, [0, ("u", 0), -2, ("u", -2), -1, ("u", -1), "B4:6 D#5:2 F#5:4 B4:4", "F#5:4 D#5:4 B4:8"])
a2 = seq_block(T, U, K, [0, "G4:2 A4:2 B4:4 E5:8", -2, "E5:4 D5:4 C5:8", -4, "B4:6 D#5:2 F#5:4 B4:4", "E5:8 B4:4 G4:4", "E4:8 r:8"])
a3 = seq_block(T, U, K, [0, "B4:4 G4:4 E5:8", -2, "G5:4 E5:4 C5:8".replace("G5", "G4"), -1, "A4:4 D5:4 F#5:8", "B4:6 D#5:2 F#5:4 B4:4", "F#5:8 B4:8"])
b1 = seq_block(T, U, K, [-2, ("u", -2), -4, ("u", -4), -1, ("u", -1), -2, "G4:4 B4:4 C5:8"])
b2 = seq_block(T, U, K, [-2, ("u", -2), -4, ("u", -4), "F5:6 C5:2 A4:4 F4:4", "A4:4 C5:4 F5:8", "B4:6 D#5:2 F#5:4 B4:4", "F#5:8 r:8"])
p1 = seq_block(T, U, K, [0, "G4:2 B4:2 E5:4 G4:8", 0, "G4:2 B4:2 E5:4 B4:8", -2, "C5:2 E5:2 G4:4 C5:8", -1, "D5:2 F#4:2 A4:4 D5:8"])
p2 = seq_block(T, U, K, [0, "G4:2 B4:2 E5:4 G4:8", 0, "G4:2 B4:2 E5:4 B4:8", -2, "C5:2 E5:2 G4:4 C5:8", "B4:6 D#5:2 F#5:4 B4:4", "E5:16"])
c1 = ["E5:6 B4:2 G4:8", "r:16", "C5:6 G4:2 E4:8", "r:16", "D5:6 A4:2 F#4:8", "r:16", "A4:6 E4:2 C4:8", "r:16", "G4:6 D4:2 B3:8", "r:16", "B4:6 D#5:2 F#5:4 B4:4", "B3:4 E4:4 G4:4 B4:4"]

SPEC = dict(
    bar=16, tps=10.0, faster=True, tonic=TONIC, scale=SCALE, shift=[0],
    sections=[("A", 16), ("A2", 16), ("B", 16), ("PEAK", 16), ("CALM", 12)],
    chords={
        "intro": ["Em", "Em", "C", "B"],
        "A": "Em Em C C D D B B Em Em C C Am B Em Em".split(),
        "A2": "Em Em C C D D B B Em Em C C D D B B".split(),
        "B": "C C Am Am D D B B C C Am Am F F B B".split(),
        "PEAK": ["Em|C", "Em|C", "Em|D", "Em|D", "C|G", "C|G", "B|B", "B|B", "Em|C", "Em|C", "Em|D", "Em|D", "C|D", "C|D", "B|B", "E"],
        "CALM": "Am Am C C D D Am Am G G B B".split(),
    },
    melody={"intro": None, "A": a1 + a2, "A2": a1 + a3, "B": b1 + b2, "PEAK": p1 + p2, "CALM": c1},
    pedal={"PEAK": "Em"},
    level={"intro": 0.8, "A": 0.9, "A2": 1.0, "B": 0.85, "PEAK": 1.1, "CALM": 0.75},
    arp_low=54, arp_high=72, stab_low=54, stab_high=68,
    run=[57, 59, 61, 63],
    voices=[heavy_voices(0, bass=("half", "walk", "eighth"))],
    heavy=True,
)

TITLE = "Raise the Axe (Boss002)"


def main():
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    target.parent.mkdir(parents=True, exist_ok=True)
    write_phases(SPEC, [target], ["Boss song of the Atlas cave in a low, heavy dress: the motif of Atlas in E minor, a heartbeat of the bass drum, a bright peak ending on E major. 80 bars, loops from bar 5."], [TITLE])


if __name__ == "__main__":
    main()
