#!/usr/bin/env python3
"""Makes unkeyed_phase1.nbs and unkeyed_phase2.nbs: "Eyes Without a Key (Boss009)" and "..., Unwritten (Boss009)", a boss beyond human understanding.

    python3 tools/bgm/make_unkeyed_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_unkeyed_bgm.py outdir     writes them somewhere else

Class 1 again ("overwhelming majesty and despair") but the other face of it: not a giant of stone but a being beyond understanding, the law of the world itself - a song with NO TONIC, and (BGM t276)
on the sixteenth-note guide: 125 BPM (8.333 ticks a second, a tick is a sixteenth, a bar is 16 ticks = the engine's bar line). It is built to be nothing like the stone giant (Boss003: C minor, brass,
organ chords, a snare backbeat, a bass that stays on its root): here there is no pling and no guitar, no backbeat, the bass is a SPINNING ORBIT of whole-tone steps and the whole song counts 6 + 6 + 4.
  tune    harp + 8-bit reed + flute, whole-tone scale, steps and then a TRITONE leap; short phrases 6+6+4 in A, WHOLE and HALF notes (16, 12+4, 10+6) in the peak
  pedal   the root falls a whole tone every four bars (every two in the peak, twice through five notes) like gravity; in phase 2 it rises
  drums   the bass drum counts 0, 6, 12 of every bar (never 1-2-3-4), a snare on 3 / 9 / 14 in the peak, hats in sixteenths
  bass    intro: quarter roots, soft / A: eighths, accents on 0-6-12 / A2: doubles and wobbling triplets (root, +2, +4: the pitch leans) / B: the line walks the whole-tone steps up and back, sixteenths in the
          last bars of each block / PEAK: the full sixteenth ORBIT, six notes then six then four (root +4 +6 +8 +6 +4 ...): a machine that spins; CALM: eighths falling back to quarters
  voices  a cluster of whole-tone neighbours in the 8-bit reed that rises, a flute cluster, a pair of flutes a tritone apart that blink (the eyes) and in B hold and climb.
Structure (68 bars = 130 s): intro 4, A 16, A2 8, B 12, PEAK 20 (from 59 %), CALM 8.
Phase 2 "Unwritten": the same bars in the OTHER whole-tone scale (a half step up), the pedal RISES, the pulse arrives early (drums and doubles from the first bar of A, triplets in A), a SECOND VOICE a tritone away
(iron xylophone) doubles the tune, the cluster is struck twice as often, and the cluster is torn by flutes of the other scale (a half step off): the rules are being rewritten.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat1_organ import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 125 / 15
SECTIONS = [("intro", 4), ("A", 16), ("A2", 8), ("B", 12), ("PEAK", 20), ("CALM", 8)]
TITLES = ("Eyes Without a Key (Boss009)", "Eyes Without a Key, Unwritten (Boss009)")
FILES = ("unkeyed_phase1.nbs", "unkeyed_phase2.nbs")
DESCRIPTIONS = (
    "Boss without a key, phase 1: class 1 (majesty and despair), the god beyond understanding. 125 BPM, no tonic: whole-tone clusters over a falling pedal, a sixteenth whole-tone orbit in the bass, counted 6+6+4, a harp tune with tritone leaps in long notes. Same bars as phase 2.",
    "Boss without a key, phase 2: the same bars in the other whole-tone scale, a rising pedal, the pulse and the orbit arrive early, a second voice a tritone away, clusters torn by a half step.")

W0 = {0, 2, 4, 6, 8, 10}
W1 = {1, 3, 5, 7, 9, 11}

U1 = ["E4:6 F#4:6 G#4:4", "A#4:6 E5:6 D5:4", "C5:6 D5:6 E5:4", "F#5:16"]
U2 = ["A#4:6 G#4:6 F#4:4", "E4:6 A#4:6 G#4:4", "A#4:6 C5:6 D5:4", "E5:16"]
U3 = ["F#4:6 G#4:6 A#4:4", "E5:6 A#4:6 C5:4", "D5:6 E5:6 F#5:4", "A#4:4 C5:4 F#5:8"]
# the peak: the tune in LONG notes (whole, 12+4, 10+6) over the sixteenth orbit
L1 = ["E4:12 G#4:4", "A#4:16", "E5:10 D5:6", "C5:16"]
L2 = ["A#4:12 G#4:4", "F#4:16", "C5:10 D5:6", "E5:16"]
L3 = ["F#4:8 G#4:8", "A#4:16", "E5:12 F#5:4", "F#5:16"]
L4 = ["A#4:16", "C5:12 D5:4", "E5:16", "F#5:16"]
L5 = ["F#5:8 E5:8", "D5:8 C5:8", "A#4:12 C5:4", "A#4:4 C5:4 F#5:8"]
R = "r:16"
MEL_SHIFT = -2          # the tune is written from E4; sounding a whole tone lower keeps its top (F#5 + 1 in phase 2) inside the harp
MEL = {
    "A": [R] * 4 + U1 + U2 + U1,
    "A2": U1 + U3,
    "PEAK": L1 + L2 + L3 + L4 + L5,
    "CALM": ["E4:6 F#4:6 G#4:4", R, "A#4:6 E5:6 D5:4", R, "C5:6 D5:6 E4:4", R, "A#4:6 C5:6 D5:4", "A#4:4 C5:4 F#5:8"],
}
# the held pairs of flutes a tritone apart in B (G5 + C#6, then a minor third higher, ...), and the chord of the cluster of every bar
B_DYADS = [(79, 85), (79, 85), (82, 88), (82, 88), (85, 91), (85, 91), (86, 92), (86, 92), (88, 94), (88, 94), (91, 97), (91, 97)]
B_CHORDS = [{0, 3, 6, 9}] * 2 + [{6, 11, 4, 9}] * 2 + [{1, 4, 7, 10}] * 2 + [{1, 3, 5, 7}] * 2 + [{0, 3, 6, 9}] * 2 + [{6, 11, 4, 9}] * 2   # diminished 7th, fourths, ..., the other whole-tone scale


def names(text):
    return [midi(n) for n in text.split()]


def steps(text, each):
    out = []
    for n in names(text):
        out += [n] * each
    return out


# the pedal of every bar (what sounds): phase 1 falls a whole tone every four bars (every two in the peak, twice through five notes), phase 2 climbs
PEDAL1 = {
    "intro": names("A#2 A#2 A#2 A#2"),
    "A": steps("A#2 G#2 F#2 E2", 4),
    "A2": steps("D2 C2", 4),
    "B": steps("C2 F#1 A#1", 4),
    "PEAK": steps("A#2 G#2 F#2 E2 D2 A#2 G#2 F#2 E2 D2", 2),
    "CALM": names("C2 C2 D2 E2 F#2 G#2 A#2 A#2"),
}
PEDAL2 = {
    "intro": names("C2 C2 C2 C2"),
    "A": steps("C2 D2 E2 F#2", 4),
    "A2": steps("G#2 A#2", 4),
    "B": names("C2 F#1 C2 F#1 C2 F#1 C2 F#1 C2 F#1 C2 F#1"),
    "PEAK": steps("C2 D2 E2 F#2 G#2 C2 D2 E2 F#2 G#2", 2),
    "CALM": names("A#2 A#2 G#2 F#2 E2 D2 D2 D2"),
}

# bass figures: (tick, semitones over the pedal, velocity); the song counts 6 + 6 + 4, so the accents are on 0, 6 and 12
ORBIT6 = [0, 4, 6, 8, 6, 4]
ORBIT4 = [0, 4, 6, 8]
FIG = {
    "Q": [(0, 0, 70), (4, 0, 44), (8, 0, 60), (12, 0, 44)],
    "E": [(t, 0, 92 if t == 0 else 80 if t in (6, 12) else 44) for t in range(0, 16, 2)],
    "E_": [(t, 0, 92 if t == 0 else 80 if t in (6, 12) else 44) for t in range(0, 16, 2)] + [(15, 0, 48)],
    "S": [(t, 0, 92 if t == 0 else 80 if t in (6, 12) else 44) for t in range(0, 16, 2)] + [(1, 0, 40), (7, 0, 44), (13, 0, 42), (15, 0, 46)],
    "T": [(b, 0, 90 if b == 0 else 78) for b in (0, 4, 8, 12)] + [(b + 1, 2, 46) for b in (0, 4, 8, 12)] + [(b + 3, 4, 52) for b in (0, 4, 8, 12)],
    "WU": [(0, 0, 92), (2, 0, 50), (4, 4, 66), (6, 4, 78), (8, 6, 60), (10, 6, 50), (12, 8, 80), (14, 10, 60), (1, 0, 40), (7, 4, 46), (13, 8, 46)],
    "WV": [(0, 0, 92), (2, 0, 50), (4, 6, 66), (6, 6, 78), (8, 12, 60), (10, 6, 50), (12, 4, 80), (14, 2, 60), (1, 0, 40), (7, 6, 46), (13, 4, 46)],
    "F16": [(0, 0, 92), (2, 0, 50), (4, 0, 66), (6, 0, 78)] + [(8 + k, o, 56 + 4 * k) for k, o in enumerate((0, 2, 4, 6, 4, 2, 4, 0))],
    "M16": [(k, o, (94 if k == 0 else 82 if k in (6, 12) else 60 if o == 8 else 50)) for k, o in enumerate(ORBIT6 + ORBIT6 + ORBIT4)],
}
BASS_PLAN = {
    0: {"intro": [None, "Q", "Q", "Q"],
        "A": ["E"] * 7 + ["E_"] + ["E"] * 3 + ["S"] + ["E", "S", "E_", "S"],
        "A2": ["S", "E_", "S", "T", "S", "E_", "S", "F16"],
        "B": ["S", "T", "WU", "T", "T", "WV", "T", "F16", "T", "WU", "T", "F16"],
        "PEAK": ["M16"] * 20,
        "CALM": ["E", "E", "E_", "E", "E", "E", "E", "Q"]},
    1: {"intro": [None, "Q", "Q", "Q"],
        "A": ["E_", "E", "E", "S", "E", "E_", "E", "S", "S", "E", "S", "T", "E_", "S", "T", "S"],
        "A2": ["S", "T", "S", "F16", "S", "T", "S", "F16"],
        "B": ["T", "T", "WU", "T", "T", "WV", "T", "F16", "T", "WU", "M16", "M16"],
        "PEAK": ["M16"] * 20,
        "CALM": ["E_", "E", "E_", "E", "E_", "E", "S", "E_"]},
}
INTRO_BASS_GAIN = [0, 0.6, 0.8, 0.95]

DRUMS = {
    "i0": [(0, "bd", 62)],
    "i1": [(0, "bd", 66), (8, "bd", 52)],
    "i2": [(0, "bd", 72), (6, "bd", 50), (12, "bd", 56)],
    "g6": [(0, "bd", 100), (6, "bd", 66), (12, "bd", 76)],
    "g6s": [(0, "bd", 100), (6, "bd", 70), (12, "bd", 80), (3, "sn", 40), (9, "sn", 46)],
    "b": [(0, "bd", 100), (6, "bd", 74), (12, "bd", 84), (7, "bd", 44), (13, "bd", 44), (3, "sn", 54), (9, "sn", 60)] + [(t, "hat", 24) for t in (2, 4, 10, 14)],
    "peak": [(0, "bd", 100), (6, "bd", 94), (12, "bd", 98), (3, "sn", 82), (9, "sn", 88), (14, "sn", 66)] + [(t, "hat", 38 if t % 2 == 0 else 26) for t in (1, 2, 4, 5, 7, 8, 10, 11, 13, 15)],
    "peak2": [(0, "bd", 100), (6, "bd", 98), (12, "bd", 100), (8, "bd", 60), (15, "bd", 54), (3, "sn", 90), (9, "sn", 94), (14, "sn", 76)] + [(t, "hat", 44 if t % 2 == 0 else 30) for t in (1, 2, 4, 5, 7, 10, 11, 13)],
}

# flags: per bar in the intro, per block of four bars elsewhere
#  didge: the pedal on every beat; drums; org = bit cluster of n notes; double = 1 more set struck two ticks later; choir = flute cluster of n notes; rw = flute cluster of the other scale of n notes;
#  eyes = loudness of the blinking flute pair; harp/bit/fl/tri = the tune on that instrument (tri = the tritone voice); hit = an ensemble hit on the first bar of the block
F1 = {
    "intro": [dict(didge=1, dvel=0.55, drums="i0"), dict(didge=1, dvel=0.8, drums="i1"), dict(didge=1, drums="i2", org=4, gain=0.55), dict(didge=1, drums="i2", org=4, gain=0.8, choir=3, cgain=0.55, eyes=0.5, roll=1)],
    "A": [dict(didge=1, drums="g6", org=4, eyes=0.7),
          dict(didge=1, drums="g6", org=4, choir=3, eyes=0.9, harp=1, bit=1, roll_last=1),
          dict(didge=1, drums="g6", org=4, choir=3, eyes=1.0, harp=1, bit=1, hit=1, roll_last=1),
          dict(didge=1, drums="g6s", org=4, choir=3, eyes=1.0, harp=1, bit=1, fl=1, roll_last=1)],
    "A2": [dict(didge=1, drums="g6s", org=3, choir=3, eyes=1.0, harp=1, bit=1, fl=1, hit=1, roll_last=1),
           dict(didge=1, drums="g6s", org=3, choir=3, eyes=1.0, harp=1, bit=1, fl=1, roll_last=1)],
    "B": [dict(didge=1, drums="b", bdyad=1, org=3, gain=0.7, eyes=0.35),
          dict(didge=1, drums="b", bdyad=1, org=3, gain=0.85, eyes=0.35),
          dict(didge=1, drums="b", bdyad=1, org=3, gain=1.0, eyes=0.35, roll_last=1)],
    "PEAK": [dict(didge=1, drums="peak", org=3, choir=3, eyes=1.2, harp=1, bit=1, fl=1, hit=1, roll_last=1)] * 5,
    "CALM": [dict(didge=1, drums="g6", org=4, gain=0.8, eyes=0.6, harp=1),
             dict(didge=1, drums="g6", org=4, gain=0.85, choir=3, cgain=0.8, eyes=0.7, harp=1, bit=1, roll_last=1)],
}
F2 = {
    "intro": [dict(didge=1, dvel=0.55, drums="i0"), dict(didge=1, dvel=0.8, drums="i1"), dict(didge=1, drums="g6", org=4, gain=0.6, double=1), dict(didge=1, drums="g6", org=4, gain=0.85, choir=3, cgain=0.6, eyes=0.6, roll=1)],
    "A": [dict(didge=1, drums="g6s", org=4, eyes=0.8),
          dict(didge=1, drums="g6s", org=4, choir=3, eyes=1.0, harp=1, bit=1, tri=1, roll_last=1),
          dict(didge=1, drums="b", org=4, choir=3, eyes=1.1, harp=1, bit=1, tri=1, hit=1, roll_last=1),
          dict(didge=1, drums="b", org=4, choir=3, eyes=1.1, harp=1, bit=1, fl=1, tri=1, roll_last=1)],
    "A2": [dict(didge=1, drums="b", org=3, choir=3, eyes=1.1, harp=1, bit=1, tri=1, hit=1, roll_last=1),
           dict(didge=1, drums="peak", org=3, choir=3, rw=2, eyes=1.1, harp=1, bit=1, tri=1, roll_last=1)],
    "B": [dict(didge=1, drums="b", bdyad=1, org=3, gain=0.75, eyes=0.5),
          dict(didge=1, drums="b", bdyad=1, org=3, gain=0.9, eyes=0.5),
          dict(didge=1, drums="peak", bdyad=1, org=3, gain=1.0, eyes=0.5, roll_last=1)],
    "PEAK": [dict(didge=1, drums="peak2", org=3, choir=3, rw=2, eyes=1.3, harp=1, bit=1, fl=1, tri=1, hit=1, roll_last=1)] * 5,
    "CALM": [dict(didge=1, drums="g6s", org=4, gain=0.85, eyes=0.7, harp=1),
             dict(didge=1, drums="b", org=4, gain=0.9, choir=3, cgain=0.85, eyes=0.8, harp=1, bit=1, roll_last=1)],
}


def organ_base(section, i):
    if section == "A":
        return 55 + 2 * min(i // 2, 5)
    if section == "A2":
        return 55 + 2 * (i // 2)
    if section == "PEAK":
        return 55 + 2 * ((i // 2) % 6)
    if section == "CALM":
        return (63, 63, 61, 61, 59, 59, 57, 57)[i]
    return 55 + 2 * min(i, 4)          # intro


def choir_base(section, i):
    if section == "A":
        return (87, 87, 85, 85, 83, 83, 81, 81, 79, 79, 79, 79, 79, 79, 79, 79)[i]
    if section == "A2":
        return 79 + 2 * (i // 2)
    if section == "PEAK":
        return 83
    if section == "CALM":
        return 79 + 2 * (i // 2)
    return 85                           # intro


def ramp(lo, hi, i, tick, block=4):
    return lerp(lo, hi, ((i % block) * BAR + tick) / (block * BAR))


def build(phase):
    sc = Score(TPS, SECTIONS, shift=0 if phase == 0 else 1)
    flags = F1 if phase == 0 else F2
    pedal = PEDAL1 if phase == 0 else PEDAL2
    for name, volume, pan in (("bass", 100, 100), ("didge", 90, 100), ("harp", 90, 100), ("bit_m", 80, 95), ("fl_m", 60, 100), ("tri_i", 70, 110),
                              ("hitb1", 100, 90), ("hitb2", 100, 110), ("o1", 70, 90), ("o2", 70, 110), ("o3", 70, 100), ("o4", 70, 85), ("p1", 70, 90), ("p2", 70, 110), ("p3", 70, 100), ("p4", 70, 85),
                              ("c1", 60, 95), ("c2", 60, 105), ("c3", 60, 100), ("c4", 60, 100), ("r1", 55, 95), ("r2", 55, 105), ("r3", 55, 100), ("eye1", 60, 80), ("eye2", 60, 120)):
        sc.layer(name, volume, pan)
    prev = {}
    for section, bars in SECTIONS:
        for i in range(bars):
            g = sc.starts[section] + i
            t0 = g * BAR
            per_bar = section == "intro"
            f = flags[section][i if per_bar else i // 4]
            block_start = per_bar or i % 4 == 0
            block_end = per_bar or i % 4 == 3
            root = pedal[section][i]
            peak = section == "PEAK"
            # the ensemble hit on the first bar of a block: the pedal and the drums together at full strength (and the reed pair in A)
            hit_here = bool(f.get("hit") and i % 4 == 0 and (i % 8 == 0 or not peak))
            if hit_here:
                sc.put("bass", t0, BASS, root, 100)
                sc.drum(t0, "bd", 100)
                sc.drum(t0, "sn", 90)
                if section == "A":
                    for k, note in enumerate(ladder(W0, organ_base(section, i), 2)):
                        sc.put(f"hitb{k + 1}", t0, BIT, note, 100)
            # the tune
            if section in MEL and any(k in f for k in ("harp", "bit", "fl", "tri")):
                long_tune = peak
                for tick, note, length in melody_events(MEL[section][i]):
                    note += MEL_SHIFT
                    a = 94 if tick == 0 else 84
                    for key, layer, inst, off, vol in (("harp", "harp", HARP, 0, 1.0), ("bit", "bit_m", BIT, 0, 0.8), ("fl", "fl_m", FLUTE, 24, 0.5)):
                        if key in f:
                            sc.hold(layer, inst, note + off, t0 + tick, length, a * vol * (0.8 if long_tune and length >= 10 else 1.0), a * vol * (1.0 if long_tune else 0.7), every=4)
                    if f.get("tri"):
                        sc.hold("tri_i", IRON_XYLOPHONE, note - 6, t0 + tick, length, a * 0.85, a * 0.55, every=4)
            # the bass figure of the bar, and the pedal on every beat
            name = BASS_PLAN[phase][section][i]
            gain = INTRO_BASS_GAIN[i] if section == "intro" else (0.85 if section == "CALM" else 1.0)
            if name:
                for tick, offset, vel in FIG[name]:
                    sc.put("bass", t0 + tick, BASS, root + offset, vel * gain)
            if f.get("didge"):
                for tick in range(0, BAR, 4):
                    if hit_here and tick == 0:
                        continue
                    sc.put("didge", t0 + tick, DIDGERIDOO, root, 56 * f.get("dvel", 1.0) + (8 if tick == 0 else 0))
            # the clusters: the reed (bit), the flute, and in phase 2 the flute of the other scale
            gain = f.get("gain", 1.0)
            if f.get("org"):
                if section == "B":
                    pcs = B_CHORDS[i]
                    cluster = voicing(pcs, 55, 70, f["org"], prev.get("b_org"), tight=True)
                    prev["b_org"] = cluster
                else:
                    cluster = ladder(W0, organ_base(section, i), f["org"])
                sets = [3, 1] if f.get("double") and not peak else [3]
                for offset in sets:
                    for tick in range(offset, BAR, 4):
                        v = ramp(34, 58, i, tick) * gain * (0.8 if offset == 1 else 1.0) * (1.1 if tick % 8 == 3 else 1.0)
                        for k, note in enumerate(cluster):
                            sc.put(f"{'o' if offset == 3 else 'p'}{k + 1}", t0 + tick, BIT, note, v)
            if f.get("choir"):
                if section == "B":
                    high = voicing(B_CHORDS[i], 79, 92, f.get("choir", 3), prev.get("b_ch"), tight=True)
                    prev["b_ch"] = high
                else:
                    high = ladder(W0, choir_base(section, i), f["choir"])
                for tick in range(5 if peak else 2, BAR, 8 if peak else 4):
                    v = ramp(22, 62, i, tick) * f.get("cgain", 1.0)
                    for k, note in enumerate(high):
                        sc.put(f"c{k + 1}", t0 + tick, FLUTE, note, v)
            if f.get("rw"):
                # the world is rewritten: the flute of the OTHER whole-tone scale lies a half step over the cluster
                torn = ladder(W1, choir_base(section, i) + 3, f["rw"])
                for tick in range(1 if peak else 3, BAR, 8 if peak else 4):
                    v = ramp(28, 60, i, tick)
                    for k, note in enumerate(torn):
                        sc.put(f"r{k + 1}", t0 + tick, FLUTE, note, v)
            # B: two flutes a tritone apart, held, climbing
            if f.get("bdyad"):
                low, high_note = B_DYADS[i]
                sc.hold("eye1", FLUTE, low, t0, BAR, 46 * gain, 78, every=2 if phase == 1 else 4)
                sc.hold("eye2", FLUTE, high_note, t0, BAR, 40 * gain, 70, every=2 if phase == 1 else 4)
            # the eyes: two flutes a tritone apart blink in turn (C6 and F#6)
            if f.get("eyes") and not f.get("bdyad"):
                for k, tick in enumerate(range(2 if peak else 1, BAR, 8 if peak else 4)):
                    sc.put("eye1" if k % 2 == 0 else "eye2", t0 + tick, FLUTE, 84 if k % 2 == 0 else 90, 24 + 16 * f["eyes"] + (6 if tick == 9 else 0))
            # the drums
            for tick, kind, vel in DRUMS[f["drums"]]:
                sc.drum(t0 + tick, kind, vel)
            if f.get("roll") or (f.get("roll_last") and block_end):
                sc.roll(t0 + 8, 8, 34, 92)
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
        print("   bass notes per bar by section: " + ", ".join(f"{name} {per:.1f}" for name, _, per in count_by_section(sc, layers, ("bass",))))


if __name__ == "__main__":
    main()
