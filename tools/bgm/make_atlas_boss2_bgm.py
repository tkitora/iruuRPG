#!/usr/bin/env python3
"""Makes atlas_vindicator_2.nbs: "Raise the Axe (Boss002)", the axe guardian of the Atlas cave (ONE song, no second phase) - class 5, the dry death match and wildness. Built again from zero (t290).

    python3 tools/bgm/make_atlas_boss2_bgm.py            writes atlas_vindicator_2.nbs into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss2_bgm.py outfile    writes it somewhere else (a file, or a folder)

STYLE 4 of the style doc (Japanese melodic dash, 165 BPM) built so that tools/bgm/guidecheck.py finds the guide in the notes. 11 ticks a second (a tick is a sixteenth), 16-tick bars, 4 intro bars + 92 =
1536 ticks = 139.6 s. Key A.
THE AXE: the motif is a chopping cell, A A E | D D F (the rhythm 3+1+4 | 3+1+4: two knocks, the blade rises to the fifth, falls, lifts to the flat sixth), raised one step of the in scale
(A Bb D E F) every bar. Wild colour: a koto waterfall of the harp, a flute (shakuhachi) and a banjo (shamisen) on the tune, wadaiko and djembe, a didgeridoo in unison with the bass, a rough guitar.
Time table (the guide's style 4 table, in seconds of the song):
  0:00  WATERFALL (4 intro bars + 3 bars, to 0:10): the harp falls down the in scale in sixteenths (11 notes a second) over QUARTER-note bass roots and the wadaiko; then the cell on the banjo (to 0:20), eighth roots.
  0:20  MAIN (26 bars, to 0:58): the flute carries the cell (an OCTAVE and more above the banjo before it: the tune jumps up); the bass runs ROOT - FIFTH - ROOT (A and E only, eighths; sixteenth double hits
        mixed in from bar 12), full drums.
  0:58  HALF-TIME (20 bars, to 1:27): the drums are halved (kick on 1, snare on 3), the bass walks the chords in quarters, the MODE FLIPS to A natural minor (Am F C G Dm Am E E): a sad harp tune in long notes.
  1:27  BUILD-UP (6 bars, to 1:36): the melody is gone; a snare / kick crescendo from quarters to sixteenths on a pedal bass that climbs E - F - G - Bb; the last beat keeps the bass.
  1:36  CHORUS (30 bars, to 2:19): a MINOR THIRD up (A -> C) AND the tune high above the harp: the sad tune on the flute in long notes (struck again every 3 ticks) over a full SIXTEENTH slap bass; guitar hits and
        a harmony a fifth below only where the flute strikes (so the tune stays one voice); the last bar climbs back (E major, the dominant of A) into tick 64.
Nothing is random. The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from boss281 import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"
TPS = 165 / 15
BAR_T = 16
START = 64
SECTIONS = [("WATER", 10), ("MAIN", 26), ("HALF", 20), ("BUILD", 6), ("CHORUS", 30)]
NBARS = sum(n for _, n in SECTIONS)
LENGTH = START + NBARS * BAR_T
LOWA = 33                                                    # A1
FIFTH = 40                                                   # E2
IN = [0, 1, 5, 7, 8]                                         # the in scale on A: A Bb D E F
VEL = {"X": 100, "x": 76, "o": 46}
G3 = 3                                                       # the strike grid of long notes (ticks): 3 ticks = 0.27 s at 11 ticks a second


def deg(i):
    q, r = divmod(i, 5)
    return IN[r] + 12 * q


CELL = [(0, 3, 0), (3, 1, 0), (4, 4, 3), (8, 3, 2), (11, 1, 2), (12, 4, 4)]
CLIMB_A = [(0, 4, 4), (4, 4, 5), (8, 8, 7)]
CLIMB_B = [(0, 4, 6), (4, 4, 8), (8, 8, 10)]
MAIN_BARS = [(CELL, 0), (CELL, 1), (CELL, 2), (CLIMB_A, 0), (CELL, 2), (CELL, 3), (CELL, 4), (CLIMB_B, 0)]
# the sad tune: A natural minor, harp at 57 + offset, two notes a bar (ticks 0-5 and 6-15), chords Am F C G Dm Am E E
SAD = [(7, 12), (8, 15), (10, 15), (14, 17), (12, 17), (12, 15), (11, 14), (14, 18)]
SAD_ROOTS = [0, 8, 3, 10, 5, 0, 7, 7]                        # semitones above A
SAD_Q = [(0, 3, 7), (0, 4, 7), (0, 4, 7), (0, 4, 7), (0, 3, 7), (0, 3, 7), (0, 4, 7), (0, 4, 7)]
SLAP = [[0, 0, 7, 0], [0, 5, 7, 5], [0, 0, 7, 10], [0, 5, 7, 7]]
KICK = {1: "X..x....X..x....", 2: "X..xX..oX..xX..o", 3: "X.xoX..xX.xoX..x", 5: "X...X.x.X...X.x."}
SNARE_S = {1: "....x.......x...", 2: "....X.......X.o.", 3: "....X..o....X..o", 5: "....X.......X.o."}
ROLES = {"flute": (100, 100), "harp": (100, 90), "banjo": (100, 80), "harm": (80, 90), "shout": (90, 100), "water": (100, 85), "guitar": (95, 100), "chug": (70, 100), "power2": (80, 100),
         "bass": (100, 100), "didg": (100, 100), "kick": (100, 100), "snare": (95, 105), "hat": (50, 115), "tom": (100, 100)}
MAP = [(name, ib) for name, n in SECTIONS for ib in range(n)]


def hold3(*args, **kwargs):
    kwargs.setdefault("every", 3)
    return hold(*args, **kwargs)


def root_of(bar):
    """Semitones above A of the root of the bar."""
    name, ib = MAP[bar]
    if name in ("WATER", "MAIN"):
        return 0
    if name == "HALF":
        return SAD_ROOTS[ib % 8]
    if name == "BUILD":
        return (7, 7, 7, 7, 8, 10)[ib]
    return 7 if ib == 29 else SAD_ROOTS[(ib if ib < 24 else 4 + ib - 24) % 8] + 3


class Song:
    def __init__(self):
        self.b = Builder(LENGTH, ROLES)

    # ---- drums
    def drums(self, t0, dl, bc, roll=False):
        b = self.b
        for tick, ch in enumerate(KICK[dl]):
            if ch != "." and not (roll and tick >= 10):
                b.drum("kick", t0 + tick, BASEDRUM, VEL[ch])
        for tick, ch in enumerate(SNARE_S[dl]):
            if ch != "." and not (roll and tick >= 12):
                b.drum("snare", t0 + tick, SNARE, VEL[ch])
        if dl == 3:
            for tick in (2, 6, 10, 14):
                b.drum("hat", t0 + tick, HAT, 30)
        if dl == 5:
            for tick in (0, 4, 8, 12):
                b.drum("hat", t0 + tick, HAT, 34)
        if roll:
            snare_roll(b, t0, 12, 1, 50, 100)

    def half_drums(self, t0, ib):
        b = self.b
        b.drum("kick", t0, BASEDRUM, 100)
        b.drum("snare", t0 + 8, SNARE, 100)
        if ib % 2:
            b.drum("kick", t0 + 10, BASEDRUM, 62)
        if ib % 4 == 3:
            snare_roll(b, t0, 12, 1, 50, 100)

    def hit(self, t0, bar, tune=True):
        """An ensemble hit: bass, didgeridoo, kick, snare - and the guitar chord only where the tune also sounds (a second tune-instrument voice would count as new notes)."""
        b = self.b
        r = root_of(bar)
        low = near((9 + r) % 12, 34, BASS)
        b.drum("kick", t0, BASEDRUM, 100, once=True)
        b.drum("snare", t0, SNARE, 96, once=True)
        b.note("bass", t0, BASS, low, 100, once=True)
        b.note("didg", t0, DIDGERIDOO, low, 100, once=True)
        if tune:
            b.note("guitar", t0, GUITAR, near((9 + r) % 12, 47, GUITAR), 100, once=True)
            b.note("power2", t0, GUITAR, near((9 + r + 7) % 12, 54, GUITAR), 90, once=True)

    # ---- bass
    def rfr(self, t0, level, ib):
        """Root - fifth - root: A and E only. level 1: eighths; level 2: eighths with sixteenth double hits."""
        b = self.b
        ticks = list(range(0, 16, 2))
        if level == 2:
            ticks = sorted(set(ticks) | {t + 1 for t in (2, 10) if (ib + t) % 3})
        for index, tick in enumerate(ticks):
            fifth = (tick // 2) % 2 == 1
            pitch = FIFTH if fifth else LOWA
            b.note("bass", t0 + tick, BASS, pitch, 100 if tick % 8 == 0 else (88 if tick % 4 == 0 else 74))
            if tick % 4 == 0:
                b.note("didg", t0 + tick, DIDGERIDOO, pitch, 92 if tick % 8 == 0 else 80)

    def quarters(self, t0, r, vel=100):
        b = self.b
        for beat in range(4):
            b.note("bass", t0 + 4 * beat, BASS, LOWA + r, vel if beat % 2 == 0 else vel - 12)
        b.note("didg", t0, DIDGERIDOO, LOWA + r, 90)
        b.note("didg", t0 + 8, DIDGERIDOO, LOWA + r, 78)

    def slap(self, t0, bar, bc):
        b = self.b
        r = root_of(bar)
        low = near((9 + r) % 12, 34, BASS)
        for tick in range(16):
            beat, pos = divmod(tick, 4)
            iv = SLAP[bc][beat]
            pitch = low + iv + (12 if pos == 2 else 0)
            if pitch > 54:
                pitch -= 12
            b.note("bass", t0 + tick, BASS, pitch, (100, 54, 84, 52)[pos])
            if pos == 0:
                b.note("didg", t0 + tick, DIDGERIDOO, low + iv, 94)

    # ---- melodies
    def waterfall(self, t0, loud=100):
        b = self.b
        notes = sorted((57 + deg(i) for i in range(11)), reverse=True)
        for tick in range(16):
            b.note("water", t0 + tick, HARP, notes[tick % len(notes)], max(60, loud - 2 * tick))

    def cell_bar(self, t0, ib, flute, vel):
        b = self.b
        cell, shift = MAIN_BARS[ib % 8]
        for tick, length, d in cell:
            off = deg(d + shift)
            if flute:
                hold3(b, "flute", FLUTE, t0 + tick, 81 + off, length, vel)
            b.note("banjo", t0 + tick, BANJO, 57 + off, vel, once=True)

    def grid_note(self, t0, role, instrument, tick, pitch, length, vel):
        """A note held by strikes on the bar's 3-tick grid (0 3 6 9 12 15): the strikes of every voice (and the guitar hits) fall on the same ticks."""
        for t in range(tick, tick + length):
            if t % G3 == 0 or t == tick:
                self.b.note(role, t0 + t, instrument, pitch, vel, once=True)

    def shout(self, t0):
        b = self.b
        b.note("shout", t0 + 8, FLUTE, 93, 84, once=True)
        b.note("shout", t0 + 12, FLUTE, 96, 95, once=True)
        b.note("shout", t0 + 14, FLUTE, 100, 100, once=True)

    # ---- the bar by section
    def bar(self, bar):
        t0 = START + bar * BAR_T
        name, ib = MAP[bar]
        bc = bar % 4
        r = root_of(bar)
        if name == "WATER":
            if ib < 3:
                self.waterfall(t0, 100 - 3 * ib)
                self.quarters(t0, 0, 96 if ib == 0 else 92)
                self.drums(t0, 1, bc)
            else:
                self.cell_bar(t0, ib, False, 92)
                self.rfr(t0, 1, ib)
                self.drums(t0, 2, bc, roll=ib == 9)
            if ib in (0, 3, 6, 9):
                self.hit(t0, bar, tune=ib >= 3)
        elif name == "MAIN":
            self.cell_bar(t0, ib, True, 98)
            self.rfr(t0, 2 if ib >= 12 else 1, ib)
            self.drums(t0, 3 if ib >= 12 else 2, bc, roll=ib in (11, 25) or bc == 3 and ib >= 12)
            self.hit(t0, bar)
            if ib % 4 == 0 and ib >= 12:
                self.hit(t0 + 8, bar)
            if ib in (11, 25):
                self.shout(t0)
        elif name == "HALF":
            self.half_drums(t0, ib)
            self.quarters(t0, r, 100)
            lo, hi = SAD[ib % 8]
            self.grid_note(t0, "harp", HARP, 0, 57 + lo, 6, 100)
            self.grid_note(t0, "harp", HARP, 6, 57 + hi, 10, 100)
            b = self.b
            b.note("banjo", t0, BANJO, 57 + lo, 92, once=True)
            b.note("banjo", t0 + 6, BANJO, 57 + hi, 92, once=True)
            self.hit(t0, bar, tune=True)
            if ib % 2:
                self.hit(t0 + 8, bar, tune=True)
        elif name == "BUILD":
            self.build(t0, bar, ib)
        else:
            self.chorus(t0, bar, ib, bc)

    def eighth_roots(self, t0, ib):
        b = self.b
        for tick in range(0, 16, 2):
            b.note("bass", t0 + tick, BASS, LOWA, 100 if tick % 8 == 0 else (86 if tick % 4 == 0 else 72))
            if tick % 4 == 0:
                b.note("didg", t0 + tick, DIDGERIDOO, LOWA, 90 if tick % 8 == 0 else 78)

    def build(self, t0, bar, ib):
        b = self.b
        step = 16 // (4, 4, 8, 8, 16, 16)[ib]
        total = 6 * 16 - 1
        for tick in range(0, 16, step):
            if ib == 5 and tick > 13:
                continue
            b.drum("snare", t0 + tick, SNARE, int(34 + 66 * (ib * 16 + tick) / total), once=True)
        for beat in range(4):
            if ib == 5 and beat == 3:
                continue
            b.drum("kick", t0 + 4 * beat, BASEDRUM, int(60 + 40 * (ib * 16 + 4 * beat) / total))
        if ib >= 2:
            for tick in range(0, 16, 4):
                if not (ib == 5 and tick > 13):
                    b.drum("hat", t0 + tick, HAT, int(26 + 22 * (ib * 16 + tick) / total), once=True)
        r = root_of(bar)
        for tick in (list(range(0, 16, 2)) if ib < 2 else (hit_ticks(3, "ST", ib) if ib < 4 else range(16))):
            pitch = LOWA + r + (12 if ib >= 4 and tick % 4 == 2 else 0)
            vel = int(70 + 30 * (ib * 16 + tick) / total)
            b.note("bass", t0 + tick, BASS, pitch, vel)
            if tick % 4 == 0:
                b.note("didg", t0 + tick, DIDGERIDOO, LOWA + r, vel)

    def chorus(self, t0, bar, ib, bc):
        b = self.b
        if ib == 29:
            self.drums(t0, 5, bc, roll=True)
            for tick in range(16):
                b.note("bass", t0 + tick, BASS, LOWA + 7 + (12 if tick % 4 == 2 else 0), 100 if tick % 4 == 0 else 60)
            notes = sorted(57 + deg(i) for i in range(11))
            for tick in range(16):
                b.note("water", t0 + tick, HARP, notes[min(tick, len(notes) - 1)], 50 + 3 * tick)
            self.shout(t0)
            return
        idx = ib if ib < 24 else 4 + ib - 24
        self.drums(t0, 5, bc, roll=ib % 8 == 7 or ib in (23, 28))
        self.slap(t0, bar, bc)
        lo, hi = SAD[idx % 8]
        for start, off, length in ((0, lo, 6), (6, hi, 10)):
            self.grid_note(t0, "flute", FLUTE, start, 72 + off, length, 100)
            if ib >= 8:
                self.grid_note(t0, "harm", FLUTE, start, 65 + off, length, 80)
            b.note("banjo", t0 + start, BANJO, 48 + off, 90, once=True)
        r = root_of(bar)
        pc = (9 + r) % 12
        for tick in (0, 6, 12):
            b.note("guitar", t0 + tick, GUITAR, near(pc, 47, GUITAR), 100 if tick == 0 else 84, once=True)
            b.note("power2", t0 + tick, GUITAR, near((pc + 7) % 12, 54, GUITAR), 80, once=True)
        if ib % 4 == 0:
            self.hit(t0, bar)

    def intro(self):
        b = self.b
        for bar in range(4):
            t0 = 16 * bar
            notes = sorted((57 + deg(i) for i in range(11)), reverse=True)
            for tick in range(16):
                b.note("water", t0 + tick, HARP, notes[tick % len(notes)], 100 - 2 * tick)
            for beat in range(4):
                b.drum("kick", t0 + 4 * beat, BASEDRUM, 96 if beat % 2 == 0 else 80)
                b.note("bass", t0 + 4 * beat, BASS, LOWA, 100 if beat % 2 == 0 else 88)
            b.note("didg", t0, DIDGERIDOO, LOWA, 96)
            b.note("didg", t0 + 8, DIDGERIDOO, LOWA, 84)
        b.drum("snare", 0, SNARE, 96)
        for index, tick in enumerate((56, 58, 60, 61, 62, 63)):
            b.drum("snare", tick, SNARE, 50 + 10 * index, once=True)


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
    layers = finish281(target, song.b, TPS, "Raise the Axe (Boss002)",
                       "Atlas cave boss, class 5, style 4 (Japanese melodic dash), 165 BPM: koto waterfall, the chopping axe cell raised a step each bar over a root-fifth bass, a half-time sad part in A natural minor, a drum build-up, the chorus a minor third up with the tune in long notes on a sixteenth slap bass. Loops from tick 64.")
    print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
    print("   section starts (s): " + ", ".join(f"{n} {s / TPS:.0f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
