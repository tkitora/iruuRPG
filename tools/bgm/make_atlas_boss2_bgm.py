#!/usr/bin/env python3
"""Makes atlas_vindicator_2.nbs: "Raise the Axe (Boss002)", the axe-wielding guardian of the Atlas cave (ONE song, no second phase) - class 5, the dry death match and wildness.

    python3 tools/bgm/make_atlas_boss2_bgm.py            writes atlas_vindicator_2.nbs into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss2_bgm.py outfile    writes it somewhere else (a file, or a folder)

t277: STYLE 4 of the style doc ("Japanese melodic dash", base 165 BPM) on the 16th-note machine-gun ladder of t276.
165 BPM: 11 ticks a second (165 / 15), a tick is a sixteenth, a beat 4 ticks, a bar 16 = the engine's bar line. 4 bars of intro + 92 bars = 1536 ticks = 139.6 s.
The MELODY keeps the base tempo; the bass and the drums are subdivided so that the song feels two to four times faster. Time table (style doc -> this song):
  0:00  WATERFALL (4 intro bars + 8 bars of the loop, to 0:17): a piano-like (harp) arpeggio falls in sixteenths over the chord, wadaiko and quarter-note bass roots; from bar 4 the tune is foreshadowed.
  0:17  MAIN 1 (30 bars, to 1:01): the oriental tune (in scale on F#: F# G B C# D, a flat second above the root) on the flute, the banjo plucking under it. The bass runs ROOT - FIFTH - ROOT in eighths
        (bars 0-11); at bar 12 the root is released: the line climbs and falls with sixteenth double hits and triplet figures (the B level). The fifth bars of every cycle end in a harp run.
  1:01  HALF-TIME (20 bars, to 1:30): the MODE FLIPS - from the dark in scale (flat second) to the natural minor (G# A E: sad, warm chords i - VI - III - VII - iv - i - V - V). The drums are halved (kick on 1, snare on 3),
        the bass walks in quarters, a harp melody in long notes sings over a soft pling arpeggio (half-ed melody, 'the boss is sad').
  1:30  BUILD-UP (6 bars, to 1:39): the melody is gone; a snare/kick CRESCENDO from quarters to sixteenths on a C# pedal that climbs to E (the dominant of the new key) and an empty last beat.
  1:39  CHORUS (28 bars, to 2:19): up a MINOR THIRD (F# -> A) AND an octave: the sad tune explodes on the flute in long notes (held by re-striking) over a full SIXTEENTH slap bass (thumb: low root, pop: octave, ghost notes),
        guitar chug + power chords, shaker and drums in sixteenths; at bar 8 a harmony a fifth below, at bar 16 a banjo/cow bell wall; the last 4 bars slide back to F# (D - A - E - C#) and a harp waterfall climbs into tick 64.
Checklist: the root is released at 0:17 + 17 s, no root repeats of 20 s; the break/pseudo tempo change is the half-time section; the mode flip is the natural minor; the build-up leaves a gap before the chorus.
Colour of the class: wadaiko (bass drum) and djembe (snare), a thick bass with the didgeridoo in unison, a rough guitar, a banjo (shamisen), a flute (shakuhachi); wild before pretty.
Everything is written by what SOUNDS. Nothing is random (a hash of the tick and the place in the 4-bar cycle). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat45 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS = 165 / 15                    # 11 ticks a second; a tick is a sixteenth
BAR_T = 16
START = 64
SECTIONS = [("WATER", 8), ("MAIN", 30), ("HALF", 20), ("BUILD", 6), ("CHORUS", 28)]
NBARS = sum(n for _, n in SECTIONS)   # 92 bars = 133.8 s + the 5.8 s intro
LENGTH = START + NBARS * BAR_T
MIN3, MAJ3 = (0, 3, 7), (0, 4, 7)
# the 8-bar cycles: roots (semitones above F#) and chord qualities
MAIN_ROOTS = [0, 0, 5, 7, 0, 8, 5, 7]
MAIN_Q = [MIN3, MIN3, MIN3, MAJ3, MIN3, MAJ3, MIN3, MAJ3]
SAD_ROOTS = [0, 8, 3, 10, 5, 0, 7, 7]               # F#m D A E Bm F#m C# C#  (i VI III VII iv i V V)
SAD_Q = [MIN3, MAJ3, MAJ3, MAJ3, MIN3, MIN3, MAJ3, MAJ3]
CHORUS_KEY = 3                                       # a minor third up: F# -> A
VEL = {"X": 100, "x": 74, "o": 44, "g": 24}
# the wadaiko (bass drum), four bars (call, short call, call, fill) per drum level; the verses keep to eighths (a figure of two sixteenths is still fine)
LOW = {
    1: ["X...x...X...x...", "X...x...X.......", "X...x...X...x...", "X...x...X.x.x.xX"],
    2: ["X.o.x.o.X.o.x.o.", "X.o.x.o.X.o.x.x.", "X.o.x.o.X.o.x.o.", "X.o.x.o.X.x.xxXX"],
    3: ["X.xox.o.X.xox.o.", "X.xox.o.X.xoxxo.", "X.xox.o.X.xox.o.", "X.xoxoxxXoxoxXxX"],
    4: ["X.xoX.x.X.xoX.x.", "X..xX.o.X..xX.xx", "X.xoX.x.X.xoX.x.", "X.xoXoxxXoxoXXXX"],
    5: ["X.xxX.xoX.xxX.xo", "X.xxX.xoX.xxXxxx", "X.xxX.xoX.xxX.xo", "XxxxXoxxXxxxXXXX"],
}
for pattern in LOW.values():
    assert all(len(p) == 16 for p in pattern), pattern
HIGH_DENSITY = {1: 8, 2: 16, 3: 30, 4: 48, 5: 58, 6: 68, 7: 76}
# the bass offsets above the root: level 2 = root - fifth - root in eighth slots (tick // 2); level 3 = climbing and falling by eighth slots
RFR = [[0, 7, 0, 7, 0, 7, 0, 7], [0, 7, 0, 7, 0, 7, 0, 5], [0, 7, 0, 7, 0, 7, 0, 7], [0, 7, 0, 7, 5, 7, 8, 7]]
MOVE3 = [[0, 0, 7, 0, 5, 0, 8, 7], [0, 5, 7, 12, 7, 5, 1, 0], [0, 0, 8, 7, 5, 7, 12, 8], [0, 5, 7, 8, 12, 8, 7, 13]]
# slap bass of the chorus: the interval of each beat above the root of the bar (thumb = low, pop = octave on the third sixteenth, ghost notes between)
SLAP = [[0, 0, 7, 0], [0, 5, 7, 5], [0, 0, 7, 10], [0, 5, 7, 7]]
# the tune of the main part (flute), semitones above F#4 (+12 on the flute), (tick, offset, length), 8 bars: a call and a higher answer, both ending hanging up
MAIN_TUNE = [
    [(0, 12, 4), (4, 8, 2), (6, 7, 2), (8, 5, 4), (12, 7, 4)],
    [(0, 8, 4), (4, 7, 2), (6, 5, 2), (8, 1, 4), (12, 0, 2), (14, 1, 2)],
    [(0, 12, 4), (4, 13, 2), (6, 12, 2), (8, 8, 4), (12, 7, 2), (14, 8, 2)],
    [(0, 12, 6), (6, 13, 2), (8, 17, 4), (12, 19, 4)],
    [(0, 17, 4), (4, 13, 2), (6, 12, 2), (8, 8, 4), (12, 12, 4)],
    [(0, 13, 4), (4, 12, 2), (6, 8, 2), (8, 7, 4), (12, 8, 2), (14, 12, 2)],
    [(0, 17, 6), (6, 19, 2), (8, 20, 4), (12, 19, 2), (14, 17, 2)],
    [(0, 19, 6), (6, 20, 2), (8, 19, 4), (12, 20, 4)],
]
# the sad tune (half-time: harp at 54 + offset, F#3 up; chorus: an octave and a minor third up on the flute, 69 + offset), (tick, offset, length), 8 bars over SAD_ROOTS
SAD_TUNE = [
    [(0, 15, 8), (8, 19, 4), (12, 15, 4)],
    [(0, 12, 8), (8, 15, 4), (12, 20, 4)],
    [(0, 19, 8), (8, 22, 4), (12, 19, 4)],
    [(0, 17, 6), (6, 22, 2), (8, 14, 8)],
    [(0, 17, 8), (8, 20, 4), (12, 17, 4)],
    [(0, 15, 8), (8, 19, 4), (12, 22, 4)],
    [(0, 14, 8), (8, 17, 4), (12, 14, 4)],
    [(0, 19, 6), (6, 17, 2), (8, 23, 8)],
]
assert min(o for bar in SAD_TUNE for _, o, _ in bar) >= 9 and max(o for bar in SAD_TUNE for _, o, _ in bar) <= 23
ROLES = {"flute": (100, 100), "harm": (80, 90), "chant": (100, 80), "cow": (80, 120), "shout": (90, 100), "water": (100, 85), "arp": (60, 115), "guitar": (95, 100), "guitar2": (60, 100),
         "bass": (100, 100), "didg": (100, 100), "kick": (100, 100), "snare": (92, 105), "hat": (50, 115)}


def build_map():
    result = []
    for name, n in SECTIONS:
        for ib in range(n):
            result.append((name, ib))
    return result


MAP = build_map()


def where(bar):
    return MAP[bar]


def tune_index(name, ib):
    if name == "CHORUS" and ib >= 24:
        return 4 + (ib - 24)
    return ib % 8


def root_of(bar):
    """Semitones above F# of the root of the bar (and the key shift is already inside)."""
    name, ib = where(bar)
    idx = tune_index(name, ib)
    if name in ("WATER", "MAIN"):
        return MAIN_ROOTS[idx]
    if name == "HALF":
        return SAD_ROOTS[idx]
    if name == "BUILD":
        return (7, 7, 7, 7, 8, 10)[ib]
    if ib == 27:
        return 7                                          # the way back: C#, the dominant of F#
    return SAD_ROOTS[idx] + CHORUS_KEY


