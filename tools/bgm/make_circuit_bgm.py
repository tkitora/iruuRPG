#!/usr/bin/env python3
"""Makes circuit_phase1.nbs and circuit_phase2.nbs: "Holy Circuit (Boss010)" and "Holy Circuit, Overclocked (Boss010)" - a holy machine that runs in a ring. REBUILT FROM ZERO (t290) so that it CONTAINS the structure guides.

    python3 tools/bgm/make_circuit_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_circuit_bgm.py outdir     writes them somewhere else

CLASS 3 (inorganic fear and erosion), the machine variant of STYLE 2 of the structure guide: 130 BPM = 8.667 ticks a second, a tick is a sixteenth, a beat 4 ticks, the bar is 7/4 = 28 ticks (3+2+2 / 2+2+3 /
2+3+2 beats), 52 bars = 1520 ticks = 175.4 s, every section a multiple of 4 bars. Key: B-flat (bass Bb1). The loop (tick 64) is a bar line.
THE MOTIF - "the circuit": a ring of FIVE notes on the iron xylophone that loops without caring about the bar of seven beats (Bb B E F Db: a half step, a tritone, a fifth, a third: the machine cell), each lap
(five notes) a half step higher, four laps, then back. In the sacred part the SAME ring turns major (Bb C D F G): the mode flip is the ring itself.
Sounds: a didgeridoo gun with a BASS sub (buzz), an iron-xylophone ring and clank, a guitar chug, cow bell / hat / snare as factory metal, pling + harp + 8-bit top voice as the sacred organ.
TIME TABLE (bars of 3.23 s):
  P1  0:00-0:20 (intro + 4 bars)   the ring alone against the gun on the beats (QUARTERS), tritones and half steps against the Bb bass (an unsettling start); the heart on the groups.
  P2  0:20-0:46 (8 bars)           PSEUDO DOUBLE TEMPO: the snare on every beat (the off-eighth), the kick on every beat, the ring in eighths, a guitar chug; the gun eighths -> doubles / 3-tick runs.
  P3  0:46-1:25 (12 bars)          THE MELODY IS LOST: only the gun and staccato factory metal (cow bell stamp on every beat, iron-xylophone clank on the groups, hat bursts, a ghost snare); the drums halve.
  P4  1:25-2:17 (16 bars)          SACRED, MODE FLIP to B-flat major: block organ chords (pling + harp + 8-bit top voice together every 3 ticks) Bb | Gm | Eb | F, the tune slow (two long notes a bar), half-time drums;
                                   the tune jumps up from 1:50; the last bar (39) is the BREAK: drums gone, the pedal stays, one bell.
  CL  2:17-2:55 (12 bars)          CLIMAX: kick and snare on every beat (the snare doubled in the last bars) with the long chords over the SIXTEENTH gun on the chord roots, the tune in long notes, a whole step up
                                   for bars 4-9, the top note (F5) held for the whole bar in the last bars; it ends on the dominant F, leading back to the dark Bb.
Bass ladder: quarters (intro, P1) -> eighths -> doubles (P2) -> eighths -> doubles (P3) -> half-time (P4) -> sixteenth gun (CL). Phase 2: a half step up, the groups change every bar (3+2+2 / 2+2+3 / 2+3+2), more hits,
a guitar under the tune. Everything is written by what SOUNDS. Nothing is random.
"""
import sys
from pathlib import Path

from cat3_machine import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS, BAR, BARS = 130 / 15, 28, 52
SECTIONS = [("P1", 0, 4), ("P2", 4, 8), ("P3", 12, 12), ("P4", 24, 16), ("CL", 40, 12)]
BREAK_BAR = 39
SPECS = {  # layer: (volume, pan)
    "kick": (100, 100), "pound": (100, 100), "snare": (100, 105), "hat": (100, 120), "roll": (100, 105), "bell": (100, 70), "stamp": (100, 125),
    "gun": (100, 100), "bass": (100, 100), "gtr": (100, 90), "ring": (100, 95), "tune": (100, 95), "tune2": (100, 105), "clank": (100, 85),
    "pad1": (100, 90), "pad2": (100, 110), "chime": (100, 100),
}
PHASES = [dict(shift=0), dict(shift=1)]
X = [0, 12, 20]     # 3+2+2 beats
Y = [0, 8, 16]      # 2+2+3
Z = [0, 8, 20]      # 2+3+2
ROOT = 34                                        # Bb1
RING_DARK = (0, 1, 6, 7, 3)                      # Bb B E F Db
RING_LIGHT = (0, 2, 4, 7, 9)                     # Bb C D F G
LAP = (0, 1, 2, 3)
RING_BASE = 58
SWAY = [0, 3, 0, 7, 5, 0, 1, 7, 6, 0, 3, 8]
# the sacred chords: (bass root, three low voices, the tune in P4 (two long notes a bar), the tune in the climax (three notes a bar))
CH = {"Bb": (34, (58, 62, 65), (70, 74), (70, 74, 77)), "Gm": (31, (55, 58, 62), (70, 73), (70, 73, 76)),
      "Eb": (39, (55, 58, 63), (70, 75), (70, 75, 78)), "F": (41, (57, 60, 65), (72, 77), (72, 77, 78))}
