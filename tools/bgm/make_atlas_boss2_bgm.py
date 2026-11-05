#!/usr/bin/env python3
"""Makes atlas_vindicator_2.nbs: "Raise the Axe (Boss002)", the axe-wielding guardian of the Atlas cave (ONE song, no second phase) - class 5, the dry death match and wildness.

    python3 tools/bgm/make_atlas_boss2_bgm.py            writes atlas_vindicator_2.nbs into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss2_bgm.py outfile    writes it somewhere else (a file, or a folder)

t276 rebuild on the "16th-note machine gun" guide. 165 BPM: 11 ticks a second (165 / 15), a tick is a sixteenth, a beat 4 ticks, a bar 16 = the engine's bar line.
The MELODY keeps the base tempo, the ACCOMPANIMENT is subdivided so that the song feels two to four times faster, section by section:
  intro   quarter-note roots of the bass and the wadaiko, soft;
  A       the bass repeats its root in eighths, tight and heavy (feels like 330 BPM); the wadaiko pounds the beats;
  A'      the same eighths, the bass sways (root - flat second - fifth), a palm-muted guitar chugs under it;
  B       eighths with sudden sixteenth double hits and triplet figures (3 + 3 + 2), the bass line climbs and falls, irregular surges (feels like 330-500);
  PEAK    the bass is a full sixteenth machine gun, 'zuku-zuku' (heavy-ghost-medium-ghost), swaying on the roots F# - D - B - C#, shaker and drums in sixteenths (feels like 660 BPM),
          and over it the MELODY is deliberately LONG: a shakuhachi-like flute holds whole and half notes (re-struck), a banjo plucks the onsets - the broad tune on the very fast bass.
The tune is Japanese-flavoured (miyako-bushi / in scale on F#: F# G B C# D, a flat second above the root) and rhythmic chants of a banjo (a shamisen) answer it in the verses.
Colour of the class: wadaiko (bass drum) and djembe (snare) in call and response, a didgeridoo in unison with a thick bass, a rough guitar, a cow bell; wild before pretty.
Plan: 4 bars of intro, then 80 bars: Awake - Stomp - Surge - Chorus - Strip (takes away; ends in a stop-and-hit break) - Surge 2 - Peak 1 - Peak 2 (a whole tone up) - Cool - Ramp.
Everything is written by what SOUNDS. Nothing is random (a hash of the tick and the place in the 4-bar cycle). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat45 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS = 165 / 15                    # 11 ticks a second; a tick is a sixteenth
BAR_T = 16
START = 64
# block: (name, drum level, bass level)  - the drum levels 1..7 grow wilder; the bass levels 0..4 are quarters, eighths, swaying eighths, eighths + doubles + triplets, sixteenths
BLOCKS = [("AWAKE", 1, 1), ("STOMP", 2, 2), ("SURGE", 3, 3), ("CHORUS", 5, 4), ("STRIP", "S", 0), ("SURGE2", 4, 3), ("PEAK1", 6, 4), ("PEAK2", 7, 4), ("COOL", 2, 1), ("RAMP", 2, 2)]
NBARS = 8 * len(BLOCKS)           # 80 bars = 116.4 s + the 5.8 s intro
LENGTH = START + NBARS * BAR_T
BASS_LOW = 30                     # F#1
VEL = {"X": 100, "x": 74, "o": 44, "g": 24}
# the wadaiko (bass drum), four bars (call, short call, call, fill) per drum level; the verses keep to eighths (a figure of two sixteenths is still fine)
LOW = {
    1: ["X...x...X...x...", "X...x...X.......", "X...x...X...x...", "X...x...X.x.x.xX"],
    2: ["X.o.x.o.X.o.x.o.", "X.o.x.o.X.o.x.x.", "X.o.x.o.X.o.x.o.", "X.o.x.o.X.x.xxXX"],
    3: ["X.xox.o.X.xox.o.", "X.xox.o.X.xoxxo.", "X.xox.o.X.xox.o.", "X.xoxoxxXoxoxXxX"],
    4: ["X.xoX.x.X.xoX.x.", "X..xX.o.X..xX.xx", "X.xoX.x.X.xoX.x.", "X.xoXoxxXoxoXXXX"],
    5: ["X.xxX.xoX.xxX.xo", "X.xxX.xoX.xxXxxx", "X.xxX.xoX.xxX.xo", "XxxxXoxxXxxxXXXX"],
    "S": ["X.......X.......", "X.......X..x....", "X.......X.......", "X.......X.x.x..X"],
}
for pattern in LOW.values():
    assert all(len(p) == 16 for p in pattern), pattern
HIGH_DENSITY = {1: 8, 2: 16, 3: 30, 4: 48, 5: 58, 6: 68, 7: 76, "S": 6}
# the bass: offsets above the root of the bar. Level 2: a sway by eighth slots (tick // 2); level 3: climbing and falling by eighth slots; level 4: one value per beat (tick // 4)
SWAY2 = [[0, 0, 0, 0, 0, 0, 1, 0], [0, 0, 5, 0, 0, 0, 7, 5], [0, 0, 0, 0, 0, 0, 1, 0], [0, 0, 5, 7, 8, 7, 5, 7]]
MOVE3 = [[0, 0, 7, 0, 5, 0, 8, 7], [0, 5, 7, 12, 7, 5, 1, 0], [0, 0, 8, 7, 5, 7, 12, 8], [0, 5, 7, 8, 12, 8, 7, 13]]
GUN4 = [[0, 0, 1, 0], [0, 5, 7, 5], [0, 0, 8, 7], [0, 5, 7, 12]]
# the roots (semitones above F#) of the 4-bar cycle: the peak moves F# - D - B - C# (i - bVI - iv - v) and hangs on the fifth
ROOTS = {1: [0, 0, 0, 0], 2: [0, 0, 0, 7], 3: [0, 5, 0, 7], 4: [0, 8, 5, 7], 0: [0, 0, 0, 0]}
# the banjo chant (shamisen), four bars: semitones above F#4, in scale F# G B C# D
CHANT = [
    [(0, 7), (4, 8), (6, 7), (8, 5), (12, 0)],
    [(0, 1), (2, 0), (4, 1), (6, 5), (8, 7), (12, 5)],
    [(0, 7), (4, 8), (6, 12), (8, 8), (12, 7), (14, 8)],
    [(0, 5), (2, 7), (4, 8), (6, 12), (8, 13), (12, 12), (14, 13)],
]
# the long-note tunes of the flute (semitones above F#5, length in ticks): X rises by calls, Y answers higher; every phrase of 4 bars ends hanging up
TUNE_X = [[(7, 16)], [(8, 8), (5, 8)], [(12, 12), (13, 4)], [(13, 8), (17, 8)]]
TUNE_Y = [[(12, 16)], [(13, 8), (12, 8)], [(17, 12), (19, 4)], [(20, 8), (19, 4), (20, 4)]]
ROLES = {"flute": (100, 100), "harm": (80, 90), "chant": (100, 80), "cow": (80, 120), "shout": (90, 100), "guitar": (95, 100), "guitar2": (80, 100), "bass": (100, 100), "didg": (100, 100),
         "kick": (100, 100), "snare": (92, 105), "hat": (50, 115)}


def block_of(bar):
    return BLOCKS[bar // 8]


def shift_of(bar):
    return 2 if block_of(bar)[0] == "PEAK2" else 0


def levels_of(bar):
    """(drum level, bass level) of the bar; the ramp climbs, the strip drops out."""
    name, dl, bl = block_of(bar)
    ib = bar % 8
    if name == "RAMP" and ib >= 4:
        return 3, 3
    if name == "SURGE" and ib >= 6:
        return 4, 3
    if name == "STRIP" and ib < 4:
        return "S", 1 if ib in (2, 3) else 0
    return dl, bl


def root_of(bar):
    return ROOTS[levels_of(bar)[1]][bar % 4] + shift_of(bar)


class Song:
    def __init__(self):
        self.b = Builder(LENGTH, ROLES)

    # ---- drums
    def low_high(self, t0, bar, dl, bc, big=False):
        """The wadaiko (bass drum) and djembe (snare) of one bar, in call and response."""
        b = self.b
        low = LOW[min(dl, 5) if dl != "S" else "S"][bc]
        hi_level = HIGH_DENSITY[dl]
        peak = dl != "S" and dl >= 5
        low_events = {tick: VEL[ch] for tick, ch in enumerate(low) if ch != "."}
        if dl in (6, 7):                                          # the gallop: ghost strokes between the accents, sixteenths
            for tick in range(16):
                if tick not in low_events and tick % 4 in (1, 3) and hash_(tick, bc, 3) % 3:
                    low_events[tick] = 36
        if bc == 1 and dl != "S":                                 # the response bar: the low drum stops at the half bar
            for tick in list(low_events):
                if tick >= 10:
                    del low_events[tick]
        high_events = {}
        if dl not in (1, "S"):
            high_events[4] = 84 if dl < 4 else 92
            high_events[12] = 84 if dl < 4 else 92
        for tick in range(16):
            if tick in low_events or tick in high_events:
                continue
            if not peak and dl != "S" and tick % 2:                # the verses keep to eighths for the djembe too (a pair of sixteenths only inside the fill)
                continue
            if bc == 1 and tick >= 8 and dl != "S":
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

    # ---- bass, didgeridoo, guitar
    def bass_bar(self, t0, bar, dl, bl, strong=True):
        b = self.b
        bc = bar % 4
        root = root_of(bar)
        ticks = hit_ticks(bl, "ST", bar % 4 + 4 * (bar // 8))
        ghost_octave = bl == 4
        for tick in ticks:
            if bl <= 1:
                off = 0
            elif bl == 2:
                off = SWAY2[bc][tick // 2]
            elif bl == 3:
                off = MOVE3[bc][tick // 2]
            else:
                off = GUN4[bc][tick // 4]
                if tick % 4 == 3 and (tick // 4 + bar) % 2 == 0:
                    off += 12
            vel = bass_velocity(tick, bl)
            if bl == 0:
                vel = 56
            pitch = BASS_LOW + root + off
            b.note("bass", t0 + tick, BASS, pitch, vel)
            heavy = tick % 4 == 0 if bl >= 3 else tick % 8 == 0
            if bl == 0:
                heavy = False
            if heavy and bl >= 1 and (bl >= 2 or bc in (0, 2)):
                b.note("didg", t0 + tick, DIDGERIDOO, pitch, vel * 0.9)
            # the guitar: palm-muted chug from the second level on, power chords on the heavy hits of the peak
            if bl >= 2 and tick % 2 == 0:
                gp = 42 + (root + off) % 12
                b.note("guitar", t0 + tick, GUITAR, gp, 58 + (8 if tick % 4 == 0 else 0) + (6 if bl == 4 else 0))
        if bl == 4 and strong:
            for tick in (0, 8):
                b.note("guitar2", t0 + tick, GUITAR, 49 + root % 12, 70)
        if bl == 3 and strong and 0 in ticks:
            b.note("guitar2", t0, GUITAR, 49 + root % 12, 64)

    # ---- the chant, the long tune, the cow bell, the shout
    def tune(self, t0, bar, dl, bl):
        b = self.b
        name = block_of(bar)[0]
        bc = bar % 4
        ib = bar % 8
        shift = shift_of(bar)
        if name in ("CHORUS", "PEAK1", "PEAK2", "COOL"):
            self.long_tune(t0, bar, name, ib, bc, shift)
        elif name == "STRIP":
            if ib >= 4 and ib < 6:
                self.long_tune(t0, bar, "COOL", ib, bc, 0)
        elif name != "AWAKE" or ib >= 4:
            quiet = 0.7 if name in ("AWAKE", "RAMP") and ib < 4 else 1.0
            for tick, off in CHANT[bc]:
                if dl == 1 and tick % 4:                           # the first answers are plain
                    continue
                vel = (80 if tick % 8 == 0 else 68) * quiet
                b.note("chant", t0 + tick, BANJO, 66 + off, vel)
                if dl in (3, 4) and tick % 8 == 0:
                    b.note("cow", t0 + tick + 2, COW_BELL, 66 + 12 + off % 12, 56, once=True)
        if (name == "SURGE" and ib == 7) or (name == "SURGE2" and ib in (3, 7)):
            self.shout(t0)

    def long_tune(self, t0, bar, name, ib, bc, shift):
        """The broad tune: whole and half notes held by the flute (re-struck every 4 ticks, a swell), the banjo plucking the onsets two octaves below."""
        b = self.b
        if name == "PEAK2":
            table = TUNE_Y if ib < 4 else TUNE_X
        elif name == "PEAK1":
            table = TUNE_X if ib < 4 else TUNE_Y
        elif name == "CHORUS":
            table = TUNE_X if ib < 4 else TUNE_Y
        else:
            table = TUNE_Y if ib < 4 else TUNE_X
        quiet = 0.62 if name == "COOL" else 1.0
        tick = 0
        for off, length in table[bc]:
            pitch = 78 + off + shift
            sustain(b, "flute", t0 + tick, FLUTE, pitch, length, 84 * quiet, 98 * quiet if length > 8 else 94 * quiet)
            b.note("chant", t0 + tick, BANJO, 54 + off + shift, 92 * quiet, once=True)
            if name == "PEAK2":
                sustain(b, "harm", t0 + tick, FLUTE, pitch - 7, length, 62, 74)
            if name in ("PEAK1", "PEAK2") and length >= 8 and off >= 12:
                b.note("cow", t0 + tick, COW_BELL, 66 + 12 + (off % 12), 54, once=True)
            tick += length

    def shout(self, t0):
        """A raw flute shout: a scoop up to F#6, re-struck, then a leap up that is left hanging (only where the flute is free)."""
        b = self.b
        b.note("shout", t0 + 8 - 1, FLUTE, 89, 62, once=True)
        for index, tick in enumerate(range(8, 12, 2)):
            b.note("shout", t0 + tick, FLUTE, 90, 70 + 3 * index, once=True)
        b.note("shout", t0 + 12, FLUTE, 93, 90, once=True)
        b.note("shout", t0 + 14, FLUTE, 97, 96, once=True)

    # ---- one bar
    def bar(self, bar):
        t0 = START + bar * BAR_T
        dl, bl = levels_of(bar)
        name = block_of(bar)[0]
        ib = bar % 8
        bc = bar % 4
        if name == "STRIP" and ib == 6:
            self.low_high(t0, bar, "S", bc)
            self.roll(t0, 16)
            self.bass_bar(t0, bar, "S", 1)
            return
        if name == "STRIP" and ib == 7:
            self.stop_and_hit(t0, bar)
            return
        big = ib == 7 and name in ("RAMP", "SURGE", "SURGE2", "CHORUS", "PEAK1")
        self.low_high(t0, bar, dl, bc, big=big)
        self.bass_bar(t0, bar, dl, bl)
        self.tune(t0, bar, dl, bl)
        if ib == 0 and name in ("CHORUS", "PEAK1", "PEAK2", "SURGE2") or bar == 0:
            self.hit(t0, bar)

    def roll(self, t0, ticks):
        for tick in range(ticks):
            v = int(30 + 70 * tick / (ticks - 1))
            self.b.drum("snare", t0 + tick, SNARE, v, once=True)
            if tick % 4 == 0:
                self.b.drum("kick", t0 + tick, BASEDRUM, min(100, v + 10), once=True)

    def hit(self, t0, bar):
        """A unison hit: the wadaiko, the djembe, the bass, the didgeridoo, the guitar, the banjo."""
        b = self.b
        root = root_of(bar)
        b.drum("kick", t0, BASEDRUM, 100, once=True)
        b.drum("snare", t0, SNARE, 96, once=True)
        b.note("bass", t0, BASS, BASS_LOW + root, 100, once=True)
        b.note("didg", t0, DIDGERIDOO, BASS_LOW + root, 100, once=True)
        b.note("guitar", t0, GUITAR, 42 + root % 12, 96, once=True)
        b.note("guitar2", t0, GUITAR, 49 + root % 12, 86, once=True)
        b.note("chant", t0, BANJO, 66 if root == 0 else 54 + root, 90, once=True)

    def stop_and_hit(self, t0, bar):
        """The break before the surge: everything stops; five hits with silence between; a rising roll into the next block."""
        for tick in (0, 4, 8, 10, 12):
            self.hit(t0 + tick, bar)
        for index, tick in enumerate((13, 14, 15)):
            self.b.drum("snare", t0 + tick, SNARE, 70 + 14 * index, once=True)
        self.b.drum("kick", t0 + 15, BASEDRUM, 100, once=True)

    def intro(self):
        """4 bars: the wadaiko alone calls, then quarter-note roots of bass and drum, soft; a roll and a shout left hanging in the last bar."""
        b = self.b
        for tick, vel in ((0, 70), (8, 56)):
            b.drum("kick", tick, BASEDRUM, vel)
        b.note("shout", 6, FLUTE, 85, 56)
        sustain(b, "shout", 8, FLUTE, 90, 8, 62, 74)
        for bar in (1, 2):                                          # quarter-note roots, soft; the djembe answers
            for beat in range(4):
                t = 16 * bar + 4 * beat
                b.drum("kick", t, BASEDRUM, (60 if beat % 2 == 0 else 46) + 8 * (bar - 1))
                b.note("bass", t, BASS, BASS_LOW, 46 + 10 * (bar - 1) + (6 if beat % 2 == 0 else 0))
            b.drum("snare", 16 * bar + 12, SNARE, 50 + 14 * (bar - 1))
        for tick in (32, 40):
            b.note("didg", tick, DIDGERIDOO, BASS_LOW, 44)
        for index, tick in enumerate((48, 52, 56, 58, 60, 61, 62, 63)):   # bar 3: a rising roll
            v = 40 + 7 * index
            b.drum("snare" if index % 2 else "kick", tick, SNARE if index % 2 else BASEDRUM, v)
        for index, tick in enumerate((48, 52, 56, 60)):
            b.note("bass", tick, BASS, BASS_LOW + (0, 0, 5, 7)[index], 60 + 8 * index)
        b.note("shout", 55, FLUTE, 89, 62)
        sustain(b, "shout", 56, FLUTE, 90, 6, 72, 80, every=2)
        b.note("shout", 62, FLUTE, 97, 100)

    def ending(self):
        """The last bar climbs into tick 64: a flute run, left hanging on the high note."""
        t0 = START + (NBARS - 1) * BAR_T
        b = self.b
        for index, tick in enumerate(range(8, 14, 2)):
            b.note("shout", t0 + tick, FLUTE, 90, 72 + 4 * index, once=True)
        b.note("shout", t0 + 14, FLUTE, 97, 100, once=True)


def main():
    arg = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    target = arg if arg.suffix == ".nbs" else arg / "atlas_vindicator_2.nbs"
    target.parent.mkdir(parents=True, exist_ok=True)
    song = Song()
    song.intro()
    for bar in range(NBARS):
        song.bar(bar)
    song.ending()
    edges = [("intro", 0, START)] + [(name, START + i * 8 * BAR_T, START + (i + 1) * 8 * BAR_T) for i, (name, _, _) in enumerate(BLOCKS)]
    layers = finish(target, song.b, TPS, "Raise the Axe (Boss002)",
                    "Atlas cave boss, class 5 (dry death match): 165 BPM, wadaiko and djembe in call and response, the bass subdivided from quarters to eighths to a sixteenth machine gun, a long-note Japanese-flavoured flute tune over it. Loops from tick 64.")
    print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
    bass_count(layers, edges)


def bass_count(layers, edges):
    """Bass notes (the bass layers) per section: quarters / eighths / eighths + doubles / sixteenths."""
    bass = [layer for layer in layers if layer.name.startswith("bass")]
    print("   bass notes per bar by section: " + ", ".join(
        f"{n} {sum(1 for layer in bass for t in layer.notes if s <= t < e) / ((e - s) / 16):.1f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
