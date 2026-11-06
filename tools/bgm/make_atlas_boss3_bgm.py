#!/usr/bin/env python3
"""Makes colossus_phase1.nbs and colossus_phase2.nbs: "Shoulders of Stone (Boss003)" and "Shoulders of Stone, Unbound (Boss003)", the stone giant in two phases. (BGM t281: written from zero.)

    python3 tools/bgm/make_atlas_boss3_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss3_bgm.py outdir     writes them somewhere else

Class 1 (majesty and despair), STYLE 2 of the structure guide (despair and sacred, many stages), 125 BPM (8.333 ticks a second, a tick is a sixteenth, a bar is 16 ticks), A PHRYGIAN / A minor.
THE MOTIF is the giant's step: A - Bb - E (the tonic, a half step up, then a leap of a fifth that is held): "a shoulder lifts a half step and falls over the edge". It is sung by low brass (guitar + bass-drum
footfall) as a two-bar cell that is repeated a step higher each time (sequence through the Phrygian scale) and always ends held and leaning UP.
110 bars = 211 s = 3:31. Time table (a bar is 1.92 s):
  P1  0:00  bars  0-15   intro 4 (hit, the motif in brass, a snare roll) + 12 bars of IRREGULAR rhythm: the bass heads fall on 7+3+3+3, 5+4+3+4 and 4+3+4+3+2; clusters of A-Bb-E-F; the motif climbs a step every 4 bars
  P2  0:31  bars 16-39   PSEUDO DOUBLE TEMPO: backbeat (snare on 2 and 4) for 4 bars, then the snare on EVERY beat; the motif sequence rises through 12 cells; gallop bass (dotted 3+1), eighths, triplets
  P3  1:17  bars 40-63   THE MELODY IS LOST: only the walking bass and staccato anvil (cow bell) and bone (xylophone), hat and rim on a 4+3+4+3+2 / 3+4+3+3+3 count; bar 63 = BREAK: all muted, one bell
  P4  1:59  bars 64-85   SACRED, A MAJOR (the mode flips: C natural becomes C#, the minor second goes): long organ chords (pling, flute and 8-bit choir swelling), a whole-note chorale that climbs
                         A-E-F#m-D ..., half-time drums with the kick on every beat; bars 80-84 no melody, a snare roll and a bass crescendo; bar 85 = BREAK (one bell)
  P5  2:45  bars 86-109  CLIMAX back in A minor: the sixteenth machine gun + snare on every beat + the sacred chords + the motif in WHOLE notes stepping up (A C D E / C D E F ...), the highest flute hammered;
                         the end falls to quarters on an E major chord and returns to P1.
The bass ladder rises: quarters (intro) / irregular 7-8 a bar (P1) / gallop eighths + doubles (P2) / walks, triplets, doubles (P3) / quarters -> eighths -> doubles -> a sixteenth build-up (P4) / sixteenths (P5).
Phase 2 "Unbound": a half step up (B-flat minor / B-flat major in P4), every stage arrives earlier and thicker (doubles from P1, the 8-bit chip doubles the brass, sixteenths in P3, root-fifth bass in P5, a wail).
Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from cat1_organ import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 125 / 15
SECTIONS = [("P1", 16), ("P2", 24), ("P3", 24), ("P4", 22), ("P5", 24)]
TITLES = ("Shoulders of Stone (Boss003)", "Shoulders of Stone, Unbound (Boss003)")
FILES = ("colossus_phase1.nbs", "colossus_phase2.nbs")
DESCRIPTIONS = (
    "Stone giant, phase 1: class 1, style 2. 125 BPM, A Phrygian: the step motif A-Bb-E in low brass, irregular rhythm, pseudo double tempo, a melody-less part with anvil and bone, a sacred A major chorale, a climax that fuses it all. Same bars as phase 2.",
    "Stone giant, phase 2: the same bars a half step up, every stage earlier and thicker (doubles from the start, chip in the brass, sixteenth bass in the melody-less part, root-fifth bass and a wail in the climax).")

VOL = {"bass": 100, "bass2": 90, "didge": 90, "gtr": 100, "g5": 100, "hit": 100, "bit_m": 100, "pl_m": 100, "fl_m": 90, "org": 60, "cb": 50, "cf": 50, "wail": 90, "ham": 90,
       "cow": 90, "xy": 100, "bell": 60}
PAN = {"org1": 90, "org2": 110, "cb1": 90, "cb2": 110, "cf1": 95, "cf2": 105, "hit1": 90, "hit2": 110, "cow": 90, "xy": 110}

# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- bass cells
def _extra(cell, ticks):
    used = {t for t, _, _ in cell}
    return cell + [(t, 0, 46) for t in ticks if t not in used]


CELLS = {
    # P1: the heads of the count. A = 7+3+3+3, B = 5+4+3+4, C = 4+3+4+3+2 (a semitone, a fifth, a tritone around the root)
    "A": [(0, 0, 98), (2, 0, 58), (5, 0, 64), (7, 1, 88), (10, 0, 80), (13, 7, 84), (15, 1, 52)],
    "B": [(0, 0, 98), (3, 0, 58), (5, 1, 86), (9, 0, 90), (11, 0, 60), (12, 7, 82), (14, 6, 70)],
    "C": [(0, 0, 98), (2, 0, 56), (4, 1, 86), (7, 0, 84), (9, 0, 56), (11, 6, 88), (14, 7, 76)],
    # P2: the gallop (dotted, 3+1), plain eighths, triplets (0, 1, 3 to a beat), a walk through the Phrygian steps, a fill
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
CELLS["Ax"] = _extra(CELLS["A"], (1, 8, 11, 14))
CELLS["Bx"] = _extra(CELLS["B"], (1, 6, 10, 13))
CELLS["Cx"] = _extra(CELLS["C"], (1, 5, 8, 12))
FIGURES.update({"c3_" + k: v for k, v in CELLS.items()})


def seg(cell):
    return "c3_" + cell if cell else None


# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- the motif
SC = [n for n in range(33, 100) if n % 12 in (9, 10, 0, 2, 4, 5, 7)]          # A Phrygian / natural minor with the flat second


def deg(d):
    """The d-th step of the scale from A2."""
    return SC[SC.index(45) + d]


def cell_bars(s):
    """The two-bar cell from scale step s: A - Bb - E | F - E - G (the step motif, then the answer that leans up)."""
    a, b, c, d, e, f = (deg(s + k) for k in (0, 1, 4, 5, 4, 6))
    return [f"{note_name(a)}:6 {note_name(b)}:2 {note_name(c)}:8", f"{note_name(d)}:4 {note_name(e)}:4 {note_name(f)}:8"]


P2_MEL = []
for s0 in (0, 1, 2, 3, 2, 3, 4, 5, 3, 4, 5, 6):
    P2_MEL += cell_bars(s0)
P2_CH = ("Am Am Bb Bb Am Am G E  Am Am Bb Bb Am Am G E  Am Am Bb Bb Am Am G E").split()
CH_ROOT = {"Am": 33, "Bb": 34, "G": 31, "E": 40, "F": 41, "Dm": 38, "A": 33, "F#m": 30, "D": 38, "Bbm": 34}

DISS = [{9, 10, 4, 5}, {9, 0, 3, 6}, {10, 2, 5, 9}, {7, 10, 1, 4}]
P1_ROOTS = [33, 33, 34, 40] + [33, 34, 33, 40, 36, 37, 36, 41, 38, 39, 38, 40]
P1_CELL = [None, None, "Q", "Q"] + ["A", "B", "C"] * 4
P1_MEL = [None, None, "A3:6 Bb3:2 E4:8", "B3:6 C4:2 F4:8"] + [
    "A3:7 Bb3:3 r:3 E4:3", "r:5 E4:4 F4:3 E4:4", "A3:4 Bb3:3 C4:4 Bb3:3 E4:2", "E4:12 F4:4",
    "B3:7 C4:3 r:3 F4:3", "r:5 F4:4 E4:3 F4:4", "B3:4 C4:3 D4:4 C4:3 F4:2", "Eb4:12 F4:4",
    "C4:7 Db4:3 r:3 F4:3", "r:5 F4:11", "C4:4 Db4:3 Eb4:4 Db4:3 F4:2", "Eb4:4 E4:4 F4:8"]

P3_ROOTS = [33, 34, 33, 40, 34, 35, 34, 41, 33, 38, 39, 33, 36, 37, 36, 31, 33, 34, 40, 31, 33, 34, 40]
P3_CELL = {0: ["DGS", "TR", "WK", "TR", "DGS", "WK", "TR", "F16", "TR", "WK", "TR", "WK", "F16", "TR", "WK", "DGS", "TR", "WK", "TR", "F16", "TR", "WK", "M16", None],
           1: ["DGS", "TR", "WK", "TR", "TR", "WK", "TR", "F16", "TR", "WK", "TR", "WK", "F16", "TR", "M16", "TR", "WK", "M16", "TR", "F16", "M16", "M16", "M16", None]}
COW = [66, 70, 73]
XY = [91, 94, 97, 92, 96, 98]
STAC = [   # (tick, kind, velocity, step)
    [(0, "cow", 90, 0), (4, "cow", 72, 1), (7, "cow", 84, 0), (11, "cow", 70, 1), (2, "xy", 84, 1), (9, "xy", 88, 2), (14, "xy", 86, 0), (12, "rim", 62, 0), (5, "hat", 30, 0)],
    [(0, "cow", 90, 1), (3, "cow", 74, 0), (7, "cow", 84, 2), (10, "xy", 88, 3), (13, "xy", 84, 4), (5, "xy", 86, 0), (15, "rim", 60, 0), (8, "hat", 30, 0)],
    [(0, "cow", 90, 2), (5, "cow", 72, 1), (8, "cow", 82, 0), (12, "cow", 76, 2), (2, "xy", 86, 5), (10, "xy", 88, 2), (14, "xy", 84, 3), (4, "rim", 60, 0), (11, "hat", 28, 0)]]
STAC2 = [[(t, "hat", 24, 0) for t in (1, 3, 6, 9, 13)], [(t, "hat", 24, 0) for t in (2, 4, 6, 11, 14)], [(t, "hat", 24, 0) for t in (1, 3, 7, 9, 15)]]

P4_CH = ("A E F#m D A D E E  F#m D A E F#m D E E  D A E E E").split()
P4_ROOTS = {"A": 33, "E": 40, "F#m": 30, "D": 38}
P4_MEL = ["A4:8 C#5:8", "B4:8 E5:8", "C#5:8 F#5:8", "D5:16", "C#5:8 E5:8", "D5:8 F#5:8", "E5:8 G#5:8", "G#5:16",
          "A4:8 C#5:8", "B4:8 D5:8", "C#5:8 E5:8", "B4:8 E5:8", "C#5:8 F#5:8", "D5:8 F#5:8", "E5:8 G#5:8", "G#5:8 B5:8"]
P4_CELL = {0: ["QQ"] * 8 + ["E2"] * 6 + ["DGS", "DGS"] + ["F16", "M16", "M16", "M16", "M16", None],
           1: ["QQ"] * 4 + ["E2"] * 8 + ["DGS"] * 4 + ["F16", "M16", "M16", "M16", "M16", None]}

P5_CH = ("Am F G E  Am Bb G E  Dm Bb G E  Am F Bb E  Dm Bb G E  Am Bb E E").split()
P5_ROOT = {"Am": 33, "F": 41, "G": 31, "E": 40, "Bb": 34, "Dm": 38}
P5_MEL = ["A4:16", "C5:16", "D5:16", "E5:16", "C5:16", "D5:16", "E5:16", "F5:16",
          "F5:16", "D5:8 F5:8", "E5:8 D5:8", "E5:16", "A4:8 E5:8", "C5:8 F5:8", "D5:8 F5:8", "E5:16",
          "F5:16", "D5:16", "D5:8 E5:8", "F5:16", "E5:16", "F5:16", "E5:8 F5:8", "F5:16"]
P5_CELL = ["M16"] * 22 + ["E2", "QQ"]


def hats(ticks, loud, soft):
    return [(t, "hat", loud if t % 2 == 0 else soft) for t in ticks]


DRUMS = {
    "i0": [(0, "bd", 100), (7, "bd", 70)], "i1": [(0, "bd", 96), (7, "bd", 74), (10, "sn", 56)], "i2": [(0, "bd", 100), (5, "bd", 80), (9, "bd", 90), (12, "sn", 70), (14, "bd", 70)],
    # P1: the heads of the count
    "A": [(0, "bd", 100), (7, "bd", 86), (10, "sn", 70), (13, "bd", 80), (3, "sn", 50)],
    "B": [(0, "bd", 100), (5, "bd", 84), (9, "sn", 72), (12, "bd", 90), (14, "sn", 54)],
    "C": [(0, "bd", 100), (4, "sn", 66), (7, "bd", 84), (11, "bd", 90), (14, "sn", 60)],
    "Ax": [(0, "bd", 100), (3, "sn", 54), (7, "bd", 90), (10, "sn", 76), (13, "bd", 86), (15, "sn", 56)],
    "Bx": [(0, "bd", 100), (5, "bd", 90), (7, "sn", 56), (9, "sn", 76), (12, "bd", 94), (14, "sn", 60)],
    "Cx": [(0, "bd", 100), (4, "sn", 72), (7, "bd", 90), (9, "sn", 54), (11, "bd", 94), (14, "sn", 66)],
    # P2: the snare on 2 and 4, then on every beat
    "back": [(0, "bd", 100), (6, "bd", 70), (10, "bd", 90), (4, "sn", 90), (12, "sn", 96)],
    "dbl": [(0, "bd", 100), (6, "bd", 76), (10, "bd", 92), (14, "bd", 60), (0, "sn", 56), (4, "sn", 92), (8, "sn", 62), (12, "sn", 98)] + [(t, "hat", 26) for t in (2, 6, 10, 14)],
    "u_dbl": [(0, "bd", 100), (3, "bd", 60), (6, "bd", 84), (10, "bd", 96), (14, "bd", 66), (0, "sn", 62), (4, "sn", 98), (8, "sn", 70), (12, "sn", 100)] + [(t, "hat", 28) for t in (2, 6, 10, 14)],
    # P4: half time, the kick on every beat
    "sac0": [(0, "bd", 88), (4, "bd", 56), (8, "bd", 78), (12, "bd", 56)],
    "sac1": [(0, "bd", 94), (4, "bd", 62), (8, "bd", 86), (12, "bd", 62), (12, "sn", 62)],
    "sac2": [(0, "bd", 98), (4, "bd", 72), (8, "bd", 92), (12, "bd", 72), (4, "sn", 66), (12, "sn", 78)] + [(t, "hat", 24) for t in (2, 6, 10, 14)],
    "build": [(0, "bd", 98), (4, "bd", 82), (8, "bd", 98), (12, "bd", 82), (6, "sn", 60), (14, "sn", 72)],
    # P5: the fast rhythm with sixteenth bass
    "fuse": [(0, "bd", 100), (4, "bd", 86), (8, "bd", 98), (12, "bd", 86), (14, "bd", 58), (0, "sn", 62), (4, "sn", 96), (8, "sn", 66), (12, "sn", 100)] + [(t, "hat", 34) for t in (2, 6, 10, 14)],
    "u_fuse": [(0, "bd", 100), (4, "bd", 92), (8, "bd", 100), (12, "bd", 92), (6, "bd", 60), (14, "bd", 66), (0, "sn", 72), (4, "sn", 100), (8, "sn", 76), (12, "sn", 100)] + [(t, "hat", 40) for t in (2, 6, 10, 14)],
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
    for i in range(16):
        b = {"shift": phase}
        pcs = DISS[i % 4]
        cell = P1_CELL[i] + ("x" if p2 and P1_CELL[i] in ("A", "B", "C") else "") if P1_CELL[i] else "Q"
        if i == 0:
            cell = "Q"
        b["bass"] = {"segs": [(P1_ROOTS[i], 3)], "fig": seg(cell) if cell != "Q" else seg("QQ"), "gain": [0.9, 0.85, 0.9, 1.0][i] if i < 4 else 1.0}
        b["didge"] = 1.0
        b["drums"] = ["i0", "i1", "i2", "i2"][i] if i < 4 else cell
        if i == 0:
            b["first"] = hit_notes(33)
            b["didge_skip0"] = True
        notes = []
        if i >= 2:
            g = [0, 0, 0.6, 0.85][i] if i < 4 else 0.9
            notes += pad_notes(st, "org", pcs, 55, 68, 3, range(2, BAR, 8), "org", PLING, lambda t, g=g, i=i: pad_vel(40, 62, i, t) * g)
        if i >= 3:
            notes += pad_notes(st, "cho", pcs, 79, 90, 2, range(6, BAR, 8), "cf", FLUTE, lambda t, i=i: pad_vel(22, 60, i, t) * (0.7 if i < 4 else 0.9))
        b["notes"] = notes
        if P1_MEL[i]:
            voices = {"gtr": (GUITAR, 0, 1.0)}
            if p2:
                voices["bit_m"] = (BIT, 12, 1.0)
            b["holds"] = hold_events(P1_MEL[i], voices)
            for tick, note, length in melody_events(P1_MEL[i]):
                if 42 <= note + 7 + phase <= 66:
                    b["holds"].append(("g5", GUITAR, note + 7, tick, length, 80, 58, 4))
        if i >= 6 and cell in ("A", "B", "C", "Ax", "Bx", "Cx"):
            t2 = {"A": 7, "B": 5, "C": 4}[cell[0]]
            low = low_brass(P1_ROOTS[i])
            notes += [("hit1", GUITAR, low, t2, 92), ("hit2", GUITAR, low + 6, t2, 92), ("sn", SNARE, 66, t2, 84), ("didge", DIDGERIDOO, P1_ROOTS[i], t2, 90)]
            b["notes"] = notes
        if i == 3 or i % 4 == 3:
            b["roll"] = (8, 8, 30, 80)
        if i in (4, 8, 12):
            b["first"] = hit_notes(P1_ROOTS[i], False) + [("bd", BASEDRUM, 66, 0, 100)]
            b["didge_skip0"] = True
        bars.append(b)
    # ------------------------------------------------------------------ P2: pseudo double tempo
    plan = {0: ["E2"] * 4 + ["DG", "DG", "E2", "DGS", "DG", "DGS", "TR", "DG", "WK", "TR", "DGS", "F16", "TR", "DGS", "WK", "TR", "TR", "WK", "DGS", "F16"],
            1: ["DGS", "E2", "DGS", "DG", "DG", "DGS", "E2", "DGS", "DGS", "TR", "TR", "DGS", "WK", "TR", "TR", "F16", "TR", "TR", "WK", "TR", "TR", "WK", "TR", "F16"]}[phase]
    for i in range(24):
        b = {"shift": phase}
        name = P2_CH[i]
        root = CH_ROOT[name]
        b["bass"] = {"segs": [(root, 3)], "fig": seg(plan[i])}
        b["didge"] = 1.0
        b["drums"] = ("back" if i < 4 else "dbl") if not p2 else ("back" if i < 2 else "u_dbl")
        pcs = pcs_of(name)
        notes = []
        if i >= 4:
            notes += pad_notes(st, "org", pcs, 55, 68, 3, range(2, BAR, 8), "org", PLING, lambda t, i=i: pad_vel(40, 60, i, t))
            notes += pad_notes(st, "cho", pcs, 79, 90, 3 if p2 else 2, range(6, BAR, 8), "cf", FLUTE, lambda t, i=i: pad_vel(22, 62, i, t))
        b["notes"] = notes
        text = P2_MEL[i]
        voices = {"gtr": (GUITAR, 0, 1.0)}
        if p2 and i >= 4:
            voices["bit_m"] = (BIT, 12, 1.0)
        b["holds"] = hold_events(text, voices)
        for tick, note, length in melody_events(text):
            if 42 <= note + 7 + phase <= 66:
                b["holds"].append(("g5", GUITAR, note + 7, tick, length, 80, 58, 4))
        if i % 4 == 0:
            b["first"] = hit_notes(root)
            b["didge_skip0"] = True
        if i >= 4:
            low = low_brass(root)
            b["notes"] += [("hit1", GUITAR, low, 8, 96), ("hit2", GUITAR, low + 7, 8, 96)]
        if i % 4 == 3:
            b["roll"] = (8, 8, 34, 92)
        bars.append(b)
    # ------------------------------------------------------------------ P3: the melody is lost
    for i in range(24):
        b = {"shift": phase}
        if i == 23:
            b["first"] = [("bell", BELL, 93, 0, 55, False)]
            bars.append(b)
            continue
        root = P3_ROOTS[i]
        b["bass"] = {"segs": [(root, 3)], "fig": seg(P3_CELL[phase][i])}
        notes = []
        extra = []
        for tick, kind, vel, step in (STAC[i % 3] + (STAC2[i % 3] if p2 or i >= 12 else [])):
            if kind == "cow":
                notes.append(("cow", COW_BELL, COW[step % 3], tick, vel))
                extra.append((tick, "bd", 76 if tick else 100))
            elif kind == "xy":
                notes.append(("xy", XYLOPHONE, XY[step % 6], tick, vel))
            else:
                extra.append((tick, kind, vel))
        if i % 4 == 0:
            b["first"] = hit_notes(root)
        if i % 2 == 1:
            low = low_brass(root)
            notes += [("hit1", GUITAR, low, 7, 92), ("hit2", GUITAR, low + 6, 7, 92), ("bd", BASEDRUM, 66, 7, 90), ("sn", SNARE, 66, 7, 84), ("didge", DIDGERIDOO, root, 7, 90)]
        b["notes"] = notes
        b["extra_drums"] = extra
        bars.append(b)
    # ------------------------------------------------------------------ P4: sacred, A major
    for i in range(22):
        b = {"shift": phase}
        if i == 21:
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
        notes = pad_notes(st, "sorg", pcs, 55, 68, 3, range(2, BAR, 8), "org", PLING, lambda t, i=i: pad_vel(40, 68, i, t))
        notes += pad_notes(st, "scho", pcs, 79, 98 if p2 else 90, 4 if p2 else 3, range(6, BAR, 8), "cf", FLUTE, lambda t, i=i: pad_vel(22, 66, i, t), wide=p2)
        notes += pad_notes(st, "sbit", pcs, 62, 74, 2, range(6, BAR, 8), "cb", BIT, lambda t, i=i: pad_vel(18, 56, i, t))
        if 4 <= i < 16 and i % 2 == 0:
            low = low_brass(root)
            notes += [("hit1", GUITAR, low, 8, 94), ("hit2", GUITAR, low + 7, 8, 94), ("sn", SNARE, 66, 8, 80)]
        b["notes"] = notes
        if i < 16:
            voices = {"pl_m": (PLING, -12, 1.0), "bit_m": (BIT, -12, 1.0), "fl_m": (FLUTE, 12, 0.9)}
            b["holds"] = hold_events(P4_MEL[i], voices, swell=1.12)
        else:
            b["roll"] = (4, 12, 30 + 12 * (i - 16), 66 + 8 * (i - 16))
        bars.append(b)
    # ------------------------------------------------------------------ P5: the fusion
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
            notes += pad_notes(st, "org", pcs, 55, 68, 3, range(2, BAR, 8), "org", PLING, lambda t, i=i: pad_vel(40, 62, i, t) * 1.1)
            notes += pad_notes(st, "cho", pcs, 79, 98 if p2 else 90, 4 if p2 else 3, range(6, BAR, 8), "cf", FLUTE, lambda t, i=i: pad_vel(22, 72, i, t), wide=p2)
        h0 = 8 if p2 else 10
        if h0 <= i < 22:
            hi = voicing(pcs, 90, 99, 1)[0]
            for tick in range(0, BAR, 4 if (i % 4 == 3 or i >= 22 or (p2 and i < 16)) else 2):
                notes.append(("ham", FLUTE, hi, tick, lerp(54, 100, ((i - h0) * BAR + tick) / ((22 - h0) * BAR))))
        b["notes"] = notes
        voices = {"pl_m": (PLING, -12, 1.0), "bit_m": (BIT, -12, 1.0), "gtr": (GUITAR, -12, 1.0)}
        b["holds"] = hold_events(P5_MEL[i], voices, swell=1.12)
        if i % 4 == 0 and i < 22:
            b["first"] = hit_notes(root)
            b["didge_skip0"] = True
        if i % 4 == 3 and i < 22:
            b["roll"] = (10, 6, 40, 92)
        if i == 23:
            b["roll"] = (8, 8, 30, 88)
        if p2 and 12 <= i < 22:
            for k in range(2):
                step = (i - 12) * 2 + k
                for tick in range(k * 8 + 1, k * 8 + 4, 4):
                    b["notes"].append(("wail", FLUTE, 78 + step, tick, lerp(55, 100, (step * 4 + (tick - k * 8) // 2) / 64)))
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
