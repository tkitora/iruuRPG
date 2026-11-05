#!/usr/bin/env python3
"""Makes unkeyed_phase1.nbs and unkeyed_phase2.nbs: "Eyes Without a Key (Boss009)" and "..., Unwritten (Boss009)", a boss beyond human understanding.

    python3 tools/bgm/make_unkeyed_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_unkeyed_bgm.py outdir     writes them somewhere else

STYLE 2 of the structure guide ("despair and sacred, many stages", BGM t277) in its own way: no tonic (whole-tone), 125 BPM (8.333 ticks a second, a tick is a sixteenth, a bar is 16 ticks), the whole song
counts 6 + 6 + 4. Nothing like the stone giant (Boss003: C minor, brass, snare backbeat): no pling and no guitar here, no backbeat, the bass is a whole-tone ORBIT. 96 bars = 184 s = 3:04.
  P1  0:00  bars  0-13  intro 4 (the pedal alone, quarters) + 10 bars: the pedal in eighths accented 0-6-12, whole-tone clusters that rise, two flutes a tritone apart that blink (the eyes); the tune comes at the end
  P2  0:27  bars 14-33  PSEUDO DOUBLE TEMPO: the bass drum counts 0-6-12 for four bars, then 0-3-6-9-12 with a clap on 3 and 9 (the counting halves); the tune (harp, reed, flute); bass: doubles, wobbling triplets
  P3  1:05  bars 34-53  THE MELODY IS LOST (native state): no clusters, no tune - only the bass (walking whole-tone steps, doubles, triplets, then the orbit) and staccato anvil (cow bell) and bone (xylophone) in
                        TRITONES, hats and rim; the last bar is a BREAK: everything muted, one bell
  P4  1:43  bars 54-73  SACRED: the mode flips to the OTHER whole-tone scale (everything a half step up), long organ-like chords (a stack of augmented thirds re-struck, a flute choir swelling), the tune in
                        WHOLE notes, held tritone pairs climbing, half-time drums; the last four bars: no melody, a snare roll and a bass crescendo, then the bell break
  P5  2:22  bars 74-95  CLIMAX back in the first scale: the sixteenth orbit (0,+4,+6,+8,+6,+4 ...) + P2's counting + P4's long chords + the tune in whole notes; the highest flute hammered (every 2 ticks, the last 8
                        bars every tick); the end falls to quarters and goes back to the pedal of P1
The bass ladder: quarters (intro) / eighths (P1) / doubles and triplets (P2) / walks, doubles, triplets, orbit at the end (P3) / quarters -> eighths -> doubles -> sixteenth crescendo (P4) / sixteenth orbit (P5).
Phase 2 "Unwritten": the same bars in the OTHER whole-tone scale (a half step up; the sacred part flips back to the first), the pedal RISES instead of falling, the doubles and the orbit come earlier, a second voice a
tritone away (iron xylophone) doubles the tune, the cluster is torn by flutes of the other scale, the hammering starts earlier.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat1_organ import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 125 / 15
SECTIONS = [("P1", 14), ("P2", 20), ("P3", 20), ("P4", 20), ("P5", 22)]
TITLES = ("Eyes Without a Key (Boss009)", "Eyes Without a Key, Unwritten (Boss009)")
FILES = ("unkeyed_phase1.nbs", "unkeyed_phase2.nbs")
DESCRIPTIONS = (
    "Boss without a key, phase 1: class 1, style 2. 125 BPM, no tonic, counted 6+6+4: whole-tone clusters over a falling pedal, a melody-less part of bass orbit and tritone anvil/bone staccato, a sacred part in the other whole-tone scale, a climax that fuses the sixteenth orbit with long chords. Same bars as phase 2.",
    "Boss without a key, phase 2: the same bars in the other whole-tone scale, a rising pedal, the doubles and the orbit earlier, a second voice a tritone away, clusters torn by a half step, earlier hammering.")

W0 = {0, 2, 4, 6, 8, 10}
W1 = {1, 3, 5, 7, 9, 11}
VOL = {"bass": 100, "bass2": 90, "didge": 95, "harp": 100, "bit_m": 100, "fl_m": 90, "tri_i": 100, "hb": 100, "o": 60, "p": 55, "c": 50, "r": 45, "eye": 50, "cow": 90, "xy": 100, "bell": 60,
       "ham": 90, "sb": 60, "sf": 50}
PAN = {"o1": 90, "o2": 110, "o3": 100, "o4": 85, "p1": 90, "p2": 110, "c1": 95, "c2": 105, "eye1": 80, "eye2": 120, "tri_i": 110, "cow": 90, "xy": 110}

U1 = ["E4:6 F#4:6 G#4:4", "A#4:6 E5:6 D5:4", "C5:6 D5:6 E5:4", "F#5:16"]
U2 = ["A#4:6 G#4:6 F#4:4", "E4:6 A#4:6 G#4:4", "A#4:6 C5:6 D5:4", "E5:16"]
U3 = ["F#4:6 G#4:6 A#4:4", "E5:6 A#4:6 C5:4", "D5:6 E5:6 F#5:4", "A#4:4 C5:4 F#5:8"]
L1 = ["E4:12 G#4:4", "A#4:16", "E5:10 D5:6", "C5:16"]
L2 = ["A#4:12 G#4:4", "F#4:16", "C5:10 D5:6", "E5:16"]
L3 = ["F#4:8 G#4:8", "A#4:16", "E5:12 F#5:4", "F#5:16"]
L4 = ["A#4:16", "C5:12 D5:4", "E5:16", "F#5:16"]
L5 = ["F#5:8 E5:8", "D5:8 C5:8", "A#4:12 C5:4", "A#4:4 C5:4 F#5:8"]
R = "r:16"
MEL_SHIFT = -6          # the tune is written from E4; sounding a whole tone lower keeps its top (F#5 + 1 in phase 2) inside the harp
MEL = {"P1": [R] * 4 + U1 + U2 + [R] * 2, "P2": U1 + U2 + U3 + U2 + U1, "P4": L1 + L2 + L3 + L4 + [R] * 4, "P5": L1 + L2 + L3 + L4 + L5 + ["F#5:16", R]}


def names(text):
    return [midi(n) for n in text.split()]


def steps(text, each):
    out = []
    for n in names(text):
        out += [n] * each
    return out


PEDAL = {
    0: {"P1": names("E2 E2 E2 E2") + names("E2 E2 E2 E2 E2 E2 D2 D2 D2 D2"),
        "P2": steps("C2 A#1 G#1 F#1 D2", 4),
        "P3": names("C2 F#1 C2 C#2 F#1 G1 C2 F#1 D2 G#1 D2 A1 C2 F#1 C#2 G1 C2 F#1 G#1 C2"),
        "P4": steps("F#1 G#1 A#1 C2 D2", 4),
        "P5": steps("E2 D2 C2 A#1 G#1 E2 D2 C2 A#1 G#1", 2) + names("E2 E2"),
        },
    1: {"P1": names("F#1 F#1 F#1 F#1") + names("F#1 F#1 F#1 F#1 F#1 F#1 G#1 G#1 G#1 G#1"),
        "P2": steps("A#1 C2 D2 E2 C2", 4),
        "P3": names("C2 F#1 C2 C#2 F#1 G1 C2 F#1 D2 G#1 D2 A1 C2 F#1 C#2 G1 C2 F#1 G#1 C2"),
        "P4": steps("F#1 G#1 A#1 C2 D2", 4),
        "P5": steps("F#1 G#1 A#1 C2 D2 F#1 G#1 A#1 C2 D2", 2) + names("D2 D2"),
        }}

ORBIT6 = [0, 4, 6, 8, 6, 4]
ORBIT4 = [0, 4, 6, 8]
GROUP = lambda t: 92 if t == 0 else 80 if t in (6, 12) else 44          # noqa: E731  (the accents on 0, 6 and 12)
FIGURES.update({
    "WQ": [(0, 0, 70), (4, 0, 44), (8, 0, 60), (12, 0, 44)],
    "WE": [(t, 0, GROUP(t)) for t in range(0, 16, 2)],
    "WE_": [(t, 0, GROUP(t)) for t in range(0, 16, 2)] + [(15, 0, 48)],
    "WS": [(t, 0, GROUP(t)) for t in range(0, 16, 2)] + [(1, 0, 40), (7, 0, 44), (13, 0, 42), (15, 0, 46)],
    "WT": [(b, 0, 90 if b == 0 else 78) for b in (0, 4, 8, 12)] + [(b + 1, 2, 46) for b in (0, 4, 8, 12)] + [(b + 3, 4, 52) for b in (0, 4, 8, 12)],
    "WWU": [(0, 0, 92), (2, 0, 50), (4, 4, 66), (6, 4, 78), (8, 6, 60), (10, 6, 50), (12, 8, 80), (14, 10, 60), (1, 0, 40), (7, 4, 46), (13, 8, 46)],
    "WWV": [(0, 0, 92), (2, 0, 50), (4, 6, 66), (6, 6, 78), (8, 12, 60), (10, 6, 50), (12, 4, 80), (14, 2, 60), (1, 0, 40), (7, 6, 46), (13, 4, 46)],
    "WF16": [(0, 0, 92), (2, 0, 50), (4, 0, 66), (6, 0, 78)] + [(8 + k, o, 56 + 4 * k) for k, o in enumerate((0, 2, 4, 6, 4, 2, 4, 0))],
    "WM16": [(k, o, (94 if k == 0 else 82 if k in (6, 12) else 60 if o == 8 else 50)) for k, o in enumerate(ORBIT6 + ORBIT6 + ORBIT4)],
})
FIG = {
    0: {"P1": [None, "WQ", "WQ", "WQ"] + ["WE"] * 6 + ["WE_", "WE", "WS", "WE_"],
        "P2": ["WE", "WE_", "WE", "WS", "WS", "WE_", "WS", "WT", "WS", "WT", "WS", "WT", "WT", "WS", "WT", "WF16", "WT", "WS", "WT", "WF16"],
        "P3": ["WS", "WT", "WWU", "WT", "WS", "WWV", "WT", "WF16", "WT", "WWU", "WT", "WWV", "WF16", "WT", "WWU", "WT", "WF16", "WT", "WM16", None],
        "P4": ["WQ"] * 8 + ["WE"] * 8 + ["WF16", "WM16", "WM16", None],
        "P5": ["WM16"] * 20 + ["WE", "WQ"]},
    1: {"P1": [None, "WQ", "WQ", "WQ"] + ["WE_"] * 2 + ["WE", "WS"] * 2 + ["WS", "WE_", "WS", "WT"],
        "P2": ["WS", "WE_", "WS", "WT", "WS", "WT", "WS", "WT", "WT", "WS", "WT", "WF16", "WT", "WT", "WT", "WF16", "WT", "WT", "WT", "WF16"],
        "P3": ["WS", "WT", "WWU", "WT", "WT", "WWV", "WT", "WF16", "WT", "WWU", "WT", "WWV", "WF16", "WT", "WM16", "WM16", "WF16", "WM16", "WM16", None],
        "P4": ["WQ"] * 4 + ["WE"] * 8 + ["WS"] * 4 + ["WF16", "WM16", "WM16", None],
        "P5": ["WM16"] * 20 + ["WE", "WQ"]}}
STAC = [   # (tick, kind, velocity, step): the counting 6 + 6 + 4, the anvil and the bone in tritones
    [(0, "cow", 84, 0), (6, "cow", 72, 1), (12, "cow", 78, 0), (3, "xy", 90, 0), (9, "xy", 92, 1), (14, "xy", 84, 2), (2, "hat", 28, 0), (8, "hat", 26, 0)],
    [(0, "cow", 84, 2), (6, "cow", 74, 3), (10, "cow", 70, 2), (3, "xy", 90, 3), (9, "xy", 88, 4), (15, "xy", 84, 5), (4, "rim", 62, 0), (12, "rim", 66, 0)],
    [(0, "cow", 84, 0), (6, "cow", 72, 1), (12, "cow", 78, 3), (1, "xy", 86, 4), (7, "xy", 90, 5), (13, "xy", 86, 0), (3, "hat", 26, 0), (9, "hat", 26, 0)]]
STAC2 = [[(t, "hat", 24, 0) for t in (1, 4, 5, 7, 10, 11, 13, 15)],
         [(t, "hat", 24, 0) for t in (1, 2, 5, 8, 10, 13, 14)],
         [(t, "hat", 24, 0) for t in (2, 4, 5, 8, 10, 11, 14, 15)]]
COW = [72, 78, 74, 80]
XY = [90, 96, 92, 98, 94, 100]
DYADS = [(79, 85), (82, 88), (85, 91), (86, 92), (88, 94), (91, 97), (79, 85), (82, 88), (85, 91), (86, 92)]


def hats(ticks, loud, soft):
    return [(t, "hat", loud if t % 2 == 0 else soft) for t in ticks]


DRUMS = {
    "i0": [(0, "bd", 100), (6, "bd", 64), (12, "bd", 70)], "i1": [(0, "bd", 90), (6, "bd", 66), (12, "bd", 76), (9, "sn", 50)], "i2": [(0, "bd", 96), (6, "bd", 72), (12, "bd", 80), (3, "sn", 50), (9, "sn", 56)],
    "g6": [(0, "bd", 100), (6, "bd", 80), (12, "bd", 86), (3, "sn", 56), (9, "sn", 60), (14, "sn", 52)],
    "g6s": [(0, "bd", 100), (6, "bd", 86), (12, "bd", 90), (3, "sn", 66), (9, "sn", 70), (14, "sn", 60), (15, "bd", 50)],
    # P2: the counting halves
    "d3": [(0, "bd", 100), (3, "bd", 56), (6, "bd", 84), (9, "bd", 56), (12, "bd", 92), (3, "sn", 70), (9, "sn", 76), (14, "sn", 44)] + [(t, "hat", 24) for t in (2, 8, 10)],
    "d3x": [(0, "bd", 100), (3, "bd", 66), (6, "bd", 90), (9, "bd", 66), (12, "bd", 98), (14, "bd", 50), (3, "sn", 84), (9, "sn", 88), (14, "sn", 50)] + hats((1, 2, 4, 5, 7, 8, 10, 11, 13), 34, 24),
    # P4: half time
    "sac0": [(0, "bd", 90), (6, "bd", 58), (12, "bd", 66)],
    "sac1": [(0, "bd", 96), (6, "bd", 66), (12, "bd", 74), (9, "sn", 56)],
    "sac2": [(0, "bd", 100), (6, "bd", 78), (12, "bd", 86), (3, "sn", 62), (9, "sn", 66), (14, "sn", 56)] + [(t, "hat", 24) for t in (2, 8, 11)],
    "build": [(0, "bd", 100), (6, "bd", 84), (12, "bd", 92), (3, "sn", 70), (9, "sn", 74), (14, "sn", 66)],
    # P5: the sixteenth orbit
    "peak": [(0, "bd", 100), (6, "bd", 94), (12, "bd", 98), (3, "sn", 82), (9, "sn", 88), (14, "sn", 66)] + hats((1, 2, 4, 5, 7, 8, 10, 11, 13, 15), 38, 26),
    "peak2": [(0, "bd", 100), (6, "bd", 98), (12, "bd", 100), (8, "bd", 60), (15, "bd", 54), (3, "sn", 90), (9, "sn", 94), (14, "sn", 76)] + hats((1, 2, 4, 5, 7, 10, 11, 13), 44, 30),
    "end": [(0, "bd", 90), (8, "bd", 70)],
}


def ladder_notes(base, count):
    return ladder(W0, base, count)


def ramp(lo, hi, i, tick, block=4):
    return lerp(lo, hi, ((i % block) * BAR + tick) / (block * BAR))


def cluster(prefix, inst, notes, ticks, vel_of):
    return [(f"{prefix}{k + 1}", inst, note, tick, vel_of(tick)) for tick in ticks for k, note in enumerate(notes)]


def bars_of(phase):
    bars = []
    p2 = phase == 1
    pedal = PEDAL[phase]
    fig = FIG[phase]
    tune_voices = {"harp": (HARP, 0, 1.0), "bit_m": (BIT, 0, 1.0)}

    def tune(text, extra_fl=False, long_tune=False):
        voices = dict(tune_voices)
        if extra_fl:
            voices["fl_m"] = (FLUTE, 24, 0.9)
        events = []
        for tick, note, length in melody_events(text):
            a = 100 if tick == 0 else 92
            for layer, (inst, off, vol) in voices.items():
                events.append((layer, inst, note + MEL_SHIFT + off, tick, length, a * vol * (0.88 if long_tune and length >= 10 else 1.0), a * vol * (1.0 if long_tune else 0.85), 4))
            if p2:
                events.append(("tri_i", IRON_XYLOPHONE, note + MEL_SHIFT - 6, tick, length, a * 0.9, a * 0.7, 4))
        return events

    def eyes(i, ticks=(1, 5, 9, 13), v=1.0):
        return [("eye1" if k % 2 == 0 else "eye2", FLUTE, 84 if k % 2 == 0 else 90, t, 24 + 16 * v + (6 if t == 9 else 0)) for k, t in enumerate(ticks)]

    def stabs(base, ticks, vel=84):
        """Reed cluster stabs on the heads of the counting (0, 6, 12): with the bass and the drums they are the ensemble hits."""
        return [(f"hb{k + 1}", BIT, n, t, vel) for t in ticks for k, n in enumerate(ladder_notes(base, 3))]

    def hit(root, base=55, stab=3):
        """The ensemble hit: pedal, bass drum, snare and a cluster of the reed struck together at full strength."""
        return [("bass", BASS, root, 0, 100), ("bd", BASEDRUM, 66, 0, 100), ("sn", SNARE, 66, 0, 90)] + [(f"hb{k + 1}", BIT, n, 0, 92) for k, n in enumerate(ladder_notes(base, stab))]

    # ------------------------------------------------------------------ P1
    for i in range(14):
        b = {"shift": phase}
        root = pedal["P1"][i]
        gain = [0.9, 0.85, 0.9, 1.0][i] if i < 4 else 1.0
        b["bass"] = {"segs": [(root, 3)], "fig": fig["P1"][i] or "WQ", "gain": gain}
        b["didge"] = 1.0
        if i == 0 or i in (4, 8, 12):
            b["first"] = hit(root, 55 + 2 * (i // 2), 3)
            b["didge_skip0"] = True
        b["drums"] = ["i0", "i1", "i2", "i2"][i] if i < 4 else ("g6s" if i >= 10 else "g6")
        notes = []
        if i >= 2:
            n = 4 if i >= 3 else 3
            base = 55 + 2 * min(i, 6)
            g = {2: 0.55, 3: 0.8}.get(i, 1.0)
            notes += cluster("o", BIT, ladder_notes(base, n), range(3, BAR, 8), lambda t, i=i, g=g: ramp(40, 62, i, t) * g * 1.1)
        if i >= 3:
            notes += cluster("c", FLUTE, ladder_notes(85 - 2 * min(i // 2, 3), 3), range(7, BAR, 8), lambda t, i=i: ramp(26, 62, i, t) * (0.6 if i < 5 else 1.0))
        if p2 and i >= 2:
            notes += cluster("p", BIT, ladder_notes(55 + 2 * min(i, 6), 3), range(5, BAR, 8), lambda t, i=i: ramp(26, 50, i, t) * 0.8)
        if i >= 1:
            notes += eyes(i, ticks=(1, 9), v=0.5 + min(i, 8) * 0.07)
        if i >= 8:
            notes += stabs(55 + 2 * (i // 4), (6, 12))
        b["notes"] = notes
        if MEL["P1"][i] != R:
            b["holds"] = tune(MEL["P1"][i])
        if i == 3 or i == 13:
            b["roll"] = (8, 8, 34, 92)
        bars.append(b)
    # ------------------------------------------------------------------ P2: the counting halves
    for i in range(20):
        b = {"shift": phase}
        root = pedal["P2"][i]
        b["bass"] = {"segs": [(root, 3)], "fig": fig["P2"][i]}
        b["didge"] = 1.0
        if i < 4 and not p2:
            b["drums"] = "g6s"
        elif p2 and i < 2:
            b["drums"] = "g6s"
        else:
            b["drums"] = "d3x" if p2 or i >= 12 else "d3"
        base = 55 + 2 * ((i // 4) % 5)
        notes = cluster("o", BIT, ladder_notes(base, 3), range(3, BAR, 8), lambda t, i=i: ramp(40, 62, i, t) * 1.1)
        notes += cluster("c", FLUTE, ladder_notes(79 + 2 * (i // 4), 3), range(7, BAR, 8), lambda t, i=i: ramp(26, 62, i, t))
        if p2:
            notes += cluster("r", FLUTE, ladder(W1, 85 + 3 * (i // 4) % 6, 2), range(5, BAR, 8), lambda t, i=i: ramp(28, 60, i, t)) if i >= 8 else []
        notes += eyes(i, ticks=(1, 9), v=1.0)
        notes += stabs(base, (6, 12) if i % 4 == 0 else (0, 6, 12) if i >= 4 else (6, 12))
        b["notes"] = notes
        b["holds"] = tune(MEL["P2"][i], extra_fl=i >= 8)
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
        b["bass"] = {"segs": [(root, 3)], "fig": fig["P3"][i]}
        notes = []
        extra = []
        for tick, kind, vel, step in (STAC[i % 3] + (STAC2[i % 3] if p2 or i >= 10 else [])):
            if kind == "cow":
                notes.append(("cow", COW_BELL, COW[step % 4], tick, vel))
            elif kind == "xy":
                notes.append(("xy", XYLOPHONE, XY[step % 6], tick, vel))
            else:
                extra.append((tick, kind, vel))
        if p2:
            notes.append(("cow", COW_BELL, 66 + 2 * (i % 5), 2 + (i % 3) * 4 + 1, 52))
        for tick, kind, vel, step in STAC[i % 3]:
            if kind == "cow":
                extra.append((tick, "bd", 80 if tick else 100))
        if i % 4 == 0:
            b["first"] = hit(root, 55 + 2 * (i // 4), 3)
        notes += stabs(55 + 2 * (i // 4), (6, 12) if i % 4 == 0 else (0, 6, 12), 80)
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
        gain = {16: 0.6, 17: 0.8, 18: 1.0}.get(i, 1.0)
        b["bass"] = {"segs": [(root, 3)], "fig": fig["P4"][i], "gain": gain}
        b["didge"] = 0.9
        if i % 4 == 0 and i < 16:
            b["first"] = hit(root, 54 + 2 * (i // 4), 3)
            b["didge_skip0"] = True
        b["drums"] = ("sac0" if i < 6 else "sac1" if i < 12 else "sac2" if i < 16 else "build") if not p2 else ("sac1" if i < 8 else "sac2" if i < 16 else "build")
        base = 54 + 2 * (i // 4)
        stack = [base + 4 * k for k in range(4)]
        notes = cluster("sb", BIT, stack, range(2, BAR, 8), lambda t, i=i: ramp(38, 68, i, t))
        notes += cluster("sf", FLUTE, [n + 28 for n in stack[:3]], range(6, BAR, 8), lambda t, i=i: ramp(22, 62, i, t) * 0.9)
        if i < 16:
            low, high_note = DYADS[i // 2]
            b["holds"] = [("eye1", FLUTE, low - 12 + 12, 1, 15, 40, 70, 2 if p2 else 4), ("eye2", FLUTE, high_note, 1, 15, 36, 64, 2 if p2 else 4)]
            b["holds"] += tune(MEL["P4"][i], extra_fl=True, long_tune=True) if MEL["P4"][i] != R else []
        if i >= 16:
            b["roll"] = (4, 12, 30 + 20 * (i - 16), 70 + 15 * (i - 16))
        if 4 <= i < 16:
            notes += stabs(54 + 2 * (i // 4), (6, 12) if i % 4 == 0 else (0, 12), 78)
        b["notes"] = notes
        bars.append(b)
    # ------------------------------------------------------------------ P5: the fusion, back in the first scale
    for i in range(22):
        b = {"shift": phase}
        root = pedal["P5"][i]
        b["bass"] = {"segs": [(root, 3)], "fig": fig["P5"][i], "pop": False}
        b["didge"] = 1.0 if i < 20 else 0.7
        b["drums"] = ("peak2" if p2 else "peak") if i < 20 else "end"
        base = 55 + 2 * ((i // 2) % 6)
        notes = []
        if i < 21:
            notes += cluster("o", BIT, ladder_notes(base, 3), range(3, BAR, 8), lambda t, i=i: ramp(40, 62, i, t) * 1.1)
            notes += cluster("c", FLUTE, ladder_notes(83, 3), range(7, BAR, 8), lambda t, i=i: ramp(26, 62, i, t))
            if p2:
                notes += cluster("r", FLUTE, ladder(W1, 86, 2), range(1, BAR, 8), lambda t, i=i: ramp(28, 60, i, t))
            notes += eyes(i, ticks=(2, 10), v=1.2)
            notes += stabs(base, (6, 12) if i % 4 == 0 else (0, 6, 12), 88)
        h0 = 4 if p2 else 8
        if h0 <= i < 21:
            top = ladder(W0, 94 + 2 * ((i // 2) % 3), 1)[0]
            every = 1 if i >= h0 + 8 else 2
            for tick in range(0, BAR, every):
                notes.append(("ham", FLUTE, top, tick, lerp(48, 100, ((i - h0) * BAR + tick) / ((21 - h0) * BAR))))
            if every == 1:
                b["didge"] = 0.0
        b["notes"] = notes
        if MEL["P5"][i] != R:
            b["holds"] = tune(MEL["P5"][i], long_tune=True)
        hit_every = 4
        if i % hit_every == 0 and i < 20:
            b["first"] = hit(root)
            b["didge_skip0"] = True
        if i % 4 == 3 and i < 20:
            b["roll"] = (8, 8, 34, 92)
        if i == 21:
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
