#!/usr/bin/env python3
"""Makes colossus_phase1.nbs and colossus_phase2.nbs: "Shoulders of Stone (Boss003)" and "Shoulders of Stone, Unbound (Boss003)", the stone giant in two phases. (BGM t290: written from zero.)

    python3 tools/bgm/make_atlas_boss3_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss3_bgm.py outdir     writes them somewhere else

Class 1 (majesty and despair), STYLE 2 of the structure guide ("despair and sacred, many stages", 120-130 BPM): 125 BPM (8.333 ticks a second, a tick is a sixteenth, a bar is 16 ticks), D PHRYGIAN.
THE MOTIF is the giant's double stomp: D - D - A - Eb ("two steps, the leap of a fifth, the flat ninth that does not resolve"), a two-bar cell in low brass that is repeated one scale step higher each time
and always ends held and leaning up. 110 bars = 211 s = 3:31. What the two guides ask for, where it is (a bar is 1.92 s):
  P1 0:00  bars  0-13  irregular start, dissonance: hit + the stomp in brass; the bass is a QUARTER pedal (the BPM ladder starts here), then cells whose heads fall on 5+5+3+3, 3+3+5+5, 6+4+6; clusters D-Eb-A-Bb (seconds, tritones)
  P2 0:27  bars 14-39  eighths (BPM ladder step 2) and PSEUDO DOUBLE TIME: the snare on 2 and 4, then on every beat; the bass is freed from its root (gallop, triplets, walk); the stomp climbs through 3 blocks
  P3 1:17  bars 40-63  THE MELODY IS LOST: bass + cow-bell anvil + xylophone bone + hat/rim only (no tune, no pad); bar 63 = BREAK: no drums, one bell, the bass pedal stays
  P4 2:03  bars 64-85  SACRED, D MAJOR (the mode flip): long organ chords (pling x3 + flutes + 8-bit, all re-struck every 3 ticks), a slow chorale, kick on the beat only; bars 80-84 the tune goes and a roll rises
                       over a bass crescendo (4th -> 8th -> 16th); bar 85 = BREAK, one bell, bass pedal
  P5 2:45  bars 86-109 CLIMAX in D minor: the sixteenth bass (BPM ladder step 4) + the snare on every beat + the sacred chords + the stomp in WHOLE notes, the highest flute note struck without end
The bass ladder: quarters (P1 intro) / eighths (P1 loop, P2) / eighths with doubles and triplets (P2, P3) / a sixteenth build-up (P4 end) / the 16th machine gun under the long notes (P5).
Phase 2 "Unbound": a half step up, every stage earlier and thicker (doubles from P1, 8-bit chip in the brass, sixteenth bass in P3, root-fifth bass in P5). Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from cat1_organ import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 125 / 15
SECTIONS = [("P1", 14), ("P2", 26), ("P3", 24), ("P4", 22), ("P5", 24)]
TITLES = ("Shoulders of Stone (Boss003)", "Shoulders of Stone, Unbound (Boss003)")
FILES = ("colossus_phase1.nbs", "colossus_phase2.nbs")
DESCRIPTIONS = (
    "Stone giant, phase 1: class 1, style 2. 125 BPM, D Phrygian: the double-stomp motif D-D-A-Eb in low brass, irregular start, pseudo double time, a melody-less part with anvil and bone, a sacred D major chorale, a climax that fuses it all. Same bars as phase 2.",
    "Stone giant, phase 2: the same bars a half step up, every stage earlier and thicker (doubles from the start, chip in the brass, sixteenth bass in the melody-less part, root-fifth bass in the climax).")

VOL = {"bass": 100, "bass2": 90, "didge": 90, "gtr": 100, "g5": 100, "hit": 100, "bit_m": 100, "pl_m": 100, "fl_m": 90, "org": 60, "cb": 50, "cf": 50, "cap": 85,
       "cow": 90, "xy": 100, "bell": 60}
PAN = {"org1": 90, "org2": 110, "cb1": 90, "cb2": 110, "cf1": 95, "cf2": 105, "hit1": 90, "hit2": 110, "cow": 90, "xy": 110, "hit3": 100}

# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- bass cells
CELLS = {
    # P1: the heads of the count. A = 5+5+3+3, B = 3+3+5+5, C = 6+4+6 (a half step, a fifth, a tritone around the root)
    "A": [(0, 0, 98), (2, 0, 56), (5, 0, 90), (7, 1, 60), (10, 0, 88), (13, 6, 84), (15, 1, 52)],
    "B": [(0, 0, 98), (3, 1, 84), (5, 0, 56), (6, 0, 90), (8, 7, 60), (11, 0, 88), (14, 6, 70)],
    "C": [(0, 0, 98), (2, 0, 56), (4, 1, 60), (6, 0, 90), (9, 7, 58), (10, 0, 88), (13, 6, 80), (15, 1, 52)],
    # P2: plain eighths (the bass pinned), the gallop, triplets, a walk, a fill
    "E2": [(t, 0, 98 if t == 0 else 82 if t == 8 else 70 if t in (4, 12) else 56) for t in range(0, 16, 2)],
    "DG": [(0, 0, 98), (3, 0, 60), (4, 0, 84), (7, 0, 60), (8, 0, 94), (11, 0, 60), (12, 0, 84), (15, 7, 66)],
    "DGS": [(0, 0, 98), (3, 0, 60), (4, 0, 84), (7, 0, 60), (8, 0, 94), (11, 0, 60), (12, 0, 84), (15, 7, 66), (1, 0, 50), (9, 0, 50), (14, 0, 54)],
    "TR": [(0, 0, 98), (1, 0, 50), (3, 0, 62), (4, 0, 80), (5, 0, 48), (7, 7, 66), (8, 0, 94), (9, 0, 50), (11, 0, 62), (12, 0, 80), (13, 1, 52), (15, 7, 66)],
    "WK": [(0, 0, 98), (2, 0, 60), (4, 1, 82), (6, 0, 64), (8, 7, 92), (10, 5, 68), (12, 3, 82), (14, 1, 72), (1, 0, 44), (9, 7, 46)],
    "F16": [(0, 0, 98), (3, 0, 60), (4, 0, 84), (7, 0, 60)] + [(8 + k, o, 62 + 4 * k) for k, o in enumerate((0, 0, 1, 0, 7, 5, 3, 1))],
    # P4 / P5
    "QQ": [(0, 0, 98), (4, 0, 66), (8, 0, 86), (12, 0, 66)],
    "M16": [(k, 12 if k in (3, 11) else 7 if k in (7, 15) else 0, 98 if k == 0 else 90 if k in (5, 8, 13) else 74 if k % 4 == 0 else 60 if k in (3, 7, 11, 15) else 46) for k in range(16) if k not in (2, 10)],
}
CELLS["Ax"] = CELLS["A"] + [(t, 0, 46) for t in (1, 8, 11, 14) if t not in {c[0] for c in CELLS["A"]}]
CELLS["Bx"] = CELLS["B"] + [(t, 0, 46) for t in (1, 9, 13) if t not in {c[0] for c in CELLS["B"]}]
CELLS["Cx"] = CELLS["C"] + [(t, 0, 46) for t in (1, 5, 8, 12) if t not in {c[0] for c in CELLS["C"]}]
FIGURES.update({"c3_" + k: v for k, v in CELLS.items()})


def seg(cell):
    return "c3_" + cell if cell else None


# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- the motif
SC = [n for n in range(36, 100) if n % 12 in (2, 3, 5, 7, 9, 10, 0)]          # D Phrygian


def deg(d):
    return SC[SC.index(50) + d]            # step d from D3


def stomp_bars(s, variant):
    """The two-bar cell from scale step s: D - D - A | Bb - A - C (the stomp, the leap of a fifth, the answer that leans up)."""
    a, c, d, e, f = (note_name(deg(s + k)) for k in (0, 4, 5, 4, 6))
    bar1 = {0: f"{a}:4 {a}:4 {c}:8", 1: f"{a}:2 {a}:2 {a}:4 {c}:8", 2: f"{a}:4 {c}:4 {c}:8"}[variant]
    return [bar1, f"{d}:6 {e}:2 {f}:8"]


P2_MEL = []
for variant in (0, 1, 2):
    for s0 in (0, 1, 2, 3):
        P2_MEL += stomp_bars(s0, variant)
P2_MEL += [f"{note_name(deg(7))}:8 {note_name(deg(8))}:8", f"{note_name(deg(9))}:16"]
P2_CH = ("Dm Dm Eb Eb Dm Dm C C  Gm Gm Eb Eb Dm Dm A A  Dm Dm Eb Eb Gm Gm A A  Dm A").split()
CH_ROOT = {"Dm": 38, "Eb": 39, "C": 36, "Gm": 31, "A": 33, "Bb": 34, "F": 41}

DISS = [{2, 3, 9, 10}, {2, 8, 5, 11}, {3, 9, 0, 6}, {2, 3, 7, 8}]
P1_ROOTS = [38, 38, 38, 33] + [38, 38, 39, 39, 41, 41, 31, 31, 33, 33]
P1_CELL = ["QQ", "QQ", "QQ", "QQ"] + ["A", "B", "C", "A", "B", "C", "A", "B", "C", "A"]
P1_MEL = [None, None, "D3:4 D3:4 A3:8", "Bb3:6 A3:2 C4:8"] + [
    "D3:5 D3:5 A3:3 Bb3:3", "C4:5 r:3 Bb3:3 C4:5", "Eb3:5 Eb3:5 Bb3:3 C4:3", "D4:5 r:3 C4:3 D4:5", "F3:5 F3:5 C4:3 D4:3",
    "Eb4:5 r:3 D4:3 Eb4:5", "G3:5 G3:5 D4:3 Eb4:3", "F4:5 r:3 Eb4:3 F4:5", "A3:5 A3:5 Eb4:3 F4:3", "F4:5 r:3 Eb4:3 F4:5"]

P3_ROOTS = [38, 39, 38, 31, 33, 34, 33, 41, 38, 36, 39, 38, 31, 33, 36, 34, 38, 39, 33, 31, 38, 39, 33]
P3_CELL = {0: ["DGS", "TR", "WK", "TR", "DGS", "WK", "TR", "F16", "TR", "WK", "TR", "WK", "F16", "TR", "WK", "DGS", "TR", "WK", "TR", "F16", "TR", "WK", "M16", None],
           1: ["DGS", "TR", "WK", "TR", "TR", "WK", "TR", "F16", "TR", "WK", "TR", "WK", "F16", "TR", "M16", "TR", "WK", "M16", "TR", "F16", "M16", "M16", "M16", None]}
COW = [66, 70, 73]
XY = [91, 94, 97, 92, 96, 98]
STAC = [   # (tick, kind, velocity, step)
    [(0, "cow", 90, 0), (4, "cow", 72, 1), (7, "cow", 84, 0), (11, "cow", 70, 1), (2, "xy", 84, 1), (9, "xy", 88, 2), (14, "xy", 86, 0), (12, "rim", 62, 0), (5, "hat", 30, 0)],
    [(0, "cow", 90, 1), (3, "cow", 74, 0), (7, "cow", 84, 2), (10, "xy", 88, 3), (13, "xy", 84, 4), (5, "xy", 86, 0), (15, "rim", 60, 0), (8, "hat", 30, 0)],
    [(0, "cow", 90, 2), (5, "cow", 72, 1), (8, "cow", 82, 0), (12, "cow", 76, 2), (2, "xy", 86, 5), (10, "xy", 88, 2), (14, "xy", 84, 3), (4, "rim", 60, 0), (11, "hat", 28, 0)]]
STAC2 = [[(t, "hat", 24, 0) for t in (1, 3, 6, 9, 13)], [(t, "hat", 24, 0) for t in (2, 4, 6, 11, 14)], [(t, "hat", 24, 0) for t in (1, 3, 7, 9, 15)]]

P4_CH = ("D A Bm G D G A A  Bm G D A Bm G A A  G D A A A").split()
P4_ROOTS = {"D": 38, "A": 33, "Bm": 35, "G": 31}
P4_MEL = ["F#4:8 A4:8", "E4:8 A4:8", "D4:8 F#4:8", "D4:16", "A4:8 D5:8", "B4:8 D5:8", "C#5:8 E5:8", "E5:16",
          "F#4:8 B4:8", "G4:8 B4:8", "F#4:8 A4:8", "E4:8 A4:8", "D5:8 F#5:8", "B4:8 D5:8", "C#5:8 E5:8", "E5:8 A5:8"]
P4_CELL = {0: ["QQ"] * 8 + ["E2"] * 6 + ["DGS", "DGS"] + ["F16", "M16", "M16", "M16", "M16", None],
           1: ["QQ"] * 4 + ["E2"] * 8 + ["DGS"] * 4 + ["F16", "M16", "M16", "M16", "M16", None]}

P5_CH = ("Dm Bb C A  Dm Eb C A  Gm Eb C A  Dm Bb Eb A  Gm Eb C A  Dm Eb A A").split()
P5_ROOT = {"Dm": 38, "Bb": 34, "C": 36, "A": 33, "Eb": 39, "Gm": 31}
P5_MEL = ["D4:16", "F4:16", "G4:16", "A4:16", "F4:16", "G4:16", "A4:16", "Bb4:16",
          "Bb4:16", "G4:8 Bb4:8", "A4:8 G4:8", "A4:16", "D4:8 A4:8", "F4:8 Bb4:8", "G4:8 Bb4:8", "A4:16",
          "Bb4:16", "G4:16", "G4:8 A4:8", "Bb4:16", "A4:16", "Bb4:16", "A4:8 Bb4:8", "Bb4:16"]
P5_CELL = ["M16"] * 22 + ["E2", "QQ"]


def hats(ticks, loud, soft):
    return [(t, "hat", loud if t % 2 == 0 else soft) for t in ticks]


DRUMS = {
    "i0": [(0, "bd", 100)] + [(t, "hat", 38) for t in (3, 6, 9, 12, 15)], "i1": [(0, "bd", 96), (8, "bd", 70)] + [(t, "hat", 40) for t in (3, 6, 11, 14)],
    "i2": [(0, "bd", 100), (8, "bd", 80), (12, "sn", 66)] + [(t, "hat", 40) for t in (3, 6, 10, 14)],
    # P1: the heads of the count (light: the double time of P2 must be a jump)
    "A": [(0, "bd", 100), (5, "bd", 84), (10, "sn", 70)] + [(t, "hat", 30) for t in (3, 7, 13)],
    "B": [(0, "bd", 100), (6, "bd", 84), (11, "sn", 72)] + [(t, "hat", 30) for t in (3, 8, 14)],
    "C": [(0, "bd", 100), (6, "sn", 66), (10, "bd", 88)] + [(t, "hat", 30) for t in (3, 8, 13)],
    "Ax": [(0, "bd", 100), (5, "bd", 90), (10, "sn", 76), (13, "sn", 54)] + [(t, "hat", 30) for t in (3, 7)],
    "Bx": [(0, "bd", 100), (6, "bd", 90), (11, "sn", 76), (3, "sn", 54)] + [(t, "hat", 30) for t in (8, 14)],
    "Cx": [(0, "bd", 100), (6, "sn", 72), (10, "bd", 92), (13, "sn", 54)] + [(t, "hat", 30) for t in (3, 8)],
    # P2: the snare on 2 and 4, then on every beat
    "back": [(0, "bd", 100), (6, "bd", 70), (10, "bd", 90), (4, "sn", 90), (12, "sn", 96)],
    "dbl": [(0, "bd", 100), (6, "bd", 76), (10, "bd", 92), (14, "bd", 60), (0, "sn", 56), (4, "sn", 92), (8, "sn", 62), (12, "sn", 98)] + [(t, "hat", 26) for t in (2, 6, 10, 14)],
    "u_dbl": [(0, "bd", 100), (3, "bd", 60), (6, "bd", 84), (10, "bd", 96), (14, "bd", 66), (0, "sn", 62), (4, "sn", 98), (8, "sn", 70), (12, "sn", 100)] + [(t, "hat", 28) for t in (2, 6, 10, 14)],
    # P4: half time, the kick on the beat
    "sac0": [(0, "bd", 88), (4, "bd", 56), (8, "bd", 78), (12, "bd", 56)],
    "sac1": [(0, "bd", 94), (4, "bd", 62), (8, "bd", 86), (12, "bd", 62), (12, "sn", 62)],
    "sac2": [(0, "bd", 98), (4, "bd", 72), (8, "bd", 92), (12, "bd", 72), (12, "sn", 70)],
    "build": [(0, "bd", 98), (4, "bd", 82), (8, "bd", 98), (12, "bd", 82), (6, "sn", 60), (14, "sn", 72)],
    # P5: the fast rhythm with sixteenth bass
    "fuse": [(0, "bd", 100), (4, "bd", 86), (8, "bd", 98), (12, "bd", 86), (14, "bd", 58), (0, "sn", 62), (4, "sn", 96), (8, "sn", 66), (12, "sn", 100)],
    "u_fuse": [(0, "bd", 100), (4, "bd", 92), (8, "bd", 100), (12, "bd", 92), (6, "bd", 60), (14, "bd", 66), (0, "sn", 72), (4, "sn", 100), (8, "sn", 76), (12, "sn", 100)],
    "end": [(0, "bd", 90), (8, "bd", 80)],
}


def pad_vel(lo, hi, i, tick, block=4):
    return lerp(lo, hi, ((i % block) * BAR + tick) / (block * BAR))


def low_brass(root):
    low = root
    while low < 43:
        low += 12
    return low


def hit_notes(root, snare=True):
    low = low_brass(root)
    out = [("hit1", GUITAR, low, 0, 100), ("hit2", GUITAR, low + 7, 0, 100), ("bass", BASS, root, 0, 100)]
    return out + ([("sn", SNARE, 66, 0, 90)] if snare else [])


def bars_of(phase):
    bars = []
    st = {}
    p2 = phase == 1
    # ------------------------------------------------------------------ P1
    G0 = 0
    for i in range(14):
        b = {"shift": phase}
        pcs = DISS[i % 4]
        cell = P1_CELL[i] + ("x" if p2 and P1_CELL[i] in ("A", "B", "C") else "")
        b["bass"] = {"segs": [(P1_ROOTS[i], 3)], "fig": seg(cell), "gain": [0.9, 0.85, 0.9, 1.0][i] if i < 4 else 1.0}
        b["didge"] = 1.0
        b["drums"] = ["i0", "i1", "i2", "i2"][i] if i < 4 else cell
        if i == 0:
            b["first"] = hit_notes(38)
            b["didge_skip0"] = True
        notes = []
        if i >= 2:
            g = [0, 0, 0.6, 0.85][i] if i < 4 else 0.9
            notes += pad_notes(st, "org", pcs, 55, 68, 3, ticks6(G0 + i, 2), "org", PLING, lambda t, g=g, i=i: pad_vel(40, 62, i, t) * g)
        if i >= 3:
            notes += pad_notes(st, "cho", pcs, 79, 90, 2, ticks6(G0 + i, 5), "cf", FLUTE, lambda t, i=i: pad_vel(22, 60, i, t) * (0.7 if i < 4 else 0.9))
        b["notes"] = notes
        if P1_MEL[i]:
            voices = {"gtr": (GUITAR, 0, 1.0)}
            if p2:
                voices["bit_m"] = (BIT, 12, 1.0)
            b["holds"] = hold_events(P1_MEL[i], voices)
            for tick, note, length in melody_events(P1_MEL[i]):
                if 42 <= note + 7 + phase <= 66:
                    b["holds"].append(("g5", GUITAR, note + 7, tick, length, 80, 58, 3))
        if i % 4 == 3:
            b["roll"] = (8, 8, 30, 80)
        if i >= 4 and i % 2 == 0:
            b["first"] = hit_notes(P1_ROOTS[i], False) + [("bd", BASEDRUM, 66, 0, 100)]
            b["didge_skip0"] = True
        bars.append(b)
    # ------------------------------------------------------------------ P2: eighths and the pseudo double time
    G0 = 14
    plan = {0: ["E2"] * 6 + ["WK", "TR", "WK", "DGS", "WK", "TR", "F16", "DG", "WK", "TR", "DGS", "F16", "TR", "DGS", "WK", "TR", "TR", "WK", "DGS", "F16", "TR", "DGS"],
            1: ["E2", "DGS", "E2", "DG", "E2", "DGS", "E2", "TR", "WK", "TR", "TR", "DGS", "WK", "TR", "TR", "F16", "TR", "TR", "WK", "TR", "TR", "WK", "TR", "F16", "TR", "DGS"]}[phase]
    for i in range(26):
        b = {"shift": phase}
        name = P2_CH[i]
        root = CH_ROOT[name] if i >= 6 else 38           # the first bars: the bass is PINNED on D (released at bar 6)
        b["bass"] = {"segs": [(root, 3)], "fig": seg(plan[i])}
        b["didge"] = 1.0
        b["drums"] = ("back" if i < 2 else "dbl") if not p2 else ("back" if i < 1 else "u_dbl")
        pcs = pcs_of(name)
        notes = []
        if i >= 4:
            notes += pad_notes(st, "org", pcs, 55, 68, 3, ticks6(G0 + i, 2), "org", PLING, lambda t, i=i: pad_vel(40, 60, i, t))
            notes += pad_notes(st, "cho", pcs, 79, 90, 2, ticks6(G0 + i, 5), "cf", FLUTE, lambda t, i=i: pad_vel(22, 62, i, t))
        b["notes"] = notes
        text = P2_MEL[i]
        voices = {"gtr": (GUITAR, 0, 1.0)}
        if i >= 16:
            voices["pl_m"] = (PLING, 12, 1.0)           # the tune jumps an octave up for the third block
        if p2 and i >= 4:
            voices["bit_m"] = (BIT, 12, 1.0)
        b["holds"] = hold_events(text, voices)
        if i % 2 == 0:
            b["first"] = hit_notes(root)
            b["didge_skip0"] = True
        if i >= 8:
            low = low_brass(root)
            for t in ((4, 12) if i >= 12 else (8,)):
                b["notes"] += [("hit1", GUITAR, low, t, 94), ("hit2", GUITAR, low + 7, t, 94)]
        if i % 4 == 3:
            b["roll"] = (8, 8, 34, 92)
        bars.append(b)
    # ------------------------------------------------------------------ P3: the melody is lost (bass + staccato only)
    G0 = 40
    for i in range(24):
        b = {"shift": phase}
        if i == 23:
            b["pedal"] = (33, 66)
            b["first"] = [("bell", BELL, 93, 0, 55, False)]
            bars.append(b)
            continue
        root = P3_ROOTS[i]
        b["bass"] = {"segs": [(root, 3)], "fig": seg(P3_CELL[phase][i])}
        b["didge"] = 1.0
        notes, extra = [], []
        for tick, kind, vel, step in (STAC[i % 3] + (STAC2[i % 3] if p2 or i >= 12 else [])):
            if kind == "cow":
                notes.append(("cow", COW_BELL, COW[step % 3], tick, vel))
                extra.append((tick, "bd", 76 if tick else 100))
            elif kind == "xy":
                notes.append(("xy", XYLOPHONE, XY[step % 6], tick, vel))
            else:
                extra.append((tick, kind, vel))
        # an ensemble hit on every bar without a tune voice: bass, didgeridoo, kick, snare, anvil, bone
        extra.append((0, "sn", 90))
        notes.append(("xy", XYLOPHONE, 89 + 2 * (i % 4), 0, 90))
        if i % 2 == 0:
            notes.append(("xy", XYLOPHONE, 95, 7, 90))
            notes.append(("cow", COW_BELL, 70, 7, 84))
            extra.append((7, "sn", 84))
        b["notes"] = notes
        b["extra_drums"] = extra
        bars.append(b)
    # ------------------------------------------------------------------ P4: sacred, D major
    G0 = 64
    for i in range(22):
        b = {"shift": phase}
        if i == 21:
            b["pedal"] = (38, 66)
            b["first"] = [("bell", BELL, 93, 0, 55, False)]
            bars.append(b)
            continue
        name = P4_CH[i]
        root = P4_ROOTS[name]
        gain = {17: 0.7, 18: 0.8, 19: 0.9, 20: 1.0}.get(i, 1.0)
        b["bass"] = {"segs": [(root, 3)], "fig": seg(P4_CELL[phase][i]), "gain": gain}
        b["didge"] = 0.9
        b["drums"] = ("sac0" if i < 6 else "sac1" if i < 12 else "sac2" if i < 16 else "build") if not p2 else ("sac1" if i < 8 else "sac2" if i < 16 else "build")
        if i % 4 == 0 and i < 16:
            b["first"] = hit_notes(root)
            b["didge_skip0"] = True
        pcs = pcs_of(name)
        notes = pad_notes(st, "sorg", pcs, 55, 68, 3, ticks6(G0 + i, 2), "org", PLING, lambda t, i=i: pad_vel(70, 92, i, t))
        notes += pad_notes(st, "scho", pcs, 79, 90, 3, ticks6(G0 + i, 5), "cf", FLUTE, lambda t, i=i: pad_vel(60, 90, i, t))
        notes += pad_notes(st, "sbit", pcs, 62, 74, 1, ticks6(G0 + i, 5), "cb", BIT, lambda t, i=i: pad_vel(50, 80, i, t))
        b["notes"] = notes
        if i < 16:
            voices = {"pl_m": (PLING, -12, 1.0), "bit_m": (BIT, -12, 1.0), "gtr": (GUITAR, -12, 1.0)}
            b["holds"] = hold_events(P4_MEL[i], voices, swell=1.12)
        else:
            b["roll"] = (4, 12, 30 + 12 * (i - 16), 66 + 8 * (i - 16))
        bars.append(b)
    # ------------------------------------------------------------------ P5: the fusion
    G0 = 86
    for i in range(24):
        b = {"shift": phase}
        name = P5_CH[i]
        root = P5_ROOT[name]
        segs = [(root, 3)]
        if p2 and i < 22:
            fifth = root - 5 if root - 5 >= 30 else root + 7
            segs = [(root, 3), (fifth, 3)]
        b["bass"] = {"segs": segs, "fig": seg(P5_CELL[i]), "pop": p2}
        b["didge"] = 1.0 if i < 22 else 0.7
        b["drums"] = ("u_fuse" if p2 else "fuse") if i < 22 else "end"
        pcs = pcs_of(name)
        notes = []
        if i < 23:
            notes += pad_notes(st, "org", pcs, 55, 68, 3, ticks6(G0 + i, 2), "org", PLING, lambda t, i=i: pad_vel(70, 92, i, t))
            notes += pad_notes(st, "cho", pcs, 79, 90, 3, ticks6(G0 + i, 5), "cf", FLUTE, lambda t, i=i: pad_vel(60, 90, i, t))
        b["notes"] = notes
        voices = {"pl_m": (PLING, 0, 1.0), "gtr": (GUITAR, -12, 1.0)}
        b["holds"] = hold_events(P5_MEL[i], voices, swell=1.12)
        if i % 4 == 0 and i < 22:
            b["first"] = hit_notes(root)
            b["didge_skip0"] = True
        if i % 4 == 3 and i < 22:
            b["roll"] = (10, 6, 40, 92)
        if i == 23:
            b["roll"] = (8, 8, 30, 88)
        bars.append(b)
    return bars


def build(phase):
    sc = Score(TPS, SECTIONS, shift=0)
    bars = bars_of(phase)
    assert len(bars) == sc.bars, (len(bars), sc.bars)
    drums = dict(DRUMS)
    for g, b in enumerate(bars):
        if b.get("extra_drums"):
            drums[f"x{g}"] = b["extra_drums"]
            b["drums"] = f"x{g}"
    # the pads are moved out of the way of a hit; the top of the chords is one held flute note (the "cap") struck on every tick that has a tune-instrument note, so the top voice of the
    # long-note parts is one pitch for a whole bar (the long notes stay long for the guide check) and in the climax it is the highest note struck without end
    hammer = {}
    for g, b in enumerate(bars):
        busy = {0} if b.get("first") else set()
        b["notes"] = [n for n in b.get("notes", []) if not (n[0].startswith(("org", "cf", "cb")) and n[3] in busy)]
    for sec, a, z in (("P4", 64, 85), ("P5", 86, 110)):
        for g in range(a, z):
            b = bars[g]
            ticks = set()
            for n in b.get("notes", []):
                if n[1] in (PLING, FLUTE, BIT, GUITAR, HARP) and n[0] != "cap":
                    ticks.add(n[3])
            for h in b.get("holds", []):
                for t in range(h[3], BAR, h[7]):
                    if t < h[3] + h[4]:
                        ticks.add(t)
            for n in b.get("first", []):
                if n[1] == GUITAR:
                    ticks.add(n[3])
            pcs = pcs_of((P4_CH if sec == "P4" else P5_CH)[g - a]) if g - a < len(P4_CH if sec == "P4" else P5_CH) else [2]
            top = voicing(pcs, 91, 99, 1)[0]
            if sec == "P5" and g - a >= (4 if phase else 8):
                ticks |= set(range(0, BAR, 4))
            ticks &= set(range(BAR))
            b["notes"] = b.get("notes", []) + [("cap", FLUTE, top, t, 74 if sec == "P4" else 84) for t in sorted(ticks)]
    render(sc, bars, drums, VOL, PAN)
    return sc


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    for phase in range(2):
        sc = build(phase)
        layers = sc.build()
        before = {layer.name: len(layer.notes) for layer in layers}
        removed = limit_polyphony(layers, 8)
        lost = ", ".join(f"{layer.name} {before[layer.name] - len(layer.notes)}/{before[layer.name]}" for layer in layers if before[layer.name] - len(layer.notes) > 0)
        print(f"   thinned: {lost}")
        target = out / FILES[phase]
        write_nbs(target, layers, sc.length, sc.tps, TITLES[phase], DESCRIPTIONS[phase], 4)
        print(f"-- phase {phase + 1}: {removed} notes thinned out to keep 8 at most, {sc.refit} notes moved an octave into range {sc.refit_log}")
        report(target, layers, sc.length, sc.tps)
        print("   notes a second by section: " + ", ".join(f"{name} {count:.1f}" for name, count in density_by_section(sc, layers)))
        print("   bass notes per bar by section: " + ", ".join(f"{name} {per:.1f}" for name, _, per in count_by_section(sc, layers, ("bass", "bass2"))))


if __name__ == "__main__":
    main()
