#!/usr/bin/env python3
"""Makes colossus_phase1.nbs and colossus_phase2.nbs: "Shoulders of Stone (Boss003)" and "Shoulders of Stone, Unbound (Boss003)", the stone giant in two phases.

    python3 tools/bgm/make_atlas_boss3_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss3_bgm.py outdir     writes them somewhere else

STYLE 2 of the structure guide ("despair and sacred, many stages", BGM t277), class 1 (majesty and despair), 125 BPM (8.333 ticks a second, a tick is a sixteenth, a bar is 16 ticks), C minor.
108 bars = 207 s = 3:27. Time table of the guide -> this song (a bar is 1.92 s):
  P1  0:00  bars  0-15  intro 4 (quarter roots, soft) + 12 bars of IRREGULAR rhythm and dissonance: the bass heads fall on 3+3+3+3+4, 5+5+6, 3+3+2+3+3+2 in turn, clusters with a minor second and a tritone
  P2  0:31  bars 16-39  PSEUDO DOUBLE TEMPO: the snare sits on beats 2 and 4 for four bars, then on EVERY beat (the bar did not change); the tread theme in low brass (guitar + fifth), then an octave up
  P3  1:17  bars 40-61  THE MELODY IS LOST: only the bass (eighths, doubles, triplets, walking) and staccato anvil (cow bell), bone (xylophone), hat and rim; the last bar is a BREAK: all muted, one bell
  P4  1:59  bars 62-85  SACRED: C MAJOR (the mode flips: parallel major), long organ chords (re-struck pling, flute and 8-bit choir swelling), a slow choir tune in whole notes, half-time drums;
                        the last four bars: the melody goes, a snare roll and a bass crescendo, then the bell break
  P5  2:45  bars 86-107 CLIMAX in C minor: P2's fast rhythm (snare on every beat, hats in sixteenths, the sixteenth machine gun) FUSED with P4's sacred chords, the hero tune in whole notes, the highest flute
                        hammered note after note (every 2 ticks, the last 6 bars every tick); the end falls to quarters on G7 and goes back to P1
The bass ladder rises section by section: quarters (intro) / irregular 6-8 a bar (P1) / eighths + doubles (P2) / eighths + doubles + triplets + walks (P3) / quarters -> eighths -> doubles -> a sixteenth
build-up (P4) / sixteenths (P5). Phase 2 "Unbound": the same bars a half step up, everything arrives earlier and thicker (doubles in P1, the 8-bit chip in the brass, sixteenths in P3, root-fifth walking
bass and an octave-above second bass in P5, a rising wail under the hammering). Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from cat1_organ import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 125 / 15
SECTIONS = [("P1", 16), ("P2", 24), ("P3", 22), ("P4", 24), ("P5", 22)]
TITLES = ("Shoulders of Stone (Boss003)", "Shoulders of Stone, Unbound (Boss003)")
FILES = ("colossus_phase1.nbs", "colossus_phase2.nbs")
DESCRIPTIONS = (
    "Stone giant, phase 1: class 1, style 2 (despair and sacred). 125 BPM, C minor: irregular rhythm and dissonance, pseudo double tempo, a melody-less part with anvil and bone staccato, a sacred C major organ chorale, a climax that fuses all of it. Same bars as phase 2.",
    "Stone giant, phase 2: the same bars a half step up, every stage earlier and thicker (doubles from the start, chip in the brass, sixteenth bass in the melody-less part, root-fifth bass and a rising wail in the climax).")

VOL = {"bass": 100, "bass2": 90, "didge": 90, "gtr": 100, "g5": 100, "hit": 100, "bit_m": 100, "pl_m": 100, "fl_m": 90, "org": 60, "cb": 50, "cf": 50, "wail": 90, "ham": 90,
       "cow": 90, "xy": 100, "bell": 60, "rim": 55}
PAN = {"org1": 90, "org2": 110, "cb1": 90, "cb2": 110, "cf1": 95, "cf2": 105, "hit1": 90, "hit2": 110, "cow": 90, "xy": 110}

# ---------------------------------------------------------------------------------------------------------------------------------------------------------------- the material
DISS = [{0, 1, 6, 7}, {0, 3, 6, 9}, {1, 4, 7, 10}, {0, 1, 7, 8}]           # P1: clusters with a minor second and a tritone
P1_ROOTS = [36, 36, 32, 31] + [36, 36, 37, 36, 31, 30, 31, 36, 36, 37, 30, 31]
P1_FIG = ["Q", "Q", "E", "E"] + ["IR1", "IR3", "IR2"] * 4
P1_FIG2 = ["Q", "E", "E_", "S"] + ["IR1x", "IR3x", "IR2x"] * 4
P1_MEL = [None, None, "C3:8 G3:8", "Ab3:6 G3:2 Eb4:8"] + ["C3:3 G3:3 r:3 Gb3:3 G3:4", "r:3 Ab3:3 G3:2 r:2 Eb4:3 Db4:3", "F3:5 r:3 C4:3 Db4:5", "r:5 D4:3 B3:3 D4:5",
                      "C3:3 G3:3 r:3 Gb3:3 G3:4", "r:3 Ab3:3 G3:2 r:2 Eb4:3 Db4:3", "F4:5 r:3 Eb4:3 D4:5", "B3:3 D4:3 F4:5 Gb4:5",
                      "G3:3 C4:3 r:3 Db4:3 C4:4", "r:3 Eb4:3 C4:2 r:2 Ab3:3 G3:3", "F4:5 r:3 Eb4:3 F4:5", "B3:3 D4:3 F4:5 B3:5"]
IRD = ["IR1", "IR3", "IR2"]

T1 = ["C3:8 G3:8", "Ab3:6 G3:2 Eb4:8", "F3:8 C4:8", "D4:4 B3:4 D4:8", "C3:8 G3:8", "Ab3:6 G3:2 Eb4:8", "F4:8 Eb4:4 D4:4", "B3:4 D4:4 F4:8"]
T2B = ["G3:8 C4:8", "Eb4:6 C4:2 Ab3:8", "F4:6 Eb4:2 F4:8", "B3:4 D4:4 F4:8"]
U = ["G4:8 Bb4:8", "D5:8 F5:8", "Eb5:6 D5:2 Eb5:8", "D5:4 B4:4 D5:8", "G4:8 C5:8", "Eb5:8 C5:8", "F5:6 Eb5:2 Db5:8", "B4:4 D5:4 F5:8"]
P2A_CH = "Cm/C2 Ab/Ab1 Fm/F2 G/G1 Cm/C2 Ab/Ab1 Fm/F2 G/G1 Cm/C2 Ab/Ab1 Fm/F2 G/G1".split()
P2B_CH = "Eb/Eb2 Bb/Bb1 Ab/Ab1 G/G1 Cm/C2 Ab/Ab1 Db/Db2 G/G1 Cm/C2 Ab/Ab1 Db/Db2 G/G1".split()
P2_FIG = {0: ["E", "E", "E", "E_", "E", "S", "E", "S", "E", "S", "E_", "T",
              "S", "T", "WU", "T", "S", "WV", "S", "T", "T", "WU", "T", "F16"],
          1: ["S", "E_", "S", "T", "S", "T", "S", "F16", "S", "T", "S", "T",
              "T", "T", "WU", "T", "T", "WV", "T", "F16", "T", "WU", "T", "F16"]}

# P3: a bass that wanders (semitones, tritones: no tonic), and the staccato of anvil (cow bell) and bone (xylophone)
P3_ROOTS = [36, 37, 36, 30, 31, 32, 31, 35, 36, 40, 39, 30, 37, 36, 31, 35, 36, 37, 30, 31, 36]
P3_FIG = {0: ["E", "E_", "E", "S", "E_", "S", "T", "S", "T", "S", "T", "F16", "T", "S", "WU", "T", "T", "WV", "T", "F16", "T"],
          1: ["S", "S", "T", "S", "T", "T", "S", "T", "T", "WU", "T", "F16", "T", "T", "WV", "T", "F16", "T", "T", "M16", "M16"]}
COW = [69, 66, 75, 72]
XY = [91, 97, 92, 98, 90, 96]
STAC = [   # (tick, kind, velocity, step): 5+5+6, 3+3+2+3+3+2, 3+3+3+3+4
    [(0, "cow", 84, 0), (5, "cow", 70, 1), (10, "cow", 78, 2), (13, "xy", 90, 0), (15, "xy", 84, 1), (7, "rim", 62, 0), (3, "hat", 30, 0), (8, "hat", 26, 0)],
    [(0, "cow", 84, 0), (6, "cow", 74, 1), (11, "cow", 78, 2), (3, "xy", 90, 2), (8, "xy", 92, 3), (14, "xy", 86, 4), (3, "rim", 60, 0), (11, "rim", 66, 0)],
    [(0, "cow", 84, 0), (9, "cow", 76, 2), (12, "cow", 70, 1), (3, "xy", 90, 5), (6, "xy", 88, 0), (15, "xy", 84, 3), (2, "hat", 30, 0), (10, "hat", 26, 0)]]
STAC2 = [[(2, "cow", 60, 3), (7, "cow", 56, 3), (12, "cow", 62, 3)] + [(t, "hat", 24, 0) for t in (1, 4, 6, 9, 11, 13, 14)],
         [(4, "cow", 58, 3), (9, "cow", 56, 1), (14, "cow", 60, 3)] + [(t, "hat", 24, 0) for t in (1, 2, 5, 7, 8, 10, 12, 13, 15)],
         [(6, "cow", 58, 3), (3, "cow", 56, 1), (15, "cow", 60, 3)] + [(t, "hat", 24, 0) for t in (1, 4, 5, 7, 8, 11, 13, 14)]]

# P4: C major, sacred
P4_CH = ("C/C2 G/G1 Am/A1 Em/E2 F/F2 C/C2 Dm/D2 G/G1  Am/A1 F/F2 C/C2 G/G1 Am/A1 F/F2 G/G1 G/G1  F/F2 Fm/F2 C/C2 C/C2 G/G1 G/G1 G/G1 G/G1").split()
P4_MEL = ["E5:16", "D5:16", "C5:8 E5:8", "B4:16", "A4:8 C5:8", "G4:8 C5:8", "F4:8 A4:8", "D5:16",
          "C5:16", "A4:16", "G4:8 E5:8", "D5:16", "E5:8 C5:8", "C5:8 A4:8", "B4:8 D5:8", "D5:16",
          "A4:16", "Ab4:16", "G4:16", "E5:16", "D5:16", "D5:16", "D5:16", None]
P4_FIG = {0: ["Q"] * 12 + ["E"] * 4 + ["E_", "E_", "S", "S", "F16", "M16", "M16", None],
          1: ["Q"] * 8 + ["E"] * 8 + ["S"] * 4 + ["F16", "M16", "M16", None]}

# P5: C minor, the fusion
P5_CH = ("Cm/C2 Ab/Ab1 Eb/Eb2 Bb/Bb1 Fm/F2 Db/Db2 G/G1 G/G1  Cm/C2 Eb/Eb2 Ab/Ab1 Fm/F2 Db/Db2 Bb/Bb1 G/G1 G/G1  Cm/C2 Ab/Ab1 Fm/F2 Db/Db2 G/G1 G/G1").split()
P5_CH2 = ("Cm/C2|Cm/G1 Ab/Ab1|Ab/Eb2 Eb/Eb2|Eb/Bb1 Bb/Bb1|Bb/F2 Fm/F2|Fm/C2 Db/Db2|Db/Ab1 G/G1|G/D2 G/G1|G/D2  "
          "Cm/C2|Cm/G1 Eb/Eb2|Eb/Bb1 Ab/Ab1|Ab/Eb2 Fm/F2|Fm/C2 Db/Db2|Db/Ab1 Bb/Bb1|Bb/F2 G/G1|G/D2 G/G1|G/D2  "
          "Cm/C2 Ab/Ab1 Fm/F2 Db/Db2 G/G1 G/G1").split()
P5_MEL = ["C5:16", "Eb5:8 C5:8", "Bb4:8 Eb5:8", "D5:16", "C5:8 F5:8", "F5:8 Db5:8", "D5:8 B4:8", "F5:16",
          "Eb5:16", "Bb4:8 Eb5:8", "Ab4:8 C5:4 Eb5:4", "F5:16", "Db5:8 F5:8", "D5:8 F5:8", "B4:8 D5:8", "F5:16",
          "Eb5:16", "C5:16", "F5:16", "Db5:16", "D5:8 B4:8", "F5:16"]
P5_FIG = ["M16"] * 20 + ["E", "Q"]


def hats(step, loud, soft):
    return [(t, "hat", loud if t % 2 == 0 else soft) for t in range(16) if t % step]


DRUMS = {
    "i0": [(0, "bd", 62)], "i1": [(0, "bd", 66), (8, "bd", 58)], "i2": [(0, "bd", 72), (4, "bd", 44), (8, "bd", 66), (12, "bd", 44)],
    # P1: heads that wander
    "IR1": [(0, "bd", 100), (3, "sn", 62), (6, "bd", 84), (9, "sn", 56), (12, "bd", 92), (14, "bd", 60)],
    "IR2": [(0, "bd", 100), (5, "bd", 86), (7, "sn", 54), (10, "bd", 92), (13, "sn", 66), (15, "bd", 56)],
    "IR3": [(0, "bd", 100), (3, "sn", 60), (6, "bd", 80), (8, "bd", 94), (11, "sn", 66), (14, "bd", 76)],
    "IR1x": [(0, "bd", 100), (3, "bd", 50), (6, "bd", 78), (9, "bd", 50), (12, "bd", 90), (3, "sn", 40), (14, "sn", 44)],
    "IR2x": [(0, "bd", 100), (5, "bd", 80), (7, "bd", 44), (10, "bd", 90), (13, "bd", 60), (13, "sn", 40), (2, "sn", 36)],
    "IR3x": [(0, "bd", 100), (3, "bd", 50), (6, "bd", 78), (8, "bd", 92), (11, "bd", 56), (14, "bd", 70), (11, "sn", 40), (6, "sn", 36)],
    # P2: the snare on 2 and 4, then on every beat
    "back": [(0, "bd", 100), (8, "bd", 94), (4, "sn", 80), (12, "sn", 90), (10, "bd", 36)],
    "dbl": [(0, "bd", 100), (8, "bd", 96), (4, "sn", 92), (12, "sn", 96), (0, "sn", 56), (8, "sn", 60)] + [(t, "hat", 26) for t in (2, 6, 10, 14)],
    "dbl2": [(0, "bd", 100), (4, "bd", 80), (8, "bd", 98), (12, "bd", 80), (14, "bd", 56), (4, "sn", 96), (12, "sn", 100), (0, "sn", 60), (8, "sn", 66)] + hats(4, 32, 22),
    "u_dbl": [(0, "bd", 100), (4, "bd", 70), (8, "bd", 96), (12, "bd", 70), (4, "sn", 94), (12, "sn", 98), (0, "sn", 62), (8, "sn", 66)] + [(t, "hat", 28) for t in (2, 6, 10, 14)],
    "u_dbl2": [(0, "bd", 100), (4, "bd", 90), (8, "bd", 100), (12, "bd", 90), (6, "bd", 50), (14, "bd", 60), (4, "sn", 100), (12, "sn", 100), (0, "sn", 70), (8, "sn", 74)] + hats(4, 38, 26),
    # P4: half time
    "sac0": [(0, "bd", 84), (4, "bd", 52), (8, "bd", 76), (12, "bd", 52)],
    "sac1": [(0, "bd", 92), (4, "bd", 60), (8, "bd", 84), (12, "bd", 60), (12, "sn", 62)],
    "sac2": [(0, "bd", 96), (4, "bd", 70), (8, "bd", 90), (12, "bd", 70), (4, "sn", 66), (12, "sn", 76)] + [(t, "hat", 24) for t in (2, 6, 10, 14)],
    "build": [(0, "bd", 96), (4, "bd", 80), (8, "bd", 96), (12, "bd", 80), (6, "sn", 60), (14, "sn", 70)],
    # P5: P2's rhythm with the sixteenth hats
    "fuse": [(0, "bd", 100), (4, "bd", 86), (8, "bd", 98), (12, "bd", 86), (14, "bd", 58), (4, "sn", 96), (12, "sn", 100), (0, "sn", 62), (8, "sn", 66)] + hats(4, 38, 26),
    "u_fuse": [(0, "bd", 100), (4, "bd", 92), (8, "bd", 100), (12, "bd", 92), (6, "bd", 60), (14, "bd", 66), (15, "bd", 58), (4, "sn", 100), (12, "sn", 100), (0, "sn", 72), (8, "sn", 76), (10, "sn", 56)] + hats(4, 44, 30),
    "end": [(0, "bd", 90), (8, "bd", 80)],
}


def parse_chords(text):
    out = []
    for seg in text.split("|"):
        name, bass = seg.split("/")
        out.append((name, midi(bass)))
    return out


def third_of(name):
    root, intervals = chord_of(name)
    return 3 if 3 in intervals else 4


def pad_vel(lo, hi, i, tick, block=4):
    return lerp(lo, hi, ((i % block) * BAR + tick) / (block * BAR))


def bars_of(phase):
    """The 108 bar descriptions of one phase."""
    bars = []
    st = {}
    p2 = phase == 1
    fl_voice = (FLUTE, 12, 0.55)
    # ------------------------------------------------------------------ P1
    for i in range(16):
        b = {"shift": phase}
        pcs = DISS[i % 4]
        fig = (P1_FIG2 if p2 else P1_FIG)[i]
        gain = [0.9, 0.85, 0.9, 1.0][i] if i < 4 else 1.0
        b["bass"] = {"segs": [(P1_ROOTS[i], 3)], "fig": fig, "gain": gain}
        b["didge"] = 1.0
        if i == 0:
            b["first"] = [("hit1", GUITAR, 48, 0, 100), ("hit2", GUITAR, 55, 0, 100), ("bass", BASS, 36, 0, 100), ("bd", BASEDRUM, 66, 0, 100), ("sn", SNARE, 66, 0, 80)]
            b["didge_skip0"] = True
        b["drums"] = ["i0", "i1", "i2", "i2"][i] if i < 4 else (IRD[(i - 4) % 3] + ("x" if p2 else ""))
        notes = []
        if i >= 2 or p2:
            g = [0, 0, 0.55, 0.8][i] if i < 4 else 0.9
            if g:
                notes += pad_notes(st, "org", pcs, 55, 68, 3, range(2, BAR, 4), "org", PLING, lambda t, g=g, i=i: pad_vel(40, 62, i, t) * g)
        if i >= 3 or (p2 and i >= 2):
            notes += pad_notes(st, "cho", pcs, 79, 90, 2, range(6, BAR, 8), "cf", FLUTE, lambda t, i=i: pad_vel(22, 60, i, t) * (0.6 if i < 4 else 0.9))
        b["notes"] = notes
        if P1_MEL[i]:
            voices = {"gtr": (GUITAR, 0, 1.0)}
            if p2:
                voices["bit_m"] = (BIT, 12, 1.0)
            b["holds"] = hold_events(P1_MEL[i], voices)
            for tick, note, length in melody_events(P1_MEL[i]):
                if 42 <= note + 7 + phase <= 66:
                    b["holds"].append(("g5", GUITAR, note + 7, tick, length, 80, 58, 4))
        if i == 3 or i % 4 == 3:
            b["roll"] = (8, 8, 30, 80)
        if i in (4, 8, 12) and "first" not in b:
            b["first"] = [("hit1", GUITAR, 43, 0, 100), ("hit2", GUITAR, 50, 0, 100), ("bass", BASS, P1_ROOTS[i], 0, 100), ("bd", BASEDRUM, 66, 0, 100)]
            b["didge_skip0"] = True
        bars.append(b)
    # ------------------------------------------------------------------ P2
    for i in range(24):
        b = {"shift": phase}
        chords = P2A_CH if i < 12 else P2B_CH
        segs = parse_chords(chords[i % 12])
        name, root = segs[0]
        b["bass"] = {"segs": [(root, third_of(name))], "fig": P2_FIG[phase][i]}
        b["didge"] = 1.0
        b["drums"] = ("back" if i < 4 and not p2 else ("u_dbl" if p2 else "dbl")) if i < 12 else ("u_dbl2" if p2 else "dbl2")
        if p2 and i < 2:
            b["drums"] = "back"
        pcs = pcs_of(name)
        notes = []
        if i % 12 >= 4 or i >= 12:
            notes += pad_notes(st, "org", pcs, 55, 68, 3, range(2, BAR, 8), "org", PLING, lambda t, i=i: pad_vel(40, 60, i, t) * 0.9)
            notes += pad_notes(st, "cho", pcs, 79, 90, 3 if p2 else 2, range(6, BAR, 8), "cf", FLUTE, lambda t, i=i: pad_vel(22, 62, i, t))
        b["notes"] = notes
        if i < 12:
            text = (T1 + T2B)[i]
            voices = {"gtr": (GUITAR, 0, 1.0)}
            if text and i >= 0:
                b["_g5"] = True
            if p2 and i >= 4:
                voices["bit_m"] = (BIT, 12, 1.0)
        else:
            text = (U + U[4:8])[i - 12]
            voices = {"bit_m": (BIT, -12, 1.0), "pl_m": (PLING, -12, 1.0), "gtr": (GUITAR, -24, 1.0)}
            if p2:
                voices["fl_m"] = (FLUTE, 12, 0.55)
        b["holds"] = hold_events(text, voices)
        if b.pop("_g5", None):
            for tick, note, length in melody_events(text):
                if 42 <= note + 7 + phase <= 66:
                    b["holds"].append(("g5", GUITAR, note + 7, tick, length, 70, 48, 4))
        if i % 8 == 7 or (p2 and i % 4 == 3):
            b["roll"] = (8, 8, 34, 92)
        if i % 12 == 0 and i > 0 or (p2 and i == 0):
            low = root
            while low < 43:
                low += 12
            b["first"] = [("hit1", GUITAR, low, 0, 100), ("hit2", GUITAR, low + 7, 0, 100), ("bass", BASS, root, 0, 100)]
            b["didge_skip0"] = True
        bars.append(b)
    # ------------------------------------------------------------------ P3: the melody is lost
    for i in range(22):
        b = {"shift": phase}
        if i == 21:
            b["first"] = [("bell", BELL, 93, 0, 55, False)]       # the break: one bell, everything else silent
            bars.append(b)
            continue
        root = P3_ROOTS[i]
        b["bass"] = {"segs": [(root, 3)], "fig": P3_FIG[phase][i]}
        notes = []
        for tick, kind, vel, step in (STAC[i % 3] + (STAC2[i % 3] if p2 or i >= 12 else [])):
            if kind == "cow":
                notes.append(("cow", COW_BELL, COW[step % 4], tick, vel))
            elif kind == "xy":
                notes.append(("xy", XYLOPHONE, XY[step % 6], tick, vel))
            else:
                b.setdefault("extra_drums", []).append((tick, kind, vel))
        for tick, kind, vel, step in STAC[i % 3]:
            if kind == "cow":
                b.setdefault("extra_drums", []).append((tick, "bd", 76 if tick else 100))
        if i % 4 == 0 and i > 0 or i == 0:
            low = root
            while low < 43:
                low += 12
            b["first"] = [("hit1", GUITAR, low, 0, 100), ("hit2", GUITAR, low + 6, 0, 100), ("bass", BASS, root, 0, 100), ("sn", SNARE, 66, 0, 90)]
        b["notes"] = notes
        if i == 0:
            b["first"] = [("hit1", GUITAR, 43, 0, 100)] if False else []
        bars.append(b)
    # ------------------------------------------------------------------ P4: sacred, C major
    for i in range(24):
        b = {"shift": phase}
        if i == 23:
            b["first"] = [("bell", BELL, 93, 0, 55, False)]
            bars.append(b)
            continue
        segs = parse_chords(P4_CH[i])
        name, root = segs[0]
        fig = P4_FIG[phase][i]
        gain = {20: 0.6, 21: 0.8, 22: 1.0}.get(i, 1.0)
        b["bass"] = {"segs": [(root, third_of(name))], "fig": fig, "gain": gain}
        b["drums"] = "sac0" if i < 8 else "sac1" if i < 16 else "sac2" if i < 20 else "build"
        if p2 and i >= 4:
            b["drums"] = "sac1" if i < 12 else "sac2" if i < 20 else "build"
        pcs = pcs_of(name)
        notes = pad_notes(st, "sorg", pcs, 55, 68, 3, range(2, BAR, 8), "org", PLING, lambda t, i=i: pad_vel(40, 68, i, t))
        notes += pad_notes(st, "scho", pcs, 79, 98 if p2 else 90, 4 if p2 else 3, range(6, BAR, 8), "cf", FLUTE, lambda t, i=i: pad_vel(22, 66, i, t), wide=p2)
        notes += pad_notes(st, "sbit", pcs, 62, 74, 2, range(6, BAR, 8), "cb", BIT, lambda t, i=i: pad_vel(18, 56, i, t))
        b["notes"] = notes
        b["didge"] = 0.9
        if i % 4 == 0 and i < 20:
            low = root
            while low < 43:
                low += 12
            b["first"] = [("hit1", GUITAR, low, 0, 100), ("hit2", GUITAR, low + 7, 0, 100), ("bass", BASS, root, 0, 100), ("sn", SNARE, 66, 0, 90)]
            b["didge_skip0"] = True
        if P4_MEL[i]:
            voices = {"pl_m": (PLING, -12, 1.0), "bit_m": (BIT, -12, 1.0), "fl_m": (FLUTE, 12, 0.9)}
            if i < 20:
                b["holds"] = hold_events(P4_MEL[i], voices, swell=1.12)
        if i >= 20:
            b["roll"] = (4, 12, 30 + 15 * (i - 20), 70 + 15 * (i - 20))
        bars.append(b)
    # ------------------------------------------------------------------ P5: fusion
    chords = P5_CH2 if p2 else P5_CH
    for i in range(22):
        b = {"shift": phase}
        segs = parse_chords(chords[i])
        name0, root0 = segs[0]
        fig = P5_FIG[i]
        b["bass"] = {"segs": [(r, third_of(n)) for n, r in segs], "fig": fig, "pop": p2}
        b["didge"] = 1.0 if i < 20 else 0.7
        b["drums"] = ("u_fuse" if p2 else "fuse") if i < 20 else "end"
        pcs = pcs_of(name0)
        notes = []
        if i < 21:
            for s, (n, r) in enumerate(segs):
                pc = pcs_of(n)
                seg_len = BAR // len(segs)
                notes += pad_notes(st, "org", pc, 55, 68, 3, range(s * seg_len + 2, (s + 1) * seg_len, 8), "org", PLING, lambda t, i=i: pad_vel(40, 62, i, t) * 1.1)
                notes += pad_notes(st, "cho", pc, 79, 98 if p2 else 90, 4 if p2 else 3, range(s * seg_len + 6, (s + 1) * seg_len, 8), "cf", FLUTE, lambda t, i=i: pad_vel(22, 72, i, t), wide=p2)
        h0 = 4 if p2 else 8
        if h0 <= i < 21:
            hi = voicing(pcs, 90, 99, 1)[0]
            every = 1 if i >= h0 + 7 else 2
            for tick in range(0, BAR, every):
                notes.append(("ham", FLUTE, hi, tick, lerp(48, 100, ((i - h0) * BAR + tick) / ((21 - h0) * BAR))))
            if every == 1:
                b["didge"] = 0.0
        b["notes"] = notes
        if True:
            voices = {"pl_m": (PLING, -12, 1.0), "bit_m": (BIT, -12, 1.0), "gtr": (GUITAR, -12, 1.0)}
            b["holds"] = hold_events(P5_MEL[i], voices, swell=1.12)
        hit_every = 4
        if i % hit_every == 0 and i < 20:
            low = root0
            while low < 43:
                low += 12
            b["first"] = [("hit1", GUITAR, low, 0, 100), ("hit2", GUITAR, low + 7, 0, 100), ("bass", BASS, root0, 0, 100)]
            b["didge_skip0"] = True
        if i % 4 == 3 and i < 20:
            b["roll"] = (8, 8, 34, 92)
        if i == 21:
            b["roll"] = (8, 8, 30, 88)
        if p2 and 12 <= i < 20:
            for k in range(2):
                step = (i - 12) * 2 + k
                for tick in range(k * 8 + 1, k * 8 + 8, 2):
                    b["notes"].append(("wail", FLUTE, 78 + step, tick, lerp(55, 100, (step * 4 + (tick - k * 8) // 2) / 64)))
        bars.append(b)
    return bars


def build(phase):
    sc = Score(TPS, SECTIONS, shift=0)
    bars = bars_of(phase)
    assert len(bars) == sc.bars, (len(bars), sc.bars)
    # the 'extra_drums' of P3 and the drum-less didgeridoo hit bars are turned into plain notes
    drums = dict(DRUMS)
    for g, b in enumerate(bars):
        if b.get("extra_drums"):
            name = f"x{g}"
            drums[name] = b["extra_drums"]
            b["drums"] = name
        if b.get("didge_skip0"):
            b["didge"] = b.get("didge", 1.0)
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
