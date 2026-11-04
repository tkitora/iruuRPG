#!/usr/bin/env python3
"""Makes reaper_phase1.nbs and reaper_phase2.nbs: "Silence of the Scythe (Boss005)" and "Silence of the Scythe, Unleashed (Boss005)", a reaper that is a clockwork, in two phases.

    python3 tools/bgm/make_reaper_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_reaper_bgm.py outdir     writes them somewhere else

CLASS 3, "inorganic fear and erosion" (a machine, an alien, a will-less curse; nothing human). 150 BPM: 10 ticks a second, a beat of 4 ticks, a bar of 5/4 = 20 ticks (3+2), 60 bars of the loop =
126 s. A bar of 20 ticks meets the engine's 16-tick line every 4 bars (80 ticks = 5 lines), so every section is a multiple of 4 bars and the phase switch lands at most 16 ticks (1.6 s) late.
The loop (tick 64) is a bar line: the intro is a silent offset of 4 ticks and three whole bars (4 + 3 x 20 = 64).
Sounds: hat / cow bell / iron xylophone ticks (the clock), the bass drum as a heart that beats on its own cycle of 8 ticks (against the bar of 20: it meets the bar line only every 2 bars; it is
never on the groove), didgeridoo and bass on a B pedal with a tritone, the 8-bit chip for half-step clusters. No tune: a loop of eight notes in a half-step / tritone cell (B C F E B C F E) that ERODES - notes are lost
in a fixed order (the anchor is the last), corroded a half step down before they die, and gained again in other places. The SILENCE IS THE THREAT: windows where every layer is cut
(the silence of the scythe), a dying heart, a clock that ticks faster over nothing before the peak, then a blow.
Phase 2 (Unleashed): a half step up, the heart twice as fast, the 5 turns 3+2 / 2+3 bar by bar, the loop fills (more gained notes, an answer on the cow bell), erosion cycles faster, hat noise, the clock rushes.
Everything is written by what SOUNDS. Nothing is random (cat3_machine.Rng). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat3_machine import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS, BAR, BARS = 10, 20, 60
SECTIONS = [("A", 0, 12), ("A2", 12, 12), ("B", 24, 12), ("PEAK", 36, 16), ("CALM", 52, 8)]
SPECS = {  # layer: (volume, pan)
    "clock": (100, 115), "tock": (100, 70), "heart": (100, 100), "pound": (100, 100), "drone": (100, 100), "bass": (100, 100),
    "ost": (100, 95), "ost2": (100, 120), "snare": (100, 105), "noise": (100, 130), "roll": (100, 105),
    "cl1": (100, 90), "cl2": (100, 110), "cl3": (100, 100),
}
PHASES = [dict(shift=0, hs=1.0, alt=False), dict(shift=1, hs=0.5, alt=True)]
# the loop of eight notes: (tick, pitch, rank); the rank is the order of erosion (0 goes first, 7 - the anchor - last)
CELL = [(0, 59, 7), (2, 60, 3), (6, 65, 5), (8, 64, 1), (12, 59, 6), (14, 60, 2), (16, 65, 4), (18, 64, 0)]
GAIN = [(4, 58), (15, 66), (10, 56), (19, 72)]          # notes the loop gains in other places (cluster against the cell)
# per phase and section: (notes removed per bar - 9 is "none left", notes gained per bar)
ERODE = {
    0: {"A": ([9] * 4 + [6, 6, 5, 5, 4, 4, 5, 4], [0] * 12),
        "A2": ([3, 3, 4, 2, 2, 3, 1, 2, 2, 1, 2, 1], [0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2]),
        "B": ([3, 4, 5, 6, 6, 7, 7, 8, 9, 9, 9, 9], [0] * 12),
        "PEAK": ([0, 0, 0, 0, 0, 1, 0, 1, 1, 2, 1, 2, 2, 3, 3, 4], [3] * 12 + [2, 2, 1, 1]),
        "CALM": ([5, 5, 6, 6, 7, 7, 8, 9], [0] * 8)},
    1: {"A": ([4, 4, 3, 3, 3, 2, 3, 2, 2, 1, 2, 1], [0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2]),
        "A2": ([1, 2, 0, 1, 0, 1, 0, 2, 0, 1, 0, 0], [2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4]),
        "B": ([2, 3, 4, 5, 3, 4, 6, 7, 5, 6, 8, 9], [1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0]),
        "PEAK": ([0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 2, 0, 2, 1, 3, 2], [4] * 16),
        "CALM": ([3, 4, 4, 5, 6, 6, 7, 8], [0] * 8)},
}
HEART_PERIOD = {0: {"A": 8, "A2": 8, "B": 10, "PEAK": 6, "CALM": 9}, 1: {"A": 4, "A2": 4, "B": 5, "PEAK": 3, "CALM": 5}}
HEART_VEL = {"A": (80, 52), "A2": (92, 62), "B": (70, 46), "PEAK": (100, 72), "CALM": (66, 42)}
DRONE_VEL = {"A": 54, "A2": 62, "B": 52, "PEAK": 80, "CALM": 44}
CLOCK_VEL = {"A": (22, 36), "A2": (26, 40), "B": (20, 30), "PEAK": (28, 46), "CALM": (18, 28)}
# the silences of the scythe: (bar, from tick, to the end of the bar)
SILENCES = {0: [(19, 8), (27, 10), (29, 4), (31, 12), (35, 6), (55, 10), (57, 4)], 1: [(19, 12), (27, 14), (35, 6), (57, 8)]}


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


def compose(phase):
    ph = PHASES[phase]
    sh = ph["shift"]
    root = 35 + sh
    g = Grid(TPS, BAR, BARS, SPECS)
    # ---- the intro: 4 silent ticks, then the clock wakes, the heart starts, the pedal rises (ticks 4 .. 63)
    g.drum("clock", g.start(-3), HAT, 30)
    g.drum("clock", g.start(-3) + 10, HAT, 24)
    for t in range(0, BAR, 4):
        g.drum("clock", g.start(-2) + t, HAT, 18 + 2 * t // 4)
    for t in (2, 12):
        g.drum("heart", g.start(-2) + t, BASEDRUM, 56)
        g.drum("heart", g.start(-2) + t + 4, BASEDRUM, 36)
    for t in range(0, BAR, 2):
        g.drum("clock", g.start(-1) + t, HAT, 22 + t // 2)
    for t in (0, 8, 16):
        g.drum("heart", g.start(-1) + t, BASEDRUM, 64 + t)
        g.drum("heart", g.start(-1) + t + 3, BASEDRUM, 44 + t // 2)
    g.restrike("drone", DIDGERIDOO, root, g.start(-1), BAR, 4, 10, 46)
    for t, p in ((8, 59), (12, 59), (14, 60)):
        g.put("ost", g.start(-1) + t, IRON_XYLOPHONE, p + sh, 34 + t)
    g.clicks("noise", HAT, 66, 63, [1, 1, 1, 2, 3, 4], 16, 60)
    # ---- the loop
    for name, first, count in SECTIONS:
        period = HEART_PERIOD[phase][name]
        lub, dub = HEART_VEL[name]
        dub_off = max(1, int(round(period * 0.375)))
        end_tick = g.start(first + count)
        if name == "B":
            end_tick = g.start(34)                  # the heart dies in the bridge: it is not there for the last two bars
        beat = 0
        tick = g.start(first)
        skip = {0: 7, 1: 9}[phase] if name in ("A2", "B") else 0
        while tick < end_tick:
            if not (skip and beat % skip == skip - 1):
                g.drum("heart", tick, BASEDRUM, lub, over=True)
                g.drum("heart", tick + dub_off, BASEDRUM, dub, over=True)
            tick += period + (beat // 3 if name == "B" else 0)       # the heart in the bridge slows down
            beat += 1
    noise_rng = Rng(5 + phase)
    for k in range(BARS):
        name, i, count = section_of(SECTIONS, k)
        s = g.start(k)
        acc = accents(k, ph)
        removed, gained = ERODE[phase][name]
        r, gn = removed[i], gained[i]
        corrode = 0
        if name in ("A2", "PEAK") or (phase == 1 and name in ("A", "B")):
            corrode = [0, 1, 2, 1][(k // 2) % 4] + phase
        # the clock: hat ticks, the accent on the group starts; the last beat of every fourth bar of the A2 is missing
        quiet, loud = CLOCK_VEL[name]
        quiet, loud = quiet + 4 * phase, loud + 4 * phase
        for t in range(0, BAR, 2):
            if name == "A2" and i % 4 == 3 and t >= 16:
                continue
            if name == "B" and i >= 8 and t % 4:
                continue
            g.drum("clock", s + t, HAT, loud if t in acc else quiet)
        if phase == 1 and name == "PEAK" and i >= 8:
            for t in range(1, BAR, 2):                 # the clock rushes: every tick
                g.drum("clock", s + t, HAT, 18 + (6 if t > 12 else 0))
        # tick and tock of the cow bell: the pitch turns over every beat, and with 5 beats the bar begins on the other one every time
        if (name == "A" and i >= 8) or name in ("A2", "PEAK") or (name == "CALM" and i < 4) or (name == "B" and i < 8) or (phase == 1 and name == "A" and i >= 4):
            beats = (0, 4, 8, 12, 16) if name != "B" else (0,)
            for b, t in enumerate(beats):
                high = (k * 5 + b) % 2 == 0
                g.put("tock", s + t, COW_BELL, (84 if high else 78) + sh, 30 + 6 * (name in ("A2", "PEAK")) + 4 * phase + (8 if t in acc else 0))
        # the pedal: re-struck didgeridoo, the last beat of every fourth bar goes down to the tritone
        base = DRONE_VEL[name]
        if name == "B" and i >= 8:
            base = 52 + (i - 8) * 9
        if name == "CALM":
            base = 44 - 2 * i
        for t in range(0, BAR, 4):
            note = root + (6 if (t == 16 and k % 4 == 3 and name in ("A2", "PEAK", "B")) else 0)
            g.put("drone", s + t, DIDGERIDOO, note, base + (8 if t in acc else 0))
        # the bass
        if name == "A" and i >= 8:
            g.put("bass", s + acc[0], BASS, root, 78)
        elif name == "A2" or (phase == 1 and name == "A" and i >= 4):
            g.put("bass", s + acc[0], BASS, root, 84)
            g.put("bass", s + acc[1], BASS, root + 6, 74)
        elif name == "PEAK":
            for b, t in enumerate((0, 4, 8, 12, 16)):
                g.put("bass", s + t, BASS, root + (0, 1, 0, 6, 1)[(k * 5 + b) % 5], 96 if t in acc else 76)
        elif name == "CALM" and i < 4:
            g.put("bass", s + acc[0], BASS, root, 54)
        # the pound: the bass drum on the group starts (it is the heart that is the clock, this is the footstep)
        if name == "PEAK" or (phase == 1 and name == "A2" and i >= 4):
            for t in acc:
                g.drum("pound", s + t, BASEDRUM, 72 + 14 * phase + (10 if t == 0 else 0))
        # the snare: the second beat of each group
        if (phase == 0 and ((name == "A2" and i >= 8) or name == "PEAK")) or (phase == 1 and ((name == "A" and i >= 8) or name == "A2" or name == "PEAK")):
            for t in (acc[0] + 4, acc[1] + 4):
                g.drum("snare", s + t, SNARE, 44 + (18 if name == "PEAK" else 0) + 6 * phase)
            if name == "PEAK":
                g.drum("snare", s + 14 if acc[1] == 12 else s + 18, SNARE, 26)
        # the loop of eight notes, eroding and gaining
        if r < 8:
            vel = {"A": 46, "A2": 56, "B": 44, "PEAK": 68, "CALM": 38}[name] + 4 * phase
            for t, p, rank in CELL:
                if rank < r:
                    continue
                pitch = p - (1 if rank < r + corrode else 0)
                g.put("ost", s + t, IRON_XYLOPHONE, pitch + sh, vel + (10 if t in acc else 0))
                if name in ("A2", "PEAK") or (phase == 1 and name in ("A", "B")):
                    if rank % 2 == 0 or phase == 1:
                        g.put("ost2", s + t + 1, COW_BELL, pitch + 12 + sh, vel * 0.5)
        for index in range(min(gn, 4)):
            t, p = GAIN[index]
            g.put("ost", s + t, IRON_XYLOPHONE, p + sh, 40 + 8 * (name == "PEAK") + 4 * phase)
        # hat noise between the ticks
        count_noise = 0
        if phase == 0:
            count_noise = 3 if (name == "PEAK" and 4 <= i < 8) else (5 if (name == "PEAK" and i >= 8) else 0)
        else:
            count_noise = {"A": 2 if i >= 8 else 0, "A2": 3, "B": 0, "PEAK": 8, "CALM": 0}[name]
        odd = list(range(1, BAR, 2))
        for _ in range(count_noise):
            t = odd.pop(noise_rng.below(len(odd)))
            g.drum("noise", s + t, HAT, 14 + noise_rng.below(14) + 4 * phase)
        # the half-step cluster of the 8-bit chip, hit on the group starts: B C F
        stab_from = {0: {"PEAK": 4}, 1: {"A2": 4, "PEAK": 0}}[phase].get(name)
        if stab_from is not None and i >= stab_from:
            for t in acc:
                for layer, p in (("cl1", 59), ("cl2", 60), ("cl3", 65)):
                    if layer == "cl3" and not (t == 0 and k % 2 == 0):      # the tritone is only in the first blow of every second bar (8 notes at one tick at most)
                        continue
                    g.put(layer, s + t, BIT, p + sh, 66 + (10 if t == 0 else 0))
                    g.put(layer, s + t + 2, BIT, p + sh, 36)
    for tick in list(g.layers["bass"].notes):                     # where the bass strikes, the pedal leaves it the low note
        g.layers["drone"].notes.pop(tick, None)
    # ---- the bridge: a cluster that swells from nothing, a roll, then the stop before the peak
    for layer, p, begin in (("cl1", 71, 32), ("cl2", 72, 33), ("cl3", 77, 34)):
        length = (35 - begin) * BAR + 6
        g.restrike(layer, BIT, p + sh, g.start(begin), length, 2, 8, 62 + 4 * (begin - 32))
    for bar in (11, 23):                                  # a short roll before the next block
        g.clicks("roll", SNARE, 66, g.start(bar) + 19, [1, 1, 1, 2, 2], 22, 66)
    g.clicks("roll", SNARE, 66, g.start(34) + 19, [1, 1, 1, 1, 2, 2, 3, 3, 4, 4], 18, 92)
    # ---- the silences of the scythe
    for bar, from_tick in SILENCES[phase]:
        g.mute(g.start(bar) + from_tick, g.start(bar + 1))
        if bar != 35:
            strike(g, g.start(bar + 1), root, False)
    g.clicks("clock", HAT, 66, g.start(35) + 19, [1, 1, 2, 3, 4], 24, 70, over=True)
    # ---- blows at the start of each block, and the way back into the loop
    for first, big in ((0, False), (12, False), (24, False), (36, True), (52, False)):
        strike(g, g.start(first), root, big)
    for layer, p in (("cl1", 59), ("cl2", 60), ("cl3", 65)):
        g.put(layer, g.start(36), BIT, p + sh, 90, over=True)
        g.put(layer, g.start(36) + 2, BIT, p + sh, 60, over=True)
    g.clicks("noise", HAT, 66, g.length - 1, [1, 1, 1, 2, 2, 3, 4], 20, 72, over=True)
    return g


TITLES = ("Silence of the Scythe (Boss005)", "Silence of the Scythe, Unleashed (Boss005)")
FILES = ("reaper_phase1.nbs", "reaper_phase2.nbs")
DESCRIPTIONS = (
    "Reaper fight, phase 1, class 3 (inorganic fear and erosion): 150 BPM in 5/4 (3+2), a clockwork of hat / cow bell / iron xylophone, a heart on its own 8-tick cycle, a pedal on B with a tritone, a loop of half steps that erodes, silences that cut every layer.",
    "Reaper fight, phase 2: a half step up, the heart twice as fast, the 5 turns 3+2 / 2+3, the loop fills and erodes faster, hat noise, a clock that rushes. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, g_start, g_start + count * BAR) for name, first, count in SECTIONS for g_start in [64 + first * BAR]]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks)


if __name__ == "__main__":
    main()
