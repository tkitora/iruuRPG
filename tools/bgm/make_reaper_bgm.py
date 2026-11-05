#!/usr/bin/env python3
"""Makes reaper_phase1.nbs and reaper_phase2.nbs: "Silence of the Scythe (Boss005)" and "Silence of the Scythe, Unleashed (Boss005)", a reaper that is a clockwork, in two phases.

    python3 tools/bgm/make_reaper_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_reaper_bgm.py outdir     writes them somewhere else

CLASS 3, "inorganic fear and erosion" (a machine, an alien, a will-less curse), built on STYLE 2 of the structure guide (t277: "despair and sacred, many stages", 120-130 BPM) and the subdivision guide (t276).
130 BPM = 8.667 ticks a second, the song tick is a SIXTEENTH, a beat of 4 ticks, a bar of 5/4 = 20 ticks (3+2), 76 bars of the loop = 1584 ticks = 182.8 s. A bar of 20 ticks meets the engine's 16-tick
line every 4 bars, so every section is a multiple of 4 bars. The loop (tick 64) is a bar line: the intro is a silent offset of 4 ticks and three whole bars.
TIME TABLE (the style table scaled from 3:30 to 3:00; bars of 2.31 s):
  P1  0:00-0:26 (intro + 8 bars)   IRREGULAR rhythm + dissonance: the bass and the hat strike on a cycle of 17 ticks against the bar of 20 (3 3 2 3 2 2 2), so the head of the beat cannot be found; the loop cell B C F E erodes.
  P2  0:26-1:03 (16 bars)          PSEUDO DOUBLE TEMPO: the snare on EVERY beat (not on the second beat of each group), the bass eighths -> eighths with sixteenth doubles / 3-tick runs; a held bit + pling dissonance
                                   (the cell slowed to long notes), a swelling cluster and a stop at the end.
  P3  1:03-1:40 (16 bars)          THE MELODY IS GONE: only the bass (quarters -> eighths -> doubles) and staccato "anvil and bone" - cow bell, a low iron-xylophone clank, ghost snare rim, 2-tick hat bursts - on the 17-tick cycle.
  P4  1:40-2:17 (16 bars)          SACRED: the MODE FLIPS - the B C F cluster turns into B major (B D# F#), organ-like long chords (pling and harp arpeggio pads re-struck every 4 ticks, a flute choir above, a
                                   didgeridoo pedal that follows the chord roots, B | G#m | E | F# ...), the drums in HALF-TIME (a slow heart, a toll).
  BREAK  (bar 55, 2:15)            one bar with every layer muted and a single high bell note.
  CL  2:17-3:00 (20 bars)          CLIMAX: the P2 rhythm (snare on every beat, the sixteenth bass gun on the chord roots, hat and pound in sixteenths) FUSED with the P4 chords, the major cell back, and the highest
                                   notes (the 8-bit chip, F#5) HAMMERED every 2 ticks; the last bar is the dominant F#, leading back to the dark loop start.
Bass ladder: quarters (intro) -> irregular (P1) -> eighths -> eighths + doubles (P2) -> quarters -> eighths -> doubles (P3) -> half-time (P4) -> sixteenth gun (CL); phase 2 brings the gun earlier.
The heart is the bass drum on its own cycle (against the bar of 20 it is never on the groove). The SILENCES (windows where every layer is cut, then a blow) stay in P1-P3.
Phase 2 (Unleashed): a half step up, the heart twice as fast, the 5 turns 3+2 / 2+3, the sixteenth gun earlier (P2, P3), more pads (flute and a third pad voice), the hammered line fuller.
Everything is written by what SOUNDS. Nothing is random (cat3_machine.Rng). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat3_machine import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS, BAR, BARS = 130 / 15, 20, 76
SECTIONS = [("P1", 0, 8), ("P2", 8, 16), ("P3", 24, 16), ("P4", 40, 16), ("CL", 56, 20)]
BREAK_BAR = 55
SPECS = {  # layer: (volume, pan)
    "clock": (100, 115), "tock": (100, 70), "heart": (100, 100), "pound": (100, 100), "drone": (100, 100), "bass": (100, 100),
    "ost": (100, 95), "ost2": (100, 120), "snare": (100, 105), "noise": (100, 130), "roll": (100, 105),
    "cl1": (100, 90), "cl2": (100, 110), "cl3": (100, 100),
    "hold1": (100, 95), "hold2": (100, 105), "clank": (100, 85), "bone": (100, 125),
    "pad": (100, 95), "harp": (100, 110), "choir": (100, 100), "ham": (100, 105), "bell": (100, 100),
}
PHASES = [dict(shift=0, alt=False), dict(shift=1, alt=True)]
# the dark loop of eight notes: (tick, pitch, rank); the rank is the order of erosion (0 goes first, 7 - the anchor - last)
CELL = [(0, 59, 7), (2, 60, 3), (6, 65, 5), (8, 64, 1), (12, 59, 6), (14, 60, 2), (16, 65, 4), (18, 64, 0)]
GAIN = [(4, 58), (15, 66), (10, 56), (19, 72)]          # notes the loop gains in other places (cluster against the cell)
CELL_MAJOR = [(0, 59), (2, 63), (6, 66), (8, 63), (12, 59), (14, 63), (16, 66), (18, 68)]     # the same rhythm in B major (B D# F# G#)
# per phase: notes of the dark cell removed per bar (9 = none left) in P1 and P2, notes gained per bar in P2
ERODE_P1 = {0: [7, 6, 5, 5, 4, 4, 3, 3], 1: [6, 5, 4, 4, 3, 3, 2, 2]}
ERODE_P2 = {0: [3, 2, 2, 3, 2, 1, 1, 2, 1, 2, 1, 3, 4, 5, 6, 7], 1: [2, 1, 1, 2, 0, 1, 0, 1, 0, 1, 1, 2, 3, 4, 5, 6]}
GAIN_P2 = [0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1]
HEART_PERIOD = {0: {"P1": 8, "P2": 8, "P3": 10, "P4": 16, "CL": 6}, 1: {"P1": 4, "P2": 4, "P3": 5, "P4": 8, "CL": 3}}
HEART_VEL = {"P1": (96, 74), "P2": (100, 82), "P3": (98, 76), "P4": (98, 74), "CL": (100, 88)}
DRONE_VEL = {"P1": 74, "P2": 80, "P4": 78, "CL": 92}
# the silences of the scythe: (bar, from tick, to the end of the bar)
SILENCES = {0: [(3, 12), (6, 6), (11, 12), (14, 8), (19, 10), (27, 12), (31, 6), (35, 14), (39, 6)], 1: [(5, 12), (11, 14), (19, 12), (31, 8), (39, 6)]}
IRREGULAR = [3, 3, 2, 3, 2, 2, 2]        # the cycle of 17 ticks of P1 / P3 (the bar has 20)
HAT_CYCLE = [2, 3, 2, 2, 3, 3, 2]
CYCLE_PITCH = [0, 1, 0, 6, 0, 1, 7]
# the chords of the sacred part (bass root, four voices), B major and its neighbours
CH = {"B": (35, [59, 63, 66, 71]), "G#m": (32, [56, 59, 63, 68]), "E": (40, [59, 64, 68, 71]), "F#": (42, [58, 61, 66, 70])}
TOP = {"B": 71, "G#m": 68, "E": 71, "F#": 70}
CELL_OFFSET = {"B": 0, "G#m": 0, "E": 5, "F#": 7}
PROGS = [["B", "G#m", "E", "F#"], ["B", "E", "F#", "B"], ["G#m", "E", "B", "F#"], ["E", "G#m", "F#", "F#"]]
CL_REPS = [0, 1, 2, 3, 0]
SMALL = [0, 0, 1, 0, 6]
WALK = [0, 1, 0, 6, 7, 6, 1, 0, 3, 1, 6, 7]
BASS_VEL = {"P2": 80, "P3": 86, "P1": 74}
# the held dissonance: a line of long notes (the cell B C F E slowed down), (pitches, ticks) per 4 bars
LINES = [
    ([59, 60, 65, 64, 58, 59], [12, 8, 20, 12, 8, 20]),
    ([71, 77, 72, 76, 70, 77], [8, 12, 20, 8, 12, 20]),          # the line ends climbing and hanging
]


def chord_of(name, i):
    """The chord (name) of bar ``i`` of the sacred P4 or the climax."""
    reps = [0, 1, 2, 3] if name == "P4" else CL_REPS
    return PROGS[reps[i // 4]][i % 4]


def bass_plan(phase, name, i):
    """(mode, restlessness) of the bass in a bar; modes as in cat3_machine.gun_ticks, "h" is half-time (P4)."""
    if phase == 0:
        if name == "P2":
            if i < 4:
                return ("e", 0)
            return ("m", 0.3 + 0.07 * (i - 4)) if i < 10 else (("m", 1.0) if i < 14 else ("s", 1))
        if name == "P3":
            if i < 2:
                return ("q", 0)
            if i < 6:
                return ("e", 0)
            return ("m", 0.4 + 0.06 * (i - 6)) if i < 12 else (("m", 1.0) if i < 14 else ("s", 1))
    else:
        if name == "P2":
            if i < 2:
                return ("e", 0)
            if i < 8:
                return ("m", 0.5 + 0.07 * (i - 2))
            return ("m", 1.0) if i % 4 == 3 else ("s", 1)
        if name == "P3":
            if i < 2:
                return ("e", 0)
            return ("s", 1) if i >= 10 else ("m", 1.0)
    return ("h", 0) if name == "P4" else ("s", 1)


def bass_velocity(name, phase, i, t, acc, mode):
    v = BASS_VEL.get(name, 90) + 4 * phase
    if name == "P2":
        v += int(i * 0.8)
    elif name == "P3":
        v += i
    elif name == "CL":
        v += 4
    if mode == "q":
        v -= 10
    if t in acc:
        v += 16
    elif t % 4 == 0:
        v += 4
    elif t % 2 == 1:
        v -= 12 if mode != "s" else 8
    return max(1, min(100, v))


def accents(k, ph):
    """Where the groups of the 5 begin: 3+2 (0, 12), in phase 2 every other bar 2+3 (0, 8)."""
    return (0, 8) if ph["alt"] and k % 2 == 1 else (0, 12)


def strike(g, tick, root, big):
    """The blow that ends a silence (or opens a block): the heart as a hammer, the pedal, a snare, the clock's tock."""
    g.drum("heart", tick, BASEDRUM, 100, over=True)
    g.put("bass", tick, BASS, root, 100, over=True)
    g.put("drone", tick, DIDGERIDOO, root, 92, over=True)
    g.drum("snare", tick, SNARE, 80 if big else 56, over=True)
    g.put("tock", tick, COW_BELL, 84 + (root - 35), 56 if big else 40, over=True)


