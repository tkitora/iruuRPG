#!/usr/bin/env python3
"""Makes colossus_phase1.nbs and colossus_phase2.nbs: "Shoulders of Stone (Boss003)" and "Shoulders of Stone, Unbound (Boss003)", the stone giant in two phases.

    python3 tools/bgm/make_atlas_boss3_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss3_bgm.py outdir     writes them somewhere else

Class 1, "overwhelming majesty and despair", rebuilt on the sixteenth-note guide (BGM t276): 125 BPM (8.333 ticks a second, a tick is a sixteenth, a bar is 16 ticks = the engine's bar line), C minor.
The melody keeps the base tempo (the tread theme in low brass: C-G, Ab-G-Eb, F-C, D-B-D; whole notes in the peak); the BASS makes the song feel two to four times faster, a crawling, thick, doom line:
  intro   quarter-note roots, soft; the bass drum is the footfall
  A       eighth-note C repeats (a pedal that does not move), every step heavy
  A2      eighths with a sixteenth pick-up, from the second half sixteenth double hits and triplet crawls (0,1,3 to a beat)
  B       eighths + sixteenth doubles + triplets, the line walks UP and back (root, third, fifth, seventh, octave): an irregular surge; the flute climbs by half steps over it
  PEAK    the full sixteenth machine gun (accents 3-3-2, octave pops), the drums and the hats in sixteenths, and over it the melody in WHOLE and HALF notes (five instruments re-strike them every beat)
  CALM    eighths falling back to quarters, G7 hung up into the first note of A.
Structure (68 bars = 130 s): intro 4, A 12, A2 12, B 16, PEAK 16 (from 65 %), CALM 8.
Phase 2 "Unbound": the same bars a half step up (C# minor); everything arrives earlier (eighths with doubles from the first bar of A, the triplets from A2, the machine gun already in the last two bars of B), the
bass drum goes on every beat, the 8-bit chip joins the brass, the harmony walks root-fifth in the peak, a second bass an octave above the machine gun, and a flute that WAILS up a half step every half bar.
Everything is written by what SOUNDS. Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat1_organ import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 125 / 15
SECTIONS = [("intro", 4), ("A", 12), ("A2", 12), ("B", 16), ("PEAK", 16), ("CALM", 8)]
TITLES = ("Shoulders of Stone (Boss003)", "Shoulders of Stone, Unbound (Boss003)")
FILES = ("colossus_phase1.nbs", "colossus_phase2.nbs")
DESCRIPTIONS = (
    "Stone giant, phase 1: class 1 (majesty and despair). 125 BPM, C minor: crawling bass (quarters, eighths, triplets, then sixteenth machine gun in the peak) under an organ, a flute choir and a low brass tread theme in long notes. Same bars as phase 2.",
    "Stone giant, phase 2: the same bars a half step up, everything earlier (doubles and triplets from A, machine gun in B), bass drum on every beat, chip in the brass, root-fifth walking bass and a rising wail in the peak.")

# chords: "Chord/Bass" a bar ('X/B|Y/C' two to a bar)
CH = {
    "intro": "Cm/C2 Cm/C2 Ab/Ab1 G/G1".split(),
    "A": "Cm/C2 Ab/C2 Fm/C2 G/G1  Cm/C2 Ab/C2 Fm/C2 G/G1  Cm/C2 Ab/Ab1 Fm/F2 G/G1".split(),
    "A2": "Cm/C2 Ab/Ab1 Fm/F2 G/G1  Cm/C2 Ab/Ab1 Fm/F2 G/G1  Cm/C2 Ab/Ab1 Db/Db2 G/G1".split(),
    "B": "Db/Db2 Db/Db2 Ab/Ab1 Ab/Ab1 Fm/F2 Fm/F2 G/G1 G/G1  Bbm/Bb1 Bbm/Bb1 Db/Db2 Db/Db2 Fm/F2 Fm/F2 G/G1 G/G1".split(),
    "PEAK": "Cm/C2 Ab/Ab1 Eb/Eb2 Bb/Bb1 Fm/F2 Db/Db2 G/G1 G/G1  Cm/C2 Eb/Eb2 Ab/Ab1 Fm/F2 Db/Db2 Bb/Bb1 G/G1 G/G1".split(),
    "CALM": "Cm/C2 Cm/C2 Ab/Ab1 Ab/Ab1 Fm/F2 Fm/F2 Bb/Bb1 G/G1".split(),
}
# phase 2 walks the harmony in the peak: root, then fifth (the same chords, the bass moves twice as fast)
CH2 = dict(CH)
CH2["PEAK"] = ("Cm/C2|Cm/G1 Ab/Ab1|Ab/Eb2 Eb/Eb2|Eb/Bb1 Bb/Bb1|Bb/F2 Fm/F2|Fm/C2 Db/Db2|Db/Ab1 G/G1|G/D2 G/G1|G/D2  "
               "Cm/C2|Cm/G1 Eb/Eb2|Eb/Bb1 Ab/Ab1|Ab/Eb2 Fm/F2|Fm/C2 Db/Db2|Db/Ab1 Bb/Bb1|Bb/F2 G/G1|G/D2 G/G1|G/D2").split()

T1 = ["C3:8 G3:8", "Ab3:6 G3:2 Eb4:8", "F3:8 C4:8", "D4:4 B3:4 D4:8", "C3:8 G3:8", "Ab3:6 G3:2 Eb4:8", "F4:8 Eb4:4 D4:4", "B3:4 D4:4 F4:8"]
T2B = ["G3:8 C4:8", "Eb4:6 C4:2 Ab3:8", "F4:6 Eb4:2 F4:8", "B3:4 D4:4 F4:8"]
UB = ["G4:8 C5:8", "Eb5:8 C5:8", "F5:6 Eb5:2 Db5:8", "B4:4 D5:4 F5:8"]
# the peak: WHOLE and HALF notes (the contrast to the sixteenth bass under them)
P1 = ["C5:16", "Eb5:8 C5:8", "Bb4:8 Eb5:8", "D5:16", "C5:8 F5:8", "F5:8 Db5:8", "D5:8 B4:8", "F5:16"]
P2 = ["Eb5:16", "Bb4:8 Eb5:8", "Ab4:8 C5:4 Eb5:4", "F5:16", "Db5:8 F5:8", "D5:8 F5:8", "B4:8 D5:8", "F5:16"]
BM = ["Ab5:16", "Ab5:16", "C6:16", "C6:16", "C6:16", "Db6:16", "D6:16", "D6:16", "F6:16", "F6:16", "F6:16", "F6:16", "Eb6:16", "Eb6:16", "D6:16", "D6:16"]
M = ["C3:8 G3:8", "Eb3:6 F3:2 G3:8", "Ab3:8 G3:8", "Ab3:8 C4:8", "F3:8 C4:8", "Ab3:8 C4:8", "D4:8 F4:8", "B3:4 D4:4 F4:8"]
MEL = {"A": T1 + T2B, "A2": T1 + UB, "B": BM, "PEAK": P1 + P2, "CALM": M}

# the bass of every bar, per phase and section
BASS_PLAN = {
    0: {"intro": [None, "Q", "Q", "Q"],
        "A": ["E", "E", "E", "E", "E", "E", "E", "E_", "E", "E", "E", "E_"],
        "A2": ["E", "E_", "E", "E_", "E", "S", "E", "S", "S", "S", "E_", "T"],
        "B": ["E", "S", "WU", "T", "S", "WV", "S", "T", "S", "WU", "T", "WV", "T", "S", "T", "F16"],
        "PEAK": ["M16"] * 16,
        "CALM": ["E", "E", "E", "E", "E", "E", "E", "Q"]},
    1: {"intro": [None, "Q", "Q", "Q"],
        "A": ["E_", "E", "E", "S", "E", "E_", "E", "S", "E", "S", "E_", "T"],
        "A2": ["S", "E_", "S", "T", "S", "T", "S", "F16", "T", "S", "T", "F16"],
        "B": ["S", "T", "WU", "T", "T", "WV", "T", "F16", "S", "T", "WU", "F16", "T", "F16", "M16", "M16"],
        "PEAK": ["M16"] * 16,
        "CALM": ["E_", "E", "E_", "E", "E_", "E", "S", "E_"]},
}
INTRO_BASS_GAIN = [0, 0.6, 0.8, 0.95]


def hats(step, loud, soft):
    return [(t, "hat", loud if t % 2 == 0 else soft) for t in range(16) if t % step]


DRUMS = {
    "i0": [(0, "bd", 62)],
    "i1": [(0, "bd", 66), (8, "bd", 58)],
    "i2": [(0, "bd", 72), (4, "bd", 44), (8, "bd", 66), (12, "bd", 44)],
    "steps": [(0, "bd", 100), (2, "bd", 34), (8, "bd", 94), (10, "bd", 34), (12, "sn", 60)],
    "march": [(0, "bd", 100), (4, "bd", 66), (8, "bd", 94), (12, "bd", 66), (4, "sn", 58), (12, "sn", 70)],
    "surge": [(0, "bd", 100), (4, "bd", 70), (8, "bd", 94), (12, "bd", 70), (7, "bd", 44), (4, "sn", 66), (12, "sn", 80)] + [(t, "hat", 26) for t in (2, 6, 10, 14)],
    "peak": [(0, "bd", 100), (4, "bd", 86), (8, "bd", 98), (12, "bd", 86), (14, "bd", 58), (4, "sn", 96), (12, "sn", 100), (10, "sn", 44)] + hats(4, 38, 26),
    # phase 2: the bass drum on every beat from the start, the snare, sixteenth pick-ups, hats earlier
    "u_steps": [(0, "bd", 100), (2, "bd", 44), (4, "bd", 66), (8, "bd", 96), (10, "bd", 44), (12, "bd", 66), (12, "sn", 60)],
    "u_march": [(0, "bd", 100), (2, "bd", 44), (4, "bd", 80), (6, "bd", 44), (8, "bd", 98), (10, "bd", 44), (12, "bd", 80), (14, "bd", 62), (4, "sn", 80), (12, "sn", 90), (7, "sn", 40), (15, "sn", 50)]
    + [(t, "hat", 24) for t in (2, 6, 10, 14)],
    "u_surge": [(0, "bd", 100), (4, "bd", 80), (8, "bd", 96), (12, "bd", 80), (6, "bd", 50), (14, "bd", 56), (7, "bd", 44), (15, "bd", 44), (4, "sn", 80), (12, "sn", 92)] + hats(4, 32, 22),
    "u_peak": [(0, "bd", 100), (4, "bd", 92), (8, "bd", 100), (12, "bd", 92), (6, "bd", 60), (14, "bd", 66), (15, "bd", 58), (4, "sn", 100), (12, "sn", 100), (10, "sn", 56), (13, "sn", 44)] + hats(4, 44, 30),
}
DRUM_PLAN = {
    0: {"intro": ["i0", "i1", "i2", "steps"],
        "A": ["steps"] * 12, "A2": ["march"] * 12, "B": ["surge"] * 16, "PEAK": ["peak"] * 16, "CALM": ["steps"] * 8},
    1: {"intro": ["i0", "i1", "u_steps", "u_steps"],
        "A": ["u_steps"] * 4 + ["u_march"] * 8, "A2": ["u_march"] * 12, "B": ["u_surge"] * 16, "PEAK": ["u_peak"] * 16, "CALM": ["u_steps"] * 4 + ["u_march"] * 4},
}


def voices(phase, section, i):
    """Which instruments carry the melody in bar i of the section: offset in semitones over the written note."""
    if section == "A":
        v = dict(gtr=0, g5=1)
        if phase == 1 and i >= 4:
            v["bit"] = 12
        return v
    if section == "A2":
        if i < 8:
            v = dict(gtr=0, g5=1, bit=12, pl=12)
        else:
            v = dict(gtr=-12, bit=0, pl=0)
        if phase == 1:
            if i >= 8:
                v["fl"] = 12
        return v
    if section == "B":
        v = dict(fl=0, bit=-12)
        if i >= 8:
            v["gtr"] = -24
        return v
    if section == "PEAK":
        v = dict(pl=0, bit=0, gtr=-12)
        if phase == 1:
            v["fl"] = 12
        return v
    if section == "CALM":
        return dict(gtr=0, g5=1) if phase == 0 else dict(gtr=0, g5=1, bit=12)
    return {}


def pads(phase, section, i):
    """(organ voices, flute+8-bit choir (flutes, bits) or None, gain)"""
    if section == "intro":
        return [(0, 0), (3, 0.55), (3, 0.8), (3, 0.9)][i][0], ((3, 2) if i == 3 else None), [0.0, 0.55, 0.8, 0.9][i]
    if section == "A":
        return 3, ((3, 2) if i >= 6 else None), 0.9
    if section == "A2":
        return 3, (3, 2), 1.0
    if section == "B":
        return 3, ((2, 0) if i >= 8 else None), 0.6 + 0.02 * i
    if section == "PEAK":
        return 3, (3, 2), 1.0
    return 3, ((3, 2) if i >= 4 else None), 0.85


def parse_chords(text):
    out = []
    for seg in text.split("|"):
        name, bass = seg.split("/")
        out.append((name, midi(bass)))
    return out


def pad_velocity(lo, hi, bar_index, tick, block=4):
    """Slow swell over a block of four bars (7.7 s at 125 BPM), then a breath and again."""
    return lerp(lo, hi, ((bar_index % block) * BAR + tick) / (block * BAR))


def build(phase):
    sc = Score(TPS, SECTIONS, shift=0 if phase == 0 else 1)
    chords = CH if phase == 0 else CH2
    for name, volume, pan in (("bass", 100, 100), ("bass2", 90, 100), ("didge", 85, 100), ("gtr", 100, 100), ("g5", 90, 100), ("hit1", 100, 90), ("hit2", 100, 110),
                              ("bit_m", 85, 95), ("pl_m", 80, 105), ("fl_m", 60, 100), ("org1", 70, 90), ("org2", 70, 110), ("org3", 70, 100),
                              ("cb1", 60, 90), ("cb2", 60, 110), ("cb3", 60, 100), ("cf1", 60, 95), ("cf2", 60, 105), ("cf3", 60, 100), ("cf4", 60, 100), ("wail", 90, 100)):
        sc.layer(name, volume, pan)
    prev = {}
    for section, bars in SECTIONS:
        for i in range(bars):
            g = sc.starts[section] + i
            t0 = g * BAR
            segs = parse_chords(chords[section][i])
            seg_len = BAR // len(segs)
            first_bass = segs[0][1]
            hit_here = False
            v = voices(phase, section, i)
            # the ensemble hit: first, so that it wins every tick it needs
            hit_every = 4 if (phase == 1 and section in ("PEAK", "B")) else 8
            if section in ("A2", "PEAK", "B", "A") and i % hit_every == 0 and (section != "A" or i > 0) and (section != "B" or phase == 1 or i == 8):
                hit_here = True
                low = first_bass
                while low < 43:
                    low += 12
                for k, note in enumerate((low, low + 7)):
                    sc.put(f"hit{k + 1}", t0, GUITAR, note, 100)
                sc.put("bass", t0, BASS, first_bass, 100)
                sc.drum(t0, "bd", 100)
            # the melody
            if section in MEL and v:
                for tick, note, length in melody_events(MEL[section][i]):
                    accent = 96 if tick == 0 else 86
                    if i % 4 == 3:
                        accent += 4
                    long_note = length >= 8
                    for key, layer, inst, vol in (("gtr", "gtr", GUITAR, 1.0), ("bit", "bit_m", BIT, 0.9), ("pl", "pl_m", PLING, 0.9), ("fl", "fl_m", FLUTE, 0.55)):
                        if key not in v:
                            continue
                        snd = note + v[key]
                        if section == "B" and key == "fl":
                            sc.hold(layer, inst, snd, t0 + tick, length, 50, 82)
                        elif section == "B":
                            sc.hold(layer, inst, snd, t0 + tick, length, 36, 56)
                        else:
                            sc.hold(layer, inst, snd, t0 + tick, length, a_from(accent, vol, long_note, section), accent * vol)
                    if "g5" in v and sc.fits(GUITAR, note + v["gtr"] + 7):
                        sc.hold("g5", GUITAR, note + v["gtr"] + 7, t0 + tick, length, accent * 0.75, accent * 0.5)
            # the bass figure of the bar (per half bar when the harmony walks) and the pedal under it
            figure_name = BASS_PLAN[phase][section][i]
            gain = INTRO_BASS_GAIN[i] if section == "intro" else (0.82 if section == "CALM" else 1.0)
            for s, (chord, bass) in enumerate(segs):
                seg_start = s * seg_len
                root, intervals = chord_of(chord)
                third = 3 if 3 in intervals else 4
                if figure_name:
                    for tick, offset, vel in figure(figure_name, third):
                        if seg_start <= tick < seg_start + seg_len:
                            sc.put("bass", t0 + tick, BASS, bass + offset, vel * gain)
                            if phase == 1 and figure_name == "M16" and tick % 8 == 3 and bass + 12 <= 54:
                                sc.put("bass2", t0 + tick, BASS, bass + 12, vel * 0.8)
                for tick in range(seg_start, seg_start + seg_len, 4):
                    if hit_here and tick == 0:
                        continue
                    dv = (0.55 if i == 0 else 0.8) if section == "intro" else 1.0
                    sc.put("didge", t0 + tick, DIDGERIDOO, bass, (54 + (6 if tick % 8 == 0 else 0)) * dv)
                # the organ and the choir
                n_org, choir, pgain = pads(phase, section, i)
                pcs = [(root + x) % 12 for x in intervals]
                if n_org and pgain:
                    voicing_org = voicing(pcs, 55, 68, n_org, prev.get("org"))
                    prev["org"] = voicing_org
                    step, first = (8, 2) if choir else (4, 2)
                    for tick in range(seg_start + first, seg_start + seg_len, step):
                        vel = pad_velocity(40, 62, i, tick % BAR) * pgain * (1.1 if tick % 8 == 2 else 1.0)
                        for k, note in enumerate(voicing_org):
                            sc.put(f"org{k + 1}", t0 + tick, PLING, note, vel)
                if choir:
                    n_flute, n_bit = choir
                    high = voicing(pcs, 79, 98 if n_flute > 3 else 90, n_flute, prev.get("choir"), wide=n_flute > 3)
                    prev["choir"] = high
                    wide = n_flute > 3
                    for tick in range(seg_start + 6, seg_start + seg_len, 8):
                        vf = pad_velocity(22, 72 if wide else 64, i, tick % BAR) * pgain
                        vb = pad_velocity(18, 58, i, tick % BAR) * pgain
                        for k, note in enumerate(high):
                            sc.put(f"cf{k + 1}", t0 + tick, FLUTE, note, vf)
                        for k in range(n_bit):
                            note = high[k] - 12 if high[k] - 12 <= 78 else high[k] - 24
                            sc.put(f"cb{k + 1}", t0 + tick, BIT, note, vb)
            # the wail that climbs a half step every half bar (phase 2, second half of the peak)
            if phase == 1 and section == "PEAK" and i >= 8:
                for k in range(2):
                    step = (i - 8) * 2 + k
                    for tick in range(k * 8 + 1, k * 8 + 8, 2):
                        sc.put("wail", t0 + tick, FLUTE, 78 + step, lerp(55, 100, (step * 4 + (tick - k * 8) // 2) / 64))
            # drums
            for tick, kind, vel in DRUMS[DRUM_PLAN[phase][section][i]]:
                sc.drum(t0 + tick, kind, vel)
            last_of_four = i % 4 == 3 and section in ("A", "A2", "B", "PEAK", "CALM")
            if (section == "intro" and i == 3) or (last_of_four and (i % 8 == 7 or section in ("PEAK",) or phase == 1)):
                sc.roll(t0 + 8, 8, 34, 92)
    return sc


def a_from(accent, vol, long_note, section):
    """The loudness a held note starts at: a long note swells (starts lower, ends at the accent)."""
    return accent * vol * (0.8 if long_note and section == "PEAK" else 1.0)


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
        print("   bass notes (bass+bass2) per bar by section: " + ", ".join(f"{name} {per:.1f}" for name, _, per in count_by_section(sc, layers, ("bass", "bass2"))))


if __name__ == "__main__":
    main()
