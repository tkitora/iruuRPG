#!/usr/bin/env python3
"""Makes atlas_vindicator_2.nbs: "Raise the Axe (Boss002)", the axe-wielding guardian of the Atlas cave (ONE song, no second phase) - class 5, the dry death match and wildness.

    python3 tools/bgm/make_atlas_boss2_bgm.py            writes atlas_vindicator_2.nbs into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss2_bgm.py outfile    writes it somewhere else (a file, or a folder)

150 BPM: 10 ticks a second, a beat of 4 ticks, a bar of 16 = the engine's bar line. Why 150 and not 200: a tribal drum ostinato lives in straight sixteenths (a tick each at 10 ticks a second;
at 200 BPM a sixteenth would fall between two ticks), and a brute does not sprint, it pounds: 150 BPM leaves room for the weight of every stroke and still pushes. (Class 4 runs at 200 BPM in triplets.)
The song is a trance build in F# (aeolian / minor pentatonic with the half step G and the flat fifth C): the war drums, the bass and the chant come in and get wilder every eight bars.
  - The drums are a wadaiko (the bass drum: low strokes, accents X x o g) and a djembe (the snare: slaps and ghost notes) in a CALL and RESPONSE: in the first bar of a pair the low drum calls and the high
    drum fills its gaps, in the second the low drum stops at the half bar and the high drum answers in a dense run; every fourth bar ends in a fill, every eighth in a roll. The drums have no pitch, so the
    wildness is in the velocities and in which of the two plays; the shaker (hat) comes in at the third level.
  - A bass and a didgeridoo in unison play a riff in sixteenths with ghost notes (four bars: statement, climbing variation, statement, turnaround that climbs into the next cycle), under a rough low guitar
    (power chords on the accents, then chugging eighths, then sixteenths). The root moves F# - A - B - E (i - III - iv - VII) only in the peak.
  - The tune is secondary and rhythmic: a banjo chant of four bars (call, call raised, answer on the fifth, answer climbing), a cow bell that clanks off the beat, and a raw flute shout that scoops up and
    is left hanging.
  - 80 bars after a 4-bar intro: Awake - Stomp - Chant - Rage - Strip (takes away; ends in a stop-and-hit break) - Peak 1 - Peak 2 - Peak 3 - Cool - Ramp (rolls and a climbing riff into tick 64).
Everything is written by what SOUNDS. Nothing is random (a hash of the tick and the bar's place in its 4-bar cycle, so an ostinato repeats). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat45 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS = 10
BAR_T = 16
START = 64
BLOCKS = ["AWAKE", "STOMP", "CHANT", "RAGE", "STRIP", "PEAK1", "PEAK2", "PEAK3", "COOL", "RAMP"]
LEVEL = {"AWAKE": 1, "STOMP": 2, "CHANT": 3, "RAGE": 4, "STRIP": "S", "PEAK1": 5, "PEAK2": 6, "PEAK3": 7, "COOL": 1, "RAMP": 2}
NBARS = 8 * len(BLOCKS)           # 80 bars = 128 s + the 6.4 s intro
LENGTH = START + NBARS * BAR_T
ROOT_OF_CYCLE = {11: 3, 13: 5, 15: 10}      # F# - A - B - E in the second cycle of each peak block
TONIC = 66                                    # F#4

VEL = {"X": 100, "x": 74, "o": 44, "g": 24}
# the wadaiko (bass drum): four bars (call, short call, call, fill) per level
LOW = {
    1: ["X..x..x.X..x..x.", "X..x..x.X.......", "X..x..x.X..x..x.", "X..x..x.X.x.xxXX"],
    2: ["X..x..xoX..x..x.", "X..x..x.X..x.oxo", "X..x..xoX..x..x.", "X..x.ox.X.xoxXXX"],
    3: ["X.ox..x.X.ox..xo", "X..xo.x.X.ox.oxx", "X.ox..x.X.ox..xo", "X.oxo.x.XoxoxXxX"],
    4: ["X.xoX.x.X.xoX.x.", "X..xX.o.X..xX.xx", "X.xoX.x.X.xoX.x.", "X.xoXoxxXoxoXXXX"],
    5: ["X.xxX.xoX.xxX.xo", "X.xxX.xoX.xxXxxx", "X.xxX.xoX.xxX.xo", "XxxxXoxxXxxxXXXX"],
    "S": ["X.......X.......", "X.......X..x....", "X.......X.......", "X.......X.x.x..X"],
}
for pattern in LOW.values():
    assert all(len(p) == 16 for p in pattern), pattern
HIGH_DENSITY = {1: 10, 2: 20, 3: 35, 4: 50, 5: 60, 6: 70, 7: 76, "S": 8}
# the riff of the bass and the didgeridoo: (tick, semitones above the root, velocity); a velocity under 40 is a ghost note
RIFF = [
    [(0, 0, 100), (2, 0, 32), (3, 0, 72), (6, 3, 82), (8, 0, 96), (10, 0, 30), (11, 0, 72), (14, 5, 80)],
    [(0, 0, 100), (3, 0, 70), (4, 0, 30), (6, 3, 80), (8, 7, 94), (10, 7, 30), (11, 5, 74), (12, 3, 60), (14, 6, 82), (15, 7, 70)],
    [(0, 0, 100), (3, 0, 70), (6, 3, 80), (7, 0, 34), (8, 0, 96), (11, 1, 74), (12, 0, 40), (14, 3, 80)],
    [(0, 0, 100), (2, 3, 50), (3, 5, 72), (4, 6, 82), (6, 7, 88), (8, 10, 92), (10, 7, 50), (11, 10, 80), (12, 12, 96), (14, 10, 60), (15, 12, 88)],
]
# the chant (banjo): (tick, semitones above the root of the cycle), four bars
CHANT = [
    [(0, 0), (2, 0), (4, 3), (6, 0), (8, -2), (10, 0)],
    [(0, 0), (2, 0), (4, 3), (6, 5), (8, 3), (10, 5), (12, 7)],
    [(0, 7), (2, 7), (4, 10), (6, 7), (8, 5), (10, 7)],
    [(0, 10), (2, 10), (4, 12), (6, 10), (8, 7), (10, 10), (12, 12), (14, 12)],
]
ROLES = {"chant": (100, 80), "cow": (80, 120), "shout": (90, 100), "guitar": (95, 100), "bass": (100, 100), "didg": (100, 100),
         "kick": (100, 100), "snare": (92, 105), "hat": (50, 115)}


def level_of(bar):
    block = BLOCKS[bar // 8]
    if block == "RAMP" and bar % 8 < 4:
        return 1
    return LEVEL[block]


def root_of(bar):
    return ROOT_OF_CYCLE.get(bar // 4, 0)


def num(level):
    return 4.5 if level == "S" else level


class Song:
    def __init__(self):
        self.b = Builder(LENGTH, ROLES)

    # ---- drums
    def low_high(self, t0, bar, level, bc, force_fill=False, big=False):
        """The wadaiko (bass drum) and djembe (snare) of one bar, in call and response."""
        b = self.b
        low = LOW[min(level, 5) if level != "S" else "S"][bc]
        hi_level = HIGH_DENSITY[level]
        low_events = {}
        for tick, ch in enumerate(low):
            if ch != ".":
                low_events[tick] = VEL[ch]
        if level in (6, 7):                                      # the gallop: every rest of the low drum between the accents gets a ghost stroke
            for tick in range(16):
                if tick not in low_events and tick % 4 in (1, 3) and hash_(tick, bc, 3) % 3:
                    low_events[tick] = 36
        if bc == 1 and level != "S":                              # the response bar: the low drum stops at the half bar
            for tick in list(low_events):
                if tick >= 10:
                    del low_events[tick]
        high_events = {}
        if level not in (1, "S"):
            high_events[4] = 84 if level < 4 else 92
            high_events[12] = 84 if level < 4 else 92
        for tick in range(16):
            if tick in low_events or tick in high_events:
                continue
            if bc == 1 and tick >= 8 and level != "S":
                chance = min(95, hi_level + 45)
                if hash_(tick, bc, level if level != "S" else 0, 5) % 100 < chance:
                    high_events[tick] = 84 if tick % 2 == 0 else 54
            elif hash_(tick, bc, 4) % 100 < hi_level:
                high_events[tick] = 70 if tick % 4 == 2 else 44
        if bc == 3 or force_fill:                                 # the fill of the cycle: a roll of the two drums, rising
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
        if level in (3, 4):                                       # the shaker
            for tick in range(0, 16, 2):
                b.drum("hat", t0 + tick, HAT, 30 if tick % 4 == 0 else 20)
        elif level in (5, 6, 7):
            for tick in range(16):
                b.drum("hat", t0 + tick, HAT, 34 if tick % 4 == 0 else (26 if tick % 2 == 0 else 16))

    # ---- bass, didgeridoo, guitar
    def riff(self, t0, bar, level, bc, force=None):
        b = self.b
        root = root_of(bar)
        notes = RIFF[bc if force is None else force]
        for tick, semi, vel in notes:
            ghost = vel < 40
            if level == 1 and vel < 80 and tick != 0:
                continue
            pitch = 30 + root + semi
            b.note("bass", t0 + tick, BASS, pitch, vel)
            if not ghost:
                b.note("didg", t0 + tick, DIDGERIDOO, pitch, vel * 0.9)
            if level != 1 and not ghost and vel >= 80 and level != "S":
                low_pitch = 42 + root + (semi % 12 if semi < 12 else 0)
                b.note("guitar", t0 + tick, GUITAR, low_pitch, 70 + 8 * (num(level) >= 4))
                if low_pitch + 7 <= 66:
                    b.note("guitar", t0 + tick, GUITAR, low_pitch + 7, 60 + 8 * (num(level) >= 4))
        if level in (4, 5, 6, 7) or level == 3:                    # the chug: muted eighths, then sixteenths
            step = 1 if level in (6, 7) else 2
            for tick in range(0, 16, step):
                if b.taken("guitar", t0 + tick):
                    continue
                if level == 3 and tick % 4 != 2:
                    continue
                v = (66 if tick % 4 == 0 else 52) + (6 if level >= 5 else 0)
                b.note("guitar", t0 + tick, GUITAR, 42 + root, v)

    # ---- the chant, the cow bell, the shout
    def tune(self, t0, bar, level, bc):
        b = self.b
        root = root_of(bar)
        base = 66 if root == 0 else 66 + root - 12
        cool = BLOCKS[bar // 8] == "COOL"
        if level in (3, 4, 5, 6, 7) or level == "S" or cool:
            quiet = 0.6 if (level == "S" or cool) else 1.0
            if (level == "S" or cool) and bc < 2:
                pass
            else:
                for tick, semi in CHANT[bc]:
                    vel = (80 if tick % 8 == 0 else 68) * quiet
                    b.note("chant", t0 + tick, BANJO, base + semi, vel)
                    if num(level) >= 5:                              # unison with the cow bell
                        b.note("cow", t0 + tick, COW_BELL, 66 + 12 + semi if level == 7 else base + semi + 12, 58, once=True)
        if level in (3, 4):                                        # the cow bell off the beat
            for tick, semi in ((6, 0), (14, 7)):
                b.note("cow", t0 + tick, COW_BELL, 66 + semi, 62, once=True)
        shout_here = (
            (level == 4 and bar % 8 == 4)
            or (level == 5 and bc == 0)
            or (level == 6 and bc in (0, 2))
            or (level == 7 and bc in (0, 1, 2))
        )
        if shout_here:
            self.shout(t0)

    def shout(self, t0):
        """A raw flute shout: a scoop up to F#6, re-struck, then a leap up that is left hanging."""
        b = self.b
        b.note("shout", t0 - 1, FLUTE, 89, 62, once=True)
        for index, tick in enumerate(range(0, 10, 2)):
            b.note("shout", t0 + tick, FLUTE, 90, 70 + 3 * index, once=True)
        b.note("shout", t0 + 10, FLUTE, 93, 90, once=True)
        b.note("shout", t0 + 12, FLUTE, 97, 96, once=True)

    # ---- one bar
    def bar(self, bar):
        t0 = START + bar * BAR_T
        level = level_of(bar)
        bc = bar % 4
        block = BLOCKS[bar // 8]
        in_block = bar % 8
        big = in_block == 7
        force_fill = big
        if block == "STRIP" and in_block == 6:
            self.low_high(t0, bar, "S", bc)
            self.roll(t0, 16)
            self.riff(t0, bar, "S", bc, force=3)
            return
        if block == "STRIP" and in_block == 7:
            self.stop_and_hit(t0, bar)
            return
        self.low_high(t0, bar, level, bc, force_fill=force_fill, big=big)
        self.riff(t0, bar, level, bc, force=3 if (big and block in ("RAMP",)) else None)
        self.tune(t0, bar, level, bc)
        if bar == 0 or (block in ("RAGE", "PEAK1", "PEAK2", "PEAK3") and in_block == 0):
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
        b.note("bass", t0, BASS, 30 + root, 100, once=True)
        b.note("didg", t0, DIDGERIDOO, 30 + root, 100, once=True)
        if not b.taken("guitar", t0):
            b.note("guitar", t0, GUITAR, 42 + root, 96)
            b.note("guitar", t0, GUITAR, 49 + root, 86)
        b.note("chant", t0, BANJO, 66 if root == 0 else 54 + root, 90, once=True)

    def stop_and_hit(self, t0, bar):
        """The break before the peak: everything stops; five hits with silence between; a rising roll into the peak."""
        for tick in (0, 4, 8, 10, 12):
            self.hit(t0 + tick, bar)
        for index, tick in enumerate((13, 14, 15)):
            self.b.drum("snare", t0 + tick, SNARE, 70 + 14 * index, once=True)
        self.b.drum("kick", t0 + 15, BASEDRUM, 100, once=True)

    def intro(self):
        b = self.b
        # bar 0: the wadaiko alone calls, bar 1: the djembe answers, bar 2: both, with the didgeridoo swelling and the bass entering; bar 3: a roll and a shout that is left hanging
        for tick, ch in enumerate(LOW[2][0]):
            if ch != ".":
                b.drum("kick", tick, BASEDRUM, VEL[ch])
        b.drum("snare", 16 + 4, SNARE, 80)
        for tick, vel in ((16 + 8, 60), (16 + 10, 84), (16 + 11, 54), (16 + 12, 96), (16 + 13, 50), (16 + 14, 100), (16 + 15, 90)):
            b.drum("snare", tick, SNARE, vel)
        b.drum("kick", 16, BASEDRUM, 100)
        for tick, ch in enumerate(LOW[3][0]):
            if ch != ".":
                b.drum("kick", 32 + tick, BASEDRUM, VEL[ch])
        for tick in (36, 44):
            b.drum("snare", tick, SNARE, 84)
        for index, tick in enumerate(range(32, 48, 4)):
            b.note("didg", tick, DIDGERIDOO, 30, 50 + 10 * index)
        for tick, semi, vel in RIFF[0]:
            if vel >= 60:
                b.note("bass", 32 + tick, BASS, 30 + semi, vel * 0.9)
        # bar 3: the roll
        for tick in range(16):
            v = int(30 + 70 * tick / 15)
            b.drum("snare" if tick % 2 else "kick", 48 + tick, SNARE if tick % 2 else BASEDRUM, v)
        for tick in range(0, 16, 4):
            b.note("bass", 48 + tick, BASS, 30 + (0, 3, 5, 7)[tick // 4], 90, once=True)
            b.note("didg", 48 + tick, DIDGERIDOO, 30 + (0, 3, 5, 7)[tick // 4], 80, once=True)
        b.note("shout", 56 - 1, FLUTE, 89, 62)
        for index, tick in enumerate(range(56, 62, 2)):
            b.note("shout", tick, FLUTE, 90, 72 + 4 * index)
        b.note("shout", 62, FLUTE, 97, 100)

    def ending(self):
        """The last bar climbs into tick 64: a roll and the shout, left hanging."""
        t0 = START + (NBARS - 1) * BAR_T
        self.shout_end(t0)

    def shout_end(self, t0):
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
    edges = [("intro", 0, START)] + [(name, START + i * 8 * BAR_T, START + (i + 1) * 8 * BAR_T) for i, name in enumerate(BLOCKS)]
    layers = finish(target, song.b, TPS, "Raise the Axe (Boss002)",
                    "Atlas cave boss, class 5 (dry death match): 150 BPM war drums in call and response, a sixteenth-note bass/didgeridoo riff, rough low guitar, a banjo chant, a trance build that gets wilder every 8 bars. Loops from tick 64.")
    print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))


if __name__ == "__main__":
    main()
