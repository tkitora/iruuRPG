#!/usr/bin/env python3
"""Makes circuit_phase1.nbs and circuit_phase2.nbs: "Holy Circuit (Boss010)" and "Holy Circuit, Overclocked (Boss010)", a holy machine that has run away, in two phases.

    python3 tools/bgm/make_circuit_bgm.py            writes both into src/main/resources/bgm/
    python3 tools/bgm/make_circuit_bgm.py outdir     writes them somewhere else

CLASS 3, "inorganic fear and erosion", the other face of it: not a cold clock (that is the reaper, Boss005: a 5/4 clockwork on a plain BASS gun, a slow heart, a dissonant held cluster) but a runaway AI,
factory metal and a prayer that the machine keeps overwriting. Rebuilt on the "16th-note machine-gun" guide (t276): base tempo 165 BPM = 11 ticks a second (the song tick is a sixteenth, about 1.8 server
ticks, so the sixteenths wobble a little: they are used at full only in the PEAK), a beat of 4 ticks, the bar is 7/4 = 28 ticks grouped 3+2+2 / 2+2+3 / 2+3+2 beats (ticks 0 12 20 | 0 8 16 | 0 8 20).
48 bars of the loop = 1344 ticks, with the intro 128 s. A 28-tick bar meets the engine's 16-tick line every 4 bars (112 ticks = 7 lines), so every section is a multiple of 4 bars. The loop (tick 64) is a bar
line: the intro is the tail of a bar (8 ticks) and two whole bars (8 + 56 = 64).
THE RHYTHM STRUCTURE (the beat stays 165, the accompaniment is subdivided): intro = the bass only as soft quarter roots; A = the DIDGERIDOO gun strikes eighths on D ("zuku-zuku" chug) and sways by a half step;
A2 = eighths growing, then eighths with sixteenth double hits (zu-ku) and 3-tick runs; B = the same restless, the bass line SWAYING up and down (D Eb G A Bb ... a miyako-bushi colour) and climbing a half step
every two bars, an irregular surge, the bridge a roll and a stop; PEAK = the gun is the SIXTEENTH machine gun (28 notes a bar) on didgeridoo with a BASS sub on every group start, hat and bass drum
drive in sixteenths, an iron-xylophone sequencer runs in eighths, and over it a CHANT of LONG notes (half and whole notes of the 8-bit chip re-struck every 4 ticks, an iron xylophone a beat later): the
prayer of the old plain hymn, slowed down to 4 bars, that the machine breaks in a different way each time (a stuck note, a flat note, a stutter) and then drops every layer for a few ticks and comes back with a blow.
Sounds: bass drum (a heart that FAILS: lub-dub, a late beat, a missed beat, a flutter of five hits on consecutive ticks, a heart that stops), snare / hat / cow bell / iron xylophone as factory metal,
the 8-bit chip for glitches (the same note struck again and again on consecutive ticks) and for the CHANT, didgeridoo and bass for the weight. The sequencer is seven steps of iron xylophone (D F A G# A F E)
that is dropped and stuttered by chance.
Phase 2 (Overclocked): a half step up (E-flat), the heart tachycardic (a lub-dub on every group), the groups change every bar (3+2+2 / 2+2+3 / 2+3+2), the machine gun comes EARLIER (the second half of the A2, the B),
a second shuffled note after every note of the sequencer (a tritone higher), hat on every tick from the A2 on, glitch pings two or three times as many, the chant cut sooner and heard from the A on.
Everything is written by what SOUNDS. Nothing is random (cat3_machine.Rng). The design record is in docs/bgm-songs.md.
"""
import sys
from pathlib import Path

from cat3_machine import *  # noqa: F401,F403

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/bgm"