def quality_of(bar):
    name, ib = where(bar)
    idx = tune_index(name, ib)
    if name in ("WATER", "MAIN"):
        return MAIN_Q[idx]
    if name == "BUILD":
        return MAJ3
    return SAD_Q[idx] if ib != 27 or name != "CHORUS" else MAJ3


class Song:
    def __init__(self):
        self.b = Builder(LENGTH, ROLES)

    # ---- drums
    def low_high(self, t0, bar, dl, bc, big=False):
        """The wadaiko (bass drum) and djembe (snare) of one bar, in call and response."""
        b = self.b
        low = LOW[min(dl, 5)][bc]
        hi_level = HIGH_DENSITY[dl]
        peak = dl >= 5
        low_events = {tick: VEL[ch] for tick, ch in enumerate(low) if ch != "."}
        if dl in (6, 7):                                          # the gallop: ghost strokes between the accents, sixteenths
            for tick in range(16):
                if tick not in low_events and tick % 4 in (1, 3) and hash_(tick, bc, 3) % 3:
                    low_events[tick] = 36
        if bc == 1:                                               # the response bar: the low drum stops at the half bar
            for tick in list(low_events):
                if tick >= 10:
                    del low_events[tick]
        high_events = {}
        if dl != 1:
            high_events[4] = 84 if dl < 4 else 92
            high_events[12] = 84 if dl < 4 else 92
        for tick in range(16):
            if tick in low_events or tick in high_events:
                continue
            if not peak and tick % 2:                              # the verses keep to eighths for the djembe too (a pair of sixteenths only inside the fill)
                continue
            if bc == 1 and tick >= 8:
                if hash_(tick, bc, 5) % 100 < min(95, hi_level + 45):
                    high_events[tick] = 84 if tick % 2 == 0 else 54
            elif hash_(tick, bc, 4) % 100 < hi_level:
                high_events[tick] = 70 if tick % 4 == 2 else 44
        if bc == 3 or big:                                         # the fill of the cycle: a roll of the two drums, rising
            start = 4 if big else 8
            for index, tick in enumerate(range(start, 16)):
                v = int(34 + 66 * index / max(1, 15 - start))
                if tick % 4 == 0 or (tick > 11 and tick % 2 == 0):
                    low_events[tick] = max(low_events.get(tick, 0), min(100, v + 8))
                else:
                    high_events[tick] = max(high_events.get(tick, 0), v)
                    if tick > 11:
                        low_events.pop(tick, None)
        for tick, vel in low_events.items():
            b.drum("kick", t0 + tick, BASEDRUM, vel)
        for tick, vel in high_events.items():
            b.drum("snare", t0 + tick, SNARE, vel)
        if dl in (3, 4):                                           # the shaker in eighths
            for tick in range(0, 16, 2):
                b.drum("hat", t0 + tick, HAT, 30 if tick % 4 == 0 else 20)
        elif peak:                                                 # the shaker in sixteenths
            for tick in range(16):
                b.drum("hat", t0 + tick, HAT, 36 if tick % 4 == 0 else (28 if tick % 2 == 0 else 18))

    def half_drums(self, t0, ib):
        """Half-time: half the strokes - the wadaiko on 1, the djembe on 3, a quiet shaker on the beats; every fourth bar a small fill."""
        b = self.b
        b.drum("kick", t0, BASEDRUM, 100)
        b.drum("snare", t0 + 8, SNARE, 96)
        if ib % 2 == 1:
            b.drum("kick", t0 + 10, BASEDRUM, 56)
        for tick in (4, 12):
            b.drum("hat", t0 + tick, HAT, 30)
        if ib % 4 == 3:
            for index, tick in enumerate((12, 13, 14, 15)):
                b.drum("snare", t0 + tick, SNARE, 50 + 12 * index, once=True)
            b.drum("kick", t0 + 12, BASEDRUM, 80, once=True)

    # ---- bass, didgeridoo, guitar
    def bass_hits(self, t0, bar, bl, bc):
        """The bass of the sections up to the B level. bl 0 quarters, 1 eighth roots, 2 root - fifth - root, 3 climbing with doubles and triplet figures."""
        b = self.b
        root = root_of(bar)
        ticks = hit_ticks(bl, "ST", bar % 5 + 3 * (bar // 8))
        for tick in ticks:
            if bl <= 1:
                off = 0
            elif bl == 2:
                off = RFR[bc][tick // 2]
            else:
                off = MOVE3[bc][tick // 2]
            vel = bass_velocity(tick, bl)
            if bl == 0:
                vel = 100 if tick % 8 == 0 else 88
            pitch = 30 + root + off
            b.note("bass", t0 + tick, BASS, pitch, vel)
            heavy = tick % 4 == 0 if bl >= 3 else tick % 8 == 0
            if heavy and bl >= 1:
                b.note("didg", t0 + tick, DIDGERIDOO, pitch, vel * 0.9)
            if bl >= 2 and tick % 2 == 0:
                b.note("guitar", t0 + tick, GUITAR, 42 + (root + off) % 12, 58 + (8 if tick % 4 == 0 else 0))
        if bl == 0:
            b.note("didg", t0, DIDGERIDOO, 30 + root, 90)
            b.note("didg", t0 + 8, DIDGERIDOO, 30 + root, 76)
            if bc == 0:
                b.note("guitar2", t0, GUITAR, 42 + root % 12, 84, once=True)
            b.note("bass", t0 + 14, BASS, 30 + root + (7 if bc % 2 else 12), 52, once=True)

    def slap(self, t0, bar, bc, ib):
        """The chorus bass: sixteenths, slap-like - the thumb on the root (heavy), a ghost note, the pop an octave up, a ghost note - and the root moves with the beats."""
        b = self.b
        root = root_of(bar)
        pc = (6 + root) % 12
        low = near(pc, 34, BASS)
        for tick in range(16):
            beat, pos = divmod(tick, 4)
            iv = SLAP[bc][beat]
            pitch = low + iv
            if pos == 0:
                vel = 100
            elif pos == 2:
                vel = 84
                pitch += 12                                   # the pop (folded down again when it would leave the bass)
            else:
                vel = 52 if pos == 1 else 58
            b.note("bass", t0 + tick, BASS, pitch, vel)
            if pos in (0, 2):
                b.note("didg", t0 + tick, DIDGERIDOO, low + iv, 90 if pos == 0 else 76)
            if tick % 2 == 0:
                b.note("guitar", t0 + tick, GUITAR, near((pc + iv) % 12, 48, GUITAR), 64 + (10 if tick % 4 == 0 else 0))
        for tick in range(0, 16, 2):
            b.note("guitar2", t0 + tick, GUITAR, near((pc + 7) % 12, 54, GUITAR), 88 if tick % 8 == 0 else (74 if tick % 4 == 0 else 58))

    # ---- melodies
    def water(self, t0, bar, fall=True, loud=92, bars16=True):
        """A piano-like arpeggio falling in sixteenths over the chord (harp, F#3 to F#5): from the top, down through all the chord tones in range, again and again."""
        b = self.b
        root = root_of(bar)
        quality = quality_of(bar)
        tones = sorted((p for p in range(54, 79) if (p - 66 - root) % 12 in quality), reverse=fall)
        for tick in range(16):
            pitch = tones[tick % len(tones)]
            b.note("water", t0 + tick, HARP, pitch, loud - 3 * tick)

    def run_up(self, t0, bar):
        """A harp run that climbs through the chord in the last eight sixteenths of a cycle."""
        b = self.b
        root = root_of(bar)
        quality = quality_of(bar)
        tones = sorted(p for p in range(54, 79) if (p - 66 - root) % 12 in quality)
        for index, tick in enumerate(range(8, 16)):
            b.note("water", t0 + tick, HARP, tones[index % len(tones)], 50 + 6 * index, once=True)

    def arpeggio(self, t0, bar, vel):
        """The soft pling arpeggio under the sad tune: eighths up and down the chord."""
        b = self.b
        root = root_of(bar)
        quality = quality_of(bar)
        tones = sorted(p for p in range(54, 73) if (p - 66 - root) % 12 in quality)
        order = [0, 1, 2, 3, 2, 1, 2, 1]
        for index, tick in enumerate(range(0, 16, 2)):
            b.note("arp", t0 + tick, PLING, tones[order[index] % len(tones)], vel - (6 if index % 2 else 0))

    def flute_tune(self, t0, table, pitch_base, quiet=1.0, banjo=True, harmony=False, every=2):
        b = self.b
        for tick, off, length in table:
            pitch = pitch_base + off
            if length >= 4:
                sustain(b, "flute", t0 + tick, FLUTE, pitch, length, 84 * quiet, 96 * quiet, every=every)
            else:
                b.note("flute", t0 + tick, FLUTE, pitch, 88 * quiet)
            if banjo:
                b.note("chant", t0 + tick, BANJO, pitch - 24, 90 * quiet, once=True)
            if harmony:
                sustain(b, "harm", t0 + tick, FLUTE, pitch - 7, length, 60, 72, every=4)

    def long_tune(self, t0, table, pitch_base, instrument_roles, quiet=1.0, harmony=False, wall=False):
        """The sad tune in long notes: whole and half notes held by re-striking every 4 ticks (a swell)."""
        b = self.b
        for tick, off, length in table:
            pitch = pitch_base + off
            for role, inst, vel in instrument_roles:
                sustain(b, role, t0 + tick, inst, pitch, length, vel * quiet * 0.97, vel * quiet, every=4)
            if harmony:
                sustain(b, "harm", t0 + tick, FLUTE, pitch - 7, length, 62, 74, every=4)

    def shout(self, t0):
        """A raw flute shout: a scoop up to F#6, re-struck, then a leap up that is left hanging."""
        b = self.b
        b.note("shout", t0 + 7, FLUTE, 89, 62, once=True)
        for index, tick in enumerate(range(8, 12, 2)):
            b.note("shout", t0 + tick, FLUTE, 90, 70 + 3 * index, once=True)
        b.note("shout", t0 + 12, FLUTE, 93, 90, once=True)
        b.note("shout", t0 + 14, FLUTE, 97, 96, once=True)

    # ---- the sections
    def bar(self, bar):
        t0 = START + bar * BAR_T
        name, ib = where(bar)
        bc = bar % 4
        idx = tune_index(name, ib)
        root = root_of(bar)
        if name == "WATER":
            self.low_high(t0, bar, 1, bc, big=ib == 7)
            self.bass_hits(t0, bar, 1 if ib < 4 else 2, bc)
            if ib < 4 or ib in (4, 5):
                self.water(t0, bar, loud=88 if ib < 2 else 74)
            if ib >= 4:
                self.flute_tune(t0, MAIN_TUNE[idx], 78, quiet=0.6, banjo=True)
        elif name == "MAIN":
            released = ib >= 12
            self.low_high(t0, bar, 2 if ib < 12 else (3 if ib < 24 else 4), bc, big=ib in (11, 23, 29))
            self.bass_hits(t0, bar, 3 if released else 2, bc)
            self.flute_tune(t0, MAIN_TUNE[idx], 78, banjo=True)
            if ib % 8 == 7:
                self.run_up(t0, bar)
            if ib in (11, 23):
                self.shout(t0)
            if ib == 12:
                self.hit(t0, bar)
        elif name == "HALF":
            self.half_drums(t0, ib)
            self.bass_hits(t0, bar, 0, bc)
            self.arpeggio(t0, bar, 46)
            self.long_tune(t0, SAD_TUNE[idx], 54, [("water", HARP, 100), ("flute", PLING, 88)])
            if ib % 4 == 0:
                self.hit(t0, bar)
        elif name == "BUILD":
            self.build(t0, bar, ib)
        else:
            self.chorus(t0, bar, ib, bc, idx)

    def build(self, t0, bar, ib):
        """No melody. A crescendo of the drums from quarters to sixteenths on a C# pedal that climbs to E; the last beat is left empty."""
        b = self.b
        root = root_of(bar)
        density = (4, 4, 8, 8, 16, 16)[ib]
        step = 16 // density
        for index, tick in enumerate(range(0, 16, step)):
            if ib == 5 and tick > 13:
                continue
            v = int(30 + 70 * (ib * 16 + tick) / (6 * 16 - 1))
            b.drum("snare", t0 + tick, SNARE, v, once=True)
        for beat in range(4):
            tick = beat * 4
            if ib == 5 and tick > 13:
                continue
            b.drum("kick", t0 + tick, BASEDRUM, int(40 + 60 * (ib * 16 + tick) / (6 * 16 - 1)), once=True)
        if ib >= 2:
            for tick in range(0, 16, 2 if ib < 4 else 1):
                if ib == 5 and tick > 13:
                    continue
                b.drum("hat", t0 + tick, HAT, int(20 + 26 * (ib * 16 + tick) / (6 * 16 - 1)), once=True)
        ticks = hit_ticks(1 if ib < 2 else (3 if ib < 4 else 4), "ST", ib)
        for tick in ticks:
            if ib == 5 and tick > 13:
                continue
            pitch = 30 + root
            if ib >= 4 and tick % 4 == 2:
                pitch += 12
            vel = int(60 + 40 * (ib * 16 + tick) / (6 * 16 - 1))
            b.note("bass", t0 + tick, BASS, pitch, vel)
            if tick % 4 == 0:
                b.note("didg", t0 + tick, DIDGERIDOO, 30 + root, vel * 0.9)
            if ib >= 2 and tick % 2 == 0:
                b.note("guitar", t0 + tick, GUITAR, 42 + root % 12, 50 + 3 * ib)

    def chorus(self, t0, bar, ib, bc, idx):
        b = self.b
        root = root_of(bar)
        dl = 5 if ib < 8 else (6 if ib < 16 else 7)
        if ib >= 24:
            dl = 7
        self.low_high(t0, bar, dl, bc, big=ib in (7, 15, 23, 26))
        if ib == 27:
            self.final_bar(t0, bar)
            return
        self.slap(t0, bar, bc, ib)
        table = SAD_TUNE[idx]
        harmony = ib >= 8
        self.long_tune(t0, table, 69, [("flute", FLUTE, 100)], harmony=harmony)
        for tick, off, length in table:                          # the banjo plucks every onset two octaves below
            b.note("chant", t0 + tick, BANJO, 69 + off - 24, 92, once=True)
        if ib in (0, 8, 16, 24):
            self.hit(t0, bar)

    def final_bar(self, t0, bar):
        """The way back: the bass slides D# - D - C# in sixteenths, a harp waterfall climbs into the first note of the loop (F# minor)."""
        b = self.b
        for tick in range(16):
            pitch = 30 + (10, 9, 8, 7)[tick // 4]
            b.note("bass", t0 + tick, BASS, pitch + (12 if tick % 4 == 2 else 0), 100 if tick % 4 == 0 else 60)
        for index, tick in enumerate(range(16)):
            tones = sorted(p for p in range(54, 79) if (p - 66 - 7) % 12 in MAJ3)
            b.note("water", t0 + tick, HARP, tones[(index * 1) % len(tones)] if index < len(tones) else tones[-1], 40 + 4 * index, once=True)
        for index, tick in enumerate(range(0, 16, 2)):
            b.note("shout", t0 + tick, FLUTE, 90 + index, 70 + 4 * index, once=True)

    def hit(self, t0, bar):
        """A unison hit: the wadaiko, the djembe, the bass, the didgeridoo, the guitar, the banjo."""
        b = self.b
        root = root_of(bar)
        b.drum("kick", t0, BASEDRUM, 100, once=True)
        b.drum("snare", t0, SNARE, 96, once=True)
        low = near((6 + root) % 12, 34, BASS)
        b.note("bass", t0, BASS, low, 100, once=True)
        b.note("didg", t0, DIDGERIDOO, low, 100, once=True)
        b.note("guitar", t0, GUITAR, near((6 + root) % 12, 46, GUITAR), 96, once=True)
        b.note("guitar2", t0, GUITAR, near((13 + root) % 12, 53, GUITAR), 86, once=True)

    def intro(self):
        """4 bars: the harp waterfall (F# minor, F# minor, B minor, C# major) over quarter-note roots of bass and wadaiko, soft; a rising roll in the last half bar."""
        b = self.b
        chords = [(0, MIN3), (0, MIN3), (5, MIN3), (7, MAJ3)]
        for bar, (root, quality) in enumerate(chords):
            t0 = 16 * bar
            tones = sorted((p for p in range(54, 79) if (p - 66 - root) % 12 in quality), reverse=True)
            for tick in range(16):
                b.note("water", t0 + tick, HARP, tones[tick % len(tones)], 100 - 3 * tick - (12 if bar == 0 else 0))
            for beat in range(4):
                t = t0 + 4 * beat
                b.drum("kick", t, BASEDRUM, (86 if beat % 2 == 0 else 70) + 4 * bar)
                b.note("bass", t, BASS, 30 + root, (88 if beat % 2 == 0 else 74) + 4 * bar)
            b.note("didg", t0, DIDGERIDOO, 30 + root, 80 + 5 * bar)
        for index, tick in enumerate((56, 58, 60, 61, 62, 63)):
            b.drum("snare", tick, SNARE, 44 + 10 * index, once=True)

    def ending(self):
        pass


def main():
    arg = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    target = arg if arg.suffix == ".nbs" else arg / "atlas_vindicator_2.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    song = Song()
    song.intro()
    for bar in range(NBARS):
        song.bar(bar)
    edges = [("intro", 0, START)]
    cursor = START
    for name, n in SECTIONS:
        edges.append((name, cursor, cursor + n * BAR_T))
        cursor += n * BAR_T
    layers = finish(target, song.b, TPS, "Raise the Axe (Boss002)",
                    "Atlas cave boss, class 5, style 4 (Japanese melodic dash): 165 BPM, harp waterfall, in-scale melody over a root-fifth-root bass, half-time sad part in the natural minor, drum build-up, chorus up a minor third and an octave on a sixteenth slap bass. Loops from tick 64.")
    print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
    bass = [layer for layer in layers if layer.name.startswith("bass")]
    print("   bass notes per bar by section: " + ", ".join(
        f"{n} {sum(1 for layer in bass for t in layer.notes if s <= t < e) / ((e - s) / 16):.1f}" for n, s, e in edges))
    print("   section starts (s): " + ", ".join(f"{n} {s / TPS:.0f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
