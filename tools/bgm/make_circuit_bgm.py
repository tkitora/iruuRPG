#!/usr/bin/env python3
"""Makes circuit_phase1.nbs and circuit_phase2.nbs: "Holy Circuit (Boss010)" and "Holy Circuit, Overclocked (Boss010)" - a holy machine that runs in a ring. REBUILT FROM ZERO (t281).

    python3 tools/bgm/make_circuit_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_circuit_bgm.py outdir     writes them somewhere else

CLASS 3 (inorganic fear and erosion), the machine variant of STYLE 2 of the structure guide, 130 BPM = 8.667 ticks a second, the song tick is a sixteenth, a beat of 4 ticks, the bar is 7/4 = 28 ticks
(3+2+2 / 2+2+3 / 2+3+2 beats), 52 bars = 1520 ticks = 175.4 s, every section a multiple of 4 bars (a 28-tick bar meets the engine's 16-tick line every 4 bars). The loop (tick 64) is a bar line: the intro is
the tail of a bar (8 ticks) and two bars.
THE MOTIF - "the ring": a cell of FIVE notes that loops without caring about the bar of seven beats (5 against 7: it meets the bar line again only after five bars), A C D Eb E (the blues-pentatonic with the
tritone), each lap a step higher (+0 +2 +3 +5, then back). In the sacred part the SAME five-note ring turns to A major pentatonic (A B C# E F#): the mode flip is the ring itself. Key: A (bass A1).
Sounds: PLING sequencer + iron xylophone for the ring, a didgeridoo gun with a BASS sub, a GUITAR chug (machine, industrial), cow bell / clank / hat as factory metal, pling + harp + flute as the sacred organ.
TIME TABLE (bars of 3.23 s; the style table scaled to 2:55):
  P1  0:00-0:20 (intro + 4 bars)   the ring starts alone and loud against the beat of seven; the bass on every beat; stutters and dropouts (the machine boots badly).
  P2  0:20-0:46 (8 bars)           PSEUDO DOUBLE TEMPO: the ring in eighths, the snare on every beat, the kick on every beat, the guitar chug in eighths, the gun eighths -> doubles.
  P3  0:46-1:25 (12 bars)          THE MELODY IS GONE: only the bass (undulating) and staccato factory metal - a 3+2+2 stamp of clank, cow bell pairs, snare rim and hat; no ring.
  P4  1:25-2:04 (12 bars)          SACRED, MODE FLIP to A major: block organ chords (A | E | F#m | D ...), the ring as a chorale of long notes in A major pentatonic, half-time drums; bar 35 is the BREAK (one bell).
  CL  2:04-2:55 (16 bars)          CLIMAX: bars 0-7 the chorale of long notes over the sixteenth gun, kick and snare on every beat, rolled chords; bars 8-11 the bass is RELEASED (kick, snare, chords, chorale only);
                                   bars 12-15 the ring runs away in eighths (major) with the gun in sixteenths and a guitar chug; the last two bars are the dominant E, leading back into the dark A.
Bass ladder: quarters (intro, P1) -> eighths -> doubles (P2) -> quarters -> eighths -> doubles -> gun tease (P3) -> half-time (P4) -> sixteenth gun (CL). Phase 2: a half step up, the groups change every bar, the
gun earlier, the ring in eighths already in P1, a third pad voice, more hits. Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from cat3_machine import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS, BAR, BARS = 130 / 15, 28, 52
SECTIONS = [("P1", 0, 4), ("P2", 4, 8), ("P3", 12, 12), ("P4", 24, 12), ("CL", 36, 16)]
BREAK_BAR = 35
SPECS = {  # layer: (volume, pan)
    "kick": (100, 100), "pound": (100, 100), "snare": (100, 105), "hat": (100, 120), "noise": (100, 130), "roll": (100, 105), "bell": (100, 70),
    "gun": (100, 100), "bass": (100, 100), "gtr": (100, 90), "cell": (100, 95), "cell2": (100, 110), "ham": (100, 105), "clank": (100, 85), "stamp": (100, 125),
    "pad1": (100, 90), "pad2": (100, 105), "pad3": (100, 115), "chime": (100, 100), "glitch": (100, 130),
}
PHASES = [dict(shift=0), dict(shift=1)]
X = [0, 12, 20]     # 3+2+2 beats
Y = [0, 8, 16]      # 2+2+3
Z = [0, 8, 20]      # 2+3+2
RING_DARK = [57, 60, 62, 63, 64]          # A C D Eb E
RING_LIGHT = [57, 59, 61, 64, 66]         # A B C# E F#
LAP = [0, 2, 3, 5]
SWAY = [0, 0, 7, 5, 0, 3, 0, 7, 12, 10, 7, 5]
# the sacred chords (bass root, voices): A, E, F#m, D
CH = {"A": (33, [57, 61, 64, 69]), "E": (40, [56, 59, 64, 68]), "F#m": (30, [57, 61, 66, 69]), "D": (38, [57, 62, 66, 69])}
P4_PROG = [["A", "E", "F#m", "D"], ["F#m", "D", "A", "E"], ["D", "A", "F#m", "E"]]
CL_PROG = [["A", "E", "F#m", "D"], ["D", "A", "E", "F#m"], ["A", "E", "F#m", "D"], ["F#m", "D", "E", "E"]]
DROPS = {0: [(2, 20, 2), (5, 18, 2), (7, 12, 3), (9, 22, 2), (13, 16, 3), (15, 6, 2), (18, 20, 3), (21, 10, 2)],
         1: [(2, 20, 2), (5, 12, 2), (6, 22, 2), (8, 6, 2), (10, 18, 2), (13, 10, 2), (16, 22, 2), (19, 14, 2), (21, 20, 2)]}


def groups(k, phase, name):
    if phase == 0:
        return {"P1": X, "P2": X if k % 2 == 0 else Y, "P3": Y if k % 4 == 3 else X, "P4": X, "CL": X if k % 2 == 0 else Y}[name]
    return {"P1": X, "P2": [X, Y, Z, Y][k % 4], "P3": [Y, Z, X, Z][k % 4], "P4": X, "CL": [X, Y, Z, Y][k % 4]}[name]


def chord_of(name, i):
    return (P4_PROG if name == "P4" else CL_PROG)[i // 4][i % 4]


def ring_pitch(index, light, sh):
    ring = RING_LIGHT if light else RING_DARK
    return ring[index % 5] + LAP[(index // 5) % 4] + sh


def hit(g, tick, note, clank, big=False, full=True):
    """An ensemble hit (7 notes at one tick): kick, sub bass, gun, snare, alarm bell, clank, hat."""
    g.drum("pound", tick, BASEDRUM, 100, over=True)
    g.put("bass", tick, BASS, note, 98, over=True)
    g.put("gun", tick, DIDGERIDOO, note, 94, over=True)
    g.drum("snare", tick, SNARE, 92 if big else 80, over=True)
    g.put("bell", tick, COW_BELL, 80, 78, over=True)
    if clank and full:
        g.put("clank", tick, IRON_XYLOPHONE, 54, 96, over=True)
    g.drum("hat", tick, HAT, 62, over=True)


def pads(g, s, chord, sh, v0, v1, rolled, third):
    root, tones = CH[chord]
    offs = (0, 1, 2) if rolled else (0, 0, 1)          # every voice is struck again every 3 ticks (0.35 s): a held chord, not a chopped one
    for t in range(BAR):
        v = v0 + (v1 - v0) * (t % 14) / 13
        if (s + t - 64) % 3 == offs[0]:
            g.put("pad1", s + t, PLING, tones[0] + sh, v)
        if (s + t - 64) % 3 == offs[1]:
            g.put("pad2", s + t, HARP, tones[2] + sh, v * 0.95)
        if third and (s + t - 64) % 3 == offs[2]:
            g.put("pad3", s + t, PLING, tones[1] + sh, v * 0.95)


def ring(g, start, end, step, light, sh, vel, layer="cell", inst=PLING, every=None, v2=None):
    """The five-note ring from tick ``start`` to ``end``: a note every ``step`` ticks (the index counts from the start of the section, so the ring ignores the bar). ``every``: a long note, struck again every
    ``every`` ticks (the chorale: a new ring note every 8 ticks)."""
    n = 0
    t = start
    while t < end:
        if every is None:
            g.put(layer, t, inst, ring_pitch(n, light, sh), vel + (6 if n % 5 == 0 else 0))
        else:
            for r in range(0, step, every):
                g.put(layer, t + r, inst, ring_pitch(n, light, sh), (vel + (6 if n % 5 == 0 else 0)) * (0.9 ** (r // every)))
        n += 1
        t += step


def compose(phase):
    sh = PHASES[phase]["shift"]
    A = 33 + sh
    g = Grid(TPS, BAR, BARS, SPECS)
    rng = Rng(61 + phase)
    gun_rng = Rng(73 + phase)
    # ---- the intro: loud, a machine that boots badly: the ring once, a stuttering note, quarter roots, a snare roll
    g.restrike("gun", DIDGERIDOO, A, 0, 64, 3, 76, 96)                       # t283: a note at tick 0 and a pedal that never leaves the bass
    for bar, base in ((-2, 80), (-1, 90)):
        for t in range(0, BAR, 4):
            g.put("bass", g.start(bar) + t, BASS, A, base - 4)
        for t in (0, 12, 20):
            g.drum("kick", g.start(bar) + t, BASEDRUM, 96)
    g.drum("kick", g.start(-2) - 4, BASEDRUM, 100)
    ring(g, g.start(-1), g.start(-1) + 20, 4, False, sh, 84)
    g.stutter("glitch", BIT, 66 + sh, g.start(-1) + 22, 5, 90, decay=0.85)
    g.clicks("roll", SNARE, 66, 63, [1, 1, 1, 1, 2, 2, 2, 3, 3, 4], 40, 98)
    g.clicks("noise", HAT, 66, 63, [1, 1, 1, 2, 3, 4], 20, 70)
    # ---- the sections that have a ring: P1 (step 4; phase 2 eighths from bar 2), P2 (eighths), the finale of the climax
    ring(g, g.start(0), g.start(4), 4, False, sh, 88)
    ring(g, g.start(4), g.start(12), 2, False, sh, 86)
    if phase == 1:
        ring(g, g.start(2), g.start(4), 2, False, sh, 90, layer="cell2", inst=IRON_XYLOPHONE) if False else None
    for k in range(BARS):
        name, i, count = section_of(SECTIONS, k)
        s = g.start(k)
        starts = groups(k, phase, name)
        chord = chord_of(name, i) if name in ("P4", "CL") else None
        note = (CH[chord][0] + sh) if chord else A
        # ---------------- P1
        if name == "P1":
            for b in range(7):
                g.put("gun", s + 4 * b, DIDGERIDOO, A + (1 if b in (3, 5) else 0), 88 + (10 if 4 * b in starts else 0) + 4 * phase)
            for t in starts:
                g.drum("kick", s + t, BASEDRUM, 96)
                g.drum("kick", s + t + 3, BASEDRUM, 76)
            for t in range(2, BAR, 4):
                g.drum("hat", s + t, HAT, 40 + 4 * i)
            for t in starts:
                if i >= 1:
                    hit(g, s + t, A, 54 + sh * 1, False)
            if i >= 2:
                for t in range(4, BAR, 4):
                    if t not in starts:
                        hit(g, s + t, A, 54 + sh, False, full=False)
        # ---------------- P2: pseudo double tempo
        elif name == "P2":
            mode, p = (("e", 0) if i < 3 else ("m", 0.5 + 0.08 * i)) if phase == 0 else (("m", 0.7) if i < 2 else (("s", 1) if i % 4 != 3 else ("m", 1.0)))
            for t in gun_ticks(mode, BAR, k, p, gun_rng):
                g.put("gun", s + t, DIDGERIDOO, A + SWAY[(k * 7 + t // 4) % 12] % 8, 86 + (12 if t in starts else 0) + (4 if t % 4 == 0 else 0))
            for t in starts:
                g.put("bass", s + t, BASS, A, 96)
            for b in range(7):
                g.drum("kick", s + 4 * b, BASEDRUM, 98 if 4 * b in starts else 88)
                g.drum("snare", s + 4 * b + 2, SNARE, 74 + 6 * phase + (8 if 4 * b in starts else 0))
            for t in range(0, BAR, 2):
                g.put("gtr", s + t, GUITAR, 45 + sh + (6 if (t // 2) % 7 == 6 else 0), 84 + (8 if t in starts else 0))
            for t in range(0, BAR, 2):
                g.drum("hat", s + t, HAT, 46 if t in starts else 34)
            for t in starts:
                hit(g, s + t, A, 54 + sh, t == 0 and i % 4 == 0)
            if i >= 2:
                for t in range(4, BAR, 4):
                    if t not in starts:
                        hit(g, s + t, A, 54 + sh, False, full=False)
        # ---------------- P3: the melody is gone: bass + factory metal
        elif name == "P3":
            if phase == 0:
                mode, p = ("q", 0) if i < 2 else (("e", 0) if i < 4 else (("m", 0.4 + 0.06 * (i - 4)) if i < 10 else ("s", 1)))
            else:
                mode, p = ("e", 0) if i < 2 else (("m", 1.0) if i < 6 else ("s", 1))
            for t in gun_ticks(mode, BAR, k, p, gun_rng):
                g.put("gun", s + t, DIDGERIDOO, A + SWAY[(k * 7 + t // 4) % 12] % 8 + min(3, i // 3), 90 + (10 if t in starts else 0) + 4 * phase)
            for t in starts:
                g.put("bass", s + t, BASS, A, 98)
                g.put("clank", s + t, IRON_XYLOPHONE, 54 + sh, 96)
                g.put("clank", s + t + 6, IRON_XYLOPHONE, 55 + sh, 80)
            for b in range(7):
                g.put("stamp", s + 4 * b + 2, COW_BELL, (84, 78, 84, 81, 78, 84, 76)[b], 80 + (8 if b in (1, 4, 6) else 0))
                g.drum("snare", s + 4 * b + 1, SNARE, 60 + i)
            for t in starts:
                hit(g, s + t, A, 54 + sh, False)
            if i >= 2:
                for t in range(4, BAR, 4):
                    if t not in starts:
                        hit(g, s + t, A, 54 + sh, False, full=False)
            for t in range(0, BAR, 4):
                g.drum("kick", s + t, BASEDRUM, 92)
        # ---------------- P4: sacred
        elif name == "P4":
            pads(g, s, chord, sh, 78 + 2 * (i % 4), 88 + 2 * (i % 4), False, True)
            hit(g, s, note, 54 + sh, i % 4 == 0)
            g.drum("pound", s + starts[2], BASEDRUM, 92)
            g.put("bass", s + starts[2], BASS, note, 94)
            g.put("gun", s + starts[2], DIDGERIDOO, note, 84)
            for t in range(3, BAR, 3):                                  # the organ pedal is struck again every 3 ticks
                g.put("gun", s + t, DIDGERIDOO, note, 78)
            for t in ((8, 16, 24) if i % 2 else (6, 14, 22)):
                g.drum("pound", s + t, BASEDRUM, 82)
            g.put("bell", s + starts[2] + 4, COW_BELL, 78, 70)
        # ---------------- CL
        else:
            for t in range(BAR):
                released = 8 <= i < 12
                if not released:
                    n = note + (0, 0, 7, 0, 12, 7, 0)[t // 4] if (i >= 12 or True) else note
                    if n > 54:
                        n -= 12
                    g.put("gun", s + t, DIDGERIDOO, n, 90 + (8 if t in starts else 0) + (4 if t % 4 == 0 else -4 if t % 2 else 0))
            for t in starts:
                g.put("bass", s + t, BASS, note, 98)
            for b in range(7):
                g.drum("kick", s + 4 * b, BASEDRUM, 98 if 4 * b in starts else 88)
                g.drum("snare", s + 4 * b + 2, SNARE, 84 + 4 * phase + (8 if 4 * b in starts else 0))
            for t in range(2, BAR, 4):                                      # the hat only on the offbeats: the kick on every beat and the sixteenth gun carry the speed
                g.drum("hat", s + t, HAT, 54)
            pads(g, s, chord, sh, 84 + 3 * phase, 94 + 3 * phase, True, False)
            if i >= 12:                                     # the ring runs away in eighths, with a chug
                for t in range(0, BAR, 4):
                    g.put("gtr", s + t, GUITAR, 45 + sh + (note - 33 - sh) % 12, 90)
            for t in starts:
                hit(g, s + t, note, 54 + sh, t == 0 and i % 4 == 0)
            if i >= 4:
                for t in range(4, BAR, 4):
                    if t not in starts:
                        hit(g, s + t, note, 54 + sh, False, full=False)
    # the ring of the climax: chorale of long notes (bars 0-7, the half-time dip is the release 8-11) and the runaway (12-15)
    cl = g.start(36)
    ring(g, cl, g.start(44), 8, True, sh, 92, every=3)
    ring(g, g.start(44), g.start(48), 8, True, sh, 94, every=3)
    ring(g, g.start(48), g.start(52), 2, True, sh, 94)
    # the ring of the sacred part: the chorale in long notes
    ring(g, g.start(24), g.start(35), 8, True, sh, 90, every=3)
    if phase == 1:
        ring(g, g.start(24), g.start(35), 8, True, sh, 80, layer="cell2", inst=IRON_XYLOPHONE, every=3)
        ring(g, g.start(48), g.start(52), 2, True, sh, 84, layer="cell2", inst=IRON_XYLOPHONE) if False else None
    # ---- rolls before a block, the dropouts, the break
    for bar in (3, 11, 23):
        g.clicks("roll", SNARE, 66, g.start(bar) + BAR - 1, [1, 1, 1, 1, 1, 2, 2, 2, 3, 3], 40, 100)
    for bar, tick, length in DROPS[phase]:
        a = g.start(bar) + tick
        g.mute(a, a + length, keep=("gun", "bass"))                      # t283: a dropout cuts the tune and the drums, never the bass
        hit(g, a + length, A, 54 + sh, False, full=False)
    for bar in (11,):
        g.mute(g.start(bar) + 14, g.start(bar + 1), keep=("gun", "bass"))
        g.restrike("gun", DIDGERIDOO, A, g.start(bar) + 14, BAR - 14, 3, 76, 84, over=True)
    for first, root_note in ((4, A), (12, A), (24, CH["A"][0] + sh), (36, CH["A"][0] + sh)):
        hit(g, g.start(first), root_note, 54 + sh, True)
    g.mute(g.start(BREAK_BAR), g.start(BREAK_BAR + 1))
    broot = CH[chord_of("P4", 11)][0] + sh
    g.restrike("gun", DIDGERIDOO, broot, g.start(BREAK_BAR), BAR, 3, 62, 72)          # the break keeps the bass: a low pedal (medium) under the one bell
    g.put("bass", g.start(BREAK_BAR), BASS, broot, 66)
    g.put("chime", g.start(BREAK_BAR) + 2, BELL, 96, 44)
    hit(g, g.start(36), CH["A"][0] + sh, 54 + sh, True)
    return g


TITLES = ("Holy Circuit (Boss010)", "Holy Circuit, Overclocked (Boss010)")
FILES = ("circuit_phase1.nbs", "circuit_phase2.nbs")
DESCRIPTIONS = (
    "Boss, phase 1, class 3 (style 2, 130 BPM, 7/4): a ring of five notes that ignores the bar of seven, a pseudo double tempo, the melody gone (bass and factory metal), the flip of the ring to A major as a chorale over organ chords, a one-bell break, the bass released once and a runaway climax.",
    "Boss, phase 2: a half step up, the groups change every bar, the gun earlier, a doubled chorale and a third pad voice. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, 64 + first * BAR, 64 + (first + count) * BAR) for name, first, count in SECTIONS]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks, bass_layers=("gun",), keep_layers=("cell", "pad1", "pad2"))


if __name__ == "__main__":
    main()
