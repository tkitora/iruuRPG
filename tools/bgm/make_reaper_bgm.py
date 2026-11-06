#!/usr/bin/env python3
"""Makes reaper_phase1.nbs and reaper_phase2.nbs: "Silence of the Scythe (Boss005)" and "Silence of the Scythe, Unleashed (Boss005)" - a reaper, faceless, whose scythe is a rising blade. REBUILT FROM ZERO (t281).

    python3 tools/bgm/make_reaper_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_reaper_bgm.py outdir     writes them somewhere else

CLASS 3 (inorganic fear and erosion) in STYLE 2 of the structure guide (despair and sacred, many stages), 130 BPM = 8.667 ticks a second, the song tick is a sixteenth, a beat of 4 ticks, a bar of 5/4 = 20 ticks
(3+2), 76 bars = 1584 ticks = 182.8 s; every section is a multiple of 4 bars (a 20-tick bar meets the engine's 16-tick line every 4 bars). The loop (tick 64) is a bar line: a silent offset of 4 ticks and 3 bars.
THE MOTIF - "the blade": a ladder of the diminished seventh climbing in threes (G Bb Db E = ticks 0 3 6 9) and then one long note on the next rung (re-struck, hanging UP), repeated a half step higher every bar
for four bars. It has no home (it is the chord that belongs to no key); in the sacred part the SAME ladder turns into a major triad (C# E# G# and the octave): the mode flip is the motif itself.
KEY: the bass is C# (C#2), the dark parts sit on it as a tritone / diminished cloud; the sacred part is C# MAJOR (C# | A#m | F# | G#), the climax adds a whole-step spurt (D#) and comes back to the dominant G#.
TIME TABLE (bars of 2.31 s; the style table scaled to 3:00):
  P1  0:00-0:26 (intro + 8 bars)   the beat has no head: the bass pattern 0 3 6 9 12 14 16 18 ROTATES by 3 ticks every bar; the blade comes in rung by rung (none, two, three, four notes); hits on the groups.
  P2  0:26-0:54 (12 bars)          PSEUDO DOUBLE TEMPO: the snare on every beat, the kick between the beats, the bass eighths -> sixteenth doubles / 3-tick runs; the full blade a half step higher each bar.
  P3  0:54-1:31 (16 bars)          THE MELODY IS GONE: only the bass (quarters -> eighths -> doubles -> gun) and staccato anvil and bone (iron-xylophone clank, banjo on a 3+3+2 cell, cow bell, snare rim).
  P4  1:31-2:08 (16 bars)          SACRED, MODE FLIP to C major: block chords of pling / harp / flute (C# | A#m | F# | G#), the blade as a loud major ladder, half-time drums; bar 51 is the BREAK (all muted, one bell).
  CL  2:08-3:03 (24 bars)          CLIMAX: P2 rhythm (snare and kick on every beat, the sixteenth gun on the chord roots) fused with the chords and the major blade; a half-time dip (bars 12-15: the blade in whole-bar notes
                                   while the drums keep speed), a whole-step spurt with hammered top notes and a guitar power chord (16-19), the way back to G# (20-23) and into the dark C#.
Bass ladder: quarters (intro) -> rotating pattern (P1) -> eighths -> doubles (P2) -> quarters -> eighths -> doubles -> gun tease (P3) -> half-time (P4) -> sixteenth gun (CL). Phase 2: a half step up, the 5 turns 3+2 / 2+3,
the gun earlier (P2, P3), the heart twice as fast, a third pad voice, the blade doubled in the sacred part, the spurt hammered longer. Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from cat3_machine import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS, BAR, BARS = 130 / 15, 20, 76
SECTIONS = [("P1", 0, 8), ("P2", 8, 12), ("P3", 20, 16), ("P4", 36, 16), ("CL", 52, 24)]
BREAK_BAR = 51
SPECS = {  # layer: (volume, pan)
    "heart": (100, 100), "pound": (100, 100), "snare": (100, 105), "clock": (100, 120), "noise": (100, 130), "roll": (100, 105), "tock": (100, 70),
    "bass": (100, 100), "drone": (100, 100), "tune": (100, 95), "tune2": (100, 105), "ham": (100, 110), "gtr": (100, 90), "clank": (100, 85), "bone": (100, 125),
    "pad1": (100, 90), "pad2": (100, 105), "pad3": (100, 115), "choir": (100, 100), "bell": (100, 100),
}
PHASES = [dict(shift=0, alt=False), dict(shift=1, alt=True)]
TONIC = 37                                               # C#2
BLADE = 55                                               # G3: the diminished ladder G Bb Db E
PATTERN = [0, 3, 6, 9, 12, 14, 16, 18]                   # the bass of P1 (rotated by 3 ticks every bar)
P1_PITCH = [0, 0, 1, 0, 6, 0, 1, 7]
WALK = [0, 1, 0, 3, 6, 3, 1, 0, 7, 6, 3, 1]
TRESILLO = [0, 3, 6, 8, 11, 14, 16, 18]                  # 3 3 2 3 3 2 2 2
BONE_PITCH = [0, 1, 0, 6, 7, 6, 1, 0]
# the sacred chords (bass root, voices): C# major, A#m, F#, G#
CH = {"C#": (37, [61, 65, 68, 73]), "A#m": (34, [58, 61, 65, 70]), "F#": (30, [58, 61, 66, 70]), "G#": (32, [56, 60, 63, 68])}
LADDER_BASE = {"C#": (61, (0, 4, 7)), "A#m": (58, (0, 3, 7)), "F#": (54, (0, 4, 7)), "G#": (56, (0, 4, 7))}
P4_PROG = [["C#", "A#m", "F#", "G#"], ["F#", "G#", "C#", "A#m"], ["C#", "A#m", "F#", "G#"], ["F#", "C#", "G#", "G#"]]
CL_PROG = [["C#", "A#m", "F#", "G#"], ["F#", "G#", "C#", "A#m"], ["C#", "A#m", "F#", "G#"], ["F#", "G#", "C#", "A#m"], ["C#", "F#", "G#", "A#m"], ["C#", "A#m", "F#", "G#"]]
SILENCES = {0: [(3, 12), (6, 6), (10, 12), (14, 8), (17, 10), (24, 12), (28, 6), (31, 14)], 1: [(5, 12), (10, 14), (15, 12), (27, 8)]}


def accents(k, ph):
    """Where the groups of the 5 begin: 3+2 (0, 12), in phase 2 every other bar 2+3 (0, 8)."""
    return (0, 8) if ph["alt"] and k % 2 == 1 else (0, 12)


def chord_of(name, i):
    if name == "P4":
        return P4_PROG[i // 4][i % 4]
    return CL_PROG[i // 4][i % 4]


def hit(g, tick, note, clank, big=False, cow=84):
    """An ensemble hit (7 notes at one tick): kick, bass, pedal, snare, cow bell, low clank, hat."""
    g.drum("pound", tick, BASEDRUM, 100, over=True)
    g.put("bass", tick, BASS, note, 98, over=True)
    g.put("drone", tick, DIDGERIDOO, note, 92, over=True)
    g.drum("snare", tick, SNARE, 92 if big else 80, over=True)
    g.put("tock", tick, COW_BELL, cow, 80, over=True)
    g.put("clank", tick, IRON_XYLOPHONE, clank, 96, over=True)
    g.drum("clock", tick, HAT, 62, over=True)


def blade(g, s, base, vel, count=4, intervals=(0, 3, 6, 9), top=12, layer="tune", inst=BIT, held=(12, 16)):
    """The motif: ``count`` rungs of the ladder three ticks apart, then one long note on the next rung (struck at ``held``, hanging up)."""
    for j in range(count):
        g.put(layer, s + 3 * j, inst, base + intervals[j], vel + (6 if j == 0 else 0))
    if count >= 4:
        for n, t in enumerate(held):
            g.put(layer, s + t, inst, base + top, vel * (0.92 ** n) + 4)


def pads(g, s, chord, sh, v0, v1, rolled, third):
    root, tones = CH[chord]
    offs = (0, 1, 2) if rolled else (0, 0, 2)
    for t in range(BAR):
        v = v0 + (v1 - v0) * t / (BAR - 1)
        if t % 4 == offs[0]:
            g.put("pad1", s + t, PLING, tones[0] + sh, v)
        if t % 4 == offs[1]:
            g.put("pad2", s + t, HARP, tones[2] + sh, v * 0.95)
        if third and t % 4 == offs[2]:
            g.put("pad3", s + t, PLING, tones[1] + sh, v * 0.95)
        if t % 4 == 0 and t % 8 == 0:
            g.put("choir", s + t, FLUTE, tones[3] + 12 + sh, 74)


def compose(phase):
    ph = PHASES[phase]
    sh = ph["shift"]
    R = TONIC + sh
    g = Grid(TPS, BAR, BARS, SPECS)
    rng = Rng(41 + phase)
    gun_rng = Rng(53 + phase)
    # ---- the intro: a heavy statement, not a whisper: loud quarter roots, the heart, a snare roll, the blade once (bars -2, -1)
    for bar, base in ((-3, 80), (-2, 84), (-1, 90)):
        for t in range(0, BAR, 4):
            g.put("bass", g.start(bar) + t, BASS, R, base + (8 if t == 0 else 0))
    for bar in (-2, -1):
        for t in (0, 12):
            g.drum("heart", g.start(bar) + t, BASEDRUM, 96)
            g.drum("heart", g.start(bar) + t + 3, BASEDRUM, 78)
    g.drum("heart", g.start(-3), BASEDRUM, 100)
    g.restrike("drone", DIDGERIDOO, R, g.start(-2), 2 * BAR, 4, 70, 92)
    blade(g, g.start(-1), BLADE - 1 + sh, 82, 4)
    g.clicks("roll", SNARE, 66, 63, [1, 1, 1, 1, 2, 2, 2, 3, 3, 4], 40, 98)
    g.clicks("noise", HAT, 66, 63, [1, 1, 1, 2, 3, 4], 20, 70)
    # ---- the heart: the bass drum on a cycle of its own through the dark parts
    for name, first, count, period in (("P1", 0, 8, 7 - 3 * phase), ("P2", 8, 12, 9 - 4 * phase), ("P3", 20, 16, 10 - 4 * phase)):
        tick = g.start(first)
        n = 0
        while tick < g.start(first + count):
            if n % 8 != 7:
                g.drum("heart", tick, BASEDRUM, 92, over=True)
                g.drum("heart", tick + 2, BASEDRUM, 74, over=True)
            tick += period
            n += 1
    for k in range(BARS):
        name, i, count = section_of(SECTIONS, k)
        s = g.start(k)
        acc = accents(k, ph)
        chord = chord_of(name, i) if name in ("P4", "CL") else None
        shift = 0
        if name == "CL" and 16 <= i < 20:
            shift = 2                                        # the whole-step spurt
        root_note = (CH[chord][0] + sh + shift) if chord else R
        # ---------------- P1: the head of the beat is hidden
        if name == "P1":
            rot = (3 * i) % BAR
            ticks = sorted((t + rot) % BAR for t in PATTERN)
            for j, t in enumerate(ticks):
                if i < 2 and j % 2:
                    continue
                g.put("bass", s + t, BASS, R + P1_PITCH[j], 82 + (12 if j == 0 else 0) + 2 * i + 4 * phase)
            for t in (rot, (rot + 12) % BAR):
                g.drum("pound", s + t, BASEDRUM, 96)
            for t in range(1, BAR, 3):
                g.drum("clock", s + t, HAT, 40 + 3 * i)
            if i >= 2:
                blade(g, s, BLADE + sh + i % 4, 88, min(4, 2 + (i - 2) // 2) if i < 6 else 4)
            if i >= 1:
                for t in acc:
                    hit(g, s + t, R, 54 + sh, False)
        # ---------------- P2: pseudo double tempo
        elif name == "P2":
            mode, p = (("e", 0) if i < 3 else (("m", 0.4 + 0.07 * i) if i < 9 else ("m", 1.0))) if phase == 0 else (("m", 0.6) if i < 2 else (("m", 1.0) if i % 4 == 3 else ("s", 1)))
            for t in gun_ticks(mode, BAR, k, p, gun_rng):
                g.put("bass", s + t, BASS, R + WALK[(k * 5 + t // 4) % 12], 84 + (14 if t in acc else 0) + (4 if t % 4 == 0 else 0))
            for b in range(5):
                g.drum("snare", s + 4 * b, SNARE, 70 + (12 if 4 * b in (acc[0] + 4, acc[1] + 4) else 0) + 6 * phase)
                g.drum("pound", s + 4 * b + 2, BASEDRUM, 84 + 6 * phase)
            for t in range(0, BAR, 2):
                g.drum("clock", s + t, HAT, 46 if t in acc else 34)
            blade(g, s, BLADE + sh + i % 4, 90, 4)
            for t in acc:
                hit(g, s + t, R, 54 + sh, t == 0 and i % 4 == 0)
            if i >= 5:
                for t in (4, 8, 16):
                    if t not in acc:
                        hit(g, s + t, R, 54 + sh, False)
        # ---------------- P3: the melody is gone: bass + anvil and bone
        elif name == "P3":
            if phase == 0:
                mode, p = ("q", 0) if i < 2 else (("e", 0) if i < 5 else (("m", 0.4 + 0.07 * (i - 5)) if i < 14 else ("s", 1)))
            else:
                mode, p = ("e", 0) if i < 2 else (("m", 1.0) if i < 8 else ("s", 1))
            for t in gun_ticks(mode, BAR, k, p, gun_rng):
                g.put("bass", s + t, BASS, R + WALK[(k * 5 + t // 4) % 12] + min(4, i // 3), 88 + (12 if t in acc else 0) + 4 * phase)
            for j, t in enumerate(TRESILLO):
                if i >= 1:
                    g.put("bone", s + t, BANJO, 61 + sh + BONE_PITCH[(j + k) % 8], 88 + (8 if j % 3 == 0 else 0))
            for t in acc:
                hit(g, s + t, R, 54 + sh, False)
            if i >= 8:
                for t in (4, 8, 16):
                    if t not in acc:
                        hit(g, s + t, R, 54 + sh, False)
            for t in (2, 8, 14):
                g.drum("snare", s + t, SNARE, 62 + i)
            if i >= 4:
                g.drum("pound", s + 8 if acc[1] == 12 else s + 4, BASEDRUM, 90)
        # ---------------- P4: sacred, the mode has flipped
        elif name == "P4":
            pads(g, s, chord, sh, 78 + 2 * (i % 4), 88 + 2 * (i % 4), False, True)
            base, iv = LADDER_BASE[chord]
            blade(g, s, base + sh, 90, 4, (iv[0], iv[1], iv[2], iv[2]), 12, "tune", BIT, held=(12, 16))
            if phase == 1:
                blade(g, s, base + sh - 12 if base - 12 >= 54 else base + sh, 80, 4, (iv[0], iv[1], iv[2], iv[2]), 12, "tune2", PLING, held=(12, 16))
            hit(g, s, root_note, 54 + sh, i % 4 == 0)
            g.drum("pound", s + acc[1], BASEDRUM, 90)
            for t in (4, 8, 16) if i % 2 else (6, 14):
                g.drum("pound", s + t, BASEDRUM, 84)
            g.put("bass", s + acc[1], BASS, root_note, 92)
            g.put("drone", s + acc[1], DIDGERIDOO, root_note, 80)
            for t in range(4, BAR, 4):
                if t != acc[1]:
                    g.put("drone", s + t, DIDGERIDOO, root_note, 70)
            g.put("tock", s + acc[1] + 4, COW_BELL, 78 + sh, 70)
        # ---------------- CL: the climax: the machine fused with the chords
        else:
            lap = i // 4
            base, iv = LADDER_BASE[chord]
            gun_notes = (0, 0, 0, 7, 12)
            for t in range(BAR):
                note = root_note + gun_notes[t // 4]
                if note > 54:
                    note -= 12
                g.put("bass", s + t, BASS, note, 90 + (8 if t in acc else 0) + (4 if t % 4 == 0 else -4 if t % 2 else 0))
            for b in range(5):
                g.drum("snare", s + 4 * b, SNARE, 84 + 4 * phase + (10 if 4 * b in (acc[0] + 4, acc[1] + 4) else 0))
                g.drum("pound", s + 4 * b, BASEDRUM, 96 if 4 * b in acc else 86)
                g.drum("pound", s + 4 * b + 2, BASEDRUM, 72 + 6 * phase)
            for t in range(0, BAR, 2):
                g.drum("clock", s + t, HAT, 56 if t in acc else 40)
            rolled = True
            pads(g, s, chord, sh + shift, 84 + 3 * phase, 94 + 3 * phase, rolled, phase == 1)
            if 12 <= i < 16:                                # half-time dip: the blade in whole-bar notes, the drums keep their speed
                tone = (base + iv[(i - 12) % 3] + sh)
                g.restrike("tune", BIT, tone, s, BAR, 4, 94, 84)
            else:
                blade(g, s, base + sh + shift, 94, 4, (iv[0], iv[1], iv[2], iv[2]), 12, "tune", BIT, held=(12, 16))
            if i >= 16:                                     # the spurt: hammered top note and a guitar power chord
                top = base + sh + shift + 12
                for t in range(0, BAR, 2):
                    g.put("ham", s + t + 1, BIT, min(78, top) - (12 if (t // 2) % 5 == 4 else 0), 90 + (8 if t in acc else 0))
                for t in acc:
                    g.put("gtr", s + t, GUITAR, 42 + (root_note - 30) % 12 + 12, 94)
                    g.put("gtr", s + t + 2, GUITAR, 42 + (root_note - 30) % 12 + 19 - 12 * ((root_note - 30) % 12 > 5), 80)
            for t in acc:
                hit(g, s + t, root_note, 54 + sh, t == 0 and i % 4 == 0, 84 + shift)
            if (i >= 12 and i % 4 == 3) or (phase == 1 and i >= 8 and i % 2 == 1):
                for t in (4, 8, 16):
                    if t not in acc:
                        hit(g, s + t, root_note, 54 + sh, False, 84 + shift)
        # the pedal in the dark parts follows the root
        if name in ("P1", "P2"):
            for t in range(0, BAR, 4):
                g.put("drone", s + t, DIDGERIDOO, R, 74 + (8 if t in acc else 0))
    # ---- rolls before a block, the silences of the scythe
    for bar in (7, 19, 35):
        g.clicks("roll", SNARE, 66, g.start(bar) + 19, [1, 1, 1, 1, 1, 2, 2, 2, 3, 3], 40, 100)
    for bar, from_tick in SILENCES[phase]:
        g.mute(g.start(bar) + from_tick, g.start(bar + 1))
        g.drum("pound", g.start(bar + 1), BASEDRUM, 100, over=True)
        g.put("bass", g.start(bar + 1), BASS, R, 100, over=True)
        g.drum("snare", g.start(bar + 1), SNARE, 84, over=True)
    # ---- blocks open with an ensemble hit; the break; the way back
    for first in (8, 20, 36, 52):
        hit(g, g.start(first), R if first < 36 else CH[chord_of("P4" if first == 36 else "CL", 0)][0] + sh, 54 + sh, True)
    g.mute(g.start(BREAK_BAR), g.start(BREAK_BAR + 1))
    g.put("bell", g.start(BREAK_BAR) + 2, BELL, 96, 44)
    hit(g, g.start(52), CH["C#"][0] + sh, 54 + sh, True)
    g.clicks("noise", HAT, 66, g.length - 1, [1, 1, 1, 2, 2, 3, 4], 40, 100, over=True)
    return g


TITLES = ("Silence of the Scythe (Boss005)", "Silence of the Scythe, Unleashed (Boss005)")
FILES = ("reaper_phase1.nbs", "reaper_phase2.nbs")
DESCRIPTIONS = (
    "Reaper fight, phase 1, class 3 (style 2, 130 BPM, 5/4): a diminished-seventh blade climbing in threes over a rotating bass, a pseudo double tempo, the melody gone (bass, anvil and bone), the flip of the blade to C# major over organ-like chords, a one-bell break and a sixteenth-gun climax with a whole-step spurt.",
    "Reaper fight, phase 2: a half step up, the 5 turns 3+2 / 2+3, the gun earlier, a doubled blade and third pad voice. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, 64 + first * BAR, 64 + (first + count) * BAR) for name, first, count in SECTIONS]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks, bass_layers=("bass",), keep_layers=("tune", "pad1", "pad2", "ham"))


if __name__ == "__main__":
    main()
