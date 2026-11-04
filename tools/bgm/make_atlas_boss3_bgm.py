#!/usr/bin/env python3
"""Makes colossus_phase1.nbs and colossus_phase2.nbs: "Shoulders of Stone (Boss003)" and "Shoulders of Stone, Unbound (Boss003)", the stone giant in two phases.

    python3 tools/bgm/make_atlas_boss3_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss3_bgm.py outdir     writes them somewhere else

Class 1, "overwhelming majesty and despair" (BGM t272): not drive but WEIGHT. 100 BPM (6.67 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line), C minor.
The giant is a C pedal that never moves (the bass drum is its footfall: beats 1 and 3, a small shock a moment after each step); over it, an organ made of re-struck chords (pling in the middle,
a reedy 8-bit choir, a flute choir an octave over it that swells over four bars), a low brass (guitar + bass + didgeridoo in unison) that sings the tread theme
(C-G, Ab-G-Eb, F-C, D-B-D: a leap of a fifth up, the phrase hung on the dominant), and every eighth bar an ensemble hit. Structure (68 bars = 163 s): intro 4 (footsteps come near, the organ and the choir
swell out of the dark), A 16 (brass alone sings the theme; the choir and the snare come in at bar 8), A2 16 (the organ takes the theme an octave up over the brass; the second half a new theme in the organ's
register over Eb-Bb-Ab), B 8 (takes away: the Phrygian Db, a flute choir line that climbs by half steps, one drum), PEAK 16 (the theme rises a whole octave in five instruments, the bass pumps in eighths,
the drums on every beat, the choir wide, a roll into each block), CALM 8 (the brass again and a G7 that hangs up into the first note of A).
Phase 2 "Unbound": the same bars a half step up (C# minor); the drums double from the start (the bass drum on every beat, the snare), the choir is wide open (four flutes, three 8-bit voices), the brass
is joined by the 8-bit chip, the harmony walks (root - fifth in the peak), the bass in sixteenths, the sustained pedal under the peak gets a flute that WAILS up a half step every half bar (the despair rises).
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat1_organ import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 20 / 3
SECTIONS = [("intro", 4), ("A", 16), ("A2", 16), ("B", 8), ("PEAK", 16), ("CALM", 8)]
TITLES = ("Shoulders of Stone (Boss003)", "Shoulders of Stone, Unbound (Boss003)")
FILES = ("colossus_phase1.nbs", "colossus_phase2.nbs")
DESCRIPTIONS = (
    "Stone giant, phase 1: class 1 (majesty and despair). 100 BPM, C minor over an immovable C pedal, organ chords, flute choir, low brass theme, huge bass drum steps. Same bars as phase 2.",
    "Stone giant, phase 2: the same bars a half step up, drums doubled, the choir wide open, the 8-bit chip in the brass, sixteenth bass, a rising wail in the peak.")

# chords: "Chord/Bass" a bar ('X/B|Y/C' two to a bar)
CH = {
    "intro": "Cm/C2 Cm/C2 Ab/C2 G/G1".split(),
    "A": "Cm/C2 Ab/C2 Fm/C2 G/G1 Cm/C2 Ab/C2 Fm/F2 G/G1  Cm/C2 Ab/Ab1 Fm/F2 G/G1 Cm/C2 Db/Db2 Bb/Bb1 G/G1".split(),
    "A2": "Cm/C2 Ab/Ab1 Fm/F2 G/G1 Cm/C2 Ab/Ab1 Fm/F2 G/G1  Eb/Eb2 Bb/Bb1 Ab/Ab1 G/G1 Cm/C2 Ab/Ab1 Db/Db2 G/G1".split(),
    "B": "Db/Db2 Db/Db2 Ab/Ab1 Ab/Ab1 Fm/F2 Fm/F2 G/G1 G/G1".split(),
    "PEAK": "Cm/C2 Ab/Ab1 Eb/Eb2 Bb/Bb1 Fm/F2 Db/Db2 G/G1 G/G1  Cm/C2 Eb/Eb2 Ab/Ab1 Fm/F2 Db/Db2 Bb/Bb1 G/G1 G/G1".split(),
    "CALM": "Cm/C2 Cm/C2 Ab/Ab1 Ab/Ab1 Fm/F2 Fm/F2 Bb/Bb1 G/G1".split(),
}
# phase 2 walks the harmony in the peak: root, then fifth (the same chords, the bass moves twice as fast)
CH2 = dict(CH)
CH2["PEAK"] = ("Cm/C2|Cm/G1 Ab/Ab1|Ab/Eb2 Eb/Eb2|Eb/Bb1 Bb/Bb1|Bb/F2 Fm/F2|Fm/C2 Db/Db2|Db/Ab1 G/G1|G/D2 G/G1|G/D2  "
               "Cm/C2|Cm/G1 Eb/Eb2|Eb/Bb1 Ab/Ab1|Ab/Eb2 Fm/F2|Fm/C2 Db/Db2|Db/Ab1 Bb/Bb1|Bb/F2 G/G1|G/D2 G/G1|G/D2").split()

T1 = ["C3:8 G3:8", "Ab3:6 G3:2 Eb4:8", "F3:8 C4:8", "D4:4 B3:4 D4:8", "C3:8 G3:8", "Ab3:6 G3:2 Eb4:8", "F4:8 Eb4:4 D4:4", "B3:4 D4:4 F4:8"]
T2 = T1[:4] + ["G3:8 C4:8", "Eb4:6 C4:2 Ab3:8", "F4:6 Eb4:2 F4:8", "B3:4 D4:4 F4:8"]
U = ["G4:8 Bb4:8", "D5:8 F5:8", "Eb5:6 D5:2 Eb5:8", "D5:4 B4:4 D5:8", "G4:8 C5:8", "Eb5:8 C5:8", "F5:6 Eb5:2 Db5:8", "B4:4 D5:4 F5:8"]
P1 = ["C4:8 G4:8", "Ab4:8 C5:8", "Bb4:8 Eb5:8", "D5:8 F5:8", "C5:6 Db5:2 F5:8", "F5:8 Db5:8", "D5:4 B4:4 D5:8", "F5:16"]
P2 = ["G4:6 C5:2 Eb5:8", "Bb4:8 Eb5:8", "C5:8 Eb5:8", "Ab4:8 C5:4 F5:4", "F5:8 Db5:8", "D5:8 F5:8", "B4:4 D5:4 F5:8", "F5:16"]
BM = ["Ab5:16", "Ab5:16", "C6:16", "C6:16", "C6:16", "Db6:16", "D6:16", "D6:16"]
M = ["C3:8 G3:8", "Eb3:6 F3:2 G3:8", "Ab3:8 G3:8", "Ab3:8 C4:8", "F3:8 C4:8", "Ab3:8 C4:8", "D4:8 F4:8", "B3:4 D4:4 F4:8"]
MEL = {"A": T1 + T2, "A2": T1 + U, "B": BM, "PEAK": P1 + P2, "CALM": M}

DRUMS = {
    "i0": [(0, "bd", 100)],
    "i1": [(0, "bd", 100), (8, "bd", 90)],
    "steps": [(0, "bd", 100), (2, "bd", 36), (8, "bd", 94), (10, "bd", 36)],
    "steps2": [(0, "bd", 100), (2, "bd", 36), (6, "bd", 52), (8, "bd", 94), (10, "bd", 36), (14, "bd", 52)],
    "march": [(0, "bd", 100), (4, "bd", 66), (8, "bd", 94), (12, "bd", 66), (4, "sn", 58), (12, "sn", 66)],
    "march8": [(0, "bd", 100), (2, "bd", 34), (4, "bd", 70), (6, "bd", 34), (8, "bd", 94), (10, "bd", 34), (12, "bd", 70), (14, "bd", 50), (4, "sn", 66), (12, "sn", 74)],
    "sparse": [(0, "bd", 100), (8, "bd", 70)],
    "peak1": [(0, "bd", 100), (4, "bd", 78), (8, "bd", 96), (12, "bd", 78), (14, "bd", 56), (4, "sn", 86), (12, "sn", 90), (10, "sn", 44)],
    "peak2": [(0, "bd", 100), (2, "bd", 50), (4, "bd", 86), (6, "bd", 50), (8, "bd", 96), (10, "bd", 50), (12, "bd", 86), (14, "bd", 60), (15, "bd", 56), (4, "sn", 96), (12, "sn", 98), (10, "sn", 46)],
    # phase 2: the bass drum on every beat from the start, the snare, sixteenth pickups in the peak
    "u_steps": [(0, "bd", 100), (2, "bd", 44), (4, "bd", 66), (8, "bd", 96), (10, "bd", 44), (12, "bd", 66), (12, "sn", 60)],
    "u_march": [(0, "bd", 100), (2, "bd", 44), (4, "bd", 80), (6, "bd", 44), (8, "bd", 98), (10, "bd", 44), (12, "bd", 80), (14, "bd", 62), (4, "sn", 80), (12, "sn", 90), (7, "sn", 40), (15, "sn", 50)],
    "u_b": [(0, "bd", 100), (4, "bd", 52), (8, "bd", 84), (12, "bd", 52)],
    "u_peak1": [(0, "bd", 100), (2, "bd", 56), (4, "bd", 92), (6, "bd", 56), (8, "bd", 100), (10, "bd", 56), (12, "bd", 92), (14, "bd", 66), (15, "bd", 60), (4, "sn", 90), (12, "sn", 94), (10, "sn", 50), (7, "sn", 40)],
    "u_peak2": [(0, "bd", 100), (2, "bd", 60), (3, "bd", 44), (4, "bd", 92), (6, "bd", 60), (7, "bd", 44), (8, "bd", 100), (10, "bd", 60), (11, "bd", 44), (12, "bd", 92), (14, "bd", 68), (15, "bd", 62),
                (4, "sn", 100), (12, "sn", 100), (10, "sn", 56), (13, "sn", 44)],
}
BASS_MODES = {
    "hold": [(0, 88), (4, 50), (8, 76), (12, 50)],
    "pump": [(0, 90), (2, 50), (4, 72), (6, 50), (8, 86), (10, 50), (12, 72), (14, 54)],
    "sixteenth": [(t, 88 if t == 0 else (70 if t % 4 == 0 else (54 if t % 2 == 0 else 40))) for t in range(16)],
}

# what plays in each half of a section (a section of one entry per bar: the intro)
# gtr/bsm/bit/pl/fl: the melody on that instrument at that offset (the guitar and the bass in the brass register, the others over it);
# org/choir: pad voices; orgT/chT: the tick (0 or 2) of every four where the pad is struck; bass: mode; drums; hit: every n-th bar of the section; roll_last: a roll into the next half
F1 = {
    "intro": [dict(didge=1, drums="i0", dvel=0.6), dict(didge=1, bass="hold", drums="i1"), dict(didge=1, bass="hold", drums="steps", org=3, orgT=0, gain=0.55),
              dict(didge=1, bass="hold", drums="steps", org=3, orgT=0, gain=0.8, choir=(3, 2), chT=2, cgain=0.6, roll=1)],
    "A": [dict(gtr=0, bsm=-12, didge=1, drums="steps", org=3, orgT=0, hit=8, roll_last=1),
          dict(gtr=0, bsm=-12, didge=1, drums="steps2", org=3, orgT=0, choir=(3, 2), chT=2, roll_last=1)],
    "A2": [dict(gtr=0, bsm=-12, bit=12, pl=12, didge=1, drums="march", org=3, orgT=2, choir=(3, 1), chT=2, hit=8, roll_last=1),
           dict(gtr=-12, bass="pump", bit=0, pl=0, fl=24, didge=1, drums="march8", org=3, orgT=2, choir=(3, 1), chT=2, hit=8, roll_last=1)],
    "B": [dict(didge=1, bass="hold", fl=0, bit=-12, drums="sparse", org=3, orgT=0, gain=0.5),
          dict(didge=1, bass="hold", fl=0, bit=-12, drums="sparse", org=3, orgT=0, gain=0.6, choir=(2, 0), chT=2, cgain=0.5, roll_last=1)],
    "PEAK": [dict(gtr=-12, bass="pump", bit=0, pl=0, fl=24, didge=1, drums="peak1", org=3, orgT=2, choir=(3, 1), chT=2, hit=8, roll_last=1),
             dict(gtr=-12, bass="pump", bit=0, pl=0, fl=24, didge=1, drums="peak2", org=3, orgT=2, choir=(3, 1), chT=2, hit=8, roll_last=1)],
    "CALM": [dict(gtr=0, bsm=-12, didge=1, drums="steps", org=3, orgT=0, gain=0.8),
             dict(gtr=0, bsm=-12, didge=1, drums="steps2", org=3, orgT=0, choir=(3, 2), chT=2, cgain=0.8, roll_last=1)],
}
F2 = {
    "intro": [dict(didge=1, drums="i0", dvel=0.6), dict(didge=1, bass="hold", drums="i1"), dict(didge=1, bass="hold", drums="u_steps", org=3, orgT=0, gain=0.6),
              dict(didge=1, bass="hold", drums="u_steps", org=3, orgT=0, gain=0.85, choir=(4, 3), chT=2, cgain=0.7, roll=1)],
    "A": [dict(gtr=0, bsm=-12, bit=12, didge=1, drums="u_steps", org=3, orgT=0, choir=(4, 3), chT=2, wide=1, hit=8, roll_last=1),
          dict(gtr=0, bsm=-12, bit=12, pl=12, didge=1, drums="u_march", org=3, orgT=0, choir=(4, 3), chT=2, wide=1, roll_last=1)],
    "A2": [dict(gtr=0, bsm=-12, bit=12, pl=12, fl=36, didge=1, drums="u_march", org=2, orgT=2, choir=(4, 1), chT=2, wide=1, hit=8, roll_last=1),
           dict(gtr=-12, bass="sixteenth", bit=0, pl=0, fl=24, didge=1, drums="u_peak1", org=2, orgT=2, choir=(4, 1), chT=2, wide=1, hit=8, roll_last=1)],
    "B": [dict(didge=1, bass="hold", fl=0, bit=-12, drums="u_b", org=3, orgT=0, gain=0.55),
          dict(didge=1, bass="hold", fl=0, bit=-12, drums="u_b", org=3, orgT=0, gain=0.7, roll_last=1)],
    "PEAK": [dict(gtr=-12, bass="sixteenth", bit=0, pl=0, fl=24, bsm2=-24, didge=1, drums="u_peak1", org=2, orgT=2, choir=(4, 1), chT=2, wide=1, hit=4, roll_last=1),
             dict(gtr=-12, bass="sixteenth", bit=0, pl=0, fl=24, bsm2=-24, didge=1, drums="u_peak2", org=2, orgT=2, choir=(4, 1), chT=2, wide=1, wail=1, hit=4, roll_last=1)],
    "CALM": [dict(gtr=0, bsm=-12, didge=1, drums="u_steps", org=3, orgT=0, choir=(3, 2), chT=2, wide=1, cgain=0.6, gain=0.85),
             dict(gtr=0, bsm=-12, bit=12, didge=1, drums="u_march", org=3, orgT=0, choir=(4, 3), chT=2, wide=1, cgain=0.8, roll_last=1)],
}


def parse_bar(text, bass_shift=0):
    segments = text.split("|")
    out = []
    for seg in segments:
        name, bass = seg.split("/")
        out.append((name, midi(bass)))
    return out


def pad_velocity(lo, hi, bar_index, tick, block=4):
    """Slow swell over a block of four bars (9.6 s at 100 BPM), then a breath and again."""
    return lerp(lo, hi, ((bar_index % block) * BAR + tick) / (block * BAR))


def build(phase):
    sc = Score(TPS, SECTIONS, shift=0 if phase == 0 else 1)
    flags = F1 if phase == 0 else F2
    chords = CH if phase == 0 else CH2
    for name, volume, pan in (("bass", 100, 100), ("bass2", 90, 100), ("didge", 85, 100), ("gtr", 100, 100), ("hit1", 100, 90), ("hit2", 100, 110), ("hit3", 100, 100),
                              ("bit_m", 85, 95), ("pl_m", 80, 105), ("fl_m", 60, 100), ("org1", 70, 90), ("org2", 70, 110), ("org3", 70, 100),
                              ("cb1", 60, 90), ("cb2", 60, 110), ("cb3", 60, 100), ("cf1", 60, 95), ("cf2", 60, 105), ("cf3", 60, 100), ("cf4", 60, 100), ("wail", 90, 100)):
        sc.layer(name, volume, pan)
    prev = {}
    for section, bars in SECTIONS:
        per_bar = len(flags[section]) == bars
        for i in range(bars):
            g = sc.starts[section] + i
            t0 = g * BAR
            half = 0 if i < bars // 2 else 1
            f = flags[section][i] if per_bar else flags[section][half]
            half_bars = bars // 2
            segs = parse_bar(chords[section][i])
            seg_len = BAR // len(segs)
            first_bass = segs[0][1]
            # the ensemble hit: first, so that it wins every tick it needs
            if f.get("hit") and i % f["hit"] == 0:
                low = first_bass
                while low < 43:
                    low += 12
                for k, note in enumerate((low, low + 7, low + 12)):
                    sc.put(f"hit{k + 1}", t0, GUITAR, note, (100, 100, 72)[k])
                sc.put("bass", t0, BASS, first_bass, 100)
                sc.put("didge", t0, DIDGERIDOO, first_bass, 92)
                sc.drum(t0, "bd", 100)
                sc.drum(t0, "sn", 88)
            # the melody
            events = melody_events(MEL[section][i]) if section in MEL and any(k in f for k in ("gtr", "bsm", "bsm2", "bit", "pl", "fl")) else []
            for tick, note, length in events:
                accent = 96 if tick == 0 else 86
                end_of_phrase = i % 4 == 3
                a = accent + (4 if end_of_phrase else 0)
                for key, layer, inst, vol in (("gtr", "gtr", GUITAR, 1.0), ("bsm", "bass", BASS, 0.95), ("bsm2", "bass2", BASS, 0.9), ("bit", "bit_m", BIT, 0.9), ("pl", "pl_m", PLING, 0.9), ("fl", "fl_m", FLUTE, 0.55)):
                    if key in f:
                        if key == "fl" and section == "B":
                            sc.hold(layer, inst, note + f[key], t0 + tick, length, 56 * (f.get("gain", 1) + 0.4), 80)
                        elif key == "bit" and section == "B":
                            sc.hold(layer, inst, note + f[key], t0 + tick, length, 40, 58)
                        else:
                            sc.hold(layer, inst, note + f[key], t0 + tick, length, a * vol, a * vol * 0.66)
            # the pedal: the bass, the didgeridoo
            for s, (chord, bass) in enumerate(segs):
                seg_start = s * seg_len
                if f.get("didge"):
                    for tick in range(seg_start, seg_start + seg_len, 4):
                        sc.put("didge", t0 + tick, DIDGERIDOO, bass, 54 * f.get("dvel", 1.0) + (6 if tick % 8 == 0 else 0))
                if "bass" in f:
                    for tick, vel in BASS_MODES[f["bass"]]:
                        if seg_start <= tick < seg_start + seg_len:
                            sc.put("bass", t0 + tick, BASS, bass + (12 if f["bass"] == "sixteenth" and tick % 4 == 3 else 0), vel)
                # the organ and the choir
                root, intervals = chord_of(chord)
                pcs = [(root + x) % 12 for x in intervals]
                if f.get("org"):
                    voices = voicing(pcs, 55, 68, f["org"], prev.get("org"))
                    prev["org"] = voices
                    for tick in range(seg_start + f.get("orgT", 0), seg_start + seg_len, 4):
                        gain = f.get("gain", 1.0)
                        v = pad_velocity(40, 62, i, tick % BAR) * gain
                        if tick % 8 == 0:
                            v *= 1.1
                        for k, note in enumerate(voices):
                            sc.put(f"org{k + 1}", t0 + tick, PLING, note, v)
                if f.get("choir"):
                    n_flute, n_bit = f["choir"]
                    if f.get("wide") or n_flute > 3:
                        high = voicing(pcs, 79, 98, n_flute, prev.get("choir"), wide=True)
                    else:
                        high = voicing(pcs, 79, 90, n_flute, prev.get("choir"))
                    prev["choir"] = high
                    cgain = f.get("cgain", 1.0)
                    for tick in range(seg_start + f.get("chT", 2), seg_start + seg_len, 4):
                        vf = pad_velocity(22, 72 if (f.get("wide") or n_flute > 3) else 64, i, tick % BAR) * cgain
                        vb = pad_velocity(18, 58, i, tick % BAR) * cgain
                        for k, note in enumerate(high):
                            sc.put(f"cf{k + 1}", t0 + tick, FLUTE, note, vf)
                        for k in range(n_bit):
                            note = high[k] - 12 if high[k] - 12 <= 78 else high[k] - 24
                            sc.put(f"cb{k + 1}", t0 + tick, BIT, note, vb)
            # the wail that climbs a half step every half bar
            if f.get("wail"):
                for k in range(2):
                    step = (g - sc.starts[section] - half_bars) * 2 + k
                    note = 78 + step
                    for tick in range(k * 8 + 1, k * 8 + 8, 2):
                        sc.put("wail", t0 + tick, FLUTE, note, lerp(55, 100, (step * 4 + (tick - k * 8) // 2) / 64))
            # drums
            for tick, kind, vel in DRUMS[f["drums"]]:
                sc.drum(t0 + tick, kind, vel)
            if f.get("roll") or (f.get("roll_last") and i % half_bars == half_bars - 1):
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
        print(f"-- phase {phase + 1}: {removed} notes thinned out to keep 8 at most, {sc.refit} notes moved an octave into range")
        report(target, layers, sc.length, sc.tps)
        print("   notes a second by section: " + ", ".join(f"{name} {count:.1f}" for name, count in density_by_section(sc, layers)))


if __name__ == "__main__":
    main()