P4_PROGS = [["Bb", "Gm", "Eb", "F"], ["Eb", "F", "Bb", "Gm"], ["Bb", "Gm", "Eb", "F"], ["Eb", "Gm", "F", "F"]]
CL_PROGS = [["Bb", "Gm", "Eb", "F"], ["Eb", "F", "Bb", "Gm"], ["Bb", "Eb", "F", "F"]]
DROPS = {0: [(6, 20, 2), (8, 12, 2), (11, 22, 2), (14, 16, 3), (18, 10, 2), (21, 24, 2)],
         1: [(2, 20, 2), (5, 12, 2), (7, 22, 2), (9, 6, 2), (13, 14, 2), (16, 20, 2), (19, 8, 2), (22, 22, 2)]}


def groups(k, phase, name):
    if phase == 0:
        return {"P1": X, "P2": X if k % 2 == 0 else Y, "P3": Y if k % 4 == 3 else X, "P4": X, "CL": X if k % 2 == 0 else Y}[name]
    return {"P1": X, "P2": [X, Y, Z, Y][k % 4], "P3": [Y, Z, X, Z][k % 4], "P4": X, "CL": [X, Y, Z, Y][k % 4]}[name]


def hit(g, tick, note, clank=True, big=False, drums=True):
    """An ensemble hit (6-7 notes at one tick): kick, sub bass, gun, snare, alarm bell, hat and (in the dark parts) the iron-xylophone clank."""
    if drums:
        g.drum("pound", tick, BASEDRUM, 100, over=True)
        g.drum("snare", tick, SNARE, 92 if big else 80, over=True)
    else:
        g.put("stamp", tick, COW_BELL, 84, 80, over=True)               # a hit without the drums (the drums are halved in the lost-melody part)
    g.put("bass", tick, BASS, note, 98, over=True)
    g.put("gun", tick, DIDGERIDOO, note, 94, over=True)
    g.put("bell", tick, COW_BELL, 80, 78, over=True)
    if clank:
        g.put("clank", tick, IRON_XYLOPHONE, 54, 96, over=True)
    g.drum("hat", tick, HAT, 64, over=True)