TPS, BAR, BARS = 11, 28, 48
SECTIONS = [("A", 0, 8), ("A2", 8, 8), ("B", 16, 8), ("PEAK", 24, 16), ("CALM", 40, 8)]
SPECS = {  # layer: (volume, pan)
    "kick": (100, 100), "bass": (100, 100), "gun": (100, 100), "snare": (100, 105), "roll": (100, 105), "hat": (100, 120),
    "bell": (100, 70), "arp": (100, 95), "clank": (100, 85), "hymn": (100, 110), "hym2": (100, 90), "glitch": (100, 130),
}
PHASES = [dict(shift=0), dict(shift=1)]
X = [0, 12, 20]     # 3+2+2 beats
Y = [0, 8, 16]      # 2+2+3
Z = [0, 8, 20]      # 2+3+2
ARP = [62, 65, 69, 68, 69, 65, 64]                       # D F A G# A F E, seven steps
# the chant, four bars of long notes: (pitch, ticks) - D Eb G A Bb is the colour (a half step over the root, the fourth, the fifth)
CHANTS = [
    [(74, 16), (75, 8), (74, 8), (70, 16), (69, 16), (67, 8), (69, 8), (74, 16), (75, 8), (74, 8)],
    [(74, 16), (75, 8), (77, 8), (75, 16), (74, 16), (70, 8), (69, 8), (74, 16), (77, 8), (75, 8)],
    [(62, 16), (63, 8), (62, 8), (58, 16), (57, 16), (55, 8), (57, 8), (62, 16), (63, 8), (62, 8)],
    [(74, 16), (75, 8), (74, 8), (70, 16), (69, 16), (67, 8), (69, 8), (75, 16), (77, 16)],
]
# (first bar of the 4-bar chant, which line, loudness, how it is broken)
CHANT_PLAN = {
    0: [(8, 0, 30, dict(cut=3)), (12, 1, 36, dict(cut=6)), (16, 0, 40, dict(cut=8, flat=(2,))), (20, 2, 44, dict(cut=9, stut=(4,))),
        (24, 0, 62, dict()), (28, 1, 64, dict(flat=(5,), stut=(3,))), (32, 2, 66, dict(cut=8)), (36, 3, 68, dict()),
        (40, 0, 34, dict(cut=4)), (44, 2, 28, dict(cut=2))],
    1: [(4, 0, 30, dict(cut=3)), (8, 1, 36, dict(cut=5)), (12, 0, 40, dict(cut=8)), (16, 2, 44, dict(cut=7, flat=(2,))), (20, 1, 48, dict(cut=9, stut=(4,))),
        (24, 0, 62, dict(stut=(1,))), (28, 1, 66, dict(flat=(5,), stut=(3, 7))), (32, 2, 68, dict(cut=7)), (36, 3, 70, dict(stut=(2,))),
        (40, 0, 34, dict(cut=3)), (44, 2, 28, dict(cut=2))],
}
DROPS = {  # (bar, tick of the bar, ticks of nothing): every layer is cut
    0: [(5, 18, 2), (7, 20, 3), (10, 12, 2), (11, 22, 3), (13, 16, 3), (14, 6, 2), (15, 20, 4), (17, 14, 2), (19, 20, 3), (21, 6, 3),
        (27, 14, 2), (33, 22, 2), (38, 10, 2), (41, 20, 4), (43, 10, 5), (45, 16, 6)],
    1: [(3, 14, 2), (6, 10, 2), (9, 20, 2), (10, 6, 2), (12, 22, 2), (13, 12, 2), (14, 4, 2), (15, 18, 3), (17, 8, 2), (18, 20, 2), (20, 12, 2), (21, 6, 2),
        (26, 20, 2), (29, 12, 2), (31, 24, 2), (34, 6, 2), (36, 18, 2), (38, 8, 2), (41, 20, 3), (43, 10, 3), (45, 16, 5)],
}
FAILS = {0: {"A": ["n", "n", "n", "miss"], "A2": ["n", "n", "late", "n", "miss", "n", "flutter", "half"], "B": ["half", "n", "miss", "half", "flutter", "half", "miss", "flat"],
             "PEAK": ["n", "flutter", "n", "late", "n", "flutter", "miss", "flutter"], "CALM": ["miss", "n", "half", "n", "miss", "half", "flat", "flat"]},
         1: {"A": ["n", "n", "miss", "n"], "A2": ["n", "late", "n", "miss", "flutter", "n", "half", "flutter"], "B": ["half", "flutter", "miss", "half", "flutter", "half", "flutter", "flat"],
             "PEAK": ["flutter", "n", "flutter", "late", "flutter", "miss", "flutter", "flutter"], "CALM": ["miss", "n", "half", "flutter", "miss", "half", "flat", "flat"]}}


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
        return [Y, Z, Y, X][k % 4]
    if name == "PEAK":
        return [X, Y, Z, Y][k % 4]
    return X


