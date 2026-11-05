#!/usr/bin/env python3
"""Makes waltz_phase1.nbs and waltz_phase2.nbs: "Waltz at the Edge of Dark (Boss008)" and "Waltz at the Edge of Dark, Out of Step (Boss008)", a tragic waltz in two phases.

    python3 tools/bgm/make_waltz_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_waltz_bgm.py outdir     writes them somewhere else

Boss class 2 "sorrow and fated battle" as an elegant, doomed duel, rebuilt on the 16th-note machine-gun guide (t276): base tempo 125 BPM in 3/4. A song tick is a sixteenth (125/15 = 8.33 ticks a second),
a beat is 4 ticks, a bar of 3/4 is 12 ticks (eighth 2, sixteenth 1). A bar of 12 ticks meets the engine's 16-tick line every 4 bars (48 ticks = 3 lines), so every section is a multiple of 4 bars and
starts on such a line. The loop starts at tick 64 = a 4-tick silence and five bars of intro; the body is 84 bars (1008 ticks), 1072 ticks, 128.6 s.
The tune (8 bars, E minor): a fifth up into the tonic (B4 E5), a leap to the leading tone's F#5 held over B7, a sigh down (E5 B4), a fall to A4, then the same rise and a turn (F#5 E5 D#5 F#5), a
descent over C (E5 D5 C5) and an arpeggio of B7 that hangs on F#5. The lead is a solo violin made of pling / bit / flute / harp whose long notes are struck again every eighth with the loudness breathing.

The melody keeps the base tempo (beats, dotted halves) while the accompaniment is subdivided, so the waltz feels 2x to 4x faster than 125 BPM:
    intro    bass = quarter-note roots (oom) played softly, no drums
    A, A'    bass = tight eighth-note root repeats (6 a bar), the first beat heavy (bass + bass drum), the guitar's pah-pah on beats 2 and 3
    B, B'    eighths with sudden 16th double hits and a dotted-eighth figure (3+3+3+3: four against three, the triplet-feel cross-rhythm of a waltz), the line moving up and down
    A'', C   the same undulation, then bursts (one beat of eighths, two beats of sixteenths) as the push to the peak
    PEAK     bass = full 16th machine gun (12 notes a bar, feels like 4x), hats in sixteenths, and the tune ENLARGED to half and dotted-half notes held by re-striking (the contrast of the guide);
             the sixteenths are used only here (and in phase 2's last climb): at 125 BPM a sixteenth is 2.4 server ticks and wobbles a little
    calm     the piano on beats, a pad; the beat returns (quarters, then eighths) for the loop
Phase 2 'Out of Step' is the same bars and notes with the footing lost: the bass and drums fall into hemiola (two bars of 3/4 felt as three of 3/2: accents at 0, 8, 16 of 24 ticks), a statement of the tune
comes a beat late against the beat (and is cut off to catch up), the bass stings a half step above (F against E), the machine gun starts in A'' - still minor and tragic, not the major reversal of Reaper's Arc.
Sections (bars of the body): A 8, A' 8, B (the lament) 8, A'' 8, C 8, B' 8, build 4, PEAK 24, calm 8. Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from sorrow_kit import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TITLES = ("Waltz at the Edge of Dark (Boss008)", "Waltz at the Edge of Dark, Out of Step (Boss008)")
FILES = ("waltz_phase1.nbs", "waltz_phase2.nbs")
DESCRIPTIONS = ("Waltz boss, phase 1 (sorrow and fated battle): a tragic 3/4 waltz at 125 BPM in E minor, a crying violin tune over an eighth-note bass that grows to a sixteenth machine gun; same bars as phase 2.",
                "Waltz boss, phase 2: the same waltz out of step - hemiola in the bass and drums, the tune a beat late, a half-step sting in the bass, an early machine gun; still E minor.")
TPS, BAR, INTRO, BODY = 125 / 15, 12, 5, 84

# --- the tune and its sisters (12 ticks a bar: a beat is 4, an eighth 2, a sixteenth 1) ---------------------------------------
T = ["B4:8 E5:4", "D#5:4 F#5:8", "E5:8 B4:4", "C5:8 A4:4", "B4:8 E5:4", "F#5:4 E5:2 D#5:2 F#5:4", "E5:6 D5:2 C5:4", "A4:4 D#5:4 F#5:4"]
TV = ["B4:8 E5:4", "D#5:2 E5:2 F#5:8", "E5:6 D5:2 B4:4", "C5:6 B4:2 A4:4", "B4:2 B4:2 B4:4 E5:4", "F#5:6 E5:2 D#5:4", "E5:6 D5:2 C5:2 D5:2", "A4:4 D#5:4 F#5:4"]
LAMENT = ["A4:4 C5:4 E5:4", "F#5:8 D5:4", "D5:8 B4:4", "E5:8 C5:4", "A4:4 C5:4 E5:4", "F#5:8 D#5:4", "G4:4 B4:4 E5:4", "D#5:4 F#5:8"]
LAMENT2 = list(LAMENT)
LAMENT2[0] = "A4:4 C5:2 E5:6"
LAMENT2[2] = "D5:6 E5:2 B4:4"
SEQ = ["B4:8 E5:4", "D5:8 G5:4", "E5:8 A5:4", "F#5:8 B5:4", "G5:4 B5:4 E6:4", "E6:8 D6:2 C6:2", "C6:4 B5:4 A5:4", "D#6:4 F#6:8"]
# the tune ENLARGED for the peak: long notes held by re-striking (three statements; the third climbs to A5 at its end)
PK = ["B4:4 E5:8", "D#5:4 F#5:8", "E5:12", "C5:8 A4:4", "B4:4 E5:8", "F#5:8 D#5:4", "E5:12", "D#5:4 F#5:8"]
PK2 = ["B4:4 E5:8", "D#5:4 F#5:8", "E5:8 F#5:4", "C5:8 A4:4", "B4:4 E5:8", "F#5:12", "E5:4 C5:8", "D#5:4 F#5:8"]
PK3 = ["B4:4 E5:8", "D#5:4 F#5:8", "E5:8 F#5:4", "C5:4 E5:8", "B4:4 E5:8", "F#5:4 E5:4 D#5:4", "E5:8 C5:4", "A4:2 D#5:2 F#5:4 A5:4"]
CHORDS = {
    "T": ["Em", "B7", "Em", "Am", "Em", "B7", "C", "B7"],
    "W": ["Am", "D", "G", "C", "Am", "B7", "Em", "B7"],
    "C": ["Em", "G", "Am", "B7", "Em", "C", "Am|B7", "B7"],
    "BUILD": ["Em", "Em", "C", "B7"],
}

# --- patterns of a bar of 12 ticks ------------------------------------------------------------------------------------------
DRUMS = {
    "waltz": {"kick": [(0, 100), (6, 50)], "snare": [(4, 80), (8, 80)], "hat": [(t, 40 if t % 4 == 0 else 28) for t in range(0, 12, 2)]},
    "quiet": {"kick": [(0, 92)], "snare": [(8, 64)], "hat": [(4, 28), (8, 28)]},
    "drive": {"kick": [(0, 100), (6, 60), (10, 50)], "snare": [(4, 92), (8, 96), (11, 44)], "hat": [(t, 46 if t % 4 == 0 else 30) for t in range(0, 12, 2)]},
    "peak": {"kick": [(0, 100), (2, 50), (6, 70), (10, 60)], "snare": [(4, 100), (8, 100), (11, 56)], "hat": [(t, 46 if t % 4 == 0 else (36 if t % 2 == 0 else 26)) for t in range(12)],
             "tom": [(3, 50, 57), (7, 50, 54)]},
    "build": {"kick": [(0, 92), (4, 66), (8, 66)], "hat": [(t, 32) for t in range(0, 12, 2)]},
    # hemiola: two bars of 3/4 felt as three of 3/2 (the strong beats at 0, 8, 16 of the 24 ticks: bar A holds 0 and 8, bar B holds 4)
    "hemiA": {"kick": [(0, 100), (8, 96)], "snare": [(4, 84), (11, 44)], "hat": [(t, 44 if t % 4 == 0 else 26) for t in range(0, 12, 2)]},
    "hemiB": {"kick": [(4, 96), (11, 44)], "snare": [(0, 84), (8, 84)], "hat": [(t, 44 if t % 4 == 0 else 26) for t in range(0, 12, 2)], "tom": [(6, 46, 54)]},
    "hemiPA": {"kick": [(0, 100), (2, 56), (8, 96), (10, 56)], "snare": [(4, 90), (6, 60), (11, 50)], "hat": [(t, 46 if t % 4 == 0 else (36 if t % 2 == 0 else 26)) for t in range(12)]},
    "hemiPB": {"kick": [(4, 98), (6, 56), (11, 50)], "snare": [(0, 90), (2, 56), (8, 90), (10, 56)], "hat": [(t, 46 if t % 4 == 0 else (36 if t % 2 == 0 else 26)) for t in range(12)],
               "tom": [(7, 50, 54)]},
}


def eighths(accents, base=46):
    return [(t, "r", accents.get(t, base)) for t in range(0, 12, 2)]


def sixteenths(accents, octave=(3, 7, 11), fifth=(), base_even=50, base_odd=42):
    return [(t, "o" if t in octave else ("f" if t in fifth else "r"), accents.get(t, base_even if t % 2 == 0 else base_odd)) for t in range(12)]


BASS_PAT = {  # (tick, kind, velocity); an integer kind is a MIDI note
    "Q": [(0, "r", 64), (4, "r", 36), (8, "r", 36)],
    "E": eighths({0: 100, 4: 66, 8: 66}),
    # eighths with sudden 16th doubles (a note at 5 and 11 right after the eighth before it)
    "ED_a": [(0, "r", 100), (2, "r", 48), (4, "r", 66), (5, "r", 40), (6, "r", 50), (8, "r", 66), (10, "r", 50), (11, "o", 42)],
    # the same, the line moving up and down (root, fifth, third, back)
    "ED_b": [(0, "r", 100), (2, "r", 50), (4, "f", 66), (5, "f", 40), (6, "t", 52), (8, "f", 66), (10, "t", 50), (11, "r", 42)],
    # dotted eighths, 3+3+3+3: four against three
    "TRI": [(0, "r", 100), (3, "r", 60), (6, "f", 72), (9, "t", 62)],
    # one beat of eighths, then two beats of sixteenths
    "BURST": [(0, "r", 100), (2, "r", 50)] + [(t, "o" if t in (7, 11) else "r", 70 if t == 4 else 56 if t % 2 == 0 else 44) for t in range(4, 12)],
    "S16": sixteenths({0: 100, 4: 72, 8: 72}),
    "S16b": sixteenths({0: 100, 4: 72, 8: 72}, fifth=(5, 9)),
    "S16sting": [(0, "r", 100), (1, "r", 42), (2, "r", 50), (3, "o", 42), (4, "r", 72), (5, "r", 42), (6, "r", 50), (7, "o", 42)] + [(t, 41, 96 if t == 8 else 60 if t % 2 == 0 else 46) for t in range(8, 12)],
    "hemiA": eighths({0: 100, 8: 94}),
    "hemiB": [(t, "o" if t == 10 else "r", 96 if t == 4 else 46) for t in range(0, 12, 2)],
    "hemiPA": sixteenths({0: 100, 8: 96}, octave=(3, 7, 11)),
    "hemiPB": sixteenths({4: 98, 0: 50}, octave=(3, 7, 11)),
    "END": [(0, "r", 96), (4, "r", 76)],
}
GUITAR_PAT = {
    "arp": [(t, i, v) for t, i, v in zip(range(0, 12, 2), (0, 2, 3, 2, 3, 2), (52, 36, 40, 36, 40, 36))],
}
HARP_ARP = [(t, i, 30) for t, i in zip(range(0, 12, 2), (0, 1, 2, 3, 2, 1))]
HARP_RISE = [(t, i, 26) for t, i in zip(range(12), (0, 1, 2, 3, 4, 3, 2, 3, 4, 3, 2, 1))]
HARP_SLOW = [(0, 0, 34), (4, 1, 30), (8, 2, 30)]
DRONE = [(0, "r", 54), (6, "r", 34)]

# the bass of every section: one pattern a bar, [phase 1, phase 2]
PLAN = {
    "A": (["E"] * 8, ["E", "E", "E", "E", "ED_a", "ED_a", "hemiA", "hemiB"]),
    "A'": (["E"] * 6 + ["ED_a"] * 2, ["E", "E", "ED_a", "ED_a", "ED_b", "ED_b", "hemiA", "hemiB"]),
    "B": (["ED_a", "ED_b", "TRI", "ED_b", "ED_a", "ED_b", "TRI", "BURST"], ["ED_b", "TRI", "ED_b", "BURST", "hemiA", "hemiB", "BURST", "BURST"]),
    "A''": (["ED_a", "ED_b", "ED_a", "TRI", "BURST", "BURST", "BURST", "BURST"], ["ED_b", "TRI", "S16", "S16", "S16b", "S16", "hemiPA", "hemiPB"]),
    "C": (["TRI", "ED_b", "TRI", "ED_b", "BURST", "BURST", "BURST", "BURST"], ["BURST", "BURST", "S16", "S16", "S16b", "S16", "hemiPA", "hemiPB"]),
    "B'": (["E", "E", "ED_a", "E", "E", "E", "ED_a", "ED_b"], ["hemiA", "hemiB", "hemiA", "hemiB", "hemiA", "hemiB", "ED_b", "BURST"]),
    "build": (["Q", "E", "ED_b", "END"], ["E", "ED_b", "BURST", "END"]),
    "calm": (["Q"] * 4 + ["E"] * 4, ["Q"] * 4 + ["E"] * 4),
}
# the drums of the sections that are not fixed: [phase 1, phase 2]
DPLAN = {
    "A": (["waltz"] * 8, ["waltz"] * 6 + ["hemiA", "hemiB"]),
    "A'": (["waltz"] * 4 + ["drive"] * 4, ["waltz", "waltz", "drive", "drive", "drive", "drive", "hemiA", "hemiB"]),
    "A''": (["drive"] * 8, ["drive", "drive"] + ["peak"] * 4 + ["hemiPA", "hemiPB"]),
    "C": (["drive"] * 8, ["drive", "drive", "peak", "peak", "peak", "peak", "hemiPA", "hemiPB"]),
}


def build(P):
    s = Score(TPS, BAR, INTRO, BODY)

    def mel(lst, i):
        return lst[i % 8]

    pling = Voice("lead", PLING, 0, 1.0, "cry", 2, octave_fit=True)
    bit = lambda g=0.55, off=0: Voice("dbl", BIT, 0, g, "cry", 4 if off else 2, octave_fit=True, offbeat=off)
    harp_hit = lambda g=0.55: Voice("dbl", HARP, 0, g, "none", octave_fit=True)
    flute12 = lambda g=0.6, off=0: Voice("oct", FLUTE, 12, g, "cry", 4 if off else 2, octave_fit=True, offbeat=off)
    gtr_lo = lambda g=0.7, off=0: Voice("low", GUITAR, -12, g, "cry", 4 if off else 2, octave_fit=True, offbeat=off)

    def fill(j, length=6, hot=False):
        t0 = s.tick(s.b(j)) + 12 - length
        roll(s, t0, t0 + length, 1 if hot else 2, 40, 98)

    def lead(j, i, text, voices, vel, start=0, cut=False, transpose=0):
        sing(s, s.b(j + i), text, voices, transpose=transpose, vel=vel, start=start, cut=cut)

    def accompany(j, sec, i, chords, drum_pat=None, guitar=None, harp=None, drone=False, pad=False, level=1.0, drum_level=1.0, bass_level=None):
        bar = s.b(j + i)
        c = chords[i % 8]
        name = PLAN[sec][P][i]
        bass(s, bar, c, BASS_PAT[name], level if bass_level is None else bass_level, high=54)
        d = DPLAN[sec][P][i] if sec in DPLAN else (name if name.startswith("hemi") else drum_pat)
        if d:
            drums(s, bar, DRUMS[d], drum_level)
        if drone:
            bass(s, bar, c, DRONE, level, role="drone", instrument=DIDGERIDOO)
        if guitar == "pah":
            for t in (4, 8):
                stab(s, bar, t, c, "gtr", GUITAR, 47, 64, (54 if t == 4 else 46) * level, count=3)
        elif guitar:
            notes_from(c, GUITAR_PAT[guitar], "gtr", GUITAR, 45, 66, s, bar, level)
        if harp:
            notes_from(c, harp, "arp", HARP, 58, 77, s, bar, level)
        if pad:
            stab(s, bar, 0, c, "pad", PLING, 55, 72, 26 * level, count=2, length=12, every=4, style="flat")

    TC, WC = CHORDS["T"], CHORDS["W"]

    # --- intro: five bars (after a silence of four ticks): the tune alone over quarter-note roots; no drums; phase 2 comes in a beat late and stumbles on ------------
    late = 4 if P else 0
    iv = [Voice("lead", PLING, 0, 0.8, "cry", 2, octave_fit=True), harp_hit(0.5)] + ([flute12(0.5)] if P else [])
    for k, ti in enumerate((0, 1, 2, 6, 7)):
        sing(s, k, T[ti], iv, vel=72 if P == 0 else 84, start=late if k < 2 else 0, cut=(k == 1))
        c = TC[ti]
        bass(s, k, c, BASS_PAT["Q"], 0.8 if P == 0 else 1.0, high=54)
        notes_from(c, [(t, kk, 30 + 3 * k) for t, kk in zip((2, 6, 10), (1, 2, 3))], "arp", HARP, 58, 77, s, k)
    if P == 1:
        s.drum("kick", s.tick(0), BASEDRUM, 96)
        drums(s, 1, DRUMS["hemiA"], 0.7)
        drums(s, 2, DRUMS["hemiB"], 0.7)
    roll(s, s.tick(4, 6), s.tick(5), 1 if P else 2, 34 if P == 0 else 44, 84 if P == 0 else 100)

    # --- A (8): the tune on the pling, picking and the pah-pah, a heavy first beat; the bass: tight eighths; phase 2 falls into hemiola at the end ---
    for i in range(8):
        v = [pling, harp_hit(0.5)] + ([flute12(0.45, 2)] if i >= 4 else [])
        lead(0, i, T[i], v, 86 if P == 0 else 90)
        accompany(0, "A", i, TC, None, "pah", None, level=0.9, drum_level=0.9)
    fill(7, 6, hot=bool(P))

    # --- A' (8): the tune again, bit and flute join, the harp circles; phase 2: the statement a beat late against the beat -----------------------
    for i in range(8):
        v = [pling, bit(0.6, 2), flute12(0.6, 2)] + ([gtr_lo(0.7, 2)] if i >= 4 else [])
        displaced = P == 1 and i >= 4
        lead(8, i, mel(TV, i), v, 88 if P == 0 else 92, start=4 if displaced else 0, cut=displaced and i == 7)
        accompany(8, "A'", i, TC, None, "pah", HARP_ARP if i >= 4 else None, drone=(i >= 4), level=0.95)
    fill(8 + 3, 4, hot=True)
    fill(8 + 7, 6, hot=True)

    # --- B (8): the lament - a flute and a pling, a pad, a slow harp, a half-time beat; the bass undulates (doubles, dotted eighths, up and down) ---
    for i in range(8):
        lead(16, i, LAMENT[i], [Voice("lead", FLUTE, 12, 1.0, "cry", 2, octave_fit=True), Voice("dbl", PLING, 0, 0.7, "cry", 4, octave_fit=True, offbeat=2),
                                Voice("echo", HARP, 0, 0.3, "none", delay=4, octave_fit=True)], 84 if P == 0 else 88)
        accompany(16, "B", i, WC, "quiet", "arp" if i >= 4 else None, HARP_SLOW if i < 4 else None, pad=True, level=0.9, drum_level=0.85, bass_level=1.0)
    roll(s, s.tick(s.b(16 + 7), 6), s.tick(s.b(16 + 8)), 2, 30, 80)

    # --- A'' (8): the bit and the harp lead, the flute above; the second half the guitar answers an octave down; bursts of sixteenths ---------
    for i in range(8):
        v = [Voice("lead", BIT, 0, 0.9, "cry", 2, octave_fit=True), harp_hit(0.7), flute12(0.7, 2)] + ([pling] if i >= 4 else []) + ([gtr_lo(0.7, 2)] if i >= 4 else [])
        displaced = P == 1 and i >= 4
        lead(24, i, mel(TV, i), v, 90 if P == 0 else 94, start=2 if displaced else 0, cut=displaced and i == 7)
        accompany(24, "A''", i, TC, None, "pah", HARP_ARP if i >= 4 else None, drone=(i >= 4))
    fill(24 + 3, 4, hot=True)
    fill(24 + 7, 6, hot=True)

    # --- C (8): the tune climbing in sequence: the pling takes it as far as it can sound, the flute carries on above -------------------------
    for i in range(8):
        v = [Voice("lead", PLING, 0, 1.0, "cry", 2, high=77), Voice("lead", FLUTE, 0, 1.0, "cry", 2, low=78), bit(0.55, 2), Voice("low", GUITAR, -12, 0.6, "cry", 4, offbeat=2, octave_fit=True)]
        if i >= 4:
            v.append(Voice("dbl", PLING, -12, 0.8, "cry", 4, offbeat=2, octave_fit=True))
        lead(32, i, SEQ[i], v, 92)
        accompany(32, "C", i, CHORDS["C"], None, "arp" if i < 4 else None, None, drone=True)
    fill(32 + 3, 4, hot=True)
    fill(32 + 7, 6, hot=True)

    # --- B' (8): the lament on the piano, a low guitar answering, a pad and a drone ------------------------------------------------------------
    for i in range(8):
        lead(40, i, LAMENT2[i], [Voice("lead", HARP, 0, 1.0, "cry", 2, octave_fit=True), bit(0.35, 2), Voice("low", GUITAR, -12, 0.55, "cry", 4, octave_fit=True, offbeat=2),
                                 Voice("echo", FLUTE, 12, 0.35, "none", delay=6, octave_fit=True)], 88 if P == 0 else 92)
        accompany(40, "B'", i, WC, "quiet", None, None, drone=True, pad=True, level=0.85, drum_level=0.8)
    roll(s, s.tick(s.b(40 + 7), 6), s.tick(s.b(40 + 8)), 2, 30, 78)

    # --- build (4): long notes climbing (B4 E5 G5 F#5) struck again and again, the bass climbing the ladder, a roll, a gap before the peak ------------
    for k, n in enumerate((71, 76, 79, 78)):
        t0 = s.tick(s.b(48), 12 * k)
        pitch = n if n <= 78 else n - 12
        length = 12 if k < 3 else 8
        held(s, "lead", PLING, t0, pitch, length, 46 + 8 * k, 84 + 4 * k, every=2)
        held(s, "oct", FLUTE, t0, fit(FLUTE, n + 12), length, 30 + 8 * k, 70 + 4 * k, every=4)
        if k >= 1:
            held(s, "dbl", BIT, t0, pitch, length, 20, 60, every=4)
    for i in range(4):
        bar = s.b(48 + i)
        c = CHORDS["BUILD"][i]
        bass(s, bar, c, BASS_PAT[PLAN["build"][P][i]], 0.95 if i < 3 else 1.0, high=54)
        if i < 3:
            notes_from(c, GUITAR_PAT["arp"], "gtr", GUITAR, 45, 66, s, bar, 0.9)
            notes_from(c, [(t, 2 + (t // 2) % 3, 22 + 4 * i) for t in range(1, 12, 2)], "arp", HARP, 58, 77, s, bar)
        if 1 <= i < 3:
            drums(s, bar, DRUMS["build"] if P == 0 else DRUMS["drive"], 0.75 + 0.05 * i)
    roll(s, s.tick(s.b(50), 6), s.tick(s.b(51)), 2, 40, 80)
    roll(s, s.tick(s.b(51)), s.tick(s.b(51), 6), 1, 60, 100)
    for t, v in ((8, 70), (10, 85), (11, 100)):              # the gap (ticks 6 and 7 are empty), then a drum pick-up
        s.drum("kick", s.tick(s.b(51), t), BASEDRUM, v)

    # --- PEAK (24): the tune ENLARGED (long notes) three times as a unison (pling, bit, the flute above, the guitar below); under it a full sixteenth machine gun,
    #     hats in sixteenths; the third time the harp circles and the end climbs to A5; in phase 2 the second statement falls into hemiola and the bass stings at the end -----
    for r in range(3):
        j0 = 52 + 8 * r
        for i in range(8):
            bar = s.b(j0 + i)
            text = (PK, PK2, PK3)[r][i]
            v = [pling, bit(0.6, 2), flute12(0.75, 2), gtr_lo(0.75, 2)] + ([harp_hit(0.55)] if r >= 1 else [])
            displaced = P == 1 and r == 2 and i < 7
            sing(s, bar, text, v, vel=96, start=4 if displaced else 0, cut=displaced and i == 6)
            c = TC[i]
            hemi = P == 1 and r == 1
            if hemi:
                name = "hemiPA" if i % 2 == 0 else "hemiPB"
            elif P == 1 and i == 7:
                name = "S16sting"
            else:
                name = "S16b" if (r == 2 and i % 2) else "S16"
            bass(s, bar, c, BASS_PAT[name], 1.0, high=54)
            drums(s, bar, DRUMS[name if hemi else "peak"], 1.0)
            if i % 2 == 0:
                bass(s, bar, c, DRONE, 1.0, role="drone", instrument=DIDGERIDOO)
            if r == 2:
                notes_from(c, HARP_RISE, "arp", HARP, 58, 77, s, bar, 0.8)
            elif r == 1:
                notes_from(c, [(4, 3, 44), (8, 3, 44)], "arp", HARP, 58, 77, s, bar)
            elif i % 2 == 0:
                for t in (4, 8):
                    stab(s, bar, t, c, "gtr", GUITAR, 47, 64, 46, count=3)
        stab(s, s.b(j0), 0, "Em", "stab", PLING, 57, 77, 92, count=3, length=6, every=2)
        s.drum("tom", s.tick(s.b(j0)), BASEDRUM, 100, 54)
        fill(j0 + 3, 4, hot=True)
        fill(j0 + 7, 6, hot=bool(P))

    # --- calm (8): the piano, a pad, a low drum; then the beat returns (quarters, then eighths) for the loop ------------------------------------------------
    for i in range(8):
        bar = s.b(76 + i)
        if i < 5:
            v = [Voice("lead", HARP, 0, 1.0, "cry", 2, octave_fit=True), Voice("dbl", PLING, 0, 0.5, "cry", 4, octave_fit=True, offbeat=2), Voice("echo", FLUTE, 12, 0.3, "cry", 4, octave_fit=True)]
        else:
            v = [pling, harp_hit(0.5), flute12(0.5, 2)]
        sing(s, bar, T[i], v, vel=70 if i < 4 else 86)
        c = TC[i]
        bass(s, bar, c, BASS_PAT[PLAN["calm"][P][i]], 0.8 if i < 4 else 0.95, high=54)
        if i < 4:
            stab(s, bar, 0, c, "pad", PLING, 55, 72, 26, count=2, length=12, every=4, style="flat")
        notes_from(c, GUITAR_PAT["arp"], "gtr", GUITAR, 45, 66, s, bar, 0.6 if i < 4 else 0.9)
        if i >= 2:
            drums(s, bar, DRUMS["quiet"] if i < 6 else DRUMS["waltz"], 0.8 if i < 6 else 0.85)
    roll(s, s.tick(s.b(76 + 7), 6), s.tick(s.b(76 + 8)), 2, 40, 86)

    return s


SECTIONS = [("A", 0, 8), ("A'", 8, 8), ("B", 16, 8), ("A''", 24, 8), ("C", 32, 8), ("B'", 40, 8), ("build", 48, 4), ("PEAK", 52, 24), ("calm", 76, 8)]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    for phase in (0, 1):
        score = build(phase)
        finish(score, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], SECTIONS)


if __name__ == "__main__":
    main()
