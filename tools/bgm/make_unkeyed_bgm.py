#!/usr/bin/env python3
"""Makes unkeyed_phase1.nbs and unkeyed_phase2.nbs: "Eyes Without a Key (Boss009)" and "..., Unwritten (Boss009)", a boss beyond human understanding.

    python3 tools/bgm/make_unkeyed_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_unkeyed_bgm.py outdir     writes them somewhere else

Class 1 again ("overwhelming majesty and despair") but the other face: not a giant of stone but a being beyond understanding, the law of the world itself - a song with NO TONIC.
75 BPM (5 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line); the first song of the set with no pling and no guitar: the weight is the didgeridoo and the bass (an eighth-note tremolo
on a pedal that FALLS a whole tone every four bars, like gravity), the voices are the 8-bit reed (a cluster of four adjacent whole-tone notes that RISES, a whole tone every two bars: the wedge opens)
and the flute (a cluster that falls), the tune is a harp in the whole-tone scale that goes step, step, then a TRITONE leap, in groups of 6 + 6 + 4 ticks that never feel like the bar, and a pair of
flutes a tritone apart that blink (the eyes). Nothing in it can be called a key: whole-tone clusters in A and the peak, diminished sevenths and a stack of fourths in B, and the other whole-tone scale
only where the world is torn. Structure (52 bars = 166 s): intro 4 (the pedal, the reed cluster and the flute swell out of nothing), A 12 (the tune comes at bar 4, the answer at bar 8), A2 8 (the tune in the
reed, harp and flute, a bass two octaves under it, an ensemble hit), B 8 (takes away: diminished and fourths, a pair of held flutes a tritone apart that climb), PEAK 12 (the tune in all of them over a
five-note cluster, the drums on every beat, the pedal falling through the whole scale in two-bar steps; the last block is the climbing U3), CALM 8 (the pedal climbs again, fragments of the tune).
Phase 2 "Unwritten": the same bars a half step up (the OTHER whole-tone scale), the pedal RISES instead of falling and in B it leaps a tritone every bar, the pulse arrives (the bass drum on every beat from the
first bar of A, a snare), the reed and the flute are struck twice as often, a SECOND VOICE a tritone away (iron xylophone and flute) doubles the tune from bar 4 of A, and in A2 and the peak the cluster
is torn by one extra note a half step off the scale (a flute cluster of the other scale on top) - the rules are being rewritten.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat1_organ import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 5.0
SECTIONS = [("intro", 4), ("A", 12), ("A2", 8), ("B", 8), ("PEAK", 12), ("CALM", 8)]
TITLES = ("Eyes Without a Key (Boss009)", "Eyes Without a Key, Unwritten (Boss009)")
FILES = ("unkeyed_phase1.nbs", "unkeyed_phase2.nbs")
DESCRIPTIONS = (
    "Boss without a key, phase 1: class 1 (majesty and despair), the god beyond understanding. 75 BPM, no tonic: whole-tone clusters over a falling pedal, a harp tune in 6+6+4 with tritone leaps. Same bars as phase 2.",
    "Boss without a key, phase 2: the same bars in the other whole-tone scale, a rising pedal, the pulse arrives, a second voice a tritone away, clusters torn by a half step.")

W0 = {0, 2, 4, 6, 8, 10}
W1 = {1, 3, 5, 7, 9, 11}

U1 = ["E4:6 F#4:6 G#4:4", "A#4:6 E5:6 D5:4", "C5:6 D5:6 E5:4", "F#5:16"]
U2 = ["A#4:6 G#4:6 F#4:4", "E4:6 A#4:6 G#4:4", "A#4:6 C5:6 D5:4", "E5:16"]
U3 = ["F#4:6 G#4:6 A#4:4", "E5:6 A#4:6 C5:4", "D5:6 E5:6 F#5:4", "A#4:4 C5:4 F#5:8"]
R = "r:16"
MEL_SHIFT = -2          # the tune is written from E4; sounding a whole tone lower keeps its top (F#5 + 1 in phase 2) inside the harp
MEL = {
    "A": [R] * 4 + U1 + U2,
    "A2": U1 + U2,
    "PEAK": U1 + U2 + U3,
    "CALM": ["E4:6 F#4:6 G#4:4", R, "A#4:6 E5:6 D5:4", R, "C5:6 D5:6 E5:4", R, "A#4:6 C5:6 D5:4", "A#4:4 C5:4 F#5:8"],
}
# the held pairs of flutes a tritone apart in B (G5 + C#6, then a minor third higher, ...)
B_DYADS = [(79, 85), (79, 85), (82, 88), (82, 88), (85, 91), (85, 91), (86, 92), (86, 92)]
B_CHORDS = [{0, 3, 6, 9}] * 2 + [{6, 11, 4, 9}] * 2 + [{1, 4, 7, 10}] * 2 + [{1, 3, 5, 7}] * 2   # diminished 7th, fourths (F#-B-E-A), diminished 7th, the other whole-tone scale

# the pedal of every bar (what sounds): phase 1 falls a whole tone every four bars (every two in the peak), phase 2 climbs
def names(text):
    return [midi(n) for n in text.split()]


PEDAL1 = {
    "intro": names("A#2 A#2 A#2 A#2"),
    "A": names("A#2 A#2 A#2 A#2 G#2 G#2 G#2 G#2 F#2 F#2 F#2 F#2"),
    "A2": names("E2 E2 E2 E2 D2 D2 D2 D2"),
    "B": names("C2 C2 C2 C2 F#1 F#1 F#1 F#1"),
    "PEAK": names("A#2 A#2 G#2 G#2 F#2 F#2 E2 E2 D2 D2 C2 C2"),
    "CALM": names("C2 C2 D2 E2 F#2 G#2 G#2 G#2"),
}
PEDAL2 = {
    "intro": names("C2 C2 C2 C2"),
    "A": names("C2 C2 C2 C2 D2 D2 D2 D2 E2 E2 E2 E2"),
    "A2": names("F#2 F#2 F#2 F#2 G#2 G#2 G#2 G#2"),
    "B": names("C2 F#1 C2 F#1 C2 F#1 C2 F#1"),
    "PEAK": names("C2 C2 D2 D2 E2 E2 F#2 F#2 G#2 G#2 A#2 A#2"),
    "CALM": names("A#2 A#2 G#2 F#2 E2 D2 D2 D2"),
}

DRUMS = {
    "i0": [(0, "bd", 100)],
    "p1": [(0, "bd", 100), (8, "bd", 58)],
    "p2": [(0, "bd", 100), (8, "bd", 70), (12, "bd", 36)],
    "beat": [(0, "bd", 100), (4, "bd", 62), (8, "bd", 90), (12, "bd", 62), (12, "sn", 60)],
    "beat8": [(0, "bd", 100), (2, "bd", 38), (4, "bd", 70), (6, "bd", 38), (8, "bd", 94), (10, "bd", 38), (12, "bd", 70), (14, "bd", 50), (4, "sn", 56), (12, "sn", 80)],
    "peak": [(0, "bd", 100), (4, "bd", 76), (8, "bd", 96), (12, "bd", 76), (12, "sn", 84), (6, "sn", 40), (14, "sn", 50)],
    "peak8": [(0, "bd", 100), (2, "bd", 44), (4, "bd", 86), (6, "bd", 44), (8, "bd", 98), (10, "bd", 44), (12, "bd", 86), (14, "bd", 56), (4, "sn", 80), (12, "sn", 96), (10, "sn", 46)],
    "b1": [(0, "bd", 100)],
}

# flags: per bar in the intro, per block of four bars elsewhere
#  didge/bass: the pedal (bass = an eighth-note tremolo); drums; org = bit cluster of n notes; double = 1 more set struck two ticks later; choir = flute cluster of n notes; rw = flute cluster of the other scale of n notes;
#  eyes = loudness of the blinking flute pair; harp/bit/fl/bs/tri = the tune on that instrument (bs = bass 2 octaves under, tri = the tritone voice); hit = an ensemble hit on the first bar of the block
F1 = {
    "intro": [dict(didge=1, dvel=0.55, drums="i0"), dict(didge=1, dvel=0.8, drums="p1"), dict(didge=1, drums="p1", org=4, gain=0.55), dict(didge=1, drums="p1", org=4, gain=0.8, choir=3, cgain=0.55, eyes=0.5, roll=1)],
    "A": [dict(didge=1, drums="p1", org=4, eyes=0.7),
          dict(didge=1, bass=1, drums="p1", org=4, choir=3, eyes=0.9, harp=1, bit=1, roll_last=1),
          dict(didge=1, bass=1, drums="p2", org=4, choir=3, eyes=1.0, harp=1, bit=1, hit=1, roll_last=1)],
    "A2": [dict(didge=1, bass=1, drums="p2", org=4, choir=3, eyes=1.0, harp=1, bit=1, fl=1, bs=1, hit=1, roll_last=1),
           dict(didge=1, bass=1, drums="beat", org=4, choir=3, eyes=1.0, harp=1, bit=1, fl=1, bs=1, roll_last=1)],
    "B": [dict(didge=1, drums="b1", bdyad=1, org=3, gain=0.7, eyes=0.35),
          dict(didge=1, drums="b1", bdyad=1, org=3, gain=0.85, eyes=0.35, roll_last=1)],
    "PEAK": [dict(didge=1, bass=1, drums="beat", org=5, choir=4, eyes=1.2, harp=1, bit=1, fl=1, bs=1, hit=1, roll_last=1),
             dict(didge=1, bass=1, drums="peak", org=5, choir=4, eyes=1.2, harp=1, bit=1, fl=1, bs=1, hit=1, roll_last=1),
             dict(didge=1, bass=1, drums="peak", org=5, choir=4, eyes=1.3, harp=1, bit=1, fl=1, bs=1, hit=1, roll_last=1)],
    "CALM": [dict(didge=1, bass=1, drums="p2", org=4, gain=0.8, eyes=0.6, harp=1),
             dict(didge=1, bass=1, drums="p2", org=4, gain=0.85, choir=3, cgain=0.8, eyes=0.7, harp=1, bit=1, roll_last=1)],
}
F2 = {
    "intro": [dict(didge=1, dvel=0.55, drums="i0"), dict(didge=1, dvel=0.8, drums="beat"), dict(didge=1, drums="beat", org=4, gain=0.6, double=1), dict(didge=1, drums="beat", org=4, gain=0.85, double=1, choir=3, cgain=0.6, eyes=0.6, roll=1)],
    "A": [dict(didge=1, bass=1, drums="beat", org=4, double=1, eyes=0.8),
          dict(didge=1, bass=1, drums="beat8", org=4, double=1, choir=3, eyes=1.0, harp=1, bit=1, tri=1, roll_last=1),
          dict(didge=1, bass=1, drums="beat8", org=4, double=1, choir=3, eyes=1.1, harp=1, bit=1, tri=1, hit=1, roll_last=1)],
    "A2": [dict(didge=1, bass=1, drums="beat8", org=4, double=1, choir=3, rw=2, eyes=1.1, harp=1, bit=1, fl=1, tri=1, hit=1, roll_last=1),
           dict(didge=1, bass=1, drums="peak8", org=4, double=1, choir=3, rw=2, eyes=1.1, harp=1, bit=1, fl=1, tri=1, roll_last=1)],
    "B": [dict(didge=1, bass=1, drums="beat", bdyad=1, org=3, gain=0.75, double=1, eyes=0.5),
          dict(didge=1, bass=1, drums="beat", bdyad=1, org=3, gain=0.9, double=1, eyes=0.5, roll_last=1)],
    "PEAK": [dict(didge=1, bass=1, drums="peak8", org=4, double=1, choir=3, rw=3, eyes=1.3, harp=1, bit=1, fl=1, tri=1, hit=1, roll_last=1),
             dict(didge=1, bass=1, drums="peak8", org=4, double=1, choir=3, rw=3, eyes=1.3, harp=1, bit=1, fl=1, tri=1, hit=1, roll_last=1),
             dict(didge=1, bass=1, drums="peak8", org=4, double=1, choir=3, rw=3, eyes=1.4, harp=1, bit=1, fl=1, tri=1, hit=1, roll_last=1)],
    "CALM": [dict(didge=1, bass=1, drums="beat", org=4, gain=0.85, double=1, eyes=0.7, harp=1),
             dict(didge=1, bass=1, drums="beat8", org=4, gain=0.9, double=1, choir=3, cgain=0.85, eyes=0.8, harp=1, bit=1, roll_last=1)],
}


def organ_base(section, i):
    if section == "A":
        return 55 + 2 * (i // 2)
    if section == "A2":
        return 55 + 2 * i
    if section == "PEAK":
        return 55 + 2 * (i // 2)
    if section == "CALM":
        return (63, 63, 61, 61, 59, 59, 57, 57)[i]
    return 55 + 2 * min(i, 4)          # intro


def choir_base(section, i):
    if section == "A":
        return (87, 87, 85, 85, 83, 83, 81, 81, 79, 79, 79, 79)[i]
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
    for name, volume, pan in (("bass", 100, 100), ("bass2", 90, 100), ("didge", 90, 100), ("harp", 90, 100), ("bit_m", 80, 95), ("fl_m", 60, 100), ("tri_i", 70, 110), ("tri_f", 50, 90),
                              ("hitb1", 100, 90), ("hitb2", 100, 110), ("o1", 70, 90), ("o2", 70, 110), ("o3", 70, 100), ("o4", 70, 85), ("o5", 70, 115),
                              ("p1", 70, 90), ("p2", 70, 110), ("p3", 70, 100), ("p4", 70, 85), ("p5", 70, 115),
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
            # the ensemble hit on the first bar of a block: the bit cluster, the pedal and the drums together at full strength
            if f.get("hit") and i % 4 == 0:
                sc.put("bass", t0, BASS, root, 100)
                sc.put("didge", t0, DIDGERIDOO, root, 94)
                sc.drum(t0, "bd", 100)
                sc.drum(t0, "sn", 90)
                if section == "A":
                    for k, note in enumerate(ladder(W0, organ_base(section, i), 2)):
                        sc.put(f"hitb{k + 1}", t0, BIT, note, 100)
            # the tune
            if section in MEL and any(k in f for k in ("harp", "bit", "fl", "bs", "tri")):
                for tick, note, length in melody_events(MEL[section][i]):
                    note += MEL_SHIFT
                    a = 94 if tick == 0 else 84
                    for key, layer, inst, off, vol in (("harp", "harp", HARP, 0, 1.0), ("bit", "bit_m", BIT, 0, 0.8), ("fl", "fl_m", FLUTE, 24, 0.5), ("bs", "bass2", BASS, -24, 0.9)):
                        if key in f:
                            sc.hold(layer, inst, note + off, t0 + tick, length, a * vol, a * vol * 0.7, every=4)
                    if f.get("tri"):
                        sc.hold("tri_i", IRON_XYLOPHONE, note - 6, t0 + tick, length, a * 0.85, a * 0.55, every=4)
            # the pedal
            if f.get("didge"):
                for tick in range(0, BAR, 4):
                    sc.put("didge", t0 + tick, DIDGERIDOO, root, 56 * f.get("dvel", 1.0) + (8 if tick == 0 else 0))
            if f.get("bass"):
                for tick in range(0, BAR, 2):
                    sc.put("bass", t0 + tick, BASS, root, 74 if tick % 8 == 0 else (60 if tick % 4 == 0 else 40))
            # the clusters: the reed (bit), the flute, and in phase 2 the flute of the other scale
            gain = f.get("gain", 1.0)
            if f.get("org"):
                if section == "B":
                    pcs = B_CHORDS[i]
                    cluster = voicing(pcs, 55, 70, f["org"], prev.get("b_org"), tight=True)
                    prev["b_org"] = cluster
                else:
                    cluster = ladder(W0, organ_base(section, i), f["org"])
                sets = [3, 1] if f.get("double") else [3]
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
                for tick in range(1, BAR, 4):
                    v = ramp(22, 62, i, tick) * f.get("cgain", 1.0)
                    for k, note in enumerate(high):
                        sc.put(f"c{k + 1}", t0 + tick, FLUTE, note, v)
            if f.get("rw"):
                # the world is rewritten: the flute of the OTHER whole-tone scale lies a half step over the cluster
                torn = ladder(W1, choir_base(section, i) + 3, f["rw"])
                for tick in range(3, BAR, 4):
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
                for k, tick in enumerate(range(1, BAR, 4)):
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


if __name__ == "__main__":
    main()
