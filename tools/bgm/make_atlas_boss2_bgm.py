#!/usr/bin/env python3
"""Makes atlas_vindicator_2.nbs: "Raise the Axe (Boss002)", the axe guardian of the Atlas cave (ONE song, no second phase) - class 5, the dry death match and wildness. Written from zero (t281).

    python3 tools/bgm/make_atlas_boss2_bgm.py            writes atlas_vindicator_2.nbs into src/main/resources/bgm/
    python3 tools/bgm/make_atlas_boss2_bgm.py outfile    writes it somewhere else (a file, or a folder)

STYLE 4 of the style doc (Japanese melodic dash, 165 BPM) on the ladder of the BPM guide. 11 ticks a second, a tick is a sixteenth, a bar is 16 ticks; 4 intro bars + 92 = 1536 ticks = 139.6 s. Key G.
THE AXE: the motif is a chopping cell, G G C D C D in the rhythm 2+2+4+2+2+4 (two dull knocks, then the blade rises C - D and falls back); every bar it is raised one step of the in scale
(G Ab C D Eb), three times, and the fourth bar climbs and hangs. Wild colour: a koto-like waterfall of harp and banjo, a flute (shakuhachi) and a banjo (shamisen) on the tune, wadaiko
and djembe, a didgeridoo in unison with the bass, a rough guitar.
Time table (the boss bar is 1.45 s):
  0:00  WATERFALL (4 intro bars + 10 bars, to 0:20): the harp and banjo fall down the in scale in sixteenths over loud quarter-note bass roots (the intro), then eighth roots, the cell on the banjo.
  0:20  MAIN (26 bars, to 0:58): the bass runs ROOT - FIFTH - ROOT in eighths under the flute (bars 0-9); bar 10 the root is RELEASED (the line climbs and falls, sixteenth doubles, triplet figures).
  0:58  HALF-TIME (20 bars, to 1:27): the MODE FLIPS - the flat second is gone, G natural minor (Gm - Eb - Bb - F - Cm - Gm - D - D); the drums are halved; a long-note harp tune, sad and leaning up.
  1:27  BUILD-UP (6 bars, to 1:36): the melody is gone, a snare / kick crescendo from quarters to sixteenths on a D pedal that climbs to F (the dominant of the new key); the last beat is empty.
  1:36  CHORUS (30 bars, to 2:19): a MINOR THIRD (G -> Bb) AND an octave up: the sad tune explodes on the flute in long notes over a full sixteenth slap bass; harmony a fifth below from bar 8;
        the last bar climbs back (D major, the dominant of G minor) into tick 64.
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
LOWG = 31                                                    # G1
IN = [0, 1, 5, 7, 8]                                         # the in scale on G: G Ab C D Eb
VEL = {"X": 100, "x": 76, "o": 46, "g": 26}


def deg(i):
    q, r = divmod(i, 5)
    return IN[r] + 12 * q


CELL = [(0, 2, 0), (2, 2, 0), (4, 4, 2), (8, 2, 3), (10, 2, 2), (12, 4, 3)]
CLIMB_A = [(0, 4, 3), (4, 4, 4), (8, 8, 6)]
CLIMB_B = [(0, 4, 5), (4, 4, 7), (8, 8, 9)]
MAIN_BARS = [(CELL, 0), (CELL, 1), (CELL, 2), (CLIMB_A, 0), (CELL, 2), (CELL, 3), (CELL, 4), (CLIMB_B, 0)]
MAIN_ROOTS = [0, 0, 8, 7, 0, 8, 5, 7]
MAIN_MIN = [True, True, False, False, True, False, True, False]
SAD_TUNE = [[(0, 0, 8), (8, 7, 8)], [(0, 8, 8), (8, 12, 8)], [(0, 7, 8), (8, 10, 8)], [(0, 5, 8), (8, 10, 8)],
            [(0, 8, 8), (8, 12, 8)], [(0, 3, 4), (4, 7, 4), (8, 12, 8)], [(0, 11, 4), (4, 14, 4), (8, 19, 8)], [(0, 14, 8), (8, 19, 8)]]
SAD_ROOTS = [0, 8, 3, 10, 5, 0, 7, 7]
SAD_Q = [(0, 3, 7), (0, 4, 7), (0, 4, 7), (0, 4, 7), (0, 3, 7), (0, 3, 7), (0, 4, 7), (0, 4, 7)]
UD = [[0, 7, 12, 7, 0, 7, 5, 7], [0, 5, 7, 12, 8, 7, 5, 1], [0, 0, 7, 5, 8, 7, 12, 13], [0, 7, 8, 12, 13, 12, 8, 7]]
SLAP = [[0, 0, 7, 0], [0, 5, 7, 5], [0, 0, 7, 10], [0, 5, 7, 7]]
KICK = {1: "X...x...X...x...", 2: "X..xx..oX..xx.o.", 3: "X.xoX..xX.xoX..x", 5: "X.x.X.xxX.x.X.xx"}
SNARE_S = {1: "........x.......", 2: "....X.......X.o.", 3: "....X..o....X..o", 5: "....X.o.....X.oo"}
ROLES = {"flute": (100, 100), "harp": (100, 90), "banjo": (100, 80), "harm": (80, 90), "shout": (90, 100), "water": (100, 85), "guitar": (95, 100), "chug": (70, 100), "power2": (80, 100),
         "stab": (90, 90), "bass": (100, 100), "didg": (100, 100), "kick": (100, 100), "snare": (95, 105), "hat": (50, 115), "tom": (100, 100)}
MAP = [(name, ib) for name, n in SECTIONS for ib in range(n)]


def root_of(bar):
    name, ib = MAP[bar]
    if name in ("WATER", "MAIN"):
        return MAIN_ROOTS[ib % 8]
    if name == "HALF":
        return SAD_ROOTS[ib % 8]
    if name == "BUILD":
        return (7, 7, 7, 7, 8, 10)[ib]
    return 7 if ib == 29 else SAD_ROOTS[(ib if ib < 24 else 4 + ib - 24) % 8] + 3


def chord_pcs(bar):
    name, ib = MAP[bar]
    r = root_of(bar)
    if name in ("WATER", "MAIN"):
        q = (0, 3, 7) if MAIN_MIN[ib % 8] else (0, 4, 7)
        if q == (0, 4, 7) and MAIN_ROOTS[ib % 8] != 7:
            q = (0, 3, 7) if MAIN_ROOTS[ib % 8] == 5 else (0, 4, 7)
    elif name == "HALF":
        q = SAD_Q[ib % 8]
    elif name == "BUILD" or (name == "CHORUS" and ib == 29):
        q = (0, 4, 7)
    else:
        q = SAD_Q[(ib if ib < 24 else 4 + ib - 24) % 8]
    return [(7 + r + i) % 12 for i in q]


def pat(strings, dl, bc, pos):
    return strings[min(dl, 5) if dl > 3 else dl][pos if False else 0:0] if False else None


class Song:
    def __init__(self):
        self.b = Builder(LENGTH, ROLES)

    # ---- drums
    def drums(self, t0, dl, bc, fill=False):
        b = self.b
        kick = KICK[dl][:]
        snare = SNARE_S[dl]
        for tick, ch in enumerate(kick):
            if ch != ".":
                b.drum("kick", t0 + tick, BASEDRUM, VEL[ch])
        for tick, ch in enumerate(snare):
            if ch != ".":
                b.drum("snare", t0 + tick, SNARE, VEL[ch])
        if dl in (3, 5):
            for tick in (range(2, 16, 4) if dl == 3 else range(0, 16, 2)):
                b.drum("hat", t0 + tick, HAT, 34 if tick % 4 == 0 else 22)
        if fill or bc == 3:
            snare_roll(b, t0, 10, 1, 40, 100)

    def half_drums(self, t0, ib):
        b = self.b
        b.drum("kick", t0, BASEDRUM, 100)
        b.drum("snare", t0 + 8, SNARE, 100)
        if ib % 2:
            b.drum("kick", t0 + 10, BASEDRUM, 62)
        for tick in (4, 12):
            b.drum("hat", t0 + tick, HAT, 32)
        if ib % 4 == 3:
            snare_roll(b, t0, 12, 1, 50, 100)

    def hit(self, t0, bar, scale_=1.0):
        b = self.b
        r = root_of(bar)
        low = near((7 + r) % 12, 34, BASS)
        b.drum("kick", t0, BASEDRUM, 100 * scale_, once=True)
        b.drum("snare", t0, SNARE, 96 * scale_, once=True)
        b.note("bass", t0, BASS, low, 100 * scale_, once=True)
        b.note("didg", t0, DIDGERIDOO, low, 100 * scale_, once=True)
        b.note("guitar", t0, GUITAR, near((7 + r) % 12, 47, GUITAR), 100 * scale_, once=True)
        b.note("power2", t0, GUITAR, near((7 + r + 7) % 12, 54, GUITAR), 90 * scale_, once=True)
        b.note("stab", t0, HARP, near((7 + r) % 12, 58, HARP), 96 * scale_, once=True)

    # ---- bass
    def bass_bar(self, t0, bar, level, bc, ib):
        b = self.b
        r = root_of(bar)
        ticks = [0, 4, 8, 12] if level == 0 else (list(range(0, 16, 2)) if level in (1, 2) else hit_ticks(3, "ST", ib % 5 + 2 * bc))
        for tick in ticks:
            if level == 0:
                off, vel = 0, 100 if tick % 8 == 0 else 86
            elif level == 1:
                off, vel = 0, bass_velocity(tick, 2)
            elif level == 2:
                off = ([0, 7, 0, 7, 0, 7, 0, 7] if bc != 3 else [0, 7, 0, 7, 8, 7, 5, 7])[tick // 2]
                vel = bass_velocity(tick, 2)
            else:
                off, vel = UD[bc][tick // 2], bass_velocity(tick, 3)
            pitch = LOWG + r + off
            if pitch > 54:
                pitch -= 12
            b.note("bass", t0 + tick, BASS, pitch, vel)
            if tick % 4 == 0 and level >= 0:
                b.note("didg", t0 + tick, DIDGERIDOO, pitch, 92 if tick % 8 == 0 else 80)
            if level >= 3 and tick % 4 == 2:
                b.note("chug", t0 + tick, GUITAR, 43 + (r + off) % 12, 62 + (8 if tick % 4 == 0 else 0))

    def slap(self, t0, bar, bc):
        b = self.b
        r = root_of(bar)
        low = near((7 + r) % 12, 34, BASS)
        for tick in range(16):
            beat, pos = divmod(tick, 4)
            iv = SLAP[bc][beat]
            pitch = low + iv + (12 if pos == 2 else 0)
            if pitch > 54:
                pitch -= 12
            b.note("bass", t0 + tick, BASS, pitch, (100, 54, 84, 52)[pos])
            if pos in (0, 2):
                b.note("didg", t0 + tick, DIDGERIDOO, low + iv, 94 if pos == 0 else 80)
            if tick % 2 == 0:
                b.note("chug", t0 + tick, GUITAR, near((7 + r + iv) % 12, 48, GUITAR), 100 if tick % 8 == 0 else (78 if tick % 4 == 0 else 62))
        for tick in range(0, 16, 2):
            b.note("power2", t0 + tick, GUITAR, near((7 + r + 7) % 12, 54, GUITAR), 90 if tick % 8 == 0 else (76 if tick % 4 == 0 else 60))

    # ---- waterfall
    def water(self, t0, bar, loud=96, rising=False):
        b = self.b
        pcs = chord_pcs(bar)
        notes = sorted((55 + deg(i) for i in range(11)), reverse=not rising)
        for tick in range(16):
            p = notes[tick % len(notes)]
            b.note("water", t0 + tick, HARP if tick % 4 else BANJO, p, max(40, loud - 2 * tick) if not rising else 50 + 3 * tick)

    def shout(self, t0):
        b = self.b
        b.note("shout", t0 + 7, FLUTE, 89, 70, once=True)
        for index, tick in enumerate((8, 10)):
            b.note("shout", t0 + tick, FLUTE, 91, 80 + 4 * index, once=True)
        b.note("shout", t0 + 12, FLUTE, 94, 95, once=True)
        b.note("shout", t0 + 14, FLUTE, 98, 100, once=True)

    def cell_bar(self, t0, ib, flute, vel):
        b = self.b
        cell, shift = MAIN_BARS[ib % 8]
        for tick, length, d in cell:
            off = deg(d + shift)
            if flute:
                hold(b, "flute", FLUTE, t0 + tick, 79 + off, length, vel, every=2)
            hold(b, "banjo", BANJO, t0 + tick, 55 + off, length, vel, every=2 if length >= 4 else 4)

    # ---- the bar by section
    def bar(self, bar):
        t0 = START + bar * BAR_T
        name, ib = MAP[bar]
        bc = bar % 4
        if name == "WATER":
            if ib < 4:
                self.water(t0, bar, 100 - 2 * ib)
                self.bass_bar(t0, bar, 1, bc, ib)
                self.drums(t0, 1, bc)
                if ib == 0:
                    self.hit(t0, bar)
            else:
                self.cell_bar(t0, ib, False, 92)
                self.bass_bar(t0, bar, 1 if ib < 8 else 2, bc, ib)
                self.drums(t0, 2, bc, fill=ib == 9)
                if ib in (4, 8):
                    self.hit(t0, bar)
        elif name == "MAIN":
            released = ib >= 10
            self.cell_bar(t0, ib, True, 98)
            self.bass_bar(t0, bar, 3 if released else 2, bc, ib)
            self.drums(t0, 3 if released else 2, bc, fill=ib in (9, 17, 25))
            if ib % 8 == 0 or ib == 10:
                self.hit(t0, bar)
            if released:
                for tick, length, d in MAIN_BARS[ib % 8][0]:
                    self.b.note("guitar", t0 + tick, GUITAR, 43 + deg(d + MAIN_BARS[ib % 8][1]) % 24, 86, once=True)
            if ib == 9 or ib == 25:
                self.shout(t0)
        elif name == "HALF":
            self.half_drums(t0, ib)
            self.bass_bar(t0, bar, 0, bc, ib)
            for tick, off, length in SAD_TUNE[ib % 8]:
                hold(self.b, "harp", HARP, t0 + tick, 55 + off, length, 100, rise=0)
                self.b.note("banjo", t0 + tick, BANJO, 55 + off, 92, once=True)
            if ib % 4 == 0:
                self.hit(t0, bar)
        elif name == "BUILD":
            self.build(t0, bar, ib)
        else:
            self.chorus(t0, bar, ib, bc)

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
            for tick in range(0, 16, 2):
                if not (ib == 5 and tick > 13):
                    b.drum("hat", t0 + tick, HAT, int(24 + 22 * (ib * 16 + tick) / total), once=True)
        r = root_of(bar)
        for tick in (list(range(0, 16, 2)) if ib < 2 else (hit_ticks(3, "ST", ib) if ib < 4 else range(16))):
            if ib == 5 and tick > 13:
                continue
            pitch = LOWG + r + (12 if ib >= 4 and tick % 4 == 2 else 0)
            vel = int(70 + 30 * (ib * 16 + tick) / total)
            b.note("bass", t0 + tick, BASS, pitch, vel)
            if tick % 4 == 0:
                b.note("didg", t0 + tick, DIDGERIDOO, LOWG + r, vel)
            if ib >= 2 and tick % 2 == 0:
                b.note("chug", t0 + tick, GUITAR, 43 + r % 12, 56 + 4 * ib)

    def chorus(self, t0, bar, ib, bc):
        b = self.b
        last = ib == 29
        idx = ib if ib < 24 else 4 + ib - 24
        self.drums(t0, 5, bc, fill=ib % 8 == 7 or ib in (23, 29))
        if last:
            for tick in range(16):
                r = root_of(bar)
                b.note("bass", t0 + tick, BASS, LOWG + r + (12 if tick % 4 == 2 else 0), 100 if tick % 4 == 0 else 60)
            self.water(t0, bar, rising=True)
            self.shout(t0)
            return
        self.slap(t0, bar, bc)
        for tick, off, length in SAD_TUNE[idx % 8]:
            hold(b, "flute", FLUTE, t0 + tick, 82 + off, length, 100, rise=0)
            hold(b, "harp", HARP, t0 + tick, 58 + off, length, 100, rise=0)
            if ib >= 8:
                hold(b, "harm", FLUTE, t0 + tick, 82 + off - 7, length, 76, rise=0)
        if ib % 4 == 0:
            self.hit(t0, bar)

    def intro(self):
        b = self.b
        for bar, (r, q) in enumerate([(0, (0, 3, 7)), (0, (0, 3, 7)), (8, (0, 4, 7)), (7, (0, 4, 7))]):
            t0 = 16 * bar
            notes = sorted((55 + deg(i) for i in range(11)), reverse=True)
            for tick in range(16):
                b.note("water", t0 + tick, HARP if tick % 4 else BANJO, notes[tick % len(notes)], 100 - 2 * tick)
            for beat in range(4):
                b.drum("kick", t0 + 4 * beat, BASEDRUM, 96 if beat % 2 == 0 else 80)
                b.note("bass", t0 + 4 * beat, BASS, LOWG + r, 100 if beat % 2 == 0 else 88)
            b.note("didg", t0, DIDGERIDOO, LOWG + r, 96)
            b.note("didg", t0 + 8, DIDGERIDOO, LOWG + r, 84)
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
                       cap=44, description="Atlas cave boss, class 5, style 4 (Japanese melodic dash), 165 BPM: koto waterfall, the chopping axe cell raised a step each bar over a root-fifth-root bass, a half-time sad part in G natural minor, a drum build-up, the chorus a minor third and an octave up on a sixteenth slap bass. Loops from tick 64.")
    print("   notes a second by section: " + ", ".join(f"{n} {c:.1f}" for n, c in sections_density(layers, LENGTH, TPS, edges)))
    bass = [layer for layer in layers if layer.name.startswith("bass")]
    print("   bass notes per bar by section: " + ", ".join(f"{n} {sum(1 for layer in bass for t in layer.notes if s <= t < e) / ((e - s) / 16):.1f}" for n, s, e in edges))
    print("   section starts (s): " + ", ".join(f"{n} {s / TPS:.0f}" for n, s, e in edges))


if __name__ == "__main__":
    main()