# ---- the bass: (mode, restlessness) per bar, see cat3_machine.gun_ticks (n none, q quarters, e eighths, m eighths + sixteenth doubles + 3-tick runs, s sixteenth machine gun)
def bass_plan(phase, name, i):
    if phase == 0:
        if name == "A":
            return ("q", 0) if i < 2 else ("e", 0)
        if name == "A2":
            return ("e", 0) if i < 4 else ("m", 0.4 + 0.1 * (i - 4))
        if name == "B":
            if i < 4:
                return ("m", 0.5 + 0.1 * i)
            return ("m", 1.0) if i < 6 else ("s", 1)
        if name == "PEAK":
            return ("s", 1)
        return ("e", 0) if i < 2 else (("q", 0) if i < 5 else ("n", 0))
    if name == "A":
        return ("e", 0) if i < 2 else ("m", 0.3)
    if name == "A2":
        return ("m", 0.7) if i < 2 else (("m", 1.0) if i % 4 == 3 else ("s", 1))
    if name == "B":
        return ("m", 1.0) if i % 4 == 1 else ("s", 1)
    if name == "PEAK":
        return ("s", 1)
    return ("e", 0) if i < 3 else (("q", 0) if i < 6 else ("n", 0))


SLOW = [0, 0, 0, 1]
A2_PATTERN = [0, 0, 1, 0, 5, 1, 0]
SWAY = [0, 0, 1, 0, 5, 1, 0, 7, 5, 1, 0, 0, 1, 5, 8, 5]
BASS_VEL = {"A": 56, "A2": 68, "B": 70, "PEAK": 80, "CALM": 46}


