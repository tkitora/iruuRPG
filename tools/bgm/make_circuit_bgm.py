#!/usr/bin/env python3
"""Makes circuit_phase1.nbs and circuit_phase2.nbs: "Holy Circuit (Boss010)" and "Holy Circuit, Overclocked (Boss010)", a holy machine that has run away, in two phases.

    python3 tools/bgm/make_circuit_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_circuit_bgm.py outdir     writes them somewhere else

CLASS 3, "inorganic fear and erosion", the other face of it: not a cold clock (that is the reaper) but a runaway AI, factory metal and a prayer that the machine keeps overwriting.
200 BPM: 20 ticks a second, a beat of 6 ticks, an eighth of 3 ticks; the bar is 7/4 = 14 eighths = 42 ticks, two bars of 7/8 in a row that are grouped 3+2+2 | 3+2+2 and from time to time 3+2+2 | 2+2+3
(ticks 0 9 15 | 21 30 36, or 0 9 15 | 21 27 33). 64 bars of the loop = 2688 ticks, with the intro 137.6 s. A 42-tick bar meets the engine's 16-tick line every 8 bars (336 ticks = 21 lines), so every section is a multiple
of 8 bars. The loop (tick 64) is a bar line: the intro is the tail of a bar (22 ticks) and one whole bar (22 + 42 = 64).
Sounds: bass drum (a heart that FAILS: lub-dub, a late beat, a missed beat, a flutter of five hits on consecutive ticks, a heart that stops), snare / hat / cow bell / iron xylophone as factory metal,
the 8-bit chip for glitches (the same note struck again and again on consecutive ticks, 1-tick pings) and for the HYMN, a plain chant (D F A D C A F | G A Bb A G F D) that comes up out of the noise and is
broken every time in a different way (cut off with a stuck note, a note skipped, a note a half step wrong, a note stuttering), then the machine drops every layer for a few ticks and comes back with a blow.
A didgeridoo hum on D with a tritone (G#) and a bass that walks D Eb D G# give the weight. The loop of the machine is seven eighths of iron xylophone (D F A G# A F E) that is dropped and stuttered by chance.
Phase 2 (Overclocked): a half step up (E-flat), the heart tachycardic (a lub-dub on every group), the groups change every bar (3+2+2 / 2+2+3 / 2+3+2), a second shuffled note after every note of the machine
(a tritone higher), hat on every tick in the peak, glitch pings two or three times as many, the hymn cut sooner. Everything is written by what SOUNDS. Nothing is random (cat3_machine.Rng).
The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat3_machine import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS, BAR, BARS = 20, 42, 64
SECTIONS = [("A", 0, 16), ("A2", 16, 16), ("B", 32, 8), ("PEAK", 40, 16), ("CALM", 56, 8)]
SPECS = {  # layer: (volume, pan)
    "kick": (100, 100), "bass": (100, 100), "hum": (100, 100), "snare": (100, 105), "roll": (100, 105), "hat3": (100, 115), "hat2": (100, 125),
    "bell": (100, 70), "arp": (100, 95), "clank": (100, 85), "hymn": (100, 110), "hym2": (100, 90), "glitch": (100, 130),
}
PHASES = [dict(shift=0), dict(shift=1)]
X = [0, 9, 15, 21, 30, 36]     # 3+2+2 | 3+2+2
Y = [0, 9, 15, 21, 27, 33]     # 3+2+2 | 2+2+3
Z = [0, 6, 12, 21, 30, 36]     # 2+2+3 | 3+2+2
W = [0, 6, 15, 21, 27, 33]     # 2+3+2 | 2+2+3
ARP = [62, 65, 69, 68, 69, 65, 64]                       # D F A G# A F E, seven eighths
HYMN = [(0, 62), (6, 65), (12, 69), (18, 74), (24, 72), (30, 69), (36, 65), (42, 67), (48, 69), (54, 70), (60, 69), (66, 67), (72, 65), (78, 62)]
# (first bar of the 2-bar hymn, how it is broken)
HYMNS = [
    (8, dict(cut=3, vel=40)), (12, dict(cut=5, stut=(1,), vel=44)),
    (18, dict(cut=8, skip=(3,), flat=(5,), vel=48)), (22, dict(cut=14, flat=(2, 9), stut=(6,), vel=50)), (26, dict(cut=7, vel=50)), (30, dict(cut=11, skip=(4, 5), vel=52)),
    (33, dict(cut=14, vel=42)), (36, dict(cut=10, stut=(9,), vel=46)),
    (40, dict(cut=11, vel=66)), (42, dict(cut=14, flat=(3,), vel=68)), (44, dict(cut=6, vel=68)), (46, dict(cut=14, skip=(5,), vel=70)), (48, dict(cut=9, vel=70)),
    (50, dict(cut=14, flat=(7, 8), vel=72)), (52, dict(cut=4, vel=72)), (54, dict(cut=10, stut=(4,), vel=72)),
    (56, dict(cut=4, vel=36)), (60, dict(cut=2, vel=30)),
]
DROPS = {  # (bar, tick of the bar, ticks of nothing): every layer is cut
    0: [(13, 30, 4), (15, 24, 3), (18, 12, 6), (21, 30, 9), (24, 18, 4), (27, 36, 6), (30, 3, 3), (33, 9, 12), (35, 21, 6), (37, 24, 9),
        (42, 30, 3), (44, 15, 4), (47, 24, 3), (50, 9, 3), (53, 33, 3), (58, 20, 12)],
    1: [(10, 20, 3), (13, 30, 3), (15, 12, 3), (17, 9, 3), (19, 24, 4), (21, 15, 3), (23, 36, 4), (25, 6, 3), (27, 30, 3), (29, 18, 4), (31, 33, 3),
        (33, 9, 6), (35, 21, 4), (37, 12, 6), (41, 18, 2), (43, 30, 3), (44, 9, 2), (46, 24, 3), (48, 36, 2), (49, 12, 2), (51, 27, 3), (52, 3, 2), (54, 21, 2), (55, 15, 3), (58, 20, 6)],
}
FAILS = {0: {"A": ["n", "n", "n", "miss"], "A2": ["n", "n", "late", "n", "miss", "n", "flutter", "half"], "B": ["half", "n", "miss", "half", "flutter", "half", "miss", "flat"],
             "PEAK": ["n", "flutter", "n", "late", "n", "flutter", "miss", "flutter"], "CALM": ["miss", "n", "half", "n", "miss", "half", "flat", "flat"]},
         1: {"A": ["n", "n", "miss", "n"], "A2": ["n", "late", "n", "miss", "flutter", "n", "half", "flutter"], "B": ["half", "flutter", "miss", "half", "flutter", "half", "flutter", "flat"],
             "PEAK": ["flutter", "n", "flutter", "late", "flutter", "miss", "flutter", "flutter"], "CALM": ["miss", "n", "half", "flutter", "miss", "half", "flat", "flat"]}}
HUM_VEL = {"A": 44, "A2": 54, "B": 48, "PEAK": 74, "CALM": 38}


def groups(k, phase, name):
    if phase == 0:
        if name == "A":
            return Y if k % 4 == 3 else X
        if name == "A2":
            return X if k % 2 == 0 else Y
        if name == "B":
            return Y
        if name == "PEAK":
            return X if k % 2 == 0 else Y
        return X
    if name == "A":
        return Y if k % 4 == 3 else X
    if name == "A2":
        return [X, Y, X, Z][k % 4]
    if name == "B":
        return [Y, Z, Y, W][k % 4]
    if name == "PEAK":
        return [X, Y, Z, W][k % 4]
    return X


def heart_bar(g, s, starts, phase, fail):
    """The bass drum as a heart: lub-dub on the first group of each half (phase 2: on every group); the failures cut and bend it."""
    beats = [(0, 0), (21, 1)] if phase == 0 else [(t, 0 if t < 21 else 1) for t in starts]
    dub_off = 3 if phase == 0 else 2
    for index, (t, half) in enumerate(beats):
        first = t in (0, 21)
        lub = 100 if first else 78
        if fail == "flat" or (fail == "half" and half == 1):
            continue
        if fail == "flutter" and half == 1 and first:
            g.stutter("kick", BASEDRUM, 66, s + t, 5, 96, decay=0.78)
            continue
        shift = 2 if (fail == "late" and half == 1) else 0
        g.drum("kick", s + t + shift, BASEDRUM, lub)
        if fail != "miss":
            g.drum("kick", s + t + shift + dub_off, BASEDRUM, lub * 0.62)
        if first and not (fail == "late" and half == 1):
            g.put("bass", s + t, BASS, 38 + PHASES[phase]["shift"], 94)


def hymn(g, start_bar, phase, params):
    """The plain chant, broken: returns the tick where the break ends (0 when it is not broken)."""
    sh = PHASES[phase]["shift"]
    s = g.start(start_bar)
    cut, vel = params["cut"], params["vel"] + 4 * phase
    skip, flat, stut = set(params.get("skip", ())), set(params.get("flat", ())), set(params.get("stut", ()))
    if phase == 1 and cut < 14:                                   # overclocked: the chant is cut sooner and stutters more
        cut = max(2, cut * 3 // 4)
        stut = stut | set(range(1, cut, 3))
    elif phase == 1:
        stut = stut | {4, 9}
    end = 0
    for index, (t, pitch) in enumerate(HYMN):
        if index >= cut:
            break
        if index in skip:
            continue
        note = pitch - (1 if index in flat else 0) + sh
        level = vel + (8 if index == 0 else 0)
        if index == cut - 1 and cut < 14:                         # the stuck note: struck again and again, then nothing
            end = g.stutter("hymn", BIT, note, s + t, 7, level, decay=0.8)
            if "hym2" in g.layers and start_bar >= 40:
                g.stutter("hym2", IRON_XYLOPHONE, note, s + t, 7, level * 0.7, decay=0.8)
        elif index in stut:
            g.stutter("hymn", BIT, note, s + t, 3, level, decay=0.8)
            g.put("hymn", s + t + 4, BIT, note, level * 0.6)
        else:
            g.put("hymn", s + t, BIT, note, level)
            g.put("hymn", s + t + 3, BIT, note, level * 0.6)
            if start_bar >= 40 and start_bar < 56:
                g.put("hym2", s + t, IRON_XYLOPHONE, note, level * 0.7)
                g.put("hym2", s + t + 3, IRON_XYLOPHONE, note, level * 0.4)
    return end


def strike(g, tick, sh, big):
    """The blow that ends a dropout: the heart, the bass, a snare, the alarm bell, the hum."""
    g.drum("kick", tick, BASEDRUM, 100, over=True)
    g.put("bass", tick, BASS, 38 + sh, 100, over=True)
    g.drum("snare", tick, SNARE, 80 if big else 56, over=True)
    g.put("bell", tick, COW_BELL, 74 + sh, 50 if big else 36, over=True)
    g.put("hum", tick, DIDGERIDOO, 38 + sh, 90, over=True)


def compose(phase):
    sh = PHASES[phase]["shift"]
    g = Grid(TPS, BAR, BARS, SPECS)
    rng = Rng(31 + phase)
    root = 38 + sh
    # ---- the intro: the tail of a bar (22 ticks), then one whole bar: a boot that stumbles
    g.stutter("glitch", BIT, 77 + sh - 1, 8, 2, 30)
    g.stutter("glitch", BIT, 72 + sh, 15, 3, 26)
    g.drum("kick", g.start(-1), BASEDRUM, 64)
    g.drum("kick", g.start(-1) + 3, BASEDRUM, 40)
    g.drum("kick", g.start(-1) + 21, BASEDRUM, 72)
    g.drum("kick", g.start(-1) + 24, BASEDRUM, 46)
    g.restrike("hum", DIDGERIDOO, root, g.start(-1), BAR, 3, 14, 52)
    for t in range(0, BAR, 6):
        g.drum("hat3", g.start(-1) + t, HAT, 14 + t // 3)
    g.put("hymn", g.start(-1) + 12, BIT, 62 + sh, 34)
    g.put("hymn", g.start(-1) + 15, BIT, 62 + sh, 20)
    g.stutter("hymn", BIT, 65 + sh, g.start(-1) + 18, 6, 36, decay=0.8)
    for index, t in enumerate(ARP):
        if index % 2 == 0 and index >= 3:
            g.put("arp", g.start(-1) + 21 + 3 * index - 18, IRON_XYLOPHONE, t + sh, 26 + 4 * index)
    g.clicks("roll", HAT, 66, 63, [1, 1, 1, 1, 2, 2, 3, 4, 5], 14, 62)
    # ---- the loop
    for k in range(BARS):
        name, i, count = section_of(SECTIONS, k)
        s = g.start(k)
        starts = groups(k, phase, name)
        fail = FAILS[phase][name][i % len(FAILS[phase][name])] if name != "B" else FAILS[phase]["B"][i]
        heart_bar(g, s, starts, phase, fail)
        # the footstep kick on the groups that are not the heart (phase 1; phase 2 has the heart on every group)
        if phase == 0 and (name == "PEAK" or (name == "A2" and i >= 8)):
            for t in (starts[1], starts[2], starts[4], starts[5]):
                if fail not in ("flat",):
                    g.drum("kick", s + t, BASEDRUM, 50 + (22 if name == "PEAK" else 0))
        # the hum, a tritone on the last group of every fourth bar
        base = HUM_VEL[name]
        if name == "B" and i >= 4:
            base = 48 + (i - 3) * 8
        if name == "CALM":
            base = 38 - 2 * i
        for t in range(0, BAR, 3):
            note = root + (6 if (t >= starts[5] and k % 4 == 3 and name in ("A2", "PEAK")) else 0)
            g.put("hum", s + t, DIDGERIDOO, note, base + (10 if t in starts else 0))
        # the bass walks in the peak (D Eb D G# D Eb), once a group
        if name == "PEAK":
            for index, t in enumerate(starts):
                g.put("bass", s + t, BASS, root + (0, 1, 0, 6, 0, 1)[index], 90)
        elif name == "A2" and i >= 8:
            g.put("bass", s + starts[3], BASS, root, 82)
        # the snare on the second and the fifth group; a flam in the peak
        snare_on = (name == "PEAK") or (phase == 0 and name == "A2" and i >= 8) or (phase == 1 and (name == "A2" or (name == "A" and i >= 8)))
        if snare_on:
            for t in (starts[1], starts[4]):
                g.drum("snare", s + t, SNARE, 66 + (14 if name == "PEAK" else 0))
                if name == "PEAK":
                    g.drum("snare", s + t - 1, SNARE, 30)
            if name == "PEAK" and i >= 4:
                for t in (starts[2], starts[5]):
                    g.drum("snare", s + t, SNARE, 44)
        # the factory metal: hats on the eighths (a dropped one now and then), the bell of the alarm, a low clank
        if name == "A" and i < 4 or name == "CALM":
            for t in starts:
                g.drum("hat3", s + t, HAT, 30 if t in (0, 21) else 22)
        elif name == "B":
            for t in range(0, BAR, 6):
                g.drum("hat3", s + t, HAT, 22 + (10 if t in starts else 0))
        else:
            for index, t in enumerate(range(0, BAR, 3)):
                if rng.chance(6 if name == "A" else 3):
                    continue
                g.drum("hat3", s + t, HAT, 32 if t in starts else 18)
        hat2 = (phase == 1 and ((name == "A" and i >= 8) or name in ("A2", "PEAK"))) or (phase == 0 and name == "PEAK" and i >= 8)
        if hat2:
            dense = phase == 1 and name == "PEAK" and i >= 8
            for t in range(0, BAR):
                if (dense or t % 2 == 1) and t % 3:
                    g.drum("hat2", s + t, HAT, 10 + 4 * (t % 3) + 4 * (name == "PEAK"))
        bells = None
        if name == "PEAK" or (name == "A2" and i >= 4):
            bells = starts if name == "PEAK" else (starts[0], starts[3])
        elif name == "A" and i >= 8:
            bells = (starts[0],)
        if bells:
            for index, t in enumerate(bells):
                g.put("bell", s + t, COW_BELL, (74 if t in (0, 21) else 80) + sh, 34 + (10 if name == "PEAK" else 0) + 4 * phase)
        if name == "PEAK" and i >= 4:
            for t in (starts[0], starts[3]):
                g.put("clank", s + t, IRON_XYLOPHONE, 54 + sh, 70)
                g.put("clank", s + t + 6, IRON_XYLOPHONE, 55 + sh, 44)
        # the machine's loop: seven eighths of iron xylophone in each half-bar, dropped and stuttered
        halves = ()
        if name == "A":
            halves = (0,) if 4 <= i < 8 else ((0, 1) if i >= 8 else ())
        elif name in ("A2", "PEAK"):
            halves = (0, 1)
        elif name == "B":
            halves = (0, 1) if i in (0, 1, 4, 5, 6, 7) else (0,)
        elif name == "CALM":
            halves = (0,) if i < 4 else ()
        lift = (i - 3) if (name == "B" and i >= 4) else 0           # the bridge climbs a half step a bar
        glitch_chance = {"A": 6, "A2": 12, "B": 10, "PEAK": 14, "CALM": 8}[name] + 6 * phase
        for half in halves:
            for step, pitch in enumerate(ARP):
                t = s + 21 * half + 3 * step
                if rng.chance({"A": 14, "A2": 10, "B": 18, "PEAK": 5, "CALM": 30}[name]):
                    continue
                note = pitch + sh + lift
                vel = {"A": 36, "A2": 44, "B": 40, "PEAK": 58, "CALM": 30}[name] + (10 if step in (0, 3, 5) else 0)
                if rng.chance(glitch_chance):
                    g.stutter("arp", IRON_XYLOPHONE, note, t, 3, vel, decay=0.7)
                else:
                    g.put("arp", t, IRON_XYLOPHONE, note, vel)
                if phase == 1 and name != "CALM":
                    g.put("arp", t + 2, IRON_XYLOPHONE, note + 6 if note + 6 <= 78 else note - 6, vel * 0.55)
        # glitch pings of the 8-bit chip: 1-tick notes struck again and again
        ping_count = {0: {"A": 1 if i % 4 == 3 else 0, "A2": 2, "B": 1 + (i >= 4), "PEAK": 4 + (i >= 8), "CALM": i % 2},
                      1: {"A": (1 + (i >= 4)), "A2": 4, "B": 2, "PEAK": 7 + 2 * (i >= 8), "CALM": 1}}[phase][name]
        for _ in range(ping_count):
            t = s + rng.below(BAR)
            g.stutter("glitch", BIT, rng.pick([70, 72, 74, 75, 77]), t, 1 + rng.below(4), 34 + rng.below(16) + 6 * (name == "PEAK"), decay=0.75)
    for tick in list(g.layers["bass"].notes):
        g.layers["hum"].notes.pop(tick, None)
    # ---- the hymn, broken in a different way every time
    cuts = []
    for start_bar, params in HYMNS:
        end = hymn(g, start_bar, phase, params)
        if end:
            cuts.append((end + 1, 5 if start_bar >= 40 else 8))
    # ---- the bridge: a roll over the whole bar 38, then the stop in bar 39
    g.clicks("roll", SNARE, 66, g.start(38) + BAR - 1, [1, 1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 6], 18, 96)
    for bar in (15, 31):                                                # a short roll before the next block
        g.clicks("roll", SNARE, 66, g.start(bar) + BAR - 1, [1, 1, 1, 2, 2, 3, 3], 22, 70)
    # ---- the dropouts: every layer is cut, then the blow
    for bar, tick, length in DROPS[phase]:
        a = g.start(bar) + tick
        g.mute(a, a + length)
        strike(g, a + length, sh, False)
    for a, length in cuts:
        g.mute(a, a + length)
        strike(g, a + length, sh, False)
    g.mute(g.start(39) + 18, g.start(40))
    g.clicks("roll", HAT, 66, g.start(40) - 1, [1, 1, 2, 2, 3, 4, 6], 24, 80, over=True)
    # ---- blows at the start of each block, the peak with every sound, and the way back into the loop
    for first, big in ((0, False), (16, False), (32, False), (40, True), (56, False)):
        strike(g, g.start(first), sh, big)
    g.put("hym2", g.start(40), IRON_XYLOPHONE, 62 + sh, 70, over=True)
    g.clicks("roll", HAT, 66, g.length - 1, [1, 1, 1, 1, 2, 2, 3, 4, 5], 14, 70, over=True)
    return g


TITLES = ("Holy Circuit (Boss010)", "Holy Circuit, Overclocked (Boss010)")
FILES = ("circuit_phase1.nbs", "circuit_phase2.nbs")
DESCRIPTIONS = (
    "Boss, phase 1, class 3 (inorganic fear and erosion): 200 BPM in 7/4 (3+2+2 | 3+2+2 or 2+2+3), factory metal, a heart that fails, 8-bit glitches, a plain chant that the machine breaks every time in a new way.",
    "Boss, phase 2: a half step up, the heart on every group, the groups change each bar, a shuffled second note after each machine note, hat on every tick in the peak, more glitches, the chant cut sooner. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, 64 + first * BAR, 64 + (first + count) * BAR) for name, first, count in SECTIONS]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks)


if __name__ == "__main__":
    main()
