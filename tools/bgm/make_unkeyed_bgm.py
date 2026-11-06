#!/usr/bin/env python3
"""Makes unkeyed_phase1.nbs and unkeyed_phase2.nbs: "Eyes Without a Key (Boss009)" and "..., Unwritten (Boss009)", a boss beyond human understanding. (BGM t281: written from zero.)

    python3 tools/bgm/make_unkeyed_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_unkeyed_bgm.py outdir     writes them somewhere else

Class 1 (majesty and despair, the cosmic face), STYLE 2 of the structure guide in its own way: NO TONIC, the whole-tone scale (C# D# F G A B in phase 1, the other scale in phase 2), 125 BPM
(8.333 ticks a second, a tick is a sixteenth, a bar is 16 ticks), the whole song counts 3+3+3+3+4. No guitar, no pling, no backbeat.
THE MOTIF is "the twin eyes": two voices a major third apart (harp under, 8-bit reed over) that climb in whole steps, 3 + 3 + 3 and then a leap of an augmented fifth that is held for 7 ticks:
low line s, s+2, s+4, s+8 | s+8, s+6, s+8, s+12, the high line the same a major third higher; each two-bar cell is repeated a whole step higher (the sequence never reaches a tonic, it only leaves).
94 bars = 181 s = 3:01. Time table (a bar is 1.92 s):
  P1  0:00  bars  0-13   intro 4 (hit, the cell in the dark, a snare roll) + 10 bars: the pedal FALLS in major thirds (F - C# - A), the count 3+3+3+3+4 on bass and bass drum, clusters of whole-tone neighbours,
                         two flutes that blink (the eyes), the motif climbs
  P2  0:27  bars 14-33   PSEUDO DOUBLE TEMPO: the bass drum counts 0-6-12 for four bars, then 0-3-6-9-12 and a clap on 3 and 9; the pedal falls a major third every two bars; the tune in two voices
  P3  1:05  bars 34-53   THE MELODY IS LOST (its native state): only the bass (walks, wobbling triplets, the orbit) and staccato anvil (cow bell) and bone (xylophone) in TRITONE pairs, hat and rim; bar 53 = BREAK, one bell
  P4  1:43  bars 54-73   SACRED, the OTHER whole-tone scale (the mode flip): long stacks of augmented thirds (8-bit, re-struck, swelling), a flute choir, the motif in WHOLE notes (low line + high line), held
                         tritone pairs that climb, half-time drums, the pedal rises; bars 70-72 no melody, a roll and a bass crescendo; bar 73 = BREAK
  P5  2:22  bars 74-93   CLIMAX in the first scale: the sixteenth ORBIT (0, +8, +4 in each group of three) + the counting 0-3-6-9-12 + the long chords + the motif stretched to half notes, the highest flute hammered
The bass ladder: quarters (intro) / count eighths (P1) / doubles and wobbling triplets (P2) / walks, triplets, orbit (P3) / quarters -> eighths -> doubles -> a sixteenth build-up (P4) / sixteenth orbit (P5).
Phase 2 "Unwritten": the other scale (a half step up; the sacred part flips back), the pedal RISES, the doubles and the orbit come earlier, an iron-xylophone voice a tritone under the low line, torn clusters (flutes of
the other scale), earlier hammering. Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from cat1_organ import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 125 / 15
SECTIONS = [("P1", 14), ("P2", 20), ("P3", 20), ("P4", 20), ("P5", 20)]
TITLES = ("Eyes Without a Key (Boss009)", "Eyes Without a Key, Unwritten (Boss009)")
FILES = ("unkeyed_phase1.nbs", "unkeyed_phase2.nbs")
DESCRIPTIONS = (
    "Boss without a key, phase 1: class 1, style 2. 125 BPM, no tonic, counted 3+3+3+3+4: the twin-eyes motif (two voices a major third apart climbing in whole steps), a pedal falling in major thirds, a melody-less part of bass and tritone anvil/bone, a sacred part in the other whole-tone scale, a climax with a sixteenth orbit bass. Same bars as phase 2.",
    "Boss without a key, phase 2: the same bars in the other whole-tone scale, a rising pedal, the doubles and the orbit earlier, a tritone voice under the low line, torn clusters, earlier hammering.")

W1 = {1, 3, 5, 7, 9, 11}
W0 = {0, 2, 4, 6, 8, 10}
VOL = {"bass": 100, "bass2": 90, "didge": 95, "harp": 100, "bit_m": 100, "fl_m": 90, "tri_i": 100, "hb": 100, "o": 60, "p": 55, "c": 50, "r": 45, "eye": 50, "cow": 90, "xy": 100, "bell": 60,
       "ham": 90, "sb": 60, "sf": 50}
PAN = {"o1": 90, "o2": 110, "o3": 100, "o4": 85, "p1": 90, "p2": 110, "c1": 95, "c2": 105, "eye1": 80, "eye2": 120, "tri_i": 110, "cow": 90, "xy": 110, "harp": 90, "bit_m": 110}


def names(text):
    return [midi(n) for n in text.split()]


def steps(values, each):
    out = []
    for n in values:
        out += [n] * each
    return out


# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- the motif
CELL_A = [(0, 0, 3), (3, 2, 3), (6, 4, 3), (9, 8, 7)]           # (start, semitones over s, length)
CELL_B = [(16, 8, 3), (19, 6, 3), (22, 8, 3), (25, 12, 7)]


def cell_events(s, stretch=1, high=0):
    return [(a * stretch, s + o + high, n * stretch) for a, o, n in CELL_A + CELL_B]


def to_texts(events, bars):
    """[(start, midi, length)] over ``bars`` bars -> one text per bar ('G3:3 A3:3 r:2 ...'); a note that crosses a bar line is struck again in the next bar."""
    texts = []
    for b in range(bars):
        lo, hi = b * BAR, (b + 1) * BAR
        parts, pos = [], 0
        for start, note, length in sorted(events):
            a, z = max(start, lo), min(start + length, hi)
            if a >= z:
                continue
            if a > lo + pos:
                parts.append(f"r:{a - lo - pos}")
            parts.append(f"{note_name(note)}:{z - a}")
            pos = z - lo
        if pos < BAR:
            parts.append(f"r:{BAR - pos}")
        texts.append(" ".join(parts))
    return texts


def tune_texts(starts, stretch, bars):
    """The low line and the high line (a major third over) of the cells that begin at the scale pitches ``starts`` (one cell = 2 bars, or 4 stretched twice)."""
    low, high = [], []
    span = 2 * stretch * BAR
    for k, s in enumerate(starts):
        low += [(a + k * span, n, ln) for a, n, ln in cell_events(s, stretch)]
        high += [(a + k * span, n, ln) for a, n, ln in cell_events(s, stretch, 4)]
    return to_texts(low, bars), to_texts(high, bars)


REST = "r:16"
P1_LOW, P1_HIGH = tune_texts([55, 57, 59, 61, 59, 61], 1, 12)
P1_LOW, P1_HIGH = [REST, REST] + P1_LOW, [REST, REST] + P1_HIGH
P2_LOW, P2_HIGH = tune_texts([55, 57, 59, 61, 57, 59, 61, 59, 61, 59], 1, 20)
P4_STARTS = [55, 57, 59, 61]
P4_LOW = []
P4_HIGH = []
for s0 in P4_STARTS:       # the motif in whole notes: 4 bars per cell
    P4_LOW += [f"{note_name(s0 + o)}:16" for o in (0, 2, 4, 8)]
    P4_HIGH += [f"{note_name(s0 + o + 4)}:16" for o in (0, 2, 4, 8)]
P5_LOW, P5_HIGH = tune_texts([55, 57, 59, 61, 59], 2, 20)

# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- bass
PEDAL = {
    0: {"P1": steps([41], 6) + steps([37], 4) + steps([33], 4),
        "P2": steps([41, 37, 33, 41, 37, 33, 41, 37, 33, 41], 2),
        "P3": [31, 35, 39, 31, 33, 37, 41, 33, 35, 36, 31, 33, 37, 41, 39, 35, 30, 33, 35, 31],
        "P4": steps([31, 33, 35, 37, 39], 4),
        "P5": steps([41, 37, 33, 41, 37, 33, 41, 37, 33, 41], 2)},
    1: {"P1": steps([33], 6) + steps([37], 4) + steps([41], 4),
        "P2": steps([33, 37, 41, 33, 37, 41, 33, 37, 41, 33], 2),
        "P3": [31, 35, 39, 31, 33, 37, 41, 33, 35, 36, 31, 33, 37, 41, 39, 35, 30, 33, 35, 31],
        "P4": steps([39, 37, 35, 33, 31], 4),
        "P5": steps([33, 37, 41, 33, 37, 41, 33, 37, 41, 33], 2)}}
ORBIT = [0, 8, 4] * 4 + [0, 8, 4, 6]
CELLS = {
    "Q": [(0, 0, 98), (3, 0, 60), (6, 0, 80), (9, 0, 60), (12, 0, 76)],
    "E": [(0, 0, 98), (3, 0, 70), (6, 0, 86), (9, 0, 70), (12, 0, 92), (14, 0, 58)],
    "S": [(0, 0, 98), (3, 0, 70), (6, 0, 86), (9, 0, 70), (12, 0, 92), (14, 0, 58)] + [(t, 0, 46) for t in (1, 4, 7, 10, 13)],
    "T": [(g + k, 2 * k, (96 if k == 0 else 54)) for g in (0, 3, 6, 9) for k in range(3)] + [(12, 0, 90), (14, 6, 64)],
    "WK": [(0, 0, 98), (3, 4, 80), (6, 6, 88), (9, 8, 76), (12, 6, 90), (14, 4, 64), (1, 0, 44), (7, 6, 48)],
    "F": [(0, 0, 98), (3, 0, 70), (6, 0, 86)] + [(9 + k, o, 60 + 5 * k) for k, o in enumerate((0, 4, 8, 6, 4, 2, 6))],
    "ORB": [(k, o, 98 if k == 0 else 88 if k in (3, 6, 9, 12) else 52) for k, o in enumerate(ORBIT)],
}
FIGURES.update({"u9_" + k: v for k, v in CELLS.items()})


def fig(name):
    return "u9_" + name if name else None


PLAN = {
    0: {"P1": ["Q", "Q", "Q", "Q"] + ["E"] * 6 + ["S", "E", "S", "S"],
        "P2": ["E", "S", "E", "S", "S", "T", "S", "T", "S", "T", "T", "S", "T", "T", "S", "F", "T", "WK", "T", "F"],
        "P3": ["S", "T", "WK", "T", "S", "WK", "T", "F", "T", "WK", "T", "WK", "F", "T", "WK", "T", "F", "T", "ORB", None],
        "P4": ["Q"] * 8 + ["E"] * 8 + ["F", "ORB", "ORB", "ORB", "ORB", None][:4],
        "P5": ["ORB"] * 18 + ["E", "Q"]},
    1: {"P1": ["Q", "Q", "E", "S"] + ["S", "E"] * 3 + ["S", "T", "S", "T"],
        "P2": ["S", "S", "T", "S", "T", "T", "S", "T", "T", "S", "T", "T", "T", "T", "S", "F", "T", "WK", "T", "F"],
        "P3": ["S", "T", "WK", "T", "T", "WK", "T", "F", "T", "WK", "ORB", "WK", "F", "T", "ORB", "T", "F", "ORB", "ORB", None],
        "P4": ["Q"] * 4 + ["E"] * 8 + ["S"] * 4 + ["F", "ORB", "ORB", "ORB", "ORB"][:4],
        "P5": ["ORB"] * 18 + ["E", "Q"]}}
PLAN[0]["P4"] = ["Q"] * 8 + ["E"] * 8 + ["F", "ORB", "ORB", "ORB"] + [None]
PLAN[1]["P4"] = ["Q"] * 4 + ["E"] * 8 + ["S"] * 4 + ["F", "ORB", "ORB", "ORB"] + [None]

STAC = [   # (tick, kind, velocity, step): the anvil and the bone in tritone pairs
    [(0, "cow", 90, 0), (6, "cow", 74, 1), (12, "cow", 84, 0), (3, "xy", 90, 0), (9, "xy", 92, 1), (14, "xy", 86, 2), (4, "rim", 62, 0), (10, "hat", 28, 0)],
    [(0, "cow", 90, 2), (3, "cow", 72, 3), (9, "cow", 84, 2), (6, "xy", 88, 3), (12, "xy", 90, 4), (15, "xy", 84, 5), (12, "rim", 66, 0), (5, "hat", 28, 0), (13, "hat", 26, 0)],
    [(0, "cow", 90, 0), (6, "cow", 76, 3), (3, "xy", 86, 4), (8, "xy", 90, 5), (11, "xy", 86, 0), (14, "xy", 84, 1), (9, "rim", 62, 0), (1, "hat", 28, 0), (4, "hat", 26, 0)]]
STAC2 = [[(t, "hat", 24, 0) for t in (1, 5, 7, 13)], [(t, "hat", 24, 0) for t in (2, 4, 8, 14)], [(t, "hat", 24, 0) for t in (2, 5, 10, 13)]]
COW = [72, 78, 74, 80]
XY = [90, 96, 92, 98, 94, 100]
DYADS = [(79, 85), (82, 88), (85, 91), (86, 92), (88, 94), (91, 97), (79, 85), (82, 88)]


def hats(ticks, loud, soft):
    return [(t, "hat", loud if t % 2 == 0 else soft) for t in ticks]


DRUMS = {
    "i0": [(0, "bd", 100), (6, "bd", 66), (12, "bd", 76)], "i1": [(0, "bd", 96), (6, "bd", 70), (12, "bd", 80), (9, "sn", 54)], "i2": [(0, "bd", 100), (6, "bd", 78), (12, "bd", 86), (3, "sn", 54), (9, "sn", 62)],
    "c": [(0, "bd", 100), (6, "bd", 80), (12, "bd", 88), (3, "sn", 58), (9, "sn", 64), (15, "sn", 50)],
    "cx": [(0, "bd", 100), (6, "bd", 86), (12, "bd", 92), (3, "sn", 68), (9, "sn", 72), (14, "bd", 56), (15, "sn", 56)],
    "d3": [(0, "bd", 100), (3, "bd", 62), (6, "bd", 88), (9, "bd", 62), (12, "bd", 96), (3, "sn", 76), (9, "sn", 82), (14, "sn", 60)] + [(t, "hat", 24) for t in (1, 7, 13)],
    "d3x": [(0, "bd", 100), (3, "bd", 70), (6, "bd", 92), (9, "bd", 70), (12, "bd", 98), (3, "sn", 86), (9, "sn", 90), (14, "sn", 66)] + [(t, "hat", 28) for t in (1, 7, 13)],
    "sac0": [(0, "bd", 92), (6, "bd", 60), (12, "bd", 68)], "sac1": [(0, "bd", 96), (6, "bd", 68), (12, "bd", 76), (9, "sn", 58)],
    "sac2": [(0, "bd", 100), (6, "bd", 80), (12, "bd", 88), (3, "sn", 64), (9, "sn", 68), (14, "sn", 58)] + [(t, "hat", 24) for t in (2, 8, 11)],
    "build": [(0, "bd", 100), (6, "bd", 86), (12, "bd", 94), (3, "sn", 72), (9, "sn", 76), (14, "sn", 68)],
    "peak": [(0, "bd", 100), (3, "bd", 80), (6, "bd", 96), (9, "bd", 82), (12, "bd", 100), (3, "sn", 90), (9, "sn", 94), (14, "sn", 76)] + [(t, "hat", 34) for t in (1, 7)],
    "peak2": [(0, "bd", 100), (3, "bd", 86), (6, "bd", 100), (9, "bd", 88), (12, "bd", 100), (3, "sn", 96), (9, "sn", 100), (14, "sn", 80), (15, "bd", 58)] + [(t, "hat", 40) for t in (1, 7)],
    "end": [(0, "bd", 90), (8, "bd", 70)],
}


def ladder_notes(base, count, pcs=W1):
    return ladder(pcs, base, count)


def ramp(lo, hi, i, tick, block=4):
    return lerp(lo, hi, ((i % block) * BAR + tick) / (block * BAR))


def cluster(prefix, inst, notes, ticks, vel_of):
    return [(f"{prefix}{k + 1}", inst, note, tick, vel_of(tick)) for tick in ticks for k, note in enumerate(notes)]


def bars_of(phase):
    bars = []
    p2 = phase == 1
    pedal = PEDAL[phase]
    plan = PLAN[phase]

    def tune(low, high, flute=False, long_tune=False, tri=True):
        out = []
        for text, voices in ((low, {"harp": (HARP, 0, 1.0)}), (high, dict({"bit_m": (BIT, 0, 1.0)}, **({"fl_m": (FLUTE, 24, 0.9)} if flute else {})))):
            if text.replace(" ", "") == REST.replace(" ", ""):
                continue
            evs = hold_events(text, voices, swell=1.14 if long_tune else 1.0)
            out += evs
            if p2 and tri and voices.get("harp"):
                for tick, note, length in melody_events(text):
                    out.append(("tri_i", IRON_XYLOPHONE, note + 6 if note + 6 + phase <= 76 else note - 6, tick, length, 90, 70, 4))
        return out

    def eyes(ticks=(1, 9), v=1.0):
        return [("eye1" if k % 2 == 0 else "eye2", FLUTE, 84 if k % 2 == 0 else 90, t, 24 + 16 * v + (6 if t == 9 else 0)) for k, t in enumerate(ticks)]

    def stabs(base, ticks, vel=84):
        return [(f"hb{k + 1}", BIT, n, t, vel) for t in ticks for k, n in enumerate(ladder_notes(base, 3))]

    def hit(root, base=55, stab=3):
        return [("bass", BASS, root, 0, 100), ("bd", BASEDRUM, 66, 0, 100), ("sn", SNARE, 66, 0, 90)] + [(f"hb{k + 1}", BIT, n, 0, 92) for k, n in enumerate(ladder_notes(base, stab))]

    # ------------------------------------------------------------------ P1
    for i in range(14):
        b = {"shift": phase}
        root = pedal["P1"][i]
        gain = [0.9, 0.85, 0.9, 1.0][i] if i < 4 else 1.0
        b["bass"] = {"segs": [(root, 3)], "fig": fig(plan["P1"][i]), "gain": gain}
        b["didge"] = 1.0
        b["drums"] = ["i0", "i1", "i2", "i2"][i] if i < 4 else ("cx" if i >= 10 or p2 else "c")
        base = 55 + 2 * min(i // 2, 5)
        if i == 0 or i in (4, 8, 12):
            b["first"] = hit(root, base, 3)
            b["didge_skip0"] = True
        notes = []
        if i >= 2:
            g = {2: 0.6, 3: 0.85}.get(i, 1.0)
            notes += cluster("o", BIT, ladder_notes(base, 4 if i >= 3 else 3), range(3, BAR, 8), lambda t, i=i, g=g: ramp(40, 62, i, t) * g * 1.1)
        if i >= 3:
            notes += cluster("c", FLUTE, ladder_notes(85 - 2 * min(i // 2, 3), 3), range(7, BAR, 8), lambda t, i=i: ramp(26, 62, i, t) * (0.6 if i < 5 else 1.0))
        if p2 and i >= 2:
            notes += cluster("p", BIT, ladder_notes(base, 3), range(5, BAR, 8), lambda t, i=i: ramp(26, 50, i, t) * 0.8)
        if i >= 1:
            notes += eyes((1, 9), 0.5 + min(i, 8) * 0.07)
        if i >= 8:
            notes += stabs(base, (6, 12))
        b["notes"] = notes
        b["holds"] = tune(P1_LOW[i], P1_HIGH[i])
        if i % 4 == 3:
            b["roll"] = (8, 8, 34, 92)
        bars.append(b)
    # ------------------------------------------------------------------ P2: the counting doubles
    for i in range(20):
        b = {"shift": phase}
        root = pedal["P2"][i]
        b["bass"] = {"segs": [(root, 3)], "fig": fig(plan["P2"][i])}
        b["didge"] = 1.0
        b["drums"] = ("c" if i < 4 else "d3") if not p2 else ("c" if i < 2 else "d3x")
        base = 55 + 2 * ((i // 4) % 5)
        notes = cluster("o", BIT, ladder_notes(base, 3), range(3, BAR, 8), lambda t, i=i: ramp(40, 62, i, t) * 1.1)
        notes += cluster("c", FLUTE, ladder_notes(79 + 2 * (i // 4), 3), range(7, BAR, 8), lambda t, i=i: ramp(26, 62, i, t))
        if p2 and i >= 8:
            notes += cluster("r", FLUTE, ladder(W0, 85 + 3 * (i // 4) % 6, 2), range(5, BAR, 8), lambda t, i=i: ramp(28, 60, i, t))
        notes += eyes((1, 9), 1.0)
        notes += stabs(base, (12,)) if i % 2 == 1 else []
        b["notes"] = notes
        b["holds"] = tune(P2_LOW[i], P2_HIGH[i], flute=i >= 8)
        if i % 4 == 0:
            b["first"] = hit(root, base)
            b["didge_skip0"] = True
        if i % 4 == 3:
            b["roll"] = (8, 8, 34, 92)
        bars.append(b)
    # ------------------------------------------------------------------ P3: the melody is lost
    for i in range(20):
        b = {"shift": phase}
        if i == 19:
            b["first"] = [("bell", BELL, 93, 0, 55, False)]
            bars.append(b)
            continue
        root = pedal["P3"][i]
        b["bass"] = {"segs": [(root, 3)], "fig": fig(plan["P3"][i])}
        notes, extra = [], []
        for tick, kind, vel, step in (STAC[i % 3] + (STAC2[i % 3] if p2 or i >= 10 else [])):
            if kind == "cow":
                notes.append(("cow", COW_BELL, COW[step % 4], tick, vel))
                extra.append((tick, "bd", 80 if tick else 100))
            elif kind == "xy":
                notes.append(("xy", XYLOPHONE, XY[step % 6], tick, vel))
            else:
                extra.append((tick, kind, vel))
        base = 55 + 2 * (i // 4)
        if i % 4 == 0:
            b["first"] = hit(root, base)
        notes += stabs(base, (6, 12) if i % 4 == 0 else (0, 6, 12), 80)
        b["notes"] = notes
        b["extra_drums"] = extra
        bars.append(b)
    # ------------------------------------------------------------------ P4: sacred, the other whole-tone scale
    for i in range(20):
        b = {"shift": phase + 1}
        if i == 19:
            b["first"] = [("bell", BELL, 93, 0, 55, False)]
            bars.append(b)
            continue
        root = pedal["P4"][i]
        gain = {15: 0.7, 16: 0.8, 17: 0.9, 18: 1.0}.get(i, 1.0)
        b["bass"] = {"segs": [(root, 3)], "fig": fig(plan["P4"][i]), "gain": gain}
        b["didge"] = 0.9
        b["drums"] = ("sac0" if i < 6 else "sac1" if i < 12 else "sac2" if i < 15 else "build") if not p2 else ("sac1" if i < 8 else "sac2" if i < 15 else "build")
        if i % 4 == 0 and i < 15:
            b["first"] = hit(root, 55 + 2 * (i // 4), 3)
            b["didge_skip0"] = True
        base = [55, 57, 59, 61, 63, 61, 59, 57][(i // 2) % 8]
        stack = [base + 4 * k for k in range(4)]
        notes = cluster("sb", BIT, stack, range(2, BAR, 8), lambda t, i=i: ramp(40, 68, i, t))
        notes += cluster("sf", FLUTE, [n + 28 for n in stack[:3]], range(6, BAR, 8), lambda t, i=i: ramp(24, 62, i, t) * 0.9)
        if 4 <= i < 15:
            notes += stabs(base, (6, 12) if i % 4 == 0 else (0, 12), 78)
        if i < 15:
            low_d, high_d = DYADS[i // 2]
            b["holds"] = [("eye1", FLUTE, low_d, 1, 15, 40, 70, 2 if p2 else 4), ("eye2", FLUTE, high_d, 1, 15, 36, 64, 2 if p2 else 4)]
            if i < 16:
                b["holds"] += tune(P4_LOW[i], P4_HIGH[i], flute=True, long_tune=True)
        if i >= 15:
            b["roll"] = (4, 12, 30 + 12 * (i - 15), 66 + 8 * (i - 15))
        b["notes"] = notes
        bars.append(b)
    # ------------------------------------------------------------------ P5: the fusion, back in the first scale
    for i in range(20):
        b = {"shift": phase}
        root = pedal["P5"][i]
        b["bass"] = {"segs": [(root, 3)], "fig": fig(plan["P5"][i])}
        b["didge"] = 1.0 if i < 19 else 0.7
        b["drums"] = ("peak2" if p2 else "peak") if i < 19 else "end"
        base = 55 + 2 * ((i // 2) % 6)
        notes = []
        if i < 19:
            notes += cluster("o", BIT, ladder_notes(base, 3), range(3, BAR, 8), lambda t, i=i: ramp(40, 62, i, t) * 1.1)
            notes += cluster("c", FLUTE, ladder_notes(83, 3), range(7, BAR, 8), lambda t, i=i: ramp(26, 62, i, t))
            notes += [(f"hb{k + 1}", BIT, n, t, 88) for t in (0, 3, 6, 9, 12) for k, n in enumerate(ladder_notes(base, 3 if p2 else 2))]
        h0 = 4 if p2 else 7
        if h0 <= i < 19:
            top = ladder(W1, 94 + 2 * ((i // 2) % 3), 1)[0]
            for tick in range(0, BAR, 4 if (i % 4 == 3 or i < 13) else 2):
                notes.append(("ham", FLUTE, top, tick, lerp(54, 100, ((i - h0) * BAR + tick) / ((19 - h0) * BAR))))
        b["notes"] = notes
        b["holds"] = tune(P5_LOW[i], P5_HIGH[i], flute=False, long_tune=True, tri=False)
        if i % 4 == 0 and i < 19:
            b["first"] = hit(root, base)
            b["didge_skip0"] = True
        if i % 4 == 3 and i < 19:
            b["roll"] = (10, 6, 40, 92)
        if i == 19:
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
