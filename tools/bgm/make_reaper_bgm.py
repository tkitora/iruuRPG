#!/usr/bin/env python3
"""Makes reaper_phase1.nbs and reaper_phase2.nbs: "Silence of the Scythe (Boss005)" and "Silence of the Scythe, Unleashed (Boss005)", a reaper that is a clockwork, in two phases.

    python3 tools/bgm/make_reaper_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_reaper_bgm.py outdir     writes them somewhere else

CLASS 3, "inorganic fear and erosion" (a machine, an alien, a will-less curse; nothing human). Rebuilt on the "16th-note machine gun" guide (t276): base tempo 150 BPM = 10 ticks a second, the song tick
is a SIXTEENTH, a beat of 4 ticks, a bar of 5/4 = 20 ticks (3+2), 60 bars of the loop = 126 s. A bar of 20 ticks meets the engine's 16-tick line every 4 bars (80 ticks = 5 lines), so every section is a
multiple of 4 bars. The loop (tick 64) is a bar line: the intro is a silent offset of 4 ticks and three whole bars (4 + 3 x 20 = 64).
THE RHYTHM STRUCTURE (the beat stays 150, the accompaniment is subdivided): intro = the bass only as soft QUARTER roots; A = the bass strikes EIGHTHS on the root (tight, each step heavy, feels like 300);
A2 = eighths, growing, with sixteenth double hits at its end; B = eighths with sudden sixteenth double hits and 3-tick runs (a triplet-like figure across the beat), the bass line walking up and down
(B C B F G F C B ...) and climbing, an irregular surge; the bridge is cut by the silence of the scythe; PEAK = the bass is the SIXTEENTH MACHINE GUN (every tick, feels like 600), the hat and the
bass-drum pound drive in sixteenths too, and over it a line of LONG notes: the held dissonance (bit + pling a half step apart, re-struck every 4 ticks, whole-bar notes) that is the loop cell B C F E slowed
down to half and whole notes - no tune, a sustained cluster over the gun. CALM = eighths, quarters, nothing (the way back to the quiet intro).
Sounds: hat / cow bell / iron xylophone ticks (the clock), the bass drum as a heart that beats on its own cycle of 8 ticks (against the bar of 20: it meets the bar line only every 2 bars; it is
never on the groove), bass (the gun) and didgeridoo (the pedal), the 8-bit chip and pling for the held dissonance. A loop of eight notes in a half-step / tritone cell (B C F E B C F E) that ERODES - notes are
lost in a fixed order (the anchor is the last), corroded a half step down before they die, and gained again in other places. The SILENCE IS THE THREAT: windows where every layer is cut
(the silence of the scythe), a dying heart, a clock that ticks faster over nothing before the peak, then a blow.
Phase 2 (Unleashed): a half step up, the heart twice as fast, the 5 turns 3+2 / 2+3 bar by bar, the machine gun comes EARLIER (A2, B), the held line is there from A2 on and gains a third voice (iron xylophone
a tritone away) in the peak, the pound doubles, hat noise, the loop fills and erodes faster.
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
    "hold1": (100, 95), "hold2": (100, 105), "hold3": (100, 115),
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
CLOCK_VEL = {"A": (22, 36), "A2": (26, 40), "B": (20, 30), "PEAK": (24, 44), "CALM": (18, 28)}
# the silences of the scythe: (bar, from tick, to the end of the bar)
SILENCES = {0: [(19, 8), (27, 10), (29, 4), (31, 12), (35, 6), (55, 10), (57, 4)], 1: [(19, 12), (27, 14), (35, 6), (57, 8)]}

# ---- the bass: what rhythm it plays in which bar (mode, restlessness), see cat3_machine.gun_ticks
#      n = none, q = quarters, e = eighths, m = eighths + sixteenth doubles + 3-tick runs, s = the sixteenth machine gun
def bass_plan(phase, name, i):
    if phase == 0:
        if name == "A":
            return ("q", 0) if i < 4 else ("e", 0)
        if name == "A2":
            return ("e", 0) if i < 8 else ("m", 0.4 + 0.15 * (i - 8))
        if name == "B":
            if i < 8:
                return ("m", 0.2 + 0.1 * i)
            return ("m", 1.0) if i < 10 else ("s", 1)
        if name == "PEAK":
            return ("s", 1)
        return ("e", 0) if i < 2 else (("q", 0) if i < 5 else ("n", 0))
    if name == "A":
        return ("e", 0) if i < 2 else ("m", 0.2 + 0.05 * i)
    if name == "A2":
        return ("s", 1) if (i >= 6 and i % 4 != 3) else ("m", 0.7)
    if name == "B":
        if i < 4:
            return ("m", 0.8)
        if i < 8:
            return ("s", 1)
        return ("m", 1.0) if i < 10 else ("s", 1)
    if name == "PEAK":
        return ("s", 1)
    return ("e", 0) if i < 4 else (("q", 0) if i < 6 else ("n", 0))


SMALL = [0, 0, 1, 0, 6]
WALK = [0, 1, 0, 6, 7, 6, 1, 0, 3, 1, 6, 7]
GUN = [0, 1, 0, 6, 1]
BASS_VEL = {"A": 58, "A2": 70, "B": 70, "PEAK": 80, "CALM": 50}


def bass_offset(name, mode, k, b, i):
    n = k * 5 + b
    if name == "A":
        return SMALL[n % 5] if mode in ("m", "s") else 0
    if name == "A2":
        return SMALL[n % 5] if mode in ("m", "s") else (6 if (b == 4 and k % 4 == 3) else 0)
    if name == "B":
        return WALK[n % 12] + min(4, i // 2)
    if name == "PEAK":
        return GUN[n % 5]
    return 0


def bass_velocity(name, mode, phase, i, t, acc):
    v = BASS_VEL[name] + 4 * phase
    if name == "A":
        v += min(i, 8)
    elif name == "B":
        v += int(i * 1.5)
    elif name == "CALM":
        v -= 3 * i
    if mode == "q":
        v -= 12
    if t in acc:
        v += 16
    elif t % 4 == 0:
        v += 4
    elif t % 2 == 1:
        v -= 12 if mode != "s" else 8
    return max(1, min(100, v))


# ---- the held dissonance: a line of long notes (the cell B C F E slowed down), (pitches, ticks) per 4 bars
LINES = [
    ([71, 72, 77, 76, 70, 71], [12, 8, 20, 12, 8, 20]),
    ([71, 77, 72, 76, 70, 65], [8, 12, 20, 8, 12, 20]),
    ([72, 71, 77, 76, 70, 71], [20, 12, 8, 20, 12, 8]),
    ([70, 71, 72, 77, 76, 77], [8, 12, 20, 12, 8, 20]),
]


def line_of(index):
    pitches, ticks = LINES[index % 4]
    return list(zip(pitches, ticks))


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


def hold_voices(phase, name, i0=0):
    voices = [("hold1", BIT, 0, 0, 1.0), ("hold2", PLING, 1, 2, 0.85)]
    if phase == 1 and name == "PEAK":
        voices.append(("hold3", IRON_XYLOPHONE, -6, 1, 0.7))
    return voices


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
        g.drum("heart", g.start(-2) + t, BASEDRUM, 56)
        g.drum("heart", g.start(-2) + t + 4, BASEDRUM, 36)
    for t in range(0, BAR, 4):
        g.drum("clock", g.start(-1) + t, HAT, 24 + t // 2)
    for t in (0, 8, 16):
        g.drum("heart", g.start(-1) + t, BASEDRUM, 64 + t)
        g.drum("heart", g.start(-1) + t + 3, BASEDRUM, 44 + t // 2)
    g.restrike("drone", DIDGERIDOO, root, g.start(-1), BAR, 4, 10, 46)
    for bar, base in ((-2, 26), (-1, 36)):
        for t in range(0, BAR, 4):
            g.put("bass", g.start(bar) + t, BASS, root, base + 2 * t // 4)
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
        mode, restless = bass_plan(phase, name, i)
        sixteenth = name == "PEAK" or (phase == 1 and ((name == "B" and i < 8) or (name == "A2" and i >= 6)))
        # the clock: hat ticks (eighths; in the peak - and in phase 2 from the machine gun on - sixteenths), the accent on the group starts; the last beat of every fourth bar of the A2 is missing
        quiet, loud = CLOCK_VEL[name]
        quiet, loud = quiet + 4 * phase, loud + 4 * phase
        for t in range(0, BAR, 1 if sixteenth else 2):
            if name == "A2" and i % 4 == 3 and t >= 16 and not sixteenth:
                continue
            if name == "B" and i >= 8 and t % 4:
                continue
            g.drum("clock", s + t, HAT, loud if t in acc else (quiet if t % 2 == 0 else quiet * 0.6) if t % 4 else quiet + 4)
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
        # the bass: quarters / eighths / eighths with doubles / the sixteenth machine gun
        for t in gun_ticks(mode, BAR, k, restless, gun_rng):
            off = bass_offset(name, mode, k, t // 4, i)
            g.put("bass", s + t, BASS, root + off, bass_velocity(name, mode, phase, i, t, acc))
        # the pound: the bass drum on the group starts (it is the heart that is the clock, this is the footstep); in the peak it doubles
        if name == "PEAK" or (phase == 1 and name == "A2" and i >= 4):
            for t in acc:
                g.drum("pound", s + t, BASEDRUM, 72 + 14 * phase + (10 if t == 0 else 0))
            if name == "PEAK" and (phase == 1 or i >= 8):
                for t in acc:
                    g.drum("pound", s + t + 2, BASEDRUM, 56 + 8 * phase)
        # the snare: the second beat of each group
        if (phase == 0 and ((name == "A2" and i >= 8) or name == "PEAK")) or (phase == 1 and ((name == "A" and i >= 8) or name == "A2" or name == "PEAK")):
            for t in (acc[0] + 4, acc[1] + 4):
                g.drum("snare", s + t, SNARE, 44 + (18 if name == "PEAK" else 0) + 6 * phase)
            if name == "PEAK":
                g.drum("snare", s + 14 if acc[1] == 12 else s + 18, SNARE, 26)
        # the loop of eight notes, eroding and gaining
        if r < 8:
            vel = {"A": 46, "A2": 56, "B": 44, "PEAK": 60, "CALM": 38}[name] + 4 * phase
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
        # hat noise between the ticks (not where the hat already plays every tick)
        count_noise = 0
        if not sixteenth:
            count_noise = {"A": 2 if i >= 8 else 0, "A2": 3, "B": 0, "PEAK": 0, "CALM": 0}[name] if phase == 1 else 0
        odd = list(range(1, BAR, 2))
        for _ in range(count_noise):
            t = odd.pop(noise_rng.below(len(odd)))
            g.drum("noise", s + t, HAT, 14 + noise_rng.below(14) + 4 * phase)
    for tick in list(g.layers["bass"].notes):                     # where the bass strikes, the pedal leaves it the low note (in the peak the pedal stays on the group starts)
        name, i, count = section_of(SECTIONS, max(0, (tick - 64) // BAR))
        if tick >= 64 and name != "PEAK":
            g.layers["drone"].notes.pop(tick, None)
        elif tick >= 64 and (tick - g.start(max(0, (tick - 64) // BAR))) not in accents((tick - 64) // BAR, ph):
            g.layers["drone"].notes.pop(tick, None)
    # ---- the held dissonance: long notes over the machine gun (phase 1: only in the peak; phase 2: from the end of the A2, quieter before the peak)
    plans = [(36 + 4 * rep, "PEAK", 66, 52, rep) for rep in range(4)]
    plans.append((52, "CALM", 40, 22, 0))
    if phase == 1:
        plans += [(20, "A2", 36, 26, 1), (24, "B", 48, 38, 2), (28, "B", 56, 44, 3)]
    for bar, kind, v_from, v_to, index in plans:
        voices = hold_voices(phase, kind)
        if kind == "CALM":
            voices = voices[:1]
        held_line(g, [(n, ins, iv, off, lv) for n, ins, iv, off, lv in voices], [(p + sh, t) for p, t in line_of(index)], g.start(bar), v_from + 4 * phase, v_to + 4 * phase)
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
    "Reaper fight, phase 1, class 3 (inorganic fear and erosion): 150 BPM in 5/4 (3+2), bass quarters > eighths > eighths with doubles > a sixteenth machine gun, a clockwork of hat / cow bell / iron xylophone, a heart on its own 8-tick cycle, a loop of half steps that erodes, a held bit + pling dissonance of long notes over the gun, silences that cut every layer.",
    "Reaper fight, phase 2: a half step up, the machine gun comes earlier, the held dissonance from the A2 on with a third voice, the heart twice as fast, the 5 turns 3+2 / 2+3, the loop fills and erodes faster. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, g_start, g_start + count * BAR) for name, first, count in SECTIONS for g_start in [64 + first * BAR]]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks, bass_layers=("bass",), keep_layers=("hold1", "hold2", "hold3"))


if __name__ == "__main__":
    main()
