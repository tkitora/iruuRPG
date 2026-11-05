#!/usr/bin/env python3
"""Makes circuit_phase1.nbs and circuit_phase2.nbs: "Holy Circuit (Boss010)" and "Holy Circuit, Overclocked (Boss010)", a holy machine that has run away, in two phases.

    python3 tools/bgm/make_circuit_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_circuit_bgm.py outdir     writes them somewhere else

CLASS 3, "inorganic fear and erosion", the machine face of STYLE 2 of the structure guide (t277: "despair and sacred, many stages", 120-130 BPM) with the subdivision guide (t276). Not the cold clock of the
reaper (Boss005: a 5/4 clockwork with an irregular cycle of 17 ticks, a slow heart on its own cycle, a plain BASS gun, a pling arpeggio pad) but a runaway AI: a steady machine loop that does not fit its bar,
factory metal, glitches, and a prayer that the machine keeps overwriting - the title is "Holy Circuit", so the sacred part is a real chant.
130 BPM = 8.667 ticks a second, the song tick is a sixteenth, a beat of 4 ticks, the bar is 7/4 = 28 ticks grouped 3+2+2 / 2+2+3 / 2+3+2 beats (ticks 0 12 20 | 0 8 16 | 0 8 20). 52 bars of the loop =
1456 ticks, with the intro 175.4 s. A 28-tick bar meets the engine's 16-tick line every 4 bars, so every section is a multiple of 4 bars. The loop (tick 64) is a bar line: the intro is the tail of a bar (8
ticks) and two whole bars.
TIME TABLE (the style table scaled from 3:30 to 2:55; bars of 3.23 s):
  P1  0:00-0:20 (intro + 4 bars)   a machine loop that does not fit: the gun (didgeridoo, D) strikes every 3 ticks, the hat every 7, against the bar of 28 (a pulse that drifts across the bar line); the sequencer of
                                   7 steps (D F A G# A F E) once a beat; glitch pings; a heart that fails.
  P2  0:20-0:59 (12 bars)          PSEUDO DOUBLE TEMPO: the snare on EVERY beat, a footstep kick on the groups, hat in eighths, the sequencer in eighths; the gun eighths -> eighths + sixteenth doubles (zu-ku) and 3-tick runs,
                                   swaying (D Eb G A Bb); the chant (D Eb G A Bb) comes up out of the noise and is broken; the stop in the last bar.
  P3  0:59-1:38 (12 bars)          THE MELODY IS GONE: the gun (quarters -> eighths -> doubles, swaying) and staccato factory metal on a steady 7-step pattern - cow bell, a low iron-xylophone clank, ghost snare rim, 2-tick hat
                                   bursts - plus glitches; no sequencer, no chant.
  P4  1:38-2:17 (12 bars)          SACRED, the MODE FLIPS: D minor-ish (D Eb G A# / the tritone G#) turns into D MAJOR (D F# A B): organ-like block chords of pling and harp (D | Bm | G | A ...), a flute choir above,
                                   and the chant in long notes in D major pentatonic (D E F# A B), sung by the 8-bit chip and an iron xylophone; the bass in half-time, a slow heart, a toll.
  BREAK  (bar 39, 2:11)            one bar with every layer muted and a single high bell note.
  CL  2:17-2:55 (12 bars)          CLIMAX: the P2 rhythm (kick and snare on every beat, the sixteenth gun on the chord roots swaying through chord tones, hat in sixteenths) FUSED with the chords; the chant's long notes
                                   are HAMMERED (re-struck every 2 ticks) in the second line; the last line is broken once more by the machine and ends on the dominant A, leading back into the dark D of the loop.
Bass ladder: quarters (intro) -> a 3-tick pulse (P1) -> eighths -> eighths + doubles (P2) -> quarters -> eighths -> doubles (P3) -> half-time (P4) -> sixteenth gun (CL); phase 2 brings the gun earlier.
Phase 2 (Overclocked): a half step up (E-flat), the heart on every group, the groups change every bar, a second shuffled note after each sequencer note, the gun earlier (P2 and P3), more glitches, a third pad voice,
the chant cut sooner in the dark parts, hammered more. Everything is written by what SOUNDS. Nothing is random (cat3_machine.Rng). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat3_machine import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS, BAR, BARS = 130 / 15, 28, 52
SECTIONS = [("P1", 0, 4), ("P2", 4, 12), ("P3", 16, 12), ("P4", 28, 12), ("CL", 40, 12)]
BREAK_BAR = 39
SPECS = {  # layer: (volume, pan)
    "kick": (100, 100), "bass": (100, 100), "gun": (100, 100), "snare": (100, 105), "roll": (100, 105), "hat": (100, 120), "bone": (100, 130),
    "bell": (100, 70), "arp": (100, 95), "clank": (100, 85), "hymn": (100, 110), "hym2": (100, 90), "glitch": (100, 130),
    "padA": (100, 95), "padB": (100, 105), "padC": (100, 115), "choir": (100, 100), "chime": (100, 100),
}
PHASES = [dict(shift=0), dict(shift=1)]
X = [0, 12, 20]     # 3+2+2 beats
Y = [0, 8, 16]      # 2+2+3
Z = [0, 8, 20]      # 2+3+2
ARP = [62, 65, 69, 68, 69, 65, 64]                       # D F A G# A F E, seven steps (dark)
ARP_MAJOR = [62, 66, 69, 71, 69, 66, 64]                 # D F# A B A F# E (the flip)
# the dark chants (D Eb G A Bb) and the chants of the sacred D major (D E F# A B), four bars of long notes: (pitch, ticks)
CHANTS = [
    [(62, 16), (63, 8), (62, 8), (58, 16), (57, 16), (55, 8), (57, 8), (62, 16), (63, 8), (62, 8)],
    [(62, 16), (63, 8), (65, 8), (63, 16), (62, 16), (58, 8), (57, 8), (62, 16), (65, 8), (63, 8)],
]
MAJOR_CHANTS = [
    [(62, 16), (64, 8), (62, 8), (59, 16), (57, 16), (54, 8), (57, 8), (62, 16), (64, 8), (62, 8)],
    [(62, 16), (64, 8), (66, 8), (64, 16), (62, 16), (59, 8), (57, 8), (62, 16), (66, 8), (69, 8)],
    [(62, 16), (64, 8), (62, 8), (59, 16), (57, 16), (54, 8), (57, 8), (62, 16), (64, 8), (62, 8)],
    [(74, 16), (76, 8), (74, 8), (71, 16), (69, 16), (66, 8), (69, 8), (76, 16), (78, 16)],
]
# (first bar of the 4-bar chant, kind, which line, loudness, how it is broken / hammered)
CHANT_PLAN = {
    0: [(8, "dark", 0, 78, dict(cut=3)), (12, "dark", 1, 84, dict(cut=7)),
        (28, "major", 0, 86, dict()), (32, "major", 1, 88, dict()), (36, "major", 2, 86, dict()),
        (40, "major", 1, 90, dict()), (44, "major", 3, 92, dict(hammer=True)), (48, "major", 0, 90, dict(cut=8))],
    1: [(8, "dark", 0, 80, dict(cut=3)), (12, "dark", 1, 86, dict(cut=6)),
        (28, "major", 0, 88, dict()), (32, "major", 1, 90, dict()), (36, "major", 2, 88, dict()),
        (40, "major", 1, 92, dict(hammer=True)), (44, "major", 3, 92, dict(hammer=True)), (48, "major", 0, 92, dict(cut=7))],
}
# (bar, tick of the bar, ticks of nothing): every layer is cut, in the dark parts
DROPS = {
    0: [(2, 20, 2), (5, 18, 2), (7, 20, 3), (9, 12, 2), (11, 22, 3), (13, 16, 3), (14, 6, 2), (17, 14, 2), (19, 20, 3), (21, 6, 3), (23, 22, 2), (26, 10, 3)],
    1: [(2, 20, 2), (5, 14, 2), (6, 10, 2), (8, 20, 2), (10, 6, 2), (12, 22, 2), (13, 12, 2), (15, 18, 3), (17, 8, 2), (18, 20, 2), (20, 12, 2), (22, 6, 2), (24, 14, 2), (26, 20, 2)],
}
FAILS = {0: {"P1": ["n", "miss", "n", "late"], "P2": ["n", "n", "late", "n", "miss", "n", "flutter", "half", "n", "late", "n", "miss"],
             "P3": ["half", "n", "miss", "half", "flutter", "half", "miss", "flat", "half", "n", "miss", "flutter"], "P4": ["n"] * 12, "CL": ["n"] * 12},
         1: {"P1": ["n", "late", "miss", "n"], "P2": ["n", "late", "n", "miss", "flutter", "n", "half", "flutter", "n", "late", "flutter", "miss"],
             "P3": ["half", "flutter", "miss", "half", "flutter", "half", "flutter", "flat", "half", "flutter", "miss", "flutter"], "P4": ["n"] * 12, "CL": ["n"] * 12}}
# the chords of the sacred part (bass root, four voices): D major and its neighbours
CHORDS = {"D": (38, [62, 66, 69, 74]), "Bm": (35, [59, 62, 66, 71]), "G": (31, [59, 62, 67, 71]), "A": (33, [57, 61, 64, 69])}
P4_PROG = [["D", "Bm", "G", "A"], ["D", "G", "A", "D"], ["Bm", "G", "A", "A"]]
CL_PROG = [["D", "Bm", "G", "A"], ["G", "D", "A", "Bm"], ["D", "G", "A", "A"]]
SLOW = [0, 0, 0, 1]
A2_PATTERN = [0, 0, 1, 0, 5, 1, 0]
SWAY = [0, 0, 1, 0, 5, 1, 0, 7, 5, 1, 0, 0, 1, 5, 8, 5]
SWAY_CHORD = [0, 0, 7, 4, 0, 2, 0, 7, 12, 7, 4, 2]
BASS_VEL = {"P1": 76, "P2": 80, "P3": 86, "CL": 90}
MUTED = ("P4",)


def groups(k, phase, name):
    if phase == 0:
        if name == "P1":
            return X
        if name == "P2":
            return X if k % 2 == 0 else Y
        if name == "P3":
            return Y
        if name == "CL":
            return X if k % 2 == 0 else Y
        return X
    if name == "P1":
        return X
    if name == "P2":
        return [X, Y, X, Z][k % 4]
    if name == "P3":
        return [Y, Z, Y, X][k % 4]
    if name == "CL":
        return [X, Y, Z, Y][k % 4]
    return X


def chord_of(name, i):
    prog = P4_PROG if name == "P4" else CL_PROG
    return prog[i // 4][i % 4]


# ---- the bass: (mode, restlessness) per bar, see cat3_machine.gun_ticks (n none, q quarters, e eighths, m eighths + sixteenth doubles + 3-tick runs, s sixteenth machine gun, h half-time)
def bass_plan(phase, name, i):
    if phase == 0:
        if name == "P2":
            return ("e", 0) if i < 4 else (("m", 0.4 + 0.1 * (i - 4)) if i < 9 else ("m", 1.0))
        if name == "P3":
            if i < 2:
                return ("q", 0)
            return ("e", 0) if i < 5 else (("m", 0.5 + 0.1 * (i - 5)) if i < 9 else (("m", 1.0) if i < 11 else ("s", 1)))
    else:
        if name == "P2":
            return ("m", 0.6) if i < 2 else (("m", 1.0) if i % 4 == 3 else ("s", 1))
        if name == "P3":
            if i < 2:
                return ("e", 0)
            return ("m", 1.0) if i < 6 else (("s", 1) if i % 4 != 3 else ("m", 1.0))
    return ("h", 0) if name == "P4" else ("s", 1)


def bass_offset(name, mode, k, b, i, chord_root, root):
    n = k * 7 + b
    if name == "P2":
        return A2_PATTERN[b] if mode in ("m", "s") else SLOW[(b + k) % 4]
    if name == "P3":
        return SWAY[n % 16] + min(4, i // 3)
    return 0


def bass_velocity(name, mode, phase, i, t, starts):
    v = BASS_VEL[name] + 4 * phase
    if name == "P2":
        v += int(i * 0.8)
    elif name == "P3":
        v += i
    if mode == "q":
        v -= 12
    if t in starts:
        v += 16
    elif t % 4 == 0:
        v += 4
    elif t % 2 == 1:
        v -= 12 if mode != "s" else 8
    return max(1, min(100, v))


def heart_bar(g, s, starts, phase, fail):
    """The bass drum as a heart: lub-dub on the bar line and on the last group (phase 2: on every group); the failures cut and bend it."""
    beats = [0, starts[2]] if phase == 0 else list(starts)
    dub_off = 3 if phase == 0 else 2
    for index, t in enumerate(beats):
        first = t == 0
        lub = 100 if first else 88
        if fail == "flat" or (fail == "half" and index >= 1):
            continue
        if fail == "flutter" and index >= 1 and (phase == 1 or index == len(beats) - 1):
            g.stutter("kick", BASEDRUM, 66, s + t, 5, 96, decay=0.78)
            continue
        shift = 2 if (fail == "late" and index >= 1) else 0
        g.drum("kick", s + t + shift, BASEDRUM, lub)
        if fail != "miss":
            g.drum("kick", s + t + shift + dub_off, BASEDRUM, lub * 0.8)
        if first:
            g.put("bass", s + t, BASS, 38 + PHASES[phase]["shift"], 94)


def chant(g, bar, kind, line, phase, vel, cut=99, hammer=False):
    """The prayer in long notes (the 8-bit chip re-struck every 4 ticks, an iron xylophone two ticks later; hammered: every 2 ticks, the xylophone a tick later), broken: returns the tick where a stuck
    note ends (0 when the line is not cut)."""
    sh = PHASES[phase]["shift"]
    s = g.start(bar)
    vel = vel + 4 * phase
    lines = CHANTS if kind == "dark" else MAJOR_CHANTS
    line = lines[line]
    if kind == "dark" and phase == 1 and cut < len(line):           # overclocked: the dark chant is cut sooner and stutters more
        cut = max(2, cut * 3 // 4)
    step = 2 if hammer else 4
    t = 0
    end = 0
    for index, (pitch, length) in enumerate(line):
        if index >= cut:
            break
        note = min(78, pitch + sh)
        level = vel + 8
        if index == cut - 1:                                      # the stuck note: struck again and again on consecutive ticks, then nothing
            end = g.stutter("hymn", BIT, note, s + t, 7, level, decay=0.85)
            g.stutter("hym2", IRON_XYLOPHONE, note, s + t, 7, level * 0.85, decay=0.85)
        else:
            for o in range(0, length, step):
                v = vel + (6 if o == 0 else 0) - 6 * o / max(1, length - 1)
                g.put("hymn", s + t + o, BIT, note, v)
                if bar >= 28 and o + step // 2 < length:
                    g.put("hym2", s + t + o + step // 2, IRON_XYLOPHONE, note, v * 0.88)
        t += length
    return end


def organ(g, s, chord, sh, v0, v1, rolled):
    """A long chord for one bar: three pling / harp voices struck every 4 ticks (block: two together and one two ticks later; rolled: one after the other), a flute choir on the top voice."""
    root, tones = CHORDS[chord]
    offsets = (0, 1, 2) if rolled else (0, 2, 0)
    for t in range(BAR):
        v = v0 + (v1 - v0) * t / (BAR - 1)
        for j, (layer, ins) in enumerate((("padA", PLING), ("padB", HARP), ("padC", PLING))):
            if t % 4 == offsets[j]:
                g.put(layer, s + t, ins, tones[j] + sh, v * (0.92 if j else 1.0))
        if t % 4 == 0 and (t < 4 or not rolled):
            g.put("choir", s + t, FLUTE, tones[3] + 12 + sh, 68 + (v - v0) * 0.4)


def strike(g, tick, sh, big):
    """The blow that ends a dropout: the heart, the bass, a snare, the alarm bell, the gun."""
    g.drum("kick", tick, BASEDRUM, 100, over=True)
    g.put("bass", tick, BASS, 38 + sh, 100, over=True)
    g.drum("snare", tick, SNARE, 80 if big else 56, over=True)
    g.put("bell", tick, COW_BELL, 74 + sh, 50 if big else 36, over=True)
    g.put("gun", tick, DIDGERIDOO, 38 + sh, 90, over=True)


def hit(g, tick, note, sh, clank=True):
    """An ensemble hit (6-8 notes at one tick): the heart, the sub bass, the gun, a snare, the alarm bell, an iron-xylophone clank, the hat."""
    g.drum("kick", tick, BASEDRUM, 100, over=True)
    g.put("bass", tick, BASS, note, 98, over=True)
    g.put("gun", tick, DIDGERIDOO, note, 94, over=True)
    g.drum("snare", tick, SNARE, 84, over=True)
    g.put("bell", tick, COW_BELL, 80 + sh, 78, over=True)
    if clank:
        g.put("clank", tick, IRON_XYLOPHONE, 54 + sh, 96, over=True)
    g.drum("hat", tick, HAT, 64, over=True)


def compose(phase):
    sh = PHASES[phase]["shift"]
    g = Grid(TPS, BAR, BARS, SPECS)
    rng = Rng(31 + phase)
    gun_rng = Rng(91 + phase)
    root = 38 + sh
    # ---- the intro: the tail of a bar (8 ticks), then two whole bars: a boot that stumbles, the gun only as soft quarter roots
    g.stutter("glitch", BIT, 77 + sh - 1, 3, 2, 84)
    g.stutter("glitch", BIT, 72 + sh, 26, 3, 84)
    g.drum("kick", g.start(-2), BASEDRUM, 92)
    g.drum("kick", g.start(-2) + 3, BASEDRUM, 70)
    g.drum("kick", g.start(-2) + 16, BASEDRUM, 94)
    g.drum("kick", g.start(-2) + 19, BASEDRUM, 72)
    for bar, base in ((-2, 74), (-1, 82)):
        for t in range(0, BAR, 4):
            g.put("gun", g.start(bar) + t, DIDGERIDOO, root, base + t // 2 + (10 if t == 0 else 0))
    for t in range(0, BAR, 4):
        g.drum("hat", g.start(-1) + t, HAT, 14 + t // 2)
    g.drum("kick", g.start(-1), BASEDRUM, 96)
    g.drum("kick", g.start(-1) + 3, BASEDRUM, 74)
    g.drum("kick", g.start(-1) + 12, BASEDRUM, 96)
    g.drum("kick", g.start(-1) + 15, BASEDRUM, 76)
    g.put("hymn", g.start(-1) + 8, BIT, 62 + sh, 84)
    g.put("hymn", g.start(-1) + 12, BIT, 62 + sh, 76)
    g.stutter("hymn", BIT, 65 + sh, g.start(-1) + 16, 6, 86, decay=0.85)
    for index, t in enumerate(ARP):
        if index % 2 == 0 and index >= 3:
            g.put("arp", g.start(-1) + 12 + 2 * index, IRON_XYLOPHONE, t + sh, 76 + 3 * index)
    g.clicks("roll", HAT, 66, 63, [1, 1, 1, 1, 2, 2, 3, 4, 5], 14, 62)
    g.clicks("roll", SNARE, 66, 63, [1, 1, 1, 1, 2, 2, 2, 3, 3, 4], 40, 96)
    # ---- P1: the machine loop that does not fit the bar: the gun every 3 ticks, the hat every 7
    for t in range(g.start(0), g.start(4), 3):
        n = (t - 64) // 3
        g.put("gun", t, DIDGERIDOO, root + (0, 0, 1, 0, 0, 5)[n % 6], 78 + (12 if n % 7 == 0 else 0) + 2 * ((t - 64) // BAR) + 4 * phase)
    for t in range(g.start(0) + 1, g.start(4), 7):
        g.drum("hat", t, HAT, 22 + 3 * ((t - 64) // BAR) + 4 * phase)
    # ---- P3: staccato factory metal on a steady 7-step pattern: cow bell, clank, ghost snare rim, 2-tick hat bursts
    for k in range(16, 28):
        i = k - 16
        s = g.start(k)
        for b in range(7):
            g.put("bell", s + 4 * b, COW_BELL, (84, 78, 84, 81, 78, 84, 76)[b] + sh, 74 + i + 4 * phase + (10 if b in (0, 3, 5) else 0))
        for t in (0, 12, 20) if k % 2 == 0 else (0, 8, 16):
            g.put("clank", s + t, IRON_XYLOPHONE, 54 + sh, 96)
            g.put("clank", s + t + 6, IRON_XYLOPHONE, 55 + sh, 78)
        if i >= 3:
            for b in range(7):
                g.drum("snare", s + 4 * b + 2, SNARE, 56 + i + 5 * phase)
        if i >= 2:
            for b in (2, 6):
                g.drum("bone", s + 4 * b, HAT, 50 + 2 * i)
                g.drum("bone", s + 4 * b + 1, HAT, 40 + 2 * i)
    # ---- the bars
    for k in range(BARS):
        name, i, count = section_of(SECTIONS, k)
        s = g.start(k)
        starts = groups(k, phase, name)
        fail = FAILS[phase][name][i]
        if name != "CL":
            heart_bar(g, s, starts, phase, fail) if name != "P4" else None
        mode, restless = bass_plan(phase, name, i)
        chord = chord_of(name, i) if name in ("P4", "CL") else None
        chord_root = CHORDS[chord][0] + sh if chord else root
        # the kick: footstep on the groups with a zu-ku pair (P2); on every beat in the climax; a slow heart in the sacred part
        if name in ("P1", "P3") and (name == "P3" or i >= 1):                  # the footstep kick on the groups, with a zu-ku pair
            for t in starts:
                g.drum("kick", s + t, BASEDRUM, 92)
                g.drum("kick", s + t + 1, BASEDRUM, 74)
        if name == "P2" and i >= 2:
            for t in (starts[1], starts[2]):
                if fail != "flat" and not (phase == 1 and fail != "n"):
                    g.drum("kick", s + t, BASEDRUM, 92)
                    if i >= 6:
                        g.drum("kick", s + t + 1, BASEDRUM, 76)
        if name == "CL":
            for b in range(7):
                t = 4 * b
                g.drum("kick", s + t, BASEDRUM, 100 if t in starts else 88)
                if t in starts:
                    g.drum("kick", s + t + 2, BASEDRUM, 76 + 8 * phase)
        if name == "P4":
            g.drum("kick", s, BASEDRUM, 94 + 4 * phase)
            g.drum("kick", s + 3, BASEDRUM, 76)
            if phase == 1:
                g.drum("kick", s + starts[2], BASEDRUM, 90)
                g.drum("kick", s + starts[2] + 2, BASEDRUM, 70)
            g.put("bell", s, COW_BELL, 78 + sh, 34 + 4 * phase)
            if i % 2 == 1:
                g.put("bell", s + starts[2], COW_BELL, 84 + sh, 26 + 4 * phase)
        # the gun: the didgeridoo strikes quarters / eighths / eighths with doubles / the sixteenth machine gun, swaying; half-time in the sacred part; the sub bass on the group starts
        if name == "P4":
            for t in (0, starts[2]):
                g.put("gun", s + t, DIDGERIDOO, chord_root, 84 + 4 * phase)
                g.put("bass", s + t, BASS, chord_root, 92 + 4 * phase + (4 if t == 0 else 0))
            for t in range(4, BAR, 4):
                g.put("gun", s + t, DIDGERIDOO, chord_root, 72 + 4 * phase)
        elif name in ("P2", "P3", "CL"):
            for t in gun_ticks(mode, BAR, k, restless, gun_rng):
                b = t // 4
                if name == "CL":
                    note = chord_root + SWAY_CHORD[(k * 7 + b) % 12]
                    if note > 54:
                        note -= 12
                else:
                    note = root + bass_offset(name, mode, k, b, i, chord_root, root)
                g.put("gun", s + t, DIDGERIDOO, note, bass_velocity(name, mode, phase, i, t, starts))
            if name in ("P2", "CL") or mode in ("m", "s"):
                for t in starts:
                    note = chord_root if name == "CL" else root + bass_offset(name, mode, k, t // 4, i, chord_root, root)
                    g.put("bass", s + t, BASS, note, 96)
        # an ensemble hit on every group start (the sacred part: on the bar line), from the second bar of P1
        if name == "P4":
            hit(g, s, chord_root, sh)
            hit(g, s + starts[2], chord_root, sh, False)
        elif name != "P1" or i >= 1:
            for t in starts:
                hit(g, s + t, chord_root if name == "CL" else root, sh, name != "CL" or t == 0)
            if name in ("P2", "P3") and i >= 4:                              # the second half of a block: a hit on every beat
                for t in range(4, BAR, 4):
                    if t not in starts:
                        hit(g, s + t, root, sh, False)
        # the snare: P2 - on EVERY beat (the pseudo double tempo); the climax louder
        if name in ("P2", "CL"):
            if name == "CL" or i >= 1:
                for b in range(7):
                    t = 4 * b
                    g.drum("snare", s + t, SNARE, (66 if name == "P2" else 84) + 6 * phase + (14 if t in starts and t else 0) + (i if name == "P2" else 0))
        # hats: P2 eighths, the climax sixteenths (phase 2: P2 sixteenths from the gun on)
        sixteenth = name == "CL" or (phase == 1 and name == "P2" and i >= 6)
        if name in ("P2", "CL"):
            for t in range(0, BAR, 1 if sixteenth else 2):
                if name == "P2" and rng.chance(5):
                    continue
                g.drum("hat", s + t, HAT, (36 + 8 * (t in starts) + 4 * phase) if t % 4 == 0 else ((24 + 4 * phase) if t % 2 == 0 else 14 + 2 * phase))
        # the alarm bell on the group starts
        if name in ("P2", "CL") and (name == "CL" or i >= 4):
            for t in starts:
                g.put("bell", s + t, COW_BELL, (74 if t == 0 else 80) + sh, 34 + (10 if name == "CL" else 0) + 4 * phase)
        if name == "CL":
            for t in (starts[0], starts[1]):
                g.put("clank", s + t, IRON_XYLOPHONE, 54 + sh, 96)
        # the sequencer: seven steps of iron xylophone (a step a beat in P1; eighths in P2; a beat in the major climax), dropped and stuttered
        step = {"P1": 4, "P2": 2, "CL": 4}.get(name)
        glitch_chance = {"P1": 6, "P2": 12, "P3": 10, "CL": 6}.get(name, 0) + 6 * phase
        if step:
            table = ARP_MAJOR if name == "CL" else ARP
            for n in range(BAR // step):
                t = s + step * n
                if name != "CL" and rng.chance({"P1": 12, "P2": 10}[name]):
                    continue
                pitch = table[(n + (3 if k % 2 else 0)) % 7]
                vel = {"P1": 80, "P2": 84, "CL": 82}[name] + (10 if n % 7 in (0, 3, 5) else 0)
                if rng.chance(glitch_chance) and step > 1 and name != "CL":
                    g.stutter("arp", IRON_XYLOPHONE, pitch + sh, t, 3, vel, decay=0.7)
                else:
                    g.put("arp", t, IRON_XYLOPHONE, pitch + sh, vel)
                if phase == 1 and name == "P2":
                    g.put("arp", t + 1, IRON_XYLOPHONE, pitch + sh + 6 if pitch + sh + 6 <= 78 else pitch + sh - 6, vel * 0.55)
        # the sacred chords (the mode has flipped to D major)
        if name == "P4":
            organ(g, s, chord, sh, 78 + 2 * (i % 4), 88 + 2 * (i % 4) + 4 * phase, False)
        elif name == "CL":
            organ(g, s, chord, sh, 86 + 2 * phase, 94 + 2 * phase, True)
        # glitch pings of the 8-bit chip: 1-tick notes struck again and again
        ping_count = {0: {"P1": 1, "P2": 2, "P3": 2 + (i >= 6), "P4": 0, "CL": 1 if i % 2 else 0},
                      1: {"P1": 2, "P2": 3, "P3": 4, "P4": 0, "CL": 2 if i % 2 else 1}}[phase][name]
        for _ in range(ping_count):
            t = s + rng.below(BAR)
            g.stutter("glitch", BIT, rng.pick([70, 72, 74, 75, 77]), t, 1 + rng.below(4), 78 + rng.below(16), decay=0.85)
    # ---- the chants
    cuts = []
    for bar, kind, line, vel, params in CHANT_PLAN[phase]:
        end = chant(g, bar, kind, line, phase, vel, **params)
        if end:
            cuts.append((end + 1, 5 if bar >= 28 else 6))
    # ---- rolls before a block, the stop before P3, the stop before the sacred part
    for bar in (3, 15, 27):
        g.clicks("roll", SNARE, 66, g.start(bar) + BAR - 1, [1, 1, 1, 1, 1, 2, 2, 2, 3, 3], 40, 100)
    # ---- the dropouts: every layer is cut, then the blow
    for bar, tick, length in DROPS[phase]:
        a = g.start(bar) + tick
        g.mute(a, a + length)
        strike(g, a + length, sh, False)
    for a, length in cuts:
        if a < g.start(BREAK_BAR):
            g.mute(a, a + length)
            strike(g, a + length, sh, False)
    for bar in (15, 27):
        g.mute(g.start(bar) + 14, g.start(bar + 1))
    # ---- blows at the start of each block
    for first, big in ((0, False), (4, False), (16, False), (28, True), (40, True)):
        strike(g, g.start(first), sh, big)
    # ---- the break before the climax: one bar, every layer muted, one high bell
    g.mute(g.start(BREAK_BAR), g.start(BREAK_BAR + 1))
    g.put("chime", g.start(BREAK_BAR) + 2, BELL, 96, 44)
    strike(g, g.start(40), sh, True)
    g.clicks("roll", HAT, 66, g.length - 1, [1, 1, 1, 1, 2, 2, 3, 4, 5], 40, 100, over=True)
    return g


TITLES = ("Holy Circuit (Boss010)", "Holy Circuit, Overclocked (Boss010)")
FILES = ("circuit_phase1.nbs", "circuit_phase2.nbs")
DESCRIPTIONS = (
    "Boss, phase 1, class 3 (style 2, 130 BPM, 7/4): a machine loop that does not fit its bar, a pseudo double tempo, the melody gone (gun and staccato factory metal), the flip to a chant in D major over organ chords in half-time, a one-bell break, then the sixteenth gun fused with the chords and the hammered chant.",
    "Boss, phase 2: a half step up, the heart on every group, the groups change each bar, a shuffled second note after each sequencer note, the gun earlier, more glitches, hammered chant. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, 64 + first * BAR, 64 + (first + count) * BAR) for name, first, count in SECTIONS]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks, bass_layers=("gun",), keep_layers=("hymn", "hym2", "padA", "padB", "padC", "choir"))


if __name__ == "__main__":
    main()
