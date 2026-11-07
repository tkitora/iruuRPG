#!/usr/bin/env python3
"""Makes reaper_phase1.nbs and reaper_phase2.nbs: "Silence of the Scythe (Boss005)" and "Silence of the Scythe, Unleashed (Boss005)". REBUILT FROM ZERO (t290) so that it CONTAINS the structure guides.

    python3 tools/bgm/make_reaper_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_reaper_bgm.py outdir     writes them somewhere else

CLASS 3 (inorganic fear and erosion) in STYLE 2 of the structure guide (despair and sacred, many stages): 130 BPM = 8.667 ticks a second, a tick is a sixteenth, a beat 4 ticks, a bar 5/4 = 20 ticks (3+2),
76 bars = 1584 ticks = 182.8 s; every section is a multiple of 4 bars. Key: G (bass G1). The loop (tick 64) is a bar line.
THE MOTIF - "the scythe": four notes G Db Ab D (a tritone leap, then a half step above the start, then a tritone leap again: 0 6 1 7 semitones), struck on the banjo (dry, bony) at ticks 0 2 5 8 and then ONE
long note on the minor sixth (Eb) re-struck every 3 ticks and hanging up; the cell is repeated a half step higher every bar for four bars. In the sacred part the same hanging idea becomes a slow line over G major.
TIME TABLE (bars of 2.31 s):
  P1  0:00-0:26 (intro + 8 bars)   IRREGULAR rhythm + dissonance: the bass does not follow the beat (6 hits a bar rotating by one tick each bar), the blade enters rung by rung; QUARTER-ish bass (the ladder starts low).
  P2  0:26-0:54 (12 bars)          PSEUDO DOUBLE TEMPO: the snare on every beat, the kick between the beats; bass eighths -> eighths with sixteenth doubles / 3-tick runs; the full cell.
  P3  0:54-1:21 (12 bars)          THE MELODY IS LOST: only the bass and staccato anvil and bone (cow bell, iron-xylophone clank, hat, ghost snare); the drums halve.
  P4  1:21-2:05 (20 bars)          SACRED, MODE FLIP to G major: block organ chords (pling + harp + 8-bit top voice struck together every 3 ticks) G | Em | C | D, the tune slow (two long notes a bar), half-time drums;
                                   the tune jumps an octave in the second half; the last bar (51) is the BREAK: drums gone, the bass pedal stays, one bell.
  CL  2:05-3:03 (24 bars)          CLIMAX: the P2 snare and kick fused with the long chords over the SIXTEENTH bass gun on the chord roots; the tune in long notes (3 a bar), a whole step up for bars 10-21, the top
                                   note (F#5) held and struck every 3 ticks in the last bars; it ends on the dominant D, leading back to the dark G.
Bass ladder: quarters (intro) -> irregular (P1) -> eighths -> doubles (P2) -> eighths -> doubles (P3) -> half-time (P4) -> sixteenth gun (CL). Phase 2: a half step up (G#), the 5 turns 3+2 / 2+3, more hits, the heart
twice as fast, a second voice under the tune. Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from cat3_machine import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS, BAR, BARS = 130 / 15, 20, 76
SECTIONS = [("P1", 0, 8), ("P2", 8, 12), ("P3", 20, 12), ("P4", 32, 20), ("CL", 52, 24)]
BREAK_BAR = 51
SPECS = {  # layer: (volume, pan)
    "pound": (100, 100), "heart": (100, 100), "snare": (100, 105), "clock": (100, 120), "noise": (100, 130), "roll": (100, 105), "tock": (100, 70),
    "bass": (100, 100), "drone": (100, 100), "tune": (100, 95), "tune2": (100, 105), "clank": (100, 85), "pad1": (100, 90), "pad2": (100, 110), "bell": (100, 100),
}
PHASES = [dict(shift=0, alt=False), dict(shift=1, alt=True)]
ROOT = 31                                              # G1
CELL = (0, 6, 1, 7)                                    # G Db Ab D
CELL_TICKS = (0, 2, 5, 8)
BASS_LINE = [0, 3, 1, 6, 0, 7, 6, 1, 3, 0, 1, 7]
P1_PITCH = [0, 0, 1, 6, 0, 1]
# the sacred chords: (bass root, three low voices, the tune in P4 (two long notes a bar), the tune in the climax (three notes a bar))
CH = {"G": (31, (55, 59, 62), (67, 71), (67, 71, 74)), "Em": (40, (55, 59, 64), (67, 71), (67, 71, 76)),
      "C": (36, (55, 60, 64), (67, 72), (67, 72, 76)), "D": (38, (54, 57, 62), (69, 74), (69, 74, 78))}
PROGS = [["G", "Em", "C", "D"], ["C", "D", "G", "Em"], ["G", "Em", "C", "D"], ["C", "D", "G", "Em"], ["C", "Em", "D", "D"]]
CL_PROGS = [["G", "Em", "C", "D"], ["C", "D", "G", "Em"], ["G", "Em", "C", "D"], ["C", "D", "G", "Em"], ["G", "Em", "C", "D"], ["C", "Em", "C", "D"]]
SILENCES = {0: [(3, 14), (6, 8), (12, 12)], 1: [(5, 14), (14, 10)]}


def accents(k, ph):
    """Where the groups of the 5 begin: 3+2 (0, 12); in phase 2 every other bar 2+3 (0, 8)."""
    return (0, 8) if ph["alt"] and k % 2 == 1 else (0, 12)


def hit(g, tick, note, clank=True, big=False):
    """An ensemble hit (6-7 notes at one tick): kick, bass, pedal, snare, cow bell, hat and (in the dark parts) the low iron-xylophone clank."""
    g.drum("pound", tick, BASEDRUM, 100, over=True)
    g.put("bass", tick, BASS, note, 98, over=True)
    g.put("drone", tick, DIDGERIDOO, note, 92, over=True)
    g.drum("snare", tick, SNARE, 92 if big else 80, over=True)
    g.put("tock", tick, COW_BELL, 84, 82, over=True)
    if clank:
        g.put("clank", tick, IRON_XYLOPHONE, 54, 96, over=True)
    g.drum("clock", tick, HAT, 62, over=True)


def cell(g, s, base, vel, count=4, up=False):
    """The scythe: ``count`` notes of the cell, then a long note on the minor sixth, struck every 3 ticks (hanging up; every 4th bar it leaps an octave higher)."""
    for j in range(count):
        g.put("tune", s + CELL_TICKS[j], BANJO, base + CELL[j], vel + (6 if j == 0 else 0))
    if count >= 4:
        hold = base + (20 if up else 8)
        for n, t in enumerate(range(10, 20, 3)):
            g.put("tune", s + t, BANJO, min(78, hold), vel * (0.95 ** n) + 4)


def organ(g, s, voices, v0, v1, tune=None, tune_second=None):
    """A long chord for one bar: the voices (and the top tune voice) are struck TOGETHER every 3 ticks (a held block chord, not an arpeggio)."""
    for t in range(0, BAR, 3):
        v = v0 + (v1 - v0) * t / (BAR - 1)
        g.put("pad1", s + t, PLING, voices[0], v)
        g.put("pad2", s + t, HARP, voices[2], v * 0.95)
        if tune is not None:
            note = tune if (tune_second is None or t < 12) else tune_second
            g.put("tune", s + t, BIT, min(78, note), min(100, v + 4))


def compose(phase):
    ph = PHASES[phase]
    sh = ph["shift"]
    R = ROOT + sh
    g = Grid(TPS, BAR, BARS, SPECS)
    gun_rng = Rng(29 + phase)
    # ---- the intro: quarters, loud; a pedal on the 3-tick grid from tick 0; the snare roll
    for t in range(0, 64, 3):
        g.put("drone", t, DIDGERIDOO, R, 76 + t // 6)
    for t in range(0, 64, 12):
        g.put("bass", t, BASS, R, 96)
        g.drum("pound", t, BASEDRUM, 98)
    for t in (24, 36, 48):
        g.put("tock", t, COW_BELL, 78, 80)
    cell(g, g.start(-1), 54 + sh, 84, 3)
    g.clicks("roll", SNARE, 66, 63, [1, 1, 1, 1, 2, 2, 2, 3, 3, 4], 40, 98)
    # ---- the heart
    for first, count, period in ((0, 8, 7 - 2 * phase), (8, 12, 99)):
        tick = g.start(first)
        n = 0
        while tick < g.start(first + count):
            if n % 8 != 7:
                g.drum("heart", tick, BASEDRUM, 90, over=True)
                g.drum("heart", tick + 2, BASEDRUM, 72, over=True)
            tick += period
            n += 1
    for k in range(BARS):
        name, i, count = section_of(SECTIONS, k)
        s = g.start(k)
        acc = accents(k, ph)
        # ---------------- P1: the beat has no head
        if name == "P1":
            rot = i % BAR
            ticks = sorted((t + rot) % BAR for t in (0, 3, 7, 10, 13, 17))
            for j, t in enumerate(ticks):
                g.put("bass", s + t, BASS, R + P1_PITCH[j], 84 + (12 if j == 0 else 0) + 2 * i)
            for t in (ticks[0], ticks[3]):
                g.drum("pound", s + t, BASEDRUM, 96)
            for t in range(1, BAR, 3):
                g.drum("clock", s + t, HAT, 44 + 2 * i)
            if i >= 2:
                cell(g, s, 55 + sh + i % 4, 88, min(4, 1 + (i - 1) // 2))
            if i >= 1:
                for t in acc:
                    hit(g, s + t, R, True)
        # ---------------- P2: pseudo double tempo
        elif name == "P2":
            mode, p = ("e", 0) if i < 4 else ("m", 0.5 + 0.06 * i)
            for t in gun_ticks(mode, BAR, k, p, gun_rng):
                g.put("bass", s + t, BASS, R + BASS_LINE[(k * 5 + t // 4) % 12], 84 + (14 if t in acc else 0) + (4 if t % 4 == 0 else 0))
            for b in range(5):
                g.drum("snare", s + 4 * b, SNARE, 72 + (12 if 4 * b in (acc[0] + 4, acc[1] + 4) else 0) + 6 * phase)
                g.drum("pound", s + 4 * b + 2, BASEDRUM, 86)
            for t in range(0, BAR, 2):
                g.drum("clock", s + t, HAT, 46 if t in acc else 34)
            cell(g, s, 55 + sh + i % 4, 90, 4, up=(i % 4 == 3))
            for t in acc:
                hit(g, s + t, R, True)
            if i >= 4:
                for t in (4, 8, 16):
                    if t not in acc:
                        hit(g, s + t, R, False)
        # ---------------- P3: the melody is lost
        elif name == "P3":
            mode, p = ("e", 0) if i < 4 else ("m", 0.5 + 0.07 * (i - 4))
            for t in gun_ticks(mode, BAR, k, p, gun_rng):
                g.put("bass", s + t, BASS, R + BASS_LINE[(k * 7 + t // 4) % 12] + min(4, i // 3), 90 + (10 if t in acc else 0) + 4 * phase)
            for j, t in enumerate((0, 3, 6, 8, 11, 14, 16, 18)):
                g.put("tock", s + t, COW_BELL, (84, 78, 84, 81, 76, 84, 78, 81)[(j + k) % 8], 84 + (8 if j % 3 == 0 else 0))
            for t in acc:
                hit(g, s + t, R, True)
                g.put("clank", s + t + 6, IRON_XYLOPHONE, 55 + sh, 82)
            if i >= 3:
                for t in (4, 8, 16):
                    if t not in acc:
                        hit(g, s + t, R, False)
            for t in (2, 8, 14):
                g.drum("snare", s + t, SNARE, 60 + i)
            for t in (5, 15):
                g.drum("clock", s + t, HAT, 56)
        # ---------------- P4: sacred
        elif name == "P4":
            if i == 19:                                         # the break bar: handled below
                continue
            chord = PROGS[i // 4][i % 4]
            root, voices, mel, _ = CH[chord]
            second_half = i >= 8                                 # from 1:40 the tune jumps up (an octave-ish leap: the guide's "tune an octave up")
            m1, m2 = (min(78, m + 9) for m in mel) if second_half else (max(m - 3, voices[2] + 2) for m in mel)
            organ(g, s, [v + sh for v in voices], 86 + (i % 4), 94, m1 + sh, m2 + sh)
            if phase == 1:
                for t in range(0, BAR, 3):
                    g.put("tune2", s + t, GUITAR, 42 + (voices[0] + sh - 42) % 12 + 12, 82)
            hit(g, s, root + sh, False, i % 4 == 0)
            hit(g, s + acc[1], root + sh, False)
            g.drum("pound", s + acc[1], BASEDRUM, 90)
            g.put("bass", s + acc[1], BASS, root + sh, 92)
            for t in range(3, BAR, 3):
                g.put("drone", s + t, DIDGERIDOO, root + sh, 78)
        # ---------------- CL: the climax
        else:
            chord = CL_PROGS[i // 4][i % 4]
            root, voices, _, mel = CH[chord]
            up = 2 if 8 <= i < 22 else 0
            r = root + sh + up
            for t in range(BAR):
                n = r + (0, 7, 0, 12, 7)[t // 4]
                if n > 54:
                    n -= 12
                g.put("bass", s + t, BASS, n, 90 + (8 if t in acc else 0) + (4 if t % 4 == 0 else -4 if t % 2 else 0))
            for b in range(5):
                g.drum("snare", s + 4 * b, SNARE, 86 + 4 * phase + (8 if 4 * b in acc else 0))
                g.drum("pound", s + 4 * b, BASEDRUM, 96 if 4 * b in acc else 88)
                if i >= 16:
                    g.drum("snare", s + 4 * b + 2, SNARE, 72)               # the finale: the snare on every half beat
            for t in range(2, BAR, 4):
                g.drum("clock", s + t, HAT, 54)
            a, b2, c = mel
            if i >= 16:                                          # the last bars: the top note held for the whole bar and struck every 3 ticks
                a = b2 = c = max(c, 76)
            for t in range(0, BAR, 3):
                note = a if t < 6 else (b2 if t < 12 else c)
                v = 92 + 2 * (i >= 16)
                g.put("tune", s + t, BIT, min(78, note + up + sh), v)
                g.put("pad1", s + t, PLING, voices[0] + up + sh, 86 + 3 * phase)
                g.put("pad2", s + t, HARP, voices[2] + up + sh, 84 + 3 * phase)
            if phase == 1:
                for t in range(0, BAR, 3):
                    g.put("tune2", s + t, GUITAR, 42 + (voices[0] + up + sh - 42) % 12, 80)
            for t in acc:
                hit(g, s + t, r, False, t == 0 and i % 4 == 0)
            if i >= 4:
                for t in (4, 8, 16):
                    if t not in acc:
                        hit(g, s + t, r, False)
    # ---- rolls before a block, the silences of the scythe (the pedal stays), blows at the starts
    for bar in (7, 19, 31):
        g.clicks("roll", SNARE, 66, g.start(bar) + 19, [1, 1, 1, 1, 2, 2, 3], 40, 100)
    for bar, from_tick in SILENCES[phase]:
        g.mute(g.start(bar) + from_tick, g.start(bar + 1), keep=("bass", "drone"))
        for t in range(g.start(bar) + from_tick, g.start(bar + 1), 3):
            g.put("drone", t, DIDGERIDOO, R, 74)
        g.drum("pound", g.start(bar + 1), BASEDRUM, 100, over=True)
        g.put("bass", g.start(bar + 1), BASS, R, 100, over=True)
        g.drum("snare", g.start(bar + 1), SNARE, 84, over=True)
    for first in (8, 20, 32):
        hit(g, g.start(first), R if first < 32 else CH["G"][0] + sh, first < 32, True)
    # ---- the break: the bar before the climax: no drums, one bell, the bass pedal stays; a snare roll before it, a blow after it
    g.clicks("roll", SNARE, 66, g.start(BREAK_BAR) - 1, [1, 1, 1, 1, 2, 2, 2, 3], 50, 100, over=True)
    broot = CH["D"][0] + sh
    g.restrike("drone", DIDGERIDOO, broot, g.start(BREAK_BAR), BAR, 3, 62, 72)
    g.put("bass", g.start(BREAK_BAR), BASS, broot, 70)
    g.put("bell", g.start(BREAK_BAR) + 10, BELL, 96, 50)
    hit(g, g.start(52), CH["G"][0] + sh, False, True)
    return g


TITLES = ("Silence of the Scythe (Boss005)", "Silence of the Scythe, Unleashed (Boss005)")
FILES = ("reaper_phase1.nbs", "reaper_phase2.nbs")
DESCRIPTIONS = (
    "Reaper fight, phase 1, class 3 (style 2, 130 BPM, 5/4, G): a banjo cell of tritones and half steps over an irregular bass, a pseudo double tempo, the melody lost (bass, anvil and bone), the flip to G major organ chords in half-time, a one-bell break, then the sixteenth gun fused with the chords and a climbing tune.",
    "Reaper fight, phase 2: a half step up, the 5 turns 3+2 / 2+3, a second voice under the tune. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, 64 + first * BAR, 64 + (first + count) * BAR) for name, first, count in SECTIONS]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks, bass_layers=("bass",), keep_layers=("tune", "pad1", "pad2"))


if __name__ == "__main__":
    main()