def bass_offset(name, mode, k, b, i):
    n = k * 7 + b
    if name == "A":
        return SLOW[(b + k) % 4]
    if name == "A2":
        return A2_PATTERN[b] if mode in ("m", "s") else SLOW[(b + k) % 4]
    if name == "B":
        return SWAY[n % 16] + min(4, i // 2)
    if name == "PEAK":
        return SWAY[n % 16]
    return 0


def bass_velocity(name, mode, phase, i, t, starts):
    v = BASS_VEL[name] + 4 * phase
    if name == "A":
        v += min(i, 6)
    elif name == "B":
        v += i
    elif name == "CALM":
        v -= 3 * i
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
        lub = 100 if first else 78
        if fail == "flat" or (fail == "half" and index >= 1):
            continue
        if fail == "flutter" and index >= 1 and (phase == 1 or index == len(beats) - 1):
            g.stutter("kick", BASEDRUM, 66, s + t, 5, 96, decay=0.78)
            continue
        shift = 2 if (fail == "late" and index >= 1) else 0
        g.drum("kick", s + t + shift, BASEDRUM, lub)
        if fail != "miss":
            g.drum("kick", s + t + shift + dub_off, BASEDRUM, lub * 0.62)
        if first:
            g.put("bass", s + t, BASS, 38 + PHASES[phase]["shift"], 94)


def chant(g, bar, line, phase, vel, cut=99, flat=(), stut=()):
    """The prayer in long notes (the 8-bit chip re-struck every 4 ticks, an iron xylophone two ticks later), broken: returns the tick where a stuck note ends (0 when the line is not cut)."""
    sh = PHASES[phase]["shift"]
    s = g.start(bar)
    vel = vel + 4 * phase
    flat, stut = set(flat), set(stut)
    if phase == 1 and cut < len(line):                              # overclocked: the chant is cut sooner and stutters more
        cut = max(2, cut * 3 // 4)
        stut = stut | set(range(1, cut, 3))
    t = 0
    end = 0
    for index, (pitch, length) in enumerate(line):
        if index >= cut:
            break
        note = pitch - (1 if index in flat else 0) + sh
        level = vel + 8
        if index == cut - 1:                                      # the stuck note: struck again and again on consecutive ticks, then nothing
            end = g.stutter("hymn", BIT, note, s + t, 7, level, decay=0.8)
            if bar >= 24:
                g.stutter("hym2", IRON_XYLOPHONE, note, s + t, 7, level * 0.7, decay=0.8)
        else:
            for o in range(0, length, 4):
                v = vel + (8 if o == 0 else 0) - 10 * o / max(1, length - 1)
                g.put("hymn", s + t + o, BIT, note, v)
                if index in stut and o == 0:
                    g.stutter("hymn", BIT, note, s + t, 3, level, decay=0.8, over=True)
                if bar >= 24 and bar < 40 and o + 2 < length:
                    g.put("hym2", s + t + o + 2, IRON_XYLOPHONE, note, v * 0.7)
        t += length
    return end


def strike(g, tick, sh, big):
    """The blow that ends a dropout: the heart, the bass, a snare, the alarm bell, the gun."""
    g.drum("kick", tick, BASEDRUM, 100, over=True)
    g.put("bass", tick, BASS, 38 + sh, 100, over=True)
    g.drum("snare", tick, SNARE, 80 if big else 56, over=True)
    g.put("bell", tick, COW_BELL, 74 + sh, 50 if big else 36, over=True)
    g.put("gun", tick, DIDGERIDOO, 38 + sh, 90, over=True)


def compose(phase):
    sh = PHASES[phase]["shift"]
    g = Grid(TPS, BAR, BARS, SPECS)
    rng = Rng(31 + phase)
    gun_rng = Rng(91 + phase)
    root = 38 + sh
    # ---- the intro: the tail of a bar (8 ticks), then two whole bars: a boot that stumbles, the gun only as soft quarter roots
    g.stutter("glitch", BIT, 77 + sh - 1, 3, 2, 30)
    g.stutter("glitch", BIT, 72 + sh, 26, 3, 26)
    g.drum("kick", g.start(-2), BASEDRUM, 64)
    g.drum("kick", g.start(-2) + 3, BASEDRUM, 40)
    g.drum("kick", g.start(-2) + 16, BASEDRUM, 72)
    g.drum("kick", g.start(-2) + 19, BASEDRUM, 46)
    for bar, base in ((-2, 24), (-1, 36)):
        for t in range(0, BAR, 4):
            g.put("gun", g.start(bar) + t, DIDGERIDOO, root, base + t // 2)
    for t in range(0, BAR, 4):
        g.drum("hat", g.start(-1) + t, HAT, 14 + t // 2)
    g.drum("kick", g.start(-1), BASEDRUM, 70)
    g.drum("kick", g.start(-1) + 3, BASEDRUM, 44)
    g.drum("kick", g.start(-1) + 12, BASEDRUM, 76)
    g.drum("kick", g.start(-1) + 15, BASEDRUM, 48)
    g.put("hymn", g.start(-1) + 8, BIT, 62 + sh, 34)
    g.put("hymn", g.start(-1) + 12, BIT, 62 + sh, 20)
    g.stutter("hymn", BIT, 65 + sh, g.start(-1) + 16, 6, 36, decay=0.8)
    for index, t in enumerate(ARP):
        if index % 2 == 0 and index >= 3:
            g.put("arp", g.start(-1) + 12 + 2 * index, IRON_XYLOPHONE, t + sh, 26 + 4 * index)
    g.clicks("roll", HAT, 66, 63, [1, 1, 1, 1, 2, 2, 3, 4, 5], 14, 62)
    # ---- the loop
    for k in range(BARS):
        name, i, count = section_of(SECTIONS, k)
        s = g.start(k)
        starts = groups(k, phase, name)
        fail = FAILS[phase][name][i % len(FAILS[phase][name])] if name != "B" else FAILS[phase]["B"][i]
        heart_bar(g, s, starts, phase, fail)
        mode, restless = bass_plan(phase, name, i)
        sixteenth = name == "PEAK" or (phase == 1 and ((name == "B") or (name == "A2" and i >= 2)))
        # the footstep kick on the groups, a zu-ku pair of sixteenths (phase 2 has the heart on every group)
        if name == "PEAK" or (name == "A2" and i >= 4):
            for t in (starts[1], starts[2]):
                if fail != "flat" and not (phase == 1 and fail != "n"):
                    g.drum("kick", s + t, BASEDRUM, 50 + (22 if name == "PEAK" else 0))
                    if name == "PEAK" and not (phase == 0 and i < 4):
                        g.drum("kick", s + t + 1, BASEDRUM, 40)
        # the gun: the didgeridoo strikes quarters / eighths / eighths with doubles / the sixteenth machine gun, swaying; the sub bass on the group starts
        for t in gun_ticks(mode, BAR, k, restless, gun_rng):
            off = bass_offset(name, mode, k, t // 4, i)
            g.put("gun", s + t, DIDGERIDOO, root + off, bass_velocity(name, mode, phase, i, t, starts))
        if mode in ("m", "s") or name in ("A2", "B"):
            for t in starts:
                g.put("bass", s + t, BASS, root + bass_offset(name, mode, k, t // 4, i), 86)
        # the snare on the second and the third group; a flam in the peak, ghost sixteenths before the groups
        snare_on = (name == "PEAK") or (phase == 0 and name == "A2" and i >= 4) or (phase == 1 and (name == "A2" or (name == "A" and i >= 4)))
        if snare_on:
            for t in (starts[1], starts[2]):
                g.drum("snare", s + t, SNARE, 66 + (14 if name == "PEAK" else 0))
                if name == "PEAK":
                    g.drum("snare", s + t - 1, SNARE, 30)
            if name == "PEAK" and (phase == 1 or i >= 4) and k % 2 == 1:
                for t in (starts[2] - 3, starts[2] - 2):
                    g.drum("snare", s + t, SNARE, 24)
        # the factory metal: hats on the groups (A), quarters, eighths, and sixteenths in the peak
        if name == "CALM" or (name == "A" and i < 4):
            for t in starts:
                g.drum("hat", s + t, HAT, 30 if t == 0 else 22)
        elif name == "A":
            for t in range(0, BAR, 4):
                g.drum("hat", s + t, HAT, 30 if t in starts else 18)
        elif name == "A2" and not sixteenth:
            for t in range(0, BAR, 2):
                if rng.chance(5):
                    continue
                g.drum("hat", s + t, HAT, 32 if t in starts else (24 if t % 4 == 0 else 16))
        else:
            for t in range(0, BAR, 1 if sixteenth else 2):
                if name == "B" and i == 7 and t >= 14:
                    continue
                g.drum("hat", s + t, HAT, 36 + 8 * (t in starts) + 4 * phase if t % 4 == 0 else (24 + 4 * phase if t % 2 == 0 else 14 + 2 * phase))
        bells = None
        if name == "PEAK" or (name == "A2" and i >= 4):
            bells = starts if name == "PEAK" else (starts[0], starts[2])
        elif name == "A" and i >= 4:
            bells = (starts[0],)
        if bells:
            for index, t in enumerate(bells):
                g.put("bell", s + t, COW_BELL, (74 if t == 0 else 80) + sh, 34 + (10 if name == "PEAK" else 0) + 4 * phase)
        if name == "PEAK" and i >= 4:
            for t in (starts[0], starts[1]):
                g.put("clank", s + t, IRON_XYLOPHONE, 54 + sh, 70)
                g.put("clank", s + t + 6, IRON_XYLOPHONE, 55 + sh, 44)
        # the sequencer: seven steps of iron xylophone (a step a beat in the A; eighths later; sixteenths in the second half of the phase-2 peak), dropped and stuttered
        step = None
        if name == "A":
            step = 4 if i >= 4 else None
        elif name in ("A2", "B", "PEAK"):
            step = 2
        elif name == "CALM":
            step = 4 if i < 4 else None
        if name == "PEAK" and phase == 1 and i >= 8:
            step = 1
        lift = (i - 3) if (name == "B" and i >= 4) else 0           # the bridge climbs a half step a bar
        glitch_chance = {"A": 6, "A2": 12, "B": 10, "PEAK": 14, "CALM": 8}[name] + 6 * phase
        if step:
            for n in range(BAR // step):
                t = s + step * n
                if rng.chance({"A": 14, "A2": 10, "B": 18, "PEAK": 5, "CALM": 30}[name]):
                    continue
                pitch = ARP[(n + (3 if k % 2 else 0)) % 7]
                note = pitch + sh + lift
                vel = {"A": 36, "A2": 44, "B": 40, "PEAK": 54, "CALM": 30}[name] + (10 if n % 7 in (0, 3, 5) else 0) - (14 if step == 1 else 0)
                if rng.chance(glitch_chance) and step > 1:
                    g.stutter("arp", IRON_XYLOPHONE, note, t, 3, vel, decay=0.7)
                else:
                    g.put("arp", t, IRON_XYLOPHONE, note, vel)
                if phase == 1 and name != "CALM" and step == 2:
                    g.put("arp", t + 1, IRON_XYLOPHONE, note + 6 if note + 6 <= 78 else note - 6, vel * 0.55)
        # glitch pings of the 8-bit chip: 1-tick notes struck again and again
        ping_count = {0: {"A": 1 if i % 4 == 3 else 0, "A2": 2, "B": 1 + (i >= 4), "PEAK": 3 + (i >= 8), "CALM": i % 2},
                      1: {"A": (1 + (i >= 4)), "A2": 3, "B": 2, "PEAK": 5 + 2 * (i >= 8), "CALM": 1}}[phase][name]
        for _ in range(ping_count):
            t = s + rng.below(BAR)
            g.stutter("glitch", BIT, rng.pick([70, 72, 74, 75, 77]), t, 1 + rng.below(4), 34 + rng.below(16) + 6 * (name == "PEAK"), decay=0.75)
    # ---- the chant in long notes, broken in a different way every time
    cuts = []
    for bar, index, vel, params in CHANT_PLAN[phase]:
        end = chant(g, bar, CHANTS[index], phase, vel, **params)
        if end:
            cuts.append((end + 1, 5 if bar >= 24 else 6))
    # ---- the bridge: a roll over the whole bar 22, then the stop in bar 23
    g.clicks("roll", SNARE, 66, g.start(22) + BAR - 1, [1, 1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 6], 18, 96)
    for bar in (7, 15):                                                 # a short roll before the next block
        g.clicks("roll", SNARE, 66, g.start(bar) + BAR - 1, [1, 1, 1, 2, 2, 3, 3], 22, 70)
    # ---- the dropouts: every layer is cut, then the blow
    for bar, tick, length in DROPS[phase]:
        a = g.start(bar) + tick
        g.mute(a, a + length)
        strike(g, a + length, sh, False)
    for a, length in cuts:
        g.mute(a, a + length)
        strike(g, a + length, sh, False)
    g.mute(g.start(23) + 14, g.start(24))
    g.clicks("roll", HAT, 66, g.start(24) - 1, [1, 1, 2, 2, 3, 4, 6], 24, 80, over=True)
    # ---- blows at the start of each block, the peak with every sound, and the way back into the loop
    for first, big in ((0, False), (8, False), (16, False), (24, True), (40, False)):
        strike(g, g.start(first), sh, big)
    g.put("hym2", g.start(24), IRON_XYLOPHONE, 62 + sh, 70, over=True)
    g.clicks("roll", HAT, 66, g.length - 1, [1, 1, 1, 1, 2, 2, 3, 4, 5], 14, 70, over=True)
    return g


TITLES = ("Holy Circuit (Boss010)", "Holy Circuit, Overclocked (Boss010)")
FILES = ("circuit_phase1.nbs", "circuit_phase2.nbs")
DESCRIPTIONS = (
    "Boss, phase 1, class 3 (inorganic fear and erosion): 165 BPM in 7/4 (3+2+2 / 2+2+3), didgeridoo gun quarters > eighths > eighths with doubles > a sixteenth machine gun, a heart that fails, factory metal, an 8-bit sequencer, a chant of long notes that the machine breaks every time in a new way.",
    "Boss, phase 2: a half step up, the machine gun comes earlier, the heart on every group, the groups change each bar, a shuffled second note after each sequencer note, hat on every tick, more glitches, the chant cut sooner. Same bars as phase 1.")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    marks = [("intro", 0, 64)] + [(name, 64 + first * BAR, 64 + (first + count) * BAR) for name, first, count in SECTIONS]
    for phase in (0, 1):
        grid = compose(phase)
        print(f"-- phase {phase + 1}")
        write_song(grid, out / FILES[phase], TITLES[phase], DESCRIPTIONS[phase], marks, bass_layers=("gun",), keep_layers=("hymn", "hym2"))


if __name__ == "__main__":
    main()