def onsets(start, end, spacings, first_skip=0):
    """Absolute ticks start, start + spacings[0], ... below ``end`` with the index in the cycle."""
    result = []
    t, n = start, 0
    while t < end:
        result.append((t, n % len(spacings)))
        t += spacings[n % len(spacings)]
        n += 1
    return result


def hit(g, tick, note, ins_note, clank, big=False):
    """An ensemble hit (6-7 notes at one tick): the heart, the bass, the pedal, a snare, the anvil (cow bell), a low iron-xylophone clank, the hat."""
    g.drum("pound", tick, BASEDRUM, 100, over=True)
    g.put("bass", tick, BASS, note, 98, over=True)
    g.put("drone", tick, DIDGERIDOO, note, 92, over=True)
    g.drum("snare", tick, SNARE, 90 if big else 78, over=True)
    g.put("tock", tick, COW_BELL, ins_note, 80, over=True)
    g.put("clank", tick, IRON_XYLOPHONE, clank, 96, over=True)
    g.drum("clock", tick, HAT, 60, over=True)


def pads(g, s, chord, sh, v0, v1, harp, choir):
    """Organ-like long chord for one bar: the four voices are struck one after the other (an arpeggio that never stops: every voice every 4 ticks), so the layer holds a single note a tick."""
    root, tones = CH[chord]
    for t in range(BAR):
        v = v0 + (v1 - v0) * t / (BAR - 1)
        g.put("pad", s + t, PLING, tones[t % 4] + sh, v)
        if harp:
            g.put("harp", s + t, HARP, tones[(t + 2) % 4] + sh, v * 0.8)
        if choir and t % 4 == 0:
            g.put("choir", s + t, FLUTE, tones[2] + 24 + sh, 66 + (v - v0) * 0.4)