def ring(g, start, end, step, light, sh, vel):
    """The ring of five notes from ``start`` to ``end``, a note every ``step`` ticks; the count starts with the section, so the ring ignores the bar; each lap a half step higher."""
    n = 0
    t = start
    table = RING_LIGHT if light else RING_DARK
    while t < end:
        g.put("ring", t, IRON_XYLOPHONE, RING_BASE + table[n % 5] + LAP[(n // 5) % 4] + sh, vel + (6 if n % 5 == 0 else 0))
        n += 1
        t += step


def organ(g, s, voices, v0, v1, tune, tune_second=None, split=16):
    """A long chord for one bar: the voices and the top tune voice are struck TOGETHER every 3 ticks (a held block chord)."""
    for t in range(0, BAR, 3):
        v = v0 + (v1 - v0) * (t % 14) / 13
        g.put("pad1", s + t, PLING, voices[0], v)
        g.put("pad2", s + t, HARP, voices[2], v * 0.95)
        note = tune if (tune_second is None or t < split) else tune_second
        g.put("tune", s + t, BIT, min(78, note), min(100, v + 4))


def compose(phase):
    sh = PHASES[phase]["shift"]
    R = ROOT + sh
    g = Grid(TPS, BAR, BARS, SPECS)
    rng = Rng(37 + phase)
    gun_rng = Rng(83 + phase)
    # ---- the intro: loud quarters on a 3-tick pedal from tick 0, the ring once, a snare roll
    for t in range(0, 64, 3):
        g.put("gun", t, DIDGERIDOO, R, 76 + t // 6)
    for t in range(0, 64, 12):
        g.put("bass", t, BASS, R, 96)
        g.drum("pound", t, BASEDRUM, 98)
    for t in (24, 48):
        g.put("bell", t, COW_BELL, 78, 80)
    ring(g, g.start(-1), g.start(-1) + 24, 4, False, sh, 84)
    g.clicks("roll", SNARE, 66, 63, [1, 1, 1, 1, 2, 2, 2, 3, 3, 4], 40, 98)
    # ---- P1 and P2 rings (the count starts with the section)
    ring(g, g.start(0), g.start(4), 4, False, sh, 88)
    ring(g, g.start(4), g.start(12), 2, False, sh, 86)
    for k in range(BARS):
        name, i, count = section_of(SECTIONS, k)
        s = g.start(k)
        starts = groups(k, phase, name)
        # ---------------- P1: quarters, tritones against the root
        if name == "P1":
            for b in range(7):
                g.put("gun", s + 4 * b, DIDGERIDOO, R + (0, 0, 1, 0, 6, 0, 1)[b], 88 + (10 if 4 * b in starts else 0))
            for t in starts:
                g.drum("pound", s + t, BASEDRUM, 96)
            for t in range(2, BAR, 4):
                g.drum("hat", s + t, HAT, 44 + 4 * i)
            if i >= 1:
                for t in starts:
                    hit(g, s + t, R, True)
            if i >= 2:
                for t in range(4, BAR, 4):
                    if t not in starts:
                        hit(g, s + t, R, True, drums=False)
        # ---------------- P2: pseudo double tempo
        elif name == "P2":
            mode, p = ("e", 0) if i < 3 else ("m", 0.5 + 0.07 * i)
            for t in gun_ticks(mode, BAR, k, p, gun_rng):
                g.put("gun", s + t, DIDGERIDOO, R + SWAY[(k * 7 + t // 4) % 12] % 8, 86 + (12 if t in starts else 0) + (4 if t % 4 == 0 else 0))
            for t in starts:
                g.put("bass", s + t, BASS, R, 96)
            for b in range(7):
                g.drum("pound", s + 4 * b, BASEDRUM, 98 if 4 * b in starts else 88)
                g.drum("snare", s + 4 * b + 2, SNARE, 76 + 6 * phase + (8 if 4 * b in starts else 0))
            for t in range(0, BAR, 2):
                g.put("gtr", s + t, GUITAR, 46 + sh + (6 if (t // 2) % 7 == 6 else 0), 84 + (8 if t in starts else 0))
            for t in starts:
                hit(g, s + t, R, True)
            if i >= 3:
                for t in range(4, BAR, 4):
                    if t not in starts:
                        hit(g, s + t, R, False)
            if k == 11:
                g.clicks("roll", SNARE, 66, s + BAR - 1, [1, 1, 1, 1, 2, 2, 3], 40, 100)
        # ---------------- P3: the melody is lost
        elif name == "P3":
            mode, p = ("e", 0) if i < 4 else ("m", 0.5 + 0.06 * (i - 4))
            for t in gun_ticks(mode, BAR, k, p, gun_rng):
                g.put("gun", s + t, DIDGERIDOO, R + SWAY[(k * 5 + t // 4) % 12] % 8 + min(3, i // 4), 90 + (10 if t in starts else 0) + 4 * phase)
            for t in starts:
                g.put("bass", s + t, BASS, R, 98)
                g.put("clank", s + t, IRON_XYLOPHONE, 54 + sh, 96)
                g.put("clank", s + t + 6, IRON_XYLOPHONE, 55 + sh, 80)
            for b in range(7):
                g.put("stamp", s + 4 * b + 2, COW_BELL, (84, 78, 84, 81, 78, 84, 76)[b], 80 + (8 if b in (1, 4, 6) else 0))
            for t in (6, 14, 22):
                g.drum("snare", s + t, SNARE, 62 + i)
            for t in starts:
                hit(g, s + t, R, True, drums=False)
            if i >= 3:
                for t in range(4, BAR, 4):
                    if t not in starts:
                        hit(g, s + t, R, True, drums=False)
        # ---------------- P4: sacred
        elif name == "P4":
            if i == 15:                                         # the break bar: handled below
                continue
            chord = P4_PROGS[i // 4][i % 4]
            root, voices, mel, _ = CH[chord]
            second_half = i >= 8
            m1, m2 = (min(78, m + 8) for m in mel) if second_half else (max(m - 3, voices[2] + 2) for m in mel)
            organ(g, s, [v + sh for v in voices], 86 + (i % 4), 94, m1 + sh, m2 + sh)
            if phase == 1:
                for t in range(0, BAR, 3):
                    g.put("tune2", s + t, GUITAR, 42 + (voices[0] + sh - 42) % 12 + 12, 82)
            hit(g, s, root + sh, False, i % 4 == 0)
            hit(g, s + starts[2], root + sh, False)
            for t in (8, 16, 24) if i % 2 else (6, 14, 22):
                g.drum("pound", s + t, BASEDRUM, 82)
            for t in range(3, BAR, 3):
                g.put("gun", s + t, DIDGERIDOO, root + sh, 78)
        # ---------------- CL: the climax
        else:
            chord = CL_PROGS[i // 4][i % 4]
            root, voices, _, mel = CH[chord]
            up = 2 if 4 <= i < 10 else 0
            r = root + sh + up
            for t in range(BAR):
                n = r + (0, 7, 0, 12, 7, 0, 12)[t // 4]
                if n > 54:
                    n -= 12
                g.put("gun", s + t, DIDGERIDOO, n, 90 + (8 if t in starts else 0) + (4 if t % 4 == 0 else -4 if t % 2 else 0))
            for t in starts:
                g.put("bass", s + t, BASS, r, 98)
            for b in range(7):
                g.drum("pound", s + 4 * b, BASEDRUM, 98 if 4 * b in starts else 88)
                g.drum("snare", s + 4 * b, SNARE, 88 + 4 * phase + (6 if 4 * b in starts else 0))
                if i >= 8:
                    g.drum("snare", s + 4 * b + 2, SNARE, 74)           # the finale: the snare on every half beat
            for t in range(2, BAR, 8):
                g.drum("hat", s + t, HAT, 54)
            a, b2, c = mel
            if i >= 8:
                a = b2 = c = max(c, 76)                                 # the last bars: the top note held the whole bar
            for t in range(0, BAR, 3):
                note = a if t < 9 else (b2 if t < 18 else c)
                g.put("tune", s + t, BIT, min(78, note + up + sh), 94)
                g.put("pad1", s + t, PLING, voices[0] + up + sh, 86 + 3 * phase)
                g.put("pad2", s + t, HARP, voices[2] + up + sh, 84 + 3 * phase)
                if phase == 1:
                    g.put("tune2", s + t, GUITAR, 42 + (voices[0] + up + sh - 42) % 12, 80)
            for t in starts:
                hit(g, s + t, r, False, t == 0 and i % 4 == 0)
            if i >= 2:
                for t in range(4, BAR, 4):
                    if t not in starts:
                        hit(g, s + t, r, False)
    # ---- rolls before a block, the dropouts (they cut the tune and the drums, never the bass)
    for bar in (3, 23):
        g.clicks("roll", SNARE, 66, g.start(bar) + BAR - 1, [1, 1, 1, 1, 1, 2, 2, 2, 3, 3], 40, 100)
    for bar, tick, length in DROPS[phase]:
        a = g.start(bar) + tick
        g.mute(a, a + length, keep=("gun", "bass"))
        hit(g, a + length, R, False)
    for first, note in ((4, R), (12, R), (24, CH["Bb"][0] + sh), (40, CH["Bb"][0] + sh)):
        hit(g, g.start(first), note, first < 24, True)
    # ---- the break: no drums, one bell, the pedal stays; a snare roll before it, a blow after it
    g.clicks("roll", SNARE, 66, g.start(BREAK_BAR) - 1, [1, 1, 1, 1, 2, 2, 2, 3], 50, 100, over=True)
    broot = CH["F"][0] + sh
    g.restrike("gun", DIDGERIDOO, broot, g.start(BREAK_BAR), BAR, 3, 62, 72)
    g.put("bass", g.start(BREAK_BAR), BASS, broot, 70)
    g.put("chime", g.start(BREAK_BAR) + 8, BELL, 96, 50)
    hit(g, g.start(40), CH["Bb"][0] + sh, False, True)
    return g


TITLES = ("Holy Circuit (Boss010)", "Holy Circuit, Overclocked (Boss010)")
FILES = ("circuit_phase1.nbs", "circuit_phase2.nbs")
DESCRIPTIONS = (
    "Boss, phase 1, class 3 (style 2, 130 BPM, 7/4, B-flat): an iron-xylophone ring of five notes that ignores the bar of seven, a pseudo double tempo, the melody lost (gun and factory metal), the flip of the ring to B-flat major over organ chords in half-time, a one-bell break, then the sixteenth gun fused with the chords and a climbing tune.",
    "Boss, phase 2: a half step up, the groups change every bar, a guitar under the tune. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, 64 + first * BAR, 64 + (first + count) * BAR) for name, first, count in SECTIONS]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks, bass_layers=("gun",), keep_layers=("tune", "pad1", "pad2"))


if __name__ == "__main__":
    main()