def compose(phase):
    ph = PHASES[phase]
    sh = ph["shift"]
    root = 35 + sh
    g = Grid(TPS, BAR, BARS, SPECS)
    gun_rng = Rng(77 + phase)
    # ---- the intro: 4 silent ticks, then the clock wakes, the heart starts, the pedal rises, the bass only as soft quarter roots (ticks 4 .. 63)
    g.drum("clock", g.start(-3), HAT, 30)
    g.drum("clock", g.start(-3) + 10, HAT, 24)
    for t in range(0, BAR, 4):
        g.drum("clock", g.start(-2) + t, HAT, 18 + 2 * t // 4)
    for t in (2, 12):
        g.drum("heart", g.start(-2) + t, BASEDRUM, 88)
        g.drum("heart", g.start(-2) + t + 4, BASEDRUM, 66)
    for t in range(0, BAR, 4):
        g.drum("clock", g.start(-1) + t, HAT, 24 + t // 2)
    for t in (0, 8, 16):
        g.drum("heart", g.start(-1) + t, BASEDRUM, 82 + t // 2)
        g.drum("heart", g.start(-1) + t + 3, BASEDRUM, 64 + t // 2)
    g.restrike("drone", DIDGERIDOO, root, g.start(-1), BAR, 4, 50, 86)
    for bar, base in ((-2, 74), (-1, 80)):
        for t in range(0, BAR, 4):
            g.put("bass", g.start(bar) + t, BASS, root, base + 2 * t // 4 + (10 if t == 0 else 0))
    for t, p in ((8, 59), (12, 59), (14, 60)):
        g.put("ost", g.start(-1) + t, IRON_XYLOPHONE, p + sh, 74 + t)
    g.clicks("noise", HAT, 66, 63, [1, 1, 1, 2, 3, 4], 16, 60)
    g.clicks("roll", SNARE, 66, 63, [1, 1, 1, 1, 2, 2, 2, 3, 3, 4], 40, 96)
    # ---- the heart: the bass drum on a cycle of its own
    for name, first, count in SECTIONS:
        period = HEART_PERIOD[phase][name]
        lub, dub = HEART_VEL[name]
        dub_off = max(1, int(round(period * 0.375)))
        end_tick = g.start(first + count)
        beat = 0
        tick = g.start(first)
        skip = {0: 7, 1: 9}[phase] if name in ("P2", "P3") else 0
        while tick < end_tick:
            if not (skip and beat % skip == skip - 1):
                g.drum("heart", tick, BASEDRUM, lub, over=True)
                g.drum("heart", tick + dub_off, BASEDRUM, dub, over=True)
            tick += period + (beat // 4 if name == "P3" else 0)       # in P3 the heart slows down
            beat += 1
    # ---- P1: the irregular bass and hat (a cycle of 17 ticks against the bar of 20)
    p1_start, p1_end = g.start(0), g.start(8)
    for t, n in onsets(p1_start, p1_end, IRREGULAR):
        bar = (t - 64) // BAR
        if bar < 2 and n not in (0, 3):
            continue
        g.put("bass", t, BASS, root + CYCLE_PITCH[n], 58 + min(bar, 6) * 2 + (22 if n == 0 else 4 * (n == 3)) + 4 * phase)
    for t, n in onsets(p1_start + 1, p1_end, HAT_CYCLE):
        bar = (t - 64) // BAR
        g.drum("clock", t, HAT, 18 + 3 * min(bar, 6) + (10 if n == 0 else 0) + 4 * phase)
    # ---- P3: bass + anvil and bone staccato (cow bell, a low iron-xylophone clank, ghost snare rim, 2-tick hat bursts) on the cycle of 17
    p3_start, p3_end = g.start(24), g.start(40)
    for t, n in onsets(p3_start, p3_end, IRREGULAR):
        i = (t - p3_start) // BAR
        g.put("tock", t, COW_BELL, (84, 78, 84, 81, 78, 84, 76)[n] + sh, 72 + (14 if n == 0 else 0) + i + 4 * phase)
        if n == 0:
            g.put("clank", t, IRON_XYLOPHONE, 54 + sh, 96)
            g.put("clank", t + 6, IRON_XYLOPHONE, 55 + sh, 78)
        if n in (2, 5) and i >= 2:
            g.drum("bone", t, HAT, 50 + 2 * i)
            g.drum("bone", t + 1, HAT, 40 + 2 * i, over=True)
    for t, n in onsets(p3_start + 2, p3_end, [4, 3, 3, 2, 5]):
        i = (t - p3_start) // BAR
        if i >= 4:
            g.drum("snare", t, SNARE, 58 + i + 6 * phase)
    noise_rng = Rng(5 + phase)
    for k in range(BARS):
        name, i, count = section_of(SECTIONS, k)
        s = g.start(k)
        acc = accents(k, ph)
        mode, restless = bass_plan(phase, name, i)
        corrode = [0, 1, 2, 1][(k // 2) % 4] + phase
        # the clock: hat ticks in P2 (eighths, sixteenths in phase 2 from the gun on) and in the climax (sixteenths)
        if name in ("P2", "CL"):
            sixteenth = name == "CL" or (phase == 1 and i >= 8)
            quiet, loud = (26, 40) if name == "P2" else (26, 46)
            quiet, loud = quiet + 4 * phase, loud + 4 * phase
            for t in range(0, BAR, 1 if sixteenth else 2):
                if name == "P2" and i % 4 == 3 and t >= 16 and not sixteenth:
                    continue
                g.drum("clock", s + t, HAT, loud if t in acc else ((quiet if t % 2 == 0 else quiet * 0.6) if t % 4 else quiet + 4))
        # tick and tock of the cow bell: the pitch turns over every beat, and with 5 beats the bar begins on the other one every time
        if name in ("P2", "CL"):
            for b, t in enumerate((0, 4, 8, 12, 16)):
                high = (k * 5 + b) % 2 == 0
                g.put("tock", s + t, COW_BELL, (84 if high else 78) + sh, 58 + 6 * (name == "CL") + 4 * phase + (12 if t in acc else 0))
        if name == "P4":                                          # the toll of the sacred part
            g.put("tock", s, COW_BELL, 78 + sh, 64 + 4 * phase)
            if i % 2 == 1:
                g.put("tock", s + 12, COW_BELL, 84 + sh, 56 + 4 * phase)
        # the pedal: re-struck didgeridoo; in P4 and the climax it follows the root of the chord
        if name in ("P1", "P2", "P4", "CL"):
            chord_root = root
            if name in ("P4", "CL"):
                chord_root = CH[chord_of(name, i)][0] + sh
            base = DRONE_VEL[name]
            for t in range(0, BAR, 4):
                note = chord_root + (6 if (t == 16 and k % 4 == 3 and name in ("P2",)) else 0)
                g.put("drone", s + t, DIDGERIDOO, note, base + (8 if t in acc else 0))
        # the bass: quarters / eighths / eighths with doubles / the sixteenth machine gun; half-time in the sacred part
        if name == "P4":
            chord_root = CH[chord_of(name, i)][0] + sh
            for t in (0, 12 if acc[1] == 12 else 8):
                g.put("bass", s + t, BASS, chord_root, 78 + 4 * phase + (8 if t == 0 else 0))
        elif name in ("P2", "P3", "CL"):
            chord_root = CH[chord_of(name, i)][0] + sh if name == "CL" else None
            for t in gun_ticks(mode, BAR, k, restless, gun_rng):
                b = t // 4
                if name == "CL":
                    note = chord_root + (0, 0, 12, 0, 7)[b]
                    if note > 54:
                        note -= 12
                elif name == "P3":
                    note = root + WALK[(k * 5 + b) % 12] + min(4, i // 3)
                else:
                    note = root + SMALL[(k * 5 + b) % 5]
                g.put("bass", s + t, BASS, note, bass_velocity(name, phase, i, t, acc, mode))
        # the pound and the snare: P2 - the snare on EVERY beat (the pseudo double tempo), the pound on the group starts; the climax doubles them
        if name in ("P2", "CL"):
            for t in acc:
                g.drum("pound", s + t, BASEDRUM, 90 + 6 * phase + (4 if t == 0 else 0))
            if name == "CL":
                for t in acc:
                    g.drum("pound", s + t + 2, BASEDRUM, 78 + 8 * phase)
            if name == "CL" or i >= 2:
                for b, t in enumerate((0, 4, 8, 12, 16)):
                    if t == 0 and name == "P2":
                        continue
                    g.drum("snare", s + t, SNARE, (66 if name == "P2" else 82) + 6 * phase + (14 if t in (acc[0] + 4, acc[1] + 4) else 0))
        # an ensemble hit on every group start (6-7 notes at one tick) from the second bar of P1; the sacred part has it on the bar line only
        if name == "P4":
            hit(g, s, CH[chord_of(name, i)][0] + sh, 78 + sh, 54 + sh, i % 4 == 0)
            g.drum("pound", s + acc[1], BASEDRUM, 88)                       # half-time: the kick on 1 and on the second group
        elif name != "P1" or i >= 1:
            for t in acc:
                hit(g, s + t, root + (0 if name != "CL" else CH[chord_of(name, i)][0] - 35), 84 + sh, 54 + sh, t == 0 and i % 4 == 0)
        # the cell: dark and eroding in P1 / P2, major in the climax
        if name in ("P1", "P2"):
            r = ERODE_P1[phase][i] if name == "P1" else ERODE_P2[phase][i]
            if r < 8:
                vel = (84 if name == "P1" else 86) + 4 * phase
                for t, p, rank in CELL:
                    if rank < r:
                        continue
                    pitch = p - (1 if rank < r + corrode else 0)
                    g.put("ost", s + t, IRON_XYLOPHONE, pitch + sh, vel + (10 if t in acc else 0))
                    if name == "P2" and (rank % 2 == 0 or phase == 1):
                        g.put("ost2", s + t + 1, COW_BELL, pitch + 12 + sh, vel * 0.5)
            if name == "P2":
                for index in range(min(GAIN_P2[i], 4)):
                    t, p = GAIN[index]
                    g.put("ost", s + t, IRON_XYLOPHONE, p + sh, 78 + 4 * phase)
            # hat noise between the ticks
            if name == "P2" and phase == 1 and not (i >= 8):
                odd = list(range(1, BAR, 2))
                for _ in range(3):
                    t = odd.pop(noise_rng.below(len(odd)))
                    g.drum("noise", s + t, HAT, 14 + noise_rng.below(14) + 4 * phase)
        if name == "CL":
            chord = chord_of(name, i)
            off = CELL_OFFSET[chord]
            for t, p in CELL_MAJOR:
                g.put("ost", s + t, IRON_XYLOPHONE, p + off + sh, 82 + 2 * phase + (10 if t in acc else 0))
        # the sacred chords (P4) and their fusion with the machine (climax); the mode has flipped: B major
        if name == "P4":
            pads(g, s, chord_of(name, i), sh, 76 + 2 * (i % 4), 86 + 2 * (i % 4) + 4 * phase, True, True)
        elif name == "CL":
            pads(g, s, chord_of(name, i), sh, 84 + 3 * phase, 94 + 3 * phase, False, True)
            top = TOP[chord_of(name, i)] + sh + (7 if (i >= 8 and i % 4 == 3) else 0)      # the last bar of each late line climbs a fifth and hangs
            for t in range(0, BAR, 2):
                n = t // 2
                p = top if n % 5 != 4 else top - 12
                g.put("ham", s + t, BIT, p, 88 + (10 if t in acc else 0) + 2 * phase)
                if phase == 1 and i >= 8 and t % 4 == 2:
                    g.put("ham", s + t + 1, BIT, min(78, top) - 3, 74)
    for tick in list(g.layers["bass"].notes):                     # where the bass strikes in the gun, the pedal leaves it the low note (it stays on the group starts)
        if tick < 64:
            continue
        k = (tick - 64) // BAR
        name, i, count = section_of(SECTIONS, k)
        if name in ("P1", "P3") or (name in ("P2", "CL") and (tick - g.start(k)) not in accents(k, ph)):
            g.layers["drone"].notes.pop(tick, None)
    # ---- the held dissonance (P2: the cell slowed down to long notes; before the stop a swelling cluster)
    for bar, v_from, v_to, index in ((12, 86, 74, 0), (16, 92, 80, 1)):
        voices = [("hold1", BIT, 0, 0, 1.0), ("hold2", PLING, 1, 2, 0.85)]
        held_line(g, voices, [(p + sh, t) for p, t in zip(*[LINES[index][0], LINES[index][1]])], g.start(bar), v_from + 4 * phase, v_to + 4 * phase)
    for layer, p, begin in (("cl1", 71, 21), ("cl2", 72, 22), ("cl3", 77, 23)):
        length = (24 - begin) * BAR - 8
        g.restrike(layer, BIT, p + sh, g.start(begin), length, 2, 40, 80 + 6 * (begin - 21))
    # ---- rolls before a block
    for bar in (7, 23, 39):
        g.clicks("roll", SNARE, 66, g.start(bar) + 19, [1, 1, 1, 1, 1, 2, 2, 2, 3, 3], 40, 100)
    # ---- the silences of the scythe
    for bar, from_tick in SILENCES[phase]:
        g.mute(g.start(bar) + from_tick, g.start(bar + 1))
        strike(g, g.start(bar + 1), root, bar + 1 in (24, 40))
    # ---- blows at the start of each block
    for first, big in ((0, False), (8, False), (24, False), (40, True)):
        strike(g, g.start(first), root, big)
    for layer, p in (("cl1", 59), ("cl2", 60), ("cl3", 65)):
        g.put(layer, g.start(8), BIT, p + sh, 96, over=True)
    # ---- the break before the climax: one bar, every layer muted, one high bell
    g.mute(g.start(BREAK_BAR), g.start(BREAK_BAR + 1))
    g.put("bell", g.start(BREAK_BAR) + 2, BELL, 96, 44)
    strike(g, g.start(56), root, True)
    for layer in ("pad", "harp", "choir"):
        g.layers[layer].notes.pop(g.start(56), None)
    g.clicks("noise", HAT, 66, g.length - 1, [1, 1, 1, 2, 2, 3, 4], 20, 72, over=True)
    return g


TITLES = ("Silence of the Scythe (Boss005)", "Silence of the Scythe, Unleashed (Boss005)")
FILES = ("reaper_phase1.nbs", "reaper_phase2.nbs")
DESCRIPTIONS = (
    "Reaper fight, phase 1, class 3 (style 2, 130 BPM, 5/4): an irregular clockwork, a pseudo double tempo, the melody gone (bass and anvil / bone staccato), the mode flip to organ-like B major chords in half-time, a one-bell break, then the sixteenth machine gun fused with the chords and hammered high notes.",
    "Reaper fight, phase 2: a half step up, the heart twice as fast, the 5 turns 3+2 / 2+3, the machine gun earlier, fuller pads and hammered line. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, 64 + first * BAR, 64 + (first + count) * BAR) for name, first, count in SECTIONS]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks, bass_layers=("bass",), keep_layers=("pad", "choir", "ham", "hold1"))


if __name__ == "__main__":
    main()
